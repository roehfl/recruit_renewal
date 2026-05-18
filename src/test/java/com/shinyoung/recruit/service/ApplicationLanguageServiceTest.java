package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationLanguage;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationLanguageRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.LanguageReplaceRequest;
import com.shinyoung.recruit.dto.request.LanguageRequest;
import com.shinyoung.recruit.dto.response.LanguageResponse;
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
class ApplicationLanguageServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private ApplicationLanguageService applicationLanguageService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private ApplicationLanguageRepository languageRepository;

    @Test
    void replace_languages_success_and_get_success() {
        Applicant applicant = createApplicant("language-success", "Language Success");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        List<LanguageResponse> responses = applicationLanguageService.replaceLanguages(
                applicant.getId(),
                applicationId,
                new LanguageReplaceRequest(List.of(
                        language("Japanese", "JLPT", 1),
                        language("English", "TOEIC", 0)
                ))
        );
        List<LanguageResponse> getResponses = applicationLanguageService.getLanguages(applicant.getId(), applicationId);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(LanguageResponse::languageName)
                .containsExactly("English", "Japanese");
        assertThat(getResponses).extracting(LanguageResponse::languageId)
                .containsExactlyElementsOf(responses.stream().map(LanguageResponse::languageId).toList());
    }

    @Test
    void replace_deletes_existing_languages_and_empty_list_is_allowed() {
        Applicant applicant = createApplicant("language-replace", "Language Replace");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));
        applicationLanguageService.replaceLanguages(
                applicant.getId(),
                applicationId,
                new LanguageReplaceRequest(List.of(language("English", "TOEIC", 0)))
        );
        List<Long> oldIds = languageRepository.findByJobApplicationId(applicationId).stream()
                .map(ApplicationLanguage::getId)
                .toList();

        List<LanguageResponse> responses = applicationLanguageService.replaceLanguages(
                applicant.getId(),
                applicationId,
                new LanguageReplaceRequest(List.of())
        );

        assertThat(responses).isEmpty();
        assertThat(languageRepository.findByJobApplicationId(applicationId)).isEmpty();
        assertThat(languageRepository.findAll()).extracting(ApplicationLanguage::getId)
                .doesNotContainAnyElementsOf(oldIds);
    }

    @Test
    void replace_fails_when_language_section_is_disabled() {
        Applicant applicant = createApplicant("language-disabled", "Language Disabled");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(false));

        assertThatThrownBy(() -> applicationLanguageService.replaceLanguages(
                applicant.getId(),
                applicationId,
                new LanguageReplaceRequest(List.of(language("English", "TOEIC", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void replace_fails_when_application_is_not_draft() {
        Applicant submittedApplicant = createApplicant("language-submitted", "Language Submitted");
        Long submittedApplicationId = createApplication(submittedApplicant, createPublishedJobPosting(true));
        jobApplicationService.submit(submittedApplicant.getId(), submittedApplicationId);

        assertThatThrownBy(() -> applicationLanguageService.replaceLanguages(
                submittedApplicant.getId(),
                submittedApplicationId,
                new LanguageReplaceRequest(List.of(language("English", "TOEIC", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant withdrawnApplicant = createApplicant("language-withdrawn", "Language Withdrawn");
        Long withdrawnApplicationId = createApplication(withdrawnApplicant, createPublishedJobPosting(true));
        jobApplicationService.submit(withdrawnApplicant.getId(), withdrawnApplicationId);
        jobApplicationService.withdraw(withdrawnApplicant.getId(), withdrawnApplicationId);

        assertThatThrownBy(() -> applicationLanguageService.replaceLanguages(
                withdrawnApplicant.getId(),
                withdrawnApplicationId,
                new LanguageReplaceRequest(List.of(language("English", "TOEIC", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void other_applicants_application_is_hidden_for_get_and_replace() {
        Applicant owner = createApplicant("language-owner", "Language Owner");
        Applicant other = createApplicant("language-other", "Language Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting(true));

        assertThatThrownBy(() -> applicationLanguageService.getLanguages(other.getId(), applicationId))
                .isInstanceOf(JobApplicationNotFoundException.class);
        assertThatThrownBy(() -> applicationLanguageService.replaceLanguages(
                other.getId(),
                applicationId,
                new LanguageReplaceRequest(List.of(language("English", "TOEIC", 0)))
        )).isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void replace_fails_outside_reception_period_or_unpublished_posting() {
        Applicant beforeApplicant = createApplicant("language-before", "Language Before");
        Long beforePostingId = createPublishedJobPosting(true);
        Long beforeApplicationId = createApplication(beforeApplicant, beforePostingId);
        setReceptionPeriod(beforePostingId, LocalDateTime.of(2026, 6, 16, 9, 0), LocalDateTime.of(2026, 6, 30, 18, 0));

        assertThatThrownBy(() -> applicationLanguageService.replaceLanguages(
                beforeApplicant.getId(),
                beforeApplicationId,
                new LanguageReplaceRequest(List.of(language("English", "TOEIC", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant afterApplicant = createApplicant("language-after", "Language After");
        Long afterPostingId = createPublishedJobPosting(true);
        Long afterApplicationId = createApplication(afterApplicant, afterPostingId);
        setReceptionPeriod(afterPostingId, LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 14, 18, 0));

        assertThatThrownBy(() -> applicationLanguageService.replaceLanguages(
                afterApplicant.getId(),
                afterApplicationId,
                new LanguageReplaceRequest(List.of(language("English", "TOEIC", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant draftApplicant = createApplicant("language-draft", "Language Draft");
        Long draftPostingId = createPublishedJobPosting(true);
        Long draftApplicationId = createApplication(draftApplicant, draftPostingId);
        setJobPostingStatus(draftPostingId, JobPostingStatus.DRAFT);

        assertThatThrownBy(() -> applicationLanguageService.replaceLanguages(
                draftApplicant.getId(),
                draftApplicationId,
                new LanguageReplaceRequest(List.of(language("English", "TOEIC", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void replace_fails_when_required_or_cross_field_validation_fails() {
        Applicant applicant = createApplicant("language-validation", "Language Validation");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        assertThatThrownBy(() -> applicationLanguageService.replaceLanguages(
                applicant.getId(),
                applicationId,
                new LanguageReplaceRequest(null)
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationLanguageService.replaceLanguages(
                applicant.getId(),
                applicationId,
                new LanguageReplaceRequest(List.of(new LanguageRequest(
                        "",
                        "TOEIC",
                        "900",
                        null,
                        LocalDate.of(2024, 1, 1),
                        null,
                        null,
                        0
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationLanguageService.replaceLanguages(
                applicant.getId(),
                applicationId,
                new LanguageReplaceRequest(List.of(new LanguageRequest(
                        "English",
                        "",
                        "900",
                        null,
                        LocalDate.of(2024, 1, 1),
                        null,
                        null,
                        0
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationLanguageService.replaceLanguages(
                applicant.getId(),
                applicationId,
                new LanguageReplaceRequest(List.of(new LanguageRequest(
                        "English",
                        "TOEIC",
                        "900",
                        null,
                        null,
                        null,
                        null,
                        0
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationLanguageService.replaceLanguages(
                applicant.getId(),
                applicationId,
                new LanguageReplaceRequest(List.of(new LanguageRequest(
                        "English",
                        "TOEIC",
                        "900",
                        null,
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2024, 12, 31),
                        null,
                        0
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationLanguageService.replaceLanguages(
                applicant.getId(),
                applicationId,
                new LanguageReplaceRequest(List.of(language("English", "TOEIC", 0), language("Japanese", "JLPT", 0)))
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

    private Long createPublishedJobPosting(boolean useLanguage) {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(
                        new JobPositionRequest("Backend", 2, 0),
                        new JobPositionRequest("Frontend", 1, 1)
                ),
                new ApplicationFormConfigRequest(false, false, false, useLanguage, false, false, false)
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

    private LanguageRequest language(String languageName, String testName, Integer sortOrder) {
        return new LanguageRequest(
                languageName,
                testName,
                "900",
                null,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2026, 1, 1),
                "ETS",
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
