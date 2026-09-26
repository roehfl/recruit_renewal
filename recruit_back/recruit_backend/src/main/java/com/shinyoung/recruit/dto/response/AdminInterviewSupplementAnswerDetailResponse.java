package com.shinyoung.recruit.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record AdminInterviewSupplementAnswerDetailResponse(
        Long jobApplicationId,
        String applicantName,
        String groupName,
        Integer candidateOrder,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        int answeredCount,
        LocalDateTime lastSavedAt,
        List<AdminInterviewSupplementAnswerItemResponse> items
) {
}
