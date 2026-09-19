# 메시지 발송 S4 (결과 수신·이력·마무리) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 발송 솔루션이 거래 ID로 나중에 보내는 발송 결과를 받아 수신자·채널별 최종 결과를 기록하고(목업 결과 포함), 발송 이력 목록·상세 API와 화면, 테스트 발송 결과 갱신, 지원서 파기 연동, 도메인 카드 확정까지 끝낸다(설계서 16절 S4).

**Architecture:** 게이트웨이 호출 결과를 "접수"(`GatewayResult.accepted(거래 ID)`)로 바꾸고, 접수된 채널은 `REQUESTED` + 거래 ID로 기록한다. 결과는 `DeliveryReportHandler`가 받아(먼저 온 결과는 `DeliveryReportBuffer`에 보관, 1분 재시도·10분 폐기) `MessageDispatchRecorder.applyReport`의 JPQL bulk update로 `REQUESTED`인 행만 `SENT`/`FAILED`로 바꾼다. 목업 게이트웨이는 `MockDeliveryReportScheduler`로 3초 뒤 가짜 결과를 넘긴다. S3의 저장 집계(`MessageSend.status`·건수 6개·`completedAt`)를 없애고 `MessageHistoryService`가 조회할 때 수신자 채널 상태로 상태·건수·지연을 계산한다. 이력 API 2개(`MessageHistoryAdminController`), 파기 bulk update 1개, 프론트 이력 화면·상세 드로어·테스트 카드 폴링·발송 후 알림을 추가한다.

**Tech Stack:** Spring Boot 4.0.2 · Java 17 · JPA(Hibernate 7.2) · Spring `@Async`·`@Scheduled`·`TaskScheduler` · JUnit5/Mockito/MockMvc · Vue 3.5 · TypeScript · ant-design-vue 4 · Vitest

---

## 근거와 작업 규칙

- 근거: 설계서 `docs/superpowers/specs/2026-09-19-message-send-design.md` 2·3.1(5·9)·3.2·7.1~7.4·8·9·10·12·13·14·16·17절, 도메인 카드 `docs/domains/message.md`, 현재 S3 코드(작성 기준).
- 커밋·브랜치 조작 금지(사용자 지시). 커밋 단계 자리에는 "커밋하지 않는다(사용자 지시)"만 둔다.
- 줄바꿈 LF 유지. 백엔드 명령은 `recruit_back/recruit_backend/`, 프론트는 `recruit_front/`, 문서 점검은 레포 루트에서 실행한다.
- 백엔드 테스트는 수정한 클래스만 PowerShell로 실행한다. 키는 백엔드 AGENTS.md 8절의 로컬·테스트 전용 예시 값이다.
- 경로 표기(`docs/domains/_index.md`): `{BE}` = `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit`, `{BT}` = `recruit_back/recruit_backend/src/test/java/com/shinyoung/recruit`, `{BR}` = `recruit_back/recruit_backend/src/main/resources`, `{FE}` = `recruit_front/src`.

## 파일 구조

| 구분 | 파일 | 책임 |
|---|---|---|
| 수정 | `{BE}/enumeration/MessageDeliveryStatus.java` | `REQUESTED` 추가(솔루션 접수·결과 수신 중) |
| 수정 | `{BE}/enumeration/MessageSendStatus.java` | `SENDING`·`RESULT_PENDING`·`COMPLETED`(저장 안 함) + 계산 `of` |
| 수정 | `{BE}/config/MessageProperties.java`, `{BR}/application.yaml` | `result-wait-minutes`(60), `success-result-codes`(`0000`) |
| 수정 | `{BE}/service/GatewayResult.java` | `(accepted, transactionId, failureReason)`, `accepted(id)`·`failure(reason)` |
| 수정 | `{BE}/service/MessageDeliveryService.java` | 접수 결과 정규화(null·빈 사유·거래 ID 없음 → `GATEWAY_ERROR`) |
| 수정 | `{BE}/service/LoggingMailGateway.java` · `LoggingSmsGateway.java` | 항상 접수(UUID 거래 ID) + 목업 결과 예약 |
| 수정 | `{BE}/domain/entity/MessageRecipient.java` | `mailTransactionId`·`smsTransactionId`(인덱스), `recordRequested`, `@DynamicUpdate` |
| 수정 | `{BE}/domain/entity/MessageSend.java` | `status`·건수 6개·`completedAt`·`complete` 제거 |
| 수정 | `{BE}/domain/repository/MessageRecipientRepository.java` | 결과 반영 bulk update 2개·거래 ID 존재 확인·발송별 상태 건수(S3 카운트 2개 제거) |
| 수정 | `{BE}/domain/repository/MessageSendRepository.java` | 이력 검색(기간·종류·공고·구분, 최신순) |
| 생성 | `{BE}/domain/repository/MessageRecipientStatusCount.java` | 상태 건수 JPQL 생성자 projection |
| 수정 | `{BE}/service/MessageDispatchRecorder.java` | `recordUnit`(REQUESTED+거래 ID / FAILED), `applyReport`(멱등), `complete` 제거 |
| 수정 | `{BE}/service/MessageDispatcher.java` | `dispatch(items)`, 접수 기록 직후 `applyBuffered` |
| 수정 | `{BE}/service/MessageSendService.java` | 접수 응답 `SENDING` 상수, 테스트 발송 응답을 디스패치 뒤 다시 읽어 만듦 |
| 생성 | `{BE}/service/DeliveryReport.java` | 발송 결과 1건(거래 ID + 결과코드) |
| 생성 | `{BE}/service/DeliveryReportBuffer.java` | 짝 없는 결과 메모리 보관(서버 1대 전제) |
| 생성 | `{BE}/service/DeliveryReportHandler.java` | 결과 수신 처리부(보관→반영, 접수 직후 반영, 1분 재시도·10분 폐기) |
| 생성 | `{BE}/service/MockDeliveryReportScheduler.java` | 목업 결과 3초 뒤 전달(`gateway=logging`일 때만) |
| 생성 | `{BE}/dto/condition/MessageHistoryCondition.java` | 이력 검색 조건 |
| 생성 | `{BE}/dto/response/MessageChannelCountResponse.java` · `MessageSendSummaryResponse.java` · `MessageSendDetailResponse.java` · `MessageRecipientResponse.java` | 이력 응답 |
| 생성 | `{BE}/service/MessageHistoryService.java` | 이력 목록·상세, 상태·건수·지연 계산(읽기 전용) |
| 생성 | `{BE}/controller/MessageHistoryAdminController.java` | `GET /admin/messages/history`, `GET /admin/messages/history/{sendId}` |
| 수정 | `{BE}/domain/repository/ApplicationPiiPurgeRepository.java`, `{BE}/service/ApplicationPiiPurgeService.java` | 수신자 이름·연락처·감사 필드 파기 |
| 수정(테스트) | `{BT}/service/MessageDeliveryServiceTest.java`, `MessageDispatcherTest.java`, `MessageDispatchRecorderTest.java`, `MessageSendServiceTest.java`, `MessageSendAsyncFlowTest.java`, `ApplicationPiiPurgeServiceTest.java`, `{BT}/controller/MessageSendCommandControllerTest.java`, `{BT}/config/SecurityConfigTest.java` | S4 동작으로 갱신·추가 |
| 생성(테스트) | `{BT}/service/DeliveryReportHandlerTest.java`, `MockDeliveryReportSchedulerTest.java`, `MessageHistoryServiceTest.java`, `{BT}/controller/MessageHistoryAdminControllerTest.java` | 새 동작 |
| 수정 | `{FE}/types/admin/message.ts`, `{FE}/api/admin/messageApi.ts` | `REQUESTED`·계산 상태·이력 타입, `getHistory`·`getHistoryDetail` |
| 생성 | `{FE}/views/admin/message/messageHistory.ts` + `__tests__/messageHistory.spec.ts` | 상태·사유 라벨, 채널 칸·건수, 파기 표시, 테스트 결과 변환, 기본 기간 |
| 생성 | `{FE}/views/admin/message/AdminMessageHistoryView.vue` · `MessageHistoryDrawer.vue` | 발송 이력 목록·상세 드로어 |
| 수정 | `{FE}/routes/adminRoutes.ts` | `AdminMessageHistory`(`/admin/messages/history`) |
| 수정 | `{FE}/views/admin/message/AdminMessageSendView.vue` · `MessageTestSendCard.vue` | 이력 버튼·발송 후 알림·테스트 결과 폴링·`REQUESTED` 표시 |
| 수정 | `docs/domains/message.md`, `docs/domains/_index.md`, `docs/domains/privacy-audit.md` | 카드·색인·파기 범위 |
| 생성 | `docs/archive/reports/message-send-s4_implementation.html` | `design-report` 스킬 구현 보고서 |
| 수정(로컬 전용, git 제외) | `recruit_front/.claude/mock/mockApi.ts`, `README.md` | 브라우저 데모용 목업 API |

## 결정 (이 계획에서 확정)

1. 발송 상태·건수는 저장하지 않는다. `MessageSendStatus`는 조회할 때 계산한다(`PENDING` 있음 → `SENDING`, 없고 `REQUESTED` 있음 → `RESULT_PENDING`, 둘 다 없음 → `COMPLETED`). `MessageSendResultResponse.status`는 남기고 접수 시 항상 `SENDING`이다.
2. `MessageRecipient`에 `@DynamicUpdate`를 붙인다. 비동기 디스패처가 SMS 단위를 기록(엔티티 변경 → 전체 컬럼 update)하는 순간 같은 수신자의 메일 결과가 bulk update로 먼저 커밋되면, 옛 `mailStatus = REQUESTED`로 덮어써 결과가 영영 사라진다. 바뀐 컬럼만 update하면 채널끼리 덮어쓰지 않는다(Hibernate 어노테이션, 새 의존성 아님).
3. 결과 반영 bulk update는 `@Modifying(flushAutomatically = true, clearAutomatically = true)`다. 테스트 발송(요청 트랜잭션에 합류)에서 반영이 일어나면 영속성 컨텍스트가 비워지므로 `testSend`는 디스패치 뒤 수신자를 다시 읽어 응답을 만든다. bulk update는 엔티티 감사(`updatedAt`)를 거치지 않는다(허용, `processedAt`만 갱신).
4. `MessageDispatcher.dispatch(List<DeliveryItem>)`로 바꾼다. `messageSendId` 매개변수는 집계 확정(`complete`)이 없어져 쓰지 않는다. `MessageSendRequestedEvent.messageSendId`는 그대로 둔다. S3 카운트 메서드 `countByMessageSendIdAndMailStatus`·`...SmsStatus`도 쓰는 곳이 없어져 지운다.
5. 접수인데 거래 ID가 비었거나 100자(컬럼 길이)를 넘으면 `GATEWAY_ERROR` 실패 + 경고 로그(연락처 없이 채널·인원만). 길이 초과를 저장하다 디스패치 전체가 예외로 멈추지 않게 하려는 추가 규칙이다.
6. 결과코드가 `success-result-codes`에 없으면 `FAILED`, 사유 = 결과코드(200자 절단). 결과코드가 null이면 사유도 null이다(설계서 규칙 그대로, 특별 처리 없음).
7. `DeliveryReportHandler`는 트랜잭션이 없고 트랜잭션은 모두 `MessageDispatchRecorder`에 둔다(자기 호출 문제 회피). 반영 중 예외는 잡아서 경고(거래 ID만)만 남기고 결과를 보관해 둔다.
8. `TaskScheduler`: Boot 4.0.2 `TaskSchedulingAutoConfiguration`이 `@EnableScheduling`(기존 `SchedulingConfig`)이 있고 다른 `TaskScheduler`·`ScheduledExecutorService` 빈이 없을 때 `taskScheduler`(`ThreadPoolTaskScheduler`, 스레드 1개)를 만든다(gradle 캐시의 `spring-boot-autoconfigure-4.0.2.jar`를 `javap`로 확인). 생성자 주입 `private final TaskScheduler taskScheduler`로 받는다. `@Async`는 계속 `applicationTaskExecutor`(별칭 `taskExecutor`)를 쓴다(이미 S3부터 두 빈이 공존).
9. 빈 의존 그래프(순환 없음): `MessageDispatcher` → `MessageDeliveryService` → `LoggingMailGateway`·`LoggingSmsGateway` → `MockDeliveryReportScheduler` → (`TaskScheduler`, `DeliveryReportHandler`, `Clock`); `MessageDispatcher` → `MessageDispatchRecorder`, `DeliveryReportHandler`; `DeliveryReportHandler` → (`MessageDispatchRecorder`, `DeliveryReportBuffer`, `Clock`); `MessageDispatchRecorder` → (`MessageRecipientRepository`, `MessageProperties`, `Clock`). 처리부·기록기는 디스패처·게이트웨이를 참조하지 않는다.
10. 이력 기간: `to` 기본 오늘, `from` 기본 `to - 29일`(양끝 포함 → `requestedAt >= from 00:00 and < to+1 00:00`). `from > to`, `page < 0`, `size` 1~100 밖은 400(`InvalidMessageException`, 기존 `ClientEventLogReadService` 가드 문구 형식). 정렬 `requestedAt desc, id desc`. 목록은 `@EntityGraph(jobPosting, stage)`, 건수는 페이지 발송 id로 `(발송, 메일 상태, SMS 상태)` group by 쿼리 1개.
11. 상세 응답은 요약 필드를 같은 이름으로 펼치고(`MessageSendDetailResponse`, 프론트 `MessageSendDetail extends MessageSendSummary`) 원문·수신자를 더한다. 수신자에는 설계서 9절 대비 `id`·`applicationId`를 추가한다. 연락처는 가리지 않는다.
12. 이력 구분 필터는 `test?`(Boolean)다. 현재 카드의 🟡 행 `test?(ALL·REAL·TEST)`는 설계서 9절(Boolean)과 달라 카드를 고친다.
13. 목업 결과코드는 `0000`, 단위 수신 연락처에 `fail`(대소문자 무시)이 있으면 `9999`로 고정이다(성공 코드 설정과 무관).
14. 로컬 DB: S3 코드로 만든 로컬 H2 파일 DB에는 지운 NOT NULL 컬럼(`status`·건수 6개)이 남고, Hibernate 7이 `@Enumerated(STRING)`에 만든 네이티브 `enum` 컬럼 타입(H2·MariaDB 방언 모두 `getEnumTypeDeclaration`)에 `REQUESTED`가 없어 저장이 실패한다. `ddl-auto: update`는 둘 다 고치지 않는다. 테스트(`create-drop`)는 영향 없다. 로컬은 두 테이블을 지우고 다시 만들어야 하므로 사용자에게 안내만 한다(에이전트가 실행하지 않음). 운영은 테이블이 아직 없고 생성 SQL 여부는 설계서 17절 1번 미결 그대로다(작성하면 S4 엔티티 기준).
15. 프론트 "발송 이력" 버튼은 기존 "템플릿 관리"와 같은 방식(`a-button` + `router.push`)으로 둔다. 발송 후에는 antd `notification` + `h(Button)` "이력 보기"로 `/admin/messages/history?sendId={id}`에 간다.
16. 기간 선택은 `a-range-picker`의 `value-format="YYYY-MM-DD"` 문자열과 `@change`의 `dateStrings`만 쓴다. `dayjs`는 `package.json` 직접 의존이 아니라 import하지 않는다.
17. `SecurityConfig` 변경 없음(`/api/admin/**`가 이미 `ADMIN`·`RECRUIT_ADMIN`). 새 예외 없음(`MessageSendNotFoundException` 핸들러 재사용, 메시지 "발송 기록을 찾을 수 없습니다.").
18. 최종 검증 명령에 `--tests "com.shinyoung.recruit.service.MockDeliveryReport*"`를 더한다. 지정된 패턴(`service.Message*`·`service.Delivery*`)만으로는 `MockDeliveryReportSchedulerTest`가 빠진다.

## 실행 배치

| 배치 | Task | 내용 | 선행 |
|---|---|---|---|
| A | 1·2·3 | 백엔드 결과 수신(상태·거래 ID·접수 기록 → 결과 반영·버퍼 → 목업 결과) | 없음 |
| B | 4·5 | 백엔드 이력(서비스·DTO·쿼리 → API·보안 테스트) | A |
| C | 6 | 파기 연동 | A(Task 1의 거래 ID 필드) |
| D | 7·8·9 | 프론트(타입·API·유틸 → 이력 화면 → 발송 화면 연결) | B의 계약 |
| E | 10·11 | 카드·색인·privacy-audit, 최종 검증·구현 보고서 | A~D |
| F | 12 | 로컬 목업(git 제외, 검증 대상 아님) | D |

배치는 A → B → C → D → E → F 순서로 한 번에 하나씩 실행한다. 백엔드 배치끼리는 같은 Gradle 빌드 디렉터리를 쓰므로 병렬로 돌리지 않는다.

---

## 배치 A — 백엔드 결과 수신

### Task 1: 채널 상태·접수 결과·거래 ID 기록 (S3 저장 집계 제거)

**Files:**
- Modify: `{BE}/enumeration/MessageDeliveryStatus.java`, `{BE}/enumeration/MessageSendStatus.java`
- Modify: `{BE}/service/GatewayResult.java`, `{BE}/service/MessageDeliveryService.java`, `{BE}/service/LoggingMailGateway.java`, `{BE}/service/LoggingSmsGateway.java`
- Modify: `{BE}/domain/entity/MessageRecipient.java`, `{BE}/domain/entity/MessageSend.java`, `{BE}/domain/repository/MessageRecipientRepository.java`
- Modify: `{BE}/service/MessageDispatchRecorder.java`, `{BE}/service/MessageDispatcher.java`, `{BE}/service/MessageSendService.java`
- Test(수정): `{BT}/service/MessageDeliveryServiceTest.java`, `MessageDispatcherTest.java`, `MessageDispatchRecorderTest.java`, `MessageSendServiceTest.java`, `MessageSendAsyncFlowTest.java`, `{BT}/controller/MessageSendCommandControllerTest.java`

`GatewayResult` 모양이 바뀌면 호출부·테스트가 한꺼번에 깨지므로 이 Task 하나에서 모두 고친다. 이 Task가 끝나면 접수된 채널은 `REQUESTED` + 거래 ID로 남는다(결과 반영은 Task 2, 목업 결과는 Task 3).

- [ ] **Step 1: 실패하는 테스트로 바꾸기**

`{BT}/service/MessageDeliveryServiceTest.java` 전체:

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
    void 메일은_레이아웃_HTML과_발신정보로_한번에_보내고_접수_결과를_돌려준다() {
        when(mailLayout.render("제목", "본문")).thenReturn("<html>본문</html>");
        when(mailGateway.send(any(), anyList())).thenReturn(GatewayResult.accepted("TX-1"));
        DeliveryUnit unit = new DeliveryUnit(MessageChannel.MAIL, "제목", "본문", null,
                List.of(1L, 2L), List.of("a@example.com", "b@example.com"));

        GatewayResult result = service.deliver(unit);

        assertThat(result.accepted()).isTrue();
        assertThat(result.transactionId()).isEqualTo("TX-1");
        ArgumentCaptor<MailMessage> captor = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailGateway).send(captor.capture(), org.mockito.ArgumentMatchers.eq(List.of("a@example.com", "b@example.com")));
        assertThat(captor.getValue().fromName()).isEqualTo("신영증권 채용담당");
        assertThat(captor.getValue().fromAddress()).isEqualTo("recruit@example.co.kr");
        assertThat(captor.getValue().html()).isEqualTo("<html>본문</html>");
        assertThat(captor.getValue().text()).isEqualTo("본문");
    }

    @Test
    void SMS는_발신번호와_구분을_넣어_보낸다() {
        when(smsGateway.send(any(), anyList())).thenReturn(GatewayResult.accepted("TX-2"));
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

        assertThat(result.accepted()).isFalse();
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

    @Test
    void 사유_없는_실패는_GATEWAY_ERROR로_채운다() {
        when(smsGateway.send(any(), anyList())).thenReturn(GatewayResult.failure(" "));
        DeliveryUnit unit = new DeliveryUnit(MessageChannel.SMS, null, "문자", SmsKind.SMS,
                List.of(1L), List.of("01012345678"));

        GatewayResult result = service.deliver(unit);

        assertThat(result.accepted()).isFalse();
        assertThat(result.failureReason()).isEqualTo(MessageContacts.GATEWAY_ERROR);
    }

    @Test
    void 접수했는데_거래_ID가_없으면_결과를_매칭할_수_없어_GATEWAY_ERROR_실패로_바꾼다() {
        when(smsGateway.send(any(), anyList())).thenReturn(new GatewayResult(true, " ", null));
        DeliveryUnit unit = new DeliveryUnit(MessageChannel.SMS, null, "문자", SmsKind.SMS,
                List.of(1L), List.of("01012345678"));

        GatewayResult result = service.deliver(unit);

        assertThat(result.accepted()).isFalse();
        assertThat(result.transactionId()).isNull();
        assertThat(result.failureReason()).isEqualTo(MessageContacts.GATEWAY_ERROR);
    }

    @Test
    void 거래_ID가_100자를_넘으면_저장할_수_없어_GATEWAY_ERROR_실패로_바꾼다() {
        when(smsGateway.send(any(), anyList())).thenReturn(GatewayResult.accepted("x".repeat(101)));
        DeliveryUnit unit = new DeliveryUnit(MessageChannel.SMS, null, "문자", SmsKind.SMS,
                List.of(1L), List.of("01012345678"));

        GatewayResult result = service.deliver(unit);

        assertThat(result.accepted()).isFalse();
        assertThat(result.failureReason()).isEqualTo(MessageContacts.GATEWAY_ERROR);
    }
}
```

`{BT}/service/MessageDispatcherTest.java` 전체:

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
    void 발송_단위마다_전달하고_접수_결과를_기록한다() {
        List<DeliveryItem> items = new ArrayList<>();
        for (long id = 1; id <= 11; id++) {
            items.add(new DeliveryItem(id, MessageChannel.MAIL, "u" + id + "@example.com", "공지", "같은 본문", null));
        }
        items.add(new DeliveryItem(1L, MessageChannel.SMS, "01000000001", null, "문자", SmsKind.SMS));
        when(deliveryService.deliver(any())).thenReturn(
                GatewayResult.accepted("TX-1"), GatewayResult.failure("X"), GatewayResult.accepted("TX-3"));

        dispatcher.dispatch(items);

        ArgumentCaptor<DeliveryUnit> units = ArgumentCaptor.forClass(DeliveryUnit.class);
        verify(deliveryService, times(3)).deliver(units.capture());
        assertThat(units.getAllValues()).extracting(DeliveryUnit::channel)
                .containsExactly(MessageChannel.MAIL, MessageChannel.MAIL, MessageChannel.SMS);
        DeliveryUnit unit0 = units.getAllValues().get(0);
        DeliveryUnit unit1 = units.getAllValues().get(1);
        DeliveryUnit unit2 = units.getAllValues().get(2);
        InOrder order = inOrder(deliveryService, recorder);
        order.verify(deliveryService).deliver(unit0);
        order.verify(recorder).recordUnit(unit0, GatewayResult.accepted("TX-1"));
        order.verify(deliveryService).deliver(unit1);
        order.verify(recorder).recordUnit(unit1, GatewayResult.failure("X"));
        order.verify(deliveryService).deliver(unit2);
        order.verify(recorder).recordUnit(unit2, GatewayResult.accepted("TX-3"));
    }

    @Test
    void 이벤트를_받으면_같은_흐름으로_보낸다() {
        when(deliveryService.deliver(any())).thenReturn(GatewayResult.accepted("TX-1"));

        dispatcher.onSendRequested(new MessageSendRequestedEvent(3L, List.of(
                new DeliveryItem(1L, MessageChannel.SMS, "01000000001", null, "문자", SmsKind.SMS))));

        verify(recorder).recordUnit(any(), eq(GatewayResult.accepted("TX-1")));
    }
}
```

`{BT}/service/MessageDispatchRecorderTest.java` 전체:

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
    void 접수된_단위는_REQUESTED와_거래_ID를_접수_실패한_단위는_FAILED와_사유를_기록한다() {
        MessageSend send = saveSend(3);
        MessageRecipient first = saveRecipient(send, MessageDeliveryStatus.PENDING, MessageDeliveryStatus.PENDING);
        MessageRecipient second = saveRecipient(send, MessageDeliveryStatus.PENDING, MessageDeliveryStatus.SKIPPED);
        MessageRecipient third = saveRecipient(send, MessageDeliveryStatus.SKIPPED, MessageDeliveryStatus.PENDING);

        recorder.recordUnit(unit(MessageChannel.MAIL, first.getId(), second.getId()), GatewayResult.failure("SMTP_REJECTED"));
        recorder.recordUnit(unit(MessageChannel.SMS, first.getId(), third.getId()), GatewayResult.accepted("TX-SMS-1"));

        assertThat(first.getMailStatus()).isEqualTo(MessageDeliveryStatus.FAILED);
        assertThat(first.getMailFailureReason()).isEqualTo("SMTP_REJECTED");
        assertThat(first.getMailTransactionId()).isNull();
        assertThat(second.getMailStatus()).isEqualTo(MessageDeliveryStatus.FAILED);
        assertThat(first.getSmsStatus()).isEqualTo(MessageDeliveryStatus.REQUESTED);
        assertThat(first.getSmsTransactionId()).isEqualTo("TX-SMS-1");
        assertThat(third.getSmsStatus()).isEqualTo(MessageDeliveryStatus.REQUESTED);
        assertThat(third.getSmsTransactionId()).isEqualTo("TX-SMS-1");
        assertThat(third.getMailStatus()).isEqualTo(MessageDeliveryStatus.SKIPPED);
        assertThat(first.getProcessedAt()).isNotNull();
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

`{BT}/service/MessageSendServiceTest.java` 두 곳을 바꾼다.

(1) `setUp()`의 게이트웨이 stub 두 줄:

```java
        when(mailGateway.send(any(), anyList())).thenReturn(GatewayResult.accepted("TX-MAIL-1"));
        when(smsGateway.send(any(), anyList())).thenReturn(GatewayResult.accepted("TX-SMS-1"));
```

(2) 메서드 `테스트_발송은_미리보기_대상_값으로_즉시_보내고_결과를_준다` 전체를 아래로 바꾼다:

```java
    @Test
    void 테스트_발송은_미리보기_대상_값으로_즉시_보내고_접수_결과를_준다() {
        JobApplication kim = submitted("김민준");

        MessageTestSendResponse response = messageSendService.testSend(new MessageTestSendRequest(
                MessageType.FREE, posting.getId(), null, null, null, JobApplicationStatus.SUBMITTED,
                kim.getId(),
                List.of(new MessageTesterRequest("김인사", "hr.kim@example.com", "010-0000-1234")),
                content("[신영증권] #{이름}님 안내", "본문", "#{이름}님 문자")), HR);

        assertThat(response.results()).extracting(MessageTestSendResultResponse::channel, MessageTestSendResultResponse::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(MessageChannel.MAIL, MessageDeliveryStatus.REQUESTED),
                        org.assertj.core.groups.Tuple.tuple(MessageChannel.SMS, MessageDeliveryStatus.REQUESTED));
        ArgumentCaptor<MailMessage> mail = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailGateway).send(mail.capture(), org.mockito.ArgumentMatchers.eq(List.of("hr.kim@example.com")));
        assertThat(mail.getValue().subject()).isEqualTo("[테스트] [신영증권] 김민준님 안내");
        ArgumentCaptor<SmsMessage> sms = ArgumentCaptor.forClass(SmsMessage.class);
        verify(smsGateway).send(sms.capture(), org.mockito.ArgumentMatchers.eq(List.of("01000001234")));
        assertThat(sms.getValue().body()).isEqualTo("[테스트] 김민준님 문자");
        MessageSend send = messageSendRepository.findById(response.sendId()).orElseThrow();
        assertThat(send.isTest()).isTrue();
        MessageRecipient tester = messageRecipientRepository.findByMessageSendIdOrderByIdAsc(send.getId()).get(0);
        assertThat(tester.getJobApplication()).isNull();
        assertThat(tester.getMailTransactionId()).isEqualTo("TX-MAIL-1");
        assertThat(tester.getSmsTransactionId()).isEqualTo("TX-SMS-1");
    }
```

`{BT}/controller/MessageSendCommandControllerTest.java`: 메서드 이름 `테스트_발송_결과를_준다` → `테스트_발송_접수_결과를_준다`, 기대값 `.andExpect(jsonPath("$.data.results[0].status").value("SENT"))` → `.andExpect(jsonPath("$.data.results[0].status").value("REQUESTED"))`. 나머지는 그대로(기본 로깅 게이트웨이가 접수를 돌려준다).

`{BT}/service/MessageSendAsyncFlowTest.java` 전체:

```java
package com.shinyoung.recruit.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.dto.request.MessageContentRequest;
import com.shinyoung.recruit.dto.request.MessageSendRequest;
import com.shinyoung.recruit.dto.response.MessageSendResultResponse;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.MessageSendStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 실제 발송 경로를 끝까지 검증한다: {@link MessageSendService#send}가 커밋되면
 * {@link MessageSendRequestedEvent}가 발행되고, {@link MessageDispatcher#onSendRequested}가
 * 커밋 후({@code AFTER_COMMIT}) 비동기로 실행되어 기본 로깅 게이트웨이({@link LoggingMailGateway},
 * {@link LoggingSmsGateway})를 호출하고 {@link MessageDispatchRecorder}가 접수 결과(REQUESTED + 거래 ID)를 기록한다.
 *
 * <p>{@code MessageSendServiceTest}는 {@code @Transactional}로 롤백되어 AFTER_COMMIT 이 발생하지
 * 않고, {@code MessageDispatcherTest}는 리스너를 직접 호출하므로 이 흐름 전체를 보여주지 못한다.
 *
 * <p>이 테스트는 {@code @Transactional}을 쓰지 않는다(롤백되면 커밋 후 이벤트가 발생하지 않는다).
 * 고정 픽스처(H2 in-memory, 이름 testdb)는 같은 JVM 에서 실행되는 다른 테스트와 공유되므로
 * {@link #tearDown()}에서 이 테스트가 만든 행을 전부 지운다.
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
class MessageSendAsyncFlowTest {

    private static final CustomUserDetails HR = CustomUserDetails.fromLdap(
            "hr.kim", "인사팀", "김인사", List.of(new SimpleGrantedAuthority("ROLE_RECRUIT_ADMIN")));
    private static final long POLL_TIMEOUT_MILLIS = 5_000L;
    private static final long POLL_INTERVAL_MILLIS = 100L;

    @Autowired
    private MessageSendService messageSendService;
    @Autowired
    private JobPostingRepository jobPostingRepository;
    @Autowired
    private JobApplicationRepository jobApplicationRepository;
    @Autowired
    private ApplicantRepository applicantRepository;
    @Autowired
    private MessageSendRepository messageSendRepository;
    @Autowired
    private MessageRecipientRepository messageRecipientRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private JobPosting posting;
    private Applicant kimApplicant;
    private Applicant leeApplicant;
    private JobApplication kim;
    private JobApplication lee;
    private Long sendId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            posting = JobPosting.create("비동기 발송 흐름 공고", "Content",
                    LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 22, 18, 0));
            posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
            posting = jobPostingRepository.saveAndFlush(posting);

            kimApplicant = newApplicant("김민준", "01000000000");
            kim = submitted(kimApplicant, "김민준");
            leeApplicant = newApplicant("이서연", null);
            lee = submitted(leeApplicant, "이서연");
        });
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            if (sendId != null) {
                messageRecipientRepository.deleteAll(
                        messageRecipientRepository.findByMessageSendIdOrderByIdAsc(sendId));
                messageSendRepository.findById(sendId).ifPresent(messageSendRepository::delete);
            }
            if (lee != null) {
                jobApplicationRepository.findById(lee.getId()).ifPresent(jobApplicationRepository::delete);
            }
            if (kim != null) {
                jobApplicationRepository.findById(kim.getId()).ifPresent(jobApplicationRepository::delete);
            }
            if (leeApplicant != null) {
                applicantRepository.findById(leeApplicant.getId()).ifPresent(applicantRepository::delete);
            }
            if (kimApplicant != null) {
                applicantRepository.findById(kimApplicant.getId()).ifPresent(applicantRepository::delete);
            }
            if (posting != null) {
                jobPostingRepository.findById(posting.getId()).ifPresent(jobPostingRepository::delete);
            }
        });
    }

    @Test
    void 발송_요청은_커밋_후_비동기로_게이트웨이에_접수되어_거래_ID를_남긴다() {
        Logger mailLogger = (Logger) LoggerFactory.getLogger(LoggingMailGateway.class);
        ListAppender<ILoggingEvent> mailLogAppender = new ListAppender<>();
        mailLogAppender.start();
        mailLogger.addAppender(mailLogAppender);
        try {
            MessageSendRequest request = new MessageSendRequest(MessageType.FREE, posting.getId(),
                    null, null, null, JobApplicationStatus.SUBMITTED,
                    List.of(kim.getId(), lee.getId()),
                    new MessageContentRequest(null, true, true,
                            "[신영증권] #{이름}님 안내", "#{이름}님, 안녕하세요.", "#{이름}님 안내 문자"));

            // 테스트 트랜잭션 밖(이 메서드는 @Transactional 이 아니다)에서 호출해야
            // send() 자체의 트랜잭션이 실제로 커밋되고 AFTER_COMMIT 리스너가 동작한다.
            MessageSendResultResponse result = messageSendService.send(request, HR);
            sendId = result.sendId();

            assertThat(result.status()).isEqualTo(MessageSendStatus.SENDING);
            assertThat(result.recipientCount()).isEqualTo(2);
            assertThat(result.excludedCount()).isEqualTo(0);

            List<MessageRecipient> recipients = awaitRecipients(sendId, recipient ->
                    recipient.getMailStatus() != MessageDeliveryStatus.PENDING
                            && recipient.getSmsStatus() != MessageDeliveryStatus.PENDING);

            assertThat(recipients)
                    .extracting(MessageRecipient::getMailStatus, MessageRecipient::getSmsStatus,
                            MessageRecipient::getSmsFailureReason)
                    .containsExactlyInAnyOrder(
                            tuple(MessageDeliveryStatus.REQUESTED, MessageDeliveryStatus.REQUESTED, null),
                            tuple(MessageDeliveryStatus.REQUESTED, MessageDeliveryStatus.SKIPPED, MessageContacts.NO_CONTACT));
            assertThat(recipients).allSatisfy(recipient ->
                    assertThat(recipient.getMailTransactionId()).isNotBlank());

            // 기본 로깅 게이트웨이가 실제로 호출됐는지, 그리고 이 호출이 테스트 스레드가 아닌
            // @Async 디스패치 스레드에서 일어났는지를 프로덕션 코드 변경 없이 로그로 확인한다.
            assertThat(mailLogAppender.list).as("LoggingMailGateway 가 호출됐어야 한다").isNotEmpty();
            String dispatchThreadName = mailLogAppender.list.get(0).getThreadName();
            assertThat(dispatchThreadName).as("디스패치는 비동기 스레드에서 실행돼야 한다")
                    .isNotEqualTo(Thread.currentThread().getName());
        } finally {
            mailLogger.detachAppender(mailLogAppender);
            mailLogAppender.stop();
        }
    }

    private List<MessageRecipient> awaitRecipients(Long id, Predicate<MessageRecipient> done) {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            List<MessageRecipient> recipients = messageRecipientRepository.findByMessageSendIdOrderByIdAsc(id);
            if (!recipients.isEmpty() && recipients.stream().allMatch(done)) {
                return recipients;
            }
            sleepQuietly();
        }
        fail("메시지 발송이 " + POLL_TIMEOUT_MILLIS + "ms 안에 끝나지 않았습니다(sendId=" + id + ").");
        throw new IllegalStateException("unreachable");
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(POLL_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Applicant newApplicant(String name, String phoneNumber) {
        String ci = "test-ci-" + UUID.randomUUID();
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName(name);
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setUserName(name);
        applicant.setPhoneNumber(phoneNumber);
        return applicantRepository.saveAndFlush(applicant);
    }

    private JobApplication submitted(Applicant applicant, String name) {
        JobPosition jobPosition = posting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant, posting, jobPosition, name, posting.getTitle(), jobPosition.getPositionName());
        application.submit(LocalDateTime.of(2026, 9, 10, 9, 0));
        return jobApplicationRepository.saveAndFlush(application);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageDeliveryServiceTest" --tests "com.shinyoung.recruit.service.MessageDispatcherTest" --tests "com.shinyoung.recruit.service.MessageDispatchRecorderTest" --tests "com.shinyoung.recruit.service.MessageSendServiceTest" --tests "com.shinyoung.recruit.service.MessageSendAsyncFlowTest" --tests "com.shinyoung.recruit.controller.MessageSendCommandControllerTest" --no-daemon`
Expected: FAIL — `compileTestJava` 오류(`GatewayResult.accepted`·`accepted()`·`MessageDeliveryStatus.REQUESTED`·`getMailTransactionId` 없음, `new MessageDispatcher`·`dispatch(List)` 시그니처 불일치).

- [ ] **Step 3: 상태 enum**

`{BE}/enumeration/MessageDeliveryStatus.java` 전체:

```java
package com.shinyoung.recruit.enumeration;

/**
 * 수신자·채널별 결과(설계서 2·7.4절). PENDING 솔루션 호출 전, REQUESTED 솔루션 접수(결과 수신 중),
 * SENT 성공, FAILED 실패(접수 실패 또는 실패 결과코드), SKIPPED 연락처 없음·형식 오류·채널 끔으로 보내지 않음.
 */
public enum MessageDeliveryStatus {
    PENDING,
    REQUESTED,
    SENT,
    FAILED,
    SKIPPED
}
```

`{BE}/enumeration/MessageSendStatus.java` 전체:

```java
package com.shinyoung.recruit.enumeration;

/**
 * 발송 1회의 상태. 저장하지 않고 조회할 때 수신자 채널 상태로 계산한다(설계서 7.4):
 * PENDING 이 있으면 SENDING, 없고 REQUESTED 가 있으면 RESULT_PENDING, 둘 다 없으면 COMPLETED.
 */
public enum MessageSendStatus {
    SENDING,
    RESULT_PENDING,
    COMPLETED
}
```

- [ ] **Step 4: 게이트웨이 접수 결과와 정규화**

`{BE}/service/GatewayResult.java` 전체:

```java
package com.shinyoung.recruit.service;

/**
 * 게이트웨이 1회 호출의 접수 결과(설계서 7.3). 접수되면 accepted = true 와 솔루션 거래 ID(필수, 100자 이내).
 * 최종 발송 결과는 나중에 거래 ID 로 온다(DeliveryReport). 접수 실패면 failureReason 에 사유 코드(200자 이내 권장).
 * 사유는 평문으로 저장되고 테스트 발송 응답에도 나가므로 주소·번호를 넣지 않는다. 비우면 GATEWAY_ERROR 로 기록한다.
 */
public record GatewayResult(boolean accepted, String transactionId, String failureReason) {

    public static GatewayResult accepted(String transactionId) {
        return new GatewayResult(true, transactionId, null);
    }

    public static GatewayResult failure(String failureReason) {
        return new GatewayResult(false, null, failureReason);
    }
}
```

`{BE}/service/MessageDeliveryService.java` 전체:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.enumeration.MessageChannel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 발송 단위 1개를 게이트웨이로 보내고 접수 결과를 정규화한다. DB 를 건드리지 않는다(기록은 MessageDispatchRecorder). */
@Service
@RequiredArgsConstructor
public class MessageDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(MessageDeliveryService.class);
    /** MessageRecipient.mailTransactionId·smsTransactionId 컬럼 길이. */
    private static final int TRANSACTION_ID_MAX_LENGTH = 100;

    private final MailGateway mailGateway;
    private final SmsGateway smsGateway;
    private final MessageMailLayout messageMailLayout;
    private final MessageProperties messageProperties;

    public GatewayResult deliver(DeliveryUnit unit) {
        try {
            GatewayResult result = unit.channel() == MessageChannel.MAIL ? sendMail(unit) : sendSms(unit);
            return normalize(unit, result);
        } catch (RuntimeException e) {
            // 예외 메시지에는 주소가 섞일 수 있어 남기지 않는다.
            log.warn("메시지 게이트웨이 호출 실패: channel={}, recipients={}, error={}",
                    unit.channel(), unit.recipientIds().size(), e.getClass().getSimpleName());
            return GatewayResult.failure(MessageContacts.GATEWAY_ERROR);
        }
    }

    /**
     * null 결과·사유 없는 실패는 GATEWAY_ERROR 실패로 바꾼다. 접수했는데 거래 ID 가 비었거나 컬럼보다 길면
     * 결과를 매칭할 수 없으므로 GATEWAY_ERROR 실패로 바꾸고 경고를 남긴다(연락처는 로그에 남기지 않는다).
     */
    private static GatewayResult normalize(DeliveryUnit unit, GatewayResult result) {
        if (result == null) {
            return GatewayResult.failure(MessageContacts.GATEWAY_ERROR);
        }
        if (result.accepted()) {
            String transactionId = result.transactionId();
            if (transactionId == null || transactionId.isBlank() || transactionId.length() > TRANSACTION_ID_MAX_LENGTH) {
                log.warn("메시지 게이트웨이가 쓸 수 없는 거래 ID 로 접수했습니다: channel={}, recipients={}",
                        unit.channel(), unit.recipientIds().size());
                return GatewayResult.failure(MessageContacts.GATEWAY_ERROR);
            }
            return result;
        }
        boolean noReason = result.failureReason() == null || result.failureReason().isBlank();
        return noReason ? GatewayResult.failure(MessageContacts.GATEWAY_ERROR) : result;
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

`{BE}/service/LoggingMailGateway.java` 전체(목업 결과 예약은 Task 3에서 붙인다):

```java
package com.shinyoung.recruit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** 목업 메일 게이트웨이. 항상 접수(가짜 거래 ID)하고 거래 ID·마스킹한 수신자·길이만 로그로 남긴다(본문 금지). */
@Component
@ConditionalOnProperty(prefix = "recruit.message", name = "gateway", havingValue = "logging", matchIfMissing = true)
public class LoggingMailGateway implements MailGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailGateway.class);

    @Override
    public GatewayResult send(MailMessage message, List<String> toAddresses) {
        String transactionId = UUID.randomUUID().toString();
        log.info("[message-mail] transactionId={} to={} subjectLength={} htmlLength={}",
                transactionId,
                toAddresses.stream().map(MessageContacts::maskEmail).toList(),
                message.subject().length(),
                message.html().length());
        return GatewayResult.accepted(transactionId);
    }
}
```

`{BE}/service/LoggingSmsGateway.java` 전체:

```java
package com.shinyoung.recruit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** 목업 문자 게이트웨이. 항상 접수(가짜 거래 ID)하고 거래 ID·마스킹한 번호·구분·길이만 로그로 남긴다(본문 금지). */
@Component
@ConditionalOnProperty(prefix = "recruit.message", name = "gateway", havingValue = "logging", matchIfMissing = true)
public class LoggingSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsGateway.class);

    @Override
    public GatewayResult send(SmsMessage message, List<String> toNumbers) {
        String transactionId = UUID.randomUUID().toString();
        log.info("[message-sms] transactionId={} to={} kind={} bodyLength={}",
                transactionId,
                toNumbers.stream().map(MessageContacts::maskPhone).toList(),
                message.kind(),
                message.body().length());
        return GatewayResult.accepted(transactionId);
    }
}
```

- [ ] **Step 5: 엔티티·리포지토리**

`{BE}/domain/entity/MessageRecipient.java` 전체:

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
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

/**
 * 발송 1회의 수신자 1명과 채널별 결과·솔루션 거래 ID. 이름·연락처는 발송 시점 값을 AES 로 암호화해 보관한다.
 * 테스트 발송 수신자(인사팀 담당자)는 jobApplication 이 null 이다.
 *
 * <p>{@code @DynamicUpdate}: 한 채널의 접수 기록(엔티티 변경)과 다른 채널의 결과 반영(JPQL bulk update)이
 * 같은 행에서 겹쳐도 바뀐 컬럼만 update 해 서로 덮어쓰지 않게 한다(설계서 7.4).
 */
@Entity
@Getter
@DynamicUpdate
@Table(
        name = "message_recipient",
        indexes = {
                @Index(name = "idx_message_recipient_send", columnList = "message_send_id"),
                @Index(name = "idx_message_recipient_application", columnList = "job_application_id"),
                @Index(name = "idx_message_recipient_mail_tx", columnList = "mail_transaction_id"),
                @Index(name = "idx_message_recipient_sms_tx", columnList = "sms_transaction_id")
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

    /** 메일 솔루션 거래 ID. 발송 결과 매칭 키. */
    @Column(length = 100)
    private String mailTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageDeliveryStatus smsStatus;

    @Column(length = 200)
    private String smsFailureReason;

    /** 문자 솔루션 거래 ID. 발송 결과 매칭 키. */
    @Column(length = 100)
    private String smsTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private SmsKind smsKind;

    /** 마지막 상태 변경 시각(접수·결과 수신). */
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

    /** 솔루션이 접수한 채널을 REQUESTED 로 두고 결과 매칭용 거래 ID 를 기록한다. 최종 결과는 발송 결과로 온다. */
    public void recordRequested(MessageChannel channel, String transactionId, LocalDateTime processedAt) {
        if (channel == MessageChannel.MAIL) {
            this.mailStatus = MessageDeliveryStatus.REQUESTED;
            this.mailTransactionId = transactionId;
        } else {
            this.smsStatus = MessageDeliveryStatus.REQUESTED;
            this.smsTransactionId = transactionId;
        }
        this.processedAt = processedAt;
    }

    /** 채널 상태와 사유를 기록한다(접수 실패 등). */
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

`{BE}/domain/entity/MessageSend.java` 전체:

```java
package com.shinyoung.recruit.domain.entity;

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

    @Column(nullable = false)
    private int recipientCount;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

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
        send.recipientCount = recipientCount;
        send.requestedAt = requestedAt;
        return send;
    }
}
```

`{BE}/domain/repository/MessageRecipientRepository.java` 전체(쓰는 곳이 없어진 S3 카운트 2개 삭제):

```java
package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.MessageRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRecipientRepository extends JpaRepository<MessageRecipient, Long> {

    List<MessageRecipient> findByMessageSendIdOrderByIdAsc(Long messageSendId);
}
```

- [ ] **Step 6: 기록기·디스패처·발송 서비스**

`{BE}/service/MessageDispatchRecorder.java` 전체:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 발송 단위의 접수 결과를 수신자에 기록한다(설계서 7.1·7.4). 기본 전파(REQUIRED):
 * 비동기 디스패처에서는 호출마다 새 트랜잭션, 테스트 발송(동기)에서는 요청 트랜잭션에 합류한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MessageDispatchRecorder {

    private static final int FAILURE_REASON_MAX_LENGTH = 200;

    private final MessageRecipientRepository messageRecipientRepository;
    private final Clock clock;

    /** 단위의 접수 결과를 그 단위 수신자 전원에 적용한다. 접수되면 REQUESTED + 거래 ID, 아니면 FAILED + 사유. */
    public void recordUnit(DeliveryUnit unit, GatewayResult result) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<MessageRecipient> recipients = messageRecipientRepository.findAllById(unit.recipientIds());
        if (result.accepted()) {
            recipients.forEach(recipient -> recipient.recordRequested(unit.channel(), result.transactionId(), now));
            return;
        }
        String reason = truncate(result.failureReason());
        recipients.forEach(recipient ->
                recipient.recordResult(unit.channel(), MessageDeliveryStatus.FAILED, reason, now));
    }

    private static String truncate(String reason) {
        if (reason == null || reason.length() <= FAILURE_REASON_MAX_LENGTH) {
            return reason;
        }
        return reason.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
```

`{BE}/service/MessageDispatcher.java` 전체:

```java
package com.shinyoung.recruit.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 발송 요청이 커밋되면 비동기로 발송 단위마다 게이트웨이를 호출하고 접수 결과를 기록한다(설계서 7.1).
 * 중간에 예외로 끝나면 남은 PENDING 은 그대로 남아 발송 상태가 SENDING 으로 계산된다(자동 재개는 범위 밖).
 */
@Component
@RequiredArgsConstructor
public class MessageDispatcher {

    private final MessageDeliveryService messageDeliveryService;
    private final MessageDispatchRecorder messageDispatchRecorder;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSendRequested(MessageSendRequestedEvent event) {
        dispatch(event.items());
    }

    /** 테스트 발송은 이 메서드를 요청 트랜잭션 안에서 동기로 부른다. */
    public void dispatch(List<DeliveryItem> items) {
        for (DeliveryUnit unit : DeliveryUnit.group(items)) {
            messageDispatchRecorder.recordUnit(unit, messageDeliveryService.deliver(unit));
        }
    }
}
```

`{BE}/service/MessageSendService.java`는 네 곳만 바꾼다.

(1) import 추가(`MessageDeliveryStatus` import 바로 아래):

```java
import com.shinyoung.recruit.enumeration.MessageSendStatus;
```

(2) 클래스 Javadoc `/** 테스트 발송(동기)과 실제 발송 접수(비동기 디스패치)를 처리한다(설계서 7절). */`를 아래로 바꾼다:

```java
/**
 * 테스트 발송(동기 접수)과 실제 발송 접수(비동기 디스패치)를 처리한다(설계서 7절).
 * 둘 다 응답은 솔루션 접수 결과이고 최종 결과는 발송 결과로 나중에 온다(7.4).
 */
```

(3) `send()`의 마지막 줄 `return new MessageSendResultResponse(send.getId(), send.getStatus(), recipients.size(), requested.size() - recipients.size());`를 아래로 바꾼다:

```java
        return new MessageSendResultResponse(send.getId(), MessageSendStatus.SENDING, recipients.size(),
                requested.size() - recipients.size());
```

(4) `testSend()`에서 `MessageSend send = messageSendRepository.save(`부터 메서드 끝 `return new MessageTestSendResponse(send.getId(), results);`까지를 아래로 바꾼다:

```java
        MessageSend send = messageSendRepository.save(
                newSend(condition, true, content, senderLoginId, userDetails.getName(), testers.size()));
        List<DeliveryItem> items = new ArrayList<>();
        for (int index = 0; index < testers.size(); index++) {
            MessageTesterRequest tester = testers.get(index);
            MessageRecipient recipient = messageRecipientRepository.save(
                    plans.get(index).toRecipient(send, null, tester.name(), tester.email(), tester.phone()));
            items.addAll(plans.get(index).items(recipient.getId()));
        }
        messageDispatcher.dispatch(items);

        // 먼저 도착한 발송 결과를 반영하면(bulk update) 영속성 컨텍스트가 비워지므로 응답은 수신자를 다시 읽어 만든다.
        List<MessageTestSendResultResponse> results = new ArrayList<>();
        for (MessageRecipient recipient : messageRecipientRepository.findByMessageSendIdOrderByIdAsc(send.getId())) {
            results.add(new MessageTestSendResultResponse(recipient.getRecipientName(), MessageChannel.MAIL,
                    recipient.getMailStatus(), recipient.getMailFailureReason()));
            results.add(new MessageTestSendResultResponse(recipient.getRecipientName(), MessageChannel.SMS,
                    recipient.getSmsStatus(), recipient.getSmsFailureReason()));
        }
        return new MessageTestSendResponse(send.getId(), results);
```

- [ ] **Step 7: 통과 확인**

Run: Step 2와 같은 명령.
Expected: `BUILD SUCCESSFUL`. `MessageDeliveryServiceTest` 7, `MessageDispatcherTest` 2, `MessageDispatchRecorderTest` 2, `MessageSendServiceTest` 14, `MessageSendAsyncFlowTest` 1, `MessageSendCommandControllerTest` 3 통과.

- [ ] **Step 8: 남은 참조 확인**

Grep(`recruit_back/recruit_backend/src`, 정규식): `GatewayResult\.ok\(|result\.success\(\)|countByMessageSendId|messageDispatchRecorder\.complete|recorder\.complete|getMailSent|getSmsSent|getMailSkipped|send\.getStatus\(\)`
Expected: 0건. (`PurgeBatch`의 `complete(`·`getCompletedAt`은 다른 도메인이라 패턴에서 뺐다.)

- [ ] **Step 9: 로컬 DB 안내(실행하지 않음)**

최종 보고에 다음을 적어 사용자에게 알린다: "S3로 만든 로컬 H2 파일 DB에서는 `message_send`의 NOT NULL 컬럼(`status`·`mail_sent` 등)과 `mail_status`·`sms_status`의 enum 값 제약 때문에 발송이 실패한다. H2 콘솔에서 `DROP TABLE message_recipient; DROP TABLE message_send;` 후 재기동하면 `ddl-auto: update`가 S4 구조로 다시 만든다(발송 이력 데이터는 사라진다)." 에이전트는 이 SQL을 실행하지 않는다.

- [ ] **Step 10: 커밋하지 않는다(사용자 지시)**

---

### Task 2: 발송 결과 반영·먼저 온 결과 보관

**Files:**
- Create: `{BE}/service/DeliveryReport.java`, `{BE}/service/DeliveryReportBuffer.java`, `{BE}/service/DeliveryReportHandler.java`
- Modify: `{BE}/config/MessageProperties.java`, `{BR}/application.yaml`
- Modify: `{BE}/domain/repository/MessageRecipientRepository.java`
- Modify: `{BE}/service/MessageDispatchRecorder.java`, `{BE}/service/MessageDispatcher.java`
- Test: Create `{BT}/service/DeliveryReportHandlerTest.java`; Modify `{BT}/service/MessageDispatchRecorderTest.java`, `{BT}/service/MessageDispatcherTest.java`

흐름(설계서 7.4): 소켓 클라이언트(지금은 Task 3의 목업) → `DeliveryReportHandler.handle(report)` → 버퍼에 넣고 `MessageDispatchRecorder.applyReport` 시도 → 반영됐거나 이미 기록된 거래면 버퍼에서 뺀다. 디스패처는 단위 접수를 기록한 직후 `applyBuffered(거래 ID)`를 부른다. `@Scheduled(fixedDelay = 60000)` `retryBuffered`가 보관 결과를 다시 시도하고, 받은 지 10분이 지나도 짝이 없으면 거래 ID만 경고 로그로 남기고 버린다. 서버 1대 전제.

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/service/DeliveryReportHandlerTest.java`:

```java
package com.shinyoung.recruit.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 결과 수신 처리부: 보관 → 반영 시도, 접수 기록 직후 반영, 1분 재시도, 10분 폐기(설계서 7.4). */
class DeliveryReportHandlerTest {

    private static class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-09-19T01:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    private final MutableClock clock = new MutableClock();
    private final MessageDispatchRecorder recorder = mock(MessageDispatchRecorder.class);
    private final DeliveryReportBuffer buffer = new DeliveryReportBuffer(clock);
    private final DeliveryReportHandler handler = new DeliveryReportHandler(recorder, buffer, clock);
    private final DeliveryReport report = new DeliveryReport("TX-1", "0000");

    @Test
    void 반영되면_보관하지_않는다() {
        when(recorder.applyReport(report)).thenReturn(true);

        handler.handle(report);

        assertThat(buffer.size()).isZero();
        verify(recorder).applyReport(report);
    }

    @Test
    void 짝이_없으면_보관했다가_접수_기록_직후_반영한다() {
        when(recorder.applyReport(report)).thenReturn(false, true);

        handler.handle(report);
        assertThat(buffer.find("TX-1")).contains(report);

        handler.applyBuffered("TX-1");

        assertThat(buffer.size()).isZero();
        verify(recorder, times(2)).applyReport(report);
    }

    @Test
    void 보관된_결과가_없으면_접수_기록_직후에_아무것도_하지_않는다() {
        handler.applyBuffered("TX-9");

        verifyNoInteractions(recorder);
    }

    @Test
    void 주기_재시도로_반영되면_보관에서_뺀다() {
        when(recorder.applyReport(report)).thenReturn(false, true);
        handler.handle(report);

        handler.retryBuffered();

        assertThat(buffer.size()).isZero();
    }

    @Test
    void 받은_지_10분이_지나도_짝이_없으면_버린다() {
        when(recorder.applyReport(report)).thenReturn(false);
        handler.handle(report);

        clock.advance(Duration.ofMinutes(9));
        handler.retryBuffered();
        assertThat(buffer.size()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(2));
        handler.retryBuffered();
        assertThat(buffer.size()).isZero();
        verify(recorder, times(3)).applyReport(report);
    }

    @Test
    void 반영_중_예외가_나도_던지지_않고_보관해_둔다() {
        when(recorder.applyReport(report)).thenThrow(new IllegalStateException("db down"));

        handler.handle(report);

        assertThat(buffer.size()).isEqualTo(1);
    }

    @Test
    void 거래_ID가_없는_결과는_무시한다() {
        handler.handle(new DeliveryReport(" ", "0000"));
        handler.handle(null);

        assertThat(buffer.size()).isZero();
        verifyNoInteractions(recorder);
    }
}
```

`{BT}/service/MessageDispatchRecorderTest.java` — `긴_실패_사유는_200자로_자른다` 메서드 바로 아래에 추가(같은 패키지라 import 추가 없음):

```java
    @Test
    void 결과는_거래_ID가_같고_REQUESTED인_채널만_바꾸고_성공_코드면_SENT로_둔다() {
        MessageSend send = saveSend(2);
        MessageRecipient first = saveRecipient(send, MessageDeliveryStatus.PENDING, MessageDeliveryStatus.PENDING);
        MessageRecipient second = saveRecipient(send, MessageDeliveryStatus.PENDING, MessageDeliveryStatus.SKIPPED);
        recorder.recordUnit(unit(MessageChannel.MAIL, first.getId(), second.getId()), GatewayResult.accepted("TX-MAIL-1"));
        recorder.recordUnit(unit(MessageChannel.SMS, first.getId()), GatewayResult.accepted("TX-SMS-1"));

        boolean known = recorder.applyReport(new DeliveryReport("TX-MAIL-1", "0000"));

        assertThat(known).isTrue();
        MessageRecipient reloadedFirst = messageRecipientRepository.findById(first.getId()).orElseThrow();
        assertThat(reloadedFirst.getMailStatus()).isEqualTo(MessageDeliveryStatus.SENT);
        assertThat(reloadedFirst.getMailFailureReason()).isNull();
        assertThat(reloadedFirst.getSmsStatus()).isEqualTo(MessageDeliveryStatus.REQUESTED);
        assertThat(messageRecipientRepository.findById(second.getId()).orElseThrow().getMailStatus())
                .isEqualTo(MessageDeliveryStatus.SENT);
    }

    @Test
    void 성공_코드가_아니면_FAILED로_두고_결과코드를_사유로_남긴다() {
        MessageSend send = saveSend(1);
        MessageRecipient recipient = saveRecipient(send, MessageDeliveryStatus.SKIPPED, MessageDeliveryStatus.PENDING);
        recorder.recordUnit(unit(MessageChannel.SMS, recipient.getId()), GatewayResult.accepted("TX-SMS-2"));

        recorder.applyReport(new DeliveryReport("TX-SMS-2", "E102"));

        MessageRecipient reloaded = messageRecipientRepository.findById(recipient.getId()).orElseThrow();
        assertThat(reloaded.getSmsStatus()).isEqualTo(MessageDeliveryStatus.FAILED);
        assertThat(reloaded.getSmsFailureReason()).isEqualTo("E102");
        assertThat(reloaded.getSmsTransactionId()).isEqualTo("TX-SMS-2");
    }

    @Test
    void 이미_반영된_거래의_결과가_다시_오면_바꾸지_않고_아는_거래로_본다() {
        MessageSend send = saveSend(1);
        MessageRecipient recipient = saveRecipient(send, MessageDeliveryStatus.PENDING, MessageDeliveryStatus.SKIPPED);
        recorder.recordUnit(unit(MessageChannel.MAIL, recipient.getId()), GatewayResult.accepted("TX-MAIL-3"));
        recorder.applyReport(new DeliveryReport("TX-MAIL-3", "0000"));

        boolean known = recorder.applyReport(new DeliveryReport("TX-MAIL-3", "9999"));

        assertThat(known).isTrue();
        MessageRecipient reloaded = messageRecipientRepository.findById(recipient.getId()).orElseThrow();
        assertThat(reloaded.getMailStatus()).isEqualTo(MessageDeliveryStatus.SENT);
        assertThat(reloaded.getMailFailureReason()).isNull();
    }

    @Test
    void 기록되지_않은_거래_ID면_모른다고_답한다() {
        assertThat(recorder.applyReport(new DeliveryReport("TX-UNKNOWN", "0000"))).isFalse();
    }
```

`{BT}/service/MessageDispatcherTest.java` 전체:

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageDispatcherTest {

    private final MessageDeliveryService deliveryService = mock(MessageDeliveryService.class);
    private final MessageDispatchRecorder recorder = mock(MessageDispatchRecorder.class);
    private final DeliveryReportHandler reportHandler = mock(DeliveryReportHandler.class);
    private final MessageDispatcher dispatcher = new MessageDispatcher(deliveryService, recorder, reportHandler);

    @Test
    void 발송_단위마다_전달하고_접수_결과를_기록한_뒤_먼저_온_결과를_반영한다() {
        List<DeliveryItem> items = new ArrayList<>();
        for (long id = 1; id <= 11; id++) {
            items.add(new DeliveryItem(id, MessageChannel.MAIL, "u" + id + "@example.com", "공지", "같은 본문", null));
        }
        items.add(new DeliveryItem(1L, MessageChannel.SMS, "01000000001", null, "문자", SmsKind.SMS));
        when(deliveryService.deliver(any())).thenReturn(
                GatewayResult.accepted("TX-1"), GatewayResult.failure("X"), GatewayResult.accepted("TX-3"));

        dispatcher.dispatch(items);

        ArgumentCaptor<DeliveryUnit> units = ArgumentCaptor.forClass(DeliveryUnit.class);
        verify(deliveryService, times(3)).deliver(units.capture());
        assertThat(units.getAllValues()).extracting(DeliveryUnit::channel)
                .containsExactly(MessageChannel.MAIL, MessageChannel.MAIL, MessageChannel.SMS);
        DeliveryUnit unit0 = units.getAllValues().get(0);
        DeliveryUnit unit1 = units.getAllValues().get(1);
        DeliveryUnit unit2 = units.getAllValues().get(2);
        InOrder order = inOrder(deliveryService, recorder, reportHandler);
        order.verify(deliveryService).deliver(unit0);
        order.verify(recorder).recordUnit(unit0, GatewayResult.accepted("TX-1"));
        order.verify(reportHandler).applyBuffered("TX-1");
        order.verify(deliveryService).deliver(unit1);
        order.verify(recorder).recordUnit(unit1, GatewayResult.failure("X"));
        order.verify(deliveryService).deliver(unit2);
        order.verify(recorder).recordUnit(unit2, GatewayResult.accepted("TX-3"));
        order.verify(reportHandler).applyBuffered("TX-3");
        verify(reportHandler, times(2)).applyBuffered(anyString());
    }

    @Test
    void 이벤트를_받으면_같은_흐름으로_보낸다() {
        when(deliveryService.deliver(any())).thenReturn(GatewayResult.accepted("TX-1"));

        dispatcher.onSendRequested(new MessageSendRequestedEvent(3L, List.of(
                new DeliveryItem(1L, MessageChannel.SMS, "01000000001", null, "문자", SmsKind.SMS))));

        verify(recorder).recordUnit(any(), eq(GatewayResult.accepted("TX-1")));
        verify(reportHandler).applyBuffered("TX-1");
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.DeliveryReportHandlerTest" --tests "com.shinyoung.recruit.service.MessageDispatchRecorderTest" --tests "com.shinyoung.recruit.service.MessageDispatcherTest" --no-daemon`
Expected: FAIL — `compileTestJava` 오류(`DeliveryReport`·`DeliveryReportBuffer`·`DeliveryReportHandler`·`applyReport` 없음, `MessageDispatcher` 생성자 인자 3개 불일치).

- [ ] **Step 3: 설정 추가**

`{BE}/config/MessageProperties.java`:

(1) 클래스 Javadoc을 아래로 바꾼다:

```java
/**
 * 메시지(메일·SMS) 발신 정보, 본문 변수 #{채용사이트} 값, 발송 연동·결과 수신 설정.
 * 실제 값은 운영 환경변수로 주입하고 코드에는 예시 값만 둔다.
 */
```

(2) import 추가: `import jakarta.validation.constraints.NotEmpty;`(`NotBlank` import 바로 아래), 그리고 `org.springframework...` import 아래 빈 줄 뒤에:

```java
import java.util.ArrayList;
import java.util.List;
```

(3) `gateway` 필드 아래에 필드 2개 추가:

```java
    /** 발송 요청 후 이 시간(분)이 지나도 완료가 아니면 이력에 지연(결과 미수신·발송 중단)으로 표시한다. */
    @Min(1)
    private int resultWaitMinutes = 60;

    /** 성공으로 볼 발송 결과코드 목록. 솔루션 스펙 확정 전 기본 0000(설계서 17절 6번). */
    @NotEmpty
    private List<String> successResultCodes = new ArrayList<>(List.of("0000"));
```

(4) `setGateway` 아래(클래스 끝 `}` 앞)에 getter/setter 추가:

```java
    public int getResultWaitMinutes() {
        return resultWaitMinutes;
    }

    public void setResultWaitMinutes(int resultWaitMinutes) {
        this.resultWaitMinutes = resultWaitMinutes;
    }

    public List<String> getSuccessResultCodes() {
        return successResultCodes;
    }

    public void setSuccessResultCodes(List<String> successResultCodes) {
        this.successResultCodes = successResultCodes;
    }
```

`{BR}/application.yaml` — `recruit.message` 블록의 `gateway: ${RECRUIT_MESSAGE_GATEWAY:logging}` 줄 바로 아래에 추가(들여쓰기 4칸, 최상위 `recruit:`를 새로 만들지 않는다):

```yaml
    # 결과 대기 시간(분)이 지나도 완료가 아니면 이력에 지연 표시. 성공 결과코드는 쉼표로 여러 개(솔루션 스펙 확정 후 운영 값).
    result-wait-minutes: ${RECRUIT_MESSAGE_RESULT_WAIT_MINUTES:60}
    success-result-codes: ${RECRUIT_MESSAGE_SUCCESS_RESULT_CODES:0000}
```

기본값이 있어 테스트 yaml은 고치지 않는다. `ApplicationYamlTest`는 `recruit.ldap` 키만 단언하므로 수정 없이 실행만 한다.

- [ ] **Step 4: 결과 값·버퍼·처리부**

`{BE}/service/DeliveryReport.java`:

```java
package com.shinyoung.recruit.service;

/**
 * 발송 솔루션이 메시지큐로 나중에 보내는 거래 1건의 결과(설계서 7.4). 그 거래의 수신자 전원에 같이 적용한다.
 * 실제 소켓 클라이언트는 받은 메시지를 이 값으로 바꿔 DeliveryReportHandler.handle 만 부르면 된다.
 */
public record DeliveryReport(String transactionId, String resultCode) {
}
```

`{BE}/service/DeliveryReportBuffer.java`:

```java
package com.shinyoung.recruit.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 거래 ID 가 아직 기록되지 않아 짝을 못 찾은 발송 결과를 잠시 보관한다(설계서 7.4).
 * 메모리 보관이라 서버 1대를 전제하고, 서버가 재시작되면 보관 중인 결과는 사라진다.
 */
@Component
@RequiredArgsConstructor
public class DeliveryReportBuffer {

    private final Clock clock;
    private final Map<String, Entry> reports = new ConcurrentHashMap<>();

    /** 같은 거래 ID 가 이미 있으면 처음 받은 시각을 유지한다. */
    public void put(DeliveryReport report) {
        reports.putIfAbsent(report.transactionId(), new Entry(report, LocalDateTime.now(clock)));
    }

    public Optional<DeliveryReport> find(String transactionId) {
        Entry entry = reports.get(transactionId);
        return entry == null ? Optional.empty() : Optional.of(entry.report());
    }

    public void remove(String transactionId) {
        reports.remove(transactionId);
    }

    /** 순회 중 추가·삭제와 겹쳐도 되도록 복사본을 준다. */
    public List<Entry> entries() {
        return List.copyOf(reports.values());
    }

    public int size() {
        return reports.size();
    }

    public record Entry(DeliveryReport report, LocalDateTime receivedAt) {
    }
}
```

`{BE}/service/DeliveryReportHandler.java`:

```java
package com.shinyoung.recruit.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 발송 결과 수신 처리부(설계서 7.4). 결과를 보관한 뒤 반영을 시도하고, 반영됐거나 이미 기록된 거래면 보관에서 뺀다.
 * 트랜잭션은 MessageDispatchRecorder 가 연다(이 빈에는 없다). 로그에는 거래 ID 만 남긴다.
 */
@Service
@RequiredArgsConstructor
public class DeliveryReportHandler {

    private static final Logger log = LoggerFactory.getLogger(DeliveryReportHandler.class);
    /** 짝을 못 찾은 결과를 보관하는 최대 시간. */
    static final Duration BUFFER_TTL = Duration.ofMinutes(10);

    private final MessageDispatchRecorder messageDispatchRecorder;
    private final DeliveryReportBuffer deliveryReportBuffer;
    private final Clock clock;

    /** 소켓 클라이언트(목업은 MockDeliveryReportScheduler)가 결과 1건마다 부른다. */
    public void handle(DeliveryReport report) {
        if (report == null || report.transactionId() == null || report.transactionId().isBlank()) {
            log.warn("거래 ID 가 없는 발송 결과를 무시합니다.");
            return;
        }
        deliveryReportBuffer.put(report);
        tryApply(report);
    }

    /** 디스패처가 단위 접수를 기록한 직후 부른다. 먼저 도착해 보관 중인 결과가 있으면 반영한다. */
    public void applyBuffered(String transactionId) {
        deliveryReportBuffer.find(transactionId).ifPresent(this::tryApply);
    }

    /** 1분마다 보관 결과를 다시 시도하고, 받은 지 10분이 지나도 짝이 없으면 버린다. */
    @Scheduled(fixedDelay = 60000)
    public void retryBuffered() {
        LocalDateTime expiredBefore = LocalDateTime.now(clock).minus(BUFFER_TTL);
        for (DeliveryReportBuffer.Entry entry : deliveryReportBuffer.entries()) {
            if (!tryApply(entry.report()) && entry.receivedAt().isBefore(expiredBefore)) {
                deliveryReportBuffer.remove(entry.report().transactionId());
                log.warn("발송 결과와 맞는 수신자가 10분 동안 없어 버립니다: transactionId={}",
                        entry.report().transactionId());
            }
        }
    }

    private boolean tryApply(DeliveryReport report) {
        try {
            if (messageDispatchRecorder.applyReport(report)) {
                deliveryReportBuffer.remove(report.transactionId());
                return true;
            }
        } catch (RuntimeException e) {
            log.warn("발송 결과 반영 실패: transactionId={}, error={}",
                    report.transactionId(), e.getClass().getSimpleName());
        }
        return false;
    }
}
```

- [ ] **Step 5: 결과 반영 쿼리·기록기·디스패처**

`{BE}/domain/repository/MessageRecipientRepository.java` 전체:

```java
package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageRecipientRepository extends JpaRepository<MessageRecipient, Long> {

    List<MessageRecipient> findByMessageSendIdOrderByIdAsc(Long messageSendId);

    /** 발송 결과의 거래 ID 가 이미 접수 기록된 거래인지(메일·SMS 어느 쪽이든). */
    boolean existsByMailTransactionIdOrSmsTransactionId(String mailTransactionId, String smsTransactionId);

    /**
     * 메일 발송 결과 반영. 거래 ID 가 같고 아직 REQUESTED 인 행만 바꾸므로 다시 온 결과는 0행이다(멱등).
     * bulk update 라 엔티티 감사(updatedAt)는 거치지 않고 processedAt 만 갱신한다. 같은 트랜잭션의 이후 조회가
     * 새 값을 보도록 영속성 컨텍스트를 비운다(테스트 발송은 요청 트랜잭션에 합류한다).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update MessageRecipient r
            set r.mailStatus = :status, r.mailFailureReason = :failureReason, r.processedAt = :processedAt
            where r.mailTransactionId = :transactionId
              and r.mailStatus = com.shinyoung.recruit.enumeration.MessageDeliveryStatus.REQUESTED
            """)
    int applyMailReport(
            @Param("transactionId") String transactionId,
            @Param("status") MessageDeliveryStatus status,
            @Param("failureReason") String failureReason,
            @Param("processedAt") LocalDateTime processedAt
    );

    /** SMS 발송 결과 반영. 규칙은 applyMailReport 와 같다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update MessageRecipient r
            set r.smsStatus = :status, r.smsFailureReason = :failureReason, r.processedAt = :processedAt
            where r.smsTransactionId = :transactionId
              and r.smsStatus = com.shinyoung.recruit.enumeration.MessageDeliveryStatus.REQUESTED
            """)
    int applySmsReport(
            @Param("transactionId") String transactionId,
            @Param("status") MessageDeliveryStatus status,
            @Param("failureReason") String failureReason,
            @Param("processedAt") LocalDateTime processedAt
    );
}
```

`{BE}/service/MessageDispatchRecorder.java` 전체:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 발송 단위의 접수 결과와 나중에 오는 발송 결과를 수신자에 기록한다(설계서 7.1·7.4). 기본 전파(REQUIRED):
 * 비동기 디스패처·결과 처리부에서는 호출마다 새 트랜잭션, 테스트 발송(동기)에서는 요청 트랜잭션에 합류한다.
 * 트랜잭션 경계를 이 빈에 모아 DeliveryReportHandler 의 자기 호출 문제를 피한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MessageDispatchRecorder {

    private static final int FAILURE_REASON_MAX_LENGTH = 200;

    private final MessageRecipientRepository messageRecipientRepository;
    private final MessageProperties messageProperties;
    private final Clock clock;

    /** 단위의 접수 결과를 그 단위 수신자 전원에 적용한다. 접수되면 REQUESTED + 거래 ID, 아니면 FAILED + 사유. */
    public void recordUnit(DeliveryUnit unit, GatewayResult result) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<MessageRecipient> recipients = messageRecipientRepository.findAllById(unit.recipientIds());
        if (result.accepted()) {
            recipients.forEach(recipient -> recipient.recordRequested(unit.channel(), result.transactionId(), now));
            return;
        }
        String reason = truncate(result.failureReason());
        recipients.forEach(recipient ->
                recipient.recordResult(unit.channel(), MessageDeliveryStatus.FAILED, reason, now));
    }

    /**
     * 발송 결과 1건을 거래 ID 가 같고 아직 REQUESTED 인 수신자·채널에 반영한다. 이미 반영된 행은 바뀌지 않는다(멱등).
     * 결과코드가 성공 목록(recruit.message.success-result-codes)에 있으면 SENT, 아니면 FAILED + 사유 = 결과코드.
     *
     * @return 거래 ID 를 알면(이번에 바꾼 행이 있거나 이미 기록된 거래) true. false 면 아직 접수 기록 전이라 보관해야 한다.
     */
    public boolean applyReport(DeliveryReport report) {
        boolean success = messageProperties.getSuccessResultCodes().contains(report.resultCode());
        MessageDeliveryStatus status = success ? MessageDeliveryStatus.SENT : MessageDeliveryStatus.FAILED;
        String reason = success ? null : truncate(report.resultCode());
        LocalDateTime now = LocalDateTime.now(clock);
        String transactionId = report.transactionId();
        int updated = messageRecipientRepository.applyMailReport(transactionId, status, reason, now)
                + messageRecipientRepository.applySmsReport(transactionId, status, reason, now);
        return updated > 0
                || messageRecipientRepository.existsByMailTransactionIdOrSmsTransactionId(transactionId, transactionId);
    }

    private static String truncate(String reason) {
        if (reason == null || reason.length() <= FAILURE_REASON_MAX_LENGTH) {
            return reason;
        }
        return reason.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
```

`{BE}/service/MessageDispatcher.java` 전체:

```java
package com.shinyoung.recruit.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 발송 요청이 커밋되면 비동기로 발송 단위마다 게이트웨이를 호출하고 접수 결과를 기록한다(설계서 7.1).
 * 접수된 단위는 기록 직후 먼저 도착해 보관 중인 발송 결과를 반영한다(7.4).
 * 중간에 예외로 끝나면 남은 PENDING 은 그대로 남아 발송 상태가 SENDING 으로 계산된다(자동 재개는 범위 밖).
 */
@Component
@RequiredArgsConstructor
public class MessageDispatcher {

    private final MessageDeliveryService messageDeliveryService;
    private final MessageDispatchRecorder messageDispatchRecorder;
    private final DeliveryReportHandler deliveryReportHandler;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSendRequested(MessageSendRequestedEvent event) {
        dispatch(event.items());
    }

    /** 테스트 발송은 이 메서드를 요청 트랜잭션 안에서 동기로 부른다. */
    public void dispatch(List<DeliveryItem> items) {
        for (DeliveryUnit unit : DeliveryUnit.group(items)) {
            GatewayResult result = messageDeliveryService.deliver(unit);
            messageDispatchRecorder.recordUnit(unit, result);
            if (result.accepted()) {
                deliveryReportHandler.applyBuffered(result.transactionId());
            }
        }
    }
}
```

- [ ] **Step 6: 통과 확인**

Run: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.DeliveryReportHandlerTest" --tests "com.shinyoung.recruit.service.MessageDispatchRecorderTest" --tests "com.shinyoung.recruit.service.MessageDispatcherTest" --tests "com.shinyoung.recruit.service.MessageSendServiceTest" --tests "com.shinyoung.recruit.service.MessageSendAsyncFlowTest" --tests "com.shinyoung.recruit.controller.MessageSendCommandControllerTest" --tests "com.shinyoung.recruit.config.ApplicationYamlTest" --no-daemon`
Expected: `BUILD SUCCESSFUL`. `DeliveryReportHandlerTest` 7, `MessageDispatchRecorderTest` 6, `MessageDispatcherTest` 2, `MessageSendServiceTest` 14, `MessageSendAsyncFlowTest` 1, `MessageSendCommandControllerTest` 3, `ApplicationYamlTest` 3 통과.

- [ ] **Step 7: 커밋하지 않는다(사용자 지시)**

---

### Task 3: 목업 결과(3초 뒤 가짜 결과)

**Files:**
- Create: `{BE}/service/MockDeliveryReportScheduler.java`
- Modify: `{BE}/service/LoggingMailGateway.java`, `{BE}/service/LoggingSmsGateway.java`
- Test: Create `{BT}/service/MockDeliveryReportSchedulerTest.java`; Modify `{BT}/service/MessageSendAsyncFlowTest.java`

`TaskScheduler`는 Boot 기본 `taskScheduler` 빈을 생성자 주입한다(결정 8). 목업 3개(게이트웨이 2개 + 스케줄러)는 모두 `recruit.message.gateway=logging`(기본)일 때만 뜬다.

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/service/MockDeliveryReportSchedulerTest.java`:

```java
package com.shinyoung.recruit.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MockDeliveryReportSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-09-19T01:00:00Z");

    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final DeliveryReportHandler handler = mock(DeliveryReportHandler.class);
    private final MockDeliveryReportScheduler scheduler =
            new MockDeliveryReportScheduler(taskScheduler, handler, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void 접수_3초_뒤_성공_코드로_결과를_넘긴다() {
        scheduler.schedule("TX-1", List.of("kim@example.com"));

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(task.capture(), eq(NOW.plusSeconds(3)));
        verifyNoInteractions(handler);

        task.getValue().run();

        verify(handler).handle(new DeliveryReport("TX-1", "0000"));
    }

    @Test
    void 연락처에_fail이_있으면_실패_코드로_넘긴다() {
        scheduler.schedule("TX-2", List.of("ok@example.com", "FAIL.test@example.com"));

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(task.capture(), any(Instant.class));
        task.getValue().run();

        verify(handler).handle(new DeliveryReport("TX-2", "9999"));
    }
}
```

`{BT}/service/MessageSendAsyncFlowTest.java`를 세 곳 바꾼다(import 변경 없음, `Predicate`는 `awaitRecipients`가 계속 쓴다).

(1) 클래스 Javadoc의 줄 ` * {@link LoggingSmsGateway})를 호출하고 {@link MessageDispatchRecorder}가 접수 결과(REQUESTED + 거래 ID)를 기록한다.`를 아래 두 줄로 바꾼다:

```java
 * {@link LoggingSmsGateway})를 호출하고 {@link MessageDispatchRecorder}가 접수 결과(REQUESTED + 거래 ID)를 기록한 뒤,
 * 3초 뒤 {@link MockDeliveryReportScheduler}의 가짜 결과를 {@link DeliveryReportHandler}가 반영해 SENT 가 된다.
```

(2) 상수 `POLL_TIMEOUT_MILLIS = 5_000L` → `10_000L`.

(3) 테스트 메서드 전체를 아래로 바꾸고, 바로 아래에 `isFinal`을 추가한다:

```java
    @Test
    void 발송_요청은_커밋_후_비동기로_접수되고_목업_결과를_받아_성공으로_바뀐다() {
        Logger mailLogger = (Logger) LoggerFactory.getLogger(LoggingMailGateway.class);
        ListAppender<ILoggingEvent> mailLogAppender = new ListAppender<>();
        mailLogAppender.start();
        mailLogger.addAppender(mailLogAppender);
        try {
            MessageSendRequest request = new MessageSendRequest(MessageType.FREE, posting.getId(),
                    null, null, null, JobApplicationStatus.SUBMITTED,
                    List.of(kim.getId(), lee.getId()),
                    new MessageContentRequest(null, true, true,
                            "[신영증권] #{이름}님 안내", "#{이름}님, 안녕하세요.", "#{이름}님 안내 문자"));

            // 테스트 트랜잭션 밖(이 메서드는 @Transactional 이 아니다)에서 호출해야
            // send() 자체의 트랜잭션이 실제로 커밋되고 AFTER_COMMIT 리스너가 동작한다.
            MessageSendResultResponse result = messageSendService.send(request, HR);
            sendId = result.sendId();

            assertThat(result.status()).isEqualTo(MessageSendStatus.SENDING);
            assertThat(result.recipientCount()).isEqualTo(2);
            assertThat(result.excludedCount()).isEqualTo(0);

            List<MessageRecipient> recipients = awaitRecipients(sendId, recipient ->
                    isFinal(recipient.getMailStatus()) && isFinal(recipient.getSmsStatus()));

            assertThat(recipients)
                    .extracting(MessageRecipient::getMailStatus, MessageRecipient::getSmsStatus,
                            MessageRecipient::getSmsFailureReason)
                    .containsExactlyInAnyOrder(
                            tuple(MessageDeliveryStatus.SENT, MessageDeliveryStatus.SENT, null),
                            tuple(MessageDeliveryStatus.SENT, MessageDeliveryStatus.SKIPPED, MessageContacts.NO_CONTACT));
            assertThat(recipients).allSatisfy(recipient ->
                    assertThat(recipient.getMailTransactionId()).isNotBlank());

            // 기본 로깅 게이트웨이가 실제로 호출됐는지, 그리고 이 호출이 테스트 스레드가 아닌
            // @Async 디스패치 스레드에서 일어났는지를 프로덕션 코드 변경 없이 로그로 확인한다.
            assertThat(mailLogAppender.list).as("LoggingMailGateway 가 호출됐어야 한다").isNotEmpty();
            String dispatchThreadName = mailLogAppender.list.get(0).getThreadName();
            assertThat(dispatchThreadName).as("디스패치는 비동기 스레드에서 실행돼야 한다")
                    .isNotEqualTo(Thread.currentThread().getName());
        } finally {
            mailLogger.detachAppender(mailLogAppender);
            mailLogAppender.stop();
        }
    }

    private static boolean isFinal(MessageDeliveryStatus status) {
        return status != MessageDeliveryStatus.PENDING && status != MessageDeliveryStatus.REQUESTED;
    }
```

- [ ] **Step 2: 실패 확인**

Run: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MockDeliveryReportSchedulerTest" --tests "com.shinyoung.recruit.service.MessageSendAsyncFlowTest" --no-daemon`
Expected: FAIL — `compileTestJava` 오류(`MockDeliveryReportScheduler` 없음).

- [ ] **Step 3: 목업 스케줄러와 게이트웨이 연결**

`{BE}/service/MockDeliveryReportScheduler.java`:

```java
package com.shinyoung.recruit.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * 목업 게이트웨이(logging)의 가짜 발송 결과(설계서 7.4). 접수 3초 뒤 TaskScheduler 로 DeliveryReportHandler 에 넘긴다.
 * 결과코드는 0000, 그 단위 수신 연락처에 fail 이 들어 있으면 9999(화면 확인용). 실제 솔루션 연동 시에는 뜨지 않는다.
 * TaskScheduler 는 SchedulingConfig(@EnableScheduling)가 있을 때 부트가 만드는 기본 taskScheduler(스레드 1개)다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "recruit.message", name = "gateway", havingValue = "logging", matchIfMissing = true)
public class MockDeliveryReportScheduler {

    static final Duration REPORT_DELAY = Duration.ofSeconds(3);
    static final String SUCCESS_CODE = "0000";
    static final String FAILURE_CODE = "9999";

    private final TaskScheduler taskScheduler;
    private final DeliveryReportHandler deliveryReportHandler;
    private final Clock clock;

    public void schedule(String transactionId, List<String> recipients) {
        String resultCode = recipients.stream().anyMatch(MockDeliveryReportScheduler::containsFail)
                ? FAILURE_CODE
                : SUCCESS_CODE;
        DeliveryReport report = new DeliveryReport(transactionId, resultCode);
        taskScheduler.schedule(() -> deliveryReportHandler.handle(report), clock.instant().plus(REPORT_DELAY));
    }

    private static boolean containsFail(String recipient) {
        return recipient != null && recipient.toLowerCase(Locale.ROOT).contains("fail");
    }
}
```

`{BE}/service/LoggingMailGateway.java` 전체:

```java
package com.shinyoung.recruit.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 목업 메일 게이트웨이. 항상 접수(가짜 거래 ID)하고 3초 뒤 목업 결과를 예약한다(MockDeliveryReportScheduler).
 * 거래 ID·마스킹한 수신자·길이만 로그로 남긴다(본문 금지).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "recruit.message", name = "gateway", havingValue = "logging", matchIfMissing = true)
public class LoggingMailGateway implements MailGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailGateway.class);

    private final MockDeliveryReportScheduler mockDeliveryReportScheduler;

    @Override
    public GatewayResult send(MailMessage message, List<String> toAddresses) {
        String transactionId = UUID.randomUUID().toString();
        log.info("[message-mail] transactionId={} to={} subjectLength={} htmlLength={}",
                transactionId,
                toAddresses.stream().map(MessageContacts::maskEmail).toList(),
                message.subject().length(),
                message.html().length());
        mockDeliveryReportScheduler.schedule(transactionId, toAddresses);
        return GatewayResult.accepted(transactionId);
    }
}
```

`{BE}/service/LoggingSmsGateway.java` 전체:

```java
package com.shinyoung.recruit.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 목업 문자 게이트웨이. 항상 접수(가짜 거래 ID)하고 3초 뒤 목업 결과를 예약한다(MockDeliveryReportScheduler).
 * 거래 ID·마스킹한 번호·구분·길이만 로그로 남긴다(본문 금지).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "recruit.message", name = "gateway", havingValue = "logging", matchIfMissing = true)
public class LoggingSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsGateway.class);

    private final MockDeliveryReportScheduler mockDeliveryReportScheduler;

    @Override
    public GatewayResult send(SmsMessage message, List<String> toNumbers) {
        String transactionId = UUID.randomUUID().toString();
        log.info("[message-sms] transactionId={} to={} kind={} bodyLength={}",
                transactionId,
                toNumbers.stream().map(MessageContacts::maskPhone).toList(),
                message.kind(),
                message.body().length());
        mockDeliveryReportScheduler.schedule(transactionId, toNumbers);
        return GatewayResult.accepted(transactionId);
    }
}
```

- [ ] **Step 4: 통과 확인(컨텍스트 기동·스케줄러 주입 포함)**

Run: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MockDeliveryReportSchedulerTest" --tests "com.shinyoung.recruit.service.MessageSendAsyncFlowTest" --tests "com.shinyoung.recruit.controller.MessageSendCommandControllerTest" --tests "com.shinyoung.recruit.config.AsyncConfigTest" --tests "com.shinyoung.recruit.config.SchedulingConfigTest" --no-daemon`
Expected: `BUILD SUCCESSFUL`. `MockDeliveryReportSchedulerTest` 2, `MessageSendAsyncFlowTest` 1(약 3~4초), `MessageSendCommandControllerTest` 3, `AsyncConfigTest` 2, `SchedulingConfigTest` 2 통과.
컨텍스트가 `No qualifying bean of type 'org.springframework.scheduling.TaskScheduler'`로 실패하면 `SchedulingConfig`의 `@EnableScheduling`이 남아 있는지 확인한다. 별도 `TaskScheduler` 빈을 만들지 않는다(만들면 부트 기본 스케줄러가 빠진다). 해결이 안 되면 멈추고 보고한다.

- [ ] **Step 5: 커밋하지 않는다(사용자 지시)**

---

## 배치 B — 백엔드 발송 이력

### Task 4: 이력 조회 서비스(상태·건수·지연 계산)

**Files:**
- Modify: `{BE}/enumeration/MessageSendStatus.java` (`of` 추가)
- Create: `{BE}/domain/repository/MessageRecipientStatusCount.java`
- Modify: `{BE}/domain/repository/MessageRecipientRepository.java` (상태 건수 쿼리), `{BE}/domain/repository/MessageSendRepository.java` (검색)
- Create: `{BE}/dto/condition/MessageHistoryCondition.java`
- Create: `{BE}/dto/response/MessageChannelCountResponse.java`, `MessageSendSummaryResponse.java`, `MessageSendDetailResponse.java`, `MessageRecipientResponse.java`
- Create: `{BE}/service/MessageHistoryService.java`
- Test: Create `{BT}/service/MessageHistoryServiceTest.java`; Modify `{BT}/service/MessageSendAsyncFlowTest.java`(이력 상세로 완료 확인)

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/service/MessageHistoryServiceTest.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.dto.condition.MessageHistoryCondition;
import com.shinyoung.recruit.dto.response.MessageChannelCountResponse;
import com.shinyoung.recruit.dto.response.MessageRecipientResponse;
import com.shinyoung.recruit.dto.response.MessageSendDetailResponse;
import com.shinyoung.recruit.dto.response.MessageSendSummaryResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.MessageSendStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.SmsKind;
import com.shinyoung.recruit.exception.InvalidMessageException;
import com.shinyoung.recruit.exception.MessageSendNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class MessageHistoryServiceTest {

    @Autowired
    private MessageHistoryService messageHistoryService;
    @Autowired
    private JobPostingRepository jobPostingRepository;
    @Autowired
    private ApplicantRepository applicantRepository;
    @Autowired
    private JobApplicationRepository jobApplicationRepository;
    @Autowired
    private MessageSendRepository messageSendRepository;
    @Autowired
    private MessageRecipientRepository messageRecipientRepository;
    @Autowired
    private Clock clock;

    private JobPosting posting;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now(clock);
        posting = JobPosting.create("이력 공고", "Content",
                LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 22, 18, 0));
        posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        posting = jobPostingRepository.saveAndFlush(posting);
    }

    @Test
    void 목록은_기간_종류_공고_구분으로_거르고_최신순으로_준다() {
        MessageSend dayAgo = saveSend(MessageType.FREE, false, true, true, now.minusDays(1));
        MessageSend announcement = saveSend(MessageType.RESULT_ANNOUNCEMENT, false, true, true, now.minusHours(2));
        MessageSend test = saveSend(MessageType.FREE, true, true, true, now.minusHours(1));
        MessageSend old = saveSend(MessageType.FREE, false, true, true, now.minusDays(40));

        assertThat(ids(search(null, null, null, null)))
                .containsExactly(test.getId(), announcement.getId(), dayAgo.getId());
        assertThat(ids(search(null, null, MessageType.FREE, null)))
                .containsExactly(test.getId(), dayAgo.getId());
        assertThat(ids(search(null, null, null, true))).containsExactly(test.getId());
        assertThat(ids(search(null, null, null, false))).containsExactly(announcement.getId(), dayAgo.getId());
        assertThat(ids(search(now.toLocalDate().minusDays(45), null, null, null)))
                .containsExactly(test.getId(), announcement.getId(), dayAgo.getId(), old.getId());
    }

    @Test
    void 건수와_상태를_수신자_채널_상태로_계산한다() {
        MessageSend sending = saveSend(MessageType.FREE, false, true, false, now.minusMinutes(5));
        saveRecipient(sending, null, MessageDeliveryStatus.PENDING, MessageDeliveryStatus.SKIPPED);
        saveRecipient(sending, null, MessageDeliveryStatus.SENT, MessageDeliveryStatus.SKIPPED);
        MessageSend resultPending = saveSend(MessageType.FREE, false, true, true, now.minusMinutes(4));
        saveRecipient(resultPending, null, MessageDeliveryStatus.REQUESTED, MessageDeliveryStatus.SENT);
        saveRecipient(resultPending, null, MessageDeliveryStatus.FAILED, MessageDeliveryStatus.SKIPPED);
        MessageSend completed = saveSend(MessageType.FREE, false, true, true, now.minusMinutes(3));
        saveRecipient(completed, null, MessageDeliveryStatus.SENT, MessageDeliveryStatus.FAILED);

        Map<Long, MessageSendSummaryResponse> byId = summariesById();

        assertThat(byId.get(sending.getId()).status()).isEqualTo(MessageSendStatus.SENDING);
        assertThat(byId.get(sending.getId()).mail()).isEqualTo(new MessageChannelCountResponse(1, 0, 1, 0, 0));
        assertThat(byId.get(sending.getId()).sms()).isEqualTo(new MessageChannelCountResponse(0, 0, 0, 0, 2));
        assertThat(byId.get(resultPending.getId()).status()).isEqualTo(MessageSendStatus.RESULT_PENDING);
        assertThat(byId.get(resultPending.getId()).mail()).isEqualTo(new MessageChannelCountResponse(0, 1, 0, 1, 0));
        assertThat(byId.get(resultPending.getId()).sms()).isEqualTo(new MessageChannelCountResponse(0, 0, 1, 0, 1));
        assertThat(byId.get(completed.getId()).status()).isEqualTo(MessageSendStatus.COMPLETED);
        assertThat(byId.get(completed.getId()).sms()).isEqualTo(new MessageChannelCountResponse(0, 0, 0, 1, 0));
    }

    @Test
    void 결과_대기_시간이_지나도_완료가_아니면_지연으로_표시한다() {
        MessageSend late = saveSend(MessageType.FREE, false, true, false, now.minusMinutes(61));
        saveRecipient(late, null, MessageDeliveryStatus.REQUESTED, MessageDeliveryStatus.SKIPPED);
        MessageSend recent = saveSend(MessageType.FREE, false, true, false, now.minusMinutes(10));
        saveRecipient(recent, null, MessageDeliveryStatus.REQUESTED, MessageDeliveryStatus.SKIPPED);
        MessageSend lateCompleted = saveSend(MessageType.FREE, false, true, false, now.minusMinutes(120));
        saveRecipient(lateCompleted, null, MessageDeliveryStatus.SENT, MessageDeliveryStatus.SKIPPED);

        Map<Long, MessageSendSummaryResponse> byId = summariesById();

        assertThat(byId.get(late.getId()).delayed()).isTrue();
        assertThat(byId.get(late.getId()).status()).isEqualTo(MessageSendStatus.RESULT_PENDING);
        assertThat(byId.get(recent.getId()).delayed()).isFalse();
        assertThat(byId.get(lateCompleted.getId()).delayed()).isFalse();
    }

    @Test
    void 제목은_메일_제목이고_메일을_끈_발송은_SMS_앞_40자다() {
        MessageSend mail = saveSend(MessageType.FREE, false, true, true, now.minusMinutes(2));
        MessageSend smsOnly = messageSendRepository.saveAndFlush(MessageSend.create(
                MessageType.FREE, false, posting, null, "제출 완료", null, null,
                false, true, null, null, "가".repeat(50), "hr.kim", "김인사", 1, now.minusMinutes(1)));

        Map<Long, MessageSendSummaryResponse> byId = summariesById();

        assertThat(byId.get(mail.getId()).title()).isEqualTo("[신영증권] #{이름}님 안내");
        assertThat(byId.get(smsOnly.getId()).title()).isEqualTo("가".repeat(40));
        assertThat(byId.get(smsOnly.getId()).mailEnabled()).isFalse();
        assertThat(byId.get(mail.getId()).jobPostingTitle()).isEqualTo("이력 공고");
        assertThat(byId.get(mail.getId()).senderName()).isEqualTo("김인사");
    }

    @Test
    void 상세는_원문과_수신자별_결과와_연락처를_그대로_주고_파기된_수신자는_비어_있다() {
        JobApplication application = submitted("김지원");
        MessageSend send = saveSend(MessageType.FREE, false, true, true, now.minusMinutes(1));
        MessageRecipient applicant = saveRecipient(send, application, MessageDeliveryStatus.SENT, MessageDeliveryStatus.FAILED);
        MessageRecipient purged = messageRecipientRepository.saveAndFlush(MessageRecipient.create(
                send, application, null, null, null,
                MessageDeliveryStatus.SENT, null, MessageDeliveryStatus.SKIPPED, MessageContacts.NO_CONTACT, null));
        MessageRecipient tester = saveRecipient(send, null, MessageDeliveryStatus.REQUESTED, MessageDeliveryStatus.REQUESTED);

        MessageSendDetailResponse detail = messageHistoryService.detail(send.getId());

        assertThat(detail.id()).isEqualTo(send.getId());
        assertThat(detail.templateName()).isEqualTo("합격 안내");
        assertThat(detail.mailSubject()).isEqualTo("[신영증권] #{이름}님 안내");
        assertThat(detail.mailBody()).isEqualTo("#{이름}님, 안녕하세요.");
        assertThat(detail.smsBody()).isEqualTo("#{이름}님 안내 문자");
        assertThat(detail.status()).isEqualTo(MessageSendStatus.RESULT_PENDING);
        assertThat(detail.mail()).isEqualTo(new MessageChannelCountResponse(0, 1, 2, 0, 0));
        assertThat(detail.recipients()).extracting(MessageRecipientResponse::id)
                .containsExactly(applicant.getId(), purged.getId(), tester.getId());
        MessageRecipientResponse first = detail.recipients().get(0);
        assertThat(first.applicationId()).isEqualTo(application.getId());
        assertThat(first.name()).isEqualTo("김지원");
        assertThat(first.email()).isEqualTo("kim@example.com");
        assertThat(first.phone()).isEqualTo("01000000000");
        assertThat(first.smsStatus()).isEqualTo(MessageDeliveryStatus.FAILED);
        assertThat(first.smsFailureReason()).isEqualTo("9999");
        assertThat(first.smsKind()).isEqualTo(SmsKind.SMS);
        MessageRecipientResponse second = detail.recipients().get(1);
        assertThat(second.applicationId()).isEqualTo(application.getId());
        assertThat(second.name()).isNull();
        assertThat(second.email()).isNull();
        assertThat(second.phone()).isNull();
        assertThat(detail.recipients().get(2).applicationId()).isNull();
    }

    @Test
    void 없는_발송이면_404_예외() {
        assertThatThrownBy(() -> messageHistoryService.detail(999_999L))
                .isInstanceOf(MessageSendNotFoundException.class)
                .hasMessage("발송 기록을 찾을 수 없습니다.");
    }

    @Test
    void 페이지와_기간이_잘못되면_거부한다() {
        MessageHistoryCondition all = new MessageHistoryCondition(null, null, null, null, null);
        LocalDate today = LocalDate.now(clock);

        assertThatThrownBy(() -> messageHistoryService.search(all, -1, 20))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("page는 0 이상이어야 합니다.");
        assertThatThrownBy(() -> messageHistoryService.search(all, 0, 101))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("size는 1 이상 100 이하여야 합니다.");
        assertThatThrownBy(() -> messageHistoryService.search(
                new MessageHistoryCondition(today, today.minusDays(1), null, null, null), 0, 20))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("조회 시작일이 종료일보다 늦습니다.");
    }

    private PageResponse<MessageSendSummaryResponse> search(LocalDate from, LocalDate to, MessageType type, Boolean test) {
        return messageHistoryService.search(new MessageHistoryCondition(from, to, type, posting.getId(), test), 0, 20);
    }

    private Map<Long, MessageSendSummaryResponse> summariesById() {
        return search(null, null, null, null).content().stream()
                .collect(Collectors.toMap(MessageSendSummaryResponse::id, Function.identity()));
    }

    private static List<Long> ids(PageResponse<MessageSendSummaryResponse> page) {
        return page.content().stream().map(MessageSendSummaryResponse::id).toList();
    }

    private MessageSend saveSend(MessageType type, boolean test, boolean mailEnabled, boolean smsEnabled,
                                 LocalDateTime requestedAt) {
        return messageSendRepository.saveAndFlush(MessageSend.create(
                type, test, posting, null, "제출 완료", 1L, "합격 안내",
                mailEnabled, smsEnabled,
                mailEnabled ? "[신영증권] #{이름}님 안내" : null,
                mailEnabled ? "#{이름}님, 안녕하세요." : null,
                smsEnabled ? "#{이름}님 안내 문자" : null,
                "hr.kim", "김인사", 3, requestedAt));
    }

    private MessageRecipient saveRecipient(MessageSend send, JobApplication application,
                                           MessageDeliveryStatus mail, MessageDeliveryStatus sms) {
        return messageRecipientRepository.saveAndFlush(MessageRecipient.create(
                send, application, "김지원", "kim@example.com", "01000000000",
                mail, reasonOf(mail), sms, reasonOf(sms), SmsKind.SMS));
    }

    private static String reasonOf(MessageDeliveryStatus status) {
        if (status == MessageDeliveryStatus.SKIPPED) {
            return MessageContacts.NO_CONTACT;
        }
        return status == MessageDeliveryStatus.FAILED ? "9999" : null;
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

`{BT}/service/MessageSendAsyncFlowTest.java` 전체(최종형: 이력 상세로 완료를 확인한다):

```java
package com.shinyoung.recruit.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.dto.request.MessageContentRequest;
import com.shinyoung.recruit.dto.request.MessageSendRequest;
import com.shinyoung.recruit.dto.response.MessageChannelCountResponse;
import com.shinyoung.recruit.dto.response.MessageRecipientResponse;
import com.shinyoung.recruit.dto.response.MessageSendDetailResponse;
import com.shinyoung.recruit.dto.response.MessageSendResultResponse;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.MessageSendStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 실제 발송 경로를 끝까지 검증한다: {@link MessageSendService#send}가 커밋되면
 * {@link MessageSendRequestedEvent}가 발행되고, {@link MessageDispatcher#onSendRequested}가
 * 커밋 후({@code AFTER_COMMIT}) 비동기로 실행되어 기본 로깅 게이트웨이({@link LoggingMailGateway},
 * {@link LoggingSmsGateway})를 호출하고 {@link MessageDispatchRecorder}가 접수 결과(REQUESTED + 거래 ID)를 기록한 뒤,
 * 3초 뒤 {@link MockDeliveryReportScheduler}의 가짜 결과를 {@link DeliveryReportHandler}가 반영하면
 * {@link MessageHistoryService}가 발송을 COMPLETED 로 계산한다.
 *
 * <p>{@code MessageSendServiceTest}는 {@code @Transactional}로 롤백되어 AFTER_COMMIT 이 발생하지
 * 않고, {@code MessageDispatcherTest}는 리스너를 직접 호출하므로 이 흐름 전체를 보여주지 못한다.
 *
 * <p>이 테스트는 {@code @Transactional}을 쓰지 않는다(롤백되면 커밋 후 이벤트가 발생하지 않는다).
 * 고정 픽스처(H2 in-memory, 이름 testdb)는 같은 JVM 에서 실행되는 다른 테스트와 공유되므로
 * {@link #tearDown()}에서 이 테스트가 만든 행을 전부 지운다.
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
class MessageSendAsyncFlowTest {

    private static final CustomUserDetails HR = CustomUserDetails.fromLdap(
            "hr.kim", "인사팀", "김인사", List.of(new SimpleGrantedAuthority("ROLE_RECRUIT_ADMIN")));
    private static final long POLL_TIMEOUT_MILLIS = 10_000L;
    private static final long POLL_INTERVAL_MILLIS = 100L;

    @Autowired
    private MessageSendService messageSendService;
    @Autowired
    private MessageHistoryService messageHistoryService;
    @Autowired
    private JobPostingRepository jobPostingRepository;
    @Autowired
    private JobApplicationRepository jobApplicationRepository;
    @Autowired
    private ApplicantRepository applicantRepository;
    @Autowired
    private MessageSendRepository messageSendRepository;
    @Autowired
    private MessageRecipientRepository messageRecipientRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private JobPosting posting;
    private Applicant kimApplicant;
    private Applicant leeApplicant;
    private JobApplication kim;
    private JobApplication lee;
    private Long sendId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            posting = JobPosting.create("비동기 발송 흐름 공고", "Content",
                    LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 22, 18, 0));
            posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
            posting = jobPostingRepository.saveAndFlush(posting);

            kimApplicant = newApplicant("김민준", "01000000000");
            kim = submitted(kimApplicant, "김민준");
            leeApplicant = newApplicant("이서연", null);
            lee = submitted(leeApplicant, "이서연");
        });
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            if (sendId != null) {
                messageRecipientRepository.deleteAll(
                        messageRecipientRepository.findByMessageSendIdOrderByIdAsc(sendId));
                messageSendRepository.findById(sendId).ifPresent(messageSendRepository::delete);
            }
            if (lee != null) {
                jobApplicationRepository.findById(lee.getId()).ifPresent(jobApplicationRepository::delete);
            }
            if (kim != null) {
                jobApplicationRepository.findById(kim.getId()).ifPresent(jobApplicationRepository::delete);
            }
            if (leeApplicant != null) {
                applicantRepository.findById(leeApplicant.getId()).ifPresent(applicantRepository::delete);
            }
            if (kimApplicant != null) {
                applicantRepository.findById(kimApplicant.getId()).ifPresent(applicantRepository::delete);
            }
            if (posting != null) {
                jobPostingRepository.findById(posting.getId()).ifPresent(jobPostingRepository::delete);
            }
        });
    }

    @Test
    void 발송_요청은_커밋_후_비동기로_접수되고_목업_결과를_받아_완료로_계산된다() {
        Logger mailLogger = (Logger) LoggerFactory.getLogger(LoggingMailGateway.class);
        ListAppender<ILoggingEvent> mailLogAppender = new ListAppender<>();
        mailLogAppender.start();
        mailLogger.addAppender(mailLogAppender);
        try {
            MessageSendRequest request = new MessageSendRequest(MessageType.FREE, posting.getId(),
                    null, null, null, JobApplicationStatus.SUBMITTED,
                    List.of(kim.getId(), lee.getId()),
                    new MessageContentRequest(null, true, true,
                            "[신영증권] #{이름}님 안내", "#{이름}님, 안녕하세요.", "#{이름}님 안내 문자"));

            // 테스트 트랜잭션 밖(이 메서드는 @Transactional 이 아니다)에서 호출해야
            // send() 자체의 트랜잭션이 실제로 커밋되고 AFTER_COMMIT 리스너가 동작한다.
            MessageSendResultResponse result = messageSendService.send(request, HR);
            sendId = result.sendId();

            assertThat(result.status()).isEqualTo(MessageSendStatus.SENDING);
            assertThat(result.recipientCount()).isEqualTo(2);
            assertThat(result.excludedCount()).isEqualTo(0);

            MessageSendDetailResponse detail = awaitCompleted(sendId);

            assertThat(detail.delayed()).isFalse();
            assertThat(detail.recipientCount()).isEqualTo(2);
            assertThat(detail.mail()).isEqualTo(new MessageChannelCountResponse(0, 0, 2, 0, 0));
            assertThat(detail.sms()).isEqualTo(new MessageChannelCountResponse(0, 0, 1, 0, 1));
            assertThat(detail.recipients())
                    .extracting(MessageRecipientResponse::mailStatus, MessageRecipientResponse::smsStatus,
                            MessageRecipientResponse::smsFailureReason)
                    .containsExactlyInAnyOrder(
                            tuple(MessageDeliveryStatus.SENT, MessageDeliveryStatus.SENT, null),
                            tuple(MessageDeliveryStatus.SENT, MessageDeliveryStatus.SKIPPED, MessageContacts.NO_CONTACT));
            assertThat(messageRecipientRepository.findByMessageSendIdOrderByIdAsc(sendId))
                    .allSatisfy(recipient -> assertThat(recipient.getMailTransactionId()).isNotBlank());

            // 기본 로깅 게이트웨이가 실제로 호출됐는지, 그리고 이 호출이 테스트 스레드가 아닌
            // @Async 디스패치 스레드에서 일어났는지를 프로덕션 코드 변경 없이 로그로 확인한다.
            assertThat(mailLogAppender.list).as("LoggingMailGateway 가 호출됐어야 한다").isNotEmpty();
            String dispatchThreadName = mailLogAppender.list.get(0).getThreadName();
            assertThat(dispatchThreadName).as("디스패치는 비동기 스레드에서 실행돼야 한다")
                    .isNotEqualTo(Thread.currentThread().getName());
        } finally {
            mailLogger.detachAppender(mailLogAppender);
            mailLogAppender.stop();
        }
    }

    private MessageSendDetailResponse awaitCompleted(Long id) {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            MessageSendDetailResponse detail = messageHistoryService.detail(id);
            if (detail.status() == MessageSendStatus.COMPLETED) {
                return detail;
            }
            sleepQuietly();
        }
        fail("메시지 발송이 " + POLL_TIMEOUT_MILLIS + "ms 안에 COMPLETED 로 계산되지 않았습니다(sendId=" + id + ").");
        throw new IllegalStateException("unreachable");
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(POLL_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Applicant newApplicant(String name, String phoneNumber) {
        String ci = "test-ci-" + UUID.randomUUID();
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName(name);
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setUserName(name);
        applicant.setPhoneNumber(phoneNumber);
        return applicantRepository.saveAndFlush(applicant);
    }

    private JobApplication submitted(Applicant applicant, String name) {
        JobPosition jobPosition = posting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant, posting, jobPosition, name, posting.getTitle(), jobPosition.getPositionName());
        application.submit(LocalDateTime.of(2026, 9, 10, 9, 0));
        return jobApplicationRepository.saveAndFlush(application);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageHistoryServiceTest" --tests "com.shinyoung.recruit.service.MessageSendAsyncFlowTest" --no-daemon`
Expected: FAIL — `compileTestJava` 오류(`MessageHistoryService`·`MessageHistoryCondition`·응답 DTO 없음).

- [ ] **Step 3: 상태 계산·조회 쿼리**

`{BE}/enumeration/MessageSendStatus.java` 전체:

```java
package com.shinyoung.recruit.enumeration;

/**
 * 발송 1회의 상태. 저장하지 않고 조회할 때 수신자 채널 상태로 계산한다(설계서 7.4):
 * PENDING 이 있으면 SENDING, 없고 REQUESTED 가 있으면 RESULT_PENDING, 둘 다 없으면 COMPLETED.
 */
public enum MessageSendStatus {
    SENDING,
    RESULT_PENDING,
    COMPLETED;

    /** 메일·SMS 채널을 합친 PENDING 수·REQUESTED 수로 상태를 정한다. */
    public static MessageSendStatus of(long pendingCount, long requestedCount) {
        if (pendingCount > 0) {
            return SENDING;
        }
        return requestedCount > 0 ? RESULT_PENDING : COMPLETED;
    }
}
```

`{BE}/domain/repository/MessageRecipientStatusCount.java`:

```java
package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;

/** 발송별 (메일 상태, SMS 상태) 조합의 수신자 수. 이력 목록 건수 계산용 JPQL 생성자 projection. */
public record MessageRecipientStatusCount(
        Long messageSendId,
        MessageDeliveryStatus mailStatus,
        MessageDeliveryStatus smsStatus,
        Long count
) {
}
```

`{BE}/domain/repository/MessageRecipientRepository.java` — import에 `import java.util.Collection;`(`java.time.LocalDateTime` 아래)을 추가하고, `applySmsReport` 메서드 아래(인터페이스 끝 `}` 앞)에 추가:

```java
    /** 이력 목록 건수: 발송별 (메일 상태, SMS 상태) 조합의 수신자 수. 페이지의 발송 id 로 한 번에 센다. */
    @Query("""
            select new com.shinyoung.recruit.domain.repository.MessageRecipientStatusCount(
                r.messageSend.id, r.mailStatus, r.smsStatus, count(r)
            )
            from MessageRecipient r
            where r.messageSend.id in :messageSendIds
            group by r.messageSend.id, r.mailStatus, r.smsStatus
            """)
    List<MessageRecipientStatusCount> countStatusesByMessageSendIds(
            @Param("messageSendIds") Collection<Long> messageSendIds
    );
```

`{BE}/domain/repository/MessageSendRepository.java` 전체:

```java
package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.MessageSend;
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
            order by s.requestedAt desc, s.id desc
            """)
    Page<MessageSend> search(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("type") MessageType type,
            @Param("jobPostingId") Long jobPostingId,
            @Param("test") Boolean test,
            Pageable pageable
    );
}
```

- [ ] **Step 4: 조건·응답 DTO**

`{BE}/dto/condition/MessageHistoryCondition.java`:

```java
package com.shinyoung.recruit.dto.condition;

import com.shinyoung.recruit.enumeration.MessageType;

import java.time.LocalDate;

/**
 * 발송 이력 검색 조건. null 은 "조건 없음": from·to 가 없으면 종료일 오늘, 시작일 = 종료일 - 29일(최근 30일, 양끝 포함).
 * test null = 실발송+테스트, true = 테스트만, false = 실발송만.
 */
public record MessageHistoryCondition(
        LocalDate from,
        LocalDate to,
        MessageType type,
        Long jobPostingId,
        Boolean test
) {
}
```

`{BE}/dto/response/MessageChannelCountResponse.java`:

```java
package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;

import java.util.Map;

/** 채널 하나의 상태별 수신자 수. 끈 채널은 수신자가 모두 skipped 다. */
public record MessageChannelCountResponse(
        long pending,
        long requested,
        long sent,
        long failed,
        long skipped
) {

    public static MessageChannelCountResponse of(Map<MessageDeliveryStatus, Long> counts) {
        return new MessageChannelCountResponse(
                counts.getOrDefault(MessageDeliveryStatus.PENDING, 0L),
                counts.getOrDefault(MessageDeliveryStatus.REQUESTED, 0L),
                counts.getOrDefault(MessageDeliveryStatus.SENT, 0L),
                counts.getOrDefault(MessageDeliveryStatus.FAILED, 0L),
                counts.getOrDefault(MessageDeliveryStatus.SKIPPED, 0L)
        );
    }
}
```

`{BE}/dto/response/MessageSendSummaryResponse.java`:

```java
package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.enumeration.MessageSendStatus;
import com.shinyoung.recruit.enumeration.MessageType;

import java.time.LocalDateTime;

/**
 * 발송 이력 목록 1행. status·건수·delayed 는 조회할 때 수신자 채널 상태로 계산한 값이다(설계서 7.4).
 * title = 메일을 켰으면 메일 제목, 아니면 SMS 원문 앞 40자.
 */
public record MessageSendSummaryResponse(
        Long id,
        LocalDateTime requestedAt,
        MessageType type,
        boolean test,
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
                send.getJobPosting().getTitle(),
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
        if (smsBody == null || smsBody.length() <= SMS_TITLE_LENGTH) {
            return smsBody;
        }
        return smsBody.substring(0, SMS_TITLE_LENGTH);
    }
}
```

`{BE}/dto/response/MessageRecipientResponse.java`:

```java
package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.SmsKind;

/**
 * 이력 상세의 수신자 1명. 연락처는 가리지 않는다(설계서 12절, 2026-09-19 사용자 결정).
 * 파기된 수신자는 name·email·phone 이 null, 테스트 수신자(담당자)는 applicationId 가 null 이다.
 */
public record MessageRecipientResponse(
        Long id,
        Long applicationId,
        String name,
        String email,
        String phone,
        MessageDeliveryStatus mailStatus,
        String mailFailureReason,
        MessageDeliveryStatus smsStatus,
        String smsFailureReason,
        SmsKind smsKind
) {

    public static MessageRecipientResponse from(MessageRecipient recipient) {
        return new MessageRecipientResponse(
                recipient.getId(),
                recipient.getJobApplication() == null ? null : recipient.getJobApplication().getId(),
                recipient.getRecipientName(),
                recipient.getEmail(),
                recipient.getPhone(),
                recipient.getMailStatus(),
                recipient.getMailFailureReason(),
                recipient.getSmsStatus(),
                recipient.getSmsFailureReason(),
                recipient.getSmsKind()
        );
    }
}
```

`{BE}/dto/response/MessageSendDetailResponse.java`:

```java
package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.MessageSend;
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

- [ ] **Step 5: 이력 서비스**

`{BE}/service/MessageHistoryService.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.domain.repository.MessageRecipientStatusCount;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.dto.condition.MessageHistoryCondition;
import com.shinyoung.recruit.dto.response.MessageChannelCountResponse;
import com.shinyoung.recruit.dto.response.MessageRecipientResponse;
import com.shinyoung.recruit.dto.response.MessageSendDetailResponse;
import com.shinyoung.recruit.dto.response.MessageSendSummaryResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.MessageSendStatus;
import com.shinyoung.recruit.exception.InvalidMessageException;
import com.shinyoung.recruit.exception.MessageSendNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 발송 이력 조회(설계서 3.2·7.4). 발송 상태·채널별 건수는 저장하지 않고 수신자 채널 상태를 세어 계산한다.
 * 지연(delayed) = 완료가 아니고 요청 후 recruit.message.result-wait-minutes 가 지남. DB 값은 바꾸지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageHistoryService {

    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_RANGE_DAYS = 30;

    private final MessageSendRepository messageSendRepository;
    private final MessageRecipientRepository messageRecipientRepository;
    private final MessageProperties messageProperties;
    private final Clock clock;

    /** 발송 이력 목록. 기간(발송일, 양끝 포함)을 비우면 종료일 = 오늘, 시작일 = 종료일 - 29일. 최신순. */
    public PageResponse<MessageSendSummaryResponse> search(MessageHistoryCondition condition, int page, int size) {
        validatePaging(page, size);
        LocalDate to = condition.to() != null ? condition.to() : LocalDate.now(clock);
        LocalDate from = condition.from() != null ? condition.from() : to.minusDays(DEFAULT_RANGE_DAYS - 1);
        if (from.isAfter(to)) {
            throw new InvalidMessageException("조회 시작일이 종료일보다 늦습니다.");
        }
        Page<MessageSend> sends = messageSendRepository.search(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                condition.type(), condition.jobPostingId(), condition.test(), PageRequest.of(page, size));
        Map<Long, ChannelTally> tallies = tally(sends.getContent().stream().map(MessageSend::getId).toList());
        LocalDateTime now = LocalDateTime.now(clock);
        return PageResponse.from(sends.map(send ->
                summary(send, tallies.getOrDefault(send.getId(), new ChannelTally()), now)));
    }

    /** 발송 1회의 원문·집계·수신자별 결과(id 순). 연락처는 가리지 않는다. 파기된 수신자는 이름·연락처가 null. */
    public MessageSendDetailResponse detail(Long sendId) {
        MessageSend send = messageSendRepository.findById(sendId)
                .orElseThrow(() -> new MessageSendNotFoundException("발송 기록을 찾을 수 없습니다."));
        List<MessageRecipient> recipients = messageRecipientRepository.findByMessageSendIdOrderByIdAsc(sendId);
        ChannelTally tally = new ChannelTally();
        recipients.forEach(recipient -> tally.add(recipient.getMailStatus(), recipient.getSmsStatus(), 1));
        return MessageSendDetailResponse.of(
                summary(send, tally, LocalDateTime.now(clock)),
                send,
                recipients.stream().map(MessageRecipientResponse::from).toList());
    }

    private MessageSendSummaryResponse summary(MessageSend send, ChannelTally tally, LocalDateTime now) {
        MessageChannelCountResponse mail = tally.mail();
        MessageChannelCountResponse sms = tally.sms();
        MessageSendStatus status = MessageSendStatus.of(
                mail.pending() + sms.pending(), mail.requested() + sms.requested());
        boolean delayed = status != MessageSendStatus.COMPLETED
                && send.getRequestedAt().isBefore(now.minusMinutes(messageProperties.getResultWaitMinutes()));
        return MessageSendSummaryResponse.of(send, mail, sms, status, delayed);
    }

    private Map<Long, ChannelTally> tally(List<Long> sendIds) {
        Map<Long, ChannelTally> tallies = new HashMap<>();
        if (sendIds.isEmpty()) {
            return tallies;
        }
        for (MessageRecipientStatusCount row : messageRecipientRepository.countStatusesByMessageSendIds(sendIds)) {
            tallies.computeIfAbsent(row.messageSendId(), ignored -> new ChannelTally())
                    .add(row.mailStatus(), row.smsStatus(), row.count());
        }
        return tallies;
    }

    private static void validatePaging(int page, int size) {
        if (page < 0) {
            throw new InvalidMessageException("page는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidMessageException("size는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다.");
        }
    }

    /** 발송 1회의 채널별 상태 건수. */
    private static final class ChannelTally {

        private final Map<MessageDeliveryStatus, Long> mailCounts = new EnumMap<>(MessageDeliveryStatus.class);
        private final Map<MessageDeliveryStatus, Long> smsCounts = new EnumMap<>(MessageDeliveryStatus.class);

        void add(MessageDeliveryStatus mailStatus, MessageDeliveryStatus smsStatus, long count) {
            mailCounts.merge(mailStatus, count, Long::sum);
            smsCounts.merge(smsStatus, count, Long::sum);
        }

        MessageChannelCountResponse mail() {
            return MessageChannelCountResponse.of(mailCounts);
        }

        MessageChannelCountResponse sms() {
            return MessageChannelCountResponse.of(smsCounts);
        }
    }
}
```

- [ ] **Step 6: 통과 확인**

Run: Step 2와 같은 명령.
Expected: `BUILD SUCCESSFUL`. `MessageHistoryServiceTest` 7, `MessageSendAsyncFlowTest` 1 통과.

- [ ] **Step 7: 커밋하지 않는다(사용자 지시)**

---

### Task 5: 이력 API와 보안 테스트

**Files:**
- Create: `{BE}/controller/MessageHistoryAdminController.java`
- Test: Create `{BT}/controller/MessageHistoryAdminControllerTest.java`; Modify `{BT}/config/SecurityConfigTest.java`

`WebMvcConfig`가 `/api`를 붙이므로 매핑은 `/admin/messages/history`다. `SecurityConfig`는 바꾸지 않는다(`/api/admin/**`).

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/controller/MessageHistoryAdminControllerTest.java`:

```java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.MessageContacts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class MessageHistoryAdminControllerTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JobPostingRepository jobPostingRepository;
    @Autowired
    private MessageSendRepository messageSendRepository;
    @Autowired
    private MessageRecipientRepository messageRecipientRepository;
    @Autowired
    private Clock clock;

    private MockMvc mockMvc;
    private JobPosting posting;
    private MessageSend send;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        posting = JobPosting.create("이력 API 공고", "Content",
                LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 22, 18, 0));
        posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        posting = jobPostingRepository.saveAndFlush(posting);
        LocalDateTime now = LocalDateTime.now(clock);
        send = messageSendRepository.saveAndFlush(MessageSend.create(
                MessageType.FREE, false, posting, null, "제출 완료", null, null,
                true, false, "[신영증권] #{이름}님 안내", "본문", null, "hr.kim", "김인사", 1, now));
        MessageRecipient recipient = MessageRecipient.create(send, null, "김지원", "kim@example.com", null,
                MessageDeliveryStatus.PENDING, null, MessageDeliveryStatus.SKIPPED, MessageContacts.CHANNEL_OFF, null);
        recipient.recordRequested(MessageChannel.MAIL, "TX-API-1", now);
        messageRecipientRepository.saveAndFlush(recipient);
    }

    @Test
    void 이력_목록을_준다() throws Exception {
        mockMvc.perform(get("/api/admin/messages/history")
                        .param("jobPostingId", String.valueOf(posting.getId()))
                        .with(authentication(employee())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(send.getId()))
                .andExpect(jsonPath("$.data.content[0].jobPostingTitle").value("이력 API 공고"))
                .andExpect(jsonPath("$.data.content[0].title").value("[신영증권] #{이름}님 안내"))
                .andExpect(jsonPath("$.data.content[0].mail.requested").value(1))
                .andExpect(jsonPath("$.data.content[0].sms.skipped").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("RESULT_PENDING"))
                .andExpect(jsonPath("$.data.content[0].delayed").value(false));
    }

    @Test
    void 이력_상세는_원문과_수신자_연락처를_그대로_준다() throws Exception {
        mockMvc.perform(get("/api/admin/messages/history/{sendId}", send.getId())
                        .with(authentication(employee())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(send.getId()))
                .andExpect(jsonPath("$.data.mailSubject").value("[신영증권] #{이름}님 안내"))
                .andExpect(jsonPath("$.data.recipients.length()").value(1))
                .andExpect(jsonPath("$.data.recipients[0].name").value("김지원"))
                .andExpect(jsonPath("$.data.recipients[0].email").value("kim@example.com"))
                .andExpect(jsonPath("$.data.recipients[0].mailStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.data.recipients[0].smsFailureReason").value("CHANNEL_OFF"));
    }

    @Test
    void 없는_발송이면_404() throws Exception {
        mockMvc.perform(get("/api/admin/messages/history/{sendId}", 999_999L)
                        .with(authentication(employee())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("발송 기록을 찾을 수 없습니다."));
    }

    @Test
    void 페이지_크기가_100을_넘으면_400() throws Exception {
        mockMvc.perform(get("/api/admin/messages/history")
                        .param("size", "101")
                        .with(authentication(employee())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("size는 1 이상 100 이하여야 합니다."));
    }

    private Authentication employee() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "hr.kim", "인사팀", "김인사", List.of(new SimpleGrantedAuthority("ROLE_RECRUIT_ADMIN")));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
```

`{BT}/config/SecurityConfigTest.java` — `메시지_발송은_지원자_권한이면_403` 메서드 아래(클래스 끝 `}` 앞)에 추가(import 추가 없음):

```java
    @Test
    void 메시지_발송_이력은_비인증이면_401() throws Exception {
        mockMvc.perform(get("/api/admin/messages/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 메시지_발송_이력은_지원자_권한이면_403() throws Exception {
        mockMvc.perform(get("/api/admin/messages/history")
                        .with(user("applicant").authorities(() -> "ROLE_APPLICANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 메시지_발송_이력_상세는_비인증이면_401() throws Exception {
        mockMvc.perform(get("/api/admin/messages/history/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 메시지_발송_이력_상세는_지원자_권한이면_403() throws Exception {
        mockMvc.perform(get("/api/admin/messages/history/1")
                        .with(user("applicant").authorities(() -> "ROLE_APPLICANT")))
                .andExpect(status().isForbidden());
    }
```

- [ ] **Step 2: 실패 확인**

Run: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.MessageHistoryAdminControllerTest" --no-daemon`
Expected: FAIL — 컨트롤러가 없어 `이력_목록을_준다`·`이력_상세는...`·`페이지_크기가...`가 200/400 대신 404 등으로 실패. (`SecurityConfigTest`의 새 4개는 보안 필터가 먼저 막으므로 컨트롤러가 없어도 통과할 수 있다 — 회귀 고정용이다.)

- [ ] **Step 3: 컨트롤러 작성**

`{BE}/controller/MessageHistoryAdminController.java`:

```java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.condition.MessageHistoryCondition;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.MessageSendDetailResponse;
import com.shinyoung.recruit.dto.response.MessageSendSummaryResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.service.MessageHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 메시지 발송 이력 API: 목록·상세. 상태·건수는 조회할 때 수신자 채널 상태로 계산한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/messages/history")
public class MessageHistoryAdminController {

    private final MessageHistoryService messageHistoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MessageSendSummaryResponse>>> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) MessageType type,
            @RequestParam(required = false) Long jobPostingId,
            @RequestParam(required = false) Boolean test,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        MessageHistoryCondition condition = new MessageHistoryCondition(from, to, type, jobPostingId, test);
        return ResponseEntity.ok(ApiResponse.success(messageHistoryService.search(condition, page, size)));
    }

    @GetMapping("/{sendId}")
    public ResponseEntity<ApiResponse<MessageSendDetailResponse>> detail(@PathVariable Long sendId) {
        return ResponseEntity.ok(ApiResponse.success(messageHistoryService.detail(sendId)));
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.MessageHistoryAdminControllerTest" --tests "com.shinyoung.recruit.config.SecurityConfigTest" --no-daemon`
Expected: `BUILD SUCCESSFUL`. `MessageHistoryAdminControllerTest` 4, `SecurityConfigTest` 27 통과.

- [ ] **Step 5: 커밋하지 않는다(사용자 지시)**

---

## 배치 C — 파기 연동

### Task 6: 지원서 파기 시 메시지 수신자 이름·연락처 파기

**Files:**
- Modify: `{BE}/domain/repository/ApplicationPiiPurgeRepository.java`, `{BE}/service/ApplicationPiiPurgeService.java`
- Test: Modify `{BT}/service/ApplicationPiiPurgeServiceTest.java`

설계서 12절: 그 지원서의 `MessageRecipient` 이름·이메일·휴대폰(암호화 컬럼이라 placeholder 대신 null)과 `createdBy`·`updatedBy`를 null로 바꾼다. 거래 ID·채널 상태·실패 사유(결과코드)는 유지한다. 테스트 수신자(`jobApplication` null)는 대상이 아니다. 기존 bulk update들과 같은 `@Modifying(flushAutomatically = true)` 형식을 따른다. Task 1의 `recordRequested`·거래 ID 필드가 있어야 한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/service/ApplicationPiiPurgeServiceTest.java`:

(1) import 추가(기존 import 목록의 알파벳 위치에):

```java
import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.SmsKind;
```

(2) `@Autowired private AuditHmac auditHmac;` 아래에 필드 추가:

```java
    @Autowired private MessageSendRepository messageSendRepository;
    @Autowired private MessageRecipientRepository messageRecipientRepository;
```

(3) 기존 테스트 메서드 아래(클래스 끝 `}` 앞)에 추가:

```java
    @Test
    void 메시지_수신자의_이름과_연락처가_파기되고_발송_결과는_남는다() {
        JobPosting posting = JobPosting.create("메시지 파기 공고", "Content",
                LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 22, 18, 0));
        posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        posting = jobPostingRepository.saveAndFlush(posting);
        Applicant applicant = new Applicant("message-pii-ci", HashUtil.sha256("message-pii-ci"));
        applicant.setLoginId("message-pii-applicant");
        applicant.setName("김지원");
        applicant.setUserName("김지원");
        applicant.setPhoneNumber("01000000000");
        applicant = applicantRepository.save(applicant);
        JobPosition position = posting.getJobPositions().get(0);
        JobApplication application = jobApplicationRepository.save(JobApplication.create(
                applicant, posting, position, "김지원", posting.getTitle(), position.getPositionName()));

        MessageSend send = messageSendRepository.save(MessageSend.create(
                MessageType.FREE, false, posting, null, "제출 완료", null, null,
                true, true, "제목", "본문", "문자", "hr.kim", "김인사", 2, LocalDateTime.of(2026, 9, 19, 10, 0)));
        MessageRecipient applicantRecipient = MessageRecipient.create(
                send, application, "김지원", "applicant@example.com", "01000000000",
                MessageDeliveryStatus.PENDING, null, MessageDeliveryStatus.PENDING, null, SmsKind.SMS);
        applicantRecipient.recordRequested(MessageChannel.MAIL, "TX-PURGE-MAIL", LocalDateTime.of(2026, 9, 19, 10, 1));
        applicantRecipient.recordResult(MessageChannel.SMS, MessageDeliveryStatus.FAILED, "9999",
                LocalDateTime.of(2026, 9, 19, 10, 2));
        applicantRecipient = messageRecipientRepository.save(applicantRecipient);
        MessageRecipient tester = messageRecipientRepository.save(MessageRecipient.create(
                send, null, "김인사", "hr.kim@example.com", "01000001234",
                MessageDeliveryStatus.PENDING, null, MessageDeliveryStatus.PENDING, null, SmsKind.SMS));
        entityManager.flush();

        applicationPiiPurgeService.purgeRelationalPii(application.getId());
        entityManager.flush();
        entityManager.clear();

        MessageRecipient purged = messageRecipientRepository.findById(applicantRecipient.getId()).orElseThrow();
        assertThat(purged.getRecipientName()).isNull();
        assertThat(purged.getEmail()).isNull();
        assertThat(purged.getPhone()).isNull();
        assertThat(purged.getCreatedBy()).isNull();
        assertThat(purged.getUpdatedBy()).isNull();
        assertThat(purged.getMailStatus()).isEqualTo(MessageDeliveryStatus.REQUESTED); // KEEP
        assertThat(purged.getMailTransactionId()).isEqualTo("TX-PURGE-MAIL"); // KEEP
        assertThat(purged.getSmsStatus()).isEqualTo(MessageDeliveryStatus.FAILED); // KEEP
        assertThat(purged.getSmsFailureReason()).isEqualTo("9999"); // KEEP(결과코드)
        MessageRecipient keptTester = messageRecipientRepository.findById(tester.getId()).orElseThrow();
        assertThat(keptTester.getRecipientName()).isEqualTo("김인사"); // 테스트 수신자는 대상 아님
        assertThat(keptTester.getEmail()).isEqualTo("hr.kim@example.com");
    }
```

- [ ] **Step 2: 실패 확인**

Run: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationPiiPurgeServiceTest" --no-daemon`
Expected: FAIL — `메시지_수신자의_이름과_연락처가_파기되고_발송_결과는_남는다`에서 `recipientName`이 `"김지원"`(null 기대). 기존 테스트 1개는 통과.

- [ ] **Step 3: 파기 쿼리와 호출**

`{BE}/domain/repository/ApplicationPiiPurgeRepository.java` — `purgeStageResultCorrectionHistories` 메서드 아래(인터페이스 끝 `}` 앞)에 추가:

```java
    /**
     * 메시지 수신자(message 카드) — 발송 시점 이름·연락처(AES 암호화 컬럼이라 placeholder 불가, NULLIFY)와 감사 필드.
     * 채널 상태·거래 ID·실패 사유(결과코드)는 개인정보가 아니라 KEEP_TOMBSTONE. 테스트 수신자(jobApplication null)는 대상 아님.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update MessageRecipient r
            set r.recipientName = null, r.email = null, r.phone = null,
                r.createdBy = null, r.updatedBy = null
            where r.jobApplication.id = :applicationId""")
    int purgeMessageRecipients(@Param("applicationId") Long applicationId);
```

`{BE}/service/ApplicationPiiPurgeService.java` — `purgeRepository.purgeStageResultCorrectionHistories(applicationId);`와 `purgeRepository.purgeJobApplicationAuditFields(applicationId);` 사이에 추가:

```java
        // 메시지 수신자 이름·연락처(message 카드) — 발송 결과·거래 ID 는 유지.
        purgeRepository.purgeMessageRecipients(applicationId);
```

같은 파일 클래스 Javadoc의 처리 범위 문장 `answers/학력(+semesterGrade 감사필드)/경력(+profile)/자격(번호는 HMAC HASH_ONLY)/어학/병역/수상/공백/평가 comment.`를 아래로 바꾼다:

```java
 * answers/학력(+semesterGrade 감사필드)/경력(+profile)/자격(번호는 HMAC HASH_ONLY)/어학/병역/수상/공백/평가 comment,
 * 메시지 수신자 이름·연락처(message 카드).
```

- [ ] **Step 4: 통과 확인**

Run: Step 2와 같은 명령.
Expected: `BUILD SUCCESSFUL`. `ApplicationPiiPurgeServiceTest` 2 통과.

- [ ] **Step 5: 커밋하지 않는다(사용자 지시)**

---

## 배치 D — 프론트

### Task 7: 타입·API·이력 유틸·테스트 카드 표시

**Files:**
- Modify: `{FE}/types/admin/message.ts`, `{FE}/api/admin/messageApi.ts`
- Create: `{FE}/views/admin/message/messageHistory.ts`
- Test: Create `{FE}/views/admin/message/__tests__/messageHistory.spec.ts`
- Modify: `{FE}/views/admin/message/MessageTestSendCard.vue` (`MessageDeliveryStatus`에 `REQUESTED`가 생기면 `Record<MessageDeliveryStatus, string>`이 타입 오류가 나므로 이 Task에서 함께 고친다)

- [ ] **Step 1: 실패하는 테스트 작성**

`{FE}/views/admin/message/__tests__/messageHistory.spec.ts`:

```ts
import { describe, expect, it } from 'vitest'

import type { MessageChannelCount, MessageHistoryRecipient, MessageSendDetail } from '@/types/admin/message'
import {
  DELIVERY_STATUS_COLOR,
  DELIVERY_STATUS_LABEL,
  channelCellText,
  channelSummaryText,
  defaultHistoryRange,
  failureReasonLabel,
  hasRequested,
  isPurgedRecipient,
  recipientNameLabel,
  resultCounts,
  sendStatusView,
  toTestResults,
} from '../messageHistory'

const count = (overrides: Partial<MessageChannelCount> = {}): MessageChannelCount => ({
  pending: 0,
  requested: 0,
  sent: 0,
  failed: 0,
  skipped: 0,
  ...overrides,
})

const recipient = (overrides: Partial<MessageHistoryRecipient> = {}): MessageHistoryRecipient => ({
  id: 1,
  applicationId: 10,
  name: '김지원',
  email: 'kim@example.com',
  phone: '01000000000',
  mailStatus: 'SENT',
  mailFailureReason: null,
  smsStatus: 'SKIPPED',
  smsFailureReason: 'NO_CONTACT',
  smsKind: null,
  ...overrides,
})

const detail = (recipients: MessageHistoryRecipient[]): MessageSendDetail => ({
  id: 7,
  requestedAt: '2026-09-19T10:00:00',
  type: 'FREE',
  test: true,
  jobPostingTitle: '공고',
  stageName: null,
  conditionSummary: '제출 완료',
  title: '[신영증권] 안내',
  mailEnabled: true,
  smsEnabled: true,
  recipientCount: recipients.length,
  mail: count(),
  sms: count(),
  status: 'RESULT_PENDING',
  delayed: false,
  senderName: '김인사',
  templateName: null,
  mailSubject: '[신영증권] 안내',
  mailBody: '본문',
  smsBody: '문자',
  recipients,
})

describe('sendStatusView', () => {
  it('계산된 발송 상태를 라벨과 태그 색으로 바꾼다', () => {
    expect(sendStatusView('SENDING', false)).toEqual({ label: '발송 중', color: 'blue' })
    expect(sendStatusView('RESULT_PENDING', false)).toEqual({ label: '결과 수신 중', color: 'processing' })
    expect(sendStatusView('COMPLETED', false)).toEqual({ label: '완료', color: 'green' })
  })

  it('지연이면 결과 수신 중은 결과 미수신, 발송 중은 발송 중단으로 보인다', () => {
    expect(sendStatusView('RESULT_PENDING', true)).toEqual({ label: '결과 미수신', color: 'orange' })
    expect(sendStatusView('SENDING', true)).toEqual({ label: '발송 중단', color: 'red' })
    expect(sendStatusView('COMPLETED', true)).toEqual({ label: '완료', color: 'green' })
  })
})

describe('채널 결과 표시', () => {
  it('REQUESTED 는 결과 수신 중(processing 태그)이다', () => {
    expect(DELIVERY_STATUS_LABEL.REQUESTED).toBe('결과 수신 중')
    expect(DELIVERY_STATUS_COLOR.REQUESTED).toBe('processing')
    expect(DELIVERY_STATUS_LABEL.PENDING).toBe('대기')
    expect(DELIVERY_STATUS_LABEL.SENT).toBe('성공')
    expect(DELIVERY_STATUS_LABEL.FAILED).toBe('실패')
    expect(DELIVERY_STATUS_LABEL.SKIPPED).toBe('제외')
  })

  it('정해진 사유는 한글로, 그 밖의 값은 솔루션 결과코드로 보인다', () => {
    expect(failureReasonLabel('NO_CONTACT')).toBe('연락처 없음')
    expect(failureReasonLabel('INVALID_CONTACT')).toBe('형식 오류')
    expect(failureReasonLabel('CHANNEL_OFF')).toBe('채널 끔')
    expect(failureReasonLabel('GATEWAY_ERROR')).toBe('발송 오류')
    expect(failureReasonLabel('9999')).toBe('결과코드 9999')
    expect(failureReasonLabel(null)).toBe('')
  })
})

describe('채널 건수', () => {
  it('목록 채널 칸은 끈 채널이면 제외, 켠 채널이면 제외를 뺀 건수다', () => {
    expect(channelCellText(false, count({ skipped: 3 }))).toBe('제외')
    expect(channelCellText(true, count({ requested: 1, sent: 2, failed: 1, skipped: 1 }))).toBe('4건')
  })

  it('상세 채널 집계는 성공·실패·수신 중·제외를 한 줄로 보여 준다', () => {
    expect(channelSummaryText(true, count({ pending: 1, requested: 1, sent: 2, failed: 1, skipped: 1 }))).toBe(
      '5건 · 성공 2 · 실패 1 · 수신 중 2 · 제외 1',
    )
    expect(channelSummaryText(false, count({ skipped: 2 }))).toBe('이번 발송에서 제외')
  })

  it('목록 결과는 두 채널을 합치고 수신 중에는 호출 전과 결과 대기를 모두 센다', () => {
    expect(resultCounts(count({ sent: 2, requested: 1 }), count({ failed: 1, pending: 2 }))).toEqual({
      sent: 2,
      failed: 1,
      inProgress: 3,
    })
  })
})

describe('수신자 표시', () => {
  it('지원서 수신자인데 이름·연락처가 모두 비었으면 파기됨으로 보인다', () => {
    const purged = recipient({ name: null, email: null, phone: null })

    expect(isPurgedRecipient(purged)).toBe(true)
    expect(recipientNameLabel(purged)).toBe('(파기됨)')
    expect(isPurgedRecipient(recipient({ applicationId: null, name: null, email: null, phone: null }))).toBe(false)
    expect(recipientNameLabel(recipient())).toBe('김지원')
  })
})

describe('toTestResults', () => {
  it('이력 상세 수신자를 테스트 발송 응답과 같은 모양(수신자마다 메일 → SMS)으로 바꾼다', () => {
    const results = toTestResults(
      detail([
        recipient({ name: '김인사', mailStatus: 'SENT', smsStatus: 'FAILED', smsFailureReason: '9999' }),
        recipient({ id: 2, name: '이인사', mailStatus: 'REQUESTED', smsStatus: 'SKIPPED' }),
      ]),
    )

    expect(results).toEqual([
      { name: '김인사', channel: 'MAIL', status: 'SENT', failureReason: null },
      { name: '김인사', channel: 'SMS', status: 'FAILED', failureReason: '9999' },
      { name: '이인사', channel: 'MAIL', status: 'REQUESTED', failureReason: null },
      { name: '이인사', channel: 'SMS', status: 'SKIPPED', failureReason: 'NO_CONTACT' },
    ])
    expect(hasRequested(results)).toBe(true)
    expect(hasRequested(results.filter((result) => result.status !== 'REQUESTED'))).toBe(false)
  })
})

describe('defaultHistoryRange', () => {
  it('오늘을 포함한 최근 30일(시작일 = 오늘 - 29일)을 YYYY-MM-DD 로 준다', () => {
    expect(defaultHistoryRange(new Date(2026, 8, 19, 15, 30))).toEqual(['2026-08-21', '2026-09-19'])
    expect(defaultHistoryRange(new Date(2026, 2, 1))).toEqual(['2026-01-31', '2026-03-01'])
  })
})
```

- [ ] **Step 2: 실패 확인**

Run(`recruit_front/`): `npx vitest run src/views/admin/message/__tests__/messageHistory.spec.ts`
Expected: FAIL — `Failed to resolve import "../messageHistory"`.

- [ ] **Step 3: 타입**

`{FE}/types/admin/message.ts`:

(1) 파일 첫 주석의 `대상자·발송 관련 요청·응답 DTO 와 대응한다` → `대상자·발송·발송 이력 관련 요청·응답 DTO 와 대응한다`.

(2) 아래 세 줄을

```ts
export type MessageChannel = 'MAIL' | 'SMS'
export type MessageDeliveryStatus = 'PENDING' | 'SENT' | 'FAILED' | 'SKIPPED'
export type MessageSendStatus = 'SENDING' | 'COMPLETED'
```

다음으로 바꾼다:

```ts
export type MessageChannel = 'MAIL' | 'SMS'
/** 수신자·채널별 결과. REQUESTED = 솔루션 접수(결과 수신 중), 최종은 SENT·FAILED. SKIPPED 는 보내지 않음. */
export type MessageDeliveryStatus = 'PENDING' | 'REQUESTED' | 'SENT' | 'FAILED' | 'SKIPPED'
/** 발송 1회 상태. 서버가 조회할 때 수신자 채널 상태로 계산한다. */
export type MessageSendStatus = 'SENDING' | 'RESULT_PENDING' | 'COMPLETED'
```

(3) `MessageTestSendResultItem`을 다음으로 바꾼다:

```ts
export interface MessageTestSendResultItem {
  name: string
  channel: MessageChannel
  /** 응답 시점에는 접수 결과(REQUESTED·FAILED·SKIPPED). 최종 결과는 발송 이력 상세로 갱신한다. */
  status: MessageDeliveryStatus
  /** NO_CONTACT · INVALID_CONTACT · CHANNEL_OFF · GATEWAY_ERROR 또는 솔루션 결과코드 */
  failureReason: string | null
}
```

(4) 파일 끝에 추가:

```ts
/** 발송 이력 조회 조건. 날짜는 YYYY-MM-DD(양끝 포함). 값이 없는 키는 조건 없음(기간은 서버 기본 최근 30일). */
export interface MessageHistoryQuery {
  from?: string
  to?: string
  type?: MessageType
  jobPostingId?: number
  /** 없으면 실발송+테스트, true 테스트만, false 실발송만 */
  test?: boolean
  page: number
  size: number
}

/** 채널 하나의 상태별 수신자 수. */
export interface MessageChannelCount {
  pending: number
  requested: number
  sent: number
  failed: number
  skipped: number
}

/** 발송 이력 목록 1행. status·건수·delayed 는 서버가 조회할 때 계산한다. */
export interface MessageSendSummary {
  id: number
  requestedAt: string
  type: MessageType
  test: boolean
  jobPostingTitle: string
  stageName: string | null
  conditionSummary: string | null
  /** 메일 제목. 메일을 끈 발송은 SMS 원문 앞 40자 */
  title: string | null
  mailEnabled: boolean
  smsEnabled: boolean
  recipientCount: number
  mail: MessageChannelCount
  sms: MessageChannelCount
  status: MessageSendStatus
  /** 요청 후 결과 대기 시간(기본 60분)이 지났는데 완료가 아님 */
  delayed: boolean
  senderName: string | null
}

/** 이력 상세의 수신자 1명. 연락처는 원문. 파기된 수신자는 name·email·phone 이 null. */
export interface MessageHistoryRecipient {
  id: number
  /** 테스트 수신자(담당자)는 null */
  applicationId: number | null
  name: string | null
  email: string | null
  phone: string | null
  mailStatus: MessageDeliveryStatus
  mailFailureReason: string | null
  smsStatus: MessageDeliveryStatus
  smsFailureReason: string | null
  smsKind: 'SMS' | 'LMS' | null
}

/** 발송 이력 상세 = 목록 필드 + 치환 전 원문 + 수신자별 결과(id 순). */
export interface MessageSendDetail extends MessageSendSummary {
  templateName: string | null
  mailSubject: string | null
  mailBody: string | null
  smsBody: string | null
  recipients: MessageHistoryRecipient[]
}
```

- [ ] **Step 4: API**

`{FE}/api/admin/messageApi.ts`:

(1) `import type { ... } from '@/types/admin/message'` 목록에 `MessageHistoryQuery`, `MessageSendDetail`, `MessageSendSummary`를 알파벳 위치에 추가하고, 그 import 아래에 한 줄 추가:

```ts
import type { PageResponse } from '@/types/page'
```

(2) 모듈 주석 `/** 메시지 발송 기능 API 모듈. 템플릿·변수·대상자 조회·테스트 발송·발송 접수. 이력은 S4에서 추가한다. */` → `/** 메시지 발송 기능 API 모듈. 템플릿·변수·대상자 조회·테스트 발송·발송 접수·발송 이력. */`

(3) `send(request: MessageSendRequest) { ... },` 바로 아래에 추가:

```ts
  /** 발송 이력 목록(발송일시 최신순). 기간을 비우면 서버 기본 최근 30일. 상태·건수는 조회 때 계산된다. */
  getHistory(query: MessageHistoryQuery) {
    return apiClient.get<ApiResponse<PageResponse<MessageSendSummary>>>('/admin/messages/history', {
      params: query,
    })
  },

  /** 발송 1회의 원문·집계·수신자별 결과. 테스트 발송 카드와 이력 드로어가 결과 수신 중일 때 다시 읽는다. */
  getHistoryDetail(sendId: number) {
    return apiClient.get<ApiResponse<MessageSendDetail>>(`/admin/messages/history/${sendId}`)
  },
```

- [ ] **Step 5: 이력 유틸**

`{FE}/views/admin/message/messageHistory.ts`:

```ts
import { formatDate } from '@/common/dateUtil'
import type {
  MessageChannelCount,
  MessageDeliveryStatus,
  MessageHistoryRecipient,
  MessageSendDetail,
  MessageSendStatus,
  MessageTestSendResultItem,
} from '@/types/admin/message'

/** 발송 이력 기본 기간(일). 서버 MessageHistoryService 기본값(종료일 오늘, 시작일 = 종료일 - 29일)과 같다. */
export const HISTORY_RANGE_DAYS = 30

export const DELIVERY_STATUS_LABEL: Record<MessageDeliveryStatus, string> = {
  PENDING: '대기',
  REQUESTED: '결과 수신 중',
  SENT: '성공',
  FAILED: '실패',
  SKIPPED: '제외',
}

/** a-tag color. */
export const DELIVERY_STATUS_COLOR: Record<MessageDeliveryStatus, string> = {
  PENDING: 'default',
  REQUESTED: 'processing',
  SENT: 'green',
  FAILED: 'red',
  SKIPPED: 'default',
}

const FAILURE_REASON_LABEL: Record<string, string> = {
  NO_CONTACT: '연락처 없음',
  INVALID_CONTACT: '형식 오류',
  CHANNEL_OFF: '채널 끔',
  GATEWAY_ERROR: '발송 오류',
}

/** 실패·제외 사유. 서버가 정한 코드가 아니면 솔루션 결과코드다(코드별 설명은 스펙 확정 후, 설계서 17절 6번). */
export const failureReasonLabel = (reason: string | null): string => {
  if (!reason) return ''
  return FAILURE_REASON_LABEL[reason] ?? `결과코드 ${reason}`
}

export interface StatusView {
  label: string
  color: string
}

/** 계산된 발송 상태의 표시. 지연(delayed)이면 결과 수신 중 → 결과 미수신, 발송 중 → 발송 중단(설계서 7.4). */
export const sendStatusView = (status: MessageSendStatus, delayed: boolean): StatusView => {
  if (status === 'COMPLETED') return { label: '완료', color: 'green' }
  if (status === 'RESULT_PENDING') {
    return delayed ? { label: '결과 미수신', color: 'orange' } : { label: '결과 수신 중', color: 'processing' }
  }
  return delayed ? { label: '발송 중단', color: 'red' } : { label: '발송 중', color: 'blue' }
}

/** 그 채널로 실제로 보내려 한 건수(제외 빼고). */
const targetCount = (count: MessageChannelCount): number =>
  count.pending + count.requested + count.sent + count.failed

/** 목록의 채널 칸. 끈 채널은 수신자가 모두 SKIPPED 라 건수가 아니라 enabled 로 판단해 '제외'로 보인다. */
export const channelCellText = (enabled: boolean, count: MessageChannelCount): string =>
  enabled ? `${targetCount(count)}건` : '제외'

/** 상세의 채널 집계 한 줄. */
export const channelSummaryText = (enabled: boolean, count: MessageChannelCount): string => {
  if (!enabled) return '이번 발송에서 제외'
  const inProgress = count.pending + count.requested
  return `${targetCount(count)}건 · 성공 ${count.sent} · 실패 ${count.failed} · 수신 중 ${inProgress} · 제외 ${count.skipped}`
}

export interface ResultCounts {
  sent: number
  failed: number
  /** 호출 전(PENDING) + 결과 대기(REQUESTED) */
  inProgress: number
}

/** 목록 결과 칸: 두 채널 합계. */
export const resultCounts = (mail: MessageChannelCount, sms: MessageChannelCount): ResultCounts => ({
  sent: mail.sent + sms.sent,
  failed: mail.failed + sms.failed,
  inProgress: mail.pending + mail.requested + sms.pending + sms.requested,
})

/** 지원서 수신자인데 이름·연락처가 모두 비었으면 파기된 수신자다(테스트 수신자는 파기 대상이 아니다). */
export const isPurgedRecipient = (recipient: MessageHistoryRecipient): boolean =>
  recipient.applicationId !== null && recipient.name === null && recipient.email === null && recipient.phone === null

export const recipientNameLabel = (recipient: MessageHistoryRecipient): string =>
  isPurgedRecipient(recipient) ? '(파기됨)' : (recipient.name ?? '-')

/** 테스트 발송 카드용: 이력 상세의 수신자별 결과를 테스트 발송 응답과 같은 모양(수신자마다 메일 → SMS)으로 바꾼다. */
export const toTestResults = (detail: MessageSendDetail): MessageTestSendResultItem[] =>
  detail.recipients.flatMap((recipient): MessageTestSendResultItem[] => [
    {
      name: recipient.name ?? '',
      channel: 'MAIL',
      status: recipient.mailStatus,
      failureReason: recipient.mailFailureReason,
    },
    {
      name: recipient.name ?? '',
      channel: 'SMS',
      status: recipient.smsStatus,
      failureReason: recipient.smsFailureReason,
    },
  ])

/** 결과 수신 중(REQUESTED)인 채널이 남았는지. */
export const hasRequested = (results: MessageTestSendResultItem[]): boolean =>
  results.some((result) => result.status === 'REQUESTED')

/** 이력 화면 기본 기간 [시작일, 종료일](YYYY-MM-DD, 양끝 포함, 로컬 날짜 기준). */
export const defaultHistoryRange = (today: Date): [string, string] => {
  const from = new Date(today.getFullYear(), today.getMonth(), today.getDate() - (HISTORY_RANGE_DAYS - 1))
  return [formatDate(from, 'YYYY-MM-DD'), formatDate(today, 'YYYY-MM-DD')]
}
```

- [ ] **Step 6: 테스트 카드 표시**

`{FE}/views/admin/message/MessageTestSendCard.vue`:

(1) import 두 개

```ts
import type {
  MessageChannel,
  MessageDeliveryStatus,
  MessageTester,
  MessageTestSendResultItem,
} from '@/types/admin/message'
import { isValidEmail, isValidPhone } from './messageSendSummary'
```

를 다음으로 바꾼다:

```ts
import type { MessageChannel, MessageTester, MessageTestSendResultItem } from '@/types/admin/message'
import { DELIVERY_STATUS_COLOR, DELIVERY_STATUS_LABEL, failureReasonLabel } from './messageHistory'
import { isValidEmail, isValidPhone } from './messageSendSummary'
```

(2) `const STATUS_TEXT: Record<MessageDeliveryStatus, string> = { ... }` 블록과 `const REASON_TEXT: Record<string, string> = { ... }` 블록을 지운다(`CHANNEL_TEXT`는 남긴다).

(3) 템플릿 결과 목록의

```vue
        <a-tag :color="result.status === 'SENT' ? 'green' : 'red'">{{ STATUS_TEXT[result.status] }}</a-tag>
        {{ result.name }} · {{ CHANNEL_TEXT[result.channel] }}
        <span v-if="result.failureReason" class="reason">
          ({{ REASON_TEXT[result.failureReason] ?? result.failureReason }})
        </span>
```

를 다음으로 바꾼다:

```vue
        <a-tag :color="DELIVERY_STATUS_COLOR[result.status]">{{ DELIVERY_STATUS_LABEL[result.status] }}</a-tag>
        {{ result.name }} · {{ CHANNEL_TEXT[result.channel] }}
        <span v-if="result.failureReason" class="reason">({{ failureReasonLabel(result.failureReason) }})</span>
```

- [ ] **Step 7: 통과 확인**

Run(`recruit_front/`): `npx vitest run src/views/admin/message`
Expected: 4 files, 36 passed(기존 26 + 새 10).

Run: `npm run type-check`
Expected: 오류 0.

- [ ] **Step 8: 커밋하지 않는다(사용자 지시)**

---

### Task 8: 발송 이력 화면·상세 드로어·라우트

**Files:**
- Create: `{FE}/views/admin/message/AdminMessageHistoryView.vue`, `{FE}/views/admin/message/MessageHistoryDrawer.vue`
- Modify: `{FE}/routes/adminRoutes.ts`

화면 SFC는 기존 message 화면처럼 단위 테스트 대상이 아니다(표시 규칙은 Task 7 유틸에서 검증했다). 검증은 type-check·eslint다. 기간 선택은 결정 16대로 문자열만 쓴다.

- [ ] **Step 1: 라우트**

`{FE}/routes/adminRoutes.ts` — `AdminMessageSend` 라우트 객체 바로 아래(= `AdminMessageTemplates` 위)에 추가(권한·레이아웃은 부모 `meta`를 그대로 물려받는다):

```ts
      {
        path: 'messages/history',
        name: 'AdminMessageHistory',
        component: () => import('@/views/admin/message/AdminMessageHistoryView.vue'),
      },
```

- [ ] **Step 2: 상세 드로어**

`{FE}/views/admin/message/MessageHistoryDrawer.vue`:

```vue
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { message } from 'ant-design-vue'

import { messageApi } from '@/api/admin/messageApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import type { MessageHistoryRecipient, MessageSendDetail } from '@/types/admin/message'
import {
  DELIVERY_STATUS_COLOR,
  DELIVERY_STATUS_LABEL,
  channelSummaryText,
  failureReasonLabel,
  recipientNameLabel,
  sendStatusView,
} from './messageHistory'
import { messageTypeLabel } from './messageTypes'

/* 완료가 아니면 열려 있는 동안 5초마다 다시 읽는다(설계서 3.2). */
const REFRESH_INTERVAL_MS = 5000

const props = defineProps<{
  /** 열 발송 번호 */
  sendId: number | null
}>()

const open = defineModel<boolean>('open', { required: true })

const detail = ref<MessageSendDetail | null>(null)
const loading = ref(false)

/* 닫거나 다른 발송을 연 뒤 늦게 도착한 응답은 요청 번호로 버린다. */
let request = 0
let refreshTimer: ReturnType<typeof setTimeout> | null = null

const stopRefresh = (): void => {
  if (refreshTimer !== null) {
    clearTimeout(refreshTimer)
    refreshTimer = null
  }
}

const status = computed(() => (detail.value ? sendStatusView(detail.value.status, detail.value.delayed) : null))

const recipientColumns = [
  { title: '수험번호', key: 'applicationId', width: 100 },
  { title: '이름', key: 'name', width: 110 },
  { title: '이메일', key: 'email' },
  { title: '휴대폰', key: 'phone', width: 130 },
  { title: '메일 결과', key: 'mail', width: 190 },
  { title: 'SMS 결과', key: 'sms', width: 220 },
]

const asRecipient = (record: unknown): MessageHistoryRecipient => record as MessageHistoryRecipient

const load = async (): Promise<void> => {
  const sendId = props.sendId
  if (sendId === null || !open.value) return
  const current = ++request
  stopRefresh()
  loading.value = detail.value === null
  try {
    const response = await messageApi.getHistoryDetail(sendId)
    if (current !== request) return
    const loaded = response.data.data
    detail.value = loaded
    if (loaded.status !== 'COMPLETED') {
      refreshTimer = setTimeout(() => {
        refreshTimer = null
        void load()
      }, REFRESH_INTERVAL_MS)
    }
  } catch (error) {
    if (current !== request) return
    message.error(getApiErrorMessage(error, '발송 상세를 불러오지 못했습니다.'))
  } finally {
    if (current === request) {
      loading.value = false
    }
  }
}

watch(
  [open, () => props.sendId],
  ([isOpen]) => {
    request += 1
    stopRefresh()
    loading.value = false
    if (isOpen) {
      detail.value = null
      void load()
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  request += 1
  stopRefresh()
})
</script>

<template>
  <a-drawer v-model:open="open" :title="detail ? `발송 상세 · 발송 번호 ${detail.id}` : '발송 상세'" width="980">
    <a-spin :spinning="loading">
      <template v-if="detail">
        <a-descriptions bordered size="small" :column="2">
          <a-descriptions-item label="종류">
            {{ messageTypeLabel(detail.type) }}
            <a-tag v-if="detail.test" color="purple">테스트</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="발송일시">{{ formatDate(detail.requestedAt, 'YYYY-MM-DD HH:mm:ss') }}</a-descriptions-item>
          <a-descriptions-item label="공고">{{ detail.jobPostingTitle }}</a-descriptions-item>
          <a-descriptions-item label="전형 · 조건">{{ detail.conditionSummary || '-' }}</a-descriptions-item>
          <a-descriptions-item label="템플릿">{{ detail.templateName ?? '직접 작성' }}</a-descriptions-item>
          <a-descriptions-item label="발송자">{{ detail.senderName ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="상태">
            <a-tag v-if="status" :color="status.color">{{ status.label }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="대상">{{ detail.recipientCount }}명</a-descriptions-item>
          <a-descriptions-item label="메일">{{ channelSummaryText(detail.mailEnabled, detail.mail) }}</a-descriptions-item>
          <a-descriptions-item label="SMS">{{ channelSummaryText(detail.smsEnabled, detail.sms) }}</a-descriptions-item>
        </a-descriptions>

        <h3 class="section-title">발송 원문 <span class="section-hint">변수 치환 전</span></h3>
        <div class="originals">
          <div class="original">
            <div class="original-label">메일</div>
            <template v-if="detail.mailEnabled">
              <div class="original-subject">{{ detail.mailSubject }}</div>
              <pre class="original-body">{{ detail.mailBody }}</pre>
            </template>
            <p v-else class="muted">이번 발송에서 제외</p>
          </div>
          <div class="original">
            <div class="original-label">SMS</div>
            <pre v-if="detail.smsEnabled" class="original-body">{{ detail.smsBody }}</pre>
            <p v-else class="muted">이번 발송에서 제외</p>
          </div>
        </div>

        <h3 class="section-title">수신자별 결과 <span class="section-hint">{{ detail.recipients.length }}명</span></h3>
        <a-table
          :columns="recipientColumns"
          :data-source="detail.recipients"
          row-key="id"
          size="small"
          :pagination="{ pageSize: 50, showSizeChanger: false }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'applicationId'">{{ asRecipient(record).applicationId ?? '테스트' }}</template>
            <template v-else-if="column.key === 'name'">{{ recipientNameLabel(asRecipient(record)) }}</template>
            <template v-else-if="column.key === 'email'">{{ asRecipient(record).email ?? '-' }}</template>
            <template v-else-if="column.key === 'phone'">{{ asRecipient(record).phone ?? '-' }}</template>
            <template v-else-if="column.key === 'mail'">
              <a-tag :color="DELIVERY_STATUS_COLOR[asRecipient(record).mailStatus]">
                {{ DELIVERY_STATUS_LABEL[asRecipient(record).mailStatus] }}
              </a-tag>
              <span class="reason">{{ failureReasonLabel(asRecipient(record).mailFailureReason) }}</span>
            </template>
            <template v-else-if="column.key === 'sms'">
              <a-tag :color="DELIVERY_STATUS_COLOR[asRecipient(record).smsStatus]">
                {{ DELIVERY_STATUS_LABEL[asRecipient(record).smsStatus] }}
              </a-tag>
              <span v-if="asRecipient(record).smsKind" class="kind">{{ asRecipient(record).smsKind }}</span>
              <span class="reason">{{ failureReasonLabel(asRecipient(record).smsFailureReason) }}</span>
            </template>
          </template>
        </a-table>
      </template>
      <a-empty v-else-if="!loading" description="발송 상세를 불러오지 못했습니다." />
    </a-spin>
  </a-drawer>
</template>

<style scoped lang="scss">
.section-title {
  margin: 20px 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.section-hint {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 400;
  color: var(--app-text-secondary);
}

.originals {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.original {
  padding: 12px;
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius-lg);
  background: var(--app-bg-muted);
}

.original-label {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--app-text-secondary);
}

.original-subject {
  margin-bottom: 6px;
  font-weight: 600;
}

.original-body {
  margin: 0;
  font-family: inherit;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
}

.muted,
.reason,
.kind {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.kind {
  margin-right: 4px;
}
</style>
```

- [ ] **Step 3: 이력 목록 화면**

`{FE}/views/admin/message/AdminMessageHistoryView.vue`:

```vue
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'

import { getAllJobPostings } from '@/api/adminJobPostingApi'
import { messageApi } from '@/api/admin/messageApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import type { MessageHistoryQuery, MessageSendSummary, MessageType } from '@/types/admin/message'
import type { AdminJobPostingListItem } from '@/types/jobPosting'
import MessageHistoryDrawer from './MessageHistoryDrawer.vue'
import { channelCellText, defaultHistoryRange, resultCounts, sendStatusView } from './messageHistory'
import { MESSAGE_TYPES, messageTypeLabel } from './messageTypes'

type TestFilter = 'ALL' | 'REAL' | 'TEST'
type HistoryFilter = Omit<MessageHistoryQuery, 'page' | 'size'>

const PAGE_SIZE = 20

const TYPE_OPTIONS = MESSAGE_TYPES.map((meta) => ({ value: meta.type, label: meta.name }))
const TEST_FILTER_OPTIONS: { value: TestFilter; label: string }[] = [
  { value: 'ALL', label: '실발송+테스트' },
  { value: 'REAL', label: '실발송만' },
  { value: 'TEST', label: '테스트만' },
]

const columns = [
  { title: '발송일시', dataIndex: 'requestedAt', key: 'requestedAt', width: 140 },
  { title: '종류', key: 'type', width: 200 },
  { title: '공고 · 조건', key: 'posting', width: 240 },
  { title: '제목', dataIndex: 'title', key: 'title', ellipsis: true },
  { title: '메일', dataIndex: 'mail', key: 'mail', width: 80 },
  { title: 'SMS', dataIndex: 'sms', key: 'sms', width: 80 },
  { title: '대상', dataIndex: 'recipientCount', key: 'recipientCount', width: 70 },
  { title: '결과', key: 'result', width: 260 },
  { title: '발송자', dataIndex: 'senderName', key: 'senderName', width: 100 },
]

const route = useRoute()

const range = ref<[string, string]>(defaultHistoryRange(new Date()))
const typeFilter = ref<MessageType | undefined>(undefined)
const jobPostingId = ref<number | undefined>(undefined)
const testFilter = ref<TestFilter>('ALL')
const postings = ref<AdminJobPostingListItem[]>([])

const rows = ref<MessageSendSummary[]>([])
const page = ref(0)
const totalElements = ref(0)
const loading = ref(false)
/* 조회 버튼을 누른 시점의 조건. 페이지 이동·새로고침은 이 조건으로 다시 읽는다. */
const appliedFilter = ref<HistoryFilter>({})

const drawerOpen = ref(false)
const detailId = ref<number | null>(null)

const postingOptions = computed(() => postings.value.map((posting) => ({ value: posting.id, label: posting.title })))

const tableRows = computed(() =>
  rows.value.map((summary) => ({
    id: summary.id,
    requestedAt: formatDate(summary.requestedAt, 'YYYY-MM-DD HH:mm'),
    typeLabel: messageTypeLabel(summary.type),
    test: summary.test,
    jobPostingTitle: summary.jobPostingTitle,
    conditionSummary: summary.conditionSummary ?? '',
    title: summary.title ?? '',
    mail: channelCellText(summary.mailEnabled, summary.mail),
    sms: channelCellText(summary.smsEnabled, summary.sms),
    recipientCount: summary.recipientCount,
    status: sendStatusView(summary.status, summary.delayed),
    counts: resultCounts(summary.mail, summary.sms),
    senderName: summary.senderName ?? '',
  })),
)

type HistoryRow = (typeof tableRows.value)[number]

const pagination = computed(() => ({
  current: page.value + 1,
  pageSize: PAGE_SIZE,
  total: totalElements.value,
  showSizeChanger: false,
}))

const toFilter = (): HistoryFilter => ({
  from: range.value[0],
  to: range.value[1],
  type: typeFilter.value,
  jobPostingId: jobPostingId.value,
  test: testFilter.value === 'ALL' ? undefined : testFilter.value === 'TEST',
})

/* 늦게 도착한 목록 응답은 요청 번호로 버린다. */
let listRequest = 0

const loadList = async (): Promise<void> => {
  const request = ++listRequest
  loading.value = true
  try {
    const response = await messageApi.getHistory({ ...appliedFilter.value, page: page.value, size: PAGE_SIZE })
    if (request !== listRequest) return
    rows.value = response.data.data.content
    totalElements.value = response.data.data.totalElements
  } catch (error) {
    if (request !== listRequest) return
    message.error(getApiErrorMessage(error, '발송 이력을 불러오지 못했습니다.'))
  } finally {
    if (request === listRequest) {
      loading.value = false
    }
  }
}

const search = (): void => {
  appliedFilter.value = toFilter()
  page.value = 0
  void loadList()
}

const handleTableChange = (nextPagination: { current?: number }): void => {
  page.value = (nextPagination.current ?? 1) - 1
  void loadList()
}

const onRangeChange = (_: unknown, dateStrings: [string, string]): void => {
  if (dateStrings[0] && dateStrings[1]) {
    range.value = dateStrings
  }
}

const openDetail = (sendId: number): void => {
  detailId.value = sendId
  drawerOpen.value = true
}

onMounted(async () => {
  /* 발송 화면의 "이력 보기"는 ?sendId= 로 들어온다. 그 발송 상세를 바로 연다. */
  const sendId = Number(route.query.sendId)
  if (Number.isInteger(sendId) && sendId > 0) {
    openDetail(sendId)
  }
  search()
  try {
    postings.value = await getAllJobPostings()
  } catch (error) {
    message.error(getApiErrorMessage(error, '공고 목록을 불러오지 못했습니다.'))
  }
})
</script>

<template>
  <div class="message-history-view">
    <header class="page-header">
      <div>
        <h1 class="page-title">발송 이력</h1>
        <p class="page-desc">
          발송 요청별 대상·채널 건수와 결과를 봅니다. 결과는 솔루션에서 받는 대로 반영되며, 목록은 새로고침으로 갱신합니다.
        </p>
      </div>
    </header>

    <section class="filters">
      <a-range-picker :value="range" value-format="YYYY-MM-DD" :allow-clear="false" @change="onRangeChange" />
      <a-select v-model:value="typeFilter" class="type-select" :options="TYPE_OPTIONS" placeholder="종류 전체" allow-clear />
      <a-select
        v-model:value="jobPostingId"
        class="posting-select"
        :options="postingOptions"
        placeholder="공고 전체"
        allow-clear
        show-search
        option-filter-prop="label"
      />
      <a-radio-group v-model:value="testFilter" :options="TEST_FILTER_OPTIONS" option-type="button" />
      <a-button type="primary" @click="search"><SearchOutlined /> 조회</a-button>
      <a-button :loading="loading" @click="loadList"><ReloadOutlined /> 새로고침</a-button>
    </section>

    <a-table
      :columns="columns"
      :data-source="tableRows"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      :custom-row="(record: HistoryRow) => ({ onClick: () => openDetail(record.id) })"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'type'">
          {{ record.typeLabel }}
          <a-tag v-if="record.test" color="purple">테스트</a-tag>
        </template>
        <template v-else-if="column.key === 'posting'">
          <div>{{ record.jobPostingTitle }}</div>
          <div class="sub">{{ record.conditionSummary }}</div>
        </template>
        <template v-else-if="column.key === 'result'">
          <a-tag :color="record.status.color">{{ record.status.label }}</a-tag>
          <span class="sub">
            성공 {{ record.counts.sent }} · 실패 {{ record.counts.failed }} · 수신 중 {{ record.counts.inProgress }}
          </span>
        </template>
      </template>
    </a-table>

    <MessageHistoryDrawer v-model:open="drawerOpen" :send-id="detailId" />
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

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.type-select {
  width: 170px;
}

.posting-select {
  width: 280px;
}

.sub {
  font-size: 12px;
  color: var(--app-text-secondary);
}

:deep(.ant-table-row) {
  cursor: pointer;
}
</style>
```

- [ ] **Step 4: 확인**

Run(`recruit_front/`): `npm run type-check`
Expected: 오류 0. (`#bodyCell`의 `record`는 기존 `AdminJobPostingListView`처럼 느슨한 타입이라 캐스트 없이 쓴다. 오류가 나면 드로어처럼 `(record as HistoryRow)`로 바꾼다.)

Run: `npx eslint src/views/admin/message src/routes/adminRoutes.ts`
Expected: 0 problems.

- [ ] **Step 5: 커밋하지 않는다(사용자 지시)**

---

### Task 9: 발송 화면 연결(이력 버튼·발송 후 알림·테스트 결과 갱신)

**Files:**
- Modify: `{FE}/views/admin/message/AdminMessageSendView.vue`

테스트 발송 응답은 접수 결과다. `REQUESTED`가 있으면 이력 상세를 3초마다 최대 2분 다시 읽어 `testResults`를 바꾼다. 결과가 모두 오거나, 내용·종류가 바뀌거나(`testVersion`과 함께 폴링 번호를 올림), 새 테스트·발송을 하거나, 화면을 떠나면 멈춘다. `tested`는 `REQUESTED` 또는 `SENT`가 하나라도 있으면 true다(설계서 3.1-5).

- [ ] **Step 1: import**

아래 줄들을

```ts
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import axios from 'axios'
import { FileTextOutlined } from '@ant-design/icons-vue'
```

다음으로 바꾼다:

```ts
import { computed, h, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Button, message, notification } from 'ant-design-vue'
import axios from 'axios'
import { FileTextOutlined, HistoryOutlined } from '@ant-design/icons-vue'
```

`import { defaultStageId, selectablePostings, toTargetQuery, type MessageCondition } from './messageCondition'` 아래에 추가:

```ts
import { hasRequested, toTestResults } from './messageHistory'
```

- [ ] **Step 2: 테스트 결과 폴링**

아래 블록을

```ts
/* 내용이나 종류가 바뀌면 이전 테스트 발송은 지금 내용과 다르다. 진행 중이던 테스트 응답도 버린다. */
let testVersion = 0
watch(
  [content, type],
  () => {
    testVersion += 1
    tested.value = false
    testResults.value = []
  },
  { deep: true },
)
```

다음으로 바꾼다:

```ts
/* 테스트 발송 응답은 솔루션 접수 결과다. 결과 수신 중(REQUESTED)인 채널이 있으면 이력 상세를 3초마다 다시 읽어 최종 결과로 바꾼다(최대 2분, 설계서 3.1-5). */
const TEST_POLL_INTERVAL_MS = 3000
const TEST_POLL_LIMIT_MS = 120000

/* 새 테스트·내용 변경·발송·화면 이탈 때 번호를 올려 진행 중인 폴링과 늦은 응답을 버린다. */
let testPollRun = 0
let testPollTimer: ReturnType<typeof setTimeout> | null = null

const stopTestPolling = (): void => {
  testPollRun += 1
  if (testPollTimer !== null) {
    clearTimeout(testPollTimer)
    testPollTimer = null
  }
}

const refreshTestResults = async (sendId: number, run: number, deadline: number): Promise<void> => {
  try {
    const response = await messageApi.getHistoryDetail(sendId)
    if (run !== testPollRun) return
    testResults.value = toTestResults(response.data.data)
  } catch {
    /* 일시 오류는 다음 주기에 다시 읽는다. */
  }
  if (run === testPollRun && hasRequested(testResults.value) && Date.now() < deadline) {
    pollTestResults(sendId, run, deadline)
  }
}

const pollTestResults = (sendId: number, run: number, deadline: number): void => {
  testPollTimer = setTimeout(() => {
    testPollTimer = null
    void refreshTestResults(sendId, run, deadline)
  }, TEST_POLL_INTERVAL_MS)
}

/* 내용이나 종류가 바뀌면 이전 테스트 발송은 지금 내용과 다르다. 진행 중이던 테스트 응답·결과 폴링도 버린다. */
let testVersion = 0
watch(
  [content, type],
  () => {
    testVersion += 1
    stopTestPolling()
    tested.value = false
    testResults.value = []
  },
  { deep: true },
)
```

- [ ] **Step 3: runTest**

`const runTest = async (testers: MessageTester[]): Promise<void> => { ... }` 전체를 다음으로 바꾼다:

```ts
const runTest = async (testers: MessageTester[]): Promise<void> => {
  const query = loadedQuery.value
  const preview = previewRecipient.value
  if (!query || !preview) return
  stopTestPolling()
  const version = testVersion
  const run = testPollRun
  testSending.value = true
  try {
    const response = await messageApi.testSend({
      ...query,
      previewApplicationId: preview.applicationId,
      testers,
      content: toContentRequest(),
    })
    if (version !== testVersion) {
      message.warning('테스트 발송 중에 내용이 바뀌었습니다. 바뀐 내용으로 다시 테스트하세요.')
      return
    }
    const { sendId, results } = response.data.data
    testResults.value = results
    /* 솔루션이 접수한 채널이 하나라도 있으면 테스트한 것으로 본다. 최종 성공·실패는 폴링으로 갱신한다. */
    tested.value = results.some((result) => result.status === 'REQUESTED' || result.status === 'SENT')
    if (tested.value) {
      message.success('테스트 발송을 접수했습니다. 결과가 오면 아래에 표시합니다.')
    } else {
      message.warning('테스트 발송이 접수된 채널이 없습니다. 결과를 확인하세요.')
    }
    if (hasRequested(results)) {
      pollTestResults(sendId, run, Date.now() + TEST_POLL_LIMIT_MS)
    }
  } catch (error) {
    message.error(getApiErrorMessage(error, '테스트 발송에 실패했습니다.'))
  } finally {
    testSending.value = false
  }
}
```

- [ ] **Step 4: 발송 후 알림**

`const confirmSend = async (): Promise<void> => {` 바로 위에 추가:

```ts
/* 발송은 서버에서 비동기로 진행된다. 알림의 "이력 보기"로 그 발송 상세를 연다(설계서 3.1-9). */
const showSendAccepted = (sendId: number, recipientCount: number): void => {
  const key = `message-send-${sendId}`
  notification.success({
    key,
    message: '발송을 요청했습니다',
    description: `발송 번호 ${sendId}, ${recipientCount}명. 결과는 발송 이력에서 확인할 수 있습니다.`,
    duration: 8,
    btn: () =>
      h(
        Button,
        {
          type: 'primary',
          size: 'small',
          onClick: () => {
            notification.close(key)
            void router.push({ name: 'AdminMessageHistory', query: { sendId: String(sendId) } })
          },
        },
        { default: () => '이력 보기' },
      ),
  })
}
```

`confirmSend` 안의 아래 부분을

```ts
    const result = response.data.data
    confirmOpen.value = false
    tested.value = false
    testResults.value = []
    message.success(
      `발송을 요청했습니다(발송 번호 ${result.sendId}, ${result.recipientCount}명). 결과는 발송 이력에서 확인할 수 있습니다.`,
    )
```

다음으로 바꾼다(`excludedCount` 경고는 그대로 둔다):

```ts
    const result = response.data.data
    confirmOpen.value = false
    /* 발송하면 테스트 카드를 비운다. 진행 중인 테스트 응답·결과 폴링도 버린다. */
    testVersion += 1
    stopTestPolling()
    tested.value = false
    testResults.value = []
    showSendAccepted(result.sendId, result.recipientCount)
```

- [ ] **Step 5: 화면을 떠나면 폴링 중단**

`onMounted(async () => { ... })` 블록 바로 아래에 추가:

```ts
onBeforeUnmount(() => {
  stopTestPolling()
})
```

- [ ] **Step 6: 헤더 버튼**

템플릿의

```vue
      <a-button @click="router.push({ name: 'AdminMessageTemplates' })"><FileTextOutlined /> 템플릿 관리</a-button>
```

를 다음으로 바꾼다(기존 버튼과 같은 방식, 결정 15):

```vue
      <a-space>
        <a-button @click="router.push({ name: 'AdminMessageHistory' })"><HistoryOutlined /> 발송 이력</a-button>
        <a-button @click="router.push({ name: 'AdminMessageTemplates' })"><FileTextOutlined /> 템플릿 관리</a-button>
      </a-space>
```

- [ ] **Step 7: 확인**

Run(`recruit_front/`): `npm run type-check`
Expected: 오류 0. (`h(Button, ...)` 오버로드 오류가 나면 props 객체 타입이 아니라 slot 형태를 확인한다 — 위 코드는 `{ default: () => '이력 보기' }` slot 객체를 쓴다.)

Run: `npx vitest run src/views/admin/message`
Expected: 36 passed.

Run: `npx eslint src/views/admin/message src/routes/adminRoutes.ts src/api/admin/messageApi.ts src/types/admin/message.ts`
Expected: 0 problems.

- [ ] **Step 8: 커밋하지 않는다(사용자 지시)**

---

## 배치 E — 문서·최종 검증

### Task 10: 도메인 카드·색인·privacy-audit 갱신

**Files:**
- Modify: `docs/domains/message.md`, `docs/domains/_index.md`, `docs/domains/privacy-audit.md`

코드가 기준이다. 아래 문구는 이 계획의 코드 기준이므로, 구현 중 이름·메시지가 달라졌으면 코드에 맞춰 적는다. 카드는 40KB 상한(30KB 넘으면 보고).

- [ ] **Step 1: `docs/domains/message.md`**

(1) 머리 관련 카드 줄의 `[privacy-audit](privacy-audit.md) (수신자 파기, S4)` → `[privacy-audit](privacy-audit.md) (수신자 파기)`.

(2) `## 요약`의 세 줄을 다음으로 바꾼다:

```markdown
- 관리자가 지원자에게 메일·SMS를 보낸다. 종류 5개를 고르면 대상자와 문구가 자동으로 채워지고, 수신자별 미리보기 → 담당자 테스트 발송 → 실제 발송(비동기) 순서로 진행한다. 솔루션은 호출을 접수하면 거래 ID를 주고 실제 결과는 나중에 보낸다(메시지큐). 결과는 발송 이력에서 수신자별로 본다.
- 설계서: `docs/superpowers/specs/2026-09-19-message-send-design.md`. 구현은 S1 템플릿 → S2 대상자·작성 → S3 발송 → S4 결과 수신·이력 순서로 나눴다. **S4까지 구현 완료.** 실제 SMTP·문자 솔루션 구현체와 결과 수신 소켓 클라이언트는 범위 밖이다(목업 게이트웨이·목업 결과).
- 화면: `/admin/messages`(`AdminMessageSend`, 종류·조건·작성·미리보기·테스트 발송·발송) · `/admin/messages/history`(`AdminMessageHistory`, 발송 이력·상세) · `/admin/messages/templates`(`AdminMessageTemplates`).
```

(3) `## 용어` 표 끝에 4행 추가:

```markdown
| 채널 결과 | `MessageDeliveryStatus` | `PENDING` 호출 전 · `REQUESTED` 결과 수신 중(솔루션 접수) · `SENT` 성공 · `FAILED` 실패 · `SKIPPED` 제외 |
| 발송 상태 | `MessageSendStatus` | `SENDING` 발송 중 · `RESULT_PENDING` 결과 수신 중 · `COMPLETED` 완료. 저장하지 않고 조회할 때 계산 |
| 거래 ID | `transactionId` | 솔루션 호출 1회(발송 단위)에 솔루션이 준 ID. 결과 매칭의 유일한 키(`mailTransactionId`·`smsTransactionId`) |
| 발송 결과 | `DeliveryReport` | 나중에 오는 거래 1건의 결과(거래 ID + 결과코드). 그 거래 수신자 전원에 적용 |
```

(4) `## 파일 지도` → `### 백엔드`: 아래 행은 역할 칸을 바꾼다(파일 칸은 그대로).

```markdown
| service | `{BE}/service/MessageSendService.java` | 테스트 발송(동기 접수)·발송 접수(비동기 디스패치) |
| service | `{BE}/service/MessageDispatcher.java` | 발송 단위별 게이트웨이 호출·접수 기록, 기록 직후 먼저 온 결과 반영(`@Async`, 커밋 후. 테스트 발송은 동기 직접 호출) |
| service | `{BE}/service/MessageDispatchRecorder.java` | 단위 접수 결과 기록(`REQUESTED`+거래 ID / `FAILED`+사유)·발송 결과 반영(bulk update, 멱등). 트랜잭션 경계 |
| service | `{BE}/service/MessageDeliveryService.java` | 발송 단위 1개를 게이트웨이로 전달·접수 결과 정규화(DB 미접근) |
| service | `{BE}/service/GatewayResult.java` | 게이트웨이 접수 결과(접수 여부·거래 ID·실패 사유) |
| service | `{BE}/service/LoggingMailGateway.java` | 목업 메일 게이트웨이(기본값, 항상 접수·가짜 거래 ID·로그만) |
| service | `{BE}/service/LoggingSmsGateway.java` | 목업 문자 게이트웨이(기본값, 항상 접수·가짜 거래 ID·로그만) |
| entity | `{BE}/domain/entity/MessageSend.java` | 발송 요청 1회. 치환 전 원문·조건 요약·발송자(상태·건수는 저장하지 않음) |
| entity | `{BE}/domain/entity/MessageRecipient.java` | 발송 1회의 수신자 1명·채널별 결과·거래 ID. 이름·연락처 AES 암호화, `@DynamicUpdate` |
| repository | `{BE}/domain/repository/MessageSendRepository.java` | 발송 CRUD·이력 검색(기간·종류·공고·구분, 최신순) |
| repository | `{BE}/domain/repository/MessageRecipientRepository.java` | 수신자 CRUD·결과 반영 bulk update·거래 ID 존재 확인·발송별 상태 건수 |
| config | `{BE}/config/MessageProperties.java` | `recruit.message.*` 발신 정보·사이트 주소·최대 수신자·게이트웨이 선택·결과 대기 시간·성공 결과코드 |
| enum | `{BE}/enumeration/MessageSendStatus.java` | 발송 1회 상태 `SENDING`·`RESULT_PENDING`·`COMPLETED`(계산값, `of`) |
| enum | `{BE}/enumeration/MessageDeliveryStatus.java` | 수신자·채널별 결과 `PENDING`·`REQUESTED`·`SENT`·`FAILED`·`SKIPPED` |
| exception | `{BE}/exception/MessageSendNotFoundException.java` | 404(이력 상세) |
| test | `{BT}/service/MessageDeliveryServiceTest.java` | 게이트웨이 호출·접수 결과 정규화 |
| test | `{BT}/service/MessageDispatcherTest.java` | 발송 단위 디스패치·접수 직후 보관 결과 반영 |
| test | `{BT}/service/MessageDispatchRecorderTest.java` | 접수 기록·결과 반영(`REQUESTED`만, 성공 코드, 멱등) |
| test | `{BT}/service/MessageSendAsyncFlowTest.java` | 커밋 → 비동기 접수 → 목업 결과 → `COMPLETED` 계산 전체 경로(트랜잭션 없음, 로깅 게이트웨이) |
| test | `{BT}/controller/MessageSendCommandControllerTest.java` | 테스트 발송(접수 결과)·발송 접수 API |
```

행 추가(위치: controller 행 아래 / service 행 끝 / repository 행 끝 / dto 행 끝 / test 행 끝):

```markdown
| controller | `{BE}/controller/MessageHistoryAdminController.java` | 발송 이력 목록·상세 2개 |
| service | `{BE}/service/DeliveryReport.java` | 발송 결과 1건(거래 ID + 결과코드) |
| service | `{BE}/service/DeliveryReportHandler.java` | 결과 수신 처리부(보관 → 반영, 접수 기록 직후 반영, 1분 재시도·10분 폐기) |
| service | `{BE}/service/DeliveryReportBuffer.java` | 짝을 못 찾은 결과의 메모리 보관(서버 1대 전제) |
| service | `{BE}/service/MockDeliveryReportScheduler.java` | 목업 게이트웨이의 가짜 결과(3초 뒤, `recruit.message.gateway=logging`일 때만) |
| service | `{BE}/service/MessageHistoryService.java` | 이력 목록·상세, 상태·건수·지연 계산(읽기 전용) |
| repository | `{BE}/domain/repository/MessageRecipientStatusCount.java` | 상태 건수 projection(발송 id·메일 상태·SMS 상태·수) |
| dto | `{BE}/dto/condition/MessageHistoryCondition.java` | 이력 검색 조건 |
| dto | `{BE}/dto/response/MessageSendSummaryResponse.java` | 이력 목록 1행 |
| dto | `{BE}/dto/response/MessageSendDetailResponse.java` | 이력 상세(목록 필드 + 원문 + 수신자) |
| dto | `{BE}/dto/response/MessageChannelCountResponse.java` | 채널별 상태 건수 |
| dto | `{BE}/dto/response/MessageRecipientResponse.java` | 이력 상세 수신자 1명(연락처 원문) |
| test | `{BT}/service/DeliveryReportHandlerTest.java` | 결과 보관·반영·재시도·10분 폐기 |
| test | `{BT}/service/MockDeliveryReportSchedulerTest.java` | 목업 결과 3초·실패 코드 |
| test | `{BT}/service/MessageHistoryServiceTest.java` | 필터·정렬·건수·상태·지연·상세·파기된 수신자 |
| test | `{BT}/controller/MessageHistoryAdminControllerTest.java` | 이력 API·400·404 |
```

(5) `### 프론트`: 아래 행은 역할 칸을 바꾸고, 아래 새 행을 추가한다.

```markdown
| route | `{FE}/routes/adminRoutes.ts` | `AdminMessageSend`(`/admin/messages`) · `AdminMessageHistory`(`/admin/messages/history`) · `AdminMessageTemplates`(`/admin/messages/templates`) — 공유 파일 |
| api | `{FE}/api/admin/messageApi.ts` | 템플릿·변수·대상자·테스트 발송·발송·이력 API |
| view | `{FE}/views/admin/message/AdminMessageSendView.vue` | 발송 화면 조립·상태·테스트 결과 폴링·발송 후 이력 알림 |
| view | `{FE}/views/admin/message/MessageTestSendCard.vue` | 테스트 발송 카드(담당자 추가·최근 수신자 기억·접수/최종 결과 표시) |
```

```markdown
| view | `{FE}/views/admin/message/AdminMessageHistoryView.vue` | 발송 이력 목록(필터·서버 페이지) |
| view | `{FE}/views/admin/message/MessageHistoryDrawer.vue` | 발송 상세 드로어(원문·수신자별 결과, 완료 전 5초 새로고침) |
| util | `{FE}/views/admin/message/messageHistory.ts` | 상태·사유 라벨, 채널 칸·건수, 파기 표시, 테스트 결과 변환, 기본 기간 |
| test | `{FE}/views/admin/message/__tests__/messageHistory.spec.ts` | Vitest |
```

(6) `## API 계약` 표: test·send 행을 아래로 바꾸고, history 🟡 두 행을 아래 🟢 두 행으로 바꾼다.

```markdown
| 🟢 | POST | /admin/messages/test | `MessageTestSendRequest` `{ type, jobPostingId, stageId?, resultStatus?, interviewGroup?, applicationStatus?, previewApplicationId, testers[{ name, email?, phone? }](1~5명), content }` | `MessageTestSendResponse` `{ sendId, results[{ name, channel, status, failureReason }] }` — status는 접수 결과(`REQUESTED`·`FAILED`·`SKIPPED`) |
| 🟢 | POST | /admin/messages/send | `MessageSendRequest` `{ type, jobPostingId, stageId?, resultStatus?, interviewGroup?, applicationStatus?, applicationIds[], content }` | `MessageSendResultResponse` `{ sendId, status(항상 SENDING), recipientCount, excludedCount }` |
| 🟢 | GET | /admin/messages/history | query `from?, to?, type?, jobPostingId?, test?, page(0), size(20)` | `PageResponse<MessageSendSummaryResponse>` 발송일시 desc, id desc |
| 🟢 | GET | /admin/messages/history/{sendId} | 없음 | `MessageSendDetailResponse` |
```

(7) `### 엔드포인트 상세`:
- `- 인증: ...` 항목 끝 문장 `` `MessageSendNotFoundException`(404, "발송 기록을 찾을 수 없습니다.")은 발송 집계 확정 시 쓰는 내부 안전장치로, 정상 흐름에서는 발생하지 않는다. ``를 지운다.
- `- history(🟡) 행의 요청·응답 필드는 설계서 9절을 따른다. S4에서 구현하며 이 표를 🟢로 바꾼다.` 항목을 지운다.
- 끝에 추가:

```markdown
- test 응답 `results[].status`는 솔루션 접수 결과다(`REQUESTED` 접수 · `FAILED` 접수 실패 · `SKIPPED` 제외). 최종 결과는 발송 결과가 오면 history 상세에 반영된다. `failureReason`·`*FailureReason`: `NO_CONTACT`·`INVALID_CONTACT`·`CHANNEL_OFF`(제외), `GATEWAY_ERROR`(접수 실패), 그 밖은 솔루션 결과코드(발송 실패).
- history query: `from`·`to`는 `YYYY-MM-DD`(발송일, 양끝 포함). 비우면 `to` = 오늘, `from` = `to` − 29일(최근 30일). `test` 없음 = 실발송+테스트, `true` = 테스트만, `false` = 실발송만. `size` 1~100.
- `MessageSendSummaryResponse`: `{ id, requestedAt, type, test, jobPostingTitle, stageName, conditionSummary, title, mailEnabled, smsEnabled, recipientCount, mail{ pending, requested, sent, failed, skipped }, sms{ 〃 }, status, delayed, senderName }`. `title` = 메일을 켰으면 메일 제목, 아니면 SMS 원문 앞 40자. `status`·건수는 조회할 때 수신자 행으로 계산, `delayed` = 완료가 아니고 요청 후 `recruit.message.result-wait-minutes`(기본 60분)가 지남.
- `MessageSendDetailResponse`: 요약 필드 전부(같은 이름) + `{ templateName, mailSubject, mailBody, smsBody(치환 전 원문), recipients[{ id, applicationId(테스트 수신자 null), name, email, phone, mailStatus, mailFailureReason, smsStatus, smsFailureReason, smsKind }] }` 수신자 id 순. 연락처는 가리지 않는다. 파기된 수신자는 `name`·`email`·`phone`이 null.
- history 400: `page는 0 이상이어야 합니다.` · `size는 1 이상 100 이하여야 합니다.` · `조회 시작일이 종료일보다 늦습니다.`(날짜·enum 형식 오류는 공통 400). history 상세 404: `발송 기록을 찾을 수 없습니다.`(`MessageSendNotFoundException`).
```

(8) `## 규칙·불변식`:
- ``- 채널별 처리 상태(`MessageDeliveryStatus`): ...``로 시작하는 항목을 다음으로 바꾼다:

```markdown
- 채널별 상태(`MessageDeliveryStatus`): 채널을 껐으면 `SKIPPED`(`CHANNEL_OFF`), 연락처가 없으면 `SKIPPED`(`NO_CONTACT`), 형식이 틀리면 `SKIPPED`(`INVALID_CONTACT`). 보낼 채널은 `PENDING`으로 시작해 솔루션이 접수하면 `REQUESTED` + 거래 ID, 접수 실패면 `FAILED` + 사유가 된다. 사유는 게이트웨이 코드 200자까지, 호출 예외·`null` 결과·빈 사유·거래 ID 없음(또는 100자 초과)은 `GATEWAY_ERROR`(`MessageDeliveryService.deliver`). 발송 결과가 오면 `REQUESTED`인 채널만 `SENT` 또는 `FAILED`(사유 = 결과코드)로 바뀐다.
```

- 발송 단위 항목의 `한 단위의 호출 결과는 그 단위 수신자 전원에게 같이 적용한다` → `한 단위의 접수 결과와 나중에 오는 발송 결과는 그 단위 수신자 전원에게 같이 적용한다`.
- ``- 테스트 발송(`MessageSendService.testSend`)은 ...``로 시작하는 항목을 다음으로 바꾼다:

```markdown
- 테스트 발송(`MessageSendService.testSend`)은 요청 트랜잭션 안에서 동기로 게이트웨이를 호출한다. 메일 제목·SMS 앞에 `[테스트] `를 붙이고, 미리보기 중인 수신자(`previewApplicationId`)의 변수 값으로 치환한다(테스터 이름은 본문에 넣지 않는다). 테스터는 최대 5명. 응답은 접수 결과이고 디스패치 뒤 수신자를 다시 읽어 만든다. 화면은 `REQUESTED`가 있으면 이력 상세를 3초마다 최대 2분 다시 읽어 최종 결과로 바꾸고, 접수된 채널이 하나라도 있으면 테스트한 것으로 본다(`AdminMessageSendView.runTest`).
```

- 끝에 추가:

```markdown
- 발송 결과 수신(`DeliveryReportHandler.handle`): 결과를 메모리 버퍼(`DeliveryReportBuffer`)에 넣고 `MessageDispatchRecorder.applyReport`로 반영을 시도한다. 반영은 JPQL bulk update(거래 ID 일치 + `REQUESTED`인 행만, 메일·SMS 각각)라 다시 온 결과는 0행이다(멱등). 바뀐 행이 있거나 이미 기록된 거래면 버퍼에서 뺀다. 성공 판정 = 결과코드 ∈ `recruit.message.success-result-codes`(기본 `0000`).
- 먼저 도착한 결과: 디스패처가 단위 접수를 기록한 직후 `applyBuffered(거래 ID)`로 보관 결과를 반영한다. `retryBuffered`(`@Scheduled(fixedDelay = 60000)`)가 1분마다 다시 시도하고, 받은 지 10분이 지나도 짝이 없으면 거래 ID만 경고 로그로 남기고 버린다.
- 발송 상태·건수는 저장하지 않는다(`MessageSend`에 상태·집계 컬럼 없음). 조회할 때 수신자 채널 상태로 센다: `PENDING`이 있으면 `SENDING`, 없고 `REQUESTED`가 있으면 `RESULT_PENDING`, 둘 다 없으면 `COMPLETED`(`MessageSendStatus.of`). 지연(`delayed`) = 완료가 아니고 요청 후 `result-wait-minutes` 경과. 화면은 `RESULT_PENDING`이면 "결과 미수신", `SENDING`이면 "발송 중단"으로 보인다. DB 값은 바꾸지 않고 시간이 지났다고 실패로 처리하지 않는다.
- 이력 목록(`MessageHistoryService.search`): 기간·종류·공고·구분 필터, `requestedAt desc, id desc`, 건수는 페이지의 발송 id로 group by 쿼리 1개(`countStatusesByMessageSendIds`). 끈 채널은 건수가 아니라 `mailEnabled`·`smsEnabled`로 "제외" 표시. 상세 드로어는 완료가 아니면 열려 있는 동안 5초마다 다시 읽는다. 목록은 새로고침 버튼으로 갱신한다.
- 파기 연동: 지원서 파기 시 그 지원서의 `MessageRecipient` 이름·이메일·휴대폰(암호화 컬럼이라 null)과 `createdBy`·`updatedBy`를 null로 바꾼다. 채널 상태·거래 ID·실패 사유(결과코드)는 유지하고, 테스트 수신자(`jobApplication` null)는 대상이 아니다(`ApplicationPiiPurgeRepository.purgeMessageRecipients`, [privacy-audit](privacy-audit.md)). 화면은 지원서 수신자인데 이름·연락처가 모두 비었으면 "(파기됨)"으로 보인다.
- 목업 결과(`recruit.message.gateway=logging`, 기본): 목업 게이트웨이는 항상 접수(가짜 거래 ID = UUID)하고 `MockDeliveryReportScheduler`가 3초 뒤 결과를 넘긴다. 결과코드 `0000`, 그 단위 수신 연락처에 `fail`이 들어 있으면 `9999`.
- 발송 요청이 접수되면 화면은 알림의 "이력 보기"로 `/admin/messages/history?sendId={id}`를 열고, 이력 화면은 `sendId`가 있으면 그 상세 드로어를 바로 연다.
```

(9) `## 변경 레시피`:
- `**메뉴**` 항목을 다음으로 바꾼다:

```markdown
- **메뉴**: 코드가 아니라 메뉴 관리 화면(`/admin/menus`)에서 등록한다. 그룹 "메시지" 아래 "메시지 발송"(`/admin/messages`), "발송 이력"(`/admin/messages/history`), "메시지 템플릿"(`/admin/messages/templates`).
```

- 끝에 추가:

```markdown
- **실제 솔루션 연동**: `MailGateway`·`SmsGateway` 구현체를 `@ConditionalOnProperty(prefix = "recruit.message", name = "gateway", havingValue = "<값>")`로 추가한다(접수 시 `GatewayResult.accepted(거래 ID)`, 거절 시 주소·번호 없는 사유 코드). 결과 소켓 클라이언트는 받은 메시지를 `DeliveryReport(거래 ID, 결과코드)`로 바꿔 `DeliveryReportHandler.handle`만 부른다. 성공 코드는 `RECRUIT_MESSAGE_SUCCESS_RESULT_CODES`(쉼표 구분). `gateway`가 `logging`이 아니면 목업 게이트웨이와 `MockDeliveryReportScheduler`는 뜨지 않는다.
```

(10) `## 검증`의 코드 블록을 다음으로 바꾼다:

```bash
# 백엔드 (recruit_back/recruit_backend/)
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.Message*" --tests "com.shinyoung.recruit.service.Delivery*" --tests "com.shinyoung.recruit.service.MockDeliveryReport*" --tests "com.shinyoung.recruit.service.ApplicationPiiPurge*" --tests "com.shinyoung.recruit.controller.Message*" --tests "com.shinyoung.recruit.config.ApplicationYamlTest" --tests "com.shinyoung.recruit.config.AsyncConfigTest" --tests "com.shinyoung.recruit.config.SecurityConfigTest" --no-daemon
# 프론트 (recruit_front/)
npm run type-check
npx vitest run src/views/admin/message
```

(11) `## 함정·결정`:
- ``- 발송 접수는 커밋 후 `MessageSendRequestedEvent`를 `@Async`로 처리한다.``로 시작하는 항목을 다음으로 바꾼다:

```markdown
- 발송 접수는 커밋 후 `MessageSendRequestedEvent`를 `@Async`로 처리한다. 치환된 내용은 이 이벤트(메모리)로만 디스패처에 넘기고 DB에는 원문만 남기므로, 접수 직후 서버가 내려가면 처리되지 못한 채널은 `PENDING`으로 남아 발송 상태가 `SENDING`으로 계산되고 60분 뒤 "발송 중단"으로 보인다. 자동 재개는 하지 않는다(범위 밖).
```

- `` - `MessageDispatchRecorder`는 기본 전파(`REQUIRED`)다. ``로 시작하는 항목을 다음으로 바꾼다:

```markdown
- `MessageDispatchRecorder`는 기본 전파(`REQUIRED`)이고 트랜잭션은 이 빈에만 둔다(`DeliveryReportHandler`는 트랜잭션 없음, 자기 호출 회피). 비동기 디스패처·결과 처리부는 호출마다 새 트랜잭션을 열지만, 테스트 발송(동기 호출)은 요청 트랜잭션에 그대로 합류한다.
```

- `` - `MessageSendServiceTest`는 ``로 시작하는 항목의 ``커밋 → 비동기 디스패치 → `COMPLETED` 전체 경로는`` → ``커밋 → 비동기 접수 → 목업 결과 → `COMPLETED` 계산 전체 경로는``.
- `- 수신자 연락처 파기 연동은 S4(privacy-audit 카드)에서 다룬다.` 항목을 지운다.
- 끝에 추가:

```markdown
- S4에서 `MessageSend`의 `status`·채널별 집계 6개·`completedAt`을 없앴다. 결과가 거래마다 나중에 여러 스레드에서 오므로 저장된 집계를 고치지 않고 조회할 때 센다. S3 코드로 만든 로컬 H2 DB에는 이 NOT NULL 컬럼이 남고, `@Enumerated(STRING)` 컬럼의 네이티브 `enum` 타입(Hibernate 7 H2·MariaDB 방언)에 `REQUESTED`가 없어 저장이 실패한다. `ddl-auto: update`는 둘 다 고치지 않으므로 로컬은 `message_recipient`·`message_send`를 지우고 다시 만든다. 운영 테이블 생성 SQL(설계서 17절 1번)은 S4 엔티티 기준으로 작성해야 한다.
- 결과 반영은 JPQL bulk update라 엔티티 감사(`updatedAt`)를 거치지 않고 `processedAt`만 갱신한다. `clearAutomatically = true`라 같은 트랜잭션(테스트 발송)에서 반영이 일어나면 영속성 컨텍스트가 비워진다. 그래서 `testSend`는 디스패치 뒤 수신자를 다시 읽어 응답을 만든다.
- `MessageRecipient`는 `@DynamicUpdate`다. SMS 단위 접수 기록(엔티티 변경)이 같은 수신자의 메일 결과 반영(bulk update)과 겹쳐도 바뀐 컬럼만 update해서 메일 결과를 옛 값으로 덮어쓰지 않는다.
- 먼저 도착한 결과 버퍼는 메모리(`ConcurrentHashMap`)다. 서버 1대 전제이고 재시작하면 보관 중인 결과는 사라진다(솔루션은 같은 결과를 다시 보내지 않으므로 그 수신자는 `REQUESTED`로 남아 "결과 미수신").
- 목업 결과와 1분 재시도는 부트 기본 `taskScheduler`(스레드 1개, `@EnableScheduling`이 있을 때 자동 생성)를 `ClientEventLogCleanupScheduler`와 함께 쓴다. 별도 `TaskScheduler` 빈을 만들면 기본 스케줄러가 빠진다. `@Transactional` 테스트에서 테스트 발송이 예약한 목업 결과는 롤백된 행을 못 찾아 버퍼에 남았다가 10분 뒤 버려진다.
- 결과코드별 설명은 스펙 확정 전이라 화면에 `결과코드 {코드}`로 보인다(설계서 17절 6번). 수신자별 거래 ID는 미확인이라 거래 단위 결과를 단위 전원에 적용한다(17절 7번). 목업 결과코드(`0000`·`9999`)는 성공 코드 설정과 무관하게 고정이다.
- 프론트: 이력 기간은 `a-range-picker`의 `value-format="YYYY-MM-DD"` 문자열만 쓴다(`dayjs`는 `package.json` 직접 의존이 아니라 가져오지 않는다). 테스트 결과 폴링·상세 드로어 새로고침은 `setTimeout` 연쇄로 하고 요청 번호로 늦은 응답을 버린다.
```

- [ ] **Step 2: `docs/domains/_index.md`**

- 라우트 표의 ``| `AdminMessageSend` · `AdminMessageTemplates` | `/admin/messages` · `/admin/messages/templates` | message |`` 행을 다음으로 바꾼다:

```markdown
| `AdminMessageSend` · `AdminMessageHistory` · `AdminMessageTemplates` | `/admin/messages` · `/admin/messages/history` · `/admin/messages/templates` | message |
```

- 키워드 행 `| 메일, SMS, 문자, LMS, 메시지 발송, ... , 발송 단위, 게이트웨이 | message |`의 끝에 `, 발송 결과, 결과 수신, 거래 ID`를 덧붙인다(`발송 이력`은 이미 있다). API 접두 행 `/admin/messages`는 이력도 덮으므로 그대로 둔다. `_index.md`는 12KB 상한이다.

- [ ] **Step 3: `docs/domains/privacy-audit.md`**

`## 규칙·불변식`의 **파기 범위** 표에서 ``| `Applicant`(ref-count 0) | ...`` 행 바로 위에 추가(파일 지도는 건드리지 않는다):

```markdown
| `MessageRecipient`([message](message.md)) | 이름·이메일·휴대폰 null(AES 컬럼이라 ph 불가). 채널 상태·거래 ID·실패 사유(결과코드) 유지. 테스트 수신자(`jobApplication` null)는 대상 아님 |
```

- [ ] **Step 4: 문서 점검**

Run(레포 루트): `node tools/check-docs.mjs`
Expected: `문서 점검 통과: 경고 N건`, 오류 0건. 새 컨트롤러 `MessageHistoryAdminController.java`와 새 화면 `AdminMessageHistoryView.vue`·`MessageHistoryDrawer.vue`가 message 카드 한 곳에만 등록돼 있어야 한다. `message.md`·`privacy-audit.md`의 30KB 초과 경고는 예상된 것이며(40KB 이하면 통과) 크기를 보고에 적는다. 40KB를 넘으면 `## 함정·결정`의 S3 항목 중 중복 설명을 줄인다(하위 카드 분할은 사용자 확인 후).

- [ ] **Step 5: 커밋하지 않는다(사용자 지시)**

---

### Task 11: S4 최종 검증·보고·구현 보고서

**Files:**
- Create: `docs/archive/reports/message-send-s4_implementation.html` (`design-report` 스킬)

- [ ] **Step 1: 백엔드 변경 범위 테스트**

Run(`recruit_back/recruit_backend/`): `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.Message*" --tests "com.shinyoung.recruit.service.Delivery*" --tests "com.shinyoung.recruit.service.MockDeliveryReport*" --tests "com.shinyoung.recruit.service.ApplicationPiiPurge*" --tests "com.shinyoung.recruit.controller.Message*" --tests "com.shinyoung.recruit.config.ApplicationYamlTest" --tests "com.shinyoung.recruit.config.SecurityConfigTest" --tests "com.shinyoung.recruit.config.AsyncConfigTest" --no-daemon`
Expected: `BUILD SUCCESSFUL`. (결정 18: 지정 명령에 `service.MockDeliveryReport*`를 더했다.)

- [ ] **Step 2: 프론트**

Run(`recruit_front/`):
- `npm run type-check` → 오류 0
- `npx vitest run src/views/admin/message` → 4 files, 36 passed
- `npx eslint src/views/admin/message src/routes/adminRoutes.ts src/api/admin/messageApi.ts src/types/admin/message.ts` → 0 problems
- `npm run build` → 성공

- [ ] **Step 3: 문서**

Run(레포 루트): `node tools/check-docs.mjs` → 오류 0건.

- [ ] **Step 4: 보고(한국어, 백엔드 AGENTS.md 12절 형식)**

변경 요약·변경 파일·테스트 결과(명령과 결과)·계약·카드 변경(history 2개 🟢, test 응답 의미 `REQUESTED`)을 적는다. 주의 사항에 다음을 반드시 넣는다:
- 로컬 H2 파일 DB 재생성 필요(Task 1 Step 9 문구). 에이전트가 실행하지 않았음.
- 설계서 대비 추가·해석: 수신자 `id`·`applicationId`(결정 11), 거래 ID 100자 초과 → `GATEWAY_ERROR`(결정 5), `@DynamicUpdate`(결정 2), 테스트 발송 응답을 다시 읽어 만듦(결정 3), 카드의 `test?(ALL·REAL·TEST)`를 Boolean으로 정정(결정 12).
- 화면 수동 확인 여부(백엔드 로그인에 LDAP가 필요하면 Task 12 목업으로 확인했는지).
- 설계서·계획 문서의 `docs/archive/superpowers/` 이동은 사용자 확인 후(카드의 설계서 경로도 함께 고쳐야 `check-docs`가 통과한다).

- [ ] **Step 5: 구현 보고서**

Skill 도구로 `design-report` 스킬을 호출해 S4 구현 보고서를 `docs/archive/reports/message-send-s4_implementation.html`에 만든다. 스킬 지침을 그대로 따른다(구조·스타일을 손으로 만들지 않는다). 내용: 목표, 결정 1~18 요약, 변경 파일(배치별), API 계약(history 2개·test 응답 의미), 결과 수신 흐름(접수 → 보관 → 반영 → 계산), 테스트 결과(Step 1~3의 명령·통과 수), 남은 이슈(설계서 17절 1·3·4·6·7번, 로컬 DB 재생성, 실제 솔루션·소켓 클라이언트). 스킬을 쓸 수 없으면 보고서를 만들지 말고 "HTML 보고서 산출 차단(사유)"을 보고한다.

- [ ] **Step 6: 커밋하지 않는다(사용자 지시)**

---

## 배치 F — 로컬 목업(git 제외, 검증 대상 아님)

### Task 12: 브라우저 데모용 목업 API 갱신

**Files:**
- Modify: `recruit_front/.claude/mock/mockApi.ts`, `recruit_front/.claude/mock/README.md`

`recruit_front/.gitignore`의 `.claude/`로 git에서 제외되는 로컬 전용 파일이다. 저장소 산출물이 아니고 Task 11 검증 대상도 아니다. 목적: 백엔드(LDAP) 없이 브라우저에서 테스트 발송·발송이 `REQUESTED` → 약 3초 뒤 결과로 바뀌는 흐름과 발송 이력 목록·상세를 볼 수 있게 한다. 먼저 `README.md`와 `mockApi.ts`의 현재 구조(ROUTES 배열, `handleTestSend`·`handleSend`, `MENU_TREE`)를 읽는다.

- [ ] **Step 1: 타입 import**

`mockApi.ts` 맨 위 `import type { ... } from '@/types/admin/message'`를 다음으로 바꾼다(`MessageChannel`은 아래에서 `toResultItem`을 지우면 쓰지 않는다):

```ts
import type {
  MessageChannelCount,
  MessageContentRequest,
  MessageDeliveryStatus,
  MessageHistoryRecipient,
  MessageSendDetail,
  MessageSendRequest,
  MessageSendResult,
  MessageSendStatus,
  MessageSendSummary,
  MessageTargetRecipient,
  MessageTargetResponse,
  MessageTemplate,
  MessageTemplateSaveRequest,
  MessageTester,
  MessageTestSendRequest,
  MessageTestSendResponse,
  MessageTestSendResultItem,
  MessageType,
} from '@/types/admin/message'
```

- [ ] **Step 2: 발송 기록·목업 결과·이력 처리 추가**

`/* ---------- 템플릿 CRUD(MessageTemplateService 이식) ---------- */` 줄 바로 위에 추가:

```ts
/* ---------- 발송 기록·목업 결과·이력(S4: MessageDispatchRecorder·MockDeliveryReportScheduler·MessageHistoryService 흉내) ---------- */

const RESULT_DELAY_MS = 3000
const SMS_TITLE_LENGTH = 40

interface MockSendLog {
  id: number
  requestedAt: string
  type: MessageType
  test: boolean
  jobPostingId: number
  jobPostingTitle: string
  stageName: string | null
  conditionSummary: string
  templateName: string | null
  mailEnabled: boolean
  smsEnabled: boolean
  mailSubject: string | null
  mailBody: string | null
  smsBody: string | null
  recipients: MessageHistoryRecipient[]
}

interface LogRecipientInput {
  applicationId: number | null
  name: string
  email: string | null
  phone: string | null
  plan: DeliveryPlan
}

const SEND_LOG: MockSendLog[] = []
let nextRecipientId = 1

const pad2 = (value: number): string => String(value).padStart(2, '0')

/** 이력 화면의 기간(브라우저 로컬 날짜)과 맞추려고 로컬 시각 문자열로 만든다. */
const localNowIso = (): string => {
  const d = new Date()
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}T${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
}

const containsFail = (value: string | null): boolean => (value ?? '').toLowerCase().includes('fail')

/** 목업 결과: 3초 뒤 REQUESTED 인 채널을 SENT, 연락처에 fail 이 있으면 FAILED(결과코드 9999)로 바꾼다. */
const scheduleMockResults = (recipients: MessageHistoryRecipient[]): void => {
  setTimeout(() => {
    for (const recipient of recipients) {
      if (recipient.mailStatus === 'REQUESTED') {
        const failed = containsFail(recipient.email)
        recipient.mailStatus = failed ? 'FAILED' : 'SENT'
        recipient.mailFailureReason = failed ? '9999' : null
      }
      if (recipient.smsStatus === 'REQUESTED') {
        const failed = containsFail(recipient.phone)
        recipient.smsStatus = failed ? 'FAILED' : 'SENT'
        recipient.smsFailureReason = failed ? '9999' : null
      }
    }
  }, RESULT_DELAY_MS)
}

/** 보낼 채널은 곧바로 접수(REQUESTED)로 기록하고 목업 결과를 예약한다. */
const recordSend = (
  body: MessageTestSendRequest | MessageSendRequest,
  sendId: number,
  test: boolean,
  inputs: LogRecipientInput[],
): MessageHistoryRecipient[] => {
  const posting = findPosting(body.jobPostingId)
  const stage = body.stageId == null ? null : (STAGES.find((s) => s.id === body.stageId) ?? null)
  const recipients: MessageHistoryRecipient[] = inputs.map((input) => ({
    id: nextRecipientId++,
    applicationId: input.applicationId,
    name: input.name,
    email: input.email,
    phone: input.phone,
    mailStatus: input.plan.mailStatus === 'PENDING' ? 'REQUESTED' : input.plan.mailStatus,
    mailFailureReason: input.plan.mailReason,
    smsStatus: input.plan.smsStatus === 'PENDING' ? 'REQUESTED' : input.plan.smsStatus,
    smsFailureReason: input.plan.smsReason,
    smsKind: null,
  }))
  const mail = body.content.mailEnabled === true
  const sms = body.content.smsEnabled === true
  SEND_LOG.push({
    id: sendId,
    requestedAt: localNowIso(),
    type: body.type,
    test,
    jobPostingId: posting.id,
    jobPostingTitle: posting.title,
    stageName: stage ? stage.stageName : null,
    conditionSummary: stage ? stage.stageName : '조건 요약(목업)',
    templateName: templates.find((t) => t.id === body.content.templateId)?.name ?? null,
    mailEnabled: mail,
    smsEnabled: sms,
    mailSubject: mail ? body.content.mailSubject : null,
    mailBody: mail ? body.content.mailBody : null,
    smsBody: sms ? body.content.smsBody : null,
    recipients,
  })
  scheduleMockResults(recipients)
  return recipients
}

const STATUS_KEY: Record<MessageDeliveryStatus, keyof MessageChannelCount> = {
  PENDING: 'pending',
  REQUESTED: 'requested',
  SENT: 'sent',
  FAILED: 'failed',
  SKIPPED: 'skipped',
}

const countChannel = (statuses: MessageDeliveryStatus[]): MessageChannelCount => {
  const count: MessageChannelCount = { pending: 0, requested: 0, sent: 0, failed: 0, skipped: 0 }
  for (const status of statuses) count[STATUS_KEY[status]] += 1
  return count
}

const toSummary = (log: MockSendLog): MessageSendSummary => {
  const mail = countChannel(log.recipients.map((r) => r.mailStatus))
  const sms = countChannel(log.recipients.map((r) => r.smsStatus))
  const status: MessageSendStatus =
    mail.pending + sms.pending > 0 ? 'SENDING' : mail.requested + sms.requested > 0 ? 'RESULT_PENDING' : 'COMPLETED'
  return {
    id: log.id,
    requestedAt: log.requestedAt,
    type: log.type,
    test: log.test,
    jobPostingTitle: log.jobPostingTitle,
    stageName: log.stageName,
    conditionSummary: log.conditionSummary,
    title: log.mailEnabled ? log.mailSubject : (log.smsBody ?? '').slice(0, SMS_TITLE_LENGTH),
    mailEnabled: log.mailEnabled,
    smsEnabled: log.smsEnabled,
    recipientCount: log.recipients.length,
    mail,
    sms,
    status,
    delayed: false,
    senderName: ADMIN_USER.name,
  }
}

const listHistory = (query: URLSearchParams): PageResponse<MessageSendSummary> => {
  const from = query.get('from')
  const to = query.get('to')
  const type = query.get('type')
  const jobPostingId = query.get('jobPostingId')
  const test = query.get('test')
  const page = Number(query.get('page') ?? '0')
  const size = Number(query.get('size') ?? '20')
  const filtered = SEND_LOG.filter((log) => {
    const day = log.requestedAt.slice(0, 10)
    return (
      (!from || day >= from) &&
      (!to || day <= to) &&
      (!type || log.type === type) &&
      (!jobPostingId || log.jobPostingId === Number(jobPostingId)) &&
      (test === null || String(log.test) === test)
    )
  }).reverse()
  const totalPages = Math.max(1, Math.ceil(filtered.length / size))
  return {
    content: filtered.slice(page * size, page * size + size).map(toSummary),
    page,
    size,
    totalElements: filtered.length,
    totalPages,
    first: page === 0,
    last: page >= totalPages - 1,
  }
}

const historyDetail = (sendId: number): MessageSendDetail => {
  const log = SEND_LOG.find((item) => item.id === sendId)
  if (!log) throw new ApiError(404, '발송 기록을 찾을 수 없습니다.')
  return {
    ...toSummary(log),
    templateName: log.templateName,
    mailSubject: log.mailSubject,
    mailBody: log.mailBody,
    smsBody: log.smsBody,
    recipients: log.recipients.map((recipient) => ({ ...recipient })),
  }
}
```

- [ ] **Step 3: 테스트 발송·발송이 기록을 남기게 바꾸기**

(1) `/** 결과를 결정적으로 보여 주기 위한 데모 규칙: ... */` 주석과 `const toResultItem = (...) => { ... }` 함수를 지운다.

(2) `handleTestSend`의 `const sendId = takeNextSendId()`부터 `body.testers.forEach(...)` 블록 끝(`})`)까지를 다음으로 바꾼다(아래 `console.log`·`return { sendId, results }`는 그대로):

```ts
  const sendId = takeNextSendId()
  const logged = recordSend(
    body,
    sendId,
    true,
    body.testers.map((tester, index) => ({
      applicationId: null,
      name: tester.name,
      email: tester.email,
      phone: tester.phone,
      plan: plans[index] as DeliveryPlan,
    })),
  )
  const results: MessageTestSendResultItem[] = logged.flatMap((recipient): MessageTestSendResultItem[] => [
    { name: recipient.name ?? '', channel: 'MAIL', status: recipient.mailStatus, failureReason: recipient.mailFailureReason },
    { name: recipient.name ?? '', channel: 'SMS', status: recipient.smsStatus, failureReason: recipient.smsFailureReason },
  ])
```

(3) `handleSend`의 `const sendId = takeNextSendId()` 바로 아래에 추가:

```ts
  recordSend(
    body,
    sendId,
    false,
    recipients.map((recipient, index) => ({
      applicationId: recipient.applicationId,
      name: recipient.name ?? '',
      email: recipient.email,
      phone: recipient.phone,
      plan: plans[index] as DeliveryPlan,
    })),
  )
```

- [ ] **Step 4: 메뉴·라우트**

`MENU_TREE`의 "메시지" 그룹 `children`에서 `메시지 발송`(id 21) 항목 아래에 추가하고, 기존 `메시지 템플릿`(id 22)의 `sortOrder`를 3으로 바꾼다:

```ts
      { id: 23, parentId: 2, site: 'ADMIN', type: 'ROUTE', name: '발송 이력', path: '/admin/messages/history', sortOrder: 2, icon: 'HistoryOutlined', children: [] },
```

`ROUTES` 배열에서 `/^\/admin\/messages\/send$/` 항목 바로 아래에 추가:

```ts
  {
    method: 'GET',
    pattern: /^\/admin\/messages\/history$/,
    handler: ({ query }) => success(listHistory(query), randomDelay(200, 400)),
  },
  {
    method: 'GET',
    pattern: /^\/admin\/messages\/history\/(\d+)$/,
    handler: ({ match }) => success(historyDetail(Number(match[1])), randomDelay(100, 200)),
  },
```

- [ ] **Step 5: README 갱신**

`README.md`:
- `## 무엇을 목업했나` 목록의 `GET /admin/messages/variables ...` 줄 아래에 ``- `GET /admin/messages/history`(기간·종류·공고·구분 필터, 페이지), `GET /admin/messages/history/{sendId}` — 서버를 다시 시작하면 이력이 비는 메모리 기록``을 추가하고, `GET /menu/tree` 설명의 "발송·템플릿 메뉴"를 "발송·발송 이력·템플릿 메뉴"로 고친다.
- `## 테스트 발송 결과를 결정적으로 보기` 절의 규칙 두 번째 항목을 다음으로 바꾼다: "보낼 채널은 먼저 `REQUESTED`(결과 수신 중)로 응답하고, 약 3초 뒤 `SENT`로 바뀐다. 연락처(이메일/휴대폰)에 `fail`이 들어 있으면 `FAILED`(결과코드 `9999`)로 바뀐다. 테스트 카드는 이력 상세를 3초마다 다시 읽어 바뀐 결과를 보여 준다." 그리고 `POST /admin/messages/send` 문단 끝에 "접수된 발송은 발송 이력에 쌓이고 같은 규칙으로 3초 뒤 결과가 반영된다."를 덧붙인다.

- [ ] **Step 6: 로컬 확인(선택, 검증 대상 아님)**

Run(레포 루트): `node recruit_front/node_modules/vite/bin/vite.js recruit_front --config recruit_front/.claude/mock/vite.mock.config.ts` 후 `http://localhost:5174/admin/messages`에서 테스트 발송 → 카드가 "결과 수신 중" → 약 3초 뒤 "성공"(`fail@example.com`이면 "실패 (결과코드 9999)"), 발송 → 알림 "이력 보기" → 이력 드로어가 열리고 5초 새로고침 뒤 완료로 바뀌는지 본다. `npx eslint .claude/mock`(`recruit_front/`)으로 목업 파일 경고가 없는지 본다. 결과는 참고로만 보고한다.

- [ ] **Step 7: 커밋하지 않는다(사용자 지시, 이 파일들은 git 대상도 아니다)**

---

## 자체 검토 (계획 작성 시 수행)

1. **설계서 대응**: 2절 용어(`REQUESTED`·`MessageSendStatus` 계산·거래 ID·`DeliveryReport`) → Task 1·2·4. 3.1-5 테스트 카드 폴링·`tested` 판정 → Task 9. 3.1-9 발송 후 이력 링크 → Task 9·8(`?sendId`). 3.2 이력 목록·상세·5초 새로고침·"(파기됨)"·끈 채널 "제외" → Task 7·8. 7.1 접수 기록·기록 직후 보관 결과 반영 → Task 1·2. 7.2 테스트 응답 = 접수 결과 → Task 1. 7.3 거래 ID 필수·빈 사유 `GATEWAY_ERROR` → Task 1. 7.4 결과 반영 멱등·성공 코드·버퍼·1분 재시도·10분 폐기·목업 3초·`fail` 규칙·상태 계산·지연 → Task 2·3·4. 8절 `MessageSend` 컬럼 제거·`MessageRecipient` 거래 ID 인덱스 → Task 1. 9절 history 2개 → Task 4·5. 10절 설정 키 → Task 2. 12절 파기·연락처 비마스킹·401/403 → Task 6·4·5. 13절 오류(404·400) → Task 4·5. 14절 테스트 계획 → 각 Task 테스트(`DeliveryReportHandlerTest`의 DB 쪽 항목은 `MessageDispatchRecorderTest`로 나눴다). 15절 문서 → Task 10. 16절 S4 완료 기준(목업 결과로 수신 중 → 완료, 이력 조회, 파기 테스트, `check-docs`) → Task 4(비동기 흐름)·5·6·10·11.
2. **자리표시자**: 코드 단계는 전부 전체 코드 또는 정확한 교체 전후 문구를 적었다. "비슷하게"·TODO·TBD 없음. 문서 Task는 S3 계획과 같이 교체 문구를 그대로 적었다.
3. **이름·시그니처 일관성**: `GatewayResult.accepted(String)`/`accepted()`/`transactionId()`/`failure(String)`; `MessageRecipient.recordRequested(MessageChannel, String, LocalDateTime)`·`recordResult(...)`; `MessageDispatcher(MessageDeliveryService, MessageDispatchRecorder, DeliveryReportHandler)`·`dispatch(List<DeliveryItem>)`; `MessageDispatchRecorder.recordUnit(DeliveryUnit, GatewayResult)`·`applyReport(DeliveryReport)`; `DeliveryReportHandler(MessageDispatchRecorder, DeliveryReportBuffer, Clock)`·`handle`·`applyBuffered(String)`·`retryBuffered()`; `DeliveryReportBuffer(Clock)`·`put`·`find`·`remove`·`entries`·`size`; `MockDeliveryReportScheduler(TaskScheduler, DeliveryReportHandler, Clock)`·`schedule(String, List<String>)`; 리포지토리 `applyMailReport`·`applySmsReport`·`existsByMailTransactionIdOrSmsTransactionId`·`countStatusesByMessageSendIds`·`search`; `MessageHistoryService.search(MessageHistoryCondition, int, int)`·`detail(Long)`; `MessageSendStatus.of(long, long)`; 프론트 `messageApi.getHistory`·`getHistoryDetail`, 유틸 export 12개(스펙 import 목록과 일치). 테스트의 생성자 인자 순서는 `@RequiredArgsConstructor` 필드 선언 순서와 같다.
4. **각 Task 끝의 컴파일·통과**: `GatewayResult`·`dispatch` 시그니처 변경은 Task 1 한 곳에서 모든 호출부·테스트를 고친다. `MessageDispatcher` 생성자 변경(Task 2)은 `MessageDispatcherTest`를 같은 Task에서 고친다. 비동기 흐름 테스트는 Task 1(접수까지)·Task 3(목업 결과까지)·Task 4(이력 상세로 완료)로 단계마다 맞춘다. 프론트 `MessageDeliveryStatus` 확장으로 깨지는 `MessageTestSendCard`는 Task 7에서 같이 고친다.
5. **범위**: 새 의존성·새 패키지·새 예외·`SecurityConfig` 변경 없음. 커밋 단계 없음. 로컬 DB SQL은 안내만 한다.
