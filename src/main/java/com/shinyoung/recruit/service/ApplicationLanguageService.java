package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.ApplicationLanguage;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.ApplicationLanguageRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.dto.request.LanguageReplaceRequest;
import com.shinyoung.recruit.dto.request.LanguageRequest;
import com.shinyoung.recruit.dto.response.LanguageResponse;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ApplicationLanguageService {

    private final JobApplicationRepository jobApplicationRepository;
    private final ApplicationLanguageRepository languageRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<LanguageResponse> getLanguages(Long applicantId, Long applicationId) {
        findApplication(applicantId, applicationId);
        return getLanguageResponses(applicationId);
    }

    @Transactional
    public List<LanguageResponse> replaceLanguages(
            Long applicantId,
            Long applicationId,
            LanguageReplaceRequest request
    ) {
        JobApplication application = findApplication(applicantId, applicationId);
        validateLanguageWritable(application);
        validateRequest(request);

        languageRepository.deleteByJobApplicationId(applicationId);
        List<ApplicationLanguage> languages = request.languages().stream()
                .map(language -> toLanguage(application, language))
                .toList();
        languageRepository.saveAll(languages);

        return getLanguageResponses(applicationId);
    }

    private JobApplication findApplication(Long applicantId, Long applicationId) {
        return jobApplicationRepository.findByIdAndApplicantId(applicationId, applicantId)
                .orElseThrow(() -> new JobApplicationNotFoundException("Application was not found."));
    }

    private void validateLanguageWritable(JobApplication application) {
        if (application.getStatus() != JobApplicationStatus.DRAFT) {
            throw new InvalidJobApplicationException("Language can be modified only in DRAFT status.");
        }
        if (application.getJobPosting().getStatus() != JobPostingStatus.PUBLISHED) {
            throw new InvalidJobApplicationException("Language can be modified only for a published job posting.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(application.getJobPosting().getReceptionStartDateTime())
                || now.isAfter(application.getJobPosting().getReceptionEndDateTime())) {
            throw new InvalidJobApplicationException("Language can be modified only during the reception period.");
        }

        ApplicationFormConfig config = application.getJobPosting().getApplicationFormConfig();
        if (config == null || !config.isUseLanguage()) {
            throw new InvalidJobApplicationException("Language section is not enabled for this job posting.");
        }
    }

    private void validateRequest(LanguageReplaceRequest request) {
        if (request == null || request.languages() == null) {
            throw new InvalidJobApplicationException("Language list is required.");
        }

        Set<Integer> sortOrders = new HashSet<>();
        for (LanguageRequest language : request.languages()) {
            validateLanguageRequiredFields(language);
            if (!sortOrders.add(language.sortOrder())) {
                throw new InvalidJobApplicationException("Language sort order must be unique.");
            }
        }
    }

    private void validateLanguageRequiredFields(LanguageRequest language) {
        if (language == null) {
            throw new InvalidJobApplicationException("Language item is required.");
        }
        if (language.languageName() == null || language.languageName().isBlank()) {
            throw new InvalidJobApplicationException("Language name is required.");
        }
        if (language.testName() == null || language.testName().isBlank()) {
            throw new InvalidJobApplicationException("Test name is required.");
        }
        if (language.examDate() == null) {
            throw new InvalidJobApplicationException("Exam date is required.");
        }
        if (language.sortOrder() == null || language.sortOrder() < 0) {
            throw new InvalidJobApplicationException("Sort order must be greater than or equal to 0.");
        }
        if (language.expiredDate() != null && language.examDate().isAfter(language.expiredDate())) {
            throw new InvalidJobApplicationException("Exam date cannot be after expired date.");
        }
    }

    private ApplicationLanguage toLanguage(JobApplication application, LanguageRequest request) {
        return ApplicationLanguage.create(
                application,
                request.languageName(),
                request.testName(),
                request.score(),
                request.grade(),
                request.examDate(),
                request.expiredDate(),
                request.issuingOrganization(),
                request.sortOrder()
        );
    }

    private List<LanguageResponse> getLanguageResponses(Long applicationId) {
        return languageRepository.findByJobApplicationIdOrderBySortOrderAscIdAsc(applicationId)
                .stream()
                .map(LanguageResponse::from)
                .toList();
    }
}
