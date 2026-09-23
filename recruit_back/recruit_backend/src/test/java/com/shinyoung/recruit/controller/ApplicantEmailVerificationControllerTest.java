package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.enumeration.EmailVerificationPurpose;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.service.EmailVerificationService;
import com.shinyoung.recruit.service.GatewayResult;
import com.shinyoung.recruit.service.MailGateway;
import com.shinyoung.recruit.service.MailMessage;
import com.shinyoung.recruit.service.SmsGateway;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicantEmailVerificationControllerTest {

    private static final Pattern CODE = Pattern.compile("인증번호: (\\d{6})");

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ApplicantRepository applicantRepository;
    @Autowired
    private Clock clock;

    @MockitoBean
    private MailGateway mailGateway;
    @MockitoBean
    private SmsGateway smsGateway;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        SecurityContextHolder.clearContext();
        when(mailGateway.send(any(), anyList(), anyList())).thenReturn(GatewayResult.accepted("TX-VERIFY-1"));
    }

    @Test
    void 인증번호를_확인하면_가입할_수_있고_인증은_1회용이다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        send(session, "verify01@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        confirm(session, "verify01@example.com", sentCode()).andExpect(status().isOk());

        addNiceIdentity(session, "19910101");
        signUp(session, "verify01@example.com").andExpect(status().isOk());

        addNiceIdentity(session, "19910102");
        signUp(session, "verify01@example.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이메일 인증이 필요합니다."));
    }

    @Test
    void 틀린_번호는_거부하고_5회_틀리면_맞는_번호도_거부한다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        send(session, "verify02@example.com").andExpect(status().isOk());
        String code = sentCode();

        for (int attempt = 0; attempt < 5; attempt++) {
            confirm(session, "verify02@example.com", "wrong")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("인증번호가 일치하지 않습니다."));
        }

        confirm(session, "verify02@example.com", code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("인증번호를 5회 틀렸습니다. 인증번호를 다시 받아 주세요."));
    }

    @Test
    void 이미_가입된_이메일이면_보내지_않는다() throws Exception {
        Applicant existing = new Applicant(HashUtil.sha256("verify-taken-ci"));
        existing.setLoginId("verify-taken@example.com");
        existing.setName("기존사용자");
        existing.setUserName("기존사용자");
        existing.setPassword("encoded");
        existing.setPhoneNumber("01000000000");
        existing.setEmail("verify-taken@example.com");
        applicantRepository.save(existing);

        send(new MockHttpSession(), "verify-taken@example.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."));
        verifyNoInteractions(mailGateway);
    }

    @Test
    void 재발송은_60초_안에는_할_수_없다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        send(session, "verify03@example.com").andExpect(status().isOk());

        send(session, "verify03@example.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("인증번호는 60초 후에 다시 받을 수 있습니다."));
    }

    @Test
    void 메일이_접수되지_않으면_인증_상태를_남기지_않는다() throws Exception {
        when(mailGateway.send(any(), anyList(), anyList())).thenReturn(GatewayResult.failure("E100"));
        MockHttpSession session = new MockHttpSession();

        send(session, "verify04@example.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해 주세요."));

        assertThat(session.getAttribute(EmailVerificationService.sessionKey(EmailVerificationPurpose.SIGNUP))).isNull();
        confirm(session, "verify04@example.com", "123456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("인증번호를 다시 받아 주세요."));
    }

    private ResultActions send(MockHttpSession session, String email) throws Exception {
        return mockMvc.perform(post("/api/auth/applicants/email-verification/send")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions confirm(MockHttpSession session, String email, String code) throws Exception {
        return mockMvc.perform(post("/api/auth/applicants/email-verification/verify")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"));
    }

    private ResultActions signUp(MockHttpSession session, String email) throws Exception {
        return mockMvc.perform(post("/api/auth/applicants/sign-up")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + email + "\",\"password\":\"Password1234!\",\"email\":\"" + email + "\"}"));
    }

    /* 다른 테스트의 식별 키와 겹치지 않는 이 테스트 전용 신원. */
    private void addNiceIdentity(MockHttpSession session, String birthDate) {
        session.setAttribute(NiceVerificationController.VERIFIED_SESSION_KEY, new NiceVerifiedIdentity(
                NiceVerificationPurpose.SIGNUP, "김메일", "01000000000", birthDate, "1", clock.instant()));
    }

    private String sentCode() {
        ArgumentCaptor<MailMessage> mail = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailGateway, atLeastOnce()).send(mail.capture(), anyList(), anyList());
        Matcher matcher = CODE.matcher(mail.getValue().text());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
