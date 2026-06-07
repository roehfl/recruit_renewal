package com.shinyoung.recruit.dto.response;

import java.time.LocalDateTime;

/**
 * Purge reconciliation sweep 결과(Phase 09e). PURGE_PENDING 잔여 건의 바이너리 삭제 재처리 집계 —
 * PII 미포함(applicationId 등 개별 식별자 미노출, coarse 집계만).
 */
public record PurgeReconcileResponse(
        LocalDateTime reconciledAt,
        long scannedCount,
        long promotedCount,
        long stillPendingCount,
        long errorCount
) {
}
