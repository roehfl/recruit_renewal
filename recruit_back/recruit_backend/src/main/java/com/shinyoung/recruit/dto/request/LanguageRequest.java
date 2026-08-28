package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record LanguageRequest(
        @NotBlank(message = "Language code is required.")
        String languageCode,

        @NotBlank(message = "Language name is required.")
        String languageName,

        @NotBlank(message = "Test code is required.")
        String testCode,

        @NotBlank(message = "Test name is required.")
        String testName,

        String scoreOrGrade,

        String conversationalAbility,

        @NotNull(message = "Exam date is required.")
        LocalDate examDate,

        LocalDate expiredDate,

        String issuingOrganization,

        @NotNull(message = "Sort order is required.")
        @Min(value = 0, message = "Sort order must be greater than or equal to 0.")
        Integer sortOrder
) {
}
