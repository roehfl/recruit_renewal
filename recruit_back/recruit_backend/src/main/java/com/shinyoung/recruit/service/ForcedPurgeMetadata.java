package com.shinyoung.recruit.service;

/**
 * 강제 파기 감사 metadata(PII-free 집계만, Phase 10). 사람 식별은 {@code applicantRefHash}(HMAC)로만 한다 —
 * 이름·이메일·휴대폰·loginId 는 넣지 않는다.
 */
public record ForcedPurgeMetadata(
        long purgeBatchId,
        String applicantRefHash,
        String reasonCode,
        long totalCount,
        long purgedCount,
        long pendingCount,
        long skippedCount,
        long failedCount
) implements AuditMetadata {
}
