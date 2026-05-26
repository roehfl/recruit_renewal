# Phase 03i-5 - Attachment Required Policy Design

## Purpose

Phase 03i-5-1 designs how attachment requirements become part of application readiness and final submit validation.

The current attachment implementation supports metadata rows, physical upload, download, soft delete, and read-only storage health scan. However, attachments are still excluded from the public required-policy response, applicant dashboard readiness, and final submit validator. This design decides the first required unit, domain model, matching rule, admin API shape, public API impact, dashboard impact, submit validator impact, and implementation split.

This is a documentation-only phase. No Java source, tests, database migration files, YAML, security config, controller, service, repository, entity, enum, or runtime API behavior is changed.

Changed documentation in this phase:

| Path | Change |
| --- | --- |
| `docs/codex/design/phase-03i-5-attachment-required-policy-design.md` | New Codex reference design |
| `docs/codex/reports/phase-03i-5-attachment-required-policy-design.html` | New self-contained human-readable report |
| `docs/codex/07-implementation-history.md` | Added design history entry |

## Current State

Attachment lifecycle already has these implemented pieces:

| Area | Current State |
| --- | --- |
| Metadata | `ApplicationAttachment` belongs to one `JobApplication` and stores type, section, optional section record id, display filename, content type, size, sort order, storage fields, and lifecycle status. |
| Upload | Applicant upload exists at `POST /applications/{applicationId}/attachments/files`. Upload creates `physicalFileStatus=STORED` rows. |
| Download | Applicant/admin download exists and accepts only `STORED` rows. `METADATA_ONLY`, `MISSING`, `DELETED`, mismatch, and missing physical file cases return controlled 404. |
| Delete | Applicant/admin delete commands retain the row and move it to `physicalFileStatus=DELETED`. Normal metadata lists exclude deleted rows. |
| Storage scan | Admin dry-run scan reports storage/DB mismatches without deleting files or mutating rows. |
| Public policy | `ApplicationFormRequiredPolicyResponse.attachmentRequired` is currently `false`; the `ATTACHMENT` section is reported as deferred. |
| Dashboard | `ApplicationCompletionReadChecker` does not evaluate attachments. |
| Submit | `ApplicationSubmitValidator` does not evaluate attachments. |

Relevant current enums:

| Enum | Current Values |
| --- | --- |
| `AttachmentType` | `RESUME`, `TRANSCRIPT`, `GRADUATION_CERTIFICATE`, `CAREER_CERTIFICATE`, `CERTIFICATE_PROOF`, `LANGUAGE_SCORE_REPORT`, `PORTFOLIO`, `ETC` |
| `ApplicationSectionType` | `APPLICATION`, `EDUCATION`, `CAREER`, `CERTIFICATE`, `LANGUAGE`, `MILITARY`, `AWARD`, `GAP_PERIOD`, `ETC` |
| `PhysicalFileStatus` | `METADATA_ONLY`, `STORED`, `MISSING`, `DELETED` |

## Problem

A single attachment boolean cannot express the actual business question.

Examples:

- A posting may require a resume but only recommend a portfolio.
- A posting may require a transcript under `EDUCATION` but not require a general attachment.
- The same attachment type can appear in different form sections.
- A file that exists on disk but has no active DB row cannot be used for readiness or submit validation.
- A retained `DELETED` row must not satisfy a requirement even though the row still exists.

The required policy therefore needs a per-posting rule model that can match uploaded `ApplicationAttachment` rows by business metadata and lifecycle state.

## Design Goals

- Decide the first stable required unit.
- Avoid adding coarse `useAttachment` or `requireAttachment` flags to `ApplicationFormConfig`.
- Keep attachment policy separate from the existing section `useXxx` and `requireXxx` model.
- Make dashboard readiness and final submit validation use the same matching rule.
- Keep storage internals hidden from public, applicant, and admin policy responses.
- Keep the first implementation small enough for a vertical slice.
- Leave room for later per-row, conditional, per-position, per-applicant-type, and template linkage requirements.

## Out of Scope

- Java source changes.
- Test source changes.
- Database migration file creation.
- Runtime API implementation.
- Controller, service, repository, entity, DTO, enum, config, or security changes.
- Changing upload, download, delete, metadata replace, or storage scan behavior.
- Creating a frontend or static resource.
- Adding `useAttachment` or `requireAttachment` to `ApplicationFormConfig`.
- Per-section-record attachment requirements.
- Conditional attachment requirements.
- Per-position or per-applicant-type attachment requirements.
- Attachment template/download-form linkage.
- Virus scan, DLP, object storage, or cleanup implementation.

## Current Attachment Lifecycle Summary

`ApplicationAttachment` is the only runtime evidence row considered by this design. Physical files are never scanned directly by readiness or submit validation.

| Lifecycle State | Satisfies Required Attachment? | Reason |
| --- | ---: | --- |
| `STORED` | Yes, when rule metadata matches | File-backed row created by the upload API and accepted by download policy |
| `METADATA_ONLY` | No | No physical file evidence |
| `MISSING` | No | DB row exists but physical file is absent or known unavailable |
| `DELETED` | No | Retained only for lifecycle/audit; hidden from normal lists |
| Orphan physical file | No | No active DB row belongs to the application |

Delete interaction:

- Applicant delete is allowed only for editable `DRAFT` applications.
- Admin delete can affect `DRAFT`, `SUBMITTED`, and `WITHDRAWN` applications with a required reason.
- If a required attachment is deleted from a `DRAFT` application, the dashboard becomes incomplete and submit must fail.
- If an admin deletes a required attachment from a `SUBMITTED` application, the application status should not be automatically rolled back in the first implementation. Readiness views that re-evaluate current evidence may show the missing required attachment, but status mutation requires a separate product/audit decision.

## Required Unit Decision

Recommended first required unit:

```text
jobPosting + attachmentType + sectionType
```

Decision matrix:

| Candidate | Decision | Reason |
| --- | --- | --- |
| Global posting-level `requireAttachment` | Rejected | Too coarse; cannot say which file is required. |
| `attachmentType` only | Rejected for first model | Cannot distinguish the same type across different sections. |
| `sectionType` only | Rejected | Cannot express multiple business files in the same section. |
| `attachmentType + sectionType` | Recommended | Matches current upload metadata, supports common requirements, and avoids pre-existing section row dependency. |
| `attachmentType + sectionType + sectionRecordId` | Deferred | Per-row requirements need row identity before applicant rows exist and complicate admin configuration. |

`sectionRecordId` remains ignored by first-phase matching. It can be introduced later only if the product needs a specific row-level evidence model, such as one certificate proof per saved certificate row.

## Recommended Model

Add a dedicated per-posting rule entity in the implementation phase:

```text
JobPostingAttachmentRequirement
```

Recommended first fields:

| Field | Type Candidate | Rule |
| --- | --- | --- |
| `id` | `Long` | Identity primary key |
| `jobPosting` | `JobPosting` | Required `ManyToOne(fetch = LAZY)` |
| `attachmentType` | `AttachmentType` | Required |
| `sectionType` | `ApplicationSectionType` | Required |
| `required` | `boolean` | Required rows block submit; optional rows are guidance only |
| `minCount` | `int` | Required rows must have at least this many matching files; default `1` for required rows |
| `sortOrder` | `Integer` | Admin/public display ordering |
| `displayName` | `String` | User-facing label such as `Resume` or `Transcript` |
| `description` | `String` | Optional user-facing guidance |
| `createdAt`, `updatedAt` | inherited from `BaseEntity` | Auditing fields |

Recommended first constraints:

- `jobPosting` is mandatory.
- `attachmentType` is mandatory.
- `sectionType` is mandatory.
- `required=true` requires `minCount >= 1`.
- `required=false` allows `minCount = 0`; optional rules do not block submit.
- `displayName` should be nonblank and limited to a reasonable length such as 100.
- `description` should be nullable and length-limited, for example 500.
- First implementation should prevent duplicate `(jobPosting, attachmentType, sectionType)` rules.

Review update - optional rule completion semantics:

| Rule State | Meaning | Dashboard When No Matching File Exists | Submit Impact |
| --- | --- | --- | --- |
| `required=false`, `minCount=0` | Simple guidance only | Do not add `optionalIncomplete` | Does not block |
| `required=false`, `minCount>0` | Recommended optional attachment item | Add `optionalIncomplete` when matching stored count is below `minCount` | Does not block |
| `required=true`, `minCount>=1` | Required attachment item | Add `requiredMissing` when matching stored count is below `minCount` | Blocks submit |

This makes optional attachments explicit without turning all optional policy rows into dashboard warnings. Optional rows with `minCount=0` can be displayed in public detail as guidance, but an applicant is not incomplete for omitting them.

Deferred model fields:

| Field | Reason Deferred |
| --- | --- |
| `sectionRecordId` | Applicant detail row ids are not known when configuring a posting. |
| `maxCount` | Existing upload properties already enforce application-level count/size; per-rule caps need UX policy. |
| `active` | DRAFT-only replace-all mutation can physically replace rows; add `active` only if published mutation/history is allowed. |
| condition fields | Needs applicant type, career type, answer, or position policy. |
| position/applicant-type scope | Requires product decisions and likely unique constraint changes. |
| template/download-form linkage | Separate file-template domain is not implemented. |

## Entity Relationship Summary

Target relationship:

```text
JobPosting 1 --- N JobPostingAttachmentRequirement
JobApplication 1 --- N ApplicationAttachment
```

Validation and readiness connect the two sides through the application:

```text
JobApplication.jobPosting
  -> JobPostingAttachmentRequirement rows
  -> match against ApplicationAttachment rows for that JobApplication
```

No direct relationship is required between `JobPostingAttachmentRequirement` and `ApplicationAttachment`. A requirement is a policy row; an attachment is applicant evidence.

## Matching Rule

A requirement is satisfied for an application when the count of matching attachments is greater than or equal to `minCount`.

Matching attachments must all satisfy:

```text
attachment.jobApplication.id == application.id
attachment.attachmentType == requirement.attachmentType
attachment.sectionType == requirement.sectionType
attachment.physicalFileStatus == STORED
attachment.deletedAt == null
```

The first implementation should not count:

- `METADATA_ONLY` rows.
- `MISSING` rows.
- `DELETED` rows.
- rows with `deletedAt` populated.
- physical files found by storage scan without an active DB row.
- attachments belonging to another application.
- attachments with the same type but a different section.
- attachments with the same section but a different type.

Repository query candidate:

```text
countByJobApplicationIdAndAttachmentTypeAndSectionTypeAndPhysicalFileStatus(
    applicationId,
    attachmentType,
    sectionType,
    STORED
)
```

If delete fields and status ever diverge, the stricter interpretation wins: deleted lifecycle metadata means the row cannot satisfy a requirement.

## Admin API Design

Two shapes were considered:

| Candidate | Decision | Reason |
| --- | --- | --- |
| Individual CRUD endpoints per requirement row | Deferred | Useful later for stable row editing and audit history, but broader than needed. |
| Replace-all per posting | Recommended first | Matches current section configuration style, gives one deterministic admin save command, and pairs well with DRAFT-only mutation. |

Recommended first endpoints:

| Method | Path | Purpose | Request | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/admin/job-postings/{jobPostingId}/attachment-requirements` | Read configured attachment requirements for one posting | Path id | `ApiResponse<List<JobPostingAttachmentRequirementResponse>>` |
| `POST` | `/admin/job-postings/{jobPostingId}/attachment-requirements` | Replace all requirements for one posting | JSON replace request | `ApiResponse<List<JobPostingAttachmentRequirementResponse>>` |

Request DTO candidate:

```json
{
  "requirements": [
    {
      "attachmentType": "RESUME",
      "sectionType": "APPLICATION",
      "required": true,
      "minCount": 1,
      "sortOrder": 0,
      "displayName": "Resume",
      "description": "Upload the latest resume."
    }
  ]
}
```

Response DTO candidate:

```json
{
  "requirementId": 1,
  "jobPostingId": 10,
  "attachmentType": "RESUME",
  "sectionType": "APPLICATION",
  "required": true,
  "minCount": 1,
  "sortOrder": 0,
  "displayName": "Resume",
  "description": "Upload the latest resume."
}
```

Validation failures should use existing `ApiResponse.fail(...)` error handling style through project exceptions.

## Mutation Policy

Recommended first mutation policy:

- Attachment requirements are editable only while the job posting is `DRAFT`.
- Published postings should reject replace-all mutation in Phase 03i-5-2.
- Do not silently change requirements for in-progress applicants.
- Do not retroactively mutate application statuses when requirements change.

Rationale:

- Final submit and dashboard readiness depend on these rows.
- Changing requirements after applicants have uploaded or submitted files can make existing applications appear newly incomplete.
- A later product decision can allow published changes with explicit audit, applicant notification, and effective-date rules.

If published mutation is later required, add:

- `active` or versioning fields,
- immutable change history,
- explicit "applies to existing applications" policy,
- admin confirmation text,
- dashboard/status impact policy for submitted applications.

## Public API Impact

Keep `applicationFormConfig` unchanged. Do not add attachment flags to it.

Recommended public aggregate policy:

- `applicationFormRequiredPolicy.attachmentRequired = true` when the posting has at least one required attachment requirement.
- The `ATTACHMENT` section in `applicationFormRequiredPolicy.sections` is no longer `DEFERRED` after implementation:
  - no requirement rows: `DISABLED`
  - only optional rows: `OPTIONAL`
  - at least one required row: `REQUIRED`

Recommended detail policy:

- Public detail should expose a separate attachment requirement list so the applicant can see which files are expected before starting or editing an application.
- Do not overload `sections[]` with per-file details.
- Public list should keep only the aggregate `applicationFormRequiredPolicy` unless frontend performance or UX explicitly requires the detail list there.

Public detail response candidate:

```json
{
  "applicationFormRequiredPolicy": {
    "attachmentRequired": true,
    "sections": [
      {
        "sectionCode": "ATTACHMENT",
        "displayName": "Attachment",
        "enabled": true,
        "required": true,
        "requirementType": "REQUIRED",
        "description": "Required attachment files are configured."
      }
    ]
  },
  "attachmentRequirements": [
    {
      "attachmentType": "RESUME",
      "sectionType": "APPLICATION",
      "required": true,
      "minCount": 1,
      "sortOrder": 0,
      "displayName": "Resume",
      "description": "Upload the latest resume."
    }
  ]
}
```

Do not expose:

- `storedFileName`
- `storagePath`
- storage root
- absolute local path
- physical file status
- delete lifecycle fields
- admin-only audit fields

## Dashboard Readiness Impact

`ApplicationCompletionReadChecker` should add attachment readiness after the policy model exists.

Recommended grouping:

- If any required attachment requirement exists, add one required group: `ATTACHMENT`.
- The required group is complete only when all required attachment requirements are satisfied.
- Add one required issue per missing required rule.
- If only optional attachment rules exist and at least one optional rule has `minCount > 0`, add one optional group: `ATTACHMENT`.
- Optional rules with `minCount=0` are public guidance only and should not create an `optionalIncomplete` dashboard issue when no file exists.
- Optional rules with `minCount>0` create optional incomplete issues when the matching stored count is below `minCount`.
- Optional attachment rules never block `submittable`.
- If both required and optional rules exist, the required `ATTACHMENT` group is enough for counts; optional details can be exposed through the public/detail requirement list rather than duplicate dashboard groups.

Readiness issue candidate:

```json
{
  "sectionCode": "ATTACHMENT",
  "sectionName": "Attachment",
  "required": true,
  "complete": false,
  "reasonCode": "REQUIRED_ATTACHMENT_MISSING",
  "message": "Required attachment is missing."
}
```

The message may include `displayName` if safe and useful, for example `Resume attachment is missing.` It must not include storage path, stored filename, physical file status, deleted actor, or internal storage health details.

## Submit Validator Impact

`ApplicationSubmitValidator` should validate attachment requirements after the existing section and question validations.

Recommended submit flow:

1. Load active requirement rows for `application.jobPosting.id`.
2. Filter to `required=true`.
3. For each required row, count matching stored, non-deleted attachments by the matching rule.
4. If count is less than `minCount`, throw `InvalidJobApplicationException`.
5. Ignore optional attachment requirements for submit blocking.

Failure message candidate:

```text
Required attachment is missing before submit.
```

If using the display name:

```text
Resume attachment is required before submit.
```

The submit validator remains the authority. Dashboard readiness is only a read model and must mirror the same rule.

## Interaction With ApplicationFormConfig

Do not add these fields to `ApplicationFormConfig` in the first implementation:

- `useAttachment`
- `requireAttachment`

Reason:

- Attachment requirements are not one section-level boolean.
- The required unit is `attachmentType + sectionType`.
- The public `applicationFormRequiredPolicy.attachmentRequired` can be derived from the dedicated requirement table.
- Section visibility and applicant attachment upload access are already controlled by application ownership/status/window and attachment APIs, not by a form config flag.

`ApplicationFormConfig` continues to own education, career, certificate, language, military, award, and gap-period section usage/required policy only.

## Interaction With Attachment Lifecycle

Readiness and submit validation must use DB attachment rows, not raw filesystem scans.

Rules:

- `STORED` rows satisfy requirements only when type/section match.
- `METADATA_ONLY` rows never satisfy physical attachment requirements.
- `MISSING` rows never satisfy requirements.
- `DELETED` rows never satisfy requirements.
- Orphan physical files never satisfy requirements.
- A `STORED` row whose physical file is actually missing but has not been marked `MISSING` may still be counted by a pure DB query. Phase 03i-5-2 does not need to perform filesystem existence checks during dashboard or submit validation. The first implementation should rely on `physicalFileStatus=STORED` and treat storage health scan/repair as the operational control that marks broken rows as `MISSING`.
- Before production use of attachment-required submit blocking, operations should run or schedule the storage health scan/repair flow so stale `STORED` rows are corrected to `MISSING` where appropriate.
- Admin deletion of a required stored attachment can make current evidence incomplete. The first implementation should not automatically rollback `SUBMITTED` to `DRAFT`; status correction is a later product/audit decision.

Recommended follow-up:

- If storage health scan detects `STORED_MISSING_PHYSICAL_FILE`, a later mark-missing/repair phase should decide whether readiness immediately excludes it. Once status becomes `MISSING`, this policy already excludes it.

## Validation Rules

Admin requirement request validation:

| Rule | Result |
| --- | --- |
| missing `attachmentType` | 400 |
| missing `sectionType` | 400 |
| unsupported enum value | 400 |
| `required=true && minCount < 1` | 400 |
| `required=false && minCount < 0` | 400 |
| duplicate `(attachmentType, sectionType)` in the same request | 400 |
| blank `displayName` | 400 |
| `displayName` too long | 400 |
| `description` too long | 400 |
| posting not found | 404 |
| posting not `DRAFT` on replace | 400 |

Recommended normalizations:

- Trim `displayName`.
- Trim nullable `description`; store null if blank.
- Default `minCount` to `1` when `required=true` and request omits it, if the request DTO allows null.
- Default optional `minCount` to `0` when `required=false` and request omits it.
- Stable response order: `sortOrder ASC`, `id ASC`.

## Security / Exposure Policy

Admin endpoints:

- Reuse existing `/admin/**` security.
- Allow admin/recruit-admin roles only.
- Anonymous requests return 401 through existing handlers.
- Applicant principals return 403.

Public endpoints:

- Expose only policy metadata required for applicant UI.
- Do not expose whether a particular applicant has uploaded a file on public posting endpoints.

Applicant dashboard/submit:

- Scope readiness to the current applicant's application through existing owned application lookup.
- Do not expose attachment storage internals in readiness issues.
- Do not expose deleted actor, deletion reason, or storage health scan details.

## Test Plan For Implementation Phase

Recommended focused tests for Phase 03i-5-2:

| Test Area | Coverage |
| --- | --- |
| Entity/repository | Create/read requirements for a posting. |
| Repository | Count `STORED` attachments by application, type, and section. |
| Repository | `METADATA_ONLY`, `MISSING`, and `DELETED` rows are not counted. |
| Admin API | `GET /admin/job-postings/{jobPostingId}/attachment-requirements` returns sorted list. |
| Admin API | `POST /admin/job-postings/{jobPostingId}/attachment-requirements` replaces all DRAFT posting requirements. |
| Admin API | duplicate type/section request fails. |
| Admin API | invalid `minCount`, blank display name, and missing enum fields fail. |
| Admin API | published posting mutation fails. |
| Public detail | detail exposes safe attachment requirement list. |
| Public list | list keeps only aggregate policy unless explicitly expanded. |
| Public policy | `attachmentRequired=true` when at least one required row exists. |
| Public policy | `ATTACHMENT` section becomes required/optional/disabled according to rows. |
| Dashboard | missing required attachment adds `REQUIRED_ATTACHMENT_MISSING`. |
| Dashboard | stored matching attachment completes the required attachment group. |
| Dashboard | optional attachment does not block `submittable`. |
| Dashboard | optional `minCount=0` rule with no file does not create an optional incomplete issue. |
| Dashboard | optional `minCount>0` rule with no matching file creates an optional incomplete issue but keeps `submittable=true`. |
| Submit | required stored matching attachment allows submit. |
| Submit | missing required attachment fails submit. |
| Submit | optional `minCount>0` missing attachment does not fail submit. |
| Submit | wrong type, wrong section, metadata-only, missing, deleted, and other-application rows fail to satisfy. |
| Lifecycle | deleting a required DRAFT attachment makes dashboard incomplete and submit fail. |
| Exposure | responses and errors do not expose `storagePath`, `storedFileName`, storage root, absolute path, or `physicalFileStatus`. |

Recommended commands after implementation:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*AttachmentRequirement*" --no-daemon
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationSubmitValidatorTest --tests com.shinyoung.recruit.service.ApplicationDashboardServiceTest --no-daemon
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*JobPostingPublic*" --no-daemon
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test --no-daemon
```

Phase 03i-5-1 test result:

| Command | Result | Reason |
| --- | --- | --- |
| Gradle tests | Not run | Documentation-only design phase; no Java/test/config/schema files changed |

## Recommended Implementation Split

Recommended next implementation phase:

```text
Phase 03i-5-2 - Attachment Required Policy Implementation
```

Suggested scope:

1. Add `JobPostingAttachmentRequirement`.
2. Add repository queries for requirements and stored attachment counts.
3. Add admin GET and replace-all POST endpoints for DRAFT postings.
4. Add safe admin/public response DTOs.
5. Add public detail requirement list and public aggregate policy update in the same implementation slice that integrates dashboard and submit validation.
6. Convert `ApplicationFormRequiredPolicyResponse.attachmentRequired` and `ATTACHMENT` section policy from deferred to derived.
7. Integrate `ApplicationCompletionReadChecker`.
8. Integrate `ApplicationSubmitValidator`.
9. Add targeted tests and implementation docs/reports.

If the phase is too large, split it as:

| Phase | Scope |
| --- | --- |
| 03i-5-2a | internal model, repository, admin configure/read only; no public contract that changes applicant expectations |
| 03i-5-2b | public policy/detail exposure, dashboard readiness, and submit validator integration together |

Do not expose public/admin policy that changes applicant expectations before dashboard and submit validator behavior are aligned. Public detail `attachmentRequirements`, `applicationFormRequiredPolicy.attachmentRequired`, dashboard readiness, and submit blocking must ship together in the same externally visible slice.

## Deferred Decisions

- Persistent MariaDB migration mechanism. The implementation document must include manual DDL if no migration tool is active.
- Whether published postings can edit attachment requirements.
- Whether published edits apply to existing draft/submitted applications.
- Whether submitted application status should change if admin deletes required evidence.
- Whether per-rule `active` or versioning is needed.
- Whether per-rule `maxCount` is needed beyond current application-level file limits.
- Whether requirements can depend on applicant type, position, answers, career type, or other conditions.
- Whether row-level `sectionRecordId` requirements are needed.
- Whether a no-file declaration is needed for applicants who cannot provide a required document.
- Whether attachment templates/download forms need a dedicated domain.
- Whether storage health scan should mark broken `STORED` rows as `MISSING` before submit validation relies on them.

## Next Phase Recommendation

Proceed with `Phase 03i-5-2 - Attachment Required Policy Implementation`.

Implement the dedicated `JobPostingAttachmentRequirement` model and keep the first version scoped to `jobPosting + attachmentType + sectionType + required + minCount`. Use replace-all admin configuration for DRAFT postings, expose only safe policy metadata publicly, and integrate dashboard/submit matching against active `STORED` attachment rows.
