# 메시지 발송 S2 — 대상자·작성 화면 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자가 메시지 종류와 조건을 고르면 서버가 대상자와 수신자별 변수 값을 내려주고, 발송 화면에서 템플릿을 불러와 작성하며 수신자별 미리보기를 볼 수 있게 한다(설계서 S2). 실제 발송·테스트 발송·하단 발송 바·확인 모달은 S3.

**Architecture:** 백엔드에 발신 정보 설정(`MessageProperties`), 치환·SMS byte 규칙(`MessageRenderer` 확장), 변수 값 계산(`MessageVariableFormatter`), 종류별 대상자 조회(`MessageTargetRepository` + `MessageTargetService`), `GET /admin/messages/targets`를 추가한다. 프론트에 같은 치환·byte 규칙(`messageRender.ts`), 변수 삽입 composable, 발송 화면(`/admin/messages`)과 하위 컴포넌트 5개를 추가한다.

**Tech Stack:** Spring Boot 4 · Java 17 · JPA(JPQL) · JUnit5/AssertJ/MockMvc · Vue 3 · TypeScript · ant-design-vue 4 · Vitest

**근거:** 설계서 `docs/superpowers/specs/2026-09-19-message-send-design.md` 3.1·4·5·6·9절, 목업 `design/메시지-발송.html`, S1 코드(도메인 카드 `docs/domains/message.md`).

**S1 리뷰에서 넘어온 요구:** 서버 SMS byte는 코드포인트 기준으로 센다(프론트 `for...of`와 일치). 치환 전에 줄바꿈 `\r\n`을 `\n`으로 통일하고 서버·프론트가 같은 예시로 테스트한다.

**설계서 대비 결정(이 계획에서 확정):**
- 대상자 조회 응답에 발신 정보 `sender { name, email, smsCallbackNumber }`를 추가한다(미리보기 표시용, 설정값).
- 대상 조회 쿼리는 다른 카드 소유 리포지토리를 건드리지 않고 메시지 전용 `MessageTargetRepository`(`Repository<JobApplication, Long>`)에 둔다.
- 공고 없음은 기존 `JobPostingNotFoundException`(404), 공고에 속하지 않은 전형은 기존 `StageNotFoundException`(404)을 쓴다.

**작업 규칙:** 커밋·브랜치 조작 금지(사용자 지시: main에서 커밋 없이 진행). 백엔드 테스트는 수정한 클래스만. `<로컬 예시 키>` = `recruit_back/recruit_backend/AGENTS.md` 8절 값. 백엔드 명령은 `recruit_back/recruit_backend/`, 프론트는 `recruit_front/`, 문서 점검은 레포 루트에서.

---

## 파일 구조

`{BE}` = `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit`, `{BT}` = `.../src/test/java/com/shinyoung/recruit`, `{BR}` = `.../src/main/resources`, `{FE}` = `recruit_front/src`.

| 구분 | 파일 | 책임 |
|---|---|---|
| 생성 | `{BE}/config/MessageProperties.java` | `recruit.message.*` 발신 정보·사이트 주소 |
| 수정 | `{BR}/application.yaml` | `recruit.message` 블록 |
| 생성 | `{BE}/enumeration/SmsKind.java` | `SMS`·`LMS` |
| 수정 | `{BE}/service/MessageRenderer.java` | 치환·줄바꿈 통일·SMS byte·구분 |
| 수정 | `{BT}/service/MessageRendererTest.java` | 위 규칙 테스트 |
| 생성 | `{BE}/service/MessageVariableContext.java` | 변수 계산 입력(이름·공고·전형·면접) |
| 생성 | `{BE}/service/MessageVariableFormatter.java` | 종류별 변수 값 계산·형식 |
| 생성 | `{BT}/service/MessageVariableFormatterTest.java` | 단위 테스트 |
| 생성 | `{BE}/domain/repository/MessageTargetRepository.java` | 종류별 대상 JPQL |
| 생성 | `{BE}/dto/condition/MessageTargetCondition.java` | 대상 조건 |
| 생성 | `{BE}/dto/response/MessageTargetResponse.java` | `{ recipients, interviewGroups, sender }` |
| 생성 | `{BE}/dto/response/MessageTargetRecipientResponse.java` | 수신자 1명 |
| 생성 | `{BE}/dto/response/MessageSenderResponse.java` | 발신 정보 |
| 생성 | `{BE}/service/MessageTargetService.java` | 조건 검증·대상 조회·연락처·변수 조립 |
| 생성 | `{BT}/service/MessageTargetServiceTest.java` | 통합 테스트 |
| 수정 | `{BE}/controller/MessageSendAdminController.java` | `GET /admin/messages/targets` |
| 생성 | `{BT}/controller/MessageSendAdminControllerTest.java` | API 테스트 |
| 수정 | `{FE}/views/admin/message/messageRender.ts` | 치환·조각 분리·줄바꿈 통일 |
| 수정 | `{FE}/views/admin/message/__tests__/messageRender.spec.ts` | 서버와 같은 예시 |
| 수정 | `{FE}/types/admin/message.ts` | 대상·발신·작성 내용 타입 |
| 수정 | `{FE}/api/admin/messageApi.ts` | `getTargets` |
| 생성 | `{FE}/views/admin/message/useVariableCursor.ts` | 변수 칩 삽입 위치 기억·삽입(템플릿 화면과 공유) |
| 생성 | `{FE}/views/admin/message/messageCondition.ts` | 조건 상태·쿼리 변환·기본 전형·라벨 |
| 생성 | `{FE}/views/admin/message/__tests__/messageCondition.spec.ts` | Vitest |
| 수정 | `{FE}/views/admin/message/AdminMessageTemplateView.vue` | 위 composable 사용(동작 동일) |
| 생성 | `{FE}/views/admin/message/MessageTypePicker.vue` | 종류 카드 5개 |
| 생성 | `{FE}/views/admin/message/MessageTargetBar.vue` | 조건 바·인원 요약 |
| 생성 | `{FE}/views/admin/message/MessageRecipientDrawer.vue` | 수신자 드로어 |
| 생성 | `{FE}/views/admin/message/MessageComposer.vue` | 템플릿·채널 탭·입력·변수 칩·템플릿으로 저장 |
| 생성 | `{FE}/views/admin/message/MessagePreview.vue` | 메일·휴대폰 미리보기 |
| 생성 | `{FE}/views/admin/message/AdminMessageSendView.vue` | 발송 화면 조립·상태 |
| 수정 | `{FE}/routes/adminRoutes.ts` | `AdminMessageSend`(`/admin/messages`) |
| 수정 | `docs/domains/message.md`, `docs/domains/_index.md` | 카드·색인 |

---

### Task 1: 발신 정보 설정 `MessageProperties`

**Files:**
- Create: `{BE}/config/MessageProperties.java`
- Modify: `{BR}/application.yaml` (`recruit.posting-image` 블록 바로 뒤, `client-event-log:` 앞)

설정 등록은 기존 `ExportProperties`와 같이 `@Component` + `@ConfigurationProperties`다. 모든 키에 기본값이 있어 테스트 yaml 수정은 필요 없다. 실제 값은 운영 환경변수로 주입한다(설계서 17절 4번).

- [ ] **Step 1: 설정 클래스 작성**

`{BE}/config/MessageProperties.java`:

```java
package com.shinyoung.recruit.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 메시지(메일·SMS) 발신 정보와 본문 변수 #{채용사이트} 값. 실제 값은 운영 환경변수로 주입하고 코드에는 예시 값만 둔다.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "recruit.message")
public class MessageProperties {

    /** 메일 보낸사람 표시 이름. */
    @NotBlank
    private String senderName = "신영증권 채용담당";

    /** 메일 보낸사람 주소. */
    @NotBlank
    private String senderEmail = "recruit@example.co.kr";

    /** SMS 발신번호(표시·발송 공용). */
    @NotBlank
    private String smsCallbackNumber = "02-0000-0000";

    /** 채용 사이트 주소. 변수 #{채용사이트}에 들어간다. */
    @NotBlank
    private String siteUrl = "https://recruit.example.co.kr";

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public void setSenderEmail(String senderEmail) {
        this.senderEmail = senderEmail;
    }

    public String getSmsCallbackNumber() {
        return smsCallbackNumber;
    }

    public void setSmsCallbackNumber(String smsCallbackNumber) {
        this.smsCallbackNumber = smsCallbackNumber;
    }

    public String getSiteUrl() {
        return siteUrl;
    }

    public void setSiteUrl(String siteUrl) {
        this.siteUrl = siteUrl;
    }
}
```

- [ ] **Step 2: yaml 추가**

`{BR}/application.yaml`의 `posting-image:` 블록 마지막 줄(`allowed-content-types: ...image/webp}`) 바로 뒤, `client-event-log:` 줄 앞에 추가(들여쓰기 2칸, `recruit:` 아래):

```yaml
  message:
    # 메일·SMS 발신 정보와 #{채용사이트} 값. 실제 값은 운영 환경변수로 주입한다(코드·문서에는 예시 값만).
    sender-name: ${RECRUIT_MESSAGE_SENDER_NAME:신영증권 채용담당}
    sender-email: ${RECRUIT_MESSAGE_SENDER_EMAIL:recruit@example.co.kr}
    sms-callback-number: ${RECRUIT_MESSAGE_SMS_CALLBACK:02-0000-0000}
    site-url: ${RECRUIT_MESSAGE_SITE_URL:https://recruit.example.co.kr}
```

- [ ] **Step 3: 설정 검증 테스트 실행**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.config.ApplicationYamlTest" --no-daemon`
Expected: PASS (최상위 `recruit:` 중복 없음)

---

### Task 2: 치환·SMS byte 규칙 (`MessageRenderer` 확장)

**Files:**
- Create: `{BE}/enumeration/SmsKind.java`
- Modify: `{BE}/service/MessageRenderer.java`
- Modify: `{BT}/service/MessageRendererTest.java`

규칙(설계서 6절, 프론트와 같은 예시): 치환 전에 `\r\n`·`\r`을 `\n`으로 통일한다. `#{키}`는 한 번만 치환(값 안의 `#{…}`는 그대로), 값이 없거나 null이면 빈 문자열. byte는 코드포인트 단위로 127 이하 1, 그 밖 2. 90 이하 `SMS`, 2000 이하 `LMS`, 초과는 빈 값.

- [ ] **Step 1: 실패하는 테스트 추가**

`{BT}/service/MessageRendererTest.java` — 기존 import에 아래를 추가하고, 클래스 끝 `}` 바로 위에 테스트 5개를 추가한다.

추가 import:

```java
import com.shinyoung.recruit.enumeration.SmsKind;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
```

추가 테스트:

```java
    @Test
    void 변수를_값으로_한번만_치환하고_없는_값은_빈_문자열로_둔다() {
        Map<String, String> values = new HashMap<>();
        values.put("이름", "김#{공고명}");
        values.put("공고명", "2026 공채");
        values.put("전형명", null);

        String rendered = renderer.render("#{이름}님 #{공고명} #{전형명}결과 #{도착시각}", values);

        assertThat(rendered).isEqualTo("김#{공고명}님 2026 공채 결과 ");
    }

    @Test
    void 치환_전에_줄바꿈을_LF로_통일한다() {
        assertThat(renderer.render("a\r\nb\rc\nd", Map.of())).isEqualTo("a\nb\nc\nd");
    }

    @Test
    void SMS_byte는_코드포인트_기준으로_ASCII_1_그밖_2로_센다() {
        assertThat(renderer.smsByteLength("abc 123")).isEqualTo(7);
        assertThat(renderer.smsByteLength("신영")).isEqualTo(4);
        assertThat(renderer.smsByteLength("[신영증권] 안내")).isEqualTo(15);
        assertThat(renderer.smsByteLength("😀")).isEqualTo(2);
        assertThat(renderer.smsByteLength("")).isZero();
    }

    @Test
    void SMS_구분은_90byte_이하_SMS_2000byte_이하_LMS_초과는_없음() {
        assertThat(renderer.smsKindOf(90)).contains(SmsKind.SMS);
        assertThat(renderer.smsKindOf(91)).contains(SmsKind.LMS);
        assertThat(renderer.smsKindOf(2000)).contains(SmsKind.LMS);
        assertThat(renderer.smsKindOf(2001)).isEmpty();
    }

    @Test
    void null_본문은_빈_문자열로_치환한다() {
        assertThat(renderer.render(null, Map.of("이름", "김"))).isEmpty();
    }
```

- [ ] **Step 2: 실패 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageRendererTest" --no-daemon`
Expected: FAIL — `SmsKind`, `render`, `smsByteLength`, `smsKindOf` 없음(컴파일 오류).

- [ ] **Step 3: 구현**

`{BE}/enumeration/SmsKind.java`:

```java
package com.shinyoung.recruit.enumeration;

/** SMS 구분. 치환 후 90byte 이하 SMS, 2000byte 이하 LMS. 수신자별로 판정한다. */
public enum SmsKind {
    SMS,
    LMS
}
```

`{BE}/service/MessageRenderer.java` 전체를 아래로 바꾼다(기존 `validateVariables`는 그대로 유지):

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.MessageVariable;
import com.shinyoung.recruit.enumeration.SmsKind;
import com.shinyoung.recruit.exception.InvalidMessageException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 메시지 본문 규칙. 프론트 views/admin/message/messageRender.ts 와 같은 규칙·같은 테스트 예시를 유지한다.
 */
@Component
public class MessageRenderer {

    public static final int SMS_MAX_BYTES = 90;
    public static final int LMS_MAX_BYTES = 2000;

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("#\\{([^}]+)\\}");

    /** 종류에 허용되지 않았거나 없는 변수가 하나라도 있으면 거부한다. null 본문은 건너뛴다. */
    public void validateVariables(MessageType type, String... texts) {
        Set<String> disallowed = new LinkedHashSet<>();
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            Matcher matcher = VARIABLE_PATTERN.matcher(text);
            while (matcher.find()) {
                String key = matcher.group(1);
                boolean allowed = MessageVariable.fromKey(key)
                        .map(variable -> variable.isAllowedFor(type))
                        .orElse(false);
                if (!allowed) {
                    disallowed.add("#{" + key + "}");
                }
            }
        }
        if (!disallowed.isEmpty()) {
            throw new InvalidMessageException("사용할 수 없는 변수: " + String.join(", ", disallowed));
        }
    }

    /** 줄바꿈을 LF로 통일한 뒤 #{키}를 한 번만 치환한다. 값이 없으면 빈 문자열. */
    public String render(String text, Map<String, String> values) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        Matcher matcher = VARIABLE_PATTERN.matcher(normalized);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    /** 코드포인트 단위로 127 이하 1byte, 그 밖 2byte. `[Web발신]` 머리말은 세지 않는다. */
    public int smsByteLength(String text) {
        return text.codePoints().map(codePoint -> codePoint <= 127 ? 1 : 2).sum();
    }

    /** 90byte 이하 SMS, 2000byte 이하 LMS, 그보다 길면 보낼 수 없어 빈 값. */
    public Optional<SmsKind> smsKindOf(int bytes) {
        if (bytes <= SMS_MAX_BYTES) {
            return Optional.of(SmsKind.SMS);
        }
        if (bytes <= LMS_MAX_BYTES) {
            return Optional.of(SmsKind.LMS);
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageRendererTest" --no-daemon`
Expected: PASS (9 tests)

---

### Task 3: 변수 값 계산 `MessageVariableFormatter`

**Files:**
- Create: `{BE}/service/MessageVariableContext.java`
- Create: `{BE}/service/MessageVariableFormatter.java`
- Test: `{BT}/service/MessageVariableFormatterTest.java`

값 규칙(설계서 5절): 날짜는 한국어 요일(`Locale.KOREAN`). `마감일시` = `M월 d일(E) HH:mm`, `면접일시` = `yyyy-MM-dd(E) HH:mm`, `도착시각` = `HH:mm`. `남은기간` = 오늘(`Clock`)부터 마감 날짜까지 일수, 0 이하 `D-DAY`, 그 밖 `D-n`. `면접장소` = `locationName` + 공백 + `roomName`(있는 것만). `면접방식` 라벨 = `IN_PERSON` 대면 · `ONLINE` 온라인 · `HYBRID` 대면+온라인 · `OTHER` 기타. `조` = `groupName`이 숫자만이면 뒤에 "조". 해당 정보가 없으면 빈 문자열. 결과 맵에는 그 종류에 허용된 변수만 `MessageVariable` 선언 순서로 넣는다.

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/service/MessageVariableFormatterTest.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.StageType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessageVariableFormatterTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final JobPosting posting = JobPosting.create("2026 하반기 공채", "content",
            LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 22, 18, 0));
    private final Stage interviewStage = Stage.create(posting, "1차 면접", StageType.FIRST_INTERVIEW, 1, null, false);

    @Test
    void 결과발표는_공통_변수와_전형명만_담는다() {
        Map<String, String> values = formatter(LocalDateTime.of(2026, 9, 19, 10, 0))
                .format(MessageType.RESULT_ANNOUNCEMENT, new MessageVariableContext("김민준", posting, interviewStage, null));

        assertThat(values).containsExactly(
                Map.entry("이름", "김민준"),
                Map.entry("공고명", "2026 하반기 공채"),
                Map.entry("채용사이트", "https://recruit.example.co.kr"),
                Map.entry("전형명", "1차 면접")
        );
    }

    @Test
    void 마감임박은_마감일시와_남은기간을_계산한다() {
        Map<String, String> values = formatter(LocalDateTime.of(2026, 9, 19, 23, 50))
                .format(MessageType.DEADLINE_REMINDER, new MessageVariableContext("김민준", posting, null, null));

        assertThat(values.get("마감일시")).isEqualTo("9월 22일(화) 18:00");
        assertThat(values.get("남은기간")).isEqualTo("D-3");
        assertThat(values).doesNotContainKey("전형명");
    }

    @Test
    void 마감_당일은_D_DAY다() {
        Map<String, String> values = formatter(LocalDateTime.of(2026, 9, 22, 9, 0))
                .format(MessageType.DEADLINE_REMINDER, new MessageVariableContext("김민준", posting, null, null));

        assertThat(values.get("남은기간")).isEqualTo("D-DAY");
    }

    @Test
    void 면접_일정은_일시_도착_장소_방식_조를_형식화한다() {
        Interview interview = Interview.createDraft(posting, interviewStage, "1",
                LocalDateTime.of(2026, 10, 14, 9, 30), LocalDateTime.of(2026, 10, 14, 9, 10),
                InterviewMethod.IN_PERSON, "본사", "12층 대회의실 A", null, null);

        Map<String, String> values = formatter(LocalDateTime.of(2026, 9, 19, 10, 0))
                .format(MessageType.INTERVIEW_SCHEDULE, new MessageVariableContext("김민준", posting, interviewStage, interview));

        assertThat(values.get("면접일시")).isEqualTo("2026-10-14(수) 09:30");
        assertThat(values.get("도착시각")).isEqualTo("09:10");
        assertThat(values.get("면접장소")).isEqualTo("본사 12층 대회의실 A");
        assertThat(values.get("면접방식")).isEqualTo("대면");
        assertThat(values.get("접속링크")).isEmpty();
        assertThat(values.get("조")).isEqualTo("1조");
    }

    @Test
    void 온라인_면접과_이름이_있는_조는_그대로_쓴다() {
        Interview interview = Interview.createDraft(posting, interviewStage, "오전A",
                LocalDateTime.of(2026, 10, 15, 14, 0), null,
                InterviewMethod.ONLINE, null, null, "https://meet.example.com/abc", null);

        Map<String, String> values = formatter(LocalDateTime.of(2026, 9, 19, 10, 0))
                .format(MessageType.INTERVIEW_SCHEDULE, new MessageVariableContext("김민준", posting, interviewStage, interview));

        assertThat(values.get("도착시각")).isEmpty();
        assertThat(values.get("면접장소")).isEmpty();
        assertThat(values.get("면접방식")).isEqualTo("온라인");
        assertThat(values.get("접속링크")).isEqualTo("https://meet.example.com/abc");
        assertThat(values.get("조")).isEqualTo("오전A");
    }

    @Test
    void 면접_공지는_허용된_면접_변수만_담는다() {
        Map<String, String> values = formatter(LocalDateTime.of(2026, 9, 19, 10, 0))
                .format(MessageType.INTERVIEW_NOTICE, new MessageVariableContext(null, posting, interviewStage, null));

        assertThat(values).containsOnlyKeys("이름", "공고명", "채용사이트", "전형명", "면접일시", "면접장소", "접속링크");
        assertThat(values.get("이름")).isEmpty();
        assertThat(values.get("면접일시")).isEmpty();
    }

    private MessageVariableFormatter formatter(LocalDateTime now) {
        MessageProperties properties = new MessageProperties();
        Clock clock = Clock.fixed(now.atZone(SEOUL).toInstant(), SEOUL);
        return new MessageVariableFormatter(properties, clock);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageVariableFormatterTest" --no-daemon`
Expected: FAIL — `MessageVariableContext`, `MessageVariableFormatter` 없음(컴파일 오류).

- [ ] **Step 3: 구현**

`{BE}/service/MessageVariableContext.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;

/** 수신자 1명의 변수 계산 입력. 종류에 따라 stage·interview 는 null 일 수 있다. */
public record MessageVariableContext(
        String name,
        JobPosting jobPosting,
        Stage stage,
        Interview interview
) {
}
```

`{BE}/service/MessageVariableFormatter.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.MessageVariable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 수신자별 #{변수} 값을 계산한다. 종류에 허용된 변수만 MessageVariable 선언 순서로 담는다(설계서 5절). */
@Component
@RequiredArgsConstructor
public class MessageVariableFormatter {

    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("M월 d일(E) HH:mm", Locale.KOREAN);
    private static final DateTimeFormatter INTERVIEW_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd(E) HH:mm", Locale.KOREAN);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final MessageProperties messageProperties;
    private final Clock clock;

    public Map<String, String> format(MessageType type, MessageVariableContext context) {
        Map<String, String> values = new LinkedHashMap<>();
        for (MessageVariable variable : MessageVariable.values()) {
            if (variable.isAllowedFor(type)) {
                values.put(variable.getKey(), valueOf(variable, context));
            }
        }
        return values;
    }

    private String valueOf(MessageVariable variable, MessageVariableContext context) {
        Interview interview = context.interview();
        return switch (variable) {
            case NAME -> Objects.toString(context.name(), "");
            case JOB_POSTING_TITLE -> context.jobPosting().getTitle();
            case SITE_URL -> messageProperties.getSiteUrl();
            case STAGE_NAME -> context.stage() == null ? "" : context.stage().getStageName();
            case DEADLINE -> context.jobPosting().getReceptionEndDateTime().format(DEADLINE_FORMAT);
            case D_DAY -> dDay(context.jobPosting().getReceptionEndDateTime());
            case INTERVIEW_DATE_TIME -> interview == null ? "" : interview.getStartDateTime().format(INTERVIEW_FORMAT);
            case ARRIVAL_TIME -> interview == null || interview.getArrivalDateTime() == null
                    ? "" : interview.getArrivalDateTime().format(TIME_FORMAT);
            case INTERVIEW_PLACE -> interview == null ? "" : place(interview);
            case INTERVIEW_METHOD -> interview == null ? "" : methodLabel(interview.getMethod());
            case MEETING_URL -> interview == null ? "" : Objects.toString(interview.getOnlineMeetingUrl(), "");
            case GROUP -> interview == null ? "" : groupLabel(interview.getGroupName());
        };
    }

    private String dDay(LocalDateTime deadline) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(clock), deadline.toLocalDate());
        return days <= 0 ? "D-DAY" : "D-" + days;
    }

    private static String place(Interview interview) {
        String location = Objects.toString(interview.getLocationName(), "").trim();
        String room = Objects.toString(interview.getRoomName(), "").trim();
        return (location + " " + room).trim();
    }

    private static String methodLabel(InterviewMethod method) {
        return switch (method) {
            case IN_PERSON -> "대면";
            case ONLINE -> "온라인";
            case HYBRID -> "대면+온라인";
            case OTHER -> "기타";
        };
    }

    private static String groupLabel(String groupName) {
        return groupName.matches("\\d+") ? groupName + "조" : groupName;
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageVariableFormatterTest" --no-daemon`
Expected: PASS (6 tests). 2026-09-22는 화요일, 2026-10-14는 수요일이다.

---

### Task 4: 대상자 조회 (`MessageTargetRepository` · `MessageTargetService`)

**Files:**
- Create: `{BE}/domain/repository/MessageTargetRepository.java`
- Create: `{BE}/dto/condition/MessageTargetCondition.java`
- Create: `{BE}/dto/response/MessageSenderResponse.java`
- Create: `{BE}/dto/response/MessageTargetRecipientResponse.java`
- Create: `{BE}/dto/response/MessageTargetResponse.java`
- Create: `{BE}/service/MessageTargetService.java`
- Test: `{BT}/service/MessageTargetServiceTest.java`

규칙(설계서 4절): 공고 필수. 철회(`WITHDRAWN`)·파기(`purgeResult != null`) 지원서는 항상 제외. 대상자 1명 = 지원서 1건.
- 결과 발표: 전형 필수, 전형 상태 `RESULT_ANNOUNCED`·`CLOSED`만. 결과 조건 null = `PASSED`·`FAILED`·`HOLD`·`ABSENT`(`PENDING`·`WITHDRAWN` 결과는 제외).
- 서류 마감 임박: 공고 `PUBLISHED` 이고 `receptionStartDateTime` ≤ now < `receptionEndDateTime`. 대상 = `DRAFT` 지원서. 전형 조건은 무시한다.
- 면접 2종: 전형 필수, 유형 `FIRST_INTERVIEW`·`SECOND_INTERVIEW`·`FINAL_INTERVIEW`. `CONFIRMED` 면접의 `CANDIDATE`·`ASSIGNED` 참가자. 같은 지원서가 여러 면접이면 시작 시각이 가장 이른 면접 값. 조 조건 null = 전체. 응답에 확정 면접 조 목록(숫자 조는 숫자순).
- 직접 입력: 지원 상태 null = `DRAFT`+`SUBMITTED`. 전형은 선택. 결과 조건은 전형을 고른 경우만(아니면 400).
- 연락처: 기본정보(`nameKorean`·`email`·`mobilePhone`) → 회원정보(`userName`·`email`·`phoneNumber`) → 이름은 지원서 스냅숏. 휴대폰은 숫자만 남겨 `01`로 시작하는 10~11자리면 SMS 가능, 이메일은 `x@y.z` 형식이면 메일 가능. 응답의 연락처는 원문 그대로.
- 정렬: 결과·마감·직접 입력은 지원서 id 오름차순, 면접은 면접 시작 시각 → 면접 순서 → 참가자 id.
- 오류: 공고 없음 `JobPostingNotFoundException`(404 "공고를 찾을 수 없습니다."), 공고에 없는 전형 `StageNotFoundException`(404 "전형을 찾을 수 없습니다."), 그 밖 `InvalidMessageException`(400).

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/service/MessageTargetServiceTest.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.InterviewParticipantRepository;
import com.shinyoung.recruit.domain.repository.InterviewRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.condition.MessageTargetCondition;
import com.shinyoung.recruit.dto.response.MessageTargetRecipientResponse;
import com.shinyoung.recruit.dto.response.MessageTargetResponse;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.PurgeResult;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import com.shinyoung.recruit.exception.InvalidMessageException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class MessageTargetServiceTest {

    @Autowired
    private MessageTargetService messageTargetService;
    @Autowired
    private JobPostingRepository jobPostingRepository;
    @Autowired
    private StageRepository stageRepository;
    @Autowired
    private StageResultRepository stageResultRepository;
    @Autowired
    private JobApplicationRepository jobApplicationRepository;
    @Autowired
    private ApplicantRepository applicantRepository;
    @Autowired
    private ApplicationBasicInfoRepository applicationBasicInfoRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private InterviewParticipantRepository interviewParticipantRepository;

    private JobPosting posting;
    private Stage documentStage;
    private Stage interviewStage;

    @BeforeEach
    void setUp() {
        posting = saveJobPosting(LocalDateTime.now().minusDays(1), LocalDate.now().plusDays(3).atTime(18, 0));
        documentStage = saveStage("서류전형", StageType.DOCUMENT, 0);
        interviewStage = saveStage("1차 면접", StageType.FIRST_INTERVIEW, 1);
    }

    @Test
    void 결과발표는_발표된_전형의_결과_조건에_맞는_지원자와_연락처_변수를_준다() {
        setStatus(documentStage, StageStatus.RESULT_ANNOUNCED);
        JobApplication withInfo = submitted("김합격");
        basicInfo(withInfo, "김기본", "010-1234-5678", "kim@example.com");
        result(withInfo, StageResultStatus.PASSED);
        JobApplication withoutInfo = submitted("이합격");
        result(withoutInfo, StageResultStatus.PASSED);
        result(submitted("박불합"), StageResultStatus.FAILED);

        MessageTargetResponse response = messageTargetService.getTargets(
                condition(MessageType.RESULT_ANNOUNCEMENT, documentStage.getId(), StageResultStatus.PASSED, null, null));

        assertThat(response.recipients()).extracting(MessageTargetRecipientResponse::applicationId)
                .containsExactly(withInfo.getId(), withoutInfo.getId());
        MessageTargetRecipientResponse first = response.recipients().get(0);
        assertThat(first.name()).isEqualTo("김기본");
        assertThat(first.email()).isEqualTo("kim@example.com");
        assertThat(first.phone()).isEqualTo("010-1234-5678");
        assertThat(first.mailAvailable()).isTrue();
        assertThat(first.smsAvailable()).isTrue();
        assertThat(first.resultStatus()).isEqualTo(StageResultStatus.PASSED);
        assertThat(first.variables())
                .containsEntry("이름", "김기본")
                .containsEntry("공고명", "메시지 공고")
                .containsEntry("전형명", "서류전형");
        assertThat(first.missingVariables()).isEmpty();
        MessageTargetRecipientResponse second = response.recipients().get(1);
        assertThat(second.name()).isEqualTo("이합격");
        assertThat(second.phone()).isEqualTo("01000000000");
        assertThat(response.interviewGroups()).isEmpty();
        assertThat(response.sender().name()).isEqualTo("신영증권 채용담당");
        assertThat(response.sender().smsCallbackNumber()).isEqualTo("02-0000-0000");
    }

    @Test
    void 결과_전체는_대기와_철회_결과를_뺀다() {
        setStatus(documentStage, StageStatus.RESULT_ANNOUNCED);
        JobApplication passed = submitted("가");
        result(passed, StageResultStatus.PASSED);
        JobApplication hold = submitted("나");
        result(hold, StageResultStatus.HOLD);
        result(submitted("다"), StageResultStatus.PENDING);
        result(submitted("라"), StageResultStatus.WITHDRAWN);

        MessageTargetResponse response = messageTargetService.getTargets(
                condition(MessageType.RESULT_ANNOUNCEMENT, documentStage.getId(), null, null, null));

        assertThat(response.recipients()).extracting(MessageTargetRecipientResponse::applicationId)
                .containsExactly(passed.getId(), hold.getId());
    }

    @Test
    void 발표_전_전형은_결과발표에_쓸_수_없다() {
        assertThatThrownBy(() -> messageTargetService.getTargets(
                condition(MessageType.RESULT_ANNOUNCEMENT, documentStage.getId(), null, null, null)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("결과가 발표된 전형만 선택할 수 있습니다.");
    }

    @Test
    void 결과발표에_전형이_없으면_거부한다() {
        assertThatThrownBy(() -> messageTargetService.getTargets(
                condition(MessageType.RESULT_ANNOUNCEMENT, null, null, null, null)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("전형을 선택해야 합니다.");
    }

    @Test
    void 철회와_파기_지원서는_제외한다() {
        setStatus(documentStage, StageStatus.RESULT_ANNOUNCED);
        JobApplication normal = submitted("정상");
        result(normal, StageResultStatus.PASSED);
        JobApplication withdrawn = submitted("철회");
        result(withdrawn, StageResultStatus.PASSED);
        withdrawn.withdraw(LocalDateTime.of(2026, 6, 3, 9, 0));
        jobApplicationRepository.saveAndFlush(withdrawn);
        JobApplication purged = submitted("파기");
        result(purged, StageResultStatus.PASSED);
        ReflectionTestUtils.setField(purged, "purgeResult", PurgeResult.PURGED);
        jobApplicationRepository.saveAndFlush(purged);

        MessageTargetResponse response = messageTargetService.getTargets(
                condition(MessageType.RESULT_ANNOUNCEMENT, documentStage.getId(), null, null, null));

        assertThat(response.recipients()).extracting(MessageTargetRecipientResponse::applicationId)
                .containsExactly(normal.getId());
    }

    @Test
    void 마감임박은_접수중_공고의_작성중_지원서만_준다() {
        setPostingStatus(JobPostingStatus.PUBLISHED);
        JobApplication draft = draft("최작성");
        submitted("정제출");

        MessageTargetResponse response = messageTargetService.getTargets(
                condition(MessageType.DEADLINE_REMINDER, null, null, null, null));

        assertThat(response.recipients()).extracting(MessageTargetRecipientResponse::applicationId)
                .containsExactly(draft.getId());
        MessageTargetRecipientResponse recipient = response.recipients().get(0);
        assertThat(recipient.variables())
                .containsEntry("남은기간", "D-3")
                .containsEntry("마감일시", posting.getReceptionEndDateTime()
                        .format(DateTimeFormatter.ofPattern("M월 d일(E) HH:mm", Locale.KOREAN)));
        assertThat(recipient.draftStartedAt()).isNotNull();
    }

    @Test
    void 접수중이_아닌_공고는_마감임박에_쓸_수_없다() {
        assertThatThrownBy(() -> messageTargetService.getTargets(
                condition(MessageType.DEADLINE_REMINDER, null, null, null, null)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("접수 중인 공고만 선택할 수 있습니다.");
    }

    @Test
    void 면접은_확정_면접_배정자만_가장_이른_면접_값으로_준다() {
        JobApplication early = submitted("김면접");
        JobApplication late = submitted("이면접");
        JobApplication unconfirmed = submitted("박미확정");
        candidate(interview("2", LocalDateTime.of(2026, 10, 14, 14, 0), null, InterviewStatus.CONFIRMED), late);
        candidate(interview("1", LocalDateTime.of(2026, 10, 14, 9, 30), LocalDateTime.of(2026, 10, 14, 9, 10),
                InterviewStatus.CONFIRMED), early);
        candidate(interview("3", LocalDateTime.of(2026, 10, 14, 16, 0), null, InterviewStatus.DRAFT), unconfirmed);
        candidate(interview("4", LocalDateTime.of(2026, 10, 15, 9, 0), null, InterviewStatus.CONFIRMED), early);

        MessageTargetResponse response = messageTargetService.getTargets(
                condition(MessageType.INTERVIEW_SCHEDULE, interviewStage.getId(), null, null, null));

        assertThat(response.recipients()).extracting(MessageTargetRecipientResponse::applicationId)
                .containsExactly(early.getId(), late.getId());
        MessageTargetRecipientResponse first = response.recipients().get(0);
        assertThat(first.interviewGroup()).isEqualTo("1");
        assertThat(first.interviewDateTime()).isEqualTo(LocalDateTime.of(2026, 10, 14, 9, 30));
        assertThat(first.variables())
                .containsEntry("면접일시", "2026-10-14(수) 09:30")
                .containsEntry("도착시각", "09:10")
                .containsEntry("면접장소", "본사 12층 대회의실")
                .containsEntry("면접방식", "대면")
                .containsEntry("조", "1조");
        assertThat(response.recipients().get(1).missingVariables()).containsExactly("도착시각", "접속링크");
        assertThat(response.interviewGroups()).containsExactly("1", "2", "4");
    }

    @Test
    void 조를_고르면_그_조_배정자만_준다() {
        JobApplication first = submitted("김면접");
        JobApplication second = submitted("이면접");
        candidate(interview("1", LocalDateTime.of(2026, 10, 14, 9, 30), null, InterviewStatus.CONFIRMED), first);
        candidate(interview("2", LocalDateTime.of(2026, 10, 14, 14, 0), null, InterviewStatus.CONFIRMED), second);

        MessageTargetResponse response = messageTargetService.getTargets(
                condition(MessageType.INTERVIEW_NOTICE, interviewStage.getId(), null, "2", null));

        assertThat(response.recipients()).extracting(MessageTargetRecipientResponse::applicationId)
                .containsExactly(second.getId());
        assertThat(response.interviewGroups()).containsExactly("1", "2");
    }

    @Test
    void 면접_유형이_아닌_전형은_면접_안내에_쓸_수_없다() {
        assertThatThrownBy(() -> messageTargetService.getTargets(
                condition(MessageType.INTERVIEW_SCHEDULE, documentStage.getId(), null, null, null)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("면접 전형만 선택할 수 있습니다.");
    }

    @Test
    void 직접입력은_지원_상태와_전형_결과로_거른다() {
        setStatus(documentStage, StageStatus.RESULT_ANNOUNCED);
        JobApplication passed = submitted("가");
        result(passed, StageResultStatus.PASSED);
        JobApplication failed = submitted("나");
        result(failed, StageResultStatus.FAILED);
        JobApplication draft = draft("다");

        assertThat(ids(condition(MessageType.FREE, null, null, null, JobApplicationStatus.SUBMITTED)))
                .containsExactly(passed.getId(), failed.getId());
        assertThat(ids(condition(MessageType.FREE, null, null, null, null)))
                .containsExactly(passed.getId(), failed.getId(), draft.getId());
        assertThat(ids(condition(MessageType.FREE, documentStage.getId(), StageResultStatus.PASSED, null,
                JobApplicationStatus.SUBMITTED)))
                .containsExactly(passed.getId());
    }

    @Test
    void 직접입력에서_전형_없이_결과_조건만_주면_거부한다() {
        assertThatThrownBy(() -> messageTargetService.getTargets(
                condition(MessageType.FREE, null, StageResultStatus.PASSED, null, null)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("결과 조건은 전형을 선택해야 쓸 수 있습니다.");
    }

    @Test
    void 연락처_형식이_틀리면_그_채널을_쓸_수_없다() {
        JobApplication application = submitted("홍형식");
        basicInfo(application, "홍형식", "010-12", "not-an-email");

        MessageTargetRecipientResponse recipient = messageTargetService.getTargets(
                condition(MessageType.FREE, null, null, null, null)).recipients().get(0);

        assertThat(recipient.mailAvailable()).isFalse();
        assertThat(recipient.smsAvailable()).isFalse();
    }

    @Test
    void 없는_공고는_404_예외다() {
        assertThatThrownBy(() -> messageTargetService.getTargets(
                new MessageTargetCondition(MessageType.FREE, Long.MAX_VALUE, null, null, null, null)))
                .isInstanceOf(JobPostingNotFoundException.class)
                .hasMessage("공고를 찾을 수 없습니다.");
    }

    private List<Long> ids(MessageTargetCondition condition) {
        return messageTargetService.getTargets(condition).recipients().stream()
                .map(MessageTargetRecipientResponse::applicationId)
                .toList();
    }

    private MessageTargetCondition condition(MessageType type, Long stageId, StageResultStatus resultStatus,
                                             String interviewGroup, JobApplicationStatus applicationStatus) {
        return new MessageTargetCondition(type, posting.getId(), stageId, resultStatus, interviewGroup, applicationStatus);
    }

    private JobPosting saveJobPosting(LocalDateTime start, LocalDateTime end) {
        JobPosting jobPosting = JobPosting.create("메시지 공고", "Content", start, end);
        jobPosting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        return jobPostingRepository.saveAndFlush(jobPosting);
    }

    private void setPostingStatus(JobPostingStatus status) {
        ReflectionTestUtils.setField(posting, "status", status);
        jobPostingRepository.saveAndFlush(posting);
    }

    private Stage saveStage(String name, StageType stageType, int stageOrder) {
        return stageRepository.saveAndFlush(Stage.create(posting, name, stageType, stageOrder, null, false));
    }

    private void setStatus(Stage stage, StageStatus status) {
        ReflectionTestUtils.setField(stage, "status", status);
        stageRepository.saveAndFlush(stage);
    }

    private JobApplication draft(String name) {
        Applicant applicant = saveApplicant(name);
        JobPosition jobPosition = posting.getJobPositions().get(0);
        return jobApplicationRepository.saveAndFlush(JobApplication.create(
                applicant, posting, jobPosition, name, posting.getTitle(), jobPosition.getPositionName()));
    }

    private JobApplication submitted(String name) {
        JobApplication application = draft(name);
        application.submit(LocalDateTime.of(2026, 6, 1, 9, 0));
        return jobApplicationRepository.saveAndFlush(application);
    }

    private void result(JobApplication application, StageResultStatus status) {
        StageResult result = StageResult.initialize(documentStage, application);
        if (status != StageResultStatus.PENDING) {
            result.updateResult(status, BigDecimal.valueOf(90), null, LocalDateTime.of(2026, 6, 2, 9, 0), "admin");
        }
        stageResultRepository.saveAndFlush(result);
    }

    private void basicInfo(JobApplication application, String name, String phone, String email) {
        applicationBasicInfoRepository.saveAndFlush(ApplicationBasicInfo.create(
                application, name, null, NationalityType.DOMESTIC, null,
                LocalDate.of(1995, 1, 1), phone, null, email,
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT,
                null, null, null, null, null, null));
    }

    private Applicant saveApplicant(String name) {
        String ci = "test-ci-" + UUID.randomUUID();
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName(name);
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setUserName(name);
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.saveAndFlush(applicant);
    }

    private Interview interview(String groupName, LocalDateTime start, LocalDateTime arrival, InterviewStatus status) {
        Interview interview = Interview.createDraft(posting, interviewStage, groupName, start, arrival,
                InterviewMethod.IN_PERSON, "본사", "12층 대회의실", null, null);
        if (status == InterviewStatus.CONFIRMED) {
            interview.confirm();
        }
        return interviewRepository.saveAndFlush(interview);
    }

    private void candidate(Interview interview, JobApplication application) {
        interviewParticipantRepository.saveAndFlush(InterviewParticipant.candidate(interview, application, 1));
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageTargetServiceTest" --no-daemon`
Expected: FAIL — `MessageTargetService`, `MessageTargetCondition` 등 없음(컴파일 오류).

- [ ] **Step 3: 리포지토리 작성**

`{BE}/domain/repository/MessageTargetRepository.java`:

```java
package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 메시지 발송 대상 조회 전용(message 카드 소유). 다른 도메인 리포지토리를 건드리지 않으려고 따로 둔다.
 * 모든 쿼리는 철회(WITHDRAWN)·파기(purgeResult not null) 지원서를 제외하고, 지원자(applicant)를 함께 읽는다.
 */
public interface MessageTargetRepository extends Repository<JobApplication, Long> {

    @Query("""
            select result
            from StageResult result
            join fetch result.jobApplication application
            join fetch application.applicant
            where result.stage.id = :stageId
              and result.resultStatus in :resultStatuses
              and application.status <> com.shinyoung.recruit.enumeration.JobApplicationStatus.WITHDRAWN
              and application.purgeResult is null
            order by application.id asc
            """)
    List<StageResult> findResultTargets(
            @Param("stageId") Long stageId,
            @Param("resultStatuses") Collection<StageResultStatus> resultStatuses
    );

    @Query("""
            select application
            from JobApplication application
            join fetch application.applicant
            where application.jobPosting.id = :jobPostingId
              and application.status in :statuses
              and application.purgeResult is null
            order by application.id asc
            """)
    List<JobApplication> findApplicationTargets(
            @Param("jobPostingId") Long jobPostingId,
            @Param("statuses") Collection<JobApplicationStatus> statuses
    );

    @Query("""
            select application
            from JobApplication application
            join fetch application.applicant
            where application.jobPosting.id = :jobPostingId
              and application.status in :statuses
              and application.purgeResult is null
              and exists (
                    select 1
                    from StageResult result
                    where result.jobApplication = application
                      and result.stage.id = :stageId
                      and result.resultStatus in :resultStatuses)
            order by application.id asc
            """)
    List<JobApplication> findApplicationTargetsWithStageResult(
            @Param("jobPostingId") Long jobPostingId,
            @Param("statuses") Collection<JobApplicationStatus> statuses,
            @Param("stageId") Long stageId,
            @Param("resultStatuses") Collection<StageResultStatus> resultStatuses
    );

    @Query("""
            select participant
            from InterviewParticipant participant
            join fetch participant.interview interview
            join fetch participant.jobApplication application
            join fetch application.applicant
            where interview.stage.id = :stageId
              and interview.status = com.shinyoung.recruit.enumeration.InterviewStatus.CONFIRMED
              and participant.role = com.shinyoung.recruit.enumeration.InterviewParticipantRole.CANDIDATE
              and participant.participantStatus = com.shinyoung.recruit.enumeration.InterviewParticipantStatus.ASSIGNED
              and application.status <> com.shinyoung.recruit.enumeration.JobApplicationStatus.WITHDRAWN
              and application.purgeResult is null
              and (:groupName is null or interview.groupName = :groupName)
            order by interview.startDateTime asc, participant.sortOrder asc, participant.id asc
            """)
    List<InterviewParticipant> findInterviewTargets(
            @Param("stageId") Long stageId,
            @Param("groupName") String groupName
    );

    @Query("""
            select distinct interview.groupName
            from Interview interview
            where interview.stage.id = :stageId
              and interview.status = com.shinyoung.recruit.enumeration.InterviewStatus.CONFIRMED
            """)
    List<String> findConfirmedInterviewGroups(@Param("stageId") Long stageId);
}
```

- [ ] **Step 4: 조건·응답 DTO 작성**

`{BE}/dto/condition/MessageTargetCondition.java`:

```java
package com.shinyoung.recruit.dto.condition;

import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.StageResultStatus;

/**
 * 메시지 대상 조건. null 은 "조건 없음": resultStatus null = 판정된 결과 전체, interviewGroup null = 전체 조,
 * applicationStatus null = 작성 중+제출. 종류별 필수 여부는 MessageTargetService 가 검증한다.
 */
public record MessageTargetCondition(
        MessageType type,
        Long jobPostingId,
        Long stageId,
        StageResultStatus resultStatus,
        String interviewGroup,
        JobApplicationStatus applicationStatus
) {
}
```

`{BE}/dto/response/MessageSenderResponse.java`:

```java
package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.config.MessageProperties;

/** 미리보기 표시용 발신 정보(설정값). */
public record MessageSenderResponse(
        String name,
        String email,
        String smsCallbackNumber
) {
    public static MessageSenderResponse from(MessageProperties properties) {
        return new MessageSenderResponse(
                properties.getSenderName(),
                properties.getSenderEmail(),
                properties.getSmsCallbackNumber()
        );
    }
}
```

`{BE}/dto/response/MessageTargetRecipientResponse.java`:

```java
package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.StageResultStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 메시지 수신 대상 1명(지원서 1건). 연락처는 원문 그대로이며 형식이 맞을 때만 *Available 이 true 다.
 * resultStatus 는 결과 발표, interviewGroup·interviewDateTime 은 면접 2종, draftStartedAt 은 마감 임박에서만 채운다.
 * variables 는 그 종류에 허용된 변수의 수신자별 값, missingVariables 는 값이 빈 변수 키다.
 */
public record MessageTargetRecipientResponse(
        Long applicationId,
        String name,
        String email,
        String phone,
        boolean mailAvailable,
        boolean smsAvailable,
        StageResultStatus resultStatus,
        String interviewGroup,
        LocalDateTime interviewDateTime,
        LocalDateTime draftStartedAt,
        Map<String, String> variables,
        List<String> missingVariables
) {
}
```

`{BE}/dto/response/MessageTargetResponse.java`:

```java
package com.shinyoung.recruit.dto.response;

import java.util.List;

/** 메시지 대상 조회 결과. interviewGroups 는 면접 2종에서만 채운다. */
public record MessageTargetResponse(
        List<MessageTargetRecipientResponse> recipients,
        List<String> interviewGroups,
        MessageSenderResponse sender
) {
}
```

- [ ] **Step 5: 서비스 작성**

`{BE}/service/MessageTargetService.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.MessageTargetRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.dto.condition.MessageTargetCondition;
import com.shinyoung.recruit.dto.response.MessageSenderResponse;
import com.shinyoung.recruit.dto.response.MessageTargetRecipientResponse;
import com.shinyoung.recruit.dto.response.MessageTargetResponse;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.InvalidMessageException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import com.shinyoung.recruit.exception.StageNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 메시지 종류·조건으로 수신 대상과 수신자별 변수 값을 조회한다(설계서 4·5절). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageTargetService {

    private static final Set<StageResultStatus> DECIDED_RESULTS = EnumSet.of(
            StageResultStatus.PASSED, StageResultStatus.FAILED, StageResultStatus.HOLD, StageResultStatus.ABSENT);
    private static final Set<StageStatus> ANNOUNCED_STAGES = EnumSet.of(StageStatus.RESULT_ANNOUNCED, StageStatus.CLOSED);
    private static final Set<StageType> INTERVIEW_STAGES = EnumSet.of(
            StageType.FIRST_INTERVIEW, StageType.SECOND_INTERVIEW, StageType.FINAL_INTERVIEW);
    private static final Set<JobApplicationStatus> OPEN_APPLICATIONS = EnumSet.of(
            JobApplicationStatus.DRAFT, JobApplicationStatus.SUBMITTED);
    private static final Pattern MOBILE_PHONE = Pattern.compile("01\\d{8,9}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Comparator<String> GROUP_ORDER = Comparator
            .comparing((String group) -> group.matches("\\d+") ? Long.parseLong(group) : Long.MAX_VALUE)
            .thenComparing(Comparator.naturalOrder());

    private final JobPostingRepository jobPostingRepository;
    private final StageRepository stageRepository;
    private final MessageTargetRepository messageTargetRepository;
    private final ApplicationBasicInfoRepository applicationBasicInfoRepository;
    private final MessageVariableFormatter messageVariableFormatter;
    private final MessageProperties messageProperties;
    private final Clock clock;

    public MessageTargetResponse getTargets(MessageTargetCondition condition) {
        JobPosting jobPosting = jobPostingRepository.findById(condition.jobPostingId())
                .orElseThrow(() -> new JobPostingNotFoundException("공고를 찾을 수 없습니다."));
        Stage stage = findStage(condition, jobPosting);
        List<Target> targets = switch (condition.type()) {
            case RESULT_ANNOUNCEMENT -> resultTargets(condition, stage);
            case DEADLINE_REMINDER -> deadlineTargets(jobPosting);
            case INTERVIEW_SCHEDULE, INTERVIEW_NOTICE -> interviewTargets(condition, stage);
            case FREE -> freeTargets(condition, jobPosting, stage);
        };
        List<String> interviewGroups = isInterview(condition.type())
                ? messageTargetRepository.findConfirmedInterviewGroups(stage.getId()).stream().sorted(GROUP_ORDER).toList()
                : List.of();
        return new MessageTargetResponse(
                toRecipients(targets, condition.type(), jobPosting, stage),
                interviewGroups,
                MessageSenderResponse.from(messageProperties)
        );
    }

    private Stage findStage(MessageTargetCondition condition, JobPosting jobPosting) {
        MessageType type = condition.type();
        if (type == MessageType.DEADLINE_REMINDER) {
            return null;
        }
        if (condition.stageId() == null) {
            if (type == MessageType.RESULT_ANNOUNCEMENT || isInterview(type)) {
                throw new InvalidMessageException("전형을 선택해야 합니다.");
            }
            if (condition.resultStatus() != null) {
                throw new InvalidMessageException("결과 조건은 전형을 선택해야 쓸 수 있습니다.");
            }
            return null;
        }
        return stageRepository.findByIdAndJobPostingId(condition.stageId(), jobPosting.getId())
                .orElseThrow(() -> new StageNotFoundException("전형을 찾을 수 없습니다."));
    }

    private List<Target> resultTargets(MessageTargetCondition condition, Stage stage) {
        if (!ANNOUNCED_STAGES.contains(stage.getStatus())) {
            throw new InvalidMessageException("결과가 발표된 전형만 선택할 수 있습니다.");
        }
        return messageTargetRepository.findResultTargets(stage.getId(), resultStatuses(condition.resultStatus())).stream()
                .map(result -> new Target(result.getJobApplication(), result.getResultStatus(), null))
                .toList();
    }

    private List<Target> deadlineTargets(JobPosting jobPosting) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean accepting = jobPosting.getStatus() == JobPostingStatus.PUBLISHED
                && !now.isBefore(jobPosting.getReceptionStartDateTime())
                && now.isBefore(jobPosting.getReceptionEndDateTime());
        if (!accepting) {
            throw new InvalidMessageException("접수 중인 공고만 선택할 수 있습니다.");
        }
        return messageTargetRepository.findApplicationTargets(jobPosting.getId(), EnumSet.of(JobApplicationStatus.DRAFT)).stream()
                .map(application -> new Target(application, null, null))
                .toList();
    }

    private List<Target> interviewTargets(MessageTargetCondition condition, Stage stage) {
        if (!INTERVIEW_STAGES.contains(stage.getStageType())) {
            throw new InvalidMessageException("면접 전형만 선택할 수 있습니다.");
        }
        String groupName = condition.interviewGroup() == null || condition.interviewGroup().isBlank()
                ? null : condition.interviewGroup().trim();
        // 쿼리가 시작 시각 오름차순이라 지원서마다 처음 만난 면접이 가장 이른 면접이다.
        Map<Long, Target> targets = new LinkedHashMap<>();
        for (InterviewParticipant participant : messageTargetRepository.findInterviewTargets(stage.getId(), groupName)) {
            JobApplication application = participant.getJobApplication();
            targets.putIfAbsent(application.getId(), new Target(application, null, participant.getInterview()));
        }
        return List.copyOf(targets.values());
    }

    private List<Target> freeTargets(MessageTargetCondition condition, JobPosting jobPosting, Stage stage) {
        Set<JobApplicationStatus> statuses = applicationStatuses(condition.applicationStatus());
        List<JobApplication> applications = stage == null
                ? messageTargetRepository.findApplicationTargets(jobPosting.getId(), statuses)
                : messageTargetRepository.findApplicationTargetsWithStageResult(
                        jobPosting.getId(), statuses, stage.getId(), resultStatuses(condition.resultStatus()));
        return applications.stream()
                .map(application -> new Target(application, null, null))
                .toList();
    }

    private List<MessageTargetRecipientResponse> toRecipients(List<Target> targets, MessageType type,
                                                              JobPosting jobPosting, Stage stage) {
        if (targets.isEmpty()) {
            return List.of();
        }
        List<Long> applicationIds = targets.stream().map(target -> target.application().getId()).toList();
        Map<Long, ApplicationBasicInfo> basicInfos = applicationBasicInfoRepository.findByJobApplicationIdIn(applicationIds).stream()
                .collect(Collectors.toMap(info -> info.getJobApplication().getId(), Function.identity()));
        return targets.stream()
                .map(target -> toRecipient(target, basicInfos.get(target.application().getId()), type, jobPosting, stage))
                .toList();
    }

    private MessageTargetRecipientResponse toRecipient(Target target, ApplicationBasicInfo info, MessageType type,
                                                       JobPosting jobPosting, Stage stage) {
        JobApplication application = target.application();
        Applicant applicant = application.getApplicant();
        String name = firstNonBlank(info == null ? null : info.getNameKorean(), applicant.getUserName(),
                application.getApplicantNameSnapshot());
        String email = firstNonBlank(info == null ? null : info.getEmail(), applicant.getEmail());
        String phone = firstNonBlank(info == null ? null : info.getMobilePhone(), applicant.getPhoneNumber());
        Interview interview = target.interview();
        Map<String, String> variables = messageVariableFormatter.format(
                type, new MessageVariableContext(name, jobPosting, stage, interview));
        List<String> missingVariables = variables.entrySet().stream()
                .filter(entry -> entry.getValue().isBlank())
                .map(Map.Entry::getKey)
                .toList();
        return new MessageTargetRecipientResponse(
                application.getId(),
                name,
                email,
                phone,
                email != null && EMAIL.matcher(email.trim()).matches(),
                phone != null && MOBILE_PHONE.matcher(phone.replaceAll("\\D", "")).matches(),
                target.resultStatus(),
                interview == null ? null : interview.getGroupName(),
                interview == null ? null : interview.getStartDateTime(),
                type == MessageType.DEADLINE_REMINDER ? application.getCreatedAt() : null,
                variables,
                missingVariables
        );
    }

    private static Set<StageResultStatus> resultStatuses(StageResultStatus resultStatus) {
        if (resultStatus == null) {
            return DECIDED_RESULTS;
        }
        if (!DECIDED_RESULTS.contains(resultStatus)) {
            throw new InvalidMessageException("선택할 수 없는 결과입니다.");
        }
        return EnumSet.of(resultStatus);
    }

    private static Set<JobApplicationStatus> applicationStatuses(JobApplicationStatus applicationStatus) {
        if (applicationStatus == null) {
            return OPEN_APPLICATIONS;
        }
        if (!OPEN_APPLICATIONS.contains(applicationStatus)) {
            throw new InvalidMessageException("철회한 지원서는 대상이 아닙니다.");
        }
        return EnumSet.of(applicationStatus);
    }

    private static boolean isInterview(MessageType type) {
        return type == MessageType.INTERVIEW_SCHEDULE || type == MessageType.INTERVIEW_NOTICE;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !JobApplication.PURGED_PLACEHOLDER.equals(value)) {
                return value;
            }
        }
        return null;
    }

    /** 결과 발표는 resultStatus, 면접은 interview 를 함께 들고 다닌다. */
    private record Target(JobApplication application, StageResultStatus resultStatus, Interview interview) {
    }
}
```

- [ ] **Step 6: 통과 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageTargetServiceTest" --no-daemon`
Expected: PASS (14 tests). 실패하면 `build/test-results`로 원인을 보고, 테스트 기대값이 설계서 4절 규칙과 다르면 멈추고 보고한다(기대값을 임의로 바꾸지 않는다).

---

### Task 5: 대상자 API `GET /admin/messages/targets`

**Files:**
- Modify: `{BE}/controller/MessageSendAdminController.java`
- Test: `{BT}/controller/MessageSendAdminControllerTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/controller/MessageSendAdminControllerTest.java`:

```java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class MessageSendAdminControllerTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JobPostingRepository jobPostingRepository;
    @Autowired
    private JobApplicationRepository jobApplicationRepository;
    @Autowired
    private ApplicantRepository applicantRepository;

    private MockMvc mockMvc;
    private JobPosting posting;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        posting = JobPosting.create("메시지 공고", "Content",
                LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 22, 18, 0));
        posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        posting = jobPostingRepository.saveAndFlush(posting);
    }

    @Test
    void 직접입력_대상자와_변수_발신정보를_조회한다() throws Exception {
        JobApplication application = submitted("김지원");

        mockMvc.perform(get("/api/admin/messages/targets")
                        .param("type", "FREE")
                        .param("jobPostingId", String.valueOf(posting.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recipients.length()").value(1))
                .andExpect(jsonPath("$.data.recipients[0].applicationId").value(application.getId()))
                .andExpect(jsonPath("$.data.recipients[0].variables['이름']").value("김지원"))
                .andExpect(jsonPath("$.data.recipients[0].smsAvailable").value(true))
                .andExpect(jsonPath("$.data.interviewGroups.length()").value(0))
                .andExpect(jsonPath("$.data.sender.smsCallbackNumber").value("02-0000-0000"));
    }

    @Test
    void 결과발표에_전형이_없으면_400() throws Exception {
        mockMvc.perform(get("/api/admin/messages/targets")
                        .param("type", "RESULT_ANNOUNCEMENT")
                        .param("jobPostingId", String.valueOf(posting.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("전형을 선택해야 합니다."));
    }

    @Test
    void 없는_공고는_404() throws Exception {
        mockMvc.perform(get("/api/admin/messages/targets")
                        .param("type", "FREE")
                        .param("jobPostingId", String.valueOf(Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("공고를 찾을 수 없습니다."));
    }

    private JobApplication submitted(String name) {
        String ci = "test-ci-" + UUID.randomUUID();
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName(name);
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setUserName(name);
        applicant.setPhoneNumber("01000000000");
        applicantRepository.saveAndFlush(applicant);
        JobPosition jobPosition = posting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant, posting, jobPosition, name, posting.getTitle(), jobPosition.getPositionName());
        application.submit(LocalDateTime.of(2026, 9, 10, 9, 0));
        return jobApplicationRepository.saveAndFlush(application);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.MessageSendAdminControllerTest" --no-daemon`
Expected: FAIL — 엔드포인트가 없어 첫 두 테스트 상태 불일치.

- [ ] **Step 3: 컨트롤러에 엔드포인트 추가**

`{BE}/controller/MessageSendAdminController.java` 전체를 아래로 바꾼다(기존 `/variables` 유지):

```java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.condition.MessageTargetCondition;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.MessageTargetResponse;
import com.shinyoung.recruit.dto.response.MessageVariableResponse;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.service.MessageTargetService;
import com.shinyoung.recruit.service.MessageTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 메시지 발송 화면 API. S2는 변수 카탈로그·대상자 조회, 테스트 발송·발송은 S3에서 추가한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/messages")
public class MessageSendAdminController {

    private final MessageTemplateService messageTemplateService;
    private final MessageTargetService messageTargetService;

    @GetMapping("/variables")
    public ResponseEntity<ApiResponse<List<MessageVariableResponse>>> getVariables() {
        return ResponseEntity.ok(ApiResponse.success(messageTemplateService.getVariables()));
    }

    @GetMapping("/targets")
    public ResponseEntity<ApiResponse<MessageTargetResponse>> getTargets(
            @RequestParam MessageType type,
            @RequestParam Long jobPostingId,
            @RequestParam(required = false) Long stageId,
            @RequestParam(required = false) StageResultStatus resultStatus,
            @RequestParam(required = false) String interviewGroup,
            @RequestParam(required = false) JobApplicationStatus applicationStatus
    ) {
        MessageTargetCondition condition = new MessageTargetCondition(
                type, jobPostingId, stageId, resultStatus, interviewGroup, applicationStatus);
        return ResponseEntity.ok(ApiResponse.success(messageTargetService.getTargets(condition)));
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.MessageSendAdminControllerTest" --tests "com.shinyoung.recruit.controller.MessageTemplateAdminControllerTest" --no-daemon`
Expected: PASS (3 + 7 tests)

---

### Task 6: 프론트 치환 규칙·대상 타입·API

**Files:**
- Modify: `{FE}/views/admin/message/messageRender.ts`
- Modify: `{FE}/views/admin/message/__tests__/messageRender.spec.ts`
- Modify: `{FE}/types/admin/message.ts`
- Modify: `{FE}/api/admin/messageApi.ts`

서버 `MessageRenderer`와 같은 예시로 테스트한다(치환 1회·빈 값·줄바꿈 통일·이모지 2byte).

- [ ] **Step 1: 실패하는 테스트 추가**

`{FE}/views/admin/message/__tests__/messageRender.spec.ts` 전체를 아래로 바꾼다:

```ts
import { describe, expect, it } from 'vitest'

import { renderMessage, renderParts, smsByteLength, smsKindOf } from '../messageRender'

describe('smsByteLength', () => {
  it('ASCII는 1byte, 그 밖(한글 등)은 2byte로 센다', () => {
    expect(smsByteLength('abc 123')).toBe(7)
    expect(smsByteLength('신영')).toBe(4)
    expect(smsByteLength('[신영증권] 안내')).toBe(15)
  })

  it('이모지는 코드포인트 1개라 2byte다', () => {
    expect(smsByteLength('😀')).toBe(2)
  })

  it('빈 문자열은 0byte다', () => {
    expect(smsByteLength('')).toBe(0)
  })
})

describe('smsKindOf', () => {
  it('90byte 이하는 SMS, 2000byte 이하는 LMS, 초과는 null이다', () => {
    expect(smsKindOf(90)).toBe('SMS')
    expect(smsKindOf(91)).toBe('LMS')
    expect(smsKindOf(2000)).toBe('LMS')
    expect(smsKindOf(2001)).toBeNull()
  })
})

describe('renderMessage', () => {
  it('변수를 값으로 한 번만 치환하고 없는 값은 빈 문자열로 둔다', () => {
    const values = { 이름: '김#{공고명}', 공고명: '2026 공채', 전형명: '' }
    expect(renderMessage('#{이름}님 #{공고명} #{전형명}결과 #{도착시각}', values)).toBe(
      '김#{공고명}님 2026 공채 결과 ',
    )
  })

  it('치환 전에 줄바꿈을 LF로 통일한다', () => {
    expect(renderMessage('a\r\nb\rc\nd', {})).toBe('a\nb\nc\nd')
  })
})

describe('renderParts', () => {
  it('값이 있으면 value, 없거나 비면 missing 으로 나눈다', () => {
    expect(renderParts('안녕 #{이름}님 #{도착시각}까지', { 이름: '김', 도착시각: '' })).toEqual([
      { text: '안녕 ', kind: 'text' },
      { text: '김', kind: 'value' },
      { text: '님 ', kind: 'text' },
      { text: '#{도착시각}', kind: 'missing' },
      { text: '까지', kind: 'text' },
    ])
  })
})
```

- [ ] **Step 2: 실패 확인**

Run: `npx vitest run src/views/admin/message/__tests__/messageRender.spec.ts`
Expected: FAIL — `renderMessage`, `renderParts` 없음.

- [ ] **Step 3: 구현**

`{FE}/views/admin/message/messageRender.ts` 전체를 아래로 바꾼다:

```ts
/*
 * 메시지 본문 규칙. 백엔드 MessageRenderer 와 같은 규칙·같은 테스트 예시를 유지한다(설계서 6절).
 * 치환 결과는 미리보기용이며, 실제 발송 내용은 서버가 다시 계산한다.
 */

export type SmsKind = 'SMS' | 'LMS'

export const SMS_MAX_BYTES = 90
export const LMS_MAX_BYTES = 2000

const VARIABLE_PATTERN = /#\{([^}]+)\}/g

export interface RenderPart {
  text: string
  /** text: 본문 그대로, value: 치환된 값, missing: 값이 없어 #{키}를 그대로 둔 자리 */
  kind: 'text' | 'value' | 'missing'
}

/** 문자 코드 127 이하는 1byte, 그 밖(한글 등)은 2byte. `[Web발신]` 머리말은 세지 않는다. */
export const smsByteLength = (text: string): number => {
  let bytes = 0
  for (const character of text) {
    bytes += (character.codePointAt(0) ?? 0) <= 127 ? 1 : 2
  }
  return bytes
}

/** 90byte 이하 SMS, 2000byte 이하 LMS, 그보다 길면 보낼 수 없어 null. */
export const smsKindOf = (bytes: number): SmsKind | null => {
  if (bytes <= SMS_MAX_BYTES) return 'SMS'
  if (bytes <= LMS_MAX_BYTES) return 'LMS'
  return null
}

/** 줄바꿈을 LF로 통일한다. 서버도 치환 전에 같은 처리를 한다. */
export const normalizeNewlines = (text: string): string => text.replace(/\r\n?/g, '\n')

/** 줄바꿈 통일 후 #{키}를 한 번만 치환한다. 값이 없으면 빈 문자열(서버 render 와 같다). */
export const renderMessage = (text: string, values: Record<string, string>): string =>
  normalizeNewlines(text).replace(VARIABLE_PATTERN, (_match, key: string) => values[key] ?? '')

/** 미리보기 강조용으로 치환 결과를 조각으로 나눈다. v-html 없이 그리기 위해 쓴다. */
export const renderParts = (text: string, values: Record<string, string>): RenderPart[] => {
  const normalized = normalizeNewlines(text)
  const parts: RenderPart[] = []
  let last = 0
  for (const match of normalized.matchAll(VARIABLE_PATTERN)) {
    const index = match.index ?? 0
    if (index > last) {
      parts.push({ text: normalized.slice(last, index), kind: 'text' })
    }
    const value = values[match[1] ?? '']
    parts.push(value ? { text: value, kind: 'value' } : { text: match[0], kind: 'missing' })
    last = index + match[0].length
  }
  if (last < normalized.length) {
    parts.push({ text: normalized.slice(last), kind: 'text' })
  }
  return parts
}
```

- [ ] **Step 4: 통과 확인**

Run: `npx vitest run src/views/admin/message/__tests__/messageRender.spec.ts`
Expected: PASS (7 tests)

- [ ] **Step 5: 타입·API 추가**

`{FE}/types/admin/message.ts` 맨 위 주석 바로 아래에 import를 추가하고, 파일 끝에 타입을 추가한다.

추가 import(파일 첫 코드 줄):

```ts
import type { StageResultStatus } from '@/types/admin/stage'
```

파일 끝에 추가:

```ts
/** 대상자 조회 조건. 값이 없는 키는 "조건 없음"(결과 전체·전체 조·작성 중+제출). */
export interface MessageTargetQuery {
  type: MessageType
  jobPostingId: number
  stageId?: number
  resultStatus?: StageResultStatus
  interviewGroup?: string
  applicationStatus?: 'DRAFT' | 'SUBMITTED'
}

/** 수신 대상 1명(지원서 1건). 연락처는 원문, 형식이 맞을 때만 *Available 이 true. */
export interface MessageTargetRecipient {
  applicationId: number
  name: string | null
  email: string | null
  phone: string | null
  mailAvailable: boolean
  smsAvailable: boolean
  /** 결과 발표에서만 */
  resultStatus: StageResultStatus | null
  /** 면접 2종에서만 */
  interviewGroup: string | null
  interviewDateTime: string | null
  /** 서류 마감 임박에서만(지원서 작성 시작 시각) */
  draftStartedAt: string | null
  /** 그 종류에 허용된 변수의 수신자별 값 */
  variables: Record<string, string>
  /** 값이 빈 변수 키 */
  missingVariables: string[]
}

export interface MessageSender {
  name: string
  email: string
  smsCallbackNumber: string
}

export interface MessageTargetResponse {
  recipients: MessageTargetRecipient[]
  /** 면접 2종에서만: 확정 면접의 조 목록 */
  interviewGroups: string[]
  sender: MessageSender
}

/** 발송 화면에서 작성 중인 내용. 템플릿에서 불러오며 여기서 고친 내용은 이번 발송에만 적용된다. */
export interface MessageContent {
  templateId: number | null
  mailEnabled: boolean
  smsEnabled: boolean
  mailSubject: string
  mailBody: string
  smsBody: string
}
```

`{FE}/api/admin/messageApi.ts` — type import 목록에 `MessageTargetQuery`, `MessageTargetResponse`를 추가하고, `messageApi` 객체의 `getVariables` 바로 뒤에 메서드를 추가한다:

```ts
  /** 종류·조건별 수신 대상과 수신자별 변수 값, 발신 정보. 선택 불가 조건은 400. */
  getTargets(query: MessageTargetQuery) {
    return apiClient.get<ApiResponse<MessageTargetResponse>>('/admin/messages/targets', { params: query })
  },
```

또 파일 상단 주석 `S1은 템플릿·변수만, 대상자·발송·이력은 이후 slice에서 추가한다.`를 `템플릿·변수·대상자 조회. 발송·이력은 이후 slice에서 추가한다.`로 바꾼다.

- [ ] **Step 6: 타입 검사**

Run: `npm run type-check`
Expected: 오류 없음

---

### Task 7: 변수 삽입 composable과 템플릿 화면 적용

**Files:**
- Create: `{FE}/views/admin/message/useVariableCursor.ts`
- Modify: `{FE}/views/admin/message/AdminMessageTemplateView.vue`

템플릿 화면의 커서 기억·변수 삽입 로직(S1 리뷰 반영본)을 그대로 옮긴다. 발송 화면 작성 영역(Task 9)도 같은 composable을 쓴다. 동작은 바꾸지 않는다.

- [ ] **Step 1: composable 작성**

`{FE}/views/admin/message/useVariableCursor.ts`:

```ts
import { reactive } from 'vue'
import { message } from 'ant-design-vue'

export type EditableField = 'mailSubject' | 'mailBody' | 'smsBody'

/** 입력란 글자 수 제한. 백엔드 요청 DTO @Size 와 같다. */
export const FIELD_MAX_LENGTH: Record<EditableField, number> = {
  mailSubject: 200,
  mailBody: 10000,
  smsBody: 2000,
}

/**
 * 변수 칩으로 #{키}를 넣는다. 입력란 blur 때 커서 위치를 기억하고(칩 클릭 전에 blur 가 먼저 일어난다),
 * 기억이 없으면 메일 본문 끝에 넣는다. 글자 수 제한을 넘으면 넣지 않는다.
 */
export const useVariableCursor = (
  read: (field: EditableField) => string,
  write: (field: EditableField, value: string) => void,
) => {
  const cursor = reactive<{ field: EditableField; start: number; end: number }>({
    field: 'mailBody',
    start: Number.MAX_SAFE_INTEGER,
    end: Number.MAX_SAFE_INTEGER,
  })

  const resetCursor = (): void => {
    cursor.field = 'mailBody'
    cursor.start = Number.MAX_SAFE_INTEGER
    cursor.end = Number.MAX_SAFE_INTEGER
  }

  const rememberCursor = (field: EditableField, event: Event): void => {
    const target = event.target as HTMLInputElement | HTMLTextAreaElement
    cursor.field = field
    cursor.start = target.selectionStart ?? read(field).length
    cursor.end = target.selectionEnd ?? cursor.start
  }

  const insertVariable = (key: string): void => {
    const token = `#{${key}}`
    const value = read(cursor.field)
    const start = Math.min(cursor.start, value.length)
    const end = Math.min(cursor.end, value.length)
    const next = value.slice(0, start) + token + value.slice(end)
    if (next.length > FIELD_MAX_LENGTH[cursor.field]) {
      message.warning('글자 수 제한을 넘어 변수를 넣을 수 없습니다.')
      return
    }
    write(cursor.field, next)
    cursor.start = start + token.length
    cursor.end = cursor.start
  }

  return { resetCursor, rememberCursor, insertVariable }
}
```

- [ ] **Step 2: 템플릿 화면에서 composable 사용**

`{FE}/views/admin/message/AdminMessageTemplateView.vue` `<script setup>`에서:

1. 아래 로컬 정의를 삭제한다: `type EditableField = ...`, `const FIELD_MAX_LENGTH ... = { ... }`, `const cursor = reactive<...>({ ... })`와 그 위 주석, `const resetCursor = ...`, `const rememberCursor = ...`, `const insertVariable = ...` (각 함수 본문 전체).
2. import 목록의 `import { smsByteLength, smsKindOf } from './messageRender'` 바로 아래에 추가:

```ts
import { FIELD_MAX_LENGTH, useVariableCursor } from './useVariableCursor'
```

3. `const form = reactive<TemplateForm>(emptyForm('RESULT_ANNOUNCEMENT'))` 바로 아래에 추가:

```ts
const { resetCursor, rememberCursor, insertVariable } = useVariableCursor(
  (field) => form[field],
  (field, value) => {
    form[field] = value
  },
)
```

4. `message`(ant-design-vue) import는 저장·삭제에서 계속 쓰므로 남긴다. `reactive` import도 `form`에서 계속 쓴다. 템플릿 마크업은 바꾸지 않는다(`FIELD_MAX_LENGTH`, `rememberCursor`, `insertVariable` 이름이 그대로다).

- [ ] **Step 3: 타입 검사·린트**

Run: `npm run type-check`
Expected: 오류 없음

Run: `npx eslint src/views/admin/message`
Expected: 0건

---

### Task 8: 조건 로직·종류 카드·조건 바·수신자 드로어

**Files:**
- Create: `{FE}/views/admin/message/messageCondition.ts`
- Test: `{FE}/views/admin/message/__tests__/messageCondition.spec.ts`
- Create: `{FE}/views/admin/message/MessageTypePicker.vue`
- Create: `{FE}/views/admin/message/MessageTargetBar.vue`
- Create: `{FE}/views/admin/message/MessageRecipientDrawer.vue`

화면 상태의 조건(`MessageCondition`)은 셀렉트용 `'ALL'` 값을 쓰고, 조회 직전에 API 쿼리로 바꾼다(`toTargetQuery`). 조건이 모자라면(공고·필수 전형 없음) 조회하지 않는다.

- [ ] **Step 1: 실패하는 테스트 작성**

`{FE}/views/admin/message/__tests__/messageCondition.spec.ts`:

```ts
import { describe, expect, it } from 'vitest'

import type { StageListItem } from '@/types/admin/stage'
import type { AdminJobPostingListItem } from '@/types/jobPosting'
import { defaultStageId, selectablePostings, toTargetQuery, type MessageCondition } from '../messageCondition'

const stage = (id: number, stageType: StageListItem['stageType'], status: StageListItem['status']): StageListItem => ({
  id,
  jobPostingId: 1,
  stageName: `전형${id}`,
  stageType,
  stageOrder: id,
  status,
  resultAnnouncementDateTime: null,
  finalStage: false,
})

const posting = (id: number, status: AdminJobPostingListItem['status'], accepting: boolean): AdminJobPostingListItem => ({
  id,
  title: `공고${id}`,
  postingType: 'PUBLIC',
  status,
  receptionStatus: accepting ? 'ACCEPTING' : 'CLOSED',
  accepting,
  receptionStartDateTime: '2026-09-01T09:00:00',
  receptionEndDateTime: '2026-09-22T18:00:00',
  positionCount: 1,
})

const base: MessageCondition = {
  jobPostingId: 1,
  stageId: 3,
  resultStatus: 'ALL',
  interviewGroup: 'ALL',
  applicationStatus: 'ALL',
}

describe('defaultStageId', () => {
  const stages = [
    stage(1, 'DOCUMENT', 'CLOSED'),
    stage(2, 'FIRST_INTERVIEW', 'RESULT_ANNOUNCED'),
    stage(3, 'SECOND_INTERVIEW', 'READY'),
  ]

  it('결과 발표는 발표된 전형 중 마지막 전형', () => {
    expect(defaultStageId('RESULT_ANNOUNCEMENT', stages)).toBe(2)
  })

  it('면접 안내는 첫 면접 전형', () => {
    expect(defaultStageId('INTERVIEW_NOTICE', stages)).toBe(2)
  })

  it('마감 임박·직접 입력은 전형을 고르지 않는다', () => {
    expect(defaultStageId('DEADLINE_REMINDER', stages)).toBeNull()
    expect(defaultStageId('FREE', stages)).toBeNull()
  })
})

describe('selectablePostings', () => {
  it('마감 임박은 게시 중이면서 접수 중인 공고만', () => {
    const postings = [posting(1, 'PUBLISHED', true), posting(2, 'PUBLISHED', false), posting(3, 'DRAFT', false)]
    expect(selectablePostings('DEADLINE_REMINDER', postings).map((item) => item.id)).toEqual([1])
    expect(selectablePostings('FREE', postings)).toHaveLength(3)
  })
})

describe('toTargetQuery', () => {
  it('공고가 없으면 조회하지 않는다', () => {
    expect(toTargetQuery('FREE', { ...base, jobPostingId: null })).toBeNull()
  })

  it('결과 발표·면접은 전형이 없으면 조회하지 않는다', () => {
    expect(toTargetQuery('RESULT_ANNOUNCEMENT', { ...base, stageId: null })).toBeNull()
    expect(toTargetQuery('INTERVIEW_SCHEDULE', { ...base, stageId: null })).toBeNull()
  })

  it('ALL 은 쿼리에서 뺀다', () => {
    expect(toTargetQuery('RESULT_ANNOUNCEMENT', base)).toEqual({ type: 'RESULT_ANNOUNCEMENT', jobPostingId: 1, stageId: 3 })
    expect(toTargetQuery('RESULT_ANNOUNCEMENT', { ...base, resultStatus: 'PASSED' })).toEqual({
      type: 'RESULT_ANNOUNCEMENT',
      jobPostingId: 1,
      stageId: 3,
      resultStatus: 'PASSED',
    })
  })

  it('면접은 조를, 마감 임박은 공고만 보낸다', () => {
    expect(toTargetQuery('INTERVIEW_NOTICE', { ...base, interviewGroup: '2' })).toEqual({
      type: 'INTERVIEW_NOTICE',
      jobPostingId: 1,
      stageId: 3,
      interviewGroup: '2',
    })
    expect(toTargetQuery('DEADLINE_REMINDER', { ...base, resultStatus: 'PASSED' })).toEqual({
      type: 'DEADLINE_REMINDER',
      jobPostingId: 1,
    })
  })

  it('직접 입력은 전형이 있을 때만 결과 조건을 보낸다', () => {
    expect(toTargetQuery('FREE', { ...base, stageId: null, resultStatus: 'PASSED', applicationStatus: 'SUBMITTED' })).toEqual({
      type: 'FREE',
      jobPostingId: 1,
      applicationStatus: 'SUBMITTED',
    })
    expect(toTargetQuery('FREE', { ...base, resultStatus: 'PASSED' })).toEqual({
      type: 'FREE',
      jobPostingId: 1,
      stageId: 3,
      resultStatus: 'PASSED',
    })
  })
})
```

- [ ] **Step 2: 실패 확인**

Run: `npx vitest run src/views/admin/message/__tests__/messageCondition.spec.ts`
Expected: FAIL — `../messageCondition` 없음.

- [ ] **Step 3: 조건 로직 구현**

`{FE}/views/admin/message/messageCondition.ts`:

```ts
import type { MessageTargetQuery, MessageType } from '@/types/admin/message'
import type { StageListItem, StageResultStatus, StageStatus } from '@/types/admin/stage'
import type { AdminJobPostingListItem } from '@/types/jobPosting'

export type ResultFilter = StageResultStatus | 'ALL'
export type ApplicationFilter = 'SUBMITTED' | 'DRAFT' | 'ALL'

/** 발송 화면 조건 바의 상태. 'ALL' 은 조건 없음이며 조회 쿼리에서 빠진다. */
export interface MessageCondition {
  jobPostingId: number | null
  stageId: number | null
  resultStatus: ResultFilter
  interviewGroup: string
  applicationStatus: ApplicationFilter
}

export const RESULT_LABEL: Record<StageResultStatus, string> = {
  PENDING: '대기',
  PASSED: '합격',
  FAILED: '불합격',
  HOLD: '보류',
  ABSENT: '결시',
  WITHDRAWN: '철회',
}

export const STAGE_STATUS_LABEL: Record<StageStatus, string> = {
  READY: '대기',
  IN_PROGRESS: '진행중',
  RESULT_ANNOUNCED: '발표완료',
  CLOSED: '마감',
}

export const RESULT_FILTER_OPTIONS: { value: ResultFilter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'PASSED', label: RESULT_LABEL.PASSED },
  { value: 'FAILED', label: RESULT_LABEL.FAILED },
  { value: 'HOLD', label: RESULT_LABEL.HOLD },
  { value: 'ABSENT', label: RESULT_LABEL.ABSENT },
]

export const APPLICATION_FILTER_OPTIONS: { value: ApplicationFilter; label: string }[] = [
  { value: 'SUBMITTED', label: '제출 완료' },
  { value: 'DRAFT', label: '작성 중' },
  { value: 'ALL', label: '전체' },
]

const INTERVIEW_STAGE_TYPES: StageListItem['stageType'][] = ['FIRST_INTERVIEW', 'SECOND_INTERVIEW', 'FINAL_INTERVIEW']

export const isInterviewType = (type: MessageType): boolean =>
  type === 'INTERVIEW_SCHEDULE' || type === 'INTERVIEW_NOTICE'

/** 숫자만인 조 이름은 뒤에 "조"를 붙여 보여 준다(서버 #{조} 변수와 같은 규칙). */
export const interviewGroupLabel = (group: string | null): string => {
  if (!group) return ''
  return /^\d+$/.test(group) ? `${group}조` : group
}

export const isAnnouncedStage = (stage: StageListItem): boolean =>
  stage.status === 'RESULT_ANNOUNCED' || stage.status === 'CLOSED'

export const isInterviewStage = (stage: StageListItem): boolean => INTERVIEW_STAGE_TYPES.includes(stage.stageType)

/** 서류 마감 임박은 게시 중이면서 접수 중인 공고만 고를 수 있다. */
export const selectablePostings = (type: MessageType, postings: AdminJobPostingListItem[]): AdminJobPostingListItem[] =>
  type === 'DEADLINE_REMINDER' ? postings.filter((posting) => posting.status === 'PUBLISHED' && posting.accepting) : postings

/** 종류·공고를 바꿨을 때의 기본 전형. 전형 목록은 stageOrder 오름차순이다. */
export const defaultStageId = (type: MessageType, stages: StageListItem[]): number | null => {
  if (type === 'RESULT_ANNOUNCEMENT') {
    const announced = stages.filter(isAnnouncedStage)
    return announced[announced.length - 1]?.id ?? null
  }
  if (isInterviewType(type)) {
    return stages.find(isInterviewStage)?.id ?? null
  }
  return null
}

/** 조회에 필요한 조건이 다 있으면 API 쿼리로 바꾸고, 모자라면 null(조회하지 않음). */
export const toTargetQuery = (type: MessageType, condition: MessageCondition): MessageTargetQuery | null => {
  if (condition.jobPostingId === null) {
    return null
  }
  const query: MessageTargetQuery = { type, jobPostingId: condition.jobPostingId }
  if (type === 'RESULT_ANNOUNCEMENT' || isInterviewType(type)) {
    if (condition.stageId === null) {
      return null
    }
    query.stageId = condition.stageId
  }
  if (type === 'RESULT_ANNOUNCEMENT' && condition.resultStatus !== 'ALL') {
    query.resultStatus = condition.resultStatus
  }
  if (isInterviewType(type) && condition.interviewGroup !== 'ALL') {
    query.interviewGroup = condition.interviewGroup
  }
  if (type === 'FREE') {
    if (condition.stageId !== null) {
      query.stageId = condition.stageId
      if (condition.resultStatus !== 'ALL') {
        query.resultStatus = condition.resultStatus
      }
    }
    if (condition.applicationStatus !== 'ALL') {
      query.applicationStatus = condition.applicationStatus
    }
  }
  return query
}
```

- [ ] **Step 4: 통과 확인**

Run: `npx vitest run src/views/admin/message/__tests__/messageCondition.spec.ts`
Expected: PASS (9 tests)

- [ ] **Step 5: 종류 카드 작성**

`{FE}/views/admin/message/MessageTypePicker.vue`:

```vue
<script setup lang="ts">
import type { Component } from 'vue'
import {
  CalendarOutlined,
  CheckOutlined,
  EditOutlined,
  HourglassOutlined,
  NotificationOutlined,
  TrophyOutlined,
} from '@ant-design/icons-vue'

import type { MessageType } from '@/types/admin/message'
import { MESSAGE_TYPES } from './messageTypes'

const type = defineModel<MessageType>({ required: true })

const ICONS: Record<MessageType, Component> = {
  RESULT_ANNOUNCEMENT: TrophyOutlined,
  DEADLINE_REMINDER: HourglassOutlined,
  INTERVIEW_SCHEDULE: CalendarOutlined,
  INTERVIEW_NOTICE: NotificationOutlined,
  FREE: EditOutlined,
}

/* 묶음 라벨은 같은 묶음이 이어지는 칸 수만큼 가로로 걸친다(공고 관련 2 · 면접 안내 2 · 기타 1). */
const groups = MESSAGE_TYPES.reduce<{ label: string; span: number }[]>((result, meta) => {
  const last = result[result.length - 1]
  if (last && last.label === meta.group) {
    last.span += 1
  } else {
    result.push({ label: meta.group, span: 1 })
  }
  return result
}, [])
</script>

<template>
  <div class="type-picker" role="radiogroup" aria-label="메시지 종류">
    <div v-for="group in groups" :key="group.label" class="group-label" :style="{ gridColumn: `span ${group.span}` }">
      {{ group.label }}
    </div>
    <button
      v-for="meta in MESSAGE_TYPES"
      :key="meta.type"
      type="button"
      role="radio"
      :aria-checked="type === meta.type"
      class="type-card"
      :class="{ selected: type === meta.type }"
      @click="type = meta.type"
    >
      <span class="type-icon"><component :is="ICONS[meta.type]" /></span>
      <span class="type-text">
        <span class="type-name">{{ meta.name }}</span>
        <span class="type-desc">{{ meta.description }}</span>
      </span>
      <CheckOutlined v-if="type === meta.type" class="type-check" />
    </button>
  </div>
</template>

<style scoped lang="scss">
.type-picker {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 6px 10px;
  margin-bottom: 14px;
}

.group-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--app-text-muted);

  &::after {
    content: '';
    flex: 1;
    height: 1px;
    background: var(--app-border-default);
  }
}

.type-card {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 11px;
  padding: 13px 14px;
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius-lg);
  background: var(--app-bg-surface);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;

  &:hover {
    border-color: var(--app-border-strong);
  }

  &.selected {
    border-color: var(--app-color-primary);
    box-shadow: 0 0 0 1px var(--app-color-primary), var(--app-shadow-panel);
    background: var(--app-bg-soft);

    .type-icon {
      background: var(--app-color-primary);
      color: #fff;
    }
  }
}

.type-icon {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: var(--app-bg-muted);
  color: var(--app-text-secondary);
  font-size: 18px;
}

.type-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.type-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.type-desc {
  margin-top: 1px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.type-check {
  position: absolute;
  top: 10px;
  right: 11px;
  color: var(--app-color-primary);
}
</style>
```

- [ ] **Step 6: 조건 바 작성**

`{FE}/views/admin/message/MessageTargetBar.vue`:

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { BulbOutlined, TeamOutlined } from '@ant-design/icons-vue'

import type { MessageType } from '@/types/admin/message'
import type { StageListItem } from '@/types/admin/stage'
import type { AdminJobPostingListItem } from '@/types/jobPosting'
import {
  APPLICATION_FILTER_OPTIONS,
  RESULT_FILTER_OPTIONS,
  STAGE_STATUS_LABEL,
  interviewGroupLabel,
  isAnnouncedStage,
  isInterviewStage,
  isInterviewType,
  selectablePostings,
  type ApplicationFilter,
  type MessageCondition,
  type ResultFilter,
} from './messageCondition'

const props = defineProps<{
  type: MessageType
  postings: AdminJobPostingListItem[]
  stages: StageListItem[]
  interviewGroups: string[]
  selectedCount: number
  totalCount: number
  loading: boolean
}>()

const condition = defineModel<MessageCondition>('condition', { required: true })

const emit = defineEmits<{
  changePosting: [jobPostingId: number]
  openDrawer: []
}>()

const HINTS: Record<MessageType, string> = {
  RESULT_ANNOUNCEMENT:
    '발표 완료된 전형만 고를 수 있습니다. 결과로 대상을 좁힌 뒤 중립·합격·불합격 템플릿 중 맞는 것을 쓰세요.',
  DEADLINE_REMINDER: '접수 중인 공고에서 지원서를 작성만 하고 제출하지 않은 지원자에게 보냅니다.',
  INTERVIEW_SCHEDULE:
    '확정된 면접에 배정된 지원자만 대상입니다. 면접 일시·도착 시각·장소는 면접 스케줄링 데이터로 사람마다 자동으로 채워집니다.',
  INTERVIEW_NOTICE: '확정된 면접에 배정된 지원자에게 준비물·유의사항·변경 사항을 공지합니다.',
  FREE: '공고 지원자 중 조건에 맞는 사람에게 직접 작성한 내용을 보냅니다.',
}

const isInterview = computed(() => isInterviewType(props.type))

const postingOptions = computed(() =>
  selectablePostings(props.type, props.postings).map((posting) => ({ value: posting.id, label: posting.title })),
)

const stageOptions = computed(() => {
  const stages = isInterview.value ? props.stages.filter(isInterviewStage) : props.stages
  return stages.map((stage) => ({
    value: stage.id,
    label: `${stage.stageName} · ${STAGE_STATUS_LABEL[stage.status]}`,
    disabled: props.type === 'RESULT_ANNOUNCEMENT' && !isAnnouncedStage(stage),
  }))
})

const stageLabel = computed(() => {
  if (props.type === 'RESULT_ANNOUNCEMENT') return '전형 (발표 완료만 선택 가능)'
  if (isInterview.value) return '면접 전형'
  return '전형 결과 조건 (선택)'
})

const showStage = computed(() => props.type !== 'DEADLINE_REMINDER')
const showResult = computed(
  () => props.type === 'RESULT_ANNOUNCEMENT' || (props.type === 'FREE' && condition.value.stageId !== null),
)

const groupOptions = computed(() => [
  { value: 'ALL', label: '전체 조' },
  ...props.interviewGroups.map((group) => ({ value: group, label: interviewGroupLabel(group) })),
])

const changeStage = (value: unknown): void => {
  condition.value = { ...condition.value, stageId: typeof value === 'number' ? value : null, interviewGroup: 'ALL' }
}

const changeResult = (value: unknown): void => {
  condition.value = { ...condition.value, resultStatus: value as ResultFilter }
}

const changeGroup = (value: unknown): void => {
  condition.value = { ...condition.value, interviewGroup: String(value) }
}

const changeApplicationStatus = (value: unknown): void => {
  condition.value = { ...condition.value, applicationStatus: value as ApplicationFilter }
}

const changePosting = (value: unknown): void => {
  if (typeof value === 'number') {
    emit('changePosting', value)
  }
}
</script>

<template>
  <section class="target-bar">
    <div class="bar-body">
      <div class="fields">
        <div class="field wide">
          <span class="field-label">{{ type === 'DEADLINE_REMINDER' ? '공고 (접수 중인 공고만)' : '공고' }}</span>
          <a-select
            :value="condition.jobPostingId ?? undefined"
            :options="postingOptions"
            placeholder="공고를 선택하세요"
            show-search
            option-filter-prop="label"
            not-found-content="선택할 수 있는 공고가 없습니다"
            @change="changePosting"
          />
        </div>
        <div v-if="showStage" class="field">
          <span class="field-label">{{ stageLabel }}</span>
          <a-select
            :value="condition.stageId ?? undefined"
            :options="stageOptions"
            :placeholder="type === 'FREE' ? '조건 없음' : '전형을 선택하세요'"
            :allow-clear="type === 'FREE'"
            not-found-content="선택할 수 있는 전형이 없습니다"
            @change="changeStage"
          />
        </div>
        <div v-if="showResult" class="field">
          <span class="field-label">결과</span>
          <a-select :value="condition.resultStatus" :options="RESULT_FILTER_OPTIONS" @change="changeResult" />
        </div>
        <div v-if="isInterview" class="field">
          <span class="field-label">조</span>
          <a-select :value="condition.interviewGroup" :options="groupOptions" @change="changeGroup" />
        </div>
        <div v-if="type === 'FREE'" class="field">
          <span class="field-label">지원 상태</span>
          <a-select
            :value="condition.applicationStatus"
            :options="APPLICATION_FILTER_OPTIONS"
            @change="changeApplicationStatus"
          />
        </div>
      </div>
      <div class="recipient-summary">
        <div class="count">
          <strong>{{ selectedCount }}</strong><span class="count-total"> / {{ totalCount }}명</span>
          <span class="count-label">수신 대상</span>
        </div>
        <a-button :loading="loading" @click="emit('openDrawer')"><TeamOutlined /> 대상자 보기·편집</a-button>
      </div>
    </div>
    <p class="hint"><BulbOutlined /> {{ HINTS[type] }}</p>
  </section>
</template>

<style scoped lang="scss">
.target-bar {
  margin-bottom: 14px;
}

.bar-body {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius-lg);
  background: var(--app-bg-surface);
  box-shadow: var(--app-card-shadow);
}

.fields {
  display: flex;
  flex: 1;
  gap: 12px;
  min-width: 0;
}

.field {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 5px;
  min-width: 0;

  &.wide {
    flex: 1.7;
  }
}

.field-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--app-text-secondary);
}

.recipient-summary {
  display: flex;
  flex: none;
  align-items: center;
  gap: 12px;
  height: 52px;
  padding-left: 14px;
  border-left: 1px solid var(--app-border-default);
}

.count {
  line-height: 1.2;

  strong {
    font-size: 22px;
    color: var(--app-color-primary);
  }
}

.count-total {
  font-size: 13px;
  color: var(--app-text-secondary);
}

.count-label {
  display: block;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 8px 2px 0;
  font-size: 12px;
  color: var(--app-text-secondary);
}
</style>
```

- [ ] **Step 7: 수신자 드로어 작성**

`{FE}/views/admin/message/MessageRecipientDrawer.vue`:

```vue
<script setup lang="ts">
import { computed, ref } from 'vue'

import { formatDate } from '@/common/dateUtil'
import type { MessageTargetRecipient, MessageType } from '@/types/admin/message'
import { RESULT_LABEL, interviewGroupLabel, isInterviewType } from './messageCondition'

const props = defineProps<{
  type: MessageType
  recipients: MessageTargetRecipient[]
}>()

const open = defineModel<boolean>('open', { required: true })
const selectedIds = defineModel<number[]>('selectedIds', { required: true })

const keyword = ref('')
const onlyMissing = ref(false)

const selectable = (recipient: MessageTargetRecipient): boolean => recipient.mailAvailable || recipient.smsAvailable

const filtered = computed(() => {
  const word = keyword.value.trim()
  return props.recipients.filter(
    (recipient) =>
      (!word || (recipient.name ?? '').includes(word) || String(recipient.applicationId).includes(word)) &&
      (!onlyMissing.value || !recipient.mailAvailable || !recipient.smsAvailable),
  )
})

const detailTitle = computed(() => {
  if (props.type === 'RESULT_ANNOUNCEMENT') return '결과'
  if (isInterviewType(props.type)) return '조 · 면접 일시'
  if (props.type === 'DEADLINE_REMINDER') return '작성 시작'
  return null
})

const columns = computed(() => [
  { title: '수험번호', dataIndex: 'applicationId', key: 'applicationId', width: 100 },
  { title: '이름', dataIndex: 'name', key: 'name', width: 110 },
  ...(detailTitle.value ? [{ title: detailTitle.value, key: 'detail', width: 190 }] : []),
  { title: '휴대폰', dataIndex: 'phone', key: 'phone', width: 140 },
  { title: '이메일', dataIndex: 'email', key: 'email' },
  { title: '발송 채널', key: 'channels', width: 170 },
])

const detailText = (recipient: MessageTargetRecipient): string => {
  if (props.type === 'RESULT_ANNOUNCEMENT') {
    return recipient.resultStatus ? RESULT_LABEL[recipient.resultStatus] : ''
  }
  if (isInterviewType(props.type)) {
    return `${interviewGroupLabel(recipient.interviewGroup)} · ${formatDate(recipient.interviewDateTime, 'YYYY-MM-DD HH:mm')}`
  }
  return formatDate(recipient.draftStartedAt, 'YYYY-MM-DD HH:mm')
}

const rowSelection = computed(() => ({
  selectedRowKeys: selectedIds.value,
  preserveSelectedRowKeys: true,
  onChange: (keys: (string | number)[]) => {
    selectedIds.value = keys.map(Number)
  },
  getCheckboxProps: (recipient: MessageTargetRecipient) => ({ disabled: !selectable(recipient) }),
}))

const selectAll = (): void => {
  selectedIds.value = props.recipients.filter(selectable).map((recipient) => recipient.applicationId)
}

const clearAll = (): void => {
  selectedIds.value = []
}
</script>

<template>
  <a-drawer v-model:open="open" title="수신자 선택" width="900" :body-style="{ padding: 0 }">
    <div class="drawer-tools">
      <a-input v-model:value="keyword" class="search" placeholder="이름·수험번호 검색" allow-clear />
      <a-checkbox v-model:checked="onlyMissing">연락처 누락만 보기</a-checkbox>
      <a-space class="tools-right">
        <span class="count">{{ selectedIds.length }} / {{ recipients.length }}명 선택</span>
        <a-button size="small" @click="selectAll">전체 선택</a-button>
        <a-button size="small" @click="clearAll">전체 해제</a-button>
      </a-space>
    </div>
    <a-table
      :columns="columns"
      :data-source="filtered"
      :row-selection="rowSelection"
      row-key="applicationId"
      size="small"
      :pagination="{ pageSize: 50, showSizeChanger: false }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'detail'">{{ detailText(record as MessageTargetRecipient) }}</template>
        <template v-else-if="column.key === 'phone'">{{ (record as MessageTargetRecipient).phone ?? '-' }}</template>
        <template v-else-if="column.key === 'email'">{{ (record as MessageTargetRecipient).email ?? '-' }}</template>
        <template v-else-if="column.key === 'channels'">
          <a-tag :color="(record as MessageTargetRecipient).mailAvailable ? undefined : 'red'">
            {{ (record as MessageTargetRecipient).mailAvailable ? '메일' : '이메일 없음' }}
          </a-tag>
          <a-tag :color="(record as MessageTargetRecipient).smsAvailable ? undefined : 'red'">
            {{ (record as MessageTargetRecipient).smsAvailable ? 'SMS' : '휴대폰 없음' }}
          </a-tag>
        </template>
      </template>
    </a-table>
    <template #footer>
      <div class="drawer-footer">
        <span>연락처가 없거나 형식이 맞지 않는 채널은 자동으로 제외됩니다. 연락처는 지원서 기본정보, 없으면 회원정보 기준입니다.</span>
        <a-button type="primary" @click="open = false">선택 완료</a-button>
      </div>
    </template>
  </a-drawer>
</template>

<style scoped lang="scss">
.drawer-tools {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  border-bottom: 1px solid var(--app-border-default);
  background: var(--app-bg-muted);
}

.search {
  width: 220px;
}

.tools-right {
  margin-left: auto;
}

.count {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.drawer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  color: var(--app-text-secondary);
}
</style>
```

- [ ] **Step 8: 타입 검사·린트**

Run: `npm run type-check`
Expected: 오류 없음

Run: `npx eslint src/views/admin/message`
Expected: 0건. ant-design-vue 타입(예: `a-table` `bodyCell` 슬롯의 `record` 타입, `a-select` `@change` 인자 타입) 때문에 타입 오류가 나면, 동작을 바꾸지 않는 최소 수정(타입 단언·인자 타입 `unknown` 등)으로 고치고 보고한다.

---

### Task 9: 작성 영역과 미리보기

**Files:**
- Modify: `{FE}/views/admin/message/messageRender.ts` (`SmsStats` 타입 추가)
- Create: `{FE}/views/admin/message/MessageComposer.vue`
- Create: `{FE}/views/admin/message/MessagePreview.vue`

작성 영역(설계서 3.1절 3): 템플릿 셀렉트(★ 기본, "새로 작성"), "템플릿에서 수정됨" + 되돌리기, 템플릿으로 저장(현재 템플릿 덮어쓰기 / 새 이름), 채널 탭 `메일`·`SMS`와 탭별 켜기·끄기 스위치, SMS byte 카운터, 변수 칩. 탭과 미리보기 채널은 같은 값(`channel`)을 공유한다.
미리보기(3.1절 4): 수신자 이동, 메일 화면·휴대폰 화면, 치환 값 강조·빈 변수 빨간 강조, 채널 제외·연락처 없음 안내. 본문은 `v-html` 없이 `renderParts` 조각으로 그린다.

- [ ] **Step 1: `SmsStats` 타입 추가**

`{FE}/views/admin/message/messageRender.ts`의 `export const LMS_MAX_BYTES = 2000` 바로 아래에 추가:

```ts

/** 작성 영역 SMS 카운터. current = 미리보기 수신자 기준, max·kind = 선택 수신자 중 가장 긴 것 기준. */
export interface SmsStats {
  currentBytes: number
  maxBytes: number
  kind: SmsKind | null
}
```

- [ ] **Step 2: 작성 영역 작성**

`{FE}/views/admin/message/MessageComposer.vue`:

```vue
<script setup lang="ts">
import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  FileTextOutlined,
  InfoCircleOutlined,
  MailOutlined,
  MessageOutlined,
  RollbackOutlined,
  SaveOutlined,
} from '@ant-design/icons-vue'

import { messageApi } from '@/api/admin/messageApi'
import { getApiErrorMessage } from '@/api/apiError'
import type {
  MessageContent,
  MessageTemplate,
  MessageTemplateSaveRequest,
  MessageType,
  MessageVariable,
} from '@/types/admin/message'
import { LMS_MAX_BYTES, SMS_MAX_BYTES, type SmsStats } from './messageRender'
import { FIELD_MAX_LENGTH, useVariableCursor, type EditableField } from './useVariableCursor'

type Channel = 'mail' | 'sms'

/* 템플릿 셀렉트의 "새로 작성" 값. 실제 템플릿 id 와 겹치지 않는다. */
const NEW_TEMPLATE = -1

const props = defineProps<{
  type: MessageType
  /** 이 종류의 템플릿(기본 우선 정렬) */
  templates: MessageTemplate[]
  /** 이 종류에서 쓸 수 있는 변수 */
  variables: MessageVariable[]
  dirty: boolean
  smsStats: SmsStats
}>()

const content = defineModel<MessageContent>('content', { required: true })
const channel = defineModel<Channel>('channel', { required: true })

const emit = defineEmits<{
  selectTemplate: [templateId: number | null]
  revert: []
  templateSaved: [template: MessageTemplate]
}>()

const templateOptions = computed(() => [
  ...props.templates.map((template) => ({
    value: template.id,
    label: template.defaultTemplate ? `★ ${template.name} (기본)` : template.name,
  })),
  { value: NEW_TEMPLATE, label: '새로 작성 (빈 양식)' },
])

const update = (field: EditableField, value: string): void => {
  content.value = { ...content.value, [field]: value }
}

const { resetCursor, rememberCursor, insertVariable } = useVariableCursor((field) => content.value[field], update)

const selectTemplate = (value: unknown): void => {
  resetCursor()
  emit('selectTemplate', value === NEW_TEMPLATE ? null : Number(value))
}

const toggleChannel = (target: Channel, enabled: boolean): void => {
  content.value =
    target === 'mail' ? { ...content.value, mailEnabled: enabled } : { ...content.value, smsEnabled: enabled }
}

const smsLimit = computed(() => (props.smsStats.kind === 'SMS' ? SMS_MAX_BYTES : LMS_MAX_BYTES))
const smsPercent = computed(() => Math.min(100, Math.round((props.smsStats.currentBytes / smsLimit.value) * 100)))
const smsNote = computed(() => {
  if (props.smsStats.kind === null) {
    return `${props.smsStats.maxBytes}byte인 수신자가 있어 보낼 수 없습니다. 2,000byte 이내로 줄이세요.`
  }
  if (props.smsStats.kind === 'LMS') {
    return `90byte를 넘는 수신자가 있어 LMS로 발송됩니다(최대 ${props.smsStats.maxBytes}byte).`
  }
  return '모든 수신자가 90byte 이내입니다.'
})

/* ---------- 템플릿으로 저장 ---------- */

const saveOpen = ref(false)
const saveMode = ref<'overwrite' | 'new'>('new')
const newName = ref('')
const saving = ref(false)

const currentTemplate = computed(() => props.templates.find((template) => template.id === content.value.templateId))

const openSave = (): void => {
  saveMode.value = currentTemplate.value ? 'overwrite' : 'new'
  newName.value = ''
  saveOpen.value = true
}

const blankToNull = (value: string): string | null => (value.trim() ? value : null)

const submitSave = async (): Promise<void> => {
  const current = currentTemplate.value
  const overwrite = saveMode.value === 'overwrite' && current !== undefined
  const name = overwrite ? current.name : newName.value.trim()
  if (!name) {
    message.warning('템플릿 이름을 입력해 주세요.')
    return
  }
  const request: MessageTemplateSaveRequest = {
    type: props.type,
    name,
    defaultTemplate: overwrite ? current.defaultTemplate : false,
    mailSubject: blankToNull(content.value.mailSubject),
    mailBody: blankToNull(content.value.mailBody),
    smsBody: blankToNull(content.value.smsBody),
  }

  saving.value = true
  try {
    const response = overwrite
      ? await messageApi.updateTemplate(current.id, request)
      : await messageApi.createTemplate(request)
    message.success('템플릿을 저장했습니다.')
    saveOpen.value = false
    emit('templateSaved', response.data.data)
  } catch (error) {
    message.error(getApiErrorMessage(error, '템플릿을 저장하지 못했습니다.'))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="composer">
    <div class="composer-head">
      <FileTextOutlined class="head-icon" />
      <a-select
        class="template-select"
        :value="content.templateId ?? NEW_TEMPLATE"
        :options="templateOptions"
        @change="selectTemplate"
      />
      <a-tag v-if="dirty && content.templateId !== null" color="orange">템플릿에서 수정됨</a-tag>
      <a-space class="head-actions">
        <a-button size="small" type="text" :disabled="!dirty" @click="emit('revert')">
          <RollbackOutlined /> 되돌리기
        </a-button>
        <a-button size="small" @click="openSave"><SaveOutlined /> 템플릿으로 저장</a-button>
      </a-space>
    </div>

    <a-tabs v-model:active-key="channel" class="channel-tabs">
      <a-tab-pane key="mail">
        <template #tab>
          <span class="tab-label" :class="{ off: !content.mailEnabled }">
            <MailOutlined /> 메일
            <span class="switch-wrap" @click.stop>
              <a-switch
                size="small"
                :checked="content.mailEnabled"
                aria-label="메일 발송 켜기/끄기"
                @change="(checked: unknown) => toggleChannel('mail', checked === true)"
              />
            </span>
          </span>
        </template>
        <div v-if="content.mailEnabled" class="pane">
          <div class="field">
            <span class="field-label">제목</span>
            <a-input
              :value="content.mailSubject"
              :maxlength="FIELD_MAX_LENGTH.mailSubject"
              @update:value="(value: string) => update('mailSubject', value)"
              @blur="rememberCursor('mailSubject', $event)"
            />
          </div>
          <div class="field">
            <span class="field-label">본문</span>
            <a-textarea
              :value="content.mailBody"
              :rows="14"
              :maxlength="FIELD_MAX_LENGTH.mailBody"
              @update:value="(value: string) => update('mailBody', value)"
              @blur="rememberCursor('mailBody', $event)"
            />
          </div>
        </div>
        <p v-else class="off-note">메일은 이번 발송에서 제외됩니다. 탭의 스위치로 다시 켤 수 있습니다.</p>
      </a-tab-pane>

      <a-tab-pane key="sms">
        <template #tab>
          <span class="tab-label" :class="{ off: !content.smsEnabled }">
            <MessageOutlined /> SMS
            <span class="switch-wrap" @click.stop>
              <a-switch
                size="small"
                :checked="content.smsEnabled"
                aria-label="SMS 발송 켜기/끄기"
                @change="(checked: unknown) => toggleChannel('sms', checked === true)"
              />
            </span>
          </span>
        </template>
        <div v-if="content.smsEnabled" class="pane">
          <div class="field">
            <span class="field-label">문자 내용</span>
            <a-textarea
              :value="content.smsBody"
              :rows="8"
              :maxlength="FIELD_MAX_LENGTH.smsBody"
              @update:value="(value: string) => update('smsBody', value)"
              @blur="rememberCursor('smsBody', $event)"
            />
          </div>
          <div class="sms-counter">
            <span>
              <a-tag v-if="smsStats.kind === null" color="red">2000byte 초과</a-tag>
              <a-tag v-else-if="smsStats.kind === 'LMS'" color="orange">LMS 장문</a-tag>
              <a-tag v-else color="green">SMS 단문</a-tag>
              <span class="counter-note">{{ smsNote }}</span>
            </span>
            <span>
              <strong>{{ smsStats.currentBytes }}</strong> / {{ smsLimit }} byte
              <span class="counter-note">(미리보기 대상 기준)</span>
            </span>
          </div>
          <a-progress :percent="smsPercent" :show-info="false" size="small" />
        </div>
        <p v-else class="off-note">SMS는 이번 발송에서 제외됩니다. 탭의 스위치로 다시 켤 수 있습니다.</p>
      </a-tab-pane>
    </a-tabs>

    <div class="variables">
      <span class="variables-label">변수 넣기</span>
      <a-tooltip v-for="variable in variables" :key="variable.key" :title="variable.label">
        <button type="button" class="variable-chip" @click="insertVariable(variable.key)">{{ variable.key }}</button>
      </a-tooltip>
    </div>

    <p class="composer-foot">
      <InfoCircleOutlined /> 변수는 수신자마다 실제 값으로 바뀝니다. 여기서 고친 내용은 이번 발송에만 적용됩니다.
    </p>

    <a-modal
      v-model:open="saveOpen"
      title="템플릿으로 저장"
      ok-text="저장"
      cancel-text="취소"
      :confirm-loading="saving"
      @ok="submitSave"
    >
      <a-radio-group v-model:value="saveMode" class="save-mode">
        <a-radio value="overwrite" :disabled="!currentTemplate">
          현재 템플릿 덮어쓰기<template v-if="currentTemplate"> ({{ currentTemplate.name }})</template>
        </a-radio>
        <a-radio value="new">새 템플릿으로 저장</a-radio>
      </a-radio-group>
      <a-input v-if="saveMode === 'new'" v-model:value="newName" :maxlength="100" placeholder="템플릿 이름" />
    </a-modal>
  </section>
</template>

<style scoped lang="scss">
.composer {
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius-lg);
  background: var(--app-bg-surface);
  box-shadow: var(--app-card-shadow);
}

.composer-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--app-border-default);
}

.head-icon {
  color: var(--app-text-secondary);
  font-size: 16px;
}

.template-select {
  flex: 1;
  max-width: 340px;
}

.head-actions {
  margin-left: auto;
}

.channel-tabs {
  padding: 0 16px;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;

  &.off {
    opacity: 0.6;
  }
}

.switch-wrap {
  display: inline-flex;
  margin-left: 4px;
}

.pane {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 12px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.field-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--app-text-secondary);
}

.sms-counter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
}

.counter-note {
  color: var(--app-text-secondary);
}

.off-note {
  margin: 0;
  padding: 28px 0;
  text-align: center;
  color: var(--app-text-secondary);
}

.variables {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin: 0 16px 14px;
  padding: 10px 12px;
  border: 1px dashed var(--app-border-strong);
  border-radius: var(--app-border-radius);
  background: var(--app-bg-muted);
}

.variables-label {
  margin-right: 2px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.variable-chip {
  height: 24px;
  padding: 0 10px;
  border: 1px solid #cfe0c6;
  border-radius: 14px;
  background: var(--app-bg-surface);
  color: var(--app-color-primary);
  font-size: 12px;
  cursor: pointer;

  &:hover {
    background: var(--app-bg-selected);
  }
}

.composer-foot {
  margin: 0;
  padding: 10px 16px;
  border-top: 1px solid var(--app-border-default);
  border-radius: 0 0 var(--app-border-radius-lg) var(--app-border-radius-lg);
  background: var(--app-bg-muted);
  font-size: 12px;
  color: var(--app-text-secondary);
}

.save-mode {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}
</style>
```

- [ ] **Step 3: 미리보기 작성**

`{FE}/views/admin/message/MessagePreview.vue`:

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { LeftOutlined, RightOutlined } from '@ant-design/icons-vue'

import { formatDate } from '@/common/dateUtil'
import type { MessageContent, MessageSender, MessageTargetRecipient, MessageType } from '@/types/admin/message'
import { RESULT_LABEL, interviewGroupLabel, isInterviewType } from './messageCondition'
import { renderMessage, renderParts, smsByteLength, smsKindOf } from './messageRender'

type Channel = 'mail' | 'sms'

const props = defineProps<{
  type: MessageType
  /** 선택된 수신자 */
  recipients: MessageTargetRecipient[]
  content: MessageContent
  sender: MessageSender | null
}>()

const index = defineModel<number>('index', { required: true })
const channel = defineModel<Channel>('channel', { required: true })

const current = computed<MessageTargetRecipient | null>(() => props.recipients[index.value] ?? null)
const values = computed<Record<string, string>>(() => current.value?.variables ?? {})

const subjectParts = computed(() => renderParts(props.content.mailSubject, values.value))
const mailBodyParts = computed(() => renderParts(props.content.mailBody, values.value))
const smsParts = computed(() => renderParts(props.content.smsBody, values.value))
const smsKind = computed(() => smsKindOf(smsByteLength(renderMessage(props.content.smsBody, values.value))))

const subInfo = computed(() => {
  const recipient = current.value
  if (!recipient) return ''
  if (props.type === 'RESULT_ANNOUNCEMENT') {
    return recipient.resultStatus ? RESULT_LABEL[recipient.resultStatus] : ''
  }
  if (isInterviewType(props.type)) {
    return `${interviewGroupLabel(recipient.interviewGroup)} ${formatDate(recipient.interviewDateTime, 'HH:mm')}`
  }
  if (props.type === 'DEADLINE_REMINDER') {
    return `작성 시작 ${formatDate(recipient.draftStartedAt, 'MM-DD HH:mm')}`
  }
  return ''
})

/* 미리보기를 그릴 수 없는 이유. 빈 문자열이면 그린다. */
const blockedReason = computed(() => {
  const recipient = current.value
  if (!recipient) return '미리볼 수신자가 없습니다. 조건을 고르거나 대상자를 선택하세요.'
  const name = recipient.name ?? '이 수신자'
  if (channel.value === 'mail') {
    if (!props.content.mailEnabled) return '이번 발송에서 메일은 제외됩니다.'
    if (!recipient.mailAvailable) return `${name}님은 이메일이 없거나 형식이 맞지 않아 메일에서 제외됩니다.`
  } else {
    if (!props.content.smsEnabled) return '이번 발송에서 SMS는 제외됩니다.'
    if (!recipient.smsAvailable) return `${name}님은 휴대폰 번호가 없거나 형식이 맞지 않아 SMS에서 제외됩니다.`
  }
  return ''
})

const move = (step: number): void => {
  const count = props.recipients.length
  if (count > 0) {
    index.value = (index.value + step + count) % count
  }
}
</script>

<template>
  <section class="preview">
    <div class="preview-head">
      <span class="preview-title">미리보기</span>
      <a-radio-group v-model:value="channel" size="small" button-style="solid">
        <a-radio-button value="mail">메일</a-radio-button>
        <a-radio-button value="sms">SMS</a-radio-button>
      </a-radio-group>
    </div>

    <div class="who">
      <a-button size="small" :disabled="recipients.length < 2" aria-label="이전 수신자" @click="move(-1)">
        <LeftOutlined />
      </a-button>
      <span class="who-name">{{ current?.name ?? '-' }}</span>
      <span class="who-sub">{{ subInfo }}</span>
      <span class="who-pos">{{ recipients.length ? index + 1 : 0 }} / {{ recipients.length }}</span>
      <a-button size="small" :disabled="recipients.length < 2" aria-label="다음 수신자" @click="move(1)">
        <RightOutlined />
      </a-button>
    </div>

    <div class="stage">
      <p v-if="blockedReason" class="blocked">{{ blockedReason }}</p>

      <div v-else-if="channel === 'mail'" class="mail">
        <div class="mail-meta">
          <div class="mail-subject">
            <template v-for="(part, partIndex) in subjectParts" :key="partIndex">
              <mark v-if="part.kind !== 'text'" :class="part.kind">{{ part.text }}</mark>
              <template v-else>{{ part.text }}</template>
            </template>
          </div>
          <div>보낸사람 <strong>{{ sender?.name }}</strong> &lt;{{ sender?.email }}&gt;</div>
          <div>받는사람 {{ current?.name }} &lt;{{ current?.email }}&gt;</div>
        </div>
        <div class="mail-brand"><span class="brand-mark">SY</span>신영증권 채용</div>
        <div class="mail-body">
          <template v-for="(part, partIndex) in mailBodyParts" :key="partIndex">
            <mark v-if="part.kind !== 'text'" :class="part.kind">{{ part.text }}</mark>
            <template v-else>{{ part.text }}</template>
          </template>
        </div>
        <div class="mail-foot">본 메일은 발신 전용입니다.</div>
      </div>

      <div v-else class="phone">
        <div class="phone-screen">
          <div class="phone-top">
            <span class="notch" />
            <strong>{{ sender?.smsCallbackNumber }}</strong>
            <span>문자 메시지</span>
          </div>
          <div class="phone-body">
            <div class="bubble">
              <span>[Web발신]</span>{{ '\n' }}
              <template v-for="(part, partIndex) in smsParts" :key="partIndex">
                <mark v-if="part.kind !== 'text'" :class="part.kind">{{ part.text }}</mark>
                <template v-else>{{ part.text }}</template>
              </template>
            </div>
            <div class="phone-meta">
              <span>{{ smsKind ?? '2000byte 초과' }}</span>
              <span>{{ current?.phone }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.preview {
  position: sticky;
  top: 16px;
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius-lg);
  background: var(--app-bg-surface);
  box-shadow: var(--app-card-shadow);
  overflow: hidden;
}

.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid var(--app-border-default);
}

.preview-title {
  font-weight: 600;
}

.who {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--app-border-default);
  background: var(--app-bg-soft);
  font-size: 12.5px;
}

.who-name {
  font-weight: 600;
}

.who-sub {
  color: var(--app-text-secondary);
}

.who-pos {
  margin-left: auto;
  color: var(--app-text-secondary);
}

.stage {
  display: flex;
  justify-content: center;
  min-height: 470px;
  padding: 18px;
  background: repeating-linear-gradient(45deg, #f3f5f7, #f3f5f7 10px, #eff2f4 10px, #eff2f4 20px);
}

.blocked {
  align-self: center;
  margin: 0;
  text-align: center;
  color: var(--app-text-secondary);
}

mark {
  border-radius: 3px;
  padding: 0 2px;

  &.value {
    background: #e7f3df;
    color: var(--app-color-primary-active);
  }

  &.missing {
    background: #fff1f0;
    color: var(--app-color-error);
  }
}

.mail {
  align-self: flex-start;
  width: 100%;
  border: 1px solid var(--app-border-default);
  border-radius: 10px;
  background: var(--app-bg-surface);
  overflow: hidden;
}

.mail-meta {
  padding: 10px 12px;
  border-bottom: 1px solid var(--app-border-default);
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--app-text-secondary);
}

.mail-subject {
  margin-bottom: 2px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.mail-brand {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 11px 16px;
  background: var(--app-color-primary);
  color: #fff;
  font-size: 12.5px;
  font-weight: 600;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 4px;
  background: #fff;
  color: var(--app-color-primary);
  font-size: 10px;
  font-weight: 700;
}

.mail-body {
  max-height: 320px;
  overflow: auto;
  padding: 16px;
  font-size: 12px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-all;
}

.mail-foot {
  padding: 10px 16px;
  border-top: 1px solid var(--app-border-default);
  background: var(--app-bg-muted);
  font-size: 10.5px;
  color: var(--app-text-muted);
}

.phone {
  width: 264px;
  padding: 9px;
  border-radius: 34px;
  background: #111;
}

.phone-screen {
  display: flex;
  flex-direction: column;
  height: 452px;
  border-radius: 26px;
  background: #fff;
  overflow: hidden;
}

.phone-top {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 14px 8px;
  border-bottom: 1px solid #eee;
  font-size: 12.5px;

  span:last-child {
    font-size: 10.5px;
    color: var(--app-text-muted);
  }
}

.notch {
  width: 70px;
  height: 6px;
  margin-bottom: 8px;
  border-radius: 3px;
  background: #e5e5e5;
}

.phone-body {
  flex: 1;
  overflow: auto;
  padding: 12px 10px;
  background: #f7f7f8;
}

.bubble {
  max-width: 92%;
  padding: 9px 11px;
  border: 1px solid #ececec;
  border-radius: 14px 14px 14px 4px;
  background: #fff;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-all;
}

.phone-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  padding: 0 2px;
  font-size: 10.5px;
  color: var(--app-text-muted);
}
</style>
```

- [ ] **Step 4: 타입 검사·린트**

Run: `npm run type-check`
Expected: 오류 없음

Run: `npx eslint src/views/admin/message`
Expected: 0건. ant-design-vue 이벤트 인자 타입 때문에 오류가 나면 동작을 바꾸지 않는 최소 수정으로 고치고 보고한다.

---

### Task 10: 발송 화면 조립과 라우트

**Files:**
- Create: `{FE}/views/admin/message/AdminMessageSendView.vue`
- Modify: `{FE}/routes/adminRoutes.ts` (`AdminMessageTemplates` 라우트 객체 바로 앞)

흐름:
1. 진입 시 공고 전체(`getAllJobPostings`)·템플릿·변수를 읽고 종류 기본값(결과 발표)으로 초기화한다.
2. 종류를 바꾸면: 그 종류에서 고를 수 있는 공고면 유지, 아니면 첫 공고 → 전형 목록 새로 읽고 기본 전형 선택 → 조건 초기화(결과 발표는 `PASSED`, 직접 입력은 `SUBMITTED`) → 기본 템플릿 적용 → 채널 탭 `메일`.
3. 공고를 바꾸면 전형 목록을 새로 읽고 기본 전형을 고른다.
4. 조회 쿼리(`toTargetQuery`)가 바뀔 때마다 대상자를 다시 읽는다. 늦게 온 응답은 버린다. 읽으면 연락처가 하나라도 있는 수신자를 모두 선택한다. 조회 실패(400 등)는 조건 바 아래 경고로 보여 준다.
5. 템플릿 적용: 템플릿의 채널 내용이 없으면 그 채널을 끈다. "새로 작성"은 둘 다 켠 빈 양식. 되돌리기는 마지막으로 적용·저장한 템플릿 내용으로 되돌린다.
6. "템플릿으로 저장" 후에는 템플릿 목록을 다시 읽고, 저장한 템플릿을 현재 템플릿으로 삼는다(수정됨 해제).
7. 실제 발송·테스트 발송·하단 발송 바는 S3에서 이 화면에 붙인다.

- [ ] **Step 1: 화면 작성**

`{FE}/views/admin/message/AdminMessageSendView.vue`:

```vue
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { FileTextOutlined } from '@ant-design/icons-vue'

import { adminStageApi } from '@/api/admin/adminStageApi'
import { getAllJobPostings } from '@/api/adminJobPostingApi'
import { messageApi } from '@/api/admin/messageApi'
import { getApiErrorMessage } from '@/api/apiError'
import type {
  MessageContent,
  MessageTargetResponse,
  MessageTemplate,
  MessageType,
  MessageVariable,
} from '@/types/admin/message'
import type { StageListItem } from '@/types/admin/stage'
import type { AdminJobPostingListItem } from '@/types/jobPosting'
import MessageComposer from './MessageComposer.vue'
import MessagePreview from './MessagePreview.vue'
import MessageRecipientDrawer from './MessageRecipientDrawer.vue'
import MessageTargetBar from './MessageTargetBar.vue'
import MessageTypePicker from './MessageTypePicker.vue'
import { defaultStageId, selectablePostings, toTargetQuery, type MessageCondition } from './messageCondition'
import { renderMessage, smsByteLength, smsKindOf, type SmsStats } from './messageRender'

type Channel = 'mail' | 'sms'

const router = useRouter()

const type = ref<MessageType>('RESULT_ANNOUNCEMENT')
const postings = ref<AdminJobPostingListItem[]>([])
const stages = ref<StageListItem[]>([])
const templates = ref<MessageTemplate[]>([])
const variables = ref<MessageVariable[]>([])

const initialCondition = (messageType: MessageType, jobPostingId: number | null): MessageCondition => ({
  jobPostingId,
  stageId: null,
  resultStatus: messageType === 'RESULT_ANNOUNCEMENT' ? 'PASSED' : 'ALL',
  interviewGroup: 'ALL',
  applicationStatus: 'SUBMITTED',
})

const condition = ref<MessageCondition>(initialCondition('RESULT_ANNOUNCEMENT', null))

const target = ref<MessageTargetResponse | null>(null)
const targetLoading = ref(false)
const targetError = ref('')
const selectedIds = ref<number[]>([])
const drawerOpen = ref(false)
const previewIndex = ref(0)
const channel = ref<Channel>('mail')

const emptyContent = (): MessageContent => ({
  templateId: null,
  mailEnabled: true,
  smsEnabled: true,
  mailSubject: '',
  mailBody: '',
  smsBody: '',
})

const content = ref<MessageContent>(emptyContent())
/* 되돌리기·수정됨 판정 기준: 마지막으로 적용하거나 저장한 템플릿 내용 */
const baseContent = ref<MessageContent>(emptyContent())

const typeTemplates = computed(() => templates.value.filter((template) => template.type === type.value))
const typeVariables = computed(() => variables.value.filter((variable) => variable.types.includes(type.value)))
const recipients = computed(() => target.value?.recipients ?? [])
const selectedRecipients = computed(() => {
  const selected = new Set(selectedIds.value)
  return recipients.value.filter((recipient) => selected.has(recipient.applicationId))
})

const dirty = computed(
  () =>
    content.value.mailSubject !== baseContent.value.mailSubject ||
    content.value.mailBody !== baseContent.value.mailBody ||
    content.value.smsBody !== baseContent.value.smsBody,
)

const smsStats = computed<SmsStats>(() => {
  const body = content.value.smsBody
  const bytesOf = (values: Record<string, string>): number => smsByteLength(renderMessage(body, values))
  const current = selectedRecipients.value[previewIndex.value]
  const currentBytes = bytesOf(current ? current.variables : {})
  const maxBytes = selectedRecipients.value.reduce((max, recipient) => Math.max(max, bytesOf(recipient.variables)), currentBytes)
  return { currentBytes, maxBytes, kind: smsKindOf(maxBytes) }
})

/* ---------- 템플릿 ---------- */

const applyTemplate = (templateId: number | null): void => {
  const template = templates.value.find((item) => item.id === templateId)
  const next: MessageContent = template
    ? {
        templateId: template.id,
        mailEnabled: template.mailSubject !== null,
        smsEnabled: template.smsBody !== null,
        mailSubject: template.mailSubject ?? '',
        mailBody: template.mailBody ?? '',
        smsBody: template.smsBody ?? '',
      }
    : emptyContent()
  content.value = next
  baseContent.value = { ...next }
}

const applyDefaultTemplate = (): void => {
  applyTemplate(typeTemplates.value.find((template) => template.defaultTemplate)?.id ?? null)
}

const revert = (): void => {
  content.value = {
    ...content.value,
    mailSubject: baseContent.value.mailSubject,
    mailBody: baseContent.value.mailBody,
    smsBody: baseContent.value.smsBody,
  }
}

const loadTemplates = async (): Promise<void> => {
  try {
    const response = await messageApi.getTemplates()
    templates.value = response.data.data
  } catch (error) {
    message.error(getApiErrorMessage(error, '템플릿 목록을 불러오지 못했습니다.'))
  }
}

const onTemplateSaved = async (saved: MessageTemplate): Promise<void> => {
  await loadTemplates()
  content.value = { ...content.value, templateId: saved.id }
  baseContent.value = { ...content.value }
}

/* ---------- 공고·전형 ---------- */

let stageRequest = 0

const refreshStages = async (): Promise<void> => {
  const request = ++stageRequest
  const jobPostingId = condition.value.jobPostingId
  if (jobPostingId === null) {
    stages.value = []
    condition.value = { ...condition.value, stageId: null }
    return
  }
  try {
    const response = await adminStageApi.getStages(jobPostingId)
    if (request !== stageRequest) return
    stages.value = response.data.data
    condition.value = { ...condition.value, stageId: defaultStageId(type.value, stages.value), interviewGroup: 'ALL' }
  } catch (error) {
    if (request !== stageRequest) return
    stages.value = []
    message.error(getApiErrorMessage(error, '전형 목록을 불러오지 못했습니다.'))
  }
}

const onChangePosting = async (jobPostingId: number): Promise<void> => {
  condition.value = { ...initialCondition(type.value, jobPostingId), resultStatus: condition.value.resultStatus }
  await refreshStages()
}

const resetForType = async (): Promise<void> => {
  const selectable = selectablePostings(type.value, postings.value)
  const keep = selectable.some((posting) => posting.id === condition.value.jobPostingId)
  const jobPostingId = keep ? condition.value.jobPostingId : (selectable[0]?.id ?? null)
  condition.value = initialCondition(type.value, jobPostingId)
  channel.value = 'mail'
  applyDefaultTemplate()
  await refreshStages()
}

watch(type, () => {
  void resetForType()
})

/* ---------- 대상자 ---------- */

const query = computed(() => toTargetQuery(type.value, condition.value))

let targetRequest = 0

const loadTargets = async (): Promise<void> => {
  const request = ++targetRequest
  const currentQuery = query.value
  previewIndex.value = 0
  if (currentQuery === null) {
    target.value = null
    selectedIds.value = []
    targetError.value = ''
    targetLoading.value = false
    return
  }
  targetLoading.value = true
  try {
    const response = await messageApi.getTargets(currentQuery)
    if (request !== targetRequest) return
    target.value = response.data.data
    selectedIds.value = target.value.recipients
      .filter((recipient) => recipient.mailAvailable || recipient.smsAvailable)
      .map((recipient) => recipient.applicationId)
    targetError.value = ''
  } catch (error) {
    if (request !== targetRequest) return
    target.value = null
    selectedIds.value = []
    targetError.value = getApiErrorMessage(error, '대상자를 불러오지 못했습니다.')
  } finally {
    if (request === targetRequest) {
      targetLoading.value = false
    }
  }
}

/* 조건 객체는 바뀔 때마다 새로 만들어지므로 쿼리 내용이 같으면 다시 읽지 않는다. */
watch(
  () => JSON.stringify(query.value),
  () => {
    void loadTargets()
  },
)

watch(
  () => selectedRecipients.value.length,
  (count) => {
    if (previewIndex.value >= count) {
      previewIndex.value = 0
    }
  },
)

onMounted(async () => {
  const [postingResult] = await Promise.allSettled([
    getAllJobPostings(),
    loadTemplates(),
    messageApi.getVariables().then((response) => {
      variables.value = response.data.data
    }),
  ])
  if (postingResult.status === 'fulfilled') {
    postings.value = postingResult.value
  } else {
    message.error(getApiErrorMessage(postingResult.reason, '공고 목록을 불러오지 못했습니다.'))
  }
  if (variables.value.length === 0) {
    message.error('변수 목록을 불러오지 못했습니다.')
  }
  await resetForType()
})
</script>

<template>
  <div class="message-send-view">
    <header class="page-header">
      <div>
        <h1 class="page-title">메시지 발송</h1>
        <p class="page-desc">
          보낼 메시지 종류를 고르면 대상자와 내용이 자동으로 채워집니다. 확인 후 테스트 발송, 실제 발송 순서로 진행하세요.
        </p>
      </div>
      <a-button @click="router.push({ name: 'AdminMessageTemplates' })"><FileTextOutlined /> 템플릿 관리</a-button>
    </header>

    <MessageTypePicker v-model="type" />

    <MessageTargetBar
      v-model:condition="condition"
      :type="type"
      :postings="postings"
      :stages="stages"
      :interview-groups="target?.interviewGroups ?? []"
      :selected-count="selectedIds.length"
      :total-count="recipients.length"
      :loading="targetLoading"
      @change-posting="onChangePosting"
      @open-drawer="drawerOpen = true"
    />

    <a-alert v-if="targetError" class="target-alert" type="warning" :message="targetError" show-icon />

    <div class="work">
      <MessageComposer
        v-model:content="content"
        v-model:channel="channel"
        :type="type"
        :templates="typeTemplates"
        :variables="typeVariables"
        :dirty="dirty"
        :sms-stats="smsStats"
        @select-template="applyTemplate"
        @revert="revert"
        @template-saved="onTemplateSaved"
      />
      <MessagePreview
        v-model:index="previewIndex"
        v-model:channel="channel"
        :type="type"
        :recipients="selectedRecipients"
        :content="content"
        :sender="target?.sender ?? null"
      />
    </div>

    <MessageRecipientDrawer
      v-model:open="drawerOpen"
      v-model:selected-ids="selectedIds"
      :type="type"
      :recipients="recipients"
    />
  </div>
</template>

<style scoped lang="scss">
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 18px;
}

.page-title {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 700;
}

.page-desc {
  margin: 0;
  color: var(--app-text-secondary);
}

.target-alert {
  margin-bottom: 14px;
}

.work {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 404px;
  gap: 14px;
  align-items: start;
}
</style>
```

- [ ] **Step 2: 라우트 추가**

`{FE}/routes/adminRoutes.ts` — `path: 'messages/templates'` 라우트 객체 바로 앞(같은 `children` 배열)에 추가:

```ts
      {
        path: 'messages',
        name: 'AdminMessageSend',
        component: () => import('@/views/admin/message/AdminMessageSendView.vue'),
      },
```

- [ ] **Step 3: 타입 검사·린트·단위 테스트**

Run: `npm run type-check`
Expected: 오류 없음

Run: `npx eslint src/views/admin/message src/routes/adminRoutes.ts src/api/admin/messageApi.ts src/types/admin/message.ts`
Expected: 0건

Run: `npx vitest run src/views/admin/message`
Expected: 2 files, 16 tests passed

- [ ] **Step 4: 화면 확인(로컬 환경이 될 때)**

관리자 로그인이 되는 로컬 환경이면 `http://localhost:5173/admin/messages`에서 확인한다. 없으면 건너뛰고 보고에 "화면 수동 확인 못 함"으로 적는다.
1. 결과 발표: 발표 완료 전형이 기본 선택, 결과 "합격" 대상이 조회되고 기본 템플릿이 채워진다. 발표 전 전형은 셀렉트에서 비활성.
2. 서류 마감 임박: 접수 중인 공고만 셀렉트에 나오고 미제출 지원자가 조회된다.
3. 면접 일정·장소: 조 셀렉트에 확정 면접 조가 나오고, 미리보기에서 수신자를 넘기면 면접 일시·장소가 사람마다 바뀐다.
4. 변수 칩을 넣고 SMS를 길게 쓰면 카운터가 LMS로 바뀐다. 드로어에서 체크를 풀면 인원 요약·미리보기 대상이 줄어든다.
5. "템플릿으로 저장"으로 새 템플릿을 만들면 셀렉트에 나타나고 "수정됨" 표시가 사라진다.

---

### Task 11: 도메인 카드·색인 갱신

**Files:**
- Modify: `docs/domains/message.md`
- Modify: `docs/domains/_index.md`

- [ ] **Step 1: 카드 갱신**

`docs/domains/message.md`를 아래대로 고친다.

1. `## 요약`의 `**현재 S1(템플릿)까지 구현.**` → `**현재 S2(대상자·작성)까지 구현.**`. 화면 줄을 `- 화면: `/admin/messages`(`AdminMessageSend`, 종류·조건·작성·미리보기, 발송은 S3) · `/admin/messages/templates`(`AdminMessageTemplates`). 이력(`/admin/messages/history`)은 S4.`로 바꾼다.

2. `## 파일 지도` `### 백엔드` 표에 행 추가(기존 행 유지, `MessageRenderer.java` 행 역할은 `#{키}` 허용 검사·치환(줄바꿈 LF 통일)·SMS byte/구분`으로 바꾼다):

```markdown
| service | `{BE}/service/MessageTargetService.java` | 종류별 대상 조건 검증·조회·연락처 판정·변수 조립 |
| service | `{BE}/service/MessageVariableFormatter.java` | 수신자별 `#{변수}` 값 계산·형식 |
| service | `{BE}/service/MessageVariableContext.java` | 변수 계산 입력(이름·공고·전형·면접) |
| repository | `{BE}/domain/repository/MessageTargetRepository.java` | 대상 JPQL(철회·파기 제외). `Repository<JobApplication, Long>` |
| config | `{BE}/config/MessageProperties.java` | `recruit.message.*` 발신 정보·사이트 주소 |
| enum | `{BE}/enumeration/SmsKind.java` | `SMS`·`LMS` |
| dto | `{BE}/dto/condition/MessageTargetCondition.java` | 대상 조건 |
| dto | `{BE}/dto/response/MessageTargetResponse.java` | 대상 조회 응답 |
| dto | `{BE}/dto/response/MessageTargetRecipientResponse.java` | 수신자 1명 |
| dto | `{BE}/dto/response/MessageSenderResponse.java` | 발신 정보 |
| test | `{BT}/service/MessageVariableFormatterTest.java` | 변수 값 |
| test | `{BT}/service/MessageTargetServiceTest.java` | 종류별 대상 |
| test | `{BT}/controller/MessageSendAdminControllerTest.java` | 대상 API |
```

`MessageSendAdminController.java` 행 역할은 `변수 카탈로그·대상자 조회(S3에서 테스트·발송 추가)`로 바꾼다.

3. `### 프론트` 표: 기존 route 행 역할을 `` `AdminMessageSend`(`/admin/messages`) · `AdminMessageTemplates`(`/admin/messages/templates`) — 공유 파일 ``로 바꾸고(같은 파일 행을 두 번 두지 않는다), `messageRender.ts` 행 역할은 `치환·조각 분리·SMS byte/구분(서버 MessageRenderer와 같은 규칙)`으로 바꾼 뒤, 아래 행을 추가한다:

```markdown
| view | `{FE}/views/admin/message/AdminMessageSendView.vue` | 발송 화면 조립·상태 |
| view | `{FE}/views/admin/message/MessageTypePicker.vue` | 종류 카드 |
| view | `{FE}/views/admin/message/MessageTargetBar.vue` | 조건 바·인원 요약 |
| view | `{FE}/views/admin/message/MessageRecipientDrawer.vue` | 수신자 드로어 |
| view | `{FE}/views/admin/message/MessageComposer.vue` | 작성 영역·템플릿으로 저장 |
| view | `{FE}/views/admin/message/MessagePreview.vue` | 메일·휴대폰 미리보기 |
| util | `{FE}/views/admin/message/messageCondition.ts` | 조건 상태·쿼리 변환·라벨 |
| util | `{FE}/views/admin/message/useVariableCursor.ts` | 변수 칩 삽입(템플릿·발송 화면 공유) |
| test | `{FE}/views/admin/message/__tests__/messageCondition.spec.ts` | Vitest |
```

4. `## API 계약` 표에서 `/admin/messages/targets` 행을 🟢로 바꾸고 응답을 `` `MessageTargetResponse` `{ recipients[], interviewGroups[], sender }` `` 로 고친다. `### 엔드포인트 상세`에 추가:

```markdown
- `MessageTargetResponse`: `recipients[]` = `{ applicationId, name, email, phone, mailAvailable, smsAvailable, resultStatus, interviewGroup, interviewDateTime, draftStartedAt, variables{키: 값}, missingVariables[] }`, `interviewGroups[]`(면접 2종, 숫자 조는 숫자순), `sender { name, email, smsCallbackNumber }`(설정값, 설계서 대비 추가).
- targets 오류: 공고 없음 404, 공고에 없는 전형 404(`StageNotFoundException`), 선택 불가 조건 400(`전형을 선택해야 합니다.` · `결과가 발표된 전형만 선택할 수 있습니다.` · `접수 중인 공고만 선택할 수 있습니다.` · `면접 전형만 선택할 수 있습니다.` · `결과 조건은 전형을 선택해야 쓸 수 있습니다.` · `선택할 수 없는 결과입니다.` · `철회한 지원서는 대상이 아닙니다.`).
```

5. `## 규칙·불변식`의 SMS byte 불릿을 아래로 바꾸고, 불릿을 추가한다:

```markdown
- SMS byte: 코드포인트 단위로 127 이하 1byte, 그 밖 2byte. 90 이하 SMS, 2000 이하 LMS. 치환 전에 `\r\n`·`\r`을 `\n`으로 통일한다. 서버 `MessageRenderer`·프론트 `messageRender.ts`가 같은 규칙·같은 테스트 예시를 쓴다.
- 치환은 한 번만(값 안의 `#{…}`는 그대로), 값이 없으면 빈 문자열. 미리보기는 빈 변수를 빨간 강조로 `#{키}` 그대로 보여 준다.
- 대상: 철회·파기 지원서는 항상 제외. 결과 발표 = 발표 완료·마감 전형의 판정 결과(`PENDING`·`WITHDRAWN` 제외), 마감 임박 = 접수 중 게시 공고의 `DRAFT`, 면접 2종 = `CONFIRMED` 면접의 `CANDIDATE`·`ASSIGNED`(여러 면접이면 가장 이른 면접), 직접 입력 = 지원 상태(기본 작성 중+제출) + 선택 전형 결과. (`MessageTargetService`)
- 연락처: 기본정보 → 회원정보 순. 휴대폰은 숫자만 `01`로 시작 10~11자리, 이메일은 `x@y.z` 형식이어야 그 채널 가능.
```

6. `## 변경 레시피`의 변수 추가 항목에서 `S2 이후라면 변수 값 계산(`MessageTargetService`)도 추가`를 `변수 값 계산은 `MessageVariableFormatter`의 `switch`에 추가(누락 시 컴파일 오류)`로 바꾼다.

7. `## 검증`의 백엔드 명령에 `--tests "com.shinyoung.recruit.controller.MessageSendAdminControllerTest"`를 추가하고, 프론트 줄은 `npx vitest run src/views/admin/message` 그대로 둔다.

- [ ] **Step 2: 색인 갱신**

`docs/domains/_index.md` 라우트 → 카드 표의 `AdminMessageTemplates` 행을 아래로 바꾼다:

```markdown
| `AdminMessageSend` · `AdminMessageTemplates` | `/admin/messages` · `/admin/messages/templates` | message |
```

키워드 행 `| 메일, SMS, 문자, LMS, 메시지 발송, ...` 끝에 `, 대상자, 미리보기`를 덧붙인다(같은 행 안).

- [ ] **Step 3: 문서 점검**

Run(레포 루트): `node tools/check-docs.mjs`
Expected: `문서 점검 통과`, 오류 0건

---

### Task 12: S2 마무리 검증과 보고

- [ ] **Step 1: 변경 범위 테스트**

Run(`recruit_back/recruit_backend/`): `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.Message*" --tests "com.shinyoung.recruit.controller.Message*" --tests "com.shinyoung.recruit.config.ApplicationYamlTest" --tests "com.shinyoung.recruit.config.SecurityConfigTest" --no-daemon`
Expected: `BUILD SUCCESSFUL`

Run(`recruit_front/`): `npm run type-check` → 오류 없음. `npx vitest run src/views/admin/message` → 16 passed. `npx eslint src/views/admin/message src/routes/adminRoutes.ts src/api/admin/messageApi.ts src/types/admin/message.ts` → 0건.

Run(레포 루트): `node tools/check-docs.mjs` → 오류 0건

- [ ] **Step 2: 보고(한국어)**

변경 파일, 테스트 결과(명령·통과 수), 계약 변경(targets 🟢, `sender` 추가), 화면 수동 확인 여부, 남은 미결을 보고한다.

- [ ] **Step 3: slice 구현 리포트**

`design-report` 스킬로 `docs/archive/reports/message-send-s2_implementation.html`을 만든다.

- [ ] **Step 4: 다음 단계**

S3(테스트 발송·실제 발송) 계획을 S2 코드 기준으로 작성한다: 발송 단위(내용 1개 + 최대 10명) 묶기, 비동기 디스패처, 게이트웨이 목업, 최대 3,000명, 확인 모달·하단 발송 바·테스트 카드.
