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

    @Column(name = "require_education", nullable = false)
    private boolean requireEducation;

    @Column(nullable = false)
    private boolean useCareer;

    @Column(name = "require_career", nullable = false)
    private boolean requireCareer;

    @Column(nullable = false)
    private boolean useCertificate;

    @Column(name = "require_certificate", nullable = false)
    private boolean requireCertificate;

    @Column(nullable = false)
    private boolean useLanguage;

    @Column(name = "require_language", nullable = false)
    private boolean requireLanguage;

    @Column(nullable = false)
    private boolean useMilitary;

    @Column(name = "require_military", nullable = false)
    private boolean requireMilitary;

    @Column(nullable = false)
    private boolean useAward;

    @Column(name = "require_award", nullable = false)
    private boolean requireAward;

    @Column(nullable = false)
    private boolean useGapPeriod;

    @Column(name = "require_gap_period", nullable = false)
    private boolean requireGapPeriod;

    private ApplicationFormConfig(
            boolean useEducation,
            boolean requireEducation,
            boolean useCareer,
            boolean requireCareer,
            boolean useCertificate,
            boolean requireCertificate,
            boolean useLanguage,
            boolean requireLanguage,
            boolean useMilitary,
            boolean requireMilitary,
            boolean useAward,
            boolean requireAward,
            boolean useGapPeriod,
            boolean requireGapPeriod
    ) {
        validateRequirement(useEducation, requireEducation, "education");
        validateRequirement(useCareer, requireCareer, "career");
        validateRequirement(useCertificate, requireCertificate, "certificate");
        validateRequirement(useLanguage, requireLanguage, "language");
        validateRequirement(useMilitary, requireMilitary, "military");
        validateRequirement(useAward, requireAward, "award");
        validateRequirement(useGapPeriod, requireGapPeriod, "gapPeriod");
        this.useEducation = useEducation;
        this.requireEducation = requireEducation;
        this.useCareer = useCareer;
        this.requireCareer = requireCareer;
        this.useCertificate = useCertificate;
        this.requireCertificate = requireCertificate;
        this.useLanguage = useLanguage;
        this.requireLanguage = requireLanguage;
        this.useMilitary = useMilitary;
        this.requireMilitary = requireMilitary;
        this.useAward = useAward;
        this.requireAward = requireAward;
        this.useGapPeriod = useGapPeriod;
        this.requireGapPeriod = requireGapPeriod;
    }

    public static ApplicationFormConfig create(boolean useEducation, boolean useCareer, boolean useCertificate, boolean useLanguage, boolean useMilitary, boolean useAward, boolean useGapPeriod) {
        return create(
                useEducation,
                useEducation,
                useCareer,
                useCareer,
                useCertificate,
                false,
                useLanguage,
                false,
                useMilitary,
                useMilitary,
                useAward,
                false,
                useGapPeriod,
                false
        );
    }

    public static ApplicationFormConfig create(
            boolean useEducation,
            boolean requireEducation,
            boolean useCareer,
            boolean requireCareer,
            boolean useCertificate,
            boolean requireCertificate,
            boolean useLanguage,
            boolean requireLanguage,
            boolean useMilitary,
            boolean requireMilitary,
            boolean useAward,
            boolean requireAward,
            boolean useGapPeriod,
            boolean requireGapPeriod
    ) {
        return new ApplicationFormConfig(
                useEducation,
                requireEducation,
                useCareer,
                requireCareer,
                useCertificate,
                requireCertificate,
                useLanguage,
                requireLanguage,
                useMilitary,
                requireMilitary,
                useAward,
                requireAward,
                useGapPeriod,
                requireGapPeriod
        );
    }

    public void update(
            boolean useEducation,
            boolean requireEducation,
            boolean useCareer,
            boolean requireCareer,
            boolean useCertificate,
            boolean requireCertificate,
            boolean useLanguage,
            boolean requireLanguage,
            boolean useMilitary,
            boolean requireMilitary,
            boolean useAward,
            boolean requireAward,
            boolean useGapPeriod,
            boolean requireGapPeriod
    ) {
        validateRequirement(useEducation, requireEducation, "education");
        validateRequirement(useCareer, requireCareer, "career");
        validateRequirement(useCertificate, requireCertificate, "certificate");
        validateRequirement(useLanguage, requireLanguage, "language");
        validateRequirement(useMilitary, requireMilitary, "military");
        validateRequirement(useAward, requireAward, "award");
        validateRequirement(useGapPeriod, requireGapPeriod, "gapPeriod");
        this.useEducation = useEducation;
        this.requireEducation = requireEducation;
        this.useCareer = useCareer;
        this.requireCareer = requireCareer;
        this.useCertificate = useCertificate;
        this.requireCertificate = requireCertificate;
        this.useLanguage = useLanguage;
        this.requireLanguage = requireLanguage;
        this.useMilitary = useMilitary;
        this.requireMilitary = requireMilitary;
        this.useAward = useAward;
        this.requireAward = requireAward;
        this.useGapPeriod = useGapPeriod;
        this.requireGapPeriod = requireGapPeriod;
    }

    void assignJobPosting(JobPosting jobPosting) {
        this.jobPosting = jobPosting;
    }

    private static void validateRequirement(boolean enabled, boolean required, String sectionName) {
        if (!enabled && required) {
            throw new IllegalArgumentException(sectionName + " section cannot be required when disabled.");
        }
    }
}
