package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobPostingQuestion;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;

import java.time.LocalDateTime;

public record JobPostingQuestionResponse(
        Long questionId,
        Long questionTemplateId,
        String questionText,
        String helperText,
        QuestionCategory category,
        QuestionAnswerType answerType,
        Boolean required,
        Integer minLength,
        Integer maxLength,
        Integer sortOrder,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static JobPostingQuestionResponse from(JobPostingQuestion question) {
        Long templateId = question.getQuestionTemplate() == null ? null : question.getQuestionTemplate().getId();
        return new JobPostingQuestionResponse(
                question.getId(),
                templateId,
                question.getQuestionText(),
                question.getHelperText(),
                question.getCategory(),
                question.getAnswerType(),
                question.getRequired(),
                question.getMinLength(),
                question.getMaxLength(),
                question.getSortOrder(),
                question.getActive(),
                question.getCreatedAt(),
                question.getUpdatedAt()
        );
    }
}
