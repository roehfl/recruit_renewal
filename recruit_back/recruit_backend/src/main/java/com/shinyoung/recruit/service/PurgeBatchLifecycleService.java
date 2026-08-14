package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.PurgeBatch;
import com.shinyoung.recruit.domain.repository.PurgeBatchRepository;
import com.shinyoung.recruit.domain.repository.PurgeJobItemRepository;
import com.shinyoung.recruit.dto.response.PurgeBatchDetailResponse;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import com.shinyoung.recruit.enumeration.PurgeBatchStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Purge execute batch 의 lifecycle 전이(Phase 09d-1). batch 는 비원자 집계 컨테이너(설계 §5.4) —
 * item 트랜잭션(REQUIRES_NEW)과 분리해 시작/완료/실패를 각각 독립 트랜잭션으로 커밋한다.
 * ActivityLog 는 batch 단위 coarse index 만(완료/실패 + 집계 metadata, item 중복 기록 금지).
 */
@Service
@RequiredArgsConstructor
public class PurgeBatchLifecycleService {

    private final PurgeBatchRepository purgeBatchRepository;
    private final PurgeJobItemRepository purgeJobItemRepository;
    private final ActivityLogService activityLogService;
    private final AuditRequestContextResolver auditRequestContextResolver;
    private final Clock clock;

    /** execute batch 시작 — item 처리 전에 RUNNING 으로 커밋해 둔다(원장 선기록). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PurgeBatch startExecute(String actor, Long sourceDryRunBatchId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return purgeBatchRepository.save(PurgeBatch.startExecute(now, now, actor, sourceDryRunBatchId));
    }

    /** execute 집계 완료(+coarse 감사, in-tx). item 실패/바이너리 삭제 실패가 있으면 PARTIAL_FAILED + FAILURE 감사. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PurgeBatchDetailResponse completeExecute(
            Long batchId,
            long totalCount,
            long purgedCount,
            long pendingCount,
            long skippedCount,
            long failedCount,
            long binaryDeleteFailedCount,
            String actor
    ) {
        PurgeBatch batch = findBatch(batchId);
        batch.completeExecute(totalCount, purgedCount, pendingCount, skippedCount, failedCount,
                binaryDeleteFailedCount, LocalDateTime.now(clock));

        AuditActorContext context = auditRequestContextResolver.resolve(actor);
        activityLogService.recordInCurrentTx(AuditEvent.builder()
                .actorType(context.actorType())
                .actorId(context.actorId())
                .actorRoleSnapshot(context.actorRoleSnapshot())
                .actionType(AuditActionType.PURGE_EXECUTE)
                .actionResult(batch.getStatus() == PurgeBatchStatus.PARTIAL_FAILED
                        ? AuditActionResult.FAILURE
                        : AuditActionResult.SUCCESS)
                .targetType(AuditTargetType.PURGE_BATCH)
                .targetId(String.valueOf(batchId))
                .reasonMessage(batch.getStatus() == PurgeBatchStatus.PARTIAL_FAILED
                        ? "partial failure: failedCount=" + failedCount
                                + ", binaryDeleteFailedCount=" + binaryDeleteFailedCount
                        : null)
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .metadata(new PurgeExecuteMetadata(
                        batchId, batch.getSourceDryRunBatchId(), totalCount,
                        purgedCount, pendingCount, skippedCount, failedCount, binaryDeleteFailedCount))
                .build());

        return PurgeBatchDetailResponse.of(batch, purgeJobItemRepository.findByPurgeBatchIdOrderByIdAsc(batchId));
    }

    /** orchestration 자체 실패(시작 후) — batch FAILED 확정 + 실패 증적(REQUIRES_NEW 로 잔존). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failExecute(Long batchId, String actor) {
        PurgeBatch batch = findBatch(batchId);
        batch.fail(LocalDateTime.now(clock));

        AuditActorContext context = auditRequestContextResolver.resolve(actor);
        activityLogService.recordInCurrentTx(AuditEvent.builder()
                .actorType(context.actorType())
                .actorId(context.actorId())
                .actorRoleSnapshot(context.actorRoleSnapshot())
                .actionType(AuditActionType.PURGE_EXECUTE)
                .actionResult(AuditActionResult.FAILURE)
                .targetType(AuditTargetType.PURGE_BATCH)
                .targetId(String.valueOf(batchId))
                .reasonMessage("execute batch failed")
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());
    }

    private PurgeBatch findBatch(Long batchId) {
        return purgeBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalStateException("PurgeBatch not found. id=" + batchId));
    }
}
