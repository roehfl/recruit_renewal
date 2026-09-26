# 면접 추가사항 (`interview-supplement`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [interview](interview.md) (면접·조·도착시간·참가자) · [stage-result](stage-result.md) (단계·단계 삭제) · [auth-account](auth-account.md) (마이페이지 `ApplicantProfile.vue`) · [privacy-audit](privacy-audit.md) (답변 파기) · [role-menu](role-menu.md) (관리자 메뉴 등록)

## 요약

- 면접 대기 중인 지원자가 마이페이지에서 미리 등록된 질문 N개에 **정해진 시간 안에만** 답을 적는 기능.
- 면접단계마다 추가사항을 받을지 정한다(켜면 `InterviewSupplement` 1행). 질문은 그 단계의 세트다.
- 입력 가능 시간은 **지원자별**. 기본값은 지원자 조의 `arrivalDateTime` ~ +2시간, 관리자가 바꾸면 지원자별 행(`InterviewSupplementWindow`)으로 저장한다.
- **시간 판정은 서버 시계(`Clock`)만** 한다. 입력 시간 밖의 조회·저장은 거부하고 유예는 없다. 화면 카운트다운과 자동 닫힘은 편의 기능이다.
- 화면: 관리자 `/admin/interview-supplements`(`AdminInterviewSupplement` — 질문 설정 / 입력 시간 설정 / 답변 조회 탭), 지원자 마이페이지 지원 목록의 "추가사항 입력" 열 + 입력 모달.

## 용어

| 용어 | 뜻 |
|---|---|
| 추가사항 (`InterviewSupplement`) | 면접 유형 단계 1개에 붙는 질문 세트. `stage_id` unique. 행이 있으면 "받음" |
| 질문 (`InterviewSupplementQuestion`) | 세트 안 질문. `content` ≤500, `sortOrder` 1..n |
| 입력 시간 (`InterviewSupplementWindow`) | 지원자(지원서)별로 바꾼 시작·종료 시각. 행이 없으면 기본값 |
| 답변 (`InterviewSupplementAnswer`) | (질문, 지원서) 1행. `answerText` ≤1000, 평문. 저장 시각 = `updatedAt` |
| 대상 지원자 | 단계의 `CONFIRMED` 면접에 `CANDIDATE`·`ASSIGNED`로 배정된 `WITHDRAWN` 아닌 지원서 |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/InterviewSupplementAdminController.java` | 관리자 11개(세트·질문·대상자·시간·답변) |
| controller | `{BE}/controller/ApplicantInterviewSupplementController.java` | 지원자 3개(목록·입력 화면·저장) |
| service | `{BE}/service/InterviewSupplementAdminService.java` | 켜기/끄기, 질문 CRUD·순서, 대상자·지원자별 시간, 답변 조회, 단계 삭제 정리(`deleteForStage`) |
| service | `{BE}/service/ApplicantInterviewSupplementService.java` | 지원서별 요약, 입력 화면, 답변 저장(서버 시각 판정) |
| service | `{BE}/service/InterviewSupplementWindows.java` | 기본 시간 계산·열림 판정·남은 초·성명(두 서비스 공용) |
| entity | `{BE}/domain/entity/InterviewSupplement.java` | 단계별 세트 |
| entity | `{BE}/domain/entity/InterviewSupplementQuestion.java` | 질문 |
| entity | `{BE}/domain/entity/InterviewSupplementWindow.java` | 지원자별 입력 시간 |
| entity | `{BE}/domain/entity/InterviewSupplementAnswer.java` | 답변 |
| repository | `{BE}/domain/repository/InterviewSupplementRepository.java` | 세트 조회 + 대상 지원자 쿼리(`findCandidateParticipantsByStageId`) |
| repository | `{BE}/domain/repository/InterviewSupplementQuestionRepository.java` | 질문 |
| repository | `{BE}/domain/repository/InterviewSupplementWindowRepository.java` | 지원자별 시간 |
| repository | `{BE}/domain/repository/InterviewSupplementAnswerRepository.java` | 답변(세트·지원서 단위 조회, 존재 검사) |
| dto | `{BE}/dto/request/InterviewSupplement*Request.java` | 질문 저장·순서, 시간 저장·복원, 답변 저장(+항목) 6종 |
| dto | `{BE}/dto/response/AdminInterviewSupplement*Response.java` | 세트·질문·대상자 행·답변 상세·답변 항목 5종 |
| dto | `{BE}/dto/response/ApplicantInterviewSupplement*Response.java` | 요약·입력 화면·질문·저장 결과 4종 |
| exception | `{BE}/exception/InterviewSupplementNotFoundException.java` | 404 |
| exception | `{BE}/exception/InvalidInterviewSupplementException.java` | 400 |
| test | `{BT}/service/InterviewSupplementAdminServiceTest.java` | 켜기·질문·순서·기본 시간·지원자별 시간·답변 보호·단계 삭제 |
| test | `{BT}/service/ApplicantInterviewSupplementServiceTest.java` | 입력·빈 답 삭제·**마감 1초 뒤 거부**·시작 전 거부·지원자별 시간·소유권 |
| test | `{BT}/controller/InterviewSupplementAdminControllerTest.java` | 관리자 API·검증 400/404·권한 403/401 |
| test | `{BT}/controller/ApplicantInterviewSupplementControllerTest.java` | 지원자 API·마감 400 메시지·1000자 초과·관리자 403 |

### 프론트

| 구분 | 파일 | 역할 |
|---|---|---|
| route | `{FE}/routes/adminRoutes.ts` | (공유) `AdminInterviewSupplement` — `/admin/interview-supplements` |
| view | `{FE}/views/admin/interview/AdminInterviewSupplementView.vue` | 공고·면접단계 선택, 켜기/끄기, 탭 3개 |
| view | `{FE}/views/admin/interview/InterviewSupplementQuestionTab.vue` | 질문 추가·수정·삭제·드래그 순서 변경 |
| view | `{FE}/views/admin/interview/InterviewSupplementWindowTab.vue` | 지원자별 입력 시간(개별·조 전체·선택 일괄 변경, 기본값 복원) |
| view | `{FE}/views/admin/interview/InterviewSupplementAnswerTab.vue` | 답변 조회 목록(조·작성 여부 필터) |
| view | `{FE}/views/admin/interview/InterviewSupplementAnswerDrawer.vue` | 지원자 답변 보기(이전·다음) |
| common | `{FE}/views/admin/interview/interviewSupplementTime.ts` | 시각 표시·+N시간 계산 |
| view | `{FE}/views/applicant/InterviewSupplementModal.vue` | 지원자 입력 모달(카운트다운·자동 저장·자동 닫힘) |
| api | `{FE}/api/admin/adminInterviewSupplementApi.ts` | 관리자 11개 |
| api | `{FE}/api/interviewSupplementApi.ts` | 지원자 3개 |
| types | `{FE}/types/admin/interviewSupplement.ts` | 관리자 타입 |
| types | `{FE}/types/interviewSupplement.ts` | 지원자 타입 |

## API 계약

권한 열: 관리자 = `/admin/**` → `ADMIN`·`RECRUIT_ADMIN`, `APPLICANT` = `/applicant/**` (`{BE}/config/SecurityConfig.java`). 응답은 `ApiResponse<...>`. 시각은 ISO date-time(`LocalDateTime`, 타임존 없음).

| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |
|---|---|---|---|---|---|
| 🟢 | GET | /admin/stages/{stageId}/interview-supplement | — | 세트 상태 | 관리자 |
| 🟢 | POST | /admin/stages/{stageId}/interview-supplement | — (켜기, 멱등) | 세트 상태 | 관리자 |
| 🟢 | POST | /admin/stages/{stageId}/interview-supplement/delete | — (끄기) | 세트 상태(`enabled=false`) | 관리자 |
| 🟢 | POST | /admin/stages/{stageId}/interview-supplement/questions | `{ content }` | 세트 상태 | 관리자 |
| 🟢 | POST | /admin/stages/{stageId}/interview-supplement/questions/{questionId} | `{ content }` | 세트 상태 | 관리자 |
| 🟢 | POST | /admin/stages/{stageId}/interview-supplement/questions/{questionId}/delete | — | 세트 상태 | 관리자 |
| 🟢 | POST | /admin/stages/{stageId}/interview-supplement/questions/reorder | `{ questionIds: [] }`(전체) | 세트 상태 | 관리자 |
| 🟢 | GET | /admin/stages/{stageId}/interview-supplement/candidates | — | 대상 지원자 행[] | 관리자 |
| 🟢 | POST | /admin/stages/{stageId}/interview-supplement/windows | `{ jobApplicationIds: [], startDateTime, endDateTime }` | 대상 지원자 행[] | 관리자 |
| 🟢 | POST | /admin/stages/{stageId}/interview-supplement/windows/reset | `{ jobApplicationIds: [] }` | 대상 지원자 행[] | 관리자 |
| 🟢 | GET | /admin/stages/{stageId}/interview-supplement/candidates/{jobApplicationId}/answers | — | 지원자 답변 | 관리자 |
| 🟢 | GET | /applicant/interview-supplements | — | 지원서별 요약[] | APPLICANT |
| 🟢 | GET | /applicant/applications/{applicationId}/interview-supplements/{stageId} | — | 입력 화면(질문+내 답변) | APPLICANT |
| 🟢 | POST | /applicant/applications/{applicationId}/interview-supplements/{stageId}/answers | `{ answers: [{ questionId, answerText }] }` | `{ savedAt, remainingSeconds, answeredCount }` | APPLICANT |

### 엔드포인트 상세

**응답 모양**

- 세트 상태 `{ stageId, enabled, questions: [{ questionId, content, sortOrder }], answeredApplicantCount }`. 꺼져 있으면 `questions` 빈 배열·카운트 0.
- 대상 지원자 행 `{ jobApplicationId, applicantName, interviewId, groupName, candidateOrder, arrivalDateTime, interviewStartDateTime, startDateTime, endDateTime, customized, answeredCount, lastSavedAt }`. 기본값을 못 만들면(도착시간 없음·지원자별 시간 없음) `startDateTime`·`endDateTime` null. 정렬 = 면접 시각 → 면접 id → 면접순서.
- 지원자 답변 `{ jobApplicationId, applicantName, groupName, candidateOrder, startDateTime, endDateTime, answeredCount, lastSavedAt, items: [{ questionId, content, answerText, savedAt }] }`. 질문 순서대로, 답 없으면 `answerText`·`savedAt` null.
- 지원서별 요약 `{ applicationId, stageId, stageName, startDateTime, endDateTime, open, remainingSeconds, questionCount, answeredCount }`. 지원서당 1건.
- 입력 화면 `{ applicationId, stageId, jobPostingTitle, stageName, endDateTime, remainingSeconds, questions: [{ questionId, content, answerText }] }`.

**오류**

- 400 `InvalidInterviewSupplementException`: 면접 단계 아님(`"면접 단계에서만 추가사항을 받을 수 있습니다."`), 종료 ≤ 시작, 입력 시간 밖(`"추가사항 입력 시간이 아닙니다."` — FE가 이 문구로 마감을 판정), 답변 있는 질문 삭제·끄기, 순서 목록 불일치, 세트에 없는·중복 질문 id.
- 400 Bean Validation: 질문 빈 값·500자 초과, 답 1000자 초과, 목록 비어 있음.
- 404 `InterviewSupplementNotFoundException`: 꺼진 세트에 질문·시간 명령, 질문 없음, 대상 지원자 아님, 지원자 쪽에서 남의 지원서·꺼짐·질문 0개. 404 `StageNotFoundException`: 단계 없음.

## 규칙·불변식

**세트·질문 (관리자)**

1. 켜기는 `FIRST_INTERVIEW`·`SECOND_INTERVIEW`·`FINAL_INTERVIEW` 단계만, 이미 켜져 있으면 그대로 돌려준다. 단계 상태는 보지 않는다 (`{BE}/service/InterviewSupplementAdminService.java` — `enable`).
2. 끄기는 답변 행이 하나라도 있으면(파기로 본문이 null이 된 행 포함) 400. 없으면 지원자별 시간·질문·세트를 지운다 (`disable`·`deleteSupplement`).
3. 질문은 trim 후 저장, 추가 = 마지막 순서+1. 답변 행이 있는 질문은 삭제 400, 지우면 남은 질문을 1..n으로 다시 매긴다 (`addQuestion`·`deleteQuestion`).
4. 순서 변경은 현재 질문 id 전체를 중복 없이 보내야 한다(아니면 400) (`reorderQuestions`).
5. 질문 수정은 입력 중에도 허용한다(화면이 경고만 띄운다). 답변은 질문 id로 묶여 있어 문구를 바꾸면 기존 답이 새 문구 아래 보인다.

**입력 시간**

6. 대상 지원자 = 단계의 `CONFIRMED` 면접 `CANDIDATE`·`ASSIGNED`, 지원서 `WITHDRAWN` 아님. 한 지원서가 두 조에 있으면 먼저 나온 조 (`{BE}/domain/repository/InterviewSupplementRepository.java` — `findCandidateParticipantsByStageId`, `findCandidates`).
7. 기본값 = 조의 `arrivalDateTime` ~ +2시간. 도착시간이 없으면 기본값 없음(지원자별 시간을 지정해야 열림) (`{BE}/service/InterviewSupplementWindows.java` — `defaultRange`).
8. 시간 저장은 선택한 지원자 모두에게 같은 값을 준다. 종료 ≤ 시작 400, 대상 아님 404. **기본값과 같으면 지원자별 행을 지운다**(`customized=false`) (`saveWindows`). 복원은 지원자별 행 삭제 (`resetWindows`).
9. 지원자별 시간은 지원서 기준이라 스케줄을 다시 업로드해 면접(조)이 바뀌어도 남는다. 기본값은 새 조의 도착시간을 따른다.

**지원자 입력 (서버 시각 판정)**

10. 열림 = `시작 ≤ now < 종료`(`now` = `LocalDateTime.now(clock)`). 종료 시각이 되면 바로 닫힌다. **유예 없음** — 마감 뒤 도착한 저장은 네트워크 지연이어도 400 (`InterviewSupplementWindows.Range.isOpen`, `{BE}/service/ApplicantInterviewSupplementService.java` — `resolveOpenTarget`).
11. 입력 화면 조회와 저장 모두 매번 다시 판정한다. 대상 = 본인 지원서가 그 단계 `CONFIRMED` 면접에 배정, 세트가 켜져 있고 질문 ≥1 (아니면 404).
12. 저장은 보낸 질문만 덮어쓴다. 빈 답(공백만 포함)은 행을 지운다. 응답 `remainingSeconds`로 화면 타이머를 다시 맞춘다 (`saveAnswers`).
13. 요약 목록은 질문이 있고 시간을 만들 수 있는 세트만, 지원서당 입력 중 → 가장 가까운 예정 → 가장 최근 종료 순으로 1건 (`getMySupplements`·`pickRelevant`).
14. 답변은 평문. 지원서 파기 때 `answerText`·감사 필드를 null로 지운다(질문은 유지) ([privacy-audit](privacy-audit.md)).

**단계 삭제**

15. READY 단계 삭제 때 세트·질문·지원자별 시간을 함께 지운다. 삭제 가능한 단계에는 노출된 면접이 없어 답변이 생길 수 없다 (`deleteForStage`, 호출은 `{BE}/service/StageService.java` — delete).

**다른 카드 파일과의 연결**

- 단계 삭제: `{BE}/service/StageService.java` — delete가 `deleteForStage`를 부른다([stage-result](stage-result.md)).
- 파기: `{BE}/domain/repository/ApplicationPiiPurgeRepository.java` — `purgeInterviewSupplementAnswers`, `{BE}/service/ApplicationPiiPurgeService.java`에서 호출, 테스트 `{BT}/service/ApplicationPiiPurgeServiceTest.java`([privacy-audit](privacy-audit.md)).
- 예외 매핑: `{BE}/exception/GlobalExceptionHandler.java`.
- 마이페이지 목록 열·760px 이하 카드 배치: `ApplicantProfile.vue`(`renderSupplementCell`·`loadSupplements`, [auth-account](auth-account.md) 소유). 공고·단계 목록은 [job-posting](job-posting.md) `getAllJobPostings`, [stage-result](stage-result.md) `adminStageApi.getStages`.

**프론트**

16. 지원자 화면은 기기 시계를 쓰지 않는다. 모달은 `remainingSeconds`를 받은 순간부터 `performance.now()`로 세고(저장 응답마다 재동기화), 목록은 `서버 시각 ≈ endDateTime − remainingSeconds + 경과`로 추정해 30초마다 버튼 상태를 바꾼다 (`{FE}/views/applicant/InterviewSupplementModal.vue`, `ApplicantProfile.vue` — `serverNow`).
17. 모달: 입력 1.5초 뒤 자동 저장(바뀐 질문만), 남은 3초 이하에서는 기다리지 않고 저장, 0이 되면 닫고 안내. 저장이 `"추가사항 입력 시간이 아닙니다."`로 거부되면 마감으로 보고 닫는다. 5분 이하 주황·1분 이하 빨강 (`flush`·`tick`·`expire`).
18. 모달은 `body`에 붙는다(페이지 스크롤과 무관하게 화면 고정). 760px 이하에서 전체 화면, 타이머·버튼 고정, 질문만 스크롤. 마이페이지 목록은 760px 이하에서 카드로 바뀌고 추가사항 없는 공고는 칸을 숨긴다.
19. 관리자 화면의 "입력 시간 중" 경고는 브라우저 시각 기준 참고 표시다.

## 변경 레시피

### 질문·답변 길이 한도 변경

1. 백엔드: 엔티티 상수·컬럼 길이(`InterviewSupplementQuestion.MAX_CONTENT_LENGTH`·`InterviewSupplementAnswer.MAX_ANSWER_LENGTH`)와 DTO `@Size`(`InterviewSupplementQuestionSaveRequest`·`InterviewSupplementAnswerItemRequest`). 컬럼 길이를 늘리면 `ddl-auto`가 운영 컬럼을 바꾸지 않으니 SQL을 `recruit_back/recruit_backend/docs/ops/`에.
2. 프론트: `InterviewSupplementQuestionTab.vue` `MAX_LENGTH`, `InterviewSupplementModal.vue` `MAX_ANSWER_LENGTH`.
3. 테스트 → 카드 용어·오류 → `node tools/check-docs.mjs`.

### 기본 입력 시간 규칙 변경 (예: 도착 30분 전부터)

1. `{BE}/service/InterviewSupplementWindows.java` — `defaultRange`(`DEFAULT_WINDOW_HOURS`). 관리자 `saveWindows`의 "기본값과 같으면 행 삭제"도 같은 함수를 쓴다.
2. 프론트: `InterviewSupplementWindowTab.vue`의 기본값 표시·"기본값으로" 버튼(도착 + 2시간 계산), 화면 안내 문구.
3. 테스트 `InterviewSupplementAdminServiceTest`(기본 시간) → 카드 요약·규칙 7 → `node tools/check-docs.mjs`.

### 마감 판정 변경 (유예 도입 등)

1. `InterviewSupplementWindows.Range.isOpen`과 `ApplicantInterviewSupplementService.resolveOpenTarget`. 조회와 저장을 다르게 하려면 판정을 나눈다.
2. 모달 `FLUSH_BEFORE_END_MS`·`isClosedError`.
3. `ApplicantInterviewSupplementServiceTest`(마감 1초 뒤 거부) → 카드 규칙 10·17, 함정 → `node tools/check-docs.mjs`.

## 검증

백엔드(`recruit_back/recruit_backend/`에서). `*Supplement*`는 이 카드 테스트 4개와 맞는다. 파기 연결은 `ApplicationPiiPurgeServiceTest`, 단계 삭제 연결은 `StageServiceTest`.

```powershell
# Windows PowerShell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.*Supplement*" --tests "com.shinyoung.recruit.service.ApplicationPiiPurgeServiceTest" --tests "com.shinyoung.recruit.service.StageServiceTest" --no-daemon
```

```bash
# Linux
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "com.shinyoung.recruit.*Supplement*" --tests "com.shinyoung.recruit.service.ApplicationPiiPurgeServiceTest" --tests "com.shinyoung.recruit.service.StageServiceTest" --no-daemon
```

프론트(`recruit_front/`에서). 이 도메인 vitest spec 없음.

```bash
npm run type-check
```

문서(레포 루트): `node tools/check-docs.mjs`

## 함정·결정

- 마감 유예 없음(사용자 결정 2026-09-26): 종료 시각 이후 도착한 저장은 네트워크 지연이어도 거부한다. 대신 화면이 1.5초 자동 저장·마감 3초 전 즉시 저장으로 손실을 줄인다.
- 답변은 평문 저장(자기소개서 답변과 같은 방식), 지원서 파기 때 함께 파기(사용자 결정 2026-09-26).
- 새 테이블 4개(`interview_supplement`·`interview_supplement_question`·`interview_supplement_window`·`interview_supplement_answer`) 운영 DDL은 만들지 않는다(사용자 결정 2026-09-26, `ddl-auto` 사용). 운영이 `validate`/`none`이면 테이블을 따로 만들어야 한다.
- 관리자 메뉴는 DB에 있다. 배포 후 메뉴 관리(`/admin/menus`)에서 `/admin/interview-supplements` 소메뉴를 등록해야 사이드바에 보인다([role-menu](role-menu.md)).
- 스케줄 재업로드는 면접을 지우고 다시 만들지만 지원자별 시간·답변은 지원서 기준이라 남는다. 재업로드로 대상에서 빠진 지원자의 답변은 목록에서만 사라진다.
- 기존 마이페이지 목록 위의 "지원자 추가사항 입력" 자리표시자 버튼·모달("준비 중입니다.")은 지원 목록 열로 대체해 없앴다.
