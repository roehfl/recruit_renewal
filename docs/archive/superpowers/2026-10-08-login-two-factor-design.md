# 지원자 로그인 2차 인증(NICE) 설계

> 상태: 설계 초안(2026-10-08). 구현 전. 카드: [auth-account](../../domains/auth-account.md)(로그인) · [auth-nice-verification](../../domains/auth-nice-verification.md)(NICE) · [auth-security](../../domains/auth-security.md)

## 1. 목표·비목표

- 목표: 지원자 로그인에 NICE 본인확인을 2차 인증으로 붙인다. 로그인 버튼을 누르면 NICE 팝업이 뜨고, 인증이 끝난 경우에만 로그인 요청이 서버로 넘어간다. **서버가 NICE 결과와 계정 명의를 직접 대조한다**(프론트만으로는 `POST /auth/login` 직접 호출로 우회된다).
- 설정으로 켜고 끈다(`recruit.nice.login-two-factor-enabled`).
- 비목표: 임직원(LDAP) 2차 인증, 비밀번호 재설정 NICE(별도 미착수), 로그인 이력 테이블, 기존 채용 시스템.

## 2. 결정 (가정 — 확인 필요 표시)

| # | 결정 | 근거·대안 |
|---|---|---|
| D1 | **대조 기준은 이름+생년월일+성별 해시(`Applicant.ciHash`)** 이고 휴대폰 번호는 쓰지 않는다 | 가입 중복 판정·휴대폰 변경(`PHONE_CHANGE`)과 같은 기준이다. 휴대폰은 번호를 바꾸면 달라지고 재할당 위험이 있다. `identityHash`를 재사용한다. **확인 필요**: 번호 대조를 원하면 D1 대안으로 바꾼다 |
| D2 | **지원자만 적용, 임직원(LDAP)은 제외** | 임직원은 대조할 명의 값이 DB에 없다. AD 인증이 이미 있다. **확인 필요** |
| D3 | **NICE 먼저, 로그인 제출은 그 뒤**(요청하신 순서) | 대안(비밀번호 먼저 확인 후 NICE)은 NICE 건당 과금 낭비가 없으나 로그인 API가 2단계가 된다. 채택하지 않음 |
| D4 | **비밀번호 틀림은 NICE 결과를 소비하지 않는다.** 로그인 성공·2차 인증 실패(명의 불일치·만료) 때만 소비한다. 유효시간 **5분** | 비밀번호 오타마다 NICE를 다시 하면 과금·불편이 크다. 공격자는 자기 명의 NICE로는 명의 대조에서 막히고, 비밀번호 시도는 기존 아이디별 한도(5회/15분)가 막는다 |
| D5 | 2차 인증 실패는 **비밀번호가 맞은 뒤에만** 사유를 구분해 안내한다 | 비밀번호를 모르는 쪽에는 정보가 안 된다. 비밀번호가 틀리면 지금처럼 401 고정 문구 |
| D6 | 설정 기본값 **`true`**(켜짐). 끄면 NICE 없이 로그인 | 보안 통제라 fail-closed. 로컬은 NICE를 실제로 못 쓰므로 `RECRUIT_LOGIN_TWO_FACTOR_ENABLED=false` |

## 3. 흐름

```
[LoginView] 로그인 클릭
  ├ 아이디·비밀번호 비어 있으면 중단
  ├ 2FA 꺼짐 또는 임직원 계정(@ 없음) → 바로 POST /auth/login
  └ 2FA 켜짐 + 지원자(@ 포함)
       → window.open('/nice-auth?purpose=LOGIN')            ← 클릭 핸들러 안에서 동기 호출(팝업 차단 방지)
       → [팝업] POST /auth/nice/request(purpose=LOGIN) → NICE 표준창 → 콜백 → /nice-auth/result
              POST /auth/nice/result → 세션 NICE_VERIFIED = {purpose=LOGIN, name, birth, gender, verifiedAt}
              postMessage(SUCCESS) → 팝업 닫힘
       → [LoginView] SUCCESS 수신 → POST /auth/login
[AuthController.login]
  ├ 세션의 NICE_VERIFIED가 purpose=LOGIN이면 (꺼내되 아직 지우지 않고) 토큰 details에 넣는다
  ├ authenticationManager.authenticate(token)
  │    └ RoutingAuthenticationProvider
  │         ├ 시도 제한 확인(429) → 비밀번호 검증(기존)
  │         └ Applicant이고 2FA 켜짐 → LoginSecondFactorVerifier.verify(details, applicant)
  │               ① details 없음 → 실패 NICE_REQUIRED ② 5분 초과 → EXPIRED
  │               ③ identityHash(name,birth,gender) ≠ applicant.ciHash → MISMATCH
  └ 성공 또는 LoginSecondFactorException이면 세션에서 NICE_VERIFIED 제거 / 비밀번호 실패면 유지(D4)
```

NICE 결과는 서버 세션에만 있고 브라우저로 내려가지 않는다(기존 규칙). `changeSessionId`는 속성을 새 세션으로 옮기므로 인증 전 세션에 담긴 값을 Provider가 읽는 데 문제없다.

## 4. 백엔드

| 파일 | 변경 |
|---|---|
| `{BE}/enumeration/NiceVerificationPurpose.java` | `LOGIN` 추가 |
| `{BE}/config/NiceProperties.java` | `loginTwoFactorEnabled`(기본 `true`) |
| `{BR}/application.yaml` · `{BT}` 용 `src/test/resources/application.yaml` | `recruit.nice.login-two-factor-enabled: ${RECRUIT_LOGIN_TWO_FACTOR_ENABLED:true}` (테스트 yaml은 기존 로그인 테스트 보호를 위해 `false`) |
| `{BE}/security/auth/LoginSecondFactorVerifier.java` (신규 `@Component`) | `verify(Object details, Applicant)` — 위 ①~③. `Clock`·`AuditHmac` 주입. 유효시간 5분은 상수 |
| `{BE}/exception/LoginSecondFactorException.java` (신규) | `BadCredentialsException` 상속(공통 부모가 `AuthenticationException`이라 Provider의 실패 횟수 집계 대상). 사유 코드 보유 |
| `{BE}/exception/GlobalExceptionHandler.java` | `LoginSecondFactorException` → **400** `ApiResponse.fail(사유 문구)`. 문구: `본인인증을 먼저 진행해주세요.` / `본인인증이 만료되었습니다. 다시 진행해주세요.` / `본인인증 명의가 가입자 정보와 일치하지 않습니다.`(휴대폰 변경과 같은 문구) |
| `{BE}/security/auth/RoutingAuthenticationProvider.java` | Applicant 분기: `daoProvider.authenticate` 성공 직후, 설정이 켜져 있으면 `verifier.verify(authentication.getDetails(), applicant)` |
| `{BE}/controller/AuthController.java` | `login`: ① 토큰에 `setDetails(NiceVerifiedIdentity)`(purpose=LOGIN일 때만) ② 성공·`LoginSecondFactorException` 시 `session.removeAttribute("NICE_VERIFIED")`(비밀번호 실패는 유지) |
| `{BE}/controller/AuthController.java` | 신규 `GET /auth/login-options` → `{ twoFactorEnabled }`(공개) |
| `{BE}/config/SecurityConfig.java` | `GET /api/auth/login-options` 를 공개 GET 매처에 추가 + `SecurityConfigTest`에 허용 테스트 |

- 임직원 경로는 건드리지 않는다. `details`는 Applicant 분기에서만 읽는다.
- 로그에는 사유 코드(`NICE_REQUIRED`·`EXPIRED`·`MISMATCH`)만 `warn`으로 남기고 이름·생년월일은 남기지 않는다. 별도 이력 테이블은 만들지 않는다(비목표).
- `NiceVerificationException`(요청·콜백 파이프라인용)은 쓰지 않는다. 로그인 경로는 인증 예외 계열로 통일해야 시도 제한이 센다.

## 5. 프론트

| 파일 | 변경 |
|---|---|
| `{FE}/types/auth/nice.ts` | `NICE_PURPOSES`에 `'LOGIN'` 추가 |
| `{FE}/api/authApi.ts` | `loginOptions()` 추가 |
| `{FE}/views/auth/LoginView.vue` | 마운트 시 `loginOptions()` 조회(실패하면 켜짐으로 간주 — 서버가 어차피 강제한다). `handleLogin`: 입력 검사 → 2FA 필요 여부 판단 → 팝업 또는 바로 로그인. `message` 리스너 등록·해제(`SignupView`와 같은 origin·source 검사, 자기가 등록한 리스너만 해제) |

- 2FA 필요 판단: `twoFactorEnabled && loginId.includes('@')`. 지원자 `loginId`는 가입 시 이메일과 같아야 하고(`ApplicantSignUpService`), 임직원은 AD 계정명이라 `@`가 없다. 이 판단은 **화면 편의일 뿐**이고 서버가 지원자에게 항상 강제한다. 판단이 빗나가 지원자가 NICE 없이 시도하면 400 `본인인증을 먼저 진행해주세요.`가 보이고, 다시 누르면 `@` 규칙으로 팝업이 뜬다.
- 팝업 대기 중에는 로딩 표시를 켜지 않는다(창을 그냥 닫으면 화면이 멈추지 않게). 팝업 창 이름은 기존과 같은 `Nice-Auth`라 재클릭 시 같은 창을 쓴다.
- 오류 표시: 429(서버 문구), **400(서버 문구)**, 그 외(401 포함)는 기존 고정 문구. 400을 추가한 이유는 2차 인증 실패 사유를 보이기 위해서다.
- 로그인 후 이동·`authStore.login`은 그대로다.

## 6. API 계약 (카드 `## API 계약`에 🟡로 먼저 적을 항목)

| 상태 | 메서드 | 경로 | 요청 | 응답 | 권한 |
|---|---|---|---|---|---|
| 🟡 | GET | /auth/login-options | — | `{ twoFactorEnabled: boolean }` | 공개 |
| 🟡 | POST | /auth/login | (변경 없음) 단, 2FA 켜짐 + 지원자면 세션의 LOGIN 용도 NICE 결과 필요 | 성공 동일. 실패: 비밀번호 401(기존), 2차 인증 400(사유 문구), 한도 429(기존) | 공개 |
| 🟡 | POST | /auth/nice/request | `purpose`에 `LOGIN` 허용 | 동일 | 공개 |

## 7. 규칙·불변식

- 서버가 지원자 로그인에 NICE 결과를 강제한다. 프론트 판단에 의존하지 않는다.
- LOGIN 용도 결과는 로그인에만 쓰인다. 다른 용도(`SIGNUP`·`FIND_EMAIL`·`PHONE_CHANGE`) 결과로는 통과하지 않고, 로그인 때 다른 용도의 세션 결과를 지우지도 않는다.
- 로그인 성공·2차 인증 실패 시 결과는 1회 소비된다.
- 이 설정을 꺼도 `POST /auth/nice/request`(LOGIN)는 막지 않는다. 쓰이지 않을 뿐이다.

## 8. 테스트 계획

| 대상 | 케이스 |
|---|---|
| `LoginSecondFactorVerifierTest` (신규) | 결과 없음 / 용도 불일치 / 5분 경과 / 명의 불일치 / 일치 통과 |
| `RoutingAuthenticationProviderTest` (기존 확장) | 지원자: 2FA 켜짐+일치 통과, 켜짐+없음 실패(횟수 증가), 꺼짐+없음 통과 / **임직원은 2FA 켜져도 통과** / 비밀번호 실패는 2차 검사 전에 끝남 |
| `AuthController` 통합 테스트 (신규) | 성공 후 세션 NICE 속성 제거 / 비밀번호 실패는 속성 유지 / 2차 실패는 속성 제거·400 문구 / 다른 용도 속성은 보존 / `login-options` 응답 |
| `SecurityConfigTest` | `GET /api/auth/login-options` 비로그인 허용 |
| FE | `npm run type-check`. 팝업·메시지 흐름은 로컬에서 실 NICE 불가 → 수동 확인 절차만 보고 |

## 9. 문서 갱신 (완료 조건)

`auth-account`(로그인 계약·엔드포인트 상세 — 카드가 40KB 상한에 가까워 로그인 2FA 상세는 `auth-nice-verification`에 두고 auth-account는 링크만), `auth-nice-verification`(용도 `LOGIN`·설정·함정), `auth-security`(공개 GET 매처), `recruit_back/recruit_backend/AGENTS.md` 환경변수 표(`RECRUIT_LOGIN_TWO_FACTOR_ENABLED`). `node tools/check-docs.mjs` 통과.

## 10. 위험·열린 질문

1. **로컬·테스트 계정**: 2FA가 켜지면 지원자 계정은 실제 NICE 명의가 맞아야 로그인된다. `docs/ops/test-seed-applicants.sql`의 가짜 `ciHash` 계정은 운영형 환경에서 로그인할 수 없다 → 그 환경은 설정을 끄거나 실명의 계정으로 가입해야 한다.
2. 설정을 끈 채 운영에 올라가면 2차 인증이 사라진다. 기본값을 `true`로 두고 기동 로그에 상태를 한 줄 남긴다(비밀값 아님).
3. NICE 건당 과금: 로그인 1회마다 NICE 1건이다. 월 로그인 건수 × 단가를 사전에 확인해야 한다.
4. NICE 장애 시 지원자 전원이 로그인할 수 없다. 이때는 설정 `false`로 재기동하는 것이 유일한 우회다(코드 변경 불필요).
5. 동일 명의 중복 계정은 가입 단계에서 이미 차단된다(`ciHash` unique).
