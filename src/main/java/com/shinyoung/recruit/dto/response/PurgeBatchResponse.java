package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.PurgeBatch;
import com.shinyoung.recruit.enumeration.PurgeBatchMode;
import com.shinyoung.recruit.enumeration.PurgeBatchStatus;
import com.shinyoung.recruit.enumeration.PurgeTriggerType;

import java.time.LocalDateTime;

public record PurgeBatchResponse(
        Long id,
        PurgeBatchMode mode,
        PurgeBatchStatus status,
        PurgeTriggerType triggerType,
        LocalDateTime scanAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String requestedBy,
        Long sourceDryRunBatchId,
        long totalCount,
        long eligibleCount,
        long skippedCount,
        long policyConflictCount,
        long purgedCount,
        long pendingCount,
        long failedCount,
        long binaryDeleteFailedCount
) {
    public static PurgeBatchResponse from(PurgeBatch batch) {
        return new PurgeBatchResponse(
                batch.getId(),
                batch.getMode(),
                batch.getStatus(),
                batch.getTriggerType(),
                batch.getScanAt(),
                batch.getStartedAt(),
                batch.getCompletedAt(),
                batch.getRequestedBy(),
                batch.getSourceDryRunBatchId(),
                batch.getTotalCount(),
                batch.getEligibleCount(),
                batch.getSkippedCount(),
                batch.getPolicyConflictCount(),
                batch.getPurgedCount(),
                batch.getPendingCount(),
                batch.getFailedCount(),
                batch.getBinaryDeleteFailedCount()
        );
    }
}
