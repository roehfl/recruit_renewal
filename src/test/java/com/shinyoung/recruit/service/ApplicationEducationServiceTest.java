package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationSemesterGradeRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.support.BasicInfoTestSupport;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.EducationReplaceRequest;
import com.shinyoung.recruit.dto.request.EducationRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.SemesterGradeRequest;
import com.shinyoung.recruit.dto.response.EducationResponse;
import com.shinyoung.recruit.enumeration.CampusType;
import com.shinyoung.recruit.enumeration.DayNightType;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.GraduationStatus;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationEducationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private ApplicationEducationService applicationEducationService;

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
    private ApplicationBasicInfoRepository basicInfoRepository;

    @Autowired
    private ApplicationEducationRepository educationRepository;

    @Autowired
    private ApplicationEducationSemesterGradeRepository semesterGradeRepository;

    @Test
    void replace_educations_success_and_get_success() {
        Applicant applicant = createApplicant("education-success", "Education Success");
        Long jobPostingId = createPublishedJobPosting(true);
        Long applicationId = createApplication(applicant, jobPostingId);

        List<EducationResponse> responses = applicationEducationService.replaceEducations(
                applicant.getId(),
                applicationId,
                new EducationReplaceRequest(List.of(
                        universityEducation(1),
                        highSchoolEducation(0)
                ))
        );
        List<EducationResponse> getResponses = applicationEducationService.getEducations(applicant.getId(), applicationId);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(EducationResponse::sortOrder).containsExactly(0, 1);
        assertThat(responses.get(0).educationLevel()).isEqualTo(EducationLevel.HIGH_SCHOOL);
        assertThat(responses.get(1).semesterGrades()).hasSize(2);
        assertThat(getResponses).extracting(EducationResponse::educationId)
                .containsExactlyElementsOf(responses.stream().map(EducationResponse::educationId).toList());
    }

    @Test
    void education_persists_optional_school_id_when_selected_and_null_when_freetext() {
        Applicant applicant = createApplicant("education-schoolid", "Education SchoolId");
        Long jobPostingId = createPublishedJobPosting(true);
        Long applicationId = createApplication(applicant, jobPostingId);

        EducationRequest linkedToMaster = new EducationRequest(
                EducationLevel.UNIVERSITY, "Linked University", null, null,
                LocalDate.of(2020, 3, 1), LocalDate.of(2024, 2, 1), GraduationStatus.GRADUATED,
                DayNightType.DAY, CampusType.MAIN, false, "KR", 1, List.of(), 777L);
        EducationRequest freeText = new EducationRequest(
                EducationLevel.HIGH_SCHOOL, "Free Text High", null, null,
                null, null, GraduationStatus.GRADUATED, null, null, false, "KR", 0, List.of());

        List<EducationResponse> responses = applicationEducationService.replaceEducations(
                applicant.getId(), applicationId,
                new EducationReplaceRequest(List.of(linkedToMaster, freeText)));

        EducationResponse linked = responses.stream().filter(r -> r.sortOrder() == 1).findFirst().orElseThrow();
        EducationResponse free = responses.stream().filter(r -> r.sortOrder() == 0).findFirst().orElseThrow();
        assertThat(linked.schoolId()).isEqualTo(777L);
        assertThat(free.schoolId()).isNull();
    }

    @Test
    void semester_grades_are_sorted_by_school_year_semester_and_id() {
        Applicant applicant = createApplicant("education-grade-sort", "Education Grade Sort");
        Long jobPostingId = createPublishedJobPosting(true);
        Long applicationId = createApplication(applicant, jobPostingId);

        List<EducationResponse> responses = applicationEducationService.replaceEducations(
                applicant.getId(),
                applicationId,
                new EducationReplaceRequest(List.of(new EducationRequest(
                        EducationLevel.UNIVERSITY,
                        "Sort University",
                        "Computer Science",
                        null,
                        LocalDate.of(2022, 3, 1),
                        null,
                        GraduationStatus.ENROLLED,
                        DayNightType.DAY,
                        CampusType.MAIN,
                        false,
                        "KR",
                        0,
                        List.of(
                                grade(2, 1),
                                grade(1, 2),
                                grade(1, 1)
                        )
                )))
        );

        assertThat(responses.get(0).semesterGrades())
                .extracting(grade -> "%d-%d".formatted(grade.schoolYear(), grade.semester()))
                .containsExactly("1-1", "1-2", "2-1");
    }

    @Test
    void replace_deletes_existing_educations_and_semester_grades() {
        Applicant applicant = createApplicant("education-replace", "Education Replace");
        Long jobPostingId = createPublishedJobPosting(true);
        Long applicationId = createApplication(applicant, jobPostingId);
        applicationEducationService.replaceEducations(
                applicant.getId(),
                applicationId,
                new EducationReplaceRequest(List.of(universityEducation(0)))
        );
        List<Long> oldEducationIds = educationRepository.findByJobApplicationId(applicationId).stream()
                .map(ApplicationEducation::getId)
                .toList();

        List<EducationResponse> responses = applicationEducationService.replaceEducations(
                applicant.getId(),
                applicationId,
                new EducationReplaceRequest(List.of(highSchoolEducation(0)))
        );

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).educationLevel()).isEqualTo(EducationLevel.HIGH_SCHOOL);
        assertThat(educationRepository.findAll()).extracting(ApplicationEducation::getId)
                .doesNotContainAnyElementsOf(oldEducationIds);
        assertThat(semesterGradeRepository.findByEducationIdInOrderBySchoolYearAscSemesterAscIdAsc(oldEducationIds))
                .isEmpty();
    }

    @Test
    void empty_education_list_is_allowed_and_deletes_existing_data() {
        Applicant applicant = createApplicant("education-empty", "Education Empty");
        Long jobPostingId = createPublishedJobPosting(true);
        Long applicationId = createApplication(applicant, jobPostingId);
        applicationEducationService.replaceEducations(
                applicant.getId(),
                applicationId,
                new EducationReplaceRequest(List.of(universityEducation(0)))
        );

        List<EducationResponse> responses = applicationEducationService.replaceEducations(
                applicant.getId(),
                applicationId,
                new EducationReplaceRequest(List.of())
        );

        assertThat(responses).isEmpty();
        assertThat(educationRepository.findByJobApplicationId(applicationId)).isEmpty();
    }

    @Test
    void replace_fails_when_education_section_is_disabled() {
        Applicant applicant = createApplicant("education-disabled", "Education Disabled");
        Long jobPostingId = createPublishedJobPosting(false);
        Long applicationId = createApplication(applicant, jobPostingId);

        assertThatThrownBy(() -> applicationEducationService.replaceEducations(
                applicant.getId(),
                applicationId,
                new EducationReplaceRequest(List.of(universityEducation(0)))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void replace_fails_when_application_is_not_draft() {
        Applicant submittedApplicant = createApplicant("education-submitted", "Education Submitted");
        Long submittedPostingId = createPublishedJobPosting(false);
        Long submittedApplicationId = createApplication(submittedApplicant, submittedPostingId);
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(submittedApplicationId).orElseThrow());
        jobApplicationService.submit(submittedApplicant.getId(), submittedApplicationId);

        assertThatThrownBy(() -> applicationEducationService.replaceEducations(
                submittedApplicant.getId(),
                submittedApplicationId,
                new EducationReplaceRequest(List.of(universityEducation(0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant withdrawnApplicant = createApplicant("education-withdrawn", "Education Withdrawn");
        Long withdrawnPostingId = createPublishedJobPosting(false);
        Long withdrawnApplicationId = createApplication(withdrawnApplicant, withdrawnPostingId);
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(withdrawnApplicationId).orElseThrow());
        jobApplicationService.submit(withdrawnApplicant.getId(), withdrawnApplicationId);
        jobApplicationService.withdraw(withdrawnApplicant.getId(), withdrawnApplicationId);

        assertThatThrownBy(() -> applicationEducationService.replaceEducations(
                withdrawnApplicant.getId(),
                withdrawnApplicationId,
                new EducationReplaceRequest(List.of(universityEducation(0)))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void other_applicants_application_is_hidden_for_get_and_replace() {
        Applicant owner = createApplicant("education-owner", "Education Owner");
        Applicant other = createApplicant("education-other", "Education Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting(true));

        assertThatThrownBy(() -> applicationEducationService.getEducations(other.getId(), applicationId))
                .isInstanceOf(JobApplicationNotFoundException.class);
        assertThatThrownBy(() -> applicationEducationService.replaceEducations(
                other.getId(),
                applicationId,
                new EducationReplaceRequest(List.of(universityEducation(0)))
        )).isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void replace_fails_outside_reception_period_or_unpublished_posting() {
        Applicant beforeApplicant = createApplicant("education-before", "Education Before");
        Long beforePostingId = createPublishedJobPosting(true);
        Long beforeApplicationId = createApplication(beforeApplicant, beforePostingId);
        setReceptionPeriod(beforePostingId, LocalDateTime.of(2026, 6, 16, 9, 0), LocalDateTime.of(2026, 6, 30, 18, 0));

        assertThatThrownBy(() -> applicationEducationService.replaceEducations(
                beforeApplicant.getId(),
                beforeApplicationId,
                new EducationReplaceRequest(List.of(universityEducation(0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant afterApplicant = createApplicant("education-after", "Education After");
        Long afterPostingId = createPublishedJobPosting(true);
        Long afterApplicationId = createApplication(afterApplicant, afterPostingId);
        setReceptionPeriod(afterPostingId, LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 14, 18, 0));

        assertThatThrownBy(() -> applicationEducationService.replaceEducations(
                afterApplicant.getId(),
                afterApplicationId,
                new EducationReplaceRequest(List.of(universityEducation(0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant draftApplicant = createApplicant("education-draft", "Education Draft");
        Long draftPostingId = createPublishedJobPosting(true);
        Long draftApplicationId = createApplication(draftApplicant, draftPostingId);
        setJobPostingStatus(draftPostingId, JobPostingStatus.DRAFT);

        assertThatThrownBy(() -> applicationEducationService.replaceEducations(
                draftApplicant.getId(),
                draftApplicationId,
                new EducationReplaceRequest(List.of(universityEducation(0)))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void replace_fails_when_required_or_cross_field_validation_fails() {
        Applicant applicant = createApplicant("education-validation", "Education Validation");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        assertThatThrownBy(() -> applicationEducationService.replaceEducations(
                applicant.getId(),
                applicationId,
                new EducationReplaceRequest(List.of(new EducationRequest(
                        null,
                        "University",
                        null,
                        null,
                        null,
                        null,
                        GraduationStatus.GRADUATED,
                        null,
                        null,
                        null,
                        null,
                        0,
                        List.of()
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationEducationService.replaceEducations(
                applicant.getId(),
                applicationId,
                new EducationReplaceRequest(List.of(new EducationRequest(
                        EducationLevel.UNIVERSITY,
                        "University",
                        null,
                        null,
                        LocalDate.of(2025, 3, 1),
                        LocalDate.of(2021, 2, 28),
                        GraduationStatus.GRADUATED,
                        null,
                        null,
                        null,
                        null,
                        0,
                        List.of()
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationEducationService.replaceEducations(
                applicant.getId(),
                applicationId,
                new EducationReplaceRequest(List.of(new EducationRequest(
                        EducationLevel.UNIVERSITY,
                        "University",
                        null,
                        null,
                        null,
                        null,
                        GraduationStatus.GRADUATED,
                        null,
                        null,
                        null,
                        null,
                        0,
                        List.of(new SemesterGradeRequest(1, 1, null, new BigDecimal("4.6"), new BigDecimal("4.5"), null, null))
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationEducationService.replaceEducations(
                applicant.getId(),
                applicationId,
                new EducationReplaceRequest(List.of(new EducationRequest(
                        EducationLevel.UNIVERSITY,
                        "University",
                        null,
                        null,
                        null,
                        null,
                        GraduationStatus.GRADUATED,
                        null,
                        null,
                        null,
                        null,
                        0,
                        List.of(new SemesterGradeRequest(1, 1, null, new BigDecimal("4.0"), new BigDecimal("4.5"), new BigDecimal("4.0"), null))
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void replace_fails_when_high_school_has_grade_or_sort_order_is_duplicated() {
        Applicant applicant = createApplicant("education-high-school", "Education High School");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        assertThatThrownBy(() -> applicationEducationService.replaceEducations(
                applicant.getId(),
                applicationId,
                new EducationReplaceRequest(List.of(new EducationRequest(
                        EducationLevel.HIGH_SCHOOL,
                        "High School",
                        null,
                        null,
                        null,
                        null,
                        GraduationStatus.GRADUATED,
                        null,
                        null,
                        false,
                        "KR",
                        0,
                        List.of(grade(1, 1))
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationEducationService.replaceEducations(
                applicant.getId(),
                applicationId,
                new EducationReplaceRequest(List.of(universityEducation(0), highSchoolEducation(0)))
        )).isInstanceOf(InvalidJobApplicationException.class);
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

    private Long createApplication(Applicant applicant, Long jobPostingId) {
        return jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );
    }

    private Long createPublishedJobPosting(boolean useEducation) {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(
                        new JobPositionRequest("Backend", 0),
                        new JobPositionRequest("Frontend", 1)
                ),
                new ApplicationFormConfigRequest(useEducation, false, false, false, false, false, false)
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

    private EducationRequest highSchoolEducation(Integer sortOrder) {
        return new EducationRequest(
                EducationLevel.HIGH_SCHOOL,
                "Shinyoung High School",
                null,
                null,
                LocalDate.of(2018, 3, 1),
                LocalDate.of(2021, 2, 28),
                GraduationStatus.GRADUATED,
                DayNightType.DAY,
                CampusType.MAIN,
                false,
                "KR",
                sortOrder,
                List.of()
        );
    }

    private EducationRequest universityEducation(Integer sortOrder) {
        return new EducationRequest(
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
                sortOrder,
                List.of(grade(2, 1), grade(1, 1))
        );
    }

    private SemesterGradeRequest grade(Integer schoolYear, Integer semester) {
        return new SemesterGradeRequest(
                schoolYear,
                semester,
                new BigDecimal("18.0"),
                new BigDecimal("4.0"),
                new BigDecimal("4.5"),
                new BigDecimal("3.8"),
                new BigDecimal("4.5")
        );
    }

    private void setReceptionPeriod(Long jobPostingId, LocalDateTime start, LocalDateTime end) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId).orElseThrow();
        jobPosting.updateBasicInfo(jobPosting.getTitle(), jobPosting.getContentHtml(), start, end);
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
