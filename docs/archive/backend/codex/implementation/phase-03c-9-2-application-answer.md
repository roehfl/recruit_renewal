# Phase 03c-9-2 - ApplicationAnswer + Applicant Question/Answer API

## Phase Summary

Phase 03c-9-2 implements the applicant-side question/answer slice for the Application question domain. Applicants can read active job posting questions for their own application and replace-save their answers while the application is still writable.

This phase intentionally stops before submit validator answer integration and admin answer read APIs.

## Implemented Scope

- `ApplicationAnswer` entity
- `ApplicationAnswerRepository`
- Applicant question/answer request and response DTOs
- `ApplicationAnswerService`
- `ApplicationAnswerController`
- `InvalidApplicationAnswerException` and global 400 mapping
- `GET /applications/{applicationId}/questions`
- `POST /applications/{applicationId}/answers`
- Active `JobPostingQuestion` based applicant question list
- Answer replace save with application-level delete then save
- `JobPostingQuestion` snapshot storage on answer save
- DRAFT save length validation
- Service and controller tests
- Phase documentation and HTML report

## Out of Scope

- `ApplicationSubmitValidator` answer integration
- Admin answer read API: `GET /admin/applications/{applicationId}/answers`
- Applicant answer-only read API: `GET /applications/{applicationId}/answers`
- QuestionTemplate / JobPostingQuestion behavior or API changes
- Choice option domain
- File answer type
- Attachment linkage
- QuestionSet
- StageResult
- PUT and HTTP DELETE APIs
- Individual answer delete API
- Physical delete API exposed over HTTP
- SecurityConfig changes
- CommonCode conversion

## Changed Files

| Path | Type | Notes |
|---|---|---|
| `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationAnswer.java` | New | Applicant answer entity with question snapshot fields |
| `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationAnswerRepository.java` | New | Answer repository |
| `src/main/java/com/shinyoung/recruit/dto/request/ApplicationAnswerRequest.java` | New | Single answer request item |
| `src/main/java/com/shinyoung/recruit/dto/request/ApplicationAnswerReplaceRequest.java` | New | Replace request wrapper |
| `src/main/java/com/shinyoung/recruit/dto/response/ApplicationQuestionResponse.java` | New | Question + current answer response |
| `src/main/java/com/shinyoung/recruit/service/ApplicationAnswerService.java` | New | Applicant question read and answer replace policy |
| `src/main/java/com/shinyoung/recruit/controller/ApplicationAnswerController.java` | New | Applicant question/answer API |
| `src/main/java/com/shinyoung/recruit/exception/InvalidApplicationAnswerException.java` | New | Invalid answer payload exception |
| `src/main/java/com/shinyoung/recruit/exception/GlobalExceptionHandler.java` | Modified | 400 mapping for answer exception |
| `src/test/java/com/shinyoung/recruit/service/ApplicationAnswerServiceTest.java` | New | Service coverage |
| `src/test/java/com/shinyoung/recruit/controller/ApplicationAnswerControllerTest.java` | New | API coverage |
| `docs/codex/implementation/phase-03c-9-2-application-answer.md` | New | Phase implementation document |
| `docs/codex/reports/phase-03c-9-2-application-answer.html` | New | Human-readable report |
| `docs/codex/design/phase-03-application-design.md` | Modified | Phase status note |
| `docs/codex/design/phase-03c-application-detail-design.md` | Modified | Phase status note and next phase |
| `docs/codex/design/phase-03c-9-question-answer-design.md` | Modified | Implementation reflection |
| `docs/codex/07-implementation-history.md` | Modified | History entry |

## Class-by-Class Explanation

| Package | Class | Type | Responsibility | Key fields or methods | Related classes | Notes |
|---|---|---|---|---|---|---|
| `domain.entity` | `ApplicationAnswer` | Entity | Stores one answer for one application question | `jobApplication`, `jobPostingQuestion`, `answerText`, snapshot fields, `create`, `updateAnswer` | `JobApplication`, `JobPostingQuestion` | No reverse collection, no cascade/orphanRemoval |
| `domain.repository` | `ApplicationAnswerRepository` | Repository | Persists and queries answers by application/question | `findByJobApplicationId`, `deleteByJobApplicationId`, `existsBy...` | `ApplicationAnswer` | Replace save uses application-wide delete |
| `dto.request` | `ApplicationAnswerRequest` | Request DTO | Single answer payload | `questionId`, `answerText` | `ApplicationAnswerReplaceRequest` | `answerText` nullable, max 5000 |
| `dto.request` | `ApplicationAnswerReplaceRequest` | Request DTO | Replace answer list payload | `answers` | `ApplicationAnswerRequest` | Empty list is allowed; null list is invalid |
| `dto.response` | `ApplicationQuestionResponse` | Response DTO | Active question plus current answer | `questionId`, question policy fields, `answerId`, `answerText`, `updatedAt` | `JobPostingQuestion`, `ApplicationAnswer` | Response shape is shared by GET and POST |
| `service` | `ApplicationAnswerService` | Service | Applicant question read and answer replace business rules | `getQuestions`, `replaceAnswers` | `ApplicationSectionAccessService`, repositories | Reuses applicant ownership/writable policy |
| `controller` | `ApplicationAnswerController` | Controller | Applicant HTTP API | `getQuestions`, `replaceAnswers` | `CurrentApplicantService`, `ApiResponse` | GET questions and POST answers only |
| `exception` | `InvalidApplicationAnswerException` | Exception | Invalid answer request | constructor | `GlobalExceptionHandler` | Mapped to 400 |
| `exception` | `GlobalExceptionHandler` | Modified | API error mapping | `handleInvalidApplicationAnswer` | `ApiResponse` | Existing response format retained |
| `service test` | `ApplicationAnswerServiceTest` | Test | Service policy coverage | read/save/failure cases | `ApplicationAnswerService` | Includes snapshot, DRAFT, length, ownership |
| `controller test` | `ApplicationAnswerControllerTest` | Test | API contract coverage | success/failure/method not allowed | `ApplicationAnswerController` | Confirms unsupported APIs are not added |

## Entity Relationship Summary

- `ApplicationAnswer` has `N:1 JobApplication`.
- `ApplicationAnswer` has `N:1 JobPostingQuestion`.
- `JobApplication` has no answer collection.
- `JobPostingQuestion` has no answer collection.
- No cascade or orphanRemoval was added.
- DB unique candidate is implemented as `job_application_id + job_posting_question_id`.

## ApplicationAnswer Snapshot Policy

Each saved answer stores the current question policy from `JobPostingQuestion`:

- `questionTextSnapshot`
- `categorySnapshot`
- `answerTypeSnapshot`
- `requiredSnapshot`
- `minLengthSnapshot`
- `maxLengthSnapshot`
- `sortOrderSnapshot`

Snapshots are written on answer save. This phase does not resync snapshots at final submit time.

## Applicant Question List Policy

- `GET /applications/{applicationId}/questions` uses applicant ownership validation.
- DRAFT, SUBMITTED, and WITHDRAWN applications can be read.
- Questions are selected from active `JobPostingQuestion` rows for the application's `JobPosting`.
- Inactive questions are hidden from the applicant question list in this phase.
- Answers are queried by `applicationId` and merged by `jobPostingQuestionId`.
- Missing answer returns `answerId=null`, `answerText=null`, `updatedAt=null`.
- Response sorting follows question `sortOrder ASC, id ASC`.
- If no active questions exist, data is an empty array.

## Answer Replace Save Policy

- `POST /applications/{applicationId}/answers` uses applicant ownership validation.
- Save is allowed only through the existing writable policy:
  - application status is `DRAFT`
  - `JobPosting.status=PUBLISHED`
  - current time is inside the reception period
- Request body and `answers` list must not be null.
- Empty `answers` list is allowed and deletes all existing answers for the application.
- Duplicate `questionId` is invalid.
- Every `questionId` must belong to an active question on the application's `JobPosting`.
- Other posting questions, inactive questions, and nonexistent question ids are invalid.
- Existing answer rows are deleted by `applicationId`, then requested rows are saved.
- If a request item is present, a row is saved even when `answerText` is null or blank.

## DRAFT Save and Validation Policy

| Rule | Policy |
|---|---|
| required question blank answer | Allowed during DRAFT save |
| null answerText | Allowed |
| blank answerText | Allowed |
| `answerText.length() > question.maxLength` | Invalid |
| SHORT_TEXT answer | Must be 500 characters or less |
| LONG_TEXT answer | Must be 5000 characters or less |
| minLength | Not enforced during DRAFT save in this phase |
| submit required validation | Deferred to Phase 03c-9-3 |

## API List

| Method | Path | Description | Request | Response |
|---|---|---|---|---|
| GET | `/applications/{applicationId}/questions` | Applicant reads active posting questions with current answers | None | `ApiResponse<List<ApplicationQuestionResponse>>` |
| POST | `/applications/{applicationId}/answers` | Applicant replace-saves answers | `ApplicationAnswerReplaceRequest` | `ApiResponse<List<ApplicationQuestionResponse>>` |

No `GET /applications/{applicationId}/answers`, admin answer API, PUT, or HTTP DELETE was added.

## Request/Response DTO Structure

| DTO | Fields |
|---|---|
| `ApplicationAnswerReplaceRequest` | `answers` |
| `ApplicationAnswerRequest` | `questionId`, `answerText` |
| `ApplicationQuestionResponse` | `questionId`, `questionText`, `helperText`, `category`, `answerType`, `required`, `minLength`, `maxLength`, `sortOrder`, `answerId`, `answerText`, `updatedAt` |

## Test Coverage

- Active question list read
- Empty question list returns `[]`
- Existing answer merge into question response
- Inactive questions hidden from applicant question list
- Sort order by `sortOrder ASC, id ASC`
- Read allowed for DRAFT/SUBMITTED/WITHDRAWN
- Other applicant access hidden as 404
- Answer replace save success
- Multiple answer replace and get
- Null/blank answer text allowed
- Required question blank allowed in DRAFT
- Empty answer list deletes existing answers
- Replace leaves only newly requested answers
- Snapshot fields saved on `ApplicationAnswer`
- SUBMITTED/WITHDRAWN save failure
- Reception period and non-PUBLISHED posting save failure
- Null request/list failure
- Null, duplicate, nonexistent, foreign, and inactive question id failure
- Question maxLength, SHORT_TEXT, and LONG_TEXT length failure
- Controller success and `ApiResponse` shape
- Unsupported PUT/DELETE and unimplemented answer paths
- Existing question template, job posting question, submit validator, and application controller regressions

## Test Commands and Results

Commands were run with `AES_SECRET_KEY` set in the local PowerShell environment.

| Command | Result |
|---|---|
| `.\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationAnswerServiceTest` | Success |
| `.\gradlew.bat test --tests com.shinyoung.recruit.controller.ApplicationAnswerControllerTest` | Success |
| `.\gradlew.bat test --tests com.shinyoung.recruit.service.QuestionTemplateServiceTest` | Success |
| `.\gradlew.bat test --tests com.shinyoung.recruit.controller.QuestionTemplateControllerTest` | Success |
| `.\gradlew.bat test --tests com.shinyoung.recruit.service.JobPostingQuestionServiceTest` | Success |
| `.\gradlew.bat test --tests com.shinyoung.recruit.controller.JobPostingQuestionControllerTest` | Success |
| `.\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationSubmitValidatorTest` | Success |
| `.\gradlew.bat test --tests com.shinyoung.recruit.controller.ApplicationControllerTest` | Success |
| `.\gradlew.bat clean test` | Success |

The focused regression classes were also run together in one Gradle invocation and passed.

## Known Limitations

- Submit validator does not yet check required question answers.
- Admin answer lazy read API is not implemented.
- Inactive question answers are not shown to applicants in `GET /questions`.
- No question revision/reopen policy exists for published postings.
- No choice option, file answer, or Attachment integration exists.
- No answer original-text audit log or role-based masking policy exists yet.

## Next Phase Considerations

Recommended next phase: Phase 03c-9-3, connect active required `JobPostingQuestion` answer checks to `ApplicationSubmitValidator`.

After that:

- Phase 03c-9-4: admin application answer lazy read API.
- Later: question revision/reopen policy, inactive question historical display, choice options, file answer policy, answer access audit logging.
