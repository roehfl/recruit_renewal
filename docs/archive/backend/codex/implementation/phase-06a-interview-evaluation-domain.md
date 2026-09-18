# Phase 06a - InterviewEvaluation Domain

## 1. Phase Summary

- Phase name: Phase 06a - InterviewEvaluation Domain
- Work type: domain/enum/repository implementation slice with targeted tests.
- Date: 2026-05-28
- Goal: implement the backend domain foundation for interview evaluation capture on top of the Phase 04 interview scheduling infrastructure, without adding APIs, services, or controllers.
- Status: implemented and tested.

This slice adds the `InterviewEvaluation` entity, its evaluation enums, the repository, and targeted entity/repository tests. It follows the Phase 06 design (`docs/codex/design/phase-06-interview-evaluation-design.md`), with the grade-based evaluation decision (enum grade instead of numeric score).

## 2. Purpose

Phase 06a establishes the persistence model for a single interviewer's evaluation of a single candidate within a single interview. `InterviewEvaluation` is evaluation evidence only; it does not reference or mutate `StageResult`.

## 3. Implemented Scope

- `EvaluationStatus` enum (DRAFT, SUBMITTED).
- `EvaluationGrade` enum (F, G_MINUS, G, G_PLUS, VG).
- `EvaluationRecommendation` enum (STRONG_YES, YES, NEUTRAL, NO, STRONG_NO).
- `InterviewEvaluation` entity with FK references, denormalized fields, unique constraint, indexes.
- Entity validation: role checks, same-interview checks, comment max length, submit-requires-grade+recommendation.
- Entity status transitions: initialize → DRAFT, updateContent (DRAFT only), submit (DRAFT → SUBMITTED), reopen (SUBMITTED → DRAFT).
- `InterviewEvaluationRepository` with `findByInterviewId` and unique-key existence query.
- Targeted entity tests (23) and repository tests (4).

## 4. Out Of Scope

- Admin initialize command and API (Phase 06b).
- Interviewer save/submit/list/detail APIs (Phase 06b).
- Admin evaluation read APIs and summary aggregation (Phase 06c).
- Admin reopen API endpoint (Phase 06d).
- `GradeDistribution` / `RecommendationDistribution` response DTOs (Phase 06c).
- `StageResult` reflection (deferred).
- DB migration / DDL files.

## 5. Changed Files

| File | Change | Type |
| --- | --- | --- |
| `src/main/java/com/shinyoung/recruit/enumeration/EvaluationStatus.java` | New | Enum |
| `src/main/java/com/shinyoung/recruit/enumeration/EvaluationGrade.java` | New | Enum |
| `src/main/java/com/shinyoung/recruit/enumeration/EvaluationRecommendation.java` | New | Enum |
| `src/main/java/com/shinyoung/recruit/domain/entity/InterviewEvaluation.java` | New | Entity |
| `src/main/java/com/shinyoung/recruit/domain/repository/InterviewEvaluationRepository.java` | New | Repository |
| `src/test/java/com/shinyoung/recruit/domain/entity/InterviewEvaluationTest.java` | New | Test |
| `src/test/java/com/shinyoung/recruit/domain/repository/InterviewEvaluationRepositoryTest.java` | New | Test |
| `docs/codex/implementation/phase-06a-interview-evaluation-domain.md` | New | Doc |
| `docs/codex/reports/phase-06a-interview-evaluation-domain.html` | New | Doc |
| `docs/codex/07-implementation-history.md` | Updated | Doc |
| `docs/codex/06-implementation-roadmap.md` | Updated | Doc |

## 6. Class-by-Class Explanation

### EvaluationStatus

- package: `com.shinyoung.recruit.enumeration`
- type: Enum
- responsibility: evaluation lifecycle state.
- values: `DRAFT`, `SUBMITTED`.

### EvaluationGrade

- package: `com.shinyoung.recruit.enumeration`
- type: Enum
- responsibility: interviewer grade for a candidate.
- values: `F`, `G_MINUS`, `G`, `G_PLUS`, `VG` (declared lowest → highest).
- note: enum-based grade replaces the earlier numeric score design. Grade name changes require only an enum edit plus data migration.

### EvaluationRecommendation

- package: `com.shinyoung.recruit.enumeration`
- type: Enum
- responsibility: interviewer recommendation for a candidate.
- values: `STRONG_YES`, `YES`, `NEUTRAL`, `NO`, `STRONG_NO`.

### InterviewEvaluation

- package: `com.shinyoung.recruit.domain.entity`
- type: Entity (extends `BaseEntity`)
- responsibility: one interviewer's evaluation of one candidate within one interview.
- key fields:
  - `interview` (LAZY ManyToOne, required)
  - `candidateParticipant` (LAZY ManyToOne, required, role CANDIDATE)
  - `interviewerParticipant` (LAZY ManyToOne, required, role INTERVIEWER)
  - `jobApplication` (LAZY ManyToOne, required, denormalized from candidateParticipant)
  - `stage` (LAZY ManyToOne, required, denormalized from interview)
  - `grade` (`EvaluationGrade`, nullable in DRAFT)
  - `recommendation` (`EvaluationRecommendation`, nullable in DRAFT)
  - `comment` (String, max 2000)
  - `status` (`EvaluationStatus`, default DRAFT)
  - `submittedAt` (LocalDateTime, set on submit, cleared on reopen)
- key methods:
  - `initialize(interview, candidateParticipant, interviewerParticipant)` static factory → DRAFT row, derives `jobApplication` and `stage`.
  - `updateContent(grade, recommendation, comment)` → DRAFT only; allows null grade/recommendation; validates comment length.
  - `submit(submittedAt)` → DRAFT only; requires grade + recommendation; transitions to SUBMITTED.
  - `reopen()` → SUBMITTED only; transitions to DRAFT and clears `submittedAt`.
  - `isDraft()`, `isSubmitted()`.
- related classes: `Interview`, `InterviewParticipant`, `JobApplication`, `Stage`, evaluation enums.
- implementation notes:
  - Unique constraint `(interview_id, candidate_participant_id, interviewer_participant_id)`.
  - No reference to `StageResult` anywhere.
  - `jobApplication`/`stage` set at initialization and never updated.
  - `COMMENT_MAX_LENGTH = 2000`.

### InterviewEvaluationRepository

- package: `com.shinyoung.recruit.domain.repository`
- type: Repository (`JpaRepository<InterviewEvaluation, Long>`)
- responsibility: persistence access for evaluations.
- key methods:
  - `findByInterviewId(Long)` — interview-level lookup.
  - `existsByInterviewIdAndCandidateParticipantIdAndInterviewerParticipantId(...)` — idempotent-initialize support (used by Phase 06b).

### InterviewEvaluationTest

- package: `com.shinyoung.recruit.domain.entity` (test)
- type: Test
- responsibility: verify factory, validation, and status transitions.
- coverage: initialize defaults/denormalization, role validation, null-argument guards (interview/candidate/interviewer/stage/jobApplication), cross-interview membership guards, updateContent behavior, comment max-length and blank-normalization, submit required-field and null-submittedAt guards, SUBMITTED immutability, reopen behavior, DRAFT reopen guard. Two defensive-branch tests (null stage, null jobApplication) use Mockito since the real factories prevent those states.

### InterviewEvaluationRepositoryTest

- package: `com.shinyoung.recruit.domain.repository` (test)
- type: Test (`@DataJpaTest`)
- responsibility: verify persistence, queries, and the unique constraint.
- coverage: save + `findByInterviewId`, grade/recommendation/comment/submittedAt persistence, existence query, duplicate-combination unique-constraint violation.

## 7. API List

- None. Phase 06a is a domain-only slice. No controller, request DTO, response DTO, or service was added.

## 8. Entity Relationship Summary

- `Interview` 1:N `InterviewEvaluation`
- `InterviewParticipant` (CANDIDATE) 1:N `InterviewEvaluation`
- `InterviewParticipant` (INTERVIEWER) 1:N `InterviewEvaluation`
- `JobApplication` 1:N `InterviewEvaluation` (denormalized)
- `Stage` 1:N `InterviewEvaluation` (denormalized)
- Unique: `(interview_id, candidate_participant_id, interviewer_participant_id)`

## 9. Business Rules

1. Both candidate and interviewer are referenced via `InterviewParticipant`, not `Employee`/`JobApplication` directly.
2. `candidateParticipant.role` must be CANDIDATE; `interviewerParticipant.role` must be INTERVIEWER.
3. Both participants must belong to the evaluation's interview.
4. `jobApplication` and `stage` are derived at initialization and immutable thereafter.
5. `grade` and `recommendation` are nullable in DRAFT, required on submit.
6. `comment` is optional, max 2000 characters.
7. Only DRAFT evaluations can be updated or submitted.
8. Only SUBMITTED evaluations can be reopened; reopen clears `submittedAt`.
9. The entity never references `StageResult`.

## 10. Test Coverage

- `InterviewEvaluationTest`: 23 tests (factory, validation, transitions).
- `InterviewEvaluationRepositoryTest`: 4 tests (persistence, query, unique constraint).

## 11. Test Commands & Results

- Command: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*InterviewEvaluation*" --no-daemon`
- Result: BUILD SUCCESSFUL (27 tests, 0 failures, 0 skipped).

## 12. Known Limitations

- No service/API layer yet; rows can only be created programmatically.
- Repository query set is minimal; admin/interviewer/stage/application queries arrive in 06b/06c.
- No DB migration file; schema is H2 generated for tests.

## 13. Next Phase Considerations

- Phase 06b: Admin initialize command + interviewer save/submit/list/detail APIs, ownership and CONFIRMED/ASSIGNED guards, SUBMITTED immutability enforcement, interviewer information isolation.
