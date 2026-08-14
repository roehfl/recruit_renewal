package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.InterviewEvaluation;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.JobApplication;

import java.util.List;

/**
 * Evaluations for a single candidate within one interview, grouped with a per-candidate summary.
 */
public record AdminEvaluationCandidateResponse(
        Long candidateParticipantId,
        Long applicationId,
        String applicantName,
        Long positionId,
        String positionName,
        AdminEvaluationSummaryResponse summary,
        List<AdminEvaluationItemResponse> evaluations
) {

    public static AdminEvaluationCandidateResponse from(List<InterviewEvaluation> candidateEvaluations) {
        InterviewEvaluation first = candidateEvaluations.get(0);
        InterviewParticipant candidate = first.getCandidateParticipant();
        JobApplication application = first.getJobApplication();
        List<AdminEvaluationItemResponse> items = candidateEvaluations.stream()
                .map(AdminEvaluationItemResponse::from)
                .toList();
        return new AdminEvaluationCandidateResponse(
                candidate.getId(),
                application.getId(),
                application.getApplicantNameSnapshot(),
                application.getJobPosition().getId(),
                application.getJobPositionNameSnapshot(),
                AdminEvaluationSummaryResponse.from(candidateEvaluations),
                items
        );
    }
}
