package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.StageCreateRequest;
import com.shinyoung.recruit.dto.request.StageResultUpdateRequest;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.enumeration.StageStatus;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.JobApplicationService;
import com.shinyoung.recruit.service.JobPostingService;
import com.shinyoung.recruit.service.StageService;
import com.shinyoung.recruit.service.StageResultService;
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
import org.springframework.test.util.ReflectionTestUtils;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class StageResultControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private StageService stageService;

    @Autowired
    private StageResultService stageResultService;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private StageRepository stageRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").with(authentication(employeeAuthentication("employee01", "ROLE_ADMIN"))))
                .build();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void initialize_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Long applicationId = createSubmittedApplication("stage-result-api-init", jobPostingId);
        Long stageId = createStage(jobPostingId);

        mockMvc.perform(post("/admin/stages/{stageId}/results/initialize", stageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.stageId").value(stageId))
                .andExpect(jsonPath("$.data.createdCount").value(1))
                .andExpect(jsonPath("$.data.existingCount").value(0))
                .andExpect(jsonPath("$.data.results[0].applicationId").value(applicationId))
                .andExpect(jsonPath("$.data.results[0].resultStatus").value("PENDING"));
    }

    @Test
    void get_results_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Long applicationId = createSubmittedApplication("stage-result-api-list", jobPostingId);
        Long stageId = createStage(jobPostingId);
        stageResultService.initialize(stageId);

        mockMvc.perform(get("/admin/stages/{stageId}/results", stageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data[0].stageId").value(stageId))
                .andExpect(jsonPath("$.data[0].applicationId").value(applicationId))
                .andExpect(jsonPath("$.data[0].applicantName").exists())
                .andExpect(jsonPath("$.data[0].jobPositionName").value("Backend"))
                .andExpect(jsonPath("$.data[0].applicationStatus").value("SUBMITTED"));
    }

    @Test
    void update_result_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-api-update", jobPostingId);
        Long stageId = createStage(jobPostingId);
        stageService.start(jobPostingId, stageId);
        Long resultId = initializeAndFirstResultId(stageId);
        authenticateEmployee("employee01");

        mockMvc.perform(post("/admin/stages/{stageId}/results/{resultId}", stageId, resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resultStatus": "PASSED",
                                  "score": 91.5,
                                  "comment": "passed"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.stageResultId").value(resultId))
                .andExpect(jsonPath("$.data.resultStatus").value("PASSED"))
                .andExpect(jsonPath("$.data.score").value(91.5))
                .andExpect(jsonPath("$.data.comment").value("passed"))
                .andExpect(jsonPath("$.data.decidedAt").exists());
    }

    @Test
    void bulk_update_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-api-bulk-1", jobPostingId);
        createSubmittedApplication("stage-result-api-bulk-2", jobPostingId);
        Long stageId = createStage(jobPostingId);
        stageService.start(jobPostingId, stageId);
        List<Long> resultIds = initializeResultIds(stageId);
        authenticateEmployee("employee01");

        mockMvc.perform(post("/admin/stages/{stageId}/results/bulk", stageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "results": [
                                    {
                                      "stageResultId": %d,
                                      "resultStatus": "PASSED",
                                      "score": 90,
                                      "comment": "pass"
                                    },
                                    {
                                      "stageResultId": %d,
                                      "resultStatus": "FAILED",
                                      "score": null,
                                      "comment": null
                                    }
                                  ]
                                }
                                """.formatted(resultIds.get(0), resultIds.get(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.stageId").value(stageId))
                .andExpect(jsonPath("$.data.updatedCount").value(2))
                .andExpect(jsonPath("$.data.results[0].resultStatus").value("PASSED"))
                .andExpect(jsonPath("$.data.results[1].resultStatus").value("FAILED"));
    }

    @Test
    void correct_result_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-api-correct", jobPostingId);
        Long stageId = createStage(jobPostingId);
        Long resultId = createAnnouncedResult(jobPostingId, stageId);
        authenticateEmployee("employee01");

        mockMvc.perform(post("/admin/stages/{stageId}/results/{resultId}/correct", stageId, resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resultStatus": "FAILED",
                                  "score": 71.5,
                                  "comment": "corrected",
                                  "reason": "wrong result"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.stageResultId").value(resultId))
                .andExpect(jsonPath("$.data.resultStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.score").value(71.5))
                .andExpect(jsonPath("$.data.comment").value("corrected"))
                .andExpect(jsonPath("$.data.decidedAt").exists());
    }

    @Test
    void get_correction_histories_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-api-history", jobPostingId);
        Long stageId = createStage(jobPostingId);
        Long resultId = createAnnouncedResult(jobPostingId, stageId);
        authenticateEmployee("employee01");

        mockMvc.perform(post("/admin/stages/{stageId}/results/{resultId}/correct", stageId, resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resultStatus": "FAILED",
                                  "score": 71.5,
                                  "comment": "corrected",
                                  "reason": "wrong result"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/stages/{stageId}/results/{resultId}/histories", stageId, resultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data[0].historyId").exists())
                .andExpect(jsonPath("$.data[0].stageResultId").value(resultId))
                .andExpect(jsonPath("$.data[0].correctedAt").exists())
                .andExpect(jsonPath("$.data[0].correctedBy").value("employee01"))
                .andExpect(jsonPath("$.data[0].reason").value("wrong result"))
                .andExpect(jsonPath("$.data[0].previousStatus").value("PASSED"))
                .andExpect(jsonPath("$.data[0].newStatus").value("FAILED"))
                .andExpect(jsonPath("$.data[0].previousScore").value(90))
                .andExpect(jsonPath("$.data[0].newScore").value(71.5))
                .andExpect(jsonPath("$.data[0].previousComment").value("passed"))
                .andExpect(jsonPath("$.data[0].newComment").value("corrected"))
                .andExpect(jsonPath("$.data[0].previousDecidedAt").exists())
                .andExpect(jsonPath("$.data[0].newDecidedAt").exists());
    }

    @Test
    void correction_validation_failure_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-api-correction-invalid", jobPostingId);
        Long stageId = createStage(jobPostingId);
        Long resultId = createAnnouncedResult(jobPostingId, stageId);
        authenticateEmployee("employee01");

        mockMvc.perform(post("/admin/stages/{stageId}/results/{resultId}/correct", stageId, resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resultStatus": "FAILED",
                                  "score": null,
                                  "comment": null,
                                  "reason": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void correction_fails_when_stage_is_in_progress() throws Exception {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-api-correction-progress", jobPostingId);
        Long stageId = createStage(jobPostingId);
        stageService.start(jobPostingId, stageId);
        Long resultId = initializeAndFirstResultId(stageId);
        authenticateEmployee("employee01");

        mockMvc.perform(post("/admin/stages/{stageId}/results/{resultId}/correct", stageId, resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resultStatus": "PASSED",
                                  "score": null,
                                  "comment": null,
                                  "reason": "too early"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void update_validation_failure_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-api-invalid", jobPostingId);
        Long stageId = createStage(jobPostingId);
        stageService.start(jobPostingId, stageId);
        Long resultId = initializeAndFirstResultId(stageId);
        authenticateEmployee("employee01");

        mockMvc.perform(post("/admin/stages/{stageId}/results/{resultId}", stageId, resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resultStatus": null,
                                  "score": null,
                                  "comment": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void result_not_found_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Long stageId = createStage(jobPostingId);
        stageService.start(jobPostingId, stageId);
        authenticateEmployee("employee01");

        mockMvc.perform(post("/admin/stages/{stageId}/results/{resultId}", stageId, 99999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resultStatus": "PASSED",
                                  "score": null,
                                  "comment": null
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void update_result_fails_when_principal_is_applicant_or_missing() throws Exception {
        Long jobPostingId = createJobPosting();
        Applicant applicant = createApplicant("stage-result-api-applicant-principal");
        createSubmittedApplication("stage-result-api-update-auth", jobPostingId);
        Long stageId = createStage(jobPostingId);
        stageService.start(jobPostingId, stageId);
        Long resultId = initializeAndFirstResultId(stageId);

        mockMvc.perform(post("/admin/stages/{stageId}/results/{resultId}", stageId, resultId)
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resultStatus": "PASSED",
                                  "score": null,
                                  "comment": null
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access is denied."));

        mockMvc.perform(post("/admin/stages/{stageId}/results/{resultId}", stageId, resultId)
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resultStatus": "PASSED",
                                  "score": null,
                                  "comment": null
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication is required."));
    }

    @Test
    void update_result_fails_when_employee_has_no_admin_authority() throws Exception {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-api-update-low-role", jobPostingId);
        Long stageId = createStage(jobPostingId);
        stageService.start(jobPostingId, stageId);
        Long resultId = initializeAndFirstResultId(stageId);

        mockMvc.perform(post("/admin/stages/{stageId}/results/{resultId}", stageId, resultId)
                        .with(authentication(employeeAuthentication("employee02", "ROLE_EMPLOYEE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resultStatus": "PASSED",
                                  "score": null,
                                  "comment": null
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access is denied."));
    }

    @Test
    void correct_result_fails_when_principal_is_applicant_or_missing() throws Exception {
        Long jobPostingId = createJobPosting();
        Applicant applicant = createApplicant("stage-result-api-correct-applicant");
        createSubmittedApplication("stage-result-api-correct-auth", jobPostingId);
        Long stageId = createStage(jobPostingId);
        Long resultId = createAnnouncedResult(jobPostingId, stageId);

        mockMvc.perform(post("/admin/stages/{stageId}/results/{resultId}/correct", stageId, resultId)
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resultStatus": "FAILED",
                                  "score": null,
                                  "comment": null,
                                  "reason": "wrong result"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access is denied."));

        mockMvc.perform(post("/admin/stages/{stageId}/results/{resultId}/correct", stageId, resultId)
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resultStatus": "FAILED",
                                  "score": null,
                                  "comment": null,
                                  "reason": "wrong result"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication is required."));
    }

    @Test
    void stage_not_found_returns_api_response() throws Exception {
        mockMvc.perform(get("/admin/stages/{stageId}/results", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void initialize_fails_when_stage_is_result_announced_or_closed() throws Exception {
        Long announcedPostingId = createJobPosting();
        Long announcedStageId = createStage(announcedPostingId);
        setStageStatus(announcedStageId, StageStatus.RESULT_ANNOUNCED);

        mockMvc.perform(post("/admin/stages/{stageId}/results/initialize", announcedStageId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());

        Long closedPostingId = createJobPosting();
        Long closedStageId = createStage(closedPostingId);
        setStageStatus(closedStageId, StageStatus.CLOSED);

        mockMvc.perform(post("/admin/stages/{stageId}/results/initialize", closedStageId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void unsupported_methods_are_not_added() throws Exception {
        mockMvc.perform(put("/admin/stages/{stageId}/results", 1L))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/admin/stages/{stageId}/results", 1L))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch("/admin/stages/{stageId}/results/{resultId}", 1L, 1L))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/admin/stages/{stageId}/results/{resultId}/correct", 1L, 1L))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch("/admin/stages/{stageId}/results/{resultId}/correct", 1L, 1L))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/admin/stages/{stageId}/results/{resultId}/histories", 1L, 1L))
                .andExpect(status().isMethodNotAllowed());
    }

    private Long createJobPosting() {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "2026 StageResult recruitment",
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

    private Long createStage(Long jobPostingId) {
        return stageService.create(jobPostingId, new StageCreateRequest(
                "Document screening",
                StageType.DOCUMENT,
                0,
                LocalDateTime.of(2026, 7, 1, 10, 0),
                true
        ));
    }

    private Long createSubmittedApplication(String loginId, Long jobPostingId) {
        Applicant applicant = createApplicant(loginId);
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );
        jobApplicationService.submit(applicant.getId(), applicationId);
        return applicationId;
    }

    private Long initializeAndFirstResultId(Long stageId) {
        return initializeResultIds(stageId).get(0);
    }

    private List<Long> initializeResultIds(Long stageId) {
        stageResultService.initialize(stageId);
        return stageResultService.getResults(stageId).stream()
                .map(response -> response.stageResultId())
                .toList();
    }

    private Long createAnnouncedResult(Long jobPostingId, Long stageId) {
        stageService.start(jobPostingId, stageId);
        Long resultId = initializeAndFirstResultId(stageId);
        stageResultService.updateResult(
                stageId,
                resultId,
                new StageResultUpdateRequest(StageResultStatus.PASSED, new java.math.BigDecimal("90"), "passed"),
                "employee01"
        );
        stageService.announce(jobPostingId, stageId);
        return resultId;
    }

    private void authenticateEmployee(String loginId) {
        SecurityContextHolder.getContext().setAuthentication(employeeAuthentication(loginId, "ROLE_ADMIN"));
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

    private void authenticateApplicant(Applicant applicant) {
        SecurityContextHolder.getContext().setAuthentication(applicantAuthentication(applicant));
    }

    private Authentication applicantAuthentication(Applicant applicant) {
        CustomUserDetails userDetails = CustomUserDetails.fromUser(
                applicant,
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private Applicant createApplicant(String loginId) {
        String ci = loginId + "-ci";
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId(loginId);
        applicant.setName("User " + loginId);
        applicant.setUserName("Applicant " + loginId);
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

    private void setStageStatus(Long stageId, StageStatus stageStatus) {
        ReflectionTestUtils.setField(stageRepository.findById(stageId).orElseThrow(), "status", stageStatus);
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
