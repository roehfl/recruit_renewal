package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationCareer;
import com.shinyoung.recruit.domain.entity.ApplicationCareerProfile;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerProfileRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.support.BasicInfoTestSupport;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.CareerReplaceRequest;
import com.shinyoung.recruit.dto.request.CareerRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.response.CareerResponse;
import com.shinyoung.recruit.enumeration.CareerType;
import com.shinyoung.recruit.enumeration.EmploymentType;
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
class ApplicationCareerServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private ApplicationCareerService applicationCareerService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private ApplicationCareerProfileRepository careerProfileRepository;

    @Autowired
    private ApplicationCareerRepository careerRepository;

    @Autowired
    private ApplicationBasicInfoRepository basicInfoRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Test
    void save_newcomer_without_careers_success() {
        Applicant applicant = createApplicant("career-newcomer", "Career Newcomer");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        CareerResponse response = applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(CareerType.NEWCOMER, List.of())
        );

        assertThat(response.careerType()).isEqualTo(CareerType.NEWCOMER);
        assertThat(response.careers()).isEmpty();
        assertThat(careerProfileRepository.findByJobApplicationId(applicationId))
                .map(ApplicationCareerProfile::getCareerType)
                .contains(CareerType.NEWCOMER);
    }

    @Test
    void save_experienced_careers_success_and_get_success() {
        Applicant applicant = createApplicant("career-experienced", "Career Experienced");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        CareerResponse response = applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(CareerType.EXPERIENCED, List.of(
                        career("Second Company", 1),
                        career("First Company", 0)
                ))
        );
        CareerResponse getResponse = applicationCareerService.getCareers(applicant.getId(), applicationId);

        assertThat(response.careerType()).isEqualTo(CareerType.EXPERIENCED);
        assertThat(response.careers()).hasSize(2);
        assertThat(response.careers()).extracting(career -> career.companyName())
                .containsExactly("First Company", "Second Company");
        assertThat(getResponse.careers()).extracting(career -> career.careerId())
                .containsExactlyElementsOf(response.careers().stream().map(career -> career.careerId()).toList());
    }

    @Test
    void replace_deletes_existing_careers_and_updates_existing_profile() {
        Applicant applicant = createApplicant("career-replace", "Career Replace");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));
        applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(CareerType.EXPERIENCED, List.of(career("Old Company", 0)))
        );
        Long oldProfileId = careerProfileRepository.findByJobApplicationId(applicationId).orElseThrow().getId();
        List<Long> oldCareerIds = careerRepository.findByJobApplicationId(applicationId).stream()
                .map(ApplicationCareer::getId)
                .toList();

        CareerResponse response = applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(CareerType.NOT_APPLICABLE, List.of())
        );

        assertThat(response.careerType()).isEqualTo(CareerType.NOT_APPLICABLE);
        assertThat(response.careers()).isEmpty();
        assertThat(careerProfileRepository.findByJobApplicationId(applicationId).orElseThrow().getId())
                .isEqualTo(oldProfileId);
        assertThat(careerRepository.findAll()).extracting(ApplicationCareer::getId)
                .doesNotContainAnyElementsOf(oldCareerIds);
    }

    @Test
    void experienced_with_empty_careers_is_allowed_and_deletes_existing_data() {
        Applicant applicant = createApplicant("career-empty", "Career Empty");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));
        applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(CareerType.EXPERIENCED, List.of(career("Old Company", 0)))
        );

        CareerResponse response = applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(CareerType.EXPERIENCED, List.of())
        );

        assertThat(response.careerType()).isEqualTo(CareerType.EXPERIENCED);
        assertThat(response.careers()).isEmpty();
        assertThat(careerRepository.findByJobApplicationId(applicationId)).isEmpty();
    }

    @Test
    void non_experienced_type_cannot_have_career_items() {
        Applicant applicant = createApplicant("career-type-validation", "Career Type Validation");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(CareerType.NEWCOMER, List.of(career("Company", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(CareerType.NOT_APPLICABLE, List.of(career("Company", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(CareerType.NOT_SELECTED, List.of(career("Company", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void replace_fails_when_request_required_fields_are_missing() {
        Applicant applicant = createApplicant("career-required", "Career Required");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(null, List.of())
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(CareerType.EXPERIENCED, null)
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(CareerType.EXPERIENCED, List.of(career(null, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(CareerType.EXPERIENCED, List.of(new CareerRequest(
                        "Company",
                        null,
                        null,
                        EmploymentType.FULL_TIME,
                        null,
                        null,
                        true,
                        null,
                        null,
                        0
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(CareerType.EXPERIENCED, List.of(new CareerRequest(
                        "Company",
                        null,
                        null,
                        EmploymentType.FULL_TIME,
                        LocalDate.of(2024, 1, 1),
                        LocalDate.of(2024, 12, 31),
                        true,
                        null,
                        null,
                        0
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(CareerType.EXPERIENCED, List.of(new CareerRequest(
                        "Company",
                        null,
                        null,
                        EmploymentType.FULL_TIME,
                        LocalDate.of(2024, 1, 1),
                        null,
                        null,
                        null,
                        null,
                        0
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void replace_fails_when_date_or_sort_order_validation_fails() {
        Applicant applicant = createApplicant("career-cross-validation", "Career Cross Validation");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(CareerType.EXPERIENCED, List.of(new CareerRequest(
                        "Company",
                        null,
                        null,
                        EmploymentType.FULL_TIME,
                        LocalDate.of(2024, 1, 1),
                        null,
                        false,
                        null,
                        null,
                        0
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(CareerType.EXPERIENCED, List.of(new CareerRequest(
                        "Company",
                        null,
                        null,
                        EmploymentType.FULL_TIME,
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2024, 12, 31),
                        false,
                        null,
                        null,
                        0
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(CareerType.EXPERIENCED, List.of(career("A", 0), career("B", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void replace_fails_when_text_fields_exceed_max_length() {
        Applicant applicant = createApplicant("career-text-validation", "Career Text Validation");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));
        String longText = "a".repeat(2001);

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(CareerType.EXPERIENCED, List.of(new CareerRequest(
                        "Company",
                        null,
                        null,
                        EmploymentType.FULL_TIME,
                        LocalDate.of(2024, 1, 1),
                        LocalDate.of(2024, 12, 31),
                        false,
                        longText,
                        null,
                        0
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(CareerType.EXPERIENCED, List.of(new CareerRequest(
                        "Company",
                        null,
                        null,
                        EmploymentType.FULL_TIME,
                        LocalDate.of(2024, 1, 1),
                        LocalDate.of(2024, 12, 31),
                        false,
                        null,
                        longText,
                        0
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void replace_fails_when_career_section_is_disabled() {
        Applicant applicant = createApplicant("career-disabled", "Career Disabled");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(false));

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(CareerType.NEWCOMER, List.of())
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void replace_fails_when_application_is_not_draft() {
        Applicant submittedApplicant = createApplicant("career-submitted", "Career Submitted");
        Long submittedApplicationId = createApplication(submittedApplicant, createPublishedJobPosting(false));
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(submittedApplicationId).orElseThrow());
        jobApplicationService.submit(submittedApplicant.getId(), submittedApplicationId);

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                submittedApplicant.getId(),
                submittedApplicationId,
                new CareerReplaceRequest(CareerType.NEWCOMER, List.of())
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant withdrawnApplicant = createApplicant("career-withdrawn", "Career Withdrawn");
        Long withdrawnApplicationId = createApplication(withdrawnApplicant, createPublishedJobPosting(false));
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(withdrawnApplicationId).orElseThrow());
        jobApplicationService.submit(withdrawnApplicant.getId(), withdrawnApplicationId);
        jobApplicationService.withdraw(withdrawnApplicant.getId(), withdrawnApplicationId);

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                withdrawnApplicant.getId(),
                withdrawnApplicationId,
                new CareerReplaceRequest(CareerType.NEWCOMER, List.of())
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void other_applicants_application_is_hidden_for_get_and_replace() {
        Applicant owner = createApplicant("career-owner", "Career Owner");
        Applicant other = createApplicant("career-other", "Career Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting(true));

        assertThatThrownBy(() -> applicationCareerService.getCareers(other.getId(), applicationId))
                .isInstanceOf(JobApplicationNotFoundException.class);
        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                other.getId(),
                applicationId,
                new CareerReplaceRequest(CareerType.NEWCOMER, List.of())
        )).isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void replace_fails_outside_reception_period_or_unpublished_posting() {
        Applicant beforeApplicant = createApplicant("career-before", "Career Before");
        Long beforePostingId = createPublishedJobPosting(true);
        Long beforeApplicationId = createApplication(beforeApplicant, beforePostingId);
        setReceptionPeriod(beforePostingId, LocalDateTime.of(2026, 6, 16, 9, 0), LocalDateTime.of(2026, 6, 30, 18, 0));

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                beforeApplicant.getId(),
                beforeApplicationId,
                new CareerReplaceRequest(CareerType.NEWCOMER, List.of())
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant afterApplicant = createApplicant("career-after", "Career After");
        Long afterPostingId = createPublishedJobPosting(true);
        Long afterApplicationId = createApplication(afterApplicant, afterPostingId);
        setReceptionPeriod(afterPostingId, LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 14, 18, 0));

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                afterApplicant.getId(),
                afterApplicationId,
                new CareerReplaceRequest(CareerType.NEWCOMER, List.of())
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant draftApplicant = createApplicant("career-draft", "Career Draft");
        Long draftPostingId = createPublishedJobPosting(true);
        Long draftApplicationId = createApplication(draftApplicant, draftPostingId);
        setJobPostingStatus(draftPostingId, JobPostingStatus.DRAFT);

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                draftApplicant.getId(),
                draftApplicationId,
                new CareerReplaceRequest(CareerType.NEWCOMER, List.of())
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

    private Long createPublishedJobPosting(boolean useCareer) {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(
                        new JobPositionRequest("Backend", 0),
                        new JobPositionRequest("Frontend", 1)
                ),
                new ApplicationFormConfigRequest(false, useCareer, false, false, false, false, false)
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

    private CareerRequest career(String companyName, Integer sortOrder) {
        return new CareerRequest(
                companyName,
                "Platform",
                "Engineer",
                EmploymentType.FULL_TIME,
                LocalDate.of(2022, 1, 1),
                LocalDate.of(2024, 12, 31),
                false,
                "Backend development",
                "Career change",
                sortOrder
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
