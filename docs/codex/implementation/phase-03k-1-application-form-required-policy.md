# Phase 03k-1 - Public Application Form Required Policy

## Phase Name

Phase 03k-1 - Public Application Form Required Policy

## Purpose

Expose a read-only application form required/optional policy in public job posting list/detail responses so applicants can see which application sections are required before starting an application.

This implementation intentionally reflects the current submit-validator behavior without changing the database schema, entity fields, admin create/update payloads, submit validation, dashboard completion checks, or attachment policy.

## Scope

- Add `applicationFormRequiredPolicy` to public `GET /job-postings` list items.
- Add `applicationFormRequiredPolicy` to public `GET /job-postings/{id}` detail response.
- Calculate section policy from existing `ApplicationFormConfig.useXxx` fields.
- Calculate question policy from active `JobPostingQuestion.required` values.
- Batch-load active/required question counts for public list responses.
- Keep public list `applicationFormConfig` hidden.
- Keep public detail `applicationFormConfig` response for backward compatibility and add the policy next to it.
- Guard public response conversion when legacy data has no `ApplicationFormConfig`.
- Add service and controller tests for policy response fields and question counts.
- Create a paired human-readable HTML report.
- Update implementation history.

## Out-of-Scope Items

- No DB migration.
- No new `requireXxx` columns.
- No `ApplicationFormConfig` entity field additions.
- No admin create/update request changes.
- No admin response contract changes.
- No final-submit validator changes.
- No applicant dashboard completion policy changes.
- No attachment-required implementation.
- No endpoint path changes.
- No security, LDAP, SMS, email, statistics, Excel, or PDF changes.

## Design Conflict Note

`docs/codex/design/phase-03k-application-form-required-policy-design.md` describes the target end-to-end split between `useXxx` and `requireXxx` fields. The Phase 03k-1 instruction narrowed this implementation to a public read-only compatibility slice and explicitly prohibited schema/entity/submit/dashboard changes.

Therefore this phase does not complete the target Phase 03k design. It exposes the current effective policy only:

- `EDUCATION`, `CAREER`, and `MILITARY` are required when their existing `useXxx` flag is `true`.
- `CERTIFICATE`, `LANGUAGE`, `AWARD`, and `GAP_PERIOD` are optional when their existing `useXxx` flag is `true`.
- `QUESTION` is required when at least one active posting question is required.
- `ATTACHMENT` is always deferred and not required.

## Changed Files

| Path | Change Type | Notes |
|---|---|---|
| `src/main/java/com/shinyoung/recruit/domain/repository/JobPostingRepository.java` | Modified | Public list projection query now left-joins `ApplicationFormConfig` use flags. |
| `src/main/java/com/shinyoung/recruit/domain/repository/JobPostingPublicListProjection.java` | Modified | Adds nullable `useXxx` getters for public list policy calculation. |
| `src/main/java/com/shinyoung/recruit/domain/repository/JobPostingQuestionRepository.java` | Modified | Adds batch active/required question count query. |
| `src/main/java/com/shinyoung/recruit/domain/repository/JobPostingQuestionPolicyCount.java` | New | Projection DTO for active and required question counts. |
| `src/main/java/com/shinyoung/recruit/enumeration/ApplicationFormRequirementType.java` | New | Enum for public section requirement type values. |
| `src/main/java/com/shinyoung/recruit/dto/response/ApplicationFormRequiredPolicyResponse.java` | New | Public required/optional policy response. |
| `src/main/java/com/shinyoung/recruit/dto/response/ApplicationFormSectionPolicyResponse.java` | New | Public per-section policy response. |
| `src/main/java/com/shinyoung/recruit/dto/response/ApplicationFormConfigPublicResponse.java` | Modified | Handles missing config as all `false` instead of throwing. |
| `src/main/java/com/shinyoung/recruit/dto/response/JobPostingPublicListResponse.java` | Modified | Adds `applicationFormRequiredPolicy`. |
| `src/main/java/com/shinyoung/recruit/dto/response/JobPostingPublicDetailResponse.java` | Modified | Adds `applicationFormRequiredPolicy`. |
| `src/main/java/com/shinyoung/recruit/service/JobPostingPublicService.java` | Modified | Loads question counts and passes policy inputs to public DTOs. |
| `src/test/java/com/shinyoung/recruit/service/JobPostingPublicServiceTest.java` | Modified | Adds policy and active-question count tests. |
| `src/test/java/com/shinyoung/recruit/controller/JobPostingPublicControllerTest.java` | Modified | Adds JSON contract assertions for policy fields. |
| `src/test/java/com/shinyoung/recruit/dto/response/ApplicationFormPolicyResponseTest.java` | New | Adds null config guard DTO tests. |
| `docs/codex/implementation/phase-03k-1-application-form-required-policy.md` | New | Codex implementation reference. |
| `docs/codex/reports/phase-03k-1-application-form-required-policy.html` | New | Human-readable implementation report. |
| `docs/codex/07-implementation-history.md` | Modified | Adds Phase 03k-1 implementation history. |
| `docs/codex/design/phase-03k-application-form-required-policy-design.md` | Modified | Adds note that Phase 03k-1 is a narrowed read-only compatibility slice. |
| `docs/codex/reports/phase-03k-application-form-required-policy-design.html` | Modified | Mirrors the Phase 03k-1 compatibility-slice note in the design report. |

## New Classes

- `JobPostingQuestionPolicyCount`
- `ApplicationFormRequirementType`
- `ApplicationFormRequiredPolicyResponse`
- `ApplicationFormSectionPolicyResponse`

## Modified Classes

- `JobPostingRepository`
- `JobPostingPublicListProjection`
- `JobPostingQuestionRepository`
- `ApplicationFormConfigPublicResponse`
- `JobPostingPublicListResponse`
- `JobPostingPublicDetailResponse`
- `JobPostingPublicService`
- `JobPostingPublicServiceTest`
- `JobPostingPublicControllerTest`

## Class-by-Class Explanation

| Package | Class | Type | Responsibility | Key Fields or Methods | Related Classes | Notes |
|---|---|---|---|---|---|---|
| `com.shinyoung.recruit.domain.repository` | `JobPostingQuestionPolicyCount` | Repository projection | Carries active and required question counts per posting. | `jobPostingId`, `activeQuestionCount`, `requiredQuestionCount`, `empty` | `JobPostingQuestionRepository`, `ApplicationFormRequiredPolicyResponse` | Converts nullable JPQL aggregate values to zero. |
| `com.shinyoung.recruit.enumeration` | `ApplicationFormRequirementType` | Enum | Defines public section requirement type values. | `REQUIRED`, `OPTIONAL`, `DISABLED`, `DEFERRED` | `ApplicationFormSectionPolicyResponse` | Jackson serializes enum names as the existing JSON strings. |
| `com.shinyoung.recruit.dto.response` | `ApplicationFormRequiredPolicyResponse` | Response DTO | Represents the public policy summary for application form sections. | `sections`, `requiredSectionCount`, `optionalSectionCount`, `requiredQuestionCount`, `optionalQuestionCount`, `hasRequiredQuestion`, `attachmentRequired`, `from` | `ApplicationFormConfig`, `JobPostingQuestionPolicyCount`, `ApplicationFormSectionPolicyResponse` | Owns stable section order and current compatibility policy. |
| `com.shinyoung.recruit.dto.response` | `ApplicationFormSectionPolicyResponse` | Response DTO | Represents a single section's public policy. | `sectionCode`, `displayName`, `enabled`, `required`, `requirementType`, `description` | `ApplicationFormRequiredPolicyResponse`, `ApplicationFormRequirementType` | Requirement type is a Java enum and remains a string in JSON. |
| `com.shinyoung.recruit.domain.repository` | `JobPostingRepository` | Repository | Reads public job posting list/detail. | `findPublicList`, `findPublicDetailById` | `JobPostingPublicListProjection`, `ApplicationFormConfig` | Public list now left-joins config flags without fetching collections. |
| `com.shinyoung.recruit.domain.repository` | `JobPostingPublicListProjection` | Repository projection | Provides public list fields. | `getUseEducation`, `getUseCareer`, `getUseCertificate`, `getUseLanguage`, `getUseMilitary`, `getUseAward`, `getUseGapPeriod` | `JobPostingRepository`, `JobPostingPublicListResponse` | Config getters are nullable to tolerate missing legacy config rows. |
| `com.shinyoung.recruit.domain.repository` | `JobPostingQuestionRepository` | Repository | Reads posting questions. | `countActiveQuestionPolicyByJobPostingIds` | `JobPostingQuestion`, `JobPostingQuestionPolicyCount` | Batch query avoids per-posting question count N+1 on public list. |
| `com.shinyoung.recruit.dto.response` | `ApplicationFormConfigPublicResponse` | Response DTO | Returns existing public config flags on detail. | `from` | `ApplicationFormConfig` | Missing config maps to all `false`. |
| `com.shinyoung.recruit.dto.response` | `JobPostingPublicListResponse` | Response DTO | Public list item response. | `applicationFormRequiredPolicy`, `from` | `JobPostingPublicListProjection`, `JobPositionPublicResponse` | Keeps `applicationFormConfig` hidden on list. |
| `com.shinyoung.recruit.dto.response` | `JobPostingPublicDetailResponse` | Response DTO | Public detail response. | `applicationFormConfig`, `applicationFormRequiredPolicy`, `from` | `JobPosting`, `ApplicationFormConfigPublicResponse` | Adds policy without removing existing detail config. |
| `com.shinyoung.recruit.service` | `JobPostingPublicService` | Service | Coordinates public list/detail reads. | `getJobPostings`, `getJobPosting`, `getQuestionPolicyCountByPostingId`, `getQuestionPolicyCount` | `JobPostingRepository`, `JobPostingQuestionRepository`, `JobPositionRepository` | Preserves existing exposure filters and reception status behavior. |
| `com.shinyoung.recruit.service` | `JobPostingPublicServiceTest` | Test | Verifies public service behavior. | `public_list_includes_application_form_required_policy`, `public_detail_and_list_include_active_question_policy_counts` | `JobPostingPublicService`, `JobPostingQuestionService` | Covers active required/optional questions and inactive exclusion. |
| `com.shinyoung.recruit.controller` | `JobPostingPublicControllerTest` | Test | Verifies public JSON contract. | public list/detail policy JSON assertions | `JobPostingPublicController`, `JobPostingService` | Confirms policy appears while internal fields remain hidden. |
| `com.shinyoung.recruit.dto.response` | `ApplicationFormPolicyResponseTest` | Test | Verifies DTO-level guard behavior. | `public_config_response_returns_all_false_when_config_is_null`, `required_policy_response_returns_disabled_policy_when_config_is_null` | `ApplicationFormConfigPublicResponse`, `ApplicationFormRequiredPolicyResponse` | Locks legacy/null config behavior without needing DB setup. |

## API List

| Method | Path | Purpose | Request | Response Impact |
|---|---|---|---|---|
| `GET` | `/job-postings?page={page}&size={size}` | Public job posting list | Query params only | Each list item now includes `applicationFormRequiredPolicy`. |
| `GET` | `/job-postings/{id}` | Public job posting detail | Path variable only | Detail now includes `applicationFormRequiredPolicy` next to existing `applicationFormConfig`. |

## Response Shape

`applicationFormRequiredPolicy`:

| Field | Type | Rule |
|---|---|---|
| `sections` | array | Stable order: `EDUCATION`, `CAREER`, `CERTIFICATE`, `LANGUAGE`, `MILITARY`, `AWARD`, `GAP_PERIOD`, `QUESTION`, `ATTACHMENT`. |
| `requiredSectionCount` | number | Count of sections whose `requirementType` is `REQUIRED`. |
| `optionalSectionCount` | number | Count of sections whose `requirementType` is `OPTIONAL`. |
| `requiredQuestionCount` | number | Count of active posting questions with `required=true`. |
| `optionalQuestionCount` | number | Count of active posting questions with `required=false`. |
| `hasRequiredQuestion` | boolean | `true` when `requiredQuestionCount > 0`. |
| `attachmentRequired` | boolean | Always `false` in this phase. |

`sections[]`:

| Field | Type | Rule |
|---|---|---|
| `sectionCode` | string | Stable section code. |
| `displayName` | string | Human-readable section name. |
| `enabled` | boolean | Existing config/question availability. |
| `required` | boolean | Current effective required policy. |
| `requirementType` | enum/string | Java type is `ApplicationFormRequirementType`; JSON value remains `REQUIRED`, `OPTIONAL`, `DISABLED`, or `DEFERRED`. |
| `description` | string | Short policy explanation. |

## Entity Relationship Summary

- `JobPosting` 1 : 1 `ApplicationFormConfig`
- `JobPosting` 1 : N `JobPosition`
- `JobPosting` 1 : N `JobPostingQuestion`
- This phase reads existing relationships only.
- No new entity relationship was added.
- No schema field was added.

## Validation and Business Rules

1. Public exposure remains unchanged:
   - `status = PUBLISHED`
   - `visible = true`
   - current time is inside the optional display period.
2. Reception status and `accepting` calculation remain unchanged.
3. Existing list sorting remains unchanged.
4. Existing public list does not expose `applicationFormConfig`.
5. Existing public detail keeps exposing `applicationFormConfig`.
6. Policy section order is stable:
   - `EDUCATION`
   - `CAREER`
   - `CERTIFICATE`
   - `LANGUAGE`
   - `MILITARY`
   - `AWARD`
   - `GAP_PERIOD`
   - `QUESTION`
   - `ATTACHMENT`
7. Section policy from existing config:
   - `EDUCATION`: required when `useEducation=true`
   - `CAREER`: required when `useCareer=true`
   - `CERTIFICATE`: optional when `useCertificate=true`
   - `LANGUAGE`: optional when `useLanguage=true`
   - `MILITARY`: required when `useMilitary=true`
   - `AWARD`: optional when `useAward=true`
   - `GAP_PERIOD`: optional when `useGapPeriod=true`
8. `QUESTION` is disabled when there are no active questions.
9. `QUESTION` is required when at least one active question has `required=true`.
10. `QUESTION` is optional when active questions exist but none are required.
11. Inactive questions are ignored.
12. `ATTACHMENT` is `DEFERRED` and not required.
13. Missing legacy `ApplicationFormConfig` rows produce all disabled config-backed sections and zero question counts instead of a public read failure.
14. No question text, helper text, answer type, or answer content is exposed by the policy response.

## Test Coverage

| Test Class | Coverage |
|---|---|
| `JobPostingPublicServiceTest` | Public list policy flags, stable section order, required/optional section counts, attachment deferred state, active question count policy, inactive question exclusion, list/detail policy parity. |
| `JobPostingPublicControllerTest` | JSON response contains `applicationFormRequiredPolicy` on list/detail, includes non-zero required/optional question counts, and still hides internal fields. |
| `ApplicationFormPolicyResponseTest` | DTO null config guard for public config and required policy responses. |

## Test Commands

```powershell
$env:AES_SECRET_KEY='<test-example-key>'; .\gradlew.bat test --tests com.shinyoung.recruit.dto.response.ApplicationFormPolicyResponseTest --tests com.shinyoung.recruit.service.JobPostingPublicServiceTest --tests com.shinyoung.recruit.controller.JobPostingPublicControllerTest --no-daemon
```

```powershell
$env:AES_SECRET_KEY='<test-example-key>'; .\gradlew.bat clean test --no-daemon
```

## Test Results

- Targeted result: success
- Targeted build output: `BUILD SUCCESSFUL`
- Targeted test count: 19 tests, 0 failures, 0 skipped.
- Targeted scope: service and controller tests for public job posting policy responses.
- Review fix scope: null config guard DTO tests, `requirementType` enum conversion, and non-zero question count controller JSON assertions.
- Full `clean test` result: not completed in this run.
- Full `clean test` reason: command timed out after 10 minutes before Gradle produced test result XML. Remaining Java/Gradle processes were stopped with `.\gradlew.bat --stop`.
- Note: an earlier targeted run also timed out before completion because Gradle startup exceeded the command timeout; the same targeted test command passed when rerun with a longer timeout.

## Known Limitations

- The response is a compatibility policy derived from current behavior, not the target `requireXxx` model from the Phase 03k design.
- Submit validation still uses the existing hard-coded required policy.
- Dashboard completion/readiness still uses the existing policy.
- Admin APIs cannot configure optional-vs-required independently yet.
- Attachment requiredness is not implemented.
- Public list still does not expose raw `applicationFormConfig`, by design.

## Remaining Issues

- Decide whether Phase 03k target implementation should still add `requireXxx` fields later.
- Align submit validation and dashboard completion before exposing configurable `requireXxx` values.
- Define attachment-required policy separately.

## Next Phase Recommendation

Proceed with one of these paths:

1. Implement the full Phase 03k target model end-to-end:
   - DB migration and backfill
   - `requireXxx` entity fields
   - admin create/update support
   - submit validator conversion
   - dashboard completion conversion
   - response contract update
2. Or keep the compatibility policy and move to the next applicant workflow phase, documenting that optional-vs-required is not admin-configurable yet.
