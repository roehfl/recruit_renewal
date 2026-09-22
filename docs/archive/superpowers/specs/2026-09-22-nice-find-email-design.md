# NICE 본인확인 기반 아이디(이메일) 찾기 설계

**작성일**: 2026-09-22
**대상 카드**: `docs/domains/auth-account.md`(화면·새 엔드포인트), `docs/domains/auth-nice-verification.md`(용도 추가·`request` 계약 변경)
**범위**: 아이디 찾기만. 비밀번호 재발급은 목업 그대로 둔다(범위 밖).

---

## 1. 목표

`AccountRecovery.vue`의 아이디 찾기를 목업(`/nice-auth/mock`, 결과 `abc12345@gmail.com` 하드코딩)에서 **NICE 실연동 본인확인 + 서버 조회**로 바꾼다. 결과는 **부분 마스킹한 아이디**로 보여준다.

성공 기준:

- NICE 인증을 통과한 사람의 계정 아이디(`loginId` = 이메일)가 마스킹되어 화면에 나온다.
- 계정이 없으면 "일치하는 계정이 없다"는 안내가 나온다.
- 가입용 인증 결과로는 아이디를 조회할 수 없고, 아이디 찾기용 인증 결과로는 가입할 수 없다(용도 대조).
- 인증 1회로 조회는 1회만 된다.
- 아이디 원문·생년월일·성별은 응답·로그에 나가지 않는다.

## 2. 확인된 사실과 전제

| 항목 | 내용 | 근거 |
|---|---|---|
| NICE 공통 계층 | 요청 발급·콜백·결과 교환·세션 저장(`NICE_VERIFIED`)이 가입 흐름으로 이미 구현·실인증 확인됨 | `NiceVerificationController`·`NiceVerificationService` |
| 용도 대조 | `NiceVerificationPurpose`(현재 `SIGNUP` 하나)를 요청 발급 시 기록하고 `requireFresh(identity, purpose)`가 소비 시 대조한다 | `NiceVerificationService.requireFresh` |
| 용도 하드코딩 | `POST /auth/nice/request`가 `SIGNUP`을 고정으로 넘긴다 | `NiceVerificationController.request` |
| 식별 키 | `AuditHmac.identityHash(이름, 생년월일, 성별)`, `Applicant.ciHash`(NOT NULL, **unique**)에 저장 | 2026-09-22 결정 |
| 계정 수 | `ciHash` unique라 식별 키 하나에 계정은 최대 1개 | `Applicant` |
| 아이디 | 이메일 = `loginId`(2026-09-20 확정). `Applicant.email`은 nullable이라 `loginId`를 쓴다 | `auth-account` 카드 |
| 파기 계정 | `ciHash`가 `PURGED:`+UUID로 덮여 식별 키로 조회되지 않는다 | `Applicant.purgePersonalData` |
| 목업 사용처 | `NiceAuthMockPopup.vue`(`/nice-auth/mock`)와 `window.phoneAuthCallback`은 `AccountRecovery.vue` 아이디 찾기만 쓴다 | grep |
| 404 예외 | `ApplicantNotFoundException` → 404 핸들러가 이미 있다 | `GlobalExceptionHandler` |
| 잘못된 본문 | 본문 누락·모르는 enum 값은 `HttpMessageNotReadableException` → 400 `"Invalid request."`, `@NotNull` 위반은 400 필드 메시지 | `GlobalExceptionHandler` |
| 카드 크기 | `auth-nice-verification.md` 40,741바이트 — 40KB 상한 직전이라 **갱신 전에 분할**해야 한다 | `wc -c` |

## 3. 결정

| # | 결정 | 이유 |
|---|---|---|
| D1 | 결과는 **부분 마스킹**(로컬부 앞 2자 + `*`, 도메인 전체) | 사용자 결정(2026-09-22). 휴대폰을 잠깐 쥔 제3자에게 원문이 드러나지 않고 본인은 알아본다 |
| D2 | **2단계 흐름**: NICE 결과는 세션에 두고, 별도 `POST /auth/applicants/find-email`이 소비해 조회한다 | 가입과 같은 패턴. NICE 모듈이 지원자 도메인(`ApplicantRepository`)에 의존하지 않고, `/auth/nice/result` 응답 모양이 용도마다 달라지지 않는다 |
| D3 | `POST /auth/nice/request`는 본문 `{ purpose }`를 **필수**로 받는다(기본값 없음) | 기본값을 두면 호출부가 용도를 빠뜨려도 조용히 `SIGNUP`이 된다. 프론트 두 곳을 함께 고치므로 호환성 부담이 없다 |
| D4 | 세션 인증 결과는 **조회 전에 제거**(계정 없음이어도 소비) | 인증 1회 = 조회 1회. 조회는 결정적이라 재시도할 이유가 없다 |
| D5 | 목업 팝업·라우트·`window.phoneAuthCallback` 타입을 **삭제**한다 | 이번 변경으로 사용처가 0이 된다. 앞선 NICE 작업이 분리해 만든 코드다 |
| D6 | 마스킹은 새 서비스 안에 둔다. `MessageContacts.maskEmail`을 재사용하지 않는다 | 그건 로그용(첫 글자+`***`)이고 메시지 발송 도메인 소유다. 형식·목적이 다르다 |

## 4. 흐름

```
AccountRecovery.vue                 NiceAuthPopup.vue (팝업)         백엔드
────────────────────────────────────────────────────────────────────────────────────
[본인 인증] 클릭
  window.open('/nice-auth?purpose=FIND_EMAIL')
                                    purpose 쿼리 검사(모르는 값 → FAIL 알림)
                                    POST /auth/nice/request {purpose:'FIND_EMAIL'}
                                                                     PENDING 레코드(purpose=FIND_EMAIL, sessionId)
                                    ← encodeData
                                    NICE 표준창으로 form POST
                                    … 통신사 인증 …
                                    NICE → GET /api/auth/nice/callback?EncodeData=…  (기존, 변경 없음)
                                    → 303 /nice-auth/result?token=…
                                    NiceAuthResult.vue: POST /auth/nice/result {token}  (기존, 변경 없음)
                                                                     세션 NICE_VERIFIED = identity(purpose=FIND_EMAIL)
                                    postMessage({source:'nice-auth', status:'SUCCESS', …}) → close
onNiceMessage(origin·source 검사)
  POST /auth/applicants/find-email
                                                                     requireFresh(identity, FIND_EMAIL)
                                                                     session.removeAttribute(NICE_VERIFIED)
                                                                     identityHash → findByCiHash
                                                                     → { maskedEmail } | 404
  마스킹 아이디 표시 | 404 안내 후 인증 전 상태로
```

콜백·결과 교환 단계는 용도와 무관하게 기존 코드를 그대로 쓴다. 용도는 발급 시 레코드에 기록돼 세션 identity까지 따라온다.

## 5. 컴포넌트

`{BE}` = `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit`, `{BT}` = 같은 패키지의 `src/test/java`, `{FE}` = `recruit_front/src`.

### 5.1 백엔드 신규

| 파일 | 책임 |
|---|---|
| `{BE}/dto/request/NiceRequestRequest.java` | `record NiceRequestRequest(@NotNull(message = "purpose는 필수입니다.") NiceVerificationPurpose purpose)` |
| `{BE}/dto/response/ApplicantFindEmailResponse.java` | `record ApplicantFindEmailResponse(String maskedEmail)` |
| `{BE}/service/ApplicantAccountRecoveryService.java` | `findEmail(NiceVerifiedIdentity)` — 식별 키 계산 → `findByCiHash` → `loginId` 마스킹. 없거나 `loginId`가 null이면 `ApplicantNotFoundException("본인인증 정보와 일치하는 계정이 없습니다.")`. `@Transactional(readOnly = true)`. 마스킹은 package-private static `maskLoginId(String)` |
| `{BE}/controller/ApplicantAccountRecoveryController.java` | `@RequestMapping("/auth/applicants")`, `@PostMapping("/find-email")` — 세션 `NICE_VERIFIED`를 `requireFresh(…, FIND_EMAIL)`로 검사 → `session.removeAttribute` → 서비스 호출 → `ApiResponse.success(response)` |

서비스는 `HttpSession`을 모른다(가입과 같은 원칙 — 단위 테스트가 서블릿 컨테이너에 묶이지 않게). 세션 읽기·제거는 컨트롤러가 한다.

### 5.2 백엔드 수정

| 파일 | 변경 |
|---|---|
| `{BE}/enumeration/NiceVerificationPurpose.java` | `FIND_EMAIL` 추가. javadoc의 "값이 하나뿐이어도" 문구 정리 |
| `{BE}/controller/NiceVerificationController.java` | `request(@Valid @RequestBody NiceRequestRequest request, HttpSession session)` — `request.purpose()`를 넘긴다 |
| `{BE}/config/SecurityConfig.java` | permitAll 목록(`/api/auth/login`… 줄)에 `/api/auth/applicants/find-email` 추가 |

`NiceVerificationService`·Store·Record·콜백 처리는 바꾸지 않는다(이미 purpose를 받아 기록·대조한다).

### 5.3 프론트

| 파일 | 변경 |
|---|---|
| `{FE}/types/auth/nice.ts` | `NICE_PURPOSES = ['SIGNUP', 'FIND_EMAIL'] as const`, `type NiceVerificationPurpose`, `isNicePurpose(value): value is NiceVerificationPurpose` |
| `{FE}/api/auth/niceApi.ts` | `request(purpose: NiceVerificationPurpose)` → 본문 `{ purpose }` |
| `{FE}/views/auth/pop-up/NiceAuthPopup.vue` | `route.query.purpose`를 `isNicePurpose`로 검사. 아니면 `reportFailure('본인확인을 시작하지 못했습니다.')`, 맞으면 `niceApi.request(purpose)` |
| `{FE}/views/applicant/SignupView.vue` | `window.open('/nice-auth?purpose=SIGNUP', …)` — 그 외 변경 없음 |
| `{FE}/types/application.ts` | `FindEmailResponse { maskedEmail: string }` |
| `{FE}/api/applicationApi.ts` | `findEmail()` → `POST /auth/applicants/find-email` (가입·이메일 중복 확인과 같은 모듈) |
| `{FE}/views/applicant/AccountRecovery.vue` | 아래 |
| `{FE}/routes/authRoutes.ts` | `/nice-auth/mock`(`NiceAuthMockPopup`) 라우트 삭제 |
| `{FE}/views/auth/pop-up/NiceAuthMockPopup.vue` | **삭제** |
| `{FE}/types/window.ts` | **삭제**(`phoneAuthCallback` 선언뿐) |

`AccountRecovery.vue` 아이디 찾기 부분:

- 팝업은 `window.open('/nice-auth?purpose=FIND_EMAIL', 'Nice-Auth', 'width=450, height=480, resizable=no')`.
- `window.phoneAuthCallback` 등록·해제 코드를 지우고 `SignupView`와 같은 `message` 리스너로 바꾼다 — `event.origin === window.location.origin`, `payload.source === NICE_MESSAGE_SOURCE` 검사, `onBeforeUnmount`에서 자기 리스너만 제거.
- `status !== 'SUCCESS'` → `message.error('본인인증에 실패했습니다. 다시 시도해주세요.')`, 인증 전 상태 유지.
- `SUCCESS` → `applicationApi.findEmail()`. 성공 시 `maskedEmail` 저장·표시, `isNiceAuthComplete = true`. 실패(404·400) 시 `message.error(getApiErrorMessage(error, '아이디를 찾지 못했습니다.'))`, 인증 전 상태로 둔다(다시 인증 가능).
- 결과 영역의 하드코딩 `abc12345@gmail.com` → `{{ maskedEmail }}`. 문구 "휴대전화번호 정보와 일치하는 아이디입니다." → "본인인증 정보와 일치하는 아이디입니다."(식별 기준이 휴대폰이 아니다).
- `openFindId`가 다시 열 때 `maskedEmail`도 비운다.
- 비밀번호 재발급 부분은 건드리지 않는다.

## 6. API 계약

| 상태 | 메서드 | 경로 | 요청 | 응답 | 권한 | 소유 카드 |
|---|---|---|---|---|---|---|
| 🟡 변경 | POST | /auth/nice/request | `{ purpose: 'SIGNUP' \| 'FIND_EMAIL' }` **필수** (기존: 없음) | `{ encodeData }` | 공개 | auth-nice-verification |
| 🟡 신규 | POST | /auth/applicants/find-email | 없음(세션의 NICE 인증 결과) | `{ maskedEmail }` | 공개 | auth-account |

`find-email` 오류:

| 상황 | 상태 | 메시지 |
|---|---|---|
| 세션에 인증 결과 없음 | 400 | `"본인인증을 먼저 진행해주세요."` |
| 인증 용도가 `FIND_EMAIL`이 아님(가입용 인증) | 400 | `"본인인증 용도가 일치하지 않습니다."` |
| 인증 후 `verified-ttl-minutes`(30분) 경과 | 400 | `"본인인증이 만료되었습니다. 다시 진행해주세요."` |
| 일치 계정 없음(미가입·파기) | 404 | `"본인인증 정보와 일치하는 계정이 없습니다."` |

앞 세 개는 `requireFresh` 기존 문구 그대로다(정상 사용자가 자기 세션 상태를 보는 안내라 원인별 문구 유지 — 기존 규칙).

`request` 오류: 본문 없음·모르는 `purpose` 문자열 → 400 `"Invalid request."`, `purpose: null` → 400 `"purpose는 필수입니다."`.

## 7. 마스킹 규칙

`loginId`를 `@` 앞(로컬부)과 뒤(도메인)로 나눈다.

- 로컬부 길이 ≥ 3 → 앞 2자 유지, 길이 ≤ 2 → 앞 1자 유지.
- 나머지 로컬부는 같은 길이의 `*`. 가린 글자가 0개가 되면 `*` 1개를 붙인다.
- 도메인은 `@` 포함 그대로.
- `@`가 없으면(API 직접 호출로 만든 계정 — BE는 `loginId` 형식을 검증하지 않는다) 문자열 전체를 로컬부로 보고 같은 규칙.

| 입력 | 출력 |
|---|---|
| `abc12345@gmail.com` | `ab******@gmail.com` |
| `abc@x.com` | `ab*@x.com` |
| `ab@x.com` | `a*@x.com` |
| `a@x.com` | `a*@x.com` |
| `hongildong` | `ho********` |

## 8. 보안 고려

- **용도 대조**: `SIGNUP` 인증으로 `find-email` 불가, `FIND_EMAIL` 인증으로 가입 불가(`requireFresh` 양쪽). 세션 키 `NICE_VERIFIED`는 하나라 나중 인증이 앞 인증을 덮는다 — 가입 도중 아이디 찾기를 하면 가입 쪽이 다시 인증해야 한다(허용).
- **1회용**: 조회 전에 세션에서 제거한다(D4).
- **세션 결속·재전송 방어**: 기존 파이프라인(`REQ_SEQ` 1회용, `resultToken` 세션 결속·1분·1회용) 그대로.
- **노출 범위**: 응답은 마스킹 아이디만. 생년월일·성별·식별 키는 응답·로그에 없다. 이 기능은 로그를 새로 남기지 않는다.
- **계정 존재 여부**: 404는 NICE로 본인 확인을 마친 사람에게만 자기 가입 여부를 알려 준다. 기존 `check-email`보다 약한 노출이다.
- **남용 비용**: NICE는 건당 과금일 수 있다. 호출 제한(rate limit)은 가입 흐름에도 없고 이번 범위 밖이다(12절).

## 9. 문서

1. **`auth-nice-verification.md` 분할(선행)** — "함정·결정" 가운데 벤더 모듈 관련 절(**jar 실측**, **평문 직렬화는 EUC-KR**, **알려진 한계 2건**, **연동 방식 확인분**)을 새 하위 카드 `docs/domains/auth-nice-verification-module.md`로 옮긴다. 원 카드에는 한 줄 링크를 남긴다. `_index.md` 색인에 하위 카드를 추가한다. **파일 지도는 옮기지 않는다** — 원 카드가 모든 NICE 파일을 계속 소유하고, 하위 카드는 서술(실측·규격·한계)만 담는다. 소유가 한 곳에 남아 점검 스크립트의 중복·누락 검사와 충돌하지 않는다.
2. **`auth-nice-verification.md`** — `request` 계약 🟡→🟢, 엔드포인트 상세의 "`purpose=SIGNUP` 고정" 서술 교체, 용어·요약의 "아이디 찾기는 목업" 서술 교체, 파일 지도에서 `NiceAuthMockPopup.vue` 제거·`NiceRequestRequest` 추가, 변경 레시피를 "비밀번호 재설정에 얹기"로 축소.
3. **`auth-account.md`** — API 계약에 `find-email` 추가, 파일 지도에 새 컨트롤러·서비스·DTO 등록·`types/window.ts` 제거, 규칙에 마스킹·1회 소비, "아이디 찾기는 목업" 서술 교체(비밀번호 재발급·이메일 인증은 목업 유지). 현재 35,308바이트라 추가분이 40KB를 넘지 않게 짧게 쓴다.
4. `node tools/check-docs.mjs` 오류 0건.

## 10. 테스트 전략

### 백엔드

| 테스트 | 케이스 |
|---|---|
| `{BT}/service/ApplicantAccountRecoveryServiceTest.java`(신규) | 식별 키 일치 계정 → 마스킹 아이디 / 일치 없음 → `ApplicantNotFoundException` / 마스킹 규칙 표(7절) 전 행 |
| `{BT}/controller/ApplicantAccountRecoveryControllerTest.java`(신규, `@SpringBootTest` + MockMvc) | `FIND_EMAIL` 세션 → 200 `maskedEmail` / 같은 세션 두 번째 호출 → 400 / 세션 없음 → 400 / `SIGNUP` 세션 → 400 / 계정 없음 → 404 / 응답 본문에 원문 아이디 없음 |
| `{BT}/controller/NiceVerificationControllerTest.java`(수정) | `issueReqSeq` 등 `request` 호출에 본문 `{"purpose":"SIGNUP"}` 추가 / `FIND_EMAIL`로 발급한 요청이 결과 교환 후 세션 identity의 `purpose == FIND_EMAIL` / 본문 없음 400 / 모르는 값 400 / `purpose: null` 400 |
| `{BT}/controller/ApplicantSignUpControllerTest.java` | `FIND_EMAIL` 세션으로 가입 → 400(용도 대조 역방향) |
| `{BT}/config/SecurityConfigTest.java` | `find-email` 비인증 인가 통과(401·403 아님). 기존 `request` 테스트는 본문 없이도 400이라 "401·403 아님"·CORS 403 단정이 그대로 성립 — 수정 불필요 |

기존 테스트에서 `/api/auth/nice/request`를 호출하는 곳은 **엔드포인트 경로로** 찾는다(클래스명 grep은 raw JSON 호출을 놓친다 — 가입 작업 교훈).

실행 범위: 위 클래스 + `com.shinyoung.recruit.service.nice.*`.

### 프론트

- `npm run type-check`.
- 수동: 로컬 Mock은 NICE 표준창을 통과하지 못해 브라우저 확인이 불가하다. **개발 서버에서 사용자가 실인증으로 확인**한다 — ① 가입한 계정으로 아이디 찾기 → 마스킹 아이디 ② 미가입 명의 → 안내 ③ 가입 흐름 회귀(여전히 가입됨).

## 11. 구현 순서

1. 카드 분할(`auth-nice-verification-module.md`) + `check-docs` 통과.
2. BE: `FIND_EMAIL` + `NiceRequestRequest` + `NiceVerificationController.request` 변경 + 기존 테스트 본문 추가(TDD).
3. BE: `ApplicantAccountRecoveryService`(마스킹 포함) + 테스트.
4. BE: `ApplicantAccountRecoveryController` + `SecurityConfig` + 컨트롤러·보안·가입 역방향 테스트.
5. FE: 타입·`niceApi`·`NiceAuthPopup` purpose 처리·`SignupView` 쿼리.
6. FE: `applicationApi.findEmail` + `AccountRecovery` 교체 + 목업 팝업·라우트·`window.ts` 삭제 → `type-check`.
7. 카드 갱신(계약 🟢) + `check-docs`.

## 12. 범위 밖

- 비밀번호 재발급(목업 유지, 카드 결정은 이메일 토큰 링크 방식 — 메일 발송 인프라 필요).
- 가입 이메일 인증 목업.
- `POST /applicant/account/phone-number`의 NICE 연동(화면 미사용).
- NICE 호출 rate limit·감사 로그(`ActivityLog`) 연동.
- 개발 서버 H2의 목업 시절 가입 계정(`ciHash` 옛 방식이라 조회되지 않음 — DB 교체 예정).
