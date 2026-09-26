package com.shinyoung.recruit.dto.response;

import java.time.LocalDateTime;

/** 기본값을 만들 수 없으면(도착시간 없음) startDateTime·endDateTime 이 null. */
public record AdminInterviewSupplementCandidateResponse(
        Long jobApplicationId,
        String applicantName,
        Long interviewId,
        String groupName,
        Integer candidateOrder,
        LocalDateTime arrivalDateTime,
        LocalDateTime interviewStartDateTime,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        boolean customized,
        int answeredCount,
        LocalDateTime lastSavedAt
) {
}
