package com.shinyoung.recruit.service;

/**
 * PurgeBatch lifecycle 감사 metadata(PII-free 집계만, Phase 09c). ActivityLog 는 batch 단위
 * coarse index 만 — item 결과 중복 기록 금지(설계 §5.4). {@code policyConflictCount} 는
 * fail-safe POLICY_CONFLICT 관측용(리뷰 3차 #4).
 */
public record PurgeBatchMetadata(
        long purgeBatchId,
        String mode,
        long totalCount,
        long eligibleCount,
        long skippedCount,
        long policyConflictCount
) implements AuditMetadata {
}
