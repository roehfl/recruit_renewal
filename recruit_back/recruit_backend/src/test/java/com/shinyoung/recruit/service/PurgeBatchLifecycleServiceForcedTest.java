package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ActivityLog;
import com.shinyoung.recruit.domain.entity.PurgeBatch;
import com.shinyoung.recruit.domain.repository.ActivityLogRepository;
import com.shinyoung.recruit.domain.repository.PurgeBatchRepository;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.ForcedPurgeReason;
import com.shinyoung.recruit.enumeration.PurgeBatchMode;
import com.shinyoung.recruit.enumeration.PurgeBatchStatus;
import com.shinyoung.recruit.enumeration.PurgeTriggerType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 강제 파기(Phase 10) batch lifecycle 검증 — start/complete/fail 은 REQUIRES_NEW 라 테스트 트랜잭션으로
 * 감싸면 커밋이 보이지 않으므로 {@link PurgeExecutionServiceTest} 와 동일하게 <b>비-트랜잭션 + JdbcTemplate
 * 정리</b>로 실행한다.
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
class PurgeBatchLifecycleServiceForcedTest {

    @Autowired private PurgeBatchLifecycleService purgeBatchLifecycleService;
    @Autowired private PurgeBatchRepository purgeBatchRepository;
    @Autowired private ActivityLogRepository activityLogRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        // 비-트랜잭션 테스트가 커밋한 행 정리(FK 자식 → 부모 순).
        List<String> tables = List.of("activity_log", "purge_job_item", "purge_batch");
        for (String table : tables) {
            jdbcTemplate.execute("DELETE FROM " + table);
        }
    }

    @Test
    @DisplayName("startForced 는 triggerType=DATA_SUBJECT_REQUEST, mode=EXECUTE 로 RUNNING batch 를 만든다")
    void startForcedCreatesBatch() {
        PurgeBatch batch = purgeBatchLifecycleService.startForced("tester");

        assertThat(batch.getMode()).isEqualTo(PurgeBatchMode.EXECUTE);
        assertThat(batch.getTriggerType()).isEqualTo(PurgeTriggerType.DATA_SUBJECT_REQUEST);
        assertThat(batch.getStatus()).isEqualTo(PurgeBatchStatus.RUNNING);
        assertThat(batch.getSourceDryRunBatchId()).isNull();
    }

    @Test
    @DisplayName("completeForced 는 집계를 확정하고 PURGE_FORCED 감사를 남긴다")
    void completeForcedRecordsAudit() {
        PurgeBatch batch = purgeBatchLifecycleService.startForced("tester");

        purgeBatchLifecycleService.completeForced(
                batch.getId(), "ref-hash", ForcedPurgeReason.DATA_SUBJECT_REQUEST,
                2, 2, 0, 0, 0, "tester");

        PurgeBatch saved = purgeBatchRepository.findById(batch.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PurgeBatchStatus.COMPLETED);
        assertThat(saved.getPurgedCount()).isEqualTo(2);

        List<ActivityLog> audits = searchForcedAudits();
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getActionType()).isEqualTo(AuditActionType.PURGE_FORCED);
        assertThat(audits.get(0).getActionResult()).isEqualTo(AuditActionResult.SUCCESS);
        assertThat(audits.get(0).getMetadataJson()).contains("ref-hash").doesNotContain("@");
    }

    @Test
    @DisplayName("pendingCount > 0 이면 batch 가 PARTIAL_FAILED 가 되고 감사 actionResult 는 FAILURE 다")
    void completeForcedWithPendingCountBecomesPartialFailed() {
        PurgeBatch batch = purgeBatchLifecycleService.startForced("tester");

        purgeBatchLifecycleService.completeForced(
                batch.getId(), "ref-hash-pending", ForcedPurgeReason.DUPLICATE_ACCOUNT,
                2, 1, 1, 0, 0, "tester");

        PurgeBatch saved = purgeBatchRepository.findById(batch.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PurgeBatchStatus.PARTIAL_FAILED);
        assertThat(saved.getPendingCount()).isEqualTo(1);

        List<ActivityLog> audits = searchForcedAudits();
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getActionResult()).isEqualTo(AuditActionResult.FAILURE);
    }

    @Test
    @DisplayName("failForced 는 batch 를 FAILED 로 만들고 PURGE_FORCED + FAILURE 감사를 남긴다")
    void failForcedMarksBatchFailedAndRecordsAudit() {
        PurgeBatch batch = purgeBatchLifecycleService.startForced("tester");

        purgeBatchLifecycleService.failForced(batch.getId(), "tester");

        PurgeBatch saved = purgeBatchRepository.findById(batch.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PurgeBatchStatus.FAILED);

        List<ActivityLog> audits = searchForcedAudits();
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getActionType()).isEqualTo(AuditActionType.PURGE_FORCED);
        assertThat(audits.get(0).getActionResult()).isEqualTo(AuditActionResult.FAILURE);
    }

    private List<ActivityLog> searchForcedAudits() {
        return activityLogRepository.search(
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                null, AuditActionType.PURGE_FORCED, null, null, null, null,
                PageRequest.of(0, 10)).getContent();
    }
}
