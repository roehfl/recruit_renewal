# Phase 09 — 개인정보 파기 / 감사 / 보존 (Privacy Purge, Audit, Retention) 설계

> 설계 세션: grill-with-docs, 2026-06-04. 본 문서는 구현 착수 전 **설계 기준(source of truth)** 이며, 구현 상세는 각 슬라이스의 `docs/codex/implementation/*.md` 가 이어받는다.
>
> 관련 산출물:
> - `CONTEXT.md` — Privacy / Audit glossary (ActivityLog, 파기, retentionAnchorAt, PURGED, 파기 saga, ROLE_PRIVACY_ADMIN 등)
> - `docs/adr/0005-retention-purge-mode-tombstone-anonymization-binary-deletion.md`
> - `docs/adr/0006-audit-transaction-policy.md`
> - `docs/adr/0007-privacy-admin-role-separation.md`
> - **`docs/codex/implementation/phase-09-pii-field-inventory.md` — 9d 선행 산출물(필드 분류, instruction.md 리뷰 #1 반영)**
> - HTML 리포트: `docs/codex/reports/phase-09-privacy-purge-audit-retention-design.html`
>
> 2026-06-04 리뷰 반영(instruction.md): PII 필드 인벤토리 선행 산출물화, terminal query 구체화, typed AuditMetadata, PhysicalFileStatus SOFT_DELETED 분리, requestMatcher method 분기, 9b read 가드, ADR accepted 게이트.

---

## 1. Phase 이름

Phase 09 — Privacy Purge, Audit, Retention (개인정보 파기 / 영속 감사 / 보존 정책).

## 2. 목적 (Purpose)

1. **영속 감사 기반 구축**: 현재 SLF4J 구조적 로그(`recruit.audit.*`)로만 남는 감사 이벤트를 영속 `ActivityLog`(append-only) 로 이관하고, 핵심 관리자 변경/정보 반출/파기 lifecycle 을 증적으로 남긴다.
2. **개인정보 보존/파기 구현**: 채용 종료 후 보존기간이 경과한 지원자 개인정보를 **tombstone 익명화 + 첨부 바이너리 물리삭제** 방식으로 파기하고, 파기 산정/실행을 원장(`PurgeBatch`/`PurgeJobItem`)과 감사로 남긴다.

두 기둥은 **감사 우선** 으로 빌드한다(감사는 파기가 증적을 남기는 sink 이고, 가역적이며, 비가역 파기는 검증된 감사 기반 위에 올린다).

## 3. 범위 (Scope)

### 3.1 감사 (Audit)

- `ActivityLog` append-only 엔티티 + enum(`actorType`/`actionType`/`actionResult`/`targetType`/`reasonCode`) + repository.
- `ActivityLogService` — 명시적 2경로: `recordInCurrentTx()`, `recordRequiresNew()`.
- correlationId 전파(servlet Filter + MDC, `X-Request-Id` 재사용 또는 UUID 생성).
- `applicantRefHash`(HMAC-SHA256 + server pepper `AUDIT_HMAC_SECRET`).
- 기존 `ExportAuditLogger`/`PdfAuditLogger`/`UploadAuditLogger` 를 `ActivityLogService` adapter 로 정리(dual-write: DB = source of truth, SLF4J = 보조).
- 정보 반출(export/PDF/admin download) **fail-close**.
- 핵심 관리자 변경 계측: StageResult 변경/발표/확정, evaluation reopen, application admin 처리, 첨부 관리자 download/delete, RetentionPolicy 변경, purge lifecycle.
- 관리자 audit read API(권한별 마스킹/원문 분기).

### 3.2 보존/파기 (Retention / Purge)

- `RetentionPolicy`(전역 기본 + 공고별 override), `RetentionHold`(보존 예외).
- `JobPosting.hiringEndedAt`(retentionAnchorAt 소스).
- eligibility 산정(`Clock` 주입) + reasonCode.
- dry-run / execute `PurgeBatch` + `PurgeJobItem`(append-only 원장).
- 관계형 PII tombstone 익명화 + Applicant ref-count 익명화.
- 첨부 바이너리 물리삭제 **stateful saga + reconciliation**(`storage-health-scan` 확장).
- `ROLE_PRIVACY_ADMIN` 권한 분리 + execute 안전장치(confirmation, `sourceDryRunBatchId`, 실행시 eligibility 재검증).

## 4. 범위 제외 (Out of scope)

- **AOP blanket 접근 감사**: 전역 `CREATE/UPDATE/DELETE/VIEW_PAGE/ACCESS_API` 자동기록(로그 폭증/PII 위험/성능). 후속 후보로만 문서화.
- **ActivityLog 자체 lifecycle policy**: 감사 로그 자체의 보존기간/접근통제/`ipAddress`·`userAgent` 마스킹/N년 삭제·회전·아카이빙. 지원자 파기 job 은 감사 row 를 수정/삭제/마스킹하지 않는다. 별도 후속 설계.
- **forced purge (정보주체 삭제요청)**: retention 미도래 우회 파기. `triggerType` enum 슬롯(`DATA_SUBJECT_REQUEST`/`FORCED_PURGE`)만 남기고 endpoint/실행 로직은 만들지 않는다. Phase 09 파기는 eligibility 충족 건만 대상.
- **파기 후 통지 메일**: `MessageBatch`/`MessageSendLog` 미구현 backlog. PurgeBatch 완료 → MessageBatch 트리거 **hook 지점만** 문서화. 파기와 통지 책임 분리.
- **스케줄 auto-execute**: `@Scheduled` 는 설계에 두되 purge 실행은 **disabled by default**. 자동은 후보 scan 까지만, 실행 자동화는 운영검증 후 후속 Phase.
- **per-subject envelope key / crypto-shred**: 별도 대규모 보안 설계.
- **Messaging 신규 기능**, 통계/엑셀 신규 기능.

## 5. 핵심 설계 결정 (locked)

### 5.1 감사 (ADR-0006)

- ActivityLog = **append-only** 감사 증적. **지원자 원문 PII 미저장(applicant raw PII-free)** — 단 `actorId`/`ipAddress`/`userAgent`/`applicationId`/`applicantRefHash` 포함이므로 "완전 PII-free 테이블"이 아니다.
- 트랜잭션 정책(사실 기준 3-way):
  - **커밋된 변경 성공 증적** → 비즈니스 tx 안에서 insert(원자적). 감사 insert 실패 시 비즈니스 rollback. afterCommit 금지.
  - **실패/거부/충돌/스킵 증적** → `REQUIRES_NEW`(비즈니스 rollback 돼도 잔존).
  - **정보 반출(export/pdf/download)** → 별도 tx + **fail-close**(반환 전 감사 commit 성공 필수, 깨지면 over-record 허용).
- emission = 명시적 `recordInCurrentTx()`/`recordRequiresNew()` 2경로(AOP 아님). `recordRequiresNew()` 는 self-invocation 회피 위해 별도 bean.
- `oldValue/newValue` 기본 금지(정정 전후는 기존 `StageResultCorrectionHistory` 가 보유, ActivityLog 는 `targetId` 참조). 필요 시 allowlist + masking.
- 정정 = row 수정이 아니라 correction event 추가. update/delete API·public setter 없음. `@Version` 불필요.

#### ActivityLog 스키마(필드)

| 필드 | 타입 | 비고 |
| --- | --- | --- |
| `id` | Long PK | |
| `occurredAt` | LocalDateTime | `Clock` 주입 |
| `actorType` | enum | `EMPLOYEE`/`SYSTEM`/`APPLICANT`/`ANONYMOUS`. Phase 09 emission 은 EMPLOYEE/SYSTEM/ANONYMOUS 만(APPLICANT 자가행위 제외, enum 엔 존재) |
| `actorId` | String nullable | loginId. SYSTEM/anon 은 null |
| `actorRoleSnapshot` | String nullable | 행위 시점 권한 스냅샷(라이브 join 아님). ADMIN/INTERVIEWER 구분도 이 값 |
| `actionType` | enum | EXPORT_*, APPLICATION_PDF, STAGE_RESULT_UPLOAD, STAGE_RESULT_CORRECT/ANNOUNCE/CONFIRM, EVALUATION_REOPEN, ATTACHMENT_ADMIN_DOWNLOAD/DELETE, RETENTION_POLICY_UPDATE, RETENTION_HOLD_*, PURGE_SCAN/PURGE_EXECUTE … (Java enum + DB VARCHAR, CommonCode 아님) |
| `actionResult` | enum | `SUCCESS`/`FAILURE`/`DENIED`/`SKIPPED`/`CONFLICT`. CONFLICT 독립(FAILURE 와 합치지 않음). 상태성(STARTED/REQUESTED/COMPLETED)은 actionType 으로 |
| `targetType` | enum | STAGE_RESULT/JOB_APPLICATION/APPLICATION_ATTACHMENT/INTERVIEW_EVALUATION/EXPORT_DATASET/APPLICATION_PDF/RETENTION_POLICY/RETENTION_HOLD/PURGE_BATCH … (enum) |
| `targetId` | String nullable | 대상 PK |
| `jobPostingId` | Long nullable | denormalized search key(추출 가능한 이벤트만) |
| `applicationId` | Long nullable | |
| `applicantRefHash` | String nullable | HMAC-SHA256 + server pepper. 단순 SHA256(id) 금지. hard-delete/파기 후 연결 증적용 |
| `reasonCode` | enum/String nullable | VERSION_MISMATCH/AUTH_DENIED/VALIDATION_FAILED/RETENTION_NOT_DUE/RETENTION_HOLD/ALREADY_PURGED/ANCHOR_NOT_FIXED/APPLICATION_NOT_TERMINAL/BINARY_DELETE_FAILED … |
| `reasonMessage` | String nullable | sanitized human-readable only |
| `correlationId` | String nullable | X-Request-Id 또는 생성 UUID |
| `traceId` | String nullable | OTel 있을 때만(현재 deferred) |
| `ipAddress` | String nullable | 민감 — 노출은 ROLE_PRIVACY_ADMIN |
| `userAgent` | String nullable | 민감 — 노출은 ROLE_PRIVACY_ADMIN |
| `metadataJson` | TEXT/CLOB | actionType 별 allowlist + PII-free 검증. 임의 Map 금지. DB JSON 타입 아님(H2/MariaDB 이식성) |

> `actorAuthority` 단수 네이밍 대신 `actorRoleSnapshot`(시점 스냅샷 의미) 사용.

**metadataJson = typed `AuditMetadata`(자유 Map 금지, 리뷰 #3)**: 호출부에서 raw JSON 문자열/`Map<String,Object>` 를 넘기면 누군가 `applicantName`/`phone`/`email` 을 넣을 위험이 있다. 따라서 actionType 별 **sealed `AuditMetadata` typed record** 로 고정하고, `ObjectMapper.writeValueAsString(metadata)` 직렬화는 **`ActivityLogService` 내부에서만** 수행한다. 호출부는 typed record 만 전달한다.

```java
public sealed interface AuditMetadata permits
        ExportMetadata, PdfMetadata, UploadMetadata, UploadConflictMetadata,
        StageResultChangeMetadata, EvaluationReopenMetadata,
        AttachmentAdminMetadata, RetentionPolicyChangeMetadata, PurgeBatchMetadata {}

// 기존 로거 흡수(실측 키 기준, PII-free):
record ExportMetadata(String datasetType, String filtersHash, String filtersSafeJson, long rowCount, String fileName) implements AuditMetadata {}
record PdfMetadata(long applicationId, long jobPostingId, long jobPositionId) implements AuditMetadata {}
record UploadMetadata(long stageId, String outcome, long rowCount, long changedCount, long unchangedCount, long errorCount, long staleCount, String sourceFileName, long sourceFileSize, String contentHash) implements AuditMetadata {}
record UploadConflictMetadata(long stageId, String sourceFileName, long sourceFileSize, String contentHash) implements AuditMetadata {}
// 신규 이벤트: StageResultChange / EvaluationReopen / AttachmentAdmin / RetentionPolicyChange / PurgeBatch (집계만, PII-free)
```
> actor/ip/ua/requestId/timestamp 는 `metadataJson` 이 아니라 ActivityLog **컬럼**(actorId/ipAddress/userAgent/correlationId/occurredAt)에 둔다 — metadata 와 중복 금지.

### 5.2 파기 방식 (ADR-0005)

- 기본 = **tombstone anonymization + binary physical delete**. 원문 PII 비가역 소거 + 통계/감사 연결용 비식별 tombstone 보존.
- crypto-shred 기각(ci 만 암호화 + 글로벌 단일 키), 전면 hard delete 기각(cascade/감사단절/통계재현불가).
- **보존(tombstone) 후보**: `applicationId`/`jobPostingId`/`jobPositionId`/stage·result status code/submitted date bucket/`purgedAt`/`purgeBatchId`/`purgeResult`.
- **소거/익명화 대상**: name/email/phone/ci/address/answers 원문/학력·경력·자격·어학·수상 등 재식별 가능 상세 섹션 원문/첨부 바이너리/originalFilename/PII 가능 자유입력 reason·comment.
- Applicant 공통 PII 는 **ref-count** — 그 Applicant 의 모든 JobApplication 이 파기 대상일 때만 익명화.
- **익명화 완전성·비가역성이 파기 인정 요건**. quasi-identifier 잔존 방지를 위한 entity 별 field-level allowlist 를 구현 문서에 명시(본 설계의 실질 안전장치).

### 5.3 보존/적격성

- **retentionAnchorAt**: 공고 단위, 소스 = `JobPosting.hiringEndedAt`(신규). **암묵 closedAt fallback 금지** — null 이면 `ANCHOR_NOT_FIXED` SKIP. `closedAt` 사용은 `RetentionPolicy.baselineType = CLOSED_AT` 명시 선택 시에만.
- **RetentionPolicy**: 전역 기본 + 공고별 override. `retentionPeriod`/`baselineType`(HIRING_ENDED_AT|CLOSED_AT)/`enabled`/`effectiveFrom`/`effectiveTo`(+override `jobPostingId`). 법정 일수 하드코딩 금지.
- **eligibility** = `anchor 종료 + retentionPeriod 경과 + not purged + not hold + terminal`. 제외 = SKIPPED + reasonCode {RETENTION_NOT_DUE, RETENTION_HOLD, ALREADY_PURGED, ANCHOR_NOT_FIXED, APPLICATION_NOT_TERMINAL, INVALID_STAGE_CONFIGURATION}.
- **terminal 판정 — 구체 query (9c 확정, 실제 enum 검증 완료)**: 아래 enum 값은 실제 코드에 존재함을 확인했다(`StageStatus`={READY, IN_PROGRESS, **RESULT_ANNOUNCED**, **CLOSED**}, `StageResultStatus`={**PENDING**, PASSED, FAILED, ABSENT, WITHDRAWN, HOLD}; `Stage.finalStage`(boolean)/`Stage.status`/`StageResult.resultStatus`/`StageResult.decidedAt` 실존).

  ```text
  terminal =
    JobApplication.status == WITHDRAWN
    OR (
      finalStage row 가 정확히 1개 존재 (Stage.finalStage == true)
      AND finalStage.status IN (RESULT_ANNOUNCED, CLOSED)
      AND 해당 application + finalStage 의 StageResult 존재
      AND StageResult.resultStatus != PENDING
      AND StageResult.decidedAt != null
    )
  ```
  - finalStage 가 **없거나 2개 이상**이면 → SKIP `INVALID_STAGE_CONFIGURATION`.
  - 위 조건 미충족(결과 미확정/진행중) → SKIP `APPLICATION_NOT_TERMINAL`. (구현자 임의 해석 금지 — 이 query 가 9c 의 계약.)
- **RetentionHold**: 자동 제외는 **최종 입사확정/onboarded/HR 이관 완료만**. 중간 전형 PASSED 는 제외 기준 아님(불합격·전형포기·미응시·최종합격後 입사포기·채용 미확정 종료는 retention 경과 시 파기 대상).
- 날짜 계산은 `Clock` bean 주입, 테스트는 fixed Clock(과거 Stage 테스트 date 의존 사전실패 전례 회피).

### 5.4 파기 실행 (ADR-0005)

- 2-level 원장: `PurgeBatch`(dry-run/execute 1회 실행) 1:N `PurgeJobItem`(application 별 결과, append-only). 중간 PurgeJob 없음.
- `ActivityLog` 는 batch 단위 **coarse index** 만(시작/완료/부분실패/실패 + 집계 metadataJson). item 결과 중복기록 금지.
- dry-run/execute 별도 batch. execute 에 `sourceDryRunBatchId` nullable. **execute 는 dry-run 을 믿지 않고 실행시 eligibility 재검증**. dry-run↔execute 대상차이는 감사/배치결과에 기록.
- **트랜잭션**: application 1건(섹션/answers/첨부메타/ref-count) = 한 item tx(all-or-nothing per application). batch 는 비원자 집계 컨테이너(`COMPLETED`/`PARTIAL_FAILED`). batch `FAILED` = 시작/criteria 생성 실패만. `ALREADY_PURGED` = idempotent skip.
- **PURGED 정의** = 관계형 PII 제거 **AND** 첨부 바이너리 소멸 확인까지 완료. 바이트 잔존 시 PURGED 아님. **"DB PURGED + 파일 잔존" 절대 불허**.
- **첨부 saga**: ① DB tx(PII·originalFilename 제거, attachment/item `BINARY_DELETE_PENDING`, JobApplication `PURGE_PENDING`, commit) → ② 파일 물리삭제(`deleteIfExists` 멱등 + 존재 재확인, 이미 없음 = `MISSING_AS_SUCCESS`) → ③ DB tx(소멸 확인 → `PURGED`/`purgedAt` 확정, 실패 → `BINARY_DELETE_FAILED`/`PARTIAL_FAILED`). `purgedAt` 은 **최종 PURGED 시점에만** 세팅.
- reconciliation sweep 이 PENDING/FAILED 재처리. `storage-health-scan` 은 "DB PURGED 인데 파일 존재"를 치명적 불일치로 탐지. "파일 소멸 + DB pending" = 안전(나중에 PURGED 승격).

### 5.5 인가/안전장치 (ADR-0007)

- **ROLE_PRIVACY_ADMIN 전용**: purge execute, RetentionPolicy/RetentionHold 변경, ActivityLog 민감필드(ip/ua) 원문, purge batch 상세/실행결과 원문.
- **ROLE_RECRUIT_ADMIN 까지**: retention dry-run/scan, retention 결과 조회, ActivityLog 마스킹 목록, RetentionPolicy read-only.
- 두 권한 모두 `DeptRoleMapping` 파생(하드코딩 금지). **narrow requestMatcher 를 broad `/api/admin/**` 보다 먼저** 배치(순서가 보안 요구사항).
- **requestMatcher 는 path 뿐 아니라 HTTP method 까지 분기**(리뷰 #5) — 같은 `/api/admin/retention/policies/**` 라도 GET=RECRUIT, write=PRIVACY. SecurityConfig 구현 지시문에 아래 수준으로 명시(기존 broad `/api/admin/**` 보다 **위**):

  ```java
  .requestMatchers(HttpMethod.POST,   "/api/admin/retention/purge-batches/execute").hasAuthority("ROLE_PRIVACY_ADMIN")
  .requestMatchers(HttpMethod.POST,   "/api/admin/retention/policies/**").hasAuthority("ROLE_PRIVACY_ADMIN")
  .requestMatchers(HttpMethod.PUT,    "/api/admin/retention/policies/**").hasAuthority("ROLE_PRIVACY_ADMIN")
  .requestMatchers(HttpMethod.DELETE, "/api/admin/retention/policies/**").hasAuthority("ROLE_PRIVACY_ADMIN")
  .requestMatchers("/api/admin/retention/holds/**").hasAuthority("ROLE_PRIVACY_ADMIN")            // write 계열
  .requestMatchers(HttpMethod.GET,    "/api/admin/retention/**").hasAnyAuthority("ROLE_RECRUIT_ADMIN","ROLE_PRIVACY_ADMIN")
  .requestMatchers(HttpMethod.GET,    "/api/admin/audit/**").hasAnyAuthority("ROLE_RECRUIT_ADMIN","ROLE_PRIVACY_ADMIN")
  // ↑ 모두 기존 .requestMatchers("/api/admin/**").hasAnyAuthority("ROLE_ADMIN","ROLE_RECRUIT_ADMIN") 보다 먼저
  ```
  audit 원문(ip/ua)·purge batch 원문은 GET 통과 후 **컨트롤러/서비스에서 권한별 projection 분기**(마스킹 vs 원문)로 추가 게이팅.
- execute: bulk = `sourceDryRunBatchId` 필수, 단건 = confirmationFlag/Token + eligibility 재검증. maxPurgeCount/batch size 가드 후보.

## 6. 슬라이스 분할 (build order)

| Slice | 범위 |
| --- | --- |
| **9a — ActivityLog Foundation** | `ActivityLog` schema/enums/repository, `ActivityLogService`(recordInCurrentTx/recordRequiresNew), correlationId filter(MDC), `applicantRefHash`(HMAC + `AUDIT_HMAC_SECRET`), append-only 정책. 업무 이벤트 계측은 아직 없음. |
| **9b — 기존 로그 흡수 + 핵심 관리자 변경 audit + read API** | Export/Pdf/Upload 로거 → ActivityLogService adapter(dual-write), egress fail-close, reopen·StageResult 정정/발표/확정·첨부 admin download/delete 계측, typed `AuditMetadata` 도입, admin audit read API(마스킹=RECRUIT_ADMIN / 원문=PRIVACY_ADMIN). **read API 가드(리뷰 #6, 9b 범위)**: page size max / occurredAt range max / default recent range / ip·ua 마스킹 테스트 / metadataJson PII 금지 테스트 / RECRUIT↔PRIVACY projection 분리 테스트. |
| **9c — Retention 모델 + eligibility scan + dry-run** | `RetentionPolicy`(전역+override)·`RetentionHold`·`JobPosting.hiringEndedAt`, eligibility(Clock), dry-run `PurgeBatch`(scan/preview, 무변경), reasonCode 산정. |
| **9d-1 — Purge execute core** | ROLE_PRIVACY_ADMIN, confirmation/`sourceDryRunBatchId`, 실행시 eligibility 재검증, `PurgeBatch`/`PurgeJobItem` execute 상태전이, JobApplication/section/answer 관계형 PII tombstone/anonymization, Applicant ref-count 익명화, ActivityLog coarse index. **첨부 바이너리 삭제 완료 전 최종 PURGED 승격 금지.** |
| **9d-2 — Attachment binary delete saga** | `ApplicationAttachment.physicalFileStatus` 전이(BINARY_DELETE_PENDING/BINARY_DELETED/BINARY_DELETE_FAILED), 파일 물리삭제(deleteIfExists/idempotent), 소멸 확인 후 JobApplication/PurgeJobItem **최종 PURGED 승격**, 실패 시 PARTIAL_FAILED/BINARY_DELETE_FAILED 유지. |
| **9e — Reconciliation + 안정화/테스트 하드닝** | `storage-health-scan` 확장(PENDING/FAILED 재처리 + "PURGED인데 파일존재" 치명탐지), 회귀(PII 부재 검증/권한 매트릭스/멱등/Clock). |

> 문서상 9d 는 한 장으로 묶을 수 있으나 **구현 지시문은 반드시 9d-1/9d-2 로 분리**한다.

## 7. 스키마 / DDL 변경 (migration framework 없음 → 전부 "수동 DDL 필요")

### 7.1 신규 테이블 (5)

`ActivityLog`, `RetentionPolicy`, `RetentionHold`, `PurgeBatch`, `PurgeJobItem`.

### 7.2 신규 컬럼 / 변경

- `JobApplication`: `purgeBatchId`, `purgeResult`(enum: `PURGE_PENDING`/`PARTIAL_PENDING`/`PARTIAL_FAILED`/`PURGED`), `purgedAt`(최종 PURGED 시점에만). `JobApplicationStatus` enum **불변**(purge 는 orthogonal marker).
- `ApplicationAttachment`: `PhysicalFileStatus` **재정의**(리뷰 #4) — 기존 `DELETED` → **`SOFT_DELETED` 개명**(현 `markDeleted()` soft-delete 의미와 purge 물리삭제 의미 분리) + 신규 `BINARY_DELETE_PENDING`/`BINARY_DELETED`/`BINARY_DELETE_FAILED`. 최종 = `METADATA_ONLY/STORED/MISSING/SOFT_DELETED/BINARY_DELETE_PENDING/BINARY_DELETED/BINARY_DELETE_FAILED`. **기존 DB `'DELETED'` row → `'SOFT_DELETED'` 수동 UPDATE 마이그레이션** 필요. + `filenameHash`·`binaryDeletedAt` 신규, `originalFileName`(NOT NULL) → PLACEHOLDER `"__PURGED__"`, `storagePath`(NOT NULL, len 1000) → ALTER nullable 후 최종 소멸 시 null.
- `JobPosting`: `hiringEndedAt`(신규, retentionAnchorAt 소스). `finalizedAt` 이름은 모호하여 채택 안 함.
- **섹션/answers PII 컬럼**: **`phase-09-pii-field-inventory.md` 에서 전 필드 분류 완료(9d 선행 산출물).** NOT NULL date PII(`acquiredDate`/`examDate`/`awardDate`/gap `startDate`·`endDate`/`storagePath`)는 ALTER nullable+NULLIFY, NOT NULL String PII(`applicantNameSnapshot`/`schoolName`/`companyName`/`certificateName`/`issuingOrganization`/`languageName`/`testName`/`awardName`/`awardingOrganization`/gap `reason`/`originalFileName`)는 PLACEHOLDER 기본. `createdBy`(@Column updatable=false)는 JPQL/native bulk update 로만 클리어.

### 7.3 권한 / 비밀

- `ROLE_PRIVACY_ADMIN`: `DeptRoleMapping` 데이터(개인정보보호/컴플라이언스 부서) + `SecurityConfig` requestMatcher.
- `AUDIT_HMAC_SECRET`(pepper): env/config 주입. prod 누락 = 기동 실패(fail-safe), test/dev = 전용 값 명시 주입. 하드코딩 금지.

## 8. API 목록 (계획)

> 경로 prefix `/api`. narrow PRIVACY 경로는 broad `/api/admin/**` matcher 보다 먼저 등록.

| Method | Path | 권한 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/admin/audit/activities` | RECRUIT_ADMIN(마스킹) / PRIVACY_ADMIN(원문) | 감사 로그 검색(actorId/actionType/actionResult/targetType/jobPostingId/applicationId/occurredAt range, 페이지네이션) |
| GET | `/api/admin/audit/activities/{id}` | 동일 | 감사 단건(권한별 ip/ua 마스킹) |
| GET | `/api/admin/retention/policies` | RECRUIT_ADMIN | RetentionPolicy 조회(전역+override) |
| POST/PUT/DELETE | `/api/admin/retention/policies/**` (write) | **PRIVACY_ADMIN** | RetentionPolicy CUD(in-tx 감사) |
| GET/POST/DELETE | `/api/admin/retention/holds/**` | **PRIVACY_ADMIN**(write) / RECRUIT_ADMIN(read) | RetentionHold 관리/조회 |
| POST | `/api/admin/retention/purge-batches/dry-run` | RECRUIT_ADMIN | dry-run scan(eligibility 산정, 무변경, PurgeBatch mode=DRY_RUN 생성) |
| GET | `/api/admin/retention/purge-batches` | RECRUIT_ADMIN(요약) / PRIVACY_ADMIN(원문) | PurgeBatch 목록/결과 |
| GET | `/api/admin/retention/purge-batches/{id}` | RECRUIT_ADMIN(요약) / PRIVACY_ADMIN(원문) | PurgeBatch 상세 + PurgeJobItem |
| POST | `/api/admin/retention/purge-batches/execute` | **PRIVACY_ADMIN** | purge execute(confirmation + bulk 는 sourceDryRunBatchId 필수 + 실행시 재검증) |

## 9. Entity / DTO / Service / Controller 요약 (계획)

- **Entity**: `ActivityLog`(append-only), `RetentionPolicy`, `RetentionHold`, `PurgeBatch`, `PurgeJobItem` + 기존 `JobApplication`/`ApplicationAttachment`/`JobPosting` 컬럼 확장.
- **Enum**: `ActorType`, `AuditActionType`, `AuditActionResult`, `AuditTargetType`, `AuditReasonCode`(또는 String), `PurgeResult`(JobApplication), `PurgeBatchMode`(DRY_RUN/EXECUTE), `PurgeBatchStatus`(RUNNING/COMPLETED/PARTIAL_FAILED/FAILED), `PurgeItemStatus`, `RetentionBaselineType`(HIRING_ENDED_AT/CLOSED_AT), `PurgeTriggerType`(RETENTION + 슬롯 DATA_SUBJECT_REQUEST/FORCED_PURGE), `PhysicalFileStatus` 확장.
- **Service**: `ActivityLogService`(2경로), `RetentionPolicyService`, `RetentionEligibilityService`(Clock), `PurgeExecutionService`(item-level tx), `AttachmentPurgeSagaService`, reconciliation(`StorageHealthScanService` 확장), audit read service(권한별 projection).
- **Controller**: `AdminAuditController`, `AdminRetentionController`(policies/holds/purge-batches). 기존 export/pdf/upload 컨트롤러는 service 계층에서 ActivityLogService 경유로 정리.
- **DTO**: read-only audit/purge-batch response(record + `from()`), dry-run/execute request(confirmation/sourceDryRunBatchId), `PageResponse<T>` 재사용. **응답에 지원자 PII·ci/password 노출 금지**, ip/ua 는 권한별 마스킹.

## 10. 검증 / 비즈니스 규칙

1. ActivityLog 는 append-only — update/delete API·public setter 없음. 정정=correction event.
2. 감사 트랜잭션: 커밋변경=in-tx, 실패계열=REQUIRES_NEW, 반출=fail-close(ADR-0006).
3. ActivityLog 는 지원자 원문 PII 미저장. `metadataJson` 은 actionType 별 allowlist + PII-free 검증.
4. 파기 eligibility = anchor 종료 + retentionPeriod 경과 + not purged + not hold + terminal. 부적격 = SKIPPED + reasonCode.
5. anchor 암묵 fallback 금지(`ANCHOR_NOT_FIXED`).
6. execute = confirmation 필수, bulk 는 sourceDryRunBatchId 필수, 실행시 eligibility 재검증.
7. item-level 원자성, batch 비원자. ALREADY_PURGED = idempotent skip.
8. PURGED = 관계형 PII 제거 + 바이너리 소멸 확인. "DB PURGED + 파일 잔존" 불허.
9. ref-count: Applicant 공통 PII 는 모든 JobApplication 파기 시에만 익명화.
10. 권한 분리(ADR-0007) + requestMatcher 순서.
11. 날짜 계산 = Clock 주입(fixed Clock 테스트).

## 11. 테스트 명령

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; $env:AUDIT_HMAC_SECRET='test-audit-hmac-pepper-0001'; .\gradlew.bat clean test --no-daemon
```

> `AUDIT_HMAC_SECRET` 은 Phase 09 부터 필요. test 전용 값 주입. 슬라이스별로는 관련 테스트만 분리 실행 가능하나 최종 보고에 전체 실행 여부 명시.

## 12. 테스트 결과

- 설계 단계 — 구현/테스트 미실행. 각 슬라이스 구현 문서에서 실제 결과 기록.

## 13. 남은 이슈 / 한계

- **field-level 익명화 allowlist 는 `phase-09-pii-field-inventory.md` 에서 전 필드 분류 완료(9d 선행 산출물).** 남은 확인 항목은 그 문서 §10(날짜 보존 trade-off, PLACEHOLDER vs ALTER 일괄정책, `Interview.memo` 잔존, `ciHash` 보존).
- ActivityLog 자체 lifecycle(보존/회전/ip·ua 마스킹)은 후속.
- forced purge(정보주체 삭제요청)는 enum 슬롯만, 후속 설계.
- 스케줄 auto-execute disabled-by-default, 운영검증 후 활성.
- `traceId`(OTel)는 현재 deferred(nullable).
- 운영 권한 매트릭스(ROLE_PRIVACY_ADMIN dept 매핑) 최종 확정은 운영 협의 필요.

## 14. 다음 Phase 권고

- Phase 09 완료 후: ActivityLog lifecycle policy, forced purge(정보주체 삭제요청), 스케줄 auto-execute 활성, Messaging(파기/결과 통지) 도메인.
- **ADR status 전환(리뷰 #7)**: 9a 구현 지시문에 ADR-0006/0007 을 `proposed → accepted` 로 전환한다고 명시. **ADR-0005 는 `phase-09-pii-field-inventory.md`(특히 §9 DDL·§10 확인항목) 확정 전까지 `accepted-with-implementation-gate`** 로 두고, 인벤토리 확정 시 `accepted` 로 전환.
