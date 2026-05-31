package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.InterviewEvaluation;
import com.shinyoung.recruit.enumeration.EvaluationRecommendation;

import java.util.List;

/**
 * Count per {@link EvaluationRecommendation} value. Always carries all five fields (zero when absent).
 */
public record RecommendationDistribution(
        int strongYes,
        int yes,
        int neutral,
        int no,
        int strongNo
) {

    public static RecommendationDistribution from(List<InterviewEvaluation> submittedEvaluations) {
        int strongYes = 0;
        int yes = 0;
        int neutral = 0;
        int no = 0;
        int strongNo = 0;
        for (InterviewEvaluation evaluation : submittedEvaluations) {
            EvaluationRecommendation recommendation = evaluation.getRecommendation();
            if (recommendation == null) {
                continue;
            }
            switch (recommendation) {
                case STRONG_YES -> strongYes++;
                case YES -> yes++;
                case NEUTRAL -> neutral++;
                case NO -> no++;
                case STRONG_NO -> strongNo++;
            }
        }
        return new RecommendationDistribution(strongYes, yes, neutral, no, strongNo);
    }
}
