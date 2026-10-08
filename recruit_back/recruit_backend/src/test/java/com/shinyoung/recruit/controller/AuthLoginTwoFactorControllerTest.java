package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 지원자 로그인 2차 인증(NICE) — 설정을 켠 상태에서 세션의 LOGIN 결과를 요구·소비하는지 본다. */
@SpringBootTest(properties = "recruit.nice.login-two-factor-enabled=true")
@Transactional
class AuthLoginTwoFactorControllerTest {

    private static final String LOGIN_ID = "login2fa@example.test";
    private static final String PASSWORD = "password123";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ApplicantRepository applicantRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuditHmac auditHmac;
    @Autowired
    private Clock clock;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Applicant applicant = new Applicant(auditHmac.identityHash("홍길동", "19900101", "1"));
        applicant.setLoginId(LOGIN_ID);
        applicant.setName("홍길동");
        applicant.setUserName("홍길동");
        applicant.setEmail(LOGIN_ID);
        applicant.setPassword(passwordEncoder.encode(PASSWORD));
        applicantRepository.save(applicant);
    }

    private MockHttpSession sessionWith(NiceVerificationPurpose purpose, String name, Instant verifiedAt) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(NiceVerificationController.VERIFIED_SESSION_KEY,
                new NiceVerifiedIdentity(purpose, name, "01000000000", "19900101", "1", verifiedAt));
        return session;
    }

    private ResultActions login(MockHttpSession session, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + LOGIN_ID + "\",\"password\":\"" + password + "\"}"));
    }

    private Object niceAttribute(MockHttpSession session) {
        return session.getAttribute(NiceVerificationController.VERIFIED_SESSION_KEY);
    }

    @Test
    void login_options는_설정값을_비로그인에게_알려준다() throws Exception {
        mockMvc.perform(get("/api/auth/login-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.twoFactorEnabled").value(true));
    }

    @Test
    void 명의가_같은_LOGIN_인증_결과가_있으면_로그인되고_결과는_소비된다() throws Exception {
        MockHttpSession session = sessionWith(NiceVerificationPurpose.LOGIN, "홍길동", clock.instant());

        login(session, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value(LOGIN_ID));

        assertThat(niceAttribute(session)).isNull();
    }

    @Test
    void 인증_결과가_없으면_비밀번호가_맞아도_400이다() throws Exception {
        login(new MockHttpSession(), PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본인인증을 먼저 진행해주세요."));
    }

    @Test
    void 명의가_다르면_400이고_결과는_소비된다() throws Exception {
        MockHttpSession session = sessionWith(NiceVerificationPurpose.LOGIN, "김철수", clock.instant());

        login(session, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본인인증 명의가 가입자 정보와 일치하지 않습니다."));

        assertThat(niceAttribute(session)).isNull();
    }

    @Test
    void 인증_후_5분이_지났으면_400이다() throws Exception {
        MockHttpSession session = sessionWith(NiceVerificationPurpose.LOGIN, "홍길동", clock.instant().minusSeconds(301));

        login(session, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본인인증이 만료되었습니다. 다시 진행해주세요."));
    }

    @Test
    void 비밀번호가_틀리면_401이고_인증_결과는_남겨_재입력에_쓴다() throws Exception {
        MockHttpSession session = sessionWith(NiceVerificationPurpose.LOGIN, "홍길동", clock.instant());

        login(session, "wrong-password").andExpect(status().isUnauthorized());

        assertThat(niceAttribute(session)).isNotNull();
        login(session, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void 다른_용도의_인증_결과로는_로그인할_수_없고_결과도_지우지_않는다() throws Exception {
        MockHttpSession session = sessionWith(NiceVerificationPurpose.SIGNUP, "홍길동", clock.instant());

        login(session, PASSWORD).andExpect(status().isBadRequest());

        assertThat(niceAttribute(session)).isNotNull();
    }
}
