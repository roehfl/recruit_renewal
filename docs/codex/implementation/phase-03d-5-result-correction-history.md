# Phase 03d-5 Result Correction History Implementation

## Phase Summary

Phase 03d-5 implements post-announcement StageResult correction and append-only correction history.

The goal is to prevent announced results from being changed through the general update API and to make every post-announcement change auditable with a required reason.

Implemented APIs:

| Method | Path | Response |
|---|---|---|
| `POST` | `/admin/stages/{stageId}/results/{resultId}/correct` | `ApiResponse<AdminStageResultResponse>` |
| `GET` | `/admin/stages/{stageId}/results/{resultId}/histories` | `ApiResponse<List<StageResultCorrectionHistoryResponse>>` |

## Implemented Scope

- Added `StageResultCorrectionHistory` entity.
- Added append-only correction history repository.
- Added correction request and history response DTOs.
- Added `StageResultCorrectionService`.
- Added correction and history endpoints to `StageResultController`.
- Updated the latest `StageResult` row during correction.
- Persisted previous/new snapshots for status, score, comment, and decidedAt.
- Required correction reason through DTO validation and service validation.
- Allowed correction only when the owning Stage is `RESULT_ANNOUNCED` or `CLOSED`.
- Kept normal StageResult update policy unchanged: general update remains `IN_PROGRESS` only.
- Added service and controller tests.
- Updated design and implementation history documents.
- Added paired self-contained HTML report.

## Out of Scope

- Applicant-facing API changes.
- `ApplicantStageResultResponse` changes.
- Exposing correction history to applicants.
- Creating duplicate `StageResult` rows.
- Allowing normal update after announcement.
- Stage announce policy changes.
- `SecurityConfig` changes.
- Message or notification integration.
- Interview or evaluation aggregation.
- PUT, PATCH, DELETE correction APIs.
- Real admin identity integration for `correctedBy`.

## Changed Files

| Path | Type | Notes |
|---|---|---|
| `src/main/java/com/shinyoung/recruit/domain/entity/StageResultCorrectionHistory.java` | New | Append-only correction history entity |
| `src/main/java/com/shinyoung/recruit/domain/repository/StageResultCorrectionHistoryRepository.java` | New | History lookup by StageResult |
| `src/main/java/com/shinyoung/recruit/dto/request/StageResultCorrectionRequest.java` | New | Correction command request |
| `src/main/java/com/shinyoung/recruit/dto/response/StageResultCorrectionHistoryResponse.java` | New | Admin history response |
| `src/main/java/com/shinyoung/recruit/service/StageResultCorrectionService.java` | New | Correction command and history read service |
| `src/main/java/com/shinyoung/recruit/controller/StageResultController.java` | Modified | Added correction and histories endpoints |
| `src/test/java/com/shinyoung/recruit/service/StageResultCorrectionServiceTest.java` | New | Correction service tests |
| `src/test/java/com/shinyoung/recruit/controller/StageResultControllerTest.java` | Modified | Correction API contract tests |
| `docs/codex/implementation/phase-03d-5-result-correction-history.md` | New | Implementation reference |
| `docs/codex/reports/phase-03d-5-result-correction-history.html` | New | Human-readable report |
| `docs/codex/design/phase-03d-stage-result-design.md` | Modified | Phase 03d-5 implementation note |
| `docs/codex/design/phase-03-application-design.md` | Modified | Phase 03d-5 implementation note |
| `docs/codex/07-implementation-history.md` | Modified | History entry |

## New Classes

| Package | Class | Class Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.domain.entity` | `StageResultCorrectionHistory` | Entity | Stores correction snapshots for one StageResult |
| `com.shinyoung.recruit.domain.repository` | `StageResultCorrectionHistoryRepository` | Repository | Reads correction histories latest-first |
| `com.shinyoung.recruit.dto.request` | `StageResultCorrectionRequest` | Request DTO | Carries corrected result values and mandatory reason |
| `com.shinyoung.recruit.dto.response` | `StageResultCorrectionHistoryResponse` | Response DTO | Exposes admin-only correction history |
| `com.shinyoung.recruit.service` | `StageResultCorrectionService` | Service | Applies correction policy, updates latest result, stores history |
| `com.shinyoung.recruit.service` | `StageResultCorrectionServiceTest` | Test | Verifies correction policy and history snapshots |

## Modified Classes

| Package | Class | Class Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.controller` | `StageResultController` | Controller | Adds admin correction and history read endpoints |
| `com.shinyoung.recruit.controller` | `StageResultControllerTest` | Test | Adds API contract coverage for correction and history |

## Class-by-Class Explanation

| Package | Class | Class Type | Responsibility | Key Fields or Methods | Related Classes | Implementation Notes |
|---|---|---|---|---|---|---|
| `domain.entity` | `StageResultCorrectionHistory` | Entity | Append-only correction audit row | `stageResult`, `correctedAt`, `correctedBy`, `reason`, previous/new snapshots | `StageResult`, `StageResultStatus` | LAZY ManyToOne to StageResult; no collection added to StageResult; no cascade/orphanRemoval |
| `domain.repository` | `StageResultCorrectionHistoryRepository` | Repository | Reads histories for one StageResult | `findByStageResultIdOrderByCorrectedAtDescIdDesc` | `StageResultCorrectionHistory` | Latest history appears first |
| `dto.request` | `StageResultCorrectionRequest` | Request DTO | Validates correction input | `resultStatus`, `score`, `comment`, `reason` | `StageResultCorrectionService` | `reason` is `@NotBlank` and max 1000; `comment` max 2000 |
| `dto.response` | `StageResultCorrectionHistoryResponse` | Response DTO | Admin-only history payload | `from(StageResultCorrectionHistory)` | `StageResultCorrectionHistory` | Includes previous/new status, score, comment, decidedAt |
| `service` | `StageResultCorrectionService` | Service | Owns correction command | `correctResult`, `getHistories` | `StageResultRepository`, `StageResultCorrectionHistoryRepository` | Looks up by `resultId + stageId`; allows only `RESULT_ANNOUNCED` or `CLOSED`; uses `SYSTEM` placeholder |
| `controller` | `StageResultController` | Controller | Admin StageResult API | `correctResult`, `getCorrectionHistories` | `ApiResponse`, `StageResultCorrectionService` | Adds only POST correction and GET histories |
| `service` | `StageResultCorrectionServiceTest` | Test | Verifies service behavior | announced/closed success, ready/in-progress failure, reason validation, mismatch 404, accumulated histories | `StageResultService`, `StageService` | Uses real initialize/update/announce flow |
| `controller` | `StageResultControllerTest` | Test | Verifies HTTP contract | correction success, histories success, blank reason 400, in-progress 400, unsupported methods | `MockMvc` | Confirms API response shape |

## API List

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| `POST` | `/admin/stages/{stageId}/results/{resultId}/correct` | Correct an announced or closed StageResult | `StageResultCorrectionRequest` | `ApiResponse<AdminStageResultResponse>` |
| `GET` | `/admin/stages/{stageId}/results/{resultId}/histories` | Read correction histories for one StageResult | none | `ApiResponse<List<StageResultCorrectionHistoryResponse>>` |

Unsupported by design:

- `PUT /admin/stages/{stageId}/results/{resultId}/correct`
- `PATCH /admin/stages/{stageId}/results/{resultId}/correct`
- `DELETE /admin/stages/{stageId}/results/{resultId}/histories`

## Request DTO

`StageResultCorrectionRequest`

| Field | Required | Validation | Notes |
|---|---:|---|---|
| `resultStatus` | Yes | not null, not `PENDING` by service policy | Corrected latest result status |
| `score` | No | nullable | Corrected score |
| `comment` | No | max 2000 | Corrected admin comment |
| `reason` | Yes | not blank, max 1000 | Required correction reason |

## Response DTO

`StageResultCorrectionHistoryResponse`

| Field | Source | Notes |
|---|---|---|
| `historyId` | history id | Admin history row id |
| `stageResultId` | `StageResult.id` | Corrected result id |
| `correctedAt` | history snapshot | Correction time |
| `correctedBy` | history snapshot | Uses `SYSTEM` placeholder in this phase |
| `reason` | history snapshot | Required reason |
| `previousStatus` / `newStatus` | history snapshot | Status before and after correction |
| `previousScore` / `newScore` | history snapshot | Score before and after correction |
| `previousComment` / `newComment` | history snapshot | Admin comment before and after correction |
| `previousDecidedAt` / `newDecidedAt` | history snapshot | Decision time before and after correction |

Applicant-facing response remains unchanged and does not expose correction history.

## Entity Relationship Summary

```text
StageResultCorrectionHistory N : 1 StageResult
StageResult N : 1 Stage
StageResult N : 1 JobApplication
```

Relationship policy:

- `StageResultCorrectionHistory.stageResult` is LAZY ManyToOne.
- `StageResult` does not receive a history collection.
- No cascade is configured.
- No orphanRemoval is configured.
- Correction history is append-only through service commands.

## Validation and Business Rules

- The correction service looks up `StageResult` by `resultId + stageId`.
- A stage/result mismatch is reported as `StageResultNotFoundException`.
- Correction is allowed only when `Stage.status` is `RESULT_ANNOUNCED` or `CLOSED`.
- `READY` and `IN_PROGRESS` stages reject correction.
- General update remains available only in `IN_PROGRESS` through `StageResultService.updateResult`.
- Correction reason is mandatory.
- `resultStatus=PENDING` is rejected.
- `comment` is still limited to 2000 characters.
- Correction updates the existing latest `StageResult`.
- Correction does not create a duplicate `StageResult`.
- Correction stores previous and new status, score, comment, and decidedAt.
- `decidedAt` is updated to correction time.
- `decidedBy` and `correctedBy` use `SYSTEM` until real admin identity is connected.
- Applicant-facing result read shows only the latest corrected result.
- Correction history is exposed only through the admin history API.

## Test Coverage

| Test Class | Coverage |
|---|---|
| `StageResultCorrectionServiceTest` | RESULT_ANNOUNCED success, CLOSED success, READY failure, IN_PROGRESS failure, blank reason failure, stage/result mismatch 404, latest StageResult mutation, previous/new snapshot persistence, multiple history accumulation/latest-first ordering |
| `StageResultControllerTest` | correction API success, history API success, reason blank 400, IN_PROGRESS correction failure, unsupported PUT/PATCH/DELETE methods |
| `ApplicationStageResultServiceTest` | Applicant-facing result read regression; latest corrected result remains the only applicant-visible surface |
| `ApplicationStageResultControllerTest` | Applicant response regression; correction history/internal fields remain hidden |
| `StageResultServiceTest` | Existing initialize/update/bulk regression |
| `StageServiceTest` | Existing stage lifecycle and announce guard regression |

## Test Commands

Executed:

```powershell
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.StageResultCorrectionServiceTest --tests com.shinyoung.recruit.controller.StageResultControllerTest
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationStageResultServiceTest --tests com.shinyoung.recruit.controller.ApplicationStageResultControllerTest
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.StageResultServiceTest --tests com.shinyoung.recruit.service.StageServiceTest
$env:AES_SECRET_KEY='***'; .\gradlew.bat clean test --no-daemon
```

## Test Results

- `StageResultCorrectionServiceTest` + `StageResultControllerTest`: success.
- `ApplicationStageResultServiceTest` + `ApplicationStageResultControllerTest`: success.
- `StageResultServiceTest` + `StageServiceTest`: success.
- `./gradlew.bat clean test --no-daemon`: success.

## Known Limitations

- `correctedBy` and `decidedBy` still use `SYSTEM`.
- No message or notification is sent after correction.
- No SecurityConfig or fine-grained authorization change is included.
- No read audit logging is included.
- No interview/evaluation aggregation is connected.
- No explicit migration file is added; tests rely on JPA schema generation.

## Next Phase Considerations

Recommended next work:

- Connect real admin identity to `correctedBy` and `decidedBy`.
- Define correction notification policy.
- Add audit logging if production authorization requirements are finalized.
- Add migration scripts if the project moves away from generated schema.
