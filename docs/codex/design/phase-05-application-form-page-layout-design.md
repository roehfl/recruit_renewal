# Phase 05 - Application Form Page Layout Design

## Phase Name

Phase 05 - Application Form Page Layout Design

## Phase Summary

- Date: 2026-05-26
- Work type: documentation-only design phase.
- Result: backend page-layout domain, API, validation, fallback, publish guard, and slice breakdown are defined.
- Implementation status: this design phase itself added no Java/runtime behavior; Phase 05a later implemented the layout domain foundation in `docs/codex/implementation/phase-05a-application-form-layout-domain.md`.
- Review update: question/answer placement, BASIC_INFO save API contract, targeted-test guidance, and enum compatibility cautions are reflected.

## Purpose

Design the backend domain and API contract for arranging the applicant application form by page and section.

This phase does not implement Java code. It defines how the existing `ApplicationFormConfig.useXxx` and `ApplicationFormConfig.requireXxx` policy, the existing attachment requirement policy, and a new page layout model should work together without changing the actual application data model or adding a field-level form builder.

## Scope

- Add a design for `ApplicationFormPage`.
- Add a design for `ApplicationFormPageItem`.
- Reuse and extend `ApplicationSectionType` for page-layout section codes.
- Define the relationship between `useXxx`, `requireXxx`, attachment requirements, and layout placement.
- Define admin layout read/save/preview APIs.
- Define applicant layout read API.
- Define backend validation rules and HTTP error policy.
- Define default layout, fallback, and publish/reception-start guard policy.
- Define implementation slices from 05a through 05e.
- Create a paired human-readable HTML report.
- Update roadmap/history/current status documentation to reflect the new phase sequence.

## Out-of-Scope Items

- Java source implementation.
- Test source implementation.
- Production DB migration files.
- Frontend Vue implementation.
- Drag and drop UI.
- Pinia store implementation.
- Page-level application save API such as `PUT /applications/{id}/pages/{pageNo}`.
- Field-level form builder.
- Per-field required configuration.
- Application section command API refactoring.
- Attachment required policy redesign.
- Application required policy redesign.
- Persistent DB migration execution.
- Static frontend build artifacts.

## Changed Files

| Path | Change Type | Notes |
| --- | --- | --- |
| `docs/codex/design/phase-05-application-form-page-layout-design.md` | New | Codex reference design for Phase 05. |
| `docs/codex/reports/phase-05-application-form-page-layout-design.html` | New | Self-contained human-readable report generated from this design. |
| `docs/codex/06-implementation-roadmap.md` | Modified | Updates the next numbered phase sequence. |
| `docs/codex/07-implementation-history.md` | Modified | Adds Phase 05 design history and current snapshot updates. |
| `docs/codex/reports/current-implementation-status.html` | Modified | Updates human-readable current status and remaining-work sequence. |

## Background

The application form currently has section-level APIs and section-level readiness/submit rules. The frontend now needs a page-oriented layout contract so it can render multiple sections on one page and provide a page navigator.

The backend should provide only stable business section codes such as `EDUCATION` and `CAREER`. It must not store Vue component names, frontend file names, field names, or pixel/height layout data.

The existing implementation already has three important policy sources:

- `ApplicationFormConfig`: owns section usage and final-submit required flags for education, career, certificate, language, military, award, and gap period.
- `JobPostingQuestion`: owns question visibility and question-level required flags.
- `JobPostingAttachmentRequirement`: owns attachment requirement rows and public/dashboard/submit attachment policy.

The new page layout model should sit beside those policies. It decides where a section appears, not whether the section is enabled or required.

## Existing ApplicationFormConfig Relationship

`ApplicationFormConfig` remains the source of truth for these flags:

```text
useEducation
useCareer
useCertificate
useLanguage
useMilitary
useAward
useGapPeriod
requireEducation
requireCareer
requireCertificate
requireLanguage
requireMilitary
requireAward
requireGapPeriod
```

Responsibility split:

| Policy | Owner | Meaning |
| --- | --- | --- |
| section enabled | `ApplicationFormConfig.useXxx` | Applicant can see and use the section API. |
| section required | `ApplicationFormConfig.requireXxx` | Final submit/dashboard required readiness uses the section. |
| section placement | `ApplicationFormPageItem.sectionType` | Applicant UI page where the enabled section appears. |

`ApplicationFormPageItem` must not store `required`. Requiredness is calculated from `ApplicationFormConfig` or `JobPostingAttachmentRequirement` when responses are built.

## New Domain Candidates

### ApplicationFormPage

Recommended package:

```text
com.shinyoung.recruit.domain.entity
```

Recommended fields:

| Field | Type | Rule |
| --- | --- | --- |
| `id` | `Long` | Primary key. |
| `jobPosting` | `JobPosting` | `ManyToOne(fetch = LAZY)`, required. |
| `pageNo` | `Integer` | Applicant-facing page number, unique per posting. |
| `title` | `String` | Required, trimmed, max length candidate 100. |
| `description` | `String` | Optional, trimmed, max length candidate 500. |
| `sortOrder` | `Integer` | Required, unique per posting. |
| `items` | `List<ApplicationFormPageItem>` | Page sections, ordered by `sortOrder`. |
| audit fields | `BaseEntity` | `createdAt`, `updatedAt`, `createdBy`, `updatedBy`. |

Relationship decision:

- Connect `ApplicationFormPage` directly to `JobPosting`.
- Reason: current `ApplicationFormConfig` is a one-to-one child of `JobPosting`, but attachment requirements are separate from `ApplicationFormConfig`. A direct `JobPosting` owner lets the layout cover basic info, form-config sections, and attachments under one owner.

### ApplicationFormPageItem

Recommended fields:

| Field | Type | Rule |
| --- | --- | --- |
| `id` | `Long` | Primary key. |
| `page` | `ApplicationFormPage` | `ManyToOne(fetch = LAZY)`, required. |
| `sectionType` | `ApplicationSectionType` | Required enum value. |
| `sortOrder` | `Integer` | Required, unique within the page. |
| audit fields | `BaseEntity` | `createdAt`, `updatedAt`, `createdBy`, `updatedBy`. |

Forbidden fields:

- `required`
- Vue component name
- frontend file name
- field-level configuration
- height, width, x/y position, column span, pixel values

## ApplicationSectionType Policy

The project already has `ApplicationSectionType`:

```text
APPLICATION
EDUCATION
CAREER
CERTIFICATE
LANGUAGE
MILITARY
AWARD
GAP_PERIOD
ETC
```

Recommended approach:

- Reuse the existing enum.
- Add `BASIC_INFO`, `QUESTION_ANSWER`, and `ATTACHMENT`.
- Keep existing values for attachment metadata compatibility.
- Layout validation should allow only the layout section subset.

Current code check:

- `ApplicationSectionType` does not currently include `ATTACHMENT`.
- Existing required-policy responses use string section codes such as `QUESTION` and `ATTACHMENT` outside this enum.
- 05a must first check whether another branch has already added `ATTACHMENT` or a question-related value before editing the enum. If one already exists, reuse it instead of adding a duplicate.

Layout section subset:

```text
BASIC_INFO
MILITARY
EDUCATION
CAREER
CERTIFICATE
LANGUAGE
AWARD
GAP_PERIOD
QUESTION_ANSWER
ATTACHMENT
```

`APPLICATION` and `ETC` should not be accepted in `ApplicationFormPageItem` in the first implementation. `APPLICATION` can remain for existing attachment metadata where it means a general application-level attachment section.

Section display names are server-calculated response fields, not stored in the layout item.

## Use / Require / Layout Consistency Rules

Define an effective enabled section set for a job posting:

```text
effectiveEnabledSections =
  BASIC_INFO
  + ApplicationFormConfig use=true sections
  + QUESTION_ANSWER when the JobPosting has at least one active question
  + ATTACHMENT when at least one JobPostingAttachmentRequirement row exists
```

Define an effective required section set:

```text
effectiveRequiredSections =
  BASIC_INFO
  + ApplicationFormConfig sections where use=true and require=true
  + QUESTION_ANSWER when the JobPosting has at least one active required question
  + ATTACHMENT when at least one required JobPostingAttachmentRequirement row exists
```

Rules:

1. A layout must contain exactly the `effectiveEnabledSections` set.
2. `BASIC_INFO` is always enabled and required.
3. `QUESTION_ANSWER` is enabled when at least one active `JobPostingQuestion` exists.
4. `QUESTION_ANSWER` is required when at least one active `JobPostingQuestion.required=true` exists.
5. `ATTACHMENT` is enabled when attachment requirement rows exist.
6. `ATTACHMENT` is required when any attachment requirement row has `required=true`.
7. A disabled section cannot be placed.
8. An enabled section must be placed exactly once.
9. A required section must be enabled and placed.
10. Layout item requiredness is response-only derived data.
11. Changing `useXxx=false` makes the matching section invalid in layout.
12. Changing `useXxx=true` requires the matching section to be present in layout before save/publish succeeds.

This preserves the invariant:

```text
effectiveEnabledSections == all sectionType values placed in the layout
```

## Admin API Design

Recommended endpoints:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/admin/job-postings/{jobPostingId}/application-form-layout` | Read stored layout or default/fallback layout plus available sections. |
| `PUT` | `/admin/job-postings/{jobPostingId}/application-form-layout` | Replace the full layout. |
| `GET` | `/admin/job-postings/{jobPostingId}/application-form-layout/preview` | Read the applicant-facing preview projection. |

If the codebase keeps POST-style command updates, implementation may use:

```text
POST /admin/job-postings/{jobPostingId}/application-form-layout
```

The design preference is replace-all semantics regardless of HTTP verb.

## Admin Layout Response

Candidate response:

```json
{
  "jobPostingId": 1,
  "layoutStored": true,
  "editable": true,
  "pages": [
    {
      "pageNo": 1,
      "title": "Basic information",
      "description": "Applicant profile and military section.",
      "sortOrder": 1,
      "items": [
        {
          "sectionType": "BASIC_INFO",
          "sectionName": "Basic information",
          "sortOrder": 1,
          "enabled": true,
          "required": true,
          "placed": true
        }
      ]
    }
  ],
  "availableSections": [
    {
      "sectionType": "EDUCATION",
      "sectionName": "Education",
      "enabled": true,
      "required": true,
      "placed": true,
      "source": "APPLICATION_FORM_CONFIG"
    },
    {
      "sectionType": "ATTACHMENT",
      "sectionName": "Attachments",
      "enabled": false,
      "required": false,
      "placed": false,
      "source": "ATTACHMENT_REQUIREMENT"
    }
  ]
}
```

`availableSections` lets the admin UI show disabled sections without allowing placement.

Do not expose storage paths, stored file names, encryption keys, LDAP values, DB credentials, or applicant personal data.

## Admin Layout Save Request

Candidate request:

```json
{
  "pages": [
    {
      "pageNo": 1,
      "title": "Basic information",
      "description": "Applicant profile and military section.",
      "sortOrder": 1,
      "items": [
        {
          "sectionType": "BASIC_INFO",
          "sortOrder": 1
        },
        {
          "sectionType": "MILITARY",
          "sortOrder": 2
        }
      ]
    },
    {
      "pageNo": 2,
      "title": "Education and career",
      "description": "Education and career history.",
      "sortOrder": 2,
      "items": [
        {
          "sectionType": "EDUCATION",
          "sortOrder": 1
        },
        {
          "sectionType": "CAREER",
          "sortOrder": 2
        }
      ]
    }
  ]
}
```

Request must not accept:

- `enabled`
- `required`
- `sectionName`
- Vue component name
- field-level settings

Those values are server-derived or frontend-owned.

## Applicant Layout Read API Design

Recommended endpoint:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/applicant/applications/{applicationId}/form-layout` | Read applicant-owned application layout. |

Recommended behavior:

- Resolve the current authenticated applicant.
- Verify the `applicationId` belongs to that applicant.
- Return 404 for non-owned applications.
- Follow existing application read visibility for withdrawn/closed cases.
- Return only enabled sections.
- Do not return admin-only available section lists.
- Do not expose disabled sections.
- Do not expose storage internals or posting admin configuration internals.

Candidate response:

```json
{
  "applicationId": 100,
  "jobPostingId": 1,
  "pages": [
    {
      "pageNo": 1,
      "title": "Basic information",
      "description": "Applicant profile and military section.",
      "sortOrder": 1,
      "items": [
        {
          "sectionType": "BASIC_INFO",
          "sectionName": "Basic information",
          "required": true,
          "sortOrder": 1
        }
      ]
    }
  ]
}
```

## Relationship With Existing Section Save APIs

Page layout is only the screen arrangement contract.

Application data saves remain owned by existing section command APIs:

| Section | Existing API Family |
| --- | --- |
| Basic info | `POST /applications/{applicationId}` using the current root draft update contract |
| Education | `/applications/{applicationId}/educations` |
| Career | `/applications/{applicationId}/careers` |
| Certificate | `/applications/{applicationId}/certificates` |
| Language | `/applications/{applicationId}/languages` |
| Military | `/applications/{applicationId}/military` |
| Award | `/applications/{applicationId}/awards` |
| Gap period | `/applications/{applicationId}/gap-periods` |
| Question/answer | `GET /applications/{applicationId}/questions`, `POST /applications/{applicationId}/answers` |
| Attachments | `/applications/{applicationId}/attachments` and file endpoints |

The frontend may call multiple section APIs when a page has multiple sections. A page-level save API is deferred until there is a clear product need.

`BASIC_INFO` is backed by the current root application draft update endpoint. At the time of this design, `ApplicationUpdateRequest` contains `jobPositionId`, so this endpoint updates the application root selection rather than a broad applicant personal profile. If Phase 05 needs more basic-info fields, implementation must either extend the existing root update contract or add a dedicated endpoint such as `POST /applications/{applicationId}/basic-info`. The layout response must not expose `BASIC_INFO` without a clear save contract.

## Reason Page-Level Save API Is Excluded

The first layout phase must not add:

```text
PUT /applications/{applicationId}/pages/{pageNo}
```

Reasons:

- Existing section services already own validation and persistence.
- A page payload would need a polymorphic body for unrelated section DTOs.
- Error mapping would become harder because one page can include multiple independent domains.
- It would refactor working section command APIs without changing business capability.

The page layout API is a read/rendering contract. Section command APIs remain the write contract.

## Attachment Required Policy Relationship

`ATTACHMENT` does not use `ApplicationFormConfig`.

Rules:

- At least one `JobPostingAttachmentRequirement` row means `ATTACHMENT` is enabled for layout.
- At least one required `JobPostingAttachmentRequirement` row means `ATTACHMENT` is required in responses.
- Optional attachment rows with `minCount=0` are guidance-only, but still make the attachment section placeable and visible when the posting has explicit attachment policy rows.
- `ApplicationFormPageItem` must not duplicate attachment required flags.
- Attachment submit/dashboard behavior remains owned by `ApplicationCompletionReadChecker` and `ApplicationSubmitValidator`.

If a posting has no attachment requirement rows, `ATTACHMENT` is disabled and must not appear in layout.

## Question / Answer Policy Relationship

`QUESTION_ANSWER` is part of the Phase 05 page layout.

Rules:

- At least one active `JobPostingQuestion` means `QUESTION_ANSWER` is enabled for layout.
- At least one active required `JobPostingQuestion` means `QUESTION_ANSWER` is required in responses.
- `ApplicationFormPageItem` must not duplicate question required flags.
- Question submit/dashboard behavior remains owned by the existing answer submit validator and completion checker.
- Question save remains owned by `POST /applications/{applicationId}/answers`.

If a posting has no active question rows, `QUESTION_ANSWER` is disabled and must not appear in layout.

## Application Required Policy Relationship

The existing application required policy remains the final-submit authority.

Rules:

- Layout cannot make a section required.
- Layout cannot make a section optional.
- Required dashboard/submit behavior remains derived from `ApplicationFormConfig.requireXxx`, required questions, and `JobPostingAttachmentRequirement`.
- Layout validation only checks that required/enabled sections are placed exactly once.

This avoids conflicting policies between page arrangement and submit readiness.

## Job Posting State Edit Policy

Recommended first policy:

| Posting State / Time | Allowed Layout Mutation |
| --- | --- |
| `DRAFT` and before reception start | Full page/item structure edit. |
| `PUBLISHED` but before reception start | Page title/description edit may be allowed; structure mutation should be blocked unless a later explicit policy exists. |
| Reception started or later | Page/item structure mutation blocked. |
| `CLOSED` | Mutation blocked. |

The implementation should use `JobPosting.receptionStartDateTime` and `Clock`.

Recommended error:

- Use existing business exception style.
- Return 400 if the project keeps business-rule violations as bad request.
- 409 Conflict is semantically valid for state conflicts, but current project convention should decide.

## Default Layout and Fallback Policy

Recommended policy:

- New postings: create a default layout from `effectiveEnabledSections` when `ApplicationFormConfig` and attachment requirements are saved, if feasible in the implementation slice.
- Existing postings without layout: admin/applicant reads may return a deterministic fallback layout.
- Publish guard: publish must require either stored valid layout or deterministic valid fallback in the first implementation; after migration, publish should require stored valid layout.

Default grouping candidate:

| Page | Sections |
| --- | --- |
| 1 | `BASIC_INFO`, `MILITARY` when enabled |
| 2 | `EDUCATION`, `CAREER` when enabled |
| 3 | `CERTIFICATE`, `LANGUAGE`, `AWARD`, `GAP_PERIOD` when enabled |
| 4 | `QUESTION_ANSWER` when enabled |
| 5 | `ATTACHMENT` when enabled |

Empty pages must be removed from the generated layout.

## Publish Guard Policy

Validate layout at these points:

1. Layout save.
2. Job posting publish.
3. Application applicant layout read, using stored layout or deterministic fallback.

Publish should fail if:

- no page exists,
- a page has no items,
- duplicate page numbers exist,
- duplicate page sort orders exist,
- duplicate item sort orders exist within a page,
- duplicate section types exist across the full layout,
- any disabled section is placed,
- any enabled section is missing,
- required section is missing,
- unsupported layout enum value is used.

## Validation Rule List

Validation must include:

1. At least one page.
2. Each page has at least one item.
3. `pageNo` is not null and not duplicated.
4. `pageNo` is greater than zero.
5. page `sortOrder` is not null and not duplicated.
6. page `sortOrder` is zero or greater.
7. page `title` is nonblank after trimming.
8. page `title` max length is 100.
9. page `description` max length is 500 when present.
10. item `sectionType` is not null.
11. item `sectionType` is one of the allowed layout section subset values.
12. item `sortOrder` is not null.
13. item `sortOrder` is zero or greater.
14. item `sortOrder` is unique within a page.
15. section type is unique across the full layout.
16. disabled section cannot be placed.
17. enabled section must be placed.
18. required section must be placed.
19. `requireXxx=true` must still imply `useXxx=true`; this remains protected by `ApplicationFormConfig`.
20. layout structure mutation is blocked after reception start.

## Exception and HTTP Status Candidates

| Case | Recommended Status |
| --- | --- |
| Job posting not found | 404 |
| Application not found | 404 |
| Non-owned applicant application | 404 |
| Invalid layout shape | 400 |
| Disabled section placed | 400 |
| Enabled section missing | 400 |
| Required section missing | 400 |
| Reception started before structure mutation | 400 or 409, following project convention |
| Unsupported enum value | 400 |

The current project generally maps business validation to `ApiResponse.fail(...)` through existing exceptions. The first implementation should follow that convention instead of introducing a new response envelope.

## DB Table Candidate

### `application_form_page`

```text
id
job_posting_id
page_no
title
description
sort_order
created_at
updated_at
created_by
updated_by
```

Recommended constraints/indexes:

```text
unique(job_posting_id, page_no)
unique(job_posting_id, sort_order)
index(job_posting_id)
```

### `application_form_page_item`

```text
id
page_id
section_type
sort_order
created_at
updated_at
created_by
updated_by
```

Recommended constraints/indexes:

```text
unique(page_id, sort_order)
index(page_id)
```

Full-layout section uniqueness needs service validation because it spans multiple pages. A DB constraint can support this only if the item table also stores `job_posting_id`, which is not recommended for the first model because it duplicates ownership.

## Repository, Service, Controller Candidates

| Layer | Class | Responsibility |
| --- | --- | --- |
| Repository | `ApplicationFormPageRepository` | Load pages with items by job posting id; delete/replace layout. |
| Repository | `ApplicationFormPageItemRepository` | Usually optional if page aggregate replacement is handled through page repository cascade. |
| Service | `ApplicationFormLayoutService` | Admin/applicant layout reads, replace-all save, preview projection. |
| Service | `ApplicationFormLayoutValidator` | Validate shape, section consistency, and state guard. |
| Service | `ApplicationFormLayoutDefaultFactory` | Build deterministic default/fallback layouts. |
| Controller | `AdminApplicationFormLayoutController` | Admin read/save/preview APIs. |
| Controller | `ApplicantApplicationFormLayoutController` | Applicant-owned application layout read API. |

## DTO Candidates

Request DTOs:

```text
ApplicationFormLayoutSaveRequest
ApplicationFormLayoutPageRequest
ApplicationFormLayoutItemRequest
```

Admin response DTOs:

```text
AdminApplicationFormLayoutResponse
AdminApplicationFormLayoutPageResponse
AdminApplicationFormLayoutItemResponse
ApplicationSectionAvailabilityResponse
```

Applicant response DTOs:

```text
ApplicantApplicationFormLayoutResponse
ApplicantApplicationFormLayoutPageResponse
ApplicantApplicationFormLayoutItemResponse
```

Response item fields:

| Field | Admin | Applicant |
| --- | --- | --- |
| `sectionType` | yes | yes |
| `sectionName` | yes | yes |
| `sortOrder` | yes | yes |
| `enabled` | yes | no |
| `required` | yes | yes |
| `placed` | yes | no |
| `source` | yes | no |

## Test Strategy

Implementation phases should add focused tests:

| Area | Coverage |
| --- | --- |
| Entity | page/item creation, ownership, ordering. |
| Validator | empty pages/items, duplicate pageNo, duplicate page sortOrder, duplicate item sortOrder. |
| Validator | disabled section placed. |
| Validator | enabled section missing. |
| Validator | duplicate sectionType across pages. |
| Validator | required section missing. |
| Validator | unsupported layout enum values rejected by request binding or validation. |
| Default factory | creates pages only with enabled sections and removes empty pages. |
| Admin service | get fallback, save valid layout, block invalid layout. |
| Admin service | block structure mutation after reception start. |
| Admin controller | admin response includes available sections. |
| Applicant service | ownership guard. |
| Applicant service | disabled sections hidden. |
| Applicant controller | applicant response excludes admin-only metadata. |
| Publish guard | invalid layout blocks publish. |
| Attachment policy regression | attachment required policy remains source of requiredness. |
| Application policy regression | submit/dashboard required policy remains source of requiredness. |
| Question policy regression | active/required questions drive `QUESTION_ANSWER` enabled/required response values. |

## Test Commands

Recommended targeted commands after implementation:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461261067'; .\gradlew.bat test --tests "*ApplicationFormLayout*" --no-daemon
```

Recommended regression commands:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.JobPostingServiceTest --tests com.shinyoung.recruit.service.ApplicationSubmitValidatorTest --tests com.shinyoung.recruit.service.ApplicationDashboardServiceTest --tests "*AttachmentRequirement*" --no-daemon
```

Full `clean test` is not required by default for Phase 05 implementation slices because the current development PC has full-suite timeout issues. Run targeted tests for modified packages only. Full-suite verification can be scheduled separately as a release/regression check when local execution constraints allow it.

## Test Results

Not executed.

Reason: documentation-only design phase. No Java source, test source, runtime configuration, schema, or API behavior was changed.

## Slice Breakdown

### 05a - Application Form Layout Domain

- Extend or prepare `ApplicationSectionType` for layout section codes.
- Confirm whether `ATTACHMENT` already exists in `ApplicationSectionType`; if it exists by then, reuse it.
- Confirm existing `APPLICATION` attachment metadata semantics and do not use it as the layout attachment section.
- Add `ApplicationFormPage`.
- Add `ApplicationFormPageItem`.
- Add repositories.
- Add entity/repository tests.
- Add validation helper for section set consistency.

### 05b - Admin Layout Management

- Add admin read/save APIs.
- Add `availableSections` response.
- Enforce full layout validation.
- Enforce reception-start structure mutation guard.
- Add admin controller/service tests.

### 05c - Applicant Layout Read

- Add applicant-owned layout read API.
- Reuse current applicant ownership guard.
- Return only enabled/placed sections.
- Hide admin-only availability and disabled section data.
- Add service/controller tests.

### 05d - Publish/Layout Guard Integration

- Validate layout on publish.
- Decide stored layout versus fallback acceptance.
- Integrate default layout generation if needed.
- Add publish guard regression tests.

### 05e - Layout Stabilization / Test Hardening

- Harden validation matrix.
- Cover existing posting fallback edge cases.
- Verify attachment/application required policy does not drift.
- Update docs/report after implementation.

## Explicit Non-Goals For This Design

- Do not design a height-based auto-placement algorithm.
- Do not design field-level form builder metadata.
- Do not store Vue component names.
- Do not create frontend implementation files.
- Do not create static resources.
- Do not add page-level save API.
- Do not change existing section data persistence.
- Do not change final-submit required policy ownership.
- Do not redesign attachment requirements.

## Frontend Contract

The backend provides:

- `sectionType`
- `sectionName`
- `pageNo`
- page `title`
- page `description`
- section `required`
- section `sortOrder`
- admin-only `enabled` and `placed`

The frontend owns:

- Vue component mapping.
- page navigation UI.
- drag and drop editor UI.
- section component implementation.
- frontend store shape.
- per-page save orchestration using existing section APIs.

Example frontend-only mapping:

```javascript
const sectionComponentMap = {
  BASIC_INFO: BasicInfoSection,
  MILITARY: MilitarySection,
  EDUCATION: EducationSection,
  CAREER: CareerSection,
  CERTIFICATE: CertificateSection,
  LANGUAGE: LanguageSection,
  AWARD: AwardSection,
  GAP_PERIOD: GapPeriodSection,
  QUESTION_ANSWER: QuestionAnswerSection,
  ATTACHMENT: AttachmentSection
}
```

This mapping must not be persisted or returned by the backend.

## Open Questions and Recommended Decisions

1. Should `ApplicationFormPage` connect to `JobPosting` or `ApplicationFormConfig`?
   - Recommendation: connect to `JobPosting` because attachments are not owned by `ApplicationFormConfig`.
2. Should `BASIC_INFO` be always required?
   - Recommendation: yes. It is the root applicant/application identity page section.
3. Should `ATTACHMENT` be part of `ApplicationFormConfig`?
   - Recommendation: no. Use `JobPostingAttachmentRequirement`.
4. Should new postings auto-create default layout?
   - Recommendation: yes if feasible in 05b or 05d; otherwise reads should provide deterministic fallback until migration is complete.
5. Should existing postings without layout return fallback?
   - Recommendation: yes for compatibility.
6. Should publish require stored layout?
   - Recommendation: initially accept valid fallback only for legacy postings; require stored layout after migration.
7. Should page title/description be editable after reception starts?
   - Recommendation: allow only if product needs copy correction; block page/item structure changes.
8. Should `APPLICATION` enum be accepted as layout basic info?
   - Recommendation: no. Add `BASIC_INFO` and keep `APPLICATION` for current attachment metadata compatibility.
9. Should question/answer be outside page layout as a separate applicant answer screen?
   - Recommendation: no. Include `QUESTION_ANSWER` in page layout so the full applicant application form can be arranged by pages.

## API List

| Method | Path | Purpose | Status |
| --- | --- | --- | --- |
| `GET` | `/admin/job-postings/{jobPostingId}/application-form-layout` | Admin layout read with available sections. | Designed |
| `PUT` or `POST` | `/admin/job-postings/{jobPostingId}/application-form-layout` | Replace full layout. | Designed |
| `GET` | `/admin/job-postings/{jobPostingId}/application-form-layout/preview` | Admin applicant-facing preview projection. | Designed |
| `GET` | `/applicant/applications/{applicationId}/form-layout` | Applicant-owned layout read. | Designed |

No runtime API is implemented in this phase.

## Entity / DTO / Service / Controller Summary

| Layer | Candidate | Type | Responsibility |
| --- | --- | --- | --- |
| `domain.entity` | `ApplicationFormPage` | Entity | Stores page metadata for one job posting. |
| `domain.entity` | `ApplicationFormPageItem` | Entity | Stores one section placement inside a page. |
| `enumeration` | `ApplicationSectionType` | Enum | Reused and extended section code enum. |
| `domain.repository` | `ApplicationFormPageRepository` | Repository | Loads and replaces page layout by posting. |
| `service` | `ApplicationFormLayoutService` | Service | Admin/applicant layout operations. |
| `service` | `ApplicationFormLayoutValidator` | Service/helper | Validates layout shape and policy consistency. |
| `service` | `ApplicationFormLayoutDefaultFactory` | Service/helper | Builds default and fallback layout. |
| `controller` | `AdminApplicationFormLayoutController` | Controller | Admin layout APIs. |
| `controller` | `ApplicantApplicationFormLayoutController` | Controller | Applicant layout read API. |
| `dto.request` | `ApplicationFormLayoutSaveRequest` | Request DTO | Replace-all layout save payload. |
| `dto.response` | `AdminApplicationFormLayoutResponse` | Response DTO | Admin layout and available section payload. |
| `dto.response` | `ApplicantApplicationFormLayoutResponse` | Response DTO | Applicant-safe layout payload. |

## Remaining Issues

- Persistent MariaDB migration mechanism is still manual in this project.
- Existing postings without stored layout need a migration or stable fallback policy.
- `ApplicationSectionType.APPLICATION` is already used for attachments and needs careful compatibility handling when adding `BASIC_INFO`.
- Phase 05a added `BASIC_INFO`, `QUESTION_ANSWER`, and `ATTACHMENT` to the current Java `ApplicationSectionType` and kept `APPLICATION`/`ETC` outside the layout subset.
- Published posting layout amendment policy may need a future audit/versioning model.
- Page title/description mutation after reception start needs product confirmation.
- Page-level save API remains deferred.

## Next Phase Recommendation

Phase 05a is complete. Proceed with `Phase 05b - Admin Layout Management`.

Keep 05b focused on admin layout read/save/preview APIs, available-section responses, validator integration, and exception mapping. Applicant read remains Phase 05c.
