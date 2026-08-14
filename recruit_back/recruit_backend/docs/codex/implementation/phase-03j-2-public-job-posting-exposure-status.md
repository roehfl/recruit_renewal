# Phase 03j-2 - Public Job Posting Exposure Status

## Phase Summary

Phase 03j-2 applies the Phase 03j public exposure contract to the existing applicant-facing job posting APIs.

The implementation keeps the existing public endpoint paths and changes only their filtering, sorting, and response shape. Public list/detail queries now require a posting to be published, visible, and inside its display period. Reception period is treated separately from exposure so closed reception postings can still be displayed as public archive/read-only postings.

## Purpose

- Apply public exposure filters to `GET /job-postings` and `GET /job-postings/{id}`.
- Sort public postings by admin-managed priority and applicant-facing reception state.
- Expose applicant-facing posting and position metadata added in Phase 03j-1.
- Hide internal admin fields from public responses.
- Avoid public list position N+1 by batch-loading positions for the current page.

## Scope

- Existing public endpoints only:
  - `GET /job-postings`
  - `GET /job-postings/{id}`
- Public list query:
  - `status = PUBLISHED`
  - `visible = true`
  - `displayStartDateTime is null or displayStartDateTime <= now`
  - `displayEndDateTime is null or displayEndDateTime >= now`
- Public detail query:
  - Same exposure policy as list.
  - Non-displayable postings return `JobPostingNotFoundException`.
- Public list ordering:
  - `pinned desc`
  - reception status priority: `ACCEPTING`, `UPCOMING`, `CLOSED`
  - `displayOrder asc`
  - `receptionEndDateTime asc`
  - `publishedAt desc`
  - `id desc`
- Public list positions are loaded in one query for the current page ids.

## Out of Scope

- New endpoint paths.
- Admin API behavior changes.
- Application create/update/submit/withdraw command changes.
- Database migration scripts for non-H2 environments.
- Security/authorization rule changes.
- Frontend/static resource generation.
- Splitting application form section usage and required policy.

## Changed Files

| Path | Change Type | Notes |
|---|---|---|
| `src/main/java/com/shinyoung/recruit/domain/repository/JobPostingPublicListProjection.java` | Modified | Added public scalar fields for list response mapping |
| `src/main/java/com/shinyoung/recruit/domain/repository/JobPostingRepository.java` | Modified | Added public list and detail JPQL queries with exposure filters and DB ordering; removed obsolete public status-only list method after review |
| `src/main/java/com/shinyoung/recruit/domain/repository/JobPositionRepository.java` | Modified | Added page-id batch position lookup ordered by posting id and `sortOrder` |
| `src/main/java/com/shinyoung/recruit/dto/response/JobPostingPublicListResponse.java` | Modified | Added applicant-facing posting fields, `ReceptionStatus`, `pinned`, and `positions` |
| `src/main/java/com/shinyoung/recruit/dto/response/JobPostingPublicDetailResponse.java` | Modified | Added applicant-facing detail fields, `ReceptionStatus`, and `pinned` |
| `src/main/java/com/shinyoung/recruit/dto/response/JobPositionPublicResponse.java` | Modified | Added public position metadata fields |
| `src/main/java/com/shinyoung/recruit/service/JobPostingPublicService.java` | Modified | Applied public queries and page-level position grouping |
| `src/test/java/com/shinyoung/recruit/service/JobPostingPublicServiceTest.java` | Modified | Reworked public service tests for exposure, sorting, response fields, detail not-found rules, and isolated sort tie-breakers |
| `src/test/java/com/shinyoung/recruit/controller/JobPostingPublicControllerTest.java` | New | Added public JSON field include/exclude and 404 tests |
| `docs/codex/implementation/phase-03j-2-public-job-posting-exposure-status.md` | New | Codex implementation reference |
| `docs/codex/reports/phase-03j-2-public-job-posting-exposure-report.html` | New | Human-readable phase report |
| `docs/codex/07-implementation-history.md` | Modified | Phase history entry |

## New Classes

| Package | Class | Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.controller` | `JobPostingPublicControllerTest` | Test | Verifies public endpoint JSON field contract and not-found behavior |

## Modified Classes

| Package | Class | Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.domain.repository` | `JobPostingPublicListProjection` | Repository projection | Carries public list scalar fields from JPQL to response mapping |
| `com.shinyoung.recruit.domain.repository` | `JobPostingRepository` | Repository | Provides public list/detail queries with exposure filters and database ordering |
| `com.shinyoung.recruit.domain.repository` | `JobPositionRepository` | Repository | Provides page-id batch lookup for public list positions |
| `com.shinyoung.recruit.dto.response` | `JobPostingPublicListResponse` | Response DTO | Defines public list response fields |
| `com.shinyoung.recruit.dto.response` | `JobPostingPublicDetailResponse` | Response DTO | Defines public detail response fields |
| `com.shinyoung.recruit.dto.response` | `JobPositionPublicResponse` | Response DTO | Defines public position response fields |
| `com.shinyoung.recruit.service` | `JobPostingPublicService` | Service | Owns public page validation, public query invocation, position grouping, and DTO mapping |
| `com.shinyoung.recruit.service` | `JobPostingPublicServiceTest` | Test | Verifies public exposure, sorting, response fields, and detail access policy |

## Class-by-Class Explanation

| Class | Type | Key Fields / Methods | Related Classes | Implementation Notes |
|---|---|---|---|---|
| `JobPostingPublicListProjection` | Repository projection | `getPostingType`, `getSummary`, `getPinned` | `JobPostingRepository`, `JobPostingPublicListResponse` | Projection includes fields needed for public list response but does not expose internal display fields |
| `JobPostingRepository` | Repository | `findPublicList(...)`, `findPublicDetailById(...)` | `JobPostingPublicService` | JPQL applies `PUBLISHED`, `visible`, and display-period filters; list query performs requested DB ordering before pagination; unused `findAllByStatusOrderByCreatedAtDesc(...)` was removed |
| `JobPositionRepository` | Repository | `findByJobPostingIdInOrderByJobPostingIdAscSortOrderAsc(...)` | `JobPostingPublicService`, `JobPosition` | Uses join fetch so grouping by posting id does not trigger extra lazy loads |
| `JobPostingPublicListResponse` | Response DTO | `postingType`, `summary`, `receptionStatus`, `accepting`, `pinned`, `positions` | `ReceptionStatus`, `JobPositionPublicResponse` | `accepting` is true only for `ReceptionStatus.ACCEPTING`; no admin-only fields are present |
| `JobPostingPublicDetailResponse` | Response DTO | `postingType`, `summary`, `contentHtml`, `receptionStatus`, `accepting`, `pinned`, `jobPositions`, `applicationFormConfig` | `JobPosting`, `ApplicationFormConfigPublicResponse` | Detail includes content and form config but excludes status, display policy, and audit fields |
| `JobPositionPublicResponse` | Response DTO | `applicationType`, `jobGroup`, `jobTitle`, `workLocation`, `employmentType` | `JobPosition` | Returns nullable metadata as null instead of placeholder values |
| `JobPostingPublicService` | Service | `getJobPostings`, `getJobPosting`, `getPositionsByPostingId` | repositories and response DTOs | Skips the position query when the page is empty and groups positions by posting id for list mapping |
| `JobPostingPublicServiceTest` | Test | public list/detail test cases | `JobPostingPublicService`, `JobPostingService` | Uses a fixed `Clock` so display and reception status behavior is deterministic; includes isolated tests for `receptionEndDateTime asc` and final `id desc` tie-break ordering |
| `JobPostingPublicControllerTest` | Test | MockMvc public JSON tests | `JobPostingPublicController` | Verifies include/exclude response fields and 404 for hidden or future-display postings |

## API List

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| `GET` | `/job-postings` | Public job posting list | `page`, `size` query params | `ApiResponse<PageResponse<JobPostingPublicListResponse>>` |
| `GET` | `/job-postings/{id}` | Public job posting detail | path id | `ApiResponse<JobPostingPublicDetailResponse>` |

No endpoint path was added or removed.

## Public Response Contract

### Public List Fields

Included:

- `id`
- `title`
- `postingType`
- `summary`
- `receptionStartDateTime`
- `receptionEndDateTime`
- `receptionStatus`
- `accepting`
- `pinned`
- `positions`

Excluded:

- `status`
- `visible`
- `displayStartDateTime`
- `displayEndDateTime`
- `displayOrder`
- `createdAt`
- `updatedAt`
- `closedAt`
- `contentHtml`
- `applicationFormConfig`

### Public Detail Fields

Included:

- `id`
- `title`
- `postingType`
- `summary`
- `contentHtml`
- `receptionStartDateTime`
- `receptionEndDateTime`
- `receptionStatus`
- `accepting`
- `pinned`
- `jobPositions`
- `applicationFormConfig`

Excluded:

- `status`
- `visible`
- `displayStartDateTime`
- `displayEndDateTime`
- `displayOrder`
- `createdAt`
- `updatedAt`
- `closedAt`

### Public Position Fields

Included:

- `id`
- `positionName`
- `applicationType`
- `jobGroup`
- `jobTitle`
- `workLocation`
- `employmentType`
- `sortOrder`

## Entity Relationship Summary

- `JobPosting` owns many `JobPosition` rows.
- `JobPosting` owns one `ApplicationFormConfig` row.
- Public list reads `JobPosting` scalar values through `JobPostingPublicListProjection`.
- Public list reads `JobPosition` rows separately by current page posting ids.
- Public detail loads `JobPosting`, `jobPositions`, and `applicationFormConfig` through an entity graph.

## Validation and Business Rules

- Page request validation remains:
  - `page >= 0`
  - `1 <= size <= 100`
- Public exposure requires:
  - `JobPosting.status = PUBLISHED`
  - `JobPosting.visible = true`
  - `displayStartDateTime` is null or not after current time
  - `displayEndDateTime` is null or not before current time
- Public detail returns not found for hidden, future-display, expired-display, draft, and closed-status postings.
- Reception period is not a public exposure filter.
- A published, visible, displayable posting with already-closed reception remains visible with:
  - `receptionStatus = CLOSED`
  - `accepting = false`
- `ReceptionStatus` is derived from reception start/end and current time:
  - `UPCOMING`: now before reception start
  - `ACCEPTING`: now from start through end
  - `CLOSED`: now after reception end
- `displayOrder` is used only for sorting and is not exposed publicly.
- Null position metadata remains null in the response.

## Test Coverage

| Test Class | Coverage |
|---|---|
| `JobPostingPublicServiceTest` | Published/visible/display filtering, hidden/future/past display exclusion, DRAFT/CLOSED exclusion, closed reception visibility, reception status calculation, requested sort order, isolated `receptionEndDateTime asc` tie-break, isolated `id desc` tie-break when previous sort keys match, public list fields, public position fields, empty page behavior, public detail fields, detail not-found rules, page validation |
| `JobPostingPublicControllerTest` | Public list JSON include/exclude fields, public detail JSON include/exclude fields, 404 for hidden and out-of-display postings |

## Test Commands

```bash
$env:AES_SECRET_KEY='<test-value>'; .\gradlew.bat cleanTest test --tests com.shinyoung.recruit.service.JobPostingPublicServiceTest --tests com.shinyoung.recruit.controller.JobPostingPublicControllerTest --no-daemon

$env:AES_SECRET_KEY='<test-value>'; .\gradlew.bat clean test --no-daemon
```

## Test Results

- Targeted public API tests: success
- Full test suite: success, 572 tests, 0 failures, 0 ignored, 100% successful
- Executed date: 2026-05-22

## Review Fixes

- Narrowed the broad public list sorting test name to match the sort keys it actually covers.
- Added an isolated test where `pinned`, reception status, and `displayOrder` match so `receptionEndDateTime asc` is verified directly.
- Added an isolated test where all preceding sort keys and `publishedAt` match so final `id desc` ordering is verified directly.
- Removed unused `JobPostingRepository.findAllByStatusOrderByCreatedAtDesc(...)` because public reads now use `findPublicList(...)`.

## Known Limitations

- No persistent DB migration script was added.
- Public API authorization was not changed.
- The public list returns positions for each posting but does not add a separate `positionCount` field.
- `ApplicationFormConfig` still exposes section usage flags only; required/optional section policy is not separated yet.

## Remaining Issues

- Persistent database schema migration needs to be handled before deploying to MariaDB or another shared DB.
- Application form section usage and required policy remain coupled.
- Public frontend rendering rules for nullable position metadata are outside backend scope.

## Next Phase Recommendation

Proceed to Phase 03k: split `ApplicationFormConfig` usage and required policy so public detail can express which sections are visible and which sections are required during application submission.
