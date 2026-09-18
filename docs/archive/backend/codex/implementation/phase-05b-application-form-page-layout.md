# Phase 05b - Application Form Page Layout Backend Slice

## Phase Summary

- Phase name: Phase 05b - Application Form Page Layout Backend Slice
- Date: 2026-05-26
- Purpose: implement a read-only bootstrap API for the applicant application form page.
- Status: completed
- Scope type: backend read model, DTO, service, repository support, controller endpoint, targeted tests, and documentation.

Phase 05b adds the applicant-owned form-page bootstrap API used after a `JobApplication` has already been created. It preserves the Phase 5a layout domain and the existing Phase 03 application create/update/submit/withdraw policies.

## Purpose

Provide the frontend with stable initial rendering metadata for an applicant-owned application form:

- selected `JobPosting`
- selected single `JobPosition`
- application status
- reception period and action hints
- current `ApplicationFormConfig`
- enabled layout sections in deterministic order

## Scope

### Implemented

- Added `GET /applications/{applicationId:[0-9]+}/form-page`.
- Added `ApiResponse<ApplicationFormPageResponse>` response contract.
- Added applicant ownership lookup through `applicationId + applicantId`.
- Added repository fetch graph for application, posting, form config, selected position, and selected position posting.
- Added read-only service that calculates:
  - `accepting`
  - `editable`
  - effective enabled sections
  - effective required sections
  - fallback layout when no stored layout exists
- Reused Phase 05a helpers:
  - `ApplicationFormLayoutSectionPolicy`
  - `ApplicationFormLayoutDefaultFactory`
  - `ApplicationFormLayoutValidator`
- Added controller and service targeted tests.
- Added HTTP mapping for `InvalidApplicationFormLayoutException`.

### Out-of-Scope Items

- Frontend/Vue/static resource work.
- DB schema changes.
- Migration files.
- Application creation policy changes.
- Post-create job position change feature.
- Submit validator policy changes.
- Attachment required policy redesign.
- File upload/download changes.
- Admin layout management APIs.
- StageResult, notification, read-audit, Swagger, and broad refactoring.

## Changed Files

### New Files

| File | Purpose |
| --- | --- |
| `src/main/java/com/shinyoung/recruit/dto/response/ApplicationFormPageResponse.java` | Applicant-safe top-level form-page bootstrap response. |
| `src/main/java/com/shinyoung/recruit/dto/response/ApplicationFormSectionResponse.java` | Section layout metadata response item. |
| `src/main/java/com/shinyoung/recruit/service/ApplicationFormPageService.java` | Read-only orchestration service for applicant form-page bootstrap. |
| `src/test/java/com/shinyoung/recruit/service/ApplicationFormPageServiceTest.java` | Service policy and response exposure tests. |
| `docs/codex/implementation/phase-05b-application-form-page-layout.md` | Codex reference implementation document. |
| `docs/codex/reports/phase-05b-application-form-page-layout.html` | Human-readable status report. |

### Modified Files

| File | Change |
| --- | --- |
| `src/main/java/com/shinyoung/recruit/controller/ApplicationController.java` | Added form-page GET endpoint. |
| `src/main/java/com/shinyoung/recruit/domain/repository/JobApplicationRepository.java` | Added applicant-owned form-page fetch method. |
| `src/main/java/com/shinyoung/recruit/domain/repository/JobPostingQuestionRepository.java` | Added active/required active question existence methods. |
| `src/main/java/com/shinyoung/recruit/domain/repository/JobPostingAttachmentRequirementRepository.java` | Added posting-scoped attachment requirement existence method. |
| `src/main/java/com/shinyoung/recruit/exception/GlobalExceptionHandler.java` | Added `InvalidApplicationFormLayoutException` bad-request mapping. |
| `src/test/java/com/shinyoung/recruit/controller/ApplicationControllerTest.java` | Added form-page endpoint success, security, ownership, unsupported-method tests. |
| `docs/codex/07-implementation-history.md` | Added Phase 05b implementation history. |

## New Classes

| Package | Class | Type | Responsibility |
| --- | --- | --- | --- |
| `com.shinyoung.recruit.dto.response` | `ApplicationFormPageResponse` | Response DTO | Returns application/posting/position/form-config/action/section bootstrap data. |
| `com.shinyoung.recruit.dto.response` | `ApplicationFormSectionResponse` | Response DTO | Returns one enabled layout section with label, required flag, and display order. |
| `com.shinyoung.recruit.service` | `ApplicationFormPageService` | Service | Loads applicant-owned application and builds the read-only form-page response. |
| `com.shinyoung.recruit.service` | `ApplicationFormPageServiceTest` | Test | Verifies ownership, selected position, flags, section policy, and forbidden-field exposure. |

## Modified Classes

| Package | Class | Type | Responsibility | Key Change |
| --- | --- | --- | --- | --- |
| `com.shinyoung.recruit.controller` | `ApplicationController` | Controller | Applicant application HTTP API. | Added `GET /applications/{applicationId:[0-9]+}/form-page`. |
| `com.shinyoung.recruit.domain.repository` | `JobApplicationRepository` | Repository | Application persistence. | Added `findFormPageByIdAndApplicantId(...)` with to-one fetch graph. |
| `com.shinyoung.recruit.domain.repository` | `JobPostingQuestionRepository` | Repository | Posting question persistence. | Added active/required active question existence methods. |
| `com.shinyoung.recruit.domain.repository` | `JobPostingAttachmentRequirementRepository` | Repository | Posting attachment requirement persistence. | Added posting-scoped existence method. |
| `com.shinyoung.recruit.exception` | `GlobalExceptionHandler` | Exception handler | JSON API error mapping. | Maps invalid layout to `400 + ApiResponse.fail(...)`. |
| `com.shinyoung.recruit.controller` | `ApplicationControllerTest` | Test | Application controller contract tests. | Added form-page route coverage. |

## Class-By-Class Explanation

### ApplicationFormPageResponse

- Package: `com.shinyoung.recruit.dto.response`
- Class type: Response DTO
- Responsibility: applicant-facing bootstrap payload for one application form page.
- Key fields:
  - `applicationId`
  - `jobPostingId`
  - `jobPostingTitle`
  - `jobPostingStatus`
  - `jobPositionId`
  - `jobPositionName`
  - `applicationStatus`
  - `receptionStartDateTime`
  - `receptionEndDateTime`
  - `accepting`
  - `editable`
  - `submittedAt`
  - `withdrawnAt`
  - `formConfig`
  - `sections`
- Related classes:
  - `JobApplication`
  - `JobPosting`
  - `JobPosition`
  - `ApplicationFormConfigResponse`
  - `ApplicationFormSectionResponse`
- Important notes:
  - Uses application snapshots for posting/position names when present.
  - Does not include `applicantId`, StageResult internals, score/comment fields, or attachment storage internals.

### ApplicationFormSectionResponse

- Package: `com.shinyoung.recruit.dto.response`
- Class type: Response DTO
- Responsibility: exposes the enabled section list used for form rendering.
- Key fields:
  - `sectionType`
  - `label`
  - `enabled`
  - `required`
  - `sortOrder`
  - `pageNo` (소속 페이지 번호, `ApplicationFormPage.pageNo`) — 2026-06-01 추가
  - `pageTitle` (소속 페이지 제목, `ApplicationFormPage.title`) — 2026-06-01 추가
- Related classes:
  - `ApplicationSectionType`
  - `ApplicationFormPageService`
- Important notes:
  - The response includes only enabled sections; `enabled` is therefore always `true`.
  - Endpoint hints are intentionally not included in this slice.
  - **(2026-06-01) `pageNo`/`pageTitle` 추가**: 평탄화된 `sections` 각 항목이 소속 페이지의 번호/제목을
    함께 노출해 프론트가 멀티페이지 폼을 그룹핑할 수 있다. `ApplicationFormPageService.toSectionResponses`가
    각 page의 `pageNo`/`title`을 item 매핑 시 전달한다. (섹션별 `completed` 완료 플래그는 후속 과제로 보류.)

### ApplicationFormPageService

- Package: `com.shinyoung.recruit.service`
- Class type: Service
- Responsibility: builds the applicant-owned form-page bootstrap read model.
- Key methods:
  - `getFormPage(Long applicantId, Long applicationId)`
- Related classes:
  - `JobApplicationRepository`
  - `ApplicationFormPageRepository`
  - `JobPostingQuestionRepository`
  - `JobPostingAttachmentRequirementRepository`
  - `ApplicationFormLayoutDefaultFactory`
  - `ApplicationFormLayoutValidator`
  - `ApplicationFormLayoutSectionPolicy`
- Important notes:
  - Uses `findFormPageByIdAndApplicantId` so another applicant's application is hidden as 404.
  - Validates that `application.jobPosition.jobPosting.id` matches `application.jobPosting.id`.
  - Calculates `accepting` as `PUBLISHED + now inside reception period`.
  - Calculates `editable` as `DRAFT + accepting`.
  - Uses stored layout if present; otherwise uses the deterministic Phase 05a default layout.
  - Validates the layout against effective enabled/required sections before responding.

### ApplicationController

- Package: `com.shinyoung.recruit.controller`
- Class type: Controller
- Responsibility: applicant application API surface.
- Key methods:
  - `getFormPage(...)`
- Related classes:
  - `CurrentApplicantService`
  - `ApplicationFormPageService`
  - `ApiResponse`
- Important notes:
  - No `applicantId` is accepted from path, query, or body.
  - The endpoint remains under the existing `/applications/**` applicant security policy.
  - The numeric path constraint keeps `/applications/me` from colliding.

## API List

| Method | Path | Purpose | Request | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/applications/{applicationId:[0-9]+}/form-page` | Applicant-owned form-page bootstrap read model | Path: `applicationId` | `ApiResponse<ApplicationFormPageResponse>` |

## Entity Relationship Summary

```text
JobApplication N --- 1 JobPosting
JobApplication N --- 1 JobPosition
JobPosition N --- 1 JobPosting
JobPosting 1 --- 1 ApplicationFormConfig
JobPosting 1 --- N ApplicationFormPage
ApplicationFormPage 1 --- N ApplicationFormPageItem
```

Business invariant verified by the service:

```text
JobApplication.jobPosition.jobPosting.id == JobApplication.jobPosting.id
```

## Validation And Business Rules

- Current applicant id is resolved by `CurrentApplicantService`.
- Other applicant applications are hidden with `JobApplicationNotFoundException`.
- Missing `ApplicationFormConfig` is treated as invalid application state.
- `accepting=true` only when:
  - `JobPosting.status == PUBLISHED`
  - `now >= receptionStartDateTime`
  - `now <= receptionEndDateTime`
- `editable=true` only when:
  - `JobApplication.status == DRAFT`
  - `accepting == true`
- Effective enabled sections:
  - `BASIC_INFO`
  - `ApplicationFormConfig.useXxx=true` sections
  - `QUESTION_ANSWER` when active questions exist
  - `ATTACHMENT` when attachment requirement rows exist
- Effective required sections:
  - `BASIC_INFO`
  - `ApplicationFormConfig.useXxx && requireXxx` sections
  - `QUESTION_ANSWER` when an active required question exists
  - `ATTACHMENT` when a required attachment requirement exists
- Stored layout is validated against effective section policy.
- No submit validator policy is changed.
- No application create/update/submit/withdraw policy is changed.

## Test Coverage

### Added/Updated Tests

| Test | Coverage |
| --- | --- |
| `ApplicationFormPageServiceTest` | Owned read success, hidden 404 ownership, selected position/posting consistency, accepting/editable flags, stored layout order, fallback default layout, question/attachment section policy, forbidden field absence. |
| `ApplicationControllerTest` | `GET /applications/{id}/form-page` success wrapper, 401 anonymous, 403 employee/admin, 404 other applicant, unsupported methods, `/applications/me` collision protection through existing numeric mapping. |

### Test Commands

Executed targeted commands only:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationFormPageServiceTest --no-daemon
```

Result:

- `BUILD SUCCESSFUL`

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.ApplicationControllerTest --no-daemon
```

Result:

- `BUILD SUCCESSFUL`

Notes:

- Full `test` and `clean test` were intentionally not run per the Phase 5b instruction.
- Initial sandbox attempts failed because Gradle wrapper distribution download requires network access.
- The targeted service test initially failed on fixture expectations and was fixed before the final successful run.

## Remaining Issues

- Admin layout read/save/preview API is still not implemented.
- Stored layout persistence/default creation policy is still separate from this applicant read slice.
- Page-level application save remains deferred.
- Publish/layout guard integration remains deferred.
- HTML/front-end mapping remains frontend-owned.

## Next Phase Recommendation

Proceed with an admin layout management slice or publish/layout guard integration, depending on product priority. The applicant form-page API now has enough metadata for initial frontend bootstrap without changing existing section save APIs.
