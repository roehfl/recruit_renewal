package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * springSecurity() filter chain을 적용해 /api/applicant/** SecurityConfig matcher까지 실제로 검증한다.
 * (미인증 401 = CustomAuthenticationEntryPoint, 임직원 403 = matcher + CustomAccessDeniedHandler)
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicantAccountControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditHmac auditHmac;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void 미인증이면_401() throws Exception {
        mockMvc.perform(post("/api/applicant/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "CurrentPw1234!",
                                  "newPassword": "NewPassword1!"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 임직원_인증이면_403() throws Exception {
        mockMvc.perform(post("/api/applicant/account/password")
                        .with(authentication(employeeAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "CurrentPw1234!",
                                  "newPassword": "NewPassword1!"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 비밀번호_변경_성공() throws Exception {
        Applicant applicant = createApplicant("account-pw", "CurrentPw1234!");

        mockMvc.perform(post("/api/applicant/account/password")
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "CurrentPw1234!",
                                  "newPassword": "NewPassword1!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Applicant updated = applicantRepository.findById(applicant.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPassword1!", updated.getPassword())).isTrue();
    }

    @Test
    void 비밀번호_변경_현재_비밀번호_불일치면_400() throws Exception {
        Applicant applicant = createApplicant("account-pw-wrong", "CurrentPw1234!");

        mockMvc.perform(post("/api/applicant/account/password")
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "WrongPw1234!",
                                  "newPassword": "NewPassword1!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void 비밀번호_변경_validation_위반이면_400() throws Exception {
        Applicant applicant = createApplicant("account-pw-valid", "CurrentPw1234!");

        mockMvc.perform(post("/api/applicant/account/password")
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "CurrentPw1234!",
                                  "newPassword": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    /* 조합 규칙: 2종류 이상 10자 이상 또는 3종류 이상 8자 이상(PasswordPolicy). */
    @Test
    void 비밀번호_변경_조합_규칙에_맞지_않으면_400() throws Exception {
        Applicant applicant = createApplicant("account-pw-policy", "CurrentPw1234!");

        mockMvc.perform(post("/api/applicant/account/password")
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "CurrentPw1234!",
                                  "newPassword": "abcdefg12"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("2종류 이상이면 10자 이상")));
    }

    /* BCrypt 는 72바이트를 넘으면 인코딩에서 예외를 던진다. 글자 수(100자)는 통과해도 500 이 아니라 400 이어야 한다. */
    @Test
    void 비밀번호_변경_72바이트를_넘으면_400() throws Exception {
        Applicant applicant = createApplicant("account-pw-bcrypt", "CurrentPw1234!");
        String tooLong = "Aa1!" + "가".repeat(23); // 조합 규칙은 통과하고 73바이트

        mockMvc.perform(post("/api/applicant/account/password")
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "CurrentPw1234!",
                                  "newPassword": "%s"
                                }
                                """.formatted(tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("72바이트")));
    }

    /*
     * 새 번호는 세션의 NICE 결과(용도 PHONE_CHANGE)에서 꺼낸다. 인증 명의(이름+생년월일+성별)가 가입자와 같아야 하고,
     * 요청 본문에 번호를 넣어도 무시한다. 인증 결과는 1회용이다.
     */
    @Test
    void 전화번호_변경_성공_시_NICE_결과의_번호로_바꾸고_인증을_소비한다() throws Exception {
        Applicant applicant = createVerifiedApplicant("account-phone", "CurrentPw1234!");
        MockHttpSession session = niceSession(NiceVerificationPurpose.PHONE_CHANGE, "홍길동", "19900101", "1", "01099998888");

        mockMvc.perform(post("/api/applicant/account/phone-number")
                        .with(authentication(applicantAuthentication(applicant)))
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "CurrentPw1234!",
                                  "phoneNumber": "01011112222"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Applicant updated = applicantRepository.findById(applicant.getId()).orElseThrow();
        assertThat(updated.getPhoneNumber()).isEqualTo("01099998888");
        assertThat(session.getAttribute(NiceVerificationController.VERIFIED_SESSION_KEY)).isNull();
    }

    @Test
    void 전화번호_변경은_NICE_인증이_없거나_용도가_다르면_400() throws Exception {
        Applicant applicant = createVerifiedApplicant("account-phone-nonice", "CurrentPw1234!");

        mockMvc.perform(post("/api/applicant/account/phone-number")
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PHONE_CHANGE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본인인증을 먼저 진행해주세요."));

        mockMvc.perform(post("/api/applicant/account/phone-number")
                        .with(authentication(applicantAuthentication(applicant)))
                        .session(niceSession(NiceVerificationPurpose.SIGNUP, "홍길동", "19900101", "1", "01099998888"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PHONE_CHANGE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본인인증 용도가 일치하지 않습니다."));

        assertThat(applicantRepository.findById(applicant.getId()).orElseThrow().getPhoneNumber()).isEqualTo("01000000000");
    }

    @Test
    void 전화번호_변경은_인증_명의가_가입자와_다르면_400() throws Exception {
        Applicant applicant = createVerifiedApplicant("account-phone-other", "CurrentPw1234!");

        mockMvc.perform(post("/api/applicant/account/phone-number")
                        .with(authentication(applicantAuthentication(applicant)))
                        .session(niceSession(NiceVerificationPurpose.PHONE_CHANGE, "김타인", "19851212", "2", "01099998888"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PHONE_CHANGE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본인인증 명의가 가입자 정보와 일치하지 않습니다."));

        assertThat(applicantRepository.findById(applicant.getId()).orElseThrow().getPhoneNumber()).isEqualTo("01000000000");
    }

    @Test
    void 전화번호_변경_validation_위반이면_400() throws Exception {
        Applicant applicant = createVerifiedApplicant("account-phone-valid", "CurrentPw1234!");

        mockMvc.perform(post("/api/applicant/account/phone-number")
                        .with(authentication(applicantAuthentication(applicant)))
                        .session(niceSession(NiceVerificationPurpose.PHONE_CHANGE, "홍길동", "19900101", "1", "01099998888"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    private static final String PHONE_CHANGE_BODY = """
            {
              "currentPassword": "CurrentPw1234!"
            }
            """;

    /** 가입 식별 키를 NICE 명의(홍길동·19900101·1)로 둔 지원자. */
    private Applicant createVerifiedApplicant(String loginId, String rawPassword) {
        Applicant applicant = new Applicant(auditHmac.identityHash("홍길동", "19900101", "1"));
        applicant.setLoginId(loginId);
        applicant.setName("홍길동");
        applicant.setUserName("홍길동");
        applicant.setPassword(passwordEncoder.encode(rawPassword));
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.save(applicant);
    }

    private static MockHttpSession niceSession(NiceVerificationPurpose purpose, String name, String birthDate,
                                               String gender, String phoneNumber) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(NiceVerificationController.VERIFIED_SESSION_KEY,
                new NiceVerifiedIdentity(purpose, name, phoneNumber, birthDate, gender, Instant.now()));
        return session;
    }

    private Applicant createApplicant(String loginId, String rawPassword) {
        String ci = loginId + "-ci";
        Applicant applicant = new Applicant(HashUtil.sha256(ci));
        applicant.setLoginId(loginId);
        applicant.setName("User-" + loginId);
        applicant.setUserName("User-" + loginId);
        applicant.setPassword(passwordEncoder.encode(rawPassword));
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.save(applicant);
    }

    private Authentication applicantAuthentication(Applicant applicant) {
        CustomUserDetails userDetails = CustomUserDetails.fromUser(
                applicant,
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private Authentication employeeAuthentication() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "emp01", "IT센터", "임직원",
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
