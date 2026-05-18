# Phase 03d-0 StageResult Domain Design

## Phase Summary

Phase 03d-0 is a design-only phase for the `StageResult` domain.

The goal is to define how recruitment stage results are stored and managed after the `Stage`, `JobApplication`, application detail sections, and question/answer domains have become available.

No Java code, Entity, Repository, Service, Controller, DTO, Test, DB schema, SecurityConfig, or existing API is changed in this phase.

Start worktree note:

- At the start of this phase, the worktree already contained uncommitted Phase 03c-9-4 code and documentation changes.
- This phase must add or update only `docs/codex/**` files.
- Existing uncommitted source changes are not part of Phase 03d-0 and must not be reverted by this document work.

## Current Context

- Phase 02 implemented the `Stage` domain under `JobPosting`.
- Phase 02 explicitly deferred `StageResult` because the `Application` root did not exist yet.
- Phase 03a implemented the `JobApplication` root and applicant application commands.
- Phase 03c implemented application detail sections and the question/answer domain.
- Phase 03c-9-4 added admin answer lazy read, closing the main application-content read axis before StageResult.

StageResult can now be designed against stable roots:

- `Stage`: one recruitment stage under a `JobPosting`.
- `JobApplication`: one applicant's application to a `JobPosting`.
- `StageResult`: one result record for a specific `Stage + JobApplication`.

## StageResult Role

`StageResult` is the result record connecting one `Stage` and one `JobApplication`.

Recommended interpretation:

- One `Stage` can have many `StageResult` rows.
- One `JobApplication` can have many `StageResult` rows, one per stage.
- `StageResult` is the N:M connection record between `Stage` and `JobApplication`.
- It is not embedded into either aggregate as a collection in the initial implementation.

Primary responsibility:

- Store the applicant's result for a stage.
- Support admin stage-result list screens.
- Support later announcement, notification, statistics, and interview/evaluation integration.

Non-responsibility in the initial design:

- Full correction history.
- Interview evaluator score aggregation.
- Applicant-facing result announcement page.
- Message sending.
- Authorization and read audit logging.

## Recommended Domain Model

### Entity Candidate

Table candidate: `stage_result`

| Field | Type candidate | Required | Description |
|---|---|---:|---|
| `id` | `Long` | Yes | Primary key |
| `stage` | `Stage` | Yes | N:1 target stage |
| `jobApplication` | `JobApplication` | Yes | N:1 target application |
| `resultStatus` | `StageResultStatus` | Yes | Stage result status |
| `score` | `BigDecimal` or `Integer` | No | Manual score in the first implementation |
| `comment` | `String` | No | Admin note or result memo |
| `decidedAt` | `LocalDateTime` | No | Time when result was decided |
| `decidedBy` | `Employee` or admin user reference candidate | No | Deciding admin, deferred until auth/audit policy is finalized |
| `createdAt` / `updatedAt` | `BaseEntity` | Yes | Common audit timestamps |

Recommended first implementation choices:

- Use `@ManyToOne(fetch = FetchType.LAZY)` from `StageResult` to `Stage`.
- Use `@ManyToOne(fetch = FetchType.LAZY)` from `StageResult` to `JobApplication`.
- Store `resultStatus` with `EnumType.STRING`.
- Keep `score` nullable because not every stage is scored.
- Keep `comment` nullable and length-limited.
- Keep `decidedBy` nullable or as a later `Employee` relation candidate. The exact authority source should be decided in the security/audit phase.

### Relationship Policy

Recommended relationship:

- `StageResult -> Stage`: N:1 unidirectional.
- `StageResult -> JobApplication`: N:1 unidirectional.
- `Stage` has no `List<StageResult>`.
- `JobApplication` has no `List<StageResult>`.
- No cascade.
- No orphanRemoval.

Reason:

- StageResult is operational result data and may later connect to announcements, messages, interviews, and evaluations.
- Deleting or cascading it through Stage or JobApplication would be risky.
- Query services can retrieve results by stage id or application id without aggregate collections.

### Unique Constraint Candidate

Recommended unique candidate:

```text
unique(stage_id, job_application_id)
```

Why it is needed:

- A single application must have at most one result in a single stage.
- Duplicate rows would make pass/fail announcement ambiguous.
- Statistics, message recipients, applicant-facing result lookup, and exports require one result per stage/application pair.

Exception handling:

- Re-evaluation or correction should not create duplicate active result rows.
- Correction history should be represented by future `StageResultHistory`, audit log, or correction command metadata.
- If business later requires multiple evaluator rows, those should belong to `Evaluation` or `InterviewEvaluation`, not duplicate `StageResult`.

## StageResultStatus

| Status | Meaning | Notes |
|---|---|---|
| `PENDING` | Result not decided yet | Default status after initialize |
| `PASSED` | Passed this stage | Can become candidate for next stage |
| `FAILED` | Failed this stage | Not eligible for next stage by default |
| `ABSENT` | Did not attend the stage | Useful for interview or test stages |
| `WITHDRAWN` | Excluded due to applicant withdrawal | Used when withdrawal happens after result row exists |
| `HOLD` | Pending review or exception | Temporary admin hold |

Recommended default:

- New initialized results start as `PENDING`.
- `decidedAt` remains null while `PENDING`.
- `decidedAt` is set when status changes to `PASSED`, `FAILED`, `ABSENT`, `WITHDRAWN`, or `HOLD` if business treats hold as a decision event.

## State Policy

### StageResult Creation Timing

| Option | Description | Pros | Cons | Recommendation |
|---|---|---|---|---|
| Stage `IN_PROGRESS` only | Results can be created only after a stage starts | Strict lifecycle | Admin cannot prepare lists early | Not first choice |
| Stage `READY` or later | Results can be initialized before start | Better admin preparation | Needs clear modification rules | Recommended |
| Admin anytime | Results can be prepared regardless of stage state | Flexible | Easy to create stale data | Too loose |

Recommended policy:

- Allow initialize in `READY` and `IN_PROGRESS`.
- Disallow initialize in `RESULT_ANNOUNCED` and `CLOSED`.
- Initialize creates only missing `PENDING` rows.
- Existing result rows are preserved.

### StageResult Modification Timing

| Stage status | Recommended result behavior |
|---|---|
| `READY` | Initialize allowed. Final result editing should be limited or allowed only for preparation if the business needs it. |
| `IN_PROGRESS` | Result input and update allowed. |
| `RESULT_ANNOUNCED` | Read allowed. General updates blocked. Correction command candidate. |
| `CLOSED` | Read allowed. Updates blocked. |

Recommended first implementation:

- Allow result update in `IN_PROGRESS`.
- Consider allowing update in `READY` only if admins need pre-stage document screening preparation.
- Block general update in `RESULT_ANNOUNCED` and `CLOSED`.
- Add a separate correction command later if post-announcement correction is required.

### JobApplication Status Policy

| JobApplication status | StageResult policy |
|---|---|
| `DRAFT` | Excluded from StageResult target. |
| `SUBMITTED` | Included in initialize target. |
| `WITHDRAWN` before initialize | Excluded from initialize target. |
| `WITHDRAWN` after result exists | Preserve existing result row. If not announced and still pending, `WITHDRAWN` status candidate. |

Recommended policy:

- StageResult is based on submitted applications only.
- A draft application cannot participate in a recruitment stage.
- A withdrawn application should not receive newly initialized result rows.
- Already-created rows should be preserved for audit/statistics and may be transitioned to `WITHDRAWN`.

## Result Creation Strategy

### Option A: Individual Create/Update

Admins create or update one result at a time.

Pros:

- Simple endpoint shape.
- Good for manual exceptional cases.

Cons:

- Admin stage list needs rows for every applicant.
- Missing rows can produce inconsistent lists and exports.
- Repeated manual create is tedious.

### Option B: Explicit Initialize Command

Admin runs an initialize command for a stage. The system creates missing `PENDING` rows for submitted applications in the posting.

Pros:

- Data consistency is strong.
- Admin screens can show a complete list.
- Missing rows are easy to detect and prevent.
- Works well with later bulk result upload/update.

Cons:

- Requires one extra command.
- Must define behavior for applications submitted after initialize.

Recommendation: choose Option B for the first implementation.

Late submissions can be handled by rerunning initialize, which creates only missing rows.

### Option C: Lazy Upsert

Rows are created when a result is first edited or viewed.

Pros:

- Fewer explicit commands.
- Flexible for small usage.

Cons:

- Read paths can accidentally become write paths.
- Harder to audit.
- List completeness is less obvious.

Recommendation: defer. Avoid read-time writes in the first StageResult implementation.

## API Candidate

All StageResult command APIs should avoid PUT and HTTP DELETE.

### Priority 1

| Method | Path | Purpose | Request candidate | Response candidate |
|---|---|---|---|---|
| GET | `/admin/stages/{stageId}/results` | List results for one stage | query: optional status, page/size candidate | `PageResponse<AdminStageResultResponse>` or list |
| POST | `/admin/stages/{stageId}/results/initialize` | Create missing `PENDING` rows for submitted applications | optional empty body | initialize summary + result list candidate |

`AdminStageResultResponse` field candidate:

- `stageResultId`
- `applicationId`
- `applicantName`
- `jobPositionName`
- `applicationStatus`
- `resultStatus`
- `score`
- `comment`
- `submittedAt`
- `decidedAt`

Initialize behavior candidate:

- Validate stage exists.
- Validate stage is `READY` or `IN_PROGRESS`.
- Find submitted applications for the stage's job posting.
- Exclude withdrawn and draft applications.
- Create only missing `(stage, jobApplication)` rows.
- Return created count, existing count, skipped count, and current result rows.

### Priority 2

| Method | Path | Purpose | Request candidate | Response candidate |
|---|---|---|---|---|
| POST | `/admin/stages/{stageId}/results/{resultId}` | Update one result | `resultStatus`, `score`, `comment` | `AdminStageResultResponse` |
| POST | `/admin/stages/{stageId}/results/bulk` | Bulk update results | list of result update items | update summary + result list |

Single update validation candidate:

- Result must belong to the requested stage.
- Stage must be editable by status policy.
- `resultStatus` must be valid.
- `score` can be null if the stage does not use scoring.
- `decidedAt` is set when a non-pending decision is recorded.

Bulk update validation candidate:

- All result ids must belong to the stage.
- Duplicate result ids are rejected.
- All updates are applied in one transaction.
- Partial success is not recommended for the first implementation.

### Priority 3

| Method | Path | Purpose | Request candidate | Response candidate |
|---|---|---|---|---|
| GET | `/admin/applications/{applicationId}/stage-results` | Read one application's stage result timeline | none | list of stage result rows by stage order |

This API is useful for admin application detail screens but can wait until stage list/initialize/update is stable.

## Admin Screen Flow

Recommended admin flow:

1. Admin opens job posting detail.
2. Admin opens stage list.
3. Admin selects one stage.
4. Admin opens stage result list with `GET /admin/stages/{stageId}/results`.
5. If rows are missing, admin runs `POST /admin/stages/{stageId}/results/initialize`.
6. Admin enters or uploads results.
7. Admin announces stage result through the existing/future stage announce command.
8. After announcement, result correction requires a separate correction policy.

The stage result list should support scanning and repeated work, so it should be compact and operational rather than aggregate-heavy.

## Stage Status And StageResult Status

| Stage.status | StageResult interaction |
|---|---|
| `READY` | Results can be initialized. Result final editing is optional by policy. Applicant exposure is blocked. |
| `IN_PROGRESS` | Results can be entered or modified. Applicant exposure is blocked. |
| `RESULT_ANNOUNCED` | Results can be read. Applicant exposure candidate becomes allowed through a separate public/applicant API. General admin updates are blocked. |
| `CLOSED` | Results can be read for audit/history. Updates are blocked. |

Command interaction candidates:

- `Stage.start`: may require stage to have no announced results.
- `Stage.announce`: should fail if required result rows remain `PENDING`.
- `Stage.close`: should be allowed after announcement and after operational checks.
- Result update after `RESULT_ANNOUNCED`: should require correction command and audit reason.

## Related Future Domains

### Interview / InterviewGroup / Interviewer

StageResult should not directly model interview scheduling.

Future relationship candidate:

- `Interview` belongs to a stage.
- Interview participants or evaluations can later feed into a StageResult.
- `StageResult` remains the final stage-level decision record.

### Evaluation

Initial `StageResult.score` is a manual score candidate.

Future options:

- Keep `score` as manual override score.
- Replace or fill `score` from evaluation aggregate result.
- Add fields such as `scoreSource` or separate evaluation summary later.

Recommended initial stance:

- Use nullable manual `score`.
- Do not block StageResult implementation on evaluation aggregation.
- Document that interview/evaluation score aggregation can later update or inform StageResult through a controlled command.

### Message / Notification

StageResult is a future recipient source for pass/fail messages.

Deferred policy:

- Message sending should use announced/finalized results only.
- Message logs should not mutate StageResult.
- Re-sending or correction notification should be handled through message history.

### Applicant Result Announcement Page

Applicant-facing result read is not part of the first StageResult implementation.

Deferred policy:

- Applicant result exposure is blocked before `Stage.RESULT_ANNOUNCED`.
- The applicant should only see their own result.
- Pass/fail wording and announcement templates may need a separate message/template policy.

## Security, Authorization, And Audit TODO

The following must be resolved before production use:

- Who can initialize results.
- Who can update individual results.
- Who can bulk update results.
- Whether interviewers can view or update stage results.
- Applicant result exposure before/after announcement.
- Result correction after announcement.
- Change history and audit log for every result change.
- `decidedBy` source and retention.
- Whether comments contain personal or sensitive information.
- Export and statistics masking rules.

## Implementation Roadmap

| Phase | Goal | Scope | Deferred |
|---|---|---|---|
| Phase 03d-1 | StageResult entity + initialize/list admin API | Entity, repository, result status enum, initialize command, stage result list | update/bulk/correction/applicant read |
| Phase 03d-2 | StageResult update commands | single update, bulk update, validation, decidedAt/decidedBy policy candidate | correction history |
| Phase 03d-3 | Application detail stage result lazy read | `GET /admin/applications/{applicationId}/stage-results` | applicant-facing result read |
| Phase 03d-4 | Announcement integration | Stage announce checks, pending result guard, applicant exposure policy | message sending |
| Phase 03d-5 | Security/audit hardening | permission rules, change history, correction command | interview evaluation aggregation |

Recommended next implementation phase:

- Phase 03d-1: StageResult Entity + initialize/list admin API. (Completed)
- Next recommended implementation: Phase 03d-2 StageResult single/bulk update commands and Stage announce pending-result guard.

## Phase 03d-1 Implementation Note

Phase 03d-1 implemented the first StageResult vertical slice described in this design.

Implemented:

- `StageResultStatus` enum with `PENDING`, `PASSED`, `FAILED`, `ABSENT`, `WITHDRAWN`, `HOLD`.
- `StageResult` entity with LAZY `Stage` and `JobApplication` references.
- `stage_id + job_application_id` unique constraint and per-column indexes.
- `POST /admin/stages/{stageId}/results/initialize`.
- `GET /admin/stages/{stageId}/results`.
- initialize policy based on `SUBMITTED` applications only.
- `READY` and `IN_PROGRESS` initialize allowed.
- `RESULT_ANNOUNCED` and `CLOSED` initialize rejected.

Still deferred:

- result update and bulk update
- correction history
- applicant-facing result read
- admin application stage-result timeline
- Stage announce integration that blocks announcement while `PENDING` rows remain
- security, authorization, and audit logging

## Deferred Items

- Java code implementation.
- DB schema migration.
- StageResult correction history.
- Applicant-facing result read.
- Message/notification integration.
- Interview/evaluation aggregation.
- Result export/download.
- Fine-grained security and audit logging.
- Stage command behavior changes.

## Verification Notes

- This phase is documentation-only.
- No Java, test, Gradle, YAML, static resource, or DB schema file should be changed by this phase.
- Tests are not required for this phase unless code changes are detected.
- The paired HTML report must remain self-contained and must not use external CSS, JavaScript, or CDN resources.
