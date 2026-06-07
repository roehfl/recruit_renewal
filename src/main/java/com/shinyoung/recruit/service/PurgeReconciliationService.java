package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.dto.response.PurgeReconcileResponse;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import com.shinyoung.recruit.enumeration.PurgeResult;
import com.shinyoung.recruit.exception.InvalidRetentionRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * PURGE_PENDING 잔여 건의 바이너리 삭제 재처리 sweep(Phase 09e, 설계 §6 reconciliation).
 *
 * <p>9d-2 saga 가 바이너리 삭제에 실패하면 JobApplication 은 PURGE_PENDING, 첨부는 BINARY_DELETE_FAILED 로
 * 남는다. execute 재실행은 ALREADY_PURGED skip 이라 재처리하지 못하므로, <b>본 sweep 이 유일한 재처리 경로</b>다.
 * 각 PURGE_PENDING 건의 원 batchId 로 saga ②③ 를 다시 돌려, 전부 소멸 확인되면 최종 PURGED 로 승격한다.
 *
 * <p>orchestrator 는 무트랜잭션(saga 가 application 별 REQUIRES_NEW). 감사는 coarse 집계만 — 개별 application
 * 결과 중복 기록 금지(설계 §5.4 원칙 승계). committed 변경(per-app saga)은 이미 각자 커밋됐으므로 summary 는
 * REQUIRES_NEW(recordRequiresNew)로 독립 기록한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurgeReconciliationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final AttachmentPurgeSagaService attachmentPurgeSagaService;
    private final ActivityLogService activityLogService;
    private final AuditRequestContextResolver auditRequestContextResolver;
    private final Clock clock;

    public PurgeReconcileResponse reconcile(String actor) {
        String resolvedActor = requireActor(actor);

        List<JobApplication> pendings = jobApplicationRepository.findByPurgeResult(PurgeResult.PURGE_PENDING);
        long scanned = pendings.size();
        long promoted = 0;
        long stillPending = 0;
        long errors = 0;

        for (JobApplication application : pendings) {
            Long batchId = application.getPurgeBatchId();
            if (batchId == null) {
                // PURGE_PENDING 은 항상 batchId 를 갖는다(markPurgePending). null 은 데이터 불일치 —
                // saga 의 item 조회가 어차피 실패(orElseThrow→rollback)하므로 호출 전에 error 로 격리한다(적대 검증 ①-A/⑥).
                log.error("PURGE_PENDING application has null purgeBatchId — skipped. applicationId={}",
                        application.getId());
                errors++;
                continue;
            }
            try {
                boolean completed = attachmentPurgeSagaService.completeBinaryDeletion(batchId, application.getId());
                if (completed) {
                    promoted++;
                } else {
                    stillPending++;
                }
            } catch (RuntimeException e) {
                // 단건 실패가 sweep 전체를 막지 않는다 — 다음 reconcile 에서 재시도.
                log.error("Purge reconcile failed. applicationId={}, batchId={}", application.getId(), batchId, e);
                errors++;
            }
        }

        recordReconcileAudit(resolvedActor, scanned, promoted, stillPending, errors);
        return new PurgeReconcileResponse(
                LocalDateTime.now(clock), scanned, promoted, stillPending, errors);
    }

    private void recordReconcileAudit(
            String actor, long scanned, long promoted, long stillPending, long errors) {
        AuditActorContext context = auditRequestContextResolver.resolve(actor);
        boolean clean = errors == 0 && stillPending == 0;
        activityLogService.recordRequiresNew(AuditEvent.builder()
                .actorType(context.actorType())
                .actorId(context.actorId())
                .actorRoleSnapshot(context.actorRoleSnapshot())
                .actionType(AuditActionType.PURGE_RECONCILE)
                .actionResult(clean ? AuditActionResult.SUCCESS : AuditActionResult.FAILURE)
                .targetType(AuditTargetType.PURGE_BATCH)
                .reasonMessage(clean ? null
                        : "reconcile incomplete: stillPending=" + stillPending + ", errors=" + errors)
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .metadata(new PurgeReconcileMetadata(scanned, promoted, stillPending, errors))
                .build());
    }

    private String requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new InvalidRetentionRequestException("Retention actor is required.");
        }
        return actor.trim();
    }
}
