# Phase 09c — Retention 모델 + eligibility scan + dry-run

## Phase Summary

- Date: 2026-06-05
- Work type: implementation (설계: `docs/codex/design/phase-09-privacy-purge-audit-retention-design.md` §5.3~5.4·§6 slice 9c, ADR-0005/0007)
- Goal: 보존 정책/예외/anchor 도메인과 eligibility 산정, 그리고 **무변경 dry-run** `PurgeBatch`(scan/preview)를 구현한다. 파기 실행(execute)은 09d-1.

> 2026-06-05 구현 리뷰 반영(instruction.md, Medium 2 + Low 3): ① **hold reason 노출 차단(권장안)** — `GET /api/admin/retention/holds/**` 를 ROLE_PRIVACY_ADMIN 전용 narrow matcher 로 좁힘(자유 텍스트 사유의 RECRUIT_ADMIN 노출 차단 — projection 설계 정합). ② **actor blank 방어** — 4개 retention write/dry-run 서비스(policy CUD·hold set/release·anchor·dry-run) 전 write 메서드에 `requireActor`(null/blank → `InvalidRetentionRequestException` 400) — 관리자 행위 감사의 ANONYMOUS 기록 차단. 미래 스케줄러는 별도 SYSTEM actor 정책(후속). ③ **batch 목록 페이지네이션** — `GET /purge-batches?page&size`(default 20, **max 100**, 위반 400), 응답 `PageResponse` 전환, repository 무제한 finder 제거. ④ **서비스 레벨 최소 방어(Low 3)** — policy(request/periodDays/baselineType/enabled null), hold(applicationId/reason), anchor(hiringEndedAt) — Bean Validation 우회 경로(배치/스케줄러) 대비. ⑤ active hold 중복 race(Low 1)는 리뷰 분류대로 **후속 항목으로 문서화**(아래 Known Limitations).

## Implemented Scope

### A — Retention 도메인

- **`RetentionPolicy`**: 전역 기본(`jobPostingId=null`) + 공고별 override. `retentionPeriodDays`(법정 일수 하드코딩 금지)/`baselineType`(HIRING_ENDED_AT|CLOSED_AT)/`enabled`/`effectiveFrom`/`effectiveTo`. CUD 시 같은 scope 의 enabled 정책 간 effective window **overlap 금지** 검증(규칙 4/5). 적용 대상(jobPostingId) 변경 금지(새 정책 생성 유도).
- **`RetentionHold`**: **관리자 수동 hold only**(리뷰 2차 #6 — 자동 onboarded-hold 는 도메인 근거 부재로 후속). release 는 행 삭제가 아니라 `releasedAt` 마킹(증적 보존). 동일 application 의 active hold 중복 금지. hold 사유 원문은 도메인만 보유(ActivityLog 미기록).
- **`JobPosting.hiringEndedAt`**: retentionAnchorAt 소스. **자동 세팅 금지** — `POST .../anchor` 수동 명령으로만 확정(`fixHiringEndedAt`, 정정 재확정 허용). 암묵 closedAt fallback 금지.
- **`JobApplication` 파기 marker 컬럼**(purgeBatchId/purgeResult/purgedAt): 09c 는 eligibility 의 `ALREADY_PURGED` 판정에 **읽기만**(쓰기는 09d-1). `JobApplicationStatus` enum 불변(orthogonal marker).

### B — 정책 선택 규칙 (9c 계약, 설계 §5.3)

`RetentionPolicyService.selectPolicy(jobPostingId, scanAt)`:
1. override 우선 → 2. global fallback → 3. effective window 는 **scanAt 기준** 평가 → 4/5. overlap 은 CUD 검증 → 6. 부재 = `POLICY_NOT_FOUND` → 7. **fail-safe**: active 후보 2개 이상이면 아무것도 선택하지 않고 `POLICY_CONFLICT`(+batch `policyConflictCount` 집계, 리뷰 3차 #4).

### C — eligibility 판정 (`RetentionEligibilityService`)

`eligibility = anchor 종료 + retentionPeriod 경과 + not purged + not hold + terminal`. 판정 순서 고정(결정적 계약):
① `ALREADY_PURGED` ② `RETENTION_HOLD` ③ `POLICY_NOT_FOUND`/`POLICY_CONFLICT` ④ `ANCHOR_NOT_FIXED` ⑤ `RETENTION_NOT_DUE` ⑥ `INVALID_STAGE_CONFIGURATION` ⑦ `APPLICATION_NOT_TERMINAL`.

terminal = 설계 9c 확정 query 그대로: `status == WITHDRAWN` OR (finalStage **정확히 1개** AND finalStage.status ∈ {RESULT_ANNOUNCED, CLOSED} AND 해당 application 의 StageResult 존재 AND resultStatus ≠ PENDING AND decidedAt ≠ null). finalStage 0/2+ = `INVALID_STAGE_CONFIGURATION`.

날짜 계산은 주입 `Clock` 산출 `scanAt` 기준 — 판정 컴포넌트는 시계를 직접 읽지 않는다(fixed Clock 테스트).

### D — dry-run PurgeBatch (무변경 preview)

- `RetentionDryRunService.dryRun(actor)`: `PurgeBatch(mode=DRY_RUN, RUNNING)` 생성 → 전체 JobApplication 을 공고 단위로 그룹 → 정책 선택/finalStage/결과 로드 → application 별 eligibility → `PurgeJobItem(ELIGIBLE | SKIPPED+reasonCode)` 기록 → batch `COMPLETED`(total/eligible/skipped/policyConflict 집계).
- **도메인 무변경** — batch/item + 감사만 생성. execute(09d-1)는 dry-run 을 믿지 않고 재검증한다.
- `PurgeBatch`/`PurgeJobItem` = **delete 금지 mutable ledger**(append-only 아님, 리뷰 2차 #10) — repository 는 `Repository` 마커로 save+조회만 노출.
- ActivityLog 는 **batch 단위 coarse index 만**: `PURGE_SCAN` SUCCESS in-tx + `PurgeBatchMetadata`(집계). item 중복 기록 금지.

### E — API + 인가 (ADR-0007)

| Method | Path | 권한 |
| --- | --- | --- |
| GET | `/admin/retention/policies` | RECRUIT_ADMIN·PRIVACY_ADMIN |
| POST | `/admin/retention/policies`(create) · `/policies/{id}`(update) · `/policies/{id}/delete`(delete) | **PRIVACY_ADMIN** |
| GET | `/admin/retention/holds` | **PRIVACY_ADMIN**(reason 자유 텍스트 — 리뷰 Medium 1 반영) |
| POST | `/admin/retention/holds`(set) · `/holds/{id}/release`(release) | **PRIVACY_ADMIN** |
| POST | `/admin/retention/job-postings/{id}/anchor` | **PRIVACY_ADMIN** |
| POST | `/admin/retention/purge-batches/dry-run` | RECRUIT_ADMIN·PRIVACY_ADMIN |
| GET | `/admin/retention/purge-batches?page&size`(+`/{id}`) | RECRUIT_ADMIN·PRIVACY_ADMIN — 목록은 page/size 필수 가드(size≤100, 리뷰 Low 2) |

- SecurityConfig: **HTTP method 까지 분기한 narrow matcher**(설계 리뷰 #5/#7)를 broad `/api/admin/**` 보다 먼저 등록. PRIVACY_ADMIN 단독 권한도 retention GET/dry-run 접근 가능.
- **전 엔드포인트 GET/POST 만 허용(프로젝트 규약)**: 정책 update/delete·hold release 는 과거 PUT/DELETE → POST 로 전환(`/policies/{id}` update, `/policies/{id}/delete`, `/holds/{id}/release`). write 권한(PRIVACY_ADMIN)은 `POST /policies/**`·`POST /holds/**` 한 줄로 커버, dead PUT/DELETE matcher 제거.
- 09c batch/item 응답은 식별자·집계·reasonCode 만(지원자 PII 없음)이라 RECRUIT/PRIVACY 동일 — execute 실패 상세 원문이 생기는 09d 에서 projection 분기 도입 예정.

### F — 감사 계측 (in-tx, ADR-0006)

| 행위 | actionType | targetType/targetId | metadata |
| --- | --- | --- | --- |
| 정책 CUD | `RETENTION_POLICY_UPDATE` | RETENTION_POLICY / policyId | `RetentionPolicyChangeMetadata(operation, …설정값)` |
| hold set/release | `RETENTION_HOLD_SET`/`RETENTION_HOLD_RELEASE` | RETENTION_HOLD / holdId (+applicationId 컬럼) | 없음(사유 원문 금지) |
| anchor 확정 | `RETENTION_ANCHOR_SET` | JOB_POSTING / jobPostingId | reasonMessage=`hiringEndedAt=…` |
| dry-run 완료 | `PURGE_SCAN` | PURGE_BATCH / batchId | `PurgeBatchMetadata(집계)` |

`AuditMetadata` sealed permits 에 `RetentionPolicyChangeMetadata`/`PurgeBatchMetadata` 추가(설계 §5.1 목록 완성). actor 는 컨트롤러가 검증한 임직원 loginId 를 명시 전달(9b 리뷰 Low 1 패턴).

## Not Implemented / Out of Scope

- purge **execute**(`POST /purge-batches/execute`, confirmation/`sourceDryRunBatchId`/재검증/tombstone/ref-count) — 09d-1.
- 첨부 바이너리 삭제 saga / reconciliation — 09d-2/09e.
- 스케줄 auto-scan(@Scheduled/Quartz) — disabled-by-default 설계, 후속.
- forced purge — enum 슬롯만(`PurgeTriggerType`).
- `ROLE_PRIVACY_ADMIN` DeptRoleMapping 운영 데이터 매핑(운영 협의).

## Changed Files

### New Files (main 27)

| 분류 | 파일 |
|------|------|
| Enum(6) | `RetentionBaselineType` `PurgeBatchMode` `PurgeBatchStatus` `PurgeItemStatus` `PurgeTriggerType` `PurgeResult` |
| Entity(4) | `RetentionPolicy` `RetentionHold` `PurgeBatch` `PurgeJobItem` |
| Repository(4) | `RetentionPolicyRepository` `RetentionHoldRepository` `PurgeBatchRepository`(delete 미노출) `PurgeJobItemRepository`(delete 미노출) |
| Service(6) | `RetentionPolicyService` `RetentionPolicySelection` `RetentionHoldService` `RetentionAnchorService` `RetentionEligibilityService` `RetentionDryRunService` `PurgeBatchReadService` |
| Metadata(2) | `RetentionPolicyChangeMetadata` `PurgeBatchMetadata` |
| DTO request(3) | `RetentionPolicyRequest` `RetentionHoldCreateRequest` `RetentionAnchorRequest` |
| DTO response(6) | `RetentionPolicyResponse` `RetentionHoldResponse` `RetentionAnchorResponse` `PurgeBatchResponse` `PurgeJobItemResponse` `PurgeBatchDetailResponse` |
| Controller(1) | `AdminRetentionController` |
| Exception(5) | `InvalidRetentionPolicyException`(400) `RetentionPolicyNotFoundException`(404) `InvalidRetentionHoldException`(400) `RetentionHoldNotFoundException`(404) `PurgeBatchNotFoundException`(404) |

### Modified Files (main 5)

| File | Change |
|------|--------|
| `domain/entity/JobPosting.java` | `hiringEndedAt` + `fixHiringEndedAt()` |
| `domain/entity/JobApplication.java` | `purgeBatchId`/`purgeResult`/`purgedAt`(읽기 전용 marker) |
| `service/AuditMetadata.java` | permits +2 |
| `exception/GlobalExceptionHandler.java` | retention 400/404 핸들러 |
| `config/SecurityConfig.java` | retention narrow matcher 8종(method 분기, broad 보다 먼저) |

### Tests (신규 4 + 보강 1)

| File | 건수 | 내용 |
|------|------|------|
| `RetentionEligibilityServiceTest` | 10 | reasonCode 전 경로 + terminal query 계약(WITHDRAWN 우회/finalStage 0·2개/IN_PROGRESS/결과 부재/PENDING) + CLOSED_AT baseline + 암묵 fallback 금지 |
| `RetentionPolicyServiceTest` | 9 | 선택 규칙 7종(override 우선/global/scanAt window/NOT_FOUND/CONFLICT×2) + overlap 거부/분리 허용/disabled 제외 + 기간 역전 거부 + 감사 검증 |
| `RetentionDryRunServiceTest` | 1 | 통합 — eligible/withdrawn/held/anchorless 4건 산정, 집계 일치, **무변경**(marker null), `PURGE_SCAN` coarse 감사 1건 |
| `AdminRetentionControllerTest` | 11 | 권한 매트릭스(springSecurity) — write=PRIVACY 전용·RECRUIT 403, GET/dry-run=RECRUIT 허용, 일반 임직원 403, 미인증 401, validation 400 |
| `AuditMetadataContractTest` | 3(보강) | permits 9종 + 신규 2 record allowlist |

## Class-by-Class Explanation (핵심)

### RetentionPolicyService — Service
- 책임: 정책 CUD(+overlap 검증, in-tx 감사) + `selectPolicy`(규칙 1~7).
- 주의: create 경로의 overlap 필터는 자기 자신 제외가 없다(null id 오인 제외 방지).

### RetentionEligibilityService — Component (순수 판정)
- 책임: 설계 terminal query/reasonCode 계약 구현. 시계/DB 비의존 — 호출자가 scanAt/사전 로드 데이터를 주입.

### RetentionDryRunService — Service
- 책임: dry-run 오케스트레이션(단일 tx) — batch 생성→공고별 정책/스테이지/결과 로드→판정→item 기록→집계 완료→PURGE_SCAN 감사.
- 주의: 전체 JobApplication 풀스캔(메모리 그룹핑) — 현 규모 전제, 대규모화 시 페이징 후속.

### RetentionHoldService / RetentionAnchorService — Service
- hold set/release(중복 active 금지, releasedAt 마킹) / anchor 수동 확정. 둘 다 in-tx 감사.

### PurgeBatch / PurgeJobItem — Entity (ledger)
- RUNNING→COMPLETED/FAILED 전이 메서드, dry-run item 정적 팩토리(eligible/skipped). repository 에서 delete 미노출.

## Entity Relationship Summary

- `PurgeBatch` 1:N `PurgeJobItem`(ManyToOne LAZY). `RetentionPolicy`/`RetentionHold`/`PurgeJobItem` 의 jobPostingId/applicationId 는 FK 없는 denormalized key(파기 후 join 회피 — ActivityLog 와 동일 원칙). 기존 도메인 관계 불변(JobPosting/JobApplication 은 컬럼 추가만).

## Validation and Business Rules

1. 정책: periodDays ≥ 1, from ≤ to, 같은 scope enabled overlap 금지, 적용 대상 변경 금지.
2. 선택: override > global, scanAt 기준 window, 부재=NOT_FOUND, 2+ 후보=CONFLICT(fail-safe, 선택 안 함).
3. eligibility 판정 순서 고정 + terminal query 계약(§C).
4. anchor 암묵 fallback 금지 — HIRING_ENDED_AT 정책은 closedAt 이 있어도 `ANCHOR_NOT_FIXED`.
5. hold: active 중복 금지, release=마킹, 사유 원문 ActivityLog 금지.
6. dry-run 무변경 — 도메인 marker 미세팅, ledger 와 coarse 감사만.
7. batch/item delete 금지(레포지토리 계약).
8. 인가: write=PRIVACY_ADMIN, narrow matcher 가 broad 보다 먼저(순서=보안 요구사항).

## 운영(MariaDB) 수동 DDL

`docs/codex/ops/phase-09c-retention-ddl.sql` — 신규 테이블 4종 + `job_posting.hiring_ended_at` + `job_application` 파기 marker 3컬럼. migration framework 부재로 수동 적용(H2 는 ddl-auto). purge ledger 테이블에는 운영 DELETE 권한 부여 금지 권고.

## Test Commands

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*Retention*" --tests "*AdminRetentionControllerTest" --tests "*AuditMetadataContractTest" --no-daemon
```

## Test Results

- 신규/보강: **34 tests — 전부 통과** (`RetentionEligibilityServiceTest` 10 · `RetentionPolicyServiceTest` 9 · `RetentionDryRunServiceTest` 1 · `AdminRetentionControllerTest` 11 · `AuditMetadataContractTest` 3).
- 회귀(영향 영역 — SecurityConfig/JobPosting/JobApplication/감사): **91 tests — 전부 통과** (`JobApplicationServiceTest` 36 · `JobPostingServiceTest` 27 · `AdminAuditControllerTest` 10 · `ActivityLogServiceTest` 10 · `JobPostingControllerTest` 7 · `StageAuditInstrumentationTest` 1).
- 전체 회귀 미실행(프로젝트 규칙 — 명시 요청 시에만).

## Known Limitations

1. dry-run 은 전체 JobApplication 메모리 스캔 — 대규모 데이터 시 페이징/스트리밍 후속(09e 하드닝 후보).
2. batch/item 응답의 권한별 projection(요약 vs 원문) 미분기 — 09c 데이터에 PII 가 없어 동일 응답, 09d execute 상세 도입 시 분기. (hold reason 은 리뷰 반영으로 GET 자체를 PRIVACY 전용화 — projection 불요.)
3. overlap 금지의 DB 제약 없음(서비스 검증만) — 선택 규칙의 fail-safe(POLICY_CONFLICT)가 운영 직접수정 우회를 흡수.
3-1. **active hold 중복의 DB unique 제약 없음(리뷰 Low 1 — 후속)**: 동시 set 2건이 exists 체크를 동시 통과하면 active hold 가 2개 생길 수 있다. eligibility 는 applicationId Set 기반이라 파기 제외는 유지되지만, 원장 위생 관점에서 후속 보강 후보: generated column `active_flag` + unique(application_id, active_flag) / 중복 감지·repair API / set 시 pessimistic lock. 미래 스케줄러의 자동 실행은 별도 SYSTEM actor 정책으로 연다(requireActor 는 수동 경로 전제).
4. `ROLE_PRIVACY_ADMIN` 운영 매핑(DeptRoleMapping) 미세팅 — 운영 협의 필요.
5. 스케줄 auto-scan 없음(설계상 disabled-by-default — 수동 dry-run 만).
6. 운영 DDL 수동 적용 필요(위 §운영 DDL).

## Next Phase Considerations

- **09d-1 — Purge execute core**: `POST /purge-batches/execute`(PRIVACY_ADMIN, confirmation + bulk 는 `sourceDryRunBatchId` 필수), 실행 시 eligibility **재검증**(본 슬라이스 `RetentionEligibilityService` 재사용), 관계형 PII tombstone/Applicant ref-count 익명화(`phase-09-pii-field-inventory.md` allowlist), `PurgeBatch`/`PurgeJobItem` execute 상태 전이, `JobApplication` purge marker 쓰기. **첨부 바이너리 삭제 완료 전 최종 PURGED 승격 금지.**
- execute 의 batch 상세 응답에 권한별 projection 분기 도입.
