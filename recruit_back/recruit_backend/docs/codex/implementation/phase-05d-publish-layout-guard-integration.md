# Phase 05d - Publish/Layout Guard Integration

## Phase Summary

- Phase name: Phase 05d - Publish/Layout Guard Integration
- Date: 2026-05-27
- Purpose: integrate layout validation into the job posting publish workflow so that a posting cannot be published without a valid layout.
- Status: completed
- Scope type: backend service integration, targeted tests, and documentation.

Phase 05d adds a layout validation step to `JobPostingService.publish()`. When a job posting is published, the system validates that the stored layout (or deterministic fallback) matches the current effective enabled/required section set. This prevents publishing a posting with a stale or missing layout configuration.

## Purpose

Ensure layout integrity at publish time:

- Validate that all enabled sections are placed in the layout.
- Validate that no disabled sections are placed in the layout.
- Validate that required sections are present.
- Accept deterministic fallback layout when no stored layout exists (migration compatibility).
- Block publish when a stored layout becomes stale due to config changes.

## Scope

### Implemented

- Added `validateLayoutForPublish(JobPosting)` method to `ApplicationFormLayoutService`.
- Modified `JobPostingService.publish()` to call layout validation before publishing.
- Layout validation at publish time uses existing `ApplicationFormLayoutValidator` and `ApplicationFormLayoutSectionPolicy`.
- Stored layout is used when present; deterministic fallback from `ApplicationFormLayoutDefaultFactory` is accepted when no stored layout exists.
- `InvalidApplicationFormLayoutException` from layout validation is wrapped as `InvalidJobPostingException` to maintain publish API contract consistency.
- Added 3 new tests to `JobPostingServiceTest`:
  - publish succeeds with valid stored layout
  - publish fails when stored layout is stale (config changed after layout save)
  - publish succeeds with fallback layout (no stored layout)

### Out-of-Scope Items

- Frontend/Vue/static resource work.
- DB schema changes or migration files.
- Auto-creation of default layout on posting create/update.
- Layout versioning or change audit trail.
- Force-publish override to skip layout validation.
- Stored layout requirement enforcement (fallback is still accepted).
- Layout stabilization and edge case hardening (Phase 05e scope).

## Changed Files

### New Files

| File | Purpose |
| --- | --- |
| `docs/codex/implementation/phase-05d-publish-layout-guard-integration.md` | Codex reference implementation document. |
| `docs/codex/reports/phase-05d-publish-layout-guard-integration.html` | Human-readable status report. |

### Modified Files

| File | Change | Purpose |
| --- | --- | --- |
| `src/main/java/com/shinyoung/recruit/service/ApplicationFormLayoutService.java` | Added `validateLayoutForPublish(JobPosting)` method | Encapsulates layout validation logic for publish guard. |
| `src/main/java/com/shinyoung/recruit/service/JobPostingService.java` | Added `ApplicationFormLayoutService` dependency and `validateLayoutForPublish()` call in `publish()` | Integrates layout validation into publish workflow. |
| `src/test/java/com/shinyoung/recruit/service/JobPostingServiceTest.java` | Added 3 publish layout guard tests and helper methods | Validates publish guard behavior. |

## New Classes

No new classes were created. Phase 05d adds methods to existing classes.

## Modified Classes

| Package | Class | Type | Change |
| --- | --- | --- | --- |
| `com.shinyoung.recruit.service` | `ApplicationFormLayoutService` | Service | Added `validateLayoutForPublish(JobPosting)` public method. |
| `com.shinyoung.recruit.service` | `JobPostingService` | Service | Added `ApplicationFormLayoutService` dependency; added private `validateLayoutForPublish(JobPosting)` method; called from `publish()`. |

## Class-By-Class Explanation

### ApplicationFormLayoutService - validateLayoutForPublish

- Package: `com.shinyoung.recruit.service`
- Class type: Service
- Responsibility: validates layout for a job posting at publish time.
- Key behavior:
  - Checks that `ApplicationFormConfig` exists on the posting.
  - Calculates effective enabled/required sections using `ApplicationFormLayoutSectionPolicy`.
  - Loads stored pages from `ApplicationFormPageRepository`.
  - Falls back to `ApplicationFormLayoutDefaultFactory.createDefaultLayout()` when no stored pages exist.
  - Delegates validation to `ApplicationFormLayoutValidator.validate()`.
  - Throws `InvalidApplicationFormLayoutException` on validation failure.
- Related classes:
  - `ApplicationFormLayoutValidator`
  - `ApplicationFormLayoutSectionPolicy`
  - `ApplicationFormLayoutDefaultFactory`
  - `ApplicationFormPageRepository`
  - `JobPostingQuestionRepository`
  - `JobPostingAttachmentRequirementRepository`

### JobPostingService - publish layout guard

- Package: `com.shinyoung.recruit.service`
- Class type: Service
- Responsibility: integrates layout validation into the publish workflow.
- Key behavior:
  - `publish()` calls `validateLayoutForPublish(jobPosting)` after existing status, reception period, and job position validations.
  - `validateLayoutForPublish()` catches `InvalidApplicationFormLayoutException` and wraps it as `InvalidJobPostingException` with `"레이아웃 검증 실패: "` prefix.
  - This maintains the existing publish API contract where all business validation errors are `InvalidJobPostingException`.
- Related classes:
  - `ApplicationFormLayoutService`

### JobPostingServiceTest - publish layout guard tests

- Package: `com.shinyoung.recruit.service`
- Class type: Test
- Responsibility: verifies publish-time layout validation behavior.
- Key test methods:
  - `게시_시_저장된_유효한_레이아웃이_있으면_성공` - creates posting with future reception, saves valid layout, publishes successfully.
  - `게시_시_저장된_레이아웃이_현재_설정과_불일치하면_실패` - creates posting, saves layout, updates config to enable new section, publish fails with stale layout.
  - `게시_시_폴백_레이아웃이_유효하면_성공` - creates posting without saving layout, publish succeeds with deterministic fallback.
- Helper methods:
  - `createFutureReceptionRequest(ApplicationFormConfigRequest)` - creates posting with reception in the future (2026-07) so layout save is allowed.
  - `buildLayoutRequest(ApplicationSectionType...)` - builds a single-page layout save request with given sections.

## API List

No new API endpoints were added. Phase 05d modifies the behavior of the existing publish command:

| Method | Path | Purpose | Change |
| --- | --- | --- | --- |
| `POST` | `/admin/job-postings/{jobPostingId}/publish` | Publish job posting | Now validates layout before publishing. |

## Entity Relationship Summary

No entity changes. Phase 05d reuses the existing entity relationships:

```text
JobPosting 1 --- 1 ApplicationFormConfig
JobPosting 1 --- N ApplicationFormPage
ApplicationFormPage 1 --- N ApplicationFormPageItem
JobPosting 1 --- N JobPostingQuestion
JobPosting 1 --- N JobPostingAttachmentRequirement
```

## Validation And Business Rules

### Publish Layout Guard

When `JobPostingService.publish()` is called:

1. Existing validations run first (status, reception period, job positions).
2. Layout validation is called via `ApplicationFormLayoutService.validateLayoutForPublish()`.
3. If stored layout exists, it is validated against current effective sections.
4. If no stored layout exists, a deterministic fallback is generated and validated.
5. Validation checks:
   - At least one page exists.
   - Each page has at least one item.
   - No duplicate page numbers or sort orders.
   - No duplicate item sort orders within a page.
   - No duplicate section types across the full layout.
   - No disabled section is placed.
   - Every enabled section is placed.
   - Every required section is placed.
   - No unsupported layout enum value.
6. On failure, `InvalidJobPostingException` is thrown with `"레이아웃 검증 실패: "` prefix.

### Fallback Policy

- Postings without stored layout: deterministic fallback is generated and always passes validation (sections are derived from effective enabled set).
- Postings with stored layout: stored layout is validated against the current config state. If config changed after layout save, the stored layout may fail validation.
- This is the initial policy. After migration to stored-only layouts, fallback acceptance can be removed.

### Stale Layout Detection

A stored layout becomes stale when:
- `ApplicationFormConfig.useXxx` is changed (sections enabled/disabled after layout save).
- `JobPostingQuestion` active status changes (QUESTION_ANSWER section enabled/disabled).
- `JobPostingAttachmentRequirement` rows are added/removed (ATTACHMENT section enabled/disabled).

The layout validator catches all stale layout conditions because it checks exact match between placed sections and effective enabled sections.

### Error Responses

| Condition | HTTP Status | Response |
| --- | --- | --- |
| No ApplicationFormConfig | 400 | `ApiResponse.fail("레이아웃 검증 실패: 지원서 항목 설정이 없는 채용공고는 게시할 수 없습니다.")` |
| Stale layout (enabled section missing) | 400 | `ApiResponse.fail("레이아웃 검증 실패: Enabled section is missing from layout: ...")` |
| Stale layout (disabled section placed) | 400 | `ApiResponse.fail("레이아웃 검증 실패: Disabled section cannot be placed in layout: ...")` |

## Test Coverage

### Added Tests

| Test Class | Test Count | Coverage |
| --- | --- | --- |
| `JobPostingServiceTest` | 3 (new) | Publish with valid stored layout succeeds, publish with stale layout fails, publish with fallback layout succeeds. |

### Existing Tests Verified

| Test Class | Status | Notes |
| --- | --- | --- |
| `JobPostingServiceTest` | All passed | Existing publish test `DRAFT에서_PUBLISHED_전환_성공` still passes (uses fallback layout). |
| `ApplicationFormLayoutServiceTest` | All passed | No regression from adding `validateLayoutForPublish` method. |
| `AdminApplicationFormLayoutControllerTest` | All passed | No regression. |

### Test Commands

Targeted tests:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*JobPostingServiceTest" --no-daemon
```

Result: `BUILD SUCCESSFUL`

Layout regression tests:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*ApplicationFormLayout*" --no-daemon
```

Result: `BUILD SUCCESSFUL`

Notes:
- Full `clean test` was intentionally not run per Phase 05 targeted-test instruction (local timeout concerns).
- AES secret key is provided as environment variable for test encryption support.

## Remaining Issues

- Fallback layout is still accepted at publish time. After migration, consider requiring stored layout.
- Auto-creation of default layout on posting create/update is not implemented.
- No per-field error detail in publish layout validation response.
- Layout versioning and change audit trail are not implemented.
- Page title/description mutation policy after reception start needs product confirmation.

## Next Phase Recommendation

Proceed with Phase 05e - Layout Stabilization / Test Hardening. The publish guard is now in place. The next step is to harden validation edge cases, verify fallback behavior for existing postings, and confirm attachment/application required policy regression safety.
