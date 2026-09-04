package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.EmployeeRepository;
import com.shinyoung.recruit.domain.repository.InterviewParticipantRepository;
import com.shinyoung.recruit.domain.repository.InterviewRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.support.BasicInfoTestSupport;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.InterviewCandidateParticipantRequest;
import com.shinyoung.recruit.dto.request.InterviewCreateRequest;
import com.shinyoung.recruit.dto.request.InterviewInterviewerParticipantRequest;
import com.shinyoung.recruit.dto.request.InterviewParticipantReplaceRequest;
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
import com.shinyoung.recruit.enumeration.InterviewMethod;
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

    @Autowired
    private StageResultRepository stageResultRepository;

    @Autowired
    private InterviewService interviewService;

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private InterviewParticipantRepository interviewParticipantRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ApplicationBasicInfoRepository basicInfoRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

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
    void update_closed_stage_fails() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        Stage stage = stageRepository.findById(stageId).orElseThrow();
        ReflectionTestUtils.setField(stage, "status", StageStatus.CLOSED);

        assertThatThrownBy(() -> stageService.update(jobPostingId, stageId, updateStageRequest(0, false)))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void update_in_progress_stage_changes_announcement_datetime_only() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        Stage stage = stageRepository.findById(stageId).orElseThrow();
        ReflectionTestUtils.setField(stage, "status", StageStatus.IN_PROGRESS);
        LocalDateTime postponed = LocalDateTime.of(2026, 8, 1, 10, 0);

        stageService.update(jobPostingId, stageId, new StageUpdateRequest(
                "Document screening", StageType.DOCUMENT, 0, postponed, false));

        Stage updated = stageRepository.findById(stageId).orElseThrow();
        assertThat(updated.getResultAnnouncementDateTime()).isEqualTo(postponed);
        assertThat(updated.getStageName()).isEqualTo("Document screening");
        assertThat(updated.getStatus()).isEqualTo(StageStatus.IN_PROGRESS);
    }

    @Test
    void update_in_progress_stage_fails_when_locked_field_changes() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        Stage stage = stageRepository.findById(stageId).orElseThrow();
        ReflectionTestUtils.setField(stage, "status", StageStatus.IN_PROGRESS);

        assertThatThrownBy(() -> stageService.update(jobPostingId, stageId, new StageUpdateRequest(
                "Renamed", StageType.DOCUMENT, 0, LocalDateTime.of(2026, 8, 1, 10, 0), false)))
                .isInstanceOf(InvalidStageException.class)
                .hasMessageContaining("resultAnnouncementDateTime");
        assertThatThrownBy(() -> stageService.update(jobPostingId, stageId, new StageUpdateRequest(
                "Document screening", StageType.DOCUMENT, 5, LocalDateTime.of(2026, 8, 1, 10, 0), false)))
                .isInstanceOf(InvalidStageException.class);
        assertThatThrownBy(() -> stageService.update(jobPostingId, stageId, new StageUpdateRequest(
                "Document screening", StageType.DOCUMENT, 0, LocalDateTime.of(2026, 8, 1, 10, 0), true)))
                .isInstanceOf(InvalidStageException.class);
        assertThatThrownBy(() -> stageService.update(jobPostingId, stageId, new StageUpdateRequest(
                "Document screening", StageType.FIRST_INTERVIEW, 0, LocalDateTime.of(2026, 8, 1, 10, 0), false)))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void update_in_progress_stage_can_clear_announcement_datetime() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        Stage stage = stageRepository.findById(stageId).orElseThrow();
        ReflectionTestUtils.setField(stage, "status", StageStatus.IN_PROGRESS);

        stageService.update(jobPostingId, stageId, new StageUpdateRequest(
                "Document screening", StageType.DOCUMENT, 0, null, false));

        assertThat(stageRepository.findById(stageId).orElseThrow().getResultAnnouncementDateTime()).isNull();
    }

    @Test
    void update_announced_stage_fails_even_for_announcement_datetime() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        Stage stage = stageRepository.findById(stageId).orElseThrow();
        ReflectionTestUtils.setField(stage, "status", StageStatus.RESULT_ANNOUNCED);

        assertThatThrownBy(() -> stageService.update(jobPostingId, stageId, new StageUpdateRequest(
                "Document screening", StageType.DOCUMENT, 0, LocalDateTime.of(2026, 8, 1, 10, 0), false)))
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
    void delete_ready_stage_removes_initialized_stage_results() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        jobPostingService.publish(jobPostingId);
        createSubmittedApplication("delete-ready-results", jobPostingId);
        stageResultService.initialize(stageId);
        assertThat(stageResultRepository.countByStageId(stageId)).isEqualTo(1);

        stageService.delete(jobPostingId, stageId);

        assertThat(stageResultRepository.countByStageId(stageId)).isZero();
        assertThatThrownBy(() -> stageService.getStage(jobPostingId, stageId))
                .isInstanceOf(StageNotFoundException.class);
    }

    @Test
    void delete_ready_stage_keeps_other_stage_results() {
        Long jobPostingId = createJobPosting();
        Long deletedStageId = stageService.create(jobPostingId, createStageRequest(0, false));
        Long keptStageId = stageService.create(jobPostingId, createStageRequest(1, false));
        jobPostingService.publish(jobPostingId);
        createSubmittedApplication("delete-ready-other", jobPostingId);
        stageResultService.initialize(deletedStageId);
        stageResultService.initialize(keptStageId);

        stageService.delete(jobPostingId, deletedStageId);

        assertThat(stageResultRepository.countByStageId(keptStageId)).isEqualTo(1);
    }

    @Test
    void delete_ready_stage_removes_draft_interviews() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, interviewStageRequest(0));
        jobPostingService.publish(jobPostingId);
        Long applicationId = createSubmittedApplication("stage-delete-draft-interview", jobPostingId);
        Long interviewId = createDraftInterviewWithParticipants(
                jobPostingId, stageId, applicationId, "delete-draft-interview");

        stageService.delete(jobPostingId, stageId);

        assertThat(interviewRepository.findById(interviewId)).isEmpty();
        assertThat(interviewParticipantRepository
                .findByInterviewIdOrderByRoleAscSortOrderAscIdAsc(interviewId)).isEmpty();
    }

    @Test
    void delete_fails_when_stage_has_confirmed_interview() {
        Long jobPostingId = createJobPosting();
        Long stageId = createConfirmableInterviewStage(jobPostingId);
        Long applicationId = passDocumentStage(jobPostingId, "delete-confirmed-interview");
        Long interviewId = createDraftInterviewWithParticipants(
                jobPostingId, stageId, applicationId, "delete-confirmed-interview");
        interviewService.confirm(interviewId);

        assertThatThrownBy(() -> stageService.delete(jobPostingId, stageId))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void delete_fails_when_stage_has_cancelled_interview() {
        Long jobPostingId = createJobPosting();
        Long stageId = createConfirmableInterviewStage(jobPostingId);
        Long applicationId = passDocumentStage(jobPostingId, "delete-cancelled-interview");
        Long interviewId = createDraftInterviewWithParticipants(
                jobPostingId, stageId, applicationId, "delete-cancelled-interview");
        interviewService.confirm(interviewId);
        interviewService.cancel(interviewId);

        assertThatThrownBy(() -> stageService.delete(jobPostingId, stageId))
                .isInstanceOf(InvalidStageException.class);
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
                // 접수기간을 현재 기준 동적으로 잡는다 — 하드코딩 날짜는 시스템 날짜가 지나면
                // createSubmittedApplication 이 접수기간 검증에서 사전-실패한다(9b 리뷰 Low 2 하드닝).
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30),
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

    private StageCreateRequest interviewStageRequest(int stageOrder) {
        return new StageCreateRequest(
                "First interview",
                StageType.FIRST_INTERVIEW,
                stageOrder,
                LocalDateTime.of(2026, 7, 1, 10, 0),
                false
        );
    }

    /** 면접 확정은 직전 단계의 PASSED 결과를 요구한다. 문서 단계(order 0) + 면접 단계(order 1)를 만들고 공고를 게시한다. */
    private Long createConfirmableInterviewStage(Long jobPostingId) {
        stageService.create(jobPostingId, createStageRequest(0, false));
        Long interviewStageId = stageService.create(jobPostingId, interviewStageRequest(1));
        jobPostingService.publish(jobPostingId);
        return interviewStageId;
    }

    private Long passDocumentStage(Long jobPostingId, String suffix) {
        Long applicationId = createSubmittedApplication("stage-" + suffix, jobPostingId);
        Long documentStageId = stageRepository.findByJobPostingIdOrderByStageOrderAscIdAsc(jobPostingId).get(0).getId();
        stageService.start(jobPostingId, documentStageId);
        stageResultService.initialize(documentStageId);
        for (AdminStageResultResponse result : stageResultService.getResults(documentStageId)) {
            stageResultService.updateResult(
                    documentStageId,
                    result.stageResultId(),
                    new StageResultUpdateRequest(StageResultStatus.PASSED, null, null),
                    "employee01"
            );
        }
        stageService.announce(jobPostingId, documentStageId);
        return applicationId;
    }

    private Long createDraftInterviewWithParticipants(
            Long jobPostingId,
            Long stageId,
            Long applicationId,
            String suffix
    ) {
        Employee employee = new Employee();
        employee.setLoginId("employee-" + suffix);
        employee.setName("Interviewer");
        employee.setDeptName("HR-" + suffix);
        employee = employeeRepository.saveAndFlush(employee);

        Long interviewId = interviewService.createDraft(jobPostingId, new InterviewCreateRequest(
                stageId,
                "Group A",
                LocalDateTime.of(2026, 7, 10, 10, 0),
                LocalDateTime.of(2026, 7, 10, 11, 0),
                InterviewMethod.IN_PERSON,
                "Head office",
                "Room 1",
                null,
                "memo"
        ));
        interviewService.replaceParticipants(interviewId, new InterviewParticipantReplaceRequest(
                List.of(new InterviewCandidateParticipantRequest(applicationId, 1)),
                List.of(new InterviewInterviewerParticipantRequest(employee.getId(), 1))
        ));
        return interviewId;
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
