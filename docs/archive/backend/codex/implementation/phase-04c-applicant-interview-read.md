# Phase 04c - Applicant Interview Read

## Phase Summary

Phase 04c implements read-only applicant interview schedule APIs on top of the Phase 04a/04b interview scheduling model.

The slice lets an authenticated applicant view only their own assigned candidate interview schedules. It exposes `CONFIRMED` and `CANCELLED` schedules, hides `DRAFT`, and does not expose admin memo, other candidates, interviewer identities, or `StageResult` internals.

## Purpose

- Provide applicant-owned interview schedule list and detail APIs.
- Keep schedule visibility tied to the current authenticated applicant.
- Hide administrator-only draft schedules and internal memo fields.
- Preserve `StageResult` as a separate result domain with no mutation from interview scheduling.

## Scope

Implemented:

- Applicant whole interview schedule list.
- Applicant per-application interview schedule list.
- Applicant interview schedule detail.
- Current applicant ownership guard through `CurrentApplicantService`.
- Repository-level visibility query for candidate participant rows.
- `DRAFT` hiding and `status=DRAFT` request rejection.
- `CONFIRMED` and `CANCELLED` schedule exposure.
- Candidate role and assigned participant status filtering.
- Withdrawn application filtering.
- Applicant-safe summary and detail response DTOs.
- Applicant URL security boundary.
- Targeted service, controller, repository, and admin regression tests.

Out of scope:

- Admin write API behavior changes.
- Interviewer read API.
- `InterviewEvaluation`.
- Interview scoring, result input, pass/fail processing, or evaluation submit.
- `StageResult` create/update/delete/announce/correction.
- Message/SMS/email sending.
- Excel, PDF, calendar integration.
- Frontend or static resources.
- Flyway, Liquibase, migration files, or production MariaDB DDL.

## Changed Files

| File | Change |
| --- | --- |
| `src/main/java/com/shinyoung/recruit/domain/repository/InterviewParticipantRepository.java` | Added applicant visibility list/detail queries. |
| `src/main/java/com/shinyoung/recruit/dto/response/ApplicantInterviewSummaryResponse.java` | Added applicant-safe list response DTO. |
| `src/main/java/com/shinyoung/recruit/dto/response/ApplicantInterviewDetailResponse.java` | Added applicant-safe detail response DTO. |
| `src/main/java/com/shinyoung/recruit/service/ApplicantInterviewService.java` | Added applicant read service and guards. |
| `src/main/java/com/shinyoung/recruit/controller/ApplicantInterviewController.java` | Added applicant interview read APIs. |
| `src/main/java/com/shinyoung/recruit/config/SecurityConfig.java` | Added `/applicant/**` applicant authority guard. |
| `src/test/java/com/shinyoung/recruit/service/ApplicantInterviewServiceTest.java` | Added service tests. |
| `src/test/java/com/shinyoung/recruit/controller/ApplicantInterviewControllerTest.java` | Added controller/API tests. |
| `src/test/java/com/shinyoung/recruit/domain/repository/InterviewParticipantRepositoryTest.java` | Added applicant visibility query tests. |
| `docs/codex/implementation/phase-04c-applicant-interview-read.md` | Added this implementation document. |
| `docs/codex/reports/phase-04c-applicant-interview-read.html` | Added human-readable report. |
| `docs/codex/07-implementation-history.md` | Added Phase 04c history. |
| `docs/codex/06-implementation-roadmap.md` | Marked 04c complete and 04d next. |
| `docs/codex/reports/current-implementation-status.html` | Updated current status. |

## New Classes

| Package | Class | Type | Responsibility |
| --- | --- | --- | --- |
| `com.shinyoung.recruit.service` | `ApplicantInterviewService` | Service | Reads applicant-owned visible interview schedules and applies request validation. |
| `com.shinyoung.recruit.controller` | `ApplicantInterviewController` | Controller | Exposes applicant read-only interview APIs. |
| `com.shinyoung.recruit.dto.response` | `ApplicantInterviewSummaryResponse` | Response DTO | Applicant-safe schedule list item. |
| `com.shinyoung.recruit.dto.response` | `ApplicantInterviewDetailResponse` | Response DTO | Applicant-safe schedule detail. |
| `com.shinyoung.recruit.service` | `ApplicantInterviewServiceTest` | Test | Verifies service visibility, ownership, filters, and errors. |
| `com.shinyoung.recruit.controller` | `ApplicantInterviewControllerTest` | Test | Verifies HTTP routes, auth boundary, and response field filtering. |

## Modified Classes

| Package | Class | Type | Responsibility | Important notes |
| --- | --- | --- | --- | --- |
| `com.shinyoung.recruit.domain.repository` | `InterviewParticipantRepository` | Repository | Participant persistence and lookup. | Added applicant visibility queries that combine applicant ownership, candidate role, assigned status, non-withdrawn application, and visible interview status. |
| `com.shinyoung.recruit.config` | `SecurityConfig` | Config | URL authorization. | Added `/applicant/**` guard for `ROLE_APPLICANT`. |
| `com.shinyoung.recruit.domain.repository` | `InterviewParticipantRepositoryTest` | Test | Repository query tests. | Added applicant visibility query coverage. |

## Class-by-Class Explanation

### `ApplicantInterviewService`

- Package: `com.shinyoung.recruit.service`
- Type: Service
- Responsibility:
  - Coordinate applicant-owned interview read queries.
  - Validate applicant read query conditions.
  - Hide non-visible detail records as not found.
- Key methods:
  - `getMyInterviews(Long applicantId, InterviewStatus status, LocalDateTime from, LocalDateTime to)`
  - `getMyApplicationInterviews(Long applicantId, Long applicationId, InterviewStatus status, LocalDateTime from, LocalDateTime to)`
  - `getMyInterviewDetail(Long applicantId, Long interviewId)`
- Related classes:
  - `InterviewParticipantRepository`
  - `JobApplicationRepository`
  - `ApplicantInterviewSummaryResponse`
  - `ApplicantInterviewDetailResponse`
- Important implementation notes:
  - `status=DRAFT` is rejected with `InvalidInterviewException`.
  - `from >= to` is rejected with `InvalidInterviewException`.
  - Per-application read first verifies `JobApplication` ownership through `findByIdAndApplicantId`.
  - Withdrawn applications are treated as inaccessible.
  - Detail reads use the visibility query directly and return `InterviewNotFoundException` for DRAFT, non-owned, non-assigned, or interviewer-only rows.

### `ApplicantInterviewController`

- Package: `com.shinyoung.recruit.controller`
- Type: Controller
- Responsibility:
  - Expose applicant interview read-only APIs.
  - Resolve the current applicant from `@AuthenticationPrincipal CustomUserDetails`.
- Key APIs:
  - `GET /applicant/interviews`
  - `GET /applicant/applications/{applicationId}/interviews`
  - `GET /applicant/interviews/{interviewId}`
- Related classes:
  - `CurrentApplicantService`
  - `ApplicantInterviewService`
- Important implementation notes:
  - No endpoint accepts `applicantId` or `userId`.
  - Query date parameters use ISO `LocalDateTime`.
  - Responses keep the existing `ResponseEntity<ApiResponse<T>>` style.

### `ApplicantInterviewSummaryResponse`

- Package: `com.shinyoung.recruit.dto.response`
- Type: Response DTO
- Responsibility:
  - Return applicant-safe schedule list fields.
- Fields:
  - `interviewId`, `applicationId`, `jobPostingId`, `jobPostingTitle`
  - `positionId`, `positionName`
  - `stageId`, `stageName`, `stageType`
  - `groupName`, `startDateTime`, `endDateTime`
  - `method`, `locationName`, `roomName`, `onlineMeetingUrl`
  - `status`, `cancelled`
- Important implementation notes:
  - Does not include `Interview.memo`.
  - Does not include other candidate records.
  - Does not include interviewer employee identifiers, names, or department data.
  - Does not include `StageResult` data.

### `ApplicantInterviewDetailResponse`

- Package: `com.shinyoung.recruit.dto.response`
- Type: Response DTO
- Responsibility:
  - Return applicant-safe schedule detail fields.
- Fields:
  - Same fields as summary response.
  - `guideMessage`
- Important implementation notes:
  - `guideMessage` is optional and currently set for `CANCELLED`.
  - It intentionally excludes admin memo, other participants, interviewer identity, and `StageResult` internals.

### `InterviewParticipantRepository`

- Package: `com.shinyoung.recruit.domain.repository`
- Type: Repository
- Responsibility:
  - Query visible applicant schedule rows through `InterviewParticipant`.
- Key methods:
  - `findVisibleApplicantInterviewParticipants(...)`
  - `findVisibleApplicationInterviewParticipants(...)`
  - `findVisibleApplicantInterviewParticipant(...)`
- Important implementation notes:
  - Filters `role = CANDIDATE`.
  - Filters `participantStatus = ASSIGNED`.
  - Filters `application.applicant.id = current applicant`.
  - Filters `application.status <> WITHDRAWN`.
  - Filters `interview.status in (CONFIRMED, CANCELLED)`.
  - Applies overlap semantics:
    - from only: `interview.endDateTime > from`
    - to only: `interview.startDateTime < to`
    - both: both predicates together.

## API List

| Method | Path | Purpose | Request | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/applicant/interviews?status=&from=&to=` | Read all visible schedules assigned to current applicant. | Query params only. No applicant id. | `ApiResponse<List<ApplicantInterviewSummaryResponse>>` |
| `GET` | `/applicant/applications/{applicationId}/interviews?status=&from=&to=` | Read visible schedules for one owned application. | Path application id and query params. | `ApiResponse<List<ApplicantInterviewSummaryResponse>>` |
| `GET` | `/applicant/interviews/{interviewId}` | Read one visible assigned interview schedule. | Path interview id. | `ApiResponse<ApplicantInterviewDetailResponse>` |

## Entity Relationship Summary

- `Interview` belongs to one `JobPosting`.
- `Interview` belongs to one `Stage`.
- `InterviewParticipant` belongs to one `Interview`.
- Applicant reads only candidate participant rows.
- Candidate participant rows point to `JobApplication`.
- `JobApplication.applicant.id` must match the current authenticated applicant.
- Interviewer participant rows point to `Employee` but are not exposed by applicant APIs.
- `StageResult` is not read or written by Phase 04c.

## Validation and Business Rules

### Visibility Rules

- Expose only `Interview.status` values:
  - `CONFIRMED`
  - `CANCELLED`
- Never expose `DRAFT`.
- Expose only participant rows where:
  - `role = CANDIDATE`
  - `participantStatus = ASSIGNED`
  - `jobApplication.applicant.id = current applicant id`
  - `jobApplication.status <> WITHDRAWN`
- `CANCELLED` schedules remain visible so the applicant can see that an assigned schedule was cancelled.

### Ownership Rules

- The current applicant id is derived from `CustomUserDetails` through `CurrentApplicantService`.
- Requests do not accept applicant id, user id, or applicant identity in path/query/body.
- Per-application reads verify ownership through `JobApplicationRepository.findByIdAndApplicantId`.
- Non-owned application reads return not found.
- Non-owned or non-visible detail reads return not found.

### Query Rules

- `status` can be omitted, `CONFIRMED`, or `CANCELLED`.
- `status=DRAFT` returns bad request.
- Invalid enum values return bad request through existing request parameter handling.
- `from` and `to` use ISO `LocalDateTime`.
- `from >= to` returns bad request.
- Date filtering uses overlap semantics:
  - from only: `interview.endDateTime > from`
  - to only: `interview.startDateTime < to`
  - both: both predicates are applied.

### Forbidden Response Fields

Applicant responses do not expose:

- `Interview.memo`
- cancel reason or internal memo
- other candidate participant list
- other applicant ids, names, or application ids
- interviewer employee id, name, or department
- `StageResult` id/status/history/announcement flags
- result score, comment, decided/corrected actor data

### StageResult Rule

- Phase 04c does not inject or use `StageResultRepository`.
- Phase 04c does not save, update, delete, initialize, announce, correct, or publish any `StageResult`.
- Applicant interview reads are schedule reads only, not result reads.

## Test Coverage

Added or updated tests:

- `ApplicantInterviewServiceTest`
  - Whole-list read returns only current applicant's visible candidate rows.
  - `CONFIRMED` and `CANCELLED` schedules are visible.
  - `DRAFT`, other applicants, cancelled participant rows, and interviewer-only rows are hidden.
  - Status filter, time filter, `status=DRAFT`, and invalid range behavior.
  - Per-application ownership and withdrawn application guards.
  - Detail success and hidden-not-found behavior.
- `ApplicantInterviewControllerTest`
  - Three applicant API routes.
  - `status=DRAFT` and invalid range bad request.
  - Other applicant application hidden.
  - DRAFT and other applicant detail hidden.
  - JSON response does not expose memo, interviewer, candidate list, applicant id, or `StageResult` fields.
  - `/applicant/**` requires applicant authentication.
- `InterviewParticipantRepositoryTest`
  - Applicant visibility query filters confirmed/cancelled, DRAFT, ownership, participant status, and role.
- `InterviewServiceTest`
  - Existing admin scheduling regression remains green.

## Test Commands

Executed:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicantInterviewServiceTest --tests com.shinyoung.recruit.controller.ApplicantInterviewControllerTest --tests com.shinyoung.recruit.domain.repository.InterviewParticipantRepositoryTest --tests com.shinyoung.recruit.service.InterviewServiceTest --no-daemon
```

Result: `BUILD SUCCESSFUL`

Not executed:

- Full `.\gradlew.bat test`
- Full `.\gradlew.bat clean test`

Reason:

- `instruction.md` explicitly forbids full suite execution for this slice.
- Required record: full tests were not run because of development PC performance concerns and potential full-suite timeout; only targeted tests tied to the changed packages were executed.

## Known Limitations

- Interviewer-facing interview read API was out of scope for Phase 04c and was implemented later in Phase 04d.
- `InterviewEvaluation` is not implemented.
- Applicant detail `guideMessage` is minimal and not backed by a message template or notification policy.
- There is no per-participant cancellation command in this slice.
- There is no migration file or production DDL in this phase.
- There is no frontend or static resource output.

## Remaining Issues

- Interviewer role and URL policy were implemented in Phase 04d with `/interviewer/**` and participant-assignment visibility.
- Future evaluation phases need a stable reference policy for `Interview + JobApplication + Employee`.
- Persistent DB application still requires separate operational handling because this phase does not introduce migration files.

## Next Phase Recommendation

Phase 04c was followed by:

- `Phase 04d - Interviewer Interview Read`
- `Phase 04e - Interview Scheduling Stabilization / Test Hardening`

Current next phase is `Phase 05 - Interview Evaluation`.
- Do not expose admin memo or `StageResult` internals.
- Keep all APIs read-only.
