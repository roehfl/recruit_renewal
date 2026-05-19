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
import com.shinyoung.recruit.dto.request.StageCreateRequest;
import com.shinyoung.recruit.dto.request.StageResultUpdateRequest;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.JobApplicationService;
import com.shinyoung.recruit.service.JobPostingService;
import com.shinyoung.recruit.service.StageResultService;
import com.shinyoung.recruit.service.StageService;
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
import org.springframework.security.core.Authentication;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationStageResultControllerTest {

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
    private StageService stageService;

    @Autowired
    private StageResultService stageResultService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void get_stage_results_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Applicant applicant = createApplicant("applicant-result-api", "Applicant Result Api");
        Long applicationId = createSubmittedApplication(applicant, jobPostingId);
        Long stageId = createStage(jobPostingId, 0, true, LocalDateTime.of(2026, 7, 1, 10, 0));
        decideAndAnnounce(jobPostingId, stageId, StageResultStatus.PASSED);

        mockMvc.perform(get("/applications/{applicationId}/stage-results", applicationId)
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].stageName").value("Stage 0"))
                .andExpect(jsonPath("$.data[0].stageType").value("DOCUMENT"))
                .andExpect(jsonPath("$.data[0].stageOrder").value(0))
                .andExpect(jsonPath("$.data[0].resultStatus").value("PASSED"))
                .andExpect(jsonPath("$.data[0].resultAnnouncementDateTime").exists())
                .andExpect(jsonPath("$.data[0].decidedAt").exists())
                .andExpect(jsonPath("$.data[0].stageResultId").doesNotExist())
                .andExpect(jsonPath("$.data[0].score").doesNotExist())
                .andExpect(jsonPath("$.data[0].comment").doesNotExist())
                .andExpect(jsonPath("$.data[0].decidedBy").doesNotExist())
                .andExpect(jsonPath("$.data[0].correctedBy").doesNotExist());
    }

    @Test
    void draft_application_returns_business_failure() throws Exception {
        Long jobPostingId = createJobPosting();
        Applicant applicant = createApplicant("applicant-result-api-draft", "Applicant Result Api Draft");
        Long applicationId = createApplication(applicant, jobPostingId);

        mockMvc.perform(get("/applications/{applicationId}/stage-results", applicationId)
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void other_applicants_application_is_hidden() throws Exception {
        Long jobPostingId = createJobPosting();
        Applicant owner = createApplicant("applicant-result-api-owner", "Applicant Result Api Owner");
        Applicant other = createApplicant("applicant-result-api-other", "Applicant Result Api Other");
        Long applicationId = createSubmittedApplication(owner, jobPostingId);
        Long stageId = createStage(jobPostingId, 0, true, LocalDateTime.of(2026, 7, 1, 10, 0));
        decideAndAnnounce(jobPostingId, stageId, StageResultStatus.PASSED);

        mockMvc.perform(get("/applications/{applicationId}/stage-results", applicationId)
                        .with(authentication(applicantAuthentication(other))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void unsupported_methods_are_not_added() throws Exception {
        Applicant applicant = createApplicant("applicant-result-api-method", "Applicant Result Api Method");

        mockMvc.perform(post("/applications/{applicationId}/stage-results", 1L)
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/applications/{applicationId}/stage-results", 1L)
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch("/applications/{applicationId}/stage-results", 1L)
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/applications/{applicationId}/stage-results", 1L)
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void application_stage_result_api_blocks_employee_admin_and_anonymous() throws Exception {
        Long jobPostingId = createJobPosting();
        Applicant applicant = createApplicant("applicant-result-api-auth", "Applicant Result Api Auth");
        Long applicationId = createSubmittedApplication(applicant, jobPostingId);

        mockMvc.perform(get("/applications/{applicationId}/stage-results", applicationId)
                        .with(authentication(employeeAuthentication("employee01", "ROLE_ADMIN"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/applications/{applicationId}/stage-results", applicationId)
                        .with(authentication(employeeAuthentication("employee02", "ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/applications/{applicationId}/stage-results", applicationId)
                        .with(anonymous()))
                .andExpect(status().is4xxClientError());
    }

    private void authenticate(Applicant applicant) {
        SecurityContextHolder.getContext().setAuthentication(applicantAuthentication(applicant));
    }

    private Authentication applicantAuthentication(Applicant applicant) {
        CustomUserDetails userDetails = CustomUserDetails.fromUser(
                applicant,
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private Authentication employeeAuthentication(String loginId, String authority) {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                loginId,
                "Recruit",
                "Employee User",
                List.of(new SimpleGrantedAuthority(authority))
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private Long createJobPosting() {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "2026 Applicant StageResult recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(
                        new JobPositionRequest("Backend", 2, 0),
                        new JobPositionRequest("Frontend", 1, 1)
                ),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)
        ));
        jobPostingService.publish(jobPostingId);
        return jobPostingId;
    }

    private Long createStage(Long jobPostingId, int stageOrder, boolean finalStage, LocalDateTime announcementAt) {
        return stageService.create(jobPostingId, new StageCreateRequest(
                "Stage " + stageOrder,
                StageType.DOCUMENT,
                stageOrder,
                announcementAt,
                finalStage
        ));
    }

    private Long createApplication(Applicant applicant, Long jobPostingId) {
        return jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );
    }

    private Long createSubmittedApplication(Applicant applicant, Long jobPostingId) {
        Long applicationId = createApplication(applicant, jobPostingId);
        jobApplicationService.submit(applicant.getId(), applicationId);
        return applicationId;
    }

    private void decideAndAnnounce(Long jobPostingId, Long stageId, StageResultStatus status) {
        stageService.start(jobPostingId, stageId);
        stageResultService.initialize(stageId);
        Long resultId = stageResultService.getResults(stageId).get(0).stageResultId();
        stageResultService.updateResult(stageId, resultId, new StageResultUpdateRequest(status, null, "internal"), "employee01");
        stageService.announce(jobPostingId, stageId);
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
