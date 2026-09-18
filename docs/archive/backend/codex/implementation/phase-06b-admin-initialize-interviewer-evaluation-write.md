# Phase 06b - Admin Initialize + Interviewer Evaluation Write

## 1. Phase Summary

- Phase name: Phase 06b - Admin Initialize + Interviewer Evaluation Write
- Work type: service/controller/DTO implementation slice with targeted tests.
- Date: 2026-05-29
- Goal: expose the admin evaluation-row initialize command and the interviewer evaluation list/detail/save/submit APIs on top of the Phase 06a `InterviewEvaluation` domain.
- Status: implemented and tested.

This slice adds the first API surface for interview evaluation. It follows the Phase 06 design (`docs/codex/design/phase-06-interview-evaluation-design.md`), specifically the 06b slice (design §13), and reuses existing patterns from `StageResultService` (admin initialize) and `InterviewerInterviewService` (interviewer ownership/visibility).

## 2. Purpose

Phase 06a created the persistence model only; rows could be created programmatically. Phase 06b makes evaluation usable:

- An admin explicitly initializes DRAFT evaluation rows for every ASSIGNED candidate × ASSIGNED interviewer combination of a CONFIRMED interview.
- Each interviewer can list and view only their own evaluations, save a draft, and submit.

`InterviewEvaluation` remains evaluation evidence only. No code in this slice references, injects, or mutates `StageResult`.

## 3. Implemented Scope

- `POST /admin/interviews/{interviewId}/evaluations/initialize` — admin initialize command (idempotent).
- `GET /interviewer/interviews/{interviewId}/evaluations` — interviewer's own evaluation list with interview context.
- `GET /interviewer/interviews/{interviewId}/evaluations/{evaluationId}` — interviewer's own evaluation detail.
- `POST /interviewer/interviews/{interviewId}/evaluations/{evaluationId}` — draft save (grade, recommendation, comment).
- `POST /interviewer/interviews/{interviewId}/evaluations/{evaluationId}/submit` — submit (optional body for final edit).
- Guards: interview CONFIRMED, participant ASSIGNED, employee ownership, SUBMITTED immutability, interviewer information isolation.
- New repository queries on `InterviewEvaluationRepository` and `InterviewParticipantRepository`.
- New exceptions `InterviewEvaluationNotFoundException` (404) and `InvalidInterviewEvaluationException` (400) with `GlobalExceptionHandler` mappings.
- Targeted service (10) and controller (7) tests.

## 4. Out Of Scope

- Admin evaluation read APIs at interview/stage/application level and summary aggregation (Phase 06c).
- `GradeDistribution` / `RecommendationDistribution` response DTOs (Phase 06c).
- Admin reopen API endpoint and ActivityLog history (Phase 06d).
- `StageResult` reflection (deferred).
- DB migration / DDL files.
- Excel/PDF/statistics, messaging.

## 5. Changed Files

| File | Change | Type |
| --- | --- | --- |
| `src/main/java/com/shinyoung/recruit/exception/InterviewEvaluationNotFoundException.java` | New | Exception |
| `src/main/java/com/shinyoung/recruit/exception/InvalidInterviewEvaluationException.java` | New | Exception |
| `src/main/java/com/shinyoung/recruit/exception/GlobalExceptionHandler.java` | Modified | Exception handler |
| `src/main/java/com/shinyoung/recruit/domain/repository/InterviewEvaluationRepository.java` | Modified | Repository |
| `src/main/java/com/shinyoung/recruit/domain/repository/InterviewParticipantRepository.java` | Modified | Repository |
| `src/main/java/com/shinyoung/recruit/dto/request/InterviewEvaluationSaveRequest.java` | New | Request DTO |
| `src/main/java/com/shinyoung/recruit/dto/response/InterviewEvaluationInitializeResponse.java` | New | Response DTO |
| `src/main/java/com/shinyoung/recruit/dto/response/InterviewerEvaluationListResponse.java` | New | Response DTO |
| `src/main/java/com/shinyoung/recruit/dto/response/InterviewerEvaluationSummaryResponse.java` | New | Response DTO |
| `src/main/java/com/shinyoung/recruit/dto/response/InterviewerEvaluationDetailResponse.java` | New | Response DTO |
| `src/main/java/com/shinyoung/recruit/service/InterviewEvaluationAdminService.java` | New | Service |
| `src/main/java/com/shinyoung/recruit/service/InterviewerEvaluationService.java` | New | Service |
| `src/main/java/com/shinyoung/recruit/controller/InterviewEvaluationAdminController.java` | New | Controller |
| `src/main/java/com/shinyoung/recruit/controller/InterviewerEvaluationController.java` | New | Controller |
| `src/test/java/com/shinyoung/recruit/service/InterviewEvaluationAdminServiceTest.java` | New | Test |
| `src/test/java/com/shinyoung/recruit/service/InterviewerEvaluationServiceTest.java` | New | Test |
| `src/test/java/com/shinyoung/recruit/controller/InterviewEvaluationAdminControllerTest.java` | New | Test |
| `src/test/java/com/shinyoung/recruit/controller/InterviewerEvaluationControllerTest.java` | New | Test |
| `docs/codex/implementation/phase-06b-admin-initialize-interviewer-evaluation-write.md` | New | Doc |
| `docs/codex/reports/phase-06b-admin-initialize-interviewer-evaluation-write.html` | New | Doc |
| `docs/codex/07-implementation-history.md` | Modified | Doc |
| `docs/codex/06-implementation-roadmap.md` | Modified | Doc |

## 6. New Classes

- `InterviewEvaluationAdminService`
- `InterviewerEvaluationService`
- `InterviewEvaluationAdminController`
- `InterviewerEvaluationController`
- `InterviewEvaluationSaveRequest`
- `InterviewEvaluationInitializeResponse`
- `InterviewerEvaluationListResponse`
- `InterviewerEvaluationSummaryResponse`
- `InterviewerEvaluationDetailResponse`
- `InterviewEvaluationNotFoundException`
- `InvalidInterviewEvaluationException`

## 7. Modified Classes

- `GlobalExceptionHandler` — added handlers for the two new exceptions (404 / 400).
- `InterviewEvaluationRepository` — added `findByInterviewIdAndInterviewerParticipantId` and `findDetailByIdAndInterviewIdAndInterviewerParticipantId` (both scoped by the interviewer participant id).
- `InterviewParticipantRepository` — added `findByInterviewIdAndRoleAndParticipantStatusOrderBySortOrderAscIdAsc`.

## 8. Class-by-Class Explanation

### InterviewEvaluationAdminService

- package: `com.shinyoung.recruit.service`
- type: Service
- responsibility: create DRAFT evaluation rows for a confirmed interview.
- key method: `initialize(Long interviewId)`:
  - loads the interview (404 if missing), requires `interview.isConfirmed()` (400 otherwise).
  - loads ASSIGNED CANDIDATE participants and ASSIGNED INTERVIEWER participants.
  - for each (candidate, interviewer) pair, skips when an evaluation already exists, otherwise builds `InterviewEvaluation.initialize(...)`.
  - persists new rows via `saveAll` and returns counts.
- related classes: `InterviewRepository`, `InterviewParticipantRepository`, `InterviewEvaluationRepository`, `InterviewEvaluation`, `InterviewEvaluationInitializeResponse`.
- notes: idempotent; cancelled participants are excluded because only ASSIGNED rows are queried; never touches `StageResult`.

### InterviewerEvaluationService

- package: `com.shinyoung.recruit.service`
- type: Service
- responsibility: interviewer-facing list/detail read and draft save/submit, with ownership and state guards.
- key methods:
  - `getMyEvaluations(employeeId, interviewId)` — resolves the caller's visible (CONFIRMED/CANCELLED) ASSIGNED interviewer participant (404 otherwise), then returns only that participant's evaluations (scoped by participant id).
  - `getMyEvaluationDetail(employeeId, interviewId, evaluationId)` — same visible-interviewer resolution, then loads the evaluation scoped to that participant id; 404 if missing or not owned (information isolation).
  - `save(...)` — guards writable state then `updateContent`.
  - `submit(...)` — guards writable state, optionally applies a final-edit body, requires grade + recommendation, then `submit(now)`.
  - private `findOwnedEvaluation` — calls `findVisibleInterviewer` first, then loads via `findDetailByIdAndInterviewIdAndInterviewerParticipantId`; throws `InterviewEvaluationNotFoundException` if no row matches that participant. This keeps detail/save/submit consistent with the list endpoint and prevents a cancelled interviewer from reading an evaluation by its id.
  - private `findVisibleInterviewer(employeeId, interviewId)` — returns the caller's visible ASSIGNED interviewer participant or throws `InterviewNotFoundException` (404).
  - private `validateWritable` — interview CONFIRMED, interviewer ASSIGNED, candidate ASSIGNED, evaluation DRAFT.
- related classes: `InterviewParticipantRepository`, `InterviewEvaluationRepository`, `Clock`, evaluation DTOs.
- notes: never references `StageResult`. Uses `Clock` for `submittedAt`. Every read/write entry point first resolves the caller's currently visible ASSIGNED interviewer participant and scopes the evaluation query to that participant id, so other interviewers' rows — and a cancelled interviewer's own past rows — are invisible at list, detail, save, and submit alike.

### InterviewEvaluationAdminController

- package: `com.shinyoung.recruit.controller`
- type: Controller (`@RestController`, base `/admin/interviews/{interviewId}/evaluations`)
- responsibility: expose the admin initialize command.
- key method: `POST /initialize` → `ApiResponse<InterviewEvaluationInitializeResponse>`.
- notes: guarded by Spring Security `/admin/**` rule (ROLE_ADMIN / ROLE_RECRUIT_ADMIN). 06c/06d admin read/reopen endpoints will be added to this controller later.

### InterviewerEvaluationController

- package: `com.shinyoung.recruit.controller`
- type: Controller (`@RestController`, base `/interviewer/interviews/{interviewId}/evaluations`)
- responsibility: expose interviewer list/detail/save/submit.
- key methods: `GET ""`, `GET /{evaluationId}`, `POST /{evaluationId}`, `POST /{evaluationId}/submit`.
- notes: resolves the current employee id via `CurrentEmployeeService.getCurrentEmployeeId`. Save uses `@Valid @RequestBody`; submit uses `@Valid @RequestBody(required = false)` so a body-less submit is allowed.

### InterviewEvaluationSaveRequest

- package: `com.shinyoung.recruit.dto.request`
- type: Request DTO (record)
- fields: `grade` (nullable), `recommendation` (nullable), `comment` (`@Size` max 2000).
- notes: grade/recommendation are nullable for draft save; submit enforces presence at the service layer.

### InterviewEvaluationInitializeResponse

- package: `com.shinyoung.recruit.dto.response`
- type: Response DTO (record): `interviewId`, `createdCount`, `alreadyExistedCount`, `totalCount`.

### InterviewerEvaluationListResponse / InterviewerEvaluationSummaryResponse

- package: `com.shinyoung.recruit.dto.response`
- type: Response DTO (records)
- list: interview context (`interviewId`, `interviewGroupName`, `interviewStatus`, `startDateTime`, `endDateTime`) + `evaluations[]`.
- summary item: `evaluationId`, `candidateParticipantId`, `applicationId`, `candidateName`, `positionId`, `positionName`, `status`, `grade`, `recommendation`, `comment`, `submittedAt`.
- notes: candidate name/position use `JobApplication` snapshot fields; no other interviewer's data is included.

### InterviewerEvaluationDetailResponse

- package: `com.shinyoung.recruit.dto.response`
- type: Response DTO (record): interview context + candidate context + evaluation fields (`status`, `grade`, `recommendation`, `comment`, `submittedAt`).

### InterviewEvaluationNotFoundException / InvalidInterviewEvaluationException

- package: `com.shinyoung.recruit.exception`
- types: RuntimeException
- mapping: 404 and 400 respectively in `GlobalExceptionHandler`.

## 9. API List

| Method | Path | Auth | Request | Response |
| --- | --- | --- | --- | --- |
| `POST` | `/admin/interviews/{interviewId}/evaluations/initialize` | ADMIN / RECRUIT_ADMIN | None | `InterviewEvaluationInitializeResponse` |
| `GET` | `/interviewer/interviews/{interviewId}/evaluations` | EMPLOYEE/INTERVIEWER/ADMIN | None | `InterviewerEvaluationListResponse` |
| `GET` | `/interviewer/interviews/{interviewId}/evaluations/{evaluationId}` | EMPLOYEE/INTERVIEWER/ADMIN | None | `InterviewerEvaluationDetailResponse` |
| `POST` | `/interviewer/interviews/{interviewId}/evaluations/{evaluationId}` | EMPLOYEE/INTERVIEWER/ADMIN | `InterviewEvaluationSaveRequest` | `InterviewerEvaluationDetailResponse` |
| `POST` | `/interviewer/interviews/{interviewId}/evaluations/{evaluationId}/submit` | EMPLOYEE/INTERVIEWER/ADMIN | `InterviewEvaluationSaveRequest` (optional) | `InterviewerEvaluationDetailResponse` |

All responses are wrapped in `ApiResponse<T>`.

## 10. Entity Relationship Summary

No entity changes in this slice. The relationships defined in 06a remain:

- `Interview` 1:N `InterviewEvaluation`
- `InterviewParticipant` (CANDIDATE / INTERVIEWER) 1:N `InterviewEvaluation`
- `JobApplication` 1:N `InterviewEvaluation` (denormalized)
- `Stage` 1:N `InterviewEvaluation` (denormalized)
- Unique: `(interview_id, candidate_participant_id, interviewer_participant_id)`

## 11. Business Rules

### Initialize
1. Interview must be CONFIRMED; DRAFT/CANCELLED rejected (`InvalidInterviewEvaluationException`).
2. Only ASSIGNED candidate and ASSIGNED interviewer participants form combinations.
3. Idempotent: existing (candidate, interviewer) rows are skipped and counted as `alreadyExistedCount`.

### Interviewer save
4. Every entry point resolves the caller's visible (CONFIRMED/CANCELLED) ASSIGNED interviewer participant first and scopes the evaluation lookup to that participant id; a cancelled or non-participant caller gets 404, and an evaluation that does not belong to that participant gets 404 (isolation).
5. Interview must be CONFIRMED; interviewer participant ASSIGNED; candidate participant ASSIGNED.
6. Evaluation must be DRAFT (SUBMITTED is immutable until reopened in 06d).
7. Comment max 2000 characters.

### Interviewer submit
8. All save guards apply.
9. `grade` and `recommendation` must be present (from existing draft or from the optional submit body).
10. DRAFT → SUBMITTED, `submittedAt` set from `Clock`.

### Read isolation
11. List, detail, save, and submit all require the caller to be a visible (CONFIRMED/CANCELLED) ASSIGNED interviewer participant; otherwise 404. DRAFT interviews are not visible, so their evaluations are unreachable.
12. List and detail return only the caller's own evaluations (scoped by participant id). Other interviewers' grades/recommendations/comments/status are never exposed, and a cancelled interviewer cannot read their own past evaluation by its id.

### StageResult boundary
13. No class in this slice references, injects, or mutates `StageResult`.

## 12. Test Coverage

- `InterviewEvaluationAdminServiceTest` (3): full N×M creation; idempotent re-run + cancelled-participant exclusion; non-CONFIRMED and missing-interview rejection.
- `InterviewerEvaluationServiceTest` (13): own-only list with interview context; non-assigned interviewer 404; cancelled interviewer list 404 (their evaluations not exposed); cross-interviewer detail isolation 404; cancelled interviewer detail 404 (blocking-issue regression); DRAFT-interview detail 404; draft save; submit + post-submit save block; submit without body using saved values; submit without body on empty draft 400; submit required-field validation; cancelled-interview save block; cancelled-candidate save block.
- `InterviewEvaluationAdminControllerTest` (3): admin initialize success; non-CONFIRMED 400; applicant forbidden / anonymous unauthorized.
- `InterviewerEvaluationControllerTest` (4): own-only list; save→submit happy path; cross-interviewer detail 404; applicant forbidden / anonymous unauthorized.

## 13. Test Commands & Results

- Command: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*Evaluation*" --no-daemon`
- Result: BUILD SUCCESSFUL.
  - `InterviewEvaluationAdminServiceTest`: 3 passed.
  - `InterviewerEvaluationServiceTest`: 13 passed.
  - `InterviewEvaluationAdminControllerTest`: 3 passed.
  - `InterviewerEvaluationControllerTest`: 4 passed.
  - `InterviewEvaluationTest` (06a regression): 23 passed.
  - `InterviewEvaluationRepositoryTest` (06a regression): 4 passed.
  - Total: 50 tests, 0 failures, 0 skipped (23 new for 06b + 27 06a regression).
- Note: per request, only the evaluation-related package tests were run (partial run), not the full suite.

## 14. Known Limitations

- Admin can only initialize and (programmatically) read; admin read APIs and summary aggregation arrive in 06c.
- No reopen endpoint yet; a SUBMITTED evaluation cannot return to DRAFT until 06d.
- A cancelled interviewer participant cannot list or read their past evaluations (visible-participant guard at every entry point); admin audit read is a 06c concern.
- Initialize is idempotent for sequential calls only. Two concurrent initialize requests for the same interview (e.g., admin double-click) can both observe `exists == false` for the same (candidate, interviewer) pair and race on insert; the DB unique constraint `(interview_id, candidate_participant_id, interviewer_participant_id)` then rejects one transaction with a `DataIntegrityViolationException`. This is accepted as a known limitation for the current MVP; hardening options are a pessimistic lock on the interview row before initialize, or catching the violation and re-reading. No data corruption can occur because the unique constraint is the backstop.
- No DB migration file; schema is H2-generated for tests.

## 15. Next Phase Considerations

- Phase 06c: admin interview/stage/application-level read with candidate-grouped summary, `GradeDistribution`, `RecommendationDistribution`.
- Phase 06d: admin reopen command, ActivityLog history, explicit StageResult non-mutation enforcement.
