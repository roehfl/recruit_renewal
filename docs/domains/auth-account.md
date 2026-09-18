# 인증·지원자 계정 (`auth-account`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [role-menu](role-menu.md)(역할 매핑·메뉴) · [application](application.md)(내 지원 현황) · [application-sections](application-sections.md)(기본정보 프리필) · [stage-result](stage-result.md)(마이페이지 전형결과) · [privacy-audit](privacy-audit.md)(PII 파기) · [interview](interview.md)(면접관 식별)

## 요약

- 서버 **세션** 인증(Spring Security + `HttpSession`, `JSESSIONID` 쿠키). 토큰·JWT 없음.
- 로그인 API는 `POST /auth/login` 하나다. `RoutingAuthenticationProvider`가 사용자 유형에 따라 경로를 나눈다. 지원자는 DB 로컬 계정(BCrypt)으로, 임직원은 AD(LDAP) bind로 인증한다. 처음 로그인한 임직원은 `Employee` 행이 자동 생성된다(JIT).
- 지원자 계정 기능: 가입, 이메일 가용성 확인, 비밀번호 변경, 전화번호 변경. 아이디 찾기, 비밀번호 재발급, 이메일 인증, NICE 본인인증은 **프론트 목업**이다(백엔드 없음).
- URL 인가(`SecurityConfig`), 401/403 규약, 역할 상수(`RoleNames`), 현재 사용자 식별(`CurrentApplicantService`·`CurrentEmployeeService`)도 이 카드가 소유한다. 다른 카드가 공용으로 쓴다.

## 용어

| 용어 | 뜻 |
|---|---|
| 지원자 `Applicant` | 로컬 계정(BCrypt). `userType="Applicant"`, 권한 `ROLE_APPLICANT` 고정 |
| 임직원 `Employee` | AD 계정. 비밀번호를 저장하지 않는다. `userType="Employee"`, 권한은 역할 매핑에서 계산 |
| `User` | 두 유형의 부모 엔티티. `users` 테이블 JOINED 상속, `loginId` 전역 unique |
| JIT 생성 | DB에 없는 loginId가 LDAP 인증에 성공하면 즉시 `Employee` 행을 저장하는 것 |
| CI / `ciHash` | 본인인증 연계정보. `ci`는 AES 컬럼 암호화(`{BE}/common/crypto/AesAttributeConverter.java`), `ciHash`=SHA-256(`{BE}/common/hash/HashUtil.java`)로 중복 가입 차단 |
| principal | 세션에 저장되는 `CustomUserDetails`(loginId, name, deptName, userType, authorities) |
| authority(역할) | `ROLE_` 접두까지 포함한 완전한 문자열. 단일 출처는 `RoleNames` |
| 부서·개인 매핑 | `dept_role_mapping`(AD 그룹 cn 부분일치) / `user_role_mapping`(loginId 완전일치). [role-menu](role-menu.md) 소유 |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/AuthController.java` | 로그인·로그아웃·`/auth/me` |
| controller | `{BE}/controller/ApplicantSignUpController.java` | 가입, 이메일 가용성 |
| controller | `{BE}/controller/ApplicantAccountController.java` | 비밀번호·전화번호 변경 |
| service | `{BE}/service/ApplicantSignUpService.java` | 가입 검증·저장 |
| service | `{BE}/service/ApplicantAccountService.java` | 현재 비밀번호 재확인 후 변경 |
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
| entity | `{BE}/domain/entity/Applicant.java` | email(unique)·password·phoneNumber·ci·ciHash(unique) |
| entity | `{BE}/domain/entity/Employee.java` | `deptName`(unique 아님) |
| repository | `{BE}/domain/repository/UserRepository.java` | `findUserByLoginId`, `existsByLoginId` |
| repository | `{BE}/domain/repository/ApplicantRepository.java` | `findByLoginId`, `existsByEmail`, `existsByCiHash` |
| repository | `{BE}/domain/repository/EmployeeRepository.java` | `findByLoginId(In)` |
| dto | `{BE}/dto/request/LoginRequest.java` | 로그인 요청 |
| dto | `{BE}/dto/response/LoginUserResponse.java` | 로그인 사용자 응답 |
| dto | `{BE}/dto/request/ApplicantSignUpRequest.java` | 가입 요청 |
| dto | `{BE}/dto/response/ApplicantSignUpResponse.java` | 가입 응답 |
| dto | `{BE}/dto/response/ApplicantEmailAvailabilityResponse.java` | `available` |
| dto | `{BE}/dto/request/ApplicantPasswordChangeRequest.java` | 비밀번호 변경 요청 |
| dto | `{BE}/dto/request/ApplicantPhoneNumberChangeRequest.java` | 전화번호 변경 요청 |
| exception | `{BE}/exception/InvalidApplicantSignUpException.java` | 400 |
| exception | `{BE}/exception/InvalidApplicantAccountException.java` | 400 |
| exception | `{BE}/exception/AuthenticationRequiredException.java` | 401 |
| exception | `{BE}/exception/AccessForbiddenException.java` | 403 |
| test | `{BT}/controller/ApplicantSignUpControllerTest.java` | 가입·check-email |
| test | `{BT}/controller/ApplicantAccountControllerTest.java` | `springSecurity()`: 401/403/400 |
| test | `{BT}/service/ApplicantSignUpServiceTest.java` | 중복·인코딩·민감정보 미노출 |
| test | `{BT}/service/ApplicantAccountServiceTest.java` | 비밀번호 불일치·동일값 거부 |
| test | `{BT}/service/CurrentApplicantServiceTest.java` | 401/403 예외 |
| test | `{BT}/service/CurrentEmployeeServiceTest.java` | 401/403·blank actor |
| test | `{BT}/security/auth/RoutingAuthenticationProviderTest.java` | JIT·경합 복구 |
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
| route | `{FE}/routes/authRoutes.ts` | `Login` `/login`, `NiceAuthPopup` `/nice-auth` (`public`) |
| route | `{FE}/routes/applicantRoutes.ts` | (공유) `Signup`·`accountRecovery`(`public`), `ApplicantProfile`(`requiresAuth`+`ROLE_APPLICANT`) |
| view | `{FE}/views/auth/LoginView.vue` | 로그인, 로그인 후 이동 |
| view | `{FE}/views/auth/pop-up/NiceAuthPopup.vue` | NICE 팝업(목업: CI=`crypto.randomUUID()`) |
| view | `{FE}/views/applicant/SignupView.vue` | 가입(이메일=loginId, 이메일 인증 목업) |
| view | `{FE}/views/applicant/AccountRecovery.vue` | 아이디 찾기·비밀번호 재발급(전체 목업) |
| view | `{FE}/views/applicant/ApplicantProfile.vue` | 마이페이지: 비밀번호 변경, 로그아웃, 내 지원 목록, 전형결과 모달 |
| api | `{FE}/api/authApi.ts` | `login`, `me`, `logout` |
| api | `{FE}/api/applicationApi.ts` | (공유) `signup`, `checkEmail`, `changePassword` |
| types | `{FE}/types/auth.ts` | `LoginRequest`, `LoginUser` |
| types | `{FE}/types/application.ts` | (공유) `SignupUser`, `checkEmailRequest`, `ChangePasswordParams`, `ChangePasswordRequest` |
| types | `{FE}/types/window.ts` | `window.phoneAuthCallback` |
| store | `{FE}/stores/authStore.ts` | `user`·`initialized`, `login`/`fetchMe`/`logout` |
| test | `{FE}/stores/__tests__/authStore.spec.ts` | `fetchMe` 판정 |

## API 계약

| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |
|---|---|---|---|---|---|
| 🔴 | POST | /auth/login | `{ loginId, password }` | `{ loginId, name, deptName, userType, roles[] }` + 세션 쿠키 | 공개 |
| 🟢 | POST | /auth/logout | 없음 | `Void` | 공개 |
| 🔴 | GET | /auth/me | 없음 | login과 동일 / 미로그인 401 | 공개(컨트롤러가 401) |
| 🟢 | POST | /auth/applicants/sign-up | `{ loginId, password, name, phoneNumber, email?, ci }` | `{ applicantId, loginId, name }` | 공개 |
| 🟢 | GET | /auth/applicants/check-email | `?email=` | `{ available }` | 공개 |
| 🟢 | POST | /applicant/account/password | `{ currentPassword, newPassword }` | `Void` | 지원자 |
| 🟢 | POST | /applicant/account/phone-number | `{ currentPassword, phoneNumber }` | `Void` | 지원자 |

- 응답은 모두 `ApiResponse<T>`(`{ success, data, message }`)로 감싼다. 이 표는 코드 기준으로 작성했다(이전 계약 문서에 섹션 없음). 🔴는 FE와 BE 코드가 서로 맞지 않는 항목이다.
- ApplicantProfile이 호출하는 다른 카드 API: `GET /applications/me`(내 지원 목록, pageSize 5) → [application](application.md), `GET /applications/{applicationId}/stage-results` → [stage-result](stage-result.md).

### 엔드포인트 상세

**POST /auth/login** 🔴
- 성공 흐름: 새 SecurityContext 생성 → `request.getSession(true)` → `request.changeSessionId()` → `securityContextRepository.saveContext()`.
- 실패(비밀번호 불일치, 계정 없음, LDAP 접속 실패): `AuthenticationException`이 필터 체인까지 올라가 `CustomAuthenticationEntryPoint`가 **401** `"Authentication is required."`를 반환한다. `GlobalExceptionHandler`에는 이 예외 핸들러가 없다. 전용 테스트는 없다. 프론트는 서버 메시지를 쓰지 않고 `'아이디 또는 비밀번호를 확인하세요.'`를 고정으로 보여준다. 빈 입력은 400.
- 🔴 FE-BE 코드 간 불일치 결함 — 코드 수정 필요(후속 슬라이스): ① `phoneNumber` — `{FE}/types/auth.ts`의 `LoginUser.phoneNumber`가 BE `LoginUserResponse`에 없다. 그래서 `authStore.phoneNumber`는 항상 `''`이고, 지원서 기본정보의 휴대폰 프리필(`{FE}/views/applicant/application/sections/BasicInfoSection.vue`)이 항상 빈 값이다. ② `deptName` — `CustomUserDetails.fromUser()`에서 항상 빈 문자열로 고정된다(`deptName=""`). 임직원의 최종 principal도 이 메서드로 만들기 때문이다(`RoutingAuthenticationProvider.buildEmployeeAuthentication`). 관리자 사이드바(`{FE}/layouts/AdminSidebar.vue`)의 부서 표시가 항상 빈 값이다. 이를 잡는 테스트가 없다.

**POST /auth/logout**: SecurityContext를 비우고, 세션이 있으면 `invalidate()`한다. 호출 위치: `ApplicantProfile`, `{FE}/layouts/ApplicantHeader.vue`, `{FE}/layouts/AdminSidebar.vue`.

**GET /auth/me** 🔴: 미인증이면 컨트롤러가 직접 401 `"로그인이 필요합니다."`를 반환한다(`anyRequest().permitAll()`로 통과). 응답은 login과 같고 위 🔴 두 항목이 그대로 해당한다. `roles`에는 authority 문자열이 담긴다. FE는 `skipAuthRedirect`·`skipClientEventLog`를 붙여 호출한다.

**POST /auth/applicants/sign-up**
- 검증: loginId ≤100, password 8~100, name ≤100, phoneNumber ≤30(모두 `@NotBlank`), email `@Email` ≤255(**선택**), ci `@NotBlank` ≤255.
- 400 메시지는 검사 순서대로 `"이미 사용 중인 아이디입니다."` → `"이미 사용 중인 이메일입니다."` → `"이미 가입된 본인인증 정보입니다."`다. 동시 가입 경합으로 DB unique에 걸리면 409 `"이미 처리되었거나 중복된 데이터입니다."`.
- 가입 후 자동 로그인하지 않는다(FE는 `/applicant`로 이동). FE 응답 타입(`SignupUser`)은 틀렸지만 응답을 쓰지 않는다.

**GET /auth/applicants/check-email**: `@NotBlank @Email @Size(max=255)` 위반은 400. trim한 뒤 `available = !existsByEmail`이다. 참고용(advisory)이고, 최종 판정은 가입 시 재검증과 DB unique가 한다. `SignupView`는 available=true를 "가입 가능"으로 읽고, `AccountRecovery`는 거꾸로 available=false를 "가입된 메일"로 읽는다. FE 응답 타입(`checkEmailRequest`)은 래퍼가 이중이라 `as unknown as` 캐스팅으로 우회한다.

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
- 기존 `Employee`의 name·deptName은 다시 로그인해도 갱신하지 않는다(JIT 시점 값 유지).
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
| `/api/auth/login`, `/api/auth/logout`, `/api/auth/applicants/sign-up`, `/api/auth/applicants/check-email` | 공개 |
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
- loginId 중복은 **users 전체**(`existsByLoginId`)에서 검사한다. 지원자 범위만 보면 임직원 loginId와 겹쳐 양쪽 다 로그인이 막힌다. 최종 방어선은 `User.loginId` unique다. (`{BE}/service/ApplicantSignUpService.java` — signUp)
- 저장 전 처리: loginId·name·phoneNumber·ci는 trim, email은 trim 후 빈 값이면 null(소문자화 안 함). 비밀번호는 BCrypt로 저장하고, name은 `userName`에도 복사한다.
- ci·ciHash·password는 응답·로그·export에 넣지 않는다. (`{BT}/service/ApplicantSignUpServiceTest.java` — 응답에_민감정보가_없다)
- ci는 클라이언트가 보낸 값을 그대로 쓰는 임시 방식이다(`ApplicantSignUpRequest` javadoc). FE NICE 목업이 매번 임의 UUID를 만들기 때문에 ciHash 중복 차단이 사실상 동작하지 않는다.
- 파기: `Applicant.purgePersonalData`가 PII를 null로 만들고 ciHash를 `PURGED:`+UUID로 덮어쓴다. 이후 그 계정은 로그인할 수 없고 같은 CI로 재가입할 수 있다. 호출은 [privacy-audit](privacy-audit.md). (`{BE}/domain/entity/Applicant.java`)
- 비밀번호·전화번호 변경에는 `currentPassword` 재확인이 필수다(세션 탈취만으로 통지 채널을 바꾸지 못하게). 변경은 setter 대신 `Applicant.changePassword`/`changePhoneNumber`로 한다. (`{BE}/service/ApplicantAccountService.java` — verifyCurrentPassword)
- 이메일 변경, 아이디 찾기, 로그인 전 비밀번호 재설정 API는 없다(loginId 정책 미결정).

### 프론트
- `authStore.fetchMe`: `success && data`일 때만 로그인으로 복구하고 나머지는 `user=null`로 둔다. **401일 때만** `initialized=true`로 확정하고, 네트워크 오류·5xx는 다음 이동 때 다시 확인한다. (`{FE}/stores/authStore.ts`)
- 전역 가드 순서: 미초기화면 `fetchMe` → `meta.public`이면 통과 → `requiresAuth`인데 미로그인이면 `fetchMe`를 한 번 더 호출하고, 실패하면 `/login?redirect=<fullPath>` → `meta.roles`가 하나도 맞지 않으면 `/403`. (`{FE}/routes/index.ts`)
- 로그인 후 이동: `redirect` 쿼리 → `ADMIN_ROLES`(`{FE}/routes/adminRoutes.ts`) 보유 시 `/admin` → 그 외 `/applicant`. userType이 아니라 역할로 판정한다. (`{FE}/views/auth/LoginView.vue` — moveAfterLogin)
- `{FE}/api/client.ts`(공통 기반, `withCredentials: true`): 401이면 `/login?redirect=`로 보낸다(`skipAuthRedirect`이거나 이미 `/login`이면 제외). 403이면 `/403`으로 보낸다. `authApi.login`은 `skipSessionExpiredLog`를 붙인다(`{FE}/common/httpErrorTelemetry.ts`).
- 본인인증: 화면이 `window.open('/nice-auth')`으로 팝업을 열면 팝업이 `window.opener.phoneAuthCallback({ name, phoneNumber, ci })`를 호출한다. 등록한 화면은 unmount 때 **자기가 등록한 함수일 때만** 해제한다.
- 가입 화면은 loginId와 email에 같은 이메일을 보내고, 전화번호에서 `-`를 뺀다.

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

### 로그인 응답 필드 추가·수정 (🔴 phoneNumber·deptName 해소 포함)
1. `{BE}/dto/response/LoginUserResponse.java`와 `AuthController.toLoginUserResponse`를 고친다.
2. 값이 principal에 없으면 `{BE}/security/auth/CustomUserDetails.java`의 `fromUser`/`fromLdap`에서 채운다. 임직원은 `buildEmployeeAuthentication`을 거친다는 점에 주의한다.
3. `CustomUserDetailsTest`와 `RoutingAuthenticationProviderTest`에 단정을 추가한다.
4. `{FE}/types/auth.ts`, `authStore` getter, `authStore.spec.ts`를 고친다.
5. API 표를 갱신한다(🔴→🟢은 사용자 확인 후). `node tools/check-docs.mjs`를 실행한다.

### 지원자 계정 API 추가 (이메일 변경, 비밀번호 재설정 등)
1. 로그인 후 기능은 `ApplicantAccountController`(`/applicant/account/**`, 매처로 자동 보호)에 둔다. 로그인 전 기능은 `ApplicantSignUpController`(`/auth/applicants/**`)에 두고 `SecurityConfig`의 permitAll 목록에 **명시적으로** 추가한다.
2. 요청 DTO는 record와 Bean Validation으로 만든다. 실패는 `InvalidApplicantAccountException`(400)으로 던진다. 민감한 변경이면 `verifyCurrentPassword`를 재사용하고, 엔티티에는 `changeXxx` 메서드를 추가한다.
3. 서비스 테스트와 `springSecurity()`를 적용한 컨트롤러 테스트(`ApplicantAccountControllerTest` 패턴, 401/403 포함)를 작성한다.
4. FE는 `{FE}/api/applicationApi.ts`와 `AccountRecovery`/`ApplicantProfile`의 목업을 실제 호출로 바꾼다.
5. API 표와 규칙을 갱신하고 `node tools/check-docs.mjs`를 실행한다.

## 검증

백엔드(`recruit_back/recruit_backend/`에서):

```bash
# Windows PowerShell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "*ApplicantSignUp*" --tests "*ApplicantAccount*" --tests "*.service.Current*ServiceTest" --tests "com.shinyoung.recruit.security.auth.*" --tests "*.config.SecurityConfigTest" --tests "*.config.AuthenticationConfigTest" --tests "*.config.LdapPropertiesTest" --tests "*.UserRepositoryTest" --tests "*.ApplicantRepositoryTest" --tests "*.EmployeeRepositoryTest" --no-daemon
# Linux
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "*ApplicantSignUp*" --tests "*ApplicantAccount*" --tests "*.service.Current*ServiceTest" --tests "com.shinyoung.recruit.security.auth.*" --tests "*.config.SecurityConfigTest" --tests "*.config.AuthenticationConfigTest" --tests "*.config.LdapPropertiesTest" --tests "*.UserRepositoryTest" --tests "*.ApplicantRepositoryTest" --tests "*.EmployeeRepositoryTest" --no-daemon
```

- 매처나 `Current*Service`(사용처: 지원자 컨트롤러 14개, 관리자·면접관 11개)를 바꾸면 영향받는 카드의 컨트롤러 테스트도 돌린다.

프론트(`recruit_front/`에서): `npm run type-check`, `npx vitest run src/stores/__tests__/authStore.spec.ts`

## 함정·결정

- **로그인 응답 `phoneNumber`·`deptName`(🔴 확정 사유)**: FE-BE 코드 간 불일치 결함 — 코드 수정 필요(후속 슬라이스). ① FE `LoginUser.phoneNumber`가 BE `LoginUserResponse`에 없음 → 지원서 기본정보 휴대폰 프리필 항상 빈 값. ② `deptName`이 `CustomUserDetails.fromUser()`에서 항상 빈 문자열 → 관리자 사이드바 부서 표시 빈 값. 상세는 "엔드포인트 상세" `POST /auth/login`.
- **`anyRequest().permitAll()`**: `/menu`·`/board` 아래 쓰기 API가 매처 누락으로 무인증 상태였던 적이 있다(6e7f6cc, 8d7485d). 새 경로는 반드시 레시피 1을 따른다.
- **CORS 빈 이름**: `http.cors(cors -> corsConfigurationSource())`의 람다는 설정을 지정하지 않는다. 실제로는 이름이 `corsConfigurationSource`인 빈을 Spring Security가 찾아서 쓴다. 메서드 이름을 바꾸면 CORS가 조용히 빠진다.
- **Employee.deptName unique 제거**: 같은 부서의 두 번째 임직원 JIT 생성이 unique 충돌로 막히던 문제를 고쳤다. 운영 DB는 ddl-auto update라 제약이 자동으로 지워지지 않으므로 `recruit_back/recruit_backend/docs/ops/fix-employee-dept-name-unique-drop.sql`을 수동 적용한다(aa4e2a7). `users.login_id` unique는 유지한다.
- **fetchMe 판정**: 200이어도 data가 비면 미로그인이다(439fbbf. 전에는 미로그인 사용자가 `/403`으로 빠졌다). 미로그인 확정은 401일 때만 한다(8d7485d, 배포 중 5xx에 로그아웃되는 것 방지). 403 인터셉터는 `skipAuthRedirect`를 존중한다(439fbbf).
- **LDAP 미설정 기동 무검증**: `AuthenticationConfigTest`의 단정이 전부 주석이라 LDAP 미설정 빈 생성·기동을 확인하는 활성 테스트가 없다.
- **로그인 후 이동은 역할 기준**(5082861): 관리자 역할이 없는 임직원(면접관 전용 등)은 `/applicant`로 간다.
- **전역 본인인증 콜백**: 화면을 떠날 때 자기가 등록한 콜백만 해제한다(86d12c9). 무조건 지우면 다른 화면의 콜백이 사라진다.
- **목업 보류**(8d7485d 결정): 이메일 인증, NICE(CI 임의 UUID, 검사 `!name && !phoneNumber`는 둘 다 비어야 걸림), 아이디 찾기(결과 `abc12345@gmail.com` 하드코딩), 비밀번호 재발급. 연동 시 함께 교체.
- **loginId 정책 미결정**(이메일=loginId 안 vs 별도 ID 안): BE는 두 안 모두 허용(email 선택). check-login-id, 이메일 변경, email 필수화, 아이디 찾기는 결정 후 진행.
- **계정 열거 감수**: check-email·가입 실패 메시지로 가입 여부가 드러난다. rate limit·시도 제한 없음.
- **ADR**: `recruit_back/recruit_backend/docs/adr/0007-privacy-admin-role-separation.md`. 파기·민감 감사 권한은 `ROLE_PRIVACY_ADMIN`으로 분리한다. 매처 순서와 HTTP 메서드 구분이 보안 요구사항이다.
