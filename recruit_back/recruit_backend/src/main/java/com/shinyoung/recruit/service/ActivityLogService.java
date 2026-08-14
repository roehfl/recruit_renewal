package com.shinyoung.recruit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.config.CorrelationIdFilter;
import com.shinyoung.recruit.domain.entity.ActivityLog;
import com.shinyoung.recruit.domain.repository.ActivityLogRepository;
import com.shinyoung.recruit.enumeration.ActorType;
import com.shinyoung.recruit.exception.InvalidActivityLogException;
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
 *
 * <p>request-derived 문자열(userAgent/ipAddress/reasonMessage 등)은 저장 직전에 normalize 한다(9a 리뷰 보완)
 * — CR/LF/TAB 제거 + 컬럼 길이 truncate. 외부 입력이 길다는 이유만으로 audit insert 가 실패해
 * 비즈니스 트랜잭션 rollback(recordInCurrentTx)이나 egress fail-close 차단(9b)이 발생하지 않게 한다.
 */
@Service
public class ActivityLogService {

    // ActivityLog 컬럼 길이와 동일하게 유지한다(초과분은 truncate).
    private static final int MAX_ACTOR_ID = 100;
    private static final int MAX_ROLE_SNAPSHOT = 255;
    private static final int MAX_REASON_MESSAGE = 1000;
    private static final int MAX_CORRELATION_ID = 100;
    private static final int MAX_IP_ADDRESS = 64;
    private static final int MAX_USER_AGENT = 512;

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
        validateActorIdPresence(event);
        return ActivityLog.builder()
                .occurredAt(LocalDateTime.now(clock))
                .actorType(event.actorType())
                .actorId(safe(event.actorId(), MAX_ACTOR_ID))
                .actorRoleSnapshot(safe(event.actorRoleSnapshot(), MAX_ROLE_SNAPSHOT))
                .actionType(event.actionType())
                .actionResult(event.actionResult())
                .targetType(event.targetType())
                .targetId(event.targetId())
                .jobPostingId(event.jobPostingId())
                .applicationId(event.applicationId())
                .applicantRefHash(auditHmac.applicantRefHash(event.applicantId()))
                .reasonCode(event.reasonCode())
                .reasonMessage(safe(event.reasonMessage(), MAX_REASON_MESSAGE))
                .correlationId(safe(resolveCorrelationId(event.correlationId()), MAX_CORRELATION_ID))
                .ipAddress(safe(event.ipAddress(), MAX_IP_ADDRESS))
                .userAgent(safe(event.userAgent(), MAX_USER_AGENT))
                .metadataJson(serializeMetadata(event.metadata()))
                .build();
    }

    /** EMPLOYEE/APPLICANT 행위자는 actorId 필수(9a 2차 리뷰 — 9b 계측 시 검증 추가). SYSTEM/ANONYMOUS 는 null 허용. */
    private void validateActorIdPresence(AuditEvent event) {
        if ((event.actorType() == ActorType.EMPLOYEE || event.actorType() == ActorType.APPLICANT)
                && (event.actorId() == null || event.actorId().isBlank())) {
            throw new InvalidActivityLogException("actorId is required for EMPLOYEE/APPLICANT actor.");
        }
    }

    private String resolveCorrelationId(String override) {
        if (override != null && !override.isBlank()) {
            return override;
        }
        return CorrelationIdFilter.currentCorrelationId();
    }

    /** CR/LF/TAB → 공백 치환 후 trim, 컬럼 길이 초과분 truncate. blank 면 null(저장 안 함). */
    private String safe(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", " ").trim();
        if (sanitized.isBlank()) {
            return null;
        }
        return sanitized.length() <= max ? sanitized : sanitized.substring(0, max);
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
