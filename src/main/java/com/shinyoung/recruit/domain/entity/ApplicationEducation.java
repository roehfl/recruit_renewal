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

    private String degreeName;

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

    @Column(nullable = false)
    private Integer sortOrder;

    private ApplicationEducation(
            JobApplication jobApplication,
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
            Long schoolId,
            Integer sortOrder
    ) {
        this.jobApplication = jobApplication;
        this.educationLevel = educationLevel;
        this.schoolName = schoolName;
        this.majorName = majorName;
        this.degreeName = degreeName;
        this.admissionDate = admissionDate;
        this.graduationDate = graduationDate;
        this.graduationStatus = graduationStatus;
        this.dayNightType = dayNightType;
        this.campusType = campusType;
        this.transfer = transfer;
        this.countryCode = countryCode;
        this.schoolId = schoolId;
        this.sortOrder = sortOrder;
    }

    public static ApplicationEducation create(
            JobApplication jobApplication,
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
            Integer sortOrder
    ) {
        return create(
                jobApplication, educationLevel, schoolName, majorName, degreeName,
                admissionDate, graduationDate, graduationStatus, dayNightType, campusType,
                transfer, countryCode, null, sortOrder);
    }

    public static ApplicationEducation create(
            JobApplication jobApplication,
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
            Long schoolId,
            Integer sortOrder
    ) {
        return new ApplicationEducation(
                jobApplication,
                educationLevel,
                schoolName,
                majorName,
                degreeName,
                admissionDate,
                graduationDate,
                graduationStatus,
                dayNightType,
                campusType,
                transfer,
                countryCode,
                schoolId,
                sortOrder
        );
    }
}
