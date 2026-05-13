package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobPosition;

public record JobPositionResponse(
        Long id,
        String positionName,
        Integer headcount,
        Integer sortOrder
) {
    public static JobPositionResponse from(JobPosition jobPosition) {
        return new JobPositionResponse(
                jobPosition.getId(),
                jobPosition.getPositionName(),
                jobPosition.getHeadcount(),
                jobPosition.getSortOrder()
        );
    }
}
