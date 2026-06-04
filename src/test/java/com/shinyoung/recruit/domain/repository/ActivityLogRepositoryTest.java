package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ActivityLog;
import com.shinyoung.recruit.enumeration.ActorType;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditReasonCode;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import com.shinyoung.recruit.exception.InvalidActivityLogException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ActivityLogRepositoryTest {

    @Autowired
    ActivityLogRepository activityLogRepository;

    @Test
    void 저장_조회_enum_및_metadata_매핑() {
        ActivityLog log = ActivityLog.builder()
                .occurredAt(LocalDateTime.of(2026, 6, 4, 10, 0))
                .actorType(ActorType.EMPLOYEE)
                .actorId("admin01")
                .actorRoleSnapshot("ROLE_PRIVACY_ADMIN")
                .actionType(AuditActionType.PURGE_EXECUTE)
                .actionResult(AuditActionResult.SUCCESS)
                .targetType(AuditTargetType.PURGE_BATCH)
                .targetId("42")
                .jobPostingId(7L)
                .applicationId(100L)
                .applicantRefHash("deadbeef")
                .reasonCode(AuditReasonCode.ALREADY_PURGED)
                .reasonMessage("already purged")
                .correlationId("corr-1")
                .ipAddress("127.0.0.1")
                .userAgent("JUnit")
                .metadataJson("{\"purgedCount\":3}")
                .build();

        ActivityLog saved = activityLogRepository.save(log);

        assertThat(saved.getId()).isNotNull();
        ActivityLog found = activityLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getActorType()).isEqualTo(ActorType.EMPLOYEE);
        assertThat(found.getActionType()).isEqualTo(AuditActionType.PURGE_EXECUTE);
        assertThat(found.getActionResult()).isEqualTo(AuditActionResult.SUCCESS);
        assertThat(found.getTargetType()).isEqualTo(AuditTargetType.PURGE_BATCH);
        assertThat(found.getReasonCode()).isEqualTo(AuditReasonCode.ALREADY_PURGED);
        assertThat(found.getMetadataJson()).isEqualTo("{\"purgedCount\":3}");
        assertThat(activityLogRepository.count()).isEqualTo(1);
    }

    @Test
    void 필수필드_누락이면_InvalidActivityLogException() {
        assertThatThrownBy(() -> ActivityLog.builder()
                .actorType(ActorType.SYSTEM)
                .actionType(AuditActionType.PURGE_SCAN)
                .actionResult(AuditActionResult.SKIPPED)
                .targetType(AuditTargetType.JOB_APPLICATION)
                // occurredAt 누락
                .build())
                .isInstanceOf(InvalidActivityLogException.class);
    }
}
