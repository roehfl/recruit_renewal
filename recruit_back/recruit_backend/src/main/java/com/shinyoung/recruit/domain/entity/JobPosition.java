package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.EmploymentType;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

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
    private String jobTitle;

    /**
     * 후보 근무지 목록. 개수가 곧 지원자 화면의 분기다 — 0개면 근무지 선택 없음, 1개면 고정, N개면 선택.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "job_position_work_location",
            joinColumns = @JoinColumn(name = "job_position_id")
    )
    @OrderColumn(name = "sort_order")
    private List<JobPositionWorkLocation> workLocations = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EmploymentType employmentType;

    @Column(nullable = false)
    private Integer sortOrder;

    private JobPosition(
            String positionName,
            JobPositionApplicationType applicationType,
            String jobTitle,
            List<JobPositionWorkLocation> workLocations,
            EmploymentType employmentType,
            Integer sortOrder
    ) {
        this.positionName = positionName;
        this.applicationType = defaultApplicationType(applicationType);
        this.jobTitle = jobTitle;
        this.workLocations = workLocations == null ? new ArrayList<>() : new ArrayList<>(workLocations);
        this.employmentType = defaultEmploymentType(employmentType);
        this.sortOrder = sortOrder;
    }

    public static JobPosition create(String positionName, Integer sortOrder) {
        return new JobPosition(positionName, null, null, null, null, sortOrder);
    }

    public static JobPosition create(
            String positionName,
            JobPositionApplicationType applicationType,
            String jobTitle,
            List<JobPositionWorkLocation> workLocations,
            EmploymentType employmentType,
            Integer sortOrder
    ) {
        return new JobPosition(
                positionName,
                applicationType,
                jobTitle,
                workLocations,
                employmentType,
                sortOrder
        );
    }

    void assignJobPosting(JobPosting jobPosting) {
        this.jobPosting = jobPosting;
    }

    /** 후보 근무지 중 해당 코드가 있는지. 없으면 지원 시 선택할 수 없다. */
    public boolean hasWorkLocation(String code) {
        return workLocations.stream().anyMatch(it -> it.getCode().equals(code));
    }

    /** 후보 근무지의 표시명. 후보에 없으면 null. */
    public String findWorkLocationName(String code) {
        return workLocations.stream()
                .filter(it -> it.getCode().equals(code))
                .map(JobPositionWorkLocation::getName)
                .findFirst()
                .orElse(null);
    }

    private static JobPositionApplicationType defaultApplicationType(JobPositionApplicationType applicationType) {
        return applicationType == null ? JobPositionApplicationType.NEW_GRADUATE_OR_EXPERIENCED : applicationType;
    }

    private static EmploymentType defaultEmploymentType(EmploymentType employmentType) {
        return employmentType == null ? EmploymentType.FULL_TIME : employmentType;
    }
}
