package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.MessageContacts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class MessageHistoryAdminControllerTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JobPostingRepository jobPostingRepository;
    @Autowired
    private MessageSendRepository messageSendRepository;
    @Autowired
    private MessageRecipientRepository messageRecipientRepository;
    @Autowired
    private Clock clock;

    private MockMvc mockMvc;
    private JobPosting posting;
    private MessageSend send;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        posting = JobPosting.create("이력 API 공고", "Content",
                LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 22, 18, 0));
        posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        posting = jobPostingRepository.saveAndFlush(posting);
        LocalDateTime now = LocalDateTime.now(clock);
        send = messageSendRepository.saveAndFlush(MessageSend.create(
                MessageType.FREE, false, posting, null, "제출 완료", null, null,
                true, false, "[신영증권] #{이름}님 안내", "본문", null, "hr.kim", "김인사", 1, now));
        MessageRecipient recipient = MessageRecipient.create(send, null, "김지원", "kim@example.com", null,
                MessageDeliveryStatus.PENDING, null, MessageDeliveryStatus.SKIPPED, MessageContacts.CHANNEL_OFF, null);
        recipient.recordRequested(MessageChannel.MAIL, "TX-API-1", now);
        messageRecipientRepository.saveAndFlush(recipient);
    }

    @Test
    void 이력_목록을_준다() throws Exception {
        mockMvc.perform(get("/api/admin/messages/history")
                        .param("jobPostingId", String.valueOf(posting.getId()))
                        .with(authentication(employee())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(send.getId()))
                .andExpect(jsonPath("$.data.content[0].origin").value("ADMIN"))
                .andExpect(jsonPath("$.data.content[0].jobPostingTitle").value("이력 API 공고"))
                .andExpect(jsonPath("$.data.content[0].title").value("[신영증권] #{이름}님 안내"))
                .andExpect(jsonPath("$.data.content[0].mail.requested").value(1))
                .andExpect(jsonPath("$.data.content[0].sms.skipped").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("RESULT_PENDING"))
                .andExpect(jsonPath("$.data.content[0].delayed").value(false));
    }

    @Test
    void 이력_목록은_발송_구분으로_거른다() throws Exception {
        mockMvc.perform(get("/api/admin/messages/history")
                        .param("jobPostingId", String.valueOf(posting.getId()))
                        .param("origin", "SYSTEM")
                        .with(authentication(employee())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 이력_상세는_원문과_수신자_연락처를_그대로_준다() throws Exception {
        mockMvc.perform(get("/api/admin/messages/history/{sendId}", send.getId())
                        .with(authentication(employee())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(send.getId()))
                .andExpect(jsonPath("$.data.mailSubject").value("[신영증권] #{이름}님 안내"))
                .andExpect(jsonPath("$.data.recipients.length()").value(1))
                .andExpect(jsonPath("$.data.recipients[0].name").value("김지원"))
                .andExpect(jsonPath("$.data.recipients[0].email").value("kim@example.com"))
                .andExpect(jsonPath("$.data.recipients[0].mailStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.data.recipients[0].smsFailureReason").value("CHANNEL_OFF"));
    }

    @Test
    void 없는_발송이면_404() throws Exception {
        mockMvc.perform(get("/api/admin/messages/history/{sendId}", 999_999L)
                        .with(authentication(employee())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("발송 기록을 찾을 수 없습니다."));
    }

    @Test
    void 페이지_크기가_100을_넘으면_400() throws Exception {
        mockMvc.perform(get("/api/admin/messages/history")
                        .param("size", "101")
                        .with(authentication(employee())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("size는 1 이상 100 이하여야 합니다."));
    }

    private Authentication employee() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "hr.kim", "인사팀", "김인사", List.of(new SimpleGrantedAuthority("ROLE_RECRUIT_ADMIN")));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
