# Phase 04 - Interview Scheduling Design

## 1. Phase Summary

- Phase name: Phase 04 - Interview Scheduling
- Work type: documentation-only design phase
- Date: 2026-05-26
- Purpose: define the backend design for interview schedule, group, candidate assignment, and interviewer assignment.
- Status: design completed, Java/source/test implementation not started.

This phase defines the minimum domain and API shape for managing who attends which interview, when, where, and in which interview group. It does not implement interview scoring, result announcement, message delivery, Excel import/export, calendar integration, or frontend behavior.

## 2. Purpose

The project already has `JobPosting`, `Stage`, `JobApplication`, and `StageResult` implementation slices. Phase 04 introduces the scheduling layer that connects a job posting and an interview stage to candidate and interviewer assignments.

The design keeps responsibilities separated:

- `Interview` owns interview schedule, group, place, method, and status.
- `InterviewParticipant` owns candidate/interviewer assignment rows.
- `StageResult` remains responsible only for pass/fail/pending result decisions, announcement, and correction history.
- Interview Scheduling never creates, updates, announces, or corrects `StageResult`.
- Interview Scheduling must not become an alternative result announcement channel. Admin confirmation may read `StageResult` only to verify candidate eligibility; applicant schedule reads do not expose or mutate `StageResult`.

## 3. Scope

Implemented by this design document:

- `Interview` entity candidate design.
- `InterviewParticipant` entity candidate design.
- supporting enum candidates:
  - `InterviewMethod`
  - `InterviewStatus`
  - `InterviewParticipantRole`
  - `InterviewParticipantStatus`
- admin API draft for interview schedule CRUD and participant replacement.
- applicant API draft for viewing the current applicant's own confirmed/cancelled interview schedules.
- interviewer API draft for viewing schedules assigned to the current employee.
- validation and business rules for confirmation, candidate eligibility, applicant visibility, stage status, participant lifecycle, time range, ownership, duplicate assignment, and collision checks.
- index and unique constraint candidates for later implementation.
- phase split recommendation for the later implementation slices.

## 4. Out Of Scope

The following items are explicitly not part of Phase 04:

- Java source implementation.
- test source implementation.
- database migration files or DDL generation.
- `InterviewEvaluation` implementation.
- interview score, grade, memo submission, or final evaluation.
- `StageResult` creation, update, announcement, correction, or publication.
- SMS, email, Alimtalk, or notification delivery.
- Excel upload/download.
- PDF generation.
- calendar integration.
- frontend or static resource generation.
- operating DB schema-management phase.

## 5. Changed Files

| File | Change |
| --- | --- |
| `docs/codex/design/phase-04-interview-scheduling-design.md` | Added Phase 04 Interview Scheduling design source of truth. |
| `docs/codex/reports/phase-04-interview-scheduling-design.html` | Added self-contained human-readable design report. |
| `docs/codex/06-implementation-roadmap.md` | Repositioned Phase 04 as Interview Scheduling and adjusted later phase order. |
| `docs/codex/07-implementation-history.md` | Added Phase 04 design history and removed DB-operations phase recommendation wording. |
| `docs/codex/01-project-context.md` | Removed DB operations from the remaining major business phase list. |
| `docs/codex/reports/current-implementation-status.html` | Updated current status report to show Phase 04 as Interview Scheduling. |
| `docs/codex/implementation/phase-03i-5-2-attachment-required-policy.md` | Removed the old next-phase DB-operations recommendation while keeping manual DDL facts. |
| `docs/codex/implementation/phase-03k-2-application-form-required-policy.md` | Removed the old next-phase DB-operations recommendation while keeping manual DDL facts. |
| `docs/codex/reports/phase-03i-5-2-attachment-required-policy.html` | Aligned human report next-phase recommendation with the markdown. |
| `docs/codex/reports/phase-03k-2-application-form-required-policy.html` | Aligned human report next-phase recommendation with the markdown. |
| `docs/codex/reports/phase-03i-5-attachment-required-policy-design.html` | Kept the manual DDL note factual without a DB-operations phase recommendation. |
| `instruction.md` | Review source read and reflected into the Phase 04 design. |

## 6. Domain Model Design

### 6.1 Interview

Package candidate: `com.shinyoung.recruit.domain.entity`

Class type: Entity

Responsibility:

- Represents one interview schedule/group for a job posting and interview stage.
- Stores when and where the interview occurs.
- Stores the lifecycle status of the schedule itself.

Candidate fields:

| Field | Type | Rule |
| --- | --- | --- |
| `id` | `Long` | PK with identity generation. |
| `jobPosting` | `JobPosting` | Required, lazy many-to-one. |
| `stage` | `Stage` | Required, lazy many-to-one. Must belong to the same `JobPosting`. |
| `groupName` | `String` | Required. For a single interview group, the admin client may send a default value such as `1조`. |
| `startDateTime` | `LocalDateTime` | Required. |
| `endDateTime` | `LocalDateTime` | Required and must be after `startDateTime`. |
| `method` | `InterviewMethod` | Required enum: `IN_PERSON`, `ONLINE`, `HYBRID`, `OTHER`. |
| `locationName` | `String` | Required for `IN_PERSON` or `HYBRID`. |
| `roomName` | `String` | Optional room or meeting space name. |
| `onlineMeetingUrl` | `String` | Required for `ONLINE` or `HYBRID`; hidden from unrelated applicants/interviewers. |
| `memo` | `String` | Admin internal memo. Not exposed to applicants. |
| `status` | `InterviewStatus` | Required enum: `DRAFT`, `CONFIRMED`, `CANCELLED`. |

Relationship summary:

- `JobPosting` 1:N `Interview`
- `Stage` 1:N `Interview`
- `Interview` 1:N `InterviewParticipant`

Important implementation notes:

- `Interview.stage.stageType` must be one of `FIRST_INTERVIEW`, `SECOND_INTERVIEW`, or `FINAL_INTERVIEW`.
- `DOCUMENT` stages and other non-interview stages must be rejected.
- As of the Phase 04a implementation check on 2026-05-26, the current source enum `StageType` contains `DOCUMENT`, `FIRST_INTERVIEW`, `SECOND_INTERVIEW`, `FINAL_INTERVIEW`, and `ETC`. Later service validation should allow interview scheduling only for `FIRST_INTERVIEW`, `SECOND_INTERVIEW`, and `FINAL_INTERVIEW`.
- `DRAFT` schedules are admin-only.
- `CONFIRMED` schedules are visible only to assigned candidates/interviewers.
- `CANCELLED` schedules are still visible to assigned candidates/interviewers so cancellation state can be communicated by later UI/message phases.

### 6.2 InterviewParticipant

Package candidate: `com.shinyoung.recruit.domain.entity`

Class type: Entity

Responsibility:

- Represents an assignment row in an interview schedule.
- Uses one table for both candidate and interviewer participants.
- Keeps candidate assignment and interviewer assignment mutually exclusive.

Candidate fields:

| Field | Type | Rule |
| --- | --- | --- |
| `id` | `Long` | PK with identity generation. |
| `interview` | `Interview` | Required, lazy many-to-one. |
| `role` | `InterviewParticipantRole` | Required enum: `CANDIDATE`, `INTERVIEWER`. |
| `jobApplication` | `JobApplication` | Required only when role is `CANDIDATE`; null for interviewer rows. |
| `employee` | `Employee` | Required only when role is `INTERVIEWER`; null for candidate rows. |
| `participantStatus` | `InterviewParticipantStatus` | Required enum: `ASSIGNED`, `CANCELLED`. |
| `sortOrder` | `Integer` | Optional display order inside the group. |

Relationship summary:

- `Interview` 1:N `InterviewParticipant`
- `JobApplication` 1:N candidate `InterviewParticipant`
- `Employee` 1:N interviewer `InterviewParticipant`

Important implementation notes:

- Candidate rows must reference a `JobApplication` from the same `JobPosting` as the `Interview`.
- Interviewer rows must reference an `Employee`.
- A candidate participant must not have `employee`.
- An interviewer participant must not have `jobApplication`.
- A participant row should not directly reference `User` for the first slice because candidates need `JobApplication` context and interviewers use existing `Employee`.
- For the first implementation, participant replacement is allowed only while `Interview.status` is `DRAFT`.
- DRAFT participant replacement may delete and recreate participant rows. No participant history is promised for DRAFT schedules.
- Cancelling a confirmed interview changes only `Interview.status` to `CANCELLED`.
- Existing participant rows remain `ASSIGNED` after schedule cancellation so assigned applicants/interviewers can still see the cancelled schedule.
- `InterviewParticipantStatus.CANCELLED` is reserved for a later partial participant cancellation, individual no-show, or amendment phase unless that behavior is explicitly implemented.

## 7. Enum Design

| Enum | Values | Purpose |
| --- | --- | --- |
| `InterviewMethod` | `IN_PERSON`, `ONLINE`, `HYBRID`, `OTHER` | Interview delivery method. |
| `InterviewStatus` | `DRAFT`, `CONFIRMED`, `CANCELLED` | Schedule lifecycle. |
| `InterviewParticipantRole` | `CANDIDATE`, `INTERVIEWER` | Assignment row type. |
| `InterviewParticipantStatus` | `ASSIGNED`, `CANCELLED` | First implementation uses `ASSIGNED`; `CANCELLED` is reserved for later partial participant cancellation/amendment unless explicitly implemented. |

## 8. API List

### 8.1 Admin API Draft

| Method | Path | Purpose | Request | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/admin/job-postings/{jobPostingId}/interviews?stageId=&status=&from=&to=` | List interview schedules for one posting. | Query parameters. | Paged or list `AdminInterviewSummaryResponse`. |
| `GET` | `/admin/interviews/{interviewId}` | Read interview detail including participants. | None. | `AdminInterviewDetailResponse`. |
| `POST` | `/admin/job-postings/{jobPostingId}/interviews` | Create a DRAFT interview schedule. | `InterviewCreateRequest`. | Created interview id/detail. |
| `POST` | `/admin/interviews/{interviewId}` | Update DRAFT interview schedule fields. | `InterviewUpdateRequest`. | Updated detail. |
| `POST` | `/admin/interviews/{interviewId}/participants` | Replace participant assignment set. | `InterviewParticipantReplaceRequest`. | Updated participant list. |
| `POST` | `/admin/interviews/{interviewId}/confirm` | Confirm schedule after validation. | Optional confirmation memo. | Updated status/detail. |
| `POST` | `/admin/interviews/{interviewId}/cancel` | Cancel schedule. | Optional cancel reason. | Updated status/detail. |
| `POST` | `/admin/interviews/{interviewId}/delete` | Physically delete a `DRAFT` or `CANCELLED` schedule. | None. | Deleted interview id. |

Admin response may include:

- interview id, job posting id/title, stage id/name/type.
- group name, start/end date-time, method, location, room, online meeting URL.
- status, memo.
- candidate participants with application id, applicant snapshot/name, position snapshot where available.
- interviewer participants with employee id, employee name, department where available.

### 8.2 Applicant API Draft

| Method | Path | Purpose | Request | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/applicant/applications/{applicationId}/interviews` | Return the current applicant's own interview schedules for one application. | Session applicant + path application id. | List `ApplicantInterviewResponse`. |

Applicant rules:

- The session applicant must own `applicationId`.
- Return only schedules where the application is assigned as a candidate participant.
- Hide `DRAFT` schedules.
- Return `CONFIRMED` and `CANCELLED` schedules only.
- Do not expose `StageResult` fields or history in applicant-visible schedules.
- Do not expose other candidates.
- Do not expose interviewer list by default.
- Do not expose admin internal memo.
- Expose online meeting URL only for the assigned applicant and only when the schedule method requires it.

### 8.3 Interviewer API Draft

| Method | Path | Purpose | Request | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/interviewer/interviews?from=&to=&jobPostingId=&stageId=` | Return interviews assigned to the current employee. | Session employee + query filters. | List `InterviewerInterviewSummaryResponse`. |
| `GET` | `/interviewer/interviews/{interviewId}` | Return assigned interview detail for the current employee. | Session employee + path id. | `InterviewerInterviewDetailResponse`. |

Interviewer rules:

- The current `Employee` must be assigned as an `INTERVIEWER` participant.
- Hide `DRAFT` schedules.
- Return `CONFIRMED` and `CANCELLED` assigned schedules.
- Detail response may include candidate list because interviewers need to know whom they interview.
- Do not expose admin internal memo unless a separate interviewer memo field is introduced.

## 9. DTO Summary

Request DTO candidates:

| DTO | Type | Responsibility |
| --- | --- | --- |
| `InterviewCreateRequest` | Request DTO record | Creates a DRAFT interview schedule under a job posting. |
| `InterviewUpdateRequest` | Request DTO record | Updates mutable DRAFT fields. |
| `InterviewParticipantReplaceRequest` | Request DTO record | Replaces candidate and interviewer assignments together. |
| `InterviewCandidateParticipantRequest` | Request DTO record | Candidate assignment row with application id and sort order. |
| `InterviewInterviewerParticipantRequest` | Request DTO record | Interviewer assignment row with employee id and sort order. |

Response DTO candidates:

| DTO | Type | Responsibility |
| --- | --- | --- |
| `AdminInterviewSummaryResponse` | Response DTO record | Admin list item. |
| `AdminInterviewDetailResponse` | Response DTO record | Admin detail with participant lists. |
| `AdminInterviewParticipantResponse` | Response DTO record | Candidate/interviewer participant display row. |
| `ApplicantInterviewResponse` | Response DTO record | Applicant-safe schedule response. |
| `InterviewerInterviewSummaryResponse` | Response DTO record | Interviewer list item. |
| `InterviewerInterviewDetailResponse` | Response DTO record | Interviewer detail with candidate list. |

## 10. Service And Controller Summary

| Class | Class type | Responsibility |
| --- | --- | --- |
| `InterviewService` | Service | Admin create/update/participant replace/confirm/cancel and shared validation. |
| `ApplicantInterviewService` | Service | Applicant-owned interview schedule reads. |
| `InterviewerInterviewService` | Service | Current employee assigned schedule reads. |
| `InterviewAdminController` | Controller | Admin interview schedule and participant APIs. |
| `ApplicantInterviewController` | Controller | Applicant own application interview schedule API. |
| `InterviewerInterviewController` | Controller | Interviewer assigned schedule APIs. |
| `InterviewRepository` | Repository | Interview queries by posting, stage, status, time range, and assignment. |
| `InterviewParticipantRepository` | Repository | Participant lookup, duplicate checks, and collision checks. |

Implementation should keep business rules in services and keep controllers responsible only for request parsing, validation, and `ApiResponse<T>` wrapping.

## 11. Validation And Business Rules

### 11.1 Stage Rules

- `Interview.stage` must belong to the same `JobPosting`.
- `Interview.stage.stageType` must be one of:
  - `FIRST_INTERVIEW`
  - `SECOND_INTERVIEW`
  - `FINAL_INTERVIEW`
- Non-interview stages must be rejected.
- Interview schedule creation and confirmation are allowed only for interview stages whose status is `READY` or `IN_PROGRESS`.
- `RESULT_ANNOUNCED` and `CLOSED` stages must reject new interview creation, participant replacement, and confirmation.
- Cancellation of an already confirmed interview is allowed while the stage is not `CLOSED`.
- `CLOSED` stages reject interview cancellation unless a later correction/amendment policy explicitly allows it.
- Before Phase 04a implementation, verify the actual `StageType` enum values. If an expected interview stage type does not exist, do not silently add it without an explicit compatibility decision. Allowed interview stage types must be aligned with the existing `StageType` enum and existing Stage tests.
- The first implementation should not infer stage result changes from interview assignment.

### 11.2 Candidate Eligibility And Applicant Visibility Rule

Interview Scheduling must not become an alternative result announcement channel.

Interview Scheduling never creates, updates, announces, or corrects `StageResult`. Admin confirmation may read `StageResult` to verify candidate eligibility. Applicant schedule read APIs do not read or expose `StageResult` internals.

For an interview stage, a candidate is eligible only when:

1. the `JobApplication` belongs to the same `JobPosting`;
2. the `JobApplication` status is `SUBMITTED`;
3. if there is a previous stage, the candidate has `PASSED` the previous stage;
4. applicant-visible interview APIs expose only confirmed/cancelled assigned schedules and do not include `StageResult` status, history, or announcement fields.

If the project intentionally allows admins to prepare interview assignments before external applicant exposure, those schedules must remain `DRAFT` and admin-only until an administrator confirms the schedule through the scheduling workflow.

### 11.3 Time Rules

- `startDateTime` and `endDateTime` are required.
- `endDateTime` must be after `startDateTime`.
- Time range filters use overlap semantics for admin/interviewer list queries.
- Confirmation must reject candidate or interviewer time collisions.
- Collision check target:
  - `Interview.status = CONFIRMED`
  - `InterviewParticipant.participantStatus = ASSIGNED`
  - same candidate `JobApplication` or same interviewer `Employee`
  - exclude the current interview itself
  - overlap condition: `existing.startDateTime < requested.endDateTime` and `requested.startDateTime < existing.endDateTime`
- Collision checks ignore `Interview.status = CANCELLED` schedules.

### 11.4 Method And Place Rules

- `IN_PERSON`: requires `locationName`; `onlineMeetingUrl` optional or rejected by policy.
- `ONLINE`: requires `onlineMeetingUrl`; `locationName` optional.
- `HYBRID`: requires both `locationName` and `onlineMeetingUrl`.
- `OTHER`: free-form location/URL can be optional but should keep memo available for admin.

### 11.5 Participant Rules

- Confirmation requires at least one assigned candidate.
- Confirmation requires at least one assigned interviewer.
- Candidate participant requires `jobApplication` and must not have `employee`.
- Interviewer participant requires `employee` and must not have `jobApplication`.
- Candidate applications must belong to the same `JobPosting`.
- Candidate applications must be eligible by the Candidate Eligibility And Applicant Visibility Rule.
- Duplicate candidate assignment within the same interview is rejected.
- Duplicate interviewer assignment within the same interview is rejected.
- `sortOrder` should be unique within each participant role when provided.
- `ASSIGNED` participant rows count toward confirmation minimums.
- `CANCELLED` participant rows do not count toward confirmation minimums if a later phase implements partial participant cancellation.
- DRAFT participant replacement may delete and recreate rows instead of preserving participant history.

### 11.6 Status Rules

| Current | Command | Next | Rule |
| --- | --- | --- | --- |
| `DRAFT` | update | `DRAFT` | Allowed. |
| `DRAFT` | replace participants | `DRAFT` | Allowed; may delete and recreate participant rows. |
| `DRAFT` | confirm | `CONFIRMED` | Allowed only after all confirmation guards pass. |
| `CONFIRMED` | cancel | `CANCELLED` | Allowed. |
| `CONFIRMED` | update | rejected | Later phase can add amendment/versioning if needed. |
| `CONFIRMED` | replace participants | rejected | Later phase can add partial cancellation/amendment if needed. |
| `CANCELLED` | update/confirm | rejected | Reopen is out of scope for the first slice. |
| `DRAFT` | delete | deleted | Allowed. Never exposed outside admin, so no evaluation can exist. |
| `CANCELLED` | delete | deleted | Allowed only when the schedule has no `InterviewEvaluation`. |
| `CONFIRMED` | delete | rejected | Must be cancelled first so candidates/interviewers see the cancellation. |

Delete rationale (2026-09-04, added while fixing the stage-delete FK defect):

- `StageService.delete` blocks a READY stage that carries a `CONFIRMED`/`CANCELLED` interview, which left such a stage permanently undeletable because `cancel` only changes status and keeps the row. This command is the escape path: cancel, delete the schedule, then delete the stage.
- `CONFIRMED` is rejected on purpose. Applicant/interviewer read APIs expose `CONFIRMED` and `CANCELLED` schedules, so deleting a confirmed one would make a communicated commitment vanish without notice. Cancelling first keeps the cancellation visible.
- Any `InterviewEvaluation` row blocks the delete. `DRAFT` evaluations are not empty placeholders — an interviewer can temporarily save grade/recommendation/comment before submitting — so no evaluation is safe to destroy here. There is no evaluation delete API, so a schedule that reached evaluation stays undeletable by design.
- `InterviewParticipant` rows are removed by the existing `cascade = ALL` + `orphanRemoval` on `Interview.participants`; no separate cleanup is needed.
- No `ActivityLog` entry is recorded, consistent with the other interview commands (create/update/confirm/cancel are all unaudited) and with the Phase 09b audit taxonomy, which targets result decisions and data egress rather than schedule configuration.

### 11.7 Visibility Rules

- Admin can see `DRAFT`, `CONFIRMED`, and `CANCELLED`.
- Applicant can see only assigned `CONFIRMED` and `CANCELLED`.
- Interviewer can see only assigned `CONFIRMED` and `CANCELLED`.
- Applicant response must hide other candidates, interviewer list, and admin memo.
- Applicant response must satisfy the applicant ownership and visibility rules.
- Interviewer response may include candidate list but not admin memo.

## 12. Database Constraint And Index Candidates

This design does not add migration files, but the implementation should consider the following schema candidates:

### 12.1 `interview`

| Candidate | Purpose |
| --- | --- |
| `index(job_posting_id, stage_id, status)` | Admin list and status filtering. |
| `index(start_date_time, end_date_time)` | Time-range and collision queries. |

### 12.2 `interview_participant`

| Candidate | Purpose |
| --- | --- |
| `unique(interview_id, role, job_application_id)` | Prevent duplicate candidate rows for one interview where DB nullable semantics allow it. |
| `unique(interview_id, role, employee_id)` | Prevent duplicate interviewer rows for one interview where DB nullable semantics allow it. |
| `index(job_application_id)` | Candidate schedule lookup and collision checks. |
| `index(employee_id)` | Interviewer schedule lookup and collision checks. |

Because role-specific nullable foreign keys can make DB uniqueness imperfect depending on the database, final duplicate protection must remain in `InterviewService`.

## 13. Entity Relationship Summary

```text
JobPosting
  └─ Stage
      └─ Interview
          ├─ InterviewParticipant(role=CANDIDATE) -> JobApplication -> Applicant
          └─ InterviewParticipant(role=INTERVIEWER) -> Employee

StageResult
  └─ remains separate from Interview Scheduling
```

`Interview` belongs to both `JobPosting` and `Stage` for efficient posting-level search and explicit stage ownership validation. The service must verify both references point to the same posting.

## 14. Suggested Implementation Slices

| Slice | Goal | Scope |
| --- | --- | --- |
| `04a` | Domain and table candidates | Add `Interview`, `InterviewParticipant`, enums, repositories, and basic tests. |
| `04b` | Admin schedule management | Add admin CRUD, participant replace, confirm/cancel validation. |
| `04c` | Applicant read | Add `/applicant/applications/{applicationId}/interviews` with ownership and visibility rules. |
| `04d` | Interviewer read | Add `/interviewer/interviews` and detail API with assignment-only access. |
| `04e` | Evaluation handoff | Document and test the connection points for later `InterviewEvaluation` without implementing scoring. |

## 15. Test Commands

This phase is documentation-only. Gradle tests were intentionally not run.

Recommended future implementation test commands:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.InterviewServiceTest --no-daemon
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.InterviewAdminControllerTest --tests com.shinyoung.recruit.controller.ApplicantInterviewControllerTest --tests com.shinyoung.recruit.controller.InterviewerInterviewControllerTest --no-daemon
```

## 16. Test Results

- Gradle tests: not run.
- Reason: this phase changed documentation and roadmap/report files only. No Java, test, schema, config, or runtime API files were implemented.
- Documentation validation target:
  - Phase 04 is represented as Interview Scheduling.
  - DB operations baseline is not represented as a recommended phase.
  - Markdown and HTML report contain the same domain, API, visibility, and status policies.

## 17. Test Coverage Plan For Implementation

Future implementation should cover:

- admin create/update/confirm/cancel service tests.
- same posting and same stage ownership validation.
- interview stage type allowlist validation.
- stage status guard validation for create, replace, confirm, and cancel.
- candidate eligibility validation against previous visible/passed `StageResult`.
- time range validation.
- method/location/URL validation.
- duplicate candidate/interviewer assignment validation.
- candidate and interviewer collision validation using the exact overlap rule.
- participant replacement row deletion/recreation behavior in DRAFT.
- confirmed cancellation keeps participant rows assigned.
- applicant ownership and `DRAFT` hiding.
- applicant result-visibility guard so interview schedules cannot reveal a previous stage pass early.
- interviewer assignment-only reads and `DRAFT` hiding.
- `StageResult` non-mutation regression tests.

## 18. Known Limitations

- No Java implementation exists yet.
- No migration file or schema file is produced by this design.
- The design does not decide whether confirmed schedules can be amended after notification. First implementation should reject confirmed updates and use cancel/recreate.
- The design does not expose interviewer list to applicants.
- The design does not define calendar provider integration.

## 19. Remaining Issues

- Final role names for admin/interviewer authorization must be aligned with the existing `DeptRoleMapping` policy.
- The project must decide whether admin participant replacement after confirmation is permanently forbidden or moved to a later amendment/versioning phase.
- Before implementation, verify `StageType` again and align any allowlist change with existing Stage tests. Do not silently add or rename stage types.
- Message phase must decide how confirmed/cancelled interview schedules trigger `MessageBatch` records.

## 20. Phase 04a Implementation Note

Phase 04a was implemented on 2026-05-26 as a domain/repository slice.

Implemented:

- `InterviewMethod`, `InterviewStatus`, `InterviewParticipantRole`, `InterviewParticipantStatus`
- `Interview`
- `InterviewParticipant`
- `InterviewRepository`
- `InterviewParticipantRepository`
- entity validation tests
- repository persistence/query tests

Not implemented in 04a:

- service commands
- controllers/APIs
- request/response DTOs
- applicant/interviewer visibility
- confirmation guard
- collision guard
- `InterviewEvaluation`
- migration file

Actual `StageType` source values checked during 04a:

- `DOCUMENT`
- `FIRST_INTERVIEW`
- `SECOND_INTERVIEW`
- `FINAL_INTERVIEW`
- `ETC`

04a did not modify `StageType`. Later service validation should allow interview scheduling only for `FIRST_INTERVIEW`, `SECOND_INTERVIEW`, and `FINAL_INTERVIEW`.

04a did not modify `StageResult` source. Interview Scheduling continues to follow the policy:

```text
Interview Scheduling does not mutate StageResult.
Candidate eligibility checks are enforced before confirmation. Applicant exposure is controlled by interview status, participant role/status, application ownership, and response field filtering.
```

04a test result:

- `InterviewTest` and `InterviewParticipantTest`: `BUILD SUCCESSFUL`
- `InterviewRepositoryTest` and `InterviewParticipantRepositoryTest`: `BUILD SUCCESSFUL`
- related regression `StageServiceTest`, `StageResultServiceTest`, `JobApplicationServiceTest`: `BUILD SUCCESSFUL`
- full `test` and `clean test`: not run because `instruction.md` forbids full suite execution for this slice.

## 21. Next Phase Recommendation

Proceed with `Phase 04b - Admin Interview Schedule Management` as the next implementation slice:

- add admin schedule create/update/read service and API;
- add DRAFT participant replacement;
- add confirm/cancel commands;
- add service-level validation tests for stage ownership, stage type allowlist, stage status guard, candidate eligibility, applicant visibility, participant role exclusivity, collision checks, and time validity;
- keep `StageResult` unchanged.
