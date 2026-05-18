package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.StageCreateRequest;
import com.shinyoung.recruit.dto.response.AdminStageResultResponse;
import com.shinyoung.recruit.dto.response.StageResultInitializeResponse;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.InvalidStageResultException;
import com.shinyoung.recruit.exception.StageNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class StageResultServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private StageResultService stageResultService;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private StageService stageService;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private StageRepository stageRepository;

    @Autowired
    private StageResultRepository stageResultRepository;

    @Test
    void initialize_ready_stage_creates_pending_results_for_submitted_applications_only() {
        Long jobPostingId = createJobPosting();
        Long submittedId = createSubmittedApplication("stage-result-submitted", jobPostingId);
        createDraftApplication("stage-result-draft", jobPostingId);
        Long withdrawnId = createSubmittedApplication("stage-result-withdrawn", jobPostingId);
        jobApplicationService.withdraw(applicantId("stage-result-withdrawn"), withdrawnId);
        Long stageId = createStage(jobPostingId);

        StageResultInitializeResponse response = stageResultService.initialize(stageId);

        assertThat(response.createdCount()).isEqualTo(1);
        assertThat(response.existingCount()).isZero();
        assertThat(response.skippedCount()).isEqualTo(2);
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).applicationId()).isEqualTo(submittedId);
        assertThat(response.results().get(0).resultStatus()).isEqualTo(StageResultStatus.PENDING);
        assertThat(stageResultRepository.countByStageIdAndResultStatus(stageId, StageResultStatus.PENDING)).isEqualTo(1);
        assertThat(stageResultRepository.existsByStageIdAndJobApplicationId(stageId, withdrawnId)).isFalse();
    }

    @Test
    void initialize_in_progress_stage_success() {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-progress", jobPostingId);
        Long stageId = createStage(jobPostingId);
        stageService.start(jobPostingId, stageId);

        StageResultInitializeResponse response = stageResultService.initialize(stageId);

        assertThat(response.createdCount()).isEqualTo(1);
        assertThat(response.results()).hasSize(1);
    }

    @Test
    void initialize_fails_when_stage_is_result_announced_or_closed() {
        Long announcedPostingId = createJobPosting();
        Long announcedStageId = createStage(announcedPostingId);
        stageService.start(announcedPostingId, announcedStageId);
        stageService.announce(announcedPostingId, announcedStageId);

        assertThatThrownBy(() -> stageResultService.initialize(announcedStageId))
                .isInstanceOf(InvalidStageResultException.class);

        Long closedPostingId = createJobPosting();
        Long closedStageId = createStage(closedPostingId);
        stageService.start(closedPostingId, closedStageId);
        stageService.announce(closedPostingId, closedStageId);
        stageService.close(closedPostingId, closedStageId);

        assertThatThrownBy(() -> stageResultService.initialize(closedStageId))
                .isInstanceOf(InvalidStageResultException.class);
    }

    @Test
    void initialize_is_idempotent_and_keeps_existing_results() {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-repeat-1", jobPostingId);
        createSubmittedApplication("stage-result-repeat-2", jobPostingId);
        Long stageId = createStage(jobPostingId);

        StageResultInitializeResponse first = stageResultService.initialize(stageId);
        StageResultInitializeResponse second = stageResultService.initialize(stageId);

        assertThat(first.createdCount()).isEqualTo(2);
        assertThat(second.createdCount()).isZero();
        assertThat(second.existingCount()).isEqualTo(2);
        assertThat(stageResultRepository.countByStageId(stageId)).isEqualTo(2);
    }

    @Test
    void initialize_ignores_applications_from_other_job_posting() {
        Long jobPostingId = createJobPosting();
        Long otherJobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-own", jobPostingId);
        Long otherApplicationId = createSubmittedApplication("stage-result-other", otherJobPostingId);
        Long stageId = createStage(jobPostingId);

        StageResultInitializeResponse response = stageResultService.initialize(stageId);

        assertThat(response.createdCount()).isEqualTo(1);
        assertThat(stageResultRepository.existsByStageIdAndJobApplicationId(stageId, otherApplicationId)).isFalse();
    }

    @Test
    void get_results_returns_only_selected_stage_results_ordered_by_submitted_at_desc() {
        Long jobPostingId = createJobPosting();
        Long firstApplicationId = createSubmittedApplication("stage-result-list-1", jobPostingId);
        Long secondApplicationId = createSubmittedApplication("stage-result-list-2", jobPostingId);
        Long firstStageId = createStage(jobPostingId);
        Long secondStageId = createStage(jobPostingId, 1, false);
        stageResultService.initialize(firstStageId);
        stageResultService.initialize(secondStageId);

        List<AdminStageResultResponse> responses = stageResultService.getResults(firstStageId);

        assertThat(responses).extracting(AdminStageResultResponse::stageId)
                .containsOnly(firstStageId);
        assertThat(responses).extracting(AdminStageResultResponse::applicationId)
                .containsExactly(secondApplicationId, firstApplicationId);
        assertThat(stageResultService.getResults(secondStageId)).hasSize(2);
    }

    @Test
    void get_results_fails_when_stage_not_found() {
        assertThatThrownBy(() -> stageResultService.getResults(99999L))
                .isInstanceOf(StageNotFoundException.class);
    }

    @Test
    void stage_result_factory_rejects_stage_and_application_from_different_job_postings() {
        Long firstJobPostingId = createJobPosting();
        Long secondJobPostingId = createJobPosting();
        Long stageId = createStage(firstJobPostingId);
        Long applicationId = createSubmittedApplication("stage-result-mismatch", secondJobPostingId);
        Stage stage = stageRepository.findById(stageId).orElseThrow();
        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();

        assertThatThrownBy(() -> StageResult.initialize(stage, application))
                .isInstanceOf(InvalidStageResultException.class);
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
        return createStage(jobPostingId, 0, true);
    }

    private Long createStage(Long jobPostingId, int stageOrder, boolean finalStage) {
        return stageService.create(jobPostingId, new StageCreateRequest(
                "Document screening " + stageOrder,
                StageType.DOCUMENT,
                stageOrder,
                LocalDateTime.of(2026, 7, 1, 10, 0),
                finalStage
        ));
    }

    private Long createDraftApplication(String loginId, Long jobPostingId) {
        Applicant applicant = createApplicant(loginId);
        return jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );
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

    private Long applicantId(String loginId) {
        return applicantRepository.findByLoginId(loginId).orElseThrow().getId();
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
