package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicantAccountControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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
        authenticateEmployee();

        mockMvc.perform(post("/api/applicant/account/password")
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
        authenticate(applicant);

        mockMvc.perform(post("/api/applicant/account/password")
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
        authenticate(applicant);

        mockMvc.perform(post("/api/applicant/account/password")
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
        authenticate(applicant);

        mockMvc.perform(post("/api/applicant/account/password")
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

    @Test
    void 전화번호_변경_성공() throws Exception {
        Applicant applicant = createApplicant("account-phone", "CurrentPw1234!");
        authenticate(applicant);

        mockMvc.perform(post("/api/applicant/account/phone-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "CurrentPw1234!",
                                  "phoneNumber": "01099998888"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Applicant updated = applicantRepository.findById(applicant.getId()).orElseThrow();
        assertThat(updated.getPhoneNumber()).isEqualTo("01099998888");
    }

    @Test
    void 전화번호_변경_validation_위반이면_400() throws Exception {
        Applicant applicant = createApplicant("account-phone-valid", "CurrentPw1234!");
        authenticate(applicant);

        mockMvc.perform(post("/api/applicant/account/phone-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "CurrentPw1234!",
                                  "phoneNumber": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    private Applicant createApplicant(String loginId, String rawPassword) {
        String ci = loginId + "-ci";
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId(loginId);
        applicant.setName("User-" + loginId);
        applicant.setUserName("User-" + loginId);
        applicant.setPassword(passwordEncoder.encode(rawPassword));
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.save(applicant);
    }

    private void authenticate(Applicant applicant) {
        CustomUserDetails userDetails = CustomUserDetails.fromUser(
                applicant,
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }

    private void authenticateEmployee() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "emp01", "IT센터", "임직원",
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }
}
