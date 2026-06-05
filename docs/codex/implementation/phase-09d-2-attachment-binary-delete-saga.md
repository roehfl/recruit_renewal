# Phase 09d-2 — Attachment Binary Delete Saga (물리 소멸 확인 + 최종 PURGED 승격)

## Phase Summary

- Date: 2026-06-05
- Work type: implementation (설계: `phase-09-privacy-purge-audit-retention-design.md` §5.4 첨부 saga·slice 9d-2, 계약: PII 인벤토리 §6/§8, ADR-0005)
- Goal: 파기의 마지막 조각 — 첨부 **바이너리 물리 삭제 saga(①②③)** 를 execute 흐름에 통합하고, **소멸 확인 후에만** `JobApplication`/`PurgeJobItem` 을 최종 PURGED 로 승격한다. `PhysicalFileStatus` 재정의 1단계 마이그레이션 포함.

## Implemented Scope

### A — PhysicalFileStatus 재정의 + 1단계 안전 마이그레이션 (인벤토리 §8)

- enum 확장: `SOFT_DELETED`(기존 DELETED 개명분) + `BINARY_DELETE_PENDING`/`BINARY_DELETED`/`BINARY_DELETE_FAILED`. **legacy `DELETED` 는 1단계 동안 유지·동일 취급**(3단계에서 제거).
- `markDeleted()` → **SOFT_DELETED** 기록(신규 soft-delete).
- 동일 취급 적용: 활성 첨부 판정(`ApplicationAttachmentDeleteService`)·지원자/관리자 목록(`ApplicationAttachmentService`/`AdminApplicationSectionService`)은 `HIDDEN_FROM_LISTING`(DELETED·SOFT_DELETED·BINARY_* 전부 제외)으로, health scan 은 DELETED·SOFT_DELETED 를 같은 soft-deleted 로 집계. **BINARY_* 는 scan 대상 외**(BINARY_DELETED 의 storagePath null 오탐 방지 — 본격 확장은 9e).
- enum 에 의미 집합 상수: `SOFT_DELETED_FAMILY` / `HIDDEN_FROM_LISTING` / `BINARY_OUTSTANDING`(STORED·DELETED·SOFT_DELETED·BINARY_DELETE_PENDING·BINARY_DELETE_FAILED — PURGED 승격 금지 집합).

### B — saga ① (item 트랜잭션 내, `PurgeItemProcessor.process`)

- 첨부 **metadata PII 제거**(인벤토리 §6): `filenameHash = HMAC("FILE_NAME:"+원문)` 을 **원문 제거 전에** 계산 → `originalFileName = "__PURGED__"`, `deletedBy/deletionReason = null`. `createdBy/updatedBy` 는 bulk(`purgeAttachmentAuditFields`). **모든 첨부 행**(MISSING/METADATA_ONLY 포함)에 적용.
- 바이너리 소멸 미확인 첨부(`BINARY_OUTSTANDING`)는 `markBinaryDeletePending()` — 이 마킹·관계형 tombstone·PURGE_PENDING marker 가 한 item tx 로 커밋(설계 saga ①).

### C — saga ②③ (`AttachmentPurgeSagaService` + `PurgeItemProcessor.finalizeBinaryDeletion`)

- **②(트랜잭션 밖)**: `deleteIfExistsWithResult`(멱등 — 이미 없음 = MISSING_AS_SUCCESS) + **`exists()` 존재 재확인**(설계 — 삭제 성공이어도 잔존하면 실패 취급). 경로 invalid/예외 = 실패.
- **③(REQUIRES_NEW)**: 성공 첨부 `markBinaryDeleted(binaryDeletedAt)` — **storagePath 는 이 시점에 null**(인벤토리 §6). 실패 첨부 `markBinaryDeleteFailed()`(storagePath 보존 — 재시도용). **모든 대상 소멸 확인 시에만** `JobApplication.markPurged(purgedAt)` + `PurgeJobItem.promoteToPurged()`(PENDING 전제 검증). 보수적 판정 — 실패 1건 또는 성공 미확인 대상 잔존 시 승격 금지.
- crash 안전성: ②와 ③ 사이 중단 = "파일 소멸 + DB pending" — 설계상 안전(재실행/9e reconciliation 이 PURGED 승격). **"DB PURGED + 파일 잔존" 경로 없음**(승격은 소멸 확인 후에만).

### D — execute 흐름 통합 + 집계

- `PurgeExecutionService` 루프: item PENDING 커밋 후 saga ②③ 실행 — 승격 성공 = purged 집계, 실패 = pending 유지 + `binaryDeleteFailedCount` 집계(saga 자체 예외도 실패로 흡수 — 9e 대상).
- `PurgeBatch`: `binary_delete_failed_count` 컬럼 + `completeExecute` 가 **item 실패 또는 바이너리 삭제 실패 시 PARTIAL_FAILED**(설계 "실패 시 PARTIAL_FAILED/BINARY_DELETE_FAILED 유지"). `PurgeExecuteMetadata`/`PurgeBatchResponse` 에 동일 필드.

## Not Implemented / Out of Scope

- **09e**: reconciliation sweep(BINARY_DELETE_PENDING/FAILED 재처리, "PURGED 인데 파일 존재" 치명 탐지 — storage-health-scan §6.1 표 본격 확장), FAILED item 재시도, reasonMessage 컬럼.
- 2단계 데이터 마이그레이션(`UPDATE DELETED→SOFT_DELETED`)은 **운영 절차**(1단계 코드 배포 후 별도 시점) — DDL 스크립트에 주석으로 고정. 3단계(enum 제거)는 후속 phase.

## Changed Files

### New Files (main 1)

| File | Type |
|------|------|
| `service/AttachmentPurgeSagaService.java` | Service(saga ②③ orchestration — 무트랜잭션) |

### Modified Files (main 12)

| File | Change |
|------|--------|
| `enumeration/PhysicalFileStatus.java` | +4 상태(1단계 — DELETED 유지) + 의미 집합 상수 3종 |
| `domain/entity/ApplicationAttachment.java` | storagePath nullable·`filenameHash`/`binaryDeletedAt` 컬럼·`markDeleted→SOFT_DELETED`·saga 메서드 4종(purgeMetadataPii/markBinaryDeletePending/markBinaryDeleted/markBinaryDeleteFailed) |
| `domain/entity/PurgeBatch.java` | `binaryDeleteFailedCount` 컬럼 + `completeExecute` 시그니처/PARTIAL_FAILED 판정 확장 |
| `domain/entity/PurgeJobItem.java` | `promoteToPurged()`(PENDING 전제 전이) |
| `domain/repository/ApplicationAttachmentRepository.java` | NotIn 목록/단건 finder + In finder(saga ③ 로드) |
| `domain/repository/PurgeJobItemRepository.java` | `findByPurgeBatchIdAndApplicationId` |
| `domain/repository/ApplicationPiiPurgeRepository.java` | `purgeAttachmentAuditFields` + `findBinaryDeleteTargets`(scalar) |
| `service/PurgeItemProcessor.java` | saga ① 통합(첨부 PII+PENDING 마킹) + `finalizeBinaryDeletion`(③ REQUIRES_NEW) |
| `service/PurgeExecutionService.java` | saga ②③ 호출 + binaryDeleteFailed 집계(+예외 흡수) |
| `service/PurgeBatchLifecycleService.java` / `PurgeExecuteMetadata.java` / `dto/response/PurgeBatchResponse.java` | binaryDeleteFailedCount 전파 |
| `service/ApplicationAttachmentDeleteService.java` / `ApplicationAttachmentService.java` / `AdminApplicationSectionService.java` | HIDDEN_FROM_LISTING 전환(1단계 호환) |
| `service/AttachmentStorageHealthScanService.java` | DELETED·SOFT_DELETED 동일 집계(1단계) |

### Tests

| File | 건수 | 내용 |
|------|------|------|
| `PurgeExecutionServiceTest`(갱신) | 2 | **saga 완주 통합** — STORED(B)/soft-deleted(D) 첨부는 소멸 확인(MISSING_AS_SUCCESS) 후 **최종 PURGED 승격**(item PENDING→PURGED·purgedAt·BINARY_DELETED·storagePath null·binaryDeletedAt·filenameHash HMAC·originalFileName placeholder), **삭제 실패(E — traversal 경로 invalid)** 는 PENDING 유지+BINARY_DELETE_FAILED(storagePath 보존)+batch **PARTIAL_FAILED**+binaryDeleteFailedCount=1+coarse 감사 검증, drift SKIP·ref-count·멱등 재실행 유지 |
| `ApplicationAttachmentDeleteServiceTest`(갱신) | 12 | soft-delete 가 **SOFT_DELETED** 를 기록(1단계) — 단언 5곳 전환 |
| `AuditMetadataContractTest`(갱신) | 3 | `PurgeExecuteMetadata` +binaryDeleteFailedCount |
| 회귀 | — | health scan 4 · attachment controller 9 · storage health controller 3 · retention controller 14 · PiiPurge 1 |

## Class-by-Class Explanation (핵심)

### AttachmentPurgeSagaService — Service(무트랜잭션)
- 책임: saga ②(멱등 물리 삭제 + 존재 재확인 — 실패/예외/잔존 = 실패) 후 ③ 위임. 대상 로드는 scalar(엔티티 비로딩).

### PurgeItemProcessor.finalizeBinaryDeletion — REQUIRES_NEW
- 책임: 삭제 결과 확정 + 조건부 최종 승격. 보수적 allCleared(실패 1건/미확인 대상 = 승격 금지).

### ApplicationAttachment saga 메서드 — Entity
- `purgeMetadataPii`(①)·`markBinaryDeletePending`(①)·`markBinaryDeleted`(③ — storagePath null 은 여기서만)·`markBinaryDeleteFailed`(③).

## Validation and Business Rules

1. PURGED = 관계형 PII 제거 **AND** 바이너리 소멸 확인(존재 재확인 포함) — 승격은 saga ③ 의 allCleared 에서만, purgedAt/storagePath-null 도 그 시점에만.
2. 삭제 실패 = attachment BINARY_DELETE_FAILED(경로 보존) + item PENDING 유지 + batch PARTIAL_FAILED — 9e reconciliation 대상.
3. 멱등: 이미 없는 파일 = 성공(MISSING_AS_SUCCESS). 재실행 batch 는 ALREADY_PURGED skip(9d-1 그대로 — PURGE_PENDING 재처리는 9e).
4. 1단계 마이그레이션: DELETED·SOFT_DELETED 동일 취급(전 사용처), 신규 기록은 SOFT_DELETED, 2단계 UPDATE 는 운영 별도 시점.
5. 첨부 PII(§6)는 saga ① 에서 **모든 첨부 행**에 제거 — filenameHash 는 원문 제거 전 HMAC.

## 운영(MariaDB) 수동 DDL

`docs/codex/ops/phase-09d-2-attachment-saga-ddl.sql` — storage_path nullable·filename_hash·binary_deleted_at·purge_batch.binary_delete_failed_count + **2단계 UPDATE 스크립트(별도 시점, 주석 고정)**.

## Test Commands

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*PurgeExecutionServiceTest" --tests "*ApplicationPiiPurgeServiceTest" --tests "*ApplicationAttachmentDeleteServiceTest" --tests "*AttachmentStorageHealthScan*" --tests "*ApplicationAttachmentControllerTest" --tests "*AdminAttachmentStorageHealthControllerTest" --tests "*AdminRetentionControllerTest" --tests "*AuditMetadataContractTest" --no-daemon
```

## Test Results

- scoped: **48 passed**(PurgeExecution 2 — saga 완주/실패/PARTIAL_FAILED 통합 · AttachmentDelete 12 — SOFT_DELETED 전환 · health scan 4+3 · attachment controller 9 · retention controller 14 · PiiPurge 1 · contract 3) + fail-loud 수정 후 재실행 통과. 전체 회귀 미실행(프로젝트 규칙).
- **적대적 검증(3-agent 워크플로)**: 첨부 PII §6 커버리지 **confirmed**. REAL 1건 반영 — `promoteToPurged` 의 `ifPresent` silent skip 을 `orElseThrow` fail-loud 로 교체(item 부재 = ledger 불변식 위반 → tx 전체 롤백으로 마커-원장 정합 유지). false positive 반박: allCleared 불일치 주장(미확인 대상은 else 분기에서 이미 `allCleared=false` — 코드 오독), 동일 REQUIRES_NEW tx 내 "부분 커밋" 주장(JPA tx 원자성), ②③ 사이 crash 의 "파일 소멸+DB pending"(설계가 명시한 안전 상태 — 9e 승격), 제출검증/legacy DELETED 재시도(STORED 필터·BINARY_OUTSTANDING 마킹으로 기처리). 관찰 사항(9e 확장 시 BINARY_DELETED null path 오탐 주의, 직렬 실행 전제)은 한계로 문서화.

## Known Limitations

1. saga ② 실패 시 같은 execute 내 재시도 없음 — 9e reconciliation 이 PENDING/FAILED 재처리(execute 재실행은 ALREADY_PURGED skip 이므로 9e 가 유일 경로).
2. legacy `DELETED` enum 은 2단계 UPDATE + 잔존 0건 확인 후 3단계(후속 phase)에서 제거.
3. health scan 의 BINARY_* 상태별 정책(§6.1 표 — "PURGED 인데 파일 존재" 치명 탐지)은 9e.
4. saga 는 직렬 실행 전제(9d-1 과 동일) — 병렬화 시 target 재조회/lock 재설계.
5. 9e 의 health scan BINARY_* 확장 시 `BINARY_DELETED` 의 null storagePath 를 INVALID 로 오탐하지 않도록 §6.1 표 기준 분기 필수(적대 검증 관찰 — 현재는 scan 대상 외라 안전).

## Next Phase Considerations

- **09e — Reconciliation + 안정화**: storage-health-scan §6.1 확장(BINARY_DELETE_PENDING/FAILED 재처리 sweep, BINARY_DELETED+파일존재 치명 탐지), PURGE_PENDING 앱의 saga 재구동 진입점, FAILED item reasonMessage, 회귀 하드닝(PII 부재 검증/권한 매트릭스/멱등/Clock).
- 2단계 데이터 마이그레이션 실행 시점 운영 협의.
