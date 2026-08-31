package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobPositionWorkLocation;

/** 모집분야의 후보 근무지 1건(CommonCode 그룹 {@code WORK_LOCATION}). */
public record WorkLocationResponse(
        String code,
        String name
) {
    public static WorkLocationResponse from(JobPositionWorkLocation workLocation) {
        return new WorkLocationResponse(workLocation.getCode(), workLocation.getName());
    }
}
