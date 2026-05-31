package com.shinyoung.recruit.dto.response;

public record InterviewEvaluationInitializeResponse(
        Long interviewId,
        int createdCount,
        int alreadyExistedCount,
        int totalCount
) {
}
