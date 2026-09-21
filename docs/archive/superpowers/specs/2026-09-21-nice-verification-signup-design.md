# NICE 본인확인 실연동(가입 흐름) 설계

**작성일**: 2026-09-21
**대상 카드**: `docs/domains/auth-account.md`
**범위**: NICE 본인확인 공통 계층 + 지원자 가입 흐름 연동. 이메일 찾기·비밀번호 재설정은 범위 밖.

---

## 1. 목표

지원자 가입 시 본인확인을 **실제 NICE 체크플러스로 수행**하고, 서버가 그 결과를 복호화·검증해 확보한 CI로 계정을 만든다.

현재는 프론트 목업이 `crypto.randomUUID()`로 CI를 만들어 보내고 서버가 그대로 믿는다. 그래서 **가입 시 본인 확인이 사실상 없고, `ciHash` 유니크 제약이 있어도 중복 차단이 동작하지 않는다**(매번 다른 UUID라 항상 통과). 이 설계는 그 결함을 구조적으로 제거한다.

**성공 기준**

1. 가입 요청 본문에 `ci`가 존재하지 않는다. 서버는 자신이 복호화한 값만 쓴다.
2. 동일인이 두 번 가입하면 `ciHash` 충돌로 거부된다.
3. 캡처된 NICE 응답을 재전송하면 거부된다.
4. 운영 설정에서 Mock 경로로 가입할 수 없다(기동 자체가 실패한다).
5. 실제 NICE 호출 없이 **우리 로직**(요청 발급·재전송 방어·세션 대조·리다이렉트·가입 연동)이 자동 테스트로 검증된다. 모듈의 복호화·파싱 자체는 검증 수단이 없다(2장 파생 제약).

---

## 2. 확인된 사실과 제약

레거시(현행 운영 시스템)를 직접 확인해 얻은 사실이다.

| 항목 | 확인분 |
|---|---|
| 방식 | 체크플러스(CheckPlus) 본인확인 표준창. 팝업 → 통신사 PASS → 리턴 URL |
| 모듈 | `NiceID.jar` **7KB 단독**. 네이티브(`.dll`/`.so`) 동반 파일 없음. 클래스 `NiceID.Check.CPClient` |
| Java 17 | 클래스 파일 major 46(Java 1.2)이라 로딩은 된다. 다만 **JVM 플래그 `--add-exports java.base/com.sun.crypto.provider=ALL-UNNAMED` 가 필수**다(아래 파생 제약). 레거시도 기동 스크립트에서 이 플래그를 쓴다 |
| 인증 키 | 사이트코드 + 사이트패스워드. 레거시는 **소스에 문자열로 하드코딩** |
| 평문 형식 | `키 + 바이트길이 + 값`을 이어 붙인 직렬화. 길이는 **EUC-KR 바이트 길이**다(구현 후 실측으로 정정 — 초안은 UTF-8로 적었다). 벤더 모듈이 `"euc-kr"`을 하드코딩한다. 레거시의 `getBytes()`(문자셋 미지정)가 한국어 서버의 플랫폼 기본값을 썼던 것이다. `홍길동`은 EUC-KR 6바이트(UTF-8이면 9 — `fnParse`가 거절한다) |
| 리턴 URL | 평문에 `RTN_URL`·`ERR_URL`로 들어가 **암호문 안에 포함**된다. 코드 참조가 아니다 |
| 결과 회신 | NICE가 팝업에 내려준 **자동 submit 폼**이 리턴 URL로 POST한다. **NICE 서버가 우리 서버를 직접 호출하지 않는다** |
| `REQ_SEQ` 생성 | **모듈이 만든다** — `CPClient.getRequestNO(사이트코드)`. **대문자 `NO`** 다. 반환 예: `<사이트코드>_2026092110561784896` |
| `REQ_SEQ` 대조 | **레거시에 저장·대조 로직이 없다.** 발급만 하고 응답값을 버린다 → 재전송 방어가 없다 |
| 사이트코드 | **운영용 1개뿐.** 개발·스테이징용 별도 코드 없음 |

**파생 제약**

- **로컬에서 실제 NICE 왕복 불가.** 리턴 URL이 운영 주소로만 등록돼 있다.
- **자체 암호문 왕복은 불가능하다**(2026-09-21 실측으로 정정). 같은 사이트코드·패스워드로 `fnEncode`한 것을 `fnDecode`하면 항상 `-6`(`DEC_DATA_ERR`)이다. 요청 암호문 헤더에 버전과 사이트코드가 평문으로 실려 있어 **NICE가 푸는 요청 포맷과 우리가 푸는 응답 포맷이 다르다.** 설계 초안은 이것이 가능하다고 적었으나 틀렸다.
  - 결과: **복호화·파싱 경로는 로컬에서 검증할 수단이 없다.** 첫 검증은 배포 후 실인증 1회다.
  - 단, `MockNiceClient`를 jar와 무관하게 만들었으므로 우리 로직(요청 발급·재전송 방어·세션 대조·컨트롤러·가입 연동)의 테스트는 전부 유효하다.
- **모듈은 Java 9+ 에서 JVM 플래그 없이 동작하지 않는다**(2026-09-21 실측). `CPClient` 생성자가 JDK 내부 클래스 `com.sun.crypto.provider.SunJCE`를 직접 만들어 `IllegalAccessError`가 난다. `--add-exports java.base/com.sun.crypto.provider=ALL-UNNAMED` 가 **필수**다. 레거시 기동 스크립트도 이 플래그를 쓴다(확인). `bootRun`·`gradle test`·운영 `java -jar` 전부에 걸어야 한다.
- **네트워크는 사용자 브라우저 → NICE 아웃바운드만 필요하다.** NICE → 우리 쪽 인바운드 경로가 필요 없다.
- 이 프로젝트에는 Spring 프로파일이 하나도 없다(`application.yaml` 단일). 환경 분기는 **설정값**으로만 한다.
- 세션 저장소 설정이 없다. 기본 인메모리 HTTP 세션이며 **단일 인스턴스 전제**다.
- CSRF는 전역 `disable` 상태다(`SecurityConfig`). 콜백용 별도 예외가 필요 없다.

---

## 3. 결정적 제약 — 콜백에 세션 쿠키가 오지 않는다

NICE → 우리 콜백은 **cross-site POST**다. 세션 쿠키의 `SameSite`는 브라우저 기본이 `Lax`이고, `Lax`는 **top-level GET에만** 쿠키를 싣는다. 따라서 **콜백 시점에 `JSESSIONID`가 도착하지 않는다.**

`SameSite=None`으로 푸는 선택은 하지 않는다. 전역 CSRF 표면을 넓히는 대가가 이 기능 하나보다 크다.

**대신 콜백이 세션에 의존하지 않게 설계한다.** 콜백은 `REQ_SEQ`만으로 서버 저장소를 찾고, 세션에 값을 심는 일은 그 뒤 **same-site 요청**으로 미룬다. 아래 4장 `[5]`단계가 그 역할이다.

---

## 4. 전체 흐름

```
[1] 가입 화면에서 "본인 인증" 클릭
    window.open('/nice-auth', 'Nice-Auth', ...)

[2] 팝업 마운트 → POST /api/auth/nice/request        (same-site, 세션 O)
    서버: reqSeq 발급 — CPClient.getRequestNO(사이트코드)
          Store 저장 { reqSeq, purpose=SIGNUP, sessionId, issuedAt, status=PENDING }
          평문 조립(REQ_SEQ·SITECODE·AUTH_TYPE·RTN_URL·ERR_URL·POPUP_GUBUN)
          fnEncode → { encodeData } 반환

[3] 팝업이 폼 생성 후 POST                            (사용자 브라우저 → NICE)
    action = https://nice.checkplus.co.kr/CheckPlusSafeModel/checkplus.cb
    hidden  = m=checkplusSerivce, EncodeData

[4] 인증 완료 → NICE 자동 submit 폼
    POST /api/auth/nice/callback                      (cross-site, 세션 X)
    서버: fnDecode(EncodeData)
          getCipherDateTime() 으로 암호문 생성 시각 검사(인자 없음, fnDecode 후 상태)
          reqSeq 추출 → Store 조회
            없음/만료/status!=PENDING → FAIL 처리
          결과(name, phoneNumber, ci) 레코드에 저장, status=VERIFIED
          1회용 resultToken 발급(TTL 1분)
          303 See Other → /nice-auth/result?token=...

[5] 팝업이 Vue 결과 라우트 로드                        (same-site GET, 세션 O)
    POST /api/auth/nice/result { token }
    서버: token 으로 Store 조회
          Store 의 sessionId 와 현재 세션 id 대조 → 불일치면 400
          세션에 NICE_VERIFIED 저장 { purpose, name, phoneNumber, ci, verifiedAt }
          Store 레코드 폐기
          { status:'SUCCESS', name, phoneNumber } 반환   ← ci 미포함

[6] 팝업 → window.opener.postMessage(payload, window.location.origin)
    → window.close()

[7] 부모(SignupView)가 message 수신
    event.origin 검증 → 이름·휴대폰을 읽기 전용으로 표시

[8] 가입 제출 → POST /api/auth/applicants/sign-up
    서버: 세션 NICE_VERIFIED 조회(purpose=SIGNUP, 미만료)
          없으면 400
          name·phoneNumber·ci 를 세션 값으로 사용
          ciHash 중복 검사 → 저장
          세션 NICE_VERIFIED 제거
```

**실패 경로**: 사용자가 취소하거나 NICE가 실패를 주면 `ERR_URL`(`POST /api/auth/nice/callback/error`)로 온다. Store 레코드를 `FAIL`로 바꾸고 동일한 결과 라우트로 `303`한다. 팝업은 `status='FAIL'`을 `postMessage`하고 닫힌다.

**CI는 브라우저에 한 번도 내려가지 않는다.** 이것이 이 설계의 핵심 보안 속성이다.

---

## 5. 컴포넌트

### 5.1 백엔드 신규

| 파일 | 책임 |
|---|---|
| `{BE}/config/NiceProperties.java` | `nice.*` 설정 바인딩 |
| `{BE}/config/NiceClientConfig.java` | `NiceClient` 빈 선택 + **fail-closed 가드** |
| `{BE}/service/nice/NiceClient.java` | 인터페이스. `generateRequestNo()` / `encode(String)` / `decode(String)` → `NiceDecodeResult(평문, 생성시각)` / `parse(String)` |
| `{BE}/service/nice/RealNiceClient.java` | `NiceID.Check.CPClient` 호출. 반환 코드 != 0 이면 예외 |
| `{BE}/service/nice/MockNiceClient.java` | **jar에 의존하지 않는** 가역 인코딩으로 동일 인터페이스 구현. 개발·테스트 전용 |
| `{BE}/service/nice/NicePlaindataCodec.java` | `키+바이트길이+값` 조립·파싱. **순수 로직, 의존성 없음** |
| `{BE}/service/nice/NiceVerificationStore.java` | 인메모리 `ConcurrentHashMap` + TTL 만료. 상태 전이는 **원자 연산만** 쓴다 — `saveIfAbsent`(REQ_SEQ 충돌 감지) · `compareAndSet`(PENDING→VERIFIED CAS) · `takeByResultToken`(`remove(key, value)`로 먼저 꺼낸 쪽만 승리). `find`→검사→`save`는 check-then-act 경쟁이라 동시 재전송을 막지 못한다(최종 리뷰에서 재현) |
| `{BE}/service/nice/NiceVerificationRecord.java` | Store 레코드(값 객체). 엔티티 아님 |
| `{BE}/service/nice/NiceVerificationService.java` | reqSeq 발급 · 콜백 검증 · token 교환 · 세션 소비 |
| `{BE}/controller/NiceVerificationController.java` | 엔드포인트 4개 |
| `{BE}/enumeration/NiceVerificationPurpose.java` | `SIGNUP`. 다음 흐름은 값만 추가 |
| `{BE}/dto/response/NiceRequestResponse.java` | `{ encodeData }` |
| `{BE}/dto/request/NiceResultRequest.java` | `{ token }` |
| `{BE}/dto/response/NiceResultResponse.java` | `{ status, name, phoneNumber }` |
| `recruit_back/recruit_backend/libs/NiceID.jar` | 모듈 원본 |

**`NicePlaindataCodec` 분리 이유**: 직렬화 규격은 순서·바이트 길이가 틀리면 NICE가 실패 코드만 준다. 모듈·네트워크·세션과 무관한 순수 함수로 떼어 두면 한글 포함 케이스까지 단독 테스트로 못 박을 수 있다.

**`REQ_SEQ`는 모듈이 만든다.** `CPClient.getRequestNO(사이트코드)`가 값을 돌려주므로 우리는 길이·문자 규칙을 정하지 않고 받은 값을 Store 키로 쓴다. 그래서 `NiceClient`에 `generateRequestNo()`를 둔다 — 모듈 호출을 인터페이스 뒤로 감춘다.

**`MockNiceClient`는 jar에 의존하지 않는다.** jar 확보 전에 나머지 전부를 만들고 검증할 수 있어야 했고, 실측 결과 **모듈로는 자체 왕복 테스트가 불가능하다**(2장). 두 이유 모두 Mock을 jar와 분리해야 한다는 결론으로 모인다. `NiceClient` 계약은 "`encode`가 불투명 문자열을 주고 `decode`가 원래 평문을 돌려준다"뿐이므로, Mock은 가역 인코딩(Base64) + 생성 시각 접두로 충족한다.

그래서 **jar가 필요한 파일은 `RealNiceClient` 하나뿐**이다. 나머지 구현과 테스트 전부가 jar 없이 진행된다. 부수 효과로 **자동 테스트가 벤더 jar에 묶이지 않는다.** Mock으로 검증되지 않는 것은 `CPClient` 자체의 복호화·파싱이고, 그건 **어떤 방법으로도 로컬 검증이 불가능하다**(자체 왕복 불가). 배포 후 실인증 1회가 유일한 검증 경로다.

**평문 키 이름은 발명하지 않는다.** 요청 키(`REQ_SEQ`·`SITECODE`·`AUTH_TYPE`·`RTN_URL`·`ERR_URL`·`POPUP_GUBUN`)와 응답 키(이름·휴대폰·CI·`REQ_SEQ`에 해당하는 키)의 **정확한 철자와 순서는 레거시 인코딩 JSP·성공 JSP에서 그대로 복사한다.** 구현 첫 단계가 이 복사다.

### 5.2 백엔드 수정

| 파일 | 변경 |
|---|---|
| `{BE}/dto/request/ApplicantSignUpRequest.java` | `ci`·`name`·`phoneNumber` 필드 **제거**. 서버가 세션 값을 쓴다 |
| `{BE}/controller/ApplicantSignUpController.java` | `NiceVerificationService.consumeVerified(SIGNUP)` 호출 → 결과를 서비스에 전달 |
| `{BE}/service/ApplicantSignUpService.java` | 인증 결과를 인자로 받아 `Applicant` 생성. 세션을 직접 보지 않는다 |
| `{BE}/config/SecurityConfig.java` | `/api/auth/nice/**` permitAll 명시(가입 전이라 미인증 접근) |
| `build.gradle` | `implementation files('libs/NiceID.jar')` |
| `application.yaml` | `nice:` 블록 |

**서비스가 세션을 보지 않는 이유**: `ApplicantSignUpService`는 트랜잭션 경계이자 도메인 로직이다. `HttpSession`을 주입하면 단위 테스트가 서블릿 컨테이너에 묶인다. 컨트롤러가 세션에서 꺼내 값으로 넘긴다.

### 5.3 프론트

| 파일 | 변경 |
|---|---|
| `{FE}/views/auth/pop-up/NiceAuthPopup.vue` | 목업 UI(통신사 버튼·이름·전화번호 입력) **제거**. 마운트 시 `request` 호출 후 NICE로 폼 POST. 화면은 진행 안내만 |
| `{FE}/views/auth/pop-up/NiceAuthResult.vue` | **신규**. `result` 호출 → `postMessage` → `window.close()` |
| `{FE}/routes/authRoutes.ts` | `/nice-auth/result` 라우트 추가 |
| `{FE}/views/applicant/SignupView.vue` | `window.phoneAuthCallback` 전역 → `message` 리스너. 이름·휴대폰 읽기 전용. `form.ci` 제거 |
| `{FE}/api/auth/niceApi.ts` | **신규**. `requestNiceAuth()` / `fetchNiceResult(token)` |
| `{FE}/types/auth/nice.ts` | **신규**. 응답 타입 |

**`postMessage` 페이로드**

```ts
{ source: 'nice-auth', status: 'SUCCESS' | 'FAIL', name?: string, phoneNumber?: string }
```

수신측은 `event.origin !== window.location.origin`이면 **무시한다**. `event.data?.source !== 'nice-auth'`도 무시한다(다른 라이브러리의 메시지와 섞이지 않게).

**기존 콜백 해제 규칙 유지**: 화면을 떠날 때 자기가 등록한 리스너만 `removeEventListener`로 해제한다. 무조건 지우면 다른 화면의 리스너가 사라진다(카드 "전역 본인인증 콜백" 항목, 커밋 `86d12c9`).

---

## 6. API 계약

카드 `## API 계약` 표에는 🟡로 먼저 올리고 구현·검증 후 🟢로 바꾼다.

| 메서드 | 경로 | 권한 | 요청 | 응답 |
|---|---|---|---|---|
| POST | `/auth/nice/request` | permitAll | `{ purpose: 'SIGNUP' }` | `{ encodeData }` |
| POST | `/auth/nice/callback` | permitAll | `EncodeData` (form, NICE가 POST) | `303` → `/nice-auth/result?token=...` |
| POST | `/auth/nice/callback/error` | permitAll | `EncodeData` (form) | `303` → `/nice-auth/result?token=...` |
| POST | `/auth/nice/result` | permitAll | `{ token }` | `{ status, name, phoneNumber }` |
| POST | `/auth/applicants/sign-up` | permitAll | **변경**: `ci`·`name`·`phoneNumber` 제거 | 기존과 동일 |

`callback`·`callback/error`는 **`application/x-www-form-urlencoded`를 받는다.** JSON이 아니다.

---

## 7. 데이터 모델

### 7.1 `NiceVerificationRecord` (Store, 인메모리)

| 필드 | 설명 |
|---|---|
| `reqSeq` | 키. **`CPClient.getRequestNo(사이트코드)`가 만든 값**을 그대로 쓴다 |
| `purpose` | `SIGNUP` |
| `sessionId` | `[2]`단계 요청의 세션 id. `[5]`에서 대조 |
| `issuedAt` | 발급 시각 |
| `status` | `PENDING` / `VERIFIED` / `FAIL` |
| `resultToken` | `[4]`에서 발급. 1회용 |
| `name` `phoneNumber` `ci` | `VERIFIED`일 때만 채워짐 |

### 7.2 세션 속성 `NICE_VERIFIED`

| 필드 | 설명 |
|---|---|
| `purpose` `name` `phoneNumber` `ci` `verifiedAt` | 가입 제출 때 소비 후 제거 |

### 7.3 TTL

| 구간 | 값 | 근거 |
|---|---|---|
| 요청 → 콜백 | **10분** | 통신사 인증에 걸리는 시간 |
| `resultToken` | **1분**, 1회용 | 팝업이 즉시 교환한다 |
| 인증 완료 → 가입 제출 | **30분** | 폼 작성 시간 |

만료된 레코드는 스케줄 정리로 제거한다. 만료 판정 자체는 읽는 시점에 하므로 정리가 늦어도 보안에 영향이 없다.

### 7.4 저장소 선택 근거

10분짜리 임시 상태라 **DDL을 추가하지 않는다.** 인메모리 맵으로 충분하다.

**전제: 단일 인스턴스.** 이 시스템은 이미 HTTP 세션이 인메모리라 같은 전제 위에 있다. 다중 인스턴스로 확장하면 세션과 이 Store를 함께 외부 저장소로 옮겨야 한다. 카드 "함정·결정"에 명시한다.

---

## 8. Mock 가드

`application.yaml`의 외부 API 설정은 전부 `recruit.*` 아래에 있다(`recruit.juso`·`recruit.neis`·`recruit.univ-info`). NICE도 같은 자리에 둔다.

```yaml
recruit:
  nice:
    site-code: ${NICE_SITE_CODE:}
    site-password: ${NICE_SITE_PASSWORD:}
    return-url: ${NICE_RETURN_URL:}
    error-url: ${NICE_ERROR_URL:}
    mock-enabled: ${NICE_MOCK_ENABLED:false}
    request-ttl-minutes: ${NICE_REQUEST_TTL_MINUTES:10}
    verified-ttl-minutes: ${NICE_VERIFIED_TTL_MINUTES:30}
```

`NiceClientConfig`가 기동 시 검사한다.

| `mock-enabled` | `site-code` | 결과 |
|---|---|---|
| `false` | 있음 | `RealNiceClient` 등록 |
| `true` | 없음 | `MockNiceClient` 등록 |
| `true` | **있음** | **기동 실패** — 운영 설정에 Mock이 섞였다 |
| `false` | **없음** | **기동 실패** — 실제 경로인데 키가 없다 |

정확히 하나만 성립해야 뜬다. 운영에 우회 경로가 생길 수 없다.

**운영 자격증명은 저장소·문서·커밋에 쓰지 않는다.** 환경변수로만 주입한다. `AES_SECRET_KEY`와 같은 취급이다.

---

## 9. 에러 처리

| 상황 | 서버 | 사용자 |
|---|---|---|
| `reqSeq` 없음·만료·`status!=PENDING` | 레코드 `FAIL` 처리 후 `303` | 결과 화면 실패 → 재인증 안내 |
| `fnDecode` 실패(반환 코드 != 0) | 동일 | 동일 |
| `getCipherDateTime`이 허용 범위 밖 | 동일 | 동일 |
| `resultToken` 없음·만료·재사용 | `400` | 팝업이 `FAIL` 전달 후 닫힘 |
| **세션 id 불일치** | `400` + **`log.warn`** | 동일 |
| 사용자 취소 / NICE 실패 | `ERR_URL` 경로 | 결과 화면 실패 |
| 인증 없이 가입 제출 | `400` | "본인인증을 먼저 진행해주세요" |
| `ciHash` 중복 | 기존 동작(가입 거부) | 기존 메시지 |
| 팝업이 닫혀 응답 없음 | — | **별도 처리 없음.** 본인인증 버튼은 팝업 상태와 무관하게 다시 누를 수 있어 타임아웃이 필요 없다 |

`getCipherDateTime`의 **허용 범위는 요청 TTL과 같은 10분**으로 둔다. `CPClient`가 `yyMMddHHmmss` 문자열을 주므로 `NiceClient` 구현이 epoch 초로 변환한다. **시간대는 `Asia/Seoul` 고정**이다 — NICE 서버 시각은 KST이고, `ZoneId.systemDefault()`로 두면 운영 JVM이 UTC일 때 9시간 어긋나 모든 실인증이 만료로 거부된다(파싱은 성공하므로 fail-open 경로를 타지 않는다). 이 검사는 Store 대조가 어떤 이유로든 뚫렸을 때를 위한 **2차 게이트**다 — 두 검사 중 하나만 걸려도 거부한다.

**검증 실패는 원인과 무관하게 전부 `400` 이다**(구현 후 정정 — 초안은 세션 불일치를 `403` + 감사 로그로 분리했다).

- **상태 코드를 나누지 않는다.** `NiceVerificationException` 의 원칙이 "원인을 구분해 보여주지 않는다"다. 세션 불일치만 `403` 을 주면 재전송을 시도하는 쪽이 "토큰은 유효한데 세션이 다르다"는 정보를 얻는다. `GlobalExceptionHandler` 가 예외 하나를 `400` 하나로 매핑하는 기존 구조와도 맞다.
- **감사 로그(`ActivityLog`) 대신 `log.warn` 이다.** 가입 전 익명 흐름이라 귀속할 행위자가 없고, `ActivityLog` 는 행위자 기반이다. 세션 불일치만 `reqSeq` 와 함께 경고를 남긴다 — token 탈취 시도의 신호이기 때문이다.

---

## 10. 테스트 전략

운영 사이트코드가 1개뿐이라 실제 NICE 왕복은 배포 후에만 가능하다. 그 밖의 전부는 로컬에서 검증한다.

| 대상 | 방법 |
|---|---|
| `NicePlaindataCodec` | 조립·파싱 왕복. **한글 포함 값의 바이트 길이**가 문자 수와 다르게 계산되는지 확인 |
| 콜백 전체 경로 | `MockNiceClient`로 **가짜 NICE 응답 암호문을 만들어 `/callback`에 form POST**. 복호화 → `reqSeq` 대조 → `VERIFIED` 전이 → `303` + token 발급까지 |
| 재전송 거부 | 같은 암호문을 두 번 POST. 두 번째는 `status!=PENDING`으로 거부 |
| 만료 거부 | `issuedAt`을 TTL 밖으로 둔 레코드 |
| token 1회용 | 같은 token으로 `result` 두 번 호출 |
| 세션 대조 | 다른 세션에서 `result` 호출 → `400`, 공격자 세션에 아무것도 안 심김 |
| `NiceClientConfig` 가드 | 4조합 각각 기동 성공/실패 |
| 가입 연동 | 세션 인증분 없이 제출 → `400`. 있으면 `ci`가 세션 값으로 저장되는지 |
| `ciHash` 중복 | 같은 CI로 두 번 가입 → 두 번째 거부 |
| 프론트 | `npm run type-check` |

**미검증으로 남는 것**: NICE 실응답의 필드 구성이 우리 파서 가정과 같은가. 배포 후 1회 실인증으로 확인한다. 이 항목은 카드에 명시하고 넘긴다.

---

## 11. 범위 밖

- **이메일 찾기 · 비밀번호 재설정** — `NiceVerificationPurpose`에 값을 추가하면 얹히도록 열어 두지만 이번에 만들지 않는다.
- **`loginId`/`email` 중복 전송 정리** — 프론트가 `loginId`와 `email`에 같은 값을 보내는 현행 구조는 그대로 둔다. 별도 슬라이스다.
- **관리자·면접관 본인확인** — 대상 아니다.
- **다중 인스턴스 대응** — 현행 세션 전제를 따른다.
- **DI 저장** — CI만 쓴다. DI는 파싱만 하고 버린다(저장 필드를 만들지 않는다).
- **생년월일 저장** — 가입 폼에 없는 값이라 저장하지 않는다.

---

## 12. 구현 순서

1. **레거시 평문 키 철자·순서 복사** — 요청·응답 양쪽. 이게 틀리면 그 뒤가 전부 무의미하다.
2. `NicePlaindataCodec` + 단위 테스트
3. `NiceClient` 인터페이스 · `MockNiceClient` (jar 불필요)
4. `NiceVerificationStore` · `NiceVerificationService`
5. `NiceVerificationController` 엔드포인트 4개 + 통합 테스트
6. 가입 연동 변경(`ApplicantSignUpRequest`·Controller·Service)
7. 프론트: `niceApi.ts` → `NiceAuthPopup.vue` → `NiceAuthResult.vue` → `SignupView.vue`
8. **`RealNiceClient` + `NiceClientConfig` 가드 + `build.gradle` 의존성** — jar 확보 후. 앞 단계와 독립이다
9. 카드 갱신(`API 계약` 🟢 전환, `파일 지도`, `규칙·불변식`) + `node tools/check-docs.mjs`

1~7은 jar 없이 끝낼 수 있다. 8만 `NiceID.jar` 확보와 새 의존성 승인을 기다린다.

---

## 13. 구현 전 확인 항목

| 항목 | 확인처 |
|---|---|
| 평문 키 철자·순서·`AUTH_TYPE`·`POPUP_GUBUN` 지정값 | 레거시 인코딩 JSP/컨트롤러 |
| 응답 필드 키 이름 | 레거시 `checkplus_success.jsp`의 추출부 |
| 신규 리턴 URL 2종 등록 | NICE 관리 화면. 운영 배포 주소 기준 |
| `NiceID.jar` 배치 승인 | 새 의존성. 레거시에서 Java 17로 검증된 동일 파일 |

---

## 14. 최종 리뷰 반영 (2026-09-21)

구현 완료 후 최종 코드 리뷰가 **테스트 106개가 순차 시나리오만 검증해 놓친 결함**을 재현 테스트로 찾았다. 전부 고쳤다.

| 구분 | 결함 | 조치 |
|---|---|---|
| Important | **`REQ_SEQ` 충돌** — 모듈 값이 `사이트코드_밀리초+random%100`이라 순차 2,000회 중 1,765건 중복. `save`가 덮어써 앞사람 요청이 사라짐 | `saveIfAbsent` + 최대 5회 재발급, 초과 시 거부. Mock은 UUID라 테스트가 못 잡았다 |
| Important | **콜백 동시 재전송** — check-then-act 경쟁으로 둘 다 통과 | `compareAndSet` CAS. 세션 결속이 있어 탈취는 불가했지만 "1회용" 불변식이 깨졌다 |
| Important | **결과 교환 동시 호출** — 같은 토큰 2회 성공 | `takeByResultToken`(`remove(key, value)`) |
| Minor | NICE 원시 오류코드(`code=-6`)가 응답에 노출 | 로그에만, 응답은 고정 문구 |
| Minor | 원인별 메시지("다른 세션", "이미 처리된")가 응답으로 새어 나감 — 상태코드는 통일했는데 메시지로 원인이 드러남 | 파이프라인 실패 문구 통일, 원인은 로그. `requireFresh`(가입 시점 안내)는 유지 |
| Minor | 시간대 `systemDefault` — 운영 JVM이 UTC면 전 인증 거부 | `Asia/Seoul` 고정 |
| Minor | 콜백 예외 시 팝업에 원시 JSON, 부모창에 실패 미전달 | 예외를 잡아 토큰 없이 `303` → 결과 화면이 FAIL 전달 |
| Minor | 가드가 `site-code`만 봄 | 실연동 시 4개 값 모두 필수 |
| (자체 발견) | 코덱이 UTF-8 — NICE는 EUC-KR | EUC-KR로 교체, **벤더 `fnParse`와 교차 검증 테스트** 추가 |
| (사용자 결정) | 벤더 `fnParse`가 파싱 실패 시 입력 조각을 stdout에 직접 찍음 — 이름·CI 조각 유출 가능 | **실연동 파싱을 우리 코덱으로 교체.** 교차 검증 테스트가 호환성을 보장한다. 예외를 cause로 넘기지 않는다(코덱 메시지에 조각이 있다) |

**동시성 테스트는 `CyclicBarrier`로 경쟁을 결정적으로 재현한다.** 스레드 두 개를 그냥 띄우면 대부분 순차로 돌아 우연히 통과한다. `Store`를 상속해 읽기 메서드 안에서 두 스레드를 만나게 해, 둘 다 읽기를 마친 뒤 쓰기로 넘어가게 한다.

**벤더 파서 교차 검증이 로컬 검증 범위를 넓혔다.** 복호화는 여전히 불가능하지만(자체 왕복 `-6`), 우리 직렬화가 벤더 파서와 호환되는지는 검증할 수 있다. EUC-KR 오류를 바로 이 방식으로 찾았다. 우리 코덱이 운영 파서가 된 뒤로 이 테스트는 **운영 파서가 NICE 규격에서 벗어나지 않게 지키는 가드**가 됐다.

**알려진 한계 — 희귀 한글.** 벤더 모듈이 `euc-kr`을 쓰므로 EUC-KR 2,350자 밖의 글자(`똠`·`뷁` 등)는 `?`가 된다(`김똠뷁` → `김??`). 벤더 한계라 고칠 수 없고 레거시도 같다. 해당 이름은 `?`가 섞여 저장된다.
