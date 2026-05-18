# Phase 03d-2 StageResult Update Commands + Announce Pending Guard

## Phase Summary

Phase 03d-2 extends the StageResult vertical slice with admin result input commands and a Stage announce guard.

Implemented:

- single StageResult update command
- bulk StageResult update command
- `StageService.announce()` pending-result guard
- request/response DTOs
- StageResult not-found exception mapping
- service/controller/regression tests

Still deferred:

- correction history
- post-announcement correction command
- applicant-facing result read
- admin application stage-result timeline
- fine-grained admin identity/audit logging
- interview/evaluation score aggregation

## Implemented Scope

APIs added:

| Method | Path | Description |
|---|---|---|
| `POST` | `/admin/stages/{stageId}/results/{resultId}` | Update one StageResult |
| `POST` | `/admin/stages/{stageId}/results/bulk` | Update multiple StageResult rows in one transaction |

Existing APIs retained:

| Method | Path | Description |
|---|---|---|
| `GET` | `/admin/stages/{stageId}/results` | List StageResult rows |
| `POST` | `/admin/stages/{stageId}/results/initialize` | Initialize missing PENDING rows |

No `PUT`, HTTP `DELETE`, `PATCH`, correction API, applicant result API, or application timeline API was added.

## Changed Files

New code files:

- `src/main/java/com/shinyoung/recruit/dto/request/StageResultUpdateRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/StageResultBulkUpdateRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/StageResultBulkUpdateItemRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/response/StageResultBulkUpdateResponse.java`
- `src/main/java/com/shinyoung/recruit/exception/StageResultNotFoundException.java`

Modified code files:

- `src/main/java/com/shinyoung/recruit/domain/entity/StageResult.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/StageResultRepository.java`
- `src/main/java/com/shinyoung/recruit/service/StageResultService.java`
- `src/main/java/com/shinyoung/recruit/controller/StageResultController.java`
- `src/main/java/com/shinyoung/recruit/service/StageService.java`
- `src/main/java/com/shinyoung/recruit/exception/GlobalExceptionHandler.java`
- `src/test/java/com/shinyoung/recruit/service/StageResultServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/StageResultControllerTest.java`
- `src/test/java/com/shinyoung/recruit/service/StageServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/StageControllerTest.java`

Documentation files:

- `docs/codex/implementation/phase-03d-2-stage-result-update-announce-guard.md`
- `docs/codex/reports/phase-03d-2-stage-result-update-announce-guard.html`
- `docs/codex/design/phase-03d-stage-result-design.md`
- `docs/codex/design/phase-02-stage-design.md`
- `docs/codex/design/phase-03-application-design.md`
- `docs/codex/07-implementation-history.md`

## Class-by-Class Explanation

| Package | Class | Type | Responsibility | Notes |
|---|---|---|---|---|
| `domain.entity` | `StageResult` | Entity | Added result update behavior | Rejects null status, PENDING rollback, overlong comments, missing decidedAt |
| `domain.repository` | `StageResultRepository` | Repository | Added update/guard query helpers | `findByIdAndStageId`, `findByStageIdAndIdIn`, `existsByStageIdAndResultStatus` |
| `dto.request` | `StageResultUpdateRequest` | Request DTO | Single update payload | `resultStatus` required, comment max 2000 |
| `dto.request` | `StageResultBulkUpdateRequest` | Request DTO | Bulk update wrapper | non-null and non-empty list |
| `dto.request` | `StageResultBulkUpdateItemRequest` | Request DTO | Bulk update item | `stageResultId` and `resultStatus` required |
| `dto.response` | `StageResultBulkUpdateResponse` | Response DTO | Bulk update summary | `stageId`, `updatedCount`, current results |
| `exception` | `StageResultNotFoundException` | Exception | Hidden not-found for result id/stage mismatch | Mapped to 404 |
| `service` | `StageResultService` | Service | Single/bulk update orchestration | Updates only existing rows; all-or-nothing bulk policy |
| `controller` | `StageResultController` | Controller | Admin result update APIs | Adds only POST command endpoints |
| `service` | `StageService` | Service | Announce guard | Rejects announce when no result rows or any PENDING row remains |

## Business Rules

### Result Update Policy

- General StageResult update is allowed only when `Stage.status == IN_PROGRESS`.
- `READY` allows initialize only, not final result input.
- `RESULT_ANNOUNCED` and `CLOSED` reject general result updates.
- Updates modify existing StageResult rows only.
- Missing rows are never created by update.
- Missing rows must be created through initialize.

### Status Policy

Allowed target statuses:

- `PASSED`
- `FAILED`
- `ABSENT`
- `WITHDRAWN`
- `HOLD`

Rejected:

- `null`
- `PENDING`

PENDING rollback is intentionally forbidden in this phase. A future correction/reopen policy can define how to undo announced or decided results.

### Field Policy

- `score`: nullable, no precision/scale restriction in this phase.
- `comment`: nullable, max 2000 characters.
- `decidedAt`: set by service when a non-PENDING result is saved.
- `decidedBy`: currently fixed to `"SYSTEM"`.

Real admin identity mapping is deferred to the security/audit phase.

### Bulk Update Policy

- Bulk list must be non-null and non-empty.
- Every item must include `stageResultId` and `resultStatus`.
- Duplicate `stageResultId` fails.
- Any result id outside the requested stage fails with 404.
- Any invalid status/comment fails.
- Partial success is not allowed.
- The whole bulk operation runs in one transaction.

### Announce Guard Policy

`StageService.announce()` now checks StageResult state before `stage.announce()`:

- StageResult rows must exist for the stage.
- If no result row exists, announce fails.
- If any `PENDING` StageResult remains, announce fails.
- Announce succeeds only when every existing result is one of:
  - `PASSED`
  - `FAILED`
  - `ABSENT`
  - `WITHDRAWN`
  - `HOLD`

Existing Stage state transition policy is otherwise unchanged.

## API Details

### POST `/admin/stages/{stageId}/results/{resultId}`

Request:

```json
{
  "resultStatus": "PASSED",
  "score": 91.5,
  "comment": "passed"
}
```

Response:

```json
{
  "success": true,
  "data": {
    "stageResultId": 1,
    "stageId": 10,
    "applicationId": 100,
    "resultStatus": "PASSED",
    "score": 91.5,
    "comment": "passed",
    "decidedAt": "2026-06-15T10:00:00"
  },
  "message": "..."
}
```

### POST `/admin/stages/{stageId}/results/bulk`

Request:

```json
{
  "results": [
    {
      "stageResultId": 1,
      "resultStatus": "PASSED",
      "score": 90,
      "comment": "pass"
    },
    {
      "stageResultId": 2,
      "resultStatus": "FAILED",
      "score": null,
      "comment": null
    }
  ]
}
```

Response:

```json
{
  "success": true,
  "data": {
    "stageId": 10,
    "updatedCount": 2,
    "results": []
  },
  "message": "..."
}
```

## Test Coverage

New or updated tests cover:

- IN_PROGRESS Stage single update success
- READY single update failure
- RESULT_ANNOUNCED single update failure
- CLOSED single update failure
- result id from another stage fails
- `PENDING` update failure
- comment over 2000 characters failure
- decidedAt/decidedBy set on update
- nullable score/comment success
- bulk update success
- bulk duplicate result id failure
- bulk empty list failure
- bulk with another stage result failure
- bulk invalid item rollback
- update/bulk controller success
- validation failure response
- result not-found response
- `PUT`/HTTP `DELETE`/`PATCH` unsupported
- announce fails without StageResult rows
- announce fails with PENDING rows
- announce succeeds after all results are decided

## Test Commands

Executed:

```powershell
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.StageResultServiceTest
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.StageResultControllerTest
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.StageServiceTest
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.StageControllerTest
```

Executed regression:

```powershell
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.JobApplicationServiceTest --tests com.shinyoung.recruit.controller.ApplicationControllerTest
$env:AES_SECRET_KEY='***'; .\gradlew.bat clean test --no-daemon
```

## Test Results

- `StageResultServiceTest`: success
- `StageResultControllerTest`: success
- `StageServiceTest`: success
- `StageControllerTest`: success
- `JobApplicationServiceTest` + `ApplicationControllerTest`: success
- `./gradlew.bat clean test --no-daemon`: success

## Known Limitations

- Correction history is not implemented.
- RESULT_ANNOUNCED/CLOSED correction command is not implemented.
- PENDING rollback is not allowed.
- Applicant-facing result read is not implemented.
- Admin application stage-result timeline is not implemented.
- `decidedBy` is currently `"SYSTEM"`, not the actual admin identity.
- Score precision/scale policy is deferred.
- Security, authorization, and audit logging are deferred.

## Next Phase Considerations

Recommended next phase:

- Phase 03d-3: admin application stage result lazy timeline API or applicant-facing result read design.

Before production:

- decide result correction workflow
- wire actual admin identity into `decidedBy`
- add audit log for all result changes
- define announcement visibility and applicant-facing result wording
