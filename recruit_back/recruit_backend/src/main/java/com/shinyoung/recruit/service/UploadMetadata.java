package com.shinyoung.recruit.service;

/**
 * StageResult Excel upload commit 감사 metadata(PII-free 집계).
 *
 * <p>업로드 원본 파일명은 PII 가능(예: "홍길동_1차면접결과.xlsx") → 원문 저장 금지(리뷰 2차 #2).
 * {@code sourceFileNameHash}(SHA-256) + {@code sourceFileExtension} 만 남긴다.
 */
public record UploadMetadata(
        long stageId,
        String outcome,
        long rowCount,
        long changedCount,
        long unchangedCount,
        long errorCount,
        long staleCount,
        String sourceFileNameHash,
        String sourceFileExtension,
        long sourceFileSize,
        String contentHash
) implements AuditMetadata {
}
