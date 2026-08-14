package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.InterviewEvaluation;
import com.shinyoung.recruit.enumeration.EvaluationGrade;

import java.util.List;

/**
 * Count per {@link EvaluationGrade} value. Always carries all five fields (zero when absent).
 * Fields are ordered from the highest grade to the lowest.
 */
public record GradeDistribution(
        int vg,
        int gPlus,
        int g,
        int gMinus,
        int f
) {

    public static GradeDistribution from(List<InterviewEvaluation> submittedEvaluations) {
        int vg = 0;
        int gPlus = 0;
        int g = 0;
        int gMinus = 0;
        int f = 0;
        for (InterviewEvaluation evaluation : submittedEvaluations) {
            EvaluationGrade grade = evaluation.getGrade();
            if (grade == null) {
                continue;
            }
            switch (grade) {
                case VG -> vg++;
                case G_PLUS -> gPlus++;
                case G -> g++;
                case G_MINUS -> gMinus++;
                case F -> f++;
            }
        }
        return new GradeDistribution(vg, gPlus, g, gMinus, f);
    }
}
