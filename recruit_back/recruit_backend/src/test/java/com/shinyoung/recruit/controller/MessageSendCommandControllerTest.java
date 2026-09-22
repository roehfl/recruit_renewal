package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class MessageSendCommandControllerTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JobPostingRepository jobPostingRepository;
    @Autowired
    private JobApplicationRepository jobApplicationRepository;
    @Autowired
    private ApplicantRepository applicantRepository;

    private MockMvc mockMvc;
    private JobPosting posting;
    private JobApplication application;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        posting = JobPosting.create("메시지 공고", "Content",
                LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 22, 18, 0));
        posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        posting = jobPostingRepository.saveAndFlush(posting);
        application = submitted("김지원");
    }

    @Test
    void 테스트_발송_접수_결과를_준다() throws Exception {
        mockMvc.perform(post("/api/admin/messages/test")
                        .with(authentication(employee()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "FREE",
                                  "jobPostingId": %d,
                                  "previewApplicationId": %d,
                                  "testers": [{"name": "김인사", "email": "hr.kim@example.com"}],
                                  "content": {"mailEnabled": true, "smsEnabled": false,
                                              "mailSubject": "#{이름}님 안내", "mailBody": "본문"}
                                }
                                """.formatted(posting.getId(), application.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sendId").isNumber())
                .andExpect(jsonPath("$.data.results.length()").value(2))
                .andExpect(jsonPath("$.data.results[0].channel").value("MAIL"))
                .andExpect(jsonPath("$.data.results[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.results[1].status").value("SKIPPED"));
    }

    @Test
    void 발송을_접수한다() throws Exception {
        mockMvc.perform(post("/api/admin/messages/send")
                        .with(authentication(employee()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "FREE",
                                  "jobPostingId": %d,
                                  "applicationIds": [%d],
                                  "content": {"mailEnabled": false, "smsEnabled": true, "smsBody": "#{이름}님 문자"}
                                }
                                """.formatted(posting.getId(), application.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENDING"))
                .andExpect(jsonPath("$.data.recipientCount").value(1))
                .andExpect(jsonPath("$.data.excludedCount").value(0));
    }

    @Test
    void 내용이_없으면_400() throws Exception {
        mockMvc.perform(post("/api/admin/messages/send")
                        .with(authentication(employee()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "FREE", "jobPostingId": %d, "applicationIds": [%d]}
                                """.formatted(posting.getId(), application.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private Authentication employee() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "hr.kim", "인사팀", "김인사", List.of(new SimpleGrantedAuthority("ROLE_RECRUIT_ADMIN")));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private JobApplication submitted(String name) {
        String ci = "test-ci-" + UUID.randomUUID();
        Applicant applicant = new Applicant(HashUtil.sha256(ci));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName(name);
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setUserName(name);
        applicant.setPhoneNumber("01000000000");
        applicantRepository.saveAndFlush(applicant);
        JobPosition jobPosition = posting.getJobPositions().get(0);
        JobApplication created = JobApplication.create(
                applicant, posting, jobPosition, name, posting.getTitle(), jobPosition.getPositionName());
        created.submit(LocalDateTime.of(2026, 9, 10, 9, 0));
        return jobApplicationRepository.saveAndFlush(created);
    }
}
