# Phase 05x - Applicant Sign-Up API

## Phase Summary

- Date: 2026-05-27
- Work type: frontend integration support / temporary applicant signup API
- Goal: implement a minimal applicant sign-up API for frontend development support. Production NICE identity verification flow is not in scope.

## Important Notice

> **Temporary signup API; production identity verification flow must replace client-provided ci.**

The `ci` field is accepted directly from the client request in this implementation. In production, `ci` must be obtained from NICE identity verification and must not be client-provided.

## Implemented Scope

- `POST /auth/applicants/sign-up` endpoint
- Request validation (loginId, password, name, phoneNumber, email, ci)
- loginId duplicate check
- email duplicate check (when provided)
- ciHash duplicate check
- Password BCrypt encoding
- Applicant entity creation and persistence
- Response with applicantId, loginId, name only (no sensitive data)
- No auto-login/session creation after signup
- SecurityConfig permitAll for signup endpoint
- GlobalExceptionHandler mapping for InvalidApplicantSignUpException
- Service unit tests (6 cases)
- Controller integration tests (4 cases)

## Not Implemented / Out of Scope

- NICE identity verification integration
- Email verification
- Password strength policy beyond min/max length
- Terms of service agreement
- CAPTCHA or rate limiting
- SMS verification for phone number

## Changed Files

### New Files

| File | Type | Description |
|------|------|-------------|
| `src/main/java/.../dto/request/ApplicantSignUpRequest.java` | Request DTO | Sign-up request record with validation |
| `src/main/java/.../dto/response/ApplicantSignUpResponse.java` | Response DTO | Sign-up response record with `from(Applicant)` |
| `src/main/java/.../exception/InvalidApplicantSignUpException.java` | Exception | Custom exception for sign-up validation failures |
| `src/main/java/.../service/ApplicantSignUpService.java` | Service | Sign-up business logic |
| `src/main/java/.../controller/ApplicantSignUpController.java` | Controller | REST endpoint |
| `src/test/java/.../service/ApplicantSignUpServiceTest.java` | Test | 6 unit test cases |
| `src/test/java/.../controller/ApplicantSignUpControllerTest.java` | Test | 4 integration test cases |

### Modified Files

| File | Change |
|------|--------|
| `src/main/java/.../domain/repository/ApplicantRepository.java` | Added `existsByLoginId`, `existsByEmail`, `existsByCiHash` |
| `src/main/java/.../exception/GlobalExceptionHandler.java` | Added `InvalidApplicantSignUpException` handler |
| `src/main/java/.../config/SecurityConfig.java` | Added `/auth/applicants/sign-up` to permitAll |

## Class-by-Class Explanation

### ApplicantSignUpRequest

- Package: `com.shinyoung.recruit.dto.request`
- Type: Request DTO (record)
- Responsibility: Carry and validate sign-up input
- Key fields: loginId, password, name, phoneNumber, email (optional), ci
- Validation: @NotBlank, @Size, @Email
- Notes: Javadoc marks this as temporary API where ci should come from NICE verification in production

### ApplicantSignUpResponse

- Package: `com.shinyoung.recruit.dto.response`
- Type: Response DTO (record)
- Responsibility: Return safe sign-up result
- Key fields: applicantId, loginId, name
- Key methods: `from(Applicant)` static factory
- Notes: Does not expose password, ci, ciHash, phoneNumber, or email

### InvalidApplicantSignUpException

- Package: `com.shinyoung.recruit.exception`
- Type: Exception (RuntimeException)
- Responsibility: Signal sign-up validation failures (duplicate loginId/email/ciHash)
- Notes: Mapped to 400 Bad Request in GlobalExceptionHandler

### ApplicantSignUpService

- Package: `com.shinyoung.recruit.service`
- Type: Service
- Responsibility: Sign-up business logic orchestration
- Dependencies: ApplicantRepository, PasswordEncoder
- Key methods: `signUp(ApplicantSignUpRequest)` — trim inputs, check duplicates, hash ci, encode password, create and save Applicant
- Notes: No auto-login. Email is optional and normalized (blank → null).

### ApplicantSignUpController

- Package: `com.shinyoung.recruit.controller`
- Type: Controller (@RestController)
- Responsibility: REST endpoint for sign-up
- Base path: `/auth/applicants`
- Key methods: `POST /sign-up`
- Response: `ResponseEntity<ApiResponse<ApplicantSignUpResponse>>`

## API

| Method | Path | Purpose | Auth |
|--------|------|---------|------|
| POST | `/auth/applicants/sign-up` | Applicant sign-up | permitAll |

### Request Body

```json
{
  "loginId": "applicant01",
  "password": "Password1234!",
  "name": "홍길동",
  "phoneNumber": "01012345678",
  "email": "applicant01@example.com",
  "ci": "test-ci-applicant01"
}
```

### Success Response (200)

```json
{
  "success": true,
  "data": {
    "applicantId": 1,
    "loginId": "applicant01",
    "name": "홍길동"
  },
  "message": "정상 처리되었습니다."
}
```

### Failure Response (400)

```json
{
  "success": false,
  "data": null,
  "message": "이미 사용 중인 아이디입니다."
}
```

## Validation and Business Rules

1. loginId: NotBlank, max 100, unique
2. password: NotBlank, min 8, max 100, BCrypt encoded before save
3. name: NotBlank, max 100
4. phoneNumber: NotBlank, max 30
5. email: @Email format, max 255, optional (nullable)
6. ci: NotBlank, max 255, hashed with SHA-256 before save
7. All string inputs trimmed before processing
8. Email blank/whitespace normalized to null
9. loginId uniqueness checked against DB
10. email uniqueness checked against DB (only when not null)
11. ciHash uniqueness checked against DB
12. No auto-login or session creation after signup
13. Response does not expose password, ci, ciHash, phoneNumber, or email

## Test Commands

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*ApplicantSignUp*" --no-daemon
```

## Test Results

- Command: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*ApplicantSignUp*" --no-daemon`
- Result: BUILD SUCCESSFUL
- Related auth/application tests: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*Auth*" --tests "*ApplicationControllerTest" --no-daemon`
- Result: BUILD SUCCESSFUL

### Service Tests (ApplicantSignUpServiceTest)

| Test | Description |
|------|-------------|
| 회원가입_성공 | Successful sign-up with all fields |
| loginId_중복이면_실패 | Duplicate loginId rejection |
| email_중복이면_실패 | Duplicate email rejection |
| ciHash_중복이면_실패 | Duplicate ciHash rejection |
| password가_인코딩되어_저장된다 | Password BCrypt encoding verified |
| 응답에_민감정보가_없다 | Response excludes sensitive data |

### Controller Tests (ApplicantSignUpControllerTest)

| Test | Description |
|------|-------------|
| 회원가입_성공 | POST /auth/applicants/sign-up returns 200 with safe response |
| validation_실패_시_400 | Invalid request returns 400 |
| loginId_중복_시_400 | Duplicate loginId returns 400 |
| GET_요청은_405 | GET method returns 405 |

## Known Limitations

1. ci is client-provided; must be replaced with NICE identity verification in production
2. No password strength policy beyond length
3. No email verification flow
4. No rate limiting or CAPTCHA
5. No SMS verification for phone number

## Next Phase Considerations

- Production NICE identity verification integration
- Email/SMS verification flow
- Password policy strengthening
- Rate limiting for sign-up endpoint
