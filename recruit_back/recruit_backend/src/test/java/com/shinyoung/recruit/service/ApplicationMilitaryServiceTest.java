package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationMilitary;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.ApplicationMilitaryRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.support.BasicInfoTestSupport;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.MilitarySaveRequest;
import com.shinyoung.recruit.dto.response.MilitaryResponse;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.MilitaryBranch;
import com.shinyoung.recruit.enumeration.MilitaryRank;
import com.shinyoung.recruit.enumeration.MilitaryServiceType;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
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
class ApplicationMilitaryServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private ApplicationMilitaryService applicationMilitaryService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private ApplicationMilitaryRepository militaryRepository;

    @Autowired
    private ApplicationBasicInfoRepository basicInfoRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Test
    void save_subject_and_get_success() {
        Applicant applicant = createApplicant("military-subject", "Military Subject");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        MilitaryResponse saved = applicationMilitaryService.saveMilitary(
                applicant.getId(),
                applicationId,
                subjectRequest(MilitarySubjectType.SUBJECT)
        );
        MilitaryResponse found = applicationMilitaryService.getMilitary(applicant.getId(), applicationId);

        assertThat(saved.militarySubjectType()).isEqualTo(MilitarySubjectType.SUBJECT);
        assertThat(saved.serviceType()).isNull();
        assertThat(found.militaryId()).isEqualTo(saved.militaryId());
    }

    @Test
    void save_not_subject_completed_and_exempted_success() {
        Applicant notSubjectApplicant = createApplicant("military-not-subject", "Military Not Subject");
        Long notSubjectApplicationId = createApplication(notSubjectApplicant, createPublishedJobPosting(true));
        assertThat(applicationMilitaryService.saveMilitary(
                notSubjectApplicant.getId(),
                notSubjectApplicationId,
                subjectRequest(MilitarySubjectType.NOT_SUBJECT)
        ).militarySubjectType()).isEqualTo(MilitarySubjectType.NOT_SUBJECT);

        Applicant completedApplicant = createApplicant("military-completed", "Military Completed");
        Long completedApplicationId = createApplication(completedApplicant, createPublishedJobPosting(true));
        MilitaryResponse completed = applicationMilitaryService.saveMilitary(
                completedApplicant.getId(),
                completedApplicationId,
                completedRequest()
        );
        assertThat(completed.militarySubjectType()).isEqualTo(MilitarySubjectType.COMPLETED);
        assertThat(completed.serviceType()).isEqualTo(MilitaryServiceType.ACTIVE_DUTY);

        Applicant exemptedApplicant = createApplicant("military-exempted", "Military Exempted");
        Long exemptedApplicationId = createApplication(exemptedApplicant, createPublishedJobPosting(true));
        MilitaryResponse exempted = applicationMilitaryService.saveMilitary(
                exemptedApplicant.getId(),
                exemptedApplicationId,
                exemptedRequest("test reason")
        );
        assertThat(exempted.militarySubjectType()).isEqualTo(MilitarySubjectType.EXEMPTED);
        assertThat(exempted.nonServiceReason()).isEqualTo("test reason");
    }

    @Test
    void save_subject_with_non_service_reason_success() {
        Applicant applicant = createApplicant("military-subject-reason", "Military Subject Reason");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        MilitaryResponse saved = applicationMilitaryService.saveMilitary(
                applicant.getId(),
                applicationId,
                new MilitarySaveRequest(MilitarySubjectType.SUBJECT, null, null, null, null, null, "졸업 예정으로 입대 연기")
        );

        assertThat(saved.militarySubjectType()).isEqualTo(MilitarySubjectType.SUBJECT);
        assertThat(saved.nonServiceReason()).isEqualTo("졸업 예정으로 입대 연기");
    }

    @Test
    void get_returns_null_when_military_record_does_not_exist() {
        Applicant applicant = createApplicant("military-empty", "Military Empty");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        MilitaryResponse response = applicationMilitaryService.getMilitary(applicant.getId(), applicationId);

        assertThat(response).isNull();
    }

    @Test
    void save_updates_existing_record() {
        Applicant applicant = createApplicant("military-update", "Military Update");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));
        MilitaryResponse first = applicationMilitaryService.saveMilitary(
                applicant.getId(),
                applicationId,
                subjectRequest(MilitarySubjectType.SUBJECT)
        );

        MilitaryResponse updated = applicationMilitaryService.saveMilitary(
                applicant.getId(),
                applicationId,
                exemptedRequest("updated reason")
        );

        assertThat(updated.militaryId()).isEqualTo(first.militaryId());
        assertThat(updated.militarySubjectType()).isEqualTo(MilitarySubjectType.EXEMPTED);
        assertThat(updated.nonServiceReason()).isEqualTo("updated reason");
        assertThat(militaryRepository.findAll()).hasSize(1);
    }

    @Test
    void save_fails_when_military_section_is_disabled() {
        Applicant applicant = createApplicant("military-disabled", "Military Disabled");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(false));

        assertThatThrownBy(() -> applicationMilitaryService.saveMilitary(
                applicant.getId(),
                applicationId,
                subjectRequest(MilitarySubjectType.SUBJECT)
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void save_fails_when_application_is_not_draft() {
        Applicant submittedApplicant = createApplicant("military-submitted", "Military Submitted");
        Long submittedApplicationId = createApplication(submittedApplicant, createPublishedJobPosting(false));
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(submittedApplicationId).orElseThrow());
        jobApplicationService.submit(submittedApplicant.getId(), submittedApplicationId);

        assertThatThrownBy(() -> applicationMilitaryService.saveMilitary(
                submittedApplicant.getId(),
                submittedApplicationId,
                subjectRequest(MilitarySubjectType.SUBJECT)
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant withdrawnApplicant = createApplicant("military-withdrawn", "Military Withdrawn");
        Long withdrawnApplicationId = createApplication(withdrawnApplicant, createPublishedJobPosting(false));
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(withdrawnApplicationId).orElseThrow());
        jobApplicationService.submit(withdrawnApplicant.getId(), withdrawnApplicationId);
        jobApplicationService.withdraw(withdrawnApplicant.getId(), withdrawnApplicationId);

        assertThatThrownBy(() -> applicationMilitaryService.saveMilitary(
                withdrawnApplicant.getId(),
                withdrawnApplicationId,
                subjectRequest(MilitarySubjectType.SUBJECT)
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void other_applicants_application_is_hidden_for_get_and_save() {
        Applicant owner = createApplicant("military-owner", "Military Owner");
        Applicant other = createApplicant("military-other", "Military Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting(true));

        assertThatThrownBy(() -> applicationMilitaryService.getMilitary(other.getId(), applicationId))
                .isInstanceOf(JobApplicationNotFoundException.class);
        assertThatThrownBy(() -> applicationMilitaryService.saveMilitary(
                other.getId(),
                applicationId,
                subjectRequest(MilitarySubjectType.SUBJECT)
        )).isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void save_fails_outside_reception_period_or_unpublished_posting() {
        Applicant beforeApplicant = createApplicant("military-before", "Military Before");
        Long beforePostingId = createPublishedJobPosting(true);
        Long beforeApplicationId = createApplication(beforeApplicant, beforePostingId);
        setReceptionPeriod(beforePostingId, LocalDateTime.of(2026, 6, 16, 9, 0), LocalDateTime.of(2026, 6, 30, 18, 0));

        assertThatThrownBy(() -> applicationMilitaryService.saveMilitary(
                beforeApplicant.getId(),
                beforeApplicationId,
                subjectRequest(MilitarySubjectType.SUBJECT)
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant afterApplicant = createApplicant("military-after", "Military After");
        Long afterPostingId = createPublishedJobPosting(true);
        Long afterApplicationId = createApplication(afterApplicant, afterPostingId);
        setReceptionPeriod(afterPostingId, LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 14, 18, 0));

        assertThatThrownBy(() -> applicationMilitaryService.saveMilitary(
                afterApplicant.getId(),
                afterApplicationId,
                subjectRequest(MilitarySubjectType.SUBJECT)
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant draftApplicant = createApplicant("military-draft", "Military Draft");
        Long draftPostingId = createPublishedJobPosting(true);
        Long draftApplicationId = createApplication(draftApplicant, draftPostingId);
        setJobPostingStatus(draftPostingId, JobPostingStatus.DRAFT);

        assertThatThrownBy(() -> applicationMilitaryService.saveMilitary(
                draftApplicant.getId(),
                draftApplicationId,
                subjectRequest(MilitarySubjectType.SUBJECT)
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void save_fails_when_common_validation_fails() {
        Applicant applicant = createApplicant("military-common-validation", "Military Common Validation");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        assertThatThrownBy(() -> applicationMilitaryService.saveMilitary(applicant.getId(), applicationId, null))
                .isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationMilitaryService.saveMilitary(
                applicant.getId(),
                applicationId,
                new MilitarySaveRequest(null, null, null, null, null, null, null)
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationMilitaryService.saveMilitary(
                applicant.getId(),
                applicationId,
                new MilitarySaveRequest(
                        MilitarySubjectType.COMPLETED,
                        MilitaryServiceType.ACTIVE_DUTY,
                        MilitaryBranch.ARMY,
                        MilitaryRank.SERGEANT,
                        LocalDate.of(2024, 1, 1),
                        LocalDate.of(2023, 12, 31),
                        null
                )
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationMilitaryService.saveMilitary(
                applicant.getId(),
                applicationId,
                exemptedRequest("a".repeat(1001))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void save_fails_when_subject_type_field_policy_is_violated() {
        Applicant applicant = createApplicant("military-policy", "Military Policy");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        assertThatThrownBy(() -> applicationMilitaryService.saveMilitary(
                applicant.getId(),
                applicationId,
                subjectWithDetailRequest(MilitarySubjectType.SUBJECT)
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationMilitaryService.saveMilitary(
                applicant.getId(),
                applicationId,
                subjectWithDetailRequest(MilitarySubjectType.NOT_SUBJECT)
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationMilitaryService.saveMilitary(
                applicant.getId(),
                applicationId,
                new MilitarySaveRequest(
                        MilitarySubjectType.NOT_SUBJECT,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "not allowed for non-subject"
                )
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationMilitaryService.saveMilitary(
                applicant.getId(),
                applicationId,
                new MilitarySaveRequest(
                        MilitarySubjectType.COMPLETED,
                        MilitaryServiceType.ACTIVE_DUTY,
                        MilitaryBranch.ARMY,
                        MilitaryRank.SERGEANT,
                        LocalDate.of(2021, 1, 1),
                        LocalDate.of(2022, 12, 31),
                        "not allowed"
                )
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationMilitaryService.saveMilitary(
                applicant.getId(),
                applicationId,
                new MilitarySaveRequest(
                        MilitarySubjectType.EXEMPTED,
                        MilitaryServiceType.ACTIVE_DUTY,
                        null,
                        null,
                        null,
                        null,
                        "reason"
                )
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

    private Long createPublishedJobPosting(boolean useMilitary) {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(
                        new JobPositionRequest("Backend", 0),
                        new JobPositionRequest("Frontend", 1)
                ),
                new ApplicationFormConfigRequest(false, false, false, false, useMilitary, false, false)
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

    private MilitarySaveRequest subjectRequest(MilitarySubjectType subjectType) {
        return new MilitarySaveRequest(subjectType, null, null, null, null, null, null);
    }

    private MilitarySaveRequest completedRequest() {
        return new MilitarySaveRequest(
                MilitarySubjectType.COMPLETED,
                MilitaryServiceType.ACTIVE_DUTY,
                MilitaryBranch.ARMY,
                MilitaryRank.SERGEANT,
                LocalDate.of(2021, 1, 1),
                LocalDate.of(2022, 12, 31),
                null
        );
    }

    private MilitarySaveRequest exemptedRequest(String reason) {
        return new MilitarySaveRequest(MilitarySubjectType.EXEMPTED, null, null, null, null, null, reason);
    }

    private MilitarySaveRequest subjectWithDetailRequest(MilitarySubjectType subjectType) {
        return new MilitarySaveRequest(
                subjectType,
                MilitaryServiceType.ACTIVE_DUTY,
                null,
                null,
                null,
                null,
                null
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
