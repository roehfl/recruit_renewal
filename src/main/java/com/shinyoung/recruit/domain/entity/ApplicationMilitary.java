package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.MilitaryBranch;
import com.shinyoung.recruit.enumeration.MilitaryRank;
import com.shinyoung.recruit.enumeration.MilitaryServiceType;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(
        name = "application_military",
        indexes = {
                @Index(name = "idx_application_military_application", columnList = "job_application_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationMilitary extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id", nullable = false, unique = true)
    private JobApplication jobApplication;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MilitarySubjectType militarySubjectType;

    @Enumerated(EnumType.STRING)
    private MilitaryServiceType serviceType;

    @Enumerated(EnumType.STRING)
    private MilitaryBranch militaryBranch;

    @Enumerated(EnumType.STRING)
    @Column(name = "military_rank")
    private MilitaryRank rank;

    private LocalDate serviceStartDate;

    private LocalDate serviceEndDate;

    @Column(length = 1000)
    private String exemptionReason;

    private ApplicationMilitary(
            JobApplication jobApplication,
            MilitarySubjectType militarySubjectType,
            MilitaryServiceType serviceType,
            MilitaryBranch militaryBranch,
            MilitaryRank rank,
            LocalDate serviceStartDate,
            LocalDate serviceEndDate,
            String exemptionReason
    ) {
        this.jobApplication = jobApplication;
        this.militarySubjectType = militarySubjectType;
        this.serviceType = serviceType;
        this.militaryBranch = militaryBranch;
        this.rank = rank;
        this.serviceStartDate = serviceStartDate;
        this.serviceEndDate = serviceEndDate;
        this.exemptionReason = exemptionReason;
    }

    public static ApplicationMilitary create(
            JobApplication jobApplication,
            MilitarySubjectType militarySubjectType,
            MilitaryServiceType serviceType,
            MilitaryBranch militaryBranch,
            MilitaryRank rank,
            LocalDate serviceStartDate,
            LocalDate serviceEndDate,
            String exemptionReason
    ) {
        return new ApplicationMilitary(
                jobApplication,
                militarySubjectType,
                serviceType,
                militaryBranch,
                rank,
                serviceStartDate,
                serviceEndDate,
                exemptionReason
        );
    }

    public void update(
            MilitarySubjectType militarySubjectType,
            MilitaryServiceType serviceType,
            MilitaryBranch militaryBranch,
            MilitaryRank rank,
            LocalDate serviceStartDate,
            LocalDate serviceEndDate,
            String exemptionReason
    ) {
        this.militarySubjectType = militarySubjectType;
        this.serviceType = serviceType;
        this.militaryBranch = militaryBranch;
        this.rank = rank;
        this.serviceStartDate = serviceStartDate;
        this.serviceEndDate = serviceEndDate;
        this.exemptionReason = exemptionReason;
    }
}
