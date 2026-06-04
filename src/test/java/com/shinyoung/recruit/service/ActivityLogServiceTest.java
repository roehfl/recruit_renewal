package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.config.CorrelationIdFilter;
import com.shinyoung.recruit.domain.entity.ActivityLog;
import com.shinyoung.recruit.domain.repository.ActivityLogRepository;
import com.shinyoung.recruit.enumeration.ActorType;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ActivityLogServiceTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime FIXED = LocalDateTime.of(2026, 6, 4, 12, 0, 0);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED.atZone(ZONE).toInstant(), ZONE);
        }
    }

    @Autowired
    ActivityLogService activityLogService;
    @Autowired
    ActivityLogRepository activityLogRepository;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    AuditHmac auditHmac;

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("delete from activity_log");
        MDC.clear();
    }

    private AuditEvent.AuditEventBuilder baseEvent() {
        return AuditEvent.builder()
                .actorType(ActorType.EMPLOYEE)
                .actorId("admin01")
                .actorRoleSnapshot("ROLE_PRIVACY_ADMIN")
                .actionType(AuditActionType.PURGE_EXECUTE)
                .actionResult(AuditActionResult.SUCCESS)
                .targetType(AuditTargetType.PURGE_BATCH)
                .targetId("1");
    }

    @Test
    void recordInCurrentTx_저장_및_필드매핑_occurredAt은_clock기준() {
        ActivityLog saved = activityLogService.recordInCurrentTx(
                baseEvent().applicationId(100L).applicantId(55L).build());

        ActivityLog found = activityLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getOccurredAt()).isEqualTo(FIXED);
        assertThat(found.getActorType()).isEqualTo(ActorType.EMPLOYEE);
        assertThat(found.getActionType()).isEqualTo(AuditActionType.PURGE_EXECUTE);
        assertThat(found.getApplicationId()).isEqualTo(100L);
    }

    @Test
    void applicantRefHash는_HMAC이고_plain_SHA256이_아니다() {
        ActivityLog saved = activityLogService.recordRequiresNew(
                baseEvent().applicantId(55L).build());

        ActivityLog found = activityLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getApplicantRefHash()).isEqualTo(auditHmac.applicantRefHash(55L));
        assertThat(found.getApplicantRefHash()).isNotEqualTo(HashUtil.sha256("APPLICANT:55"));
    }

    @Test
    void metadata는_typed_record로_직렬화되고_null이면_null() {
        ActivityLog withMeta = activityLogService.recordRequiresNew(
                baseEvent().metadata(new SampleMeta("APPLICATIONS", 10L)).build());
        ActivityLog noMeta = activityLogService.recordRequiresNew(baseEvent().build());

        assertThat(activityLogRepository.findById(withMeta.getId()).orElseThrow().getMetadataJson())
                .contains("\"datasetType\":\"APPLICATIONS\"")
                .contains("\"rowCount\":10");
        assertThat(activityLogRepository.findById(noMeta.getId()).orElseThrow().getMetadataJson())
                .isNull();
    }

    @Test
    void correlationId는_MDC에서_채워진다() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-xyz");
        ActivityLog saved = activityLogService.recordRequiresNew(baseEvent().build());
        assertThat(activityLogRepository.findById(saved.getId()).orElseThrow().getCorrelationId())
                .isEqualTo("corr-xyz");
    }

    @Test
    void recordInCurrentTx는_비즈니스_롤백과_함께_사라진다() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            activityLogService.recordInCurrentTx(baseEvent().build());
            throw new RuntimeException("business failure");
        })).isInstanceOf(RuntimeException.class);

        assertThat(activityLogRepository.count()).isZero();
    }

    @Test
    void recordRequiresNew는_비즈니스_롤백에도_증적이_남는다() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            activityLogService.recordRequiresNew(baseEvent()
                    .actionResult(AuditActionResult.CONFLICT)
                    .build());
            throw new RuntimeException("business failure");
        })).isInstanceOf(RuntimeException.class);

        assertThat(activityLogRepository.count()).isEqualTo(1);
    }

    record SampleMeta(String datasetType, long rowCount) implements AuditMetadata {
    }
}
