package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.ActorType;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditReasonCode;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import com.shinyoung.recruit.exception.InvalidActivityLogException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 영속 감사 증적(Phase 09a, ADR-0006).
 *
 * <p><b>append-only</b> — 한 번 기록되면 수정/삭제하지 않는다. 정정이 필요하면 별도 correction event 를 추가
 * 기록한다. 따라서 setter·update 경로·{@code @Version} 을 두지 않으며, repository 에서도 delete/update 를
 * 노출하지 않는다({@code ActivityLogRepository}).
 *
 * <p>이 엔티티는 감사 로그 자체이므로 {@code BaseEntity}(createdAt/createdBy auditing)를 상속하지 않는다 —
 * 자체 {@code occurredAt}(Clock 주입)을 source of truth 로 둔다.
 *
 * <p><b>지원자 원문 PII 미저장(applicant raw PII-free)</b>. 단 행위자/접속/대상 식별정보
 * ({@code actorId}, {@code ipAddress}, {@code userAgent}, {@code applicationId}, {@code applicantRefHash})는
 * 포함하므로 "완전 PII-free 테이블"은 아니다. {@code metadataJson} 은 actionType 별 allowlist + PII-free 만 담는다.
 */
@Entity
@Getter
@Table(
        name = "activity_log",
        indexes = {
                @Index(name = "idx_activity_log_occurred_at", columnList = "occurred_at"),
                @Index(name = "idx_activity_log_actor_id", columnList = "actor_id"),
                @Index(name = "idx_activity_log_target", columnList = "target_type,target_id"),
                @Index(name = "idx_activity_log_application_id", columnList = "application_id"),
                @Index(name = "idx_activity_log_job_posting_id", columnList = "job_posting_id"),
                @Index(name = "idx_activity_log_action_type", columnList = "action_type")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private ActorType actorType;

    /** 행위자 loginId. SYSTEM/ANONYMOUS 면 null. */
    @Column(name = "actor_id", length = 100)
    private String actorId;

    /** 행위 시점 권한 스냅샷(라이브 join 아님). ADMIN/INTERVIEWER 구분도 이 값으로 한다. */
    @Column(name = "actor_role_snapshot", length = 255)
    private String actorRoleSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50)
    private AuditActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_result", nullable = false, length = 20)
    private AuditActionResult actionResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 40)
    private AuditTargetType targetType;

    @Column(name = "target_id", length = 100)
    private String targetId;

    /** 관리자 검색/retention 필터/파기 후 join 회피용 denormalized search key(추출 가능한 이벤트만). */
    @Column(name = "job_posting_id")
    private Long jobPostingId;

    @Column(name = "application_id")
    private Long applicationId;

    /** {@code HMAC_SHA256(AUDIT_HMAC_SECRET, "APPLICANT:" + applicantId)}. 원문(ci/email/phone) 미포함. */
    @Column(name = "applicant_ref_hash", length = 128)
    private String applicantRefHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 40)
    private AuditReasonCode reasonCode;

    /** sanitized human-readable only. */
    @Column(name = "reason_message", length = 1000)
    private String reasonMessage;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    /** OpenTelemetry trace id. 현재 deferred(nullable). */
    @Column(name = "trace_id", length = 100)
    private String traceId;

    /** 민감 — read API 노출은 ROLE_PRIVACY_ADMIN(09b). */
    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** actionType 별 allowlist + PII-free JSON 문자열(typed AuditMetadata 직렬화 결과). */
    @Lob
    @Column(name = "metadata_json")
    private String metadataJson;

    @Builder
    private ActivityLog(
            LocalDateTime occurredAt,
            ActorType actorType,
            String actorId,
            String actorRoleSnapshot,
            AuditActionType actionType,
            AuditActionResult actionResult,
            AuditTargetType targetType,
            String targetId,
            Long jobPostingId,
            Long applicationId,
            String applicantRefHash,
            AuditReasonCode reasonCode,
            String reasonMessage,
            String correlationId,
            String traceId,
            String ipAddress,
            String userAgent,
            String metadataJson
    ) {
        validateRequired(occurredAt, actorType, actionType, actionResult, targetType);
        this.occurredAt = occurredAt;
        this.actorType = actorType;
        this.actorId = actorId;
        this.actorRoleSnapshot = actorRoleSnapshot;
        this.actionType = actionType;
        this.actionResult = actionResult;
        this.targetType = targetType;
        this.targetId = targetId;
        this.jobPostingId = jobPostingId;
        this.applicationId = applicationId;
        this.applicantRefHash = applicantRefHash;
        this.reasonCode = reasonCode;
        this.reasonMessage = reasonMessage;
        this.correlationId = correlationId;
        this.traceId = traceId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.metadataJson = metadataJson;
    }

    private static void validateRequired(
            LocalDateTime occurredAt,
            ActorType actorType,
            AuditActionType actionType,
            AuditActionResult actionResult,
            AuditTargetType targetType
    ) {
        if (occurredAt == null) {
            throw new InvalidActivityLogException("occurredAt is required.");
        }
        if (actorType == null) {
            throw new InvalidActivityLogException("actorType is required.");
        }
        if (actionType == null) {
            throw new InvalidActivityLogException("actionType is required.");
        }
        if (actionResult == null) {
            throw new InvalidActivityLogException("actionResult is required.");
        }
        if (targetType == null) {
            throw new InvalidActivityLogException("targetType is required.");
        }
    }
}
