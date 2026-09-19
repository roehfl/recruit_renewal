# 메시지 발송 S1 — 템플릿 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자가 메시지 종류별 메일·SMS 템플릿을 등록·수정·삭제하고, 종류당 기본 템플릿 1개를 지정할 수 있게 한다(설계서 S1).

**Architecture:** 백엔드에 `MessageType`·`MessageVariable` enum, `MessageTemplate` 엔티티, 변수 허용 검사(`MessageRenderer.validateVariables`), 템플릿 CRUD API와 변수 카탈로그 API를 추가한다. 프론트에 `/admin/messages/templates` 템플릿 관리 화면(왼쪽 종류별 목록, 오른쪽 편집기)을 추가한다. 새 도메인 카드 `message`를 만든다.

**Tech Stack:** Spring Boot 4 · Java 17 · JPA · JUnit5/AssertJ/MockMvc · Vue 3 · TypeScript · ant-design-vue 4 · Vitest

**설계서:** `docs/superpowers/specs/2026-09-19-message-send-design.md` (3.3절, 5절, 8절 `MessageTemplate`, 9절 템플릿·변수 API). 화면 목업: `design/메시지-발송.html`의 "메시지 템플릿" 화면.

**이 계획의 범위 밖:** 대상자 조회·치환·발송·이력(S2~S4). S2~S4 계획은 S1 완료 후 실제 코드 기준으로 따로 작성한다.

**작업 규칙(레포 AGENTS.md):**
- 커밋은 사용자가 요청할 때만 한다. 각 Task의 "커밋" 단계는 사용자 승인 후 실행한다. 메시지 형식 `feat(message): ...`.
- 백엔드 테스트는 수정한 클래스만 실행한다. `AES_SECRET_KEY`는 `recruit_back/recruit_backend/AGENTS.md` 8절의 로컬 예시 값을 쓴다. 아래 명령의 `<로컬 예시 키>` 자리에 넣는다.
- 백엔드 명령은 `recruit_back/recruit_backend/`에서, 프론트 명령은 `recruit_front/`에서 실행한다.

---

## 파일 구조

경로 표기: `{BE}` = `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit`, `{BT}` = `recruit_back/recruit_backend/src/test/java/com/shinyoung/recruit`, `{FE}` = `recruit_front/src`.

| 구분 | 파일 | 책임 |
|---|---|---|
| 생성 | `{BE}/enumeration/MessageType.java` | 메시지 종류 5개 |
| 생성 | `{BE}/enumeration/MessageVariable.java` | 변수 키·설명·허용 종류, 키로 찾기 |
| 생성 | `{BE}/exception/InvalidMessageException.java` | 400 |
| 생성 | `{BE}/exception/MessageTemplateNotFoundException.java` | 404 |
| 수정 | `{BE}/exception/GlobalExceptionHandler.java` | 위 두 예외 핸들러 |
| 생성 | `{BE}/service/MessageRenderer.java` | 본문의 `#{키}` 추출, 종류별 허용 검사(S2에서 치환·byte 추가) |
| 생성 | `{BE}/domain/entity/MessageTemplate.java` | 템플릿 엔티티 |
| 생성 | `{BE}/domain/repository/MessageTemplateRepository.java` | 종류별·기본 템플릿 조회 |
| 생성 | `{BE}/dto/request/MessageTemplateSaveRequest.java` | 등록·수정 공용 요청 |
| 생성 | `{BE}/dto/response/MessageTemplateResponse.java` | 템플릿 응답 |
| 생성 | `{BE}/dto/response/MessageVariableResponse.java` | 변수 카탈로그 응답 |
| 생성 | `{BE}/service/MessageTemplateService.java` | 검증·기본 단일화·CRUD·변수 목록 |
| 생성 | `{BE}/controller/MessageTemplateAdminController.java` | `/admin/message-templates` 5개 |
| 생성 | `{BE}/controller/MessageSendAdminController.java` | `/admin/messages/variables` (S2·S3에서 대상자·발송 추가) |
| 생성 | `{BT}/service/MessageRendererTest.java` | 변수 검사 단위 테스트 |
| 생성 | `{BT}/service/MessageTemplateServiceTest.java` | 서비스 통합 테스트 |
| 생성 | `{BT}/controller/MessageTemplateAdminControllerTest.java` | API 테스트 |
| 수정 | `{BT}/config/SecurityConfigTest.java` | 401·403·통과 테스트 3개 |
| 생성 | `{FE}/types/admin/message.ts` | 타입 |
| 생성 | `{FE}/api/admin/messageApi.ts` | 템플릿·변수 API |
| 생성 | `{FE}/views/admin/message/messageTypes.ts` | 종류 표시 메타(묶음·이름·설명) |
| 생성 | `{FE}/views/admin/message/messageRender.ts` | SMS byte·구분(S2에서 치환 추가) |
| 생성 | `{FE}/views/admin/message/__tests__/messageRender.spec.ts` | Vitest |
| 생성 | `{FE}/views/admin/message/AdminMessageTemplateView.vue` | 템플릿 관리 화면 |
| 수정 | `{FE}/routes/adminRoutes.ts` | 라우트 `AdminMessageTemplates` |
| 생성 | `docs/domains/message.md` | 도메인 카드 |
| 수정 | `docs/domains/_index.md` | 카드 목록·역색인 |

---

### Task 1: 종류·변수 enum과 변수 허용 검사

**Files:**
- Create: `{BE}/enumeration/MessageType.java`
- Create: `{BE}/enumeration/MessageVariable.java`
- Create: `{BE}/exception/InvalidMessageException.java`
- Create: `{BE}/service/MessageRenderer.java`
- Test: `{BT}/service/MessageRendererTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/service/MessageRendererTest.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.exception.InvalidMessageException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageRendererTest {

    private final MessageRenderer renderer = new MessageRenderer();

    @Test
    void 종류에_허용된_변수만_있으면_통과한다() {
        assertThatCode(() -> renderer.validateVariables(
                MessageType.INTERVIEW_SCHEDULE,
                "[신영증권] #{이름}님 #{전형명} 안내",
                "일시: #{면접일시}\n도착: #{도착시각}\n장소: #{면접장소}",
                null
        )).doesNotThrowAnyException();
    }

    @Test
    void 변수가_없는_본문은_통과한다() {
        assertThatCode(() -> renderer.validateVariables(MessageType.FREE, "채용 설명회에 초대합니다."))
                .doesNotThrowAnyException();
    }

    @Test
    void 다른_종류의_변수가_있으면_거부한다() {
        assertThatThrownBy(() -> renderer.validateVariables(
                MessageType.RESULT_ANNOUNCEMENT,
                "#{이름}님 면접은 #{면접일시}입니다."
        ))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("사용할 수 없는 변수: #{면접일시}");
    }

    @Test
    void 없는_키는_중복없이_모두_알려준다() {
        assertThatThrownBy(() -> renderer.validateVariables(
                MessageType.FREE,
                "#{성명} #{이름} #{성명}",
                "#{마감일시}"
        ))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("사용할 수 없는 변수: #{성명}, #{마감일시}");
    }
}
```

- [ ] **Step 2: 테스트가 컴파일 실패하는지 확인**

Run(PowerShell): `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageRendererTest" --no-daemon`
Expected: FAIL — `MessageRenderer`, `MessageType`, `InvalidMessageException`을 찾을 수 없다는 컴파일 오류.

- [ ] **Step 3: enum·예외·검사 구현**

`{BE}/enumeration/MessageType.java`:

```java
package com.shinyoung.recruit.enumeration;

/** 메시지 종류. 화면의 묶음(공고 관련·면접 안내·기타)은 프론트 표시용이다. */
public enum MessageType {
    RESULT_ANNOUNCEMENT,
    DEADLINE_REMINDER,
    INTERVIEW_SCHEDULE,
    INTERVIEW_NOTICE,
    FREE
}
```

`{BE}/enumeration/MessageVariable.java`:

```java
package com.shinyoung.recruit.enumeration;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static com.shinyoung.recruit.enumeration.MessageType.DEADLINE_REMINDER;
import static com.shinyoung.recruit.enumeration.MessageType.INTERVIEW_NOTICE;
import static com.shinyoung.recruit.enumeration.MessageType.INTERVIEW_SCHEDULE;
import static com.shinyoung.recruit.enumeration.MessageType.RESULT_ANNOUNCEMENT;

/**
 * 본문의 #{키} 변수. 키는 관리자가 읽기 쉽게 한글 이름 그대로 쓴다.
 * 종류별 허용 목록의 단일 출처이며 프론트는 변수 카탈로그 API로 받아 쓴다.
 */
public enum MessageVariable {
    NAME("이름", "지원자 이름", EnumSet.allOf(MessageType.class)),
    JOB_POSTING_TITLE("공고명", "공고 제목", EnumSet.allOf(MessageType.class)),
    SITE_URL("채용사이트", "채용 사이트 주소", EnumSet.allOf(MessageType.class)),
    STAGE_NAME("전형명", "전형 이름", EnumSet.of(RESULT_ANNOUNCEMENT, INTERVIEW_SCHEDULE, INTERVIEW_NOTICE)),
    DEADLINE("마감일시", "서류 접수 마감 일시", EnumSet.of(DEADLINE_REMINDER)),
    D_DAY("남은기간", "마감까지 남은 기간(D-n)", EnumSet.of(DEADLINE_REMINDER)),
    INTERVIEW_DATE_TIME("면접일시", "면접 시작 일시", EnumSet.of(INTERVIEW_SCHEDULE, INTERVIEW_NOTICE)),
    ARRIVAL_TIME("도착시각", "면접 도착 시각", EnumSet.of(INTERVIEW_SCHEDULE)),
    INTERVIEW_PLACE("면접장소", "면접 장소·호실", EnumSet.of(INTERVIEW_SCHEDULE, INTERVIEW_NOTICE)),
    INTERVIEW_METHOD("면접방식", "면접 방식", EnumSet.of(INTERVIEW_SCHEDULE)),
    MEETING_URL("접속링크", "온라인 면접 접속 링크", EnumSet.of(INTERVIEW_SCHEDULE, INTERVIEW_NOTICE)),
    GROUP("조", "면접 조", EnumSet.of(INTERVIEW_SCHEDULE));

    private final String key;
    private final String label;
    private final Set<MessageType> types;

    MessageVariable(String key, String label, Set<MessageType> types) {
        this.key = key;
        this.label = label;
        this.types = types;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public Set<MessageType> getTypes() {
        return types;
    }

    public boolean isAllowedFor(MessageType type) {
        return types.contains(type);
    }

    public static Optional<MessageVariable> fromKey(String key) {
        return Arrays.stream(values())
                .filter(variable -> variable.key.equals(key))
                .findFirst();
    }
}
```

`{BE}/exception/InvalidMessageException.java`:

```java
package com.shinyoung.recruit.exception;

public class InvalidMessageException extends RuntimeException {

    public InvalidMessageException(String message) {
        super(message);
    }
}
```

`{BE}/service/MessageRenderer.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.MessageVariable;
import com.shinyoung.recruit.exception.InvalidMessageException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 메시지 본문 규칙. 프론트 views/admin/message/messageRender.ts 와 같은 규칙을 유지한다.
 * S1은 변수 허용 검사만, 치환·SMS byte는 S2에서 추가한다.
 */
@Component
public class MessageRenderer {

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
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageRendererTest" --no-daemon`
Expected: PASS (4 tests)

- [ ] **Step 5: 커밋(사용자 승인 후)**

```bash
git add recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/enumeration/MessageType.java recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/enumeration/MessageVariable.java recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/exception/InvalidMessageException.java recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/service/MessageRenderer.java recruit_back/recruit_backend/src/test/java/com/shinyoung/recruit/service/MessageRendererTest.java
git commit -m "feat(message): 메시지 종류·변수 enum과 변수 허용 검사 추가"
```

---

### Task 2: `MessageTemplate` 엔티티와 리포지토리

**Files:**
- Create: `{BE}/domain/entity/MessageTemplate.java`
- Create: `{BE}/domain/repository/MessageTemplateRepository.java`

엔티티는 Task 3 서비스 테스트로 검증한다(리포지토리에 커스텀 쿼리 로직이 없어 별도 `@DataJpaTest`는 두지 않는다).

- [ ] **Step 1: 엔티티 작성**

`{BE}/domain/entity/MessageTemplate.java`:

```java
package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.MessageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 메시지 종류 1개에 속한 메일(제목·본문)과 SMS 본문 묶음. 종류당 기본 템플릿은 서비스가 1개로 유지한다. */
@Entity
@Getter
@Table(
        name = "message_template",
        indexes = {
                @Index(name = "idx_message_template_type", columnList = "message_type")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageTemplate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 40)
    private MessageType type;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean defaultTemplate;

    @Column(length = 200)
    private String mailSubject;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String mailBody;

    @Column(length = 2000)
    private String smsBody;

    private MessageTemplate(MessageType type, String name, boolean defaultTemplate,
                            String mailSubject, String mailBody, String smsBody) {
        this.type = type;
        this.name = name;
        this.defaultTemplate = defaultTemplate;
        this.mailSubject = mailSubject;
        this.mailBody = mailBody;
        this.smsBody = smsBody;
    }

    public static MessageTemplate create(MessageType type, String name, boolean defaultTemplate,
                                         String mailSubject, String mailBody, String smsBody) {
        return new MessageTemplate(type, name, defaultTemplate, mailSubject, mailBody, smsBody);
    }

    public void update(MessageType type, String name, boolean defaultTemplate,
                       String mailSubject, String mailBody, String smsBody) {
        this.type = type;
        this.name = name;
        this.defaultTemplate = defaultTemplate;
        this.mailSubject = mailSubject;
        this.mailBody = mailBody;
        this.smsBody = smsBody;
    }

    public void unmarkDefault() {
        this.defaultTemplate = false;
    }
}
```

- [ ] **Step 2: 리포지토리 작성**

`{BE}/domain/repository/MessageTemplateRepository.java`:

```java
package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.MessageTemplate;
import com.shinyoung.recruit.enumeration.MessageType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, Long> {

    List<MessageTemplate> findByType(MessageType type);

    List<MessageTemplate> findByTypeAndDefaultTemplateTrue(MessageType type);
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `.\gradlew.bat compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋(사용자 승인 후)**

```bash
git add recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/domain/entity/MessageTemplate.java recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/domain/repository/MessageTemplateRepository.java
git commit -m "feat(message): MessageTemplate 엔티티와 리포지토리 추가"
```

---

### Task 3: 템플릿 서비스 (검증·기본 템플릿 단일화·CRUD)

**Files:**
- Create: `{BE}/exception/MessageTemplateNotFoundException.java`
- Modify: `{BE}/exception/GlobalExceptionHandler.java` (`InvalidQuestionTemplateException` 핸들러 바로 아래)
- Create: `{BE}/dto/request/MessageTemplateSaveRequest.java`
- Create: `{BE}/dto/response/MessageTemplateResponse.java`
- Create: `{BE}/dto/response/MessageVariableResponse.java`
- Create: `{BE}/service/MessageTemplateService.java`
- Test: `{BT}/service/MessageTemplateServiceTest.java`

규칙(설계서 8절): 메일은 제목·본문을 함께 채우거나 함께 비운다. 메일과 SMS 중 하나 이상 필요. 변수는 종류에 허용된 것만. 기본으로 저장하면 같은 종류의 다른 기본 템플릿을 해제한다. 빈 문자열·공백만 있는 값은 null로 저장한다. 목록은 종류 → 기본 우선 → 이름순.

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/service/MessageTemplateServiceTest.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.request.MessageTemplateSaveRequest;
import com.shinyoung.recruit.dto.response.MessageTemplateResponse;
import com.shinyoung.recruit.dto.response.MessageVariableResponse;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.exception.InvalidMessageException;
import com.shinyoung.recruit.exception.MessageTemplateNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class MessageTemplateServiceTest {

    @Autowired
    private MessageTemplateService messageTemplateService;

    @Test
    void 템플릿을_등록하고_빈_문자열은_null로_저장한다() {
        MessageTemplateResponse saved = messageTemplateService.createTemplate(new MessageTemplateSaveRequest(
                MessageType.RESULT_ANNOUNCEMENT, "  결과 안내  ", false,
                "  ", "", "[신영증권] #{이름}님, #{전형명} 결과가 발표되었습니다."
        ));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.name()).isEqualTo("결과 안내");
        assertThat(saved.mailSubject()).isNull();
        assertThat(saved.mailBody()).isNull();
        assertThat(saved.smsBody()).startsWith("[신영증권]");
    }

    @Test
    void 메일과_SMS가_모두_비면_거부한다() {
        assertThatThrownBy(() -> messageTemplateService.createTemplate(new MessageTemplateSaveRequest(
                MessageType.FREE, "빈 템플릿", false, null, null, " "
        )))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("메일 또는 SMS 내용을 입력해야 합니다.");
    }

    @Test
    void 메일_제목과_본문_중_하나만_있으면_거부한다() {
        assertThatThrownBy(() -> messageTemplateService.createTemplate(new MessageTemplateSaveRequest(
                MessageType.FREE, "제목만", false, "[신영증권] 안내", null, null
        )))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("메일은 제목과 본문을 함께 입력해야 합니다.");
    }

    @Test
    void 종류에_허용되지_않은_변수가_있으면_거부한다() {
        assertThatThrownBy(() -> messageTemplateService.createTemplate(new MessageTemplateSaveRequest(
                MessageType.DEADLINE_REMINDER, "마감 안내", false,
                "#{공고명} 마감 안내", "#{이름}님 면접은 #{면접일시}입니다.", null
        )))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("사용할 수 없는 변수: #{면접일시}");
    }

    @Test
    void 기본으로_저장하면_같은_종류의_기존_기본을_해제한다() {
        MessageTemplateResponse first = createDefault(MessageType.RESULT_ANNOUNCEMENT, "중립 안내");
        MessageTemplateResponse otherType = createDefault(MessageType.DEADLINE_REMINDER, "마감 안내");
        MessageTemplateResponse second = createDefault(MessageType.RESULT_ANNOUNCEMENT, "합격 안내");

        assertThat(messageTemplateService.getTemplate(first.id()).defaultTemplate()).isFalse();
        assertThat(messageTemplateService.getTemplate(second.id()).defaultTemplate()).isTrue();
        assertThat(messageTemplateService.getTemplate(otherType.id()).defaultTemplate()).isTrue();
    }

    @Test
    void 수정으로_기본을_지정해도_기존_기본을_해제한다() {
        MessageTemplateResponse first = createDefault(MessageType.INTERVIEW_NOTICE, "유의사항");
        MessageTemplateResponse second = messageTemplateService.createTemplate(smsRequest(MessageType.INTERVIEW_NOTICE, "변경 공지", false));

        MessageTemplateResponse updated = messageTemplateService.updateTemplate(
                second.id(), smsRequest(MessageType.INTERVIEW_NOTICE, "변경 공지", true));

        assertThat(updated.defaultTemplate()).isTrue();
        assertThat(messageTemplateService.getTemplate(first.id()).defaultTemplate()).isFalse();
    }

    @Test
    void 목록은_종류_기본_이름_순이고_종류로_거를_수_있다() {
        messageTemplateService.createTemplate(smsRequest(MessageType.RESULT_ANNOUNCEMENT, "합격 안내", false));
        messageTemplateService.createTemplate(smsRequest(MessageType.RESULT_ANNOUNCEMENT, "불합격 안내", false));
        createDefault(MessageType.RESULT_ANNOUNCEMENT, "중립 안내");
        createDefault(MessageType.FREE, "설명회 초대");

        List<MessageTemplateResponse> all = messageTemplateService.getTemplates(null);
        List<MessageTemplateResponse> results = messageTemplateService.getTemplates(MessageType.RESULT_ANNOUNCEMENT);

        assertThat(all).extracting(MessageTemplateResponse::name)
                .containsExactly("중립 안내", "불합격 안내", "합격 안내", "설명회 초대");
        assertThat(results).hasSize(3);
    }

    @Test
    void 삭제하면_조회할_수_없다() {
        MessageTemplateResponse saved = createDefault(MessageType.FREE, "삭제 대상");

        messageTemplateService.deleteTemplate(saved.id());

        assertThatThrownBy(() -> messageTemplateService.getTemplate(saved.id()))
                .isInstanceOf(MessageTemplateNotFoundException.class)
                .hasMessage("메시지 템플릿을 찾을 수 없습니다.");
    }

    @Test
    void 변수_목록은_12개이고_종류별_허용을_담는다() {
        List<MessageVariableResponse> variables = messageTemplateService.getVariables();

        assertThat(variables).hasSize(12);
        assertThat(variables.get(0).key()).isEqualTo("이름");
        assertThat(variables).filteredOn(variable -> variable.key().equals("도착시각"))
                .singleElement()
                .satisfies(variable -> assertThat(variable.types()).containsExactly(MessageType.INTERVIEW_SCHEDULE));
    }

    private MessageTemplateResponse createDefault(MessageType type, String name) {
        return messageTemplateService.createTemplate(smsRequest(type, name, true));
    }

    private MessageTemplateSaveRequest smsRequest(MessageType type, String name, boolean defaultTemplate) {
        return new MessageTemplateSaveRequest(type, name, defaultTemplate, null, null, "[신영증권] #{이름}님 안내");
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageTemplateServiceTest" --no-daemon`
Expected: FAIL — `MessageTemplateService`, `MessageTemplateSaveRequest` 등을 찾을 수 없다는 컴파일 오류.

- [ ] **Step 3: 예외·핸들러 작성**

`{BE}/exception/MessageTemplateNotFoundException.java`:

```java
package com.shinyoung.recruit.exception;

public class MessageTemplateNotFoundException extends RuntimeException {

    public MessageTemplateNotFoundException(String message) {
        super(message);
    }
}
```

`{BE}/exception/GlobalExceptionHandler.java` — `handleInvalidQuestionTemplate` 메서드 바로 아래에 추가(필요한 import는 같은 패키지라 불필요):

```java
    @ExceptionHandler(MessageTemplateNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageTemplateNotFound(MessageTemplateNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(InvalidMessageException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidMessage(InvalidMessageException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(e.getMessage()));
    }
```

- [ ] **Step 4: DTO 작성**

`{BE}/dto/request/MessageTemplateSaveRequest.java`:

```java
package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 템플릿 등록·수정 공용. 채널 조합·변수 허용은 MessageTemplateService 가 검증한다. */
public record MessageTemplateSaveRequest(
        @NotNull MessageType type,
        @NotBlank @Size(max = 100) String name,
        @NotNull Boolean defaultTemplate,
        @Size(max = 200) String mailSubject,
        @Size(max = 10000) String mailBody,
        @Size(max = 2000) String smsBody
) {
}
```

`{BE}/dto/response/MessageTemplateResponse.java`:

```java
package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.MessageTemplate;
import com.shinyoung.recruit.enumeration.MessageType;

import java.time.LocalDateTime;

public record MessageTemplateResponse(
        Long id,
        MessageType type,
        String name,
        boolean defaultTemplate,
        String mailSubject,
        String mailBody,
        String smsBody,
        LocalDateTime updatedAt
) {
    public static MessageTemplateResponse from(MessageTemplate template) {
        return new MessageTemplateResponse(
                template.getId(),
                template.getType(),
                template.getName(),
                template.isDefaultTemplate(),
                template.getMailSubject(),
                template.getMailBody(),
                template.getSmsBody(),
                template.getUpdatedAt()
        );
    }
}
```

`{BE}/dto/response/MessageVariableResponse.java`:

```java
package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.MessageVariable;

import java.util.List;

public record MessageVariableResponse(
        String key,
        String label,
        List<MessageType> types
) {
    public static MessageVariableResponse from(MessageVariable variable) {
        return new MessageVariableResponse(
                variable.getKey(),
                variable.getLabel(),
                variable.getTypes().stream().sorted().toList()
        );
    }
}
```

- [ ] **Step 5: 서비스 작성**

`{BE}/service/MessageTemplateService.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.MessageTemplate;
import com.shinyoung.recruit.domain.repository.MessageTemplateRepository;
import com.shinyoung.recruit.dto.request.MessageTemplateSaveRequest;
import com.shinyoung.recruit.dto.response.MessageTemplateResponse;
import com.shinyoung.recruit.dto.response.MessageVariableResponse;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.MessageVariable;
import com.shinyoung.recruit.exception.InvalidMessageException;
import com.shinyoung.recruit.exception.MessageTemplateNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageTemplateService {

    private static final Comparator<MessageTemplate> LIST_ORDER = Comparator
            .comparing(MessageTemplate::getType)
            .thenComparing(MessageTemplate::isDefaultTemplate, Comparator.reverseOrder())
            .thenComparing(MessageTemplate::getName);

    private final MessageTemplateRepository messageTemplateRepository;
    private final MessageRenderer messageRenderer;

    public List<MessageTemplateResponse> getTemplates(MessageType type) {
        List<MessageTemplate> templates = type == null
                ? messageTemplateRepository.findAll()
                : messageTemplateRepository.findByType(type);
        return templates.stream()
                .sorted(LIST_ORDER)
                .map(MessageTemplateResponse::from)
                .toList();
    }

    public MessageTemplateResponse getTemplate(Long templateId) {
        return MessageTemplateResponse.from(findTemplate(templateId));
    }

    public List<MessageVariableResponse> getVariables() {
        return Arrays.stream(MessageVariable.values())
                .map(MessageVariableResponse::from)
                .toList();
    }

    @Transactional
    public MessageTemplateResponse createTemplate(MessageTemplateSaveRequest request) {
        Content content = validate(request);
        if (request.defaultTemplate()) {
            clearDefault(request.type(), null);
        }
        MessageTemplate saved = messageTemplateRepository.save(MessageTemplate.create(
                request.type(), request.name().trim(), request.defaultTemplate(),
                content.mailSubject(), content.mailBody(), content.smsBody()
        ));
        return MessageTemplateResponse.from(saved);
    }

    @Transactional
    public MessageTemplateResponse updateTemplate(Long templateId, MessageTemplateSaveRequest request) {
        MessageTemplate template = findTemplate(templateId);
        Content content = validate(request);
        if (request.defaultTemplate()) {
            clearDefault(request.type(), templateId);
        }
        template.update(
                request.type(), request.name().trim(), request.defaultTemplate(),
                content.mailSubject(), content.mailBody(), content.smsBody()
        );
        return MessageTemplateResponse.from(template);
    }

    @Transactional
    public void deleteTemplate(Long templateId) {
        messageTemplateRepository.delete(findTemplate(templateId));
    }

    private MessageTemplate findTemplate(Long templateId) {
        return messageTemplateRepository.findById(templateId)
                .orElseThrow(() -> new MessageTemplateNotFoundException("메시지 템플릿을 찾을 수 없습니다."));
    }

    private void clearDefault(MessageType type, Long exceptTemplateId) {
        messageTemplateRepository.findByTypeAndDefaultTemplateTrue(type).stream()
                .filter(template -> !template.getId().equals(exceptTemplateId))
                .forEach(MessageTemplate::unmarkDefault);
    }

    private Content validate(MessageTemplateSaveRequest request) {
        Content content = new Content(
                blankToNull(request.mailSubject()),
                blankToNull(request.mailBody()),
                blankToNull(request.smsBody())
        );
        if ((content.mailSubject() == null) != (content.mailBody() == null)) {
            throw new InvalidMessageException("메일은 제목과 본문을 함께 입력해야 합니다.");
        }
        if (content.mailSubject() == null && content.smsBody() == null) {
            throw new InvalidMessageException("메일 또는 SMS 내용을 입력해야 합니다.");
        }
        messageRenderer.validateVariables(request.type(), content.mailSubject(), content.mailBody(), content.smsBody());
        return content;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record Content(String mailSubject, String mailBody, String smsBody) {
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageTemplateServiceTest" --tests "com.shinyoung.recruit.service.MessageRendererTest" --no-daemon`
Expected: PASS (13 tests)

- [ ] **Step 7: 커밋(사용자 승인 후)**

```bash
git add recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/exception recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/dto/request/MessageTemplateSaveRequest.java recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/dto/response/MessageTemplateResponse.java recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/dto/response/MessageVariableResponse.java recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/service/MessageTemplateService.java recruit_back/recruit_backend/src/test/java/com/shinyoung/recruit/service/MessageTemplateServiceTest.java
git commit -m "feat(message): 메시지 템플릿 서비스와 검증 추가"
```

---

### Task 4: 템플릿·변수 API와 보안 테스트

**Files:**
- Create: `{BE}/controller/MessageTemplateAdminController.java`
- Create: `{BE}/controller/MessageSendAdminController.java`
- Test: `{BT}/controller/MessageTemplateAdminControllerTest.java`
- Modify: `{BT}/config/SecurityConfigTest.java` (파일 끝 `}` 바로 위에 테스트 3개)

`/api/admin/**` 매처가 이미 `ADMIN`·`RECRUIT_ADMIN`만 허용하므로 `SecurityConfig`는 바꾸지 않는다. 보안 테스트로 고정만 한다.

- [ ] **Step 1: 실패하는 컨트롤러 테스트 작성**

`{BT}/controller/MessageTemplateAdminControllerTest.java`:

```java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.MessageTemplateSaveRequest;
import com.shinyoung.recruit.dto.response.MessageTemplateResponse;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.service.MessageTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class MessageTemplateAdminControllerTest {

    private static final String SAVE_JSON = """
            {
              "type": "RESULT_ANNOUNCEMENT",
              "name": "중립 안내",
              "defaultTemplate": true,
              "mailSubject": "[신영증권] #{공고명} #{전형명} 결과 안내",
              "mailBody": "#{이름}님, 안녕하세요.",
              "smsBody": "[신영증권] #{이름}님, #{전형명} 결과가 발표되었습니다."
            }
            """;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MessageTemplateService messageTemplateService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void 템플릿을_등록한다() throws Exception {
        mockMvc.perform(post("/api/admin/message-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SAVE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.defaultTemplate").value(true));
    }

    @Test
    void 허용되지_않은_변수는_400() throws Exception {
        mockMvc.perform(post("/api/admin/message-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"FREE","name":"잘못된 변수","defaultTemplate":false,"smsBody":"#{면접일시} 안내"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("사용할 수 없는 변수: #{면접일시}"));
    }

    @Test
    void 이름이_없으면_400() throws Exception {
        mockMvc.perform(post("/api/admin/message-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"FREE","name":"","defaultTemplate":false,"smsBody":"안내"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 종류로_목록을_거른다() throws Exception {
        createTemplate(MessageType.RESULT_ANNOUNCEMENT, "결과 안내");
        createTemplate(MessageType.FREE, "설명회 초대");

        mockMvc.perform(get("/api/admin/message-templates").param("type", "FREE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("설명회 초대"));
    }

    @Test
    void 템플릿을_수정한다() throws Exception {
        MessageTemplateResponse saved = createTemplate(MessageType.FREE, "설명회 초대");

        mockMvc.perform(post("/api/admin/message-templates/{id}", saved.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SAVE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("중립 안내"))
                .andExpect(jsonPath("$.data.type").value("RESULT_ANNOUNCEMENT"));
    }

    @Test
    void 삭제한_템플릿_조회는_404() throws Exception {
        MessageTemplateResponse saved = createTemplate(MessageType.FREE, "삭제 대상");

        mockMvc.perform(post("/api/admin/message-templates/{id}/delete", saved.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/admin/message-templates/{id}", saved.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("메시지 템플릿을 찾을 수 없습니다."));
    }

    @Test
    void 변수_카탈로그를_조회한다() throws Exception {
        mockMvc.perform(get("/api/admin/messages/variables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(12))
                .andExpect(jsonPath("$.data[0].key").value("이름"))
                .andExpect(jsonPath("$.data[0].label").value("지원자 이름"));
    }

    private MessageTemplateResponse createTemplate(MessageType type, String name) {
        return messageTemplateService.createTemplate(
                new MessageTemplateSaveRequest(type, name, false, null, null, "[신영증권] #{이름}님 안내"));
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.MessageTemplateAdminControllerTest" --no-daemon`
Expected: FAIL — 컨트롤러가 없어 404(`status().isOk()` 불일치).

- [ ] **Step 3: 컨트롤러 작성**

`{BE}/controller/MessageTemplateAdminController.java`:

```java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.MessageTemplateSaveRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.MessageTemplateResponse;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.service.MessageTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/message-templates")
public class MessageTemplateAdminController {

    private final MessageTemplateService messageTemplateService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MessageTemplateResponse>>> getTemplates(
            @RequestParam(required = false) MessageType type
    ) {
        return ResponseEntity.ok(ApiResponse.success(messageTemplateService.getTemplates(type)));
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<ApiResponse<MessageTemplateResponse>> getTemplate(@PathVariable Long templateId) {
        return ResponseEntity.ok(ApiResponse.success(messageTemplateService.getTemplate(templateId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MessageTemplateResponse>> createTemplate(
            @Valid @RequestBody MessageTemplateSaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(messageTemplateService.createTemplate(request)));
    }

    @PostMapping("/{templateId}")
    public ResponseEntity<ApiResponse<MessageTemplateResponse>> updateTemplate(
            @PathVariable Long templateId,
            @Valid @RequestBody MessageTemplateSaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(messageTemplateService.updateTemplate(templateId, request)));
    }

    @PostMapping("/{templateId}/delete")
    public ResponseEntity<ApiResponse<Void>> deleteTemplate(@PathVariable Long templateId) {
        messageTemplateService.deleteTemplate(templateId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
```

`{BE}/controller/MessageSendAdminController.java`:

```java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.MessageVariableResponse;
import com.shinyoung.recruit.service.MessageTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 메시지 발송 화면 API. S1은 변수 카탈로그만, 대상자 조회·테스트·발송은 S2·S3에서 추가한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/messages")
public class MessageSendAdminController {

    private final MessageTemplateService messageTemplateService;

    @GetMapping("/variables")
    public ResponseEntity<ApiResponse<List<MessageVariableResponse>>> getVariables() {
        return ResponseEntity.ok(ApiResponse.success(messageTemplateService.getVariables()));
    }
}
```


- [ ] **Step 4: 컨트롤러 테스트 통과 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.MessageTemplateAdminControllerTest" --no-daemon`
Expected: PASS (7 tests)

- [ ] **Step 5: 보안 테스트 추가**

`{BT}/config/SecurityConfigTest.java` 파일 끝 `}` 바로 위에 추가(필요한 import는 이미 있음):

```java
    @Test
    void 메시지_템플릿_조회는_비인증이면_401() throws Exception {
        mockMvc.perform(get("/api/admin/message-templates"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 메시지_템플릿은_지원자_권한이면_403() throws Exception {
        mockMvc.perform(get("/api/admin/message-templates")
                        .with(user("applicant").authorities(() -> "ROLE_APPLICANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 메시지_변수_조회는_운영관리자_권한이면_인가를_통과() throws Exception {
        mockMvc.perform(get("/api/admin/messages/variables")
                        .with(user("recruitAdmin").authorities(() -> "ROLE_RECRUIT_ADMIN")))
                .andExpect(status().is(allOf(not(401), not(403))));
    }
```

- [ ] **Step 6: 보안 테스트 통과 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.config.SecurityConfigTest" --no-daemon`
Expected: PASS (기존 테스트 + 3개)

- [ ] **Step 7: 커밋(사용자 승인 후)**

```bash
git add recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/controller/MessageTemplateAdminController.java recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/controller/MessageSendAdminController.java recruit_back/recruit_backend/src/test/java/com/shinyoung/recruit/controller/MessageTemplateAdminControllerTest.java recruit_back/recruit_backend/src/test/java/com/shinyoung/recruit/config/SecurityConfigTest.java
git commit -m "feat(message): 메시지 템플릿·변수 관리자 API 추가"
```

---

### Task 5: 프론트 타입·API·종류 메타·SMS byte 유틸

**Files:**
- Create: `{FE}/types/admin/message.ts`
- Create: `{FE}/api/admin/messageApi.ts`
- Create: `{FE}/views/admin/message/messageTypes.ts`
- Create: `{FE}/views/admin/message/messageRender.ts`
- Test: `{FE}/views/admin/message/__tests__/messageRender.spec.ts`

- [ ] **Step 1: 실패하는 테스트 작성**

`{FE}/views/admin/message/__tests__/messageRender.spec.ts`:

```ts
import { describe, expect, it } from 'vitest'

import { smsByteLength, smsKindOf } from '../messageRender'

describe('smsByteLength', () => {
  it('ASCII는 1byte, 그 밖(한글 등)은 2byte로 센다', () => {
    expect(smsByteLength('abc 123')).toBe(7)
    expect(smsByteLength('신영')).toBe(4)
    expect(smsByteLength('[신영증권] 안내')).toBe(15)
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
```

- [ ] **Step 2: 실패 확인**

Run: `npx vitest run src/views/admin/message/__tests__/messageRender.spec.ts`
Expected: FAIL — `../messageRender`를 찾을 수 없음.

- [ ] **Step 3: 유틸 작성**

`{FE}/views/admin/message/messageRender.ts`:

```ts
/*
 * 메시지 본문 규칙. 백엔드 MessageRenderer 와 같은 규칙을 유지한다(설계서 6절).
 * S1은 SMS byte·구분만, 변수 치환은 S2에서 추가한다.
 */

export type SmsKind = 'SMS' | 'LMS'

export const SMS_MAX_BYTES = 90
export const LMS_MAX_BYTES = 2000

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
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `npx vitest run src/views/admin/message/__tests__/messageRender.spec.ts`
Expected: PASS (3 tests)

- [ ] **Step 5: 타입·API·종류 메타 작성**

`{FE}/types/admin/message.ts`:

```ts
/*
 * 메시지(메일·SMS) 타입. 백엔드 MessageTemplateResponse / MessageVariableResponse 와 대응한다
 * (docs/domains/message.md "API 계약").
 */

export type MessageType =
  | 'RESULT_ANNOUNCEMENT'
  | 'DEADLINE_REMINDER'
  | 'INTERVIEW_SCHEDULE'
  | 'INTERVIEW_NOTICE'
  | 'FREE'

export interface MessageTemplate {
  id: number
  type: MessageType
  name: string
  defaultTemplate: boolean
  mailSubject: string | null
  mailBody: string | null
  smsBody: string | null
  updatedAt: string
}

/** 등록·수정 공용. 빈 채널은 null 로 보낸다. 메일은 제목·본문을 함께 채우거나 함께 비운다. */
export interface MessageTemplateSaveRequest {
  type: MessageType
  name: string
  defaultTemplate: boolean
  mailSubject: string | null
  mailBody: string | null
  smsBody: string | null
}

/** 본문 변수. key 가 #{key} 로 쓰이고, types 에 든 종류에서만 쓸 수 있다. */
export interface MessageVariable {
  key: string
  label: string
  types: MessageType[]
}
```

`{FE}/api/admin/messageApi.ts`:

```ts
import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type {
  MessageTemplate,
  MessageTemplateSaveRequest,
  MessageType,
  MessageVariable,
} from '@/types/admin/message'

/** 메시지 발송 기능 API 모듈. S1은 템플릿·변수만, 대상자·발송·이력은 이후 slice에서 추가한다. */
export const messageApi = {
  /** 변수 카탈로그. 종류별 허용 여부를 담는다. */
  getVariables() {
    return apiClient.get<ApiResponse<MessageVariable[]>>('/admin/messages/variables')
  },

  /** 템플릿 목록. 종류 → 기본 우선 → 이름순. type 을 주면 그 종류만. */
  getTemplates(type?: MessageType) {
    return apiClient.get<ApiResponse<MessageTemplate[]>>('/admin/message-templates', {
      params: { type },
    })
  },

  /** 기본으로 저장하면 같은 종류의 기존 기본 템플릿이 해제된다. */
  createTemplate(request: MessageTemplateSaveRequest) {
    return apiClient.post<ApiResponse<MessageTemplate>>('/admin/message-templates', request)
  },

  updateTemplate(templateId: number, request: MessageTemplateSaveRequest) {
    return apiClient.post<ApiResponse<MessageTemplate>>(`/admin/message-templates/${templateId}`, request)
  },

  /** 발송 이력은 템플릿 이름·원문을 복사해 두므로 삭제해도 영향이 없다. */
  deleteTemplate(templateId: number) {
    return apiClient.post<ApiResponse<null>>(`/admin/message-templates/${templateId}/delete`)
  },
}
```

`{FE}/views/admin/message/messageTypes.ts`:

```ts
import type { MessageType } from '@/types/admin/message'

export interface MessageTypeMeta {
  type: MessageType
  /** 화면 묶음 라벨(공고 관련·면접 안내·기타). 표시용이다. */
  group: string
  name: string
  description: string
}

/** 표시 순서 = 발송 화면 종류 카드 순서(설계서 2절). */
export const MESSAGE_TYPES: MessageTypeMeta[] = [
  { type: 'RESULT_ANNOUNCEMENT', group: '공고 관련', name: '결과 발표', description: '전형 결과 발표 후 안내' },
  { type: 'DEADLINE_REMINDER', group: '공고 관련', name: '서류 마감 임박', description: '미제출 지원자에게 리마인드' },
  { type: 'INTERVIEW_SCHEDULE', group: '면접 안내', name: '면접 일정·장소', description: '배정된 일시·장소 개별 안내' },
  { type: 'INTERVIEW_NOTICE', group: '면접 안내', name: '면접 공지', description: '준비물·유의사항·변경 공지' },
  { type: 'FREE', group: '기타', name: '직접 입력', description: '내용을 자유롭게 작성' },
]

export const messageTypeLabel = (type: MessageType): string => {
  const meta = MESSAGE_TYPES.find((item) => item.type === type)
  return meta ? `${meta.group} · ${meta.name}` : type
}
```

- [ ] **Step 6: 타입 검사**

Run: `npm run type-check`
Expected: 오류 없이 종료

- [ ] **Step 7: 커밋(사용자 승인 후)**

```bash
git add recruit_front/src/types/admin/message.ts recruit_front/src/api/admin/messageApi.ts recruit_front/src/views/admin/message/messageTypes.ts recruit_front/src/views/admin/message/messageRender.ts recruit_front/src/views/admin/message/__tests__/messageRender.spec.ts
git commit -m "feat(message): 메시지 템플릿 프론트 API·타입·SMS byte 유틸 추가"
```

---

### Task 6: 템플릿 관리 화면과 라우트

**Files:**
- Create: `{FE}/views/admin/message/AdminMessageTemplateView.vue`
- Modify: `{FE}/routes/adminRoutes.ts` (`InterviewSchedulingSetting` 라우트 객체 바로 뒤, `children` 배열 안)

화면(설계서 3.3절, 목업 "메시지 템플릿"): 왼쪽 종류별 목록(검색·종류 필터, ★ 기본), 오른쪽 편집기(이름·종류·기본 체크·메일 제목/본문·SMS·변수 칩), 삭제·복제·저장. 변수 칩은 마지막으로 편집한 입력란의 커서 위치에 `#{키}`를 넣는다(입력란 blur 때 위치를 기억, 기억이 없으면 메일 본문 끝).

- [ ] **Step 1: 화면 작성**

`{FE}/views/admin/message/AdminMessageTemplateView.vue`:

```vue
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { MailOutlined, MessageOutlined, PlusOutlined, StarFilled } from '@ant-design/icons-vue'

import { messageApi } from '@/api/admin/messageApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import type {
  MessageTemplate,
  MessageTemplateSaveRequest,
  MessageType,
  MessageVariable,
} from '@/types/admin/message'
import { MESSAGE_TYPES, messageTypeLabel } from './messageTypes'
import { smsByteLength, smsKindOf } from './messageRender'

type EditableField = 'mailSubject' | 'mailBody' | 'smsBody'

interface TemplateForm {
  type: MessageType
  name: string
  defaultTemplate: boolean
  mailSubject: string
  mailBody: string
  smsBody: string
}

const templates = ref<MessageTemplate[]>([])
const variables = ref<MessageVariable[]>([])
const loading = ref(false)
const saving = ref(false)

const keyword = ref('')
const typeFilter = ref<MessageType | 'ALL'>('ALL')
const selectedId = ref<number | null>(null)

const emptyForm = (type: MessageType): TemplateForm => ({
  type,
  name: '',
  defaultTemplate: false,
  mailSubject: '',
  mailBody: '',
  smsBody: '',
})

const form = reactive<TemplateForm>(emptyForm('RESULT_ANNOUNCEMENT'))

/* 변수 칩을 넣을 위치. 입력란 blur 때 갱신하고, 아직 편집한 적이 없으면 메일 본문 끝에 넣는다. */
const cursor = reactive<{ field: EditableField; start: number; end: number }>({
  field: 'mailBody',
  start: Number.MAX_SAFE_INTEGER,
  end: Number.MAX_SAFE_INTEGER,
})

const typeOptions = MESSAGE_TYPES.map((meta) => ({ value: meta.type, label: `${meta.group} · ${meta.name}` }))
const typeFilterOptions = [{ value: 'ALL', label: '전체 종류' }, ...typeOptions]

const groups = computed(() => {
  const word = keyword.value.trim()
  return MESSAGE_TYPES.filter((meta) => typeFilter.value === 'ALL' || meta.type === typeFilter.value).map(
    (meta) => ({
      meta,
      items: templates.value.filter((template) => template.type === meta.type && (!word || template.name.includes(word))),
    }),
  )
})

const selectedTemplate = computed<MessageTemplate | undefined>(() =>
  templates.value.find((template) => template.id === selectedId.value),
)

const availableVariables = computed(() => variables.value.filter((variable) => variable.types.includes(form.type)))

const smsBytes = computed(() => smsByteLength(form.smsBody))
const smsKindText = computed(() => {
  const kind = smsKindOf(smsBytes.value)
  return kind === null ? '2000byte 초과' : `${kind} 예상`
})

/* ---------- 조회 ---------- */

const loadTemplates = async (): Promise<void> => {
  loading.value = true
  try {
    const response = await messageApi.getTemplates()
    templates.value = response.data.data
  } catch (error) {
    message.error(getApiErrorMessage(error, '템플릿 목록을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

const loadVariables = async (): Promise<void> => {
  try {
    const response = await messageApi.getVariables()
    variables.value = response.data.data
  } catch (error) {
    message.error(getApiErrorMessage(error, '변수 목록을 불러오지 못했습니다.'))
  }
}

/* ---------- 선택·편집 ---------- */

const selectTemplate = (template: MessageTemplate): void => {
  selectedId.value = template.id
  Object.assign(form, {
    type: template.type,
    name: template.name,
    defaultTemplate: template.defaultTemplate,
    mailSubject: template.mailSubject ?? '',
    mailBody: template.mailBody ?? '',
    smsBody: template.smsBody ?? '',
  })
}

const startNew = (): void => {
  selectedId.value = null
  Object.assign(form, emptyForm(typeFilter.value === 'ALL' ? 'RESULT_ANNOUNCEMENT' : typeFilter.value))
}

const duplicate = (): void => {
  selectedId.value = null
  form.name = `${form.name} (복사본)`
  form.defaultTemplate = false
}

const rememberCursor = (field: EditableField, event: Event): void => {
  const target = event.target as HTMLInputElement | HTMLTextAreaElement
  cursor.field = field
  cursor.start = target.selectionStart ?? form[field].length
  cursor.end = target.selectionEnd ?? cursor.start
}

const insertVariable = (key: string): void => {
  const token = `#{${key}}`
  const value = form[cursor.field]
  const start = Math.min(cursor.start, value.length)
  const end = Math.min(cursor.end, value.length)
  form[cursor.field] = value.slice(0, start) + token + value.slice(end)
  cursor.start = start + token.length
  cursor.end = cursor.start
}

/* ---------- 저장·삭제 ---------- */

const blankToNull = (value: string): string | null => (value.trim() ? value : null)

const toRequest = (): MessageTemplateSaveRequest => ({
  type: form.type,
  name: form.name.trim(),
  defaultTemplate: form.defaultTemplate,
  mailSubject: blankToNull(form.mailSubject),
  mailBody: blankToNull(form.mailBody),
  smsBody: blankToNull(form.smsBody),
})

const save = async (): Promise<void> => {
  if (!form.name.trim()) {
    message.warning('템플릿 이름을 입력해 주세요.')
    return
  }

  saving.value = true
  try {
    const request = toRequest()
    const response =
      selectedId.value === null
        ? await messageApi.createTemplate(request)
        : await messageApi.updateTemplate(selectedId.value, request)
    message.success('템플릿을 저장했습니다.')
    await loadTemplates()
    selectTemplate(response.data.data)
  } catch (error) {
    message.error(getApiErrorMessage(error, '템플릿을 저장하지 못했습니다.'))
  } finally {
    saving.value = false
  }
}

const remove = (): void => {
  const target = selectedTemplate.value
  if (!target) {
    return
  }

  Modal.confirm({
    title: '템플릿을 삭제할까요?',
    content: `"${target.name}" 템플릿을 삭제합니다. 발송 이력에는 영향이 없습니다.`,
    okText: '삭제',
    okType: 'danger',
    cancelText: '취소',
    onOk: async () => {
      try {
        await messageApi.deleteTemplate(target.id)
        message.success('템플릿을 삭제했습니다.')
        await loadTemplates()
        const first = templates.value[0]
        if (first) {
          selectTemplate(first)
        } else {
          startNew()
        }
      } catch (error) {
        message.error(getApiErrorMessage(error, '템플릿을 삭제하지 못했습니다.'))
      }
    },
  })
}

onMounted(async () => {
  await Promise.all([loadVariables(), loadTemplates()])
  const first = templates.value[0]
  if (first) {
    selectTemplate(first)
  }
})
</script>

<template>
  <div class="message-template-view">
    <header class="page-header">
      <div>
        <h1 class="page-title">메시지 템플릿</h1>
        <p class="page-desc">
          종류별로 메일·SMS 문구를 만들어 둡니다. 종류마다 기본 템플릿 1개는 발송 화면에서 자동으로 불러옵니다.
        </p>
      </div>
      <a-button type="primary" @click="startNew"><PlusOutlined /> 새 템플릿</a-button>
    </header>

    <div class="manage-body">
      <!-- 좌: 종류별 목록 -->
      <section class="panel list-panel">
        <div class="list-filter">
          <a-input v-model:value="keyword" placeholder="템플릿 이름 검색" allow-clear />
          <a-select v-model:value="typeFilter" class="type-filter" :options="typeFilterOptions" />
        </div>

        <a-spin :spinning="loading">
          <div v-for="group in groups" :key="group.meta.type" class="template-group">
            <p class="group-label">
              <span>{{ group.meta.group }} · {{ group.meta.name }}</span>
              <span>{{ group.items.length }}</span>
            </p>
            <button
              v-for="template in group.items"
              :key="template.id"
              type="button"
              class="template-item"
              :class="{ selected: template.id === selectedId }"
              @click="selectTemplate(template)"
            >
              <span class="item-name">
                <StarFilled v-if="template.defaultTemplate" class="default-star" />
                {{ template.name }}
                <a-tag v-if="template.defaultTemplate" color="green" class="default-tag">기본</a-tag>
              </span>
              <span class="item-sub">{{ template.mailSubject ?? template.smsBody }}</span>
            </button>
          </div>
        </a-spin>
      </section>

      <!-- 우: 편집기 -->
      <section class="panel">
        <div class="panel-header">
          <div>
            <span class="panel-title">{{ selectedTemplate?.name ?? '새 템플릿' }}</span>
            <p class="panel-meta">
              {{ messageTypeLabel(form.type) }}<template v-if="selectedTemplate?.defaultTemplate"> · 기본 템플릿</template>
            </p>
          </div>
          <a-space>
            <a-button v-if="selectedId !== null" danger @click="remove">삭제</a-button>
            <a-button v-if="selectedId !== null" @click="duplicate">복제</a-button>
            <a-button type="primary" :loading="saving" @click="save">저장</a-button>
          </a-space>
        </div>

        <div class="editor-body">
          <div class="form-row">
            <div class="field">
              <span class="field-label">템플릿 이름</span>
              <a-input v-model:value="form.name" :maxlength="100" />
            </div>
            <div class="field">
              <span class="field-label">메시지 종류</span>
              <a-select v-model:value="form.type" :options="typeOptions" />
            </div>
          </div>
          <a-checkbox v-model:checked="form.defaultTemplate">
            이 종류의 기본 템플릿으로 사용 <span class="hint">(기존 기본 템플릿은 해제됨)</span>
          </a-checkbox>

          <h3 class="section-title"><MailOutlined /> 메일</h3>
          <div class="field">
            <span class="field-label">제목</span>
            <a-input v-model:value="form.mailSubject" :maxlength="200" @blur="rememberCursor('mailSubject', $event)" />
          </div>
          <div class="field">
            <span class="field-label">본문</span>
            <a-textarea
              v-model:value="form.mailBody"
              :rows="10"
              :maxlength="10000"
              @blur="rememberCursor('mailBody', $event)"
            />
          </div>

          <h3 class="section-title">
            <MessageOutlined /> SMS <span class="hint">변수 치환 전 {{ smsBytes }}byte · {{ smsKindText }}</span>
          </h3>
          <a-textarea v-model:value="form.smsBody" :rows="5" :maxlength="2000" @blur="rememberCursor('smsBody', $event)" />

          <div class="variables">
            <span class="variables-label">이 종류에서 쓸 수 있는 변수</span>
            <a-tooltip v-for="variable in availableVariables" :key="variable.key" :title="variable.label">
              <button type="button" class="variable-chip" @click="insertVariable(variable.key)">
                {{ variable.key }}
              </button>
            </a-tooltip>
          </div>
        </div>

        <div class="panel-footer">
          <span>메일·SMS 중 하나만 채워도 저장할 수 있습니다. 메일은 제목과 본문을 함께 입력합니다.</span>
          <span v-if="selectedTemplate">마지막 수정 {{ formatDate(selectedTemplate.updatedAt, 'YYYY-MM-DD HH:mm') }}</span>
        </div>
      </section>
    </div>
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

.manage-body {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.panel {
  background: var(--app-bg-surface);
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius);
  box-shadow: var(--app-shadow-soft);
}

.list-filter {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-bottom: 1px solid var(--app-border-default);
}

.type-filter {
  width: 140px;
  flex: none;
}

.template-group {
  padding-bottom: 4px;
}

.group-label {
  display: flex;
  justify-content: space-between;
  margin: 0;
  padding: 10px 14px 4px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.template-item {
  display: block;
  width: 100%;
  padding: 10px 14px;
  border: 0;
  border-left: 3px solid transparent;
  background: none;
  text-align: left;
  cursor: pointer;

  &:hover {
    background: var(--app-bg-muted);
  }

  &.selected {
    background: var(--app-bg-selected);
    border-left-color: var(--app-color-primary);
  }
}

.item-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.default-star {
  color: #d4a017;
}

.default-tag {
  margin: 0;
}

.item-sub {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: var(--app-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--app-border-default);
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
}

.panel-meta {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.editor-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 18px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
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

.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 6px 0 0;
  padding-top: 14px;
  border-top: 1px solid var(--app-border-default);
  font-size: 14px;
  font-weight: 600;
}

.hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--app-text-muted);
}

.variables {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
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

.panel-footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 18px;
  border-top: 1px solid var(--app-border-default);
  background: var(--app-bg-muted);
  font-size: 12px;
  color: var(--app-text-secondary);
}
</style>
```

- [ ] **Step 2: 라우트 추가**

`{FE}/routes/adminRoutes.ts` — `InterviewSchedulingSetting` 라우트 객체(`path: 'interview'`) 바로 뒤, 같은 `children` 배열 안에 추가:

```ts
      {
        path: 'messages/templates',
        name: 'AdminMessageTemplates',
        component: () => import('@/views/admin/message/AdminMessageTemplateView.vue'),
      },
```

- [ ] **Step 3: 타입 검사·린트**

Run: `npm run type-check`
Expected: 오류 없이 종료

Run: `npx eslint src/views/admin/message src/api/admin/messageApi.ts src/types/admin/message.ts src/routes/adminRoutes.ts`
Expected: 오류 0건(경고가 나오면 이 계획에서 만든 파일의 것만 고친다)

- [ ] **Step 4: 화면 확인(로컬 환경이 될 때)**

관리자 로그인이 되는 로컬 환경(백엔드 `bootRun` + 프론트 `npm run dev`)이면 `http://localhost:5173/admin/messages/templates`에서 확인한다:
1. 새 템플릿 → 종류 "결과 발표", 이름·SMS 입력 → 저장 → 왼쪽 목록에 나타남.
2. 변수 칩 "전형명" 클릭 → SMS 커서 위치에 `#{전형명}` 삽입. 종류를 "서류 마감 임박"으로 바꾸면 칩이 `마감일시`·`남은기간` 등으로 바뀜.
3. `#{면접일시}`를 넣고 종류 "직접 입력"으로 저장 → "사용할 수 없는 변수: #{면접일시}" 오류 메시지.
4. 기본 체크로 같은 종류 템플릿 2개를 차례로 저장 → ★가 나중 것에만 남음.
5. 복제 → 이름 "(복사본)", 기본 해제된 새 템플릿으로 저장. 삭제 → 확인 모달 → 목록에서 사라짐.

로그인 환경(LDAP)이 없으면 이 단계는 건너뛰고 보고에 "화면 수동 확인 못 함"으로 적는다.

- [ ] **Step 5: 커밋(사용자 승인 후)**

```bash
git add recruit_front/src/views/admin/message/AdminMessageTemplateView.vue recruit_front/src/routes/adminRoutes.ts
git commit -m "feat(message): 메시지 템플릿 관리 화면 추가"
```

---

### Task 7: 도메인 카드 `message`와 색인

**Files:**
- Create: `docs/domains/message.md`
- Modify: `docs/domains/_index.md` (카드 목록 표 마지막 행 뒤, 라우트 역색인 표, API 접두 표, 키워드 표)

- [ ] **Step 1: 카드 작성**

`docs/domains/message.md`:

````markdown
# 메일·SMS 메시지 발송 (`message`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [stage-result](stage-result.md) (결과 발표 대상) · [interview](interview.md) (면접 안내 대상·변수) · [job-posting](job-posting.md) (마감 임박 대상) · [privacy-audit](privacy-audit.md) (수신자 파기, S4) · [role-menu](role-menu.md) (메뉴 등록)

## 요약

- 관리자가 지원자에게 메일·SMS를 보낸다. 종류 5개를 고르면 대상자와 문구가 자동으로 채워지고, 수신자별 미리보기 → 담당자 테스트 발송 → 실제 발송(비동기) 순서로 진행한다.
- 설계서: `docs/superpowers/specs/2026-09-19-message-send-design.md`. 구현은 S1 템플릿 → S2 대상자·작성 → S3 발송 → S4 이력으로 나눈다. **현재 S1(템플릿)까지 구현.**
- 화면: `/admin/messages/templates`(`AdminMessageTemplates`). 발송(`/admin/messages`)·이력(`/admin/messages/history`)은 S2~S4.

## 용어

| 용어 | 코드 | 설명 |
|---|---|---|
| 메시지 종류 | `MessageType` | `RESULT_ANNOUNCEMENT` 결과 발표 · `DEADLINE_REMINDER` 서류 마감 임박 · `INTERVIEW_SCHEDULE` 면접 일정·장소 · `INTERVIEW_NOTICE` 면접 공지 · `FREE` 직접 입력 |
| 템플릿 | `MessageTemplate` | 종류 1개에 속한 메일 제목·본문 + SMS 본문 |
| 기본 템플릿 | `defaultTemplate` | 종류당 최대 1개. 발송 화면에서 종류를 고르면 자동으로 불러온다 |
| 변수 | `MessageVariable` | 본문의 `#{키}`. 키는 한글(`이름`, `면접일시` 등 12개). 종류별 허용 목록의 단일 출처 |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/MessageTemplateAdminController.java` | 템플릿 CRUD 5개 |
| controller | `{BE}/controller/MessageSendAdminController.java` | 변수 카탈로그(S2·S3에서 대상자·테스트·발송 추가) |
| service | `{BE}/service/MessageTemplateService.java` | 검증·기본 단일화·CRUD·변수 목록 |
| service | `{BE}/service/MessageRenderer.java` | `#{키}` 추출·종류별 허용 검사(프론트 `messageRender.ts`와 같은 규칙) |
| entity | `{BE}/domain/entity/MessageTemplate.java` | 템플릿. 컬럼 `message_type` |
| repository | `{BE}/domain/repository/MessageTemplateRepository.java` | 종류별·기본 조회 |
| dto | `{BE}/dto/request/MessageTemplateSaveRequest.java` | 등록·수정 공용 |
| dto | `{BE}/dto/response/MessageTemplateResponse.java` | 템플릿 응답 |
| dto | `{BE}/dto/response/MessageVariableResponse.java` | 변수 카탈로그 응답 |
| enum | `{BE}/enumeration/MessageType.java` | 종류 5개 |
| enum | `{BE}/enumeration/MessageVariable.java` | 변수 12개·허용 종류 |
| exception | `{BE}/exception/InvalidMessageException.java` | 400 |
| exception | `{BE}/exception/MessageTemplateNotFoundException.java` | 404 |
| test | `{BT}/service/MessageRendererTest.java` | 변수 검사 |
| test | `{BT}/service/MessageTemplateServiceTest.java` | 서비스 |
| test | `{BT}/controller/MessageTemplateAdminControllerTest.java` | API |

### 프론트

| 종류 | 파일 | 역할 |
|---|---|---|
| route | `{FE}/routes/adminRoutes.ts` | `AdminMessageTemplates`(`/admin/messages/templates`) — 공유 파일 |
| api | `{FE}/api/admin/messageApi.ts` | 템플릿·변수 API |
| type | `{FE}/types/admin/message.ts` | 타입 |
| view | `{FE}/views/admin/message/AdminMessageTemplateView.vue` | 템플릿 관리 화면 |
| util | `{FE}/views/admin/message/messageTypes.ts` | 종류 표시 메타(묶음·이름·설명) |
| util | `{FE}/views/admin/message/messageRender.ts` | SMS byte·구분(서버와 같은 규칙) |
| test | `{FE}/views/admin/message/__tests__/messageRender.spec.ts` | Vitest |

## API 계약

| 상태 | 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|---|
| 🟢 | GET | /admin/messages/variables | 없음 | `List<MessageVariableResponse>` `{ key, label, types[] }` 12개 |
| 🟢 | GET | /admin/message-templates | query `type?` | `List<MessageTemplateResponse>` 종류 → 기본 우선 → 이름순 |
| 🟢 | GET | /admin/message-templates/{id} | 없음 | `MessageTemplateResponse` |
| 🟢 | POST | /admin/message-templates | `{ type, name, defaultTemplate, mailSubject?, mailBody?, smsBody? }` | `MessageTemplateResponse` |
| 🟢 | POST | /admin/message-templates/{id} | 위와 같음 | `MessageTemplateResponse` |
| 🟢 | POST | /admin/message-templates/{id}/delete | 없음 | `null` |
| 🟡 | GET | /admin/messages/targets | query `type, jobPostingId, stageId?, resultStatus?, interviewGroup?, applicationStatus?` | `MessageTargetResponse` (S2) |
| 🟡 | POST | /admin/messages/test | `MessageTestSendRequest` | `MessageTestSendResponse` (S3) |
| 🟡 | POST | /admin/messages/send | `MessageSendRequest` | `{ sendId, status, recipientCount, excludedCount }` (S3) |
| 🟡 | GET | /admin/messages/history | query `from?, to?, type?, jobPostingId?, test?, page, size` | `PageResponse<MessageSendSummaryResponse>` (S4) |
| 🟡 | GET | /admin/messages/history/{sendId} | 없음 | `MessageSendDetailResponse` (S4) |

권한: 모두 `/api/admin/**` 규칙(`ADMIN`, `RECRUIT_ADMIN`). `SecurityConfig` 변경 없음.

### 엔드포인트 상세

- `MessageTemplateResponse`: `{ id, type, name, defaultTemplate, mailSubject, mailBody, smsBody, updatedAt }`. 빈 채널은 null.
- 🟡 행의 요청·응답 필드는 설계서 9절을 따른다. 구현하면서 이 표를 🟢로 바꾼다.

## 규칙·불변식

- 메일은 제목·본문을 함께 채우거나 함께 비운다. 메일과 SMS 중 하나 이상 필요. 공백만 있는 값은 null로 저장한다. (`MessageTemplateService.validate`)
- 본문의 `#{키}`는 그 종류에 허용된 변수여야 한다. 없는 키·다른 종류의 변수는 400 "사용할 수 없는 변수: #{…}". (`MessageRenderer.validateVariables`)
- 기본으로 저장(등록·수정)하면 같은 종류의 다른 기본 템플릿을 해제한다. 종류를 바꾸며 기본으로 저장하면 새 종류 기준으로 해제한다.
- 템플릿 삭제는 행 삭제다(발송 이력은 S3부터 이름·원문을 복사해 둔다).
- SMS byte: 문자 코드 127 이하 1byte, 그 밖 2byte. 90 이하 SMS, 2000 이하 LMS. 서버·프론트가 같은 규칙.

## 변경 레시피

- **변수 추가**: `MessageVariable`에 상수 추가(키·설명·허용 종류) → `MessageTemplateServiceTest`의 변수 개수·`MessageTemplateAdminControllerTest` 개수 단언 갱신 → S2 이후라면 변수 값 계산(`MessageTargetService`)도 추가. 프론트는 카탈로그 API로 받으므로 수정 불필요.
- **종류 추가**: `MessageType` → `MessageVariable` 허용 종류 → 프론트 `types/admin/message.ts`의 `MessageType`·`messageTypes.ts`의 `MESSAGE_TYPES`.
- **메뉴**: 코드가 아니라 메뉴 관리 화면(`/admin/menus`)에서 등록한다. 그룹 "메시지" 아래 "메시지 템플릿"(`/admin/messages/templates`).

## 검증

```bash
# 백엔드 (recruit_back/recruit_backend/)
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.Message*" --tests "com.shinyoung.recruit.controller.MessageTemplateAdminControllerTest" --no-daemon
# 프론트 (recruit_front/)
npm run type-check
npx vitest run src/views/admin/message
```

## 함정·결정

- 엔티티 필드 `type`은 컬럼명을 `message_type`으로 지정했다(DB 예약어 충돌 회피).
- 변수 키를 한글로 둔 이유: 관리자가 본문에서 바로 읽을 수 있게. 서버 enum이 키의 단일 출처다.
- 운영 DB에 `message_template` 테이블 생성 SQL을 `recruit_back/recruit_backend/docs/ops/`에 둘지는 미결(설계서 17절 1번).
````

- [ ] **Step 2: 색인 갱신**

`docs/domains/_index.md`에 아래를 추가한다.

카드 목록 표 마지막 행(`client-event-log`) 뒤:

```markdown
| [message](message.md) | 메일·SMS 메시지 템플릿·발송·발송 이력 | 관리자 |
```

라우트 → 카드 표, `AdminHome` 행 뒤:

```markdown
| `AdminMessageTemplates` | `/admin/messages/templates` | message |
```

API 경로 접두 → 카드 표, `/client-events` 행 뒤:

```markdown
| `/admin/messages` · `/admin/message-templates` | message |
```

키워드 → 카드 표, 마지막 행 뒤:

```markdown
| 메일, SMS, 문자, LMS, 메시지 발송, 메시지 템플릿, 변수, 테스트 발송, 발송 이력 | message |
```

- [ ] **Step 3: 문서 점검**

Run(레포 루트): `node tools/check-docs.mjs`
Expected: `문서 점검 통과` (오류 0건. 기존 크기 경고는 무관)

- [ ] **Step 4: 커밋(사용자 승인 후)**

```bash
git add docs/domains/message.md docs/domains/_index.md
git commit -m "docs(message): 메시지 도메인 카드 추가"
```

---

### Task 8: S1 마무리 검증과 보고

- [ ] **Step 1: 변경 범위 테스트 일괄 실행**

Run(`recruit_back/recruit_backend/`): `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.Message*" --tests "com.shinyoung.recruit.controller.MessageTemplateAdminControllerTest" --tests "com.shinyoung.recruit.config.SecurityConfigTest" --no-daemon`
Expected: `BUILD SUCCESSFUL`

Run(`recruit_front/`): `npm run type-check` → 오류 없음, `npx vitest run src/views/admin/message` → 3 passed

Run(레포 루트): `node tools/check-docs.mjs` → 통과

- [ ] **Step 2: 보고(한국어)**

변경 파일, 테스트 결과(명령과 통과 수), 계약 변경(🟢 6개), 화면 수동 확인 여부, 남은 미결(운영 DDL·초기 템플릿·발신 정보)을 보고한다.

- [ ] **Step 3: slice 구현 리포트**

`design-report` 스킬로 `docs/archive/reports/message-send-s1_implementation.html`을 만든다(글로벌 리포트 정책: 여러 slice로 나눈 작업은 slice 구현마다 리포트). 사용자가 리포트를 최종 1회로 미루라고 하면 따른다.

- [ ] **Step 4: 다음 단계**

S2(대상자·작성) 구현 계획을 S1 코드 기준으로 작성한다: `docs/superpowers/plans/YYYY-MM-DD-message-send-s2-targets.md`.
