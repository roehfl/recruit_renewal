package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.EmploymentType;
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
        name = "application_career",
        indexes = {
                @Index(name = "idx_application_career_application", columnList = "job_application_id"),
                @Index(name = "idx_application_career_sort", columnList = "job_application_id,sort_order")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationCareer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id", nullable = false)
    private JobApplication jobApplication;

    @Column(nullable = false)
    private String companyName;

    private String departmentName;

    private String positionTitle;

    @Enumerated(EnumType.STRING)
    private EmploymentType employmentType;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    @Column(nullable = false)
    private Boolean currentlyEmployed;

    @Column(length = 2000)
    private String responsibilities;

    @Column(length = 2000)
    private String resignationReason;

    @Column(nullable = false)
    private Integer sortOrder;

    private ApplicationCareer(
            JobApplication jobApplication,
            String companyName,
            String departmentName,
            String positionTitle,
            EmploymentType employmentType,
            LocalDate startDate,
            LocalDate endDate,
            Boolean currentlyEmployed,
            String responsibilities,
            String resignationReason,
            Integer sortOrder
    ) {
        this.jobApplication = jobApplication;
        this.companyName = companyName;
        this.departmentName = departmentName;
        this.positionTitle = positionTitle;
        this.employmentType = employmentType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.currentlyEmployed = currentlyEmployed;
        this.responsibilities = responsibilities;
        this.resignationReason = resignationReason;
        this.sortOrder = sortOrder;
    }

    public static ApplicationCareer create(
            JobApplication jobApplication,
            String companyName,
            String departmentName,
            String positionTitle,
            EmploymentType employmentType,
            LocalDate startDate,
            LocalDate endDate,
            Boolean currentlyEmployed,
            String responsibilities,
            String resignationReason,
            Integer sortOrder
    ) {
        return new ApplicationCareer(
                jobApplication,
                companyName,
                departmentName,
                positionTitle,
                employmentType,
                startDate,
                endDate,
                currentlyEmployed,
                responsibilities,
                resignationReason,
                sortOrder
        );
    }
}
