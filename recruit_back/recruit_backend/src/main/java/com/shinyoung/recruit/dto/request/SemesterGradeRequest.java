package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SemesterGradeRequest(
        @NotNull(message = "School year is required.")
        @Min(value = 1, message = "School year must be greater than or equal to 1.")
        Integer schoolYear,

        @NotNull(message = "Semester is required.")
        @Min(value = 1, message = "Semester must be greater than or equal to 1.")
        Integer semester,

        @DecimalMin(value = "0.0", message = "Earned credits must be greater than or equal to 0.")
        BigDecimal earnedCredits,

        @NotNull(message = "Grade point is required.")
        @DecimalMin(value = "0.0", message = "Grade point must be greater than or equal to 0.")
        BigDecimal gradePoint,

        @NotNull(message = "Max grade point is required.")
        @DecimalMin(value = "0.0", inclusive = false, message = "Max grade point must be greater than 0.")
        BigDecimal maxGradePoint,

        @DecimalMin(value = "0.0", message = "Major grade point must be greater than or equal to 0.")
        BigDecimal majorGradePoint,

        @DecimalMin(value = "0.0", inclusive = false, message = "Major max grade point must be greater than 0.")
        BigDecimal majorMaxGradePoint
) {
}
