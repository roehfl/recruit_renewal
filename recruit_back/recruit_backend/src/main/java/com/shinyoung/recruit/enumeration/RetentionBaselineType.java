package com.shinyoung.recruit.enumeration;

/**
 * RetentionPolicy 의 retentionAnchorAt 산정 기준(Phase 09c).
 *
 * <p>{@code HIRING_ENDED_AT} 가 기본 — {@code JobPosting.hiringEndedAt}(관리자 수동 확정 anchor).
 * {@code CLOSED_AT} 은 정책이 <b>명시적으로</b> 선택했을 때만 {@code JobPosting.closedAt} 을 쓴다.
 * 암묵 closedAt fallback 은 금지(설계 §5.3) — anchor 부재면 {@code ANCHOR_NOT_FIXED} SKIP.
 */
public enum RetentionBaselineType {
    HIRING_ENDED_AT,
    CLOSED_AT
}
