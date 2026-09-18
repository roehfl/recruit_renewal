# Phase 03d-1 StageResult Initialize/List Implementation

## Phase Summary

Phase 03d-1 implements the first StageResult vertical slice.

Implemented scope:

- `StageResultStatus` enum
- `StageResult` entity
- `StageResultRepository`
- admin initialize command
- admin stage result list API
- service/controller tests
- implementation documentation and HTML report

This phase intentionally does not implement result update, bulk update, correction history, applicant-facing result read, application timeline read, message sending, or interview/evaluation aggregation.

## Implemented Scope

StageResult is now represented as one result row for one `Stage + JobApplication` pair.

Implemented APIs:

| Method | Path | Description |
|---|---|---|
| `GET` | `/admin/stages/{stageId}/results` | List existing StageResult rows for a stage |
| `POST` | `/admin/stages/{stageId}/results/initialize` | Create missing `PENDING` rows for submitted applications in the stage posting |

No `PUT`, HTTP `DELETE`, single result update, bulk update, or applicant-facing API was added.

## Changed Files

New code files:

- `src/main/java/com/shinyoung/recruit/enumeration/StageResultStatus.java`
- `src/main/java/com/shinyoung/recruit/domain/entity/StageResult.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/StageResultRepository.java`
- `src/main/java/com/shinyoung/recruit/dto/response/AdminStageResultResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/StageResultInitializeResponse.java`
- `src/main/java/com/shinyoung/recruit/exception/InvalidStageResultException.java`
- `src/main/java/com/shinyoung/recruit/service/StageResultService.java`
- `src/main/java/com/shinyoung/recruit/controller/StageResultController.java`
- `src/test/java/com/shinyoung/recruit/service/StageResultServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/StageResultControllerTest.java`

Modified code files:

- `src/main/java/com/shinyoung/recruit/domain/repository/JobApplicationRepository.java`
- `src/main/java/com/shinyoung/recruit/exception/GlobalExceptionHandler.java`

Documentation files:

- `docs/codex/implementation/phase-03d-1-stage-result-initialize-list.md`
- `docs/codex/reports/phase-03d-1-stage-result-initialize-list.html`
- `docs/codex/design/phase-03d-stage-result-design.md`
- `docs/codex/design/phase-03-application-design.md`
- `docs/codex/design/phase-02-stage-design.md`
- `docs/codex/07-implementation-history.md`

## Class-by-Class Explanation

| Package | Class | Type | Responsibility | Notes |
|---|---|---|---|---|
| `com.shinyoung.recruit.enumeration` | `StageResultStatus` | Enum | Result status for one stage/application result | Values: `PENDING`, `PASSED`, `FAILED`, `ABSENT`, `WITHDRAWN`, `HOLD` |
| `com.shinyoung.recruit.domain.entity` | `StageResult` | Entity | Stores one result row for `Stage + JobApplication` | Uses LAZY `ManyToOne`; no collection added to Stage or JobApplication |
| `com.shinyoung.recruit.domain.repository` | `StageResultRepository` | Repository | StageResult persistence and admin list query | Admin list uses fetch join to avoid N+1 for application/applicant/position |
| `com.shinyoung.recruit.domain.repository` | `JobApplicationRepository` | Repository | Added posting-level application lookup | `findByJobPostingId` uses `@EntityGraph` for initialize |
| `com.shinyoung.recruit.dto.response` | `AdminStageResultResponse` | Response DTO | Stage result row for admin stage result list | Includes applicant/application/stage result summary fields |
| `com.shinyoung.recruit.dto.response` | `StageResultInitializeResponse` | Response DTO | Initialize command summary | Includes created/existing/skipped counts and current result rows |
| `com.shinyoung.recruit.exception` | `InvalidStageResultException` | Exception | Invalid StageResult operation | Mapped to 400 by `GlobalExceptionHandler` |
| `com.shinyoung.recruit.service` | `StageResultService` | Service | Initialize/list StageResult rows | Initialize is idempotent and only creates missing submitted application rows |
| `com.shinyoung.recruit.controller` | `StageResultController` | Controller | Admin StageResult read/initialize API | Exposes only `GET` and initialize `POST` |
| `com.shinyoung.recruit.service` | `StageResultServiceTest` | Test | Service policy coverage | Covers READY/IN_PROGRESS, announced/closed failure, submitted-only, idempotency, mismatch guard |
| `com.shinyoung.recruit.controller` | `StageResultControllerTest` | Test | API response and method coverage | Covers success/failure and unsupported method checks |

## Entity Relationship Summary

`StageResult` relationships:

- `StageResult -> Stage`: `@ManyToOne(fetch = FetchType.LAZY)`
- `StageResult -> JobApplication`: `@ManyToOne(fetch = FetchType.LAZY)`
- `Stage` does not have a StageResult collection.
- `JobApplication` does not have a StageResult collection.
- No cascade.
- No orphanRemoval.

Table constraints:

- Unique candidate implemented: `stage_id + job_application_id`
- Indexes implemented: `stage_id`, `job_application_id`

The entity factory validates that the `Stage` and `JobApplication` belong to the same `JobPosting`.

## Business Rules

### Initialize Policy

`POST /admin/stages/{stageId}/results/initialize`:

- Stage must exist.
- Stage status must be `READY` or `IN_PROGRESS`.
- `RESULT_ANNOUNCED` and `CLOSED` stages are rejected.
- Target applications are loaded from the Stage's `JobPosting`.
- Only `SUBMITTED` applications are initialized.
- `DRAFT` and `WITHDRAWN` applications are skipped.
- Applications from other postings are naturally excluded by the posting-scoped lookup.
- Existing StageResult rows are preserved.
- Missing submitted applications get a new `PENDING` StageResult.
- Re-running initialize is idempotent.

Counts:

- `createdCount`: newly created result rows
- `existingCount`: submitted application rows that already had a result for the stage
- `skippedCount`: applications in the same posting that were not `SUBMITTED`

### List Policy

`GET /admin/stages/{stageId}/results`:

- Stage must exist.
- Only existing StageResult rows are returned.
- Missing application rows are not synthesized by read.
- Initialize should be run first when admins need a complete stage result list.
- Sorting is `application.submittedAt DESC, application.id DESC`.

## API List

### GET `/admin/stages/{stageId}/results`

Response:

```json
{
  "success": true,
  "data": [
    {
      "stageResultId": 1,
      "stageId": 10,
      "applicationId": 100,
      "applicantName": "Applicant",
      "jobPositionId": 3,
      "jobPositionName": "Backend",
      "applicationStatus": "SUBMITTED",
      "resultStatus": "PENDING",
      "score": null,
      "comment": null,
      "submittedAt": "2026-06-15T10:00:00",
      "decidedAt": null
    }
  ],
  "message": "..."
}
```

### POST `/admin/stages/{stageId}/results/initialize`

Response:

```json
{
  "success": true,
  "data": {
    "stageId": 10,
    "createdCount": 1,
    "existingCount": 0,
    "skippedCount": 2,
    "results": []
  },
  "message": "..."
}
```

## Test Coverage

New tests:

- `StageResultServiceTest`
- `StageResultControllerTest`

Covered cases:

- READY Stage initialize success
- IN_PROGRESS Stage initialize success
- RESULT_ANNOUNCED Stage initialize failure
- CLOSED Stage initialize failure
- Only SUBMITTED applications create PENDING rows
- DRAFT applications are skipped
- WITHDRAWN applications are skipped
- Other posting applications are excluded
- Existing rows are not duplicated
- Re-initialize is idempotent
- Stage-specific result list does not mix other stages
- Stage and JobApplication posting mismatch is rejected by the factory
- API success/failure response shape
- `PUT`, HTTP `DELETE`, and generic single-result POST paths are not implemented

## Test Commands

Executed:

```powershell
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.StageResultServiceTest
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.StageResultControllerTest
```

Executed regression:

```powershell
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.StageServiceTest --tests com.shinyoung.recruit.controller.StageControllerTest
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.JobApplicationServiceTest --tests com.shinyoung.recruit.controller.ApplicationControllerTest
$env:AES_SECRET_KEY='***'; .\gradlew.bat clean test --no-daemon
```

## Test Results

- `StageResultServiceTest`: success
- `StageResultControllerTest`: success
- `StageServiceTest` + `StageControllerTest`: success
- `JobApplicationServiceTest` + `ApplicationControllerTest`: success
- `./gradlew.bat clean test --no-daemon`: success

## Known Limitations

- Result update is not implemented.
- Bulk update is not implemented.
- Correction command/history is not implemented.
- Applicant-facing result read is not implemented.
- Admin application stage-result timeline is not implemented.
- Stage announce command still does not check pending StageResult rows.
- StageResult row handling after applicant withdrawal is deferred.
- `decidedBy` remains a nullable string candidate until auth/audit policy is finalized.
- Security, authorization, and audit logging are deferred.

## Next Phase Considerations

Recommended next phase:

- Phase 03d-2: StageResult single/bulk update commands.

Recommended scope:

- update result status, score, comment
- validate editable Stage status
- set `decidedAt`
- decide `decidedBy` source
- prevent announcement while pending rows remain
- defer correction history unless explicitly required
