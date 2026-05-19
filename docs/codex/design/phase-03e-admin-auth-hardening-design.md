# Phase 03e-1 Admin/Auth Hardening Design

## Phase Name

Phase 03e-1 - Admin/Auth Hardening Design

## Phase 03e-2 Implementation Note

Phase 03e-2 implemented the StageResult actor propagation portion of this design.

Implemented:

- Added `CurrentEmployeeService`.
- `CurrentEmployeeService` validates non-null `CustomUserDetails`.
- `CurrentEmployeeService` requires `userType == Employee`.
- Actor string is `CustomUserDetails.getUsername()`.
- Null or blank actor values are rejected.
- No employee DB lookup is performed.
- `CurrentAdminService` was not added.
- `StageResultService.updateResult(...)` now accepts `String actor`.
- `StageResultService.bulkUpdateResults(...)` now accepts `String actor`.
- `StageResultCorrectionService.correctResult(...)` now accepts `String actor`.
- Single and bulk update store `decidedBy = actor`.
- Correction stores latest `StageResult.decidedBy = actor`.
- Correction history stores `StageResultCorrectionHistory.correctedBy = actor`.
- `StageResultController` receives `@AuthenticationPrincipal CustomUserDetails` for update, bulk update, and correction endpoints.
- Initialize, list, and history endpoints do not resolve or require actor.
- Applicant result read remains unchanged and does not expose actor fields.

Still deferred:

- `SecurityConfig` URL authorization.
- 401/403 JSON `ApiResponse.fail` handling.
- `CurrentAdminService`.
- `AdminStageResultResponse.decidedBy`.
- Employee FK or audit actor entity.
- LDAP configuration changes.

Verification:

- `CurrentEmployeeServiceTest`: success.
- `StageResultServiceTest` + `StageResultCorrectionServiceTest`: success.
- `StageResultControllerTest`: success.
- `ApplicationStageResultServiceTest` + `ApplicationStageResultControllerTest`: success.
- `.\gradlew.bat clean test --no-daemon`: success.

## Phase 03e-3 Implementation Note

Phase 03e-3 implemented the URL authorization portion of this design.

Implemented:

- Verified that `DeptRoleMapping.roleName` stores full authority names such as `ROLE_ADMIN`.
- Confirmed LDAP mapping passes `DeptRoleMapping.roleName` directly into `SimpleGrantedAuthority`.
- Used `hasAnyAuthority("ROLE_ADMIN", "ROLE_RECRUIT_ADMIN")` for `/admin/**`.
- Used `hasAuthority("ROLE_APPLICANT")` for `/applications/**`.
- Protected `GET /job-postings/{jobPostingId}/application` with `ROLE_APPLICANT` before the public job posting read rule.
- Preserved public access for `/auth/login`, `/auth/logout`, Swagger/OpenAPI, H2 console, `/menu/tree`, and public `GET /job-postings/**`.
- Kept `anyRequest().permitAll()` as a conservative fallback until remaining APIs are classified.
- Added `spring-security-test` for MockMvc security verification.
- Updated `StageResultControllerTest`, `ApplicationStageResultControllerTest`, and `StageControllerTest` to run through Spring Security filters.

Still deferred:

- 401/403 JSON `ApiResponse.fail` handling.
- `AuthenticationEntryPoint`.
- `AccessDeniedHandler`.
- `CurrentAdminService`.
- `AdminStageResultResponse.decidedBy`.
- Employee FK or audit actor entity.
- LDAP configuration changes.
- Replacing fallback `permitAll` with `authenticated`.

Verification:

- `StageResultControllerTest`: success.
- `ApplicationStageResultControllerTest`: success.
- `StageControllerTest`: success.
- `StageResultServiceTest` + `StageResultCorrectionServiceTest`: success.
- `.\gradlew.bat clean test --no-daemon`: success.

## Purpose

Phase 03e-1 is a design-only phase for hardening authentication and authorization before changing `SecurityConfig` or StageResult actor handling.

The current backend already has Spring Security session login, applicant DB authentication, employee LDAP authentication, and `CustomUserDetails`, but development-time access control is still too permissive for production use.

This phase defines how to protect admin APIs, applicant APIs, and StageResult actor propagation in later implementation phases.

## Scope

- Review the current security and current-user structure.
- Define `/admin/**` protection policy.
- Define applicant API protection policy.
- Define current employee/admin identity resolver candidates.
- Define StageResult `decidedBy` and correction `correctedBy` propagation policy.
- Define 401/403 `ApiResponse.fail` behavior.
- Split implementation into follow-up phases.
- Produce a paired self-contained HTML report.

## Out of Scope

- Java source changes.
- Test source changes.
- `SecurityConfig` changes.
- `AuthenticationConfig` changes.
- `StageResultService` changes.
- `StageResultCorrectionService` changes.
- Controller changes.
- Actual `decidedBy` or `correctedBy` implementation.
- DB schema changes.
- Build or application YAML changes.
- Message/notification implementation.
- LDAP integration changes.

## Changed Files

| Path | Type | Notes |
|---|---|---|
| `docs/codex/design/phase-03e-admin-auth-hardening-design.md` | New | Source design document |
| `docs/codex/reports/phase-03e-admin-auth-hardening-design.html` | New | Human-readable design report |
| `docs/codex/design/phase-03-application-design.md` | Modified | Added Phase 03e-1 design note |
| `docs/codex/design/phase-03d-stage-result-design.md` | Modified | Added StageResult identity hardening note |
| `docs/codex/07-implementation-history.md` | Modified | Added design history entry |

No Java, test, build, YAML, or schema file is changed by this phase.

## Current Security State

### `SecurityConfig`

Current behavior:

- CSRF is disabled.
- HTTP Basic is disabled.
- Session creation policy is `IF_REQUIRED`.
- `AuthenticationManager` is wired.
- All requests are currently permitted through `anyRequest().permitAll()`.
- Commented code shows a previous intent to make `/auth/**`, Swagger, H2, and `/menu/tree` public and all other requests authenticated.

Main issue:

- `/admin/**` and applicant APIs are not protected by Spring Security URL authorization.
- Controller/service tests often rely on development-time `permitAll`.
- Unauthenticated users can reach controller methods unless the controller explicitly uses a principal and service-level guard.

### `CustomUserDetails`

Current fields:

- `loginId`
- `deptName`
- `name`
- `userType`
- `password`
- `authorities`

Current user types:

- `Applicant`
- `Employee`

Current authority sources:

- Applicant DB login receives `ROLE_APPLICANT`.
- LDAP employee login receives role names from `DeptRoleMapping`.
- Employee JIT login stores an `Employee` and reuses LDAP-derived authorities.

Main issue:

- `CustomUserDetails` does not expose a stable user id.
- Applicant resolver must query `ApplicantRepository` by loginId.
- There is no equivalent current employee/admin resolver yet.

### `CurrentApplicantService`

Current behavior:

- Requires non-null `CustomUserDetails`.
- Requires `userType == Applicant`.
- Resolves `applicantId` by `loginId`.
- Throws `InvalidJobApplicationException` for missing or non-applicant users.

Main issue:

- This is good for service-level ownership checks but not a replacement for URL-level authentication/authorization.
- Authentication failure and authorization failure currently map to business exceptions in some flows instead of consistent 401/403 security responses.

### StageResult Actor Fields

Current behavior:

- `StageResultService.updateResult` stores `decidedBy = "SYSTEM"`.
- `StageResultCorrectionService.correctResult` stores `correctedBy = "SYSTEM"` and updates latest result with `decidedBy = "SYSTEM"`.

Main issue:

- Admin actions cannot be traced to the authenticated employee/admin.
- Correction history has the right field shape but uses a placeholder actor.

## Recommended Policy

## Admin API Protection

Recommended target:

| Path | Authentication | Authorization | Notes |
|---|---|---|---|
| `/admin/**` | Required | Employee/admin authority required | Applicant users must be denied |
| `/auth/**` | Public for login/logout shape; authenticated for `me` if needed | Endpoint-specific | Keep current auth flow |
| `/swagger-ui/**`, `/api-docs/**`, `/h2-console/**` | Environment-specific | Development only unless disabled | Do not expose in production |

Recommended authority baseline:

- Treat employee LDAP users as the only admin-side principal type.
- Allow `/admin/**` when `CustomUserDetails.userType == Employee` and the user has at least one configured admin/recruitment authority.
- Prefer authority checks over department-name string checks inside controllers.

Candidate authority names:

- `ROLE_ADMIN`
- `ROLE_RECRUIT_ADMIN`
- `ROLE_INTERVIEWER`

Recommended first hardening step:

```java
authorize.requestMatchers("/admin/**").hasAnyRole("ADMIN", "RECRUIT_ADMIN")
```

If existing `DeptRoleMapping.roleName` already stores full authority strings such as `ROLE_ADMIN`, use `hasAnyAuthority(...)` instead of `hasAnyRole(...)`.

Decision point for Phase 03e-3:

- Verify actual `DeptRoleMapping.roleName` values before choosing `hasRole` or `hasAuthority`.
- Do not hardcode department names in `SecurityConfig`.

## Applicant API Protection

Recommended target:

| Path | Authentication | Authorization | Notes |
|---|---|---|---|
| `/applications/**` | Required | `ROLE_APPLICANT` only | Employee/admin must use admin APIs |
| `/job-postings/**` public read | Public or endpoint-specific | No applicant identity required | Public posting read remains separate |
| `/me/password` candidate | Required | `ROLE_APPLICANT` | Future applicant account feature |

Recommended policy:

- Applicant APIs should allow only `ROLE_APPLICANT`.
- Employee/admin users should receive 403 when calling applicant APIs.
- Service-level ownership checks remain mandatory.
- Access to another applicant's application should keep the existing 404 hiding policy.

Reason:

- URL authorization proves the caller is an applicant.
- Service ownership proves the applicant owns the specific resource.
- Both checks are needed.

## Current User Resolver Design

### Keep `CurrentApplicantService`

Use it for applicant-owned resources:

```text
CustomUserDetails -> loginId -> ApplicantRepository -> applicantId
```

It should continue to enforce applicant-only access at service/controller boundary even after `SecurityConfig` is hardened.

### Add `CurrentEmployeeService` in Phase 03e-2

Recommended responsibility:

- Require non-null `CustomUserDetails`.
- Require `userType == Employee`.
- Resolve employee identity for audit fields.
- Return a small value object such as `CurrentEmployee`.

Candidate response object:

```text
CurrentEmployee(
  loginId,
  name,
  deptName,
  authorities
)
```

Recommended initial actor string:

```text
CustomUserDetails.getUsername()
```

Reason:

- `loginId` is available for DB-backed and LDAP/JIT employee users.
- It avoids adding an `Employee` FK before audit policy is stable.
- It can later migrate to `Employee.id`, `Employee` relation, or an `AuditActor` value object.

### Optional `CurrentAdminService`

Use only if the project differentiates recruitment admins from general employees.

Recommended shape:

- Wrap `CurrentEmployeeService`.
- Check required admin authority.
- Return the same actor object or actor string.

### Optional `CurrentUserService`

Use only for common functionality that is genuinely shared by applicant and employee flows.

Do not use a generic resolver to weaken applicant/admin separation.

## StageResult Identity Policy

### Phase 03e-2 Recommended Service Changes

Recommended signatures:

```java
AdminStageResultResponse updateResult(
    Long stageId,
    Long resultId,
    StageResultUpdateRequest request,
    String actor
)

StageResultBulkUpdateResponse bulkUpdateResults(
    Long stageId,
    StageResultBulkUpdateRequest request,
    String actor
)

AdminStageResultResponse correctResult(
    Long stageId,
    Long resultId,
    StageResultCorrectionRequest request,
    String actor
)
```

Controller responsibility:

- Receive `@AuthenticationPrincipal CustomUserDetails`.
- Resolve employee/admin actor through `CurrentEmployeeService` or `CurrentAdminService`.
- Pass actor to service.

Service responsibility:

- Validate business state.
- Store actor in `decidedBy` or `correctedBy`.
- Never reach into `SecurityContextHolder` directly unless the project standardizes on that pattern later.

### Response Exposure

Recommended response policy:

| Response | `decidedBy` | `correctedBy` | Notes |
|---|---:|---:|---|
| `AdminStageResultResponse` | Candidate | No | Add only if admin screens need it |
| `StageResultCorrectionHistoryResponse` | No | Yes | Already designed as admin-only history |
| `ApplicantStageResultResponse` | No | No | Must remain hidden |

Initial recommendation:

- Keep `AdminStageResultResponse` unchanged until a UI requirement asks for `decidedBy`.
- Keep `StageResultCorrectionHistoryResponse.correctedBy`.
- Never expose actor fields to applicant result read.

## Security Exception Handling Policy

Recommended target:

| Case | HTTP | Response |
|---|---:|---|
| Unauthenticated request | 401 | `ApiResponse.fail("Authentication is required.")` |
| Authenticated but unauthorized | 403 | `ApiResponse.fail("Access is denied.")` |
| Applicant accesses another applicant's application | 404 | Existing hidden-resource policy |
| Admin resource not found | 404 | Normal not-found |
| Admin lacks authority | 403 | Do not mask as not-found unless a specific information-hiding policy is defined |

Implementation candidate:

- Configure `exceptionHandling` in `SecurityConfig`.
- Add an `AuthenticationEntryPoint` that writes JSON `ApiResponse.fail`.
- Add an `AccessDeniedHandler` that writes JSON `ApiResponse.fail`.
- Ensure response content type is JSON.

Do not rely on `GlobalExceptionHandler` for Spring Security authentication/authorization exceptions thrown before controller invocation.

## SecurityConfig Transition Plan

Recommended sequence:

1. Keep public endpoints explicit.
2. Add `/admin/**` rule.
3. Add `/applications/**` rule.
4. Add fallback `authenticated()` or carefully reviewed public allowlist.
5. Add JSON 401/403 handlers.
6. Update MockMvc tests to authenticate principals.

Candidate rule order:

```text
/auth/login                  permitAll
/auth/logout                 authenticated or permitAll by policy
/auth/me                     authenticated
/swagger-ui/**               development only
/api-docs/**                 development only
/h2-console/**               development only
/job-postings/**             permitAll for public read endpoints
/admin/**                    admin/recruit employee authority
/applications/**             ROLE_APPLICANT
anyRequest                   authenticated
```

Open question:

- Some existing non-admin/non-applicant APIs such as menu and board may need their own public or authenticated policy.
- Do not switch `anyRequest` from `permitAll` to `authenticated` until these paths are classified.

## Test Strategy

### Phase 03e-2 Tests

StageResult actor propagation:

- Single update stores current admin/employee loginId in `decidedBy`.
- Bulk update stores current admin/employee loginId in each updated result.
- Correction stores current admin/employee loginId in both latest `StageResult.decidedBy` and history `correctedBy`.
- Applicant result response still excludes `decidedBy` and `correctedBy`.

### Phase 03e-3 Tests

Applicant API:

- Unauthenticated request returns 401.
- Employee/admin request returns 403.
- Applicant own request succeeds.
- Applicant other-resource request remains 404.

Admin API:

- Unauthenticated request returns 401.
- Applicant request returns 403.
- Employee/admin request succeeds when authority is valid.
- Employee without required authority returns 403.

### Phase 03e-4 Tests

Security exception JSON:

- 401 response body follows `ApiResponse.fail`.
- 403 response body follows `ApiResponse.fail`.
- Content type is JSON.
- Existing validation/business exceptions still use `GlobalExceptionHandler`.

## Implementation Phasing

| Phase | Goal | Recommended Scope | Out of Scope |
|---|---|---|---|
| Phase 03e-1 | Design | This document and HTML report only | Code/test/config changes |
| Phase 03e-2 | Current admin identity + StageResult actor | `CurrentEmployeeService` or `CurrentAdminService`, controller actor extraction, StageResult service signatures, tests | URL security hardening |
| Phase 03e-3 | URL authorization | `/admin/**`, `/applications/**`, public allowlist, MockMvc auth updates | JSON security handler polish if too large |
| Phase 03e-4 | Security exception responses | 401/403 `ApiResponse.fail`, entry point, access denied handler, tests | Business feature changes |

## Validation and Business Rules

- `/admin/**` must not be accessible to applicants.
- `/applications/**` must not be accessible to employees/admins unless an explicit exception is designed.
- Applicant resource ownership remains service-level and should not move only to `SecurityConfig`.
- Admin actor propagation should use authenticated identity, not a constant.
- Applicant response must not expose admin actor fields.
- Security failures should be separated from business validation failures.
- LDAP and authority source values must remain externalized or DB-driven, not hardcoded in new code.

## Test Commands

Not executed in this phase.

Reason:

- Phase 03e-1 is documentation/design only.
- Java source, test source, build files, YAML, `SecurityConfig`, and schema files were intentionally not changed.

Recommended implementation-phase commands:

```powershell
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.service.StageResultServiceTest --tests com.shinyoung.recruit.service.StageResultCorrectionServiceTest
$env:AES_SECRET_KEY='***'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.StageResultControllerTest --tests com.shinyoung.recruit.controller.ApplicationStageResultControllerTest
$env:AES_SECRET_KEY='***'; .\gradlew.bat clean test --no-daemon
```

## Test Results

- Not run.
- Documentation consistency was checked manually by limiting changes to `docs/codex/**`.

## Remaining Issues

- Actual admin authority names need confirmation from `DeptRoleMapping` seed/operations data.
- `CustomUserDetails` does not expose stable DB user id.
- Current `AuthenticationConfig` and LDAP property design should be reviewed before production hardening.
- Existing MockMvc tests may depend on `permitAll` and will need authentication setup in Phase 03e-3.
- Public endpoint allowlist must be classified before changing fallback authorization.

## Next Phase Recommendation

Recommended next implementation:

1. Phase 03e-2: add current employee/admin identity resolver and replace StageResult `SYSTEM` actor values.
2. Phase 03e-3: apply `/admin/**` and `/applications/**` URL authorization.
3. Phase 03e-4: add JSON `ApiResponse.fail` handling for 401/403.
