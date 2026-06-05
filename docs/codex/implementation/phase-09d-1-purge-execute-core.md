# Phase 09d-1 — Purge Execute Core (관계형 PII tombstone + ref-count + 원장 전이)

## Phase Summary

- Date: 2026-06-05
- Work type: implementation (설계: `phase-09-privacy-purge-audit-retention-design.md` §5.2/§5.4·slice 9d-1, **계약: `phase-09-pii-field-inventory.md`**, ADR-0005/0007)
- Goal: 비가역 파기 실행의 core — execute API(안전장치), 실행 시 eligibility **재검증**, 관계형 PII tombstone(인벤토리 분류표 그대로), Applicant ref-count 익명화, `PurgeBatch`/`PurgeJobItem` execute 상태전이, coarse 감사. **첨부 바이너리 삭제 saga(물리삭제·최종 승격)는 09d-2.**

## Implemented Scope

### A — Execute API + 안전장치 (ADR-0007)

| Method | Path | 권한 |
| --- | --- | --- |
| POST | `/admin/retention/purge-batches/execute` | **ROLE_PRIVACY_ADMIN**(narrow matcher, broad 보다 먼저) |

- `PurgeExecuteRequest(confirm, sourceDryRunBatchId, applicationId)`:
  - **confirm=true 명시 필수**(비가역 실행 확인) — 아니면 400.
  - **bulk** = `sourceDryRunBatchId` 필수(존재 + mode=DRY_RUN 검증, 그 batch 의 **ELIGIBLE item 만** 후보) / **단건** = `applicationId` — 정확히 하나(XOR, 아니면 400).
  - actor blank 방어(`requireActor` — 9c 패턴).
- criteria 검증 실패는 batch 생성 **전** 400/404 — `FAILED` batch 는 시작 후 orchestration 실패 전용(설계 §5.4 해석 명시).

### B — 실행 시 eligibility 재검증 (dry-run 불신뢰)

- `PurgeItemProcessor.process()` 가 9c `RetentionEligibilityService` 를 그대로 재사용해 item 마다 재평가. 탈락(drift — 예: dry-run 후 hold 설정) = `SKIPPED`+reasonCode 로 기록(설계: "dry-run↔execute 대상차이는 배치결과에 기록"). `ALREADY_PURGED` = idempotent skip(재실행 안전).

### C — 트랜잭션 구조 (설계 §5.4)

- **application 1건 = 한 item 트랜잭션(REQUIRES_NEW, all-or-nothing)**: 재검증→tombstone→marker→ref-count 가 한 tx. 실패 시 그 건만 rollback, `recordFailure`(별도 REQUIRES_NEW)로 FAILED item 잔존.
- **batch 는 비원자 집계 컨테이너**: `PurgeBatchLifecycleService` 가 start(RUNNING)/complete(COMPLETED·**PARTIAL_FAILED**)/fail(FAILED) 을 각각 독립 tx 로 커밋. orchestrator(`PurgeExecutionService`)는 의도적으로 무트랜잭션.
- ActivityLog 는 **batch 단위 coarse index 만**: `PURGE_EXECUTE`(완료=SUCCESS·부분실패=FAILURE+reasonMessage·실패=FAILURE) + `PurgeExecuteMetadata`(집계). item 중복 기록 금지.

### D — 관계형 PII tombstone (인벤토리 §3~§7 계약 그대로)

- **`ApplicationPiiPurgeRepository`**(전용, delete 미노출): 인벤토리 분류표를 1:1 구현한 `@Modifying` bulk JPQL 12종 + certificate 번호 scalar 조회. 전용 인터페이스로 응집한 이유 — ① `createdBy` 는 `@Column(updatable=false)` 라 **JPQL bulk 만 가능**(인벤토리 §9), ② 인벤토리와의 대조 검증 용이.
- 처리 요약: answers(`answerText` NULLIFY) · 학력(schoolName PLACEHOLDER, major/degree/country/입학·졸업일 NULLIFY — **안 A: 정확 날짜 전부 null**) · semesterGrade(감사필드만 — metric 은 KEEP) · 경력(companyName PLACEHOLDER, 부서/직급/업무/사유/시작·종료일 NULLIFY) · careerProfile(감사필드만) · 자격(명칭/기관 PLACEHOLDER, 날짜/점수 NULLIFY, **certificateNumber = HMAC `hmacHex("CERT_NO:"+원문)` HASH_ONLY**) · 어학/병역/수상/공백(분류표 그대로) · **평가 comment NULLIFY(per-candidate 만 — Interview-level 공유 텍스트 불가침)** · JobApplication `createdBy/updatedBy` NULLIFY + `applicantNameSnapshot` 은 엔티티 `markPurge*` 가 PLACEHOLDER 처리.
- 섹션 엔티티 비로딩(scalar/bulk only) — stale flush 위험 없음. JobApplication 엔티티 변경(marker)과 bulk(createdBy)는 컬럼이 겹치지 않음(updatable=false 라 entity flush 가 bulk 결과를 못 덮음).
- **엔티티 nullable 완화 6필드**(인벤토리 §9): certificate.acquiredDate, language.examDate, award.awardDate, gapPeriod.startDate/endDate, career.startDate — 입력 필수는 request 검증이 계속 보장.

### E — Marker + PURGED 승격 금지 가드

- 바이너리 소멸 **미확인** 첨부가 있으면: `JobApplication.markPurgePending(batchId)`(purgedAt 미세팅) + item `PENDING` → 9d-2 saga 가 물리 소멸 확인 후 최종 승격.
- 미확인 판정 = `physicalFileStatus IN (STORED, **DELETED**)` — soft-delete 의 after-commit 물리삭제가 실패하면 DELETED 인데 파일이 잔존할 수 있으므로 **DELETED 도 outstanding**(적대 검증 반영 — "DB PURGED + 파일 잔존" 불허). MISSING/METADATA_ONLY 는 파일 부재 확인 상태.
- 미보유면: `markPurged(batchId, purgedAt)` + item `PURGED` — purgedAt 은 **이 시점에만**.

### F — Applicant ref-count 익명화 (인벤토리 §2)

- 이 지원자의 **모든** JobApplication 이 파기 대상(marker 보유)일 때만 `Applicant.purgePersonalData()`: `loginId/name/userName/email/password/phoneNumber/ci` → null, **`ciHash` → `"PURGED:"+UUID` sentinel**(NOT NULL·unique 유지, plain SHA-256 연결 단절 — 리뷰 2차 #1. 동일 CI 재가입 허용 = 파기 우선 방침).
- PURGE_PENDING 도 파기 대상(관계형 PII 는 이미 제거됨). 형제 중 SKIPPED 가 있으면 익명화하지 않음(보존 — 의도된 게이트).

## Not Implemented / Out of Scope

- **09d-2**: 첨부 바이너리 물리삭제 saga(`BINARY_DELETE_*` enum 확장 + `DELETED→SOFT_DELETED` 3단계 마이그레이션, originalFileName/storagePath/deletedBy 등 첨부 PII §6 처리, filenameHash/binaryDeletedAt 신규 컬럼, PENDING→PURGED 최종 승격).
- **09e**: reconciliation(storage-health-scan 확장, FAILED 재처리).
- forced purge / 스케줄 auto-execute / 파기 통지(MessageBatch hook) — 설계 범위 제외 승계.
- `StageResult.comment`/`StageResultCorrectionHistory` 의 comment 계열 — **인벤토리 미분류 갭으로 flag**(아래 한계 6).

## Changed Files

### New Files (main 7)

| File | Type |
|------|------|
| `domain/repository/ApplicationPiiPurgeRepository.java` | Repository(전용 bulk, delete 미노출) |
| `service/ApplicationPiiPurgeService.java` | Service(tombstone 실행, REQUIRED) |
| `service/PurgeItemProcessor.java` | Service(item REQUIRES_NEW + recordFailure) |
| `service/PurgeBatchLifecycleService.java` | Service(start/complete/fail + coarse 감사) |
| `service/PurgeExecutionService.java` | Service(orchestrator, 무트랜잭션) |
| `service/PurgeExecuteMetadata.java` | AuditMetadata record |
| `dto/request/PurgeExecuteRequest.java` | Request DTO |

### Modified Files (main 12)

| File | Change |
|------|--------|
| `domain/entity/JobApplication.java` | `PURGED_PLACEHOLDER` + `markPurgePending`/`markPurged` |
| `domain/entity/Applicant.java` | `purgePersonalData(ciHashSentinel)` |
| `domain/entity/PurgeBatch.java` | purged/pending/failed 집계 컬럼 + `startExecute`/`completeExecute` |
| `domain/entity/PurgeJobItem.java` | `executePurged`/`executePending`/`executeFailed` 팩토리 |
| `domain/entity/ApplicationCertificate·Language·Award·GapPeriod·Career.java` | NOT NULL date PII 6필드 nullable 완화(인벤토리 §9) |
| `dto/response/PurgeBatchResponse.java` | execute 집계 3필드 |
| `service/AuditMetadata.java` | permits +1(`PurgeExecuteMetadata`) |
| `controller/AdminRetentionController.java` | `POST /purge-batches/execute` |
| `config/SecurityConfig.java` | execute narrow matcher(PRIVACY_ADMIN) |

### Tests

| File | 건수 | 내용 |
|------|------|------|
| `ApplicationPiiPurgeServiceTest`(신규) | 1(대형) | **인벤토리 field-level 계약** — 전 섹션 fixture(학력/경력+profile/자격/어학/병역/수상/공백/answer/평가) 생성 후 PLACEHOLDER/NULLIFY/HASH_ONLY/KEEP_TOMBSTONE 필드 단위 검증(Interview-level 텍스트 불가침 포함) |
| `PurgeExecutionServiceTest`(신규) | 2 | **비-트랜잭션 통합**(REQUIRES_NEW commit 의미론 실검증 + JdbcTemplate 정리) — bulk(drift SKIPPED/STORED·**soft-DELETED** 첨부 PENDING/무첨부 PURGED/ref-count 익명화/ciHash sentinel/coarse 감사/멱등 재실행 ALREADY_PURGED) + 검증 4종/단건 모드 |
| `AdminRetentionControllerTest`(보강) | 14 | +execute RECRUIT 403 / PRIVACY confirm=false 400 |
| `AuditMetadataContractTest`(보강) | 3 | permits 10종 + `PurgeExecuteMetadata` allowlist |

## Class-by-Class Explanation (핵심)

### PurgeExecutionService — Service(무트랜잭션 orchestrator)
- 책임: 안전장치 검증 → 후보 확정(bulk=dry-run ELIGIBLE / 단건) → batch start → item 루프(실패 격리) → complete/fail.
- 실패 분류: item 예외 = FAILED item + PARTIAL_FAILED batch / 루프 자체 예외 = batch FAILED + 전파.

### PurgeItemProcessor — Service(REQUIRES_NEW)
- 책임: item 1건 — 재검증·tombstone·marker·ref-count 를 한 tx 로. `recordFailure` 는 별도 REQUIRES_NEW(실패 증적 잔존).

### ApplicationPiiPurgeRepository / ApplicationPiiPurgeService
- 책임: 인벤토리 §3~§7 의 유일한 구현체. 분류표 변경 시 인벤토리부터 갱신하는 계약.

### PurgeBatchLifecycleService — Service(REQUIRES_NEW ×3)
- 책임: batch 상태전이 + `PURGE_EXECUTE` coarse 감사(in-tx) + 상세 응답 조립.

## API

기존 9c API 불변. 신규 1건(위 §A). 응답 `PurgeBatchResponse` 에 purged/pending/failed 집계 추가(배치 목록/상세 공통).

## Validation and Business Rules

1. execute = PRIVACY_ADMIN + confirm=true + (bulk: DRY_RUN source 의 ELIGIBLE만 | 단건: applicationId) XOR.
2. 실행 시 eligibility 재검증 — dry-run 불신뢰, drift 는 SKIPPED 기록.
3. item = REQUIRES_NEW all-or-nothing / batch = 비원자 집계(PARTIAL_FAILED) / FAILED = 시작·orchestration 실패.
4. tombstone 은 인벤토리 분류표가 유일 계약(PLACEHOLDER `__PURGED__` / NULLIFY / HASH_ONLY HMAC / KEEP_TOMBSTONE 불가침 / Interview-level 공유 텍스트 불가침).
5. **PURGED = 관계형 제거 + 바이너리 소멸 확인** — STORED·DELETED(soft) 잔존 시 PURGE_PENDING, purgedAt 은 최종 PURGED 에만.
6. ref-count: 모든 지원서가 파기 대상일 때만 Applicant 익명화, ciHash 는 sentinel overwrite.
7. ALREADY_PURGED idempotent skip. ledger delete 금지.
8. 감사는 batch coarse 만(`PURGE_EXECUTE` + 집계 metadata).

## 운영(MariaDB) 수동 DDL

`docs/codex/ops/phase-09d-1-purge-execute-ddl.sql` — date PII 6컬럼 nullable 화 + purge_batch 집계 3컬럼. PLACEHOLDER 대상/ciHash overwrite 는 DDL 불요. 첨부 관련 DDL 은 9d-2.

## Test Commands

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*ApplicationPiiPurgeServiceTest" --tests "*PurgeExecutionServiceTest" --tests "*AdminRetentionControllerTest" --tests "*AuditMetadataContractTest" --tests "*RetentionDryRunServiceTest" --no-daemon
```

## Test Results

- 9d-1 scoped: **22 passed**(PiiPurge 1 · Execution 2 · RetentionController 14 · MetadataContract 3 · DryRun 2) — promotion guard 보강 후 재실행 포함 전부 통과.
- 영향 영역 회귀: **통과(BUILD SUCCESSFUL)** — 섹션 controller 5종(certificate/language/award/gapPeriod/career — nullable 완화 영향) + ApplicantSignUp/Account 스위트(Applicant 엔티티 변경) + JobApplicationServiceTest 36.
- **적대적 검증 워크플로(5 agents)**: safety-controls confirmed. 실반영 1건 — **PURGED 승격 가드에 soft-DELETED 포함**(물리삭제 실패 시 파일 잔존 가능 — 수정+테스트 보강). false positive 2건 반박(applicantNameSnapshot 은 markPurge* 가 처리·테스트 실측 / ref-count 는 동일 영속성 컨텍스트+id-fallback 으로 정확·테스트 실측). tx 의미론 지적들은 문서화(아래 한계).
- 전체 회귀 미실행(프로젝트 규칙).

## Known Limitations

1. PURGE_PENDING 상태의 첨부 PII(originalFileName/deletedBy/deletionReason 등 §6)는 9d-2 saga 에서 처리 — 그때까지 잔존(앱은 PURGED 미표기 상태이므로 계약 위반 아님).
2. item 처리 직렬 실행 전제 — 병렬화 시 ref-count race 재설계 필요(적대 검증 지적, 문서화).
3. 재실행 batch 는 동일 application 의 item 을 새로 기록(ledger 의미상 실행별 기록 — 중복 아님). FAILED 재시도 reconciliation 은 9e.
4. orchestration 루프 자체 실패를 batch FAILED 로 분류 — 설계 "시작/criteria 실패" 의 확장 해석(명시).
5. 파기 후 지원자 로그인 불가(loginId null) — 의도된 결과. 파기 통지는 후속(MessageBatch hook).
6. **인벤토리 갭 flag**: `StageResult.comment`(관리자 자유서술)·`StageResultCorrectionHistory` 의 comment 스냅샷이 인벤토리에 미분류 — 설계 §5.2 "자유입력 comment 소거" 원칙과 대조 필요. **인벤토리 갱신 후 9d-2/9e 에서 처리 권고**(임의 확장하지 않고 flag).

## Next Phase Considerations

- **09d-2 — Attachment binary delete saga**: `PhysicalFileStatus` 확장(+`SOFT_DELETED` 1단계 병행)·첨부 §6 PII 처리·물리삭제(멱등)·소멸 확인 후 PENDING→**PURGED 최종 승격**(JobApplication.purgedAt 세팅)·`filenameHash`/`binaryDeletedAt` 컬럼.
- 09d-2 에서 본 슬라이스의 `countAttachmentsWithStatus` outstanding 집합에 `BINARY_DELETE_PENDING`/`BINARY_DELETE_FAILED` 추가.
- 인벤토리에 StageResult comment 계열 분류 추가(위 한계 6).
