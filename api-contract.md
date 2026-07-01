# API 계약 문서 (recruit)

front-back 동기화의 **단일 기준**. 화면 슬라이스 작업 시 구현 전 🟡 초안으로 기재하고, 구현 후 🟢로 확정한다.

## 상태 표기

- 🟢 구현됨 (front-back 양쪽 구현·검증 완료)
- 🟡 초안 (구현 중)
- 🔴 변경필요 / 불명확 (사용자 확인 필요)

## 운영 규칙

- 화면 단위로 섹션을 만들고, 각 화면 아래 엔드포인트를 나열한다.
- 요청/응답은 **필드 모양 요약** 수준만 적는다. 정확한 타입·검증 규칙은 백엔드 DTO가 단일 출처다.
- 계약을 임의로 발명하지 않는다. 불명확하면 🔴로 표시하고 사용자에게 확인한다.
- 한 엔드포인트가 여러 화면에서 쓰이면 한 곳에 정의하고 다른 화면에서 참조한다.
- 변경 이력은 이 파일의 git 로그로 대체한다(별도 changelog 불필요).

---

## 템플릿 (복사해서 사용 — 아래는 형식 예시이며 실제 계약 아님)

### 화면: <화면명> (<ViewComponent>)

- 프론트: `src/views/.../<View>.vue`, `src/api/<module>.ts`
- 백엔드: `com.shinyoung.recruit.controller.<Controller>`

#### <METHOD> <경로>  🟡

- 설명: <한 줄>
- 요청: `{ ... }`
- 응답(200): `ApiResponse<{ ... }>`
- 오류: <코드/사유>
- 매핑: front `<api 함수>()` ↔ back `<Controller>.<method>()`

> 형식 참고용 예시(실제 검증 전 계약 아님):
> 화면: 로그인 (LoginView) — 프론트 `src/views/auth/LoginView.vue`, `src/api/authApi.ts` / 백엔드 `AuthController`
> `POST /auth/login` → 요청 `{ username, password }`, 응답 `ApiResponse<{ ... }>`, 매핑 front `authApi.login()` ↔ back `AuthController.login()`

---

## 화면 계약

> 실제 화면 계약은 슬라이스 작업 시 위 템플릿을 복사해 이 아래에 추가한다.

### 화면: 지원자 경력 (ApplicationCareer)

- 프론트: (후속) `src/api/applicationApi.ts` 경력 관련 + 경력 입력 화면
- 백엔드: `com.shinyoung.recruit.controller.ApplicationCareerController`

#### GET·POST `/api/applications/{applicationId}/careers`  🔴 백엔드 구현됨 / 프론트 미반영

- 변경(2026-06-23): 요청·응답에서 `careerType` 제거. 경력 행에 `promotionDate`(진급일, nullable) 추가.
- 변경(2026-06-25): 요청·응답에서 `responsibilities` 제거, `currentSalary`(현재연봉, Integer 만원 단위, nullable, 0 이상) 추가.
- 요청: `{ careers: [{ companyName, departmentName, positionTitle, employmentType, startDate, endDate, promotionDate, currentlyEmployed, currentSalary, resignationReason, sortOrder }] }`
- 응답(200): `ApiResponse<{ careers: [...] }>` (careerType 필드 없음)
- 경력은 선택(0개 허용). 신입/경력 타입(careerType) 개념 폐지.
- 관리자 조회 `GET /api/admin/applications/{id}/careers` 응답도 동일하게 careerType 제거 + promotionDate 추가.

### 화면: 지원자 학력 (ApplicationEducation)

- 프론트: (후속) 학력 입력 화면 + `src/api` education 관련
- 백엔드: `com.shinyoung.recruit.controller.ApplicationEducationController`

#### GET·POST `/api/applications/{applicationId}/educations`  🔴 백엔드 구현됨 / 프론트 미반영

- 변경(2026-06-25): 요청·응답에서 `degreeName` 제거. `additionalMajorType`(복수/부/세부전공 구분), `additionalMajorName`(해당 전공 명칭), `thesisTitle`(논문명) 추가.
- 변경(2026-06-30, 🟢 확정): 학력 단위 전체 평점 요약 4필드 추가 — `overallGradePoint`(전체 평점), `overallMaxGradePoint`(전체 만점기준), `overallMajorGradePoint`(전공 전체 평점), `overallMajorMaxGradePoint`(전공 전체 만점기준). 모두 `BigDecimal`. 전체 쌍은 `HIGH_SCHOOL`이 아니면 필수, `HIGH_SCHOOL`이면 선택. 전공 전체 쌍은 모든 레벨에서 선택. 자동 평균계산 없음(수동 입력).
- 요청: `{ educations: [{ educationLevel, schoolName, majorName, additionalMajorType, additionalMajorName, thesisTitle, admissionDate, graduationDate, graduationStatus, dayNightType, campusType, transfer, countryCode, sortOrder, semesterGrades, schoolId, overallGradePoint, overallMaxGradePoint, overallMajorGradePoint, overallMajorMaxGradePoint }] }`
- 응답(200): `ApiResponse<{ educations: [...] }>` (degreeName 없음, 3필드 + 전체평점 4필드 포함, educationId 포함)
- `additionalMajorType`는 코드 문자열(프론트가 CommonCode 그룹 `MAJOR_TYPE`로 렌더, 백엔드 validation 미결합). `additionalMajorName`/`thesisTitle`는 선택 자유텍스트.
- 전체 평점 쌍/전공 전체 쌍은 함께 입력해야 한다(평점만 있고 만점이 없으면 검증 실패). 평점 ≤ 만점, 만점 > 0.
- 관리자 조회 `GET /api/admin/applications/{id}/educations` 응답도 동일하게 4필드 추가.

### 화면: 관리자 학교 마스터 (School)

- 프론트: (후속) 학교 검색/관리 화면, `src/api`의 school 관련
- 백엔드: `SchoolController`(공개 검색), `AdminSchoolController`(CRUD/xlsx import)

#### School 생성·수정·검색·import  🔴 백엔드 구현됨 / 프론트 미반영

- 변경(2026-06-23): `schoolCode` 제거, `schoolCategory` 추가.
- 생성/수정 요청·응답: `schoolCode` 없음, `schoolCategory` 포함. `schoolType`/`schoolCategory`는 코드 문자열(프론트가 CommonCode 그룹 `SCHOOL_TYPE`/`SCHOOL_CATEGORY`로 렌더, 백엔드 validation 미결합).
- xlsx import 헤더(7열): `schoolName, schoolType, schoolCategory, educationMode, region, address, countryCode`
- 중복제거: `(schoolName, schoolType, region)` fallback

### 화면: 지원자 어학 (ApplicationLanguage)

- 프론트: `src/api/application/sections/languageApi.ts`, `src/views/applicant/application/sections/LanguageSection.vue` (ApplicationFormView `sectionComponentMap.LANGUAGE`)
- 백엔드: `com.shinyoung.recruit.controller.ApplicationLanguageController`

#### GET·POST `/api/applications/{applicationId}/languages`  🟢 (프론트 반영 완료)

- 변경(2026-06-23): 요청·응답에서 `score`,`grade` 제거. `scoreOrGrade`(선택), `conversationalAbility`(선택) 추가.
- 요청: `{ languages: [{ languageName, testName, scoreOrGrade, conversationalAbility, examDate, expiredDate, issuingOrganization, sortOrder }] }`
- 응답(200): `ApiResponse<[{ languageId, languageName, testName, scoreOrGrade, conversationalAbility, examDate, expiredDate, issuingOrganization, sortOrder }]>`
- `conversationalAbility`는 공통코드 그룹 `LANGUAGE_CONVERSATION` 코드 문자열(프론트가 CommonCode로 렌더, 백엔드 validation 미결합, 코드 시드 안 함).
- 관리자 조회 `GET /api/admin/applications/{id}/languages` 응답도 동일하게 `scoreOrGrade`/`conversationalAbility` 반영.

### 화면: 지원자 수상 (ApplicationAward)

- 프론트: `src/api/application/sections/awardApi.ts`, `src/views/applicant/application/sections/AwardSection.vue` (ApplicationFormView `sectionComponentMap.AWARD`)
- 백엔드: `com.shinyoung.recruit.controller.ApplicationAwardController`

#### GET·POST `/api/applications/{applicationId}/awards`  🟢

- 요청: `{ awards: [{ awardName, awardingOrganization, awardDate, description, sortOrder }] }`
- 응답(200): `ApiResponse<[{ awardId, awardName, awardingOrganization, awardDate, description, sortOrder }]>`
- 필수: `awardName`, `awardingOrganization`, `awardDate`. `description`은 선택, 최대 2000자.
- 수상은 선택(0개 허용 = 빈 배열). 전체 교체(replace) 방식.

### 화면: 지원자 자격증 (ApplicationCertificate)

- 프론트: `src/api/application/sections/certificateApi.ts`, `src/views/applicant/application/sections/CertificateSection.vue` (ApplicationFormView `sectionComponentMap.CERTIFICATE`)
- 백엔드: `com.shinyoung.recruit.controller.ApplicationCertificateController`

#### GET·POST `/api/applications/{applicationId}/certificates`  🟢

- 요청: `{ certificates: [{ certificateName, issuingOrganization, acquiredDate, certificateNumber, expiredDate, scoreOrGrade, sortOrder }] }`
- 응답(200): `ApiResponse<[{ certificateId, certificateName, issuingOrganization, acquiredDate, certificateNumber, expiredDate, scoreOrGrade, sortOrder }]>`
- 필수: `certificateName`, `issuingOrganization`, `acquiredDate`. 나머지 선택.
- 자격증은 선택(0개 허용 = 빈 배열). 전체 교체(replace) 방식.

### 화면: 지원자 공백기간 (ApplicationGapPeriod)

- 프론트: `src/api/application/sections/gapPeriodApi.ts`, `src/views/applicant/application/sections/GapPeriodSection.vue` (ApplicationFormView `sectionComponentMap.GAP_PERIOD`)
- 백엔드: `com.shinyoung.recruit.controller.ApplicationGapPeriodController`

#### GET·POST `/api/applications/{applicationId}/gap-periods`  🟢

- 요청: `{ gapPeriods: [{ startDate, endDate, gapType, reason, description, sortOrder }] }`
- 응답(200): `ApiResponse<[{ gapPeriodId, startDate, endDate, gapType, reason, description, sortOrder }]>`
- 필수: `startDate`, `endDate`, `gapType`, `reason`. `description`은 선택(≤2000).
- `gapType`은 enum `EDUCATION`/`CAREER`/`OTHER`(프론트 라벨 학업/경력/기타, 하드코딩 — 공통코드 아님).
- 공백기간은 선택(0개 허용 = 빈 배열). 전체 교체(replace) 방식. 프론트 "해당 사항 없음" 체크박스는 영속화 부재로 주석 처리됨.

### 화면: 지원자 자기소개/질문 (ApplicationQuestionAnswer)

- 프론트: `src/api/application/sections/questionAnswerApi.ts`, `src/views/applicant/application/sections/QuestionAnswerSection.vue` (ApplicationFormView `sectionComponentMap.QUESTION_ANSWER`)
- 백엔드: `com.shinyoung.recruit.controller.ApplicationAnswerController`

#### GET `/api/applications/{applicationId}/questions`  🟢

- 응답(200): `ApiResponse<[{ questionId, questionText, helperText, category, answerType, required, minLength, maxLength, sortOrder, answerId, answerText, updatedAt }]>`
- 공고가 정의한 질문(JobPostingQuestion) + 지원자 기존 답변 병합. `category` enum SELF_INTRODUCTION/GENERAL/JOB_SPECIFIC/ETC, `answerType` enum SHORT_TEXT/LONG_TEXT.

#### POST `/api/applications/{applicationId}/answers`  🟢

- 요청: `{ answers: [{ questionId, answerText }] }` (answerText ≤5000, 전체 교체)
- 응답(200): `GET /questions`와 동일 형태(질문 + 갱신된 답변).
- draft 부분 저장 허용(answerText NotBlank 아님). 필수/minLength는 프론트 최종 제출 검증에서만 강제.

### 화면: 지원자 기본정보 — 보훈 (ApplicationBasicInfo)

- 프론트: (후속) 기본정보 입력 화면 보훈 영역
- 백엔드: `com.shinyoung.recruit.controller.ApplicationBasicInfoController`

#### GET·POST `/api/applications/{applicationId}/basic-info`  🔴 백엔드 구현됨 / 프론트 미반영

- 변경(2026-06-23): 요청·응답에 `veteranType`(문자열, 평문, 선택) 추가.
- 규칙: `veteranStatus=="SUBJECT"`면 `veteranType` 필수, `"NOT_SUBJECT"`면 비어 있어야 함(값 있으면 400).
- 자유 입력 String(공통코드/암호화 아님 — 보훈은 일반 PII). 관리자 조회 응답(`AdminBasicInfoResponse`)에도 `veteranType` 포함.

### 화면: 지원자 병역 (ApplicationMilitary)

- 프론트: (후속) 지원서 입력 화면 병역 영역 — 현재 `ApplicationSectionPlaceholder`
- 백엔드: `com.shinyoung.recruit.controller.ApplicationMilitaryController`

#### GET·POST `/api/applications/{applicationId}/military`  🟢 백엔드 구현됨 / 프론트 미반영

- `militarySubjectType` enum: `SUBJECT`(병역대상·미필) / `NOT_SUBJECT`(비대상) / `COMPLETED`(군필) / `EXEMPTED`(면제). (2026-06-29: `NOT_APPLICABLE` 제거 — `NOT_SUBJECT`로 통합.)
- 요청·응답 필드: `{ militarySubjectType, serviceType, militaryBranch, rank, serviceStartDate, serviceEndDate, nonServiceReason }`. (2026-06-29: `exemptionReason` → `nonServiceReason`으로 명칭 변경 — 면제·미필 사유 공통.)
- `nonServiceReason`: 자유 입력 String(≤1000), 평문 저장이나 **민감정보 취급** — 관리자 조회 응답은 `nonServiceReasonMasked`(`***`)로만 노출, PII 파기 대상.
- 저장(POST) 시 대상구분별 필드 정책:
  - `SUBJECT`·`EXEMPTED`: `nonServiceReason` 허용, 복무 상세(serviceType/branch/rank/기간) 금지.
  - `COMPLETED`: 복무 상세 허용, `nonServiceReason` 금지.
  - `NOT_SUBJECT`: 모든 상세 필드 금지.
- 최종 제출 검증: `COMPLETED`는 복무기간(start·end) 필수, **`SUBJECT`·`EXEMPTED`는 `nonServiceReason` 필수**(임시저장 단계에서는 선택). (2026-06-29: `SUBJECT` 사유 필수 신규.)
- 관리자 조회: `AdminMilitaryResponse`(`nonServiceReasonMasked`), PDF 출력 라벨 "병역사유".

### 화면: 메뉴 (Menu — 헤더/사이드바, 관리자 메뉴관리)

- 프론트: `src/api/menuApi.ts`, `src/types/menu.ts`, 헤더(지원자)·관리자 사이드바(후속)
- 백엔드: `com.shinyoung.recruit.controller.MenuController`

#### GET `/menu/tree` · `/menu/{menuId}` · `/menu/breadcrumb`, POST `/menu/admin/menu` · `/menu/admin/menu/{menuId}`  🟢

- 변경(2026-06-30, 🟢 확정): 메뉴에 `icon`(아이콘 컴포넌트명) 필드 추가. ant-design-vue 아이콘 컴포넌트명을 **문자열 그대로** 저장(예: `"SettingOutlined"`). nullable, 백엔드 검증 없음(자유 문자열, `VARCHAR(100)`). 대메뉴/소메뉴 모두 허용. 지원자(가로 헤더) 메뉴는 아이콘 미사용(null), 관리자 좌측 사이드바용.
- 메뉴 노드: `{ id, parentId, site('APPLICANT'|'ADMIN'), type('ROUTE'|'URL'), name, path, sortOrder, icon, children:[...] }`. (트리는 최대 2단계, `children` 재귀.)
- 저장 요청(`MenuSaveRequest`): `{ site, type, parentId?, name, path?, sortOrder?, icon? }` → 응답 `ApiResponse<{ id }>`.
- 조회 응답(`MenuResponse`): 위 메뉴 노드 모양. `GET /menu/tree?site=`, `GET /menu/breadcrumb?site=&path=`, `GET /menu/{menuId}`.
- 매핑: front `menuApi.getMenuTree()`/`getBreadcrumb()` ↔ back `MenuController.getTree()`/`getBreadcrumb()`. 생성/수정 API는 프론트 미반영(관리 화면은 후속 슬라이스).
- 범위 밖(후속): 메뉴 관리 CRUD 화면 + 아이콘 피커, 아이콘 실제 렌더링(관리자 사이드바), 삭제(요청 시 DELETE 대신 POST 사용).

### 화면: 지원서 작성 완성도 (ApplicationFormView — 상단 스텝 카운터)

- 프론트: `src/views/applicant/ApplicationFormView.vue`, `src/api/application/dashboardApi.ts`, `src/types/application/dashboard.ts`
- 백엔드: `com.shinyoung.recruit.controller.ApplicationController` (신규 아님, 기존 구현)

#### GET `/applications/{applicationId}/dashboard`  🟢 확정 (프론트 반영 완료 / 백엔드 기존 구현)

- 용도: 지원서 작성 화면 상단 스텝 네비게이션의 "남은 입력사항"(페이지별 `완료수/필수수`) 카운터 데이터 소스. **임시저장 여부가 아니라 저장된 데이터가 필수 규칙을 만족하는지**를 백엔드 `ApplicationCompletionReadChecker`가 판정한다.
- 응답(200): `ApiResponse<ApplicationDashboardResponse>`
  - `completionSummary`: `{ requiredSectionCount, completedRequiredSectionCount, requiredMissingCount, optionalSectionCount, completedOptionalSectionCount, optionalIncompleteCount, requiredCompletionRate, submitBlockingIssueCount }`
  - `requiredMissingSections` / `optionalIncompleteSections`: `[{ sectionCode, sectionName, required, complete, reasonCode, message }]` — **미완 섹션만** 담김(완료 섹션은 목록에 없음).
  - 그 외: `{ applicationId, jobPostingId, jobPostingTitle, jobPositionName, applicationStatus, accepting, editable, submittable, withdrawable, submittedAt, withdrawnAt, latestAnnouncedStageName, latestResultStatus }`
- 프론트 완료 판정: 필수 섹션은 `sectionCode`가 `requiredMissingSections`에 **없으면** 완료로 본다. 프론트 `ApplicationSectionType` ↔ 백엔드 `sectionCode` 매핑 필요 — 대부분 동일하나 **`QUESTION_ANSWER` ↔ `QUESTION`**. **`CAREER`는 checker 판정 대상이 아님**(완료 확정 불가 → 완료로 단정하지 않음).
- 갱신 시점: 화면 최초 로드(form-page 조회 직후) + **임시저장 성공 직후** 재조회하여 카운터 재계산.
- 매핑: front `dashboardApi.getDashboard()` ↔ back `ApplicationController.getDashboard()`.
- 범위 밖: 완성도 요약(`requiredCompletionRate` 등) 별도 UI 표시, `submittable` 기반 제출 버튼 제어, CAREER 섹션 완성도 백엔드 판정 추가.
