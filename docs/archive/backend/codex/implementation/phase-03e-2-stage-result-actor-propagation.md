# Phase 03e-2 StageResult Actor Propagation

## Phase Summary

Phase 03e-2 replaces the temporary StageResult admin actor value with the authenticated employee login id.

The implementation keeps the current development-time `SecurityConfig` shape unchanged. URL authorization, 401/403 JSON handlers, Employee foreign keys, and applicant API changes are deferred.

## Purpose

- Remove `"SYSTEM"` from StageResult admin command services.
- Resolve the command actor from `CustomUserDetails`.
- Store the employee login id in `StageResult.decidedBy`.
- Store the employee login id in `StageResultCorrectionHistory.correctedBy`.
- Keep applicant-facing StageResult responses free of actor fields.

## Scope

Implemented:

- `CurrentEmployeeService`
- Actor extraction in `StageResultController` for update, bulk update, and correction commands
- Actor parameter propagation into `StageResultService`
- Actor parameter propagation into `StageResultCorrectionService`
- Service-level null/blank actor validation
- Employee/applicant/missing principal controller tests for StageResult commands
- Applicant result-read regression checks for actor field exclusion

## Out of Scope

- `SecurityConfig` changes
- URL authorization for `/admin/**` or `/applications/**`
- 401/403 JSON `ApiResponse.fail` security handlers
- `CurrentAdminService`
- Employee FK or `AuditActor` entity
- DB schema changes
- LDAP configuration changes
- `AdminStageResultResponse.decidedBy`
- `ApplicantStageResultResponse` changes
- Applicant API behavior changes
- Message or notification behavior
- PUT/PATCH/DELETE API additions

## Changed Files

| Path | Type | Notes |
|---|---|---|
| `src/main/java/com/shinyoung/recruit/service/CurrentEmployeeService.java` | New | Resolves authenticated employee actor string |
| `src/main/java/com/shinyoung/recruit/service/StageResultService.java` | Modified | Adds actor parameters and stores actor in `decidedBy` |
| `src/main/java/com/shinyoung/recruit/service/StageResultCorrectionService.java` | Modified | Adds actor parameter and stores actor in latest result/history |
| `src/main/java/com/shinyoung/recruit/controller/StageResultController.java` | Modified | Reads `@AuthenticationPrincipal` for command endpoints |
| `src/test/java/com/shinyoung/recruit/service/CurrentEmployeeServiceTest.java` | New | Resolver unit tests |
| `src/test/java/com/shinyoung/recruit/service/StageResultServiceTest.java` | Modified | Actor persistence and validation coverage |
| `src/test/java/com/shinyoung/recruit/service/StageResultCorrectionServiceTest.java` | Modified | Correction actor persistence and validation coverage |
| `src/test/java/com/shinyoung/recruit/controller/StageResultControllerTest.java` | Modified | Principal-based command tests |
| `src/test/java/com/shinyoung/recruit/service/ApplicationStageResultServiceTest.java` | Modified | Applicant response actor-field regression |
| `src/test/java/com/shinyoung/recruit/controller/ApplicationStageResultControllerTest.java` | Modified | Applicant response actor-field regression |
| `src/test/java/com/shinyoung/recruit/service/StageServiceTest.java` | Modified | Updated helper calls to pass actor |
| `src/test/java/com/shinyoung/recruit/controller/StageControllerTest.java` | Modified | Updated helper calls to pass actor |
| `docs/codex/implementation/phase-03e-2-stage-result-actor-propagation.md` | New | Codex implementation reference |
| `docs/codex/reports/phase-03e-2-stage-result-actor-propagation.html` | New | Human-readable report |
| `docs/codex/design/phase-03e-admin-auth-hardening-design.md` | Modified | Phase 03e-2 implementation note |
| `docs/codex/design/phase-03d-stage-result-design.md` | Modified | Actor propagation implementation note |
| `docs/codex/design/phase-03-application-design.md` | Modified | Applicant/admin identity note |
| `docs/codex/07-implementation-history.md` | Modified | Phase history entry |

## New Classes

| Package | Class | Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.service` | `CurrentEmployeeService` | Service | Validate `CustomUserDetails` is an employee principal and return `getUsername()` as the actor |
| `com.shinyoung.recruit.service` | `CurrentEmployeeServiceTest` | Test | Verifies employee success, applicant failure, null failure, and blank username failure |

## Modified Classes

| Package | Class | Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.service` | `StageResultService` | Service | Requires actor for single and bulk result update commands |
| `com.shinyoung.recruit.service` | `StageResultCorrectionService` | Service | Requires actor for post-announcement correction command |
| `com.shinyoung.recruit.controller` | `StageResultController` | Controller | Resolves current employee actor before command service calls |
| `com.shinyoung.recruit.service` | `StageResultServiceTest` | Test | Verifies `decidedBy` uses actor and blank actor is rejected |
| `com.shinyoung.recruit.service` | `StageResultCorrectionServiceTest` | Test | Verifies latest `decidedBy` and history `correctedBy` use actor |
| `com.shinyoung.recruit.controller` | `StageResultControllerTest` | Test | Verifies employee principal success and applicant/missing principal failure |
| `com.shinyoung.recruit.service` | `ApplicationStageResultServiceTest` | Test | Verifies applicant response record has no actor/history fields |
| `com.shinyoung.recruit.controller` | `ApplicationStageResultControllerTest` | Test | Verifies applicant JSON response has no actor fields |

## Class-by-Class Explanation

### `CurrentEmployeeService`

- Package: `com.shinyoung.recruit.service`
- Class type: Service
- Responsibility: Convert an authenticated employee `CustomUserDetails` into a StageResult audit actor string.
- Key methods:
  - `getCurrentEmployeeActor(CustomUserDetails userDetails)`
- Related classes:
  - `CustomUserDetails`
  - `InvalidStageResultException`
- Implementation notes:
  - Rejects null principal.
  - Requires `CustomUserDetails.USER_TYPE_EMPLOYEE`.
  - Returns `CustomUserDetails.getUsername()`.
  - Rejects null/blank username.
  - Does not query the database.

### `StageResultService`

- Package: `com.shinyoung.recruit.service`
- Class type: Service
- Responsibility: Manage StageResult initialize/list/update commands.
- Key changed methods:
  - `updateResult(Long stageId, Long resultId, StageResultUpdateRequest request, String actor)`
  - `bulkUpdateResults(Long stageId, StageResultBulkUpdateRequest request, String actor)`
- Related classes:
  - `StageResult`
  - `StageResultUpdateRequest`
  - `StageResultBulkUpdateRequest`
  - `AdminStageResultResponse`
  - `StageResultBulkUpdateResponse`
- Implementation notes:
  - `actor` is validated before mutating results.
  - `decidedBy` is now the supplied actor.
  - Stage edit policy remains `Stage.status == IN_PROGRESS`.
  - Initialize/list do not require actor.

### `StageResultCorrectionService`

- Package: `com.shinyoung.recruit.service`
- Class type: Service
- Responsibility: Manage post-announcement StageResult correction and history.
- Key changed methods:
  - `correctResult(Long stageId, Long resultId, StageResultCorrectionRequest request, String actor)`
- Related classes:
  - `StageResult`
  - `StageResultCorrectionHistory`
  - `StageResultCorrectionHistoryRepository`
  - `StageResultCorrectionRequest`
- Implementation notes:
  - `actor` is validated before correction.
  - Latest `StageResult.decidedBy` is set to actor.
  - `StageResultCorrectionHistory.correctedBy` is set to actor.
  - Correction remains limited to `RESULT_ANNOUNCED` and `CLOSED`.
  - History read does not require actor.

### `StageResultController`

- Package: `com.shinyoung.recruit.controller`
- Class type: Controller
- Responsibility: Expose admin StageResult endpoints.
- Key changed methods:
  - `updateResult(...)`
  - `bulkUpdateResults(...)`
  - `correctResult(...)`
- Related classes:
  - `CurrentEmployeeService`
  - `CustomUserDetails`
  - `StageResultService`
  - `StageResultCorrectionService`
- Implementation notes:
  - Command endpoints accept `@AuthenticationPrincipal CustomUserDetails`.
  - Actor resolution stays in controller boundary.
  - Service layer remains independent of `SecurityContextHolder`.
  - Initialize/list/history endpoints do not resolve actor.

## API List

| Method | Path | Phase 03e-2 change | Request | Response |
|---|---|---|---|---|
| `GET` | `/admin/stages/{stageId}/results` | No actor required | None | `ApiResponse<List<AdminStageResultResponse>>` |
| `POST` | `/admin/stages/{stageId}/results/initialize` | No actor required | None | `ApiResponse<StageResultInitializeResponse>` |
| `POST` | `/admin/stages/{stageId}/results/{resultId}` | Employee principal required by controller resolver | `StageResultUpdateRequest` | `ApiResponse<AdminStageResultResponse>` |
| `POST` | `/admin/stages/{stageId}/results/bulk` | Employee principal required by controller resolver | `StageResultBulkUpdateRequest` | `ApiResponse<StageResultBulkUpdateResponse>` |
| `POST` | `/admin/stages/{stageId}/results/{resultId}/correct` | Employee principal required by controller resolver | `StageResultCorrectionRequest` | `ApiResponse<AdminStageResultResponse>` |
| `GET` | `/admin/stages/{stageId}/results/{resultId}/histories` | No actor required | None | `ApiResponse<List<StageResultCorrectionHistoryResponse>>` |
| `GET` | `/applications/{applicationId}/stage-results` | No behavior change | None | `ApiResponse<List<ApplicantStageResultResponse>>` |

## Entity Relationship Summary

No entity relationship changed in this phase.

- `StageResult` still references `Stage` and `JobApplication`.
- `StageResultCorrectionHistory` still references `StageResult`.
- `decidedBy` and `correctedBy` remain string fields.
- No `Employee` relation, audit actor table, cascade rule, or schema change was added.

## Validation and Business Rules

- StageResult update and bulk update require non-null, non-blank actor.
- StageResult correction requires non-null, non-blank actor.
- Controller command endpoints reject null principals and applicant principals via `CurrentEmployeeService`.
- Actor source is `CustomUserDetails.getUsername()`.
- `CurrentEmployeeService` requires `userType == Employee`.
- General result update remains allowed only while the Stage is `IN_PROGRESS`.
- Correction remains allowed only when the Stage is `RESULT_ANNOUNCED` or `CLOSED`.
- Applicant result read continues to expose only `stageName`, `stageType`, `stageOrder`, `resultStatus`, `resultAnnouncementDateTime`, and `decidedAt`.
- Applicant result read does not expose `stageResultId`, `score`, `comment`, `decidedBy`, `correctedBy`, or correction history.

## Test Commands

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.CurrentEmployeeServiceTest
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.StageResultServiceTest --tests com.shinyoung.recruit.service.StageResultCorrectionServiceTest
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.StageResultControllerTest
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationStageResultServiceTest --tests com.shinyoung.recruit.controller.ApplicationStageResultControllerTest
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test --no-daemon
```

## Test Results

| Command | Result | Notes |
|---|---|---|
| `CurrentEmployeeServiceTest` | Success | Required Gradle wrapper network approval on first run |
| `StageResultServiceTest` + `StageResultCorrectionServiceTest` | Success | Actor persistence and state policies verified |
| `StageResultControllerTest` | Success | Employee principal success and non-employee/missing principal failure verified |
| `ApplicationStageResultServiceTest` + `ApplicationStageResultControllerTest` | Success | Applicant actor-field non-exposure verified |
| `clean test --no-daemon` | Success | First full run timed out and left a Gradle process locking `build/reports`; after `.\gradlew.bat --stop`, the full run passed |

## Remaining Issues

- `SecurityConfig` still permits all requests.
- Command endpoints currently fail non-employee/missing principals through `InvalidStageResultException` and therefore business-style 400 responses.
- URL-level 401/403 behavior is deferred to Phase 03e-3 and 03e-4.
- `AdminStageResultResponse` still does not expose `decidedBy`.
- Actor remains a login-id string, not an `Employee` relation.
- Authority names for admin/recruit roles still need confirmation from `DeptRoleMapping`.

## Next Phase Recommendation

Recommended next phase: Phase 03e-3 URL authorization hardening.

Suggested scope:

- Protect `/admin/**` for employee/admin authorities.
- Protect `/applications/**` for applicant authorities.
- Keep service-level ownership checks.
- Update MockMvc tests to use authenticated principals consistently.
- Leave JSON 401/403 response handler polish to Phase 03e-4 if the change becomes large.
