package com.shinyoung.recruit.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ApplicantInterviewSupplementFormResponse(
        Long applicationId,
        Long stageId,
        String jobPostingTitle,
        String stageName,
        LocalDateTime endDateTime,
        long remainingSeconds,
        List<ApplicantInterviewSupplementQuestionResponse> questions
) {
}
