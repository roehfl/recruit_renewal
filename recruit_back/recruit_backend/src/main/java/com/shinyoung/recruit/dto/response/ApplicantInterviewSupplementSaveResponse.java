package com.shinyoung.recruit.dto.response;

import java.time.LocalDateTime;

public record ApplicantInterviewSupplementSaveResponse(
        LocalDateTime savedAt,
        long remainingSeconds,
        int answeredCount
) {
}
