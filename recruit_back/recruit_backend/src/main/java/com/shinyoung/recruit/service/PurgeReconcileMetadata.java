package com.shinyoung.recruit.service;

/**
 * Purge reconciliation sweep 감사 metadata(PII-free 집계만, Phase 09e). PURGE_PENDING 잔여 건의 바이너리
 * 삭제 재처리 결과 — coarse index(개별 application 결과 중복 기록 금지, 설계 §5.4 원칙 승계).
 *
 * @param scannedCount     스윕 대상(PURGE_PENDING) 건수
 * @param promotedCount    이번 스윕으로 최종 PURGED 승격된 건수
 * @param stillPendingCount 재처리 후에도 미완(바이너리 삭제 실패 잔존) 건수
 * @param errorCount       saga 자체 예외로 처리 실패한 건수
 */
public record PurgeReconcileMetadata(
        long scannedCount,
        long promotedCount,
        long stillPendingCount,
        long errorCount
) implements AuditMetadata {
}
