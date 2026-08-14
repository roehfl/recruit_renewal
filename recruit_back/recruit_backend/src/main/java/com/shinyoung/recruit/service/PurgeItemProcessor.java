package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.PurgeBatch;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
import com.shinyoung.recruit.domain.repository.ApplicationPiiPurgeRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.PurgeBatchRepository;
import com.shinyoung.recruit.domain.repository.PurgeJobItemRepository;
import com.shinyoung.recruit.domain.repository.RetentionHoldRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.domain.entity.PurgeJobItem;
import com.shinyoung.recruit.enumeration.AuditReasonCode;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;
import com.shinyoung.recruit.enumeration.PurgeItemStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Purge execute 의 application 1건 처리(Phase 09d-1, 설계 §5.4).
 *
 * <p><b>item = 한 트랜잭션(all-or-nothing per application)</b> — {@code REQUIRES_NEW} 로 batch 와 분리해
 * 한 건의 실패가 다른 건을 막지 않는다. execute 는 dry-run 을 믿지 않고 <b>실행 시 eligibility 를 재검증</b>한다.
 *
 * <p>흐름: 재검증 → 부적격이면 SKIPPED(+reasonCode, dry-run↔execute drift 포함) → 관계형 PII tombstone →
 * Applicant ref-count 익명화 → 첨부 바이너리 보유 여부에 따라 PURGE_PENDING(9d-2 saga 대상) 또는 최종 PURGED.
 * <b>바이너리 잔존 시 PURGED 승격 금지</b>("DB PURGED + 파일 잔존" 불허).
 */
@Service
@RequiredArgsConstructor
public class PurgeItemProcessor {

    /** item 처리 결과(상태 + skip 사유). */
    public record PurgeItemOutcome(PurgeItemStatus status, AuditReasonCode reasonCode) {
    }

    private final JobApplicationRepository jobApplicationRepository;
    private final StageRepository stageRepository;
    private final StageResultRepository stageResultRepository;
    private final RetentionHoldRepository retentionHoldRepository;
    private final ApplicationAttachmentRepository applicationAttachmentRepository;
    private final AuditHmac auditHmac;
    private final RetentionPolicyService retentionPolicyService;
    private final RetentionEligibilityService retentionEligibilityService;
    private final ApplicationPiiPurgeService applicationPiiPurgeService;
    private final ApplicationPiiPurgeRepository applicationPiiPurgeRepository;
    private final PurgeBatchRepository purgeBatchRepository;
    private final PurgeJobItemRepository purgeJobItemRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PurgeItemOutcome process(Long batchId, Long applicationId, LocalDateTime scanAt) {
        PurgeBatch batch = purgeBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalStateException("PurgeBatch not found. id=" + batchId));
        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalStateException("JobApplication not found. id=" + applicationId));
        JobPosting jobPosting = application.getJobPosting();
        Long jobPostingId = jobPosting.getId();

        // 실행 시 eligibility 재검증(dry-run 결과 불신뢰). 탈락 = SKIPPED + 사유(drift 기록).
        RetentionEligibilityService.EligibilityDecision decision = retentionEligibilityService.evaluate(
                application,
                jobPosting,
                retentionPolicyService.selectPolicy(jobPostingId, scanAt),
                retentionHoldRepository.existsByApplicationIdAndReleasedAtIsNull(applicationId),
                finalStages(jobPostingId),
                finalStageResult(jobPostingId, applicationId),
                scanAt
        );
        if (!decision.eligible()) {
            purgeJobItemRepository.save(
                    PurgeJobItem.skipped(batch, applicationId, jobPostingId, decision.reasonCode()));
            return new PurgeItemOutcome(PurgeItemStatus.SKIPPED, decision.reasonCode());
        }

        // 관계형 PII tombstone(인벤토리 §3~§7) — 이 트랜잭션 안에서 원자적.
        applicationPiiPurgeService.purgeRelationalPii(applicationId);

        // saga ①(9d-2, 인벤토리 §6): 첨부 metadata PII 제거(원본 파일명 → hash 후 placeholder, 삭제 행위자/사유
        // null) + 바이너리 소멸 미확인 상태(STORED·soft-delete·이전 saga 잔여)는 BINARY_DELETE_PENDING 마킹.
        boolean binaryOutstanding = false;
        for (ApplicationAttachment attachment : applicationAttachmentRepository.findByJobApplicationId(applicationId)) {
            attachment.purgeMetadataPii(
                    auditHmac.hmacHex("FILE_NAME:" + attachment.getOriginalFileName()));
            if (PhysicalFileStatus.BINARY_OUTSTANDING.contains(attachment.getPhysicalFileStatus())) {
                attachment.markBinaryDeletePending();
                binaryOutstanding = true;
            }
        }
        applicationPiiPurgeRepository.purgeAttachmentAuditFields(applicationId);

        // 바이너리 소멸 미확인 시 최종 PURGED 승격 금지("DB PURGED + 파일 잔존" 불허) — saga ②③ 가 확인 후 승격.
        PurgeJobItem item;
        if (binaryOutstanding) {
            application.markPurgePending(batchId);
            item = PurgeJobItem.executePending(batch, applicationId, jobPostingId);
        } else {
            application.markPurged(batchId, LocalDateTime.now(clock));
            item = PurgeJobItem.executePurged(batch, applicationId, jobPostingId);
        }
        purgeJobItemRepository.save(item);

        anonymizeApplicantIfRefZero(application);

        return new PurgeItemOutcome(item.getStatus(), null);
    }

    /**
     * saga ③(9d-2) — 물리 삭제 결과 확정. 성공 첨부는 BINARY_DELETED(storagePath null·binaryDeletedAt),
     * 실패는 BINARY_DELETE_FAILED 유지(9e reconciliation 대상). <b>모든</b> 대상이 소멸 확인됐을 때만
     * JobApplication PURGED(purgedAt) + item PENDING→PURGED 최종 승격.
     *
     * @return 최종 PURGED 승격 여부
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean finalizeBinaryDeletion(
            Long batchId,
            Long applicationId,
            Set<Long> succeededAttachmentIds,
            Map<Long, String> failedAttachmentCodes
    ) {
        List<ApplicationAttachment> targets = applicationAttachmentRepository
                .findByJobApplicationIdAndPhysicalFileStatusIn(applicationId, List.of(
                        PhysicalFileStatus.BINARY_DELETE_PENDING, PhysicalFileStatus.BINARY_DELETE_FAILED));
        LocalDateTime now = LocalDateTime.now(clock);
        // 보수적 판정: 실패가 하나라도 있거나, 성공 확인이 없는 대상이 남으면 승격 금지.
        boolean allCleared = failedAttachmentCodes.isEmpty();
        for (ApplicationAttachment attachment : targets) {
            if (succeededAttachmentIds.contains(attachment.getId())) {
                attachment.markBinaryDeleted(now);
            } else {
                // 실패 코드 미상이면 보수적으로 UNKNOWN(스캔↔확정 사이 대상 변동 등) — 승격 금지.
                attachment.markBinaryDeleteFailed(failedAttachmentCodes.getOrDefault(attachment.getId(), "UNKNOWN"));
                allCleared = false;
            }
        }
        if (allCleared) {
            JobApplication application = jobApplicationRepository.findById(applicationId)
                    .orElseThrow(() -> new IllegalStateException("JobApplication not found. id=" + applicationId));
            application.markPurged(batchId, now);
            // item 부재는 ledger 불변식 위반 — silent skip 금지(적대 검증 반영). 예외 시 본 tx 전체 롤백되어
            // app PURGED 승격도 함께 취소된다(원장-마커 정합 유지).
            purgeJobItemRepository.findByPurgeBatchIdAndApplicationId(batchId, applicationId)
                    .orElseThrow(() -> new IllegalStateException(
                            "PurgeJobItem not found for promotion. batchId=" + batchId
                                    + ", applicationId=" + applicationId))
                    .promoteToPurged();
        }
        return allCleared;
    }

    /** item 트랜잭션 실패 기록 — 비즈니스 rollback 과 무관하게 잔존해야 하므로 별도 REQUIRES_NEW. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long batchId, Long applicationId) {
        PurgeBatch batch = purgeBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalStateException("PurgeBatch not found. id=" + batchId));
        Long jobPostingId = jobApplicationRepository.findById(applicationId)
                .map(application -> application.getJobPosting().getId())
                .orElse(null);
        purgeJobItemRepository.save(PurgeJobItem.executeFailed(batch, applicationId, jobPostingId));
    }

    /**
     * Applicant 공통 PII 는 ref-count(설계 §5.2) — 이 지원자의 <b>모든</b> JobApplication 이 파기 대상
     * (purgeResult 세팅)일 때만 익명화. ciHash 는 {@code "PURGED:"+UUID} sentinel(리뷰 2차 #1).
     */
    private void anonymizeApplicantIfRefZero(JobApplication application) {
        Long applicantId = application.getApplicant().getId();
        // 같은 영속성 컨텍스트라 현재 application 은 동일 인스턴스로 반환되어 marker 가 보인다.
        // id-equals 조건은 컨텍스트 의미론과 무관하게 "현재 건 = 파기 대상"을 보장하는 명시적 안전망.
        List<JobApplication> siblings = applicationPiiPurgeRepository.findByApplicantId(applicantId);
        boolean allTargeted = siblings.stream()
                .allMatch(sibling -> sibling.getPurgeResult() != null
                        || sibling.getId().equals(application.getId()));
        if (allTargeted) {
            application.getApplicant().purgePersonalData("PURGED:" + UUID.randomUUID());
        }
    }

    private List<Stage> finalStages(Long jobPostingId) {
        return stageRepository.findByJobPostingIdOrderByStageOrderAscIdAsc(jobPostingId).stream()
                .filter(Stage::isFinalStage)
                .toList();
    }

    private StageResult finalStageResult(Long jobPostingId, Long applicationId) {
        List<Stage> finalStages = finalStages(jobPostingId);
        if (finalStages.size() != 1) {
            return null;
        }
        return stageResultRepository
                .findByStageIdAndJobApplicationIdIn(finalStages.get(0).getId(), List.of(applicationId)).stream()
                .findFirst()
                .orElse(null);
    }
}
