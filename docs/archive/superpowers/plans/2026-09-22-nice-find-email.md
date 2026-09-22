# NICE 본인확인 기반 아이디(이메일) 찾기 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `AccountRecovery.vue`의 아이디 찾기를 목업에서 NICE 실연동 본인확인 + 서버 조회(부분 마스킹 아이디 응답)로 바꾼다.

**Architecture:** 가입과 같은 2단계 흐름. 팝업이 `purpose=FIND_EMAIL`로 NICE 요청을 발급받고, 기존 콜백·결과 교환이 세션 `NICE_VERIFIED`에 인증 결과를 심는다. 새 `POST /auth/applicants/find-email`이 그 결과를 `requireFresh(…, FIND_EMAIL)`로 검사·소비하고, 식별 키(이름+생년월일+성별 HMAC = `ciHash`)로 계정을 찾아 `loginId`를 부분 마스킹해 돌려준다.

**Tech Stack:** Spring Boot 4 · Java 17 · JPA · Spring Security · JUnit 5/Mockito/MockMvc · Vue 3 · TypeScript · ant-design-vue · Axios

**설계서:** `docs/superpowers/specs/2026-09-22-nice-find-email-design.md`

---

## 작업 전 반드시 알 것

- **커밋 금지.** 이 저장소 규칙상 사용자가 명시적으로 요청할 때만 `git commit`·`git push`를 한다. 각 Task 끝의 "커밋" 단계는 없다. 작업은 `main` 작업 트리에서 한다.
- **다른 세션의 미커밋 작업이 섞여 있다.** 다음 파일은 건드리지도, 되돌리지도 않는다: `docs/domains/message-delivery.md`, `recruit_back/recruit_backend/build.gradle`, `src/main/resources/application.yaml`, `src/test/resources/application.yaml`, `Message*`·`Delivery*`·`*Gateway*`·`TRNode*` 관련 파일, `design/*.html`. `docs/domains/auth-nice-verification.md`에도 그 세션의 1줄 변경(파일 지도 `lib` 행의 `fileTree('libs')`)이 있다 — **그 줄은 그대로 둔다.**
- 백엔드 컴파일이 **다른 세션 파일 때문에** 깨지면 고치지 말고 멈춰서 보고한다.
- 백엔드 명령은 `recruit_back/recruit_backend/`에서, 프론트 명령은 `recruit_front/`에서, 문서 점검은 저장소 루트(`D:\recruit`)에서 실행한다.
- AES 키는 로컬 예시 값 `22791194512954214612461221261067`만 쓴다. NICE 사이트코드·패스워드 실제 값은 어디에도 쓰지 않는다.
- 경로 약어: `{BE}` = `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit`, `{BT}` = `recruit_back/recruit_backend/src/test/java/com/shinyoung/recruit`, `{FE}` = `recruit_front/src`.

백엔드 테스트 실행 형식(PowerShell):

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "<클래스 FQCN>" --no-daemon
```

## 파일 구조

| 구분 | 파일 | 책임 |
|---|---|---|
| 신규 | `docs/domains/auth-nice-verification-module.md` | NICE 벤더 모듈 실측·평문 규격·한계·레거시 연동 확인분(서술만, 파일 소유 없음) |
| 신규 | `{BE}/dto/request/NiceRequestRequest.java` | `request` 본문 `{ purpose }` |
| 신규 | `{BE}/dto/response/ApplicantFindEmailResponse.java` | `{ maskedEmail }` |
| 신규 | `{BE}/service/ApplicantAccountRecoveryService.java` | 식별 키 조회 + 마스킹 |
| 신규 | `{BE}/controller/ApplicantAccountRecoveryController.java` | `POST /auth/applicants/find-email` — 세션 검사·소비 |
| 신규 | `{BT}/service/ApplicantAccountRecoveryServiceTest.java` | 서비스·마스킹 단위 테스트 |
| 신규 | `{BT}/controller/ApplicantAccountRecoveryControllerTest.java` | 엔드포인트 통합 테스트 |
| 수정 | `{BE}/enumeration/NiceVerificationPurpose.java` | `FIND_EMAIL` 추가 |
| 수정 | `{BE}/controller/NiceVerificationController.java` | `request`가 본문 `purpose`를 받음 |
| 수정 | `{BE}/config/SecurityConfig.java` | `find-email` permitAll |
| 수정 | `{BT}/controller/NiceVerificationControllerTest.java` | 본문 추가·purpose 검증 테스트 |
| 수정 | `{BT}/controller/ApplicantSignUpControllerTest.java` | 용도 대조 역방향 테스트 |
| 수정 | `{BT}/config/SecurityConfigTest.java` | `find-email` 인가 통과 테스트 |
| 수정 | `{FE}/types/auth/nice.ts` | 용도 타입·가드 |
| 수정 | `{FE}/api/auth/niceApi.ts` | `request(purpose)` |
| 수정 | `{FE}/views/auth/pop-up/NiceAuthPopup.vue` | 쿼리 `purpose` 검사·전달 |
| 수정 | `{FE}/views/applicant/SignupView.vue` | `/nice-auth?purpose=SIGNUP` |
| 수정 | `{FE}/types/application.ts` | `FindEmailResponse` |
| 수정 | `{FE}/api/applicationApi.ts` | `findEmail()` |
| 수정 | `{FE}/views/applicant/AccountRecovery.vue` | 실연동 팝업·postMessage·결과 표시 |
| 수정 | `{FE}/routes/authRoutes.ts` | `/nice-auth/mock` 라우트 삭제 |
| 삭제 | `{FE}/views/auth/pop-up/NiceAuthMockPopup.vue` | 사용처 없음 |
| 삭제 | `{FE}/types/window.ts` | `phoneAuthCallback` 선언뿐, 사용처 없음 |
| 수정 | `docs/domains/auth-nice-verification.md`, `docs/domains/auth-account.md`, `docs/domains/_index.md` | 카드 갱신 |

---

### Task 0: 기준선 확인

**Files:** 없음(읽기·실행만)

- [ ] **Step 1: 작업 트리 상태 기록**

Run(루트): `git status --short`
Expected: 위 "다른 세션" 파일들과 `docs/superpowers/specs/2026-09-22-nice-find-email-design.md`, 이 계획서만 보인다. 그 밖의 변경이 있으면 멈추고 보고.

- [ ] **Step 2: 영향 받는 테스트 기준선**

Run(`recruit_back/recruit_backend/`):

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.NiceVerificationControllerTest" --tests "com.shinyoung.recruit.controller.ApplicantSignUpControllerTest" --tests "com.shinyoung.recruit.config.SecurityConfigTest" --no-daemon
```

Expected: BUILD SUCCESSFUL. 컴파일 실패가 다른 세션 파일 때문이면 멈추고 보고.

- [ ] **Step 3: 문서 점검 기준선**

Run(루트): `node tools/check-docs.mjs`
Expected: `문서 점검 통과`(경고만 있음).

---

### Task 1: NICE 카드 분할

`auth-nice-verification.md`(40,741B)가 40KB 상한 직전이라 갱신 전에 벤더 모듈 서술을 하위 카드로 옮긴다. **파일 지도는 옮기지 않는다**(원 카드가 계속 소유).

**Files:**
- Create: `docs/domains/auth-nice-verification-module.md`
- Modify: `docs/domains/auth-nice-verification.md`
- Modify: `docs/domains/_index.md`

- [ ] **Step 1: 분할 스크립트 작성**

세션 스크래치패드(없으면 OS 임시 디렉터리)에 `split-nice-card.mjs`를 만든다. **저장소 안에 만들지 않는다.**

```js
// 저장소 루트에서 실행: node <경로>/split-nice-card.mjs
import fs from 'node:fs';

const parentPath = 'docs/domains/auth-nice-verification.md';
const modulePath = 'docs/domains/auth-nice-verification-module.md';
const lines = fs.readFileSync(parentPath, 'utf8').split('\n');

// 옮길 범위: "**jar 실측**" 문단부터 "**NICE 실연동은 세 흐름" 문단 직전까지
// (jar 실측 · 평문 직렬화는 EUC-KR · 알려진 한계 2건 · 연동 방식 확인분)
const start = lines.findIndex((l) => l.startsWith('**jar 실측**'));
const end = lines.findIndex((l) => l.startsWith('**NICE 실연동은 세 흐름'));
if (start < 0 || end < 0 || end <= start) throw new Error(`marker not found: start=${start} end=${end}`);
if (fs.existsSync(modulePath)) throw new Error('module card already exists');

const moved = lines.slice(start, end).join('\n').trimEnd();
const header = [
  '# NICE 본인확인 — 벤더 모듈·규격 (`auth-nice-verification-module`)',
  '',
  '> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.',
  '> 상위 카드: [auth-nice-verification](auth-nice-verification.md) — 흐름·API 계약·규칙·파일 지도는 모두 상위 카드에 있다. 이 카드는 파일을 소유하지 않는다.',
  '',
  '`auth-nice-verification.md`가 40KB 상한에 가까워져 벤더 모듈(`NiceID.jar`) 실측·평문 규격·레거시 연동 확인분을 분리했다(2026-09-22). `RealNiceClient`·`NicePlaindataCodec`을 고칠 때 먼저 읽는다.',
  '',
  '## 모듈 실측·규격',
  '',
];
fs.writeFileSync(modulePath, header.join('\n') + '\n' + moved + '\n');

const pointer =
  '**벤더 모듈 실측·평문 규격·알려진 한계·레거시 연동 확인분**은 [auth-nice-verification-module](auth-nice-verification-module.md)로 옮겼다(2026-09-22).';
const rest = [...lines.slice(0, start), pointer, '', ...lines.slice(end)];
fs.writeFileSync(parentPath, rest.join('\n'));
console.log(`moved lines ${start + 1}..${end}`);
```

- [ ] **Step 2: 실행**

Run(루트): `node <스크래치패드>/split-nice-card.mjs`
Expected: `moved lines 198..224` 근처 값 출력(줄 번호는 다를 수 있다). 파일 두 개 크기 확인: `wc -c docs/domains/auth-nice-verification.md docs/domains/auth-nice-verification-module.md` — 원 카드 약 31KB, 하위 카드 약 10KB.

- [ ] **Step 3: 옮긴 문단의 상호 참조 고치기**

(a) 원 카드에서 옮겨 간 문단을 가리키는 문구를 하위 카드 링크로 바꾼다.

Run: `grep -n 'jar 실측\|연동 방식 확인분\|모듈 자체 암호문 왕복\|평문 직렬화는 EUC-KR\|알려진 한계' docs/domains/auth-nice-verification.md`

적어도 다음 두 곳이 걸린다. Edit로 바꾼다.
- 파일 지도 `RealNiceClient.java` 행: `복호화·파싱 로컬 왕복 검증 불가(아래 "함정·결정")` → `복호화·파싱 로컬 왕복 검증 불가([auth-nice-verification-module](auth-nice-verification-module.md))`
- 변경 레시피 "`RealNiceClient` 연결 — 완료됨" 문단: `상세는 아래 "함정·결정"의 "jar 실측"·"연동 방식 확인분" 참고.` → `상세는 [auth-nice-verification-module](auth-nice-verification-module.md) 참고.`
- 그 밖에 걸리는 줄(포인터 문단 제외)도 같은 방식으로 링크로 바꾼다.

(b) 하위 카드 안에서 원 카드의 절을 가리키는 문구(`위 "`·`아래 "`)를 원 카드 링크로 바꾼다.

Run: `grep -n '위 "\|아래 "' docs/domains/auth-nice-verification-module.md`

걸리는 문구 중 하위 카드 **안에 있는** 문단(예: `위 "모듈 자체 암호문 왕복이 불가능"`, `위 "평문 직렬화는 EUC-KR 바이트 길이다"`)을 가리키면 그대로 둔다. 원 카드에 남은 절(예: `위 "결함 8건" 표`, `아래 "함정·결정"`)을 가리키면 `[auth-nice-verification](auth-nice-verification.md) "결함 8건" 표`처럼 링크로 바꾼다.

- [ ] **Step 4: 색인에 하위 카드 추가**

`docs/domains/_index.md`는 12,127B로 상한(12KB = 12,288B)까지 161B 남았다. 한 줄만 추가한다.

Edit — old:

```
| [auth-nice-verification](auth-nice-verification.md) | NICE 본인확인 | 지원자 |
```

new:

```
| [auth-nice-verification](auth-nice-verification.md) | NICE 본인확인 | 지원자 |
| [auth-nice-verification-module](auth-nice-verification-module.md) | NICE 모듈 실측·규격 | — |
```

- [ ] **Step 5: 점검**

Run(루트): `node tools/check-docs.mjs`
Expected: `문서 점검 통과`. `_index.md` 크기 오류가 나면 추가한 행의 설명을 `NICE 모듈 규격`으로 줄인다.

---

### Task 2: `request`가 용도를 본문으로 받는다

**Files:**
- Create: `{BE}/dto/request/NiceRequestRequest.java`
- Modify: `{BE}/controller/NiceVerificationController.java`
- Modify: `{BE}/enumeration/NiceVerificationPurpose.java`
- Test: `{BT}/controller/NiceVerificationControllerTest.java`
- Test: `{BT}/controller/ApplicantSignUpControllerTest.java`

- [ ] **Step 1: 기존 발급 헬퍼에 본문을 넣고, 본문 검증 테스트 3개 추가**

`NiceVerificationControllerTest.java`의 `issueReqSeq`를 아래 두 메서드로 바꾼다(기존 호출부 `issueReqSeq(session)`은 그대로 동작).

```java
    /** 요청을 발급하고 그 EncodeData 에서 REQ_SEQ 를 꺼낸다. 용도는 가입. */
    private String issueReqSeq(MockHttpSession session) throws Exception {
        return issueReqSeq(session, "SIGNUP");
    }

    private String issueReqSeq(MockHttpSession session, String purpose) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/nice/request")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"" + purpose + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String encodeData = new ObjectMapper()
                .readTree(result.getResponse().getContentAsString())
                .path("data").path("encodeData").asText();
        return codec.decode(client.decode(encodeData).plaindata()).get("REQ_SEQ");
    }
```

같은 파일 끝(`blankTokenIsRejectedByValidation` 뒤)에 추가:

```java
    /*
     * 용도는 필수다. 기본값을 두면 호출부가 용도를 빠뜨려도 조용히 가입용이 된다.
     */
    @Test
    void requestWithoutBodyIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/nice/request").session(new MockHttpSession()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestWithUnknownPurposeIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/nice/request")
                        .session(new MockHttpSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"UNKNOWN_PURPOSE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestWithNullPurposeIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/nice/request")
                        .session(new MockHttpSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("purpose는 필수입니다."));
    }
```

- [ ] **Step 2: 실패 확인**

Run: `.\gradlew.bat test --tests "com.shinyoung.recruit.controller.NiceVerificationControllerTest" --no-daemon` (AES 키 포함)
Expected: 새 테스트 3개 FAIL(지금 컨트롤러는 본문을 무시해 200). 기존 11개는 PASS.

- [ ] **Step 3: 요청 DTO 작성**

Create `{BE}/dto/request/NiceRequestRequest.java`:

```java
package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import jakarta.validation.constraints.NotNull;

/**
 * 본인확인 요청 발급. 용도는 서버가 레코드에 기록해 소비 시점에 대조한다.
 *
 * <p>기본값을 두지 않는다 — 호출부가 용도를 빠뜨리면 조용히 가입용이 되기 때문이다.
 * 모르는 값은 역직렬화 단계에서 400 이 난다.
 */
public record NiceRequestRequest(
        @NotNull(message = "purpose는 필수입니다.")
        NiceVerificationPurpose purpose
) {
}
```

- [ ] **Step 4: 컨트롤러가 본문의 용도를 쓰게 한다**

`{BE}/controller/NiceVerificationController.java`

import 교체 — `import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;` 줄을 지우고 `import com.shinyoung.recruit.dto.request.NiceRequestRequest;`를 `NiceResultRequest` import 위에 추가한다.

old:

```java
    @PostMapping("/request")
    public ResponseEntity<ApiResponse<NiceRequestResponse>> request(HttpSession session) {
        String encodeData =
                niceVerificationService.request(NiceVerificationPurpose.SIGNUP, session.getId());
        return ResponseEntity.ok(ApiResponse.success(new NiceRequestResponse(encodeData)));
    }
```

new:

```java
    @PostMapping("/request")
    public ResponseEntity<ApiResponse<NiceRequestResponse>> request(
            @Valid @RequestBody NiceRequestRequest request, HttpSession session) {
        String encodeData = niceVerificationService.request(request.purpose(), session.getId());
        return ResponseEntity.ok(ApiResponse.success(new NiceRequestResponse(encodeData)));
    }
```

- [ ] **Step 5: 통과 확인**

Run: 위 Step 2와 같은 명령.
Expected: PASS 14건.

- [ ] **Step 6: `FIND_EMAIL` 용도 테스트 추가(양방향)**

`NiceVerificationControllerTest.java` — import 추가 `import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;`, 파일 끝에 추가:

```java
    /* 발급 때 받은 용도가 콜백·결과 교환을 거쳐 세션의 인증 결과까지 그대로 따라와야 한다. */
    @Test
    void findEmailPurposeFlowsIntoSessionIdentity() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = callbackAndExtractToken(issueReqSeq(session, "FIND_EMAIL"));

        mockMvc.perform(post("/api/auth/nice/result")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk());

        NiceVerifiedIdentity identity = (NiceVerifiedIdentity)
                session.getAttribute(NiceVerificationController.VERIFIED_SESSION_KEY);
        assertNotNull(identity);
        assertEquals(NiceVerificationPurpose.FIND_EMAIL, identity.purpose());
    }
```

`ApplicantSignUpControllerTest.java` 끝(`이메일_blank는_400` 뒤)에 추가:

```java
    /* 용도 대조 역방향 — 아이디 찾기용 인증 결과로 계정을 만들 수 없어야 한다. */
    @Test
    void 아이디_찾기용_인증으로는_가입할_수_없다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                NiceVerificationController.VERIFIED_SESSION_KEY,
                new NiceVerifiedIdentity(
                        NiceVerificationPurpose.FIND_EMAIL, "홍길동", "01012345678", "19900101", "1", clock.instant()));

        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "purpose-mismatch@example.com",
                                  "password": "Password1234!",
                                  "email": "purpose-mismatch@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본인인증 용도가 일치하지 않습니다."));
    }
```

- [ ] **Step 7: 실패 확인**

Run: `.\gradlew.bat test --tests "com.shinyoung.recruit.controller.NiceVerificationControllerTest" --tests "com.shinyoung.recruit.controller.ApplicantSignUpControllerTest" --no-daemon`
Expected: 컴파일 실패 — `FIND_EMAIL` 심볼 없음.

- [ ] **Step 8: 용도 추가**

`{BE}/enumeration/NiceVerificationPurpose.java` 전체:

```java
package com.shinyoung.recruit.enumeration;

/**
 * 본인확인을 요구한 흐름. 요청 발급 시 기록하고 결과 소비 시 대조한다.
 *
 * <p>용도를 대조하지 않으면 한 흐름용으로 받은 인증 결과를 다른 흐름에 그대로 밀어 넣을 수 있다
 * (가입용 인증으로 아이디 찾기, 아이디 찾기용 인증으로 가입).
 */
public enum NiceVerificationPurpose {
    SIGNUP,
    FIND_EMAIL
}
```

- [ ] **Step 9: 통과 확인**

Run: Step 7과 같은 명령.
Expected: PASS — `NiceVerificationControllerTest` 15건, `ApplicantSignUpControllerTest` 10건.

---

### Task 3: 아이디 찾기 서비스와 마스킹

**Files:**
- Create: `{BE}/dto/response/ApplicantFindEmailResponse.java`
- Create: `{BE}/service/ApplicantAccountRecoveryService.java`
- Test: `{BT}/service/ApplicantAccountRecoveryServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

Create `{BT}/service/ApplicantAccountRecoveryServiceTest.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.exception.ApplicantNotFoundException;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ApplicantAccountRecoveryServiceTest {

    @Mock
    private ApplicantRepository applicantRepository;

    private final AuditHmac auditHmac = new AuditHmac("test-secret-value");

    private ApplicantAccountRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new ApplicantAccountRecoveryService(applicantRepository, auditHmac);
    }

    private NiceVerifiedIdentity identity() {
        return new NiceVerifiedIdentity(
                NiceVerificationPurpose.FIND_EMAIL, "홍길동", "01012345678", "19900101", "1", Instant.now());
    }

    @Test
    void 식별키가_일치하는_계정의_아이디를_마스킹해_돌려준다() {
        String identityKey = auditHmac.identityHash("홍길동", "19900101", "1");
        Applicant applicant = new Applicant(identityKey);
        applicant.setLoginId("abc12345@gmail.com");
        given(applicantRepository.findByCiHash(identityKey)).willReturn(Optional.of(applicant));

        assertThat(service.findEmail(identity()).maskedEmail()).isEqualTo("ab******@gmail.com");
    }

    @Test
    void 일치하는_계정이_없으면_ApplicantNotFoundException() {
        given(applicantRepository.findByCiHash(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findEmail(identity()))
                .isInstanceOf(ApplicantNotFoundException.class)
                .hasMessage("본인인증 정보와 일치하는 계정이 없습니다.");
    }

    /* loginId 가 비어 있는 계정(파기 처리)은 찾지 못한 것으로 본다. */
    @Test
    void 아이디가_없는_계정도_ApplicantNotFoundException() {
        given(applicantRepository.findByCiHash(anyString()))
                .willReturn(Optional.of(new Applicant("PURGED:x")));

        assertThatThrownBy(() -> service.findEmail(identity()))
                .isInstanceOf(ApplicantNotFoundException.class);
    }

    @Test
    void 마스킹_규칙() {
        assertThat(ApplicantAccountRecoveryService.maskLoginId("abc12345@gmail.com")).isEqualTo("ab******@gmail.com");
        assertThat(ApplicantAccountRecoveryService.maskLoginId("abc@x.com")).isEqualTo("ab*@x.com");
        assertThat(ApplicantAccountRecoveryService.maskLoginId("ab@x.com")).isEqualTo("a*@x.com");
        assertThat(ApplicantAccountRecoveryService.maskLoginId("a@x.com")).isEqualTo("a*@x.com");
        assertThat(ApplicantAccountRecoveryService.maskLoginId("hongildong")).isEqualTo("ho********");
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `.\gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicantAccountRecoveryServiceTest" --no-daemon`
Expected: 컴파일 실패 — `ApplicantAccountRecoveryService` 없음.

- [ ] **Step 3: 응답 DTO 작성**

Create `{BE}/dto/response/ApplicantFindEmailResponse.java`:

```java
package com.shinyoung.recruit.dto.response;

/** 아이디 찾기 결과. 원문 아이디는 담지 않는다 — 부분 마스킹한 값만. */
public record ApplicantFindEmailResponse(String maskedEmail) {
}
```

- [ ] **Step 4: 서비스 작성**

Create `{BE}/service/ApplicantAccountRecoveryService.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.dto.response.ApplicantFindEmailResponse;
import com.shinyoung.recruit.exception.ApplicantNotFoundException;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 전 계정 복구. 지금은 아이디(이메일) 찾기뿐이다.
 *
 * <p>세션을 직접 만지지 않는다. 컨트롤러가 세션의 NICE 인증 결과를 검사·소비한 뒤 값으로 넘긴다
 * — 서비스가 {@code HttpSession} 을 알면 단위 테스트가 서블릿 컨테이너에 묶인다.
 */
@Service
public class ApplicantAccountRecoveryService {

    private static final String NOT_FOUND_MESSAGE = "본인인증 정보와 일치하는 계정이 없습니다.";

    private final ApplicantRepository applicantRepository;
    private final AuditHmac auditHmac;

    public ApplicantAccountRecoveryService(ApplicantRepository applicantRepository, AuditHmac auditHmac) {
        this.applicantRepository = applicantRepository;
        this.auditHmac = auditHmac;
    }

    /**
     * 본인확인한 사람의 아이디를 부분 마스킹해 돌려준다.
     *
     * <p>가입과 같은 식별 키(이름+생년월일+성별 HMAC)로 찾는다. {@code ciHash} 가 unique 라 계정은 많아야 하나다.
     * 파기된 계정은 {@code ciHash} 가 {@code PURGED:} sentinel 로 덮여 있어 찾아지지 않는다.
     */
    @Transactional(readOnly = true)
    public ApplicantFindEmailResponse findEmail(NiceVerifiedIdentity identity) {
        String identityKey = auditHmac.identityHash(identity.name(), identity.birthDate(), identity.gender());
        String loginId = applicantRepository.findByCiHash(identityKey)
                .map(Applicant::getLoginId)
                .orElseThrow(() -> new ApplicantNotFoundException(NOT_FOUND_MESSAGE));
        return new ApplicantFindEmailResponse(maskLoginId(loginId));
    }

    /**
     * 아이디 부분 마스킹. 로컬부({@code @} 앞)는 3자 이상이면 앞 2자, 2자 이하면 앞 1자만 남기고 나머지를
     * 같은 길이의 {@code *} 로 가린다(가린 글자가 0개면 {@code *} 1개). 도메인은 그대로 둔다.
     * {@code @} 가 없으면(형식 검증 없이 만든 계정) 전체를 로컬부로 본다.
     *
     * <p>{@code MessageContacts.maskEmail} 을 쓰지 않는다 — 그건 로그용이라 형식이 다르다(첫 글자+{@code ***}).
     */
    static String maskLoginId(String loginId) {
        int at = loginId.indexOf('@');
        String local = at < 0 ? loginId : loginId.substring(0, at);
        String domain = at < 0 ? "" : loginId.substring(at);
        int keep = Math.min(local.length() >= 3 ? 2 : 1, local.length());
        int hidden = Math.max(local.length() - keep, 1);
        return local.substring(0, keep) + "*".repeat(hidden) + domain;
    }
}
```

- [ ] **Step 5: 통과 확인**

Run: Step 2와 같은 명령.
Expected: PASS 4건.

---

### Task 4: 아이디 찾기 엔드포인트

**Files:**
- Create: `{BE}/controller/ApplicantAccountRecoveryController.java`
- Modify: `{BE}/config/SecurityConfig.java`
- Test: `{BT}/controller/ApplicantAccountRecoveryControllerTest.java`
- Test: `{BT}/config/SecurityConfigTest.java`

- [ ] **Step 1: 실패하는 통합 테스트 작성**

Create `{BT}/controller/ApplicantAccountRecoveryControllerTest.java`:

```java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicantAccountRecoveryControllerTest {

    private static final String PATH = "/api/auth/applicants/find-email";

    /* 다른 테스트가 남긴 계정과 식별 키가 겹치지 않도록 이 테스트 전용 신원을 쓴다. */
    private static final String NAME = "김찾기";
    private static final String BIRTH_DATE = "19851212";
    private static final String GENDER = "2";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private AuditHmac auditHmac;

    @Autowired
    private Clock clock;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        SecurityContextHolder.clearContext();
    }

    private MockHttpSession verifiedSession(NiceVerificationPurpose purpose, Instant verifiedAt) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                NiceVerificationController.VERIFIED_SESSION_KEY,
                new NiceVerifiedIdentity(purpose, NAME, "01012345678", BIRTH_DATE, GENDER, verifiedAt));
        return session;
    }

    private MockHttpSession verifiedSession(NiceVerificationPurpose purpose) {
        return verifiedSession(purpose, clock.instant());
    }

    private void saveApplicant(String loginId) {
        Applicant applicant = new Applicant(auditHmac.identityHash(NAME, BIRTH_DATE, GENDER));
        applicant.setLoginId(loginId);
        applicant.setName(NAME);
        applicant.setUserName(NAME);
        applicant.setPassword("encoded");
        applicant.setPhoneNumber("01012345678");
        applicant.setEmail(loginId);
        applicantRepository.save(applicant);
    }

    @Test
    void 아이디_찾기_성공_시_마스킹한_아이디만_응답한다() throws Exception {
        saveApplicant("abc12345@gmail.com");

        MvcResult result = mockMvc.perform(post(PATH).session(verifiedSession(NiceVerificationPurpose.FIND_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.maskedEmail").value("ab******@gmail.com"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("abc12345");
    }

    /* 인증 1회 = 조회 1회. 성공한 뒤 같은 세션으로 다시 부르면 인증을 먼저 하라고 해야 한다. */
    @Test
    void 인증_결과는_1회용이라_두번째_호출은_400() throws Exception {
        saveApplicant("abc12345@gmail.com");
        MockHttpSession session = verifiedSession(NiceVerificationPurpose.FIND_EMAIL);

        mockMvc.perform(post(PATH).session(session)).andExpect(status().isOk());
        assertThat(session.getAttribute(NiceVerificationController.VERIFIED_SESSION_KEY)).isNull();

        mockMvc.perform(post(PATH).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본인인증을 먼저 진행해주세요."));
    }

    @Test
    void 세션에_인증_결과가_없으면_400() throws Exception {
        mockMvc.perform(post(PATH).session(new MockHttpSession()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본인인증을 먼저 진행해주세요."));
    }

    @Test
    void 가입용_인증으로는_아이디를_찾을_수_없다() throws Exception {
        saveApplicant("abc12345@gmail.com");

        mockMvc.perform(post(PATH).session(verifiedSession(NiceVerificationPurpose.SIGNUP)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본인인증 용도가 일치하지 않습니다."));
    }

    @Test
    void 만료된_인증은_400() throws Exception {
        saveApplicant("abc12345@gmail.com");
        Instant expired = clock.instant().minus(Duration.ofMinutes(31));

        mockMvc.perform(post(PATH).session(verifiedSession(NiceVerificationPurpose.FIND_EMAIL, expired)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본인인증이 만료되었습니다. 다시 진행해주세요."));
    }

    /* 계정이 없어도 인증 결과는 소비된다 — 조회는 결정적이라 같은 인증으로 다시 시도할 이유가 없다. */
    @Test
    void 일치하는_계정이_없으면_404이고_인증_결과는_소비된다() throws Exception {
        MockHttpSession session = verifiedSession(NiceVerificationPurpose.FIND_EMAIL);

        mockMvc.perform(post(PATH).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("본인인증 정보와 일치하는 계정이 없습니다."));

        assertThat(session.getAttribute(NiceVerificationController.VERIFIED_SESSION_KEY)).isNull();
    }
}
```

`SecurityConfigTest.java`의 `본인확인_요청은_비인증이어도_인가를_통과` 테스트 바로 위에 추가:

```java
    /*
     * 아이디 찾기도 로그인 전 흐름이다. 세션 인증 결과가 없어 400 이 나지만 인가 단계에서 막히면 안 된다.
     * 지금은 전용 매처가 없어도 anyRequest().permitAll() 로 통과한다 — broad 매처 추가 회귀를 잡는 용도다.
     */
    @Test
    void 아이디_찾기는_비인증이어도_인가를_통과() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/find-email"))
                .andExpect(status().is(allOf(not(401), not(403))));
    }
```

- [ ] **Step 2: 실패 확인**

Run: `.\gradlew.bat test --tests "com.shinyoung.recruit.controller.ApplicantAccountRecoveryControllerTest" --no-daemon`
Expected: FAIL — 엔드포인트가 없어 6건 모두 상태 또는 메시지 단정에서 실패한다. `SecurityConfigTest`의 새 테스트는 `anyRequest().permitAll()` 때문에 지금도 통과한다(회귀 방지용이라 정상).

- [ ] **Step 3: 컨트롤러 작성**

Create `{BE}/controller/ApplicantAccountRecoveryController.java`:

```java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ApplicantFindEmailResponse;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.service.ApplicantAccountRecoveryService;
import com.shinyoung.recruit.service.nice.NiceVerificationService;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 전 계정 복구. 지금은 아이디(이메일) 찾기뿐이다. */
@RestController
@RequestMapping("/auth/applicants")
public class ApplicantAccountRecoveryController {

    private final ApplicantAccountRecoveryService applicantAccountRecoveryService;
    private final NiceVerificationService niceVerificationService;

    public ApplicantAccountRecoveryController(
            ApplicantAccountRecoveryService applicantAccountRecoveryService,
            NiceVerificationService niceVerificationService) {
        this.applicantAccountRecoveryService = applicantAccountRecoveryService;
        this.niceVerificationService = niceVerificationService;
    }

    /**
     * 아이디(이메일) 찾기. 요청 본문은 없다 — 세션에 담긴 NICE 인증 결과(용도 {@code FIND_EMAIL})로 찾는다.
     *
     * <p>인증 결과는 <b>조회 전에</b> 세션에서 지운다. 인증 1회로 조회 1회만 된다. 계정이 없어도 소비한다
     * — 조회는 결정적이라 같은 인증으로 다시 시도할 이유가 없다.
     */
    @PostMapping("/find-email")
    public ResponseEntity<ApiResponse<ApplicantFindEmailResponse>> findEmail(HttpSession session) {
        NiceVerifiedIdentity identity = niceVerificationService.requireFresh(
                (NiceVerifiedIdentity) session.getAttribute(NiceVerificationController.VERIFIED_SESSION_KEY),
                NiceVerificationPurpose.FIND_EMAIL);
        session.removeAttribute(NiceVerificationController.VERIFIED_SESSION_KEY);

        return ResponseEntity.ok(ApiResponse.success(applicantAccountRecoveryService.findEmail(identity)));
    }
}
```

- [ ] **Step 4: 보안 매처에 명시**

`{BE}/config/SecurityConfig.java`

old:

```java
                .requestMatchers("/api/auth/login", "/api/auth/logout", "/api/auth/applicants/sign-up", "/api/auth/applicants/check-email").permitAll()
```

new:

```java
                .requestMatchers("/api/auth/login", "/api/auth/logout", "/api/auth/applicants/sign-up", "/api/auth/applicants/check-email", "/api/auth/applicants/find-email").permitAll()
```

- [ ] **Step 5: 통과 확인**

Run: `.\gradlew.bat test --tests "com.shinyoung.recruit.controller.ApplicantAccountRecoveryControllerTest" --tests "com.shinyoung.recruit.config.SecurityConfigTest" --no-daemon`
Expected: PASS — `ApplicantAccountRecoveryControllerTest` 6건, `SecurityConfigTest` 전부(기존 `request` 테스트 3건은 본문이 없어 400이 나지만 "401·403 아님"·CORS 403 단정은 그대로 성립한다).

---

### Task 5: 백엔드 영향 범위 회귀

**Files:** 없음(실행만)

- [ ] **Step 1: 변경 범위 테스트 일괄 실행**

Run:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.nice.*" --tests "com.shinyoung.recruit.controller.NiceVerificationControllerTest" --tests "com.shinyoung.recruit.config.NiceClientConfigTest" --tests "com.shinyoung.recruit.controller.ApplicantSignUpControllerTest" --tests "com.shinyoung.recruit.controller.ApplicantSignUpNiceIntegrationTest" --tests "com.shinyoung.recruit.service.ApplicantSignUpServiceTest" --tests "com.shinyoung.recruit.controller.ApplicantAccountRecoveryControllerTest" --tests "com.shinyoung.recruit.service.ApplicantAccountRecoveryServiceTest" --tests "com.shinyoung.recruit.config.SecurityConfigTest" --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: 건수 기록(카드 갱신용)**

Run(Git Bash, `recruit_back/recruit_backend/`): `grep -c "<testcase" build/test-results/test/TEST-*.xml`
클래스별 건수를 적어 둔다. Task 8에서 카드 "검증" 표에 쓴다.

- [ ] **Step 3: 남은 참조 확인**

Run(루트): `grep -rn "NiceVerificationPurpose.SIGNUP" recruit_back/recruit_backend/src/main`
Expected: `ApplicantSignUpController.java` 1곳만. `NiceVerificationController`에는 없어야 한다.

---

### Task 6: 프론트 — 팝업이 용도를 받는다

**Files:**
- Modify: `{FE}/types/auth/nice.ts`
- Modify: `{FE}/api/auth/niceApi.ts`
- Modify: `{FE}/views/auth/pop-up/NiceAuthPopup.vue`
- Modify: `{FE}/views/applicant/SignupView.vue`

- [ ] **Step 1: 용도 타입·가드 추가**

`{FE}/types/auth/nice.ts` 끝에 추가:

```ts
/** 본인확인 용도. 백엔드 NiceVerificationPurpose 와 같은 값이다. 서버가 발급 시 기록하고 소비 시 대조한다. */
export const NICE_PURPOSES = ['SIGNUP', 'FIND_EMAIL'] as const

export type NiceVerificationPurpose = (typeof NICE_PURPOSES)[number]

/** 팝업 쿼리(?purpose=)로 받은 값이 알려진 용도인지 확인한다. */
export function isNicePurpose(value: unknown): value is NiceVerificationPurpose {
  return typeof value === 'string' && (NICE_PURPOSES as readonly string[]).includes(value)
}
```

- [ ] **Step 2: API가 용도를 보내게 한다**

`{FE}/api/auth/niceApi.ts` 전체:

```ts
import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type { NiceRequestResponse, NiceResultResponse, NiceVerificationPurpose } from '@/types/auth/nice'

/** NICE 본인확인. 로그인 전 흐름(가입·아이디 찾기)이라 인증 없이 호출된다. */
export const niceApi = {
  /** 요청번호를 발급받고 표준창으로 보낼 암호문을 받는다. 용도는 서버가 기록해 소비 시점에 대조한다. */
  request(purpose: NiceVerificationPurpose) {
    return apiClient.post<ApiResponse<NiceRequestResponse>>('/auth/nice/request', { purpose })
  },

  /** 콜백 리다이렉트로 받은 1회용 토큰을 인증 결과로 교환한다. 서버 세션에 인증 결과가 심긴다. */
  exchangeResult(token: string) {
    return apiClient.post<ApiResponse<NiceResultResponse>>('/auth/nice/result', { token })
  },
}
```

- [ ] **Step 3: 타입 검사 실패 확인**

Run(`recruit_front/`): `npm run type-check`
Expected: FAIL — `NiceAuthPopup.vue`의 `niceApi.request()` 인자 부족.

- [ ] **Step 4: 팝업이 쿼리 용도를 검사·전달**

`{FE}/views/auth/pop-up/NiceAuthPopup.vue`

import 블록 old:

```ts
import { onMounted, ref } from 'vue'
import { niceApi } from '@/api/auth/niceApi'
import { getApiErrorMessage } from '@/api/apiError'
import {
  NICE_CHECKPLUS_ACTION,
  NICE_CHECKPLUS_M,
  NICE_MESSAGE_SOURCE,
} from '@/types/auth/nice'

const message = ref('본인확인 창으로 이동합니다...')
```

new:

```ts
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { niceApi } from '@/api/auth/niceApi'
import { getApiErrorMessage } from '@/api/apiError'
import {
  NICE_CHECKPLUS_ACTION,
  NICE_CHECKPLUS_M,
  NICE_MESSAGE_SOURCE,
  isNicePurpose,
} from '@/types/auth/nice'

const route = useRoute()
const message = ref('본인확인 창으로 이동합니다...')
```

`onMounted` old:

```ts
onMounted(async () => {
  try {
    const { data } = await niceApi.request()
    submitToNice(data.data.encodeData)
  } catch (error) {
    reportFailure(getApiErrorMessage(error, '본인확인을 시작하지 못했습니다.'))
  }
})
```

new:

```ts
/**
 * 여는 화면이 용도를 쿼리로 넘긴다(/nice-auth?purpose=SIGNUP|FIND_EMAIL).
 * 모르는 값이면 서버에 묻지 않고 실패로 알린다.
 */
onMounted(async () => {
  const purpose = route.query.purpose
  if (!isNicePurpose(purpose)) {
    reportFailure('본인확인을 시작하지 못했습니다.')
    return
  }
  try {
    const { data } = await niceApi.request(purpose)
    submitToNice(data.data.encodeData)
  } catch (error) {
    reportFailure(getApiErrorMessage(error, '본인확인을 시작하지 못했습니다.'))
  }
})
```

- [ ] **Step 5: 가입 화면이 용도를 넘긴다**

`{FE}/views/applicant/SignupView.vue` — `clickToNiceAuthPopupOpen` 안의

old: `    "/nice-auth",`
new: `    "/nice-auth?purpose=SIGNUP",`

- [ ] **Step 6: 타입 검사 통과 확인**

Run: `npm run type-check`
Expected: 오류 0.

---

### Task 7: 프론트 — 아이디 찾기 실연동과 목업 삭제

**Files:**
- Modify: `{FE}/types/application.ts`
- Modify: `{FE}/api/applicationApi.ts`
- Modify: `{FE}/views/applicant/AccountRecovery.vue`
- Modify: `{FE}/routes/authRoutes.ts`
- Delete: `{FE}/views/auth/pop-up/NiceAuthMockPopup.vue`
- Delete: `{FE}/types/window.ts`

- [ ] **Step 1: 응답 타입과 API 추가**

`{FE}/types/application.ts` — `checkEmailRequest` 인터페이스 바로 뒤에 추가:

```ts
/** POST /auth/applicants/find-email — 세션의 NICE 인증 결과로 찾은 아이디(부분 마스킹). 원문은 오지 않는다. */
export interface FindEmailResponse {
  maskedEmail: string
}
```

`{FE}/api/applicationApi.ts`

import old:

```ts
import type { ApplicantStageResult, ApplicationSearchParams, MyApplicationList, ChangePasswordParams, ChangePasswordRequest, SignupUser, checkEmailRequest } from '@/types/application'
```

new:

```ts
import type { ApplicantStageResult, ApplicationSearchParams, MyApplicationList, ChangePasswordParams, ChangePasswordRequest, SignupUser, checkEmailRequest, FindEmailResponse } from '@/types/application'
```

`checkEmail(...) { ... },` 블록 뒤에 추가:

```ts

  /** 아이디 찾기. 요청 본문은 없다 — 서버가 세션의 NICE 인증 결과(용도 FIND_EMAIL)를 1회 소비한다. */
  findEmail() {
    return apiClient.post<ApiResponse<FindEmailResponse>>('/auth/applicants/find-email')
  },
```

- [ ] **Step 2: 화면 템플릿 — 결과 표시**

`{FE}/views/applicant/AccountRecovery.vue`

old:

```html
                    <p class="find-id-view-text">휴대전화번호 정보와 일치하는 아이디입니다.</p>
                    <div class="find-id-view-text">
                        <span>아이디 : </span>
                        <span>abc12345@gmail.com</span>
```

new:

```html
                    <p class="find-id-view-text">본인인증 정보와 일치하는 아이디입니다.</p>
                    <div class="find-id-view-text">
                        <span>아이디 : </span>
                        <span>{{ maskedEmail }}</span>
```

- [ ] **Step 3: 화면 스크립트 — import·상태**

import old:

```ts
import { applicationApi } from '@/api/applicationApi';
import type { checkEmailRequest } from '@/types/application';
```

new:

```ts
import { applicationApi } from '@/api/applicationApi';
import { getApiErrorMessage } from '@/api/apiError';
import type { checkEmailRequest } from '@/types/application';
import { NICE_MESSAGE_SOURCE, type NiceAuthMessage } from '@/types/auth/nice';
```

상태 old:

```ts
const isNiceAuthPopupOpen = ref(false);
const isNiceAuthComplete = ref(false);
```

new:

```ts
const isNiceAuthPopupOpen = ref(false);
const isNiceAuthComplete = ref(false);
const maskedEmail = ref('');
```

`openFindId` old:

```ts
    findId.value = true;
    isNiceAuthComplete.value = false;
```

new:

```ts
    findId.value = true;
    isNiceAuthComplete.value = false;
    maskedEmail.value = '';
```

- [ ] **Step 4: 화면 스크립트 — 팝업·결과 수신 교체**

old(`clickToNiceAuthPopupOpen`부터 `NiceAuthComplete`까지 전부):

```ts
const clickToNiceAuthPopupOpen = async () => {
  isNiceAuthPopupOpen.value = true;
  window.open(
    "/nice-auth/mock",
    "Nice-Auth",
    "width=450, height=480"
  );
};

const phoneAuthCallback = (data: { name: string, phoneNumber: string, ci:string }) => {

  if(data.name && data.phoneNumber && data.ci) {
    message.success('본인인증이 완료되었습니다.');
    NiceAuthComplete(true);
    isNiceAuthPopupOpen.value = false;
  }
};
window.phoneAuthCallback = phoneAuthCallback;

// 화면을 떠난 뒤 팝업이 콜백을 부르지 않도록 정리한다. 다른 화면이 새로 등록한 콜백은 지우지 않는다.
onBeforeUnmount(() => {
  if (window.phoneAuthCallback === phoneAuthCallback) {
    window.phoneAuthCallback = undefined;
  }
});

const NiceAuthComplete = async (result:boolean) => {
  // 나이스 인증 후 로직
  isNiceAuthPopupOpen.value = false;
  isNiceAuthComplete.value = result;
}
```

new:

```ts
const clickToNiceAuthPopupOpen = async () => {
  isNiceAuthPopupOpen.value = true;
  window.open(
    "/nice-auth?purpose=FIND_EMAIL",
    "Nice-Auth",
    "width=450, height=480, resizable=no"
  );
};

/**
 * 팝업이 postMessage 로 결과를 보낸다(SignupView 와 같은 방식).
 * 성공 알림에는 아이디가 없다 — 서버 세션에 담긴 인증 결과로 find-email 을 불러야 나온다.
 */
const onNiceMessage = async (event: MessageEvent) => {
  if (event.origin !== window.location.origin) {
    return
  }
  const payload = event.data as NiceAuthMessage | undefined
  if (payload?.source !== NICE_MESSAGE_SOURCE) {
    return
  }

  isNiceAuthPopupOpen.value = false

  if (payload.status !== 'SUCCESS') {
    message.error('본인인증에 실패했습니다. 다시 시도해주세요.')
    return
  }

  try {
    const { data } = await applicationApi.findEmail()
    maskedEmail.value = data.data.maskedEmail
    message.success('본인인증이 완료되었습니다.')
    isNiceAuthComplete.value = true
  } catch (error) {
    message.error(getApiErrorMessage(error, '아이디를 찾지 못했습니다.'))
  }
}

window.addEventListener('message', onNiceMessage)

// 화면을 떠난 뒤 팝업이 메시지를 보내도 반응하지 않게 한다. 자기가 등록한 리스너만 지운다.
onBeforeUnmount(() => {
  window.removeEventListener('message', onNiceMessage)
})
```

- [ ] **Step 5: 목업 라우트·파일 삭제**

`{FE}/routes/authRoutes.ts` — 아래 블록을 지운다:

```ts
  {
    path: '/nice-auth/mock',
    name: 'NiceAuthMockPopup',
    component: () => import('@/views/auth/pop-up/NiceAuthMockPopup.vue'),
    meta: {
      public: true,
    },
  },
```

Run(루트): `git rm recruit_front/src/views/auth/pop-up/NiceAuthMockPopup.vue recruit_front/src/types/window.ts`
(`git rm`은 스테이징만 한다. 커밋하지 않는다.)

- [ ] **Step 6: 남은 참조 확인**

Run(루트): `grep -rn "phoneAuthCallback\|NiceAuthMockPopup\|nice-auth/mock\|abc12345" recruit_front/src`
Expected: `SignupView.vue`의 설명 주석 1줄(`전역 함수(window.phoneAuthCallback)를 쓰던 방식에서 바뀌었다`)만 남는다. 다른 결과가 있으면 고친다.

- [ ] **Step 7: 타입 검사·단위 테스트**

Run(`recruit_front/`): `npm run type-check`
Expected: 오류 0.

Run: `npm run test:unit -- --run`
Expected: 전부 PASS(이번 변경과 무관한 기존 스펙이 도는지 확인).

---

### Task 8: 카드 갱신

**Files:**
- Modify: `docs/domains/auth-nice-verification.md`
- Modify: `docs/domains/auth-account.md`

모든 Edit는 **정확한 문자열 치환**이다. 치환 대상이 안 보이면 멈추고 보고한다(다른 세션이 고쳤을 수 있다).

- [ ] **Step 1: `auth-nice-verification.md` — 요약**

old:

```
- 가입 시 본인확인은 **NICE 체크플러스(CheckPlus) 표준창 실연동**이다. 아이디 찾기·비밀번호 재설정은 범위 밖(미착수)이며, 그 전까지는 `NiceAuthMockPopup.vue` 목업을 그대로 쓴다([auth-account](auth-account.md) 소유 화면, `AccountRecovery.vue`에서 호출).
```

new:

```
- 가입과 아이디(이메일) 찾기의 본인확인은 **NICE 체크플러스(CheckPlus) 표준창 실연동**이다(용도 `SIGNUP`·`FIND_EMAIL`, 2026-09-22 아이디 찾기 추가). 비밀번호 재설정은 범위 밖(미착수)이다. 아이디 찾기 화면·API는 [auth-account](auth-account.md) 소유.
```

old:

```
- 용도(`NiceVerificationPurpose`)는 현재 `SIGNUP` 하나뿐이다. 이메일 찾기·비밀번호 재설정은 같은 검증 지점(`NiceVerificationService`)에 값만 추가하면 얹을 수 있다(범위 밖, 미착수).
```

new:

```
- 용도(`NiceVerificationPurpose`)는 `SIGNUP`·`FIND_EMAIL`이다. 팝업이 쿼리(`/nice-auth?purpose=`)로 받아 `request` 본문으로 보내고(필수, 기본값 없음), 서버가 레코드에 기록해 소비 시점(`requireFresh`)에 대조한다. 비밀번호 재설정은 같은 방식으로 값만 추가하면 얹을 수 있다(범위 밖).
```

- [ ] **Step 2: `auth-nice-verification.md` — 파일 지도**

old: `| enumeration | `{BE}/enumeration/NiceVerificationPurpose.java` | `SIGNUP`(현재 유일값) |`
new: `| enumeration | `{BE}/enumeration/NiceVerificationPurpose.java` | `SIGNUP`·`FIND_EMAIL` |`

old: `| dto | `{BE}/dto/request/NiceResultRequest.java` | `{ token }`(`@NotBlank`) |`
new(두 줄):

```
| dto | `{BE}/dto/request/NiceRequestRequest.java` | `{ purpose }`(`@NotNull`, 기본값 없음) |
| dto | `{BE}/dto/request/NiceResultRequest.java` | `{ token }`(`@NotBlank`) |
```

`NiceVerificationControllerTest.java` 행의 `(7건)` 또는 현재 적힌 건수를 Task 5 Step 2에서 기록한 건수로 바꾸고, 설명 끝에 `, purpose 필수·용도 전달`을 붙인다.

old: `| view | `{FE}/views/auth/pop-up/NiceAuthPopup.vue` | 가입용 NICE 실연동 팝업(`/nice-auth`). `request` 호출 후 표준창으로 폼 POST(진행 안내만, 입력 UI 없음) |`
new: `| view | `{FE}/views/auth/pop-up/NiceAuthPopup.vue` | NICE 실연동 팝업(`/nice-auth?purpose=SIGNUP` 또는 `FIND_EMAIL`). 쿼리 용도를 검사해 `request` 호출 후 표준창으로 폼 POST(진행 안내만, 입력 UI 없음) |`
(표 셀 안에 `|`를 쓰면 표가 깨진다 — 위처럼 "또는"으로 쓴다.)

아래 행을 **지운다**:

```
| view | `{FE}/views/auth/pop-up/NiceAuthMockPopup.vue` | **아이디·비밀번호 찾기 전용** 본인인증 목업(`/nice-auth/mock`). CI=`crypto.randomUUID()` |
```

old: `| api | `{FE}/api/auth/niceApi.ts` | `request`·`exchangeResult` |`
new: `| api | `{FE}/api/auth/niceApi.ts` | `request(purpose)`·`exchangeResult` |`

old: `| types | `{FE}/types/auth/nice.ts` | 요청·응답 타입, 상수 |`
new: `| types | `{FE}/types/auth/nice.ts` | 요청·응답 타입, 상수, `NiceVerificationPurpose`·`isNicePurpose` |`

old: `라우트 3개(`NiceAuthPopup`·`NiceAuthResult`·`NiceAuthMockPopup`, 전부 `public`)`
new: `라우트 2개(`NiceAuthPopup`·`NiceAuthResult`, 전부 `public`)`

- [ ] **Step 3: `auth-nice-verification.md` — API 계약**

old: `| 🟢 | POST | /auth/nice/request | 없음(세션으로 식별) | `{ encodeData }` | 공개 |`
new: `| 🟢 | POST | /auth/nice/request | `{ purpose: 'SIGNUP' \| 'FIND_EMAIL' }` 필수(세션으로 요청자 식별) | `{ encodeData }` | 공개 |`

old: `(`purpose=SIGNUP` 고정, `NiceVerificationController`가 하드코딩)`
new: `(`purpose`는 요청 본문으로 받는다 — 필수, 기본값 없음. 본문 없음·모르는 값은 400 `"Invalid request."`, `null`은 400 `"purpose는 필수입니다."`)`

- [ ] **Step 4: `auth-nice-verification.md` — 변경 레시피**

old(절 제목부터 4번 항목까지):

```
### 이메일 찾기·비밀번호 재설정에 NICE 본인확인 얹기
1. `{BE}/enumeration/NiceVerificationPurpose.java`에 값 추가(예: `FIND_EMAIL`·`RESET_PASSWORD`).
2. 해당 플로우 서비스에서 `NiceVerificationService.requireFresh(identity, purpose)`로 세션의 `NICE_VERIFIED`를 소비한다. 용도 대조를 빼면 가입용 인증 결과를 다른 흐름에 밀어 넣을 수 있다.
3. FE는 `NiceAuthPopup.vue`(또는 별도 팝업)로 `request`를 호출하고, 완료 후 목업(`NiceAuthMockPopup.vue`)을 실제 팝업으로 교체한다.
4. 새 `{BT}/service/nice/*Test.java` 케이스 추가, 카드 API 표·용어 갱신, `node tools/check-docs.mjs`.
```

new:

```
### 비밀번호 재설정에 NICE 본인확인 얹기
아이디 찾기(`ApplicantAccountRecoveryController.findEmail`, [auth-account](auth-account.md))가 참고 구현이다.
1. `{BE}/enumeration/NiceVerificationPurpose.java`와 `{FE}/types/auth/nice.ts`의 `NICE_PURPOSES`에 같은 값을 추가한다(예: `RESET_PASSWORD`).
2. 해당 컨트롤러에서 `NiceVerificationService.requireFresh(identity, purpose)`로 세션의 `NICE_VERIFIED`를 검사하고 소비(`removeAttribute`)한다. 용도 대조를 빼면 다른 흐름의 인증 결과를 밀어 넣을 수 있다.
3. FE는 `/nice-auth?purpose=<값>` 팝업을 열고 `postMessage`(origin·source 검사)로 결과를 받는다.
4. 용도 대조 양방향 테스트 추가, 카드 API 표·용어 갱신, `node tools/check-docs.mjs`.
```

- [ ] **Step 5: `auth-nice-verification.md` — 세 흐름 문단·검증 표**

old: `(2026-09-20 정리, 2026-09-21 가입분 구현 완료)`
new: `(2026-09-20 정리, 2026-09-21 가입분·2026-09-22 이메일 찾기분 구현 완료)`

old: `나머지 둘은 `NiceVerificationPurpose`에 값을 추가해 같은 검증 지점(`NiceVerificationService`)에 얹는다(범위 밖, 미착수 — 위 "변경 레시피").`
new: `이메일 찾기는 `FIND_EMAIL`로 얹었다. 비밀번호 재설정은 같은 방식으로 얹는다(범위 밖, 미착수 — 위 "변경 레시피").`

"검증" 절의 건수 표·합계 문장을 Task 5 Step 2 실측값으로 고친다(`NiceVerificationControllerTest` 건수와 합계, 가입 연동 줄의 `ApplicantSignUpControllerTest`·`SecurityConfig` 건수와 합계). 날짜 표기는 `2026-09-22 아이디 찾기 반영 후`로 바꾼다.

- [ ] **Step 6: `auth-account.md` — 요약**

old: `- 지원자 계정 기능: 가입, 이메일 가용성 확인, 비밀번호 변경, 전화번호 변경. 아이디 찾기, 비밀번호 재발급, 이메일 인증은 **프론트 목업**이다(백엔드 없음).`
new: `- 지원자 계정 기능: 가입, 이메일 가용성 확인, 아이디(이메일) 찾기, 비밀번호 변경, 전화번호 변경. 아이디 찾기는 NICE 본인확인(용도 `FIND_EMAIL`) 뒤 `find-email`이 부분 마스킹한 아이디를 준다(2026-09-22). 비밀번호 재발급, 이메일 인증은 **프론트 목업**이다(백엔드 없음).`

- [ ] **Step 7: `auth-account.md` — 파일 지도**

각 old 행 **바로 뒤에** new 행을 추가한다(old 행은 그대로 둔다):

| old 행(기준) | 뒤에 추가할 행 |
|---|---|
| `\| controller \| `{BE}/controller/ApplicantAccountController.java` \| 비밀번호·전화번호 변경 \|` | `\| controller \| `{BE}/controller/ApplicantAccountRecoveryController.java` \| 아이디 찾기(`find-email`) — 세션 NICE 결과 검사·소비 \|` |
| `\| service \| `{BE}/service/ApplicantAccountService.java` \| 현재 비밀번호 재확인 후 변경 \|` | `\| service \| `{BE}/service/ApplicantAccountRecoveryService.java` \| 식별 키로 계정 조회, `loginId` 부분 마스킹 \|` |
| `\| dto \| `{BE}/dto/request/ApplicantPhoneNumberChangeRequest.java` \| 전화번호 변경 요청 \|` | `\| dto \| `{BE}/dto/response/ApplicantFindEmailResponse.java` \| `{ maskedEmail }` \|` |
| `\| test \| `{BT}/service/ApplicantAccountServiceTest.java` \| 비밀번호 불일치·동일값 거부 \|` | `\| test \| `{BT}/service/ApplicantAccountRecoveryServiceTest.java` \| 조회·미존재·마스킹 규칙 \|` 그리고 `\| test \| `{BT}/controller/ApplicantAccountRecoveryControllerTest.java` \| 성공·1회용·세션 없음·용도 불일치·만료·404 \|` |

(표 안의 `\|`는 이 계획서의 이스케이프다. 카드에는 `|`로 쓴다.)

치환:

- old: `| repository | `{BE}/domain/repository/ApplicantRepository.java` | `findByLoginId`, `existsByEmail`, `existsByCiHash` |`
  new: `| repository | `{BE}/domain/repository/ApplicantRepository.java` | `findByLoginId`, `existsByEmail`, `existsByCiHash`, `findByCiHash` |`
- old: `, `NiceAuthMockPopup` `/nice-auth/mock`(전부 `public`)`
  new: `(전부 `public`)`
- old: `| view | `{FE}/views/applicant/AccountRecovery.vue` | 아이디 찾기·비밀번호 재발급(전체 목업) |`
  new: `| view | `{FE}/views/applicant/AccountRecovery.vue` | 아이디 찾기(NICE 실연동, 마스킹 아이디 표시)·비밀번호 재발급(목업) |`
- old: `| api | `{FE}/api/applicationApi.ts` | (공유) `signup`, `checkEmail`, `changePassword` |`
  new: `| api | `{FE}/api/applicationApi.ts` | (공유) `signup`, `checkEmail`, `findEmail`, `changePassword` |`
- old: `, `checkEmailRequest`, `ChangePasswordParams`, `ChangePasswordRequest` |`
  new: `, `checkEmailRequest`, `FindEmailResponse`, `ChangePasswordParams`, `ChangePasswordRequest` |`
- 아래 행을 **지운다**: `| types | `{FE}/types/window.ts` | `window.phoneAuthCallback` |`
- old: ``NiceAuthPopup.vue`·`NiceAuthResult.vue`·`NiceAuthMockPopup.vue`)은`
  new: ``NiceAuthPopup.vue`·`NiceAuthResult.vue`)은`
- old: `그 카드 소유 라우트 3개도`
  new: `그 카드 소유 라우트 2개도`

- [ ] **Step 8: `auth-account.md` — API 계약·상세**

`| 🟢 | GET | /auth/applicants/check-email | ...` 행 바로 뒤에 추가:

```
| 🟢 | POST | /auth/applicants/find-email | 없음(세션의 NICE 인증 결과, 용도 `FIND_EMAIL`) | `{ maskedEmail }` | 공개 |
```

`**GET /auth/applicants/check-email**:`로 시작하는 문단 바로 뒤(빈 줄 다음)에 추가:

```
**POST /auth/applicants/find-email** 🟢(2026-09-22): 세션 `NICE_VERIFIED`를 `requireFresh(purpose=FIND_EMAIL)`로 검사한 뒤 **조회 전에 제거**한다(인증 1회 = 조회 1회, 계정이 없어도 소비). 식별 키(`identityHash`)로 `findByCiHash` → `loginId`를 부분 마스킹해 응답한다(로컬부 3자 이상은 앞 2자, 2자 이하는 앞 1자 + 나머지 길이만큼 `*`(최소 1개), 도메인 그대로 — `abc12345@gmail.com` → `ab******@gmail.com`). 오류: 인증 없음·용도 불일치·만료 400(`requireFresh` 문구), 일치 계정 없음(미가입·파기) 404 `"본인인증 정보와 일치하는 계정이 없습니다."`. 원문 아이디·생년월일·성별은 응답·로그에 없다. 가입용 인증으로는 호출할 수 없고, 이 인증으로는 가입할 수 없다.
```

- [ ] **Step 9: `auth-account.md` — 규칙·프론트·함정**

old: `- 이메일 변경, 아이디(이메일) 찾기, 로그인 전 비밀번호 재설정 API는 아직 없다. **loginId 정책은 확정됐고**(아래 "함정·결정" — 이메일 = loginId), NICE 실연동 인프라는 가입 흐름에 만들어졌다(2026-09-21). 나머지 둘은 `NiceVerificationPurpose`에 값만 추가하면 얹을 수 있지만 범위 밖이라 아직 없다.`
new: `- 이메일 변경, 로그인 전 비밀번호 재설정 API는 아직 없다. **loginId 정책은 확정됐고**(아래 "함정·결정" — 이메일 = loginId), 아이디(이메일) 찾기는 NICE 실연동으로 구현됐다(2026-09-22).`

old: `  - **이메일 찾기**: NICE 본인인증으로 CI를 확보한 뒤 그 CI의 계정 이메일을 **마스킹해서** 표시한다(원문 노출 금지 — 이름·휴대폰만으로 남의 이메일을 수집하는 경로가 된다).`
new: `  - **이메일 찾기(구현됨)**: NICE 본인인증 결과의 식별 키로 계정을 찾아 `loginId`를 **부분 마스킹해서** 표시한다(원문 노출 금지 — 휴대폰을 잠깐 쥔 제3자에게 아이디가 드러나지 않게, 2026-09-22 사용자 결정). 상세는 위 API 상세.`

old: `아이디·비번 찾기(`AccountRecovery`)는 `/nice-auth/mock` 팝업이 옛 방식 그대로 `window.opener.phoneAuthCallback({ name, phoneNumber, ci })`를 호출한다(목업). 두 경우 모두`
new: `아이디 찾기(`AccountRecovery`)도 같은 방식으로 `/nice-auth?purpose=FIND_EMAIL` 팝업을 열고, 성공 알림을 받으면 `findEmail()`을 불러 마스킹 아이디를 표시한다(실패·404는 서버 문구 알림 후 인증 전 상태). 두 경우 모두`

old: `- **목업 보류**(8d7485d 결정, 2026-09-21 가입 NICE는 실연동으로 해제): 이메일 인증, 아이디 찾기(결과 `abc12345@gmail.com` 하드코딩), 비밀번호 재발급은 그대로 목업이다. 가입용 NICE 팝업(`/nice-auth`)이 실연동으로 바뀌며 공유하던 본인인증 목업이 끊겨, 아이디·비번 찾기 전용으로 `NiceAuthMockPopup.vue`(`/nice-auth/mock`)를 분리했다(CI 임의 UUID, 검사 `!name && !phoneNumber`는 둘 다 비어야 걸림). 나머지 연동 시 함께 교체.`
new: `- **목업 보류**(8d7485d 결정, 2026-09-21 가입·2026-09-22 아이디 찾기 NICE 실연동으로 해제): 이메일 인증, 비밀번호 재발급은 그대로 목업이다. 아이디 찾기 전용으로 잠시 분리했던 `NiceAuthMockPopup.vue`(`/nice-auth/mock`)와 `window.phoneAuthCallback` 타입(`types/window.ts`)은 사용처가 없어져 삭제했다.`

old: `남은 작업은 이메일 찾기·비밀번호 재설정 둘뿐이다.`
new: `남은 작업은 비밀번호 재설정뿐이다(이메일 찾기는 2026-09-22 구현).`

- [ ] **Step 10: 크기·점검**

Run(루트): `wc -c docs/domains/auth-account.md docs/domains/auth-nice-verification.md docs/domains/_index.md` 그리고 `node tools/check-docs.mjs`
Expected: 카드 두 개 모두 40KB(40,960B) 미만, `_index.md` 12KB 미만, `문서 점검 통과`. 누락 경로·소유 오류가 나면 고친다.

- [ ] **Step 11: 남은 옛 서술 확인**

Run(루트): `grep -n "nice-auth/mock\|NiceAuthMockPopup\|phoneAuthCallback\|아이디 찾기.*목업\|현재 유일값" docs/domains/*.md`
Expected: `auth-account.md`의 "목업 보류" 문단(삭제 사실 기록)과 "전역 본인인증 콜백" 결정 문단만 남는다. 현행 동작처럼 읽히는 서술이 있으면 고친다.

---

### Task 9: 마무리

**Files:**
- Create: `docs/archive/reports/nice-find-email_implementation.html`
- Move: `docs/superpowers/specs/2026-09-22-nice-find-email-design.md` → `docs/archive/superpowers/specs/`
- Move: `docs/superpowers/plans/2026-09-22-nice-find-email.md` → `docs/archive/superpowers/plans/`

- [ ] **Step 1: 최종 확인**

- 백엔드: Task 5 Step 1 명령 재실행 → BUILD SUCCESSFUL.
- 프론트: `npm run type-check` → 오류 0.
- 문서: `node tools/check-docs.mjs` → 통과.
- `git status --short`로 이번 작업 파일만 늘었는지 확인(다른 세션 파일은 그대로).

- [ ] **Step 2: 구현 리포트**

`design-report` 스킬로 `docs/archive/reports/nice-find-email_implementation.html`을 만든다. 내용: 설계 요약(D1~D6), 변경 파일, API 계약 변경 2건, 테스트 결과(Task 5 실측 건수), 미검증 항목(개발 서버 실인증 — ① 가입 계정 아이디 찾기 ② 미가입 명의 404 안내 ③ 가입 회귀), 범위 밖.

- [ ] **Step 3: 설계서·계획서 보관**

Run(루트): `git mv`는 추적 파일에만 쓸 수 있다. 두 문서가 미추적이면 일반 이동으로 옮긴다.

```bash
mv docs/superpowers/specs/2026-09-22-nice-find-email-design.md docs/archive/superpowers/specs/
mv docs/superpowers/plans/2026-09-22-nice-find-email.md docs/archive/superpowers/plans/
```

- [ ] **Step 4: 보고**

변경 파일 목록, 테스트 결과, 계약 변경(`request` 본문 필수·`find-email` 신규), 사용자 확인 필요 항목(개발 서버 실인증 3건)을 한국어로 보고한다. **커밋·푸시는 사용자가 요청할 때만** 한다. 요청받으면 다른 세션 파일과 `auth-nice-verification.md`의 `fileTree('libs')` 1줄 hunk는 제외하고 스테이징한다.
