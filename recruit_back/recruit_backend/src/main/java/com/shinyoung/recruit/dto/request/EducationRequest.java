package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.CampusType;
import com.shinyoung.recruit.enumeration.DayNightType;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.GraduationStatus;
import com.shinyoung.recruit.enumeration.SchoolSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record EducationRequest(
        @NotNull(message = "Education level is required.")
        EducationLevel educationLevel,

        @NotBlank(message = "School name is required.")
        String schoolName,

        String majorName,

        @Size(max = 100, message = "Additional major type must be 100 characters or less.")
        String additionalMajorType,

        String additionalMajorName,

        String thesisTitle,

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

        /** 외부 학교 검색이 준 학교코드. 자동완성 선택 시에만 값, 직접입력이면 null. */
        String schoolCode,

        /** {@code schoolCode} 의 출처. 코드와 함께 채워진다. */
        SchoolSource schoolSource,

        @DecimalMin(value = "0.0", message = "Overall grade point must be greater than or equal to 0.")
        BigDecimal overallGradePoint,

        @DecimalMin(value = "0.0", inclusive = false, message = "Overall max grade point must be greater than 0.")
        BigDecimal overallMaxGradePoint,

        @DecimalMin(value = "0.0", message = "Overall major grade point must be greater than or equal to 0.")
        BigDecimal overallMajorGradePoint,

        @DecimalMin(value = "0.0", inclusive = false, message = "Overall major max grade point must be greater than 0.")
        BigDecimal overallMajorMaxGradePoint
) {

    /** 학교코드·overall 없이 호출하던 기존 코드 호환용(Phase 08c 이전). */
    public EducationRequest(
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
            Integer sortOrder,
            List<SemesterGradeRequest> semesterGrades
    ) {
        this(educationLevel, schoolName, majorName, additionalMajorType, additionalMajorName, thesisTitle,
                admissionDate, graduationDate, graduationStatus, dayNightType, campusType, transfer, countryCode,
                sortOrder, semesterGrades, null, null);
    }

    /** 학교코드 포함, overall 없이 호출하던 기존 코드 호환용. */
    public EducationRequest(
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
            Integer sortOrder,
            List<SemesterGradeRequest> semesterGrades,
            String schoolCode,
            SchoolSource schoolSource
    ) {
        this(educationLevel, schoolName, majorName, additionalMajorType, additionalMajorName, thesisTitle,
                admissionDate, graduationDate, graduationStatus, dayNightType, campusType, transfer, countryCode,
                sortOrder, semesterGrades, schoolCode, schoolSource, null, null, null, null);
    }
}
