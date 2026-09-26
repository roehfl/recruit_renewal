# 개인정보 보존·파기 (`privacy-audit`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [privacy-audit-audit](privacy-audit-audit.md)(감사 로그 `ActivityLog`, 이 카드는 `PURGE_SCAN`·`PURGE_EXECUTE`·`PURGE_RECONCILE`·`PURGE_FORCED`·`RETENTION_*` 이벤트를 그 서비스로 기록한다) · [attachment](attachment.md)(첨부 저장소) · [role-menu](role-menu.md)(역할 매핑) · [auth-account](auth-account.md)(`SecurityConfig`·`Applicant` 익명화) · [application](application.md)(`JobApplication`) · [application-sections](application-sections.md)(섹션 엔티티) · [job-posting](job-posting.md)(`hiringEndedAt`) · [stage-result](stage-result.md) · [interview](interview.md) · [admin-application](admin-application.md)(반출 감사)

## 요약

- **보존**: 정책(`RetentionPolicy`, 전역+공고별), 보류(`RetentionHold`), 기산점(`JobPosting.hiringEndedAt`) — 전부 수동 관리.
- **파기**: dry-run(산정만) → execute(재검증 후 tombstone 익명화 + 첨부 바이너리 물리삭제) → reconcile(바이너리 잔여 재처리).
- **강제 파기**(정보주체 삭제 요청)와 **자동 파기 스케줄러**(매일 03:00, 게이트 통과 시 dry-run → execute)가 추가됐다. 관리자 화면 `/admin/retention`에서 보존 정책·자동 파기 설정, 삭제 요청 파기, 파기 이력을 다룬다.
- 권한(ADR 0007): 쓰기·실행·민감 조회 = `ROLE_PRIVACY_ADMIN`, 조회·dry-run = `ROLE_RECRUIT_ADMIN`도. `ROLE_ADMIN`(IT)만 있으면 403.

## 용어

| 용어 | 뜻 |
|---|---|
| 파기(purge) | 원문 PII 비가역 소거 + 비식별 tombstone 유지 + 첨부 바이너리 물리삭제. 행 삭제 아님 |
| placeholder | NOT NULL 문자열 PII 대체값 `__PURGED__` |
| ref-count 익명화 | 지원자의 **모든** 지원서가 파기 대상일 때만 `Applicant` 공통 PII 제거 |
| anchor | 보존 기산점. 기본 `hiringEndedAt`(채용 실질 종료, 수동 확정). `closedAt`은 정책이 `CLOSED_AT`일 때만 |
| 전역 / override 정책 | `RetentionPolicy.jobPostingId` null / 공고 id |
| ledger | `PurgeBatch`(실행 1회)·`PurgeJobItem`(지원서별). delete 금지 |
| drift | dry-run ELIGIBLE이 execute 재검증에서 탈락(`SKIPPED`) |
| `PurgeResult` | `PURGE_PENDING`(PII 제거, 바이너리 소멸 미확인) · `PURGED`(둘 다 완료, "DB PURGED + 파일 잔존" 불허). `JobApplicationStatus`와 별개 |
| saga | ① item tx(PII 제거·`BINARY_DELETE_PENDING`) → ② tx 밖 물리 삭제+재확인 → ③ 새 tx 확정 |

감사 로그 자체 수명 정책은 미구현([privacy-audit-audit](privacy-audit-audit.md)).

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/AdminRetentionController.java` | `/admin/retention/**` 18개 |
| service | `{BE}/service/RetentionPolicy*` | `RetentionPolicyService`(CUD·겹침·적용 정책 선택)·`RetentionPolicySelection`(선택 결과)·`RetentionPolicyChangeMetadata` |
| service | `{BE}/service/RetentionHoldService.java` | 보류 설정·해제 |
| service | `{BE}/service/RetentionAnchorService.java` | anchor 확정 |
| service | `{BE}/service/RetentionEligibilityService.java` | 적격성 판정(순서 고정) |
| service | `{BE}/service/RetentionDryRunService.java` | dry-run |
| service | `{BE}/service/PurgeBatchReadService.java` | batch 목록·상세 |
| service | `{BE}/service/PurgeExecutionService.java` | execute 오케스트레이션(무트랜잭션) |
| service | `{BE}/service/PurgeBatchLifecycleService.java` | execute batch 시작·완료·실패 + 감사 |
| service | `{BE}/service/PurgeItemProcessor.java` | 지원서 1건 파기 tx·saga ③·실패 기록·ref-count |
| service | `{BE}/service/ApplicationPiiPurgeService.java` | 관계형 PII tombstone 호출 순서 |
| service | `{BE}/service/AttachmentPurgeSagaService.java` | saga ② 물리 삭제·재확인 |
| service | `{BE}/service/PurgeReconciliationService.java` | `PURGE_PENDING` 재처리 |
| service | `{BE}/service/Purge*Metadata.java` | dry-run·execute·reconcile 감사 metadata 3종 |
| service | `{BE}/service/ForcedPurgeService.java` | 강제 파기 오케스트레이션(무트랜잭션, 지원자 1명 단위) |
| service | `{BE}/service/DataSubjectLookupService.java` | 파기 대상자 검색·상세(읽기 전용, 상한 50) |
| service | `{BE}/service/RetentionScheduleService.java` | 자동 파기 설정·다음 파기 예정일·실행 결과 기록 |
| service | `{BE}/service/RetentionPurgeScheduler.java` | 매일 03:00 게이트 2단 → dry-run → execute |
| service | `{BE}/service/ForcedPurgeMetadata.java` | 강제 파기 감사 metadata(PII-free) |
| entity | `{BE}/domain/entity/Purge*` | `PurgeBatch`(모드·상태·집계)·`PurgeJobItem`(지원서별 결과) |
| entity | `{BE}/domain/entity/Retention*` | `RetentionHold`·`RetentionPolicy`(`isEffectiveAt`·`overlapsWith`) |
| entity | `{BE}/domain/entity/RetentionScheduleSetting.java` | 자동 파기 단일 행 설정 |
| repository | `{BE}/domain/repository/ApplicationPiiPurgeRepository.java` | 파기 JPQL bulk update — 필드 분류의 단일 출처 |
| repository | `{BE}/domain/repository/Purge*` | batch·item — save·조회만(delete 없음) |
| repository | `{BE}/domain/repository/Retention*` | active hold, scope별 enabled 정책 |
| repository | `{BE}/domain/repository/RetentionScheduleSettingRepository.java` | 단일 행 조회·저장 |
| dto | `{BE}/dto/request/Retention*` | 정책·보류·anchor 요청 |
| dto | `{BE}/dto/request/PurgeExecuteRequest.java` | execute 요청 |
| dto | `{BE}/dto/request/ForcedPurgeRequest.java` · `RetentionScheduleRequest.java` | 강제 파기·스케줄 토글 요청 |
| dto | `{BE}/dto/response/Retention*` | 정책·보류·anchor 응답 |
| dto | `{BE}/dto/response/Purge*` | batch·batch 상세·item·reconcile 응답 |
| dto | `{BE}/dto/response/DataSubject*Response.java` · `RetentionScheduleResponse.java` | 대상자 요약·상세·스케줄 응답 |
| enum | `{BE}/enumeration/Purge*` | `PurgeBatchMode`·`PurgeBatchStatus`·`PurgeItemStatus`·`PurgeResult`·`PurgeTriggerType` |
| enum | `{BE}/enumeration/RetentionBaselineType.java` | `HIRING_ENDED_AT`·`CLOSED_AT` |
| enum | `{BE}/enumeration/ForcedPurgeReason.java` · `RetentionScheduleRunResult.java` | 강제 파기 사유 3종·스케줄 실행 결과 5종 |
| exception | `{BE}/exception/InvalidRetention*` | Policy·Hold·Request 400 |
| exception | `{BE}/exception/Retention*NotFoundException.java` | Policy·Hold 404 |
| exception | `{BE}/exception/PurgeBatchNotFoundException.java` | 404 |
| exception | `{BE}/exception/ApplicantNotFoundException.java` | 404 |
| test | `{BT}/controller/AdminRetentionControllerTest.java` | 권한 행렬·validation |
| test | `{BT}/service/Retention*` | 정책 선택, 판정 순서, dry-run |
| test | `{BT}/service/Purge*` | execute→saga, reconcile |
| test | `{BT}/service/ApplicationPiiPurgeServiceTest.java` | 필드별 tombstone |
| test | `{BT}/service/ForcedPurge*` | 강제 파기 오케스트레이션·감사 metadata |
| test | `{BT}/service/DataSubject*` | 대상자 검색·상세 |
| test | `{BT}/service/RetentionSchedule*` | 스케줄 설정·다음 예정일 |
| test | `{BT}/service/RetentionPurgeScheduler*` | 게이트 2단·dry-run→execute |
| test | `{BT}/domain/repository/RetentionScheduleSettingRepositoryTest.java` | 단일 행 조회·저장 |
| test | `{BT}/domain/repository/ApplicationPiiPurgeRepositoryTest.java` | 파기 JPQL bulk update |

### 프론트

| 파일 | 역할 |
|---|---|
| `{FE}/views/admin/retention/AdminRetentionView.vue` | 화면 뼈대(정책·자동 파기 카드 + 탭 2개) |
| `{FE}/views/admin/retention/RetentionPolicyCard.vue` | 전역 보존 정책 조회·등록·수정 |
| `{FE}/views/admin/retention/RetentionScheduleCard.vue` | 자동 파기 on/off·다음 예정일·마지막 실행 |
| `{FE}/views/admin/retention/DataSubjectSearchPanel.vue` | 대상자 검색·결과 표 |
| `{FE}/views/admin/retention/DataSubjectDrawer.vue` | 대상자 상세·파기 버튼 |
| `{FE}/views/admin/retention/ForcedPurgeConfirmModal.vue` | 비가역 파기 확인(사유·체크박스) |
| `{FE}/views/admin/retention/PurgeBatchPanel.vue` | 파기 이력 목록·상세 드로어 |
| `{FE}/views/admin/retention/retentionLabel.ts` | 코드→한글 라벨 매핑 |
| `{FE}/api/admin/retentionApi.ts` · `{FE}/types/admin/retention.ts` | API 모듈·타입 |

라우트 `/admin/retention`(`AdminRetention`)은 `meta.roles`로 `ROLE_RECRUIT_ADMIN`·`ROLE_PRIVACY_ADMIN`만 통과시킨다({FE}/routes/adminRoutes.ts). `ROLE_ADMIN` 단독 사용자는 백엔드 retention GET도 RECRUIT·PRIVACY 전용이라 들어오면 403으로 튕기기 때문이다.

## API 계약

응답은 `ApiResponse<T>`. `PRIVACY` = `hasAuthority(ROLE_PRIVACY_ADMIN)`, `R·P` = `hasAnyAuthority(ROLE_RECRUIT_ADMIN, ROLE_PRIVACY_ADMIN)`. 비인증 401, 권한 없음 403.

| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |
|---|---|---|---|---|---|
| 🟢 | GET | /admin/retention/policies | 없음 | `RetentionPolicyResponse[]` id asc | R·P |
| 🟢 | POST | /admin/retention/policies | `RetentionPolicyRequest` | `RetentionPolicyResponse` | PRIVACY |
| 🟢 | POST | /admin/retention/policies/{policyId} | `RetentionPolicyRequest`(전체 교체) | `RetentionPolicyResponse` | PRIVACY |
| 🟢 | POST | /admin/retention/policies/{policyId}/delete | 없음 | `data: null` | PRIVACY |
| 🟢 | GET | /admin/retention/holds | 없음 | `RetentionHoldResponse[]` id desc(해제 포함) | PRIVACY |
| 🟢 | POST | /admin/retention/holds | `{ applicationId, reason }` | `RetentionHoldResponse` | PRIVACY |
| 🟢 | POST | /admin/retention/holds/{holdId}/release | 없음 | `RetentionHoldResponse` | PRIVACY |
| 🟢 | POST | /admin/retention/job-postings/{jobPostingId}/anchor | `{ hiringEndedAt }` | `{ jobPostingId, hiringEndedAt }` | PRIVACY |
| 🟢 | POST | /admin/retention/purge-batches/dry-run | 없음 | `PurgeBatchDetailResponse` | R·P |
| 🟢 | POST | /admin/retention/purge-batches/execute | `{ confirm, sourceDryRunBatchId?, applicationId? }` | `PurgeBatchDetailResponse` | PRIVACY |
| 🟢 | POST | /admin/retention/purge-batches/reconcile | query `limit`(100) | `PurgeReconcileResponse` | PRIVACY |
| 🟢 | GET | /admin/retention/purge-batches | query `page`(0)·`size`(20) | `PageResponse<PurgeBatchResponse>` id desc | R·P |
| 🟢 | GET | /admin/retention/purge-batches/{batchId} | 없음 | `PurgeBatchDetailResponse` | R·P |
| 🟢 | GET | /admin/retention/data-subjects | query `name`·`phoneNumber`·`email` | `DataSubjectSummaryResponse[]` | PRIVACY |
| 🟢 | GET | /admin/retention/data-subjects/{applicantId} | 없음 | `DataSubjectDetailResponse` | PRIVACY |
| 🟢 | POST | /admin/retention/purge-batches/force | `ForcedPurgeRequest` | `PurgeBatchDetailResponse` | PRIVACY |
| 🟢 | GET | /admin/retention/schedule | 없음 | `RetentionScheduleResponse` | R·P |
| 🟢 | POST | /admin/retention/schedule | `{ enabled }` | `RetentionScheduleResponse` | PRIVACY |

감사 로그 조회 API(`GET /admin/audit/activities[/{id}]`)는 [privacy-audit-audit](privacy-audit-audit.md) 소유.

### 엔드포인트 상세

- FE(`{FE}/api/admin/retentionApi.ts`)는 정책 조회·등록·수정, 자동 파기 스케줄 조회·토글, 대상자 검색·상세, 강제 파기, 파기 배치 목록·상세를 쓴다. 정책 삭제·holds·anchor·dry-run·execute·reconcile는 FE 미사용. 코드 기준 🟢.
- POST는 actor를 `CurrentEmployeeService.getCurrentEmployeeActor`로 얻는다(임직원 principal 아니면 401/403). enum query 값 오류는 400 `"Invalid request."`. 목록은 공통 `PageResponse`(`content`·`page`·`totalElements` 등).
- **정책**: `{ jobPostingId?, retentionPeriodDays*(≥1), baselineType*, enabled*, effectiveFrom?, effectiveTo? }` ↔ 응답 같은 필드 + `id`. 수정 시 `jobPostingId` 변경 400. 없는 policyId·jobPostingId 404.
- **보류**: `{ applicationId*, reason*(≤1000) }` → `{ id, applicationId, reason, heldBy, createdAt, releasedAt, releasedBy, active }`. `reason`은 민감할 수 있는 자유 텍스트라 **목록도 PRIVACY 전용**. 없는 지원서·holdId 404, active 중복·이미 해제 400.
- **anchor**: `hiringEndedAt`(LocalDateTime) 필수, 재확정 허용, 없는 공고 404.
- **batch**: `{ batch, items[] }`. batch = `PurgeBatch` 컬럼(모드·상태·시각·`requestedBy`·`sourceDryRunBatchId`·집계 8종), item = `{ id, applicationId, jobPostingId, status, reasonCode }`. dry-run items = 전 지원서. PII 없음. 목록 `size` 1..100 밖·`page<0` 400.
- **execute**(batch 생성 전 검사): `confirm`≠true 400, `sourceDryRunBatchId`(일괄)·`applicationId`(단건) 중 정확히 하나 아니면 400, 근거 batch 없음 404·`COMPLETED` `DRY_RUN` 아님 400, 단건 지원서 없음 404.
- **reconcile**: `limit`은 1..1000으로 clamp(400 없음) → `{ reconciledAt, scannedCount, promotedCount, stillPendingCount, errorCount }`. `scannedCount == limit`이면 재호출.
- **대상자 조회**: `name`·`phoneNumber`·`email` 세 조건 중 1개 이상 필수(전부 비면 400), 이름·이메일 부분 일치, 휴대폰은 하이픈·공백 제거 후 부분 일치, 상한 50건, `ciHash`가 `PURGED:` 접두인 익명화 계정 제외. 상세는 지원서별 적격성 판정(`eligible`·`reasonCode`)을 함께 주고 **hold 사유 원문은 주지 않는다**. 없는 applicantId는 404. 연락처는 **마스킹하지 않는다**.
- **강제 파기**: `confirm`≠true 400, 없는 지원자 404, active hold 보유 시 400(batch 생성 전 거부). 사유는 `ForcedPurgeReason` 3값(`DATA_SUBJECT_REQUEST`·`DUPLICATE_ACCOUNT`·`OTHER`) 선택형만 — 자유 텍스트를 받지 않는다.
- **스케줄**: GET은 설정 행이 없어도 기본값(꺼짐·`nextPurgeDate` `9999-12-31`)을 준다. POST는 보존 정책이 없으면 400(켜도 전건 스킵되므로), actor 없으면 400.

## 규칙·불변식

감사 기록 메커니즘(`ActivityLogService` 2경로·필수 필드·`AuditMetadata` allowlist)과 "감사 호출 규약" 표(이 카드가 기록하는 `RETENTION_*`·`PURGE_*` actionType 포함)는 [privacy-audit-audit](privacy-audit-audit.md)에 있다.

**정책·보류·anchor**
- 보존·파기 쓰기 서비스는 모두 actor 필수(빈 값 400, ANONYMOUS 감사 차단). (각 서비스 — requireActor)
- 정책: days ≥1, `baselineType`·`enabled` 필수, `jobPostingId` 존재(404), `effectiveFrom ≤ effectiveTo`. 같은 scope(같은 공고 또는 전역) enabled 정책끼리 유효기간 겹침 400(null = 열린 구간, disabled·자기 자신 제외). ({BE}/service/RetentionPolicyService.java — validateRequest, validateNoOverlap)
- 적용 정책 선택(`scanAt` 기준, 유효 = enabled && from ≤ scanAt ≤ to): override 유효 1개 → 사용 / 2개+ → `POLICY_CONFLICT`(전역으로 안 내려감) / 0개 → 전역 1개 → 사용, 2개+ → `POLICY_CONFLICT`, 0개 → `POLICY_NOT_FOUND`. ({BE}/service/RetentionPolicyService.java — selectPolicy)
- 정책 CUD 감사 = `RETENTION_POLICY_UPDATE`(metadata `operation` `CREATE`·`UPDATE`·`DELETE`). 삭제는 hard delete. ({BE}/service/RetentionPolicyService.java — recordPolicyAudit)
- 보류: 지원서당 active 1개(서비스 검사만, DB unique 없음), 해제는 active만, 감사에 **사유 원문 미기록**. ({BE}/service/RetentionHoldService.java — set, release)
- anchor: 검증 없이 덮어쓴다. 자동 설정 없음(마감 ≠ 채용 종료). ({BE}/service/RetentionAnchorService.java — fixAnchor)

**적격성 판정 — 순서가 계약** ({BE}/service/RetentionEligibilityService.java — evaluate)
1. `purgeResult != null` → `ALREADY_PURGED` 2. active hold → `RETENTION_HOLD` 3. 정책 미선택 → `POLICY_NOT_FOUND`·`POLICY_CONFLICT`
4. 정책 baseline 날짜(`hiringEndedAt` 또는 `closedAt`) null → `ANCHOR_NOT_FIXED`(다른 날짜로 대체 금지) 5. anchor + days > scanAt → `RETENTION_NOT_DUE`
6. `WITHDRAWN` → 적격 7. `finalStage` 단계 수 ≠ 1 → `INVALID_STAGE_CONFIGURATION`
8. 최종 단계가 `RESULT_ANNOUNCED`·`CLOSED` 아님 / 결과 없음 / `PENDING` / `decidedAt` null → `APPLICATION_NOT_TERMINAL` 9. 그 외 적격. 시계는 호출자가 `scanAt`으로 넣는다.

**dry-run** ({BE}/service/RetentionDryRunService.java — dryRun)
- 한 트랜잭션: batch(`DRY_RUN`) → 전 지원서(`findAll`) 판정 → item `ELIGIBLE`/`SKIPPED`+사유 → `COMPLETED`+집계 → `PURGE_SCAN` 감사. 도메인 무변경, 실패하면 전부 롤백(FAILED dry-run은 남지 않음).

**execute** ({BE}/service/PurgeExecutionService.java — execute)
- 클래스 트랜잭션 없음(의도). 검증 → 후보(일괄 = 근거 dry-run의 `ELIGIBLE` item, 단건 = `applicationId`) → batch `RUNNING` 선커밋 → item 처리 → 집계. 시작 감사는 없다.
- item 1건 = REQUIRES_NEW all-or-nothing: 재검증(탈락 `SKIPPED`) → 관계형 PII tombstone → 모든 첨부 metadata PII 제거, 바이너리 미소멸(`STORED`·`DELETED`·`SOFT_DELETED`·`BINARY_DELETE_*`) 첨부는 `BINARY_DELETE_PENDING` → 있으면 `PURGE_PENDING`+item `PENDING`, 없으면 `PURGED`(`purgedAt`)+item `PURGED` → ref-count 익명화. ({BE}/service/PurgeItemProcessor.java — process)
- item 예외 → 롤백, 별도 tx로 `FAILED` item(`PURGE_ITEM_FAILED`). marker가 null로 남아 다음 dry-run→execute에서 다시 후보. ({BE}/service/PurgeItemProcessor.java — recordFailure)
- item `PENDING`이면 즉시 saga ②③: 성공 → purged, 실패 → pending·binaryDeleteFailed +1. marker 있는 지원서는 재실행해도 `ALREADY_PURGED`라 잔여는 reconcile로만 처리.
- batch: failed·binaryDeleteFailed 0 → `COMPLETED`, 하나라도 → `PARTIAL_FAILED`, 오케스트레이션·완료 기록 실패 → `FAILED`. `PURGE_EXECUTE` 감사는 `COMPLETED`면 SUCCESS, 아니면 FAILURE. ({BE}/service/PurgeBatchLifecycleService.java — completeExecute, failExecute)

**강제 파기(Phase 10)** ({BE}/service/ForcedPurgeService.java)
- 단위는 지원자 1명 전체. 모든 지원서 + `Applicant` 계정을 한 batch로 처리한다. 일부만 지우면 계정에 이름·연락처가 남는다.
- 적격성 9단계 중 **2개만** 적용: `ALREADY_PURGED`, `RETENTION_HOLD`. 보존기간·anchor·전형 종료 여부는 보지 않는다.
- active hold가 하나라도 있으면 **batch 생성 전** 400으로 거부한다(원장에 흔적을 남기지 않는다).
- 계정 익명화 판정은 **집계 카운터가 아니라 `countUnpurgedByApplicantId` 집계 쿼리**로 한다. 엔티티 재조회는 OSIV 1차 캐시가 stale 인스턴스를 돌려줄 수 있어 계정이 영영 익명화되지 않는다.
- 지원 이력 0건 계정도 대상이다(item 없이 계정만 익명화).
- 사유는 선택형 enum만 받는다. `ActivityLog`와 파기 대장은 파기 대상이 아니라 자유 텍스트에 섞인 PII가 영구히 남는다.

**자동 파기 스케줄(Phase 10)** ({BE}/service/RetentionPurgeScheduler.java, {BE}/service/RetentionScheduleService.java)
- cron은 매일 울리되 게이트 2단(자동 파기 on/off, 다음 파기 예정일)을 통과해야 스캔한다. 게이트가 없으면 dry-run이 매일 전 지원서를 훑어 지원서 수만큼 item을 쌓는다.
- 다음 파기 예정일 = 미파기 지원서를 가진 마감 공고 중 가장 이른 `closedAt` + 보존기간. 없거나 정책이 없으면 `9999-12-31`(약속된 값).
- 예정일은 **보존기간만 계산한 하한**이다. 그날 실제 파기 건수가 0일 수 있다. **한계**: 적격성을 영원히 통과하지 못하는 지원서(`DRAFT`·`APPLICATION_NOT_TERMINAL`·`INVALID_STAGE_CONFIGURATION`)가 있으면 그 공고의 예정일이 지난 뒤부터 게이트가 상시 개방된다.
- 설정 행이 없으면 꺼짐으로 본다(안전 기본값).
- `RetentionPolicy.enabled`를 스케줄 스위치로 재사용하지 않는다. 정책을 끄면 적격성 판정이 `POLICY_NOT_FOUND`가 되어 화면 표시와 감사 사유까지 오염된다.
- 스케줄 actor는 예약값 `SYSTEM`이며 감사에 `ActorType.SYSTEM`으로 남는다. `ActivityLogService`의 actorId 필수 검증은 `EMPLOYEE`·`APPLICANT`만 보므로 통과한다.
- 건수 상한 없음. 첫 가동 때 누적분이 한꺼번에 처리될 수 있다.

**파기 범위** — 모든 대상의 `createdBy`·`updatedBy`도 null. ({BE}/domain/repository/ApplicationPiiPurgeRepository.java, {BE}/service/ApplicationPiiPurgeService.java — purgeRelationalPii)

| 대상 | 처리(ph = `__PURGED__`) |
|---|---|
| `ApplicationBasicInfo` | 이름·연락처·생년월일·국적·보훈·장애·주소 전부 null(`applicationRouteCode` 유지) |
| `ApplicationAnswer` / `ApplicationEducationSemesterGrade` | `answerText` null / 감사 필드만 |
| `ApplicationEducation` | `schoolName` ph, 전공·부전공명·논문·국가·입학/졸업일 null. 코드·학점·정렬 유지 |
| `ApplicationCareer` | `companyName` ph, 부서·직위·연봉·퇴직사유·날짜 null |
| `ApplicationCertificate` | 이름·기관 ph, 날짜·점수 null, `certificateNumber` = `AuditHmac("CERT_NO:"+번호)` |
| `ApplicationLanguage` | `languageName`·`testName` ph, 나머지(`registrationNumber` 포함) null |
| `ApplicationMilitary` · `ApplicationAward` · `ApplicationGapPeriod` | 날짜·사유 null / 이름·기관 ph·설명 null / `reason` ph·설명 null |
| `InterviewEvaluation` · `StageResult` · `StageResultCorrectionHistory` | `comment` null / `comment` null / `reason` ph·전후 comment null. 결과·점수·Interview memo 유지 |
| `InterviewSupplementAnswer` | `answerText` null. 질문은 유지([interview-supplement](interview-supplement.md)) |
| `JobApplication` | `applicantNameSnapshot` ph, `purgeResult`·`purgeBatchId`·`purgedAt` |
| `ApplicationAttachment` | `originalFileName` ph, `filenameHash` = `AuditHmac("FILE_NAME:"+원본명)`, 삭제자·사유 null |
| `MessageRecipient`([message-delivery](message-delivery.md)) | 이름·이메일·휴대폰 null(AES 컬럼이라 ph 불가). 채널 상태·거래 ID·실패 사유(결과코드) 유지. 테스트 수신자(`jobApplication` null)는 대상 아님 |
| `Applicant`(ref-count 0) | `purgePersonalData` — loginId·이름·이메일·비밀번호·전화 null, `ciHash` = `PURGED:`+UUID([auth-account](auth-account.md)) |

**saga ②③·reconcile** ({BE}/service/AttachmentPurgeSagaService.java — completeBinaryDeletion, {BE}/service/PurgeItemProcessor.java — finalizeBinaryDeletion, {BE}/service/PurgeReconciliationService.java — reconcile)
- ② 대상 = `BINARY_DELETE_PENDING`·`_FAILED` 첨부. 삭제 후 `exists` 재확인, 이미 없음 = 성공, 경로 없음 = 실패(`EMPTY_STORAGE_PATH`). 그 외 실패 코드 `DELETE_FAILED`·`STILL_EXISTS`·`EXCEPTION`·`UNKNOWN`.
- ③ 성공 첨부 `BINARY_DELETED`(`storagePath` null), 실패 `BINARY_DELETE_FAILED`. **전부** 성공일 때만 지원서 `PURGED`+item PENDING→PURGED(item이 없으면 예외 롤백).
- reconcile: `PURGE_PENDING` 지원서 id asc `limit`건마다 원 `purgeBatchId`로 ②③. `purgeBatchId` null·단건 예외는 errorCount만 올리고 계속. 감사 시작(`reconcile started: scanned=N`)+요약(stillPending·error 있으면 FAILURE) — 시작만 있으면 중단된 sweep.

**권한**: API 표 권한 = `{BE}/config/SecurityConfig.java`([auth-account](auth-account.md) 소유)의 메서드별 좁은 매처. 전부 broad `/api/admin/**`보다 **앞**, GET `holds/**`는 GET `retention/**`보다 앞 — 순서가 보안 요구사항.

`AUDIT_HMAC_SECRET`·`AuditHmac`(파기 값 해시)는 [privacy-audit-audit](privacy-audit-audit.md) 소유. 새 감사 이벤트(`AuditActionType` 추가 등) 레시피도 그 카드에 있다.

## 변경 레시피

### 지원서에 PII 필드·섹션 추가
1. `{BE}/domain/repository/ApplicationPiiPurgeRepository.java`에 null/placeholder 처리 추가(새 섹션이면 쿼리 + `{BE}/service/ApplicationPiiPurgeService.java` 호출). NOT NULL 날짜는 운영 DDL로 nullable 변경 후 null. `createdBy`는 `updatable=false`라 JPQL bulk update로만 지워진다.
2. `{BT}/service/ApplicationPiiPurgeServiceTest.java` 기대값 추가. 새 테이블이면 `{BT}/service/PurgeExecutionServiceTest.java` `cleanUp` 목록도 갱신.
3. 운영 DDL은 `recruit_back/recruit_backend/docs/ops/`에 추가. 카드 "파기 범위" 표 갱신, `node tools/check-docs.mjs`.

### retention·audit 엔드포인트 추가
1. 서비스(`requireActor`·in-tx 감사) → 컨트롤러. 쓰기는 POST만, actor는 `CurrentEmployeeService.getCurrentEmployeeActor`.
2. **`{BE}/config/SecurityConfig.java`에 좁은 매처를 broad `/api/admin/**`보다 먼저 추가.** 없으면 새 POST는 ADMIN·RECRUIT에게, 새 GET은 `/api/admin/retention/**`(RECRUIT 포함)로 열린다.
3. `{BT}/controller/AdminRetentionControllerTest.java`(또는 `AdminAuditControllerTest`)에 401/403/200 추가.
4. 카드 API 표 🟡 → 구현 후 🟢, `node tools/check-docs.mjs`.

### 적격성·정책 선택 변경
1. `{BE}/service/RetentionEligibilityService.java`(순서가 계약) 또는 `{BE}/service/RetentionPolicyService.java` `selectPolicy`. 새 SKIP 사유는 `AuditReasonCode`에 추가.
2. dry-run(`RetentionDryRunService`)과 execute(`PurgeItemProcessor`)가 판정 입력을 각자 조립하므로 둘 다 맞춘다.
3. `{BT}/service/RetentionEligibilityServiceTest.java`·`{BT}/service/RetentionPolicyServiceTest.java`, 카드 규칙 갱신, `node tools/check-docs.mjs`.

## 검증

백엔드(`recruit_back/recruit_backend/`에서, 테스트 설정에 감사 HMAC 값이 있어 `AUDIT_HMAC_SECRET` 불필요):

```powershell
# Windows PowerShell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.AdminRetentionControllerTest" --tests "com.shinyoung.recruit.service.Retention*" --tests "com.shinyoung.recruit.service.Purge*" --tests "com.shinyoung.recruit.service.ForcedPurge*" --tests "com.shinyoung.recruit.service.DataSubject*" --tests "com.shinyoung.recruit.service.RetentionPurgeScheduler*" --tests "com.shinyoung.recruit.service.ApplicationPiiPurgeServiceTest" --tests "com.shinyoung.recruit.domain.repository.RetentionScheduleSettingRepositoryTest" --tests "com.shinyoung.recruit.domain.repository.ApplicationPiiPurgeRepositoryTest" --no-daemon
```

```bash
# Linux
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "com.shinyoung.recruit.controller.AdminRetentionControllerTest" --tests "com.shinyoung.recruit.service.Retention*" --tests "com.shinyoung.recruit.service.Purge*" --tests "com.shinyoung.recruit.service.ForcedPurge*" --tests "com.shinyoung.recruit.service.DataSubject*" --tests "com.shinyoung.recruit.service.RetentionPurgeScheduler*" --tests "com.shinyoung.recruit.service.ApplicationPiiPurgeServiceTest" --tests "com.shinyoung.recruit.domain.repository.RetentionScheduleSettingRepositoryTest" --tests "com.shinyoung.recruit.domain.repository.ApplicationPiiPurgeRepositoryTest" --no-daemon
```

`ActivityLogService`·`AuditMetadata` 변경 시 이 카드의 `RETENTION_*`·`PURGE_*` 호출부도 영향을 받으므로 위 명령과 [privacy-audit-audit](privacy-audit-audit.md)의 검증을 함께 돌린다.

프론트(`recruit_front/`에서, "### 프론트" 파일 지도 참고):

```bash
npm run type-check
```

## 함정·결정

- 소유 컨트롤러 fix 커밋 없음(`c3d93ab` 통합 이전은 아카이브). `9f20a74`: Purge 테스트 `cleanUp` 수동 테이블 목록 불일치 → 정리 중단·연쇄 실패(테이블 추가·삭제 시 목록 갱신). `98d33b9`·`4cca804`: 새 PII 컬럼을 파기 쿼리에 뒤늦게 반영 — **파기 쿼리에 없는 PII는 조용히 남는다**.
- `recruit_back/recruit_backend/docs/adr/0005-retention-purge-mode-tombstone-anonymization-binary-deletion.md` — tombstone 익명화 + 바이너리 물리삭제(crypto-shred·전면 hard delete 기각).
- `recruit_back/recruit_backend/docs/adr/0007-privacy-admin-role-separation.md` — 파기·민감 감사는 `ROLE_PRIVACY_ADMIN`(RECRUIT 재사용 금지). 매핑이 없으면 파기 불가(안전 기본값).
- **직무 분리 구멍(코드 확인)**: `/api/admin/role-mappings/**` 전용 매처가 없어 broad `/api/admin/**`(ADMIN·RECRUIT)로 열려 있다. `ROLE_RECRUIT_ADMIN`이 자기에게 `ROLE_PRIVACY_ADMIN`을 매핑하고 재로그인하면 파기 권한을 얻는다(ADR 0007 우회, 설계 시 승인된 현 상태 — [role-menu](role-menu.md)). 막으려면 role-mappings 쓰기 매처를 broad 앞에 추가.
- reconcile로 승격돼도 원 batch의 `pendingCount`·`purgedCount`·`status`는 그대로다. 잔여는 `PURGE_PENDING` 지원서 수로 본다.
- anchor를 과거로 잘못 넣으면 다음 execute에서 **비가역 파기**된다. 확정 후 dry-run으로 확인.
- execute 일괄은 건수 상한 없이 요청 스레드에서 동기 처리. dry-run은 실행마다 지원서 수만큼 item이 쌓인다.
- 정책 이력이 필요하면 삭제 대신 `enabled:false`. `AuditHmac`(이 카드의 "파기 범위" 표가 `certificateNumber`·`filenameHash`에 쓴다)와 `AUDIT_HMAC_SECRET`는 [privacy-audit-audit](privacy-audit-audit.md) 소유.
- 최종 전형이 1개가 아닌 공고의 지원서는 `WITHDRAWN` 외에는 영원히 파기되지 않는다(전형 설정 화면이 경고, [stage-result](stage-result.md)).
- 로그인 ID 정책 미결: 이메일=loginId 채택 시 `User.loginId`가 PII가 되어 파기 범위 재검토.
- 미사용 슬롯: `PurgeResult.PARTIAL_*`, `PurgeTriggerType.FORCED_PURGE`(`PurgeTriggerType.DATA_SUBJECT_REQUEST`는 강제 파기로 이제 쓰인다). 감사 관련 enum(`ActorType`·`AuditActionResult`·`AuditReasonCode`·`AuditTargetType`)의 미사용 슬롯은 [privacy-audit-audit](privacy-audit-audit.md).
- **엔티티와 운영 수동 DDL의 불일치는 테스트로 검증되지 않는다.** 테스트는 `ddl-auto: create-drop`으로 엔티티에서 스키마를 매번 재생성하므로 `recruit_back/recruit_backend/docs/ops/*.sql`은 실행되지 않는다. 운영 반영 전 사람이 대조해야 한다.
- 수동 DDL(ddl-auto validate/none 운영 DB, 순서대로. `activity_log` DDL은 [privacy-audit-audit](privacy-audit-audit.md)): `recruit_back/recruit_backend/docs/ops/phase-09c-retention-ddl.sql`(정책·보류·batch·item, `hiring_ended_at`, 지원서 파기 marker), `recruit_back/recruit_backend/docs/ops/phase-09d-1-purge-execute-ddl.sql`(날짜 PII nullable·execute 집계), `recruit_back/recruit_backend/docs/ops/phase-09d-2-attachment-saga-ddl.sql`(첨부 saga 컬럼, 주석 처리된 `DELETED`→`SOFT_DELETED` 이관), `recruit_back/recruit_backend/docs/ops/phase-09e-reconciliation-ddl.sql`(`binary_delete_failure_code`), `recruit_back/recruit_backend/docs/ops/phase-10-retention-schedule-ddl.sql`(`retention_schedule_setting`). `purge_batch`·`purge_job_item`은 DELETE 권한 금지.
