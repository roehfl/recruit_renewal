# Phase 03e-3 URL Authorization Hardening

## Phase Summary

Phase 03e-3 tightens URL-level authorization for admin and applicant API families.

The implementation keeps the existing session-based Spring Security architecture and does not add custom JSON 401/403 handlers. Default Spring Security authentication and authorization failures are accepted for this phase.

## Purpose

- Protect `/admin/**` with recruitment admin authorities.
- Protect `/applications/**` with applicant authority.
- Preserve public login, development tooling, and public job posting read endpoints.
- Keep service-level ownership checks for applicant-owned resources.
- Verify admin authority matching against the current `DeptRoleMapping.roleName` shape before choosing `hasAnyAuthority(...)`.

## Scope

Implemented:

- `SecurityConfig` URL authorization rules for `/admin/**`.
- `SecurityConfig` URL authorization rules for `/applications/**`.
- Applicant protection for `GET /job-postings/{jobPostingId}/application`.
- Public read access for `GET /job-postings/**`.
- Existing dev/public allowlist for auth, Swagger/OpenAPI, H2 console, and menu tree.
- Conservative fallback `anyRequest().permitAll()` for unclassified APIs.
- MockMvc security filter setup in targeted controller tests.
- Admin API authorization tests for valid admin, applicant, employee without admin authority, and anonymous users.
- Applicant API authorization tests for applicant, employee/admin, and anonymous users.
- `spring-security-test` test dependency.

## Out of Scope

- JSON `ApiResponse.fail` body for 401/403.
- `AuthenticationEntryPoint`.
- `AccessDeniedHandler`.
- `CurrentAdminService`.
- StageResult actor propagation changes.
- `StageResultService` actor logic changes.
- `StageResultCorrectionService` actor logic changes.
- `CurrentEmployeeService` structure changes.
- `ApplicantStageResultResponse` changes.
- `AdminStageResultResponse.decidedBy`.
- DB schema changes.
- LDAP configuration changes.
- Employee foreign key or audit actor entity.
- Message, notification, export, or download behavior.

## Changed Files

| Path | Type | Notes |
|---|---|---|
| `build.gradle` | Modified | Added `spring-security-test` for MockMvc security tests |
| `src/main/java/com/shinyoung/recruit/config/SecurityConfig.java` | Modified | Added admin/applicant URL authorization rules |
| `src/test/java/com/shinyoung/recruit/controller/StageResultControllerTest.java` | Modified | Enables Spring Security filters and verifies admin API access policy |
| `src/test/java/com/shinyoung/recruit/controller/ApplicationStageResultControllerTest.java` | Modified | Enables Spring Security filters and verifies applicant API access policy |
| `src/test/java/com/shinyoung/recruit/controller/StageControllerTest.java` | Modified | Enables Spring Security filters and verifies admin Stage API access policy |
| `docs/codex/implementation/phase-03e-3-url-authorization-hardening.md` | New | Codex implementation reference |
| `docs/codex/reports/phase-03e-3-url-authorization-hardening.html` | New | Human-readable report |
| `docs/codex/design/phase-03e-admin-auth-hardening-design.md` | Modified | Added Phase 03e-3 implementation note |
| `docs/codex/design/phase-03-application-design.md` | Modified | Added applicant/admin URL authorization note |
| `docs/codex/07-implementation-history.md` | Modified | Added Phase 03e-3 history entry |

## New Classes

No production or test class was added in this phase.

## Modified Classes

| Package | Class | Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.config` | `SecurityConfig` | Configuration | Defines URL authorization policy for admin, applicant, and public endpoints |
| root build | `build.gradle` | Build configuration | Adds `spring-security-test` to support MockMvc security assertions |
| `com.shinyoung.recruit.controller` | `StageResultControllerTest` | Test | Verifies `/admin/stages/**/results/**` access policy with security filters |
| `com.shinyoung.recruit.controller` | `ApplicationStageResultControllerTest` | Test | Verifies `/applications/{applicationId}/stage-results` access policy with security filters |
| `com.shinyoung.recruit.controller` | `StageControllerTest` | Test | Verifies `/admin/job-postings/**/stages/**` access policy with security filters |

## Class-by-Class Explanation

### `SecurityConfig`

- Package: `com.shinyoung.recruit.config`
- Class type: Configuration
- Responsibility: Configure Spring Security request authorization.
- Key changed method:
  - `filterChain(HttpSecurity http)`
- Related classes:
  - `CustomUserDetails`
  - `DeptRoleMapping`
  - `CustomLdapUserDetailsMapper`
- Implementation notes:
  - `DeptRoleMapping.roleName` is already stored as full authority names such as `ROLE_ADMIN`.
  - LDAP employee authorities are passed directly into `SimpleGrantedAuthority`.
  - The implementation therefore uses `hasAnyAuthority("ROLE_ADMIN", "ROLE_RECRUIT_ADMIN")`.
  - `/applications/**` uses `hasAuthority("ROLE_APPLICANT")`.
  - `GET /job-postings/{jobPostingId}/application` is protected as an applicant endpoint before the public `GET /job-postings/**` rule.
  - `anyRequest().permitAll()` remains as a conservative fallback until all non-admin/non-applicant APIs are classified.
  - No custom exception handler is configured.

### `StageResultControllerTest`

- Package: `com.shinyoung.recruit.controller`
- Class type: Test
- Responsibility: Verify admin StageResult controller behavior and admin URL authorization.
- Key methods:
  - MockMvc setup with `springSecurity()`.
  - Admin principal helper using employee `CustomUserDetails`.
  - Applicant principal helper using applicant `CustomUserDetails`.
- Related classes:
  - `StageResultController`
  - `StageResultService`
  - `StageResultCorrectionService`
  - `CurrentEmployeeService`
- Implementation notes:
  - Existing success paths run with a default `ROLE_ADMIN` employee principal.
  - Applicant and non-admin employee principals receive 403 for admin result commands.
  - Anonymous requests assert 4xx instead of a custom JSON body because JSON security handlers are deferred.

### `ApplicationStageResultControllerTest`

- Package: `com.shinyoung.recruit.controller`
- Class type: Test
- Responsibility: Verify applicant StageResult read API and applicant URL authorization.
- Key methods:
  - MockMvc setup with `springSecurity()`.
  - Applicant and employee authentication helpers.
- Related classes:
  - `ApplicationStageResultController`
  - `ApplicationStageResultService`
  - `CurrentApplicantService`
- Implementation notes:
  - Applicant-owned reads still reach the service-level ownership check.
  - Employee/admin principals receive 403 for `/applications/**`.
  - Anonymous requests assert 4xx.
  - Other applicant ownership failure remains a hidden-resource 404 policy.

### `StageControllerTest`

- Package: `com.shinyoung.recruit.controller`
- Class type: Test
- Responsibility: Verify Stage admin controller behavior and admin URL authorization.
- Key methods:
  - MockMvc setup with `springSecurity()`.
  - Default `ROLE_ADMIN` employee principal.
- Related classes:
  - `StageController`
  - `StageService`
- Implementation notes:
  - Existing admin Stage tests continue under a valid admin authority.
  - Applicant and non-admin employee principals receive 403.
  - Anonymous requests assert 4xx.

## API List

| Method | Path | Phase 03e-3 policy | Request | Response |
|---|---|---|---|---|
| `POST` | `/auth/login` | Public | `LoginRequest` | Existing auth response |
| `POST` | `/auth/logout` | Public by current policy | None | Existing auth response |
| `GET` | `/swagger-ui/**`, `/api-docs/**`, `/v3/api-docs/**`, `/h2-console/**`, `/menu/tree` | Public/dev allowlist | None | Existing responses |
| `GET` | `/job-postings/**` | Public read | None | Existing public posting responses |
| `GET` | `/job-postings/{jobPostingId}/application` | `ROLE_APPLICANT` | None | `ApiResponse<ApplicationDetailResponse>` |
| Any | `/admin/**` | `ROLE_ADMIN` or `ROLE_RECRUIT_ADMIN` | Endpoint-specific | Endpoint-specific |
| Any | `/applications/**` | `ROLE_APPLICANT` | Endpoint-specific | Endpoint-specific |
| Any | Other paths | Current fallback `permitAll` | Endpoint-specific | Endpoint-specific |

## Entity, DTO, Service, and Controller Summary

- No entity relationship changed.
- No DTO shape changed.
- No service business logic changed.
- No controller production code changed.
- URL authorization now blocks callers before protected controllers are invoked.
- Applicant resource ownership remains enforced by existing services such as `CurrentApplicantService` and `ApplicationStageResultService`.
- StageResult admin actor propagation from Phase 03e-2 remains unchanged.

## Validation and Business Rules

- `/admin/**` requires an authenticated employee/admin authority.
- `ROLE_ADMIN` and `ROLE_RECRUIT_ADMIN` can access admin endpoints.
- `ROLE_APPLICANT` cannot access admin endpoints.
- Employees without admin/recruit authority cannot access admin endpoints.
- `/applications/**` requires `ROLE_APPLICANT`.
- Employee/admin principals cannot access applicant APIs under `/applications/**`.
- `GET /job-postings/{jobPostingId}/application` is treated as applicant-owned and requires `ROLE_APPLICANT`.
- Public job posting read endpoints remain public.
- Applicant access to another applicant's application still uses the existing 404 hiding policy at service level.
- 401/403 response body shape is not asserted in this phase.

## Authority Decision

`DeptRoleMapping.roleName` is used as a full authority string.

Evidence:

- Existing tests seed values such as `ROLE_ADMIN` and `ROLE_INTERVIEWER`.
- LDAP mapping converts each role name directly into `SimpleGrantedAuthority`.

Decision:

- Use `hasAnyAuthority("ROLE_ADMIN", "ROLE_RECRUIT_ADMIN")` for admin URLs.
- Use `hasAuthority("ROLE_APPLICANT")` for applicant URLs.
- Do not use `hasAnyRole(...)` unless role storage is later changed to names without the `ROLE_` prefix.

## Test Commands

```powershell
$env:AES_SECRET_KEY='<example AES key>'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.StageResultControllerTest
$env:AES_SECRET_KEY='<example AES key>'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.ApplicationStageResultControllerTest
$env:AES_SECRET_KEY='<example AES key>'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.StageControllerTest
$env:AES_SECRET_KEY='<example AES key>'; .\gradlew.bat test --tests com.shinyoung.recruit.service.StageResultServiceTest --tests com.shinyoung.recruit.service.StageResultCorrectionServiceTest
$env:AES_SECRET_KEY='<example AES key>'; .\gradlew.bat clean test --no-daemon
```

## Test Results

| Command | Result | Notes |
|---|---|---|
| `StageResultControllerTest` | Success | Admin/applicant/non-admin/anonymous policy covered |
| `ApplicationStageResultControllerTest` | Success | Applicant/employee-admin/anonymous policy covered |
| `StageControllerTest` | Success | Admin/applicant/non-admin/anonymous policy covered |
| `StageResultServiceTest` + `StageResultCorrectionServiceTest` | Success | Actor propagation regression unchanged |
| `clean test --no-daemon` | Success | Full suite passed |

## Remaining Issues

- 401/403 responses still use Spring Security defaults.
- `ApiResponse.fail` security error body is deferred to Phase 03e-4.
- Fallback remains `anyRequest().permitAll()` until remaining public/authenticated paths are classified.
- Admin policy currently allows `ROLE_ADMIN` and `ROLE_RECRUIT_ADMIN`; additional roles such as interviewer access should be designed separately per API family.
- Swagger/OpenAPI and H2 console remain public in the current configuration and should be environment-gated before production.

## Next Phase Recommendation

Recommended next phase: Phase 03e-4 security exception response hardening.

Suggested scope:

- Add an `AuthenticationEntryPoint` for 401 JSON responses.
- Add an `AccessDeniedHandler` for 403 JSON responses.
- Return `ApiResponse.fail(...)` with JSON content type.
- Keep business exceptions in `GlobalExceptionHandler`.
- Add response-body tests for unauthenticated and unauthorized requests.
