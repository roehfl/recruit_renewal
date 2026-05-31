package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewEvaluation;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.enumeration.EvaluationGrade;
import com.shinyoung.recruit.enumeration.EvaluationRecommendation;
import com.shinyoung.recruit.enumeration.EvaluationStatus;
import com.shinyoung.recruit.enumeration.InterviewStatus;

import java.time.LocalDateTime;

public record InterviewerEvaluationDetailResponse(
        Long evaluationId,
        Long interviewId,
        String interviewGroupName,
        InterviewStatus interviewStatus,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
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

    public static InterviewerEvaluationDetailResponse from(InterviewEvaluation evaluation) {
        Interview interview = evaluation.getInterview();
        JobApplication application = evaluation.getJobApplication();
        return new InterviewerEvaluationDetailResponse(
                evaluation.getId(),
                interview.getId(),
                interview.getGroupName(),
                interview.getStatus(),
                interview.getStartDateTime(),
                interview.getEndDateTime(),
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
