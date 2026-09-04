package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.support.BasicInfoTestSupport;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.StageCreateRequest;
import com.shinyoung.recruit.dto.request.StageResultBulkUpdateItemRequest;
import com.shinyoung.recruit.dto.request.StageResultBulkUpdateRequest;
import com.shinyoung.recruit.dto.request.StageResultUpdateRequest;
import com.shinyoung.recruit.dto.response.AdminStageResultResponse;
import com.shinyoung.recruit.dto.response.StageResultBulkUpdateResponse;
import com.shinyoung.recruit.dto.response.StageResultInitializeResponse;
import com.shinyoung.recruit.enumeration.CampusType;
import com.shinyoung.recruit.enumeration.DayNightType;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.GraduationStatus;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.InvalidStageResultException;
import com.shinyoung.recruit.exception.StageResultNotFoundException;
import com.shinyoung.recruit.exception.StageNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
    private static final String ACTOR = "employee01";

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
    private ApplicationBasicInfoRepository basicInfoRepository;

    @Autowired
    private StageRepository stageRepository;

    @Autowired
    private StageResultRepository stageResultRepository;

    @Autowired
    private ApplicationEducationRepository educationRepository;

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
        setStageStatus(announcedStageId, StageStatus.RESULT_ANNOUNCED);

        assertThatThrownBy(() -> stageResultService.initialize(announcedStageId))
                .isInstanceOf(InvalidStageResultException.class);

        Long closedPostingId = createJobPosting();
        Long closedStageId = createStage(closedPostingId);
        setStageStatus(closedStageId, StageStatus.CLOSED);

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
    void get_results_includes_grid_fields_and_previous_stage_result() {
        Long jobPostingId = createJobPosting();
        Long applicationId = createSubmittedApplication("stage-result-grid", jobPostingId);
        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();
        educationRepository.save(education(application, EducationLevel.UNIVERSITY, "Second University", 0));
        educationRepository.save(education(application, EducationLevel.HIGH_SCHOOL, "First High School", 1));
        Long documentStageId = createStage(jobPostingId, 0, false);
        Long interviewStageId = createStage(jobPostingId, 1, true);
        Long documentResultId = initializeAndFirstResultId(documentStageId);
        stageService.start(jobPostingId, documentStageId);
        stageResultService.updateResult(documentStageId, documentResultId,
                new StageResultUpdateRequest(StageResultStatus.PASSED, new BigDecimal("80"), null), ACTOR);
        stageResultService.initialize(interviewStageId);

        AdminStageResultResponse document = stageResultService.getResults(documentStageId).get(0);
        AdminStageResultResponse interview = stageResultService.getResults(interviewStageId).get(0);

        assertThat(document.decidedBy()).isEqualTo(ACTOR);
        assertThat(document.workLocation()).isEqualTo(application.getWorkLocationNameSnapshot());
        assertThat(document.applicationType()).isEqualTo(application.getJobPosition().getApplicationType());
        assertThat(document.finalEducationLevel()).isEqualTo(EducationLevel.UNIVERSITY);
        assertThat(document.finalSchoolName()).isEqualTo("Second University");
        assertThat(document.previousStageResultStatus()).isNull(); // 첫 단계
        assertThat(interview.previousStageResultStatus()).isEqualTo(StageResultStatus.PASSED);
        assertThat(interview.decidedBy()).isNull(); // 초기화 직후 미판정
    }

    @Test
    void update_result_response_carries_grid_fields() {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-grid-update", jobPostingId);
        Long stageId = createStage(jobPostingId);
        Long resultId = initializeAndFirstResultId(stageId);
        stageService.start(jobPostingId, stageId);

        AdminStageResultResponse response = stageResultService.updateResult(stageId, resultId,
                new StageResultUpdateRequest(StageResultStatus.HOLD, null, "검토"), ACTOR);

        assertThat(response.decidedBy()).isEqualTo(ACTOR);
        assertThat(response.previousStageResultStatus()).isNull();
        assertThat(response.finalEducationLevel()).isNull(); // 학력 미입력
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

    @Test
    void update_result_success_when_stage_is_in_progress() {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-update", jobPostingId);
        Long stageId = createStage(jobPostingId);
        stageService.start(jobPostingId, stageId);
        Long resultId = initializeAndFirstResultId(stageId);

        AdminStageResultResponse response = stageResultService.updateResult(
                stageId,
                resultId,
                new StageResultUpdateRequest(StageResultStatus.PASSED, new BigDecimal("95.5"), "passed"),
                ACTOR
        );
        StageResult saved = stageResultRepository.findById(resultId).orElseThrow();

        assertThat(response.resultStatus()).isEqualTo(StageResultStatus.PASSED);
        assertThat(response.score()).isEqualByComparingTo("95.5");
        assertThat(response.comment()).isEqualTo("passed");
        assertThat(response.decidedAt()).isNotNull();
        assertThat(saved.getDecidedAt()).isNotNull();
        assertThat(saved.getDecidedBy()).isEqualTo(ACTOR);
    }

    @Test
    void update_result_fails_when_stage_is_not_in_progress() {
        Long readyPostingId = createJobPosting();
        createSubmittedApplication("stage-result-ready-update", readyPostingId);
        Long readyStageId = createStage(readyPostingId);
        Long readyResultId = initializeAndFirstResultId(readyStageId);

        assertThatThrownBy(() -> stageResultService.updateResult(
                readyStageId,
                readyResultId,
                new StageResultUpdateRequest(StageResultStatus.PASSED, null, null),
                ACTOR
        )).isInstanceOf(InvalidStageResultException.class);

        Long announcedPostingId = createJobPosting();
        createSubmittedApplication("stage-result-announced-update", announcedPostingId);
        Long announcedStageId = createStage(announcedPostingId);
        Long announcedResultId = initializeAndFirstResultId(announcedStageId);
        setStageStatus(announcedStageId, StageStatus.RESULT_ANNOUNCED);

        assertThatThrownBy(() -> stageResultService.updateResult(
                announcedStageId,
                announcedResultId,
                new StageResultUpdateRequest(StageResultStatus.PASSED, null, null),
                ACTOR
        )).isInstanceOf(InvalidStageResultException.class);

        Long closedPostingId = createJobPosting();
        createSubmittedApplication("stage-result-closed-update", closedPostingId);
        Long closedStageId = createStage(closedPostingId);
        Long closedResultId = initializeAndFirstResultId(closedStageId);
        setStageStatus(closedStageId, StageStatus.CLOSED);

        assertThatThrownBy(() -> stageResultService.updateResult(
                closedStageId,
                closedResultId,
                new StageResultUpdateRequest(StageResultStatus.PASSED, null, null),
                ACTOR
        )).isInstanceOf(InvalidStageResultException.class);
    }

    @Test
    void update_result_fails_when_result_does_not_belong_to_stage() {
        Long firstPostingId = createJobPosting();
        Long secondPostingId = createJobPosting();
        createSubmittedApplication("stage-result-mismatch-update-1", firstPostingId);
        createSubmittedApplication("stage-result-mismatch-update-2", secondPostingId);
        Long firstStageId = createStage(firstPostingId);
        Long secondStageId = createStage(secondPostingId);
        stageService.start(firstPostingId, firstStageId);
        Long otherResultId = initializeAndFirstResultId(secondStageId);

        assertThatThrownBy(() -> stageResultService.updateResult(
                firstStageId,
                otherResultId,
                new StageResultUpdateRequest(StageResultStatus.PASSED, null, null),
                ACTOR
        )).isInstanceOf(StageResultNotFoundException.class);
    }

    @Test
    void update_result_fails_when_status_is_pending_or_comment_is_too_long() {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-invalid-update", jobPostingId);
        Long stageId = createStage(jobPostingId);
        stageService.start(jobPostingId, stageId);
        Long resultId = initializeAndFirstResultId(stageId);

        assertThatThrownBy(() -> stageResultService.updateResult(
                stageId,
                resultId,
                new StageResultUpdateRequest(StageResultStatus.PENDING, null, null),
                ACTOR
        )).isInstanceOf(InvalidStageResultException.class);
        assertThatThrownBy(() -> stageResultService.updateResult(
                stageId,
                resultId,
                new StageResultUpdateRequest(StageResultStatus.PASSED, null, "a".repeat(2001)),
                ACTOR
        )).isInstanceOf(InvalidStageResultException.class);
    }

    @Test
    void update_result_allows_nullable_score_and_comment() {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-null-update", jobPostingId);
        Long stageId = createStage(jobPostingId);
        stageService.start(jobPostingId, stageId);
        Long resultId = initializeAndFirstResultId(stageId);

        AdminStageResultResponse response = stageResultService.updateResult(
                stageId,
                resultId,
                new StageResultUpdateRequest(StageResultStatus.HOLD, null, null),
                ACTOR
        );

        assertThat(response.score()).isNull();
        assertThat(response.comment()).isNull();
        assertThat(response.resultStatus()).isEqualTo(StageResultStatus.HOLD);
    }

    @Test
    void update_result_fails_when_actor_is_blank() {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-blank-actor", jobPostingId);
        Long stageId = createStage(jobPostingId);
        stageService.start(jobPostingId, stageId);
        Long resultId = initializeAndFirstResultId(stageId);

        assertThatThrownBy(() -> stageResultService.updateResult(
                stageId,
                resultId,
                new StageResultUpdateRequest(StageResultStatus.PASSED, null, null),
                " "
        )).isInstanceOf(InvalidStageResultException.class);
    }

    @Test
    void bulk_update_success() {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-bulk-1", jobPostingId);
        createSubmittedApplication("stage-result-bulk-2", jobPostingId);
        Long stageId = createStage(jobPostingId);
        stageService.start(jobPostingId, stageId);
        List<Long> resultIds = initializeResultIds(stageId);

        StageResultBulkUpdateResponse response = stageResultService.bulkUpdateResults(
                stageId,
                new StageResultBulkUpdateRequest(List.of(
                        new StageResultBulkUpdateItemRequest(resultIds.get(0), StageResultStatus.PASSED, BigDecimal.ONE, "one"),
                        new StageResultBulkUpdateItemRequest(resultIds.get(1), StageResultStatus.FAILED, null, null)
                )),
                ACTOR
        );

        assertThat(response.updatedCount()).isEqualTo(2);
        assertThat(response.results()).extracting(AdminStageResultResponse::resultStatus)
                .containsExactly(StageResultStatus.PASSED, StageResultStatus.FAILED);
        assertThat(stageResultRepository.findByStageId(stageId))
                .extracting(StageResult::getResultStatus)
                .containsExactlyInAnyOrder(StageResultStatus.PASSED, StageResultStatus.FAILED);
        assertThat(stageResultRepository.findByStageId(stageId))
                .extracting(StageResult::getDecidedBy)
                .containsOnly(ACTOR);
    }

    @Test
    void bulk_update_fails_for_duplicate_empty_or_other_stage_result() {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-bulk-invalid-1", jobPostingId);
        Long stageId = createStage(jobPostingId);
        stageService.start(jobPostingId, stageId);
        Long resultId = initializeAndFirstResultId(stageId);

        assertThatThrownBy(() -> stageResultService.bulkUpdateResults(stageId, new StageResultBulkUpdateRequest(List.of()), ACTOR))
                .isInstanceOf(InvalidStageResultException.class);
        assertThatThrownBy(() -> stageResultService.bulkUpdateResults(stageId, new StageResultBulkUpdateRequest(List.of(
                new StageResultBulkUpdateItemRequest(resultId, StageResultStatus.PASSED, null, null),
                new StageResultBulkUpdateItemRequest(resultId, StageResultStatus.FAILED, null, null)
        )), ACTOR)).isInstanceOf(InvalidStageResultException.class);

        Long otherJobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-bulk-invalid-2", otherJobPostingId);
        Long otherStageId = createStage(otherJobPostingId);
        Long otherResultId = initializeAndFirstResultId(otherStageId);

        assertThatThrownBy(() -> stageResultService.bulkUpdateResults(stageId, new StageResultBulkUpdateRequest(List.of(
                new StageResultBulkUpdateItemRequest(otherResultId, StageResultStatus.PASSED, null, null)
        )), ACTOR)).isInstanceOf(StageResultNotFoundException.class);
    }

    @Test
    void bulk_update_fails_when_actor_is_blank() {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-bulk-blank-actor", jobPostingId);
        Long stageId = createStage(jobPostingId);
        stageService.start(jobPostingId, stageId);
        Long resultId = initializeAndFirstResultId(stageId);

        assertThatThrownBy(() -> stageResultService.bulkUpdateResults(
                stageId,
                new StageResultBulkUpdateRequest(List.of(
                        new StageResultBulkUpdateItemRequest(resultId, StageResultStatus.PASSED, null, null)
                )),
                " "
        )).isInstanceOf(InvalidStageResultException.class);
    }

    @Test
    void bulk_update_rolls_back_when_any_item_is_invalid() {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-bulk-rollback-1", jobPostingId);
        createSubmittedApplication("stage-result-bulk-rollback-2", jobPostingId);
        Long stageId = createStage(jobPostingId);
        stageService.start(jobPostingId, stageId);
        List<Long> resultIds = initializeResultIds(stageId);

        assertThatThrownBy(() -> stageResultService.bulkUpdateResults(stageId, new StageResultBulkUpdateRequest(List.of(
                new StageResultBulkUpdateItemRequest(resultIds.get(0), StageResultStatus.PASSED, null, null),
                new StageResultBulkUpdateItemRequest(resultIds.get(1), StageResultStatus.PENDING, null, null)
        )), ACTOR)).isInstanceOf(InvalidStageResultException.class);

        assertThat(stageResultRepository.findByStageId(stageId))
                .extracting(StageResult::getResultStatus)
                .containsOnly(StageResultStatus.PENDING);
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
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(applicationId).orElseThrow());
        jobApplicationService.submit(applicant.getId(), applicationId);
        return applicationId;
    }

    private Long initializeAndFirstResultId(Long stageId) {
        return initializeResultIds(stageId).get(0);
    }

    private List<Long> initializeResultIds(Long stageId) {
        stageResultService.initialize(stageId);
        return stageResultRepository.findByStageIdForAdminList(stageId).stream()
                .map(StageResult::getId)
                .toList();
    }

    private void setStageStatus(Long stageId, StageStatus stageStatus) {
        Stage stage = stageRepository.findById(stageId).orElseThrow();
        ReflectionTestUtils.setField(stage, "status", stageStatus);
    }

    private ApplicationEducation education(
            JobApplication application,
            EducationLevel educationLevel,
            String schoolName,
            Integer sortOrder
    ) {
        return ApplicationEducation.create(
                application,
                educationLevel,
                schoolName,
                educationLevel == EducationLevel.HIGH_SCHOOL ? null : "Computer Science",
                null,
                null,
                null,
                LocalDate.of(2021, 3, 1),
                LocalDate.of(2025, 2, 28),
                GraduationStatus.GRADUATED,
                DayNightType.DAY,
                CampusType.MAIN,
                false,
                "KR",
                sortOrder
        );
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
