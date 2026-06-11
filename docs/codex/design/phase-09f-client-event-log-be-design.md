# Phase 09f — Client Event Log Backend 설계

## 1. Phase Summary

- Phase name: Phase 09f — Client Event Log (Backend)
- Work type: documentation-only design phase
- Date: 2026-06-10
- Base document: `docs/codex/design/phase-09f-client-event-log-design.md`
- Scope: 지원자 Web UI에서 발생하는 오류·중요 흐름 이벤트를 수집/조회/보존하는 백엔드를 설계한다.
- Status: design completed, implementation not started.

원본 설계 문서의 FE 설계(5장)는 이 저장소 범위 밖이며, API 계약(요청 스키마, `X-Request-Id` 응답 헤더 노출)만 참고 기준으로 유지한다.

## 2. 원본 문서 Open Question 해소

| # | Open Question | 결정 |
| --- | --- | --- |
| 1 | `/api` prefix 적용 방식 | 기존 `WebMvcConfig.configurePathMatch`가 `controller` 패키지에 중앙 부여(infra-01). controller는 `/client-events`로 선언하고 Security matcher는 `/api/client-events`로 정렬한다. |
| 2 | principalHash secret | 기존 `AuditHmac` 빈(`audit.hmac-secret` = `AUDIT_HMAC_SECRET`) 재사용. `hmacHex("CLIENT_PRINCIPAL:" + username)`. 별도 secret 분리는 장기 검토로 이연. |
| 3 | admin FE 화면 | API만 먼저 구현. FE 화면은 별도 저장소/별도 phase. |
| 4 | rate limit 방식 | 초기 slice부터 in-memory 고정 윈도우 guard. Redis 전환은 운영 규모 확인 후 별도 phase. |
| 5 | source map 해석 | 비범위(원본 문서 13장 Non-goals 유지). |

## 3. Slice 계획

| Slice | 내용 |
| --- | --- |
| 09f-1 Backend Ingest Foundation | Entity/Enum/Repository, `POST /client-events`, `ClientEventMetadataSanitizer`, in-memory rate limit, Security matcher/CORS 보강, 테스트 |
| 09f-3 Admin Read API | `GET /admin/client-events`, `GET /admin/client-events/{id}`, range/page guard, 권한별 마스킹, 테스트 |
| 09f-4 Retention | `@EnableScheduling` 도입, 일 단위 cleanup 스케줄러 + 관리자 수동 트리거, 테스트 |

09f-2(Frontend Telemetry Foundation)는 FE 저장소 작업이므로 이 설계의 비범위다.

## 4. 신규/변경 파일

### 09f-1

| Layer | File | 비고 |
| --- | --- | --- |
| Entity | `domain/entity/ClientEventLog.java` | 신규 |
| Enum | `enumeration/ClientEventType.java` | 신규 |
| Enum | `enumeration/ClientEventSeverity.java` | 신규 |
| Enum | `enumeration/ClientEventSource.java` | 신규 |
| Repository | `domain/repository/ClientEventLogRepository.java` | 신규 |
| Request DTO | `dto/request/ClientEventLogRequest.java` | 신규, record |
| Response DTO | `dto/response/ClientEventLogIngestResponse.java` | 신규, record |
| Service | `service/ClientEventLogService.java` | 신규 |
| Service | `service/ClientEventMetadataSanitizer.java` | 신규 |
| Component | `service/ClientEventRateLimiter.java` | 신규 |
| Controller | `controller/ClientEventLogController.java` | 신규 |
| Exception | `exception/InvalidClientEventLogException.java` | 신규 |
| Exception | `exception/ClientEventRateLimitExceededException.java` | 신규 |
| Exception | `exception/GlobalExceptionHandler.java` | 변경 — `InvalidClientEventLogException` → 400, `ClientEventRateLimitExceededException` → 429 명시 매핑 |
| Config | `config/SecurityConfig.java` | 변경 — matcher/`exposedHeaders` 추가 |

### 09f-3

| Layer | File | 비고 |
| --- | --- | --- |
| Service | `service/ClientEventLogReadService.java` | 신규 |
| Response DTO | `dto/response/ClientEventLogResponse.java` | 신규, `from(log, includeSensitive)` |
| Controller | `controller/AdminClientEventLogController.java` | 신규 |
| Exception | `exception/ClientEventLogNotFoundException.java` | 신규 |
| Exception | `exception/InvalidClientEventQueryException.java` | 신규 |
| Exception | `exception/GlobalExceptionHandler.java` | 변경 — `InvalidClientEventQueryException` → 400, `ClientEventLogNotFoundException` → 404 명시 매핑 |
| Config | `config/SecurityConfig.java` | 변경 — admin narrow matcher |

### 09f-4

| Layer | File | 비고 |
| --- | --- | --- |
| Config | `config/SchedulingConfig.java` | 신규 — `@EnableScheduling`(프로젝트 최초 도입) |
| Service | `service/ClientEventLogCleanupService.java` | 신규 |
| Scheduler | `service/ClientEventLogCleanupScheduler.java` | 신규 |
| Controller | `controller/AdminClientEventLogController.java` | 변경 — cleanup 트리거 추가 |
| Config | `config/SecurityConfig.java` | 변경 — cleanup matcher |

## 5. 도메인 설계

### 5.1 ClientEventLog Entity

원본 문서 4.2의 필드/인덱스/unique 제약을 그대로 사용한다. 확정 사항:

- `BaseEntity`를 상속하지 않는다. `ActivityLog` 선례를 따른다 — 독립 진단 엔티티이며 `receivedAt`(서버 시각)이 시간 기준이고, 생성자/수정자 감사 필드가 무의미하다.
- insert-only로 다룬다. update/delete 업무 API를 만들지 않는다(삭제는 retention cleanup 전용).
- 생성은 정적 팩토리 메서드로 한다. setter를 두지 않는다.
- unique 제약: `uk_client_event_session_event (client_session_id, client_event_id)`.
- 인덱스: `received_at`, `(event_type, received_at)`, `client_session_id`, `application_id`, `job_posting_id`, `related_correlation_id`, `(source, received_at)`.

저장 금지 원칙(원본 7장)을 그대로 적용한다:

- `ipAddress`, `userAgent`는 서버(`HttpServletRequest`)에서만 추출한다. FE body 값은 받지 않는다.
- 인증 사용자 loginId 원문 대신 `principalHash`(HMAC)만 저장한다.
- request/response body, query string, 첨부 원본 파일명, 지원자 입력 원문을 저장하지 않는다.

### 5.2 Enum

원본 문서 4.3과 동일하게 3종을 정의한다.

- `ClientEventSource`: `APPLICANT_WEB`, `ADMIN_WEB`. 단, public 수집 API는 09f-1에서 `APPLICANT_WEB`만 허용한다(6.1) — `ADMIN_WEB` 값은 enum에만 존재하고, 향후 별도 admin-authenticated 수집 endpoint에서만 받는다.
- `ClientEventSeverity`: `INFO`, `WARN`, `ERROR`
- `ClientEventType`: 14개 값 전부 정의(`PAGE_OPENED`, `CHECKPOINT`, `API_ERROR`, `API_TIMEOUT`, `NETWORK_ERROR`, `SESSION_EXPIRED`, `FORBIDDEN`, `JS_ERROR`, `UNHANDLED_REJECTION`, `APPLICATION_DRAFT_SAVE_FAILED`, `APPLICATION_SUBMIT_CLICKED`, `APPLICATION_SUBMIT_FAILED`, `ATTACHMENT_UPLOAD_FAILED`, `CLIENT_VALIDATION_FAILED`)

모두 `@Enumerated(EnumType.STRING)`으로 저장한다.

## 6. 수집 파이프라인 (09f-1)

### 6.1 API

| Method | Controller path | 외부 path | Auth |
| --- | --- | --- | --- |
| POST | `/client-events` | `/api/client-events` | permitAll |

요청 DTO는 원본 문서 4.4의 `ClientEventLogRequest` record를 그대로 사용한다. `ipAddress`, `userAgent`, `principalHash`, `principalType`, `receivedAt`, `ingestCorrelationId`는 body로 받지 않는다.

계약 고정:

- controller는 `@PostMapping(value = "/client-events", consumes = MediaType.APPLICATION_JSON_VALUE)` + `@Valid @RequestBody`로 JSON-only 계약을 고정한다. form/text 요청은 415로 거부된다.
- **source 위조 방지(리뷰 Blocker 1)**: public endpoint이므로 `request.source != APPLICANT_WEB`이면 `InvalidClientEventLogException` → 400으로 거부한다. 인증 여부와 무관하게 적용한다. `ADMIN_WEB` 수집은 향후 별도 admin-authenticated endpoint에서만 허용한다.
- `clientSessionId`, `clientEventId`는 형식을 제한한다: `^[A-Za-z0-9-]{8,80}$`(UUID 포함 안전한 opaque id). 위반 시 400. blank/임의 장문 문자열로 unique 컬럼과 rate limiter map key가 오염되는 것을 막는다.

응답:

```json
{
  "success": true,
  "data": { "accepted": true, "duplicate": false, "id": 123 },
  "message": "정상 처리되었습니다."
}
```

### 6.2 ClientEventLogService.record(request, authentication, servletRequest)

1. `client-event-log.enabled=false`면 저장하지 않고 `accepted=false, duplicate=false` 성공 응답을 준다.
2. `source != APPLICANT_WEB`, `clientSessionId`/`clientEventId` 형식 위반, message safe-code 위반(6.3)은 `InvalidClientEventLogException` → 400.
3. rate limit 확인(6.4). 초과 시 `ClientEventRateLimitExceededException` → 429.
4. `receivedAt = LocalDateTime.now(clock)` — 기존 `Clock` 빈 재사용.
5. `ingestCorrelationId = CorrelationIdFilter.currentCorrelationId()`.
6. IP/User-Agent를 `HttpServletRequest`에서 추출한다.
7. 인증 사용자가 있으면 `principalType = CustomUserDetails.userType`, `principalHash = auditHmac.hmacHex("CLIENT_PRINCIPAL:" + username)`. 원문 username은 저장하지 않는다.
8. 문자열 필드 sanitize(6.3) 후 `ClientEventMetadataSanitizer`로 metadata 검증/직렬화.
9. `existsByClientSessionIdAndClientEventId` 중복 선확인 → 중복이면 `accepted=false, duplicate=true` 성공 응답.
10. **insert는 `saveAndFlush`를 사용한다(리뷰 Major 4)** — `save()`만 호출하면 unique violation이 transaction commit 시점에 터져 service 내부 catch를 타지 못하고 기존 `GlobalExceptionHandler`의 `DataIntegrityViolationException` → 409 매핑으로 샌다. `DataIntegrityViolationException` catch 범위 안에서 flush가 발생해야 하며, catch 시 `accepted=false, duplicate=true` 성공 응답을 준다(unique 제약이 최종 방어선).

저장 실패(중복/검증 외 예외)는 수집 API 응답만 실패 처리하고 서버 로그에 남긴다. 업무 API와는 트랜잭션/흐름이 완전히 독립이다. 진단 로그 API 실패를 다시 진단 로그로 남기지 않는다.

### 6.3 Sanitize 규칙

- `stackSummary`, `routePath`, `apiPath`, `operation`, `pageCode`, `componentCode`, `errorCode` 등 자유 문자열: ISO 제어문자(CR/LF/TAB 포함) 제거 + 컬럼 길이 truncate.
- `apiPath`, `routePath`: `?` 이후 query string 제거.
- `clientOccurredAt`은 참고값으로 저장만 하고 정렬/보존 기준으로 쓰지 않는다.

**message는 자유 문자열로 받지 않는다(리뷰 Blocker 3, 권장안 A 채택)**:

- message는 FE가 정의한 safe message code만 허용한다. 상세 원인 추적은 `errorCode`, `eventType`, `httpStatus`, `apiPath`, `relatedCorrelationId`로 한다.
- BE 강제 규칙(2차 리뷰 Major 2로 강화): 패턴 `^[A-Z][A-Z0-9_]{2,80}$`(대문자 코드)만 허용한다. 최초 패턴 `^[A-Za-z0-9 _.:\-]{0,200}$`는 영문 자유 문장(이름/회사명/주소성 문자열)을 통과시켜 "safe code" 계약보다 약했으므로 폐기. 한글·`@`는 물론 소문자 문장·axios 기본 문구(`Request failed with status code 500`)도 400. FE는 표시 문구 대신 messageCode(`API_REQUEST_FAILED` 등)만 전송한다. FE는 fire-and-forget이므로 사용자 영향 없다.
- 패턴을 통과해도 7자리 이상 연속 숫자(하이픈 개입 포함)는 `*`로 마스킹한다 — 검증 우회 경로 대비 2차 방어.

### 6.4 ClientEventRateLimiter

in-memory 고정 윈도우 카운터(`ConcurrentHashMap`). **`clientSessionId`는 client-controlled 값이므로 단독 key로 쓰지 않는다(리뷰 Major 5)** — sessionId만 바꾸면 같은 IP에서 우회 가능하다. 다음 3단으로 잡는다.

| 단계 | key | 기본 한도 | 적용 |
| --- | --- | --- | --- |
| 1차 global | `ip` | 1분 300건 | 항상 — sessionId 변조 우회 차단 |
| 2차 session | `ip + ":" + clientSessionId` | 1분 60건 | 항상 |
| 3차 principal | `principalHash` | 1분 120건 | 인증 상태일 때만 |

- 어느 단계든 초과하면 `ClientEventRateLimitExceededException` → 429. FE는 fire-and-forget이므로 사용자 흐름에 영향 없다.
- map key 폭증 가드: `clientSessionId` 형식 제한(6.1)으로 임의 장문 key를 차단하고, 맵 크기 상한 초과 시 신규 key의 수집을 제한하거나 oldest 윈도우 엔트리를 eviction한다. 윈도우 경과 엔트리는 접근 시 lazy eviction.
- Redis/token bucket 전환은 운영 트래픽 확인 후 별도 phase.

### 6.5 ClientEventMetadataSanitizer

**metadata는 eventType별 exact allowlist만 허용한다(리뷰 Blocker 2)**. 원본 문서 4.5는 이벤트별 key를 "허용 예"로 제시했으나, 금지어 목록(denylist)만으로는 `mobile`, `residentNo`, `schoolName`, `companyName`, `kakaoId` 같은 PII성 key가 통과한다. 지원자 화면에서 발생하는 로그라 PII 혼입 확률이 높으므로 allowlist를 계약으로 고정한다.

eventType별 exact allowlist:

| eventType | 허용 key |
| --- | --- |
| `API_ERROR` | `durationMs`, `retryable`, `axiosCode` |
| `API_TIMEOUT` | `durationMs`, `timeoutMs` |
| `NETWORK_ERROR` | `durationMs`, `axiosCode` |
| `SESSION_EXPIRED` | (없음 — metadata 불허) |
| `FORBIDDEN` | (없음 — metadata 불허) |
| `JS_ERROR` | `file`, `line`, `column` |
| `UNHANDLED_REJECTION` | `reasonType` |
| `APPLICATION_DRAFT_SAVE_FAILED` | `sectionCode`, `failedStep` |
| `APPLICATION_SUBMIT_CLICKED` | (없음 — metadata 불허) |
| `APPLICATION_SUBMIT_FAILED` | `sectionCode`, `failedStep` |
| `ATTACHMENT_UPLOAD_FAILED` | `fileSize`, `fileExtension`, `uploadStep` |
| `CLIENT_VALIDATION_FAILED` | `sectionCode`, `fieldCount`, `errorCount` |
| `PAGE_OPENED` | (없음 — metadata 불허) |
| `CHECKPOINT` | `checkpointCode` |

검증 규칙(순서대로 적용, 위반 시 `InvalidClientEventLogException` → 400. FE는 결과를 사용하지 않으므로 사용자 영향 없다):

1. allowlist에 없는 key는 400으로 거부한다. 다른 eventType의 허용 key여도 해당 eventType allowlist에 없으면 거부한다(예: `API_ERROR`에 `fileExtension` → 400).
2. **금지 key 패턴은 allowlist 통과 후에도 적용되는 2차 방어선이다**(대소문자 무시): `name`, `userName`, `applicantName`, `email`, `phone`, `phoneNumber`, `ci`, `ciHash`, `password`, `birth`, `address`, `content`, `answer`, `essay`, `resume`, `coverLetter`, `fileName`, `originalFilename`, `body`, `requestBody`, `responseBody`. allowlist가 향후 확장될 때 PII key가 실수로 추가되는 것을 막는다.
3. 공통 제한: key 최대 20개, key 길이 50 이하, value는 `String`/`Number`/`Boolean`/`null`만(nested object/array 금지), 문자열 value 200자 이하, 직렬화 결과 4000자 이하(`client-event-log.max-metadata-json-length`).

### 6.6 예외 매핑 (GlobalExceptionHandler)

기존 도메인 예외 명시 매핑 패턴을 따라 `GlobalExceptionHandler`에 추가한다(리뷰 Major 6).

| 예외 | HTTP status |
| --- | --- |
| `InvalidClientEventLogException` | 400 |
| `ClientEventRateLimitExceededException` | 429 |
| `InvalidClientEventQueryException` (09f-3) | 400 |
| `ClientEventLogNotFoundException` (09f-3) | 404 |

수집 경로의 unique 중복은 6.2의 `saveAndFlush` + catch로 service에서 흡수하므로 전역 `DataIntegrityViolationException` → 409 매핑으로 새지 않아야 한다.

## 7. Security / CORS 보강

`SecurityConfig.filterChain`에 추가한다. narrow matcher를 broad `/api/admin/**`보다 앞에 두는 기존 audit/retention 관례를 따른다.

```java
.requestMatchers(HttpMethod.POST, "/api/client-events").permitAll()
.requestMatchers(HttpMethod.POST, "/api/admin/client-events/cleanup").hasAuthority("ROLE_PRIVACY_ADMIN")
.requestMatchers(HttpMethod.GET, "/api/admin/client-events/**").hasAnyAuthority("ROLE_RECRUIT_ADMIN", "ROLE_PRIVACY_ADMIN")
```

- 수집 permitAll 근거: 로그인 전 오류, 세션 만료 오류, 회원가입 화면 오류도 수집 대상이다. 현재 `anyRequest().permitAll()`이지만 명시적 matcher로 의도를 고정한다.
- cleanup은 삭제(write)이므로 retention 관례에 따라 `ROLE_PRIVACY_ADMIN` 전용.
- CORS: `corsConfiguration.setExposedHeaders(List.of("X-Request-Id"))` 추가(현재 부재). FE가 실패 업무 API 응답에서 `X-Request-Id`를 읽어 `relatedCorrelationId`로 보내기 위한 필수 보강. 추가 custom request header는 도입하지 않는다(`clientSessionId`는 body).

## 8. 관리자 조회 (09f-3)

`AuditActivityReadService`(09b) 패턴을 따른다.

### 8.1 API

| Method | Controller path | 외부 path | Auth |
| --- | --- | --- | --- |
| GET | `/admin/client-events` | `/api/admin/client-events` | `ROLE_RECRUIT_ADMIN`, `ROLE_PRIVACY_ADMIN` |
| GET | `/admin/client-events/{id}` | `/api/admin/client-events/{id}` | `ROLE_RECRUIT_ADMIN`, `ROLE_PRIVACY_ADMIN` |

검색 조건: `eventType`, `severity`, `source`, `applicationId`, `jobPostingId`, `clientSessionId`, `relatedCorrelationId`, `from`/`to`(receivedAt 기준), `page`/`size`.

### 8.2 Guard

- page size 상한 100.
- 범위 미지정 시 default 최근 7일(진단 로그 — 감사 read의 30일보다 짧게, 원본 문서 값 유지).
- 범위 상한 90일. `Duration` 비교로 검증(9b 리뷰 선례 — `toDays()` 절삭 회피).
- 정렬: `receivedAt DESC, id DESC` 고정.
- 위반 시 `InvalidClientEventQueryException` → 400.
- 목록 응답은 `PageResponse<ClientEventLogResponse>`.

### 8.3 권한별 마스킹

`ClientEventLogResponse.from(log, includeSensitive)` — `AuditActivityResponse.mask` 선례와 동일.

- `ROLE_PRIVACY_ADMIN`(includeSensitive=true): `ipAddress`, `userAgent`, `principalHash`, `stackSummary` 원문.
- `ROLE_RECRUIT_ADMIN`: 위 4개 필드 `***` 마스킹.
- 마스킹 분기는 controller에서 authority 확인 후 service에 boolean으로 전달한다(09b와 동일).

필드별 노출 근거(리뷰 Blocker 3):

- `message`: 수집 시점에 safe message code 패턴이 강제되므로(6.3) 양 권한 노출.
- `metadataJson`: eventType별 exact allowlist만 저장되므로(6.5) 양 권한 노출.
- `stackSummary`: 자유 문자열 성격이 남아 PII 혼입 가능성을 배제할 수 없으므로 `ROLE_PRIVACY_ADMIN` 전용. `stackHash`는 group by용이므로 양 권한 노출.

## 9. Retention (09f-4)

- `SchedulingConfig`(신규)에서 `@EnableScheduling`을 도입한다(프로젝트 최초).
- `ClientEventLogCleanupService.cleanup()`: `deleteByReceivedAtBefore(now(clock) - retentionDays)` bulk delete, 삭제 건수 반환. `@Transactional`. repository 메서드는 파생 delete(엔티티 단건 로딩 후 삭제)가 아니라 **명시적 `@Modifying @Query`의 단일 DELETE 문**으로 구현한다.
- `ClientEventLogCleanupScheduler`: cron 외부 설정(`client-event-log.cleanup-cron`, 기본 매일 04:00)으로 `cleanup()` 호출. 예외는 로그만 남기고 전파하지 않는다.
- 수동 트리거: `POST /admin/client-events/cleanup` → 같은 서비스 호출, `{ "deletedCount": n }` 응답. `ROLE_PRIVACY_ADMIN` 전용.
- 대용량 batch 분할/partition은 운영 데이터 규모 확인 후 별도 검토(비범위).

## 10. 설정

```yaml
client-event-log:
  enabled: ${CLIENT_EVENT_LOG_ENABLED:true}
  retention-days: ${CLIENT_EVENT_LOG_RETENTION_DAYS:90}
  max-metadata-json-length: ${CLIENT_EVENT_LOG_MAX_METADATA_JSON_LENGTH:4000}
  cleanup-cron: ${CLIENT_EVENT_LOG_CLEANUP_CRON:0 0 4 * * *}
  rate-limit:
    per-minute-ip: ${CLIENT_EVENT_LOG_RATE_LIMIT_PER_MINUTE_IP:300}
    per-minute-session: ${CLIENT_EVENT_LOG_RATE_LIMIT_PER_MINUTE_SESSION:60}
    per-minute-principal: ${CLIENT_EVENT_LOG_RATE_LIMIT_PER_MINUTE_PRINCIPAL:120}
```

- HMAC secret은 신규 항목 없이 기존 `audit.hmac-secret`(`AUDIT_HMAC_SECRET`)을 재사용한다.
- 모든 값은 외부 설정 주입. 하드코딩 금지.

## 11. 테스트 전략

| Slice | Test | 내용 |
| --- | --- | --- |
| 09f-1 | `ClientEventLogServiceTest` | 정상 저장, sanitize, message safe-code 거부/숫자열 마스킹, principal hash(원문 미저장), enabled=false, server-only ip/ua, **실제 unique 충돌 duplicate** — `saveAndFlush` 후 `accepted=false, duplicate=true` 응답이고 `GlobalExceptionHandler` 409로 새지 않음 |
| 09f-1 | `ClientEventMetadataSanitizerTest` | **exact allowlist 위반 400**: `unknownKey`, `mobile`, `schoolName`, `companyName`, `fileName`, `API_ERROR`에 `fileExtension`, `ATTACHMENT_UPLOAD_FAILED`에 `axiosCode`. 금지 key 2차 방어선, nested, key 수/길이, value 길이, JSON 길이, 제어문자 |
| 09f-1 | `ClientEventRateLimiterTest` | 단계별(ip/ip+session/principal) 한도 내 허용·초과 차단, **sessionId 변조 시 ip 단 한도로 차단**, 윈도우 경과 후 회복, 맵 크기 상한 가드 |
| 09f-1 | `ClientEventLogRepositoryTest` | 저장/조회, unique 제약 위반 (`@DataJpaTest` + H2) |
| 09f-1 | `ClientEventLogControllerTest` | anonymous/authenticated 수집, **anonymous·authenticated `source=ADMIN_WEB` → 400**, `clientSessionId` 형식 위반 400, 검증 400, PII key 400, 429, JSON-only(form/text 415), permitAll |
| 09f-1 | CORS 검증 | `X-Request-Id` exposed header 확인 |
| 09f-3 | `ClientEventLogReadServiceTest` | range/page guard(위반 400), default 7일, 정렬, not found 404 |
| 09f-3 | `AdminClientEventLogControllerTest` | 권한별 마스킹(stackSummary 포함), 401/403, 검색 조건 |
| 09f-4 | `ClientEventLogCleanupServiceTest` | 기준일 경계 삭제, 삭제 건수 |
| 09f-4 | controller/scheduler 테스트 | cleanup 트리거 권한, 스케줄러 예외 무전파 |

- 실제 LDAP 연결 없음. MockMvc slice + H2.
- AssertJ, 한글 설명형 메서드명 허용.
- 각 slice 완료 시 관련 scoped 테스트 실행(전체 `clean test`는 명시 요청 시에만).

## 12. Acceptance Criteria

원본 문서 11장 Backend 항목을 그대로 적용한다.

- `POST /api/client-events`는 미인증 상태에서도 수집 가능하다.
- public 수집 API는 `source=APPLICANT_WEB`만 허용한다. `ADMIN_WEB`은 인증 여부와 무관하게 400으로 거부된다.
- 인증 상태면 principal 원문이 아니라 HMAC hash만 저장한다.
- FE가 보낸 IP/User-Agent/principal 값은 무시한다.
- `relatedCorrelationId`로 실패 업무 API의 `X-Request-Id`를 저장할 수 있다.
- metadata는 eventType별 exact allowlist 외 key가 하나라도 있으면 400으로 거부된다. 금지 key 패턴은 2차 방어선으로 함께 적용된다.
- message는 safe message code 패턴(`^[A-Z][A-Z0-9_]{2,80}$`, 2차 리뷰 Major 2로 강화)만 허용되고, 7자리 이상 연속 숫자는 마스킹된다.
- stack/route/apiPath는 sanitize + truncate된다.
- `(clientSessionId, clientEventId)` 중복은 중복 insert하지 않는다. race 충돌도 `saveAndFlush` + catch로 `duplicate=true` 성공 응답으로 흡수되며 409로 새지 않는다.
- rate limit은 ip/ip+session/principal 3단으로 동작하고, 초과는 429로 거부되며 업무 API에 영향이 없다.
- admin read API는 range(default 7일/max 90일)/page(최대 100) guard를 가진다.
- `ROLE_RECRUIT_ADMIN`은 ip/userAgent/principalHash/stackSummary를 마스킹해서 본다.
- `ROLE_PRIVACY_ADMIN`만 해당 원문을 본다.
- retention 삭제는 `receivedAt` 기준이며 스케줄러와 관리자 트리거 양쪽에서 실행 가능하다.

## 13. Non-goals

- FE telemetry client 구현(별도 저장소 09f-2).
- admin 조회 FE 화면.
- Redis 기반 rate limit.
- source map 기반 stack 해석.
- clickstream/마케팅 분석, 감사 로그 대체, 장기 개인정보 보관(원본 문서 13장 유지).

## 14. 리뷰 반영 이력

2026-06-10 설계 리뷰(`instruction.md`) 반영:

| # | 등급 | 지적 | 반영 |
| --- | --- | --- | --- |
| 1 | Blocker | public API에서 `source=ADMIN_WEB` 위조 가능 | 6.1 — 09f-1은 `APPLICANT_WEB`만 허용, `ADMIN_WEB` → 400. 테스트 추가 |
| 2 | Blocker | metadata 정책이 실질 denylist | 6.5 — eventType별 exact allowlist로 고정, 금지 key 패턴은 2차 방어선. 테스트 추가 |
| 3 | Blocker | message/stackSummary/metadataJson 양 권한 노출 전제 약함 | 6.3 — message는 safe code 패턴 강제(권장안 A), 8.3 — stackSummary는 PRIVACY_ADMIN 전용 마스킹 |
| 4 | Major | duplicate race가 commit 시점 flush로 catch를 못 탐 | 6.2 — `saveAndFlush` 명시, catch 범위 내 flush, 409 누출 금지. 실제 충돌 테스트 |
| 5 | Major | rate limit key가 client-controlled | 6.4 — ip/ip+session/principal 3단, sessionId 형식 제한, 맵 크기 가드 |
| 6 | Major | `GlobalExceptionHandler` 변경 누락 | 4장 변경 파일 추가, 6.6 — 400/429/400/404 매핑 명시 |
| - | 보정 | JSON-only 계약, bulk delete 명시 | 6.1 — `consumes` + `@Valid @RequestBody` 고정, 9장 — `@Modifying @Query` 명시 |

검증 메모: `GlobalExceptionHandler`의 `DataIntegrityViolationException` → 409 매핑과 도메인 예외 명시 매핑 패턴은 코드에서 확인했다(리뷰 전제 사실과 일치).

## 15. Next Phase Recommendation

09f-1 → 09f-3 → 09f-4 순서로 구현한다. 각 slice는 Entity/Repository/Service/Controller/DTO/Test를 함께 추가하고, `docs/codex/implementation/` Markdown + `docs/codex/reports/` HTML을 동반 갱신한다.
