# Phase 09f Client Event Log Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 지원자 Web UI 오류/중요 흐름 이벤트를 수집(`POST /api/client-events`)·조회(`GET /api/admin/client-events`)·보존(90일 retention)하는 백엔드를 3개 slice(09f-1/09f-3/09f-4)로 구현한다.

**Architecture:** `ActivityLog`(감사)와 분리된 insert-only 진단 로그 도메인. 수집은 permitAll + 3단 in-memory rate limit + eventType별 exact metadata allowlist + safe message code 강제. 조회는 09b 감사 read 패턴(range/page guard + `includeSensitive` 권한별 마스킹) 복제. 보존은 `@EnableScheduling`(프로젝트 최초 도입) + 관리자 수동 트리거.

**Tech Stack:** Java 17, Spring Boot 4.x, Spring Security(Session), JPA(H2/MariaDB), JUnit 5 + AssertJ + Mockito, MockMvc.

**Spec:** `docs/codex/design/phase-09f-client-event-log-be-design.md` (필수 선독. 원본: `docs/codex/design/phase-09f-client-event-log-design.md`)

---

## 실행 전 주의 (CLAUDE.md 준수)

- **테스트는 scoped로만 실행한다.** `clean test` 전체 실행은 사용자가 명시 요청할 때만. 각 태스크의 Run 명령을 그대로 쓴다.
- **git commit은 사용자가 이 플랜의 실행을 지시한 경우에만** 태스크별로 수행한다(CLAUDE.md 4.4). 커밋 메시지는 프로젝트 관례(`9f-1 ...` 형식 단문)를 따른다.
- 모든 `@DataJpaTest` import는 Spring Boot 4 경로다: `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`
- `@SpringBootTest`는 `properties = "crypto.aes.key=22791194512954214612461221261067"`를 붙인다(기존 `AdminAuditControllerTest` 패턴, 테스트용 예시 키).
- 컨트롤러 선언 경로에는 `/api`를 붙이지 않는다 — `WebMvcConfig`가 `controller` 패키지에 자동 부여한다. MockMvc 요청 경로에는 `/api`를 붙인다.

## 파일 구조 (전체)

| Slice | 파일 | 책임 |
| --- | --- | --- |
| 09f-1 | `enumeration/ClientEventType.java` (신규) | 이벤트 유형 14종 |
| 09f-1 | `enumeration/ClientEventSeverity.java` (신규) | INFO/WARN/ERROR |
| 09f-1 | `enumeration/ClientEventSource.java` (신규) | APPLICANT_WEB/ADMIN_WEB |
| 09f-1 | `exception/InvalidClientEventLogException.java` (신규) | 수집 검증 실패(400) |
| 09f-1 | `exception/ClientEventRateLimitExceededException.java` (신규) | rate limit 초과(429) |
| 09f-1 | `exception/GlobalExceptionHandler.java` (변경) | 신규 예외 매핑 |
| 09f-1 | `domain/entity/ClientEventLog.java` (신규) | insert-only 진단 로그 엔티티 |
| 09f-1 | `domain/repository/ClientEventLogRepository.java` (신규) | save/exists/조회 (marker Repository) |
| 09f-1 | `service/ClientEventMetadataSanitizer.java` (신규) | eventType별 exact allowlist 검증 + JSON 직렬화 |
| 09f-1 | `service/ClientEventRateLimiter.java` (신규) | ip/ip+session/principal 3단 고정 윈도우 |
| 09f-1 | `dto/request/ClientEventLogRequest.java` (신규) | 수집 요청 record |
| 09f-1 | `dto/response/ClientEventLogIngestResponse.java` (신규) | accepted/duplicate/id |
| 09f-1 | `service/ClientEventLogService.java` (신규) | 수집 파이프라인 |
| 09f-1 | `controller/ClientEventLogController.java` (신규) | `POST /client-events` |
| 09f-1 | `config/SecurityConfig.java` (변경) | permitAll matcher + CORS exposedHeaders |
| 09f-1 | `src/main/resources/application.yaml` (변경) | `client-event-log:` 설정 블록 |
| 09f-3 | `exception/ClientEventLogNotFoundException.java` (신규) | 404 |
| 09f-3 | `exception/InvalidClientEventQueryException.java` (신규) | 조회 가드 위반(400) |
| 09f-3 | `dto/response/ClientEventLogResponse.java` (신규) | 권한별 마스킹 projection |
| 09f-3 | `service/ClientEventLogReadService.java` (신규) | 검색/단건 + guard |
| 09f-3 | `controller/AdminClientEventLogController.java` (신규) | `GET /admin/client-events` |
| 09f-4 | `config/SchedulingConfig.java` (신규) | `@EnableScheduling` |
| 09f-4 | `service/ClientEventLogCleanupService.java` (신규) | retention bulk delete |
| 09f-4 | `service/ClientEventLogCleanupScheduler.java` (신규) | 일 1회 cron 실행 |

---

# Slice 09f-1 — Backend Ingest Foundation

### Task 1: Enum 3종 + 예외 2종 + GlobalExceptionHandler 매핑

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/enumeration/ClientEventType.java`
- Create: `src/main/java/com/shinyoung/recruit/enumeration/ClientEventSeverity.java`
- Create: `src/main/java/com/shinyoung/recruit/enumeration/ClientEventSource.java`
- Create: `src/main/java/com/shinyoung/recruit/exception/InvalidClientEventLogException.java`
- Create: `src/main/java/com/shinyoung/recruit/exception/ClientEventRateLimitExceededException.java`
- Modify: `src/main/java/com/shinyoung/recruit/exception/GlobalExceptionHandler.java` (handleActivityLogNotFound 메서드 뒤)

선언부만 있는 태스크라 테스트는 컴파일 확인으로 갈음한다. 매핑 동작은 Task 6 컨트롤러 테스트가 검증한다.

- [ ] **Step 1: Enum 3종 작성**

```java
// src/main/java/com/shinyoung/recruit/enumeration/ClientEventType.java
package com.shinyoung.recruit.enumeration;

/** Client event 유형(Phase 09f). 수집 ROI가 높은 오류/핵심 checkpoint만 정의한다 — clickstream 아님. */
public enum ClientEventType {
    PAGE_OPENED,
    CHECKPOINT,

    API_ERROR,
    API_TIMEOUT,
    NETWORK_ERROR,
    SESSION_EXPIRED,
    FORBIDDEN,

    JS_ERROR,
    UNHANDLED_REJECTION,

    APPLICATION_DRAFT_SAVE_FAILED,
    APPLICATION_SUBMIT_CLICKED,
    APPLICATION_SUBMIT_FAILED,
    ATTACHMENT_UPLOAD_FAILED,

    CLIENT_VALIDATION_FAILED
}
```

```java
// src/main/java/com/shinyoung/recruit/enumeration/ClientEventSeverity.java
package com.shinyoung.recruit.enumeration;

public enum ClientEventSeverity {
    INFO,
    WARN,
    ERROR
}
```

```java
// src/main/java/com/shinyoung/recruit/enumeration/ClientEventSource.java
package com.shinyoung.recruit.enumeration;

/**
 * Client event 발생 채널(Phase 09f). public 수집 API는 APPLICANT_WEB만 허용한다(설계 6.1, 리뷰 Blocker 1)
 * — ADMIN_WEB은 enum에만 존재하며, 향후 별도 admin-authenticated 수집 endpoint에서만 받는다.
 */
public enum ClientEventSource {
    APPLICANT_WEB,
    ADMIN_WEB
}
```

- [ ] **Step 2: 예외 2종 작성**

```java
// src/main/java/com/shinyoung/recruit/exception/InvalidClientEventLogException.java
package com.shinyoung.recruit.exception;

public class InvalidClientEventLogException extends RuntimeException {

    public InvalidClientEventLogException(String message) {
        super(message);
    }
}
```

```java
// src/main/java/com/shinyoung/recruit/exception/ClientEventRateLimitExceededException.java
package com.shinyoung.recruit.exception;

public class ClientEventRateLimitExceededException extends RuntimeException {

    public ClientEventRateLimitExceededException(String message) {
        super(message);
    }
}
```

- [ ] **Step 3: GlobalExceptionHandler에 매핑 추가**

`handleActivityLogNotFound` 메서드(189행 부근) 바로 뒤에 삽입:

```java
    @ExceptionHandler(InvalidClientEventLogException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidClientEventLog(InvalidClientEventLogException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
    }

    /** client event 수집 rate limit 초과(Phase 09f). FE telemetry는 fire-and-forget이라 응답을 사용하지 않는다. */
    @ExceptionHandler(ClientEventRateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleClientEventRateLimitExceeded(ClientEventRateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.fail(e.getMessage()));
    }
```

- [ ] **Step 4: 컴파일 확인**

Run: `.\gradlew.bat compileJava --no-daemon`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```
git add src/main/java/com/shinyoung/recruit/enumeration/ClientEvent*.java src/main/java/com/shinyoung/recruit/exception/InvalidClientEventLogException.java src/main/java/com/shinyoung/recruit/exception/ClientEventRateLimitExceededException.java src/main/java/com/shinyoung/recruit/exception/GlobalExceptionHandler.java
git commit -m "9f-1 client event enum/exception 추가"
```

---

### Task 2: ClientEventLog 엔티티 + Repository + 리포지토리 테스트

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/domain/entity/ClientEventLog.java`
- Create: `src/main/java/com/shinyoung/recruit/domain/repository/ClientEventLogRepository.java`
- Test: `src/test/java/com/shinyoung/recruit/domain/repository/ClientEventLogRepositoryTest.java`

- [ ] **Step 1: 실패하는 리포지토리 테스트 작성**

```java
// src/test/java/com/shinyoung/recruit/domain/repository/ClientEventLogRepositoryTest.java
package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ClientEventLog;
import com.shinyoung.recruit.enumeration.ClientEventSeverity;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.enumeration.ClientEventType;
import com.shinyoung.recruit.exception.InvalidClientEventLogException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ClientEventLogRepositoryTest {

    @Autowired
    ClientEventLogRepository clientEventLogRepository;

    private ClientEventLog.ClientEventLogBuilder baseBuilder(String sessionId, String eventId) {
        return ClientEventLog.builder()
                .receivedAt(LocalDateTime.of(2026, 6, 10, 12, 0))
                .eventType(ClientEventType.API_ERROR)
                .severity(ClientEventSeverity.ERROR)
                .source(ClientEventSource.APPLICANT_WEB)
                .clientSessionId(sessionId)
                .clientEventId(eventId);
    }

    @Test
    void 저장_조회_enum_매핑() {
        ClientEventLog saved = clientEventLogRepository.save(baseBuilder("session-0001", "event-0001")
                .relatedCorrelationId("corr-1")
                .pageCode("APPLICATION_FORM")
                .httpMethod("POST")
                .apiPath("/api/applicant/applications/1/education")
                .httpStatus(500)
                .errorCode("INTERNAL_SERVER_ERROR")
                .message("Request failed with status code 500")
                .applicationId(123L)
                .jobPostingId(10L)
                .ipAddress("127.0.0.1")
                .userAgent("JUnit")
                .metadataJson("{\"durationMs\":1250}")
                .build());

        assertThat(saved.getId()).isNotNull();
        ClientEventLog found = clientEventLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getEventType()).isEqualTo(ClientEventType.API_ERROR);
        assertThat(found.getSeverity()).isEqualTo(ClientEventSeverity.ERROR);
        assertThat(found.getSource()).isEqualTo(ClientEventSource.APPLICANT_WEB);
        assertThat(found.getReceivedAt()).isEqualTo(LocalDateTime.of(2026, 6, 10, 12, 0));
        assertThat(found.getMetadataJson()).isEqualTo("{\"durationMs\":1250}");
        assertThat(clientEventLogRepository.count()).isEqualTo(1);
    }

    @Test
    void 같은_session_event_쌍은_unique_제약으로_거부된다() {
        clientEventLogRepository.saveAndFlush(baseBuilder("session-0001", "event-0001").build());

        assertThatThrownBy(() ->
                clientEventLogRepository.saveAndFlush(baseBuilder("session-0001", "event-0001").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsBy로_중복을_선확인한다() {
        clientEventLogRepository.save(baseBuilder("session-0001", "event-0001").build());

        assertThat(clientEventLogRepository
                .existsByClientSessionIdAndClientEventId("session-0001", "event-0001")).isTrue();
        assertThat(clientEventLogRepository
                .existsByClientSessionIdAndClientEventId("session-0001", "other")).isFalse();
    }

    @Test
    void 필수값_누락이면_엔티티_생성이_거부된다() {
        assertThatThrownBy(() -> ClientEventLog.builder()
                .receivedAt(LocalDateTime.of(2026, 6, 10, 12, 0))
                .eventType(ClientEventType.API_ERROR)
                .severity(ClientEventSeverity.ERROR)
                .source(ClientEventSource.APPLICANT_WEB)
                .clientSessionId("session-0001")
                // clientEventId 누락
                .build())
                .isInstanceOf(InvalidClientEventLogException.class);
    }
}
```

- [ ] **Step 2: 테스트가 컴파일 실패하는지 확인**

Run: `.\gradlew.bat compileTestJava --no-daemon`
Expected: FAIL — `ClientEventLog` 심볼 없음

- [ ] **Step 3: 엔티티 구현**

```java
// src/main/java/com/shinyoung/recruit/domain/entity/ClientEventLog.java
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
```

- [ ] **Step 4: Repository 구현**

```java
// src/main/java/com/shinyoung/recruit/domain/repository/ClientEventLogRepository.java
package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ClientEventLog;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * ClientEventLog 영속 접근(Phase 09f).
 *
 * <p><b>insert-only</b> — {@code JpaRepository} 대신 {@link Repository} 마커를 상속해 insert + 조회만
 * 노출한다({@code ActivityLogRepository} 선례). update/단건 delete를 두지 않는다. retention bulk delete는
 * 09f-4에서 {@code @Modifying @Query}로 추가한다.
 *
 * <p>{@code saveAndFlush}는 중복 race 흡수에 필수다(설계 6.2, 리뷰 Major 4) — {@code save()}만 쓰면
 * unique violation이 commit 시점에 터져 service의 catch를 타지 못하고 전역 409 매핑으로 샌다.
 */
public interface ClientEventLogRepository extends Repository<ClientEventLog, Long> {

    ClientEventLog save(ClientEventLog clientEventLog);

    ClientEventLog saveAndFlush(ClientEventLog clientEventLog);

    Optional<ClientEventLog> findById(Long id);

    long count();

    boolean existsByClientSessionIdAndClientEventId(String clientSessionId, String clientEventId);
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `.\gradlew.bat test --tests "com.shinyoung.recruit.domain.repository.ClientEventLogRepositoryTest" --no-daemon`
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```
git add src/main/java/com/shinyoung/recruit/domain/entity/ClientEventLog.java src/main/java/com/shinyoung/recruit/domain/repository/ClientEventLogRepository.java src/test/java/com/shinyoung/recruit/domain/repository/ClientEventLogRepositoryTest.java
git commit -m "9f-1 ClientEventLog entity/repository"
```

---

### Task 3: ClientEventMetadataSanitizer (exact allowlist)

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/service/ClientEventMetadataSanitizer.java`
- Test: `src/test/java/com/shinyoung/recruit/service/ClientEventMetadataSanitizerTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
// src/test/java/com/shinyoung/recruit/service/ClientEventMetadataSanitizerTest.java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.ClientEventType;
import com.shinyoung.recruit.exception.InvalidClientEventLogException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** eventType별 exact allowlist(설계 6.5, 리뷰 Blocker 2) — allowlist 외 key는 전부 400. 금지 key는 2차 방어선. */
class ClientEventMetadataSanitizerTest {

    private final ClientEventMetadataSanitizer sanitizer = new ClientEventMetadataSanitizer(4000);

    @Test
    void null_또는_빈_metadata는_null을_반환한다() {
        assertThat(sanitizer.sanitize(ClientEventType.API_ERROR, null)).isNull();
        assertThat(sanitizer.sanitize(ClientEventType.API_ERROR, Map.of())).isNull();
    }

    @Test
    void allowlist_key는_JSON으로_직렬화된다() {
        String json = sanitizer.sanitize(ClientEventType.API_ERROR,
                Map.of("durationMs", 1250, "retryable", false));

        assertThat(json).contains("\"durationMs\":1250");
        assertThat(json).contains("\"retryable\":false");
    }

    @Test
    void allowlist에_없는_key는_거부된다() {
        assertThatThrownBy(() -> sanitizer.sanitize(ClientEventType.API_ERROR, Map.of("unknownKey", 1)))
                .isInstanceOf(InvalidClientEventLogException.class);
    }

    @Test
    void PII성_key는_어느_eventType에서도_거부된다() {
        for (String piiKey : List.of("mobile", "schoolName", "companyName", "fileName")) {
            assertThatThrownBy(() -> sanitizer.sanitize(ClientEventType.API_ERROR, Map.of(piiKey, "x")))
                    .as("key=%s", piiKey)
                    .isInstanceOf(InvalidClientEventLogException.class);
        }
    }

    @Test
    void 다른_eventType의_허용_key라도_해당_eventType_allowlist에_없으면_거부된다() {
        // fileExtension은 ATTACHMENT_UPLOAD_FAILED 전용
        assertThatThrownBy(() -> sanitizer.sanitize(ClientEventType.API_ERROR, Map.of("fileExtension", "pdf")))
                .isInstanceOf(InvalidClientEventLogException.class);
        // axiosCode는 API_ERROR/NETWORK_ERROR 전용
        assertThatThrownBy(() -> sanitizer.sanitize(ClientEventType.ATTACHMENT_UPLOAD_FAILED, Map.of("axiosCode", "ERR")))
                .isInstanceOf(InvalidClientEventLogException.class);
    }

    @Test
    void metadata가_허용되지_않는_eventType은_key가_있으면_거부된다() {
        assertThatThrownBy(() -> sanitizer.sanitize(ClientEventType.SESSION_EXPIRED, Map.of("durationMs", 1)))
                .isInstanceOf(InvalidClientEventLogException.class);
    }

    @Test
    void nested_object와_array_value는_거부된다() {
        assertThatThrownBy(() -> sanitizer.sanitize(ClientEventType.API_ERROR,
                Map.of("durationMs", Map.of("inner", 1))))
                .isInstanceOf(InvalidClientEventLogException.class);
        assertThatThrownBy(() -> sanitizer.sanitize(ClientEventType.API_ERROR,
                Map.of("durationMs", List.of(1, 2))))
                .isInstanceOf(InvalidClientEventLogException.class);
    }

    @Test
    void 문자열_value_200자_초과는_거부된다() {
        assertThatThrownBy(() -> sanitizer.sanitize(ClientEventType.API_ERROR,
                Map.of("axiosCode", "x".repeat(201))))
                .isInstanceOf(InvalidClientEventLogException.class);
    }

    @Test
    void 직렬화_결과가_최대_길이를_초과하면_거부된다() {
        ClientEventMetadataSanitizer small = new ClientEventMetadataSanitizer(20);

        assertThatThrownBy(() -> small.sanitize(ClientEventType.API_ERROR,
                Map.of("axiosCode", "long-enough-value-here")))
                .isInstanceOf(InvalidClientEventLogException.class);
    }

    @Test
    void 문자열_value의_제어문자는_공백으로_치환된다() {
        String json = sanitizer.sanitize(ClientEventType.API_ERROR,
                Map.of("axiosCode", "ERR\r\nBAD\tCODE"));

        assertThat(json).doesNotContain("\\r").doesNotContain("\\n").doesNotContain("\\t");
        assertThat(json).contains("ERR BAD CODE");
    }

    @Test
    void null_value는_허용된다() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("axiosCode", null);

        String json = sanitizer.sanitize(ClientEventType.API_ERROR, metadata);

        assertThat(json).contains("\"axiosCode\":null");
    }
}
```

- [ ] **Step 2: 테스트가 컴파일 실패하는지 확인**

Run: `.\gradlew.bat compileTestJava --no-daemon`
Expected: FAIL — `ClientEventMetadataSanitizer` 심볼 없음

- [ ] **Step 3: 구현**

```java
// src/main/java/com/shinyoung/recruit/service/ClientEventMetadataSanitizer.java
package com.shinyoung.recruit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.shinyoung.recruit.enumeration.ClientEventType;
import com.shinyoung.recruit.exception.InvalidClientEventLogException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * client event metadata 검증/직렬화(Phase 09f, 설계 6.5).
 *
 * <p><b>eventType별 exact allowlist</b>(리뷰 Blocker 2) — allowlist에 없는 key는 400으로 거부한다.
 * 금지 key 패턴(denylist)은 allowlist 통과 후에도 적용되는 2차 방어선으로, 향후 allowlist 확장 시
 * PII key가 실수로 추가되는 것을 막는다.
 *
 * <p>value는 String/Number/Boolean/null만 허용한다(nested object/array 금지). 문자열은 200자 이하,
 * 직렬화 결과는 {@code client-event-log.max-metadata-json-length}(기본 4000자) 이하.
 */
@Component
public class ClientEventMetadataSanitizer {

    static final int MAX_KEYS = 20;
    static final int MAX_KEY_LENGTH = 50;
    static final int MAX_STRING_VALUE_LENGTH = 200;

    /** eventType별 허용 key(설계 6.5 표). 여기 없는 eventType/key 조합은 전부 거부. */
    private static final Map<ClientEventType, Set<String>> ALLOWLIST = Map.ofEntries(
            Map.entry(ClientEventType.API_ERROR, Set.of("durationMs", "retryable", "axiosCode")),
            Map.entry(ClientEventType.API_TIMEOUT, Set.of("durationMs", "timeoutMs")),
            Map.entry(ClientEventType.NETWORK_ERROR, Set.of("durationMs", "axiosCode")),
            Map.entry(ClientEventType.SESSION_EXPIRED, Set.of()),
            Map.entry(ClientEventType.FORBIDDEN, Set.of()),
            Map.entry(ClientEventType.JS_ERROR, Set.of("file", "line", "column")),
            Map.entry(ClientEventType.UNHANDLED_REJECTION, Set.of("reasonType")),
            Map.entry(ClientEventType.APPLICATION_DRAFT_SAVE_FAILED, Set.of("sectionCode", "failedStep")),
            Map.entry(ClientEventType.APPLICATION_SUBMIT_CLICKED, Set.of()),
            Map.entry(ClientEventType.APPLICATION_SUBMIT_FAILED, Set.of("sectionCode", "failedStep")),
            Map.entry(ClientEventType.ATTACHMENT_UPLOAD_FAILED, Set.of("fileSize", "fileExtension", "uploadStep")),
            Map.entry(ClientEventType.CLIENT_VALIDATION_FAILED, Set.of("sectionCode", "fieldCount", "errorCount")),
            Map.entry(ClientEventType.PAGE_OPENED, Set.of()),
            Map.entry(ClientEventType.CHECKPOINT, Set.of("checkpointCode"))
    );

    /** 2차 방어선 — allowlist가 확장돼도 절대 허용하면 안 되는 PII성 key(대소문자 무시, 설계 6.5). */
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "name", "username", "applicantname", "email", "phone", "phonenumber",
            "ci", "cihash", "password", "birth", "address",
            "content", "answer", "essay", "resume", "coverletter",
            "filename", "originalfilename", "body", "requestbody", "responsebody"
    );

    private final int maxMetadataJsonLength;

    /** 직렬화 전용 ObjectMapper — 앱(web) Jackson 설정 변경이 저장 포맷에 새지 않도록 분리(ActivityLogService 선례). */
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public ClientEventMetadataSanitizer(
            @Value("${client-event-log.max-metadata-json-length:4000}") int maxMetadataJsonLength
    ) {
        this.maxMetadataJsonLength = maxMetadataJsonLength;
    }

    /** 검증 통과 시 JSON 문자열, metadata 부재 시 null. 위반 시 {@link InvalidClientEventLogException}(400). */
    public String sanitize(ClientEventType eventType, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        if (metadata.size() > MAX_KEYS) {
            throw new InvalidClientEventLogException("metadata key는 최대 " + MAX_KEYS + "개입니다.");
        }

        Set<String> allowed = ALLOWLIST.getOrDefault(eventType, Set.of());
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
                throw new InvalidClientEventLogException("metadata key 형식이 올바르지 않습니다.");
            }
            if (!allowed.contains(key)) {
                throw new InvalidClientEventLogException(
                        "허용되지 않는 metadata key입니다. eventType=" + eventType + ", key=" + key);
            }
            if (FORBIDDEN_KEYS.contains(key.toLowerCase())) {
                throw new InvalidClientEventLogException("금지된 metadata key입니다. key=" + key);
            }
            sanitized.put(key, sanitizeValue(key, entry.getValue()));
        }

        String json = serialize(sanitized);
        if (json.length() > maxMetadataJsonLength) {
            throw new InvalidClientEventLogException(
                    "metadata 직렬화 길이는 최대 " + maxMetadataJsonLength + "자입니다.");
        }
        return json;
    }

    private Object sanitizeValue(String key, Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String s) {
            if (s.length() > MAX_STRING_VALUE_LENGTH) {
                throw new InvalidClientEventLogException(
                        "metadata 문자열 value는 최대 " + MAX_STRING_VALUE_LENGTH + "자입니다. key=" + key);
            }
            return s.replaceAll("\\p{Cntrl}", " ").trim();
        }
        throw new InvalidClientEventLogException(
                "metadata value는 String/Number/Boolean/null만 허용됩니다. key=" + key);
    }

    private String serialize(Map<String, Object> sanitized) {
        try {
            return objectMapper.writeValueAsString(sanitized);
        } catch (JsonProcessingException e) {
            throw new InvalidClientEventLogException("metadata 직렬화에 실패했습니다.");
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `.\gradlew.bat test --tests "com.shinyoung.recruit.service.ClientEventMetadataSanitizerTest" --no-daemon`
Expected: PASS (10 tests)

- [ ] **Step 5: Commit**

```
git add src/main/java/com/shinyoung/recruit/service/ClientEventMetadataSanitizer.java src/test/java/com/shinyoung/recruit/service/ClientEventMetadataSanitizerTest.java
git commit -m "9f-1 metadata exact allowlist sanitizer"
```

---

### Task 4: ClientEventRateLimiter (3단) + application.yaml 설정 블록

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/service/ClientEventRateLimiter.java`
- Modify: `src/main/resources/application.yaml` (파일 끝 `recruit:` 블록 뒤)
- Test: `src/test/java/com/shinyoung/recruit/service/ClientEventRateLimiterTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
// src/test/java/com/shinyoung/recruit/service/ClientEventRateLimiterTest.java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.exception.ClientEventRateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 3단 고정 윈도우 rate limit(설계 6.4, 리뷰 Major 5) — clientSessionId는 client-controlled라
 * 단독 key로 쓰지 않고 ip 글로벌 한도가 1차다.
 */
class ClientEventRateLimiterTest {

    private static class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-06-10T12:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    private ClientEventRateLimiter limiter(MutableClock clock, int ip, int session, int principal) {
        return new ClientEventRateLimiter(clock, ip, session, principal, 100);
    }

    @Test
    void 한도_내_요청은_허용된다() {
        ClientEventRateLimiter limiter = limiter(new MutableClock(), 10, 5, 5);

        assertThatCode(() -> {
            for (int i = 0; i < 5; i++) {
                limiter.check("10.0.0.1", "session-0001", null);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void session_한도_초과는_차단된다() {
        ClientEventRateLimiter limiter = limiter(new MutableClock(), 100, 2, 100);
        limiter.check("10.0.0.1", "session-0001", null);
        limiter.check("10.0.0.1", "session-0001", null);

        assertThatThrownBy(() -> limiter.check("10.0.0.1", "session-0001", null))
                .isInstanceOf(ClientEventRateLimitExceededException.class);
    }

    @Test
    void sessionId를_바꿔도_ip_글로벌_한도로_차단된다() {
        ClientEventRateLimiter limiter = limiter(new MutableClock(), 3, 100, 100);
        limiter.check("10.0.0.1", "session-0001", null);
        limiter.check("10.0.0.1", "session-0002", null);
        limiter.check("10.0.0.1", "session-0003", null);

        assertThatThrownBy(() -> limiter.check("10.0.0.1", "session-0004", null))
                .isInstanceOf(ClientEventRateLimitExceededException.class);
    }

    @Test
    void 인증_사용자는_principal_한도도_적용된다() {
        ClientEventRateLimiter limiter = limiter(new MutableClock(), 100, 100, 2);
        limiter.check("10.0.0.1", "session-0001", "hash-a");
        limiter.check("10.0.0.2", "session-0002", "hash-a"); // 다른 ip/session, 같은 principal

        assertThatThrownBy(() -> limiter.check("10.0.0.3", "session-0003", "hash-a"))
                .isInstanceOf(ClientEventRateLimitExceededException.class);
    }

    @Test
    void 윈도우가_지나면_카운터가_회복된다() {
        MutableClock clock = new MutableClock();
        ClientEventRateLimiter limiter = limiter(clock, 100, 1, 100);
        limiter.check("10.0.0.1", "session-0001", null);
        assertThatThrownBy(() -> limiter.check("10.0.0.1", "session-0001", null))
                .isInstanceOf(ClientEventRateLimitExceededException.class);

        clock.advance(Duration.ofSeconds(61));

        assertThatCode(() -> limiter.check("10.0.0.1", "session-0001", null))
                .doesNotThrowAnyException();
    }

    @Test
    void 맵_크기_상한을_넘으면_신규_key는_차단된다() {
        ClientEventRateLimiter limiter = new ClientEventRateLimiter(new MutableClock(), 1000, 1000, 1000, 4);
        // ip key + session key 2개씩 → 4 entry 채움
        limiter.check("10.0.0.1", "session-0001", null);
        limiter.check("10.0.0.2", "session-0002", null);

        assertThatThrownBy(() -> limiter.check("10.0.0.3", "session-0003", null))
                .isInstanceOf(ClientEventRateLimitExceededException.class);
    }
}
```

- [ ] **Step 2: 테스트가 컴파일 실패하는지 확인**

Run: `.\gradlew.bat compileTestJava --no-daemon`
Expected: FAIL — `ClientEventRateLimiter` 심볼 없음

- [ ] **Step 3: 구현**

```java
// src/main/java/com/shinyoung/recruit/service/ClientEventRateLimiter.java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.exception.ClientEventRateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * client event 수집 in-memory 고정 윈도우(1분) rate limit(Phase 09f, 설계 6.4).
 *
 * <p>3단 — 1차 {@code ip} 글로벌(클라이언트가 sessionId를 바꿔도 우회 불가, 리뷰 Major 5),
 * 2차 {@code ip + clientSessionId}, 3차 인증 시 {@code principalHash}. 어느 단이든 초과하면
 * {@link ClientEventRateLimitExceededException}(429).
 *
 * <p>맵 크기 가드 — 만료 엔트리는 접근 시 lazy eviction하고, 정리 후에도 상한을 넘으면 신규 key를
 * 거부한다(map 폭증 방어, fail-closed). 운영 트래픽 증가 시 Redis/token bucket 전환은 별도 phase.
 */
@Component
public class ClientEventRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;
    static final int DEFAULT_MAX_ENTRIES = 10_000;

    private record Window(long windowStart, AtomicInteger count) {
    }

    private final Clock clock;
    private final int perMinuteIp;
    private final int perMinuteSession;
    private final int perMinutePrincipal;
    private final int maxEntries;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public ClientEventRateLimiter(
            Clock clock,
            @Value("${client-event-log.rate-limit.per-minute-ip:300}") int perMinuteIp,
            @Value("${client-event-log.rate-limit.per-minute-session:60}") int perMinuteSession,
            @Value("${client-event-log.rate-limit.per-minute-principal:120}") int perMinutePrincipal
    ) {
        this(clock, perMinuteIp, perMinuteSession, perMinutePrincipal, DEFAULT_MAX_ENTRIES);
    }

    ClientEventRateLimiter(Clock clock, int perMinuteIp, int perMinuteSession, int perMinutePrincipal, int maxEntries) {
        this.clock = clock;
        this.perMinuteIp = perMinuteIp;
        this.perMinuteSession = perMinuteSession;
        this.perMinutePrincipal = perMinutePrincipal;
        this.maxEntries = maxEntries;
    }

    /** 3단 한도 검사. principalHash는 미인증이면 null. 초과 시 429 예외. */
    public void check(String ip, String clientSessionId, String principalHash) {
        long now = clock.millis();
        increment("ip:" + ip, perMinuteIp, now);
        increment("session:" + ip + ":" + clientSessionId, perMinuteSession, now);
        if (principalHash != null) {
            increment("principal:" + principalHash, perMinutePrincipal, now);
        }
    }

    private void increment(String key, int limit, long now) {
        Window window = windows.get(key);
        if (window != null && expired(window, now)) {
            windows.remove(key, window);
            window = null;
        }
        if (window == null) {
            guardCapacity(key, now);
            window = windows.computeIfAbsent(key, k -> new Window(now, new AtomicInteger()));
        }
        if (window.count().incrementAndGet() > limit) {
            throw new ClientEventRateLimitExceededException("client event 수집 요청이 너무 많습니다.");
        }
    }

    private boolean expired(Window window, long now) {
        return now - window.windowStart() >= WINDOW_MILLIS;
    }

    /** 상한 도달 시 만료 엔트리 일괄 정리 후, 그래도 가득이면 신규 key 거부(fail-closed). */
    private void guardCapacity(String newKey, long now) {
        if (windows.size() < maxEntries || windows.containsKey(newKey)) {
            return;
        }
        windows.entrySet().removeIf(entry -> expired(entry.getValue(), now));
        if (windows.size() >= maxEntries) {
            throw new ClientEventRateLimitExceededException("client event 수집 요청이 너무 많습니다.");
        }
    }
}
```

- [ ] **Step 4: application.yaml에 설정 블록 추가**

`src/main/resources/application.yaml` 파일 끝(`recruit:` 블록 뒤)에 추가:

```yaml
client-event-log:
  # 지원자 화면 진단 로그(Phase 09f). 감사 로그(activity_log)와 분리된 단기 운영 데이터.
  enabled: ${CLIENT_EVENT_LOG_ENABLED:true}
  retention-days: ${CLIENT_EVENT_LOG_RETENTION_DAYS:90}
  max-metadata-json-length: ${CLIENT_EVENT_LOG_MAX_METADATA_JSON_LENGTH:4000}
  cleanup-cron: ${CLIENT_EVENT_LOG_CLEANUP_CRON:0 0 4 * * *}
  rate-limit:
    # 1차 ip 글로벌(sessionId 변조 우회 차단) / 2차 ip+session / 3차 인증 principal
    per-minute-ip: ${CLIENT_EVENT_LOG_RATE_LIMIT_PER_MINUTE_IP:300}
    per-minute-session: ${CLIENT_EVENT_LOG_RATE_LIMIT_PER_MINUTE_SESSION:60}
    per-minute-principal: ${CLIENT_EVENT_LOG_RATE_LIMIT_PER_MINUTE_PRINCIPAL:120}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `.\gradlew.bat test --tests "com.shinyoung.recruit.service.ClientEventRateLimiterTest" --no-daemon`
Expected: PASS (6 tests)

- [ ] **Step 6: Commit**

```
git add src/main/java/com/shinyoung/recruit/service/ClientEventRateLimiter.java src/test/java/com/shinyoung/recruit/service/ClientEventRateLimiterTest.java src/main/resources/application.yaml
git commit -m "9f-1 3단 rate limiter + 설정 블록"
```

---

### Task 5: Request/Response DTO + ClientEventLogService

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/dto/request/ClientEventLogRequest.java`
- Create: `src/main/java/com/shinyoung/recruit/dto/response/ClientEventLogIngestResponse.java`
- Create: `src/main/java/com/shinyoung/recruit/service/ClientEventLogService.java`
- Test: `src/test/java/com/shinyoung/recruit/service/ClientEventLogServiceTest.java`

- [ ] **Step 1: 실패하는 서비스 단위 테스트 작성** (Mockito — repository만 mock, 나머지 협력자는 실물)

```java
// src/test/java/com/shinyoung/recruit/service/ClientEventLogServiceTest.java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.domain.entity.ClientEventLog;
import com.shinyoung.recruit.domain.repository.ClientEventLogRepository;
import com.shinyoung.recruit.dto.request.ClientEventLogRequest;
import com.shinyoung.recruit.dto.response.ClientEventLogIngestResponse;
import com.shinyoung.recruit.enumeration.ClientEventSeverity;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.enumeration.ClientEventType;
import com.shinyoung.recruit.exception.InvalidClientEventLogException;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientEventLogServiceTest {

    private ClientEventLogRepository repository;
    private ClientEventLogService service;
    private final AuditHmac auditHmac = new AuditHmac("test-client-event-secret");
    private final Clock fixedClock =
            Clock.fixed(Instant.parse("2026-06-10T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @BeforeEach
    void setUp() {
        repository = mock(ClientEventLogRepository.class);
        service = new ClientEventLogService(
                repository,
                new ClientEventMetadataSanitizer(4000),
                new ClientEventRateLimiter(fixedClock, 1000, 1000, 1000),
                auditHmac,
                fixedClock,
                true
        );
    }

    private ClientEventLogRequest request(ClientEventSource source, String message, Map<String, Object> metadata) {
        return new ClientEventLogRequest(
                ClientEventType.API_ERROR, ClientEventSeverity.ERROR, source,
                "5d7c00ff-0d53-4ea3-bd44-68a9f7d68f9f", "6a1bd08e-3cd1-4c0f-85c0-e6a65e4a0a33",
                LocalDateTime.of(2026, 6, 10, 11, 59),
                "7c51f646-e75b-4d19-9f69-f82f7f0f2dc4",
                "APPLICATION_FORM", "EDUCATION_SECTION", "/applications/123/form?step=2", "SAVE_EDUCATION",
                10L, 123L,
                "POST", "/api/applicant/applications/123/education?retry=1", 500, "INTERNAL_SERVER_ERROR",
                message, null, "at saveEducation (app.js:120)",
                "2026.06.10-1", "Chrome", "126", "Windows", "1440x900", "Asia/Seoul",
                metadata
        );
    }

    private MockHttpServletRequest servletRequest() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("10.0.0.9");
        servletRequest.addHeader("User-Agent", "JUnit-agent");
        return servletRequest;
    }

    @Test
    void 정상_수집시_서버값으로_저장된다() {
        when(repository.existsByClientSessionIdAndClientEventId(anyString(), anyString())).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ClientEventLogIngestResponse response = service.record(
                request(ClientEventSource.APPLICANT_WEB, "Request failed with status code 500",
                        Map.of("durationMs", 1250)),
                null, servletRequest());

        assertThat(response.accepted()).isTrue();
        assertThat(response.duplicate()).isFalse();

        ArgumentCaptor<ClientEventLog> captor = ArgumentCaptor.forClass(ClientEventLog.class);
        verify(repository).saveAndFlush(captor.capture());
        ClientEventLog saved = captor.getValue();
        assertThat(saved.getReceivedAt()).isEqualTo(LocalDateTime.ofInstant(
                Instant.parse("2026-06-10T03:00:00Z"), ZoneId.of("Asia/Seoul")));
        assertThat(saved.getIpAddress()).isEqualTo("10.0.0.9");        // 서버 추출값
        assertThat(saved.getUserAgent()).isEqualTo("JUnit-agent");     // 서버 추출값
        assertThat(saved.getApiPath()).isEqualTo("/api/applicant/applications/123/education"); // query 제거
        assertThat(saved.getRoutePath()).isEqualTo("/applications/123/form");                  // query 제거
        assertThat(saved.getPrincipalHash()).isNull();
        assertThat(saved.getPrincipalType()).isNull();
        assertThat(saved.getMetadataJson()).contains("\"durationMs\":1250");
    }

    @Test
    void 인증_사용자는_HMAC_hash만_저장되고_원문은_저장되지_않는다() {
        when(repository.existsByClientSessionIdAndClientEventId(anyString(), anyString())).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "applicant01", "", "지원자", List.of(new SimpleGrantedAuthority("ROLE_APPLICANT")));

        service.record(request(ClientEventSource.APPLICANT_WEB, null, null), userDetails, servletRequest());

        ArgumentCaptor<ClientEventLog> captor = ArgumentCaptor.forClass(ClientEventLog.class);
        verify(repository).saveAndFlush(captor.capture());
        ClientEventLog saved = captor.getValue();
        assertThat(saved.getPrincipalHash())
                .isEqualTo(auditHmac.hmacHex("CLIENT_PRINCIPAL:applicant01"))
                .doesNotContain("applicant01");
        assertThat(saved.getPrincipalType()).isEqualTo("Employee"); // fromLdap 픽스처의 userType
    }

    @Test
    void source가_APPLICANT_WEB이_아니면_거부된다() {
        assertThatThrownBy(() -> service.record(
                request(ClientEventSource.ADMIN_WEB, null, null), null, servletRequest()))
                .isInstanceOf(InvalidClientEventLogException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void message의_7자리_이상_연속_숫자는_마스킹된다() {
        when(repository.existsByClientSessionIdAndClientEventId(anyString(), anyString())).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.record(request(ClientEventSource.APPLICANT_WEB, "submit failed 010-1234-5678 retry", null),
                null, servletRequest());

        ArgumentCaptor<ClientEventLog> captor = ArgumentCaptor.forClass(ClientEventLog.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getMessage())
                .isEqualTo("submit failed * retry")
                .doesNotContain("010")
                .doesNotContain("5678");
    }

    @Test
    void 중복_선확인되면_저장없이_duplicate_응답한다() {
        when(repository.existsByClientSessionIdAndClientEventId(anyString(), anyString())).thenReturn(true);

        ClientEventLogIngestResponse response = service.record(
                request(ClientEventSource.APPLICANT_WEB, null, null), null, servletRequest());

        assertThat(response.accepted()).isFalse();
        assertThat(response.duplicate()).isTrue();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void race_충돌도_duplicate_응답으로_흡수되고_예외가_전파되지_않는다() {
        when(repository.existsByClientSessionIdAndClientEventId(anyString(), anyString())).thenReturn(false);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk violation"));

        ClientEventLogIngestResponse response = service.record(
                request(ClientEventSource.APPLICANT_WEB, null, null), null, servletRequest());

        assertThat(response.accepted()).isFalse();
        assertThat(response.duplicate()).isTrue();
    }

    @Test
    void enabled가_꺼져있으면_저장하지_않는다() {
        ClientEventLogService disabled = new ClientEventLogService(
                repository, new ClientEventMetadataSanitizer(4000),
                new ClientEventRateLimiter(fixedClock, 1000, 1000, 1000), auditHmac, fixedClock, false);

        ClientEventLogIngestResponse response = disabled.record(
                request(ClientEventSource.APPLICANT_WEB, null, null), null, servletRequest());

        assertThat(response.accepted()).isFalse();
        assertThat(response.duplicate()).isFalse();
        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).existsByClientSessionIdAndClientEventId(anyString(), anyString());
    }
}
```

- [ ] **Step 2: 테스트가 컴파일 실패하는지 확인**

Run: `.\gradlew.bat compileTestJava --no-daemon`
Expected: FAIL — `ClientEventLogRequest`/`ClientEventLogService` 심볼 없음

- [ ] **Step 3: Request DTO 구현**

```java
// src/main/java/com/shinyoung/recruit/dto/request/ClientEventLogRequest.java
package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.ClientEventSeverity;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.enumeration.ClientEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * client event 수집 요청(Phase 09f, 설계 6.1).
 *
 * <p>{@code ipAddress}/{@code userAgent}/{@code principalHash}/{@code principalType}/{@code receivedAt}/
 * {@code ingestCorrelationId}는 body로 받지 않는다 — 서버에서만 채운다(FE 값 신뢰 금지).
 *
 * <p>{@code clientSessionId}/{@code clientEventId}는 UUID 등 안전한 opaque id 형식으로 제한한다
 * (unique 컬럼/rate limiter map key 오염 방지, 리뷰 Major 5). {@code message}는 자유 원문이 아니라
 * safe message code/고정 영문 문구만 허용한다(리뷰 Blocker 3) — 한글/{@code @} 등은 PII 혼입 신호로 400.
 */
public record ClientEventLogRequest(
        @NotNull ClientEventType eventType,
        @NotNull ClientEventSeverity severity,
        @NotNull ClientEventSource source,
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9\\-]{8,80}$", message = "clientSessionId 형식이 올바르지 않습니다.")
        String clientSessionId,
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9\\-]{8,80}$", message = "clientEventId 형식이 올바르지 않습니다.")
        String clientEventId,
        LocalDateTime clientOccurredAt,

        @Size(max = 100) String relatedCorrelationId,
        @Size(max = 80) String pageCode,
        @Size(max = 80) String componentCode,
        @Size(max = 300) String routePath,
        @Size(max = 80) String operation,

        Long jobPostingId,
        Long applicationId,

        @Size(max = 10) String httpMethod,
        @Size(max = 300) String apiPath,
        Integer httpStatus,
        @Size(max = 100) String errorCode,
        @Size(max = 200)
        @Pattern(regexp = "^[A-Za-z0-9 _.:\\-]*$", message = "message는 safe message code만 허용됩니다.")
        String message,
        @Size(max = 128) String stackHash,
        @Size(max = 2000) String stackSummary,

        @Size(max = 80) String frontendVersion,
        @Size(max = 80) String browserName,
        @Size(max = 80) String browserVersion,
        @Size(max = 80) String osName,
        @Size(max = 40) String viewport,
        @Size(max = 80) String timezone,

        Map<String, Object> metadata
) {
}
```

- [ ] **Step 4: Ingest Response DTO 구현**

```java
// src/main/java/com/shinyoung/recruit/dto/response/ClientEventLogIngestResponse.java
package com.shinyoung.recruit.dto.response;

/** 수집 결과(Phase 09f). FE telemetry는 fire-and-forget이라 이 값을 사용하지 않는다 — 운영 진단/테스트용. */
public record ClientEventLogIngestResponse(
        boolean accepted,
        boolean duplicate,
        Long id
) {

    public static ClientEventLogIngestResponse accepted(Long id) {
        return new ClientEventLogIngestResponse(true, false, id);
    }

    public static ClientEventLogIngestResponse duplicate() {
        return new ClientEventLogIngestResponse(false, true, null);
    }

    public static ClientEventLogIngestResponse disabled() {
        return new ClientEventLogIngestResponse(false, false, null);
    }
}
```

- [ ] **Step 5: Service 구현**

```java
// src/main/java/com/shinyoung/recruit/service/ClientEventLogService.java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.config.CorrelationIdFilter;
import com.shinyoung.recruit.domain.entity.ClientEventLog;
import com.shinyoung.recruit.domain.repository.ClientEventLogRepository;
import com.shinyoung.recruit.dto.request.ClientEventLogRequest;
import com.shinyoung.recruit.dto.response.ClientEventLogIngestResponse;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.exception.InvalidClientEventLogException;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * client event 수집 파이프라인(Phase 09f, 설계 6.2). best-effort — 수집 실패가 업무 API에
 * 영향을 주지 않고, 진단 로그 실패를 다시 진단 로그로 남기지 않는다.
 *
 * <p>의도적으로 {@code @Transactional}을 붙이지 않는다 — {@code saveAndFlush}의
 * {@link DataIntegrityViolationException}(중복 race)을 catch해 duplicate 성공 응답으로 흡수해야 하는데
 * (리뷰 Major 4), 외부 트랜잭션이 있으면 flush 시 rollback-only로 마킹돼 catch 후 정상 반환이
 * {@code UnexpectedRollbackException}으로 깨진다. repository 호출이 각자 자체 트랜잭션을 쓴다.
 */
@Service
public class ClientEventLogService {

    // 컬럼 길이와 동일(초과분 truncate). message만 safe code 계약에 따라 컬럼(500)보다 좁은 200.
    private static final int MAX_CORRELATION_ID = 100;
    private static final int MAX_PAGE_CODE = 80;
    private static final int MAX_COMPONENT_CODE = 80;
    private static final int MAX_ROUTE_PATH = 300;
    private static final int MAX_OPERATION = 80;
    private static final int MAX_HTTP_METHOD = 10;
    private static final int MAX_API_PATH = 300;
    private static final int MAX_ERROR_CODE = 100;
    private static final int MAX_MESSAGE = 200;
    private static final int MAX_STACK_HASH = 128;
    private static final int MAX_STACK_SUMMARY = 2000;
    private static final int MAX_FRONTEND_VERSION = 80;
    private static final int MAX_BROWSER = 80;
    private static final int MAX_OS = 80;
    private static final int MAX_VIEWPORT = 40;
    private static final int MAX_TIMEZONE = 80;
    private static final int MAX_IP_ADDRESS = 64;
    private static final int MAX_USER_AGENT = 512;

    /** 7자리 이상 연속 숫자(하이픈 개입 허용) — 전화번호류 혼입 마스킹(설계 6.3). */
    private static final String LONG_DIGIT_RUN = "[0-9][0-9\\-]{5,}[0-9]";

    private final ClientEventLogRepository repository;
    private final ClientEventMetadataSanitizer metadataSanitizer;
    private final ClientEventRateLimiter rateLimiter;
    private final AuditHmac auditHmac;
    private final Clock clock;
    private final boolean enabled;

    public ClientEventLogService(
            ClientEventLogRepository repository,
            ClientEventMetadataSanitizer metadataSanitizer,
            ClientEventRateLimiter rateLimiter,
            AuditHmac auditHmac,
            Clock clock,
            @Value("${client-event-log.enabled:true}") boolean enabled
    ) {
        this.repository = repository;
        this.metadataSanitizer = metadataSanitizer;
        this.rateLimiter = rateLimiter;
        this.auditHmac = auditHmac;
        this.clock = clock;
        this.enabled = enabled;
    }

    public ClientEventLogIngestResponse record(
            ClientEventLogRequest request,
            CustomUserDetails userDetails,
            HttpServletRequest servletRequest
    ) {
        if (!enabled) {
            return ClientEventLogIngestResponse.disabled();
        }
        if (request.source() != ClientEventSource.APPLICANT_WEB) {
            // public endpoint에서 ADMIN_WEB 진단 로그 위조 차단(리뷰 Blocker 1).
            throw new InvalidClientEventLogException("source는 APPLICANT_WEB만 허용됩니다.");
        }

        String ip = servletRequest.getRemoteAddr();
        String principalHash = userDetails == null
                ? null
                : auditHmac.hmacHex("CLIENT_PRINCIPAL:" + userDetails.getUsername());
        rateLimiter.check(ip, request.clientSessionId(), principalHash);

        if (repository.existsByClientSessionIdAndClientEventId(request.clientSessionId(), request.clientEventId())) {
            return ClientEventLogIngestResponse.duplicate();
        }

        String metadataJson = metadataSanitizer.sanitize(request.eventType(), request.metadata());
        ClientEventLog entity = ClientEventLog.builder()
                .receivedAt(LocalDateTime.now(clock))
                .clientOccurredAt(request.clientOccurredAt())
                .eventType(request.eventType())
                .severity(request.severity())
                .source(request.source())
                .clientSessionId(request.clientSessionId())
                .clientEventId(request.clientEventId())
                .ingestCorrelationId(safe(CorrelationIdFilter.currentCorrelationId(), MAX_CORRELATION_ID))
                .relatedCorrelationId(safe(request.relatedCorrelationId(), MAX_CORRELATION_ID))
                .pageCode(safe(request.pageCode(), MAX_PAGE_CODE))
                .componentCode(safe(request.componentCode(), MAX_COMPONENT_CODE))
                .routePath(safe(stripQuery(request.routePath()), MAX_ROUTE_PATH))
                .operation(safe(request.operation(), MAX_OPERATION))
                .jobPostingId(request.jobPostingId())
                .applicationId(request.applicationId())
                .httpMethod(safe(request.httpMethod(), MAX_HTTP_METHOD))
                .apiPath(safe(stripQuery(request.apiPath()), MAX_API_PATH))
                .httpStatus(request.httpStatus())
                .errorCode(safe(request.errorCode(), MAX_ERROR_CODE))
                .message(maskLongDigitRuns(safe(request.message(), MAX_MESSAGE)))
                .stackHash(safe(request.stackHash(), MAX_STACK_HASH))
                .stackSummary(safe(request.stackSummary(), MAX_STACK_SUMMARY))
                .frontendVersion(safe(request.frontendVersion(), MAX_FRONTEND_VERSION))
                .browserName(safe(request.browserName(), MAX_BROWSER))
                .browserVersion(safe(request.browserVersion(), MAX_BROWSER))
                .osName(safe(request.osName(), MAX_OS))
                .viewport(safe(request.viewport(), MAX_VIEWPORT))
                .timezone(safe(request.timezone(), MAX_TIMEZONE))
                .ipAddress(safe(ip, MAX_IP_ADDRESS))
                .userAgent(safe(servletRequest.getHeader("User-Agent"), MAX_USER_AGENT))
                .principalHash(principalHash)
                .principalType(userDetails == null ? null : userDetails.getUserType())
                .metadataJson(metadataJson)
                .build();

        try {
            ClientEventLog saved = repository.saveAndFlush(entity);
            return ClientEventLogIngestResponse.accepted(saved.getId());
        } catch (DataIntegrityViolationException e) {
            // 동시 재전송 race — unique(client_session_id, client_event_id)가 최종 방어선.
            return ClientEventLogIngestResponse.duplicate();
        }
    }

    /** CR/LF/TAB 등 제어문자 → 공백 치환 후 trim, 길이 초과분 truncate. blank면 null. */
    private String safe(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = value.replaceAll("\\p{Cntrl}", " ").trim();
        if (sanitized.isBlank()) {
            return null;
        }
        return sanitized.length() <= max ? sanitized : sanitized.substring(0, max);
    }

    private String stripQuery(String path) {
        if (path == null) {
            return null;
        }
        int queryIndex = path.indexOf('?');
        return queryIndex < 0 ? path : path.substring(0, queryIndex);
    }

    private String maskLongDigitRuns(String message) {
        if (message == null) {
            return null;
        }
        return message.replaceAll(LONG_DIGIT_RUN, "*");
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `.\gradlew.bat test --tests "com.shinyoung.recruit.service.ClientEventLogServiceTest" --no-daemon`
Expected: PASS (7 tests)

- [ ] **Step 7: Commit**

```
git add src/main/java/com/shinyoung/recruit/dto/request/ClientEventLogRequest.java src/main/java/com/shinyoung/recruit/dto/response/ClientEventLogIngestResponse.java src/main/java/com/shinyoung/recruit/service/ClientEventLogService.java src/test/java/com/shinyoung/recruit/service/ClientEventLogServiceTest.java
git commit -m "9f-1 수집 DTO/Service"
```

---

### Task 6: Controller + SecurityConfig/CORS 보강 + 통합 테스트

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/controller/ClientEventLogController.java`
- Modify: `src/main/java/com/shinyoung/recruit/config/SecurityConfig.java` (corsConfigurationSource ~59-61행, filterChain ~82행 뒤)
- Test: `src/test/java/com/shinyoung/recruit/controller/ClientEventLogControllerTest.java`
- Test: `src/test/java/com/shinyoung/recruit/controller/ClientEventLogRateLimitControllerTest.java`

- [ ] **Step 1: 실패하는 컨트롤러 테스트 작성**

```java
// src/test/java/com/shinyoung/recruit/controller/ClientEventLogControllerTest.java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 수집 API(Phase 09f-1) — permitAll, source 위조 거부(리뷰 Blocker 1), metadata allowlist(Blocker 2),
 * safe message code(Blocker 3), 중복 duplicate 흡수(409 누출 없음, Major 4), JSON-only 계약.
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ClientEventLogControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private String body(String source, String sessionId, String eventId, String extraJsonFields) {
        return """
                {
                  "eventType": "API_ERROR",
                  "severity": "ERROR",
                  "source": "%s",
                  "clientSessionId": "%s",
                  "clientEventId": "%s"%s
                }
                """.formatted(source, sessionId, eventId,
                extraJsonFields.isEmpty() ? "" : ",\n" + extraJsonFields);
    }

    private Authentication applicantAuthentication() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "applicant01", "", "지원자", List.of(new SimpleGrantedAuthority("ROLE_APPLICANT")));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Test
    void 미인증_상태에서도_수집된다() throws Exception {
        mockMvc.perform(post("/api/client-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("APPLICANT_WEB", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accepted").value(true))
                .andExpect(jsonPath("$.data.duplicate").value(false))
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    void 인증_상태에서도_수집된다() throws Exception {
        mockMvc.perform(post("/api/client-events")
                        .with(authentication(applicantAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("APPLICANT_WEB", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(true));
    }

    @Test
    void anonymous가_ADMIN_WEB_source를_보내면_400이다() throws Exception {
        mockMvc.perform(post("/api/client-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ADMIN_WEB", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 인증_사용자도_ADMIN_WEB_source는_400이다() throws Exception {
        mockMvc.perform(post("/api/client-events")
                        .with(authentication(applicantAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ADMIN_WEB", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clientSessionId_형식_위반은_400이다() throws Exception {
        mockMvc.perform(post("/api/client-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("APPLICANT_WEB", "한글세션아이디!!", UUID.randomUUID().toString(), "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 허용되지_않은_metadata_key는_400이다() throws Exception {
        mockMvc.perform(post("/api/client-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("APPLICANT_WEB", UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                "\"metadata\": {\"phoneNumber\": \"010-1234-5678\"}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void message에_한글이_섞이면_400이다() throws Exception {
        mockMvc.perform(post("/api/client-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("APPLICANT_WEB", UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                "\"message\": \"홍길동 지원자 저장 실패\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 같은_이벤트_재전송은_duplicate로_흡수되고_409로_새지_않는다() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/client-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("APPLICANT_WEB", sessionId, eventId, "")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/client-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("APPLICANT_WEB", sessionId, eventId, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accepted").value(false))
                .andExpect(jsonPath("$.data.duplicate").value(true));
    }

    @Test
    void JSON이_아닌_content_type은_415다() throws Exception {
        mockMvc.perform(post("/api/client-events")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not-json"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void CORS_응답에_X_Request_Id가_노출된다() throws Exception {
        mockMvc.perform(post("/api/client-events")
                        .header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("APPLICANT_WEB", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "")))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Expose-Headers", "X-Request-Id"));
    }
}
```

```java
// src/test/java/com/shinyoung/recruit/controller/ClientEventLogRateLimitControllerTest.java
package com.shinyoung.recruit.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * rate limit 429(Phase 09f-1). in-memory limiter 상태가 컨텍스트 단위로 공유되므로
 * 낮은 한도 properties로 별도 컨텍스트를 만들어 다른 테스트와 격리한다.
 */
@SpringBootTest(properties = {
        "crypto.aes.key=22791194512954214612461221261067",
        "client-event-log.rate-limit.per-minute-ip=2",
        "client-event-log.rate-limit.per-minute-session=2",
        "client-event-log.rate-limit.per-minute-principal=2"
})
@Transactional
class ClientEventLogRateLimitControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private String body(String sessionId) {
        return """
                {
                  "eventType": "API_ERROR",
                  "severity": "ERROR",
                  "source": "APPLICANT_WEB",
                  "clientSessionId": "%s",
                  "clientEventId": "%s"
                }
                """.formatted(sessionId, UUID.randomUUID().toString());
    }

    @Test
    void ip_한도_초과는_sessionId를_바꿔도_429다() throws Exception {
        // per-minute-ip=2 — sessionId를 매번 바꿔 session 한도를 피해도 ip 한도가 차단한다(리뷰 Major 5).
        mockMvc.perform(post("/api/client-events").contentType(MediaType.APPLICATION_JSON)
                .content(body(UUID.randomUUID().toString()))).andExpect(status().isOk());
        mockMvc.perform(post("/api/client-events").contentType(MediaType.APPLICATION_JSON)
                .content(body(UUID.randomUUID().toString()))).andExpect(status().isOk());

        mockMvc.perform(post("/api/client-events").contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID().toString())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인** (404 — 엔드포인트 부재)

Run: `.\gradlew.bat test --tests "com.shinyoung.recruit.controller.ClientEventLogControllerTest" --no-daemon`
Expected: FAIL — 404 또는 401 응답(엔드포인트/matcher 부재)

- [ ] **Step 3: Controller 구현**

```java
// src/main/java/com/shinyoung/recruit/controller/ClientEventLogController.java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ClientEventLogRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ClientEventLogIngestResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ClientEventLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * client event 수집 API(Phase 09f-1, 설계 6.1). public write endpoint —
 * permitAll(로그인 전/세션 만료/회원가입 화면 오류도 수집)이며 JSON-only 계약으로 고정한다.
 * 인증 정보/IP/User-Agent는 service가 서버에서만 추출한다(FE body 값 신뢰 금지).
 */
@RestController
@RequiredArgsConstructor
public class ClientEventLogController {

    private final ClientEventLogService clientEventLogService;

    @PostMapping(value = "/client-events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ClientEventLogIngestResponse>> record(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ClientEventLogRequest request,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                clientEventLogService.record(request, userDetails, servletRequest)));
    }
}
```

- [ ] **Step 4: SecurityConfig 보강 (2곳)**

(1) `corsConfigurationSource()`의 `setAllowedHeaders(...)` 호출 바로 뒤에 추가:

```java
        // FE가 실패 업무 API 응답에서 X-Request-Id를 읽어 relatedCorrelationId로 보낸다(Phase 09f, 설계 7장).
        corsConfiguration.setExposedHeaders(List.of("X-Request-Id"));
```

(2) `filterChain`의 `.requestMatchers(HttpMethod.GET, "/api/job-postings/**").permitAll()` 라인 바로 뒤에 추가:

```java
                // client event 수집(Phase 09f) — 로그인 전/세션 만료 오류도 수집하므로 permitAll(설계 7장).
                // anyRequest().permitAll()이 있어도 의도를 명시적으로 고정한다.
                .requestMatchers(HttpMethod.POST, "/api/client-events").permitAll()
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `.\gradlew.bat test --tests "com.shinyoung.recruit.controller.ClientEventLogControllerTest" --tests "com.shinyoung.recruit.controller.ClientEventLogRateLimitControllerTest" --no-daemon`
Expected: PASS (11 tests)

- [ ] **Step 6: Commit**

```
git add src/main/java/com/shinyoung/recruit/controller/ClientEventLogController.java src/main/java/com/shinyoung/recruit/config/SecurityConfig.java src/test/java/com/shinyoung/recruit/controller/ClientEventLogControllerTest.java src/test/java/com/shinyoung/recruit/controller/ClientEventLogRateLimitControllerTest.java
git commit -m "9f-1 수집 controller + security/cors 보강"
```

---

### Task 7: Slice 09f-1 문서화

**Files:**
- Create: `docs/codex/implementation/phase-09f-1-client-event-ingest.md`
- Create: `docs/codex/reports/phase-09f-1-client-event-ingest.html`
- Modify: `docs/codex/07-implementation-history.md`

- [ ] **Step 1: 구현 문서 작성** — `docs/codex/implementation/phase-09f-1-client-event-ingest.md`

CLAUDE.md 규정 12개 섹션(Phase summary / Implemented scope / Changed files / New classes / Modified classes / Class-by-class explanation / API list / Entity relationship summary / Business rules / Test coverage / Known limitations / Next phase considerations)을 모두 채운다. Class-by-class에는 Task 1~6에서 만든 클래스 전부(package, class name, class type, responsibility, key fields or methods, related classes, important implementation notes)를 기록한다. 설계 결정 근거는 `docs/codex/design/phase-09f-client-event-log-be-design.md`의 6장(특히 saveAndFlush 이유, 3단 rate limit, exact allowlist)을 인용한다. 실제 구현/테스트 결과만 기록하고 추측성 서술 금지.

- [ ] **Step 2: HTML 리포트 생성** — `docs/codex/reports/phase-09f-1-client-event-ingest.html`

`docs/codex/templates/human-report-template.md`를 따라 Step 1 markdown 내용에서 생성한다. self-contained(inline CSS, 외부 CDN/JS 금지), 상태 배지(Completed/Pending/Out of scope/Warning), 섹션: 완료 범위 / 미구현 범위(09f-3, 09f-4) / API 목록 / 변경 파일 / 도메인 구조 / 검증 규칙 / 테스트 결과 / 남은 이슈 / 다음 단계. secret/내부 경로/민감 데이터 노출 금지.

- [ ] **Step 3: 구현 이력 갱신** — `docs/codex/07-implementation-history.md`

기존 항목 형식대로 `## 2026-06-10 - Phase 09f-1 Client Event Ingest Foundation` 항목 추가(Scope/Implemented/APIs/Business rules/Tests/Documentation/Deferred/Next).

- [ ] **Step 4: Commit**

```
git add docs/codex/implementation/phase-09f-1-client-event-ingest.md docs/codex/reports/phase-09f-1-client-event-ingest.html docs/codex/07-implementation-history.md
git commit -m "9f-1 구현 문서/리포트"
```

---

# Slice 09f-3 — Admin Read API

### Task 8: 조회 예외 2종 + 핸들러 매핑 + Repository search 쿼리

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/exception/ClientEventLogNotFoundException.java`
- Create: `src/main/java/com/shinyoung/recruit/exception/InvalidClientEventQueryException.java`
- Modify: `src/main/java/com/shinyoung/recruit/exception/GlobalExceptionHandler.java` (Task 1에서 추가한 handleClientEventRateLimitExceeded 뒤)
- Modify: `src/main/java/com/shinyoung/recruit/domain/repository/ClientEventLogRepository.java`
- Test: `src/test/java/com/shinyoung/recruit/domain/repository/ClientEventLogRepositoryTest.java` (search 테스트 추가)

- [ ] **Step 1: 예외 2종 작성**

```java
// src/main/java/com/shinyoung/recruit/exception/ClientEventLogNotFoundException.java
package com.shinyoung.recruit.exception;

public class ClientEventLogNotFoundException extends RuntimeException {

    public ClientEventLogNotFoundException(String message) {
        super(message);
    }
}
```

```java
// src/main/java/com/shinyoung/recruit/exception/InvalidClientEventQueryException.java
package com.shinyoung.recruit.exception;

public class InvalidClientEventQueryException extends RuntimeException {

    public InvalidClientEventQueryException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: GlobalExceptionHandler 매핑 추가** (Task 1 추가분 바로 뒤)

```java
    @ExceptionHandler(InvalidClientEventQueryException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidClientEventQuery(InvalidClientEventQueryException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(ClientEventLogNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleClientEventLogNotFound(ClientEventLogNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(e.getMessage()));
    }
```

- [ ] **Step 3: 실패하는 search 테스트 추가** — `ClientEventLogRepositoryTest`에 메서드 추가

```java
    @Test
    void search는_필터와_최신순_정렬을_지원한다() {
        clientEventLogRepository.save(baseBuilder("session-0001", "event-0001")
                .applicationId(123L).build());
        clientEventLogRepository.save(ClientEventLog.builder()
                .receivedAt(LocalDateTime.of(2026, 6, 10, 13, 0))
                .eventType(ClientEventType.JS_ERROR)
                .severity(ClientEventSeverity.ERROR)
                .source(ClientEventSource.APPLICANT_WEB)
                .clientSessionId("session-0002")
                .clientEventId("event-0002")
                .applicationId(123L)
                .build());

        var page = clientEventLogRepository.search(
                LocalDateTime.of(2026, 6, 10, 0, 0), LocalDateTime.of(2026, 6, 11, 0, 0),
                null, null, null, 123L, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        // receivedAt DESC — 13:00 이벤트가 먼저
        assertThat(page.getContent().get(0).getClientEventId()).isEqualTo("event-0002");

        var filtered = clientEventLogRepository.search(
                LocalDateTime.of(2026, 6, 10, 0, 0), LocalDateTime.of(2026, 6, 11, 0, 0),
                ClientEventType.JS_ERROR, null, null, null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(filtered.getTotalElements()).isEqualTo(1);
    }
```

- [ ] **Step 4: 컴파일 실패 확인**

Run: `.\gradlew.bat compileTestJava --no-daemon`
Expected: FAIL — `search` 메서드 없음

- [ ] **Step 5: Repository에 search 추가** — `ClientEventLogRepository`에 메서드/임포트 추가

```java
    /** client event 검색(09f-3 read API). receivedAt 범위는 필수(가드는 서비스에서). 최신순 고정 정렬. */
    @Query("""
            SELECT c FROM ClientEventLog c
            WHERE c.receivedAt >= :from AND c.receivedAt <= :to
              AND (:eventType IS NULL OR c.eventType = :eventType)
              AND (:severity IS NULL OR c.severity = :severity)
              AND (:source IS NULL OR c.source = :source)
              AND (:applicationId IS NULL OR c.applicationId = :applicationId)
              AND (:jobPostingId IS NULL OR c.jobPostingId = :jobPostingId)
              AND (:clientSessionId IS NULL OR c.clientSessionId = :clientSessionId)
              AND (:relatedCorrelationId IS NULL OR c.relatedCorrelationId = :relatedCorrelationId)
            ORDER BY c.receivedAt DESC, c.id DESC
            """)
    Page<ClientEventLog> search(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("eventType") ClientEventType eventType,
            @Param("severity") ClientEventSeverity severity,
            @Param("source") ClientEventSource source,
            @Param("applicationId") Long applicationId,
            @Param("jobPostingId") Long jobPostingId,
            @Param("clientSessionId") String clientSessionId,
            @Param("relatedCorrelationId") String relatedCorrelationId,
            Pageable pageable
    );
```

추가 임포트:

```java
import com.shinyoung.recruit.enumeration.ClientEventSeverity;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.enumeration.ClientEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `.\gradlew.bat test --tests "com.shinyoung.recruit.domain.repository.ClientEventLogRepositoryTest" --no-daemon`
Expected: PASS (5 tests)

- [ ] **Step 7: Commit**

```
git add src/main/java/com/shinyoung/recruit/exception/ClientEventLogNotFoundException.java src/main/java/com/shinyoung/recruit/exception/InvalidClientEventQueryException.java src/main/java/com/shinyoung/recruit/exception/GlobalExceptionHandler.java src/main/java/com/shinyoung/recruit/domain/repository/ClientEventLogRepository.java src/test/java/com/shinyoung/recruit/domain/repository/ClientEventLogRepositoryTest.java
git commit -m "9f-3 조회 예외/repository search"
```

---

### Task 9: ClientEventLogResponse + ClientEventLogReadService

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/dto/response/ClientEventLogResponse.java`
- Create: `src/main/java/com/shinyoung/recruit/service/ClientEventLogReadService.java`
- Test: `src/test/java/com/shinyoung/recruit/service/ClientEventLogReadServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
// src/test/java/com/shinyoung/recruit/service/ClientEventLogReadServiceTest.java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ClientEventLog;
import com.shinyoung.recruit.domain.repository.ClientEventLogRepository;
import com.shinyoung.recruit.dto.response.ClientEventLogResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.ClientEventSeverity;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.enumeration.ClientEventType;
import com.shinyoung.recruit.exception.ClientEventLogNotFoundException;
import com.shinyoung.recruit.exception.InvalidClientEventQueryException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** read 가드(설계 8.2 — default 7일/max 90일/size 100) + 권한별 마스킹(8.3 — stackSummary 포함, 리뷰 Blocker 3). */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ClientEventLogReadServiceTest {

    @Autowired
    ClientEventLogReadService readService;

    @Autowired
    ClientEventLogRepository repository;

    private ClientEventLog seed(String sessionId, LocalDateTime receivedAt) {
        return repository.save(ClientEventLog.builder()
                .receivedAt(receivedAt)
                .eventType(ClientEventType.API_ERROR)
                .severity(ClientEventSeverity.ERROR)
                .source(ClientEventSource.APPLICANT_WEB)
                .clientSessionId(sessionId)
                .clientEventId("event-" + sessionId)
                .ipAddress("10.0.0.1")
                .userAgent("test-agent")
                .principalHash("abc123hash")
                .stackSummary("at submit (app.js:1)")
                .build());
    }

    @Test
    void 범위_미지정이면_최근_7일만_조회된다() {
        seed("session-0001", LocalDateTime.now().minusDays(1));
        seed("session-0002", LocalDateTime.now().minusDays(10)); // default range(7일) 밖

        PageResponse<ClientEventLogResponse> result = readService.search(
                null, null, null, null, null, null, null, null, null, 0, 20, false);

        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void includeSensitive가_false면_민감_필드가_마스킹된다() {
        seed("session-0001", LocalDateTime.now().minusDays(1));

        ClientEventLogResponse response = readService.search(
                null, null, null, null, null, null, null, null, null, 0, 20, false).content().get(0);

        assertThat(response.ipAddress()).isEqualTo("***");
        assertThat(response.userAgent()).isEqualTo("***");
        assertThat(response.principalHash()).isEqualTo("***");
        assertThat(response.stackSummary()).isEqualTo("***");
    }

    @Test
    void includeSensitive가_true면_원문을_본다() {
        seed("session-0001", LocalDateTime.now().minusDays(1));

        ClientEventLogResponse response = readService.search(
                null, null, null, null, null, null, null, null, null, 0, 20, true).content().get(0);

        assertThat(response.ipAddress()).isEqualTo("10.0.0.1");
        assertThat(response.userAgent()).isEqualTo("test-agent");
        assertThat(response.principalHash()).isEqualTo("abc123hash");
        assertThat(response.stackSummary()).isEqualTo("at submit (app.js:1)");
    }

    @Test
    void 범위가_90일을_넘으면_거부된다() {
        assertThatThrownBy(() -> readService.search(
                null, null, null, null, null, null, null,
                LocalDateTime.now().minusDays(120), LocalDateTime.now(), 0, 20, false))
                .isInstanceOf(InvalidClientEventQueryException.class);
    }

    @Test
    void size가_100을_넘으면_거부된다() {
        assertThatThrownBy(() -> readService.search(
                null, null, null, null, null, null, null, null, null, 0, 101, false))
                .isInstanceOf(InvalidClientEventQueryException.class);
    }

    @Test
    void 없는_id_단건_조회는_NotFound_예외다() {
        assertThatThrownBy(() -> readService.getEvent(999999L, false))
                .isInstanceOf(ClientEventLogNotFoundException.class);
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `.\gradlew.bat compileTestJava --no-daemon`
Expected: FAIL — `ClientEventLogReadService`/`ClientEventLogResponse` 심볼 없음

- [ ] **Step 3: Response DTO 구현**

```java
// src/main/java/com/shinyoung/recruit/dto/response/ClientEventLogResponse.java
package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ClientEventLog;
import com.shinyoung.recruit.enumeration.ClientEventSeverity;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.enumeration.ClientEventType;

import java.time.LocalDateTime;

/**
 * client event read API 응답(Phase 09f-3, 설계 8.3). 권한별 projection —
 * {@code ipAddress}/{@code userAgent}/{@code principalHash}/{@code stackSummary}는 ROLE_PRIVACY_ADMIN만
 * 원문을 본다(stackSummary는 자유 문자열 성격이라 PII 혼입 가능성 때문에 포함, 리뷰 Blocker 3).
 * {@code message}(safe code 강제)/{@code metadataJson}(exact allowlist)/{@code stackHash}(group by용)는
 * 수집 시점에 PII가 차단되므로 양 권한 노출.
 */
public record ClientEventLogResponse(
        Long id,
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

    private static final String MASKED = "***";

    public static ClientEventLogResponse from(ClientEventLog log, boolean includeSensitive) {
        return new ClientEventLogResponse(
                log.getId(),
                log.getReceivedAt(),
                log.getClientOccurredAt(),
                log.getEventType(),
                log.getSeverity(),
                log.getSource(),
                log.getClientSessionId(),
                log.getClientEventId(),
                log.getIngestCorrelationId(),
                log.getRelatedCorrelationId(),
                log.getPageCode(),
                log.getComponentCode(),
                log.getRoutePath(),
                log.getOperation(),
                log.getJobPostingId(),
                log.getApplicationId(),
                log.getHttpMethod(),
                log.getApiPath(),
                log.getHttpStatus(),
                log.getErrorCode(),
                log.getMessage(),
                log.getStackHash(),
                mask(log.getStackSummary(), includeSensitive),
                log.getFrontendVersion(),
                log.getBrowserName(),
                log.getBrowserVersion(),
                log.getOsName(),
                log.getViewport(),
                log.getTimezone(),
                mask(log.getIpAddress(), includeSensitive),
                mask(log.getUserAgent(), includeSensitive),
                mask(log.getPrincipalHash(), includeSensitive),
                log.getPrincipalType(),
                log.getMetadataJson()
        );
    }

    private static String mask(String value, boolean includeSensitive) {
        if (value == null || includeSensitive) {
            return value;
        }
        return MASKED;
    }
}
```

- [ ] **Step 4: ReadService 구현**

```java
// src/main/java/com/shinyoung/recruit/service/ClientEventLogReadService.java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.repository.ClientEventLogRepository;
import com.shinyoung.recruit.dto.response.ClientEventLogResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.ClientEventSeverity;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.enumeration.ClientEventType;
import com.shinyoung.recruit.exception.ClientEventLogNotFoundException;
import com.shinyoung.recruit.exception.InvalidClientEventQueryException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * client event read API 서비스(Phase 09f-3). {@code AuditActivityReadService}(09b) 가드 패턴 —
 * page size 상한 / 범위 상한(90일) / default 범위. 진단 로그라 default는 감사(30일)보다 짧은
 * 최근 {@value #DEFAULT_RANGE_DAYS}일이다(설계 8.2).
 */
@Service
@Transactional(readOnly = true)
public class ClientEventLogReadService {

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_RANGE_DAYS = 90;
    static final int DEFAULT_RANGE_DAYS = 7;

    private final ClientEventLogRepository repository;
    private final Clock clock;

    public ClientEventLogReadService(ClientEventLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public PageResponse<ClientEventLogResponse> search(
            ClientEventType eventType,
            ClientEventSeverity severity,
            ClientEventSource source,
            Long applicationId,
            Long jobPostingId,
            String clientSessionId,
            String relatedCorrelationId,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size,
            boolean includeSensitive
    ) {
        validatePaging(page, size);
        LocalDateTime effectiveTo = to != null ? to : LocalDateTime.now(clock);
        LocalDateTime effectiveFrom = from != null ? from : effectiveTo.minusDays(DEFAULT_RANGE_DAYS);
        validateRange(effectiveFrom, effectiveTo);

        return PageResponse.from(repository.search(
                        effectiveFrom,
                        effectiveTo,
                        eventType,
                        severity,
                        source,
                        applicationId,
                        jobPostingId,
                        normalize(clientSessionId),
                        normalize(relatedCorrelationId),
                        PageRequest.of(page, size))
                .map(log -> ClientEventLogResponse.from(log, includeSensitive)));
    }

    public ClientEventLogResponse getEvent(Long id, boolean includeSensitive) {
        return repository.findById(id)
                .map(log -> ClientEventLogResponse.from(log, includeSensitive))
                .orElseThrow(() -> new ClientEventLogNotFoundException("Client event log was not found."));
    }

    private void validatePaging(int page, int size) {
        if (page < 0) {
            throw new InvalidClientEventQueryException("page는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidClientEventQueryException("size는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다.");
        }
    }

    private void validateRange(LocalDateTime from, LocalDateTime to) {
        if (from.isAfter(to)) {
            throw new InvalidClientEventQueryException("receivedAt 검색 범위가 올바르지 않습니다(from > to).");
        }
        // toDays()는 소수 일수를 버려 경계가 새므로 Duration 자체를 비교한다(09b 선례).
        if (Duration.between(from, to).compareTo(Duration.ofDays(MAX_RANGE_DAYS)) > 0) {
            throw new InvalidClientEventQueryException("receivedAt 검색 범위는 최대 " + MAX_RANGE_DAYS + "일입니다.");
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `.\gradlew.bat test --tests "com.shinyoung.recruit.service.ClientEventLogReadServiceTest" --no-daemon`
Expected: PASS (6 tests)

- [ ] **Step 6: Commit**

```
git add src/main/java/com/shinyoung/recruit/dto/response/ClientEventLogResponse.java src/main/java/com/shinyoung/recruit/service/ClientEventLogReadService.java src/test/java/com/shinyoung/recruit/service/ClientEventLogReadServiceTest.java
git commit -m "9f-3 read service/response projection"
```

---

### Task 10: AdminClientEventLogController + Security matcher + 통합 테스트

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/controller/AdminClientEventLogController.java`
- Modify: `src/main/java/com/shinyoung/recruit/config/SecurityConfig.java` (broad `/api/admin/**` 라인 앞)
- Test: `src/test/java/com/shinyoung/recruit/controller/AdminClientEventLogControllerTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
// src/test/java/com/shinyoung/recruit/controller/AdminClientEventLogControllerTest.java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.domain.entity.ClientEventLog;
import com.shinyoung.recruit.domain.repository.ClientEventLogRepository;
import com.shinyoung.recruit.enumeration.ClientEventSeverity;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.enumeration.ClientEventType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 09f-3 관리자 조회 — narrow matcher 권한 + 권한별 마스킹(stackSummary 포함) + 가드 + 404. */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class AdminClientEventLogControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ClientEventLogRepository repository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private ClientEventLog seed() {
        return repository.save(ClientEventLog.builder()
                .receivedAt(LocalDateTime.now().minusDays(1))
                .eventType(ClientEventType.API_ERROR)
                .severity(ClientEventSeverity.ERROR)
                .source(ClientEventSource.APPLICANT_WEB)
                .clientSessionId("session-0001")
                .clientEventId("event-0001")
                .applicationId(123L)
                .ipAddress("10.0.0.1")
                .userAgent("test-agent")
                .principalHash("abc123hash")
                .stackSummary("at submit (app.js:1)")
                .build());
    }

    private Authentication auth(String... roles) {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "admin01", "인사팀", "관리자",
                List.of(roles).stream().map(SimpleGrantedAuthority::new).toList());
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Test
    void RECRUIT_ADMIN은_민감_필드가_마스킹된다() throws Exception {
        seed();

        mockMvc.perform(get("/api/admin/client-events")
                        .with(authentication(auth("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].ipAddress").value("***"))
                .andExpect(jsonPath("$.data.content[0].userAgent").value("***"))
                .andExpect(jsonPath("$.data.content[0].principalHash").value("***"))
                .andExpect(jsonPath("$.data.content[0].stackSummary").value("***"))
                .andExpect(jsonPath("$.data.content[0].clientSessionId").value("session-0001"));
    }

    @Test
    void PRIVACY_ADMIN은_원문을_본다() throws Exception {
        seed();

        mockMvc.perform(get("/api/admin/client-events")
                        .with(authentication(auth("ROLE_PRIVACY_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].ipAddress").value("10.0.0.1"))
                .andExpect(jsonPath("$.data.content[0].stackSummary").value("at submit (app.js:1)"));
    }

    @Test
    void 단건_조회와_404를_지원한다() throws Exception {
        ClientEventLog saved = seed();

        mockMvc.perform(get("/api/admin/client-events/" + saved.getId())
                        .with(authentication(auth("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientEventId").value("event-0001"));

        mockMvc.perform(get("/api/admin/client-events/999999")
                        .with(authentication(auth("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void applicationId_필터가_동작한다() throws Exception {
        seed();

        mockMvc.perform(get("/api/admin/client-events")
                        .param("applicationId", "999")
                        .with(authentication(auth("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void size_초과는_400이다() throws Exception {
        mockMvc.perform(get("/api/admin/client-events")
                        .param("size", "101")
                        .with(authentication(auth("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void APPLICANT_권한은_403이다() throws Exception {
        mockMvc.perform(get("/api/admin/client-events")
                        .with(authentication(auth("ROLE_APPLICANT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void 미인증은_401이다() throws Exception {
        mockMvc.perform(get("/api/admin/client-events"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `.\gradlew.bat test --tests "com.shinyoung.recruit.controller.AdminClientEventLogControllerTest" --no-daemon`
Expected: FAIL — 404/403(엔드포인트·matcher 부재)

- [ ] **Step 3: Controller 구현**

```java
// src/main/java/com/shinyoung/recruit/controller/AdminClientEventLogController.java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ClientEventLogResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.ClientEventSeverity;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.enumeration.ClientEventType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ClientEventLogReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 관리자 client event 조회 API(Phase 09f-3). 접근은 SecurityConfig narrow matcher
 * ({@code GET /api/admin/client-events/**} → RECRUIT_ADMIN/PRIVACY_ADMIN)가 게이팅하고,
 * 민감 필드(ip/ua/principalHash/stackSummary) 원문은 권한별 projection으로 추가 게이팅한다
 * ({@code AdminAuditController} 선례).
 */
@RestController
@RequiredArgsConstructor
public class AdminClientEventLogController {

    private static final String ROLE_PRIVACY_ADMIN = "ROLE_PRIVACY_ADMIN";

    private final ClientEventLogReadService clientEventLogReadService;

    @GetMapping("/admin/client-events")
    public ResponseEntity<ApiResponse<PageResponse<ClientEventLogResponse>>> searchEvents(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) ClientEventType eventType,
            @RequestParam(required = false) ClientEventSeverity severity,
            @RequestParam(required = false) ClientEventSource source,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) Long jobPostingId,
            @RequestParam(required = false) String clientSessionId,
            @RequestParam(required = false) String relatedCorrelationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(clientEventLogReadService.search(
                eventType, severity, source, applicationId, jobPostingId, clientSessionId,
                relatedCorrelationId, from, to, page, size, includeSensitive(userDetails))));
    }

    @GetMapping("/admin/client-events/{id}")
    public ResponseEntity<ApiResponse<ClientEventLogResponse>> getEvent(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                clientEventLogReadService.getEvent(id, includeSensitive(userDetails))));
    }

    /** 민감 필드 원문은 ROLE_PRIVACY_ADMIN 전용. principal 부재 시 항상 마스킹(심층 방어). */
    private boolean includeSensitive(CustomUserDetails userDetails) {
        if (userDetails == null) {
            return false;
        }
        return userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_PRIVACY_ADMIN::equals);
    }
}
```

- [ ] **Step 4: SecurityConfig matcher 추가** — broad `.requestMatchers("/api/admin/**")` 라인 **바로 앞**에 삽입(narrow-before-broad, 기존 audit 관례):

```java
                // client event 조회(Phase 09f-3) — broad /api/admin/** 보다 먼저(순서가 보안 요구사항).
                // 민감 필드 원문 vs 마스킹은 컨트롤러에서 권한별 projection으로 추가 게이팅한다.
                .requestMatchers(HttpMethod.GET, "/api/admin/client-events/**").hasAnyAuthority("ROLE_RECRUIT_ADMIN", "ROLE_PRIVACY_ADMIN")
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `.\gradlew.bat test --tests "com.shinyoung.recruit.controller.AdminClientEventLogControllerTest" --no-daemon`
Expected: PASS (7 tests)

- [ ] **Step 6: Commit**

```
git add src/main/java/com/shinyoung/recruit/controller/AdminClientEventLogController.java src/main/java/com/shinyoung/recruit/config/SecurityConfig.java src/test/java/com/shinyoung/recruit/controller/AdminClientEventLogControllerTest.java
git commit -m "9f-3 admin 조회 controller/matcher"
```

---

### Task 11: Slice 09f-3 문서화

**Files:**
- Create: `docs/codex/implementation/phase-09f-3-admin-client-event-read.md`
- Create: `docs/codex/reports/phase-09f-3-admin-client-event-read.html`
- Modify: `docs/codex/07-implementation-history.md`

- [ ] **Step 1: 구현 문서 작성** — Task 7과 동일한 12개 섹션 구성으로 Task 8~10 산출물을 기록한다. 마스킹 정책 표(필드별 RECRUIT_ADMIN/PRIVACY_ADMIN 노출)와 가드 값(7/90/100)을 명시한다.

- [ ] **Step 2: HTML 리포트 생성** — `docs/codex/templates/human-report-template.md` 기준, Step 1 markdown에서 생성. 미구현 범위는 09f-4.

- [ ] **Step 3: 구현 이력 갱신** — `## 2026-06-10 - Phase 09f-3 Admin Client Event Read API` 항목 추가.

- [ ] **Step 4: Commit**

```
git add docs/codex/implementation/phase-09f-3-admin-client-event-read.md docs/codex/reports/phase-09f-3-admin-client-event-read.html docs/codex/07-implementation-history.md
git commit -m "9f-3 구현 문서/리포트"
```

---

# Slice 09f-4 — Retention

### Task 12: bulk delete + ClientEventLogCleanupService

**Files:**
- Modify: `src/main/java/com/shinyoung/recruit/domain/repository/ClientEventLogRepository.java`
- Create: `src/main/java/com/shinyoung/recruit/service/ClientEventLogCleanupService.java`
- Test: `src/test/java/com/shinyoung/recruit/service/ClientEventLogCleanupServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
// src/test/java/com/shinyoung/recruit/service/ClientEventLogCleanupServiceTest.java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ClientEventLog;
import com.shinyoung.recruit.domain.repository.ClientEventLogRepository;
import com.shinyoung.recruit.enumeration.ClientEventSeverity;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.enumeration.ClientEventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** retention cleanup(설계 9장) — receivedAt 기준 경계 삭제. 기본 retention-days=90(테스트는 동일 기본값 가정). */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ClientEventLogCleanupServiceTest {

    @Autowired
    ClientEventLogCleanupService cleanupService;

    @Autowired
    ClientEventLogRepository repository;

    @Autowired
    Clock clock;

    private void seed(String sessionId, LocalDateTime receivedAt) {
        repository.save(ClientEventLog.builder()
                .receivedAt(receivedAt)
                .eventType(ClientEventType.API_ERROR)
                .severity(ClientEventSeverity.ERROR)
                .source(ClientEventSource.APPLICANT_WEB)
                .clientSessionId(sessionId)
                .clientEventId("event-" + sessionId)
                .build());
    }

    @Test
    void 보존기간이_지난_로그만_삭제된다() {
        LocalDateTime now = LocalDateTime.now(clock);
        seed("session-old-0001", now.minusDays(91));   // 보존기간(90일) 밖 — 삭제 대상
        seed("session-keep-001", now.minusDays(89));   // 보존기간 안 — 유지
        seed("session-keep-002", now.minusDays(1));    // 유지

        int deleted = cleanupService.cleanup();

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void 삭제_대상이_없으면_0을_반환한다() {
        seed("session-keep-001", LocalDateTime.now(clock).minusDays(1));

        assertThat(cleanupService.cleanup()).isZero();
        assertThat(repository.count()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `.\gradlew.bat compileTestJava --no-daemon`
Expected: FAIL — `ClientEventLogCleanupService` 심볼 없음

- [ ] **Step 3: Repository에 bulk delete 추가** — `ClientEventLogRepository`에 메서드 추가(파생 delete가 아니라 명시적 단일 DELETE 문, 설계 9장):

```java
    /** retention cleanup 전용 bulk delete(09f-4). 엔티티 로딩 없이 단일 DELETE 문으로 삭제 건수를 반환한다. */
    @Modifying
    @Query("DELETE FROM ClientEventLog c WHERE c.receivedAt < :threshold")
    int deleteByReceivedAtBefore(@Param("threshold") LocalDateTime threshold);
```

추가 임포트:

```java
import org.springframework.data.jpa.repository.Modifying;
```

- [ ] **Step 4: CleanupService 구현**

```java
// src/main/java/com/shinyoung/recruit/service/ClientEventLogCleanupService.java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.repository.ClientEventLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * client event retention 삭제(Phase 09f-4, 설계 9장). {@code receivedAt < now - retentionDays} 기준
 * bulk delete. 진단 로그는 감사 로그와 달리 장기 보존하지 않는다(기본 90일).
 * 스케줄러(매일)와 관리자 수동 트리거 양쪽에서 호출된다.
 */
@Service
public class ClientEventLogCleanupService {

    private final ClientEventLogRepository repository;
    private final Clock clock;
    private final int retentionDays;

    public ClientEventLogCleanupService(
            ClientEventLogRepository repository,
            Clock clock,
            @Value("${client-event-log.retention-days:90}") int retentionDays
    ) {
        this.repository = repository;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    /** 보존기간 경과 로그 삭제. 삭제 건수 반환. */
    @Transactional
    public int cleanup() {
        LocalDateTime threshold = LocalDateTime.now(clock).minusDays(retentionDays);
        return repository.deleteByReceivedAtBefore(threshold);
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `.\gradlew.bat test --tests "com.shinyoung.recruit.service.ClientEventLogCleanupServiceTest" --no-daemon`
Expected: PASS (2 tests)

- [ ] **Step 6: Commit**

```
git add src/main/java/com/shinyoung/recruit/domain/repository/ClientEventLogRepository.java src/main/java/com/shinyoung/recruit/service/ClientEventLogCleanupService.java src/test/java/com/shinyoung/recruit/service/ClientEventLogCleanupServiceTest.java
git commit -m "9f-4 retention cleanup service"
```

---

### Task 13: SchedulingConfig + ClientEventLogCleanupScheduler

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/config/SchedulingConfig.java`
- Create: `src/main/java/com/shinyoung/recruit/service/ClientEventLogCleanupScheduler.java`
- Test: `src/test/java/com/shinyoung/recruit/service/ClientEventLogCleanupSchedulerTest.java`

- [ ] **Step 1: 실패하는 테스트 작성** (스케줄러는 예외를 전파하지 않아야 한다 — 설계 9장)

```java
// src/test/java/com/shinyoung/recruit/service/ClientEventLogCleanupSchedulerTest.java
package com.shinyoung.recruit.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientEventLogCleanupSchedulerTest {

    @Test
    void 정상_실행시_cleanup을_위임한다() {
        ClientEventLogCleanupService cleanupService = mock(ClientEventLogCleanupService.class);
        when(cleanupService.cleanup()).thenReturn(3);

        new ClientEventLogCleanupScheduler(cleanupService).runCleanup();

        verify(cleanupService).cleanup();
    }

    @Test
    void cleanup_실패는_로그만_남기고_전파하지_않는다() {
        ClientEventLogCleanupService cleanupService = mock(ClientEventLogCleanupService.class);
        when(cleanupService.cleanup()).thenThrow(new IllegalStateException("DB down"));

        assertThatCode(() -> new ClientEventLogCleanupScheduler(cleanupService).runCleanup())
                .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `.\gradlew.bat compileTestJava --no-daemon`
Expected: FAIL — `ClientEventLogCleanupScheduler` 심볼 없음

- [ ] **Step 3: 구현**

```java
// src/main/java/com/shinyoung/recruit/config/SchedulingConfig.java
package com.shinyoung.recruit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 활성화(Phase 09f-4 — 프로젝트 최초 도입). 현재 사용처는
 * {@code ClientEventLogCleanupScheduler}(client event retention) 하나다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
```

```java
// src/main/java/com/shinyoung/recruit/service/ClientEventLogCleanupScheduler.java
package com.shinyoung.recruit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * client event retention 스케줄러(Phase 09f-4). 매일 1회(기본 04:00, cron 외부 설정) cleanup을 실행한다.
 * 실패해도 예외를 전파하지 않는다 — 진단 로그 정리 실패가 스케줄링 스레드/다음 실행에 영향을 주지 않게 한다.
 */
@Component
public class ClientEventLogCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(ClientEventLogCleanupScheduler.class);

    private final ClientEventLogCleanupService cleanupService;

    public ClientEventLogCleanupScheduler(ClientEventLogCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "${client-event-log.cleanup-cron:0 0 4 * * *}")
    public void runCleanup() {
        try {
            int deleted = cleanupService.cleanup();
            log.info("Client event log cleanup deleted {} rows", deleted);
        } catch (Exception e) {
            log.error("Client event log cleanup failed", e);
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `.\gradlew.bat test --tests "com.shinyoung.recruit.service.ClientEventLogCleanupSchedulerTest" --no-daemon`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```
git add src/main/java/com/shinyoung/recruit/config/SchedulingConfig.java src/main/java/com/shinyoung/recruit/service/ClientEventLogCleanupScheduler.java src/test/java/com/shinyoung/recruit/service/ClientEventLogCleanupSchedulerTest.java
git commit -m "9f-4 scheduling 도입 + cleanup scheduler"
```

---

### Task 14: 관리자 수동 cleanup 트리거 + Security matcher

**Files:**
- Modify: `src/main/java/com/shinyoung/recruit/controller/AdminClientEventLogController.java`
- Create: `src/main/java/com/shinyoung/recruit/dto/response/ClientEventLogCleanupResponse.java`
- Modify: `src/main/java/com/shinyoung/recruit/config/SecurityConfig.java` (Task 10에서 추가한 GET matcher **앞**)
- Test: `src/test/java/com/shinyoung/recruit/controller/AdminClientEventLogControllerTest.java` (테스트 추가)

- [ ] **Step 1: 실패하는 테스트 추가** — `AdminClientEventLogControllerTest`에 메서드 추가

```java
    @Test
    void PRIVACY_ADMIN은_cleanup을_수동_트리거할_수_있다() throws Exception {
        repository.save(ClientEventLog.builder()
                .receivedAt(LocalDateTime.now().minusDays(120))
                .eventType(ClientEventType.API_ERROR)
                .severity(ClientEventSeverity.ERROR)
                .source(ClientEventSource.APPLICANT_WEB)
                .clientSessionId("session-old-0001")
                .clientEventId("event-old-0001")
                .build());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/admin/client-events/cleanup")
                        .with(authentication(auth("ROLE_PRIVACY_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(1));
    }

    @Test
    void RECRUIT_ADMIN은_cleanup을_트리거할_수_없다() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/admin/client-events/cleanup")
                        .with(authentication(auth("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isForbidden());
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `.\gradlew.bat test --tests "com.shinyoung.recruit.controller.AdminClientEventLogControllerTest" --no-daemon`
Expected: FAIL — 신규 2개 테스트(404/405)

- [ ] **Step 3: Cleanup Response DTO 구현**

```java
// src/main/java/com/shinyoung/recruit/dto/response/ClientEventLogCleanupResponse.java
package com.shinyoung.recruit.dto.response;

/** 수동 retention cleanup 결과(Phase 09f-4). */
public record ClientEventLogCleanupResponse(int deletedCount) {
}
```

- [ ] **Step 4: Controller에 cleanup 엔드포인트 추가** — `AdminClientEventLogController`에 필드/메서드 추가

필드 추가(`clientEventLogReadService` 옆):

```java
    private final ClientEventLogCleanupService clientEventLogCleanupService;
```

메서드 추가(`getEvent` 뒤) + 임포트(`ClientEventLogCleanupResponse`, `ClientEventLogCleanupService`, `PostMapping`):

```java
    /** retention cleanup 수동 트리거 — 삭제(write)라 ROLE_PRIVACY_ADMIN 전용(SecurityConfig matcher, 설계 9장). */
    @PostMapping("/admin/client-events/cleanup")
    public ResponseEntity<ApiResponse<ClientEventLogCleanupResponse>> cleanup() {
        return ResponseEntity.ok(ApiResponse.success(
                new ClientEventLogCleanupResponse(clientEventLogCleanupService.cleanup())));
    }
```

- [ ] **Step 5: SecurityConfig matcher 추가** — Task 10의 GET matcher **바로 앞**에 삽입(POST가 GET `/**`에 안 걸리지만 명시 순서 유지):

```java
                // cleanup은 삭제(write) — retention 관례에 따라 PRIVACY_ADMIN 전용(설계 9장).
                .requestMatchers(HttpMethod.POST, "/api/admin/client-events/cleanup").hasAuthority("ROLE_PRIVACY_ADMIN")
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `.\gradlew.bat test --tests "com.shinyoung.recruit.controller.AdminClientEventLogControllerTest" --no-daemon`
Expected: PASS (9 tests)

- [ ] **Step 7: Commit**

```
git add src/main/java/com/shinyoung/recruit/controller/AdminClientEventLogController.java src/main/java/com/shinyoung/recruit/dto/response/ClientEventLogCleanupResponse.java src/main/java/com/shinyoung/recruit/config/SecurityConfig.java src/test/java/com/shinyoung/recruit/controller/AdminClientEventLogControllerTest.java
git commit -m "9f-4 cleanup 수동 트리거"
```

---

### Task 15: Slice 09f-4 문서화 + 최종 검증

**Files:**
- Create: `docs/codex/implementation/phase-09f-4-client-event-retention.md`
- Create: `docs/codex/reports/phase-09f-4-client-event-retention.html`
- Modify: `docs/codex/07-implementation-history.md`

- [ ] **Step 1: 09f 관련 테스트 일괄 실행(스코프)**

Run:
```
.\gradlew.bat test --tests "com.shinyoung.recruit.domain.repository.ClientEventLogRepositoryTest" --tests "com.shinyoung.recruit.service.ClientEvent*" --tests "com.shinyoung.recruit.controller.ClientEventLog*" --tests "com.shinyoung.recruit.controller.AdminClientEventLogControllerTest" --no-daemon
```
Expected: PASS (전체 09f 테스트)

추가로 SecurityConfig/CORS를 건드렸으므로 기존 인접 테스트 회귀 확인:
```
.\gradlew.bat test --tests "com.shinyoung.recruit.controller.AdminAuditControllerTest" --no-daemon
```
Expected: PASS

- [ ] **Step 2: 구현 문서 작성** — Task 7과 동일 구성으로 Task 12~14 산출물 기록. `@EnableScheduling` 최초 도입 사실과 영향 범위(현재 사용처 1개), retention 기본값(90일)을 명시한다.

- [ ] **Step 3: HTML 리포트 생성** — 템플릿 기준. Phase 09f 전체 완료 상태(09f-1/3/4 Completed, FE 09f-2는 Out of scope) 표시.

- [ ] **Step 4: 구현 이력 갱신** — `## 2026-06-10 - Phase 09f-4 Client Event Retention` 항목 추가.

- [ ] **Step 5: Commit**

```
git add docs/codex/implementation/phase-09f-4-client-event-retention.md docs/codex/reports/phase-09f-4-client-event-retention.html docs/codex/07-implementation-history.md
git commit -m "9f-4 구현 문서/리포트"
```

- [ ] **Step 6: 최종 보고** — CLAUDE.md 12장 형식(변경 요약/변경 파일/테스트 결과/문서 갱신/주의 사항)으로 보고한다. 전체 테스트(`clean test`)는 실행하지 않았음과 그 사유(사용자 정책 — scoped 테스트만, 명시 요청 시 전체 실행)를 명확히 남긴다.

---

## Self-Review 결과 (작성 시 수행)

- **Spec coverage:** 설계 6.1(JSON-only/source 거부/id 형식)→Task 5·6, 6.2(파이프라인/saveAndFlush)→Task 5, 6.3(safe message/숫자 마스킹)→Task 5, 6.4(3단 rate limit/맵 가드)→Task 4, 6.5(exact allowlist)→Task 3, 6.6(예외 매핑)→Task 1·8, 7장(Security/CORS)→Task 6·10·14, 8장(read guard/마스킹)→Task 9·10, 9장(retention/@Modifying)→Task 12·13·14, 10장(설정)→Task 4, 11장(테스트 전략)→각 태스크, 12장(Acceptance)→Task 15 Step 1로 충족.
- **Type consistency:** `ClientEventLogIngestResponse.accepted/duplicate/disabled`, `ClientEventRateLimiter.check(ip, sessionId, principalHash)`, `ClientEventMetadataSanitizer.sanitize(eventType, map)`, `ClientEventLogReadService.search(...12 params)/getEvent`, repository `search(...9 params + pageable)`/`deleteByReceivedAtBefore` — 태스크 간 시그니처 일치 확인.
- **주의:** `ClientEventLogService`는 의도적으로 `@Transactional` 미사용(rollback-only 함정) — Task 5 javadoc에 근거 명시. 실행자가 임의로 추가하지 말 것.
