# NICE 본인확인 (`auth-nice-verification`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [auth-account](auth-account.md)(로그인·세션·지원자 가입 — 가입 API가 이 카드의 세션 인증 결과를 소비한다)

`auth-account.md`가 40KB 상한(348 bytes 여유)에 가까워져 NICE 본인확인 부분을 분리했다(2026-09-21, `privacy-audit`/`privacy-audit-audit` 분리 선례를 따름). 이동 이력은 git log로 대신한다.

## 요약

- 가입 시 본인확인은 **NICE 체크플러스(CheckPlus) 표준창 실연동**이다. 아이디 찾기·비밀번호 재설정은 범위 밖(미착수)이며, 그 전까지는 `NiceAuthMockPopup.vue` 목업을 그대로 쓴다([auth-account](auth-account.md) 소유 화면, `AccountRecovery.vue`에서 호출).
- 흐름: ① 팝업이 `POST /auth/nice/request`로 암호문(`encodeData`)을 받아 NICE 표준창에 폼 POST ② 사용자 인증 후 NICE가 **사용자 브라우저를** `/auth/nice/callback`(성공)·`/auth/nice/callback/error`(실패)로 보낸다 — **GET 쿼리 `?EncodeData=`**(2026-09-22 실인증 확인, POST도 받는다. 세션 쿠키 없음, `REQ_SEQ`로만 레코드 식별) ③ `303`으로 리다이렉트된 `/nice-auth/result`가 same-site로 `POST /auth/nice/result`를 호출해 1회용 `resultToken`을 인증 결과로 교환하고 세션에 담는다.
- **가입자 식별은 이름+생년월일+성별이다.** 이 사이트코드는 NICE 계약상 CI를 받지 않는다(2026-09-22 실응답 확인). 식별 값은 서버 세션에만 있다가 가입 제출 시 1회 소비되고 브라우저로 내려가지 않는다.
- 벤더 모듈(`NiceID.jar`, `NiceID.Check.CPClient`)이 `build.gradle`에 연결돼 있고 `RealNiceClient`가 이를 호출해 실연동을 구현한다. `NiceClientConfig`는 `mock-enabled`×실연동 설정 4개(`site-code`·`site-password`·`return-url`·`error-url`) 조합을 검사하는 fail-closed 가드를 갖췄다 — 애매한 조합은 기동을 막는다(아래 "함정·결정").
- 용도(`NiceVerificationPurpose`)는 현재 `SIGNUP` 하나뿐이다. 이메일 찾기·비밀번호 재설정은 같은 검증 지점(`NiceVerificationService`)에 값만 추가하면 얹을 수 있다(범위 밖, 미착수).

## 용어

| 용어 | 뜻 |
|---|---|
| CheckPlus | NICE 표준창 본인확인 방식. 인증 키 = 사이트코드+사이트패스워드, 암복호화는 벤더 모듈(`NiceID.jar`)이 담당 |
| `REQ_SEQ` | 요청 1건 식별자. 모듈이 발급, 1회용(`PENDING` 상태에서만 소비 가능) |
| `EncodeData` | 평문(plaindata)을 모듈이 암호화한 왕복 암호문. 요청·콜백 양쪽에 쓰는 이름 |
| plaindata | 평문 직렬화 규격. `바이트길이:값`을 키·값 순서로 반복해 이어 붙인다(예: `7:REQ_SEQ3:abc`). 길이는 **EUC-KR 바이트**다. 레거시 조립 코드(`"7:REQ_SEQ" + len + ":" + val`)와 같은 형식이고, 벤더 `fnParse`가 우리 출력을 그대로 파싱한다(`NicePlaindataCodecVendorCompatibilityTest`). 요청 조립과 응답 파싱 **모두 우리 `NicePlaindataCodec`** 이 한다. 복호화 왕복은 로컬 검증 불가 |
| `resultToken` | `/auth/nice/result` 교환용 1회용 토큰(TTL 1분). 발급한 세션에만 묶인다 |
| `NiceVerifiedIdentity` | 세션 속성 `NICE_VERIFIED`에 담기는 인증 결과(`purpose`·`name`·`phoneNumber`·`birthDate`·`gender`·`verifiedAt`). 생년월일·성별이 있어 **그대로 응답에 넣지 않는다** |
| `NiceVerificationRecord` | Store가 보관하는 요청 1건의 불변 값 객체. 상태 전이마다 새 인스턴스로 교체 |
| `NiceVerificationStatus` | `PENDING`(콜백 대기)·`VERIFIED`(콜백 성공)·`FAIL`(사용자 취소·NICE 실패) |
| `param_r1`~`param_r3` | NICE 스펙상 업체 지정 데이터 왕복 슬롯(`REQ_SEQ` 대조와 별개 채널). 현재 코드는 쓰지 않는다 |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/NiceVerificationController.java` | 엔드포인트 4개(`request`·`callback`·`callback/error`·`result`) |
| service | `{BE}/service/nice/NiceClient.java` | 벤더 모듈 경계 인터페이스(`generateRequestNo`·`encode`·`decode`→`NiceDecodeResult`·`parse`) |
| service | `{BE}/service/nice/NiceDecodeResult.java` | 복호화 결과 값 객체(`plaindata`+`cipherEpochSeconds`). 모듈의 `getCipherDateTime()`이 인자 없이 `fnDecode` 이후 인스턴스 상태를 읽어 둘을 같이 돌려준다 |
| service | `{BE}/service/nice/MockNiceClient.java` | jar 비의존 개발·테스트 구현(`MOCK.<epoch초>.<Base64>`) |
| service | `{BE}/service/nice/RealNiceClient.java` | `NiceID.jar`(`CPClient`) 호출 구현. 복호화·파싱 로컬 왕복 검증 불가(아래 "함정·결정") |
| service | `{BE}/service/nice/NicePlaindataCodec.java` | 키+**EUC-KR** 바이트길이+값 조립·파싱. **요청 조립과 응답 파싱 모두 담당**(실연동 포함 — 벤더 `fnParse`를 쓰지 않는다) |
| service | `{BE}/service/nice/NiceVerificationRecord.java` | Store 레코드(값 객체, 불변) |
| service | `{BE}/service/nice/NiceVerifiedIdentity.java` | 세션에 담기는 인증 결과 |
| service | `{BE}/service/nice/NiceVerificationStore.java` | 인메모리 보관소(`ConcurrentHashMap`) + 원자 연산(`saveIfAbsent`·`compareAndSet`·`takeByResultToken`) + TTL 정리 |
| service | `{BE}/service/nice/NiceVerificationService.java` | 발급·콜백 검증·결과 교환 오케스트레이션. Store 원자 연산만 쓴다 |
| service | `{BE}/service/nice/NiceVerificationCleanupScheduler.java` | 만료 레코드 정리(`verified-ttl-minutes` 기준, `recruit.nice.cleanup-interval-ms` 주기 — 기본 5분) |
| config | `{BE}/config/NiceProperties.java` | `recruit.nice.*` 바인딩 |
| config | `{BE}/config/NiceClientConfig.java` | 구현 선택 + fail-closed 가드(`mock-enabled`×실연동 설정 4개 조합, 아래 "함정·결정") |
| enumeration | `{BE}/enumeration/NiceVerificationPurpose.java` | `SIGNUP`(현재 유일값) |
| enumeration | `{BE}/enumeration/NiceVerificationStatus.java` | `PENDING`·`VERIFIED`·`FAIL` |
| exception | `{BE}/exception/NiceVerificationException.java` | 검증 실패(복호화·요청번호·세션 불일치 전부 포함, 사유 구분 없이 사용자에 반환) |
| dto | `{BE}/dto/response/NiceRequestResponse.java` | `{ encodeData }` |
| dto | `{BE}/dto/request/NiceResultRequest.java` | `{ token }`(`@NotBlank`) |
| dto | `{BE}/dto/response/NiceResultResponse.java` | `{ status, name, phoneNumber }`(생년월일·성별 없음) |
| test | `{BT}/service/nice/*Test.java` | 코덱 왕복·벤더 파서 교차 검증·Store TTL·원자 연산·동시성(`CyclicBarrier`)·request/callback/exchange 흐름·`RealNiceClient` 모듈 로딩(9개 클래스, 58건) |
| test | `{BT}/controller/NiceVerificationControllerTest.java` | 엔드포인트 4개 통합, 콜백 실패 시 303 무토큰 리다이렉트 포함(7건) |
| test | `{BT}/config/NiceClientConfigTest.java` | fail-closed 가드 조합 검증, 거부 메시지에 자격증명 값 미노출 확인(7건) |
| lib | `recruit_back/recruit_backend/libs/NiceID.jar` | 벤더 모듈(7KB, 네이티브 동반 파일 없음). `build.gradle`에 `implementation files(...)`로 연결됨 |

가입 흐름과의 통합 테스트(`{BT}/controller/ApplicantSignUpNiceIntegrationTest.java`, 5건 — "세션의 인증 결과를 가입이 소비" 검증)는 [auth-account](auth-account.md)의 가입 흐름에 속한다. 이 카드는 그 파일을 소유하지 않는다.

### 프론트

| 구분 | 파일 | 역할 |
|---|---|---|
| view | `{FE}/views/auth/pop-up/NiceAuthPopup.vue` | 가입용 NICE 실연동 팝업(`/nice-auth`). `request` 호출 후 표준창으로 폼 POST(진행 안내만, 입력 UI 없음) |
| view | `{FE}/views/auth/pop-up/NiceAuthResult.vue` | NICE 콜백 결과 중계(`/nice-auth/result`). `result` 호출 → `postMessage` → `window.close()` |
| view | `{FE}/views/auth/pop-up/NiceAuthMockPopup.vue` | **아이디·비밀번호 찾기 전용** 본인인증 목업(`/nice-auth/mock`). CI=`crypto.randomUUID()` |
| api | `{FE}/api/auth/niceApi.ts` | `request`·`exchangeResult` |
| types | `{FE}/types/auth/nice.ts` | 요청·응답 타입, 상수 |

라우트 3개(`NiceAuthPopup`·`NiceAuthResult`·`NiceAuthMockPopup`, 전부 `public`)는 `{FE}/routes/authRoutes.ts`에 선언돼 있다. 이 파일은 `Login` 라우트도 함께 갖고 있어 소유는 [auth-account](auth-account.md)다(`{FE}/routes/*.ts`는 점검 스크립트의 소유 검사 대상이 아니다).

## API 계약

| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |
|---|---|---|---|---|---|
| 🟢 | POST | /auth/nice/request | 없음(세션으로 식별) | `{ encodeData }` | 공개 |
| 🟢 | GET·POST | /auth/nice/callback | `EncodeData` — NICE는 **GET 쿼리**로 보낸다(POST form도 받음, 세션 없음) | `303` → `/nice-auth/result?token=...`(성공) 또는 토큰 없이(검증 실패) | 공개 |
| 🟢 | GET·POST | /auth/nice/callback/error | `EncodeData` — GET 쿼리 또는 POST form(세션 없음) | `303` → `/nice-auth/result?token=...`(정상 실패 콜백) 또는 토큰 없이(검증 예외) | 공개 |
| 🟢 | POST | /auth/nice/result | `{ token }` | `{ status, name, phoneNumber }` | 공개 |

응답은 `ApiResponse<T>`. `POST /auth/applicants/sign-up`이 이 카드의 세션 인증 결과를 소비하는 계약은 [auth-account](auth-account.md) 소유.

### 엔드포인트 상세

- **`POST /auth/nice/request`**: `HttpSession`의 `session.getId()`로 레코드를 만든다(`purpose=SIGNUP` 고정, `NiceVerificationController`가 하드코딩). 요청번호(`REQ_SEQ`)는 모듈이 만들지만 밀리초+random%100이라 동시 발급 시 충돌한다 — `NiceVerificationStore.saveIfAbsent`로 감지해 최대 5회까지 재발급하고, 그래도 겹치면 거부한다. `REQ_SEQ`·`SITECODE`·`AUTH_TYPE`(빈 값)·`RTN_URL`·`ERR_URL`·`POPUP_GUBUN`(`"N"`)·`CUSTOMIZE`(빈 값) 7개 평문 키·순서로 `NicePlaindataCodec.encode`(EUC-KR 바이트 길이) → `NiceClient.encode`를 거쳐 `encodeData`를 돌려준다.
- **`GET·POST /auth/nice/callback` / `/callback/error`**: NICE가 사용자 브라우저를 보내 부르는 콜백. **NICE는 GET 쿼리(`?EncodeData=`)로 돌려준다**(2026-09-22 실인증 확인 — 설계 초안은 폼 POST로 가정해 `@PostMapping`이었고 `405`가 났다. 레거시 JSP는 메서드를 가리지 않아 드러나지 않았다). 레거시처럼 GET·POST 둘 다 받는다. `@RequestParam(value = "EncodeData", required = false)`로 받아, 비었으면 실패 경로로 보내고, **공백을 `+`로 되돌린다**(Base64라 `+`가 섞이는데 NICE가 퍼센트 인코딩 없이 쿼리에 붙이면 서블릿이 `+`를 공백으로 디코드한다. Base64엔 공백이 없어 손실이 없다. 값이 `+`로 시작·끝날 수 있어 trim하지 않는다). 그다음 `decode`→`parse`한 뒤 `REQ_SEQ`로 `PENDING` 레코드를 소비한다(`consumePending` — 없음·이미 처리·만료는 전부 거부). 성공 콜백은 추가로 암호문 생성 시각 검사(`NiceDecodeResult.cipherEpochSeconds`, `request-ttl-minutes`와 같은 창, **KST 고정**)를 통과해야 `NAME`·`MOBILE_NO`·`BIRTHDATE`·`GENDER`를 저장한다. 상태 전이(`PENDING`→`VERIFIED`/`FAIL`)는 `NiceVerificationStore.compareAndSet`으로 원자적으로 하며, 동시에 두 번 온 콜백 중 진 쪽은 예외를 받는다. 통과하면 1회용 `resultToken`을 발급해 `303`으로 `/nice-auth/result?token=...`에 리다이렉트한다. **컨트롤러가 `NiceVerificationException`을 잡아 토큰 없이 `303`으로 보낸다** — 팝업이 직접 받는 페이지 이동이라 400 JSON을 돌려주면 원시 응답이 렌더되고 부모창에 실패가 전달되지 않는다. 콜백에는 세션 쿠키가 실리지 않아(cross-site 이동, `SameSite=Lax`) 세션 대조는 하지 않는다.
- **`POST /auth/nice/result`**: same-site 요청이라 세션 쿠키가 실린다. `NiceVerificationStore.takeByResultToken`(`remove(key, value)`)으로 `resultToken`에 해당하는 레코드를 **원자적으로 찾아 제거**한 뒤(재시도 방지, 동시 호출 시 한쪽만 성공) 세션 id 일치 → TTL(1분) → `VERIFIED` 상태를 순서대로 검사한다. 통과하면 `NiceVerifiedIdentity`를 세션 속성 `NICE_VERIFIED`에 저장하고 이름·휴대폰만 응답한다(생년월일·성별은 응답에 없음).
- **오류 처리**: `request`·`callback`·`callback/error`·`result` 파이프라인의 모든 검증 실패는 `NiceVerificationException` 하나로 통일돼 있고 **사용자 노출 문구도 하나**(`"본인확인에 실패했습니다. 다시 시도해주세요."`)다. 원인은 로그로만 구분한다 — 세션 불일치·재발급 상한 초과·모듈 오류(`RealNiceClient`)는 `log.warn`, 만료·이미 처리됨·동시 처리 패배 등은 `log.info`. 원인별 문구를 노출하면 재전송·토큰 탈취 시도자에게 정보가 된다.
  - `result`(same-site, `GlobalExceptionHandler.handleNiceVerification`)는 **여전히 400 JSON**이다.
  - `callback`·`callback/error`(NICE가 보내는 cross-site 이동)는 컨트롤러가 예외를 잡아 **토큰 없이 `303`**으로 보낸다. 결과 화면(`NiceAuthResult.vue`)이 토큰 없음을 보고 부모창에 FAIL을 전달하고 닫는다.
  - **`requireFresh`(가입 제출 시점 세션 검사)는 이 통일 문구를 쓰지 않는다.** 정상 사용자가 자기 세션 상태를 보는 안내라 공격 가치가 없고, 무엇을 해야 할지 알아야 한다(`"본인인증을 먼저 진행해주세요."` 등 원인별 문구 유지).
  - NICE 원시 오류코드(`fnEncode`/`fnDecode`의 `code=-6` 등)는 `RealNiceClient`의 `log.warn`에만 남고 응답에는 나가지 않는다.
  - 설계 초안은 세션 불일치를 403+감사 로그로 분리할 계획이었으나 구현은 문구를 하나로 합쳤고 감사 로그(`ActivityLog`, [privacy-audit-audit](privacy-audit-audit.md)) 연동도 없다 — SLF4J `log.warn`만 남는다. 토큰 탈취 시도를 감사 로그로 추적하려면 별도 작업이 필요하다.

## 규칙·불변식

- **가입자 식별은 이름+생년월일+성별이다**(2026-09-22 결정). 이 사이트코드는 NICE 계약상 **CI·DI를 받지 않는다**(레거시도 안 받았다). 중복 판정 키 = `AuditHmac.identityHash(NAME, BIRTHDATE, GENDER)` = `HMAC_SHA256(AUDIT_HMAC_SECRET, "IDENTITY:"+이름|생년월일|성별)`이고 **기존 `Applicant.ciHash` 컬럼에 저장한다**(필드명은 파기 모듈·테스트와 묶여 유지, 의미만 바뀜). `Applicant.ci` 컬럼은 제거했다([auth-account](auth-account.md)). 일반 SHA-256을 쓰지 않는 이유: 이름이 같은 행에 평문이라 생년월일·성별 대입(약 7만 번)으로 역산된다. 동명이인이 생일·성별까지 같으면 막힌다(감수). 휴대폰은 판정에 넣지 않는다(번호를 바꾸면 재가입되므로).
- **`AUDIT_HMAC_SECRET`을 교체하면 중복 가입 판정이 깨진다.** 기존 가입자의 키와 새로 계산한 키가 달라져 같은 사람이 다시 가입할 수 있다. 교체하지 않거나, 교체 시 전 가입자 키를 재계산한다.
- **식별 값은 브라우저로 내려가지 않는다.** 서버가 복호화한 생년월일·성별을 세션에만 두고 가입 시 꺼내 쓴다. 가입 요청 본문에 이름·휴대폰·식별 값이 없다. 예전엔 프론트가 만든 가짜 CI를 서버가 믿어 본인확인이 사실상 없었다.
- **콜백엔 세션 쿠키가 없다**(cross-site 이동, `SameSite=Lax`). `callback`·`callback/error`는 `REQ_SEQ`로만 레코드를 찾는다. 세션 대조는 뒤따르는 same-site `result`에서 한다.
- **콜백 2종은 CORS 처리에서 빠진다**(`SecurityConfig.CORS_EXEMPT_PATHS`). NICE는 현재 GET으로 돌려주고 브라우저는 **GET 이동엔 `Origin`을 붙이지 않으므로** 실사용 경로는 원래 CORS 대상이 아니다. 다만 콜백이 POST도 받으므로 그 경우를 위해 예외를 둔다. POST 이동에는 브라우저가 `Origin: https://nice.checkplus.co.kr`(Referrer-Policy에 따라 `null`)을 붙이는데, `CorsFilter`는 폼 이동과 스크립트 요청을 구분하지 않고 허용 목록 밖 Origin을 `403 Invalid CORS request`로 거부한다. **NICE Origin을 허용 목록에 넣지 않는다** — `allowCredentials=true`라 NICE 쪽 스크립트가 모든 API를 자격 증명과 함께 읽을 수 있게 된다. `null`은 샌드박스 iframe·`file://`도 쓰는 값이라 허용하면 안 된다. 콜백의 안전성은 Origin이 아니라 `REQ_SEQ` 대조와 암호문에 있다. 예외는 이 두 경로뿐이고 `request`·`result`는 허용 목록 검사를 그대로 받는다.
- **파이프라인 실패는 사용자에게 문구 하나로만 보인다.** `request`·`callback`·`callback/error`·`result` 전부 `"본인확인에 실패했습니다. 다시 시도해주세요."`(`NiceVerificationService.FAILURE_MESSAGE`)다. 원인은 로그로만 구분한다(세션 불일치·재발급 상한 초과·모듈 오류 `warn`, 만료·이미 처리됨 등 `info`) — 원인별로 다른 문구를 보여주면 재전송·토큰 탈취 시도자에게 정보가 된다. 가입 제출 시점 `requireFresh` 안내 문구는 예외다(아래 API 계약 상세).
- **`REQ_SEQ`는 1회용이다.** 레코드가 `PENDING`이 아니면 거부한다(`consumePending`). 암호문 생성 시각 검사(`NiceDecodeResult.cipherEpochSeconds`, **KST 고정** — 서버 JVM이 UTC로 떠도 무관)가 독립된 2차 게이트로 붙는다 — 둘 중 하나만 걸려도 거부한다. 시각을 읽지 못하면 거부 대신 경고 로그만 남기고 2차 게이트를 건너뛴다(포맷을 실응답으로 확인할 수단이 없어 fail-open, 주 방어선은 Store 대조). 레거시엔 이 대조가 없어 재전송에 열려 있었다.
- **Store 원자 연산으로 동시 재전송을 막는다.** `find`→검사→`save`는 check-then-act 경쟁이라 동시 재전송을 막지 못했다(최종 코드 리뷰가 재현). 발급은 `saveIfAbsent`(모듈 `REQ_SEQ`가 밀리초+random%100이라 동시 발급 시 충돌 — 실측 순차 2,000회 중 1,765건 — 감지 시 최대 5회 재발급, 초과 시 거부), 콜백 상태 전이는 `compareAndSet`(동시에 두 번 오면 한쪽만 이긴다), 결과 교환은 `takeByResultToken`(`remove(key, value)`, 조회·제거가 원자적)만 쓴다. `NiceVerificationConcurrencyTest`가 `CyclicBarrier`로 경쟁을 결정적으로 재현한다(스레드 두 개를 그냥 띄우면 대부분 순차로 돌아 우연히 통과하므로 쓰지 않는다).
- **`resultToken`도 1회용이고 세션에 묶인다.** `exchangeResult`는 `takeByResultToken`으로 레코드를 **검사보다 먼저, 원자적으로 제거**한다 — 세션 불일치·만료로 거부돼도 소각해야 조건을 바꿔가며 같은 토큰을 재시도할 수 없고, 같은 토큰으로 동시에 두 번 불려도 한쪽만 레코드를 받는다.
- **`NiceVerificationStore`는 인메모리·단일 인스턴스 전제다**(`ConcurrentHashMap`). HTTP 세션과 같은 전제다. 다중 인스턴스로 확장하면 세션과 이 Store를 함께 외부 저장소로 옮겨야 한다.
- **TTL 3종**: 요청→콜백 10분(`request-ttl-minutes`, 통신사 인증 소요 시간 겸 암호문 생성 시각 검사 창) · `resultToken` 1분(1회용, 팝업이 즉시 교환) · 인증 완료→가입 제출 30분(`verified-ttl-minutes`, 폼 작성 시간). 만료 판정은 읽는 시점에도 다시 하므로 `NiceVerificationCleanupScheduler`(기본 5분 주기, `recruit.nice.cleanup-interval-ms`)가 늦게 돌아도 보안에 영향이 없다 — 목적은 메모리 누적 방지뿐이다. 정리 기준은 **verified-ttl**이다(request-ttl로 지우면 아직 유효한 인증 완료 레코드가 지워진다).
- 재기동하면 진행 중이던 인증이 전부 끊긴다(인메모리). 사용자는 재인증하면 되고 빈도가 낮아 허용한다.

### 설정

`{BR}/application.yaml`의 `recruit.nice.*`(전부 환경변수 주입, **운영 자격증명 값은 저장소에 두지 않는다**):

| 키 | 환경변수 | 기본값 | 비고 |
|---|---|---|---|
| `site-code` | `NICE_SITE_CODE` | 빈 값 | 운영 자격증명 |
| `site-password` | `NICE_SITE_PASSWORD` | 빈 값 | 운영 자격증명 |
| `return-url` | `NICE_RETURN_URL` | 빈 값 | NICE 등록값과 정확히 같아야 한다(평문에 실려 암호화된다) |
| `error-url` | `NICE_ERROR_URL` | 빈 값 | 위와 동일 |
| `mock-enabled` | `NICE_MOCK_ENABLED` | **`false`** | 로컬은 `NICE_MOCK_ENABLED=true`를 명시적으로 준다. 운영은 `NICE_SITE_CODE`와 함께 두지 않는다(그러면 가드가 기동을 막는다) |
| `request-ttl-minutes` | `NICE_REQUEST_TTL_MINUTES` | `10` | `@Min(1)` |
| `verified-ttl-minutes` | `NICE_VERIFIED_TTL_MINUTES` | `30` | `@Min(1)` |
| `cleanup-interval-ms`(`NiceVerificationCleanupScheduler` 자체 프로퍼티, `NiceProperties` 밖) | 없음(`@Scheduled` 기본식) | `300000`(5분) | 코드에서 확인, 태스크 지시 목록엔 없던 항목 |

`mock-enabled` 기본값은 **`false`** 다. `true`로 두면 운영이 NICE 설정을 통째로 빠뜨렸을 때 Mock + 사이트코드 없음 조합이 되어 가드를 통과하고, 가짜 신원으로 가입이 조용히 된다. 이 프로젝트는 프로파일이 없어 개발·운영을 구분할 수 없으므로 기본값 자체가 안전해야 한다. 로컬은 `AUDIT_ALLOW_FALLBACK_SECRET`처럼 `NICE_MOCK_ENABLED=true`를 명시적으로 준다.

## 변경 레시피

### 이메일 찾기·비밀번호 재설정에 NICE 본인확인 얹기
1. `{BE}/enumeration/NiceVerificationPurpose.java`에 값 추가(예: `FIND_EMAIL`·`RESET_PASSWORD`).
2. 해당 플로우 서비스에서 `NiceVerificationService.requireFresh(identity, purpose)`로 세션의 `NICE_VERIFIED`를 소비한다. 용도 대조를 빼면 가입용 인증 결과를 다른 흐름에 밀어 넣을 수 있다.
3. FE는 `NiceAuthPopup.vue`(또는 별도 팝업)로 `request`를 호출하고, 완료 후 목업(`NiceAuthMockPopup.vue`)을 실제 팝업으로 교체한다.
4. 새 `{BT}/service/nice/*Test.java` 케이스 추가, 카드 API 표·용어 갱신, `node tools/check-docs.mjs`.

### `RealNiceClient` 연결 — 완료됨(2026-09-21)
`libs/NiceID.jar`가 `build.gradle`에 연결돼 있고 `RealNiceClient`가 `CPClient`를 호출한다(요청번호는 `getRequestNO(사이트코드)`, 시각 검사는 `getCipherDateTime()`. 응답 파싱은 벤더가 아니라 우리 `NicePlaindataCodec`). `NiceClientConfig`는 `mock-enabled`×실연동 설정 4개(`site-code`·`site-password`·`return-url`·`error-url`) fail-closed 가드를 한다. JVM 플래그(`--add-exports java.base/com.sun.crypto.provider=ALL-UNNAMED`)는 `build.gradle`의 `test`·`bootRun` 태스크에 있다(운영 `java -jar` 실행 스크립트에도 필요). 상세는 아래 "함정·결정"의 "jar 실측"·"연동 방식 확인분" 참고. 응답 키는 2026-09-22 실응답으로 확인됐다. 남은 일은 `getCipherDateTime()` 포맷 확인뿐이다(파싱 실패 WARN 로그가 없는지 본다(키가 다르면 `NiceVerificationService.required`의 WARN 로그에 실제 키 목록이 남는다).

## 검증

백엔드(`recruit_back/recruit_backend/`에서):

```bash
# Windows PowerShell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.nice.*" --tests "com.shinyoung.recruit.controller.NiceVerificationControllerTest" --tests "com.shinyoung.recruit.config.NiceClientConfigTest" --no-daemon
# Linux
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "com.shinyoung.recruit.service.nice.*" --tests "com.shinyoung.recruit.controller.NiceVerificationControllerTest" --tests "com.shinyoung.recruit.config.NiceClientConfigTest" --no-daemon
```

현재 통과 수(`--rerun`으로 확인, 2026-09-21 최종 리뷰 반영 후):

| 클래스 | 건수 |
|---|---|
| `NiceClientConfigTest` | 7 |
| `NiceVerificationControllerTest` | 11 |
| `MockNiceClientTest` | 7 |
| `NicePlaindataCodecTest` | 7 |
| `NicePlaindataCodecVendorCompatibilityTest` | 2 |
| `NiceVerificationConcurrencyTest` | 5 |
| `NiceVerificationServiceCallbackTest` | 8 |
| `NiceVerificationServiceExchangeTest` | 9 |
| `NiceVerificationServiceRequestTest` | 5 |
| `NiceVerificationStoreTest` | 9 |
| `RealNiceClientTest` | 8 |
| 이 카드 소유 범위 합계 | **78** |

가입 연동([auth-account](auth-account.md) 소유·검증 명령): `ApplicantSignUpControllerTest` 9 + `ApplicantSignUpNiceIntegrationTest` 7 + `ApplicantSignUpServiceTest` 10 + `SecurityConfig` 37 + `RecruitApplicationTests` 1 = 64.

전체 합계 78 + 64 = **142**(… 개발 서버 Origin 추가 후 139, 식별 기준 교체 후 142). 식별 키 테스트는 `AuditHmacTest`(13건)에 있다.

프론트(`recruit_front/`에서): `npm run type-check`.

## 함정·결정

**외부 실인증에서 콜백이 `405`였다(2026-09-22) — NICE는 결과를 GET 쿼리로 돌려준다.** 콜백이 `@PostMapping`이라 거부됐다. 팝업 주소창에 `?EncodeData=`가 붙어 있어 GET으로 확정했다(POST가 리다이렉트로 GET이 된 경우라면 쿼리 없이 본문이 사라진다). GET·POST 둘 다 받게 고치고 `+` 복원·`EncodeData` 없음 처리를 넣었다. `NiceVerificationControllerTest` GET 4건, `SecurityConfigTest` GET 1건 추가. 변이 검증: `+` 복원을 빼면 해당 테스트만 실패한다.

**그 직전 외부 접속의 첫 `403`은 원인이 확정되지 않았다(2026-09-22).** 처음엔 콜백이 CORS에 막힌 것으로 진단해 콜백 CORS 예외를 넣었지만, NICE가 GET으로 돌려주고 GET 이동엔 `Origin`이 없으므로 **콜백 CORS는 그 403의 원인이 아니었다.** 가장 유력한 것은 아래 항목 — 허용 목록 밖 주소로 접속해 `POST /auth/nice/request`가 막힌 경우다. 외부 테스트에 쓴 개발 서버 주소 `https://shinrecruitdev.shinyoung.com`이 목록에 없어 추가했다(`SecurityConfigTest`로 고정). 콜백 CORS 예외는 POST 콜백을 위해 유지한다. 콜백 CORS 예외 테스트: `SecurityConfigTest`에 NICE Origin·`null` Origin POST 콜백 3건과, 예외가 좁은지 보는 1건(`request`는 NICE Origin을 계속 거부)이 있다. 변이 검증: 예외 경로 목록을 비우면 콜백 3건만 실패한다.

**리버스 프록시 뒤에서는 same-origin 요청도 CORS 판정을 받는다.** 프록시 헤더 처리(`server.forward-headers-strategy`) 설정이 없어 Spring은 자기 주소를 프록시 내부 주소로 안다. 그래서 브라우저가 `Origin`을 붙이는 요청(모든 POST)은 전부 cross-origin으로 판정되고, 접속 주소가 허용 목록(`http://localhost:5173`, `https://rec.shinyoung.com`, `https://shinrecruitdev.shinyoung.com`)에 **정확히** 없으면 403이다 — 테스트 도메인·IP·`http://` 전부 해당. GET은 same-origin이면 `Origin`이 안 붙어 통과하므로 증상이 POST에만 나타난다.

**최종 코드 리뷰가 결함 8건을 찾아 전부 고쳤다(2026-09-21, 테스트 106 → 128).** 순차 시나리오만 보던 테스트가 놓친 경쟁 조건 3건과, 리뷰가 직접 찾은 정보 노출·설정 4건, 구현 중 자체 발견 1건이다.

| 구분 | 결함 | 조치 |
|---|---|---|
| 동시성 | `REQ_SEQ` 충돌 — 모듈 값이 `사이트코드_밀리초+random%100`이라 순차 2,000회 중 1,765건 중복. `save`가 덮어써 앞사람 요청이 사라짐 | `saveIfAbsent` + 최대 5회 재발급, 초과 시 거부 |
| 동시성 | 콜백 동시 재전송(check-then-act 경쟁으로 둘 다 통과) | `compareAndSet` |
| 동시성 | 결과 교환 동시 호출(같은 토큰 2회 성공) | `takeByResultToken`(`remove(key, value)`) |
| 정보 노출 | NICE 원시 오류코드(`code=-6` 등)가 응답에 노출 | 로그에만, 응답은 고정 문구 |
| 정보 노출 | 원인별 메시지("다른 세션"·"이미 처리된" 등)가 상태코드는 통일했는데 메시지로 원인이 드러남 | 파이프라인 실패 문구 하나로 통일, 원인은 로그(`warn`/`info`). 가입 시점 `requireFresh` 안내는 유지 |
| 정보 노출 | 콜백 예외 시 팝업에 원시 400 JSON, 부모창에 실패 미전달 | 예외를 잡아 토큰 없이 `303` → 결과 화면이 FAIL 전달 |
| 설정 | 시간대 `systemDefault` — 운영 JVM이 UTC면 전 인증이 만료로 거부(KST인 개발 PC에서는 숨는 버그) | `Asia/Seoul` 고정 |
| 설정 | fail-closed 가드가 `site-code`만 봄 | 실연동 시 `site-code`·`site-password`·`return-url`·`error-url` 4개 모두 필수 |
| (자체 발견) | 코덱이 UTF-8 — NICE는 EUC-KR | EUC-KR로 교체, 벤더 `fnParse` 교차 검증 테스트 추가 |

**동시성 테스트는 `CyclicBarrier`로 경쟁을 결정적으로 재현한다**(`NiceVerificationConcurrencyTest`, 5건). 스레드 두 개를 그냥 띄우면 대부분 순차로 돌아 우연히 통과한다. `Store`를 상속해 읽기 메서드 안에서 두 스레드를 만나게 해, 둘 다 읽기를 마친 뒤 쓰기로 넘어가게 한다.

**jar 실측** — `RealNiceClient` 구현·수정 시 참고한다.

- **JVM 플래그가 필수다.** `CPClient` 생성자가 JDK 내부 클래스 `com.sun.crypto.provider.SunJCE`를 직접 만든다. `--add-exports java.base/com.sun.crypto.provider=ALL-UNNAMED` 없이는 Java 9+에서 `IllegalAccessError`로 죽는다. `build.gradle`의 `test`·`bootRun` 태스크에 있다(운영 `java -jar` 실행 스크립트에도 필요).
- **모듈 자체 암호문 왕복이 불가능하다.** `fnEncode`한 것을 `fnDecode`하면 항상 `-6`(`DEC_DATA_ERR`). 요청 암호문 헤더에 버전·사이트코드가 평문으로 실려 있어 **NICE가 푸는 요청 포맷과 우리가 푸는 응답 포맷이 다르다.** **복호화 경로는 로컬 검증 수단이 없다** — 첫 검증은 배포 후 실인증 1회다. **직렬화(요청 평문 조립)는 `NicePlaindataCodecVendorCompatibilityTest`가 실제 `CPClient.fnParse`로 교차 검증한다** — "복호화·파싱 둘 다 로컬 검증 불가"가 아니라 이 둘로 나뉜다.
- **메서드 시그니처**: `getRequestNO()`다(**대문자 `NO`**). `RealNiceClient.generateRequestNo()`는 `new CPClient().getRequestNO(properties.getSiteCode())`로 **사이트코드를 인자로 넘긴다** — `{BE}/service/nice/NiceClient.java`의 인터페이스 javadoc은 "길이·문자 규칙은 NICE가 정한다"고만 적고 인자 여부를 명시하지 않아 혼동 여지가 있다(무관한 결함이라 이 카드 갱신에서는 고치지 않고 보고만 한다). `getCipherDateTime()`은 인자가 없고 `fnDecode`가 채운 인스턴스 상태를 읽는다 — 그래서 `NiceDecodeResult`가 평문과 생성 시각을 함께 돌려주는 값 객체다. `fnParse`는 raw `HashMap`을 준다 — **운영 코드는 `fnParse`를 쓰지 않는다**(아래 "알려진 한계"의 파서 교체). 교차 검증 테스트만 호출한다.
  - 오류 코드: `0` 성공, `-1` AES_SYSTEM, `-2` ENC_PROC, `-3` ENC_DATA, `-4` DEC_PROC, `-5` DEC_HASH, `-6` DEC_DATA, `-7`·`-8` CIPHER_VERSION, `-9` INPUT_DATA, `-12` PWD_MISMATCH. 전부 로그에만 남고 응답 문구는 고정이다.

**평문 직렬화는 EUC-KR 바이트 길이다(구현 후 실측으로 정정 — 설계 초안은 UTF-8로 적었다).** 벤더 모듈이 `"euc-kr"`을 하드코딩한다(바이트코드 확인). `홍길동`은 EUC-KR 6바이트(UTF-8이면 9 — 벤더 파서가 `null`을 돌려준다). `NicePlaindataCodecVendorCompatibilityTest`가 우리 `codec.encode` 결과를 실제 `CPClient.fnParse`에 넣어 같은 필드가 나오는지 본다 — **직렬화 호환성은 이렇게 검증된다.** 우리 코덱이 운영 파서가 된 뒤로 이 테스트는 **운영 파서가 NICE 규격에서 벗어나지 않게 지키는 가드**다. 복호화는 여전히 로컬 검증 불가(위 "모듈 자체 암호문 왕복이 불가능하다").

**알려진 한계 2건**

- **희귀 한글**: 벤더가 `euc-kr`을 쓰므로 EUC-KR 2,350자(KS X 1001 완성형) 밖의 글자(`똠`·`뷁` 등)는 `?`가 된다(`김똠뷁` → `김??`). 벤더 한계이고 레거시도 같다. 해당 이름은 `?`가 섞여 저장된다.
- **벤더 `fnParse`를 쓰지 않는다 — 해결됨**(2026-09-21 사용자 결정). `fnParse`는 파싱에 실패하면 입력 조각과 스택트레이스를 stdout/stderr에 직접 찍는다(실측: `ArrayIndexOutOfBoundsException`·`NumberFormatException`과 입력 조각). 실응답 형식이 어긋나면 이름·생년월일 조각이 콘솔 로그로 나갈 수 있었고, 그 상황이 가장 일어날 법한 때가 검증 수단이 없는 배포 후 첫 실인증이다. 게다가 쓰레기 입력에 예외 없이 `null`을 돌려주기도 한다. 그래서 `RealNiceClient.parse`를 우리 `NicePlaindataCodec`으로 바꿨다 — 실패 시 예외만 던지고 아무것도 출력하지 않는다. **예외를 cause로 넘기지 않는다** — 코덱 예외 메시지에 입력 조각이 들어 있어 상위 스택트레이스로 샐 수 있다. `RealNiceClientTest`가 파싱 실패 시 stdout·stderr에 평문 조각이 없는지 캡처해 검증한다(변이 검증: `fnParse`로 되돌리면 실패).

**연동 방식 확인분**(2026-09-20 레거시 확인, 2026-09-21 실연동 구현으로 확정): **체크플러스(CheckPlus) 본인확인 표준창**이다. 인증 키는 **사이트코드 + 사이트패스워드**, 암복호화는 **NICE가 제공한 모듈**(`NiceID.jar`, 클래스 `NiceID.Check.CPClient`)을 쓴다(직접 구현 아님). **평문 키 7개·순서 확정**: `REQ_SEQ`·`SITECODE`·`AUTH_TYPE`(빈 값)·`RTN_URL`·`ERR_URL`·`POPUP_GUBUN`(`"N"`)·`CUSTOMIZE`(빈 값). 레거시는 응답 파싱에 모듈의 `fnParse`를 쓰지만 **우리는 쓰지 않는다**(아래 "알려진 한계" — stdout 유출).
- **모듈은 `NiceID.jar` 7KB 단독이다. 네이티브(.dll/.so) 동반 파일이 없다. Java 17 호환 확인됨**(레거시가 Spring Boot + Java 17로 운영 중, 2026-09-21). 공개 저장소에는 없어 저장소에 두었고(`recruit_back/recruit_backend/libs/NiceID.jar`) `build.gradle`의 `implementation fileTree('libs') { include '*.jar' }`로 연결했다(libs/ 의 jar 전부 참조).
- **호출 흐름**(레거시 `checkplus_*.jsp` 확인, `NiceVerificationService`가 동일하게 따른다). ① 평문 `sPlaindata` 조립(`키+길이+값`) ② `fnEncode`→`EncodeData` ③ hidden `encData`로 표준창 POST ④ 인증 후 NICE가 사용자 브라우저를 암호문 속 `RTN_URL`(실패 `ERR_URL`)로 보낸다(**GET 쿼리** — 2026-09-22 확인) ⑤ `fnDecode`로 이름·생년월일·성별·휴대폰 추출(이 계약엔 CI·DI 없음).
- **리턴 URL은 코드가 아니라 암호문(`EncodeData`) 안에 들어간다.** 결과 회신은 사용자 브라우저의 GET 이동이다 — NICE 서버가 우리 서버를 직접 호출하지 않는다. **GET이라 `EncodeData`가 웹서버·프록시 접근 로그에 남는다.** 사이트 패스워드로 복호화 가능한 암호문(이름·생년월일·성별·휴대폰)이므로 접근 로그의 보관 기간·열람 권한을 관리해야 한다(레거시도 같다). `REQ_SEQ` 1회용이라 로그에서 꺼내 재전송해도 통과하지 않는다. 네트워크가 필요한 곳은 사용자 브라우저 → NICE 아웃바운드뿐이다.
- **레거시는 사이트코드·사이트패스워드를 소스에 문자열로 박아 뒀다. 그대로 옮기지 않는다.** 운영 자격증명은 `AES_SECRET_KEY`와 같이 환경변수로 주입한다.
- **레거시에는 `REQ_SEQ` 저장·대조가 없었다**(2026-09-21 확인) — `RTN_URL`로 오는 `EncodeData`가 만료·1회성 없이 캡처 한 번으로 영구 재사용됐고, 흐름 구분도 없어 가입용 인증 결과를 다른 흐름에 밀어 넣을 수 있었다. 신규 구현이 해소한 방식은 위 "규칙·불변식" 참고.
- **평문 조립은 `키 + 바이트길이 + 값`을 이어 붙인 직렬화 규격이다(NICE 원 스펙, 레거시 확인).** 길이는 문자 수가 아니라 **EUC-KR** 바이트 길이다(위 "평문 직렬화는 EUC-KR 바이트 길이다" 참고 — 설계 초안은 UTF-8/`getBytes().length`로 적었으나 벤더 모듈이 `euc-kr`을 하드코딩한다). 우리 `NicePlaindataCodec`의 실제 구현은 `키바이트길이:키 값바이트길이:값`(콜론 구분자 + 키 길이도 포함) 형식이라 NICE 원 스펙 문구와 완전히 같지는 않지만, **직렬화 결과가 벤더 파서와 호환되는지는 `NicePlaindataCodecVendorCompatibilityTest`로 검증됐다.** 복호화 왕복은 여전히 로컬 검증 불가(위 "모듈 자체 암호문 왕복이 불가능" 항목과 같은 이유).
- **`param_r1`~`param_r3`**: 업체 지정 데이터 왕복 슬롯이다. 인증 결과와 함께 그대로 돌아온다(`REQ_SEQ` 대조와는 별개 채널). 현재 코드는 쓰지 않는다.
- **팝업↔부모창**: 레거시는 `window.opener` 직접 참조였다. 가입 실연동은 `postMessage` + origin 검증으로 구현했다([auth-account](auth-account.md) "규칙·불변식 > 프론트"). 콜백 해제 규칙([auth-account](auth-account.md) "함정·결정 > 전역 본인인증 콜백")은 그대로 지킨다.
- **실응답 키 확인됨**(2026-09-22): `REQ_SEQ, RES_SEQ, AUTH_TYPE, NAME, BIRTHDATE, GENDER, NATIONALINFO, MOBILE_NO, UTF8_NAME` — **CI·DI·`MOBILE_CO` 없음**. `NiceVerificationService.required`의 WARN 로그(수신 키 목록)로 찾았다. 코드 흔적만 보고 레거시가 CI를 받아서 버린다고 여겼으나 애초에 받지 않았다. `UTF8_NAME`은 희귀 한글(`?` 깨짐) 문제를 풀 후보다(형식 미확인).
- **`RealNiceClient` 구현 완료, 실검증 미완**: `libs/NiceID.jar`를 `build.gradle`에 연결했고 `NiceClientConfig`에 `mock-enabled`×실연동 설정 4개 fail-closed 가드가 있다(위 "결함 8건" 표). 다만 **복호화는 여전히 로컬 검증이 불가능**하다(자체 왕복이 항상 `-6`) — 직렬화만 벤더 파서 교차 검증으로 커버된다. `RealNiceClientTest`는 모듈 로딩·암호화·KST 시각 파싱·파싱 실패 시 평문 미노출을 본다 — 주 목적은 JVM 플래그 누락을 잡는 것이다.
- **`MockNiceClient`는 jar 비의존**(Base64 가역 인코딩, `MOCK.<epoch초>.<Base64>` 형식). 그래서 jar 없이 파이프라인 전체를 만들고 검증했고, 자동 테스트도 벤더 jar에 묶이지 않는다.

**NICE 실연동은 세 흐름(① 가입 ② 이메일 찾기 ③ 비밀번호 재설정)의 공통 기반이다**(2026-09-20 정리, 2026-09-21 가입분 구현 완료). **가입이 먼저였다** — 중복 차단(이름+생년월일+성별)이 살아야 한 사람에 계정 1개가 보장되고, 그래야 이메일 찾기 결과가 1건으로 확정된다. 나머지 둘은 `NiceVerificationPurpose`에 값을 추가해 같은 검증 지점(`NiceVerificationService`)에 얹는다(범위 밖, 미착수 — 위 "변경 레시피").

**오류 응답이 설계 초안과 다르다(코드 확인, 2026-09-21 최종 리뷰 반영 후에도 유지)**: 설계 문서(`docs/archive/superpowers/specs/2026-09-21-nice-verification-signup-design.md`)는 `resultToken` 문제·세션 불일치를 `403`+감사 로그로 분리할 계획이었다. 이 403+감사 로그 분리안은 채택되지 않았다 — 실제 구현은 `NiceVerificationException` 하나로 합쳐 사용자 문구를 통일했다. 최종 리뷰로 바뀐 것은 상태코드가 아니라 **콜백 두 개의 실패 응답 형태**다: `result`(same-site)는 리뷰 전후 모두 **400 JSON**, `callback`·`callback/error`(cross-site 이동)는 리뷰 전 400 JSON → 리뷰 후 **303**(토큰 없이, 위 API 계약 상세)으로 바뀌었다. 세션 불일치는 `ActivityLog`(감사 로그, [privacy-audit-audit](privacy-audit-audit.md))가 아니라 SLF4J `log.warn`으로만 남는다. 토큰 탈취 시도를 감사 로그로 추적하려면 별도 작업이 필요하다.
