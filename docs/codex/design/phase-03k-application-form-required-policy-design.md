# Phase 03k - ApplicationFormConfig Use/Required Policy Split Design

## Phase Name

Phase 03k - ApplicationFormConfig Use/Required Policy Split Design

## Purpose

This phase designs how `ApplicationFormConfig` should split section usage flags from final-submit required flags.

The current implementation has only `useXxx` flags. Those flags currently drive all of these meanings at once:

- whether the applicant screen should show a section,
- whether applicant section save/read APIs are enabled,
- whether the dashboard treats a section as required or optional,
- whether final submit blocks when the section is missing.

This coupling makes it difficult to support postings where a section is visible and editable but not required. Phase 03k defines the target policy, API shape, validation rules, migration/default strategy, and implementation phase split. This is a documentation-only design phase.

## Scope

- Analyze current `ApplicationFormConfig` behavior.
- Define `useXxx` and `requireXxx` responsibilities.
- Recommend new required fields.
- Define backward-compatible request defaults and data backfill.
- Define admin/public response impact.
- Define submit validator and dashboard/readiness changes.
- Define detailed section access policy.
- Define question required policy boundary.
- Decide attachment required-policy handling.
- Define validation, migration, test plan, and a safe implementation phase split.
- Create a paired self-contained HTML report.
- Update implementation history.

## Out-of-Scope Items

- Java source changes.
- Test source changes.
- DB migration file creation.
- Runtime API behavior changes.
- Controller, service, repository, DTO, entity, or enum implementation.
- `application.yml` or properties changes.
- `SecurityConfig` changes.
- QueryDSL introduction.
- New endpoint paths.
- Existing endpoint path changes.
- SMS, email, notification, LDAP, interview, stage-result, Excel, PDF, statistics, or CommonCode implementation.
- Attachment required-policy implementation.

## Changed Files

This design phase changes documentation only.

| Path | Change Type | Notes |
|---|---|---|
| `docs/codex/design/phase-03k-application-form-required-policy-design.md` | New | Codex reference design |
| `docs/codex/reports/phase-03k-application-form-required-policy-design.html` | New | Human-readable self-contained report |
| `docs/codex/07-implementation-history.md` | Modified | Adds Phase 03k design history |

## Current Implementation Summary

### Current `ApplicationFormConfig`

`ApplicationFormConfig` currently stores seven booleans:

| Field | Current Use |
|---|---|
| `useEducation` | enables education section and currently makes education submit-required |
| `useCareer` | enables career section and currently makes career profile/type rules submit-required |
| `useCertificate` | enables certificate section; dashboard treats missing rows as optional guidance |
| `useLanguage` | enables language section; dashboard treats missing rows as optional guidance |
| `useMilitary` | enables military section and currently makes military submit-required |
| `useAward` | enables award section; dashboard treats missing rows as optional guidance |
| `useGapPeriod` | enables gap period section; dashboard treats missing rows as optional guidance |

The current `ApplicationFormConfigRequest`, `ApplicationFormConfigResponse`, and `ApplicationFormConfigPublicResponse` expose only these seven `useXxx` fields.

### Current Submit Validator Policy

`ApplicationSubmitValidator` currently uses `useXxx` as submit-required policy for a subset of sections:

| Section | Current Submit Behavior |
|---|---|
| Education | `useEducation=true` requires at least one education row |
| Career | `useCareer=true` requires `ApplicationCareerProfile`, a selected `CareerType`, and type-specific career row rules |
| Military | `useMilitary=true` requires one military row, subject type, and type-specific required fields |
| Certificate | optional even when `useCertificate=true` |
| Language | optional even when `useLanguage=true` |
| Award | optional even when `useAward=true` |
| GapPeriod | optional even when `useGapPeriod=true` |
| Questions | active `JobPostingQuestion.required=true` questions are submit-required |
| Attachments | no submit-required policy currently exists |

### Current Dashboard/Readiness Policy

`ApplicationCompletionReadChecker` mirrors current submit behavior:

- `useEducation`, `useCareer`, and `useMilitary` create required readiness groups.
- `useCertificate`, `useLanguage`, `useAward`, and `useGapPeriod` create optional guidance groups.
- active required questions create required readiness issues.
- attachments are excluded because there is no attachment config or required policy.

### Current Section Access Policy

`ApplicationSectionAccessService` uses `useXxx` to decide whether applicant detail section APIs are enabled. This is the correct responsibility for `useXxx` and should remain unchanged.

## Design Decision Summary

### Final Recommendation

Add explicit required flags to `ApplicationFormConfig`:

```text
requireEducation
requireCareer
requireCertificate
requireLanguage
requireMilitary
requireAward
requireGapPeriod
```

Keep the existing use flags:

```text
useEducation
useCareer
useCertificate
useLanguage
useMilitary
useAward
useGapPeriod
```

Use this responsibility split:

| Flag Family | Responsibility |
|---|---|
| `useXxx` | section visibility, applicant section API access, admin/public config visibility |
| `requireXxx` | final-submit blocking validation, dashboard required-missing calculation, completion required group calculation |

Do not silently coerce invalid `use=false && require=true` requests. Reject them with a 400-equivalent validation failure in the implementation phase.

### Why This Recommendation

- It preserves the existing screen/access semantics of `useXxx`.
- It lets optional-but-visible sections be represented directly.
- It keeps submit blocking policy explicit and auditable.
- It avoids overloading `JobPostingQuestion.required` or attachment metadata with unrelated section-level rules.
- It enables dashboard to separate required missing sections from optional incomplete sections.

### Review Update: API Contract Safety

Do not expose `requireXxx` in admin/public request or response payloads until the submit validator and dashboard readiness checker also use `requireXxx`.

Unsafe split to avoid:

1. Phase 03k-1 exposes `requireEducation=false` in public detail.
2. Applicant UI treats education as optional.
3. Submit still uses `useEducation=true`.
4. Final submit fails even though the public contract said education was optional.

Therefore the preferred implementation split is to make Phase 03k-1 an end-to-end policy change:

- add and backfill `requireXxx` fields,
- expose `requireXxx` in admin create/update and admin/public detail responses,
- validate `requireXxx -> useXxx`,
- convert `ApplicationSubmitValidator` to `requireXxx`,
- convert `ApplicationCompletionReadChecker` to `requireXxx`,
- keep `ApplicationSectionAccessService` based on `useXxx`.

If implementation must be split smaller, the only safe alternative is an internal-only Phase 03k-1:

- add entity columns and backfill only,
- do not expose `requireXxx` in request/response,
- do not change client-visible semantics,
- then expose request/response and convert submit/dashboard together in Phase 03k-2.

This document recommends the end-to-end Phase 03k-1 path.

## Use/Required Responsibility Split

### `useXxx`

`useXxx` answers whether the section participates in the application form for a posting.

Rules:

- `useXxx=false`: section is hidden from applicant UI.
- `useXxx=false`: applicant section save API remains blocked.
- `useXxx=false`: dashboard excludes the section from both required and optional groups.
- `useXxx=false`: submit validator ignores the section.
- `useXxx=true`: section is visible and writable while the application is otherwise editable.
- `useXxx=true`: admin detail/public detail may present the section config.

### `requireXxx`

`requireXxx` answers whether the section blocks final submission when incomplete.

Rules:

- `requireXxx=true` is valid only when `useXxx=true`.
- `requireXxx=true`: submit validator applies section-specific blocking rules.
- `requireXxx=true`: dashboard places missing/invalid section status in `requiredMissingSections`.
- `requireXxx=true`: section participates in `completionSummary.requiredSectionCount`.
- `requireXxx=false`: missing section data does not block submit.
- `requireXxx=false` with `useXxx=true`: dashboard may place missing/empty section status in `optionalIncompleteSections`.

## Field Proposal

### Entity Fields

Add non-null boolean fields to `ApplicationFormConfig`:

| New Field | Type | Default/Backfill |
|---|---|---|
| `requireEducation` | `boolean` | `useEducation` |
| `requireCareer` | `boolean` | `useCareer` |
| `requireCertificate` | `boolean` | `false` |
| `requireLanguage` | `boolean` | `false` |
| `requireMilitary` | `boolean` | `useMilitary` |
| `requireAward` | `boolean` | `false` |
| `requireGapPeriod` | `boolean` | `false` |

The recommended field names are `requireXxx`, not `requiredXxx`, because the existing field family is `useXxx` and the shorter verb form is consistent and direct.

### Request DTO Fields

Extend `ApplicationFormConfigRequest` with nullable wrapper fields:

```java
Boolean requireEducation
Boolean requireCareer
Boolean requireCertificate
Boolean requireLanguage
Boolean requireMilitary
Boolean requireAward
Boolean requireGapPeriod
```

Use `Boolean` rather than primitive `boolean` in request DTOs so existing clients can omit the new fields and still receive backward-compatible defaults.

### Response DTO Fields

Admin detail and public detail config responses should include both use and require fields:

```text
useEducation
requireEducation
useCareer
requireCareer
useCertificate
requireCertificate
useLanguage
requireLanguage
useMilitary
requireMilitary
useAward
requireAward
useGapPeriod
requireGapPeriod
```

Admin list should not be expanded unless a UI requirement appears. The first implementation should keep required fields in detail responses to avoid overloading list payloads.

## Backward Compatibility and Defaults

Existing admin clients do not send required fields. To preserve current submit behavior:

| New Field When Request Value Is Null | Default |
|---|---|
| `requireEducation` | `useEducation` |
| `requireCareer` | `useCareer` |
| `requireMilitary` | `useMilitary` |
| `requireCertificate` | `false` |
| `requireLanguage` | `false` |
| `requireAward` | `false` |
| `requireGapPeriod` | `false` |

This create-time defaulting preserves the current behavior:

- enabled education remains submit-required,
- enabled career remains submit-required,
- enabled military remains submit-required,
- enabled certificate/language/award/gap period remain optional.

### Create vs Update Null Semantics

Create and update must not use the same null-defaulting policy.

Create behavior:

- If a `requireXxx` request value is null, apply the backward-compatible default table above.
- This preserves behavior for old clients creating new postings.

Update behavior:

- If a `requireXxx` request value is explicitly provided, use that value.
- If a `requireXxx` request value is null and an existing config row exists, preserve the existing `requireXxx` value.
- If the matching `useXxx` is changed to `false`, force the stored `requireXxx` to `false` unless the request explicitly sends `requireXxx=true`, in which case reject the request.
- If the request explicitly sends `useXxx=false && requireXxx=true`, reject it with a 400-equivalent validation failure.

Reason:

- An existing posting may already have `useCareer=true, requireCareer=false`.
- A legacy or partial update client may omit `requireCareer`.
- Reapplying create defaults on update would turn `requireCareer` back into `true` because `useCareer=true`.
- That would silently convert an optional visible section back into a required section.

Update must therefore be preserve-first, while create remains backward-compatible default-first.

## Validation Policy

### Request Validation

Reject these combinations:

| Invalid Combination | Result |
|---|---|
| `useEducation=false && requireEducation=true` | 400 |
| `useCareer=false && requireCareer=true` | 400 |
| `useCertificate=false && requireCertificate=true` | 400 |
| `useLanguage=false && requireLanguage=true` | 400 |
| `useMilitary=false && requireMilitary=true` | 400 |
| `useAward=false && requireAward=true` | 400 |
| `useGapPeriod=false && requireGapPeriod=true` | 400 |

Do not silently convert `requireXxx=true` to `false`; doing so would hide admin configuration mistakes.

### Entity Invariant

Implementation should enforce the same invariant in entity factory/update logic as well as service validation:

```text
requireXxx=true implies useXxx=true
```

Recommended implementation boundary:

- Service validates request semantics and returns user-facing `InvalidJobPostingException` style errors.
- Entity factory/update applies null/default resolution and protects invariants so future internal callers cannot create invalid state.

## API Impact

No endpoint path changes are recommended.

### Admin Create/Update Requests

Existing endpoints remain:

| Method | Path | Impact |
|---|---|---|
| `POST` | `/admin/job-postings` | `applicationFormConfig` accepts optional `requireXxx` fields |
| `POST` | `/admin/job-postings/{id}` | `applicationFormConfig` accepts optional `requireXxx` fields |

Recommended request shape:

```json
{
  "applicationFormConfig": {
    "useEducation": true,
    "requireEducation": true,
    "useCareer": true,
    "requireCareer": false,
    "useCertificate": true,
    "requireCertificate": false,
    "useLanguage": true,
    "requireLanguage": false,
    "useMilitary": true,
    "requireMilitary": true,
    "useAward": true,
    "requireAward": false,
    "useGapPeriod": true,
    "requireGapPeriod": false
  }
}
```

### Admin Detail Response

Existing endpoint:

| Method | Path | Impact |
|---|---|---|
| `GET` | `/admin/job-postings/{id}` | `applicationFormConfig` includes all `useXxx` and `requireXxx` fields |

Admin list should remain unchanged in Phase 03k-1 unless the admin UI needs list-level visibility.

### Public Detail Response

Existing endpoint:

| Method | Path | Impact |
|---|---|---|
| `GET` | `/job-postings/{id}` | `applicationFormConfig` includes all `useXxx` and `requireXxx` fields |

Rationale:

- public detail already includes `applicationFormConfig`,
- applicants need required markers before filling the form,
- public list must continue excluding `applicationFormConfig`.

### Applicant Dashboard Response

Existing endpoint:

| Method | Path | Impact |
|---|---|---|
| `GET` | `/applications/{applicationId:[0-9]+}/dashboard` | readiness groups switch from submit-required `useXxx` assumptions to `requireXxx` in the same implementation phase that exposes `requireXxx` |

Recommended dashboard behavior:

- `requiredMissingSections`: missing/invalid sections where `useXxx=true && requireXxx=true`,
- `optionalIncompleteSections`: incomplete sections where `useXxx=true && requireXxx=false`,
- use-disabled sections are excluded from both groups.

## Submit Validator Impact

`ApplicationSubmitValidator` should switch only blocking checks from `useXxx` to `requireXxx`.

Target behavior:

| Config | Submit Behavior |
|---|---|
| `useXxx=false` | no validation for that section |
| `useXxx=true && requireXxx=false` | section save/read allowed, submit does not require data |
| `useXxx=true && requireXxx=true` | section-specific submit validation runs |

Important distinction:

- section save API access remains `useXxx`-based,
- final submit blocking validation becomes `requireXxx`-based.

### Section-Specific Submit Rules

| Section | When Required | Required Validation |
|---|---|---|
| Education | `useEducation=true && requireEducation=true` | at least one education row |
| Career | `useCareer=true && requireCareer=true` | career profile exists; career type selected; `EXPERIENCED` requires at least one career row; `NEWCOMER`/`NOT_APPLICABLE` disallow career rows |
| Military | `useMilitary=true && requireMilitary=true` | military row exists; subject type selected; `COMPLETED` requires service period; `EXEMPTED` requires exemption reason |
| Certificate | `useCertificate=true && requireCertificate=true` | at least one certificate row |
| Language | `useLanguage=true && requireLanguage=true` | at least one language row |
| Award | `useAward=true && requireAward=true` | at least one award row |
| GapPeriod | `useGapPeriod=true && requireGapPeriod=true` | at least one gap-period row, but this should be used carefully |

For Certificate/Language/Award/GapPeriod, row-level field validity should remain owned by their save APIs. Submit should initially check row existence only unless a saved row can be partially valid in a way not already enforced by the save API.

## Dashboard / Completion Impact

`ApplicationCompletionReadChecker` should mirror the new submit validator policy.

Target grouping:

| Section State | Dashboard Result |
|---|---|
| `useXxx=false` | excluded |
| `useXxx=true && requireXxx=true && incomplete` | `requiredMissingSections` |
| `useXxx=true && requireXxx=true && complete` | completed required group |
| `useXxx=true && requireXxx=false && incomplete` | `optionalIncompleteSections` |
| `useXxx=true && requireXxx=false && complete` | completed optional group |

Recommended count policy:

- `requiredSectionCount` counts required groups only.
- `completedRequiredSectionCount` counts required groups without issues.
- `requiredMissingCount` is `requiredMissingSections.size()`.
- `submitBlockingIssueCount` is `requiredMissingSections.size()`.
- `requiredCompletionRate` remains `100` when there are no required groups.
- `optionalSectionCount` counts enabled optional groups.
- `optionalIncompleteCount` is `optionalIncompleteSections.size()`.

This keeps dashboard `submittable` aligned with final submit.

## Detailed Section Access Impact

`ApplicationSectionAccessService` should continue using only `useXxx`.

Reason:

- `requireXxx=false` means the section is optional, not disabled.
- Applicants must be able to save optional sections.
- Admins may still inspect optional section content.

Target behavior:

| Config | Section Save/Read API |
|---|---|
| `useXxx=false` | blocked for applicant section APIs |
| `useXxx=true && requireXxx=false` | allowed while application is otherwise writable |
| `useXxx=true && requireXxx=true` | allowed while application is otherwise writable |

Admin lazy section reads should continue to read existing section data by application id. If a section is `use=false`, admin behavior can remain read-capable for operational visibility, but applicant save/access should remain blocked.

## Section-by-Section Policy

### Education

| Config | Behavior |
|---|---|
| `useEducation=false` | section hidden; applicant education APIs blocked; submit/dashboard ignore education |
| `useEducation=true && requireEducation=false` | section visible/editable; submit passes without education rows; dashboard may show optional incomplete |
| `useEducation=true && requireEducation=true` | section visible/editable; submit requires at least one education row; dashboard missing goes to required group |

Do not add `requireEducationGrade` in Phase 03k-1. Grade/semester completeness should remain a later decision after reviewing the actual education save validation and UI requirements.

### Career

| Config | Behavior |
|---|---|
| `useCareer=false` | section hidden; applicant career APIs blocked |
| `useCareer=true && requireCareer=false` | section visible/editable; missing career profile does not block submit; saved career data must still pass save validation |
| `useCareer=true && requireCareer=true` | career profile required; selected career type required; `EXPERIENCED` requires rows; `NEWCOMER`/`NOT_APPLICABLE` disallow rows |

`requireCareer` replaces the current `useCareer` submit-blocking role. Save-time career row validation remains unchanged.

### Military

| Config | Behavior |
|---|---|
| `useMilitary=false` | section hidden; applicant military API blocked |
| `useMilitary=true && requireMilitary=false` | section visible/editable; missing military row does not block submit; saved military data must still pass save validation |
| `useMilitary=true && requireMilitary=true` | military row required; subject type required; `COMPLETED` requires service period; `EXEMPTED` requires exemption reason |

### Certificate

| Config | Behavior |
|---|---|
| `useCertificate=false` | section hidden; applicant certificate API blocked |
| `useCertificate=true && requireCertificate=false` | section visible/editable; empty section is optional |
| `useCertificate=true && requireCertificate=true` | at least one certificate row required |

Certificate number/issuer/date validity should remain save API validation. Submit should initially check row existence only.

### Language

| Config | Behavior |
|---|---|
| `useLanguage=false` | section hidden; applicant language API blocked |
| `useLanguage=true && requireLanguage=false` | section visible/editable; empty section is optional |
| `useLanguage=true && requireLanguage=true` | at least one language row required |

Score/grade requiredness should not be expanded in Phase 03k-1 unless the existing save API already guarantees it.

### Award

| Config | Behavior |
|---|---|
| `useAward=false` | section hidden; applicant award API blocked |
| `useAward=true && requireAward=false` | section visible/editable; empty section is optional |
| `useAward=true && requireAward=true` | at least one award row required |

### GapPeriod

| Config | Behavior |
|---|---|
| `useGapPeriod=false` | section hidden; applicant gap-period API blocked |
| `useGapPeriod=true && requireGapPeriod=false` | section visible/editable; empty section is optional |
| `useGapPeriod=true && requireGapPeriod=true` | at least one gap-period row required |

Operational caution:

- Not every applicant has a gap period.
- `requireGapPeriod=true` can force applicants without gaps to invent data unless a "no gap period" declaration model exists.
- Phase 03k should add `requireGapPeriod` for symmetry but document it as a risky operator setting.
- A future `GapPeriodProfile` or `hasGapPeriod` declaration should be considered before making gap period broadly required.

## Question Required Policy Boundary

Do not add question required flags to `ApplicationFormConfig` in Phase 03k.

Boundary:

| Policy | Owner |
|---|---|
| section-level requiredness | `ApplicationFormConfig.requireXxx` |
| question-level requiredness | `JobPostingQuestion.required` |

Current question behavior should remain:

- active required `JobPostingQuestion` records are submit-blocking,
- active optional questions are not submit-blocking unless answer length is invalid,
- inactive questions are ignored by submit/readiness policy.

No `useQuestion`, `requireQuestion`, or question-related `ApplicationFormConfig` field is recommended in Phase 03k.

## Attachment Required Policy Decision

Final recommendation: exclude attachment required policy from Phase 03k implementation and split it into a later phase.

Reason:

- Attachment already has metadata/file-backed lifecycle complexity.
- The correct required unit may be attachment type, section type, min count, or a per-posting attachment policy, not one global boolean.
- A simple `useAttachment`/`requireAttachment` pair would not express which attachment type is required.
- Required attachment policy likely needs its own model.

Recommended later model candidate:

```text
JobPostingAttachmentRequirement
- jobPosting
- attachmentType
- sectionType
- required
- minCount
- maxCount
```

Phase 03k should only document that attachments remain excluded from submit/dashboard requiredness until a dedicated attachment policy phase.

## Migration and Compatibility

### Database Columns

Implementation should add non-null boolean columns:

```text
application_form_config.require_education
application_form_config.require_career
application_form_config.require_certificate
application_form_config.require_language
application_form_config.require_military
application_form_config.require_award
application_form_config.require_gap_period
```

### Backfill

Recommended backfill:

```text
require_education = use_education
require_career = use_career
require_military = use_military
require_certificate = false
require_language = false
require_award = false
require_gap_period = false
```

### DDL Sequence Candidate

For shared DB environments:

1. Add nullable columns or add columns with safe defaults.
2. Backfill from existing `useXxx` values.
3. Add not-null constraints.
4. Deploy application code that reads/writes required fields.
5. Verify admin create/update response compatibility.

If the project still relies on H2 automatic DDL locally, implementation docs must still include separate MariaDB/manual migration notes.

## Entity / DTO / Service / Controller Summary

No class is implemented in this phase. The following changes are recommended for the end-to-end Phase 03k-1 implementation.

| Package | Class | Type | Responsibility | Recommended Change |
|---|---|---|---|---|
| `domain.entity` | `ApplicationFormConfig` | Entity | stores per-posting form policy | add `requireXxx` fields, defaulting, update, invariant |
| `dto.request` | `ApplicationFormConfigRequest` | Request DTO | admin create/update config payload | add nullable `Boolean requireXxx` fields |
| `dto.response` | `ApplicationFormConfigResponse` | Response DTO | admin config response | expose use and require fields |
| `dto.response` | `ApplicationFormConfigPublicResponse` | Response DTO | public detail config response | expose use and require fields |
| `service` | `JobPostingService` | Service | admin posting create/update | resolve create defaults, preserve update values, force disabled-section required flags to false, and validate `require -> use` |
| `service` | `ApplicationSubmitValidator` | Service | final submit validation | switch section blocking checks to `requireXxx` |
| `service` | `ApplicationCompletionReadChecker` | Service | dashboard readiness | split required and optional groups using `requireXxx` |
| `service` | `ApplicationSectionAccessService` | Service | applicant section access | keep using `useXxx`; do not check `requireXxx` |
| `controller` | existing posting/application controllers | Controller | existing endpoints | no path changes; response payload changes only where DTO expands |

## API List

No new endpoint is recommended.

| Method | Path | Phase 03k Impact |
|---|---|---|
| `POST` | `/admin/job-postings` | `applicationFormConfig` accepts optional required flags |
| `POST` | `/admin/job-postings/{id}` | `applicationFormConfig` accepts optional required flags |
| `GET` | `/admin/job-postings/{id}` | detail config returns required flags |
| `GET` | `/job-postings/{id}` | public detail config returns required flags |
| `GET` | `/applications/{applicationId:[0-9]+}/dashboard` | readiness uses required flags in the same phase that exposes required flags |
| `POST` | `/applications/{applicationId}/submit` | submit validator uses required flags in the same phase that exposes required flags |

Public list remains unchanged and must not include `applicationFormConfig`.

## Implementation Phase Split

### Phase 03k-1 - End-to-End Required Policy

Recommended scope:

- add `requireXxx` fields to `ApplicationFormConfig`,
- extend `ApplicationFormConfigRequest`,
- apply create defaults for omitted `requireXxx` values,
- preserve existing `requireXxx` values on update when request values are omitted,
- force `requireXxx=false` when the matching `useXxx` is updated to false,
- reject `use=false && require=true`,
- extend admin detail response config,
- extend public detail response config,
- keep public list unchanged,
- convert `ApplicationSubmitValidator` blocking checks to `requireXxx`,
- convert `ApplicationCompletionReadChecker` required/optional grouping to `requireXxx`,
- keep `ApplicationSectionAccessService` on `useXxx`,
- add focused tests,
- add implementation markdown and HTML report.

Out of scope for 03k-1:

- detailed section access policy changes,
- attachment required policy.

### Phase 03k-2 - Policy Hardening Candidate

Recommended scope if a follow-up phase is still needed:

- extract a shared required/readiness helper if duplication appears after Phase 03k-1,
- add additional edge-case regression tests discovered during frontend integration,
- refine optional incomplete dashboard wording if needed,
- handle deployment migration follow-up if shared DB rollout requires it,
- update implementation docs and report.

Phase 03k-2 must not be the first phase where submit/dashboard semantics catch up to already-exposed `requireXxx` fields. That mismatch is explicitly disallowed.

### Phase 03k-3 Candidate - Attachment Required Policy

Recommended scope:

- design or implement dedicated attachment required policy if needed,
- decide attachment type/section type/min count semantics,
- integrate submit/dashboard only after the policy model is clear.

## Test Plan

This design phase does not add or run tests. Implementation phases should add tests.

### Phase 03k-1 Tests

| Test Area | Coverage |
|---|---|
| Config defaults | omitted required fields preserve current submit behavior defaults |
| Config update | omitted required fields preserve existing `requireXxx` values |
| Config update | changing `useXxx=false` clears matching `requireXxx` |
| Validation | `requireXxx=true && useXxx=false` fails on create |
| Validation | same invalid combination fails on update |
| Admin detail | response includes all `requireXxx` fields |
| Public detail | `applicationFormConfig` includes all `requireXxx` fields |
| Public list | still excludes `applicationFormConfig` |
| Entity/service | update mutates existing config and keeps invariant |
| Education optional | `useEducation=true, requireEducation=false` submits without education rows |
| Education required | `useEducation=true, requireEducation=true` fails without rows |
| Career optional | `useCareer=true, requireCareer=false` submits without career profile |
| Career required | current career profile/type/row rules apply when required |
| Military optional | `useMilitary=true, requireMilitary=false` submits without military row |
| Military required | current military row/type/period/reason rules apply when required |
| Certificate required | at least one row required when `requireCertificate=true` |
| Language required | at least one row required when `requireLanguage=true` |
| Award required | at least one row required when `requireAward=true` |
| GapPeriod required | at least one row required when `requireGapPeriod=true` |
| Disabled sections | `useXxx=false` sections are ignored by submit |
| Questions | active required question behavior remains unchanged |
| Required missing | missing required section appears in `requiredMissingSections` |
| Optional incomplete | missing optional enabled section appears in `optionalIncompleteSections` |
| Disabled section | disabled section is absent from both lists |
| Submittable | true only when required missing count is zero |
| Completion rate | required group count and rate use `requireXxx` |
| Questions | required question behavior remains in required group |

### Section Access Regression Tests

| Test Area | Coverage |
|---|---|
| Optional section save | `useXxx=true && requireXxx=false` still allows save |
| Required section save | `useXxx=true && requireXxx=true` allows save |
| Disabled section save | `useXxx=false` remains blocked |

## Test Commands

Not executed in this phase because this is documentation-only.

Recommended Phase 03k-1 targeted commands:

```powershell
$env:AES_SECRET_KEY='<test-value>'; .\gradlew.bat test --tests com.shinyoung.recruit.service.JobPostingServiceTest --tests com.shinyoung.recruit.controller.JobPostingControllerTest --tests com.shinyoung.recruit.service.JobPostingPublicServiceTest --tests com.shinyoung.recruit.controller.JobPostingPublicControllerTest --tests com.shinyoung.recruit.service.ApplicationSubmitValidatorTest --tests com.shinyoung.recruit.service.ApplicationDashboardServiceTest --tests com.shinyoung.recruit.controller.ApplicationControllerTest --no-daemon
```

Recommended Phase 03k-2 hardening commands, if a follow-up phase is created:

```powershell
$env:AES_SECRET_KEY='<test-value>'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationSubmitValidatorTest --tests com.shinyoung.recruit.service.ApplicationDashboardServiceTest --tests com.shinyoung.recruit.controller.ApplicationControllerTest --no-daemon
```

Recommended full regression:

```powershell
$env:AES_SECRET_KEY='<test-value>'; .\gradlew.bat clean test --no-daemon
```

## Test Results

- Not executed.
- Reason: documentation-only design phase with no Java source, test source, schema, or runtime API behavior changes.

## Remaining Issues

- Exact migration mechanism for MariaDB is still undecided.
- `requireGapPeriod=true` is semantically risky without an explicit "no gap period" declaration model.
- Education grade/semester requiredness remains a later policy decision.
- Language score/grade requiredness remains a later policy decision.
- Attachment requiredness is deferred to a dedicated attachment policy phase.
- A shared read/write readiness abstraction may be useful later, but should wait until `requireXxx` behavior stabilizes.

## Next Phase Recommendation

Proceed with Phase 03k-1:

- add `requireXxx` fields to `ApplicationFormConfig`,
- apply create defaults and update preserve semantics,
- extend admin/public detail config responses,
- validate `requireXxx -> useXxx`,
- convert submit validator to `requireXxx`,
- convert dashboard readiness to `requireXxx`,
- keep section access on `useXxx`.

Do not ship a phase where public/admin responses expose `requireXxx=false` while final submit still blocks on `useXxx=true`.
