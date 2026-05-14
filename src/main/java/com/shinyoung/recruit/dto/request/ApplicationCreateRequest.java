package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotNull;

public record ApplicationCreateRequest(
        @NotNull(message = "채용공고 ID는 필수입니다.")
        Long jobPostingId,

        @NotNull(message = "모집분야 ID는 필수입니다.")
        Long jobPositionId
) {
}
