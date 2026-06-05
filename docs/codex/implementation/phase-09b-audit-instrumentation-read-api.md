# Phase 09b — 기존 로그 흡수 + 핵심 관리자 변경 audit + read API

## Phase Summary

- Date: 2026-06-05
- Work type: implementation (설계: `docs/codex/design/phase-09-privacy-purge-audit-retention-design.md` §6 slice 9b, ADR-0006/0007)
- Goal: 09a foundation 위에 ① 기존 SLF4J 감사 로거(Export/Pdf/Upload)를 `ActivityLogService` adapter 로 흡수(dual-write — **DB가 source of truth**, SLF4J 보조), ② 정보 반출 **fail-close**(temp file 누수 방지 포함), ③ 핵심 관리자 변경 계측(StageResult 정정/발표/확정, evaluation reopen, 첨부 admin download/delete), ④ typed `AuditMetadata`(sealed) 도입, ⑤ admin audit **read API**(권한별 마스킹/원문 projection + read 가드).

## Implemented Scope

### A — typed AuditMetadata (sealed)

- `AuditMetadata` 를 `sealed interface` 로 좁히고 구체 record 7종 고정: `ExportMetadata`, `PdfMetadata`, `UploadMetadata`, `UploadConflictMetadata`, `StageResultChangeMetadata`, `EvaluationReopenMetadata`, `AttachmentAdminMetadata`. 직렬화는 `ActivityLogService` 내부에서만(리뷰 #3).
- **사용자 제공 파일명 원문 금지**(리뷰 2차 #2): upload metadata 는 `sourceFileNameHash`(SHA-256) + `sourceFileExtension` 만. SLF4J 보조 로그에서도 파일명 원문 제거.
- `AuditActionType.EXPORT_STAGE_RESULT_TEMPLATE` 추가(upload-template 다운로드는 별개 dataset 의 egress).
- 9c~9e metadata record 는 해당 슬라이스에서 permits 에 추가.

### B — 기존 로거 흡수(dual-write) + egress fail-close

- `ExportAuditLogger.logExport()`: **ActivityLog 먼저 기록**(`recordRequiresNew`, 실패 시 throw = fail-close) → SLF4J 보조. datasetType→`AuditActionType` 고정 매핑(미등록 dataset 즉시 실패). `targetType=EXPORT_DATASET`, `targetId=datasetType`, filters 의 `jobPostingId` denormalize.
- `PdfAuditLogger.logApplicationPdf()`: 동일 dual-write + fail-close. `APPLICATION_PDF`, applicationId/jobPostingId 컬럼 + `PdfMetadata`.
- `UploadAuditLogger`: outcome 별 트랜잭션 분기(ADR-0006) —
  - `APPLIED` → `recordInCurrentTx`(**commit 트랜잭션에 join, 원자적**) — 호출 위치를 `StageResultUploadService.commit()` 내부로 이동.
  - `REJECTED_VALIDATION` → `recordRequiresNew` + `FAILURE/VALIDATION_FAILED`.
  - `REJECTED_STALE` → `recordRequiresNew` + `CONFLICT/VERSION_MISMATCH`.
  - 낙관적 잠금 충돌(`logUploadConflict`, controller catch) → `recordRequiresNew` + `CONFLICT/VERSION_MISMATCH` + `UploadConflictMetadata`.
- **temp file 누수 방지(리뷰 2차 #3)**: `AdminExportController` 4개 export + `StageResultUploadController.uploadTemplate` 을 `try { 감사 → 응답 } catch { Files.deleteIfExists(temp) → rethrow }` 패턴으로 변경. PDF 는 메모리 byte[] 라 정리 불필요(감사가 응답 전 — fail-close 동일).

### C — 핵심 관리자 변경 계측 (in-tx, ADR-0006)

| 행위 | 위치 | actionType | targetType/targetId | 비고 |
| --- | --- | --- | --- | --- |
| StageResult 수동 정정(단건) | `StageResultService.updateResult` | `STAGE_RESULT_CORRECT` | STAGE_RESULT / resultId | applicationId·jobPostingId denormalize, `StageResultChangeMetadata(stageId, 1)`. 전후값은 CorrectionHistory 가 보유(중복 저장 금지) |
| StageResult 수동 정정(bulk) | `StageResultService.bulkUpdateResults(..., recordAudit=true)` | `STAGE_RESULT_CORRECT` | STAGE_RESULT / stageId | `StageResultChangeMetadata(stageId, n)`. **upload 경로는 recordAudit=false** 로 STAGE_RESULT_UPLOAD 와 이중 기록 방지 |
| 발표 | `StageService.announce` | `STAGE_RESULT_ANNOUNCE` | STAGE_RESULT / stageId | |
| 확정(close) | `StageService.close` | `STAGE_RESULT_CONFIRM` | STAGE_RESULT / stageId | |
| 평가 reopen | `InterviewEvaluationAdminService.reopen` | `EVALUATION_REOPEN` | INTERVIEW_EVALUATION / evaluationId | `EvaluationReopenMetadata(interviewId, previousSubmittedAt)`. SLF4J dual-write 유지 |
| 첨부 admin 삭제 | `ApplicationAttachmentDeleteService.deleteForAdmin` | `ATTACHMENT_ADMIN_DELETE` | APPLICATION_ATTACHMENT / attachmentId | `AttachmentAdminMetadata(physicalDeleteRequested)`. 삭제 사유 원문은 도메인만 보유. **지원자 자가 삭제는 계측 제외**(emission 은 EMPLOYEE/SYSTEM/ANONYMOUS 만) |
| 첨부 admin 다운로드 | `AdminApplicationAttachmentController.downloadAttachmentFile` | `ATTACHMENT_ADMIN_DOWNLOAD` | APPLICATION_ATTACHMENT / attachmentId | 정보 반출 — `recordRequiresNew` **fail-close**(감사 commit 전 바이너리 미반출). endpoint 에 `@AuthenticationPrincipal` + actor 검증 추가 |

- 서비스 계층 in-tx 계측을 위해 `AuditRequestContextResolver`(+`AuditActorContext`) 신설 — SecurityContext(actorId/권한 스냅샷) + RequestContext(ip/ua)를 시그니처 오염 없이 해석. principal 부재 시 서비스가 검증한 actor 파라미터 fallback(EMPLOYEE), 그것도 없으면 `ANONYMOUS`.
- `ActivityLogService` 검증 보강(9a 2차 리뷰 예고분): `actorType=EMPLOYEE/APPLICANT → actorId 필수`(위반 시 `InvalidActivityLogException`).

### D — admin audit read API

| Method | Path | 권한 |
| --- | --- | --- |
| GET | `/admin/audit/activities` | RECRUIT_ADMIN(마스킹) / PRIVACY_ADMIN(원문) |
| GET | `/admin/audit/activities/{id}` | 동일 |

- 검색 필터: `actorId`/`actionType`/`actionResult`/`targetType`/`jobPostingId`/`applicationId`/`from`/`to`(ISO DATE_TIME) + `page`/`size`. 최신순 고정.
- **read 가드(리뷰 #6)**: page size 최대 100(초과 400) · occurredAt 범위 최대 90일(초과 400, from>to 400) · 범위 미지정 시 default 최근 30일.
- **권한별 projection(ADR-0007)**: `ipAddress`/`userAgent` 는 `ROLE_PRIVACY_ADMIN` 만 원문, 그 외 `"***"` 마스킹. matcher 통과 후 컨트롤러에서 권한별 projection 으로 분기 — principal 부재 시 항상 마스킹(심층 방어).
- SecurityConfig: `GET /api/admin/audit/**` → `hasAnyAuthority(ROLE_RECRUIT_ADMIN, ROLE_PRIVACY_ADMIN)` narrow matcher 를 **broad `/api/admin/**` 보다 먼저** 등록(순서가 보안 요구사항 — PRIVACY_ADMIN 단독 권한도 접근 가능해짐).
- `ActivityLogRepository` 에 `search(...)` JPQL finder 추가(append-only 불변 — save/findById/count/search 만 노출).

## Not Implemented / Out of Scope

- 보존/파기 도메인(`RetentionPolicy`/`RetentionHold`/`PurgeBatch`/`hiringEndedAt`) — 9c~9e.
- `ROLE_PRIVACY_ADMIN` 의 `DeptRoleMapping` 운영 데이터 매핑(운영 협의) — 코드상 권한 분기만 구현.
- AOP blanket 접근 감사, ActivityLog 자체 lifecycle, forced purge, traceId(OTel) — 설계 범위 제외 승계.
- 응시자(APPLICANT) 자가행위 계측 — Phase 09 emission 정책상 제외.

## Changed Files

### New Files (main 13)

| File | Type |
|------|------|
| `service/ExportMetadata.java` `PdfMetadata.java` `UploadMetadata.java` `UploadConflictMetadata.java` `StageResultChangeMetadata.java` `EvaluationReopenMetadata.java` `AttachmentAdminMetadata.java` | AuditMetadata record 7종 |
| `service/AuditActorContext.java` | record (계측 컨텍스트) |
| `service/AuditRequestContextResolver.java` | Component |
| `service/AuditActivityReadService.java` | Service (read API) |
| `dto/response/AuditActivityResponse.java` | Response DTO (record, 마스킹 projection) |
| `controller/AdminAuditController.java` | Controller |
| `exception/InvalidAuditQueryException.java` `ActivityLogNotFoundException.java` | Exception (400/404) |

### Modified Files (main 13)

| File | Change |
|------|--------|
| `service/AuditMetadata.java` | sealed + permits 7종 |
| `enumeration/AuditActionType.java` | `EXPORT_STAGE_RESULT_TEMPLATE` 추가 |
| `service/ActivityLogService.java` | EMPLOYEE/APPLICANT actorId 필수 검증 |
| `service/ExportAuditLogger.java` / `PdfAuditLogger.java` / `UploadAuditLogger.java` | dual-write adapter 전환(DB 우선·fail-close / upload 는 outcome 별 tx 분기, 파일명 원문 제거) |
| `service/StageResultUploadService.java` | commit 내부에서 outcome 별 감사(in-tx), bulkUpdate `recordAudit=false` 호출 |
| `service/StageResultService.java` | updateResult/bulkUpdate `STAGE_RESULT_CORRECT` in-tx 계측(+`recordAudit` overload) |
| `service/StageService.java` | announce/close → `STAGE_RESULT_ANNOUNCE`/`STAGE_RESULT_CONFIRM` in-tx 계측 |
| `service/InterviewEvaluationAdminService.java` | reopen `EVALUATION_REOPEN` in-tx 계측 |
| `service/ApplicationAttachmentDeleteService.java` | deleteForAdmin `ATTACHMENT_ADMIN_DELETE` in-tx 계측 |
| `controller/AdminExportController.java` / `StageResultUploadController.java` | egress fail-close temp 정리(`deleteQuietly`), upload commit 감사 호출 service 로 이동 |
| `controller/AdminApplicationAttachmentController.java` | admin download `ATTACHMENT_ADMIN_DOWNLOAD` fail-close 계측(+principal/request 파라미터) |
| `domain/repository/ActivityLogRepository.java` | `search` JPQL finder |
| `exception/GlobalExceptionHandler.java` | `InvalidAuditQueryException`→400, `ActivityLogNotFoundException`→404 |
| `config/SecurityConfig.java` | `GET /api/admin/audit/**` narrow matcher(broad 보다 먼저) |

### Tests

| File | 구분 | 내용 |
|------|------|------|
| `controller/AdminAuditControllerTest` | 신규(10) | 마스킹/원문 projection 분리(목록+단건), default 30일 range, 명시 range, size 상한 400, range 상한 400, 404, 미인증 401, 무권한 403 — springSecurity filter chain |
| `service/AuditActivityReadServiceTest` | 신규(8) | default range 산정, size/range/from>to 가드, 마스킹/원문, 404, actorId trim 정규화 |
| `service/AuditMetadataContractTest` | 신규(3) | sealed permits 고정, record component allowlist 고정, **PII 의심 이름 금지**(metadataJson PII 금지 가드) |
| `service/StageAuditInstrumentationTest` | 신규(1) | 발표/확정/정정 in-tx 기록 실증(동적 접수기간 fixture — 기존 Stage 픽스처의 날짜 의존 사전-실패 회피) |
| `service/ActivityLogServiceTest` | 수정 | sealed 전환에 따라 SampleMeta → `ExportMetadata` 사용 |

## Class-by-Class Explanation (신규 핵심)

### AuditRequestContextResolver
- Package: `com.shinyoung.recruit.service` / Type: Component
- 책임: 서비스 계층 in-tx 계측용 actor/요청 컨텍스트 해석. SecurityContext principal(CustomUserDetails) → actorId+권한 스냅샷, RequestContextHolder → ip/ua. fallback: 서비스 검증 actor 파라미터(EMPLOYEE) → 없으면 ANONYMOUS.
- 관련: `AuditActorContext`, `ActivityLogService`, 계측된 5개 서비스.

### AuditActivityReadService
- Package: `service` / Type: Service (`@Transactional(readOnly = true)`)
- 책임: 감사 검색/단건 + read 가드(page≤100, range≤90일, default 30일) + 권한별 projection 위임.
- Key: `search(...)`, `getActivity(id, includeSensitive)`. 실패: `InvalidAuditQueryException`(400)/`ActivityLogNotFoundException`(404).

### AdminAuditController
- Package: `controller` / Type: Controller
- 책임: `GET /admin/audit/activities`(+`/{id}`). `includeSensitive` = principal 권한에 `ROLE_PRIVACY_ADMIN` 포함 여부(부재 시 false — 심층 방어).

### AuditActivityResponse
- Package: `dto.response` / Type: Response DTO(record)
- 책임: ActivityLog 전 컬럼 + `from(log, includeSensitive)` — ip/ua 를 `"***"` 마스킹 또는 원문.

### AuditMetadata record 7종
- Package: `service` / Type: record(sealed 구현)
- 책임: actionType 별 PII-free metadata allowlist. `AuditMetadataContractTest` 가 taxonomy 를 고정.

(수정 클래스 책임 변화는 위 Changed Files 표 참조 — 기존 책임 불변, 감사 계측만 추가.)

## API

| Method | Path | Purpose | Auth |
|--------|------|---------|------|
| GET | `/admin/audit/activities?actorId=&actionType=&actionResult=&targetType=&jobPostingId=&applicationId=&from=&to=&page=&size=` | 감사 로그 검색 | GET `/api/admin/audit/**` = RECRUIT_ADMIN·PRIVACY_ADMIN (ip/ua 는 PRIVACY 만 원문) |
| GET | `/admin/audit/activities/{id}` | 감사 단건 | 동일 |

기존 export/PDF/upload/첨부/stage 엔드포인트의 경로·응답 형태는 불변(내부 감사 동작만 변경).

## Entity Relationship Summary

- 스키마 변경 없음 — `activity_log` 테이블(09a)에 read finder 만 추가. FK 없음(denormalized key) 불변.

## Validation and Business Rules

1. dual-write 에서 **DB(ActivityLog)가 source of truth**, SLF4J 는 보조 — DB 기록 실패 시 SLF4J 도 남지 않고 예외 전파.
2. 정보 반출(export/PDF/admin download) = fail-close: 감사 insert 성공 전 반출물이 나가지 않는다. export temp xlsx 는 실패 시 즉시 삭제.
3. 커밋된 변경 성공 증적(upload APPLIED, 정정/발표/확정/reopen/첨부삭제) = in-tx(`recordInCurrentTx`) — 감사 실패 시 비즈니스 rollback. 실패/거부/충돌 = `recordRequiresNew`.
4. metadataJson = sealed typed record 만, PII-free(파일명 원문 금지 — hash+확장자), 컬럼과 중복 금지.
5. upload 와 수동 정정의 이중 기록 금지(`recordAudit=false`).
6. `actorType EMPLOYEE/APPLICANT → actorId 필수`. APPLICANT 자가행위 비계측.
7. read API: size≤100, range≤90일, default 최근 30일, ip/ua 는 PRIVACY_ADMIN 만 원문, 항상 최신순.
8. narrow audit matcher 가 broad admin matcher 보다 먼저(순서 = 보안 요구사항).

## Test Commands

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*AdminAuditControllerTest" --tests "*AuditActivityReadServiceTest" --tests "*AuditMetadataContractTest" --tests "*StageAuditInstrumentationTest" --tests "*ActivityLog*" --tests "*AuditHmacTest" --tests "*StageResultUploadControllerTest" --tests "*AdminExportControllerTest" --tests "*AdminExportRowCapTest" --tests "*ApplicationPdfControllerTest" --tests "*StageControllerTest" --tests "*StageResultControllerTest" --tests "*InterviewEvaluationAdminControllerTest" --tests "*ApplicationAttachmentDownloadControllerTest" --tests "*ApplicationAttachmentDeleteServiceTest" --no-daemon
```

> 테스트 yaml 의 `audit.hmac-secret` 은 09a 에서 주입 완료(별도 env 불필요).

## Test Results

- Result: **scoped 136 tests — 134 passed / 2 failed(기존 결함, 9b 무관)**

| Test class | Tests | Result |
|------------|-------|--------|
| `AdminAuditControllerTest`(신규) | 10 | passed |
| `AuditActivityReadServiceTest`(신규) | 8 | passed |
| `AuditMetadataContractTest`(신규) | 3 | passed |
| `StageAuditInstrumentationTest`(신규) | 1 | passed |
| `ActivityLogServiceTest` / `ActivityLogRepositoryTest` / `AuditHmacTest` (09a 회귀) | 10/2/5 | passed |
| `StageResultUploadControllerTest`(upload 계측 경로) | 18 | passed |
| `AdminExportControllerTest`/`AdminExportRowCapTest`/`ApplicationPdfControllerTest`(egress 경로) | 7/1/4 | passed |
| `StageResultControllerTest`(정정 계측 경로) | 16 | passed |
| `InterviewEvaluationAdminControllerTest`(reopen 경로) | 8 | passed |
| `ApplicationAttachmentDownloadControllerTest`/`ApplicationAttachmentDeleteServiceTest`(첨부 admin 경로) | 10/12 | passed |
| `ApplicationStageResultControllerTest` | 5 | passed |
| `StageControllerTest` | 16 중 **2 실패** | announce/close — **기존 날짜 의존 사전-실패**(접수기간 2026-05 하드코딩 픽스처가 `jobApplicationService.create` 에서 throw, 9b 변경 무관·이전부터 실패). 해당 계측 경로는 신규 `StageAuditInstrumentationTest`(동적 접수기간)로 실증 |

- 전체 회귀 미실행(프로젝트 규칙 — 명시 요청 시에만).

## Known Limitations

1. announce/close 의 기존 `StageControllerTest`/`StageServiceTest` 픽스처는 날짜 의존 사전-실패 지속 — 고정 Clock/동적 접수기간으로의 픽스처 하드닝은 별도 과제(메모리/9a 문서에 기록된 기존 한계).
2. `ROLE_PRIVACY_ADMIN` 부여 경로(DeptRoleMapping 데이터)는 운영 협의 후 세팅 필요 — 코드상 분기만 존재.
3. egress fail-close 의 "감사 실패 → temp 삭제" 경로는 단위 검증(코드 경로 단순) — ActivityLogService 강제 실패 통합 테스트는 미작성(모킹 침습 대비 효익 낮음 판단).
4. read API 의 `metadataJson` 은 저장된 JSON 문자열 그대로 반환(파싱/再구조화 없음) — PII-free 는 기록 시점 sealed allowlist 로 보장.
5. SLF4J 보조 로그는 dual-write 순서상 DB 성공 후에만 남는다 — DB 실패 시 SLF4J 부재는 의도된 동작(fail-close).
6. 대량 조회 가드는 page/range 상한까지 — rate limiting 은 별도.

## Next Phase Considerations

- **9c**: `RetentionPolicy`/`RetentionHold`/`JobPosting.hiringEndedAt` + eligibility scan + dry-run `PurgeBatch`. metadata permits 에 `RetentionPolicyChangeMetadata`/`PurgeBatchMetadata` 추가.
- 9c 의 `RETENTION_*`/`PURGE_SCAN` 계측은 본 슬라이스의 resolver/in-tx 패턴 재사용.
- ActivityLog 운영 DDL 은 09a 스크립트(`docs/codex/ops/phase-09a-activity-log-ddl.sql`) 그대로 — 9b 는 스키마 변경 없음.
