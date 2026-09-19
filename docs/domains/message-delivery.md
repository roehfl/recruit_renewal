# 메시지 발송 처리·결과 수신·발송 이력 (`message-delivery`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [message](message.md) (템플릿·대상자·작성·테스트 발송·발송 접수) · [privacy-audit](privacy-audit.md) (수신자 파기) · [client-event-log](client-event-log.md) (스케줄러 공유)

## 요약

- [message](message.md)의 발송 접수가 커밋되면 비동기로 발송 단위마다 게이트웨이(발송 솔루션)를 호출하고 접수 결과(거래 ID)를 수신자에 기록한다. 솔루션은 실제 발송 결과를 나중에 메시지큐로 보내고, 거래 ID로 수신자에 반영한다. 발송 상태·건수는 저장하지 않고 조회할 때 계산해 발송 이력에 보인다.
- 설계서 7.1·7.3·7.4절(`docs/archive/superpowers/specs/2026-09-19-message-send-design.md`). S3(디스패치)·S4(결과 수신·이력) 구현 완료. 실제 SMTP·문자 솔루션 구현체와 결과 수신 소켓 클라이언트는 범위 밖이다(목업 게이트웨이·목업 결과).
- 화면: `/admin/messages/history`(`AdminMessageHistory`, 목록·상세 드로어). 발송 화면의 테스트 발송 카드도 이력 상세 API로 최종 결과를 갱신한다([message](message.md)).
- 경계: 발송 접수(검증·수신자 행 생성·이벤트 발행)·테스트 발송은 `MessageSendService` → [message](message.md). 프론트 `{FE}/api/admin/messageApi.ts`·`{FE}/types/admin/message.ts`는 [message](message.md) 소유(이력 API·타입 포함). 연락처 마스킹 함수 `MessageContacts` → [message](message.md). 지원서 파기 → [privacy-audit](privacy-audit.md).

## 용어

| 용어 | 코드 | 설명 |
|---|---|---|
| 채널 결과 | `MessageDeliveryStatus` | `PENDING` 호출 전 · `REQUESTED` 결과 수신 중(솔루션 접수) · `SENT` 성공 · `FAILED` 실패 · `SKIPPED` 제외 |
| 발송 상태 | `MessageSendStatus` | `SENDING` 발송 중 · `RESULT_PENDING` 결과 수신 중 · `COMPLETED` 완료. 저장하지 않고 조회할 때 계산 |
| 지연 | `delayed` | 완료가 아니고 `MessageSend.requestedAt` 후 `recruit.message.result-wait-minutes`(기본 60분)가 지남. 화면 표시만 바꾼다 |
| 발송 단위 | `DeliveryUnit` | 내용이 같은 수신자를 최대 10명씩 묶은 게이트웨이 호출 1회 |
| 거래 ID | `transactionId` | 발송 단위 호출 1회에 솔루션이 준 ID. 결과 매칭의 유일한 키(`mailTransactionId`·`smsTransactionId`) |
| 발송 결과 | `DeliveryReport` | 나중에 오는 거래 1건의 결과(거래 ID + 결과코드). 그 거래 수신자 전원에 적용. 채널 필드는 없다 |
| 실패 사유 | `failureReason` | `GATEWAY_ERROR` 접수 실패(서버 코드) 또는 솔루션 결과코드. 제외 사유 `CHANNEL_OFF`·`NO_CONTACT`·`INVALID_CONTACT`는 [message](message.md) |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/MessageHistoryAdminController.java` | 발송 이력 목록·상세 2개 |
| service | `{BE}/service/MessageSendRequestedEvent.java` | 발송 접수 커밋 후 디스패처가 받는 이벤트(수신자 id·치환 내용) |
| service | `{BE}/service/MessageDispatcher.java` | 발송 단위별 게이트웨이 호출·접수 기록, 기록 직후 먼저 온 결과 반영(`@Async`, 커밋 후. 테스트 발송은 동기 직접 호출) |
| service | `{BE}/service/MessageDispatchRecorder.java` | 단위 접수 결과 기록·발송 결과 반영(bulk update, 멱등). 트랜잭션 경계 |
| service | `{BE}/service/MessageDeliveryService.java` | 발송 단위 1개를 게이트웨이로 전달·접수 결과 정규화(DB 미접근) |
| service | `{BE}/service/DeliveryItem.java` | 수신자 1명·채널 1개의 치환 완료 내용 |
| service | `{BE}/service/DeliveryUnit.java` | 발송 단위 묶기(최대 10명, 메일 먼저) |
| service | `{BE}/service/GatewayResult.java` | 게이트웨이 접수 결과(접수 여부·거래 ID·실패 사유) |
| service | `{BE}/service/MailMessage.java` | 메일 1통 내용(HTML+일반 텍스트) |
| service | `{BE}/service/SmsMessage.java` | 문자 1건 내용(발신번호·본문·SMS/LMS 구분) |
| service | `{BE}/service/MailGateway.java` | 메일 발송 연동 인터페이스 |
| service | `{BE}/service/SmsGateway.java` | 문자 발송 연동 인터페이스 |
| service | `{BE}/service/LoggingMailGateway.java` | 목업 메일 게이트웨이(기본값, 항상 접수·가짜 거래 ID·로그만) |
| service | `{BE}/service/LoggingSmsGateway.java` | 목업 문자 게이트웨이(기본값, 항상 접수·가짜 거래 ID·로그만) |
| service | `{BE}/service/MessageMailLayout.java` | 치환된 본문을 고정 브랜드 레이아웃에 입힘 |
| template | `{BR}/templates/message-mail.html` | 메일 브랜드 레이아웃 |
| service | `{BE}/service/DeliveryReport.java` | 발송 결과 1건(거래 ID + 결과코드) |
| service | `{BE}/service/DeliveryReportHandler.java` | 결과 수신 처리부(보관 → 반영, 접수 기록 직후 반영, 1분 재시도·10분 폐기) |
| service | `{BE}/service/DeliveryReportBuffer.java` | 짝을 못 찾은 결과의 메모리 보관(서버 1대 전제) |
| service | `{BE}/service/MockDeliveryReportScheduler.java` | 목업 게이트웨이의 가짜 결과(3초 뒤, `recruit.message.gateway=logging`일 때만) |
| service | `{BE}/service/MessageHistoryService.java` | 이력 목록·상세, 상태·건수·지연 계산(읽기 전용) |
| entity | `{BE}/domain/entity/MessageSend.java` | 발송 요청 1회. 치환 전 원문·템플릿 이름·조건 요약·발송자·`requestedAt`(상태·건수는 저장하지 않음) |
| entity | `{BE}/domain/entity/MessageRecipient.java` | 발송 1회의 수신자 1명·채널별 결과·거래 ID. 이름·연락처 AES 암호화, `@DynamicUpdate` |
| repository | `{BE}/domain/repository/MessageSendRepository.java` | 발송 CRUD·이력 검색(기간·종류·공고·구분, 최신순) |
| repository | `{BE}/domain/repository/MessageRecipientRepository.java` | 수신자 CRUD·결과 반영 bulk update·거래 ID 존재 확인·발송별 상태 건수 |
| repository | `{BE}/domain/repository/MessageRecipientStatusCount.java` | 상태 건수 projection(발송 id·메일 상태·SMS 상태·수) |
| config | `{BE}/config/MessageProperties.java` | `recruit.message.*` 발신 정보·사이트 주소·최대 수신자·게이트웨이 선택·결과 대기 시간·성공 결과코드 |
| config | `{BE}/config/AsyncConfig.java` | `@EnableAsync`만 둔다 |
| enum | `{BE}/enumeration/MessageChannel.java` | `MAIL`·`SMS` |
| enum | `{BE}/enumeration/MessageSendStatus.java` | 발송 1회 상태(계산값, `of`) |
| enum | `{BE}/enumeration/MessageDeliveryStatus.java` | 수신자·채널별 결과 |
| dto | `{BE}/dto/condition/MessageHistoryCondition.java` | 이력 검색 조건 |
| dto | `{BE}/dto/response/MessageSendSummaryResponse.java` | 이력 목록 1행 |
| dto | `{BE}/dto/response/MessageSendDetailResponse.java` | 이력 상세(목록 필드 + 원문 + 수신자) |
| dto | `{BE}/dto/response/MessageChannelCountResponse.java` | 채널별 상태 건수 |
| dto | `{BE}/dto/response/MessageRecipientResponse.java` | 이력 상세 수신자 1명(연락처 원문) |
| exception | `{BE}/exception/MessageSendNotFoundException.java` | 404(이력 상세) |
| test | `{BT}/service/DeliveryUnitTest.java` | 발송 단위 묶기(최대 10명·메일 먼저) |
| test | `{BT}/service/MessageMailLayoutTest.java` | 메일 레이아웃 렌더링 |
| test | `{BT}/service/MessageDeliveryServiceTest.java` | 게이트웨이 호출·접수 결과 정규화 |
| test | `{BT}/service/MessageDispatcherTest.java` | 3개: 단위 디스패치·접수 직후 보관 결과 반영, 이벤트 경로, 단위 기록 예외 뒤 다음 단위 계속 |
| test | `{BT}/service/MessageDispatchRecorderTest.java` | 접수 기록·결과 반영(`REQUESTED`만, 성공 코드, 멱등) |
| test | `{BT}/service/MessageSendAsyncFlowTest.java` | 커밋 → 비동기 접수 → 목업 결과 → `COMPLETED` 계산 전체 경로(트랜잭션 없음, 로깅 게이트웨이) |
| test | `{BT}/config/AsyncConfigTest.java` | `@EnableAsync` 동작 검증 |
| test | `{BT}/service/DeliveryReportHandlerTest.java` | 결과 보관·반영·재시도·10분 폐기 |
| test | `{BT}/service/MockDeliveryReportSchedulerTest.java` | 목업 결과 3초·실패 코드 |
| test | `{BT}/service/MessageRecipientDynamicUpdateTest.java` | `@DynamicUpdate` 회귀(같은 행의 메일·SMS 채널이 서로 덮어쓰지 않음) |
| test | `{BT}/service/MessageHistoryServiceTest.java` | 필터·정렬·건수·상태·지연·상세·파기된 수신자 |
| test | `{BT}/controller/MessageHistoryAdminControllerTest.java` | 이력 API·400·404 |

### 프론트

| 종류 | 파일 | 역할 |
|---|---|---|
| route | `{FE}/routes/adminRoutes.ts` | `AdminMessageHistory`(`/admin/messages/history`) — 공유 파일 |
| view | `{FE}/views/admin/message/AdminMessageHistoryView.vue` | 발송 이력 목록(필터·서버 페이지), `sendId` 쿼리면 상세 드로어 자동 열기 |
| view | `{FE}/views/admin/message/MessageHistoryDrawer.vue` | 발송 상세 드로어(원문·수신자별 결과, 완료 전 5초 새로고침) |
| util | `{FE}/views/admin/message/messageHistory.ts` | 상태·사유 라벨, 채널 칸·건수, 파기 표시, 테스트 결과 변환(`toTestResults`, 테스트 발송 카드가 씀), 기본 기간 |
| test | `{FE}/views/admin/message/__tests__/messageHistory.spec.ts` | Vitest |

API 모듈·타입은 [message](message.md) 소유 `{FE}/api/admin/messageApi.ts`(`getHistory`·`getHistoryDetail`)·`{FE}/types/admin/message.ts`를 쓴다.

## API 계약

| 상태 | 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|---|
| 🟢 | GET | /admin/messages/history | query `from?, to?, type?, jobPostingId?, test?, page(0), size(20)` | `PageResponse<MessageSendSummaryResponse>` 발송일시 desc, id desc |
| 🟢 | GET | /admin/messages/history/{sendId} | 없음 | `MessageSendDetailResponse` |

권한: `/api/admin/**` 규칙(`ADMIN`, `RECRUIT_ADMIN`). 비로그인 401, 그 밖 역할 403. 테스트 발송·발송 접수 API는 [message](message.md).

### 엔드포인트 상세

- history query: `from`·`to`는 `YYYY-MM-DD`(발송일, 양끝 포함). 비우면 `to` = 오늘, `from` = `to` − 29일(최근 30일). `test` 없음 = 실발송+테스트, `true` = 테스트만, `false` = 실발송만. `size` 1~100.
- `MessageSendSummaryResponse`: `{ id, requestedAt, type, test, jobPostingTitle, stageName, conditionSummary, title, mailEnabled, smsEnabled, recipientCount, mail{ pending, requested, sent, failed, skipped }, sms{ 〃 }, status, delayed, senderName }`. `title` = 메일을 켰으면 메일 제목, 아니면 SMS 원문 앞 40자. `status`·건수·`delayed`는 조회할 때 계산(`## 규칙·불변식`).
- `MessageSendDetailResponse`: 요약 필드 전부(같은 이름) + `{ templateName, mailSubject, mailBody, smsBody(치환 전 원문), recipients[{ id, applicationId(테스트 수신자 null), name, email, phone, mailStatus, mailFailureReason, smsStatus, smsFailureReason, smsKind }] }` 수신자 id 순. 연락처는 가리지 않는다. 파기된 수신자는 `name`·`email`·`phone`이 null.
- history 400(`InvalidMessageException`): `page는 0 이상이어야 합니다.` · `size는 1 이상 100 이하여야 합니다.` · `조회 시작일이 종료일보다 늦습니다.`(날짜·enum 형식 오류는 공통 400). 상세 404: `발송 기록을 찾을 수 없습니다.`(`MessageSendNotFoundException`).

## 규칙·불변식

- 비동기 디스패치: 발송 접수가 커밋되면(`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`) `MessageDispatcher`가 `MessageSendRequestedEvent`의 치환 완료 내용(메모리)으로 발송한다. 테스트 발송은 같은 `dispatch`를 요청 트랜잭션 안에서 동기로 부른다([message](message.md)).
- 발송 단위(`DeliveryUnit.group`): 채널·제목·본문·SMS 구분이 완전히 같은 수신자를 최대 10명씩 묶어 게이트웨이를 1회 호출한다(변수로 내용이 사람마다 다르면 1명당 1단위). 메일 단위를 먼저 처리하고 단위들은 디스패처 스레드에서 순차 처리한다. 한 단위의 접수 결과와 나중에 오는 발송 결과는 그 단위 수신자 전원에게 같이 적용한다.
- 채널 상태 전이: `PENDING` → 접수되면 `REQUESTED` + 거래 ID, 접수 실패면 `FAILED` + 사유(`MessageDispatchRecorder.recordUnit`). `REQUESTED` → 발송 결과가 오면 `SENT` 또는 `FAILED`(사유 = 결과코드). 접수 사유는 게이트웨이 코드 200자까지, 호출 예외·`null` 결과·빈 사유·거래 ID 없음(또는 100자 초과)은 `GATEWAY_ERROR`(`MessageDeliveryService.deliver`).
- 단위 기록 실패: 단위 기록(`recordUnit`, 직후 `applyBuffered` 포함)이 예외로 끝나면 디스패처는 경고 로그(채널·인원·예외 클래스만)를 남기고 다음 단위를 계속 처리한다. `recordUnit`이 실패한 단위의 수신자는 `PENDING`으로 남는다(`MessageDispatcherTest`).
- 게이트웨이 계약(`MailGateway`·`SmsGateway`): 호출 1회 = 내용 1개 + 수신자 1~10명(10명 제한은 호출 측이 지킨다). 접수면 `GatewayResult.accepted(거래 ID)`(거래 ID 필수), 거절이면 주소·번호가 없는 사유 코드. 메일 구현체는 단위 안에서도 수신자마다 따로 보낸다(수신자별 To 또는 BCC, 지원자끼리 주소 노출 금지).
- 발송 결과 수신(`DeliveryReportHandler.handle`): 결과를 메모리 버퍼(`DeliveryReportBuffer`)에 넣고 `MessageDispatchRecorder.applyReport`로 반영을 시도한다. 반영은 JPQL bulk update(거래 ID 일치 + `REQUESTED`인 행만, 메일·SMS 컬럼 각각)라 다시 온 결과는 0행이다(멱등). 바뀐 행이 있거나 이미 기록된 거래면 버퍼에서 뺀다. 성공 판정 = 결과코드 ∈ `recruit.message.success-result-codes`(기본 `0000`).
- 먼저 도착한 결과: 디스패처가 단위 접수를 기록한 직후 `applyBuffered(거래 ID)`로 보관 결과를 반영한다. `retryBuffered`(1분 간격)가 다시 시도하고, 받은 지 10분이 지나도 짝이 없으면 거래 ID만 경고 로그로 남기고 버린다.
- 발송 상태·건수는 저장하지 않는다(`MessageSend`에 상태·집계 컬럼 없음). 조회할 때 수신자 채널 상태로 센다: `PENDING`이 있으면 `SENDING`, 없고 `REQUESTED`가 있으면 `RESULT_PENDING`, 둘 다 없으면 `COMPLETED`(`MessageSendStatus.of`). 지연이면 화면은 `RESULT_PENDING`을 "결과 미수신", `SENDING`을 "발송 중단"으로 보인다. DB 값은 바꾸지 않고, 시간이 지났다고 실패로 처리하지 않는다.
- 이력 목록(`MessageHistoryService.search`): 기간·종류·공고·구분 필터, `requestedAt desc, id desc`, 건수는 페이지의 발송 id로 group by 쿼리 1개(`countStatusesByMessageSendIds`). 끈 채널은 건수가 아니라 `mailEnabled`·`smsEnabled`로 "제외" 표시. 목록 행은 상태 태그 + 채널별 건수 텍스트("N건"/"제외") + 결과 칸(성공·실패·수신 중 건수 텍스트, 막대 그래프 아님)으로 보인다. 목록은 새로고침 버튼으로 갱신하고, `sendId` 쿼리로 들어오면 그 상세 드로어를 바로 연다. 상세 드로어는 완료가 아니면 열려 있는 동안 5초마다 다시 읽는다.
- 수신자 연락처(`MessageRecipient`의 `recipientName`·`email`·`phone`)는 발송 시점 값을 AES로 암호화해 저장한다. 발송 뒤 지원자가 연락처를 바꿔도 발송 당시 값이 남는다.
- 로그: 목업 게이트웨이·호출 실패 로그는 이메일·전화번호를 마스킹하고(`MessageContacts`, [message](message.md)) 본문·예외 메시지는 남기지 않는다. 결과 처리 로그는 거래 ID만 남긴다.
- 파기 연동: 지원서 파기 시 그 지원서의 `MessageRecipient` 이름·이메일·휴대폰(암호화 컬럼이라 null)과 `createdBy`·`updatedBy`를 null로 바꾼다. 채널 상태·거래 ID·실패 사유(결과코드)는 유지하고, 테스트 수신자(`jobApplication` null)는 대상이 아니다(`ApplicationPiiPurgeRepository.purgeMessageRecipients`, [privacy-audit](privacy-audit.md)). 화면은 지원서 수신자인데 이름·연락처가 모두 비었으면 "(파기됨)"으로 보인다.
- 목업 결과(`recruit.message.gateway=logging`, 기본): 목업 게이트웨이는 항상 접수(가짜 거래 ID = UUID)하고 `MockDeliveryReportScheduler`가 3초 뒤 결과를 넘긴다. 결과코드 `0000`, 그 단위 수신 연락처에 `fail`이 들어 있으면 `9999`.

## 변경 레시피

- **실제 솔루션 연동**: `MailGateway`·`SmsGateway` 구현체를 `@ConditionalOnProperty(prefix = "recruit.message", name = "gateway", havingValue = "<값>")`로 추가한다(위 게이트웨이 계약). 결과 소켓 클라이언트는 받은 메시지를 `DeliveryReport(거래 ID, 결과코드)`로 바꿔 `DeliveryReportHandler.handle`만 부른다. 성공 코드는 `RECRUIT_MESSAGE_SUCCESS_RESULT_CODES`(쉼표 구분). `gateway`가 `logging`이 아니면 목업 게이트웨이와 `MockDeliveryReportScheduler`는 뜨지 않는다. `spring-boot-starter-mail` 등 새 의존성은 사용자 승인이 필요하다. 거래 ID가 채널 간에 겹칠 수 있는 스펙이면 아래 함정의 채널 구분을 함께 넣는다.

## 검증

```bash
# 백엔드 (recruit_back/recruit_backend/)
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageDispatch*" --tests "com.shinyoung.recruit.service.MessageDelivery*" --tests "com.shinyoung.recruit.service.Delivery*" --tests "com.shinyoung.recruit.service.MockDeliveryReport*" --tests "com.shinyoung.recruit.service.MessageHistory*" --tests "com.shinyoung.recruit.service.MessageMailLayout*" --tests "com.shinyoung.recruit.service.MessageRecipient*" --tests "com.shinyoung.recruit.service.MessageSendAsyncFlow*" --tests "com.shinyoung.recruit.service.ApplicationPiiPurge*" --tests "com.shinyoung.recruit.controller.MessageHistory*" --tests "com.shinyoung.recruit.config.AsyncConfigTest" --tests "com.shinyoung.recruit.config.ApplicationYamlTest" --tests "com.shinyoung.recruit.config.SecurityConfigTest" --no-daemon
# 프론트 (recruit_front/)
npm run type-check
npx vitest run src/views/admin/message/__tests__/messageHistory.spec.ts
```

## 함정·결정

- `@EnableAsync`만 두고 별도 `Executor` 빈을 만들지 않는다: 부트 기본 `applicationTaskExecutor`를 쓴다(새로 만들면 기본 실행기가 빠져 다른 비동기 작업에 영향). 비동기 발송은 이 기본 실행기를 다른 비동기 작업과 공유한다.
- 치환된 내용은 이벤트(메모리)로만 디스패처에 넘기고 DB에는 원문만 남긴다. 접수 직후 서버가 내려가면 처리되지 못한 채널은 `PENDING`으로 남아 `SENDING`으로 계산되고 60분 뒤 "발송 중단"으로 보인다. 자동 재개는 하지 않는다(범위 밖).
- 단위 기록 실패의 수용 위험: 게이트웨이가 이미 접수한 단위의 기록이 실패하면(드문 락 타임아웃·DB 오류) 거래 ID가 저장되지 않는다. 그 거래의 결과는 짝을 못 찾아 10분 뒤 버려지고, 수신자는 `PENDING`으로 남아 60분 뒤 "발송 중단"으로 보이지만 실제로는 발송됐을 수 있다. 드물어서 수용한다.
- 지연 판정은 `MessageSend.requestedAt` 기준이다. 아주 큰 발송(3,000명 × 2채널 = 최대 6,000회 순차 호출)이 60분 넘게 걸리면 디스패치가 아직 진행 중인데도 일시적으로 "발송 중단"으로 보일 수 있다(디스패치가 끝나면 "결과 미수신", 결과가 모두 오면 "완료"로 바뀐다).
- 🔴 거래 ID 유일성 가정(미확인): 거래 ID가 메일·SMS 사이에서도 유일하고 재사용되지 않는다고 가정한다. `DeliveryReport`에 채널이 없어 `applyReport`는 같은 ID로 두 채널 컬럼을 모두 확인·갱신한다. 채널 간에 ID가 겹치거나 재사용되면(예: 일자별 일련번호) 다른 수신자에 결과가 반영되거나, 새 결과가 "이미 기록된 거래"로 판정돼 버려질 수 있다. 솔루션 스펙이 확정되면 `DeliveryReport`에 채널을 넣고 존재 확인·update를 채널별로 한정한다(설계서 17절 8번).
- `MessageDispatchRecorder`는 기본 전파(`REQUIRED`)이고 트랜잭션은 이 빈에만 둔다(`DeliveryReportHandler`는 트랜잭션 없음, 자기 호출 회피). 비동기 디스패처·결과 처리부는 호출마다 새 트랜잭션을 열고, 테스트 발송(동기)은 요청 트랜잭션에 합류한다(그 위험은 [message](message.md) 함정).
- `applyReport`는 거래 ID 존재 확인을 두 bulk update **전에** 한다. update 뒤에 확인하면 그 사이(READ COMMITTED) 접수 기록이 막 커밋돼 update는 0행인데 확인은 true가 되어, 이미 기록된 거래로 오판해 버퍼에서 빠지고 결과가 영영 반영되지 않을 수 있다.
- 결과 반영은 JPQL bulk update라 엔티티 감사(`updatedAt`)를 거치지 않고 `processedAt`만 갱신한다. `clearAutomatically = true`라 같은 트랜잭션에서 반영이 일어나면 영속성 컨텍스트가 비워진다.
- `MessageRecipient`는 `@DynamicUpdate`다. SMS 단위 접수 기록(엔티티 변경)이 같은 수신자의 메일 결과 반영(bulk update)과 겹쳐도 바뀐 컬럼만 update해서 메일 결과를 옛 값으로 덮어쓰지 않는다. 회귀 테스트는 `MessageRecipientDynamicUpdateTest`.
- 먼저 도착한 결과 버퍼는 메모리(`ConcurrentHashMap`)다. 서버 1대 전제이고 재시작하면 보관 중인 결과는 사라진다(솔루션은 같은 결과를 다시 보내지 않으므로 그 수신자는 `REQUESTED`로 남아 "결과 미수신").
- 목업 결과와 1분 재시도는 부트 기본 `taskScheduler`(스레드 1개, `@EnableScheduling`이 있을 때 자동 생성)를 `ClientEventLogCleanupScheduler`와 함께 쓴다. 별도 `TaskScheduler` 빈을 만들면 기본 스케줄러가 빠진다. 스케줄러 사용처는 `SchedulingConfig` Javadoc에도 적혀 있다. `@Transactional` 테스트에서 테스트 발송이 예약한 목업 결과는 롤백된 행을 못 찾아 버퍼에 남았다가 10분 뒤 버려진다.
- 결과코드별 설명은 스펙 확정 전이라 화면에 `결과코드 {코드}`로 보인다(설계서 17절 6번). 수신자별 거래 ID는 미확인이라 거래 단위 결과를 단위 전원에 적용한다(17절 7번). 목업 결과코드(`0000`·`9999`)는 성공 코드 설정과 무관하게 고정이다.
- 게이트웨이 실패 사유(`GatewayResult.failureReason`)는 평문 컬럼에 저장되고 테스트 발송 응답에도 나가며 연락처 파기 대상도 아니다. 구현체는 주소·번호를 넣지 않은 코드만 돌려줘야 한다.
- S4에서 `MessageSend`의 `status`·채널별 집계 6개·`completedAt`을 없앴다. 결과가 거래마다 나중에 여러 스레드에서 오므로 저장된 집계를 고치지 않고 조회할 때 센다. S3 코드로 만든 로컬 H2 DB에는 이 NOT NULL 컬럼이 남고, `@Enumerated(STRING)` 컬럼의 네이티브 `enum` 타입(Hibernate 7 H2·MariaDB 방언)에 `REQUESTED`가 없어 저장이 실패한다. `ddl-auto: update`는 둘 다 고치지 않으므로 로컬은 `message_recipient`·`message_send`를 지우고 다시 만든다. 운영 테이블 생성 SQL(설계서 17절 1번)은 S4 엔티티 기준으로 작성해야 한다.
- `MessageSendAsyncFlowTest`는 `@Transactional` 없이 기본 로깅 게이트웨이로 커밋 → 비동기 접수 → 목업 결과 → `COMPLETED`를 확인하고, 만든 행은 `@AfterEach`에서 지운다(테스트 H2를 다른 테스트와 공유).
- 프론트: 이력 기간은 `a-range-picker`의 `value-format="YYYY-MM-DD"` 문자열만 쓴다(`dayjs`는 `package.json` 직접 의존이 아니라 가져오지 않는다). 테스트 결과 폴링·상세 드로어 새로고침은 `setTimeout` 연쇄로 하고 요청 번호로 늦은 응답을 버린다.
