package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.QuestionTemplate;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;

import java.time.LocalDateTime;

public record QuestionTemplateResponse(
        Long templateId,
        String title,
        String questionText,
        String helperText,
        QuestionCategory category,
        QuestionAnswerType answerType,
        Boolean defaultRequired,
        Integer defaultMaxLength,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static QuestionTemplateResponse from(QuestionTemplate template) {
        return new QuestionTemplateResponse(
                template.getId(),
                template.getTitle(),
                template.getQuestionText(),
                template.getHelperText(),
                template.getCategory(),
                template.getAnswerType(),
                template.getDefaultRequired(),
                template.getDefaultMaxLength(),
                template.getActive(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }
}
