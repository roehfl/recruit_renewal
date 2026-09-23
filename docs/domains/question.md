# 질문 템플릿·공고별 질문 (`question`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [job-posting](job-posting.md) (공고·공고 상태·`adminJobPostingApi` 공유) · [application-form](application-form.md) (질문 탭을 품은 양식 상세 화면·편집 가능 판정·필수 정책 집계) · [application-sections](application-sections.md) (지원자 질문 조회·답변 저장) · [application](application.md) (제출 검증) · [admin-application](admin-application.md) (관리자 답변 조회·PDF)

## 요약

- 관리자가 **전역 질문 은행**(`QuestionTemplate`)을 관리하고, 공고마다 **공고 질문**(`JobPostingQuestion`)을 배치한다. 공고 질문은 템플릿에서 불러오거나 직접 입력한다.
- 공고 질문은 템플릿 값을 **복사한 스냅샷**이다. 템플릿을 고치거나 비활성화해도 이미 배치된 공고 질문은 바뀌지 않는다. `questionTemplateId`는 출처 기록용이다.
- 공고 질문의 추가·삭제·순서 변경은 `DRAFT` 공고에서만 된다. 게시 후(`PUBLISHED`/`CLOSED`)에는 문구(`questionText`/`helperText`)만 고칠 수 있다.
- 화면: 템플릿 목록·편집(`/admin/question-templates`, `/admin/question-template/:id?/edit`)과 지원서 양식 상세의 "질문" 탭(`ApplicationFormQuestionTab.vue`).

## 용어

| 용어 | 뜻 |
|---|---|
| 질문 템플릿 (`QuestionTemplate`) | 전역 질문 은행 항목. `title`, `defaultRequired`, `defaultMaxLength`, `active` 보유. 삭제 없음, `active` soft disable만 |
| 공고 질문 (`JobPostingQuestion`) | 특정 공고에 배치된 질문. nullable `questionTemplate` FK(출처). **`active` 개념 없음**(필요 없으면 hard delete) |
| 문구 필드 | `questionText`, `helperText` — 게시 후에도 수정 가능 |
| 정책 필드 | `category`, `answerType`, `required`, `minLength`, `maxLength`, `sortOrder` — 게시 후 변경 불가 |
| 구조 변경 | 질문 생성·삭제·순서 변경. `DRAFT` 공고 전용 |
| `QuestionCategory` | `SELF_INTRODUCTION`(자기소개) / `GENERAL`(기본질문) / `JOB_SPECIFIC`(직무질문) / `ETC`(기타). 자기소개서는 별도 도메인이 아니라 이 category로 처리 |
| `QuestionAnswerType` | `SHORT_TEXT`(단답형, `maxLength` ≤ 500) / `LONG_TEXT`(서술형, `maxLength` ≤ 5000) |
| 공고 상태 | `JobPostingStatus` = `DRAFT` / `PUBLISHED` / `CLOSED` ([job-posting](job-posting.md) 소유) |
| 답변 스냅샷 | `ApplicationAnswer.questionTextSnapshot` 등 — 답변 저장 시점의 질문 값 ([application-sections](application-sections.md) 소유) |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/QuestionTemplateController.java` | `/admin/question-templates` 6개 엔드포인트 |
| controller | `{BE}/controller/JobPostingQuestionController.java` | `/admin/job-postings/{jobPostingId}/questions` 5개 엔드포인트 |
| service | `{BE}/service/QuestionTemplateService.java` | 템플릿 CRUD·활성 전이·페이지 검증. 유형별 길이 상한 상수 `SHORT_TEXT_MAX_LENGTH`/`LONG_TEXT_MAX_LENGTH` 원천 |
| service | `{BE}/service/JobPostingQuestionService.java` | 공고 질문 생성(템플릿/직접)·수정(DRAFT 전체 / 게시 후 문구만)·순서·삭제 |
| entity | `{BE}/domain/entity/QuestionTemplate.java` | 테이블 `question_template`. 생성 시 `active=true` |
| entity | `{BE}/domain/entity/JobPostingQuestion.java` | 테이블 `job_posting_question`. `createFromTemplate`/`createDirect`/`update`/`changeOrder` |
| repository | `{BE}/domain/repository/QuestionTemplateRepository.java` | `findByActive` 페이지 조회 |
| repository | `{BE}/domain/repository/JobPostingQuestionRepository.java` | 정렬 조회·sortOrder 중복 검사·공고별 개수 집계(다른 카드 서비스도 사용) |
| repository | `{BE}/domain/repository/JobPostingQuestionPolicyCount.java` | 공고별 질문 수·필수 질문 수 집계 record (`countQuestionPolicyByJobPostingIds` 결과) |
| dto | `{BE}/dto/request/QuestionTemplateCreateRequest.java` | 템플릿 생성 요청 |
| dto | `{BE}/dto/request/QuestionTemplateUpdateRequest.java` | 템플릿 수정 요청(생성과 동일 필드, `active` 없음) |
| dto | `{BE}/dto/request/JobPostingQuestionCreateRequest.java` | 공고 질문 생성(`questionTemplateId` 선택, 나머지 선택·`sortOrder` 필수) |
| dto | `{BE}/dto/request/JobPostingQuestionUpdateRequest.java` | 공고 질문 수정(전 필드 필수, `helperText`/`minLength`만 선택, `questionTemplateId` 없음) |
| dto | `{BE}/dto/request/JobPostingQuestionReorderRequest.java` | 순서 변경 `{ questions: [...] }` |
| dto | `{BE}/dto/request/JobPostingQuestionOrderRequest.java` | 순서 항목 `{ questionId, sortOrder }` |
| dto | `{BE}/dto/response/QuestionTemplateResponse.java` | 템플릿 응답 |
| dto | `{BE}/dto/response/JobPostingQuestionResponse.java` | 공고 질문 응답 |
| enum | `{BE}/enumeration/QuestionCategory.java` | 질문 분류 4종 |
| enum | `{BE}/enumeration/QuestionAnswerType.java` | 답변 유형 2종 |
| exception | `{BE}/exception/InvalidQuestionTemplateException.java` | 템플릿 검증 실패 → 400 |
| exception | `{BE}/exception/QuestionTemplateNotFoundException.java` | 템플릿 없음 → 404 |
| exception | `{BE}/exception/InvalidJobPostingQuestionException.java` | 공고 질문 검증·상태 위반 → 400 |
| exception | `{BE}/exception/JobPostingQuestionNotFoundException.java` | 공고 질문 없음(다른 공고 소속 포함) → 404 |
| test | `{BT}/service/QuestionTemplateServiceTest.java` | 템플릿 서비스 규칙 13건 |
| test | `{BT}/service/JobPostingQuestionServiceTest.java` | 공고 질문 규칙 20건(게시 후 문구 수정·정책 변경 거부 포함) |
| test | `{BT}/controller/QuestionTemplateControllerTest.java` | 템플릿 API·오류 응답·PUT/DELETE 405 |
| test | `{BT}/controller/JobPostingQuestionControllerTest.java` | 공고 질문 API·오류 응답·PUT/DELETE 405 |

### 프론트

| 구분 | 파일 | 역할 |
|---|---|---|
| route | `{FE}/routes/adminRoutes.ts` | (공유) `AdminQuestionTemplates`(`question-templates`), `AdminJobPostingQuestionTemplateEdit`(`question-template/:id?/edit`) |
| view | `{FE}/views/admin/applicant/AdminQuestionTemplatesView.vue` | 템플릿 목록(size 10)·사용여부 스위치(activate/deactivate)·수정 버튼(상세로 이동) |
| view | `{FE}/views/admin/applicant/AdminQuestionTemplateEditView.vue` | 템플릿 등록/수정(`:id` 없으면 등록) |
| view | `{FE}/views/admin/applicationForm/ApplicationFormQuestionTab.vue` | 양식 상세의 질문 탭: 목록·펼쳐서 문구 수정·순서 이동·삭제·추가 모달 |
| component | `{FE}/views/admin/applicationForm/questionModal/QuestionModalBody.vue` | "질문 추가"(직접 입력) 모달 |
| component | `{FE}/views/admin/applicationForm/questionModal/QuestionTemplatesModalBody.vue` | "질문 템플릿 불러오기" 모달(활성 템플릿만, size 6) → 선택 시 폼에 채움 |
| component | `{FE}/views/admin/applicationForm/questionModal/AddQuestionBody.vue` | 질문 입력 폼·클라이언트 검증(두 모달 공용) |
| api | `{FE}/api/adminJobPostingApi.ts` | (공유, [job-posting](job-posting.md)과 같은 모듈) 질문 함수 12개: 템플릿 7개(목록 조회 2개 함수 포함)·공고 질문 5개 |
| types | `{FE}/types/question.ts` | `QuestionTemplateRequest`/`QuestionTemplateItem`/`QuestionRequest`/`QuestionItem`/`QuestionReOrderRequest`/`QuestionForm` |

## API 계약

권한: 모두 `/api/admin/**` → `ADMIN`·`RECRUIT_ADMIN` (`{BE}/config/SecurityConfig.java`). 응답은 모두 `ApiResponse<...>` 래핑.

| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |
|---|---|---|---|---|---|
| 🟢 | GET | /admin/question-templates | query `page`(0), `size`(20, 1~100), `active?`(미지정=전체) | `PageResponse<QuestionTemplateResponse>` | ADMIN·RECRUIT_ADMIN |
| 🟢 | GET | /admin/question-templates/{templateId} | — | `QuestionTemplateResponse`(비활성도 조회 가능) | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/question-templates | `{ title, questionText, helperText?, category, answerType, defaultRequired, defaultMaxLength }` | `QuestionTemplateResponse`(`active=true`) | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/question-templates/{templateId} | 생성과 동일(`active` 없음) | `QuestionTemplateResponse` | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/question-templates/{templateId}/deactivate | — | `QuestionTemplateResponse`(`active=false`), 멱등 | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/question-templates/{templateId}/activate | — | `QuestionTemplateResponse`(`active=true`), 이미 활성이면 400 | ADMIN·RECRUIT_ADMIN |
| 🟢 | GET | /admin/job-postings/{jobPostingId}/questions | — | `List<JobPostingQuestionResponse>`(`sortOrder`↑, `id`↑) | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/job-postings/{jobPostingId}/questions | `{ questionTemplateId?, questionText?, helperText?, category?, answerType?, required?, minLength?, maxLength?, sortOrder }` | `JobPostingQuestionResponse` | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/job-postings/{jobPostingId}/questions/{questionId} | `{ questionText, helperText?, category, answerType, required, minLength?, maxLength, sortOrder }` | `JobPostingQuestionResponse` | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/job-postings/{jobPostingId}/questions/reorder | `{ questions: [{ questionId, sortOrder }] }` — 공고 질문 전부 | `List<JobPostingQuestionResponse>` | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/job-postings/{jobPostingId}/questions/{questionId}/delete | — | `Void`(hard delete) | ADMIN·RECRUIT_ADMIN |

### 엔드포인트 상세

**응답 모양**

- `QuestionTemplateResponse`: `{ templateId, title, questionText, helperText, category, answerType, defaultRequired, defaultMaxLength, active, createdAt, updatedAt }`
- `JobPostingQuestionResponse`: `{ questionId, questionTemplateId, questionText, helperText, category, answerType, required, minLength, maxLength, sortOrder, createdAt, updatedAt }` — 2026-09-09 `active` 필드 제거.

**질문 템플릿 (전역 질문 은행)** — 삭제 API 없음, `active` 플래그 soft disable만(DB row 유지).

- 상태 메모: 2026-09-19 코드 기준 확정(FE 호출 확인). FE(`AdminQuestionTemplatesView.vue`, `AdminQuestionTemplateEditView.vue`, `QuestionTemplatesModalBody.vue`)가 6개 모두 호출하며 요청·응답 모양은 백엔드와 맞다.
- GET 목록: 정렬 지정 없음(`findAll(pageable)`/`findByActive`). FE 목록 화면은 `active` 없이 size 10, 불러오기 모달은 `active=true` size 6.
- GET 단건: 비활성 템플릿도 허용. 오류 404(미존재).
- POST 생성/수정: 요청에 `active` 없음 — 활성 상태는 수정 API로 못 바꾸고 deactivate/activate 명령으로만 전이. 오류 400(검증 실패, enum 값 오류 포함), 404(수정 시 미존재).
- deactivate: `active=false` UPDATE(row 삭제 아님). 비활성 템플릿은 **신규** 공고 질문 생성에 쓸 수 없다(기존 공고 질문 스냅샷은 영향 없음). 이미 비활성이어도 200(멱등). 오류 404.
- activate: 비활성 → `active=true`. 이미 활성이면 400 `Active template cannot be activated again.`(예외 방식 확정). 오류 404.
- 매핑(FE `adminJobPostingApi` ↔ BE `QuestionTemplateController`): `getQuestionTemplates`·`getQuestionTemplatesActive` ↔ `getTemplates`, `selectQuestionTemplate` ↔ `getTemplate`, `createQuestionTemplate` ↔ `createTemplate`, `updateQuestionTemplate` ↔ `updateTemplate`, `setQuestionDeactive` ↔ `deactivateTemplate`, `setQuestionActive` ↔ `activateTemplate`.

**공고별 질문 구성** — 🟢 확정 (2026-09-09).

- 생성·삭제·순서 변경은 `DRAFT` 공고에서만. 그래서 삭제 시점엔 지원자 답변이 있을 수 없다.
- 수정은 게시 후에도 가능하되 `questionText`/`helperText`만(오타 수정 목적, 2026-09-09). 정책 필드를 바꾸려 하면 400 — 이미 작성된 답변이 소급해서 정책 위반이 되기 때문이다(제출 후에도 답변 수정이 가능하므로 `maxLength`를 줄이면 그 지원자가 자기 답변을 저장할 수 없게 됨).
- 생성: 템플릿에서 가져오거나(`questionTemplateId`) 직접 입력. 템플릿 기반이면 요청에서 `null`인 필드를 템플릿 값으로 채운다. 비활성 템플릿은 400(템플릿 쪽 `active`는 유지).
- 이미 답변한 지원자의 관리자 화면·PDF는 답변 시점 스냅샷(`ApplicationAnswer.questionTextSnapshot`)을 보여주므로 문구 수정이 반영되지 않는다 — 지원자가 본 문구를 보존하는 의도된 동작.
- 생성/수정 오류: 400(검증 실패 / 생성 시 DRAFT 아님 / 게시 후 정책 필드 변경 / `sortOrder` 중복 / 비활성 템플릿), 404(공고·질문·템플릿 미존재).
- reorder: 전체 질문의 `sortOrder`를 한 번에 재배치. 요청에 현재 질문 전부가 정확히 한 번씩 있어야 한다. 빈 목록은 400.
- delete: 2026-09-09 hard delete로 변경(이전 `active=false` soft delete, 응답도 이전엔 질문 객체). 경로는 유지. 오류 400(DRAFT 아님), 404(공고·질문 미존재). 발행 후에는 삭제 불가.
- 매핑(FE `adminJobPostingApi` ↔ BE `JobPostingQuestionController`): `getQuestionList` ↔ `getQuestions`, `saveQuestion` ↔ `createQuestion`, `updateQuestion` ↔ `updateQuestion`, `reOrderQuestion` ↔ `reorderQuestions`, `deleteQuestion` ↔ `deleteQuestion`.
- 지원자 쪽 질문·답변 API(`/applications/{applicationId}/questions`, `/applications/{applicationId}/answers`)는 [application-sections](application-sections.md), 관리자 답변 조회는 [admin-application](admin-application.md).

## 규칙·불변식

**질문 템플릿**

1. 필드 검증: `title` 필수·≤200, `questionText` 필수·≤2000, `helperText` ≤2000, `category`·`answerType`·`defaultRequired` 필수. DTO Bean Validation이 먼저 400을 내고 서비스가 다시 검사한다 (`{BE}/service/QuestionTemplateService.java` — `validateTemplateRequest`).
2. `defaultMaxLength` ≥1, `SHORT_TEXT`면 ≤500, `LONG_TEXT`면 ≤5000 (`{BE}/service/QuestionTemplateService.java` — `validateMaxLength`).
3. 삭제 없음. 생성 시 `active=true`, 활성 변경은 deactivate/activate로만 (`{BE}/domain/entity/QuestionTemplate.java` — 생성자·`deactivate`·`activate`).
4. deactivate는 멱등, activate는 이미 활성이면 400 (`{BE}/service/QuestionTemplateService.java` — `validateTemplateInactive`).
5. 목록 `page` ≥0, `size` 1~100, `active` null이면 전체 (`{BE}/service/QuestionTemplateService.java` — `validatePageRequest`·`getTemplates`).

**공고 질문**

6. 생성·순서 변경·삭제는 공고가 `DRAFT`일 때만. 아니면 400 `Question configuration is allowed only for DRAFT JobPosting.` (`{BE}/service/JobPostingQuestionService.java` — `validateJobPostingDraft`).
7. 수정: `DRAFT`면 모든 필드 변경 가능(+`sortOrder` 중복 검사). `PUBLISHED`/`CLOSED`면 정책 필드 6개가 기존 값과 하나라도 다르면 400 `Only question text can be changed after the job posting is published.` 요청은 전 필드를 보내야 하므로 **정책 필드는 현재 값을 그대로** 보낸다. `minLength`의 `null`과 `0`은 다른 값으로 비교된다(`Objects.equals`) (`{BE}/service/JobPostingQuestionService.java` — `updateQuestion`·`validateTextOnlyUpdate`).
8. 템플릿 기반 생성: 템플릿이 없으면 404, 비활성이면 400 `Inactive question template cannot be used.` (`{BE}/service/JobPostingQuestionService.java` — `findActiveTemplate`).
9. 템플릿 기반 생성 시 요청의 `null` 필드는 템플릿 값으로 채운다: `questionText`, `helperText`, `category`, `answerType`, `required`←`defaultRequired`, `maxLength`←`defaultMaxLength`. `minLength`는 요청 값만 쓴다 (`{BE}/service/JobPostingQuestionService.java` — `templateSnapshot`).
10. `questionTemplateId`는 생성 때만 받는다. 수정은 템플릿 참조를 바꾸지 않는다(`JobPostingQuestionUpdateRequest`에 필드 없음). 템플릿 수정·비활성은 기존 공고 질문에 전파되지 않는다(값 복사 스냅샷) (`{BE}/domain/entity/JobPostingQuestion.java` — `createFromTemplate`·`update`).
11. 스냅샷 검증(생성·수정 공통): `questionText` 필수·≤2000, `category`·`answerType`·`required` 필수, `sortOrder` ≥0. 직접 생성(`questionTemplateId` 없음)이면 이 값을 전부 요청으로 줘야 한다 (`{BE}/service/JobPostingQuestionService.java` — `validateQuestionSnapshot`).
12. 길이: `maxLength` ≥1(필수), `minLength` ≥0(선택), `minLength` ≤ `maxLength`, `SHORT_TEXT` `maxLength` ≤500, `LONG_TEXT` ≤5000 (`{BE}/service/JobPostingQuestionService.java` — `validateLength`).
13. `sortOrder`는 공고 안에서 중복 불가 — 생성 시와 DRAFT 수정 시 검사. DB unique 제약은 없다(인덱스만) (`{BE}/service/JobPostingQuestionService.java` — `validateSortOrderForCreate`·`validateSortOrderForUpdate`).
14. 조회 정렬은 `sortOrder ASC, id ASC` (`{BE}/domain/repository/JobPostingQuestionRepository.java` — `findByJobPostingIdOrderBySortOrderAscIdAsc`).
15. reorder: 항목 비어 있으면 400, `questionId`·`sortOrder` 필수·`sortOrder` ≥0, `sortOrder` 중복 400, `questionId` 중복 400, 다른 공고(또는 없는) 질문 id 404, 공고 질문 전부를 포함하지 않으면 400 (`{BE}/service/JobPostingQuestionService.java` — `validateReorderRequest`).
16. 삭제는 hard delete. DRAFT 전용이라 `ApplicationAnswer` FK를 건드리지 않는다 (`{BE}/service/JobPostingQuestionService.java` — `deleteQuestion`).
17. 질문은 항상 `jobPostingId`와 함께 찾는다 — 다른 공고의 질문 id면 404 (`{BE}/service/JobPostingQuestionService.java` — `findQuestion`).

**공통**

18. 예외 → HTTP: `Invalid*Question*Exception` 400, `*NotFoundException` 404, 공고 없음 `JobPostingNotFoundException` 404 (`{BE}/exception/GlobalExceptionHandler.java`).
19. GET/POST만 쓴다. 같은 경로의 PUT/DELETE는 405(수정·삭제도 POST 명령) (`{BT}/controller/JobPostingQuestionControllerTest.java` — `put_and_delete_methods_are_not_supported`).
20. 답변 쪽 효과([application](application.md)): `required=true`면 제출 시 답변 필수, `maxLength`는 저장·제출 시 검사, `minLength`는 제출 시 비어 있지 않은 답변에만 검사 (`{BE}/service/ApplicationSubmitValidator.java`).

**프론트**

21. 탭 권한은 부모가 계산한다([application-form](application-form.md)): `editable` = `CLOSED` 아님 && (`DRAFT` 또는 `receptionStatus=UPCOMING`), `structureEditable` = `DRAFT`. 추가·불러오기·순서·삭제 버튼은 `structureEditable`, 저장 버튼은 `editable`로 막는다. 즉 UI의 문구 수정은 접수 시작 전까지만(백엔드는 게시 후 언제나 허용) (`{FE}/views/admin/applicationForm/AdminApplicationFormDetailView.vue`).
22. 펼침 수정 폼은 정책 필드를 **항상** disabled로 둔다(DRAFT 포함). DRAFT에서 정책을 바꾸려면 삭제 후 다시 추가 (`{FE}/views/admin/applicationForm/ApplicationFormQuestionTab.vue` — 템플릿 `expandedRowRender`).
23. 수정 요청은 `minLength`를 `null` 그대로 보낸다(0으로 바꾸면 게시 후 400). `sortOrder`는 펼친 복사본이 아니라 현재 목록 값 (`{FE}/views/admin/applicationForm/ApplicationFormQuestionTab.vue` — `updateQuestion`).
24. 생성 요청: `sortOrder` = 현재 개수+1, `minLength` 미입력은 0, 템플릿에서 불러왔으면 `questionTemplateId` 전달 (`{FE}/views/admin/applicationForm/ApplicationFormQuestionTab.vue` — `saveQuestion`; `{FE}/views/admin/applicationForm/questionModal/QuestionTemplatesModalBody.vue` — `handleRowClick`).
25. 순서 이동은 이웃과 자리를 바꾸고 전체를 1..n으로 다시 매겨 reorder 호출, 실패하면 서버 순서를 다시 읽는다. 삭제 성공 후 남은 질문이 있을 때만 재번호 reorder(빈 요청은 400) (`{FE}/views/admin/applicationForm/ApplicationFormQuestionTab.vue` — `move`·`reOrder`·`deleteQuetion`).
26. 저장 실패 시 모달 입력을 비우지 않는다. 성공했을 때만 닫고 `questionFormKey`를 올려 폼을 새로 만든다 (`{FE}/views/admin/applicationForm/ApplicationFormQuestionTab.vue` — `saveQuestion`).
27. 질문 입력 폼 검증은 백엔드와 같게 유지: 필수값, `maxLength` 정수>0, `minLength` 정수≥0, `minLength` ≤ `maxLength`, 유형별 상한 500/5000(`TYPE_MAX_LENGTH`) (`{FE}/views/admin/applicationForm/questionModal/AddQuestionBody.vue` — `validation`).
28. 템플릿 편집 검증: 템플릿명·질문 유형·답변 유형·질문·필수 여부 필수, 최대 글자 수 정수≥1, `a-input type=number` 문자열을 `Number()`로 변환. 유형별 상한은 검사하지 않음(백엔드 400) (`{FE}/views/admin/applicant/AdminQuestionTemplateEditView.vue` — `validateForm`·`save`).
29. 템플릿 목록 스위치: 토글된 새 값이 `true`면 activate, `false`면 deactivate 호출, 끝나면 목록 재조회 (`{FE}/views/admin/applicant/AdminQuestionTemplatesView.vue` — `changeQuestionTemplateActive`).

## 변경 레시피

### 공고 질문에 필드 추가 (예: 새 정책 필드)

1. `{BE}/domain/entity/JobPostingQuestion.java` 필드·팩토리·`update` 추가. 스키마는 Hibernate `ddl-auto`(기본 `update`)로 갱신되므로 NOT NULL 컬럼은 기존 행 값 처리를 먼저 정한다.
2. 요청 DTO(`JobPostingQuestionCreateRequest`, `JobPostingQuestionUpdateRequest`)·`JobPostingQuestionResponse` 수정.
3. `{BE}/service/JobPostingQuestionService.java`: `QuestionSnapshot`, `directSnapshot`, `templateSnapshot`, `validateQuestionSnapshot`. 정책 필드면 `validateTextOnlyUpdate` 비교에도 추가(게시 후 변경 금지).
4. 템플릿에도 기본값이 필요하면 `QuestionTemplate`·템플릿 DTO·`QuestionTemplateService` 같은 순서로.
5. 테스트: `{BT}/service/JobPostingQuestionServiceTest.java`, `{BT}/controller/JobPostingQuestionControllerTest.java`. 답변 검증에 쓰이면 [application](application.md) 카드 레시피도 따른다.
6. 프론트: `{FE}/types/question.ts` → `AddQuestionBody.vue` 입력·검증 → `ApplicationFormQuestionTab.vue` `saveQuestion`/`updateQuestion`/펼침 폼.
7. 이 카드 `## API 계약`·`## 규칙·불변식` 갱신 → `node tools/check-docs.mjs`.

### 게시 후 수정 허용 범위 변경

1. `{BE}/service/JobPostingQuestionService.java` — `validateTextOnlyUpdate`(허용 필드), `validateJobPostingDraft`(구조 변경 허용 상태).
2. 답변 소급 위반 가능성 확인: `{BE}/service/ApplicationAnswerService.java`([application-sections](application-sections.md)), `{BE}/service/ApplicationSubmitValidator.java`([application](application.md)).
3. `JobPostingQuestionServiceTest`의 `update_question_policy_fails_after_publish`·`update_question_text_is_allowed_after_publish` 갱신.
4. 프론트: `ApplicationFormQuestionTab.vue` 알림 문구·disabled 조건. `editable`/`structureEditable` 계산은 [application-form](application-form.md) 카드 소유 파일에서.
5. 카드 규칙 6·7·21~23 갱신 → `node tools/check-docs.mjs`.

### 답변 유형·분류 추가 또는 길이 상한 변경

1. enum: `{BE}/enumeration/QuestionAnswerType.java` / `{BE}/enumeration/QuestionCategory.java`. enum 유지 원칙(CommonCode로 옮기지 않음, 함정 참고).
2. 길이 상한은 여러 곳에 중복돼 있다: `QuestionTemplateService`(상수 원천), `JobPostingQuestionService.validateLength`, `{BE}/service/ApplicationAnswerService.java`, `{BE}/service/ApplicationCompletionReadChecker.java`, `{BE}/service/ApplicationSubmitValidator.java` — 전부 맞춘다.
3. 프론트 한글 라벨·라디오가 중복돼 있다: `AdminQuestionTemplatesView.vue`, `AdminQuestionTemplateEditView.vue`, `ApplicationFormQuestionTab.vue`, `QuestionTemplatesModalBody.vue`, `AddQuestionBody.vue`(`TYPE_MAX_LENGTH`), `{FE}/types/question.ts`. 지원자 화면 쪽(`{FE}/types/application/sections/questionAnswer.ts` 등)은 [application-sections](application-sections.md) 카드.
4. 테스트: 이 카드 4개 + 영향 받는 `ApplicationAnswerServiceTest`·`ApplicationSubmitValidatorTest`.
5. 카드 `## 용어` 갱신 → `node tools/check-docs.mjs`.

### 템플릿 필드·목록 조건 변경

1. `{BE}/domain/entity/QuestionTemplate.java` → 템플릿 DTO 3개 → `{BE}/service/QuestionTemplateService.java`(`validateTemplateRequest`, 목록이면 `{BE}/domain/repository/QuestionTemplateRepository.java`).
2. 테스트: `{BT}/service/QuestionTemplateServiceTest.java`, `{BT}/controller/QuestionTemplateControllerTest.java`.
3. 프론트: `{FE}/types/question.ts` → `adminJobPostingApi.ts` 템플릿 함수 → 목록/편집 화면, 불러오기 모달이 템플릿 값을 폼에 채우는 `handleRowClick`.
4. 카드 갱신 → `node tools/check-docs.mjs`.

## 검증

백엔드(`recruit_back/recruit_backend/`에서, 이 카드 테스트 4개):

```powershell
# Windows PowerShell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.QuestionTemplateServiceTest" --tests "com.shinyoung.recruit.service.JobPostingQuestionServiceTest" --tests "com.shinyoung.recruit.controller.QuestionTemplateControllerTest" --tests "com.shinyoung.recruit.controller.JobPostingQuestionControllerTest" --no-daemon
```

```bash
# Linux
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "com.shinyoung.recruit.service.QuestionTemplateServiceTest" --tests "com.shinyoung.recruit.service.JobPostingQuestionServiceTest" --tests "com.shinyoung.recruit.controller.QuestionTemplateControllerTest" --tests "com.shinyoung.recruit.controller.JobPostingQuestionControllerTest" --no-daemon
```

`JobPostingQuestion` 엔티티·`JobPostingQuestionRepository`를 바꿨으면 이 저장소를 쓰는 다른 카드 테스트도 돌린다: `ApplicationAnswerServiceTest`, `ApplicationSubmitValidatorTest`, `ApplicationFormLayoutServiceTest`, `ApplicationFormPageServiceTest`, `AdminApplicationFormSummaryServiceTest`, `AdminApplicationSectionServiceTest`, `JobPostingPublicServiceTest`(모두 `com.shinyoung.recruit.service` 패키지).

프론트(`recruit_front/`에서). 이 도메인의 vitest spec은 없다.

```bash
npm run type-check
```

문서(레포 루트에서): `node tools/check-docs.mjs`

## 함정·결정

- `86d12c9` 게시 후 질문 수정에서 `minLength` `null`을 0으로 바꿔 보내 400이 나던 것 수정, 템플릿에서 불러온 질문이 `questionTemplateId`를 안 보내 출처가 남지 않던 것 수정 — 둘 다 재발 주의(규칙 23·24).
- `8d7485d` 질문 탭에 `structureEditable` 도입(DRAFT 외 추가·삭제·순서 비활성), 저장 실패 시 모달 입력 유지, 순서 저장 실패 시 서버 순서 재조회, 템플릿 편집 `defaultMaxLength` 숫자 변환(`a-input type=number`는 문자열 반환).
- `60858ca` 결정: 공고 질문 `active` 제거 → hard delete, 게시 후 문구만 수정 허용. 공고 질문에 soft delete·activate를 되살리지 않는다(템플릿 `active`는 유지).
- `f021646` 결정: 템플릿 activate 명령 추가, 이미 활성이면 예외(400)로 확정.
- `sortOrder` DB unique 제약 없음 — 서비스의 exists 검사가 유일한 방어라 동시 생성 시 중복될 수 있다. FE는 생성 시 `개수+1`을 쓰므로 순서에 빈 번호가 있으면 기존 값과 겹쳐 400이 날 수 있다(삭제 후 재번호로 보통 1..n 유지).
- FE 응답 타입이 실제와 다르다(`{FE}/api/adminJobPostingApi.ts`): `saveQuestion`/`updateQuestion` → `ApiResponse<QuestionRequest>`(실제 `QuestionItem`), `deleteQuestion` → `ApiResponse<QuestionItem>`(실제 `Void`), `reOrderQuestion` → `ApiResponse<QuestionReOrderRequest>`(실제 `QuestionItem[]`), `create/updateQuestionTemplate` → `ApiResponse<QuestionTemplateRequest>`(실제 `QuestionTemplateItem`). 지금은 응답 본문을 읽지 않아 무해하다. 응답을 쓰기 전에 타입부터 고친다.
- 이름 오타가 동작에 얽혀 있다: 모달의 `v-model:opne`·`update:opne`는 양쪽이 같은 오타라 동작한다(고치면 둘 다). `record.quistionId`는 항상 빈 값. `deleteQuetion`, `cilckTemplate`는 이름만 오타.
- 이름이 도메인과 안 맞는다: 라우트 `AdminJobPostingQuestionTemplateEdit`는 공고와 무관한 템플릿 편집 화면, 디렉터리 `{FE}/views/admin/applicant/`는 지원자와 무관한 템플릿 화면이다. 질문 API 함수는 `adminJobPostingApi`에 섞여 있다.
- `JobPostingQuestionRepository`는 [application](application.md)·[application-form](application-form.md)·[admin-application](admin-application.md)·[job-posting](job-posting.md) 서비스가 함께 쓴다(`existsByJobPostingId`, `existsByJobPostingIdAndRequiredTrue`, `countQuestionPolicyByJobPostingIds` 등). 시그니처 변경 시 검증 절의 영향 테스트를 돌린다.
- 미사용(삭제는 승인 후): `QuestionTemplateRepository.existsByIdAndActiveTrue`, `{FE}/types/question.ts`의 `QuestionList`.
- `recruit_back/recruit_backend/docs/adr/0003-commoncode-additive-no-enum-migration.md` — 기존 enum은 CommonCode로 전환하지 않는다. `QuestionCategory`/`QuestionAnswerType`도 enum으로 유지.
