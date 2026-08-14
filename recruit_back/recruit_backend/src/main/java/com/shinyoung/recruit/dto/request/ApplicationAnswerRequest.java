package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApplicationAnswerRequest(
        @NotNull Long questionId,
        @Size(max = 5000) String answerText
) {
}
