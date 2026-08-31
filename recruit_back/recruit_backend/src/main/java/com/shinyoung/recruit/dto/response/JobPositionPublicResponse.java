package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.enumeration.EmploymentType;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;

import java.util.List;

public record JobPositionPublicResponse(
        Long id,
        String positionName,
        JobPositionApplicationType applicationType,
        String jobTitle,
        List<WorkLocationResponse> workLocations,
        EmploymentType employmentType,
        Integer sortOrder
) {
    public static JobPositionPublicResponse from(JobPosition jobPosition) {
        return new JobPositionPublicResponse(
                jobPosition.getId(),
                jobPosition.getPositionName(),
                jobPosition.getApplicationType(),
                jobPosition.getJobTitle(),
                jobPosition.getWorkLocations().stream().map(WorkLocationResponse::from).toList(),
                jobPosition.getEmploymentType(),
                jobPosition.getSortOrder()
        );
    }
}
