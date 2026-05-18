package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPositionRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationAnswerReplaceRequest;
import com.shinyoung.recruit.dto.request.ApplicationAnswerRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.ApplicationUpdateRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.JobPostingQuestionCreateRequest;
import com.shinyoung.recruit.dto.response.JobPostingQuestionResponse;
import com.shinyoung.recruit.dto.response.AdminApplicationDetailResponse;
import com.shinyoung.recruit.dto.response.AdminApplicationSummaryResponse;
import com.shinyoung.recruit.dto.response.ApplicationDetailResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.util.ReflectionTestUtils;
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
class JobApplicationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 15, 10, 0);

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobPostingQuestionService jobPostingQuestionService;

    @Autowired
    private ApplicationAnswerService applicationAnswerService;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private JobPositionRepository jobPositionRepository;

    @Test
    void create_application_success() {
        Applicant applicant = createApplicant("applicant-create", "Applicant A");
        Long jobPostingId = createPublishedJobPosting();
        Long jobPositionId = firstJobPositionId(jobPostingId);

        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, jobPositionId)
        );

        ApplicationDetailResponse detail = jobApplicationService.getApplication(applicant.getId(), applicationId);
        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();
        assertThat(detail.applicationId()).isEqualTo(applicationId);
        assertThat(detail.applicantId()).isEqualTo(applicant.getId());
        assertThat(detail.status()).isEqualTo(JobApplicationStatus.DRAFT);
        assertThat(application.getSubmittedAt()).isNull();
        assertThat(application.getWithdrawnAt()).isNull();
    }

    @Test
    void create_application_saves_snapshots() {
        Applicant applicant = createApplicant("applicant-snapshot", "Snapshot Applicant");
        Long jobPostingId = createPublishedJobPosting("Snapshot Posting");
        Long jobPositionId = firstJobPositionId(jobPostingId);

        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, jobPositionId)
        );

        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();
        assertThat(application.getApplicantNameSnapshot()).isEqualTo("Snapshot Applicant");
        assertThat(application.getJobPostingTitleSnapshot()).isEqualTo("Snapshot Posting");
        assertThat(application.getJobPositionNameSnapshot()).isEqualTo("Backend");
    }

    @Test
    void create_application_fails_when_job_posting_is_not_published() {
        Applicant draftApplicant = createApplicant("applicant-draft-posting", "Applicant B");
        Long draftJobPostingId = createDraftJobPosting();

        assertThatThrownBy(() -> jobApplicationService.create(
                draftApplicant.getId(),
                new ApplicationCreateRequest(draftJobPostingId, firstJobPositionId(draftJobPostingId))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant closedApplicant = createApplicant("applicant-closed-posting", "Applicant C");
        Long closedJobPostingId = createPublishedJobPosting();
        jobPostingService.close(closedJobPostingId);

        assertThatThrownBy(() -> jobApplicationService.create(
                closedApplicant.getId(),
                new ApplicationCreateRequest(closedJobPostingId, firstJobPositionId(closedJobPostingId))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void create_application_fails_outside_reception_period() {
        Applicant beforeApplicant = createApplicant("applicant-before", "Applicant D");
        Long beforeJobPostingId = createPublishedJobPosting(
                "Before Posting",
                LocalDateTime.of(2026, 6, 16, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0)
        );

        assertThatThrownBy(() -> jobApplicationService.create(
                beforeApplicant.getId(),
                new ApplicationCreateRequest(beforeJobPostingId, firstJobPositionId(beforeJobPostingId))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant afterApplicant = createApplicant("applicant-after", "Applicant E");
        Long afterJobPostingId = createPublishedJobPosting(
                "After Posting",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 14, 18, 0)
        );

        assertThatThrownBy(() -> jobApplicationService.create(
                afterApplicant.getId(),
                new ApplicationCreateRequest(afterJobPostingId, firstJobPositionId(afterJobPostingId))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void create_application_fails_when_reference_is_invalid() {
        Applicant applicant = createApplicant("applicant-reference", "Applicant F");
        Long jobPostingId = createPublishedJobPosting("Reference Posting");
        Long otherJobPostingId = createPublishedJobPosting("Other Posting");

        assertThatThrownBy(() -> jobApplicationService.create(
                99999L,
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(99999L, 1L)
        )).isInstanceOf(JobPostingNotFoundException.class);

        assertThatThrownBy(() -> jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, 99999L)
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(otherJobPostingId))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void create_application_fails_when_application_form_config_is_missing() {
        Applicant applicant = createApplicant("applicant-no-config", "Applicant G");
        JobPosting jobPosting = JobPosting.create(
                "No Config Posting",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0)
        );
        jobPosting.replaceJobPositions(List.of(JobPosition.create("Backend", 1, 0)));
        jobPosting.publish(NOW);
        Long jobPostingId = jobPostingRepository.save(jobPosting).getId();

        assertThatThrownBy(() -> jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void create_application_fails_when_duplicate_application_exists() {
        Applicant applicant = createApplicant("applicant-duplicate", "Applicant H");
        Long jobPostingId = createPublishedJobPosting();
        List<Long> jobPositionIds = jobPositionIds(jobPostingId);
        jobApplicationService.create(applicant.getId(), new ApplicationCreateRequest(jobPostingId, jobPositionIds.get(0)));

        assertThatThrownBy(() -> jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, jobPositionIds.get(0))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, jobPositionIds.get(1))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void get_application_success_and_owner_check() {
        Applicant applicant = createApplicant("applicant-get", "Applicant I");
        Applicant otherApplicant = createApplicant("applicant-get-other", "Applicant J");
        Long jobPostingId = createPublishedJobPosting();
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );

        ApplicationDetailResponse detail = jobApplicationService.getApplication(applicant.getId(), applicationId);

        assertThat(detail.applicationId()).isEqualTo(applicationId);
        assertThat(detail.jobPostingId()).isEqualTo(jobPostingId);
        assertThatThrownBy(() -> jobApplicationService.getApplication(otherApplicant.getId(), applicationId))
                .isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void get_my_application_by_job_posting_success_and_not_found() {
        Applicant applicant = createApplicant("applicant-by-posting", "Applicant K");
        Long jobPostingId = createPublishedJobPosting();
        Long otherJobPostingId = createPublishedJobPosting("No Application Posting");
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );

        ApplicationDetailResponse detail = jobApplicationService.getMyApplicationByJobPosting(applicant.getId(), jobPostingId);

        assertThat(detail.applicationId()).isEqualTo(applicationId);
        assertThatThrownBy(() -> jobApplicationService.getMyApplicationByJobPosting(applicant.getId(), otherJobPostingId))
                .isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void update_draft_changes_job_position_snapshot_and_keeps_draft_status() {
        Applicant applicant = createApplicant("applicant-update-draft", "Applicant L");
        Long jobPostingId = createPublishedJobPosting();
        List<Long> jobPositionIds = jobPositionIds(jobPostingId);
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, jobPositionIds.get(0))
        );

        Long updatedId = jobApplicationService.updateDraft(
                applicant.getId(),
                applicationId,
                new ApplicationUpdateRequest(jobPositionIds.get(1))
        );

        ApplicationDetailResponse detail = jobApplicationService.getApplication(applicant.getId(), updatedId);
        JobApplication application = jobApplicationRepository.findById(updatedId).orElseThrow();
        assertThat(detail.jobPositionId()).isEqualTo(jobPositionIds.get(1));
        assertThat(detail.jobPositionName()).isEqualTo("Frontend");
        assertThat(detail.status()).isEqualTo(JobApplicationStatus.DRAFT);
        assertThat(application.getSubmittedAt()).isNull();
        assertThat(application.getWithdrawnAt()).isNull();
    }

    @Test
    void update_draft_fails_when_application_is_not_draft() {
        Applicant submittedApplicant = createApplicant("applicant-update-submitted", "Applicant M");
        Long submittedPostingId = createPublishedJobPosting("Submitted Update Posting");
        List<Long> submittedPositionIds = jobPositionIds(submittedPostingId);
        Long submittedApplicationId = jobApplicationService.create(
                submittedApplicant.getId(),
                new ApplicationCreateRequest(submittedPostingId, submittedPositionIds.get(0))
        );
        jobApplicationService.submit(submittedApplicant.getId(), submittedApplicationId);

        assertThatThrownBy(() -> jobApplicationService.updateDraft(
                submittedApplicant.getId(),
                submittedApplicationId,
                new ApplicationUpdateRequest(submittedPositionIds.get(1))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant withdrawnApplicant = createApplicant("applicant-update-withdrawn", "Applicant N");
        Long withdrawnPostingId = createPublishedJobPosting("Withdrawn Update Posting");
        List<Long> withdrawnPositionIds = jobPositionIds(withdrawnPostingId);
        Long withdrawnApplicationId = jobApplicationService.create(
                withdrawnApplicant.getId(),
                new ApplicationCreateRequest(withdrawnPostingId, withdrawnPositionIds.get(0))
        );
        jobApplicationService.submit(withdrawnApplicant.getId(), withdrawnApplicationId);
        jobApplicationService.withdraw(withdrawnApplicant.getId(), withdrawnApplicationId);

        assertThatThrownBy(() -> jobApplicationService.updateDraft(
                withdrawnApplicant.getId(),
                withdrawnApplicationId,
                new ApplicationUpdateRequest(withdrawnPositionIds.get(1))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void update_draft_fails_when_owner_period_posting_or_position_is_invalid() {
        Applicant applicant = createApplicant("applicant-update-invalid", "Applicant O");
        Applicant otherApplicant = createApplicant("applicant-update-other", "Applicant P");
        Long jobPostingId = createPublishedJobPosting("Update Invalid Posting");
        Long otherJobPostingId = createPublishedJobPosting("Update Other Posting");
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );

        assertThatThrownBy(() -> jobApplicationService.updateDraft(
                otherApplicant.getId(),
                applicationId,
                new ApplicationUpdateRequest(firstJobPositionId(jobPostingId))
        )).isInstanceOf(JobApplicationNotFoundException.class);

        assertThatThrownBy(() -> jobApplicationService.updateDraft(
                applicant.getId(),
                applicationId,
                new ApplicationUpdateRequest(99999L)
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> jobApplicationService.updateDraft(
                applicant.getId(),
                applicationId,
                new ApplicationUpdateRequest(firstJobPositionId(otherJobPostingId))
        )).isInstanceOf(InvalidJobApplicationException.class);

        setReceptionPeriod(
                jobPostingId,
                LocalDateTime.of(2026, 6, 16, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0)
        );
        assertThatThrownBy(() -> jobApplicationService.updateDraft(
                applicant.getId(),
                applicationId,
                new ApplicationUpdateRequest(firstJobPositionId(jobPostingId))
        )).isInstanceOf(InvalidJobApplicationException.class);

        setReceptionPeriod(
                jobPostingId,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0)
        );
        setJobPostingStatus(jobPostingId, JobPostingStatus.DRAFT);
        assertThatThrownBy(() -> jobApplicationService.updateDraft(
                applicant.getId(),
                applicationId,
                new ApplicationUpdateRequest(firstJobPositionId(jobPostingId))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void update_draft_fails_after_reception_end_or_closed_posting() {
        Applicant afterApplicant = createApplicant("applicant-update-after", "Applicant Q");
        Long afterPostingId = createPublishedJobPosting("Update After Posting");
        Long afterApplicationId = jobApplicationService.create(
                afterApplicant.getId(),
                new ApplicationCreateRequest(afterPostingId, firstJobPositionId(afterPostingId))
        );
        setReceptionPeriod(
                afterPostingId,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 14, 18, 0)
        );

        assertThatThrownBy(() -> jobApplicationService.updateDraft(
                afterApplicant.getId(),
                afterApplicationId,
                new ApplicationUpdateRequest(firstJobPositionId(afterPostingId))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant closedApplicant = createApplicant("applicant-update-closed", "Applicant R");
        Long closedPostingId = createPublishedJobPosting("Update Closed Posting");
        Long closedApplicationId = jobApplicationService.create(
                closedApplicant.getId(),
                new ApplicationCreateRequest(closedPostingId, firstJobPositionId(closedPostingId))
        );
        jobPostingService.close(closedPostingId);

        assertThatThrownBy(() -> jobApplicationService.updateDraft(
                closedApplicant.getId(),
                closedApplicationId,
                new ApplicationUpdateRequest(firstJobPositionId(closedPostingId))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void submit_draft_application_success() {
        Applicant applicant = createApplicant("applicant-submit", "Applicant S");
        Long jobPostingId = createPublishedJobPosting();
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );

        Long submittedId = jobApplicationService.submit(applicant.getId(), applicationId);

        JobApplication application = jobApplicationRepository.findById(submittedId).orElseThrow();
        assertThat(application.getStatus()).isEqualTo(JobApplicationStatus.SUBMITTED);
        assertThat(application.getSubmittedAt()).isEqualTo(NOW);
        assertThat(application.getWithdrawnAt()).isNull();
    }

    @Test
    void submit_fails_when_application_is_not_draft_or_owner_is_invalid() {
        Applicant applicant = createApplicant("applicant-submit-invalid", "Applicant T");
        Applicant otherApplicant = createApplicant("applicant-submit-other", "Applicant U");
        Long jobPostingId = createPublishedJobPosting();
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );

        assertThatThrownBy(() -> jobApplicationService.submit(otherApplicant.getId(), applicationId))
                .isInstanceOf(JobApplicationNotFoundException.class);

        jobApplicationService.submit(applicant.getId(), applicationId);

        assertThatThrownBy(() -> jobApplicationService.submit(applicant.getId(), applicationId))
                .isInstanceOf(InvalidJobApplicationException.class);

        jobApplicationService.withdraw(applicant.getId(), applicationId);

        assertThatThrownBy(() -> jobApplicationService.submit(applicant.getId(), applicationId))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void submit_fails_outside_reception_period_or_unpublished_posting() {
        Applicant beforeApplicant = createApplicant("applicant-submit-before", "Applicant V");
        Long beforePostingId = createPublishedJobPosting("Submit Before Posting");
        Long beforeApplicationId = jobApplicationService.create(
                beforeApplicant.getId(),
                new ApplicationCreateRequest(beforePostingId, firstJobPositionId(beforePostingId))
        );
        setReceptionPeriod(
                beforePostingId,
                LocalDateTime.of(2026, 6, 16, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0)
        );

        assertThatThrownBy(() -> jobApplicationService.submit(beforeApplicant.getId(), beforeApplicationId))
                .isInstanceOf(InvalidJobApplicationException.class);

        Applicant afterApplicant = createApplicant("applicant-submit-after", "Applicant W");
        Long afterPostingId = createPublishedJobPosting("Submit After Posting");
        Long afterApplicationId = jobApplicationService.create(
                afterApplicant.getId(),
                new ApplicationCreateRequest(afterPostingId, firstJobPositionId(afterPostingId))
        );
        setReceptionPeriod(
                afterPostingId,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 14, 18, 0)
        );

        assertThatThrownBy(() -> jobApplicationService.submit(afterApplicant.getId(), afterApplicationId))
                .isInstanceOf(InvalidJobApplicationException.class);

        Applicant draftApplicant = createApplicant("applicant-submit-draft", "Applicant X");
        Long draftPostingId = createPublishedJobPosting("Submit Draft Posting");
        Long draftApplicationId = jobApplicationService.create(
                draftApplicant.getId(),
                new ApplicationCreateRequest(draftPostingId, firstJobPositionId(draftPostingId))
        );
        setJobPostingStatus(draftPostingId, JobPostingStatus.DRAFT);

        assertThatThrownBy(() -> jobApplicationService.submit(draftApplicant.getId(), draftApplicationId))
                .isInstanceOf(InvalidJobApplicationException.class);

        Applicant closedApplicant = createApplicant("applicant-submit-closed", "Applicant Y");
        Long closedPostingId = createPublishedJobPosting("Submit Closed Posting");
        Long closedApplicationId = jobApplicationService.create(
                closedApplicant.getId(),
                new ApplicationCreateRequest(closedPostingId, firstJobPositionId(closedPostingId))
        );
        jobPostingService.close(closedPostingId);

        assertThatThrownBy(() -> jobApplicationService.submit(closedApplicant.getId(), closedApplicationId))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void submit_fails_when_config_or_selected_position_is_invalid() {
        Applicant configApplicant = createApplicant("applicant-submit-config", "Applicant Z");
        Long configPostingId = createPublishedJobPosting("Submit Config Posting");
        Long configApplicationId = jobApplicationService.create(
                configApplicant.getId(),
                new ApplicationCreateRequest(configPostingId, firstJobPositionId(configPostingId))
        );
        JobApplication configApplication = jobApplicationRepository.findById(configApplicationId).orElseThrow();
        ReflectionTestUtils.setField(configApplication.getJobPosting(), "applicationFormConfig", null);

        assertThatThrownBy(() -> jobApplicationService.submit(configApplicant.getId(), configApplicationId))
                .isInstanceOf(InvalidJobApplicationException.class);

        Applicant positionApplicant = createApplicant("applicant-submit-position", "Applicant AA");
        Long positionPostingId = createPublishedJobPosting("Submit Position Posting");
        Long otherPostingId = createPublishedJobPosting("Submit Other Posting");
        Long positionApplicationId = jobApplicationService.create(
                positionApplicant.getId(),
                new ApplicationCreateRequest(positionPostingId, firstJobPositionId(positionPostingId))
        );
        JobApplication positionApplication = jobApplicationRepository.findById(positionApplicationId).orElseThrow();
        JobPosition otherPosition = jobPositionRepository
                .findByIdAndJobPostingId(firstJobPositionId(otherPostingId), otherPostingId)
                .orElseThrow();
        ReflectionTestUtils.setField(positionApplication, "jobPosition", otherPosition);

        assertThatThrownBy(() -> jobApplicationService.submit(positionApplicant.getId(), positionApplicationId))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void submit_fails_when_required_detail_section_is_missing_and_keeps_draft() {
        assertSubmitValidationFailureKeepsDraft(
                "applicant-submit-no-education",
                new ApplicationFormConfigRequest(true, false, false, false, false, false, false)
        );
        assertSubmitValidationFailureKeepsDraft(
                "applicant-submit-no-career",
                new ApplicationFormConfigRequest(false, true, false, false, false, false, false)
        );
        assertSubmitValidationFailureKeepsDraft(
                "applicant-submit-no-military",
                new ApplicationFormConfigRequest(false, false, false, false, true, false, false)
        );
    }

    @Test
    void submit_succeeds_when_required_question_answer_exists() {
        Applicant applicant = createApplicant("applicant-submit-answer-ok", "Applicant Answer Ok");
        Long jobPostingId = createDraftJobPosting();
        JobPostingQuestionResponse question = createQuestion(jobPostingId, true, QuestionAnswerType.LONG_TEXT, 1000);
        jobPostingService.publish(jobPostingId);
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );
        applicationAnswerService.replaceAnswers(
                applicant.getId(),
                applicationId,
                new ApplicationAnswerReplaceRequest(List.of(new ApplicationAnswerRequest(question.questionId(), "submitted answer")))
        );

        Long submittedId = jobApplicationService.submit(applicant.getId(), applicationId);

        JobApplication application = jobApplicationRepository.findById(submittedId).orElseThrow();
        assertThat(application.getStatus()).isEqualTo(JobApplicationStatus.SUBMITTED);
        assertThat(application.getSubmittedAt()).isEqualTo(NOW);
    }

    @Test
    void submit_fails_when_required_question_answer_is_missing_and_keeps_draft() {
        Applicant applicant = createApplicant("applicant-submit-answer-missing", "Applicant Answer Missing");
        Long jobPostingId = createDraftJobPosting();
        createQuestion(jobPostingId, true, QuestionAnswerType.LONG_TEXT, 1000);
        jobPostingService.publish(jobPostingId);
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );

        assertThatThrownBy(() -> jobApplicationService.submit(applicant.getId(), applicationId))
                .isInstanceOf(InvalidJobApplicationException.class);

        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();
        assertThat(application.getStatus()).isEqualTo(JobApplicationStatus.DRAFT);
        assertThat(application.getSubmittedAt()).isNull();
    }

    @Test
    void withdraw_submitted_application_success() {
        Applicant applicant = createApplicant("applicant-withdraw", "Applicant AB");
        Long jobPostingId = createPublishedJobPosting();
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );
        jobApplicationService.submit(applicant.getId(), applicationId);
        LocalDateTime submittedAt = jobApplicationRepository.findById(applicationId).orElseThrow().getSubmittedAt();

        Long withdrawnId = jobApplicationService.withdraw(applicant.getId(), applicationId);

        JobApplication application = jobApplicationRepository.findById(withdrawnId).orElseThrow();
        assertThat(application.getStatus()).isEqualTo(JobApplicationStatus.WITHDRAWN);
        assertThat(application.getSubmittedAt()).isEqualTo(submittedAt);
        assertThat(application.getWithdrawnAt()).isEqualTo(NOW);
    }

    @Test
    void withdraw_fails_when_application_is_not_submitted_or_owner_is_invalid() {
        Applicant applicant = createApplicant("applicant-withdraw-invalid", "Applicant AC");
        Applicant otherApplicant = createApplicant("applicant-withdraw-other", "Applicant AD");
        Long jobPostingId = createPublishedJobPosting();
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );

        assertThatThrownBy(() -> jobApplicationService.withdraw(applicant.getId(), applicationId))
                .isInstanceOf(InvalidJobApplicationException.class);

        jobApplicationService.submit(applicant.getId(), applicationId);

        assertThatThrownBy(() -> jobApplicationService.withdraw(otherApplicant.getId(), applicationId))
                .isInstanceOf(JobApplicationNotFoundException.class);

        jobApplicationService.withdraw(applicant.getId(), applicationId);

        assertThatThrownBy(() -> jobApplicationService.withdraw(applicant.getId(), applicationId))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void withdraw_fails_outside_reception_period_or_unpublished_posting() {
        Applicant beforeApplicant = createApplicant("applicant-withdraw-before", "Applicant AE");
        Long beforePostingId = createPublishedJobPosting("Withdraw Before Posting");
        Long beforeApplicationId = jobApplicationService.create(
                beforeApplicant.getId(),
                new ApplicationCreateRequest(beforePostingId, firstJobPositionId(beforePostingId))
        );
        jobApplicationService.submit(beforeApplicant.getId(), beforeApplicationId);
        setReceptionPeriod(
                beforePostingId,
                LocalDateTime.of(2026, 6, 16, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0)
        );

        assertThatThrownBy(() -> jobApplicationService.withdraw(beforeApplicant.getId(), beforeApplicationId))
                .isInstanceOf(InvalidJobApplicationException.class);

        Applicant afterApplicant = createApplicant("applicant-withdraw-after", "Applicant AF");
        Long afterPostingId = createPublishedJobPosting("Withdraw After Posting");
        Long afterApplicationId = jobApplicationService.create(
                afterApplicant.getId(),
                new ApplicationCreateRequest(afterPostingId, firstJobPositionId(afterPostingId))
        );
        jobApplicationService.submit(afterApplicant.getId(), afterApplicationId);
        setReceptionPeriod(
                afterPostingId,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 14, 18, 0)
        );

        assertThatThrownBy(() -> jobApplicationService.withdraw(afterApplicant.getId(), afterApplicationId))
                .isInstanceOf(InvalidJobApplicationException.class);

        Applicant draftApplicant = createApplicant("applicant-withdraw-draft", "Applicant AG");
        Long draftPostingId = createPublishedJobPosting("Withdraw Draft Posting");
        Long draftApplicationId = jobApplicationService.create(
                draftApplicant.getId(),
                new ApplicationCreateRequest(draftPostingId, firstJobPositionId(draftPostingId))
        );
        jobApplicationService.submit(draftApplicant.getId(), draftApplicationId);
        setJobPostingStatus(draftPostingId, JobPostingStatus.DRAFT);

        assertThatThrownBy(() -> jobApplicationService.withdraw(draftApplicant.getId(), draftApplicationId))
                .isInstanceOf(InvalidJobApplicationException.class);

        Applicant closedApplicant = createApplicant("applicant-withdraw-closed", "Applicant AH");
        Long closedPostingId = createPublishedJobPosting("Withdraw Closed Posting");
        Long closedApplicationId = jobApplicationService.create(
                closedApplicant.getId(),
                new ApplicationCreateRequest(closedPostingId, firstJobPositionId(closedPostingId))
        );
        jobApplicationService.submit(closedApplicant.getId(), closedApplicationId);
        jobPostingService.close(closedPostingId);

        assertThatThrownBy(() -> jobApplicationService.withdraw(closedApplicant.getId(), closedApplicationId))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void admin_get_applications_success() {
        Applicant firstApplicant = createApplicant("admin-list-first", "Admin List First");
        Applicant secondApplicant = createApplicant("admin-list-second", "Admin List Second");
        Long jobPostingId = createPublishedJobPosting("Admin List Posting");
        Long firstApplicationId = jobApplicationService.create(
                firstApplicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );
        Long secondApplicationId = jobApplicationService.create(
                secondApplicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );

        PageResponse<AdminApplicationSummaryResponse> response = jobApplicationService.getApplicationsForAdmin(
                null,
                null,
                null,
                0,
                20
        );

        assertThat(response.content()).extracting(AdminApplicationSummaryResponse::applicationId)
                .contains(firstApplicationId, secondApplicationId);
        assertThat(response.totalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void admin_get_applications_filters_by_status_job_posting_and_position() {
        Applicant draftApplicant = createApplicant("admin-filter-draft", "Admin Filter Draft");
        Applicant submittedApplicant = createApplicant("admin-filter-submitted", "Admin Filter Submitted");
        Long jobPostingId = createPublishedJobPosting("Admin Filter Posting");
        List<Long> jobPositionIds = jobPositionIds(jobPostingId);
        Long draftApplicationId = jobApplicationService.create(
                draftApplicant.getId(),
                new ApplicationCreateRequest(jobPostingId, jobPositionIds.get(0))
        );
        Long submittedApplicationId = jobApplicationService.create(
                submittedApplicant.getId(),
                new ApplicationCreateRequest(jobPostingId, jobPositionIds.get(1))
        );
        jobApplicationService.submit(submittedApplicant.getId(), submittedApplicationId);

        PageResponse<AdminApplicationSummaryResponse> statusResponse = jobApplicationService.getApplicationsForAdmin(
                null,
                null,
                " submitted ",
                0,
                20
        );
        PageResponse<AdminApplicationSummaryResponse> postingResponse = jobApplicationService.getApplicationsForAdmin(
                jobPostingId,
                null,
                null,
                0,
                20
        );
        PageResponse<AdminApplicationSummaryResponse> positionResponse = jobApplicationService.getApplicationsForAdmin(
                null,
                jobPositionIds.get(0),
                null,
                0,
                20
        );

        assertThat(statusResponse.content()).extracting(AdminApplicationSummaryResponse::applicationId)
                .contains(submittedApplicationId)
                .doesNotContain(draftApplicationId);
        assertThat(postingResponse.content()).extracting(AdminApplicationSummaryResponse::applicationId)
                .contains(draftApplicationId, submittedApplicationId);
        assertThat(positionResponse.content()).extracting(AdminApplicationSummaryResponse::applicationId)
                .contains(draftApplicationId)
                .doesNotContain(submittedApplicationId);
    }

    @Test
    void admin_get_applications_by_job_posting_success() {
        Applicant firstApplicant = createApplicant("admin-posting-first", "Admin Posting First");
        Applicant secondApplicant = createApplicant("admin-posting-second", "Admin Posting Second");
        Applicant otherApplicant = createApplicant("admin-posting-other", "Admin Posting Other");
        Long jobPostingId = createPublishedJobPosting("Admin Posting Applications");
        Long otherJobPostingId = createPublishedJobPosting("Admin Posting Other");
        Long firstApplicationId = jobApplicationService.create(
                firstApplicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );
        Long secondApplicationId = jobApplicationService.create(
                secondApplicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );
        Long otherApplicationId = jobApplicationService.create(
                otherApplicant.getId(),
                new ApplicationCreateRequest(otherJobPostingId, firstJobPositionId(otherJobPostingId))
        );

        PageResponse<AdminApplicationSummaryResponse> response = jobApplicationService.getApplicationsByJobPostingForAdmin(
                jobPostingId,
                null,
                null,
                0,
                20
        );

        assertThat(response.content()).extracting(AdminApplicationSummaryResponse::applicationId)
                .contains(firstApplicationId, secondApplicationId)
                .doesNotContain(otherApplicationId);
    }

    @Test
    void admin_get_application_detail_success_and_not_found() {
        Applicant applicant = createApplicant("admin-detail", "Admin Detail");
        Long jobPostingId = createPublishedJobPosting("Admin Detail Posting");
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );

        AdminApplicationDetailResponse detail = jobApplicationService.getApplicationForAdmin(applicationId);

        assertThat(detail.applicationId()).isEqualTo(applicationId);
        assertThat(detail.applicantId()).isEqualTo(applicant.getId());
        assertThat(detail.applicantNameSnapshot()).isEqualTo("Admin Detail");
        assertThat(detail.jobPostingTitleSnapshot()).isEqualTo("Admin Detail Posting");
        assertThatThrownBy(() -> jobApplicationService.getApplicationForAdmin(99999L))
                .isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void admin_get_applications_fails_when_paging_or_status_is_invalid() {
        assertThatThrownBy(() -> jobApplicationService.getApplicationsForAdmin(null, null, null, -1, 20))
                .isInstanceOf(InvalidJobApplicationException.class);
        assertThatThrownBy(() -> jobApplicationService.getApplicationsForAdmin(null, null, null, 0, 0))
                .isInstanceOf(InvalidJobApplicationException.class);
        assertThatThrownBy(() -> jobApplicationService.getApplicationsForAdmin(null, null, null, 0, 101))
                .isInstanceOf(InvalidJobApplicationException.class);
        assertThatThrownBy(() -> jobApplicationService.getApplicationsForAdmin(null, null, "UNKNOWN", 0, 20))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void admin_get_applications_by_job_posting_fails_when_job_posting_does_not_exist() {
        assertThatThrownBy(() -> jobApplicationService.getApplicationsByJobPostingForAdmin(99999L, null, null, 0, 20))
                .isInstanceOf(JobPostingNotFoundException.class);
        assertThatThrownBy(() -> jobApplicationService.getApplicationsForAdmin(99999L, null, null, 0, 20))
                .isInstanceOf(JobPostingNotFoundException.class);
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

    private Long createDraftJobPosting() {
        return jobPostingService.create(createJobPostingRequest(
                "2026 recruitment",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0)
        ));
    }

    private Long createPublishedJobPosting() {
        return createPublishedJobPosting("2026 recruitment");
    }

    private Long createPublishedJobPosting(String title) {
        return createPublishedJobPosting(
                title,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0)
        );
    }

    private Long createPublishedJobPosting(String title, LocalDateTime start, LocalDateTime end) {
        Long jobPostingId = jobPostingService.create(createJobPostingRequest(title, start, end));
        jobPostingService.publish(jobPostingId);
        assertThat(jobPostingRepository.findById(jobPostingId).orElseThrow().getStatus()).isEqualTo(JobPostingStatus.PUBLISHED);
        return jobPostingId;
    }

    private Long createPublishedJobPosting(String title, ApplicationFormConfigRequest formConfig) {
        Long jobPostingId = jobPostingService.create(createJobPostingRequest(
                title,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                formConfig
        ));
        jobPostingService.publish(jobPostingId);
        assertThat(jobPostingRepository.findById(jobPostingId).orElseThrow().getStatus()).isEqualTo(JobPostingStatus.PUBLISHED);
        return jobPostingId;
    }

    private JobPostingCreateRequest createJobPostingRequest(String title, LocalDateTime start, LocalDateTime end) {
        return createJobPostingRequest(
                title,
                start,
                end,
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)
        );
    }

    private JobPostingCreateRequest createJobPostingRequest(
            String title,
            LocalDateTime start,
            LocalDateTime end,
            ApplicationFormConfigRequest formConfig
    ) {
        return new JobPostingCreateRequest(
                title,
                "<p>content</p>",
                start,
                end,
                List.of(
                        new JobPositionRequest("Backend", 2, 0),
                        new JobPositionRequest("Frontend", 1, 1)
                ),
                formConfig
        );
    }

    private void assertSubmitValidationFailureKeepsDraft(String loginId, ApplicationFormConfigRequest formConfig) {
        Applicant applicant = createApplicant(loginId, loginId);
        Long jobPostingId = createPublishedJobPosting("Submit Validation " + loginId, formConfig);
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );

        assertThatThrownBy(() -> jobApplicationService.submit(applicant.getId(), applicationId))
                .isInstanceOf(InvalidJobApplicationException.class);

        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();
        assertThat(application.getStatus()).isEqualTo(JobApplicationStatus.DRAFT);
        assertThat(application.getSubmittedAt()).isNull();
    }

    private JobPostingQuestionResponse createQuestion(
            Long jobPostingId,
            boolean required,
            QuestionAnswerType answerType,
            int maxLength
    ) {
        return jobPostingQuestionService.createQuestion(jobPostingId, new JobPostingQuestionCreateRequest(
                null,
                "Submit question",
                "Submit helper",
                QuestionCategory.JOB_SPECIFIC,
                answerType,
                required,
                null,
                maxLength,
                0
        ));
    }

    private Long firstJobPositionId(Long jobPostingId) {
        return jobPositionIds(jobPostingId).get(0);
    }

    private List<Long> jobPositionIds(Long jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        return jobPosting.getJobPositions().stream()
                .sorted(Comparator.comparing(JobPosition::getSortOrder).thenComparing(JobPosition::getId))
                .map(JobPosition::getId)
                .toList();
    }

    private void setReceptionPeriod(Long jobPostingId, LocalDateTime start, LocalDateTime end) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId).orElseThrow();
        jobPosting.updateBasicInfo(
                jobPosting.getTitle(),
                jobPosting.getContentHtml(),
                start,
                end
        );
    }

    private void setJobPostingStatus(Long jobPostingId, JobPostingStatus status) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId).orElseThrow();
        ReflectionTestUtils.setField(jobPosting, "status", status);
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
