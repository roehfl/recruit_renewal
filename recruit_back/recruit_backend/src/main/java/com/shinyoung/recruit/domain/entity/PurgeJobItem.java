package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.AuditReasonCode;
import com.shinyoung.recruit.enumeration.PurgeItemStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * PurgeBatch 의 application 별 결과(Phase 09c, 설계 §5.4). delete 금지 mutable ledger.
 *
 * <p>dry-run(09c): {@code ELIGIBLE} 또는 {@code SKIPPED}+reasonCode 로 산정 결과만 기록(무변경).
 * execute(09d-1): {@code PENDING→FAILED(retry)→PURGED} 상태 전이.
 */
@Entity
@Getter
@Table(
        name = "purge_job_item",
        indexes = {
                @Index(name = "idx_purge_job_item_batch", columnList = "purge_batch_id"),
                @Index(name = "idx_purge_job_item_application", columnList = "application_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurgeJobItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purge_batch_id", nullable = false)
    private PurgeBatch purgeBatch;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    /** 검색용 denormalized key(파기 후 join 회피 — ActivityLog 와 동일 원칙). */
    @Column(name = "job_posting_id")
    private Long jobPostingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PurgeItemStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 40)
    private AuditReasonCode reasonCode;

    private PurgeJobItem(
            PurgeBatch purgeBatch,
            Long applicationId,
            Long jobPostingId,
            PurgeItemStatus status,
            AuditReasonCode reasonCode
    ) {
        this.purgeBatch = purgeBatch;
        this.applicationId = applicationId;
        this.jobPostingId = jobPostingId;
        this.status = status;
        this.reasonCode = reasonCode;
    }

    public static PurgeJobItem eligible(PurgeBatch purgeBatch, Long applicationId, Long jobPostingId) {
        return new PurgeJobItem(purgeBatch, applicationId, jobPostingId, PurgeItemStatus.ELIGIBLE, null);
    }

    public static PurgeJobItem skipped(
            PurgeBatch purgeBatch,
            Long applicationId,
            Long jobPostingId,
            AuditReasonCode reasonCode
    ) {
        return new PurgeJobItem(purgeBatch, applicationId, jobPostingId, PurgeItemStatus.SKIPPED, reasonCode);
    }

    /** execute — 관계형 PII 제거 + 바이너리 미보유로 최종 PURGED 확정(9d-1). */
    public static PurgeJobItem executePurged(PurgeBatch purgeBatch, Long applicationId, Long jobPostingId) {
        return new PurgeJobItem(purgeBatch, applicationId, jobPostingId, PurgeItemStatus.PURGED, null);
    }

    /** execute — 관계형 PII 제거 완료, 첨부 바이너리 삭제 대기(9d-2 saga 가 최종 승격). */
    public static PurgeJobItem executePending(PurgeBatch purgeBatch, Long applicationId, Long jobPostingId) {
        return new PurgeJobItem(purgeBatch, applicationId, jobPostingId, PurgeItemStatus.PENDING, null);
    }

    /**
     * execute — item 트랜잭션 실패(rollback 됨). reconciliation/재시도 대상(9e).
     * reasonCode = PURGE_ITEM_FAILED 로 추적 가능하게 한다(9d-1 리뷰 Low 1).
     * TODO(9e): 실패 원인 상세를 담을 sanitized reasonMessage 컬럼 추가 검토.
     */
    public static PurgeJobItem executeFailed(PurgeBatch purgeBatch, Long applicationId, Long jobPostingId) {
        return new PurgeJobItem(purgeBatch, applicationId, jobPostingId, PurgeItemStatus.FAILED,
                AuditReasonCode.PURGE_ITEM_FAILED);
    }

    /** saga ③ — 바이너리 소멸 확인 완료로 PENDING → PURGED 최종 승격(9d-2, delete 금지 ledger 의 상태 전이). */
    public void promoteToPurged() {
        if (this.status != PurgeItemStatus.PENDING) {
            throw new IllegalStateException("Only PENDING purge item can be promoted to PURGED. current=" + status);
        }
        this.status = PurgeItemStatus.PURGED;
    }
}
