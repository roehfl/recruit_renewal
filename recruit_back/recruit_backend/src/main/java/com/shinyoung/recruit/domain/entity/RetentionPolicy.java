package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.RetentionBaselineType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 보존 정책(Phase 09c, 설계 §5.3). 전역 기본({@code jobPostingId = null}) + 공고별 override.
 * 법정 일수는 하드코딩하지 않고 {@code retentionPeriodDays} 로 설정한다.
 *
 * <p>선택 규칙(9c 확정): override 우선 → global, effective window 는 <b>scanAt 기준</b> 평가,
 * 같은 scope 의 enabled 정책 overlap 금지(서비스 검증), 적용 정책 부재 = {@code POLICY_NOT_FOUND},
 * active 후보 2개 이상 = fail-safe {@code POLICY_CONFLICT}.
 */
@Entity
@Getter
@Table(
        name = "retention_policy",
        indexes = {
                @Index(name = "idx_retention_policy_job_posting", columnList = "job_posting_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RetentionPolicy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** null = 전역 기본 정책, 값 있으면 해당 공고 override. */
    @Column(name = "job_posting_id")
    private Long jobPostingId;

    @Column(name = "retention_period_days", nullable = false)
    private int retentionPeriodDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "baseline_type", nullable = false, length = 30)
    private RetentionBaselineType baselineType;

    @Column(nullable = false)
    private boolean enabled;

    /** null = 무기한 시작(과거 전체). scanAt 기준 평가. */
    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    /** null = 무기한 종료. scanAt 기준 평가. */
    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    private RetentionPolicy(
            Long jobPostingId,
            int retentionPeriodDays,
            RetentionBaselineType baselineType,
            boolean enabled,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveTo
    ) {
        this.jobPostingId = jobPostingId;
        this.retentionPeriodDays = retentionPeriodDays;
        this.baselineType = baselineType;
        this.enabled = enabled;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    public static RetentionPolicy create(
            Long jobPostingId,
            int retentionPeriodDays,
            RetentionBaselineType baselineType,
            boolean enabled,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveTo
    ) {
        return new RetentionPolicy(jobPostingId, retentionPeriodDays, baselineType, enabled, effectiveFrom, effectiveTo);
    }

    public void update(
            int retentionPeriodDays,
            RetentionBaselineType baselineType,
            boolean enabled,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveTo
    ) {
        this.retentionPeriodDays = retentionPeriodDays;
        this.baselineType = baselineType;
        this.enabled = enabled;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    /** scanAt 시점에 이 정책이 유효한지(enabled + effective window 포함). */
    public boolean isEffectiveAt(LocalDateTime scanAt) {
        if (!enabled) {
            return false;
        }
        if (effectiveFrom != null && scanAt.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveTo == null || !scanAt.isAfter(effectiveTo);
    }

    /** 다른 정책과 effective window 가 겹치는지(overlap 금지 검증용 — null 은 개방 구간으로 취급). */
    public boolean overlapsWith(LocalDateTime otherFrom, LocalDateTime otherTo) {
        boolean thisEndsBeforeOtherStarts =
                effectiveTo != null && otherFrom != null && effectiveTo.isBefore(otherFrom);
        boolean otherEndsBeforeThisStarts =
                otherTo != null && effectiveFrom != null && otherTo.isBefore(effectiveFrom);
        return !thisEndsBeforeOtherStarts && !otherEndsBeforeThisStarts;
    }
}
