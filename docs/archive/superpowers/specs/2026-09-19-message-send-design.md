# 메일·SMS 메시지 발송 설계서

- 작성일: 2026-09-19
- 상태: 승인(2026-09-19). 17절 1·3·4는 구현 계획 단계에서 확정
- S4 변경(2026-09-19): 발송 결과를 메시지큐로 나중에 받는 구조를 반영했다(7.4절). 발송·수신자 상태, 건수 계산 방식, 이력 화면, 테스트 발송 결과 표시가 바뀐다
- 화면 목업: `design/메시지-발송.html` (발송 · 발송 이력 · 메시지 템플릿 3개 화면, 클릭 동작 포함)
- 새 도메인 카드: `message` (구현 시 생성)

## 1. 목적과 범위

관리자가 지원자에게 메일·SMS를 보낸다. 메시지 종류를 고르면 대상자와 문구가 자동으로 채워지고, 수신자별 실제 값으로 미리보기한 뒤, 인사팀 담당자에게 테스트 발송을 하고 나서 실제로 발송한다. 문구는 관리자가 템플릿으로 만들어 두고 불러온다.

### 범위에 포함

| 항목 | 내용 |
|---|---|
| 메시지 종류 5개 | 결과 발표, 서류 마감 임박, 면접 일정·장소, 면접 공지, 직접 입력 |
| 대상자 자동 조회 | 종류·조건에 맞는 지원자를 서버가 조회. 관리자는 체크 해제만 할 수 있다 |
| 자동완성 | 종류별 기본 템플릿 자동 적용 + `#{변수}`를 수신자별 값으로 치환 |
| 채널 | 메일(제목·본문) + SMS(본문). 한 번 발송에 둘 다 또는 하나만 |
| 미리보기 | 메일 화면·휴대폰 화면 모양으로 수신자별 렌더링, 수신자 넘겨 보기 |
| 테스트 발송 | 담당자 이메일·휴대폰으로 현재 미리보기 내용 발송, `[테스트]` 표시 |
| 템플릿 관리 | 종류별 템플릿 등록·수정·삭제, 종류당 기본 템플릿 1개 |
| 발송 이력 | 발송 목록, 발송 원문, 수신자별 성공·실패·결과 수신 중·제외 |
| 발송 연동 | `MailGateway`·`SmsGateway` 인터페이스 + 로그만 남기는 목업 구현, 발송 결과 비동기 수신 처리부(거래 ID 기준) + 목업 결과 |

### 범위에서 제외

- 실제 SMTP·문자 솔루션 연동(사내 스펙 확정 후 구현체만 추가)
- 발송 결과를 받는 실제 소켓(메시지큐) 클라이언트(처리부 `DeliveryReportHandler`만 두고 스펙 확정 후 연결), 결과코드별 설명 매핑
- 예약 발송, 실패 건 재발송, 메일 첨부파일, 수신 거부 관리
- 발송 이력 엑셀 다운로드, 이력 화면 통계 카드(목업에는 있으나 제외. 필요하면 후속 작업)
- 다른 화면(전형결과·면접 스케줄링 등)에서 발송 화면 호출
- 서버 재시작으로 멈춘 발송(`SENDING`)의 자동 재개

## 2. 용어

| 용어 | 코드 | 설명 |
|---|---|---|
| 메시지 종류 | `MessageType` | `RESULT_ANNOUNCEMENT` 결과 발표 · `DEADLINE_REMINDER` 서류 마감 임박 · `INTERVIEW_SCHEDULE` 면접 일정·장소 · `INTERVIEW_NOTICE` 면접 공지 · `FREE` 직접 입력 |
| 종류 묶음 | (FE 표시용) | 공고 관련 = 결과 발표·마감 임박, 면접 안내 = 면접 일정·장소·면접 공지, 기타 = 직접 입력 |
| 템플릿 | `MessageTemplate` | 종류 1개에 속한 메일 제목·본문 + SMS 본문 묶음 |
| 기본 템플릿 | `defaultTemplate` | 종류당 최대 1개. 발송 화면에서 종류를 고르면 자동으로 불러온다 |
| 변수 | `MessageVariable` | 본문의 `#{이름}` 형식 자리표시자. 수신자별 값으로 치환 |
| 발송 | `MessageSend` | 발송 요청 1회. 조건·원문·발송자 보관(상태·건수는 조회 시 계산) |
| 수신자 | `MessageRecipient` | 발송 1회의 수신자 1명. 연락처·채널별 결과 보관 |
| 채널 결과 | `MessageDeliveryStatus` | `PENDING` 호출 전 · `REQUESTED` 결과 수신 중(솔루션 접수) · `SENT` 성공 · `FAILED` 실패 · `SKIPPED` 제외(연락처 없음·형식 오류·채널 끔) |
| 발송 상태 | `MessageSendStatus` | `SENDING` 발송 중 · `RESULT_PENDING` 결과 수신 중 · `COMPLETED` 완료. 저장하지 않고 계산(7.4절) |
| 거래 ID | `transactionId` | 솔루션 호출 1회(발송 단위)에 솔루션이 돌려준 ID. 결과를 매칭하는 유일한 키 |
| 발송 결과 | `DeliveryReport` | 메시지큐로 나중에 오는 거래 1건의 결과(거래 ID + 결과코드) |
| SMS 구분 | `SmsKind` | `SMS`(90byte 이하) · `LMS`(91~2000byte). 수신자별로 판정 |

## 3. 화면 설계

목업의 화면 구성을 따른다. 관리자 메뉴 그룹 "메시지"에 3개 메뉴를 둔다. 메뉴는 코드가 아니라 메뉴 관리 화면(`/admin/menus`)에서 등록한다.

| 라우트 name | 경로 | 화면 |
|---|---|---|
| `AdminMessageSend` | `/admin/messages` | 메시지 발송 |
| `AdminMessageHistory` | `/admin/messages/history` | 발송 이력 |
| `AdminMessageTemplates` | `/admin/messages/templates` | 메시지 템플릿 |

권한은 기존 관리자 라우트와 같은 `ADMIN_ROLES`(`ROLE_ADMIN`, `ROLE_RECRUIT_ADMIN`).

### 3.1 메시지 발송 (`/admin/messages`)

위에서 아래로:

1. **종류 카드 5개**. 묶음 라벨(공고 관련 · 면접 안내 · 기타) 아래 가로로 나열. 기본 선택은 결과 발표. 종류를 바꾸면 조건·대상자·템플릿·변수 칩을 모두 초기화한다.
2. **조건 바**. 종류별 필드(4절)와 오른쪽 끝 수신 인원 요약(`선택 n / 전체 m명`) + "대상자 보기·편집" 버튼. 아래 한 줄 안내 문구.
3. **작성 영역**(왼쪽)
   - 템플릿 셀렉트: 해당 종류의 템플릿 목록(기본 템플릿 ★ 표시) + "새로 작성(빈 양식)". 고르면 제목·본문·SMS를 채운다.
   - 템플릿에서 바꾼 내용이 있으면 "템플릿에서 수정됨" 표시 + "되돌리기". "템플릿으로 저장"은 기존 템플릿 덮어쓰기 또는 새 이름으로 저장(템플릿 API 사용).
   - 채널 탭 `메일` · `SMS`. 탭마다 켜기·끄기 스위치. 끈 채널은 입력란 대신 "이번 발송에서 제외" 안내.
   - 메일 탭: 제목, 본문(textarea, 일반 텍스트). SMS 탭: 본문 + byte 카운터(미리보기 수신자 기준) + SMS/LMS 판정 표시(선택 수신자 중 최대 byte 기준).
   - 변수 칩: 해당 종류에서 쓸 수 있는 변수만. 누르면 마지막으로 포커스된 입력란의 커서 위치에 `#{변수}` 삽입.
   - 여기서 고친 내용은 이번 발송에만 적용되고 템플릿은 바뀌지 않는다.
4. **미리보기**(오른쪽)
   - `메일` · `SMS` 전환. 입력란에 포커스하면 해당 채널로 자동 전환.
   - 수신자 이동(◀ ▶, `k / n`), 수신자 이름과 종류별 부가정보(결과·조·면접 시각 등).
   - 메일: 제목, 보낸사람·받는사람, 고정 브랜드 머리글·바닥글 안에 본문. SMS: 휴대폰 모양, `[Web발신]` 머리말, 발신번호, SMS/LMS 표시.
   - 치환된 값은 강조 표시. 값이 없는 변수는 빨간 강조로 `#{변수}` 그대로 보여 준다.
   - 해당 수신자에게 그 채널 연락처가 없으면 "연락처 없어 제외" 안내.
5. **테스트 발송 카드**(미리보기 아래)
   - 이름 + 이메일 또는 휴대폰 입력 → 칩으로 추가(최대 5명). 최근 테스트 수신자는 브라우저 `localStorage`에 기억(실패해도 동작에 영향 없게 try/catch).
   - "현재 미리보기 내용으로 테스트 발송": 켜진 채널로, 미리보기 중인 수신자의 변수 값으로 보낸다. 담당자·채널별 결과를 보여 준다. 처음에는 "결과 수신 중"이고, 이력 상세(`GET /admin/messages/history/{sendId}`)를 3초 간격으로 다시 읽어 결과가 오면 성공·실패로 바꾼다(최대 2분, 내용이 바뀌거나 화면을 떠나면 중단). 솔루션이 접수한 채널이 하나라도 있으면 테스트한 것으로 본다.
6. **하단 고정 바**: 종류, 수신 대상 수, 채널별 발송 건수(연락처 없는 인원 빨간 표시), 테스트 미실시 경고, "발송하기".
7. **수신자 드로어**: 수험번호(지원서 id), 이름, 종류별 열(결과 / 조·면접 일시 / 작성 시작 일시), 휴대폰, 이메일, 채널 가능 여부 태그. 체크박스로 제외·복구. 두 연락처가 모두 없으면 선택 불가. "연락처 누락만 보기" 필터, 이름·수험번호 검색(클라이언트 필터).
8. **발송 확인 모달**: 종류, 공고·전형·조건, 수신 인원, 채널별 건수(SMS/LMS 구분), 템플릿 이름(수정됨 표시). 테스트 미실시 경고. "대상자와 내용을 확인했습니다" 체크해야 발송 버튼이 동작. 버튼 문구 `n명에게 발송`. 요청 중에는 버튼을 잠근다.
9. 발송 요청이 접수되면 토스트 후 발송 이력 상세로 이동할 수 있는 링크를 보여 준다. 발송은 서버에서 비동기로 진행된다(7절).

목업 대비 변경: 마감 임박 대상자 드로어의 "작성 진행률"은 "작성 시작 일시"로 바꾼다(진행률은 지원서마다 완성도 계산이 필요해 비용이 크다).

### 3.2 발송 이력 (`/admin/messages/history`)

- 필터: 기간(발송일, 기본 최근 30일), 종류, 공고, 구분(실발송+테스트 / 실발송만 / 테스트만). 조회 버튼.
- 목록(페이지): 발송일시, 종류 태그(+ 테스트 태그), 공고·전형, 메일 제목(메일을 끈 발송은 SMS 본문 앞부분), 채널별 건수 텍스트("N건", 끈 채널은 "제외"), 대상 수, 결과 칸(상태 태그 "발송 중" / "결과 수신 중" / "완료", 지연이면 "발송 중단" / "결과 미수신"(7.4절) + 두 채널 합계 "성공 n · 실패 n · 수신 중 n" 텍스트, 막대 그래프 아님), 발송자. 끈 채널의 수신자는 모두 `SKIPPED`라 "제외"는 건수가 아니라 `mailEnabled`·`smsEnabled`로 판단한다.
- 행 클릭 → 상세 드로어: 종류·공고·전형·조건 요약, 대상·채널 집계, 발송 원문(변수 치환 전 제목·본문·SMS), 수신자별 표(이름, 이메일, 휴대폰, 메일 결과, SMS 결과 + 실패 사유·결과코드). 파기된 수신자는 "(파기됨)". 완료가 아니면 드로어가 열려 있는 동안 5초 간격으로 다시 읽는다. 목록은 새로고침 버튼으로 갱신한다.

### 3.3 메시지 템플릿 (`/admin/messages/templates`)

- 왼쪽 목록: 종류 묶음·종류별 그룹, 그룹마다 개수. 항목은 이름 + 메일 제목, 기본 템플릿은 ★와 "기본" 태그. 이름 검색·종류 필터(클라이언트).
- 오른쪽 편집기: 이름, 메시지 종류, "이 종류의 기본 템플릿으로 사용" 체크, 메일 제목·본문, SMS 본문(+ 변수 제외 byte 수와 SMS/LMS 예상), 변수 칩(선택한 종류의 변수만). 버튼: 삭제, 복제, 저장. "새 템플릿"은 빈 편집기로 시작.
- 메일·SMS 중 하나만 채워도 저장 가능. 둘 다 비면 저장 불가.

## 4. 종류별 대상자 규칙

모든 종류 공통: 공고 1개를 반드시 고른다. 파기된 지원서(`purgeResult != null`)와 철회(`WITHDRAWN`) 지원서는 항상 제외한다. 대상자 1명 = 지원서 1건(`applicationId`).

| 종류 | 조건 필드 | 선택 가능 범위 | 대상자 |
|---|---|---|---|
| 결과 발표 | 공고, 전형, 결과(`ALL`·`PASSED`·`FAILED`·`HOLD`·`ABSENT`) | 전형 상태 `RESULT_ANNOUNCED`·`CLOSED`만 | 그 전형의 `StageResult` 중 결과 조건에 맞는 지원서. 결과 기본값 `PASSED` |
| 서류 마감 임박 | 공고 | 공고 상태 `PUBLISHED`이고 지금이 접수 기간(`receptionStartDateTime` ≤ now < `receptionEndDateTime`) 안 | 그 공고의 `DRAFT` 지원서 |
| 면접 일정·장소 | 공고, 면접 전형, 조(전체 또는 `groupName` 1개) | 전형 유형 `FIRST_INTERVIEW`·`SECOND_INTERVIEW`·`FINAL_INTERVIEW` | 그 전형의 `CONFIRMED` 면접에 `CANDIDATE`·`ASSIGNED`로 배정된 지원서 |
| 면접 공지 | 면접 일정·장소와 같음 | 같음 | 같음 |
| 직접 입력 | 공고, 지원 상태(`SUBMITTED` 기본·`DRAFT`·`ALL`), 전형·결과(선택) | 전형을 고르면 발표 완료·마감 전형만(2026-09-19 결정) | 조건에 맞는 지원서. 전형을 고르면 그 전형 결과로 한 번 더 거른다 |

- 결과 `ALL`은 `PASSED`·`FAILED`·`HOLD`·`ABSENT`를 뜻한다. `PENDING`·`WITHDRAWN` 결과는 어떤 조건에서도 대상이 아니다.
- 면접: 한 지원서가 같은 전형에서 확정 면접 2건 이상에 배정되면 시작 시각이 가장 이른 면접의 값을 쓴다.
- 조 목록은 대상자 조회 응답에 함께 준다(해당 전형 확정 면접의 `groupName` 목록).
- 선택 불가 조건(발표 전 전형, 마감된 공고 등)은 셀렉트에서 비활성으로 보여 주고, 서버도 400으로 막는다.

### 연락처

- 이메일·휴대폰은 지원서 기본정보(`ApplicationBasicInfo.email`·`mobilePhone`)를 먼저 쓰고, 없으면 회원정보(`Applicant.email`·`phoneNumber`)를 쓴다. 마감 임박 대상(`DRAFT`)은 기본정보가 비어 있는 경우가 많아 회원정보로 채워진다.
- 이름도 기본정보 `nameKorean` → `Applicant.userName` 순서.
- 휴대폰은 숫자만 남겨 `01`로 시작하는 10~11자리여야 유효. 이메일은 `@`와 도메인이 있어야 유효. 유효하지 않으면 그 채널은 `SKIPPED`(사유 `INVALID_CONTACT`), 없으면 `SKIPPED`(사유 `NO_CONTACT`).

## 5. 변수

형식은 `#{키}`. 키는 한글 이름 그대로 쓴다(관리자가 읽기 쉽게). 서버 enum `MessageVariable`이 키·표시명·허용 종류·값 계산의 단일 출처이고, 프론트는 변수 카탈로그 API로 받아 쓴다.

| 키 | 값 | 형식 예 | 허용 종류 |
|---|---|---|---|
| `이름` | 4절 이름 | 김민준 | 전체 |
| `공고명` | `JobPosting.title` | 2026 하반기 신입사원 공개채용 | 전체 |
| `채용사이트` | 설정 `recruit.message.site-url` | https://recruit.example.co.kr | 전체 |
| `전형명` | `Stage.stageName` | 서류전형 | 결과 발표, 면접 2종 |
| `마감일시` | `receptionEndDateTime` | 9월 22일(화) 18:00 | 마감 임박 |
| `남은기간` | 오늘(`Clock`)부터 마감일까지 날짜 차이 | D-3, 당일이면 D-DAY | 마감 임박 |
| `면접일시` | `Interview.startDateTime` | 2026-10-14(수) 09:30 | 면접 2종 |
| `도착시각` | `Interview.arrivalDateTime` | 09:10 | 면접 일정·장소 |
| `면접장소` | `locationName` + 공백 + `roomName`(있는 것만) | 본사 12층 대회의실 A | 면접 2종 |
| `면접방식` | `InterviewMethod` 한글 라벨 | 대면 · 온라인 · 대면+온라인 · 기타 | 면접 일정·장소 |
| `접속링크` | `onlineMeetingUrl` | https://… | 면접 2종 |
| `조` | `groupName`. 숫자만이면 뒤에 "조" | 1조 | 면접 일정·장소 |

- 날짜 형식은 `Asia/Seoul` 기준, 요일은 한글 한 글자.
- 값이 없는 변수(예: 도착시각 미입력)는 빈 문자열로 치환한다. 대상자 조회 응답에 수신자별 "값 없는 변수" 목록을 주고, 미리보기는 빨간 강조, 발송 확인 모달은 "값이 없는 변수가 있는 수신자 n명" 경고를 띄운다(발송은 막지 않음).
- 종류에 허용되지 않은 변수나 없는 키가 본문에 있으면 템플릿 저장·테스트 발송·발송 모두 400("사용할 수 없는 변수: #{…}").

## 6. 렌더링 규칙

한 규칙을 서버(`MessageRenderer`)와 프론트(`messageRender.ts`)가 똑같이 구현한다. 프론트는 입력할 때마다 즉시 미리보기를 그리기 위해, 서버는 실제 발송 내용의 기준으로 쓴다. 규칙이 단순해서 중복 비용이 작고, 양쪽 단위 테스트에 같은 예시를 둔다.

1. 치환: 정규식 `#\{([^}]+)\}`로 찾은 키를 수신자의 변수 값으로 바꾼다. 치환은 한 번만 한다(값 안의 `#{…}`는 다시 치환하지 않음).
2. SMS byte: 문자 코드 127 이하 1byte, 그 밖(한글 등) 2byte. `[Web발신]` 머리말은 통신사가 붙이므로 세지 않는다.
3. SMS 구분: 치환 후 90byte 이하 `SMS`, 91~2000byte `LMS`, 2000 초과는 400(수험번호와 인원을 메시지에 포함. 개인정보 원문 금지 규칙에 따라 이름은 넣지 않음).
4. 메일 본문: 일반 텍스트로 받는다. HTML 이스케이프 후 줄바꿈을 `<br>`로 바꿔 고정 레이아웃 템플릿(`{BR}/templates/message-mail.html`, Thymeleaf) 본문 자리에 넣는다. 머리글(신영증권 채용)·바닥글(발신 전용 안내)은 레이아웃에 고정. 링크 자동 변환은 하지 않는다.
5. 제한: 메일 제목 200자, 메일 본문 10,000자, SMS 원문 2,000자. 한 번 발송 대상 최대 `recruit.message.max-recipients`(기본 3,000명).
6. 테스트 발송: 메일 제목과 SMS 본문 앞에 `[테스트] `를 붙인다(byte 계산에도 포함).

## 7. 발송 흐름

### 7.1 실제 발송

```
[FE] 발송 확인 모달 → POST /admin/messages/send
[BE] MessageSendService.send (트랜잭션 1개)
     1. 요청 검증(종류·조건 선택 가능 여부, 채널 1개 이상, 내용 길이, 변수 허용 여부)
     2. 조건으로 대상자 다시 조회 → 요청의 applicationIds와 교집합만 수신자로 확정
        (화면을 연 뒤 결과 정정 등으로 조건에서 빠진 사람은 제외하고 excludedCount로 알림)
     3. 수신자별 연락처 판정 → 채널별 PENDING 또는 SKIPPED
     4. 수신자별 SMS 치환 결과로 2000byte 초과 검사(초과 시 전체 400)
     5. MessageSend(상태는 저장하지 않고 조회 때 계산) + MessageRecipient 저장, MessageSendRequestedEvent 발행
     6. 응답 { sendId, status: SENDING, recipientCount, excludedCount }
[BE] MessageDispatcher (@TransactionalEventListener AFTER_COMMIT + @Async)
     이벤트에 담긴 수신자별 치환 결과(요청 시점에 계산, DB에 저장하지 않음)
     → 발송 단위(7.1.1)로 묶음 → 단위마다 게이트웨이 1회 호출
     → 접수되면 그 단위 수신자들의 REQUESTED + 거래 ID, 접수 실패면 FAILED(+사유) 저장
       (발송 단위 1개 = DB 트랜잭션 1개)
     → 저장 직후 먼저 도착해 보관 중인 결과가 있으면 반영(7.4)
[BE] 결과 수신(7.4): 거래 ID로 REQUESTED 수신자를 SENT / FAILED(결과코드)로 바꿈
     발송 상태·건수는 저장하지 않고 조회할 때 계산
```

#### 7.1.1 발송 단위 (게이트웨이 트랜잭션)

발송 솔루션 제약: **1회 호출(트랜잭션) = 메시지 내용 1개 + 수신자 최대 10명.**

- 채널별로 치환 결과가 **완전히 같은** 수신자끼리 묶고, 묶음을 10명씩 나눈다. 비교 기준은 메일 = 제목 + 본문, SMS = 본문(SMS/LMS 구분 포함).
- 변수로 사람마다 내용이 달라지면(보통 `#{이름}`이 있으므로 대부분) 1명당 1회 호출이 된다.
- 변수가 없거나 값이 같은 공지는 최대 10명씩 묶여 호출 수가 줄어든다.
- 호출 결과와 나중에 오는 발송 결과 모두 그 단위의 수신자 전원에게 똑같이 적용한다(접수되면 전원 `REQUESTED` + 같은 거래 ID, 결과가 오면 전원 `SENT` 또는 `FAILED` + 같은 결과코드, 접수 실패면 전원 `FAILED` + 같은 사유).
- 순서: 메일 단위를 모두 보낸 뒤 SMS 단위를 보낸다. 3,000명 × 2채널이면 최대 6,000회 호출이며 디스패처 스레드에서 순차 처리한다.

- 비동기인 이유: 프론트 Axios 타임아웃이 10초이고, 실제 SMTP는 수백 건이면 수십 초가 걸릴 수 있다.
- 변수 값은 발송 요청 시점(2단계 재조회 직후)에 계산하고, 치환 결과는 이벤트에 담아 디스패처로 넘긴다(메모리, DB 미저장). 관리자가 확인한 시점의 값이 그대로 나가고, 디스패처가 조건을 다시 검증하다 멈추는 일이 없다(2026-09-19 S3 계획에서 변경. 이전안: 디스패치 시점 재계산).
- 서버가 재시작되면 이벤트가 사라져 남은 `PENDING`은 그대로 남는다(자동 재개는 범위 밖).
- 게이트웨이 호출 예외는 그 단위의 수신자·채널을 `FAILED`(`GATEWAY_ERROR`)로 기록하고 계속 진행한다. 단위 기록(`recordUnit`, 직후 `applyBuffered` 포함)이 예외로 끝나면 경고 로그(채널·인원·예외 클래스만)를 남기고 다음 단위를 계속 처리하며, 기록하지 못한 단위의 수신자는 `PENDING`으로 남는다. 남은 `PENDING`이 있으면 발송 상태는 `SENDING`으로 계산되고, 1시간이 지나면 이력에 "발송 중단"으로 표시된다(자동 재개는 범위 밖). 게이트웨이가 이미 접수한 단위라면 거래 ID가 저장되지 않아 결과를 매칭할 수 없고, 실제로는 발송됐을 수 있다(드문 락 타임아웃·DB 오류라 수용).
- 중복 발송 방지: 모달 확인 체크 + 요청 중 버튼 잠금. 서버 멱등키는 두지 않는다.

### 7.2 테스트 발송

- `POST /admin/messages/test`. 요청에 발송과 같은 내용·종류·조건 + `previewApplicationId` + 테스트 수신자(최대 5명, 각자 이메일 또는 휴대폰 1개 이상).
- `previewApplicationId`는 현재 조건의 대상자여야 한다(아니면 400). 그 지원서의 변수 값으로 치환한다.
- 솔루션 호출은 동기로 한다. 테스트 수신자 전원이 같은 내용(미리보기 수신자 값)을 받으므로 채널당 호출 1회(수신자 최대 5명)다. `MessageSend(test = true)` + 테스트 수신자 `MessageRecipient`(`jobApplication` 없음)로 이력에 남긴다. 응답에는 담당자·채널별 접수 결과(`REQUESTED` · `FAILED` · `SKIPPED`)를 주고, 최종 결과는 실제 발송과 같이 7.4절로 나중에 온다. 화면은 이력 상세를 다시 읽어 갱신한다(3.1-5).
- 테스트 여부는 서버가 강제하지 않는다(화면 경고만).

### 7.3 발송 연동(목업)

```java
// 호출 1회 = 내용 1개 + 수신자 1~10명(DeliveryUnit.MAX_RECIPIENTS = 10). 10명 제한은 호출 측(DeliveryUnit.group)이 지킨다
public interface MailGateway { GatewayResult send(MailMessage message, List<String> toAddresses); } // message: fromName, fromAddress, subject, html, text
public interface SmsGateway  { GatewayResult send(SmsMessage message, List<String> toNumbers); }   // message: callbackNumber, body, kind(SMS/LMS)
public record GatewayResult(boolean accepted, String transactionId, String failureReason) {} // 접수 결과. 최종 결과는 7.4
```

- 게이트웨이는 수신자 묶기를 모른다. 묶기(7.1.1)는 `MessageDispatcher`의 책임이다.
- 메일 구현체는 한 호출의 수신자에게도 따로 보낸다(수신자별 To 또는 BCC). 지원자끼리 주소가 보이면 안 된다.
- 실패 사유는 평문으로 저장되고 테스트 발송 응답에 나가므로 주소·번호 없는 코드로 준다. 비어 있으면 `GATEWAY_ERROR`로 기록한다.
- 접수되면 거래 ID는 필수다. 비어 있으면 결과를 매칭할 수 없으므로 `FAILED`(`GATEWAY_ERROR`)로 기록하고 경고 로그를 남긴다.

- 기본 구현 `LoggingMailGateway`·`LoggingSmsGateway`: 항상 접수 성공(가짜 거래 ID)을 돌려주고 3초 뒤 가짜 결과를 보낸다(7.4). 로그에는 마스킹한 수신자(`010-****-3307`, `m***@example.com`), 채널, byte 수만 남기고 본문은 남기지 않는다.
- 선택은 `recruit.message.gateway`(기본 `logging`). 실제 구현(SMTP, 문자 에이전트 테이블 등)은 사내 스펙 확정 후 `@ConditionalOnProperty`로 추가한다. SMTP를 쓰게 되면 `spring-boot-starter-mail` 의존성 추가가 필요하다(그때 승인 받음).

### 7.4 발송 결과 수신 (2026-09-19 S4 변경)

발송 솔루션은 호출(트랜잭션)을 받으면 거래 ID를 돌려주고, 실제 발송 결과는 나중에 메시지큐(소켓)로 보낸다.

- 결과는 **거래 1건당 1개**(거래 ID + 결과코드)다. 그 거래의 수신자(최대 10명) 전원에게 같은 결과를 적용한다. 수신자별 거래 ID가 따로 있는지는 스펙 미확인(17절 7번).
- 메일·SMS 모두 같은 솔루션이라 같은 방식으로 처리한다.
- 솔루션은 결과를 늦더라도 반드시 1번 보낸다. 우리 서버가 내려가 있었어도 소켓이 다시 연결되면 받는다. 수신 확인(ack)은 없고, 같은 결과가 두 번 오지 않는다(그래도 반영은 멱등으로 한다).
- 발송 요청에 우리 쪽 키를 넣을 수 없으므로 매칭은 거래 ID로만 한다. 연락처는 AES 무작위 IV 암호화라 조회 키로 쓸 수 없다.
- 서버는 1대로 운영한다.

```
[솔루션 호출] 접수 성공 → 그 단위 수신자·채널 REQUESTED + 거래 ID 저장(단위 1개 = DB 트랜잭션 1개)
              접수 실패(호출 예외·거절) → FAILED + 사유(GATEWAY_ERROR 등). 이 거래의 결과는 오지 않는다
[결과 수신]   소켓 클라이언트 → DeliveryReportHandler.handle(DeliveryReport{ transactionId, resultCode })
              → 거래 ID가 같고 아직 REQUESTED인 수신자·채널만 SENT 또는 FAILED(사유 = 결과코드)
```

- 성공 판정: 결과코드가 `recruit.message.success-result-codes`(기본 `0000`, 17절 6번)에 있으면 `SENT`, 아니면 `FAILED`이고 사유에 결과코드를 저장한다. 코드별 설명은 스펙 확정 후 추가하고, 그전에는 코드 원문을 보여 준다.
- 먼저 도착한 결과: 우리가 거래 ID를 커밋하기 전에 결과가 올 수 있다. 짝을 못 찾은 결과는 메모리(`DeliveryReportBuffer`)에 잠시 보관한다. 처리부는 결과를 버퍼에 넣은 뒤 반영을 시도하고, 반영되면(또는 이미 반영된 거래면) 버퍼에서 뺀다. 디스패처는 단위를 기록한 직후 그 거래 ID의 보관 결과를 반영한다. 1분마다 버퍼를 다시 시도하고, 10분이 지나도 짝이 없으면 거래 ID만 경고 로그로 남기고 버린다.
- 이미 반영된 거래의 결과가 다시 오면 바뀌는 행이 없으므로 무시한다.
- 실제 소켓 클라이언트는 범위 밖이다. 스펙이 확정되면 받은 메시지를 `DeliveryReport`로 바꿔 `DeliveryReportHandler.handle`만 호출하면 된다.
- 목업: `LoggingMailGateway`·`LoggingSmsGateway`는 가짜 거래 ID(UUID)로 접수 성공을 돌려주고, 3초 뒤 스프링 `TaskScheduler`로 가짜 결과를 처리부에 넘긴다. 결과코드는 `0000`, 수신자 연락처에 `fail`이 들어 있으면 `9999`(화면 확인용).

발송 상태는 저장하지 않고, 조회할 때 수신자 채널 상태로 계산한다(`MessageSendStatus`).

| 상태 | 조건 | 화면 |
|---|---|---|
| `SENDING` | 채널 상태가 `PENDING`인 수신자가 있음 | 발송 중 |
| `RESULT_PENDING` | `PENDING`은 없고 `REQUESTED`가 있음 | 결과 수신 중 |
| `COMPLETED` | `PENDING`·`REQUESTED` 모두 없음 | 완료 |

- 지연 표시: 요청 후 `recruit.message.result-wait-minutes`(기본 60분)가 지났는데 완료가 아니면 `delayed = true`. 화면은 `RESULT_PENDING`이면 "결과 미수신", `SENDING`이면 "발송 중단"으로 표시한다. DB 값은 바꾸지 않고, 결과가 오면 정상 표시로 바뀐다. 시간이 지났다고 실패로 처리하지 않는다(발송은 반드시 나가고 결과도 반드시 온다).
- 건수(성공·실패·수신 중·제외)도 조회할 때 수신자 행을 모아 센다. 결과가 여러 스레드에서 나눠 들어오므로 저장된 집계를 고치는 방식은 쓰지 않는다.

## 8. 데이터 모델

모두 `BaseEntity` 상속, `@Setter` 없음, 생성은 `create(...)`. 새 테이블 3개.

### `MessageTemplate`

| 필드 | 타입 | 비고 |
|---|---|---|
| `id` | Long | PK |
| `type` | `MessageType` | not null, `@Index` |
| `name` | String(100) | not null |
| `defaultTemplate` | boolean | 종류당 최대 1개(서비스가 보장: 기본으로 지정하면 같은 종류의 기존 기본을 해제) |
| `mailSubject` | String(200) | null 가능 |
| `mailBody` | LONGTEXT | null 가능 |
| `smsBody` | String(2000) | null 가능 |

- 메일(제목+본문)과 SMS 중 하나 이상은 있어야 한다. 메일은 제목·본문을 함께 채우거나 함께 비운다.
- 삭제는 행 삭제. 이력은 템플릿 이름과 원문을 복사해 두므로 영향 없다.

### `MessageSend`

| 필드 | 타입 | 비고 |
|---|---|---|
| `id` | Long | PK |
| `type` | `MessageType` | not null |
| `test` | boolean | 테스트 발송 여부 |
| `jobPosting` | `JobPosting` (N:1 LAZY) | not null |
| `stage` | `Stage` (N:1 LAZY) | 결과 발표·면접·직접 입력(전형 선택 시) |
| `conditionSummary` | String(200) | 화면 표시용 조건 요약(예: `서류전형 · 합격`, `1차 면접 · 1조`) |
| `templateId` · `templateName` | Long · String(100) | 사용한 템플릿(외래키 아님, 이름 복사) |
| `mailEnabled` · `smsEnabled` | boolean | 채널 사용 여부 |
| `mailSubject` · `mailBody` · `smsBody` | String(200) · LONGTEXT · String(2000) | 치환 전 원문 |
| `senderLoginId` · `senderName` | String | 발송 관리자(`CurrentEmployeeService`) |
| `recipientCount` | int | |
| `requestedAt` | LocalDateTime | |

인덱스: `requestedAt`, `(jobPosting, requestedAt)`.

- S4 변경: S3에서 저장하던 `status`·채널별 집계 6개·`completedAt`을 없앤다. 결과가 거래마다 나중에 들어오므로 상태·건수는 조회할 때 수신자 채널 상태로 계산한다(7.4).

### `MessageRecipient`

| 필드 | 타입 | 비고 |
|---|---|---|
| `id` | Long | PK |
| `messageSend` | `MessageSend` (N:1 LAZY) | not null, `@Index` |
| `jobApplication` | `JobApplication` (N:1 LAZY) | 테스트 수신자는 null, `@Index` |
| `recipientName` | String | **AES 암호화** |
| `email` · `phone` | String | **AES 암호화**, 발송 시점 연락처 |
| `mailStatus` · `smsStatus` | `MessageDeliveryStatus` | |
| `mailTransactionId` · `smsTransactionId` | String(100) | 솔루션 거래 ID. 접수 때 저장하는 결과 매칭 키, `@Index` |
| `mailFailureReason` · `smsFailureReason` | String(200) | `NO_CONTACT` · `INVALID_CONTACT` · `CHANNEL_OFF` · 게이트웨이 접수 실패 사유 · 발송 결과코드(실패일 때) |
| `smsKind` | `SmsKind` | SMS 대상일 때만 |
| `processedAt` | LocalDateTime | 마지막 상태 변경 시각(접수·결과 수신) |

- 수신자별 치환 결과는 저장하지 않는다. 이력의 "무엇을 보냈나"는 치환 전 원문 + 조건 + 수신자 목록으로 본다(저장량과 개인정보 보관을 줄이기 위해). 수신자별 정확한 본문은 재현하지 않는다.
- `MessageSend`→`MessageRecipient` cascade는 두지 않는다(개인정보 포함 테이블, 백엔드 규칙).

### enum (`enumeration` 패키지)

`MessageType`, `MessageVariable`(키·라벨·허용 종류), `MessageSendStatus`(계산값), `MessageDeliveryStatus`, `MessageChannel`, `SmsKind`.

## 9. API 계약 (초안 🟡)

경로는 `/api` 생략. 권한은 모두 `/api/admin/**` 규칙(`ADMIN`, `RECRUIT_ADMIN`)을 그대로 따른다. HTTP는 GET·POST만.

| 상태 | 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|---|
| 🟡 | GET | /admin/messages/variables | 없음 | `List<MessageVariableResponse>` `{ key, label, types[] }` |
| 🟡 | GET | /admin/message-templates | query `type?` | `List<MessageTemplateResponse>` 종류·기본 우선·이름순 |
| 🟡 | GET | /admin/message-templates/{id} | 없음 | `MessageTemplateResponse` |
| 🟡 | POST | /admin/message-templates | `{ type, name, defaultTemplate, mailSubject?, mailBody?, smsBody? }` | `MessageTemplateResponse` |
| 🟡 | POST | /admin/message-templates/{id} | 위와 같음 | `MessageTemplateResponse` |
| 🟡 | POST | /admin/message-templates/{id}/delete | 없음 | `null` |
| 🟡 | GET | /admin/messages/targets | query `type, jobPostingId, stageId?, resultStatus?, interviewGroup?, applicationStatus?` | `MessageTargetResponse` |
| 🟡 | POST | /admin/messages/test | `MessageTestSendRequest` | `MessageTestSendResponse` |
| 🟡 | POST | /admin/messages/send | `MessageSendRequest` | `{ sendId, status, recipientCount, excludedCount }` |
| 🟡 | GET | /admin/messages/history | query `from?, to?, type?, jobPostingId?, test?(없으면 전체, true 테스트만, false 실발송만), page(0), size(20)` | `PageResponse<MessageSendSummaryResponse>` 발송일시 desc |
| 🟡 | GET | /admin/messages/history/{sendId} | 없음 | `MessageSendDetailResponse` |

필드 요약(정확한 타입·검증은 DTO가 기준):

- `MessageTemplateResponse`: `{ id, type, name, defaultTemplate, mailSubject, mailBody, smsBody, updatedAt }`
- `MessageTargetResponse`: `{ recipients[], interviewGroups[] }`. recipient = `{ applicationId, name, email, phone, mailAvailable, smsAvailable, resultStatus?, interviewGroup?, interviewDateTime?, draftStartedAt?, variables{키: 값}, missingVariables[] }`
- 발송 내용 공통 필드 `content`: `{ templateId?, mailEnabled, smsEnabled, mailSubject?, mailBody?, smsBody? }`
- `MessageSendRequest`: `{ type, jobPostingId, stageId?, resultStatus?, interviewGroup?, applicationStatus?, applicationIds[], content }`
- `MessageTestSendRequest`: `{ type, jobPostingId, stageId?, resultStatus?, interviewGroup?, applicationStatus?, previewApplicationId, testers[{ name, email?, phone? }], content }`
- `MessageTestSendResponse`: `{ sendId, results[{ name, channel, status, failureReason? }] }`. status는 접수 결과(`REQUESTED` · `FAILED` · `SKIPPED`)이고 최종 결과는 history 상세로 확인한다
- `MessageSendSummaryResponse`: `{ id, requestedAt, type, test, jobPostingTitle, stageName?, conditionSummary, title(메일 제목 또는 SMS 앞 40자), mailEnabled, smsEnabled, recipientCount, mail{ pending, requested, sent, failed, skipped }, sms{ 〃 }, status(계산값), delayed, senderName }`
- `MessageSendDetailResponse`: 요약 + `{ templateName, mailSubject, mailBody, smsBody, recipients[{ name, email, phone, mailStatus, mailFailureReason, smsStatus, smsFailureReason, smsKind }] }`. 파기된 수신자는 name·email·phone이 null

## 10. 백엔드 구성 (`{BE}` 기준)

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `controller/MessageTemplateAdminController.java` | 템플릿 CRUD 5개 |
| controller | `controller/MessageSendAdminController.java` | 변수 카탈로그·대상자·테스트·발송 4개 |
| controller | `controller/MessageHistoryAdminController.java` | 이력 목록·상세 2개 |
| service | `service/MessageTemplateService.java` | 템플릿 검증·기본 템플릿 단일화 |
| service | `service/MessageTargetService.java` | 종류별 대상자 조회·연락처 판정·변수 값 계산 |
| service | `service/MessageRenderer.java` | 치환·byte·SMS 구분·변수 허용 검사·메일 HTML(Thymeleaf) |
| service | `service/MessageSendService.java` | 발송 생성·테스트 발송 |
| service | `service/MessageDispatcher.java` | 비동기 디스패치(접수 기록) |
| service | `service/DeliveryReport.java` · `DeliveryReportHandler.java` · `DeliveryReportBuffer.java` | 발송 결과 수신·거래 ID 매칭·먼저 도착한 결과 보관(7.4) |
| service | `service/MockDeliveryReportScheduler.java` | 목업 게이트웨이의 가짜 결과(`recruit.message.gateway=logging`일 때만) |
| service | `service/MessageHistoryService.java` | 이력 조회(읽기 전용) |
| service | `service/MailGateway.java` · `SmsGateway.java` · `GatewayResult.java` · `MailMessage.java` · `SmsMessage.java` | 연동 인터페이스·값 객체 |
| service | `service/LoggingMailGateway.java` · `LoggingSmsGateway.java` | 목업 구현 |
| config | `config/MessageProperties.java` | `recruit.message.*` |
| config | `config/AsyncConfig.java` | `@EnableAsync`만. 실행기는 스프링 부트 기본 `applicationTaskExecutor`를 쓴다(별도 Executor 빈을 만들면 부트 기본 실행기가 빠져 파일 스트리밍 응답 등에 영향) |
| entity · repository | `MessageTemplate` · `MessageSend` · `MessageRecipient` + 각 Repository | |
| dto | `dto/request/Message*Request.java`, `dto/response/Message*Response.java` | 9절 |
| exception | `InvalidMessageException`(400) · `MessageTemplateNotFoundException`(404) · `MessageSendNotFoundException`(404) | `GlobalExceptionHandler`에 메서드 추가 |
| resource | `{BR}/templates/message-mail.html` | 메일 고정 레이아웃 |

대상자 조회는 JPQL(파생 메서드/`@Query`)로 종류별 쿼리를 둔다. native query 금지.

설정(`application.yaml`, 최상위 `recruit:` 아래에 추가):

```yaml
recruit:
  message:
    gateway: ${RECRUIT_MESSAGE_GATEWAY:logging}
    sender-name: ${RECRUIT_MESSAGE_SENDER_NAME:신영증권 채용담당}
    sender-email: ${RECRUIT_MESSAGE_SENDER_EMAIL:recruit@example.co.kr}
    sms-callback-number: ${RECRUIT_MESSAGE_SMS_CALLBACK:0200000000}
    site-url: ${RECRUIT_MESSAGE_SITE_URL:https://recruit.example.co.kr}
    max-recipients: ${RECRUIT_MESSAGE_MAX_RECIPIENTS:3000}
    result-wait-minutes: ${RECRUIT_MESSAGE_RESULT_WAIT_MINUTES:60}
    success-result-codes: ${RECRUIT_MESSAGE_SUCCESS_RESULT_CODES:0000}
```

## 11. 프론트 구성 (`{FE}` 기준)

| 종류 | 파일 | 역할 |
|---|---|---|
| api | `api/admin/messageApi.ts` | 9절 API 11개 |
| type | `types/admin/message.ts` | 요청·응답 타입 |
| util | `views/admin/message/messageRender.ts` | 치환·byte·SMS 구분(6절, 서버와 같은 규칙) + vitest |
| view | `views/admin/message/AdminMessageSendView.vue` | 발송 화면 조립·상태 |
| view | `views/admin/message/MessageTypePicker.vue` | 종류 카드 |
| view | `views/admin/message/MessageTargetBar.vue` | 조건 바·인원 요약 |
| view | `views/admin/message/MessageComposer.vue` | 템플릿 셀렉트·채널 탭·입력·변수 칩 |
| view | `views/admin/message/MessagePreview.vue` | 메일·휴대폰 미리보기·수신자 이동 |
| view | `views/admin/message/MessageTestSendCard.vue` | 테스트 발송 |
| view | `views/admin/message/MessageRecipientDrawer.vue` | 수신자 드로어 |
| view | `views/admin/message/MessageSendConfirmModal.vue` | 발송 확인 |
| view | `views/admin/message/AdminMessageHistoryView.vue` · `MessageHistoryDrawer.vue` | 발송 이력·상세 |
| view | `views/admin/message/AdminMessageTemplateView.vue` | 템플릿 관리 |
| route | `routes/adminRoutes.ts` | 3절 라우트 3개 추가 |

- ant-design-vue 컴포넌트를 쓴다(`a-select`, `a-tabs`, `a-switch`, `a-drawer`, `a-modal`, `a-table`). 목업의 휴대폰·메일 프레임만 scoped CSS로 만든다.
- 공고·전형 목록은 기존 API를 재사용한다(관리자 공고 목록, `GET /admin/job-postings/{id}/stages`).

## 12. 권한·보안·개인정보

- 권한: `ADMIN`, `RECRUIT_ADMIN`. `/api/admin/**` 매처에 이미 포함되므로 `SecurityConfig` 변경은 없다. `SecurityConfigTest`에 비로그인 401·지원자 403 테스트를 추가한다.
- 발송자는 `CurrentEmployeeService`로 확인(임직원이 아니면 403).
- `MessageRecipient`의 이름·이메일·휴대폰은 `AesAttributeConverter`로 암호화한다(기본정보 연락처와 같은 수준).
- 파기 연동: 지원서 파기 시 그 지원서의 `MessageRecipient` 이름·이메일·휴대폰(암호화 컬럼이라 placeholder 대신 null)과 `createdBy`·`updatedBy`를 null로 바꾼다. 거래 ID·상태·결과코드는 개인정보가 아니라 유지한다. `ApplicationPiiPurgeRepository`에 bulk update 1개 추가, `ApplicationPiiPurgeService` 호출 순서에 넣고 `ApplicationPiiPurgeServiceTest`에 필드 검증 추가. 테스트 수신자(임직원)는 파기 대상이 아니다.
- 로그·예외 메시지에 연락처·본문 원문을 남기지 않는다(게이트웨이 로그는 마스킹).
- 화면(이력 상세·테스트 카드·수신자 드로어 등)에서는 이메일·휴대폰을 가리지 않고 그대로 보여 준다(2026-09-19 사용자 결정). 마스킹은 로그에만 한다.
- 감사 로그: 메시지 발송은 기존 감사 범위(반출·핵심 관리자 변경·보존/파기)에 들지 않고, `MessageSend` 자체가 발송자·시각·대상을 기록하므로 `ActivityLog`는 추가하지 않는다.
- 테스트 발송은 임의 연락처로 지원자 1명의 변수 값이 나가므로 수신자 5명 제한 + 이력 기록으로 추적한다.

## 13. 오류 처리

| 상황 | 응답 |
|---|---|
| 선택 불가 조건(발표 전 전형, 마감된 공고, 면접 유형 아닌 전형 등) | 400 |
| 채널 0개, 켠 채널의 내용이 비어 있음, 길이 초과, 허용되지 않은 변수 | 400 |
| 수신자 0명(교집합 결과 포함), 최대 인원 초과 | 400 |
| 치환 후 SMS 2000byte 초과 | 400(수험번호·인원) |
| 테스트: `previewApplicationId`가 대상자가 아님, 테스트 수신자 0명·6명 이상·연락처 없음 | 400 |
| 템플릿·발송 없음 | 404 |
| 게이트웨이 접수 실패 | 수신자·채널 `FAILED` + 사유, 발송은 계속 |
| 결과의 거래 ID와 맞는 수신자가 없음 | 메모리에 보관하고 재시도, 10분 뒤 경고 로그(거래 ID만)와 함께 버림 |

## 14. 테스트 계획

백엔드(수정한 클래스·패키지만 실행):
- `MessageRendererTest`: 치환, 재치환 안 함, 빈 값, byte(한글·영문 혼합), 90/91/2000/2001 경계, 허용되지 않은 변수, 메일 HTML 이스케이프·줄바꿈.
- `MessageTemplateServiceTest`: 저장 검증(채널 0개, 메일 반쪽), 기본 템플릿 단일화, 삭제.
- `MessageTargetServiceTest`: 종류별 대상자·제외 규칙(철회·파기·발표 전·마감 후·미확정 면접), 연락처 우선순위·유효성, 변수 값(`Clock` 고정으로 D-n).
- `MessageSendServiceTest`: 교집합·excludedCount, SKIPPED 판정, 테스트 발송 대상 검증·`[테스트]` 접두, 이력 저장.
- `MessageDispatcherTest`: 발송 단위 묶기(같은 내용 11명 → 10+1회, 사람마다 다른 내용 → 1명당 1회, 채널별 따로), 접수 결과를 단위 전원에 적용(`REQUESTED` + 거래 ID / `FAILED`), 접수 성공·실패 혼합, 예외 격리, 기록 직후 보관 결과 반영.
- `DeliveryReportHandlerTest`: 거래 ID로 `REQUESTED`만 `SENT`/`FAILED`, 성공 코드 판정, 다시 온 결과 무시, 먼저 온 결과 보관 후 단위 기록 때 반영, 10분 지난 보관 결과 폐기.
- `MessageHistoryServiceTest`: 필터·페이지, 건수·상태 계산(발송 중·결과 수신 중·완료), 지연 표시, 연락처 원문 반환, 파기된 수신자.
- `MessageSendAsyncFlowTest`: 커밋 → 비동기 접수(`REQUESTED`) → 목업 결과 → `COMPLETED` 계산.
- 컨트롤러·보안: 401·403, 400 응답 형식.
- `ApplicationPiiPurgeServiceTest`: `MessageRecipient` tombstone.

프론트: `npm run type-check`, `messageRender.ts` vitest(서버와 같은 예시).

## 15. 문서 갱신

- 카드 2개(카드 템플릿): `docs/domains/message.md`(템플릿·대상자·작성·테스트 발송·발송 접수, 컨트롤러 2개)와 `docs/domains/message-delivery.md`(디스패치·게이트웨이·결과 수신·상태 계산·발송 이력, 컨트롤러 1개). S4 뒤 한 카드가 40KB 상한에 가까워져 나눴다. 두 카드의 파일 지도에 컨트롤러 3개·뷰 전부를 한 번씩 등록, API 계약 표는 9절로 시작해 구현 후 🟢.
- `docs/domains/_index.md`: 카드 목록, 라우트·API 접두(`/admin/messages`, `/admin/message-templates` → message, `/admin/messages/history` → message-delivery)·키워드 역색인.
- `privacy-audit.md`: 파기 대상 필드에 `MessageRecipient` 추가.
- 완료 전 `node tools/check-docs.mjs` 오류 0건.

## 16. 구현 순서 (슬라이스 제안)

| 슬라이스 | 내용 | 완료 기준 |
|---|---|---|
| S1 템플릿 | enum·`MessageTemplate`·변수 카탈로그·템플릿 API + 템플릿 관리 화면 | 템플릿 CRUD·기본 단일화 테스트, 화면에서 등록·수정 |
| S2 대상자·작성 | `MessageTargetService`·`MessageRenderer`·targets API + 발송 화면(종류·조건·작성·미리보기·드로어), 발송 버튼은 비활성 | 종류별 대상자·변수 테스트, 미리보기 동작 |
| S3 발송 | `MessageSend`·`MessageRecipient`·게이트웨이 목업·디스패처·test/send API + 테스트 카드·확인 모달 | 발송·테스트 발송 테스트, 로그 게이트웨이로 끝까지 동작 |
| S4 결과 수신·이력·마무리 | 발송 결과 비동기 수신(7.4)과 S3 상태·집계 변경, 이력 API·화면, 테스트 카드 결과 갱신, 파기 연동, 카드·색인 확정 | 목업 결과로 결과 수신 중 → 완료 흐름 확인, 이력 조회, 파기 테스트, `check-docs` 통과 |

## 17. 결정 필요 사항 (🔴)

1. **운영 DB 반영**: 새 테이블 3개(`message_template`, `message_send`, `message_recipient`). 운영이 `ddl-auto` `validate`/`none`이면 `docs/ops/`에 생성 SQL을 둬야 한다. 둘지 확인 필요.
2. ~~수신자 연락처 암호화~~ → **확정**: AES 암호화(2026-09-19 승인).
3. **초기 템플릿**: 목업의 기본 문구 9개를 초기 데이터로 넣을지, 관리자가 직접 입력할지. 넣는다면 운영 반영 SQL로 제공.
4. **발신 정보 실제 값**: 발신 이메일·표시 이름·SMS 발신번호·채용 사이트 주소(설정값, 코드에는 예시 값만).
5. ~~최대 발송 인원~~ → **확정**: 3,000명(2026-09-19).
6. **결과코드 성공 판정**: 솔루션의 성공 결과코드 목록(스펙 미확인). 우선 `0000`만 성공으로 보고 설정값(`recruit.message.success-result-codes`)으로 바꾼다. 코드별 설명도 스펙 확정 후 추가.
7. **수신자별 거래 ID**: 거래 1건 안에 수신자별 ID·결과가 따로 있는지 스펙 미확인. 우선 거래 단위 결과를 그 거래 수신자 전원에 적용한다.
8. **거래 ID 유일성**: 거래 ID가 메일·SMS 사이에서도 유일하고 재사용되지 않는지 스펙 미확인. `DeliveryReport`에 채널이 없어 `applyReport`가 같은 ID로 두 채널 컬럼을 모두 확인·갱신하므로, 채널 간에 겹치거나 재사용되면(예: 일자별 일련번호) 다른 수신자에 결과가 반영되거나 새 결과가 이미 기록된 거래로 판정돼 버려질 수 있다. 현재 입장: 유일하다고 가정한다. 스펙이 오면 `DeliveryReport`에 채널을 넣고 존재 확인·update를 채널별로 한정한다.
