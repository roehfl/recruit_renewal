package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewEvaluation;
import com.shinyoung.recruit.enumeration.InterviewStatus;

import java.time.LocalDateTime;
import java.util.List;

public record InterviewerEvaluationListResponse(
        Long interviewId,
        String interviewGroupName,
        InterviewStatus interviewStatus,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        List<InterviewerEvaluationSummaryResponse> evaluations
) {

    public static InterviewerEvaluationListResponse from(
            Interview interview,
            List<InterviewEvaluation> evaluations
    ) {
        List<InterviewerEvaluationSummaryResponse> items = evaluations.stream()
                .map(InterviewerEvaluationSummaryResponse::from)
                .toList();
        return new InterviewerEvaluationListResponse(
                interview.getId(),
                interview.getGroupName(),
                interview.getStatus(),
                interview.getStartDateTime(),
                interview.getEndDateTime(),
                items
        );
    }
}
