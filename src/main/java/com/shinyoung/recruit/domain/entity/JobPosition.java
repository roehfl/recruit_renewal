package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.EmploymentType;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Column(nullable = false)
    private String positionName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JobPositionApplicationType applicationType;

    @Column(length = 100)
    private String jobGroup;

    @Column(length = 100)
    private String jobTitle;

    @Column(length = 100)
    private String workLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EmploymentType employmentType;

    @Column(nullable = false)
    private Integer headcount;

    @Column(nullable = false)
    private Integer sortOrder;

    private JobPosition(
            String positionName,
            JobPositionApplicationType applicationType,
            String jobGroup,
            String jobTitle,
            String workLocation,
            EmploymentType employmentType,
            Integer headcount,
            Integer sortOrder
    ) {
        this.positionName = positionName;
        this.applicationType = defaultApplicationType(applicationType);
        this.jobGroup = jobGroup;
        this.jobTitle = jobTitle;
        this.workLocation = workLocation;
        this.employmentType = defaultEmploymentType(employmentType);
        this.headcount = headcount;
        this.sortOrder = sortOrder;
    }

    public static JobPosition create(String positionName, Integer headcount, Integer sortOrder) {
        return new JobPosition(positionName, null, null, null, null, null, headcount, sortOrder);
    }

    public static JobPosition create(
            String positionName,
            JobPositionApplicationType applicationType,
            String jobGroup,
            String jobTitle,
            String workLocation,
            EmploymentType employmentType,
            Integer headcount,
            Integer sortOrder
    ) {
        return new JobPosition(
                positionName,
                applicationType,
                jobGroup,
                jobTitle,
                workLocation,
                employmentType,
                headcount,
                sortOrder
        );
    }

    void assignJobPosting(JobPosting jobPosting) {
        this.jobPosting = jobPosting;
    }

    private static JobPositionApplicationType defaultApplicationType(JobPositionApplicationType applicationType) {
        return applicationType == null ? JobPositionApplicationType.NEW_GRADUATE_OR_EXPERIENCED : applicationType;
    }

    private static EmploymentType defaultEmploymentType(EmploymentType employmentType) {
        return employmentType == null ? EmploymentType.FULL_TIME : employmentType;
    }
}
