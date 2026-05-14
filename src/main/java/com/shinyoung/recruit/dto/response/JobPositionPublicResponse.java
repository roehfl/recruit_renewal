package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobPosition;

public record JobPositionPublicResponse(
        Long id,
        String positionName,
        Integer headcount,
        Integer sortOrder
) {
    public static JobPositionPublicResponse from(JobPosition jobPosition) {
        return new JobPositionPublicResponse(
                jobPosition.getId(),
                jobPosition.getPositionName(),
                jobPosition.getHeadcount(),
                jobPosition.getSortOrder()
        );
    }
}
