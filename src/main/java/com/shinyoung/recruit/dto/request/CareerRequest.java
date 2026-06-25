package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.EmploymentType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CareerRequest(
        @NotBlank(message = "Company name is required.")
        String companyName,

        String departmentName,

        String positionTitle,

        EmploymentType employmentType,

        @NotNull(message = "Start date is required.")
        LocalDate startDate,

        LocalDate endDate,

        LocalDate promotionDate,

        @NotNull(message = "Currently employed flag is required.")
        Boolean currentlyEmployed,

        @PositiveOrZero(message = "Current salary must be greater than or equal to 0.")
        Integer currentSalary,

        @Size(max = 2000, message = "Resignation reason must be 2000 characters or less.")
        String resignationReason,

        @NotNull(message = "Sort order is required.")
        @Min(value = 0, message = "Sort order must be greater than or equal to 0.")
        Integer sortOrder
) {
}
