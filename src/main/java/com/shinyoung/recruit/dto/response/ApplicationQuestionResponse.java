package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationAnswer;
import com.shinyoung.recruit.domain.entity.JobPostingQuestion;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;

import java.time.LocalDateTime;

public record ApplicationQuestionResponse(
        Long questionId,
        String questionText,
        String helperText,
        QuestionCategory category,
        QuestionAnswerType answerType,
        Boolean required,
        Integer minLength,
        Integer maxLength,
        Integer sortOrder,
        Long answerId,
        String answerText,
        LocalDateTime updatedAt
) {
    public static ApplicationQuestionResponse of(JobPostingQuestion question, ApplicationAnswer answer) {
        return new ApplicationQuestionResponse(
                question.getId(),
                question.getQuestionText(),
                question.getHelperText(),
                question.getCategory(),
                question.getAnswerType(),
                question.getRequired(),
                question.getMinLength(),
                question.getMaxLength(),
                question.getSortOrder(),
                answer == null ? null : answer.getId(),
                answer == null ? null : answer.getAnswerText(),
                answer == null ? null : answer.getUpdatedAt()
        );
    }
}
