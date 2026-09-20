package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotNull;

/** 자동 파기 on/off 요청(Phase 10). */
public record RetentionScheduleRequest(
        @NotNull(message = "enabled는 필수입니다.")
        Boolean enabled
) {
}
