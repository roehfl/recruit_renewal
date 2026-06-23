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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationBasicInfoControllerTest {

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
    void get_returns_prefill_before_save() throws Exception {
        Applicant applicant = createApplicant("bi-api-prefill", "Api Prefill");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        authenticate(applicant);

        mockMvc.perform(get("/api/applications/{applicationId}/basic-info", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.persisted").value(false))
                .andExpect(jsonPath("$.data.basicInfoId").isEmpty())
                .andExpect(jsonPath("$.data.nameKorean").value("Api Prefill"));
    }

    @Test
    void save_then_get_returns_persisted() throws Exception {
        Applicant applicant = createApplicant("bi-api-save", "Api Save");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}/basic-info", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nameKorean": "홍길동",
                                  "nationalityType": "DOMESTIC",
                                  "birthDate": "1995-01-01",
                                  "mobilePhone": "01012345678",
                                  "email": "hong@example.com",
                                  "veteranStatus": "NOT_SUBJECT",
                                  "disabilityStatus": "NOT_SUBJECT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.persisted").value(true))
                .andExpect(jsonPath("$.data.nameKorean").value("홍길동"));

        mockMvc.perform(get("/api/applications/{applicationId}/basic-info", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.persisted").value(true));
    }

    @Test
    void save_with_missing_required_field_returns_bad_request() throws Exception {
        Applicant applicant = createApplicant("bi-api-validation", "Api Validation");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}/basic-info", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nationalityType": "DOMESTIC",
                                  "birthDate": "1995-01-01",
                                  "mobilePhone": "01012345678",
                                  "email": "hong@example.com",
                                  "veteranStatus": "NOT_SUBJECT",
                                  "disabilityStatus": "NOT_SUBJECT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void veteran_subject_without_type_returns_bad_request() throws Exception {
        Applicant applicant = createApplicant("bi-api-veteran", "Api Veteran");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}/basic-info", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nameKorean": "홍길동",
                                  "nationalityType": "DOMESTIC",
                                  "birthDate": "1995-01-01",
                                  "mobilePhone": "01012345678",
                                  "email": "hong@example.com",
                                  "veteranStatus": "SUBJECT",
                                  "disabilityStatus": "NOT_SUBJECT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void veteran_subject_with_type_persists() throws Exception {
        Applicant applicant = createApplicant("bi-api-veteran-ok", "Api Veteran Ok");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}/basic-info", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nameKorean": "홍길동",
                                  "nationalityType": "DOMESTIC",
                                  "birthDate": "1995-01-01",
                                  "mobilePhone": "01012345678",
                                  "email": "hong@example.com",
                                  "veteranStatus": "SUBJECT",
                                  "veteranType": "국가유공자",
                                  "disabilityStatus": "NOT_SUBJECT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.persisted").value(true))
                .andExpect(jsonPath("$.data.veteranType").value("국가유공자"));
    }

    @Test
    void other_applicants_application_is_hidden() throws Exception {
        Applicant owner = createApplicant("bi-api-owner", "Api Owner");
        Applicant other = createApplicant("bi-api-other", "Api Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting());
        authenticate(other);

        mockMvc.perform(get("/api/applications/{applicationId}/basic-info", applicationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
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

    private Long createPublishedJobPosting() {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(
                        new JobPositionRequest("Backend", 0),
                        new JobPositionRequest("Frontend", 1)
                ),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)
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

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return FIXED_CLOCK;
        }
    }
}
