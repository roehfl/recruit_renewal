package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationLanguage;

import java.time.LocalDate;

public record AdminLanguageResponse(
        Long languageId,
        String languageName,
        String testName,
        String score,
        String grade,
        LocalDate examDate,
        LocalDate expiredDate,
        String issuingOrganization,
        Integer sortOrder
) {

    public static AdminLanguageResponse from(ApplicationLanguage language) {
        return new AdminLanguageResponse(
                language.getId(),
                language.getLanguageName(),
                language.getTestName(),
                language.getScore(),
                language.getGrade(),
                language.getExamDate(),
                language.getExpiredDate(),
                language.getIssuingOrganization(),
                language.getSortOrder()
        );
    }
}
