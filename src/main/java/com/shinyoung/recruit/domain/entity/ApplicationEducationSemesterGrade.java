package com.shinyoung.recruit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(
        name = "application_education_semester_grade",
        indexes = {
                @Index(name = "idx_application_education_grade_education", columnList = "education_id"),
                @Index(name = "idx_application_education_grade_sort", columnList = "education_id,school_year,semester")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationEducationSemesterGrade extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "education_id", nullable = false)
    private ApplicationEducation education;

    @Column(nullable = false)
    private Integer schoolYear;

    @Column(nullable = false)
    private Integer semester;

    private BigDecimal earnedCredits;

    @Column(nullable = false)
    private BigDecimal gradePoint;

    @Column(nullable = false)
    private BigDecimal maxGradePoint;

    private BigDecimal majorGradePoint;

    private BigDecimal majorMaxGradePoint;

    private ApplicationEducationSemesterGrade(
            ApplicationEducation education,
            Integer schoolYear,
            Integer semester,
            BigDecimal earnedCredits,
            BigDecimal gradePoint,
            BigDecimal maxGradePoint,
            BigDecimal majorGradePoint,
            BigDecimal majorMaxGradePoint
    ) {
        this.education = education;
        this.schoolYear = schoolYear;
        this.semester = semester;
        this.earnedCredits = earnedCredits;
        this.gradePoint = gradePoint;
        this.maxGradePoint = maxGradePoint;
        this.majorGradePoint = majorGradePoint;
        this.majorMaxGradePoint = majorMaxGradePoint;
    }

    public static ApplicationEducationSemesterGrade create(
            ApplicationEducation education,
            Integer schoolYear,
            Integer semester,
            BigDecimal earnedCredits,
            BigDecimal gradePoint,
            BigDecimal maxGradePoint,
            BigDecimal majorGradePoint,
            BigDecimal majorMaxGradePoint
    ) {
        return new ApplicationEducationSemesterGrade(
                education,
                schoolYear,
                semester,
                earnedCredits,
                gradePoint,
                maxGradePoint,
                majorGradePoint,
                majorMaxGradePoint
        );
    }
}
