package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JobPostingQuestionCreateRequest(
        Long questionTemplateId,
        @Size(max = 2000) String questionText,
        @Size(max = 2000) String helperText,
        QuestionCategory category,
        QuestionAnswerType answerType,
        Boolean required,
        @Min(0) Integer minLength,
        @Min(1) Integer maxLength,
        @NotNull @Min(0) Integer sortOrder
) {
}
