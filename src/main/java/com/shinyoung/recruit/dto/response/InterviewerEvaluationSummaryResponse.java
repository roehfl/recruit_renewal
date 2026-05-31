package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.InterviewEvaluation;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.enumeration.EvaluationGrade;
import com.shinyoung.recruit.enumeration.EvaluationRecommendation;
import com.shinyoung.recruit.enumeration.EvaluationStatus;

import java.time.LocalDateTime;

public record InterviewerEvaluationSummaryResponse(
        Long evaluationId,
        Long candidateParticipantId,
        Long applicationId,
        String candidateName,
        Long positionId,
        String positionName,
        EvaluationStatus status,
        EvaluationGrade grade,
        EvaluationRecommendation recommendation,
        String comment,
        LocalDateTime submittedAt
) {

    public static InterviewerEvaluationSummaryResponse from(InterviewEvaluation evaluation) {
        JobApplication application = evaluation.getJobApplication();
        return new InterviewerEvaluationSummaryResponse(
                evaluation.getId(),
                evaluation.getCandidateParticipant().getId(),
                application.getId(),
                application.getApplicantNameSnapshot(),
                application.getJobPosition().getId(),
                application.getJobPositionNameSnapshot(),
                evaluation.getStatus(),
                evaluation.getGrade(),
                evaluation.getRecommendation(),
                evaluation.getComment(),
                evaluation.getSubmittedAt()
        );
    }
}
