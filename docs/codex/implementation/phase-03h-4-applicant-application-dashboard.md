# Phase 03h-4 - Applicant Application Dashboard API

## Phase Summary

Phase 03h-4 implements a read-only applicant dashboard summary API:

```text
GET /applications/{applicationId:[0-9]+}/dashboard
```

The endpoint returns one current applicant-owned application's high-level status, action flags, completion summary, missing-section guidance, and compact visible result summary. It does not change the submit command, detailed section APIs, applicant StageResult detail API, `SecurityConfig`, DB schema, Attachment readiness policy, message/notification behavior, or read audit logging.

## Purpose

- Let an applicant detail screen load one compact dashboard summary before loading every application section.
- Keep ownership scoped to the current applicant session.
- Mirror current final-submit readiness rules through a read-only checker.
- Return action flags for edit, submit, and withdraw display decisions.
- Keep detailed StageResult rows in `GET /applications/{applicationId}/stage-results`.
- Avoid exposing applicant personal data, answer text, internal result fields, actor fields, correction history, or storage fields.

## Implemented Scope

- Added dashboard response DTO records.
- Added `ApplicationCompletionReadChecker`.
- Added `ApplicationDashboardService`.
- Added applicant-owned dashboard query to `JobApplicationRepository`.
- Added existence methods to optional section repositories for dashboard guidance.
- Added `GET /applications/{applicationId:[0-9]+}/dashboard` to `ApplicationController`.
- Added `ApplicationDashboardServiceTest`.
- Expanded `ApplicationControllerTest` for dashboard API contract, security, ownership hiding, forbidden field absence, and unsupported methods.
- Updated implementation/design/history documents and added a self-contained HTML report.

## Out-of-Scope Items

- `SecurityConfig` changes.
- `ApplicationSubmitValidator` changes.
- `POST /applications/{applicationId}/submit` command changes.
- Detailed section save API changes.
- `GET /applications/{applicationId}/stage-results` changes.
- Applicant result response changes.
- Admin API changes.
- DB schema changes.
- Attachment readiness.
- Per-question detailed error payloads.
- `answerText`, `exemptionReason`, or `certificateNumber` exposure.
- `score`, `comment`, `decidedBy`, `correctedBy`, or correction history exposure.
- Read audit logging.
- Message/notification behavior.
- `PUT`, `PATCH`, or `DELETE` dashboard endpoints.

## Changed Files

| Path | Type | Notes |
|---|---|---|
| `src/main/java/com/shinyoung/recruit/dto/response/ApplicationDashboardResponse.java` | New | Top-level applicant dashboard response |
| `src/main/java/com/shinyoung/recruit/dto/response/ApplicationCompletionSummaryResponse.java` | New | Completion count summary |
| `src/main/java/com/shinyoung/recruit/dto/response/ApplicationSectionReadinessResponse.java` | New | Missing/incomplete section item |
| `src/main/java/com/shinyoung/recruit/service/ApplicationCompletionReadChecker.java` | New | Read-only readiness checker mirroring submit validator policy |
| `src/main/java/com/shinyoung/recruit/service/ApplicationDashboardService.java` | New | Dashboard orchestration service |
| `src/main/java/com/shinyoung/recruit/domain/repository/JobApplicationRepository.java` | Modified | Added dashboard owned fetch query |
| `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationCertificateRepository.java` | Modified | Added `existsByJobApplicationId` |
| `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationLanguageRepository.java` | Modified | Added `existsByJobApplicationId` |
| `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationAwardRepository.java` | Modified | Added `existsByJobApplicationId` |
| `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationGapPeriodRepository.java` | Modified | Added `existsByJobApplicationId` |
| `src/main/java/com/shinyoung/recruit/controller/ApplicationController.java` | Modified | Added dashboard GET endpoint |
| `src/test/java/com/shinyoung/recruit/service/ApplicationDashboardServiceTest.java` | New | Dashboard service/checker policy coverage |
| `src/test/java/com/shinyoung/recruit/controller/ApplicationControllerTest.java` | Modified | Dashboard API/security/field-exclusion coverage |
| `docs/codex/implementation/phase-03h-4-applicant-application-dashboard.md` | New | Implementation reference |
| `docs/codex/reports/phase-03h-4-applicant-application-dashboard.html` | New | Human-readable report |
| `docs/codex/design/phase-03h-3-applicant-application-dashboard-design.md` | Modified | Added implementation note |
| `docs/codex/design/phase-03h-applicant-my-applications-design.md` | Modified | Added Phase 03h-4 note |
| `docs/codex/design/phase-03-application-design.md` | Modified | Added Phase 03h-4 note |
| `docs/codex/07-implementation-history.md` | Modified | Added Phase 03h-4 history entry |

## New Classes

| Package | Class | Class Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.dto.response` | `ApplicationDashboardResponse` | Response DTO | Dashboard top-level response |
| `com.shinyoung.recruit.dto.response` | `ApplicationCompletionSummaryResponse` | Response DTO | Required/optional completion counts and rate |
| `com.shinyoung.recruit.dto.response` | `ApplicationSectionReadinessResponse` | Response DTO | One required missing or optional incomplete item |
| `com.shinyoung.recruit.service` | `ApplicationCompletionReadChecker` | Service | Read-only readiness checker aligned with current submit validator |
| `com.shinyoung.recruit.service` | `ApplicationDashboardService` | Service | Applicant dashboard read orchestration |
| `com.shinyoung.recruit.service` | `ApplicationDashboardServiceTest` | Test | Dashboard service/checker policy coverage |

## Modified Classes

| Package | Class | Class Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.domain.repository` | `JobApplicationRepository` | Repository | Applicant-owned dashboard lookup |
| `com.shinyoung.recruit.domain.repository` | `ApplicationCertificateRepository` | Repository | Optional certificate existence lookup |
| `com.shinyoung.recruit.domain.repository` | `ApplicationLanguageRepository` | Repository | Optional language existence lookup |
| `com.shinyoung.recruit.domain.repository` | `ApplicationAwardRepository` | Repository | Optional award existence lookup |
| `com.shinyoung.recruit.domain.repository` | `ApplicationGapPeriodRepository` | Repository | Optional gap-period existence lookup |
| `com.shinyoung.recruit.controller` | `ApplicationController` | Controller | Exposes applicant dashboard endpoint |
| `com.shinyoung.recruit.controller` | `ApplicationControllerTest` | Test | Adds dashboard API contract and security coverage |

## Class-by-Class Explanation

| Package | Class | Type | Responsibility | Key fields or methods | Related classes | Important implementation notes |
|---|---|---|---|---|---|---|
| `dto.response` | `ApplicationDashboardResponse` | Response DTO | Top-level dashboard response | `from(JobApplication, flags, completion, result summary)` | `JobApplication`, `ApplicationCompletionSummaryResponse`, `ApplicationSectionReadinessResponse` | Uses posting/position snapshots first; does not expose applicant id or internal result fields |
| `dto.response` | `ApplicationCompletionSummaryResponse` | Response DTO | Completion counts | required/optional counts, `requiredCompletionRate`, `submitBlockingIssueCount` | `ApplicationCompletionReadChecker` | Integer floor completion rate; 100 when no required groups |
| `dto.response` | `ApplicationSectionReadinessResponse` | Response DTO | Readiness item | `sectionCode`, `sectionName`, `required`, `complete`, `reasonCode`, `message` | `ApplicationCompletionReadChecker` | Messages are generic and do not include sensitive source values |
| `service` | `ApplicationCompletionReadChecker` | Service | Read-only checker | `check(JobApplication)` | detail section repositories, questions, answers | Mirrors submit validator policy without mutation or submit invocation |
| `service` | `ApplicationDashboardService` | Service | Dashboard orchestration | `getDashboard(applicantId, applicationId)` | `JobApplicationRepository`, `StageResultRepository`, `ApplicationCompletionReadChecker`, `Clock` | Calculates `accepting` and action flags; loads latest visible result summary |
| `domain.repository` | `JobApplicationRepository` | Repository | Owned dashboard fetch | `findDashboardByIdAndApplicantId` | `JobApplication`, `JobPosting`, `JobPosition` | Uses `@EntityGraph` for posting, form config, and position |
| `domain.repository` | Optional section repositories | Repository | Optional row existence | `existsByJobApplicationId` | certificate/language/award/gap entities | Used only for non-blocking guidance |
| `controller` | `ApplicationController` | Controller | Applicant HTTP endpoint | `getDashboard` | `CurrentApplicantService`, `ApplicationDashboardService`, `ApiResponse` | Does not accept `applicantId`; path uses numeric id constraint |
| `service test` | `ApplicationDashboardServiceTest` | Test | Service/checker policy coverage | dashboard policy tests | Mockito repositories, fixed `Clock` | Covers action flags, required readiness, optional guidance, result summary, hidden ownership |
| `controller test` | `ApplicationControllerTest` | Test | API/security coverage | dashboard API tests | `SecurityConfig`, MockMvc | Verifies 200/401/403/404, unsupported methods, and forbidden field absence |

## API List

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| `GET` | `/applications/{applicationId:[0-9]+}/dashboard` | Current applicant reads one owned application dashboard summary | Path: `applicationId` | `ApiResponse<ApplicationDashboardResponse>` |

Unsupported by design:

- `POST /applications/{applicationId}/dashboard`
- `PUT /applications/{applicationId}/dashboard`
- `PATCH /applications/{applicationId}/dashboard`
- `DELETE /applications/{applicationId}/dashboard`

## Response Fields

| Field | Source | Notes |
|---|---|---|
| `applicationId` | `JobApplication.id` | Current application id |
| `jobPostingId` | `JobPosting.id` | Posting id |
| `jobPostingTitle` | snapshot first, current posting title fallback | Stable display |
| `jobPositionName` | snapshot first, current position name fallback | Stable display |
| `applicationStatus` | `JobApplication.status` | `DRAFT`, `SUBMITTED`, `WITHDRAWN` |
| `accepting` | service calculation | `PUBLISHED` and current time inside reception period |
| `editable` | action flag | `DRAFT + accepting` |
| `submittable` | action flag | `DRAFT + accepting + no blocking issue` |
| `withdrawable` | action flag | `SUBMITTED + accepting` |
| `submittedAt` | `JobApplication.submittedAt` | Nullable |
| `withdrawnAt` | `JobApplication.withdrawnAt` | Nullable |
| `completionSummary` | read checker | Required/optional counts and completion rate |
| `requiredMissingSections` | read checker | Submit-blocking readiness issues |
| `optionalIncompleteSections` | read checker | Non-blocking optional hints |
| `latestAnnouncedStageName` | visible StageResult summary | Nullable |
| `latestResultStatus` | visible StageResult summary | Nullable |

Explicitly not exposed:

- `applicantId`
- applicant name/email/phone/CI/address
- `stageResultId`
- `score`
- `comment`
- `decidedBy`
- `correctedBy`
- correction reason/history
- `storedFileName`
- `storagePath`
- `answerText`
- `exemptionReason`
- `certificateNumber`

## Entity Relationship Summary

```text
Applicant 1 : N JobApplication
JobApplication N : 1 JobPosting
JobPosting 1 : 1 ApplicationFormConfig
JobApplication N : 1 JobPosition
JobApplication 1 : N detail sections
JobApplication 1 : N ApplicationAnswer
JobPosting 1 : N JobPostingQuestion
JobApplication 1 : N StageResult
StageResult N : 1 Stage
```

Read flow:

1. Controller resolves current applicant through `CurrentApplicantService`.
2. `ApplicationDashboardService.getDashboard` loads `applicationId + applicantId`.
3. Missing or non-owned application raises `JobApplicationNotFoundException`.
4. Service calculates `accepting` from posting status and reception period.
5. `ApplicationCompletionReadChecker.check` calculates completion summary and readiness items.
6. Service loads visible StageResult rows through existing applicant-visible query.
7. Service selects the latest visible stage by `stageOrder DESC, stage.id DESC`.
8. Service calculates action flags and maps to `ApplicationDashboardResponse`.

## Business Rules

### Access

- The endpoint is applicant-only through existing `/applications/**` URL authorization.
- The request does not accept `applicantId`.
- Ownership is checked by `applicationId + applicantId`.
- Another applicant's application uses the same hidden 404 policy as existing applicant application reads.

### Action Flags

| Flag | Policy |
|---|---|
| `accepting` | `JobPosting.status == PUBLISHED` and current time is inside reception period |
| `editable` | `application.status == DRAFT && accepting` |
| `submittable` | `application.status == DRAFT && accepting && submitBlockingIssueCount == 0` |
| `withdrawable` | `application.status == SUBMITTED && accepting` |
| `WITHDRAWN` | All command flags are false |

### Completion Checker

Required blocking checks:

| Section | Reason |
|---|---|
| `FORM_CONFIG / MISSING_CONFIG` | posting has no `ApplicationFormConfig`; dashboard blocks submit to match submit command policy |
| `EDUCATION / MISSING_ROW` | `useEducation=true` and no education row |
| `CAREER / MISSING_PROFILE` | `useCareer=true` and no career profile |
| `CAREER / TYPE_NOT_SELECTED` | career type is null or `NOT_SELECTED` |
| `CAREER / MISSING_ROW` | `EXPERIENCED` without career rows |
| `CAREER / INVALID_DISALLOWED_ROW` | `NEWCOMER` or `NOT_APPLICABLE` with career rows |
| `MILITARY / MISSING_ROW` | `useMilitary=true` and no military row |
| `MILITARY / TYPE_NOT_SELECTED` | military subject type is null |
| `MILITARY / MISSING_PERIOD` | `COMPLETED` without service period |
| `MILITARY / MISSING_REASON` | `EXEMPTED` with blank exemption reason |
| `QUESTION / MISSING_REQUIRED_ANSWER` | active required question has no answer or blank answer |
| `QUESTION / INVALID_LENGTH` | present answer exceeds effective max length or type hard limit |

Optional guidance:

| Section | Reason |
|---|---|
| `CERTIFICATE / OPTIONAL_EMPTY` | `useCertificate=true` and no certificate row |
| `LANGUAGE / OPTIONAL_EMPTY` | `useLanguage=true` and no language row |
| `AWARD / OPTIONAL_EMPTY` | `useAward=true` and no award row |
| `GAP_PERIOD / OPTIONAL_EMPTY` | `useGapPeriod=true` and no gap-period row |

Count policy:

- `requiredSectionCount` is group-based.
- `FORM_CONFIG` counts as a required blocking group when `ApplicationFormConfig` is missing.
- `EDUCATION`, `CAREER`, and `MILITARY` count when their config flag is enabled.
- `QUESTION` counts when active required questions exist or when an active answer has a blocking length issue.
- `completedRequiredSectionCount = requiredSectionCount - required groups with issues`.
- `requiredMissingCount = requiredMissingSections.size()`.
- `submitBlockingIssueCount = requiredMissingSections.size()`.
- `requiredCompletionRate = 100` when no required group exists, otherwise integer floor `(completedRequiredSectionCount * 100) / requiredSectionCount`.
- Optional counts are group-based for enabled optional sections.

### Result Summary

- Uses existing applicant-visible query: `findVisibleByJobApplicationIdForApplicant`.
- Visible stages are `Stage.status == RESULT_ANNOUNCED || CLOSED`.
- Latest visible result uses `stageOrder DESC, stage.id DESC`.
- Detailed result rows remain in `GET /applications/{applicationId}/stage-results`.
- Internal result fields remain hidden.

## Test Coverage

| Test Class | Coverage |
|---|---|
| `ApplicationDashboardServiceTest` | DRAFT/submitted/withdrawn/closed action flags, hidden ownership, education/career/military/question required readiness, answer length, optional guidance, latest result summary |
| `ApplicationControllerTest` | Dashboard success response, required missing response, 401 JSON, 403 JSON, other applicant 404, forbidden field absence, unsupported methods |
| `JobApplicationServiceTest` | Regression for `GET /applications/me` and existing application commands |
| `ApplicationSubmitValidatorTest` | Regression for unchanged submit validator policy |
| `ApplicationStageResultServiceTest` / `ApplicationStageResultControllerTest` | Regression for existing detailed applicant result API |

## Test Commands

Executed:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationDashboardServiceTest
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.ApplicationControllerTest
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.JobApplicationServiceTest
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationSubmitValidatorTest
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationStageResultServiceTest --tests com.shinyoung.recruit.controller.ApplicationStageResultControllerTest
```

Full regression command executed:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test --no-daemon
```

## Test Results

| Command | Result | Notes |
|---|---|---|
| `ApplicationDashboardServiceTest` | Success | First run exposed Mockito helper stubbing issue; test helper was fixed and retry passed |
| `ApplicationControllerTest` | Success | Dashboard and existing application controller coverage passed |
| `JobApplicationServiceTest` | Success | Existing my applications and command coverage passed |
| `ApplicationSubmitValidatorTest` | Success | Submit validator remained unchanged |
| `ApplicationStageResultServiceTest` + `ApplicationStageResultControllerTest` | Success | Existing applicant StageResult detail API passed |
| `clean test --no-daemon` | Success | Full suite passed after documentation update |

## Known Limitations

- Attachment readiness is not implemented because `ApplicationFormConfig` has no attachment flag.
- Per-question detailed error payloads are not implemented.
- `ApplicationCompletionReadChecker` intentionally mirrors validator policy and may duplicate some rules until the read/write contract stabilizes.
- Optional section completion is row-existence based only.
- Result summary still follows `Stage.status` visibility and does not add a scheduled release guard by `resultAnnouncementDateTime`.

## Remaining Issues

- Decide whether Phase 03h-5 should add richer missing-section details.
- Decide whether Attachment needs a config flag and dashboard readiness rule.
- Consider extracting shared question answer length policy if answer types expand.
- Consider extracting shared submit readiness policy only after dashboard and submit semantics stabilize.

## Next Phase Considerations

Recommended Phase 03h-5 candidates:

- Completion checker/detail improvement.
- Per-question missing detail without exposing answer text.
- Attachment readiness policy after config/business rule decision.
- Shared read/write validation policy extraction if duplication becomes risky.
