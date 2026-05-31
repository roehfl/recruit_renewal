package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.InterviewEvaluation;

import java.util.List;

/**
 * Per-candidate evaluation summary. Counts and distributions are computed from SUBMITTED evaluations only;
 * DRAFT evaluations contribute to {@code totalEvaluatorCount} but not to the distributions.
 */
public record AdminEvaluationSummaryResponse(
        int submittedCount,
        int totalEvaluatorCount,
        GradeDistribution gradeDistribution,
        RecommendationDistribution recommendationDistribution
) {

    public static AdminEvaluationSummaryResponse from(List<InterviewEvaluation> evaluations) {
        List<InterviewEvaluation> submitted = evaluations.stream()
                .filter(InterviewEvaluation::isSubmitted)
                .toList();
        return new AdminEvaluationSummaryResponse(
                submitted.size(),
                evaluations.size(),
                GradeDistribution.from(submitted),
                RecommendationDistribution.from(submitted)
        );
    }
}
