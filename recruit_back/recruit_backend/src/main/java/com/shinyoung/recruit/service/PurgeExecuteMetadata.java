package com.shinyoung.recruit.service;

/**
 * Purge execute lifecycle 감사 metadata(PII-free 집계만, Phase 09d-1). batch 단위 coarse index —
 * item 결과 중복 기록 금지(설계 §5.4). {@code skippedCount} 에는 dry-run↔execute 재검증 대상차이(drift)가
 * 포함된다(설계 §5.4 — 대상차이는 감사/배치결과에 기록).
 */
public record PurgeExecuteMetadata(
        long purgeBatchId,
        Long sourceDryRunBatchId,
        long totalCount,
        long purgedCount,
        long pendingCount,
        long skippedCount,
        long failedCount,
        long binaryDeleteFailedCount
) implements AuditMetadata {
}
