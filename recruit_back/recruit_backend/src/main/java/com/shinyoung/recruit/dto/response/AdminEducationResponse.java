package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.entity.ApplicationEducationSemesterGrade;
import com.shinyoung.recruit.enumeration.CampusType;
import com.shinyoung.recruit.enumeration.DayNightType;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.GraduationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AdminEducationResponse(
        Long educationId,
        EducationLevel educationLevel,
        String schoolName,
        String majorName,
        String additionalMajorType,
        String additionalMajorName,
        String thesisTitle,
        LocalDate admissionDate,
        LocalDate graduationDate,
        GraduationStatus graduationStatus,
        DayNightType dayNightType,
        CampusType campusType,
        Boolean transfer,
        String countryCode,
        Long schoolId,
        Integer sortOrder,
        BigDecimal overallGradePoint,
        BigDecimal overallMaxGradePoint,
        BigDecimal overallMajorGradePoint,
        BigDecimal overallMajorMaxGradePoint,
        List<AdminSemesterGradeResponse> semesterGrades
) {

    public static AdminEducationResponse from(
            ApplicationEducation education,
            List<ApplicationEducationSemesterGrade> semesterGrades
    ) {
        return new AdminEducationResponse(
                education.getId(),
                education.getEducationLevel(),
                education.getSchoolName(),
                education.getMajorName(),
                education.getAdditionalMajorType(),
                education.getAdditionalMajorName(),
                education.getThesisTitle(),
                education.getAdmissionDate(),
                education.getGraduationDate(),
                education.getGraduationStatus(),
                education.getDayNightType(),
                education.getCampusType(),
                education.getTransfer(),
                education.getCountryCode(),
                education.getSchoolId(),
                education.getSortOrder(),
                education.getOverallGradePoint(),
                education.getOverallMaxGradePoint(),
                education.getOverallMajorGradePoint(),
                education.getOverallMajorMaxGradePoint(),
                semesterGrades.stream()
                        .map(AdminSemesterGradeResponse::from)
                        .toList()
        );
    }
}
