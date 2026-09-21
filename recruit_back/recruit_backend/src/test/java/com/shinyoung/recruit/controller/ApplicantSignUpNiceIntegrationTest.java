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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class ApplicantSignUpNiceIntegrationTest {

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
    }

    private MockHttpSession sessionWithIdentity(String ci) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                NiceVerificationController.VERIFIED_SESSION_KEY,
                new NiceVerifiedIdentity(
                        NiceVerificationPurpose.SIGNUP, "홍길동", "01012345678", ci, clock.instant()));
        return session;
    }

    private String signUpBody(String loginId) {
        return "{\"loginId\":\"" + loginId + "\",\"password\":\"password123\",\"email\":\""
                + loginId + "\"}";
    }

    @Test
    void signUpUsesIdentityFromSessionNotFromRequestBody() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(sessionWithIdentity("CI-FROM-SERVER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody("nice1@example.test")))
                .andExpect(status().isOk());

        Applicant saved = applicantRepository.findByCiHash(HashUtil.sha256("CI-FROM-SERVER")).orElseThrow();
        assertEquals("홍길동", saved.getUserName());
        assertEquals("01012345678", saved.getPhoneNumber());
    }

    @Test
    void signUpWithoutVerifiedSessionIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(new MockHttpSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody("nice2@example.test")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void signUpConsumesIdentitySoItCannotBeReused() throws Exception {
        MockHttpSession session = sessionWithIdentity("CI-ONCE");

        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody("nice3@example.test")))
                .andExpect(status().isOk());

        // 같은 세션으로 다시 가입을 시도한다. 인증 1회로 계정 2개를 만들 수 있으면 안 된다.
        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody("nice4@example.test")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void sameCiCannotSignUpTwice() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(sessionWithIdentity("CI-DUPLICATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody("nice5@example.test")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(sessionWithIdentity("CI-DUPLICATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody("nice6@example.test")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void expiredIdentityIsRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                NiceVerificationController.VERIFIED_SESSION_KEY,
                new NiceVerifiedIdentity(
                        NiceVerificationPurpose.SIGNUP, "홍길동", "01012345678", "CI-STALE",
                        clock.instant().minus(Duration.ofMinutes(31))));

        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody("nice7@example.test")))
                .andExpect(status().is4xxClientError());
    }
}
