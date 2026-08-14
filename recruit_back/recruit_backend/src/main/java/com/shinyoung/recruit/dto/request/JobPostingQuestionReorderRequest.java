package com.shinyoung.recruit.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record JobPostingQuestionReorderRequest(
        @NotNull @NotEmpty List<@Valid JobPostingQuestionOrderRequest> questions
) {
}
