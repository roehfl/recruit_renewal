package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewEvaluation;
import com.shinyoung.recruit.enumeration.InterviewStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Application-level admin evaluation view: one element per interview the candidate took part in.
 * The candidate is fixed (a single application), so evaluations sit directly under each interview
 * without an additional candidate grouping.
 */
public record AdminApplicationEvaluationResponse(
        Long interviewId,
        String interviewGroupName,
        Long stageId,
        String stageName,
        InterviewStatus interviewStatus,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        AdminEvaluationSummaryResponse summary,
        List<AdminEvaluationItemResponse> evaluations
) {

    public static AdminApplicationEvaluationResponse from(List<InterviewEvaluation> interviewEvaluations) {
        InterviewEvaluation first = interviewEvaluations.get(0);
        Interview interview = first.getInterview();
        List<AdminEvaluationItemResponse> items = interviewEvaluations.stream()
                .map(AdminEvaluationItemResponse::from)
                .toList();
        return new AdminApplicationEvaluationResponse(
                interview.getId(),
                interview.getGroupName(),
                interview.getStage().getId(),
                interview.getStage().getStageName(),
                interview.getStatus(),
                interview.getStartDateTime(),
                interview.getEndDateTime(),
                AdminEvaluationSummaryResponse.from(interviewEvaluations),
                items
        );
    }
}
