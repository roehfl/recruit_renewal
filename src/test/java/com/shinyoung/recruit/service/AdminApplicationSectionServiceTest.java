package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.domain.entity.ApplicationAnswer;
import com.shinyoung.recruit.domain.entity.ApplicationAward;
import com.shinyoung.recruit.domain.entity.ApplicationCareer;
import com.shinyoung.recruit.domain.entity.ApplicationCareerProfile;
import com.shinyoung.recruit.domain.entity.ApplicationCertificate;
import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.entity.ApplicationEducationSemesterGrade;
import com.shinyoung.recruit.domain.entity.ApplicationGapPeriod;
import com.shinyoung.recruit.domain.entity.ApplicationLanguage;
import com.shinyoung.recruit.domain.entity.ApplicationMilitary;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.JobPostingQuestion;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAnswerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAwardRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerProfileRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationSemesterGradeRepository;
import com.shinyoung.recruit.domain.repository.ApplicationGapPeriodRepository;
import com.shinyoung.recruit.domain.repository.ApplicationLanguageRepository;
import com.shinyoung.recruit.domain.repository.ApplicationMilitaryRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.JobPostingQuestionRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.response.AdminAttachmentResponse;
import com.shinyoung.recruit.dto.response.AdminApplicationAnswerResponse;
import com.shinyoung.recruit.dto.response.AdminApplicationStageResultResponse;
import com.shinyoung.recruit.dto.response.AdminAwardResponse;
import com.shinyoung.recruit.dto.response.AdminCareerResponse;
import com.shinyoung.recruit.dto.response.AdminCertificateResponse;
import com.shinyoung.recruit.dto.response.AdminEducationResponse;
import com.shinyoung.recruit.dto.response.AdminGapPeriodResponse;
import com.shinyoung.recruit.dto.response.AdminLanguageResponse;
import com.shinyoung.recruit.dto.response.AdminMilitaryResponse;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.enumeration.CampusType;
import com.shinyoung.recruit.enumeration.CareerType;
import com.shinyoung.recruit.enumeration.DayNightType;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.EmploymentType;
import com.shinyoung.recruit.enumeration.GapType;
import com.shinyoung.recruit.enumeration.GraduationStatus;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.MilitaryBranch;
import com.shinyoung.recruit.enumeration.MilitaryRank;
import com.shinyoung.recruit.enumeration.MilitaryServiceType;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.lang.reflect.RecordComponent;
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
class AdminApplicationSectionServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private AdminApplicationSectionService adminApplicationSectionService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ApplicationEducationRepository educationRepository;

    @Autowired
    private ApplicationEducationSemesterGradeRepository semesterGradeRepository;

    @Autowired
    private ApplicationCareerProfileRepository careerProfileRepository;

    @Autowired
    private ApplicationCareerRepository careerRepository;

    @Autowired
    private ApplicationCertificateRepository certificateRepository;

    @Autowired
    private ApplicationLanguageRepository languageRepository;

    @Autowired
    private ApplicationMilitaryRepository militaryRepository;

    @Autowired
    private ApplicationAwardRepository awardRepository;

    @Autowired
    private ApplicationGapPeriodRepository gapPeriodRepository;

    @Autowired
    private ApplicationAttachmentRepository attachmentRepository;

    @Autowired
    private JobPostingQuestionRepository jobPostingQuestionRepository;

    @Autowired
    private ApplicationAnswerRepository applicationAnswerRepository;

    @Autowired
    private StageRepository stageRepository;

    @Autowired
    private StageResultRepository stageResultRepository;

    @Test
    void get_educations_returns_sorted_educations_and_semester_grades() {
        JobApplication application = createApplication("admin-section-education");
        ApplicationEducation second = educationRepository.save(education(application, EducationLevel.UNIVERSITY, "Second University", 1));
        ApplicationEducation first = educationRepository.save(education(application, EducationLevel.HIGH_SCHOOL, "First High School", 0));
        semesterGradeRepository.save(ApplicationEducationSemesterGrade.create(
                second,
                2,
                1,
                new BigDecimal("18.0"),
                new BigDecimal("4.1"),
                new BigDecimal("4.5"),
                null,
                null
        ));
        semesterGradeRepository.save(ApplicationEducationSemesterGrade.create(
                second,
                1,
                2,
                new BigDecimal("18.0"),
                new BigDecimal("4.0"),
                new BigDecimal("4.5"),
                null,
                null
        ));

        List<AdminEducationResponse> responses = adminApplicationSectionService.getEducations(application.getId());

        assertThat(responses).extracting(AdminEducationResponse::educationId)
                .containsExactly(first.getId(), second.getId());
        assertThat(responses.get(1).semesterGrades())
                .extracting(grade -> "%d-%d".formatted(grade.schoolYear(), grade.semester()))
                .containsExactly("1-2", "2-1");
    }

    @Test
    void get_empty_sections_returns_empty_or_default_response() {
        JobApplication application = createApplication("admin-section-empty");

        assertThat(adminApplicationSectionService.getEducations(application.getId())).isEmpty();
        assertThat(adminApplicationSectionService.getCertificates(application.getId())).isEmpty();
        assertThat(adminApplicationSectionService.getLanguages(application.getId())).isEmpty();
        assertThat(adminApplicationSectionService.getAwards(application.getId())).isEmpty();
        assertThat(adminApplicationSectionService.getGapPeriods(application.getId())).isEmpty();
        assertThat(adminApplicationSectionService.getAttachments(application.getId())).isEmpty();
        assertThat(adminApplicationSectionService.getAnswers(application.getId())).isEmpty();
        assertThat(adminApplicationSectionService.getMilitary(application.getId())).isNull();
        assertThat(adminApplicationSectionService.getCareers(application.getId()).careerType()).isEqualTo(CareerType.NOT_SELECTED);
        assertThat(adminApplicationSectionService.getCareers(application.getId()).careers()).isEmpty();
    }

    @Test
    void get_careers_returns_profile_and_sorted_careers() {
        JobApplication application = createApplication("admin-section-career");
        careerProfileRepository.save(ApplicationCareerProfile.create(application, CareerType.EXPERIENCED));
        ApplicationCareer second = careerRepository.save(career(application, "Second Company", 1));
        ApplicationCareer first = careerRepository.save(career(application, "First Company", 0));

        AdminCareerResponse response = adminApplicationSectionService.getCareers(application.getId());

        assertThat(response.careerType()).isEqualTo(CareerType.EXPERIENCED);
        assertThat(response.careers()).extracting(career -> career.careerId())
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void get_careers_allows_newcomer_without_career_rows() {
        JobApplication application = createApplication("admin-section-newcomer");
        careerProfileRepository.save(ApplicationCareerProfile.create(application, CareerType.NEWCOMER));

        AdminCareerResponse response = adminApplicationSectionService.getCareers(application.getId());

        assertThat(response.careerType()).isEqualTo(CareerType.NEWCOMER);
        assertThat(response.careers()).isEmpty();
    }

    @Test
    void get_certificates_masks_certificate_number() {
        JobApplication application = createApplication("admin-section-certificate");
        certificateRepository.save(ApplicationCertificate.create(
                application,
                "SQLD",
                "Kdata",
                LocalDate.of(2024, 5, 1),
                "ABCD-1234-SECRET",
                null,
                "PASS",
                0
        ));

        List<AdminCertificateResponse> responses = adminApplicationSectionService.getCertificates(application.getId());

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).certificateNumberMasked()).isEqualTo("ABC***");
    }

    @Test
    void get_languages_returns_sorted_languages() {
        JobApplication application = createApplication("admin-section-language");
        ApplicationLanguage second = languageRepository.save(language(application, "English", 1));
        ApplicationLanguage first = languageRepository.save(language(application, "Japanese", 0));

        List<AdminLanguageResponse> responses = adminApplicationSectionService.getLanguages(application.getId());

        assertThat(responses).extracting(AdminLanguageResponse::languageId)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void get_military_masks_exemption_reason() {
        JobApplication application = createApplication("admin-section-military");
        militaryRepository.save(ApplicationMilitary.create(
                application,
                MilitarySubjectType.EXEMPTED,
                null,
                null,
                null,
                null,
                null,
                "personal medical reason"
        ));

        AdminMilitaryResponse response = adminApplicationSectionService.getMilitary(application.getId());

        assertThat(response.exemptionReasonMasked()).isEqualTo("***");
    }

    @Test
    void get_awards_gap_periods_and_attachments_return_sorted_metadata() {
        JobApplication application = createApplication("admin-section-misc");
        ApplicationAward secondAward = awardRepository.save(award(application, "Second Award", 1));
        ApplicationAward firstAward = awardRepository.save(award(application, "First Award", 0));
        ApplicationGapPeriod secondGap = gapPeriodRepository.save(gapPeriod(application, GapType.OTHER, 1));
        ApplicationGapPeriod firstGap = gapPeriodRepository.save(gapPeriod(application, GapType.CAREER, 0));
        ApplicationAttachment secondAttachment = attachmentRepository.save(attachment(application, AttachmentType.PORTFOLIO, 1));
        ApplicationAttachment firstAttachment = attachmentRepository.save(attachment(application, AttachmentType.RESUME, 0));

        List<AdminAwardResponse> awards = adminApplicationSectionService.getAwards(application.getId());
        List<AdminGapPeriodResponse> gapPeriods = adminApplicationSectionService.getGapPeriods(application.getId());
        List<AdminAttachmentResponse> attachments = adminApplicationSectionService.getAttachments(application.getId());

        assertThat(awards).extracting(AdminAwardResponse::awardId)
                .containsExactly(firstAward.getId(), secondAward.getId());
        assertThat(gapPeriods).extracting(AdminGapPeriodResponse::gapPeriodId)
                .containsExactly(firstGap.getId(), secondGap.getId());
        assertThat(attachments).extracting(AdminAttachmentResponse::attachmentId)
                .containsExactly(firstAttachment.getId(), secondAttachment.getId());
        assertThat(attachments.get(0).originalFileName()).isEqualTo("file-0.pdf");
    }

    @Test
    void get_answers_returns_active_questions_with_saved_answer_snapshot() {
        JobApplication application = createApplication("admin-section-answer");
        JobPostingQuestion question = question(application.getJobPosting(), "Original Question", 0, true);
        ApplicationAnswer answer = applicationAnswerRepository.save(ApplicationAnswer.create(
                application,
                question,
                "original answer"
        ));
        question.update(
                "Changed Question",
                "Changed helper",
                QuestionCategory.GENERAL,
                QuestionAnswerType.SHORT_TEXT,
                false,
                null,
                500,
                9
        );

        List<AdminApplicationAnswerResponse> responses = adminApplicationSectionService.getAnswers(application.getId());

        assertThat(responses).hasSize(1);
        AdminApplicationAnswerResponse response = responses.get(0);
        assertThat(response.questionId()).isEqualTo(question.getId());
        assertThat(response.questionText()).isEqualTo("Original Question");
        assertThat(response.category()).isEqualTo(QuestionCategory.JOB_SPECIFIC);
        assertThat(response.answerType()).isEqualTo(QuestionAnswerType.LONG_TEXT);
        assertThat(response.required()).isTrue();
        assertThat(response.maxLength()).isEqualTo(3000);
        assertThat(response.sortOrder()).isEqualTo(0);
        assertThat(response.answerId()).isEqualTo(answer.getId());
        assertThat(response.answerText()).isEqualTo("original answer");
        assertThat(response.answerUpdatedAt()).isNotNull();
    }

    @Test
    void get_answers_returns_unanswered_active_questions_with_null_answer_fields() {
        JobApplication application = createApplication("admin-section-answer-empty");
        JobPostingQuestion second = question(application.getJobPosting(), "Second Question", 1, false);
        JobPostingQuestion first = question(application.getJobPosting(), "First Question", 0, true);

        List<AdminApplicationAnswerResponse> responses = adminApplicationSectionService.getAnswers(application.getId());

        assertThat(responses).extracting(AdminApplicationAnswerResponse::questionId)
                .containsExactly(first.getId(), second.getId());
        assertThat(responses.get(0).answerId()).isNull();
        assertThat(responses.get(0).answerText()).isNull();
        assertThat(responses.get(0).answerUpdatedAt()).isNull();
    }

    @Test
    void get_answers_excludes_inactive_and_foreign_question_answers() {
        JobApplication application = createApplication("admin-section-answer-filter");
        JobPostingQuestion active = question(application.getJobPosting(), "Active Question", 0, false);
        JobPostingQuestion inactive = question(application.getJobPosting(), "Inactive Question", 1, true);
        inactive.deactivate();
        applicationAnswerRepository.save(ApplicationAnswer.create(application, inactive, "inactive answer"));

        JobApplication otherApplication = createApplication("admin-section-answer-other");
        JobPostingQuestion foreign = question(otherApplication.getJobPosting(), "Foreign Question", 0, true);
        applicationAnswerRepository.save(ApplicationAnswer.create(application, foreign, "foreign answer"));

        List<AdminApplicationAnswerResponse> responses = adminApplicationSectionService.getAnswers(application.getId());

        assertThat(responses).extracting(AdminApplicationAnswerResponse::questionId)
                .containsExactly(active.getId());
        assertThat(responses.get(0).answerText()).isNull();
    }

    @Test
    void get_stage_results_returns_stage_timeline_with_result_merge() {
        JobApplication application = createApplication("admin-section-stage-result");
        Stage later = stageRepository.save(stage(application.getJobPosting(), "Final Interview", 2, true));
        Stage earlier = stageRepository.save(stage(application.getJobPosting(), "Document Screening", 1, false));
        StageResult result = stageResultRepository.save(StageResult.initialize(later, application));
        result.updateResult(
                StageResultStatus.PASSED,
                new BigDecimal("95.5"),
                "passed",
                LocalDateTime.of(2026, 7, 1, 10, 30),
                "SYSTEM"
        );

        List<AdminApplicationStageResultResponse> responses = adminApplicationSectionService.getStageResults(application.getId());

        assertThat(responses).extracting(AdminApplicationStageResultResponse::stageId)
                .containsExactly(earlier.getId(), later.getId());
        assertThat(responses.get(0).stageResultId()).isNull();
        assertThat(responses.get(0).resultStatus()).isNull();
        assertThat(responses.get(0).score()).isNull();
        assertThat(responses.get(0).comment()).isNull();
        assertThat(responses.get(0).decidedAt()).isNull();
        assertThat(responses.get(1).stageResultId()).isEqualTo(result.getId());
        assertThat(responses.get(1).resultStatus()).isEqualTo(StageResultStatus.PASSED);
        assertThat(responses.get(1).score()).isEqualByComparingTo("95.5");
        assertThat(responses.get(1).comment()).isEqualTo("passed");
        assertThat(responses.get(1).decidedAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 10, 30));
    }

    @Test
    void get_stage_results_excludes_other_job_posting_results() {
        JobApplication application = createApplication("admin-section-stage-result-own");
        Stage ownStage = stageRepository.save(stage(application.getJobPosting(), "Own Stage", 1, true));
        JobApplication otherApplication = createApplication("admin-section-stage-result-other");
        Stage otherStage = stageRepository.save(stage(otherApplication.getJobPosting(), "Other Stage", 1, true));
        stageResultRepository.save(StageResult.initialize(otherStage, otherApplication));

        List<AdminApplicationStageResultResponse> responses = adminApplicationSectionService.getStageResults(application.getId());

        assertThat(responses).extracting(AdminApplicationStageResultResponse::stageId)
                .containsExactly(ownStage.getId());
        assertThat(responses.get(0).stageResultId()).isNull();
    }

    @Test
    void get_stage_results_does_not_expose_decided_by() {
        assertThat(AdminApplicationStageResultResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("decidedBy");
    }

    @Test
    void get_sections_fails_when_application_does_not_exist() {
        assertThatThrownBy(() -> adminApplicationSectionService.getEducations(99999L))
                .isInstanceOf(JobApplicationNotFoundException.class);
        assertThatThrownBy(() -> adminApplicationSectionService.getAttachments(99999L))
                .isInstanceOf(JobApplicationNotFoundException.class);
        assertThatThrownBy(() -> adminApplicationSectionService.getAnswers(99999L))
                .isInstanceOf(JobApplicationNotFoundException.class);
        assertThatThrownBy(() -> adminApplicationSectionService.getStageResults(99999L))
                .isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void get_sections_are_allowed_for_draft_submitted_and_withdrawn_applications() {
        JobApplication draft = createApplication("admin-section-draft");
        JobApplication submitted = createApplication("admin-section-submitted");
        JobApplication withdrawn = createApplication("admin-section-withdrawn");
        JobPostingQuestion draftQuestion = question(draft.getJobPosting(), "Draft Question", 0, false);
        JobPostingQuestion submittedQuestion = question(submitted.getJobPosting(), "Submitted Question", 0, false);
        JobPostingQuestion withdrawnQuestion = question(withdrawn.getJobPosting(), "Withdrawn Question", 0, false);
        Stage draftStage = stageRepository.save(stage(draft.getJobPosting(), "Draft Stage", 1, true));
        Stage submittedStage = stageRepository.save(stage(submitted.getJobPosting(), "Submitted Stage", 1, true));
        Stage withdrawnStage = stageRepository.save(stage(withdrawn.getJobPosting(), "Withdrawn Stage", 1, true));
        jobApplicationService.submit(submitted.getApplicant().getId(), submitted.getId());
        jobApplicationService.submit(withdrawn.getApplicant().getId(), withdrawn.getId());
        jobApplicationService.withdraw(withdrawn.getApplicant().getId(), withdrawn.getId());

        assertThat(jobApplicationRepository.findById(draft.getId()).orElseThrow().getStatus()).isEqualTo(JobApplicationStatus.DRAFT);
        assertThat(adminApplicationSectionService.getAttachments(draft.getId())).isEmpty();
        assertThat(adminApplicationSectionService.getAttachments(submitted.getId())).isEmpty();
        assertThat(adminApplicationSectionService.getAttachments(withdrawn.getId())).isEmpty();
        assertThat(adminApplicationSectionService.getAnswers(draft.getId()))
                .extracting(AdminApplicationAnswerResponse::questionId)
                .containsExactly(draftQuestion.getId());
        assertThat(adminApplicationSectionService.getAnswers(submitted.getId()))
                .extracting(AdminApplicationAnswerResponse::questionId)
                .containsExactly(submittedQuestion.getId());
        assertThat(adminApplicationSectionService.getAnswers(withdrawn.getId()))
                .extracting(AdminApplicationAnswerResponse::questionId)
                .containsExactly(withdrawnQuestion.getId());
        assertThat(adminApplicationSectionService.getStageResults(draft.getId()))
                .extracting(AdminApplicationStageResultResponse::stageId)
                .containsExactly(draftStage.getId());
        assertThat(adminApplicationSectionService.getStageResults(submitted.getId()))
                .extracting(AdminApplicationStageResultResponse::stageId)
                .containsExactly(submittedStage.getId());
        assertThat(adminApplicationSectionService.getStageResults(withdrawn.getId()))
                .extracting(AdminApplicationStageResultResponse::stageId)
                .containsExactly(withdrawnStage.getId());
    }

    private JobApplication createApplication(String loginId) {
        Applicant applicant = createApplicant(loginId, loginId);
        Long jobPostingId = createPublishedJobPosting();
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );
        return jobApplicationRepository.findById(applicationId).orElseThrow();
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

    private Long createPublishedJobPosting() {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "Admin Section Posting",
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

    private Long firstJobPositionId(Long jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        return jobPosting.getJobPositions().stream()
                .sorted(Comparator.comparing(JobPosition::getSortOrder).thenComparing(JobPosition::getId))
                .map(JobPosition::getId)
                .findFirst()
                .orElseThrow();
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

    private ApplicationCareer career(JobApplication application, String companyName, Integer sortOrder) {
        return ApplicationCareer.create(
                application,
                companyName,
                "Platform",
                "Engineer",
                EmploymentType.FULL_TIME,
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2024, 12, 31),
                false,
                "Backend development",
                "Career move",
                sortOrder
        );
    }

    private ApplicationLanguage language(JobApplication application, String languageName, Integer sortOrder) {
        return ApplicationLanguage.create(
                application,
                languageName,
                "Test",
                "900",
                "A",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2027, 1, 1),
                "Issuer",
                sortOrder
        );
    }

    private ApplicationAward award(JobApplication application, String awardName, Integer sortOrder) {
        return ApplicationAward.create(
                application,
                awardName,
                "Organization",
                LocalDate.of(2025, 3, 1),
                "Description",
                sortOrder
        );
    }

    private ApplicationGapPeriod gapPeriod(JobApplication application, GapType gapType, Integer sortOrder) {
        return ApplicationGapPeriod.create(
                application,
                LocalDate.of(2022, 1, 1).plusMonths(sortOrder),
                LocalDate.of(2022, 2, 1).plusMonths(sortOrder),
                gapType,
                "Reason",
                "Description",
                sortOrder
        );
    }

    private ApplicationAttachment attachment(JobApplication application, AttachmentType attachmentType, Integer sortOrder) {
        return ApplicationAttachment.create(
                application,
                attachmentType,
                ApplicationSectionType.APPLICATION,
                null,
                "file-%d.pdf".formatted(sortOrder),
                "stored-%d.pdf".formatted(sortOrder),
                "/internal/applications/%d/stored-%d.pdf".formatted(application.getId(), sortOrder),
                "application/pdf",
                1024L,
                sortOrder
        );
    }

    private JobPostingQuestion question(
            JobPosting jobPosting,
            String questionText,
            Integer sortOrder,
            Boolean required
    ) {
        return jobPostingQuestionRepository.save(JobPostingQuestion.createDirect(
                jobPosting,
                questionText,
                "Helper",
                QuestionCategory.JOB_SPECIFIC,
                QuestionAnswerType.LONG_TEXT,
                required,
                null,
                3000,
                sortOrder
        ));
    }

    private Stage stage(JobPosting jobPosting, String stageName, Integer stageOrder, boolean finalStage) {
        return Stage.create(
                jobPosting,
                stageName,
                StageType.DOCUMENT,
                stageOrder,
                LocalDateTime.of(2026, 7, 1, 10, 0).plusDays(stageOrder),
                finalStage
        );
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
