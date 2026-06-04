# Phase 09a — ActivityLog Foundation 구현

> Phase 09(개인정보 파기/감사/보존)의 첫 슬라이스. 영속 감사 증적의 **기반(foundation)** 만 구현한다 — 업무 이벤트 계측(기존 SLF4J 로거 흡수·관리자 변경 감사)과 read API 는 **9b**, 보존/파기는 9c~9e.
> 설계 기준: `docs/codex/design/phase-09-privacy-purge-audit-retention-design.md`, ADR-0006(감사 트랜잭션 정책), `CONTEXT.md`(Privacy/Audit glossary).
> **리뷰 보완 반영(09a-RF)**: request-derived 문자열 normalize, `(action_result, occurred_at)` 인덱스, 운영 DDL, fallback secret property 게이트 — `phase-09a-review-fix.md` 참조. 본 문서의 해당 기술은 보완 후 상태로 갱신됨.

## 1. Phase 요약

- 영속 `ActivityLog`(append-only) 엔티티 + 감사 enum 5종 + append-only repository 를 추가했다.
- `ActivityLogService` 의 **명시적 2경로**(`recordInCurrentTx` / `recordRequiresNew`)로 기록 기반을 만들었다(AOP blanket 아님, ADR-0006).
- 요청 단위 `correlationId` 전파(`CorrelationIdFilter` + MDC, `X-Request-Id`)를 추가했다.
- `applicantRefHash` = HMAC-SHA256 + server pepper(`AUDIT_HMAC_SECRET`)를 추가했다(plain SHA-256 인 `ciHash` 와 분리, 입력은 applicantId 만).
- 업무 이벤트 계측·API·보존/파기 도메인은 이 슬라이스에 **없다**.

## 2. 구현 범위 (Implemented scope)

- `ActivityLog` 엔티티(append-only, BaseEntity 미상속, 자체 `occurredAt`(Clock)).
- enum: `ActorType`, `AuditActionType`, `AuditActionResult`, `AuditTargetType`, `AuditReasonCode`.
- `ActivityLogRepository`(insert+조회만, delete/update 미노출).
- `ActivityLogService`: `recordInCurrentTx`(REQUIRED, 비즈니스 tx 원자적) / `recordRequiresNew`(REQUIRES_NEW, 비즈니스 rollback 무관 보존).
- `AuditEvent`(기록 요청 record, @Builder), `AuditMetadata`(typed metadata 마커 — 9b 에서 sealed + 구체 record).
- `AuditHmac`(HMAC-SHA256 pepper) + `AuditConfig`(누락=기본 기동실패, fallback 은 `audit.allow-fallback-secret=true` 명시 시에만 — 09a-RF).
- `CorrelationIdFilter`(OncePerRequestFilter, MDC `correlationId`, 응답 echo. 100자 초과/CRLF 헤더는 UUID 대체 — 09a-RF).
- 설정: `audit.hmac-secret: ${AUDIT_HMAC_SECRET:}` + `audit.allow-fallback-secret: ${AUDIT_ALLOW_FALLBACK_SECRET:false}`(main), test 전용 pepper(test yaml).

**범위 밖(9b+)**: 기존 Export/Pdf/Upload 로거 흡수, egress fail-close, 관리자 변경 계측, typed AuditMetadata 구체 record, admin audit read API.

## 3. 변경 파일 (Changed files)

신규(main):
- `enumeration/ActorType.java`
- `enumeration/AuditActionType.java`
- `enumeration/AuditActionResult.java`
- `enumeration/AuditTargetType.java`
- `enumeration/AuditReasonCode.java`
- `domain/entity/ActivityLog.java`
- `domain/repository/ActivityLogRepository.java`
- `exception/InvalidActivityLogException.java`
- `common/hash/AuditHmac.java`
- `config/AuditConfig.java`
- `config/CorrelationIdFilter.java`
- `service/AuditMetadata.java`
- `service/AuditEvent.java`
- `service/ActivityLogService.java`

수정(main/test resources):
- `src/main/resources/application.yaml` (+`audit.hmac-secret`)
- `src/test/resources/application.yaml` (+`audit.hmac-secret` 테스트 전용 값)

신규(test):
- `common/hash/AuditHmacTest.java`
- `config/CorrelationIdFilterTest.java`
- `config/AuditConfigTest.java`
- `domain/repository/ActivityLogRepositoryTest.java`
- `service/ActivityLogServiceTest.java`

## 4. 신규 클래스 (New classes)

(모두 신규. 기존 수정 클래스는 없음 — 설정 yaml 만 추가.)

## 5. 클래스별 설명 (Class-by-class)

### `enumeration.ActorType` — Enum
- 책임: 행위자 유형 `EMPLOYEE`/`SYSTEM`/`APPLICANT`/`ANONYMOUS`. Phase 09 emission 은 EMPLOYEE/SYSTEM/ANONYMOUS 만(APPLICANT 자가행위 제외). ADMIN/INTERVIEWER 구분은 `actorRoleSnapshot`.

### `enumeration.AuditActionType` — Enum
- 책임: 감사 행위 유형 taxonomy(EXPORT_*/APPLICATION_PDF/STAGE_RESULT_*/EVALUATION_REOPEN/ATTACHMENT_ADMIN_*/RETENTION_*/PURGE_*). Java enum + DB VARCHAR(CommonCode 아님). 09a 는 정의만, 계측은 09b+.

### `enumeration.AuditActionResult` — Enum
- 책임: 결과 분류 `SUCCESS`/`FAILURE`/`DENIED`/`SKIPPED`/`CONFLICT`. CONFLICT 독립.

### `enumeration.AuditTargetType` — Enum
- 책임: 대상 유형(STAGE_RESULT/JOB_APPLICATION/APPLICATION_ATTACHMENT/INTERVIEW_EVALUATION/EXPORT_DATASET/APPLICATION_PDF/RETENTION_POLICY/RETENTION_HOLD/JOB_POSTING/PURGE_BATCH).

### `enumeration.AuditReasonCode` — Enum
- 책임: 검색 가능 사유 코드(VERSION_MISMATCH/AUTH_DENIED/VALIDATION_FAILED/RETENTION_NOT_DUE/RETENTION_HOLD/ALREADY_PURGED/ANCHOR_NOT_FIXED/APPLICATION_NOT_TERMINAL/INVALID_STAGE_CONFIGURATION/POLICY_NOT_FOUND/POLICY_CONFLICT/BINARY_DELETE_FAILED).

### `domain.entity.ActivityLog` — Entity
- 책임: 영속 감사 증적. **append-only**(setter/update/@Version 없음). BaseEntity 미상속(자체 `occurredAt`).
- 주요 필드: `occurredAt`, `actorType`, `actorId`, `actorRoleSnapshot`, `actionType`, `actionResult`, `targetType`, `targetId`, `jobPostingId`, `applicationId`, `applicantRefHash`, `reasonCode`, `reasonMessage`, `correlationId`, `traceId`, `ipAddress`, `userAgent`, `metadataJson`(@Lob).
- 생성: Lombok `@Builder`(private 생성자) + `validateRequired`(occurredAt/actorType/actionType/actionResult/targetType 필수 → `InvalidActivityLogException`).
- 노트: 지원자 원문 PII 미저장. enum 은 `@Enumerated(STRING)`. index 7종(occurredAt/actorId/(targetType,targetId)/applicationId/jobPostingId/actionType/(actionResult,occurredAt) — 마지막은 09a-RF).

### `domain.repository.ActivityLogRepository` — Repository
- 책임: append-only 접근. `JpaRepository` 대신 `Repository` 마커 + `save`/`findById`/`count` 만 노출(delete/update 미노출). 조회 finder 는 9b 에서 확장.

### `exception.InvalidActivityLogException` — Exception
- 책임: ActivityLog 필수 필드 누락 등 무결성 위반(RuntimeException).

### `common.hash.AuditHmac` — 컴포넌트(빈은 AuditConfig 생성)
- 책임: `applicantRefHash(applicantId)` = `HMAC_SHA256(secret, "APPLICANT:"+applicantId)`. `HashUtil`(plain SHA-256)과 분리. blank secret 거부. 입력은 applicantId 만(ci/email/phone 금지).

### `config.AuditConfig` — Config
- 책임: `AuditHmac` 빈 생성. pepper 를 `audit.hmac-secret`(=env `AUDIT_HMAC_SECRET`)에서 주입. **fail-safe(09a-RF)**: blank 면 기본 기동 실패(profile 이름 무관). fallback 은 `audit.allow-fallback-secret=true` 명시 시에만 + 경고(prod profile 은 flag 무관 거부).

### `config.CorrelationIdFilter` — Filter(@Component, HIGHEST_PRECEDENCE)
- 책임: `X-Request-Id` 헤더 재사용/없으면 UUID 생성 → MDC `correlationId` + 응답 echo, finally 제거. `currentCorrelationId()` 정적 접근. traceId(OTel)는 deferred. **외부 헤더 불신(09a-RF)**: 100자 초과 또는 CR/LF 포함 헤더는 재사용하지 않고 UUID 대체.

### `service.AuditMetadata` — interface(marker)
- 책임: metadataJson 의 typed 입력. 자유 Map/raw JSON 금지. 직렬화는 ActivityLogService 내부. 09b 에서 sealed + 구체 record 추가.

### `service.AuditEvent` — Request record(@Builder)
- 책임: 기록 요청. actor/action/target/식별자/reason/ip/ua/correlationId/metadata + `applicantId`(hash 입력). `correlationId` null 이면 서비스가 MDC 에서 채움.

### `service.ActivityLogService` — Service
- 책임: 감사 기록 2경로.
  - `recordInCurrentTx`(`@Transactional(REQUIRED)`): 커밋된 변경 성공 증적 — 비즈니스 tx 에 join(원자적, 함께 rollback).
  - `recordRequiresNew`(`@Transactional(REQUIRES_NEW)`): 실패/거부/충돌/스킵 + 정보 반출(fail-close) — 비즈니스 rollback 무관 보존.
- 내부: `occurredAt = now(clock)`, `applicantRefHash = auditHmac.applicantRefHash(applicantId)`, `correlationId` = event override → 없으면 MDC, `metadataJson` = 전용 ObjectMapper 직렬화(앱 web Jackson 과 분리).
- 노트: 두 메서드는 외부(09b)에서 호출(self-invocation 프록시 함정 회피).

## 6. API 목록

- **없음.** 09a 는 foundation 이라 controller/endpoint 가 없다. 감사 read API 는 09b.

## 7. Entity 관계 요약

- `ActivityLog` 는 독립 테이블(`activity_log`). FK 없음 — `jobPostingId`/`applicationId` 는 denormalized search key(파기 후 join 회피), `applicantRefHash` 는 가명 연결자. 다른 엔티티를 참조하지 않는다(감사 증적 독립성).

## 8. 비즈니스 규칙

1. ActivityLog 는 append-only — update/delete API·public setter 없음, `@Version` 없음. 정정은 correction event 추가(09b+).
2. 기록 트랜잭션(ADR-0006): 커밋변경=`recordInCurrentTx`(REQUIRED), 실패/거부/충돌/스킵·반출=`recordRequiresNew`(REQUIRES_NEW).
3. `applicantRefHash` = HMAC+pepper, 입력 applicantId 만(원문 PII 금지). plain SHA-256 아님.
4. `metadataJson` 은 typed record 만(자유 Map 금지), 직렬화는 서비스 내부.
5. `AUDIT_HMAC_SECRET`: 누락 = 기본 기동 실패(fail-safe, profile 이름 무관). fallback 은 `audit.allow-fallback-secret=true` 명시 시에만(09a-RF).
5-1. request-derived 문자열(actorId/roleSnapshot/reasonMessage/correlationId/ip/ua)은 저장 직전 sanitize+truncate — 외부 입력 길이가 audit insert 실패로 전이되지 않는다(09a-RF).
6. `occurredAt` 은 주입 `Clock` 기준(테스트 fixed Clock).

## 9. 테스트 커버리지 (Test coverage)

- 명령: `$env:AES_SECRET_KEY='...'; .\gradlew.bat test --tests "*ActivityLog*" --tests "*AuditHmacTest" --tests "*AuditConfigTest" --tests "*CorrelationIdFilterTest" --no-daemon`
- 결과: **19 tests, 전부 통과**(2회차 — 09a 구현 당시 기준. 09a-RF 보완 후 28 tests: `phase-09a-review-fix.md` 참조).
  - `AuditHmacTest`(5): 결정성, plain SHA-256 과 상이, secret/id 별 상이, null, blank 거부.
  - `CorrelationIdFilterTest`(3): 헤더 재사용+echo, UUID 생성, blank 헤더, MDC 정리.
  - `AuditConfigTest`(3): secret 주입 빈 생성, 비운영 fallback, prod blank 기동 실패.
  - `ActivityLogRepositoryTest`(2, @DataJpaTest): 저장/조회/enum/metadata, 필수필드 누락 예외.
  - `ActivityLogServiceTest`(6, @SpringBootTest): 필드매핑·occurredAt(clock), applicantRefHash=HMAC≠SHA256, metadata 직렬화/null, correlationId MDC, **recordInCurrentTx 롤백 시 소멸 / recordRequiresNew 롤백에도 잔존**.
- 전체 회귀 스위트: 별도 실행(보고 본문 참조).

## 10. 알려진 한계 (Known limitations)

- 업무 이벤트 계측이 없어 현재 ActivityLog 에 실제 데이터가 쌓이지 않는다(09b 가 채움).
- `AuditMetadata` 는 아직 sealed 가 아니고 구체 record 가 없다(09b).
- `traceId`(OTel)는 항상 null(deferred).
- 비운영 fallback pepper 사용 시 HMAC 이 예측 가능 — 운영은 반드시 `AUDIT_HMAC_SECRET` 주입.
- 운영 DB(MariaDB)는 `activity_log` 테이블 수동 DDL 필요(H2 dev/test 는 ddl-auto 자동 생성) — 스크립트: `docs/codex/ops/phase-09a-activity-log-ddl.sql`(09a-RF).

## 11. 다음 슬라이스 (Next)

- **9b**: Export/Pdf/Upload 로거 → `ActivityLogService` adapter(dual-write), egress fail-close(temp file 누수 방지 패턴), reopen·StageResult·첨부 admin 계측, typed `AuditMetadata` 구체 record(`UploadMetadata` 는 `sourceFileNameHash`+ext), admin audit read API(마스킹/원문 권한 분리 + page/range/projection 가드).
- 관찰: 컨텍스트에 **Quartz 스케줄러**가 존재한다 — 9c/9e 의 retention 스케줄(설계는 disabled-by-default)에 `@Scheduled` 대신 Quartz 활용 가능(별도 검토).
