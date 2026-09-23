# 지원서 작성·제출 (`application`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [application-sections](application-sections.md)(섹션 9종 API·저장 검증·섹션 화면) · [job-posting](job-posting.md) · [application-form](application-form.md) · [question](question.md) · [attachment](attachment.md) · [master-data](master-data.md) · [stage-result](stage-result.md) · [auth-account](auth-account.md) · [admin-application](admin-application.md) · [privacy-audit](privacy-audit.md)

## 요약

- 지원자(`ROLE_APPLICANT`)가 모집분야·근무지를 골라 지원서(`JobApplication`)를 만들고, 섹션 9종을 임시저장한 뒤 최종 제출한다. **접수기간 중에는 제출 후에도 수정·재제출**할 수 있다.
- 상태: `DRAFT` → `SUBMITTED`(재제출 시 `submittedAt` 갱신) → `WITHDRAWN`(종착). 지원자·공고 쌍당 1건.
- 이 카드 범위: 지원서 생성·지원분야 수정·제출·철회, 내 지원 현황, 대시보드(완성도), form-page 로드, 공고별 기지원 여부, 섹션 공통 접근 규칙, 작성 화면 틀. **섹션 9종의 GET/POST·섹션별 저장 검증·섹션 컴포넌트는 [application-sections](application-sections.md).**
- 화면: `ApplicationFormView.vue`가 두 라우트를 처리한다 — `ApplicationStart`(`/applicant/:jobPostingId/apply`, 모집분야 선택 후 생성)·`application`(`/applicant/:applicationId/form`, 작성). 페이지·섹션 배치는 form-page 응답(레이아웃은 [application-form](application-form.md))을 따른다.
- 경계: 섹션 → [application-sections](application-sections.md) / `{BE}/controller/ApplicationAttachmentController.java` → [attachment](attachment.md) / `{BE}/controller/ApplicationStageResultController.java` → [stage-result](stage-result.md) / `{BE}/service/ApplicationFormPageService.java`·양식 설정 → [application-form](application-form.md) / 질문 정의 → [question](question.md).

## 용어

| 용어 | 뜻 |
|---|---|
| 지원서 (`JobApplication`) | 테이블 `job_application`, unique `(applicant_id, job_posting_id)`. 이름·공고명·모집분야명·근무지명 스냅샷과 `workLocationCode` 보유 |
| 섹션 9종 | 지원서 입력 단위. 목록·섹션 타입 코드는 [application-sections](application-sections.md). `ATTACHMENT` 섹션은 지원자 화면에서 폐지 |
| 완성도 sectionCode | 대시보드 판정 코드. 섹션 타입과 같되 **`QUESTION_ANSWER`↔`QUESTION`**, 추가로 `FORM_CONFIG`·`ATTACHMENT`. `CAREER`는 판정 대상 아님 |
| `accepting` | 공고 `PUBLISHED` && 접수시작 ≤ now ≤ 접수종료 |
| `editable` | `status != WITHDRAWN && accepting`. 섹션 저장·지원분야 변경·제출 가능 여부 |
| 지원분야 | 모집분야(`JobPosition`) + 근무지 코드(CommonCode `WORK_LOCATION`). 후보 0개=선택 없음, 1개=자동 선택, N개=선택 필수 |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/ApplicationController.java` | 생성·조회·내 목록·대시보드·form-page·지원분야 수정·제출·철회·공고별 내 지원서 |
| service | `{BE}/service/JobApplicationService.java` | 생성·지원분야 수정·제출·철회·내 목록. 관리자 검색 메서드도 포함([admin-application](admin-application.md)) |
| service | `{BE}/service/ApplicationSubmitValidator.java` | 최종 제출 검증(한글 메시지) |
| service | `{BE}/service/ApplicationSubmittedEvent.java` | 제출·재제출 성공 이벤트(`applicationId`). 커밋 후 [message-delivery](message-delivery.md)의 리스너가 제출 완료 메일을 보낸다 |
| service | `{BE}/service/ApplicationCompletionReadChecker.java` | 완성도 판정 |
| service | `{BE}/service/ApplicationDashboardService.java` | 대시보드 플래그+완성도+최신 발표 결과 |
| service | `{BE}/service/ApplicationSectionAccessService.java` | 섹션 공통: 본인 소유·편집 가능·섹션 사용 여부(섹션 서비스·첨부가 사용) |
| service | `{BE}/service/CurrentApplicantService.java` | 세션 → 지원자 id(401/403). 섹션·첨부·전형결과·면접·계정 컨트롤러도 사용 |
| entity | `{BE}/domain/entity/JobApplication.java` | 루트. `create`·`updateDraft`·`updateWorkLocation`·`submit`·`withdraw` |
| repository | `{BE}/domain/repository/JobApplicationRepository.java` | 본인 지원서·내 목록·대시보드 조회(관리자 검색 쿼리도 포함) |
| dto | `{BE}/dto/request/ApplicationCreateRequest.java` `{BE}/dto/request/ApplicationUpdateRequest.java` `{BE}/dto/response/ApplicationDetailResponse.java` `{BE}/dto/response/MyApplicationResponse.java` `{BE}/dto/response/ApplicationDashboardResponse.java` `{BE}/dto/response/ApplicationCompletionSummaryResponse.java` `{BE}/dto/response/ApplicationSectionReadinessResponse.java` | 지원서 루트·대시보드 |
| enum | `{BE}/enumeration/JobApplicationStatus.java` | 지원서 상태 |
| exception | `{BE}/exception/InvalidJobApplicationException.java` | 검증 실패 → 400(섹션 서비스도 사용) |
| exception | `{BE}/exception/JobApplicationNotFoundException.java` | 없음·타인 지원서 → 404 |
| test | `{BT}/controller/ApplicationControllerTest.java` `{BT}/service/JobApplicationServiceTest.java` `{BT}/service/ApplicationSubmitValidatorTest.java` `{BT}/service/ApplicationSubmitValidatorBasicInfoTest.java` `{BT}/service/ApplicationCompletionReadCheckerTest.java` `{BT}/service/ApplicationDashboardServiceTest.java` `{BT}/service/ApplicationSectionAccessServiceTest.java` `{BT}/service/CurrentApplicantServiceTest.java` | 코어 |

### 프론트

| 구분 | 파일 | 역할 |
|---|---|---|
| route | `{FE}/routes/applicantRoutes.ts` | 공유. 이 카드 몫은 `ApplicationStart`·`application`(`requiresAuth`, `ROLE_APPLICANT`) |
| view | `{FE}/views/applicant/ApplicationFormView.vue` | 지원분야 선택·페이지 스텝·섹션 렌더·자동 임시저장·제출 |
| api | `{FE}/api/applicationApi.ts` | 공유. 이 카드 몫은 `getMyApplications`. `getStageResults`=[stage-result](stage-result.md), 비밀번호·가입=[auth-account](auth-account.md) |
| api | `{FE}/api/application/dashboardApi.ts` | `getApplicationDashboard` |
| types | `{FE}/types/application.ts` | 공유. form-page·`SectionComponentProps`·`SectionActionHandle`·내 지원 목록·`postingType` |
| types | `{FE}/types/application/dashboard.ts` | 대시보드 타입 |
| test | `{FE}/api/__tests__/applicationApi.spec.ts` | 내 목록 파라미터 |

## API 계약

권한 `APPLICANT`: `{BE}/config/SecurityConfig.java`에서 `/api/applications/**`와 `GET /api/job-postings/{jobPostingId}/application`이 `ROLE_APPLICANT`. 핸들러는 `CurrentApplicantService`로 지원자 id를 구하고(세션 없음 401, 지원자 아님 403) `findByIdAndApplicantId`로 본인 지원서만 찾는다(타인·없음 404). 응답은 `ApiResponse<T>`. 섹션 엔드포인트(`/applications/{id}/basic-info` 등 18개)는 [application-sections](application-sections.md).

| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |
|---|---|---|---|---|---|
| 🟢 | POST | /applications | `{ jobPostingId, jobPositionId, workLocationCode? }` | `Long` 지원서 id | APPLICANT |
| 🟢 | GET | /applications/{applicationId} | - | `ApplicationDetailResponse` | APPLICANT |
| 🟢 | GET | /applications/me | `page`=0, `size`=20(1~100) | `PageResponse<MyApplicationResponse>` | APPLICANT |
| 🟢 | GET | /applications/{applicationId}/dashboard | - | `ApplicationDashboardResponse` | APPLICANT |
| 🟢 | GET | /applications/{applicationId}/form-page | - | `ApplicationFormPageResponse` | APPLICANT |
| 🟢 | POST | /applications/{applicationId} | `{ jobPositionId, workLocationCode? }` | `Long` | APPLICANT |
| 🟢 | POST | /applications/{applicationId}/submit | - | `Long` | APPLICANT |
| 🟢 | POST | /applications/{applicationId}/withdraw | - | `Long` | APPLICANT |
| 🟢 | GET | /job-postings/{jobPostingId}/application | - | `ApplicationDetailResponse`, 없으면 404 | APPLICANT |

### 엔드포인트 상세

- `ApplicationController` 경로 변수는 `{applicationId:[0-9]+}` 정규식이라 `/applications/me`와 겹치지 않는다. 생성·지원분야 수정·form-page·제출은 `ApplicationFormView.vue`가 `apiClient`로 직접 호출한다(api 모듈 없음).
- `POST /applications`(2026-08-31 🟢): `workLocationNameSnapshot`(선택 시점 `displayName`)도 저장. 근무지: 후보 ≥1인데 코드 없음 400 / 후보에 없는 코드 400 / 후보 0인데 값 있음 400. 호출 시점(2026-09-11): 공고 상세 '지원하기' → 작성 시작 화면에서 모집분야·근무지 선택 후 '지원서 작성' 버튼. 모집분야가 1개여도 자동 생성하지 않는다.
- `POST /applications/{id}`(지원분야 수정, 2026-08-31 🟢): 검증은 생성과 동일(바뀐 모집분야 후보 기준). FE(2026-09-11): 헤더 상시 드롭다운 변경 → 확인창 → 저장, 취소 시 저장값 복구. 후보 목록은 공개 공고 상세 `GET /job-postings/{id}` 재사용([job-posting](job-posting.md), 전용 API 없음). `editable`일 때만 변경 가능.
- `GET /applications/{id}`: **FE 미사용**. `{ applicationId, applicantId, jobPostingId, jobPostingTitle, jobPositionId, jobPositionName, status, submittedAt, withdrawnAt, createdAt, updatedAt }`.
- `GET /applications/me`: 호출처는 [auth-account](auth-account.md) 소유 `ApplicantProfile.vue`. 정렬 `createdAt desc, id desc`. 행 `{ applicationId, jobPostingId, jobPostingTitle, jobPostingStatus, jobPositionId, jobPositionName, applicationStatus, createdAt, submittedAt, withdrawnAt, receptionStart/EndDateTime, accepting, announcedResultCount, latestAnnouncedStageName, latestResultStatus }`(결과 요약은 **발표된 결과만**). page<0·size<1·size>100 → 400.
- `GET /applications/{id}/dashboard`(🟢 확정): 작성 화면 스텝의 "완료수/필수수" 카운터 소스. **임시저장 여부가 아니라 저장된 데이터가 필수 규칙을 만족하는지** 판정. 응답 `completionSummary { requiredSectionCount, completedRequiredSectionCount, requiredMissingCount, optionalSectionCount, completedOptionalSectionCount, optionalIncompleteCount, requiredCompletionRate, submitBlockingIssueCount }`, `requiredMissingSections`·`optionalIncompleteSections: [{ sectionCode, sectionName, required, complete, reasonCode, message }]`(**미완만**), 그 외 `{ applicationId, jobPostingId, jobPostingTitle, jobPositionName, applicationStatus, accepting, editable, submittable, withdrawable, submittedAt, withdrawnAt, latestAnnouncedStageName, latestResultStatus }`. 플래그: `editable = status != WITHDRAWN && accepting`(2026-09-02, 제출 후에도 수정), `submittable = editable && 결함 0`(2026-09-11, 재제출 허용), `withdrawable = status == SUBMITTED && accepting`. FE는 필수 섹션 코드가 `requiredMissingSections`에 없으면 완료로 본다(`CAREER`는 완료로 단정 안 함). 재조회: form-page 직후 + 임시저장 성공 직후. 매핑 `dashboardApi.getApplicationDashboard()` ↔ `ApplicationController.getDashboard()`. 메시지는 영문.
- `GET /applications/{id}/form-page`(🟢 확정): 조립은 [application-form](application-form.md)(`ApplicationFormPageService`·`ApplicationFormPageResponse`). 응답 `{ applicationId, jobPostingId, jobPostingTitle, jobPostingStatus, postingType, jobPositionId, jobPositionName, workLocationCode, workLocationName, applicationStatus, receptionStartDateTime, receptionEndDateTime, accepting, editable, submittedAt, withdrawnAt, formConfig, sections: [...] }`. `postingType`(2026-07-06 🟢, 미설정 공고는 `PUBLIC_RECRUITMENT`)으로 학력 섹션이 성적 입력 모드를 고른다([application-sections](application-sections.md)). `workLocationCode`/`workLocationName`(2026-08-31 🟢, 스냅샷, 미선택 null).
- `POST /applications/{id}/submit`(🟢 확정 2026-09-18): 실패는 400 `ApiResponse.fail(message)`, **메시지 한글**("학력을 입력해야 제출할 수 있습니다." 등)을 FE가 그대로 표시. 질문 `minLength`도 서버가 검증(질문 섹션 없는 페이지에서 제출해 우회하던 문제 해결). 공고 수정으로 근무지 후보가 바뀌었으면 400. 재제출 시 `submittedAt`=마지막 제출 시각. FE 흐름은 규칙의 "프론트" 참고. 성공하면 `ApplicationSubmittedEvent`를 발행하고 커밋 후 비동기로 제출 완료 메일이 간다(2026-09-23, 메일 실패는 제출 결과와 무관).
- `POST /applications/{id}/withdraw`: **FE 미사용**. `SUBMITTED` + 공고 `PUBLISHED` + 접수기간 내만.
- `GET /job-postings/{jobPostingId}/application`(🟢 확정 2026-09-18, [job-posting](job-posting.md) 화면 섹션에서 이관): 호출처 `ApplicationDetailView.vue`(job-posting 소유). 본인 지원서(`status` 포함), 없으면 404 — FE는 404를 "미지원"으로 처리하고 `skipClientEventLog`로 텔레메트리 제외.

## 규칙·불변식

**지원서 생명주기** ({BE}/service/JobApplicationService.java)
- 생성: 공고 `PUBLISHED`·접수기간 내·`visible`·노출기간 내(null=무제한), 양식 config 존재, 같은 공고 지원서 없음(상태 무관 — 철회 후 재지원 불가), 모집분야가 그 공고 소속, 근무지 규칙. 이름 스냅샷 `userName`→`name`, 둘 다 없으면 400. (— create)
- 동시 생성은 DB unique 위반 → 409 "이미 처리되었거나 중복된 데이터입니다." ({BE}/exception/GlobalExceptionHandler.java — handleDataIntegrityViolation)
- 지원분야 수정·제출·철회는 공고 `PUBLISHED`+접수기간 내만. 수정·제출은 `WITHDRAWN` 거부, 철회는 `SUBMITTED`만. 제출은 `DRAFT`·`SUBMITTED` 모두 허용(재제출)하며 config·모집분야 소속·근무지를 재검증한 뒤 `ApplicationSubmitValidator`를 돈다. (— updateDraft·submit·withdraw)
- 근무지: 후보 0개면 코드 금지, 있으면 필수·후보 안의 코드. 코드는 trim, 공백은 null. (— validateWorkLocationChoice)

**섹션 접근** ({BE}/service/ApplicationSectionAccessService.java) — 섹션 서비스 9종이 공통으로 부른다([application-sections](application-sections.md)).
- 본인 지원서가 아니면 404. GET은 소유만 확인(편집 불가·섹션 미사용이어도 조회). (— findOwnedApplication)
- 저장 조건: `WITHDRAWN` 아님 + 공고 `PUBLISHED` + 접수기간 내. `SUBMITTED`도 저장 가능하며 즉시 제출본에 반영. (— validateWritable)
- 학력·경력·자격증·어학·병역·수상·공백기간은 config `useXxx=false`(또는 config 없음)면 저장 400. 기본정보·답변은 검사 없음. (— validate*Enabled)

**최종 제출 검증** (400 한글, 첫 위반에서 중단) ({BE}/service/ApplicationSubmitValidator.java — validate)
1. 기본정보 행 + 성명·생년월일·국적·휴대전화·이메일·보훈·장애 여부, `FOREIGN`⇒국가, 장애 `SUBJECT`⇒등급·유형. 주소·영문명·비상연락처는 비필수.
2. 양식 config 존재.
3. 학력: `useEducation && requireEducation`이면 ≥1건.
4. 병역: `use && require`이면 행·대상구분, `COMPLETED`⇒복무 시작·종료일, `SUBJECT`·`EXEMPTED`⇒사유.
5. 자격증·어학·수상·공백기간: `use && require`이면 ≥1건.
6. 질문 전부: `required`면 비공백, `minLength`(strip 길이, 0이면 제외), `maxLength`(없으면 유형 기본), 유형 상한 500/5000.
7. 첨부: `required=true` 요건 중 `sectionType.acceptsApplicantAttachment()`(= `BASIC_INFO`·`CAREER`)만, `(attachmentType, sectionType)`별 `STORED`·미삭제 수 ≥ `minCount`. 폐지된 `ATTACHMENT` 등 다른 섹션 요건은 무시. `ATTACHMENT` 제외는 523ab48, `acceptsApplicantAttachment()` 필터는 300e792에서 도입.
- **경력은 제출 검증 대상이 아니다**(`requireCareer`를 서버가 보지 않음 — FE만 검사, API 직접 호출이면 경력 없이 제출됨).

**완성도 판정** ({BE}/service/ApplicationCompletionReadChecker.java — check)
- 그룹: `BASIC_INFO` 항상 필수 / config 없으면 `FORM_CONFIG` 필수 이슈 / 학력·병역·자격증·어학·수상·공백기간은 `use=false`면 제외, `require`면 필수, 아니면 선택 그룹 / `QUESTION`은 필수 질문 또는 길이 초과가 있으면 필수 그룹 / `ATTACHMENT`는 제출 검증과 같은 섹션 필터, 필수 요건 있으면 필수, 없고 `minCount>0` 선택 요건만 있으면 선택 그룹.
- 판정은 제출 검증과 같게 유지한다. 단 **`minLength`는 보지 않는다**(카운터가 모두 완료여도 제출 400 가능).
- `requiredCompletionRate` = 완료 필수×100/필수 수(0이면 100), `submitBlockingIssueCount` = 필수 이슈 수. `reasonCode`: `MISSING_ROW`·`MISSING_REQUIRED_FIELD`·`MISSING_CONFIG`·`TYPE_NOT_SELECTED`·`MISSING_PERIOD`·`MISSING_REASON`·`MISSING_REQUIRED_ANSWER`·`INVALID_LENGTH`·`OPTIONAL_EMPTY`·`REQUIRED_ATTACHMENT_MISSING`·`OPTIONAL_ATTACHMENT_MISSING`.
- 대시보드 최신 결과 = 발표된 결과 중 `stageOrder`(동률 stage id) 최대. ({BE}/service/ApplicationDashboardService.java — loadResultSummary)

**프론트** ({FE}/views/applicant/ApplicationFormView.vue)
- `editable=false`면 섹션 영역 전체를 `a-config-provider :component-disabled` + `fieldset :disabled`로 막는다. 단계 이동·이전/다음은 영역 밖이라 동작(86d12c9). 섹션 안 입력도 `:disabled="!editable || …"`.
- 섹션 계약: props `SectionComponentProps { applicationId, section, page, editable, formPage }`, `defineExpose({ saveDraft, validateBeforeSubmit, isDirty })`. 새 섹션은 `sectionComponentMap`·`sectionNameMap`·`completionSectionCodeMap`에 등록.
- 자동 임시저장: 페이지 이동·제출·지원분야 변경 전 dirty 섹션만 저장, 실패 시 중단, 편집 불가면 생략. dirty 상태로 이탈(라우트·beforeunload) 시 확인창. `SUBMITTED`면 저장 전 대상 섹션 `validateBeforeSubmit` 통과 필수. (— saveCurrentPage)
- 제출: dirty 자동 저장 → 전 섹션 `validateBeforeSubmit`(실패 페이지로 이동) → `POST /submit` → form-page 재조회. 모집분야만 바꾸고 근무지 미선택(`positionPending`)이면 제출 불가. (— submitApplication·canSubmit)
- `ATTACHMENT` 섹션은 화면에서 제외. 첨부 업로드는 기본정보·경력 섹션이 한다([application-sections](application-sections.md)).

## 변경 레시피

### 제출 필수 규칙 추가·변경
1. `{BE}/service/ApplicationSubmitValidator.java`에 규칙(한글 메시지).
2. 같은 판정을 `{BE}/service/ApplicationCompletionReadChecker.java`에(새 sectionCode면 FE `completionSectionCodeMap`도).
3. 해당 섹션 컴포넌트 `validateBeforeSubmit`에 같은 규칙([application-sections](application-sections.md)).
4. `{BT}/service/ApplicationSubmitValidatorTest.java`·`{BT}/service/ApplicationCompletionReadCheckerTest.java`·`{BT}/service/ApplicationDashboardServiceTest.java` 갱신·실행.
5. 카드 "최종 제출 검증"·"완성도 판정" 갱신, `node tools/check-docs.mjs`.

### 편집 가능 조건 변경
1. 서버 네 곳을 함께: `ApplicationSectionAccessService.validateWritable`, `JobApplicationService.validatePublishedAndAccepting`, `ApplicationDashboardService`(`editable`), [application-form](application-form.md)의 `ApplicationFormPageService`(`editable`).
2. FE는 `formPage.editable` 하나로 제어. 새 버튼은 `fieldset` 안인지, 모달이면 `:disabled="!editable"`가 있는지 확인.
3. `ApplicationSectionAccessServiceTest`·`JobApplicationServiceTest`·`ApplicationDashboardServiceTest` 실행, 카드 갱신, `node tools/check-docs.mjs`.

## 검증

백엔드(`recruit_back/recruit_backend/`에서). 섹션별 테스트는 [application-sections](application-sections.md):

```powershell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "*.ApplicationControllerTest" --tests "*.JobApplicationServiceTest" --tests "*.ApplicationSubmitValidator*" --tests "*.ApplicationCompletionReadCheckerTest" --tests "*.ApplicationDashboardServiceTest" --tests "*.ApplicationSectionAccessServiceTest" --tests "*.CurrentApplicantServiceTest" --no-daemon
```

```bash
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "*.ApplicationControllerTest" --tests "*.JobApplicationServiceTest" --tests "*.ApplicationSubmitValidator*" --tests "*.ApplicationCompletionReadCheckerTest" --tests "*.ApplicationDashboardServiceTest" --tests "*.ApplicationSectionAccessServiceTest" --tests "*.CurrentApplicantServiceTest" --no-daemon
```

프론트(`recruit_front/`에서):

```bash
npm run type-check
npx vitest run src/api/__tests__/applicationApi.spec.ts
```

## 함정·결정

- 86d12c9: 편집 불가 비활성화는 `a-config-provider`(antd) + `fieldset disabled`(일반 button·input) 두 겹이다. teleport 모달 안 버튼 처리는 [application-sections](application-sections.md).
- 8d7485d: 제출 메시지 한글화·`minLength` 서버 검증. 같은 커밋의 로드 전 저장 차단(`assertLoaded`)·첨부 교체 순서는 [application-sections](application-sections.md).
- 523ab48: 폐지된 `ATTACHMENT` 섹션 필수 요건이 제출을 영구히 막아 제출 검증·완성도에서 뺐다. 현재 기준 `ApplicationSectionType.acceptsApplicantAttachment()`는 300e792에서 도입이며 두 곳이 같은 필터를 써야 한다. 요건 등록 거부는 [job-posting](job-posting.md).
- `/applications/<문자열>` 경로를 추가할 땐 `{applicationId:[0-9]+}` 정규식과 `/applications/me`를 확인. 지원자 전용 `GET /api/job-postings/{jobPostingId}/application` 매처는 `GET /api/job-postings/**` permitAll보다 먼저 선언돼야 한다(`{BE}/config/SecurityConfig.java`).
- `JobApplicationService`의 공용 private 메서드(`validatePageRequest`·`createPageRequest`)는 관리자 검색도 쓴다 → [admin-application](admin-application.md).
