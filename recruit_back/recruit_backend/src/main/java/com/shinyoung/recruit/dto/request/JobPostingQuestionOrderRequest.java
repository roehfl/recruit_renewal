package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record JobPostingQuestionOrderRequest(
        @NotNull Long questionId,
        @NotNull @Min(0) Integer sortOrder
) {
}
