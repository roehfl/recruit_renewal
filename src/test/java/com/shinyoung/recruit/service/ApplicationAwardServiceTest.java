package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationAward;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAwardRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.AwardReplaceRequest;
import com.shinyoung.recruit.dto.request.AwardRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.response.AwardResponse;
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
class ApplicationAwardServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private ApplicationAwardService applicationAwardService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private ApplicationAwardRepository awardRepository;

    @Test
    void replace_awards_success_and_get_success() {
        Applicant applicant = createApplicant("award-success", "Award Success");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        List<AwardResponse> responses = applicationAwardService.replaceAwards(
                applicant.getId(),
                applicationId,
                new AwardReplaceRequest(List.of(award("Second Prize", 1), award("First Prize", 0)))
        );
        List<AwardResponse> getResponses = applicationAwardService.getAwards(applicant.getId(), applicationId);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(AwardResponse::awardName)
                .containsExactly("First Prize", "Second Prize");
        assertThat(getResponses).extracting(AwardResponse::awardId)
                .containsExactlyElementsOf(responses.stream().map(AwardResponse::awardId).toList());
    }

    @Test
    void replace_deletes_existing_awards_and_empty_list_is_allowed() {
        Applicant applicant = createApplicant("award-replace", "Award Replace");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));
        applicationAwardService.replaceAwards(
                applicant.getId(),
                applicationId,
                new AwardReplaceRequest(List.of(award("Old Award", 0)))
        );
        List<Long> oldIds = awardRepository.findByJobApplicationId(applicationId).stream()
                .map(ApplicationAward::getId)
                .toList();

        List<AwardResponse> responses = applicationAwardService.replaceAwards(
                applicant.getId(),
                applicationId,
                new AwardReplaceRequest(List.of())
        );

        assertThat(responses).isEmpty();
        assertThat(awardRepository.findByJobApplicationId(applicationId)).isEmpty();
        assertThat(awardRepository.findAll()).extracting(ApplicationAward::getId)
                .doesNotContainAnyElementsOf(oldIds);
    }

    @Test
    void replace_fails_when_award_section_is_disabled() {
        Applicant applicant = createApplicant("award-disabled", "Award Disabled");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(false));

        assertThatThrownBy(() -> applicationAwardService.replaceAwards(
                applicant.getId(),
                applicationId,
                new AwardReplaceRequest(List.of(award("Prize", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void replace_fails_when_application_is_not_draft() {
        Applicant submittedApplicant = createApplicant("award-submitted", "Award Submitted");
        Long submittedApplicationId = createApplication(submittedApplicant, createPublishedJobPosting(true));
        jobApplicationService.submit(submittedApplicant.getId(), submittedApplicationId);

        assertThatThrownBy(() -> applicationAwardService.replaceAwards(
                submittedApplicant.getId(),
                submittedApplicationId,
                new AwardReplaceRequest(List.of(award("Prize", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant withdrawnApplicant = createApplicant("award-withdrawn", "Award Withdrawn");
        Long withdrawnApplicationId = createApplication(withdrawnApplicant, createPublishedJobPosting(true));
        jobApplicationService.submit(withdrawnApplicant.getId(), withdrawnApplicationId);
        jobApplicationService.withdraw(withdrawnApplicant.getId(), withdrawnApplicationId);

        assertThatThrownBy(() -> applicationAwardService.replaceAwards(
                withdrawnApplicant.getId(),
                withdrawnApplicationId,
                new AwardReplaceRequest(List.of(award("Prize", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void other_applicants_application_is_hidden_for_get_and_replace() {
        Applicant owner = createApplicant("award-owner", "Award Owner");
        Applicant other = createApplicant("award-other", "Award Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting(true));

        assertThatThrownBy(() -> applicationAwardService.getAwards(other.getId(), applicationId))
                .isInstanceOf(JobApplicationNotFoundException.class);
        assertThatThrownBy(() -> applicationAwardService.replaceAwards(
                other.getId(),
                applicationId,
                new AwardReplaceRequest(List.of(award("Prize", 0)))
        )).isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void replace_fails_outside_reception_period_or_unpublished_posting() {
        Applicant beforeApplicant = createApplicant("award-before", "Award Before");
        Long beforePostingId = createPublishedJobPosting(true);
        Long beforeApplicationId = createApplication(beforeApplicant, beforePostingId);
        setReceptionPeriod(beforePostingId, LocalDateTime.of(2026, 6, 16, 9, 0), LocalDateTime.of(2026, 6, 30, 18, 0));

        assertThatThrownBy(() -> applicationAwardService.replaceAwards(
                beforeApplicant.getId(),
                beforeApplicationId,
                new AwardReplaceRequest(List.of(award("Prize", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant afterApplicant = createApplicant("award-after", "Award After");
        Long afterPostingId = createPublishedJobPosting(true);
        Long afterApplicationId = createApplication(afterApplicant, afterPostingId);
        setReceptionPeriod(afterPostingId, LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 14, 18, 0));

        assertThatThrownBy(() -> applicationAwardService.replaceAwards(
                afterApplicant.getId(),
                afterApplicationId,
                new AwardReplaceRequest(List.of(award("Prize", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant draftApplicant = createApplicant("award-draft", "Award Draft");
        Long draftPostingId = createPublishedJobPosting(true);
        Long draftApplicationId = createApplication(draftApplicant, draftPostingId);
        setJobPostingStatus(draftPostingId, JobPostingStatus.DRAFT);

        assertThatThrownBy(() -> applicationAwardService.replaceAwards(
                draftApplicant.getId(),
                draftApplicationId,
                new AwardReplaceRequest(List.of(award("Prize", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void replace_fails_when_required_fields_sort_or_description_validation_fails() {
        Applicant applicant = createApplicant("award-validation", "Award Validation");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        assertThatThrownBy(() -> applicationAwardService.replaceAwards(
                applicant.getId(),
                applicationId,
                new AwardReplaceRequest(null)
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAwardService.replaceAwards(
                applicant.getId(),
                applicationId,
                new AwardReplaceRequest(List.of(new AwardRequest("", "Org", LocalDate.of(2024, 1, 1), null, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAwardService.replaceAwards(
                applicant.getId(),
                applicationId,
                new AwardReplaceRequest(List.of(new AwardRequest("Prize", "", LocalDate.of(2024, 1, 1), null, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAwardService.replaceAwards(
                applicant.getId(),
                applicationId,
                new AwardReplaceRequest(List.of(new AwardRequest("Prize", "Org", null, null, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAwardService.replaceAwards(
                applicant.getId(),
                applicationId,
                new AwardReplaceRequest(List.of(new AwardRequest("Prize", "Org", LocalDate.of(2024, 1, 1), "a".repeat(2001), 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAwardService.replaceAwards(
                applicant.getId(),
                applicationId,
                new AwardReplaceRequest(List.of(award("A", 0), award("B", 0)))
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

    private Long createPublishedJobPosting(boolean useAward) {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(
                        new JobPositionRequest("Backend", 2, 0),
                        new JobPositionRequest("Frontend", 1, 1)
                ),
                new ApplicationFormConfigRequest(false, false, false, false, false, useAward, false)
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

    private AwardRequest award(String awardName, Integer sortOrder) {
        return new AwardRequest(
                awardName,
                "Test Organization",
                LocalDate.of(2024, 1, 1),
                "test description",
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
