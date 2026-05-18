package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationEducationSemesterGrade;

import java.math.BigDecimal;

public record AdminSemesterGradeResponse(
        Long semesterGradeId,
        Integer schoolYear,
        Integer semester,
        BigDecimal earnedCredits,
        BigDecimal gradePoint,
        BigDecimal maxGradePoint,
        BigDecimal majorGradePoint,
        BigDecimal majorMaxGradePoint
) {

    public static AdminSemesterGradeResponse from(ApplicationEducationSemesterGrade semesterGrade) {
        return new AdminSemesterGradeResponse(
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
