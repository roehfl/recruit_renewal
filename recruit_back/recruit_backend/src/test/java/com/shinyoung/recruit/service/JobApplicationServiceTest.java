package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.ApplicationCertificate;
import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.entity.ApplicationLanguage;
import com.shinyoung.recruit.domain.entity.CommonCode;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.CommonCodeRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationLanguageRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPositionRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.AdminApplicationSearchRequest;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationAnswerReplaceRequest;
import com.shinyoung.recruit.dto.request.ApplicationAnswerRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.ApplicationUpdateRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.JobPostingQuestionCreateRequest;
import com.shinyoung.recruit.dto.request.StageCreateRequest;
import com.shinyoung.recruit.dto.request.StageResultUpdateRequest;
import com.shinyoung.recruit.dto.response.JobPostingQuestionResponse;
import com.shinyoung.recruit.dto.response.AdminApplicationDetailResponse;
import com.shinyoung.recruit.dto.response.AdminApplicationSummaryResponse;
import com.shinyoung.recruit.dto.response.ApplicationDetailResponse;
import com.shinyoung.recruit.dto.response.MyApplicationResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.enumeration.DayNightType;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.GraduationStatus;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.enumeration.VeteranStatus;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
    private StageService stageService;

    @Autowired
    private StageResultService stageResultService;

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

    @Autowired
    private CommonCodeRepository commonCodeRepository;

    @Autowired
    private ApplicationBasicInfoRepository basicInfoRepository;

    @Autowired
    private ApplicationEducationRepository educationRepository;

    @Autowired
    private ApplicationCertificateRepository certificateRepository;

    @Autowired
    private ApplicationLanguageRepository languageRepository;

    @Autowired
    private ApplicationAttachmentRepository attachmentRepository;

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
    void create_application_fails_when_job_posting_is_hidden_or_outside_display_period() {
        Applicant hiddenApplicant = createApplicant("applicant-hidden-posting", "Applicant Hidden");
        Long hiddenJobPostingId = createPublishedJobPosting("Hidden Posting");
        setDisplayPolicy(hiddenJobPostingId, false, null, null);

        assertThatThrownBy(() -> jobApplicationService.create(
                hiddenApplicant.getId(),
                new ApplicationCreateRequest(hiddenJobPostingId, firstJobPositionId(hiddenJobPostingId))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant displayApplicant = createApplicant("applicant-display-posting", "Applicant Display");
        Long displayJobPostingId = createPublishedJobPosting("Display Future Posting");
        setDisplayPolicy(
                displayJobPostingId,
                true,
                LocalDateTime.of(2026, 6, 16, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0)
        );

        assertThatThrownBy(() -> jobApplicationService.create(
                displayApplicant.getId(),
                new ApplicationCreateRequest(displayJobPostingId, firstJobPositionId(displayJobPostingId))
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
        jobPosting.replaceJobPositions(List.of(JobPosition.create("Backend", 0)));
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
    void existing_application_commands_ignore_visible_and_display_conditions() {
        Applicant applicant = createApplicant("applicant-existing-display", "Applicant Existing Display");
        Long jobPostingId = createPublishedJobPosting("Existing Display Posting");
        List<Long> jobPositionIds = jobPositionIds(jobPostingId);
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, jobPositionIds.get(0))
        );
        setDisplayPolicy(
                jobPostingId,
                false,
                LocalDateTime.of(2026, 6, 16, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0)
        );

        jobApplicationService.updateDraft(
                applicant.getId(),
                applicationId,
                new ApplicationUpdateRequest(jobPositionIds.get(1))
        );
        seedBasicInfo(applicationId, LocalDate.of(1995, 1, 1));
        jobApplicationService.submit(applicant.getId(), applicationId);
        jobApplicationService.withdraw(applicant.getId(), applicationId);

        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();
        assertThat(application.getJobPosition().getId()).isEqualTo(jobPositionIds.get(1));
        assertThat(application.getStatus()).isEqualTo(JobApplicationStatus.WITHDRAWN);
        assertThat(application.getSubmittedAt()).isNotNull();
        assertThat(application.getWithdrawnAt()).isNotNull();
    }

    @Test
    void update_draft_fails_when_application_is_withdrawn() {
        Applicant submittedApplicant = createApplicant("applicant-update-submitted", "Applicant M");
        Long submittedPostingId = createPublishedJobPosting("Submitted Update Posting");
        List<Long> submittedPositionIds = jobPositionIds(submittedPostingId);
        Long submittedApplicationId = jobApplicationService.create(
                submittedApplicant.getId(),
                new ApplicationCreateRequest(submittedPostingId, submittedPositionIds.get(0))
        );
        seedBasicInfo(submittedApplicationId, LocalDate.of(1995, 1, 1));
        jobApplicationService.submit(submittedApplicant.getId(), submittedApplicationId);

        assertThatCode(() -> jobApplicationService.updateDraft(
                submittedApplicant.getId(),
                submittedApplicationId,
                new ApplicationUpdateRequest(submittedPositionIds.get(1))
        )).doesNotThrowAnyException();

        Applicant withdrawnApplicant = createApplicant("applicant-update-withdrawn", "Applicant N");
        Long withdrawnPostingId = createPublishedJobPosting("Withdrawn Update Posting");
        List<Long> withdrawnPositionIds = jobPositionIds(withdrawnPostingId);
        Long withdrawnApplicationId = jobApplicationService.create(
                withdrawnApplicant.getId(),
                new ApplicationCreateRequest(withdrawnPostingId, withdrawnPositionIds.get(0))
        );
        seedBasicInfo(withdrawnApplicationId, LocalDate.of(1995, 1, 1));
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
        seedBasicInfo(applicationId, LocalDate.of(1995, 1, 1));

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

        seedBasicInfo(applicationId, LocalDate.of(1995, 1, 1));
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
        seedBasicInfo(applicationId, LocalDate.of(1995, 1, 1));

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
        seedBasicInfo(applicationId, LocalDate.of(1995, 1, 1));
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

        seedBasicInfo(applicationId, LocalDate.of(1995, 1, 1));
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
        seedBasicInfo(beforeApplicationId, LocalDate.of(1995, 1, 1));
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
        seedBasicInfo(afterApplicationId, LocalDate.of(1995, 1, 1));
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
        seedBasicInfo(draftApplicationId, LocalDate.of(1995, 1, 1));
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
        seedBasicInfo(closedApplicationId, LocalDate.of(1995, 1, 1));
        jobApplicationService.submit(closedApplicant.getId(), closedApplicationId);
        jobPostingService.close(closedPostingId);

        assertThatThrownBy(() -> jobApplicationService.withdraw(closedApplicant.getId(), closedApplicationId))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void get_my_applications_returns_only_owned_applications_with_all_statuses_and_closed_postings() {
        Applicant applicant = createApplicant("my-list-owner", "My List Owner");
        Applicant otherApplicant = createApplicant("my-list-other", "My List Other");
        Long draftPostingId = createPublishedJobPosting("My List Draft Posting");
        Long submittedPostingId = createPublishedJobPosting("My List Submitted Posting");
        Long withdrawnPostingId = createPublishedJobPosting("My List Withdrawn Posting");
        Long otherPostingId = createPublishedJobPosting("My List Other Posting");

        Long draftApplicationId = createApplication(applicant, draftPostingId);
        Long submittedApplicationId = createApplication(applicant, submittedPostingId);
        seedBasicInfo(submittedApplicationId, LocalDate.of(1995, 1, 1));
        jobApplicationService.submit(applicant.getId(), submittedApplicationId);
        Long withdrawnApplicationId = createApplication(applicant, withdrawnPostingId);
        seedBasicInfo(withdrawnApplicationId, LocalDate.of(1995, 1, 1));
        jobApplicationService.submit(applicant.getId(), withdrawnApplicationId);
        jobApplicationService.withdraw(applicant.getId(), withdrawnApplicationId);
        jobPostingService.close(withdrawnPostingId);
        Long otherApplicationId = createApplication(otherApplicant, otherPostingId);

        PageResponse<MyApplicationResponse> response = jobApplicationService.getMyApplications(applicant.getId(), 0, 20);

        assertThat(response.content()).extracting(MyApplicationResponse::applicationId)
                .contains(draftApplicationId, submittedApplicationId, withdrawnApplicationId)
                .doesNotContain(otherApplicationId);
        assertThat(response.content()).extracting(MyApplicationResponse::applicationStatus)
                .contains(JobApplicationStatus.DRAFT, JobApplicationStatus.SUBMITTED, JobApplicationStatus.WITHDRAWN);
        assertThat(response.content())
                .filteredOn(item -> item.applicationId().equals(withdrawnApplicationId))
                .singleElement()
                .extracting(MyApplicationResponse::jobPostingStatus)
                .isEqualTo(JobPostingStatus.CLOSED);
        assertThat(response.content()).isSortedAccordingTo(
                Comparator
                        .comparing(MyApplicationResponse::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(MyApplicationResponse::applicationId)
                        .reversed()
        );
    }

    @Test
    void get_my_applications_validates_page_and_size() {
        Applicant applicant = createApplicant("my-list-page", "My List Page");

        assertThatThrownBy(() -> jobApplicationService.getMyApplications(applicant.getId(), -1, 20))
                .isInstanceOf(InvalidJobApplicationException.class);
        assertThatThrownBy(() -> jobApplicationService.getMyApplications(applicant.getId(), 0, 0))
                .isInstanceOf(InvalidJobApplicationException.class);
        assertThatThrownBy(() -> jobApplicationService.getMyApplications(applicant.getId(), 0, 101))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void get_my_applications_calculates_accepting() {
        Applicant applicant = createApplicant("my-list-accepting", "My List Accepting");
        Long acceptingPostingId = createPublishedJobPosting("My List Accepting Posting");
        Long futurePostingId = createPublishedJobPosting("My List Future Posting");
        Long closedPostingId = createPublishedJobPosting("My List Closed Posting");
        Long acceptingApplicationId = createApplication(applicant, acceptingPostingId);
        Long futureApplicationId = createApplication(applicant, futurePostingId);
        Long closedApplicationId = createApplication(applicant, closedPostingId);
        setReceptionPeriod(
                futurePostingId,
                LocalDateTime.of(2026, 6, 16, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0)
        );
        jobPostingService.close(closedPostingId);

        PageResponse<MyApplicationResponse> response = jobApplicationService.getMyApplications(applicant.getId(), 0, 20);

        assertThat(findMyApplication(response, acceptingApplicationId).accepting()).isTrue();
        assertThat(findMyApplication(response, futureApplicationId).accepting()).isFalse();
        assertThat(findMyApplication(response, closedApplicationId).accepting()).isFalse();
    }

    @Test
    void get_my_applications_summarizes_only_visible_announced_results() {
        Applicant applicant = createApplicant("my-list-result", "My List Result");
        Long jobPostingId = createPublishedJobPosting("My List Result Posting");
        Long applicationId = createApplication(applicant, jobPostingId);
        seedBasicInfo(applicationId, LocalDate.of(1995, 1, 1));
        jobApplicationService.submit(applicant.getId(), applicationId);
        Long readyStageId = createStage(jobPostingId, 0, false);
        Long inProgressStageId = createStage(jobPostingId, 1, false);
        Long announcedStageId = createStage(jobPostingId, 2, false);
        Long closedStageId = createStage(jobPostingId, 3, true);

        stageResultService.initialize(readyStageId);
        decideResult(jobPostingId, inProgressStageId, StageResultStatus.FAILED);
        decideResult(jobPostingId, announcedStageId, StageResultStatus.FAILED);
        stageService.announce(jobPostingId, announcedStageId);
        decideResult(jobPostingId, closedStageId, StageResultStatus.PASSED);
        stageService.announce(jobPostingId, closedStageId);
        stageService.close(jobPostingId, closedStageId);

        MyApplicationResponse response = findMyApplication(
                jobApplicationService.getMyApplications(applicant.getId(), 0, 20),
                applicationId
        );

        assertThat(response.announcedResultCount()).isEqualTo(2);
        assertThat(response.latestAnnouncedStageName()).isEqualTo("Stage 3");
        assertThat(response.latestResultStatus()).isEqualTo(StageResultStatus.PASSED);
    }

    @Test
    void get_my_applications_returns_empty_page_when_applicant_has_no_applications() {
        Applicant applicant = createApplicant("my-list-empty", "My List Empty");

        PageResponse<MyApplicationResponse> response = jobApplicationService.getMyApplications(applicant.getId(), 0, 20);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
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
                emptySearchRequest(),
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
        seedBasicInfo(submittedApplicationId, LocalDate.of(1995, 1, 1));
        jobApplicationService.submit(submittedApplicant.getId(), submittedApplicationId);

        PageResponse<AdminApplicationSummaryResponse> statusResponse = jobApplicationService.getApplicationsForAdmin(
                null,
                searchRequest(null, " submitted "),
                0,
                20
        );
        PageResponse<AdminApplicationSummaryResponse> postingResponse = jobApplicationService.getApplicationsForAdmin(
                jobPostingId,
                emptySearchRequest(),
                0,
                20
        );
        PageResponse<AdminApplicationSummaryResponse> positionResponse = jobApplicationService.getApplicationsForAdmin(
                null,
                searchRequest(jobPositionIds.get(0), null),
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
                emptySearchRequest(),
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
        assertThatThrownBy(() -> jobApplicationService.getApplicationsForAdmin(null, emptySearchRequest(), -1, 20))
                .isInstanceOf(InvalidJobApplicationException.class);
        assertThatThrownBy(() -> jobApplicationService.getApplicationsForAdmin(null, emptySearchRequest(), 0, 0))
                .isInstanceOf(InvalidJobApplicationException.class);
        assertThatThrownBy(() -> jobApplicationService.getApplicationsForAdmin(null, emptySearchRequest(), 0, 101))
                .isInstanceOf(InvalidJobApplicationException.class);
        assertThatThrownBy(() -> jobApplicationService.getApplicationsForAdmin(null, searchRequest(null, "UNKNOWN"), 0, 20))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void admin_get_applications_fails_when_search_condition_is_invalid() {
        assertThatThrownBy(() -> jobApplicationService.getApplicationsForAdmin(
                null, search().finalEducationLevel("BAD_LEVEL").build(), 0, 20))
                .isInstanceOf(InvalidJobApplicationException.class);
        assertThatThrownBy(() -> jobApplicationService.getApplicationsForAdmin(
                null, search().stageType("BAD_STAGE").build(), 0, 20))
                .isInstanceOf(InvalidJobApplicationException.class);
        assertThatThrownBy(() -> jobApplicationService.getApplicationsForAdmin(
                null,
                search().birthDateFrom(LocalDate.of(2000, 1, 1)).birthDateTo(LocalDate.of(1990, 1, 1)).build(),
                0,
                20))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void admin_get_applications_by_job_posting_fails_when_job_posting_does_not_exist() {
        assertThatThrownBy(() -> jobApplicationService.getApplicationsByJobPostingForAdmin(99999L, emptySearchRequest(), 0, 20))
                .isInstanceOf(JobPostingNotFoundException.class);
        assertThatThrownBy(() -> jobApplicationService.getApplicationsForAdmin(99999L, emptySearchRequest(), 0, 20))
                .isInstanceOf(JobPostingNotFoundException.class);
    }

    @Test
    void admin_search_filters_by_name_and_birth_date() {
        Applicant kimApplicant = createApplicant("search-name-kim", "Kim Search");
        Applicant leeApplicant = createApplicant("search-name-lee", "Lee Search");
        Long jobPostingId = createPublishedJobPosting("Search Name Posting");
        Long kimApplicationId = createApplication(kimApplicant, jobPostingId);
        Long leeApplicationId = createApplication(leeApplicant, jobPostingId);
        seedBasicInfo(kimApplicationId, LocalDate.of(1990, 1, 1));
        seedBasicInfo(leeApplicationId, LocalDate.of(2000, 5, 5));

        PageResponse<AdminApplicationSummaryResponse> nameResponse = jobApplicationService.getApplicationsForAdmin(
                jobPostingId, search().name("Kim").build(), 0, 20);
        PageResponse<AdminApplicationSummaryResponse> birthResponse = jobApplicationService.getApplicationsForAdmin(
                jobPostingId, search().birthDateFrom(LocalDate.of(1995, 1, 1)).build(), 0, 20);

        assertThat(nameResponse.content()).extracting(AdminApplicationSummaryResponse::applicationId)
                .containsExactly(kimApplicationId);
        assertThat(birthResponse.content()).extracting(AdminApplicationSummaryResponse::applicationId)
                .containsExactly(leeApplicationId);
    }

    @Test
    void admin_search_filters_by_phone_number() {
        Applicant hyphenApplicant = createApplicant("search-phone-hyphen", "Phone Hyphen");
        hyphenApplicant.setPhoneNumber("010-1234-5678");
        applicantRepository.save(hyphenApplicant);
        Applicant plainApplicant = createApplicant("search-phone-plain", "Phone Plain");
        plainApplicant.setPhoneNumber("01099998888");
        applicantRepository.save(plainApplicant);

        Long jobPostingId = createPublishedJobPosting("Search Phone Posting");
        Long hyphenApplicationId = createApplication(hyphenApplicant, jobPostingId);
        Long plainApplicationId = createApplication(plainApplicant, jobPostingId);

        // 하이픈으로 저장된 번호를 숫자만으로 검색
        assertThat(searchApplicationIds(jobPostingId, search().phoneNumber("01012345678").build()))
                .containsExactly(hyphenApplicationId);
        // 검색어에 하이픈이 있어도 같은 결과
        assertThat(searchApplicationIds(jobPostingId, search().phoneNumber("010-1234-5678").build()))
                .containsExactly(hyphenApplicationId);
        // 뒷자리 부분일치(전화 문의 시 뒤 4자리만 아는 경우)
        assertThat(searchApplicationIds(jobPostingId, search().phoneNumber("9888").build()))
                .containsExactly(plainApplicationId);
        // 숫자가 없는 검색어는 조건 미적용
        assertThat(searchApplicationIds(jobPostingId, search().phoneNumber("---").build()))
                .containsExactlyInAnyOrder(hyphenApplicationId, plainApplicationId);
    }

    @Test
    void admin_search_filters_by_final_education() {
        Applicant multiApplicant = createApplicant("search-edu-multi", "Edu Multi");
        Applicant singleApplicant = createApplicant("search-edu-single", "Edu Single");
        Long jobPostingId = createPublishedJobPosting("Search Education Posting");
        Long multiApplicationId = createApplication(multiApplicant, jobPostingId);
        Long singleApplicationId = createApplication(singleApplicant, jobPostingId);
        // multi: 고교(야간, 국내, 졸업) + 대학(주간, 해외 US, 졸업예정) — 최종학력 행은 대학
        seedEducation(multiApplicationId, EducationLevel.HIGH_SCHOOL, "Seoul High",
                GraduationStatus.GRADUATED, DayNightType.NIGHT, null, 0);
        seedEducation(multiApplicationId, EducationLevel.UNIVERSITY, "Global Univ",
                GraduationStatus.EXPECTED, DayNightType.DAY, "US", 1);
        // single: 대학(야간, 국내, 졸업) 하나
        seedEducation(singleApplicationId, EducationLevel.UNIVERSITY, "Korea Univ",
                GraduationStatus.GRADUATED, DayNightType.NIGHT, null, 0);

        assertThat(searchApplicationIds(jobPostingId, search().finalEducationLevel("UNIVERSITY").build()))
                .containsExactlyInAnyOrder(multiApplicationId, singleApplicationId);
        assertThat(searchApplicationIds(jobPostingId, search().finalEducationLevel("HIGH_SCHOOL").build()))
                .isEmpty();
        assertThat(searchApplicationIds(jobPostingId, search().graduationStatus("EXPECTED").build()))
                .containsExactly(multiApplicationId);
        assertThat(searchApplicationIds(jobPostingId, search().finalSchoolCondition("OVERSEAS").build()))
                .containsExactly(multiApplicationId);
        assertThat(searchApplicationIds(jobPostingId, search().finalSchoolCondition("DOMESTIC").build()))
                .containsExactly(singleApplicationId);
        // NIGHT 는 최종학력 행 기준 — multi 의 야간은 고교 행이라 제외된다
        assertThat(searchApplicationIds(jobPostingId, search().finalSchoolCondition("NIGHT").build()))
                .containsExactly(singleApplicationId);
        assertThat(searchApplicationIds(jobPostingId, search().schoolName("Seoul").build()))
                .containsExactly(multiApplicationId);
    }

    @Test
    void admin_search_filters_by_certificate_and_language() {
        Applicant certApplicant = createApplicant("search-cert", "Cert Search");
        Applicant plainApplicant = createApplicant("search-plain", "Plain Search");
        Long jobPostingId = createPublishedJobPosting("Search Certificate Posting");
        Long certApplicationId = createApplication(certApplicant, jobPostingId);
        Long plainApplicationId = createApplication(plainApplicant, jobPostingId);
        JobApplication certApplication = jobApplicationRepository.findById(certApplicationId).orElseThrow();
        certificateRepository.save(ApplicationCertificate.create(
                certApplication, "정보처리기사", "한국산업인력공단",
                LocalDate.of(2024, 3, 1), "12345", null, null, 0));
        languageRepository.save(ApplicationLanguage.create(
                certApplication, "ENGLISH", "영어", "OPIC", "OPIc", "IH", "HIGH",
                LocalDate.of(2025, 1, 1), null, "ACTFL", null, 0));

        assertThat(searchApplicationIds(jobPostingId, search().certificateName("정보처리").build()))
                .containsExactly(certApplicationId);
        assertThat(searchApplicationIds(jobPostingId, search().languageName("영어").languageLevel("HIGH").build()))
                .containsExactly(certApplicationId);
        assertThat(searchApplicationIds(jobPostingId, search().languageName("영어").languageLevel("LOW").build()))
                .isEmpty();
        assertThat(searchApplicationIds(jobPostingId, search().certificateName("없는자격증").build()))
                .doesNotContain(certApplicationId, plainApplicationId);
    }

    @Test
    void admin_search_filters_by_stage_result() {
        Applicant passedApplicant = createApplicant("search-stage-passed", "Stage Passed");
        Applicant pendingApplicant = createApplicant("search-stage-pending", "Stage Pending");
        Long passedJobPostingId = createPublishedJobPosting("Search Stage Passed Posting");
        Long pendingJobPostingId = createPublishedJobPosting("Search Stage Pending Posting");
        Long passedApplicationId = createApplication(passedApplicant, passedJobPostingId);
        Long pendingApplicationId = createApplication(pendingApplicant, pendingJobPostingId);
        // StageResult initialize 는 제출된 지원서만 대상이므로 제출을 선행한다
        seedBasicInfo(passedApplicationId, LocalDate.of(1995, 1, 1));
        seedBasicInfo(pendingApplicationId, LocalDate.of(1995, 1, 1));
        jobApplicationService.submit(passedApplicant.getId(), passedApplicationId);
        jobApplicationService.submit(pendingApplicant.getId(), pendingApplicationId);
        Long passedStageId = createStage(passedJobPostingId, 1, false);
        Long pendingStageId = createStage(pendingJobPostingId, 1, false);
        decideResult(passedJobPostingId, passedStageId, StageResultStatus.PASSED);
        stageService.start(pendingJobPostingId, pendingStageId);
        stageResultService.initialize(pendingStageId);

        PageResponse<AdminApplicationSummaryResponse> passedResponse = jobApplicationService.getApplicationsForAdmin(
                null, search().stageType("DOCUMENT").stageResultStatus("PASSED").build(), 0, 100);
        PageResponse<AdminApplicationSummaryResponse> pendingResponse = jobApplicationService.getApplicationsForAdmin(
                null, search().stageType("DOCUMENT").stageResultStatus("PENDING").build(), 0, 100);

        assertThat(passedResponse.content()).extracting(AdminApplicationSummaryResponse::applicationId)
                .contains(passedApplicationId)
                .doesNotContain(pendingApplicationId);
        assertThat(pendingResponse.content()).extracting(AdminApplicationSummaryResponse::applicationId)
                .contains(pendingApplicationId)
                .doesNotContain(passedApplicationId);
    }

    @Test
    void admin_search_response_includes_enriched_fields() {
        Applicant applicant = createApplicant("search-enrich", "Enrich Target");
        Long jobPostingId = createPublishedJobPosting("Search Enrich Posting");
        Long applicationId = createApplication(applicant, jobPostingId);
        seedBasicInfo(applicationId, LocalDate.of(1995, 1, 1));
        seedEducation(applicationId, EducationLevel.HIGH_SCHOOL, "Enrich High",
                GraduationStatus.GRADUATED, DayNightType.DAY, null, 0);
        seedEducation(applicationId, EducationLevel.UNIVERSITY, "Enrich Univ",
                GraduationStatus.GRADUATED, DayNightType.DAY, null, 1);
        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();
        Long attachmentId = attachmentRepository.save(ApplicationAttachment.createStored(
                application, AttachmentType.CAREER_DESCRIPTION, ApplicationSectionType.ATTACHMENT, null,
                "career.pdf", "stored-career.pdf", "attachments/career.pdf", "application/pdf", 1024L, 0)).getId();
        jobApplicationService.submit(applicant.getId(), applicationId);
        Long stageId = createStage(jobPostingId, 1, false);
        decideResult(jobPostingId, stageId, StageResultStatus.PASSED);

        AdminApplicationSummaryResponse row = jobApplicationService
                .getApplicationsForAdmin(jobPostingId, emptySearchRequest(), 0, 20)
                .content().get(0);

        assertThat(row.applicationId()).isEqualTo(applicationId);
        assertThat(row.birthDate()).isEqualTo(LocalDate.of(1995, 1, 1));
        // FIXED_CLOCK = 2026-06-15 기준 만 나이
        assertThat(row.age()).isEqualTo(31);
        assertThat(row.finalEducationLevel()).isEqualTo(EducationLevel.UNIVERSITY);
        assertThat(row.finalSchoolName()).isEqualTo("Enrich Univ");
        assertThat(row.stageType()).isEqualTo(StageType.DOCUMENT);
        assertThat(row.stageResultStatus()).isEqualTo(StageResultStatus.PASSED);
        assertThat(row.careerDescriptionDownloadUrl()).isEqualTo(
                "/admin/applications/%d/attachments/%d/download".formatted(applicationId, attachmentId));
    }

    @Test
    void admin_search_response_enriched_fields_are_null_when_no_related_data() {
        Applicant applicant = createApplicant("search-enrich-empty", "Enrich Empty");
        Long jobPostingId = createPublishedJobPosting("Search Enrich Empty Posting");
        Long applicationId = createApplication(applicant, jobPostingId);

        AdminApplicationSummaryResponse row = jobApplicationService
                .getApplicationsForAdmin(jobPostingId, emptySearchRequest(), 0, 20)
                .content().get(0);

        assertThat(row.applicationId()).isEqualTo(applicationId);
        assertThat(row.birthDate()).isNull();
        assertThat(row.age()).isNull();
        assertThat(row.finalEducationLevel()).isNull();
        assertThat(row.finalSchoolName()).isNull();
        assertThat(row.stageType()).isNull();
        assertThat(row.stageResultStatus()).isNull();
        assertThat(row.careerDescriptionDownloadUrl()).isNull();
    }

    private List<Long> searchApplicationIds(Long jobPostingId, AdminApplicationSearchRequest request) {
        return jobApplicationService.getApplicationsForAdmin(jobPostingId, request, 0, 100).content().stream()
                .map(AdminApplicationSummaryResponse::applicationId)
                .toList();
    }

    private void seedBasicInfo(Long applicationId, LocalDate birthDate) {
        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();
        basicInfoRepository.save(ApplicationBasicInfo.create(
                application, application.getApplicantNameSnapshot(), null, NationalityType.DOMESTIC, null, birthDate,
                "01012345678", null, "test@example.com", VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT,
                null, null, null, null, null, null));
    }

    private void seedEducation(
            Long applicationId,
            EducationLevel educationLevel,
            String schoolName,
            GraduationStatus graduationStatus,
            DayNightType dayNightType,
            String countryCode,
            int sortOrder
    ) {
        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();
        educationRepository.save(ApplicationEducation.create(
                application, educationLevel, schoolName, null, null, null, null,
                null, null, graduationStatus, dayNightType, null, false, countryCode, sortOrder));
    }

    private AdminApplicationSearchRequest emptySearchRequest() {
        return search().build();
    }

    private AdminApplicationSearchRequest searchRequest(Long jobPositionId, String status) {
        return search().jobPositionId(jobPositionId).status(status).build();
    }

    private static SearchRequestBuilder search() {
        return new SearchRequestBuilder();
    }

    private static final class SearchRequestBuilder {
        private Long jobPositionId;
        private String status;
        private String applicationType;
        private String workLocation;
        private String name;
        private String phoneNumber;
        private LocalDate birthDateFrom;
        private LocalDate birthDateTo;
        private String finalEducationLevel;
        private String schoolName;
        private String graduationStatus;
        private String finalSchoolCondition;
        private String certificateName;
        private String languageName;
        private String languageLevel;
        private String stageType;
        private String stageResultStatus;

        SearchRequestBuilder jobPositionId(Long value) { this.jobPositionId = value; return this; }
        SearchRequestBuilder status(String value) { this.status = value; return this; }
        SearchRequestBuilder name(String value) { this.name = value; return this; }
        SearchRequestBuilder phoneNumber(String value) { this.phoneNumber = value; return this; }
        SearchRequestBuilder birthDateFrom(LocalDate value) { this.birthDateFrom = value; return this; }
        SearchRequestBuilder birthDateTo(LocalDate value) { this.birthDateTo = value; return this; }
        SearchRequestBuilder finalEducationLevel(String value) { this.finalEducationLevel = value; return this; }
        SearchRequestBuilder schoolName(String value) { this.schoolName = value; return this; }
        SearchRequestBuilder graduationStatus(String value) { this.graduationStatus = value; return this; }
        SearchRequestBuilder finalSchoolCondition(String value) { this.finalSchoolCondition = value; return this; }
        SearchRequestBuilder certificateName(String value) { this.certificateName = value; return this; }
        SearchRequestBuilder languageName(String value) { this.languageName = value; return this; }
        SearchRequestBuilder languageLevel(String value) { this.languageLevel = value; return this; }
        SearchRequestBuilder stageType(String value) { this.stageType = value; return this; }
        SearchRequestBuilder stageResultStatus(String value) { this.stageResultStatus = value; return this; }

        AdminApplicationSearchRequest build() {
            return new AdminApplicationSearchRequest(
                    jobPositionId, status, applicationType, workLocation, name, phoneNumber,
                    birthDateFrom, birthDateTo, finalEducationLevel, schoolName, graduationStatus,
                    finalSchoolCondition, certificateName, languageName, languageLevel,
                    stageType, stageResultStatus);
        }
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

    @Test
    void 근무지_후보가_있으면_선택값을_스냅샷과_함께_저장한다() {
        Applicant applicant = createApplicant("applicant-work-location", "Work Location Applicant");
        Long jobPostingId = createPublishedJobPostingWithWorkLocations("Work Location Posting");

        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId), "BUSAN")
        );

        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();
        assertThat(application.getWorkLocationCode()).isEqualTo("BUSAN");
        assertThat(application.getWorkLocationNameSnapshot()).isEqualTo("부산");
    }

    @Test
    void 근무지_후보가_있는데_미선택이면_생성_실패() {
        Applicant applicant = createApplicant("applicant-work-location-missing", "Missing");
        Long jobPostingId = createPublishedJobPostingWithWorkLocations("Work Location Missing");

        assertThatThrownBy(() -> jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void 후보에_없는_근무지코드면_생성_실패() {
        Applicant applicant = createApplicant("applicant-work-location-unknown", "Unknown");
        Long jobPostingId = createPublishedJobPostingWithWorkLocations("Work Location Unknown");

        assertThatThrownBy(() -> jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId), "DAEGU")
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void 근무지_후보가_없는_모집분야에_근무지를_보내면_생성_실패() {
        Applicant applicant = createApplicant("applicant-work-location-none", "None");
        Long jobPostingId = createPublishedJobPosting("Work Location None");

        assertThatThrownBy(() -> jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId), "HQ")
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void 제출시_선택한_근무지가_후보에서_사라졌으면_실패() {
        Applicant applicant = createApplicant("applicant-work-location-stale", "Stale");
        Long jobPostingId = createPublishedJobPostingWithWorkLocations("Work Location Stale");
        Long jobPositionId = firstJobPositionId(jobPostingId);
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, jobPositionId, "BUSAN")
        );

        // 공고 수정으로 후보 목록이 줄어든 상황을 재현한다.
        JobPosition jobPosition = jobPositionRepository.findById(jobPositionId).orElseThrow();
        ReflectionTestUtils.setField(jobPosition, "workLocations", new java.util.ArrayList<>(
                List.of(jobPosition.getWorkLocations().get(0))
        ));

        assertThatThrownBy(() -> jobApplicationService.submit(applicant.getId(), applicationId))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    private Long createPublishedJobPostingWithWorkLocations(String title) {
        commonCodeRepository.save(CommonCode.create("WORK_LOCATION", "HQ", "본사", 0, true, null));
        commonCodeRepository.save(CommonCode.create("WORK_LOCATION", "BUSAN", "부산", 1, true, null));

        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                title,
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(new JobPositionRequest(
                        "Backend",
                        null,
                        null,
                        List.of("HQ", "BUSAN"),
                        null,
                        0
                )),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)
        ));
        jobPostingService.publish(jobPostingId);
        return jobPostingId;
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
                        new JobPositionRequest("Backend", 0),
                        new JobPositionRequest("Frontend", 1)
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

    private Long createApplication(Applicant applicant, Long jobPostingId) {
        return jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );
    }

    private Long createStage(Long jobPostingId, int stageOrder, boolean finalStage) {
        return stageService.create(jobPostingId, new StageCreateRequest(
                "Stage " + stageOrder,
                StageType.DOCUMENT,
                stageOrder,
                LocalDateTime.of(2026, 7, stageOrder + 1, 10, 0),
                finalStage
        ));
    }

    private void decideResult(Long jobPostingId, Long stageId, StageResultStatus status) {
        stageService.start(jobPostingId, stageId);
        stageResultService.initialize(stageId);
        Long resultId = stageResultService.getResults(stageId).get(0).stageResultId();
        stageResultService.updateResult(
                stageId,
                resultId,
                new StageResultUpdateRequest(status, new BigDecimal("90.0"), "internal"),
                "employee01"
        );
    }

    private MyApplicationResponse findMyApplication(PageResponse<MyApplicationResponse> response, Long applicationId) {
        return response.content().stream()
                .filter(item -> item.applicationId().equals(applicationId))
                .findFirst()
                .orElseThrow();
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

    private void setDisplayPolicy(
            Long jobPostingId,
            boolean visible,
            LocalDateTime displayStart,
            LocalDateTime displayEnd
    ) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId).orElseThrow();
        jobPosting.updateBasicInfo(
                jobPosting.getTitle(),
                jobPosting.getPostingType(),
                jobPosting.getSummary(),
                jobPosting.getContentHtml(),
                jobPosting.getReceptionStartDateTime(),
                jobPosting.getReceptionEndDateTime(),
                displayStart,
                displayEnd,
                visible,
                jobPosting.isPinned(),
                jobPosting.getDisplayOrder()
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
