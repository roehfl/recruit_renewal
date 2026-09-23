package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.enumeration.EmailVerificationPurpose;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.service.EmailVerificationService;
import com.shinyoung.recruit.service.EmailVerificationState;
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
import java.time.LocalDateTime;

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

    /** 가입 이메일 인증까지 마친 세션으로 만든다. 번호 발송·확인 흐름은 ApplicantEmailVerificationControllerTest 가 본다. */
    private MockHttpSession withVerifiedEmail(MockHttpSession session, String email) {
        LocalDateTime now = LocalDateTime.now(clock);
        EmailVerificationState state = EmailVerificationState.issued(
                EmailVerificationPurpose.SIGNUP, email, "dummy-hash", now.plusMinutes(5), now);
        state.markVerified(now);
        session.setAttribute(EmailVerificationService.sessionKey(EmailVerificationPurpose.SIGNUP), state);
        return session;
    }

    @Test
    void 회원가입_성공() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(withVerifiedEmail(verifiedSession("홍길동", "01012345678", "19900101", "1"), "applicant01@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "applicant01@example.com",
                                  "password": "Password1234!",
                                  "email": "applicant01@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicantId").isNumber())
                .andExpect(jsonPath("$.data.loginId").value("applicant01@example.com"))
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
        existing.setLoginId("duplicate-new@example.com");
        existing.setName("기존사용자");
        existing.setUserName("기존사용자");
        existing.setPassword("encoded");
        existing.setPhoneNumber("01000000000");
        applicantRepository.save(existing);

        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(withVerifiedEmail(verifiedSession("새사용자", "01011111111", "19900101", "1"),
                                "duplicate-new@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "duplicate-new@example.com",
                                  "password": "Password1234!",
                                  "email": "duplicate-new@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 아이디입니다."));
    }

    @Test
    void 이메일_인증_없이는_가입할_수_없다() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(verifiedSession("메일미인증", "01022222222", "19900202", "1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "no-verify@example.com",
                                  "password": "Password1234!",
                                  "email": "no-verify@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이메일 인증이 필요합니다."));
    }

    @Test
    void 인증한_이메일과_다른_이메일로는_가입할_수_없다() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(withVerifiedEmail(verifiedSession("메일다름", "01033333333", "19900303", "1"),
                                "verified@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "other@example.com",
                                  "password": "Password1234!",
                                  "email": "other@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이메일 인증이 필요합니다."));
    }

    @Test
    void 아이디가_인증한_이메일과_다르면_가입할_수_없다() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(withVerifiedEmail(verifiedSession("아이디다름", "01055555555", "19900505", "1"),
                                "mine@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "someone-else@example.com",
                                  "password": "Password1234!",
                                  "email": "mine@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("아이디는 인증한 이메일과 같아야 합니다."));
    }

    @Test
    void 이메일을_비우면_이메일_인증이_필요하다() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(withVerifiedEmail(verifiedSession("메일없음", "01044444444", "19900404", "1"),
                                "verified@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "no-email-id",
                                  "password": "Password1234!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이메일 인증이 필요합니다."));
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
