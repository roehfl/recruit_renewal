# Phase 04e - Interview Scheduling Stabilization / Test Hardening

## Phase Summary

Phase 04e stabilizes the completed Phase 04 interview scheduling slices before moving to interview evaluation.

This phase does not add new production APIs or domain behavior. It hardens cross-slice behavior with focused tests, records the authorization matrix, and updates documentation so Phase 04 can hand off cleanly to `Phase 05 - Interview Evaluation`.

## Purpose

- Verify that Phase 04b admin cancellation remains compatible with Phase 04c applicant reads and Phase 04d interviewer reads.
- Lock the participant lifecycle contract: cancelling an interview changes the interview status only and keeps participant rows assigned.
- Confirm `StageResult` remains unchanged by scheduling cancel/read flows.
- Review and document admin, applicant, employee, and anonymous access boundaries.
- Remove stale Phase 04 next-step text from earlier implementation documents.

## Scope

Implemented:

- Added cross-slice stabilization service test.
- Added admin interview API authorization matrix controller test.
- Re-ran applicant/interviewer/admin/repository/current employee targeted regression tests.
- Updated Phase 04 roadmap/history/status documentation.
- Added this implementation document and the matching self-contained HTML report.

Out of scope:

- New interview scheduling production APIs.
- New request or response DTOs.
- `InterviewEvaluation`.
- Interview scoring, evaluator draft/submit, or evaluation form design.
- `StageResult` create/update/delete/announce/correction behavior changes.
- Message/SMS/email sending.
- Excel, PDF, calendar integration.
- Frontend or static resources.
- Flyway, Liquibase, migration files, or production MariaDB DDL.

## Changed Files

| File | Change |
| --- | --- |
| `src/test/java/com/shinyoung/recruit/service/InterviewSchedulingStabilizationServiceTest.java` | Added cross-slice cancellation, participant lifecycle, applicant/interviewer visibility, and `StageResult` non-mutation regression. |
| `src/test/java/com/shinyoung/recruit/controller/InterviewAdminControllerTest.java` | Added admin interview authorization matrix coverage for admin, applicant, and anonymous access. |
| `docs/codex/implementation/phase-04e-interview-scheduling-stabilization.md` | Added this implementation document. |
| `docs/codex/reports/phase-04e-interview-scheduling-stabilization.html` | Added human-readable phase report. |
| `docs/codex/06-implementation-roadmap.md` | Marked 04e complete and moved next work to Phase 05. |
| `docs/codex/07-implementation-history.md` | Added Phase 04e history and snapshot update. |
| `docs/codex/reports/current-implementation-status.html` | Updated current status to Phase 04 complete. |
| `docs/codex/implementation/phase-04b-admin-interview-schedule-management.md` | Clarified that applicant/interviewer reads were implemented in later Phase 04 slices. |
| `docs/codex/implementation/phase-04c-applicant-interview-read.md` | Clarified that interviewer read was implemented in Phase 04d and current next phase is Phase 05. |
| `docs/codex/implementation/phase-04d-interviewer-interview-read.md` | Updated next phase recommendation after 04e completion. |

## New Classes

| Package | Class | Type | Responsibility |
| --- | --- | --- | --- |
| `com.shinyoung.recruit.service` | `InterviewSchedulingStabilizationServiceTest` | Test | Verifies cancellation consistency across admin, applicant, interviewer, participant lifecycle, and `StageResult` boundaries. |

## Modified Classes

| Package | Class | Type | Responsibility | Important notes |
| --- | --- | --- | --- | --- |
| `com.shinyoung.recruit.controller` | `InterviewAdminControllerTest` | Test | Admin interview HTTP API tests. | Added explicit admin/applicant/anonymous authorization matrix coverage. |

No production Java class was modified in Phase 04e.

## Class-by-Class Explanation

### `InterviewSchedulingStabilizationServiceTest`

- Package: `com.shinyoung.recruit.service`
- Type: Test
- Responsibility:
  - Verify that a confirmed interview cancelled through `InterviewService.cancel` remains visible to assigned applicant and assigned interviewer read services.
  - Verify that cancellation keeps candidate/interviewer participant rows as `ASSIGNED`.
  - Verify that the previous-stage `StageResult` remains `PASSED`.
- Key method:
  - `cancelledInterviewRemainsVisibleWithoutChangingParticipantsOrStageResult()`
- Related classes:
  - `InterviewService`
  - `ApplicantInterviewService`
  - `InterviewerInterviewService`
  - `InterviewParticipantRepository`
  - `StageResultRepository`
- Important implementation notes:
  - Builds the full scheduling flow through public service methods: create draft, replace participants, confirm, cancel, applicant read, interviewer read.
  - Uses H2 transactional test data and example-only applicant/employee values.

### `InterviewAdminControllerTest`

- Package: `com.shinyoung.recruit.controller`
- Type: Test
- Responsibility:
  - Verify admin interview route behavior and response wrapper.
  - Verify `/admin/**` interview schedule APIs reject applicant and anonymous access.
- Added test:
  - `adminInterviewApisRequireAdminAuthentication()`
- Important implementation notes:
  - Admin access still follows the existing `/admin/**` security boundary.
  - Applicant receives 403.
  - Anonymous receives 401.

## API List

No new APIs were added.

Phase 04e re-verified existing Phase 04 APIs:

| Method | Path | Purpose | Current access policy |
| --- | --- | --- | --- |
| `GET` | `/admin/job-postings/{jobPostingId}/interviews?stageId=&status=&from=&to=` | Admin schedule list. | `ROLE_ADMIN` or `ROLE_RECRUIT_ADMIN`. |
| `GET` | `/admin/interviews/{interviewId}` | Admin schedule detail. | `ROLE_ADMIN` or `ROLE_RECRUIT_ADMIN`. |
| `POST` | `/admin/job-postings/{jobPostingId}/interviews` | Admin create draft schedule. | `ROLE_ADMIN` or `ROLE_RECRUIT_ADMIN`. |
| `POST` | `/admin/interviews/{interviewId}` | Admin update draft schedule. | `ROLE_ADMIN` or `ROLE_RECRUIT_ADMIN`. |
| `POST` | `/admin/interviews/{interviewId}/participants` | Admin replace draft participants. | `ROLE_ADMIN` or `ROLE_RECRUIT_ADMIN`. |
| `POST` | `/admin/interviews/{interviewId}/confirm` | Admin confirm draft schedule. | `ROLE_ADMIN` or `ROLE_RECRUIT_ADMIN`. |
| `POST` | `/admin/interviews/{interviewId}/cancel` | Admin cancel confirmed schedule. | `ROLE_ADMIN` or `ROLE_RECRUIT_ADMIN`. |
| `GET` | `/applicant/interviews?status=&from=&to=` | Applicant-owned visible schedule list. | `ROLE_APPLICANT`; participant ownership still required. |
| `GET` | `/applicant/applications/{applicationId}/interviews?status=&from=&to=` | Applicant-owned application schedule list. | `ROLE_APPLICANT`; application ownership still required. |
| `GET` | `/applicant/interviews/{interviewId}` | Applicant-owned visible schedule detail. | `ROLE_APPLICANT`; candidate participant assignment still required. |
| `GET` | `/interviewer/interviews?status=&from=&to=` | Interviewer-owned visible schedule list. | Employee-family authorities; interviewer participant assignment still required. |
| `GET` | `/interviewer/interviews/{interviewId}` | Interviewer-owned visible schedule detail. | Employee-family authorities; interviewer participant assignment still required. |

## Entity/DTO/Service/Controller Summary

No entity, DTO, service, or controller implementation changed in Phase 04e.

Re-validated Phase 04 contracts:

- `Interview.status` carries schedule lifecycle.
- `InterviewParticipant.participantStatus` carries participant assignment lifecycle.
- Admin cancel changes only `Interview.status` from `CONFIRMED` to `CANCELLED`.
- Applicant reads are rooted in assigned `CANDIDATE` participant rows.
- Interviewer reads are rooted in assigned `INTERVIEWER` participant rows.
- `StageResult` remains a separate result domain.

## Authorization Matrix

| API area | Admin | Recruit admin | Employee/interviewer | Applicant | Anonymous |
| --- | --- | --- | --- | --- | --- |
| `/admin/**` interview APIs | Allowed | Allowed | Rejected unless separately granted admin authority | 403 | 401 |
| `/applicant/**` interview APIs | 403 | 403 | 403 | Allowed, then ownership checked | 401 |
| `/interviewer/**` interview APIs | Allowed by current employee-family boundary, then assignment checked | Allowed by current employee-family boundary, then assignment checked | Allowed, then assignment checked | 403 | 401 |

Notes:

- `/interviewer/**` currently allows employee-family authorities because interviewer assignment is ultimately controlled by `InterviewParticipant`.
- A dedicated operating `ROLE_INTERVIEWER` policy remains a future authorization hardening decision.
- The read services never accept applicant id or employee id from request parameters or request bodies.

## Validation and Business Rules

### Cancellation and Participant Lifecycle

- Only `CONFIRMED` interviews can be cancelled.
- Cancelled interviews remain visible to assigned applicants and assigned interviewers.
- Cancel changes only `Interview.status`.
- Candidate and interviewer participant rows remain `ASSIGNED`.
- Cancelled participant rows are still hidden by applicant/interviewer read queries.

### Visibility

- Applicant and interviewer read APIs expose only:
  - `CONFIRMED`
  - `CANCELLED`
- `DRAFT` schedules remain admin-only.
- `status=DRAFT` list requests return bad request in applicant/interviewer APIs.
- Detail reads for DRAFT or non-owned schedules are hidden as not found.

### Response Field Policy

Applicant responses still do not expose:

- admin memo
- other candidates
- interviewer identity
- `StageResult` data

Interviewer responses still do not expose:

- admin memo
- other interviewer details
- `StageResult` data

### StageResult Boundary

- Scheduling confirmation reads prior-stage `StageResult` for eligibility.
- Scheduling cancel does not create, update, delete, announce, correct, or publish `StageResult`.
- Applicant/interviewer schedule reads do not expose `StageResult` internals.

## Test Coverage

Added:

- `InterviewSchedulingStabilizationServiceTest`
  - Confirmed interview cancellation remains visible to applicant and interviewer.
  - Participant rows remain `ASSIGNED` after cancel.
  - Previous-stage `StageResult` remains `PASSED`.
  - Interviewer detail still returns assigned candidate list after cancellation.
- `InterviewAdminControllerTest`
  - Admin interview list API accepts admin authentication.
  - Applicant authentication receives 403.
  - Anonymous access receives 401.

Regressions re-run:

- `ApplicantInterviewServiceTest`
- `ApplicantInterviewControllerTest`
- `InterviewerInterviewServiceTest`
- `InterviewerInterviewControllerTest`
- `InterviewParticipantRepositoryTest`
- `InterviewServiceTest`
- `CurrentEmployeeServiceTest`

## Test Commands

Initial sandbox execution failed because the Gradle wrapper attempted to download Gradle and network access was restricted:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.InterviewSchedulingStabilizationServiceTest --tests com.shinyoung.recruit.controller.InterviewAdminControllerTest --tests com.shinyoung.recruit.service.InterviewerInterviewServiceTest --tests com.shinyoung.recruit.controller.InterviewerInterviewControllerTest --tests com.shinyoung.recruit.service.ApplicantInterviewServiceTest --tests com.shinyoung.recruit.controller.ApplicantInterviewControllerTest --tests com.shinyoung.recruit.domain.repository.InterviewParticipantRepositoryTest --tests com.shinyoung.recruit.service.InterviewServiceTest --tests com.shinyoung.recruit.service.CurrentEmployeeServiceTest --no-daemon
```

Failure classification:

- Sandbox/network restriction while downloading Gradle distribution.
- Not a source or test failure.

Executed again with approved escalation for Gradle wrapper network access:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.InterviewSchedulingStabilizationServiceTest --tests com.shinyoung.recruit.controller.InterviewAdminControllerTest --tests com.shinyoung.recruit.service.InterviewerInterviewServiceTest --tests com.shinyoung.recruit.controller.InterviewerInterviewControllerTest --tests com.shinyoung.recruit.service.ApplicantInterviewServiceTest --tests com.shinyoung.recruit.controller.ApplicantInterviewControllerTest --tests com.shinyoung.recruit.domain.repository.InterviewParticipantRepositoryTest --tests com.shinyoung.recruit.service.InterviewServiceTest --tests com.shinyoung.recruit.service.CurrentEmployeeServiceTest --no-daemon
```

Result: `BUILD SUCCESSFUL`

Full suite not executed:

- `.\gradlew.bat test`
- `.\gradlew.bat clean test`

Reason:

- Current `instruction.md` still instructs Phase 04 work to avoid full-suite execution because of development PC performance/full-suite timeout concerns.
- Phase 04e changed only focused tests and documentation, so targeted Phase 04 regression coverage was used.

## Known Limitations

- This phase does not implement `InterviewEvaluation`.
- This phase does not introduce a dedicated participant cancellation command.
- `/interviewer/**` authority policy is broad to employee-family authorities; participant assignment remains the final visibility guard.
- No migration file or production DDL was generated.
- No frontend or static resources were generated.

## Remaining Issues

- Phase 05 must define `InterviewEvaluation` ownership and uniqueness around `Interview + JobApplication + Employee`.
- Phase 05 must decide whether evaluation submit affects `StageResult`, or whether result processing remains a separate admin phase.
- A final production role matrix for interviewer-specific access can be tightened after operating roles are confirmed.

## Next Phase Recommendation

Proceed to `Phase 05 - Interview Evaluation`.

Recommended Phase 05 scope:

- Add `InterviewEvaluation` domain and repository.
- Let assigned interviewers draft/save/submit their own evaluations.
- Restrict evaluation access to interviewer assignment.
- Add admin read APIs for evaluation status.
- Define explicit `StageResult` integration policy without mutating result state implicitly from scheduling reads.
