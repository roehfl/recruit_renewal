package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.CampusType;
import com.shinyoung.recruit.enumeration.DayNightType;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.GraduationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "application_education",
        indexes = {
                @Index(name = "idx_application_education_application", columnList = "job_application_id"),
                @Index(name = "idx_application_education_sort", columnList = "job_application_id,sort_order"),
                @Index(name = "idx_application_education_school", columnList = "school_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationEducation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id", nullable = false)
    private JobApplication jobApplication;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EducationLevel educationLevel;

    @Column(nullable = false)
    private String schoolName;

    private String majorName;

    // 복수/부/세부전공 구분 — CommonCode 그룹 MAJOR_TYPE 코드값(veteranType 선례: String 저장, FK·검증 없음).
    @Column(length = 200)
    private String additionalMajorType;

    // additionalMajorType에 해당하는 전공 명칭(자유텍스트). 파기 시 NULLIFY.
    private String additionalMajorName;

    // 논문명(자유텍스트). 파기 시 NULLIFY.
    private String thesisTitle;

    private LocalDate admissionDate;

    private LocalDate graduationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GraduationStatus graduationStatus;

    @Enumerated(EnumType.STRING)
    private DayNightType dayNightType;

    @Enumerated(EnumType.STRING)
    private CampusType campusType;

    private Boolean transfer;

    private String countryCode;

    /**
     * 선택적 {@link School} master 참조(Phase 08c). 지원자가 자동완성에서 학교를 고른 경우에만 채워지고,
     * 직접입력(미매칭)이면 null 이다. 강한 FK 가 아니라 application-level 참조다(ADR 0004).
     */
    @Column(name = "school_id")
    private Long schoolId;

    // 학력 단위 전체(평균) 평점·만점기준. HIGH_SCHOOL이 아니면 서비스 검증에서 필수, HIGH_SCHOOL은 선택. DB는 nullable.
    private BigDecimal overallGradePoint;

    private BigDecimal overallMaxGradePoint;

    // 전공 전체 평점·만점기준(모든 레벨 선택).
    private BigDecimal overallMajorGradePoint;

    private BigDecimal overallMajorMaxGradePoint;

    @Column(nullable = false)
    private Integer sortOrder;

    private ApplicationEducation(
            JobApplication jobApplication,
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
            BigDecimal overallGradePoint,
            BigDecimal overallMaxGradePoint,
            BigDecimal overallMajorGradePoint,
            BigDecimal overallMajorMaxGradePoint,
            Integer sortOrder
    ) {
        this.jobApplication = jobApplication;
        this.educationLevel = educationLevel;
        this.schoolName = schoolName;
        this.majorName = majorName;
        this.additionalMajorType = additionalMajorType;
        this.additionalMajorName = additionalMajorName;
        this.thesisTitle = thesisTitle;
        this.admissionDate = admissionDate;
        this.graduationDate = graduationDate;
        this.graduationStatus = graduationStatus;
        this.dayNightType = dayNightType;
        this.campusType = campusType;
        this.transfer = transfer;
        this.countryCode = countryCode;
        this.schoolId = schoolId;
        this.overallGradePoint = overallGradePoint;
        this.overallMaxGradePoint = overallMaxGradePoint;
        this.overallMajorGradePoint = overallMajorGradePoint;
        this.overallMajorMaxGradePoint = overallMajorMaxGradePoint;
        this.sortOrder = sortOrder;
    }

    public static ApplicationEducation create(
            JobApplication jobApplication,
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
            Integer sortOrder
    ) {
        return create(
                jobApplication, educationLevel, schoolName, majorName,
                additionalMajorType, additionalMajorName, thesisTitle,
                admissionDate, graduationDate, graduationStatus, dayNightType, campusType,
                transfer, countryCode, null, sortOrder);
    }

    public static ApplicationEducation create(
            JobApplication jobApplication,
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
            Integer sortOrder
    ) {
        return create(
                jobApplication, educationLevel, schoolName, majorName,
                additionalMajorType, additionalMajorName, thesisTitle,
                admissionDate, graduationDate, graduationStatus, dayNightType, campusType,
                transfer, countryCode, schoolId, null, null, null, null, sortOrder);
    }

    public static ApplicationEducation create(
            JobApplication jobApplication,
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
            BigDecimal overallGradePoint,
            BigDecimal overallMaxGradePoint,
            BigDecimal overallMajorGradePoint,
            BigDecimal overallMajorMaxGradePoint,
            Integer sortOrder
    ) {
        return new ApplicationEducation(
                jobApplication,
                educationLevel,
                schoolName,
                majorName,
                additionalMajorType,
                additionalMajorName,
                thesisTitle,
                admissionDate,
                graduationDate,
                graduationStatus,
                dayNightType,
                campusType,
                transfer,
                countryCode,
                schoolId,
                overallGradePoint,
                overallMaxGradePoint,
                overallMajorGradePoint,
                overallMajorMaxGradePoint,
                sortOrder
        );
    }
}
