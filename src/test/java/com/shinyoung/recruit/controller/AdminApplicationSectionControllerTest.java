package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
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
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
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
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.enumeration.CampusType;
import com.shinyoung.recruit.enumeration.CareerType;
import com.shinyoung.recruit.enumeration.DayNightType;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.EmploymentType;
import com.shinyoung.recruit.enumeration.GapType;
import com.shinyoung.recruit.enumeration.GraduationStatus;
import com.shinyoung.recruit.enumeration.MilitaryBranch;
import com.shinyoung.recruit.enumeration.MilitaryRank;
import com.shinyoung.recruit.enumeration.MilitaryServiceType;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void get_admin_section_apis_return_success_responses() throws Exception {
        JobApplication application = createApplication("admin-section-api-success");
        seedAllSections(application);

        mockMvc.perform(get("/admin/applications/{applicationId}/educations", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].schoolName").value("Shinyoung University"))
                .andExpect(jsonPath("$.data[0].semesterGrades[0].schoolYear").value(1));

        mockMvc.perform(get("/admin/applications/{applicationId}/careers", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.careerType").value("EXPERIENCED"))
                .andExpect(jsonPath("$.data.careers[0].companyName").value("Shinyoung Securities"));

        mockMvc.perform(get("/admin/applications/{applicationId}/certificates", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].certificateName").value("SQLD"))
                .andExpect(jsonPath("$.data[0].certificateNumberMasked").value("ABC***"))
                .andExpect(jsonPath("$.data[0].certificateNumber").doesNotExist());

        mockMvc.perform(get("/admin/applications/{applicationId}/languages", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].languageName").value("English"));

        mockMvc.perform(get("/admin/applications/{applicationId}/military", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.militarySubjectType").value("EXEMPTED"))
                .andExpect(jsonPath("$.data.exemptionReasonMasked").value("***"))
                .andExpect(jsonPath("$.data.exemptionReason").doesNotExist());

        mockMvc.perform(get("/admin/applications/{applicationId}/awards", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].awardName").value("Best Project"));

        mockMvc.perform(get("/admin/applications/{applicationId}/gap-periods", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].gapType").value("OTHER"));

        mockMvc.perform(get("/admin/applications/{applicationId}/attachments", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].originalFileName").value("resume.pdf"))
                .andExpect(jsonPath("$.data[0].storedFileName").doesNotExist())
                .andExpect(jsonPath("$.data[0].storagePath").doesNotExist());
    }

    @Test
    void empty_admin_sections_return_expected_shapes() throws Exception {
        JobApplication application = createApplication("admin-section-api-empty");

        mockMvc.perform(get("/admin/applications/{applicationId}/educations", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/admin/applications/{applicationId}/careers", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.careerType").value("NOT_SELECTED"))
                .andExpect(jsonPath("$.data.careers").isArray())
                .andExpect(jsonPath("$.data.careers").isEmpty());

        mockMvc.perform(get("/admin/applications/{applicationId}/military", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/admin/applications/{applicationId}/attachments", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void missing_application_returns_not_found_api_response() throws Exception {
        mockMvc.perform(get("/admin/applications/{applicationId}/educations", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void admin_section_write_methods_are_not_supported() throws Exception {
        String[] paths = {
                "/admin/applications/1/educations",
                "/admin/applications/1/careers",
                "/admin/applications/1/certificates",
                "/admin/applications/1/languages",
                "/admin/applications/1/military",
                "/admin/applications/1/awards",
                "/admin/applications/1/gap-periods",
                "/admin/applications/1/attachments"
        };

        for (String path : paths) {
            assertMethodNotAllowed(put(path).contentType(MediaType.APPLICATION_JSON).content("{}"));
            assertMethodNotAllowed(delete(path));
            assertMethodNotAllowed(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"));
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
                "Bachelor",
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
        careerProfileRepository.save(ApplicationCareerProfile.create(application, CareerType.EXPERIENCED));
        careerRepository.save(ApplicationCareer.create(
                application,
                "Shinyoung Securities",
                "Platform",
                "Engineer",
                EmploymentType.FULL_TIME,
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2024, 12, 31),
                false,
                "Backend development",
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

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return FIXED_CLOCK;
        }
    }
}
