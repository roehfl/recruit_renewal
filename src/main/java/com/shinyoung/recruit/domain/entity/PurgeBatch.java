package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.PurgeBatchMode;
import com.shinyoung.recruit.enumeration.PurgeBatchStatus;
import com.shinyoung.recruit.enumeration.PurgeTriggerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 파기 산정/실행 원장(Phase 09c, 설계 §5.4). dry-run/execute 1회 실행 = 1 batch, 1:N {@code PurgeJobItem}.
 *
 * <p><b>append-only 가 아니라 "delete 금지 mutable ledger/control table"</b>(리뷰 2차 #10) —
 * {@code RUNNING → COMPLETED/PARTIAL_FAILED/FAILED} 로 상태 전이한다(update 허용, delete 금지).
 * append-only 는 {@code ActivityLog} 에만 해당하며, ActivityLog 에는 batch 단위 coarse index 만 남긴다.
 *
 * <p>09c 는 dry-run(scan/preview, 무변경) 만 생성한다. execute 전이는 09d-1.
 */
@Entity
@Getter
@Table(
        name = "purge_batch",
        indexes = {
                @Index(name = "idx_purge_batch_mode_status", columnList = "mode,status")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurgeBatch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurgeBatchMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurgeBatchStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 30)
    private PurgeTriggerType triggerType;

    /** eligibility 산정 기준 시각(Clock 주입). 정책 effective window 도 이 시각 기준으로 평가됐다. */
    @Column(name = "scan_at", nullable = false)
    private LocalDateTime scanAt;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    /** execute 전용(09d-1) — bulk execute 의 근거 dry-run batch. dry-run 은 null. */
    @Column(name = "source_dry_run_batch_id")
    private Long sourceDryRunBatchId;

    @Column(name = "total_count", nullable = false)
    private long totalCount;

    @Column(name = "eligible_count", nullable = false)
    private long eligibleCount;

    @Column(name = "skipped_count", nullable = false)
    private long skippedCount;

    /** fail-safe {@code POLICY_CONFLICT} 발생 건수 — dry-run 비결정성 차단 관측용(리뷰 3차 #4). */
    @Column(name = "policy_conflict_count", nullable = false)
    private long policyConflictCount;

    private PurgeBatch(
            PurgeBatchMode mode,
            PurgeTriggerType triggerType,
            LocalDateTime scanAt,
            LocalDateTime startedAt,
            String requestedBy
    ) {
        this.mode = mode;
        this.status = PurgeBatchStatus.RUNNING;
        this.triggerType = triggerType;
        this.scanAt = scanAt;
        this.startedAt = startedAt;
        this.requestedBy = requestedBy;
    }

    public static PurgeBatch startDryRun(LocalDateTime scanAt, LocalDateTime startedAt, String requestedBy) {
        return new PurgeBatch(PurgeBatchMode.DRY_RUN, PurgeTriggerType.RETENTION, scanAt, startedAt, requestedBy);
    }

    public void complete(
            long totalCount,
            long eligibleCount,
            long skippedCount,
            long policyConflictCount,
            LocalDateTime completedAt
    ) {
        this.status = PurgeBatchStatus.COMPLETED;
        this.totalCount = totalCount;
        this.eligibleCount = eligibleCount;
        this.skippedCount = skippedCount;
        this.policyConflictCount = policyConflictCount;
        this.completedAt = completedAt;
    }

    public void fail(LocalDateTime completedAt) {
        this.status = PurgeBatchStatus.FAILED;
        this.completedAt = completedAt;
    }
}
