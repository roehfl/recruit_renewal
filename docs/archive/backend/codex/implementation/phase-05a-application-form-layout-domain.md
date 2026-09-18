# Phase 05a - Application Form Layout Domain

## Phase Summary

- Phase name: Phase 05a - Application Form Layout Domain
- Date: 2026-05-26
- Purpose: add the backend domain foundation for page-level arrangement of application form sections.
- Status: completed
- Scope type: domain, repository, validator/helper, targeted tests, and documentation.

Phase 05a adds persistent layout entities and validation support only. It does not add admin APIs, applicant APIs, page-level application save APIs, frontend code, migration files, or changes to existing attachment/question/submit policies.

## Implemented Scope

- Extended `ApplicationSectionType` with layout-only section values:
  - `BASIC_INFO`
  - `QUESTION_ANSWER`
  - `ATTACHMENT`
- Added an explicit layout subset through:
  - `ApplicationSectionType.isLayoutSection()`
  - `ApplicationSectionType.layoutSectionTypes()`
- Added `ApplicationFormPage`.
- Added `ApplicationFormPageItem`.
- Added `ApplicationFormPageRepository`.
- Added `ApplicationFormPageItemRepository`.
- Added `ApplicationFormLayoutValidator`.
- Added review fix validation that requires `BASIC_INFO` in both effective enabled and effective required section sets.
- Added `ApplicationFormLayoutSectionPolicy`.
- Added `ApplicationFormLayoutDefaultFactory`.
- Added `InvalidApplicationFormLayoutException`.
- Added targeted entity, repository, validator, policy, and factory tests.
- Added this implementation document and the matching human-readable HTML report.
- Updated implementation history, roadmap, and current status report.

## Out Of Scope

- `AdminApplicationFormLayoutController`
- `ApplicantApplicationFormLayoutController`
- admin layout read/save/preview API
- applicant form-layout read API
- `JobPosting` publish guard integration
- application page-level save API
- existing section save API refactoring
- attachment required policy changes
- question/answer save or validation changes
- application submit validator changes
- Vue, Pinia, drag/drop UI, static resources
- Flyway/Liquibase/migration file
- production MariaDB DDL file

## Changed Files

### New Files

| File | Purpose |
| --- | --- |
| `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationFormPage.java` | JobPosting-owned application form layout page entity. |
| `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationFormPageItem.java` | Section placement item entity under a layout page. |
| `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationFormPageRepository.java` | Page repository with posting-scoped lookup, fetch-with-items, exists, and delete helpers. |
| `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationFormPageItemRepository.java` | Optional item repository for page-scoped ordered item lookup. |
| `src/main/java/com/shinyoung/recruit/exception/InvalidApplicationFormLayoutException.java` | Layout-specific validation exception. |
| `src/main/java/com/shinyoung/recruit/service/ApplicationFormLayoutValidator.java` | Stored layout consistency validator. |
| `src/main/java/com/shinyoung/recruit/service/ApplicationFormLayoutSectionPolicy.java` | Pure helper for effective enabled/required layout section sets. |
| `src/main/java/com/shinyoung/recruit/service/ApplicationFormLayoutDefaultFactory.java` | Pure default layout page factory. |
| `src/test/java/com/shinyoung/recruit/domain/entity/ApplicationFormPageTest.java` | Page entity tests. |
| `src/test/java/com/shinyoung/recruit/domain/entity/ApplicationFormPageItemTest.java` | Page item entity tests. |
| `src/test/java/com/shinyoung/recruit/domain/repository/ApplicationFormPageRepositoryTest.java` | Page/item repository tests. |
| `src/test/java/com/shinyoung/recruit/service/ApplicationFormLayoutValidatorTest.java` | Layout validator tests. |
| `src/test/java/com/shinyoung/recruit/service/ApplicationFormLayoutSectionPolicyTest.java` | Effective section policy tests. |
| `src/test/java/com/shinyoung/recruit/service/ApplicationFormLayoutDefaultFactoryTest.java` | Default layout factory tests. |
| `docs/codex/implementation/phase-05a-application-form-layout-domain.md` | Codex reference implementation document. |
| `docs/codex/reports/phase-05a-application-form-layout-domain.html` | Human-readable phase report. |

### Modified Files

| File | Change |
| --- | --- |
| `src/main/java/com/shinyoung/recruit/enumeration/ApplicationSectionType.java` | Added layout section values and layout subset helper methods. |
| `docs/codex/06-implementation-roadmap.md` | Marked Phase 05a complete and set Phase 05b as next. |
| `docs/codex/07-implementation-history.md` | Added Phase 05a implementation history. |
| `docs/codex/reports/current-implementation-status.html` | Updated current status and next phase. |

## New Classes

| Package | Class | Type | Responsibility |
| --- | --- | --- | --- |
| `com.shinyoung.recruit.domain.entity` | `ApplicationFormPage` | Entity | Stores one page in a JobPosting-specific application form layout. |
| `com.shinyoung.recruit.domain.entity` | `ApplicationFormPageItem` | Entity | Stores one section placement inside a layout page. |
| `com.shinyoung.recruit.domain.repository` | `ApplicationFormPageRepository` | Repository | Reads and deletes layout pages by JobPosting. |
| `com.shinyoung.recruit.domain.repository` | `ApplicationFormPageItemRepository` | Repository | Reads page items by page id. |
| `com.shinyoung.recruit.exception` | `InvalidApplicationFormLayoutException` | Exception | Represents layout validation failure. |
| `com.shinyoung.recruit.service` | `ApplicationFormLayoutValidator` | Service | Validates page shape and effective section set consistency. |
| `com.shinyoung.recruit.service` | `ApplicationFormLayoutSectionPolicy` | Service helper | Calculates effective enabled/required section sets from existing policies. |
| `com.shinyoung.recruit.service` | `ApplicationFormLayoutDefaultFactory` | Service | Creates the default in-memory layout page grouping. |

## Modified Classes

| Package | Class | Type | Responsibility | Key Change |
| --- | --- | --- | --- | --- |
| `com.shinyoung.recruit.enumeration` | `ApplicationSectionType` | Enum | Section codes used by application details and attachments. | Added `BASIC_INFO`, `QUESTION_ANSWER`, `ATTACHMENT`; added layout subset helper methods. |

## Class-By-Class Explanation

### ApplicationSectionType

- Package: `com.shinyoung.recruit.enumeration`
- Class type: Enum
- Responsibility: provides application section codes.
- Key fields or methods:
  - `BASIC_INFO`
  - `QUESTION_ANSWER`
  - `ATTACHMENT`
  - `isLayoutSection()`
  - `layoutSectionTypes()`
- Related classes:
  - `ApplicationFormPageItem`
  - `ApplicationFormLayoutValidator`
  - `ApplicationFormLayoutSectionPolicy`
  - existing `ApplicationAttachment` and attachment policy classes
- Important notes:
  - Existing `APPLICATION` remains unchanged for attachment metadata semantics.
  - `APPLICATION` and `ETC` are intentionally not layout section values.
  - The layout subset is explicit; enum values are not all treated as layout-allowable values.

### ApplicationFormPage

- Package: `com.shinyoung.recruit.domain.entity`
- Class type: Entity
- Responsibility: stores one page in the application form layout for a `JobPosting`.
- Key fields or methods:
  - `id`
  - `jobPosting`
  - `pageNo`
  - `title`
  - `description`
  - `sortOrder`
  - `items`
  - `create(...)`
  - `addItem(ApplicationSectionType, Integer)`
  - `addItem(ApplicationFormPageItem)`
  - `clearItems()`
  - `hasItems()`
- Related classes:
  - `JobPosting`
  - `ApplicationFormPageItem`
  - `ApplicationFormPageRepository`
- Important notes:
  - The entity validates required page fields, title length, description length, and sort order.
  - Minimum one item is validated by `ApplicationFormLayoutValidator`, not the entity factory, so JPA aggregate construction remains flexible.
  - Items are mapped with cascade and orphan removal because page owns its item rows.

### ApplicationFormPageItem

- Package: `com.shinyoung.recruit.domain.entity`
- Class type: Entity
- Responsibility: stores one allowed section placement inside an application form page.
- Key fields or methods:
  - `id`
  - `page`
  - `sectionType`
  - `sortOrder`
  - `create(...)`
  - `assignPage(...)`
- Related classes:
  - `ApplicationFormPage`
  - `ApplicationSectionType`
  - `ApplicationFormPageItemRepository`
- Important notes:
  - The entity rejects `null` page, `null` section type, non-layout section values, and negative sort order.
  - It intentionally does not store `required`, `enabled`, section display names, component names, frontend file names, pixel dimensions, or field-level config.

### ApplicationFormPageRepository

- Package: `com.shinyoung.recruit.domain.repository`
- Class type: Repository
- Responsibility: provides persistence access for `ApplicationFormPage`.
- Key methods:
  - `findByJobPostingIdOrderBySortOrderAscIdAsc(Long jobPostingId)`
  - `findByJobPostingIdWithItems(Long jobPostingId)`
  - `existsByJobPostingId(Long jobPostingId)`
  - `deleteByJobPostingId(Long jobPostingId)`
- Related classes:
  - `ApplicationFormPage`
  - `ApplicationFormPageItem`
- Important notes:
  - `findByJobPostingIdWithItems` uses `@EntityGraph(attributePaths = {"items"})`.
  - Item ordering is backed by `@OrderBy("sortOrder ASC, id ASC")` on the entity.

### ApplicationFormPageItemRepository

- Package: `com.shinyoung.recruit.domain.repository`
- Class type: Repository
- Responsibility: optional repository for direct page-item lookup.
- Key methods:
  - `findByPageIdOrderBySortOrderAscIdAsc(Long pageId)`
- Related classes:
  - `ApplicationFormPageItem`
- Important notes:
  - The aggregate can be persisted through `ApplicationFormPageRepository`; this repository exists for tests and future direct queries.

### InvalidApplicationFormLayoutException

- Package: `com.shinyoung.recruit.exception`
- Class type: Exception
- Responsibility: identifies layout validation failures separately from generic job posting or application exceptions.
- Key methods:
  - constructor accepting `String message`
- Related classes:
  - `ApplicationFormLayoutValidator`
- Important notes:
  - No `GlobalExceptionHandler` mapping was added because Phase 05a does not add HTTP APIs.
  - HTTP mapping can be added in Phase 05b with admin API endpoints.

### ApplicationFormLayoutValidator

- Package: `com.shinyoung.recruit.service`
- Class type: Service
- Responsibility: validates stored layout pages against effective enabled and required section sets.
- Key methods:
  - `validate(List<ApplicationFormPage>, Set<ApplicationSectionType>, Set<ApplicationSectionType>)`
- Related classes:
  - `ApplicationFormPage`
  - `ApplicationFormPageItem`
  - `ApplicationSectionType`
  - `InvalidApplicationFormLayoutException`
- Important notes:
  - Validates page null/empty, page numbers, page sort order, titles, description lengths, item presence, item sort order, layout section subset, duplicate section placement, disabled section placement, enabled section omission, required section omission, `BASIC_INFO` presence in both effective section sets, and required-set subset.
  - It does not read repositories or calculate effective sections by itself.

### ApplicationFormLayoutSectionPolicy

- Package: `com.shinyoung.recruit.service`
- Class type: Service helper
- Responsibility: calculates effective layout sections from existing form config and external policy booleans.
- Key methods:
  - `enabledSections(ApplicationFormConfig, boolean hasAttachmentRequirements, boolean hasActiveQuestions)`
  - `requiredSections(ApplicationFormConfig, boolean hasRequiredAttachmentRequirements, boolean hasRequiredQuestions)`
- Related classes:
  - `ApplicationFormConfig`
  - `ApplicationSectionType`
  - `JobPostingAttachmentRequirement`
  - `JobPostingQuestion`
- Important notes:
  - `BASIC_INFO` is always enabled and required.
  - `QUESTION_ANSWER` is driven by active/required question flags passed in by future service code.
  - `ATTACHMENT` is driven by attachment requirement flags passed in by future service code.
  - No repository dependencies were introduced in this slice.

### ApplicationFormLayoutDefaultFactory

- Package: `com.shinyoung.recruit.service`
- Class type: Service
- Responsibility: creates default in-memory layout pages from the effective enabled section set.
- Key methods:
  - `createDefaultLayout(JobPosting, Set<ApplicationSectionType>)`
- Related classes:
  - `JobPosting`
  - `ApplicationFormPage`
  - `ApplicationFormPageItem`
  - `ApplicationSectionType`
- Important notes:
  - It does not save to the repository.
  - Empty pages are not created.
  - Default groups:
    - Page 1: `BASIC_INFO`, `MILITARY`
    - Page 2: `EDUCATION`, `CAREER`
    - Page 3: `CERTIFICATE`, `LANGUAGE`, `AWARD`, `GAP_PERIOD`
    - Page 4: `QUESTION_ANSWER`
    - Page 5: `ATTACHMENT`

## API List

No API was added in Phase 05a.

Future API candidates remain Phase 05b/05c work:

| Method | Path | Purpose | Status |
| --- | --- | --- | --- |
| `GET` | `/admin/job-postings/{jobPostingId}/form-layout` | Admin layout read | Not implemented |
| `POST` | `/admin/job-postings/{jobPostingId}/form-layout` | Admin layout save | Not implemented |
| `GET` | `/admin/job-postings/{jobPostingId}/form-layout/preview` | Admin layout preview | Not implemented |
| `GET` | `/job-postings/{jobPostingId}/form-layout` | Applicant layout read | Not implemented |

## Entity Relationship Summary

```text
JobPosting 1 --- N ApplicationFormPage
ApplicationFormPage 1 --- N ApplicationFormPageItem
ApplicationFormPageItem N --- 1 ApplicationSectionType enum value
```

- `ApplicationFormPage.jobPosting` is required.
- `ApplicationFormPage.items` owns item rows through cascade and orphan removal.
- `ApplicationFormPageItem.page` is required.
- `ApplicationFormPageItem.sectionType` stores `ApplicationSectionType` as `EnumType.STRING`.

## Validation And Business Rules

- `BASIC_INFO` is layout-only in 05a and is always enabled/required through policy helper.
- `effectiveEnabledSections` must contain `BASIC_INFO`.
- `effectiveRequiredSections` must contain `BASIC_INFO`.
- `BASIC_INFO` write API is not implemented in 05a.
- Existing root application draft update remains the current candidate for basic application update behavior.
- `QUESTION_ANSWER` is included as a layout section because the backend already has `JobPostingQuestion` and application answer APIs.
- `ATTACHMENT` is included as a layout section because the backend already has attachment requirement and attachment upload/read APIs.
- `APPLICATION` is not reused as layout basic info and is not allowed as a layout item.
- `ETC` is not allowed as a layout item.
- Layout item rows do not store `required`, `enabled`, frontend component names, or field-level builder metadata.
- Effective enabled sections must exactly match placed sections.
- Effective required sections must be a subset of effective enabled sections.
- A required section must be placed.
- A disabled section must not be placed.
- A section type must not be duplicated across the full layout.
- A page must have at least one item.
- Page number and page sort order must not duplicate.
- Item sort order must not duplicate within a page.

## Test Coverage

### Added Tests

| Test | Coverage |
| --- | --- |
| `ApplicationFormPageTest` | Page creation, trimming, validation, add/clear items. |
| `ApplicationFormPageItemTest` | Item creation, layout subset validation, rejection of `APPLICATION` and `ETC`. |
| `ApplicationFormPageRepositoryTest` | Cascade save, ordered page lookup, fetch-with-items, exists/delete by posting. |
| `ApplicationFormLayoutValidatorTest` | Valid layout, null/empty pages, duplicate page/item order, duplicate sections, unsupported/disabled/missing sections, missing `BASIC_INFO` in enabled/required sets, required subset. |
| `ApplicationFormLayoutSectionPolicyTest` | Always-on `BASIC_INFO`, config-driven sections, attachment/question flags. |
| `ApplicationFormLayoutDefaultFactoryTest` | Default grouping, skipped empty pages, page/order assignment. |

### Test Commands

Full `test` and `clean test` were intentionally not run because `instruction.md` forbids full-suite execution for Phase 05a due current development PC full-suite timeout concerns.

Executed targeted command:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.domain.entity.ApplicationFormPageTest --tests com.shinyoung.recruit.domain.entity.ApplicationFormPageItemTest --tests com.shinyoung.recruit.domain.repository.ApplicationFormPageRepositoryTest --tests com.shinyoung.recruit.service.ApplicationFormLayoutValidatorTest --tests com.shinyoung.recruit.service.ApplicationFormLayoutSectionPolicyTest --tests com.shinyoung.recruit.service.ApplicationFormLayoutDefaultFactoryTest --no-daemon
```

Result:

- `BUILD SUCCESSFUL`

Review fix targeted command:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationFormLayoutValidatorTest --no-daemon --rerun-tasks
```

Review fix result:

- `BUILD SUCCESSFUL`

Notes:

- Initial sandbox execution failed because Gradle wrapper network access was blocked while downloading the configured Gradle distribution.
- A later test run failed because a previous timed-out Gradle process held `build/test-results/test/binary/output.bin`.
- `.\gradlew.bat --stop` cleared the stale Gradle daemon.
- The final targeted command above passed after approved escalation.

## Known Limitations

- No admin layout read/save/preview API exists yet.
- No applicant layout read API exists yet.
- No publish guard uses `ApplicationFormLayoutValidator` yet.
- No default layout persistence service exists yet.
- No migration file was created.
- Production MariaDB requires manual table creation for `application_form_page` and `application_form_page_item` if this code is deployed before a migration phase.
- `GlobalExceptionHandler` does not yet map `InvalidApplicationFormLayoutException` because there is no Phase 05a HTTP API.

## Remaining Issues

- Decide whether Phase 05b should save default layout automatically when no stored layout exists, or return a preview-only default.
- Decide whether admin layout save should replace all pages atomically or support granular page/item commands.
- Decide whether layout mutation should be blocked at `PUBLISHED` or reception-start time.
- Add API-safe response DTOs that do not expose internal file paths, attachment storage paths, or sensitive applicant data.

## Next Phase Recommendation

Proceed to `Phase 05b - Admin Layout Management`.

Recommended 05b scope:

- Admin layout read API.
- Available section response using effective policy.
- Admin layout save API.
- Validator integration at save time.
- Optional default layout preview.
- HTTP exception mapping for `InvalidApplicationFormLayoutException`.
