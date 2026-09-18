# Phase 03h-2 Applicant My Applications API

## Phase Summary

Phase 03h-2 implements the applicant-owned application list API:

```text
GET /applications/me
```

The endpoint returns a pageable list of the current applicant's applications with compact announced-result summary fields. It does not change the existing applicant stage-result detail API, admin APIs, `SecurityConfig`, DB schema, LDAP settings, message/notification behavior, or read audit logging.

## Purpose

- Let a logged-in applicant view their own `DRAFT`, `SUBMITTED`, and `WITHDRAWN` applications.
- Include existing applications even when the related posting is `CLOSED`.
- Return stable application/posting/position display fields and page metadata.
- Include only a compact result summary in the list.
- Keep detailed results in `GET /applications/{applicationId}/stage-results`.
- Avoid StageResult N+1 loading by batch querying visible result rows for the current page.

## Implemented Scope

- Added `MyApplicationResponse`.
- Added applicant-owned pageable list query to `JobApplicationRepository`.
- Added batch visible result summary query to `StageResultRepository`.
- Extended `JobApplicationService` with `getMyApplications`.
- Added `GET /applications/me` to `ApplicationController`.
- Narrowed numeric application id mappings in `ApplicationController` to avoid `/applications/me` colliding with `{applicationId}` command paths.
- Added service tests for ownership, status inclusion, closed postings, sorting, page/size validation, `accepting`, result summary, and empty pages.
- Added controller tests for API response shape, page metadata, field exclusion, 401/403 JSON behavior, empty page, and unsupported methods.
- Updated implementation/design/history docs and added this self-contained report pair.

## Out-of-Scope Items

- `SecurityConfig` changes.
- `GET /applications/{applicationId}/stage-results` changes.
- Applicant stage-result response changes.
- Admin API changes.
- Admin DTO reuse.
- `applicantId` query parameter.
- Status filter or sort query parameters.
- DB schema changes.
- `resultAnnouncementDateTime` scheduled-release guard.
- Dashboard completion rate or missing-section guidance.
- Message/notification implementation.
- Read audit logging.

## Changed Files

| Path | Type | Notes |
|---|---|---|
| `src/main/java/com/shinyoung/recruit/dto/response/MyApplicationResponse.java` | New | Applicant-safe list row DTO |
| `src/main/java/com/shinyoung/recruit/domain/repository/JobApplicationRepository.java` | Modified | Added applicant-owned pageable query |
| `src/main/java/com/shinyoung/recruit/domain/repository/StageResultRepository.java` | Modified | Added batch visible result summary query |
| `src/main/java/com/shinyoung/recruit/service/JobApplicationService.java` | Modified | Added list orchestration, accepting calculation, result summary grouping |
| `src/main/java/com/shinyoung/recruit/controller/ApplicationController.java` | Modified | Added `GET /applications/me`; narrowed numeric id mappings |
| `src/test/java/com/shinyoung/recruit/service/JobApplicationServiceTest.java` | Modified | Added service coverage for my applications list |
| `src/test/java/com/shinyoung/recruit/controller/ApplicationControllerTest.java` | Modified | Added API/security/field-exclusion coverage |
| `docs/codex/implementation/phase-03h-2-applicant-my-applications.md` | New | Implementation reference |
| `docs/codex/reports/phase-03h-2-applicant-my-applications.html` | New | Human-readable implementation report |
| `docs/codex/design/phase-03h-applicant-my-applications-design.md` | Modified | Added implementation note |
| `docs/codex/design/phase-03-application-design.md` | Modified | Added Phase 03h-2 implementation note |
| `docs/codex/07-implementation-history.md` | Modified | Added history entry |

## New Classes

| Package | Class | Class Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.dto.response` | `MyApplicationResponse` | Response DTO | Represents one current-applicant application list row with compact announced-result summary |

## Modified Classes

| Package | Class | Class Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.domain.repository` | `JobApplicationRepository` | Repository | Reads current applicant's applications as a pageable result |
| `com.shinyoung.recruit.domain.repository` | `StageResultRepository` | Repository | Reads applicant-visible result rows for a page of applications in one batch |
| `com.shinyoung.recruit.service` | `JobApplicationService` | Service | Orchestrates list lookup, paging validation, result summary, and `accepting` calculation |
| `com.shinyoung.recruit.controller` | `ApplicationController` | Controller | Exposes `GET /applications/me` and keeps applicant id sourced from the session principal |
| `com.shinyoung.recruit.service` | `JobApplicationServiceTest` | Test | Adds service policy coverage for the new list behavior |
| `com.shinyoung.recruit.controller` | `ApplicationControllerTest` | Test | Adds API contract, security, and field-exclusion coverage |

## Class-by-Class Explanation

| Package | Class | Class Type | Responsibility | Key Fields or Methods | Related Classes | Important Implementation Notes |
|---|---|---|---|---|---|---|
| `dto.response` | `MyApplicationResponse` | Response DTO | Applicant-safe list row | `from(JobApplication, boolean, long, String, StageResultStatus)` | `JobApplication`, `JobPosting`, `JobPosition`, `StageResultStatus` | Uses posting/position snapshots first, falls back to current entity fields, does not expose applicant personal data or internal result fields |
| `domain.repository` | `JobApplicationRepository` | Repository | Applicant-owned pageable lookup | `findMyApplications(Long applicantId, Pageable pageable)` | `JobApplication`, `JobPosting`, `JobPosition` | Uses `@EntityGraph` for to-one `jobPosting` and `jobPosition`; no collection fetch in pageable query |
| `domain.repository` | `StageResultRepository` | Repository | Batch visible result lookup | `findVisibleByJobApplicationIdsForApplicantSummary(Collection<Long>)` | `StageResult`, `Stage`, `StageStatus` | Filters `RESULT_ANNOUNCED` and `CLOSED`; fetches `stage` and `jobApplication`; result score/comment/actors are not mapped to response |
| `service` | `JobApplicationService` | Service | Applicant application list orchestration | `getMyApplications`, `loadApplicationResultSummaries`, `isAccepting` | `JobApplicationRepository`, `StageResultRepository`, `Clock`, `PageResponse` | Validates `page >= 0`, `1 <= size <= 100`; fixed sort `createdAt DESC, id DESC`; skips summary query for empty pages |
| `controller` | `ApplicationController` | Controller | Applicant HTTP API | `getMyApplications` | `CurrentApplicantService`, `ApiResponse`, `PageResponse` | Does not accept `applicantId`; uses `@AuthenticationPrincipal`; default query params are `page=0`, `size=20` |
| `service` | `JobApplicationServiceTest` | Test | Service regression and policy coverage | my-list test methods | `StageService`, `StageResultService` | Covers all requested service policies except query invocation count internals |
| `controller` | `ApplicationControllerTest` | Test | HTTP contract and security coverage | my-list test methods | `SecurityConfig`, Spring Security test helpers | Uses secured MockMvc for 401/403 checks and verifies forbidden fields are not serialized |

## API List

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| `GET` | `/applications/me` | Current applicant reads their own application list | Query: `page` default `0`, `size` default `20` | `ApiResponse<PageResponse<MyApplicationResponse>>` |

Unsupported by design:

- `POST /applications/me`
- `PUT /applications/me`
- `PATCH /applications/me`
- `DELETE /applications/me`

## Response Fields

| Field | Source | Notes |
|---|---|---|
| `applicationId` | `JobApplication.id` | Application navigation key |
| `jobPostingId` | `JobPosting.id` | Posting key |
| `jobPostingTitle` | snapshot first, current title fallback | Stable display |
| `jobPostingStatus` | `JobPosting.status` | `DRAFT`, `PUBLISHED`, `CLOSED` |
| `jobPositionId` | `JobPosition.id` | Position key |
| `jobPositionName` | snapshot first, current name fallback | Stable display |
| `applicationStatus` | `JobApplication.status` | `DRAFT`, `SUBMITTED`, `WITHDRAWN` |
| `createdAt` | `BaseEntity.createdAt` | Fixed sort key |
| `submittedAt` | `JobApplication.submittedAt` | Nullable |
| `withdrawnAt` | `JobApplication.withdrawnAt` | Nullable |
| `receptionStartDateTime` | `JobPosting.receptionStartDateTime` | Display and accepting input |
| `receptionEndDateTime` | `JobPosting.receptionEndDateTime` | Display and accepting input |
| `accepting` | service calculation | `PUBLISHED` and current time inside reception period |
| `announcedResultCount` | visible StageResult summary | Count of visible announced/closed stage results |
| `latestAnnouncedStageName` | visible StageResult summary | Nullable |
| `latestResultStatus` | visible StageResult summary | Nullable |

Explicitly not exposed:

- `applicantId`
- applicant name/email/phone/CI/address
- `stageResultId`
- `score`
- `comment`
- `decidedBy`
- `correctedBy`
- correction reason/history
- `storedFileName`
- `storagePath`

## Entity Relationship Summary

```text
Applicant 1 : N JobApplication
JobApplication N : 1 JobPosting
JobApplication N : 1 JobPosition
JobApplication 1 : N StageResult
StageResult N : 1 Stage
Stage N : 1 JobPosting
```

Read flow:

1. Controller resolves the current applicant through `CurrentApplicantService`.
2. `JobApplicationService.getMyApplications` validates page and size.
3. `JobApplicationRepository.findMyApplications` loads one applicant-owned page.
4. The service extracts application ids from the page.
5. If the page is empty, StageResult summary query is skipped.
6. `StageResultRepository.findVisibleByJobApplicationIdsForApplicantSummary` batch-loads visible results.
7. The service groups results by application id, calculates count, and selects the latest visible stage by `stageOrder DESC, stage.id DESC`.
8. The service maps rows to `MyApplicationResponse`.

## Business Rules

- The endpoint is applicant-only through the existing `/applications/**` URL authorization.
- `applicantId` is never accepted as request input.
- Service query is scoped by current `applicantId`.
- `DRAFT`, `SUBMITTED`, and `WITHDRAWN` applications are included.
- Applications remain visible when posting status is `CLOSED`.
- Default sort is `createdAt DESC, id DESC`.
- `page < 0` fails.
- `size <= 0` fails.
- `size > 100` fails.
- `accepting` is true only when `JobPosting.status == PUBLISHED` and current time is inside the reception period.
- Result summary includes only `Stage.status == RESULT_ANNOUNCED || CLOSED`.
- `READY` and `IN_PROGRESS` stage results are excluded from summary.
- Missing StageResult rows are not synthesized.
- Detailed result rows remain in `GET /applications/{applicationId}/stage-results`.

## Test Coverage

| Test Class | Coverage |
|---|---|
| `JobApplicationServiceTest` | Own list success, other applicant exclusion, all statuses included, closed posting included, sort check, page/size validation, `accepting`, visible result count, latest visible result, empty page |
| `ApplicationControllerTest` | `GET /applications/me` response wrapper, page metadata, response fields, forbidden field absence, empty page, anonymous 401 JSON, employee/admin 403 JSON, unsupported methods |
| `ApplicationStageResultServiceTest` / `ApplicationStageResultControllerTest` | Regression for existing detailed applicant result API |
| `StageResultServiceTest` / `StageResultCorrectionServiceTest` | Regression for result update/correction behavior used by result summary data |

## Test Commands

Executed:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.JobApplicationServiceTest
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.ApplicationControllerTest
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationStageResultServiceTest --tests com.shinyoung.recruit.controller.ApplicationStageResultControllerTest
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.StageResultServiceTest --tests com.shinyoung.recruit.service.StageResultCorrectionServiceTest
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test --no-daemon
```

## Test Results

| Command | Result | Notes |
|---|---|---|
| `JobApplicationServiceTest` | Success | First attempt timed out and left Gradle result files locked; after `.\gradlew.bat --stop`, retry succeeded |
| `ApplicationControllerTest` | Success | API contract and security checks passed |
| `ApplicationStageResultServiceTest` + `ApplicationStageResultControllerTest` | Success | Existing applicant result detail API regression passed |
| `StageResultServiceTest` + `StageResultCorrectionServiceTest` | Success | StageResult update/correction regression passed |
| `clean test --no-daemon` | Success | Full suite completed successfully |

## Known Limitations

- No status filter is implemented.
- No custom sort parameter is implemented.
- No dashboard completion rate or missing-section guidance is implemented.
- No scheduled release guard based on `resultAnnouncementDateTime` is implemented for the summary; summary follows existing `Stage.status` visibility.
- The service test verifies empty-page behavior but does not assert repository method invocation count.

## Remaining Issues

- Decide whether future my-page work needs application completion rate or required-section missing indicators.
- Decide whether a future status filter is needed for applicant UX.
- Decide whether result summary display should use localized message templates rather than raw enum values.

## Next Phase Considerations

Recommended next phase candidate:

- Phase 03h-3 applicant my-page dashboard summary, if required by frontend UX.

Alternative next work:

- Continue with the next application detail section or interview/message phase according to project priority.
