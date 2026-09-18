# Phase 06 - Interview Evaluation Design

## 1. Phase Summary

- Phase name: Phase 06 - Interview Evaluation
- Work type: documentation-only design phase
- Date: 2026-05-27
- Purpose: define the backend design for interviewer evaluation capture, admin evaluation read, and StageResult boundary policy on top of the existing interview scheduling infrastructure.
- Status: design completed, Java/source/test implementation not started.

This phase defines the domain, API shape, and business rules for interview evaluation. It does not implement Java source, tests, DB migration, Excel/PDF/statistics, evaluation template management, weighted scoring, automatic pass/fail determination, or message delivery.

## 2. Purpose

The project already has `Interview` and `InterviewParticipant` implementation from Phase 04. Phase 06 introduces the evaluation layer that captures each interviewer's assessment of each candidate within a confirmed interview.

The design keeps responsibilities separated:

- `InterviewEvaluation` is evaluation evidence: an interviewer's score, recommendation, and comment for a specific candidate in a specific interview.
- `StageResult` remains the final decision record: PASS/FAIL/PENDING determined by an administrator.
- Interviewer `submit` never creates, updates, announces, or corrects `StageResult`.
- `InterviewEvaluation` must not become an alternative result announcement channel. Administrators view evaluation summaries and then use existing `StageResult` update/bulk update APIs to confirm final results.

## 3. Scope

Designed by this document:

- `InterviewEvaluation` entity design.
- `EvaluationStatus` enum design.
- `EvaluationRecommendation` enum design.
- `InterviewEvaluationRepository` design.
- Admin initialize command for creating evaluation rows.
- Interviewer evaluation save (draft) and submit APIs.
- Interviewer evaluation list and detail APIs.
- Admin evaluation read APIs at three levels: interview, stage, and application.
- Admin reopen command for submitted evaluations.
- Evaluation summary aggregation design.
- StageResult non-mutation guarantee policy.
- Unique constraint and index candidates.
- Phase split into implementation slices.

## 4. Out Of Scope

The following items are explicitly not part of Phase 06:

- Java source implementation.
- Test source implementation.
- Database migration files or DDL generation.
- Excel upload/download.
- PDF generation.
- Statistics or reporting.
- Evaluation template management.
- Weighted grading or per-item detailed grading.
- Automatic pass/fail determination based on evaluation scores.
- `StageResult` creation, update, announcement, correction, or publication triggered by interviewer submission.
- StageResult reflect/sync command (deferred to a future phase).
- SMS, email, Alimtalk, or notification delivery.
- Frontend or static resource generation.
- Admin memo on evaluation rows (deferred to a future phase).

## 5. Changed Files

| File | Change |
| --- | --- |
| `docs/codex/design/phase-06-interview-evaluation-design.md` | Added Phase 06 Interview Evaluation design source of truth. |
| `docs/codex/reports/phase-06-interview-evaluation-design.html` | Added self-contained human-readable design report. |
| `docs/codex/06-implementation-roadmap.md` | Updated Phase 06 section with detailed slice breakdown and design outputs. |
| `docs/codex/07-implementation-history.md` | Added Phase 06 design history entry. |
| `docs/codex/reports/current-implementation-status.html` | Updated current status report to show Phase 06 design as completed. |

## 6. Domain Model Design

### 6.1 InterviewEvaluation

Package candidate: `com.shinyoung.recruit.domain.entity`

Class type: Entity

Responsibility:

- Represents one interviewer's evaluation of one candidate within one interview.
- Stores score, recommendation, comment, and submission status.
- Does not determine or mutate `StageResult`.

Candidate fields:

| Field | Type | Rule |
| --- | --- | --- |
| `id` | `Long` | PK with identity generation. |
| `interview` | `Interview` | Required, lazy many-to-one. The interview this evaluation belongs to. |
| `candidateParticipant` | `InterviewParticipant` | Required, lazy many-to-one. Must have `role = CANDIDATE`. |
| `interviewerParticipant` | `InterviewParticipant` | Required, lazy many-to-one. Must have `role = INTERVIEWER`. |
| `jobApplication` | `JobApplication` | Required, lazy many-to-one. Denormalized from `candidateParticipant.jobApplication` for query convenience. |
| `stage` | `Stage` | Required, lazy many-to-one. Denormalized from `interview.stage` for query convenience. |
| `grade` | `EvaluationGrade` | Nullable in DRAFT. Required for SUBMIT. 5-level grade enum. |
| `recommendation` | `EvaluationRecommendation` | Nullable in DRAFT. Required for SUBMIT. |
| `comment` | `String(2000)` | Optional in both DRAFT and SUBMITTED. Max 2000 characters. |
| `status` | `EvaluationStatus` | Required. Default `DRAFT`. |
| `submittedAt` | `LocalDateTime` | Nullable. Set on submit. Cleared on reopen. |

Extends `BaseEntity` for `createdAt` and `updatedAt`.

Relationship summary:

- `Interview` 1:N `InterviewEvaluation`
- `InterviewParticipant` (CANDIDATE) 1:N `InterviewEvaluation`
- `InterviewParticipant` (INTERVIEWER) 1:N `InterviewEvaluation`
- `JobApplication` 1:N `InterviewEvaluation` (denormalized)
- `Stage` 1:N `InterviewEvaluation` (denormalized)

Unique constraint:

```
unique(interview_id, candidate_participant_id, interviewer_participant_id)
```

This ensures one evaluation per interviewer per candidate per interview. Both candidate and interviewer are referenced via `InterviewParticipant` FK, which is consistent with the existing participant model where `InterviewParticipant` already has unique constraints on `(interview_id, role, job_application_id)` and `(interview_id, role, employee_id)`.

Index candidates:

| Name | Columns | Purpose |
| --- | --- | --- |
| `idx_evaluation_interview` | `interview_id` | Interview-level evaluation lookup. |
| `idx_evaluation_stage` | `stage_id` | Stage-level evaluation lookup. |
| `idx_evaluation_job_application` | `job_application_id` | Application-level evaluation lookup. |
| `idx_evaluation_interviewer_participant` | `interviewer_participant_id` | Interviewer-owned evaluation lookup. |

Important implementation notes:

- `candidateParticipant` and `interviewerParticipant` must belong to the same `Interview`.
- `candidateParticipant.role` must be `CANDIDATE`.
- `interviewerParticipant.role` must be `INTERVIEWER`.
- `jobApplication` and `stage` are set at initialization time from the participant and interview relationships. They are never updated after creation.
- The entity does not reference or inject `StageResult` in any way.

### 6.2 Design Decision: InterviewParticipant FK vs Employee Direct FK

The unique key uses `interviewer_participant_id` (InterviewParticipant FK) rather than `interviewer_employee_id` (Employee direct FK).

Reasons:

1. **Consistency**: Both candidate and interviewer sides use `InterviewParticipant` FK, creating a symmetric design.
2. **Guard simplification**: Evaluations inherit participant status (`ASSIGNED`/`CANCELLED`) checks naturally through the FK relationship.
3. **Replacement tracking**: When an interviewer is replaced (old participant CANCELLED, new participant ASSIGNED), evaluations from each are cleanly separated by participant ID.
4. **Existing model alignment**: `InterviewParticipant` already has `(interview_id, role, employee_id)` unique constraint, so each participant row uniquely identifies an interviewer within an interview.

## 7. Enum Design

| Enum | Values | Purpose |
| --- | --- | --- |
| `EvaluationStatus` | `DRAFT`, `SUBMITTED` | Evaluation lifecycle. DRAFT allows editing. SUBMITTED is immutable unless reopened by admin. |
| `EvaluationGrade` | `F`, `G_MINUS`, `G`, `G_PLUS`, `VG` | Interviewer grade for the candidate. 5-level scale (F lowest, VG highest). |
| `EvaluationRecommendation` | `STRONG_YES`, `YES`, `NEUTRAL`, `NO`, `STRONG_NO` | Interviewer recommendation for the candidate. 5-level scale. |

### 7.1 EvaluationStatus State Transitions

```
(initialize) --> DRAFT
DRAFT --> (save) --> DRAFT
DRAFT --> (submit) --> SUBMITTED
SUBMITTED --> (admin reopen) --> DRAFT
DRAFT --> (submit) --> SUBMITTED
```

- `save`: Updates grade, recommendation, comment while keeping DRAFT status.
- `submit`: Validates required fields (grade, recommendation), transitions to SUBMITTED, sets `submittedAt`.
- `reopen`: Admin command transitions SUBMITTED back to DRAFT, clears `submittedAt`. History is recorded in `ActivityLog`.

## 8. Evaluation Creation Policy

### 8.1 Admin Initialize Command

Evaluation rows are created by an explicit admin command:

```http
POST /admin/interviews/{interviewId}/evaluations/initialize
```

This follows the same pattern as `POST /admin/stages/{stageId}/results/initialize` for StageResult.

**Preconditions:**

- `Interview.status` must be `CONFIRMED`. DRAFT and CANCELLED interviews cannot be initialized.

**Behavior:**

1. Query all `InterviewParticipant` rows where `role = CANDIDATE` and `participantStatus = ASSIGNED`.
2. Query all `InterviewParticipant` rows where `role = INTERVIEWER` and `participantStatus = ASSIGNED`.
3. Compute all `(candidate, interviewer)` combinations.
4. For each combination, check if an `InterviewEvaluation` row already exists.
5. Create new rows only for missing combinations with status `DRAFT`.
6. Skip existing rows (idempotent re-invocation).

**Response:**

```java
public record InterviewEvaluationInitializeResponse(
    int createdCount,
    int alreadyExistedCount,
    int totalCount
) {}
```

**Design rationale:**

- No read side effects: GET requests never create evaluation rows.
- Consistent with existing StageResult initialize pattern.
- Admin controls the timing: evaluation rows can be created after the interview is confirmed but before or after the interview actually takes place.
- Participant replacement after initialization: re-invoking initialize creates rows for newly assigned participants and skips existing ones. Cancelled participants' existing evaluation rows are preserved but cannot receive new saves/submits.

## 9. API List

### 9.1 Admin API

| Method | Path | Purpose | Request | Response |
| --- | --- | --- | --- | --- |
| `POST` | `/admin/interviews/{interviewId}/evaluations/initialize` | Create DRAFT evaluation rows for all ASSIGNED candidate x interviewer combinations. | None. | `InterviewEvaluationInitializeResponse`. |
| `GET` | `/admin/interviews/{interviewId}/evaluations` | Read all evaluations for one interview, grouped by candidate with summary. | None. | `AdminInterviewEvaluationResponse`. |
| `GET` | `/admin/stages/{stageId}/interview-evaluations` | Read all evaluations across all interviews in one stage. | None. | List of `AdminInterviewEvaluationResponse`. |
| `GET` | `/admin/applications/{applicationId}/interview-evaluations` | Read all evaluations for one applicant across all interviews. | None. | List of `AdminApplicationEvaluationResponse`. |
| `POST` | `/admin/interviews/{interviewId}/evaluations/{evaluationId}/reopen` | Reopen a submitted evaluation back to DRAFT. | None. | Updated evaluation detail. |

### 9.2 Interviewer API

| Method | Path | Purpose | Request | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/interviewer/interviews/{interviewId}/evaluations` | List evaluations assigned to the current interviewer for one interview. | None. | `InterviewerEvaluationListResponse`. |
| `GET` | `/interviewer/interviews/{interviewId}/evaluations/{evaluationId}` | Read one evaluation detail for the current interviewer. | None. | `InterviewerEvaluationDetailResponse`. |
| `POST` | `/interviewer/interviews/{interviewId}/evaluations/{evaluationId}` | Save evaluation draft (grade, recommendation, comment). | `InterviewEvaluationSaveRequest`. | Updated evaluation detail. |
| `POST` | `/interviewer/interviews/{interviewId}/evaluations/{evaluationId}/submit` | Submit evaluation (validates required fields, transitions to SUBMITTED). | None or `InterviewEvaluationSaveRequest`. | Updated evaluation detail. |

### 9.3 Controller Placement

| Controller | APIs |
| --- | --- |
| `InterviewEvaluationAdminController` | All admin evaluation APIs (initialize, read at interview/stage/application level, reopen). |
| `InterviewerEvaluationController` | All interviewer evaluation APIs (list, detail, save, submit). |

## 10. Validation and Business Rules

### 10.1 Initialize Guard

| Rule | Description |
| --- | --- |
| Interview must be CONFIRMED | DRAFT and CANCELLED interviews cannot be initialized. |
| Only ASSIGNED participants | CANCELLED candidate or interviewer participants are excluded from initialization. |
| Idempotent | Re-invocation skips existing rows. |

### 10.2 Interviewer Save Guard

| Rule | Description |
| --- | --- |
| Current employee ownership | The logged-in employee must be the interviewer participant's employee. No employeeId in request path/query/body. |
| Interviewer must be ASSIGNED | `interviewerParticipant.participantStatus = ASSIGNED`. |
| Candidate must be ASSIGNED | `candidateParticipant.participantStatus = ASSIGNED`. |
| Interview must be CONFIRMED | DRAFT and CANCELLED interviews block save. |
| Evaluation must be DRAFT | SUBMITTED evaluations cannot be saved. |
| Grade value | If provided, must be a valid `EvaluationGrade` enum value. |
| Comment length | If provided, max 2000 characters. |

### 10.3 Interviewer Submit Guard

All save guards apply, plus:

| Rule | Description |
| --- | --- |
| Grade required | `grade` must not be null. |
| Recommendation required | `recommendation` must not be null. |
| Status transition | DRAFT -> SUBMITTED. `submittedAt` set to current timestamp. |

### 10.4 Admin Reopen Guard

| Rule | Description |
| --- | --- |
| Evaluation must be SUBMITTED | Only SUBMITTED evaluations can be reopened. |
| Status transition | SUBMITTED -> DRAFT. `submittedAt` cleared. |
| History | Reopen action recorded in `ActivityLog`. No dedicated reopen fields on the entity. |

### 10.5 Cancelled Interview/Participant Handling

| Scenario | Existing Evaluation | New Save/Submit |
| --- | --- | --- |
| Interview CANCELLED, evaluation DRAFT | Data preserved | Blocked |
| Interview CANCELLED, evaluation SUBMITTED | Data preserved | N/A (already submitted) |
| Interviewer participant CANCELLED, evaluation DRAFT | Data preserved | Blocked |
| Candidate participant CANCELLED, evaluation DRAFT | Data preserved | Blocked |
| New participant after replacement + re-initialize | New row created | Allowed |

Core principle: **write operations blocked, existing data preserved**. Cancelled participants' evaluations are never deleted. Admin can view them for audit purposes.

### 10.6 Interviewer Information Isolation

Interviewers see only their own evaluations. They cannot see:

- Other interviewers' scores, recommendations, or comments.
- Other interviewers' submission status.
- Evaluation summary or aggregations.

This ensures evaluation independence and prevents bias.

## 11. Response Design

### 11.1 Admin Interview-Level Evaluation Response

Grouped by candidate with per-candidate summary:

```json
{
  "interviewId": 1,
  "interviewGroupName": "1조",
  "candidates": [
    {
      "candidateParticipantId": 10,
      "applicantName": "홍길동",
      "applicationId": 100,
      "positionName": "IT개발",
      "summary": {
        "submittedCount": 2,
        "totalEvaluatorCount": 3,
        "gradeDistribution": {
          "vg": 1,
          "gPlus": 1,
          "g": 0,
          "gMinus": 0,
          "f": 0
        },
        "recommendationDistribution": {
          "strongYes": 1,
          "yes": 1,
          "neutral": 0,
          "no": 0,
          "strongNo": 0
        }
      },
      "evaluations": [
        {
          "evaluationId": 1,
          "interviewerName": "김면접",
          "status": "SUBMITTED",
          "grade": "VG",
          "recommendation": "STRONG_YES",
          "comment": "기술 역량 우수",
          "submittedAt": "2026-05-27T14:30:00"
        }
      ]
    }
  ]
}
```

Summary aggregation rules:

- `submittedCount`: count of evaluations with status `SUBMITTED`.
- `totalEvaluatorCount`: total evaluation rows for this candidate (DRAFT + SUBMITTED).
- `gradeDistribution`: count per grade value from SUBMITTED evaluations only.
- `recommendationDistribution`: count per recommendation value from SUBMITTED evaluations only.
- DRAFT evaluations are included in the `evaluations` list but excluded from summary calculations.

### 11.2 Admin Stage-Level Evaluation Response

Structure: `interviews[] -> candidates[] -> evaluations[]`

Wraps the interview-level response with an outer interview grouping for all interviews in the stage.

### 11.3 Admin Application-Level Evaluation Response

Structure: `interviews[] -> evaluations[]`

Since the candidate is fixed (one application), the response omits the candidate grouping and shows evaluations directly under each interview.

### 11.4 Interviewer Evaluation List Response

```json
{
  "interviewId": 1,
  "interviewGroupName": "1조",
  "interviewStatus": "CONFIRMED",
  "startDateTime": "2026-06-01T10:00:00",
  "endDateTime": "2026-06-01T12:00:00",
  "evaluations": [
    {
      "evaluationId": 1,
      "candidateName": "홍길동",
      "applicationId": 100,
      "positionName": "IT개발",
      "status": "DRAFT",
      "grade": null,
      "recommendation": null,
      "comment": null,
      "submittedAt": null
    }
  ]
}
```

- Flat list of the interviewer's own evaluations only.
- Includes interview context (time, status, group name).
- No other interviewer information.
- No summary or aggregation.

### 11.5 GradeDistribution DTO

```java
public record GradeDistribution(
    int vg,
    int gPlus,
    int g,
    int gMinus,
    int f
) {}
```

Type-safe DTO with all 5 fields always present (zero for no submissions in that category). Order follows grade level from highest to lowest.

### 11.6 RecommendationDistribution DTO

```java
public record RecommendationDistribution(
    int strongYes,
    int yes,
    int neutral,
    int no,
    int strongNo
) {}
```

Type-safe DTO with all 5 fields always present (zero for no submissions in that category).

## 12. StageResult Boundary Policy

This is the most critical design decision in Phase 06.

### 12.1 Non-Mutation Guarantee

- `InterviewEvaluation` is evaluation evidence, not the final stage result.
- `StageResult` remains the final decision record.
- Interviewer `submit` does NOT create, update, announce, correct, or publish `StageResult`.
- `InterviewEvaluation` entity, service, and controller do NOT inject or call `StageResultRepository` or `StageResultService`.
- No automatic or semi-automatic StageResult reflection command is included in Phase 06.

### 12.2 Administrator Workflow

The intended workflow for administrators:

1. Initialize evaluation rows for a confirmed interview.
2. Wait for interviewers to save and submit evaluations.
3. View evaluation summaries via admin read APIs.
4. Use existing `POST /admin/stages/{stageId}/results/{resultId}` or `POST /admin/stages/{stageId}/results/bulk` to manually set StageResult based on evaluation data.

### 12.3 Future Reflection Command

A dedicated StageResult reflection command (e.g., `POST /admin/stages/{stageId}/results/reflect-interview-evaluations`) is explicitly deferred to a future phase. Reasons:

- Phase 06 prioritizes evaluation capture and read stability.
- Reflection logic requires scoring criteria (threshold scores, recommendation weights) that are not yet defined.
- Existing StageResult update/bulk update APIs are sufficient for manual workflow.

## 13. Slice Plan

### Phase 06a - InterviewEvaluation Domain

- `InterviewEvaluation` entity.
- `EvaluationStatus` enum.
- `EvaluationGrade` enum.
- `EvaluationRecommendation` enum.
- `InterviewEvaluationRepository`.
- Unique constraint: `(interview_id, candidate_participant_id, interviewer_participant_id)`.
- Entity validation: grade enum value, comment max 2000, submit requires grade + recommendation.
- Status transition: DRAFT -> SUBMITTED, SUBMITTED -> DRAFT (reopen).
- Targeted entity and repository tests.

### Phase 06b - Admin Initialize + Interviewer Evaluation Write

- `POST /admin/interviews/{interviewId}/evaluations/initialize` — admin initialize command.
- `GET /interviewer/interviews/{interviewId}/evaluations` — interviewer evaluation list.
- `GET /interviewer/interviews/{interviewId}/evaluations/{evaluationId}` — interviewer evaluation detail.
- `POST /interviewer/interviews/{interviewId}/evaluations/{evaluationId}` — draft save.
- `POST /interviewer/interviews/{interviewId}/evaluations/{evaluationId}/submit` — submit.
- Employee ownership and assignment guard.
- Interview CONFIRMED guard.
- Participant ASSIGNED guard.
- SUBMITTED immutability guard.
- Interviewer information isolation (own evaluations only).
- Targeted service and controller tests.

### Phase 06c - Admin Evaluation Read

- `GET /admin/interviews/{interviewId}/evaluations` — interview-level read.
- `GET /admin/stages/{stageId}/interview-evaluations` — stage-level read.
- `GET /admin/applications/{applicationId}/interview-evaluations` — application-level read.
- Candidate-grouped response with per-candidate summary.
- `GradeDistribution` DTO.
- `RecommendationDistribution` DTO.
- Summary aggregation from SUBMITTED evaluations only.
- Targeted service and controller tests.

### Phase 06d - Reopen + StageResult Boundary

- `POST /admin/interviews/{interviewId}/evaluations/{evaluationId}/reopen` — admin reopen command.
- SUBMITTED -> DRAFT transition.
- ActivityLog history recording.
- StageResult non-mutation guarantee documented and enforced.
- StageResult reflect command explicitly deferred to future phase.
- Targeted tests.

### Phase 06e - Stabilization / Test Hardening

- N candidates x M interviewers matrix regression.
- Cancelled interview guard regression.
- Cancelled participant guard regression.
- DRAFT/CONFIRMED/CANCELLED visibility regression.
- SUBMITTED evaluation immutability regression.
- Reopen -> re-submit cycle regression.
- Non-assigned interviewer forbidden regression.
- Non-assigned candidate forbidden regression.
- StageResult non-mutation regression.

## 14. Open Questions

| # | Question | Current Position | Resolution |
| --- | --- | --- | --- |
| 1 | Should `submittedAt` be cleared or preserved on reopen? | Clear on reopen. The evaluation is back to draft state. | Decided: clear. |
| 2 | Should evaluation rows be physically deleted when an interview is cancelled? | No. Data preserved for audit. Write operations blocked. | Decided: preserve. |
| 3 | Should admin memo be added to evaluation rows? | Deferred to future phase. | Decided: exclude from Phase 06. |
| 4 | Should a StageResult reflection command be included in Phase 06? | No. Deferred to future phase. | Decided: exclude. |
| 5 | What is the maximum number of interviewers per interview? | No hard limit in the current design. Practical limit expected to be 5~10. | Open. |
| 6 | Should evaluation data be versioned for reopen/re-submit tracking? | No. Status transition only. ActivityLog for audit trail. | Decided: no versioning. |
| 7 | Should the submit endpoint accept a request body for final edits? | To be decided during implementation. Both options are viable. | Open for 06b. |

## 15. Decision Log

| # | Decision | Rationale | Date |
| --- | --- | --- | --- |
| 1 | Use `InterviewParticipant` FK for both candidate and interviewer references. | Symmetric design, consistent with existing model, simplifies guard checks, cleanly separates replacement scenarios. | 2026-05-27 |
| 2 | Admin explicit initialize command, not lazy creation. | No read side effects. Consistent with StageResult initialize pattern. Admin controls timing. | 2026-05-27 |
| 3 | Initialize is separate from interview confirm. | Different concerns. Confirm validates schedule completeness. Initialize creates evaluation rows. | 2026-05-27 |
| 4 | Grade type is `EvaluationGrade` enum with 5 levels: `F`, `G_MINUS`, `G`, `G_PLUS`, `VG`. | Grade-based evaluation is more intuitive for interviewers than numeric scores. Enum approach allows grade name changes with minimal code impact (enum file only + DB migration). Summary uses grade distribution instead of arithmetic average. | 2026-05-28 |
| 5 | Recommendation is 5-level enum: STRONG_YES, YES, NEUTRAL, NO, STRONG_NO. | Captures nuanced interviewer opinion. Useful for distribution analysis. HOLD is a StageResult concept, not an evaluation concept. | 2026-05-27 |
| 6 | Comment is optional on both DRAFT and SUBMIT. | Required score and recommendation provide quantitative data. Forcing comment produces low-quality filler text. | 2026-05-27 |
| 7 | Admin memo excluded from Phase 06. | Phase 06 focuses on evaluation capture stability. Admin can use StageResult comment for notes. Low-frequency scenario. | 2026-05-27 |
| 8 | Reopen included in Phase 06 as 06d slice. | Practical need for correction. Simple status transition without dedicated entity fields. ActivityLog handles audit. | 2026-05-27 |
| 9 | No reopen-specific fields on entity. Status transition only. ActivityLog for history. | Minimizes entity complexity. Reopen is infrequent. Last-reopen metadata is low value. | 2026-05-27 |
| 10 | EvaluationStatus has only DRAFT and SUBMITTED. No CANCELLED status. | Cancellation is handled by Interview and InterviewParticipant status checks, not by evaluation status. Fewer states = simpler transition rules. | 2026-05-27 |
| 11 | Summary uses `GradeDistribution` instead of averageScore/scoreSum. | Grade-based evaluation cannot produce arithmetic average. Distribution (count per grade) is the natural aggregation for enum-based grades. | 2026-05-28 |
| 12 | `GradeDistribution` and `RecommendationDistribution` are typed DTOs, not Maps. | Type-safe, frontend-friendly, always includes all 5 fields. | 2026-05-27 |
| 13 | Denormalized `jobApplication` and `stage` FKs on entity. | Enables direct index queries for stage-level and application-level admin reads without multi-join. Consistent with StageResult pattern. Values set at initialization and never updated. | 2026-05-27 |
| 14 | StageResult reflect command excluded from Phase 06. | Phase 06 prioritizes evaluation capture stability. Reflection requires scoring criteria not yet defined. Existing manual update/bulk update workflow is sufficient. | 2026-05-27 |
| 15 | Cancelled interview/participant: write blocked, data preserved. | Evaluation data has audit value. Deletion would lose evidence. Guard checks are sufficient without additional status values. | 2026-05-27 |
| 16 | Interviewer cannot see other interviewers' evaluations. | Ensures evaluation independence and prevents bias. | 2026-05-27 |
| 17 | Admin read at stage and application levels includes full evaluation detail, not just summary. | Avoids drill-down API calls. Data volume is manageable (practical N*M is under a few hundred). | 2026-05-27 |

## 16. Next Phase Considerations

- Phase 06a implementation: start with entity, enums, repository, and targeted tests.
- Phase 07 candidate: Excel/PDF/statistics, which may include evaluation data export.
- Future candidate: StageResult reflection command with configurable scoring criteria.
- Future candidate: Admin memo on evaluation rows.
- Future candidate: Evaluation template management with per-item detailed grading.
- Future candidate: Grade name changes (enum value rename + DB migration only).
