# Phase 03d-4 Applicant StageResult Read Implementation

## Phase Summary

Phase 03d-4 implements the applicant-facing read-only StageResult API.

The goal is to let an applicant read only their own announced stage results while preventing pre-announcement stage leakage and internal admin data exposure.

Implemented API:

| Method | Path | Response |
|---|---|---|
| `GET` | `/applications/{applicationId}/stage-results` | `ApiResponse<List<ApplicantStageResultResponse>>` |

This phase does not implement Phase 03d-5 correction/history.

## Implemented Scope

- Added applicant-only result read API.
- Added applicant-specific response DTO.
- Added StageResult repository query for applicant-visible rows.
- Added applicant ownership and application status validation service.
- Added controller integration using existing `CurrentApplicantService`.
- Added service/controller tests.
- Updated design and implementation history documents.
- Added paired self-contained HTML report.

## Out of Scope

- Phase 03d-5 correction/history.
- `StageResultCorrectionHistory`.
- Correction command or correction history API.
- DB schema changes.
- `SecurityConfig` changes.
- Admin timeline API changes.
- Stage announce policy changes.
- Message or notification integration.
- `resultAnnouncementDateTime` scheduled-release guard.
- PUT, PATCH, DELETE, or POST command APIs for applicant result read.
- Read-time StageResult creation/upsert.

## Changed Files

| Path | Type | Notes |
|---|---|---|
| `src/main/java/com/shinyoung/recruit/dto/response/ApplicantStageResultResponse.java` | New | Applicant-facing StageResult response DTO |
| `src/main/java/com/shinyoung/recruit/domain/repository/StageResultRepository.java` | Modified | Added applicant-visible StageResult query |
| `src/main/java/com/shinyoung/recruit/service/ApplicationStageResultService.java` | New | Applicant ownership and result visibility service |
| `src/main/java/com/shinyoung/recruit/controller/ApplicationStageResultController.java` | New | Applicant result read controller |
| `src/test/java/com/shinyoung/recruit/service/ApplicationStageResultServiceTest.java` | New | Service policy tests |
| `src/test/java/com/shinyoung/recruit/controller/ApplicationStageResultControllerTest.java` | New | API contract tests |
| `docs/codex/implementation/phase-03d-4-applicant-stage-result-read.md` | New | Implementation reference |
| `docs/codex/reports/phase-03d-4-applicant-stage-result-read.html` | New | Human-readable report |
| `docs/codex/design/phase-03d-stage-result-design.md` | Modified | Phase 03d-4 implementation note |
| `docs/codex/design/phase-03-application-design.md` | Modified | Phase 03d-4 implementation note |
| `docs/codex/07-implementation-history.md` | Modified | History entry |

## New Classes

| Package | Class | Class Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.dto.response` | `ApplicantStageResultResponse` | Response DTO | Exposes only applicant-safe stage/result fields |
| `com.shinyoung.recruit.service` | `ApplicationStageResultService` | Service | Validates applicant ownership and returns visible announced results |
| `com.shinyoung.recruit.controller` | `ApplicationStageResultController` | Controller | Exposes `GET /applications/{applicationId}/stage-results` |
| `com.shinyoung.recruit.service` | `ApplicationStageResultServiceTest` | Test | Covers service-level ownership, status, visibility, and DTO field policy |
| `com.shinyoung.recruit.controller` | `ApplicationStageResultControllerTest` | Test | Covers API response, field exclusion, unsupported methods, and error responses |

## Modified Classes

| Package | Class | Class Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.domain.repository` | `StageResultRepository` | Repository | Adds applicant-visible result query with `Stage` fetch join |

## Class-by-Class Explanation

| Package | Class | Class Type | Responsibility | Key Fields or Methods | Related Classes | Implementation Notes |
|---|---|---|---|---|---|---|
| `dto.response` | `ApplicantStageResultResponse` | Response DTO | Applicant-safe result row | `from(StageResult)` | `StageResult`, `Stage`, `StageType`, `StageResultStatus` | Exposes `stageName`, `stageType`, `stageOrder`, `resultStatus`, `resultAnnouncementDateTime`, `decidedAt` only |
| `domain.repository` | `StageResultRepository` | Repository | Reads applicant-visible results | `findVisibleByJobApplicationIdForApplicant(Long)` | `StageResult`, `StageStatus` | StageResult-based query; fetch joins `stage`; filters `RESULT_ANNOUNCED`, `CLOSED`; sorts by `stageOrder ASC, stage.id ASC` |
| `service` | `ApplicationStageResultService` | Service | Applies applicant result read policy | `getApplicantStageResults(Long applicantId, Long applicationId)` | `JobApplicationRepository`, `StageResultRepository` | Loads application by `applicationId + applicantId`; rejects `DRAFT`; does not synthesize missing rows |
| `controller` | `ApplicationStageResultController` | Controller | Connects applicant API to service | `getStageResults` | `CurrentApplicantService`, `ApiResponse` | Uses existing applicant authentication pattern; GET only |
| `service` | `ApplicationStageResultServiceTest` | Test | Verifies service behavior | announced/closed read, hidden READY/IN_PROGRESS, DRAFT fail, WITHDRAWN read, owner check, field policy, future announcement time | `StageResultService`, `StageService`, `JobApplicationService` | Uses real service flow to create, decide, announce, close, and withdraw |
| `controller` | `ApplicationStageResultControllerTest` | Test | Verifies HTTP contract | success, DRAFT 400, other applicant 404, unsupported methods, forbidden fields absent | `MockMvc`, `SecurityContextHolder` | Confirms `stageResultId`, `score`, `comment`, `decidedBy` are not serialized |

## API List

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| `GET` | `/applications/{applicationId}/stage-results` | Applicant reads their own announced stage results | none | `ApiResponse<List<ApplicantStageResultResponse>>` |

Unsupported by design:

- `POST /applications/{applicationId}/stage-results`
- `PUT /applications/{applicationId}/stage-results`
- `PATCH /applications/{applicationId}/stage-results`
- `DELETE /applications/{applicationId}/stage-results`

## Response DTO

`ApplicantStageResultResponse`

| Field | Source | Notes |
|---|---|---|
| `stageName` | `Stage.stageName` | Applicant display label |
| `stageType` | `Stage.stageType` | Enum serialized as string |
| `stageOrder` | `Stage.stageOrder` | Display ordering |
| `resultStatus` | `StageResult.resultStatus` | Announced latest result |
| `resultAnnouncementDateTime` | `Stage.resultAnnouncementDateTime` | Display only in this phase |
| `decidedAt` | `StageResult.decidedAt` | Decision time |

Explicitly not exposed:

- `stageResultId`
- `score`
- `comment`
- `decidedBy`
- `createdAt`
- `updatedAt`
- correction history fields

## Entity Relationship Summary

```text
JobApplication N : 1 Applicant
StageResult N : 1 JobApplication
StageResult N : 1 Stage
Stage N : 1 JobPosting
```

Read flow:

1. Resolve current applicant in controller through `CurrentApplicantService`.
2. Load `JobApplication` by `applicationId + applicantId`.
3. Reject `DRAFT`.
4. Query existing `StageResult` rows whose `Stage.status` is `RESULT_ANNOUNCED` or `CLOSED`.
5. Map each row to `ApplicantStageResultResponse`.

No collection was added to `Stage` or `JobApplication`.

## Validation and Business Rules

- Applicant must be authenticated as an applicant user.
- Service receives both `applicantId` and `applicationId`.
- `applicationId`-only lookup is not used.
- Other applicant's application is hidden through `JobApplicationNotFoundException`.
- `DRAFT` applications fail with `InvalidJobApplicationException`.
- `SUBMITTED` applications can read visible results.
- `WITHDRAWN` applications can read visible results already associated with the application.
- Only stages in `RESULT_ANNOUNCED` or `CLOSED` are visible.
- `READY` and `IN_PROGRESS` stages are not returned.
- "Announcement pending" rows are not created.
- Missing StageResult rows are not exposed as null rows.
- Read-time writes/upserts are not performed.
- `resultAnnouncementDateTime` is display data only.
- Future `resultAnnouncementDateTime` does not block a `RESULT_ANNOUNCED` stage in this phase.

## Test Coverage

| Test Class | Coverage |
|---|---|
| `ApplicationStageResultServiceTest` | announced result read, closed result read, READY/IN_PROGRESS hidden, DRAFT failure, WITHDRAWN read, owner failure, DTO internal field exclusion, future announcement time allowed |
| `ApplicationStageResultControllerTest` | GET success, `ApiResponse` shape, forbidden fields not serialized, DRAFT 400, other applicant 404, POST/PUT/PATCH/DELETE unsupported |
| `StageResultServiceTest` | Existing StageResult initialize/update/bulk regression |
| `StageResultControllerTest` | Existing admin StageResult API regression |
| `StageServiceTest` | Existing stage lifecycle and announce guard regression |
| `StageControllerTest` | Existing Stage API regression |

## Test Commands

Executed:

```powershell
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationStageResultServiceTest --tests com.shinyoung.recruit.controller.ApplicationStageResultControllerTest
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.StageResultServiceTest --tests com.shinyoung.recruit.controller.StageResultControllerTest --tests com.shinyoung.recruit.service.StageServiceTest --tests com.shinyoung.recruit.controller.StageControllerTest
```

Attempted:

```powershell
$env:AES_SECRET_KEY='***'; .\gradlew.bat clean test --no-daemon
```

## Test Results

- `ApplicationStageResultServiceTest` + `ApplicationStageResultControllerTest`: success.
- `StageResultServiceTest` + `StageResultControllerTest` + `StageServiceTest` + `StageControllerTest`: success.
- `./gradlew.bat clean test --no-daemon`: success.

## Known Limitations

- Phase 03d-5 correction/history is not implemented.
- Correction history is not exposed or stored.
- Result correction notification is not implemented.
- Scheduled release by `resultAnnouncementDateTime` is not implemented.
- SecurityConfig remains unchanged; service-level applicant ownership remains the main guard in this phase.
- Read audit logging is not implemented.

## Next Phase Considerations

Recommended next phase:

- Phase 03d-5 result correction history.

Before Phase 03d-5:

- Decide the real admin identity source for `correctedBy`.
- Decide whether `CLOSED` stages allow correction.
- Keep applicant-facing correction exposure limited to the latest corrected result only.
