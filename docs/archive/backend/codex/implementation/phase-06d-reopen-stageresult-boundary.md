# Phase 06d - Reopen + StageResult Boundary

## 1. Phase Summary

- Phase name: Phase 06d - Reopen + StageResult Boundary
- Work type: admin command implementation slice with targeted tests.
- Date: 2026-05-29
- Goal: add the admin reopen command (SUBMITTED → DRAFT), record the action in an audit log, and enforce/document the StageResult non-mutation guarantee.
- Status: implemented and tested.

This slice builds on Phase 06a–06c. It adds the reopen endpoint defined in the Phase 06 design (`docs/codex/design/phase-06-interview-evaluation-design.md`, §9.1, §10.4, slice 06d) and reuses the entity-level `reopen()` transition implemented in 06a.

## 2. Purpose

Administrators occasionally need to let an interviewer correct a submitted evaluation. Reopen flips a SUBMITTED evaluation back to DRAFT and clears `submittedAt`, after which the owning interviewer can edit and re-submit through the existing 06b APIs. The action is auditable and never touches `StageResult`.

## 3. Implemented Scope

- `POST /admin/interviews/{interviewId}/evaluations/{evaluationId}/reopen` — admin reopen command.
- SUBMITTED → DRAFT transition with `submittedAt` cleared (entity `reopen()` from 06a).
- Guard: only SUBMITTED evaluations can be reopened (400 otherwise); evaluation must belong to the interview (404 otherwise); actor required.
- Reopen-target state guards: interview must be CONFIRMED, candidate participant ASSIGNED, interviewer participant ASSIGNED (400 otherwise). Reopening into a cancelled state would produce a DRAFT the interviewer could never re-submit (06b write guards block it), so reopen is blocked there.
- Audit: reopen action recorded via the application audit log (actor, evaluation id, interview id, previous `submittedAt`, reopen timestamp).
- StageResult non-mutation guarantee: the evaluation entity, service, and controller never reference, inject, or call `StageResult`/`StageResultRepository`/`StageResultService`.
- StageResult reflection command explicitly deferred to a future phase.
- Targeted service (3) and controller (2) tests.

## 4. Out Of Scope

- Persistent `ActivityLog` domain (entity/repository) — deferred until the ActivityLog domain root is implemented; see Known Limitations.
- StageResult reflect/sync command (deferred to a future phase, design §12.3).
- Reopen-specific fields on the entity (design decision 9: status transition only).
- Excel/PDF/statistics, messaging.
- DB migration / DDL files.

## 5. Changed Files

| File | Change | Type |
| --- | --- | --- |
| `src/main/java/com/shinyoung/recruit/domain/repository/InterviewEvaluationRepository.java` | Modified | Repository |
| `src/main/java/com/shinyoung/recruit/service/InterviewEvaluationAdminService.java` | Modified | Service |
| `src/main/java/com/shinyoung/recruit/controller/InterviewEvaluationAdminController.java` | Modified | Controller |
| `src/test/java/com/shinyoung/recruit/service/InterviewEvaluationAdminServiceTest.java` | Modified | Test |
| `src/test/java/com/shinyoung/recruit/controller/InterviewEvaluationAdminControllerTest.java` | Modified | Test |
| `docs/codex/implementation/phase-06d-reopen-stageresult-boundary.md` | New | Doc |
| `docs/codex/reports/phase-06d-reopen-stageresult-boundary.html` | New | Doc |
| `docs/codex/07-implementation-history.md` | Modified | Doc |
| `docs/codex/06-implementation-roadmap.md` | Modified | Doc |

## 6. New Classes

- None. The reopen response reuses the existing `AdminEvaluationItemResponse` (06c).

## 7. Modified Classes

- `InterviewEvaluationRepository` — added `findAdminDetailByIdAndInterviewId(evaluationId, interviewId)` (fetch joins interview/stage/candidate participant/interviewer participant/employee; not scoped by participant, since admin is not an interviewer participant).
- `InterviewEvaluationAdminService` — added `reopen(interviewId, evaluationId, actor)` and a private `validateActor`; injected `Clock` and an SLF4J logger for the audit record.
- `InterviewEvaluationAdminController` — added the reopen `POST` endpoint; injected `CurrentEmployeeService` to resolve the admin actor (`getCurrentEmployeeActor`).

## 8. Class-by-Class Explanation

### InterviewEvaluationAdminService (modified)

- package: `com.shinyoung.recruit.service`
- type: Service
- new method `reopen(Long interviewId, Long evaluationId, String actor)`:
  - validates actor is non-blank (`InvalidInterviewEvaluationException` otherwise).
  - loads the evaluation via `findAdminDetailByIdAndInterviewId`; 404 (`InterviewEvaluationNotFoundException`) if no row matches the interview.
  - requires `evaluation.isSubmitted()`; otherwise 400 (`InvalidInterviewEvaluationException`). This pre-check produces a clean domain error instead of the entity's raw `IllegalStateException`.
  - requires the reopen target to be re-submittable: interview CONFIRMED, candidate participant ASSIGNED, interviewer participant ASSIGNED; otherwise 400. Without these, reopen could leave a DRAFT that the 06b write guards forbid re-submitting.
  - captures `previousSubmittedAt`, calls `evaluation.reopen()` (SUBMITTED → DRAFT, clears `submittedAt`).
  - records an audit log line (actor, evaluationId, interviewId, previousSubmittedAt, reopenedAt from `Clock`).
  - returns `AdminEvaluationItemResponse.from(evaluation)`.
- notes: grade/recommendation/comment are preserved on reopen; only `status` and `submittedAt` change. No reference to `StageResult` anywhere.

### InterviewEvaluationAdminController (modified)

- package: `com.shinyoung.recruit.controller`
- new endpoint `POST /admin/interviews/{interviewId}/evaluations/{evaluationId}/reopen`:
  - resolves the admin actor via `CurrentEmployeeService.getCurrentEmployeeActor(userDetails)`.
  - returns `ApiResponse<AdminEvaluationItemResponse>`.
- notes: lives under `/admin/**`, so SecurityConfig restricts it to ROLE_ADMIN / ROLE_RECRUIT_ADMIN.

### InterviewEvaluationRepository (modified)

- new query `findAdminDetailByIdAndInterviewId` fetches interview, stage, interviewer participant, and employee for building the admin item response.

## 9. API List

| Method | Path | Auth | Request | Response |
| --- | --- | --- | --- | --- |
| `POST` | `/admin/interviews/{interviewId}/evaluations/{evaluationId}/reopen` | ADMIN / RECRUIT_ADMIN | None | `AdminEvaluationItemResponse` |

Response wrapped in `ApiResponse<T>`. After reopen, `status = DRAFT` and `submittedAt = null`.

## 10. Entity Relationship Summary

No entity changes. The reopen transition reuses the 06a `InterviewEvaluation.reopen()` method. No new relationships, no `StageResult` reference.

## 11. Business Rules (Reopen)

1. Evaluation must belong to the path interview; otherwise 404.
2. Only SUBMITTED evaluations can be reopened; DRAFT → 400.
3. Reopen target must be re-submittable: interview CONFIRMED, candidate participant ASSIGNED, interviewer participant ASSIGNED; otherwise 400. This keeps reopen consistent with the 06b write guards so a reopened DRAFT can always be edited and re-submitted.
4. Reopen sets `status = DRAFT` and clears `submittedAt`. Grade/recommendation/comment are preserved.
5. After reopen, the owning interviewer can save/submit again through the 06b APIs (DRAFT is writable).
6. Actor is required (resolved from the authenticated admin); reopen is recorded in the audit log.
7. StageResult non-mutation: no class in the evaluation feature references, injects, or mutates `StageResult`. Final pass/fail remains an explicit administrator decision via the existing StageResult update/bulk-update APIs.
8. No automatic or semi-automatic StageResult reflection is triggered by reopen or by any evaluation action.

## 12. StageResult Boundary Enforcement

- `InterviewEvaluation`, `InterviewEvaluationAdminService`, `InterviewerEvaluationService`, and the two controllers have no field, parameter, import, or call referencing `StageResult`, `StageResultRepository`, or `StageResultService`. The guarantee holds by construction.
- A dedicated StageResult reflection command (e.g. `POST /admin/stages/{stageId}/results/reflect-interview-evaluations`) remains explicitly deferred (design §12.3): reflection requires scoring criteria not yet defined, and the manual StageResult update/bulk-update workflow is sufficient.

## 13. Test Coverage

- `InterviewEvaluationAdminServiceTest` (6 new, 13 total): reopen SUBMITTED → DRAFT clears `submittedAt` and preserves grade (reload verified); reopen rejects DRAFT (400); reopen rejects missing evaluation (404) and blank actor (400); reopen rejects cancelled interview (400); reopen rejects cancelled candidate participant (400); reopen rejects cancelled interviewer participant (400).
- `InterviewEvaluationAdminControllerTest` (2 new, 8 total): reopen happy path returns DRAFT with preserved grade and interviewer identity; reopen rejects DRAFT (400) and blocks applicant (403).

## 14. Test Commands & Results

- Command: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*Evaluation*" --no-daemon`
- Result: BUILD SUCCESSFUL — 65 tests, 0 failures, 0 skipped.
  - `InterviewEvaluationAdminServiceTest`: 13 passed (6 new for 06d).
  - `InterviewEvaluationAdminControllerTest`: 8 passed (2 new for 06d).
  - `InterviewerEvaluationServiceTest`: 13 passed (06b regression).
  - `InterviewerEvaluationControllerTest`: 4 passed (06b regression).
  - `InterviewEvaluationTest`: 23 passed (06a regression).
  - `InterviewEvaluationRepositoryTest`: 4 passed (06a regression).
- Note: per request, only the evaluation-related package tests were run (partial run), not the full suite.

## 15. Known Limitations

- Reopen history is written to the application audit log (SLF4J), not a queryable store. Persistent `ActivityLog` recording is deferred until the ActivityLog domain root is implemented; at that point the reopen audit should be migrated to it. No reopen-specific fields were added to the entity (design decision 9).
- Initialize concurrency limitation from 06b still applies (sequential idempotency only).
- No DB migration file; schema is H2-generated for tests.

## 16. Next Phase Considerations

- Phase 06e: stabilization / test hardening — N×M matrix regression, cancelled interview/participant guards, reopen → re-submit cycle, SUBMITTED immutability, StageResult non-mutation regression.
- Future: ActivityLog domain root, then migrate the reopen audit record to it.
- Future: StageResult reflection command with configurable scoring criteria.
