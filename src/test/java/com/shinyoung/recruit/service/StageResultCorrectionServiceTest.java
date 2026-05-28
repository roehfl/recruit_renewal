package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.entity.StageResultCorrectionHistory;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageResultCorrectionHistoryRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.StageCreateRequest;
import com.shinyoung.recruit.dto.request.StageResultCorrectionRequest;
import com.shinyoung.recruit.dto.request.StageResultUpdateRequest;
import com.shinyoung.recruit.dto.response.AdminStageResultResponse;
import com.shinyoung.recruit.dto.response.StageResultCorrectionHistoryResponse;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.InvalidStageResultException;
import com.shinyoung.recruit.exception.StageResultNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
class StageResultCorrectionServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );
    private static final String ACTOR = "employee01";

    @Autowired
    private StageResultCorrectionService correctionService;

    @Autowired
    private StageResultService stageResultService;

    @Autowired
    private StageService stageService;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private StageResultRepository stageResultRepository;

    @Autowired
    private StageResultCorrectionHistoryRepository correctionHistoryRepository;

    @Test
    void result_announced_stage_correction_updates_latest_result_and_saves_history() {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("correction-announced", jobPostingId);
        Long stageId = createStage(jobPostingId);
        Long resultId = createAnnouncedResult(jobPostingId, stageId, StageResultStatus.PASSED, "90", "original");

        AdminStageResultResponse response = correctionService.correctResult(
                stageId,
                resultId,
                new StageResultCorrectionRequest(StageResultStatus.FAILED, new BigDecimal("70.5"), "corrected", "wrong pass"),
                ACTOR
        );
        StageResult saved = stageResultRepository.findById(resultId).orElseThrow();
        List<StageResultCorrectionHistory> histories = correctionHistoryRepository
                .findByStageResultIdOrderByCorrectedAtDescIdDesc(resultId);

        assertThat(response.resultStatus()).isEqualTo(StageResultStatus.FAILED);
        assertThat(response.score()).isEqualByComparingTo("70.5");
        assertThat(response.comment()).isEqualTo("corrected");
        assertThat(saved.getResultStatus()).isEqualTo(StageResultStatus.FAILED);
        assertThat(saved.getDecidedAt()).isEqualTo(LocalDateTime.now(FIXED_CLOCK));
        assertThat(saved.getDecidedBy()).isEqualTo(ACTOR);
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getCorrectedBy()).isEqualTo(ACTOR);
        assertThat(histories.get(0).getPreviousStatus()).isEqualTo(StageResultStatus.PASSED);
        assertThat(histories.get(0).getNewStatus()).isEqualTo(StageResultStatus.FAILED);
        assertThat(histories.get(0).getPreviousScore()).isEqualByComparingTo("90");
        assertThat(histories.get(0).getNewScore()).isEqualByComparingTo("70.5");
        assertThat(histories.get(0).getPreviousComment()).isEqualTo("original");
        assertThat(histories.get(0).getNewComment()).isEqualTo("corrected");
        assertThat(histories.get(0).getPreviousDecidedAt()).isEqualTo(LocalDateTime.now(FIXED_CLOCK));
        assertThat(histories.get(0).getNewDecidedAt()).isEqualTo(LocalDateTime.now(FIXED_CLOCK));
        assertThat(stageResultRepository.findByStageId(stageId)).hasSize(1);
    }

    @Test
    void closed_stage_correction_success() {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("correction-closed", jobPostingId);
        Long stageId = createStage(jobPostingId);
        Long resultId = createAnnouncedResult(jobPostingId, stageId, StageResultStatus.PASSED, null, null);
        stageService.close(jobPostingId, stageId);

        AdminStageResultResponse response = correctionService.correctResult(
                stageId,
                resultId,
                new StageResultCorrectionRequest(StageResultStatus.HOLD, null, "hold", "post close exception"),
                ACTOR
        );

        assertThat(response.resultStatus()).isEqualTo(StageResultStatus.HOLD);
        assertThat(correctionHistoryRepository.findByStageResultIdOrderByCorrectedAtDescIdDesc(resultId)).hasSize(1);
    }

    @Test
    void correction_fails_when_stage_is_ready_or_in_progress() {
        Long readyPostingId = createJobPosting();
        createSubmittedApplication("correction-ready", readyPostingId);
        Long readyStageId = createStage(readyPostingId);
        Long readyResultId = initializeAndFirstResultId(readyStageId);

        assertThatThrownBy(() -> correctionService.correctResult(
                readyStageId,
                readyResultId,
                new StageResultCorrectionRequest(StageResultStatus.PASSED, null, null, "too early"),
                ACTOR
        )).isInstanceOf(InvalidStageResultException.class);

        Long progressPostingId = createJobPosting();
        createSubmittedApplication("correction-progress", progressPostingId);
        Long progressStageId = createStage(progressPostingId);
        stageService.start(progressPostingId, progressStageId);
        Long progressResultId = initializeAndFirstResultId(progressStageId);

        assertThatThrownBy(() -> correctionService.correctResult(
                progressStageId,
                progressResultId,
                new StageResultCorrectionRequest(StageResultStatus.PASSED, null, null, "use normal update"),
                ACTOR
        )).isInstanceOf(InvalidStageResultException.class);
    }

    @Test
    void correction_fails_when_reason_is_blank_or_result_mismatches_stage() {
        Long firstPostingId = createJobPosting();
        createSubmittedApplication("correction-invalid-1", firstPostingId);
        Long firstStageId = createStage(firstPostingId);
        Long firstResultId = createAnnouncedResult(firstPostingId, firstStageId, StageResultStatus.PASSED, null, null);

        assertThatThrownBy(() -> correctionService.correctResult(
                firstStageId,
                firstResultId,
                new StageResultCorrectionRequest(StageResultStatus.FAILED, null, null, " "),
                ACTOR
        )).isInstanceOf(InvalidStageResultException.class);

        Long secondPostingId = createJobPosting();
        createSubmittedApplication("correction-invalid-2", secondPostingId);
        Long secondStageId = createStage(secondPostingId);
        Long secondResultId = createAnnouncedResult(secondPostingId, secondStageId, StageResultStatus.PASSED, null, null);

        assertThatThrownBy(() -> correctionService.correctResult(
                firstStageId,
                secondResultId,
                new StageResultCorrectionRequest(StageResultStatus.FAILED, null, null, "wrong stage"),
                ACTOR
        )).isInstanceOf(StageResultNotFoundException.class);
    }

    @Test
    void correction_fails_when_actor_is_blank() {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("correction-blank-actor", jobPostingId);
        Long stageId = createStage(jobPostingId);
        Long resultId = createAnnouncedResult(jobPostingId, stageId, StageResultStatus.PASSED, null, null);

        assertThatThrownBy(() -> correctionService.correctResult(
                stageId,
                resultId,
                new StageResultCorrectionRequest(StageResultStatus.FAILED, null, null, "wrong result"),
                " "
        )).isInstanceOf(InvalidStageResultException.class);
    }

    @Test
    void correction_histories_are_accumulated_and_returned_latest_first() {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("correction-history", jobPostingId);
        Long stageId = createStage(jobPostingId);
        Long resultId = createAnnouncedResult(jobPostingId, stageId, StageResultStatus.PASSED, null, "first");

        correctionService.correctResult(
                stageId,
                resultId,
                new StageResultCorrectionRequest(StageResultStatus.FAILED, BigDecimal.ONE, "second", "first correction"),
                ACTOR
        );
        correctionService.correctResult(
                stageId,
                resultId,
                new StageResultCorrectionRequest(StageResultStatus.HOLD, BigDecimal.TEN, "third", "second correction"),
                ACTOR
        );

        List<StageResultCorrectionHistoryResponse> histories = correctionService.getHistories(stageId, resultId);

        assertThat(histories).hasSize(2);
        assertThat(histories.get(0).reason()).isEqualTo("second correction");
        assertThat(histories.get(0).previousStatus()).isEqualTo(StageResultStatus.FAILED);
        assertThat(histories.get(0).newStatus()).isEqualTo(StageResultStatus.HOLD);
        assertThat(histories.get(1).reason()).isEqualTo("first correction");
        assertThat(histories.get(1).previousStatus()).isEqualTo(StageResultStatus.PASSED);
        assertThat(histories.get(1).newStatus()).isEqualTo(StageResultStatus.FAILED);
    }

    private Long createJobPosting() {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "2026 StageResult correction recruitment",
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

    private Long createAnnouncedResult(
            Long jobPostingId,
            Long stageId,
            StageResultStatus status,
            String score,
            String comment
    ) {
        stageService.start(jobPostingId, stageId);
        Long resultId = initializeAndFirstResultId(stageId);
        stageResultService.updateResult(
                stageId,
                resultId,
                new StageResultUpdateRequest(status, score == null ? null : new BigDecimal(score), comment),
                ACTOR
        );
        stageService.announce(jobPostingId, stageId);
        return resultId;
    }

    private Long initializeAndFirstResultId(Long stageId) {
        stageResultService.initialize(stageId);
        return stageResultRepository.findByStageIdForAdminList(stageId).stream()
                .map(StageResult::getId)
                .findFirst()
                .orElseThrow();
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
