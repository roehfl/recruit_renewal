package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationEducationSemesterGrade;

import java.math.BigDecimal;

public record SemesterGradeResponse(
        Long semesterGradeId,
        Integer schoolYear,
        Integer semester,
        BigDecimal earnedCredits,
        BigDecimal gradePoint,
        BigDecimal maxGradePoint,
        BigDecimal majorGradePoint,
        BigDecimal majorMaxGradePoint
) {

    public static SemesterGradeResponse from(ApplicationEducationSemesterGrade semesterGrade) {
        return new SemesterGradeResponse(
                semesterGrade.getId(),
                semesterGrade.getSchoolYear(),
                semesterGrade.getSemester(),
                semesterGrade.getEarnedCredits(),
                semesterGrade.getGradePoint(),
                semesterGrade.getMaxGradePoint(),
                semesterGrade.getMajorGradePoint(),
                semesterGrade.getMajorMaxGradePoint()
        );
    }
}
