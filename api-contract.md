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

### 화면: 지원서 작성 폼 로드 (ApplicationFormView — form-page)

- 프론트: `src/views/applicant/ApplicationFormView.vue`, `src/api/application/*` (form-page 로드)
- 백엔드: `com.shinyoung.recruit.controller.ApplicationController`

#### GET `/applications/{applicationId}/form-page`  🟢 확정 (백엔드 구현·검증 완료)

- 용도: 지원서 작성 화면의 폼 메타/레이아웃 로드. 응답 `postingType`으로 프론트가 채용유형별 UI 분기(예: 학력 성적 입력을 공개·인턴=학기별, 경력·수시=평균만 표시)를 판단한다.
- 변경(2026-07-06, 🟢 확정): 응답에 `postingType`(`JobPostingType`) 추가.
- 응답(200): `ApiResponse<{ applicationId, jobPostingId, jobPostingTitle, jobPostingStatus, postingType, jobPositionId, jobPositionName, applicationStatus, receptionStartDateTime, receptionEndDateTime, accepting, editable, submittedAt, withdrawnAt, formConfig, sections:[...] }>`
- `postingType` 값: `"PUBLIC_RECRUITMENT" | "EXPERIENCED_RECRUITMENT" | "INTERN_RECRUITMENT" | "ROLLING_RECRUITMENT"`. 공고 미설정 시 백엔드 기본값 `PUBLIC_RECRUITMENT`.
- 유형→성적모드 매핑은 **프론트**에 위치(백엔드 학력 검증 무변경). 주의: 현재 학력 검증은 비고졸 평균평점 필수·학기별 선택이므로, 공개·인턴이라도 평균 입력란을 숨기면 저장이 400으로 막힌다(평균 유지 필요).
- 매핑: front form-page 로드 ↔ back `ApplicationController.getFormPage()`.

### 화면: 주소 검색 (AddressSearch — juso.go.kr 프록시)

- 프론트: (후속) 기본정보 주소 입력 보조 — 주소 검색 모달/자동완성, `src/api`의 address 관련
- 백엔드: `com.shinyoung.recruit.controller.AddressSearchController`, `service.AddressSearchService`, `service.JusoAddressClient`

#### GET `/api/addresses`  🟢 확정 (백엔드 구현·테스트 완료 / 프론트 미반영)

- 용도: 정부 도로명주소 OpenAPI(`business.juso.go.kr/addrlink/addrLinkApi.do`)를 백엔드가 대리 호출(proxy)하는 주소 검색. 지원자 주소 입력 보조용 public 검색이다.
- 승인키(`confmKey`)는 **서버 설정(`recruit.juso.confm-key`, 환경변수 `JUSO_CONFM_KEY`)에 보관**한다. 프론트/클라이언트는 confmKey를 보내지 않는다(승인키 미노출).
- 외부 호출 방식(검증됨): `GET addrLinkApi.do?confmKey&currentPage&countPerPage&keyword&resultType=json` (레거시 파라미터 4종 + `resultType=json`).
- 변경(2026-07-31, 🟢 확정): juso 조회 범위 상한(E0015) 대응 — 응답에 `maxPage` 추가, 범위 초과 요청은 400으로 선차단. juso 오류코드를 사용자 입력(400)/서버(502)로 분류.
- 요청(query): `{ keyword(필수, 비어있으면 400), currentPage(기본 1, min 1), countPerPage(기본 10, min 1, max `recruit.juso.max-count-per-page` 기본 100) }`
- **조회 범위 상한**: `currentPage × countPerPage ≤ recruit.juso.max-search-range`(기본 **9,000**). 초과 시 juso가 `E0015 검색 범위를 초과하였습니다`를 반환하므로, 백엔드가 **외부 호출 전에 400**으로 막는다(상위 장애 502와 구분).
  - 실측 경계(2026-07-31, keyword=`중앙로`, totalCount=10,715): `900×10`=9,000 정상 / `901×10`=9,010 E0015 / `90×100`=9,000 정상 / `91×100`=9,100 E0015 → **countPerPage와 무관한 offset 상한**.
- 응답(200): `ApiResponse<{ totalCount, currentPage, countPerPage, maxPage, addresses: [{ roadAddr, jibunAddr, zipNo, siNm, sggNm, emdNm, bdNm, engAddr }] }>` (juso 원본을 정제 DTO로 매핑)
  - `totalCount`/`currentPage`/`countPerPage`는 **juso 응답 `results.common`의 에코값**이다(백엔드 계산값 아님). 파싱 실패 시 방어적으로 `0`.
  - ⚠️ **`totalCount`로 페이지네이션을 계산하지 말 것.** `totalCount`(10,715)가 조회 가능 범위(9,000)보다 클 수 있어 `ceil(totalCount/countPerPage)`로 페이지 수를 잡으면 조회 불가능한 페이지가 생긴다. 프론트는 반드시 `maxPage`를 쓴다.
  - `maxPage` = `min(ceil(totalCount/countPerPage), floor(maxSearchRange/countPerPage))`. 결과 없음/비정상 응답이면 `0`.
- 오류 매핑: **juso 오류코드를 전부 502로 뭉개지 않는다.** 검색어가 원인이면 400 + juso 안내 메시지, 서버/승인키 문제면 502 + 일반 메시지.

  | 상황 | 상태 | message |
  |---|---|---|
  | 검색어 누락/공백 | 400 | `검색어를 입력해 주세요.` |
  | 조회 범위 상한 초과(선차단) | 400 | `조회 가능한 검색 범위(9000건)를 초과했습니다. 검색어를 더 자세히 입력해 주세요.` |
  | juso가 검색어 거부 (`E0006`, `E0015`) | 400 | **juso 원문 그대로** (예: `주소를 상세히 입력해 주시기 바랍니다.`) |
  | 승인키 미설정/거부, 네트워크·타임아웃, 파싱 실패, 미확인 juso 코드 | 502 | `주소 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.` |

  - ⚠️ `E0006`은 **행정구역명 단독 검색에서 상시 발생한다** (실측: `영등포구` → E0006). juso는 도로명주소 검색이라 `여의대로`처럼 도로명/건물명이 필요하다. 정상 사용 중에도 흔한 경로이므로 프론트는 400 message를 검색창 밑에 그대로 노출한다.
  - 미확인 코드를 400이 아니라 502로 보내는 이유: 서버 문제를 사용자 탓으로 잘못 분류해 승인키 관련 메시지가 노출되는 것을 막기 위함. 새 코드는 서버 로그 `juso 오류코드=...`를 보고 `JusoAddressClient.USER_INPUT_ERROR_CODES`에 추가한다.
  - 결과 없음은 오류가 아니다 → 200 + `totalCount=0` + 빈 배열.
- 매핑: front address 검색 ↔ back `AddressSearchController.searchAddresses()` → `AddressSearchService.search()` → `JusoAddressClient.search()`.
- 범위 밖: 상세주소(동/호) 입력, 좌표(위경도) 조회(별도 API), 프론트 주소 검색 UI, 도로명 영문주소 표시 정책.

### 화면: 지원자 첨부파일 (ApplicationAttachment — 독립 섹션)

- 프론트: `src/views/applicant/application/sections/AttachmentSection.vue`(신규), `src/api/application/sections/attachmentApi.ts`(신규), `src/types/application/sections/attachment.ts`(신규)
- 백엔드: `com.shinyoung.recruit.controller.ApplicationAttachmentController` (기존 구현, 무변경)

#### 섹션 노출 규칙 (`ApplicationFormConfig.useAttachment`)  🟢 확정 (백엔드 구현·테스트 / 프론트 반영·type-check)

- 배경: `ATTACHMENT`는 이미 `ApplicationSectionType`의 layout section이나, 노출 스위치가 **`job_posting_attachment_requirement` 행 존재 여부뿐**이었다. 요구사항 행 없이 "사용자가 자유롭게 첨부하는 선택 섹션"을 만들 수 없어 `useAttachment`를 추가한다.
- 규칙: `enabled = useAttachment || hasAttachmentRequirements`, `required = hasRequiredAttachmentRequirements`(**무변경**).
  - `requireAttachment` 컬럼은 **추가하지 않는다.** required는 requirement 행이 단일 출처이며 `ApplicationCompletionReadChecker`·`ApplicationSubmitValidator`에 이미 배선되어 있다. 컬럼을 추가하면 진실 공급원이 갈라진다.
  - 위 OR 규칙으로 "필수면 반드시 노출" 불변식이 자동 성립한다.
- ⚠️ 운영 주의: 섹션을 켜면 **저장된 레이아웃(`application_form_page`)이 있는 기존 공고는 form-page 조회가 예외**로 막힌다(`ApplicationFormLayoutValidator` — enabled ⊆ placed ⊆ enabled 강제). 관리자 레이아웃 API로 ATTACHMENT를 포함해 재저장해야 한다. 저장 레이아웃이 없는 공고는 default factory가 자동 처리. `useAttachment` 기본값 `false`가 안전장치.
- 영향 엔드포인트(필드 1개 추가): 공고 생성·수정 요청 `ApplicationFormConfigRequest`, 공고 상세/공개상세/공개목록 응답, `GET /applications/{id}/form-page`의 `formConfig`.

#### `AttachmentType` enum 확장  🟢 확정

- 신규 값 2개: `CAREER_DESCRIPTION`(경력기술서), `EMPLOYMENT_CERTIFICATE`(재직증명).
- 기존 `CAREER_CERTIFICATE`(경력증명서)·`RESUME`·`TRANSCRIPT`는 **enum에 유지하되 드롭다운에는 노출하지 않는다**(기존 데이터 보존, 의미 왜곡 방지).
- 프론트 드롭다운 7종(라벨 ↔ 값): 경력기술서=`CAREER_DESCRIPTION`, 포트폴리오=`PORTFOLIO`, 자격증명=`CERTIFICATE_PROOF`, 졸업증명=`GRADUATION_CERTIFICATE`, 재직증명=`EMPLOYMENT_CERTIFICATE`, 어학점수=`LANGUAGE_SCORE_REPORT`, 기타=`ETC`.
- 라벨은 **프론트 상수 맵**이 출처. `ATTACHMENT_TYPE` 공통코드 그룹은 만들지 않는다(값 집합의 출처가 Java enum이라 코드 테이블과 이중화됨).

#### GET·POST·DELETE `/applications/{applicationId}/attachments*`  🟢 확정 (백엔드 기존 구현 무변경 / 프론트 신규 반영)

- 백엔드 **무변경**. 아래는 기존 구현을 계약에 명문화하는 것.
- `GET /applications/{applicationId}/attachments` → `ApiResponse<AttachmentResponse[]>`
  - `AttachmentResponse`: `{ attachmentId, attachmentType, sectionType, sectionRecordId, originalFileName, contentType, fileSize, sortOrder }`
  - ⚠️ **sectionType 필터 파라미터가 없다. 전체가 내려온다.** 프론트는 반드시 `sectionType === 'ATTACHMENT'`로 필터링할 것. 누락 시 BASIC_INFO 증명사진이 첨부 목록에 섞이고, 거기서 삭제하면 사진이 지워진다.
- `POST /applications/{applicationId}/attachments/files` (`multipart/form-data`) → `ApiResponse<AttachmentResponse>`
  - part: `file`. query: `attachmentType`, `sectionType`, `sectionRecordId`(선택).
  - `sortOrder`/`displayName`/`originalFileName`/`storedFileName`/`storagePath`를 클라이언트가 보내면 **400**(서버가 `sortOrder = max+1` 자동 부여).
  - 제한: 지원서당 20개 / 총 100MB(증명사진과 **공유**), 파일당 20MB. 허용 확장자 `pdf,jpg,jpeg,png,doc,docx,xls,xlsx,hwp,hwpx`.
  - 같은 `(attachmentType, sectionType)` 중복 업로드 **허용**(unique 제약 없음).
- `POST /applications/{applicationId}/attachments/{attachmentId}/delete` → `ApiResponse<AttachmentDeleteResponse>`
  - `AttachmentDeleteResponse`: `{ applicationId, attachmentId, deleted, physicalDeleteRequested, message }`
  - 🔴 기존 프론트 `basicInfoApi.deleteApplicationAttachments`가 반환 타입을 `AttachmentResponse`로 **잘못 선언**함(필드가 전혀 다름). 신규 `attachmentApi.ts`에서 바로잡는다.
- `GET /applications/{applicationId}/attachments/{attachmentId}/download` → 바이너리(`Content-Disposition: attachment`, `Cache-Control: no-store`)
  - `STORED` 상태만 200. `METADATA_ONLY`/`MISSING`/`DELETED`는 404.
- **쓰지 말 것**: `POST /applications/{applicationId}/attachments`(`replaceAttachments`)는 `METADATA_ONLY` 행만 교체하고 sortOrder가 기존 `STORED`와 겹치면 400이다. 업로드된 파일에는 무용 → 저장은 순차 delete/post 루프로 처리.
- 매핑: front `attachmentApi.getApplicationAttachments()`/`postApplicationAttachmentsFile()`/`deleteApplicationAttachments()` ↔ back `ApplicationAttachmentController.getAttachments()`/`uploadAttachmentFile()`/`deleteAttachment()`.
- 범위 밖: 증명사진 inline 서빙/썸네일 바인딩(BASIC_INFO 유지, 별도 슬라이스), `sectionType != ATTACHMENT`인 requirement 행 처리, 관리자 첨부 요구사항 설정 화면.
