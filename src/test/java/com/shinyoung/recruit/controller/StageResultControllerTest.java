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
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.service.JobApplicationService;
import com.shinyoung.recruit.service.JobPostingService;
import com.shinyoung.recruit.service.StageService;
import com.shinyoung.recruit.service.StageResultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
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
        stageService.start(announcedPostingId, announcedStageId);
        stageService.announce(announcedPostingId, announcedStageId);

        mockMvc.perform(post("/admin/stages/{stageId}/results/initialize", announcedStageId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());

        Long closedPostingId = createJobPosting();
        Long closedStageId = createStage(closedPostingId);
        stageService.start(closedPostingId, closedStageId);
        stageService.announce(closedPostingId, closedStageId);
        stageService.close(closedPostingId, closedStageId);

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
        mockMvc.perform(post("/admin/stages/{stageId}/results/{resultId}", 1L, 1L))
                .andExpect(status().isNotFound());
    }

    private Long createJobPosting() {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "2026 StageResult recruitment",
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

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return FIXED_CLOCK;
        }
    }
}
