package com.shinyoung.recruit.service;

/**
 * StageResult Excel upload 의 낙관적 잠금(@Version) 충돌 attempt 감사 metadata(PII-free).
 * 파일명 원문 금지 — hash + 확장자만(리뷰 2차 #2).
 */
public record UploadConflictMetadata(
        long stageId,
        String sourceFileNameHash,
        String sourceFileExtension,
        long sourceFileSize,
        String contentHash
) implements AuditMetadata {
}
