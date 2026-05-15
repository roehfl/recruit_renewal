package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.GapType;
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
        name = "application_gap_period",
        indexes = {
                @Index(name = "idx_application_gap_period_application", columnList = "job_application_id"),
                @Index(name = "idx_application_gap_period_sort", columnList = "job_application_id,sort_order"),
                @Index(name = "idx_application_gap_period_start", columnList = "job_application_id,start_date")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationGapPeriod extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id", nullable = false)
    private JobApplication jobApplication;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GapType gapType;

    @Column(nullable = false)
    private String reason;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private Integer sortOrder;

    private ApplicationGapPeriod(
            JobApplication jobApplication,
            LocalDate startDate,
            LocalDate endDate,
            GapType gapType,
            String reason,
            String description,
            Integer sortOrder
    ) {
        this.jobApplication = jobApplication;
        this.startDate = startDate;
        this.endDate = endDate;
        this.gapType = gapType;
        this.reason = reason;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    public static ApplicationGapPeriod create(
            JobApplication jobApplication,
            LocalDate startDate,
            LocalDate endDate,
            GapType gapType,
            String reason,
            String description,
            Integer sortOrder
    ) {
        return new ApplicationGapPeriod(
                jobApplication,
                startDate,
                endDate,
                gapType,
                reason,
                description,
                sortOrder
        );
    }
}
