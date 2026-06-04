package com.shinyoung.recruit.enumeration;

/**
 * ActivityLog 감사 행위의 결과 분류(Phase 09a, ADR-0006).
 *
 * <p>{@code CONFLICT}(낙관적 락/버전 충돌)는 검색·장애분석 가치를 위해 {@code FAILURE} 와 분리한다.
 * 상태성(STARTED/REQUESTED/COMPLETED)은 결과가 아니라 {@code AuditActionType} 으로 표현한다.
 */
public enum AuditActionResult {
    SUCCESS,
    FAILURE,
    DENIED,
    SKIPPED,
    CONFLICT
}
