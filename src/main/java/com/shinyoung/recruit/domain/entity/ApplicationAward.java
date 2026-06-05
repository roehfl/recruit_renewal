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

import java.time.LocalDate;

@Entity
@Table(
        name = "application_award",
        indexes = {
                @Index(name = "idx_application_award_application", columnList = "job_application_id"),
                @Index(name = "idx_application_award_sort", columnList = "job_application_id,sort_order")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationAward extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id", nullable = false)
    private JobApplication jobApplication;

    @Column(nullable = false)
    private String awardName;

    @Column(nullable = false)
    private String awardingOrganization;

    // 파기(ALTER_NULLABLE+NULLIFY, PII 인벤토리 §5) 대상이라 nullable — 입력 필수는 request 검증이 보장한다(9d-1).
    private LocalDate awardDate;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private Integer sortOrder;

    private ApplicationAward(
            JobApplication jobApplication,
            String awardName,
            String awardingOrganization,
            LocalDate awardDate,
            String description,
            Integer sortOrder
    ) {
        this.jobApplication = jobApplication;
        this.awardName = awardName;
        this.awardingOrganization = awardingOrganization;
        this.awardDate = awardDate;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    public static ApplicationAward create(
            JobApplication jobApplication,
            String awardName,
            String awardingOrganization,
            LocalDate awardDate,
            String description,
            Integer sortOrder
    ) {
        return new ApplicationAward(
                jobApplication,
                awardName,
                awardingOrganization,
                awardDate,
                description,
                sortOrder
        );
    }
}
