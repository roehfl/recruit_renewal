# Phase 03c-9-1 QuestionTemplate + JobPostingQuestion Admin API

## Phase Summary

Phase 03c-9-1 implements the first concrete slice of the Application question/answer domain. The phase adds global question templates and job-posting-specific question configuration APIs for administrators.

This phase intentionally stops before applicant answers. `ApplicationAnswer`, applicant answer APIs, submit validator answer checks, and admin answer read APIs are not implemented.

## Implemented Scope

- `QuestionCategory` enum
- `QuestionAnswerType` enum
- `QuestionTemplate` entity and repository
- `JobPostingQuestion` entity and repository
- Question template create/read/update/deactivate/activate service and controller
- Job posting question create/read/update/reorder/deactivate service and controller
- Template-based question creation with snapshot copy and request override
- Direct job posting question creation
- DRAFT-only mutation policy for job posting questions
- Soft delete by `active=false`
- Dedicated exceptions and `GlobalExceptionHandler` mappings
- Service and controller tests for the new API

## Out of Scope

- `ApplicationAnswer`
- `GET /applications/{applicationId}/questions`
- `POST /applications/{applicationId}/answers`
- `GET /admin/applications/{applicationId}/answers`
- `ApplicationSubmitValidator` answer integration
- `QuestionSet`
- Choice option domain
- File answer type
- Attachment linkage
- PUT and HTTP DELETE APIs
- Physical delete
- SecurityConfig changes
- CommonCode conversion

## Changed Files

| Path | Type | Notes |
|---|---|---|
| `src/main/java/com/shinyoung/recruit/enumeration/QuestionCategory.java` | New | Question category enum |
| `src/main/java/com/shinyoung/recruit/enumeration/QuestionAnswerType.java` | New | Initial answer type enum |
| `src/main/java/com/shinyoung/recruit/domain/entity/QuestionTemplate.java` | New | Global question bank entity |
| `src/main/java/com/shinyoung/recruit/domain/entity/JobPostingQuestion.java` | New | JobPosting question snapshot entity |
| `src/main/java/com/shinyoung/recruit/domain/repository/QuestionTemplateRepository.java` | New | Template repository |
| `src/main/java/com/shinyoung/recruit/domain/repository/JobPostingQuestionRepository.java` | New | Job posting question repository |
| `src/main/java/com/shinyoung/recruit/dto/request/*Question*.java` | New | Request DTOs |
| `src/main/java/com/shinyoung/recruit/dto/response/*Question*.java` | New | Response DTOs |
| `src/main/java/com/shinyoung/recruit/service/QuestionTemplateService.java` | New | Template business rules |
| `src/main/java/com/shinyoung/recruit/service/JobPostingQuestionService.java` | New | Job posting question rules |
| `src/main/java/com/shinyoung/recruit/controller/QuestionTemplateController.java` | New | Template admin API |
| `src/main/java/com/shinyoung/recruit/controller/JobPostingQuestionController.java` | New | JobPosting question admin API |
| `src/main/java/com/shinyoung/recruit/exception/*Question*.java` | New | Dedicated question exceptions |
| `src/main/java/com/shinyoung/recruit/exception/GlobalExceptionHandler.java` | Modified | 400/404 mapping for question exceptions |
| `src/test/java/com/shinyoung/recruit/service/QuestionTemplateServiceTest.java` | New | Template service coverage |
| `src/test/java/com/shinyoung/recruit/controller/QuestionTemplateControllerTest.java` | New | Template API coverage |
| `src/test/java/com/shinyoung/recruit/service/JobPostingQuestionServiceTest.java` | New | JobPosting question service coverage |
| `src/test/java/com/shinyoung/recruit/controller/JobPostingQuestionControllerTest.java` | New | JobPosting question API coverage |
| `src/test/java/com/shinyoung/recruit/controller/JobPostingControllerTest.java` | New | Basic JobPosting controller regression |
| `docs/codex/design/phase-03-application-design.md` | Modified | Phase note |
| `docs/codex/design/phase-03c-application-detail-design.md` | Modified | Phase note and next phase |
| `docs/codex/design/phase-03c-9-question-answer-design.md` | Modified | Implementation status |
| `docs/codex/07-implementation-history.md` | Modified | History entry |
| `docs/codex/reports/phase-03c-9-1-question-template-job-posting-question.html` | New | Human-readable report |

## Class-by-Class Explanation

| Package | Class | Type | Responsibility | Key fields/methods | Notes |
|---|---|---|---|---|---|
| `enumeration` | `QuestionCategory` | Enum | Categorizes question intent | `SELF_INTRODUCTION`, `GENERAL`, `JOB_SPECIFIC`, `ETC` | Display labels remain outside enum |
| `enumeration` | `QuestionAnswerType` | Enum | Defines initial answer input type | `SHORT_TEXT`, `LONG_TEXT` | Choice/file types deferred |
| `domain.entity` | `QuestionTemplate` | Entity | Global question bank | `create`, `update`, `deactivate`, `activate` | No reverse collection |
| `domain.entity` | `JobPostingQuestion` | Entity | JobPosting-specific question snapshot | `createFromTemplate`, `createDirect`, `update`, `changeOrder`, `deactivate` | No `JobPosting` collection |
| `domain.repository` | `QuestionTemplateRepository` | Repository | Template persistence | `findByActive`, `existsByIdAndActiveTrue` | Page-based list |
| `domain.repository` | `JobPostingQuestionRepository` | Repository | JobPosting question persistence | sorted finders, sortOrder exists checks | Active duplicate sort order enforced in service |
| `dto.request` | `QuestionTemplateCreateRequest` | Request DTO | Template create input | validation annotations | `active` not accepted |
| `dto.request` | `QuestionTemplateUpdateRequest` | Request DTO | Template update input | validation annotations | Deactivate and activate are separate commands |
| `dto.response` | `QuestionTemplateResponse` | Response DTO | Template response | `from(QuestionTemplate)` | Includes active and audit dates |
| `dto.request` | `JobPostingQuestionCreateRequest` | Request DTO | JobPosting question create input | nullable template/override fields | Branch validation is in service |
| `dto.request` | `JobPostingQuestionUpdateRequest` | Request DTO | Snapshot update input | required snapshot fields | Template reference is not changed |
| `dto.request` | `JobPostingQuestionReorderRequest` | Request DTO | Reorder command input | `questions` | Requires non-empty list |
| `dto.response` | `JobPostingQuestionResponse` | Response DTO | JobPosting question response | `from(JobPostingQuestion)` | Exposes `questionTemplateId`, not template body |
| `service` | `QuestionTemplateService` | Service | Template policy and CRUD | `getTemplates`, `createTemplate`, `updateTemplate`, `deactivateTemplate`, `activateTemplate` | `SHORT_TEXT <= 500`, `LONG_TEXT <= 5000` |
| `service` | `JobPostingQuestionService` | Service | JobPosting question policy | create/update/reorder/deactivate | DRAFT-only mutation |
| `controller` | `QuestionTemplateController` | Controller | Template admin API | GET/POST mappings | No PUT/DELETE |
| `controller` | `JobPostingQuestionController` | Controller | JobPosting question admin API | GET/POST mappings | Delete command is soft delete |
| `exception` | `QuestionTemplateNotFoundException` | Exception | Missing template | 404 | Mapped in global handler |
| `exception` | `InvalidQuestionTemplateException` | Exception | Invalid template request | 400 | Mapped in global handler |
| `exception` | `JobPostingQuestionNotFoundException` | Exception | Missing JobPosting question | 404 | Mapped in global handler |
| `exception` | `InvalidJobPostingQuestionException` | Exception | Invalid JobPosting question command | 400 | Mapped in global handler |

## Entity Relationship Summary

- `QuestionTemplate` has no child collection.
- `JobPostingQuestion` has `N:1 JobPosting`.
- `JobPostingQuestion` has nullable `N:1 QuestionTemplate`.
- `JobPosting` has no question collection.
- `QuestionTemplate` has no question collection.
- No cascade or orphanRemoval was added.

## QuestionTemplate Policy

- Global reusable question bank.
- `title`, `questionText`, `category`, `answerType`, `defaultRequired`, `defaultMaxLength`, `active` are required.
- `helperText` is nullable.
- `active` defaults to true.
- Deactivate uses `active=false`.
- Activate uses `active=true` and only applies to an inactive template. Calling it on an active template is rejected with `InvalidQuestionTemplateException` (400).
- Deactivate stays idempotent; only activate validates the current state.
- Update does not reactivate or deactivate. Activation and deactivation are separate commands.
- Inactive template detail lookup is allowed, but inactive templates cannot be used to create new job posting questions.

## JobPostingQuestion Policy

- A `JobPostingQuestion` is the actual question configured for one job posting.
- It stores snapshot fields independently from the template:
  - `questionText`
  - `helperText`
  - `category`
  - `answerType`
  - `required`
  - `minLength`
  - `maxLength`
  - `sortOrder`
  - `active`
- Template changes do not mutate existing job posting questions.
- Listing returns both active and inactive questions sorted by `sortOrder ASC, id ASC`.
- Reorder targets active questions only and returns active questions sorted by `sortOrder ASC, id ASC`.

## Template-Based Create Policy

- `questionTemplateId != null` means template-based creation.
- Template must exist and be active.
- Template values are copied as defaults.
- Request values override copied values when present.
- `sortOrder` is always required from request.
- Final copied/overridden snapshot is validated before save.

## Direct Create Policy

- `questionTemplateId == null` means direct question creation.
- `questionText`, `category`, `answerType`, `required`, `maxLength`, `sortOrder` are required.
- `helperText` and `minLength` are optional.
- Final snapshot is stored directly on `JobPostingQuestion`.

## DRAFT-Only Mutation Policy

- Create, update, reorder, and deactivate are allowed only when `JobPosting.status=DRAFT`.
- `PUBLISHED` and `CLOSED` job postings reject question configuration commands with `InvalidJobPostingQuestionException`.
- Read is allowed regardless of `DRAFT`, `PUBLISHED`, or `CLOSED`.

## Validation Policy

| Rule | Policy |
|---|---|
| `SHORT_TEXT` maxLength | `<= 500` |
| `LONG_TEXT` maxLength | `<= 5000` |
| `maxLength` | `>= 1` |
| `minLength` | nullable, if present `>= 0` |
| `minLength > maxLength` | invalid |
| active sortOrder duplicate | invalid |
| reorder list | must include all active questions exactly once |
| duplicate reorder questionId | invalid |
| duplicate reorder sortOrder | invalid |
| inactive question update/reorder/deactivate | invalid |

## API List

| Method | Path | Description |
|---|---|---|
| GET | `/admin/question-templates` | Template page list, optional `active` filter |
| GET | `/admin/question-templates/{templateId}` | Template detail |
| POST | `/admin/question-templates` | Create template |
| POST | `/admin/question-templates/{templateId}` | Update template |
| POST | `/admin/question-templates/{templateId}/deactivate` | Deactivate template |
| POST | `/admin/question-templates/{templateId}/activate` | Activate inactive template (400 when already active) |
| GET | `/admin/job-postings/{jobPostingId}/questions` | JobPosting question list |
| POST | `/admin/job-postings/{jobPostingId}/questions` | Create direct or template-based question |
| POST | `/admin/job-postings/{jobPostingId}/questions/{questionId}` | Update question snapshot |
| POST | `/admin/job-postings/{jobPostingId}/questions/reorder` | Reorder active questions |
| POST | `/admin/job-postings/{jobPostingId}/questions/{questionId}/delete` | Soft delete question |

## Request/Response DTO Structure

| DTO | Fields |
|---|---|
| `QuestionTemplateCreateRequest` | `title`, `questionText`, `helperText`, `category`, `answerType`, `defaultRequired`, `defaultMaxLength` |
| `QuestionTemplateUpdateRequest` | `title`, `questionText`, `helperText`, `category`, `answerType`, `defaultRequired`, `defaultMaxLength` |
| `QuestionTemplateResponse` | `templateId`, `title`, `questionText`, `helperText`, `category`, `answerType`, `defaultRequired`, `defaultMaxLength`, `active`, `createdAt`, `updatedAt` |
| `JobPostingQuestionCreateRequest` | `questionTemplateId`, `questionText`, `helperText`, `category`, `answerType`, `required`, `minLength`, `maxLength`, `sortOrder` |
| `JobPostingQuestionUpdateRequest` | `questionText`, `helperText`, `category`, `answerType`, `required`, `minLength`, `maxLength`, `sortOrder` |
| `JobPostingQuestionReorderRequest` | `questions` |
| `JobPostingQuestionOrderRequest` | `questionId`, `sortOrder` |
| `JobPostingQuestionResponse` | `questionId`, `questionTemplateId`, `questionText`, `helperText`, `category`, `answerType`, `required`, `minLength`, `maxLength`, `sortOrder`, `active`, `createdAt`, `updatedAt` |

## Test Coverage

- Template create/list/filter/detail/update/deactivate
- Template activate success, already-active rejection, and not-found rejection
- Template page validation
- Service direct-call null request validation
- Template required field and max length policy
- Direct JobPosting question creation
- Template-based JobPosting question creation and override
- Inactive template rejection
- DRAFT-only command policy
- List sorting and inactive visibility
- Update, soft delete, reorder
- Reorder missing/duplicate/foreign question validation
- Duplicate sortOrder validation
- Controller success, validation failure, invalid enum, not-found, and method-not-allowed responses

## Test Commands and Results

| Command | Result |
|---|---|
| `.\gradlew.bat test --tests com.shinyoung.recruit.service.QuestionTemplateServiceTest` | Success |
| `.\gradlew.bat test --tests com.shinyoung.recruit.controller.QuestionTemplateControllerTest` | Success |
| `.\gradlew.bat test --tests com.shinyoung.recruit.service.JobPostingQuestionServiceTest` | Success |
| `.\gradlew.bat test --tests com.shinyoung.recruit.controller.JobPostingQuestionControllerTest` | Success |
| `.\gradlew.bat test --tests com.shinyoung.recruit.service.JobPostingServiceTest` | Success |
| `.\gradlew.bat test --tests com.shinyoung.recruit.controller.JobPostingControllerTest` | Success |
| `.\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationSubmitValidatorTest` | Success |
| `.\gradlew.bat test --tests com.shinyoung.recruit.controller.AdminApplicationSectionControllerTest` | Success |
| `.\gradlew.bat clean test` | Success |

Commands were run with `AES_SECRET_KEY` set in the local PowerShell environment.

## Follow-up Change 2026-08-19: Template Activate Command

The phase originally shipped without a way to restore an inactive template. `POST /admin/question-templates/{templateId}/activate` closes that gap and keeps the existing command-style endpoint convention (`publish`, `close`, `reopen`, `deactivate`) instead of merging both transitions into one toggle endpoint.

| Path | Type | Notes |
|---|---|---|
| `src/main/java/com/shinyoung/recruit/domain/entity/QuestionTemplate.java` | Modified | `activate()` sets `active=true` |
| `src/main/java/com/shinyoung/recruit/service/QuestionTemplateService.java` | Modified | `activateTemplate` + `validateTemplateInactive` |
| `src/main/java/com/shinyoung/recruit/controller/QuestionTemplateController.java` | Modified | `POST /{templateId}/activate` |
| `src/test/java/com/shinyoung/recruit/service/QuestionTemplateServiceTest.java` | Modified | Activate success/already-active/not-found tests |
| `src/test/java/com/shinyoung/recruit/controller/QuestionTemplateControllerTest.java` | Modified | Activate API success test |
| `../../api-contract.md` | Modified | Question template screen contract section |

| Command | Result |
|---|---|
| `.\gradlew.bat test --tests com.shinyoung.recruit.service.QuestionTemplateServiceTest --tests com.shinyoung.recruit.controller.QuestionTemplateControllerTest --tests com.shinyoung.recruit.service.JobPostingQuestionServiceTest --no-daemon` | Success (41 tests, 0 failures) |

`JobPostingQuestion` deliberately gets no activate command in this change. A deactivated question keeps its `sortOrder`, and the duplicate check only covers active rows (`existsByJobPostingIdAndActiveTrueAndSortOrder`), so reactivation can collide with a live question. That needs a sortOrder resolution policy first.

## Known Limitations

- No DB unique constraint for active `sortOrder`; service validation enforces it.
- No title search API for templates.
- No reactivation command for inactive job posting questions; blocked on the `sortOrder` collision policy above.
- No revision/reopen policy for published job posting question changes.
- No answer storage or submit answer validation yet.

## Next Phase Considerations

Recommended next phase: Phase 03c-9-2, implement `ApplicationAnswer` plus applicant question list and answer replace APIs.

After that:

- Phase 03c-9-3: connect required answer validation to `ApplicationSubmitValidator`.
- Phase 03c-9-4: add admin application answer lazy read API.
- Later: question revision/reopen policy, choice options, file answer policy, audit log for answer original text access.
