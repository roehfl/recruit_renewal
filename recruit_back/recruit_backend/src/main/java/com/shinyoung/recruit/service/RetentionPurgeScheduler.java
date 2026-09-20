package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.request.PurgeExecuteRequest;
import com.shinyoung.recruit.dto.response.PurgeBatchDetailResponse;
import com.shinyoung.recruit.enumeration.RetentionScheduleRunResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 보존기간 만료 지원서 자동 파기 스케줄러(Phase 10). 기본 매일 03:00, cron 은 외부 설정.
 *
 * <p><b>게이트 2단</b> — ① 자동 파기 off ② 다음 파기 예정일 전이면 스캔하지 않는다.
 * 게이트가 없으면 dry-run 이 매일 밤 전 지원서를 훑고 지원서 수만큼 {@code purge_job_item} 행을 쌓는다.
 * 대상이 0건인 기간에도 그렇다.
 *
 * <p>{@link ClientEventLogCleanupScheduler} 와 같이 예외를 전파하지 않는다 —
 * 실패가 스케줄링 스레드와 다음 실행에 영향을 주지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionPurgeScheduler {

    private final RetentionScheduleService retentionScheduleService;
    private final RetentionDryRunService retentionDryRunService;
    private final PurgeExecutionService purgeExecutionService;
    private final Clock clock;

    @Scheduled(cron = "${retention.purge-cron:0 0 3 * * *}")
    public void runScheduledPurge() {
        try {
            if (!retentionScheduleService.isEnabled()) {
                retentionScheduleService.recordRun(RetentionScheduleRunResult.SKIPPED_DISABLED, null);
                log.info("Retention purge skipped: disabled");
                return;
            }
            LocalDate nextPurgeDate = retentionScheduleService.nextPurgeDate();
            if (nextPurgeDate.isAfter(LocalDate.now(clock))) {
                retentionScheduleService.recordRun(RetentionScheduleRunResult.SKIPPED_NOT_DUE, null);
                log.info("Retention purge skipped: not due until {}", nextPurgeDate);
                return;
            }

            PurgeBatchDetailResponse dryRun = retentionDryRunService.dryRun(
                    AuditRequestContextResolver.SYSTEM_ACTOR_ID);
            if (dryRun.batch().eligibleCount() == 0) {
                retentionScheduleService.recordRun(RetentionScheduleRunResult.NO_TARGET, null);
                log.info("Retention purge found no target. dryRunBatchId={}", dryRun.batch().id());
                return;
            }

            PurgeBatchDetailResponse executed = purgeExecutionService.execute(
                    new PurgeExecuteRequest(true, dryRun.batch().id(), null),
                    AuditRequestContextResolver.SYSTEM_ACTOR_ID);
            retentionScheduleService.recordRun(RetentionScheduleRunResult.EXECUTED, executed.batch().id());
            log.info("Retention purge executed. batchId={}, purged={}, skipped={}, failed={}",
                    executed.batch().id(), executed.batch().purgedCount(),
                    executed.batch().skippedCount(), executed.batch().failedCount());
        } catch (Exception e) {
            log.error("Retention purge failed", e);
            recordErrorSafely();
        }
    }

    /** 결과 기록까지 실패하면 더 할 수 있는 일이 없다 — 로그만 남기고 삼킨다. */
    private void recordErrorSafely() {
        try {
            retentionScheduleService.recordRun(RetentionScheduleRunResult.ERROR, null);
        } catch (RuntimeException e) {
            log.error("Retention purge result recording also failed", e);
        }
    }
}
