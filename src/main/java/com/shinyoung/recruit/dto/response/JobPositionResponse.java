package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.enumeration.EmploymentType;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;

public record JobPositionResponse(
        Long id,
        String positionName,
        JobPositionApplicationType applicationType,
        String jobGroup,
        String jobTitle,
        String workLocation,
        EmploymentType employmentType,
        Integer sortOrder
) {
    public static JobPositionResponse from(JobPosition jobPosition) {
        return new JobPositionResponse(
                jobPosition.getId(),
                jobPosition.getPositionName(),
                jobPosition.getApplicationType(),
                jobPosition.getJobGroup(),
                jobPosition.getJobTitle(),
                jobPosition.getWorkLocation(),
                jobPosition.getEmploymentType(),
                jobPosition.getSortOrder()
        );
    }
}
