# Phase 09f-3 — Admin Client Event Read API 구현

> Phase 09f(지원자 화면 진단 로그)의 세 번째 슬라이스. **관리자 조회 파이프라인**을 구현한다 —
> `GET /api/admin/client-events`(검색) + `GET /api/admin/client-events/{id}`(단건 조회).
> 권한별 민감 필드 projection(마스킹/원문)과 조회 가드(page size / 날짜 범위 / default 범위)를 포함한다.
> retention cleanup은 **09f-4**로 이월.
>
> 설계 기준: `docs/codex/design/phase-09f-client-event-log-be-design.md`(§8 가드/마스킹, §6.6 예외 매핑).

---

## 1. Phase 요약

- `ClientEventLogNotFoundException`(404) / `InvalidClientEventQueryException`(400) 예외 추가. `GlobalExceptionHandler` 핸들러 2건 추가.
- `ClientEventLogRepository.search` — JPQL `@Query`(9 필터 + `Pageable`), `receivedAt DESC, id DESC` 고정 정렬. 기존 Repository에 메서드 추가.
- `ClientEventLogRepositoryTest` — `search는_필터와_최신순_정렬을_지원한다` 1건 추가(총 5건).
- `ClientEventLogResponse` — 34필드 `record`. `from(log, includeSensitive)` 정적 팩토리. `includeSensitive=false`이면 `ipAddress`/`userAgent`/`principalHash`/`stackSummary` 4필드를 `"***"` 마스킹. `message`(safe code)/`metadataJson`(exact allowlist)/`stackHash`(group by용)는 양 권한 모두에 노출.
- `ClientEventLogReadService` — `@Transactional(readOnly=true)`. 가드: page size 최대 100 / 범위 최대 90일(Duration 비교) / default 최근 7일(감사 30일보다 짧음). `normalize` 유틸(trim, blank→null).
- `AdminClientEventLogController` — `GET /admin/client-events`(11파라미터) + `GET /admin/client-events/{id}`. `includeSensitive` = `ROLE_PRIVACY_ADMIN` 보유 시만 `true`. `AdminAuditController` 선례.
- `SecurityConfig` — `GET /api/admin/client-events/**` → `ROLE_RECRUIT_ADMIN/ROLE_PRIVACY_ADMIN` narrow matcher를 broad `/api/admin/**` 앞에 삽입. `ROLE_ADMIN` 단독 사용자는 403(의도적 privacy-tightening — 감사 read 선례와 동일).

**범위 밖(후속 슬라이스)**: retention bulk delete + 스케줄러(09f-4), 프론트엔드 연동(09f-2 별도 FE 프로젝트).

---

## 2. 구현 범위

### 구현됨

| 항목 | 설명 |
|------|------|
| 예외 2종 | `ClientEventLogNotFoundException`(404), `InvalidClientEventQueryException`(400) |
| Repository 검색 쿼리 | `ClientEventLogRepository.search` — 9 필터 + Pageable, receivedAt DESC/id DESC 고정 정렬 |
| Repository 테스트 보강 | `search는_필터와_최신순_정렬을_지원한다` 추가(총 5건) |
| Response DTO | `ClientEventLogResponse` — 34필드 record, `from(log, includeSensitive)`, 마스킹 4필드 |
| Read Service | `ClientEventLogReadService` — 가드(size 100 / 범위 90일 / default 7일) + `normalize` |
| Admin Controller | `AdminClientEventLogController` — 검색 + 단건 조회, `includeSensitive` 결정 로직 |
| Security matcher | `SecurityConfig` — `GET /api/admin/client-events/**` narrow matcher 삽입 |
| 예외 핸들러 | `GlobalExceptionHandler` — 400/404 핸들러 2건 추가 |

### 범위 밖

| 항목 | 이월 Phase |
|------|-----------|
| Retention bulk delete + 스케줄러 | 09f-4 |
| 프론트엔드 연동 | 09f-2 (별도 FE 프로젝트) |

---

## 3. 변경 파일

### commit 43cce7f — 9f-3 조회 예외 2종 + 핸들러 + repository search

신규(main):
- `exception/ClientEventLogNotFoundException.java`
- `exception/InvalidClientEventQueryException.java`

수정(main):
- `exception/GlobalExceptionHandler.java` (+`handleInvalidClientEventQuery`(400), +`handleClientEventLogNotFound`(404))
- `domain/repository/ClientEventLogRepository.java` (+`search` @Query 메서드)

수정(test):
- `domain/repository/ClientEventLogRepositoryTest.java` (+`search는_필터와_최신순_정렬을_지원한다`)

### commit a2418f5 — 9f-3 ClientEventLogResponse + ReadService

신규(main):
- `dto/response/ClientEventLogResponse.java`
- `service/ClientEventLogReadService.java`

신규(test):
- `service/ClientEventLogReadServiceTest.java`

### commit fe8dbfc — 9f-3 AdminClientEventLogController + SecurityConfig narrow matcher

신규(main):
- `controller/AdminClientEventLogController.java`

수정(main):
- `config/SecurityConfig.java` (+`GET /api/admin/client-events/**` narrow matcher)

신규(test):
- `controller/AdminClientEventLogControllerTest.java`

---

## 4. 신규 클래스

- `exception.ClientEventLogNotFoundException`
- `exception.InvalidClientEventQueryException`
- `dto.response.ClientEventLogResponse`
- `service.ClientEventLogReadService`
- `controller.AdminClientEventLogController`

---

## 5. 수정 클래스

- `exception.GlobalExceptionHandler` — `handleInvalidClientEventQuery`(400) / `handleClientEventLogNotFound`(404) 핸들러 2건 추가.
- `domain.repository.ClientEventLogRepository` — `search(@Query)` 메서드 추가. 9 필터 파라미터 + `Pageable`. `ORDER BY c.receivedAt DESC, c.id DESC` 고정 정렬.
- `config.SecurityConfig` — `GET /api/admin/client-events/**` narrow matcher 추가. broad `/api/admin/**` matcher 바로 앞에 삽입. 순서가 보안 요구사항.

---

## 6. 클래스별 설명

### `exception.ClientEventLogNotFoundException` — Exception

- **패키지**: `com.shinyoung.recruit.exception`
- **책임**: 단건 조회(`GET /admin/client-events/{id}`) 시 id가 존재하지 않을 때 404 신호.
- **형태**: `RuntimeException` 단순 상속. 생성자 1개(`String message`).
- **핸들러**: `GlobalExceptionHandler.handleClientEventLogNotFound` → HTTP 404.
- **관련**: `ClientEventLogReadService.getEvent`, `GlobalExceptionHandler`.

---

### `exception.InvalidClientEventQueryException` — Exception

- **패키지**: `com.shinyoung.recruit.exception`
- **책임**: 조회 파라미터 검증 실패(400). page 음수 / size 범위 초과 / 날짜 범위 역전 / 90일 초과.
- **형태**: `RuntimeException` 단순 상속. 생성자 1개(`String message`).
- **핸들러**: `GlobalExceptionHandler.handleInvalidClientEventQuery` → HTTP 400.
- **관련**: `ClientEventLogReadService.validatePaging`, `ClientEventLogReadService.validateRange`.

---

### `domain.repository.ClientEventLogRepository` — Repository (수정)

- **패키지**: `com.shinyoung.recruit.domain.repository`
- **책임**: `ClientEventLog` insert + 조회. 09f-1에서 마커 `Repository` 구조를 유지하면서 `search` 쿼리 추가.
- **추가된 주요 메서드**:
  - `search(from, to, eventType, severity, source, applicationId, jobPostingId, clientSessionId, relatedCorrelationId, Pageable)` — JPQL `@Query`. `receivedAt` 범위 필수(서비스에서 가드). 나머지 9 필터는 null이면 조건 제외(`IS NULL OR` 패턴). `ORDER BY c.receivedAt DESC, c.id DESC` 고정 정렬.
- **구현 주의**: `toDays()` 대신 `Duration` 비교를 서비스에서 사용해 경계 버림 문제 방지(09b 선례). 쿼리 자체는 단순 범위 포함.
- **관련**: `ClientEventLogReadService.search`, `AdminClientEventLogController`.

---

### `dto.response.ClientEventLogResponse` — Response DTO

- **패키지**: `com.shinyoung.recruit.dto.response`
- **클래스 유형**: Response DTO (Java `record`)
- **책임**: 관리자 조회 응답. 권한별 민감 필드 마스킹을 단일 팩토리 메서드로 캡슐화.
- **필드 수**: 34개.
- **주요 필드 그룹**:
  - 식별자: `id`, `clientSessionId`, `clientEventId`, `ingestCorrelationId`, `relatedCorrelationId`
  - 분류: `eventType`, `severity`, `source`
  - 시각: `receivedAt`, `clientOccurredAt`
  - 화면 컨텍스트: `pageCode`, `componentCode`, `routePath`, `operation`
  - 업무 키: `jobPostingId`, `applicationId`
  - API 오류: `httpMethod`, `apiPath`, `httpStatus`, `errorCode`
  - 메시지/스택: `message`, `stackHash`, `stackSummary`
  - 브라우저: `frontendVersion`, `browserName`, `browserVersion`, `osName`, `viewport`, `timezone`
  - 민감 필드(마스킹 대상): `ipAddress`, `userAgent`, `principalHash`
  - 인증: `principalType`
  - 메타데이터: `metadataJson`
- **정적 팩토리**: `from(ClientEventLog log, boolean includeSensitive)`.
  - `includeSensitive=false` 시 4필드(`ipAddress`/`userAgent`/`principalHash`/`stackSummary`) → `"***"`.
  - null 원문은 null 유지(`mask` 헬퍼: `value == null || includeSensitive`이면 원문 반환).
- **항상 노출(양 권한)**: `message`(safe code 강제 — PII 혼입 불가), `metadataJson`(exact allowlist 통과 — PII 혼입 불가), `stackHash`(group by용 해시값 — 원문 아님).
- **구현 주의**: 마스킹 헬퍼 `mask(String value, boolean includeSensitive)`가 null과 includeSensitive=true를 같은 분기로 처리하므로, null 원문은 null로 반환되어 `"***"` 오버라이드되지 않는다.
- **관련**: `ClientEventLogReadService`, `AdminClientEventLogController`.

---

### `service.ClientEventLogReadService` — Service

- **패키지**: `com.shinyoung.recruit.service`
- **클래스 유형**: Service
- **책임**: client event read API 서비스. 조회 가드 + normalize + `includeSensitive` projection 위임.
- **어노테이션**: `@Service`, `@Transactional(readOnly = true)`.
- **상수**:
  - `MAX_PAGE_SIZE = 100`
  - `MAX_RANGE_DAYS = 90`
  - `DEFAULT_RANGE_DAYS = 7`
- **주요 메서드**:
  - `search(eventType, severity, source, applicationId, jobPostingId, clientSessionId, relatedCorrelationId, from, to, page, size, includeSensitive)` — 가드 → normalize → repository 호출 → `ClientEventLogResponse.from` map → `PageResponse<ClientEventLogResponse>` 반환.
  - `getEvent(Long id, boolean includeSensitive)` — `findById` → `from` → `orElseThrow(ClientEventLogNotFoundException)`.
  - `validatePaging(page, size)` — `page < 0` → 400. `size < 1 || size > MAX_PAGE_SIZE` → 400.
  - `validateRange(from, to)` — `from.isAfter(to)` → 400. `Duration.between(from, to).compareTo(Duration.ofDays(90)) > 0` → 400.
  - `normalize(String value)` — null 반환 시 null. trim 후 blank이면 null. 공백 clientSessionId/relatedCorrelationId 오탐 방지.
- **default 범위 결정 로직**: `to`가 null이면 `LocalDateTime.now(clock)`. `from`이 null이면 `effectiveTo.minusDays(7)`.
- **구현 주의**: `Duration` 비교(`compareTo(Duration.ofDays(90))`)는 `ChronoUnit.toDays()` 방식의 소수 버림 문제 없음(09b `AuditActivityReadService` 선례).
- **관련**: `ClientEventLogRepository`, `ClientEventLogResponse`, `AdminClientEventLogController`, `Clock`.

---

### `controller.AdminClientEventLogController` — Controller

- **패키지**: `com.shinyoung.recruit.controller`
- **클래스 유형**: Controller
- **책임**: 관리자 client event 조회 endpoint. `AdminAuditController` 선례.
- **어노테이션**: `@RestController`, `@RequiredArgsConstructor`.
- **Base path prefix**: `/api` — `WebMvcConfig`가 controller 패키지에 일괄 부여.
- **엔드포인트**:
  - `GET /admin/client-events` — 검색. 11 `@RequestParam`(모두 optional, page default 0, size default 20).
  - `GET /admin/client-events/{id}` — 단건 조회.
- **`includeSensitive` 결정**: `private boolean includeSensitive(CustomUserDetails userDetails)` — `userDetails == null` → `false`. `getAuthorities()`에 `"ROLE_PRIVACY_ADMIN"` 포함 시 `true`.
- **응답**: `ResponseEntity<ApiResponse<PageResponse<ClientEventLogResponse>>>` / `ResponseEntity<ApiResponse<ClientEventLogResponse>>`.
- **관련**: `ClientEventLogReadService`, `SecurityConfig`, `AdminAuditController`.

---

### `config.SecurityConfig` — Config (수정)

- **패키지**: `com.shinyoung.recruit.config`
- **책임 추가**: `GET /api/admin/client-events/**` narrow matcher 삽입.
- **추가된 규칙**:
  ```
  .requestMatchers(HttpMethod.GET, "/api/admin/client-events/**")
    .hasAnyAuthority("ROLE_RECRUIT_ADMIN", "ROLE_PRIVACY_ADMIN")
  ```
- **위치**: broad `/api/admin/**` matcher(`.hasAnyAuthority("ROLE_ADMIN", "ROLE_RECRUIT_ADMIN")`) **앞**에 삽입. 순서가 보안 요구사항.
- **결과**: `ROLE_RECRUIT_ADMIN`/`ROLE_PRIVACY_ADMIN` 단독 보유 사용자는 접근 허용. `ROLE_ADMIN` 단독 사용자는 narrow matcher에서 거부되어 403(의도적 — 감사 read `/api/admin/audit/**` 선례와 동일한 privacy-tightening).
- **관련**: `AdminClientEventLogController`, `AdminAuditController`(선례).

---

## 7. API 목록

### GET /api/admin/client-events — 검색

| 항목 | 내용 |
|------|------|
| Method | GET |
| Path | `/api/admin/client-events` |
| Base Path Prefix | `/api` (`WebMvcConfig`가 controller 패키지에 일괄 부여) |
| 인증/인가 | `ROLE_RECRUIT_ADMIN` 또는 `ROLE_PRIVACY_ADMIN` 필요 |
| 민감 필드 원문 | `ROLE_PRIVACY_ADMIN` 보유 시만 원문, 나머지는 `***` 마스킹 |

**검색 파라미터**:

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `eventType` | `ClientEventType` enum | 선택 | 이벤트 유형 필터 |
| `severity` | `ClientEventSeverity` enum | 선택 | 심각도 필터 |
| `source` | `ClientEventSource` enum | 선택 | 발생 채널 필터 |
| `applicationId` | `Long` | 선택 | 지원서 ID 필터 |
| `jobPostingId` | `Long` | 선택 | 공고 ID 필터 |
| `clientSessionId` | `String` | 선택 | 세션 ID 필터(정확일치, normalize 적용) |
| `relatedCorrelationId` | `String` | 선택 | 상관 correlation ID 필터(normalize 적용) |
| `from` | `LocalDateTime` (ISO) | 선택 | receivedAt 범위 시작. null이면 `to - 7일` |
| `to` | `LocalDateTime` (ISO) | 선택 | receivedAt 범위 끝. null이면 현재 시각 |
| `page` | `int` | 선택 | 페이지 번호(default 0) |
| `size` | `int` | 선택 | 페이지 크기(default 20, 최대 100) |

**응답 예시 — RECRUIT_ADMIN (마스킹)**:

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 42,
        "receivedAt": "2026-06-10T13:00:00",
        "clientOccurredAt": "2026-06-10T12:59:58",
        "eventType": "API_ERROR",
        "severity": "ERROR",
        "source": "APPLICANT_WEB",
        "clientSessionId": "5d7c00ff-0d53-4ea3-bd44-68a9f7d68f9f",
        "clientEventId": "6a1bd08e-3cd1-4c0f-85c0-e6a65e4a0a33",
        "ingestCorrelationId": "abc-123",
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
        "message": "Request failed with status code 500",
        "stackHash": "abc123",
        "stackSummary": "***",
        "frontendVersion": "2026.06.10-1",
        "browserName": "Chrome",
        "browserVersion": "126",
        "osName": "Windows",
        "viewport": "1440x900",
        "timezone": "Asia/Seoul",
        "ipAddress": "***",
        "userAgent": "***",
        "principalHash": "***",
        "principalType": "Employee",
        "metadataJson": "{\"durationMs\":1250,\"retryable\":false}"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "page": 0,
    "size": 20
  }
}
```

**응답 예시 — PRIVACY_ADMIN (원문)**:

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 42,
        "stackSummary": "at saveEducation (app.js:120)",
        "ipAddress": "10.0.0.1",
        "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ...",
        "principalHash": "a3f8c2e1d94b..."
      }
    ]
  }
}
```

---

### GET /api/admin/client-events/{id} — 단건 조회

| 항목 | 내용 |
|------|------|
| Method | GET |
| Path | `/api/admin/client-events/{id}` |
| 인증/인가 | `ROLE_RECRUIT_ADMIN` 또는 `ROLE_PRIVACY_ADMIN` 필요 |
| 민감 필드 원문 | `ROLE_PRIVACY_ADMIN` 보유 시만 원문 |

**오류 조건(양 엔드포인트 공통)**:

| 조건 | HTTP |
|------|------|
| 미인증 | 401 |
| `ROLE_ADMIN` 단독 / `ROLE_APPLICANT` 등 권한 미보유 | 403 |
| `id` 존재하지 않음(`getEvent`) | 404 |
| `page < 0` | 400 |
| `size < 1 또는 size > 100` | 400 |
| `from > to` | 400 |
| `to - from > 90일` | 400 |

---

## 8. Entity 관계 요약

`ClientEventLog`는 09f-1과 동일하게 **독립 엔티티**. 이 슬라이스에서 Entity 변경 없음.

```
client_event_log (독립)
  ← GET /admin/client-events    [읽기 전용, 삭제/수정 API 없음]
  ← GET /admin/client-events/{id}
```

09f-3은 insert-only 엔티티를 읽기 전용으로 조회하는 레이어만 추가한다. retention bulk delete는 09f-4.

---

## 9. 비즈니스 규칙

### 가드 규칙

1. **page size 상한 100**: `size > 100` → `InvalidClientEventQueryException`(400). 대량 조회로 인한 부하 방지.

2. **날짜 범위 상한 90일**: `Duration.between(from, to).compareTo(Duration.ofDays(90)) > 0` → `InvalidClientEventQueryException`(400). Duration 비교 사용으로 소수 일수 버림 없음(09b `AuditActivityReadService` 선례).

3. **default 범위 최근 7일**: `from`/`to` 미지정 시 `[현재-7일, 현재]`. 감사 로그(09b, default 30일)보다 짧음 — 진단 로그는 단기 운영 용도(설계 §8.2).

4. **normalize**: `clientSessionId`/`relatedCorrelationId`는 trim 후 blank이면 null 처리. 공백만 전달 시 필터 없는 전체 조회가 되는 오탐 방지.

### 민감 필드 마스킹 정책

5. **4필드 마스킹**: `ipAddress`/`userAgent`/`principalHash`/`stackSummary` — `ROLE_PRIVACY_ADMIN` 미보유 시 `"***"`.

6. **stackSummary 마스킹 근거(설계 §8.3, 리뷰 Blocker 3)**: JS 스택 트레이스는 자유 문자열 성격이라 PII(이름/연락처/파일명 등)가 혼입될 수 있음. `message`와 달리 safe code 패턴 강제 없이 2000자까지 수집. 따라서 `ROLE_PRIVACY_ADMIN` 전용.

7. **항상 노출되는 필드**: `message`는 safe code 패턴(`^[A-Za-z0-9 _.:\\-]*$`)으로 강제됨 — PII 혼입 불가. `metadataJson`은 exact allowlist 통과 — PII성 key 수집 자체가 차단됨. `stackHash`는 스택 원문의 해시값 — 원문 미포함.

8. **null 원문 보존**: `mask` 헬퍼가 `value == null` 조건을 `includeSensitive=true`와 동일하게 처리하므로, null 원문 필드는 `"***"` 아닌 null로 반환됨.

### SecurityConfig matcher 순서 규칙

9. **narrow matcher 우선**: `GET /api/admin/client-events/**` matcher가 broad `GET /api/admin/**` matcher보다 앞에 위치해야 한다. matcher 평가는 선언 순서 기준이므로 순서가 보안 요구사항.

10. **ROLE_ADMIN 의도적 403**: broad `/api/admin/**`는 `ROLE_ADMIN`을 허용하지만, narrow `/api/admin/client-events/**`는 `ROLE_RECRUIT_ADMIN`/`ROLE_PRIVACY_ADMIN`만 허용. `ROLE_ADMIN` 단독 사용자는 narrow matcher에서 거부 → 403. 감사 read `/api/admin/audit/**` 선례와 동일한 privacy-tightening.

---

## 10. 테스트 커버리지

### ClientEventLogRepositoryTest (5건, 09f-1 4건 + 09f-3 추가 1건) — `@DataJpaTest`

| 테스트명 | 검증 내용 |
|---------|----------|
| 저장_조회_enum_매핑 | save → findById, enum 문자열 저장/복원, count *(09f-1)* |
| 같은_session_event_쌍은_unique_제약으로_거부된다 | saveAndFlush 중복 → DataIntegrityViolationException *(09f-1)* |
| existsBy로_중복을_선확인한다 | existsByClientSessionIdAndClientEventId true/false *(09f-1)* |
| 필수값_누락이면_엔티티_생성이_거부된다 | clientEventId 누락 → InvalidClientEventLogException *(09f-1)* |
| **search는_필터와_최신순_정렬을_지원한다** | applicationId 필터(2건 중 1건), JS_ERROR eventType 필터(1건), receivedAt DESC 정렬(13:00 이벤트 먼저) *(09f-3 신규)* |

### ClientEventLogReadServiceTest (6건) — `@SpringBootTest`

| 테스트명 | 검증 내용 |
|---------|----------|
| 범위_미지정이면_최근_7일만_조회된다 | now-1d 이벤트 조회, now-10d 이벤트 미조회(default 7일 범위) |
| includeSensitive가_false면_민감_필드가_마스킹된다 | ipAddress/userAgent/principalHash/stackSummary → `"***"` |
| includeSensitive가_true면_원문을_본다 | ipAddress/userAgent/principalHash/stackSummary 원문 검증 |
| 범위가_90일을_넘으면_거부된다 | from=now-120d, to=now → InvalidClientEventQueryException |
| size가_100을_넘으면_거부된다 | size=101 → InvalidClientEventQueryException |
| 없는_id_단건_조회는_NotFound_예외다 | getEvent(999999L) → ClientEventLogNotFoundException |

### AdminClientEventLogControllerTest (8건) — `@SpringBootTest`

| 테스트명 | 검증 내용 |
|---------|----------|
| RECRUIT_ADMIN은_민감_필드가_마스킹된다 | ipAddress/userAgent/principalHash/stackSummary → `"***"`, clientSessionId 노출 |
| PRIVACY_ADMIN은_원문을_본다 | ipAddress 원문, stackSummary 원문 |
| 단건_조회와_404를_지원한다 | 존재 id → 200 + clientEventId, 존재하지 않는 id → 404 |
| applicationId_필터가_동작한다 | applicationId=999 → totalElements=0 |
| **최신순으로_정렬된다** | receivedAt now-1h 이벤트가 content[0], now-1d 이벤트가 content[1] *(플랜 7건 + 정렬 검증 1건 추가)* |
| size_초과는_400이다 | size=101 → 400 |
| APPLICANT_권한은_403이다 | ROLE_APPLICANT → 403 |
| 미인증은_401이다 | 미인증 → 401 |

**실행 명령**:

```bash
# 전체 09f-3 테스트(scoped)
$env:AES_SECRET_KEY="22791194512954214612461221261067"; ./gradlew test `
  "--tests=com.shinyoung.recruit.domain.repository.ClientEventLogRepositoryTest" `
  "--tests=com.shinyoung.recruit.service.ClientEventLogReadServiceTest" `
  "--tests=com.shinyoung.recruit.controller.AdminClientEventLogControllerTest"
```

**결과**: 전체 **19건 성공** (scoped 실행 확인).

---

## 11. Known Limitations

### PRIVACY_ADMIN 원문 테스트의 부분 커버리지

`ClientEventLogReadServiceTest.includeSensitive가_true면_원문을_본다`는 `ipAddress`/`userAgent`/`principalHash`/`stackSummary` 4필드를 모두 검증한다. 그러나 `AdminClientEventLogControllerTest.PRIVACY_ADMIN은_원문을_본다`는 `ipAddress`와 `stackSummary` 2필드만 검증하며 `userAgent`/`principalHash` 원문을 명시적으로 검증하지 않는다.

`mask()` 헬퍼가 4필드에 동일하게 적용되는 단일 함수이므로 런타임 위험은 낮다. 서비스 레벨 테스트에서 4필드 전체가 원문/마스킹 양쪽 검증됨. 향후 컨트롤러 테스트 보강 시 `userAgent`/`principalHash` 원문 검증 추가 권장.

### 정렬 검증이 컨트롤러 레벨에서 보완됨

`ClientEventLogRepositoryTest.search는_필터와_최신순_정렬을_지원한다`에서 repository 레벨 정렬을 검증하고, `AdminClientEventLogControllerTest.최신순으로_정렬된다`에서 통합 레벨 정렬을 추가 검증했다.

### `ROLE_ADMIN` 접근 테스트 미포함

`ROLE_ADMIN` 단독 사용자가 `GET /api/admin/client-events/**`에서 실제로 403을 받는지에 대한 전용 테스트가 없다. SecurityConfig 주석에 의도가 명시되어 있고, `APPLICANT_권한은_403이다` 테스트에서 일반 구조는 검증되므로 즉각적 위험은 낮음.

---

## 12. Next Phase 고려사항

### 09f-4 — Retention Cleanup

- `ClientEventLogRepository`에 `@Modifying @Query` bulk delete 추가(`receivedAt < cutoff`).
- `ClientEventLogCleanupService` — `client-event-log.retention-days` / `cleanup-cron` yaml 설정 활성화.
- `SchedulingConfig`/`CleanupScheduler`(`@Scheduled` + `cleanup-cron`).
- 관리자 수동 트리거 API(`POST /api/admin/client-events/cleanup`) + SecurityConfig narrow matcher.
- 09f-4 완료로 Phase 09f 전 슬라이스 종료.
