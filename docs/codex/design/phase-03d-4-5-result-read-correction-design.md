# Phase 03d-4/03d-5 Result Read and Correction Design

## Phase Summary

This is a design-only phase that splits the next StageResult work into two implementable phases.

- Phase 03d-4: applicant-facing stage result read with announcement visibility guard.
- Phase 03d-5: post-announcement result correction command and correction history.

No Java code, Entity, Repository, Service, Controller, DTO, Test, DB schema, SecurityConfig, YAML, Gradle, or static resource file is changed in this phase.

## Purpose

Phase 03d-1 through Phase 03d-3 completed the admin side of the initial StageResult flow:

- StageResult initialize/list for one stage.
- StageResult single/bulk update before announcement.
- Stage announce pending-result guard.
- Admin application detail stage-result lazy timeline.

Two policies still need to be separated before implementation:

1. What an applicant can see after result announcement.
2. How an admin corrects an already announced result without losing change history.

This document defines those policies so Phase 03d-4 and Phase 03d-5 can be implemented as separate vertical slices.

## Scope

### Phase 03d-4 Scope

- Candidate applicant API: `GET /applications/{applicationId}/stage-results`.
- Applicant ownership validation.
- Application status visibility policy.
- Stage announcement visibility policy.
- Applicant response DTO field policy.
- Service, controller, and test candidates.

### Phase 03d-5 Scope

- Candidate admin correction API: `POST /admin/stages/{stageId}/results/{resultId}/correct`.
- Candidate admin correction history API: `GET /admin/stages/{stageId}/results/{resultId}/histories`.
- Correction reason requirement.
- `StageResultCorrectionHistory` entity candidate.
- Latest-result and history exposure policy.
- Service, controller, repository, and test candidates.

## Out of Scope

- Implementing the APIs.
- Adding or modifying Java classes.
- Changing `SecurityConfig`.
- Changing Stage announce command behavior beyond the existing Phase 03d-2 pending-result guard.
- Adding message, SMS, email, or notification sending.
- Adding result announcement template text.
- Adding interview/evaluation score aggregation.
- Adding Excel/PDF export.
- Adding read audit logging implementation.
- Reusing admin stage-result response DTOs for applicant-facing APIs.

## Changed Files

This design phase changes documentation only.

| Path | Type | Notes |
|---|---|---|
| `docs/codex/design/phase-03d-4-5-result-read-correction-design.md` | New | Codex reference design for Phase 03d-4 and 03d-5 |
| `docs/codex/reports/phase-03d-4-5-result-read-correction-design.html` | New | Self-contained human-readable report generated from this design |
| `docs/codex/design/phase-03d-stage-result-design.md` | Modified | Adds latest Phase 03d-4/03d-5 design note and roadmap correction |
| `docs/codex/design/phase-03-application-design.md` | Modified | Adds applicant result read and correction design note |
| `docs/codex/07-implementation-history.md` | Modified | Adds design history entry |

## Current Context

### Implemented Before This Design

| Phase | Implemented |
|---|---|
| Phase 03d-1 | `StageResult`, initialize, admin list |
| Phase 03d-2 | admin result update/bulk update, Stage announce pending-result guard |
| Phase 03d-3 | admin application detail lazy timeline API |

### Key Existing Policies

- StageResult rows are unique by `Stage + JobApplication`.
- Initialize targets `SUBMITTED` applications only.
- General result update is allowed only while `Stage.status == IN_PROGRESS`.
- Stage announce rejects missing StageResult rows or remaining `PENDING` results.
- Admin application timeline can read `DRAFT`, `SUBMITTED`, and `WITHDRAWN`.
- Admin timeline exposes `comment` but does not expose `decidedBy`.
- The admin timeline DTO must not be reused for applicant-facing result read.

## Phase Split

| Phase | Goal | API Candidate | Main Risk Controlled |
|---|---|---|---|
| Phase 03d-4 | Applicant result read | `GET /applications/{applicationId}/stage-results` | Prevent pre-announcement or internal admin data exposure |
| Phase 03d-5 | Result correction history | `POST /admin/stages/{stageId}/results/{resultId}/correct`, `GET /admin/stages/{stageId}/results/{resultId}/histories` | Preserve audit trail after announced result changes |

Recommended order:

1. Implement Phase 03d-4 first so applicant-visible response and visibility rules are fixed.
2. Implement Phase 03d-5 second so corrections mutate the same latest result that applicants read.

## Phase 03d-4 Applicant Result Read Design

### API List

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| `GET` | `/applications/{applicationId}/stage-results` | Applicant reads their announced stage results | none | `ApiResponse<List<ApplicantStageResultResponse>>` |

No POST, PUT, PATCH, or DELETE endpoint is recommended for applicant result read.

### Access Policy

| Rule | Recommended Behavior |
|---|---|
| Authentication | Applicant login required |
| Ownership | Applicant can read only their own `JobApplication` |
| Employee/Admin on applicant path | Reject through applicant identity resolution |
| Missing application | Not found |
| Other applicant's application | Not found or access denied according to existing application path behavior |

Implementation should follow the current applicant API pattern:

- Controller resolves current applicant using the existing applicant authentication helper.
- Service receives `applicantId` and `applicationId`.
- Service fetches by `applicationId + applicantId`, not by `applicationId` alone.

### Application Status Policy

| `JobApplication.status` | Applicant result read |
|---|---|
| `DRAFT` | Not eligible. Recommended response is business failure because draft applications are not submitted result targets. |
| `SUBMITTED` | Eligible. Return only visible announced stage results. |
| `WITHDRAWN` | Eligible. Return only visible announced stage results already associated with the withdrawn application. |

Rationale:

- A draft application has not entered the stage result process.
- A submitted application is the normal result-read target.
- A withdrawn application may still need to see previously announced outcomes or final administrative status, but it must not receive newly initialized results.

### Stage Visibility Guard

Applicant-facing visibility must be based primarily on `Stage.status`.

| `Stage.status` | Applicant API behavior | Reason |
|---|---|---|
| `READY` | Do not return a row | Stage existence and schedule can be internal operation data |
| `IN_PROGRESS` | Do not return a row | A "not announced yet" row can leak stage progress |
| `RESULT_ANNOUNCED` | Return row if a matching StageResult exists | Result has been announced |
| `CLOSED` | Return row if a matching StageResult exists | Closed announced stage remains readable |

Recommended first guard:

```text
Stage.status in (RESULT_ANNOUNCED, CLOSED)
```

`resultAnnouncementDateTime` is display data in the first implementation.

If scheduled release becomes necessary later, extend the guard to:

```text
Stage.status in (RESULT_ANNOUNCED, CLOSED)
and now >= Stage.resultAnnouncementDateTime
```

Do not implement the scheduled release guard until the product policy explicitly requires it.

### Missing Result Row Policy

The existing Phase 03d-2 announce guard should prevent announced stages from having missing or `PENDING` result rows.

Applicant API fallback policy:

- If a visible stage has no matching `StageResult`, do not expose a partial row to the applicant.
- Treat it as data inconsistency for logs or admin follow-up.
- Do not expose `resultStatus = null` to applicants.

This differs from the admin timeline, where missing rows are intentionally visible as null result fields.

### Applicant Response DTO Candidate

Recommended DTO name:

```text
ApplicantStageResultResponse
```

Recommended fields:

| Field | Source | Exposure Reason |
|---|---|---|
| `stageName` | `Stage.stageName` | Applicant display label |
| `stageType` | `Stage.stageType` | Client grouping or display |
| `stageOrder` | `Stage.stageOrder` | Timeline sorting/display |
| `resultStatus` | `StageResult.resultStatus` | Main announced result |
| `resultAnnouncementDateTime` | `Stage.resultAnnouncementDateTime` | Displayed announcement time |
| `decidedAt` | `StageResult.decidedAt` | Decision time if available |

Fields that must not be exposed:

| Field | Reason |
|---|---|
| `stageResultId` | Internal result row id is not needed by applicants |
| `score` | Can be internal evaluation data |
| `comment` | May contain admin memo or sensitive internal note |
| `decidedBy` | Internal employee/admin identity |
| `createdAt`, `updatedAt`, `createdBy`, `updatedBy` | Internal audit fields |

`AdminApplicationStageResultResponse` must not be reused because it contains admin-oriented fields such as `comment` and nullable result-row behavior.

### Entity/DTO/Service/Controller Summary

No classes are implemented in this design phase. Candidate implementation units:

| Layer | Candidate | Notes |
|---|---|---|
| Response DTO | `ApplicantStageResultResponse` | Separate from admin DTO |
| Repository | `StageResultRepository` method candidate | Load applicant-owned visible results with Stage fetch |
| Service | `ApplicationStageResultService` or existing application service extension | Validate ownership and visibility policy |
| Controller | `ApplicationStageResultController` or existing `ApplicationController` extension | Expose GET only |
| Test | Service and controller tests | Cover ownership, status, visibility, and field exclusion |

Recommended service shape:

```text
getApplicantStageResults(Long applicantId, Long applicationId)
```

Recommended query shape:

```text
find visible StageResult rows by jobApplicationId
where Stage.status in (RESULT_ANNOUNCED, CLOSED)
order by Stage.stageOrder asc, Stage.id asc
```

The service should first validate applicant ownership by loading the `JobApplication` through the existing applicant-owned lookup policy.

## Phase 03d-5 Result Correction Design

### API List

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| `POST` | `/admin/stages/{stageId}/results/{resultId}/correct` | Correct an announced or closed result with mandatory reason | `StageResultCorrectionRequest` | `ApiResponse<AdminStageResultResponse>` |
| `GET` | `/admin/stages/{stageId}/results/{resultId}/histories` | Read correction history for one result | none | `ApiResponse<List<StageResultCorrectionHistoryResponse>>` |

No PUT, PATCH, or DELETE endpoint is recommended.

### Correction Timing Policy

| Stage status | General update API | Correction API |
|---|---|---|
| `READY` | Blocked by current policy | Not applicable |
| `IN_PROGRESS` | Allowed through existing update/bulk APIs | Not allowed; use regular update before announcement |
| `RESULT_ANNOUNCED` | Blocked | Allowed with reason |
| `CLOSED` | Blocked | Allowed with reason if business permits post-close correction |

Default recommendation:

- Before announcement: use the existing admin result update API.
- After announcement: use the correction API only.

### Correction Request Candidate

Recommended DTO name:

```text
StageResultCorrectionRequest
```

Recommended fields:

| Field | Validation | Notes |
|---|---|---|
| `resultStatus` | required | New final result status |
| `score` | nullable | Explicitly nullable to allow score removal |
| `comment` | nullable, length-limited | Admin-only memo; not applicant-visible |
| `reason` | required, not blank, length-limited | Mandatory correction reason |

`reason` is mandatory because a post-announcement result change affects applicant-facing state and may trigger later notification or audit review.

### Correction History Entity Candidate

Recommended entity name:

```text
StageResultCorrectionHistory
```

Recommended table name:

```text
stage_result_correction_history
```

Recommended fields:

| Field | Type Candidate | Required | Notes |
|---|---|---:|---|
| `id` | `Long` | Yes | Primary key |
| `stageResult` | `StageResult` | Yes | LAZY many-to-one |
| `correctedAt` | `LocalDateTime` | Yes | Correction command time |
| `correctedBy` | `String` or `Employee` candidate | Yes | Current admin identity source must be finalized |
| `reason` | `String` | Yes | Mandatory correction reason |
| `previousStatus` | `StageResultStatus` | Yes | Before correction |
| `newStatus` | `StageResultStatus` | Yes | After correction |
| `previousScore` | score type | No | Before correction |
| `newScore` | score type | No | After correction |
| `previousComment` | `String` | No | Before correction, admin-only |
| `newComment` | `String` | No | After correction, admin-only |
| `previousDecidedAt` | `LocalDateTime` | No | Before correction |
| `newDecidedAt` | `LocalDateTime` | Yes | After correction |

Relationship policy:

- `StageResultCorrectionHistory -> StageResult`: N:1 unidirectional.
- `StageResult` should not receive a correction history collection in the first implementation.
- No cascade.
- No orphanRemoval.

### Correction Behavior

Recommended transaction flow:

1. Validate `Stage` exists.
2. Validate `StageResult` exists and belongs to the requested `stageId`.
3. Validate `Stage.status` allows correction.
4. Validate request status and reason.
5. Capture previous status, score, comment, and decidedAt.
6. Update the current `StageResult` to the corrected latest state.
7. Set `decidedAt` to the correction command time.
8. Persist `StageResultCorrectionHistory`.
9. Return the updated admin result response.

Correction must mutate the latest `StageResult` row so list screens and applicant-facing reads see the current result. History is append-only and admin-only.

### Correction History Response Candidate

Recommended DTO name:

```text
StageResultCorrectionHistoryResponse
```

Recommended fields:

| Field | Notes |
|---|---|
| `historyId` | Correction history row id |
| `stageResultId` | Corrected result row id |
| `correctedAt` | Correction time |
| `correctedBy` | Admin identity display or id, subject to security policy |
| `reason` | Correction reason |
| `previousStatus` / `newStatus` | Status change |
| `previousScore` / `newScore` | Score change |
| `previousComment` / `newComment` | Admin-only memo change |
| `previousDecidedAt` / `newDecidedAt` | Decision timestamp change |

This response is admin-only and must not be returned from applicant-facing APIs.

### Applicant Exposure After Correction

Applicant-facing API policy:

- Return only the latest corrected `StageResult`.
- Do not expose correction history.
- Do not expose correction reason.
- Do not expose admin comments.
- Do not expose `correctedBy`.

Message or notification for corrected results is deferred. A later message phase can compare correction history and decide whether to notify affected applicants.

## Visibility Matrix

### Applicant Result Read

| Application Status | Stage `READY` | Stage `IN_PROGRESS` | Stage `RESULT_ANNOUNCED` | Stage `CLOSED` |
|---|---|---|---|---|
| `DRAFT` | blocked | blocked | blocked | blocked |
| `SUBMITTED` | no row | no row | latest result only | latest result only |
| `WITHDRAWN` | no row | no row | latest result only | latest result only |

### Admin Result Read

| API | Application Status | Stage Status Guard | Missing Result Rows |
|---|---|---|---|
| `GET /admin/stages/{stageId}/results` | StageResult list source | no applicant visibility guard | list existing rows |
| `GET /admin/applications/{applicationId}/stage-results` | `DRAFT`, `SUBMITTED`, `WITHDRAWN` | no applicant visibility guard | returns stage rows with null result fields |
| `GET /applications/{applicationId}/stage-results` | `SUBMITTED`, `WITHDRAWN` | `RESULT_ANNOUNCED`, `CLOSED` only | do not expose partial row |

## Validation and Business Rules

### Phase 03d-4 Rules

- Applicant result read requires applicant ownership.
- `DRAFT` applications are not eligible.
- `SUBMITTED` and `WITHDRAWN` applications are eligible.
- Only `RESULT_ANNOUNCED` and `CLOSED` stages are visible.
- `READY` and `IN_PROGRESS` stages are not returned.
- `resultAnnouncementDateTime` is display data only in the first implementation.
- Missing visible result rows are not returned to applicants.
- Applicant response excludes score, comment, decidedBy, and audit fields.
- Admin DTOs are not reused for applicant responses.

### Phase 03d-5 Rules

- General update remains the pre-announcement mutation path.
- Post-announcement mutation uses correction command only.
- Correction reason is required.
- Correction mutates the latest `StageResult`.
- Correction history is append-only.
- Correction history is admin-only.
- Applicant result read exposes only the latest corrected result, not the correction history.
- No notification is sent by the correction command in the first implementation.

## Test Plan

### Phase 03d-4 Test Candidates

| Test Type | Coverage |
|---|---|
| Service | Applicant can read own announced results |
| Service | Other applicant's application is rejected |
| Service | `DRAFT` application is rejected |
| Service | `SUBMITTED` and `WITHDRAWN` are readable |
| Service | `READY` and `IN_PROGRESS` stages are excluded |
| Service | `RESULT_ANNOUNCED` and `CLOSED` stages are included |
| Service | Missing StageResult for a visible stage is not exposed |
| Controller | GET response is `ApiResponse<List<ApplicantStageResultResponse>>` |
| Controller | POST/PUT/PATCH/DELETE unsupported |
| Controller | Response does not serialize `score`, `comment`, or `decidedBy` |

### Phase 03d-5 Test Candidates

| Test Type | Coverage |
|---|---|
| Service | Correction succeeds after `RESULT_ANNOUNCED` |
| Service | Correction succeeds after `CLOSED` if policy allows |
| Service | Correction fails while `IN_PROGRESS` and regular update should be used |
| Service | Missing/blank reason fails |
| Service | Result id/stage mismatch fails |
| Service | History row captures previous/new status, score, comment, decidedAt |
| Service | Latest `StageResult` is updated |
| Controller | Correction API returns updated admin result |
| Controller | History API returns correction history in order |
| Controller | PUT/PATCH/DELETE unsupported |

## Test Commands

This design phase does not run Gradle tests because it does not change Java code or runtime configuration.

Recommended commands when Phase 03d-4 is implemented:

```powershell
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationStageResultServiceTest --tests com.shinyoung.recruit.controller.ApplicationStageResultControllerTest
```

Recommended commands when Phase 03d-5 is implemented:

```powershell
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.StageResultCorrectionServiceTest --tests com.shinyoung.recruit.controller.StageResultCorrectionControllerTest
```

Recommended full regression command after either implementation:

```powershell
$env:AES_SECRET_KEY='***'; .\gradlew.bat clean test --no-daemon
```

## Test Results

- Not executed.
- Reason: documentation-only design phase.
- Verification performed by document consistency review:
  - No Java/test/build/schema/SecurityConfig file is changed.
  - Applicant-facing API and correction API are described as candidates, not implemented APIs.
  - Applicant response excludes `score`, `comment`, and `decidedBy`.
  - Phase 03d-4 and Phase 03d-5 implementation and test scopes are separated.

## Remaining Issues

- Exact current-admin identity source for `correctedBy` must be finalized before Phase 03d-5 implementation.
- Whether `CLOSED` stages allow correction needs product confirmation; this design recommends allowing it with reason if post-close operational correction is required.
- Scheduled announcement by `resultAnnouncementDateTime` is not included in the first applicant read guard.
- Correction notification/message sending is deferred.
- Read audit logging is deferred to a security/audit phase.
- Applicant-facing wording/template for pass/fail result display is deferred.

## Next Phase Recommendation

Implement Phase 03d-4 first.

Recommended Phase 03d-4 implementation unit:

| Step | Scope |
|---|---|
| 1 | Add `ApplicantStageResultResponse` |
| 2 | Add repository query for applicant-visible StageResult rows |
| 3 | Add applicant ownership and visibility service |
| 4 | Add `GET /applications/{applicationId}/stage-results` |
| 5 | Add service/controller tests for ownership, status, visibility, and field exclusion |
| 6 | Update implementation docs and human report |

Then implement Phase 03d-5:

| Step | Scope |
|---|---|
| 1 | Add `StageResultCorrectionHistory` and repository |
| 2 | Add correction request and history response DTOs |
| 3 | Add correction service command |
| 4 | Add correction and history admin controller endpoints |
| 5 | Add tests for post-announcement correction, required reason, and history capture |
| 6 | Update implementation docs and human report |

