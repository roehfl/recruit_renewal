package com.shinyoung.recruit.service;

/**
 * StageResult 수동 변경(정정) 감사 metadata(PII-free 집계만). 정정 전후값은 ActivityLog 가 아니라
 * 기존 {@code StageResultCorrectionHistory} 가 보유한다 — oldValue/newValue 저장 금지(ADR-0006).
 */
public record StageResultChangeMetadata(
        long stageId,
        long changedCount
) implements AuditMetadata {
}
