# Phase 05c - Admin Layout Management

## Phase Summary

- Phase name: Phase 05c - Admin Layout Management
- Date: 2026-05-27
- Purpose: implement admin-facing layout management APIs for configuring the page arrangement of applicant application forms.
- Status: completed
- Scope type: backend admin API, DTO, service, controller, targeted tests, and documentation.

Phase 05c adds the admin layout read/save/preview APIs that allow recruitment administrators to configure how applicant application form sections are arranged across pages. It reuses the Phase 05a layout domain entities, validators, and helpers and does not modify any existing files.

## Purpose

Provide admin users with APIs to:

- Read the current layout for a job posting (stored or deterministic default)
- See all 10 layout section types with enabled/required/placed/source metadata
- Replace the full layout with a new page arrangement (replace-all semantics)
- Preview the applicant-facing projection of the layout

## Scope

### Implemented

- Added `GET /admin/job-postings/{jobPostingId}/application-form-layout` for admin layout read with available sections.
- Added `POST /admin/job-postings/{jobPostingId}/application-form-layout` for replace-all layout save.
- Added `GET /admin/job-postings/{jobPostingId}/application-form-layout/preview` for applicant-facing preview projection.
- Added `AdminApplicationFormLayoutResponse` with nested `PageResponse`, `ItemResponse`, and `SectionAvailability` records.
- Added `ApplicationFormLayoutPreviewResponse` with nested `PageResponse` and `ItemResponse` records.
- Added `ApplicationFormLayoutSaveRequest` with nested `PageRequest` and `ItemRequest` records with Bean Validation.
- Added `ApplicationFormLayoutService` for admin layout read/save/preview orchestration.
- Added `AdminApplicationFormLayoutController` under `/admin/job-postings/{jobPostingId}/application-form-layout`.
- Reused Phase 05a helpers:
  - `ApplicationFormLayoutSectionPolicy`
  - `ApplicationFormLayoutDefaultFactory`
  - `ApplicationFormLayoutValidator`
- All endpoints are protected by `/admin/**` security policy requiring `ROLE_ADMIN` or `ROLE_RECRUIT_ADMIN`.
- Added targeted service unit tests (13 tests) and controller integration tests (11 tests).

### Out-of-Scope Items

- Frontend/Vue/static resource work.
- DB schema changes or migration files.
- Application creation/update/submit/withdraw policy changes.
- Applicant-facing layout read changes (Phase 05b scope).
- Publish/layout guard integration (Phase 05d scope).
- Layout versioning or change audit trail.
- Per-field error mapping for save validation.
- StageResult, notification, read-audit, Swagger, and broad refactoring.

## Changed Files

### New Files

| File | Purpose |
| --- | --- |
| `src/main/java/com/shinyoung/recruit/dto/request/ApplicationFormLayoutSaveRequest.java` | Replace-all layout save payload with nested `PageRequest` and `ItemRequest` records. |
| `src/main/java/com/shinyoung/recruit/dto/response/AdminApplicationFormLayoutResponse.java` | Admin layout response with pages, items, and `availableSections` (nested records). |
| `src/main/java/com/shinyoung/recruit/dto/response/ApplicationFormLayoutPreviewResponse.java` | Applicant-facing preview projection with page structure. |
| `src/main/java/com/shinyoung/recruit/service/ApplicationFormLayoutService.java` | Admin layout read/save/preview orchestration service. |
| `src/main/java/com/shinyoung/recruit/controller/AdminApplicationFormLayoutController.java` | Admin layout REST endpoints. |
| `src/test/java/com/shinyoung/recruit/service/ApplicationFormLayoutServiceTest.java` | Service unit tests (13 tests). |
| `src/test/java/com/shinyoung/recruit/controller/AdminApplicationFormLayoutControllerTest.java` | Controller integration tests (11 tests). |
| `docs/codex/implementation/phase-05c-admin-layout-management.md` | Codex reference implementation document. |
| `docs/codex/reports/phase-05c-admin-layout-management.html` | Human-readable status report. |

### Modified Files

No existing source files were modified. The `GlobalExceptionHandler` already handled `InvalidApplicationFormLayoutException` from Phase 05a.

## New Classes

| Package | Class | Type | Responsibility |
| --- | --- | --- | --- |
| `com.shinyoung.recruit.dto.request` | `ApplicationFormLayoutSaveRequest` | Request DTO | Carries the replace-all layout save payload with nested page and item structures. |
| `com.shinyoung.recruit.dto.response` | `AdminApplicationFormLayoutResponse` | Response DTO | Returns admin layout with pages, items, section availability, editable flag, and stored flag. |
| `com.shinyoung.recruit.dto.response` | `ApplicationFormLayoutPreviewResponse` | Response DTO | Returns applicant-facing preview with filtered enabled sections only. |
| `com.shinyoung.recruit.service` | `ApplicationFormLayoutService` | Service | Orchestrates admin layout read, save, and preview operations. |
| `com.shinyoung.recruit.controller` | `AdminApplicationFormLayoutController` | Controller | Exposes admin layout read, save, and preview REST endpoints. |

## Modified Classes

No existing classes were modified.

## Class-By-Class Explanation

### ApplicationFormLayoutSaveRequest

- Package: `com.shinyoung.recruit.dto.request`
- Class type: Request DTO (record)
- Responsibility: carries the replace-all layout save payload from the admin client.
- Key fields:
  - `pages` - `List<PageRequest>`, `@NotEmpty`, `@Valid`
  - `PageRequest.pageNo` - `Integer`, `@NotNull`, `@Positive`
  - `PageRequest.title` - `String`, `@NotBlank`, `@Size(max=100)`
  - `PageRequest.description` - `String`, `@Size(max=500)`
  - `PageRequest.sortOrder` - `Integer`, `@NotNull`, `@PositiveOrZero`
  - `PageRequest.items` - `List<ItemRequest>`, `@NotEmpty`, `@Valid`
  - `ItemRequest.sectionType` - `ApplicationSectionType`, `@NotNull`
  - `ItemRequest.sortOrder` - `Integer`, `@NotNull`, `@PositiveOrZero`
- Related classes:
  - `ApplicationSectionType`
  - `ApplicationFormLayoutService`
- Important notes:
  - Uses nested records for page and item structures.
  - Bean Validation annotations enforce structural correctness before business validation.

### AdminApplicationFormLayoutResponse

- Package: `com.shinyoung.recruit.dto.response`
- Class type: Response DTO (record)
- Responsibility: returns admin-safe layout metadata for a job posting.
- Key fields:
  - `jobPostingId` - `Long`
  - `layoutStored` - `boolean`, true when persisted pages exist
  - `editable` - `boolean`, true when posting is not CLOSED and reception has not started
  - `pages` - `List<PageResponse>`, the current layout pages with items
  - `availableSections` - `List<SectionAvailability>`, all 10 layout section types
  - `PageResponse.pageNo`, `title`, `description`, `sortOrder`, `items`
  - `ItemResponse.sectionType`, `sectionName`, `sortOrder`, `enabled`, `required`, `placed`
  - `SectionAvailability.sectionType`, `sectionName`, `enabled`, `required`, `placed`, `source`
- Related classes:
  - `ApplicationSectionType`
  - `ApplicationFormLayoutService`
- Important notes:
  - `availableSections` always includes all 10 layout section types regardless of current config.
  - `source` indicates where the section enablement comes from: `ALWAYS`, `APPLICATION_FORM_CONFIG`, `QUESTION`, `ATTACHMENT_REQUIREMENT`.

### ApplicationFormLayoutPreviewResponse

- Package: `com.shinyoung.recruit.dto.response`
- Class type: Response DTO (record)
- Responsibility: returns applicant-facing preview projection of the layout.
- Key fields:
  - `jobPostingId` - `Long`
  - `jobPostingTitle` - `String`
  - `pages` - `List<PageResponse>`, filtered to enabled sections only
  - `PageResponse.pageNo`, `title`, `description`, `sortOrder`, `items`
  - `ItemResponse.sectionType`, `sectionName`, `required`, `sortOrder`
- Related classes:
  - `ApplicationSectionType`
  - `ApplicationFormLayoutService`
- Important notes:
  - Only enabled sections appear in the preview.
  - Pages with no enabled items are excluded from the response.
  - Admin-only metadata (enabled, placed, source) is not exposed.

### ApplicationFormLayoutService

- Package: `com.shinyoung.recruit.service`
- Class type: Service
- Responsibility: orchestrates admin layout read, save, and preview operations.
- Key methods:
  - `getLayout(Long jobPostingId)` - reads stored or default layout with available sections
  - `saveLayout(Long jobPostingId, ApplicationFormLayoutSaveRequest request)` - validates and replaces layout
  - `getPreview(Long jobPostingId)` - returns applicant-facing preview projection
- Related classes:
  - `JobPostingRepository`
  - `ApplicationFormPageRepository`
  - `JobPostingQuestionRepository`
  - `JobPostingAttachmentRequirementRepository`
  - `ApplicationFormLayoutDefaultFactory`
  - `ApplicationFormLayoutValidator`
  - `ApplicationFormLayoutSectionPolicy`
  - `Clock`
- Important notes:
  - Uses `Clock` for testable time comparison (editable check).
  - `getLayout` returns stored layout if present, otherwise deterministic default from Phase 05a.
  - `saveLayout` validates: posting not CLOSED, reception not started, `ApplicationFormConfig` exists, full layout validation via existing validator.
  - `saveLayout` uses replace-all semantics: deletes old pages, flushes, saves new pages.
  - `getPreview` filters only enabled sections and excludes empty pages.
  - `labelOf(ApplicationSectionType)` returns human-readable section names.
  - `sourceOf(ApplicationSectionType)` returns the enablement source category.

### AdminApplicationFormLayoutController

- Package: `com.shinyoung.recruit.controller`
- Class type: Controller
- Responsibility: exposes admin layout REST endpoints.
- Key methods:
  - `getLayout(@PathVariable Long jobPostingId)` - `GET /admin/job-postings/{jobPostingId}/application-form-layout`
  - `saveLayout(@PathVariable Long jobPostingId, @Valid @RequestBody ApplicationFormLayoutSaveRequest request)` - `POST ...`
  - `getPreview(@PathVariable Long jobPostingId)` - `GET .../preview`
- Related classes:
  - `ApplicationFormLayoutService`
  - `ApiResponse`
- Important notes:
  - All endpoints return `ResponseEntity<ApiResponse<...>>`.
  - Base path: `/admin/job-postings/{jobPostingId}/application-form-layout`.
  - Protected by existing `/admin/**` security policy.

### ApplicationFormLayoutServiceTest

- Package: `com.shinyoung.recruit.service`
- Class type: Test
- Responsibility: verifies admin layout service behavior with Mockito mocks.
- Key test methods:
  - `getLayout_기본_설정_공고의_기본_레이아웃_반환` - default layout returns expected sections
  - `getLayout_저장된_레이아웃이_있으면_layoutStored_true` - stored layout is returned
  - `getLayout_availableSections에_전체_레이아웃_섹션_표시` - all 10 sections in availability
  - `getLayout_질문_첨부_활성_시_섹션_포함` - question/attachment section policy
  - `getLayout_마감된_공고는_editable_false` - CLOSED posting not editable
  - `getLayout_접수_시작_후에는_editable_false` - reception started not editable
  - `getLayout_접수_시작_전이면_editable_true` - before reception is editable
  - `getLayout_존재하지_않는_공고는_404` - missing posting throws
  - `saveLayout_유효한_레이아웃_저장_성공` - successful save with replace-all
  - `saveLayout_마감된_공고에_저장_시도시_실패` - CLOSED save rejected
  - `saveLayout_접수_시작_후_저장_시도시_실패` - reception started save rejected
  - `saveLayout_formConfig_없으면_실패` - missing config rejected
  - `saveLayout_비활성_섹션_배치시_검증_실패` - disabled section placement rejected
- Related classes:
  - `ApplicationFormLayoutService`
  - `ApplicationFormLayoutDefaultFactory`
  - `ApplicationFormLayoutValidator`
- Important notes:
  - Uses fixed `Clock` at `2026-06-15T10:00:00Z` for deterministic time checks.
  - Uses real `ApplicationFormLayoutDefaultFactory` and `ApplicationFormLayoutValidator`, not mocks.
  - Preview tests cover enabled-only filtering, required flag propagation, and disabled section hiding.

### AdminApplicationFormLayoutControllerTest

- Package: `com.shinyoung.recruit.controller`
- Class type: Test
- Responsibility: verifies admin layout controller integration with real application context.
- Key test methods:
  - `GET_레이아웃_조회_기본_레이아웃_반환` - default layout read success
  - `GET_레이아웃_조회_availableSections에_전체_레이아웃_섹션_포함` - section availability check
  - `POST_레이아웃_저장_성공` - save layout with two pages
  - `POST_저장_후_GET_조회시_layoutStored_true` - round-trip save then read
  - `POST_마감된_공고에_저장_시_400` - CLOSED posting rejected
  - `POST_접수_시작_후_저장_시_400` - reception started rejected
  - `POST_비활성_섹션_배치시_검증_실패_400` - disabled section placement rejected
  - `POST_잘못된_요청_body_400` - empty pages request rejected
  - `GET_preview_페이지_구조_응답` - preview response structure
  - `GET_존재하지_않는_공고_404` - missing posting 404
  - `지원되지_않는_HTTP_메서드_405` - PUT/DELETE return 405
- Related classes:
  - `AdminApplicationFormLayoutController`
  - `JobPostingService`
  - `MockMvc`
- Important notes:
  - Uses `@SpringBootTest` with real `WebApplicationContext` and `@Transactional` rollback.
  - Uses `TestConfiguration` with fixed `Clock` at `2026-06-15T10:00:00Z`.
  - Creates test job postings through `JobPostingService` for realistic integration testing.
  - AES key is provided via `@SpringBootTest(properties = ...)`.

## API List

| Method | Path | Purpose | Request | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/admin/job-postings/{jobPostingId}/application-form-layout` | Admin layout read with available sections | Path: `jobPostingId` | `ApiResponse<AdminApplicationFormLayoutResponse>` |
| `POST` | `/admin/job-postings/{jobPostingId}/application-form-layout` | Replace full layout (replace-all semantics) | Path: `jobPostingId`, Body: `ApplicationFormLayoutSaveRequest` | `ApiResponse<AdminApplicationFormLayoutResponse>` |
| `GET` | `/admin/job-postings/{jobPostingId}/application-form-layout/preview` | Applicant-facing preview projection | Path: `jobPostingId` | `ApiResponse<ApplicationFormLayoutPreviewResponse>` |

## Entity Relationship Summary

```text
JobPosting 1 --- 1 ApplicationFormConfig
JobPosting 1 --- N ApplicationFormPage
ApplicationFormPage 1 --- N ApplicationFormPageItem
JobPosting 1 --- N JobPostingQuestion
JobPosting 1 --- N JobPostingAttachmentRequirement
```

Phase 05c does not add new entities. It reuses the Phase 05a domain entities (`ApplicationFormPage`, `ApplicationFormPageItem`) and repositories.

Layout ownership:

```text
ApplicationFormPage.jobPosting -> JobPosting
ApplicationFormPageItem.applicationFormPage -> ApplicationFormPage
```

## Validation And Business Rules

### Layout Read (GET)

- Returns stored layout if persisted pages exist for the posting.
- Returns deterministic default from Phase 05a `ApplicationFormLayoutDefaultFactory` if no stored layout exists.
- No validation is performed on read. Stale layouts are shown as-is so the admin can fix them.
- `layoutStored` is `true` when persisted pages exist.
- `editable` is `true` only when:
  - `JobPosting.status != CLOSED`
  - `now < receptionStartDateTime`
- `availableSections` always includes all 10 layout section types with metadata:
  - `enabled` - whether the section is currently enabled by config/questions/attachments
  - `required` - whether the section is currently required
  - `placed` - whether the section exists in the current layout pages
  - `source` - enablement source category

### Layout Save (POST)

- Posting must not be `CLOSED`.
- Reception must not have started (`now < receptionStartDateTime`).
- `ApplicationFormConfig` must exist for the posting.
- Request is validated by Bean Validation (pages not empty, items not empty, valid types, valid sort orders).
- Layout is validated by `ApplicationFormLayoutValidator` against effective enabled/required sections.
- Disabled sections cannot be placed in the layout.
- Required sections must be placed in the layout.
- `BASIC_INFO` must always be placed.
- Save uses replace-all semantics: delete old pages, flush, save new pages.
- Response returns the newly saved layout with `layoutStored=true`.

### Preview (GET)

- Returns only enabled sections in the page structure.
- Pages with no enabled items are excluded.
- Admin-only metadata (`enabled`, `placed`, `source`) is not exposed.
- Each item includes `required` flag.
- Uses stored layout or default layout, same as read.

### Section Source Mapping

| Section Type | Source |
| --- | --- |
| `BASIC_INFO` | `ALWAYS` |
| `EDUCATION`, `CAREER`, `CERTIFICATE`, `LANGUAGE`, `MILITARY`, `AWARD`, `GAP_PERIOD` | `APPLICATION_FORM_CONFIG` |
| `QUESTION_ANSWER` | `QUESTION` |
| `ATTACHMENT` | `ATTACHMENT_REQUIREMENT` |

### Error Responses

| Condition | HTTP Status | Response |
| --- | --- | --- |
| Posting not found | 404 | `ApiResponse.fail(...)` |
| Posting CLOSED (save) | 400 | `ApiResponse.fail(...)` |
| Reception started (save) | 400 | `ApiResponse.fail(...)` |
| Missing ApplicationFormConfig (save) | 400 | `ApiResponse.fail(...)` |
| Layout validation failure (save) | 400 | `ApiResponse.fail(...)` |
| Bean Validation failure (save) | 400 | `ApiResponse.fail(...)` |
| Unsupported HTTP method | 405 | Method Not Allowed |

## Test Coverage

### Added Tests

| Test Class | Test Count | Coverage |
| --- | --- | --- |
| `ApplicationFormLayoutServiceTest` | 13 | Default layout read, stored layout read, available sections completeness, question/attachment section policy, editable flag for CLOSED/reception-started/before-reception, missing posting 404, successful save, CLOSED save rejection, reception-started save rejection, missing config rejection, disabled section placement rejection, preview enabled-only filtering, preview required flag propagation, preview disabled section hiding. |
| `AdminApplicationFormLayoutControllerTest` | 11 | Default layout GET success, available sections GET, save POST success, round-trip save-then-read, CLOSED posting save 400, reception-started save 400, disabled section save 400, invalid request body 400, preview GET structure, missing posting 404, unsupported methods 405. |

### Test Commands

Executed targeted commands:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*ApplicationFormLayout*" --no-daemon
```

Result:

- `BUILD SUCCESSFUL`
- Service: 13 tests passed
- Controller: 11 tests passed

Notes:

- Full `clean test` was intentionally not run per Phase 05 targeted-test instruction (local timeout concerns).
- AES secret key is provided as environment variable for test encryption support.

## Remaining Issues

- Admin endpoints do not provide per-field error mapping. The first validation error message is returned.
- No audit trail for layout changes beyond `BaseEntity` `createdBy`/`updatedBy` fields.
- Layout versioning and change history are not implemented.
- Publish/layout guard integration remains Phase 05d scope.
- Admin security is enforced by path-based `/admin/**` policy. Per-posting ownership is not verified.

## Next Phase Recommendation

Proceed with Phase 05d - Publish/Layout Guard Integration. The admin can now read, save, and preview layouts. The next step is to integrate layout validation into the publish workflow so that a posting cannot be published without a valid complete layout.
