# 클라이언트 이벤트 로그 (`client-event-log`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [privacy-audit](privacy-audit.md)(서버 감사 로그 `ActivityLog`·`AuditHmac`·`ROLE_PRIVACY_ADMIN`) · [auth-account](auth-account.md)(`SecurityConfig` 경로 규칙·CORS `X-Request-Id` 노출·`authApi` 텔레메트리 옵션) · [application](application.md)(수동 로깅하는 지원서 섹션 화면) · [job-posting](job-posting.md)(`ApplicationDetailView`의 `skipClientEventLog`)

## 요약

- 브라우저가 `POST /client-events`로 보낸 오류·주요 이벤트를 DB `client_event_log`(`ClientEventLog`)에 쌓는 **운영 진단 로그**. 감사 증적 아님(서버 감사 로그는 [privacy-audit](privacy-audit.md)).
- 수집은 **공개·best-effort·fire-and-forget**. FE는 응답을 안 쓰고, 수집 실패를 다시 로깅하지 않는다.
- 원문 PII 미저장: `message`는 safe code만, `metadata`는 eventType별 allowlist, 사용자는 HMAC만, IP·UA는 서버 추출.
- 3단 in-memory rate limit(1분 고정 윈도우) 초과 429. 보존 90일(매일 04:00 스케줄러 + 수동 트리거).
- 관리자 조회·cleanup API는 **FE 화면 없음**(운영자 직접 호출).
- **소유 화면 없음.** FE 송신 지점 3종: ① 전역 오류 핸들러 ② `apiClient` 응답 인터셉터(HTTP 오류) ③ 지원서 섹션 화면의 수동 `logClientEvent`([application](application.md) 소유).

## 용어

| 용어 | 뜻 |
|---|---|
| `clientSessionId` | 탭 세션 UUID(`sessionStorage['recruit.clientSessionId']`, 접근이 막히면 메모리 id). 형식 `^[A-Za-z0-9-]{8,80}$` |
| `clientEventId` | 이벤트마다 새 UUID. (`clientSessionId`, `clientEventId`) unique `uk_client_event_session_event`가 재전송 중복 판정 키 |
| `ingestCorrelationId` | 수집 요청 자체의 `X-Request-Id`(서버 `CorrelationIdFilter`가 채움) |
| `relatedCorrelationId` | 실패한 **업무 API** 응답 헤더 `X-Request-Id`를 FE가 읽어 보낸 값. CS 추적 핵심 키 |
| safe message code | `^[A-Z][A-Z0-9_]{2,80}$`(3~81자). 자유 문장·한글·소문자 금지 |
| `pageCode`·`operation`·`componentCode` | 화면이 문자열 리터럴로 정하는 식별 코드. 서버는 길이만 자른다(형식·값 검증 없음, 조회 필터 아님) |
| `principalHash`·`principalType` | 로그인 요청만: `AuditHmac.hmacHex("CLIENT_PRINCIPAL:" + username)`, `APPLICANT`/`EMPLOYEE` |
| 민감 4필드 | `ipAddress`·`userAgent`·`principalHash`·`stackSummary` — 조회 시 `ROLE_PRIVACY_ADMIN`만 원문, 그 외 `***` |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/ClientEventLogController.java` | 수집 `POST /client-events`(JSON 전용) |
| controller | `{BE}/controller/AdminClientEventLogController.java` | 목록·단건·cleanup, `includeSensitive` 판정 |
| service | `{BE}/service/ClientEventLogService.java` | 수집 파이프라인(source→rate limit→중복→정제→`saveAndFlush`) |
| service | `{BE}/service/ClientEventRateLimiter.java` | 3단 1분 고정 윈도우, 맵 상한 10,000 |
| service | `{BE}/service/ClientEventMetadataSanitizer.java` | eventType별 allowlist·금지 key·값 형식·JSON 길이 |
| service | `{BE}/service/ClientEventLogReadService.java` | 검색 가드(page·size·기간)·단건 |
| service | `{BE}/service/ClientEventLogCleanupService.java` | 보존기간 경과 bulk delete |
| service | `{BE}/service/ClientEventLogCleanupScheduler.java` | cron 실행, 예외 삼킴 |
| entity | `{BE}/domain/entity/ClientEventLog.java` | `client_event_log` insert-only, 인덱스 7·unique 1 |
| repository | `{BE}/domain/repository/ClientEventLogRepository.java` | `Repository` 마커(insert·조회·bulk delete만) |
| dto | `{BE}/dto/request/ClientEventLogRequest.java` | 수집 요청 + Bean Validation |
| dto | `{BE}/dto/response/ClientEventLogIngestResponse.java` | `{ accepted, duplicate, id }` |
| dto | `{BE}/dto/response/ClientEventLogResponse.java` | 조회 응답 + 권한별 마스킹 |
| dto | `{BE}/dto/response/ClientEventLogCleanupResponse.java` | `{ deletedCount }` |
| enum | `{BE}/enumeration/ClientEventType.java` | 이벤트 유형 14종 |
| enum | `{BE}/enumeration/ClientEventSeverity.java` | `INFO`·`WARN`·`ERROR` |
| enum | `{BE}/enumeration/ClientEventSource.java` | `APPLICANT_WEB`·`ADMIN_WEB`(수집 불가) |
| exception | `{BE}/exception/InvalidClientEventLogException.java` | 수집 검증 400 |
| exception | `{BE}/exception/ClientEventRateLimitExceededException.java` | 429 |
| exception | `{BE}/exception/InvalidClientEventQueryException.java` | 조회 가드 400 |
| exception | `{BE}/exception/ClientEventLogNotFoundException.java` | 단건 404 |
| config | `{BE}/config/SchedulingConfig.java` | `@EnableScheduling`(프로젝트 유일 스케줄러가 cleanup) |
| test | `{BT}/controller/ClientEventLogControllerTest.java` | 수집 API 400·415·duplicate·CORS |
| test | `{BT}/controller/ClientEventLogRateLimitControllerTest.java` | ip 한도 429 |
| test | `{BT}/controller/AdminClientEventLogControllerTest.java` | 조회·마스킹·권한·cleanup |
| test | `{BT}/service/ClientEventLogServiceTest.java` | 수집 파이프라인 |
| test | `{BT}/service/ClientEventMetadataSanitizerTest.java` | metadata 정제 |
| test | `{BT}/service/ClientEventRateLimiterTest.java` | rate limit |
| test | `{BT}/service/ClientEventLogReadServiceTest.java` | 조회 가드 |
| test | `{BT}/service/ClientEventLogCleanupServiceTest.java` | 보존 삭제·범위 |
| test | `{BT}/service/ClientEventLogCleanupSchedulerTest.java` | 예외 비전파 |
| test | `{BT}/domain/repository/ClientEventLogRepositoryTest.java` | unique·검색 쿼리 |
| test | `{BT}/config/SchedulingConfigTest.java` | cron 등록 |

### 프론트

| 구분 | 파일 | 역할 |
|---|---|---|
| api | `{FE}/api/clientEventApi.ts` | `record(payload)` → `POST /client-events` |
| api | `{FE}/api/telemetryClient.ts` | 전용 axios(타임아웃 3초, 인터셉터 없음 — 재귀 로깅·로딩 표시 방지) |
| api | `{FE}/common/clientEventLogger.ts` | `logClientEvent`·`buildPayload`: 공통 필드·FE 정제·예외 삼킴 |
| api | `{FE}/common/clientSession.ts` | `getClientSessionId`·`createOpaqueId` |
| api | `{FE}/common/httpErrorTelemetry.ts` | `logApiError`: axios 오류 → 이벤트, axios config 확장(`skipClientEventLog` 등) |
| api | `{FE}/plugins/clientErrorHandlers.ts` | `installClientErrorHandlers`·`installVueErrorHandler`, stack 정제·해시 |
| types | `{FE}/types/clientEvent.ts` | payload·유니언 타입·`ClientEventContext` |
| test | `{FE}/common/__tests__/clientEventLogger.spec.ts` | routePath 기본값, sessionStorage 차단, 예외 비전파 |

## API 계약

코드 기준(옛 api-contract 섹션 없음). 응답은 `ApiResponse<T>`. 권한은 `{BE}/config/SecurityConfig.java`([auth-account](auth-account.md)) 매처 기준: 수집 = `POST /api/client-events` permitAll. 조회 = `GET /api/admin/client-events/**` `ROLE_RECRUIT_ADMIN`·`ROLE_PRIVACY_ADMIN`(**`ROLE_ADMIN`만 있으면 403** — broad `/api/admin/**`보다 앞 매처). cleanup = `ROLE_PRIVACY_ADMIN`.

| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |
|---|---|---|---|---|---|
| 🟢 | POST | /client-events | JSON `{ eventType, severity, source, clientSessionId, clientEventId, clientOccurredAt?, …선택 필드, metadata? }` | `{ accepted, duplicate, id }` | 공개(비로그인 포함) |
| 🟢 | GET | /admin/client-events | query `eventType?, severity?, source?, applicationId?, jobPostingId?, clientSessionId?, relatedCorrelationId?, from?, to?, page=0, size=20` | `PageResponse<ClientEventLogResponse>` | RECRUIT_ADMIN·PRIVACY_ADMIN |
| 🟢 | GET | /admin/client-events/{id} | 없음 | `ClientEventLogResponse` | RECRUIT_ADMIN·PRIVACY_ADMIN |
| 🟢 | POST | /admin/client-events/cleanup | 없음 | `{ deletedCount }` | PRIVACY_ADMIN |

### 엔드포인트 상세

**POST /client-events — 🟢**
- `Content-Type: application/json`만(form·text는 415).
- 필수: `eventType`·`severity`·`source`(enum), `clientSessionId`·`clientEventId`(`^[A-Za-z0-9-]{8,80}$`).
- 선택(최대 길이): `clientOccurredAt`(zone 없는 LocalDateTime — FE는 로컬 시각 `yyyy-MM-ddTHH:mm:ss.SSS`), `relatedCorrelationId`100, `pageCode`80, `componentCode`80, `routePath`300, `operation`80, `jobPostingId`·`applicationId`(숫자), `httpMethod`10, `apiPath`300, `httpStatus`(숫자), `errorCode`100, `message`(safe code), `stackHash`128, `stackSummary`2000, `frontendVersion`·`browserName`·`browserVersion`·`osName`80, `viewport`40, `timezone`80, `metadata`(평면 객체).
- DTO에 없는 필드(서버만 채움): `ipAddress`(`getRemoteAddr()`), `userAgent`(헤더), `principalHash`, `principalType`, `receivedAt`, `ingestCorrelationId`.
- 응답(모두 200): 저장 `{ accepted: true, duplicate: false, id }` / 중복 `{ false, true, null }` / `client-event-log.enabled=false` `{ false, false, null }`.
- 오류: 400 Bean Validation, 400 "Invalid request."(enum 밖 값·JSON 파싱 실패), 400 `InvalidClientEventLogException`(source·message·metadata 위반, 한글 메시지), 429 "client event 수집 요청이 너무 많습니다.". unique 충돌은 409로 새지 않고 duplicate 200.
- 매핑: FE `clientEventApi.record()` ↔ `ClientEventLogController.record()`.

**GET /admin/client-events — 🟢, FE 미사용**
- `from`/`to`는 ISO date-time(`receivedAt` 기준, 양끝 포함). `to` 없으면 now, `from` 없으면 `to - 7일`.
- `from > to` 400, 기간 90일 초과 400, `page < 0` 400, `size` 1~100 밖 400.
- `clientSessionId`·`relatedCorrelationId`는 trim 후 정확 일치(빈 값이면 조건 없음). 정렬 `receivedAt DESC, id DESC` 고정.
- `ClientEventLogResponse`: 엔티티 전 필드 — `id, receivedAt, clientOccurredAt, eventType, severity, source, clientSessionId, clientEventId, ingestCorrelationId, relatedCorrelationId, pageCode, componentCode, routePath, operation, jobPostingId, applicationId, httpMethod, apiPath, httpStatus, errorCode, message, stackHash, stackSummary, frontendVersion, browserName, browserVersion, osName, viewport, timezone, ipAddress, userAgent, principalHash, principalType, metadataJson`(JSON 문자열 그대로).
- `ROLE_PRIVACY_ADMIN`이 아니면 민감 4필드가 `***`(원래 null이면 null). `message`·`metadataJson`·`stackHash`는 양쪽 다 원문.

**GET /admin/client-events/{id} — 🟢, FE 미사용**
- 없으면 404 "Client event log was not found.". 마스킹은 목록과 같다.

**POST /admin/client-events/cleanup — 🟢, FE 미사용**
- 스케줄러와 같은 `cleanup()`을 즉시 실행하고 삭제 건수를 돌려준다. `ROLE_RECRUIT_ADMIN`은 403. 감사 로그를 남기지 않는다.

## 규칙·불변식

### 수집(서버)
- 처리 순서: enabled 확인 → source 검사 → rate limit → 중복 선확인(`existsByClientSessionIdAndClientEventId`) → metadata 정제 → 필드 정제 → `saveAndFlush` → `DataIntegrityViolationException`은 duplicate 응답. ({BE}/service/ClientEventLogService.java — record)
- `source`는 `APPLICANT_WEB`만. `ADMIN_WEB`은 로그인 여부와 무관하게 400(공개 엔드포인트에서 관리자 로그 위조 차단). ({BE}/service/ClientEventLogService.java — record)
- 서비스에 `@Transactional`을 붙이지 않는다. 붙이면 unique 충돌 catch 후 `UnexpectedRollbackException`이 난다. `save()`가 아니라 `saveAndFlush()`여야 충돌이 catch 안에서 터진다(아니면 전역 409). ({BE}/service/ClientEventLogService.java — record)
- 문자열 필드: 제어문자 → 공백, trim, 컬럼 길이로 truncate, blank → null. `routePath`·`apiPath`는 `?` 이후 제거. ({BE}/service/ClientEventLogService.java — safe·stripQuery)
- `message`: DTO `@Pattern`과 서비스가 같은 safe code 패턴을 이중 검사하고, 통과해도 7자리 이상 숫자열(하이픈 포함, `[0-9][0-9-]{5,}[0-9]`)을 `*`로 바꾼다. ({BE}/service/ClientEventLogService.java — safeMessage·maskLongDigitRuns)
- `receivedAt` = 서버 `Clock`, 정렬·보존 기준. `clientOccurredAt`은 참고값. ({BE}/service/ClientEventLogService.java — record)
- 로그인 사용자는 loginId 원문 대신 `principalHash`만 저장한다(secret `AUDIT_HMAC_SECRET`, 비면 기동 실패 — [privacy-audit](privacy-audit.md)). ({BE}/service/ClientEventLogService.java — record)
- 엔티티는 insert-only. 필수 6필드(`receivedAt`·`eventType`·`severity`·`source`·`clientSessionId`·`clientEventId`) 누락 시 `InvalidClientEventLogException`. Repository는 `JpaRepository`가 아닌 `Repository` 마커다 — update·단건 delete를 추가하지 않는다. ({BE}/domain/entity/ClientEventLog.java — validateRequired)
- 킬 스위치 `client-event-log.enabled`(env `CLIENT_EVENT_LOG_ENABLED`) false면 저장 없이 200(Bean Validation 400은 유지). ({BE}/service/ClientEventLogService.java — record)

### Rate limit

| 단 | key | 기본 한도/분 | 설정 `client-event-log.rate-limit.*` | 적용 |
|---|---|---|---|---|
| 1 | `ip:<remoteAddr>` | 300 | `per-minute-ip` | 항상 — sessionId를 바꿔도 우회 불가 |
| 2 | `session:<ip>:<clientSessionId>` | 60 | `per-minute-session` | 항상 |
| 3 | `principal:<principalHash>` | 120 | `per-minute-principal` | 로그인 시 |

- env는 `CLIENT_EVENT_LOG_RATE_LIMIT_PER_MINUTE_IP`/`_SESSION`/`_PRINCIPAL`. 윈도우는 key별 첫 요청부터 60초 고정, 인스턴스 메모리(다중 인스턴스면 각자 셈).
- 1→2→3 순서로 카운트를 올리고 하나라도 한도를 넘으면 `ClientEventRateLimitExceededException` → 429 `ApiResponse.fail`. 거부된 요청도 카운트를 소모한다. ({BE}/service/ClientEventRateLimiter.java — check·increment)
- 맵 key 상한 10,000: 만료 엔트리를 정리해도 가득이면 신규 key를 429로 거부(fail-closed). ({BE}/service/ClientEventRateLimiter.java — guardCapacity)
- rate limit이 중복 판정보다 먼저라 같은 이벤트 재전송도 한도를 쓴다. ({BE}/service/ClientEventLogService.java — record)

### Metadata 정제
- metadata가 없거나 비면 `metadata_json` null. ({BE}/service/ClientEventMetadataSanitizer.java — sanitize)
- **eventType별 exact allowlist**(아래 표). 목록 밖 key는 400 — 다른 eventType에서 허용된 key여도 400. ({BE}/service/ClientEventMetadataSanitizer.java — ALLOWLIST·sanitize)
- 금지 key(소문자 비교, allowlist 통과 뒤 2차 방어): `name, username, applicantname, email, phone, phonenumber, ci, cihash, password, birth, address, content, answer, essay, resume, coverletter, filename, originalfilename, body, requestbody, responsebody`. allowlist에 넣어도 400. ({BE}/service/ClientEventMetadataSanitizer.java — FORBIDDEN_KEYS)
- key 최대 20개, key 길이 1~50, value는 string·number·boolean·null만(객체·배열 400), 문자열 200자 이하(제어문자 → 공백·trim), 직렬화 JSON이 `client-event-log.max-metadata-json-length`(기본 4000) 이하. 직렬화는 앱 Jackson 설정과 분리된 전용 `JsonMapper`. ({BE}/service/ClientEventMetadataSanitizer.java — sanitize·sanitizeValue)

### 이벤트 타입·심각도

| eventType | 뜻 | 허용 metadata key | 현재 FE 송신 지점 |
|---|---|---|---|
| `PAGE_OPENED` | 핵심 화면 진입 | 없음 | 없음 |
| `CHECKPOINT` | 핵심 흐름 지점 | `checkpointCode` | 없음 |
| `API_ERROR` | HTTP 오류(아래 4종 제외) | `durationMs`, `retryable`, `axiosCode` | `logApiError`, BasicInfo 주소 검색 수동 |
| `API_TIMEOUT` | axios `ECONNABORTED` | `durationMs`, `timeoutMs` | `logApiError` |
| `NETWORK_ERROR` | 응답 없음 | `durationMs`, `axiosCode` | `logApiError` |
| `SESSION_EXPIRED` | 401 | 없음 | `logApiError` |
| `FORBIDDEN` | 403 | 없음 | `logApiError` |
| `JS_ERROR` | 런타임·Vue 오류 | `file`, `line`, `column` | 전역 핸들러 |
| `UNHANDLED_REJECTION` | 처리 안 된 Promise | `reasonType` | 전역 핸들러 |
| `APPLICATION_DRAFT_SAVE_FAILED` | 섹션 저장 실패 | `sectionCode`, `failedStep` | 섹션 8곳 수동 |
| `APPLICATION_SUBMIT_CLICKED` | 제출 클릭 | 없음 | 없음 |
| `APPLICATION_SUBMIT_FAILED` | 제출 실패 | `sectionCode`, `failedStep` | BasicInfo 수동(실제로는 저장·사진 실패) |
| `ATTACHMENT_UPLOAD_FAILED` | 첨부 실패 | `fileSize`, `fileExtension`, `uploadStep` | 없음 |
| `CLIENT_VALIDATION_FAILED` | FE 검증 실패 요약 | `sectionCode`, `fieldCount`, `errorCount` | 없음 |

- 심각도 `INFO`·`WARN`·`ERROR`. 서버는 eventType·severity 조합을 검사하지 않는다. FE 관례: `logApiError`는 `SESSION_EXPIRED`·`FORBIDDEN` = WARN, 그 외 ERROR. 전역 오류 ERROR. 섹션 수동 로깅은 현재 INFO(주소 검색만 ERROR).

### 조회·보존
- 조회 가드는 page·size·기간 순으로 검사한다(위 엔드포인트 상세). ({BE}/service/ClientEventLogReadService.java — validatePaging·validateRange)
- 민감 필드 원문 여부는 컨트롤러가 authority에 `ROLE_PRIVACY_ADMIN`이 있는지로 정한다. principal이 없으면 항상 마스킹. ({BE}/controller/AdminClientEventLogController.java — includeSensitive)
- 보존: `receivedAt < now - retentionDays` 단일 JPQL DELETE(엔티티 로딩 없음). `client-event-log.retention-days`(env `CLIENT_EVENT_LOG_RETENTION_DAYS`, 기본 90)가 1~365 밖이면 기동 실패. ({BE}/service/ClientEventLogCleanupService.java — 생성자·cleanup)
- 스케줄: `client-event-log.cleanup-cron`(env `CLIENT_EVENT_LOG_CLEANUP_CRON`, 기본 `0 0 4 * * *`, 서버 시간대). 예외는 error 로그만 남기고 삼킨다. 분산 락 없음(인스턴스마다 실행, 결과 동일). ({BE}/service/ClientEventLogCleanupScheduler.java — runCleanup)
- 지원자 파기(purge, [privacy-audit](privacy-audit.md))는 이 테이블을 건드리지 않는다. `applicationId`가 담긴 행도 보존기간이 지나야 지워진다.

### 프론트 송신 규약
- 모든 송신은 `logClientEvent()`를 거친다. `clientEventApi`·`telemetryClient`를 직접 부르지 않는다. `logClientEvent`는 payload 생성 예외와 전송 실패를 모두 삼킨다 — 호출부 오류 처리를 깨지 않는다. 반환값이 void라 await하지 않는다. ({FE}/common/clientEventLogger.ts — logClientEvent)
- `buildPayload`가 채우는 값: `source='APPLICANT_WEB'`(관리자 화면도 같다), `clientSessionId`, `clientEventId`, `clientOccurredAt`(로컬 시각), `routePath`(기본값 현재 `location.pathname`, 쿼리 제거), `frontendVersion`(`VITE_APP_VERSION`), `viewport`, `timezone`, 브라우저·OS(UA 파싱). 입력값이 우선(`routePath`는 `sanitizeInput`에 넣지 않는다 — 함정 `8d7485d`). ({FE}/common/clientEventLogger.ts — buildPayload·sanitizeInput)
- FE 정제: `message`는 대문자화 후 safe code가 아니면 `CLIENT_EVENT_RECORDED`로 바꾼다. 문자열은 제어문자 제거·길이 자름, metadata는 앞 20개·key 50자·문자열 200자. **FE는 allowlist를 검사하지 않는다**(아래 함정). ({FE}/common/clientEventLogger.ts — safeMessageCode·sanitizeMetadata)
- 설치 지점(공통 기반, 이 카드 소유 아님): `{FE}/main.ts`가 앱 생성 전에 `installClientErrorHandlers()`, 생성 직후 `installVueErrorHandler(app)`를 호출한다. `{FE}/api/client.ts` 요청 인터셉터가 `requestStartedAt = performance.now()`를 넣고, 응답 인터셉터가 모든 실패에서 401/403 리다이렉트 **전에** `logApiError(error)`를 부른다.

**송신 지점 ① 전역 오류** ({FE}/plugins/clientErrorHandlers.ts)
- window `error` → `JS_ERROR`, message `JS_RUNTIME_ERROR`, metadata `file`(origin·절대 URL·쿼리 제거, `/applicant/<숫자>/form|detail|apply` → `*`)·`line`·`column`.
- `unhandledrejection` → `UNHANDLED_REJECTION`, message `UNHANDLED_PROMISE_REJECTION`, metadata `reasonType`(`typeof reason`).
- Vue `app.config.errorHandler` → `JS_ERROR`, message `VUE_COMPONENT_ERROR`, metadata 없음. Vue 기본 콘솔 출력이 사라지므로 `console.error(err)`를 직접 유지한다.
- stack: 상위 6줄, 줄당 250자, 전체 1200자. origin·절대 URL·쿼리/해시 제거, `applicationId|jobPostingId|id=<숫자>` → `=*`. `stackHash` = FNV-1a 32bit hex.

**송신 지점 ② HTTP 오류** ({FE}/common/httpErrorTelemetry.ts — logApiError)
- `apiClient` 요청만 대상. 분류: `ECONNABORTED` → `API_TIMEOUT` / 응답 없음 → `NETWORK_ERROR` / 401 → `SESSION_EXPIRED` / 403 → `FORBIDDEN` / 그 외 → `API_ERROR`.
- message 코드: `API_TIMEOUT_OCCURRED`·`NETWORK_ERROR_OCCURRED`·`SESSION_EXPIRED`·`FORBIDDEN_ACCESS`·`API_REQUEST_FAILED`.
- `relatedCorrelationId` = 응답 헤더 `x-request-id`(CORS 노출 필요 — [auth-account](auth-account.md)). `apiPath` = `config.url`(쿼리 제거). `errorCode` = 응답 본문 `errorCode`·`code`·`messageCode`·`status` 중 `^[A-Z0-9_]{2,100}$`인 첫 값, 없으면 axios `error.code`. `durationMs` = `performance.now() - requestStartedAt`. `retryable` = 상태 없음·408·429·5xx.
- 요청별 axios config 옵션: `skipClientEventLog: true` → 기록 안 함, `skipSessionExpiredLog: true` → 401만 기록 안 함, `clientEventContext` → `pageCode`·`componentCode`·`operation`·`jobPostingId`·`applicationId` 첨부(현재 사용처 없음).
- 현재 옵션 사용처: `authApi.me`(`skipClientEventLog`, 비로그인 401이 정상)·`authApi.login`(`skipSessionExpiredLog`, 로그인 실패 401) — `{FE}/api/authApi.ts`([auth-account](auth-account.md)). 기지원 단건 조회(404가 정상) — `{FE}/views/applicant/ApplicationDetailView.vue`([job-posting](job-posting.md)).

**송신 지점 ③ 화면 수동 로깅** — [application](application.md) 소유 섹션 화면이 저장 실패 catch에서 `logClientEvent`를 부른다.
- 코드는 각 화면이 문자열 리터럴로 정한다(공용 상수·enum 없음). 규약: `pageCode = APPLICATION_FORM_<섹션>`, `operation = SAVE_DRAFT_<섹션>`, `message` = eventType과 같은 코드, `applicationId` 포함.
- 같은 실패를 `apiClient` 인터셉터가 `API_ERROR`로도 보낸다. `relatedCorrelationId`·`httpStatus`는 자동 이벤트에만 있으니 두 행은 `clientSessionId`+시각으로 묶는다.
- 현재 코드(⚠ = 잘못 복사됨·불일치):

| 파일(`{FE}/views/applicant/application/sections/`) | eventType | pageCode | operation | message |
|---|---|---|---|---|
| `AwardSection.vue` | DRAFT_SAVE_FAILED | `APPLICATION_FORM_AWARD` | `SAVE_DRAFT_AWARD` | = eventType |
| `BasicInfoSection.vue` 저장·사진 업로드·사진 삭제 3곳 | ⚠ SUBMIT_FAILED | `APPLICATION_FORM_BASIC_INFO` | ⚠ `SUBMIT_APPLICATION_BASIC_INFO` | ⚠ `APPLICATION_SUBMIT_CLICKED` |
| `BasicInfoSection.vue` 주소 검색 | API_ERROR(ERROR) | `APPLICATION_FORM_BASIC_INFO_ADDRESS_SEARCH` | `SEARCH_APPLICATION_BASIC_INFO_ADDRESS` | `APPLICATION_SEARCH_ADDRESS` |
| `CareerSection.vue` | DRAFT_SAVE_FAILED | ⚠ `APPLICATION_FORM_CERTIFICATE` | ⚠ `SAVE_DRAFT_CERTIFICATE` | = eventType |
| `CertificateSection.vue` | DRAFT_SAVE_FAILED | `APPLICATION_FORM_CERTIFICATE` | `SAVE_DRAFT_CERTIFICATE` | = eventType |
| `EducationSection.vue` | DRAFT_SAVE_FAILED | `APPLICATION_FORM_EDUCATION` | ⚠ `SUBMIT_APPLICATION_EDUCATION` | = eventType |
| `GapPeriodSection.vue` | DRAFT_SAVE_FAILED | `APPLICATION_FORM_GAP_PERIOD` | `SAVE_DRAFT_GAP_PERIOD` | = eventType |
| `LanguageSection.vue` | DRAFT_SAVE_FAILED | `APPLICATION_FORM_LANGUAGE` | `SAVE_DRAFT_LANGUAGE` | = eventType |
| `MilitarySection.vue` | DRAFT_SAVE_FAILED | ⚠ `APPLICATION_FORM_CERTIFICATE` | ⚠ `SAVE_DRAFT_CERTIFICATE` | = eventType |
| `QuestionAnswerSection.vue` | DRAFT_SAVE_FAILED | `APPLICATION_FORM_QUESTION_ANSWER` | `SAVE_DRAFT_QUESTION_ANSWER` | = eventType |

(eventType 앞의 `APPLICATION_` 생략. 수동 로깅 중 metadata를 보내는 곳은 없다.)

## 변경 레시피

### 화면에 수동 이벤트 추가·수정
1. eventType을 위 표에서 고른다. 새 타입이 필요하면 다음 레시피를 먼저 한다.
2. **이벤트 코드 확인**: `pageCode`·`operation`을 그 파일의 섹션 이름으로 쓴다(`CareerSection.vue` → `APPLICATION_FORM_CAREER`·`SAVE_DRAFT_CAREER`). 다른 섹션에서 복사했다면 반드시 바꾼다. 점검: `grep -rn "pageCode:\|operation:" recruit_front/src/views` 결과에서 같은 코드가 서로 다른 섹션 파일에 나오면 복사 실수다. `message`는 eventType과 같은 코드로 쓰고, eventType과 뜻이 맞는지 본다(저장 실패에 `SUBMIT_*` 금지).
3. metadata key는 그 eventType 행의 허용 key만 쓴다. 파일명·입력값·이름 같은 원문 금지(확장자·크기 같은 파생값만).
4. `logClientEvent`는 catch 안에서 원래 오류 처리(throw·메시지 표시) 전에 부르고 await하지 않는다. `applicationId`·`jobPostingId`는 숫자 id만.
5. 카드 "현재 코드" 표 갱신 → `npm run type-check` → `node tools/check-docs.mjs`.

### 새 eventType·metadata key 추가
1. `{BE}/enumeration/ClientEventType.java`에 추가하고 `{BE}/service/ClientEventMetadataSanitizer.java` `ALLOWLIST`에 행을 넣는다(metadata가 없어도 `Set.of()` 행 필요). 금지 key와 겹치지 않게 한다.
2. `{FE}/types/clientEvent.ts` `ClientEventType` 유니언을 맞춘다.
3. `event_type` 컬럼은 50자. 스키마는 `ddl-auto`(문자열 enum이라 DDL 불필요).
4. `{BT}/service/ClientEventMetadataSanitizerTest.java`에 허용·거부 케이스 추가 → 검증 → 카드 이벤트 표 갱신 → `node tools/check-docs.mjs`.

### HTTP 오류 텔레메트리 제외·분류 변경
1. 정상 흐름의 실패(404 = 미존재, 401 = 비로그인 확인)는 호출부 axios config에 `skipClientEventLog: true`(401만이면 `skipSessionExpiredLog: true`)를 붙인다. 인터셉터 전역 조건은 바꾸지 않는다.
2. 분류·message 코드·metadata는 `{FE}/common/httpErrorTelemetry.ts` `resolveEventType`·`resolveMessageCode`·`buildMetadata` 한 곳에서 바꾸고, metadata는 allowlist 안에서만 쓴다.
3. `npm run type-check` → 카드 규칙 갱신 → `node tools/check-docs.mjs`.

### Rate limit·보존·스케줄 값 변경
1. 운영 값은 env `CLIENT_EVENT_LOG_*`로 바꾼다. 기본값을 바꾸면 `{BR}/application.yaml`과 각 클래스 `@Value` 기본값(`ClientEventRateLimiter`·`ClientEventLogCleanupService`·`ClientEventLogCleanupScheduler`·`ClientEventMetadataSanitizer`·`ClientEventLogService`)을 같이 바꾼다.
2. 보존 1~365일 검증과 스케줄러 예외 삼킴을 유지한다.
3. `{BT}/service/ClientEventRateLimiterTest.java`·`{BT}/service/ClientEventLogCleanupServiceTest.java`·`{BT}/config/SchedulingConfigTest.java` 보강 → 검증 → 카드 갱신 → `node tools/check-docs.mjs`.

## 검증

백엔드(`recruit_back/recruit_backend/`에서, 이 카드 테스트 11개):

```powershell
# Windows PowerShell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.*ClientEventLog*" --tests "com.shinyoung.recruit.service.ClientEvent*" --tests "com.shinyoung.recruit.domain.repository.ClientEventLogRepositoryTest" --tests "com.shinyoung.recruit.config.SchedulingConfigTest" --no-daemon
```

```bash
# Linux
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "com.shinyoung.recruit.controller.*ClientEventLog*" --tests "com.shinyoung.recruit.service.ClientEvent*" --tests "com.shinyoung.recruit.domain.repository.ClientEventLogRepositoryTest" --tests "com.shinyoung.recruit.config.SchedulingConfigTest" --no-daemon
```

`SecurityConfig` 매처나 CORS를 바꿨으면 [auth-account](auth-account.md) 테스트도 돌린다.

프론트(`recruit_front/`에서):

```bash
npm run type-check
npx vitest run src/common/__tests__/clientEventLogger.spec.ts
```

## 함정·결정

- `8d7485d` 텔레메트리 결함 묶음 수정: `sanitizeInput`의 `routePath: undefined`가 현재 경로 기본값을 덮어써 routePath가 늘 비었다(spread 순서 주의 — spec이 회귀 검사), sessionStorage `SecurityError` 시 메모리 id, 로그인 실패 401의 `SESSION_EXPIRED` 오분류(`skipSessionExpiredLog`), Vue 컴포넌트 오류 미수집(`errorHandler` 추가), `authApi.me` 401 제외, `durationMs` 기준 시각 추가.
- `366546f` `retention-days` 1~365 검증 추가 — 0·음수면 다음 cleanup에서 전부 지워질 수 있었다. 범위 검증을 빼지 않는다.
- 섹션 이벤트 코드 복사 실수: `CareerSection.vue`·`MilitarySection.vue`가 자격증 코드(`APPLICATION_FORM_CERTIFICATE`/`SAVE_DRAFT_CERTIFICATE`)를 쓴다. 조회할 때 `pageCode`만 믿지 말고 `routePath`·시각을 같이 본다. `BasicInfoSection.vue`는 저장 실패를 `APPLICATION_SUBMIT_FAILED` + message `APPLICATION_SUBMIT_CLICKED`로 보낸다.
- 관리자 화면 오류도 `APPLICANT_WEB`으로 쌓인다(FE 고정값). `ADMIN_WEB`은 400이라 분리하려면 인증 전용 수집 엔드포인트가 필요하다(미구현).
- IP = `getRemoteAddr()`(forward-headers 설정 없음). 리버스 프록시 뒤면 전원이 프록시 IP로 집계돼 ip 단 300/분이 사이트 전체 한도가 된다.
- FE는 allowlist를 모른다. 허용 밖 metadata key를 보내면 서버 400으로 그 이벤트가 조용히 사라진다. 이벤트가 안 쌓이면 브라우저 네트워크 탭에서 `/client-events` 400 본문부터 본다.
- `VITE_APP_VERSION`을 정의하는 곳이 없어 `frontendVersion`은 항상 비어 있다.
- `clientEventContext` 사용처가 없어 자동 HTTP 오류 이벤트에는 `pageCode`·`operation`·`applicationId`가 없다(`routePath`로 화면을 판별).
- 옛 설계의 FE 샘플링(`PAGE_OPENED`·`CHECKPOINT`)·5초 동일 오류 dedupe·10분 한도는 구현되지 않았다. 렌더마다 throw하는 오류 폭주는 session 단 60/분으로만 억제된다.
- `principalHash`는 감사 로그와 같은 `AUDIT_HMAC_SECRET`을 쓴다(목적별 secret 분리 미적용). secret을 바꾸면 같은 사용자의 과거 행과 연결이 끊긴다.
- `client_event_log` 운영 DDL 스크립트는 `recruit_back/recruit_backend/docs/ops/`에 없다 — 스키마는 `ddl-auto`로 생성된다. 운영이 `validate`/`none`이면 테이블·인덱스를 수동으로 만들어야 한다.
- `recruit_back/recruit_backend/docs/adr/0007-privacy-admin-role-separation.md` — 민감 원문 조회·삭제는 `ROLE_PRIVACY_ADMIN` 전용. narrow 매처가 broad `/api/admin/**`보다 먼저여야 한다.
