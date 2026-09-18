# Phase 03k-2 - Application Form Required Policy

## Phase Summary

- Date: 2026-05-22
- Purpose: implement the Phase 03k target model by separating application form section visibility (`useXxx`) from final-submit required policy (`requireXxx`).
- Result: `ApplicationFormConfig` now stores explicit required flags, admin/public responses expose them where appropriate, public required policy is derived from `use && require`, and submit/dashboard readiness use the same blocking rules.

## Scope

- Add explicit `requireXxx` fields to `ApplicationFormConfig`.
- Preserve request backward compatibility by keeping a 7-argument `ApplicationFormConfigRequest` constructor and using nullable `Boolean requireXxx` fields in the canonical request.
- Apply create/update defaults and invariants:
  - create omitted defaults: education, career, military default to matching `useXxx`; certificate, language, award, gap period default to `false`.
  - update omitted values preserve existing required flags while the section remains enabled.
  - update omitted values reset to `false` when the section is disabled.
  - `requireXxx=true` with `useXxx=false` is rejected.
- Extend admin/public detail config responses with `requireXxx`.
- Keep public list `applicationFormConfig` hidden.
- Extend public list projection so `applicationFormRequiredPolicy` can be derived without loading full entities.
- Convert final submit validator and dashboard completion checker to the explicit required policy.
- Add regression coverage for defaults, invalid combinations, update preservation/reset, public/admin response fields, submit validator, dashboard readiness, and section access.

## Out Of Scope

- Attachment required enforcement remains deferred.
- No Flyway/Liquibase or explicit migration file was added because the project has no active migration convention.
- No security, LDAP, authentication, frontend, static resource, SMS/email, or batch behavior was changed.

## Changed Files

| File | Change |
| --- | --- |
| `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationFormConfig.java` | Added seven `requireXxx` columns, invariant validation, create/update overloads. |
| `src/main/java/com/shinyoung/recruit/domain/entity/JobPosting.java` | Copies required flags when updating embedded application form config. |
| `src/main/java/com/shinyoung/recruit/dto/request/ApplicationFormConfigRequest.java` | Added nullable `Boolean requireXxx` request fields and retained 7-argument compatibility constructor. |
| `src/main/java/com/shinyoung/recruit/dto/response/ApplicationFormConfigResponse.java` | Added admin detail `requireXxx` response fields. |
| `src/main/java/com/shinyoung/recruit/dto/response/ApplicationFormConfigPublicResponse.java` | Added public detail `requireXxx` response fields and null-config fallback. |
| `src/main/java/com/shinyoung/recruit/dto/response/ApplicationFormRequiredPolicyResponse.java` | Derives required policy from `useXxx && requireXxx`. |
| `src/main/java/com/shinyoung/recruit/dto/response/JobPostingPublicListResponse.java` | Passes projected use/require fields into required policy response. |
| `src/main/java/com/shinyoung/recruit/domain/repository/JobPostingPublicListProjection.java` | Added nullable `getRequireXxx()` projection getters. |
| `src/main/java/com/shinyoung/recruit/domain/repository/JobPostingRepository.java` | Public list query now projects `requireXxx` fields. |
| `src/main/java/com/shinyoung/recruit/service/JobPostingService.java` | Resolves create/update required defaults, preserves omitted values, rejects invalid combinations. |
| `src/main/java/com/shinyoung/recruit/service/ApplicationSubmitValidator.java` | Uses `requireXxx` for submit-blocking section validation and adds required certificate/language/award/gap checks. |
| `src/main/java/com/shinyoung/recruit/service/ApplicationCompletionReadChecker.java` | Separates required and optional readiness groups by `useXxx` plus `requireXxx`. |
| `src/test/java/com/shinyoung/recruit/service/JobPostingServiceTest.java` | Added required flag defaults/update/invariant coverage. |
| `src/test/java/com/shinyoung/recruit/service/JobPostingPublicServiceTest.java` | Added explicit policy detail/list parity and public config required field assertions. |
| `src/test/java/com/shinyoung/recruit/controller/JobPostingControllerTest.java` | Added admin detail required field JSON assertions. |
| `src/test/java/com/shinyoung/recruit/controller/JobPostingPublicControllerTest.java` | Added public detail required field JSON assertions. |
| `src/test/java/com/shinyoung/recruit/service/ApplicationSubmitValidatorTest.java` | Added optional-vs-required submit validation coverage. |
| `src/test/java/com/shinyoung/recruit/service/ApplicationDashboardServiceTest.java` | Added optional core sections, required optional-domain dashboard coverage, and optional-message regression coverage. |
| `src/test/java/com/shinyoung/recruit/service/ApplicationSectionAccessServiceTest.java` | Added regression that section access checks `useXxx`, not `requireXxx`. |
| `src/test/java/com/shinyoung/recruit/dto/response/ApplicationFormPolicyResponseTest.java` | Added null-config required fields and explicit policy derivation coverage. |
| `docs/codex/07-implementation-history.md` | Added Phase 03k-2 history entry. |
| `docs/codex/reports/phase-03k-2-application-form-required-policy.html` | Human-readable report for this phase. |

## New Classes

| Package | Class | Type | Responsibility |
| --- | --- | --- | --- |
| `com.shinyoung.recruit.service` | `ApplicationSectionAccessServiceTest` | Test | Verifies section write/read access remains controlled by `useXxx` and does not depend on `requireXxx`. |

## Modified Classes

| Package | Class | Type | Responsibility | Key fields/methods | Related classes | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| `com.shinyoung.recruit.domain.entity` | `ApplicationFormConfig` | Entity | Stores application form section usage and required policy for a job posting. | `requireEducation`, `requireCareer`, `requireCertificate`, `requireLanguage`, `requireMilitary`, `requireAward`, `requireGapPeriod`, `create(...)`, `update(...)` | `JobPosting`, `JobPostingService` | Entity-level invariant prevents a disabled section from being required. |
| `com.shinyoung.recruit.domain.entity` | `JobPosting` | Entity | Owns application form config as a one-to-one child. | `updateApplicationFormConfig(...)` | `ApplicationFormConfig` | Existing child row is updated in place, including required flags. |
| `com.shinyoung.recruit.dto.request` | `ApplicationFormConfigRequest` | Request DTO | Receives admin section usage and required policy. | nullable `Boolean requireXxx`, compatibility constructor | `JobPostingCreateRequest`, `JobPostingUpdateRequest` | Missing required fields are interpreted by `JobPostingService`, not by JSON binding. |
| `com.shinyoung.recruit.dto.response` | `ApplicationFormConfigResponse` | Response DTO | Admin detail application form config response. | `requireXxx` accessors | `JobPostingDetailResponse` | Admin detail now shows both use and require values. |
| `com.shinyoung.recruit.dto.response` | `ApplicationFormConfigPublicResponse` | Response DTO | Public detail application form config response. | `requireXxx` accessors, `from(null)` | `JobPostingPublicDetailResponse` | Public list still does not include this config object. |
| `com.shinyoung.recruit.dto.response` | `ApplicationFormRequiredPolicyResponse` | Response DTO | Public normalized required/optional policy. | `from(config, counts)`, `from(projected values, counts)` | `ApplicationFormSectionPolicyResponse`, `JobPostingQuestionPolicyCount` | A section is required only when enabled and required. |
| `com.shinyoung.recruit.domain.repository` | `JobPostingPublicListProjection` | Repository Projection | Public list lightweight projection. | `getRequireXxx()` | `JobPostingRepository` | Keeps list response from exposing raw config while supporting policy calculation. |
| `com.shinyoung.recruit.domain.repository` | `JobPostingRepository` | Repository | Fetches admin/public job postings. | `findPublicList(...)` | `JobPostingPublicListProjection` | Public list JPQL now selects require flags. |
| `com.shinyoung.recruit.service` | `JobPostingService` | Service | Creates/updates postings and config policy. | `toCreateApplicationFormConfig`, `toUpdateApplicationFormConfig`, `validateApplicationFormRequirement` | `ApplicationFormConfigRequest`, `InvalidJobPostingException` | Explicit invalid requests fail before persistence. |
| `com.shinyoung.recruit.service` | `ApplicationSubmitValidator` | Service | Checks final-submit blockers. | `validateEducation`, `validateCareer`, `validateMilitary`, `validateSimpleRequiredSection` | section repositories | Optional enabled sections no longer block submit. Required certificate/language/award/gap now block when empty. |
| `com.shinyoung.recruit.service` | `ApplicationCompletionReadChecker` | Service | Builds dashboard completion/readiness summary. | `addGroup`, `addIssue`, `checkSimpleSection`, `sectionMissingMessage` | `ApplicationDashboardService` | Required/optional grouping now matches submit validator; optional issue messages are non-blocking wording and do not say they are required before submit. |

## API List

| Method | Path | Purpose | Request Change | Response Change |
| --- | --- | --- | --- | --- |
| `POST` | `/admin/job-postings` | Create job posting | `applicationFormConfig` accepts nullable `requireXxx`. | Created id unchanged. |
| `POST` | `/admin/job-postings/{id}` | Update job posting | `applicationFormConfig` accepts nullable `requireXxx`; omitted values follow update rules. | Updated id unchanged. |
| `GET` | `/admin/job-postings/{id}` | Admin detail | None | `applicationFormConfig` includes `requireXxx`. |
| `GET` | `/job-postings` | Public list | None | Still hides `applicationFormConfig`; `applicationFormRequiredPolicy` uses explicit required flags. |
| `GET` | `/job-postings/{id}` | Public detail | None | `applicationFormConfig` includes `requireXxx`; `applicationFormRequiredPolicy` uses explicit required flags. |
| submit command callers | existing submit flow | Final submit validation | None | Optional enabled sections do not block; required optional-domain sections can block. |
| dashboard read callers | existing dashboard flow | Completion/readiness summary | None | Required/optional counts follow `useXxx && requireXxx`. |

## Entity Relationship Summary

- `JobPosting` owns one `ApplicationFormConfig`.
- `ApplicationFormConfig` remains a one-to-one child table keyed by `job_posting_id`.
- `useXxx` controls section visibility/access.
- `requireXxx` controls submit blocking and dashboard required-readiness.
- No new relation was added from application detail entities back to `ApplicationFormConfig`.

## Validation And Business Rules

- `requireXxx=true` is valid only when the matching `useXxx=true`.
- Create defaults:
  - `requireEducation = useEducation` when omitted.
  - `requireCareer = useCareer` when omitted.
  - `requireMilitary = useMilitary` when omitted.
  - `requireCertificate`, `requireLanguage`, `requireAward`, `requireGapPeriod` default to `false` when omitted.
- Update defaults:
  - explicit `requireXxx` is applied first and validated.
  - omitted `requireXxx` preserves the existing value if the section remains enabled.
  - omitted `requireXxx` becomes `false` if the section is disabled in the update request.
- Public policy:
  - `enabled = useXxx`.
  - `required = useXxx && requireXxx`.
  - question required policy is unchanged.
  - attachment remains deferred.
- Submit validator:
  - education, career, military block only when their matching required flag is true.
  - certificate, language, award, and gap period now block when enabled and required but no row exists.
- Dashboard:
  - required section count includes only enabled required sections plus required question group when applicable.
  - optional section count includes enabled non-required sections.
  - `submittable` remains aligned with submit validator blocking issues.
  - optional incomplete section messages use non-blocking wording so applicant-facing screens do not present optional gaps as submit blockers.
- Section access:
  - applicant section access remains based on `useXxx`; `requireXxx` does not disable access.

## Manual MariaDB DDL

No migration framework is active in this repository. H2 tests rely on generated schema. Before applying this change to a persistent MariaDB database, the manual DDL and backfill below are required. Deploying the Java change without these columns will break runtime reads/writes, and existing rows must be backfilled to preserve the previous education/career/military submit-required behavior:

```sql
ALTER TABLE application_form_config
    ADD COLUMN require_education BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN require_career BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN require_certificate BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN require_language BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN require_military BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN require_award BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN require_gap_period BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE application_form_config
SET require_education = use_education,
    require_career = use_career,
    require_military = use_military;
```

## Test Commands

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.JobPostingServiceTest --tests com.shinyoung.recruit.service.ApplicationSubmitValidatorTest --tests com.shinyoung.recruit.service.ApplicationDashboardServiceTest --tests com.shinyoung.recruit.service.ApplicationSectionAccessServiceTest --tests com.shinyoung.recruit.dto.response.ApplicationFormPolicyResponseTest --no-daemon
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.JobPostingPublicServiceTest --tests com.shinyoung.recruit.controller.JobPostingPublicControllerTest --tests com.shinyoung.recruit.controller.JobPostingControllerTest --no-daemon
```

## Test Results

- Targeted service/DTO test group: `BUILD SUCCESSFUL`
- Targeted public/admin controller/service group: `BUILD SUCCESSFUL`
- Review fix targeted dashboard test group: `BUILD SUCCESSFUL`
- Initial non-escalated Gradle run failed because the wrapper attempted to download Gradle and network access was sandbox-blocked.
- One intermediate run failed due to a locked `build/test-results/test/binary/output.bin`; `.\gradlew.bat --stop` released the stale daemon lock.
- Full check attempted after review fix: `AES_SECRET_KEY=<test-example-key> .\gradlew.bat clean test --no-daemon`
- Full check result after review fix: `BUILD SUCCESSFUL` in 15m 4s.

## Remaining Issues

- Persistent DB migration is manual for this phase.
- Existing rows in a persistent database must be backfilled before deployment to preserve previous education/career/military required behavior.
- Attachment required policy remains deferred and is still reported as not enforced.

## Next Phase Recommendation

- If attachments become submit-blocking, extend the same `use + require` policy model to attachment metadata and dashboard readiness.
