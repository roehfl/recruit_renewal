package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        SecurityContextHolder.clearContext();
    }

    @Test
    void 회원가입_성공() throws Exception {
        mockMvc.perform(post("/auth/applicants/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "applicant01",
                                  "password": "Password1234!",
                                  "name": "홍길동",
                                  "phoneNumber": "01012345678",
                                  "email": "applicant01@example.com",
                                  "ci": "test-ci-applicant01"
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
                .andExpect(jsonPath("$.data.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist());
    }

    @Test
    void validation_실패_시_400() throws Exception {
        mockMvc.perform(post("/auth/applicants/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "",
                                  "password": "short",
                                  "name": "",
                                  "phoneNumber": "",
                                  "ci": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void loginId_중복_시_400() throws Exception {
        Applicant existing = new Applicant("existing-ci", HashUtil.sha256("existing-ci"));
        existing.setLoginId("duplicate-id");
        existing.setName("기존사용자");
        existing.setUserName("기존사용자");
        existing.setPassword("encoded");
        existing.setPhoneNumber("01000000000");
        applicantRepository.save(existing);

        mockMvc.perform(post("/auth/applicants/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "duplicate-id",
                                  "password": "Password1234!",
                                  "name": "새사용자",
                                  "phoneNumber": "01011111111",
                                  "ci": "new-ci-value"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void GET_요청은_405() throws Exception {
        mockMvc.perform(get("/auth/applicants/sign-up"))
                .andExpect(status().isMethodNotAllowed());
    }
}
