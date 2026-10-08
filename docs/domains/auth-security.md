# 웹 보안 설정 (`auth-security`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [auth-account](auth-account.md)(로그인·계정·역할 상수·401/403) · [auth-nice-verification](auth-nice-verification.md)(CORS·CSRF 예외인 NICE 콜백)

## 요약

- 모든 API에 걸리는 필터 체인 설정을 소유한다: URL 인가 표, CORS, CSRF 헤더 검사, 세션 쿠키·세션 만료(비밀번호 변경), 로그인·인증번호 시도 제한, 개발용 기능(H2 콘솔·Swagger) 기본 비활성, 업로드 xlsx 압축 해제 한도.
- 기본 정책은 **인증 필수**(`anyRequest().authenticated()`, 2026-09-27). 비로그인 공개 경로는 인가 표에 명시한 것뿐이다.
- 전 시스템 보안 점검(2026-09-27) 1~3차 조치로 만든 카드다. 기록: `docs/archive/reports/security-hardening-s1_implementation.html`, `docs/archive/reports/security-hardening-s2_implementation.html`, `docs/archive/reports/security-hardening-s3_implementation.html`.

## 용어

| 용어 | 뜻 |
|---|---|
| 보호 접두 | `/admin/**`, `/applicant/**`, `/applications/**`, `/interviewer/**`. 매처 한 줄로 역할이 걸린다 |
| CSRF 헤더 | `X-Requested-With`. 교차 사이트 폼은 붙일 수 없고, 스크립트가 붙이면 preflight가 CORS 허용 목록에서 막힌다 |
| 시도 제한 | 세션과 무관한 서버 전역 in-memory 고정 윈도우. 키는 로그인 아이디·이메일(소문자·trim) |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| config | `{BE}/config/SecurityConfig.java` | 필터 체인, URL 매처, CORS, CSRF 헤더 필터 등록, 컨텍스트 저장소 |
| config | `{BE}/config/CorsProperties.java` | `recruit.cors.allowed-origins` |
| config | `{BE}/config/CsrfHeaderFilter.java` | `/api/**` 상태 변경 요청에 `X-Requested-With` 요구, 없으면 403 |
| config | `{BE}/config/CsrfProperties.java` | `recruit.csrf.header-required` |
| config | `{BE}/config/AuthAttemptLimitProperties.java` | `recruit.auth-attempt-limit.*` |
| service | `{BE}/service/AuthAttemptLimiter.java` | 로그인 실패·인증번호 발송·오답 횟수 |
| exception | `{BE}/exception/AuthAttemptLimitExceededException.java` | 429 |
| service | `{BE}/service/UserSessionRevoker.java` | 한 계정의 로그인 세션 만료(`SessionRegistry`) |
| config | `{BE}/config/PoiSecurityConfig.java` | `ZipSecureFile` 항목 크기 상한 20MB(업로드 xlsx 압축 폭탄 방어, JVM 전역) |
| test | `{BT}/config/SecurityConfigTest.java` | 매처 401/403/통과, 기본 인증 필수, CORS |
| test | `{BT}/config/CsrfHeaderFilterTest.java` | 헤더 없는 POST 403, 조회·API 밖·NICE 콜백 제외 |
| test | `{BT}/service/AuthAttemptLimiterTest.java` | 윈도우 만료, 맵 상한 fail-closed |
| test | `{BT}/service/UserSessionRevokerTest.java` | 같은 계정의 다른 세션만 만료 |

시도 제한·세션 만료를 부르는 곳([auth-account](auth-account.md) 소유): `RoutingAuthenticationProvider`(로그인), `EmailVerificationService`(인증번호), `AuthController`(세션 등록), `ApplicantAccountService`·`ApplicantAccountRecoveryService`(비밀번호 변경·재설정).

### 프론트

프론트 axios 두 인스턴스가 CSRF 헤더를 기본으로 붙인다: `{FE}/api/client.ts`(공통 기반), `{FE}/api/telemetryClient.ts`([client-event-log](client-event-log.md) 소유). 새 axios 인스턴스·`fetch`·`sendBeacon`으로 POST하면 헤더를 직접 붙여야 한다.

## API 계약

이 카드가 소유한 엔드포인트는 없다. 모든 API에 걸리는 공통 규약만 적는다.

| 규약 | 내용 |
|---|---|
| CSRF 헤더 | `/api/**`의 GET·HEAD·OPTIONS·TRACE 외 요청은 `X-Requested-With`(값 무관)가 없으면 403. 예외: `/auth/nice/callback`, `/auth/nice/callback/error` |
| 429 | 시도 제한 초과. 본문 `ApiResponse.fail("...N분 후 다시 시도해 주세요.")`. 대상: `POST /auth/login`, 인증번호 `send`·`verify` 4종([auth-account](auth-account.md)) |

## 규칙·불변식

### URL 인가 (`SecurityConfig.filterChain`)

- 매처 경로는 `/api`를 포함해 쓴다(`{BE}/config/WebMvcConfig.java`가 접두를 붙인다). 먼저 맞는 매처가 적용되므로 **좁은 매처를 넓은 매처보다 먼저** 둔다.
- 역할은 `RoleNames` 상수와 `hasAuthority`/`hasAnyAuthority`로만 쓴다(`hasRole` 금지 — [auth-account](auth-account.md)).
- 마지막이 `anyRequest().authenticated()`다. 아래 표에 없는 경로는 **로그인한 누구나**(역할 무관) 통과한다. 역할을 좁혀야 하면 매처를 추가한다.

| 경로(`/api` 포함, 선언 순서) | 권한 |
|---|---|
| `/error`, `/actuator/health[/**]` | 공개(오류 디스패치·헬스체크) |
| `/api/auth/login`, `/api/auth/logout`, `/api/auth/applicants/`(`sign-up`·`check-email`·`find-email`·`email-verification/*`·`password-reset[/*]`) | 공개 |
| `/api/auth/nice/**` | 공개 |
| `/swagger-ui/**`, `/api-docs/**`, `/v3/api-docs/**`, `/h2-console/**`, `/api/menu/tree` | 공개(swagger·api-docs·H2 콘솔은 `SPRINGDOC_ENABLED`·`H2_CONSOLE_ENABLED`=`true`일 때만 뜬다) |
| POST `/api/menu/admin/menu`, `/api/menu/admin/menu/*`, POST `/api/board/**` | ADMIN, RECRUIT_ADMIN |
| GET `/api/job-postings/{jobPostingId}/application` | APPLICANT |
| GET `/api/job-postings/**` | 공개 |
| GET `/api/auth/me`, `/api/auth/login-options`, `/api/faqs`, `/api/board/**`, `/api/menu/**` | 공개(`/api/codes`·`/api/addresses`·`/api/schools`는 2026-09-27부터 로그인 필수 — 아래 "그 외") |
| POST `/api/client-events` | 공개 |
| GET `/api/admin/audit/**` | RECRUIT_ADMIN, PRIVACY_ADMIN |
| `/api/admin/retention/**` | 쓰기(execute·reconcile·policies·holds·anchor)와 holds 조회 → PRIVACY_ADMIN / dry-run·그 외 GET → RECRUIT_ADMIN, PRIVACY_ADMIN |
| `/api/admin/client-events/**` | POST cleanup → PRIVACY_ADMIN / GET → RECRUIT_ADMIN, PRIVACY_ADMIN |
| `/api/admin/**` | ADMIN, RECRUIT_ADMIN |
| `/api/applicant/**`, `/api/applications/**` | APPLICANT |
| `/api/interviewer/**` | EMPLOYEE, ADMIN, RECRUIT_ADMIN, INTERVIEWER |
| 그 외 | 인증 필수(역할 무관) |

### CORS·CSRF·쿠키·헤더

- CORS: 허용 origin은 `recruit.cors.allowed-origins`(`RECRUIT_CORS_ALLOWED_ORIGINS`, 쉼표 구분, 기본 운영 도메인만). 로컬·개발 서버는 환경변수로 자기 주소를 준다. 메서드는 **GET·POST만**, credentials 허용, 허용 헤더 `Content-Type`·`X-Requested-With`·`X-XSRF-TOKEN`, 노출 헤더 `X-Request-Id`·`Content-Disposition`. NICE 콜백 2종은 CORS 처리에서 빠진다(`CORS_EXEMPT_PATHS`).
- CSRF: Spring Security 토큰 CSRF는 끄고 `CsrfHeaderFilter`로 막는다(`CorsFilter` 뒤). 예외 경로는 `CORS_EXEMPT_PATHS`와 같다. 거부는 `CustomAccessDeniedHandler`의 403이라 FE는 `/403`으로 간다. `recruit.csrf.header-required=false`(`RECRUIT_CSRF_HEADER_REQUIRED`)면 필터를 등록하지 않는다 — 테스트 yaml과 로컬 Swagger POST용.
- 세션 쿠키: `SameSite=Lax`·`Secure`(`SESSION_COOKIE_SECURE`, 기본 `true`)·`HttpOnly`(`{BR}/application.yaml` — `server.servlet.session.cookie.*`). Lax라 NICE 콜백(교차 사이트)에는 쿠키가 실리지 않는다 — 콜백은 원래 세션 없이 동작하도록 설계됐다.
- `X-Frame-Options: SAMEORIGIN`. 개발용 기능은 기본 꺼짐(`H2_CONSOLE_ENABLED`, `SPRINGDOC_ENABLED`).

### 세션 만료 (비밀번호 변경)

- 로그인하면 `AuthController`가 세션을 `SessionRegistry`(`SessionRegistryImpl`)에 principal과 함께 등록한다. principal `CustomUserDetails`는 loginId로 `equals`하고, 인증이 끝나면 비밀번호 해시를 지운다.
- 비밀번호 변경은 이 계정의 다른 세션을, 로그인 전 재설정은 모든 세션을 `expireNow()`로 표시한다(`UserSessionRevoker`).
- `sessionManagement().maximumSessions(-1)`(동시 세션 무제한)이 넣는 `ConcurrentSessionFilter`가 만료 표시된 세션의 다음 요청을 로그아웃시키고 `CustomAuthenticationEntryPoint` 401을 준다. `HttpSessionEventPublisher`가 세션 소멸·ID 변경을 레지스트리에 알린다.
- 레지스트리에 없는 세션(배포 전 로그인)은 대상이 아니다. in-memory·단일 인스턴스 전제다.

### 시도 제한 (`AuthAttemptLimiter`)

| 대상 | 키 | 한도(기본) | 동작 |
|---|---|---|---|
| 로그인 실패 | loginId | 첫 실패부터 15분 안에 5회 | 한도에 닿으면 LDAP·DB 인증을 시도하지 않고 429. 성공하면 초기화. LDAP 장애(`InternalAuthenticationServiceException`)는 세지 않는다 |
| 인증번호 발송 | email | 60분 안에 5회 | 6번째 발송부터 429(가입·재발급 합산, 세션 무관) |
| 인증번호 오답 | email | 60분 안에 10회 | 한도에 닿으면 맞는 번호도 비교하지 않고 429. 세션별 5회 무효 규칙은 그대로 |

- 키는 trim·소문자. 없는 아이디도 똑같이 세서 계정 존재 여부를 드러내지 않는다.
- 맵 상한 100,000. 만료 엔트리를 정리해도 가득이면 새 키를 429로 거부한다(fail-closed, `ClientEventRateLimiter`와 같은 방식).
- 한도는 `recruit.auth-attempt-limit.*`(`RECRUIT_LOGIN_MAX_FAILURES` 등). 테스트 yaml은 크게 둔다(한 컨텍스트에서 같은 계정을 여러 테스트가 쓴다).

## 변경 레시피

### 새 API 경로의 권한
1. 가능하면 보호 접두 아래에 둔다. 매처를 추가할 필요가 없다.
2. 로그인만 필요하면(역할 무관) 아무것도 하지 않아도 된다(`anyRequest().authenticated()`).
3. 비로그인 공개면 인가 표의 공개 매처에 **메서드까지** 명시하고, 역할을 좁히면 전용 매처를 넓은 매처보다 위에 둔다.
4. `{BT}/config/SecurityConfigTest.java`에 비인증 401, 다른 권한 403, 허용 권한 통과(`not(401), not(403)`) 테스트를 추가하고 이 카드의 인가 표를 갱신한다.

### 외부 사이트가 부르는 새 콜백
CORS·CSRF 모두 막히므로 `SecurityConfig.CORS_EXEMPT_PATHS`에 넣고 permitAll 매처를 둔다. 안전성은 Origin이 아니라 서명·대조값으로 확보한다(NICE는 `REQ_SEQ`).

## 검증

```bash
# Windows PowerShell (recruit_back/recruit_backend)
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.config.*" --tests "*AuthAttemptLimiter*" --no-daemon
```

## 함정·결정

- **IP 기준 제한 없음**: `server.forward-headers-strategy`가 없어 프록시 뒤에서는 모든 요청 IP가 프록시 주소다. IP 한도를 두면 전체 한도가 된다. 프록시 헤더 신뢰를 설정한 뒤 검토한다.
- **단일 인스턴스 전제**: 시도 제한은 in-memory라 인스턴스마다 따로 세고 재기동하면 초기화된다.
- **아이디 단위 잠금**: 공격자가 남의 아이디로 5번 틀려 15분 동안 로그인을 막을 수 있다. AD 계정 대입·AD 잠금 유발을 막는 쪽을 택했다.
- **MockMvc 테스트는 CSRF 헤더를 붙이지 않는다**: 테스트 yaml이 필터를 끈다. 필터 동작은 `CsrfHeaderFilterTest`가 본다.
- **`anyRequest().permitAll()` 이력**: `/menu`·`/board` 쓰기 API가 매처 누락으로 무인증이었던 적이 있다(6e7f6cc, 8d7485d). 2026-09-27에 기본을 인증 필수로 바꿨다.
- **CORS 빈 이름**: `http.cors(cors -> corsConfigurationSource())`의 람다는 설정을 지정하지 않는다. 이름이 `corsConfigurationSource`인 빈을 Spring Security가 찾아 쓴다. 메서드 이름을 바꾸면 CORS가 조용히 빠진다.
- **프록시 뒤 CORS**: 같은 출처 요청도 CORS 판정을 받아, 접속 주소가 허용 목록에 없으면 POST가 전부 403이다 — [auth-nice-verification](auth-nice-verification.md) 함정 참고.
- **운영 설정 권고(코드 밖)**: 프록시가 `X-Forwarded-*`를 넘기면 `SERVER_FORWARD_HEADERS_STRATEGY=native`로 실제 IP·https를 인식시킨다(감사 IP·HSTS·쿠키 판단이 맞아지고, 그 뒤 IP 기준 제한을 검토한다). 신뢰 프록시 범위는 Tomcat 기본(사설 대역)이다. LDAP은 `ldaps://`를 쓴다. 프론트 정적 서버는 CSP·`Referrer-Policy` 헤더를 준다. 운영 DB는 `SPRING_JPA_DDL_AUTO=validate`를 검토한다.
