# Phase 03c-9-4 - Admin Application Answer Lazy Read API

## Phase Summary

Phase 03c-9-4 adds a read-only admin lazy section API for application question/answer content.

The new API lets the admin application detail screen load self-introduction and question answers separately from the root application detail response.

## Implemented Scope

- Added `GET /admin/applications/{applicationId}/answers`.
- Extended the existing `AdminApplicationSectionController`.
- Extended the existing `AdminApplicationSectionService`.
- Added admin-only response DTO `AdminApplicationAnswerResponse`.
- Reads active `JobPostingQuestion` rows for the application's posting.
- Merges `ApplicationAnswer` rows by `jobPostingQuestionId`.
- Returns unanswered active questions as rows with null answer fields.
- Uses answer snapshot metadata first when an answer exists, with current active question metadata as fallback.
- Allows DRAFT, SUBMITTED, and WITHDRAWN application states.
- Checks only that `applicationId` exists.
- Keeps applicant answer APIs, answer save policy, and submit validator unchanged.

## Out of Scope

- Applicant answer save policy changes
- Applicant API path/method changes
- `ApplicationSubmitValidator` changes
- QuestionTemplate / JobPostingQuestion command changes
- Admin application root detail response changes
- Admin list/search/statistics answer text exposure
- Inactive question answer display
- Active-question-external orphan answer display
- Answer revision/history
- Answer read audit logs
- Fine-grained answer-text authorization
- Answer masking
- StageResult
- Choice option domain
- File answer type
- Attachment linkage
- PUT or HTTP DELETE APIs
- SecurityConfig changes

## Changed Files

| Path | Type | Notes |
|---|---|---|
| `src/main/java/com/shinyoung/recruit/dto/response/AdminApplicationAnswerResponse.java` | New | Admin answer lazy read DTO |
| `src/main/java/com/shinyoung/recruit/service/AdminApplicationSectionService.java` | Modified | Added `getAnswers` |
| `src/main/java/com/shinyoung/recruit/controller/AdminApplicationSectionController.java` | Modified | Added `GET /admin/applications/{applicationId}/answers` |
| `src/test/java/com/shinyoung/recruit/service/AdminApplicationSectionServiceTest.java` | Modified | Added admin answer read service tests |
| `src/test/java/com/shinyoung/recruit/controller/AdminApplicationSectionControllerTest.java` | Modified | Added admin answer read controller tests |
| `docs/codex/implementation/phase-03c-9-3-answer-submit-validator.md` | Modified | Added future policy-class refactoring candidate |
| `docs/codex/implementation/phase-03c-9-4-admin-answer-read.md` | New | Phase implementation document |
| `docs/codex/reports/phase-03c-9-4-admin-answer-read.html` | New | Human-readable report |
| `docs/codex/design/phase-03-application-design.md` | Modified | Phase status note |
| `docs/codex/design/phase-03c-application-detail-design.md` | Modified | Phase status note |
| `docs/codex/design/phase-03c-9-question-answer-design.md` | Modified | Phase status note |
| `docs/codex/07-implementation-history.md` | Modified | History entry |

## Class-by-Class Explanation

| Package | Class | Type | Responsibility | Key fields or methods | Related classes | Notes |
|---|---|---|---|---|---|---|
| `dto.response` | `AdminApplicationAnswerResponse` | Response DTO | Represents one active question row and its current admin-visible answer | `of(JobPostingQuestion, ApplicationAnswer)` | `JobPostingQuestion`, `ApplicationAnswer` | Uses answer snapshot first when answer exists |
| `service` | `AdminApplicationSectionService` | Service | Admin read-only lazy section service | `getAnswers` | `JobApplicationRepository`, `JobPostingQuestionRepository`, `ApplicationAnswerRepository` | Existing section service reused |
| `controller` | `AdminApplicationSectionController` | Controller | Admin section HTTP endpoints | `getAnswers` | `ApiResponse`, `AdminApplicationSectionService` | GET only |
| `service test` | `AdminApplicationSectionServiceTest` | Test | Service policy coverage | answer merge, unanswered rows, state access, filtering | `AdminApplicationSectionService` | Covers inactive/foreign answer exclusion |
| `controller test` | `AdminApplicationSectionControllerTest` | Test | API response coverage | success, answer text, unanswered row, not found, unsupported methods | `AdminApplicationSectionController` | Confirms no write method added |

## API List

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/admin/applications/{applicationId}/answers` | Read active posting questions and current answers for one application |

No POST, PUT, or DELETE endpoint was added.

## Response DTO

`AdminApplicationAnswerResponse`

| Field | Source | Notes |
|---|---|---|
| `questionId` | `JobPostingQuestion.id` | Active question id |
| `questionText` | answer snapshot, fallback question | Admin detail display text |
| `category` | answer snapshot, fallback question | `QuestionCategory` |
| `answerType` | answer snapshot, fallback question | `QuestionAnswerType` |
| `required` | answer snapshot, fallback question | Submit-required flag |
| `minLength` | answer snapshot, fallback question | Stored but not enforced in submit yet |
| `maxLength` | answer snapshot, fallback question | Answer length policy |
| `sortOrder` | answer snapshot, fallback question | Display order metadata |
| `answerId` | `ApplicationAnswer.id` | Null when unanswered |
| `answerText` | `ApplicationAnswer.answerText` | Original text returned only in admin detail lazy API |
| `answerUpdatedAt` | `ApplicationAnswer.updatedAt` | Null when unanswered |

## Read Policy

- The service verifies only that `JobApplication` exists.
- Applicant ownership is not checked for admin read.
- DRAFT, SUBMITTED, and WITHDRAWN applications can be read.
- `JobPosting.status`, reception period, and `ApplicationFormConfig` flags do not block admin read.
- Response rows are based on active `JobPostingQuestion` records.
- Sorting follows active question order: `sortOrder ASC, id ASC`.
- If no active questions exist, `data=[]`.
- If a question has no answer, answer fields are null.
- Inactive question answers are not returned.
- Active-question-external orphan answer rows are not returned.

## Privacy and Exposure Policy

- `answerText` is returned as original text only in this admin detail lazy API.
- Admin root detail, admin list, search, statistics, and exports must not include `answerText` by default.
- Free-text answers can contain sensitive personal information.
- Fine-grained original-text authorization, masking, read audit logs, and export policies remain high-priority security-phase TODOs before broadening answer text to any list, search, statistics, export, or report surface.

## Test Coverage

| Test class | Coverage |
|---|---|
| `AdminApplicationSectionServiceTest` | saved answer merge, snapshot metadata preference, unanswered rows, no questions, DRAFT/SUBMITTED/WITHDRAWN access with actual active answer rows, not found, inactive/foreign answer exclusion |
| `AdminApplicationSectionControllerTest` | API success, answer text response, unanswered null fields, not found response, POST/PUT/DELETE unsupported |
| `ApplicationAnswerServiceTest` | Applicant answer API regression |
| `ApplicationAnswerControllerTest` | Applicant answer API regression |
| `ApplicationSubmitValidatorTest` | Submit validation regression |
| `JobApplicationServiceTest` | Submit service regression |
| `ApplicationControllerTest` | Submit API regression |

## Test Commands and Results

| Command | Result |
|---|---|
| `$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.AdminApplicationSectionServiceTest --tests com.shinyoung.recruit.controller.AdminApplicationSectionControllerTest` | Success |
| `$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationAnswerServiceTest --tests com.shinyoung.recruit.controller.ApplicationAnswerControllerTest --tests com.shinyoung.recruit.service.ApplicationSubmitValidatorTest --tests com.shinyoung.recruit.service.JobApplicationServiceTest --tests com.shinyoung.recruit.controller.ApplicationControllerTest` | Success |
| `$env:AES_SECRET_KEY='***'; .\gradlew.bat clean test --no-daemon` | Success |

## Known Limitations

- Inactive/orphan answer display is deferred until revision/history policy is decided.
- No admin answer read audit log exists yet.
- No fine-grained answer-text authorization or masking exists yet.
- Admin exports and statistics must be reviewed separately before including free-text answers.
- Shared answer length constants remain duplicated between save/submit policies. A future `QuestionAnswerPolicy` class is a good refactoring candidate when answer types expand.

## Next Phase Considerations

Recommended next work:

- Confirm answer text authorization, audit logging, and masking policy.
- Decide whether StageResult should come next or whether admin answer read audit/security should be completed first.
