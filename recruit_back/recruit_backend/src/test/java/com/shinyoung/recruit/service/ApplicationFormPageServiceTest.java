package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.ApplicationFormPage;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicationFormPageRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingAttachmentRequirementRepository;
import com.shinyoung.recruit.domain.repository.JobPostingQuestionRepository;
import com.shinyoung.recruit.dto.response.ApplicationFormPageResponse;
import com.shinyoung.recruit.dto.response.ApplicationFormSectionResponse;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.JobPostingType;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationFormPageServiceTest {

    private static final Long APPLICANT_ID = 1L;
    private static final Long APPLICATION_ID = 10L;
    private static final Long JOB_POSTING_ID = 100L;
    private static final Long JOB_POSITION_ID = 200L;
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Mock
    private JobApplicationRepository jobApplicationRepository;

    @Mock
    private ApplicationFormPageRepository applicationFormPageRepository;

    @Mock
    private JobPostingQuestionRepository jobPostingQuestionRepository;

    @Mock
    private JobPostingAttachmentRequirementRepository attachmentRequirementRepository;

    private ApplicationFormPageService formPageService;

    @BeforeEach
    void setUp() {
        formPageService = new ApplicationFormPageService(
                jobApplicationRepository,
                applicationFormPageRepository,
                jobPostingQuestionRepository,
                attachmentRequirementRepository,
                new ApplicationFormLayoutDefaultFactory(),
                new ApplicationFormLayoutValidator(),
                FIXED_CLOCK
        );

        lenient().when(applicationFormPageRepository.findByJobPostingIdWithItems(JOB_POSTING_ID))
                .thenReturn(List.of());
        lenient().when(jobPostingQuestionRepository.existsByJobPostingIdAndActiveTrue(JOB_POSTING_ID))
                .thenReturn(false);
        lenient().when(jobPostingQuestionRepository.existsByJobPostingIdAndActiveTrueAndRequiredTrue(JOB_POSTING_ID))
                .thenReturn(false);
        lenient().when(attachmentRequirementRepository.existsByJobPostingId(JOB_POSTING_ID))
                .thenReturn(false);
        lenient().when(attachmentRequirementRepository.existsByJobPostingIdAndRequiredTrue(JOB_POSTING_ID))
                .thenReturn(false);
    }

    @Test
    void owned_application_form_page_returns_summary_and_default_layout_sections() {
        ApplicationFormConfig config = config(
                true, true,
                false, false,
                false, false,
                false, false,
                true, false,
                false, false,
                false, false
        );
        JobApplication application = application(JobApplicationStatus.DRAFT, posting(JobPostingStatus.PUBLISHED, config));
        stubApplication(application);

        ApplicationFormPageResponse response = formPageService.getFormPage(APPLICANT_ID, APPLICATION_ID);

        assertThat(response.applicationId()).isEqualTo(APPLICATION_ID);
        assertThat(response.jobPostingId()).isEqualTo(JOB_POSTING_ID);
        assertThat(response.jobPostingTitle()).isEqualTo("Snapshot Posting");
        assertThat(response.jobPostingStatus()).isEqualTo(JobPostingStatus.PUBLISHED);
        assertThat(response.jobPositionId()).isEqualTo(JOB_POSITION_ID);
        assertThat(response.jobPositionName()).isEqualTo("Snapshot Backend");
        assertThat(response.applicationStatus()).isEqualTo(JobApplicationStatus.DRAFT);
        assertThat(response.accepting()).isTrue();
        assertThat(response.editable()).isTrue();
        assertThat(response.formConfig().useEducation()).isTrue();
        assertThat(response.formConfig().requireEducation()).isTrue();
        assertThat(response.formConfig().useMilitary()).isTrue();
        assertThat(response.formConfig().requireMilitary()).isFalse();
        assertThat(response.sections())
                .extracting(ApplicationFormSectionResponse::sectionType)
                .containsExactly(
                        ApplicationSectionType.BASIC_INFO,
                        ApplicationSectionType.MILITARY,
                        ApplicationSectionType.EDUCATION
                );
        assertThat(response.sections())
                .extracting(ApplicationFormSectionResponse::required)
                .containsExactly(true, false, true);
        assertThat(response.sections())
                .extracting(ApplicationFormSectionResponse::pageNo)
                .containsExactly(1, 1, 2);
        assertThat(response.sections())
                .extracting(ApplicationFormSectionResponse::pageTitle)
                .containsExactly("Basic Info", "Basic Info", "Education And Career");
    }

    @Test
    void form_page_exposes_job_posting_type() {
        JobPosting jobPosting = posting(JobPostingStatus.PUBLISHED, config());
        when(jobPosting.getPostingType()).thenReturn(JobPostingType.EXPERIENCED_RECRUITMENT);
        stubApplication(application(JobApplicationStatus.DRAFT, jobPosting));

        ApplicationFormPageResponse response = formPageService.getFormPage(APPLICANT_ID, APPLICATION_ID);

        assertThat(response.postingType()).isEqualTo(JobPostingType.EXPERIENCED_RECRUITMENT);
    }

    @Test
    void stored_layout_order_is_used_when_layout_exists() {
        ApplicationFormConfig config = config(true, true, false, false, true, false, false);
        JobPosting jobPosting = posting(JobPostingStatus.PUBLISHED, config);
        JobApplication application = application(JobApplicationStatus.DRAFT, jobPosting);
        ApplicationFormPage page1 = page(jobPosting, 1, 0, ApplicationSectionType.BASIC_INFO, ApplicationSectionType.MILITARY);
        ApplicationFormPage page2 = page(jobPosting, 2, 1, ApplicationSectionType.EDUCATION, ApplicationSectionType.CAREER);
        stubApplication(application);
        when(applicationFormPageRepository.findByJobPostingIdWithItems(JOB_POSTING_ID))
                .thenReturn(List.of(page2, page1));

        ApplicationFormPageResponse response = formPageService.getFormPage(APPLICANT_ID, APPLICATION_ID);

        assertThat(response.sections())
                .extracting(ApplicationFormSectionResponse::sectionType)
                .containsExactly(
                        ApplicationSectionType.BASIC_INFO,
                        ApplicationSectionType.MILITARY,
                        ApplicationSectionType.EDUCATION,
                        ApplicationSectionType.CAREER
                );
        assertThat(response.sections())
                .extracting(ApplicationFormSectionResponse::sortOrder)
                .containsExactly(0, 1, 2, 3);
        assertThat(response.sections())
                .extracting(ApplicationFormSectionResponse::pageNo)
                .containsExactly(1, 1, 2, 2);
        assertThat(response.sections())
                .extracting(ApplicationFormSectionResponse::pageTitle)
                .containsExactly("Page 1", "Page 1", "Page 2", "Page 2");
    }

    @Test
    void other_applicants_application_is_hidden() {
        when(jobApplicationRepository.findFormPageByIdAndApplicantId(APPLICATION_ID, APPLICANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> formPageService.getFormPage(APPLICANT_ID, APPLICATION_ID))
                .isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void selected_job_position_must_belong_to_application_job_posting() {
        JobPosting applicationPosting = posting(JobPostingStatus.PUBLISHED, config());
        JobPosting otherPosting = posting(999L, JobPostingStatus.PUBLISHED, config());
        JobApplication application = application(JobApplicationStatus.DRAFT, applicationPosting, otherPosting);
        stubApplication(application);

        assertThatThrownBy(() -> formPageService.getFormPage(APPLICANT_ID, APPLICATION_ID))
                .isInstanceOf(InvalidJobApplicationException.class)
                .hasMessageContaining("모집분야");
    }

    @Test
    void accepting_is_false_outside_period_or_when_posting_is_not_published() {
        JobApplication beforePeriod = application(
                JobApplicationStatus.DRAFT,
                posting(JobPostingStatus.PUBLISHED, config(), LocalDateTime.of(2026, 7, 1, 9, 0), LocalDateTime.of(2026, 7, 30, 18, 0))
        );
        stubApplication(beforePeriod);
        assertThat(formPageService.getFormPage(APPLICANT_ID, APPLICATION_ID).accepting()).isFalse();
        assertThat(formPageService.getFormPage(APPLICANT_ID, APPLICATION_ID).editable()).isFalse();

        JobApplication closed = application(JobApplicationStatus.DRAFT, posting(JobPostingStatus.CLOSED, config()));
        stubApplication(closed);
        assertThat(formPageService.getFormPage(APPLICANT_ID, APPLICATION_ID).accepting()).isFalse();
    }

    @Test
    void editable_is_true_only_for_draft_accepting_application() {
        JobPosting acceptingPosting = posting(JobPostingStatus.PUBLISHED, config());
        stubApplication(application(JobApplicationStatus.SUBMITTED, acceptingPosting));
        assertThat(formPageService.getFormPage(APPLICANT_ID, APPLICATION_ID).editable()).isFalse();

        stubApplication(application(JobApplicationStatus.WITHDRAWN, acceptingPosting));
        assertThat(formPageService.getFormPage(APPLICANT_ID, APPLICATION_ID).editable()).isFalse();
    }

    @Test
    void form_config_question_and_attachment_policy_drive_sections() {
        ApplicationFormConfig config = config(false, false, false, false, false, true, true);
        stubApplication(application(JobApplicationStatus.DRAFT, posting(JobPostingStatus.PUBLISHED, config)));
        when(jobPostingQuestionRepository.existsByJobPostingIdAndActiveTrue(JOB_POSTING_ID))
                .thenReturn(true);
        when(jobPostingQuestionRepository.existsByJobPostingIdAndActiveTrueAndRequiredTrue(JOB_POSTING_ID))
                .thenReturn(true);
        when(attachmentRequirementRepository.existsByJobPostingId(JOB_POSTING_ID))
                .thenReturn(true);
        when(attachmentRequirementRepository.existsByJobPostingIdAndRequiredTrue(JOB_POSTING_ID))
                .thenReturn(true);

        ApplicationFormPageResponse response = formPageService.getFormPage(APPLICANT_ID, APPLICATION_ID);

        assertThat(response.sections())
                .extracting(ApplicationFormSectionResponse::sectionType)
                .containsExactly(
                        ApplicationSectionType.BASIC_INFO,
                        ApplicationSectionType.AWARD,
                        ApplicationSectionType.GAP_PERIOD,
                        ApplicationSectionType.QUESTION_ANSWER,
                        ApplicationSectionType.ATTACHMENT
                );
        assertThat(response.sections())
                .filteredOn(ApplicationFormSectionResponse::required)
                .extracting(ApplicationFormSectionResponse::sectionType)
                .containsExactlyInAnyOrder(
                        ApplicationSectionType.BASIC_INFO,
                        ApplicationSectionType.QUESTION_ANSWER,
                        ApplicationSectionType.ATTACHMENT
                );
    }

    @Test
    void response_dto_does_not_define_forbidden_fields() {
        List<String> fieldNames = Arrays.stream(ApplicationFormPageResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(fieldNames).doesNotContain(
                "applicantId",
                "stageResultId",
                "score",
                "comment",
                "decidedBy",
                "correctedBy",
                "storagePath",
                "storedFileName"
        );
    }

    private void stubApplication(JobApplication application) {
        when(jobApplicationRepository.findFormPageByIdAndApplicantId(APPLICATION_ID, APPLICANT_ID))
                .thenReturn(Optional.of(application));
    }

    private ApplicationFormPage page(
            JobPosting jobPosting,
            int pageNo,
            int pageSortOrder,
            ApplicationSectionType first,
            ApplicationSectionType second
    ) {
        ApplicationFormPage page = ApplicationFormPage.create(
                jobPosting,
                pageNo,
                "Page " + pageNo,
                null,
                pageSortOrder
        );
        page.addItem(first, 0);
        page.addItem(second, 1);
        return page;
    }

    private JobApplication application(JobApplicationStatus status, JobPosting jobPosting) {
        return application(status, jobPosting, jobPosting);
    }

    private JobApplication application(JobApplicationStatus status, JobPosting jobPosting, JobPosting positionPosting) {
        JobPosition jobPosition = mock(JobPosition.class);
        lenient().when(jobPosition.getId()).thenReturn(JOB_POSITION_ID);
        lenient().when(jobPosition.getPositionName()).thenReturn("Backend");
        lenient().when(jobPosition.getJobPosting()).thenReturn(positionPosting);

        JobApplication application = mock(JobApplication.class);
        lenient().when(application.getId()).thenReturn(APPLICATION_ID);
        lenient().when(application.getJobPosting()).thenReturn(jobPosting);
        lenient().when(application.getJobPosition()).thenReturn(jobPosition);
        lenient().when(application.getJobPostingTitleSnapshot()).thenReturn("Snapshot Posting");
        lenient().when(application.getJobPositionNameSnapshot()).thenReturn("Snapshot Backend");
        lenient().when(application.getStatus()).thenReturn(status);
        lenient().when(application.getSubmittedAt()).thenReturn(null);
        lenient().when(application.getWithdrawnAt()).thenReturn(null);
        return application;
    }

    private JobPosting posting(JobPostingStatus status, ApplicationFormConfig config) {
        return posting(status, config, LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 30, 18, 0));
    }

    private JobPosting posting(
            JobPostingStatus status,
            ApplicationFormConfig config,
            LocalDateTime receptionStartDateTime,
            LocalDateTime receptionEndDateTime
    ) {
        return posting(JOB_POSTING_ID, status, config, receptionStartDateTime, receptionEndDateTime);
    }

    private JobPosting posting(Long id, JobPostingStatus status, ApplicationFormConfig config) {
        return posting(id, status, config, LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 30, 18, 0));
    }

    private JobPosting posting(
            Long id,
            JobPostingStatus status,
            ApplicationFormConfig config,
            LocalDateTime receptionStartDateTime,
            LocalDateTime receptionEndDateTime
    ) {
        JobPosting jobPosting = mock(JobPosting.class);
        lenient().when(jobPosting.getId()).thenReturn(id);
        lenient().when(jobPosting.getTitle()).thenReturn("Form Page Posting");
        lenient().when(jobPosting.getStatus()).thenReturn(status);
        lenient().when(jobPosting.getReceptionStartDateTime()).thenReturn(receptionStartDateTime);
        lenient().when(jobPosting.getReceptionEndDateTime()).thenReturn(receptionEndDateTime);
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
}
