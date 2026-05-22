package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.enumeration.EmploymentType;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;

public record JobPositionPublicResponse(
        Long id,
        String positionName,
        JobPositionApplicationType applicationType,
        String jobGroup,
        String jobTitle,
        String workLocation,
        EmploymentType employmentType,
        Integer headcount,
        Integer sortOrder
) {
    public static JobPositionPublicResponse from(JobPosition jobPosition) {
        return new JobPositionPublicResponse(
                jobPosition.getId(),
                jobPosition.getPositionName(),
                jobPosition.getApplicationType(),
                jobPosition.getJobGroup(),
                jobPosition.getJobTitle(),
                jobPosition.getWorkLocation(),
                jobPosition.getEmploymentType(),
                jobPosition.getHeadcount(),
                jobPosition.getSortOrder()
        );
    }
}
