package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.entity.ApplicationEducationSemesterGrade;
import com.shinyoung.recruit.enumeration.CampusType;
import com.shinyoung.recruit.enumeration.DayNightType;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.GraduationStatus;

import java.time.LocalDate;
import java.util.List;

public record AdminEducationResponse(
        Long educationId,
        EducationLevel educationLevel,
        String schoolName,
        String majorName,
        String degreeName,
        LocalDate admissionDate,
        LocalDate graduationDate,
        GraduationStatus graduationStatus,
        DayNightType dayNightType,
        CampusType campusType,
        Boolean transfer,
        String countryCode,
        Integer sortOrder,
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
                education.getDegreeName(),
                education.getAdmissionDate(),
                education.getGraduationDate(),
                education.getGraduationStatus(),
                education.getDayNightType(),
                education.getCampusType(),
                education.getTransfer(),
                education.getCountryCode(),
                education.getSortOrder(),
                semesterGrades.stream()
                        .map(AdminSemesterGradeResponse::from)
                        .toList()
        );
    }
}
