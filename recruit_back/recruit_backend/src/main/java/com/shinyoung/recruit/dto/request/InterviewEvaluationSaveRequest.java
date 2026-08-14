package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.domain.entity.InterviewEvaluation;
import com.shinyoung.recruit.enumeration.EvaluationGrade;
import com.shinyoung.recruit.enumeration.EvaluationRecommendation;
import jakarta.validation.constraints.Size;

public record InterviewEvaluationSaveRequest(
        EvaluationGrade grade,

        EvaluationRecommendation recommendation,

        @Size(max = InterviewEvaluation.COMMENT_MAX_LENGTH, message = "comment must be at most 2000 characters.")
        String comment
) {
}
