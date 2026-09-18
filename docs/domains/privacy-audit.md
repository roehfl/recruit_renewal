# 개인정보 보존·파기·감사 (`privacy-audit`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [attachment](attachment.md)(첨부 저장소) · [role-menu](role-menu.md)(역할 매핑) · [auth-account](auth-account.md)(`SecurityConfig`·`Applicant` 익명화) · [application](application.md)(`JobApplication`) · [application-sections](application-sections.md)(섹션 엔티티) · [job-posting](job-posting.md)(`hiringEndedAt`) · [stage-result](stage-result.md) · [interview](interview.md) · [admin-application](admin-application.md)(반출 감사)

## 요약

- **감사 로그**: `ActivityLog`(append-only). 여러 도메인이 `ActivityLogService` 2경로로 기록, 관리자는 `/admin/audit/**`로 조회.
- **보존**: 정책(`RetentionPolicy`, 전역+공고별), 보류(`RetentionHold`), 기산점(`JobPosting.hiringEndedAt`) — 전부 수동 관리.
- **파기**: dry-run(산정만) → execute(재검증 후 tombstone 익명화 + 첨부 바이너리 물리삭제) → reconcile(바이너리 잔여 재처리).
- 관리자 API만 있다. **프론트 화면·스케줄러 없음**.
- 권한(ADR 0007): 쓰기·실행·민감 조회 = `ROLE_PRIVACY_ADMIN`, 조회·dry-run = `ROLE_RECRUIT_ADMIN`도. `ROLE_ADMIN`(IT)만 있으면 403.

## 용어

| 용어 | 뜻 |
|---|---|
| `ActivityLog` | 감사 행. 수정·삭제 없음(정정은 새 이벤트). 지원자 원문 PII는 없지만 `actorId`·ip·ua·`applicationId`가 있어 완전 PII-free는 아니다 |
| 감사 이벤트 | 정보 반출(엑셀·PDF·첨부 다운로드), 핵심 관리자 변경, 보존·파기만. 반출은 fail-close(감사 커밋 후에만 반환) |
| `actorRoleSnapshot` | 행위 시점 authority 목록(쉼표 연결) |
| `applicantRefHash` | `HMAC_SHA256(AUDIT_HMAC_SECRET, "APPLICANT:"+applicantId)` — 파기 후에도 같은 지원자를 가명으로 묶는다 |
| 파기(purge) | 원문 PII 비가역 소거 + 비식별 tombstone 유지 + 첨부 바이너리 물리삭제. 행 삭제 아님 |
| placeholder | NOT NULL 문자열 PII 대체값 `__PURGED__` |
| ref-count 익명화 | 지원자의 **모든** 지원서가 파기 대상일 때만 `Applicant` 공통 PII 제거 |
| anchor | 보존 기산점. 기본 `hiringEndedAt`(채용 실질 종료, 수동 확정). `closedAt`은 정책이 `CLOSED_AT`일 때만 |
| 전역 / override 정책 | `RetentionPolicy.jobPostingId` null / 공고 id |
| ledger | `PurgeBatch`(실행 1회)·`PurgeJobItem`(지원서별). delete 금지 |
| drift | dry-run ELIGIBLE이 execute 재검증에서 탈락(`SKIPPED`) |
| `PurgeResult` | `PURGE_PENDING`(PII 제거, 바이너리 소멸 미확인) · `PURGED`(둘 다 완료, "DB PURGED + 파일 잔존" 불허). `JobApplicationStatus`와 별개 |
| saga | ① item tx(PII 제거·`BINARY_DELETE_PENDING`) → ② tx 밖 물리 삭제+재확인 → ③ 새 tx 확정 |
| 미구현 | forced purge(정보주체 요청), 감사 로그 자체 수명 정책, 스케줄 실행 |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/AdminRetentionController.java` | `/admin/retention/**` 13개 |
| controller | `{BE}/controller/AdminAuditController.java` | `/admin/audit/activities` 검색·단건, ip/ua 마스킹 |
| service | `{BE}/service/ActivityLogService.java` | 감사 기록 2경로·문자열 정제·HMAC·metadata 직렬화 |
| service | `{BE}/service/Audit*` | `AuditEvent`(기록 요청)·`AuditMetadata`(sealed, permits 11)·`AuditActorContext`·`AuditRequestContextResolver`(행위자 해석)·`AuditActivityReadService`(조회 가드) |
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
| entity | `{BE}/domain/entity/ActivityLog.java` | 감사 행(BaseEntity 미상속) |
| entity | `{BE}/domain/entity/Purge*` | `PurgeBatch`(모드·상태·집계)·`PurgeJobItem`(지원서별 결과) |
| entity | `{BE}/domain/entity/Retention*` | `RetentionHold`·`RetentionPolicy`(`isEffectiveAt`·`overlapsWith`) |
| repository | `{BE}/domain/repository/ActivityLogRepository.java` | save·조회·검색만 |
| repository | `{BE}/domain/repository/ApplicationPiiPurgeRepository.java` | 파기 JPQL bulk update — 필드 분류의 단일 출처 |
| repository | `{BE}/domain/repository/Purge*` | batch·item — save·조회만(delete 없음) |
| repository | `{BE}/domain/repository/Retention*` | active hold, scope별 enabled 정책 |
| dto | `{BE}/dto/request/Retention*` | 정책·보류·anchor 요청 |
| dto | `{BE}/dto/request/PurgeExecuteRequest.java` | execute 요청 |
| dto | `{BE}/dto/response/Retention*` | 정책·보류·anchor 응답 |
| dto | `{BE}/dto/response/Purge*` | batch·batch 상세·item·reconcile 응답 |
| dto | `{BE}/dto/response/AuditActivityResponse.java` | 감사 행 + 마스킹 |
| enum | `{BE}/enumeration/Audit*` | `AuditActionType`(20)·`AuditActionResult`(5)·`AuditTargetType`(10)·`AuditReasonCode`(13) |
| enum | `{BE}/enumeration/Purge*` | `PurgeBatchMode`·`PurgeBatchStatus`·`PurgeItemStatus`·`PurgeResult`·`PurgeTriggerType` |
| enum | `{BE}/enumeration/ActorType.java` | `EMPLOYEE`·`SYSTEM`·`APPLICANT`·`ANONYMOUS` |
| enum | `{BE}/enumeration/RetentionBaselineType.java` | `HIRING_ENDED_AT`·`CLOSED_AT` |
| exception | `{BE}/exception/InvalidAuditQueryException.java` | 400 |
| exception | `{BE}/exception/ActivityLogNotFoundException.java` | 404 |
| exception | `{BE}/exception/InvalidActivityLogException.java` | 감사 행 필수값 위반 — **핸들러 없음 → 500** |
| exception | `{BE}/exception/InvalidRetention*` | Policy·Hold·Request 400 |
| exception | `{BE}/exception/Retention*NotFoundException.java` | Policy·Hold 404 |
| exception | `{BE}/exception/PurgeBatchNotFoundException.java` | 404 |
| config | `{BE}/config/AuditConfig.java` | HMAC 키 주입·누락 시 기동 실패 |
| config | `{BE}/common/hash/AuditHmac.java` | HMAC-SHA256 hex |
| test | `{BT}/controller/AdminAuditControllerTest.java` | 권한·마스킹·가드 |
| test | `{BT}/controller/AdminRetentionControllerTest.java` | 권한 행렬·validation |
| test | `{BT}/service/ActivityLogServiceTest.java` | 2경로 롤백·정제·HMAC |
| test | `{BT}/service/Audit*` | 조회 가드, metadata allowlist |
| test | `{BT}/service/Retention*` | 정책 선택, 판정 순서, dry-run |
| test | `{BT}/service/Purge*` | execute→saga, reconcile |
| test | `{BT}/service/ApplicationPiiPurgeServiceTest.java` | 필드별 tombstone |
| test | `{BT}/domain/repository/ActivityLogRepositoryTest.java` | 매핑·필수값 |
| test | `{BT}/common/hash/AuditHmacTest.java` | HMAC |
| test | `{BT}/config/AuditConfigTest.java` | 키 누락·fallback·`prod` |

### 프론트

없음(`recruit_front/src` grep 확인). `ROLE_PRIVACY_ADMIN`만 가진 사용자는 `/admin` 라우트 가드(`ADMIN_ROLES`)에 막힌다. 화면을 만들면 여기에 등록한다.

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
| 🟢 | GET | /admin/audit/activities | query 필터·`from`·`to`·`page`(0)·`size`(20) | `PageResponse<AuditActivityResponse>` | R·P |
| 🟢 | GET | /admin/audit/activities/{id} | 없음 | `AuditActivityResponse` | R·P |

### 엔드포인트 상세

- 전 엔드포인트 **FE 미사용**(`recruit_front/src/api` grep 0건). 코드 기준 🟢.
- POST는 actor를 `CurrentEmployeeService.getCurrentEmployeeActor`로 얻는다(임직원 principal 아니면 401/403). enum query 값 오류는 400 `"Invalid request."`. 목록은 공통 `PageResponse`(`content`·`page`·`totalElements` 등).
- **감사 조회**: query `actorId`(완전일치·trim), `actionType`·`actionResult`·`targetType`(enum 이름), `jobPostingId`, `applicationId`, `from`·`to`(ISO `yyyy-MM-ddTHH:mm:ss`). 응답 = `ActivityLog` 컬럼(`traceId` 제외, `metadataJson`은 문자열). `ipAddress`·`userAgent`는 PRIVACY만 원문, 그 외 `"***"`(null은 null). 단건 없음 404.
- **정책**: `{ jobPostingId?, retentionPeriodDays*(≥1), baselineType*, enabled*, effectiveFrom?, effectiveTo? }` ↔ 응답 같은 필드 + `id`. 수정 시 `jobPostingId` 변경 400. 없는 policyId·jobPostingId 404.
- **보류**: `{ applicationId*, reason*(≤1000) }` → `{ id, applicationId, reason, heldBy, createdAt, releasedAt, releasedBy, active }`. `reason`은 민감할 수 있는 자유 텍스트라 **목록도 PRIVACY 전용**. 없는 지원서·holdId 404, active 중복·이미 해제 400.
- **anchor**: `hiringEndedAt`(LocalDateTime) 필수, 재확정 허용, 없는 공고 404.
- **batch**: `{ batch, items[] }`. batch = `PurgeBatch` 컬럼(모드·상태·시각·`requestedBy`·`sourceDryRunBatchId`·집계 8종), item = `{ id, applicationId, jobPostingId, status, reasonCode }`. dry-run items = 전 지원서. PII 없음. 목록 `size` 1..100 밖·`page<0` 400.
- **execute**(batch 생성 전 검사): `confirm`≠true 400, `sourceDryRunBatchId`(일괄)·`applicationId`(단건) 중 정확히 하나 아니면 400, 근거 batch 없음 404·`COMPLETED` `DRY_RUN` 아님 400, 단건 지원서 없음 404.
- **reconcile**: `limit`은 1..1000으로 clamp(400 없음) → `{ reconciledAt, scannedCount, promotedCount, stillPendingCount, errorCount }`. `scannedCount == limit`이면 재호출.

## 규칙·불변식

**감사 기록 (`ActivityLogService`)**
- 2경로(ADR 0006): `recordInCurrentTx`(REQUIRED — 커밋된 변경의 성공 증적, 감사 실패 시 비즈니스도 롤백) / `recordRequiresNew`(실패·충돌 증적과 반출 fail-close, 비즈니스 롤백과 무관하게 남음). afterCommit·AOP 금지, 같은 빈 안 호출 금지(self-invocation이면 REQUIRES_NEW 무효). ({BE}/service/ActivityLogService.java — recordInCurrentTx, recordRequiresNew)
- 필수 `occurredAt`(Clock)·`actorType`·`actionType`·`actionResult`·`targetType`, `EMPLOYEE`·`APPLICANT`는 `actorId`도. 위반 = `InvalidActivityLogException`(500). ({BE}/domain/entity/ActivityLog.java — validateRequired, {BE}/service/ActivityLogService.java — validateActorIdPresence)
- 요청 유래 문자열은 CR/LF/TAB→공백, trim, 컬럼 길이 절단, 공백뿐이면 null(긴 입력으로 insert 실패 방지). `correlationId`가 비면 MDC 값, `applicantId`는 HMAC 입력으로만, `traceId`는 항상 null. ({BE}/service/ActivityLogService.java — toEntity, safe)
- 리포지토리는 `save`·`findById`·`count`·`search`만 노출. `AuditMetadata` permits 11개와 record 필드는 테스트가 allowlist로 고정(email·phone·ci·sourcefilename 등 이름 금지). ({BE}/domain/repository/ActivityLogRepository.java, {BT}/service/AuditMetadataContractTest.java)
- 서비스 계층 행위자 = `AuditRequestContextResolver.resolve(actor)`: principal username·authority → 없으면 넘긴 actor → 없으면 `ANONYMOUS`. ip = `getRemoteAddr()`(프록시 뒤면 프록시 주소). ({BE}/service/AuditRequestContextResolver.java — resolve)

**감사 호출 규약** — 지원자 자가 행위는 기록하지 않는다.

| actionType | 기록 위치 | 경로 |
|---|---|---|
| `EXPORT_APPLICATIONS`·`_STAGE_RESULTS`·`_INTERVIEWS`·`_EVALUATIONS`·`_STAGE_RESULT_TEMPLATE`, `APPLICATION_PDF` | `ExportAuditLogger`·`PdfAuditLogger`([admin-application](admin-application.md)) | REQUIRES_NEW, fail-close |
| `STAGE_RESULT_UPLOAD` | `UploadAuditLogger`([stage-result](stage-result.md)) | 적용 in-tx / 거부 FAILURE·충돌 CONFLICT는 REQUIRES_NEW |
| `STAGE_RESULT_CORRECT`·`_ANNOUNCE`·`_CONFIRM` | `StageResultService`·`StageService`([stage-result](stage-result.md)) | in-tx |
| `EVALUATION_REOPEN` | `InterviewEvaluationAdminService`([interview](interview.md)) | in-tx |
| `ATTACHMENT_ADMIN_DOWNLOAD` / `_DELETE` | `AdminApplicationAttachmentController` / `ApplicationAttachmentDeleteService`([attachment](attachment.md)) | REQUIRES_NEW fail-close / in-tx |
| `RETENTION_POLICY_UPDATE`·`RETENTION_HOLD_SET`·`_RELEASE`·`RETENTION_ANCHOR_SET`·`PURGE_SCAN` | 이 카드 서비스 | in-tx |
| `PURGE_EXECUTE` | `PurgeBatchLifecycleService` | 완료·실패용 REQUIRES_NEW tx 안 in-tx |
| `PURGE_RECONCILE` | `PurgeReconciliationService` | REQUIRES_NEW 2건(시작·요약) |

**감사 조회**
- size 1..100, page ≥0, `to` 기본 now, `from` 기본 `to`−30일, `from>to`·90일 초과 400(정확히 90일 허용). 정렬 `occurredAt desc, id desc`. ({BE}/service/AuditActivityReadService.java — search, validateRange)
- ip·ua 원문은 `ROLE_PRIVACY_ADMIN`만(principal 없으면 마스킹). ({BE}/controller/AdminAuditController.java — includeSensitive)

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
| `JobApplication` | `applicantNameSnapshot` ph, `purgeResult`·`purgeBatchId`·`purgedAt` |
| `ApplicationAttachment` | `originalFileName` ph, `filenameHash` = `AuditHmac("FILE_NAME:"+원본명)`, 삭제자·사유 null |
| `Applicant`(ref-count 0) | `purgePersonalData` — loginId·이름·이메일·비밀번호·전화·ci null, `ciHash` = `PURGED:`+UUID([auth-account](auth-account.md)) |

**saga ②③·reconcile** ({BE}/service/AttachmentPurgeSagaService.java — completeBinaryDeletion, {BE}/service/PurgeItemProcessor.java — finalizeBinaryDeletion, {BE}/service/PurgeReconciliationService.java — reconcile)
- ② 대상 = `BINARY_DELETE_PENDING`·`_FAILED` 첨부. 삭제 후 `exists` 재확인, 이미 없음 = 성공, 경로 없음 = 실패(`EMPTY_STORAGE_PATH`). 그 외 실패 코드 `DELETE_FAILED`·`STILL_EXISTS`·`EXCEPTION`·`UNKNOWN`.
- ③ 성공 첨부 `BINARY_DELETED`(`storagePath` null), 실패 `BINARY_DELETE_FAILED`. **전부** 성공일 때만 지원서 `PURGED`+item PENDING→PURGED(item이 없으면 예외 롤백).
- reconcile: `PURGE_PENDING` 지원서 id asc `limit`건마다 원 `purgeBatchId`로 ②③. `purgeBatchId` null·단건 예외는 errorCount만 올리고 계속. 감사 시작(`reconcile started: scanned=N`)+요약(stillPending·error 있으면 FAILURE) — 시작만 있으면 중단된 sweep.

**권한**: API 표 권한 = `{BE}/config/SecurityConfig.java`([auth-account](auth-account.md) 소유)의 메서드별 좁은 매처. 전부 broad `/api/admin/**`보다 **앞**, GET `holds/**`는 GET `retention/**`보다 앞 — 순서가 보안 요구사항.

**감사 HMAC 키** ({BE}/config/AuditConfig.java — auditHmac)
- `audit.hmac-secret` = env `AUDIT_HMAC_SECRET`. 비면 기동 실패. 단 `audit.allow-fallback-secret=true`(env `AUDIT_ALLOW_FALLBACK_SECRET`)이고 active profile에 `prod`가 없으면 비운영 대체 값 + 경고, `prod`면 flag가 true여도 거부. 테스트 값은 `recruit_back/recruit_backend/src/test/resources/application.yaml`. 실제 키 값은 문서·코드에 쓰지 않는다.
- `AuditHmac` 입력 접두 `APPLICANT:`·`CERT_NO:`·`FILE_NAME:`. ci·email·phone 원문 입력 금지. ({BE}/common/hash/AuditHmac.java)

## 변경 레시피

### 다른 도메인에 새 감사 이벤트 추가
1. `{BE}/enumeration/AuditActionType.java`(필요하면 `AuditTargetType`·`AuditReasonCode`)에 값 추가. VARCHAR라 DDL 불필요(50·40자 이내).
2. 커밋된 변경 = 그 서비스 `@Transactional` 안 `recordInCurrentTx`, 실패·충돌·반출 = `recordRequiresNew`(반출은 성공 후 산출물 반환). 서비스 계층이면 `AuditRequestContextResolver.resolve(actor)`.
3. metadata record → `{BE}/service/AuditMetadata.java` permits → `{BT}/service/AuditMetadataContractTest.java` `EXPECTED_COMPONENTS`. 지원자 PII·원본 파일명 금지.
4. 그 도메인 테스트 + 아래 검증, "감사 호출 규약" 표 갱신, `node tools/check-docs.mjs`.

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
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.AdminAuditControllerTest" --tests "com.shinyoung.recruit.controller.AdminRetentionControllerTest" --tests "com.shinyoung.recruit.service.ActivityLogServiceTest" --tests "com.shinyoung.recruit.service.Audit*" --tests "com.shinyoung.recruit.service.Retention*" --tests "com.shinyoung.recruit.service.Purge*" --tests "com.shinyoung.recruit.service.ApplicationPiiPurgeServiceTest" --tests "com.shinyoung.recruit.domain.repository.ActivityLogRepositoryTest" --tests "com.shinyoung.recruit.common.hash.AuditHmacTest" --tests "com.shinyoung.recruit.config.AuditConfigTest" --no-daemon
```

```bash
# Linux
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "com.shinyoung.recruit.controller.AdminAuditControllerTest" --tests "com.shinyoung.recruit.controller.AdminRetentionControllerTest" --tests "com.shinyoung.recruit.service.ActivityLogServiceTest" --tests "com.shinyoung.recruit.service.Audit*" --tests "com.shinyoung.recruit.service.Retention*" --tests "com.shinyoung.recruit.service.Purge*" --tests "com.shinyoung.recruit.service.ApplicationPiiPurgeServiceTest" --tests "com.shinyoung.recruit.domain.repository.ActivityLogRepositoryTest" --tests "com.shinyoung.recruit.common.hash.AuditHmacTest" --tests "com.shinyoung.recruit.config.AuditConfigTest" --no-daemon
```

`ActivityLogService`·`AuditMetadata` 변경 시 호출부 테스트 `StageAuditInstrumentationTest`([stage-result](stage-result.md)), `ExportAuditLoggerTest`([admin-application](admin-application.md))도 돌린다.

프론트 코드는 없다. 화면을 추가했다면 `recruit_front/`에서:

```bash
npm run type-check
```

## 함정·결정

- 소유 컨트롤러 fix 커밋 없음(`c3d93ab` 통합 이전은 아카이브). `9f20a74`: Purge 테스트 `cleanUp` 수동 테이블 목록 불일치 → 정리 중단·연쇄 실패(테이블 추가·삭제 시 목록 갱신). `98d33b9`·`4cca804`: 새 PII 컬럼을 파기 쿼리에 뒤늦게 반영 — **파기 쿼리에 없는 PII는 조용히 남는다**.
- `recruit_back/recruit_backend/docs/adr/0005-retention-purge-mode-tombstone-anonymization-binary-deletion.md` — tombstone 익명화 + 바이너리 물리삭제(crypto-shred·전면 hard delete 기각).
- `recruit_back/recruit_backend/docs/adr/0006-audit-transaction-policy.md` — 커밋변경 in-tx / 실패계열 REQUIRES_NEW / 반출 fail-close. 감사 장애가 업무를 막는 것은 의도된 것, afterCommit 전환은 ADR 재검토 필요.
- `recruit_back/recruit_backend/docs/adr/0007-privacy-admin-role-separation.md` — 파기·민감 감사는 `ROLE_PRIVACY_ADMIN`(RECRUIT 재사용 금지). 매핑이 없으면 파기 불가(안전 기본값).
- **직무 분리 구멍(코드 확인)**: `/api/admin/role-mappings/**` 전용 매처가 없어 broad `/api/admin/**`(ADMIN·RECRUIT)로 열려 있다. `ROLE_RECRUIT_ADMIN`이 자기에게 `ROLE_PRIVACY_ADMIN`을 매핑하고 재로그인하면 파기 권한을 얻는다(ADR 0007 우회, 설계 시 승인된 현 상태 — [role-menu](role-menu.md)). 막으려면 role-mappings 쓰기 매처를 broad 앞에 추가.
- reconcile로 승격돼도 원 batch의 `pendingCount`·`purgedCount`·`status`는 그대로다. 잔여는 `PURGE_PENDING` 지원서 수로 본다.
- anchor를 과거로 잘못 넣으면 다음 execute에서 **비가역 파기**된다. 확정 후 dry-run으로 확인.
- execute 일괄은 건수 상한 없이 요청 스레드에서 동기 처리. dry-run은 실행마다 지원서 수만큼 item이 쌓인다.
- `InvalidActivityLogException`은 핸들러가 없어 500(ApiResponse 형식 아님).
- 정책 이력이 필요하면 삭제 대신 `enabled:false`. `AUDIT_HMAC_SECRET`를 바꾸면 이후 해시가 이전 값과 연결되지 않는다. `ActivityLog`는 파기되지 않는다.
- 최종 전형이 1개가 아닌 공고의 지원서는 `WITHDRAWN` 외에는 영원히 파기되지 않는다(전형 설정 화면이 경고, [stage-result](stage-result.md)).
- 로그인 ID 정책 미결: 이메일=loginId 채택 시 `User.loginId`가 PII가 되어 파기 범위 재검토.
- 미사용 슬롯: `ActorType.SYSTEM`·`APPLICANT`, `AuditActionResult.DENIED`·`SKIPPED`, `AuditReasonCode.AUTH_DENIED`·`BINARY_DELETE_FAILED`, `AuditTargetType.JOB_APPLICATION`, `PurgeResult.PARTIAL_*`, `PurgeTriggerType` 중 `RETENTION` 외. 스케줄 실행·forced purge를 만들려면 SYSTEM actor 정책이 먼저다(`requireActor`가 빈 actor 거부).
- 수동 DDL(ddl-auto validate/none 운영 DB, 순서대로): `recruit_back/recruit_backend/docs/ops/phase-09a-activity-log-ddl.sql`(`activity_log`), `recruit_back/recruit_backend/docs/ops/phase-09c-retention-ddl.sql`(정책·보류·batch·item, `hiring_ended_at`, 지원서 파기 marker), `recruit_back/recruit_backend/docs/ops/phase-09d-1-purge-execute-ddl.sql`(날짜 PII nullable·execute 집계), `recruit_back/recruit_backend/docs/ops/phase-09d-2-attachment-saga-ddl.sql`(첨부 saga 컬럼, 주석 처리된 `DELETED`→`SOFT_DELETED` 이관), `recruit_back/recruit_backend/docs/ops/phase-09e-reconciliation-ddl.sql`(`binary_delete_failure_code`). `activity_log`는 앱 계정에 INSERT·SELECT만, `purge_batch`·`purge_job_item`은 DELETE 권한 금지.
