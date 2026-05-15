package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotNull;

public record ApplicationUpdateRequest(
        @NotNull(message = "모집분야 ID는 필수입니다.")
        Long jobPositionId
) {
}
