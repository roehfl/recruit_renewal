package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.InterviewSupplementQuestion;

public record AdminInterviewSupplementQuestionResponse(
        Long questionId,
        String content,
        Integer sortOrder
) {
    public static AdminInterviewSupplementQuestionResponse from(InterviewSupplementQuestion question) {
        return new AdminInterviewSupplementQuestionResponse(
                question.getId(),
                question.getContent(),
                question.getSortOrder()
        );
    }
}
