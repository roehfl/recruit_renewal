# Phase 04b - Admin Interview Schedule Management

## Phase Summary

Phase 04b implements the administrator-side interview schedule management slice on top of the Phase 04a interview domain.

The slice adds admin APIs for interview schedule list/detail, DRAFT schedule creation and update, participant replacement, confirmation, and cancellation. It keeps `StageResult` as a read-only eligibility source only. This phase does not create, update, announce, correct, or publish any stage result.

## Purpose

- Provide backend APIs for administrators to manage interview schedules and groups.
- Allow candidate and interviewer assignment replacement while the schedule is still `DRAFT`.
- Confirm only schedules that satisfy stage, candidate, participant, and collision policies.
- Cancel confirmed schedules without mutating participant rows or stage results.

## Scope

Implemented:

- Admin interview schedule list and detail APIs.
- Admin DRAFT schedule creation.
- Admin DRAFT schedule update.
- Admin DRAFT participant replacement.
- Confirm command with validation guards.
- Cancel command for confirmed schedules.
- DTOs for admin requests and responses.
- Exceptions and global exception mapping for interview errors.
- Repository methods for admin search/detail and confirmed time collision checks.
- Targeted service, controller, and repository tests.

Out of scope:

- Applicant interview schedule read API.
- Interviewer interview schedule read API.
- `InterviewEvaluation`.
- Interview scoring, evaluation submission, and result processing.
- `StageResult` creation, update, announcement, correction, or history mutation.
- Message/SMS/email sending.
- Excel, PDF, calendar integration.
- Frontend or static resources.
- Flyway, migration framework, or production DDL files.

## Changed Files

| File | Change |
| --- | --- |
| `src/main/java/com/shinyoung/recruit/domain/entity/Interview.java` | Added `clearParticipantsForDraft()` domain method. |
| `src/main/java/com/shinyoung/recruit/domain/repository/InterviewRepository.java` | Added admin search and detail fetch queries. |
| `src/main/java/com/shinyoung/recruit/domain/repository/InterviewParticipantRepository.java` | Added admin participant fetch and confirmed time collision queries. |
| `src/main/java/com/shinyoung/recruit/domain/repository/StageResultRepository.java` | Added single stage/application result lookup for eligibility checks. |
| `src/main/java/com/shinyoung/recruit/exception/InterviewNotFoundException.java` | Added 404 exception. |
| `src/main/java/com/shinyoung/recruit/exception/InvalidInterviewException.java` | Added 400 exception. |
| `src/main/java/com/shinyoung/recruit/exception/GlobalExceptionHandler.java` | Added interview exception handlers. |
| `src/main/java/com/shinyoung/recruit/dto/request/InterviewCreateRequest.java` | Added create request DTO. |
| `src/main/java/com/shinyoung/recruit/dto/request/InterviewUpdateRequest.java` | Added update request DTO. |
| `src/main/java/com/shinyoung/recruit/dto/request/InterviewCandidateParticipantRequest.java` | Added candidate participant item DTO. |
| `src/main/java/com/shinyoung/recruit/dto/request/InterviewInterviewerParticipantRequest.java` | Added interviewer participant item DTO. |
| `src/main/java/com/shinyoung/recruit/dto/request/InterviewParticipantReplaceRequest.java` | Added participant replace request DTO. |
| `src/main/java/com/shinyoung/recruit/dto/response/AdminInterviewSummaryResponse.java` | Added admin list response DTO. |
| `src/main/java/com/shinyoung/recruit/dto/response/AdminInterviewDetailResponse.java` | Added admin detail response DTO. |
| `src/main/java/com/shinyoung/recruit/dto/response/AdminInterviewParticipantResponse.java` | Added admin participant response DTO. |
| `src/main/java/com/shinyoung/recruit/service/InterviewService.java` | Added admin interview scheduling application service. |
| `src/main/java/com/shinyoung/recruit/controller/InterviewAdminController.java` | Added admin interview scheduling controller. |
| `src/test/java/com/shinyoung/recruit/service/InterviewServiceTest.java` | Added service tests. |
| `src/test/java/com/shinyoung/recruit/controller/InterviewAdminControllerTest.java` | Added controller tests. |
| `src/test/java/com/shinyoung/recruit/domain/repository/InterviewRepositoryTest.java` | Added admin search query test. |
| `src/test/java/com/shinyoung/recruit/domain/repository/InterviewParticipantRepositoryTest.java` | Added confirmed collision query test. |
| `docs/codex/implementation/phase-04b-admin-interview-schedule-management.md` | Added this implementation document. |
| `docs/codex/reports/phase-04b-admin-interview-schedule-management.html` | Added human-readable report. |
| `docs/codex/07-implementation-history.md` | Added Phase 04b history. |
| `docs/codex/06-implementation-roadmap.md` | Marked 04b complete and 04c next. |
| `docs/codex/reports/current-implementation-status.html` | Updated current status. |

## New Classes

| Package | Class | Type | Responsibility |
| --- | --- | --- | --- |
| `com.shinyoung.recruit.service` | `InterviewService` | Service | Coordinates admin interview schedule commands and validation. |
| `com.shinyoung.recruit.controller` | `InterviewAdminController` | Controller | Exposes admin interview schedule APIs. |
| `com.shinyoung.recruit.dto.request` | `InterviewCreateRequest` | Request DTO | DRAFT schedule creation payload. |
| `com.shinyoung.recruit.dto.request` | `InterviewUpdateRequest` | Request DTO | DRAFT schedule update payload. |
| `com.shinyoung.recruit.dto.request` | `InterviewParticipantReplaceRequest` | Request DTO | Replace-all participant payload. |
| `com.shinyoung.recruit.dto.request` | `InterviewCandidateParticipantRequest` | Request DTO | Candidate participant row payload. |
| `com.shinyoung.recruit.dto.request` | `InterviewInterviewerParticipantRequest` | Request DTO | Interviewer participant row payload. |
| `com.shinyoung.recruit.dto.response` | `AdminInterviewSummaryResponse` | Response DTO | Admin list item response. |
| `com.shinyoung.recruit.dto.response` | `AdminInterviewDetailResponse` | Response DTO | Admin detail response. |
| `com.shinyoung.recruit.dto.response` | `AdminInterviewParticipantResponse` | Response DTO | Admin candidate/interviewer participant response. |
| `com.shinyoung.recruit.exception` | `InterviewNotFoundException` | Exception | Maps missing interview to 404. |
| `com.shinyoung.recruit.exception` | `InvalidInterviewException` | Exception | Maps invalid interview command to 400. |
| `com.shinyoung.recruit.service` | `InterviewServiceTest` | Test | Verifies service business rules. |
| `com.shinyoung.recruit.controller` | `InterviewAdminControllerTest` | Test | Verifies admin API response shape and command routes. |

## Modified Classes

| Package | Class | Type | Responsibility | Important notes |
| --- | --- | --- | --- | --- |
| `com.shinyoung.recruit.domain.entity` | `Interview` | Entity | Interview schedule/group aggregate. | Added `clearParticipantsForDraft()` and keeps participant replacement guarded by DRAFT status. |
| `com.shinyoung.recruit.domain.repository` | `InterviewRepository` | Repository | Interview persistence and lookup. | Added admin filter query and detail fetch query. |
| `com.shinyoung.recruit.domain.repository` | `InterviewParticipantRepository` | Repository | Participant persistence and lookup. | Added admin fetch query and candidate/interviewer confirmed collision checks. |
| `com.shinyoung.recruit.domain.repository` | `StageResultRepository` | Repository | Stage result persistence and lookup. | Added read-only eligibility lookup by stage/application. |
| `com.shinyoung.recruit.exception` | `GlobalExceptionHandler` | Exception handler | Converts exceptions to `ApiResponse.fail`. | Added interview 404/400 handlers. |
| `com.shinyoung.recruit.domain.repository` | `InterviewRepositoryTest` | Test | Repository tests. | Added admin search filter test. |
| `com.shinyoung.recruit.domain.repository` | `InterviewParticipantRepositoryTest` | Test | Repository tests. | Added confirmed collision query test. |

## Class-by-Class Explanation

### `InterviewService`

- Package: `com.shinyoung.recruit.service`
- Type: Service
- Responsibility:
  - Admin list/detail query orchestration.
  - DRAFT create/update.
  - DRAFT participant replacement.
  - Confirm/cancel command validation.
- Key methods:
  - `getAdminInterviews(...)`
  - `getAdminInterview(Long interviewId)`
  - `createDraft(Long jobPostingId, InterviewCreateRequest request)`
  - `updateDraft(Long interviewId, InterviewUpdateRequest request)`
  - `replaceParticipants(Long interviewId, InterviewParticipantReplaceRequest request)`
  - `confirm(Long interviewId)`
  - `cancel(Long interviewId)`
- Related classes:
  - `Interview`, `InterviewParticipant`, `JobPosting`, `Stage`, `JobApplication`, `Employee`, `StageResult`
  - repositories for those aggregates
- Important implementation notes:
  - Only `FIRST_INTERVIEW`, `SECOND_INTERVIEW`, and `FINAL_INTERVIEW` stages can be scheduled.
  - Create/update/replace/confirm are allowed only when stage status is `READY` or `IN_PROGRESS`.
  - Participant replacement uses `Interview.clearParticipantsForDraft()` and JPA `orphanRemoval`; it does not run a separate repository `deleteAll` path.
  - Confirm checks at least one assigned candidate and one assigned interviewer.
  - Confirm reads previous-stage `StageResult` only; it does not mutate it.
  - Candidate and interviewer confirmed time collisions use repository queries and ignore cancelled/draft schedules.

### `InterviewAdminController`

- Package: `com.shinyoung.recruit.controller`
- Type: Controller
- Responsibility:
  - Exposes admin interview schedule APIs under `/admin`.
- Key methods:
  - `GET /admin/job-postings/{jobPostingId}/interviews`
  - `GET /admin/interviews/{interviewId}`
  - `POST /admin/job-postings/{jobPostingId}/interviews`
  - `POST /admin/interviews/{interviewId}`
  - `POST /admin/interviews/{interviewId}/participants`
  - `POST /admin/interviews/{interviewId}/confirm`
  - `POST /admin/interviews/{interviewId}/cancel`
- Related classes:
  - `InterviewService`
  - admin request/response DTOs
- Important implementation notes:
  - Responses keep the existing `ResponseEntity<ApiResponse<T>>` style.
  - Query dates use ISO `LocalDateTime` parsing.

### Request DTOs

- Package: `com.shinyoung.recruit.dto.request`
- Type: Request DTO
- Responsibility:
  - Validate shape and size constraints at controller boundary.
- Classes:
  - `InterviewCreateRequest`
  - `InterviewUpdateRequest`
  - `InterviewParticipantReplaceRequest`
  - `InterviewCandidateParticipantRequest`
  - `InterviewInterviewerParticipantRequest`
- Important implementation notes:
  - Participant lists are required but can be empty while the interview remains `DRAFT`.
  - Confirm later rejects empty candidate/interviewer assignments.
  - Confirm and cancel currently accept no request body because there is no audit/history table for memo or cancel reason.

### Response DTOs

- Package: `com.shinyoung.recruit.dto.response`
- Type: Response DTO
- Responsibility:
  - Present admin-safe interview schedule data.
- Classes:
  - `AdminInterviewSummaryResponse`
  - `AdminInterviewDetailResponse`
  - `AdminInterviewParticipantResponse`
- Important implementation notes:
  - Admin detail includes candidate and interviewer participant lists.
  - Applicant/interviewer-facing response filtering is not implemented in this phase.

### Exceptions

- Package: `com.shinyoung.recruit.exception`
- Type: Exception
- Responsibility:
  - `InterviewNotFoundException`: missing interview.
  - `InvalidInterviewException`: invalid state, eligibility, duplicate, or collision.
- Related classes:
  - `GlobalExceptionHandler`
- Important implementation notes:
  - 404 and 400 response mapping follows existing exception style.

## API List

| Method | Path | Purpose | Request | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/admin/job-postings/{jobPostingId}/interviews?stageId=&status=&from=&to=` | Admin interview list | Query params | `ApiResponse<List<AdminInterviewSummaryResponse>>` |
| `GET` | `/admin/interviews/{interviewId}` | Admin interview detail | Path variable | `ApiResponse<AdminInterviewDetailResponse>` |
| `POST` | `/admin/job-postings/{jobPostingId}/interviews` | Create DRAFT interview | `InterviewCreateRequest` | `ApiResponse<Long>` |
| `POST` | `/admin/interviews/{interviewId}` | Update DRAFT interview | `InterviewUpdateRequest` | `ApiResponse<Long>` |
| `POST` | `/admin/interviews/{interviewId}/participants` | Replace DRAFT participants | `InterviewParticipantReplaceRequest` | `ApiResponse<Long>` |
| `POST` | `/admin/interviews/{interviewId}/confirm` | Confirm DRAFT interview | none | `ApiResponse<Long>` |
| `POST` | `/admin/interviews/{interviewId}/cancel` | Cancel CONFIRMED interview | none | `ApiResponse<Long>` |

## Entity Relationship Summary

- `Interview` belongs to one `JobPosting`.
- `Interview` belongs to one `Stage`.
- `InterviewParticipant` belongs to one `Interview`.
- Candidate participant rows point to `JobApplication`.
- Interviewer participant rows point to `Employee`.
- `StageResult` is read only for confirmation eligibility. There is no relationship mutation from Phase 04b.

## Business Rules

### Stage Rules

- Interview schedules are allowed only for these `StageType` values:
  - `FIRST_INTERVIEW`
  - `SECOND_INTERVIEW`
  - `FINAL_INTERVIEW`
- Create, update, participant replacement, and confirm are allowed only when current stage status is:
  - `READY`
  - `IN_PROGRESS`
- `RESULT_ANNOUNCED` and `CLOSED` reject create/update/participant replacement/confirm.
- Cancel is allowed only from `CONFIRMED` while the stage is `READY` or `IN_PROGRESS`.
- `RESULT_ANNOUNCED` and `CLOSED` reject cancel.

### Schedule Rules

- `startDateTime` and `endDateTime` are required.
- `endDateTime` must be after `startDateTime`.
- `IN_PERSON` and `HYBRID` require `locationName`.
- `ONLINE` and `HYBRID` require `onlineMeetingUrl`.
- Only `DRAFT` schedules can be updated.
- Only `DRAFT` schedules can replace participants.
- Only `DRAFT` schedules can be confirmed.
- Only `CONFIRMED` schedules can be cancelled.
- Cancel changes only `Interview.status` to `CANCELLED`.

### Participant Rules

- Participant replace is replace-all.
- Participant request lists cannot be null.
- Empty lists are allowed while the schedule remains `DRAFT`.
- Confirm requires at least one assigned candidate and at least one assigned interviewer.
- Candidate IDs cannot be duplicated.
- Interviewer employee IDs cannot be duplicated.
- Non-null candidate sort orders cannot be duplicated.
- Non-null interviewer sort orders cannot be duplicated.
- Candidate rows must belong to the same job posting.
- Candidate applications must be `SUBMITTED`.

### Previous-Stage Eligibility

- Confirm requires a previous stage for the posting.
- Previous stage status must be `RESULT_ANNOUNCED` or `CLOSED`.
- Previous-stage `StageResult` must exist for the candidate.
- Previous-stage result must be `PASSED`.
- Missing or not-visible previous result fails closed.

### Collision Rules

- Collision checks inspect `CONFIRMED` interviews only.
- Collision checks inspect `ASSIGNED` participant rows only.
- Cancelled schedules are ignored because their interview status is `CANCELLED`.
- Candidate collision key: same `JobApplication`.
- Interviewer collision key: same `Employee`.
- Current interview is excluded.
- Time overlap formula:
  - `existing.startDateTime < requested.endDateTime`
  - `requested.startDateTime < existing.endDateTime`

### StageResult Rule

- Phase 04b reads `StageResult` only for previous-stage eligibility.
- Phase 04b does not call `StageResult.updateResult`.
- Phase 04b does not announce, correct, initialize, publish, or create stage results.

## Test Coverage

Added or updated tests:

- `InterviewServiceTest`
  - DRAFT creation for interview stage only.
  - DRAFT participant replacement.
  - Confirm with previous-stage `PASSED` result.
  - `StageResult` non-mutation after confirm.
  - Candidate confirmed time collision rejection.
- `InterviewAdminControllerTest`
  - Create and list API response shape.
  - Detail API response shape.
  - Confirm/cancel route response shape.
- `InterviewRepositoryTest`
  - Admin search filters by status and time range.
- `InterviewParticipantRepositoryTest`
  - Candidate and interviewer confirmed time collision query detection.

## Test Commands

Executed:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.InterviewServiceTest --no-daemon
```

Result: `BUILD SUCCESSFUL`

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.InterviewAdminControllerTest --no-daemon
```

Result: `BUILD SUCCESSFUL`

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.domain.repository.InterviewRepositoryTest --tests com.shinyoung.recruit.domain.repository.InterviewParticipantRepositoryTest --no-daemon
```

Result: `BUILD SUCCESSFUL`

Not executed:

- Full `.\gradlew.bat test`
- Full `.\gradlew.bat clean test`

Reason:

- `instruction.md` limited this work to targeted tests and explicitly excluded full suite execution for this phase.

## Known Limitations

- Applicant-facing interview read API was out of scope for Phase 04b and was implemented later in Phase 04c.
- Interviewer-facing interview read API was out of scope for Phase 04b and was implemented later in Phase 04d.
- Confirm/cancel memo or reason is not accepted yet because there is no audit/history row to store it.
- Participant partial cancellation is not implemented; cancellation changes the schedule status only.
- There is no migration file or production DDL in this phase.
- There is no frontend or static resource output.

## Remaining Issues

- Admin authorization still relies on the existing `/admin/**` security boundary; interviewer-specific role policy remains future work.
- `StageResult` previous-stage eligibility uses the immediately preceding stage by `stageOrder`; more complex branch or skip policies are deferred.
- If an interview stage is the first stage, confirmation fails closed because no previous visible pass result exists.

## Next Phase Recommendation

Phase 04b was followed by:

- `Phase 04c - Applicant Interview Read`
- `Phase 04d - Interviewer Interview Read`
- `Phase 04e - Interview Scheduling Stabilization / Test Hardening`

Current next phase is `Phase 05 - Interview Evaluation`.
