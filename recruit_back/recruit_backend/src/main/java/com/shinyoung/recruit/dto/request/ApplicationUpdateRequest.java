package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotNull;

public record ApplicationUpdateRequest(
        @NotNull(message = "모집분야 ID는 필수입니다.")
        Long jobPositionId,

        /** 선택 근무지 코드. 모집분야에 후보 근무지가 없으면 생략한다. */
        String workLocationCode
) {
    /** 근무지 후보가 없는 모집분야용 축약 생성자. */
    public ApplicationUpdateRequest(Long jobPositionId) {
        this(jobPositionId, null);
    }
}
