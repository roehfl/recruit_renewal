# Phase 09e — Reconciliation + 안정화 (Phase 09 마지막 슬라이스)

## Phase Summary

- Date: 2026-06-08
- Work type: implementation (설계: `phase-09-privacy-purge-audit-retention-design.md` §6 reconciliation·§6.1 storage-health-scan 상태별 정책, 계약: PII 인벤토리, ADR-0005/0006/0007)
- Goal: 파기 lifecycle 의 마지막 안전망 — ① **reconciliation sweep**(PURGE_PENDING 잔여 건 바이너리 삭제 재처리 → 최종 PURGED 승격, execute 재실행은 ALREADY_PURGED skip 이라 **유일한 재처리 경로**), ② **storage-health-scan §6.1 상태별 정책 확장**("DB PURGED + 파일 잔존" 치명탐지 + BINARY_DELETED null path 정상 분류 + BINARY_DELETE_PENDING/FAILED retry 가시화), ③ **Low 2 — row 수준 실패 사유**(reconciliation 추적), ④ 권한/멱등/Clock 회귀 하드닝.

## Implemented Scope

### A — Reconciliation sweep (action, ROLE_PRIVACY_ADMIN)

| Method | Path | 권한 |
| --- | --- | --- |
| POST | `/admin/retention/purge-batches/reconcile` | **ROLE_PRIVACY_ADMIN**(narrow matcher, broad 보다 먼저) |

- `PurgeReconciliationService`(무트랜잭션 orchestrator): `findByPurgeResult(PURGE_PENDING)` → 각 건의 원 `purgeBatchId` 로 9d-2 `AttachmentPurgeSagaService.completeBinaryDeletion(batchId, applicationId)` 재호출 → 전부 소멸 확인되면 최종 PURGED 승격(saga ③ 의 `promoteToPurged`), 실패 시 PENDING 유지(다음 sweep 재시도).
- 단건 실패는 try/catch 로 격리(sweep 전체를 막지 않음). **null batchId** 는 saga 호출 전 error 로 격리(데이터 불일치 방어, 적대 검증 ①-A/⑥).
- coarse 감사 `PURGE_RECONCILE` + `PurgeReconcileMetadata`(scanned/promoted/stillPending/errors) — committed 변경(per-app saga REQUIRES_NEW)은 이미 커밋됐으므로 summary 는 `recordRequiresNew` 로 독립 기록. `stillPending>0||errors>0` → FAILURE.
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

- 치명탐지 메커니즘: orphan 후보 파일의 key(`applications/{id}/...`)에서 applicationId 파싱 → `findIdsByIdInAndPurgeResult(ids, PURGED)` 일괄 조회 → PURGED 집합이면 치명 분류. 이슈는 **fileKeyHash 만 노출**(경로/파일명 원문 미노출).
- 응답에 `pendingBinaryDeleteRowCount`·`purgedPhysicalFilePresentCount` 추가.

### C — Low 2: row 수준 바이너리 삭제 실패 사유

- `ApplicationAttachment.binaryDeleteFailureCode`(sanitized, len 100, nullable) 신규. `markBinaryDeleteFailed(failureCode)` 가 세팅, `markBinaryDeleted` 가 성공 시 null 로 해소.
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
| `service/AttachmentStorageHealthScanService.java` | §6.1 — pending 집계 + PURGED 치명 분류(JobApplicationRepository 주입) |
| `dto/response/AttachmentStorageHealthScanResponse.java` | +2 카운트 |
| `service/AuditMetadata.java` | permits +`PurgeReconcileMetadata` |
| `controller/AdminRetentionController.java` / `config/SecurityConfig.java` | reconcile 엔드포인트 + PRIVACY narrow matcher |

### Tests

| File | 건수 | 내용 |
|------|------|------|
| `PurgeReconciliationServiceTest`(신규) | 3 | 비-tx 통합 — PURGE_PENDING+BINARY_DELETE_FAILED → 재처리 → **최종 PURGED 승격**(item PURGED·purgedAt·BINARY_DELETED·storagePath null·실패코드 해소·SUCCESS 감사) + 멱등(2회차 무대상) / 빈 storagePath = stillPending 유지·`EMPTY_STORAGE_PATH`·FAILURE 감사 / actor 부재 거부 |
| `AttachmentStorageHealthScanServiceTest`(보강) | 7 | +PURGED 지원서 파일 = `PURGED_PHYSICAL_FILE_PRESENT`(치명, orphan 아님) / BINARY_DELETED+null = 무이슈(Low 1) |
| `PurgeExecutionServiceTest`(보강) | 2 | 실패 첨부 `binaryDeleteFailureCode == INVALID_STORAGE_PATH`(Low 2) |
| `AdminRetentionControllerTest`(보강) | 15 | reconcile RECRUIT 403 / PRIVACY 200(무대상 scanned 0) |
| `AuditMetadataContractTest`(보강) | 3 | permits 11종 + `PurgeReconcileMetadata` allowlist |

## Validation and Business Rules

1. reconcile = PRIVACY_ADMIN + actor 필수. PURGE_PENDING 만 대상, 원 batchId 로 saga 재구동.
2. PURGED 승격은 saga ③ allCleared(전부 소멸 확인)에서만 — reconcile 도 동일 불변식 승계("DB PURGED + 파일 잔존" 불허). 빈 path/STILL_EXISTS/예외 = 실패코드 → PENDING 유지.
3. 멱등: 승격 후 PURGE_PENDING 목록에서 빠짐 → 재sweep 무대상. 실패 건은 다음 sweep 재시도.
4. health-scan §6.1: BINARY_DELETED+null=정상, PENDING/FAILED=retry(issue 아님), PURGED+파일존재=치명. 치명/orphan 이슈는 fileKeyHash 만(PII 미노출).
5. 실패코드는 sanitized(경로/파일명 원문 미포함) — attachment row 에 영속(queryable) + 감사는 coarse 집계.
6. ledger delete 금지(PurgeJobItem mutable 전이만). 감사는 batch/sweep coarse(item 중복 기록 금지).

## 운영(MariaDB) 수동 DDL

`docs/codex/ops/phase-09e-reconciliation-ddl.sql` — `application_attachment.binary_delete_failure_code` 1컬럼. 신규 테이블 없음. enum(PURGE_RECONCILE/PURGED_PHYSICAL_FILE_PRESENT)은 VARCHAR 값이라 DDL 불요.

## Test Commands

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*PurgeReconciliationServiceTest" --tests "*PurgeExecutionServiceTest" --tests "*AttachmentStorageHealthScanServiceTest" --tests "*AdminRetentionControllerTest" --tests "*AuditMetadataContractTest" --tests "*ApplicationAttachmentDeleteServiceTest" --no-daemon
```

## Test Results

- scoped: **42 passed**(Reconciliation 3 · Execution 2 · HealthScan 7 · RetentionController 15 · MetadataContract 3 · AttachmentDelete 12). null-guard 보강 후 재실행 통과. 전체 회귀 미실행(프로젝트 규칙).
- **적대적 검증(3-agent 워크플로)**: health-scan 치명탐지·Low 2/회귀 두 영역 **confirmed(clean)**. reconcile 영역 지적 다수는 ① false positive(null batchId 여도 saga 는 applicationId 기준 조회 + item 부재 시 orElseThrow 롤백으로 오승격 차단 — empty-target 승격은 "바이너리 실제 소멸"이라 의도된 복구), ② 동시성(reconcile↔execute race·중복 sweep) = **직렬 실행 전제로 범위 외**(문서화). 실반영 1건 — **null batchId 조기 가드**(보장된 롤백 churn 제거 + 명시적 error 격리).

## Known Limitations

1. reconcile/execute/saga 는 **직렬 실행 전제**(9d 승계). 병렬·동시 sweep 시 lost-update/중복 승격 가능 — 운영상 동시 실행 금지 권고. 낙관적 lock(@Version) 도입은 후속 과제.
2. reconcile 감사는 coarse 집계만 — per-attachment 실패 사유는 attachment row(`binaryDeleteFailureCode`)에 영속되어 queryable(감사에는 미중복). 9f+ 에서 실패 상세 read API 검토 가능.
3. 자동 스케줄러 없음 — 수동 트리거. 미래 스케줄러는 SYSTEM actor 정책 필요.
4. legacy `DELETED` enum 잔존(2단계 UPDATE 후 3단계 제거 — 후속 phase).

## Next Phase Considerations

- **Phase 09 완료**. 후속(09f/별도 phase): legacy `DELETED` enum 제거(3단계), reconcile 자동 스케줄러(SYSTEM actor), 동시성 제어(@Version), 실패 상세 운영 대시보드.
- 운영 절차 문서화: reconcile 운영 가이드(언제/얼마나 자주 수동 실행), health-scan 치명탐지 알람 연동.
