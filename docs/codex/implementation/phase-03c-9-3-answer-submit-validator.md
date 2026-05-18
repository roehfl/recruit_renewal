# Phase 03c-9-3 - ApplicationSubmitValidator Question/Answer Integration

## Phase Summary

Phase 03c-9-3 connects the applicant question/answer domain to final submit validation.

The applicant answer API still allows incomplete DRAFT answers, but final submit now validates active posting questions and blocks required question blanks or length violations.

## Implemented Scope

- Added question/answer validation to `ApplicationSubmitValidator`.
- Loaded active `JobPostingQuestion` rows by application posting.
- Loaded `ApplicationAnswer` rows by application id and matched them by question id.
- Required active questions fail submit when the answer row is missing, `answerText` is null, or `answerText` is blank.
- Optional questions may be unanswered or blank.
- Any present non-null answer is checked against question `maxLength`.
- Answer type hard limits are enforced defensively:
  - `SHORT_TEXT`: 500 characters
  - `LONG_TEXT`: 5000 characters
- Existing `InvalidJobApplicationException` based 400 response policy is retained.
- Submit state transition order is unchanged: the validator runs before `application.submit(now)`.
- Service and controller tests were expanded for answer submit validation.

## Out of Scope

- `ApplicationAnswer` entity structure changes
- Applicant answer API path/method changes
- Applicant answer save policy changes
- `GET /applications/{applicationId}/answers`
- Admin answer read API: `GET /admin/applications/{applicationId}/answers`
- QuestionTemplate / JobPostingQuestion admin API changes
- Choice option domain
- File answer type
- Attachment linkage
- QuestionSet
- StageResult
- PUT or HTTP DELETE APIs
- SecurityConfig changes
- CommonCode conversion

## Changed Files

| Path | Type | Notes |
|---|---|---|
| `src/main/java/com/shinyoung/recruit/service/ApplicationSubmitValidator.java` | Modified | Added question/answer submit validation |
| `src/test/java/com/shinyoung/recruit/service/ApplicationSubmitValidatorTest.java` | Modified | Added answer validation unit cases |
| `src/test/java/com/shinyoung/recruit/service/JobApplicationServiceTest.java` | Modified | Added submit integration cases for required answers |
| `src/test/java/com/shinyoung/recruit/controller/ApplicationControllerTest.java` | Modified | Added submit API failure case for missing required answer |
| `src/test/java/com/shinyoung/recruit/service/ApplicationAnswerServiceTest.java` | Modified | Adjusted status-fixture questions so submit setup matches new validator |
| `src/test/java/com/shinyoung/recruit/controller/ApplicationAnswerControllerTest.java` | Modified | Adjusted submitted-state fixture question to optional |
| `docs/codex/implementation/phase-03c-9-3-answer-submit-validator.md` | New | Phase implementation document |
| `docs/codex/reports/phase-03c-9-3-answer-submit-validator.html` | New | Human-readable report |
| `docs/codex/design/phase-03-application-design.md` | Modified | Phase status and next phase note |
| `docs/codex/design/phase-03c-application-detail-design.md` | Modified | Phase status and next phase note |
| `docs/codex/design/phase-03c-9-question-answer-design.md` | Modified | Implementation reflection and next phase note |
| `docs/codex/07-implementation-history.md` | Modified | History entry |

## Class-by-Class Explanation

| Package | Class | Type | Responsibility | Key fields or methods | Related classes | Notes |
|---|---|---|---|---|---|---|
| `service` | `ApplicationSubmitValidator` | Service | Validates final submit readiness | `validate`, `validateAnswers`, `validateRequiredAnswer`, `validateAnswerLength` | `JobPostingQuestionRepository`, `ApplicationAnswerRepository` | No status transition; throws `InvalidJobApplicationException` |
| `service test` | `ApplicationSubmitValidatorTest` | Test | Unit coverage for submit validation policies | Required/optional/missing/blank/maxLength/type-limit cases | `ApplicationSubmitValidator` | Uses mocks for question and answer repositories |
| `service test` | `JobApplicationServiceTest` | Test | Integration coverage for submit state behavior | submit success with answer, failure without answer | `JobApplicationService`, `ApplicationAnswerService` | Confirms status stays `DRAFT` and `submittedAt` stays null on failure |
| `controller test` | `ApplicationControllerTest` | Test | API response coverage for submit answer validation | `POST /applications/{applicationId}/submit` failure case | `ApplicationController` | Confirms 400 + `ApiResponse.fail` |
| `service test` | `ApplicationAnswerServiceTest` | Test | Applicant answer API regression coverage | status fixture updates | `ApplicationAnswerService` | Existing answer save policy unchanged |
| `controller test` | `ApplicationAnswerControllerTest` | Test | Applicant answer API regression coverage | submitted-state fixture update | `ApplicationAnswerController` | Existing API path/method unchanged |

## Validator Flow

`JobApplicationService.submit(applicantId, applicationId)` keeps the existing order:

1. Find the applicant-owned application.
2. Validate base submit rules:
   - application status is `DRAFT`
   - `JobPosting.status=PUBLISHED`
   - reception period is open
   - `ApplicationFormConfig` exists
3. Call `ApplicationSubmitValidator.validate(application)`.
4. If validation passes, call `application.submit(now)`.
5. Return the application id.

Question/answer validation is inside step 3 and runs before `submittedAt` is set.

## Question/Answer Submit Validation Policy

| Case | Policy |
|---|---|
| No active questions | Pass |
| `required=true` and no answer row | Fail |
| `required=true` and `answerText=null` | Fail |
| `required=true` and blank `answerText` | Fail |
| `required=false` and no answer row | Pass |
| `required=false` and null/blank answer | Pass |
| Non-null answer exceeds question `maxLength` | Fail |
| `maxLength=null` | Use answer type default |
| `SHORT_TEXT` answer exceeds 500 | Fail |
| `LONG_TEXT` answer exceeds 5000 | Fail |
| inactive question | Ignored |
| answer row outside active question set | Ignored |
| `minLength` | Stored but not enforced in this phase |

## Required Question Policy

Required active questions are final-submit required only.

DRAFT answer save still permits null and blank answers, including required questions. This keeps incremental draft editing ergonomic while making final submit strict.

## Optional Question Policy

Optional questions do not block submit when unanswered or blank. If an optional answer is present and non-null, length limits still apply.

## Length Policy

Submit revalidates the same effective length boundaries used by answer save:

- Question `maxLength` is the first boundary.
- If question `maxLength` is unavailable, answer type default is used.
- Answer type hard limits are also checked even when `maxLength` is larger:
  - `SHORT_TEXT <= 500`
  - `LONG_TEXT <= 5000`

## Deferred Policies

- `minLength` is not enforced at submit yet.
- Inactive question answers are not validated.
- Answer rows that do not belong to the current active question set are not treated as submit failures.
- Historical display, revision, and orphan-answer cleanup policies remain separate follow-up work.

## API List

No new API was added.

The existing submit API has stricter validation:

| Method | Path | Change |
|---|---|---|
| `POST` | `/applications/{applicationId}/submit` | Fails when active required question answers are missing/blank or answers exceed length limits |

## Test Coverage

| Test class | Added or covered cases |
|---|---|
| `ApplicationSubmitValidatorTest` | active question absence, required answer present/missing/null/blank, optional unanswered/blank, inactive question ignored, foreign answer ignored, maxLength and type limits |
| `JobApplicationServiceTest` | submit succeeds with required answer, fails without required answer, failure keeps `DRAFT` and `submittedAt=null` |
| `ApplicationControllerTest` | submit required answer missing returns 400 + `ApiResponse.fail` |
| `ApplicationAnswerServiceTest` | regression for DRAFT/SUBMITTED/WITHDRAWN read and non-writable save cases |
| `ApplicationAnswerControllerTest` | regression for submitted application answer replace failure |

## Test Commands and Results

| Command | Result |
|---|---|
| `$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationSubmitValidatorTest` | Success |
| `$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.JobApplicationServiceTest --tests com.shinyoung.recruit.controller.ApplicationControllerTest --tests com.shinyoung.recruit.service.ApplicationAnswerServiceTest --tests com.shinyoung.recruit.controller.ApplicationAnswerControllerTest --tests com.shinyoung.recruit.service.QuestionTemplateServiceTest --tests com.shinyoung.recruit.controller.QuestionTemplateControllerTest --tests com.shinyoung.recruit.service.JobPostingQuestionServiceTest --tests com.shinyoung.recruit.controller.JobPostingQuestionControllerTest` | Success |
| `$env:AES_SECRET_KEY='***'; .\gradlew.bat clean test --no-daemon` | Success |

## Known Limitations

- Admin answer lazy read API is still missing.
- `minLength` is stored but not enforced.
- Active-question-external answer rows are ignored rather than cleaned up.
- No answer revision/history policy exists yet.
- No choice, file, or Attachment-linked question answer is implemented.

## Next Phase Considerations

Recommended next phase:

- Phase 03c-9-4: Admin answer lazy read API
  - `GET /admin/applications/{applicationId}/answers`
  - Read-only DTO with question metadata and answer text
  - Admin detail only, not admin list
  - Document privacy, authorization, and audit-log follow-up policies
