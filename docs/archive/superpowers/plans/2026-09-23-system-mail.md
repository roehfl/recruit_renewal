# 시스템 자동발송 메일 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 가입 이메일 인증·비밀번호 재발급을 실제 인증번호 메일로 붙이고, 지원서 최종 제출(재제출 포함)마다 제출 완료 메일을 보낸다. 세 메일 모두 기존 발송 이력(`message_send`·`message_recipient`)에 `origin=SYSTEM`으로 남긴다.

**Architecture:** 메시지 종류에 시스템 유형 3개를 더하고, 기동 시 유형별 기본 템플릿을 만든다. `SystemMailService`가 기본 템플릿으로 이력(치환 전 원문)을 먼저 커밋한 뒤 기존 `MessageDispatcher.dispatch`를 동기로 부른다. 인증번호는 `EmailVerificationService`가 발급·확인하고 HTTP 세션에 해시만 둔다(세션 읽기·쓰기는 컨트롤러). 제출 메일은 `ApplicationSubmittedEvent` → 커밋 후 비동기 리스너가 보낸다. 이력 API에 `origin` 필터·응답 필드를 더한다.

**Tech Stack:** Spring Boot 4 · Java 17 · JPA · Spring Security(세션) · JUnit 5/AssertJ/Mockito · Vue 3 `<script setup lang="ts">` · ant-design-vue 4 · Axios · vitest

**설계서:** `docs/superpowers/specs/2026-09-23-system-mail-design.md`

---

## 선행 지식 (구현 전에 읽을 것)

| 문서·코드 | 왜 |
|---|---|
| `docs/superpowers/specs/2026-09-23-system-mail-design.md` | 이 계획의 근거 |
| `docs/domains/auth-account.md` · `message.md` · `message-delivery.md` · `application.md` | 계약·규칙·파일 지도(마지막 태스크에서 갱신) |
| `recruit_back/recruit_backend/AGENTS.md` | 백엔드 규칙(Clock 주입, native query 금지, 예외 → 핸들러) |
| `recruit_front/AGENTS.md` | 프론트 규칙 |
| `{BE}/service/MessageSendService.java` `testSend` | 동기 디스패치 → 수신자 재조회 패턴의 원본 |
| `{BE}/controller/NiceVerificationController.java` · `ApplicantSignUpController.java` | 컨트롤러가 세션을 읽고 서비스에 값으로 넘기는 패턴 |

**주의:**
- 테스트 컨텍스트 재사용: 메일을 실제로 보내는 테스트는 `MessageSendServiceTest`와 같은 조합(`@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")` + `@MockitoBean MailGateway` + `@MockitoBean SmsGateway`)을 쓴다. 메일을 안 보내는 테스트는 `properties`만 쓴다.
- 기동 시 시스템 기본 템플릿 3개가 모든 테스트 컨텍스트에 커밋된 상태로 생긴다. 템플릿 전체 목록을 단언하는 기존 테스트는 시스템 유형을 걸러 본다(Task 3).
- 백엔드 테스트 명령은 `recruit_back/recruit_backend/`에서 PowerShell로 실행한다. 아래 `<FQCN>`만 바꾼다.
  ```powershell
  $env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "<FQCN>" --no-daemon
  ```
- 커밋하지 않는다(사용자가 명시적으로 요청할 때만). 각 태스크는 "검증" 단계로 끝난다.

---

## 파일 구조

### 백엔드 (`{BE}` = `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit`, `{BT}` = 테스트 루트)

| 구분 | 경로 | 책임 |
|---|---|---|
| 수정 | `{BE}/enumeration/MessageType.java` | 시스템 유형 3개 추가, `isSystem()` |
| 수정 | `{BE}/enumeration/MessageVariable.java` | `VERIFICATION_CODE`·`SUBMITTED_AT` 추가, `JOB_POSTING_TITLE` 허용 종류 조정 |
| 생성 | `{BE}/enumeration/MessageOrigin.java` | 발송 구분 `ADMIN`·`SYSTEM` |
| 생성 | `{BE}/enumeration/SystemMailOutcome.java` | 시스템 메일 결과 `ACCEPTED`·`FAILED`·`NO_TEMPLATE` |
| 생성 | `{BE}/enumeration/EmailVerificationPurpose.java` | 인증 용도 `SIGNUP`·`PASSWORD_RESET` → 메일 종류 |
| 수정 | `{BE}/service/MessageVariableFormatter.java` | 새 변수 2개 switch case(빈 값) |
| 수정 | `{BE}/service/MessageTargetService.java` | 시스템 유형 대상 조회 400, `firstNonBlank` 패키지 공개 |
| 수정 | `{BE}/domain/entity/MessageSend.java` | `origin` 컬럼, `job_posting_id` NULL 허용, `createSystem` |
| 생성 | `recruit_back/recruit_backend/docs/ops/message-send-origin-ddl.sql` | 운영 DDL |
| 수정 | `{BE}/service/MessageTemplateService.java` | 시스템 템플릿 규칙(메일 필수·SMS 저장 안 함·`#{인증번호}` 필수·기본 삭제/해제 차단) |
| 생성 | `{BE}/service/SystemMessageTemplateInitializer.java` | 기동 시 시스템 유형 기본 템플릿 생성 |
| 생성 | `{BE}/service/SystemMailService.java` | 기본 템플릿으로 이력 저장·커밋 → 동기 디스패치 → 결과 판정 |
| 생성 | `{BE}/service/EmailVerificationService.java` | 인증번호 발급·확인·확인 후 10분 검사·발송 |
| 생성 | `{BE}/service/EmailVerificationState.java` | 세션 값(번호 해시·만료·실패 수·발송·확인 시각) |
| 생성 | `{BE}/exception/InvalidEmailVerificationException.java` | 400 |
| 수정 | `{BE}/exception/GlobalExceptionHandler.java` | 새 예외 400 매핑 |
| 생성 | `{BE}/dto/request/EmailVerificationSendRequest.java` | `{ email }` |
| 생성 | `{BE}/dto/request/EmailVerificationConfirmRequest.java` | `{ email, code }` |
| 생성 | `{BE}/dto/request/ApplicantPasswordResetRequest.java` | `{ email, newPassword }` |
| 수정 | `{BE}/service/ApplicantSignUpService.java` | 가입 인증번호 발송(가입된 이메일 거부) |
| 수정 | `{BE}/controller/ApplicantSignUpController.java` | 인증 API 2개, 가입 시 이메일 인증 강제 |
| 수정 | `{BE}/domain/repository/ApplicantRepository.java` | `findByEmail` |
| 수정 | `{BE}/service/ApplicantAccountRecoveryService.java` | 재설정 인증번호 발송·새 비밀번호 저장 |
| 수정 | `{BE}/controller/ApplicantAccountRecoveryController.java` | 재발급 API 3개 |
| 수정 | `{BE}/config/SecurityConfig.java` | 공개 경로 5개 |
| 생성 | `{BE}/service/ApplicationSubmittedEvent.java` | 제출 성공 이벤트 |
| 수정 | `{BE}/service/JobApplicationService.java` | `submit` 끝에서 이벤트 발행 |
| 생성 | `{BE}/service/ApplicationSubmittedMailListener.java` | 커밋 후 비동기 제출 완료 메일 |
| 수정 | `{BE}/dto/condition/MessageHistoryCondition.java` | `origin` |
| 수정 | `{BE}/domain/repository/MessageSendRepository.java` | 검색에 `origin` |
| 수정 | `{BE}/service/MessageHistoryService.java` | `origin` 전달 |
| 수정 | `{BE}/controller/MessageHistoryAdminController.java` | `origin` 파라미터 |
| 수정 | `{BE}/dto/response/MessageSendSummaryResponse.java` | `origin`, 공고 null 안전 |
| 수정 | `{BE}/dto/response/MessageSendDetailResponse.java` | `origin` |
| 수정 | `{BE}/service/MessageSendService.java` | 시스템 유형 발송·테스트 발송 400 |

### 백엔드 테스트

| 구분 | 경로 |
|---|---|
| 생성 | `{BT}/domain/entity/MessageSendTest.java` |
| 생성 | `{BT}/service/SystemMailServiceTest.java` |
| 생성 | `{BT}/service/EmailVerificationServiceTest.java` |
| 생성 | `{BT}/controller/ApplicantEmailVerificationControllerTest.java` |
| 생성 | `{BT}/controller/ApplicantPasswordResetControllerTest.java` |
| 생성 | `{BT}/service/ApplicationSubmittedMailListenerTest.java` |
| 수정 | `{BT}/service/MessageTemplateServiceTest.java` · `{BT}/controller/MessageTemplateAdminControllerTest.java` · `{BT}/service/MessageTargetServiceTest.java` · `{BT}/service/ApplicantSignUpServiceTest.java` · `{BT}/controller/ApplicantSignUpControllerTest.java` · `{BT}/controller/ApplicantSignUpNiceIntegrationTest.java` · `{BT}/service/ApplicantAccountRecoveryServiceTest.java` · `{BT}/config/SecurityConfigTest.java` · `{BT}/service/JobApplicationServiceTest.java` · `{BT}/service/MessageHistoryServiceTest.java` · `{BT}/controller/MessageHistoryAdminControllerTest.java` · `{BT}/service/MessageSendServiceTest.java` |

### 프론트 (`{FE}` = `recruit_front/src`)

| 구분 | 경로 | 책임 |
|---|---|---|
| 수정 | `{FE}/types/application.ts` | `EmailVerificationRequest`·`PasswordResetRequest` |
| 수정 | `{FE}/api/applicationApi.ts` | 인증·재발급 API 5개 |
| 수정 | `{FE}/types/admin/message.ts` | `SystemMessageType`·`AnyMessageType`·`MessageOrigin`, 이력 타입 `origin` |
| 수정 | `{FE}/views/admin/message/messageTypes.ts` | `SYSTEM_MESSAGE_TYPES`·`ALL_MESSAGE_TYPES`·`isSystemMessageType` |
| 수정 | `{FE}/views/admin/message/messageHistory.ts` | `MESSAGE_ORIGIN_LABEL` |
| 생성 | `{FE}/views/admin/message/__tests__/messageTypes.spec.ts` | 종류 메타 테스트 |
| 수정 | `{FE}/views/admin/message/__tests__/messageHistory.spec.ts` | 픽스처에 `origin` |
| 수정 | `{FE}/views/applicant/SignupView.vue` | 인증 실연동 |
| 수정 | `{FE}/views/applicant/AccountRecovery.vue` | 비밀번호 탭 3단계 |
| 수정 | `{FE}/views/admin/message/AdminMessageHistoryView.vue` | 발송 구분 열·필터, 시스템 종류 필터, 공고 `-` |
| 수정 | `{FE}/views/admin/message/MessageHistoryDrawer.vue` | 공고 `-` |
| 수정 | `{FE}/views/admin/message/AdminMessageTemplateView.vue` | 시스템 종류 표시, SMS 숨김, 기본 템플릿 삭제 버튼 숨김 |

발송 화면(`AdminMessageSendView.vue`·`MessageTypePicker.vue`)은 `MESSAGE_TYPES`(관리자 종류 5개)를 그대로 쓰므로 수정하지 않는다 — 시스템 3종은 자동으로 빠진다.

### 문서

| 구분 | 경로 |
|---|---|
| 수정 | `docs/domains/auth-account.md` · `docs/domains/message.md` · `docs/domains/message-delivery.md` · `docs/domains/application.md` · `docs/domains/_index.md` |

---

## Task 1: 메시지 종류·변수 확장

**Files:**
- Modify: `{BE}/enumeration/MessageType.java`
- Modify: `{BE}/enumeration/MessageVariable.java`
- Create: `{BE}/enumeration/MessageOrigin.java`
- Modify: `{BE}/service/MessageVariableFormatter.java`
- Modify: `{BE}/service/MessageTargetService.java`
- Test: `{BT}/service/MessageTemplateServiceTest.java`, `{BT}/controller/MessageTemplateAdminControllerTest.java`, `{BT}/service/MessageTargetServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/service/MessageTemplateServiceTest.java`의 `변수_목록은_12개이고_종류별_허용을_담는다`를 아래로 **교체**하고, 테스트 1개를 추가한다. import에 `java.util.Arrays`를 추가한다.

```java
    @Test
    void 변수_목록은_14개이고_종류별_허용을_담는다() {
        List<MessageVariableResponse> variables = messageTemplateService.getVariables();

        assertThat(variables).hasSize(14);
        assertThat(variables.get(0).key()).isEqualTo("이름");
        assertThat(variables).filteredOn(variable -> variable.key().equals("도착시각"))
                .singleElement()
                .satisfies(variable -> assertThat(variable.types()).containsExactly(MessageType.INTERVIEW_SCHEDULE));
        assertThat(variables).filteredOn(variable -> variable.key().equals("인증번호"))
                .singleElement()
                .satisfies(variable -> assertThat(variable.types())
                        .containsExactly(MessageType.SIGNUP_VERIFICATION, MessageType.PASSWORD_RESET));
        assertThat(variables).filteredOn(variable -> variable.key().equals("제출일시"))
                .singleElement()
                .satisfies(variable -> assertThat(variable.types()).containsExactly(MessageType.APPLICATION_SUBMITTED));
        assertThat(variables).filteredOn(variable -> variable.key().equals("공고명"))
                .singleElement()
                .satisfies(variable -> assertThat(variable.types())
                        .contains(MessageType.FREE, MessageType.APPLICATION_SUBMITTED)
                        .doesNotContain(MessageType.SIGNUP_VERIFICATION, MessageType.PASSWORD_RESET));
    }

    @Test
    void 시스템_자동발송_유형은_3개다() {
        assertThat(Arrays.stream(MessageType.values()).filter(MessageType::isSystem))
                .containsExactly(MessageType.SIGNUP_VERIFICATION, MessageType.PASSWORD_RESET,
                        MessageType.APPLICATION_SUBMITTED);
    }
```

`{BT}/controller/MessageTemplateAdminControllerTest.java`의 `변수_카탈로그를_조회한다`에서 `.value(12)`를 `.value(14)`로 바꾼다.

`{BT}/service/MessageTargetServiceTest.java`에 추가한다(이미 `InvalidMessageException`·`assertThatThrownBy` import가 있다).

```java
    @Test
    void 시스템_자동발송_유형은_대상을_조회할_수_없다() {
        assertThatThrownBy(() -> messageTargetService.getTargets(
                condition(MessageType.SIGNUP_VERIFICATION, null, null, null, null)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("시스템 자동발송 유형은 직접 보낼 수 없습니다.");
    }
```

- [ ] **Step 2: 실패 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageTemplateServiceTest" --tests "com.shinyoung.recruit.controller.MessageTemplateAdminControllerTest" --tests "com.shinyoung.recruit.service.MessageTargetServiceTest" --no-daemon
```
Expected: FAIL — 컴파일 오류(`MessageType.SIGNUP_VERIFICATION`·`isSystem` 없음).

- [ ] **Step 3: 구현**

`{BE}/enumeration/MessageType.java` 전체:

```java
package com.shinyoung.recruit.enumeration;

/**
 * 메시지 종류. 화면의 묶음(공고 관련·면접 안내·기타)은 프론트 표시용이다.
 * 뒤의 3개는 시스템 자동발송 종류다. 관리자 발송·테스트 발송·대상 조회에는 쓸 수 없고 템플릿만 관리한다.
 */
public enum MessageType {
    RESULT_ANNOUNCEMENT,
    DEADLINE_REMINDER,
    INTERVIEW_SCHEDULE,
    INTERVIEW_NOTICE,
    FREE,
    SIGNUP_VERIFICATION,
    PASSWORD_RESET,
    APPLICATION_SUBMITTED;

    /** 시스템 자동발송 종류(가입 인증·비밀번호 재설정·제출 완료)인지. */
    public boolean isSystem() {
        return this == SIGNUP_VERIFICATION || this == PASSWORD_RESET || this == APPLICATION_SUBMITTED;
    }
}
```

`{BE}/enumeration/MessageVariable.java` — static import와 상수 목록을 아래로 바꾼다(나머지 필드·메서드는 그대로).

```java
import static com.shinyoung.recruit.enumeration.MessageType.APPLICATION_SUBMITTED;
import static com.shinyoung.recruit.enumeration.MessageType.DEADLINE_REMINDER;
import static com.shinyoung.recruit.enumeration.MessageType.FREE;
import static com.shinyoung.recruit.enumeration.MessageType.INTERVIEW_NOTICE;
import static com.shinyoung.recruit.enumeration.MessageType.INTERVIEW_SCHEDULE;
import static com.shinyoung.recruit.enumeration.MessageType.PASSWORD_RESET;
import static com.shinyoung.recruit.enumeration.MessageType.RESULT_ANNOUNCEMENT;
import static com.shinyoung.recruit.enumeration.MessageType.SIGNUP_VERIFICATION;

/**
 * 본문의 #{키} 변수. 키는 관리자가 읽기 쉽게 한글 이름 그대로 쓴다.
 * 종류별 허용 목록의 단일 출처이며 프론트는 변수 카탈로그 API로 받아 쓴다.
 */
public enum MessageVariable {
    NAME("이름", "지원자 이름", EnumSet.allOf(MessageType.class)),
    JOB_POSTING_TITLE("공고명", "공고 제목", EnumSet.of(RESULT_ANNOUNCEMENT, DEADLINE_REMINDER,
            INTERVIEW_SCHEDULE, INTERVIEW_NOTICE, FREE, APPLICATION_SUBMITTED)),
    SITE_URL("채용사이트", "채용 사이트 주소", EnumSet.allOf(MessageType.class)),
    STAGE_NAME("전형명", "전형 이름", EnumSet.of(RESULT_ANNOUNCEMENT, INTERVIEW_SCHEDULE, INTERVIEW_NOTICE)),
    DEADLINE("마감일시", "서류 접수 마감 일시", EnumSet.of(DEADLINE_REMINDER)),
    D_DAY("남은기간", "마감까지 남은 기간(D-n)", EnumSet.of(DEADLINE_REMINDER)),
    INTERVIEW_DATE_TIME("면접일시", "면접 시작 일시", EnumSet.of(INTERVIEW_SCHEDULE, INTERVIEW_NOTICE)),
    ARRIVAL_TIME("도착시각", "면접 도착 시각", EnumSet.of(INTERVIEW_SCHEDULE)),
    INTERVIEW_PLACE("면접장소", "면접 장소·호실", EnumSet.of(INTERVIEW_SCHEDULE, INTERVIEW_NOTICE)),
    INTERVIEW_METHOD("면접방식", "면접 방식", EnumSet.of(INTERVIEW_SCHEDULE)),
    MEETING_URL("접속링크", "온라인 면접 접속 링크", EnumSet.of(INTERVIEW_SCHEDULE, INTERVIEW_NOTICE)),
    GROUP("조", "면접 조", EnumSet.of(INTERVIEW_SCHEDULE)),
    VERIFICATION_CODE("인증번호", "메일 인증번호", EnumSet.of(SIGNUP_VERIFICATION, PASSWORD_RESET)),
    SUBMITTED_AT("제출일시", "지원서 제출 일시", EnumSet.of(APPLICATION_SUBMITTED));
```

`{BE}/enumeration/MessageOrigin.java` 생성:

```java
package com.shinyoung.recruit.enumeration;

/** 발송 구분. ADMIN 관리자 발송(발송 화면), SYSTEM 시스템 자동발송(가입 인증·비밀번호 재설정·제출 완료). */
public enum MessageOrigin {
    ADMIN,
    SYSTEM
}
```

`{BE}/service/MessageVariableFormatter.java` — `valueOf`의 switch 마지막 `case GROUP -> ...;` 아래에 추가한다.

```java
            // 시스템 자동발송 전용 변수. 관리자 종류에는 허용되지 않아 여기서 값이 쓰이지 않는다(값은 SystemMailService 호출자가 넣는다).
            case VERIFICATION_CODE, SUBMITTED_AT -> "";
```

`{BE}/service/MessageTargetService.java`:
1. `GROUP_ORDER` 상수 아래에 추가:
```java
    static final String SYSTEM_TYPE_REJECTED = "시스템 자동발송 유형은 직접 보낼 수 없습니다.";
```
2. `getTargets` 첫 줄에 추가:
```java
        if (condition.type().isSystem()) {
            throw new InvalidMessageException(SYSTEM_TYPE_REJECTED);
        }
```
3. `getTargets`의 switch 식 `case FREE -> freeTargets(condition, jobPosting, stage);` 아래에 추가(switch 식은 모든 값을 다뤄야 한다):
```java
            case SIGNUP_VERIFICATION, PASSWORD_RESET, APPLICATION_SUBMITTED ->
                    throw new InvalidMessageException(SYSTEM_TYPE_REJECTED);
```

- [ ] **Step 4: 통과 확인**

Step 2 명령을 다시 실행한다. Expected: PASS.

- [ ] **Step 5: 검증**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageVariableFormatterTest" --tests "com.shinyoung.recruit.service.MessageRendererTest" --no-daemon
```
Expected: PASS(관리자 종류의 변수 목록은 바뀌지 않았다).

---

## Task 2: `MessageSend` 발송 구분·공고 선택값 + 운영 DDL

**Files:**
- Modify: `{BE}/domain/entity/MessageSend.java`
- Create: `recruit_back/recruit_backend/docs/ops/message-send-origin-ddl.sql`
- Test: `{BT}/domain/entity/MessageSendTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/domain/entity/MessageSendTest.java`:

```java
package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.MessageOrigin;
import com.shinyoung.recruit.enumeration.MessageType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MessageSendTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 23, 10, 0);

    @Test
    void 관리자_발송은_ADMIN으로_만든다() {
        MessageSend send = MessageSend.create(MessageType.FREE, false, null, null, null, null, null,
                true, false, "제목", "본문", null, "hr.kim", "김인사", 1, NOW);

        assertThat(send.getOrigin()).isEqualTo(MessageOrigin.ADMIN);
    }

    @Test
    void 시스템_발송은_템플릿_원문과_시스템_발송자로_만든다() {
        MessageTemplate template = MessageTemplate.create(MessageType.SIGNUP_VERIFICATION, "회원가입 인증 메일", true,
                "[신영증권 채용] 회원가입 이메일 인증번호", "인증번호: #{인증번호}", null);

        MessageSend send = MessageSend.createSystem(MessageType.SIGNUP_VERIFICATION, null, template, NOW);

        assertThat(send.getOrigin()).isEqualTo(MessageOrigin.SYSTEM);
        assertThat(send.getType()).isEqualTo(MessageType.SIGNUP_VERIFICATION);
        assertThat(send.isTest()).isFalse();
        assertThat(send.getJobPosting()).isNull();
        assertThat(send.getStage()).isNull();
        assertThat(send.getConditionSummary()).isNull();
        assertThat(send.getTemplateName()).isEqualTo("회원가입 인증 메일");
        assertThat(send.isMailEnabled()).isTrue();
        assertThat(send.isSmsEnabled()).isFalse();
        assertThat(send.getMailSubject()).isEqualTo("[신영증권 채용] 회원가입 이메일 인증번호");
        assertThat(send.getMailBody()).isEqualTo("인증번호: #{인증번호}");
        assertThat(send.getSmsBody()).isNull();
        assertThat(send.getSenderLoginId()).isEqualTo("SYSTEM");
        assertThat(send.getSenderName()).isEqualTo("시스템");
        assertThat(send.getRecipientCount()).isEqualTo(1);
        assertThat(send.getRequestedAt()).isEqualTo(NOW);
    }
}
```

- [ ] **Step 2: 실패 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.domain.entity.MessageSendTest" --no-daemon
```
Expected: FAIL — 컴파일 오류(`getOrigin`·`createSystem` 없음).

- [ ] **Step 3: 구현**

`{BE}/domain/entity/MessageSend.java` 전체:

```java
package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.MessageOrigin;
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

/**
 * 메시지 발송 요청 1회. 치환 전 원문·조건 요약·발송자를 보관한다(설계서 8절).
 * 상태·채널별 건수는 저장하지 않고 조회할 때 수신자 채널 상태로 계산한다(7.4, MessageHistoryService).
 * 시스템 자동발송(origin = SYSTEM)은 공고가 없을 수 있다(가입 인증·비밀번호 재설정). 관리자 발송은 서비스가 공고를 필수로 검증한다.
 */
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

    public static final String SYSTEM_SENDER_LOGIN_ID = "SYSTEM";
    public static final String SYSTEM_SENDER_NAME = "시스템";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 40)
    private MessageType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageOrigin origin;

    @Column(name = "test_send", nullable = false)
    private boolean test;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id")
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

    @Column(nullable = false)
    private int recipientCount;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    /** 관리자 발송(발송 화면의 발송·테스트 발송). */
    public static MessageSend create(MessageType type, boolean test, JobPosting jobPosting, Stage stage,
                                     String conditionSummary, Long templateId, String templateName,
                                     boolean mailEnabled, boolean smsEnabled,
                                     String mailSubject, String mailBody, String smsBody,
                                     String senderLoginId, String senderName,
                                     int recipientCount, LocalDateTime requestedAt) {
        MessageSend send = new MessageSend();
        send.type = type;
        send.origin = MessageOrigin.ADMIN;
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
        send.recipientCount = recipientCount;
        send.requestedAt = requestedAt;
        return send;
    }

    /**
     * 시스템 자동발송 1통(수신자 1명, 메일만). 원문은 사용한 기본 템플릿의 치환 전 제목·본문이다.
     * 인증번호 같은 치환 값은 여기에 들어가지 않는다(설계서 4절).
     */
    public static MessageSend createSystem(MessageType type, JobPosting jobPosting, MessageTemplate template,
                                           LocalDateTime requestedAt) {
        MessageSend send = create(type, false, jobPosting, null, null, template.getId(), template.getName(),
                true, false, template.getMailSubject(), template.getMailBody(), null,
                SYSTEM_SENDER_LOGIN_ID, SYSTEM_SENDER_NAME, 1, requestedAt);
        send.origin = MessageOrigin.SYSTEM;
        return send;
    }
}
```

`recruit_back/recruit_backend/docs/ops/message-send-origin-ddl.sql` 생성:

```sql
-- 시스템 자동발송 메일(2026-09-23): message_send 발송 구분(origin) 추가 · 공고 없는 발송 허용 수동 반영 DDL
--
-- 배경
--   - 본 프로젝트는 Flyway/Liquibase 등 migration framework를 사용하지 않는다(스키마는 Hibernate ddl-auto 생성).
--   - 신규/개발 H2(create-drop, jdbc:h2:mem 또는 새 파일 DB)에서는 엔티티대로 자동 생성되므로 본 SQL은 불필요하다.
--   - 기존 데이터가 있는 영속 DB(운영 후보 MariaDB, 또는 기존 행이 쌓인 dev H2 파일 DB)에는 아래 DDL을 1회 수동 반영한다.
--     (엔티티: `@Enumerated(STRING) @Column(nullable = false, length = 20) MessageOrigin origin`,
--      `@JoinColumn(name = "job_posting_id")` NULL 허용)
--
-- 주의
--   - 기존 발송 이력은 모두 관리자 발송이므로 'ADMIN'으로 채운다. 적용 후 애플리케이션을 재기동한다.
--   - 가입 인증·비밀번호 재설정 메일 이력은 공고가 없어 job_posting_id 가 NULL 이다.
--   - 적용 전 백업을 권장한다.

-- MariaDB (운영 후보) / H2 (MODE=MySQL)
ALTER TABLE message_send
    ADD COLUMN origin VARCHAR(20) NOT NULL DEFAULT 'ADMIN';

UPDATE message_send SET origin = 'ADMIN' WHERE origin IS NULL OR origin = '';

ALTER TABLE message_send
    MODIFY COLUMN job_posting_id BIGINT NULL;

-- 참고: DEFAULT 적용이 다른 환경이면 아래로 분리 적용한다.
-- ALTER TABLE message_send ADD COLUMN origin VARCHAR(20);
-- UPDATE message_send SET origin = 'ADMIN' WHERE origin IS NULL;
-- ALTER TABLE message_send MODIFY COLUMN origin VARCHAR(20) NOT NULL;
```

- [ ] **Step 4: 통과 확인**

Step 2 명령. Expected: PASS.

- [ ] **Step 5: 검증**

기존 `MessageSend.create` 사용처(서비스 1곳·테스트 5곳)는 시그니처가 같아 그대로 컴파일된다.
```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageHistoryServiceTest" --tests "com.shinyoung.recruit.service.MessageDispatchRecorderTest" --tests "com.shinyoung.recruit.service.MessageRecipientDynamicUpdateTest" --no-daemon
```
Expected: PASS.

---

## Task 3: 시스템 템플릿 규칙 + 기동 시 기본 템플릿

**Files:**
- Modify: `{BE}/service/MessageTemplateService.java`
- Create: `{BE}/service/SystemMessageTemplateInitializer.java`
- Test: `{BT}/service/MessageTemplateServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/service/MessageTemplateServiceTest.java`:

1. 필드 추가(import `com.shinyoung.recruit.domain.entity.MessageTemplate`, `com.shinyoung.recruit.domain.repository.MessageTemplateRepository`):
```java
    @Autowired
    private MessageTemplateRepository messageTemplateRepository;
```

2. `목록은_종류_기본_이름_순이고_종류로_거를_수_있다`의 `all` 단언을 아래로 바꾼다(기동 시 만든 시스템 기본 템플릿이 목록 끝에 붙는다).
```java
        assertThat(all).filteredOn(template -> !template.type().isSystem())
                .extracting(MessageTemplateResponse::name)
                .containsExactly("중립 안내", "불합격 안내", "합격 안내", "설명회 초대");
```

3. 테스트 추가:
```java
    @Test
    void 시스템_유형_기본_템플릿은_기동_시_만들어진다() {
        MessageTemplate signup = systemDefault(MessageType.SIGNUP_VERIFICATION);
        MessageTemplate reset = systemDefault(MessageType.PASSWORD_RESET);
        MessageTemplate submitted = systemDefault(MessageType.APPLICATION_SUBMITTED);

        assertThat(signup.getMailSubject()).isEqualTo("[신영증권 채용] 회원가입 이메일 인증번호");
        assertThat(signup.getMailBody()).contains("인증번호: #{인증번호}");
        assertThat(reset.getMailSubject()).isEqualTo("[신영증권 채용] 비밀번호 재설정 인증번호");
        assertThat(reset.getMailBody()).startsWith("#{이름}님,").contains("#{인증번호}");
        assertThat(submitted.getMailSubject()).isEqualTo("[신영증권 채용] #{공고명} 지원서 제출 완료 안내");
        assertThat(submitted.getMailBody()).contains("#{제출일시}").contains("#{채용사이트}");
        assertThat(signup.getSmsBody()).isNull();
    }

    @Test
    void 시스템_기본_템플릿은_삭제할_수_없다() {
        MessageTemplate signup = systemDefault(MessageType.SIGNUP_VERIFICATION);

        assertThatThrownBy(() -> messageTemplateService.deleteTemplate(signup.getId()))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("시스템 기본 템플릿은 삭제할 수 없습니다.");
    }

    @Test
    void 시스템_기본_템플릿은_기본을_해제하거나_종류를_바꿀_수_없다() {
        MessageTemplate reset = systemDefault(MessageType.PASSWORD_RESET);

        assertThatThrownBy(() -> messageTemplateService.updateTemplate(reset.getId(), new MessageTemplateSaveRequest(
                MessageType.PASSWORD_RESET, reset.getName(), false, reset.getMailSubject(), reset.getMailBody(), null)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("시스템 기본 템플릿은 기본을 해제할 수 없습니다.");
        assertThatThrownBy(() -> messageTemplateService.updateTemplate(reset.getId(), new MessageTemplateSaveRequest(
                MessageType.SIGNUP_VERIFICATION, reset.getName(), true, reset.getMailSubject(), reset.getMailBody(), null)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("시스템 기본 템플릿은 기본을 해제할 수 없습니다.");
    }

    @Test
    void 다른_템플릿을_기본으로_지정하면_시스템_기본이_바뀐다() {
        MessageTemplate old = systemDefault(MessageType.SIGNUP_VERIFICATION);

        MessageTemplateResponse created = messageTemplateService.createTemplate(new MessageTemplateSaveRequest(
                MessageType.SIGNUP_VERIFICATION, "새 인증 메일", true, "[신영증권] 인증번호", "번호: #{인증번호}", null));

        assertThat(created.defaultTemplate()).isTrue();
        assertThat(messageTemplateService.getTemplate(old.getId()).defaultTemplate()).isFalse();
        messageTemplateService.deleteTemplate(old.getId());
    }

    @Test
    void 인증_메일에는_인증번호_변수가_있어야_한다() {
        assertThatThrownBy(() -> messageTemplateService.createTemplate(new MessageTemplateSaveRequest(
                MessageType.PASSWORD_RESET, "번호 없는 메일", false, "[신영증권] 안내", "#{이름}님 안내", null)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("인증 메일에는 #{인증번호}가 있어야 합니다.");
    }

    @Test
    void 시스템_유형은_메일이_필수이고_SMS는_저장하지_않는다() {
        assertThatThrownBy(() -> messageTemplateService.createTemplate(new MessageTemplateSaveRequest(
                MessageType.APPLICATION_SUBMITTED, "문자만", false, null, null, "#{이름}님 제출 완료")))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("시스템 자동발송 템플릿은 메일 제목과 본문을 입력해야 합니다.");

        MessageTemplateResponse saved = messageTemplateService.createTemplate(new MessageTemplateSaveRequest(
                MessageType.APPLICATION_SUBMITTED, "제출 안내 2", false, "#{공고명} 제출", "#{이름}님 제출 완료", "무시될 문자"));

        assertThat(saved.smsBody()).isNull();
    }

    private MessageTemplate systemDefault(MessageType type) {
        List<MessageTemplate> defaults = messageTemplateRepository.findByTypeAndDefaultTemplateTrue(type);
        assertThat(defaults).hasSize(1);
        return defaults.get(0);
    }
```

- [ ] **Step 2: 실패 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageTemplateServiceTest" --no-daemon
```
Expected: FAIL — `시스템_유형_기본_템플릿은_기동_시_만들어진다`(hasSize(1)이 0), 삭제·해제·`#{인증번호}`·메일 필수 테스트가 예외 없이 끝나 실패.

- [ ] **Step 3: 구현**

`{BE}/service/SystemMessageTemplateInitializer.java` 생성:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.MessageTemplate;
import com.shinyoung.recruit.domain.repository.MessageTemplateRepository;
import com.shinyoung.recruit.enumeration.MessageType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 기동 시 시스템 자동발송 종류마다 기본 템플릿이 없으면 초안으로 만든다(설계서 5·7절).
 * 문구는 관리자가 템플릿 화면에서 고칠 수 있다. 기본 템플릿은 삭제·기본 해제가 막혀 있다(MessageTemplateService).
 */
@Component
@RequiredArgsConstructor
public class SystemMessageTemplateInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemMessageTemplateInitializer.class);

    private static final Map<MessageType, Draft> DRAFTS = Map.of(
            MessageType.SIGNUP_VERIFICATION, new Draft(
                    "회원가입 인증 메일",
                    "[신영증권 채용] 회원가입 이메일 인증번호",
                    "아래 인증번호를 회원가입 화면에 입력해 주세요.\n\n인증번호: #{인증번호}\n\n인증번호는 5분 동안 유효합니다."),
            MessageType.PASSWORD_RESET, new Draft(
                    "비밀번호 재설정 인증 메일",
                    "[신영증권 채용] 비밀번호 재설정 인증번호",
                    "#{이름}님, 아래 인증번호를 비밀번호 재설정 화면에 입력해 주세요.\n\n인증번호: #{인증번호}\n\n"
                            + "인증번호는 5분 동안 유효합니다. 본인이 요청하지 않았다면 이 메일을 무시해 주세요."),
            MessageType.APPLICATION_SUBMITTED, new Draft(
                    "지원서 제출 완료 안내",
                    "[신영증권 채용] #{공고명} 지원서 제출 완료 안내",
                    "#{이름}님, #{공고명} 지원서가 #{제출일시}에 제출되었습니다.\n\n"
                            + "지원 현황은 채용 사이트(#{채용사이트})에서 확인할 수 있습니다.")
    );

    private final MessageTemplateRepository messageTemplateRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        DRAFTS.forEach((type, draft) -> {
            if (messageTemplateRepository.findByTypeAndDefaultTemplateTrue(type).isEmpty()) {
                messageTemplateRepository.save(MessageTemplate.create(
                        type, draft.name(), true, draft.subject(), draft.body(), null));
                log.info("시스템 기본 메시지 템플릿을 만들었습니다: type={}", type);
            }
        });
    }

    private record Draft(String name, String subject, String body) {
    }
}
```

`{BE}/service/MessageTemplateService.java` 전체:

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
    private static final String VERIFICATION_CODE_TOKEN = "#{" + MessageVariable.VERIFICATION_CODE.getKey() + "}";
    private static final String SYSTEM_DEFAULT_LOCKED = "시스템 기본 템플릿은 기본을 해제할 수 없습니다.";

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
        // 시스템 기본 템플릿은 자동발송이 쓰므로 스스로 기본에서 빠지면 안 된다. 다른 템플릿을 기본으로 지정하면 아래 clearDefault 로 바뀐다.
        if (isSystemDefault(template) && (!request.defaultTemplate() || request.type() != template.getType())) {
            throw new InvalidMessageException(SYSTEM_DEFAULT_LOCKED);
        }
        Content content = validate(request);
        if (request.defaultTemplate()) {
            clearDefault(request.type(), templateId);
        }
        template.update(
                request.type(), request.name().trim(), request.defaultTemplate(),
                content.mailSubject(), content.mailBody(), content.smsBody()
        );
        // @LastModifiedDate 는 flush 시점에 채워지므로 응답 생성 전에 명시적으로 flush 한다.
        messageTemplateRepository.flush();
        return MessageTemplateResponse.from(template);
    }

    @Transactional
    public void deleteTemplate(Long templateId) {
        MessageTemplate template = findTemplate(templateId);
        if (isSystemDefault(template)) {
            throw new InvalidMessageException("시스템 기본 템플릿은 삭제할 수 없습니다.");
        }
        messageTemplateRepository.delete(template);
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

    private static boolean isSystemDefault(MessageTemplate template) {
        return template.isDefaultTemplate() && template.getType().isSystem();
    }

    private Content validate(MessageTemplateSaveRequest request) {
        boolean system = request.type().isSystem();
        Content content = new Content(
                blankToNull(request.mailSubject()),
                blankToNull(request.mailBody()),
                system ? null : blankToNull(request.smsBody())
        );
        if (system && (content.mailSubject() == null || content.mailBody() == null)) {
            throw new InvalidMessageException("시스템 자동발송 템플릿은 메일 제목과 본문을 입력해야 합니다.");
        }
        if ((content.mailSubject() == null) != (content.mailBody() == null)) {
            throw new InvalidMessageException("메일은 제목과 본문을 함께 입력해야 합니다.");
        }
        if (content.mailSubject() == null && content.smsBody() == null) {
            throw new InvalidMessageException("메일 또는 SMS 내용을 입력해야 합니다.");
        }
        messageRenderer.validateVariables(request.type(), content.mailSubject(), content.mailBody(), content.smsBody());
        if (MessageVariable.VERIFICATION_CODE.isAllowedFor(request.type())
                && !content.mailSubject().contains(VERIFICATION_CODE_TOKEN)
                && !content.mailBody().contains(VERIFICATION_CODE_TOKEN)) {
            throw new InvalidMessageException("인증 메일에는 #{인증번호}가 있어야 합니다.");
        }
        return content;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record Content(String mailSubject, String mailBody, String smsBody) {
    }
}
```

- [ ] **Step 4: 통과 확인**

Step 2 명령. Expected: PASS.

- [ ] **Step 5: 검증**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.MessageTemplateAdminControllerTest" --tests "com.shinyoung.recruit.service.MessageSendServiceTest" --no-daemon
```
Expected: PASS(시드 템플릿이 생겨도 관리자 종류 조회·발송은 영향 없음).

---

## Task 4: `SystemMailService`

**Files:**
- Create: `{BE}/enumeration/SystemMailOutcome.java`
- Create: `{BE}/service/SystemMailService.java`
- Test: `{BT}/service/SystemMailServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/service/SystemMailServiceTest.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.domain.repository.MessageTemplateRepository;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.MessageOrigin;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.SystemMailOutcome;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class SystemMailServiceTest {

    @Autowired
    private SystemMailService systemMailService;
    @Autowired
    private MessageSendRepository messageSendRepository;
    @Autowired
    private MessageRecipientRepository messageRecipientRepository;
    @Autowired
    private MessageTemplateRepository messageTemplateRepository;

    @MockitoBean
    private MailGateway mailGateway;
    @MockitoBean
    private SmsGateway smsGateway;

    @Test
    void 인증_메일은_공고_없이_시스템_이력으로_남기고_원문에는_인증번호가_없다() {
        when(mailGateway.send(any(), anyList(), anyList())).thenReturn(GatewayResult.accepted("TX-SYSTEM-1"));

        SystemMailOutcome outcome = systemMailService.send(MessageType.SIGNUP_VERIFICATION, "applicant@example.com", "",
                Map.of("인증번호", "123456"), null, null);

        assertThat(outcome).isEqualTo(SystemMailOutcome.ACCEPTED);
        ArgumentCaptor<MailMessage> mail = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailGateway).send(mail.capture(), eq(List.of("applicant@example.com")), eq(List.of("")));
        assertThat(mail.getValue().subject()).isEqualTo("[신영증권 채용] 회원가입 이메일 인증번호");
        assertThat(mail.getValue().text()).contains("인증번호: 123456");

        MessageSend send = latestSend(MessageType.SIGNUP_VERIFICATION);
        assertThat(send.getOrigin()).isEqualTo(MessageOrigin.SYSTEM);
        assertThat(send.getJobPosting()).isNull();
        assertThat(send.getSenderLoginId()).isEqualTo("SYSTEM");
        assertThat(send.getMailSubject()).doesNotContain("123456");
        assertThat(send.getMailBody()).contains("#{인증번호}").doesNotContain("123456");
        MessageRecipient recipient = messageRecipientRepository.findByMessageSendIdOrderByIdAsc(send.getId()).get(0);
        assertThat(recipient.getJobApplication()).isNull();
        assertThat(recipient.getEmail()).isEqualTo("applicant@example.com");
        assertThat(recipient.getMailStatus()).isEqualTo(MessageDeliveryStatus.REQUESTED);
        assertThat(recipient.getMailTransactionId()).isEqualTo("TX-SYSTEM-1");
        assertThat(recipient.getSmsStatus()).isEqualTo(MessageDeliveryStatus.SKIPPED);
        assertThat(recipient.getSmsFailureReason()).isEqualTo(MessageContacts.CHANNEL_OFF);
    }

    @Test
    void 게이트웨이가_접수하지_않으면_FAILED로_남긴다() {
        when(mailGateway.send(any(), anyList(), anyList())).thenReturn(GatewayResult.failure("E100"));

        SystemMailOutcome outcome = systemMailService.send(MessageType.PASSWORD_RESET, "reset@example.com", "김재설정",
                Map.of("인증번호", "654321"), null, null);

        assertThat(outcome).isEqualTo(SystemMailOutcome.FAILED);
        MessageSend send = latestSend(MessageType.PASSWORD_RESET);
        MessageRecipient recipient = messageRecipientRepository.findByMessageSendIdOrderByIdAsc(send.getId()).get(0);
        assertThat(recipient.getMailStatus()).isEqualTo(MessageDeliveryStatus.FAILED);
        assertThat(recipient.getMailFailureReason()).isEqualTo("E100");
    }

    @Test
    void 기본_템플릿이_없으면_보내지_않는다() {
        messageTemplateRepository.deleteAll(messageTemplateRepository.findByType(MessageType.PASSWORD_RESET));

        SystemMailOutcome outcome = systemMailService.send(MessageType.PASSWORD_RESET, "reset@example.com", "김재설정",
                Map.of("인증번호", "654321"), null, null);

        assertThat(outcome).isEqualTo(SystemMailOutcome.NO_TEMPLATE);
        verifyNoInteractions(mailGateway);
    }

    private MessageSend latestSend(MessageType type) {
        return messageSendRepository.findAll().stream()
                .filter(send -> send.getType() == type)
                .max(Comparator.comparing(MessageSend::getId))
                .orElseThrow();
    }
}
```

- [ ] **Step 2: 실패 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.SystemMailServiceTest" --no-daemon
```
Expected: FAIL — 컴파일 오류(`SystemMailService`·`SystemMailOutcome` 없음).

- [ ] **Step 3: 구현**

`{BE}/enumeration/SystemMailOutcome.java`:

```java
package com.shinyoung.recruit.enumeration;

/** 시스템 자동발송 1통의 결과. ACCEPTED 솔루션 접수(또는 이미 성공), FAILED 접수 실패, NO_TEMPLATE 기본 템플릿 없음(보내지 않음). */
public enum SystemMailOutcome {
    ACCEPTED,
    FAILED,
    NO_TEMPLATE
}
```

`{BE}/service/SystemMailService.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.domain.entity.MessageTemplate;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.domain.repository.MessageTemplateRepository;
import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.MessageVariable;
import com.shinyoung.recruit.enumeration.SystemMailOutcome;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 시스템 자동발송 메일 1통(설계서 6.1). 그 종류의 기본 템플릿으로 이력(치환 전 원문)을 저장해 커밋한 뒤
 * {@link MessageDispatcher#dispatch}를 동기로 부르고, 수신자의 메일 상태로 결과를 판정한다.
 *
 * <p>호출자(가입·재발급 요청 스레드, 제출 리스너의 비동기 스레드)는 트랜잭션 밖에서 부른다. 그래서 저장 트랜잭션은
 * 여기서 커밋되고 게이트웨이 호출은 커밋 뒤에 일어난다. 트랜잭션 안에서 부르면(테스트) 그 트랜잭션에 합류한다.
 * 치환 결과(인증번호 등)는 {@link DeliveryItem}에만 담기고 DB·로그에 남지 않는다(설계서 4절).
 */
@Service
@RequiredArgsConstructor
public class SystemMailService {

    private static final Logger log = LoggerFactory.getLogger(SystemMailService.class);

    private final MessageTemplateRepository messageTemplateRepository;
    private final MessageSendRepository messageSendRepository;
    private final MessageRecipientRepository messageRecipientRepository;
    private final MessageRenderer messageRenderer;
    private final MessageDispatcher messageDispatcher;
    private final MessageProperties messageProperties;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    /**
     * @param variables 종류 전용 변수 값(예: 인증번호, 공고명·제출일시). #{이름}·#{채용사이트}는 비어 있으면 name·설정값으로 채운다.
     * @param jobPosting 공고 없는 발송이면 null
     * @param application 지원서 없는 발송이면 null
     */
    public SystemMailOutcome send(MessageType type, String email, String name, Map<String, String> variables,
                                  JobPosting jobPosting, JobApplication application) {
        Prepared prepared = new TransactionTemplate(transactionManager)
                .execute(status -> prepare(type, email, name, variables, jobPosting, application));
        if (prepared == null) {
            log.warn("시스템 메일 기본 템플릿이 없어 보내지 않습니다: type={}", type);
            return SystemMailOutcome.NO_TEMPLATE;
        }
        messageDispatcher.dispatch(prepared.sendId(), List.of(prepared.item()));
        MessageDeliveryStatus status = messageRecipientRepository.findById(prepared.item().recipientId())
                .map(MessageRecipient::getMailStatus)
                .orElse(MessageDeliveryStatus.FAILED);
        SystemMailOutcome outcome = status == MessageDeliveryStatus.REQUESTED || status == MessageDeliveryStatus.SENT
                ? SystemMailOutcome.ACCEPTED
                : SystemMailOutcome.FAILED;
        log.info("시스템 메일 발송: type={}, to={}, outcome={}", type, MessageContacts.maskEmail(email), outcome);
        return outcome;
    }

    private Prepared prepare(MessageType type, String email, String name, Map<String, String> variables,
                             JobPosting jobPosting, JobApplication application) {
        MessageTemplate template = messageTemplateRepository.findByTypeAndDefaultTemplateTrue(type).stream()
                .findFirst()
                .orElse(null);
        if (template == null) {
            return null;
        }
        MessageSend send = messageSendRepository.save(
                MessageSend.createSystem(type, jobPosting, template, LocalDateTime.now(clock)));
        MessageRecipient recipient = messageRecipientRepository.save(MessageRecipient.create(
                send, application, name, email, null,
                MessageDeliveryStatus.PENDING, null,
                MessageDeliveryStatus.SKIPPED, MessageContacts.CHANNEL_OFF, null));

        Map<String, String> values = new HashMap<>(variables);
        values.putIfAbsent(MessageVariable.NAME.getKey(), Objects.toString(name, ""));
        values.putIfAbsent(MessageVariable.SITE_URL.getKey(), messageProperties.getSiteUrl());
        DeliveryItem item = new DeliveryItem(recipient.getId(), MessageChannel.MAIL, name,
                MessageContacts.normalizeEmail(email),
                messageRenderer.render(template.getMailSubject(), values),
                messageRenderer.render(template.getMailBody(), values),
                null);
        return new Prepared(send.getId(), item);
    }

    private record Prepared(Long sendId, DeliveryItem item) {
    }
}
```

- [ ] **Step 4: 통과 확인**

Step 2 명령. Expected: PASS.

- [ ] **Step 5: 검증**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageDispatcherTest" --tests "com.shinyoung.recruit.service.MessageSendAsyncFlowTest" --no-daemon
```
Expected: PASS(디스패처는 바꾸지 않았다).

---

## Task 5: `EmailVerificationService` + 예외

**Files:**
- Create: `{BE}/enumeration/EmailVerificationPurpose.java`
- Create: `{BE}/service/EmailVerificationState.java`
- Create: `{BE}/service/EmailVerificationService.java`
- Create: `{BE}/exception/InvalidEmailVerificationException.java`
- Modify: `{BE}/exception/GlobalExceptionHandler.java`
- Test: `{BT}/service/EmailVerificationServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/service/EmailVerificationServiceTest.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.enumeration.EmailVerificationPurpose;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.SystemMailOutcome;
import com.shinyoung.recruit.exception.InvalidEmailVerificationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static com.shinyoung.recruit.enumeration.EmailVerificationPurpose.PASSWORD_RESET;
import static com.shinyoung.recruit.enumeration.EmailVerificationPurpose.SIGNUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final Instant BASE = Instant.parse("2026-09-23T01:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final String EMAIL = "applicant@example.com";
    private static final String REISSUE = "인증번호를 다시 받아 주세요.";
    private static final String REQUIRED = "이메일 인증이 필요합니다.";

    @Mock
    private SystemMailService systemMailService;

    @Captor
    private ArgumentCaptor<Map<String, String>> variables;

    private EmailVerificationService at(Duration elapsed) {
        return new EmailVerificationService(systemMailService, Clock.fixed(BASE.plus(elapsed), ZONE));
    }

    private static LocalDateTime time(Duration elapsed) {
        return LocalDateTime.ofInstant(BASE.plus(elapsed), ZONE);
    }

    @Test
    void 발급하면_6자리_번호와_번호_해시만_담은_상태를_준다() {
        EmailVerificationService.IssuedCode issued = at(Duration.ZERO).issue(null, SIGNUP, " " + EMAIL + " ");

        assertThat(issued.code()).matches("\\d{6}");
        EmailVerificationState state = issued.state();
        assertThat(state.getPurpose()).isEqualTo(SIGNUP);
        assertThat(state.getEmail()).isEqualTo(EMAIL);
        assertThat(state.getCodeHash()).isEqualTo(HashUtil.sha256(issued.code())).isNotEqualTo(issued.code());
        assertThat(state.getExpiresAt()).isEqualTo(time(Duration.ofMinutes(5)));
        assertThat(state.getSentAt()).isEqualTo(time(Duration.ZERO));
        assertThat(state.getFailedCount()).isZero();
        assertThat(state.getVerifiedAt()).isNull();
    }

    @Test
    void 같은_목적은_60초가_지나야_다시_받는다() {
        EmailVerificationState first = at(Duration.ZERO).issue(null, SIGNUP, EMAIL).state();

        assertThatThrownBy(() -> at(Duration.ofSeconds(59)).issue(first, SIGNUP, EMAIL))
                .isInstanceOf(InvalidEmailVerificationException.class)
                .hasMessage("인증번호는 60초 후에 다시 받을 수 있습니다.");
        assertThat(at(Duration.ofSeconds(60)).issue(first, SIGNUP, EMAIL).state().getSentAt())
                .isEqualTo(time(Duration.ofSeconds(60)));
    }

    @Test
    void 만료됐거나_이메일이_다르거나_상태가_없으면_다시_받으라고_한다() {
        EmailVerificationService.IssuedCode issued = at(Duration.ZERO).issue(null, SIGNUP, EMAIL);

        assertThatThrownBy(() -> at(Duration.ofMinutes(5)).verify(issued.state(), SIGNUP, EMAIL, issued.code()))
                .isInstanceOf(InvalidEmailVerificationException.class)
                .hasMessage(REISSUE);
        assertThatThrownBy(() -> at(Duration.ofMinutes(1)).verify(issued.state(), SIGNUP, "other@example.com", issued.code()))
                .hasMessage(REISSUE);
        assertThatThrownBy(() -> at(Duration.ofMinutes(1)).verify(null, SIGNUP, EMAIL, issued.code()))
                .hasMessage(REISSUE);
        assertThatThrownBy(() -> at(Duration.ofMinutes(1)).verify(issued.state(), PASSWORD_RESET, EMAIL, issued.code()))
                .hasMessage(REISSUE);
    }

    @Test
    void 틀리면_실패_수를_늘리고_5회_틀리면_맞아도_거부한다() {
        EmailVerificationService.IssuedCode issued = at(Duration.ZERO).issue(null, SIGNUP, EMAIL);
        EmailVerificationService service = at(Duration.ofMinutes(1));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> service.verify(issued.state(), SIGNUP, EMAIL, "wrong"))
                    .isInstanceOf(InvalidEmailVerificationException.class)
                    .hasMessage("인증번호가 일치하지 않습니다.");
        }

        assertThat(issued.state().getFailedCount()).isEqualTo(5);
        assertThatThrownBy(() -> service.verify(issued.state(), SIGNUP, EMAIL, issued.code()))
                .hasMessage("인증번호를 5회 틀렸습니다. 인증번호를 다시 받아 주세요.");
        assertThat(issued.state().getVerifiedAt()).isNull();
    }

    @Test
    void 맞으면_확인하고_10분_동안_대소문자와_공백을_무시한_같은_이메일만_통과한다() {
        EmailVerificationService.IssuedCode issued = at(Duration.ZERO).issue(null, SIGNUP, EMAIL);
        EmailVerificationState state = issued.state();

        at(Duration.ofMinutes(1)).verify(state, SIGNUP, EMAIL, issued.code());

        assertThat(state.getVerifiedAt()).isEqualTo(time(Duration.ofMinutes(1)));
        assertThatCode(() -> at(Duration.ofMinutes(10).plusSeconds(59))
                .requireVerified(state, SIGNUP, " Applicant@Example.com "))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> at(Duration.ofMinutes(11)).requireVerified(state, SIGNUP, EMAIL))
                .isInstanceOf(InvalidEmailVerificationException.class)
                .hasMessage(REQUIRED);
        assertThatThrownBy(() -> at(Duration.ofMinutes(2)).requireVerified(state, SIGNUP, "other@example.com"))
                .hasMessage(REQUIRED);
        assertThatThrownBy(() -> at(Duration.ofMinutes(2)).requireVerified(state, PASSWORD_RESET, EMAIL))
                .hasMessage(REQUIRED);
        assertThatThrownBy(() -> at(Duration.ofMinutes(2)).requireVerified(state, SIGNUP, null))
                .hasMessage(REQUIRED);
    }

    @Test
    void 확인_전이거나_상태가_없으면_이메일_인증이_필요하다() {
        EmailVerificationState state = at(Duration.ZERO).issue(null, SIGNUP, EMAIL).state();

        assertThatThrownBy(() -> at(Duration.ofMinutes(1)).requireVerified(state, SIGNUP, EMAIL))
                .hasMessage(REQUIRED);
        assertThatThrownBy(() -> at(Duration.ofMinutes(1)).requireVerified(null, SIGNUP, EMAIL))
                .hasMessage(REQUIRED);
    }

    @Test
    void 발송이_접수되면_상태를_주고_메일에는_평문_번호를_넘긴다() {
        given(systemMailService.send(any(), any(), any(), any(), any(), any())).willReturn(SystemMailOutcome.ACCEPTED);

        EmailVerificationState state = at(Duration.ZERO).send(null, PASSWORD_RESET, EMAIL, "김재설정");

        verify(systemMailService).send(eq(MessageType.PASSWORD_RESET), eq(EMAIL), eq("김재설정"),
                variables.capture(), isNull(), isNull());
        assertThat(HashUtil.sha256(variables.getValue().get("인증번호"))).isEqualTo(state.getCodeHash());
        assertThat(state.getPurpose()).isEqualTo(PASSWORD_RESET);
    }

    @Test
    void 발송이_실패하거나_템플릿이_없으면_거부한다() {
        given(systemMailService.send(any(), any(), any(), any(), any(), any()))
                .willReturn(SystemMailOutcome.FAILED, SystemMailOutcome.NO_TEMPLATE);

        assertThatThrownBy(() -> at(Duration.ZERO).send(null, SIGNUP, EMAIL, ""))
                .isInstanceOf(InvalidEmailVerificationException.class)
                .hasMessage("인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해 주세요.");
        assertThatThrownBy(() -> at(Duration.ZERO).send(null, SIGNUP, EMAIL, ""))
                .isInstanceOf(InvalidEmailVerificationException.class)
                .hasMessage("인증 메일 템플릿이 없습니다. 관리자에게 문의하세요.");
    }

    @Test
    void 용도마다_세션_키와_메일_종류가_다르다() {
        assertThat(EmailVerificationService.sessionKey(SIGNUP)).isEqualTo("EMAIL_VERIFICATION_SIGNUP");
        assertThat(EmailVerificationService.sessionKey(PASSWORD_RESET)).isEqualTo("EMAIL_VERIFICATION_PASSWORD_RESET");
        assertThat(EmailVerificationPurpose.SIGNUP.getMessageType()).isEqualTo(MessageType.SIGNUP_VERIFICATION);
        assertThat(EmailVerificationPurpose.PASSWORD_RESET.getMessageType()).isEqualTo(MessageType.PASSWORD_RESET);
    }
}
```

- [ ] **Step 2: 실패 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.EmailVerificationServiceTest" --no-daemon
```
Expected: FAIL — 컴파일 오류(클래스 없음).

- [ ] **Step 3: 구현**

`{BE}/enumeration/EmailVerificationPurpose.java`:

```java
package com.shinyoung.recruit.enumeration;

/** 이메일 인증번호의 용도. 용도마다 세션 키와 보내는 메일 종류가 다르다. */
public enum EmailVerificationPurpose {
    SIGNUP(MessageType.SIGNUP_VERIFICATION),
    PASSWORD_RESET(MessageType.PASSWORD_RESET);

    private final MessageType messageType;

    EmailVerificationPurpose(MessageType messageType) {
        this.messageType = messageType;
    }

    public MessageType getMessageType() {
        return messageType;
    }
}
```

`{BE}/exception/InvalidEmailVerificationException.java`:

```java
package com.shinyoung.recruit.exception;

public class InvalidEmailVerificationException extends RuntimeException {
    public InvalidEmailVerificationException(String message) {
        super(message);
    }
}
```

`{BE}/exception/GlobalExceptionHandler.java` — `handleNiceVerification` 메서드 바로 아래에 추가:

```java
    @ExceptionHandler(InvalidEmailVerificationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidEmailVerification(InvalidEmailVerificationException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
    }
```

`{BE}/service/EmailVerificationState.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.EmailVerificationPurpose;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * HTTP 세션에 두는 이메일 인증 진행 상태(설계서 6.2). 번호 원문은 없고 SHA-256 해시만 있다.
 * 실패 수·확인 시각은 제자리에서 바뀐다 — 컨트롤러는 확인 뒤 같은 객체를 세션에 다시 넣는다.
 */
@Getter
public class EmailVerificationState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final EmailVerificationPurpose purpose;
    private final String email;
    private final String codeHash;
    private final LocalDateTime expiresAt;
    private final LocalDateTime sentAt;
    private int failedCount;
    private LocalDateTime verifiedAt;

    private EmailVerificationState(EmailVerificationPurpose purpose, String email, String codeHash,
                                   LocalDateTime expiresAt, LocalDateTime sentAt) {
        this.purpose = purpose;
        this.email = email;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.sentAt = sentAt;
    }

    public static EmailVerificationState issued(EmailVerificationPurpose purpose, String email, String codeHash,
                                                LocalDateTime expiresAt, LocalDateTime sentAt) {
        return new EmailVerificationState(purpose, email, codeHash, expiresAt, sentAt);
    }

    public void recordFailure() {
        this.failedCount++;
    }

    public void markVerified(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }
}
```

`{BE}/service/EmailVerificationService.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.enumeration.EmailVerificationPurpose;
import com.shinyoung.recruit.enumeration.MessageVariable;
import com.shinyoung.recruit.enumeration.SystemMailOutcome;
import com.shinyoung.recruit.exception.InvalidEmailVerificationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 이메일 인증번호(설계서 6.2): 숫자 6자리 · 유효 5분 · 같은 목적 재발송은 60초 뒤 · 5회 틀리면 무효 ·
 * 확인 후 10분 안에 가입/재설정. 세션은 컨트롤러가 읽고 쓰며 이 서비스는 값만 다룬다(NICE 패턴과 같다).
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    static final Duration VERIFIED_TTL = Duration.ofMinutes(10);
    static final int MAX_FAILURES = 5;

    private static final String SESSION_KEY_PREFIX = "EMAIL_VERIFICATION_";
    private static final String REISSUE_MESSAGE = "인증번호를 다시 받아 주세요.";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SystemMailService systemMailService;
    private final Clock clock;

    /** 세션 속성 키. 목적별로 나눠 가입 인증과 비밀번호 재발급이 서로 덮어쓰지 않게 한다. */
    public static String sessionKey(EmailVerificationPurpose purpose) {
        return SESSION_KEY_PREFIX + purpose.name();
    }

    /**
     * 번호를 발급해 메일로 보내고 새 상태를 준다. 발송이 접수되지 않으면 예외 — 호출자는 세션에 저장하지 않는다.
     * 호출자는 트랜잭션 밖에서 부른다(발송 이력을 먼저 커밋해야 한다, SystemMailService).
     */
    public EmailVerificationState send(EmailVerificationState previous, EmailVerificationPurpose purpose,
                                       String email, String name) {
        IssuedCode issued = issue(previous, purpose, email);
        SystemMailOutcome outcome = systemMailService.send(purpose.getMessageType(), issued.state().getEmail(), name,
                Map.of(MessageVariable.VERIFICATION_CODE.getKey(), issued.code()), null, null);
        if (outcome == SystemMailOutcome.NO_TEMPLATE) {
            throw new InvalidEmailVerificationException("인증 메일 템플릿이 없습니다. 관리자에게 문의하세요.");
        }
        if (outcome != SystemMailOutcome.ACCEPTED) {
            throw new InvalidEmailVerificationException("인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return issued.state();
    }

    /** 새 번호와 상태. 같은 목적의 직전 발송에서 60초가 지나지 않았으면 거부한다. */
    public IssuedCode issue(EmailVerificationState previous, EmailVerificationPurpose purpose, String email) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (previous != null && previous.getPurpose() == purpose
                && now.isBefore(previous.getSentAt().plus(RESEND_INTERVAL))) {
            throw new InvalidEmailVerificationException("인증번호는 60초 후에 다시 받을 수 있습니다.");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        EmailVerificationState state = EmailVerificationState.issued(
                purpose, email.trim(), HashUtil.sha256(code), now.plus(CODE_TTL), now);
        return new IssuedCode(state, code);
    }

    /** 번호를 확인한다. 틀리면 상태의 실패 수를 늘리고 예외, 맞으면 확인 시각을 남긴다. */
    public void verify(EmailVerificationState state, EmailVerificationPurpose purpose, String email, String code) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (state == null || state.getPurpose() != purpose || !sameEmail(state.getEmail(), email)
                || !now.isBefore(state.getExpiresAt())) {
            throw new InvalidEmailVerificationException(REISSUE_MESSAGE);
        }
        if (state.getFailedCount() >= MAX_FAILURES) {
            throw new InvalidEmailVerificationException("인증번호를 5회 틀렸습니다. 인증번호를 다시 받아 주세요.");
        }
        if (code == null || !state.getCodeHash().equals(HashUtil.sha256(code.trim()))) {
            state.recordFailure();
            throw new InvalidEmailVerificationException("인증번호가 일치하지 않습니다.");
        }
        state.markVerified(now);
    }

    /** 확인했고 10분이 지나지 않았으며 같은 이메일(앞뒤 공백 제거, 대소문자 무시)이어야 통과한다. */
    public void requireVerified(EmailVerificationState state, EmailVerificationPurpose purpose, String email) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean verified = state != null
                && state.getPurpose() == purpose
                && state.getVerifiedAt() != null
                && now.isBefore(state.getVerifiedAt().plus(VERIFIED_TTL))
                && sameEmail(state.getEmail(), email);
        if (!verified) {
            throw new InvalidEmailVerificationException("이메일 인증이 필요합니다.");
        }
    }

    private static boolean sameEmail(String expected, String actual) {
        return expected != null && actual != null && expected.trim().equalsIgnoreCase(actual.trim());
    }

    /** 발급 결과. code 는 메일로만 나가고 세션·DB·로그에 남지 않는다. */
    public record IssuedCode(EmailVerificationState state, String code) {
    }
}
```

- [ ] **Step 4: 통과 확인**

Step 2 명령. Expected: PASS.

- [ ] **Step 5: 검증**

`GlobalExceptionHandler`에 새 예외 매핑이 추가됐는지 확인하고(빠지면 500), 아래를 실행한다.
```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.EmailVerificationServiceTest" --tests "com.shinyoung.recruit.common.hash.HashUtilTest" --no-daemon
```
Expected: PASS.

---

## Task 6: 가입 이메일 인증 API + 가입 시 인증 강제

**Files:**
- Create: `{BE}/dto/request/EmailVerificationSendRequest.java`, `{BE}/dto/request/EmailVerificationConfirmRequest.java`
- Modify: `{BE}/service/ApplicantSignUpService.java`
- Modify: `{BE}/controller/ApplicantSignUpController.java`
- Modify: `{BE}/config/SecurityConfig.java`
- Test: `{BT}/controller/ApplicantEmailVerificationControllerTest.java`(생성), `{BT}/controller/ApplicantSignUpControllerTest.java`, `{BT}/controller/ApplicantSignUpNiceIntegrationTest.java`, `{BT}/service/ApplicantSignUpServiceTest.java`, `{BT}/config/SecurityConfigTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

(1) `{BT}/controller/ApplicantEmailVerificationControllerTest.java` 생성:

```java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.enumeration.EmailVerificationPurpose;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.service.EmailVerificationService;
import com.shinyoung.recruit.service.GatewayResult;
import com.shinyoung.recruit.service.MailGateway;
import com.shinyoung.recruit.service.MailMessage;
import com.shinyoung.recruit.service.SmsGateway;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicantEmailVerificationControllerTest {

    private static final Pattern CODE = Pattern.compile("인증번호: (\\d{6})");

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ApplicantRepository applicantRepository;
    @Autowired
    private Clock clock;

    @MockitoBean
    private MailGateway mailGateway;
    @MockitoBean
    private SmsGateway smsGateway;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        SecurityContextHolder.clearContext();
        when(mailGateway.send(any(), anyList(), anyList())).thenReturn(GatewayResult.accepted("TX-VERIFY-1"));
    }

    @Test
    void 인증번호를_확인하면_가입할_수_있고_인증은_1회용이다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        send(session, "verify01@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        confirm(session, "verify01@example.com", sentCode()).andExpect(status().isOk());

        addNiceIdentity(session, "19910101");
        signUp(session, "verify01@example.com").andExpect(status().isOk());

        addNiceIdentity(session, "19910102");
        signUp(session, "verify01@example.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이메일 인증이 필요합니다."));
    }

    @Test
    void 틀린_번호는_거부하고_5회_틀리면_맞는_번호도_거부한다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        send(session, "verify02@example.com").andExpect(status().isOk());
        String code = sentCode();

        for (int attempt = 0; attempt < 5; attempt++) {
            confirm(session, "verify02@example.com", "wrong")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("인증번호가 일치하지 않습니다."));
        }

        confirm(session, "verify02@example.com", code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("인증번호를 5회 틀렸습니다. 인증번호를 다시 받아 주세요."));
    }

    @Test
    void 이미_가입된_이메일이면_보내지_않는다() throws Exception {
        Applicant existing = new Applicant(HashUtil.sha256("verify-taken-ci"));
        existing.setLoginId("verify-taken@example.com");
        existing.setName("기존사용자");
        existing.setUserName("기존사용자");
        existing.setPassword("encoded");
        existing.setPhoneNumber("01000000000");
        existing.setEmail("verify-taken@example.com");
        applicantRepository.save(existing);

        send(new MockHttpSession(), "verify-taken@example.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."));
        verifyNoInteractions(mailGateway);
    }

    @Test
    void 60초_안에는_다시_받을_수_없다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        send(session, "verify03@example.com").andExpect(status().isOk());

        send(session, "verify03@example.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("인증번호는 60초 후에 다시 받을 수 있습니다."));
    }

    @Test
    void 메일이_접수되지_않으면_인증_상태를_남기지_않는다() throws Exception {
        when(mailGateway.send(any(), anyList(), anyList())).thenReturn(GatewayResult.failure("E100"));
        MockHttpSession session = new MockHttpSession();

        send(session, "verify04@example.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해 주세요."));

        assertThat(session.getAttribute(EmailVerificationService.sessionKey(EmailVerificationPurpose.SIGNUP))).isNull();
        confirm(session, "verify04@example.com", "123456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("인증번호를 다시 받아 주세요."));
    }

    private ResultActions send(MockHttpSession session, String email) throws Exception {
        return mockMvc.perform(post("/api/auth/applicants/email-verification/send")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions confirm(MockHttpSession session, String email, String code) throws Exception {
        return mockMvc.perform(post("/api/auth/applicants/email-verification/verify")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"));
    }

    private ResultActions signUp(MockHttpSession session, String email) throws Exception {
        return mockMvc.perform(post("/api/auth/applicants/sign-up")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + email + "\",\"password\":\"Password1234!\",\"email\":\"" + email + "\"}"));
    }

    /* 다른 테스트의 식별 키와 겹치지 않는 이 테스트 전용 신원. */
    private void addNiceIdentity(MockHttpSession session, String birthDate) {
        session.setAttribute(NiceVerificationController.VERIFIED_SESSION_KEY, new NiceVerifiedIdentity(
                NiceVerificationPurpose.SIGNUP, "김메일", "01000000000", birthDate, "1", clock.instant()));
    }

    private String sentCode() {
        ArgumentCaptor<MailMessage> mail = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailGateway, atLeastOnce()).send(mail.capture(), anyList(), anyList());
        Matcher matcher = CODE.matcher(mail.getValue().text());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
```

(2) `{BT}/controller/ApplicantSignUpControllerTest.java`:
- import 추가: `com.shinyoung.recruit.enumeration.EmailVerificationPurpose`, `com.shinyoung.recruit.service.EmailVerificationService`, `com.shinyoung.recruit.service.EmailVerificationState`, `java.time.LocalDateTime`.
- `verifiedSession(...)` 아래에 helper 추가:
```java
    /** 가입 이메일 인증까지 마친 세션으로 만든다. 번호 발송·확인 흐름은 ApplicantEmailVerificationControllerTest 가 본다. */
    private MockHttpSession withVerifiedEmail(MockHttpSession session, String email) {
        LocalDateTime now = LocalDateTime.now(clock);
        EmailVerificationState state = EmailVerificationState.issued(
                EmailVerificationPurpose.SIGNUP, email, "dummy-hash", now.plusMinutes(5), now);
        state.markVerified(now);
        session.setAttribute(EmailVerificationService.sessionKey(EmailVerificationPurpose.SIGNUP), state);
        return session;
    }
```
- `회원가입_성공`의 `.session(verifiedSession("홍길동", "01012345678", "19900101", "1"))`를 `.session(withVerifiedEmail(verifiedSession("홍길동", "01012345678", "19900101", "1"), "applicant01@example.com"))`로 바꾼다.
- `loginId_중복_시_400` 전체를 아래로 교체:
```java
    @Test
    void loginId_중복_시_400() throws Exception {
        Applicant existing = new Applicant(HashUtil.sha256("existing-ci"));
        existing.setLoginId("duplicate-id");
        existing.setName("기존사용자");
        existing.setUserName("기존사용자");
        existing.setPassword("encoded");
        existing.setPhoneNumber("01000000000");
        applicantRepository.save(existing);

        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(withVerifiedEmail(verifiedSession("새사용자", "01011111111", "19900101", "1"),
                                "duplicate-new@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "duplicate-id",
                                  "password": "Password1234!",
                                  "email": "duplicate-new@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 아이디입니다."));
    }
```
- 테스트 3개 추가:
```java
    @Test
    void 이메일_인증_없이는_가입할_수_없다() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(verifiedSession("메일미인증", "01022222222", "19900202", "1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "no-verify@example.com",
                                  "password": "Password1234!",
                                  "email": "no-verify@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이메일 인증이 필요합니다."));
    }

    @Test
    void 인증한_이메일과_다른_이메일로는_가입할_수_없다() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(withVerifiedEmail(verifiedSession("메일다름", "01033333333", "19900303", "1"),
                                "verified@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "other@example.com",
                                  "password": "Password1234!",
                                  "email": "other@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이메일 인증이 필요합니다."));
    }

    @Test
    void 이메일을_비우면_이메일_인증이_필요하다() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/sign-up")
                        .session(withVerifiedEmail(verifiedSession("메일없음", "01044444444", "19900404", "1"),
                                "verified@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "no-email-id",
                                  "password": "Password1234!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이메일 인증이 필요합니다."));
    }
```

(3) `{BT}/controller/ApplicantSignUpNiceIntegrationTest.java` 전체를 아래로 교체(가입 요청마다 가입 이메일 인증 상태를 세션에 넣는 helper `signUp`을 쓴다. 검증 의도는 그대로다):

```java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.enumeration.EmailVerificationPurpose;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.service.EmailVerificationService;
import com.shinyoung.recruit.service.EmailVerificationState;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @Autowired
    private AuditHmac auditHmac;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static final String DUPLICATE_IDENTITY_MESSAGE = "이미 가입된 본인인증 정보입니다.";

    private MockHttpSession sessionWithIdentity(String phoneNumber, String birthDate, String gender) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                NiceVerificationController.VERIFIED_SESSION_KEY,
                new NiceVerifiedIdentity(
                        NiceVerificationPurpose.SIGNUP, "홍길동", phoneNumber, birthDate, gender, clock.instant()));
        return session;
    }

    private String signUpBody(String loginId) {
        return "{\"loginId\":\"" + loginId + "\",\"password\":\"password123\",\"email\":\""
                + loginId + "\"}";
    }

    /**
     * 가입 이메일 인증을 마친 상태를 세션에 넣고 가입을 요청한다. 이 클래스는 NICE 세션 규칙만 보므로
     * 이메일 인증은 항상 통과시킨다(인증 흐름은 ApplicantEmailVerificationControllerTest).
     */
    private ResultActions signUp(MockHttpSession session, String loginId) throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        EmailVerificationState state = EmailVerificationState.issued(
                EmailVerificationPurpose.SIGNUP, loginId, "dummy-hash", now.plusMinutes(5), now);
        state.markVerified(now);
        session.setAttribute(EmailVerificationService.sessionKey(EmailVerificationPurpose.SIGNUP), state);
        return mockMvc.perform(post("/api/auth/applicants/sign-up")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(signUpBody(loginId)));
    }

    @Test
    void signUpUsesIdentityFromSessionNotFromRequestBody() throws Exception {
        signUp(sessionWithIdentity("01012345678", "19900101", "1"), "nice1@example.test")
                .andExpect(status().isOk());

        Applicant saved = applicantRepository
                .findByCiHash(auditHmac.identityHash("홍길동", "19900101", "1")).orElseThrow();
        assertEquals("홍길동", saved.getUserName());
        assertEquals("01012345678", saved.getPhoneNumber());
    }

    @Test
    void signUpWithoutVerifiedSessionIsRejected() throws Exception {
        signUp(new MockHttpSession(), "nice2@example.test")
                .andExpect(status().is4xxClientError());
    }

    @Test
    void signUpConsumesIdentitySoItCannotBeReused() throws Exception {
        MockHttpSession session = sessionWithIdentity("01012345678", "19900101", "1");

        signUp(session, "nice3@example.test").andExpect(status().isOk());

        // 같은 세션으로 다시 가입을 시도한다. 인증 1회로 계정 2개를 만들 수 있으면 안 된다.
        signUp(session, "nice4@example.test").andExpect(status().is4xxClientError());
    }

    @Test
    void sameIdentityCannotSignUpTwice() throws Exception {
        signUp(sessionWithIdentity("01012345678", "19900101", "1"), "nice5@example.test")
                .andExpect(status().isOk());

        signUp(sessionWithIdentity("01012345678", "19900101", "1"), "nice6@example.test")
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.message").value(DUPLICATE_IDENTITY_MESSAGE));
    }

    /* 중복 판정에 휴대폰은 들어가지 않는다 — 번호만 바꿔 두 번째 계정을 만들 수 없어야 한다. */
    @Test
    void sameIdentityWithDifferentPhoneIsRejected() throws Exception {
        signUp(sessionWithIdentity("01012345678", "19900101", "1"), "nice8@example.test")
                .andExpect(status().isOk());

        signUp(sessionWithIdentity("01099998888", "19900101", "1"), "nice9@example.test")
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.message").value(DUPLICATE_IDENTITY_MESSAGE));
    }

    /* 이름·생년월일이 같아도 성별이 다르면 다른 사람이다 — 둘 다 가입된다. */
    @Test
    void sameNameAndBirthDateWithDifferentGenderCanBothSignUp() throws Exception {
        signUp(sessionWithIdentity("01012345678", "19900101", "1"), "nice10@example.test")
                .andExpect(status().isOk());

        signUp(sessionWithIdentity("01012345678", "19900101", "0"), "nice11@example.test")
                .andExpect(status().isOk());

        assertTrue(applicantRepository.existsByCiHash(auditHmac.identityHash("홍길동", "19900101", "1")));
        assertTrue(applicantRepository.existsByCiHash(auditHmac.identityHash("홍길동", "19900101", "0")));
    }

    @Test
    void expiredIdentityIsRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                NiceVerificationController.VERIFIED_SESSION_KEY,
                new NiceVerifiedIdentity(
                        NiceVerificationPurpose.SIGNUP, "홍길동", "01012345678", "19900101", "1",
                        clock.instant().minus(Duration.ofMinutes(31))));

        signUp(session, "nice7@example.test").andExpect(status().is4xxClientError());
    }
}
```

(4) `{BT}/service/ApplicantSignUpServiceTest.java`:
- import 추가: `com.shinyoung.recruit.enumeration.EmailVerificationPurpose`, `java.time.LocalDateTime`, `static org.mockito.Mockito.verifyNoInteractions`.
- `@Mock private PasswordEncoder passwordEncoder;` 아래에 추가:
```java
    @Mock
    private EmailVerificationService emailVerificationService;
```
- `setUp`을 바꾼다:
```java
        applicantSignUpService = new ApplicantSignUpService(
                applicantRepository, userRepository, passwordEncoder, auditHmac, emailVerificationService);
```
- 테스트 2개 추가:
```java
    @Test
    void 가입된_이메일이면_인증번호를_보내지_않는다() {
        given(applicantRepository.existsByEmail("taken@example.com")).willReturn(true);

        assertThatThrownBy(() -> applicantSignUpService.sendEmailVerification(null, " taken@example.com "))
                .isInstanceOf(InvalidApplicantSignUpException.class)
                .hasMessage("이미 사용 중인 이메일입니다.");
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void 가입_인증번호는_이름_없이_보낸다() {
        EmailVerificationState state = EmailVerificationState.issued(EmailVerificationPurpose.SIGNUP,
                "new@example.com", "hash", LocalDateTime.of(2026, 9, 23, 10, 5), LocalDateTime.of(2026, 9, 23, 10, 0));
        given(applicantRepository.existsByEmail("new@example.com")).willReturn(false);
        given(emailVerificationService.send(null, EmailVerificationPurpose.SIGNUP, "new@example.com", "")).willReturn(state);

        assertThat(applicantSignUpService.sendEmailVerification(null, "new@example.com")).isSameAs(state);
    }
```

(5) `{BT}/config/SecurityConfigTest.java` — `아이디_찾기는_비인증이어도_인가를_통과` 아래에 추가:
```java
    /* 가입 이메일 인증번호 발송·확인도 로그인 전에 쓴다. 본문이 없어 400 이지만 401/403 이 아니면 인가 통과다. */
    @Test
    void 가입_인증번호_발송은_비인증이어도_인가를_통과() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/email-verification/send"))
                .andExpect(status().is(allOf(not(401), not(403))));
    }

    @Test
    void 가입_인증번호_확인은_비인증이어도_인가를_통과() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/email-verification/verify"))
                .andExpect(status().is(allOf(not(401), not(403))));
    }
```

- [ ] **Step 2: 실패 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.ApplicantEmailVerificationControllerTest" --tests "com.shinyoung.recruit.controller.ApplicantSignUpControllerTest" --tests "com.shinyoung.recruit.controller.ApplicantSignUpNiceIntegrationTest" --tests "com.shinyoung.recruit.service.ApplicantSignUpServiceTest" --tests "com.shinyoung.recruit.config.SecurityConfigTest" --no-daemon
```
Expected: FAIL — 컴파일 오류(`ApplicantSignUpService` 5인자 생성자·`sendEmailVerification` 없음). SecurityConfigTest 신규 2개는 `anyRequest().permitAll()` 때문에 구현 전에도 통과한다(명시 매처 회귀 방지용).

- [ ] **Step 3: 구현**

`{BE}/dto/request/EmailVerificationSendRequest.java`:

```java
package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 이메일 인증번호 발송(가입·비밀번호 재발급 공용). */
public record EmailVerificationSendRequest(
        @NotBlank(message = "email은 필수입니다.")
        @Email(message = "유효한 이메일 형식이어야 합니다.")
        @Size(max = 255, message = "email은 255자 이하여야 합니다.")
        String email
) {
}
```

`{BE}/dto/request/EmailVerificationConfirmRequest.java`:

```java
package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 이메일 인증번호 확인(가입·비밀번호 재발급 공용). 번호 형식은 보지 않는다 — 틀린 값은 불일치로 센다. */
public record EmailVerificationConfirmRequest(
        @NotBlank(message = "email은 필수입니다.")
        @Email(message = "유효한 이메일 형식이어야 합니다.")
        @Size(max = 255, message = "email은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "인증번호를 입력해 주세요.")
        @Size(max = 20, message = "인증번호가 너무 깁니다.")
        String code
) {
}
```

`{BE}/service/ApplicantSignUpService.java` 전체:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.UserRepository;
import com.shinyoung.recruit.dto.request.ApplicantSignUpRequest;
import com.shinyoung.recruit.dto.response.ApplicantEmailAvailabilityResponse;
import com.shinyoung.recruit.dto.response.ApplicantSignUpResponse;
import com.shinyoung.recruit.enumeration.EmailVerificationPurpose;
import com.shinyoung.recruit.exception.InvalidApplicantSignUpException;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicantSignUpService {

    private final ApplicantRepository applicantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditHmac auditHmac;
    private final EmailVerificationService emailVerificationService;

    public ApplicantSignUpService(ApplicantRepository applicantRepository, UserRepository userRepository, PasswordEncoder passwordEncoder, AuditHmac auditHmac,
                                  EmailVerificationService emailVerificationService) {
        this.applicantRepository = applicantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditHmac = auditHmac;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional
    public ApplicantSignUpResponse signUp(ApplicantSignUpRequest request, NiceVerifiedIdentity identity) {
        String loginId = request.loginId().trim();
        String name = identity.name().trim();
        String phoneNumber = identity.phoneNumber().trim();
        String email = normalizeEmail(request.email());

        // 로그인 해석(findUserByLoginId)이 users 테이블 전체에서 일어나므로 중복체크도 User 레벨로 수행한다.
        // (Applicant 레벨만 체크하면 임직원(LDAP JIT) loginId와 충돌해 양쪽 로그인 장애가 된다.)
        if (userRepository.existsByLoginId(loginId)) {
            throw new InvalidApplicantSignUpException("이미 사용 중인 아이디입니다.");
        }

        if (email != null && applicantRepository.existsByEmail(email)) {
            throw new InvalidApplicantSignUpException("이미 사용 중인 이메일입니다.");
        }

        // 중복 판정 키 = 이름+생년월일+성별의 HMAC(NICE 계약에 CI 가 없어 CI 대신 쓴다).
        // 휴대폰은 넣지 않는다 — 번호만 바꿔 중복 가입하는 것을 막는다.
        String identityKey = auditHmac.identityHash(identity.name(), identity.birthDate(), identity.gender());
        if (applicantRepository.existsByCiHash(identityKey)) {
            throw new InvalidApplicantSignUpException("이미 가입된 본인인증 정보입니다.");
        }

        Applicant applicant = new Applicant(identityKey);
        applicant.setLoginId(loginId);
        applicant.setName(name);
        applicant.setUserName(name);
        applicant.setPassword(passwordEncoder.encode(request.password()));
        applicant.setPhoneNumber(phoneNumber);
        applicant.setEmail(email);

        applicantRepository.save(applicant);

        return ApplicantSignUpResponse.from(applicant);
    }

    /**
     * 가입 이메일 인증번호를 보내고 세션에 둘 상태를 돌려준다. 이미 가입된 이메일이면 보내지 않는다.
     *
     * <p>트랜잭션을 걸지 않는다 — 발송 이력은 SystemMailService 가 먼저 커밋한 뒤 게이트웨이를 부른다.
     * 가입 인증 메일에는 이름이 없다(아직 본인확인 전일 수 있다).
     */
    public EmailVerificationState sendEmailVerification(EmailVerificationState previous, String email) {
        String normalized = normalizeEmail(email);
        if (applicantRepository.existsByEmail(normalized)) {
            throw new InvalidApplicantSignUpException("이미 사용 중인 이메일입니다.");
        }
        return emailVerificationService.send(previous, EmailVerificationPurpose.SIGNUP, normalized, "");
    }

    /**
     * 가입 화면용 advisory 이메일 가용성 판정. signUp과 동일한 정규화(trim)를 거쳐 판정하며,
     * 최종 권위는 signUp 시점 재검증 + Applicant.email DB unique 제약이다.
     */
    @Transactional(readOnly = true)
    public ApplicantEmailAvailabilityResponse checkEmailAvailability(String email) {
        String normalized = normalizeEmail(email);
        boolean available = normalized != null && !applicantRepository.existsByEmail(normalized);
        return new ApplicantEmailAvailabilityResponse(available);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
```

`{BE}/controller/ApplicantSignUpController.java` 전체:

```java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ApplicantSignUpRequest;
import com.shinyoung.recruit.dto.request.EmailVerificationConfirmRequest;
import com.shinyoung.recruit.dto.request.EmailVerificationSendRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ApplicantEmailAvailabilityResponse;
import com.shinyoung.recruit.dto.response.ApplicantSignUpResponse;
import com.shinyoung.recruit.enumeration.EmailVerificationPurpose;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.service.ApplicantSignUpService;
import com.shinyoung.recruit.service.EmailVerificationService;
import com.shinyoung.recruit.service.EmailVerificationState;
import com.shinyoung.recruit.service.nice.NiceVerificationService;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/auth/applicants")
public class ApplicantSignUpController {

    private static final String SIGNUP_EMAIL_KEY = EmailVerificationService.sessionKey(EmailVerificationPurpose.SIGNUP);

    private final ApplicantSignUpService applicantSignUpService;
    private final NiceVerificationService niceVerificationService;
    private final EmailVerificationService emailVerificationService;

    public ApplicantSignUpController(
            ApplicantSignUpService applicantSignUpService,
            NiceVerificationService niceVerificationService,
            EmailVerificationService emailVerificationService) {
        this.applicantSignUpService = applicantSignUpService;
        this.niceVerificationService = niceVerificationService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/sign-up")
    public ResponseEntity<ApiResponse<ApplicantSignUpResponse>> signUp(
            @Valid @RequestBody ApplicantSignUpRequest request, HttpSession session) {
        // 세션에서 인증 결과를 꺼내 유효성을 확인한 뒤 서비스에 값으로 넘긴다.
        // 서비스가 HttpSession 을 알면 단위 테스트가 서블릿 컨테이너에 묶인다.
        NiceVerifiedIdentity identity = niceVerificationService.requireFresh(
                (NiceVerifiedIdentity) session.getAttribute(
                        NiceVerificationController.VERIFIED_SESSION_KEY),
                NiceVerificationPurpose.SIGNUP);
        // 가입 이메일은 같은 세션에서 인증번호를 확인한 주소여야 한다(확인 후 10분).
        emailVerificationService.requireVerified(
                (EmailVerificationState) session.getAttribute(SIGNUP_EMAIL_KEY),
                EmailVerificationPurpose.SIGNUP, request.email());

        ApplicantSignUpResponse response = applicantSignUpService.signUp(request, identity);

        // 1회용이다. 남겨 두면 한 번의 인증으로 여러 계정을 만들 수 있다.
        session.removeAttribute(NiceVerificationController.VERIFIED_SESSION_KEY);
        session.removeAttribute(SIGNUP_EMAIL_KEY);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 가입 이메일 인증번호 발송. 발송이 접수된 뒤에만 세션 상태를 바꾼다(실패하면 예외로 끝나 이전 상태가 남는다). */
    @PostMapping("/email-verification/send")
    public ResponseEntity<ApiResponse<Void>> sendEmailVerification(
            @Valid @RequestBody EmailVerificationSendRequest request, HttpSession session) {
        EmailVerificationState state = applicantSignUpService.sendEmailVerification(
                (EmailVerificationState) session.getAttribute(SIGNUP_EMAIL_KEY), request.email());
        session.setAttribute(SIGNUP_EMAIL_KEY, state);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/email-verification/verify")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Valid @RequestBody EmailVerificationConfirmRequest request, HttpSession session) {
        EmailVerificationState state = (EmailVerificationState) session.getAttribute(SIGNUP_EMAIL_KEY);
        try {
            emailVerificationService.verify(state, EmailVerificationPurpose.SIGNUP, request.email(), request.code());
        } finally {
            // 실패 수·확인 시각은 상태 객체 안에서 바뀐다. 세션 저장소가 바뀐 값을 알도록 다시 넣는다.
            if (state != null) {
                session.setAttribute(SIGNUP_EMAIL_KEY, state);
            }
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 가입 화면용 advisory 이메일 중복체크. email 입력값이 있을 때만 호출한다.
     */
    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<ApplicantEmailAvailabilityResponse>> checkEmail(
            @RequestParam
            @NotBlank(message = "email은 필수입니다.")
            @Email(message = "유효한 이메일 형식이어야 합니다.")
            @Size(max = 255, message = "email은 255자 이하여야 합니다.")
            String email) {
        return ResponseEntity.ok(ApiResponse.success(applicantSignUpService.checkEmailAvailability(email)));
    }
}
```

`{BE}/config/SecurityConfig.java` 107행 매처를 아래로 바꾼다:

```java
                .requestMatchers("/api/auth/login", "/api/auth/logout", "/api/auth/applicants/sign-up", "/api/auth/applicants/check-email", "/api/auth/applicants/find-email",
                        "/api/auth/applicants/email-verification/send", "/api/auth/applicants/email-verification/verify").permitAll()
```

- [ ] **Step 4: 통과 확인**

Step 2 명령. Expected: PASS.

- [ ] **Step 5: 검증**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.NiceVerificationControllerTest" --tests "com.shinyoung.recruit.controller.ApplicantAccountRecoveryControllerTest" --no-daemon
```
Expected: PASS(NICE·아이디 찾기 흐름 영향 없음).

---

## Task 7: 비밀번호 재발급 API

**Files:**
- Modify: `{BE}/domain/repository/ApplicantRepository.java`
- Create: `{BE}/dto/request/ApplicantPasswordResetRequest.java`
- Modify: `{BE}/service/ApplicantAccountRecoveryService.java`
- Modify: `{BE}/controller/ApplicantAccountRecoveryController.java`
- Modify: `{BE}/config/SecurityConfig.java`
- Test: `{BT}/controller/ApplicantPasswordResetControllerTest.java`(생성), `{BT}/service/ApplicantAccountRecoveryServiceTest.java`, `{BT}/config/SecurityConfigTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

(1) `{BT}/controller/ApplicantPasswordResetControllerTest.java` 생성:

```java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.service.GatewayResult;
import com.shinyoung.recruit.service.MailGateway;
import com.shinyoung.recruit.service.MailMessage;
import com.shinyoung.recruit.service.SmsGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicantPasswordResetControllerTest {

    private static final Pattern CODE = Pattern.compile("인증번호: (\\d{6})");
    private static final String EMAIL = "reset01@example.com";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ApplicantRepository applicantRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private MailGateway mailGateway;
    @MockitoBean
    private SmsGateway smsGateway;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        SecurityContextHolder.clearContext();
        when(mailGateway.send(any(), anyList(), anyList())).thenReturn(GatewayResult.accepted("TX-RESET-1"));
    }

    @Test
    void 인증번호를_확인하면_새_비밀번호로_바꾸고_인증은_1회용이다() throws Exception {
        Applicant applicant = saveApplicant();
        MockHttpSession session = new MockHttpSession();

        send(session, EMAIL).andExpect(status().isOk());
        ArgumentCaptor<MailMessage> mail = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailGateway).send(mail.capture(), eq(List.of(EMAIL)), eq(List.of("김재설정")));
        assertThat(mail.getValue().text()).startsWith("김재설정님,");
        Matcher matcher = CODE.matcher(mail.getValue().text());
        assertThat(matcher.find()).isTrue();

        confirm(session, EMAIL, matcher.group(1)).andExpect(status().isOk());
        reset(session, EMAIL, "NewPassword1!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Applicant changed = applicantRepository.findById(applicant.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPassword1!", changed.getPassword())).isTrue();
        reset(session, EMAIL, "AnotherPassword1!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이메일 인증이 필요합니다."));
    }

    @Test
    void 가입되지_않은_이메일이면_404() throws Exception {
        send(new MockHttpSession(), "none@example.com")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("가입된 이메일이 아닙니다."));
        verifyNoInteractions(mailGateway);
    }

    @Test
    void 인증_없이_바꾸면_400() throws Exception {
        saveApplicant();

        reset(new MockHttpSession(), EMAIL, "NewPassword1!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이메일 인증이 필요합니다."));
    }

    @Test
    void 새_비밀번호가_8자_미만이면_400() throws Exception {
        reset(new MockHttpSession(), EMAIL, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private Applicant saveApplicant() {
        Applicant applicant = new Applicant(HashUtil.sha256("reset-test-ci"));
        applicant.setLoginId(EMAIL);
        applicant.setName("김재설정");
        applicant.setUserName("김재설정");
        applicant.setPassword(passwordEncoder.encode("OldPassword1!"));
        applicant.setPhoneNumber("01000000000");
        applicant.setEmail(EMAIL);
        return applicantRepository.save(applicant);
    }

    private ResultActions send(MockHttpSession session, String email) throws Exception {
        return mockMvc.perform(post("/api/auth/applicants/password-reset/send")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions confirm(MockHttpSession session, String email, String code) throws Exception {
        return mockMvc.perform(post("/api/auth/applicants/password-reset/verify")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"));
    }

    private ResultActions reset(MockHttpSession session, String email, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/auth/applicants/password-reset")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"newPassword\":\"" + newPassword + "\"}"));
    }
}
```

(2) `{BT}/service/ApplicantAccountRecoveryServiceTest.java`:
- import 추가: `com.shinyoung.recruit.enumeration.EmailVerificationPurpose`, `org.springframework.security.crypto.password.PasswordEncoder`, `java.time.LocalDateTime`, `static org.mockito.Mockito.verifyNoInteractions`.
- 필드 추가(`@Mock private ApplicantRepository applicantRepository;` 아래):
```java
    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private PasswordEncoder passwordEncoder;
```
- `setUp`:
```java
        service = new ApplicantAccountRecoveryService(applicantRepository, auditHmac, emailVerificationService, passwordEncoder);
```
- 테스트 추가:
```java
    @Test
    void 가입되지_않은_이메일이면_재설정_메일을_보내지_않는다() {
        given(applicantRepository.findByEmail("none@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendPasswordResetCode(null, " none@example.com "))
                .isInstanceOf(ApplicantNotFoundException.class)
                .hasMessage("가입된 이메일이 아닙니다.");
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void 재설정_메일은_지원자_이름으로_보낸다() {
        Applicant applicant = applicantWithEmail("reset@example.com", "김재설정");
        EmailVerificationState state = EmailVerificationState.issued(EmailVerificationPurpose.PASSWORD_RESET,
                "reset@example.com", "hash", LocalDateTime.of(2026, 9, 23, 10, 5), LocalDateTime.of(2026, 9, 23, 10, 0));
        given(applicantRepository.findByEmail("reset@example.com")).willReturn(Optional.of(applicant));
        given(emailVerificationService.send(null, EmailVerificationPurpose.PASSWORD_RESET, "reset@example.com", "김재설정"))
                .willReturn(state);

        assertThat(service.sendPasswordResetCode(null, "reset@example.com")).isSameAs(state);
    }

    @Test
    void 새_비밀번호는_인코딩해_저장한다() {
        Applicant applicant = applicantWithEmail("reset@example.com", "김재설정");
        given(applicantRepository.findByEmail("reset@example.com")).willReturn(Optional.of(applicant));
        given(passwordEncoder.encode("NewPassword1!")).willReturn("encoded-new");

        service.resetPassword("reset@example.com", "NewPassword1!");

        assertThat(applicant.getPassword()).isEqualTo("encoded-new");
    }

    private static Applicant applicantWithEmail(String email, String name) {
        Applicant applicant = new Applicant("test-ci-hash");
        applicant.setLoginId(email);
        applicant.setName(name);
        applicant.setEmail(email);
        return applicant;
    }
```

(3) `{BT}/config/SecurityConfigTest.java` — Task 6에서 추가한 테스트 아래에 추가:
```java
    @Test
    void 재설정_인증번호_발송은_비인증이어도_인가를_통과() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/password-reset/send"))
                .andExpect(status().is(allOf(not(401), not(403))));
    }

    @Test
    void 재설정_인증번호_확인은_비인증이어도_인가를_통과() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/password-reset/verify"))
                .andExpect(status().is(allOf(not(401), not(403))));
    }

    @Test
    void 비밀번호_재설정은_비인증이어도_인가를_통과() throws Exception {
        mockMvc.perform(post("/api/auth/applicants/password-reset"))
                .andExpect(status().is(allOf(not(401), not(403))));
    }
```

- [ ] **Step 2: 실패 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.ApplicantPasswordResetControllerTest" --tests "com.shinyoung.recruit.service.ApplicantAccountRecoveryServiceTest" --tests "com.shinyoung.recruit.config.SecurityConfigTest" --no-daemon
```
Expected: FAIL — 컴파일 오류(`findByEmail`·4인자 생성자·`sendPasswordResetCode` 없음). SecurityConfigTest 신규 3개는 구현 전에도 통과한다.

- [ ] **Step 3: 구현**

`{BE}/domain/repository/ApplicantRepository.java` — `findByLoginId` 아래에 추가:
```java
    Optional<Applicant> findByEmail(String email);
```

`{BE}/dto/request/ApplicantPasswordResetRequest.java`:

```java
package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 로그인 전 비밀번호 재설정. 같은 세션에서 인증번호를 확인한 뒤 10분 안에 보낸다. 비밀번호 규칙은 가입과 같다. */
public record ApplicantPasswordResetRequest(
        @NotBlank(message = "email은 필수입니다.")
        @Email(message = "유효한 이메일 형식이어야 합니다.")
        @Size(max = 255, message = "email은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "newPassword는 필수입니다.")
        @Size(min = 8, max = 100, message = "newPassword는 8자 이상 100자 이하여야 합니다.")
        String newPassword
) {
}
```

`{BE}/service/ApplicantAccountRecoveryService.java` 전체:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.dto.response.ApplicantFindEmailResponse;
import com.shinyoung.recruit.enumeration.EmailVerificationPurpose;
import com.shinyoung.recruit.exception.ApplicantNotFoundException;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 전 계정 복구: 아이디(이메일) 찾기, 비밀번호 재설정.
 *
 * <p>세션을 직접 만지지 않는다. 컨트롤러가 세션의 NICE 인증 결과·이메일 인증 상태를 검사·소비한 뒤 값으로 넘긴다
 * — 서비스가 {@code HttpSession} 을 알면 단위 테스트가 서블릿 컨테이너에 묶인다.
 */
@Service
public class ApplicantAccountRecoveryService {

    private static final String NOT_FOUND_MESSAGE = "본인인증 정보와 일치하는 계정이 없습니다.";
    private static final String EMAIL_NOT_FOUND_MESSAGE = "가입된 이메일이 아닙니다.";

    private final ApplicantRepository applicantRepository;
    private final AuditHmac auditHmac;
    private final EmailVerificationService emailVerificationService;
    private final PasswordEncoder passwordEncoder;

    public ApplicantAccountRecoveryService(ApplicantRepository applicantRepository, AuditHmac auditHmac,
                                           EmailVerificationService emailVerificationService,
                                           PasswordEncoder passwordEncoder) {
        this.applicantRepository = applicantRepository;
        this.auditHmac = auditHmac;
        this.emailVerificationService = emailVerificationService;
        this.passwordEncoder = passwordEncoder;
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
     * 가입 이메일로 비밀번호 재설정 인증번호를 보내고 세션에 둘 상태를 돌려준다. 가입된 지원자가 없으면 404
     * (계정 열거 감수 — 기존 결정). 트랜잭션을 걸지 않는다 — 발송 이력은 SystemMailService 가 먼저 커밋한다.
     */
    public EmailVerificationState sendPasswordResetCode(EmailVerificationState previous, String email) {
        Applicant applicant = findByEmail(email);
        return emailVerificationService.send(previous, EmailVerificationPurpose.PASSWORD_RESET,
                applicant.getEmail(), applicant.getName());
    }

    /** 인증을 마친 이메일의 비밀번호를 바꾼다(BCrypt). 다른 로그인 세션은 건드리지 않는다. */
    @Transactional
    public void resetPassword(String email, String newPassword) {
        findByEmail(email).changePassword(passwordEncoder.encode(newPassword));
    }

    private Applicant findByEmail(String email) {
        return applicantRepository.findByEmail(email.trim())
                .orElseThrow(() -> new ApplicantNotFoundException(EMAIL_NOT_FOUND_MESSAGE));
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

`{BE}/controller/ApplicantAccountRecoveryController.java` 전체:

```java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ApplicantPasswordResetRequest;
import com.shinyoung.recruit.dto.request.EmailVerificationConfirmRequest;
import com.shinyoung.recruit.dto.request.EmailVerificationSendRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ApplicantFindEmailResponse;
import com.shinyoung.recruit.enumeration.EmailVerificationPurpose;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.service.ApplicantAccountRecoveryService;
import com.shinyoung.recruit.service.EmailVerificationService;
import com.shinyoung.recruit.service.EmailVerificationState;
import com.shinyoung.recruit.service.nice.NiceVerificationService;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 전 계정 복구: 아이디(이메일) 찾기, 비밀번호 재설정(가입 이메일 인증번호). */
@RestController
@RequestMapping("/auth/applicants")
public class ApplicantAccountRecoveryController {

    private static final String RESET_EMAIL_KEY = EmailVerificationService.sessionKey(EmailVerificationPurpose.PASSWORD_RESET);

    private final ApplicantAccountRecoveryService applicantAccountRecoveryService;
    private final NiceVerificationService niceVerificationService;
    private final EmailVerificationService emailVerificationService;

    public ApplicantAccountRecoveryController(
            ApplicantAccountRecoveryService applicantAccountRecoveryService,
            NiceVerificationService niceVerificationService,
            EmailVerificationService emailVerificationService) {
        this.applicantAccountRecoveryService = applicantAccountRecoveryService;
        this.niceVerificationService = niceVerificationService;
        this.emailVerificationService = emailVerificationService;
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

    /** 비밀번호 재설정 인증번호 발송. 발송이 접수된 뒤에만 세션 상태를 바꾼다. */
    @PostMapping("/password-reset/send")
    public ResponseEntity<ApiResponse<Void>> sendPasswordResetCode(
            @Valid @RequestBody EmailVerificationSendRequest request, HttpSession session) {
        EmailVerificationState state = applicantAccountRecoveryService.sendPasswordResetCode(
                (EmailVerificationState) session.getAttribute(RESET_EMAIL_KEY), request.email());
        session.setAttribute(RESET_EMAIL_KEY, state);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/password-reset/verify")
    public ResponseEntity<ApiResponse<Void>> verifyPasswordResetCode(
            @Valid @RequestBody EmailVerificationConfirmRequest request, HttpSession session) {
        EmailVerificationState state = (EmailVerificationState) session.getAttribute(RESET_EMAIL_KEY);
        try {
            emailVerificationService.verify(state, EmailVerificationPurpose.PASSWORD_RESET, request.email(), request.code());
        } finally {
            // 실패 수·확인 시각은 상태 객체 안에서 바뀐다. 세션 저장소가 바뀐 값을 알도록 다시 넣는다.
            if (state != null) {
                session.setAttribute(RESET_EMAIL_KEY, state);
            }
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/password-reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ApplicantPasswordResetRequest request, HttpSession session) {
        emailVerificationService.requireVerified(
                (EmailVerificationState) session.getAttribute(RESET_EMAIL_KEY),
                EmailVerificationPurpose.PASSWORD_RESET, request.email());
        applicantAccountRecoveryService.resetPassword(request.email(), request.newPassword());
        // 1회용이다. 남겨 두면 10분 안에 같은 인증으로 여러 번 바꿀 수 있다.
        session.removeAttribute(RESET_EMAIL_KEY);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
```

`{BE}/config/SecurityConfig.java` — Task 6에서 바꾼 매처에 경로 3개를 더한다:

```java
                .requestMatchers("/api/auth/login", "/api/auth/logout", "/api/auth/applicants/sign-up", "/api/auth/applicants/check-email", "/api/auth/applicants/find-email",
                        "/api/auth/applicants/email-verification/send", "/api/auth/applicants/email-verification/verify",
                        "/api/auth/applicants/password-reset/send", "/api/auth/applicants/password-reset/verify",
                        "/api/auth/applicants/password-reset").permitAll()
```

- [ ] **Step 4: 통과 확인**

Step 2 명령. Expected: PASS.

- [ ] **Step 5: 검증**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.ApplicantAccountRecoveryControllerTest" --tests "com.shinyoung.recruit.domain.repository.ApplicantRepositoryTest" --no-daemon
```
Expected: PASS.

---

## Task 8: 제출 완료 이벤트·리스너

**Files:**
- Create: `{BE}/service/ApplicationSubmittedEvent.java`
- Modify: `{BE}/service/JobApplicationService.java`
- Modify: `{BE}/service/MessageTargetService.java`(`firstNonBlank` 가시성)
- Create: `{BE}/service/ApplicationSubmittedMailListener.java`
- Test: `{BT}/service/JobApplicationServiceTest.java`, `{BT}/service/ApplicationSubmittedMailListenerTest.java`(생성)

- [ ] **Step 1: 실패하는 테스트 작성**

(1) `{BT}/service/JobApplicationServiceTest.java`:
- import 추가: `org.springframework.test.context.event.ApplicationEvents`, `org.springframework.test.context.event.RecordApplicationEvents`.
- 클래스 선언부 `@Transactional` 아래에 `@RecordApplicationEvents` 추가.
- 필드 추가:
```java
    @Autowired
    private ApplicationEvents applicationEvents;
```
- `submit_draft_application_success` 끝에 추가:
```java
        assertThat(applicationEvents.stream(ApplicationSubmittedEvent.class))
                .extracting(ApplicationSubmittedEvent::applicationId)
                .containsExactly(submittedId);
```

(2) `{BT}/service/ApplicationSubmittedMailListenerTest.java` 생성:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.MessageOrigin;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationSubmittedMailListenerTest {

    @Autowired
    private ApplicationSubmittedMailListener listener;
    @Autowired
    private JobPostingRepository jobPostingRepository;
    @Autowired
    private ApplicantRepository applicantRepository;
    @Autowired
    private JobApplicationRepository jobApplicationRepository;
    @Autowired
    private ApplicationBasicInfoRepository applicationBasicInfoRepository;
    @Autowired
    private MessageSendRepository messageSendRepository;

    @MockitoBean
    private MailGateway mailGateway;
    @MockitoBean
    private SmsGateway smsGateway;

    private JobPosting posting;

    @BeforeEach
    void setUp() {
        posting = JobPosting.create("제출 메일 공고", "Content",
                LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 22, 18, 0));
        posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        posting = jobPostingRepository.saveAndFlush(posting);
        when(mailGateway.send(any(), anyList(), anyList())).thenReturn(GatewayResult.accepted("TX-SUBMIT-1"));
    }

    @Test
    void 기본정보_이메일로_제출_완료_메일을_보내고_시스템_이력에_공고를_남긴다() {
        JobApplication application = submitted("김회원", "member@example.com");
        basicInfo(application, "김제출", "basic@example.com");

        listener.sendSubmittedMail(application.getId());

        ArgumentCaptor<MailMessage> mail = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailGateway).send(mail.capture(), eq(List.of("basic@example.com")), eq(List.of("김제출")));
        assertThat(mail.getValue().subject()).isEqualTo("[신영증권 채용] 제출 메일 공고 지원서 제출 완료 안내");
        assertThat(mail.getValue().text()).contains("김제출님, 제출 메일 공고 지원서가 2026-09-10 09:00에 제출되었습니다.");
        MessageSend send = messageSendRepository.findAll().stream()
                .filter(row -> row.getType() == MessageType.APPLICATION_SUBMITTED)
                .max(Comparator.comparing(MessageSend::getId))
                .orElseThrow();
        assertThat(send.getOrigin()).isEqualTo(MessageOrigin.SYSTEM);
        assertThat(send.getJobPosting().getId()).isEqualTo(posting.getId());
    }

    @Test
    void 기본정보_이메일이_없으면_회원_이메일로_보낸다() {
        JobApplication application = submitted("김회원", "member@example.com");

        listener.sendSubmittedMail(application.getId());

        verify(mailGateway).send(any(), eq(List.of("member@example.com")), eq(List.of("김회원")));
    }

    @Test
    void 받을_주소가_없으면_보내지_않는다() {
        JobApplication application = submitted("김주소없음", null);

        listener.sendSubmittedMail(application.getId());

        verifyNoInteractions(mailGateway);
    }

    private JobApplication submitted(String name, String email) {
        Applicant applicant = new Applicant(HashUtil.sha256("test-ci-" + UUID.randomUUID()));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName(name);
        applicant.setUserName(name);
        applicant.setEmail(email);
        applicant.setPhoneNumber("01000000000");
        applicantRepository.saveAndFlush(applicant);
        JobPosition jobPosition = posting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant, posting, jobPosition, name, posting.getTitle(), jobPosition.getPositionName());
        application.submit(LocalDateTime.of(2026, 9, 10, 9, 0));
        return jobApplicationRepository.saveAndFlush(application);
    }

    private void basicInfo(JobApplication application, String name, String email) {
        applicationBasicInfoRepository.saveAndFlush(ApplicationBasicInfo.create(
                application, name, null, NationalityType.DOMESTIC, null,
                LocalDate.of(1995, 1, 1), "01000000000", null, email,
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT,
                null, null, null, null, null, null));
    }
}
```

- [ ] **Step 2: 실패 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.JobApplicationServiceTest" --tests "com.shinyoung.recruit.service.ApplicationSubmittedMailListenerTest" --no-daemon
```
Expected: FAIL — 컴파일 오류(`ApplicationSubmittedEvent`·`ApplicationSubmittedMailListener` 없음).

- [ ] **Step 3: 구현**

`{BE}/service/ApplicationSubmittedEvent.java`:

```java
package com.shinyoung.recruit.service;

/** 지원서 최종 제출(재제출 포함) 성공. 커밋 뒤 ApplicationSubmittedMailListener 가 제출 완료 메일을 보낸다. */
public record ApplicationSubmittedEvent(Long applicationId) {
}
```

`{BE}/service/JobApplicationService.java`:
1. import 추가: `org.springframework.context.ApplicationEventPublisher`.
2. 필드 `private final AdminApplicationSearchConditionFactory searchConditionFactory;` 아래에 추가:
```java
    private final ApplicationEventPublisher eventPublisher;
```
3. `submit`의 `application.submit(LocalDateTime.now(clock));` 아래에 추가:
```java
        eventPublisher.publishEvent(new ApplicationSubmittedEvent(application.getId()));
```

`{BE}/service/MessageTargetService.java` — `private static String firstNonBlank(String... values)`의 `private`를 지운다(`static String firstNonBlank`). 제출 메일이 대상자 조회와 같은 이름·주소 규칙(빈 값·파기 표식 건너뜀)을 쓰게 한다.

`{BE}/service/ApplicationSubmittedMailListener.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.MessageVariable;
import com.shinyoung.recruit.enumeration.SystemMailOutcome;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 제출 완료 메일(설계서 6.5). 제출 트랜잭션이 커밋되면 비동기로 지원서·기본정보·공고를 읽고
 * SystemMailService 로 보낸다(이미 비동기 스레드라 동기 디스패치). 받는 주소는 기본정보 이메일 → 회원 이메일,
 * 둘 다 없으면 보내지 않는다. 예외는 경고 로그만 남기고 제출 결과에는 영향을 주지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ApplicationSubmittedMailListener {

    private static final Logger log = LoggerFactory.getLogger(ApplicationSubmittedMailListener.class);
    private static final DateTimeFormatter SUBMITTED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JobApplicationRepository jobApplicationRepository;
    private final ApplicationBasicInfoRepository applicationBasicInfoRepository;
    private final SystemMailService systemMailService;
    private final PlatformTransactionManager transactionManager;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmitted(ApplicationSubmittedEvent event) {
        try {
            sendSubmittedMail(event.applicationId());
        } catch (RuntimeException e) {
            log.warn("제출 완료 메일 처리 실패: applicationId={}, error={}",
                    event.applicationId(), e.getClass().getSimpleName());
        }
    }

    /** 제출 완료 메일 1통. 읽기는 짧은 읽기 트랜잭션에서 끝내고, 발송은 트랜잭션 밖에서 부른다(이력 커밋 후 디스패치). */
    public void sendSubmittedMail(Long applicationId) {
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        SubmittedMail mail = readOnly.execute(status -> load(applicationId));
        if (mail == null) {
            log.warn("제출 완료 메일 대상 지원서가 없습니다: applicationId={}", applicationId);
            return;
        }
        if (mail.email() == null) {
            log.warn("제출 완료 메일 받을 주소가 없어 보내지 않습니다: applicationId={}", applicationId);
            return;
        }
        SystemMailOutcome outcome = systemMailService.send(MessageType.APPLICATION_SUBMITTED, mail.email(), mail.name(),
                Map.of(MessageVariable.JOB_POSTING_TITLE.getKey(), mail.jobPostingTitle(),
                        MessageVariable.SUBMITTED_AT.getKey(), mail.submittedAt()),
                mail.jobPosting(), mail.application());
        if (outcome != SystemMailOutcome.ACCEPTED) {
            log.warn("제출 완료 메일을 보내지 못했습니다: applicationId={}, outcome={}", applicationId, outcome);
        }
    }

    private SubmittedMail load(Long applicationId) {
        JobApplication application = jobApplicationRepository.findById(applicationId).orElse(null);
        if (application == null) {
            return null;
        }
        ApplicationBasicInfo info = applicationBasicInfoRepository.findByJobApplicationId(applicationId).orElse(null);
        Applicant applicant = application.getApplicant();
        JobPosting jobPosting = application.getJobPosting();
        String email = MessageTargetService.firstNonBlank(info == null ? null : info.getEmail(), applicant.getEmail());
        String name = MessageTargetService.firstNonBlank(info == null ? null : info.getNameKorean(),
                applicant.getUserName(), application.getApplicantNameSnapshot());
        return new SubmittedMail(application, jobPosting, email, name, jobPosting.getTitle(),
                application.getSubmittedAt().format(SUBMITTED_AT_FORMAT));
    }

    private record SubmittedMail(JobApplication application, JobPosting jobPosting, String email, String name,
                                 String jobPostingTitle, String submittedAt) {
    }
}
```

- [ ] **Step 4: 통과 확인**

Step 2 명령. Expected: PASS.

- [ ] **Step 5: 검증**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.ApplicationControllerTest" --tests "com.shinyoung.recruit.service.MessageTargetServiceTest" --no-daemon
```
Expected: PASS.

---

## Task 9: 발송 이력 `origin` 필터·응답 필드

**Files:**
- Modify: `{BE}/dto/condition/MessageHistoryCondition.java`
- Modify: `{BE}/domain/repository/MessageSendRepository.java`
- Modify: `{BE}/service/MessageHistoryService.java`
- Modify: `{BE}/controller/MessageHistoryAdminController.java`
- Modify: `{BE}/dto/response/MessageSendSummaryResponse.java`, `{BE}/dto/response/MessageSendDetailResponse.java`
- Test: `{BT}/service/MessageHistoryServiceTest.java`, `{BT}/controller/MessageHistoryAdminControllerTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

(1) `{BT}/service/MessageHistoryServiceTest.java`:
- import 추가: `com.shinyoung.recruit.domain.entity.MessageTemplate`, `com.shinyoung.recruit.enumeration.MessageOrigin`.
- `new MessageHistoryCondition(` 3곳에 마지막 인자 `null`을 더한다:
  - `new MessageHistoryCondition(null, null, null, null, null, null)`
  - `new MessageHistoryCondition(today, today.minusDays(1), null, null, null, null)`
  - `search` helper: `new MessageHistoryCondition(from, to, type, posting.getId(), test, null)`
- 테스트 추가:
```java
    @Test
    void 발송_구분으로_거르고_공고_없는_시스템_발송은_공고_조건에서_빠진다() {
        MessageSend admin = saveSend(MessageType.FREE, false, true, false, now.minusMinutes(2));
        MessageSend system = messageSendRepository.saveAndFlush(MessageSend.createSystem(
                MessageType.SIGNUP_VERIFICATION, null,
                MessageTemplate.create(MessageType.SIGNUP_VERIFICATION, "회원가입 인증 메일", true,
                        "[신영증권 채용] 회원가입 이메일 인증번호", "인증번호: #{인증번호}", null),
                now.minusMinutes(1)));

        List<Long> systemOnly = ids(messageHistoryService.search(
                new MessageHistoryCondition(null, null, null, null, null, MessageOrigin.SYSTEM), 0, 100));
        List<Long> adminOnly = ids(messageHistoryService.search(
                new MessageHistoryCondition(null, null, null, null, null, MessageOrigin.ADMIN), 0, 100));

        assertThat(systemOnly).contains(system.getId()).doesNotContain(admin.getId());
        assertThat(adminOnly).contains(admin.getId()).doesNotContain(system.getId());
        assertThat(ids(search(null, null, null, null))).containsExactly(admin.getId());
        MessageSendSummaryResponse summary = messageHistoryService.search(
                        new MessageHistoryCondition(null, null, MessageType.SIGNUP_VERIFICATION, null, null, MessageOrigin.SYSTEM), 0, 100)
                .content().stream()
                .filter(row -> row.id().equals(system.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(summary.origin()).isEqualTo(MessageOrigin.SYSTEM);
        assertThat(summary.jobPostingTitle()).isNull();
        assertThat(messageHistoryService.detail(system.getId()).origin()).isEqualTo(MessageOrigin.SYSTEM);
    }
```

(2) `{BT}/controller/MessageHistoryAdminControllerTest.java`:
- `이력_목록을_준다`에 단언 추가: `.andExpect(jsonPath("$.data.content[0].origin").value("ADMIN"))`
- 테스트 추가:
```java
    @Test
    void 이력_목록은_발송_구분으로_거른다() throws Exception {
        mockMvc.perform(get("/api/admin/messages/history")
                        .param("jobPostingId", String.valueOf(posting.getId()))
                        .param("origin", "SYSTEM")
                        .with(authentication(employee())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }
```

- [ ] **Step 2: 실패 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageHistoryServiceTest" --tests "com.shinyoung.recruit.controller.MessageHistoryAdminControllerTest" --no-daemon
```
Expected: FAIL — 컴파일 오류(`MessageHistoryCondition` 6인자·`origin()` 없음).

- [ ] **Step 3: 구현**

`{BE}/dto/condition/MessageHistoryCondition.java` 전체:

```java
package com.shinyoung.recruit.dto.condition;

import com.shinyoung.recruit.enumeration.MessageOrigin;
import com.shinyoung.recruit.enumeration.MessageType;

import java.time.LocalDate;

/**
 * 발송 이력 검색 조건. null 은 "조건 없음": from·to 가 없으면 종료일 오늘, 시작일 = 종료일 - 29일(최근 30일, 양끝 포함).
 * test null = 실발송+테스트, true = 테스트만, false = 실발송만. origin null = 관리자 발송+시스템 자동발송.
 * jobPostingId 로 거르면 공고 없는 시스템 발송(가입 인증·비밀번호 재설정)은 빠진다.
 */
public record MessageHistoryCondition(
        LocalDate from,
        LocalDate to,
        MessageType type,
        Long jobPostingId,
        Boolean test,
        MessageOrigin origin
) {
}
```

`{BE}/domain/repository/MessageSendRepository.java` 전체:

```java
package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.enumeration.MessageOrigin;
import com.shinyoung.recruit.enumeration.MessageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface MessageSendRepository extends JpaRepository<MessageSend, Long> {

    /** 발송 이력 검색. 조건이 null 이면 적용하지 않는다. requestedAt 은 [from, to) 범위, 최신순 고정 정렬. */
    @EntityGraph(attributePaths = {"jobPosting", "stage"})
    @Query("""
            select s
            from MessageSend s
            where s.requestedAt >= :from and s.requestedAt < :to
              and (:type is null or s.type = :type)
              and (:jobPostingId is null or s.jobPosting.id = :jobPostingId)
              and (:test is null or s.test = :test)
              and (:origin is null or s.origin = :origin)
            order by s.requestedAt desc, s.id desc
            """)
    Page<MessageSend> search(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("type") MessageType type,
            @Param("jobPostingId") Long jobPostingId,
            @Param("test") Boolean test,
            @Param("origin") MessageOrigin origin,
            Pageable pageable
    );
}
```

`{BE}/service/MessageHistoryService.java` — `search`의 저장소 호출을 바꾼다:
```java
        Page<MessageSend> sends = messageSendRepository.search(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                condition.type(), condition.jobPostingId(), condition.test(), condition.origin(),
                PageRequest.of(page, size));
```

`{BE}/controller/MessageHistoryAdminController.java`:
1. import 추가: `com.shinyoung.recruit.enumeration.MessageOrigin`.
2. `search` 파라미터 `@RequestParam(required = false) Boolean test,` 아래에 추가:
```java
            @RequestParam(required = false) MessageOrigin origin,
```
3. 조건 생성: `new MessageHistoryCondition(from, to, type, jobPostingId, test, origin);`

`{BE}/dto/response/MessageSendSummaryResponse.java` 전체:

```java
package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.enumeration.MessageOrigin;
import com.shinyoung.recruit.enumeration.MessageSendStatus;
import com.shinyoung.recruit.enumeration.MessageType;

import java.time.LocalDateTime;

/**
 * 발송 이력 목록 1행. status·건수·delayed 는 조회할 때 수신자 채널 상태로 계산한 값이다(설계서 7.4).
 * title = 메일을 켰으면 메일 제목, 아니면 SMS 원문 앞 40자. 공고 없는 시스템 발송은 jobPostingTitle 이 null.
 */
public record MessageSendSummaryResponse(
        Long id,
        LocalDateTime requestedAt,
        MessageType type,
        boolean test,
        MessageOrigin origin,
        String jobPostingTitle,
        String stageName,
        String conditionSummary,
        String title,
        boolean mailEnabled,
        boolean smsEnabled,
        int recipientCount,
        MessageChannelCountResponse mail,
        MessageChannelCountResponse sms,
        MessageSendStatus status,
        boolean delayed,
        String senderName
) {

    private static final int SMS_TITLE_LENGTH = 40;

    public static MessageSendSummaryResponse of(MessageSend send, MessageChannelCountResponse mail,
                                                MessageChannelCountResponse sms, MessageSendStatus status,
                                                boolean delayed) {
        return new MessageSendSummaryResponse(
                send.getId(),
                send.getRequestedAt(),
                send.getType(),
                send.isTest(),
                send.getOrigin(),
                send.getJobPosting() == null ? null : send.getJobPosting().getTitle(),
                send.getStage() == null ? null : send.getStage().getStageName(),
                send.getConditionSummary(),
                titleOf(send),
                send.isMailEnabled(),
                send.isSmsEnabled(),
                send.getRecipientCount(),
                mail,
                sms,
                status,
                delayed,
                send.getSenderName()
        );
    }

    private static String titleOf(MessageSend send) {
        if (send.isMailEnabled()) {
            return send.getMailSubject();
        }
        String smsBody = send.getSmsBody();
        if (smsBody == null || smsBody.codePointCount(0, smsBody.length()) <= SMS_TITLE_LENGTH) {
            return smsBody;
        }
        return smsBody.substring(0, smsBody.offsetByCodePoints(0, SMS_TITLE_LENGTH));
    }
}
```

`{BE}/dto/response/MessageSendDetailResponse.java` 전체:

```java
package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.enumeration.MessageOrigin;
import com.shinyoung.recruit.enumeration.MessageSendStatus;
import com.shinyoung.recruit.enumeration.MessageType;

import java.time.LocalDateTime;
import java.util.List;

/** 발송 이력 상세 = 목록 필드(같은 이름) + 치환 전 원문 + 수신자별 결과(id 순). */
public record MessageSendDetailResponse(
        Long id,
        LocalDateTime requestedAt,
        MessageType type,
        boolean test,
        MessageOrigin origin,
        String jobPostingTitle,
        String stageName,
        String conditionSummary,
        String title,
        boolean mailEnabled,
        boolean smsEnabled,
        int recipientCount,
        MessageChannelCountResponse mail,
        MessageChannelCountResponse sms,
        MessageSendStatus status,
        boolean delayed,
        String senderName,
        String templateName,
        String mailSubject,
        String mailBody,
        String smsBody,
        List<MessageRecipientResponse> recipients
) {

    public static MessageSendDetailResponse of(MessageSendSummaryResponse summary, MessageSend send,
                                               List<MessageRecipientResponse> recipients) {
        return new MessageSendDetailResponse(
                summary.id(),
                summary.requestedAt(),
                summary.type(),
                summary.test(),
                summary.origin(),
                summary.jobPostingTitle(),
                summary.stageName(),
                summary.conditionSummary(),
                summary.title(),
                summary.mailEnabled(),
                summary.smsEnabled(),
                summary.recipientCount(),
                summary.mail(),
                summary.sms(),
                summary.status(),
                summary.delayed(),
                summary.senderName(),
                send.getTemplateName(),
                send.getMailSubject(),
                send.getMailBody(),
                send.getSmsBody(),
                recipients
        );
    }
}
```

- [ ] **Step 4: 통과 확인**

Step 2 명령. Expected: PASS.

- [ ] **Step 5: 검증**

`MessageHistoryCondition`·`search(` 사용처가 더 없는지 확인한다(Grep `new MessageHistoryCondition(`·`messageSendRepository.search(` → 위에서 고친 곳뿐). 이후 Task 2 Step 5 명령을 다시 돌려 PASS를 확인한다.

---

## Task 10: 관리자 발송·테스트 발송에서 시스템 유형 거부

**Files:**
- Modify: `{BE}/service/MessageSendService.java`
- Test: `{BT}/service/MessageSendServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/service/MessageSendServiceTest.java`에 추가(필요한 import는 이미 있다):

```java
    @Test
    void 시스템_자동발송_유형은_발송도_테스트_발송도_거부한다() {
        JobApplication kim = submitted("김민준");

        assertThatThrownBy(() -> messageSendService.send(new MessageSendRequest(
                MessageType.SIGNUP_VERIFICATION, posting.getId(), null, null, null, null,
                List.of(kim.getId()), content("제목", "#{인증번호}", null)), HR))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("시스템 자동발송 유형은 직접 보낼 수 없습니다.");
        assertThatThrownBy(() -> messageSendService.testSend(new MessageTestSendRequest(
                MessageType.APPLICATION_SUBMITTED, posting.getId(), null, null, null, null, kim.getId(),
                List.of(new MessageTesterRequest("김인사", "hr.kim@example.com", null)),
                content("제목", "본문", null)), HR))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("시스템 자동발송 유형은 직접 보낼 수 없습니다.");
    }
```

- [ ] **Step 2: 실패 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageSendServiceTest" --no-daemon
```
Expected: FAIL — 메시지 불일치(`SMS 내용을 입력해야 합니다.`가 먼저 난다).

- [ ] **Step 3: 구현**

`{BE}/service/MessageSendService.java`의 `validateContent` 첫 줄에 추가:

```java
        if (type.isSystem()) {
            throw new InvalidMessageException(MessageTargetService.SYSTEM_TYPE_REJECTED);
        }
```

- [ ] **Step 4: 통과 확인**

Step 2 명령. Expected: PASS.

- [ ] **Step 5: 검증**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.MessageSendCommandControllerTest" --tests "com.shinyoung.recruit.controller.MessageSendAdminControllerTest" --no-daemon
```
Expected: PASS.

---

## Task 11: 프론트 API·타입

**Files:**
- Modify: `{FE}/types/application.ts`, `{FE}/api/applicationApi.ts`
- Modify: `{FE}/types/admin/message.ts`, `{FE}/views/admin/message/messageTypes.ts`, `{FE}/views/admin/message/messageHistory.ts`
- Create: `{FE}/views/admin/message/__tests__/messageTypes.spec.ts`
- Modify: `{FE}/views/admin/message/__tests__/messageHistory.spec.ts`

- [ ] **Step 1: 실패하는 테스트 작성**

`{FE}/views/admin/message/__tests__/messageTypes.spec.ts`:

```ts
import { describe, expect, it } from 'vitest'

import { ALL_MESSAGE_TYPES, MESSAGE_TYPES, isSystemMessageType, messageTypeLabel } from '../messageTypes'

describe('messageTypes', () => {
  it('발송 화면 종류에는 시스템 자동발송이 없고 전체 종류에는 있다', () => {
    expect(MESSAGE_TYPES.map((meta) => meta.type)).toEqual([
      'RESULT_ANNOUNCEMENT',
      'DEADLINE_REMINDER',
      'INTERVIEW_SCHEDULE',
      'INTERVIEW_NOTICE',
      'FREE',
    ])
    expect(ALL_MESSAGE_TYPES.map((meta) => meta.type).slice(5)).toEqual([
      'SIGNUP_VERIFICATION',
      'PASSWORD_RESET',
      'APPLICATION_SUBMITTED',
    ])
  })

  it('시스템 자동발송 종류를 구분하고 라벨을 붙인다', () => {
    expect(isSystemMessageType('PASSWORD_RESET')).toBe(true)
    expect(isSystemMessageType('FREE')).toBe(false)
    expect(messageTypeLabel('APPLICATION_SUBMITTED')).toBe('시스템 자동발송 · 지원서 제출 완료')
    expect(messageTypeLabel('FREE')).toBe('기타 · 직접 입력')
  })
})
```

`{FE}/views/admin/message/__tests__/messageHistory.spec.ts` — `detail` 픽스처의 `test: true,` 아래에 `origin: 'ADMIN',`을 추가한다.

- [ ] **Step 2: 실패 확인**

`recruit_front/`에서:
```bash
npx vitest run src/views/admin/message/__tests__/messageTypes.spec.ts
```
Expected: FAIL — `ALL_MESSAGE_TYPES`·`isSystemMessageType` export 없음.

- [ ] **Step 3: 구현**

`{FE}/types/application.ts` — `FindEmailResponse` 아래에 추가:

```ts
/** 이메일 인증번호 확인 요청(가입·비밀번호 재발급 공용). 같은 세션에서 발송한 번호를 확인한다. */
export interface EmailVerificationRequest {
  email: string
  code: string
}

/** POST /auth/applicants/password-reset — 같은 세션에서 인증번호를 확인한 뒤 10분 안에 보낸다. */
export interface PasswordResetRequest {
  email: string
  newPassword: string
}
```

`{FE}/api/applicationApi.ts` 전체:

```ts
import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { ApplicantStageResult, ApplicationSearchParams, MyApplicationList, ChangePasswordParams, ChangePasswordRequest, SignupUser, checkEmailRequest, FindEmailResponse, EmailVerificationRequest, PasswordResetRequest } from '@/types/application'

export const applicationApi = {

  getMyApplications(params: ApplicationSearchParams){
    return apiClient.get<ApiResponse<MyApplicationList>>('/applications/me', { params })
  },

  getStageResults(applicationId: number) {
    return apiClient.get<ApiResponse<ApplicantStageResult[]>>(`/applications/${applicationId}/stage-results`)
  },

  changePassword(params: ChangePasswordParams){
    return apiClient.post<ApiResponse<ChangePasswordRequest>>('/applicant/account/password', params)
  },

  signup(request: SignupUser) {
    return apiClient.post<ApiResponse<SignupUser>>('/auth/applicants/sign-up', request)
  },
  
  checkEmail(email: string) {
    return apiClient.get<ApiResponse<checkEmailRequest>>('/auth/applicants/check-email', {
      params: {
        email,
      },
    })
  },

  /** 아이디 찾기. 요청 본문은 없다 — 서버가 세션의 NICE 인증 결과(용도 FIND_EMAIL)를 1회 소비한다. */
  findEmail() {
    return apiClient.post<ApiResponse<FindEmailResponse>>('/auth/applicants/find-email')
  },

  /** 가입 이메일 인증번호 발송. 가입된 이메일·60초 안 재요청·발송 실패는 400(서버 문구). */
  sendSignupEmailVerification(email: string) {
    return apiClient.post<ApiResponse<null>>('/auth/applicants/email-verification/send', { email })
  },

  /** 가입 이메일 인증번호 확인. 성공해야 가입할 수 있다(확인 후 10분). */
  verifySignupEmail(request: EmailVerificationRequest) {
    return apiClient.post<ApiResponse<null>>('/auth/applicants/email-verification/verify', request)
  },

  /** 비밀번호 재설정 인증번호 발송. 미가입 이메일은 404(서버 문구). */
  sendPasswordResetCode(email: string) {
    return apiClient.post<ApiResponse<null>>('/auth/applicants/password-reset/send', { email })
  },

  verifyPasswordResetCode(request: EmailVerificationRequest) {
    return apiClient.post<ApiResponse<null>>('/auth/applicants/password-reset/verify', request)
  },

  /** 새 비밀번호 설정. 같은 세션에서 인증번호를 확인한 뒤에만 된다. */
  resetPassword(request: PasswordResetRequest) {
    return apiClient.post<ApiResponse<null>>('/auth/applicants/password-reset', request)
  },

}
```

`{FE}/types/admin/message.ts`:
1. `MessageType` 정의를 아래로 바꾼다(관리자 종류 5개는 그대로):
```ts
/** 관리자가 발송 화면에서 고르는 종류. */
export type MessageType =
  | 'RESULT_ANNOUNCEMENT'
  | 'DEADLINE_REMINDER'
  | 'INTERVIEW_SCHEDULE'
  | 'INTERVIEW_NOTICE'
  | 'FREE'

/** 시스템 자동발송 종류(가입 인증·비밀번호 재설정·제출 완료). 발송 화면에서는 고를 수 없다. */
export type SystemMessageType = 'SIGNUP_VERIFICATION' | 'PASSWORD_RESET' | 'APPLICATION_SUBMITTED'

/** 템플릿·변수·발송 이력에 나오는 전체 종류. */
export type AnyMessageType = MessageType | SystemMessageType

/** 발송 구분. ADMIN 관리자 발송 · SYSTEM 시스템 자동발송. */
export type MessageOrigin = 'ADMIN' | 'SYSTEM'
```
2. 아래 필드 타입을 `AnyMessageType`으로 바꾼다: `MessageTemplate.type`, `MessageTemplateSaveRequest.type`, `MessageVariable.types`(→ `AnyMessageType[]`), `MessageHistoryQuery.type?`, `MessageSendSummary.type`.
3. `MessageHistoryQuery`의 `test?: boolean` 아래에 추가:
```ts
  /** 없으면 관리자 발송+시스템 자동발송 */
  origin?: MessageOrigin
```
4. `MessageSendSummary`의 `test: boolean` 아래에 `origin: MessageOrigin`을 추가하고, `jobPostingTitle: string`을 아래로 바꾼다:
```ts
  /** 공고 없는 시스템 발송(가입 인증·비밀번호 재설정)은 null */
  jobPostingTitle: string | null
```

`{FE}/views/admin/message/messageTypes.ts` 전체:

```ts
import type { AnyMessageType, MessageType, SystemMessageType } from '@/types/admin/message'

export interface MessageTypeMeta<T extends AnyMessageType = MessageType> {
  type: T
  /** 화면 묶음 라벨(공고 관련·면접 안내·기타·시스템 자동발송). 표시용이다. */
  group: string
  name: string
  description: string
}

/** 표시 순서 = 발송 화면 종류 카드 순서(설계서 2절). 관리자가 직접 보내는 종류만 담는다. */
export const MESSAGE_TYPES: MessageTypeMeta[] = [
  { type: 'RESULT_ANNOUNCEMENT', group: '공고 관련', name: '결과 발표', description: '전형 결과 발표 후 안내' },
  { type: 'DEADLINE_REMINDER', group: '공고 관련', name: '서류 마감 임박', description: '미제출 지원자에게 리마인드' },
  { type: 'INTERVIEW_SCHEDULE', group: '면접 안내', name: '면접 일정·장소', description: '배정된 일시·장소 개별 안내' },
  { type: 'INTERVIEW_NOTICE', group: '면접 안내', name: '면접 공지', description: '준비물·유의사항·변경 공지' },
  { type: 'FREE', group: '기타', name: '직접 입력', description: '내용을 자유롭게 작성' },
]

/** 시스템 자동발송 종류. 템플릿·발송 이력 화면에만 나오고 발송 화면에서는 고를 수 없다. */
export const SYSTEM_MESSAGE_TYPES: MessageTypeMeta<SystemMessageType>[] = [
  { type: 'SIGNUP_VERIFICATION', group: '시스템 자동발송', name: '회원가입 인증', description: '가입 이메일 인증번호' },
  { type: 'PASSWORD_RESET', group: '시스템 자동발송', name: '비밀번호 재설정 인증', description: '비밀번호 재발급 인증번호' },
  { type: 'APPLICATION_SUBMITTED', group: '시스템 자동발송', name: '지원서 제출 완료', description: '최종 제출·재제출 안내' },
]

/** 템플릿·발송 이력 화면용 전체 종류(관리자 종류 → 시스템 종류). */
export const ALL_MESSAGE_TYPES: MessageTypeMeta<AnyMessageType>[] = [...MESSAGE_TYPES, ...SYSTEM_MESSAGE_TYPES]

export const isSystemMessageType = (type: AnyMessageType): type is SystemMessageType =>
  SYSTEM_MESSAGE_TYPES.some((meta) => meta.type === type)

export const messageTypeLabel = (type: AnyMessageType): string => {
  const meta = ALL_MESSAGE_TYPES.find((item) => item.type === type)
  return meta ? `${meta.group} · ${meta.name}` : type
}
```

`{FE}/views/admin/message/messageHistory.ts`:
1. 타입 import 목록에 `MessageOrigin`을 추가한다.
2. `HISTORY_RANGE_DAYS` 아래에 추가:
```ts
/** 발송 구분 라벨(이력 목록 열·필터). */
export const MESSAGE_ORIGIN_LABEL: Record<MessageOrigin, string> = {
  ADMIN: '관리자 발송',
  SYSTEM: '시스템 자동발송',
}
```

- [ ] **Step 4: 통과 확인**

```bash
npx vitest run src/views/admin/message
```
Expected: PASS.

- [ ] **Step 5: 검증**

```bash
npm run type-check
```
Expected: 오류 0건 — 이 시점에 `AdminMessageHistoryView.vue`의 `jobPostingTitle`은 `string | null`이어도 템플릿 보간이라 타입 오류가 나지 않는다. 오류가 나면 Task 14에서 고칠 파일인지 확인하고, 그 밖의 파일이면 원인을 찾는다.

---

## Task 12: 가입 화면 이메일 인증 실연동

**Files:**
- Modify: `{FE}/views/applicant/SignupView.vue`

프론트 화면은 단위 테스트가 없다(기존 관례). 타입 검사로 검증한다.

- [ ] **Step 1: 템플릿 수정**

인증번호 입력칸(30~36행)을 아래로 바꾼다:

```vue
            <a-form-item>
              <div class="item-abreast" v-if="isEmailCertification">
                <a-input class="item" size="large" placeholder="이메일 인증번호를 입력해주세요."
                  v-model:value="verificationCode" :maxlength="6">
                  <template #prefix>
                  </template>
                </a-input>    
                <a-button type="primary" class="mail-button" @click="clickToEmailCertificationButton">인증확인</a-button>
              </div>
            </a-form-item>
```

- [ ] **Step 2: 스크립트 수정**

1. `const isNiceAuthComplete = ref(false);` 아래에 추가:
```ts
const verificationCode = ref('');
```
2. `watch(() => form.loginId, ...)` 본문에 `verificationCode.value = '';`를 추가한다.
3. `checkDuplicateEmailButton` 전체를 아래로 교체:
```ts
const checkDuplicateEmailButton = async () => {
  // 가용성 확인이 실패하면 인증번호 입력칸이 이전 결과로 남지 않도록 먼저 되돌린다.
  isEmailChecked.value = false;
  isEmailCertification.value = false;
  if (!(await checkAvailableEmail())) {
    return;
  }
  if(!isAvailable.value) {
    message.error('이미 가입된 메일주소 입니다.');
    return;
  }
  try {
    await applicationApi.sendSignupEmailVerification(form.loginId);
  }
  catch (error) {
    // 60초 재발송 제한·발송 실패는 서버 문구를 그대로 보여 준다. 직전에 받은 번호는 서버 세션에 남아 있다.
    message.error(getApiErrorMessage(error, '인증 메일을 보내지 못했습니다.'));
    isEmailChecked.value = true;
    isEmailCertification.value = true;
    return;
  }
  isEmailChecked.value = true;
  isEmailCertification.value = true;
  message.success('해당 메일주소로 인증번호를 발송하였습니다.');
};
```
4. `clickToEmailCertificationButton` 전체를 아래로 교체:
```ts
const clickToEmailCertificationButton = async () => {
  if (!verificationCode.value.trim()) {
    message.warning('인증번호를 입력해주세요.');
    return;
  }
  try {
    await applicationApi.verifySignupEmail({ email: form.loginId, code: verificationCode.value.trim() });
  }
  catch (error) {
    message.error(getApiErrorMessage(error, '인증번호를 확인하지 못했습니다.'));
    return;
  }
  message.success('이메일 인증이 완료되었습니다.');
  isEmailCertification.value = false;
  isEmailCertificationDone.value = true;
}
```

- [ ] **Step 3: 검증**

```bash
npm run type-check
```
Expected: 오류 0건. 수동 확인(선택, 로컬 백엔드 기동 시): "메일 인증" → 로그의 목업 메일(`[message-mail]`)은 번호를 남기지 않으므로, 로컬에서는 `recruit.message.gateway=logging` 상태에서 DB의 번호를 알 수 없다. 번호 확인이 필요하면 백엔드 테스트(Task 6)로 대신한다.

---

## Task 13: 비밀번호 재발급 화면 3단계

**Files:**
- Modify: `{FE}/views/applicant/AccountRecovery.vue`

- [ ] **Step 1: 템플릿 수정**

`<a-tab-pane key="resetPassword" ...>` 블록 전체를 아래로 교체:

```vue
            <a-tab-pane key="resetPassword" tab="비밀번호 재발급">
              <div class="tab-body" v-if="!isPasswordResetDone">
                <div class="tab-icon"><MailOutlined /></div>
                <p class="tab-title">이메일 인증으로 재설정</p>
                <p class="tab-description">가입한 이메일로 받은 인증번호를 확인한 뒤<br>새 비밀번호를 설정합니다.</p>
                <div class="mail-row">
                  <a-input size="large" placeholder="이메일을 입력해주세요."
                    v-model:value="loginId" :disabled="isEmailCertificationDone" />
                  <a-button type="primary" size="large" class="mail-button" v-if="!isEmailCertificationDone"
                    @click="clickToEmailCheckButton">메일 인증</a-button>
                  <a-button type="primary" size="large" class="mail-button" v-else disabled>인증 완료</a-button>
                </div>
                <div class="mail-row" v-if="isEmailCertification">
                  <a-input size="large" placeholder="이메일 인증번호를 입력해주세요."
                    v-model:value="verificationCode" :maxlength="6" />
                  <a-button type="primary" size="large" class="mail-button" @click="clickToEmailCertificationButton">인증확인</a-button>
                </div>
                <template v-if="isEmailCertificationDone">
                  <a-input-password class="password-input" size="large" placeholder="새 비밀번호를 입력해주세요. (8자 이상)"
                    v-model:value="newPassword" />
                  <a-input-password class="password-input" size="large" placeholder="새 비밀번호를 다시 입력해주세요."
                    v-model:value="newPasswordConfirm" />
                  <a-button type="primary" size="large" block :loading="isResetting"
                    @click="clickToResetPasswordButton">비밀번호 변경</a-button>
                </template>
              </div>
              <div class="tab-body" v-else>
                <div class="tab-icon"><CheckCircleOutlined /></div>
                <p class="tab-title">비밀번호가 변경되었습니다</p>
                <p class="tab-description">새 비밀번호로 로그인해주세요.</p>
                <a-button type="primary" size="large" block @click="goToLogin">로그인</a-button>
              </div>
            </a-tab-pane>
```

- [ ] **Step 2: 스크립트 수정**

1. `import type { checkEmailRequest } from '@/types/application';` 줄을 지운다(이 변경으로 쓰지 않게 된다).
2. `const isAvailable = ref(false);` ~ `const checkEmail = ref<checkEmailRequest>({...})` 블록(77~86행)을 아래로 교체:
```ts
const isEmailCertification = ref(false);
const isEmailCertificationDone = ref(false);
const verificationCode = ref('');
const newPassword = ref('');
const newPasswordConfirm = ref('');
const isResetting = ref(false);
const isPasswordResetDone = ref(false);
```
3. `clickToEmailCheckButton`부터 파일 끝 `</script>` 직전까지(141~188행: `clickToEmailCheckButton`·`checkAvailableEmail`·`checkDuplicateEmailButton`·`clickToEmailCertificationButton`)를 아래로 교체:
```ts
const clickToEmailCheckButton = async () => {
  if(!loginId.value) {
    return;
  }
  else if(!regEmail.test(loginId.value)) {
    message.error('올바른 형식의 이메일 주소를 작성해주세요.');
    return;
  }
  try {
    await applicationApi.sendPasswordResetCode(loginId.value);
  }
  catch (error) {
    // 미가입(404)·60초 재발송 제한·발송 실패는 서버 문구를 그대로 보여 준다.
    message.error(getApiErrorMessage(error, '인증 메일을 보내지 못했습니다.'));
    return;
  }
  isEmailCertification.value = true;
  message.success('해당 메일주소로 인증번호를 발송하였습니다.');
}

const clickToEmailCertificationButton = async () => {
  if (!verificationCode.value.trim()) {
    message.warning('인증번호를 입력해주세요.');
    return;
  }
  try {
    await applicationApi.verifyPasswordResetCode({ email: loginId.value, code: verificationCode.value.trim() });
  }
  catch (error) {
    message.error(getApiErrorMessage(error, '인증번호를 확인하지 못했습니다.'));
    return;
  }
  message.success('이메일 인증이 완료되었습니다.');
  isEmailCertification.value = false;
  isEmailCertificationDone.value = true;
}

const clickToResetPasswordButton = async () => {
  // 백엔드 ApplicantPasswordResetRequest 는 가입과 같이 8자 이상을 요구한다.
  if (newPassword.value.length < 8) {
    message.warning('비밀번호는 8자 이상 입력해주세요.');
    return;
  }
  if (newPassword.value !== newPasswordConfirm.value) {
    message.warning('비밀번호 확인이 일치하지 않습니다.');
    return;
  }
  isResetting.value = true;
  try {
    await applicationApi.resetPassword({ email: loginId.value, newPassword: newPassword.value });
    message.success('비밀번호가 변경되었습니다.');
    isPasswordResetDone.value = true;
  }
  catch (error) {
    message.error(getApiErrorMessage(error, '비밀번호를 변경하지 못했습니다.'));
  }
  finally {
    isResetting.value = false;
  }
}
```

- [ ] **Step 3: 스타일 수정**

1. 주석 `/* 비밀번호 재발급(메일 인증 목업) */`을 `/* 비밀번호 재발급(메일 인증) */`으로 바꾼다.
2. `.mail-done { ... }` 블록(이제 쓰지 않음)을 아래로 교체:
```css
.password-input {
  margin-bottom: 12px;
}
```

- [ ] **Step 4: 검증**

```bash
npm run type-check
```
Expected: 오류 0건. "임시 비밀번호" 문구가 남지 않았는지 Grep(`임시 비밀번호`, 대상 `recruit_front/src/views/applicant/AccountRecovery.vue`) → 0건.

---

## Task 14: 관리자 이력·템플릿 화면

**Files:**
- Modify: `{FE}/views/admin/message/AdminMessageHistoryView.vue`
- Modify: `{FE}/views/admin/message/MessageHistoryDrawer.vue`
- Modify: `{FE}/views/admin/message/AdminMessageTemplateView.vue`

- [ ] **Step 1: 이력 화면**

`AdminMessageHistoryView.vue`:
1. import 교체:
```ts
import type { AnyMessageType, MessageHistoryQuery, MessageOrigin, MessageSendSummary } from '@/types/admin/message'
```
```ts
import { MESSAGE_ORIGIN_LABEL, channelCellText, defaultHistoryRange, resultCounts, sendStatusView } from './messageHistory'
import { ALL_MESSAGE_TYPES, messageTypeLabel } from './messageTypes'
```
2. `TYPE_OPTIONS`를 바꾸고 `ORIGIN_OPTIONS`를 추가:
```ts
const TYPE_OPTIONS = ALL_MESSAGE_TYPES.map((meta) => ({ value: meta.type, label: meta.name }))
const ORIGIN_OPTIONS: { value: MessageOrigin; label: string }[] = [
  { value: 'ADMIN', label: MESSAGE_ORIGIN_LABEL.ADMIN },
  { value: 'SYSTEM', label: MESSAGE_ORIGIN_LABEL.SYSTEM },
]
```
3. `columns`의 발송일시 항목 아래에 추가:
```ts
  { title: '발송 구분', dataIndex: 'originLabel', key: 'origin', width: 120 },
```
4. `typeFilter`를 바꾸고 `originFilter`를 추가:
```ts
const typeFilter = ref<AnyMessageType | undefined>(undefined)
const originFilter = ref<MessageOrigin | undefined>(undefined)
```
5. `tableRows` 매핑에서 `typeLabel:` 아래에 `originLabel: MESSAGE_ORIGIN_LABEL[summary.origin],`을 추가하고, `jobPostingTitle: summary.jobPostingTitle,`를 `jobPostingTitle: summary.jobPostingTitle ?? '-',`로 바꾼다.
6. `toFilter`에 `origin: originFilter.value,`를 추가한다(`test:` 위).
7. 템플릿의 종류 `a-select` 아래에 추가:
```vue
      <a-select
        v-model:value="originFilter"
        class="origin-select"
        :options="ORIGIN_OPTIONS"
        placeholder="발송 구분 전체"
        aria-label="발송 구분"
        allow-clear
      />
```
8. 스타일 `.type-select` 아래에 추가:
```scss
.origin-select {
  width: 150px;
}
```

`MessageHistoryDrawer.vue` — `<a-descriptions-item label="공고">{{ detail.jobPostingTitle }}</a-descriptions-item>`를 `<a-descriptions-item label="공고">{{ detail.jobPostingTitle ?? '-' }}</a-descriptions-item>`로 바꾼다.

- [ ] **Step 2: 템플릿 화면**

`AdminMessageTemplateView.vue`:
1. 타입 import의 `MessageType`을 `AnyMessageType`으로 바꾼다. 로컬 import를 교체:
```ts
import { ALL_MESSAGE_TYPES, isSystemMessageType, messageTypeLabel } from './messageTypes'
```
2. `TemplateForm.type`, `typeFilter`, `emptyForm` 인자 타입을 바꾼다:
```ts
interface TemplateForm {
  type: AnyMessageType
  name: string
  defaultTemplate: boolean
  mailSubject: string
  mailBody: string
  smsBody: string
}
```
```ts
const typeFilter = ref<AnyMessageType | 'ALL'>('ALL')
```
```ts
const emptyForm = (type: AnyMessageType): TemplateForm => ({
```
3. `typeOptions`·`groups`에서 `MESSAGE_TYPES`를 `ALL_MESSAGE_TYPES`로 바꾼다.
4. `selectedTemplate` computed 아래에 추가:
```ts
/* 시스템 자동발송은 메일만 보낸다. SMS 입력을 숨기고 저장하지 않는다(서버도 null 로 저장). */
const isSystemType = computed(() => isSystemMessageType(form.type))

/* 시스템 기본 템플릿은 자동발송이 쓰므로 삭제할 수 없다(서버도 400). */
const isLockedSystemDefault = computed(() => {
  const template = selectedTemplate.value
  return template !== undefined && template.defaultTemplate && isSystemMessageType(template.type)
})
```
5. `toRequest`의 `smsBody: blankToNull(form.smsBody),`를 `smsBody: isSystemType.value ? null : blankToNull(form.smsBody),`로 바꾼다.
6. 삭제 버튼: `<a-button v-if="selectedId !== null" danger @click="remove">삭제</a-button>`를 `<a-button v-if="selectedId !== null && !isLockedSystemDefault" danger @click="remove">삭제</a-button>`로 바꾼다.
7. SMS 영역 숨김: `<h3 class="section-title">` (MessageOutlined SMS 제목)과 바로 아래 SMS `<a-textarea ...>`를 `<template v-if="!isSystemType"> ... </template>`로 감싼다.

발송 화면(`AdminMessageSendView.vue`·`MessageTypePicker.vue`)은 `MESSAGE_TYPES`(관리자 종류)를 쓰므로 수정하지 않는다.

- [ ] **Step 3: 검증**

```bash
npm run type-check
npx vitest run src/views/admin/message
```
Expected: 오류 0건, vitest PASS.

---

## Task 15: 도메인 카드 갱신 + 문서 점검

**Files:**
- Modify: `docs/domains/auth-account.md`, `docs/domains/message.md`, `docs/domains/message-delivery.md`, `docs/domains/application.md`, `docs/domains/_index.md`

auth-account 카드는 현재 36.9KB라 상한(40KB) 가까이 있다. 아래 문구는 바이트를 맞춰 줄인 것이다(예상 약 39.4KB). 문구를 늘리지 않는다.

- [ ] **Step 1: `auth-account.md`**

1. `## 요약`의 `- 지원자 계정 기능:` 줄 전체를 교체:
```markdown
- 지원자 계정 기능: 가입, 이메일 가용성 확인, 아이디(이메일) 찾기, 비밀번호 재발급, 비밀번호 변경, 전화번호 변경. 아이디 찾기는 NICE 본인확인(용도 `FIND_EMAIL`) 뒤 `find-email`이 부분 마스킹한 아이디를 준다(2026-09-22). 가입 이메일 인증·비밀번호 재발급은 가입 이메일로 보낸 6자리 인증번호로 한다(2026-09-23). 가입은 세션의 NICE 본인확인 결과와 이메일 인증에 의존한다(요청 본문에 name·phoneNumber·ci가 없는 이유) — 연동 상세는 [auth-nice-verification](auth-nice-verification.md).
```
2. `### 백엔드` 표의 행을 교체·추가:
```markdown
| controller | `{BE}/controller/ApplicantSignUpController.java` | 가입, 이메일 가용성, 가입 인증번호 발송·확인 |
| controller | `{BE}/controller/ApplicantAccountRecoveryController.java` | 아이디 찾기, 비밀번호 재발급(인증번호 발송·확인·재설정) — 세션 값 검사·소비 |
| service | `{BE}/service/ApplicantSignUpService.java` | 가입 검증·저장, 가입 인증 메일 |
| service | `{BE}/service/ApplicantAccountRecoveryService.java` | 식별 키로 계정 조회·`loginId` 마스킹, 재설정 메일·새 비밀번호 저장 |
| service | `{BE}/service/EmailVerificationService.java` `{BE}/service/EmailVerificationState.java` `{BE}/enumeration/EmailVerificationPurpose.java` | 인증번호 발급·확인·확인 후 10분 검사, 세션 값(번호는 해시), 목적 → 메일 종류 |
| repository | `{BE}/domain/repository/ApplicantRepository.java` | `findByLoginId`, `findByEmail`, `existsByEmail`, `existsByCiHash`, `findByCiHash` |
| dto | `{BE}/dto/request/EmailVerificationSendRequest.java` `{BE}/dto/request/EmailVerificationConfirmRequest.java` `{BE}/dto/request/ApplicantPasswordResetRequest.java` | 인증번호 발송·확인, 재설정 |
| exception | `{BE}/exception/InvalidEmailVerificationException.java` | 400 |
| test | `{BT}/service/EmailVerificationServiceTest.java` `{BT}/controller/ApplicantEmailVerificationControllerTest.java` `{BT}/controller/ApplicantPasswordResetControllerTest.java` | 인증번호 규칙, 가입 인증·재발급 흐름 |
```
(앞 4행·`ApplicantRepository` 행은 기존 행을 교체, 나머지는 같은 표에 추가. dto 행은 기존 dto 행들 아래, exception 행은 `InvalidApplicantAccountException` 아래, test 행은 `ApplicantAccountRecoveryControllerTest` 아래에 둔다.)
3. `### 프론트` 표의 행 교체:
```markdown
| view | `{FE}/views/applicant/SignupView.vue` | 가입(이메일=loginId, 메일 인증번호). 이름·휴대폰은 NICE 결과로 채워지는 읽기 전용 필드 |
| view | `{FE}/views/applicant/AccountRecovery.vue` | 가운데 단일 카드 + `a-tabs` 2개: 아이디 찾기(NICE 실연동, 마스킹 아이디 표시 후 로그인·비밀번호 재발급 탭으로 이동)·비밀번호 재발급(인증번호 → 새 비밀번호) |
| api | `{FE}/api/applicationApi.ts` | (공유) `signup`, `checkEmail`, `findEmail`, `changePassword`, 인증번호 5종 |
| types | `{FE}/types/application.ts` | (공유) `SignupUser`(`{ loginId, password, email }`, name·phoneNumber·ci 없음), `checkEmailRequest`, `FindEmailResponse`, `ChangePasswordParams`, `ChangePasswordRequest`, `EmailVerificationRequest`, `PasswordResetRequest` |
```
4. `## API 계약` 표: `sign-up` 행 교체 + `find-email` 행 아래에 5행 추가:
```markdown
| 🟢 | POST | /auth/applicants/sign-up | `{ loginId, password, email }`(name·phoneNumber·ci 없음 — 세션의 NICE 결과·이메일 인증을 쓴다) | `{ applicantId, loginId, name }` | 공개 |
| 🟢 | POST | /auth/applicants/email-verification/send | `{ email }` | `null` | 공개 |
| 🟢 | POST | /auth/applicants/email-verification/verify | `{ email, code }` | `null` | 공개 |
| 🟢 | POST | /auth/applicants/password-reset/send | `{ email }` | `null` | 공개 |
| 🟢 | POST | /auth/applicants/password-reset/verify | `{ email, code }` | `null` | 공개 |
| 🟢 | POST | /auth/applicants/password-reset | `{ email, newPassword }` | `null` | 공개 |
```
5. `**POST /auth/applicants/sign-up**` 상세의 `- 검증:` 줄과 `- 가입 성공 후 세션의 NICE` 줄을 교체:
```markdown
- 검증: loginId ≤100, password 8~100(모두 `@NotBlank`), email `@Email` ≤255. **name·phoneNumber·ci는 요청 본문에 없다** — 세션의 NICE 인증 결과(`requireFresh(purpose=SIGNUP)`)를 쓴다. 없으면(미진행·용도 불일치·만료) 400. 그다음 `requireVerified(SIGNUP, email)`: email이 비었거나 확인 전·확인 후 10분 지남·다른 이메일이면 400 `이메일 인증이 필요합니다.`. 둘 다 중복 검사보다 먼저다.
- 가입 성공 후 세션의 NICE 결과와 이메일 인증 상태는 **1회용이라 즉시 제거**한다.
```
6. `**POST /auth/applicants/find-email**` 문단 아래에 추가:
```markdown
**인증번호 5종**(2026-09-23): 세션 키는 목적별, 값 `EmailVerificationState`(번호는 SHA-256 해시만). 숫자 6자리·유효 5분·재발송은 60초 뒤·5회 틀리면 무효·확인 후 10분 안에 가입/재설정. 메일은 `SystemMailService`가 동기 발송([message-delivery](message-delivery.md)), 접수되지 않으면 세션에 저장하지 않는다.
- send 400: 가입 인증에서 가입된 이메일 `이미 사용 중인 이메일입니다.` · `인증번호는 60초 후에 다시 받을 수 있습니다.` · `인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해 주세요.` · `인증 메일 템플릿이 없습니다. 관리자에게 문의하세요.` / 재발급에서 미가입 404 `가입된 이메일이 아닙니다.`(계정 열거 감수).
- verify 400: `인증번호를 다시 받아 주세요.`(상태 없음·이메일 다름·만료) · `인증번호를 5회 틀렸습니다. 인증번호를 다시 받아 주세요.` · `인증번호가 일치하지 않습니다.`(실패 수 +1).
- `password-reset`: `requireVerified(PASSWORD_RESET, email)` 실패 400 → BCrypt 저장 → 세션 상태 제거. 다른 로그인 세션은 그대로.
```
7. `### 역할·URL 인가` 표의 첫 행을 교체:
```markdown
| `/api/auth/login`, `/api/auth/logout`, `/api/auth/applicants/`(`sign-up`·`check-email`·`find-email`·`email-verification/*`·`password-reset[/*]`) | 공개 |
```
8. `### 지원자 가입·계정`: `- 이메일 변경, 로그인 전 비밀번호 재설정 API는 아직 없다.` 줄과 그 아래 하위 항목 3개(`**이메일 변경: 불허**`·`**이메일 찾기(구현됨)**`·`**비밀번호 재설정**`)를 아래 2줄로 교체:
```markdown
- 이메일 변경 API는 없다(불허). **loginId 정책은 확정됐고**(아래 "함정·결정" — 이메일 = loginId), 아이디 찾기(2026-09-22)와 로그인 전 비밀번호 재설정(2026-09-23)은 구현됐다.
  - **비밀번호 재설정**: 가입 이메일로 인증번호 → 화면에서 확인 → 그 자리에서 새 비밀번호 설정(2026-09-23, 임시 비밀번호·토큰 링크 방식을 대체).
```
9. `### 프론트`(규칙) 마지막 줄 아래에 추가:
```markdown
- 이메일 인증(가입·재발급): "메일 인증" = `send`, "인증확인" = `verify` 성공 시에만 완료. 60초 제한은 서버 문구를 그대로 보여 준다.
```
10. `### 지원자 계정 API 추가` 레시피 4번을 교체:
```markdown
4. FE는 `{FE}/api/applicationApi.ts`에 호출을 추가하고 `AccountRecovery`/`ApplicantProfile`에서 쓴다.
```
11. `## 검증`의 두 명령에 `--tests "*EmailVerification*" --tests "*ApplicantPasswordReset*"`를 `--tests "*ApplicantAccount*"` 뒤에 추가한다.
12. `## 함정·결정`: `- **목업 보류**` 줄을 교체하고, `loginId 정책 확정` 하위의 `- 후속:`·`- BE가 아직 느슨한 부분:` 줄을 교체하고, 마지막에 1줄 추가:
```markdown
- **목업 해제**(가입·아이디 찾기 NICE 2026-09-21·22, 이메일 인증·비밀번호 재발급 2026-09-23): 남은 목업 없음. `NiceAuthMockPopup.vue`와 `window.phoneAuthCallback` 타입은 삭제했다.
```
```markdown
  - 후속: `check-login-id`는 만들지 않는다(이메일 중복 확인으로 갈음). 이메일 변경은 불허.
  - BE가 느슨한 부분: `loginId`에 이메일 형식 검증이 없다(`email`은 이메일 인증 검사로 사실상 필수).
```
```markdown
- **인증번호 한계**: 세션에만 있어 다른 브라우저로 이어갈 수 없고 IP 단위 시도 제한은 없다(2026-09-23 범위 제외).
```

- [ ] **Step 2: `message.md`**

1. `## 요약` 첫 줄 아래에 추가:
```markdown
- 시스템 자동발송 종류 3개(`SIGNUP_VERIFICATION`·`PASSWORD_RESET`·`APPLICATION_SUBMITTED`, 2026-09-23)는 이 카드가 템플릿만 관리한다(기동 시 기본 템플릿 생성, `SystemMessageTemplateInitializer`). 발송은 [message-delivery](message-delivery.md)의 `SystemMailService`가 하고, 관리자 발송·테스트 발송·대상 조회는 400으로 막는다.
```
2. `## 용어`: 메시지 종류 행 설명 끝에 ` · 시스템 자동발송 `SIGNUP_VERIFICATION`·`PASSWORD_RESET`·`APPLICATION_SUBMITTED`(`isSystem()`)`을 붙이고, 변수 행의 `12개`를 `14개`로 바꾼다.
3. `## 파일 지도`:
   - `MessageType.java` 행 역할을 `종류 8개(관리자 5 + 시스템 3, `isSystem`)`로, `MessageVariable.java` 행을 `변수 14개·허용 종류`로 바꾼다.
   - `MessageTemplateService.java` 행 아래에 추가: `| service | `{BE}/service/SystemMessageTemplateInitializer.java` | 기동 시 시스템 종류 기본 템플릿이 없으면 초안 생성(`ApplicationRunner`) |`
   - 프론트 `types/admin/message.ts` 행 역할을 `타입(이력 타입 포함). `MessageType` = 관리자 종류, `AnyMessageType` = 전체`로, `messageTypes.ts` 행을 `종류 표시 메타(관리자 `MESSAGE_TYPES`·시스템 `SYSTEM_MESSAGE_TYPES`·전체 `ALL_MESSAGE_TYPES`)`로 바꾼다.
   - 프론트 test 행 추가: `| test | `{FE}/views/admin/message/__tests__/messageTypes.spec.ts` | Vitest |`
   - `AdminMessageTemplateView.vue` 행을 `템플릿 관리 화면(시스템 종류는 SMS 숨김·기본 템플릿 삭제 버튼 숨김)`로 바꾼다.
4. `## API 계약`: variables 행 `12개` → `14개`.
5. `### 엔드포인트 상세`에 추가:
```markdown
- 템플릿 등록·수정 400(시스템 종류): `시스템 자동발송 템플릿은 메일 제목과 본문을 입력해야 합니다.` · `인증 메일에는 #{인증번호}가 있어야 합니다.`(가입 인증·비밀번호 재설정, 제목 또는 본문) · `시스템 기본 템플릿은 기본을 해제할 수 없습니다.`(기본 해제·종류 변경). 삭제 400: `시스템 기본 템플릿은 삭제할 수 없습니다.` 시스템 종류의 `smsBody`는 보내도 null로 저장한다.
- targets·test·send 공통 400: `시스템 자동발송 유형은 직접 보낼 수 없습니다.`
```
6. `## 규칙·불변식`에 추가:
```markdown
- 시스템 종류 템플릿: 메일 필수·SMS 저장 안 함, 인증 2종은 `#{인증번호}` 필수, 종류마다 기본 템플릿 1개는 삭제·기본 해제 불가(다른 템플릿을 기본으로 지정하면 기존 기본 전환 로직으로 바뀐다). 변수: `#{인증번호}`(인증 2종), `#{제출일시}`(제출 완료), `#{공고명}`은 관리자 5종 + 제출 완료, `#{이름}`·`#{채용사이트}`는 전체.
```
7. `## 변경 레시피`의 `- **종류 추가**:` 줄 끝에 ` 시스템 종류면 `isSystem()`·`SYSTEM_MESSAGE_TYPES`·`SystemMessageTemplateInitializer`도 고친다.`를 붙인다.

- [ ] **Step 3: `message-delivery.md`**

1. `## 요약` 첫 줄 아래에 추가:
```markdown
- 시스템 자동발송(2026-09-23): 가입 인증·비밀번호 재설정·제출 완료 메일을 `SystemMailService`가 기본 템플릿으로 보낸다. 이력은 같은 `message_send`·`message_recipient`에 `origin=SYSTEM`으로 남고(인증 2종은 공고·지원서 없음), 이력 화면에서 발송 구분으로 거른다.
```
2. `## 용어`에 행 추가:
```markdown
| 발송 구분 | `MessageOrigin` | `ADMIN` 관리자 발송 · `SYSTEM` 시스템 자동발송(발송자 `SYSTEM`/`시스템`, 메일만, 수신자 1명) |
```
3. `## 파일 지도` 백엔드:
   - `MessageSend.java` 행을 `발송 요청 1회. 치환 전 원문·템플릿 이름·조건 요약·발송자·`requestedAt`·`origin`(상태·건수는 저장하지 않음). 공고는 선택값(시스템 발송), `createSystem``로, `MessageSendRepository.java` 행을 `발송 CRUD·이력 검색(기간·종류·공고·구분·발송 구분, 최신순)`로 바꾼다.
   - 추가:
```markdown
| service | `{BE}/service/SystemMailService.java` | 시스템 메일 1통: 기본 템플릿으로 이력 저장·커밋 → 동기 디스패치 → 접수 판정 |
| service | `{BE}/service/ApplicationSubmittedMailListener.java` | 제출 커밋 후 비동기 제출 완료 메일(기본정보 → 회원 이메일) |
| enum | `{BE}/enumeration/MessageOrigin.java` `{BE}/enumeration/SystemMailOutcome.java` | 발송 구분, 시스템 메일 결과(`ACCEPTED`·`FAILED`·`NO_TEMPLATE`) |
| ops | `recruit_back/recruit_backend/docs/ops/message-send-origin-ddl.sql` | 운영 DDL: `origin` 추가·`job_posting_id` NULL 허용 |
| test | `{BT}/service/SystemMailServiceTest.java` `{BT}/service/ApplicationSubmittedMailListenerTest.java` `{BT}/domain/entity/MessageSendTest.java` | 시스템 이력·인증번호 미저장·실패·템플릿 없음, 제출 메일 주소 선택, 생성 규칙 |
```
4. `### 프론트`: `AdminMessageHistoryView.vue` 행 끝에 `, 발송 구분 열·필터`를, `messageHistory.ts` 행 끝에 `, 발송 구분 라벨`을 붙인다.
5. `## API 계약` history 행의 query를 `from?, to?, type?, jobPostingId?, test?, origin?, page(0), size(20)`로 바꾼다.
6. `### 엔드포인트 상세`:
   - history query 줄 끝에 ` `origin` 없음 = 전체, `ADMIN`·`SYSTEM`. `jobPostingId`로 거르면 공고 없는 시스템 발송은 빠진다.`를 붙인다.
   - `MessageSendSummaryResponse` 필드 목록의 `test,` 뒤에 `origin,`을 넣고, 문장 끝에 ` 공고 없는 시스템 발송은 `jobPostingTitle`이 null.`을 붙인다.
7. `## 규칙·불변식`에 추가:
```markdown
- 시스템 자동발송(`SystemMailService.send`): 종류의 기본 템플릿(`defaultTemplate=true`)이 없으면 보내지 않고 경고 로그(`NO_TEMPLATE`). 있으면 `MessageSend.createSystem`(원문 = 템플릿 치환 전 제목·본문) + 수신자 1명(메일 `PENDING`, SMS `SKIPPED`/`CHANNEL_OFF`)을 저장·커밋한 뒤 `MessageDispatcher.dispatch`를 동기로 부르고 수신자 메일 상태가 `REQUESTED`·`SENT`면 `ACCEPTED`, 아니면 `FAILED`. `#{이름}`·`#{채용사이트}`는 비어 있으면 이름·설정값으로 채운다. 치환 결과(인증번호)는 `DeliveryItem`에만 있다. 결과 수신·이력 상세는 관리자 발송과 같은 경로다.
- 제출 완료 메일: `ApplicationSubmittedEvent`(제출·재제출 성공, [application](application.md)) → `ApplicationSubmittedMailListener`(`AFTER_COMMIT` + `@Async`). 받는 주소 = 기본정보 이메일 → 회원 이메일(둘 다 없으면 경고 로그만), 이름 = 대상자 조회와 같은 규칙, 변수 `#{공고명}`·`#{제출일시}`(`yyyy-MM-dd HH:mm`). 예외는 경고 로그만, 제출 응답에 영향 없음.
```
8. `## 함정·결정`에 추가:
```markdown
- 시스템 메일 저장은 `TransactionTemplate` 기본 전파다(설계서의 REQUIRES_NEW 대신). 운영 호출자는 트랜잭션 밖이라 이력이 먼저 커밋되고, `@Transactional` 테스트에서는 합류해 롤백된다(다른 테스트 이력 오염 방지).
- 가입 인증·비밀번호 재설정 수신자 행은 `jobApplication`이 null이라 지원서 파기(`purgeMessageRecipients`) 대상이 아니다. 이메일·이름이 암호화된 채 남는다(2026-09-23 범위 밖, 파기 정책 미결).
```

- [ ] **Step 4: `application.md`**

1. `## 파일 지도` 백엔드 `ApplicationSubmitValidator.java` 행 아래에 추가:
```markdown
| service | `{BE}/service/ApplicationSubmittedEvent.java` | 제출·재제출 성공 이벤트(`applicationId`). 커밋 후 [message-delivery](message-delivery.md)의 리스너가 제출 완료 메일을 보낸다 |
```
2. `### 엔드포인트 상세`의 `POST /applications/{id}/submit` 줄 끝에 ` 성공하면 `ApplicationSubmittedEvent`를 발행하고 커밋 후 비동기로 제출 완료 메일이 간다(2026-09-23, 메일 실패는 제출 결과와 무관).`를 붙인다.

- [ ] **Step 5: `_index.md`**

`### 키워드 → 카드` 표:
- auth-account 행 키워드 끝에 `, 이메일 인증, 비밀번호 재발급`을 붙인다.
- message-delivery 행 키워드 끝에 `, 시스템 자동발송, 제출 완료 메일`을 붙인다.

- [ ] **Step 6: 검증**

레포 루트에서:
```bash
node tools/check-docs.mjs
```
Expected: `문서 점검 통과`(오류 0건). auth-account 크기 경고(30KB 초과)는 기존과 같다. 오류로 40KB 초과가 나오면 Step 1의 추가 문구를 줄이고(내용 추가 금지) 다시 실행한다.

---

## Task 16: 최종 대상 테스트 실행

전체 리그레션은 하지 않는다(AGENTS.md 5절). 변경 범위만 한 번에 확인한다.

- [ ] **Step 1: 백엔드**

`recruit_back/recruit_backend/`에서:
```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.domain.entity.MessageSendTest" --tests "com.shinyoung.recruit.service.MessageTemplateServiceTest" --tests "com.shinyoung.recruit.controller.MessageTemplateAdminControllerTest" --tests "com.shinyoung.recruit.service.MessageTargetServiceTest" --tests "com.shinyoung.recruit.service.MessageVariableFormatterTest" --tests "com.shinyoung.recruit.service.MessageRendererTest" --tests "com.shinyoung.recruit.service.SystemMailServiceTest" --tests "com.shinyoung.recruit.service.EmailVerificationServiceTest" --tests "com.shinyoung.recruit.service.ApplicantSignUpServiceTest" --tests "com.shinyoung.recruit.controller.ApplicantSignUpControllerTest" --tests "com.shinyoung.recruit.controller.ApplicantSignUpNiceIntegrationTest" --tests "com.shinyoung.recruit.controller.ApplicantEmailVerificationControllerTest" --tests "com.shinyoung.recruit.service.ApplicantAccountRecoveryServiceTest" --tests "com.shinyoung.recruit.controller.ApplicantAccountRecoveryControllerTest" --tests "com.shinyoung.recruit.controller.ApplicantPasswordResetControllerTest" --tests "com.shinyoung.recruit.config.SecurityConfigTest" --tests "com.shinyoung.recruit.service.JobApplicationServiceTest" --tests "com.shinyoung.recruit.service.ApplicationSubmittedMailListenerTest" --tests "com.shinyoung.recruit.controller.ApplicationControllerTest" --tests "com.shinyoung.recruit.service.MessageHistoryServiceTest" --tests "com.shinyoung.recruit.controller.MessageHistoryAdminControllerTest" --tests "com.shinyoung.recruit.service.MessageSendServiceTest" --tests "com.shinyoung.recruit.controller.MessageSendCommandControllerTest" --tests "com.shinyoung.recruit.controller.MessageSendAdminControllerTest" --tests "com.shinyoung.recruit.service.MessageSendAsyncFlowTest" --tests "com.shinyoung.recruit.service.MessageDispatcherTest" --tests "com.shinyoung.recruit.service.MessageDispatchRecorderTest" --tests "com.shinyoung.recruit.service.MessageRecipientDynamicUpdateTest" --tests "com.shinyoung.recruit.service.ApplicationPiiPurgeServiceTest" --no-daemon
```
Expected: PASS. 실패하면 백엔드 AGENTS.md 11절 순서로 원인을 분류하고, 변경과 무관한 기존 실패는 보고만 한다.

- [ ] **Step 2: 프론트**

`recruit_front/`에서:
```bash
npm run type-check
npx vitest run src/views/admin/message
```
Expected: 오류 0건, PASS.

- [ ] **Step 3: 문서**

레포 루트에서 `node tools/check-docs.mjs` → 오류 0건.

- [ ] **Step 4: 보고**

백엔드 AGENTS.md 12절 형식으로 변경 요약·변경 파일·테스트 결과·계약 변경(auth-account 🟢 5개 추가·sign-up 요청 변경, message-delivery history `origin`)·남은 이슈(아래 "스펙과 다른 점"의 참고 항목)를 보고한다. HTML 리포트는 사용자 메모리 규칙에 따라 요청이 있을 때만 `design-report` 스킬로 만든다. 커밋하지 않는다.

---

## 스펙과 다른 점

1. **이력 저장 트랜잭션 전파**: 스펙 6.1은 `REQUIRES_NEW`로 커밋한 뒤 디스패치한다. 계획은 `TransactionTemplate` 기본 전파(REQUIRED)를 쓴다. 운영 호출자(가입·재발급 요청 스레드, 제출 리스너 비동기 스레드)는 모두 트랜잭션 밖이라 "이력 커밋 → 디스패치" 효과는 같다. `REQUIRES_NEW`면 `@Transactional` 테스트에서도 이력이 커밋돼 롤백되지 않고, 공유 H2 메모리 DB에 남아 `MessageHistoryServiceTest`의 `containsExactly` 단언을 깨뜨린다.
2. **`sendAfterCommit` 미구현**: 스펙 6.5가 제출 메일도 리스너(이미 비동기) 안에서 동기 디스패치로 정해 호출자가 없다. `SystemMailService.send(...)` 하나만 둔다(기존 `MessageSendRequestedEvent`는 쓰지 않는다).
3. **결과 enum 이름·위치**: 스펙의 `SendOutcome`을 `enumeration/SystemMailOutcome`으로 둔다(백엔드 규칙: 모든 enum은 `enumeration` 패키지, 일반적인 이름 충돌 회피).
4. **`EmailVerificationService` 시그니처**: 스펙의 `issue/verify/requireVerified(..., now)`에서 `now` 인자를 빼고 주입한 `Clock`으로 계산한다(스펙 6.2 "시간은 주입한 Clock"과 맞춤). 발급+`SystemMailService` 발송+결과 판정을 묶은 `send(previous, purpose, email, name)`을 추가해 가입·재발급 두 서비스가 같은 발송 실패·템플릿 없음 처리를 쓴다. `issue`는 `IssuedCode(state, code)`를 준다.
5. **`EmailVerificationState`는 record가 아니라 가변 클래스**: 불일치 시 "실패 수 +1"을 저장하면서 400을 던져야 해서, 상태 객체를 제자리에서 바꾸고 컨트롤러가 `finally`에서 세션에 다시 넣는다. 용도(`EmailVerificationPurpose`)가 메일 종류(`getMessageType()`)를 들고 있다.
6. **이력 응답의 `jobPostingId`**: 스펙 6.6은 "`jobPostingId`·`jobPostingTitle` null"이라 했지만 현재 `MessageSendSummaryResponse`·`MessageSendDetailResponse`에 `jobPostingId` 필드가 없다. 필드를 새로 만들지 않고 `jobPostingTitle`만 null로 준다(프론트 타입도 `jobPostingTitle: string | null`).
7. **대상자 조회도 시스템 유형 400**: 스펙은 발송·테스트 발송 거부만 적었다. `MessageTargetService.getTargets`의 switch 식이 모든 enum 값을 요구해 case가 필요하므로 같은 문구(`시스템 자동발송 유형은 직접 보낼 수 없습니다.`)로 막는다. `MessageVariableFormatter` switch에도 새 변수 2개를 빈 값 case로 넣는다(컴파일 필요, 관리자 종류에는 허용되지 않아 쓰이지 않음). 이 두 변경은 컴파일 때문에 Task 1에 들어간다.
8. **스펙에 문구가 없는 오류 메시지를 정함**: `시스템 자동발송 템플릿은 메일 제목과 본문을 입력해야 합니다.`, `시스템 기본 템플릿은 기본을 해제할 수 없습니다.`(기본 해제뿐 아니라 시스템 기본 템플릿의 **종류 변경**도 같은 규칙으로 막는다 — 바꾸면 그 종류의 기본이 사라지기 때문), 요청 DTO 검증 문구(`인증번호를 입력해 주세요.` 등). 인증번호 형식(숫자 6자리)은 DTO에서 검사하지 않고 불일치로 센다.
9. **`#{이름}`·`#{채용사이트}` 기본 채움**: 호출자는 종류 전용 변수(인증번호, 공고명·제출일시)만 넘기고 `SystemMailService`가 이름·사이트 주소를 채운다(스펙 3.1이 두 변수를 전체 종류에 허용했으므로 관리자가 넣어도 값이 나오게).
10. **이름·주소 규칙 재사용**: 제출 메일이 "기존 대상자 조회와 같은 규칙"을 쓰도록 `MessageTargetService.firstNonBlank`를 `private` → 패키지 공개로 바꾼다.
11. **프론트 타입 분리**: `MessageType`을 8개로 넓히면 발송 화면의 `Record<MessageType, …>`(`MessageTypePicker`·`MessageTargetBar`)가 깨진다. `MessageType`은 관리자 5종으로 두고 `SystemMessageType`·`AnyMessageType`을 추가해 템플릿·변수·이력 타입만 넓힌다. 발송 화면은 `MESSAGE_TYPES`(관리자 5종)를 그대로 써서 코드 변경 없이 시스템 3종이 빠진다.
12. **가입 화면**: 기존 `check-email` 선확인을 유지한 뒤 `send`를 부른다(서버도 가입된 이메일을 400으로 막는다). 재발송이 60초 제한 등으로 실패해도 직전 번호가 세션에 남아 있으므로 인증번호 입력칸은 유지한다.
13. **템플릿 화면**: 시스템 기본 템플릿이면 삭제 버튼을 숨긴다(서버 400이 최종 방어). 이력 목록의 발송 구분은 별도 `bodyCell` 없이 `originLabel` 열로 보인다.
14. **보안 테스트**: 새 공개 경로 5개의 `SecurityConfigTest`는 `anyRequest().permitAll()` 때문에 구현 전에도 통과한다(명시 매처 회귀 방지 목적).

참고(스펙 범위 밖, 보고만):
- 가입 인증·비밀번호 재설정 이력의 수신자 행은 `jobApplication`이 null이라 지원서 파기 대상이 아니다. 가입하지 않은 사람의 이메일도 암호화된 채 남는다 — 파기 정책 결정 필요.
- 가입 화면은 이메일 인증 완료 후 입력칸이 잠기므로, 10분이 지나 가입이 `이메일 인증이 필요합니다.`로 실패하면 새로고침 외에 다시 인증할 방법이 없다(스펙이 화면 동작을 정하지 않음).
- 로컬 목업 게이트웨이(`gateway=logging`)는 본문을 로그에 남기지 않아 로컬에서 인증번호를 볼 수 없다. 로컬 화면 확인은 실제 TR 게이트웨이 환경이나 백엔드 테스트로 한다.

## 추가 결정 (2026-09-23, 사용자 확정)

- 파기 정책(가입 인증·비밀번호 재설정 이력의 수신자 행): 이번 범위 밖. 나중에 정한다.
- **결정 A — 로컬 목업 게이트웨이 본문 로그 (Task 4 에 포함)**: `LoggingMailGateway`(`gateway=logging`, 로컬 전용)는 기존 한 줄 로그 뒤에 `log.info("[message-mail] transactionId={} text={}", transactionId, message.text());` 를 추가해 로컬에서 인증번호를 확인할 수 있게 한다. 클래스 주석의 "본문 금지" 문구를 "로컬 목업이라 본문(text)도 남긴다(인증번호 확인용). 운영 게이트웨이(trnode)는 본문을 남기지 않는다."로 바꾼다. `TRNodeMessageGateway` 는 건드리지 않는다. message-delivery 카드의 목업 게이트웨이 설명에도 반영한다(Task 15).
- **결정 B — 인증 만료 뒤 재인증 (Task 12 에 포함)**: `SignupView.vue` 에서 가입(`sign-up`) 요청이 실패하고 서버 메시지가 `이메일 인증이 필요합니다.` 이면, 이메일 인증 상태(완료 플래그·입력칸 비활성·인증번호 입력값)를 초기화해 사용자가 같은 화면에서 다시 인증번호를 받을 수 있게 하고, 메시지를 그대로 보여 준다. NICE 인증 상태는 건드리지 않는다. `AccountRecovery.vue` 비밀번호 탭(Task 13)도 `password-reset` 요청이 같은 메시지로 실패하면 인증번호 단계로 되돌린다.
