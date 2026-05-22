package com.shinyoung.recruit.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationFormConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false, unique = true)
    private JobPosting jobPosting;

    @Column(nullable = false)
    private boolean useEducation;

    @Column(nullable = false)
    private boolean useCareer;

    @Column(nullable = false)
    private boolean useCertificate;

    @Column(nullable = false)
    private boolean useLanguage;

    @Column(nullable = false)
    private boolean useMilitary;

    @Column(nullable = false)
    private boolean useAward;

    @Column(nullable = false)
    private boolean useGapPeriod;

    private ApplicationFormConfig(boolean useEducation, boolean useCareer, boolean useCertificate, boolean useLanguage, boolean useMilitary, boolean useAward, boolean useGapPeriod) {
        this.useEducation = useEducation;
        this.useCareer = useCareer;
        this.useCertificate = useCertificate;
        this.useLanguage = useLanguage;
        this.useMilitary = useMilitary;
        this.useAward = useAward;
        this.useGapPeriod = useGapPeriod;
    }

    public static ApplicationFormConfig create(boolean useEducation, boolean useCareer, boolean useCertificate, boolean useLanguage, boolean useMilitary, boolean useAward, boolean useGapPeriod) {
        return new ApplicationFormConfig(useEducation, useCareer, useCertificate, useLanguage, useMilitary, useAward, useGapPeriod);
    }

    public void update(boolean useEducation, boolean useCareer, boolean useCertificate, boolean useLanguage, boolean useMilitary, boolean useAward, boolean useGapPeriod) {
        this.useEducation = useEducation;
        this.useCareer = useCareer;
        this.useCertificate = useCertificate;
        this.useLanguage = useLanguage;
        this.useMilitary = useMilitary;
        this.useAward = useAward;
        this.useGapPeriod = useGapPeriod;
    }

    void assignJobPosting(JobPosting jobPosting) {
        this.jobPosting = jobPosting;
    }
}
