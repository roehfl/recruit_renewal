# Phase 09f-4 — Client Event Retention Cleanup 구현

> Phase 09f(지원자 화면 진단 로그)의 네 번째 슬라이스이자 **마지막 BE 슬라이스**. **retention cleanup 파이프라인**을 구현한다 —
> bulk delete, `ClientEventLogCleanupService`, `SchedulingConfig`(`@EnableScheduling` 프로젝트 최초 도입),
> `ClientEventLogCleanupScheduler`, 관리자 수동 트리거 API(`POST /api/admin/client-events/cleanup`).
> 이 슬라이스 완료로 Phase 09f 전 BE 슬라이스(09f-1/09f-3/09f-4)가 종료된다.
>
> 설계 기준: `docs/codex/design/phase-09f-client-event-log-be-design.md`(§9 retention cleanup).

---

## 1. Phase 요약

- `ClientEventLogRepository.deleteByReceivedAtBefore` — `@Modifying(flushAutomatically = true)` + 단일 JPQL DELETE. 엔티티 로딩 없이 삭제 건수(`int`) 반환. `flushAutomatically = true`는 프로젝트 `@Modifying` 관례 정렬(리뷰 반영 커밋 ca19479).
- `ClientEventLogCleanupService` — `@Transactional`. threshold = `LocalDateTime.now(clock).minusDays(retentionDays)`. `retentionDays` 기본 90(`client-event-log.retention-days`). 삭제 기준은 strict `<`(receivedAt == threshold인 로그는 유지됨).
- `SchedulingConfig` — `@EnableScheduling` 프로젝트 **최초 도입**. 현재 사용처는 `ClientEventLogCleanupScheduler` 하나.
- `ClientEventLogCleanupScheduler` — `@Scheduled(cron = "${client-event-log.cleanup-cron:0 0 4 * * *}")`. 매일 04:00(기본). 예외 무전파(`log.error`만) — 진단 로그 정리 실패가 스케줄링 스레드/다음 실행에 영향을 주지 않도록.
- `AdminClientEventLogController` — 기존 조회 컨트롤러에 `POST /admin/client-events/cleanup` 추가. 삭제(`write`)이므로 `ROLE_PRIVACY_ADMIN` 전용 narrow matcher.
- `ClientEventLogCleanupResponse` — `record(int deletedCount)`. cleanup 결과 단순 응답.
- `SecurityConfig` — `POST /api/admin/client-events/cleanup → hasAuthority("ROLE_PRIVACY_ADMIN")` narrow matcher를 기존 `GET /api/admin/client-events/**` matcher 앞(broad `/api/admin/**` 앞)에 삽입.
- **헤더 주석 정리(커밋 62d3b82)**: 09f-1 슬라이스에서 생성된 9개 파일의 첫 줄 `// src/...` 경로 주석 제거. 기존 프로젝트 스타일에 없는 패턴이므로 정렬.

---

## 2. 구현 범위

### 구현됨

| 항목 | 설명 |
|------|------|
| Repository bulk delete | `ClientEventLogRepository.deleteByReceivedAtBefore` — `@Modifying(flushAutomatically = true)`, 단일 JPQL DELETE, `int` 반환 |
| CleanupService | `ClientEventLogCleanupService` — `@Transactional`, threshold strict `<`, `Clock` 주입, retention-days 외부 설정 |
| SchedulingConfig | `@EnableScheduling` 프로젝트 최초 도입 |
| CleanupScheduler | `ClientEventLogCleanupScheduler` — `@Scheduled(cron)`, 예외 무전파 |
| Response DTO | `ClientEventLogCleanupResponse` — `record(int deletedCount)` |
| Admin 수동 트리거 API | `AdminClientEventLogController.cleanup()` — `POST /admin/client-events/cleanup` |
| Security matcher | `SecurityConfig` — `POST /api/admin/client-events/cleanup → ROLE_PRIVACY_ADMIN` narrow matcher 삽입 |
| 헤더 주석 정리 | 09f-1 파일 9개의 `// src/...` 경로 주석 제거 |

### 범위 밖

| 항목 | 이월 Phase |
|------|-----------|
| 프론트엔드 연동 | 09f-2 (별도 FE 프로젝트) |
| cleanup 행위자 감사 추적 | 후속 phase(Known Limitations 참조) |

---

## 3. 변경 파일

### commit 91c8f38 — 9f-4 retention cleanup service

신규(main):
- `service/ClientEventLogCleanupService.java`

수정(main):
- `domain/repository/ClientEventLogRepository.java` (+`deleteByReceivedAtBefore` `@Modifying` bulk delete)

신규(test):
- `service/ClientEventLogCleanupServiceTest.java`

### commit ca19479 — 9f-4 bulk delete flushAutomatically 정렬

수정(main):
- `domain/repository/ClientEventLogRepository.java` (`@Modifying` → `@Modifying(flushAutomatically = true)` 정렬)

### commit 7ecf2e6 — 9f-4 scheduling 도입 + cleanup scheduler

신규(main):
- `config/SchedulingConfig.java`
- `service/ClientEventLogCleanupScheduler.java`

신규(test):
- `service/ClientEventLogCleanupSchedulerTest.java`

### commit cd8629f — 9f-4 cleanup 수동 트리거

신규(main):
- `dto/response/ClientEventLogCleanupResponse.java`

수정(main):
- `controller/AdminClientEventLogController.java` (`+cleanup()`, `+ClientEventLogCleanupService` 의존성)
- `config/SecurityConfig.java` (`+POST /api/admin/client-events/cleanup → ROLE_PRIVACY_ADMIN` narrow matcher)

수정(test):
- `controller/AdminClientEventLogControllerTest.java` (+`PRIVACY_ADMIN은_cleanup을_수동_트리거할_수_있다`, +`RECRUIT_ADMIN은_cleanup을_트리거할_수_없다` — 총 10건)

### commit 62d3b82 — 9f 파일 헤더 경로 주석 제거(프로젝트 스타일 정렬)

수정(main):
- `controller/ClientEventLogController.java` (첫 줄 `// src/...` 제거)
- `dto/request/ClientEventLogRequest.java` (첫 줄 `// src/...` 제거)
- `dto/response/ClientEventLogIngestResponse.java` (첫 줄 `// src/...` 제거)
- `service/ClientEventLogService.java` (첫 줄 `// src/...` 제거)
- `service/ClientEventRateLimiter.java` (첫 줄 `// src/...` 제거)

수정(test):
- `controller/ClientEventLogControllerTest.java` (첫 줄 `// src/...` 제거)
- `controller/ClientEventLogRateLimitControllerTest.java` (첫 줄 `// src/...` 제거)
- `service/ClientEventLogServiceTest.java` (첫 줄 `// src/...` 제거)
- `service/ClientEventRateLimiterTest.java` (첫 줄 `// src/...` 제거)

### 2차 리뷰 반영 — Major 1(스케줄링 테스트)/Major 2(message 패턴 강화)/Minor 1(proxy 문서)

신규(test):
- `config/SchedulingConfigTest.java` (Major 1 — `@EnableScheduling` 활성/cron 등록 검증 2건)

수정(main):
- `dto/request/ClientEventLogRequest.java` (Major 2 — `message` 패턴 `^[A-Za-z0-9 _.:\\-]*$` → `^[A-Z][A-Z0-9_]{2,80}$`, `@Size(max=200)` 제거)
- `service/ClientEventLogService.java` (주석만 — 숫자 마스킹을 2차 방어로 명시)

수정(test):
- `controller/ClientEventLogControllerTest.java` (+영문 자유 문장 400, +소문자 400, +axios 기본 문구 400, +safe code 200 — 총 14건)

수정(docs):
- `phase-09f-1-client-event-ingest.md`/`.html` (message 계약, reverse proxy trusted-proxy 정책)
- `phase-09f-4-client-event-retention.md`/`.html` (본 문서 — SchedulingConfigTest)

---

## 4. 신규 클래스

- `service.ClientEventLogCleanupService`
- `config.SchedulingConfig`
- `service.ClientEventLogCleanupScheduler`
- `dto.response.ClientEventLogCleanupResponse`

---

## 5. 수정 클래스

- `domain.repository.ClientEventLogRepository` — `deleteByReceivedAtBefore` bulk delete 메서드 추가. `@Modifying(flushAutomatically = true)` + 단일 JPQL DELETE.
- `controller.AdminClientEventLogController` — `POST /admin/client-events/cleanup` 엔드포인트 + `ClientEventLogCleanupService` 의존성 추가.
- `config.SecurityConfig` — `POST /api/admin/client-events/cleanup → ROLE_PRIVACY_ADMIN` narrow matcher 삽입. 기존 `GET /api/admin/client-events/**` matcher보다도 앞에 위치.

---

## 6. 클래스별 설명

### `domain.repository.ClientEventLogRepository` — Repository (수정)

- **패키지**: `com.shinyoung.recruit.domain.repository`
- **책임**: 09f-4에서 retention cleanup 전용 bulk delete 메서드 추가.
- **추가된 메서드**:
  ```java
  @Modifying(flushAutomatically = true)
  @Query("DELETE FROM ClientEventLog c WHERE c.receivedAt < :threshold")
  int deleteByReceivedAtBefore(@Param("threshold") LocalDateTime threshold);
  ```
- **설계 결정**:
  - `flushAutomatically = true`: 트랜잭션 내 아직 flush되지 않은 엔티티가 DELETE 대상에 포함되도록 보장(프로젝트 `@Modifying` 관례 정렬 — 리뷰 ca19479).
  - 단일 JPQL DELETE: 엔티티 로딩 없이 DB에서 직접 삭제 → 대량 데이터 처리에 효율적.
  - strict `<`: `receivedAt == threshold`인 로그는 유지됨. `<= threshold`가 아니라 `< threshold` 사용.
  - `int` 반환: 삭제 건수를 서비스/컨트롤러 응답에 전달.
- **관련**: `ClientEventLogCleanupService`.

---

### `service.ClientEventLogCleanupService` — Service

- **패키지**: `com.shinyoung.recruit.service`
- **클래스 유형**: Service
- **책임**: client event retention 삭제 비즈니스 로직. threshold 계산 + repository 위임.
- **어노테이션**: `@Service`, `@Transactional`.
- **생성자 파라미터**:
  - `ClientEventLogRepository repository`
  - `Clock clock`
  - `@Value("${client-event-log.retention-days:90}") int retentionDays`
- **주요 메서드**:
  - `cleanup()` — `threshold = LocalDateTime.now(clock).minusDays(retentionDays)`. `repository.deleteByReceivedAtBefore(threshold)`. 삭제 건수 반환.
- **strict `<` 의미**: `receivedAt < threshold`이므로 `receivedAt == threshold`(정확히 90일 경과 시점)인 로그는 유지된다. 경계 로그가 cleanup 직후 보존기간 내로 전환되는 race 방지 설계.
- **관련**: `ClientEventLogRepository`, `Clock`(Bean — `ClockConfig` 또는 테스트 주입), `ClientEventLogCleanupScheduler`, `AdminClientEventLogController`.

---

### `config.SchedulingConfig` — Config

- **패키지**: `com.shinyoung.recruit.config`
- **클래스 유형**: Configuration
- **책임**: Spring Scheduling 활성화. `@EnableScheduling` **프로젝트 최초 도입**.
- **어노테이션**: `@Configuration`, `@EnableScheduling`.
- **영향**: `@EnableScheduling`이 애플리케이션 컨텍스트에 포함되므로 `@SpringBootTest` 기반 통합 테스트 컨텍스트에도 스케줄러가 활성화된다. `ClientEventLogCleanupScheduler`의 cron이 `0 0 4 * * *`(매일 04:00)이므로 테스트 실행 시간 간섭 위험은 매우 낮다. 향후 cron 설정이 테스트 중 실행될 수 있는 짧은 주기로 바뀌면 테스트에서 `@MockBean`이나 `spring.task.scheduling.enabled=false`로 격리할 것.
- **관련**: `ClientEventLogCleanupScheduler`.

---

### `service.ClientEventLogCleanupScheduler` — Component

- **패키지**: `com.shinyoung.recruit.service`
- **클래스 유형**: Component(스케줄러)
- **책임**: retention cleanup 자동 스케줄 실행. 매일 1회(기본 04:00).
- **어노테이션**: `@Component`, `@Scheduled(cron = "${client-event-log.cleanup-cron:0 0 4 * * *}")`.
- **주요 메서드**:
  - `runCleanup()` — `cleanupService.cleanup()` 위임. 결과(`deleted`) `log.info`. 예외 발생 시 `log.error`만 남기고 전파하지 않음.
- **예외 무전파 설계**: 진단 로그 정리 실패는 채용 서비스 주요 기능이 아니므로 스케줄링 스레드가 죽거나 다음 실행이 건너뛰어지는 것을 방지.
- **cron 외부 설정**: `client-event-log.cleanup-cron`으로 운영 환경에서 시간대 조정 가능. 기본값 `0 0 4 * * *`(한국 기준 오전 4시, 트래픽 최저점).
- **관련**: `ClientEventLogCleanupService`, `SchedulingConfig`.

---

### `dto.response.ClientEventLogCleanupResponse` — Response DTO

- **패키지**: `com.shinyoung.recruit.dto.response`
- **클래스 유형**: Response DTO (Java `record`)
- **책임**: 수동 retention cleanup 트리거 결과 응답.
- **필드**: `int deletedCount`.
- **관련**: `AdminClientEventLogController.cleanup()`.

---

### `controller.AdminClientEventLogController` — Controller (수정)

- **패키지**: `com.shinyoung.recruit.controller`
- **클래스 유형**: Controller
- **책임**: 09f-4에서 cleanup 수동 트리거 엔드포인트 추가. 09f-3 조회 엔드포인트는 변경 없음.
- **추가된 엔드포인트**:
  - `POST /admin/client-events/cleanup` — `cleanup()`. `ClientEventLogCleanupService.cleanup()` 위임. `ClientEventLogCleanupResponse(deletedCount)` 반환.
- **인증/인가**: SecurityConfig narrow matcher에서 게이팅(`ROLE_PRIVACY_ADMIN` 전용). 컨트롤러 메서드 내에는 인증 로직 없음. retention write 관례(`AdminRetentionController`/`SecurityConfig` 선례)와 동일 패턴.
- **관련**: `ClientEventLogCleanupService`, `ClientEventLogCleanupResponse`, `SecurityConfig`.

---

### `config.SecurityConfig` — Config (수정)

- **패키지**: `com.shinyoung.recruit.config`
- **책임 추가**: `POST /api/admin/client-events/cleanup → ROLE_PRIVACY_ADMIN` narrow matcher 삽입.
- **추가된 규칙**:
  ```
  .requestMatchers(HttpMethod.POST, "/api/admin/client-events/cleanup")
    .hasAuthority("ROLE_PRIVACY_ADMIN")
  ```
- **위치**: 기존 `GET /api/admin/client-events/**` matcher보다 앞, broad `/api/admin/**` matcher보다 앞에 삽입.
- **결과**: `ROLE_RECRUIT_ADMIN` 보유 사용자가 `POST /api/admin/client-events/cleanup`을 호출하면 narrow matcher에서 403. cleanup은 bulk delete(write)이므로 retention write 관례(`POST /api/admin/retention/**` 선례)와 동일하게 `ROLE_PRIVACY_ADMIN` 전용.
- **관련**: `AdminClientEventLogController`, retention SecurityConfig 기존 matcher.

---

## 7. API 목록

### POST /api/admin/client-events/cleanup — retention cleanup 수동 트리거

| 항목 | 내용 |
|------|------|
| Method | POST |
| Path | `/api/admin/client-events/cleanup` |
| Base Path Prefix | `/api` (`WebMvcConfig`가 controller 패키지에 일괄 부여) |
| 인증/인가 | `ROLE_PRIVACY_ADMIN` 전용 (narrow matcher — `ROLE_RECRUIT_ADMIN`은 403) |
| 요청 Body | 없음 |
| 응답 | `ApiResponse<ClientEventLogCleanupResponse>` |

**응답 예시**:

```json
{
  "success": true,
  "data": {
    "deletedCount": 42
  }
}
```

**오류 조건**:

| 조건 | HTTP |
|------|------|
| 미인증 | 401 |
| `ROLE_RECRUIT_ADMIN`/`ROLE_APPLICANT` 등 `ROLE_PRIVACY_ADMIN` 미보유 | 403 |

---

### 기존 GET 엔드포인트 (09f-3, 변경 없음)

- `GET /api/admin/client-events` — 검색(11파라미터, `ROLE_RECRUIT_ADMIN`/`ROLE_PRIVACY_ADMIN`)
- `GET /api/admin/client-events/{id}` — 단건 조회(`ROLE_RECRUIT_ADMIN`/`ROLE_PRIVACY_ADMIN`)

---

## 8. Entity 관계 요약

`ClientEventLog`는 09f 전체에서 **독립 엔티티**. 이 슬라이스에서 Entity 필드 변경 없음.

```
client_event_log (독립)
  ← POST /client-events               [수집, permitAll]
  ← GET /admin/client-events          [조회, RECRUIT_ADMIN/PRIVACY_ADMIN]
  ← GET /admin/client-events/{id}     [단건 조회, RECRUIT_ADMIN/PRIVACY_ADMIN]
  ← POST /admin/client-events/cleanup [bulk delete, PRIVACY_ADMIN 전용]
  ← @Scheduled(cron 04:00)            [자동 bulk delete]
```

---

## 9. 비즈니스 규칙

### Retention 삭제 기준

1. **보존 기간 기본 90일**: `client-event-log.retention-days` yaml 설정. 기본값 90. 운영 환경에서 조정 가능.

2. **strict `<` 경계(receivedAt == threshold 유지)**: threshold = `now - retentionDays`. `receivedAt < threshold`인 로그만 삭제. `receivedAt == threshold`인 로그는 유지됨. 경계 시점의 로그가 조회 중 삭제되는 race를 방지하는 설계.

3. **Clock 주입**: `LocalDateTime.now(clock)` 사용. 테스트에서 `Clock`을 `@Autowired`로 주입받아 고정 시간 기준 검증 가능.

4. **엔티티 로딩 없는 bulk delete**: `@Query("DELETE FROM ClientEventLog c WHERE c.receivedAt < :threshold")` — JPA `merge`/`remove` 사이클 없이 DB 직접 DELETE. 대량 로그 처리에 적합.

5. **flushAutomatically**: 같은 트랜잭션 내에서 아직 persist 되지 않은 엔티티도 flush 후 DELETE 대상에 포함. 프로젝트 `@Modifying` 관례(retention purge 등 선례)와 정렬.

### Scheduler 규칙

6. **예외 무전파**: `runCleanup()`은 `try-catch`로 모든 예외를 `log.error`로 처리. 진단 로그 정리 실패가 채용 서비스 주요 기능에 영향을 주지 않음.

7. **cron 외부 설정**: `client-event-log.cleanup-cron`으로 운영/스테이징 환경별 시간 조정.

### SecurityConfig matcher 순서 규칙

8. **write → PRIVACY_ADMIN 전용**: retention 관련 write API는 프로젝트 전체적으로 `ROLE_PRIVACY_ADMIN` 전용(`POST /api/admin/retention/**` 선례). cleanup도 동일 정책 적용.

9. **narrow matcher 우선**: `POST /api/admin/client-events/cleanup` matcher가 broad `/api/admin/**` matcher보다 앞에 위치해야 한다. matcher 평가는 선언 순서 기준이므로 순서가 보안 요구사항.

---

## 10. 테스트 커버리지

### ClientEventLogCleanupServiceTest (2건) — `@SpringBootTest`

| 테스트명 | 검증 내용 |
|---------|----------|
| 보존기간이_지난_로그만_삭제된다 | now-91d 로그 삭제(1건), now-89d/now-1d 로그 유지(2건). 91/89일 쌍으로 경계 검증. |
| 삭제_대상이_없으면_0을_반환한다 | now-1d 로그 1건만 존재 → cleanup 결과 0 |

> **경계 검증 방식**: 정확히 90일(now-90d)은 시스템 Clock 특성상 결정적이지 않으므로(실행 시점 ms 오차 누적), 91일/89일 쌍으로 검증한다.

### ClientEventLogCleanupSchedulerTest (2건) — 단위 테스트(Mock)

| 테스트명 | 검증 내용 |
|---------|----------|
| 정상_실행시_cleanup을_위임한다 | `cleanupService.cleanup()` 호출 verify |
| cleanup_실패는_로그만_남기고_전파하지_않는다 | `cleanup()` throw → `runCleanup()` 예외 없음 |

### SchedulingConfigTest (2건, 2차 리뷰 Major 1 반영) — `@SpringBootTest`

| 테스트명 | 검증 내용 |
|---------|----------|
| 스케줄링이_활성화되어_있다 | `ScheduledAnnotationBeanPostProcessor` 빈 존재 |
| cleanup_스케줄러의_cron_작업이_등록되어_있다 | 등록된 ScheduledTask 중 `ClientEventLogCleanupScheduler` 대상 작업 존재 |

> **배경**: `@EnableScheduling`이 빠지면 `@Scheduled` cron이 컴파일/기동 오류 없이 조용히 미동작한다. `SchedulingConfig` 자체는 09f-4에서 함께 도입됐으나, 누락 회귀를 컨텍스트 수준에서 잡는 테스트가 없어 2차 리뷰(Major 1)에서 보강했다.

### AdminClientEventLogControllerTest (10건, 09f-3 8건 + 09f-4 신규 2건) — `@SpringBootTest`

| 테스트명 | 검증 내용 |
|---------|----------|
| RECRUIT_ADMIN은_민감_필드가_마스킹된다 | 4필드 `***`, clientSessionId 노출 *(09f-3)* |
| PRIVACY_ADMIN은_원문을_본다 | ipAddress/stackSummary 원문 *(09f-3)* |
| 단건_조회와_404를_지원한다 | 존재 id → 200, 미존재 id → 404 *(09f-3)* |
| applicationId_필터가_동작한다 | applicationId=999 → totalElements=0 *(09f-3)* |
| 최신순으로_정렬된다 | now-1h 이벤트 content[0], now-1d 이벤트 content[1] *(09f-3)* |
| size_초과는_400이다 | size=101 → 400 *(09f-3)* |
| APPLICANT_권한은_403이다 | ROLE_APPLICANT → 403 *(09f-3)* |
| 미인증은_401이다 | 미인증 → 401 *(09f-3)* |
| **PRIVACY_ADMIN은_cleanup을_수동_트리거할_수_있다** | now-120d 로그 1건 seed → POST /api/admin/client-events/cleanup → deletedCount=1 *(09f-4 신규)* |
| **RECRUIT_ADMIN은_cleanup을_트리거할_수_없다** | ROLE_RECRUIT_ADMIN → POST cleanup → 403 *(09f-4 신규)* |

### 09f 전체 scoped 테스트 (66건)

| 테스트 클래스 | 건수 | 슬라이스 |
|-------------|------|---------|
| `ClientEventLogRepositoryTest` | 5 | 09f-1/09f-3 |
| `ClientEventMetadataSanitizerTest` | 11 | 09f-1 |
| `ClientEventRateLimiterTest` | 6 | 09f-1 |
| `ClientEventLogServiceTest` | 7 | 09f-1 |
| `ClientEventLogControllerTest` | 14 | 09f-1 (2차 리뷰 Major 2로 4건 추가) |
| `ClientEventLogRateLimitControllerTest` | 1 | 09f-1 |
| `ClientEventLogReadServiceTest` | 6 | 09f-3 |
| `AdminClientEventLogControllerTest` | 10 | 09f-3/09f-4 |
| `ClientEventLogCleanupServiceTest` | 2 | 09f-4 |
| `ClientEventLogCleanupSchedulerTest` | 2 | 09f-4 |
| `SchedulingConfigTest` | 2 | 09f-4 (2차 리뷰 Major 1로 추가) |
| **합계** | **66** | |

**실행 명령**:

```powershell
# 09f-4 scoped (+ 09f 전체)
$env:AES_SECRET_KEY="22791194512954214612461221261067"
.\gradlew.bat test `
  "--tests=com.shinyoung.recruit.domain.repository.ClientEventLogRepositoryTest" `
  "--tests=com.shinyoung.recruit.service.ClientEvent*" `
  "--tests=com.shinyoung.recruit.controller.ClientEventLog*" `
  "--tests=com.shinyoung.recruit.controller.AdminClientEventLogControllerTest" `
  "--tests=com.shinyoung.recruit.config.SchedulingConfigTest" `
  --no-daemon

# 인접 회귀
.\gradlew.bat test --tests "com.shinyoung.recruit.controller.AdminAuditControllerTest" --no-daemon
```

**결과**: **66건 통과**, 실패 0(2차 리뷰 반영 후 재실행). 회귀 `AdminAuditControllerTest` 10건은 09f-4 구현 시점 통과.

---

## 11. Known Limitations

### 수동 cleanup 트리거의 행위자 감사 추적 없음

`POST /api/admin/client-events/cleanup`은 삭제 건수만 반환하며 `ActivityLog`에 행위자를 기록하지 않는다. 진단 로그 정리는 운영 진단 목적이라 설계상 ActivityLog 계측 제외로 결정됐다. 행위자 추적이 필요해지는 규정 요건 발생 시 후속 phase에서 추가 가능.

### 경계값(정확히 90일) 테스트의 결정성

`receivedAt == threshold`(정확히 90일 시점)에 대한 테스트는 작성하지 않았다. 시스템 Clock의 ms 오차로 `now.minusDays(90)`과 `now(clock).minusDays(retentionDays)` 사이에 미미한 차이가 생길 수 있어 테스트가 비결정적이 된다. 91일/89일 쌍으로 검증하는 방식으로 경계를 검증한다.

### PRIVACY_ADMIN 조회 원문 테스트 부분 커버리지

`AdminClientEventLogControllerTest.PRIVACY_ADMIN은_원문을_본다`는 `ipAddress`와 `stackSummary` 2필드만 검증. `userAgent`/`principalHash` 원문은 서비스 레벨 테스트(`ClientEventLogReadServiceTest`)에서 커버됨. 컨트롤러 테스트 보강 시 추가 권장(09f-3 Known Limitations 동일).

### @EnableScheduling과 통합 테스트 컨텍스트

`SchedulingConfig`가 주 애플리케이션 컨텍스트에 포함되므로 `@SpringBootTest` 통합 테스트 컨텍스트에서도 스케줄러가 활성화된다. 현재 cron이 `0 0 4 * * *`(매일 04:00)이므로 일반 테스트 실행 시간대 간섭 위험은 낮다. 추후 단기 주기 스케줄러가 추가될 경우 해당 테스트에서 `@MockBean`이나 프로파일 분리로 격리할 것.

---

## 12. Next Phase 고려사항

### Phase 09f 완료 상태

Phase 09f 전 BE 슬라이스가 이 슬라이스로 종료된다.

| 슬라이스 | 범위 | 상태 |
|---------|------|------|
| 09f-1 | 수집(ingest) 파이프라인, Entity, Rate Limiter, Sanitizer | 완료 |
| 09f-2 | 프론트엔드 연동 | 별도 FE 프로젝트 (BE 범위 외) |
| 09f-3 | 관리자 조회 API | 완료 |
| 09f-4 | Retention Cleanup | **완료 (이 슬라이스)** |

### 운영 고려

- `client-event-log.retention-days`, `client-event-log.cleanup-cron`을 운영 환경 `application-prod.yaml`에 명시적으로 선언할 것.
- cleanup 실행 결과는 현재 `log.info`에만 남음. 운영 모니터링이 필요하면 Actuator metrics 또는 AlertManager 연동 고려.
- 수동 cleanup 트리거 행위자 추적이 필요하면 `ActivityLog` 계측 후속 phase 추가.
