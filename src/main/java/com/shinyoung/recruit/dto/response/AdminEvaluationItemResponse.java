package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.InterviewEvaluation;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.enumeration.EvaluationGrade;
import com.shinyoung.recruit.enumeration.EvaluationRecommendation;
import com.shinyoung.recruit.enumeration.EvaluationStatus;

import java.time.LocalDateTime;

/**
 * One interviewer's evaluation row as seen by an administrator. Unlike the interviewer-facing DTOs,
 * the admin view exposes the interviewer identity.
 */
public record AdminEvaluationItemResponse(
        Long evaluationId,
        Long interviewerParticipantId,
        String interviewerName,
        EvaluationStatus status,
        EvaluationGrade grade,
        EvaluationRecommendation recommendation,
        String comment,
        LocalDateTime submittedAt
) {

    public static AdminEvaluationItemResponse from(InterviewEvaluation evaluation) {
        InterviewParticipant interviewer = evaluation.getInterviewerParticipant();
        Employee employee = interviewer.getEmployee();
        return new AdminEvaluationItemResponse(
                evaluation.getId(),
                interviewer.getId(),
                employee != null ? employee.getName() : null,
                evaluation.getStatus(),
                evaluation.getGrade(),
                evaluation.getRecommendation(),
                evaluation.getComment(),
                evaluation.getSubmittedAt()
        );
    }
}
