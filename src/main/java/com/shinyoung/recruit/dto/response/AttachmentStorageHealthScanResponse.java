package com.shinyoung.recruit.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record AttachmentStorageHealthScanResponse(
        boolean dryRun,
        LocalDateTime scannedAt,
        long scannedPhysicalFileCount,
        long managedPhysicalFileCount,
        long ignoredPhysicalFileCount,
        long storedRowCount,
        long deletedRowCount,
        long missingRowCount,
        /** BINARY_DELETE_PENDING/FAILED row 수(09e §6.1 — reconciliation 재처리 대상, issue 아님). */
        long pendingBinaryDeleteRowCount,
        long storedMissingPhysicalFileCount,
        long deletedPhysicalFileRemainingCount,
        long orphanPhysicalFileCount,
        /** PURGED 지원서 경로에 파일 잔존(09e §6.1 — 치명적 불일치). */
        long purgedPhysicalFilePresentCount,
        long invalidStoragePathCount,
        List<AttachmentStorageHealthIssueResponse> issues
) {
}
