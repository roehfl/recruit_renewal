package com.shinyoung.recruit.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ApplicationAnswerReplaceRequest(
        @NotNull List<@Valid ApplicationAnswerRequest> answers
) {
}
