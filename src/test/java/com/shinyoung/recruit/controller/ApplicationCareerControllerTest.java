package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.support.BasicInfoTestSupport;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicationCareerService;
import com.shinyoung.recruit.service.JobApplicationService;
import com.shinyoung.recruit.service.JobPostingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationCareerControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private ApplicationCareerService applicationCareerService;

    @Autowired
    private ApplicationBasicInfoRepository basicInfoRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

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
    void get_careers_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("career-api-get", "Career Api Get");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(false));
        authenticate(applicant);

        mockMvc.perform(get("/api/applications/{applicationId}/careers", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.careers").isArray());
    }

    @Test
    void replace_careers_empty_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("career-api-empty", "Career Api Empty");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}/careers", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "careers": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.careers").isArray());
    }

    @Test
    void replace_careers_experienced_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("career-api-experienced", "Career Api Experienced");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}/careers", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validExperiencedCareerJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.careers[0].companyName").value("Shinyoung Tech"));
    }

    @Test
    void validation_failure_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("career-api-validation", "Career Api Validation");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}/careers", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "careers": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void disabled_career_section_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("career-api-disabled", "Career Api Disabled");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(false));
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}/careers", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "careers": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void other_applicants_application_is_hidden() throws Exception {
        Applicant owner = createApplicant("career-api-owner", "Career Api Owner");
        Applicant other = createApplicant("career-api-other", "Career Api Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting(true));
        authenticate(other);

        mockMvc.perform(get("/api/applications/{applicationId}/careers", applicationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(post("/api/applications/{applicationId}/careers", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "careers": []
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void submitted_application_replace_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("career-api-submitted", "Career Api Submitted");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(false));
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(applicationId).orElseThrow());
        jobApplicationService.submit(applicant.getId(), applicationId);
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}/careers", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "careers": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void put_and_delete_methods_are_not_supported() throws Exception {
        mockMvc.perform(put("/api/applications/{applicationId}/careers", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete("/api/applications/{applicationId}/careers", 1L))
                .andExpect(status().isMethodNotAllowed());
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

    private Applicant createApplicant(String loginId, String applicantName) {
        String ci = loginId + "-ci";
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId(loginId);
        applicant.setName("User-" + applicantName);
        applicant.setUserName(applicantName);
        applicant.setPassword("encoded-password");
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.save(applicant);
    }

    private Long createApplication(Applicant applicant, Long jobPostingId) {
        return jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );
    }

    private Long createPublishedJobPosting(boolean useCareer) {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(
                        new JobPositionRequest("Backend", 0),
                        new JobPositionRequest("Frontend", 1)
                ),
                new ApplicationFormConfigRequest(false, useCareer, false, false, false, false, false)
        ));
        jobPostingService.publish(jobPostingId);
        return jobPostingId;
    }

    private Long firstJobPositionId(Long jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        return jobPosting.getJobPositions().stream()
                .sorted(Comparator.comparing(JobPosition::getSortOrder).thenComparing(JobPosition::getId))
                .map(JobPosition::getId)
                .findFirst()
                .orElseThrow();
    }

    private String validExperiencedCareerJson() {
        return """
                {
                  "careers": [
                    {
                      "companyName": "Shinyoung Tech",
                      "departmentName": "Platform",
                      "positionTitle": "Engineer",
                      "employmentType": "FULL_TIME",
                      "startDate": "2022-01-01",
                      "endDate": "2024-12-31",
                      "currentlyEmployed": false,
                      "responsibilities": "Backend development",
                      "resignationReason": "Career change",
                      "sortOrder": 0
                    }
                  ]
                }
                """;
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return FIXED_CLOCK;
        }
    }
}
