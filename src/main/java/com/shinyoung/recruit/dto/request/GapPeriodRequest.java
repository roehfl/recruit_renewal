package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.GapType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record GapPeriodRequest(
        @NotNull(message = "Start date is required.")
        LocalDate startDate,

        @NotNull(message = "End date is required.")
        LocalDate endDate,

        @NotNull(message = "Gap type is required.")
        GapType gapType,

        @NotBlank(message = "Reason is required.")
        String reason,

        @Size(max = 2000, message = "Description must be 2000 characters or less.")
        String description,

        @NotNull(message = "Sort order is required.")
        @Min(value = 0, message = "Sort order must be greater than or equal to 0.")
        Integer sortOrder
) {
}
