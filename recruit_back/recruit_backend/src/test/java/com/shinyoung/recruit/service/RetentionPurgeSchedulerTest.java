package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.request.PurgeExecuteRequest;
import com.shinyoung.recruit.dto.response.PurgeBatchDetailResponse;
import com.shinyoung.recruit.dto.response.PurgeBatchResponse;
import com.shinyoung.recruit.enumeration.PurgeBatchMode;
import com.shinyoung.recruit.enumeration.PurgeBatchStatus;
import com.shinyoung.recruit.enumeration.PurgeTriggerType;
import com.shinyoung.recruit.enumeration.RetentionScheduleRunResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link RetentionPurgeScheduler} 의 게이트·오케스트레이션 분기 단위 검증(Phase 10).
 * dry-run·execute 는 mock 으로 대체해 호출 여부·전달 인자만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class RetentionPurgeSchedulerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-20T18:00:00Z"),
            ZoneId.of("UTC")
    );

    @Mock
    private RetentionScheduleService retentionScheduleService;

    @Mock
    private RetentionDryRunService retentionDryRunService;

    @Mock
    private PurgeExecutionService purgeExecutionService;

    private RetentionPurgeScheduler retentionPurgeScheduler;

    @BeforeEach
    void setUp() {
        retentionPurgeScheduler = new RetentionPurgeScheduler(
                retentionScheduleService, retentionDryRunService, purgeExecutionService, FIXED_CLOCK);
    }

    @Test
    @DisplayName("꺼져 있으면 dry-run 을 호출하지 않고 SKIPPED_DISABLED 를 기록한다")
    void skipsWhenDisabled() {
        given(retentionScheduleService.isEnabled()).willReturn(false);

        retentionPurgeScheduler.runScheduledPurge();

        verifyNoInteractions(retentionDryRunService);
        verify(retentionScheduleService).recordRun(RetentionScheduleRunResult.SKIPPED_DISABLED, null);
    }

    @Test
    @DisplayName("예정일 전이면 dry-run 을 호출하지 않고 SKIPPED_NOT_DUE 를 기록한다")
    void skipsWhenNotDue() {
        given(retentionScheduleService.isEnabled()).willReturn(true);
        given(retentionScheduleService.nextPurgeDate()).willReturn(LocalDate.now(FIXED_CLOCK).plusDays(1));

        retentionPurgeScheduler.runScheduledPurge();

        verifyNoInteractions(retentionDryRunService);
        verify(retentionScheduleService).recordRun(RetentionScheduleRunResult.SKIPPED_NOT_DUE, null);
    }

    @Test
    @DisplayName("적격 0건이면 execute 를 호출하지 않고 NO_TARGET 을 기록한다")
    void noTarget() {
        given(retentionScheduleService.isEnabled()).willReturn(true);
        given(retentionScheduleService.nextPurgeDate()).willReturn(LocalDate.now(FIXED_CLOCK));
        given(retentionDryRunService.dryRun(AuditRequestContextResolver.SYSTEM_ACTOR_ID))
                .willReturn(batchDetail(10L, 0));

        retentionPurgeScheduler.runScheduledPurge();

        verifyNoInteractions(purgeExecutionService);
        verify(retentionScheduleService).recordRun(RetentionScheduleRunResult.NO_TARGET, null);
    }

    @Test
    @DisplayName("적격 건이 있으면 근거 batch 로 execute 를 호출하고 EXECUTED 를 기록한다")
    void executes() {
        given(retentionScheduleService.isEnabled()).willReturn(true);
        given(retentionScheduleService.nextPurgeDate()).willReturn(LocalDate.now(FIXED_CLOCK));
        given(retentionDryRunService.dryRun(AuditRequestContextResolver.SYSTEM_ACTOR_ID))
                .willReturn(batchDetail(11L, 5));
        given(purgeExecutionService.execute(any(), any()))
                .willReturn(batchDetail(12L, 5));

        retentionPurgeScheduler.runScheduledPurge();

        ArgumentCaptor<PurgeExecuteRequest> requestCaptor = ArgumentCaptor.forClass(PurgeExecuteRequest.class);
        verify(purgeExecutionService).execute(
                requestCaptor.capture(), eq(AuditRequestContextResolver.SYSTEM_ACTOR_ID));
        PurgeExecuteRequest request = requestCaptor.getValue();
        assertThat(request.confirm()).isTrue();
        assertThat(request.sourceDryRunBatchId()).isEqualTo(11L);
        assertThat(request.applicationId()).isNull();

        verify(retentionScheduleService).recordRun(RetentionScheduleRunResult.EXECUTED, 12L);
    }

    @Test
    @DisplayName("예외가 나도 전파하지 않고 ERROR 를 기록한다")
    void swallowsException() {
        given(retentionScheduleService.isEnabled()).willReturn(true);
        given(retentionScheduleService.nextPurgeDate()).willReturn(LocalDate.now(FIXED_CLOCK));
        given(retentionDryRunService.dryRun(AuditRequestContextResolver.SYSTEM_ACTOR_ID))
                .willThrow(new RuntimeException("scan failed"));

        assertThatCode(() -> retentionPurgeScheduler.runScheduledPurge()).doesNotThrowAnyException();

        verify(retentionScheduleService).recordRun(RetentionScheduleRunResult.ERROR, null);
    }

    private PurgeBatchDetailResponse batchDetail(long id, long eligibleCount) {
        LocalDateTime now = LocalDateTime.now(FIXED_CLOCK);
        PurgeBatchResponse batch = new PurgeBatchResponse(
                id, PurgeBatchMode.DRY_RUN, PurgeBatchStatus.COMPLETED, PurgeTriggerType.RETENTION,
                now, now, now, AuditRequestContextResolver.SYSTEM_ACTOR_ID, null,
                eligibleCount, eligibleCount, 0, 0, 0, 0, 0, 0);
        return new PurgeBatchDetailResponse(batch, List.of());
    }
}
