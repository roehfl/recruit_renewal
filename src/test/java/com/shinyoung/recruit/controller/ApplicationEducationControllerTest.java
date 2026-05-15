package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicationEducationService;
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
class ApplicationEducationControllerTest {

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
    private ApplicationEducationService applicationEducationService;

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
    void get_educations_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("education-api-get", "Education Api Get");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));
        authenticate(applicant);

        mockMvc.perform(get("/applications/{applicationId}/educations", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void replace_educations_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("education-api-replace", "Education Api Replace");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));
        authenticate(applicant);

        mockMvc.perform(post("/applications/{applicationId}/educations", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEducationJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data[0].educationLevel").value("UNIVERSITY"))
                .andExpect(jsonPath("$.data[0].semesterGrades[0].schoolYear").value(1));
    }

    @Test
    void validation_failure_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("education-api-validation", "Education Api Validation");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));
        authenticate(applicant);

        mockMvc.perform(post("/applications/{applicationId}/educations", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "educations": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void invalid_enum_failure_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("education-api-enum", "Education Api Enum");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));
        authenticate(applicant);

        mockMvc.perform(post("/applications/{applicationId}/educations", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "educations": [
                                    {
                                      "educationLevel": "UNKNOWN_LEVEL",
                                      "schoolName": "University",
                                      "graduationStatus": "GRADUATED",
                                      "sortOrder": 0,
                                      "semesterGrades": []
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void disabled_education_section_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("education-api-disabled", "Education Api Disabled");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(false));
        authenticate(applicant);

        mockMvc.perform(post("/applications/{applicationId}/educations", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEducationJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void other_applicants_application_is_hidden() throws Exception {
        Applicant owner = createApplicant("education-api-owner", "Education Api Owner");
        Applicant other = createApplicant("education-api-other", "Education Api Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting(true));
        authenticate(other);

        mockMvc.perform(get("/applications/{applicationId}/educations", applicationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(post("/applications/{applicationId}/educations", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEducationJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void submitted_application_replace_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("education-api-submitted", "Education Api Submitted");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));
        jobApplicationService.submit(applicant.getId(), applicationId);
        authenticate(applicant);

        mockMvc.perform(post("/applications/{applicationId}/educations", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEducationJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void put_and_delete_methods_are_not_supported() throws Exception {
        mockMvc.perform(put("/applications/{applicationId}/educations", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete("/applications/{applicationId}/educations", 1L))
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

    private Long createPublishedJobPosting(boolean useEducation) {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(
                        new JobPositionRequest("Backend", 2, 0),
                        new JobPositionRequest("Frontend", 1, 1)
                ),
                new ApplicationFormConfigRequest(useEducation, true, true, true, true, true, true)
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

    private String validEducationJson() {
        return """
                {
                  "educations": [
                    {
                      "educationLevel": "UNIVERSITY",
                      "schoolName": "Shinyoung University",
                      "majorName": "Computer Science",
                      "degreeName": "Bachelor",
                      "admissionDate": "2021-03-01",
                      "graduationDate": "2025-02-28",
                      "graduationStatus": "GRADUATED",
                      "dayNightType": "DAY",
                      "campusType": "MAIN",
                      "transfer": false,
                      "countryCode": "KR",
                      "sortOrder": 0,
                      "semesterGrades": [
                        {
                          "schoolYear": 1,
                          "semester": 1,
                          "earnedCredits": 18.0,
                          "gradePoint": 4.0,
                          "maxGradePoint": 4.5,
                          "majorGradePoint": 3.8,
                          "majorMaxGradePoint": 4.5
                        }
                      ]
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
