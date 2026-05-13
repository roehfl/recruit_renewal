package com.shinyoung.recruit.domain.entity;

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

    @Column(nullable = false)
    private Integer headcount;

    @Column(nullable = false)
    private Integer sortOrder;

    private JobPosition(String positionName, Integer headcount, Integer sortOrder) {
        this.positionName = positionName;
        this.headcount = headcount;
        this.sortOrder = sortOrder;
    }

    public static JobPosition create(String positionName, Integer headcount, Integer sortOrder) {
        return new JobPosition(positionName, headcount, sortOrder);
    }

    void assignJobPosting(JobPosting jobPosting) {
        this.jobPosting = jobPosting;
    }
}
