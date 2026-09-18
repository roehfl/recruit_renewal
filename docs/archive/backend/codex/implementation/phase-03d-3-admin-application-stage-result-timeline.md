# Phase 03d-3 Admin Application StageResult Timeline Lazy API

## Phase Summary

Phase 03d-3 adds a read-only admin lazy section API for viewing one application's stage-result timeline.

The API is attached to the existing admin application detail section flow and returns one row per `Stage` in the application's `JobPosting`. If a `StageResult` exists for that application and stage, result fields are merged. If no result row exists yet, result fields are null.

## Implemented Scope

- Added `GET /admin/applications/{applicationId}/stage-results`.
- Extended existing `AdminApplicationSectionController`.
- Extended existing `AdminApplicationSectionService`.
- Added `AdminApplicationStageResultResponse`.
- Added a timeline query method to `StageResultRepository`.
- Used `StageRepository.findByJobPostingIdOrderByStageOrderAscIdAsc(...)` as the source stage list.
- Verified `applicationId` existence through admin read path.
- Allowed `DRAFT`, `SUBMITTED`, and `WITHDRAWN` applications.
- Returned stages sorted by `stageOrder ASC, id ASC`.
- Merged only `StageResult` rows for the requested application and the application's own posting stages.
- Kept missing `StageResult` rows as null result fields.
- Kept `decidedBy` out of the response.

## Out of Scope

- StageResult initialize/update/bulk policy changes.
- Stage announce guard changes.
- Applicant-facing result API.
- Result correction/history.
- Message or notification integration.
- `SecurityConfig` changes.
- PUT, DELETE, or PATCH API additions.
- Adding stage results to the admin application root detail response.

## Changed Files

| Path | Type | Notes |
|---|---|---|
| `src/main/java/com/shinyoung/recruit/dto/response/AdminApplicationStageResultResponse.java` | New | Admin application stage-result timeline DTO |
| `src/main/java/com/shinyoung/recruit/domain/repository/StageResultRepository.java` | Modified | Added application timeline query |
| `src/main/java/com/shinyoung/recruit/service/AdminApplicationSectionService.java` | Modified | Added `getStageResults` |
| `src/main/java/com/shinyoung/recruit/controller/AdminApplicationSectionController.java` | Modified | Added `GET /admin/applications/{applicationId}/stage-results` |
| `src/test/java/com/shinyoung/recruit/service/AdminApplicationSectionServiceTest.java` | Modified | Added service tests for timeline merge/null/exclusion/state policy |
| `src/test/java/com/shinyoung/recruit/controller/AdminApplicationSectionControllerTest.java` | Modified | Added API response and unsupported method tests |
| `docs/codex/implementation/phase-03d-3-admin-application-stage-result-timeline.md` | New | Implementation reference |
| `docs/codex/reports/phase-03d-3-admin-application-stage-result-timeline.html` | New | Human-readable report |
| `docs/codex/design/phase-03d-stage-result-design.md` | Modified | Phase 03d-3 implementation note |
| `docs/codex/design/phase-03-application-design.md` | Modified | Phase 03d-3 implementation note |
| `docs/codex/07-implementation-history.md` | Modified | History entry |

## New Classes

| Package | Class | Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.dto.response` | `AdminApplicationStageResultResponse` | Response DTO | Represents one stage row and optional result fields for an admin application detail timeline |

## Modified Classes

| Package | Class | Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.domain.repository` | `StageResultRepository` | Repository | Loads result rows for one application and a bounded set of stage ids |
| `com.shinyoung.recruit.service` | `AdminApplicationSectionService` | Service | Builds the admin application stage-result timeline from Stage rows and StageResult rows |
| `com.shinyoung.recruit.controller` | `AdminApplicationSectionController` | Controller | Exposes the admin lazy section GET endpoint |
| `com.shinyoung.recruit.service` | `AdminApplicationSectionServiceTest` | Test | Covers service-level timeline behavior |
| `com.shinyoung.recruit.controller` | `AdminApplicationSectionControllerTest` | Test | Covers API contract and unsupported methods |

## Class-by-Class Explanation

| Package | Class | Class Type | Responsibility | Key fields or methods | Related classes | Implementation notes |
|---|---|---|---|---|---|---|
| `dto.response` | `AdminApplicationStageResultResponse` | Response DTO | Admin read-only timeline row | `of(Stage, StageResult)` | `Stage`, `StageResult`, `StageType`, `StageStatus`, `StageResultStatus` | `stageResultId`, `resultStatus`, `score`, `comment`, `decidedAt` are null when no result row exists; `decidedBy` is intentionally absent |
| `domain.repository` | `StageResultRepository` | Repository | Fetches StageResult rows for timeline merge | `findByJobApplicationIdAndStageIdInForTimeline` | `StageResult`, `Stage` | Uses a JPQL fetch join for `result.stage`; the `stageIds` constraint prevents other posting stages from being merged |
| `service` | `AdminApplicationSectionService` | Service | Creates timeline rows for one admin application detail section | `getStageResults(Long applicationId)` | `JobApplicationRepository`, `StageRepository`, `StageResultRepository` | Finds the application, loads posting stages in display order, maps result rows by stage id, then merges each row |
| `controller` | `AdminApplicationSectionController` | Controller | Adds the admin lazy read endpoint | `getStageResults` | `ApiResponse`, `AdminApplicationSectionService` | GET only; no write method added |
| `service` | `AdminApplicationSectionServiceTest` | Test | Verifies service policy | timeline sort, result merge, null fields, other posting exclusion, status access, not found, no decidedBy field | `StageRepository`, `StageResultRepository` | Uses repository-created stages and StageResult rows to keep scope on read behavior |
| `controller` | `AdminApplicationSectionControllerTest` | Test | Verifies HTTP contract | success response, null field omission, 404, POST/PUT/DELETE/PATCH unsupported | `MockMvc` | Confirms `decidedBy` is not serialized |

## API List

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| `GET` | `/admin/applications/{applicationId}/stage-results` | Read one application's stage-result timeline for admin detail lazy section | None | `ApiResponse<List<AdminApplicationStageResultResponse>>` |

No POST, PUT, DELETE, or PATCH endpoint was added for this path.

## Response DTO

`AdminApplicationStageResultResponse`

| Field | Source | Null policy |
|---|---|---|
| `stageId` | `Stage.id` | Not null for persisted stages |
| `stageName` | `Stage.stageName` | Not null |
| `stageType` | `Stage.stageType` | Not null |
| `stageOrder` | `Stage.stageOrder` | Not null |
| `stageStatus` | `Stage.status` | Not null |
| `finalStage` | `Stage.finalStage` | Not null |
| `resultAnnouncementDateTime` | `Stage.resultAnnouncementDateTime` | Nullable by stage configuration |
| `stageResultId` | `StageResult.id` | Null when no result row exists |
| `resultStatus` | `StageResult.resultStatus` | Null when no result row exists |
| `score` | `StageResult.score` | Null when no result row exists or no score is stored |
| `comment` | `StageResult.comment` | Null when no result row exists or no comment is stored |
| `decidedAt` | `StageResult.decidedAt` | Null when no result row exists or result is not decided |

Excluded:

- `decidedBy`

## Entity Relationship Summary

```text
JobApplication N : 1 JobPosting
Stage N : 1 JobPosting
StageResult N : 1 Stage
StageResult N : 1 JobApplication
```

Timeline construction:

1. Find `JobApplication` by `applicationId`.
2. Read all `Stage` rows for `application.jobPosting.id`, sorted by `stageOrder ASC, id ASC`.
3. Read `StageResult` rows for the same `applicationId` and those stage ids.
4. Merge by `Stage.id`.
5. Return every Stage row, with nullable result fields.

No collection was added to `Stage` or `JobApplication`.

## Validation and Business Rules

- The API is admin-only by path.
- The service checks that `applicationId` exists.
- Applicant ownership is not checked in this admin read path.
- `DRAFT`, `SUBMITTED`, and `WITHDRAWN` applications are all readable.
- JobPosting status, reception period, and result announcement state do not block admin read.
- Result announcement visibility is not applied to admin read.
- The row source is Stage, not StageResult.
- Missing StageResult rows are represented by null result fields.
- StageResult rows outside the application's JobPosting stages are not merged.
- `comment` is treated as admin-visible data. It must not be reused in applicant-facing result APIs without a separate exposure policy.
- `decidedBy` is not exposed.

## Security and Exposure Policy

- This phase does not add applicant-facing result read.
- Applicant-facing result exposure remains deferred and should be gated by announcement policy in a separate API.
- `comment` may contain internal admin notes and must not be copied into applicant responses by default.
- `decidedBy` remains an internal/audit field and is not part of the DTO.
- `SecurityConfig` was not changed.

## Test Coverage

| Test class | Coverage |
|---|---|
| `AdminApplicationSectionServiceTest` | Stage order, result merge, null result fields, DRAFT/SUBMITTED/WITHDRAWN access, nonexistent application, other posting exclusion, `decidedBy` absence |
| `AdminApplicationSectionControllerTest` | GET success, result row plus missing row response, nonexistent application 404, POST/PUT/DELETE/PATCH unsupported, `decidedBy` not serialized |
| `StageResultServiceTest` | Existing initialize/update/bulk regression |
| `StageResultControllerTest` | Existing StageResult API regression |
| `StageServiceTest` | Existing Stage lifecycle and announce guard regression |
| `StageControllerTest` | Existing Stage API regression |

## Test Commands

Executed:

```powershell
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.AdminApplicationSectionServiceTest --tests com.shinyoung.recruit.controller.AdminApplicationSectionControllerTest
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.StageResultServiceTest --tests com.shinyoung.recruit.controller.StageResultControllerTest --tests com.shinyoung.recruit.service.StageServiceTest --tests com.shinyoung.recruit.controller.StageControllerTest
$env:AES_SECRET_KEY='***'; .\gradlew.bat clean test --no-daemon
```

## Test Results

- `AdminApplicationSectionServiceTest` + `AdminApplicationSectionControllerTest`: success
- `StageResultServiceTest` + `StageResultControllerTest` + `StageServiceTest` + `StageControllerTest`: success
- `./gradlew.bat clean test --no-daemon`: success

## Known Limitations

- Applicant-facing result read is not implemented.
- Result correction/history is not implemented.
- Result announce policy is unchanged except existing Phase 03d-2 guard.
- Message/notification integration is not implemented.
- Fine-grained admin authorization and read audit logging are not implemented.
- The admin root application detail response still does not include stage results.

## Next Phase Considerations

Recommended next work:

- Design or implement applicant-facing result read with announcement visibility guard.
- Decide whether applicant responses expose only pass/fail status or also stage name/announcement time.
- Add read audit logging for admin detail sections when the security/audit phase begins.
- Define result correction history before allowing post-announcement result changes.
