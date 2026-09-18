# Phase 06c - Admin Evaluation Read

## 1. Phase Summary

- Phase name: Phase 06c - Admin Evaluation Read
- Work type: read API / response-DTO implementation slice with targeted tests.
- Date: 2026-05-29
- Goal: expose administrator evaluation read APIs at interview, stage, and application level with candidate-grouped responses and per-candidate summary aggregation.
- Status: implemented and tested.

This slice builds on Phase 06a (`InterviewEvaluation` domain) and Phase 06b (initialize + interviewer write). It adds the admin read surface defined in the Phase 06 design (`docs/codex/design/phase-06-interview-evaluation-design.md`, §9.1, §11, slice 06c) and reuses the denormalized `stage`/`jobApplication` FKs for direct index queries.

## 2. Purpose

Administrators need to view interviewer evaluations to drive the manual `StageResult` confirmation workflow. Phase 06c provides three read levels:

- interview-level: all evaluations of one interview, grouped by candidate, each with a summary.
- stage-level: the same shape, one entry per interview across the whole stage.
- application-level: one entry per interview a single candidate took part in (candidate fixed, no candidate grouping).

Summary counts and grade/recommendation distributions are computed from SUBMITTED evaluations only; DRAFT rows are listed but excluded from distributions. No code references or mutates `StageResult`.

## 3. Implemented Scope

- `GET /admin/interviews/{interviewId}/evaluations` — interview-level, candidate-grouped.
- `GET /admin/stages/{stageId}/interview-evaluations` — stage-level (list of interview-level responses).
- `GET /admin/applications/{applicationId}/interview-evaluations` — application-level (list per interview).
- Candidate-grouped response with per-candidate `AdminEvaluationSummaryResponse`.
- `GradeDistribution` and `RecommendationDistribution` typed DTOs (all five fields always present).
- Summary aggregation: `submittedCount`, `totalEvaluatorCount`, distributions from SUBMITTED only.
- Admin item view exposes interviewer identity (`interviewerName`).
- Three admin read repository queries with fetch joins.
- Existence guards: interview / stage / application 404.
- Targeted service (4) and controller (3) tests.

## 4. Out Of Scope

- Admin reopen API and ActivityLog history (Phase 06d).
- `StageResult` reflection (deferred).
- Excel/PDF/statistics, messaging.
- DB migration / DDL files.
- Pagination of evaluation reads (practical N×M volume is small; design §15 decision 17).

## 5. Changed Files

| File | Change | Type |
| --- | --- | --- |
| `src/main/java/com/shinyoung/recruit/dto/response/GradeDistribution.java` | New | Response DTO |
| `src/main/java/com/shinyoung/recruit/dto/response/RecommendationDistribution.java` | New | Response DTO |
| `src/main/java/com/shinyoung/recruit/dto/response/AdminEvaluationItemResponse.java` | New | Response DTO |
| `src/main/java/com/shinyoung/recruit/dto/response/AdminEvaluationSummaryResponse.java` | New | Response DTO |
| `src/main/java/com/shinyoung/recruit/dto/response/AdminEvaluationCandidateResponse.java` | New | Response DTO |
| `src/main/java/com/shinyoung/recruit/dto/response/AdminInterviewEvaluationResponse.java` | New | Response DTO |
| `src/main/java/com/shinyoung/recruit/dto/response/AdminApplicationEvaluationResponse.java` | New | Response DTO |
| `src/main/java/com/shinyoung/recruit/domain/repository/InterviewEvaluationRepository.java` | Modified | Repository |
| `src/main/java/com/shinyoung/recruit/service/InterviewEvaluationAdminService.java` | Modified | Service |
| `src/main/java/com/shinyoung/recruit/controller/InterviewEvaluationAdminController.java` | Modified | Controller |
| `src/test/java/com/shinyoung/recruit/service/InterviewEvaluationAdminServiceTest.java` | Modified | Test |
| `src/test/java/com/shinyoung/recruit/controller/InterviewEvaluationAdminControllerTest.java` | Modified | Test |
| `docs/codex/implementation/phase-06c-admin-evaluation-read.md` | New | Doc |
| `docs/codex/reports/phase-06c-admin-evaluation-read.html` | New | Doc |
| `docs/codex/07-implementation-history.md` | Modified | Doc |
| `docs/codex/06-implementation-roadmap.md` | Modified | Doc |

## 6. New Classes

- `GradeDistribution`
- `RecommendationDistribution`
- `AdminEvaluationItemResponse`
- `AdminEvaluationSummaryResponse`
- `AdminEvaluationCandidateResponse`
- `AdminInterviewEvaluationResponse`
- `AdminApplicationEvaluationResponse`

## 7. Modified Classes

- `InterviewEvaluationRepository` — added `findByInterviewIdForAdmin`, `findByStageIdForAdmin`, `findByJobApplicationIdForAdmin` (fetch joins on interview/stage/candidate/application/jobPosition/interviewer/employee).
- `InterviewEvaluationAdminService` — added `getInterviewEvaluations`, `getStageEvaluations`, `getApplicationEvaluations` and a private `groupByInterview` helper; injected `StageRepository` and `JobApplicationRepository` for existence guards.
- `InterviewEvaluationAdminController` — dropped the class-level base path and switched to full method paths so the single admin controller (design §9.3) can serve `/admin/interviews/...`, `/admin/stages/...`, and `/admin/applications/...`; added the three GET endpoints alongside the existing initialize.

## 8. Class-by-Class Explanation

### GradeDistribution / RecommendationDistribution

- package: `com.shinyoung.recruit.dto.response`
- type: Response DTO (record)
- responsibility: type-safe count per enum value, always carrying all five fields (zero when absent).
- key method: static `from(List<InterviewEvaluation> submittedEvaluations)` — counts via switch; null grade/recommendation is skipped defensively.
- notes: `GradeDistribution` fields are ordered highest→lowest (vg, gPlus, g, gMinus, f).

### AdminEvaluationItemResponse

- package: `com.shinyoung.recruit.dto.response`
- type: Response DTO (record)
- responsibility: one interviewer's evaluation row for the admin view, exposing interviewer identity.
- fields: `evaluationId`, `interviewerParticipantId`, `interviewerName`, `status`, `grade`, `recommendation`, `comment`, `submittedAt`.
- notes: unlike the interviewer-facing DTOs (06b), admin sees who evaluated.

### AdminEvaluationSummaryResponse

- package: `com.shinyoung.recruit.dto.response`
- type: Response DTO (record)
- responsibility: per-candidate (or per-interview, for application-level) summary.
- fields: `submittedCount`, `totalEvaluatorCount`, `gradeDistribution`, `recommendationDistribution`.
- key method: `from(List<InterviewEvaluation>)` — filters SUBMITTED for the distributions, uses the full list size for `totalEvaluatorCount`.

### AdminEvaluationCandidateResponse

- package: `com.shinyoung.recruit.dto.response`
- type: Response DTO (record)
- responsibility: one candidate's evaluations within an interview, grouped with a summary.
- fields: `candidateParticipantId`, `applicationId`, `applicantName`, `positionId`, `positionName`, `summary`, `evaluations`.
- key method: `from(List<InterviewEvaluation> candidateEvaluations)` — reads candidate/application snapshots from the first row.

### AdminInterviewEvaluationResponse

- package: `com.shinyoung.recruit.dto.response`
- type: Response DTO (record)
- responsibility: interview-level view; groups a flat evaluation list by candidate participant (insertion order preserved via `LinkedHashMap`).
- key method: `of(Interview, List<InterviewEvaluation>)`.
- fields: interview context (`interviewId`, `interviewGroupName`, `stageId`, `stageName`, `interviewStatus`, `startDateTime`, `endDateTime`) + `candidates`.

### AdminApplicationEvaluationResponse

- package: `com.shinyoung.recruit.dto.response`
- type: Response DTO (record)
- responsibility: application-level element; evaluations sit directly under the interview (candidate fixed).
- key method: `from(List<InterviewEvaluation> interviewEvaluations)`.
- fields: interview context + `summary` + `evaluations`.

### InterviewEvaluationAdminService (modified)

- `getInterviewEvaluations(interviewId)` — loads the interview via `findAdminDetailById` (stage fetched; 404 otherwise), then groups `findByInterviewIdForAdmin`.
- `getStageEvaluations(stageId)` — `stageRepository.existsById` guard (404), then `findByStageIdForAdmin` grouped by interview into a list of interview-level responses.
- `getApplicationEvaluations(applicationId)` — `jobApplicationRepository.existsById` guard (404), then `findByJobApplicationIdForAdmin` grouped by interview into application-level elements.
- `groupByInterview` — `LinkedHashMap` keyed by interview id; query ordering keeps interviews in start-time order.
- notes: read-only methods; never touches `StageResult`.

## 9. API List

| Method | Path | Auth | Response |
| --- | --- | --- | --- |
| `GET` | `/admin/interviews/{interviewId}/evaluations` | ADMIN / RECRUIT_ADMIN | `AdminInterviewEvaluationResponse` |
| `GET` | `/admin/stages/{stageId}/interview-evaluations` | ADMIN / RECRUIT_ADMIN | `List<AdminInterviewEvaluationResponse>` |
| `GET` | `/admin/applications/{applicationId}/interview-evaluations` | ADMIN / RECRUIT_ADMIN | `List<AdminApplicationEvaluationResponse>` |

All responses are wrapped in `ApiResponse<T>`. (The 06b `POST .../evaluations/initialize` remains on the same controller.)

## 10. Entity Relationship Summary

No entity changes. Reads exploit the denormalized FKs from 06a:

- `findByStageIdForAdmin` filters on `evaluation.stage.id` directly.
- `findByJobApplicationIdForAdmin` filters on `evaluation.jobApplication.id` directly.
- No multi-join traversal through `interview`/`candidateParticipant` is required for the WHERE clause.

## 11. Business Rules

1. Interview/stage/application must exist; otherwise 404 (`InterviewNotFoundException` / `StageNotFoundException` / `JobApplicationNotFoundException`).
2. Interview-level groups evaluations by candidate participant, preserving candidate sort order.
3. `submittedCount` counts SUBMITTED rows; `totalEvaluatorCount` counts all rows (DRAFT + SUBMITTED).
4. `gradeDistribution` and `recommendationDistribution` count SUBMITTED rows only; DRAFT rows are listed but excluded from distributions.
5. Distribution DTOs always include all five fields (zero when a category has no submissions).
6. The admin item view exposes `interviewerName`; the interviewer-facing DTOs (06b) do not expose other interviewers.
7. Stage-level and application-level reads only surface interviews that have evaluation rows (interviews never initialized do not appear).
8. No class references, injects, or mutates `StageResult`.

## 12. Test Coverage

- `InterviewEvaluationAdminServiceTest` (4 new, 7 total): interview-level candidate grouping + SUBMITTED-only summary/distribution with one DRAFT excluded; stage-level one-entry-per-interview; application-level one-entry-per-interview for a fixed candidate; not-found for interview/stage/application.
- `InterviewEvaluationAdminControllerTest` (3 new, 6 total): interview-level candidate-grouped summary JSON (counts, distributions, interviewerName); stage-level array JSON; application-level array JSON + applicant forbidden.

## 13. Test Commands & Results

- Command: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*Evaluation*" --no-daemon`
- Result: BUILD SUCCESSFUL — 57 tests, 0 failures, 0 skipped.
  - `InterviewEvaluationAdminServiceTest`: 7 passed (4 new for 06c).
  - `InterviewEvaluationAdminControllerTest`: 6 passed (3 new for 06c).
  - `InterviewerEvaluationServiceTest`: 13 passed (06b regression).
  - `InterviewerEvaluationControllerTest`: 4 passed (06b regression).
  - `InterviewEvaluationTest`: 23 passed (06a regression).
  - `InterviewEvaluationRepositoryTest`: 4 passed (06a regression).
- Note: per request, only the evaluation-related package tests were run (partial run), not the full suite.

## 14. Known Limitations

- Reads are unpaginated. Acceptable per design (practical N×M is a few hundred at most); pagination can be added later if needed.
- Stage/application reads list only interviews that have evaluation rows; interviews not yet initialized are omitted.
- Initialize concurrency limitation from 06b still applies (sequential idempotency only).
- No DB migration file; schema is H2-generated for tests.

## 15. Next Phase Considerations

- Phase 06d: admin reopen command (SUBMITTED → DRAFT), ActivityLog history, explicit StageResult non-mutation enforcement.
- Phase 06e: N×M matrix and guard regression hardening.
