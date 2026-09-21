# NICE 본인확인 실연동(가입 흐름) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 지원자 가입 시 NICE 체크플러스 본인확인을 실제로 수행하고, 서버가 복호화·검증해 확보한 CI로만 계정을 만든다.

**Architecture:** NICE 결과는 cross-site POST로 오므로 콜백에 세션 쿠키가 없다. 그래서 콜백은 `REQ_SEQ`만으로 인메모리 Store를 찾고, 세션에 값을 심는 일은 뒤따르는 same-site 요청(1회용 `resultToken` 교환)으로 미룬다. CI는 브라우저에 내려가지 않고 서버 세션에만 남는다. 벤더 모듈 호출은 `NiceClient` 인터페이스 뒤에 감추고, jar가 필요한 구현은 `RealNiceClient` 하나뿐이다.

**Tech Stack:** Spring Boot 4 · Java 17 · Spring Security(세션) · JPA · Vue 3.5 `<script setup>` · ant-design-vue 4 · Vitest

**설계서:** `docs/superpowers/specs/2026-09-21-nice-verification-signup-design.md`

---

## 사전 확인 (Task 1 전에 반드시)

이 계획의 Task 2는 **레거시 시스템에서 복사해 와야 하는 값**에 의존한다. 아래를 먼저 확보하지 못하면 Task 2에서 멈추고 사용자에게 묻는다.

| 필요한 것 | 어디서 |
|---|---|
| 요청 평문 키의 **정확한 철자와 순서** | 레거시 인코딩부(`fnEncode` 호출 직전 `sPlaindata` 조립 코드) |
| `AUTH_TYPE`·`POPUP_GUBUN`에 넣는 **지정값** | 같은 곳 |
| 응답 평문에서 **이름·휴대폰·CI·REQ_SEQ를 꺼낼 때 쓰는 키 이름** | 레거시 `checkplus_success.jsp`의 추출부 |

**`NiceID.jar`는 Task 15까지 필요 없다.** Task 1~14는 jar 없이 전부 구현·검증된다.

---

## 파일 구조

### 백엔드 신규

| 파일 | 책임 |
|---|---|
| `src/main/java/com/shinyoung/recruit/config/NiceProperties.java` | `recruit.nice.*` 설정 바인딩 |
| `src/main/java/com/shinyoung/recruit/config/NiceClientConfig.java` | `NiceClient` 빈 선택 + fail-closed 가드 |
| `src/main/java/com/shinyoung/recruit/service/nice/NiceClient.java` | 벤더 모듈 경계 인터페이스 |
| `src/main/java/com/shinyoung/recruit/service/nice/MockNiceClient.java` | jar 없이 동작하는 개발·테스트용 구현 |
| `src/main/java/com/shinyoung/recruit/service/nice/RealNiceClient.java` | `NiceID.Check.CPClient` 호출 |
| `src/main/java/com/shinyoung/recruit/service/nice/NicePlaindataCodec.java` | `키+바이트길이+값` 조립·파싱 (순수 로직) |
| `src/main/java/com/shinyoung/recruit/service/nice/NiceVerificationRecord.java` | Store 레코드(값 객체) |
| `src/main/java/com/shinyoung/recruit/service/nice/NiceVerificationStore.java` | 인메모리 저장 + TTL |
| `src/main/java/com/shinyoung/recruit/service/nice/NiceVerifiedIdentity.java` | 세션에 담기는 인증 결과(값 객체) |
| `src/main/java/com/shinyoung/recruit/service/nice/NiceVerificationService.java` | 발급 · 콜백 검증 · token 교환 |
| `src/main/java/com/shinyoung/recruit/controller/NiceVerificationController.java` | 엔드포인트 4개 |
| `src/main/java/com/shinyoung/recruit/enumeration/NiceVerificationPurpose.java` | `SIGNUP` |
| `src/main/java/com/shinyoung/recruit/enumeration/NiceVerificationStatus.java` | `PENDING`/`VERIFIED`/`FAIL` |
| `src/main/java/com/shinyoung/recruit/dto/response/NiceRequestResponse.java` | `{ encodeData }` |
| `src/main/java/com/shinyoung/recruit/dto/request/NiceResultRequest.java` | `{ token }` |
| `src/main/java/com/shinyoung/recruit/dto/response/NiceResultResponse.java` | `{ status, name, phoneNumber }` |
| `src/main/java/com/shinyoung/recruit/exception/NiceVerificationException.java` | 검증 실패 |

### 백엔드 수정

| 파일 | 변경 |
|---|---|
| `src/main/java/com/shinyoung/recruit/dto/request/ApplicantSignUpRequest.java` | `name`·`phoneNumber`·`ci` 제거 |
| `src/main/java/com/shinyoung/recruit/service/ApplicantSignUpService.java` | 인증 결과를 인자로 받음 |
| `src/main/java/com/shinyoung/recruit/controller/ApplicantSignUpController.java` | 세션 인증분 소비 후 전달 |
| `src/main/java/com/shinyoung/recruit/config/SecurityConfig.java` | `/api/auth/nice/**` permitAll |
| `src/main/resources/application.yaml` | `recruit.nice` 블록 |
| `build.gradle` | `implementation files('libs/NiceID.jar')` |

### 프론트

| 파일 | 변경 |
|---|---|
| `src/types/auth/nice.ts` | 신규 타입 |
| `src/api/auth/niceApi.ts` | 신규 API 모듈 |
| `src/views/auth/pop-up/NiceAuthPopup.vue` | 목업 제거, 실제 흐름 |
| `src/views/auth/pop-up/NiceAuthResult.vue` | 신규 결과 중계 화면 |
| `src/routes/authRoutes.ts` | `/nice-auth/result` 추가 |
| `src/views/applicant/SignupView.vue` | `postMessage` 수신, 읽기 전용화 |

---

## 공통 사항

**백엔드 테스트 실행** (`recruit_back/recruit_backend/`에서):

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.nice.*" --no-daemon
```

Windows PowerShell:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.nice.*" --no-daemon
```

`22791194512954214612461221261067`은 로컬 예시 키다. **운영 키를 쓰지 않는다.**

**`/api` 접두는 자동으로 붙는다.** `WebMvcConfig.configurePathMatch`가 `com.shinyoung.recruit.controller` 패키지 전체에 `/api`를 붙인다. 그래서 컨트롤러에는 `@RequestMapping("/auth/nice")`로 적고, `SecurityConfig` 매처와 MockMvc URL 에는 `/api/auth/nice/...`로 적는다.

**`Clock` 빈이 이미 있다** — `src/main/java/com/shinyoung/recruit/config/TimeConfig.java`. 새로 만들지 않고 주입만 받는다.

**커밋 금지.** 사용자가 명시적으로 지시할 때만 커밋한다(`AGENTS.md` 7장). 각 Task의 마지막 단계는 커밋이 아니라 **검증 결과 보고**다.

---

## Task 1: 열거형 2개

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/enumeration/NiceVerificationPurpose.java`
- Create: `src/main/java/com/shinyoung/recruit/enumeration/NiceVerificationStatus.java`

- [ ] **Step 1: `NiceVerificationPurpose` 작성**

```java
package com.shinyoung.recruit.enumeration;

/**
 * 본인확인을 요구한 흐름. 요청 발급 시 기록하고 결과 소비 시 대조한다.
 *
 * <p>용도를 대조하지 않으면 가입용으로 받은 인증 결과를 다른 흐름(이메일 찾기 등)에
 * 그대로 밀어 넣을 수 있다. 값이 하나뿐이어도 대조 지점을 먼저 만들어 둔다.
 */
public enum NiceVerificationPurpose {
    SIGNUP
}
```

- [ ] **Step 2: `NiceVerificationStatus` 작성**

```java
package com.shinyoung.recruit.enumeration;

/** 본인확인 요청 1건의 진행 상태. */
public enum NiceVerificationStatus {
    /** 요청 발급 완료, 콜백 대기. */
    PENDING,
    /** 콜백 수신·복호화 성공. resultToken 교환 대기. */
    VERIFIED,
    /** 사용자 취소 또는 NICE 실패. */
    FAIL
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
cd recruit_back/recruit_backend && ./gradlew compileJava --no-daemon
```

Expected: `BUILD SUCCESSFUL`

---

## Task 2: `NicePlaindataCodec` — 직렬화 규격

레거시가 쓰는 평문 형식은 `키 + 바이트길이 + ":" + 값`을 이어 붙인 것이다. **길이는 문자 수가 아니라 `getBytes().length`다.** 한글이 들어가면 달라지고, 틀리면 NICE는 실패 코드만 준다.

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/service/nice/NicePlaindataCodec.java`
- Test: `src/test/java/com/shinyoung/recruit/service/nice/NicePlaindataCodecTest.java`

**이 Task 는 레거시 키 이름과 무관하다.** 코덱은 임의의 키를 직렬화하는 범용 로직이다. 실제 키 이름이 필요한 곳은 Task 7(요청 조립)과 Task 8(응답 파싱)이다.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.shinyoung.recruit.service.nice;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NicePlaindataCodecTest {

    private final NicePlaindataCodec codec = new NicePlaindataCodec();

    @Test
    void encodeWritesKeyThenByteLengthThenValue() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", "abc");

        assertEquals("7:REQ_SEQ3:abc", codec.encode(fields));
    }

    @Test
    void encodeUsesByteLengthNotCharacterLength() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("NAME", "홍길동");

        // "홍길동" 은 3글자지만 UTF-8 로 9바이트다. 문자 수로 세면 NICE 가 거절한다.
        assertEquals("4:NAME9:홍길동", codec.encode(fields));
    }

    @Test
    void encodePreservesInsertionOrder() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", "a");
        fields.put("SITECODE", "b");

        assertEquals("7:REQ_SEQ1:a8:SITECODE1:b", codec.encode(fields));
    }

    @Test
    void decodeReturnsFieldsInOrder() {
        Map<String, String> decoded = codec.decode("7:REQ_SEQ3:abc4:NAME9:홍길동");

        assertEquals("abc", decoded.get("REQ_SEQ"));
        assertEquals("홍길동", decoded.get("NAME"));
        assertEquals(2, decoded.size());
    }

    @Test
    void decodeHandlesValueContainingColon() {
        // RTN_URL 에는 반드시 ':' 가 들어간다. 길이 기반으로 잘라야 깨지지 않는다.
        String url = "https://example.test/api/auth/nice/callback";
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("RTN_URL", url);

        assertEquals(url, codec.decode(codec.encode(fields)).get("RTN_URL"));
    }

    @Test
    void decodeRejectsTruncatedInput() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("7:REQ_SEQ9:abc"));
    }

    @Test
    void decodeRejectsMissingLengthDelimiter() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("7:REQ_SEQabc"));
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.nice.NicePlaindataCodecTest" --no-daemon
```

Expected: 컴파일 실패 — `cannot find symbol: class NicePlaindataCodec`

- [ ] **Step 3: 구현 작성**

```java
package com.shinyoung.recruit.service.nice;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NICE 체크플러스 평문 직렬화 규격 코덱.
 *
 * <p>형식은 {@code 키바이트길이:키 값바이트길이:값} 을 구분자 없이 이어 붙인 것이다.
 * 예: {@code 7:REQ_SEQ3:abc}
 *
 * <p><b>길이는 문자 수가 아니라 UTF-8 바이트 길이다.</b> 한글 값에서 갈리며,
 * 틀리면 NICE 는 원인을 알려주지 않고 실패 코드만 돌려준다.
 *
 * <p>순서도 규격의 일부라 {@link LinkedHashMap} 으로 보존한다.
 */
@Component
public class NicePlaindataCodec {

    public String encode(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            appendToken(sb, entry.getKey());
            appendToken(sb, entry.getValue());
        }
        return sb.toString();
    }

    public Map<String, String> decode(String plaindata) {
        Map<String, String> fields = new LinkedHashMap<>();
        Cursor cursor = new Cursor(plaindata);
        while (cursor.hasRemaining()) {
            String key = cursor.readToken();
            String value = cursor.readToken();
            fields.put(key, value);
        }
        return fields;
    }

    private void appendToken(StringBuilder sb, String value) {
        sb.append(value.getBytes(StandardCharsets.UTF_8).length).append(':').append(value);
    }

    /**
     * 바이트 길이로 잘라내는 커서. 값에 ':' 가 들어가도(RTN_URL 등) 안전하다.
     * 문자 단위로 자르면 한글에서 어긋나므로 바이트 배열 위에서 처리한다.
     */
    private static final class Cursor {
        private final byte[] bytes;
        private int position;

        private Cursor(String plaindata) {
            this.bytes = plaindata.getBytes(StandardCharsets.UTF_8);
        }

        private boolean hasRemaining() {
            return position < bytes.length;
        }

        private String readToken() {
            int delimiter = indexOfColon();
            int length = parseLength(delimiter);
            int valueStart = delimiter + 1;
            if (valueStart + length > bytes.length) {
                throw new IllegalArgumentException("평문이 선언된 길이보다 짧습니다.");
            }
            String value = new String(bytes, valueStart, length, StandardCharsets.UTF_8);
            position = valueStart + length;
            return value;
        }

        private int indexOfColon() {
            for (int i = position; i < bytes.length; i++) {
                if (bytes[i] == ':') {
                    return i;
                }
            }
            throw new IllegalArgumentException("길이 구분자(':')가 없습니다.");
        }

        private int parseLength(int delimiter) {
            String raw = new String(bytes, position, delimiter - position, StandardCharsets.UTF_8);
            try {
                int length = Integer.parseInt(raw);
                if (length < 0) {
                    throw new IllegalArgumentException("길이가 음수입니다: " + raw);
                }
                return length;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("길이를 숫자로 읽을 수 없습니다: " + raw, e);
            }
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.nice.NicePlaindataCodecTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 7 tests passed

---

## Task 3: `NiceProperties`

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/config/NiceProperties.java`
- Modify: `src/main/resources/application.yaml`

- [ ] **Step 1: `NiceProperties` 작성**

기존 `JusoProperties`와 같은 형태다(`@Component` + `@ConfigurationProperties(prefix = "recruit.*")`).

```java
package com.shinyoung.recruit.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * NICE 체크플러스 본인확인 연동 설정.
 *
 * <p>사이트코드·사이트패스워드는 운영 자격증명이므로 코드·문서·커밋에 값을 두지 않는다.
 * 환경변수로만 주입한다(AES_SECRET_KEY 와 같은 취급).
 *
 * <p>{@code mockEnabled} 와 {@code siteCode} 의 조합 검증은
 * {@link NiceClientConfig} 가 기동 시 수행한다.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "recruit.nice")
public class NiceProperties {

    /** NICE 발급 사이트코드. 실제 연동 시 필수. */
    private String siteCode = "";

    /** NICE 발급 사이트패스워드. 실제 연동 시 필수. */
    private String sitePassword = "";

    /** 인증 성공 시 NICE 팝업이 POST 할 우리 서버 URL. NICE 에 등록된 값과 같아야 한다. */
    private String returnUrl = "";

    /** 인증 실패·취소 시 POST 될 우리 서버 URL. */
    private String errorUrl = "";

    /** 개발용 Mock 구현 사용 여부. 운영은 반드시 false. */
    private boolean mockEnabled = false;

    /** 요청 발급 → 콜백 수신 허용 시간(분). 통신사 인증에 걸리는 시간. */
    @Min(1)
    private int requestTtlMinutes = 10;

    /** 인증 완료 → 가입 제출 허용 시간(분). 폼 작성 시간. */
    @Min(1)
    private int verifiedTtlMinutes = 30;

    public String getSiteCode() {
        return siteCode;
    }

    public void setSiteCode(String siteCode) {
        this.siteCode = siteCode;
    }

    public String getSitePassword() {
        return sitePassword;
    }

    public void setSitePassword(String sitePassword) {
        this.sitePassword = sitePassword;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getErrorUrl() {
        return errorUrl;
    }

    public void setErrorUrl(String errorUrl) {
        this.errorUrl = errorUrl;
    }

    public boolean isMockEnabled() {
        return mockEnabled;
    }

    public void setMockEnabled(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }

    public int getRequestTtlMinutes() {
        return requestTtlMinutes;
    }

    public void setRequestTtlMinutes(int requestTtlMinutes) {
        this.requestTtlMinutes = requestTtlMinutes;
    }

    public int getVerifiedTtlMinutes() {
        return verifiedTtlMinutes;
    }

    public void setVerifiedTtlMinutes(int verifiedTtlMinutes) {
        this.verifiedTtlMinutes = verifiedTtlMinutes;
    }
}
```

- [ ] **Step 2: `application.yaml`에 블록 추가**

`recruit:` 아래 `univ-info:` 블록 **다음**에 붙인다(같은 들여쓰기 2칸).

```yaml
  nice:
    # NICE 체크플러스 본인확인. 사이트코드·사이트패스워드는 운영 자격증명이라 저장소에 값을 두지 않는다.
    # 미설정 + mock-enabled=false 조합은 기동 실패한다(NiceClientConfig 가드).
    site-code: ${NICE_SITE_CODE:}
    site-password: ${NICE_SITE_PASSWORD:}
    # NICE 에 등록한 리턴 URL 2종. 평문에 실려 암호화되므로 등록값과 정확히 같아야 한다.
    return-url: ${NICE_RETURN_URL:}
    error-url: ${NICE_ERROR_URL:}
    # 개발 전용 Mock. 운영에서 true 면 기동이 거부된다.
    mock-enabled: ${NICE_MOCK_ENABLED:true}
    request-ttl-minutes: ${NICE_REQUEST_TTL_MINUTES:10}
    verified-ttl-minutes: ${NICE_VERIFIED_TTL_MINUTES:30}
```

`mock-enabled` 기본값이 `true`인 이유: 이 저장소에는 사이트코드가 없다. 기본을 `false`로 두면 **개발자가 받자마자 기동에 실패한다.** 운영은 `NICE_SITE_CODE`를 주입하므로 `NICE_MOCK_ENABLED=false`도 함께 주입해야 하고, 빠뜨리면 Task 15의 가드가 기동을 막는다.

- [ ] **Step 3: 기동 확인**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.RecruitApplicationTests" --no-daemon
```

Expected: `BUILD SUCCESSFUL` — 컨텍스트가 뜬다

---

## Task 4: `NiceClient` 인터페이스 + `MockNiceClient`

`NiceID.jar`는 아직 저장소에 없다. Mock을 jar와 무관하게 만들어 Task 14까지를 jar 없이 끝낸다.

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/service/nice/NiceClient.java`
- Create: `src/main/java/com/shinyoung/recruit/service/nice/MockNiceClient.java`
- Test: `src/test/java/com/shinyoung/recruit/service/nice/MockNiceClientTest.java`

- [ ] **Step 1: 인터페이스 작성**

```java
package com.shinyoung.recruit.service.nice;

/**
 * NICE 벤더 모듈 경계.
 *
 * <p>구현은 둘이다. {@link RealNiceClient} 는 {@code NiceID.jar} 의 {@code CPClient} 를 부르고,
 * {@link MockNiceClient} 는 jar 없이 같은 계약을 만족한다. 계약은 "{@code encode} 가 불투명
 * 문자열을 주고 {@code decode} 가 원래 평문을 돌려준다"뿐이라 Mock 으로도 우리 로직 전부를
 * 검증할 수 있다.
 */
public interface NiceClient {

    /**
     * 요청번호(REQ_SEQ) 발급. 실제 구현은 모듈의 {@code getRequestNo(사이트코드)} 를 부른다.
     * 길이·문자 규칙은 NICE 가 정하므로 우리가 만들지 않는다.
     */
    String generateRequestNo();

    /** 평문 → 암호문(EncodeData). */
    String encode(String plaindata);

    /** 암호문 → 평문. 실패하면 {@link NiceVerificationException}. */
    String decode(String encodeData);

    /**
     * 암호문이 만들어진 시각(epoch 초). 재전송 방어의 2차 게이트로 쓴다.
     * 실제 구현은 모듈의 {@code getCipherDateTime} 이 주는 {@code yyMMddHHmmss} 를 변환한다.
     */
    long cipherEpochSeconds(String encodeData);
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

```java
package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.exception.NiceVerificationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockNiceClientTest {

    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC);
    private final MockNiceClient client = new MockNiceClient(clock);

    @Test
    void encodeThenDecodeReturnsOriginalPlaindata() {
        String plaindata = "7:REQ_SEQ3:abc4:NAME9:홍길동";

        assertEquals(plaindata, client.decode(client.encode(plaindata)));
    }

    @Test
    void encodeDoesNotLeakPlaindataDirectly() {
        assertTrue(client.encode("7:REQ_SEQ3:abc").indexOf("REQ_SEQ") < 0);
    }

    @Test
    void cipherEpochSecondsReflectsEncodeTime() {
        assertEquals(1_700_000_000L, client.cipherEpochSeconds(client.encode("4:NAME1:a")));
    }

    @Test
    void decodeRejectsGarbage() {
        assertThrows(NiceVerificationException.class, () -> client.decode("not-a-valid-payload"));
    }

    @Test
    void generateRequestNoReturnsDistinctValues() {
        assertNotEquals(client.generateRequestNo(), client.generateRequestNo());
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.nice.MockNiceClientTest" --no-daemon
```

Expected: 컴파일 실패 — `cannot find symbol: class MockNiceClient`

- [ ] **Step 4: 예외 클래스 작성**

```java
package com.shinyoung.recruit.exception;

/**
 * 본인확인 검증 실패. 복호화 실패, 요청번호 불일치·만료·재사용, 세션 불일치를 모두 포함한다.
 *
 * <p>사용자에게는 원인을 구분해 보여주지 않는다. 어떤 값이 왜 틀렸는지 알려주면
 * 재전송을 시도하는 쪽에 정보를 주게 된다.
 */
public class NiceVerificationException extends RuntimeException {

    public NiceVerificationException(String message) {
        super(message);
    }

    public NiceVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

파일 경로: `src/main/java/com/shinyoung/recruit/exception/NiceVerificationException.java`

- [ ] **Step 5: `MockNiceClient` 작성**

```java
package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.exception.NiceVerificationException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * 개발·테스트용 {@link NiceClient}. <b>{@code NiceID.jar} 에 의존하지 않는다.</b>
 *
 * <p>jar 는 공개 저장소에 없고 확보 전에도 나머지 전부를 만들고 검증할 수 있어야 하므로,
 * 계약("encode 가 불투명 문자열을 주고 decode 가 원래 평문을 돌려준다")만 가역 인코딩으로
 * 충족한다. 암호학적 강도는 없다 — 그래서 운영 사용은 {@code NiceClientConfig} 가 막는다.
 *
 * <p>형식: {@code MOCK.<epoch초>.<Base64(평문)>}
 */
public class MockNiceClient implements NiceClient {

    private static final String PREFIX = "MOCK.";

    private final Clock clock;
    private final NicePlaindataCodec codec;

    public MockNiceClient(Clock clock, NicePlaindataCodec codec) {
        this.clock = clock;
        this.codec = codec;
    }

    @Override
    public String generateRequestNo() {
        return "MOCK" + UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public String encode(String plaindata) {
        String payload = Base64.getEncoder()
                .encodeToString(plaindata.getBytes(StandardCharsets.UTF_8));
        return PREFIX + clock.instant().getEpochSecond() + "." + payload;
    }

    @Override
    public String decode(String encodeData) {
        String payload = parts(encodeData)[2];
        try {
            return new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new NiceVerificationException("복호화에 실패했습니다.", e);
        }
    }

    @Override
    public long cipherEpochSeconds(String encodeData) {
        try {
            return Long.parseLong(parts(encodeData)[1]);
        } catch (NumberFormatException e) {
            throw new NiceVerificationException("복호화에 실패했습니다.", e);
        }
    }

    /** 실제 구현은 모듈의 {@code fnParse} 를 쓴다. Mock 은 우리 코덱에 위임한다. */
    @Override
    public Map<String, String> parse(String plaindata) {
        try {
            return codec.decode(plaindata);
        } catch (IllegalArgumentException e) {
            throw new NiceVerificationException("본인확인 결과를 읽을 수 없습니다.", e);
        }
    }

    private String[] parts(String encodeData) {
        if (encodeData == null || !encodeData.startsWith(PREFIX)) {
            throw new NiceVerificationException("복호화에 실패했습니다.");
        }
        String[] parts = encodeData.split("\\.", 3);
        if (parts.length != 3) {
            throw new NiceVerificationException("복호화에 실패했습니다.");
        }
        return parts;
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.nice.MockNiceClientTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 5 tests passed

---

## Task 5: `NiceVerificationRecord` · `NiceVerifiedIdentity`

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/service/nice/NiceVerificationRecord.java`
- Create: `src/main/java/com/shinyoung/recruit/service/nice/NiceVerifiedIdentity.java`

- [ ] **Step 1: `NiceVerifiedIdentity` 작성**

```java
package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;

import java.io.Serializable;
import java.time.Instant;

/**
 * 본인확인 결과 중 세션에 보관하는 부분.
 *
 * <p>{@code ci} 가 들어 있으므로 <b>이 객체를 그대로 응답에 담지 않는다.</b>
 * 화면에는 {@code name}, {@code phoneNumber} 만 내려간다.
 *
 * <p>HTTP 세션 속성으로 저장되므로 {@link Serializable} 이어야 한다.
 */
public record NiceVerifiedIdentity(
        NiceVerificationPurpose purpose,
        String name,
        String phoneNumber,
        String ci,
        Instant verifiedAt
) implements Serializable {
}
```

- [ ] **Step 2: `NiceVerificationRecord` 작성**

```java
package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.enumeration.NiceVerificationStatus;

import java.time.Instant;

/**
 * 본인확인 요청 1건. {@link NiceVerificationStore} 가 메모리에 들고 있는다.
 *
 * <p>불변이다. 상태가 바뀔 때마다 새 인스턴스를 만들어 교체한다 — 동시 접근에서
 * 부분 갱신된 상태가 보이지 않게 한다.
 *
 * <p>{@code sessionId} 는 요청을 낸 브라우저 세션이다. NICE 콜백에는 세션 쿠키가
 * 실리지 않으므로(cross-site POST) 콜백 시점에는 대조하지 못하고,
 * 뒤따르는 same-site 결과 교환에서 대조한다.
 */
public record NiceVerificationRecord(
        String reqSeq,
        NiceVerificationPurpose purpose,
        String sessionId,
        Instant issuedAt,
        NiceVerificationStatus status,
        String resultToken,
        Instant resultTokenIssuedAt,
        String name,
        String phoneNumber,
        String ci
) {

    public static NiceVerificationRecord pending(
            String reqSeq, NiceVerificationPurpose purpose, String sessionId, Instant issuedAt) {
        return new NiceVerificationRecord(
                reqSeq, purpose, sessionId, issuedAt,
                NiceVerificationStatus.PENDING, null, null, null, null, null);
    }

    public NiceVerificationRecord verified(
            String resultToken, Instant tokenIssuedAt, String name, String phoneNumber, String ci) {
        return new NiceVerificationRecord(
                reqSeq, purpose, sessionId, issuedAt,
                NiceVerificationStatus.VERIFIED, resultToken, tokenIssuedAt, name, phoneNumber, ci);
    }

    public NiceVerificationRecord failed(String resultToken, Instant tokenIssuedAt) {
        return new NiceVerificationRecord(
                reqSeq, purpose, sessionId, issuedAt,
                NiceVerificationStatus.FAIL, resultToken, tokenIssuedAt, null, null, null);
    }
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
cd recruit_back/recruit_backend && ./gradlew compileJava --no-daemon
```

Expected: `BUILD SUCCESSFUL`

---

## Task 6: `NiceVerificationStore`

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/service/nice/NiceVerificationStore.java`
- Test: `src/test/java/com/shinyoung/recruit/service/nice/NiceVerificationStoreTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NiceVerificationStoreTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);

    private final NiceVerificationStore store = new NiceVerificationStore();

    @Test
    void savedRecordIsFoundByReqSeq() {
        store.save(NiceVerificationRecord.pending("req-1", NiceVerificationPurpose.SIGNUP, "sess-1", NOW));

        Optional<NiceVerificationRecord> found = store.find("req-1");

        assertTrue(found.isPresent());
        assertEquals("sess-1", found.get().sessionId());
    }

    @Test
    void unknownReqSeqIsEmpty() {
        assertTrue(store.find("nope").isEmpty());
    }

    @Test
    void findByResultTokenLocatesRecord() {
        NiceVerificationRecord pending =
                NiceVerificationRecord.pending("req-2", NiceVerificationPurpose.SIGNUP, "sess-1", NOW);
        store.save(pending.verified("tok-2", NOW, "홍길동", "01012345678", "CI-VALUE"));

        assertEquals("req-2", store.findByResultToken("tok-2").orElseThrow().reqSeq());
    }

    @Test
    void removeDeletesRecord() {
        store.save(NiceVerificationRecord.pending("req-3", NiceVerificationPurpose.SIGNUP, "sess-1", NOW));

        store.remove("req-3");

        assertTrue(store.find("req-3").isEmpty());
    }

    @Test
    void purgeExpiredRemovesOnlyRecordsOlderThanTtl() {
        store.save(NiceVerificationRecord.pending("old", NiceVerificationPurpose.SIGNUP, "sess-1", NOW));
        store.save(NiceVerificationRecord.pending(
                "fresh", NiceVerificationPurpose.SIGNUP, "sess-1", NOW.plus(Duration.ofMinutes(9))));

        Clock later = Clock.fixed(NOW.plus(Duration.ofMinutes(11)), ZoneOffset.UTC);
        store.purgeExpired(later.instant(), Duration.ofMinutes(10));

        assertTrue(store.find("old").isEmpty());
        assertTrue(store.find("fresh").isPresent());
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.nice.NiceVerificationStoreTest" --no-daemon
```

Expected: 컴파일 실패 — `cannot find symbol: class NiceVerificationStore`

- [ ] **Step 3: 구현 작성**

```java
package com.shinyoung.recruit.service.nice;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 진행 중인 본인확인 요청 보관소.
 *
 * <p><b>인메모리다. DDL 을 추가하지 않는다.</b> 수명이 10분인 임시 상태라 영속화 가치가 없다.
 * 이 시스템은 이미 HTTP 세션이 인메모리라 같은 단일 인스턴스 전제 위에 있다.
 * 다중 인스턴스로 확장하면 세션과 함께 외부 저장소로 옮겨야 한다.
 *
 * <p>재기동하면 진행 중이던 인증이 끊긴다. 사용자는 재인증하면 되고, 빈도가 낮아 허용한다.
 */
@Component
public class NiceVerificationStore {

    private final Map<String, NiceVerificationRecord> records = new ConcurrentHashMap<>();

    public void save(NiceVerificationRecord record) {
        records.put(record.reqSeq(), record);
    }

    public Optional<NiceVerificationRecord> find(String reqSeq) {
        if (reqSeq == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(records.get(reqSeq));
    }

    public Optional<NiceVerificationRecord> findByResultToken(String resultToken) {
        if (resultToken == null) {
            return Optional.empty();
        }
        return records.values().stream()
                .filter(record -> resultToken.equals(record.resultToken()))
                .findFirst();
    }

    public void remove(String reqSeq) {
        records.remove(reqSeq);
    }

    /**
     * 발급 시각이 ttl 보다 오래된 레코드를 지운다.
     *
     * <p>만료 판정 자체는 읽는 시점에도 하므로 이 정리가 늦어져도 보안에 영향이 없다.
     * 메모리 누적을 막는 것이 목적이다.
     */
    public void purgeExpired(Instant now, Duration ttl) {
        Instant threshold = now.minus(ttl);
        records.values().removeIf(record -> record.issuedAt().isBefore(threshold));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.nice.NiceVerificationStoreTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 5 tests passed

---

## Task 7: `NiceVerificationService` — 요청 발급

> **레거시 키 확보 완료**(2026-09-21). 요청 평문은 아래 7개를 이 순서로 이어 붙인다.
> `REQ_SEQ` · `SITECODE` · `AUTH_TYPE`(빈 값) · `RTN_URL` · `ERR_URL` · `POPUP_GUBUN`(`"N"`) · `CUSTOMIZE`

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/service/nice/NiceVerificationService.java`
- Test: `src/test/java/com/shinyoung/recruit/service/nice/NiceVerificationServiceRequestTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`AUTH_TYPE`·`POPUP_GUBUN` 값과 키 순서는 Task 2 Step 1에서 확인한 레거시 값을 쓴다. 아래는 표준 샘플 기준이며 **다르면 레거시를 따르고 이 테스트의 기대값도 함께 고친다.**

```java
package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.config.NiceProperties;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.enumeration.NiceVerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NiceVerificationServiceRequestTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);

    private NiceVerificationStore store;
    private NicePlaindataCodec codec;
    private MockNiceClient client;
    private NiceVerificationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        store = new NiceVerificationStore();
        codec = new NicePlaindataCodec();
        client = new MockNiceClient(clock);

        NiceProperties properties = new NiceProperties();
        properties.setSiteCode("SITECODE");
        properties.setSitePassword("SITEPASS");
        properties.setReturnUrl("https://example.test/api/auth/nice/callback");
        properties.setErrorUrl("https://example.test/api/auth/nice/callback/error");

        service = new NiceVerificationService(client, codec, store, properties, clock);
    }

    @Test
    void requestStoresPendingRecordBoundToSession() {
        String encodeData = service.request(NiceVerificationPurpose.SIGNUP, "sess-1");

        assertNotNull(encodeData);
        Map<String, String> plaindata = codec.decode(client.decode(encodeData));
        NiceVerificationRecord record = store.find(plaindata.get("REQ_SEQ")).orElseThrow();

        assertEquals(NiceVerificationStatus.PENDING, record.status());
        assertEquals("sess-1", record.sessionId());
        assertEquals(NiceVerificationPurpose.SIGNUP, record.purpose());
        assertEquals(NOW, record.issuedAt());
    }

    @Test
    void requestPlaindataCarriesSiteCodeAndReturnUrls() {
        Map<String, String> plaindata =
                codec.decode(client.decode(service.request(NiceVerificationPurpose.SIGNUP, "sess-1")));

        assertEquals("SITECODE", plaindata.get("SITECODE"));
        assertEquals("https://example.test/api/auth/nice/callback", plaindata.get("RTN_URL"));
        assertEquals("https://example.test/api/auth/nice/callback/error", plaindata.get("ERR_URL"));
    }

    @Test
    void eachRequestGetsDistinctReqSeq() {
        Map<String, String> first =
                codec.decode(client.decode(service.request(NiceVerificationPurpose.SIGNUP, "sess-1")));
        Map<String, String> second =
                codec.decode(client.decode(service.request(NiceVerificationPurpose.SIGNUP, "sess-1")));

        org.junit.jupiter.api.Assertions.assertNotEquals(
                first.get("REQ_SEQ"), second.get("REQ_SEQ"));
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.nice.NiceVerificationServiceRequestTest" --no-daemon
```

Expected: 컴파일 실패 — `cannot find symbol: class NiceVerificationService`

- [ ] **Step 3: 서비스 작성 (요청 발급 부분만)**

```java
package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.config.NiceProperties;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 본인확인 요청 발급 · 콜백 검증 · 결과 교환.
 *
 * <p>세션을 직접 만지지 않는다. 세션 id 는 인자로 받고, 세션에 값을 심는 일은
 * 컨트롤러가 한다 — 서비스가 {@code HttpSession} 을 알면 단위 테스트가 서블릿
 * 컨테이너에 묶인다.
 */
@Service
public class NiceVerificationService {

    private static final Logger log = LoggerFactory.getLogger(NiceVerificationService.class);

    private final NiceClient niceClient;
    private final NicePlaindataCodec codec;
    private final NiceVerificationStore store;
    private final NiceProperties properties;
    private final Clock clock;

    public NiceVerificationService(
            NiceClient niceClient,
            NicePlaindataCodec codec,
            NiceVerificationStore store,
            NiceProperties properties,
            Clock clock) {
        this.niceClient = niceClient;
        this.codec = codec;
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 요청번호를 발급해 보관하고 팝업이 NICE 로 보낼 EncodeData 를 돌려준다.
     *
     * <p>키 이름과 순서는 규격이다. 바꾸면 NICE 가 원인을 알려주지 않고 실패한다.
     */
    public String request(NiceVerificationPurpose purpose, String sessionId) {
        String reqSeq = niceClient.generateRequestNo();
        Instant now = clock.instant();

        store.save(NiceVerificationRecord.pending(reqSeq, purpose, sessionId, now));

        // 키 이름·순서·값은 레거시 인코딩부에서 확인한 것이다(2026-09-21). 바꾸지 않는다.
        // AUTH_TYPE 과 CUSTOMIZE 는 빈 값이다 — 빼는 것과 다르다. 키는 있고 길이가 0 이다.
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", reqSeq);
        fields.put("SITECODE", properties.getSiteCode());
        fields.put("AUTH_TYPE", "");
        fields.put("RTN_URL", properties.getReturnUrl());
        fields.put("ERR_URL", properties.getErrorUrl());
        fields.put("POPUP_GUBUN", "N");
        fields.put("CUSTOMIZE", "");

        return niceClient.encode(codec.encode(fields));
    }
}
```

**레거시 확인분(2026-09-21)** — 키 순서는 `REQ_SEQ` → `SITECODE` → `AUTH_TYPE` → `RTN_URL` → `ERR_URL` → `POPUP_GUBUN` → `CUSTOMIZE` 7개다. `AUTH_TYPE`은 **빈 값**(인증수단 선택 화면을 NICE가 띄우는 기본 동작), `POPUP_GUBUN`은 `"N"`이다.

빈 값도 키는 나간다. `AUTH_TYPE`이 빈 문자열이면 평문에 `9:AUTH_TYPE0:`으로 직렬화된다 — 키를 빼는 것과 결과가 다르므로 `fields.put` 을 생략하면 안 된다.

- [ ] **Step 4: `NiceClient` 빈 임시 등록**

`Clock` 빈은 이미 있다(`src/main/java/com/shinyoung/recruit/config/TimeConfig.java`). 새로 만들지 않는다.

Task 15에서 가드와 함께 정식 설정을 만든다. 그 전까지 컨텍스트가 뜨도록 임시 빈을 둔다 — **Task 15에서 이 파일 내용을 통째로 교체한다.**

`src/main/java/com/shinyoung/recruit/config/NiceClientConfig.java`:

```java
package com.shinyoung.recruit.config;

import com.shinyoung.recruit.service.nice.MockNiceClient;
import com.shinyoung.recruit.service.nice.NiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Task 15 에서 fail-closed 가드로 교체한다. 그때까지는 Mock 만 등록한다. */
@Configuration
public class NiceClientConfig {

    @Bean
    public NiceClient niceClient(Clock clock) {
        return new MockNiceClient(clock);
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.nice.NiceVerificationServiceRequestTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 3 tests passed

---

## Task 8: `NiceVerificationService` — 콜백 검증

콜백은 재전송 방어의 핵심이다. 네 가지를 통과해야 한다: 복호화 성공, 요청번호 존재, `PENDING` 상태, TTL 이내. 여기에 암호문 생성 시각 검사가 독립된 2차 게이트로 붙는다.

**Files:**
- Modify: `src/main/java/com/shinyoung/recruit/service/nice/NiceVerificationService.java`
- Test: `src/test/java/com/shinyoung/recruit/service/nice/NiceVerificationServiceCallbackTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.config.NiceProperties;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.enumeration.NiceVerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NiceVerificationServiceCallbackTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);

    private NiceVerificationStore store;
    private NicePlaindataCodec codec;
    private MockNiceClient client;
    private NiceProperties properties;

    @BeforeEach
    void setUp() {
        store = new NiceVerificationStore();
        codec = new NicePlaindataCodec();
        client = new MockNiceClient(Clock.fixed(NOW, ZoneOffset.UTC));

        properties = new NiceProperties();
        properties.setSiteCode("SITECODE");
        properties.setSitePassword("SITEPASS");
        properties.setReturnUrl("https://example.test/api/auth/nice/callback");
        properties.setErrorUrl("https://example.test/api/auth/nice/callback/error");
    }

    private NiceVerificationService serviceAt(Instant now) {
        return new NiceVerificationService(
                client, codec, store, properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    /** NICE 가 돌려주는 성공 응답을 흉내낸 암호문. */
    private String niceSuccessResponse(String reqSeq) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", reqSeq);
        fields.put("NAME", "홍길동");
        fields.put("MOBILE_NO", "01012345678");
        fields.put("CI", "CI-VALUE-1");
        fields.put("DI", "DI-VALUE-1");
        return client.encode(codec.encode(fields));
    }

    private String issueRequest(NiceVerificationService service) {
        String encodeData = service.request(NiceVerificationPurpose.SIGNUP, "sess-1");
        return codec.decode(client.decode(encodeData)).get("REQ_SEQ");
    }

    @Test
    void successfulCallbackMarksRecordVerifiedAndIssuesToken() {
        NiceVerificationService service = serviceAt(NOW);
        String reqSeq = issueRequest(service);

        String token = service.handleCallback(niceSuccessResponse(reqSeq));

        assertNotNull(token);
        NiceVerificationRecord record = store.find(reqSeq).orElseThrow();
        assertEquals(NiceVerificationStatus.VERIFIED, record.status());
        assertEquals("홍길동", record.name());
        assertEquals("01012345678", record.phoneNumber());
        assertEquals("CI-VALUE-1", record.ci());
        assertEquals(token, record.resultToken());
    }

    @Test
    void replayedCallbackIsRejected() {
        NiceVerificationService service = serviceAt(NOW);
        String reqSeq = issueRequest(service);
        String response = niceSuccessResponse(reqSeq);
        service.handleCallback(response);

        // 같은 암호문을 다시 넣는다. 레코드가 PENDING 이 아니므로 거부돼야 한다.
        org.junit.jupiter.api.Assertions.assertThrows(
                com.shinyoung.recruit.exception.NiceVerificationException.class,
                () -> service.handleCallback(response));
    }

    @Test
    void callbackAfterRequestTtlIsRejected() {
        NiceVerificationService service = serviceAt(NOW);
        String reqSeq = issueRequest(service);
        String response = niceSuccessResponse(reqSeq);

        NiceVerificationService late = serviceAt(NOW.plus(Duration.ofMinutes(11)));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.shinyoung.recruit.exception.NiceVerificationException.class,
                () -> late.handleCallback(response));
    }

    @Test
    void callbackWithUnknownReqSeqIsRejected() {
        NiceVerificationService service = serviceAt(NOW);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.shinyoung.recruit.exception.NiceVerificationException.class,
                () -> service.handleCallback(niceSuccessResponse("never-issued")));
    }

    @Test
    void callbackWithStaleCipherTimeIsRejected() {
        NiceVerificationService service = serviceAt(NOW);
        String reqSeq = issueRequest(service);

        // 암호문은 11분 전에 만들어졌는데 요청 레코드는 방금 발급된 상황.
        // Store 대조는 통과하지만 2차 게이트가 걸러야 한다.
        MockNiceClient staleClient =
                new MockNiceClient(Clock.fixed(NOW.minus(Duration.ofMinutes(11)), ZoneOffset.UTC));
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", reqSeq);
        fields.put("NAME", "홍길동");
        fields.put("MOBILE_NO", "01012345678");
        fields.put("CI", "CI-VALUE-1");
        String stale = staleClient.encode(codec.encode(fields));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.shinyoung.recruit.exception.NiceVerificationException.class,
                () -> service.handleCallback(stale));
    }

    @Test
    void errorCallbackMarksRecordFailedAndIssuesToken() {
        NiceVerificationService service = serviceAt(NOW);
        String reqSeq = issueRequest(service);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", reqSeq);
        fields.put("ERR_CODE", "9999");
        String token = service.handleErrorCallback(client.encode(codec.encode(fields)));

        assertNotNull(token);
        assertEquals(NiceVerificationStatus.FAIL, store.find(reqSeq).orElseThrow().status());
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.nice.NiceVerificationServiceCallbackTest" --no-daemon
```

Expected: 컴파일 실패 — `cannot find symbol: method handleCallback`

- [ ] **Step 3: 콜백 처리 추가**

`NiceVerificationService`에 아래를 추가한다. import 도 함께 넣는다.

```java
import com.shinyoung.recruit.enumeration.NiceVerificationStatus;
import com.shinyoung.recruit.exception.NiceVerificationException;

import java.time.Duration;
import java.util.UUID;
```

메서드:

```java
    /**
     * NICE 성공 콜백 처리. 1회용 resultToken 을 돌려준다.
     *
     * <p>이 요청에는 세션 쿠키가 실리지 않는다(cross-site POST, SameSite=Lax).
     * 그래서 세션 대조는 여기서 못 하고 {@link #exchangeResult} 로 미룬다.
     */
    public String handleCallback(String encodeData) {
        Map<String, String> fields = decodeFields(encodeData);
        NiceVerificationRecord record = consumePending(fields.get("REQ_SEQ"));
        verifyCipherTime(encodeData);

        String token = newResultToken();
        store.save(record.verified(
                token,
                clock.instant(),
                required(fields, "NAME"),
                required(fields, "MOBILE_NO"),
                required(fields, "CI")));
        return token;
    }

    /** NICE 실패·취소 콜백 처리. 성공 경로와 같은 결과 화면으로 보내기 위해 token 을 발급한다. */
    public String handleErrorCallback(String encodeData) {
        Map<String, String> fields = decodeFields(encodeData);
        NiceVerificationRecord record = consumePending(fields.get("REQ_SEQ"));

        String token = newResultToken();
        store.save(record.failed(token, clock.instant()));
        return token;
    }

    /**
     * 응답 파싱은 모듈에 맡긴다({@code fnParse}). 레거시도 그렇게 한다.
     * 요청 조립만 우리 코덱이 한다 — 모듈에 조립 함수가 없다.
     */
    private Map<String, String> decodeFields(String encodeData) {
        return niceClient.parse(niceClient.decode(encodeData));
    }

    /**
     * 요청번호로 레코드를 찾아 재사용 가능한 상태인지 확인한다.
     *
     * <p>이 검사가 재전송 방어의 본체다. 캡처된 응답을 다시 넣으면 레코드가 이미
     * {@code PENDING} 이 아니라 여기서 걸린다.
     */
    private NiceVerificationRecord consumePending(String reqSeq) {
        NiceVerificationRecord record = store.find(reqSeq)
                .orElseThrow(() -> new NiceVerificationException("유효하지 않은 본인확인 요청입니다."));

        if (record.status() != NiceVerificationStatus.PENDING) {
            throw new NiceVerificationException("이미 처리된 본인확인 요청입니다.");
        }
        if (isExpired(record.issuedAt(), properties.getRequestTtlMinutes())) {
            throw new NiceVerificationException("본인확인 요청이 만료되었습니다.");
        }
        return record;
    }

    /**
     * 암호문 생성 시각 검사. Store 대조와 독립된 2차 게이트다.
     * 두 검사 중 하나만 걸려도 거부한다.
     */
    private void verifyCipherTime(String encodeData) {
        Instant cipherTime = Instant.ofEpochSecond(niceClient.cipherEpochSeconds(encodeData));
        if (isExpired(cipherTime, properties.getRequestTtlMinutes())) {
            throw new NiceVerificationException("본인확인 결과가 만료되었습니다.");
        }
    }

    private boolean isExpired(Instant issuedAt, int ttlMinutes) {
        return issuedAt.isBefore(clock.instant().minus(Duration.ofMinutes(ttlMinutes)));
    }

    /**
     * 응답에서 필수 필드를 꺼낸다.
     *
     * <p>키 이름은 모듈의 {@code fnParse} 가 정한다. 운영 사이트코드가 1개뿐이라
     * 실응답의 키 구성을 배포 전에 확인할 수 없으므로, 없을 때 <b>실제로 온 키 목록을
     * 로그로 남긴다</b> — 첫 실인증에서 바로 진짜 이름이 드러난다.
     *
     * <p>키 목록은 로그에만 남긴다. 예외 메시지는 사용자 응답으로 나가므로 넣지 않는다.
     */
    private String required(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) {
            log.warn("본인확인 응답에 필수 키 {} 가 없습니다. 수신된 키: {}", key, fields.keySet());
            throw new NiceVerificationException("본인확인 결과를 읽을 수 없습니다.");
        }
        return value;
    }

    private String newResultToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
```

응답 필드 키(`NAME`·`MOBILE_NO`·`CI`)는 표준 샘플 기준이다. **Task 2 Step 1에서 확인한 레거시 추출부와 다르면 레거시를 따르고 테스트도 함께 고친다.**

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.nice.NiceVerificationServiceCallbackTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 6 tests passed

- [ ] **Step 5: 변이 검증 — 재전송 가드가 실제로 동작하는지**

`consumePending`의 상태 검사를 잠시 지운다.

```java
        // if (record.status() != NiceVerificationStatus.PENDING) {
        //     throw new NiceVerificationException("이미 처리된 본인확인 요청입니다.");
        // }
```

테스트를 다시 돌린다. `replayedCallbackIsRejected`가 **실패해야 한다.** 실패하지 않으면 그 테스트는 다른 이유로 통과하고 있는 것이니 원인을 찾아 고친다.

확인했으면 **주석을 원복한다.**

---

## Task 9: `NiceVerificationService` — 결과 교환과 세션 소비

**Files:**
- Modify: `src/main/java/com/shinyoung/recruit/service/nice/NiceVerificationService.java`
- Test: `src/test/java/com/shinyoung/recruit/service/nice/NiceVerificationServiceExchangeTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.config.NiceProperties;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.exception.NiceVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NiceVerificationServiceExchangeTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);

    private NiceVerificationStore store;
    private NicePlaindataCodec codec;
    private MockNiceClient client;
    private NiceProperties properties;

    @BeforeEach
    void setUp() {
        store = new NiceVerificationStore();
        codec = new NicePlaindataCodec();
        client = new MockNiceClient(Clock.fixed(NOW, ZoneOffset.UTC));

        properties = new NiceProperties();
        properties.setSiteCode("SITECODE");
        properties.setSitePassword("SITEPASS");
        properties.setReturnUrl("https://example.test/api/auth/nice/callback");
        properties.setErrorUrl("https://example.test/api/auth/nice/callback/error");
    }

    private NiceVerificationService serviceAt(Instant now) {
        return new NiceVerificationService(
                client, codec, store, properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private String verifiedToken(NiceVerificationService service) {
        String encodeData = service.request(NiceVerificationPurpose.SIGNUP, "sess-1");
        String reqSeq = codec.decode(client.decode(encodeData)).get("REQ_SEQ");

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", reqSeq);
        fields.put("NAME", "홍길동");
        fields.put("MOBILE_NO", "01012345678");
        fields.put("CI", "CI-VALUE-1");
        return service.handleCallback(client.encode(codec.encode(fields)));
    }

    @Test
    void exchangeReturnsIdentityForMatchingSession() {
        NiceVerificationService service = serviceAt(NOW);
        String token = verifiedToken(service);

        NiceVerifiedIdentity identity = service.exchangeResult(token, "sess-1");

        assertEquals("홍길동", identity.name());
        assertEquals("01012345678", identity.phoneNumber());
        assertEquals("CI-VALUE-1", identity.ci());
        assertEquals(NiceVerificationPurpose.SIGNUP, identity.purpose());
    }

    @Test
    void exchangeRemovesRecordSoTokenCannotBeReused() {
        NiceVerificationService service = serviceAt(NOW);
        String token = verifiedToken(service);

        service.exchangeResult(token, "sess-1");

        assertThrows(NiceVerificationException.class, () -> service.exchangeResult(token, "sess-1"));
    }

    @Test
    void exchangeFromDifferentSessionIsRejected() {
        NiceVerificationService service = serviceAt(NOW);
        String token = verifiedToken(service);

        assertThrows(NiceVerificationException.class, () -> service.exchangeResult(token, "sess-OTHER"));
    }

    @Test
    void exchangeAfterTokenTtlIsRejected() {
        NiceVerificationService service = serviceAt(NOW);
        String token = verifiedToken(service);

        NiceVerificationService late = serviceAt(NOW.plus(Duration.ofSeconds(61)));

        assertThrows(NiceVerificationException.class, () -> late.exchangeResult(token, "sess-1"));
    }

    @Test
    void exchangeOfFailedRecordThrowsFailure() {
        NiceVerificationService service = serviceAt(NOW);
        String encodeData = service.request(NiceVerificationPurpose.SIGNUP, "sess-1");
        String reqSeq = codec.decode(client.decode(encodeData)).get("REQ_SEQ");

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", reqSeq);
        fields.put("ERR_CODE", "9999");
        String token = service.handleErrorCallback(client.encode(codec.encode(fields)));

        assertThrows(NiceVerificationException.class, () -> service.exchangeResult(token, "sess-1"));
        // 실패 레코드도 교환 시점에 제거돼야 메모리에 남지 않는다.
        assertTrue(store.find(reqSeq).isEmpty());
    }

    @Test
    void verifiedIdentityIsAcceptedWithinVerifiedTtl() {
        NiceVerificationService service = serviceAt(NOW);
        NiceVerifiedIdentity identity = service.exchangeResult(verifiedToken(service), "sess-1");

        NiceVerificationService later = serviceAt(NOW.plus(Duration.ofMinutes(29)));

        assertEquals(identity, later.requireFresh(identity, NiceVerificationPurpose.SIGNUP));
    }

    @Test
    void verifiedIdentityExpiresAfterVerifiedTtl() {
        NiceVerificationService service = serviceAt(NOW);
        NiceVerifiedIdentity identity = service.exchangeResult(verifiedToken(service), "sess-1");

        NiceVerificationService later = serviceAt(NOW.plus(Duration.ofMinutes(31)));

        assertThrows(NiceVerificationException.class,
                () -> later.requireFresh(identity, NiceVerificationPurpose.SIGNUP));
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.nice.NiceVerificationServiceExchangeTest" --no-daemon
```

Expected: 컴파일 실패 — `cannot find symbol: method exchangeResult`

- [ ] **Step 3: 교환·검증 메서드 추가**

`NiceVerificationService`에 상수와 메서드를 추가한다.

```java
    /** resultToken 유효시간. 팝업이 리다이렉트 직후 교환하므로 짧게 둔다. */
    private static final Duration RESULT_TOKEN_TTL = Duration.ofMinutes(1);
```

```java
    /**
     * resultToken 을 인증 결과로 교환한다. <b>1회용이다</b> — 성공·실패 모두 레코드를 제거한다.
     *
     * <p>이 요청은 same-site 라 세션 쿠키가 실린다. 그래서 여기서 세션 대조를 한다.
     * 토큰이 유출돼도 다른 세션에서는 쓸 수 없다.
     */
    public NiceVerifiedIdentity exchangeResult(String resultToken, String sessionId) {
        NiceVerificationRecord record = store.findByResultToken(resultToken)
                .orElseThrow(() -> new NiceVerificationException("유효하지 않은 본인확인 결과입니다."));

        store.remove(record.reqSeq());

        if (!record.sessionId().equals(sessionId)) {
            throw new NiceVerificationException("본인확인 요청과 다른 세션입니다.");
        }
        if (record.resultTokenIssuedAt().isBefore(clock.instant().minus(RESULT_TOKEN_TTL))) {
            throw new NiceVerificationException("본인확인 결과가 만료되었습니다.");
        }
        if (record.status() != NiceVerificationStatus.VERIFIED) {
            throw new NiceVerificationException("본인확인에 실패했습니다.");
        }

        return new NiceVerifiedIdentity(
                record.purpose(), record.name(), record.phoneNumber(), record.ci(), clock.instant());
    }

    /**
     * 세션에 들어 있는 인증 결과가 아직 쓸 수 있는지 확인한다. 가입 제출 시점에 부른다.
     *
     * <p>용도를 대조하지 않으면 가입용 인증을 다른 흐름에 밀어 넣을 수 있다.
     */
    public NiceVerifiedIdentity requireFresh(
            NiceVerifiedIdentity identity, NiceVerificationPurpose expectedPurpose) {
        if (identity == null) {
            throw new NiceVerificationException("본인인증을 먼저 진행해주세요.");
        }
        if (identity.purpose() != expectedPurpose) {
            throw new NiceVerificationException("본인인증 용도가 일치하지 않습니다.");
        }
        if (isExpired(identity.verifiedAt(), properties.getVerifiedTtlMinutes())) {
            throw new NiceVerificationException("본인인증이 만료되었습니다. 다시 진행해주세요.");
        }
        return identity;
    }
```

`store.remove`를 검사보다 **먼저** 부르는 것이 의도다. 세션 불일치·만료로 거부하더라도 레코드는 사라져야 한다. 남겨 두면 공격자가 조건을 바꿔 가며 같은 토큰을 계속 시도할 수 있다.

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.nice.NiceVerificationServiceExchangeTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 7 tests passed

- [ ] **Step 5: 변이 검증 — 세션 대조가 실제로 동작하는지**

세션 검사를 잠시 지운다.

```java
        // if (!record.sessionId().equals(sessionId)) {
        //     throw new NiceVerificationException("본인확인 요청과 다른 세션입니다.");
        // }
```

`exchangeFromDifferentSessionIsRejected`가 **실패해야 한다.** 확인 후 원복한다.

---

## Task 10: 만료 정리 스케줄

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/service/nice/NiceVerificationCleanupScheduler.java`

- [ ] **Step 1: 기존 스케줄러 패턴 확인**

```bash
cd recruit_back/recruit_backend && grep -rn "@Scheduled" src/main/java --include=*.java | head
```

`@EnableScheduling`이 이미 켜져 있는지 확인한다. `RetentionPurgeScheduler`가 `@Scheduled`를 쓰고 있으면 켜져 있다.

- [ ] **Step 2: 스케줄러 작성**

```java
package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.config.NiceProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * 만료된 본인확인 레코드 정리.
 *
 * <p>만료 판정 자체는 읽는 시점에도 하므로 이 정리는 보안 장치가 아니라
 * <b>메모리 누적 방지</b>가 목적이다. 늦게 돌아도 무방하다.
 *
 * <p>정리 기준은 요청 TTL 이 아니라 인증 완료 TTL 이다 — 요청 TTL 로 지우면
 * 아직 유효한 인증 완료 레코드를 없앨 수 있다.
 */
@Component
public class NiceVerificationCleanupScheduler {

    private final NiceVerificationStore store;
    private final NiceProperties properties;
    private final Clock clock;

    public NiceVerificationCleanupScheduler(
            NiceVerificationStore store, NiceProperties properties, Clock clock) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${recruit.nice.cleanup-interval-ms:300000}")
    public void purgeExpired() {
        store.purgeExpired(
                clock.instant(), Duration.ofMinutes(properties.getVerifiedTtlMinutes()));
    }
}
```

- [ ] **Step 3: 기동 확인**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.RecruitApplicationTests" --no-daemon
```

Expected: `BUILD SUCCESSFUL`

---

## Task 11: DTO 3개 + `NiceVerificationController`

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/dto/response/NiceRequestResponse.java`
- Create: `src/main/java/com/shinyoung/recruit/dto/request/NiceResultRequest.java`
- Create: `src/main/java/com/shinyoung/recruit/dto/response/NiceResultResponse.java`
- Create: `src/main/java/com/shinyoung/recruit/controller/NiceVerificationController.java`
- Modify: `src/main/java/com/shinyoung/recruit/config/SecurityConfig.java`

- [ ] **Step 1: DTO 작성**

`NiceRequestResponse.java`:

```java
package com.shinyoung.recruit.dto.response;

/** 팝업이 NICE 표준창으로 POST 할 암호문. */
public record NiceRequestResponse(String encodeData) {
}
```

`NiceResultRequest.java`:

```java
package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 콜백 리다이렉트로 받은 1회용 토큰을 인증 결과로 교환하는 요청. */
public record NiceResultRequest(
        @NotBlank(message = "token은 필수입니다.")
        String token
) {
}
```

`NiceResultResponse.java`:

```java
package com.shinyoung.recruit.dto.response;

/**
 * 팝업 결과 화면에 내려가는 값.
 *
 * <p><b>ci 를 담지 않는다.</b> CI 는 서버 세션에만 두고 브라우저로 내리지 않는다 —
 * 이것이 클라이언트가 보낸 ci 를 믿던 기존 구조와의 핵심 차이다.
 */
public record NiceResultResponse(String status, String name, String phoneNumber) {

    public static NiceResultResponse success(String name, String phoneNumber) {
        return new NiceResultResponse("SUCCESS", name, phoneNumber);
    }
}
```

- [ ] **Step 2: 컨트롤러 작성**

```java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.NiceResultRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.NiceRequestResponse;
import com.shinyoung.recruit.dto.response.NiceResultResponse;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.service.nice.NiceVerificationService;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * NICE 체크플러스 본인확인 엔드포인트.
 *
 * <p>{@code /callback} 과 {@code /callback/error} 는 <b>NICE 팝업이 POST 하는 cross-site 요청</b>이다.
 * 세션 쿠키가 실리지 않고(SameSite=Lax) form-urlencoded 로 온다. 그래서 JSON 이 아니라
 * {@code @RequestParam} 으로 받고, 세션 대조는 뒤따르는 {@code /result} 에서 한다.
 */
@RestController
@RequestMapping("/auth/nice")
public class NiceVerificationController {

    /** 팝업이 로드할 프론트 결과 라우트. 백엔드는 HTML 을 반환하지 않는다. */
    private static final String RESULT_PATH = "/nice-auth/result?token=";

    /** 세션에 인증 결과를 담는 키. */
    public static final String VERIFIED_SESSION_KEY = "NICE_VERIFIED";

    private final NiceVerificationService niceVerificationService;

    public NiceVerificationController(NiceVerificationService niceVerificationService) {
        this.niceVerificationService = niceVerificationService;
    }

    @PostMapping("/request")
    public ResponseEntity<ApiResponse<NiceRequestResponse>> request(HttpSession session) {
        String encodeData =
                niceVerificationService.request(NiceVerificationPurpose.SIGNUP, session.getId());
        return ResponseEntity.ok(ApiResponse.success(new NiceRequestResponse(encodeData)));
    }

    @PostMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam("EncodeData") String encodeData) {
        return redirectToResult(niceVerificationService.handleCallback(encodeData));
    }

    @PostMapping("/callback/error")
    public ResponseEntity<Void> errorCallback(@RequestParam("EncodeData") String encodeData) {
        return redirectToResult(niceVerificationService.handleErrorCallback(encodeData));
    }

    @PostMapping("/result")
    public ResponseEntity<ApiResponse<NiceResultResponse>> result(
            @Valid @RequestBody NiceResultRequest request, HttpSession session) {
        NiceVerifiedIdentity identity =
                niceVerificationService.exchangeResult(request.token(), session.getId());
        session.setAttribute(VERIFIED_SESSION_KEY, identity);
        return ResponseEntity.ok(
                ApiResponse.success(NiceResultResponse.success(identity.name(), identity.phoneNumber())));
    }

    /**
     * POST 로 받아 GET 으로 넘긴다. 303 을 쓰는 이유는 302 가 일부 브라우저에서
     * 메서드를 보존해 POST 로 프론트 라우트를 치기 때문이다.
     */
    private ResponseEntity<Void> redirectToResult(String token) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(URI.create(RESULT_PATH + token))
                .build();
    }
}
```

- [ ] **Step 3: `SecurityConfig`에 매처 추가**

`SecurityConfig`의 `authorizeHttpRequests` 블록에서 기존 `/api/auth/login ... permitAll()` 줄 **바로 다음**에 추가한다.

```java
                // 본인확인은 가입 전(미인증) 흐름이다. callback 2종은 NICE 팝업이 부르는
                // cross-site POST 라 세션·인증이 없다.
                .requestMatchers("/api/auth/nice/**").permitAll()
```

- [ ] **Step 4: 기동 확인**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.RecruitApplicationTests" --no-daemon
```

Expected: `BUILD SUCCESSFUL`

---

## Task 12: 컨트롤러 통합 테스트

**Files:**
- Test: `src/test/java/com/shinyoung/recruit/controller/NiceVerificationControllerTest.java`

- [ ] **Step 1: 테스트 작성**

```java
package com.shinyoung.recruit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinyoung.recruit.service.nice.MockNiceClient;
import com.shinyoung.recruit.service.nice.NicePlaindataCodec;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class NiceVerificationControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private Clock clock;

    @Autowired
    private NicePlaindataCodec codec;

    private MockMvc mockMvc;
    private MockNiceClient client;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        // 애플리케이션 빈과 같은 Clock 을 쓴다. 암호문 생성 시각 검사가 어긋나지 않게 한다.
        client = new MockNiceClient(clock);
    }

    /** 요청을 발급하고 그 EncodeData 에서 REQ_SEQ 를 꺼낸다. */
    private String issueReqSeq(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/nice/request").session(session))
                .andExpect(status().isOk())
                .andReturn();

        String encodeData = new ObjectMapper()
                .readTree(result.getResponse().getContentAsString())
                .path("data").path("encodeData").asText();
        return codec.decode(client.decode(encodeData)).get("REQ_SEQ");
    }

    private String niceSuccessPayload(String reqSeq) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", reqSeq);
        fields.put("NAME", "홍길동");
        fields.put("MOBILE_NO", "01012345678");
        fields.put("CI", "CI-VALUE-1");
        return client.encode(codec.encode(fields));
    }

    @Test
    void callbackRedirectsToFrontResultRouteWithToken() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String reqSeq = issueReqSeq(session);

        // 콜백은 세션 없이(cross-site POST) form-urlencoded 로 들어온다.
        MvcResult result = mockMvc.perform(post("/api/auth/nice/callback")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("EncodeData", niceSuccessPayload(reqSeq)))
                .andExpect(status().isSeeOther())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertNotNull(location);
        org.junit.jupiter.api.Assertions.assertTrue(location.startsWith("/nice-auth/result?token="));
    }

    @Test
    void resultStoresIdentityInSessionAndOmitsCiFromResponse() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String reqSeq = issueReqSeq(session);

        MvcResult callback = mockMvc.perform(post("/api/auth/nice/callback")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("EncodeData", niceSuccessPayload(reqSeq)))
                .andReturn();
        String token = callback.getResponse().getHeader("Location").split("token=")[1];

        MvcResult result = mockMvc.perform(post("/api/auth/nice/result")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.phoneNumber").value("01012345678"))
                .andReturn();

        // 응답 본문 어디에도 CI 가 없어야 한다.
        assertEquals(-1, result.getResponse().getContentAsString().indexOf("CI-VALUE-1"));

        NiceVerifiedIdentity identity = (NiceVerifiedIdentity)
                session.getAttribute(NiceVerificationController.VERIFIED_SESSION_KEY);
        assertNotNull(identity);
        assertEquals("CI-VALUE-1", identity.ci());
    }

    @Test
    void resultFromDifferentSessionDoesNotPopulateSession() throws Exception {
        MockHttpSession requester = new MockHttpSession();
        String reqSeq = issueReqSeq(requester);

        MvcResult callback = mockMvc.perform(post("/api/auth/nice/callback")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("EncodeData", niceSuccessPayload(reqSeq)))
                .andReturn();
        String token = callback.getResponse().getHeader("Location").split("token=")[1];

        MockHttpSession attacker = new MockHttpSession();
        mockMvc.perform(post("/api/auth/nice/result")
                        .session(attacker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().is4xxClientError());

        assertNull(attacker.getAttribute(NiceVerificationController.VERIFIED_SESSION_KEY));
    }
}
```

- [ ] **Step 2: 실행**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.controller.NiceVerificationControllerTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 3 tests passed

`resultFromDifferentSessionDoesNotPopulateSession`이 4xx가 아니라 500으로 떨어지면 `NiceVerificationException`을 처리하는 핸들러가 없는 것이다. Step 3으로 간다.

- [ ] **Step 3: `GlobalExceptionHandler`에 매핑 추가**

`src/main/java/com/shinyoung/recruit/exception/GlobalExceptionHandler.java`의 `handleInvalidApplicantSignUp` 바로 다음에 넣는다. 기존 예외들과 같은 형태다.

```java
    @ExceptionHandler(NiceVerificationException.class)
    public ResponseEntity<ApiResponse<Void>> handleNiceVerification(NiceVerificationException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
    }
```

`ApiResponse.error`가 아니라 **`ApiResponse.fail`**이다.

- [ ] **Step 4: 재실행**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.controller.NiceVerificationControllerTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 3 tests passed

---

## Task 13: 가입 연동 — 요청 DTO 축소와 세션 소비

**Files:**
- Modify: `src/main/java/com/shinyoung/recruit/dto/request/ApplicantSignUpRequest.java`
- Modify: `src/main/java/com/shinyoung/recruit/service/ApplicantSignUpService.java`
- Modify: `src/main/java/com/shinyoung/recruit/controller/ApplicantSignUpController.java`
- Test: `src/test/java/com/shinyoung/recruit/controller/ApplicantSignUpNiceIntegrationTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class ApplicantSignUpNiceIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private Clock clock;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private MockHttpSession sessionWithIdentity(String ci) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                NiceVerificationController.VERIFIED_SESSION_KEY,
                new NiceVerifiedIdentity(
                        NiceVerificationPurpose.SIGNUP, "홍길동", "01012345678", ci, clock.instant()));
        return session;
    }

    private String signUpBody(String loginId) {
        return "{\"loginId\":\"" + loginId + "\",\"password\":\"password123\",\"email\":\""
                + loginId + "\"}";
    }

    @Test
    void signUpUsesIdentityFromSessionNotFromRequestBody() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(sessionWithIdentity("CI-FROM-SERVER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody("nice1@example.test")))
                .andExpect(status().isOk());

        Applicant saved = applicantRepository.findByCiHash(HashUtil.sha256("CI-FROM-SERVER")).orElseThrow();
        assertEquals("홍길동", saved.getUserName());
        assertEquals("01012345678", saved.getPhoneNumber());
    }

    @Test
    void signUpWithoutVerifiedSessionIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(new MockHttpSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody("nice2@example.test")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void signUpConsumesIdentitySoItCannotBeReused() throws Exception {
        MockHttpSession session = sessionWithIdentity("CI-ONCE");

        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody("nice3@example.test")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody("nice4@example.test")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void sameCiCannotSignUpTwice() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(sessionWithIdentity("CI-DUPLICATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody("nice5@example.test")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(sessionWithIdentity("CI-DUPLICATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody("nice6@example.test")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void expiredIdentityIsRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                NiceVerificationController.VERIFIED_SESSION_KEY,
                new NiceVerifiedIdentity(
                        NiceVerificationPurpose.SIGNUP, "홍길동", "01012345678", "CI-STALE",
                        clock.instant().minus(Duration.ofMinutes(31))));

        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody("nice7@example.test")))
                .andExpect(status().is4xxClientError());
    }
}
```

`ApplicantRepository.findByCiHash`가 없으면 추가한다.

```java
    Optional<Applicant> findByCiHash(String ciHash);
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.controller.ApplicantSignUpNiceIntegrationTest" --no-daemon
```

Expected: 실패 — 현재 `ApplicantSignUpRequest`가 `name`·`phoneNumber`·`ci`를 `@NotBlank`로 요구하므로 400이 난다

- [ ] **Step 3: `ApplicantSignUpRequest` 축소**

```java
package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 지원자 가입 요청.
 *
 * <p><b>이름·휴대폰·CI 는 요청 본문에 없다.</b> NICE 본인확인 결과를 서버 세션에서 꺼내 쓴다.
 * 클라이언트가 보낸 값을 믿으면 본인확인이 무의미해지고 CI 중복 차단도 뚫린다.
 */
public record ApplicantSignUpRequest(
        @NotBlank(message = "loginId는 필수입니다.")
        @Size(max = 100, message = "loginId는 100자 이하여야 합니다.")
        String loginId,

        @NotBlank(message = "password는 필수입니다.")
        @Size(min = 8, max = 100, message = "password는 8자 이상 100자 이하여야 합니다.")
        String password,

        @Email(message = "유효한 이메일 형식이어야 합니다.")
        @Size(max = 255, message = "email은 255자 이하여야 합니다.")
        String email
) {
}
```

- [ ] **Step 4: `ApplicantSignUpService.signUp` 시그니처 변경**

`signUp`이 인증 결과를 두 번째 인자로 받는다. 메서드 본문에서 `request.name()`·`request.phoneNumber()`·`request.ci()`를 쓰던 세 줄만 바꾼다.

```java
    @Transactional
    public ApplicantSignUpResponse signUp(ApplicantSignUpRequest request, NiceVerifiedIdentity identity) {
        String loginId = request.loginId().trim();
        String name = identity.name().trim();
        String phoneNumber = identity.phoneNumber().trim();
        String email = normalizeEmail(request.email());
        String ci = identity.ci().trim();
```

나머지 본문은 그대로 둔다. import 를 추가한다.

```java
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
```

- [ ] **Step 5: `ApplicantSignUpController` 수정**

`signUp` 메서드만 바꾼다.

```java
    @PostMapping("/sign-up")
    public ResponseEntity<ApiResponse<ApplicantSignUpResponse>> signUp(
            @Valid @RequestBody ApplicantSignUpRequest request, HttpSession session) {
        // 세션에서 인증 결과를 꺼내 유효성을 확인한 뒤 서비스에 값으로 넘긴다.
        // 서비스가 HttpSession 을 알면 단위 테스트가 서블릿 컨테이너에 묶인다.
        NiceVerifiedIdentity identity = niceVerificationService.requireFresh(
                (NiceVerifiedIdentity) session.getAttribute(
                        NiceVerificationController.VERIFIED_SESSION_KEY),
                NiceVerificationPurpose.SIGNUP);

        ApplicantSignUpResponse response = applicantSignUpService.signUp(request, identity);

        // 1회용이다. 남겨 두면 한 번의 인증으로 여러 계정을 만들 수 있다.
        session.removeAttribute(NiceVerificationController.VERIFIED_SESSION_KEY);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
```

생성자와 import 를 추가한다.

```java
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.service.nice.NiceVerificationService;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import jakarta.servlet.http.HttpSession;
```

```java
    private final ApplicantSignUpService applicantSignUpService;
    private final NiceVerificationService niceVerificationService;

    public ApplicantSignUpController(
            ApplicantSignUpService applicantSignUpService,
            NiceVerificationService niceVerificationService) {
        this.applicantSignUpService = applicantSignUpService;
        this.niceVerificationService = niceVerificationService;
    }
```

- [ ] **Step 6: 기존 가입 테스트 수정**

```bash
cd recruit_back/recruit_backend && grep -rln "ApplicantSignUpRequest\|sign-up" src/test/java
```

나오는 파일마다 `ApplicantSignUpRequest` 생성자 인자와 `signUp` 호출 인자를 새 시그니처에 맞춘다. **테스트가 검증하던 동작은 바꾸지 않는다** — 인자만 옮긴다. `ci`를 검증하던 테스트는 `NiceVerifiedIdentity`를 만들어 넘긴다.

- [ ] **Step 7: 테스트 통과 확인**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.controller.ApplicantSignUp*" --tests "com.shinyoung.recruit.service.ApplicantSignUp*" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 모든 테스트 통과

- [ ] **Step 8: 백엔드 전체 범위 재확인**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.nice.*" --tests "com.shinyoung.recruit.controller.NiceVerificationControllerTest" --tests "com.shinyoung.recruit.controller.ApplicantSignUp*" --no-daemon
```

Expected: `BUILD SUCCESSFUL`

---

## Task 14: 프론트 — 타입 · API 모듈 · 팝업 · 결과 화면 · 가입 화면

**Files:**
- Create: `recruit_front/src/types/auth/nice.ts`
- Create: `recruit_front/src/api/auth/niceApi.ts`
- Create: `recruit_front/src/views/auth/pop-up/NiceAuthResult.vue`
- Modify: `recruit_front/src/views/auth/pop-up/NiceAuthPopup.vue`
- Modify: `recruit_front/src/routes/authRoutes.ts`
- Modify: `recruit_front/src/views/applicant/SignupView.vue`

- [ ] **Step 1: 타입 작성**

`recruit_front/src/types/auth/nice.ts`:

```ts
/** 팝업이 NICE 표준창으로 POST 할 암호문. */
export interface NiceRequestResponse {
  encodeData: string
}

/**
 * 결과 교환 응답. ci 는 없다 — 서버 세션에만 보관한다.
 */
export interface NiceResultResponse {
  status: 'SUCCESS'
  name: string
  phoneNumber: string
}

/** 팝업이 부모창으로 보내는 postMessage 페이로드. */
export interface NiceAuthMessage {
  source: 'nice-auth'
  status: 'SUCCESS' | 'FAIL'
  name?: string
  phoneNumber?: string
}

/** NICE 표준창 엔드포인트. 레거시와 동일하다. */
export const NICE_CHECKPLUS_ACTION = 'https://nice.checkplus.co.kr/CheckPlusSafeModel/checkplus.cb'

/** NICE 규격상 고정값. 'Serivce' 오타는 규격 그대로이므로 고치지 않는다. */
export const NICE_CHECKPLUS_M = 'checkplusSerivce'

/** postMessage 식별자. 다른 라이브러리 메시지와 섞이지 않게 한다. */
export const NICE_MESSAGE_SOURCE = 'nice-auth'
```

- [ ] **Step 2: API 모듈 작성**

`recruit_front/src/api/auth/niceApi.ts`:

```ts
import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type { NiceRequestResponse, NiceResultResponse } from '@/types/auth/nice'

/** NICE 본인확인. 가입 전 흐름이라 인증 없이 호출된다. */
export const niceApi = {
  /** 요청번호를 발급받고 표준창으로 보낼 암호문을 받는다. */
  request() {
    return apiClient.post<ApiResponse<NiceRequestResponse>>('/auth/nice/request')
  },

  /** 콜백 리다이렉트로 받은 1회용 토큰을 인증 결과로 교환한다. 서버 세션에 CI 가 심긴다. */
  exchangeResult(token: string) {
    return apiClient.post<ApiResponse<NiceResultResponse>>('/auth/nice/result', { token })
  },
}
```

- [ ] **Step 3: 팝업 교체**

`recruit_front/src/views/auth/pop-up/NiceAuthPopup.vue` **전체**를 아래로 바꾼다. 목업 UI(통신사 버튼·이름·전화번호 입력·`crypto.randomUUID()`)는 전부 사라진다.

```vue
<template>
  <div class="nice-auth-loading">
    <p class="message">{{ message }}</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { niceApi } from '@/api/auth/niceApi'
import { getApiErrorMessage } from '@/api/apiError'
import {
  NICE_CHECKPLUS_ACTION,
  NICE_CHECKPLUS_M,
  NICE_MESSAGE_SOURCE,
} from '@/types/auth/nice'

const message = ref('본인확인 창으로 이동합니다...')

/**
 * 레거시 checkplus_main.jsp 가 하던 일과 같다 — 서버가 만든 EncodeData 를
 * NICE 표준창으로 POST 한다. JSP 가 필요한 부분은 없다.
 *
 * 이미 팝업 안이므로 target 은 _self 다.
 */
function submitToNice(encodeData: string) {
  const form = document.createElement('form')
  form.method = 'post'
  form.action = NICE_CHECKPLUS_ACTION
  form.target = '_self'
  form.appendChild(hidden('m', NICE_CHECKPLUS_M))
  form.appendChild(hidden('EncodeData', encodeData))
  document.body.appendChild(form)
  form.submit()
}

function hidden(name: string, value: string) {
  const input = document.createElement('input')
  input.type = 'hidden'
  input.name = name
  input.value = value
  return input
}

/** 실패를 부모창에 알리고 닫는다. 부모가 버튼을 다시 열어 줄 수 있게 한다. */
function reportFailure(text: string) {
  message.value = text
  window.opener?.postMessage(
    { source: NICE_MESSAGE_SOURCE, status: 'FAIL' },
    window.location.origin,
  )
}

onMounted(async () => {
  try {
    const { data } = await niceApi.request()
    submitToNice(data.data.encodeData)
  } catch (error) {
    reportFailure(getApiErrorMessage(error, '본인확인을 시작하지 못했습니다.'))
  }
})
</script>

<style scoped lang="scss">
.nice-auth-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  padding: 24px;
}

.message {
  font-size: 15px;
  color: #333;
  text-align: center;
}
</style>
```

`getApiErrorMessage`의 import 경로는 `SignupView.vue`가 쓰는 것과 같게 맞춘다. 다르면 그 쪽을 따른다.

- [ ] **Step 4: 결과 화면 작성**

`recruit_front/src/views/auth/pop-up/NiceAuthResult.vue`:

```vue
<template>
  <div class="nice-auth-result">
    <p class="message">{{ message }}</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { niceApi } from '@/api/auth/niceApi'
import { NICE_MESSAGE_SOURCE, type NiceAuthMessage } from '@/types/auth/nice'

const route = useRoute()
const message = ref('본인확인 결과를 확인하고 있습니다...')

/**
 * NICE 콜백을 받은 서버가 303 으로 여기로 보낸다.
 *
 * 이 요청은 same-site 라 세션 쿠키가 실린다. 그래서 여기서 토큰을 교환하면
 * 서버가 세션에 CI 를 심을 수 있다. 콜백 자체는 cross-site POST 라 세션이 없다.
 *
 * 부모창에는 이름·휴대폰만 넘긴다. CI 는 브라우저로 내려오지 않는다.
 */
function notifyParent(payload: NiceAuthMessage) {
  window.opener?.postMessage(payload, window.location.origin)
  window.close()
}

onMounted(async () => {
  const token = route.query.token
  if (typeof token !== 'string' || !token) {
    notifyParent({ source: NICE_MESSAGE_SOURCE, status: 'FAIL' })
    return
  }

  try {
    const { data } = await niceApi.exchangeResult(token)
    notifyParent({
      source: NICE_MESSAGE_SOURCE,
      status: 'SUCCESS',
      name: data.data.name,
      phoneNumber: data.data.phoneNumber,
    })
  } catch {
    message.value = '본인확인에 실패했습니다.'
    notifyParent({ source: NICE_MESSAGE_SOURCE, status: 'FAIL' })
  }
})
</script>

<style scoped lang="scss">
.nice-auth-result {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  padding: 24px;
}

.message {
  font-size: 15px;
  color: #333;
  text-align: center;
}
</style>
```

- [ ] **Step 5: 라우트 추가**

`recruit_front/src/routes/authRoutes.ts`의 `NiceAuthPopup` 항목 **다음**에 추가한다.

```ts
  {
    path: '/nice-auth/result',
    name: 'NiceAuthResult',
    component: () => import('@/views/auth/pop-up/NiceAuthResult.vue'),
    meta: {
      public: true,
    },
  },
```

- [ ] **Step 6: `SignupView.vue` 수정 — 콜백 수신**

`phoneAuthCallback` 전역 함수 등록·해제 블록을 `message` 리스너로 바꾼다. 기존 블록(`const phoneAuthCallback = ...`부터 `onBeforeUnmount(...)` 끝까지)을 아래로 교체한다.

```ts
/**
 * 팝업이 postMessage 로 결과를 보낸다. 전역 함수(window.phoneAuthCallback)를 쓰던
 * 방식에서 바뀌었다 — 전역은 다른 화면과 충돌하고 origin 검증이 불가능하다.
 *
 * CI 는 오지 않는다. 서버 세션에만 있고 가입 제출 때 서버가 꺼내 쓴다.
 */
const onNiceMessage = (event: MessageEvent) => {
  if (event.origin !== window.location.origin) {
    return
  }
  const payload = event.data as NiceAuthMessage | undefined
  if (payload?.source !== NICE_MESSAGE_SOURCE) {
    return
  }

  isNiceAuthPopupOpen.value = false

  if (payload.status !== 'SUCCESS' || !payload.name || !payload.phoneNumber) {
    message.error('본인인증에 실패했습니다. 다시 시도해주세요.')
    isNiceAuthComplete.value = false
    return
  }

  form.name = payload.name
  form.phoneNumber = payload.phoneNumber
  message.success('본인인증이 완료되었습니다.')
  isNiceAuthComplete.value = true
}

window.addEventListener('message', onNiceMessage)

// 화면을 떠난 뒤 팝업이 메시지를 보내도 반응하지 않게 한다.
// 자기가 등록한 리스너만 지운다 — 다른 화면의 리스너를 건드리지 않는다.
onBeforeUnmount(() => {
  window.removeEventListener('message', onNiceMessage)
})
```

import 를 추가한다.

```ts
import { NICE_MESSAGE_SOURCE, type NiceAuthMessage } from '@/types/auth/nice'
```

`NiceAuthComplete` 함수는 더 이상 쓰이지 않으면 지운다.

**팝업이 그냥 닫혀 메시지가 오지 않는 경우에 타임아웃을 넣지 않는다.** `clickToNiceAuthPopupOpen`은 `isNiceAuthPopupOpen`을 검사하지 않으므로 사용자가 버튼을 다시 누르면 새 팝업이 열린다. 대기 해제 로직은 불필요하다.

- [ ] **Step 7: `SignupView.vue` 수정 — `ci` 제거와 읽기 전용화**

`SignupForm` 인터페이스에서 `ci`를 지운다.

```ts
interface SignupForm {
  loginId: string
  phoneNumber: string
  name: string
  password: string
  passwordConfirm : string
}
```

`form` 초기화에서도 `ci: '',`를 지운다.

`clickToSignupButton`의 요청 본문에서 `name`·`phoneNumber`·`ci`를 지운다.

```ts
    const request = {
        loginId: form.loginId,
        password: form.password,
        email: form.loginId,
    };
```

이름·휴대폰을 입력하는 `a-input`이 있으면 `:disabled="true"` 또는 `readonly`를 붙여 수정할 수 없게 한다. 본인확인 결과가 신원이므로 화면에서 고칠 수 있으면 안 된다. **값은 마스킹하지 않고 원문으로 보여준다**(`AGENTS.md` — 관리자·사용자 화면 연락처 마스킹 금지).

- [ ] **Step 8: 타입 체크**

```bash
cd recruit_front && npm run type-check
```

Expected: 에러 0건

`window.phoneAuthCallback` 타입 선언이 남아 있어 에러가 나면, 그 선언이 다른 화면에서도 쓰이는지 확인한다.

```bash
cd recruit_front && grep -rn "phoneAuthCallback" src/
```

`SignupView.vue` 외에 쓰는 곳이 없으면 전역 타입 선언도 함께 지운다. 쓰는 곳이 있으면 **건드리지 않는다.**

- [ ] **Step 9: 단위 테스트 실행**

```bash
cd recruit_front && npm run test:unit
```

Expected: 기존 테스트 통과. `SignupView` 관련 테스트가 `ci`나 `phoneAuthCallback`에 의존하면 새 구조에 맞게 고친다.

---

## Task 14b: `SecurityConfigTest` 보강

백엔드 `AGENTS.md` 6절: 매처를 추가하면 `{BT}/config/SecurityConfigTest.java` 에 허용·거부 테스트를 추가한다.

NICE 엔드포인트 4개는 **의도적으로 공개**다(가입 전 흐름). 다만 `anyRequest().permitAll()` 폴백 때문에 매처가 없어도 지금은 동작한다 — 즉 매처가 문서 역할만 한다. 나중에 누가 위쪽에 broad 매처를 넣으면 **비로그인 가입 경로가 조용히 막힌다.** 그 회귀를 잡는 것이 이 테스트의 목적이다.

**Files:**
- Modify: `src/test/java/com/shinyoung/recruit/config/SecurityConfigTest.java`

- [ ] **Step 1: 기존 패턴 확인**

`메뉴_트리_조회는_인증없이_허용` 같은 기존 테스트가 쓰는 형태(`webAppContextSetup(context).apply(springSecurity())`, `.with(anonymous())`)를 그대로 따른다.

- [ ] **Step 2: 테스트 추가**

파일 끝에 추가한다. 인가 통과만 확인하면 되므로 **본문 검증은 하지 않는다** — 4xx 중 `401`·`403` 이 아니면 인가는 통과한 것이다.

```java

    /**
     * NICE 본인확인은 가입 전(비로그인) 흐름이다. callback 2종은 NICE 팝업이 부르는
     * cross-site POST 라 세션·인증이 아예 없다.
     *
     * 전용 매처가 없어도 anyRequest().permitAll() 로 흘러 지금은 통과하지만,
     * 위쪽에 broad 매처가 추가되면 비로그인 가입 경로가 조용히 막힌다. 그 회귀를 잡는다.
     */
    @Test
    void 본인확인_요청은_비인증이어도_인가를_통과() throws Exception {
        mockMvc.perform(post("/api/auth/nice/request").with(anonymous()))
                .andExpect(status().is(not(anyOf(is(401), is(403)))));
    }

    @Test
    void 본인확인_콜백은_비인증이어도_인가를_통과() throws Exception {
        mockMvc.perform(post("/api/auth/nice/callback")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("EncodeData", "INVALID"))
                .andExpect(status().is(not(anyOf(is(401), is(403)))));
    }

    @Test
    void 본인확인_실패콜백은_비인증이어도_인가를_통과() throws Exception {
        mockMvc.perform(post("/api/auth/nice/callback/error")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("EncodeData", "INVALID"))
                .andExpect(status().is(not(anyOf(is(401), is(403)))));
    }

    @Test
    void 본인확인_결과교환은_비인증이어도_인가를_통과() throws Exception {
        mockMvc.perform(post("/api/auth/nice/result")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"INVALID\"}"))
                .andExpect(status().is(not(anyOf(is(401), is(403)))));
    }
```

`not`·`anyOf`·`is` 는 `org.hamcrest.Matchers` 다. 기존 파일에 hamcrest import 가 없으면 추가한다. 기존 테스트가 다른 방식으로 "인가 통과"를 표현하고 있으면 **그 방식을 따른다.**

- [ ] **Step 3: 실행**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.config.SecurityConfigTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL` — 기존 테스트 전부 + 신규 4개 통과

---

## Task 15a: `NiceClient.decode` 를 복호화 결과 객체로 (jar 불필요)

jar 를 실측해 보니 설계 전제가 둘 틀렸다.

- `getRequestNo` 가 아니라 **`getRequestNO()`** 다(대문자 `NO`).
- **`getCipherDateTime()` 은 인자가 없다.** `fnDecode` 가 채운 인스턴스 상태를 읽는 getter 다. 그래서 `cipherEpochSeconds(String)` 를 독립 호출로 둘 수 없다 — 한 번의 복호화가 평문과 시각을 함께 내놓아야 한다.

이 Task 는 인터페이스를 그 모양으로 바꾼다. **jar 없이 할 수 있다.**

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/service/nice/NiceDecodeResult.java`
- Modify: `src/main/java/com/shinyoung/recruit/service/nice/NiceClient.java`
- Modify: `src/main/java/com/shinyoung/recruit/service/nice/MockNiceClient.java`
- Modify: `src/main/java/com/shinyoung/recruit/service/nice/NiceVerificationService.java`
- Modify: `src/test/java/com/shinyoung/recruit/service/nice/MockNiceClientTest.java`
- Modify: 나머지 nice 테스트 중 `client.decode(...)` 를 쓰는 곳

- [ ] **Step 1: `NiceDecodeResult` 작성**

```java
package com.shinyoung.recruit.service.nice;

/**
 * 복호화 결과. 평문과 암호문 생성 시각을 함께 담는다.
 *
 * <p>둘을 따로 얻을 수 없다. 모듈의 {@code getCipherDateTime()} 은 인자가 없고
 * {@code fnDecode} 가 채운 인스턴스 상태를 읽기 때문이다(2026-09-21 실측).
 *
 * @param cipherEpochSeconds 암호문 생성 시각(epoch 초). 읽을 수 없으면 {@code null}
 */
public record NiceDecodeResult(String plaindata, Long cipherEpochSeconds) {
}
```

- [ ] **Step 2: `NiceClient` 인터페이스 수정**

`decode` 의 반환형을 바꾸고 `cipherEpochSeconds` 를 **삭제**한다.

```java
    /**
     * 암호문 → 평문과 생성 시각. 실패하면
     * {@link com.shinyoung.recruit.exception.NiceVerificationException}.
     */
    NiceDecodeResult decode(String encodeData);
```

- [ ] **Step 3: `MockNiceClient` 수정**

`decode` 가 `NiceDecodeResult` 를 돌려주게 하고 `cipherEpochSeconds` 메서드를 삭제한다. Mock 형식(`MOCK.<epoch>.<Base64>`)은 그대로다.

```java
    @Override
    public NiceDecodeResult decode(String encodeData) {
        String[] parts = parts(encodeData);
        try {
            return new NiceDecodeResult(
                    new String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8),
                    Long.parseLong(parts[1]));
        } catch (IllegalArgumentException | NumberFormatException e) {
            throw new NiceVerificationException("복호화에 실패했습니다.", e);
        }
    }
```

- [ ] **Step 4: `NiceVerificationService` 수정**

`handleCallback` 이 한 번만 복호화하게 고친다.

```java
    public String handleCallback(String encodeData) {
        NiceDecodeResult decoded = niceClient.decode(encodeData);
        Map<String, String> fields = niceClient.parse(decoded.plaindata());
        NiceVerificationRecord record = consumePending(fields.get("REQ_SEQ"));
        verifyCipherTime(decoded.cipherEpochSeconds());
        ...
    }
```

`handleErrorCallback` 도 같은 방식으로 평문을 얻는다. `decodeFields` 는 지운다.

`verifyCipherTime` 을 아래로 바꾼다.

```java
    /**
     * 암호문 생성 시각 검사. Store 대조와 독립된 2차 게이트다.
     *
     * <p><b>읽지 못하면 거부하지 않고 경고만 남긴다.</b> 시각 포맷을 실제 NICE 응답으로
     * 확인할 수단이 없기 때문이다(자체 왕복 불가, 운영 사이트코드 1개). 여기서 fail-closed 로
     * 두면 포맷 가정이 빗나갔을 때 본인확인 전체가 멈춘다. 주 방어선인 Store 대조는 그대로다.
     */
    private void verifyCipherTime(Long cipherEpochSeconds) {
        if (cipherEpochSeconds == null) {
            log.warn("암호문 생성 시각을 읽지 못했습니다. 2차 게이트를 건너뜁니다.");
            return;
        }
        if (isExpired(Instant.ofEpochSecond(cipherEpochSeconds), properties.getRequestTtlMinutes())) {
            throw new NiceVerificationException("본인확인 결과가 만료되었습니다.");
        }
    }
```

- [ ] **Step 5: 테스트 수정**

`client.decode(x)` 로 평문을 꺼내던 곳을 `client.decode(x).plaindata()` 로 바꾼다. `cipherEpochSecondsReflectsEncodeTime` 은 `client.decode(...).cipherEpochSeconds()` 를 보게 고친다.

**검증 내용을 지우지 않는다.** 호출 형태만 바꾼다.

- [ ] **Step 6: 실행**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.nice.*" --tests "com.shinyoung.recruit.controller.NiceVerificationControllerTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL` — 45개 통과(기존과 동일)

---

## Task 15b: `RealNiceClient` + fail-closed 가드 + JVM 플래그

**선행**: `recruit_back/recruit_backend/libs/NiceID.jar` (확보 완료).

실측으로 확인된 `CPClient` 공개 API 다.

```
public String  getRequestNO();
public String  getRequestNO(String);
public int     fnEncode(String, String, String);
public String  getCipherData();
public int     fnDecode(String, String, String);
public String  getPlainData();
public String  getCipherDateTime();      // 인자 없음
public String  getCipherIPAddress();
public HashMap fnParse(String);          // raw HashMap
```

오류 코드: `0` 성공, `-1` AES_SYSTEM, `-2` ENC_PROC, `-3` ENC_DATA, `-4` DEC_PROC, `-5` DEC_HASH, `-6` DEC_DATA, `-7` CIPHER_VERSION_MISMATCH, `-8` CIPHER_VERSION_ERR, `-9` INPUT_DATA, `-12` PWD_MISMATCH.

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/service/nice/RealNiceClient.java`
- Create: `src/test/java/com/shinyoung/recruit/service/nice/RealNiceClientTest.java`
- Create: `src/test/java/com/shinyoung/recruit/config/NiceClientConfigTest.java`
- Modify: `src/main/java/com/shinyoung/recruit/config/NiceClientConfig.java`
- Modify: `build.gradle`
- Modify: `recruit_back/recruit_backend/AGENTS.md`

- [ ] **Step 1: `build.gradle` — 의존성과 JVM 플래그**

`dependencies` 블록에 추가한다.

```gradle
	// NICE 체크플러스 본인확인 모듈. 공개 저장소에 없어 저장소에 파일을 두고 직접 참조한다.
	implementation files('libs/NiceID.jar')
```

**그리고 JVM 플래그가 필수다.** `CPClient` 생성자가 JDK 내부 클래스 `com.sun.crypto.provider.SunJCE` 를 직접 만들어, 플래그가 없으면 Java 9+ 에서 `IllegalAccessError` 로 죽는다. 레거시 기동 스크립트도 같은 플래그를 쓴다(확인).

`test` 와 `bootRun` 에 추가한다.

```gradle
tasks.named('test') {
	jvmArgs '--add-exports', 'java.base/com.sun.crypto.provider=ALL-UNNAMED'
}

tasks.named('bootRun') {
	jvmArgs '--add-exports', 'java.base/com.sun.crypto.provider=ALL-UNNAMED'
}
```

기존 `test` 블록에 `maxHeapSize = '2g'` 가 있으면 **지우지 말고 같은 블록에 합친다.**

- [ ] **Step 2: `RealNiceClient` 작성**

```java
package com.shinyoung.recruit.service.nice;

import NiceID.Check.CPClient;
import com.shinyoung.recruit.config.NiceProperties;
import com.shinyoung.recruit.exception.NiceVerificationException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NICE 제공 모듈({@code NiceID.jar}) 호출 구현.
 *
 * <p>{@code CPClient} 는 결과를 인스턴스 상태로 들고 있다({@code getCipherData}·
 * {@code getPlainData}·{@code getCipherDateTime}). 호출마다 새로 만든다 —
 * 공유하면 동시 요청에서 결과가 섞인다.
 *
 * <p><b>이 클래스는 로컬에서 검증할 수 없다.</b> 모듈은 자기가 만든 암호문을 자기가
 * 복호화하지 못한다({@code fnEncode} 뒤 {@code fnDecode} 는 항상 {@code -6}).
 * 요청 포맷과 응답 포맷이 다르기 때문이다. 첫 검증은 배포 후 실인증 1회다.
 */
public class RealNiceClient implements NiceClient {

    /** {@code getCipherDateTime()} 의 포맷 후보. 실응답으로 확인되지 않았다. */
    private static final DateTimeFormatter CIPHER_TIME =
            DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private final NiceProperties properties;

    public RealNiceClient(NiceProperties properties) {
        this.properties = properties;
    }

    @Override
    public String generateRequestNo() {
        return new CPClient().getRequestNO(properties.getSiteCode());
    }

    @Override
    public String encode(String plaindata) {
        CPClient client = new CPClient();
        int result = client.fnEncode(
                properties.getSiteCode(), properties.getSitePassword(), plaindata);
        if (result != 0) {
            throw new NiceVerificationException("본인확인 요청 생성에 실패했습니다. code=" + result);
        }
        return client.getCipherData();
    }

    @Override
    public NiceDecodeResult decode(String encodeData) {
        CPClient client = new CPClient();
        int result = client.fnDecode(
                properties.getSiteCode(), properties.getSitePassword(), encodeData);
        if (result != 0) {
            throw new NiceVerificationException("본인확인 결과 복호화에 실패했습니다. code=" + result);
        }
        return new NiceDecodeResult(client.getPlainData(), cipherEpochSeconds(client));
    }

    /**
     * 시각 문자열을 epoch 초로 바꾼다. <b>읽지 못하면 null 을 돌려준다</b> — 포맷을
     * 실응답으로 확인할 수단이 없어, 여기서 예외를 던지면 가정이 빗나갔을 때
     * 본인확인 전체가 멈춘다. 호출부가 경고만 남기고 2차 게이트를 건너뛴다.
     */
    private Long cipherEpochSeconds(CPClient client) {
        try {
            return LocalDateTime.parse(client.getCipherDateTime(), CIPHER_TIME)
                    .atZone(ZoneId.systemDefault())
                    .toEpochSecond();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 응답 파싱을 모듈에 맡긴다. 레거시 {@code checkplus_success.jsp} 도 {@code fnParse} 를 쓴다. */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> parse(String plaindata) {
        Map<String, String> parsed = new LinkedHashMap<>();
        try {
            ((Map<String, String>) new CPClient().fnParse(plaindata)).forEach(parsed::put);
        } catch (RuntimeException e) {
            throw new NiceVerificationException("본인확인 결과를 읽을 수 없습니다.", e);
        }
        return parsed;
    }
}
```

- [ ] **Step 3: 가드 테스트 작성**

```java
package com.shinyoung.recruit.config;

import com.shinyoung.recruit.service.nice.MockNiceClient;
import com.shinyoung.recruit.service.nice.NiceClient;
import com.shinyoung.recruit.service.nice.NicePlaindataCodec;
import com.shinyoung.recruit.service.nice.RealNiceClient;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NiceClientConfigTest {

    private final NiceClientConfig config = new NiceClientConfig();
    private final NicePlaindataCodec codec = new NicePlaindataCodec();

    private NiceProperties properties(boolean mockEnabled, String siteCode) {
        NiceProperties properties = new NiceProperties();
        properties.setMockEnabled(mockEnabled);
        properties.setSiteCode(siteCode);
        return properties;
    }

    @Test
    void realClientWhenMockDisabledAndSiteCodePresent() {
        NiceClient client = config.niceClient(properties(false, "SITECODE"), Clock.systemUTC(), codec);

        assertInstanceOf(RealNiceClient.class, client);
    }

    @Test
    void mockClientWhenMockEnabledAndSiteCodeAbsent() {
        NiceClient client = config.niceClient(properties(true, ""), Clock.systemUTC(), codec);

        assertInstanceOf(MockNiceClient.class, client);
    }

    @Test
    void mockEnabledWithSiteCodeFailsFast() {
        // 운영 설정에 Mock 이 섞인 상태. 기동을 막지 않으면 본인확인 우회 경로가 열린다.
        assertThrows(IllegalStateException.class,
                () -> config.niceClient(properties(true, "SITECODE"), Clock.systemUTC(), codec));
    }

    @Test
    void mockDisabledWithoutSiteCodeFailsFast() {
        assertThrows(IllegalStateException.class,
                () -> config.niceClient(properties(false, ""), Clock.systemUTC(), codec));
    }
}
```

- [ ] **Step 4: `NiceClientConfig` 교체**

임시 구현을 아래로 **완전히 바꾼다.**

```java
package com.shinyoung.recruit.config;

import com.shinyoung.recruit.service.nice.MockNiceClient;
import com.shinyoung.recruit.service.nice.NiceClient;
import com.shinyoung.recruit.service.nice.NicePlaindataCodec;
import com.shinyoung.recruit.service.nice.RealNiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * {@link NiceClient} 구현 선택과 <b>fail-closed 가드</b>.
 *
 * <p>정확히 한 조합만 기동을 허용한다.
 * <ul>
 *   <li>mock=false + siteCode 있음 → 실제 연동</li>
 *   <li>mock=true + siteCode 없음 → 개발</li>
 *   <li>mock=true + siteCode <b>있음</b> → 기동 거부. 운영 설정에 Mock 이 섞였다</li>
 *   <li>mock=false + siteCode <b>없음</b> → 기동 거부. 실제 경로인데 키가 없다</li>
 * </ul>
 *
 * <p>애매한 조합을 통과시키면 운영에 본인확인 우회 경로가 생긴다. 뜨지 않는 편이 낫다.
 */
@Configuration
public class NiceClientConfig {

    @Bean
    public NiceClient niceClient(NiceProperties properties, Clock clock, NicePlaindataCodec codec) {
        boolean hasSiteCode = properties.getSiteCode() != null && !properties.getSiteCode().isBlank();

        if (properties.isMockEnabled() && hasSiteCode) {
            throw new IllegalStateException(
                    "recruit.nice.mock-enabled=true 인데 site-code 가 설정돼 있습니다. "
                            + "운영 설정에 Mock 이 섞였을 수 있어 기동을 중단합니다.");
        }
        if (!properties.isMockEnabled() && !hasSiteCode) {
            throw new IllegalStateException(
                    "recruit.nice.site-code 가 없습니다. 실제 연동에는 사이트코드가 필요합니다. "
                            + "개발이라면 recruit.nice.mock-enabled=true 로 두십시오.");
        }

        return properties.isMockEnabled()
                ? new MockNiceClient(clock, codec)
                : new RealNiceClient(properties);
    }
}
```

- [ ] **Step 5: 모듈 로딩 확인 테스트**

자체 왕복은 불가능하지만 **클래스가 로딩되고 생성자가 도는지**는 확인할 수 있다. JVM 플래그가 빠지면 이 테스트가 잡는다.

```java
package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.config.NiceProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 모듈이 이 JVM 에서 로딩·동작하는지만 본다.
 *
 * <p><b>encode 뒤 decode 왕복은 검증할 수 없다.</b> 모듈이 자기 암호문을 복호화하지 못한다
 * (항상 {@code -6}). 요청 포맷과 응답 포맷이 다르기 때문이다. 복호화·파싱의 실검증은
 * 배포 후 실인증 1회뿐이다.
 *
 * <p>이 테스트는 {@code --add-exports java.base/com.sun.crypto.provider=ALL-UNNAMED}
 * 플래그가 빠지면 {@code IllegalAccessError} 로 실패한다. 그것이 이 테스트의 주 목적이다.
 */
class RealNiceClientTest {

    private RealNiceClient client() {
        NiceProperties properties = new NiceProperties();
        properties.setSiteCode("EXAMPLE");
        properties.setSitePassword("0123456789ABCDEF");
        return new RealNiceClient(properties);
    }

    @Test
    void generateRequestNoReturnsValueFromModule() {
        String requestNo = client().generateRequestNo();

        assertNotNull(requestNo);
        assertTrue(requestNo.startsWith("EXAMPLE"));
    }

    @Test
    void encodeProducesCipherText() {
        String cipher = client().encode("7:REQ_SEQ3:abc");

        assertNotNull(cipher);
        assertTrue(cipher.length() > 100);
    }
}
```

- [ ] **Step 6: 실행**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.config.NiceClientConfigTest" --tests "com.shinyoung.recruit.service.nice.*" --no-daemon
```

Expected: `BUILD SUCCESSFUL` — 가드 4 + 모듈 2 + 기존 40 = 46개 통과

- [ ] **Step 7: 변이 검증 — JVM 플래그가 실제로 필요한지**

`build.gradle` 의 `test` 블록에서 `jvmArgs` 줄을 **잠시** 주석 처리한다.

Step 6 을 다시 돌린다. **`RealNiceClientTest` 2개가 `IllegalAccessError` 로 실패해야 한다.**

확인했으면 **주석을 원복한다.**

- [ ] **Step 8: 운영 기동 문서**

`recruit_back/recruit_backend/AGENTS.md` 8절(설정·실행)에 한 줄 추가한다.

```
- NICE 모듈(`libs/NiceID.jar`)은 JDK 내부 클래스를 직접 참조하므로 **기동에 `--add-exports java.base/com.sun.crypto.provider=ALL-UNNAMED` 가 필수**다. 빠지면 본인확인 호출에서 `IllegalAccessError` 가 난다. `bootRun`·`test` 는 `build.gradle` 에 걸려 있고, 운영 `java -jar` 실행 스크립트에도 넣어야 한다.
```

---

## Task 16: 문서 갱신

**Files:**
- Modify: `docs/domains/auth-account.md`

- [ ] **Step 1: API 계약 표 갱신**

`## API 계약` 절에 5행을 추가·수정하고 상태를 🟢로 적는다.

| 메서드 | 경로 | 권한 | 요청 | 응답 |
|---|---|---|---|---|
| POST | `/auth/nice/request` | permitAll | 없음(세션으로 식별) | `{ encodeData }` |
| POST | `/auth/nice/callback` | permitAll | form `EncodeData` | `303` → `/nice-auth/result?token=` |
| POST | `/auth/nice/callback/error` | permitAll | form `EncodeData` | `303` → `/nice-auth/result?token=` |
| POST | `/auth/nice/result` | permitAll | `{ token }` | `{ status, name, phoneNumber }` |
| POST | `/auth/applicants/sign-up` | permitAll | `{ loginId, password, email }` | 기존 응답 |

`sign-up` 행의 기존 내용을 고친다 — `name`·`phoneNumber`·`ci`가 빠졌다.

- [ ] **Step 2: 파일 지도 갱신**

신규 컨트롤러 `NiceVerificationController.java`와 신규 화면 `NiceAuthResult.vue`를 `## 파일 지도`에 등록한다. **정확히 한 카드에만 등록한다**(점검 스크립트가 중복·누락을 잡는다).

- [ ] **Step 3: 규칙·불변식 갱신**

아래 4개를 `## 규칙·불변식`에 추가한다.

- **CI 는 브라우저로 내려가지 않는다.** 서버가 복호화한 값을 세션에만 두고 가입 시 꺼내 쓴다. 가입 요청 본문에 `ci` 가 없다.
- **NICE 콜백에는 세션 쿠키가 없다**(cross-site POST, SameSite=Lax). 콜백은 `REQ_SEQ` 로만 찾고 세션 대조는 뒤따르는 same-site 결과 교환에서 한다.
- **`REQ_SEQ` 는 1회용이다.** 레코드가 `PENDING` 이 아니면 거부한다. 레거시에는 이 대조가 없어 재전송에 열려 있었다.
- **`NiceVerificationStore` 는 인메모리이며 단일 인스턴스 전제다.** 세션과 같은 전제 위에 있다. 다중 인스턴스로 확장하면 둘을 함께 외부 저장소로 옮겨야 한다.

- [ ] **Step 4: 함정·결정 갱신**

기존 "NICE 연동 방식 확인분" 항목에서 미확인으로 남겨 둔 것들을 실제 구현값으로 바꾼다. 다음 한 줄을 추가한다.

- **미검증으로 남은 것**: NICE 실응답의 필드 구성이 우리 파서 가정(`NAME`·`MOBILE_NO`·`CI`·`REQ_SEQ`)과 같은가. 운영 사이트코드가 1개뿐이라 배포 후 1회 실인증으로 확인한다.

- [ ] **Step 5: 목업 보류 항목 정리**

"목업 보류" 항목에서 NICE 부분을 지운다. 이메일 인증·아이디 찾기·비밀번호 재발급은 **그대로 둔다** — 이번 범위가 아니다.

- [ ] **Step 6: 문서 점검**

```bash
cd /d/recruit && node tools/check-docs.mjs
```

Expected: 오류 0건

카드가 40KB를 넘으면 하위 도메인으로 나누고 `_index.md`를 고친다.

---

## Task 17: 최종 검증

- [ ] **Step 1: 백엔드 변경 범위 테스트**

```bash
cd recruit_back/recruit_backend && AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.nice.*" --tests "com.shinyoung.recruit.config.NiceClientConfigTest" --tests "com.shinyoung.recruit.controller.NiceVerificationControllerTest" --tests "com.shinyoung.recruit.controller.ApplicantSignUp*" --tests "com.shinyoung.recruit.service.ApplicantSignUp*" --no-daemon
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: 프론트 타입 체크**

```bash
cd recruit_front && npm run type-check
```

Expected: 에러 0건

- [ ] **Step 3: 프론트 단위 테스트**

```bash
cd recruit_front && npm run test:unit
```

Expected: 통과

- [ ] **Step 4: 문서 점검**

```bash
cd /d/recruit && node tools/check-docs.mjs
```

Expected: 오류 0건

- [ ] **Step 5: 보고**

다음을 적는다.

- 변경·추가 파일 목록
- 테스트 결과(통과 수, 실패 있으면 출력 그대로)
- 계약 변경(`sign-up` 요청 본문 축소 포함)
- **검증하지 못한 것**: NICE 실응답 필드 구성, 실제 표준창 왕복
- Task 15를 건너뛰었으면 그 사실과 이유
