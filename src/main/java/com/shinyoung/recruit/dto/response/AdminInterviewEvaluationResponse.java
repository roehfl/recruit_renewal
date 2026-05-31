package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewEvaluation;
import com.shinyoung.recruit.enumeration.InterviewStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Interview-level admin evaluation view: all evaluations of one interview grouped by candidate.
 */
public record AdminInterviewEvaluationResponse(
        Long interviewId,
        String interviewGroupName,
        Long stageId,
        String stageName,
        InterviewStatus interviewStatus,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        List<AdminEvaluationCandidateResponse> candidates
) {

    public static AdminInterviewEvaluationResponse of(Interview interview, List<InterviewEvaluation> evaluations) {
        Map<Long, List<InterviewEvaluation>> byCandidate = new LinkedHashMap<>();
        for (InterviewEvaluation evaluation : evaluations) {
            byCandidate
                    .computeIfAbsent(evaluation.getCandidateParticipant().getId(), key -> new ArrayList<>())
                    .add(evaluation);
        }
        List<AdminEvaluationCandidateResponse> candidates = byCandidate.values().stream()
                .map(AdminEvaluationCandidateResponse::from)
                .toList();
        return new AdminInterviewEvaluationResponse(
                interview.getId(),
                interview.getGroupName(),
                interview.getStage().getId(),
                interview.getStage().getStageName(),
                interview.getStatus(),
                interview.getStartDateTime(),
                interview.getEndDateTime(),
                candidates
        );
    }
}
