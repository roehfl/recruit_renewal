# Phase 03h-3 Applicant Application Dashboard Design

## Phase 03i-1 Attachment File Upload/Download Design Note

- Phase 03i-1 designs attachment file upload/download but does not change dashboard readiness.
- Attachment remains excluded from `ApplicationCompletionReadChecker` and `submittable` calculation because there is still no attachment required policy or `ApplicationFormConfig` attachment flag.
- Phase 03i-2 upload response should not add `downloadAvailable`; dashboard readiness should not infer attachment completion from download availability until Phase 03i-3/03i-5 define download and required-attachment semantics.
- Attachment storage internals, `physicalFileStatus`, `storedFileName`, and `storagePath` remain outside dashboard summary responses.
- A later Phase 03i-5 candidate should decide whether uploaded attachments become submit-blocking and whether the dashboard should add attachment readiness.
- References:
  - `docs/codex/design/phase-03i-attachment-file-upload-download-design.md`
  - `docs/codex/reports/phase-03i-attachment-file-upload-download-design.html`

## Phase 03h-4 Implementation Note

Phase 03h-4 implemented this design as:

```text
GET /applications/{applicationId:[0-9]+}/dashboard
```

Implemented:

- Added `ApplicationDashboardResponse`.
- Added `ApplicationCompletionSummaryResponse`.
- Added `ApplicationSectionReadinessResponse`.
- Added `ApplicationCompletionReadChecker`.
- Added `ApplicationDashboardService`.
- Added `JobApplicationRepository.findDashboardByIdAndApplicantId(Long applicationId, Long applicantId)`.
- Added optional section `existsByJobApplicationId` repository methods for certificate, language, award, and gap period guidance.
- Added `GET /applications/{applicationId:[0-9]+}/dashboard` to `ApplicationController`.
- Added `ApplicationDashboardServiceTest`.
- Expanded `ApplicationControllerTest` for dashboard success, 401/403, ownership hiding, forbidden fields, and unsupported methods.
- Added implementation reference and human-readable report:
  - `docs/codex/implementation/phase-03h-4-applicant-application-dashboard.md`
  - `docs/codex/reports/phase-03h-4-applicant-application-dashboard.html`

Preserved:

- `SecurityConfig`.
- `ApplicationSubmitValidator`.
- `POST /applications/{applicationId}/submit`.
- Detailed section save APIs.
- `GET /applications/{applicationId}/stage-results`.
- Applicant result response DTOs.
- Admin APIs.
- DB schema.
- Attachment readiness, per-question detailed error payloads, message/notification, and read audit logging remain unimplemented.

Verification:

- `ApplicationDashboardServiceTest`: success.
- `ApplicationControllerTest`: success.
- `JobApplicationServiceTest`: success.
- `ApplicationSubmitValidatorTest`: success.
- `ApplicationStageResultServiceTest` + `ApplicationStageResultControllerTest`: success.
- Full `clean test --no-daemon`: success.

## Phase Name

Phase 03h-3 - Applicant My Application Dashboard Design

## Purpose

This phase designs an applicant-facing dashboard summary API for one application detail screen.

Target API:

```text
GET /applications/{applicationId}/dashboard
```

The dashboard is a compact read model between the applicant's application list (`GET /applications/me`) and the detailed section APIs. It should let the frontend decide which high-level actions and status cards to show before loading every detail section.

This is a design-only phase. No Java source, test source, security configuration, build configuration, DB schema, repository, service, controller, DTO, validator, or API implementation is changed.

## Scope

- Define the dashboard summary API contract candidate.
- Define applicant ownership and access policy.
- Define response fields and nested summary structures.
- Define `accepting`, `editable`, `submittable`, and `withdrawable` policy.
- Define completion and missing-section policy aligned with the current submit validator.
- Define compact announced-result summary policy.
- Define Phase 03h-4 implementation candidates.
- Produce a paired self-contained HTML report.
- Update Phase 03 application design, Phase 03h applicant list design, and implementation history documents.

## Out of Scope

- Implementing `GET /applications/{applicationId}/dashboard`.
- Creating dashboard Java DTOs.
- Adding repository queries.
- Adding service or checker classes.
- Modifying `ApplicationController`.
- Modifying `SecurityConfig`.
- Modifying `ApplicationSubmitValidator`.
- Modifying the existing submit command.
- Modifying any StageResult API.
- Changing DB schema or entity mappings.
- Adding tests or running Gradle tests for this documentation-only phase.

## Changed Files

This phase changes documentation only.

| Path | Type | Notes |
|---|---|---|
| `docs/codex/design/phase-03h-3-applicant-application-dashboard-design.md` | New | Codex reference design for applicant dashboard summary |
| `docs/codex/reports/phase-03h-3-applicant-application-dashboard-design.html` | New | Self-contained human-readable report generated from this design |
| `docs/codex/design/phase-03h-applicant-my-applications-design.md` | Modified | Adds Phase 03h-3 follow-up design note |
| `docs/codex/design/phase-03-application-design.md` | Modified | Adds Phase 03h-3 dashboard design note |
| `docs/codex/07-implementation-history.md` | Modified | Adds design history entry |

No Java, test, build, YAML, security, schema, or static resource file is changed by this phase.

## Current Context

### Existing Related Applicant APIs

| Method | Path | Current Purpose |
|---|---|---|
| `GET` | `/applications/me` | Applicant reads own application list with compact result summary |
| `POST` | `/applications` | Create applicant application |
| `GET` | `/applications/{applicationId}` | Read one owned applicant application |
| `POST` | `/applications/{applicationId}` | Update draft application |
| `POST` | `/applications/{applicationId}/submit` | Submit draft application |
| `POST` | `/applications/{applicationId}/withdraw` | Withdraw submitted application |
| `GET` | `/applications/{applicationId}/stage-results` | Read announced stage results for one owned application |

### Existing Submit Readiness Logic

The current final-submit validator is `ApplicationSubmitValidator`.

Current submit validation basis:

- `ApplicationFormConfig` must exist.
- `useEducation=true` requires at least one `ApplicationEducation` row.
- `useCareer=true` requires `ApplicationCareerProfile`.
- `CareerType` must be selected when career is used.
- `CareerType.EXPERIENCED` requires at least one career row.
- `CareerType.NEWCOMER` and `CareerType.NOT_APPLICABLE` reject existing career rows.
- `useMilitary=true` requires one `ApplicationMilitary` row.
- Military subject type is required.
- `MilitarySubjectType.COMPLETED` requires service start/end dates.
- `MilitarySubjectType.EXEMPTED` requires exemption reason.
- Active required posting questions require a non-null, non-blank answer.
- Present non-null answers must be within effective max length.
- `Certificate`, `Language`, `Award`, and `GapPeriod` are currently optional for submit even when their `use*` flag is enabled.
- Attachment has no `ApplicationFormConfig` flag and is not final-submit required.

Phase 03h-3 recommends a read-only completion checker that mirrors this policy without calling command validation code directly from the dashboard endpoint.

## API List

### Recommended API

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| `GET` | `/applications/{applicationId}/dashboard` | Logged-in applicant reads one owned application dashboard summary | Path: `applicationId` | `ApiResponse<ApplicationDashboardResponse>` |

Recommended path mapping in Phase 03h-4:

```text
/applications/{applicationId:[0-9]+}/dashboard
```

The numeric path constraint prevents collisions with `/applications/me`.

No `POST`, `PUT`, `PATCH`, or `DELETE` endpoint is part of this dashboard phase.

## Access Policy

| Caller | Behavior |
|---|---|
| Anonymous | `401 + ApiResponse.fail(...)` through existing security exception handling |
| Applicant with `ROLE_APPLICANT` and owned application | Allowed |
| Applicant with no matching owned application | 404 hiding policy through existing owned application lookup pattern |
| Applicant attempting another applicant's application | Same 404 hiding policy |
| Employee/admin | `403 + ApiResponse.fail(...)`; must use admin APIs |

The endpoint must not accept `applicantId`.

Recommended controller flow:

```text
CustomUserDetails -> CurrentApplicantService -> applicantId -> dashboard service
```

## Response DTO Candidate

Recommended DTO name:

```text
ApplicationDashboardResponse
```

Recommended wrapper:

```text
ApiResponse<ApplicationDashboardResponse>
```

### Top-Level Response Fields

| Field | Source | Required | Notes |
|---|---|---:|---|
| `applicationId` | `JobApplication.id` | Yes | Current application id |
| `jobPostingId` | `JobPosting.id` | Yes | Posting id |
| `jobPostingTitle` | Application snapshot or current posting title | Yes | Prefer application snapshot for stable applicant history display |
| `jobPositionName` | Application snapshot or current position name | Yes | Prefer application snapshot |
| `applicationStatus` | `JobApplication.status` | Yes | `DRAFT`, `SUBMITTED`, or `WITHDRAWN` |
| `accepting` | service-calculated boolean | Yes | Posting is `PUBLISHED` and now is inside reception period |
| `editable` | action policy | Yes | Whether draft editing commands should be shown |
| `submittable` | action policy + completion checker | Yes | Whether submit command should be shown/enabled |
| `withdrawable` | action policy | Yes | Whether withdraw command should be shown/enabled |
| `submittedAt` | `JobApplication.submittedAt` | No | Nullable |
| `withdrawnAt` | `JobApplication.withdrawnAt` | No | Nullable |
| `completionSummary` | completion checker | Yes | Required/optional completion counts and rate |
| `requiredMissingSections` | completion checker | Yes | Submit-blocking missing or invalid readiness items |
| `optionalIncompleteSections` | completion checker | Yes | Non-blocking enabled optional section hints |
| `latestAnnouncedStageName` | StageResult summary | No | Latest visible announced stage |
| `latestResultStatus` | StageResult summary | No | Latest visible announced result status |

### Completion Summary Candidate

Recommended nested DTO name:

```text
ApplicationCompletionSummaryResponse
```

Recommended fields:

| Field | Meaning |
|---|---|
| `requiredSectionCount` | Count of enabled submit-required checks |
| `completedRequiredSectionCount` | Count of submit-required checks currently satisfied |
| `requiredMissingCount` | Count of submit-required readiness issues |
| `optionalSectionCount` | Count of enabled optional sections tracked for guidance |
| `completedOptionalSectionCount` | Count of optional sections with at least one meaningful record or completed state |
| `optionalIncompleteCount` | Count of optional tracked sections that are enabled but empty or incomplete |
| `requiredCompletionRate` | Integer percentage from required counts; `100` when there are no required checks |
| `submitBlockingIssueCount` | Count of issues that should keep `submittable=false` |

`requiredCompletionRate` is a dashboard display value only. The submit command remains the authority for final state transition.

### Section Readiness Item Candidate

Recommended nested DTO name:

```text
ApplicationSectionReadinessResponse
```

Recommended fields:

| Field | Meaning |
|---|---|
| `sectionCode` | Stable code such as `EDUCATION`, `CAREER`, `MILITARY`, `QUESTION`, `CERTIFICATE`, `LANGUAGE`, `AWARD`, `GAP_PERIOD` |
| `sectionName` | Display name candidate for the frontend |
| `required` | Whether the issue blocks final submit |
| `complete` | Whether the section/check is currently complete |
| `reasonCode` | Stable machine-readable reason |
| `message` | Short non-sensitive display hint |

Recommended reason codes:

| Reason Code | Meaning |
|---|---|
| `MISSING_ROW` | Required row or section is absent |
| `MISSING_PROFILE` | Career profile is absent |
| `TYPE_NOT_SELECTED` | Required type selection is absent |
| `MISSING_PERIOD` | Required start/end period is absent |
| `MISSING_REASON` | Required reason text is absent |
| `MISSING_REQUIRED_ANSWER` | Required question answer is absent, null, or blank |
| `INVALID_DISALLOWED_ROW` | A row exists when the selected policy disallows it |
| `INVALID_LENGTH` | Present answer exceeds allowed length |
| `OPTIONAL_EMPTY` | Enabled optional section has no meaningful data |

Messages must not include internal score, employee actor, audit history, storage path, or sensitive personal data.

## Field Exposure Rules

Do not include:

| Field | Reason |
|---|---|
| applicant name/email/phone/CI/address | Not needed for the dashboard summary |
| `applicantId` | The endpoint is implicitly scoped to the current applicant |
| `stageResultId` | Internal result row id |
| `score` | Internal evaluation data |
| `comment` | Admin memo or sensitive internal note |
| `decidedBy` | Internal employee/admin identity |
| `correctedBy` | Internal correction actor |
| correction reason/history | Admin-only audit data |
| file storage path, stored file name | Attachment storage details are not dashboard summary data |

## Action Flag Policy

### Accepting

Recommended calculation:

```text
accepting =
  jobPosting.status == PUBLISHED
  and now >= receptionStartDateTime
  and now <= receptionEndDateTime
```

Use the existing `Clock`-based pattern in Phase 03h-4 so tests can fix time.

### Editable

```text
editable =
  application.status == DRAFT
  and accepting == true
```

`editable` is a UI action hint. Existing section save APIs must continue enforcing their own rules.

### Submittable

```text
submittable =
  application.status == DRAFT
  and accepting == true
  and completionSummary.submitBlockingIssueCount == 0
```

The dashboard checker should mirror `ApplicationSubmitValidator` as closely as possible. However, `POST /applications/{applicationId}/submit` remains the authority and must revalidate everything.

### Withdrawable

```text
withdrawable =
  application.status == SUBMITTED
  and accepting == true
```

### Status Matrix

| Application Status | `accepting=true` | editable | submittable | withdrawable |
|---|---:|---:|---:|---:|
| `DRAFT` and no blocking issues | Yes | Yes | Yes | No |
| `DRAFT` and blocking issues | Yes | Yes | No | No |
| `DRAFT` | No | No | No | No |
| `SUBMITTED` | Yes | No | No | Yes |
| `SUBMITTED` | No | No | No | No |
| `WITHDRAWN` | Either | No | No | No |

## Completion and Missing Section Policy

### Required Checks

The dashboard should treat the following as submit-required when the relevant config or posting data makes them active:

| Area | Required When | Completion Rule |
|---|---|---|
| Education | `ApplicationFormConfig.useEducation=true` | At least one education row exists |
| Career profile | `ApplicationFormConfig.useCareer=true` | Career profile exists and `careerType` is selected |
| Career rows | `useCareer=true` and `careerType=EXPERIENCED` | At least one career row exists |
| Career row absence | `useCareer=true` and `careerType=NEWCOMER` or `NOT_APPLICABLE` | No career row exists |
| Military record | `ApplicationFormConfig.useMilitary=true` | Military row exists and subject type is selected |
| Military period | `useMilitary=true` and subject type is `COMPLETED` | Service start and end dates exist |
| Military exemption reason | `useMilitary=true` and subject type is `EXEMPTED` | Non-blank exemption reason exists |
| Required questions | Active `JobPostingQuestion.required=true` | Answer row exists and `answerText` is not null or blank |
| Answer length | Any present non-null answer | Effective max length and type hard limits are satisfied |

### Optional Guidance

The following enabled sections should be candidates for `optionalIncompleteSections` only. They do not block `submittable` under the current submit policy:

| Section | Config Flag | Basic Phase 03h-4 Optional Completion Candidate |
|---|---|---|
| Certificate | `useCertificate` | At least one certificate row exists |
| Language | `useLanguage` | At least one language row exists |
| Award | `useAward` | At least one award row exists |
| GapPeriod | `useGapPeriod` | At least one gap period row exists |

Attachment is deferred because `ApplicationFormConfig` has no attachment flag.

### Question and Answer Decision

Phase 03h-3 recommends including active required question answer missing/blank checks in dashboard readiness.

Rationale:

- Phase 03c-9-3 already makes active required questions submit-blocking.
- The dashboard should not show `submittable=true` when final submit would predictably fail due to a missing required answer.
- Optional question blanks should not block dashboard submission.

Answer length violations should keep `submittable=false` when the Phase 03h-4 checker can detect them. A richer per-question error payload can be deferred to Phase 03h-5.

## Result Summary Policy

The dashboard should use the same applicant-visible result summary policy as Phase 03h-2:

- Only stages with `Stage.status == RESULT_ANNOUNCED || CLOSED` are visible.
- `READY` and `IN_PROGRESS` stages are excluded.
- Latest visible result is determined by `stageOrder DESC, stage.id DESC`.
- `latestAnnouncedStageName` and `latestResultStatus` are nullable.
- Detailed result rows remain in `GET /applications/{applicationId}/stage-results`.
- `score`, `comment`, actor fields, and correction history remain hidden.

## Entity, DTO, Service, Controller Summary

No class is implemented in this design phase. Candidate Phase 03h-4 classes/methods:

| Layer | Candidate | Type | Responsibility | Key Fields or Methods | Related Classes | Notes |
|---|---|---|---|---|---|---|
| Response DTO | `ApplicationDashboardResponse` | Response DTO | Dashboard top-level response | fields listed above, static factory candidate | `JobApplication`, completion summary, StageResult summary | Must not expose applicant personal data or internal result fields |
| Response DTO | `ApplicationCompletionSummaryResponse` | Response DTO | Completion counts and required rate | required/optional counts, `submitBlockingIssueCount` | completion checker | Display-only summary |
| Response DTO | `ApplicationSectionReadinessResponse` | Response DTO | Missing/incomplete section item | `sectionCode`, `reasonCode`, `message` | completion checker | Stable reason codes for frontend rendering |
| Service | `ApplicationDashboardService` | Service | Read orchestration for one dashboard | `getDashboard(applicantId, applicationId)` | repositories, `Clock`, completion checker | Recommended if checker grows beyond `JobApplicationService` |
| Service | `ApplicationCompletionReadChecker` | Service/helper | Read-only submit readiness mirror | `check(JobApplication)` | `ApplicationSubmitValidator` policy, repositories | Must not mutate state or call submit |
| Repository | existing section repositories | Repository methods | Existence/count checks | `existsByJobApplicationId`, `findByJobApplicationId` | section entities | Reuse existing methods where possible |
| Repository | `StageResultRepository` | Repository method | Visible result summary | existing or generalized visible summary query | `StageResult`, `Stage` | Avoid N+1 when dashboard later expands |
| Controller | `ApplicationController` | Controller method | HTTP endpoint | `GET /applications/{applicationId}/dashboard` | `CurrentApplicantService`, `ApiResponse` | Path should use numeric id constraint |
| Test | service/controller tests | Test | Contract and policy coverage | ownership, status/action flags, completion, result summary, 401/403 | Spring Security test | Add in implementation phase only |

## Phase 03h-4 Implementation Recommendation

Implement a narrow read-only vertical slice:

1. Add `ApplicationDashboardResponse`.
2. Add nested completion/readiness response records.
3. Add `ApplicationDashboardService` or a clearly scoped read method.
4. Add `ApplicationCompletionReadChecker` that mirrors current submit validator policy.
5. Reuse existing owned-application lookup pattern.
6. Reuse existing `Clock`-based accepting calculation.
7. Reuse or generalize Phase 03h-2 visible result summary logic.
8. Add `GET /applications/{applicationId:[0-9]+}/dashboard`.
9. Add focused service and controller tests.

Recommended Phase 03h-4 minimum:

- Top-level application/posting/action fields.
- Required completion summary for Education, Career, Military, and required questions.
- Optional incomplete hints for Certificate, Language, Award, and GapPeriod.
- Latest visible announced result summary.

Defer to Phase 03h-5:

- Per-question detailed error payloads.
- Attachment completion policy.
- Fine-grained section progress percentages.
- Central refactoring between `ApplicationSubmitValidator` and read-only checker if duplication grows.

## Test Plan

Phase 03h-3 does not add or run tests. Phase 03h-4 should add coverage.

| Test Area | Coverage |
|---|---|
| Service | Applicant can read dashboard for own `DRAFT`, `SUBMITTED`, and `WITHDRAWN` applications |
| Service | Other applicant's application is hidden |
| Service | `accepting` true/false by posting status and reception period |
| Service | `editable` true only for `DRAFT + accepting` |
| Service | `submittable` true only for `DRAFT + accepting + no blocking readiness issues` |
| Service | `withdrawable` true only for `SUBMITTED + accepting` |
| Service | `WITHDRAWN` returns all action flags false |
| Service | Education required missing appears when `useEducation=true` and no row exists |
| Service | Career profile/type/row policy follows current submit validator |
| Service | Military row/type/period/reason policy follows current submit validator |
| Service | Active required question answer missing/blank appears as blocking |
| Service | Optional sections appear only in optional incomplete list |
| Service | Visible result summary uses only `RESULT_ANNOUNCED` and `CLOSED` stages |
| Controller | Applicant request returns `ApiResponse<ApplicationDashboardResponse>` |
| Controller | Anonymous request returns 401 JSON |
| Controller | Employee/admin request returns 403 JSON |
| Controller | Response does not serialize score/comment/actor/history fields |

## Test Commands

Not executed in this phase because this is documentation-only.

Recommended Phase 03h-4 targeted commands:

```powershell
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationDashboardServiceTest --tests com.shinyoung.recruit.controller.ApplicationControllerTest
```

Recommended full regression command after implementation:

```powershell
$env:AES_SECRET_KEY='***'; .\gradlew.bat clean test --no-daemon
```

## Test Results

- Not executed.
- Reason: documentation-only design phase.
- Verification to perform for this phase:
  - New markdown document exists.
  - New HTML report exists and is self-contained.
  - Related design/history documents reference Phase 03h-3.
  - No Java or test file is changed by this phase.

## Remaining Issues

- Exact DTO class names can be adjusted in Phase 03h-4 if existing naming conventions suggest a better name.
- `ApplicationCompletionReadChecker` will intentionally duplicate parts of `ApplicationSubmitValidator` at first; a shared policy abstraction should wait until the read/write requirements stabilize.
- Optional section completion should start with simple row-existence checks and be refined only if the frontend needs more precision.
- Attachment readiness is deferred until an attachment config flag or business rule exists.
- Detailed per-question validation messages are deferred to Phase 03h-5.
- Result announcement scheduling by `resultAnnouncementDateTime` remains deferred; the current visibility policy follows `Stage.status`.

## Next Phase Recommendation

Phase 03h-4 should implement the basic dashboard summary API as a read-only endpoint.

Recommended implementation boundary:

| Step | Scope |
|---|---|
| 1 | Add dashboard response DTO records |
| 2 | Add read-only completion checker with current submit validator parity |
| 3 | Add dashboard service orchestration |
| 4 | Add applicant-owned controller endpoint |
| 5 | Add service and controller tests |
| 6 | Add implementation markdown and HTML report |

Phase 03h-5 candidate:

- Completion checker/detail improvement.
- Rich missing-section detail.
- Attachment readiness policy.
- Shared validation policy extraction if duplication becomes risky.
