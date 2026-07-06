package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.domain.entity.ApplicationAnswer;
import com.shinyoung.recruit.domain.entity.ApplicationAward;
import com.shinyoung.recruit.domain.entity.ApplicationCareer;
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
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.enumeration.CampusType;
import com.shinyoung.recruit.enumeration.DayNightType;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.EmploymentType;
import com.shinyoung.recruit.enumeration.GapType;
import com.shinyoung.recruit.enumeration.GraduationStatus;
import com.shinyoung.recruit.enumeration.MilitaryBranch;
import com.shinyoung.recruit.enumeration.MilitaryRank;
import com.shinyoung.recruit.enumeration.MilitaryServiceType;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.service.JobApplicationService;
import com.shinyoung.recruit.service.JobPostingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class AdminApplicationSectionControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private WebApplicationContext context;

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void get_admin_section_apis_return_success_responses() throws Exception {
        JobApplication application = createApplication("admin-section-api-success");
        seedAllSections(application);

        mockMvc.perform(get("/api/admin/applications/{applicationId}/educations", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].schoolName").value("Shinyoung University"))
                .andExpect(jsonPath("$.data[0].semesterGrades[0].schoolYear").value(1));

        mockMvc.perform(get("/api/admin/applications/{applicationId}/careers", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.careers[0].companyName").value("Shinyoung Securities"));

        mockMvc.perform(get("/api/admin/applications/{applicationId}/certificates", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].certificateName").value("SQLD"))
                .andExpect(jsonPath("$.data[0].certificateNumberMasked").value("ABC***"))
                .andExpect(jsonPath("$.data[0].certificateNumber").doesNotExist());

        mockMvc.perform(get("/api/admin/applications/{applicationId}/languages", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].languageName").value("English"));

        mockMvc.perform(get("/api/admin/applications/{applicationId}/military", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.militarySubjectType").value("EXEMPTED"))
                .andExpect(jsonPath("$.data.nonServiceReasonMasked").value("***"))
                .andExpect(jsonPath("$.data.nonServiceReason").doesNotExist());

        mockMvc.perform(get("/api/admin/applications/{applicationId}/awards", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].awardName").value("Best Project"));

        mockMvc.perform(get("/api/admin/applications/{applicationId}/gap-periods", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].gapType").value("OTHER"));

        mockMvc.perform(get("/api/admin/applications/{applicationId}/attachments", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].originalFileName").value("resume.pdf"))
                .andExpect(jsonPath("$.data[0].storedFileName").doesNotExist())
                .andExpect(jsonPath("$.data[0].storagePath").doesNotExist());

        mockMvc.perform(get("/api/admin/applications/{applicationId}/answers", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].questionText").value("Self introduction"))
                .andExpect(jsonPath("$.data[0].answerText").value("answer text"));
    }

    @Test
    void empty_admin_sections_return_expected_shapes() throws Exception {
        JobApplication application = createApplication("admin-section-api-empty");

        mockMvc.perform(get("/api/admin/applications/{applicationId}/educations", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/admin/applications/{applicationId}/careers", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.careers").isArray())
                .andExpect(jsonPath("$.data.careers").isEmpty());

        mockMvc.perform(get("/api/admin/applications/{applicationId}/military", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/api/admin/applications/{applicationId}/attachments", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/admin/applications/{applicationId}/answers", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void admin_answers_return_unanswered_rows_and_not_found_response() throws Exception {
        JobApplication application = createApplication("admin-section-api-answer");
        JobPostingQuestion question = question(application.getJobPosting(), "Unanswered Question", 0, true);

        mockMvc.perform(get("/api/admin/applications/{applicationId}/answers", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].questionId").value(question.getId()))
                .andExpect(jsonPath("$.data[0].answerId").doesNotExist())
                .andExpect(jsonPath("$.data[0].answerText").doesNotExist());

        mockMvc.perform(get("/api/admin/applications/{applicationId}/answers", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void admin_stage_results_return_stage_rows_with_nullable_result_fields() throws Exception {
        JobApplication application = createApplication("admin-section-api-stage-result");
        Stage first = stageRepository.save(stage(application.getJobPosting(), "Document Screening", 1, false));
        Stage second = stageRepository.save(stage(application.getJobPosting(), "Final Interview", 2, true));
        StageResult result = stageResultRepository.save(StageResult.initialize(second, application));
        result.updateResult(
                StageResultStatus.PASSED,
                new BigDecimal("88.5"),
                "passed",
                LocalDateTime.of(2026, 7, 2, 11, 0),
                "SYSTEM"
        );

        mockMvc.perform(get("/api/admin/applications/{applicationId}/stage-results", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].stageId").value(first.getId()))
                .andExpect(jsonPath("$.data[0].stageName").value("Document Screening"))
                .andExpect(jsonPath("$.data[0].stageType").value("DOCUMENT"))
                .andExpect(jsonPath("$.data[0].stageOrder").value(1))
                .andExpect(jsonPath("$.data[0].stageStatus").value("READY"))
                .andExpect(jsonPath("$.data[0].finalStage").value(false))
                .andExpect(jsonPath("$.data[0].resultAnnouncementDateTime").exists())
                .andExpect(jsonPath("$.data[0].stageResultId").doesNotExist())
                .andExpect(jsonPath("$.data[0].resultStatus").doesNotExist())
                .andExpect(jsonPath("$.data[0].score").doesNotExist())
                .andExpect(jsonPath("$.data[0].comment").doesNotExist())
                .andExpect(jsonPath("$.data[0].decidedAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].decidedBy").doesNotExist())
                .andExpect(jsonPath("$.data[1].stageId").value(second.getId()))
                .andExpect(jsonPath("$.data[1].stageResultId").value(result.getId()))
                .andExpect(jsonPath("$.data[1].resultStatus").value("PASSED"))
                .andExpect(jsonPath("$.data[1].score").value(88.5))
                .andExpect(jsonPath("$.data[1].comment").value("passed"))
                .andExpect(jsonPath("$.data[1].decidedAt").exists())
                .andExpect(jsonPath("$.data[1].decidedBy").doesNotExist());
    }

    @Test
    void missing_application_returns_not_found_api_response() throws Exception {
        mockMvc.perform(get("/api/admin/applications/{applicationId}/educations", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(get("/api/admin/applications/{applicationId}/stage-results", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void admin_section_write_methods_are_not_supported() throws Exception {
        String[] paths = {
                "/api/admin/applications/1/educations",
                "/api/admin/applications/1/careers",
                "/api/admin/applications/1/certificates",
                "/api/admin/applications/1/languages",
                "/api/admin/applications/1/military",
                "/api/admin/applications/1/awards",
                "/api/admin/applications/1/gap-periods",
                "/api/admin/applications/1/attachments",
                "/api/admin/applications/1/answers",
                "/api/admin/applications/1/stage-results"
        };

        for (String path : paths) {
            assertMethodNotAllowed(put(path).contentType(MediaType.APPLICATION_JSON).content("{}"));
            assertMethodNotAllowed(delete(path));
            assertMethodNotAllowed(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"));
            assertMethodNotAllowed(patch(path).contentType(MediaType.APPLICATION_JSON).content("{}"));
        }
    }

    private void assertMethodNotAllowed(RequestBuilder requestBuilder) throws Exception {
        mockMvc.perform(requestBuilder)
                .andExpect(status().isMethodNotAllowed());
    }

    private void seedAllSections(JobApplication application) {
        ApplicationEducation education = educationRepository.save(ApplicationEducation.create(
                application,
                EducationLevel.UNIVERSITY,
                "Shinyoung University",
                "Computer Science",
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
                0
        ));
        semesterGradeRepository.save(ApplicationEducationSemesterGrade.create(
                education,
                1,
                1,
                new BigDecimal("18.0"),
                new BigDecimal("4.0"),
                new BigDecimal("4.5"),
                new BigDecimal("3.8"),
                new BigDecimal("4.5")
        ));
        careerRepository.save(ApplicationCareer.create(
                application,
                "Shinyoung Securities",
                "Platform",
                "Engineer",
                EmploymentType.FULL_TIME,
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2024, 12, 31),
                null,
                false,
                4500,
                "Career move",
                0
        ));
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
        languageRepository.save(ApplicationLanguage.create(
                application,
                "English",
                "TOEIC",
                "900",
                "A",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2027, 1, 1),
                "ETS",
                0
        ));
        militaryRepository.save(ApplicationMilitary.create(
                application,
                MilitarySubjectType.EXEMPTED,
                MilitaryServiceType.ACTIVE_DUTY,
                MilitaryBranch.ARMY,
                MilitaryRank.SERGEANT,
                null,
                null,
                "personal medical reason"
        ));
        awardRepository.save(ApplicationAward.create(
                application,
                "Best Project",
                "Shinyoung",
                LocalDate.of(2025, 3, 1),
                "Description",
                0
        ));
        gapPeriodRepository.save(ApplicationGapPeriod.create(
                application,
                LocalDate.of(2022, 1, 1),
                LocalDate.of(2022, 2, 1),
                GapType.OTHER,
                "Reason",
                "Description",
                0
        ));
        attachmentRepository.save(ApplicationAttachment.create(
                application,
                AttachmentType.RESUME,
                ApplicationSectionType.APPLICATION,
                null,
                "resume.pdf",
                "stored-resume.pdf",
                "/internal/applications/%d/stored-resume.pdf".formatted(application.getId()),
                "application/pdf",
                1024L,
                0
        ));
        JobPostingQuestion question = question(application.getJobPosting(), "Self introduction", 0, true);
        applicationAnswerRepository.save(ApplicationAnswer.create(application, question, "answer text"));
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
                "Admin Section Api Posting",
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

    private Long firstJobPositionId(Long jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        return jobPosting.getJobPositions().stream()
                .sorted(Comparator.comparing(JobPosition::getSortOrder).thenComparing(JobPosition::getId))
                .map(JobPosition::getId)
                .findFirst()
                .orElseThrow();
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
                QuestionCategory.SELF_INTRODUCTION,
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
