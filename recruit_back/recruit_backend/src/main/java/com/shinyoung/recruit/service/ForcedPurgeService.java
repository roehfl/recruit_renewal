package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.PurgeBatch;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationPiiPurgeRepository;
import com.shinyoung.recruit.domain.repository.RetentionHoldRepository;
import com.shinyoung.recruit.dto.request.ForcedPurgeRequest;
import com.shinyoung.recruit.dto.response.PurgeBatchDetailResponse;
import com.shinyoung.recruit.exception.ApplicantNotFoundException;
import com.shinyoung.recruit.exception.InvalidRetentionRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 강제 파기(정보주체 삭제 요청) 오케스트레이션(Phase 10).
 *
 * <p><b>클래스 트랜잭션이 없다</b> — batch lifecycle 과 item 처리(REQUIRES_NEW)가 각각 독립 커밋되는
 * 비원자 구조로, 기존 {@link PurgeExecutionService} 와 같다.
 *
 * <p>단위는 지원자 1명 전체다. 일부만 지우면 계정에 이름·연락처가 남아 "지체없이 삭제" 약속을 못 지킨다.
 * 보류(hold)가 하나라도 있으면 거부한다 — 법적 보존 의무가 삭제 요구권보다 우선한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForcedPurgeService {

    private final ApplicantRepository applicantRepository;
    private final ApplicationPiiPurgeRepository applicationPiiPurgeRepository;
    private final RetentionHoldRepository retentionHoldRepository;
    private final PurgeBatchLifecycleService purgeBatchLifecycleService;
    private final PurgeItemProcessor purgeItemProcessor;
    private final AttachmentPurgeSagaService attachmentPurgeSagaService;
    private final AuditHmac auditHmac;

    public PurgeBatchDetailResponse forcePurge(ForcedPurgeRequest request, String actor) {
        String resolvedActor = requireActor(actor);
        if (request == null || !Boolean.TRUE.equals(request.confirm())) {
            throw new InvalidRetentionRequestException("강제 파기는 confirm=true 명시가 필요합니다.");
        }
        Applicant applicant = applicantRepository.findById(request.applicantId())
                .orElseThrow(() -> new ApplicantNotFoundException("지원자를 찾을 수 없습니다."));

        List<Long> applicationIds = applicationPiiPurgeRepository.findByApplicantId(applicant.getId()).stream()
                .map(JobApplication::getId)
                .toList();
        // 보류가 하나라도 있으면 batch 를 만들기 전에 거부한다(원장에 흔적을 남기지 않는다).
        if (!retentionHoldRepository.findActiveApplicationIds(applicationIds).isEmpty()) {
            throw new InvalidRetentionRequestException("파기 보류가 걸려 있어 파기할 수 없습니다.");
        }

        PurgeBatch batch = purgeBatchLifecycleService.startForced(resolvedActor);

        long purged = 0;
        long pending = 0;
        long skipped = 0;
        long failed = 0;
        try {
            for (Long applicationId : applicationIds) {
                try {
                    PurgeItemProcessor.PurgeItemOutcome outcome =
                            purgeItemProcessor.process(batch.getId(), applicationId, batch.getScanAt(), true);
                    switch (outcome.status()) {
                        case PURGED -> purged++;
                        case PENDING -> {
                            if (completeBinaryDeletionSafely(batch.getId(), applicationId)) {
                                purged++;
                            } else {
                                pending++;
                            }
                        }
                        case SKIPPED -> skipped++;
                        default -> failed++;
                    }
                } catch (RuntimeException e) {
                    log.error("Forced purge item failed. batchId={}, applicationId={}",
                            batch.getId(), applicationId, e);
                    purgeItemProcessor.recordFailure(batch.getId(), applicationId);
                    failed++;
                }
            }
            // 남은 지원서가 하나도 없을 때만 계정 PII 를 지운다. 엔티티 재조회(findByApplicantId) 대신
            // 집계 쿼리를 쓴다 — OSIV 하에서 item 처리(REQUIRES_NEW)는 별도 EntityManager 로 커밋되므로
            // DB 는 갱신돼도 이 요청 스레드의 1차 캐시는 stale 한 채로 남아 재조회 시 identity map 이
            // 옛 인스턴스를 그대로 돌려준다(엔티티를 다시 읽어서 판단하면 절대 anonymize 가 실행되지 않는다).
            // 집계 쿼리는 identity map 을 거치지 않아 항상 최신값이고, 지원서 0건도 count=0 으로 자연히 포함된다.
            boolean allCleared = applicationPiiPurgeRepository.countUnpurgedByApplicantId(applicant.getId()) == 0;
            if (allCleared) {
                purgeItemProcessor.anonymizeApplicant(applicant.getId());
            }
        } catch (RuntimeException e) {
            purgeBatchLifecycleService.failForced(batch.getId(), resolvedActor);
            throw e;
        }

        try {
            return purgeBatchLifecycleService.completeForced(
                    batch.getId(),
                    auditHmac.applicantRefHash(applicant.getId()),
                    request.reasonCode(),
                    applicationIds.size(), purged, pending, skipped, failed,
                    resolvedActor);
        } catch (RuntimeException e) {
            // complete/audit 실패 시 batch 가 RUNNING 으로 방치되면 안 된다(9d-1 리뷰 Medium 1 과 동일 원칙) —
            // 최소한 FAILED 로 확정(베스트 에포트)하고 전파. item 들은 이미 각자 커밋된 상태(ledger 보존).
            log.error("Forced purge batch complete failed. batchId={}", batch.getId(), e);
            try {
                purgeBatchLifecycleService.failForced(batch.getId(), resolvedActor);
            } catch (RuntimeException failError) {
                log.error("Forced purge batch FAILED marking also failed — batch may remain RUNNING. batchId={}",
                        batch.getId(), failError);
            }
            throw e;
        }
    }

    /** saga 예외는 PENDING 유지로 흡수 — 잔여는 reconcile 이 수습한다(기존 execute 와 동일). */
    private boolean completeBinaryDeletionSafely(Long batchId, Long applicationId) {
        try {
            return attachmentPurgeSagaService.completeBinaryDeletion(batchId, applicationId);
        } catch (RuntimeException e) {
            log.error("Attachment purge saga failed. batchId={}, applicationId={}", batchId, applicationId, e);
            return false;
        }
    }

    /** 비가역 파기는 관리자 행위 — actor 부재 시 ANONYMOUS 감사 차단. */
    private String requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new InvalidRetentionRequestException("Retention actor is required.");
        }
        return actor.trim();
    }
}
