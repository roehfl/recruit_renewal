package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InterviewSupplementAnswerItemRequest(
        @NotNull Long questionId,
        @Size(max = 1000) String answerText
) {
}
