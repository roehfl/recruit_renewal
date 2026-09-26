package com.shinyoung.recruit.dto.response;

import java.util.List;

public record AdminInterviewSupplementResponse(
        Long stageId,
        boolean enabled,
        List<AdminInterviewSupplementQuestionResponse> questions,
        long answeredApplicantCount
) {
}
