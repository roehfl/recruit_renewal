# Phase 04d - Interviewer Interview Read

## Phase Summary

Phase 04d implements read-only interviewer interview schedule APIs on top of the Phase 04a/04b/04c interview scheduling model.

The slice lets an authenticated employee view only interview schedules where they are assigned as an `INTERVIEWER` participant. It exposes `CONFIRMED` and `CANCELLED` schedules, hides `DRAFT`, and does not expose admin memo, other interviewers, or `StageResult` internals.

## Purpose

- Provide interviewer-owned interview schedule list and detail APIs.
- Keep schedule visibility tied to the current authenticated employee.
- Hide administrator-only draft schedules and internal memo fields.
- Return assigned candidate rows only in interviewer detail.
- Preserve `StageResult` as a separate result domain with no mutation from interview scheduling.

## Scope

Implemented:

- Interviewer interview schedule list.
- Interviewer interview schedule detail.
- Current employee ownership guard through `CurrentEmployeeService`.
- Repository-level visibility query for interviewer participant rows.
- Detail candidate list query for assigned candidate rows.
- `DRAFT` hiding and `status=DRAFT` request rejection.
- `CONFIRMED` and `CANCELLED` schedule exposure.
- Interviewer role and assigned participant status filtering.
- Interviewer-safe summary/detail/candidate response DTOs.
- `/interviewer/**` security boundary.
- Targeted service, controller, repository, applicant-read regression, admin scheduling regression, and current employee resolver tests.

Out of scope:

- Admin write API behavior changes.
- Applicant read API behavior changes.
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
| `src/main/java/com/shinyoung/recruit/domain/repository/EmployeeRepository.java` | Added `findByLoginId` for current employee resolution. |
| `src/main/java/com/shinyoung/recruit/service/CurrentEmployeeService.java` | Added current employee id resolver for interviewer APIs. |
| `src/main/java/com/shinyoung/recruit/domain/repository/InterviewParticipantRepository.java` | Added interviewer visibility and candidate list queries. |
| `src/main/java/com/shinyoung/recruit/dto/response/InterviewerInterviewSummaryResponse.java` | Added interviewer-safe list response DTO. |
| `src/main/java/com/shinyoung/recruit/dto/response/InterviewerInterviewDetailResponse.java` | Added interviewer-safe detail response DTO. |
| `src/main/java/com/shinyoung/recruit/dto/response/InterviewerInterviewCandidateResponse.java` | Added assigned candidate response DTO. |
| `src/main/java/com/shinyoung/recruit/service/InterviewerInterviewService.java` | Added interviewer read service and guards. |
| `src/main/java/com/shinyoung/recruit/controller/InterviewerInterviewController.java` | Added interviewer interview read APIs. |
| `src/main/java/com/shinyoung/recruit/config/SecurityConfig.java` | Added `/interviewer/**` employee-family authority guard. |
| `src/test/java/com/shinyoung/recruit/service/InterviewerInterviewServiceTest.java` | Added service tests. |
| `src/test/java/com/shinyoung/recruit/controller/InterviewerInterviewControllerTest.java` | Added controller/API tests. |
| `src/test/java/com/shinyoung/recruit/domain/repository/InterviewParticipantRepositoryTest.java` | Added interviewer visibility query tests. |
| `src/test/java/com/shinyoung/recruit/service/CurrentEmployeeServiceTest.java` | Added employee id resolver tests. |
| `docs/codex/implementation/phase-04d-interviewer-interview-read.md` | Added this implementation document. |
| `docs/codex/reports/phase-04d-interviewer-interview-read.html` | Added human-readable report. |
| `docs/codex/07-implementation-history.md` | Added Phase 04d history. |
| `docs/codex/06-implementation-roadmap.md` | Marked 04d complete and 04e next. |
| `docs/codex/reports/current-implementation-status.html` | Updated current status. |

## New Classes

| Package | Class | Type | Responsibility |
| --- | --- | --- | --- |
| `com.shinyoung.recruit.service` | `InterviewerInterviewService` | Service | Reads interviewer-owned visible interview schedules and applies request validation. |
| `com.shinyoung.recruit.controller` | `InterviewerInterviewController` | Controller | Exposes interviewer read-only interview APIs. |
| `com.shinyoung.recruit.dto.response` | `InterviewerInterviewSummaryResponse` | Response DTO | Interviewer-safe schedule list item. |
| `com.shinyoung.recruit.dto.response` | `InterviewerInterviewDetailResponse` | Response DTO | Interviewer-safe schedule detail. |
| `com.shinyoung.recruit.dto.response` | `InterviewerInterviewCandidateResponse` | Response DTO | Assigned candidate item for interviewer detail. |
| `com.shinyoung.recruit.service` | `InterviewerInterviewServiceTest` | Test | Verifies service visibility, ownership, filters, candidate list, and errors. |
| `com.shinyoung.recruit.controller` | `InterviewerInterviewControllerTest` | Test | Verifies HTTP routes, auth boundary, and response field filtering. |

## Modified Classes

| Package | Class | Type | Responsibility | Important notes |
| --- | --- | --- | --- | --- |
| `com.shinyoung.recruit.domain.repository` | `EmployeeRepository` | Repository | Employee persistence and lookup. | Added `findByLoginId` for principal login id to employee id resolution. |
| `com.shinyoung.recruit.service` | `CurrentEmployeeService` | Service | Current employee helper. | Keeps existing actor behavior and adds `getCurrentEmployeeId`. |
| `com.shinyoung.recruit.domain.repository` | `InterviewParticipantRepository` | Repository | Participant persistence and lookup. | Added interviewer visibility, detail ownership, assigned candidate list, and candidate count queries. |
| `com.shinyoung.recruit.config` | `SecurityConfig` | Config | URL authorization. | Added `/interviewer/**` guard for employee-family authorities. |
| `com.shinyoung.recruit.domain.repository` | `InterviewParticipantRepositoryTest` | Test | Repository query tests. | Added interviewer visibility query coverage. |
| `com.shinyoung.recruit.service` | `CurrentEmployeeServiceTest` | Test | Current employee resolver tests. | Added employee id lookup success and failure cases. |

## Class-by-Class Explanation

### `InterviewerInterviewService`

- Package: `com.shinyoung.recruit.service`
- Type: Service
- Responsibility:
  - Coordinate interviewer-owned interview read queries.
  - Validate interviewer read query conditions.
  - Hide non-visible detail records as not found.
- Key methods:
  - `getMyInterviews(Long employeeId, InterviewStatus status, LocalDateTime from, LocalDateTime to)`
  - `getMyInterviewDetail(Long employeeId, Long interviewId)`
- Related classes:
  - `InterviewParticipantRepository`
  - `InterviewerInterviewSummaryResponse`
  - `InterviewerInterviewDetailResponse`
  - `InterviewerInterviewCandidateResponse`
- Important implementation notes:
  - `status=DRAFT` is rejected with `InvalidInterviewException`.
  - `from >= to` is rejected with `InvalidInterviewException`.
  - Detail reads use the interviewer visibility query directly and return `InterviewNotFoundException` for DRAFT, non-owned, non-assigned, or candidate-only rows.
  - Detail candidate list is loaded only after interviewer ownership is verified.

### `InterviewerInterviewController`

- Package: `com.shinyoung.recruit.controller`
- Type: Controller
- Responsibility:
  - Expose interviewer interview read-only APIs.
  - Resolve the current employee from `@AuthenticationPrincipal CustomUserDetails`.
- Key APIs:
  - `GET /interviewer/interviews`
  - `GET /interviewer/interviews/{interviewId}`
- Related classes:
  - `CurrentEmployeeService`
  - `InterviewerInterviewService`
- Important implementation notes:
  - No endpoint accepts `employeeId` or `userId`.
  - Query date parameters use ISO `LocalDateTime`.
  - Responses keep the existing `ResponseEntity<ApiResponse<T>>` style.

### `CurrentEmployeeService`

- Package: `com.shinyoung.recruit.service`
- Type: Service
- Responsibility:
  - Keep existing employee actor resolution.
  - Resolve current employee id for interviewer APIs.
- Key methods:
  - `getCurrentEmployeeActor(CustomUserDetails userDetails)`
  - `getCurrentEmployeeId(CustomUserDetails userDetails)`
- Related classes:
  - `EmployeeRepository`
  - `CustomUserDetails`
- Important implementation notes:
  - Actor behavior still throws the existing `InvalidStageResultException` for StageResult command callers.
  - Interviewer API id resolution throws `InvalidInterviewException`.

### Response DTOs

- `InterviewerInterviewSummaryResponse`
  - Includes schedule, posting, stage, time, method, place/URL, status, cancelled flag, and `candidateCount`.
  - Does not include admin memo, interviewer list, employee id, or `StageResult`.
- `InterviewerInterviewDetailResponse`
  - Includes the same schedule fields plus assigned candidates and optional `guideMessage`.
  - Does not include admin memo, other interviewers, or `StageResult`.
- `InterviewerInterviewCandidateResponse`
  - Includes `jobApplicationId`, `applicantId`, applicant name snapshot, position id/name, and `sortOrder`.
  - Includes only assigned candidate participants in the same interview.

### `InterviewParticipantRepository`

- Package: `com.shinyoung.recruit.domain.repository`
- Type: Repository
- Responsibility:
  - Query visible interviewer schedule rows through `InterviewParticipant`.
- Key methods:
  - `findVisibleInterviewerInterviewParticipants(...)`
  - `findVisibleInterviewerInterviewParticipant(...)`
  - `findAssignedCandidatesByInterviewId(Long interviewId)`
  - `countAssignedCandidatesByInterviewId(Long interviewId)`
- Important implementation notes:
  - Filters `role = INTERVIEWER`.
  - Filters `participantStatus = ASSIGNED`.
  - Filters `employee.id = current employee`.
  - Filters `interview.status in (CONFIRMED, CANCELLED)`.
  - Candidate list filters `role = CANDIDATE` and `participantStatus = ASSIGNED`.
  - Applies overlap semantics:
    - from only: `interview.endDateTime > from`
    - to only: `interview.startDateTime < to`
    - both: both predicates together.

## API List

| Method | Path | Purpose | Request | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/interviewer/interviews?status=&from=&to=` | Read all visible schedules assigned to current employee as interviewer. | Query params only. No employee id. | `ApiResponse<List<InterviewerInterviewSummaryResponse>>` |
| `GET` | `/interviewer/interviews/{interviewId}` | Read one visible assigned interviewer schedule with assigned candidates. | Path interview id. | `ApiResponse<InterviewerInterviewDetailResponse>` |

## Entity Relationship Summary

- `Interview` belongs to one `JobPosting`.
- `Interview` belongs to one `Stage`.
- `InterviewParticipant` belongs to one `Interview`.
- Interviewer reads only interviewer participant rows.
- Interviewer participant rows point to `Employee`.
- `InterviewParticipant.employee.id` must match the current authenticated employee.
- Detail candidate list reads assigned candidate participant rows for the same interview after ownership is verified.
- `StageResult` is not read or written by Phase 04d.

## Validation and Business Rules

### Visibility Rules

- Expose only `Interview.status` values:
  - `CONFIRMED`
  - `CANCELLED`
- Never expose `DRAFT`.
- Expose only interviewer rows where:
  - `role = INTERVIEWER`
  - `participantStatus = ASSIGNED`
  - `employee.id = current employee id`
- `CANCELLED` schedules remain visible so the interviewer can see that an assigned schedule was cancelled.

### Candidate List Rules

- Detail response includes candidate rows only after the current employee's interviewer assignment is verified.
- Candidate rows must have:
  - `role = CANDIDATE`
  - `participantStatus = ASSIGNED`
- Cancelled candidate participant rows are excluded.
- Other interviewer rows are not exposed.

### Ownership Rules

- The current employee id is derived from `CustomUserDetails.username` through `CurrentEmployeeService` and `EmployeeRepository.findByLoginId`.
- Requests do not accept employee id, user id, or employee identity in path/query/body.
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

Interviewer responses do not expose:

- `Interview.memo`
- cancel reason or internal memo
- other interviewer employee id, name, or department
- interviewer participant list
- `StageResult` id/status/history/announcement flags
- result score, comment, decided/corrected actor data

## StageResult Non-Mutation

- Phase 04d does not inject or use `StageResultRepository`.
- Phase 04d does not save, update, delete, initialize, announce, correct, or publish any `StageResult`.
- Interviewer interview reads are schedule reads only, not result reads.

## Test Coverage

Added or updated tests:

- `InterviewerInterviewServiceTest`
  - Whole-list read returns only current employee's visible interviewer rows.
  - `CONFIRMED` and `CANCELLED` schedules are visible.
  - `DRAFT`, other employees, cancelled interviewer rows, and candidate-only rows are hidden.
  - Status filter, time filter, `status=DRAFT`, and invalid range behavior.
  - Detail success and hidden-not-found behavior.
  - Detail candidate list includes assigned candidates and excludes cancelled candidate rows.
- `InterviewerInterviewControllerTest`
  - Two interviewer API routes.
  - `status=DRAFT` and invalid range bad request.
  - Other employee detail hidden.
  - DRAFT detail hidden.
  - Applicant and anonymous access blocked.
  - JSON response does not expose memo, interviewer list, employee id, or `StageResult` fields.
- `InterviewParticipantRepositoryTest`
  - Interviewer visibility query filters confirmed/cancelled, DRAFT, ownership, participant status, and role.
  - Assigned candidate list and count queries.
- `CurrentEmployeeServiceTest`
  - Current employee id lookup success and missing employee failure.
- Regression:
  - `ApplicantInterviewServiceTest`
  - `InterviewServiceTest`

## Test Commands

Executed:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.InterviewerInterviewServiceTest --tests com.shinyoung.recruit.controller.InterviewerInterviewControllerTest --tests com.shinyoung.recruit.domain.repository.InterviewParticipantRepositoryTest --tests com.shinyoung.recruit.service.ApplicantInterviewServiceTest --tests com.shinyoung.recruit.service.InterviewServiceTest --tests com.shinyoung.recruit.service.CurrentEmployeeServiceTest --no-daemon
```

Result: `BUILD SUCCESSFUL`

Not executed:

- Full `.\gradlew.bat test`
- Full `.\gradlew.bat clean test`

Reason:

- `instruction.md` explicitly forbids full suite execution for this slice.
- Required record: full tests were not run because of development PC performance concerns and potential full-suite timeout; only targeted tests tied to the changed packages were executed.

## Known Limitations

- `InterviewEvaluation` is not implemented.
- Interviewer detail candidate fields are intentionally minimal and do not include full applicant personal data.
- There is no per-participant cancellation command in this slice.
- There is no migration file or production DDL in this phase.
- There is no frontend or static resource output.

## Remaining Issues

- A dedicated `ROLE_INTERVIEWER` assignment policy can be refined later; current access allows employee-family authorities and final visibility is participant-assignment based.
- Future evaluation phases need a stable reference policy for `Interview + JobApplication + Employee`.
- Persistent DB application still requires separate operational handling because this phase does not introduce migration files.

## Next Phase Recommendation

Phase 04d was followed by `Phase 04e - Interview Scheduling Stabilization / Test Hardening`.

Current next phase is `Phase 05 - Interview Evaluation`.

Recommended Phase 05 scope:

- Add `InterviewEvaluation`.
- Support assigned interviewer evaluation draft/save/submit flows.
- Add admin evaluation status/read APIs.
- Define explicit `StageResult` integration policy.
