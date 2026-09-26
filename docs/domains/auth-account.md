# 인증·지원자 계정 (`auth-account`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [auth-nice-verification](auth-nice-verification.md)(NICE 본인확인) · [role-menu](role-menu.md)(역할 매핑·메뉴) · [application](application.md)(내 지원 현황) · [application-sections](application-sections.md)(기본정보 프리필) · [stage-result](stage-result.md)(마이페이지 전형결과) · [privacy-audit](privacy-audit.md)(PII 파기) · [interview](interview.md)(면접관 식별)

## 요약

- 서버 **세션** 인증(Spring Security + `HttpSession`, `JSESSIONID` 쿠키). 토큰·JWT 없음.
- 로그인 API는 `POST /auth/login` 하나다. `RoutingAuthenticationProvider`가 사용자 유형에 따라 경로를 나눈다. 지원자는 DB 로컬 계정(BCrypt)으로, 임직원은 AD(LDAP) bind로 인증한다. 처음 로그인한 임직원은 `Employee` 행이 자동 생성된다(JIT).
- 지원자 계정 기능: 가입, 이메일 가용성 확인, 아이디(이메일) 찾기, 비밀번호 재발급, 비밀번호 변경, 전화번호 변경. 아이디 찾기는 NICE 본인확인(용도 `FIND_EMAIL`) 뒤 `find-email`이 부분 마스킹한 아이디를 준다(2026-09-22). 가입 이메일 인증·비밀번호 재발급은 가입 이메일로 보낸 6자리 인증번호로 한다(2026-09-23). 가입은 세션의 NICE 본인확인 결과와 이메일 인증에 의존한다(요청 본문에 name·phoneNumber·ci가 없는 이유) — 연동 상세는 [auth-nice-verification](auth-nice-verification.md).
- URL 인가(`SecurityConfig`), 401/403 규약, 역할 상수(`RoleNames`), 현재 사용자 식별(`CurrentApplicantService`·`CurrentEmployeeService`)도 이 카드가 소유한다. 다른 카드가 공용으로 쓴다.

## 용어

| 용어 | 뜻 |
|---|---|
| 지원자 `Applicant` | 로컬 계정(BCrypt). `userType="Applicant"`, 권한 `ROLE_APPLICANT` 고정 |
| 임직원 `Employee` | AD 계정. 비밀번호를 저장하지 않는다. `userType="Employee"`, 권한은 역할 매핑에서 계산 |
| `User` | 두 유형의 부모 엔티티. `users` 테이블 JOINED 상속, `loginId` 전역 unique |
| JIT 생성 | DB에 없는 loginId가 LDAP 인증에 성공하면 즉시 `Employee` 행을 저장하는 것 |
| CI / `ciHash` | `ciHash`는 **이름+생년월일+성별의 HMAC**(`{BE}/common/hash/AuditHmac.java` `identityHash`)으로 중복 가입을 차단한다 — NICE 계약상 CI를 받지 않아 필드명만 남았다. `ci` 컬럼은 제거했다(운영 DDL `recruit_back/recruit_backend/docs/ops/applicant-drop-ci-ddl.sql`). 상세 [auth-nice-verification](auth-nice-verification.md) |
| principal | 세션에 저장되는 `CustomUserDetails`(loginId, name, deptName, userType, authorities) |
| authority(역할) | `ROLE_` 접두까지 포함한 완전한 문자열. 단일 출처는 `RoleNames` |
| 부서·개인 매핑 | `dept_role_mapping`(AD 그룹 cn 부분일치) / `user_role_mapping`(loginId 완전일치). [role-menu](role-menu.md) 소유 |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/AuthController.java` | 로그인·로그아웃·`/auth/me` |
| controller | `{BE}/controller/ApplicantSignUpController.java` | 가입, 이메일 가용성, 가입 인증번호 발송·확인 |
| controller | `{BE}/controller/ApplicantAccountController.java` | 비밀번호·전화번호 변경 |
| controller | `{BE}/controller/ApplicantAccountRecoveryController.java` | 아이디 찾기, 비밀번호 재발급(인증번호 발송·확인·재설정) — 세션 값 검사·소비 |
| service | `{BE}/service/ApplicantSignUpService.java` | 가입 검증·저장, 가입 인증 메일 |
| service | `{BE}/service/ApplicantAccountService.java` | 현재 비밀번호 재확인 후 변경 |
| service | `{BE}/service/ApplicantAccountRecoveryService.java` | 식별 키로 계정 조회·`loginId` 마스킹, 재설정 메일·새 비밀번호 저장 |
| service | `{BE}/service/EmailVerificationService.java` `{BE}/service/EmailVerificationState.java` `{BE}/enumeration/EmailVerificationPurpose.java` | 인증번호 발급·확인·확인 후 10분 검사, 세션 값(번호는 해시), 목적 → 메일 종류 |
| service | `{BE}/service/CurrentApplicantService.java` | principal → applicantId (401/403), 지원자 API 공용 |
| service | `{BE}/service/CurrentEmployeeService.java` | principal → 임직원 actor/employeeId (401/403), 관리자·면접관 API 공용 |
| config | `{BE}/config/SecurityConfig.java` | 필터 체인, URL 매처, CORS, 컨텍스트 저장소 |
| config | `{BE}/config/AuthenticationConfig.java` | `AuthenticationManager`, LDAP/DAO provider, BCrypt |
| config | `{BE}/config/LdapProperties.java` | `recruit.ldap.*`, `isConfigured()` |
| config | `{BE}/security/auth/RoutingAuthenticationProvider.java` | 지원자 DAO / 임직원 LDAP 분기, JIT |
| config | `{BE}/security/auth/CustomUserDetailsService.java` | 지원자 전용 조회, `ROLE_APPLICANT` 부여 |
| config | `{BE}/security/auth/CustomLdapUserDetailsMapper.java` | AD 그룹 → 부서 role ∪ 개인 role, 부서명 결정 |
| config | `{BE}/security/auth/CustomUserDetails.java` | 세션 principal (`fromUser`/`fromLdap`) |
| config | `{BE}/security/auth/RoleNames.java` | 역할 상수, `ASSIGNABLE_ROLES` |
| config | `{BE}/security/auth/CustomAuthenticationEntryPoint.java` | 필터 401 JSON |
| config | `{BE}/security/auth/CustomAccessDeniedHandler.java` | 필터 403 JSON |
| entity | `{BE}/domain/entity/User.java` | `users`, `loginId` unique |
| entity | `{BE}/domain/entity/Applicant.java` | email(unique)·password·phoneNumber·ciHash(unique) |
| entity | `{BE}/domain/entity/Employee.java` | `deptName`(unique 아님) |
| repository | `{BE}/domain/repository/UserRepository.java` | `findUserByLoginId`, `existsByLoginId` |
| repository | `{BE}/domain/repository/ApplicantRepository.java` | `findByLoginId`, `findByEmail`, `existsByEmail`, `existsByCiHash`, `findByCiHash` |
| repository | `{BE}/domain/repository/EmployeeRepository.java` | `findByLoginId(In)` |
| dto | `{BE}/dto/request/LoginRequest.java` | 로그인 요청 |
| dto | `{BE}/dto/response/LoginUserResponse.java` | 로그인 사용자 응답 |
| dto | `{BE}/dto/request/ApplicantSignUpRequest.java` | 가입 요청 |
| dto | `{BE}/dto/response/ApplicantSignUpResponse.java` | 가입 응답 |
| dto | `{BE}/dto/response/ApplicantEmailAvailabilityResponse.java` | `available` |
| dto | `{BE}/dto/request/ApplicantPasswordChangeRequest.java` | 비밀번호 변경 요청 |
| dto | `{BE}/dto/request/ApplicantPhoneNumberChangeRequest.java` | 전화번호 변경 요청 |
| dto | `{BE}/dto/response/ApplicantFindEmailResponse.java` | `{ maskedEmail }` |
| dto | `{BE}/dto/request/EmailVerificationSendRequest.java` `{BE}/dto/request/EmailVerificationConfirmRequest.java` `{BE}/dto/request/ApplicantPasswordResetRequest.java` | 인증번호 발송·확인, 재설정 |
| exception | `{BE}/exception/InvalidApplicantSignUpException.java` | 400 |
| exception | `{BE}/exception/InvalidApplicantAccountException.java` | 400 |
| exception | `{BE}/exception/InvalidEmailVerificationException.java` | 400 |
| exception | `{BE}/exception/AuthenticationRequiredException.java` | 401 |
| exception | `{BE}/exception/AccessForbiddenException.java` | 403 |
| test | `{BT}/controller/ApplicantSignUpControllerTest.java` | 가입·check-email |
| test | `{BT}/controller/ApplicantAccountControllerTest.java` | `springSecurity()`: 401/403/400 |
| test | `{BT}/service/ApplicantSignUpServiceTest.java` | 중복·인코딩·민감정보 미노출 |
| test | `{BT}/service/ApplicantAccountServiceTest.java` | 비밀번호 불일치·동일값 거부 |
| test | `{BT}/service/ApplicantAccountRecoveryServiceTest.java` | 조회·미존재·마스킹 규칙 |
| test | `{BT}/controller/ApplicantAccountRecoveryControllerTest.java` | 성공·1회용·세션 없음·용도 불일치·만료·404 |
| test | `{BT}/service/EmailVerificationServiceTest.java` `{BT}/controller/ApplicantEmailVerificationControllerTest.java` `{BT}/controller/ApplicantPasswordResetControllerTest.java` | 인증번호 규칙, 가입 인증·재발급 흐름 |
| test | `{BT}/service/CurrentApplicantServiceTest.java` | 401/403 예외 |
| test | `{BT}/service/CurrentEmployeeServiceTest.java` | 401/403·blank actor |
| test | `{BT}/security/auth/RoutingAuthenticationProviderTest.java` | JIT·경합 복구·임직원 부서명(LDAP 최신값) |
| test | `{BT}/security/auth/CustomLdapUserDetailsMapperTest.java` | 매핑 합집합 |
| test | `{BT}/security/auth/CustomUserDetailsTest.java` | username·userType |
| test | `{BT}/config/SecurityConfigTest.java` | 매처 401/403/통과 |
| test | `{BT}/config/AuthenticationConfigTest.java` | 컨텍스트 로드만 시도(단정 전부 주석, 비활성 단정) |
| test | `{BT}/config/LdapPropertiesTest.java` | `isConfigured()` |
| test | `{BT}/domain/repository/UserRepositoryTest.java` | loginId unique |
| test | `{BT}/domain/repository/ApplicantRepositoryTest.java` | 저장·ciHash 조회 |
| test | `{BT}/domain/repository/EmployeeRepositoryTest.java` | 같은 deptName 2명 |

### 프론트

| 구분 | 파일 | 역할 |
|---|---|---|
| route | `{FE}/routes/authRoutes.ts` | `Login` `/login`, `NiceAuthPopup` `/nice-auth`, `NiceAuthResult` `/nice-auth/result`(전부 `public`) |
| route | `{FE}/routes/applicantRoutes.ts` | (공유) `Signup`·`accountRecovery`(`public`), `ApplicantProfile`(`requiresAuth`+`ROLE_APPLICANT`) |
| view | `{FE}/views/auth/LoginView.vue` | 로그인, 로그인 후 이동 |
| view | `{FE}/views/applicant/SignupView.vue` | 가입(이메일=loginId, 메일 인증번호). 이름·휴대폰은 NICE 결과로 채워지는 읽기 전용 필드 |
| view | `{FE}/views/applicant/AccountRecovery.vue` | 가운데 단일 카드 + `a-tabs` 2개: 아이디 찾기(NICE 실연동, 마스킹 아이디 표시 후 로그인·비밀번호 재발급 탭으로 이동)·비밀번호 재발급(인증번호 → 새 비밀번호) |
| view | `{FE}/views/applicant/ApplicantProfile.vue` | 마이페이지: 비밀번호 변경, 로그아웃, 내 지원 목록(760px 이하 카드), 전형결과 모달, 면접 추가사항 열 |
| api | `{FE}/api/authApi.ts` | `login`, `me`, `logout` |
| api | `{FE}/api/applicationApi.ts` | (공유) `signup`, `checkEmail`, `findEmail`, `changePassword`, 인증번호 5종 |
| types | `{FE}/types/auth.ts` | `LoginRequest`, `LoginUser` |
| types | `{FE}/types/application.ts` | (공유) `SignupUser`(`{ loginId, password, email }`, name·phoneNumber·ci 없음), `checkEmailRequest`, `FindEmailResponse`, `ChangePasswordParams`, `ChangePasswordRequest`, `EmailVerificationRequest`, `PasswordResetRequest` |
| store | `{FE}/stores/authStore.ts` | `user`·`initialized`, `login`/`fetchMe`/`logout` |
| test | `{FE}/stores/__tests__/authStore.spec.ts` | `fetchMe` 판정 |

NICE 본인확인 컨트롤러·화면(`NiceVerificationController.java`, `NiceAuthPopup.vue`·`NiceAuthResult.vue`)은 [auth-nice-verification](auth-nice-verification.md) 소유다. `{FE}/routes/authRoutes.ts`(위 표)는 이 카드가 소유한 `Login` 라우트와 함께 그 카드 소유 라우트 2개도 같은 파일에 선언한다.

## API 계약

| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |
|---|---|---|---|---|---|
| 🟢 | POST | /auth/login | `{ loginId, password }` | `{ loginId, name, deptName, userType, roles[] }` + 세션 쿠키 | 공개 |
| 🟢 | POST | /auth/logout | 없음 | `Void` | 공개 |
| 🟢 | GET | /auth/me | 없음 | login과 동일 / 미로그인 401 | 공개(컨트롤러가 401) |
| 🟢 | POST | /auth/applicants/sign-up | `{ loginId, password, email }`(name·phoneNumber·ci 없음 — 세션의 NICE 결과·이메일 인증을 쓴다) | `{ applicantId, loginId, name }` | 공개 |
| 🟢 | GET | /auth/applicants/check-email | `?email=` | `{ available }` | 공개 |
| 🟢 | POST | /auth/applicants/find-email | 없음(세션의 NICE 인증 결과, 용도 `FIND_EMAIL`) | `{ maskedEmail }` | 공개 |
| 🟢 | POST | /auth/applicants/email-verification/send | `{ email }` | `null` | 공개 |
| 🟢 | POST | /auth/applicants/email-verification/verify | `{ email, code }` | `null` | 공개 |
| 🟢 | POST | /auth/applicants/password-reset/send | `{ email }` | `null` | 공개 |
| 🟢 | POST | /auth/applicants/password-reset/verify | `{ email, code }` | `null` | 공개 |
| 🟢 | POST | /auth/applicants/password-reset | `{ email, newPassword }` | `null` | 공개 |
| 🟢 | POST | /applicant/account/password | `{ currentPassword, newPassword }` | `Void` | 지원자 |
| 🟢 | POST | /applicant/account/phone-number | `{ currentPassword, phoneNumber }` | `Void` | 지원자 |

NICE 본인확인 엔드포인트 4개(`/auth/nice/request`·`/auth/nice/callback`·`/auth/nice/callback/error`·`/auth/nice/result`)는 [auth-nice-verification](auth-nice-verification.md) 소유.

- 응답은 모두 `ApiResponse<T>`(`{ success, data, message }`)로 감싼다. 이 표는 코드 기준으로 작성했다(이전 계약 문서에 섹션 없음).
- ApplicantProfile이 호출하는 다른 카드 API: `GET /applications/me`(내 지원 목록, pageSize 5) → [application](application.md), `GET /applications/{applicationId}/stage-results` → [stage-result](stage-result.md), `GET /applicant/interview-supplements`(추가사항 입력 열·입력 모달) → [interview-supplement](interview-supplement.md).

### 엔드포인트 상세

**POST /auth/login** 🟢(2026-09-19 deptName 결함 수정 후 확정)
- 성공 흐름: 새 SecurityContext 생성 → `request.getSession(true)` → `request.changeSessionId()` → `securityContextRepository.saveContext()`.
- 실패(비밀번호 불일치, 계정 없음, LDAP 접속 실패): `AuthenticationException`이 필터 체인까지 올라가 `CustomAuthenticationEntryPoint`가 **401** `"Authentication is required."`를 반환한다. `GlobalExceptionHandler`에는 이 예외 핸들러가 없다. 전용 테스트는 없다. 프론트는 서버 메시지를 쓰지 않고 `'아이디 또는 비밀번호를 확인하세요.'`를 고정으로 보여준다. 빈 입력은 400.
- `deptName`: 임직원은 LDAP 최신 부서명(매퍼 `resolveDeptName` 결과), 지원자는 빈 문자열. 임직원 최종 principal은 `buildEmployeeAuthentication`이 `CustomUserDetails.fromLdap`으로 만든다(`fromUser`는 부서명을 비우므로 임직원에 쓰지 않는다). 권한(`roles`)은 LDAP 매퍼 단계에서 계산한 값을 그대로 쓴다. ({BE}/security/auth/RoutingAuthenticationProvider.java — buildEmployeeAuthentication)
- 응답에 휴대폰 번호는 없다. 지원서 기본정보의 휴대폰은 `GET /applications/{applicationId}/basic-info`의 prefill이 채운다([application-sections](application-sections.md)).

**POST /auth/logout**: SecurityContext를 비우고, 세션이 있으면 `invalidate()`한다. 호출 위치: `ApplicantProfile`, `{FE}/layouts/ApplicantHeader.vue`, `{FE}/layouts/AdminSidebar.vue`.

**GET /auth/me** 🟢: 미인증이면 컨트롤러가 직접 401 `"로그인이 필요합니다."`를 반환한다(`anyRequest().permitAll()`로 통과). 응답은 login과 같다. `roles`에는 authority 문자열이 담긴다. FE는 `skipAuthRedirect`·`skipClientEventLog`를 붙여 호출한다.

NICE 4종(`request`·`callback`·`callback/error`·`result`) 상세는 [auth-nice-verification](auth-nice-verification.md) 참고.

**POST /auth/applicants/sign-up**
- 검증: loginId ≤100, password 8~100(모두 `@NotBlank`), email `@Email` ≤255. **name·phoneNumber·ci는 요청 본문에 없다** — 세션의 NICE 인증 결과(`requireFresh(purpose=SIGNUP)`)를 쓴다. 없으면(미진행·용도 불일치·만료) 400. 그다음 `requireVerified(SIGNUP, email)`: email이 비었거나 확인 전·확인 후 10분 지남·다른 이메일이면 400 `이메일 인증이 필요합니다.`. 둘 다 중복 검사보다 먼저다.
- 400 메시지는 검사 순서대로 `"이미 사용 중인 아이디입니다."` → `"이미 사용 중인 이메일입니다."` → `"이미 가입된 본인인증 정보입니다."`다. 동시 가입 경합으로 DB unique에 걸리면 409 `"이미 처리되었거나 중복된 데이터입니다."`.
- 가입 성공 후 세션의 NICE 결과와 이메일 인증 상태는 **1회용이라 즉시 제거**한다.
- 가입 후 자동 로그인하지 않는다(FE는 `/applicant`로 이동). FE 응답 타입(`SignupUser`)은 틀렸지만 응답을 쓰지 않는다.

**GET /auth/applicants/check-email**: `@NotBlank @Email @Size(max=255)` 위반은 400. trim한 뒤 `available = !existsByEmail`이다. 참고용(advisory)이고, 최종 판정은 가입 시 재검증과 DB unique가 한다. `SignupView`는 available=true를 "가입 가능"으로 읽고, `AccountRecovery`는 거꾸로 available=false를 "가입된 메일"로 읽는다. FE 응답 타입(`checkEmailRequest`)은 래퍼가 이중이라 `as unknown as` 캐스팅으로 우회한다.

**POST /auth/applicants/find-email** 🟢(2026-09-22): 세션 `NICE_VERIFIED`를 `requireFresh(purpose=FIND_EMAIL)`로 검사한 뒤 **조회 전에 제거**한다(인증 1회 = 조회 1회, 계정이 없어도 소비). 식별 키(`identityHash`)로 `findByCiHash` → `loginId`를 부분 마스킹해 응답한다(로컬부 3자 이상은 앞 2자, 2자 이하는 앞 1자 + 나머지 길이만큼 `*`(최소 1개), 도메인 그대로 — `abc12345@gmail.com` → `ab******@gmail.com`). 오류: 인증 없음·용도 불일치·만료 400(`requireFresh` 문구), 일치 계정 없음(미가입·파기) 404 `"본인인증 정보와 일치하는 계정이 없습니다."`. 원문 아이디·생년월일·성별은 응답·로그에 없다. 가입용 인증으로는 호출할 수 없고, 이 인증으로는 가입할 수 없다.

**인증번호 5종**(2026-09-23): 세션 키는 목적별, 값 `EmailVerificationState`(번호는 SHA-256 해시만). 숫자 6자리·유효 5분·재발송은 60초 뒤·5회 틀리면 무효·확인 후 10분 안에 가입/재설정. 메일은 `SystemMailService`가 동기 발송([message-delivery](message-delivery.md)), 접수되지 않으면 세션에 저장하지 않는다.
- send 400: 가입 인증에서 가입된 이메일 `이미 사용 중인 이메일입니다.` · `인증번호는 60초 후에 다시 받을 수 있습니다.` · `인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해 주세요.` · `인증 메일 템플릿이 없습니다. 관리자에게 문의하세요.` / 재발급에서 미가입 404 `가입된 이메일이 아닙니다.`(계정 열거 감수).
- verify 400: `인증번호를 다시 받아 주세요.`(상태 없음·이메일 다름·만료) · `인증번호를 5회 틀렸습니다. 인증번호를 다시 받아 주세요.` · `인증번호가 일치하지 않습니다.`(실패 수 +1).
- `password-reset`: `requireVerified(PASSWORD_RESET, email)` 실패 400 → BCrypt 저장 → 세션 상태 제거. 다른 로그인 세션은 그대로.

**POST /applicant/account/password**: 400 `"현재 비밀번호가 일치하지 않습니다."` / `"새 비밀번호가 현재 비밀번호와 달라야 합니다."` / `"지원자 정보를 찾을 수 없습니다."`, 미인증 401, 임직원 403(필터). 변경 후에도 현재 세션과 다른 세션을 무효화하지 않는다. FE(`ApplicantProfile` "내 정보 수정" 모달)는 새 비밀번호 확인 일치와 현재 비밀번호 입력 여부만 검사한다.

**POST /applicant/account/phone-number**: currentPassword를 재확인한 뒤 trim해서 저장한다. 전화번호 unique는 없다. **FE 미사용**(호출 코드·UI 없음).

## 규칙·불변식

### 세션
- 인증 상태는 서버 세션에만 둔다. 백엔드는 redirect하지 않고, 로그인 화면으로 보내는 일은 FE가 한다. CSRF·httpBasic·frameOptions(H2 콘솔 때문)는 끈다. 세션 정책은 `IF_REQUIRED`이고 타임아웃 설정은 없다(서블릿 기본값). (`{BE}/config/SecurityConfig.java` — filterChain)
- formLogin을 쓰지 않으므로 세션 고정 방어는 `request.changeSessionId()` 한 줄뿐이다. **제거 금지.** (`{BE}/controller/AuthController.java` — login)
- 역할은 로그인 시점에 계산해 세션에 저장한다. 역할 매핑을 바꾸면 **다시 로그인해야** 반영된다. (`{BE}/security/auth/RoutingAuthenticationProvider.java` — buildEmployeeAuthentication)

### 로그인 분기 (`RoutingAuthenticationProvider.authenticate`)

| `findUserByLoginId` 결과 | 인증 경로 | 권한 |
|---|---|---|
| `Applicant` | `DaoAuthenticationProvider` → `CustomUserDetailsService`, BCrypt | `ROLE_APPLICANT` |
| `Employee` | LDAP bind(`processLdap`) | LDAP 매퍼 |
| 없음 | LDAP bind → 성공 시 `Employee` JIT 저장(`processLdapAndJit`) | LDAP 매퍼 |

- DB에 없는 loginId는 모두 LDAP 경로로 간다. 지원자가 아이디를 오타 내도 LDAP bind를 시도한다.
- `CustomUserDetailsService`는 `Applicant`가 아니면 `UsernameNotFoundException`을 던진다. DAO 경로로는 임직원을 인증할 수 없다.
- JIT 저장이 경합으로 `DataIntegrityViolationException`을 내면 다시 조회한다. `Employee`가 있으면 **LDAP 재인증 없이** 토큰을 만든다. 없으면 예외를 전파하고 409가 된다. (`RoutingAuthenticationProvider` — processLdapAndJit)
- 기존 `Employee`의 name·deptName은 다시 로그인해도 갱신하지 않는다(JIT 시점 값 유지). 단 세션 principal의 부서명은 로그인마다 LDAP 최신값이다.
- 임직원 권한 = 부서 매핑 role ∪ 개인 매핑 role. 추가만 하고 회수(revoke)는 없다. 매핑이 없어도 권한 0개로 로그인은 성공한다. 부서 매핑은 AD 그룹 cn이 매핑 부서명을 **포함**하는지로 찾는다. 표시용 부서명은 매핑 부서명 → AD `department` 속성 → 첫 그룹 cn 순으로 정하고 JIT 때 `Employee.deptName`에 저장한다. (`{BE}/security/auth/CustomLdapUserDetailsMapper.java` — mapUserFromContext/resolveDeptName)
- LDAP 그룹 조회는 접두어 `""`, 대문자 변환 off로 설정해 그룹 cn 원문을 받는다. (`{BE}/config/AuthenticationConfig.java` — ldapAuthenticationProvider)
- loginId는 가입할 때만 trim하고, 로그인과 JIT에서는 입력값을 그대로 쓴다. 대소문자 구분은 DB collation을 따른다(정책 미결정).

### LDAP 설정·미설정
- 설정 키 `recruit.ldap.*`(`{BR}/application.yaml`), 환경변수 `LDAP_URL`, `LDAP_BASE_DN`, `LDAP_MANAGER_DN`, `LDAP_MANAGER_PASSWORD`, `LDAP_USER_SEARCH_BASE`, `LDAP_USER_SEARCH_FILTER`(기본 `(sAMAccountName={0})`), `LDAP_GROUP_SEARCH_BASE`. **실제 값은 코드·문서·로그·커밋에 쓰지 않는다.** 자격증명에는 기본값이 없다. (`{BE}/config/LdapProperties.java`)
- `isConfigured()` 조건: url이 비어 있지 않고 `ldap://`(미설정 표식)가 아닐 것, 그리고 managerDn·managerPassword·userSearchBase가 모두 있을 것. base와 groupSearchBase는 선택이다.
- **미설정이면**: 기동은 성공하고 경고 로그 1줄만 남는다(비밀값 미출력). 지원자 로그인은 정상. 임직원이나 DB에 없는 loginId는 LDAP 접속 실패로 401이 된다. 로컬 관리자 화면 우회 수단(목 사용자, dev 프로필)은 없다. 코드 추론이며 활성 테스트 없음. (`AuthenticationConfig` — ldapAuthenticationProvider)
- LDAP 값을 `@NotBlank`로 강제하지 않는다. 강제하면 LDAP 없는 환경에서 기동이 실패한다.

### 역할·URL 인가 (`SecurityConfig.filterChain`)
- 역할: `ROLE_ADMIN`(IT), `ROLE_RECRUIT_ADMIN`(채용 운영), `ROLE_PRIVACY_ADMIN`(정보보호), `ROLE_INTERVIEWER`, `ROLE_EMPLOYEE`, `ROLE_APPLICANT`. 매핑 화면에서 부여할 수 있는 역할은 APPLICANT를 뺀 5개다. (`{BE}/security/auth/RoleNames.java`)
- 매처에는 `hasAuthority`/`hasAnyAuthority`와 `RoleNames` 상수를 쓴다. **`hasRole` 금지**(`ROLE_ROLE_` 이중 접두가 된다).
- 매처 경로는 `/api`를 포함해 쓴다(`{BE}/config/WebMvcConfig.java`가 접두를 붙인다). 먼저 맞는 매처가 적용되므로 **좁은 매처를 넓은 매처보다 먼저** 둔다.
- 마지막이 `anyRequest().permitAll()`이다. 아래 보호 경로에 해당하지 않으면 **무인증으로 공개**된다.

| 경로(`/api` 포함, 선언 순서) | 권한 |
|---|---|
| `/api/auth/login`, `/api/auth/logout`, `/api/auth/applicants/`(`sign-up`·`check-email`·`find-email`·`email-verification/*`·`password-reset[/*]`) | 공개 |
| `/swagger-ui/**`, `/api-docs/**`, `/v3/api-docs/**`, `/h2-console/**`, `/api/menu/tree` | 공개 |
| POST `/api/menu/admin/menu`, `/api/menu/admin/menu/*`, POST `/api/board/**` | ADMIN, RECRUIT_ADMIN |
| GET `/api/job-postings/{jobPostingId}/application` | APPLICANT |
| GET `/api/job-postings/**`, POST `/api/client-events` | 공개 |
| GET `/api/admin/audit/**` | RECRUIT_ADMIN, PRIVACY_ADMIN |
| `/api/admin/retention/**` | 쓰기(execute·reconcile·policies·holds·anchor)와 holds 조회 → PRIVACY_ADMIN / dry-run·그 외 GET → RECRUIT_ADMIN, PRIVACY_ADMIN |
| `/api/admin/client-events/**` | POST cleanup → PRIVACY_ADMIN / GET → RECRUIT_ADMIN, PRIVACY_ADMIN |
| `/api/admin/**` | ADMIN, RECRUIT_ADMIN |
| `/api/applicant/**`, `/api/applications/**` | APPLICANT |
| `/api/interviewer/**` | EMPLOYEE, ADMIN, RECRUIT_ADMIN, INTERVIEWER |
| 그 외 | 공개 |

- CORS: 허용 origin은 `http://localhost:5173`과 운영 도메인 1개. 메서드는 **GET·POST만** 허용하고, credentials 허용, 노출 헤더는 `X-Request-Id`·`Content-Disposition`. 그래서 수정·삭제 API도 POST로 만든다. (`SecurityConfig` — corsConfigurationSource)

### 401/403

| 상황 | 상태 | 메시지 | 강제 위치 |
|---|---|---|---|
| 보호 경로 미인증 / 로그인 실패 | 401 | `Authentication is required.` | `{BE}/security/auth/CustomAuthenticationEntryPoint.java` |
| 보호 경로 권한 부족 | 403 | `Access is denied.` | `{BE}/security/auth/CustomAccessDeniedHandler.java` |
| 서비스 식별: principal null | 401 | `... authentication is required.` | `AuthenticationRequiredException` |
| 서비스 식별: userType 불일치 | 403 | `Only ... users can access ...` | `AccessForbiddenException` |
| `/auth/me` 미로그인 | 401 | `로그인이 필요합니다.` | `AuthController` — me |

- 응답은 모두 `ApiResponse.fail` JSON이다. 인증·인가 실패를 400으로 내지 않는다. (`{BE}/exception/GlobalExceptionHandler.java` — handleAuthenticationRequired/handleAccessForbidden)
- 지원자·관리자·면접관 컨트롤러는 `@AuthenticationPrincipal CustomUserDetails`를 받아 `CurrentApplicantService.getCurrentApplicantId`나 `CurrentEmployeeService.getCurrentEmployeeActor`/`getCurrentEmployeeId`로 사용자를 식별한다. 매처가 빠졌을 때의 2차 방어선이다.
- 무결성 오류는 400이다. blank actor → `InvalidStageResultException`([stage-result](stage-result.md)) 또는 `InvalidInterviewException`([interview](interview.md)), 지원자 행 없음 → `InvalidJobApplicationException`([application](application.md)), 임직원 행 없음 → `InvalidInterviewException`. (`{BE}/service/CurrentEmployeeService.java`, `{BE}/service/CurrentApplicantService.java`)

### 지원자 가입·계정
- loginId(trim)는 email과 대소문자 무시로 같아야 한다. 아니면 400 `아이디는 인증한 이메일과 같아야 합니다.`(중복 검사보다 먼저).
- loginId 중복은 **users 전체**(`existsByLoginId`)에서 검사한다. 지원자 범위만 보면 임직원 loginId와 겹쳐 양쪽 다 로그인이 막힌다. 최종 방어선은 `User.loginId` unique다. (`{BE}/service/ApplicantSignUpService.java` — signUp)
- 저장 전 처리: loginId·name·phoneNumber·ci는 trim, email은 trim 후 빈 값이면 null(소문자화 안 함). 비밀번호는 BCrypt로 저장하고, name은 `userName`에도 복사한다.
- ciHash·password는 응답·로그·export에 넣지 않는다. (`{BT}/service/ApplicantSignUpServiceTest.java` — 응답에_민감정보가_없다)
- **(2026-09-21 해소)** ci는 더 이상 클라이언트가 보낸 값을 쓰지 않는다. 서버가 세션에 둔 NICE 인증 결과만 쓴다(`ApplicantSignUpRequest` javadoc). 상세는 [auth-nice-verification](auth-nice-verification.md) 참고.
- 파기: `Applicant.purgePersonalData`가 PII를 null로 만들고 ciHash를 `PURGED:`+UUID로 덮어쓴다. 이후 그 계정은 로그인할 수 없고 같은 사람(이름+생년월일+성별)이 재가입할 수 있다. 호출은 [privacy-audit](privacy-audit.md). (`{BE}/domain/entity/Applicant.java`)
- 비밀번호·전화번호 변경에는 `currentPassword` 재확인이 필수다(세션 탈취만으로 통지 채널을 바꾸지 못하게). 변경은 setter 대신 `Applicant.changePassword`/`changePhoneNumber`로 한다. (`{BE}/service/ApplicantAccountService.java` — verifyCurrentPassword)
- 이메일 변경 API는 없다(불허). **loginId 정책은 확정됐고**(아래 "함정·결정" — 이메일 = loginId), 아이디 찾기(2026-09-22)와 로그인 전 비밀번호 재설정(2026-09-23)은 구현됐다.
  - **비밀번호 재설정**: 가입 이메일로 인증번호 → 화면에서 확인 → 그 자리에서 새 비밀번호 설정(2026-09-23, 임시 비밀번호·토큰 링크 방식을 대체).

### 프론트
- `authStore.fetchMe`: `success && data`일 때만 로그인으로 복구하고 나머지는 `user=null`로 둔다. **401일 때만** `initialized=true`로 확정하고, 네트워크 오류·5xx는 다음 이동 때 다시 확인한다. (`{FE}/stores/authStore.ts`)
- 전역 가드 순서: 미초기화면 `fetchMe` → `meta.public`이면 통과 → `requiresAuth`인데 미로그인이면 `fetchMe`를 한 번 더 호출하고, 실패하면 `/login?redirect=<fullPath>` → `meta.roles`가 하나도 맞지 않으면 `/403`. (`{FE}/routes/index.ts`)
- 로그인 후 이동: `redirect` 쿼리 → `ADMIN_ROLES`(`{FE}/routes/adminRoutes.ts`) 보유 시 `/admin` → 그 외 `/applicant`. userType이 아니라 역할로 판정한다. (`{FE}/views/auth/LoginView.vue` — moveAfterLogin)
- `{FE}/api/client.ts`(공통 기반, `withCredentials: true`): 401이면 `/login?redirect=`로 보낸다(`skipAuthRedirect`이거나 이미 `/login`이면 제외). 403이면 `/403`으로 보낸다. `authApi.login`은 `skipSessionExpiredLog`를 붙인다(`{FE}/common/httpErrorTelemetry.ts`).
- 본인인증(가입, 실연동): `SignupView`가 연 `/nice-auth` 팝업이 `postMessage({ source:'nice-auth', status, name?, phoneNumber? }, origin)`로 결과를 알린다(생년월일·성별 없음). `event.origin`·`payload.source` 검증 후 반영. 아이디 찾기(`AccountRecovery`)도 같은 방식으로 `/nice-auth?purpose=FIND_EMAIL` 팝업을 열고, 성공 알림을 받으면 `findEmail()`을 불러 마스킹 아이디를 표시한다(실패·404는 서버 문구 알림 후 인증 전 상태). 두 경우 모두 등록한 화면은 unmount 때 **자기가 등록한 리스너·함수일 때만** 해제한다.
- 가입 화면은 loginId와 email에 같은 이메일을 보내고, 전화번호에서 `-`를 뺀다.
- 이메일 인증(가입·재발급): "메일 인증" = `send`, "인증확인" = `verify` 성공 시에만 완료. 60초 제한은 서버 문구를 그대로 보여 준다. `sign-up`·`password-reset`이 `이메일 인증이 필요합니다.`로 실패하면 인증 단계로 되돌려 다시 받게 한다(NICE 상태 유지).

## 변경 레시피

### 인증이 필요한 새 API 경로 추가
1. 가능하면 보호 접두(`/admin/**`, `/applicant/**`, `/applications/**`, `/interviewer/**`) 아래에 둔다. 그러면 매처를 추가할 필요가 없다.
2. 불가능하면 `{BE}/config/SecurityConfig.java`에 `/api`를 포함한 매처를 넓은 매처보다 위에 추가한다(`RoleNames` + `hasAuthority`, 필요하면 HTTP 메서드도 지정).
3. 컨트롤러는 `CurrentApplicantService`/`CurrentEmployeeService`로 사용자를 식별한다.
4. `{BT}/config/SecurityConfigTest.java`에 비인증 401, 다른 권한 403, 허용 권한 통과(`not(401), not(403)`) 테스트를 추가한다.
5. FE 라우트면 `meta.requiresAuth`와 `meta.roles`를 지정한다.
6. 도메인 카드의 권한 열과 이 카드의 URL 표를 갱신하고 `node tools/check-docs.mjs`를 실행한다.

### 새 역할 추가
1. `{BE}/security/auth/RoleNames.java`에 상수를 추가한다. 매핑 화면에서 부여할 역할이면 `ASSIGNABLE_ROLES`에도 넣는다([role-menu](role-menu.md)의 `/admin/role-mappings/roles`가 자동으로 반영).
2. `SecurityConfig` 매처와, 필요하면 `{FE}/routes/adminRoutes.ts`의 `ADMIN_ROLES`나 라우트 `meta.roles`를 고친다.
3. `SecurityConfigTest`를 돌리고, 이 카드의 역할 목록을 갱신한 뒤 `node tools/check-docs.mjs`를 실행한다.

### 로그인 응답 필드 추가·수정
1. `{BE}/dto/response/LoginUserResponse.java`와 `AuthController.toLoginUserResponse`를 고친다.
2. 값이 principal에 없으면 `{BE}/security/auth/CustomUserDetails.java`의 `fromUser`/`fromLdap`에서 채운다. 임직원은 `buildEmployeeAuthentication`(`fromLdap`)을 거친다는 점에 주의한다.
3. `CustomUserDetailsTest`와 `RoutingAuthenticationProviderTest`에 단정을 추가한다.
4. `{FE}/types/auth.ts`, `authStore` getter, `authStore.spec.ts`를 고친다.
5. API 표를 갱신한다. `node tools/check-docs.mjs`를 실행한다.

### 지원자 계정 API 추가 (이메일 변경, 비밀번호 재설정 등)
1. 로그인 후 기능은 `ApplicantAccountController`(`/applicant/account/**`, 매처로 자동 보호)에 둔다. 로그인 전 기능은 `ApplicantSignUpController`(`/auth/applicants/**`)에 두고 `SecurityConfig`의 permitAll 목록에 **명시적으로** 추가한다.
2. 요청 DTO는 record와 Bean Validation으로 만든다. 실패는 `InvalidApplicantAccountException`(400)으로 던진다. 민감한 변경이면 `verifyCurrentPassword`를 재사용하고, 엔티티에는 `changeXxx` 메서드를 추가한다.
3. 서비스 테스트와 `springSecurity()`를 적용한 컨트롤러 테스트(`ApplicantAccountControllerTest` 패턴, 401/403 포함)를 작성한다.
4. FE는 `{FE}/api/applicationApi.ts`에 호출을 추가하고 `AccountRecovery`/`ApplicantProfile`에서 쓴다.
5. API 표와 규칙을 갱신하고 `node tools/check-docs.mjs`를 실행한다.

## 검증

백엔드(`recruit_back/recruit_backend/`에서):

```bash
# Windows PowerShell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "*ApplicantSignUp*" --tests "*ApplicantAccount*" --tests "*EmailVerification*" --tests "*ApplicantPasswordReset*" --tests "*.service.Current*ServiceTest" --tests "com.shinyoung.recruit.security.auth.*" --tests "*.config.SecurityConfigTest" --tests "*.config.AuthenticationConfigTest" --tests "*.config.LdapPropertiesTest" --tests "*.UserRepositoryTest" --tests "*.ApplicantRepositoryTest" --tests "*.EmployeeRepositoryTest" --no-daemon
# Linux
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "*ApplicantSignUp*" --tests "*ApplicantAccount*" --tests "*EmailVerification*" --tests "*ApplicantPasswordReset*" --tests "*.service.Current*ServiceTest" --tests "com.shinyoung.recruit.security.auth.*" --tests "*.config.SecurityConfigTest" --tests "*.config.AuthenticationConfigTest" --tests "*.config.LdapPropertiesTest" --tests "*.UserRepositoryTest" --tests "*.ApplicantRepositoryTest" --tests "*.EmployeeRepositoryTest" --no-daemon
```

- 매처나 `Current*Service`(사용처: 지원자 컨트롤러 14개, 관리자·면접관 11개)를 바꾸면 영향받는 카드의 컨트롤러 테스트도 돌린다.

프론트(`recruit_front/`에서): `npm run type-check`, `npx vitest run src/stores/__tests__/authStore.spec.ts`

## 함정·결정

- **로그인 응답 `deptName` 공란 결함(2026-09-19 수정)**: 임직원 principal을 `fromUser`로 다시 감싸 부서명이 `""`로 덮이던 문제(권한은 원래 정상, 표시용 필드만 공란 → 관리자 사이드바 부서 빈 값). `buildEmployeeAuthentication`을 `fromLdap`으로 바꾸고 `RoutingAuthenticationProviderTest`에 신규·기존 임직원 부서명 단정을 추가했다. 함께 BE 응답에 없던 FE `LoginUser.phoneNumber`와 `authStore.phoneNumber` getter를 제거했다.
- **`anyRequest().permitAll()`**: `/menu`·`/board` 아래 쓰기 API가 매처 누락으로 무인증 상태였던 적이 있다(6e7f6cc, 8d7485d). 새 경로는 반드시 레시피 1을 따른다.
- **CORS 빈 이름**: `http.cors(cors -> corsConfigurationSource())`의 람다는 설정을 지정하지 않는다. 실제로는 이름이 `corsConfigurationSource`인 빈을 Spring Security가 찾아서 쓴다. 메서드 이름을 바꾸면 CORS가 조용히 빠진다.
- **Employee.deptName unique 제거**: 같은 부서의 두 번째 임직원 JIT 생성이 unique 충돌로 막히던 문제를 고쳤다. 운영 DB는 ddl-auto update라 제약이 자동으로 지워지지 않으므로 `recruit_back/recruit_backend/docs/ops/fix-employee-dept-name-unique-drop.sql`을 수동 적용한다(aa4e2a7). `users.login_id` unique는 유지한다.
- **fetchMe 판정**: 200이어도 data가 비면 미로그인이다(439fbbf. 전에는 미로그인 사용자가 `/403`으로 빠졌다). 미로그인 확정은 401일 때만 한다(8d7485d, 배포 중 5xx에 로그아웃되는 것 방지). 403 인터셉터는 `skipAuthRedirect`를 존중한다(439fbbf).
- **LDAP 미설정 기동 무검증**: `AuthenticationConfigTest`의 단정이 전부 주석이라 LDAP 미설정 빈 생성·기동을 확인하는 활성 테스트가 없다.
- **로그인 후 이동은 역할 기준**(5082861): 관리자 역할이 없는 임직원(면접관 전용 등)은 `/applicant`로 간다.
- **전역 본인인증 콜백**: 화면을 떠날 때 자기가 등록한 콜백만 해제한다(86d12c9). 무조건 지우면 다른 화면의 콜백이 사라진다.
- **목업 해제**(가입·아이디 찾기 NICE 2026-09-21·22, 이메일 인증·비밀번호 재발급 2026-09-23): 남은 목업 없음. `NiceAuthMockPopup.vue`와 `window.phoneAuthCallback` 타입은 삭제했다.
- **NICE 연동 방식·모듈 제약(`NiceID.jar`)·호출 흐름·`REQ_SEQ`/`resultToken` 대조·평문 조립 규격·`RealNiceClient` 미구현 등**은 [auth-nice-verification](auth-nice-verification.md) 함정·결정 참고. 그 카드가 이 요약을 대체한다.
- **loginId 정책 확정: 이메일 = loginId**(2026-09-20). 지원자 가입 화면은 **이미 이렇게 동작한다** — `{FE}/views/applicant/SignupView.vue`가 입력 라벨을 "이메일"로 두고 이메일 정규식으로 검증한 뒤 `loginId`·`email` 두 필드에 **같은 값**을 보낸다. `Applicant.email`·`User.loginId` 모두 unique다. 따라서 이메일 필수화·기존 데이터 이관은 할 일이 없다(오픈 전 시스템이라 기존 데이터도 없다).
  - 후속: `check-login-id`는 만들지 않는다(이메일 중복 확인으로 갈음). 이메일 변경은 불허.
  - BE가 느슨한 부분: `loginId`에 이메일 형식 검증이 없다(`email`은 이메일 인증 검사로 사실상 필수).
- **계정 열거 감수**: check-email·가입 실패 메시지로 가입 여부가 드러난다. rate limit·시도 제한 없음.
- **ADR**: `recruit_back/recruit_backend/docs/adr/0007-privacy-admin-role-separation.md`. 파기·민감 감사 권한은 `ROLE_PRIVACY_ADMIN`으로 분리한다. 매처 순서와 HTTP 메서드 구분이 보안 요구사항이다.
- **인증번호 한계**: 세션에만 있어 다른 브라우저로 이어갈 수 없고 IP 단위 시도 제한은 없다(2026-09-23 범위 제외).
