package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationLanguage;

import java.time.LocalDate;

public record LanguageResponse(
        Long languageId,
        String languageCode,
        String languageName,
        String testCode,
        String testName,
        String scoreOrGrade,
        String conversationalAbility,
        LocalDate examDate,
        LocalDate expiredDate,
        String issuingOrganization,
        Integer sortOrder
) {

    public static LanguageResponse from(ApplicationLanguage language) {
        return new LanguageResponse(
                language.getId(),
                language.getLanguageCode(),
                language.getLanguageName(),
                language.getTestCode(),
                language.getTestName(),
                language.getScoreOrGrade(),
                language.getConversationalAbility(),
                language.getExamDate(),
                language.getExpiredDate(),
                language.getIssuingOrganization(),
                language.getSortOrder()
        );
    }
}
