package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JobPostingQuestionUpdateRequest(
        @NotBlank @Size(max = 2000) String questionText,
        @Size(max = 2000) String helperText,
        @NotNull QuestionCategory category,
        @NotNull QuestionAnswerType answerType,
        @NotNull Boolean required,
        @Min(0) Integer minLength,
        @NotNull @Min(1) Integer maxLength,
        @NotNull @Min(0) Integer sortOrder
) {
}
