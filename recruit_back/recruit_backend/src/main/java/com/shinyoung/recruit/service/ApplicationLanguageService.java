package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationLanguage;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.ApplicationLanguageRepository;
import com.shinyoung.recruit.dto.request.LanguageReplaceRequest;
import com.shinyoung.recruit.dto.request.LanguageRequest;
import com.shinyoung.recruit.dto.response.LanguageResponse;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ApplicationLanguageService {

    private final ApplicationSectionAccessService sectionAccessService;
    private final ApplicationLanguageRepository languageRepository;

    @Transactional(readOnly = true)
    public List<LanguageResponse> getLanguages(Long applicantId, Long applicationId) {
        sectionAccessService.findOwnedApplication(applicantId, applicationId);
        return getLanguageResponses(applicationId);
    }

    @Transactional
    public List<LanguageResponse> replaceLanguages(
            Long applicantId,
            Long applicationId,
            LanguageReplaceRequest request
    ) {
        JobApplication application = sectionAccessService.findOwnedApplication(applicantId, applicationId);
        sectionAccessService.validateWritable(application);
        sectionAccessService.validateLanguageEnabled(application);
        validateRequest(request);

        languageRepository.deleteByJobApplicationId(applicationId);
        List<ApplicationLanguage> languages = request.languages().stream()
                .map(language -> toLanguage(application, language))
                .toList();
        languageRepository.saveAll(languages);

        return getLanguageResponses(applicationId);
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
        if (language.languageCode() == null || language.languageCode().isBlank()) {
            throw new InvalidJobApplicationException("Language code is required.");
        }
        if (language.languageName() == null || language.languageName().isBlank()) {
            throw new InvalidJobApplicationException("Language name is required.");
        }
        if (language.testCode() == null || language.testCode().isBlank()) {
            throw new InvalidJobApplicationException("Test code is required.");
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
                request.languageCode(),
                request.languageName(),
                request.testCode(),
                request.testName(),
                request.scoreOrGrade(),
                request.conversationalAbility(),
                request.examDate(),
                request.expiredDate(),
                request.issuingOrganization(),
                request.registrationNumber(),
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
