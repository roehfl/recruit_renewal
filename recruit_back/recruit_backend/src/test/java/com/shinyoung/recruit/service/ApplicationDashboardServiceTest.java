package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationAnswer;
import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.ApplicationMilitary;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPostingAttachmentRequirement;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.JobPostingQuestion;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicationAnswerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAwardRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationGapPeriodRepository;
import com.shinyoung.recruit.domain.repository.ApplicationLanguageRepository;
import com.shinyoung.recruit.domain.repository.ApplicationMilitaryRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingAttachmentRequirementRepository;
import com.shinyoung.recruit.domain.repository.JobPostingQuestionRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import com.shinyoung.recruit.dto.response.ApplicationDashboardResponse;
import com.shinyoung.recruit.dto.response.ApplicationSectionReadinessResponse;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentDeleteActorType;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationDashboardServiceTest {

    private static final Long APPLICANT_ID = 1L;
    private static final Long APPLICATION_ID = 10L;
    private static final Long JOB_POSTING_ID = 100L;
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Mock
    private JobApplicationRepository jobApplicationRepository;

    @Mock
    private StageResultRepository stageResultRepository;

    @Mock
    private ApplicationEducationRepository educationRepository;

    @Mock
    private ApplicationMilitaryRepository militaryRepository;

    @Mock
    private JobPostingQuestionRepository jobPostingQuestionRepository;

    @Mock
    private ApplicationAnswerRepository applicationAnswerRepository;

    @Mock
    private ApplicationCertificateRepository certificateRepository;

    @Mock
    private ApplicationLanguageRepository languageRepository;

    @Mock
    private ApplicationAwardRepository awardRepository;

    @Mock
    private ApplicationGapPeriodRepository gapPeriodRepository;

    @Mock
    private JobPostingAttachmentRequirementRepository attachmentRequirementRepository;

    @Mock
    private ApplicationAttachmentRepository attachmentRepository;

    @Mock
    private ApplicationBasicInfoRepository basicInfoRepository;

    private ApplicationDashboardService dashboardService;

    @BeforeEach
    void setUp() {
        ApplicationCompletionReadChecker checker = new ApplicationCompletionReadChecker(
                basicInfoRepository,
                educationRepository,
                militaryRepository,
                jobPostingQuestionRepository,
                applicationAnswerRepository,
                certificateRepository,
                languageRepository,
                awardRepository,
                gapPeriodRepository,
                attachmentRequirementRepository,
                attachmentRepository
        );
        dashboardService = new ApplicationDashboardService(
                jobApplicationRepository,
                stageResultRepository,
                checker,
                FIXED_CLOCK
        );

        lenient().when(stageResultRepository.findVisibleByJobApplicationIdForApplicant(APPLICATION_ID))
                .thenReturn(List.of());
        lenient().when(jobPostingQuestionRepository.findByJobPostingIdAndActiveTrueOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of());
        lenient().when(applicationAnswerRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(List.of());
        lenient().when(attachmentRequirementRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of());
        lenient().when(attachmentRepository.findByJobApplicationIdAndPhysicalFileStatus(APPLICATION_ID, PhysicalFileStatus.STORED))
                .thenReturn(List.of());
        lenient().when(militaryRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(Optional.empty());
        // Default: BASIC_INFO absent (always-required group, will show as missing)
        lenient().when(basicInfoRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(Optional.empty());
    }

    @Test
    void draft_accepting_complete_application_is_editable_and_submittable() {
        ApplicationFormConfig config = config(true, true, false, false, true, false, false);
        JobApplication application = application(JobApplicationStatus.DRAFT, acceptingPosting(config));
        stubDashboard(application);
        when(basicInfoRepository.findByJobApplicationId(APPLICATION_ID)).thenReturn(Optional.of(validBasicInfo()));
        when(educationRepository.existsByJobApplicationId(APPLICATION_ID)).thenReturn(true);
        when(militaryRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(military(
                        MilitarySubjectType.COMPLETED,
                        LocalDate.of(2020, 1, 1),
                        LocalDate.of(2021, 1, 1),
                        null
                )));

        ApplicationDashboardResponse response = dashboardService.getDashboard(APPLICANT_ID, APPLICATION_ID);

        assertThat(response.accepting()).isTrue();
        assertThat(response.editable()).isTrue();
        assertThat(response.submittable()).isTrue();
        assertThat(response.withdrawable()).isFalse();
        // BASIC_INFO is now always a required group (+1): education + military + basic_info = 3 (career removed)
        assertThat(response.completionSummary().requiredSectionCount()).isEqualTo(3);
        assertThat(response.completionSummary().completedRequiredSectionCount()).isEqualTo(3);
        assertThat(response.completionSummary().requiredCompletionRate()).isEqualTo(100);
        assertThat(response.requiredMissingSections()).isEmpty();
    }

    @Test
    void draft_with_blocking_issue_is_not_submittable() {
        ApplicationFormConfig config = config(true, false, false, false, false, false, false);
        JobApplication application = application(JobApplicationStatus.DRAFT, acceptingPosting(config));
        stubDashboard(application);
        when(basicInfoRepository.findByJobApplicationId(APPLICATION_ID)).thenReturn(Optional.of(validBasicInfo()));
        when(educationRepository.existsByJobApplicationId(APPLICATION_ID)).thenReturn(false);

        ApplicationDashboardResponse response = dashboardService.getDashboard(APPLICANT_ID, APPLICATION_ID);

        assertThat(response.editable()).isTrue();
        assertThat(response.submittable()).isFalse();
        // BASIC_INFO required + present = 1 completed; EDUCATION required + missing = 0 completed → 2 required total, 1 completed
        assertThat(response.completionSummary().requiredSectionCount()).isEqualTo(2);
        assertThat(response.completionSummary().completedRequiredSectionCount()).isEqualTo(1);
        assertThat(response.completionSummary().requiredCompletionRate()).isEqualTo(50);
        assertThat(response.requiredMissingSections())
                .extracting(ApplicationSectionReadinessResponse::sectionCode, ApplicationSectionReadinessResponse::reasonCode)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("EDUCATION", "MISSING_ROW"));
    }

    @Test
    void missing_form_config_is_blocking() {
        JobApplication application = application(JobApplicationStatus.DRAFT, acceptingPosting(null));
        stubDashboard(application);
        when(basicInfoRepository.findByJobApplicationId(APPLICATION_ID)).thenReturn(Optional.of(validBasicInfo()));

        ApplicationDashboardResponse response = dashboardService.getDashboard(APPLICANT_ID, APPLICATION_ID);

        assertThat(response.editable()).isTrue();
        assertThat(response.submittable()).isFalse();
        // BASIC_INFO present (1 completed required) + FORM_CONFIG missing (1 incomplete required) = 2 required total
        assertThat(response.completionSummary().requiredSectionCount()).isEqualTo(2);
        assertThat(response.completionSummary().submitBlockingIssueCount()).isEqualTo(1);
        assertThat(response.requiredMissingSections())
                .extracting(ApplicationSectionReadinessResponse::sectionCode, ApplicationSectionReadinessResponse::reasonCode)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("FORM_CONFIG", "MISSING_CONFIG"));
    }

    @Test
    void submitted_accepting_application_is_only_withdrawable() {
        JobApplication application = application(JobApplicationStatus.SUBMITTED, acceptingPosting(config()));
        stubDashboard(application);

        ApplicationDashboardResponse response = dashboardService.getDashboard(APPLICANT_ID, APPLICATION_ID);

        assertThat(response.editable()).isFalse();
        assertThat(response.submittable()).isFalse();
        assertThat(response.withdrawable()).isTrue();
    }

    @Test
    void withdrawn_application_has_all_command_flags_false() {
        JobApplication application = application(JobApplicationStatus.WITHDRAWN, acceptingPosting(config()));
        stubDashboard(application);

        ApplicationDashboardResponse response = dashboardService.getDashboard(APPLICANT_ID, APPLICATION_ID);

        assertThat(response.accepting()).isTrue();
        assertThat(response.editable()).isFalse();
        assertThat(response.submittable()).isFalse();
        assertThat(response.withdrawable()).isFalse();
    }

    @Test
    void closed_posting_is_not_accepting_and_all_command_flags_are_false() {
        JobApplication application = application(JobApplicationStatus.DRAFT, closedPosting(config()));
        stubDashboard(application);

        ApplicationDashboardResponse response = dashboardService.getDashboard(APPLICANT_ID, APPLICATION_ID);

        assertThat(response.accepting()).isFalse();
        assertThat(response.editable()).isFalse();
        assertThat(response.submittable()).isFalse();
        assertThat(response.withdrawable()).isFalse();
    }

    @Test
    void other_applicants_application_is_hidden() {
        when(jobApplicationRepository.findDashboardByIdAndApplicantId(APPLICATION_ID, APPLICANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.getDashboard(APPLICANT_ID, APPLICATION_ID))
                .isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void military_missing_row_is_blocking() {
        ApplicationDashboardResponse response = dashboardFor(config(false, false, false, false, true, false, false));

        assertRequiredIssue(response, "MILITARY", "MISSING_ROW");
    }

    @Test
    void military_completed_missing_period_is_blocking() {
        when(militaryRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(military(MilitarySubjectType.COMPLETED, null, null, null)));

        ApplicationDashboardResponse response = dashboardFor(config(false, false, false, false, true, false, false));

        assertRequiredIssue(response, "MILITARY", "MISSING_PERIOD");
    }

    @Test
    void military_exempted_blank_reason_is_blocking() {
        when(militaryRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(military(MilitarySubjectType.EXEMPTED, null, null, " ")));

        ApplicationDashboardResponse response = dashboardFor(config(false, false, false, false, true, false, false));

        assertRequiredIssue(response, "MILITARY", "MISSING_REASON");
    }

    @Test
    void required_question_missing_or_blank_is_blocking() {
        JobPostingQuestion missing = question(1000L, true, QuestionAnswerType.LONG_TEXT, 1000);
        JobPostingQuestion blank = question(1001L, true, QuestionAnswerType.SHORT_TEXT, 500);
        ApplicationAnswer blankAnswer = answer(blank, " ");
        when(jobPostingQuestionRepository.findByJobPostingIdAndActiveTrueOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(missing, blank));
        when(applicationAnswerRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(List.of(blankAnswer));

        ApplicationDashboardResponse response = dashboardFor(config());

        assertThat(response.requiredMissingSections())
                .extracting(ApplicationSectionReadinessResponse::sectionCode, ApplicationSectionReadinessResponse::reasonCode)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("QUESTION", "MISSING_REQUIRED_ANSWER"),
                        org.assertj.core.groups.Tuple.tuple("QUESTION", "MISSING_REQUIRED_ANSWER")
                );
    }

    @Test
    void answer_length_violation_is_blocking_without_exposing_answer_text() {
        JobPostingQuestion question = question(1000L, false, QuestionAnswerType.SHORT_TEXT, 1000);
        ApplicationAnswer longAnswer = answer(question, "a".repeat(501));
        when(jobPostingQuestionRepository.findByJobPostingIdAndActiveTrueOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(question));
        when(applicationAnswerRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(List.of(longAnswer));

        ApplicationDashboardResponse response = dashboardFor(config());

        assertRequiredIssue(response, "QUESTION", "INVALID_LENGTH");
        assertThat(response.requiredMissingSections().get(0).message()).doesNotContain("aaaa");
    }

    @Test
    void optional_empty_sections_are_not_blocking() {
        ApplicationFormConfig config = config(false, false, true, true, false, true, true);
        when(basicInfoRepository.findByJobApplicationId(APPLICATION_ID)).thenReturn(Optional.of(validBasicInfo()));
        ApplicationDashboardResponse response = dashboardFor(config);

        assertThat(response.submittable()).isTrue();
        assertThat(response.requiredMissingSections()).isEmpty();
        assertThat(response.optionalIncompleteSections())
                .extracting(ApplicationSectionReadinessResponse::sectionCode, ApplicationSectionReadinessResponse::reasonCode)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("CERTIFICATE", "OPTIONAL_EMPTY"),
                        org.assertj.core.groups.Tuple.tuple("LANGUAGE", "OPTIONAL_EMPTY"),
                        org.assertj.core.groups.Tuple.tuple("AWARD", "OPTIONAL_EMPTY"),
                        org.assertj.core.groups.Tuple.tuple("GAP_PERIOD", "OPTIONAL_EMPTY")
                );
        assertThat(response.completionSummary().submitBlockingIssueCount()).isZero();
        assertThat(response.completionSummary().optionalIncompleteCount()).isEqualTo(4);
    }

    @Test
    void enabled_but_not_required_core_sections_are_optional_and_not_blocking() {
        ApplicationFormConfig config = config(
                true, false,
                true, false,
                false, false,
                false, false,
                true, false,
                false, false,
                false, false
        );
        when(basicInfoRepository.findByJobApplicationId(APPLICATION_ID)).thenReturn(Optional.of(validBasicInfo()));

        ApplicationDashboardResponse response = dashboardFor(config);

        assertThat(response.submittable()).isTrue();
        // BASIC_INFO is always required and present → 1 required, 1 completed (not blocking)
        assertThat(response.completionSummary().requiredSectionCount()).isEqualTo(1);
        assertThat(response.completionSummary().completedRequiredSectionCount()).isEqualTo(1);
        assertThat(response.completionSummary().optionalSectionCount()).isEqualTo(2);
        assertThat(response.optionalIncompleteSections())
                .extracting(ApplicationSectionReadinessResponse::sectionCode)
                .containsExactlyInAnyOrder("EDUCATION", "MILITARY");
        assertThat(response.optionalIncompleteSections())
                .extracting(ApplicationSectionReadinessResponse::message)
                .allSatisfy(message -> assertThat(message).doesNotContain("required before submit"));
    }

    @Test
    void optional_domain_section_can_be_required_and_block_submit() {
        ApplicationFormConfig config = config(
                false, false,
                false, false,
                true, true,
                true, true,
                false, false,
                true, true,
                true, true
        );
        when(basicInfoRepository.findByJobApplicationId(APPLICATION_ID)).thenReturn(Optional.of(validBasicInfo()));

        ApplicationDashboardResponse response = dashboardFor(config);

        assertThat(response.submittable()).isFalse();
        // BASIC_INFO present (completed) + 4 domain required sections missing = 5 required total
        assertThat(response.completionSummary().requiredSectionCount()).isEqualTo(5);
        assertThat(response.requiredMissingSections())
                .extracting(ApplicationSectionReadinessResponse::sectionCode, ApplicationSectionReadinessResponse::reasonCode)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("CERTIFICATE", "MISSING_ROW"),
                        org.assertj.core.groups.Tuple.tuple("LANGUAGE", "MISSING_ROW"),
                        org.assertj.core.groups.Tuple.tuple("AWARD", "MISSING_ROW"),
                        org.assertj.core.groups.Tuple.tuple("GAP_PERIOD", "MISSING_ROW")
                );
    }

    @Test
    void required_attachment_missing_is_blocking() {
        when(attachmentRequirementRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(requirement(true, 1, AttachmentType.RESUME, ApplicationSectionType.APPLICATION, "Resume")));

        ApplicationDashboardResponse response = dashboardFor(config());

        assertThat(response.submittable()).isFalse();
        assertThat(response.requiredMissingSections())
                .extracting(ApplicationSectionReadinessResponse::sectionCode, ApplicationSectionReadinessResponse::reasonCode)
                .contains(org.assertj.core.groups.Tuple.tuple("ATTACHMENT", "REQUIRED_ATTACHMENT_MISSING"));
    }

    @Test
    void required_attachment_is_complete_only_with_matching_stored_row() {
        when(attachmentRequirementRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(requirement(true, 1, AttachmentType.RESUME, ApplicationSectionType.APPLICATION, "Resume")));
        when(attachmentRepository.findByJobApplicationIdAndPhysicalFileStatus(APPLICATION_ID, PhysicalFileStatus.STORED))
                .thenReturn(List.of(attachment(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, false)));
        when(basicInfoRepository.findByJobApplicationId(APPLICATION_ID)).thenReturn(Optional.of(validBasicInfo()));

        ApplicationDashboardResponse response = dashboardFor(config());

        assertThat(response.submittable()).isTrue();
        assertThat(response.requiredMissingSections()).isEmpty();
        // BASIC_INFO required + present, ATTACHMENT required + present = 2 required, 2 completed
        assertThat(response.completionSummary().requiredSectionCount()).isEqualTo(2);
        assertThat(response.completionSummary().completedRequiredSectionCount()).isEqualTo(2);
    }

    @Test
    void required_attachment_group_ignores_optional_missing_issue_when_required_requirement_exists() {
        when(attachmentRequirementRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(
                        requirement(true, 1, AttachmentType.RESUME, ApplicationSectionType.APPLICATION, "Resume"),
                        requirement(false, 1, AttachmentType.PORTFOLIO, ApplicationSectionType.APPLICATION, "Portfolio")
                ));
        when(attachmentRepository.findByJobApplicationIdAndPhysicalFileStatus(APPLICATION_ID, PhysicalFileStatus.STORED))
                .thenReturn(List.of(attachment(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, false)));
        when(basicInfoRepository.findByJobApplicationId(APPLICATION_ID)).thenReturn(Optional.of(validBasicInfo()));

        ApplicationDashboardResponse response = dashboardFor(config());

        assertThat(response.submittable()).isTrue();
        assertThat(response.requiredMissingSections()).isEmpty();
        assertThat(response.optionalIncompleteSections()).isEmpty();
        // BASIC_INFO required + present, ATTACHMENT required + present = 2 required, 2 completed
        assertThat(response.completionSummary().requiredSectionCount()).isEqualTo(2);
        assertThat(response.completionSummary().completedRequiredSectionCount()).isEqualTo(2);
        assertThat(response.completionSummary().optionalSectionCount()).isZero();
        assertThat(response.completionSummary().optionalIncompleteCount()).isZero();
    }

    @Test
    void wrong_or_deleted_attachment_does_not_satisfy_required_rule() {
        when(attachmentRequirementRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(requirement(true, 1, AttachmentType.RESUME, ApplicationSectionType.APPLICATION, "Resume")));
        when(attachmentRepository.findByJobApplicationIdAndPhysicalFileStatus(APPLICATION_ID, PhysicalFileStatus.STORED))
                .thenReturn(List.of(
                        attachment(AttachmentType.PORTFOLIO, ApplicationSectionType.APPLICATION, false),
                        attachment(AttachmentType.RESUME, ApplicationSectionType.CAREER, false),
                        attachment(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, true)
                ));

        ApplicationDashboardResponse response = dashboardFor(config());

        assertRequiredIssue(response, "ATTACHMENT", "REQUIRED_ATTACHMENT_MISSING");
    }

    @Test
    void optional_attachment_min_count_zero_does_not_create_optional_issue() {
        when(attachmentRequirementRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(requirement(false, 0, AttachmentType.PORTFOLIO, ApplicationSectionType.APPLICATION, "Portfolio")));
        when(basicInfoRepository.findByJobApplicationId(APPLICATION_ID)).thenReturn(Optional.of(validBasicInfo()));

        ApplicationDashboardResponse response = dashboardFor(config());

        assertThat(response.submittable()).isTrue();
        assertThat(response.optionalIncompleteSections()).isEmpty();
        assertThat(response.completionSummary().optionalSectionCount()).isZero();
    }

    @Test
    void optional_attachment_min_count_positive_creates_non_blocking_issue() {
        when(attachmentRequirementRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(requirement(false, 1, AttachmentType.PORTFOLIO, ApplicationSectionType.APPLICATION, "Portfolio")));
        when(basicInfoRepository.findByJobApplicationId(APPLICATION_ID)).thenReturn(Optional.of(validBasicInfo()));

        ApplicationDashboardResponse response = dashboardFor(config());

        assertThat(response.submittable()).isTrue();
        assertThat(response.optionalIncompleteSections())
                .extracting(ApplicationSectionReadinessResponse::sectionCode, ApplicationSectionReadinessResponse::reasonCode)
                .contains(org.assertj.core.groups.Tuple.tuple("ATTACHMENT", "OPTIONAL_ATTACHMENT_MISSING"));
        assertThat(response.optionalIncompleteSections())
                .extracting(ApplicationSectionReadinessResponse::message)
                .allSatisfy(message -> assertThat(message).doesNotContain("required before submit"));
    }

    @Test
    void latest_result_summary_uses_highest_visible_stage_order_and_id() {
        JobApplication application = application(JobApplicationStatus.DRAFT, acceptingPosting(config()));
        stubDashboard(application);
        StageResult documentResult = stageResult(1, 10L, "Document", StageResultStatus.FAILED);
        StageResult interviewResult = stageResult(2, 20L, "Interview", StageResultStatus.PASSED);
        when(stageResultRepository.findVisibleByJobApplicationIdForApplicant(APPLICATION_ID))
                .thenReturn(List.of(documentResult, interviewResult));

        ApplicationDashboardResponse response = dashboardService.getDashboard(APPLICANT_ID, APPLICATION_ID);

        assertThat(response.latestAnnouncedStageName()).isEqualTo("Interview");
        assertThat(response.latestResultStatus()).isEqualTo(StageResultStatus.PASSED);
    }

    private ApplicationDashboardResponse dashboardFor(ApplicationFormConfig config) {
        JobApplication application = application(JobApplicationStatus.DRAFT, acceptingPosting(config));
        stubDashboard(application);
        return dashboardService.getDashboard(APPLICANT_ID, APPLICATION_ID);
    }

    private void assertRequiredIssue(ApplicationDashboardResponse response, String sectionCode, String reasonCode) {
        assertThat(response.submittable()).isFalse();
        assertThat(response.requiredMissingSections())
                .extracting(ApplicationSectionReadinessResponse::sectionCode, ApplicationSectionReadinessResponse::reasonCode)
                .contains(org.assertj.core.groups.Tuple.tuple(sectionCode, reasonCode));
    }

    private void stubDashboard(JobApplication application) {
        when(jobApplicationRepository.findDashboardByIdAndApplicantId(APPLICATION_ID, APPLICANT_ID))
                .thenReturn(Optional.of(application));
    }

    private JobApplication application(JobApplicationStatus status, JobPosting jobPosting) {
        JobPosition jobPosition = mock(JobPosition.class);
        lenient().when(jobPosition.getPositionName()).thenReturn("Backend");

        JobApplication application = mock(JobApplication.class);
        lenient().when(application.getId()).thenReturn(APPLICATION_ID);
        lenient().when(application.getJobPosting()).thenReturn(jobPosting);
        lenient().when(application.getJobPosition()).thenReturn(jobPosition);
        lenient().when(application.getJobPostingTitleSnapshot()).thenReturn("Dashboard Posting");
        lenient().when(application.getJobPositionNameSnapshot()).thenReturn("Backend");
        lenient().when(application.getStatus()).thenReturn(status);
        return application;
    }

    private JobPosting acceptingPosting(ApplicationFormConfig config) {
        return posting(JobPostingStatus.PUBLISHED, config);
    }

    private JobPosting closedPosting(ApplicationFormConfig config) {
        return posting(JobPostingStatus.CLOSED, config);
    }

    private JobPosting posting(JobPostingStatus status, ApplicationFormConfig config) {
        JobPosting jobPosting = mock(JobPosting.class);
        lenient().when(jobPosting.getId()).thenReturn(JOB_POSTING_ID);
        lenient().when(jobPosting.getTitle()).thenReturn("Dashboard Posting");
        lenient().when(jobPosting.getStatus()).thenReturn(status);
        lenient().when(jobPosting.getReceptionStartDateTime()).thenReturn(LocalDateTime.of(2026, 6, 1, 9, 0));
        lenient().when(jobPosting.getReceptionEndDateTime()).thenReturn(LocalDateTime.of(2026, 6, 30, 18, 0));
        lenient().when(jobPosting.getApplicationFormConfig()).thenReturn(config);
        return jobPosting;
    }

    private ApplicationFormConfig config() {
        return config(false, false, false, false, false, false, false);
    }

    private ApplicationFormConfig config(
            boolean useEducation,
            boolean useCareer,
            boolean useCertificate,
            boolean useLanguage,
            boolean useMilitary,
            boolean useAward,
            boolean useGapPeriod
    ) {
        ApplicationFormConfig config = mock(ApplicationFormConfig.class);
        lenient().when(config.isUseEducation()).thenReturn(useEducation);
        lenient().when(config.isRequireEducation()).thenReturn(useEducation);
        lenient().when(config.isUseCareer()).thenReturn(useCareer);
        lenient().when(config.isRequireCareer()).thenReturn(useCareer);
        lenient().when(config.isUseCertificate()).thenReturn(useCertificate);
        lenient().when(config.isRequireCertificate()).thenReturn(false);
        lenient().when(config.isUseLanguage()).thenReturn(useLanguage);
        lenient().when(config.isRequireLanguage()).thenReturn(false);
        lenient().when(config.isUseMilitary()).thenReturn(useMilitary);
        lenient().when(config.isRequireMilitary()).thenReturn(useMilitary);
        lenient().when(config.isUseAward()).thenReturn(useAward);
        lenient().when(config.isRequireAward()).thenReturn(false);
        lenient().when(config.isUseGapPeriod()).thenReturn(useGapPeriod);
        lenient().when(config.isRequireGapPeriod()).thenReturn(false);
        return config;
    }

    private ApplicationFormConfig config(
            boolean useEducation,
            boolean requireEducation,
            boolean useCareer,
            boolean requireCareer,
            boolean useCertificate,
            boolean requireCertificate,
            boolean useLanguage,
            boolean requireLanguage,
            boolean useMilitary,
            boolean requireMilitary,
            boolean useAward,
            boolean requireAward,
            boolean useGapPeriod,
            boolean requireGapPeriod
    ) {
        ApplicationFormConfig config = mock(ApplicationFormConfig.class);
        lenient().when(config.isUseEducation()).thenReturn(useEducation);
        lenient().when(config.isRequireEducation()).thenReturn(requireEducation);
        lenient().when(config.isUseCareer()).thenReturn(useCareer);
        lenient().when(config.isRequireCareer()).thenReturn(requireCareer);
        lenient().when(config.isUseCertificate()).thenReturn(useCertificate);
        lenient().when(config.isRequireCertificate()).thenReturn(requireCertificate);
        lenient().when(config.isUseLanguage()).thenReturn(useLanguage);
        lenient().when(config.isRequireLanguage()).thenReturn(requireLanguage);
        lenient().when(config.isUseMilitary()).thenReturn(useMilitary);
        lenient().when(config.isRequireMilitary()).thenReturn(requireMilitary);
        lenient().when(config.isUseAward()).thenReturn(useAward);
        lenient().when(config.isRequireAward()).thenReturn(requireAward);
        lenient().when(config.isUseGapPeriod()).thenReturn(useGapPeriod);
        lenient().when(config.isRequireGapPeriod()).thenReturn(requireGapPeriod);
        return config;
    }

    private ApplicationBasicInfo validBasicInfo() {
        return ApplicationBasicInfo.create(
                mock(JobApplication.class),
                "홍길동", null, NationalityType.DOMESTIC, null,
                java.time.LocalDate.of(1995, 1, 1), "01012345678", null, "test@example.com",
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT,
                null, null, null, null, null, null
        );
    }

    private ApplicationMilitary military(
            MilitarySubjectType subjectType,
            LocalDate serviceStartDate,
            LocalDate serviceEndDate,
            String nonServiceReason
    ) {
        return ApplicationMilitary.create(
                mock(JobApplication.class),
                subjectType,
                null,
                null,
                null,
                serviceStartDate,
                serviceEndDate,
                nonServiceReason
        );
    }

    private JobPostingQuestion question(Long id, boolean required, QuestionAnswerType answerType, Integer maxLength) {
        JobPostingQuestion question = mock(JobPostingQuestion.class);
        lenient().when(question.getId()).thenReturn(id);
        lenient().when(question.getRequired()).thenReturn(required);
        lenient().when(question.getAnswerType()).thenReturn(answerType);
        lenient().when(question.getMaxLength()).thenReturn(maxLength);
        return question;
    }

    private ApplicationAnswer answer(JobPostingQuestion question, String answerText) {
        ApplicationAnswer answer = mock(ApplicationAnswer.class);
        lenient().when(answer.getJobPostingQuestion()).thenReturn(question);
        lenient().when(answer.getAnswerText()).thenReturn(answerText);
        return answer;
    }

    private StageResult stageResult(int stageOrder, Long stageId, String stageName, StageResultStatus status) {
        Stage stage = mock(Stage.class);
        lenient().when(stage.getStageOrder()).thenReturn(stageOrder);
        lenient().when(stage.getId()).thenReturn(stageId);
        lenient().when(stage.getStageName()).thenReturn(stageName);

        StageResult result = mock(StageResult.class);
        lenient().when(result.getStage()).thenReturn(stage);
        lenient().when(result.getResultStatus()).thenReturn(status);
        return result;
    }

    private JobPostingAttachmentRequirement requirement(
            boolean required,
            int minCount,
            AttachmentType attachmentType,
            ApplicationSectionType sectionType,
            String displayName
    ) {
        return JobPostingAttachmentRequirement.create(
                null,
                attachmentType,
                sectionType,
                required,
                minCount,
                0,
                displayName,
                null
        );
    }

    private ApplicationAttachment attachment(
            AttachmentType attachmentType,
            ApplicationSectionType sectionType,
            boolean deleted
    ) {
        ApplicationAttachment attachment = ApplicationAttachment.createStored(
                null,
                attachmentType,
                sectionType,
                null,
                "test.pdf",
                "stored.pdf",
                "/test/stored.pdf",
                "application/pdf",
                100L,
                0
        );
        if (deleted) {
            attachment.markDeleted(
                    "test",
                    AttachmentDeleteActorType.APPLICANT,
                    "test delete",
                    LocalDateTime.of(2026, 6, 2, 0, 0)
            );
        }
        return attachment;
    }
}
