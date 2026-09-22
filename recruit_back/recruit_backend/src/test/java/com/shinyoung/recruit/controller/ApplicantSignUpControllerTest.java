package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicantSignUpControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private Clock clock;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        SecurityContextHolder.clearContext();
    }

    /**
     * NICE 본인확인을 마친 세션을 만든다.
     *
     * 가입 API 는 이제 이름·휴대폰·생년월일·성별을 요청 본문이 아니라 이 세션 속성에서 읽는다.
     * 이 세션이 없으면 어떤 요청이든 400 이 나므로, 가입 로직을 검증하려는
     * 테스트는 반드시 이 세션을 붙여야 한다 — 안 붙이면 다른 이유로 400 이 나서
     * 테스트가 의도한 것을 검증하지 못한다.
     */
    private MockHttpSession verifiedSession(String name, String phoneNumber, String birthDate, String gender) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                NiceVerificationController.VERIFIED_SESSION_KEY,
                new NiceVerifiedIdentity(
                        NiceVerificationPurpose.SIGNUP, name, phoneNumber, birthDate, gender, clock.instant()));
        return session;
    }

    @Test
    void 회원가입_성공() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(verifiedSession("홍길동", "01012345678", "19900101", "1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "applicant01",
                                  "password": "Password1234!",
                                  "email": "applicant01@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicantId").isNumber())
                .andExpect(jsonPath("$.data.loginId").value("applicant01"))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.ci").doesNotExist())
                .andExpect(jsonPath("$.data.ciHash").doesNotExist())
                .andExpect(jsonPath("$.data.birthDate").doesNotExist())
                .andExpect(jsonPath("$.data.gender").doesNotExist())
                .andExpect(jsonPath("$.data.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist());
    }

    @Test
    void validation_실패_시_400() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(verifiedSession("검증실패", "01099999999", "19900101", "1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void loginId_중복_시_400() throws Exception {
        Applicant existing = new Applicant(HashUtil.sha256("existing-ci"));
        existing.setLoginId("duplicate-id");
        existing.setName("기존사용자");
        existing.setUserName("기존사용자");
        existing.setPassword("encoded");
        existing.setPhoneNumber("01000000000");
        applicantRepository.save(existing);

        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(verifiedSession("새사용자", "01011111111", "19900101", "1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "duplicate-id",
                                  "password": "Password1234!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void 본인인증_세션이_없으면_400() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "nosession01",
                                  "password": "Password1234!",
                                  "email": "nosession01@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void GET_요청은_405() throws Exception {
        mockMvc.perform(get("/api/auth/applicants/sign-up"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void 이메일_가용성_미점유면_available_true() throws Exception {
        mockMvc.perform(get("/api/auth/applicants/check-email")
                        .param("email", "free@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    void 이메일_가용성_점유면_available_false() throws Exception {
        Applicant existing = new Applicant(HashUtil.sha256("email-ci"));
        existing.setLoginId("email-holder");
        existing.setName("기존사용자");
        existing.setUserName("기존사용자");
        existing.setPassword("encoded");
        existing.setPhoneNumber("01000000000");
        existing.setEmail("taken@example.com");
        applicantRepository.save(existing);

        mockMvc.perform(get("/api/auth/applicants/check-email")
                        .param("email", "taken@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    void 이메일_형식_오류는_400() throws Exception {
        mockMvc.perform(get("/api/auth/applicants/check-email")
                        .param("email", "not-an-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void 이메일_blank는_400() throws Exception {
        mockMvc.perform(get("/api/auth/applicants/check-email")
                        .param("email", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    /* 용도 대조 역방향 — 아이디 찾기용 인증 결과로 계정을 만들 수 없어야 한다. */
    @Test
    void 아이디_찾기용_인증으로는_가입할_수_없다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                NiceVerificationController.VERIFIED_SESSION_KEY,
                new NiceVerifiedIdentity(
                        NiceVerificationPurpose.FIND_EMAIL, "홍길동", "01012345678", "19900101", "1", clock.instant()));

        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "purpose-mismatch@example.com",
                                  "password": "Password1234!",
                                  "email": "purpose-mismatch@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본인인증 용도가 일치하지 않습니다."));
    }
}
