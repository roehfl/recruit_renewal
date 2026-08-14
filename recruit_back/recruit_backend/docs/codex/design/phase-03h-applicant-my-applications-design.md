# Phase 03h-1 Applicant My Applications Design

## Phase 03h-4 Dashboard Implementation Note

Phase 03h-4 implemented the applicant dashboard summary API:

```text
GET /applications/{applicationId:[0-9]+}/dashboard
```

Implementation summary:

- Added dashboard response DTOs:
  - `ApplicationDashboardResponse`
  - `ApplicationCompletionSummaryResponse`
  - `ApplicationSectionReadinessResponse`
- Added `ApplicationCompletionReadChecker` as a read-only mirror of current submit readiness policy.
- Added `ApplicationDashboardService`.
- Added applicant-owned dashboard fetch query to `JobApplicationRepository`.
- Added optional existence checks for certificate, language, award, and gap period repositories.
- Added the dashboard endpoint to `ApplicationController`.
- Preserved `GET /applications/me`.
- Preserved detailed result API `GET /applications/{applicationId}/stage-results`.
- Preserved `ApplicationSubmitValidator` and submit command behavior.

Action flags:

- `accepting = PUBLISHED posting + reception period`.
- `editable = DRAFT + accepting`.
- `submittable = DRAFT + accepting + submitBlockingIssueCount == 0`.
- `withdrawable = SUBMITTED + accepting`.
- `WITHDRAWN` returns all command flags as `false`.

Completion checker:

- Blocking readiness covers education, career, military, active required questions, and answer length.
- Optional guidance covers certificate, language, award, and gap period.
- Attachment readiness remains deferred.

Result summary:

- Same visibility policy as Phase 03h-2.
- Only `Stage.status == RESULT_ANNOUNCED || CLOSED` is visible.
- Latest result uses `stageOrder DESC, stage.id DESC`.
- Internal result fields remain hidden.

References:

- `docs/codex/implementation/phase-03h-4-applicant-application-dashboard.md`
- `docs/codex/reports/phase-03h-4-applicant-application-dashboard.html`

## Phase 03h-3 Dashboard Design Note

Phase 03h-3 designs the applicant application dashboard summary API:

```text
GET /applications/{applicationId}/dashboard
```

Design conclusion:

- The dashboard is a read-only summary for one applicant-owned application.
- It sits between `GET /applications/me` and detailed application section APIs.
- It is applicant-only under the same `/applications/**` policy.
- Employee/admin users are not allowed to use this applicant endpoint.
- The request must not accept `applicantId`; ownership comes from the current session applicant.
- Recommended response wrapper: `ApiResponse<ApplicationDashboardResponse>`.
- Recommended top-level fields:
  - `applicationId`, `jobPostingId`, `jobPostingTitle`, `jobPositionName`
  - `applicationStatus`, `accepting`
  - `editable`, `submittable`, `withdrawable`
  - `submittedAt`, `withdrawnAt`
  - `completionSummary`
  - `requiredMissingSections`
  - `optionalIncompleteSections`
  - `latestAnnouncedStageName`, `latestResultStatus`
- Action flag policy:
  - `editable = DRAFT + accepting`
  - `submittable = DRAFT + accepting + no submit-blocking readiness issue`
  - `withdrawable = SUBMITTED + accepting`
  - `WITHDRAWN` returns all command flags as `false`
- Completion policy:
  - Mirror current `ApplicationSubmitValidator` rules through a read-only checker.
  - Include `useEducation`, `useCareer`, `useMilitary`, active required question answer, and answer length checks as submit-blocking readiness.
  - Treat `useCertificate`, `useLanguage`, `useAward`, and `useGapPeriod` as optional guidance under the current submit policy.
  - Defer Attachment readiness until a config flag or business rule exists.
- Result summary policy:
  - Same as Phase 03h-2: only `Stage.status == RESULT_ANNOUNCED || CLOSED` is visible.
  - Detailed result rows remain in `GET /applications/{applicationId}/stage-results`.
  - `score`, `comment`, actor fields, and correction history remain hidden.
- Phase 03h-4 recommendation:
  - Implement a narrow read-only dashboard endpoint.
  - Add dashboard DTOs, a dashboard service, and a read-only completion checker.
  - Add service/controller tests for ownership, action flags, completion policy, result summary, and 401/403 behavior.
- References:
  - `docs/codex/design/phase-03h-3-applicant-application-dashboard-design.md`
  - `docs/codex/reports/phase-03h-3-applicant-application-dashboard-design.html`

## Phase 03h-2 Implementation Note

Phase 03h-2 implemented this design as `GET /applications/me`.

Implemented:

- Added `MyApplicationResponse`.
- Added `JobApplicationRepository.findMyApplications(Long applicantId, Pageable pageable)`.
- Added `StageResultRepository.findVisibleByJobApplicationIdsForApplicantSummary(Collection<Long>)`.
- Added `JobApplicationService.getMyApplications(Long applicantId, int page, int size)`.
- Added `GET /applications/me` to `ApplicationController`.
- Narrowed existing numeric application id routes in `ApplicationController` so `/applications/me` does not collide with `{applicationId}` command mappings.
- Added service and controller tests.
- Added implementation reference and human-readable report:
  - `docs/codex/implementation/phase-03h-2-applicant-my-applications.md`
  - `docs/codex/reports/phase-03h-2-applicant-my-applications.html`

Preserved:

- Existing `GET /applications/{applicationId}/stage-results`.
- Applicant result response field policy.
- Admin APIs.
- `SecurityConfig`.
- DB schema.
- LDAP/security handlers.
- Message/notification and read audit behavior.

Verification:

- `JobApplicationServiceTest`: success after retry.
- `ApplicationControllerTest`: success.
- `ApplicationStageResultServiceTest` + `ApplicationStageResultControllerTest`: success.
- `StageResultServiceTest` + `StageResultCorrectionServiceTest`: success.
- `.\gradlew.bat clean test --no-daemon`: success.

## Phase Name

Phase 03h-1 - Applicant My Applications Design

## Purpose

This phase defines the applicant-facing "my applications" list API before implementation.

The target API is:

```text
GET /applications/me
```

The API lets a logged-in applicant view only their own applications, including draft, submitted, and withdrawn applications. It is intentionally separate from the existing single-application detail API and the existing applicant stage-result detail API.

This is a design-only phase. No Java source, test source, `SecurityConfig`, Gradle, YAML, DB schema, Repository, Service, Controller, DTO, or API implementation is changed.

## Scope

- Define `GET /applications/me` API contract candidate.
- Define authentication and authorization policy.
- Define pagination and default sorting.
- Define list response fields.
- Define how much StageResult information can be summarized in the list.
- Define repository/service/controller implementation candidates for Phase 03h-2.
- Define test strategy for Phase 03h-2.
- Produce a paired self-contained HTML report.
- Update Phase 03 application design and implementation history documents.

## Out of Scope

- Implementing `GET /applications/me`.
- Creating `MyApplicationResponse` or any Java DTO.
- Adding repository queries.
- Adding service methods.
- Adding controller methods.
- Modifying `ApplicationController`.
- Modifying `SecurityConfig`.
- Modifying `StageResultRepository` or applicant result APIs.
- Changing `GET /applications/{applicationId}/stage-results`.
- Changing applicant result response field exposure.
- Adding dashboard summary, completion rate, or missing-section guidance.
- Adding admin APIs.
- Running Gradle tests for this documentation-only phase.

## Changed Files

This design phase changes documentation only.

| Path | Type | Notes |
|---|---|---|
| `docs/codex/design/phase-03h-applicant-my-applications-design.md` | New | Codex reference design for applicant my applications list |
| `docs/codex/reports/phase-03h-applicant-my-applications-design.html` | New | Self-contained human-readable report generated from this design |
| `docs/codex/design/phase-03-application-design.md` | Modified | Adds Phase 03h-1 design note |
| `docs/codex/07-implementation-history.md` | Modified | Adds design history entry |

No Java, test, build, YAML, security, schema, or static resource file is changed by this phase.

## Current Context

### Existing Application APIs

| Method | Path | Current Purpose |
|---|---|---|
| `POST` | `/applications` | Create applicant application |
| `GET` | `/applications/{applicationId}` | Read one owned applicant application |
| `POST` | `/applications/{applicationId}` | Update draft application |
| `POST` | `/applications/{applicationId}/submit` | Submit draft application |
| `POST` | `/applications/{applicationId}/withdraw` | Withdraw submitted application |
| `GET` | `/job-postings/{jobPostingId}/application` | Read current applicant's application for one posting |
| `GET` | `/applications/{applicationId}/stage-results` | Read announced stage results for one owned application |

### Existing Security Context

Phase 03e-3 and 03e-4 established the target applicant path policy:

- `/applications/**` requires `ROLE_APPLICANT`.
- Employee/admin users cannot use applicant application APIs.
- Unauthenticated users receive `401 + ApiResponse.fail(...)`.
- Authenticated users without applicant authority receive `403 + ApiResponse.fail(...)`.
- Service-level applicant ownership validation remains mandatory.

`GET /applications/me` should follow the same `/applications/**` policy.

### Existing Domain Facts

| Domain | Current Implementation Fact |
|---|---|
| `JobApplication` | Root application entity with applicant, job posting, job position, status, submitted/withdrawn timestamps, and snapshots |
| `JobApplicationStatus` | `DRAFT`, `SUBMITTED`, `WITHDRAWN` |
| `JobPosting` | Has `title`, `receptionStartDateTime`, `receptionEndDateTime`, and `status` |
| `JobPostingStatus` | `DRAFT`, `PUBLISHED`, `CLOSED` |
| `Stage` | Has `stageName`, `stageOrder`, `status`, and result announcement date/time |
| `StageStatus` | `READY`, `IN_PROGRESS`, `RESULT_ANNOUNCED`, `CLOSED` |
| `StageResult` | Has latest result status plus internal score/comment/actor fields |
| `ApplicantStageResultResponse` | Already hides score, comment, decidedBy, correctedBy, correction history, and internal result id |

## API List

### Recommended API

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| `GET` | `/applications/me` | Logged-in applicant reads their own application list | `page`, `size` query parameters | `ApiResponse<PageResponse<MyApplicationResponse>>` |

No `POST`, `PUT`, `PATCH`, or `DELETE` endpoint is part of this phase.

### Query Parameters

| Parameter | Required | Recommended Default | Notes |
|---|---:|---|---|
| `page` | No | `0` | Zero-based page index, consistent with existing `PageResponse` usage |
| `size` | No | existing service default or `20` | Validate positive size and reasonable upper bound in implementation |

No status filter is recommended for Phase 03h-2. The first implementation should return all owned application statuses and let the client group them.

### Default Sorting

Recommended default sort:

```text
createdAt DESC, id DESC
```

Rationale:

- `createdAt DESC` puts recently created applications first.
- `id DESC` gives deterministic order when multiple rows have the same creation timestamp.
- Sorting should be fixed by the service in Phase 03h-2 rather than exposing arbitrary sort parameters in the first implementation.

## Access Policy

| Caller | Behavior |
|---|---|
| Anonymous | `401 + ApiResponse.fail(...)` through Security exception handling |
| Applicant with `ROLE_APPLICANT` | Allowed; only their own applications are returned |
| Applicant with no applications | Allowed; empty `PageResponse` |
| Employee/admin | `403 + ApiResponse.fail(...)`; must use admin application APIs |
| Applicant attempting to infer another applicant's data | Not possible through the API contract because applicant id comes from current session only |

Controller implementation should use the existing `CurrentApplicantService` pattern:

```text
CustomUserDetails -> CurrentApplicantService -> applicantId -> JobApplicationService
```

The request must not accept `applicantId` as a query parameter.

## Response DTO Candidate

Recommended DTO name:

```text
MyApplicationResponse
```

Recommended wrapper:

```text
ApiResponse<PageResponse<MyApplicationResponse>>
```

### Response Fields

| Field | Source | Required | Notes |
|---|---|---:|---|
| `applicationId` | `JobApplication.id` | Yes | Application identifier for navigation to detail |
| `jobPostingId` | `JobPosting.id` | Yes | Posting identifier |
| `jobPostingTitle` | `JobApplication.jobPostingTitleSnapshot` or current `JobPosting.title` | Yes | Prefer snapshot for stable application list display |
| `jobPostingStatus` | `JobPosting.status` | Yes | Shows whether posting is `PUBLISHED` or `CLOSED`; closed postings remain listed |
| `jobPositionId` | `JobPosition.id` | Yes | Position identifier |
| `jobPositionName` | `JobApplication.jobPositionNameSnapshot` or current `JobPosition.positionName` | Yes | Prefer snapshot for stable display |
| `applicationStatus` | `JobApplication.status` | Yes | `DRAFT`, `SUBMITTED`, or `WITHDRAWN` |
| `createdAt` | `BaseEntity.createdAt` | Yes | Sort key and display data |
| `submittedAt` | `JobApplication.submittedAt` | No | Nullable for draft applications |
| `withdrawnAt` | `JobApplication.withdrawnAt` | No | Nullable for non-withdrawn applications |
| `receptionStartDateTime` | `JobPosting.receptionStartDateTime` | Yes | Display and accepting calculation input |
| `receptionEndDateTime` | `JobPosting.receptionEndDateTime` | Yes | Display and accepting calculation input |
| `accepting` | service-calculated boolean | Yes | True when posting is published and current time is inside reception period |
| `announcedResultCount` | StageResult summary | Yes | Count of visible announced results for this application |
| `latestAnnouncedStageName` | StageResult summary | No | Latest visible announced stage by `stageOrder DESC, stageId DESC` candidate |
| `latestResultStatus` | StageResult summary | No | Latest visible announced result status |

### Field Exposure Rules

Do not include:

| Field | Reason |
|---|---|
| `applicantId` | The endpoint is implicitly scoped to the current applicant; exposing it is unnecessary |
| applicant name/email/phone/CI/address | Personal data not needed for list display |
| `stageResultId` | Internal result row id |
| `score` | Internal evaluation data |
| `comment` | Admin memo or sensitive internal note |
| `decidedBy` | Internal employee/admin identity |
| `correctedBy` | Internal correction actor |
| correction reason/history | Admin-only audit data |
| `storedFileName`, `storagePath`, attachment download details | Not part of the list view |

## Application Inclusion Policy

| `JobApplication.status` | Included in `/applications/me` | Notes |
|---|---:|---|
| `DRAFT` | Yes | Applicant needs to resume or inspect draft applications |
| `SUBMITTED` | Yes | Normal submitted application list item |
| `WITHDRAWN` | Yes | Applicant should still see historical withdrawn applications |

Job posting status does not hide existing applications:

| `JobPosting.status` | Existing application listed? | Notes |
|---|---:|---|
| `DRAFT` | Yes if an application exists | This should be rare, but the list should not hide owned data |
| `PUBLISHED` | Yes | Normal active posting |
| `CLOSED` | Yes | Closed postings remain visible in the applicant's history |

## Accepting Calculation

Recommended first implementation:

```text
accepting =
  jobPosting.status == PUBLISHED
  and now >= receptionStartDateTime
  and now <= receptionEndDateTime
```

Use the same `Clock`-based pattern already used in application/job posting services so tests can fix the current time.

`accepting` is display data for the list. It must not loosen existing command rules for submit, update, or withdraw.

## Result Summary Policy

### Recommended Summary

`/applications/me` should include only a compact result summary:

- `announcedResultCount`
- `latestAnnouncedStageName`
- `latestResultStatus`

Detailed results remain in:

```text
GET /applications/{applicationId}/stage-results
```

### Visibility Guard

Use the same visibility basis as the existing applicant result read:

```text
Stage.status == RESULT_ANNOUNCED || Stage.status == CLOSED
```

Do not include `READY` or `IN_PROGRESS` stages in list summaries.

### Latest Result Definition

Recommended first implementation:

```text
latest announced result =
  visible StageResult for the application
  ordered by Stage.stageOrder DESC, Stage.id DESC
  first row
```

Rationale:

- Applicant-facing detail results are ordered by `stageOrder ASC, stage.id ASC`.
- The list needs a compact "latest visible stage/result" hint.
- `stageOrder DESC, stage.id DESC` is deterministic and matches the stage sequence model.

### No Result Case

If no visible announced result exists:

| Field | Value |
|---|---|
| `announcedResultCount` | `0` |
| `latestAnnouncedStageName` | `null` |
| `latestResultStatus` | `null` |

Do not synthesize a "pending" row for applicants in this list.

## Repository Design Candidate

### JobApplicationRepository

Recommended query shape for Phase 03h-2:

```text
find owned applications by applicantId
fetch jobPosting and jobPosition
order by createdAt desc, id desc
return Page<JobApplication>
```

Candidate method names:

```java
Page<JobApplication> findMyApplications(Long applicantId, Pageable pageable)
```

or:

```java
Page<JobApplication> findByApplicantIdOrderByCreatedAtDescIdDesc(Long applicantId, Pageable pageable)
```

Prefer an explicit `@Query` plus `@EntityGraph(attributePaths = {"jobPosting", "jobPosition"})` or `join fetch` equivalent that remains compatible with pageable count queries.

The current repository does not yet expose an applicant pageable list query. Phase 03h-2 should add one rather than reusing admin search queries.

### StageResultRepository

Avoid N+1 result summary loading.

Recommended Phase 03h-2 approach:

1. Load one page of `JobApplication`.
2. Extract application ids from page content.
3. Batch load visible StageResult rows for those application ids with Stage fetched.
4. Group by `jobApplication.id` in service.
5. Compute count and latest visible result per application.

Candidate query shape:

```text
select result
from StageResult result
join fetch result.stage stage
join fetch result.jobApplication application
where application.id in :applicationIds
  and stage.status in (RESULT_ANNOUNCED, CLOSED)
order by application.id asc, stage.stageOrder asc, stage.id asc
```

This query should not fetch score/comment/actor as response fields, even though they exist on the entity.

## Service Design Candidate

Recommended service location:

```text
JobApplicationService
```

Recommended method:

```java
PageResponse<MyApplicationResponse> getMyApplications(Long applicantId, int page, int size)
```

Rationale:

- `JobApplicationService` already owns applicant application create/read/update/submit/withdraw flows.
- The new endpoint is a list read for the same aggregate.
- A separate `ApplicationMyPageService` is not necessary until my-page/dashboard behavior grows beyond application list reads.

Service responsibilities:

- Validate page and size.
- Build fixed sort: `createdAt DESC, id DESC`.
- Load current applicant's applications by `applicantId`.
- Preserve `DRAFT`, `SUBMITTED`, and `WITHDRAWN`.
- Load result summaries in batch.
- Calculate `accepting` from `Clock`.
- Map to `MyApplicationResponse`.
- Return `PageResponse`.

## Controller Design Candidate

Recommended location:

```text
ApplicationController
```

Recommended method:

```java
@GetMapping("/applications/me")
public ResponseEntity<ApiResponse<PageResponse<MyApplicationResponse>>> getMyApplications(...)
```

Controller responsibilities:

- Resolve current applicant through `CurrentApplicantService`.
- Accept optional `page` and `size` query parameters.
- Delegate to `JobApplicationService`.
- Wrap response with `ApiResponse.success(...)`.

Controller must not:

- Accept `applicantId`.
- Load entities directly.
- Compute result summaries.
- Expose admin DTOs.

## Entity, DTO, Service, Controller Summary

No class is implemented in this design phase. Candidate Phase 03h-2 classes/methods:

| Layer | Candidate | Type | Responsibility | Key Fields or Methods | Related Classes | Notes |
|---|---|---|---|---|---|---|
| Response DTO | `MyApplicationResponse` | Response DTO | One row in current applicant's application list | listed response fields above, static factory candidate | `JobApplication`, `JobPosting`, `StageResult` summary value | Must not expose applicant personal data or internal result fields |
| Repository | `JobApplicationRepository` | Repository method | Pageable applicant-owned application list | applicant id query, fixed sort via pageable | `JobApplication` | Do not reuse admin search for applicant path |
| Repository | `StageResultRepository` | Repository method | Batch visible result summary source | application id collection, visible stage statuses | `StageResult`, `Stage` | Avoid N+1 summary loading |
| Service | `JobApplicationService` | Service method | Application list orchestration | `getMyApplications(applicantId, page, size)` | `CurrentApplicantService`, repositories, `Clock` | Existing service extension recommended |
| Controller | `ApplicationController` | Controller method | HTTP endpoint for `/applications/me` | `GET /applications/me` | `ApiResponse`, `PageResponse` | Existing applicant API controller extension recommended |
| Test | `JobApplicationServiceTest` | Service test | Business list, sorting, summary, accepting | multiple list cases | repositories mocked or existing test style | Add focused service coverage |
| Test | `ApplicationControllerTest` | Controller test | API contract and security behavior | 200/401/403 and field hiding | Spring Security test | Follow Phase 03e-3/03e-4 patterns |

## Validation and Business Rules

- `GET /applications/me` requires applicant authentication.
- Only `ROLE_APPLICANT` can access the endpoint.
- Employee/admin users are rejected at URL authorization level.
- Service receives current applicant id from the authenticated principal, not from request input.
- The list includes `DRAFT`, `SUBMITTED`, and `WITHDRAWN`.
- The list includes applications even when the posting is `CLOSED`.
- Default sort is `createdAt DESC, id DESC`.
- `accepting` is calculated from posting status and reception period.
- Result summary uses only stages whose status is `RESULT_ANNOUNCED` or `CLOSED`.
- Result summary excludes unannounced `READY` and `IN_PROGRESS` stages.
- Result summary does not expose score, comment, decidedBy, correctedBy, reason, or history.
- Detailed result rows remain available only through `GET /applications/{applicationId}/stage-results`.
- Missing visible result rows are not synthesized.
- Arbitrary status/sort filters are deferred.

## Test Plan

Phase 03h-1 does not add or run tests. Phase 03h-2 should add the following coverage.

| Test Area | Coverage |
|---|---|
| Service | Applicant can read own application list |
| Service | Applications owned by other applicants are not included |
| Service | `DRAFT`, `SUBMITTED`, and `WITHDRAWN` are included |
| Service | Closed posting applications remain listed |
| Service | Default order is `createdAt DESC, id DESC` |
| Service | `accepting` is true for published posting inside reception period |
| Service | `accepting` is false outside reception period or when posting is closed |
| Service | Announced result count is calculated from visible stages only |
| Service | Latest announced stage/result uses visible stages only |
| Service | Unannounced `READY` and `IN_PROGRESS` StageResult rows are excluded from summary |
| Controller | `GET /applications/me` returns `ApiResponse<PageResponse<MyApplicationResponse>>` |
| Controller | Anonymous request returns 401 JSON |
| Controller | Employee/admin request returns 403 JSON |
| Controller | Applicant request succeeds |
| Controller | Response does not serialize score/comment/decidedBy/correctedBy/history |

## Test Commands

Not executed in this phase because this is documentation-only.

Recommended Phase 03h-2 targeted commands:

```powershell
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.JobApplicationServiceTest --tests com.shinyoung.recruit.controller.ApplicationControllerTest
```

Recommended full regression command after implementation:

```powershell
$env:AES_SECRET_KEY='***'; .\gradlew.bat clean test --no-daemon
```

## Test Results

- Not executed.
- Reason: documentation-only design phase.
- Verification performed by document consistency review:
  - No Java source changed.
  - No test source changed.
  - No `SecurityConfig`, build, YAML, or DB schema file changed.
  - API is documented as a candidate for Phase 03h-2, not implemented.
  - Applicant result field hiding policy remains consistent with Phase 03d-4 and Phase 03d-5.

## Remaining Issues

- Exact page size default and maximum should be aligned with existing service validation during Phase 03h-2.
- Whether `jobPostingTitle` and `jobPositionName` should use snapshots or live entity values should be confirmed; this design recommends snapshots for stable applicant history display.
- Whether to add status filters later is deferred.
- Whether `resultAnnouncementDateTime` should become a scheduled release guard is deferred; current design follows existing `Stage.status` visibility policy.
- Dashboard completion rate, missing required sections, and my-page summary cards are deferred to Phase 03h-3 or later.

## Next Phase Recommendation

Implement Phase 03h-2 as a narrow vertical slice:

| Step | Scope |
|---|---|
| 1 | Add `MyApplicationResponse` |
| 2 | Add applicant-owned pageable list query to `JobApplicationRepository` |
| 3 | Add batch visible result summary query to `StageResultRepository` |
| 4 | Extend `JobApplicationService` with `getMyApplications` |
| 5 | Add `GET /applications/me` to `ApplicationController` |
| 6 | Add service and controller tests for ownership, statuses, ordering, accepting, result summary, and security |
| 7 | Update implementation markdown and HTML report |

Phase 03h-3 candidate:

- Applicant dashboard summary.
- Application completion rate.
- Missing required section guidance.
- Result announcement badge/text policy.
