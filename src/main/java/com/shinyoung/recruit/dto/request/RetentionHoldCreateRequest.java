package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** RetentionHold 수동 설정 요청(Phase 09c — manual hold only). */
public record RetentionHoldCreateRequest(
        @NotNull(message = "applicationId는 필수입니다.")
        Long applicationId,

        @NotBlank(message = "reason은 필수입니다.")
        @Size(max = 1000, message = "reason은 1000자 이하여야 합니다.")
        String reason
) {
}
