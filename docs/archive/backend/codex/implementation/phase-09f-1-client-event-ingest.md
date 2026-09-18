# Phase 09f-1 — Client Event Ingest Foundation 구현

> Phase 09f(지원자 화면 진단 로그)의 첫 슬라이스. **수집(ingest) 파이프라인 기반**만 구현한다 —
> 지원자 화면 오류/체크포인트를 `POST /api/client-events` 로 수집하고 `client_event_log` 테이블에 저장한다.
> 관리자 조회 API는 **09f-3**, retention cleanup은 **09f-4** 로 이월.
>
> 설계 기준: `docs/codex/design/phase-09f-client-event-log-be-design.md`(BE 상세 설계),
> `docs/codex/design/phase-09f-client-event-log-design.md`(원본 요건).

---

## 1. Phase 요약

- `ClientEventLog` 엔티티(insert-only, `BaseEntity` 미상속) + `ClientEventLogRepository`(마커 Repository) 추가.
- `ClientEventType`(14종) / `ClientEventSeverity`(3종) / `ClientEventSource`(2종) enum 추가.
- `ClientEventMetadataSanitizer`: eventType별 exact allowlist 14종 + 금지 key 2차 방어선 21종.
- `ClientEventRateLimiter`: in-memory 고정 윈도우(1분) 3단 rate limit — ip 300/분, ip+session 60/분, principal 120/분.
- `ClientEventLogRequest`(record, @Pattern) + `ClientEventLogIngestResponse`(정적 팩토리 ofAccepted/ofDuplicate/ofDisabled).
- `ClientEventLogService`: 의도적 `@Transactional` 미사용, 서버 추출값 신뢰, principalHash=HMAC, message 마스킹, 중복 race 흡수.
- `ClientEventLogController`: `POST /api/client-events`, `consumes=APPLICATION_JSON_VALUE`, `permitAll`.
- `SecurityConfig`: `/api/client-events` permitAll matcher 추가, CORS `setExposedHeaders(X-Request-Id)`.
- `application.yaml`: `client-event-log` 설정 블록(enabled/retention-days/max-metadata-json-length/cleanup-cron/rate-limit 3종).
- `GlobalExceptionHandler`: `InvalidClientEventLogException`(400) / `ClientEventRateLimitExceededException`(429) 핸들러 추가.

**범위 밖(후속 슬라이스)**: 관리자 조회 API(09f-3), retention bulk delete + 스케줄러(09f-4), 프론트엔드(09f-2).

---

## 2. 구현 범위

### 구현됨

| 항목 | 설명 |
|------|------|
| enum 3종 | `ClientEventType`(14), `ClientEventSeverity`(3), `ClientEventSource`(2) |
| 예외 2종 | `InvalidClientEventLogException`(400), `ClientEventRateLimitExceededException`(429) |
| Entity | `ClientEventLog` — insert-only, unique(client_session_id, client_event_id), 7 인덱스 |
| Repository | `ClientEventLogRepository` — 마커 Repository(save/saveAndFlush/findById/count/existsBy) |
| Sanitizer | `ClientEventMetadataSanitizer` — eventType별 allowlist, 2차 금지 key 방어선 |
| Rate Limiter | `ClientEventRateLimiter` — 3단 in-memory 고정 윈도우, 맵 크기 가드 |
| Request DTO | `ClientEventLogRequest` — record, `@Pattern` 엄격 검증 |
| Response DTO | `ClientEventLogIngestResponse` — record, 정적 팩토리 3종 |
| Service | `ClientEventLogService` — 수집 파이프라인(source 검증→rate limit→중복→sanitize→저장) |
| Controller | `ClientEventLogController` — `POST /client-events`, JSON-only |
| Security | `SecurityConfig` — permitAll matcher, CORS `X-Request-Id` expose |
| Config | `application.yaml` — `client-event-log` 블록 |
| 예외 핸들러 | `GlobalExceptionHandler` — 400/429 매핑 추가 |

### 범위 밖

| 항목 | 이월 Phase |
|------|-----------|
| 관리자 조회 API | 09f-3 |
| Retention bulk delete + 스케줄러 | 09f-4 |
| 프론트엔드 연동 | 09f-2 (별도 FE 프로젝트) |

---

## 3. 변경 파일

### commit f9256ff — 9f-1 client event enum/exception 추가

신규(main):
- `enumeration/ClientEventType.java`
- `enumeration/ClientEventSeverity.java`
- `enumeration/ClientEventSource.java`
- `exception/InvalidClientEventLogException.java`
- `exception/ClientEventRateLimitExceededException.java`

수정(main):
- `exception/GlobalExceptionHandler.java` (+`handleInvalidClientEventLog`, +`handleClientEventRateLimitExceeded`)

### commit 233faf3 — 9f-1 ClientEventLog entity/repository

신규(main):
- `domain/entity/ClientEventLog.java`
- `domain/repository/ClientEventLogRepository.java`

신규(test):
- `domain/repository/ClientEventLogRepositoryTest.java`

### commit accb1e9 — 9f-1 metadata exact allowlist sanitizer

신규(main):
- `service/ClientEventMetadataSanitizer.java`

신규(test):
- `service/ClientEventMetadataSanitizerTest.java`

### commit ce3351d — 9f-1 3단 rate limiter + 설정 블록

신규(main):
- `service/ClientEventRateLimiter.java`

수정(main):
- `src/main/resources/application.yaml` (+`client-event-log` 블록 11줄)

신규(test):
- `service/ClientEventRateLimiterTest.java`

### commit f61df5b — 9f-1 수집 DTO/Service

신규(main):
- `dto/request/ClientEventLogRequest.java`
- `dto/response/ClientEventLogIngestResponse.java`
- `service/ClientEventLogService.java`

수정(main):
- `domain/entity/ClientEventLog.java` (주석 3개 필드 보강 — Task 2 리뷰 반영)

신규(test):
- `service/ClientEventLogServiceTest.java`

### commit e1c14c1 — 9f-1 수집 controller + security/cors 보강

신규(main):
- `controller/ClientEventLogController.java`

수정(main):
- `config/SecurityConfig.java` (+permitAll matcher, +`setExposedHeaders(X-Request-Id)`)
- `service/ClientEventRateLimiter.java` (+`@Autowired` on primary constructor)

신규(test):
- `controller/ClientEventLogControllerTest.java`
- `controller/ClientEventLogRateLimitControllerTest.java`

---

## 4. 신규 클래스

- `enumeration.ClientEventType`
- `enumeration.ClientEventSeverity`
- `enumeration.ClientEventSource`
- `exception.InvalidClientEventLogException`
- `exception.ClientEventRateLimitExceededException`
- `domain.entity.ClientEventLog`
- `domain.repository.ClientEventLogRepository`
- `service.ClientEventMetadataSanitizer`
- `service.ClientEventRateLimiter`
- `dto.request.ClientEventLogRequest`
- `dto.response.ClientEventLogIngestResponse`
- `service.ClientEventLogService`
- `controller.ClientEventLogController`

---

## 5. 수정 클래스

- `exception.GlobalExceptionHandler` — `handleInvalidClientEventLog`(400) / `handleClientEventRateLimitExceeded`(429) 핸들러 2건 추가.
- `config.SecurityConfig` — `POST /api/client-events` permitAll matcher 추가. CORS `corsConfiguration.setExposedHeaders(List.of("X-Request-Id"))` 추가. 주석으로 의도 고정.
- `src/main/resources/application.yaml` — 최하단에 `client-event-log:` 블록 11줄 추가(enabled/retention-days/max-metadata-json-length/cleanup-cron/rate-limit 3종). 전부 환경변수 주입 + 기본값 내장.
- `domain.entity.ClientEventLog` — commit f61df5b에서 3개 필드 Javadoc 주석 보강(Task 2 리뷰 반영): `message`(safe message code 계약), `ipAddress`(서버 추출 + PRIVACY_ADMIN 전용), `userAgent`(서버 추출 + PRIVACY_ADMIN 전용).
- `service.ClientEventRateLimiter` — commit e1c14c1에서 public primary 생성자에 `@Autowired` 추가(다중 생성자 Spring 자동 주입 해석 실패 해결).

---

## 6. 클래스별 설명

### `enumeration.ClientEventType` — Enum

- **책임**: 지원자 화면 진단 이벤트 유형 taxonomy. 수집 ROI가 높은 오류/핵심 체크포인트만 정의(clickstream 아님).
- **값(14종)**: `PAGE_OPENED`, `CHECKPOINT`, `API_ERROR`, `API_TIMEOUT`, `NETWORK_ERROR`, `SESSION_EXPIRED`, `FORBIDDEN`, `JS_ERROR`, `UNHANDLED_REJECTION`, `APPLICATION_DRAFT_SAVE_FAILED`, `APPLICATION_SUBMIT_CLICKED`, `APPLICATION_SUBMIT_FAILED`, `ATTACHMENT_UPLOAD_FAILED`, `CLIENT_VALIDATION_FAILED`.
- **관련**: `ClientEventLog.eventType`, `ClientEventMetadataSanitizer.ALLOWLIST` 키.

### `enumeration.ClientEventSeverity` — Enum

- **책임**: 이벤트 심각도 분류. 값: `INFO`, `WARN`, `ERROR`.
- **관련**: `ClientEventLog.severity`, `ClientEventLogRequest.severity`.

### `enumeration.ClientEventSource` — Enum

- **책임**: 이벤트 발생 채널. 값: `APPLICANT_WEB`, `ADMIN_WEB`. public 수집 API는 `APPLICANT_WEB`만 허용(설계 6.1, Blocker 1). `ADMIN_WEB`은 enum에만 존재하며 별도 인증 endpoint에서만 사용 예정.
- **관련**: `ClientEventLog.source`, `ClientEventLogService.record`에서 서비스 레벨 차단.

### `exception.InvalidClientEventLogException` — Exception

- **책임**: client event 입력 검증 실패(400). 예: source 위조, metadata allowlist 위반, message 형식 위반, 필수값 누락.
- **형태**: `RuntimeException` 단순 상속.
- **핸들러**: `GlobalExceptionHandler.handleInvalidClientEventLog` → HTTP 400.

### `exception.ClientEventRateLimitExceededException` — Exception

- **책임**: 3단 rate limit 초과(429).
- **형태**: `RuntimeException` 단순 상속.
- **핸들러**: `GlobalExceptionHandler.handleClientEventRateLimitExceeded` → HTTP 429.

### `domain.entity.ClientEventLog` — Entity

- **책임**: 지원자 화면 진단 로그 영속. **insert-only** — update/delete 업무 API 없음(삭제는 09f-4 retention cleanup 전용). `BaseEntity` 미상속(자체 `receivedAt`(Clock)).
- **주요 필드**:
  - `id` — `@GeneratedValue IDENTITY`
  - `receivedAt` — 서버 수신 시각(Clock 주입), 정렬/보존 기준. NOT NULL.
  - `clientOccurredAt` — 브라우저 발생 시각, 참고값.
  - `eventType` / `severity` / `source` — Enum(@Enumerated STRING).
  - `clientSessionId` / `clientEventId` — unique 쌍(`uk_client_event_session_event`). length=80.
  - `ingestCorrelationId` — 수집 API 자체의 X-Request-Id.
  - `relatedCorrelationId` — FE가 실패 API 응답에서 읽은 X-Request-Id(CS 상관키).
  - `pageCode` / `componentCode` / `routePath` / `operation` — 화면 컨텍스트.
  - `jobPostingId` / `applicationId` — denormalized 검색 키(FK 없음).
  - `httpMethod` / `apiPath` / `httpStatus` / `errorCode` — API 오류 컨텍스트.
  - `message` — safe message code allowlist 패턴 강제(`^[A-Z][A-Z0-9_]{2,80}$`), DB 500자/서비스 200자.
  - `stackHash`(128) / `stackSummary`(2000) — JS 오류 스택. stackSummary는 ROLE_PRIVACY_ADMIN 전용(설계 8.3).
  - `ipAddress`(64) / `userAgent`(512) — 서버 추출값. ROLE_PRIVACY_ADMIN 전용.
  - `principalHash`(128) — HMAC(`"CLIENT_PRINCIPAL:"+username`). 원문 미저장.
  - `principalType`(30) — 인증 사용자 type(Employee/Applicant 등).
  - `metadataJson`(@Lob) — eventType별 allowlist 통과 metadata JSON.
- **인덱스(7)**: `received_at`, `event_type+received_at`, `client_session_id`, `application_id`, `job_posting_id`, `related_correlation_id`, `source+received_at`.
- **unique 제약**: `(client_session_id, client_event_id)`.
- **생성**: `@Builder` private 생성자 + `validateRequired` 정적 메서드(6개 필수 필드).
- **관련**: `ClientEventLogRepository`, `ClientEventLogService.record`.

### `domain.repository.ClientEventLogRepository` — Repository

- **책임**: `ClientEventLog` insert + 제한적 조회. `JpaRepository` 대신 마커 `Repository<ClientEventLog, Long>` 상속(insert-only 의도 명시, `ActivityLogRepository` 선례).
- **주요 메서드**:
  - `save` — 자체 tx 사용.
  - `saveAndFlush` — 중복 race 흡수에 필수. `save()`만 쓰면 unique violation이 commit 시점에 발생해 service catch를 타지 못함.
  - `findById(Long)` — Optional 반환.
  - `count()` — 테스트/모니터링용.
  - `existsByClientSessionIdAndClientEventId(String, String)` — 중복 선확인.
- **관련**: `ClientEventLogService`, retention bulk delete(09f-4에서 `@Modifying @Query` 추가 예정).

### `service.ClientEventMetadataSanitizer` — Component

- **책임**: client event metadata Map을 검증/직렬화. `@Component`. eventType별 exact allowlist + 2차 금지 key 방어선.
- **상수**:
  - `MAX_KEYS=20`, `MAX_KEY_LENGTH=50`, `MAX_STRING_VALUE_LENGTH=200`.
  - `ALLOWLIST` — `Map<ClientEventType, Set<String>>`(14 entry). 허용 key 없는 eventType(SESSION_EXPIRED, FORBIDDEN 등)은 빈 Set → 키 보내면 즉시 400.
  - `FORBIDDEN_KEYS` — 21종(대소문자 무시): name/username/applicantname/email/phone/phonenumber/ci/cihash/password/birth/address/content/answer/essay/resume/coverletter/filename/originalfilename/body/requestbody/responsebody.
- **주요 메서드**:
  - `sanitize(ClientEventType, Map<String,Object>)` — null/empty → null 반환. allowlist 외 key → 400. FORBIDDEN_KEYS → 400. nested object/array → 400. 문자열 200자 초과 → 400. 직렬화 결과 `maxMetadataJsonLength`(기본 4000) 초과 → 400. 통과 시 JSON 문자열 반환.
  - `sanitizeValue(key, value)` — String: `\p{Cntrl}+` → 공백 치환. Number/Boolean/null: 허용.
- **설정**: `@Value("${client-event-log.max-metadata-json-length:4000}")`.
- **직렬화**: 전용 `JsonMapper.builder().build()` 인스턴스(앱 Jackson 설정 분리, `ActivityLogService` 선례).
- **관련**: `ClientEventLogService.record`, `ClientEventType`.
- **구현 주의**: `\p{Cntrl}+`(+가 있음) — 연속 제어문자를 공백 1개로 collapse. 플랜의 `\p{Cntrl}`와 차이.

### `service.ClientEventRateLimiter` — Component

- **책임**: in-memory 고정 윈도우(60초) 3단 rate limit. `@Component`.
- **3단 구조**:
  - 1차: `ip:` prefix — ip 글로벌. sessionId를 바꿔도 우회 불가(Blocker Major 5 반영).
  - 2차: `session:ip:sessionId` prefix — ip + sessionId 조합.
  - 3차: `principal:hash` prefix — 인증 시만(미인증이면 skip).
- **기본 한도**: ip 300/분, ip+session 60/분, principal 120/분(yaml 주입).
- **주요 메서드**:
  - `check(String ip, String clientSessionId, String principalHash)` — 3단 순차 검사. 초과 시 `ClientEventRateLimitExceededException`(429).
  - `increment(key, limit, now)` — 윈도우 만료 확인(lazy eviction) → 카운터 증가 → 한도 초과 시 예외.
  - `guardCapacity(newKey, now)` — 맵 크기 상한(기본 10,000) 도달 시 만료 엔트리 일괄 정리 → 그래도 가득이면 신규 key 거부(fail-closed).
- **생성자**: `@Autowired` on 4-param public 생성자(yaml 주입용). package-private 5-param 생성자(테스트용 maxEntries 주입).
- **구현 주의**: `@Autowired` 추가는 다중 생성자 환경에서 Spring이 주입 생성자를 오인하는 문제 해결.
- **관련**: `ClientEventLogService.record`, `ClientEventRateLimitExceededException`.

### `dto.request.ClientEventLogRequest` — Request DTO

- **책임**: 수집 요청 body. Java `record`.
- **서버 전용 필드(body로 받지 않음)**: `ipAddress`, `userAgent`, `principalHash`, `principalType`, `receivedAt`, `ingestCorrelationId` — 서비스가 서버에서만 채움(FE body 값 신뢰 금지).
- **필수 검증**:
  - `eventType`, `severity`, `source` — `@NotNull`.
  - `clientSessionId`, `clientEventId` — `@NotBlank` + `@Pattern(regexp = "^[A-Za-z0-9\\-]{8,80}$")`.
  - `message` — `@Pattern(regexp = "^[A-Z][A-Z0-9_]{2,80}$")` (대문자 safe message code만 허용 — 2차 리뷰 Major 2 반영. 한글은 물론 `Hong Gil Dong application failed` 같은 영문 자유 문장도 400. null 허용).
- **선택 필드**: `clientOccurredAt`, `relatedCorrelationId`(100), `pageCode`(80), `componentCode`(80), `routePath`(300), `operation`(80), `jobPostingId`, `applicationId`, `httpMethod`(10), `apiPath`(300), `httpStatus`, `errorCode`(100), `stackHash`(128), `stackSummary`(2000), `frontendVersion`(80), `browserName`(80), `browserVersion`(80), `osName`(80), `viewport`(40), `timezone`(80), `metadata(Map<String,Object>)`.
- **관련**: `ClientEventLogController`, `ClientEventLogService.record`, `ClientEventMetadataSanitizer`.

### `dto.response.ClientEventLogIngestResponse` — Response DTO

- **책임**: 수집 결과 응답. FE telemetry는 fire-and-forget이므로 이 값은 운영 진단/테스트용.
- **필드**: `boolean accepted`, `boolean duplicate`, `Long id`.
- **정적 팩토리**:
  - `ofAccepted(Long id)` — `accepted=true, duplicate=false, id=<저장 id>`.
  - `ofDuplicate()` — `accepted=false, duplicate=true, id=null`.
  - `ofDisabled()` — `accepted=false, duplicate=false, id=null`.
- **구현 주의**: 플랜은 `accepted()`/`duplicate()`/`disabled()` 네이밍이었으나 record 컴포넌트 접근자(accepted(), duplicate())와 이름 충돌로 컴파일 불가. `ofAccepted`/`ofDuplicate`/`ofDisabled`로 변경.
- **관련**: `ClientEventLogService.record`, `ClientEventLogController`.

### `service.ClientEventLogService` — Service

- **책임**: client event 수집 파이프라인. best-effort — 수집 실패가 업무 API에 영향 없음.
- **의도적 `@Transactional` 미사용**: `saveAndFlush` 의 `DataIntegrityViolationException`(중복 race)을 catch해 duplicate 응답으로 흡수해야 하는데, 외부 트랜잭션이 있으면 flush 시 rollback-only 마킹 → catch 후 정상 반환이 `UnexpectedRollbackException`으로 깨짐. repository 호출이 각자 자체 트랜잭션 사용.
- **수집 파이프라인(`record` 메서드)**:
  1. `enabled` false → `ofDisabled()`.
  2. `source != APPLICANT_WEB` → `InvalidClientEventLogException(400)`.
  3. ip 서버 추출(`servletRequest.getRemoteAddr()`), principalHash 계산(`AuditHmac.hmacHex("CLIENT_PRINCIPAL:"+username)`).
  4. `rateLimiter.check(ip, clientSessionId, principalHash)` → 초과 시 429.
  5. `existsByClientSessionIdAndClientEventId` 선확인 → `ofDuplicate()`.
  6. `metadataSanitizer.sanitize(eventType, metadata)`.
  7. `ClientEventLog.builder()` 생성 — 서버 추출값(`receivedAt`, `ipAddress`, `userAgent`, `ingestCorrelationId`) 주입.
  8. `saveAndFlush` → `DataIntegrityViolationException` catch → `ofDuplicate()`.
- **safe 유틸**:
  - `safe(value, max)` — `\p{Cntrl}+` 치환 후 trim, 길이 초과 truncate, blank→null.
  - `stripQuery(path)` — `?` 이후 제거.
  - `safeMessage(message)` — `safe()` 후 `^[A-Z][A-Z0-9_]{2,80}$` 패턴 검증, 위반 시 `InvalidClientEventLogException(400)`. DTO `@Pattern`과 동일 계약 — 컨트롤러 `@Valid`를 거치지 않는 내부 호출 경로 대비(3차 리뷰 Minor).
  - `maskLongDigitRuns(message)` — 7자리 이상 연속 숫자(`[0-9][0-9\\-]{5,}[0-9]`) → `*`. safe code 검증 통과 후에도 적용 — 코드 형태로 위장한 숫자열(`P01012345678` 등) 방어.
- **관련**: `ClientEventLogRepository`, `ClientEventMetadataSanitizer`, `ClientEventRateLimiter`, `AuditHmac`, `CorrelationIdFilter`.

### `controller.ClientEventLogController` — Controller

- **책임**: `POST /api/client-events` 수집 endpoint. `@RestController`. `consumes = APPLICATION_JSON_VALUE`(JSON-only 계약 명시적 고정). `@Valid`로 request 검증.
- **인증**: `@AuthenticationPrincipal CustomUserDetails` — null 허용(미인증 수집 가능). `permitAll` 설정.
- **응답**: `ResponseEntity<ApiResponse<ClientEventLogIngestResponse>>`.
- **관련**: `ClientEventLogService`, `SecurityConfig`.

---

## 7. API 목록

### POST /api/client-events

| 항목 | 내용 |
|------|------|
| Method | POST |
| Path | `/api/client-events` |
| Base Path Prefix | `/api` (`WebMvcConfig`가 controller 패키지에 일괄 부여) |
| Content-Type | `application/json` (JSON-only, 다른 타입 → 415) |
| 인증 | `permitAll` — 미인증/세션 만료/가입 화면 오류도 수집 가능 |
| Rate Limit | ip 300/분, ip+session 60/분, 인증 시 principal 120/분 |

**Request body 예시**:

```json
{
  "eventType": "API_ERROR",
  "severity": "ERROR",
  "source": "APPLICANT_WEB",
  "clientSessionId": "5d7c00ff-0d53-4ea3-bd44-68a9f7d68f9f",
  "clientEventId": "6a1bd08e-3cd1-4c0f-85c0-e6a65e4a0a33",
  "clientOccurredAt": "2026-06-10T11:59:00",
  "relatedCorrelationId": "7c51f646-e75b-4d19-9f69-f82f7f0f2dc4",
  "pageCode": "APPLICATION_FORM",
  "componentCode": "EDUCATION_SECTION",
  "routePath": "/applications/123/form",
  "operation": "SAVE_EDUCATION",
  "jobPostingId": 10,
  "applicationId": 123,
  "httpMethod": "POST",
  "apiPath": "/api/applicant/applications/123/education",
  "httpStatus": 500,
  "errorCode": "INTERNAL_SERVER_ERROR",
  "message": "API_REQUEST_FAILED",
  "stackHash": "abc123",
  "stackSummary": "at saveEducation (app.js:120)",
  "frontendVersion": "2026.06.10-1",
  "browserName": "Chrome",
  "browserVersion": "126",
  "osName": "Windows",
  "viewport": "1440x900",
  "timezone": "Asia/Seoul",
  "metadata": {
    "durationMs": 1250,
    "retryable": false
  }
}
```

**Response 예시 — 수집 성공**:

```json
{
  "success": true,
  "data": {
    "accepted": true,
    "duplicate": false,
    "id": 42
  }
}
```

**Response 예시 — 중복 재전송**:

```json
{
  "success": true,
  "data": {
    "accepted": false,
    "duplicate": true,
    "id": null
  }
}
```

**오류 조건**:

| 조건 | HTTP |
|------|------|
| `source != APPLICANT_WEB` | 400 |
| `clientSessionId` 형식 위반(`^[A-Za-z0-9\\-]{8,80}$`) | 400 |
| `clientEventId` 형식 위반 | 400 |
| `message` safe code 위반(한글/영문 자유 문장/소문자/특수문자) | 400 |
| metadata allowlist 외 key | 400 |
| metadata 금지 key(PII성) | 400 |
| metadata nested object/array | 400 |
| Content-Type이 JSON 아님 | 415 |
| ip/session/principal 한도 초과 | 429 |

---

## 8. Entity 관계 요약

`ClientEventLog`는 **독립 엔티티** — 다른 도메인 엔티티와 FK가 없다.

- `applicationId` / `jobPostingId` — denormalized 검색 키. JOIN 불가(참조 정합성 미강제). FE가 전송한 값을 신뢰하지 않고 safe 처리 후 저장.
- `relatedCorrelationId` — FE가 실패 업무 API 응답의 `X-Request-Id`를 읽어 전송. `activity_log.correlation_id`와 상관분석 가능(FK 없음).
- `ActivityLog`와 관계 없음 — 별도 운영 진단 데이터. retention 90일(단기), ActivityLog는 장기 감사 증적.

```
client_event_log (독립)
  jobPostingId  ──(denormalized)──> job_posting.id
  applicationId ──(denormalized)──> job_application.id
  relatedCorrelationId ──(참고)──> activity_log.correlation_id
```

---

## 9. 비즈니스 규칙

1. **source 검증**: public endpoint는 `APPLICANT_WEB`만 허용. `ADMIN_WEB` source 보내면 즉시 400. enum은 존재하나 별도 인증 endpoint(09f-3+)에서만 사용 예정.

2. **metadata exact allowlist**: 각 `ClientEventType`마다 허용 key 집합이 고정. 같은 key라도 다른 eventType에서는 400. 예: `fileExtension`은 `ATTACHMENT_UPLOAD_FAILED`에서만 허용. allowlist가 없는 eventType(SESSION_EXPIRED, FORBIDDEN 등)은 metadata key 자체가 400.

3. **2차 금지 key**: allowlist 통과 후에도 21종 PII성 key(대소문자 무시)를 차단. allowlist 향후 확장 시 실수로 PII key 추가되는 것 방어.

4. **safe message code 계약(2차 리뷰 Major 2 강화 + 3차 리뷰 Minor 서비스 검증)**: `message`는 자유 문자열 전면 금지. `^[A-Z][A-Z0-9_]{2,80}$` 패턴의 대문자 코드만 허용(`API_REQUEST_FAILED`, `APPLICATION_SUBMIT_FAILED`, `ATTACHMENT_UPLOAD_FAILED`, `SESSION_EXPIRED` 등). 한글뿐 아니라 영문 이름/회사명/학교명/주소성 문자열(`Hong Gil Dong application failed`)도 400. axios 기본 문구(`Request failed with status code 500`)도 자유 문장이므로 정책상 거부 — HTTP 상태 정보는 `httpStatus`/`errorCode` 필드로 전달한다. FE는 표시 문구 대신 messageCode만 전송한다. 동일 패턴을 DTO(`@Pattern`)와 서비스(`safeMessage`) **양쪽에서 검증** — 컨트롤러 `@Valid`를 거치지 않는 내부 호출 경로가 생겨도 자유 문자열이 저장되지 않는다. 7자리 이상 연속 숫자 `*` 마스킹은 검증 통과 후에도 적용(코드 형태로 위장한 숫자열 방어).

5. **3단 rate limit(고정 윈도우 60초)**:
   - 1차: ip 300/분 — sessionId를 바꿔도 동일 ip면 차단.
   - 2차: ip+session 60/분.
   - 3차: 인증 시 principal(HMAC hash) 120/분.
   - 맵 크기 상한(10,000) 도달 시 만료 엔트리 정리 후에도 가득이면 신규 key 거부(fail-closed).

6. **중복 흡수**: `existsByClientSessionIdAndClientEventId` 선확인으로 중복이면 저장 없이 `ofDuplicate()` 반환. 동시 race는 `saveAndFlush`의 unique constraint violation을 catch해 `ofDuplicate()` 반환(409 누출 없음). 업무 API와 달리 409를 FE로 노출하지 않는다.

7. **서버 추출값만 신뢰**: `receivedAt`, `ipAddress`, `userAgent`, `ingestCorrelationId`, `principalHash`, `principalType` — 전부 서버에서만 채움. FE body에서 해당 필드를 보내도 무시.

8. **principalHash**: 인증 사용자의 username(loginId) 원문 미저장. `HMAC_SHA256(secret, "CLIENT_PRINCIPAL:"+username)`. `AuditHmac` 재사용. 미인증이면 null.

9. **routePath/apiPath query string 제거**: `?` 이후 제거 후 저장. 검색 파라미터에 PII가 포함될 수 있음.

10. **enabled 플래그**: `client-event-log.enabled=false` 시 수집 즉시 비활성(`ofDisabled()`). DB 쿼리 없음.

---

## 10. 테스트 커버리지

### ClientEventLogRepositoryTest (4건) — `@DataJpaTest`

| 테스트명 | 검증 내용 |
|---------|----------|
| 저장_조회_enum_매핑 | save → findById, enum 문자열 저장/복원, count |
| 같은_session_event_쌍은_unique_제약으로_거부된다 | saveAndFlush 중복 → DataIntegrityViolationException |
| existsBy로_중복을_선확인한다 | existsByClientSessionIdAndClientEventId true/false |
| 필수값_누락이면_엔티티_생성이_거부된다 | clientEventId 누락 → InvalidClientEventLogException |

### ClientEventMetadataSanitizerTest (11건) — 단위

| 테스트명 | 검증 내용 |
|---------|----------|
| null_또는_빈_metadata는_null을_반환한다 | null/empty → null |
| allowlist_key는_JSON으로_직렬화된다 | durationMs/retryable 통과, JSON 포함 확인 |
| allowlist에_없는_key는_거부된다 | unknownKey → 400 |
| PII성_key는_어느_eventType에서도_거부된다 | mobile/schoolName/companyName/fileName → 400 (어느 allowlist에도 없어 1차 allowlist 검사에서 거부 — FORBIDDEN_KEYS는 allowlist 확장 시 대비용 2차 방어선) |
| 다른_eventType의_허용_key라도_해당_eventType_allowlist에_없으면_거부된다 | fileExtension→API_ERROR: 400, axiosCode→ATTACHMENT: 400 |
| metadata가_허용되지_않는_eventType은_key가_있으면_거부된다 | SESSION_EXPIRED+durationMs → 400 |
| nested_object와_array_value는_거부된다 | Map/List value → 400 |
| 문자열_value_200자_초과는_거부된다 | 201자 → 400 |
| 직렬화_결과가_최대_길이를_초과하면_거부된다 | maxLength=20 → 400 |
| 문자열_value의_제어문자는_공백으로_치환된다 | `\r\n\t` → 공백 collapse |
| null_value는_허용된다 | null value → JSON null |

### ClientEventRateLimiterTest (6건) — 단위

| 테스트명 | 검증 내용 |
|---------|----------|
| 한도_내_요청은_허용된다 | 5회 연속 허용 |
| session_한도_초과는_차단된다 | 3번째 → 429 |
| sessionId를_바꿔도_ip_글로벌_한도로_차단된다 | 4번째 ip 초과 → 429 |
| 인증_사용자는_principal_한도도_적용된다 | 다른 ip/session, 같은 principal 3번째 → 429 |
| 윈도우가_지나면_카운터가_회복된다 | 61초 진행 후 허용 |
| 맵_크기_상한을_넘으면_신규_key는_차단된다 | maxEntries=4 채움 → 신규 429 |

### ClientEventLogServiceTest (8건) — 단위(Mock)

| 테스트명 | 검증 내용 |
|---------|----------|
| 정상_수집시_서버값으로_저장된다 | ip/ua 서버 추출, apiPath/routePath query 제거, receivedAt Clock 기준. message=`API_REQUEST_FAILED`(safe code 픽스처) |
| 인증_사용자는_HMAC_hash만_저장되고_원문은_저장되지_않는다 | principalHash=HMAC, username 미포함 |
| source가_APPLICANT_WEB이_아니면_거부된다 | ADMIN_WEB → InvalidClientEventLogException |
| 자유_문자열_message는_서비스에서도_거부된다 | `Request failed with status code 500` → InvalidClientEventLogException, 저장 없음(3차 리뷰 Minor) |
| safe_code_안의_7자리_이상_연속_숫자는_마스킹된다 | `ERR_01012345678` → `ERR_*` (위장 숫자열 방어) |
| 중복_선확인되면_저장없이_duplicate_응답한다 | existsBy true → ofDuplicate |
| race_충돌도_duplicate_응답으로_흡수되고_예외가_전파되지_않는다 | saveAndFlush 예외 → ofDuplicate |
| enabled가_꺼져있으면_저장하지_않는다 | enabled=false → ofDisabled |

### ClientEventLogControllerTest (14건) — `@SpringBootTest`

| 테스트명 | 검증 내용 |
|---------|----------|
| 미인증_상태에서도_수집된다 | 200, accepted=true |
| 인증_상태에서도_수집된다 | 200, accepted=true |
| anonymous가_ADMIN_WEB_source를_보내면_400이다 | source=ADMIN_WEB → 400 |
| 인증_사용자도_ADMIN_WEB_source는_400이다 | source=ADMIN_WEB → 400 |
| clientSessionId_형식_위반은_400이다 | 한글 sessionId → 400 |
| 허용되지_않은_metadata_key는_400이다 | phoneNumber key → 400 |
| message에_한글이_섞이면_400이다 | 한글 message → 400 |
| 영문_자유_문장_message는_400이다 | `Hong Gil Dong application failed` → 400 (2차 리뷰 Major 2) |
| 소문자_message는_400이다 | `submit failed` → 400 |
| axios_기본_오류_문구도_자유_문장이므로_400이다 | `Request failed with status code 500` → 400 (정책: 자유 문장 전면 금지) |
| safe_message_code는_허용된다 | `API_REQUEST_FAILED` → 200, accepted=true |
| 같은_이벤트_재전송은_duplicate로_흡수되고_409로_새지_않는다 | 2번째 전송 → 200(duplicate=true) |
| JSON이_아닌_content_type은_415다 | text/plain → 415 |
| CORS_응답에_X_Request_Id가_노출된다 | Access-Control-Expose-Headers: X-Request-Id |

### ClientEventLogRateLimitControllerTest (1건) — `@SpringBootTest`(별도 컨텍스트)

| 테스트명 | 검증 내용 |
|---------|----------|
| ip_한도_초과는_sessionId를_바꿔도_429다 | per-minute-ip=2, 3번째 다른 sessionId → 429 |

**실행 명령**:

```bash
# 전체 09f-1 테스트
AES_SECRET_KEY=22791194512954214612461221261067 ./gradlew test \
  --tests "com.shinyoung.recruit.domain.repository.ClientEventLogRepositoryTest" \
  --tests "com.shinyoung.recruit.service.ClientEventMetadataSanitizerTest" \
  --tests "com.shinyoung.recruit.service.ClientEventRateLimiterTest" \
  --tests "com.shinyoung.recruit.service.ClientEventLogServiceTest" \
  --tests "com.shinyoung.recruit.controller.ClientEventLogControllerTest" \
  --tests "com.shinyoung.recruit.controller.ClientEventLogRateLimitControllerTest"
```

**결과**: 전체 **44건 성공** (scoped 실행 확인 — 2차 리뷰 반영 controller 4건 + 3차 리뷰 반영 service 1건 추가).

---

## 11. Known Limitations

### 플랜 대비 구현 편차

1. **ClientEventLogIngestResponse 정적 팩토리 네이밍 변경**: 플랜은 `accepted()`/`duplicate()`/`disabled()` 였으나 Java `record`의 컴포넌트 접근자(accepted(), duplicate())와 이름이 충돌해 컴파일 불가. `ofAccepted`/`ofDuplicate`/`ofDisabled`로 변경.

2. **sanitize 정규식 `\p{Cntrl}+`**: 플랜은 `\p{Cntrl}`(각 문자 개별 치환)이었으나 연속 제어문자를 공백 1개로 collapse하도록 `+` 추가. 기능적으로 더 적합한 동작.

3. **ClientEventRateLimiter `@Autowired` 추가**: 다중 생성자(public 4-param, package-private 5-param) 환경에서 Spring이 주입 생성자를 판단하지 못하는 문제를 `@Autowired`로 해결.

4. **ClientEventLog 주석 보강(3필드)**: commit f61df5b에서 `message`, `ipAddress`, `userAgent` 필드 Javadoc 주석 보강(설계 의도 명시 — safe code 계약, PRIVACY_ADMIN 전용).

5. **message 패턴 강화(2차 리뷰 Major 2)**: 최초 구현의 `^[A-Za-z0-9 _.:\\-]*$`는 영문 자유 문장(이름/회사명/주소성 문자열)을 통과시켜 "safe code" 계약보다 약했다. `^[A-Z][A-Z0-9_]{2,80}$` 대문자 코드 allowlist 패턴으로 교체(리뷰 안 A). `@Size(max=200)`은 패턴이 길이를 상한하므로 제거. FE 연동 시 표시 문구가 아닌 messageCode만 전송하도록 계약 변경 필요.

6. **message 서비스 검증 추가(3차 리뷰 Minor)**: DTO `@Pattern`만으로는 컨트롤러 `@Valid`를 거치지 않는 내부 호출 경로에서 자유 문자열이 저장될 수 있었다(서비스 테스트가 그 동작을 고정하고 있었음). 서비스 `safeMessage()`에 동일 패턴 검증을 추가하고, 서비스 테스트 픽스처를 safe code로 교체 + 자유 문자열 거부 테스트로 정렬.

### 기능 제한

5. **rate limit이 `@Valid` 이후에 적용됨**: 검증 실패 요청(malformed flood)은 rate limiter 카운터에 집계되지 않는다. Bean Validation 실패는 `MethodArgumentNotValidException`으로 처리되어 service에 도달하지 않기 때문.

6. **stackSummary 자유 문자열**: 현재는 `@Size(max=2000)` 외 검증 없이 수집된다. 조회 API(09f-3) 에서 `ROLE_PRIVACY_ADMIN` 전용 projection 예정.

7. **ipAddress/userAgent 원문 저장**: 현재는 원문 저장. 조회 API(09f-3)에서 `ROLE_PRIVACY_ADMIN` 게이팅 예정.

8. **retention 미구현**: `cleanup-cron`, `retention-days` 설정 블록은 yaml에 정의됐으나 실제 bulk delete + 스케줄러는 09f-4.

9. **in-memory rate limiter**: 서버 재시작 시 카운터 초기화. 멀티 인스턴스 환경에서 공유 안 됨. Redis/token bucket 전환은 후속 별도 phase.

10. **reverse proxy 배치 시 ip 단 한계**: IP는 `HttpServletRequest.getRemoteAddr()`로 추출하므로, 운영에서 reverse proxy 뒤에 배치되면 모든 지원자가 proxy IP를 공유해 1차 ip 한도(기본 300/분)가 사이트 전체 telemetry 상한이 되고 2차 `ip+session` 단은 사실상 session 단으로 동작한다. 운영 배치 시 `server.forward-headers-strategy=framework` 정렬 또는 trusted proxy 기준의 `ForwardedHeaderFilter`/`X-Forwarded-For` 처리 정책을 별도 config로 명시해야 한다(또는 `CLIENT_EVENT_LOG_RATE_LIMIT_PER_MINUTE_IP` 상향 — 한도는 모두 환경변수로 조정 가능). **단, `/api/client-events`는 public endpoint이므로 임의 `X-Forwarded-For` 헤더를 무조건 신뢰하면 안 된다** — 클라이언트가 헤더를 위조해 ip rate limit을 우회할 수 있으므로, forwarded 헤더는 반드시 신뢰할 수 있는 proxy의 것만 수용하도록 trusted proxy 목록 기준으로 처리한다(2차 리뷰 Minor 1).

---

## 12. Next Phase 고려사항

### 09f-3 — 관리자 조회 API

- `ClientEventLogRepository`에 검색 쿼리 추가(`findByCondition` + 페이지네이션).
- `ClientEventLogResponse` record (stackSummary/ipAddress/userAgent는 `ROLE_PRIVACY_ADMIN` 전용 projection).
- `ClientEventLogReadService` + `AdminClientEventLogController` (`GET /api/admin/client-events`).
- `SecurityConfig` narrow matcher 추가(`ROLE_RECRUIT_ADMIN` 기본 조회, `ROLE_PRIVACY_ADMIN` 민감 필드).

### 09f-4 — Retention Cleanup

- `ClientEventLogRepository`에 `@Modifying @Query` bulk delete 추가(`receivedAt < cutoff`).
- `ClientEventLogCleanupService`.
- `SchedulingConfig`/`CleanupScheduler`(`@Scheduled` + `cleanup-cron` 설정).
- 관리자 수동 트리거 API(`POST /api/admin/client-events/cleanup`).
