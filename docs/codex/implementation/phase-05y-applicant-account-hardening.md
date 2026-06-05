# Phase 05y - Applicant Account Hardening

## Phase Summary

- Date: 2026-06-05
- Work type: implementation (설계: `docs/codex/design/phase-05y-applicant-account-hardening-design.md`, 리뷰 2차 반영본 기준)
- Goal: 지원자 loginId 정책(이메일=loginId vs 별도 ID) 미결정 상태에서도 유효한 **결정-독립** 계정 기능 구현 + 현재 코드에 실재하는 loginId 무결성 결함 수정. Phase 09b 착수 전 선행 슬라이스.

## Implemented Scope

### Scope A — loginId 무결성

- `User.loginId`에 `@Column(unique = true)` 추가 (`nullable = false`는 후속 — 픽스처 영향)
- `UserRepository.existsByLoginId(String)` 추가
- `ApplicantSignUpService.signUp()`의 loginId 중복체크를 Applicant 레벨 → **User 레벨(users 테이블 전체)**로 교정. 실패 메시지 불변(`"이미 사용 중인 아이디입니다."`)
- `ApplicantRepository.existsByLoginId` 제거 (프로덕션 사용처 1곳이 위로 이동, 테스트 모킹 전환 완료)
- loginId 정규화는 trim only 유지 (대소문자 semantics는 collation 의존 제거 후속 결정 — 설계 §4 Scope A-5)
- **LDAP JIT 동시 생성 race 복구**: `RoutingAuthenticationProvider.processLdapAndJit()`에서 Employee save 중 `DataIntegrityViolationException` 발생 시 `findUserByLoginId` 재조회 → Employee면 **LDAP 재인증 없이** `buildEmployeeAuthentication(user, ldapUser)` helper로 토큰 생성(복구), Employee 아님/부재면 원본 예외 전파. `processLdap()`의 토큰 생성부도 동일 helper로 추출.

### Scope B — 이메일 중복체크 API

- `GET /auth/applicants/check-email?email=...` (permitAll, advisory 전용)
- 컨트롤러 `@Validated` + `@RequestParam @NotBlank @Email @Size(max = 255)` — 위반 시 기존 `ConstraintViolationException` 핸들러가 400 응답
- signUp의 `normalizeEmail`(trim)과 동일 정규화 후 `existsByEmail` 판정 (같은 private 메서드 재사용)
- 응답: `{ "available": true | false }` — 가입 여부 외 정보 비노출
- SecurityConfig permitAll 목록에 명시 등록 (allowlist 전환 대비)

### Scope C — 비밀번호 변경 API

- `POST /applicant/account/password` (ROLE_APPLICANT)
- currentPassword 일치 검증 → 새 비밀번호가 현재와 동일하면 거부 → BCrypt 인코딩 저장
- 본인 세션 유지(재로그인 불요)

### Scope D — 전화번호 변경 API

- `POST /applicant/account/phone-number` (ROLE_APPLICANT)
- **currentPassword 재확인**(통지 채널 변조 방지) → trim 후 저장
- 전화번호 유니크 제약 없음(기존 정책 유지)

### Scope E — 횡단 보강

- `GlobalExceptionHandler`에 `DataIntegrityViolationException` → **409 CONFLICT** + generic 메시지(`"이미 처리되었거나 중복된 데이터입니다."`). 제약명/SQL 응답 비노출, 원인 예외는 **warn 서버 로그** 기록(관측성 보존)
- `InvalidApplicantAccountException` → 400 핸들러 추가
- `Applicant`에 의미 메서드 `changePassword(String encodedPassword)` / `changePhoneNumber(String phoneNumber)` 추가 (기존 `@Setter`는 유지 — 점진 개선)

## Not Implemented / Out of Scope

- `check-login-id` API (loginId 정책 결정-의존)
- 이메일 변경 API (안 1에서 자격증명 변경이 됨)
- 가입 email `@NotBlank` 전환 (실제 분기점)
- 아이디 찾기 / 비로그인 비밀번호 재설정 (NICE 본인인증 의존)
- email lowercase 정규화 / loginId 대소문자 정규화 (별도 검토·후속 결정)
- NICE 본인인증, rate limiting, CAPTCHA (05x 한계 승계)
- ActivityLog 계측 (9b 이후 계측 스윕에서 일괄)
- `User.loginId` `nullable = false` (후속)
- 운영(MariaDB) DDL 적용 — 수동 적용 절차는 설계 문서 §4 Scope A-6 참조 (사전 점검 4종 + `uk_users_login_id`)

## Changed Files

### New Files

| File | Type | Description |
|------|------|-------------|
| `src/main/java/.../controller/ApplicantAccountController.java` | Controller | 비밀번호/전화번호 변경 엔드포인트 |
| `src/main/java/.../service/ApplicantAccountService.java` | Service | 계정 변경 비즈니스 로직 |
| `src/main/java/.../dto/request/ApplicantPasswordChangeRequest.java` | Request DTO | currentPassword/newPassword record |
| `src/main/java/.../dto/request/ApplicantPhoneNumberChangeRequest.java` | Request DTO | currentPassword/phoneNumber record |
| `src/main/java/.../dto/response/ApplicantEmailAvailabilityResponse.java` | Response DTO | `available` boolean 단일 필드 record |
| `src/main/java/.../exception/InvalidApplicantAccountException.java` | Exception | 계정 변경 검증 실패 → 400 |
| `src/test/java/.../service/ApplicantAccountServiceTest.java` | Test | 7 unit test cases |
| `src/test/java/.../controller/ApplicantAccountControllerTest.java` | Test | 7 integration test cases |
| `src/test/java/.../security/auth/RoutingAuthenticationProviderTest.java` | Test | 4 unit test cases (JIT race 복구, LDAP mock) |

### Modified Files

| File | Change |
|------|--------|
| `src/main/java/.../domain/entity/User.java` | `loginId`에 `@Column(unique = true)` |
| `src/main/java/.../domain/entity/Applicant.java` | `changePassword`/`changePhoneNumber` 의미 메서드 추가 |
| `src/main/java/.../domain/repository/UserRepository.java` | `existsByLoginId` 추가 |
| `src/main/java/.../domain/repository/ApplicantRepository.java` | `existsByLoginId` 제거 |
| `src/main/java/.../service/ApplicantSignUpService.java` | loginId 중복체크 `UserRepository`로 교체(생성자에 UserRepository 추가), `checkEmailAvailability` 추가 |
| `src/main/java/.../controller/ApplicantSignUpController.java` | 클래스 `@Validated` + `GET /check-email` 추가 |
| `src/main/java/.../security/auth/RoutingAuthenticationProvider.java` | JIT save `DataIntegrityViolationException` catch → 재조회 → `buildEmployeeAuthentication` 복구(LDAP 재인증 없음), helper 추출 |
| `src/main/java/.../exception/GlobalExceptionHandler.java` | `InvalidApplicantAccountException` → 400, `DataIntegrityViolationException` → 409(+warn 로그) 핸들러 추가 |
| `src/main/java/.../config/SecurityConfig.java` | permitAll에 `/api/auth/applicants/check-email` 명시 추가 |
| `src/test/java/.../service/ApplicantSignUpServiceTest.java` | `UserRepository` 모킹 전환 + 임직원 점유 loginId 실패 + checkEmailAvailability 3건 추가 (10 cases) |
| `src/test/java/.../controller/ApplicantSignUpControllerTest.java` | check-email 4건 추가 (8 cases) |
| `src/test/java/.../domain/repository/UserRepositoryTest.java` | unique 제약 동작 3건 추가 (4 cases) |

## Class-by-Class Explanation

### ApplicantAccountController

- Package: `com.shinyoung.recruit.controller`
- Type: Controller (@RestController)
- Responsibility: `/applicant/account` 하위 비밀번호/전화번호 변경 엔드포인트
- Key methods: `POST /password`, `POST /phone-number`
- Related classes: `ApplicantAccountService`, `CurrentApplicantService`, `CustomUserDetails`
- Notes: `@AuthenticationPrincipal CustomUserDetails` → `CurrentApplicantService.getCurrentApplicantId()`로 본인 식별(미인증 401 / 비지원자 403 심층 방어). 기존 `requestMatchers("/api/applicant/**").hasAuthority("ROLE_APPLICANT")` 보호 네임스페이스 하위라 SecurityConfig 변경 불요.

### ApplicantAccountService

- Package: `com.shinyoung.recruit.service`
- Type: Service
- Responsibility: 비밀번호/전화번호 변경 비즈니스 로직
- Dependencies: `ApplicantRepository`, `PasswordEncoder`
- Key methods:
  - `changePassword(applicantId, request)` — currentPassword 검증 → 동일 새 비밀번호 거부 → 인코딩 후 `Applicant.changePassword`
  - `changePhoneNumber(applicantId, request)` — currentPassword 검증 → trim 후 `Applicant.changePhoneNumber`
- Notes: 둘 다 `@Transactional` 변경 메서드(dirty checking 저장). Applicant 부재/검증 실패는 `InvalidApplicantAccountException`.

### ApplicantPasswordChangeRequest

- Package: `com.shinyoung.recruit.dto.request`
- Type: Request DTO (record)
- Key fields: `currentPassword`(@NotBlank), `newPassword`(@NotBlank @Size(min=8, max=100) — 05x 가입 정책과 동일)

### ApplicantPhoneNumberChangeRequest

- Package: `com.shinyoung.recruit.dto.request`
- Type: Request DTO (record)
- Key fields: `currentPassword`(@NotBlank), `phoneNumber`(@NotBlank @Size(max=30))
- Notes: 전화번호는 통지 채널이므로 세션 탈취만으로 변조할 수 없도록 currentPassword 재확인을 요구(리뷰 반영).

### ApplicantEmailAvailabilityResponse

- Package: `com.shinyoung.recruit.dto.response`
- Type: Response DTO (record)
- Key fields: `available` boolean 단일 필드 — 가입 여부 외 정보 비노출

### InvalidApplicantAccountException

- Package: `com.shinyoung.recruit.exception`
- Type: Exception (RuntimeException)
- Responsibility: 계정 변경 검증 실패(현재 비밀번호 불일치, 동일 새 비밀번호, 지원자 부재)
- Notes: GlobalExceptionHandler에서 400 매핑

### User (modified)

- Package: `com.shinyoung.recruit.domain.entity`
- Type: Entity (abstract, JOINED 상속 루트)
- Change: `loginId`에 `@Column(unique = true)` — H2(ddl-auto)는 자동 반영, 운영 MariaDB는 수동 DDL(`uk_users_login_id`) 별도 적용 필요

### Applicant (modified)

- Package: `com.shinyoung.recruit.domain.entity`
- Type: Entity
- Change: `changePassword(String encodedPassword)`, `changePhoneNumber(String phoneNumber)` 의미 메서드 추가. 기존 `@Setter` 유지(05x signUp 경로 사용 중 — 점진 개선 원칙), 신규 코드는 의미 메서드만 사용.

### UserRepository / ApplicantRepository (modified)

- `UserRepository.existsByLoginId(String)` 추가 — users 테이블 전체(서브타입 무관) 판정
- `ApplicantRepository.existsByLoginId` 제거 — 유일 사용처가 User 레벨로 이동

### ApplicantSignUpService (modified)

- loginId 중복체크를 `userRepository.existsByLoginId()`로 교체 (생성자에 `UserRepository` 추가)
- `checkEmailAvailability(String email)` 추가 — `@Transactional(readOnly = true)`, `normalizeEmail` 재사용, blank 정규화 결과 null이면 `available=false`

### ApplicantSignUpController (modified)

- 클래스에 `@Validated` 추가(컨트롤러 파라미터 검증 첫 사례)
- `GET /check-email` 추가 — `@RequestParam @NotBlank @Email @Size(max=255)`

### RoutingAuthenticationProvider (modified)

- Package: `com.shinyoung.recruit.security.auth`
- Type: AuthenticationProvider
- Change:
  - `processLdapAndJit()`: `employeeRepository.save()` 호출을 try-catch로 감싸고, `DataIntegrityViolationException` 시 `findUserByLoginId(loginId)` 재조회 → `filter(Employee.class::isInstance)` → 존재하면 `buildEmployeeAuthentication(existingUser, ldapUser)`로 복구, 아니면 `orElseThrow(() -> e)`로 원본 예외 전파
  - `buildEmployeeAuthentication(User, CustomUserDetails)` helper 신설 — **LDAP 재인증 없이** 이미 인증된 `ldapUser`의 authorities로 토큰 생성(2차 리뷰 Major 1)
  - `processLdap()`의 토큰 생성부도 동일 helper 재사용
- Notes: 복구는 loginId race에 한정된다. `Employee.deptName` unique 등 다른 제약 위반이면 재조회가 부재로 떨어져 예외가 전파된다(정상 동작 — 2차 리뷰 Medium 2).

### GlobalExceptionHandler (modified)

- `InvalidApplicantAccountException` → 400
- `DataIntegrityViolationException` → 409 + `"이미 처리되었거나 중복된 데이터입니다."` generic 메시지. `e.getMessage()` 미사용(내부 정보 비노출), 원인 예외 warn 로그 기록. 전역 영향: 기존 전 엔드포인트의 DB 제약 위반 응답이 500 → 409로 변경(설계 결정 — 500 기대 기존 테스트 없음 사전 확인)

### SecurityConfig (modified)

- permitAll 목록에 `/api/auth/applicants/check-email` 명시 추가 (현재 `anyRequest().permitAll()` fall-through로도 접근 가능하나 allowlist 전환 대비)

### Tests

- `ApplicantAccountServiceTest` (Test, Mockito): 비밀번호 변경 성공(인코딩 저장)/현재 비밀번호 불일치/동일 새 비밀번호/지원자 부재, 전화번호 변경 성공/trim/currentPassword 불일치 — 7건
- `ApplicantAccountControllerTest` (Test, @SpringBootTest+MockMvc): 미인증 401, 임직원 403, 비밀번호 변경 200(+BCrypt 실저장 검증)/불일치 400/validation 400, 전화번호 변경 200(+저장 검증)/validation 400 — 7건
- `RoutingAuthenticationProviderTest` (Test, Mockito — 실제 LDAP 미연결): JIT 최초 로그인 성공, race 재조회 Employee → 복구 + **`ldapProvider.authenticate()` 정확히 1회 호출 검증**, 재조회 부재 → 예외 전파, 재조회 비-Employee(지원자 선점) → 예외 전파 — 4건
- `ApplicantSignUpServiceTest` 보강: UserRepository 모킹 전환, 임직원 점유 loginId 실패, checkEmailAvailability 가용/점유/trim — 10건
- `ApplicantSignUpControllerTest` 보강: check-email 200 true/200 false/400 형식/400 blank — 8건
- `UserRepositoryTest` 보강: 동일 loginId 2건 unique 충돌(Employee↔Applicant 교차 — JOINED 부모 테이블 제약 검증), null loginId 2건 허용, existsByLoginId 서브타입 무관 판정 — 4건

## API

| Method | Path | Purpose | Auth |
|--------|------|---------|------|
| GET | `/auth/applicants/check-email?email=...` | 이메일 가용성 advisory 체크 | permitAll |
| POST | `/applicant/account/password` | 비밀번호 변경 | ROLE_APPLICANT |
| POST | `/applicant/account/phone-number` | 전화번호 변경 | ROLE_APPLICANT |

### GET /auth/applicants/check-email

- Query: `email` (필수, @Email, max 255)
- 의미: **email 입력값이 있을 때만 호출하는 advisory(UX 사전 안내) API.** 최종 권위는 signUp 재검증 + DB unique. race로 사전 체크와 결과가 달라질 수 있음은 정상.

```json
{ "success": true, "data": { "available": true }, "message": "정상 처리되었습니다." }
```

### POST /applicant/account/password

```json
{ "currentPassword": "CurrentPw1234!", "newPassword": "NewPassword1!" }
```

- 200: `{ "success": true, "data": null, "message": "정상 처리되었습니다." }`
- 400: 현재 비밀번호 불일치 `"현재 비밀번호가 일치하지 않습니다."` / 동일 새 비밀번호 `"새 비밀번호가 현재 비밀번호와 달라야 합니다."` / Bean Validation

### POST /applicant/account/phone-number

```json
{ "currentPassword": "CurrentPw1234!", "phoneNumber": "01099998888" }
```

- 200 / 400 (currentPassword 불일치 메시지는 비밀번호 변경과 동일 — 검증 로직 공유)

## Validation and Business Rules

1. loginId 중복체크는 users 테이블 전체(임직원 포함) 범위 — `UserRepository.existsByLoginId`
2. `User.loginId` DB unique 제약(JOINED 상속 부모 테이블) — 동시 가입/동시 JIT race의 backstop
3. loginId 정규화는 trim only(가입 경로) — 대소문자 semantics는 후속 결정
4. check-email은 signUp과 동일 정규화(trim) 후 판정, `available`만 응답
5. 비밀번호 변경: currentPassword 일치 필수 → 새 비밀번호 8~100자 → 현재와 동일 거부 → BCrypt 저장
6. 전화번호 변경: currentPassword 일치 필수, NotBlank/max 30, trim 저장
7. DB 제약 위반은 409 + generic 메시지(내부 정보 비노출, 원인 warn 로그)
8. 동시 JIT **loginId race**는 재조회 복구로 양쪽 로그인 성공(LDAP 재인증 없음 — authenticate 1회). loginId race가 아닌 제약 위반(deptName unique 등)은 예외 전파
9. name/ci는 변경 API로 열지 않음(본인인증 산출물)

## Entity Relationship Summary

- `User`(abstract, JOINED) ← `Applicant` / `Employee` 상속 구조 불변. 변경은 `User.loginId` unique 제약과 `Applicant` 의미 메서드 추가뿐 — 연관관계 변경 없음.

## Test Commands

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*ApplicantSignUp*" --tests "*ApplicantAccount*" --tests "*RoutingAuthenticationProvider*" --tests "*UserRepositoryTest*" --no-daemon
```

## Test Results

- Command: 위 scoped 명령
- Result: **BUILD SUCCESSFUL — 40 tests, 0 failures**

| Test class | Tests | Result |
|------------|-------|--------|
| `ApplicantAccountControllerTest` | 7 | passed |
| `ApplicantSignUpControllerTest` | 8 | passed |
| `UserRepositoryTest` | 4 | passed |
| `RoutingAuthenticationProviderTest` | 4 | passed |
| `ApplicantAccountServiceTest` | 7 | passed |
| `ApplicantSignUpServiceTest` | 10 | passed |

- 전체 회귀 미실행(프로젝트 규칙 — 명시 요청 시에만). 설계 검증 시점에 `@Column(unique=true)` 영향 위험 테스트 27건 통과를 사전 실증했고, src/test `setLoginId` 전수조사 결과 영속 중복 픽스처 없음.

## Known Limitations

1. check-email은 enumeration 벡터 — rate limiting/CAPTCHA 미적용(05x 한계 승계, signUp 실패 메시지와 동일 표면)
2. email 비교는 trim만(대소문자 구분) — lowercase 정규화는 별도 검토
3. 비밀번호 변경 시 타 세션 무효화 없음(세션 레지스트리 부재)
4. currentPassword 검증 시도 횟수 제한 없음 — 세션 탈취 상태의 brute-force 이론상 가능
5. CSRF disabled는 기존 공통 posture — 비밀번호 변경은 재검토 시 우선 대상
6. 전화번호 변경 SMS 재인증/변경 알림 없음 — SMS 인증/계정 복구 수단 사용 전 필수 도입
7. loginId 대소문자 semantics가 DB collation 의존 — 후속 phase에서 명시 결정
8. `User.loginId`는 여전히 nullable
9. 운영 MariaDB에 `uk_users_login_id` DDL 수동 적용 필요(설계 문서 §4 Scope A-6 사전 점검 절차 포함)
10. ActivityLog 계측 보류 — 9b 이후 계측 스윕에서 `APPLICANT_PASSWORD_CHANGE` 등 추가

## Next Phase Considerations

- Phase 09b 진행(본 슬라이스는 audit 파이프라인 비접촉 — 영향 없음)
- loginId 정책(이메일 vs 별도 ID) 결정 후: 안 1 = signUp `loginId=email` 대입 + email 필수화, 안 2 = check-login-id API 추가
- 운영 배포 시 MariaDB 수동 DDL 적용(중복/collation 사전 점검 포함)
