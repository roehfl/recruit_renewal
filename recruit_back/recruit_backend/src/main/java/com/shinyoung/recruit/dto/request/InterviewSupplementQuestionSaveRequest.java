package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InterviewSupplementQuestionSaveRequest(
        @NotBlank @Size(max = 500) String content
) {
}
