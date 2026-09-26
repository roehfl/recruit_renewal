package com.shinyoung.recruit.dto.response;

import java.time.LocalDateTime;

public record AdminInterviewSupplementAnswerItemResponse(
        Long questionId,
        String content,
        String answerText,
        LocalDateTime savedAt
) {
}
