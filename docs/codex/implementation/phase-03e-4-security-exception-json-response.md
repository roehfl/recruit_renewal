# Phase 03e-4 Security Exception JSON Response

## Phase Summary

Phase 03e-4 adds Spring Security authentication and authorization failure handlers that return JSON responses using the existing `ApiResponse.fail(...)` shape.

This phase keeps the Phase 03e-3 URL authorization rules unchanged and does not change StageResult actor propagation, applicant ownership checks, DTO response shapes, database schema, LDAP settings, or business exception handling.

## Purpose

- Return `401` security failures as JSON `ApiResponse.fail("Authentication is required.")`.
- Return `403` security failures as JSON `ApiResponse.fail("Access is denied.")`.
- Handle security failures before controller invocation through Spring Security handlers.
- Keep validation, not-found, and business exceptions in `GlobalExceptionHandler`.

## Scope

Implemented:

- `CustomAuthenticationEntryPoint` for unauthenticated requests.
- `CustomAccessDeniedHandler` for authenticated users without authority.
- `SecurityConfig.exceptionHandling(...)` registration.
- JSON content type and UTF-8 response encoding for 401/403.
- `ObjectMapper`-based response serialization.
- MockMvc assertions for 401/403 JSON bodies in StageResult, Stage, and applicant StageResult controller tests.
- Documentation and human-readable HTML report.

## Out-of-Scope Items

- URL authorization rule changes.
- `anyRequest().permitAll()` fallback changes.
- StageResult actor logic changes.
- `CurrentEmployeeService` changes.
- `CurrentApplicantService` changes.
- DTO response shape changes.
- `ApiResponse` structure changes.
- Business exception handler redesign.
- DB schema changes.
- LDAP setting changes.
- Employee FK or audit actor model.
- Read audit logging.
- Message or notification implementation.

## Changed Files

| Path | Type | Notes |
|---|---|---|
| `src/main/java/com/shinyoung/recruit/security/auth/CustomAuthenticationEntryPoint.java` | New | Writes 401 JSON `ApiResponse.fail` responses |
| `src/main/java/com/shinyoung/recruit/security/auth/CustomAccessDeniedHandler.java` | New | Writes 403 JSON `ApiResponse.fail` responses |
| `src/main/java/com/shinyoung/recruit/config/SecurityConfig.java` | Modified | Registers security exception handlers without changing authorization rules |
| `src/test/java/com/shinyoung/recruit/controller/ApplicationStageResultControllerTest.java` | Modified | Verifies applicant API 401/403 JSON responses |
| `src/test/java/com/shinyoung/recruit/controller/StageResultControllerTest.java` | Modified | Verifies admin StageResult 401/403 JSON responses |
| `src/test/java/com/shinyoung/recruit/controller/StageControllerTest.java` | Modified | Verifies admin Stage 401/403 JSON responses |
| `docs/codex/implementation/phase-03e-4-security-exception-json-response.md` | New | Codex implementation reference |
| `docs/codex/reports/phase-03e-4-security-exception-json-response.html` | New | Human-readable report |
| `docs/codex/design/phase-03e-admin-auth-hardening-design.md` | Modified | Added Phase 03e-4 implementation note |
| `docs/codex/design/phase-03-application-design.md` | Modified | Added Phase 03e-4 application security note |
| `docs/codex/07-implementation-history.md` | Modified | Added Phase 03e-4 history entry |

## New Classes

| Package | Class | Class Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.security.auth` | `CustomAuthenticationEntryPoint` | Security handler | Writes unauthenticated request failures as JSON `ApiResponse.fail` with HTTP 401 |
| `com.shinyoung.recruit.security.auth` | `CustomAccessDeniedHandler` | Security handler | Writes unauthorized request failures as JSON `ApiResponse.fail` with HTTP 403 |

## Modified Classes

| Package | Class | Class Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.config` | `SecurityConfig` | Configuration | Registers custom security exception handlers and preserves existing authorization rules |
| `com.shinyoung.recruit.controller` | `ApplicationStageResultControllerTest` | Test | Verifies applicant result read security failures use JSON `ApiResponse.fail` |
| `com.shinyoung.recruit.controller` | `StageResultControllerTest` | Test | Verifies admin StageResult security failures use JSON `ApiResponse.fail` |
| `com.shinyoung.recruit.controller` | `StageControllerTest` | Test | Verifies admin Stage security failures use JSON `ApiResponse.fail` |

## Class-by-Class Explanation

### `CustomAuthenticationEntryPoint`

- Package: `com.shinyoung.recruit.security.auth`
- Class name: `CustomAuthenticationEntryPoint`
- Class type: Security handler
- Responsibility: Handle unauthenticated requests rejected by Spring Security before controller invocation.
- Key fields or methods:
  - `MESSAGE = "Authentication is required."`
  - `commence(...)`
- Related classes:
  - `ApiResponse`
  - `SecurityConfig`
  - Spring Security `AuthenticationEntryPoint`
- Important implementation notes:
  - Sets HTTP status to `401`.
  - Sets `Content-Type` to `application/json;charset=UTF-8`.
  - Serializes `ApiResponse.fail(MESSAGE)` with `ObjectMapper.writeValue(...)`.
  - Uses `ObjectProvider<ObjectMapper>` so the handler can reuse a Spring `ObjectMapper` when one exists without requiring a global mapper bean.

### `CustomAccessDeniedHandler`

- Package: `com.shinyoung.recruit.security.auth`
- Class name: `CustomAccessDeniedHandler`
- Class type: Security handler
- Responsibility: Handle authenticated requests rejected by Spring Security authorization checks.
- Key fields or methods:
  - `MESSAGE = "Access is denied."`
  - `handle(...)`
- Related classes:
  - `ApiResponse`
  - `SecurityConfig`
  - Spring Security `AccessDeniedHandler`
- Important implementation notes:
  - Sets HTTP status to `403`.
  - Sets `Content-Type` to `application/json;charset=UTF-8`.
  - Serializes `ApiResponse.fail(MESSAGE)` with `ObjectMapper.writeValue(...)`.
  - Does not inspect business domain state or resource ownership.

### `SecurityConfig`

- Package: `com.shinyoung.recruit.config`
- Class name: `SecurityConfig`
- Class type: Configuration
- Responsibility: Configure session security, URL authorization, and security exception handling.
- Key fields or methods:
  - `authenticationEntryPoint`
  - `accessDeniedHandler`
  - `filterChain(HttpSecurity http)`
- Related classes:
  - `CustomAuthenticationEntryPoint`
  - `CustomAccessDeniedHandler`
  - `AuthenticationManager`
- Important implementation notes:
  - Adds `http.exceptionHandling(...)`.
  - Keeps CSRF, CORS, session, HTTP Basic, H2 frame, and existing URL authorization policy unchanged.
  - Keeps `/admin/**` restricted to `ROLE_ADMIN` and `ROLE_RECRUIT_ADMIN`.
  - Keeps `/applications/**` restricted to `ROLE_APPLICANT`.
  - Keeps `GET /job-postings/{jobPostingId}/application` restricted to `ROLE_APPLICANT`.
  - Keeps public read and development allowlist rules.
  - Keeps fallback `anyRequest().permitAll()`.

### `ApplicationStageResultControllerTest`

- Package: `com.shinyoung.recruit.controller`
- Class name: `ApplicationStageResultControllerTest`
- Class type: Test
- Responsibility: Verify applicant StageResult read API behavior and URL authorization failure responses.
- Key methods:
  - `application_stage_result_api_blocks_employee_admin_and_anonymous()`
- Related classes:
  - `ApplicationStageResultController`
  - `SecurityConfig`
  - `CustomAuthenticationEntryPoint`
  - `CustomAccessDeniedHandler`
- Important implementation notes:
  - Employee/admin and non-admin employee principals receive `403`.
  - Anonymous principal receives `401`.
  - All security failures assert JSON content type, `success=false`, and the configured message.

### `StageResultControllerTest`

- Package: `com.shinyoung.recruit.controller`
- Class name: `StageResultControllerTest`
- Class type: Test
- Responsibility: Verify admin StageResult API behavior and URL authorization failure responses.
- Key methods:
  - `update_result_fails_when_principal_is_applicant_or_missing()`
  - `update_result_fails_when_employee_has_no_admin_authority()`
  - `correct_result_fails_when_principal_is_applicant_or_missing()`
- Related classes:
  - `StageResultController`
  - `SecurityConfig`
  - `CustomAuthenticationEntryPoint`
  - `CustomAccessDeniedHandler`
- Important implementation notes:
  - Applicant and non-admin employee principals receive `403`.
  - Anonymous principal receives `401`.
  - Existing business exception assertions still verify `GlobalExceptionHandler` responses for validation and not-found cases.

### `StageControllerTest`

- Package: `com.shinyoung.recruit.controller`
- Class name: `StageControllerTest`
- Class type: Test
- Responsibility: Verify admin Stage API behavior and URL authorization failure responses.
- Key methods:
  - `admin_stage_api_blocks_applicant_employee_without_admin_authority_and_anonymous()`
- Related classes:
  - `StageController`
  - `SecurityConfig`
  - `CustomAuthenticationEntryPoint`
  - `CustomAccessDeniedHandler`
- Important implementation notes:
  - Applicant and non-admin employee principals receive `403`.
  - Anonymous principal receives `401`.
  - Existing validation and not-found assertions remain as business exception regression coverage.

## API List

No new application API endpoint was added.

Security failure response behavior changed for protected endpoints:

| Method | Path | Failure Case | HTTP | Response |
|---|---|---|---:|---|
| Any | `/admin/**` | Anonymous request | `401` | `ApiResponse.fail("Authentication is required.")` |
| Any | `/admin/**` | Authenticated principal without admin/recruit authority | `403` | `ApiResponse.fail("Access is denied.")` |
| Any | `/applications/**` | Anonymous request | `401` | `ApiResponse.fail("Authentication is required.")` |
| Any | `/applications/**` | Authenticated non-applicant principal | `403` | `ApiResponse.fail("Access is denied.")` |
| `GET` | `/job-postings/{jobPostingId}/application` | Anonymous request | `401` | `ApiResponse.fail("Authentication is required.")` |
| `GET` | `/job-postings/{jobPostingId}/application` | Authenticated non-applicant principal | `403` | `ApiResponse.fail("Access is denied.")` |

## Entity Relationship Summary

- No entity was added or modified.
- No relationship was added or modified.
- No table, index, foreign key, or schema rule changed.
- `StageResult` actor fields remain as implemented in Phase 03e-2.
- Applicant-facing result responses still do not expose `decidedBy`, `correctedBy`, correction history, score, or comment.

## Entity/DTO/Service/Controller Summary

- Entity: unchanged.
- DTO: unchanged; `ApiResponse` structure is reused as-is.
- Service: unchanged.
- Controller: production controllers unchanged.
- Security handlers: new pre-controller JSON response path for 401/403.
- Tests: targeted controller tests now assert the JSON security response body.

## Validation and Business Rules

- Authentication failure is a security concern and is handled by `CustomAuthenticationEntryPoint`.
- Authorization failure is a security concern and is handled by `CustomAccessDeniedHandler`.
- Business validation failures continue through `GlobalExceptionHandler`.
- Not-found failures continue through `GlobalExceptionHandler`.
- Applicant ownership failures continue to use the existing service-level hidden-resource policy.
- `/admin/**`, `/applications/**`, and applicant-owned job posting application lookup authorization rules are unchanged from Phase 03e-3.

## Test Coverage

Covered:

- Anonymous applicant StageResult read returns `401` JSON.
- Employee/admin applicant StageResult read returns `403` JSON.
- Anonymous admin Stage endpoints return `401` JSON.
- Applicant and non-admin employee admin Stage endpoints return `403` JSON.
- Anonymous admin StageResult commands return `401` JSON.
- Applicant and non-admin employee admin StageResult commands return `403` JSON.
- Existing validation failures still return `400 + ApiResponse.fail`.
- Existing not-found failures still return `404 + ApiResponse.fail`.

## Test Commands

Initial attempt:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.ApplicationStageResultControllerTest --tests com.shinyoung.recruit.controller.StageResultControllerTest --tests com.shinyoung.recruit.controller.StageControllerTest
```

Result:

- Failed before test execution because the Gradle wrapper distribution needed to be downloaded and sandbox network access was blocked.

Approved retry after wrapper download access:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.ApplicationStageResultControllerTest --tests com.shinyoung.recruit.controller.StageResultControllerTest --tests com.shinyoung.recruit.controller.StageControllerTest
```

Result:

- First retry compiled but failed because no global `ObjectMapper` bean existed in the Boot 4 test context.
- The handlers were adjusted to use `ObjectProvider<ObjectMapper>` with a local fallback mapper.
- Final retry succeeded.

Additional service regression command:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.StageResultServiceTest --tests com.shinyoung.recruit.service.StageResultCorrectionServiceTest
```

Result:

- Success.

Full regression command:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test --no-daemon
```

Result:

- Timed out twice, once at 5 minutes and once at 10 minutes.
- Gradle daemons were stopped after each timeout.
- No `<failure>` or `<error>` entries were found in the generated XML test result files, but the full suite did not produce a completed success result.

## Test Results

| Command | Result | Notes |
|---|---|---|
| `ApplicationStageResultControllerTest` + `StageResultControllerTest` + `StageControllerTest` | Success | 37 tests completed |
| `StageResultServiceTest` + `StageResultCorrectionServiceTest` | Success | Actor propagation and correction service regression |
| `clean test --no-daemon` | Timeout | No XML failures/errors observed in generated partial results, but full suite did not complete |

## Known Limitations

- The fallback authorization rule remains `anyRequest().permitAll()`.
- Swagger/OpenAPI and H2 console remain public under the current configuration.
- The security handler messages are fixed English strings.
- The handler fallback `ObjectMapper` is sufficient for `ApiResponse.fail(String)` and does not introduce a global mapper bean.
- Fine-grained admin role separation remains deferred.

## Remaining Issues

- Classify remaining unprotected API families before replacing `anyRequest().permitAll()`.
- Decide whether dev tooling endpoints should be profile-gated before production.
- Decide whether security failure messages should be centralized as constants or error codes.
- Decide whether future admin APIs need roles beyond `ROLE_ADMIN` and `ROLE_RECRUIT_ADMIN`.

## Next Phase Recommendation

Recommended next phase: classify remaining public/authenticated API families and decide when the fallback should move from `permitAll()` to `authenticated()` or a complete allowlist.
