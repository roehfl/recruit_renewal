package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.EmploymentType;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JobPositionRequest(
        @NotBlank @Size(max = 100) String positionName,
        JobPositionApplicationType applicationType,
        @Size(max = 100) String jobGroup,
        @Size(max = 100) String jobTitle,
        @Size(max = 100) String workLocation,
        EmploymentType employmentType,
        @NotNull @Min(1) Integer headcount,
        @NotNull @Min(0) Integer sortOrder
) {
    public JobPositionRequest(String positionName, Integer headcount, Integer sortOrder) {
        this(positionName, null, null, null, null, null, headcount, sortOrder);
    }
}
