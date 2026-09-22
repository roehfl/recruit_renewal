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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicantAccountRecoveryControllerTest {

    private static final String PATH = "/api/auth/applicants/find-email";

    /* 다른 테스트가 남긴 계정과 식별 키가 겹치지 않도록 이 테스트 전용 신원을 쓴다. */
    private static final String NAME = "김찾기";
    private static final String BIRTH_DATE = "19851212";
    private static final String GENDER = "2";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private AuditHmac auditHmac;

    @Autowired
    private Clock clock;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        SecurityContextHolder.clearContext();
    }

    private MockHttpSession verifiedSession(NiceVerificationPurpose purpose, Instant verifiedAt) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                NiceVerificationController.VERIFIED_SESSION_KEY,
                new NiceVerifiedIdentity(purpose, NAME, "01012345678", BIRTH_DATE, GENDER, verifiedAt));
        return session;
    }

    private MockHttpSession verifiedSession(NiceVerificationPurpose purpose) {
        return verifiedSession(purpose, clock.instant());
    }

    private void saveApplicant(String loginId) {
        Applicant applicant = new Applicant(auditHmac.identityHash(NAME, BIRTH_DATE, GENDER));
        applicant.setLoginId(loginId);
        applicant.setName(NAME);
        applicant.setUserName(NAME);
        applicant.setPassword("encoded");
        applicant.setPhoneNumber("01012345678");
        applicant.setEmail(loginId);
        applicantRepository.save(applicant);
    }

    @Test
    void 아이디_찾기_성공_시_마스킹한_아이디만_응답한다() throws Exception {
        saveApplicant("abc12345@gmail.com");

        MvcResult result = mockMvc.perform(post(PATH).session(verifiedSession(NiceVerificationPurpose.FIND_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.maskedEmail").value("ab******@gmail.com"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("abc12345");
    }

    /* 인증 1회 = 조회 1회. 성공한 뒤 같은 세션으로 다시 부르면 인증을 먼저 하라고 해야 한다. */
    @Test
    void 인증_결과는_1회용이라_두번째_호출은_400() throws Exception {
        saveApplicant("abc12345@gmail.com");
        MockHttpSession session = verifiedSession(NiceVerificationPurpose.FIND_EMAIL);

        mockMvc.perform(post(PATH).session(session)).andExpect(status().isOk());
        assertThat(session.getAttribute(NiceVerificationController.VERIFIED_SESSION_KEY)).isNull();

        mockMvc.perform(post(PATH).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본인인증을 먼저 진행해주세요."));
    }

    @Test
    void 세션에_인증_결과가_없으면_400() throws Exception {
        mockMvc.perform(post(PATH).session(new MockHttpSession()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본인인증을 먼저 진행해주세요."));
    }

    @Test
    void 가입용_인증으로는_아이디를_찾을_수_없다() throws Exception {
        saveApplicant("abc12345@gmail.com");

        mockMvc.perform(post(PATH).session(verifiedSession(NiceVerificationPurpose.SIGNUP)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본인인증 용도가 일치하지 않습니다."));
    }

    @Test
    void 만료된_인증은_400() throws Exception {
        saveApplicant("abc12345@gmail.com");
        Instant expired = clock.instant().minus(Duration.ofMinutes(31));

        mockMvc.perform(post(PATH).session(verifiedSession(NiceVerificationPurpose.FIND_EMAIL, expired)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본인인증이 만료되었습니다. 다시 진행해주세요."));
    }

    /* 계정이 없어도 인증 결과는 소비된다 — 조회는 결정적이라 같은 인증으로 다시 시도할 이유가 없다. */
    @Test
    void 일치하는_계정이_없으면_404이고_인증_결과는_소비된다() throws Exception {
        MockHttpSession session = verifiedSession(NiceVerificationPurpose.FIND_EMAIL);

        mockMvc.perform(post(PATH).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("본인인증 정보와 일치하는 계정이 없습니다."));

        assertThat(session.getAttribute(NiceVerificationController.VERIFIED_SESSION_KEY)).isNull();
    }
}
