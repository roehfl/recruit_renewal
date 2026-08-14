package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AwardRequest(
        @NotBlank(message = "Award name is required.")
        String awardName,

        @NotBlank(message = "Awarding organization is required.")
        String awardingOrganization,

        @NotNull(message = "Award date is required.")
        LocalDate awardDate,

        @Size(max = 2000, message = "Description must be 2000 characters or less.")
        String description,

        @NotNull(message = "Sort order is required.")
        @Min(value = 0, message = "Sort order must be greater than or equal to 0.")
        Integer sortOrder
) {
}
