# 메시지 발송 S3 — 테스트 발송·실제 발송 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 발송 화면에서 인사팀 담당자에게 테스트 발송을 하고, 확인 모달을 거쳐 선택 수신자에게 메일·SMS를 실제로 발송(비동기)할 수 있게 한다(설계서 S3). 발송 연동은 로그만 남기는 목업 게이트웨이다.

**Architecture:** 백엔드에 발송 기록(`MessageSend`·`MessageRecipient`), 연락처 공용 규칙(`MessageContacts`), 게이트웨이 인터페이스와 로그 목업, 메일 HTML 레이아웃, 발송 단위 묶기(`DeliveryUnit`), 단위 전달(`MessageDeliveryService`), 결과 기록(`MessageDispatchRecorder`), 비동기 디스패처(`MessageDispatcher`, 커밋 후 이벤트), 발송 서비스(`MessageSendService`: 테스트 발송 동기·실제 발송 비동기)와 `POST /admin/messages/test`·`/send`를 추가한다. 프론트에 테스트 발송 카드, 하단 발송 바, 발송 확인 모달, 허용되지 않은 변수 경고를 붙인다.

**Tech Stack:** Spring Boot 4 · Java 17 · JPA · Spring `@Async` + `@TransactionalEventListener` · Thymeleaf · JUnit5/Mockito/MockMvc · Vue 3 · TypeScript · ant-design-vue 4 · Vitest

**근거:** 설계서 `docs/superpowers/specs/2026-09-19-message-send-design.md` 3.1(5·6·8·9)·6·7·8·9·12·13절, 도메인 카드 `docs/domains/message.md`, 목업 `design/메시지-발송.html`.

**이 계획에서 확정한 결정(설계서 반영 완료):**
- 수신자별 치환 결과는 **발송 요청 시점**에 계산해 이벤트로 디스패처에 넘긴다(메모리, DB 미저장). 디스패처는 조건을 다시 검증하지 않는다. 그래서 S2 최종 리뷰의 선행 과제(대상 해석 분리·조건 구조화 저장)는 필요 없다. 서버 재시작 시 남은 `PENDING`은 그대로 남는다(자동 재개는 범위 밖).
- 비동기 실행기는 스프링 부트 기본 `applicationTaskExecutor`를 쓴다(`@EnableAsync`만 추가). 별도 `Executor` 빈을 만들면 부트 기본 실행기가 빠져 스트리밍 응답 등에 영향이 있다.
- 결과 기록(`MessageDispatchRecorder`)은 기본 전파(`REQUIRED`)다. 비동기 스레드에서는 호출마다 새 트랜잭션, 테스트 발송(동기)에서는 요청 트랜잭션에 합류한다.
- 발송 단위 = 채널별로 내용(제목+본문 / 본문+SMS 구분)이 완전히 같은 수신자 묶음, 최대 10명. 단위 결과는 그 단위 수신자 전원에 적용한다.
- SMS 2,000byte 초과 오류 메시지에는 이름 대신 수험번호를 넣는다(개인정보 원문 금지).
- 최대 발송 인원은 설정 `recruit.message.max-recipients`(기본 3,000). 게이트웨이 선택은 `recruit.message.gateway`(기본 `logging`).

**작업 규칙:** 커밋·브랜치 조작 금지(사용자 지시: main에서 커밋 없이). 줄바꿈 LF 유지. 백엔드 테스트는 수정한 클래스만. `<로컬 예시 키>` = `recruit_back/recruit_backend/AGENTS.md` 8절 값. 백엔드 명령은 `recruit_back/recruit_backend/`, 프론트는 `recruit_front/`, 문서 점검은 레포 루트.

---

## 파일 구조

`{BE}` = `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit`, `{BT}` = `.../src/test/java/com/shinyoung/recruit`, `{BR}` = `.../src/main/resources`, `{FE}` = `recruit_front/src`.

| 구분 | 파일 | 책임 |
|---|---|---|
| 수정 | `{BE}/config/MessageProperties.java`, `{BR}/application.yaml` | `max-recipients`, `gateway` 추가 |
| 생성 | `{BE}/enumeration/MessageChannel.java` | `MAIL`·`SMS` |
| 생성 | `{BE}/enumeration/MessageSendStatus.java` | `SENDING`·`COMPLETED` |
| 생성 | `{BE}/enumeration/MessageDeliveryStatus.java` | `PENDING`·`SENT`·`FAILED`·`SKIPPED` |
| 생성 | `{BE}/service/MessageContacts.java` | 연락처 유효성·정규화·마스킹·제외 사유(공용) |
| 생성 | `{BT}/service/MessageContactsTest.java` | 단위 테스트 |
| 수정 | `{BE}/service/MessageTargetService.java` | 연락처 판정을 `MessageContacts`로 |
| 생성 | `{BE}/domain/entity/MessageSend.java` | 발송 1회 |
| 생성 | `{BE}/domain/entity/MessageRecipient.java` | 수신자 1명(연락처 AES) |
| 생성 | `{BE}/domain/repository/MessageSendRepository.java` | |
| 생성 | `{BE}/domain/repository/MessageRecipientRepository.java` | 채널별 상태 집계 |
| 생성 | `{BE}/service/MailGateway.java` · `SmsGateway.java` · `GatewayResult.java` · `MailMessage.java` · `SmsMessage.java` | 연동 인터페이스·값 |
| 생성 | `{BE}/service/LoggingMailGateway.java` · `LoggingSmsGateway.java` | 목업(항상 성공, 마스킹 로그) |
| 생성 | `{BE}/service/MessageMailLayout.java`, `{BR}/templates/message-mail.html` | 메일 HTML 레이아웃 |
| 생성 | `{BE}/service/DeliveryItem.java` · `DeliveryUnit.java` | 수신자·채널별 전달 항목과 묶기 |
| 생성 | `{BE}/service/MessageDeliveryService.java` | 단위 1개를 게이트웨이로 전달 |
| 생성 | `{BE}/service/MessageDispatchRecorder.java` | 수신자 결과 기록·발송 집계 |
| 생성 | `{BE}/service/MessageSendRequestedEvent.java` · `MessageDispatcher.java` | 커밋 후 비동기 디스패치 |
| 생성 | `{BE}/config/AsyncConfig.java` | `@EnableAsync` |
| 생성 | `{BE}/dto/request/MessageContentRequest.java` · `MessageSendRequest.java` · `MessageTestSendRequest.java` · `MessageTesterRequest.java` | 요청 |
| 생성 | `{BE}/dto/response/MessageSendResultResponse.java` · `MessageTestSendResponse.java` · `MessageTestSendResultResponse.java` | 응답 |
| 생성 | `{BE}/service/MessageSendService.java` | 테스트 발송·실제 발송 |
| 수정 | `{BE}/controller/MessageSendAdminController.java` | `POST /test`, `POST /send` |
| 생성 | 테스트: `DeliveryUnitTest`, `MessageMailLayoutTest`, `MessageDeliveryServiceTest`, `MessageDispatcherTest`, `MessageDispatchRecorderTest`, `MessageSendServiceTest` (`{BT}/service/`), 컨트롤러 테스트 추가 | |
| 수정 | `{BT}/config/SecurityConfigTest.java` | `/send` 401·403 |
| 수정 | `{FE}/types/admin/message.ts`, `{FE}/api/admin/messageApi.ts` | 발송 타입·API |
| 수정 | `{FE}/views/admin/message/messageRender.ts` + spec | `extractVariableKeys` |
| 수정 | `{FE}/views/admin/message/MessageComposer.vue` | 허용되지 않은 변수 경고 |
| 생성 | `{FE}/views/admin/message/messageSendSummary.ts` + spec | 발송 요약 계산(건수·누락 변수·발송 가능 여부) |
| 생성 | `{FE}/views/admin/message/MessageTestSendCard.vue` | 테스트 발송 |
| 생성 | `{FE}/views/admin/message/MessageSendBar.vue` | 하단 발송 바 |
| 생성 | `{FE}/views/admin/message/MessageSendConfirmModal.vue` | 발송 확인 |
| 수정 | `{FE}/views/admin/message/AdminMessageSendView.vue` | 조립 |
| 수정 | `docs/domains/message.md`, `docs/domains/_index.md` | 카드·색인 |

---

### Task 1: 설정·enum·연락처 공용 규칙

**Files:**
- Modify: `{BE}/config/MessageProperties.java`, `{BR}/application.yaml`
- Create: `{BE}/enumeration/MessageChannel.java`, `{BE}/enumeration/MessageSendStatus.java`, `{BE}/enumeration/MessageDeliveryStatus.java`
- Create: `{BE}/service/MessageContacts.java`
- Test: `{BT}/service/MessageContactsTest.java`
- Modify: `{BE}/service/MessageTargetService.java` (정규식 상수 2개 제거, `MessageContacts` 사용)

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/service/MessageContactsTest.java`:

```java
package com.shinyoung.recruit.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageContactsTest {

    @Test
    void 휴대폰은_숫자만_남겨_01로_시작하는_10에서_11자리면_유효하다() {
        assertThat(MessageContacts.isValidPhone("010-1234-5678")).isTrue();
        assertThat(MessageContacts.isValidPhone("0111234567")).isTrue();
        assertThat(MessageContacts.isValidPhone("010-12")).isFalse();
        assertThat(MessageContacts.isValidPhone("02-1234-5678")).isFalse();
        assertThat(MessageContacts.isValidPhone(null)).isFalse();
        assertThat(MessageContacts.normalizePhone(" 010-1234-5678 ")).isEqualTo("01012345678");
    }

    @Test
    void 이메일은_앞뒤_공백을_빼고_x_at_y_dot_z_형식이면_유효하다() {
        assertThat(MessageContacts.isValidEmail(" kim@example.com ")).isTrue();
        assertThat(MessageContacts.isValidEmail("not-an-email")).isFalse();
        assertThat(MessageContacts.isValidEmail("a@b")).isFalse();
        assertThat(MessageContacts.isValidEmail(null)).isFalse();
        assertThat(MessageContacts.normalizeEmail(" kim@example.com ")).isEqualTo("kim@example.com");
    }

    @Test
    void 제외_사유는_값이_없으면_NO_CONTACT_형식이_틀리면_INVALID_CONTACT() {
        assertThat(MessageContacts.skipReason(null)).isEqualTo(MessageContacts.NO_CONTACT);
        assertThat(MessageContacts.skipReason("  ")).isEqualTo(MessageContacts.NO_CONTACT);
        assertThat(MessageContacts.skipReason("010-12")).isEqualTo(MessageContacts.INVALID_CONTACT);
    }

    @Test
    void 로그용_마스킹은_앞뒤만_남긴다() {
        assertThat(MessageContacts.maskEmail("minjun.kim@example.com")).isEqualTo("m***@example.com");
        assertThat(MessageContacts.maskPhone("010-2481-3307")).isEqualTo("010-****-3307");
        assertThat(MessageContacts.maskPhone("0112345678")).isEqualTo("011-****-5678");
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageContactsTest" --no-daemon`
Expected: FAIL — `MessageContacts` 없음.

- [ ] **Step 3: 구현**

`{BE}/service/MessageContacts.java`:

```java
package com.shinyoung.recruit.service;

import java.util.regex.Pattern;

/**
 * 메시지 수신 연락처 규칙(설계서 4절). 대상 조회·발송·로그 마스킹이 같은 규칙을 쓴다.
 * 휴대폰은 숫자만 남겨 01로 시작하는 10~11자리, 이메일은 앞뒤 공백을 뺀 x@y.z 형식이어야 유효하다.
 */
public final class MessageContacts {

    /** 연락처 값이 없음. */
    public static final String NO_CONTACT = "NO_CONTACT";
    /** 연락처가 있지만 형식이 맞지 않음. */
    public static final String INVALID_CONTACT = "INVALID_CONTACT";
    /** 이번 발송에서 끈 채널. */
    public static final String CHANNEL_OFF = "CHANNEL_OFF";
    /** 게이트웨이 호출 중 예외. */
    public static final String GATEWAY_ERROR = "GATEWAY_ERROR";

    private static final Pattern MOBILE_PHONE = Pattern.compile("01\\d{8,9}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private MessageContacts() {
    }

    public static String normalizePhone(String phone) {
        return phone == null ? null : phone.replaceAll("\\D", "");
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim();
    }

    public static boolean isValidPhone(String phone) {
        return phone != null && MOBILE_PHONE.matcher(normalizePhone(phone)).matches();
    }

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL.matcher(normalizeEmail(email)).matches();
    }

    /** 채널을 쓸 수 없을 때의 제외 사유. 값이 비었으면 NO_CONTACT, 있으면 INVALID_CONTACT. */
    public static String skipReason(String value) {
        return value == null || value.isBlank() ? NO_CONTACT : INVALID_CONTACT;
    }

    /** 로그용. 첫 글자와 도메인만 남긴다. */
    public static String maskEmail(String email) {
        String normalized = normalizeEmail(email);
        int at = normalized == null ? -1 : normalized.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return normalized.charAt(0) + "***" + normalized.substring(at);
    }

    /** 로그용. 앞 3자리와 뒤 4자리만 남긴다. */
    public static String maskPhone(String phone) {
        String digits = normalizePhone(phone);
        if (digits == null || digits.length() < 7) {
            return "***";
        }
        return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
    }
}
```

`{BE}/enumeration/MessageChannel.java`:

```java
package com.shinyoung.recruit.enumeration;

/** 메시지 채널. */
public enum MessageChannel {
    MAIL,
    SMS
}
```

`{BE}/enumeration/MessageSendStatus.java`:

```java
package com.shinyoung.recruit.enumeration;

/** 발송 1회의 상태. 디스패처가 모든 발송 단위를 처리하면 COMPLETED. */
public enum MessageSendStatus {
    SENDING,
    COMPLETED
}
```

`{BE}/enumeration/MessageDeliveryStatus.java`:

```java
package com.shinyoung.recruit.enumeration;

/** 수신자·채널별 결과. SKIPPED 는 연락처 없음·형식 오류·채널 끔으로 보내지 않은 것. */
public enum MessageDeliveryStatus {
    PENDING,
    SENT,
    FAILED,
    SKIPPED
}
```

`{BE}/config/MessageProperties.java` — `siteUrl` 필드 아래에 필드 2개와 getter/setter를 추가한다(`import jakarta.validation.constraints.Min;` 추가):

```java
    /** 한 번 발송의 최대 수신자 수. */
    @Min(1)
    private int maxRecipients = 3000;

    /** 발송 연동 구현 선택. 기본 logging(로그만 남기는 목업). */
    @NotBlank
    private String gateway = "logging";

    public int getMaxRecipients() {
        return maxRecipients;
    }

    public void setMaxRecipients(int maxRecipients) {
        this.maxRecipients = maxRecipients;
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }
```

`{BR}/application.yaml` — `recruit.message` 블록 마지막 줄(`site-url: ...`) 바로 뒤에 추가:

```yaml
    max-recipients: ${RECRUIT_MESSAGE_MAX_RECIPIENTS:3000}
    gateway: ${RECRUIT_MESSAGE_GATEWAY:logging}
```

`{BE}/service/MessageTargetService.java`:
1. `MOBILE_PHONE`·`EMAIL` 상수 2개와 `import java.util.regex.Pattern;`을 삭제한다(다른 곳에서 `Pattern`을 쓰지 않는지 확인).
2. `toRecipient`의 availability 두 줄을 아래로 바꾼다:

```java
                MessageContacts.isValidEmail(email),
                MessageContacts.isValidPhone(phone),
```

- [ ] **Step 4: 통과 확인(회귀 포함)**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageContactsTest" --tests "com.shinyoung.recruit.service.MessageTargetServiceTest" --tests "com.shinyoung.recruit.config.ApplicationYamlTest" --no-daemon`
Expected: PASS (4 + 17 + 3)

---

### Task 2: 발송 기록 엔티티와 리포지토리

**Files:**
- Create: `{BE}/domain/entity/MessageSend.java`, `{BE}/domain/entity/MessageRecipient.java`
- Create: `{BE}/domain/repository/MessageSendRepository.java`, `{BE}/domain/repository/MessageRecipientRepository.java`

설계서 8절. 두 엔티티 모두 `BaseEntity` 상속, cascade 없음. 수신자 이름·이메일·휴대폰은 `AesAttributeConverter`로 암호화(`ApplicationBasicInfo`와 같은 방식, 컬럼 길이 500). 엔티티 동작은 Task 4·5 테스트로 검증한다.

- [ ] **Step 1: `MessageSend` 작성**

`{BE}/domain/entity/MessageSend.java`:

```java
package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.MessageSendStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 메시지 발송 요청 1회. 치환 전 원문·조건 요약·발송자·집계를 보관한다(설계서 8절). */
@Entity
@Getter
@Table(
        name = "message_send",
        indexes = {
                @Index(name = "idx_message_send_requested_at", columnList = "requested_at"),
                @Index(name = "idx_message_send_posting_requested_at", columnList = "job_posting_id, requested_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageSend extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 40)
    private MessageType type;

    @Column(name = "test_send", nullable = false)
    private boolean test;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id")
    private Stage stage;

    @Column(length = 200)
    private String conditionSummary;

    private Long templateId;

    @Column(length = 100)
    private String templateName;

    @Column(nullable = false)
    private boolean mailEnabled;

    @Column(nullable = false)
    private boolean smsEnabled;

    @Column(length = 200)
    private String mailSubject;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String mailBody;

    @Column(length = 2000)
    private String smsBody;

    @Column(nullable = false, length = 100)
    private String senderLoginId;

    @Column(length = 100)
    private String senderName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageSendStatus status;

    @Column(nullable = false)
    private int recipientCount;

    @Column(nullable = false)
    private int mailSent;

    @Column(nullable = false)
    private int mailFailed;

    @Column(nullable = false)
    private int mailSkipped;

    @Column(nullable = false)
    private int smsSent;

    @Column(nullable = false)
    private int smsFailed;

    @Column(nullable = false)
    private int smsSkipped;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime completedAt;

    public static MessageSend create(MessageType type, boolean test, JobPosting jobPosting, Stage stage,
                                     String conditionSummary, Long templateId, String templateName,
                                     boolean mailEnabled, boolean smsEnabled,
                                     String mailSubject, String mailBody, String smsBody,
                                     String senderLoginId, String senderName,
                                     int recipientCount, LocalDateTime requestedAt) {
        MessageSend send = new MessageSend();
        send.type = type;
        send.test = test;
        send.jobPosting = jobPosting;
        send.stage = stage;
        send.conditionSummary = conditionSummary;
        send.templateId = templateId;
        send.templateName = templateName;
        send.mailEnabled = mailEnabled;
        send.smsEnabled = smsEnabled;
        send.mailSubject = mailSubject;
        send.mailBody = mailBody;
        send.smsBody = smsBody;
        send.senderLoginId = senderLoginId;
        send.senderName = senderName;
        send.status = MessageSendStatus.SENDING;
        send.recipientCount = recipientCount;
        send.requestedAt = requestedAt;
        return send;
    }

    /** 모든 발송 단위를 처리한 뒤 채널별 집계를 확정한다. */
    public void complete(int mailSent, int mailFailed, int mailSkipped,
                         int smsSent, int smsFailed, int smsSkipped, LocalDateTime completedAt) {
        this.mailSent = mailSent;
        this.mailFailed = mailFailed;
        this.mailSkipped = mailSkipped;
        this.smsSent = smsSent;
        this.smsFailed = smsFailed;
        this.smsSkipped = smsSkipped;
        this.status = MessageSendStatus.COMPLETED;
        this.completedAt = completedAt;
    }
}
```

- [ ] **Step 2: `MessageRecipient` 작성**

`{BE}/domain/entity/MessageRecipient.java`:

```java
package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.common.crypto.AesAttributeConverter;
import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.SmsKind;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 발송 1회의 수신자 1명과 채널별 결과. 이름·연락처는 발송 시점 값을 AES 로 암호화해 보관한다.
 * 테스트 발송 수신자(인사팀 담당자)는 jobApplication 이 null 이다.
 */
@Entity
@Getter
@Table(
        name = "message_recipient",
        indexes = {
                @Index(name = "idx_message_recipient_send", columnList = "message_send_id"),
                @Index(name = "idx_message_recipient_application", columnList = "job_application_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageRecipient extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_send_id", nullable = false)
    private MessageSend messageSend;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id")
    private JobApplication jobApplication;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String recipientName;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String email;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageDeliveryStatus mailStatus;

    @Column(length = 200)
    private String mailFailureReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageDeliveryStatus smsStatus;

    @Column(length = 200)
    private String smsFailureReason;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private SmsKind smsKind;

    private LocalDateTime processedAt;

    public static MessageRecipient create(MessageSend messageSend, JobApplication jobApplication,
                                          String recipientName, String email, String phone,
                                          MessageDeliveryStatus mailStatus, String mailFailureReason,
                                          MessageDeliveryStatus smsStatus, String smsFailureReason,
                                          SmsKind smsKind) {
        MessageRecipient recipient = new MessageRecipient();
        recipient.messageSend = messageSend;
        recipient.jobApplication = jobApplication;
        recipient.recipientName = recipientName;
        recipient.email = email;
        recipient.phone = phone;
        recipient.mailStatus = mailStatus;
        recipient.mailFailureReason = mailFailureReason;
        recipient.smsStatus = smsStatus;
        recipient.smsFailureReason = smsFailureReason;
        recipient.smsKind = smsKind;
        return recipient;
    }

    /** 게이트웨이 호출 결과를 채널에 기록한다. */
    public void recordResult(MessageChannel channel, MessageDeliveryStatus status, String failureReason,
                             LocalDateTime processedAt) {
        if (channel == MessageChannel.MAIL) {
            this.mailStatus = status;
            this.mailFailureReason = failureReason;
        } else {
            this.smsStatus = status;
            this.smsFailureReason = failureReason;
        }
        this.processedAt = processedAt;
    }
}
```

- [ ] **Step 3: 리포지토리 작성**

`{BE}/domain/repository/MessageSendRepository.java`:

```java
package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.MessageSend;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageSendRepository extends JpaRepository<MessageSend, Long> {
}
```

`{BE}/domain/repository/MessageRecipientRepository.java`:

```java
package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRecipientRepository extends JpaRepository<MessageRecipient, Long> {

    List<MessageRecipient> findByMessageSendIdOrderByIdAsc(Long messageSendId);

    long countByMessageSendIdAndMailStatus(Long messageSendId, MessageDeliveryStatus mailStatus);

    long countByMessageSendIdAndSmsStatus(Long messageSendId, MessageDeliveryStatus smsStatus);
}
```

- [ ] **Step 4: 컴파일·기동 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageTemplateServiceTest" --no-daemon`
Expected: PASS(10). 새 엔티티가 H2(`MODE=MySQL`)에서 테이블 생성에 성공해야 컨텍스트가 뜬다. 예약어·길이 오류로 실패하면 멈추고 보고한다.

---

### Task 3: 게이트웨이·메일 레이아웃·발송 단위·전달

**Files:**
- Create: `{BE}/service/GatewayResult.java`, `MailMessage.java`, `SmsMessage.java`, `MailGateway.java`, `SmsGateway.java`, `LoggingMailGateway.java`, `LoggingSmsGateway.java`
- Create: `{BE}/service/MessageMailLayout.java`, `{BR}/templates/message-mail.html`
- Create: `{BE}/service/DeliveryItem.java`, `{BE}/service/DeliveryUnit.java`
- Create: `{BE}/service/MessageDeliveryService.java`
- Test: `{BT}/service/DeliveryUnitTest.java`, `{BT}/service/MessageMailLayoutTest.java`, `{BT}/service/MessageDeliveryServiceTest.java`

규칙(설계서 7.1.1·7.3): 게이트웨이 1회 호출 = 내용 1개 + 수신자 1~10명. 묶기는 호출하는 쪽(`DeliveryUnit.group`) 책임이고 메일 단위를 먼저 둔다. 목업 게이트웨이는 항상 성공하고, 로그에는 마스킹한 수신자와 길이만 남긴다(본문 금지). 게이트웨이 예외는 그 단위만 `GATEWAY_ERROR` 실패로 바꾸고 예외 메시지는 로그에 남기지 않는다(주소가 섞일 수 있음).

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/service/DeliveryUnitTest.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.SmsKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryUnitTest {

    @Test
    void 같은_내용은_10명씩_묶는다() {
        List<DeliveryItem> items = new ArrayList<>();
        for (long id = 1; id <= 11; id++) {
            items.add(mail(id, "공지", "같은 본문"));
        }

        List<DeliveryUnit> units = DeliveryUnit.group(items);

        assertThat(units).hasSize(2);
        assertThat(units.get(0).recipientIds()).hasSize(10);
        assertThat(units.get(1).recipientIds()).containsExactly(11L);
        assertThat(units.get(0).to()).hasSize(10).allMatch(address -> address.endsWith("@example.com"));
    }

    @Test
    void 내용이_다르면_1명씩_보낸다() {
        List<DeliveryUnit> units = DeliveryUnit.group(List.of(
                mail(1L, "공지", "김민준님 안녕하세요"),
                mail(2L, "공지", "이서연님 안녕하세요")
        ));

        assertThat(units).extracting(DeliveryUnit::recipientIds)
                .containsExactly(List.of(1L), List.of(2L));
    }

    @Test
    void 메일_단위를_SMS_단위보다_먼저_둔다() {
        List<DeliveryUnit> units = DeliveryUnit.group(List.of(
                new DeliveryItem(1L, MessageChannel.SMS, "01000000001", null, "문자", SmsKind.SMS),
                mail(1L, "공지", "본문")
        ));

        assertThat(units).extracting(DeliveryUnit::channel)
                .containsExactly(MessageChannel.MAIL, MessageChannel.SMS);
    }

    private DeliveryItem mail(Long recipientId, String subject, String body) {
        return new DeliveryItem(recipientId, MessageChannel.MAIL, "user" + recipientId + "@example.com", subject, body, null);
    }
}
```

`{BT}/service/MessageMailLayoutTest.java`:

```java
package com.shinyoung.recruit.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
class MessageMailLayoutTest {

    @Autowired
    private MessageMailLayout messageMailLayout;

    @Test
    void 본문을_이스케이프하고_줄바꿈을_br로_바꿔_고정_레이아웃에_넣는다() {
        String html = messageMailLayout.render("[신영증권] 안내", "김민준님 <b>안녕</b>\n둘째 줄");

        assertThat(html).contains("신영증권 채용");
        assertThat(html).contains("김민준님 &lt;b&gt;안녕&lt;/b&gt;");
        assertThat(html).contains("둘째 줄");
        assertThat(html).contains("<br");
        assertThat(html).contains("본 메일은 발신 전용입니다.");
        assertThat(html).doesNotContain("<b>안녕</b>");
    }
}
```

`{BT}/service/MessageDeliveryServiceTest.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.SmsKind;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageDeliveryServiceTest {

    private final MailGateway mailGateway = mock(MailGateway.class);
    private final SmsGateway smsGateway = mock(SmsGateway.class);
    private final MessageMailLayout mailLayout = mock(MessageMailLayout.class);
    private final MessageDeliveryService service =
            new MessageDeliveryService(mailGateway, smsGateway, mailLayout, new MessageProperties());

    @Test
    void 메일은_레이아웃_HTML과_발신정보로_한번에_보낸다() {
        when(mailLayout.render("제목", "본문")).thenReturn("<html>본문</html>");
        when(mailGateway.send(any(), anyList())).thenReturn(GatewayResult.ok());
        DeliveryUnit unit = new DeliveryUnit(MessageChannel.MAIL, "제목", "본문", null,
                List.of(1L, 2L), List.of("a@example.com", "b@example.com"));

        GatewayResult result = service.deliver(unit);

        assertThat(result.success()).isTrue();
        ArgumentCaptor<MailMessage> captor = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailGateway).send(captor.capture(), org.mockito.ArgumentMatchers.eq(List.of("a@example.com", "b@example.com")));
        assertThat(captor.getValue().fromName()).isEqualTo("신영증권 채용담당");
        assertThat(captor.getValue().fromAddress()).isEqualTo("recruit@example.co.kr");
        assertThat(captor.getValue().html()).isEqualTo("<html>본문</html>");
        assertThat(captor.getValue().text()).isEqualTo("본문");
    }

    @Test
    void SMS는_발신번호와_구분을_넣어_보낸다() {
        when(smsGateway.send(any(), anyList())).thenReturn(GatewayResult.ok());
        DeliveryUnit unit = new DeliveryUnit(MessageChannel.SMS, null, "문자", SmsKind.LMS,
                List.of(1L), List.of("01012345678"));

        service.deliver(unit);

        ArgumentCaptor<SmsMessage> captor = ArgumentCaptor.forClass(SmsMessage.class);
        verify(smsGateway).send(captor.capture(), org.mockito.ArgumentMatchers.eq(List.of("01012345678")));
        assertThat(captor.getValue().callbackNumber()).isEqualTo("02-0000-0000");
        assertThat(captor.getValue().kind()).isEqualTo(SmsKind.LMS);
    }

    @Test
    void 게이트웨이_예외는_그_단위만_GATEWAY_ERROR_실패로_바꾼다() {
        when(smsGateway.send(any(), anyList())).thenThrow(new IllegalStateException("smtp down 010-1234-5678"));
        DeliveryUnit unit = new DeliveryUnit(MessageChannel.SMS, null, "문자", SmsKind.SMS,
                List.of(1L), List.of("01012345678"));

        GatewayResult result = service.deliver(unit);

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).isEqualTo(MessageContacts.GATEWAY_ERROR);
    }

    @Test
    void 게이트웨이가_null을_돌려주면_실패로_본다() {
        when(mailLayout.render(any(), any())).thenReturn("<html/>");
        when(mailGateway.send(any(), anyList())).thenReturn(null);
        DeliveryUnit unit = new DeliveryUnit(MessageChannel.MAIL, "제목", "본문", null,
                List.of(1L), List.of("a@example.com"));

        assertThat(service.deliver(unit).failureReason()).isEqualTo(MessageContacts.GATEWAY_ERROR);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.DeliveryUnitTest" --tests "com.shinyoung.recruit.service.MessageMailLayoutTest" --tests "com.shinyoung.recruit.service.MessageDeliveryServiceTest" --no-daemon`
Expected: FAIL — 클래스 없음(컴파일 오류).

- [ ] **Step 3: 게이트웨이 값 객체·인터페이스·목업 작성**

`{BE}/service/GatewayResult.java`:

```java
package com.shinyoung.recruit.service;

/** 게이트웨이 1회 호출 결과. 실패면 failureReason 에 사유 코드(200자 이내 권장). */
public record GatewayResult(boolean success, String failureReason) {

    public static GatewayResult ok() {
        return new GatewayResult(true, null);
    }

    public static GatewayResult failure(String failureReason) {
        return new GatewayResult(false, failureReason);
    }
}
```

`{BE}/service/MailMessage.java`:

```java
package com.shinyoung.recruit.service;

/** 메일 1통의 내용. html 은 고정 레이아웃을 입힌 본문, text 는 같은 내용의 일반 텍스트. */
public record MailMessage(String fromName, String fromAddress, String subject, String html, String text) {
}
```

`{BE}/service/SmsMessage.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.SmsKind;

/** 문자 1건의 내용. kind 는 치환 후 byte 로 판정한 SMS/LMS. */
public record SmsMessage(String callbackNumber, String body, SmsKind kind) {
}
```

`{BE}/service/MailGateway.java`:

```java
package com.shinyoung.recruit.service;

import java.util.List;

/**
 * 메일 발송 연동. 호출 1회 = 내용 1개 + 수신자 1~10명(발송 솔루션 트랜잭션 제약).
 * 수신자 묶기는 호출하는 쪽(DeliveryUnit)이 책임진다. 실제 SMTP 구현은 사내 스펙 확정 후 추가한다.
 */
public interface MailGateway {

    GatewayResult send(MailMessage message, List<String> toAddresses);
}
```

`{BE}/service/SmsGateway.java`:

```java
package com.shinyoung.recruit.service;

import java.util.List;

/** 문자 발송 연동. 호출 1회 = 내용 1개 + 수신자 1~10명. 번호는 숫자만 넘긴다. */
public interface SmsGateway {

    GatewayResult send(SmsMessage message, List<String> toNumbers);
}
```

`{BE}/service/LoggingMailGateway.java`:

```java
package com.shinyoung.recruit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** 목업 메일 게이트웨이. 항상 성공하고 마스킹한 수신자와 길이만 로그로 남긴다(본문 금지). */
@Component
@ConditionalOnProperty(prefix = "recruit.message", name = "gateway", havingValue = "logging", matchIfMissing = true)
public class LoggingMailGateway implements MailGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailGateway.class);

    @Override
    public GatewayResult send(MailMessage message, List<String> toAddresses) {
        log.info("[message-mail] to={} subjectLength={} htmlLength={}",
                toAddresses.stream().map(MessageContacts::maskEmail).toList(),
                message.subject().length(),
                message.html().length());
        return GatewayResult.ok();
    }
}
```

`{BE}/service/LoggingSmsGateway.java`:

```java
package com.shinyoung.recruit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** 목업 문자 게이트웨이. 항상 성공하고 마스킹한 번호·구분·길이만 로그로 남긴다(본문 금지). */
@Component
@ConditionalOnProperty(prefix = "recruit.message", name = "gateway", havingValue = "logging", matchIfMissing = true)
public class LoggingSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsGateway.class);

    @Override
    public GatewayResult send(SmsMessage message, List<String> toNumbers) {
        log.info("[message-sms] to={} kind={} bodyLength={}",
                toNumbers.stream().map(MessageContacts::maskPhone).toList(),
                message.kind(),
                message.body().length());
        return GatewayResult.ok();
    }
}
```

- [ ] **Step 4: 메일 레이아웃 작성**

`{BR}/templates/message-mail.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="ko">
<head>
    <meta charset="UTF-8"/>
    <title th:text="${subject}">제목</title>
</head>
<body style="margin:0;padding:0;background:#f5f7fa;font-family:'Malgun Gothic',Arial,sans-serif;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f5f7fa;padding:24px 0;">
    <tr>
        <td align="center">
            <table role="presentation" width="600" cellpadding="0" cellspacing="0"
                   style="background:#ffffff;border:1px solid #e5e7eb;">
                <tr>
                    <td style="background:#0f4726;color:#ffffff;padding:14px 20px;font-size:15px;font-weight:bold;">신영증권 채용</td>
                </tr>
                <tr>
                    <td style="padding:24px 20px;font-size:14px;line-height:1.75;color:#1f2937;">
                        <th:block th:each="line : ${lines}"><span th:text="${line}">본문</span><br/></th:block>
                    </td>
                </tr>
                <tr>
                    <td style="padding:12px 20px;background:#f9fafb;border-top:1px solid #e5e7eb;font-size:12px;color:#9ca3af;">본 메일은 발신 전용입니다.</td>
                </tr>
            </table>
        </td>
    </tr>
</table>
</body>
</html>
```

`{BE}/service/MessageMailLayout.java`:

```java
package com.shinyoung.recruit.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Locale;

/**
 * 치환이 끝난 일반 텍스트 본문을 고정 브랜드 레이아웃(templates/message-mail.html)에 넣는다.
 * 줄마다 th:text 로 이스케이프하고 줄바꿈은 br 로 바꾼다. 링크 자동 변환은 하지 않는다(설계서 6절).
 */
@Component
@RequiredArgsConstructor
public class MessageMailLayout {

    private static final String TEMPLATE_NAME = "message-mail";

    private final TemplateEngine templateEngine;

    public String render(String subject, String body) {
        Context context = new Context(Locale.KOREA);
        context.setVariable("subject", subject);
        context.setVariable("lines", List.of(body.split("\n", -1)));
        return templateEngine.process(TEMPLATE_NAME, context);
    }
}
```

- [ ] **Step 5: 발송 단위·전달 작성**

`{BE}/service/DeliveryItem.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.SmsKind;

/**
 * 수신자 1명·채널 1개의 치환 완료 내용. to 는 정규화한 이메일 또는 숫자만 남긴 번호.
 * 메일은 subject·body(일반 텍스트), SMS 는 body·smsKind 를 쓴다.
 */
public record DeliveryItem(
        Long recipientId,
        MessageChannel channel,
        String to,
        String subject,
        String body,
        SmsKind smsKind
) {
}
```

`{BE}/service/DeliveryUnit.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.SmsKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 게이트웨이 1회 호출 단위 = 채널별로 내용이 완전히 같은 수신자 최대 10명(설계서 7.1.1).
 * 변수로 사람마다 내용이 달라지면 1명당 1단위가 된다.
 */
public record DeliveryUnit(
        MessageChannel channel,
        String subject,
        String body,
        SmsKind smsKind,
        List<Long> recipientIds,
        List<String> to
) {

    public static final int MAX_RECIPIENTS = 10;

    /** 같은 내용끼리 묶어 10명씩 나눈다. 메일 단위를 먼저 두고, 같은 채널 안에서는 처음 나온 순서를 지킨다. */
    public static List<DeliveryUnit> group(List<DeliveryItem> items) {
        Map<ContentKey, List<DeliveryItem>> groups = new LinkedHashMap<>();
        for (DeliveryItem item : items) {
            ContentKey key = new ContentKey(item.channel(), item.subject(), item.body(), item.smsKind());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        List<DeliveryUnit> units = new ArrayList<>();
        groups.forEach((key, grouped) -> {
            for (int from = 0; from < grouped.size(); from += MAX_RECIPIENTS) {
                List<DeliveryItem> chunk = grouped.subList(from, Math.min(from + MAX_RECIPIENTS, grouped.size()));
                units.add(new DeliveryUnit(
                        key.channel(), key.subject(), key.body(), key.smsKind(),
                        chunk.stream().map(DeliveryItem::recipientId).toList(),
                        chunk.stream().map(DeliveryItem::to).toList()
                ));
            }
        });
        units.sort(Comparator.comparing(DeliveryUnit::channel));
        return units;
    }

    private record ContentKey(MessageChannel channel, String subject, String body, SmsKind smsKind) {
    }
}
```

`{BE}/service/MessageDeliveryService.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.enumeration.MessageChannel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 발송 단위 1개를 게이트웨이로 보낸다. DB 를 건드리지 않는다(결과 기록은 MessageDispatchRecorder). */
@Service
@RequiredArgsConstructor
public class MessageDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(MessageDeliveryService.class);

    private final MailGateway mailGateway;
    private final SmsGateway smsGateway;
    private final MessageMailLayout messageMailLayout;
    private final MessageProperties messageProperties;

    public GatewayResult deliver(DeliveryUnit unit) {
        try {
            GatewayResult result = unit.channel() == MessageChannel.MAIL ? sendMail(unit) : sendSms(unit);
            return result == null ? GatewayResult.failure(MessageContacts.GATEWAY_ERROR) : result;
        } catch (RuntimeException e) {
            // 예외 메시지에는 주소가 섞일 수 있어 남기지 않는다.
            log.warn("메시지 게이트웨이 호출 실패: channel={}, recipients={}, error={}",
                    unit.channel(), unit.recipientIds().size(), e.getClass().getSimpleName());
            return GatewayResult.failure(MessageContacts.GATEWAY_ERROR);
        }
    }

    private GatewayResult sendMail(DeliveryUnit unit) {
        String html = messageMailLayout.render(unit.subject(), unit.body());
        MailMessage message = new MailMessage(
                messageProperties.getSenderName(), messageProperties.getSenderEmail(), unit.subject(), html, unit.body());
        return mailGateway.send(message, unit.to());
    }

    private GatewayResult sendSms(DeliveryUnit unit) {
        SmsMessage message = new SmsMessage(messageProperties.getSmsCallbackNumber(), unit.body(), unit.smsKind());
        return smsGateway.send(message, unit.to());
    }
}
```

- [ ] **Step 6: 통과 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.DeliveryUnitTest" --tests "com.shinyoung.recruit.service.MessageMailLayoutTest" --tests "com.shinyoung.recruit.service.MessageDeliveryServiceTest" --no-daemon`
Expected: PASS (3 + 1 + 4)

---

### Task 4: 결과 기록·비동기 디스패처

**Files:**
- Create: `{BE}/exception/MessageSendNotFoundException.java` + `{BE}/exception/GlobalExceptionHandler.java` 핸들러(404)
- Create: `{BE}/service/MessageDispatchRecorder.java`
- Create: `{BE}/service/MessageSendRequestedEvent.java`, `{BE}/service/MessageDispatcher.java`
- Create: `{BE}/config/AsyncConfig.java`
- Test: `{BT}/service/MessageDispatchRecorderTest.java`, `{BT}/service/MessageDispatcherTest.java`

흐름: 발송 서비스가 트랜잭션 안에서 `MessageSendRequestedEvent`를 발행 → 커밋 뒤 `MessageDispatcher.onSendRequested`가 비동기로 `dispatch` → 단위마다 `deliver` 후 `recordUnit` → 끝에 `complete`. 테스트 발송은 `dispatch`를 요청 트랜잭션 안에서 동기로 부른다(`MessageDispatchRecorder`가 `REQUIRED`라 합류).

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/service/MessageDispatcherTest.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.SmsKind;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageDispatcherTest {

    private final MessageDeliveryService deliveryService = mock(MessageDeliveryService.class);
    private final MessageDispatchRecorder recorder = mock(MessageDispatchRecorder.class);
    private final MessageDispatcher dispatcher = new MessageDispatcher(deliveryService, recorder);

    @Test
    void 발송_단위마다_전달하고_결과를_기록한_뒤_집계를_확정한다() {
        List<DeliveryItem> items = new ArrayList<>();
        for (long id = 1; id <= 11; id++) {
            items.add(new DeliveryItem(id, MessageChannel.MAIL, "u" + id + "@example.com", "공지", "같은 본문", null));
        }
        items.add(new DeliveryItem(1L, MessageChannel.SMS, "01000000001", null, "문자", SmsKind.SMS));
        when(deliveryService.deliver(any())).thenReturn(GatewayResult.ok(), GatewayResult.failure("X"), GatewayResult.ok());

        dispatcher.dispatch(7L, items);

        ArgumentCaptor<DeliveryUnit> units = ArgumentCaptor.forClass(DeliveryUnit.class);
        verify(deliveryService, times(3)).deliver(units.capture());
        assertThat(units.getAllValues()).extracting(DeliveryUnit::channel)
                .containsExactly(MessageChannel.MAIL, MessageChannel.MAIL, MessageChannel.SMS);
        InOrder order = inOrder(recorder);
        order.verify(recorder).recordUnit(units.getAllValues().get(0), GatewayResult.ok());
        order.verify(recorder).recordUnit(units.getAllValues().get(1), GatewayResult.failure("X"));
        order.verify(recorder).recordUnit(units.getAllValues().get(2), GatewayResult.ok());
        order.verify(recorder).complete(eq(7L));
    }

    @Test
    void 이벤트를_받으면_같은_흐름으로_보낸다() {
        when(deliveryService.deliver(any())).thenReturn(GatewayResult.ok());

        dispatcher.onSendRequested(new MessageSendRequestedEvent(3L, List.of(
                new DeliveryItem(1L, MessageChannel.SMS, "01000000001", null, "문자", SmsKind.SMS))));

        verify(recorder).complete(3L);
    }
}
```

`{BT}/service/MessageDispatchRecorderTest.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.MessageSendStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.SmsKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class MessageDispatchRecorderTest {

    @Autowired
    private MessageDispatchRecorder recorder;
    @Autowired
    private JobPostingRepository jobPostingRepository;
    @Autowired
    private MessageSendRepository messageSendRepository;
    @Autowired
    private MessageRecipientRepository messageRecipientRepository;

    @Test
    void 단위_결과를_수신자에_기록하고_채널별로_집계한다() {
        MessageSend send = saveSend(3);
        MessageRecipient first = saveRecipient(send, MessageDeliveryStatus.PENDING, MessageDeliveryStatus.PENDING);
        MessageRecipient second = saveRecipient(send, MessageDeliveryStatus.PENDING, MessageDeliveryStatus.SKIPPED);
        MessageRecipient third = saveRecipient(send, MessageDeliveryStatus.SKIPPED, MessageDeliveryStatus.PENDING);

        recorder.recordUnit(unit(MessageChannel.MAIL, first.getId(), second.getId()), GatewayResult.failure("SMTP_REJECTED"));
        recorder.recordUnit(unit(MessageChannel.SMS, first.getId(), third.getId()), GatewayResult.ok());
        recorder.complete(send.getId());

        assertThat(first.getMailStatus()).isEqualTo(MessageDeliveryStatus.FAILED);
        assertThat(first.getMailFailureReason()).isEqualTo("SMTP_REJECTED");
        assertThat(first.getSmsStatus()).isEqualTo(MessageDeliveryStatus.SENT);
        assertThat(first.getProcessedAt()).isNotNull();
        MessageSend completed = messageSendRepository.findById(send.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(MessageSendStatus.COMPLETED);
        assertThat(completed.getMailFailed()).isEqualTo(2);
        assertThat(completed.getMailSkipped()).isEqualTo(1);
        assertThat(completed.getSmsSent()).isEqualTo(2);
        assertThat(completed.getSmsSkipped()).isEqualTo(1);
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    void 긴_실패_사유는_200자로_자른다() {
        MessageSend send = saveSend(1);
        MessageRecipient recipient = saveRecipient(send, MessageDeliveryStatus.PENDING, MessageDeliveryStatus.SKIPPED);

        recorder.recordUnit(unit(MessageChannel.MAIL, recipient.getId()), GatewayResult.failure("x".repeat(300)));

        assertThat(recipient.getMailFailureReason()).hasSize(200);
    }

    private DeliveryUnit unit(MessageChannel channel, Long... recipientIds) {
        return new DeliveryUnit(channel, null, "본문", channel == MessageChannel.SMS ? SmsKind.SMS : null,
                List.of(recipientIds), List.of());
    }

    private MessageSend saveSend(int recipientCount) {
        JobPosting posting = JobPosting.create("메시지 공고", "Content",
                LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 22, 18, 0));
        posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        jobPostingRepository.saveAndFlush(posting);
        return messageSendRepository.saveAndFlush(MessageSend.create(
                MessageType.FREE, false, posting, null, "제출 완료", null, null,
                true, true, "제목", "본문", "문자", "hr.kim", "김인사",
                recipientCount, LocalDateTime.of(2026, 9, 19, 10, 0)));
    }

    private MessageRecipient saveRecipient(MessageSend send, MessageDeliveryStatus mail, MessageDeliveryStatus sms) {
        return messageRecipientRepository.saveAndFlush(MessageRecipient.create(
                send, null, "김지원", "kim@example.com", "01000000000",
                mail, mail == MessageDeliveryStatus.SKIPPED ? MessageContacts.NO_CONTACT : null,
                sms, sms == MessageDeliveryStatus.SKIPPED ? MessageContacts.NO_CONTACT : null,
                SmsKind.SMS));
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageDispatcherTest" --tests "com.shinyoung.recruit.service.MessageDispatchRecorderTest" --no-daemon`
Expected: FAIL — 클래스 없음.

- [ ] **Step 3: 예외·기록·이벤트·디스패처·비동기 설정 작성**

`{BE}/exception/MessageSendNotFoundException.java`:

```java
package com.shinyoung.recruit.exception;

public class MessageSendNotFoundException extends RuntimeException {

    public MessageSendNotFoundException(String message) {
        super(message);
    }
}
```

`{BE}/exception/GlobalExceptionHandler.java` — `handleInvalidMessage` 메서드 바로 아래에 추가:

```java
    @ExceptionHandler(MessageSendNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageSendNotFound(MessageSendNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(e.getMessage()));
    }
```

`{BE}/service/MessageDispatchRecorder.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.exception.MessageSendNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 발송 단위 결과를 수신자에 기록하고 발송 집계를 확정한다. 기본 전파(REQUIRED):
 * 비동기 디스패처에서는 호출마다 새 트랜잭션, 테스트 발송(동기)에서는 요청 트랜잭션에 합류한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MessageDispatchRecorder {

    private static final int FAILURE_REASON_MAX_LENGTH = 200;

    private final MessageRecipientRepository messageRecipientRepository;
    private final MessageSendRepository messageSendRepository;
    private final Clock clock;

    /** 단위 결과를 그 단위 수신자 전원에 적용한다. */
    public void recordUnit(DeliveryUnit unit, GatewayResult result) {
        LocalDateTime now = LocalDateTime.now(clock);
        MessageDeliveryStatus status = result.success() ? MessageDeliveryStatus.SENT : MessageDeliveryStatus.FAILED;
        String reason = result.success() ? null : truncate(result.failureReason());
        messageRecipientRepository.findAllById(unit.recipientIds())
                .forEach(recipient -> recipient.recordResult(unit.channel(), status, reason, now));
    }

    /** 채널별 성공·실패·제외 수를 세어 발송을 COMPLETED 로 닫는다. */
    public void complete(Long messageSendId) {
        MessageSend send = messageSendRepository.findById(messageSendId)
                .orElseThrow(() -> new MessageSendNotFoundException("발송 기록을 찾을 수 없습니다."));
        send.complete(
                count(messageSendId, true, MessageDeliveryStatus.SENT),
                count(messageSendId, true, MessageDeliveryStatus.FAILED),
                count(messageSendId, true, MessageDeliveryStatus.SKIPPED),
                count(messageSendId, false, MessageDeliveryStatus.SENT),
                count(messageSendId, false, MessageDeliveryStatus.FAILED),
                count(messageSendId, false, MessageDeliveryStatus.SKIPPED),
                LocalDateTime.now(clock)
        );
    }

    private int count(Long messageSendId, boolean mail, MessageDeliveryStatus status) {
        long count = mail
                ? messageRecipientRepository.countByMessageSendIdAndMailStatus(messageSendId, status)
                : messageRecipientRepository.countByMessageSendIdAndSmsStatus(messageSendId, status);
        return Math.toIntExact(count);
    }

    private static String truncate(String reason) {
        if (reason == null || reason.length() <= FAILURE_REASON_MAX_LENGTH) {
            return reason;
        }
        return reason.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
```

`{BE}/service/MessageSendRequestedEvent.java`:

```java
package com.shinyoung.recruit.service;

import java.util.List;

/** 발송 요청 커밋 후 디스패처가 받는 이벤트. 치환 결과는 메모리로만 넘기고 DB 에 저장하지 않는다. */
public record MessageSendRequestedEvent(Long messageSendId, List<DeliveryItem> items) {
}
```

`{BE}/service/MessageDispatcher.java`:

```java
package com.shinyoung.recruit.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 발송 요청이 커밋되면 비동기로 발송 단위마다 게이트웨이를 호출하고 결과를 기록한다(설계서 7.1).
 * 중간에 예외로 끝나면 남은 PENDING 과 SENDING 상태는 그대로 남는다(자동 재개는 범위 밖).
 */
@Component
@RequiredArgsConstructor
public class MessageDispatcher {

    private final MessageDeliveryService messageDeliveryService;
    private final MessageDispatchRecorder messageDispatchRecorder;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSendRequested(MessageSendRequestedEvent event) {
        dispatch(event.messageSendId(), event.items());
    }

    /** 테스트 발송은 이 메서드를 요청 트랜잭션 안에서 동기로 부른다. */
    public void dispatch(Long messageSendId, List<DeliveryItem> items) {
        for (DeliveryUnit unit : DeliveryUnit.group(items)) {
            messageDispatchRecorder.recordUnit(unit, messageDeliveryService.deliver(unit));
        }
        messageDispatchRecorder.complete(messageSendId);
    }
}
```

`{BE}/config/AsyncConfig.java`:

```java
package com.shinyoung.recruit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 메시지 디스패처(@Async)를 켠다. 실행기는 스프링 부트 기본 applicationTaskExecutor 를 쓴다.
 * 별도 Executor 빈을 만들면 부트 기본 실행기가 빠져 스트리밍 응답 등에 영향이 있으니 만들지 않는다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
```

- [ ] **Step 4: 통과 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageDispatcherTest" --tests "com.shinyoung.recruit.service.MessageDispatchRecorderTest" --no-daemon`
Expected: PASS (2 + 2)

---

### Task 5: 발송 서비스 (테스트 발송·실제 발송)

**Files:**
- Create: `{BE}/dto/request/MessageContentRequest.java`, `MessageSendRequest.java`, `MessageTestSendRequest.java`, `MessageTesterRequest.java`
- Create: `{BE}/dto/response/MessageSendResultResponse.java`, `MessageTestSendResponse.java`, `MessageTestSendResultResponse.java`
- Modify: `{BE}/service/MessageVariableFormatter.java` (`groupLabel`을 패키지 공개 static으로)
- Create: `{BE}/service/MessageSendService.java`
- Test: `{BT}/service/MessageSendServiceTest.java`

규칙(설계서 7.1·7.2·13절, 이 계획의 결정):
- 공통 검증: 채널 1개 이상, 켠 메일은 제목·본문 필수, 켠 SMS는 본문 필수, 켠 채널 원문의 변수는 종류 허용 목록 안(`validateVariables`).
- 실제 발송: 조건으로 대상 재조회(`getTargets`, 선택 가능 검증 포함) → 요청 `applicationIds`와 교집합(대상 순서 유지), 빠진 수 = `excludedCount` → 0명이면 400, `max-recipients` 초과면 400 → 수신자별 치환 → 켠 SMS가 2,000byte를 넘는 수신자가 있으면 400(수험번호 최대 5개와 인원) → 보낼 수 있는 채널이 하나도 없으면 400 → `MessageSend(SENDING)` + `MessageRecipient` 저장 → 이벤트 발행 → `{ sendId, status, recipientCount, excludedCount }`.
- 채널 상태: 끈 채널 `SKIPPED`/`CHANNEL_OFF`, 연락처 불가 `SKIPPED`/`NO_CONTACT`·`INVALID_CONTACT`, 그 밖 `PENDING`. SMS 구분은 치환 후 byte로.
- 테스트 발송: 테스터 1~5명(각자 이메일·휴대폰 중 1개 이상, 입력한 것은 형식 유효) → 대상 재조회 후 `previewApplicationId`가 대상이어야 함 → 미리보기 대상의 변수로 치환하고 메일 제목과 SMS 본문 앞에 `[테스트] ` → `MessageSend(test=true)` + 테스터 수신자(`jobApplication` 없음) → 디스패처 `dispatch`를 동기로 호출 → 결과 `{ sendId, results[{ name, channel, status, failureReason }] }`(테스터마다 메일·SMS 2건).
- 원문 저장: 켠 채널의 치환 전 원문만 저장(끈 채널은 null). 조건 요약(`conditionSummary`)은 전형 이름 + 종류별 조건 라벨, 200자 이내. 템플릿 이름은 `templateId`로 조회해 복사(삭제된 템플릿이면 null).
- 오류 메시지에 지원자 이름·연락처를 넣지 않는다.

- [ ] **Step 1: DTO 작성**

`{BE}/dto/request/MessageContentRequest.java`:

```java
package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 발송 화면에서 작성한 내용(치환 전). 켠 채널의 필수 입력은 MessageSendService 가 검증한다. */
public record MessageContentRequest(
        Long templateId,
        @NotNull Boolean mailEnabled,
        @NotNull Boolean smsEnabled,
        @Size(max = 200) String mailSubject,
        @Size(max = 10000) String mailBody,
        @Size(max = 2000) String smsBody
) {
}
```

`{BE}/dto/request/MessageSendRequest.java`:

```java
package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.dto.condition.MessageTargetCondition;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 실제 발송. 조건 필드는 대상자 조회(GET /admin/messages/targets)와 같다. */
public record MessageSendRequest(
        @NotNull MessageType type,
        @NotNull Long jobPostingId,
        Long stageId,
        StageResultStatus resultStatus,
        String interviewGroup,
        JobApplicationStatus applicationStatus,
        @NotEmpty List<@NotNull Long> applicationIds,
        @NotNull @Valid MessageContentRequest content
) {
    public MessageTargetCondition toCondition() {
        return new MessageTargetCondition(type, jobPostingId, stageId, resultStatus, interviewGroup, applicationStatus);
    }
}
```

`{BE}/dto/request/MessageTesterRequest.java`:

```java
package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 테스트 발송 수신자(인사팀 담당자). 이메일·휴대폰 중 1개 이상. */
public record MessageTesterRequest(
        @NotBlank @Size(max = 50) String name,
        @Size(max = 200) String email,
        @Size(max = 30) String phone
) {
}
```

`{BE}/dto/request/MessageTestSendRequest.java`:

```java
package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.dto.condition.MessageTargetCondition;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 테스트 발송. previewApplicationId 의 변수 값으로 치환해 testers 에게 보낸다. */
public record MessageTestSendRequest(
        @NotNull MessageType type,
        @NotNull Long jobPostingId,
        Long stageId,
        StageResultStatus resultStatus,
        String interviewGroup,
        JobApplicationStatus applicationStatus,
        @NotNull Long previewApplicationId,
        @NotEmpty @Size(max = 5) List<@NotNull @Valid MessageTesterRequest> testers,
        @NotNull @Valid MessageContentRequest content
) {
    public MessageTargetCondition toCondition() {
        return new MessageTargetCondition(type, jobPostingId, stageId, resultStatus, interviewGroup, applicationStatus);
    }
}
```

`{BE}/dto/response/MessageSendResultResponse.java`:

```java
package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.MessageSendStatus;

/** 실제 발송 접수 결과. 발송은 비동기로 진행되며 excludedCount 는 요청했지만 조건에서 빠진 인원. */
public record MessageSendResultResponse(
        Long sendId,
        MessageSendStatus status,
        int recipientCount,
        int excludedCount
) {
}
```

`{BE}/dto/response/MessageTestSendResultResponse.java`:

```java
package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;

/** 테스트 수신자 1명·채널 1개의 결과. */
public record MessageTestSendResultResponse(
        String name,
        MessageChannel channel,
        MessageDeliveryStatus status,
        String failureReason
) {
}
```

`{BE}/dto/response/MessageTestSendResponse.java`:

```java
package com.shinyoung.recruit.dto.response;

import java.util.List;

public record MessageTestSendResponse(
        Long sendId,
        List<MessageTestSendResultResponse> results
) {
}
```

`{BE}/service/MessageVariableFormatter.java` — `private static String groupLabel(String groupName)`의 `private`을 지워 패키지 공개로 바꾼다(발송 서비스의 조건 요약이 같은 규칙을 쓴다). 다른 변경은 없다.

- [ ] **Step 2: 실패하는 테스트 작성**

`{BT}/service/MessageSendServiceTest.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.dto.request.MessageContentRequest;
import com.shinyoung.recruit.dto.request.MessageSendRequest;
import com.shinyoung.recruit.dto.request.MessageTestSendRequest;
import com.shinyoung.recruit.dto.request.MessageTesterRequest;
import com.shinyoung.recruit.dto.response.MessageSendResultResponse;
import com.shinyoung.recruit.dto.response.MessageTestSendResponse;
import com.shinyoung.recruit.dto.response.MessageTestSendResultResponse;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.MessageSendStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.SmsKind;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import com.shinyoung.recruit.exception.InvalidMessageException;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
@RecordApplicationEvents
class MessageSendServiceTest {

    private static final CustomUserDetails HR = CustomUserDetails.fromLdap(
            "hr.kim", "인사팀", "김인사", List.of(new SimpleGrantedAuthority("ROLE_RECRUIT_ADMIN")));

    @Autowired
    private MessageSendService messageSendService;
    @Autowired
    private MessageProperties messageProperties;
    @Autowired
    private JobPostingRepository jobPostingRepository;
    @Autowired
    private JobApplicationRepository jobApplicationRepository;
    @Autowired
    private ApplicantRepository applicantRepository;
    @Autowired
    private ApplicationBasicInfoRepository applicationBasicInfoRepository;
    @Autowired
    private MessageSendRepository messageSendRepository;
    @Autowired
    private MessageRecipientRepository messageRecipientRepository;
    @Autowired
    private ApplicationEvents applicationEvents;

    @MockitoBean
    private MailGateway mailGateway;
    @MockitoBean
    private SmsGateway smsGateway;

    private JobPosting posting;

    @BeforeEach
    void setUp() {
        posting = JobPosting.create("메시지 공고", "Content",
                LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 22, 18, 0));
        posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        posting = jobPostingRepository.saveAndFlush(posting);
        when(mailGateway.send(any(), anyList())).thenReturn(GatewayResult.ok());
        when(smsGateway.send(any(), anyList())).thenReturn(GatewayResult.ok());
    }

    @Test
    void 발송은_선택_수신자만_기록하고_치환_결과를_이벤트로_넘긴다() {
        JobApplication kim = submitted("김민준");
        JobApplication lee = submitted("이서연");
        basicInfo(lee, "이서연", "010-12", "lee@example.com");
        submitted("박제외");

        MessageSendResultResponse result = messageSendService.send(sendRequest(
                List.of(kim.getId(), lee.getId(), 999_999L),
                content("[신영증권] #{이름}님 안내", "#{이름}님, 안녕하세요.", "#{이름}님 안내 문자")), HR);

        assertThat(result.status()).isEqualTo(MessageSendStatus.SENDING);
        assertThat(result.recipientCount()).isEqualTo(2);
        assertThat(result.excludedCount()).isEqualTo(1);
        MessageSend send = messageSendRepository.findById(result.sendId()).orElseThrow();
        assertThat(send.isTest()).isFalse();
        assertThat(send.getSenderLoginId()).isEqualTo("hr.kim");
        assertThat(send.getSenderName()).isEqualTo("김인사");
        assertThat(send.getConditionSummary()).isEqualTo("제출 완료");
        assertThat(send.getMailSubject()).isEqualTo("[신영증권] #{이름}님 안내");
        List<MessageRecipient> recipients = messageRecipientRepository.findByMessageSendIdOrderByIdAsc(send.getId());
        assertThat(recipients).hasSize(2);
        assertThat(recipients.get(1).getSmsStatus()).isEqualTo(MessageDeliveryStatus.SKIPPED);
        assertThat(recipients.get(1).getSmsFailureReason()).isEqualTo(MessageContacts.INVALID_CONTACT);
        MessageSendRequestedEvent event = applicationEvents.stream(MessageSendRequestedEvent.class).findFirst().orElseThrow();
        assertThat(event.messageSendId()).isEqualTo(send.getId());
        assertThat(event.items()).filteredOn(item -> item.channel() == MessageChannel.MAIL)
                .extracting(DeliveryItem::subject)
                .containsExactly("[신영증권] 김민준님 안내", "[신영증권] 이서연님 안내");
        assertThat(event.items()).filteredOn(item -> item.channel() == MessageChannel.SMS)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.body()).isEqualTo("김민준님 안내 문자");
                    assertThat(item.to()).isEqualTo("01000000000");
                    assertThat(item.smsKind()).isEqualTo(SmsKind.SMS);
                });
    }

    @Test
    void 끈_채널은_CHANNEL_OFF로_제외하고_원문도_저장하지_않는다() {
        JobApplication kim = submitted("김민준");

        MessageSendResultResponse result = messageSendService.send(sendRequest(List.of(kim.getId()),
                new MessageContentRequest(null, true, false, "제목", "본문", "무시될 문자")), HR);

        MessageRecipient recipient = messageRecipientRepository.findByMessageSendIdOrderByIdAsc(result.sendId()).get(0);
        assertThat(recipient.getSmsStatus()).isEqualTo(MessageDeliveryStatus.SKIPPED);
        assertThat(recipient.getSmsFailureReason()).isEqualTo(MessageContacts.CHANNEL_OFF);
        assertThat(messageSendRepository.findById(result.sendId()).orElseThrow().getSmsBody()).isNull();
    }

    @Test
    void 채널을_모두_끄면_거부한다() {
        JobApplication kim = submitted("김민준");

        assertThatThrownBy(() -> messageSendService.send(sendRequest(List.of(kim.getId()),
                new MessageContentRequest(null, false, false, null, null, null)), HR))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("메일이나 SMS 중 하나 이상 켜야 합니다.");
    }

    @Test
    void 켠_메일에_제목이나_본문이_없으면_거부한다() {
        JobApplication kim = submitted("김민준");

        assertThatThrownBy(() -> messageSendService.send(sendRequest(List.of(kim.getId()),
                new MessageContentRequest(null, true, false, "제목", " ", null)), HR))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("메일 제목과 본문을 입력해야 합니다.");
    }

    @Test
    void 허용되지_않은_변수가_있으면_거부한다() {
        JobApplication kim = submitted("김민준");

        assertThatThrownBy(() -> messageSendService.send(sendRequest(List.of(kim.getId()),
                content("제목", "#{면접일시}에 오세요", "문자")), HR))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("사용할 수 없는 변수: #{면접일시}");
    }

    @Test
    void 선택한_수신자가_조건에_없으면_거부한다() {
        submitted("김민준");

        assertThatThrownBy(() -> messageSendService.send(sendRequest(List.of(999_999L),
                content("제목", "본문", "문자")), HR))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("보낼 수신자가 없습니다. 대상자를 다시 조회하세요.");
    }

    @Test
    void 최대_인원을_넘으면_거부한다() {
        JobApplication kim = submitted("김민준");
        JobApplication lee = submitted("이서연");
        int original = messageProperties.getMaxRecipients();
        ReflectionTestUtils.setField(messageProperties, "maxRecipients", 1);
        try {
            assertThatThrownBy(() -> messageSendService.send(sendRequest(List.of(kim.getId(), lee.getId()),
                    content("제목", "본문", "문자")), HR))
                    .isInstanceOf(InvalidMessageException.class)
                    .hasMessage("한 번에 최대 1명까지 보낼 수 있습니다.");
        } finally {
            ReflectionTestUtils.setField(messageProperties, "maxRecipients", original);
        }
    }

    @Test
    void SMS가_2000byte를_넘는_수신자가_있으면_수험번호로_알리고_거부한다() {
        JobApplication kim = submitted("김민준");

        assertThatThrownBy(() -> messageSendService.send(sendRequest(List.of(kim.getId()),
                new MessageContentRequest(null, false, true, null, null, "가".repeat(1000) + "#{이름}")), HR))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("SMS가 2,000byte를 넘는 수신자가 1명 있습니다(수험번호 " + kim.getId() + ").");
    }

    @Test
    void 테스트_발송은_미리보기_대상_값으로_즉시_보내고_결과를_준다() {
        JobApplication kim = submitted("김민준");

        MessageTestSendResponse response = messageSendService.testSend(new MessageTestSendRequest(
                MessageType.FREE, posting.getId(), null, null, null, JobApplicationStatus.SUBMITTED,
                kim.getId(),
                List.of(new MessageTesterRequest("김인사", "hr.kim@example.com", "010-0000-1234")),
                content("[신영증권] #{이름}님 안내", "본문", "#{이름}님 문자")), HR);

        assertThat(response.results()).extracting(MessageTestSendResultResponse::channel, MessageTestSendResultResponse::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(MessageChannel.MAIL, MessageDeliveryStatus.SENT),
                        org.assertj.core.groups.Tuple.tuple(MessageChannel.SMS, MessageDeliveryStatus.SENT));
        ArgumentCaptor<MailMessage> mail = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailGateway).send(mail.capture(), org.mockito.ArgumentMatchers.eq(List.of("hr.kim@example.com")));
        assertThat(mail.getValue().subject()).isEqualTo("[테스트] [신영증권] 김민준님 안내");
        ArgumentCaptor<SmsMessage> sms = ArgumentCaptor.forClass(SmsMessage.class);
        verify(smsGateway).send(sms.capture(), org.mockito.ArgumentMatchers.eq(List.of("01000001234")));
        assertThat(sms.getValue().body()).isEqualTo("[테스트] 김민준님 문자");
        MessageSend send = messageSendRepository.findById(response.sendId()).orElseThrow();
        assertThat(send.isTest()).isTrue();
        assertThat(send.getStatus()).isEqualTo(MessageSendStatus.COMPLETED);
        assertThat(send.getMailSent()).isEqualTo(1);
        assertThat(send.getSmsSent()).isEqualTo(1);
    }

    @Test
    void 테스트_수신자에_연락처가_하나도_없으면_거부한다() {
        JobApplication kim = submitted("김민준");

        assertThatThrownBy(() -> messageSendService.testSend(new MessageTestSendRequest(
                MessageType.FREE, posting.getId(), null, null, null, null, kim.getId(),
                List.of(new MessageTesterRequest("김인사", " ", null)),
                content("제목", "본문", "문자")), HR))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("테스트 수신자는 이메일이나 휴대폰 중 하나 이상 입력해야 합니다.");
    }

    @Test
    void 테스트_수신자_연락처_형식이_틀리면_거부한다() {
        JobApplication kim = submitted("김민준");

        assertThatThrownBy(() -> messageSendService.testSend(new MessageTestSendRequest(
                MessageType.FREE, posting.getId(), null, null, null, null, kim.getId(),
                List.of(new MessageTesterRequest("김인사", "not-an-email", null)),
                content("제목", "본문", "문자")), HR))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("테스트 수신자 연락처 형식이 올바르지 않습니다.");
    }

    @Test
    void 미리보기_대상이_조건에_없으면_테스트_발송을_거부한다() {
        submitted("김민준");

        assertThatThrownBy(() -> messageSendService.testSend(new MessageTestSendRequest(
                MessageType.FREE, posting.getId(), null, null, null, null, 999_999L,
                List.of(new MessageTesterRequest("김인사", "hr.kim@example.com", null)),
                content("제목", "본문", "문자")), HR))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("미리보기 대상이 현재 조건의 대상자가 아닙니다. 대상자를 다시 조회하세요.");
    }

    private MessageSendRequest sendRequest(List<Long> applicationIds, MessageContentRequest content) {
        return new MessageSendRequest(MessageType.FREE, posting.getId(), null, null, null,
                JobApplicationStatus.SUBMITTED, applicationIds, content);
    }

    private MessageContentRequest content(String mailSubject, String mailBody, String smsBody) {
        return new MessageContentRequest(null, true, true, mailSubject, mailBody, smsBody);
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

    private void basicInfo(JobApplication application, String name, String phone, String email) {
        applicationBasicInfoRepository.saveAndFlush(ApplicationBasicInfo.create(
                application, name, null, NationalityType.DOMESTIC, null,
                LocalDate.of(1995, 1, 1), phone, null, email,
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT,
                null, null, null, null, null, null));
    }
}
```

- [ ] **Step 3: 실패 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageSendServiceTest" --no-daemon`
Expected: FAIL — `MessageSendService` 없음(컴파일 오류).

- [ ] **Step 4: 서비스 작성**

`{BE}/service/MessageSendService.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.domain.entity.MessageTemplate;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.domain.repository.MessageTemplateRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.dto.condition.MessageTargetCondition;
import com.shinyoung.recruit.dto.request.MessageContentRequest;
import com.shinyoung.recruit.dto.request.MessageSendRequest;
import com.shinyoung.recruit.dto.request.MessageTestSendRequest;
import com.shinyoung.recruit.dto.request.MessageTesterRequest;
import com.shinyoung.recruit.dto.response.MessageSendResultResponse;
import com.shinyoung.recruit.dto.response.MessageTargetRecipientResponse;
import com.shinyoung.recruit.dto.response.MessageTestSendResponse;
import com.shinyoung.recruit.dto.response.MessageTestSendResultResponse;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.SmsKind;
import com.shinyoung.recruit.exception.InvalidMessageException;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 테스트 발송(동기)과 실제 발송 접수(비동기 디스패치)를 처리한다(설계서 7절). */
@Service
@RequiredArgsConstructor
@Transactional
public class MessageSendService {

    private static final String TEST_PREFIX = "[테스트] ";
    private static final int CONDITION_SUMMARY_MAX_LENGTH = 200;
    private static final int OVER_LIMIT_IDS_SHOWN = 5;

    private final MessageTargetService messageTargetService;
    private final MessageRenderer messageRenderer;
    private final MessageDispatcher messageDispatcher;
    private final MessageTemplateRepository messageTemplateRepository;
    private final MessageSendRepository messageSendRepository;
    private final MessageRecipientRepository messageRecipientRepository;
    private final JobPostingRepository jobPostingRepository;
    private final StageRepository stageRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final CurrentEmployeeService currentEmployeeService;
    private final ApplicationEventPublisher eventPublisher;
    private final MessageProperties messageProperties;
    private final Clock clock;

    public MessageSendResultResponse send(MessageSendRequest request, CustomUserDetails userDetails) {
        String senderLoginId = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        MessageContentRequest content = request.content();
        validateContent(request.type(), content);
        MessageTargetCondition condition = request.toCondition();

        Set<Long> requested = new LinkedHashSet<>(request.applicationIds());
        List<MessageTargetRecipientResponse> recipients = messageTargetService.getTargets(condition).recipients().stream()
                .filter(recipient -> requested.contains(recipient.applicationId()))
                .toList();
        if (recipients.isEmpty()) {
            throw new InvalidMessageException("보낼 수신자가 없습니다. 대상자를 다시 조회하세요.");
        }
        if (recipients.size() > messageProperties.getMaxRecipients()) {
            throw new InvalidMessageException(
                    String.format("한 번에 최대 %,d명까지 보낼 수 있습니다.", messageProperties.getMaxRecipients()));
        }

        List<Plan> plans = recipients.stream()
                .map(recipient -> plan(content, recipient.variables(), "", recipient.email(), recipient.phone(),
                        recipient.mailAvailable(), recipient.smsAvailable()))
                .toList();
        requireSmsWithinLimit(plans, recipients.stream().map(recipient -> String.valueOf(recipient.applicationId())).toList());
        requireDeliverable(plans);

        MessageSend send = messageSendRepository.save(
                newSend(condition, false, content, senderLoginId, userDetails.getName(), recipients.size()));
        List<DeliveryItem> items = new ArrayList<>();
        for (int index = 0; index < recipients.size(); index++) {
            MessageTargetRecipientResponse target = recipients.get(index);
            Plan plan = plans.get(index);
            MessageRecipient recipient = messageRecipientRepository.save(plan.toRecipient(
                    send, jobApplicationRepository.getReferenceById(target.applicationId()),
                    target.name(), target.email(), target.phone()));
            items.addAll(plan.items(recipient.getId()));
        }
        eventPublisher.publishEvent(new MessageSendRequestedEvent(send.getId(), items));
        return new MessageSendResultResponse(send.getId(), send.getStatus(), recipients.size(), requested.size() - recipients.size());
    }

    public MessageTestSendResponse testSend(MessageTestSendRequest request, CustomUserDetails userDetails) {
        String senderLoginId = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        MessageContentRequest content = request.content();
        validateContent(request.type(), content);
        validateTesters(request.testers());
        MessageTargetCondition condition = request.toCondition();

        MessageTargetRecipientResponse preview = messageTargetService.getTargets(condition).recipients().stream()
                .filter(recipient -> recipient.applicationId().equals(request.previewApplicationId()))
                .findFirst()
                .orElseThrow(() -> new InvalidMessageException("미리보기 대상이 현재 조건의 대상자가 아닙니다. 대상자를 다시 조회하세요."));

        List<MessageTesterRequest> testers = request.testers();
        List<Plan> plans = testers.stream()
                .map(tester -> plan(content, preview.variables(), TEST_PREFIX, tester.email(), tester.phone(),
                        MessageContacts.isValidEmail(tester.email()), MessageContacts.isValidPhone(tester.phone())))
                .toList();
        requireSmsWithinLimit(plans, testers.stream().map(MessageTesterRequest::name).toList());
        requireDeliverable(plans);

        MessageSend send = messageSendRepository.save(
                newSend(condition, true, content, senderLoginId, userDetails.getName(), testers.size()));
        List<MessageRecipient> recipients = new ArrayList<>();
        List<DeliveryItem> items = new ArrayList<>();
        for (int index = 0; index < testers.size(); index++) {
            MessageTesterRequest tester = testers.get(index);
            MessageRecipient recipient = messageRecipientRepository.save(
                    plans.get(index).toRecipient(send, null, tester.name(), tester.email(), tester.phone()));
            recipients.add(recipient);
            items.addAll(plans.get(index).items(recipient.getId()));
        }
        messageDispatcher.dispatch(send.getId(), items);

        List<MessageTestSendResultResponse> results = new ArrayList<>();
        for (MessageRecipient recipient : recipients) {
            results.add(new MessageTestSendResultResponse(recipient.getRecipientName(), MessageChannel.MAIL,
                    recipient.getMailStatus(), recipient.getMailFailureReason()));
            results.add(new MessageTestSendResultResponse(recipient.getRecipientName(), MessageChannel.SMS,
                    recipient.getSmsStatus(), recipient.getSmsFailureReason()));
        }
        return new MessageTestSendResponse(send.getId(), results);
    }

    private void validateContent(MessageType type, MessageContentRequest content) {
        boolean mail = Boolean.TRUE.equals(content.mailEnabled());
        boolean sms = Boolean.TRUE.equals(content.smsEnabled());
        if (!mail && !sms) {
            throw new InvalidMessageException("메일이나 SMS 중 하나 이상 켜야 합니다.");
        }
        if (mail && (isBlank(content.mailSubject()) || isBlank(content.mailBody()))) {
            throw new InvalidMessageException("메일 제목과 본문을 입력해야 합니다.");
        }
        if (sms && isBlank(content.smsBody())) {
            throw new InvalidMessageException("SMS 내용을 입력해야 합니다.");
        }
        messageRenderer.validateVariables(type,
                mail ? content.mailSubject() : null,
                mail ? content.mailBody() : null,
                sms ? content.smsBody() : null);
    }

    private static void validateTesters(List<MessageTesterRequest> testers) {
        for (MessageTesterRequest tester : testers) {
            boolean hasEmail = !isBlank(tester.email());
            boolean hasPhone = !isBlank(tester.phone());
            if (!hasEmail && !hasPhone) {
                throw new InvalidMessageException("테스트 수신자는 이메일이나 휴대폰 중 하나 이상 입력해야 합니다.");
            }
            if ((hasEmail && !MessageContacts.isValidEmail(tester.email()))
                    || (hasPhone && !MessageContacts.isValidPhone(tester.phone()))) {
                throw new InvalidMessageException("테스트 수신자 연락처 형식이 올바르지 않습니다.");
            }
        }
    }

    private Plan plan(MessageContentRequest content, Map<String, String> variables, String prefix,
                      String email, String phone, boolean mailAvailable, boolean smsAvailable) {
        MessageDeliveryStatus mailStatus;
        String mailReason = null;
        String subject = null;
        String mailBody = null;
        if (!Boolean.TRUE.equals(content.mailEnabled())) {
            mailStatus = MessageDeliveryStatus.SKIPPED;
            mailReason = MessageContacts.CHANNEL_OFF;
        } else if (!mailAvailable) {
            mailStatus = MessageDeliveryStatus.SKIPPED;
            mailReason = MessageContacts.skipReason(email);
        } else {
            mailStatus = MessageDeliveryStatus.PENDING;
            subject = prefix + messageRenderer.render(content.mailSubject(), variables);
            mailBody = messageRenderer.render(content.mailBody(), variables);
        }

        MessageDeliveryStatus smsStatus;
        String smsReason = null;
        String smsBody = null;
        SmsKind smsKind = null;
        boolean smsTooLong = false;
        if (!Boolean.TRUE.equals(content.smsEnabled())) {
            smsStatus = MessageDeliveryStatus.SKIPPED;
            smsReason = MessageContacts.CHANNEL_OFF;
        } else if (!smsAvailable) {
            smsStatus = MessageDeliveryStatus.SKIPPED;
            smsReason = MessageContacts.skipReason(phone);
        } else {
            smsStatus = MessageDeliveryStatus.PENDING;
            smsBody = prefix + messageRenderer.render(content.smsBody(), variables);
            smsKind = messageRenderer.smsKindOf(messageRenderer.smsByteLength(smsBody)).orElse(null);
            smsTooLong = smsKind == null;
        }
        return new Plan(mailStatus, mailReason, subject, mailBody, MessageContacts.normalizeEmail(email),
                smsStatus, smsReason, smsBody, smsKind, MessageContacts.normalizePhone(phone), smsTooLong);
    }

    private static void requireSmsWithinLimit(List<Plan> plans, List<String> labels) {
        List<String> overLimit = new ArrayList<>();
        for (int index = 0; index < plans.size(); index++) {
            if (plans.get(index).smsTooLong()) {
                overLimit.add(labels.get(index));
            }
        }
        if (overLimit.isEmpty()) {
            return;
        }
        String shown = overLimit.stream().limit(OVER_LIMIT_IDS_SHOWN).collect(Collectors.joining(", "));
        String more = overLimit.size() > OVER_LIMIT_IDS_SHOWN ? " 등" : "";
        throw new InvalidMessageException(String.format(
                "SMS가 2,000byte를 넘는 수신자가 %d명 있습니다(수험번호 %s%s).", overLimit.size(), shown, more));
    }

    private static void requireDeliverable(List<Plan> plans) {
        if (plans.stream().noneMatch(Plan::deliverable)) {
            throw new InvalidMessageException("보낼 수 있는 연락처가 없습니다.");
        }
    }

    private MessageSend newSend(MessageTargetCondition condition, boolean test, MessageContentRequest content,
                                String senderLoginId, String senderName, int recipientCount) {
        boolean mail = Boolean.TRUE.equals(content.mailEnabled());
        boolean sms = Boolean.TRUE.equals(content.smsEnabled());
        Stage stage = condition.stageId() == null || condition.type() == MessageType.DEADLINE_REMINDER
                ? null
                : stageRepository.findById(condition.stageId()).orElse(null);
        String templateName = content.templateId() == null
                ? null
                : messageTemplateRepository.findById(content.templateId()).map(MessageTemplate::getName).orElse(null);
        return MessageSend.create(
                condition.type(), test, jobPostingRepository.getReferenceById(condition.jobPostingId()), stage,
                conditionSummary(condition, stage), content.templateId(), templateName,
                mail, sms,
                mail ? content.mailSubject() : null, mail ? content.mailBody() : null, sms ? content.smsBody() : null,
                senderLoginId, senderName, recipientCount, LocalDateTime.now(clock));
    }

    private static String conditionSummary(MessageTargetCondition condition, Stage stage) {
        List<String> parts = new ArrayList<>();
        if (stage != null) {
            parts.add(stage.getStageName());
        }
        switch (condition.type()) {
            case RESULT_ANNOUNCEMENT -> parts.add(resultLabel(condition));
            case DEADLINE_REMINDER -> parts.add("작성 중 지원서");
            case INTERVIEW_SCHEDULE, INTERVIEW_NOTICE -> parts.add(
                    isBlank(condition.interviewGroup()) ? "전체 조" : MessageVariableFormatter.groupLabel(condition.interviewGroup().trim()));
            case FREE -> {
                if (stage != null) {
                    parts.add(resultLabel(condition));
                }
                parts.add(applicationStatusLabel(condition.applicationStatus()));
            }
        }
        String summary = String.join(" · ", parts);
        return summary.length() <= CONDITION_SUMMARY_MAX_LENGTH ? summary : summary.substring(0, CONDITION_SUMMARY_MAX_LENGTH);
    }

    private static String resultLabel(MessageTargetCondition condition) {
        return condition.resultStatus() == null ? "결과 전체" : StageResultStatusLabels.label(condition.resultStatus());
    }

    private static String applicationStatusLabel(JobApplicationStatus status) {
        if (status == null) {
            return "작성 중+제출";
        }
        return status == JobApplicationStatus.SUBMITTED ? "제출 완료" : "작성 중";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 수신자 1명의 채널별 판정과 치환 결과. */
    private record Plan(
            MessageDeliveryStatus mailStatus, String mailReason, String mailSubject, String mailBody, String mailTo,
            MessageDeliveryStatus smsStatus, String smsReason, String smsBody, SmsKind smsKind, String smsTo,
            boolean smsTooLong
    ) {
        boolean deliverable() {
            return mailStatus == MessageDeliveryStatus.PENDING || smsStatus == MessageDeliveryStatus.PENDING;
        }

        MessageRecipient toRecipient(MessageSend send, com.shinyoung.recruit.domain.entity.JobApplication application,
                                     String name, String email, String phone) {
            return MessageRecipient.create(send, application, name, email, phone,
                    mailStatus, mailReason, smsStatus, smsReason, smsKind);
        }

        List<DeliveryItem> items(Long recipientId) {
            List<DeliveryItem> items = new ArrayList<>(2);
            if (mailStatus == MessageDeliveryStatus.PENDING) {
                items.add(new DeliveryItem(recipientId, MessageChannel.MAIL, mailTo, mailSubject, mailBody, null));
            }
            if (smsStatus == MessageDeliveryStatus.PENDING) {
                items.add(new DeliveryItem(recipientId, MessageChannel.SMS, smsTo, null, smsBody, smsKind));
            }
            return items;
        }
    }
}
```

`Plan.toRecipient`의 `JobApplication`은 파일 상단 import로 바꿔도 된다(`com.shinyoung.recruit.domain.entity.JobApplication`). 테스트 발송 수신자는 `null`이다.

- [ ] **Step 5: 통과 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageSendServiceTest" --tests "com.shinyoung.recruit.service.MessageVariableFormatterTest" --no-daemon`
Expected: PASS (12 + 6). `@RecordApplicationEvents`·`@MockitoBean`으로 이 클래스는 별도 스프링 컨텍스트를 쓴다. 테스트 트랜잭션은 롤백되므로 커밋 후 리스너(디스패처)는 실제 발송에서 돌지 않는다(의도). 기대값이 실제 동작과 다르면 멈추고 보고한다.

---

### Task 6: 발송 API와 보안 테스트

**Files:**
- Modify: `{BE}/controller/MessageSendAdminController.java`
- Test: `{BT}/controller/MessageSendCommandControllerTest.java` (신규, 스프링 시큐리티 적용)
- Modify: `{BT}/config/SecurityConfigTest.java` (끝에 2개)

기존 `MessageSendAdminControllerTest`는 시큐리티 없이 조회 API를 검사하므로 그대로 두고, 로그인 사용자가 필요한 발송 API는 새 테스트 클래스에서 `springSecurity()` + 임직원 인증으로 검사한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/controller/MessageSendCommandControllerTest.java`:

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
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class MessageSendCommandControllerTest {

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
    private JobApplication application;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        posting = JobPosting.create("메시지 공고", "Content",
                LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 22, 18, 0));
        posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        posting = jobPostingRepository.saveAndFlush(posting);
        application = submitted("김지원");
    }

    @Test
    void 테스트_발송_결과를_준다() throws Exception {
        mockMvc.perform(post("/api/admin/messages/test")
                        .with(authentication(employee()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "FREE",
                                  "jobPostingId": %d,
                                  "previewApplicationId": %d,
                                  "testers": [{"name": "김인사", "email": "hr.kim@example.com"}],
                                  "content": {"mailEnabled": true, "smsEnabled": false,
                                              "mailSubject": "#{이름}님 안내", "mailBody": "본문"}
                                }
                                """.formatted(posting.getId(), application.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sendId").isNumber())
                .andExpect(jsonPath("$.data.results.length()").value(2))
                .andExpect(jsonPath("$.data.results[0].channel").value("MAIL"))
                .andExpect(jsonPath("$.data.results[0].status").value("SENT"))
                .andExpect(jsonPath("$.data.results[1].status").value("SKIPPED"));
    }

    @Test
    void 발송을_접수한다() throws Exception {
        mockMvc.perform(post("/api/admin/messages/send")
                        .with(authentication(employee()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "FREE",
                                  "jobPostingId": %d,
                                  "applicationIds": [%d],
                                  "content": {"mailEnabled": false, "smsEnabled": true, "smsBody": "#{이름}님 문자"}
                                }
                                """.formatted(posting.getId(), application.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENDING"))
                .andExpect(jsonPath("$.data.recipientCount").value(1))
                .andExpect(jsonPath("$.data.excludedCount").value(0));
    }

    @Test
    void 내용이_없으면_400() throws Exception {
        mockMvc.perform(post("/api/admin/messages/send")
                        .with(authentication(employee()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "FREE", "jobPostingId": %d, "applicationIds": [%d]}
                                """.formatted(posting.getId(), application.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private Authentication employee() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "hr.kim", "인사팀", "김인사", List.of(new SimpleGrantedAuthority("ROLE_RECRUIT_ADMIN")));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
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
        JobApplication created = JobApplication.create(
                applicant, posting, jobPosition, name, posting.getTitle(), jobPosition.getPositionName());
        created.submit(LocalDateTime.of(2026, 9, 10, 9, 0));
        return jobApplicationRepository.saveAndFlush(created);
    }
}
```

`{BT}/config/SecurityConfigTest.java` 끝 `}` 바로 위에 추가:

```java
    @Test
    void 메시지_발송은_비인증이면_401() throws Exception {
        mockMvc.perform(post("/api/admin/messages/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 메시지_발송은_지원자_권한이면_403() throws Exception {
        mockMvc.perform(post("/api/admin/messages/send")
                        .with(user("applicant").authorities(() -> "ROLE_APPLICANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
```

- [ ] **Step 2: 실패 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.MessageSendCommandControllerTest" --no-daemon`
Expected: FAIL — 엔드포인트 없음.

- [ ] **Step 3: 엔드포인트 추가**

`{BE}/controller/MessageSendAdminController.java`:
1. 필드 `private final MessageSendService messageSendService;` 추가.
2. import 추가: `com.shinyoung.recruit.dto.request.MessageSendRequest`, `com.shinyoung.recruit.dto.request.MessageTestSendRequest`, `com.shinyoung.recruit.dto.response.MessageSendResultResponse`, `com.shinyoung.recruit.dto.response.MessageTestSendResponse`, `com.shinyoung.recruit.security.auth.CustomUserDetails`, `com.shinyoung.recruit.service.MessageSendService`, `jakarta.validation.Valid`, `org.springframework.security.core.annotation.AuthenticationPrincipal`, `org.springframework.web.bind.annotation.PostMapping`, `org.springframework.web.bind.annotation.RequestBody`.
3. 클래스 주석을 `/** 메시지 발송 화면 API: 변수 카탈로그·대상자 조회·테스트 발송·발송 접수. */`로 바꾼다.
4. `getTargets` 메서드 아래에 추가:

```java
    @PostMapping("/test")
    public ResponseEntity<ApiResponse<MessageTestSendResponse>> testSend(
            @Valid @RequestBody MessageTestSendRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.success(messageSendService.testSend(request, userDetails)));
    }

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<MessageSendResultResponse>> send(
            @Valid @RequestBody MessageSendRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.success(messageSendService.send(request, userDetails)));
    }
```

- [ ] **Step 4: 통과 확인**

Run: `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.MessageSendCommandControllerTest" --tests "com.shinyoung.recruit.controller.MessageSendAdminControllerTest" --tests "com.shinyoung.recruit.config.SecurityConfigTest" --no-daemon`
Expected: PASS (3 + 3 + 23)

---

### Task 7: 프론트 발송 타입·API·변수 추출·허용되지 않은 변수 경고

**Files:**
- Modify: `{FE}/types/admin/message.ts`, `{FE}/api/admin/messageApi.ts`
- Modify: `{FE}/views/admin/message/messageRender.ts`, `{FE}/views/admin/message/__tests__/messageRender.spec.ts`
- Modify: `{FE}/views/admin/message/MessageComposer.vue`

- [ ] **Step 1: 실패하는 테스트 추가**

`{FE}/views/admin/message/__tests__/messageRender.spec.ts` — import에 `extractVariableKeys`를 추가하고 파일 끝에 추가:

```ts
describe('extractVariableKeys', () => {
  it('본문의 #{키}를 처음 나온 순서대로 중복 없이 돌려준다', () => {
    expect(extractVariableKeys('#{이름}님 #{공고명} #{이름}')).toEqual(['이름', '공고명'])
    expect(extractVariableKeys('변수 없음')).toEqual([])
  })
})
```

- [ ] **Step 2: 실패 확인**

Run: `npx vitest run src/views/admin/message/__tests__/messageRender.spec.ts`
Expected: FAIL — `extractVariableKeys` 없음.

- [ ] **Step 3: 구현**

`{FE}/views/admin/message/messageRender.ts` 파일 끝에 추가:

```ts
/** 본문에 쓴 #{키}를 처음 나온 순서대로 중복 없이 돌려준다. */
export const extractVariableKeys = (text: string): string[] => {
  const keys: string[] = []
  for (const match of text.matchAll(VARIABLE_PATTERN)) {
    const key = match[1] ?? ''
    if (!keys.includes(key)) {
      keys.push(key)
    }
  }
  return keys
}
```

`{FE}/types/admin/message.ts` 파일 끝에 추가:

```ts
export type MessageChannel = 'MAIL' | 'SMS'
export type MessageDeliveryStatus = 'PENDING' | 'SENT' | 'FAILED' | 'SKIPPED'
export type MessageSendStatus = 'SENDING' | 'COMPLETED'

/** 발송·테스트 발송 요청의 작성 내용(치환 전). 켠 채널의 필수 입력은 서버도 검증한다. */
export interface MessageContentRequest {
  templateId: number | null
  mailEnabled: boolean
  smsEnabled: boolean
  mailSubject: string | null
  mailBody: string | null
  smsBody: string | null
}

/** 테스트 발송 수신자(인사팀 담당자). 이메일·휴대폰 중 1개 이상. */
export interface MessageTester {
  name: string
  email: string | null
  phone: string | null
}

/** 실제 발송. 조건은 대상자를 조회했던 쿼리 그대로 보낸다. */
export interface MessageSendRequest extends MessageTargetQuery {
  applicationIds: number[]
  content: MessageContentRequest
}

export interface MessageTestSendRequest extends MessageTargetQuery {
  previewApplicationId: number
  testers: MessageTester[]
  content: MessageContentRequest
}

export interface MessageSendResult {
  sendId: number
  status: MessageSendStatus
  recipientCount: number
  /** 요청했지만 조건이 바뀌어 빠진 인원 */
  excludedCount: number
}

export interface MessageTestSendResultItem {
  name: string
  channel: MessageChannel
  status: MessageDeliveryStatus
  /** NO_CONTACT · INVALID_CONTACT · CHANNEL_OFF · GATEWAY_ERROR 또는 게이트웨이 사유 */
  failureReason: string | null
}

export interface MessageTestSendResponse {
  sendId: number
  results: MessageTestSendResultItem[]
}
```

`{FE}/api/admin/messageApi.ts`:
1. type import 목록에 `MessageSendRequest`, `MessageSendResult`, `MessageTestSendRequest`, `MessageTestSendResponse`를 추가한다.
2. import 아래에 상수를 추가한다:

```ts
// 테스트 발송은 게이트웨이를 동기로 호출하고, 실제 발송은 최대 3,000명 기록을 한 번에 저장한다. 기본 10초로는 부족할 수 있다.
const SEND_TIMEOUT_MS = 60000
```

3. `getTargets` 뒤에 메서드 2개를 추가하고, 파일 상단 주석을 `템플릿·변수·대상자 조회·테스트 발송·발송 접수. 이력은 S4에서 추가한다.`로 바꾼다:

```ts
  /** 테스트 발송. 미리보기 대상의 값으로 치환해 담당자에게 바로 보내고 채널별 결과를 돌려준다. */
  testSend(request: MessageTestSendRequest) {
    return apiClient.post<ApiResponse<MessageTestSendResponse>>('/admin/messages/test', request, {
      timeout: SEND_TIMEOUT_MS,
    })
  },

  /** 실제 발송 접수. 발송은 서버에서 비동기로 진행되고 결과는 발송 이력에서 본다. */
  send(request: MessageSendRequest) {
    return apiClient.post<ApiResponse<MessageSendResult>>('/admin/messages/send', request, {
      timeout: SEND_TIMEOUT_MS,
    })
  },
```

`{FE}/views/admin/message/MessageComposer.vue`:
1. `./messageRender` import에 `extractVariableKeys`를 추가한다.
2. `<script setup>`에 계산값을 추가한다:

```ts
/* 켠 채널 원문에 쓴 변수 중 이 종류에서 쓸 수 없는 것. 서버도 발송 시 400 으로 막는다. */
const unknownKeys = computed(() => {
  const allowed = new Set(props.variables.map((variable) => variable.key))
  const texts = [
    ...(content.value.mailEnabled ? [content.value.mailSubject, content.value.mailBody] : []),
    ...(content.value.smsEnabled ? [content.value.smsBody] : []),
  ]
  return [...new Set(texts.flatMap(extractVariableKeys))].filter((key) => !allowed.has(key))
})
```

3. 템플릿에서 `<div class="variables">` 바로 위에 추가:

```vue
    <a-alert
      v-if="unknownKeys.length"
      class="unknown-alert"
      type="error"
      show-icon
      :message="`이 종류에서 쓸 수 없는 변수가 있습니다: ${unknownKeys.map((key) => `#{${key}}`).join(', ')}`"
    />
```

4. `<style>`에 추가: `.unknown-alert { margin: 0 16px 10px; }`

- [ ] **Step 4: 확인**

Run: `npx vitest run src/views/admin/message` → 18 passed
Run: `npm run type-check` → 오류 없음
Run: `npx eslint src/views/admin/message src/api/admin/messageApi.ts src/types/admin/message.ts` → 0건

---

### Task 8: 발송 요약 계산 (`messageSendSummary.ts`)

**Files:**
- Create: `{FE}/views/admin/message/messageSendSummary.ts`
- Test: `{FE}/views/admin/message/__tests__/messageSendSummary.spec.ts`

하단 발송 바·확인 모달·테스트 카드가 같은 계산을 쓴다. SMS byte는 화면이 이미 계산한 `smsBytesByRecipient`를 받아 다시 치환하지 않는다(3,000명 입력 성능). 연락처 형식 규칙은 서버 `MessageContacts`와 같다. 조건 설명은 서버 `conditionSummary`와 같은 문구다.

- [ ] **Step 1: 실패하는 테스트 작성**

`{FE}/views/admin/message/__tests__/messageSendSummary.spec.ts`:

```ts
import { describe, expect, it } from 'vitest'

import type { MessageContent, MessageTargetRecipient } from '@/types/admin/message'
import type { StageListItem } from '@/types/admin/stage'
import type { MessageCondition } from '../messageCondition'
import { buildSendSummary, describeCondition, isValidEmail, isValidPhone } from '../messageSendSummary'

const recipient = (
  applicationId: number,
  mailAvailable: boolean,
  smsAvailable: boolean,
  missingVariables: string[] = [],
): MessageTargetRecipient => ({
  applicationId,
  name: `지원자${applicationId}`,
  email: mailAvailable ? `u${applicationId}@example.com` : null,
  phone: smsAvailable ? '01000000000' : null,
  mailAvailable,
  smsAvailable,
  resultStatus: null,
  interviewGroup: null,
  interviewDateTime: null,
  draftStartedAt: null,
  variables: {},
  missingVariables,
})

const content = (overrides: Partial<MessageContent> = {}): MessageContent => ({
  templateId: null,
  mailEnabled: true,
  smsEnabled: true,
  mailSubject: '#{이름}님 안내',
  mailBody: '본문',
  smsBody: '문자',
  ...overrides,
})

const input = (overrides: Partial<Parameters<typeof buildSendSummary>[0]> = {}) => ({
  recipients: [recipient(1, true, true), recipient(2, true, false), recipient(3, false, false)],
  smsBytes: [50, 0, 0],
  content: content(),
  allowedKeys: ['이름', '공고명'],
  loading: false,
  error: '',
  ...overrides,
})

describe('buildSendSummary', () => {
  it('채널별 발송 건수와 연락처 없는 인원을 센다', () => {
    const summary = buildSendSummary(input())
    expect(summary).toMatchObject({
      recipientCount: 3,
      mailCount: 2,
      mailMissing: 1,
      smsCount: 1,
      lmsCount: 0,
      smsMissing: 2,
      blockReason: null,
    })
  })

  it('끈 채널은 0건으로 센다', () => {
    const summary = buildSendSummary(input({ content: content({ smsEnabled: false }) }))
    expect(summary.smsCount + summary.lmsCount + summary.smsMissing).toBe(0)
  })

  it('SMS 수신자별 byte로 SMS·LMS를 나누고 2000byte 초과면 막는다', () => {
    const recipients = [recipient(1, true, true), recipient(2, true, true)]
    expect(buildSendSummary(input({ recipients, smsBytes: [100, 60] }))).toMatchObject({ smsCount: 1, lmsCount: 1 })
    expect(buildSendSummary(input({ recipients, smsBytes: [100, 2500] })).blockReason).toBe(
      '2,000byte를 넘는 SMS 수신자가 있습니다.',
    )
  })

  it('값 없는 변수는 본문에 실제로 쓴 변수만 센다', () => {
    const recipients = [recipient(1, true, true, ['도착시각']), recipient(2, true, true, ['이름'])]
    expect(buildSendSummary(input({ recipients, smsBytes: [10, 10] })).missingVariableRecipients).toBe(1)
  })

  it('막는 이유는 불러오는 중 → 조회 실패 → 수신자 없음 → 채널 → 입력 → 변수 순서로 본다', () => {
    expect(buildSendSummary(input({ loading: true, error: 'x' })).blockReason).toBe('대상자를 불러오는 중입니다.')
    expect(buildSendSummary(input({ error: 'x' })).blockReason).toBe('대상자 조회에 실패했습니다. 다시 조회하세요.')
    expect(buildSendSummary(input({ recipients: [], smsBytes: [] })).blockReason).toBe('선택된 수신자가 없습니다.')
    expect(buildSendSummary(input({ content: content({ mailEnabled: false, smsEnabled: false }) })).blockReason).toBe(
      '메일이나 SMS 중 하나 이상 켜세요.',
    )
    expect(buildSendSummary(input({ content: content({ mailBody: ' ' }) })).blockReason).toBe('메일 제목과 본문을 입력하세요.')
    expect(buildSendSummary(input({ content: content({ smsBody: '' }) })).blockReason).toBe('SMS 내용을 입력하세요.')
    const unknown = buildSendSummary(input({ content: content({ mailBody: '#{면접일시}' }) }))
    expect(unknown.unknownKeys).toEqual(['면접일시'])
    expect(unknown.blockReason).toBe('이 종류에서 쓸 수 없는 변수가 있습니다.')
  })

  it('보낼 수 있는 연락처가 하나도 없으면 막는다', () => {
    const summary = buildSendSummary(input({ recipients: [recipient(3, false, false)], smsBytes: [0] }))
    expect(summary.blockReason).toBe('보낼 수 있는 연락처가 없습니다.')
  })
})

describe('describeCondition', () => {
  const stages: StageListItem[] = [
    {
      id: 1,
      jobPostingId: 1,
      stageName: '서류전형',
      stageType: 'DOCUMENT',
      stageOrder: 0,
      status: 'RESULT_ANNOUNCED',
      resultAnnouncementDateTime: null,
      finalStage: false,
    },
    {
      id: 2,
      jobPostingId: 1,
      stageName: '1차 면접',
      stageType: 'FIRST_INTERVIEW',
      stageOrder: 1,
      status: 'READY',
      resultAnnouncementDateTime: null,
      finalStage: false,
    },
  ]
  const base: MessageCondition = {
    jobPostingId: 1,
    stageId: null,
    resultStatus: 'ALL',
    interviewGroup: 'ALL',
    applicationStatus: 'SUBMITTED',
  }

  it('종류별 조건을 서버 조건 요약과 같은 문구로 만든다', () => {
    expect(describeCondition('RESULT_ANNOUNCEMENT', { ...base, stageId: 1, resultStatus: 'PASSED' }, stages)).toBe(
      '서류전형 · 합격',
    )
    expect(describeCondition('INTERVIEW_SCHEDULE', { ...base, stageId: 2, interviewGroup: '2' }, stages)).toBe(
      '1차 면접 · 2조',
    )
    expect(describeCondition('DEADLINE_REMINDER', base, stages)).toBe('작성 중 지원서')
    expect(describeCondition('FREE', base, stages)).toBe('제출 완료')
    expect(describeCondition('FREE', { ...base, stageId: 1, applicationStatus: 'ALL' }, stages)).toBe(
      '서류전형 · 결과 전체 · 작성 중+제출',
    )
  })
})

describe('연락처 형식', () => {
  it('서버 MessageContacts 와 같은 규칙으로 본다', () => {
    expect(isValidEmail(' hr.kim@example.com ')).toBe(true)
    expect(isValidEmail('a@b')).toBe(false)
    expect(isValidPhone('010-1234-5678')).toBe(true)
    expect(isValidPhone('02-1234-5678')).toBe(false)
  })
})
```

- [ ] **Step 2: 실패 확인**

Run: `npx vitest run src/views/admin/message/__tests__/messageSendSummary.spec.ts`
Expected: FAIL — `../messageSendSummary` 없음.

- [ ] **Step 3: 구현**

`{FE}/views/admin/message/messageSendSummary.ts`:

```ts
import type { MessageContent, MessageTargetRecipient, MessageType } from '@/types/admin/message'
import type { StageListItem } from '@/types/admin/stage'
import { RESULT_LABEL, interviewGroupLabel, isInterviewType, type MessageCondition } from './messageCondition'
import { extractVariableKeys, smsKindOf } from './messageRender'

/** 하단 발송 바·확인 모달·테스트 카드가 함께 쓰는 발송 요약. */
export interface SendSummary {
  recipientCount: number
  mailCount: number
  mailMissing: number
  smsCount: number
  lmsCount: number
  smsMissing: number
  /** 본문에 실제로 쓴 변수 중 값이 빈 변수가 있는 수신자 수 */
  missingVariableRecipients: number
  /** 켠 채널 원문에 쓴 변수 중 이 종류에서 쓸 수 없는 것 */
  unknownKeys: string[]
  /** 발송·테스트 발송을 막는 이유. 없으면 null */
  blockReason: string | null
}

export interface SendSummaryInput {
  /** 선택된 수신자 */
  recipients: MessageTargetRecipient[]
  /** recipients 와 같은 순서의 치환 후 SMS byte(SMS 를 받을 수 없는 수신자는 0) */
  smsBytes: number[]
  content: MessageContent
  /** 이 종류에서 쓸 수 있는 변수 키 */
  allowedKeys: string[]
  loading: boolean
  error: string
}

const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/
const MOBILE_PHONE_PATTERN = /^01\d{8,9}$/

/** 서버 MessageContacts.isValidEmail 과 같은 규칙. */
export const isValidEmail = (value: string): boolean => EMAIL_PATTERN.test(value.trim())

/** 서버 MessageContacts.isValidPhone 과 같은 규칙(숫자만 남겨 01로 시작하는 10~11자리). */
export const isValidPhone = (value: string): boolean => MOBILE_PHONE_PATTERN.test(value.replace(/\D/g, ''))

/** 켠 채널 원문에 쓴 변수 키. */
export const usedVariableKeys = (content: MessageContent): string[] => {
  const texts = [
    ...(content.mailEnabled ? [content.mailSubject, content.mailBody] : []),
    ...(content.smsEnabled ? [content.smsBody] : []),
  ]
  return [...new Set(texts.flatMap(extractVariableKeys))]
}

export const buildSendSummary = (input: SendSummaryInput): SendSummary => {
  const { recipients, smsBytes, content } = input
  const recipientCount = recipients.length
  const mailCount = content.mailEnabled ? recipients.filter((recipient) => recipient.mailAvailable).length : 0

  let smsCount = 0
  let lmsCount = 0
  let overLimit = 0
  let smsReachable = 0
  if (content.smsEnabled) {
    recipients.forEach((recipient, index) => {
      if (!recipient.smsAvailable) return
      smsReachable += 1
      const kind = smsKindOf(smsBytes[index] ?? 0)
      if (kind === 'SMS') smsCount += 1
      else if (kind === 'LMS') lmsCount += 1
      else overLimit += 1
    })
  }

  const usedKeys = usedVariableKeys(content)
  const allowed = new Set(input.allowedKeys)
  const unknownKeys = usedKeys.filter((key) => !allowed.has(key))
  const missingVariableRecipients = recipients.filter((recipient) =>
    recipient.missingVariables.some((key) => usedKeys.includes(key)),
  ).length

  return {
    recipientCount,
    mailCount,
    mailMissing: content.mailEnabled ? recipientCount - mailCount : 0,
    smsCount,
    lmsCount,
    smsMissing: content.smsEnabled ? recipientCount - smsReachable : 0,
    missingVariableRecipients,
    unknownKeys,
    blockReason: blockReasonOf(input, recipientCount, mailCount + smsCount + lmsCount, overLimit, unknownKeys),
  }
}

const blockReasonOf = (
  input: SendSummaryInput,
  recipientCount: number,
  deliverableCount: number,
  overLimit: number,
  unknownKeys: string[],
): string | null => {
  const { content } = input
  if (input.loading) return '대상자를 불러오는 중입니다.'
  if (input.error) return '대상자 조회에 실패했습니다. 다시 조회하세요.'
  if (recipientCount === 0) return '선택된 수신자가 없습니다.'
  if (!content.mailEnabled && !content.smsEnabled) return '메일이나 SMS 중 하나 이상 켜세요.'
  if (content.mailEnabled && (!content.mailSubject.trim() || !content.mailBody.trim())) return '메일 제목과 본문을 입력하세요.'
  if (content.smsEnabled && !content.smsBody.trim()) return 'SMS 내용을 입력하세요.'
  if (unknownKeys.length > 0) return '이 종류에서 쓸 수 없는 변수가 있습니다.'
  if (overLimit > 0) return '2,000byte를 넘는 SMS 수신자가 있습니다.'
  if (deliverableCount === 0) return '보낼 수 있는 연락처가 없습니다.'
  return null
}

const resultText = (condition: MessageCondition): string =>
  condition.resultStatus === 'ALL' ? '결과 전체' : RESULT_LABEL[condition.resultStatus]

const applicationStatusText = (condition: MessageCondition): string => {
  if (condition.applicationStatus === 'SUBMITTED') return '제출 완료'
  if (condition.applicationStatus === 'DRAFT') return '작성 중'
  return '작성 중+제출'
}

/** 확인 모달용 조건 설명. 서버 MessageSendService.conditionSummary 와 같은 문구다. */
export const describeCondition = (type: MessageType, condition: MessageCondition, stages: StageListItem[]): string => {
  const stage = type === 'DEADLINE_REMINDER' ? undefined : stages.find((item) => item.id === condition.stageId)
  const parts: string[] = stage ? [stage.stageName] : []
  if (type === 'RESULT_ANNOUNCEMENT') parts.push(resultText(condition))
  if (type === 'DEADLINE_REMINDER') parts.push('작성 중 지원서')
  if (isInterviewType(type)) {
    parts.push(condition.interviewGroup === 'ALL' ? '전체 조' : interviewGroupLabel(condition.interviewGroup))
  }
  if (type === 'FREE') {
    if (stage) parts.push(resultText(condition))
    parts.push(applicationStatusText(condition))
  }
  return parts.join(' · ')
}
```

- [ ] **Step 4: 통과 확인**

Run: `npx vitest run src/views/admin/message/__tests__/messageSendSummary.spec.ts` → 8 passed
Run: `npm run type-check` → 오류 없음

---

### Task 9: 테스트 발송 카드·하단 발송 바·발송 확인 모달

**Files:**
- Create: `{FE}/views/admin/message/MessageTestSendCard.vue`
- Create: `{FE}/views/admin/message/MessageSendBar.vue`
- Create: `{FE}/views/admin/message/MessageSendConfirmModal.vue`

세 컴포넌트는 표시·입력만 하고 API는 부르지 않는다(발송 화면이 호출). 설계서 3.1절 5·6·8.

- [ ] **Step 1: 테스트 발송 카드 작성**

`{FE}/views/admin/message/MessageTestSendCard.vue`:

```vue
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { CloseOutlined, ExperimentOutlined, PlusOutlined, SendOutlined } from '@ant-design/icons-vue'

import type {
  MessageChannel,
  MessageDeliveryStatus,
  MessageTester,
  MessageTestSendResultItem,
} from '@/types/admin/message'
import { isValidEmail, isValidPhone } from './messageSendSummary'

const MAX_TESTERS = 5
/* 최근 테스트 수신자는 이 브라우저에만 기억한다. 저장소를 못 쓰면 기억만 하지 않는다. */
const STORAGE_KEY = 'recruit.message.testers'

const props = defineProps<{
  /** 미리보기 중인 수신자 이름. 없으면 테스트 발송을 할 수 없다. */
  previewName: string | null
  /** 발송 요약의 막는 이유(테스트 발송도 같은 조건으로 막는다) */
  blockReason: string | null
  sending: boolean
  results: MessageTestSendResultItem[]
}>()

const emit = defineEmits<{
  test: [testers: MessageTester[]]
}>()

const STATUS_TEXT: Record<MessageDeliveryStatus, string> = {
  PENDING: '대기',
  SENT: '성공',
  FAILED: '실패',
  SKIPPED: '제외',
}

const CHANNEL_TEXT: Record<MessageChannel, string> = {
  MAIL: '메일',
  SMS: 'SMS',
}

const REASON_TEXT: Record<string, string> = {
  INVALID_CONTACT: '형식 오류',
  GATEWAY_ERROR: '발송 오류',
}

const isTester = (value: unknown): value is MessageTester =>
  typeof value === 'object' && value !== null && typeof (value as MessageTester).name === 'string'

const loadTesters = (): MessageTester[] => {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    const parsed: unknown = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed.filter(isTester).slice(0, MAX_TESTERS) : []
  } catch {
    return []
  }
}

const testers = ref<MessageTester[]>(loadTesters())

watch(
  testers,
  (value) => {
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(value))
    } catch {
      /* 저장소를 못 쓰면 기억하지 않는다. */
    }
  },
  { deep: true },
)

const name = ref('')
const contact = ref('')

const addTester = (): void => {
  const value = contact.value.trim()
  if (!value) {
    message.warning('이메일이나 휴대폰 번호를 입력해 주세요.')
    return
  }
  if (testers.value.length >= MAX_TESTERS) {
    message.warning(`테스트 수신자는 최대 ${MAX_TESTERS}명입니다.`)
    return
  }
  const email = value.includes('@')
  if (email ? !isValidEmail(value) : !isValidPhone(value)) {
    message.warning('연락처 형식이 올바르지 않습니다.')
    return
  }
  testers.value = [
    ...testers.value,
    { name: name.value.trim() || '담당자', email: email ? value : null, phone: email ? null : value },
  ]
  name.value = ''
  contact.value = ''
}

const removeTester = (index: number): void => {
  testers.value = testers.value.filter((_, position) => position !== index)
}

const disabledReason = computed(() => {
  if (props.blockReason) return props.blockReason
  if (!props.previewName) return '미리볼 수신자가 없습니다.'
  if (testers.value.length === 0) return '테스트 받을 담당자를 추가하세요.'
  return null
})

/* 채널을 껐거나 그 담당자에게 해당 연락처가 없어 건너뛴 결과는 보여 주지 않는다. */
const visibleResults = computed(() =>
  props.results.filter((result) => result.failureReason !== 'CHANNEL_OFF' && result.failureReason !== 'NO_CONTACT'),
)
</script>

<template>
  <section class="test-card">
    <div class="card-head">
      <span class="card-title"><ExperimentOutlined /> 테스트 발송</span>
      <span class="card-hint">인사팀 담당자에게 먼저 보내 확인</span>
    </div>

    <div class="tester-input">
      <a-input v-model:value="name" class="name-input" placeholder="이름" :maxlength="50" />
      <a-input v-model:value="contact" placeholder="이메일 또는 휴대폰" :maxlength="200" @press-enter="addTester" />
      <a-button aria-label="테스트 수신자 추가" @click="addTester"><PlusOutlined /></a-button>
    </div>

    <div class="chips">
      <span v-for="(tester, index) in testers" :key="`${index}-${tester.email ?? tester.phone}`" class="chip">
        {{ tester.name }} · {{ tester.email ?? tester.phone }}
        <button type="button" class="chip-remove" :aria-label="`${tester.name} 삭제`" @click="removeTester(index)">
          <CloseOutlined />
        </button>
      </span>
      <span v-if="testers.length === 0" class="empty">받을 담당자를 추가하세요</span>
    </div>

    <a-tooltip :title="disabledReason">
      <a-button block :disabled="disabledReason !== null" :loading="sending" @click="emit('test', testers)">
        <SendOutlined /> 현재 미리보기 내용으로 테스트 발송
      </a-button>
    </a-tooltip>
    <p v-if="previewName" class="preview-note">
      {{ previewName }}님 데이터로 치환하고 메일 제목과 문자 앞에 [테스트]를 붙입니다.
    </p>

    <ul v-if="visibleResults.length" class="results">
      <li v-for="(result, index) in visibleResults" :key="index">
        <a-tag :color="result.status === 'SENT' ? 'green' : 'red'">{{ STATUS_TEXT[result.status] }}</a-tag>
        {{ result.name }} · {{ CHANNEL_TEXT[result.channel] }}
        <span v-if="result.failureReason" class="reason">
          ({{ REASON_TEXT[result.failureReason] ?? result.failureReason }})
        </span>
      </li>
    </ul>
  </section>
</template>

<style scoped lang="scss">
.test-card {
  padding: 14px;
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius-lg);
  background: var(--app-bg-surface);
  box-shadow: var(--app-card-shadow);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.card-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.card-hint,
.preview-note,
.empty {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.tester-input {
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr) auto;
  gap: 6px;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 8px 0 10px;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 6px 3px 10px;
  border: 1px solid var(--app-border-default);
  border-radius: 16px;
  background: var(--app-bg-muted);
  font-size: 12px;
}

.chip-remove {
  display: inline-flex;
  padding: 0;
  border: 0;
  background: none;
  color: var(--app-text-muted);
  cursor: pointer;
}

.preview-note {
  margin: 8px 0 0;
}

.results {
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
  font-size: 12px;

  li + li {
    margin-top: 4px;
  }
}

.reason {
  color: var(--app-text-secondary);
}
</style>
```

- [ ] **Step 2: 하단 발송 바 작성**

`{FE}/views/admin/message/MessageSendBar.vue`:

```vue
<script setup lang="ts">
import { MailOutlined, MessageOutlined, SendOutlined, WarningOutlined } from '@ant-design/icons-vue'

import type { SendSummary } from './messageSendSummary'

defineProps<{
  typeName: string
  summary: SendSummary
  mailEnabled: boolean
  smsEnabled: boolean
  tested: boolean
  sending: boolean
}>()

const emit = defineEmits<{
  send: []
}>()
</script>

<template>
  <div class="send-bar">
    <div class="bar-summary">
      <a-tag color="green">{{ typeName }}</a-tag>
      <span>수신 대상 <strong>{{ summary.recipientCount }}명</strong></span>
      <span class="divider" />
      <span>
        <MailOutlined /> 메일 <strong>{{ mailEnabled ? `${summary.mailCount}건` : '제외' }}</strong>
        <span v-if="summary.mailMissing" class="missing">(이메일 없음 {{ summary.mailMissing }})</span>
      </span>
      <span>
        <MessageOutlined /> 문자
        <strong>{{ smsEnabled ? `SMS ${summary.smsCount} · LMS ${summary.lmsCount}건` : '제외' }}</strong>
        <span v-if="summary.smsMissing" class="missing">(휴대폰 없음 {{ summary.smsMissing }})</span>
      </span>
    </div>
    <div class="bar-actions">
      <span v-if="summary.blockReason" class="block-reason">{{ summary.blockReason }}</span>
      <span v-else-if="!tested" class="untested"><WarningOutlined /> 아직 테스트 발송 안 함</span>
      <a-button
        type="primary"
        size="large"
        :disabled="summary.blockReason !== null"
        :loading="sending"
        @click="emit('send')"
      >
        <SendOutlined /> 발송하기
      </a-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.send-bar {
  position: sticky;
  bottom: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 18px;
  margin-top: 16px;
  padding: 12px 20px;
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius-lg);
  background: var(--app-bg-surface);
  box-shadow: 0 -6px 18px rgb(0 0 0 / 5%);
}

.bar-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  font-size: 13px;
  color: var(--app-text-secondary);

  strong {
    color: var(--app-text-primary);
  }
}

.divider {
  width: 1px;
  height: 18px;
  background: var(--app-border-default);
}

.missing,
.block-reason {
  color: var(--app-color-error);
}

.bar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
  font-size: 13px;
}

.untested {
  color: var(--app-color-warning);
}
</style>
```

- [ ] **Step 3: 발송 확인 모달 작성**

`{FE}/views/admin/message/MessageSendConfirmModal.vue`:

```vue
<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import type { SendSummary } from './messageSendSummary'

const props = defineProps<{
  typeName: string
  postingTitle: string
  conditionText: string
  templateName: string | null
  dirty: boolean
  summary: SendSummary
  mailEnabled: boolean
  smsEnabled: boolean
  tested: boolean
  sending: boolean
}>()

const open = defineModel<boolean>('open', { required: true })

const emit = defineEmits<{
  confirm: []
}>()

const confirmed = ref(false)

watch(open, (value) => {
  if (value) {
    confirmed.value = false
  }
})

const templateText = computed(() =>
  props.templateName ? `${props.templateName}${props.dirty ? ' (수정됨)' : ''}` : '새로 작성',
)

const channelText = computed(() =>
  [
    props.mailEnabled ? `메일 ${props.summary.mailCount}건` : '메일 제외',
    props.smsEnabled ? `SMS ${props.summary.smsCount}건 · LMS ${props.summary.lmsCount}건` : 'SMS 제외',
  ].join(' / '),
)
</script>

<template>
  <a-modal
    v-model:open="open"
    title="메시지를 발송할까요?"
    width="540px"
    :closable="!sending"
    :mask-closable="!sending"
    :keyboard="!sending"
  >
    <p class="lead">발송 후에는 취소할 수 없습니다. 아래 내용을 확인하세요.</p>
    <a-descriptions bordered size="small" :column="1">
      <a-descriptions-item label="종류">{{ typeName }}</a-descriptions-item>
      <a-descriptions-item label="공고 · 조건">
        {{ postingTitle }}<template v-if="conditionText"> · {{ conditionText }}</template>
      </a-descriptions-item>
      <a-descriptions-item label="수신 대상"><strong>{{ summary.recipientCount }}명</strong></a-descriptions-item>
      <a-descriptions-item label="채널">{{ channelText }}</a-descriptions-item>
      <a-descriptions-item label="템플릿">{{ templateText }}</a-descriptions-item>
    </a-descriptions>
    <a-alert
      v-if="!tested"
      class="notice"
      type="warning"
      show-icon
      message="아직 테스트 발송을 하지 않았습니다. 실제 수신 화면을 먼저 확인하는 것을 권장합니다."
    />
    <a-alert
      v-if="summary.missingVariableRecipients > 0"
      class="notice"
      type="warning"
      show-icon
      :message="`값이 없는 변수가 있는 수신자가 ${summary.missingVariableRecipients}명 있습니다. 그 자리는 빈칸으로 발송됩니다.`"
    />
    <a-checkbox v-model:checked="confirmed" class="confirm-check">대상자와 내용을 확인했습니다</a-checkbox>
    <template #footer>
      <a-button :disabled="sending" @click="open = false">취소</a-button>
      <a-button type="primary" :disabled="!confirmed" :loading="sending" @click="emit('confirm')">
        {{ summary.recipientCount }}명에게 발송
      </a-button>
    </template>
  </a-modal>
</template>

<style scoped lang="scss">
.lead {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--app-text-secondary);
}

.notice {
  margin-top: 12px;
}

.confirm-check {
  margin-top: 14px;
}
</style>
```

- [ ] **Step 4: 타입 검사·린트**

Run: `npm run type-check` → 오류 없음
Run: `npx eslint src/views/admin/message` → 0건. ant-design-vue 타입(`a-modal` `keyboard`, `a-descriptions-item` 등) 때문에 오류가 나면 동작을 바꾸지 않는 최소 수정으로 고치고 보고한다.

---

### Task 10: 발송 화면에 테스트 발송·발송 연결

**Files:**
- Modify: `{FE}/views/admin/message/AdminMessageSendView.vue`

현재 파일(S2 구현 + 리뷰 수정본)을 먼저 읽고 아래만 바꾼다. 기존 흐름(종류·공고·전형 변경, 대상 조회, 요청 순번, `smsBytesByRecipient`)은 그대로 둔다.

- [ ] **Step 1: import·상태 추가**

1. import 추가:

```ts
import MessageSendBar from './MessageSendBar.vue'
import MessageSendConfirmModal from './MessageSendConfirmModal.vue'
import MessageTestSendCard from './MessageTestSendCard.vue'
import { buildSendSummary, describeCondition } from './messageSendSummary'
import { MESSAGE_TYPES } from './messageTypes'
```

`@/types/admin/message` type import에 `MessageContentRequest`, `MessageTargetQuery`, `MessageTester`, `MessageTestSendResultItem`를 추가한다.

2. 상태 추가(`const channel = ref<Channel>('mail')` 아래):

```ts
/* 지금 목록을 만든 조회 쿼리. 발송·테스트 발송은 이 쿼리로 보낸다(조건을 바꿨는데 목록이 옛것인 상태를 막는다). */
const loadedQuery = ref<MessageTargetQuery | null>(null)
const tested = ref(false)
const testSending = ref(false)
const testResults = ref<MessageTestSendResultItem[]>([])
const confirmOpen = ref(false)
const sending = ref(false)
```

3. `loadTargets`에서 쿼리 기준을 기록한다:
   - `currentQuery === null` 분기에 `loadedQuery.value = null` 추가.
   - 성공 분기(`target.value = response.data.data` 다음)에 `loadedQuery.value = currentQuery` 추가.
   - 실패 분기(`target.value = null` 옆)에 `loadedQuery.value = null` 추가.
   - 요청을 시작할 때(`targetLoading.value = true` 바로 앞)도 `loadedQuery.value = null`로 비운다(재조회 중 발송 차단).

- [ ] **Step 2: 요약·요청·핸들러 추가**

`smsStats` computed 아래에 추가:

```ts
const typeName = computed(() => MESSAGE_TYPES.find((meta) => meta.type === type.value)?.name ?? '')
const postingTitle = computed(
  () => postings.value.find((posting) => posting.id === condition.value.jobPostingId)?.title ?? '',
)
const conditionText = computed(() => describeCondition(type.value, condition.value, stages.value))
const templateName = computed(
  () => templates.value.find((template) => template.id === content.value.templateId)?.name ?? null,
)
const previewRecipient = computed(() => selectedRecipients.value[previewIndex.value] ?? null)

const sendSummary = computed(() =>
  buildSendSummary({
    recipients: selectedRecipients.value,
    smsBytes: smsBytesByRecipient.value,
    content: content.value,
    allowedKeys: typeVariables.value.map((variable) => variable.key),
    loading: targetLoading.value,
    error: targetError.value,
  }),
)

/* 내용이나 종류가 바뀌면 이전 테스트 발송은 지금 내용과 다르다. */
watch(
  [content, type],
  () => {
    tested.value = false
    testResults.value = []
  },
  { deep: true },
)

const toContentRequest = (): MessageContentRequest => ({
  templateId: content.value.templateId,
  mailEnabled: content.value.mailEnabled,
  smsEnabled: content.value.smsEnabled,
  mailSubject: content.value.mailSubject,
  mailBody: content.value.mailBody,
  smsBody: content.value.smsBody,
})

const runTest = async (testers: MessageTester[]): Promise<void> => {
  const query = loadedQuery.value
  const preview = previewRecipient.value
  if (!query || !preview) return
  testSending.value = true
  try {
    const response = await messageApi.testSend({
      ...query,
      previewApplicationId: preview.applicationId,
      testers,
      content: toContentRequest(),
    })
    testResults.value = response.data.data.results
    tested.value = testResults.value.some((result) => result.status === 'SENT')
    if (tested.value) {
      message.success('테스트 발송을 보냈습니다. 받은 메일·문자를 확인하세요.')
    } else {
      message.warning('테스트 발송에 성공한 채널이 없습니다. 결과를 확인하세요.')
    }
  } catch (error) {
    message.error(getApiErrorMessage(error, '테스트 발송에 실패했습니다.'))
  } finally {
    testSending.value = false
  }
}

const confirmSend = async (): Promise<void> => {
  const query = loadedQuery.value
  if (!query) return
  sending.value = true
  try {
    const response = await messageApi.send({
      ...query,
      applicationIds: [...selectedIds.value],
      content: toContentRequest(),
    })
    const result = response.data.data
    confirmOpen.value = false
    tested.value = false
    message.success(
      `발송을 요청했습니다(발송 번호 ${result.sendId}, ${result.recipientCount}명). 결과는 발송 이력에서 확인할 수 있습니다.`,
    )
    if (result.excludedCount > 0) {
      message.warning(`조건이 바뀌어 ${result.excludedCount}명은 발송에서 제외했습니다.`)
    }
  } catch (error) {
    message.error(getApiErrorMessage(error, '발송 요청에 실패했습니다.'))
  } finally {
    sending.value = false
  }
}
```

`tested` 초기화 watch는 `runTest`에서 `tested`를 true로 만든 뒤 내용이 바뀌지 않는 한 다시 돌지 않는다(내용·종류가 바뀔 때만 돈다).

- [ ] **Step 3: 템플릿 연결**

1. `<MessagePreview ... />`를 오른쪽 열 래퍼로 감싸고 테스트 카드를 붙인다:

```vue
      <div class="side">
        <MessagePreview
          v-model:index="previewIndex"
          v-model:channel="channel"
          :type="type"
          :recipients="selectedRecipients"
          :content="content"
          :sender="target?.sender ?? null"
        />
        <MessageTestSendCard
          :preview-name="previewRecipient?.name ?? null"
          :block-reason="sendSummary.blockReason"
          :sending="testSending"
          :results="testResults"
          @test="runTest"
        />
      </div>
```

2. `</div>`(`.work` 닫는 태그)와 `<MessageRecipientDrawer ...>` 사이에 추가:

```vue
    <MessageSendBar
      :type-name="typeName"
      :summary="sendSummary"
      :mail-enabled="content.mailEnabled"
      :sms-enabled="content.smsEnabled"
      :tested="tested"
      :sending="sending"
      @send="confirmOpen = true"
    />

    <MessageSendConfirmModal
      v-model:open="confirmOpen"
      :type-name="typeName"
      :posting-title="postingTitle"
      :condition-text="conditionText"
      :template-name="templateName"
      :dirty="dirty"
      :summary="sendSummary"
      :mail-enabled="content.mailEnabled"
      :sms-enabled="content.smsEnabled"
      :tested="tested"
      :sending="sending"
      @confirm="confirmSend"
    />
```

3. `<style>`에 추가:

```scss
.side {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
```

- [ ] **Step 4: 확인**

Run: `npm run type-check` → 오류 없음
Run: `npx eslint src/views/admin/message src/routes/adminRoutes.ts src/api/admin/messageApi.ts src/types/admin/message.ts` → 0건
Run: `npx vitest run src/views/admin/message` → 26 passed
Run: `npm run build` → 성공(크기 경고만 무시)

화면 수동 확인(로그인 환경이 될 때): 테스트 담당자 추가 → 테스트 발송 결과 표시 → "아직 테스트 발송 안 함" 경고 사라짐 → 본문 한 글자 수정 시 경고 다시 표시 → 발송하기 → 확인 모달 체크 → 발송 요청 토스트. 로그인 환경이 없으면 건너뛰고 보고한다.

---

### Task 11: 도메인 카드·색인·설계서 갱신

**Files:**
- Modify: `docs/domains/message.md`, `docs/domains/_index.md`

코드가 기준이다. 아래를 실제 코드와 대조해 쓴다.

- [ ] **Step 1: 카드 갱신**

1. `## 요약`: `**현재 S3(테스트 발송·발송)까지 구현.**`, 화면 줄에 "발송 화면에서 테스트 발송·발송 가능, 이력은 S4".
2. `## 파일 지도` 백엔드 행 추가: `MessageContacts`, `MessageSend`·`MessageRecipient`(엔티티), `MessageSendRepository`·`MessageRecipientRepository`, `MailGateway`·`SmsGateway`·`GatewayResult`·`MailMessage`·`SmsMessage`, `LoggingMailGateway`·`LoggingSmsGateway`, `MessageMailLayout` + `{BR}/templates/message-mail.html`, `DeliveryItem`·`DeliveryUnit`, `MessageDeliveryService`, `MessageDispatchRecorder`, `MessageSendRequestedEvent`·`MessageDispatcher`, `MessageSendService`, `config/AsyncConfig.java`, enum 3개(`MessageChannel`·`MessageSendStatus`·`MessageDeliveryStatus`), 요청 DTO 4개·응답 DTO 3개, `MessageSendNotFoundException`, 테스트 클래스들. `MessageSendAdminController` 역할을 `변수 카탈로그·대상자 조회·테스트 발송·발송 접수`로.
3. 프론트 행 추가: `MessageTestSendCard.vue`, `MessageSendBar.vue`, `MessageSendConfirmModal.vue`, `messageSendSummary.ts`, `__tests__/messageSendSummary.spec.ts`. `messageApi.ts` 역할에 테스트 발송·발송 추가.
4. `## API 계약`: `/admin/messages/test`, `/admin/messages/send` 행을 🟢로 바꾸고 요청·응답 요약을 실제 DTO대로 적는다(`content { templateId?, mailEnabled, smsEnabled, mailSubject?, mailBody?, smsBody? }`, 테스트 `testers[{ name, email?, phone? }]` 1~5명, 응답 `{ sendId, results[{ name, channel, status, failureReason }] }` / `{ sendId, status, recipientCount, excludedCount }`). `### 엔드포인트 상세`에 400 메시지 목록(Task 5 규칙의 메시지들)과 인증(임직원 아니면 401/403)을 적는다.
5. `## 규칙·불변식` 추가: 발송 흐름(재조회·교집합·최대 3,000명·SMS 2,000byte 초과는 수험번호로 400·치환은 요청 시점에 하고 이벤트로 디스패처에 넘김·DB에는 치환 전 원문만), 채널 상태(`PENDING`/`SKIPPED` + `CHANNEL_OFF`·`NO_CONTACT`·`INVALID_CONTACT`, 실패 `GATEWAY_ERROR`), 발송 단위(내용 같은 수신자 최대 10명, 메일 먼저, 단위 결과를 전원에), 테스트 발송(동기, `[테스트] ` 접두, 미리보기 대상 값, 결과 즉시), 수신자 연락처 AES 암호화, 로그 마스킹.
6. `## 함정·결정` 추가: `@EnableAsync`만 두고 부트 기본 실행기를 쓰는 이유, 서버 재시작 시 `SENDING`·`PENDING`이 남음(자동 재개 없음), `MessageDispatchRecorder`가 `REQUIRED`라 테스트 발송은 요청 트랜잭션에 합류, `MessageSendServiceTest`는 `@RecordApplicationEvents`·`@MockitoBean`으로 별도 컨텍스트, 실제 SMTP·문자 솔루션 구현체는 `recruit.message.gateway` 값으로 교체(추가 시 `spring-boot-starter-mail` 등 의존성 승인 필요), 파기 연동은 S4.
7. `## 검증` 명령에 새 테스트 클래스(`DeliveryUnitTest`·`MessageMailLayoutTest`·`MessageDeliveryServiceTest`·`MessageDispatcherTest`·`MessageDispatchRecorderTest`·`MessageSendServiceTest`·`MessageContactsTest`·`MessageSendCommandControllerTest`)가 포함되도록 `--tests "com.shinyoung.recruit.service.Message*"`·`"com.shinyoung.recruit.service.Delivery*"`·`"com.shinyoung.recruit.controller.Message*"` 패턴을 쓴다.

- [ ] **Step 2: 색인**

`docs/domains/_index.md` 키워드 행(`| 메일, SMS, ...`) 끝에 `, 발송 단위, 게이트웨이`를 덧붙인다.

- [ ] **Step 3: 문서 점검**

Run(레포 루트): `node tools/check-docs.mjs` → 오류 0건. 카드가 30KB를 넘으면 보고한다(40KB 상한).

---

### Task 12: S3 마무리 검증과 보고

- [ ] **Step 1: 변경 범위 테스트**

Run(`recruit_back/recruit_backend/`): `$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.Message*" --tests "com.shinyoung.recruit.service.Delivery*" --tests "com.shinyoung.recruit.controller.Message*" --tests "com.shinyoung.recruit.config.ApplicationYamlTest" --tests "com.shinyoung.recruit.config.SecurityConfigTest" --no-daemon`
Expected: `BUILD SUCCESSFUL`

Run(`recruit_front/`): `npm run type-check`, `npx vitest run src/views/admin/message`(26 passed), `npx eslint src/views/admin/message src/routes/adminRoutes.ts src/api/admin/messageApi.ts src/types/admin/message.ts`, `npm run build`

Run(레포 루트): `node tools/check-docs.mjs`

- [ ] **Step 2: 보고(한국어)**

변경 파일, 테스트 결과, 계약 변경(test·send 🟢), 설계 변경(요청 시점 치환·기본 실행기), 화면 수동 확인 여부, 남은 미결을 보고한다.

- [ ] **Step 3: slice 구현 리포트**

`design-report` 스킬로 `docs/archive/reports/message-send-s3_implementation.html`을 만든다.

- [ ] **Step 4: 다음 단계**

S4(발송 이력 API·화면, 수신자 연락처 파기 연동, 카드 확정) 계획을 S3 코드 기준으로 작성한다.
