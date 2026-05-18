package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationAnswer;
import com.shinyoung.recruit.domain.entity.JobPostingQuestion;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;

import java.time.LocalDateTime;

public record AdminApplicationAnswerResponse(
        Long questionId,
        String questionText,
        QuestionCategory category,
        QuestionAnswerType answerType,
        Boolean required,
        Integer minLength,
        Integer maxLength,
        Integer sortOrder,
        Long answerId,
        String answerText,
        LocalDateTime answerUpdatedAt
) {

    public static AdminApplicationAnswerResponse of(JobPostingQuestion question, ApplicationAnswer answer) {
        return new AdminApplicationAnswerResponse(
                question.getId(),
                answer == null || answer.getQuestionTextSnapshot() == null
                        ? question.getQuestionText()
                        : answer.getQuestionTextSnapshot(),
                answer == null || answer.getCategorySnapshot() == null
                        ? question.getCategory()
                        : answer.getCategorySnapshot(),
                answer == null || answer.getAnswerTypeSnapshot() == null
                        ? question.getAnswerType()
                        : answer.getAnswerTypeSnapshot(),
                answer == null || answer.getRequiredSnapshot() == null
                        ? question.getRequired()
                        : answer.getRequiredSnapshot(),
                answer == null || answer.getMinLengthSnapshot() == null
                        ? question.getMinLength()
                        : answer.getMinLengthSnapshot(),
                answer == null || answer.getMaxLengthSnapshot() == null
                        ? question.getMaxLength()
                        : answer.getMaxLengthSnapshot(),
                answer == null || answer.getSortOrderSnapshot() == null
                        ? question.getSortOrder()
                        : answer.getSortOrderSnapshot(),
                answer == null ? null : answer.getId(),
                answer == null ? null : answer.getAnswerText(),
                answer == null ? null : answer.getUpdatedAt()
        );
    }
}
