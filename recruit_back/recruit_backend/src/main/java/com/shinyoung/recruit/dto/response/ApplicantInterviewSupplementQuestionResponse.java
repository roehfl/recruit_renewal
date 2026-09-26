package com.shinyoung.recruit.dto.response;

public record ApplicantInterviewSupplementQuestionResponse(
        Long questionId,
        String content,
        String answerText
) {
}
