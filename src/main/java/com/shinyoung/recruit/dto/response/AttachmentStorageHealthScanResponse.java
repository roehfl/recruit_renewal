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
        long storedMissingPhysicalFileCount,
        long deletedPhysicalFileRemainingCount,
        long orphanPhysicalFileCount,
        long invalidStoragePathCount,
        List<AttachmentStorageHealthIssueResponse> issues
) {
}
