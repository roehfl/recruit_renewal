package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationAnswer;
import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.ApplicationMilitary;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.JobPostingAttachmentRequirement;
import com.shinyoung.recruit.domain.entity.JobPostingQuestion;
import com.shinyoung.recruit.domain.repository.ApplicationAnswerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAwardRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationGapPeriodRepository;
import com.shinyoung.recruit.domain.repository.ApplicationLanguageRepository;
import com.shinyoung.recruit.domain.repository.ApplicationMilitaryRepository;
import com.shinyoung.recruit.domain.repository.JobPostingAttachmentRequirementRepository;
import com.shinyoung.recruit.domain.repository.JobPostingQuestionRepository;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentDeleteActorType;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationSubmitValidatorTest {

    private static final long APPLICATION_ID = 1L;
    private static final long JOB_POSTING_ID = 10L;

    @Mock
    private ApplicationEducationRepository educationRepository;

    @Mock
    private ApplicationMilitaryRepository militaryRepository;

    @Mock
    private ApplicationCertificateRepository certificateRepository;

    @Mock
    private ApplicationLanguageRepository languageRepository;

    @Mock
    private ApplicationAwardRepository awardRepository;

    @Mock
    private ApplicationGapPeriodRepository gapPeriodRepository;

    @Mock
    private JobPostingQuestionRepository jobPostingQuestionRepository;

    @Mock
    private ApplicationAnswerRepository applicationAnswerRepository;

    @Mock
    private JobPostingAttachmentRequirementRepository attachmentRequirementRepository;

    @Mock
    private ApplicationAttachmentRepository attachmentRepository;

    @InjectMocks
    private ApplicationSubmitValidator validator;

    @BeforeEach
    void setUp() {
        lenient().when(jobPostingQuestionRepository.findByJobPostingIdAndActiveTrueOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of());
        lenient().when(applicationAnswerRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(List.of());
        lenient().when(attachmentRequirementRepository.findByJobPostingIdAndRequiredTrueOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of());
        lenient().when(attachmentRepository.findByJobApplicationIdAndPhysicalFileStatus(APPLICATION_ID, PhysicalFileStatus.STORED))
                .thenReturn(List.of());
    }

    @Test
    void education_required_fails_when_education_is_missing() {
        ApplicationFormConfig config = config();
        when(config.isUseEducation()).thenReturn(true);
        when(educationRepository.existsByJobApplicationId(APPLICATION_ID)).thenReturn(false);

        assertThatThrownBy(() -> validator.validate(application(config)))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void education_required_passes_when_education_exists() {
        ApplicationFormConfig config = config();
        when(config.isUseEducation()).thenReturn(true);
        when(educationRepository.existsByJobApplicationId(APPLICATION_ID)).thenReturn(true);

        assertThatCode(() -> validator.validate(application(config)))
                .doesNotThrowAnyException();
    }

    @Test
    void education_disabled_passes_without_education() {
        assertThatCode(() -> validator.validate(application(config())))
                .doesNotThrowAnyException();
    }

    @Test
    void military_required_fails_when_record_is_missing() {
        ApplicationFormConfig config = config();
        when(config.isUseMilitary()).thenReturn(true);
        when(militaryRepository.findByJobApplicationId(APPLICATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate(application(config)))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void military_subject_types_pass_when_record_exists() {
        assertMilitaryPasses(MilitarySubjectType.SUBJECT, null, null, "졸업 예정으로 입대 연기");
        assertMilitaryPasses(MilitarySubjectType.NOT_SUBJECT, null, null, null);
    }

    @Test
    void subject_military_fails_without_non_service_reason() {
        ApplicationFormConfig config = config();
        when(config.isUseMilitary()).thenReturn(true);
        when(militaryRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(military(MilitarySubjectType.SUBJECT, null, null, " ")));

        assertThatThrownBy(() -> validator.validate(application(config)))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void completed_military_fails_without_service_period() {
        ApplicationFormConfig config = config();
        when(config.isUseMilitary()).thenReturn(true);
        when(militaryRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(military(MilitarySubjectType.COMPLETED, null, null, null)));

        assertThatThrownBy(() -> validator.validate(application(config)))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void completed_military_passes_with_service_period() {
        assertMilitaryPasses(
                MilitarySubjectType.COMPLETED,
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2021, 6, 30),
                null
        );
    }

    @Test
    void exempted_military_fails_without_exemption_reason() {
        ApplicationFormConfig config = config();
        when(config.isUseMilitary()).thenReturn(true);
        when(militaryRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(military(MilitarySubjectType.EXEMPTED, null, null, " ")));

        assertThatThrownBy(() -> validator.validate(application(config)))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void exempted_military_passes_with_exemption_reason() {
        assertMilitaryPasses(MilitarySubjectType.EXEMPTED, null, null, "test reason");
    }

    @Test
    void military_disabled_passes_without_record() {
        assertThatCode(() -> validator.validate(application(config())))
                .doesNotThrowAnyException();
    }

    @Test
    void optional_sections_and_attachment_absence_do_not_block_submit_validation() {
        ApplicationFormConfig config = config();
        lenient().when(config.isUseCertificate()).thenReturn(true);
        lenient().when(config.isUseLanguage()).thenReturn(true);
        lenient().when(config.isUseAward()).thenReturn(true);
        lenient().when(config.isUseGapPeriod()).thenReturn(true);

        assertThatCode(() -> validator.validate(application(config)))
                .doesNotThrowAnyException();
    }

    @Test
    void education_career_military_optional_do_not_block_submit_validation() {
        ApplicationFormConfig config = config();
        when(config.isUseEducation()).thenReturn(true);
        when(config.isRequireEducation()).thenReturn(false);
        when(config.isUseCareer()).thenReturn(true);
        when(config.isRequireCareer()).thenReturn(false);
        when(config.isUseMilitary()).thenReturn(true);
        when(config.isRequireMilitary()).thenReturn(false);

        assertThatCode(() -> validator.validate(application(config)))
                .doesNotThrowAnyException();
    }

    @Test
    void optional_domain_sections_required_fail_when_rows_are_missing() {
        assertRequiredSimpleSectionFails("certificate");
        assertRequiredSimpleSectionFails("language");
        assertRequiredSimpleSectionFails("award");
        assertRequiredSimpleSectionFails("gapPeriod");
    }

    @Test
    void optional_domain_sections_required_pass_when_rows_exist() {
        ApplicationFormConfig config = config();
        when(config.isUseCertificate()).thenReturn(true);
        when(config.isRequireCertificate()).thenReturn(true);
        when(config.isUseLanguage()).thenReturn(true);
        when(config.isRequireLanguage()).thenReturn(true);
        when(config.isUseAward()).thenReturn(true);
        when(config.isRequireAward()).thenReturn(true);
        when(config.isUseGapPeriod()).thenReturn(true);
        when(config.isRequireGapPeriod()).thenReturn(true);
        when(certificateRepository.existsByJobApplicationId(APPLICATION_ID)).thenReturn(true);
        when(languageRepository.existsByJobApplicationId(APPLICATION_ID)).thenReturn(true);
        when(awardRepository.existsByJobApplicationId(APPLICATION_ID)).thenReturn(true);
        when(gapPeriodRepository.existsByJobApplicationId(APPLICATION_ID)).thenReturn(true);

        assertThatCode(() -> validator.validate(application(config)))
                .doesNotThrowAnyException();
    }

    @Test
    void required_attachment_missing_fails_submit() {
        when(attachmentRequirementRepository.findByJobPostingIdAndRequiredTrueOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(requirement(true, 1, AttachmentType.RESUME, ApplicationSectionType.APPLICATION, "Resume")));

        assertThatThrownBy(() -> validator.validate(application(config())))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void matching_stored_attachment_allows_submit() {
        when(attachmentRequirementRepository.findByJobPostingIdAndRequiredTrueOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(requirement(true, 1, AttachmentType.RESUME, ApplicationSectionType.APPLICATION, "Resume")));
        when(attachmentRepository.findByJobApplicationIdAndPhysicalFileStatus(APPLICATION_ID, PhysicalFileStatus.STORED))
                .thenReturn(List.of(attachment(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, false)));

        assertThatCode(() -> validator.validate(application(config())))
                .doesNotThrowAnyException();
    }

    @Test
    void wrong_type_wrong_section_and_deleted_attachment_do_not_satisfy_requirement() {
        when(attachmentRequirementRepository.findByJobPostingIdAndRequiredTrueOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(requirement(true, 1, AttachmentType.RESUME, ApplicationSectionType.APPLICATION, "Resume")));
        when(attachmentRepository.findByJobApplicationIdAndPhysicalFileStatus(APPLICATION_ID, PhysicalFileStatus.STORED))
                .thenReturn(List.of(
                        attachment(AttachmentType.PORTFOLIO, ApplicationSectionType.APPLICATION, false),
                        attachment(AttachmentType.RESUME, ApplicationSectionType.CAREER, false),
                        attachment(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, true)
                ));

        assertThatThrownBy(() -> validator.validate(application(config())))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void required_min_count_requires_enough_matching_stored_files() {
        when(attachmentRequirementRepository.findByJobPostingIdAndRequiredTrueOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(requirement(true, 2, AttachmentType.RESUME, ApplicationSectionType.APPLICATION, "Resume")));
        when(attachmentRepository.findByJobApplicationIdAndPhysicalFileStatus(APPLICATION_ID, PhysicalFileStatus.STORED))
                .thenReturn(List.of(attachment(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, false)));

        assertThatThrownBy(() -> validator.validate(application(config())))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void optional_attachment_missing_does_not_fail_submit() {
        assertThatCode(() -> validator.validate(application(config())))
                .doesNotThrowAnyException();
    }

    @Test
    void answer_validation_passes_when_active_questions_are_missing() {
        assertThatCode(() -> validator.validate(application(config())))
                .doesNotThrowAnyException();
    }

    @Test
    void required_answer_passes_when_non_blank_answer_exists() {
        JobPostingQuestion question = question(100L, true, QuestionAnswerType.LONG_TEXT, 1000);
        ApplicationAnswer answer = answer(question, "answer");
        when(jobPostingQuestionRepository.findByJobPostingIdAndActiveTrueOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(question));
        when(applicationAnswerRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(List.of(answer));

        assertThatCode(() -> validator.validate(application(config())))
                .doesNotThrowAnyException();
    }

    @Test
    void optional_answer_passes_when_answer_is_missing_or_blank() {
        JobPostingQuestion missing = question(100L, false, QuestionAnswerType.LONG_TEXT, 1000);
        when(jobPostingQuestionRepository.findByJobPostingIdAndActiveTrueOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(missing));
        when(applicationAnswerRepository.findByJobApplicationId(APPLICATION_ID)).thenReturn(List.of());

        assertThatCode(() -> validator.validate(application(config())))
                .doesNotThrowAnyException();

        JobPostingQuestion blank = question(101L, false, QuestionAnswerType.SHORT_TEXT, 500);
        ApplicationAnswer blankAnswer = answer(blank, "   ");
        when(jobPostingQuestionRepository.findByJobPostingIdAndActiveTrueOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(blank));
        when(applicationAnswerRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(List.of(blankAnswer));

        assertThatCode(() -> validator.validate(application(config())))
                .doesNotThrowAnyException();
    }

    @Test
    void required_answer_fails_when_answer_row_is_missing() {
        JobPostingQuestion question = question(100L, true, QuestionAnswerType.LONG_TEXT, 1000);
        when(jobPostingQuestionRepository.findByJobPostingIdAndActiveTrueOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(question));
        when(applicationAnswerRepository.findByJobApplicationId(APPLICATION_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> validator.validate(application(config())))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void required_answer_fails_when_answer_text_is_null_or_blank() {
        assertRequiredAnswerTextFails(null);
        assertRequiredAnswerTextFails("");
        assertRequiredAnswerTextFails("   ");
    }

    @Test
    void answer_validation_fails_when_answer_exceeds_question_max_length() {
        JobPostingQuestion question = question(100L, false, QuestionAnswerType.LONG_TEXT, 10);
        ApplicationAnswer answer = answer(question, "a".repeat(11));
        when(jobPostingQuestionRepository.findByJobPostingIdAndActiveTrueOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(question));
        when(applicationAnswerRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(List.of(answer));

        assertThatThrownBy(() -> validator.validate(application(config())))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void answer_validation_fails_when_short_text_exceeds_type_limit() {
        JobPostingQuestion question = question(100L, false, QuestionAnswerType.SHORT_TEXT, 1000);
        ApplicationAnswer answer = answer(question, "a".repeat(501));
        when(jobPostingQuestionRepository.findByJobPostingIdAndActiveTrueOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(question));
        when(applicationAnswerRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(List.of(answer));

        assertThatThrownBy(() -> validator.validate(application(config())))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void answer_validation_fails_when_long_text_exceeds_type_limit() {
        JobPostingQuestion question = question(100L, false, QuestionAnswerType.LONG_TEXT, 6000);
        ApplicationAnswer answer = answer(question, "a".repeat(5001));
        when(jobPostingQuestionRepository.findByJobPostingIdAndActiveTrueOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(question));
        when(applicationAnswerRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(List.of(answer));

        assertThatThrownBy(() -> validator.validate(application(config())))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void inactive_question_and_foreign_answer_are_not_submit_validation_targets() {
        JobPostingQuestion activeOptional = question(100L, false, QuestionAnswerType.LONG_TEXT, 1000);
        JobPostingQuestion inactiveRequired = question(101L, true, QuestionAnswerType.LONG_TEXT, 1000);
        ApplicationAnswer inactiveAnswer = answer(inactiveRequired, null);
        when(jobPostingQuestionRepository.findByJobPostingIdAndActiveTrueOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(activeOptional));
        when(applicationAnswerRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(List.of(inactiveAnswer));

        assertThatCode(() -> validator.validate(application(config())))
                .doesNotThrowAnyException();
    }

    @Test
    void answer_validation_uses_type_default_when_question_max_length_is_null() {
        JobPostingQuestion question = question(100L, false, QuestionAnswerType.SHORT_TEXT, null);
        ApplicationAnswer answer = answer(question, "a".repeat(501));
        when(jobPostingQuestionRepository.findByJobPostingIdAndActiveTrueOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(question));
        when(applicationAnswerRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(List.of(answer));

        assertThatThrownBy(() -> validator.validate(application(config())))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    private void assertMilitaryPasses(
            MilitarySubjectType subjectType,
            LocalDate serviceStartDate,
            LocalDate serviceEndDate,
            String nonServiceReason
    ) {
        ApplicationFormConfig config = config();
        when(config.isUseMilitary()).thenReturn(true);
        when(militaryRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(military(subjectType, serviceStartDate, serviceEndDate, nonServiceReason)));

        assertThatCode(() -> validator.validate(application(config)))
                .doesNotThrowAnyException();
    }

    private JobApplication application(ApplicationFormConfig config) {
        JobPosting jobPosting = mock(JobPosting.class);
        when(jobPosting.getApplicationFormConfig()).thenReturn(config);
        lenient().when(jobPosting.getId()).thenReturn(JOB_POSTING_ID);

        JobApplication application = mock(JobApplication.class);
        when(application.getId()).thenReturn(APPLICATION_ID);
        when(application.getJobPosting()).thenReturn(jobPosting);
        return application;
    }

    private ApplicationFormConfig config() {
        return mock(ApplicationFormConfig.class, invocation -> switch (invocation.getMethod().getName()) {
            case "isRequireEducation" -> ((ApplicationFormConfig) invocation.getMock()).isUseEducation();
            case "isRequireCareer" -> ((ApplicationFormConfig) invocation.getMock()).isUseCareer();
            case "isRequireMilitary" -> ((ApplicationFormConfig) invocation.getMock()).isUseMilitary();
            default -> RETURNS_DEFAULTS.answer(invocation);
        });
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

    private void assertRequiredAnswerTextFails(String answerText) {
        JobPostingQuestion question = question(100L, true, QuestionAnswerType.LONG_TEXT, 1000);
        ApplicationAnswer answer = answer(question, answerText);
        when(jobPostingQuestionRepository.findByJobPostingIdAndActiveTrueOrderBySortOrderAscIdAsc(JOB_POSTING_ID))
                .thenReturn(List.of(question));
        when(applicationAnswerRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(List.of(answer));

        assertThatThrownBy(() -> validator.validate(application(config())))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    private void assertRequiredSimpleSectionFails(String section) {
        ApplicationFormConfig config = config();
        switch (section) {
            case "certificate" -> {
                when(config.isUseCertificate()).thenReturn(true);
                when(config.isRequireCertificate()).thenReturn(true);
                when(certificateRepository.existsByJobApplicationId(APPLICATION_ID)).thenReturn(false);
            }
            case "language" -> {
                when(config.isUseLanguage()).thenReturn(true);
                when(config.isRequireLanguage()).thenReturn(true);
                when(languageRepository.existsByJobApplicationId(APPLICATION_ID)).thenReturn(false);
            }
            case "award" -> {
                when(config.isUseAward()).thenReturn(true);
                when(config.isRequireAward()).thenReturn(true);
                when(awardRepository.existsByJobApplicationId(APPLICATION_ID)).thenReturn(false);
            }
            case "gapPeriod" -> {
                when(config.isUseGapPeriod()).thenReturn(true);
                when(config.isRequireGapPeriod()).thenReturn(true);
                when(gapPeriodRepository.existsByJobApplicationId(APPLICATION_ID)).thenReturn(false);
            }
            default -> throw new IllegalArgumentException("Unknown section: " + section);
        }

        assertThatThrownBy(() -> validator.validate(application(config)))
                .isInstanceOf(InvalidJobApplicationException.class);
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
        when(answer.getJobPostingQuestion()).thenReturn(question);
        lenient().when(answer.getAnswerText()).thenReturn(answerText);
        return answer;
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
                    java.time.LocalDateTime.of(2026, 6, 2, 0, 0)
            );
        }
        return attachment;
    }
}
