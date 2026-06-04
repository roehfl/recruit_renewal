package com.shinyoung.recruit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.config.CorrelationIdFilter;
import com.shinyoung.recruit.domain.entity.ActivityLog;
import com.shinyoung.recruit.domain.repository.ActivityLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 영속 감사 증적 기록 서비스(Phase 09a, ADR-0006). emission 은 AOP blanket 이 아니라 이 서비스의 명시적
 * 2경로 호출로 한다. ActivityLog 가 무엇을 증명하는가에 따라 호출부가 경로를 고른다.
 *
 * <ul>
 *   <li>{@link #recordInCurrentTx(AuditEvent)} — <b>커밋된 변경 성공 증적</b>. 비즈니스 트랜잭션에 join(REQUIRED)
 *       해 원자적으로 남긴다. 감사 insert 실패 시 비즈니스도 함께 rollback 된다. afterCommit 으로 쓰지 않는다.</li>
 *   <li>{@link #recordRequiresNew(AuditEvent)} — <b>실패/거부/충돌/스킵 증적 + 정보 반출(fail-close)</b>.
 *       {@code REQUIRES_NEW} 로 별도 트랜잭션에 남겨 비즈니스 rollback 과 무관하게 보존한다.</li>
 * </ul>
 *
 * <p>두 메서드는 외부(09b 서비스/컨트롤러)에서 호출된다 — Spring self-invocation 프록시 함정을 피하려면
 * 같은 서비스 내부에서 호출하지 말 것.
 */
@Service
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final Clock clock;
    private final AuditHmac auditHmac;

    /**
     * 감사 metadata 직렬화 전용 ObjectMapper. 앱(web) Jackson 설정과 분리해 audit JSON 포맷을 안정적으로
     * 유지한다(설정 변경이 감사 증적 포맷에 새지 않도록). typed AuditMetadata record 만 직렬화한다.
     */
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    public ActivityLogService(
            ActivityLogRepository activityLogRepository,
            Clock clock,
            AuditHmac auditHmac
    ) {
        this.activityLogRepository = activityLogRepository;
        this.clock = clock;
        this.auditHmac = auditHmac;
    }

    /** 커밋된 도메인 변경의 성공 증적 — 비즈니스 트랜잭션에 join(원자적). */
    @Transactional(propagation = Propagation.REQUIRED)
    public ActivityLog recordInCurrentTx(AuditEvent event) {
        return activityLogRepository.save(toEntity(event));
    }

    /** 실패/거부/충돌/스킵 증적 + 정보 반출(fail-close) — 별도 트랜잭션에 남겨 비즈니스 rollback 과 무관하게 보존. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ActivityLog recordRequiresNew(AuditEvent event) {
        return activityLogRepository.save(toEntity(event));
    }

    private ActivityLog toEntity(AuditEvent event) {
        return ActivityLog.builder()
                .occurredAt(LocalDateTime.now(clock))
                .actorType(event.actorType())
                .actorId(event.actorId())
                .actorRoleSnapshot(event.actorRoleSnapshot())
                .actionType(event.actionType())
                .actionResult(event.actionResult())
                .targetType(event.targetType())
                .targetId(event.targetId())
                .jobPostingId(event.jobPostingId())
                .applicationId(event.applicationId())
                .applicantRefHash(auditHmac.applicantRefHash(event.applicantId()))
                .reasonCode(event.reasonCode())
                .reasonMessage(event.reasonMessage())
                .correlationId(resolveCorrelationId(event.correlationId()))
                .ipAddress(event.ipAddress())
                .userAgent(event.userAgent())
                .metadataJson(serializeMetadata(event.metadata()))
                .build();
    }

    private String resolveCorrelationId(String override) {
        if (override != null && !override.isBlank()) {
            return override;
        }
        return CorrelationIdFilter.currentCorrelationId();
    }

    private String serializeMetadata(AuditMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit metadata", e);
        }
    }
}
