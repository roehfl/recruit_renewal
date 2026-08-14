package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.PurgeBatch;
import com.shinyoung.recruit.domain.entity.PurgeJobItem;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.PurgeBatchRepository;
import com.shinyoung.recruit.domain.repository.PurgeJobItemRepository;
import com.shinyoung.recruit.dto.request.PurgeExecuteRequest;
import com.shinyoung.recruit.dto.response.PurgeBatchDetailResponse;
import com.shinyoung.recruit.enumeration.PurgeBatchMode;
import com.shinyoung.recruit.enumeration.PurgeBatchStatus;
import com.shinyoung.recruit.enumeration.PurgeItemStatus;
import com.shinyoung.recruit.exception.InvalidRetentionRequestException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import com.shinyoung.recruit.exception.PurgeBatchNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Purge execute 오케스트레이션(Phase 09d-1, 설계 §5.4 / ADR-0007 안전장치).
 *
 * <p><b>의도적으로 클래스 트랜잭션이 없다</b> — batch lifecycle 과 item 처리(REQUIRES_NEW)가 각각 독립
 * 트랜잭션으로 커밋되는 비원자 구조(설계 §5.4: batch 는 집계 컨테이너, item 이 all-or-nothing 단위).
 *
 * <p>안전장치: ① {@code confirm=true} 명시 필수, ② bulk 는 근거 dry-run batch(`sourceDryRunBatchId`,
 * mode=DRY_RUN) 필수 — 그 batch 의 ELIGIBLE item 만 후보, ③ 단건은 applicationId 지정,
 * ④ 어느 모드든 <b>실행 시 eligibility 재검증</b>(processor) — 재검증 탈락(drift)은 SKIPPED 로 기록.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurgeExecutionService {

    private final PurgeBatchRepository purgeBatchRepository;
    private final PurgeJobItemRepository purgeJobItemRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final PurgeBatchLifecycleService purgeBatchLifecycleService;
    private final PurgeItemProcessor purgeItemProcessor;
    private final AttachmentPurgeSagaService attachmentPurgeSagaService;

    public PurgeBatchDetailResponse execute(PurgeExecuteRequest request, String actor) {
        actor = requireActor(actor);
        validateRequest(request);

        // criteria 확정(검증 실패는 batch 생성 전 400/404 — batch FAILED 는 시작 후 실패 전용).
        List<Long> candidateApplicationIds = resolveCandidates(request);

        PurgeBatch batch = purgeBatchLifecycleService.startExecute(actor, request.sourceDryRunBatchId());

        long purged = 0;
        long pending = 0;
        long skipped = 0;
        long failed = 0;
        long binaryDeleteFailed = 0;
        try {
            for (Long applicationId : candidateApplicationIds) {
                try {
                    PurgeItemProcessor.PurgeItemOutcome outcome =
                            purgeItemProcessor.process(batch.getId(), applicationId, batch.getScanAt());
                    switch (outcome.status()) {
                        case PURGED -> purged++;
                        case PENDING -> {
                            // saga ②③(9d-2): item 커밋 후 물리 삭제 → 소멸 확인 시 최종 PURGED 승격.
                            if (completeBinaryDeletionSafely(batch.getId(), applicationId)) {
                                purged++;
                            } else {
                                pending++;
                                binaryDeleteFailed++;
                            }
                        }
                        case SKIPPED -> skipped++;
                        default -> failed++;
                    }
                } catch (RuntimeException e) {
                    // item 트랜잭션은 rollback 됨 — 실패 증적만 별도 트랜잭션으로 남기고 다음 건 진행.
                    log.error("Purge item failed. batchId={}, applicationId={}", batch.getId(), applicationId, e);
                    purgeItemProcessor.recordFailure(batch.getId(), applicationId);
                    failed++;
                }
            }
        } catch (RuntimeException e) {
            // orchestration 자체 실패 — batch FAILED 확정 후 전파(설계 §5.4: FAILED = 시작/criteria 실패 계열).
            purgeBatchLifecycleService.failExecute(batch.getId(), actor);
            throw e;
        }

        try {
            return purgeBatchLifecycleService.completeExecute(
                    batch.getId(), candidateApplicationIds.size(), purged, pending, skipped, failed,
                    binaryDeleteFailed, actor);
        } catch (RuntimeException e) {
            // complete/audit 실패 시 batch 가 RUNNING 으로 방치되면 안 된다(9d-1 리뷰 Medium 1) —
            // 최소한 FAILED 로 확정(베스트 에포트)하고 전파. item 들은 이미 각자 커밋된 상태(ledger 보존).
            log.error("Purge batch complete failed. batchId={}", batch.getId(), e);
            try {
                purgeBatchLifecycleService.failExecute(batch.getId(), actor);
            } catch (RuntimeException failError) {
                log.error("Purge batch FAILED marking also failed — batch may remain RUNNING. batchId={}",
                        batch.getId(), failError);
            }
            throw e;
        }
    }

    /** saga 자체 예외도 PENDING 유지(승격 금지)로 흡수 — attachment 상태는 reconciliation(9e)이 수습한다. */
    private boolean completeBinaryDeletionSafely(Long batchId, Long applicationId) {
        try {
            return attachmentPurgeSagaService.completeBinaryDeletion(batchId, applicationId);
        } catch (RuntimeException e) {
            log.error("Attachment purge saga failed. batchId={}, applicationId={}", batchId, applicationId, e);
            return false;
        }
    }

    private void validateRequest(PurgeExecuteRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.confirm())) {
            throw new InvalidRetentionRequestException("파기 실행은 confirm=true 명시가 필요합니다.");
        }
        boolean bulk = request.sourceDryRunBatchId() != null;
        boolean single = request.applicationId() != null;
        if (bulk == single) {
            throw new InvalidRetentionRequestException(
                    "sourceDryRunBatchId(일괄) 또는 applicationId(단건) 중 정확히 하나를 지정해야 합니다.");
        }
    }

    private List<Long> resolveCandidates(PurgeExecuteRequest request) {
        if (request.sourceDryRunBatchId() != null) {
            PurgeBatch source = purgeBatchRepository.findById(request.sourceDryRunBatchId())
                    .orElseThrow(() -> new PurgeBatchNotFoundException("근거 dry-run batch를 찾을 수 없습니다."));
            // RUNNING/FAILED dry-run 을 근거로 실행하면 안 된다(9d-1 리뷰 Medium 2) — COMPLETED 만 허용.
            if (source.getMode() != PurgeBatchMode.DRY_RUN
                    || source.getStatus() != PurgeBatchStatus.COMPLETED) {
                throw new InvalidRetentionRequestException("sourceDryRunBatchId는 COMPLETED DRY_RUN batch여야 합니다.");
            }
            return purgeJobItemRepository.findByPurgeBatchIdOrderByIdAsc(source.getId()).stream()
                    .filter(item -> item.getStatus() == PurgeItemStatus.ELIGIBLE)
                    .map(PurgeJobItem::getApplicationId)
                    .toList();
        }
        if (!jobApplicationRepository.existsById(request.applicationId())) {
            throw new JobApplicationNotFoundException("지원서를 찾을 수 없습니다.");
        }
        return List.of(request.applicationId());
    }

    /** 비가역 파기 실행은 관리자 행위 — actor 부재 시 ANONYMOUS 감사 차단(9c 리뷰 Medium 2와 동일 원칙). */
    private String requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new InvalidRetentionRequestException("Retention actor is required.");
        }
        return actor.trim();
    }
}
