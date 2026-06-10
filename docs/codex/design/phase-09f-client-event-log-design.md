# Phase 09f — Client Event Log 설계

## 1. Phase Summary

- Phase name: Phase 09f — Client Event Log
- Work type: documentation-only design phase
- Date: 2026-06-10
- Scope: 지원자 Web UI에서 발생하는 오류·중요 흐름 이벤트를 백엔드에 수집하여 CS/FU, 장애 재현, API 오류 상관분석에 활용한다.
- Repository boundary:
  - Backend: `recruit_backend` — `client_event_log` 도메인, 수집 API, 조회 API, 보존 정책을 구현한다.
  - Frontend: `recruit` — Vue3/Vite/Pinia/Axios 구조에서 telemetry client, 전역 오류 핸들러, API 오류 interceptor, 명시적 업무 이벤트 logger를 구현한다.
- Status: design completed, Java/TypeScript/source/test implementation not started.

이 문서는 `ActivityLog`와 별개의 진단 로그 도메인을 설계한다. `ActivityLog`는 감사·컴플라이언스·관리자 행위 증적이고, `ClientEventLog`는 지원자 화면 오류 추적과 고객 응대 보조를 위한 운영 진단 데이터다.

## 2. Decision

### 2.1 `ActivityLog`에 섞지 않는다

`activity_log`는 Phase 09a/09b에서 append-only 감사 증적으로 설계되어 있고, 관리자 정보 반출/변경/파기 계측의 source of truth다. 지원자 브라우저 이벤트는 성격이 다르다.

따라서 다음 경계를 둔다.

| 구분 | 목적 | 주체 | 대표 이벤트 | 보존 |
| --- | --- | --- | --- | --- |
| `activity_log` | 감사/컴플라이언스 증적 | 관리자/시스템 | export, PDF, stage result correction, attachment admin download, purge | 장기/정책 기반 |
| `client_event_log` | 클라이언트 오류 추적/CS/FU | 지원자 브라우저 | JS error, API error, submit failed, upload failed, session expired | 단기/운영 진단 |

### 2.2 모든 클릭을 남기지 않는다

ROI가 높은 이벤트만 수집한다.

수집한다:

- JavaScript runtime error
- unhandled promise rejection
- API 실패(HTTP 4xx/5xx, timeout, network error)
- 지원서 저장/제출 실패
- 첨부 업로드 실패
- 세션 만료/권한 오류
- 지원서 작성 핵심 checkpoint(page open, submit click 등 최소 범위)

수집하지 않는다:

- 모든 클릭 좌표
- 키 입력 이벤트
- form field 원문
- 자기소개서/경력기술/학력/자격 등 입력값 원문
- request body / response body 원문
- 첨부파일 원본 파일명
- 화면 캡처

### 2.3 best-effort 수집

Client event log 전송 실패는 사용자 업무 흐름을 막지 않는다.

- FE: fire-and-forget, 짧은 timeout, telemetry 전용 Axios client 사용
- BE: 수집 API는 가볍게 insert하고, 검증 실패/중복/과다 요청은 업무 API에 영향을 주지 않는다.
- 진단 로그 API 실패를 다시 진단 로그로 남기지 않는다. 재귀 로깅 금지.

## 3. Current Structure Assumptions

### 3.1 Frontend

현재 FE는 다음 전제를 가진다.

- Vue 3 + Vite + TypeScript
- Pinia store
- Ant Design Vue
- Axios 1.x
- 공통 API client: `src/api/client.ts`
  - `baseURL: import.meta.env.VITE_API_BASE_URL`
  - `timeout: 10000`
  - `withCredentials: true`
  - request/response interceptor에서 전역 loading 처리
  - 401/403 redirect 처리

따라서 client event logging은 기존 `apiClient`를 그대로 쓰지 않는다. 기존 client를 쓰면 loading이 켜지고, 401/403 redirect와 재귀 오류 로깅이 섞일 수 있다. 별도 `telemetryClient`를 둔다.

### 3.2 Backend

현재 BE는 다음 전제를 가진다.

- Spring Boot + JPA
- 공통 응답: `ApiResponse<T>`
- 세션 기반 인증(`withCredentials`)
- `CorrelationIdFilter`
  - 요청별 `X-Request-Id` 생성/재사용
  - MDC `correlationId` 저장
  - 응답 헤더 `X-Request-Id` echo
- CORS 현재 보강 필요
  - FE가 응답 헤더 `X-Request-Id`를 읽으려면 `exposedHeaders`에 `X-Request-Id`를 추가해야 한다.

## 4. Backend Design

## 4.1 Package/Layer

신규 파일 후보:

| Layer | File |
| --- | --- |
| Entity | `domain/entity/ClientEventLog.java` |
| Enum | `enumeration/ClientEventType.java` |
| Enum | `enumeration/ClientEventSeverity.java` |
| Enum | `enumeration/ClientEventSource.java` |
| Repository | `domain/repository/ClientEventLogRepository.java` |
| Request DTO | `dto/request/ClientEventLogRequest.java` |
| Response DTO | `dto/response/ClientEventLogIngestResponse.java` |
| Response DTO | `dto/response/ClientEventLogResponse.java` |
| Service | `service/ClientEventLogService.java` |
| Service | `service/ClientEventMetadataSanitizer.java` |
| Controller | `controller/ClientEventLogController.java` |
| Admin Controller | `controller/AdminClientEventLogController.java` |
| Exception | `exception/InvalidClientEventLogException.java` |
| Config | `config/SecurityConfig.java` CORS/security matcher 보강 |

## 4.2 Entity

`ClientEventLog`는 진단 로그이며 `ActivityLog`와 분리한다. 다만 insert-only로 다룬다. update/delete 업무 API는 만들지 않는다.

```java
@Entity
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
public class ClientEventLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

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

    /** query string 제거, 필요 시 FE에서 template path 사용. */
    @Column(name = "api_path", length = 300)
    private String apiPath;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    /** 원문 장문 금지. sanitize + truncate. */
    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "stack_hash", length = 128)
    private String stackHash;

    /** 운영 진단용 요약. minified stack 전체/소스맵 복원 전 stack 전체 저장 금지. */
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

    /** 서버에서 추출. FE body 값 신뢰 금지. */
    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    /** 서버에서 추출. FE body 값 신뢰 금지. */
    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** 인증 사용자 원문 loginId 저장 금지. HMAC hash만 저장. */
    @Column(name = "principal_hash", length = 128)
    private String principalHash;

    @Column(name = "principal_type", length = 30)
    private String principalType;

    /** allowlist metadata만 JSON 직렬화. */
    @Lob
    @Column(name = "metadata_json")
    private String metadataJson;
}
```

### 설계 이유

- `receivedAt`은 서버 기준 source of truth다.
- `clientOccurredAt`은 브라우저 시각이므로 참고값이다. 정렬/보존 판단 기준으로 쓰지 않는다.
- `relatedCorrelationId`는 실패한 업무 API와 연결하기 위한 핵심 키다.
- `ingestCorrelationId`는 client event 수집 API 자체의 요청 추적값이다.
- `principalHash`는 로그인 ID 원문 저장을 피하기 위한 HMAC 값이다.
- `applicationId`, `jobPostingId`, `clientSessionId`를 검색 키로 둔다.

## 4.3 Enum

```java
public enum ClientEventSource {
    APPLICANT_WEB,
    ADMIN_WEB
}
```

초기 구현은 `APPLICANT_WEB`만 사용한다. `ADMIN_WEB`은 향후 관리자 화면 진단 로그 확장 대비다.

```java
public enum ClientEventSeverity {
    INFO,
    WARN,
    ERROR
}
```

```java
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

초기에는 위 값 중 다음만 실제 사용해도 된다.

- `API_ERROR`
- `API_TIMEOUT`
- `NETWORK_ERROR`
- `SESSION_EXPIRED`
- `FORBIDDEN`
- `JS_ERROR`
- `UNHANDLED_REJECTION`
- `APPLICATION_SUBMIT_FAILED`
- `ATTACHMENT_UPLOAD_FAILED`

## 4.4 Request DTO

```java
public record ClientEventLogRequest(
        @NotNull ClientEventType eventType,
        @NotNull ClientEventSeverity severity,
        @NotNull ClientEventSource source,
        @NotBlank @Size(max = 80) String clientSessionId,
        @NotBlank @Size(max = 80) String clientEventId,
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
        @Size(max = 500) String message,
        @Size(max = 128) String stackHash,
        @Size(max = 2000) String stackSummary,

        @Size(max = 80) String frontendVersion,
        @Size(max = 80) String browserName,
        @Size(max = 80) String browserVersion,
        @Size(max = 80) String osName,
        @Size(max = 40) String viewport,
        @Size(max = 80) String timezone,

        Map<String, Object> metadata
) {}
```

주의:

- `ipAddress`, `userAgent`, `principalHash`, `principalType`, `receivedAt`, `ingestCorrelationId`는 request body에서 받지 않는다.
- FE가 보내는 actor/user 정보는 저장하지 않는다.
- `apiPath`는 query string 제거 후 저장한다.
- `message`는 사용자 입력값이 섞일 수 있으므로 sanitize + truncate한다.

## 4.5 Metadata Policy

`metadata`는 자유 JSON처럼 보이지만 실제로는 allowlist 기반으로 제한한다.

공통 제한:

- 최대 key 20개
- key 길이 50 이하
- value는 `String`, `Number`, `Boolean`, `null`만 허용
- 문자열 value 길이 200 이하
- 전체 직렬화 후 4000자 이하
- nested object/array 기본 금지

금지 key 패턴:

```text
name, userName, applicantName, email, phone, phoneNumber,
ci, ciHash, password, birth, address,
content, answer, essay, resume, coverLetter,
fileName, originalFilename, body, requestBody, responseBody
```

이벤트별 허용 예:

| eventType | metadata key |
| --- | --- |
| `API_ERROR` | `durationMs`, `retryable`, `axiosCode` |
| `API_TIMEOUT` | `durationMs`, `timeoutMs` |
| `JS_ERROR` | `file`, `line`, `column` |
| `UNHANDLED_REJECTION` | `reasonType` |
| `ATTACHMENT_UPLOAD_FAILED` | `fileSize`, `fileExtension`, `uploadStep` |
| `CLIENT_VALIDATION_FAILED` | `sectionCode`, `fieldCount`, `errorCount` |
| `APPLICATION_SUBMIT_FAILED` | `sectionCode`, `failedStep` |

첨부파일 관련 metadata에도 원본 파일명은 저장하지 않는다. 필요하면 FE에서 확장자와 크기만 전송한다.

## 4.6 Service Rules

`ClientEventLogService.record(request, authentication, servletRequest)` 책임:

1. 서버 기준 `receivedAt = now(clock)` 설정
2. `ingestCorrelationId = CorrelationIdFilter.currentCorrelationId()` 설정
3. IP/User-Agent는 `HttpServletRequest`에서만 추출
4. 인증 사용자가 있으면:
   - `principalType = CustomUserDetails.userType`
   - `principalHash = HMAC_SHA256(AUDIT_HMAC_SECRET, "CLIENT_PRINCIPAL:" + username)`
   - username 원문 저장 금지
5. request-derived 문자열 sanitize/truncate
6. `metadata` allowlist 검증 및 직렬화
7. `(clientSessionId, clientEventId)` 중복이면 기존 성공 응답처럼 처리
8. 저장 실패는 서버 로그에는 남기되, FE 업무 흐름에는 영향 없도록 수집 API 응답만 실패 처리

중복 처리:

```text
unique(client_session_id, client_event_id)
```

동일 이벤트가 재전송되면 `accepted=false, duplicate=true` 응답을 주거나, 단순 success로 처리한다. FE는 결과를 사용하지 않는다.

## 4.7 API

### 4.7.1 수집 API

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| POST | `/client-events` | optional | client event 단건 수집 |

외부 프록시/FE baseURL 정책에 따라 최종 URL은 다음 중 하나가 된다.

- Backend logical path: `POST /client-events`
- FE 호출 path: `telemetryClient.post('/client-events', payload)`
- 운영에서 `/api` prefix를 baseURL로 붙이는 경우: `POST /api/client-events`

인증 정책:

- `permitAll` 권장
- 세션 쿠키가 있으면 인증 사용자 정보를 서버에서 추출
- 세션이 없어도 anonymous event 수집 가능
- 이유: 로그인 전 오류, 세션 만료 오류, 회원가입 화면 오류도 수집 대상이 될 수 있음

응답:

```json
{
  "success": true,
  "data": {
    "accepted": true,
    "duplicate": false,
    "id": 123
  },
  "message": "정상 처리되었습니다."
}
```

### 4.7.2 관리자 조회 API

FU에 실제로 쓰려면 조회 API가 필요하다.

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/admin/client-events` | `ROLE_RECRUIT_ADMIN`, `ROLE_PRIVACY_ADMIN` | client event 검색 |
| GET | `/admin/client-events/{id}` | `ROLE_RECRUIT_ADMIN`, `ROLE_PRIVACY_ADMIN` | client event 단건 |

검색 조건:

| Param | 설명 |
| --- | --- |
| `eventType` | 이벤트 유형 |
| `severity` | INFO/WARN/ERROR |
| `source` | APPLICANT_WEB/ADMIN_WEB |
| `applicationId` | 지원서 ID |
| `jobPostingId` | 공고 ID |
| `clientSessionId` | 브라우저 세션 ID |
| `relatedCorrelationId` | 실패 업무 API의 X-Request-Id |
| `from` / `to` | receivedAt 기준 |
| `page` / `size` | page size 최대 100 |

조회 가드:

- default range: 최근 7일
- max range: 90일
- size 최대 100
- 최신순 정렬: `receivedAt DESC, id DESC`
- `ROLE_RECRUIT_ADMIN`: `ipAddress`, `userAgent`, `principalHash` 마스킹
- `ROLE_PRIVACY_ADMIN`: 원문 접근 가능

## 4.8 SecurityConfig 보강

수집 API:

```java
.requestMatchers(HttpMethod.POST, "/api/client-events").permitAll()
```

또는 현재 controller/baseURL 정책에 맞춰:

```java
.requestMatchers(HttpMethod.POST, "/client-events").permitAll()
```

관리자 조회:

```java
.requestMatchers(HttpMethod.GET, "/api/admin/client-events/**")
    .hasAnyAuthority("ROLE_RECRUIT_ADMIN", "ROLE_PRIVACY_ADMIN")
```

주의: 프로젝트의 외부 `/api` prefix 적용 방식이 `VITE_API_BASE_URL`인지, reverse proxy rewrite인지, Spring path prefix인지 확정한 뒤 matcher와 controller path를 맞춘다.

## 4.9 CORS 보강

FE가 업무 API 실패 응답에서 `X-Request-Id`를 읽으려면 다음 설정이 필요하다.

```java
corsConfiguration.setExposedHeaders(List.of("X-Request-Id"));
```

FE가 별도 custom request header를 보내지 않는다면 allowed header는 현재의 `Content-Type`만으로 충분하다. 만약 FE가 `X-Client-Session-Id` 같은 header를 추가한다면 CORS allowed headers에 추가해야 한다. 본 설계에서는 header 대신 body에 `clientSessionId`를 넣는다.

## 4.10 Retention

`client_event_log`는 감사 로그가 아니라 진단 로그다. 장기 보존하지 않는다.

권장 기본값:

```yaml
client-event-log:
  retention-days: ${CLIENT_EVENT_LOG_RETENTION_DAYS:90}
  max-metadata-json-length: ${CLIENT_EVENT_LOG_MAX_METADATA_JSON_LENGTH:4000}
  enabled: ${CLIENT_EVENT_LOG_ENABLED:true}
```

보존 정책:

- 초기 운영: 90일
- 안정화 후: 30~60일 검토
- 삭제 기준: `receivedAt < now - retentionDays`
- 스케줄러는 별도 phase에서 구현 가능
- 대용량 우려 시 일 단위 partition 또는 월별 archive table은 추후 검토

## 4.11 Volume Control

초기부터 대량 clickstream을 하지 않는 것이 1차 제어다.

추가 권장:

- FE sampling: `PAGE_OPENED`/`CHECKPOINT`는 10~30% sampling 가능
- ERROR/WARN은 sampling 하지 않음
- API 동일 오류 dedupe: 같은 route + method + status + errorCode가 5초 내 반복되면 1건만 전송
- BE rate limit: `clientSessionId + ip` 기준 1분 60건, 10분 300건 수준

초기 구현에서 rate limit 인프라가 없다면 service-level in-memory guard로 시작하고, 운영 트래픽이 늘면 Redis/token bucket으로 전환한다.

## 5. Frontend Design

## 5.1 신규 파일 후보

| File | 책임 |
| --- | --- |
| `src/types/clientEvent.ts` | client event type/interface 정의 |
| `src/api/telemetryClient.ts` | loading/redirect 없는 telemetry 전용 Axios client |
| `src/api/clientEventApi.ts` | `POST /client-events` wrapper |
| `src/utils/clientSession.ts` | `sessionStorage` 기반 clientSessionId 발급 |
| `src/utils/clientEventLogger.ts` | 이벤트 생성/sanitize/fire-and-forget 전송 |
| `src/plugins/clientErrorHandlers.ts` | `window.error`, `unhandledrejection` 등록 |
| `src/utils/httpErrorTelemetry.ts` | Axios error → ClientEvent 변환 |

## 5.2 TypeScript Types

```ts
export type ClientEventSource = 'APPLICANT_WEB' | 'ADMIN_WEB'

export type ClientEventSeverity = 'INFO' | 'WARN' | 'ERROR'

export type ClientEventType =
  | 'PAGE_OPENED'
  | 'CHECKPOINT'
  | 'API_ERROR'
  | 'API_TIMEOUT'
  | 'NETWORK_ERROR'
  | 'SESSION_EXPIRED'
  | 'FORBIDDEN'
  | 'JS_ERROR'
  | 'UNHANDLED_REJECTION'
  | 'APPLICATION_DRAFT_SAVE_FAILED'
  | 'APPLICATION_SUBMIT_CLICKED'
  | 'APPLICATION_SUBMIT_FAILED'
  | 'ATTACHMENT_UPLOAD_FAILED'
  | 'CLIENT_VALIDATION_FAILED'

export interface ClientEventPayload {
  eventType: ClientEventType
  severity: ClientEventSeverity
  source: ClientEventSource
  clientSessionId: string
  clientEventId: string
  clientOccurredAt: string

  relatedCorrelationId?: string
  pageCode?: string
  componentCode?: string
  routePath?: string
  operation?: string

  jobPostingId?: number
  applicationId?: number

  httpMethod?: string
  apiPath?: string
  httpStatus?: number
  errorCode?: string
  message?: string
  stackHash?: string
  stackSummary?: string

  frontendVersion?: string
  browserName?: string
  browserVersion?: string
  osName?: string
  viewport?: string
  timezone?: string

  metadata?: Record<string, string | number | boolean | null>
}
```

## 5.3 Telemetry Client

기존 `apiClient`는 loading/redirect interceptor가 있으므로 사용하지 않는다.

```ts
// src/api/telemetryClient.ts
import axios from 'axios'

export const telemetryClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 3000,
  withCredentials: true,
})
```

주의:

- interceptor 최소화
- 401/403 redirect 금지
- loading store 호출 금지
- telemetry request 실패를 다시 telemetry로 남기지 않음

## 5.4 Client Event API

```ts
// src/api/clientEventApi.ts
import { telemetryClient } from './telemetryClient'
import type { ApiResponse } from '@/types/api'
import type { ClientEventPayload } from '@/types/clientEvent'

export interface ClientEventIngestResponse {
  accepted: boolean
  duplicate: boolean
  id?: number
}

export const clientEventApi = {
  record(payload: ClientEventPayload) {
    return telemetryClient.post<ApiResponse<ClientEventIngestResponse>>('/client-events', payload)
  },
}
```

## 5.5 Client Session

```ts
// src/utils/clientSession.ts
const KEY = 'recruit.clientSessionId'

export function getClientSessionId(): string {
  const current = sessionStorage.getItem(KEY)
  if (current) return current

  const next = crypto.randomUUID()
  sessionStorage.setItem(KEY, next)
  return next
}
```

`sessionStorage` 기준을 권장한다.

- 같은 탭에서 지원서 작성 흐름을 묶을 수 있다.
- 브라우저를 닫으면 사라진다.
- 장기 tracking cookie 성격을 피한다.

## 5.6 Logger

```ts
// src/utils/clientEventLogger.ts
import { clientEventApi } from '@/api/clientEventApi'
import { getClientSessionId } from '@/utils/clientSession'
import type { ClientEventPayload, ClientEventType, ClientEventSeverity } from '@/types/clientEvent'

function basePayload(eventType: ClientEventType, severity: ClientEventSeverity): ClientEventPayload {
  return {
    eventType,
    severity,
    source: 'APPLICANT_WEB',
    clientSessionId: getClientSessionId(),
    clientEventId: crypto.randomUUID(),
    clientOccurredAt: new Date().toISOString(),
    routePath: sanitizeRoute(window.location.pathname),
    frontendVersion: import.meta.env.VITE_APP_VERSION,
    viewport: `${window.innerWidth}x${window.innerHeight}`,
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
  }
}

export function logClientEvent(partial: Partial<ClientEventPayload> & Pick<ClientEventPayload, 'eventType' | 'severity'>): void {
  const payload: ClientEventPayload = {
    ...basePayload(partial.eventType, partial.severity),
    ...sanitizePayload(partial),
  }

  void clientEventApi.record(payload).catch(() => {
    // best-effort: 사용자 흐름 방해 금지, 재귀 로깅 금지
  })
}

function sanitizeRoute(path: string): string {
  return path.split('?')[0].slice(0, 300)
}

function sanitizePayload<T extends Partial<ClientEventPayload>>(payload: T): T {
  // 구현 시 message/stack/apiPath/query/body 제거 규칙 적용
  return payload
}
```

## 5.7 API Error Interceptor 연동

현재 `src/api/client.ts` response error interceptor에 다음 흐름을 추가한다.

```ts
apiClient.interceptors.response.use(
  (response) => {
    const uiStore = useUiStore()
    uiStore.hideLoading()
    return response
  },
  (error) => {
    const uiStore = useUiStore()
    const currentPath = window.location.pathname
    uiStore.hideLoading()

    logApiError(error)

    const status = error.response?.status
    const skipAuthRedirect = error.config?.skipAuthRedirect === true

    if (status === 401 && !skipAuthRedirect && currentPath !== '/login') {
      window.location.href = '/login'
    }

    if (status === 403) {
      window.location.href = '/403'
    }

    return Promise.reject(error)
  },
)
```

`logApiError(error)` 변환 규칙:

| Axios 상태 | eventType | severity |
| --- | --- | --- |
| `error.code === 'ECONNABORTED'` | `API_TIMEOUT` | `ERROR` |
| `!error.response` | `NETWORK_ERROR` | `ERROR` |
| `status === 401` | `SESSION_EXPIRED` | `WARN` |
| `status === 403` | `FORBIDDEN` | `WARN` |
| 그 외 4xx/5xx | `API_ERROR` | `ERROR` |

수집 필드:

```ts
relatedCorrelationId: error.response?.headers?.['x-request-id']
httpMethod: error.config?.method?.toUpperCase()
apiPath: stripQuery(error.config?.url)
httpStatus: error.response?.status
errorCode: extractSafeErrorCode(error.response?.data)
message: safeMessage(error.message)
metadata: {
  axiosCode: error.code,
  timeoutMs: error.config?.timeout,
}
```

금지:

- `error.config.data` 저장 금지
- `error.response.data` 전체 저장 금지
- query string 저장 금지
- request headers 저장 금지

## 5.8 Global JS Error Handler

`main.ts`에서 앱 mount 전후로 등록한다.

```ts
// src/plugins/clientErrorHandlers.ts
import { logClientEvent } from '@/utils/clientEventLogger'

export function installClientErrorHandlers(): void {
  window.addEventListener('error', (event) => {
    logClientEvent({
      eventType: 'JS_ERROR',
      severity: 'ERROR',
      message: event.message,
      stackSummary: summarizeStack(event.error?.stack),
      stackHash: hashStack(event.error?.stack),
      metadata: {
        file: stripOrigin(event.filename),
        line: event.lineno,
        column: event.colno,
      },
    })
  })

  window.addEventListener('unhandledrejection', (event) => {
    logClientEvent({
      eventType: 'UNHANDLED_REJECTION',
      severity: 'ERROR',
      message: safeReasonMessage(event.reason),
      stackSummary: summarizeStack(event.reason?.stack),
      stackHash: hashStack(event.reason?.stack),
      metadata: {
        reasonType: typeof event.reason,
      },
    })
  })
}
```

`main.ts` 적용:

```ts
import { installClientErrorHandlers } from '@/plugins/clientErrorHandlers'

installClientErrorHandlers()

const app = createApp(App)
```

## 5.9 Explicit Business Event Logging

화면에서 명시적으로 남길 이벤트는 실패/중요 checkpoint 위주로 제한한다.

예: 지원서 제출 실패

```ts
try {
  await applicationApi.submit(applicationId)
} catch (e) {
  logClientEvent({
    eventType: 'APPLICATION_SUBMIT_FAILED',
    severity: 'ERROR',
    pageCode: 'APPLICATION_FORM',
    operation: 'SUBMIT_APPLICATION',
    applicationId,
    jobPostingId,
    message: 'Application submit failed',
  })
  throw e
}
```

예: 첨부 업로드 실패

```ts
logClientEvent({
  eventType: 'ATTACHMENT_UPLOAD_FAILED',
  severity: 'ERROR',
  pageCode: 'APPLICATION_FORM',
  componentCode: 'ATTACHMENT_SECTION',
  operation: 'UPLOAD_ATTACHMENT',
  applicationId,
  metadata: {
    fileSize: file.size,
    fileExtension: safeExtension(file.name),
  },
})
```

주의: `file.name` 원문은 보내지 않는다. 확장자만 보낸다.

## 5.10 Route/Page Event

모든 route 이동을 남기지 않는다. 지원자 핵심 화면만 남긴다.

권장:

- 지원서 작성 화면 최초 진입: `PAGE_OPENED`
- 제출 버튼 클릭 직전: `APPLICATION_SUBMIT_CLICKED`
- validation 실패 요약: `CLIENT_VALIDATION_FAILED`

비권장:

- admin 메뉴 이동 전체
- hover/click 전체
- input focus/blur 전체

## 6. API Contract

### 6.1 Request Example

```json
{
  "eventType": "API_ERROR",
  "severity": "ERROR",
  "source": "APPLICANT_WEB",
  "clientSessionId": "5d7c00ff-0d53-4ea3-bd44-68a9f7d68f9f",
  "clientEventId": "6a1bd08e-3cd1-4c0f-85c0-e6a65e4a0a33",
  "clientOccurredAt": "2026-06-10T12:30:15.123",
  "relatedCorrelationId": "7c51f646-e75b-4d19-9f69-f82f7f0f2dc4",
  "pageCode": "APPLICATION_FORM",
  "componentCode": "EDUCATION_SECTION",
  "routePath": "/applications/123/form",
  "operation": "SAVE_EDUCATION",
  "jobPostingId": 10,
  "applicationId": 123,
  "httpMethod": "POST",
  "apiPath": "/applicant/applications/123/education",
  "httpStatus": 500,
  "errorCode": "INTERNAL_SERVER_ERROR",
  "message": "Request failed with status code 500",
  "frontendVersion": "2026.06.10-1",
  "viewport": "1440x900",
  "timezone": "Asia/Seoul",
  "metadata": {
    "durationMs": 1250,
    "retryable": false
  }
}
```

### 6.2 Response Example

```json
{
  "success": true,
  "data": {
    "accepted": true,
    "duplicate": false,
    "id": 987
  },
  "message": "정상 처리되었습니다."
}
```

## 7. Privacy / Security Rules

1. 지원자 입력값 원문 저장 금지.
2. 자기소개서/경력기술/학력/자격/주소/연락처/이메일/CI 저장 금지.
3. 첨부파일 원본 파일명 저장 금지.
4. request body/response body 저장 금지.
5. query string 저장 금지.
6. 인증 사용자 loginId 원문 저장 금지. HMAC hash만 저장.
7. IP/User-Agent는 서버에서 추출한다. FE body 값을 신뢰하지 않는다.
8. `clientOccurredAt`은 참고값이며, 보존/정렬 기준은 `receivedAt`이다.
9. 수집 API는 `permitAll`이 가능하므로 rate limit/dedupe/size limit이 필수다.
10. 관리자 조회에서 IP/User-Agent/principalHash 원문은 `ROLE_PRIVACY_ADMIN` 전용으로 제한한다.

## 8. Query / FU Scenario

### 8.1 사용자가 “제출 버튼 눌렀는데 오류가 났다”고 문의

조회 순서:

1. `applicationId`로 최근 `APPLICATION_SUBMIT_FAILED`, `API_ERROR` 검색
2. `clientSessionId`로 같은 브라우저 세션의 전후 이벤트 확인
3. `relatedCorrelationId`로 서버 로그/API 로그/ActivityLog와 상관분석
4. `httpStatus`, `errorCode`, `apiPath`로 장애 범위 판단

### 8.2 특정 시간대 다수 지원자가 저장 실패

조회 순서:

1. `eventType=API_ERROR`, `from/to` 검색
2. `apiPath`, `httpStatus`, `errorCode` group by 분석
3. 동일 `relatedCorrelationId` 또는 동일 stackHash/API path 반복 여부 확인

### 8.3 JS 오류 발생

조회 순서:

1. `eventType=JS_ERROR`, `stackHash` 기준 group by
2. `frontendVersion`, `browserName`, `osName` 기준 영향 범위 확인
3. source map 기반 상세 분석은 배포 산출물 관리 체계가 생긴 뒤 별도 확장

## 9. Test Strategy

### 9.1 Backend Tests

신규 테스트 후보:

| Test | 내용 |
| --- | --- |
| `ClientEventLogServiceTest` | 정상 저장, sanitize, metadata allowlist, principal hash, duplicate 처리 |
| `ClientEventLogRepositoryTest` | 저장/조회/index finder 기본 |
| `ClientEventLogControllerTest` | anonymous 수집 가능, authenticated 수집 가능, 크기 제한 400, PII key 거부 |
| `AdminClientEventLogControllerTest` | 권한별 조회, masking, range/page guard, 401/403 |
| `ClientEventMetadataSanitizerTest` | 금지 key, nested object, long value, control char, JSON length |
| `SecurityConfigTest` 또는 controller slice | `POST /client-events` permitAll, `GET /admin/client-events` 권한 필요 |
| `CorsConfigTest` | `X-Request-Id` exposed header 확인 |

검증 포인트:

- `requestBody`, `responseBody`, `email`, `phoneNumber`, `fileName` key 거부
- message/stack CRLF 제거
- metadata 길이 제한
- duplicate insert 시 사용자 visible error 없이 처리
- 수집 API 실패가 업무 API와 독립적임

### 9.2 Frontend Tests

신규 테스트 후보:

| Test | 내용 |
| --- | --- |
| `clientSession.test.ts` | sessionStorage UUID 생성/재사용 |
| `clientEventLogger.test.ts` | payload 기본값, sanitize, fire-and-forget 실패 swallow |
| `httpErrorTelemetry.test.ts` | Axios error → eventType/severity 변환 |
| `clientErrorHandlers.test.ts` | window error/unhandledrejection handler 등록 및 payload 생성 |
| `apiClient.interceptor.test.ts` | API error 시 telemetry 호출, telemetry 실패해도 원래 error reject |

## 10. Implementation Slice Plan

### 09f-1 Backend Ingest Foundation

- `ClientEventLog` entity/enum/repository 추가
- `ClientEventLogRequest`, `ClientEventLogIngestResponse` 추가
- `ClientEventLogService` 추가
- `ClientEventMetadataSanitizer` 추가
- `POST /client-events` 추가
- CORS `X-Request-Id` exposed header 추가
- Security matcher 추가
- 수집 API 테스트 작성

### 09f-2 Frontend Telemetry Foundation

- `telemetryClient` 추가
- `clientEventApi` 추가
- `clientSession` 추가
- `clientEventLogger` 추가
- `apiClient` response error interceptor 연동
- 전역 JS error/unhandledrejection handler 추가
- 주요 지원자 화면 실패 지점 1~2개 명시적 연동

### 09f-3 Admin Read API

- `GET /admin/client-events`
- `GET /admin/client-events/{id}`
- range/page guard
- 권한별 masking
- 관리자 FU용 검색 조건 지원

### 09f-4 Cleanup / Hardening

- retention cleanup job
- rate limit/dedupe 강화
- volume dashboard 또는 간단한 집계 endpoint 검토
- FE sampling 정책 적용

## 11. Acceptance Criteria

### Backend

- `POST /client-events`는 미인증 상태에서도 수집 가능하다.
- 인증 상태면 principal 원문이 아니라 HMAC hash만 저장한다.
- FE가 보낸 IP/User-Agent/principal 값은 무시한다.
- `relatedCorrelationId`로 실패 업무 API의 `X-Request-Id`를 저장할 수 있다.
- 금지 metadata key는 저장되지 않거나 400으로 거부된다.
- message/stack/route/apiPath는 sanitize + truncate된다.
- `(clientSessionId, clientEventId)` 중복은 중복 insert하지 않는다.
- admin read API는 range/page guard를 가진다.
- `ROLE_RECRUIT_ADMIN`은 민감 필드를 마스킹해서 본다.
- `ROLE_PRIVACY_ADMIN`만 IP/User-Agent/principalHash 원문을 본다.

### Frontend

- API 오류 발생 시 client event가 best-effort로 전송된다.
- telemetry 전송 실패가 사용자 화면 흐름을 막지 않는다.
- telemetry 전송은 전역 loading을 켜지 않는다.
- telemetry 전송은 401/403 redirect를 유발하지 않는다.
- JS runtime error와 unhandled rejection이 수집된다.
- request/response body, 입력값 원문, 파일명 원문을 보내지 않는다.
- 실패한 업무 API 응답의 `X-Request-Id`를 `relatedCorrelationId`로 보낸다.

## 12. Open Questions

1. 운영에서 외부 API prefix가 `/api`로 고정인지, FE `VITE_API_BASE_URL`에 `/api`가 포함되는지, reverse proxy rewrite인지 확정 필요.
2. `principalHash`에 기존 `AUDIT_HMAC_SECRET`을 재사용할지, `CLIENT_EVENT_HMAC_SECRET`을 분리할지 결정 필요.
   - 권장: 초기에는 `AUDIT_HMAC_SECRET` 재사용 가능.
   - 장기적으로는 목적 분리를 위해 별도 secret 검토.
3. `ClientEventLog` 조회 화면을 admin FE에 즉시 만들지, API만 먼저 만들지 결정 필요.
4. rate limit을 in-memory로 시작할지, Redis 기반으로 갈지 운영 규모에 따라 결정 필요.
5. source map 기반 stack 해석을 도입할지 여부는 배포 산출물 관리 정책과 함께 별도 결정.

## 13. Non-goals

- 사용자 행동 분석/마케팅 clickstream 구축 아님.
- 모든 클릭/키 입력/마우스 좌표 수집 아님.
- 감사 로그 대체 아님.
- 장애 모니터링 제품(Sentry 등) 완전 대체 아님.
- 장기 개인정보 보관 저장소 아님.
