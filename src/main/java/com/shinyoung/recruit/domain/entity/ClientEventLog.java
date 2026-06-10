package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.ClientEventSeverity;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.enumeration.ClientEventType;
import com.shinyoung.recruit.exception.InvalidClientEventLogException;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 지원자 화면 진단 로그(Phase 09f). {@code ActivityLog}(감사 증적)와 별개의 운영 진단 데이터다 —
 * CS/FU, 장애 재현, API 오류 상관분석에 쓰고 retention(기본 90일)으로 단기 보존한다.
 *
 * <p><b>insert-only</b> — update/delete 업무 API를 두지 않는다(삭제는 retention cleanup 전용).
 * {@code ActivityLog} 선례를 따라 {@code BaseEntity}를 상속하지 않고 서버 시각 {@code receivedAt}
 * (Clock 주입)을 source of truth로 둔다. {@code clientOccurredAt}은 브라우저 시각 참고값이다.
 *
 * <p><b>원문 PII 미저장</b> — message는 safe code 패턴 강제, metadata는 eventType별 exact allowlist,
 * principal은 HMAC hash만, ip/userAgent는 서버 추출값만 저장한다(설계 7장).
 */
@Entity
@Getter
@Table(
        name = "client_event_log",
        indexes = {
                @Index(name = "idx_client_event_received_at", columnList = "received_at"),
                @Index(name = "idx_client_event_type_received", columnList = "event_type,received_at"),
                @Index(name = "idx_client_event_session", columnList = "client_session_id"),
                @Index(name = "idx_client_event_application", columnList = "application_id"),
                @Index(name = "idx_client_event_job_posting", columnList = "job_posting_id"),
                @Index(name = "idx_client_event_related_correlation", columnList = "related_correlation_id"),
                @Index(name = "idx_client_event_source", columnList = "source,received_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_client_event_session_event",
                        columnNames = {"client_session_id", "client_event_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 서버 수신 시각(Clock 주입). 정렬/보존 기준. */
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    /** 브라우저 발생 시각. 참고값 — 정렬/보존 기준으로 쓰지 않는다. */
    @Column(name = "client_occurred_at")
    private LocalDateTime clientOccurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private ClientEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private ClientEventSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private ClientEventSource source;

    @Column(name = "client_session_id", nullable = false, length = 80)
    private String clientSessionId;

    @Column(name = "client_event_id", nullable = false, length = 80)
    private String clientEventId;

    /** ClientEvent 수집 API 자체의 X-Request-Id. */
    @Column(name = "ingest_correlation_id", length = 100)
    private String ingestCorrelationId;

    /** 실패한 업무 API 응답에서 FE가 읽어온 X-Request-Id. CS/FU 핵심 상관키. */
    @Column(name = "related_correlation_id", length = 100)
    private String relatedCorrelationId;

    @Column(name = "page_code", length = 80)
    private String pageCode;

    @Column(name = "component_code", length = 80)
    private String componentCode;

    @Column(name = "route_path", length = 300)
    private String routePath;

    @Column(name = "operation", length = 80)
    private String operation;

    @Column(name = "job_posting_id")
    private Long jobPostingId;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    /** query string 제거 후 저장. */
    @Column(name = "api_path", length = 300)
    private String apiPath;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    /** safe message code만 허용(설계 6.3, 리뷰 Blocker 3) — 자유 원문 금지. */
    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "stack_hash", length = 128)
    private String stackHash;

    /** 운영 진단용 요약. read API에서 ROLE_PRIVACY_ADMIN 전용(설계 8.3). */
    @Column(name = "stack_summary", length = 2000)
    private String stackSummary;

    @Column(name = "frontend_version", length = 80)
    private String frontendVersion;

    @Column(name = "browser_name", length = 80)
    private String browserName;

    @Column(name = "browser_version", length = 80)
    private String browserVersion;

    @Column(name = "os_name", length = 80)
    private String osName;

    @Column(name = "viewport", length = 40)
    private String viewport;

    @Column(name = "timezone", length = 80)
    private String timezone;

    /** 서버(HttpServletRequest)에서 추출. FE body 값 신뢰 금지. 민감 — ROLE_PRIVACY_ADMIN 전용. */
    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    /** 서버에서 추출. 민감 — ROLE_PRIVACY_ADMIN 전용. */
    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** 인증 사용자 loginId 원문 저장 금지 — {@code HMAC_SHA256(secret, "CLIENT_PRINCIPAL:" + username)}. */
    @Column(name = "principal_hash", length = 128)
    private String principalHash;

    @Column(name = "principal_type", length = 30)
    private String principalType;

    /** eventType별 exact allowlist 통과 metadata만 JSON 직렬화(ClientEventMetadataSanitizer). */
    @Lob
    @Column(name = "metadata_json")
    private String metadataJson;

    @Builder
    private ClientEventLog(
            LocalDateTime receivedAt,
            LocalDateTime clientOccurredAt,
            ClientEventType eventType,
            ClientEventSeverity severity,
            ClientEventSource source,
            String clientSessionId,
            String clientEventId,
            String ingestCorrelationId,
            String relatedCorrelationId,
            String pageCode,
            String componentCode,
            String routePath,
            String operation,
            Long jobPostingId,
            Long applicationId,
            String httpMethod,
            String apiPath,
            Integer httpStatus,
            String errorCode,
            String message,
            String stackHash,
            String stackSummary,
            String frontendVersion,
            String browserName,
            String browserVersion,
            String osName,
            String viewport,
            String timezone,
            String ipAddress,
            String userAgent,
            String principalHash,
            String principalType,
            String metadataJson
    ) {
        validateRequired(receivedAt, eventType, severity, source, clientSessionId, clientEventId);
        this.receivedAt = receivedAt;
        this.clientOccurredAt = clientOccurredAt;
        this.eventType = eventType;
        this.severity = severity;
        this.source = source;
        this.clientSessionId = clientSessionId;
        this.clientEventId = clientEventId;
        this.ingestCorrelationId = ingestCorrelationId;
        this.relatedCorrelationId = relatedCorrelationId;
        this.pageCode = pageCode;
        this.componentCode = componentCode;
        this.routePath = routePath;
        this.operation = operation;
        this.jobPostingId = jobPostingId;
        this.applicationId = applicationId;
        this.httpMethod = httpMethod;
        this.apiPath = apiPath;
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.message = message;
        this.stackHash = stackHash;
        this.stackSummary = stackSummary;
        this.frontendVersion = frontendVersion;
        this.browserName = browserName;
        this.browserVersion = browserVersion;
        this.osName = osName;
        this.viewport = viewport;
        this.timezone = timezone;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.principalHash = principalHash;
        this.principalType = principalType;
        this.metadataJson = metadataJson;
    }

    private static void validateRequired(
            LocalDateTime receivedAt,
            ClientEventType eventType,
            ClientEventSeverity severity,
            ClientEventSource source,
            String clientSessionId,
            String clientEventId
    ) {
        if (receivedAt == null) {
            throw new InvalidClientEventLogException("receivedAt is required.");
        }
        if (eventType == null) {
            throw new InvalidClientEventLogException("eventType is required.");
        }
        if (severity == null) {
            throw new InvalidClientEventLogException("severity is required.");
        }
        if (source == null) {
            throw new InvalidClientEventLogException("source is required.");
        }
        if (clientSessionId == null || clientSessionId.isBlank()) {
            throw new InvalidClientEventLogException("clientSessionId is required.");
        }
        if (clientEventId == null || clientEventId.isBlank()) {
            throw new InvalidClientEventLogException("clientEventId is required.");
        }
    }
}
