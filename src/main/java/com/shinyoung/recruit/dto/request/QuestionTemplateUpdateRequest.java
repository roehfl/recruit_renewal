package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QuestionTemplateUpdateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 2000) String questionText,
        @Size(max = 2000) String helperText,
        @NotNull QuestionCategory category,
        @NotNull QuestionAnswerType answerType,
        @NotNull Boolean defaultRequired,
        @NotNull @Min(1) Integer defaultMaxLength
) {
}
