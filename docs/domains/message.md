# 메일·SMS 메시지 템플릿·발송 요청 (`message`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [message-delivery](message-delivery.md) (디스패치·게이트웨이·결과 수신·상태 계산·발송 이력) · [stage-result](stage-result.md) (결과 발표 대상) · [interview](interview.md) (면접 안내 대상·변수) · [job-posting](job-posting.md) (마감 임박 대상) · [role-menu](role-menu.md) (메뉴 등록)

## 요약

- 관리자가 지원자에게 메일·SMS를 보낸다. 종류 5개를 고르면 대상자와 문구가 자동으로 채워지고, 수신자별 미리보기 → 담당자 테스트 발송 → 실제 발송 요청 순서로 진행한다.
- 시스템 자동발송 종류 3개(`SIGNUP_VERIFICATION`·`PASSWORD_RESET`·`APPLICATION_SUBMITTED`, 2026-09-23)는 이 카드가 템플릿만 관리한다(기동 시 기본 템플릿 생성, `SystemMessageTemplateInitializer`). 발송은 [message-delivery](message-delivery.md)의 `SystemMailService`가 하고, 관리자 발송·테스트 발송·대상 조회는 400으로 막는다.
- 이 카드 범위: 템플릿·변수, 종류별 대상자 조회, 작성·미리보기, 테스트 발송·발송 **접수**(검증 → 수신자 행 저장 → `MessageSendRequestedEvent` 발행). **접수 뒤의 디스패치·게이트웨이·결과 수신·상태 계산·발송 이력은 [message-delivery](message-delivery.md).**
- 설계서: `docs/archive/superpowers/specs/2026-09-19-message-send-design.md`. 구현은 S1 템플릿 → S2 대상자·작성 → S3 발송 → S4 결과 수신·이력 순서로 나눴고 S4까지 완료했다.
- 화면: `/admin/messages`(`AdminMessageSend`, 종류·조건·작성·미리보기·테스트 발송·발송) · `/admin/messages/templates`(`AdminMessageTemplates`). 발송 이력 `/admin/messages/history`는 [message-delivery](message-delivery.md).
- 경계: `MessageSend`·`MessageRecipient` 엔티티·저장소, 채널·상태 enum, `MessageProperties`(`recruit.message.*`)는 [message-delivery](message-delivery.md) 소유다(이 카드의 `MessageSendService`가 행을 만들고 발신 정보·`max-recipients`를 읽는다). 프론트 공유 모듈 `{FE}/api/admin/messageApi.ts`·`{FE}/types/admin/message.ts`는 이력 API·타입까지 이 카드가 소유한다.

## 용어

| 용어 | 코드 | 설명 |
|---|---|---|
| 메시지 종류 | `MessageType` | `RESULT_ANNOUNCEMENT` 결과 발표 · `DEADLINE_REMINDER` 서류 마감 임박 · `INTERVIEW_SCHEDULE` 면접 일정·장소 · `INTERVIEW_NOTICE` 면접 공지 · `FREE` 직접 입력 · 시스템 자동발송 `SIGNUP_VERIFICATION`·`PASSWORD_RESET`·`APPLICATION_SUBMITTED`(`isSystem()`) |
| 템플릿 | `MessageTemplate` | 종류 1개에 속한 메일 제목·본문 + SMS 본문 |
| 기본 템플릿 | `defaultTemplate` | 종류당 최대 1개. 발송 화면에서 종류를 고르면 자동으로 불러온다 |
| 변수 | `MessageVariable` | 본문의 `#{키}`. 키는 한글(`이름`, `면접일시` 등 14개). 종류별 허용 목록의 단일 출처 |
| 발송 접수 | `MessageSendService.send` | 대상 재조회·검증 뒤 발송 1회와 수신자 행을 저장하고 이벤트를 발행한다. 실제 호출은 커밋 후 비동기([message-delivery](message-delivery.md)) |
| 제외 사유 | `MessageContacts` 상수 | `CHANNEL_OFF` 채널 끔 · `NO_CONTACT` 연락처 없음 · `INVALID_CONTACT` 형식 오류 → 그 채널은 `SKIPPED`. 그 밖의 채널 상태·사유는 [message-delivery](message-delivery.md) |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/MessageTemplateAdminController.java` | 템플릿 CRUD 5개 |
| controller | `{BE}/controller/MessageSendAdminController.java` | 변수 카탈로그·대상자 조회·테스트 발송·발송 접수 |
| service | `{BE}/service/MessageTemplateService.java` | 검증·기본 단일화·CRUD·변수 목록 |
| service | `{BE}/service/SystemMessageTemplateInitializer.java` | 기동 시 시스템 종류 기본 템플릿이 없으면 초안 생성(`ApplicationRunner`) |
| service | `{BE}/service/MessageRenderer.java` | `#{키}` 허용 검사·치환(줄바꿈 LF 통일)·SMS byte/구분 |
| service | `{BE}/service/MessageTargetService.java` | 종류별 대상 조건 검증·조회·연락처 판정·변수 조립 |
| service | `{BE}/service/MessageVariableFormatter.java` | 수신자별 `#{변수}` 값 계산·형식 |
| service | `{BE}/service/MessageVariableContext.java` | 변수 계산 입력(이름·공고·전형·면접) |
| service | `{BE}/service/MessageContacts.java` | 연락처 정규화·유효성 검사·제외 사유 코드·로그 마스킹 |
| service | `{BE}/service/MessageSendService.java` | 테스트 발송(디스패처 동기 호출)·발송 접수(이벤트 발행) |
| entity | `{BE}/domain/entity/MessageTemplate.java` | 템플릿. 컬럼 `message_type` |
| repository | `{BE}/domain/repository/MessageTemplateRepository.java` | 종류별·기본 조회 |
| repository | `{BE}/domain/repository/MessageTargetRepository.java` | 대상 JPQL(철회·파기 제외). `Repository<JobApplication, Long>` |
| enum | `{BE}/enumeration/MessageType.java` | 종류 8개(관리자 5 + 시스템 3, `isSystem`) |
| enum | `{BE}/enumeration/MessageVariable.java` | 변수 14개·허용 종류 |
| enum | `{BE}/enumeration/SmsKind.java` | `SMS`·`LMS` |
| dto | `{BE}/dto/request/MessageTemplateSaveRequest.java` | 등록·수정 공용 |
| dto | `{BE}/dto/request/MessageContentRequest.java` | 발송 화면에서 작성한 내용(치환 전) |
| dto | `{BE}/dto/request/MessageSendRequest.java` | 발송 요청 |
| dto | `{BE}/dto/request/MessageTestSendRequest.java` | 테스트 발송 요청 |
| dto | `{BE}/dto/request/MessageTesterRequest.java` | 테스트 수신자(담당자) 1명 |
| dto | `{BE}/dto/condition/MessageTargetCondition.java` | 대상 조건 |
| dto | `{BE}/dto/response/MessageTemplateResponse.java` | 템플릿 응답 |
| dto | `{BE}/dto/response/MessageVariableResponse.java` | 변수 카탈로그 응답 |
| dto | `{BE}/dto/response/MessageTargetResponse.java` | 대상 조회 응답 |
| dto | `{BE}/dto/response/MessageTargetRecipientResponse.java` | 수신자 1명 |
| dto | `{BE}/dto/response/MessageSenderResponse.java` | 발신 정보 |
| dto | `{BE}/dto/response/MessageSendResultResponse.java` | 발송 접수 결과 |
| dto | `{BE}/dto/response/MessageTestSendResponse.java` | 테스트 발송 결과(수신자·채널별 목록) |
| dto | `{BE}/dto/response/MessageTestSendResultResponse.java` | 테스트 발송 결과 1건 |
| exception | `{BE}/exception/InvalidMessageException.java` | 400(이력 조회 400도 이 예외) |
| exception | `{BE}/exception/MessageTemplateNotFoundException.java` | 404 |
| test | `{BT}/service/MessageRendererTest.java` | 변수 검사·치환·SMS byte |
| test | `{BT}/service/MessageTemplateServiceTest.java` | 서비스 |
| test | `{BT}/controller/MessageTemplateAdminControllerTest.java` | API |
| test | `{BT}/service/MessageVariableFormatterTest.java` | 변수 값 |
| test | `{BT}/service/MessageTargetServiceTest.java` | 종류별 대상 |
| test | `{BT}/controller/MessageSendAdminControllerTest.java` | 변수 카탈로그·대상자 조회 API |
| test | `{BT}/service/MessageContactsTest.java` | 연락처 정규화·유효성·마스킹 |
| test | `{BT}/service/MessageSendServiceTest.java` | 15개: 발송 접수·검증 400·테스트 발송(접수 결과, 테스트 발송 중 먼저 온 결과가 반영되면 바로 `SENT`) |
| test | `{BT}/controller/MessageSendCommandControllerTest.java` | 테스트 발송(접수 결과)·발송 접수 API |

### 프론트

| 종류 | 파일 | 역할 |
|---|---|---|
| route | `{FE}/routes/adminRoutes.ts` | `AdminMessageSend`(`/admin/messages`) · `AdminMessageTemplates`(`/admin/messages/templates`) — 공유 파일 |
| api | `{FE}/api/admin/messageApi.ts` | 템플릿·변수·대상자·테스트 발송·발송·이력 API(이력은 [message-delivery](message-delivery.md) 화면이 씀) |
| type | `{FE}/types/admin/message.ts` | 타입(이력 타입 포함). `MessageType` = 관리자 종류, `AnyMessageType` = 전체 |
| view | `{FE}/views/admin/message/AdminMessageTemplateView.vue` | 템플릿 관리 화면(시스템 종류는 SMS 숨김·기본 템플릿 삭제 버튼 숨김) |
| view | `{FE}/views/admin/message/AdminMessageSendView.vue` | 발송 화면 조립·상태·테스트 결과 폴링·발송 후 이력 알림 |
| view | `{FE}/views/admin/message/MessageTypePicker.vue` | 종류 카드 |
| view | `{FE}/views/admin/message/MessageTargetBar.vue` | 조건 바·인원 요약 |
| view | `{FE}/views/admin/message/MessageRecipientDrawer.vue` | 수신자 드로어 |
| view | `{FE}/views/admin/message/MessageComposer.vue` | 작성 영역·템플릿으로 저장 |
| view | `{FE}/views/admin/message/MessagePreview.vue` | 메일·휴대폰 미리보기 |
| view | `{FE}/views/admin/message/MessageTestSendCard.vue` | 테스트 발송 카드(담당자 추가·최근 수신자 기억·접수/최종 결과 표시, 결과 변환은 `messageHistory.ts`) |
| view | `{FE}/views/admin/message/MessageSendBar.vue` | 하단 고정 발송 바 |
| view | `{FE}/views/admin/message/MessageSendConfirmModal.vue` | 발송 확인 모달 |
| util | `{FE}/views/admin/message/messageTypes.ts` | 종류 표시 메타(관리자 `MESSAGE_TYPES`·시스템 `SYSTEM_MESSAGE_TYPES`·전체 `ALL_MESSAGE_TYPES`) |
| util | `{FE}/views/admin/message/messageRender.ts` | 치환·조각 분리·SMS byte/구분(서버 `MessageRenderer`와 같은 규칙) |
| util | `{FE}/views/admin/message/messageCondition.ts` | 조건 상태·쿼리 변환·라벨 |
| util | `{FE}/views/admin/message/useVariableCursor.ts` | 변수 칩 삽입(템플릿·발송 화면 공유) |
| util | `{FE}/views/admin/message/messageSendSummary.ts` | 발송 요약 계산·연락처 유효성·조건 설명(서버와 같은 규칙) |
| test | `{FE}/views/admin/message/__tests__/messageRender.spec.ts` | Vitest |
| test | `{FE}/views/admin/message/__tests__/messageCondition.spec.ts` | Vitest |
| test | `{FE}/views/admin/message/__tests__/messageSendSummary.spec.ts` | Vitest |
| test | `{FE}/views/admin/message/__tests__/messageTypes.spec.ts` | Vitest |

## API 계약

| 상태 | 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|---|
| 🟢 | GET | /admin/messages/variables | 없음 | `List<MessageVariableResponse>` `{ key, label, types[] }` 14개 |
| 🟢 | GET | /admin/message-templates | query `type?` | `List<MessageTemplateResponse>` 종류 → 기본 우선 → 이름순 |
| 🟢 | GET | /admin/message-templates/{id} | 없음 | `MessageTemplateResponse` (FE 미사용) |
| 🟢 | POST | /admin/message-templates | `{ type, name, defaultTemplate, mailSubject?, mailBody?, smsBody? }` | `MessageTemplateResponse` |
| 🟢 | POST | /admin/message-templates/{id} | 위와 같음 | `MessageTemplateResponse` |
| 🟢 | POST | /admin/message-templates/{id}/delete | 없음 | `null` |
| 🟢 | GET | /admin/messages/targets | query `type, jobPostingId, stageId?, resultStatus?, interviewGroup?, applicationStatus?` | `MessageTargetResponse` `{ recipients[], interviewGroups[], sender }` |
| 🟢 | POST | /admin/messages/test | `MessageTestSendRequest` `{ type, jobPostingId, stageId?, resultStatus?, interviewGroup?, applicationStatus?, previewApplicationId, testers[{ name, email?, phone? }](1~5명), content }` | `MessageTestSendResponse` `{ sendId, results[{ name, channel, status, failureReason }] }` — status는 접수 결과(`REQUESTED`·`FAILED`·`SKIPPED`) |
| 🟢 | POST | /admin/messages/send | `MessageSendRequest` `{ type, jobPostingId, stageId?, resultStatus?, interviewGroup?, applicationStatus?, applicationIds[], content }` | `MessageSendResultResponse` `{ sendId, status(항상 SENDING), recipientCount, excludedCount }` |

발송 이력 `GET /admin/messages/history`·`/history/{sendId}`는 [message-delivery](message-delivery.md). 권한: 모두 `/api/admin/**` 규칙(`ADMIN`, `RECRUIT_ADMIN`). `SecurityConfig` 변경 없음.

### 엔드포인트 상세

- `MessageTemplateResponse`: `{ id, type, name, defaultTemplate, mailSubject, mailBody, smsBody, updatedAt }`. 빈 채널은 null.
- `MessageTargetResponse`: `recipients[]` = `{ applicationId, name, email, phone, mailAvailable, smsAvailable, resultStatus, interviewGroup, interviewDateTime, draftStartedAt, variables{키: 값}, missingVariables[] }`, `interviewGroups[]`(면접 2종, 숫자 조는 숫자순), `sender { name, email, smsCallbackNumber }`(설정값, 설계서 대비 추가).
- targets 오류: 공고 없음 404, 공고에 없는 전형 404(`StageNotFoundException`), 선택 불가 조건 400(`전형을 선택해야 합니다.` · `결과가 발표된 전형만 선택할 수 있습니다.` · `접수 중인 공고만 선택할 수 있습니다.` · `면접 전형만 선택할 수 있습니다.` · `결과 조건은 전형을 선택해야 쓸 수 있습니다.` · `선택할 수 없는 결과입니다.` · `철회한 지원서는 대상이 아닙니다.`).
- `content`(테스트·발송 공용) = `{ templateId?, mailEnabled, smsEnabled, mailSubject?, mailBody?, smsBody? }`. 켠 채널의 제목·본문은 필수(서비스가 검증, DTO `@Size`는 제목 200·본문 10,000·SMS 2,000).
- test·send 공통 400: `메일이나 SMS 중 하나 이상 켜야 합니다.` · `메일 제목과 본문을 입력해야 합니다.` · `SMS 내용을 입력해야 합니다.` · `사용할 수 없는 변수: #{…}`(허용 목록은 `## 규칙·불변식`) · `보낼 수 있는 연락처가 없습니다.`(켠 채널이 전부 제외 대상).
- test 전용 400: `미리보기 대상이 현재 조건의 대상자가 아닙니다. 대상자를 다시 조회하세요.` · `테스트 수신자는 이메일이나 휴대폰 중 하나 이상 입력해야 합니다.` · `테스트 수신자 연락처 형식이 올바르지 않습니다.` · `SMS가 2,000byte를 넘습니다(미리보기 수험번호 {id}).`
- send 전용 400: `보낼 수신자가 없습니다. 대상자를 다시 조회하세요.`(요청한 `applicationIds`가 재조회한 현재 대상자와 겹치지 않음) · `한 번에 최대 {maxRecipients}명까지 보낼 수 있습니다.`(기본 3,000, `recruit.message.max-recipients`) · `SMS가 2,000byte를 넘는 수신자가 {n}명 있습니다(수험번호 {id, id...}).`
- 인증: 비로그인 401, `ROLE_ADMIN`·`ROLE_RECRUIT_ADMIN`이 아니면 403(`/api/admin/**` 보안 설정). test·send는 서비스에서 한 번 더 임직원인지 확인한다(`CurrentEmployeeService.getCurrentEmployeeActor`).
- test 응답 `results[].status`는 솔루션 접수 결과다(`REQUESTED` 접수 · `FAILED` 접수 실패 · `SKIPPED` 제외). 최종 결과는 발송 결과가 오면 이력 상세에 반영된다. 상태·`failureReason` 코드의 뜻과 전이는 [message-delivery](message-delivery.md) `## 규칙·불변식`.
- send 응답 `status`는 저장값이 아니라 항상 `SENDING`이다(발송 상태는 조회 때 계산 — [message-delivery](message-delivery.md)).
- 템플릿 등록·수정 400(시스템 종류): `시스템 자동발송 템플릿은 메일 제목과 본문을 입력해야 합니다.` · `인증 메일에는 #{인증번호}가 있어야 합니다.`(가입 인증·비밀번호 재설정, 제목 또는 본문) · `시스템 기본 템플릿은 기본을 해제할 수 없습니다.`(기본 해제·종류 변경). 삭제 400: `시스템 기본 템플릿은 삭제할 수 없습니다.` 시스템 종류의 `smsBody`는 보내도 null로 저장한다.
- targets·test·send 공통 400: `시스템 자동발송 유형은 직접 보낼 수 없습니다.`

## 규칙·불변식

- 메일은 제목·본문을 함께 채우거나 함께 비운다. 메일과 SMS 중 하나 이상 필요. 공백만 있는 값은 null로 저장한다. (`MessageTemplateService.validate`)
- 본문의 `#{키}`는 그 종류에 허용된 변수여야 한다. 없는 키·다른 종류의 변수는 400 "사용할 수 없는 변수: #{…}". (`MessageRenderer.validateVariables`)
- 기본으로 저장(등록·수정)하면 같은 종류의 다른 기본 템플릿을 해제한다. 종류를 바꾸며 기본으로 저장하면 새 종류 기준으로 해제한다.
- 템플릿 삭제는 행 삭제다. 발송 이력은 템플릿 이름·원문을 `MessageSend`에 복사해 두므로 영향이 없다.
- SMS byte: 코드포인트 단위로 127 이하 1byte, 그 밖 2byte. 90 이하 SMS, 2000 이하 LMS. 치환 전에 `\r\n`·`\r`을 `\n`으로 통일한다. 서버 `MessageRenderer`·프론트 `messageRender.ts`가 같은 규칙·같은 테스트 예시를 쓴다.
- 치환은 한 번만(값 안의 `#{…}`는 그대로), 값이 없으면 빈 문자열. 미리보기는 빈 변수를 빨간 강조로 `#{키}` 그대로 보여 준다.
- 대상: 철회·파기 지원서는 항상 제외. 결과 발표 = 발표 완료·마감 전형의 판정 결과(`PENDING`·`WITHDRAWN` 제외), 마감 임박 판정 = 공고 `PUBLISHED`이고 `ReceptionStatus.from(...) == ACCEPTING`(프론트 `accepting`과 같은 경계)인 공고의 `DRAFT` 지원서, 면접 2종 = `CONFIRMED` 면접의 `CANDIDATE`·`ASSIGNED`(여러 면접이면 가장 이른 면접, 정렬은 시작 시각 → 면접 id → 면접 순서 → 참가자 id), 직접 입력 = 지원 상태(기본 작성 중+제출) + 선택 전형 결과(발표 완료·마감 전형만). (`MessageTargetService`)
- 면접 조 목록(`interviewGroups`)은 숫자 조 먼저(길이 → 자연 순서), 그다음 이름 조 순으로 정렬한다.
- 연락처: 기본정보 → 회원정보 순. 휴대폰은 숫자만 `01`로 시작 10~11자리, 이메일은 `x@y.z` 형식이어야 그 채널 가능. 접수 시 채널별 시작 상태: 채널을 껐으면 `SKIPPED`(`CHANNEL_OFF`), 연락처가 없으면 `SKIPPED`(`NO_CONTACT`), 형식이 틀리면 `SKIPPED`(`INVALID_CONTACT`), 보낼 채널은 `PENDING`. 이후 전이는 [message-delivery](message-delivery.md).
- 화면에서 변수 칩으로 넣을 때도 입력란 글자 수 제한(제목 200·본문 10,000·SMS 2,000, 백엔드 @Size와 같음)을 넘으면 삽입하지 않는다. (`useVariableCursor.ts` `insertVariable`, 템플릿·발송 화면 공유)
- 발송 접수(`MessageSendService.send`)는 대상자를 접수 시점에 다시 조회해 요청한 `applicationIds`와의 교집합만 받는다. 조건이 그새 바뀌어 빠진 인원은 `excludedCount`로 알린다. 한 번에 최대 `recruit.message.max-recipients`(기본 3,000)명. 치환 후 SMS가 2,000byte를 넘는 수신자가 1명이라도 있으면 전체를 400으로 막는다. 치환은 접수 요청 시점에 하고, `MessageSend`·`MessageRecipient`에는 치환 전 원문만 저장하며 치환 결과는 이벤트로만 넘긴다.
- 테스트 발송(`MessageSendService.testSend`)은 요청 트랜잭션 안에서 디스패처(`MessageDispatcher.dispatch`)를 동기로 부른다. 메일 제목·SMS 앞에 `[테스트] `를 붙이고, 미리보기 중인 수신자(`previewApplicationId`)의 변수 값으로 치환한다(테스터 이름은 본문에 넣지 않는다). 테스터는 최대 5명. 응답은 접수 결과이고 디스패치 뒤 수신자를 다시 읽어 만든다. 화면은 `REQUESTED`가 있으면 이력 상세를 3초마다 최대 2분 다시 읽어 최종 결과로 바꾸고, 접수된 채널이 하나라도 있으면 테스트한 것으로 본다(`AdminMessageSendView.runTest`).
- 연락처 마스킹은 로그 전용이다: 게이트웨이 목업·호출 실패 로그는 `MessageContacts.maskEmail`·`maskPhone`을 쓰고 본문은 남기지 않는다(로컬 목업 메일 게이트웨이만 예외, [message-delivery](message-delivery.md)). 화면(발송·테스트 발송·이력)은 연락처를 가리지 않는다(2026-09-19 사용자 결정).
- 발송 요청이 접수되면 화면은 알림의 "이력 보기"로 `/admin/messages/history?sendId={id}`를 연다(이력 화면 동작은 [message-delivery](message-delivery.md)).
- 시스템 종류 템플릿: 메일 필수·SMS 저장 안 함, 인증 2종은 `#{인증번호}` 필수, 종류마다 기본 템플릿 1개는 삭제·기본 해제 불가(다른 템플릿을 기본으로 지정하면 기존 기본 전환 로직으로 바뀐다). 변수: `#{인증번호}`(인증 2종), `#{제출일시}`(제출 완료), `#{공고명}`은 관리자 5종 + 제출 완료, `#{이름}`·`#{채용사이트}`는 전체.

## 변경 레시피

- **변수 추가**: `MessageVariable`에 상수 추가(키·설명·허용 종류) → `MessageTemplateServiceTest`의 변수 개수·`MessageTemplateAdminControllerTest` 개수 단언 갱신 → 변수 값 계산은 `MessageVariableFormatter`의 `switch`에 추가(누락 시 컴파일 오류). 프론트는 카탈로그 API로 받으므로 수정 불필요.
- **종류 추가**: `MessageType` → `MessageVariable` 허용 종류 → 프론트 `types/admin/message.ts`의 `MessageType`·`messageTypes.ts`의 `MESSAGE_TYPES`. 시스템 종류면 `isSystem()`·`SYSTEM_MESSAGE_TYPES`·`SystemMessageTemplateInitializer`도 고친다.
- **메뉴**: 코드가 아니라 메뉴 관리 화면(`/admin/menus`)에서 등록한다. 그룹 "메시지" 아래 "메시지 발송"(`/admin/messages`), "발송 이력"(`/admin/messages/history`), "메시지 템플릿"(`/admin/messages/templates`).

## 검증

```bash
# 백엔드 (recruit_back/recruit_backend/)
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.MessageTemplate*" --tests "com.shinyoung.recruit.service.MessageRenderer*" --tests "com.shinyoung.recruit.service.MessageTarget*" --tests "com.shinyoung.recruit.service.MessageVariable*" --tests "com.shinyoung.recruit.service.MessageContacts*" --tests "com.shinyoung.recruit.service.MessageSendService*" --tests "com.shinyoung.recruit.controller.MessageTemplate*" --tests "com.shinyoung.recruit.controller.MessageSend*" --tests "com.shinyoung.recruit.config.SecurityConfigTest" --no-daemon
# 프론트 (recruit_front/)
npm run type-check
npx vitest run src/views/admin/message
```

## 함정·결정

- 엔티티 필드 `type`은 컬럼명을 `message_type`으로 지정했다(DB 예약어 충돌 회피).
- 변수 키를 한글로 둔 이유: 관리자가 본문에서 바로 읽을 수 있게. 서버 enum이 키의 단일 출처다.
- 운영 DB에 `message_template`·`message_send`·`message_recipient` 테이블 생성 SQL을 `recruit_back/recruit_backend/docs/ops/`에 둘지는 미결(설계서 17절 1번). 작성한다면 S4 엔티티 기준이다([message-delivery](message-delivery.md) 함정).
- 종류당 기본 템플릿 1개는 서비스가 조회 후 해제하는 방식이라, 두 관리자가 같은 종류를 동시에 기본으로 저장하면 기본이 2개 남을 수 있다. 저트래픽 관리자 화면이라 락·유니크 제약은 두지 않았다(필요하면 스키마 변경).
- `MessageTargetRepository`는 `Repository<JobApplication, Long>`이지만 JPQL로 `StageResult`·`InterviewParticipant`·`String`을 돌려준다(다른 카드 소유 리포지토리를 건드리지 않으려는 선택).
- `draftStartedAt`은 생성 시각이다. 섹션 저장은 `JobApplication.updatedAt`을 바꾸지 않아 "마지막 저장"으로는 쓸 수 없다.
- 직접 입력의 전형 결과 조건은 발표 완료·마감 전형만 허용한다(2026-09-19 사용자 결정). 발표 전 합불이 대상 선정으로 새지 않게 하려는 것.
- 대상 조회에는 인원 상한이 없다. 발송 상한 3,000명은 발송 접수 때 검사한다.
- 발송 접수는 쓰기 트랜잭션 안에서 대상을 재조회하고 수신자 행을 건별로 insert한다(최대 3,000건). 대량 발송이면 접수 응답이 느릴 수 있다(배치 insert 등 성능 개선은 미결).
- 테스트 발송은 동기라 접수 기록·직후 결과 반영이 요청 트랜잭션에 합류한다. 실제 게이트웨이로 바꿀 때 연결·응답 타임아웃이 필수다(없으면 요청 스레드가 오래 묶인다). 기록·반영 중 예외는 디스패처·결과 처리부가 로그만 남기고 삼키지만, 참여 중인 트랜잭션은 이미 rollback-only라 `testSend` 커밋 시점에 `UnexpectedRollbackException`으로 500이 난다(게이트웨이 호출은 이미 일어난 뒤). 발생 조건이 극히 드물어 수용한다.
- 결과 반영 bulk update가 `clearAutomatically = true`라 테스트 발송 중에 반영이 일어나면 영속성 컨텍스트가 비워진다. 그래서 `testSend`는 디스패치 뒤 수신자를 다시 읽어 응답을 만든다.
- `MessageSendServiceTest`는 `@RecordApplicationEvents`·`@MockitoBean`(`MailGateway`·`SmsGateway`)으로 게이트웨이 호출 없이 검증한다. 다른 `@SpringBootTest`와 `properties`·`@MockitoBean` 조합이 달라 별도 컨텍스트로 뜬다. 커밋 → 비동기 → 결과 전체 경로는 [message-delivery](message-delivery.md)의 `MessageSendAsyncFlowTest`.
- 프론트: 발송 요청이 응답 없이 끝나면(타임아웃 등) 서버에 멱등키가 없어 이미 접수됐을 수 있다. 이때는 확인 모달을 닫고 "발송 이력을 확인"하라고만 안내하고 재시도를 유도하지 않는다(`AdminMessageSendView.confirmSend`).
- 프론트: 테스트 발송 중 작성 내용이나 종류가 바뀌면 `testVersion`을 올려 늦게 도착한 응답을 버린다.
- 프론트: 미리보기(`MessagePreview`)는 스크롤 고정(sticky)을 쓰지 않는다. 고정하면 아래 테스트 발송 카드를 가리기 때문이다.
- 프론트: 테스트 수신자는 브라우저 `localStorage`(`recruit.message.testers`)에만 기억한다. 불러올 때도 추가할 때와 같은 규칙으로 다시 검사하고, 같은 연락처는 중복으로 추가할 수 없다.
- 프론트: 셀렉트 목록·표 선택 행의 선택·hover 색은 카드 소유가 아닌 `{FE}/App.vue`의 ant-design-vue 테마 토큰(`controlItemBgActive`·`controlItemBgActiveHover`·`controlItemBgHover`)에서 앱 전체 공통으로 정한다. 성공 배너(합격 확인 배너 등)의 배경·테두리도 같은 이유로 `colorSuccessBg`·`colorSuccessBorder` 계열 토큰을 밝게 지정한다. 기본값이 진한 `colorPrimary`에서 파생돼 칙칙하기 때문이며, 메시지 화면 전용 오버라이드는 두지 않는다(2026-09-20 사용자 결정).
- 프론트: 전형이 필요한 종류(결과 발표·면접 안내 2종, `requiresStage`)는 종류를 바꿀 때 고를 수 있는 전형이 있는 공고를 기본값으로 고른다. 앞에서부터 최대 5개 공고의 전형을 조회해 `defaultStageId`가 나오는 첫 공고를 쓰고, 없으면 첫 공고를 그대로 둔다(`AdminMessageSendView.pickPostingWithStage`). 최신 공고가 접수 중이라 발표된 전형이 없어 0명으로 시작하던 문제를 막는다.
- 프론트: 결과(합격·불합격 등)는 조건 바·수신자 드로어·미리보기·하단 발송 바·발송 확인 모달에서 같은 색 배지로 보여 준다(색·라벨은 `{FE}/types/admin/stage.ts`의 `STAGE_RESULT_STATUS_COLORS`·`STAGE_RESULT_STATUS_LABELS`, 전형결과 화면과 동일). 판정은 `messageCondition.ts`의 `resultConditionTag`·`recipientResultTag`가 한다. 결과 조건이 실제로 걸리는 경우에만 배지를 보여 준다(면접 2종·마감 임박은 없음, 직접 입력은 전형을 골랐을 때만).
- 프론트: 발송 확인 모달은 결과가 합격이면 초록, 불합격이면 빨간 배너로 "<결과> 안내를 N명에게 보냅니다"를 띄운다(`isFinalResult`). 보류·결시는 배지만 보여 준다. 합격·불합격을 잘못 골라 보내는 것을 막기 위한 장치다(2026-09-20 사용자 요청).
- 프론트: `MessageRecipientDrawer`는 좌우 24px 패딩, 선택 행 연한 색 오버라이드(`.ant-table-row-selected`)를 다른 목록 화면과 통일해 쓴다.
