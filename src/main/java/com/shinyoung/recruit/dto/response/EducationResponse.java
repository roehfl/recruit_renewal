package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.entity.ApplicationEducationSemesterGrade;
import com.shinyoung.recruit.enumeration.CampusType;
import com.shinyoung.recruit.enumeration.DayNightType;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.GraduationStatus;

import java.time.LocalDate;
import java.util.List;

public record EducationResponse(
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
        List<SemesterGradeResponse> semesterGrades
) {

    public static EducationResponse from(
            ApplicationEducation education,
            List<ApplicationEducationSemesterGrade> semesterGrades
    ) {
        return new EducationResponse(
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
                        .map(SemesterGradeResponse::from)
                        .toList()
        );
    }
}
