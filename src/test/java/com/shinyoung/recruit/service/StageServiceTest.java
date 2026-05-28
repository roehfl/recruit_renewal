package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.StageCreateRequest;
import com.shinyoung.recruit.dto.request.StageOrderRequest;
import com.shinyoung.recruit.dto.request.StageReorderRequest;
import com.shinyoung.recruit.dto.request.StageResultUpdateRequest;
import com.shinyoung.recruit.dto.request.StageUpdateRequest;
import com.shinyoung.recruit.dto.response.AdminStageResultResponse;
import com.shinyoung.recruit.dto.response.StageDetailResponse;
import com.shinyoung.recruit.dto.response.StageListResponse;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.InvalidStageException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import com.shinyoung.recruit.exception.StageNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class StageServiceTest {

    @Autowired
    private StageService stageService;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private StageResultService stageResultService;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private StageRepository stageRepository;

    @Test
    void create_stage_success() {
        Long jobPostingId = createJobPosting();

        Long stageId = stageService.create(jobPostingId, createStageRequest(0, true));

        StageDetailResponse detail = stageService.getStage(jobPostingId, stageId);
        assertThat(detail.stageName()).isEqualTo("Document screening");
        assertThat(detail.stageType()).isEqualTo(StageType.DOCUMENT);
        assertThat(detail.stageOrder()).isZero();
        assertThat(detail.finalStage()).isTrue();
    }

    @Test
    void created_stage_status_is_ready() {
        Long jobPostingId = createJobPosting();

        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));

        assertThat(stageService.getStage(jobPostingId, stageId).status()).isEqualTo(StageStatus.READY);
    }

    @Test
    void create_stage_fails_when_job_posting_not_found() {
        assertThatThrownBy(() -> stageService.create(99999L, createStageRequest(0, false)))
                .isInstanceOf(JobPostingNotFoundException.class);
    }

    @Test
    void create_stage_fails_when_job_posting_is_closed() {
        Long jobPostingId = createJobPosting();
        jobPostingService.publish(jobPostingId);
        jobPostingService.close(jobPostingId);

        assertThatThrownBy(() -> stageService.create(jobPostingId, createStageRequest(0, false)))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void create_stage_fails_when_stage_name_is_blank() {
        Long jobPostingId = createJobPosting();

        StageCreateRequest request = new StageCreateRequest(
                " ",
                StageType.DOCUMENT,
                0,
                LocalDateTime.of(2026, 6, 10, 10, 0),
                false
        );

        assertThatThrownBy(() -> stageService.create(jobPostingId, request))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void create_stage_fails_when_stage_type_is_null() {
        Long jobPostingId = createJobPosting();

        StageCreateRequest request = new StageCreateRequest(
                "Document screening",
                null,
                0,
                LocalDateTime.of(2026, 6, 10, 10, 0),
                false
        );

        assertThatThrownBy(() -> stageService.create(jobPostingId, request))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void create_stage_fails_when_stage_order_is_null() {
        Long jobPostingId = createJobPosting();

        StageCreateRequest request = new StageCreateRequest(
                "Document screening",
                StageType.DOCUMENT,
                null,
                LocalDateTime.of(2026, 6, 10, 10, 0),
                false
        );

        assertThatThrownBy(() -> stageService.create(jobPostingId, request))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void create_stage_fails_when_stage_order_is_negative() {
        Long jobPostingId = createJobPosting();

        assertThatThrownBy(() -> stageService.create(jobPostingId, createStageRequest(-1, false)))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void create_stage_fails_when_stage_order_is_duplicated() {
        Long jobPostingId = createJobPosting();
        stageService.create(jobPostingId, createStageRequest(0, false));

        assertThatThrownBy(() -> stageService.create(jobPostingId, createStageRequest(0, false)))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void create_stage_fails_when_final_stage_is_duplicated() {
        Long jobPostingId = createJobPosting();
        stageService.create(jobPostingId, createStageRequest(0, true));

        assertThatThrownBy(() -> stageService.create(jobPostingId, createStageRequest(1, true)))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void get_stages_returns_stage_order_asc_and_id_asc() {
        Long jobPostingId = createJobPosting();
        stageService.create(jobPostingId, createStageRequest(2, false));
        stageService.create(jobPostingId, createStageRequest(0, false));
        stageService.create(jobPostingId, createStageRequest(1, true));

        List<StageListResponse> stages = stageService.getStages(jobPostingId);

        assertThat(stages).extracting(StageListResponse::stageOrder)
                .containsExactly(0, 1, 2);
    }

    @Test
    void get_stage_success() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));

        StageDetailResponse detail = stageService.getStage(jobPostingId, stageId);

        assertThat(detail.id()).isEqualTo(stageId);
        assertThat(detail.jobPostingId()).isEqualTo(jobPostingId);
    }

    @Test
    void get_stage_fails_when_stage_belongs_to_other_job_posting() {
        Long firstJobPostingId = createJobPosting();
        Long secondJobPostingId = createJobPosting();
        Long stageId = stageService.create(firstJobPostingId, createStageRequest(0, false));

        assertThatThrownBy(() -> stageService.getStage(secondJobPostingId, stageId))
                .isInstanceOf(StageNotFoundException.class);
    }

    @Test
    void update_ready_stage_success() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));

        Long updatedId = stageService.update(jobPostingId, stageId, updateStageRequest(1, true));

        StageDetailResponse detail = stageService.getStage(jobPostingId, updatedId);
        assertThat(detail.stageName()).isEqualTo("First interview");
        assertThat(detail.stageType()).isEqualTo(StageType.FIRST_INTERVIEW);
        assertThat(detail.stageOrder()).isEqualTo(1);
        assertThat(detail.finalStage()).isTrue();
        assertThat(detail.status()).isEqualTo(StageStatus.READY);
    }

    @Test
    void update_stage_fails_when_stage_order_is_duplicated() {
        Long jobPostingId = createJobPosting();
        stageService.create(jobPostingId, createStageRequest(0, false));
        Long stageId = stageService.create(jobPostingId, createStageRequest(1, false));

        assertThatThrownBy(() -> stageService.update(jobPostingId, stageId, updateStageRequest(0, false)))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void update_stage_fails_when_final_stage_is_duplicated() {
        Long jobPostingId = createJobPosting();
        stageService.create(jobPostingId, createStageRequest(0, true));
        Long stageId = stageService.create(jobPostingId, createStageRequest(1, false));

        assertThatThrownBy(() -> stageService.update(jobPostingId, stageId, updateStageRequest(1, true)))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void update_stage_fails_when_job_posting_is_closed() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        jobPostingService.publish(jobPostingId);
        jobPostingService.close(jobPostingId);

        assertThatThrownBy(() -> stageService.update(jobPostingId, stageId, updateStageRequest(0, false)))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void update_stage_fails_when_stage_not_found() {
        Long jobPostingId = createJobPosting();

        assertThatThrownBy(() -> stageService.update(jobPostingId, 99999L, updateStageRequest(0, false)))
                .isInstanceOf(StageNotFoundException.class);
    }

    @Test
    void update_stage_fails_when_stage_status_is_not_ready() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        Stage stage = stageRepository.findById(stageId).orElseThrow();
        ReflectionTestUtils.setField(stage, "status", StageStatus.IN_PROGRESS);

        assertThatThrownBy(() -> stageService.update(jobPostingId, stageId, updateStageRequest(0, false)))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void reorder_success() {
        Long jobPostingId = createJobPosting();
        Long firstStageId = stageService.create(jobPostingId, createStageRequest(0, false));
        Long secondStageId = stageService.create(jobPostingId, createStageRequest(1, false));
        Long thirdStageId = stageService.create(jobPostingId, createStageRequest(2, true));

        List<StageListResponse> reordered = stageService.reorder(jobPostingId, new StageReorderRequest(List.of(
                new StageOrderRequest(firstStageId, 2),
                new StageOrderRequest(secondStageId, 0),
                new StageOrderRequest(thirdStageId, 1)
        )));

        assertThat(reordered).extracting(StageListResponse::id)
                .containsExactly(secondStageId, thirdStageId, firstStageId);
        assertThat(reordered).extracting(StageListResponse::stageOrder)
                .containsExactly(0, 1, 2);
    }

    @Test
    void reorder_fails_when_stage_is_missing() {
        Long jobPostingId = createJobPosting();
        Long firstStageId = stageService.create(jobPostingId, createStageRequest(0, false));
        stageService.create(jobPostingId, createStageRequest(1, true));

        assertThatThrownBy(() -> stageService.reorder(jobPostingId, new StageReorderRequest(List.of(
                new StageOrderRequest(firstStageId, 0)
        )))).isInstanceOf(InvalidStageException.class);
    }

    @Test
    void reorder_fails_when_stage_id_is_duplicated() {
        Long jobPostingId = createJobPosting();
        Long firstStageId = stageService.create(jobPostingId, createStageRequest(0, false));
        stageService.create(jobPostingId, createStageRequest(1, true));

        assertThatThrownBy(() -> stageService.reorder(jobPostingId, new StageReorderRequest(List.of(
                new StageOrderRequest(firstStageId, 0),
                new StageOrderRequest(firstStageId, 1)
        )))).isInstanceOf(InvalidStageException.class);
    }

    @Test
    void reorder_fails_when_stage_order_is_duplicated() {
        Long jobPostingId = createJobPosting();
        Long firstStageId = stageService.create(jobPostingId, createStageRequest(0, false));
        Long secondStageId = stageService.create(jobPostingId, createStageRequest(1, true));

        assertThatThrownBy(() -> stageService.reorder(jobPostingId, new StageReorderRequest(List.of(
                new StageOrderRequest(firstStageId, 0),
                new StageOrderRequest(secondStageId, 0)
        )))).isInstanceOf(InvalidStageException.class);
    }

    @Test
    void reorder_fails_when_stage_belongs_to_other_job_posting() {
        Long firstJobPostingId = createJobPosting();
        Long secondJobPostingId = createJobPosting();
        Long firstStageId = stageService.create(firstJobPostingId, createStageRequest(0, false));
        Long otherStageId = stageService.create(secondJobPostingId, createStageRequest(0, false));

        assertThatThrownBy(() -> stageService.reorder(firstJobPostingId, new StageReorderRequest(List.of(
                new StageOrderRequest(firstStageId, 0),
                new StageOrderRequest(otherStageId, 1)
        )))).isInstanceOf(StageNotFoundException.class);
    }

    @Test
    void reorder_fails_when_stage_id_does_not_exist() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));

        assertThatThrownBy(() -> stageService.reorder(jobPostingId, new StageReorderRequest(List.of(
                new StageOrderRequest(stageId, 0),
                new StageOrderRequest(99999L, 1)
        )))).isInstanceOf(StageNotFoundException.class);
    }

    @Test
    void reorder_fails_when_job_posting_is_closed() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        jobPostingService.publish(jobPostingId);
        jobPostingService.close(jobPostingId);

        assertThatThrownBy(() -> stageService.reorder(jobPostingId, new StageReorderRequest(List.of(
                new StageOrderRequest(stageId, 0)
        )))).isInstanceOf(InvalidStageException.class);
    }

    @Test
    void reorder_fails_when_non_ready_stage_is_included() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        Stage stage = stageRepository.findById(stageId).orElseThrow();
        ReflectionTestUtils.setField(stage, "status", StageStatus.IN_PROGRESS);

        assertThatThrownBy(() -> stageService.reorder(jobPostingId, new StageReorderRequest(List.of(
                new StageOrderRequest(stageId, 0)
        )))).isInstanceOf(InvalidStageException.class);
    }

    @Test
    void start_success_when_job_posting_is_published_and_stage_is_ready() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        jobPostingService.publish(jobPostingId);

        stageService.start(jobPostingId, stageId);

        assertThat(stageService.getStage(jobPostingId, stageId).status()).isEqualTo(StageStatus.IN_PROGRESS);
    }

    @Test
    void start_fails_when_job_posting_is_draft() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));

        assertThatThrownBy(() -> stageService.start(jobPostingId, stageId))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void start_fails_when_job_posting_is_closed() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        jobPostingService.publish(jobPostingId);
        jobPostingService.close(jobPostingId);

        assertThatThrownBy(() -> stageService.start(jobPostingId, stageId))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void start_fails_when_stage_is_not_ready() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        jobPostingService.publish(jobPostingId);
        stageService.start(jobPostingId, stageId);

        assertThatThrownBy(() -> stageService.start(jobPostingId, stageId))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void announce_success_when_stage_is_in_progress() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        jobPostingService.publish(jobPostingId);
        stageService.start(jobPostingId, stageId);
        prepareDecidedStageResult(jobPostingId, stageId, "announce-success");

        stageService.announce(jobPostingId, stageId);

        StageDetailResponse detail = stageService.getStage(jobPostingId, stageId);
        assertThat(detail.status()).isEqualTo(StageStatus.RESULT_ANNOUNCED);
        assertThat(detail.resultAnnouncementDateTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 10, 0));
    }

    @Test
    void announce_fails_when_stage_result_is_missing() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        jobPostingService.publish(jobPostingId);
        stageService.start(jobPostingId, stageId);

        assertThatThrownBy(() -> stageService.announce(jobPostingId, stageId))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void announce_fails_when_stage_result_has_pending() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        jobPostingService.publish(jobPostingId);
        stageService.start(jobPostingId, stageId);
        createSubmittedApplication("announce-pending", jobPostingId);
        stageResultService.initialize(stageId);

        assertThatThrownBy(() -> stageService.announce(jobPostingId, stageId))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void announce_fails_when_stage_is_not_in_progress() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        jobPostingService.publish(jobPostingId);

        assertThatThrownBy(() -> stageService.announce(jobPostingId, stageId))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void close_success_when_stage_is_result_announced() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        jobPostingService.publish(jobPostingId);
        stageService.start(jobPostingId, stageId);
        prepareDecidedStageResult(jobPostingId, stageId, "close-success");
        stageService.announce(jobPostingId, stageId);

        stageService.close(jobPostingId, stageId);

        assertThat(stageService.getStage(jobPostingId, stageId).status()).isEqualTo(StageStatus.CLOSED);
    }

    @Test
    void close_fails_when_stage_is_not_result_announced() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        jobPostingService.publish(jobPostingId);
        stageService.start(jobPostingId, stageId);

        assertThatThrownBy(() -> stageService.close(jobPostingId, stageId))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void closed_stage_cannot_transition_again() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        jobPostingService.publish(jobPostingId);
        stageService.start(jobPostingId, stageId);
        prepareDecidedStageResult(jobPostingId, stageId, "closed-transition");
        stageService.announce(jobPostingId, stageId);
        stageService.close(jobPostingId, stageId);

        assertThatThrownBy(() -> stageService.start(jobPostingId, stageId))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void delete_ready_stage_success_when_job_posting_is_draft() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));

        Long deletedId = stageService.delete(jobPostingId, stageId);

        assertThat(deletedId).isEqualTo(stageId);
        assertThatThrownBy(() -> stageService.getStage(jobPostingId, stageId))
                .isInstanceOf(StageNotFoundException.class);
    }

    @Test
    void delete_ready_stage_success_when_job_posting_is_published() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        jobPostingService.publish(jobPostingId);

        stageService.delete(jobPostingId, stageId);

        assertThatThrownBy(() -> stageService.getStage(jobPostingId, stageId))
                .isInstanceOf(StageNotFoundException.class);
    }

    @Test
    void delete_fails_when_job_posting_is_closed() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        jobPostingService.publish(jobPostingId);
        jobPostingService.close(jobPostingId);

        assertThatThrownBy(() -> stageService.delete(jobPostingId, stageId))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void delete_fails_when_stage_is_in_progress() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        jobPostingService.publish(jobPostingId);
        stageService.start(jobPostingId, stageId);

        assertThatThrownBy(() -> stageService.delete(jobPostingId, stageId))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void delete_fails_when_stage_is_result_announced() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        jobPostingService.publish(jobPostingId);
        stageService.start(jobPostingId, stageId);
        prepareDecidedStageResult(jobPostingId, stageId, "delete-announced");
        stageService.announce(jobPostingId, stageId);

        assertThatThrownBy(() -> stageService.delete(jobPostingId, stageId))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void delete_fails_when_stage_is_closed() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        jobPostingService.publish(jobPostingId);
        stageService.start(jobPostingId, stageId);
        prepareDecidedStageResult(jobPostingId, stageId, "delete-closed");
        stageService.announce(jobPostingId, stageId);
        stageService.close(jobPostingId, stageId);

        assertThatThrownBy(() -> stageService.delete(jobPostingId, stageId))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void delete_fails_when_stage_not_found() {
        Long jobPostingId = createJobPosting();

        assertThatThrownBy(() -> stageService.delete(jobPostingId, 99999L))
                .isInstanceOf(StageNotFoundException.class);
    }

    @Test
    void delete_fails_when_stage_belongs_to_other_job_posting() {
        Long firstJobPostingId = createJobPosting();
        Long secondJobPostingId = createJobPosting();
        Long stageId = stageService.create(firstJobPostingId, createStageRequest(0, false));

        assertThatThrownBy(() -> stageService.delete(secondJobPostingId, stageId))
                .isInstanceOf(StageNotFoundException.class);
    }

    private Long createJobPosting() {
        return jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 30, 18, 0),
                List.of(new JobPositionRequest("Backend", 1)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)
        ));
    }

    private void prepareDecidedStageResult(Long jobPostingId, Long stageId, String suffix) {
        createSubmittedApplication("stage-" + suffix, jobPostingId);
        stageResultService.initialize(stageId);
        for (AdminStageResultResponse result : stageResultService.getResults(stageId)) {
            stageResultService.updateResult(
                    stageId,
                    result.stageResultId(),
                    new StageResultUpdateRequest(StageResultStatus.PASSED, null, null),
                    "employee01"
            );
        }
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

    private StageCreateRequest createStageRequest(int stageOrder, boolean finalStage) {
        return new StageCreateRequest(
                "Document screening",
                StageType.DOCUMENT,
                stageOrder,
                LocalDateTime.of(2026, 7, 1, 10, 0),
                finalStage
        );
    }

    private StageUpdateRequest updateStageRequest(int stageOrder, boolean finalStage) {
        return new StageUpdateRequest(
                "First interview",
                StageType.FIRST_INTERVIEW,
                stageOrder,
                LocalDateTime.of(2026, 7, 2, 10, 0),
                finalStage
        );
    }
}
