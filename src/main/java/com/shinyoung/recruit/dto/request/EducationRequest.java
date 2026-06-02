package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.CampusType;
import com.shinyoung.recruit.enumeration.DayNightType;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.GraduationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record EducationRequest(
        @NotNull(message = "Education level is required.")
        EducationLevel educationLevel,

        @NotBlank(message = "School name is required.")
        String schoolName,

        String majorName,

        String degreeName,

        LocalDate admissionDate,

        LocalDate graduationDate,

        @NotNull(message = "Graduation status is required.")
        GraduationStatus graduationStatus,

        DayNightType dayNightType,

        CampusType campusType,

        Boolean transfer,

        String countryCode,

        @NotNull(message = "Sort order is required.")
        @Min(value = 0, message = "Sort order must be greater than or equal to 0.")
        Integer sortOrder,

        List<@Valid SemesterGradeRequest> semesterGrades,

        /** 선택적 School master 참조(Phase 08c). 자동완성 선택 시에만 값, 직접입력이면 null. */
        Long schoolId
) {

    /** schoolId 없이 호출하던 기존 코드 호환용(Phase 08c 이전). */
    public EducationRequest(
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
            List<SemesterGradeRequest> semesterGrades
    ) {
        this(educationLevel, schoolName, majorName, degreeName, admissionDate, graduationDate,
                graduationStatus, dayNightType, campusType, transfer, countryCode, sortOrder,
                semesterGrades, null);
    }
}
