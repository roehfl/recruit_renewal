# 시스템 자동발송 메일 설계 (가입 이메일 인증 · 비밀번호 재발급 · 제출 완료 안내)

- 작성일: 2026-09-23
- 관련 카드: [auth-account](../../domains/auth-account.md), [message](../../domains/message.md), [message-delivery](../../domains/message-delivery.md), [application](../../domains/application.md)
- 배경: 사내 TR 게이트웨이로 메일 실발송·결과 수신이 확인됐다(2026-09-23). 목업이던 가입 이메일 인증·비밀번호 재발급을 실제 메일로 붙이고, 최종 제출 시 안내 메일을 보낸다.

## 1. 결정 사항

| 항목 | 결정 |
|---|---|
| 비밀번호 재발급 방식 | 가입 이메일로 인증번호 → 화면에서 확인 → 그 자리에서 새 비밀번호 설정. 임시 비밀번호·재설정 링크는 쓰지 않는다(카드의 "토큰 링크" 결정을 대체) |
| 이력 저장 | 3종 모두 기존 `message_send`·`message_recipient`에 저장. 발송 구분 `origin`(`ADMIN`·`SYSTEM`) 추가 |
| 인증번호 규칙 | 숫자 6자리 · 유효 5분 · 5회 틀리면 무효 · 재발송 60초 후 · 확인 후 10분 안에 가입/변경 완료 |
| 인증번호 저장 | HTTP 세션(번호는 해시만). DB 테이블 없음 |
| 문구 관리 | 관리자 메시지 템플릿(유형별 기본 템플릿) |
| 제출 메일 시점 | 제출·재제출 성공마다 |
| 채널 | 메일만(SMS 없음) |

## 2. 범위

포함
- 백엔드: `MessageOrigin`·`MessageType` 3개·`#{인증번호}` 변수, `message_send` 변경(+운영 DDL), 시스템 기본 템플릿 자동 생성, `SystemMailService`, 이메일 인증번호 저장소, 가입 인증 API 2개, 비밀번호 재발급 API 3개, 가입 시 인증 강제, 제출 완료 이벤트·리스너, 이력 목록 `origin` 필터·응답 필드.
- 프론트: `SignupView` 인증 실연동, `AccountRecovery` 비밀번호 탭 3단계, 이력 화면 발송 구분 열·필터, 템플릿 화면 시스템 유형 표시·삭제 차단, 발송 화면 유형 목록에서 시스템 유형 제외.

제외
- SMS 인증, 인증 시도의 IP 단위 제한, 세션 밖(다른 브라우저) 인증 이어가기, 제출 메일 재발송.

## 3. 데이터

### 3.1 enum
- `MessageOrigin { ADMIN, SYSTEM }` 신설(`enumeration`).
- `MessageType`에 `SIGNUP_VERIFICATION`, `PASSWORD_RESET`, `APPLICATION_SUBMITTED` 추가. `MessageType.isSystem()`으로 세 값을 구분한다.
- `MessageVariable`
  - 신설 `VERIFICATION_CODE("인증번호", "메일 인증번호", {SIGNUP_VERIFICATION, PASSWORD_RESET})`.
  - `NAME`·`SITE_URL`: 기존 전체 유형 유지(가입 인증에서 이름은 빈 값으로 치환된다).
  - `JOB_POSTING_TITLE`: 관리자 유형 5개 + `APPLICATION_SUBMITTED`(인증 메일 2종 제외).
  - 신설 `SUBMITTED_AT("제출일시", "지원서 제출 일시", {APPLICATION_SUBMITTED})`.

### 3.2 `MessageSend`
- `origin` 컬럼 추가: `@Enumerated(STRING)`, `nullable = false`, 길이 20. 기존 행은 운영 DDL로 `ADMIN`.
- `job_posting_id`를 `nullable = true`로 바꾼다. 관리자 발송은 서비스에서 계속 필수로 검증한다.
- 시스템 발송 값: `test=false`, `stage=null`, `conditionSummary=null`, `templateId`·`templateName` = 사용한 기본 템플릿, `mailEnabled=true`, `smsEnabled=false`, `mailSubject`·`mailBody` = 템플릿 원문(변수 치환 전), `senderLoginId="SYSTEM"`, `senderName="시스템"`, `recipientCount=1`.
- 생성은 `MessageSend.createSystem(...)` 정적 메서드로 한다.

### 3.3 `MessageRecipient`
- 변경 없음. `jobApplication`은 이미 선택값이다. 가입 인증·비밀번호 재발급은 `jobApplication=null`, 제출 메일은 그 지원서.
- 이름: 가입 인증 = 빈 값, 비밀번호 재발급 = `Applicant.name`, 제출 = 기존 대상자 조회와 같은 규칙.

### 3.4 운영 DDL
`recruit_back/recruit_backend/docs/ops/`에 추가:
- `message_send.origin` 추가(`NOT NULL DEFAULT 'ADMIN'` 후 기존 행 채움).
- `message_send.job_posting_id` NULL 허용.

## 4. 인증번호는 DB·로그에 남지 않는다

- 이력에는 `#{인증번호}`가 들어 있는 템플릿 원문만 저장한다.
- 치환은 `DeliveryItem`을 만드는 순간 메모리에서만 한다. `DeliveryItem`은 이벤트·동기 호출로만 전달되고 저장되지 않는다.
- 세션에는 번호의 SHA-256 해시(`HashUtil`)만 둔다. 로그에는 목적·마스킹 이메일·결과만 남긴다.

## 5. 템플릿

- 기동 시(`ApplicationRunner`) 시스템 유형 3개 각각 기본 템플릿이 없으면 만든다. 문구 초안은 7절.
- 발송은 그 유형의 기본 템플릿(`defaultTemplate=true`)을 쓴다. 없으면(수동 삭제 등) 발송하지 않고 경고 로그. 인증 API는 400 `인증 메일 템플릿이 없습니다. 관리자에게 문의하세요.`
- 관리자 템플릿 API 규칙 추가(`MessageTemplateService`)
  - 시스템 유형 템플릿은 메일 제목·본문 필수, `smsBody`는 저장하지 않는다(null).
  - 인증 유형 2개는 제목 또는 본문에 `#{인증번호}`가 없으면 400 `인증 메일에는 #{인증번호}가 있어야 합니다.`
  - 시스템 유형의 기본 템플릿은 삭제 400 `시스템 기본 템플릿은 삭제할 수 없습니다.`, 기본 해제도 같은 규칙(다른 템플릿을 기본으로 지정하면 해제 허용 — 기존 기본 전환 로직 그대로).
- 관리자 발송·테스트 발송 요청에 시스템 유형이 오면 400 `시스템 자동발송 유형은 직접 보낼 수 없습니다.`

## 6. 구성 요소

### 6.1 `SystemMailService` (`service`)
```
SendOutcome send(MessageType type, String email, String name,
                 Map<String,String> variables, JobPosting posting, JobApplication application)
```
1. 유형의 기본 템플릿을 찾는다. 없으면 `SendOutcome.NO_TEMPLATE`.
2. `MessageSend.createSystem` + `MessageRecipient` 저장(메일 `REQUESTED` 전 상태는 기존 생성 규칙 그대로).
3. 제목·본문을 `MessageRenderer.render`로 치환해 `DeliveryItem`(메일 1건)을 만든다.
4. 호출자에 따라
   - `sendNow`(인증 메일): 이력 저장을 `REQUIRES_NEW` 트랜잭션으로 커밋한 뒤 `MessageDispatcher.dispatch`를 동기 호출. 접수되면 `ACCEPTED`, 아니면 `FAILED`(이력은 `FAILED`로 남는다).
   - `sendAfterCommit`(제출 메일): 저장 후 기존 `MessageSendRequestedEvent`를 발행해 커밋 뒤 비동기 디스패치.
- 결과 수신·이력 상세는 기존 경로를 그대로 탄다.

### 6.2 `EmailVerificationService` (`service`) + `EmailVerificationPurpose { SIGNUP, PASSWORD_RESET }`
- 세션 값 `EmailVerificationState(purpose, email, codeHash, expiresAt, failedCount, sentAt, verifiedAt)`. 키는 목적별로 나눈다.
- `issue(state?, purpose, email, now)` → 새 상태 + 평문 번호. 같은 목적의 직전 `sentAt`에서 60초 안이면 400 `인증번호는 60초 후에 다시 받을 수 있습니다.`
- `verify(state, purpose, email, code, now)`
  - 상태 없음·이메일 다름·만료 → 400 `인증번호를 다시 받아 주세요.`
  - `failedCount >= 5` → 400 `인증번호를 5회 틀렸습니다. 인증번호를 다시 받아 주세요.`
  - 불일치 → `failedCount+1`, 400 `인증번호가 일치하지 않습니다.`
  - 일치 → `verifiedAt=now`.
- `requireVerified(state, purpose, email, now)`: `verifiedAt` 있고 10분 안이고 이메일(앞뒤 공백 제거, 대소문자 무시) 같으면 통과, 아니면 400 `이메일 인증이 필요합니다.`
- 번호 생성은 `SecureRandom`. 시간은 주입한 `Clock`.
- 예외는 새 `InvalidEmailVerificationException`(400) + `GlobalExceptionHandler` 매핑.
- 컨트롤러가 세션 읽기·쓰기를 맡고 서비스는 값만 다룬다(기존 NICE 패턴과 같다).

### 6.3 가입 이메일 인증
| API | 요청 | 응답 | 동작 |
|---|---|---|---|
| `POST /auth/applicants/email-verification/send` | `{ email }` | `null` | 이미 가입된 이메일이면 400 `이미 사용 중인 이메일입니다.` → 번호 발급 → `SIGNUP_VERIFICATION` 메일 동기 발송. 발송 실패면 세션 상태를 저장하지 않고 502 대신 400 `인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해 주세요.` |
| `POST /auth/applicants/email-verification/verify` | `{ email, code }` | `null` | 6.2 verify |

- `sign-up`: NICE 확인 뒤 `requireVerified(SIGNUP, request.email)`. 가입 이메일은 사실상 필수가 된다(`email` 비면 400 `이메일 인증이 필요합니다.`). 성공 시 세션 상태 제거.
- `SecurityConfig` permitAll 목록에 두 경로 추가 + `SecurityConfigTest`.

### 6.4 비밀번호 재발급
| API | 요청 | 응답 | 동작 |
|---|---|---|---|
| `POST /auth/applicants/password-reset/send` | `{ email }` | `null` | 가입된 지원자 없으면 404 `가입된 이메일이 아닙니다.`(계정 열거 감수 — 기존 결정) → 발급 → `PASSWORD_RESET` 메일 동기 발송(이름 = `Applicant.name`) |
| `POST /auth/applicants/password-reset/verify` | `{ email, code }` | `null` | 6.2 verify |
| `POST /auth/applicants/password-reset` | `{ email, newPassword }` | `null` | `requireVerified(PASSWORD_RESET, email)` → 비밀번호 규칙은 가입과 같다(`@Size(min=8,max=100)`) → BCrypt 저장 → 세션 상태 제거 |

- 위치: `ApplicantAccountRecoveryController` / `ApplicantAccountRecoveryService`. permitAll 추가.
- 기존 로그인 세션은 건드리지 않는다.

### 6.5 제출 완료 메일
- `JobApplicationService.submit` 끝에서 `ApplicationSubmittedEvent(applicationId)` 발행.
- `ApplicationSubmittedMailListener`: `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`, 새 트랜잭션에서 지원서·기본정보·공고를 읽어 `SystemMailService.send(APPLICATION_SUBMITTED, ...)`를 동기 디스패치로 호출(이미 비동기 스레드이므로).
- 받는 주소: `ApplicationBasicInfo.email` → 비면 `Applicant.email`. 둘 다 비면 발송하지 않고 경고 로그.
- 변수: `이름`, `공고명`, `제출일시`(`yyyy-MM-dd HH:mm`), `채용사이트`.
- 리스너 예외는 잡아서 경고 로그만. 제출 응답에는 영향 없다.

### 6.6 발송 이력 화면
- `GET /admin/messages/history`에 `origin?`(`ADMIN`·`SYSTEM`) 필터 추가. `jobPostingId`로 거르면 공고 없는 시스템 행은 자연히 빠진다.
- `MessageSendSummaryResponse`·`MessageSendDetailResponse`에 `origin` 추가. 공고 없는 행은 `jobPostingId`·`jobPostingTitle` null.
- 프론트: 목록에 "발송 구분" 열(관리자 발송 / 시스템 자동발송), 검색 조건에 발송 구분 선택, 공고 null이면 `-`, 유형 라벨에 시스템 3종 추가.

### 6.7 프론트
- `src/api`: 인증·재발급 API 5개 추가(기존 `applicationApi.ts`의 계정 호출 옆).
- `SignupView.vue`: "메일 인증" → `email-verification/send`, "인증확인" → `verify` 성공 시에만 완료. 60초 재발송 버튼 비활성·남은 시간 표시는 하지 않고 서버 메시지를 그대로 보여 준다.
- `AccountRecovery.vue` 비밀번호 탭: 이메일 → 인증번호 → 새 비밀번호·확인 입력 → 완료 후 로그인 버튼. "임시 비밀번호" 문구 제거.
- 템플릿 화면: 유형 목록에 시스템 3종(그룹 "시스템 자동발송"), 시스템 유형은 SMS 입력 숨김, 변수 목록은 기존 카탈로그 API로 자동 반영.
- 발송 화면: 유형 선택지에서 시스템 3종 제외.

## 7. 기본 템플릿 문구(초안, 관리자가 수정 가능)

- 가입 인증: 제목 `[신영증권 채용] 회원가입 이메일 인증번호`, 본문 `아래 인증번호를 회원가입 화면에 입력해 주세요.\n\n인증번호: #{인증번호}\n\n인증번호는 5분 동안 유효합니다.`
- 비밀번호 재발급: 제목 `[신영증권 채용] 비밀번호 재설정 인증번호`, 본문 `#{이름}님, 아래 인증번호를 비밀번호 재설정 화면에 입력해 주세요.\n\n인증번호: #{인증번호}\n\n인증번호는 5분 동안 유효합니다. 본인이 요청하지 않았다면 이 메일을 무시해 주세요.`
- 제출 완료: 제목 `[신영증권 채용] #{공고명} 지원서 제출 완료 안내`, 본문 `#{이름}님, #{공고명} 지원서가 #{제출일시}에 제출되었습니다.\n\n지원 현황은 채용 사이트(#{채용사이트})에서 확인할 수 있습니다.`

## 8. 오류·예외 정리

| 상황 | 결과 |
|---|---|
| 인증 메일 게이트웨이 미접수 | 400 안내 메시지, 이력 `FAILED`, 세션 상태 저장 안 함 |
| 시스템 기본 템플릿 없음 | 인증: 400 / 제출: 발송 생략 + 경고 로그 |
| 제출 메일 실패 | 이력 `FAILED`, 제출은 성공 |
| 관리자가 시스템 유형으로 발송 시도 | 400 |

## 9. 테스트

- `EmailVerificationServiceTest`(Mockito, 고정 `Clock`): 발급·60초 제한·만료·5회·불일치 누적·일치·`requireVerified` 10분·이메일 대소문자.
- `SystemMailServiceTest`(`@SpringBootTest`): 이력 `origin=SYSTEM`·공고 null 저장, 이력 본문에 인증번호 없음, 게이트웨이 실패 시 `FAILED`, 템플릿 없음.
- 가입·재발급 컨트롤러 테스트(MockMvc + 세션): 인증 없이 가입 400, 인증 후 가입 성공·재사용 불가, 재발급 전체 흐름, 미가입 404.
- `JobApplicationService` 제출 이벤트 발행, 리스너 주소 선택.
- `MessageTemplateService`: 시스템 기본 템플릿 삭제 거부, `#{인증번호}` 필수, 시스템 유형 발송 거부, 기동 시 기본 템플릿 생성.
- 이력 `origin` 필터.
- `SecurityConfigTest`: 새 공개 경로 5개.
- 프론트 `npm run type-check`.

## 10. 문서
- auth-account: API 계약 5개, 가입 인증 강제 규칙, 비밀번호 재발급 결정 교체, 목업 문구 제거.
- message: 시스템 유형·템플릿 규칙·변수 추가, 발송 거부 규칙.
- message-delivery: `origin`, 공고 선택값, `SystemMailService`, 이력 API `origin`.
- application: 제출 완료 이벤트.
- `docs/ops/` DDL.
