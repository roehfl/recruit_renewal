# Phase 09e — Reconciliation + 안정화 (Phase 09 마지막 슬라이스)

## Phase Summary

- Date: 2026-06-08
- Work type: implementation (설계: `phase-09-privacy-purge-audit-retention-design.md` §6 reconciliation·§6.1 storage-health-scan 상태별 정책, 계약: PII 인벤토리, ADR-0005/0006/0007)
- Goal: 파기 lifecycle 의 마지막 안전망 — ① **reconciliation sweep**(PURGE_PENDING 잔여 건 바이너리 삭제 재처리 → 최종 PURGED 승격, execute 재실행은 ALREADY_PURGED skip 이라 **유일한 재처리 경로**), ② **storage-health-scan §6.1 상태별 정책 확장**("DB PURGED + 파일 잔존" 치명탐지 + BINARY_DELETED null path 정상 분류 + BINARY_DELETE_PENDING/FAILED retry 가시화), ③ **Low 2 — row 수준 실패 사유**(reconciliation 추적), ④ 권한/멱등/Clock 회귀 하드닝.

> 2026-06-08 구현 리뷰 1차 반영(Medium 1·2 + Low 1): ① **Medium 1 — chunk 처리**: reconcile 이 전체 PURGE_PENDING 을 한 번에 조회하던 것을 `limit`(기본 100, max 1000 clamp) + `findByPurgeResultOrderByIdAsc(..., PageRequest)` 로 chunk 처리 — 장애 후 PENDING 누적 시 단일 요청 폭주 방지. `scannedCount == limit` 이면 운영자가 재호출(잔여 sweep). ② **Medium 2 — STARTED 선행 감사(방안 A)**: sweep 시작 시 `PURGE_RECONCILE` STARTED 감사를 먼저 남기고 완료 시 SUMMARY 감사 — summary 저장이 실패해도 'STARTED 만 있고 SUMMARY 없음' 으로 미완 sweep 을 감사 read 에서 탐지 가능(9d batch complete audit 문제와 동형). ③ **Low 1 — 실패코드 sanitize 강제**: `ApplicationAttachment.markBinaryDeleteFailed` 가 엔티티 경계에서 `[A-Z0-9_]` 외 치환 + len100 절단 — 미래 S3/NAS 구현이 긴 메시지·경로를 넘겨도 컬럼 초과/정보 노출 차단.
>
> 2026-06-08 구현 리뷰 2차 반영(**Major — 치명탐지 누락 보정** + 문서 Low): **Major** — `PURGED_PHYSICAL_FILE_PRESENT` 분류가 orphan 후보로만 제한돼 "PURGED 지원서 + STORED(또는 DELETED/MISSING) row + 실파일 잔존" 케이스를 놓쳤다(STORED row 의 파일은 orphan 후보에서 제외되므로 무이슈 통과). 수정: 치명탐지를 **전체 물리파일 기준 선행 분기**로 분리 — `scanDryRun` 이 모든 물리파일의 applicationId 로 `purgedApplicationIds`/`purgedFileKeys` 를 먼저 구해 `addPurgedFilePresentIssues` 로 분류하고, `addDeletedRemainingIssues`/`addOrphanIssues` 는 purgedFileKeys 를 skip(중복 방지·상위 심각도). row 상태 무관하게 PURGED 경로 파일은 치명 1회로 분류. 적대 검증(9 케이스 전수) confirmed — 중복/누락/회귀 없음. 회귀 테스트 추가(STORED row + 실파일 + PURGED → `purgedPhysicalFilePresentCount==1`, orphan 아님). 문서 Low — Major 보정 후 "Phase 09 종료" 유지.

## Implemented Scope

### A — Reconciliation sweep (action, ROLE_PRIVACY_ADMIN)

| Method | Path | 권한 |
| --- | --- | --- |
| POST | `/admin/retention/purge-batches/reconcile?limit=100` | **ROLE_PRIVACY_ADMIN**(narrow matcher, broad 보다 먼저) |

- `PurgeReconciliationService`(무트랜잭션 orchestrator): `findByPurgeResultOrderByIdAsc(PURGE_PENDING, PageRequest.of(0, limit))` → 각 건의 원 `purgeBatchId` 로 9d-2 `AttachmentPurgeSagaService.completeBinaryDeletion(batchId, applicationId)` 재호출 → 전부 소멸 확인되면 최종 PURGED 승격(saga ③ 의 `promoteToPurged`), 실패 시 PENDING 유지(다음 sweep 재시도).
- **chunk 처리(리뷰 Medium 1)**: `limit`(기본 100, 1..1000 clamp)로 단일 요청 처리량 상한. `scannedCount == limit` = 잔여 가능 → 재호출.
- 단건 실패는 try/catch 로 격리(sweep 전체를 막지 않음). **null batchId** 는 saga 호출 전 error 로 격리(데이터 불일치 방어, 적대 검증 ①-A/⑥).
- coarse 감사 **2-phase(리뷰 Medium 2)**: 시작 시 `PURGE_RECONCILE` **STARTED**(no metadata, reasonMessage="reconcile started: scanned=N", SUCCESS) → 완료 시 **SUMMARY**(`PurgeReconcileMetadata` scanned/promoted/stillPending/errors, `stillPending>0||errors>0`→FAILURE). 둘 다 `recordRequiresNew`. STARTED 만 있고 SUMMARY 없음 = 미완 sweep 탐지 신호.
- 응답 `PurgeReconcileResponse`(PII-free 집계 + reconciledAt).

### B — storage-health-scan §6.1 상태별 정책 (detection, read-only)

설계 §6.1 표를 구현. 신규 issue type `PURGED_PHYSICAL_FILE_PRESENT`(치명).

| physicalFileStatus | scan 처리(09e) |
| --- | --- |
| `STORED` | 파일 없으면 `STORED_MISSING_PHYSICAL_FILE` |
| `SOFT_DELETED`(+legacy `DELETED`) | 파일 있으면 `DELETED_PHYSICAL_FILE_REMAINING`(동일 취급) |
| `MISSING` | 파일 있으면 `MISSING_ROW_PHYSICAL_FILE_PRESENT` |
| `BINARY_DELETE_PENDING`/`FAILED` | **retry 대상** — issue 아님, orphan 오탐 방지(deferred key) + `pendingBinaryDeleteRowCount` 집계 |
| `BINARY_DELETED` | storagePath null → 스캔 대상 외(**오탐 금지**, Low 1) |
| orphan 파일(어떤 row 도 매칭 안 됨) | applicationId 파싱 → **PURGED 지원서면 `PURGED_PHYSICAL_FILE_PRESENT`(치명)**, 아니면 `ORPHAN_PHYSICAL_FILE` |

- 치명탐지 메커니즘: **전체 물리파일**(orphan 후보 한정 아님 — 리뷰 2차 Major)의 key(`applications/{id}/...`)에서 applicationId 파싱 → `findIdsByIdInAndPurgeResult(ids, PURGED)` 일괄 조회 → PURGED 집합이면 `addPurgedFilePresentIssues` 가 선행 분류, 그 키는 deleted-remaining/orphan 분기에서 skip(중복 방지). row 상태(STORED/DELETED/MISSING/orphan) 무관. 이슈는 **fileKeyHash 만 노출**(경로/파일명 원문 미노출).
- 응답에 `pendingBinaryDeleteRowCount`·`purgedPhysicalFilePresentCount` 추가.

### C — Low 2: row 수준 바이너리 삭제 실패 사유

- `ApplicationAttachment.binaryDeleteFailureCode`(sanitized, len 100, nullable) 신규. `markBinaryDeleteFailed(failureCode)` 가 세팅(**엔티티 경계에서 sanitize 강제 — 리뷰 Low 1**: `[A-Z0-9_]` 외 치환 + len100 절단, null/blank→`UNKNOWN`), `markBinaryDeleted` 가 성공 시 null 로 해소.
- saga `deletePhysicalFile` 는 boolean → **실패코드 반환**(null=성공): `EMPTY_STORAGE_PATH`/`STILL_EXISTS`/`INVALID_STORAGE_PATH`/`DELETE_FAILED`/`EXCEPTION`. `finalizeBinaryDeletion` 시그니처 `Set<Long> failed` → `Map<Long,String> failedAttachmentCodes`(미상 fallback `UNKNOWN`). 전부 PII-free.

### D — 하드닝 회귀

- reconcile 권한 매트릭스(RECRUIT 403 / PRIVACY 200), 멱등(2회차 무대상), actor 부재 거부, FAILURE 감사(빈 storagePath).
- health-scan 치명탐지/Low 1 정상분류 테스트. AuditMetadataContractTest 에 `PurgeReconcileMetadata` 추가(Map.ofEntries 전환 — Map.of 10쌍 한계).

## Not Implemented / Out of Scope

- 자동 스케줄러(주기 reconcile) — 수동 PRIVACY_ADMIN 트리거만. 미래 스케줄러는 SYSTEM actor 정책 별도(9a 이후 문서화된 follow-up).
- 동시 실행(병렬 reconcile, reconcile↔execute race) — **직렬 실행 전제**(9d 와 동일 승계). 낙관적 lock/버전은 후속 과제(아래 한계).
- legacy `DELETED` enum 제거(3단계 마이그레이션) — 후속 phase.
- forced purge / data-subject-request 트리거 — 설계 슬롯만.

## Changed Files

### New Files (main 4)

| File | Type |
|------|------|
| `service/PurgeReconciliationService.java` | Service(무트랜잭션 sweep orchestrator) |
| `service/PurgeReconcileMetadata.java` | AuditMetadata record |
| `dto/response/PurgeReconcileResponse.java` | Response DTO |
| `docs/codex/ops/phase-09e-reconciliation-ddl.sql` | 운영 DDL |

### Modified Files (main 9)

| File | Change |
|------|--------|
| `enumeration/AuditActionType.java` | +`PURGE_RECONCILE` |
| `enumeration/AttachmentStorageHealthIssueType.java` | +`PURGED_PHYSICAL_FILE_PRESENT` |
| `domain/entity/ApplicationAttachment.java` | `binaryDeleteFailureCode` 컬럼 + `markBinaryDeleteFailed(code)`/`markBinaryDeleted` 해소 |
| `domain/repository/JobApplicationRepository.java` | `findByPurgeResult` + `findIdsByIdInAndPurgeResult` |
| `service/AttachmentPurgeSagaService.java` | `deletePhysicalFile` 실패코드 반환 + failed `Map` 전파 |
| `service/PurgeItemProcessor.java` | `finalizeBinaryDeletion` 시그니처 `Map<Long,String>` |
| `service/AttachmentStorageHealthScanService.java` | §6.1 — pending 집계 + **PURGED 치명 분류(전체 물리파일 선행 분기, row 상태 무관 — 리뷰 2차 Major)**, JobApplicationRepository 주입 |
| `dto/response/AttachmentStorageHealthScanResponse.java` | +2 카운트 |
| `service/AuditMetadata.java` | permits +`PurgeReconcileMetadata` |
| `controller/AdminRetentionController.java` / `config/SecurityConfig.java` | reconcile 엔드포인트 + PRIVACY narrow matcher |

### Tests

| File | 건수 | 내용 |
|------|------|------|
| `PurgeReconciliationServiceTest`(신규) | 4 | 비-tx 통합 — PURGE_PENDING+BINARY_DELETE_FAILED → 재처리 → **최종 PURGED 승격**(item PURGED·purgedAt·BINARY_DELETED·storagePath null·실패코드 해소·STARTED+SUMMARY 2감사) + 멱등(2회차 무대상) / 빈 storagePath = stillPending 유지·`EMPTY_STORAGE_PATH`·FAILURE summary / actor 부재 거부 / **limit chunk 처리(리뷰 Medium 1)** |
| `AttachmentStorageHealthScanServiceTest`(보강) | 8 | +PURGED+orphan 파일 = 치명 / **PURGED+STORED row+실파일 = 치명(리뷰 2차 Major — orphan 아님·STORED_MISSING 아님)** / BINARY_DELETED+null = 무이슈(Low 1) |
| `PurgeExecutionServiceTest`(보강) | 2 | 실패 첨부 `binaryDeleteFailureCode == INVALID_STORAGE_PATH`(Low 2) |
| `AdminRetentionControllerTest`(보강) | 15 | reconcile RECRUIT 403 / PRIVACY 200(무대상 scanned 0) |
| `AuditMetadataContractTest`(보강) | 3 | permits 11종 + `PurgeReconcileMetadata` allowlist |

## Validation and Business Rules

1. reconcile = PRIVACY_ADMIN + actor 필수. PURGE_PENDING 만 대상, 원 batchId 로 saga 재구동.
2. PURGED 승격은 saga ③ allCleared(전부 소멸 확인)에서만 — reconcile 도 동일 불변식 승계("DB PURGED + 파일 잔존" 불허). 빈 path/STILL_EXISTS/예외 = 실패코드 → PENDING 유지.
3. 멱등: 승격 후 PURGE_PENDING 목록에서 빠짐 → 재sweep 무대상. 실패 건은 다음 sweep 재시도.
4. health-scan §6.1: BINARY_DELETED+null=정상, PENDING/FAILED=retry(issue 아님), **PURGED 경로 파일존재=치명(row 상태 STORED/DELETED/MISSING/orphan 무관, 전체 물리파일 기준 선행 분류, 중복 없음)**. 치명/orphan 이슈는 fileKeyHash 만(PII 미노출).
5. 실패코드는 sanitized(경로/파일명 원문 미포함) — attachment row 에 영속(queryable) + 감사는 coarse 집계.
6. ledger delete 금지(PurgeJobItem mutable 전이만). 감사는 batch/sweep coarse(item 중복 기록 금지).

## 운영(MariaDB) 수동 DDL

`docs/codex/ops/phase-09e-reconciliation-ddl.sql` — `application_attachment.binary_delete_failure_code` 1컬럼. 신규 테이블 없음. enum(PURGE_RECONCILE/PURGED_PHYSICAL_FILE_PRESENT)은 VARCHAR 값이라 DDL 불요.

## Test Commands

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*PurgeReconciliationServiceTest" --tests "*PurgeExecutionServiceTest" --tests "*AttachmentStorageHealthScanServiceTest" --tests "*AdminRetentionControllerTest" --tests "*AuditMetadataContractTest" --tests "*ApplicationAttachmentDeleteServiceTest" --no-daemon
```

## Test Results

- scoped: 1차 **42 passed**, 리뷰 1차(Medium/Low) **28 passed**, 리뷰 2차(Major) **HealthScan 8 passed**(Reconciliation 4 — chunk 포함 · HealthScan **8** — PURGED+STORED 치명 회귀 포함 · Execution 2 · RetentionController 15). 전체 회귀 미실행(프로젝트 규칙).
- **적대적 검증**: (3-agent) health-scan 치명탐지·Low 2/회귀 **confirmed(clean)**, reconcile 지적은 false positive(null batchId 도 applicationId 기준 조회 + orElseThrow 롤백으로 오승격 차단) 또는 동시성(직렬 전제 범위 외) — 실반영 **null batchId 조기 가드**. (리뷰 2차 1-agent 전수) 치명탐지 보정 **9 케이스 전수 confirmed** — STORED/DELETED/MISSING/orphan × PURGED 모두 치명 1회, 중복·누락·회귀 없음.

## Known Limitations

1. reconcile/execute/saga 는 **직렬 실행 전제**(9d 승계). 병렬·동시 sweep 시 lost-update/중복 승격 가능 — 운영상 동시 실행 금지 권고. 낙관적 lock(@Version) 도입은 후속 과제.
2. reconcile 감사는 coarse 집계만(STARTED+SUMMARY 2-phase) — per-attachment 실패 사유는 attachment row(`binaryDeleteFailureCode`, sanitized)에 영속되어 queryable(감사에는 미중복). 9f+ 에서 실패 상세 read API 검토 가능.
3. chunk 처리: `limit` 초과 잔여는 응답 `scannedCount == limit` 로 추정해 재호출(별도 hasMore 플래그 미도입 — DTO 최소 유지). 자동 반복 호출은 스케줄러(후속) 몫.
3. 자동 스케줄러 없음 — 수동 트리거. 미래 스케줄러는 SYSTEM actor 정책 필요.
4. legacy `DELETED` enum 잔존(2단계 UPDATE 후 3단계 제거 — 후속 phase).

## Next Phase Considerations

- **Phase 09 완료**. 후속(09f/별도 phase): legacy `DELETED` enum 제거(3단계), reconcile 자동 스케줄러(SYSTEM actor), 동시성 제어(@Version), 실패 상세 운영 대시보드.
- 운영 절차 문서화: reconcile 운영 가이드(언제/얼마나 자주 수동 실행), health-scan 치명탐지 알람 연동.
