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
- 변경(2026-09-01, 프론트 전용 / 백엔드 무변경): 경력 섹션에 **경력기술서 첨부란 1개**를 추가. 경력사항이 1건 이상일 때만 노출한다. 기존 첨부 엔드포인트를 그대로 쓰며 식별자는 `sectionType=CAREER` + `attachmentType=CAREER_DESCRIPTION`(`sectionRecordId`는 보내지 않음 — 섹션당 1건). 저장은 기존 파일 delete → 신규 upload 순차 호출. 경력사항을 0건으로 만들면 첨부도 함께 삭제한다.
- 변경(2026-09-01, 프론트 전용): 안내문구 "* 최근 경력부터 순서대로 입력해 주세요." 추가. `currentSalary`(연봉)를 최종직급 셀 안이 아니라 **독립 cell header**로 분리.

### 화면: 지원자 학력 (ApplicationEducation)

- 프론트: (후속) 학력 입력 화면 + `src/api` education 관련
- 백엔드: `com.shinyoung.recruit.controller.ApplicationEducationController`

#### GET·POST `/api/applications/{applicationId}/educations`  🔴 백엔드 구현됨 / 프론트 미반영

- 변경(2026-06-25): 요청·응답에서 `degreeName` 제거. `additionalMajorType`(복수/부/세부전공 구분), `additionalMajorName`(해당 전공 명칭), `thesisTitle`(논문명) 추가.
- 변경(2026-06-30, 🟢 확정): 학력 단위 전체 평점 요약 4필드 추가 — `overallGradePoint`(전체 평점), `overallMaxGradePoint`(전체 만점기준), `overallMajorGradePoint`(전공 전체 평점), `overallMajorMaxGradePoint`(전공 전체 만점기준). 모두 `BigDecimal`. 전체 쌍은 `HIGH_SCHOOL`이 아니면 필수, `HIGH_SCHOOL`이면 선택. 전공 전체 쌍은 모든 레벨에서 선택. 자동 평균계산 없음(수동 입력).
- 변경(2026-08-26, 🟢 확정): `HIGH_SCHOOL`(화면 라벨 "최종 고등학교")은 지원서당 **1건만** 허용. 2건 이상이면 400. 스키마 변경 없음(검증 규칙만 추가).
- 요청: `{ educations: [{ educationLevel, schoolName, majorName, additionalMajorType, additionalMajorName, thesisTitle, admissionDate, graduationDate, graduationStatus, dayNightType, campusType, transfer, countryCode, sortOrder, semesterGrades, schoolCode, schoolSource, overallGradePoint, overallMaxGradePoint, overallMajorGradePoint, overallMajorMaxGradePoint }] }`
- 응답(200): `ApiResponse<{ educations: [...] }>` (degreeName 없음, 3필드 + 전체평점 4필드 포함, educationId 포함)
- `additionalMajorType`는 코드 문자열(프론트가 CommonCode 그룹 `MAJOR_TYPE`로 렌더, 백엔드 validation 미결합). `additionalMajorName`/`thesisTitle`는 선택 자유텍스트.
- 전체 평점 쌍/전공 전체 쌍은 함께 입력해야 한다(평점만 있고 만점이 없으면 검증 실패). 평점 ≤ 만점, 만점 > 0.
- 변경(2026-09-01, 🟢 확정): 학력 1건당 `semesterGrades` **최대 16개**. 17개 이상이면 400(`ApplicationEducationService.MAX_SEMESTER_GRADES`). 스키마 변경 없음(검증 규칙만 추가).
- 변경(2026-09-01, 프론트 전용): `HIGH_SCHOOL` 화면 라벨 "최종 고등학교" → **"고등학교"**(1건 제약은 그대로). 고등학교도 `admissionDate` 입력 가능(백엔드는 원래 레벨 제약 없음). 전공/평점 모달의 학기 입력은 학년×학기 표가 아니라 **1~8학기 기본 + '학기 추가'로 16학기까지** 늘리는 목록으로 바뀜. N학기 ↔ `(schoolYear = ceil(N/2), semester = (N-1)%2+1)` 환산이라 저장 스키마는 동일.
- 관리자 조회 `GET /api/admin/applications/{id}/educations` 응답도 동일하게 4필드 추가.

### 화면: 학교 검색 (외부 OpenAPI)

- 프론트: `src/views/applicant/application/sections/EducationSection.vue` 학교찾기 모달, `src/api/application/sections/educationApi.ts`
- 백엔드: `com.shinyoung.recruit.controller.SchoolSearchController`

#### GET `/api/schools`  🟢 확정 (2026-08-27, 프론트 반영 완료)

- 변경(2026-08-27): School 마스터 DB 검색을 **외부 OpenAPI 프록시**로 교체한다. `school` 테이블·관리자 학교 관리(xlsx import 포함)는 폐기한다.
- 요청: `q`(검색어, 공백이면 빈 목록), `educationLevel`(`EducationLevel` enum). 기존 `schoolType`(한글 라벨) 파라미터는 제거.
- 응답(200): `ApiResponse<[{ schoolCode, schoolName, schoolSource, region }]>` — 활성/비활성 개념 없음, 상위 20건.
  - `schoolCode` — 외부 학교 식별자 문자열. NEIS는 `SD_SCHUL_CODE`, 대학은 **학교명**(해당 데이터셋에 학교코드가 없다).
  - `schoolSource` — `NEIS` | `UNIV_INFO` | `UNIV_DEPT`. 코드 네임스페이스 구분용. 학교 검색이 실제로 쓰는 값은 `NEIS`·`UNIV_INFO` 둘뿐이다.
- 라우팅: `HIGH_SCHOOL` → NEIS 학교기본정보, `COLLEGE`/`UNIVERSITY`/`MASTER`/`DOCTOR` → 전국대학및전문대학정보표준데이터.
- 인증키는 서버 설정에만 두고 프론트에 노출하지 않는다(juso 선례). 키 미설정·외부 장애·파싱 실패는 502.
- 외부 API 는 직접 호출하지 않고 **DMZ 웹서버를 경유**한다(HTTP 80): NEIS `http://juso.go.kr/neis/...`(→ `open.neis.go.kr`), 공공데이터 `http://juso.go.kr/gov/...`(→ `api.data.go.kr`), 주소 `http://juso.go.kr/juso/...`(→ `business.juso.go.kr`). 프리픽스 뒤 경로는 원본과 동일하다.
- 대학 API 확정(2026-08-27): 전국대학및전문대학정보(`/openapi/tn_pubr_public_univ_info_api`, 행 1건 = 학교 1곳). 공공데이터포털 표준데이터 규격 — `serviceKey/pageNo/numOfRows/type=json`.
- 학과 단위 데이터셋(`tn_pubr_public_univ_major_api`) 호출 코드는 제거하지 않고 남겨뒀다. 전공명 자동완성 후속 검토용이며 학교 검색 경로에서는 호출하지 않는다.
- 대학 API 명세 확정(2026-08-31, 운영 환경 실호출로 검증):
  - 요청 파라미터는 대문자 스네이크다 — `SCHL_NM`(학교명), `UNIV_SE_NM`(대학구분명), `pageNo`, `numOfRows`(최대 1000), `type=json`.
  - **`SCHL_NM`은 완전일치다.** `SCHL_NM=평택`은 `resultCode=03`(NODATA), `SCHL_NM=서울대학교`는 정상 반환. `평택%`·`%평택%` 와일드카드도 NODATA다. 즉 상위 API로 부분검색이 불가능하다.
    - 대응(2026-08-31): 부분검색을 서버에서 구현하지 않고, 학교찾기 모달에 "대학은 학교명을 전체 입력해야 검색됩니다" 안내를 노출한다. 전량 조회(`totalCount` 약 2000건) 후 로컬 필터링은 후속 과제로 남긴다.
  - 응답 구조는 요청과 다르다 — 최상위에 바로 `header`/`body`(`response` 래퍼 없음), 목록은 **`body.items.item`** 이고 행이 1건이면 배열이 아니라 객체로 온다. `numOfRows`/`pageNo`/`totalCount`는 최상위에 있다.
  - 응답 항목명은 **lowerCamel** 이다 — `schlNm`, `univSeNm`, `schlSeNm`, `ctpvNm`, `insttNm` 등. 요청의 대문자 스네이크와 표기가 다르다.
  - **학교 식별 코드가 없다**(제공 항목은 제공기관코드 `instt_code` 뿐). 따라서 대학 계열의 `schoolCode`는 **학교명 문자열**이다. 학교별 통계 그룹 키도 학교명이 된다.
  - 대학원은 별도 행으로 제공된다(`univSeNm`=대학원, `schlSeNm`=일반대학원/특수대학원/전문대학원). 예: "평택대학교 물류·정보·경영대학원".
- DMZ 프록시 주의(2026-08-31): Apache `/gov` Location 이 `api.data.go.kr` 백엔드 연결을 재사용하는데 상위가 유휴 연결을 끊어 **간헐적으로 60초 무응답 후 실패**한다(동일 요청 3회 중 1회 재현). 앱은 readTimeout 5초라 이때 502가 되고 프론트에는 "학교 검색에 실패했습니다"가 뜬다. 조치는 인프라 쪽 `ProxyPass ... disablereuse=On connectiontimeout=5 timeout=15`.
- 학교 구분 매핑: `COLLEGE`→`UNIV_SE_NM`=전문대학, `UNIVERSITY`→대학, `MASTER`/`DOCTOR`→대학원.
- NEIS 명세 확인(2026-08-28): 신청주소 `hub/schoolInfo`, 기본인자 `KEY`/`Type`/`pIndex`/`pSize`, 신청인자 `SCHUL_NM`/`SCHUL_KND_SC_NM`, 출력값 `SD_SCHUL_CODE`/`SCHUL_NM`/`LCTN_SC_NM`. 고등학교는 학교코드가 있다.

#### 지원서 학력의 학교 식별자  🟢 확정 (2026-08-27, 프론트 반영 완료)

- `ApplicationEducation.schoolId`(School PK, Long) → `schoolCode`(String, 50자) + `schoolSource`(`SchoolSource` enum)로 교체 완료(S2·S3).
- 지원자가 학교명을 직접 입력하면 `schoolCode`·`schoolSource`는 null 이다(프론트가 검색 선택값을 비운다).
- 학력 요청/응답에서 `schoolId` 제거, `schoolCode`·`schoolSource` 추가. 관리자 학력 조회 응답도 동일.
- 학교별 퍼널 통계는 `schoolCode` 기준 그룹핑으로 바뀌었고, 표시명은 지원서 학력 행의 `schoolName`을 쓴다.
  학교 그룹의 `groupId`는 항상 null 이다(학교코드가 Long PK 가 아님, CERTIFICATE dimension 과 동일).
- 기존 `school_id` 컬럼/값은 폐기한다. `ddl-auto: update` 라 컬럼은 자동 삭제되지 않으므로 운영에서는 수동 DROP 이 필요하다.

### 화면: 지원자 어학 (ApplicationLanguage)

- 프론트: `src/api/application/sections/languageApi.ts`, `src/views/applicant/application/sections/LanguageSection.vue` (ApplicationFormView `sectionComponentMap.LANGUAGE`)
- 백엔드: `com.shinyoung.recruit.controller.ApplicationLanguageController`

#### GET·POST `/api/applications/{applicationId}/languages`  🟢 (프론트 반영 완료)

- 변경(2026-06-23): 요청·응답에서 `score`,`grade` 제거. `scoreOrGrade`(선택), `conversationalAbility`(선택) 추가.
- 변경(2026-08-28): 언어·시험명을 공통코드 선택으로 전환. `languageCode`,`testCode` 추가(필수). `languageName`,`testName`은 표시명 스냅샷으로 유지(필수).
- 요청: `{ languages: [{ languageCode, languageName, testCode, testName, scoreOrGrade, conversationalAbility, examDate, expiredDate, issuingOrganization, sortOrder }] }`
- 응답(200): `ApiResponse<[{ languageId, languageCode, languageName, testCode, testName, scoreOrGrade, conversationalAbility, examDate, expiredDate, issuingOrganization, sortOrder }]>`
- `conversationalAbility`는 공통코드 그룹 `LANGUAGE_CONVERSATION` 코드 문자열(프론트가 CommonCode로 렌더, 백엔드 validation 미결합, 코드 시드 안 함).
- `languageCode`는 공통코드 그룹 `LANGUAGE_TYPE`, `testCode`는 언어별 그룹 `LANGUAGE_TEST_{languageCode}`(예 `LANGUAGE_TEST_ENGLISH`) 코드 문자열이다. 백엔드 validation 미결합(공백만 검사), 코드 시드 안 함 — 관리자 CommonCode API로 등록한다.
- 직접입력: 코드가 `ETC`면 프론트가 자유입력을 받아 `languageName`/`testName`에 넣는다. 그 외엔 선택한 코드의 `displayName`을 그대로 스냅샷한다. `ETC` 옵션은 프론트가 목록 끝에 항상 붙인다(관리자 등록 불필요).
- 표시·PDF·관리자 검색은 `languageName`/`testName`(표시명)을 쓴다. 집계는 `languageCode`/`testCode` 기준이다.
- 변경(2026-09-01, 🟢 확정): 요청·응답에 `registrationNumber`(등록번호, 선택) 추가. 자유 입력 String, 백엔드 검증 없음. PII 파기(`purgeLanguages`) 대상에 포함(null 처리).
- 변경(2026-09-01, 프론트 전용): 지원서 어학 화면에서 `conversationalAbility`(회화능력) 입력을 제거하고 그 자리에 `registrationNumber`를 노출한다. **백엔드 필드·DB 컬럼·관리자 검색 필터(`languageLevel`)·PDF '회화능력' 출력은 유지**한다(프론트가 값을 보내지 않으므로 신규 저장분은 null).
- 관리자 조회 `GET /api/admin/applications/{id}/languages` 응답도 동일하게 `scoreOrGrade`/`conversationalAbility`/`languageCode`/`testCode` 반영. `registrationNumber`는 관리자 응답에 **미포함**(필요 시 별도 슬라이스).

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
- 변경(2026-09-01, 프론트 전용 / 계약 무변경): `expiredDate` 화면 라벨 "유효기간" → **"유효일자"**.

### 화면: 지원자 공백기간 (ApplicationGapPeriod)

- 프론트: `src/api/application/sections/gapPeriodApi.ts`, `src/views/applicant/application/sections/GapPeriodSection.vue` (ApplicationFormView `sectionComponentMap.GAP_PERIOD`)
- 백엔드: `com.shinyoung.recruit.controller.ApplicationGapPeriodController`

#### GET·POST `/api/applications/{applicationId}/gap-periods`  🟢

- 요청: `{ gapPeriods: [{ startDate, endDate, gapType, reason, description, sortOrder }] }`
- 응답(200): `ApiResponse<[{ gapPeriodId, startDate, endDate, gapType, reason, description, sortOrder }]>`
- 필수: `startDate`, `endDate`, `gapType`, `reason`. `description`은 선택(≤2000).
- `gapType`은 enum `EDUCATION`/`CAREER`/`OTHER`(프론트 라벨 학업/경력/기타, 하드코딩 — 공통코드 아님).
- 공백기간은 선택(0개 허용 = 빈 배열). 전체 교체(replace) 방식. 프론트 "해당 사항 없음" 체크박스는 영속화 부재로 주석 처리됨.
- 변경(2026-09-01, 프론트 전용 / 계약 무변경): 화면에서 "시작일"/"종료일" 두 행을 **"공백기간" 한 행**으로 합쳐 표시(`startDate ~ endDate`). 전송 필드는 동일.

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
- 변경(2026-09-01, 🟢 확정): 요청·응답에 `applicationRouteCode`(지원 경로, 선택, ≤50) 추가. 공통코드 그룹 `APPLICATION_ROUTE` 코드 문자열이며 값이 있으면 **활성 코드인지 백엔드가 검증**한다(비활성/미등록이면 400). 미입력 허용. 평문 컬럼(암호화 아님, PII 파기 대상 아님). 코드 시드는 안 함 — 관리자 CommonCode API로 등록해야 드롭다운에 뜬다. 프론트는 `BasicInfoSection.vue`에서 드롭다운으로 렌더. 관리자 조회 응답(`AdminBasicInfoResponse`)은 무변경.
- 변경(2026-09-01, 프론트 전용): 이메일 항목 라벨 `e-mail` → `이메일`.

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

- 프론트: `src/api/menuApi.ts`, `src/types/menu.ts`, 헤더(지원자) `src/layouts/ApplicantHeader.vue`, 관리자 사이드바 `src/layouts/AdminSidebar.vue`
- 백엔드: `com.shinyoung.recruit.controller.MenuController`

#### GET `/menu/tree` · `/menu/{menuId}` · `/menu/breadcrumb`, POST `/menu/admin/menu` · `/menu/admin/menu/{menuId}`  🟢

- 변경(2026-06-30, 🟢 확정): 메뉴에 `icon`(아이콘 컴포넌트명) 필드 추가. ant-design-vue 아이콘 컴포넌트명을 **문자열 그대로** 저장(예: `"SettingOutlined"`). nullable, 백엔드 검증 없음(자유 문자열, `VARCHAR(100)`). 대메뉴/소메뉴 모두 허용. 지원자(가로 헤더) 메뉴는 아이콘 미사용(null), 관리자 좌측 사이드바용.
- 메뉴 노드: `{ id, parentId, site('APPLICANT'|'ADMIN'), type('ROUTE'|'URL'), name, path, sortOrder, icon, children:[...] }`. (트리는 최대 2단계, `children` 재귀.)
- 저장 요청(`MenuSaveRequest`): `{ site, type, parentId?, name, path?, sortOrder?, icon? }` → 응답 `ApiResponse<{ id }>`.
- 조회 응답(`MenuResponse`): 위 메뉴 노드 모양. `GET /menu/tree?site=`, `GET /menu/breadcrumb?site=&path=`, `GET /menu/{menuId}`.
- 매핑: front `menuApi.getMenuTree()`/`getBreadcrumb()` ↔ back `MenuController.getTree()`/`getBreadcrumb()`, front `menuApi.createMenu()`/`updateMenu()` ↔ back `MenuController.createMenu()`/`updateMenu()`.
- 변경(2026-08-11, 🟢 확정): 프론트 `MenuItem`에 `icon: string | null` 반영 + 관리자 사이드바 아이콘 렌더링 구현. → **백엔드 계약 변경 없음**(응답 스키마 그대로).
- 아이콘 허용 목록(중요): `icon` 문자열은 `src/common/antIcon.ts`의 `ADMIN_MENU_ICONS`(명시적으로 import 한 ant-design-vue 아이콘 93종)에서만 해석된다. 목록 밖 이름이거나 `null`이면 `AppstoreOutlined`로 대체되어 라벨 정렬만 유지된다(오류 아님). **DB에 넣는 `icon` 값은 이 목록 안에 있어야 실제 아이콘이 보인다.**
  - 네임스페이스(`import * as`)로 전체 세트를 해석하지 않는 이유: 아이콘 790종이 전부 번들에 포함되고 지원자 화면까지 쓰는 공통 청크로 올라간다(측정 결과 index 청크 +1,057kB / gzip +175kB). 동적 import로 분리해도 지원자 화면이 같은 패키지를 정적 import 하고 있어 한 청크로 합쳐진다. 명시 import 방식은 공통 청크 증가 0이고 아이콘이 admin 지연 청크(113kB / gzip 26kB)에만 들어간다.
  - 후속 메뉴 관리 화면의 **아이콘 피커는 `ADMIN_MENU_ICONS`를 선택지로 그대로 사용**한다. 아이콘을 추가하려면 이 목록에 import를 추가한다.
- 관리자 사이드바 메뉴 운용 규약(`GET /menu/tree?site=ADMIN`): **대메뉴 = path 없는 그룹 라벨**(작은 글씨, 이동 안 함), **소메뉴 = 아이콘 + 이름의 실제 이동 대상**. 활성 표시는 `menuStore.isActiveMenu('ADMIN', ...)`(경로 trail 기반)이라 대메뉴에 path가 없어도 정상 동작한다. 하위가 없는 대메뉴는 그 자체를 이동 항목으로 표시한다.
- 범위 밖(후속): 삭제(요청 시 DELETE 대신 POST 사용), 메뉴 뱃지(설계 시안의 카운트 뱃지 — `MenuItem`에 해당 필드 없음).

#### 화면: 관리자 메뉴 관리 (MenuManageView) — 시안 1c "3단 컬럼"  🟢 확정

- 프론트: `src/views/admin/MenuManageView.vue`, 라우트 `/admin/menus`(name `AdminMenuManage`, `adminRoutes` 하위 → `requiresAuth` + `ROLE_ADMIN`/`ROLE_RECRUIT_ADMIN` 상속)
- **백엔드 계약 변경 없음.** 위 기존 엔드포인트(`GET /menu/tree`, `POST /menu/admin/menu`, `POST /menu/admin/menu/{menuId}`)를 그대로 소비한다. 요청/응답 스키마 무변경.
- 화면 구성: 탭(지원자 `APPLICANT` / 관리자 `ADMIN`) → 3단 컬럼(메인메뉴 목록 › 서브메뉴 목록 › 상세 폼).
- 편집 규약(시안 1c): 한 번에 **메인메뉴 또는 서브메뉴 하나만** 편집한다. 서브메뉴 추가는 **저장된 메인메뉴가 선택된 경우에만** 가능(미선택 시 `+` 비활성).
- 폼 필드 → `MenuSaveRequest` 매핑: 메뉴명→`name`, 상위 메인메뉴(읽기 전용 표시)→`parentId`, 메뉴 유형→`type`, 경로→`path`, 정렬 순서(숫자 입력)→`sortOrder`, 아이콘→`icon`. `site`는 활성 탭에서 결정된다.
- 아이콘 피커: `ADMIN_MENU_ICONS`(93종)를 선택지로 사용하며 **관리자 탭의 서브메뉴에서만** 노출한다(시안 1c 규약). 백엔드는 대메뉴 `icon`도 허용하므로 이는 화면 레벨 제약이다.
- 화면에서 제외(2026-08-11 결정): 시안 1c의 **"사용 여부" 토글** — `Menu` 엔티티/`MenuResponse`에 해당 필드가 없다. 필요해지면 별도 슬라이스에서 엔티티·DDL·트리 필터링 규칙과 함께 추가한다.
- 클라이언트 검증은 `MenuService`의 서버 검증을 그대로 미러링한다(소메뉴 `path` 필수, `ROUTE`는 `/` 시작, `URL`은 `http(s)://` 시작). 서버가 단일 출처이며 클라이언트 검증은 왕복을 줄이기 위한 것이다.
- 보안(2026-08-11, 🟢 확정): `POST /menu/admin/menu*`는 컨트롤러 경로가 `/api/menu/admin/menu`라 `SecurityConfig`의 broad `/api/admin/**` 매처에 걸리지 않았고 `anyRequest().permitAll()`로 흘러 **비인증 생성/수정이 가능한 상태**였다. 명시 매처 `POST /api/menu/admin/menu`, `POST /api/menu/admin/menu/*` → `hasAnyAuthority("ROLE_ADMIN","ROLE_RECRUIT_ADMIN")`를 추가해 막았고 `SecurityConfigTest` 6건으로 고정했다(비인증 401 / 타권한 403 / 관리자 통과 / `GET /menu/tree` permitAll 회귀). 경로 자체(`/menu/admin/menu`)는 계약 안정성을 위해 유지한다 — 정리하려면 별도 슬라이스에서 프론트와 함께 옮긴다.
- 부트스트랩 주의: 메뉴 데이터는 DB에만 있으므로 사이드바에 "메뉴 관리" 항목이 없는 상태에서는 `/admin/menus`로 직접 접속해 이 화면에서 자기 자신의 메뉴(대메뉴 "메뉴 관리" + 소메뉴 "메뉴 관리")를 등록한다.

### 화면: 지원서 작성 완성도 (ApplicationFormView — 상단 스텝 카운터)

- 프론트: `src/views/applicant/ApplicationFormView.vue`, `src/api/application/dashboardApi.ts`, `src/types/application/dashboard.ts`
- 백엔드: `com.shinyoung.recruit.controller.ApplicationController` (신규 아님, 기존 구현)

#### GET `/applications/{applicationId}/dashboard`  🟢 확정 (프론트 반영 완료 / 백엔드 기존 구현)

- 용도: 지원서 작성 화면 상단 스텝 네비게이션의 "남은 입력사항"(페이지별 `완료수/필수수`) 카운터 데이터 소스. **임시저장 여부가 아니라 저장된 데이터가 필수 규칙을 만족하는지**를 백엔드 `ApplicationCompletionReadChecker`가 판정한다.
- 응답(200): `ApiResponse<ApplicationDashboardResponse>`
  - `completionSummary`: `{ requiredSectionCount, completedRequiredSectionCount, requiredMissingCount, optionalSectionCount, completedOptionalSectionCount, optionalIncompleteCount, requiredCompletionRate, submitBlockingIssueCount }`
  - `requiredMissingSections` / `optionalIncompleteSections`: `[{ sectionCode, sectionName, required, complete, reasonCode, message }]` — **미완 섹션만** 담김(완료 섹션은 목록에 없음).
  - 그 외: `{ applicationId, jobPostingId, jobPostingTitle, jobPositionName, applicationStatus, accepting, editable, submittable, withdrawable, submittedAt, withdrawnAt, latestAnnouncedStageName, latestResultStatus }`
- 플래그 규칙(2026-09-02 변경): `editable = status != WITHDRAWN && accepting` — **최종 제출(SUBMITTED) 이후에도 접수기간 중이면 수정 가능**. `submittable = status == DRAFT && accepting && 결함 0`(재제출 없음), `withdrawable = status == SUBMITTED && accepting`.
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
- `editable` 규칙(2026-09-02 변경): `status != WITHDRAWN && accepting`. 제출 이후에도 접수기간 중이면 true다. 프론트는 `editable`로 섹션 편집/임시저장을, `applicationStatus == 'DRAFT'`로 최종 제출 버튼을 제어한다.
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

### 화면: 지원자 첨부파일 (ApplicationAttachment — 독립 섹션) ⛔ 프론트 폐지 (2026-09-01)

- 폐지(2026-09-01, 프론트 전용 / 백엔드 무변경): 독립 첨부 섹션을 지원서 화면에서 제거했다.
  - `AttachmentSection.vue` 삭제. `ApplicationFormView`의 `sectionComponentMap`/`sectionNameMap`/`completionSectionCodeMap`에서 `ATTACHMENT` 제거하고, `pages` 계산 시 `sectionType === 'ATTACHMENT'` 항목과 그로 인해 비게 된 페이지를 걸러낸다.
  - 관리자 공고 등록/수정 화면에서 "첨부파일" 체크박스 제거. `AdminApplicationFormConfig.useAttachment`는 계약 유지를 위해 **항상 `false`로 전송**(신규 공고). 기존 공고를 수정 저장하면 저장돼 있던 값이 그대로 다시 전송되므로 `useAttachment=true`인 기존 공고는 백엔드 레이아웃에 ATTACHMENT 페이지가 남는다 — 프론트가 걸러내므로 화면에는 안 보인다.
  - 백엔드 `ApplicationSectionType.ATTACHMENT`, `ApplicationFormConfig.useAttachment`, `JobPostingAttachmentRequirement`, 첨부 엔드포인트는 **모두 그대로 유지**한다(증명사진·경력기술서가 같은 엔드포인트를 쓴다).
  - 대체: 경력기술서는 경력 섹션에서 첨부한다(위 "지원자 경력" 참고). 증명사진은 기본정보 섹션(`sectionType=BASIC_INFO`) 유지.
- 프론트: ~~`src/views/applicant/application/sections/AttachmentSection.vue`~~(삭제), `src/api/application/sections/attachmentApi.ts`(유지 — 증명사진·경력기술서가 사용), `src/types/application/sections/attachment.ts`(유지, 섹션 전용 상수/타입만 제거)
- 백엔드: `com.shinyoung.recruit.controller.ApplicationAttachmentController` (기존 구현, 무변경)

아래는 **폐지 이전 계약 기록**이다. 엔드포인트 계약 자체는 여전히 유효하다.

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

### 화면: 관리자 대시보드 (AdminHomeView — 시안 2a "퍼널 중심")

- 프론트: `src/views/admin/AdminHomeView.vue` + `src/views/admin/dashboard/*.vue`(위젯 카드 6종), `src/api/statisticsApi.ts`, `src/api/adminJobPostingApi.ts`, `src/types/statistics.ts`, `src/common/chartPalette.ts`. 라우트 `/admin`
- 대시보드는 `dimension=POSITION,SCHOOL,CERTIFICATE&topN=5` 1콜 + `applications-daily` 1콜을 병렬로 호출한다. 공고 목록은 `GET /admin/job-postings`(기본 선택 = 접수 중인 첫 공고).
- 백엔드: `com.shinyoung.recruit.controller.AdminStatisticsController`
- 설계서: `docs/superpowers/specs/2026-08-11-admin-dashboard-statistics-api-design.md`
- 시안 요청서: `docs/design/관리자 대시보드 시안 요청서.md`

#### GET `/admin/job-postings/{jobPostingId}/statistics/funnel`  🟢 확정 (확장, front-back 반영 완료)

- 기존 엔드포인트의 **하위호환 확장**. 화면 스코프는 공고 1건이다.
- 변경: `dimension`이 **콤마 구분 다중 값**을 받는다(`?dimension=POSITION,SCHOOL,CERTIFICATE`). 대시보드 1회 로드에 4콜이 필요하던 것을 1콜로 줄인다(코호트·단계결과 로드 1회). 파싱은 trim + 대문자 + 중복 제거(입력 순서 유지), 값 하나라도 `FunnelDimension` 밖이면 **400**(기존 동작).
- 추가 응답 필드: `dimensionGroups: [{ dimension, groups: [DimensionFunnelResponse] }]` — 항상 채운다(미지정이면 `[]`).
- 기존 `dimension`/`dimensions`는 **단일 요청일 때만** 기존과 동일하게 채우고, 다중 요청이면 `null`/`[]`이다. `@Deprecated` 표기하며 **신규 소비자는 `dimensionGroups`만 쓴다.**
- 추가 응답 필드: `stages[].averageDwellDays: Double | null` — 단계 간 평균 소요일. 기준시각은 첫 단계면 `JobApplication.submittedAt`, 그 외에는 직전 단계 `StageResult.decidedAt`. 미확정 건·직전 값 없는 건은 표본에서 제외하고, 표본 0이면 `null`(0.0으로 채우지 않는다).
- `topN`은 기존 규칙 유지 — SCHOOL/CERTIFICATE에 **공통** 적용, POSITION에서는 무시. 기본 10, 상한 100.
- 프로젝션 변경(구현 주의): `FunnelStageResultRow`에 `decidedAt`, `FunnelCohortRow`에 `submittedAt` 추가. 둘 다 JPQL `new` 프로젝션이라 **record와 쿼리를 반드시 함께** 고친다.

#### GET `/admin/job-postings/{jobPostingId}/statistics/applications-daily`  🟢 확정 (신규, front-back 반영 완료)

- 응답: `{ jobPostingId, jobPostingTitle, from, to, totalSubmitted, days: [{ date, submittedCount, cumulativeCount }] }`
- 기준은 `JobApplication.submittedAt`의 날짜. `WITHDRAWN` **포함**(그날 제출 사실은 있었다), `DRAFT` 제외(`submittedAt is null`) — 퍼널 코호트와 같은 기준이라 `totalSubmitted`가 퍼널 `population.p`와 일치한다.
- 구간은 공고 `receptionStartDateTime` 날짜 ~ `min(receptionEndDateTime, 오늘)`. **제출 0건인 날짜도 0으로 채운다**(라인 차트가 끊기지 않게).
- 서비스는 `ApplicationTrendStatisticsService` 신설(퍼널과 관심사가 다름). 집계는 DB `GROUP BY` 1회 — 퍼널처럼 전체를 메모리에 올리지 않는다.
- 🔴 **DB 호환성 미검증**: 그룹핑에 JPQL `cast(application.submittedAt as LocalDate)`를 쓴다. H2(테스트)에서는 검증됐으나 **운영 후보 MariaDB에서는 아직 확인되지 않았다.** 번역이 실패하면 날짜 그룹핑을 Java 계층으로 옮긴다(한 공고의 제출 건수만 읽으므로 비용 차이는 크지 않다).

#### 프론트가 처리할 불일치 (백엔드 무변경)

- **`distribution`의 분모는 P(공고 전체)다.** 시안 스택 막대는 "그 단계 대상자" 분모이므로, 프론트가 `noResult`를 제외한 6버킷 합(`P − noResult`)으로 **정규화**해야 한다. 정규화하지 않으면 1차 이후 막대가 전부 쪼그라든다.
- **`pending`(미확정)을 6번째 세그먼트로 그린다.** 진행 중 공고에서는 pending이 다수일 수 있어 빠뜨리면 비율이 왜곡된다. 색은 팔레트 슬롯 6(녹색)을 쓰면 합격으로 오독되므로 중립 회색 계열로 빼는 것을 검토한다(프론트 슬라이스에서 확정).

- 보안: 두 경로 모두 `/api/admin/**` 매처 → `ROLE_ADMIN`/`ROLE_RECRUIT_ADMIN`. **SecurityConfig 변경 없음.** statistics는 집계값만 노출하므로 audit 미기록(기존 규칙).
- 범위 밖: 시안 D(진행 상태·일정)·E(처리 대기)·F(지원자 구성) 위젯, 전사 통합 퍼널(공고 횡단), 경쟁률(`JobPosition`에 모집인원 필드 없음), 캐싱(실측 후 별도 슬라이스).

### 화면: 관리자 공고 등록/수정 + 공고 이미지 (JobPostingImage)  🟢 확정 (2026-08-12, front-back 반영 완료)

설계: `docs/superpowers/specs/2026-08-12-job-posting-image-input-design.md`. 공고 본문은 WYSIWYG 대신 이미지 목록. `contentHtml`은 공고에서 deprecated — 생성/수정 요청(`JobPostingCreateRequest`/`JobPostingUpdateRequest`)에서 optional이며, **null 입력은 빈 문자열 `""`로 저장**된다(ddl-auto:update가 기존 스키마의 NOT NULL을 완화하지 못하므로 마이그레이션 없이 호환 — `JobPosting.defaultContentHtml`). 신규 화면은 읽고 쓰지 않음(공지사항 Notice는 무관). **발행 조건: 이미지 ≥1장 또는 (레거시) contentHtml 존재(blank 제외)** — 위반 시 400 "공고 본문 이미지가 최소 1장 필요합니다."

- 백엔드: `JobPostingImage` 엔티티(+`JobPostingImageRepository`), `JobPostingImageService`, `JobPostingImageStorageService`(전용 root, 첨부 헬스스캔과 분리), `ImageSignatureValidator`, `JobPostingImageController`.
- 프론트: `src/views/admin/jobPosting/`(List/Form/Detail 3종), `src/components/jobPosting/JobPostingImageStack.vue`(지원자 상세·관리자 미리보기 공용), `src/api/adminJobPostingApi.ts`·`boardApi.ts` 확장, 라우트 `/admin/job-postings`, `/new`, `/:id`, `/:id/edit`. 이미지는 `<img src>` 직접 참조 대신 **blob 응답 + objectURL**(세션 쿠키 이슈 회피).

#### POST `/admin/job-postings` (multipart 변형 추가) 🟢
- 기존 JSON 생성은 유지(하위호환). `consumes=multipart/form-data` 변형 추가:
  - part `request`(application/json): 기존 `JobPostingCreateRequest` 모양 (contentHtml optional)
  - part `imageMetas`(application/json, optional): `[{ altText, sortOrder }]`
  - part `imageFiles`(file[], optional): imageMetas와 개수·순서(index 짝) 일치 — 불일치 시 400
- 응답: `ApiResponse<Long>` (생성 id). 공고+이미지 단일 트랜잭션 생성(draft). 파일은 전체 선검증 후 저장.

#### 이미지 단위 API (관리자, 수정 화면 diff용) 🟢
- POST `/admin/job-postings/{id}/images` (multipart part `file` + query `altText`, `sortOrder?`) → `ApiResponse<Long>` (imageId). sortOrder 생략 시 맨 뒤(+1). 마감(CLOSED) 공고는 400.
- POST `/admin/job-postings/{id}/images/{imageId}` body `{ altText }` → altText 수정
- POST `/admin/job-postings/{id}/images/{imageId}/delete` → 삭제(행 삭제 + 파일 best-effort 삭제)
- POST `/admin/job-postings/{id}/images/order` body `{ imageIds: [..] }` → 전체 순서 재지정(배열 index = sortOrder). imageIds가 해당 공고 이미지 전체와 정확히 일치하지 않으면 400.
- GET `/admin/job-postings/{id}/images/{imageId}/file` → 바이너리(Content-Disposition inline). draft 포함.
- 수정 화면 diff 적용 순서(프론트): 삭제 → 신규 추가(imageId 확보) → altText 변경 → 전체 order 재지정.

#### 상세 응답 확장 🟢
- `GET /admin/job-postings/{id}`(`JobPostingDetailResponse`), `GET /job-postings/{id}`(`JobPostingPublicDetailResponse`) 응답에 `images: [{ id, altText, sortOrder, contentType, fileSize }]` 추가(sortOrder asc, id asc). `storagePath`는 노출하지 않음(첨부 규약과 동일).

#### GET `/job-postings/{id}/images/{imageId}/file` (공개) 🟢
- **발행(PUBLISHED)+공개조건 충족 공고의 이미지만** 응답(공개 상세와 동일 조건 — `findPublicDetailById`). 아니면 404. permitAll 경로이므로 이 검사가 draft 유출 차단의 2차 방어선(MockMvc 테스트로 draft 404 검증 완료).

#### 제한/검증 🟢
- 형식 jpg/jpeg/png/webp — Content-Type 허용목록(`image/jpg`→`image/jpeg` 정규화) + 확장자 허용목록 + **매직바이트**(앞 12바이트) 삼중 검증. 장당 10MB, 공고당 10장, altText 필수(trim, ≤200자). 위반은 모두 400(`InvalidJobPostingException`).
- 설정 prefix `recruit.posting-image.*`(storage-root 기본 `posting-images` — 첨부 storage와 반드시 분리). multipart 전역 한도(25MB/105MB)는 기존 설정으로 충분.
- ⚠️ 기존 컨트롤러 테스트 6건이 Boot 4(Jackson 3)의 null→primitive 거부로 **본 슬라이스와 무관하게 깨져 있었음** — 픽스처에 `useAttachment` 필드를 보강해 수리(백엔드 커밋 `2e683ea`). JSON으로 공고를 생성하는 클라이언트는 `applicationFormConfig`의 8개 boolean(useAttachment 포함)을 모두 보내야 한다.

#### 운영 작업 (코드 아님)
- 메뉴 등록: 메뉴 관리 화면(`/admin/menus`)에서 대메뉴 "공고 관리"(path 없는 그룹 라벨) + 소메뉴 "공고 목록"(`/admin/job-postings`), "공고 등록"(`/admin/job-postings/new`) 등록. 아이콘은 `ADMIN_MENU_ICONS`에서 선택.

### 화면: 관리자 권한 관리 (RoleMappingView)  🟢 확정 (2026-08-13, front-back 반영 완료)

- 프론트: `src/views/admin/RoleMappingView.vue`, `src/api/adminRoleMappingApi.ts`, `src/types/roleMapping.ts`, 라우트 `/admin/role-mappings`(name `AdminRoleMapping`, `adminRoutes` 하위 → `requiresAuth` + `ROLE_ADMIN`/`ROLE_RECRUIT_ADMIN` 상속)
- 백엔드: `com.shinyoung.recruit.controller.AdminRoleMappingController`, `service.RoleMappingService`, 신규 엔티티 `UserRoleMapping`(테이블 `user_role_mapping`), 상수 `security.auth.RoleNames`
- 설계 문서: `docs/superpowers/specs/2026-08-13-role-mapping-admin-design.md`

#### 권한 모델 (계약의 전제)

- 권한 5축: `ROLE_ADMIN`(IT), `ROLE_RECRUIT_ADMIN`(운영), `ROLE_PRIVACY_ADMIN`(정보보호), `ROLE_INTERVIEWER`(면접관, 이번에 부여 경로 신설), `ROLE_APPLICANT`(지원자) + 보조 `ROLE_EMPLOYEE`.
- 최종 임직원 권한 = 부서 매핑(`dept_role_mapping`, AD 그룹명 부분일치) ∪ 개인 매핑(`user_role_mapping`, loginId 완전일치). **합집합만, revoke 없음.** 로그인/`/auth/me` 응답의 `roles` 배열 스키마는 무변경.
- `user_role_mapping.login_id`는 FK 없는 문자열 — 최초 로그인 전(JIT 생성 전) 직원에게도 사전 부여 가능.

#### GET `/admin/role-mappings/roles`  🟢

- 응답 `ApiResponse<[{ name, label }]>` — 부여 가능 role 5종(지원자 제외: ADMIN, RECRUIT_ADMIN, PRIVACY_ADMIN, INTERVIEWER, EMPLOYEE). 단일 출처는 백엔드 `RoleNames`.

#### 부서 매핑: GET·POST `/admin/role-mappings/dept`, POST `/dept/{id}`, POST `/dept/{id}/delete`  🟢

- 목록 응답: `ApiResponse<[{ id, deptName, roleName }]>` (페이징 없음, 소규모 전제).
- 저장 요청: `{ deptName, roleName }` → 응답 `ApiResponse<{ id }>`. 수정은 전체 교체.
- 검증: `roleName`은 부여 가능 5종만, `deptName` trim 후 **2자 이상**(로그인 부분일치 매칭의 오매칭 방어), `(deptName, roleName)` 중복 거부(서비스 레벨).

#### 사용자 매핑: GET·POST `/admin/role-mappings/user`, POST `/user/{id}`, POST `/user/{id}/delete`  🟢

- 목록 응답: `ApiResponse<[{ id, loginId, roleName, userName, userDeptName }]>` — `userName`/`userDeptName`은 users에 있으면 채움, 없으면 null(화면에 "미등록" 표기).
- 저장 요청: `{ loginId, roleName }` → 응답 `ApiResponse<{ id }>`. 검증: loginId trim 필수, roleName 5종, `(loginId, roleName)` 중복 거부.

#### 보안

- 경로가 전부 `/api/admin/**`라 기존 broad 매처(`hasAnyAuthority(ROLE_ADMIN, ROLE_RECRUIT_ADMIN)`)에 걸림 → **SecurityConfig 무변경.** 인가(401/403/통과)는 테스트로 고정한다.
- 레포 관례: 수정·삭제도 POST(DELETE 동사 미사용).

#### 운영 작업 (코드 아님)

- 신규 테이블 `user_role_mapping` 운영 DDL: `recruit_back/recruit_backend/docs/codex/ops/` 수동 SQL 참조.
- 메뉴 등록: 메뉴 관리 화면에서 소메뉴 "권한 관리"(`/admin/role-mappings`) 등록.

### 화면: 지원현황 조회 (관리자 — 지원서 검색)

- 프론트: `src/views/admin/application/ApplicationStatus.vue`(목록·검색), `src/views/admin/application/Application.vue`(지원서 상세) + `src/api/admin/adminApplicationApi.ts`
- 백엔드: `com.shinyoung.recruit.controller.AdminApplicationController`, `service.JobApplicationService`

#### GET `/admin/applications`, GET `/admin/job-postings/{jobPostingId}/applications`  🟢 검색 조건 확장 — 백엔드 구현·검증 완료(2026-08-14) / 프론트 반영(2026-08-31, `phoneNumber` 조건만 화면 미노출)

- 기존 페이징 목록 API의 **하위호환 확장**. 기존 파라미터(`jobPostingId`(전자만)·`jobPositionId`·`status`·`page`·`size`) 유지, 아래 검색 조건 추가. 모든 조건 optional, 빈 문자열은 미적용으로 간주. enum 계열 값이 정의 밖이면 400.
- 추가 요청 파라미터(query):
  - `applicationType` — 지원구분. `JobPositionApplicationType` (NEW_GRADUATE | EXPERIENCED | NEW_GRADUATE_OR_EXPERIENCED)
  - `workLocation` — 근무지. 지원서의 `workLocationCode` 완전일치 (`jobGroup` 조건은 2026-08-31 제거)
  - `name` — 이름. `applicantNameSnapshot` 부분일치(LIKE)
  - `birthDateFrom`, `birthDateTo` — 생년월일 범위(ISO date). `ApplicationBasicInfo.birthDate`
  - `finalEducationLevel` — 최종학력. `EducationLevel` (HIGH_SCHOOL~DOCTOR). 지원서 학력 중 **최고 레벨**이 일치해야 함
  - `schoolName` — 학교명 부분일치(학력 행 아무거나)
  - `graduationStatus` — 졸업여부. `GraduationStatus`. **최종학력 행** 기준
  - `finalSchoolCondition` — 최종학교조건. DOMESTIC(countryCode 없음) | OVERSEAS(countryCode 있음) | TRANSFER(편입) | BRANCH(분교) | NIGHT(야간). **최종학력 행** 기준
  - `phoneNumber` — 휴대폰번호 부분일치(2026-08-28 추가, 프론트 검색폼 미노출). 전화 문의 지원자 F/U 용. 하이픈·공백을 무시하고 숫자만으로 비교한다(`010-1234-5678` 저장분을 `01012345678`로 검색해도 매칭). `Applicant.phoneNumber` 기준
  - `certificateName` — 자격증명 부분일치
  - `languageName` — 외국어구사. `ApplicationLanguage.languageName` 완전일치
  - `languageLevel` — 외국어수준. `conversationalAbility` 완전일치 (값 체계는 CommonCode 그룹 `LANGUAGE_LEVEL`(상/중/하)로 관리자 등록 — 별도 백엔드 코드 없음)
  - `stageType` — 전형 단계. `StageType` (DOCUMENT | FIRST_INTERVIEW | SECOND_INTERVIEW | FINAL_INTERVIEW | ETC)
  - `stageResultStatus` — 전형 결과. `StageResultStatus` (PENDING | PASSED | FAILED | ABSENT | WITHDRAWN | HOLD)
  - 전형별결과 화면 값 매핑: "서류지원" → `status=SUBMITTED`(stage 조건 없이), 그 외 → `stageType` + `stageResultStatus` 조합 (예: 서류전형합격 = DOCUMENT+PASSED, 1차면접결시 = FIRST_INTERVIEW+ABSENT)
- 응답(200): `ApiResponse<PageResponse<AdminApplicationSummaryResponse>>` — 기존 필드 유지 + 그리드용 파생 필드 추가(2026-08-14, 백엔드 검증 완료):
  - `jobTitle`(직무) — `JobPosition` 속성. `workLocation`(근무지)은 지원자가 선택한 근무지 표시명 (`jobGroup`은 2026-08-31 제거)
  - `birthDate`, `age` — `ApplicationBasicInfo.birthDate` + 조회 시점(오늘, 서버 Clock) 기준 만 나이. basic info 없으면 null
  - `finalEducationLevel`(최종학력), `finalSchoolName`(최종대학교) — 최고 EducationLevel 학력 행(검색 필터와 동일 판정). 학력 없으면 null
  - `stageType`, `stageResultStatus` — 최신(stageOrder 최대) 전형 결과, 검색 조건과 동일 값 체계. 발표 여부 무관(관리자 화면). 결과 없으면 null(서류지원 상태는 `status=SUBMITTED`로 판별)
  - `careerDescriptionDownloadUrl` — 경력기술서(`AttachmentType.CAREER_DESCRIPTION`, STORED·미삭제, 복수면 최신 1건) 다운로드 상대 URL `/admin/applications/{id}/attachments/{attachmentId}/download`. 없으면 null
  - 수험번호는 `applicationId`로 대체. 파생 필드는 페이지 단위 배치 조회 4회로 채움(N+1 없음)
- 오류: 400(page/size 범위 위반, enum 값 오류), 404(`jobPostingId` 미존재)
- 제외 확정: 성별(도메인 없음), 연락처(암호화 컬럼 검색 불가), 채용구분 연도

### 화면: 관리자 질문 템플릿 (전역 질문 은행)

- 프론트: (미구현) 질문 템플릿 관리 화면 + `src/api/questionTemplateApi.ts`
- 백엔드: `com.shinyoung.recruit.controller.QuestionTemplateController`, `service.QuestionTemplateService`
- 공통: 모든 경로는 `/api` prefix(`WebMvcConfig.addPathPrefix`) 적용. 삭제는 없고 `active` 플래그 soft disable만 존재(DB row 유지).

#### GET `/api/admin/question-templates`  🔴 백엔드 구현됨 / 프론트 미반영

- 요청(query): `page`(기본 0), `size`(기본 20, 1~100), `active`(optional — 미지정 시 활성·비활성 전체)
- 응답(200): `ApiResponse<PageResponse<QuestionTemplateResponse>>`
- `QuestionTemplateResponse`: `{ templateId, title, questionText, helperText, category, answerType, defaultRequired, defaultMaxLength, active, createdAt, updatedAt }`

#### GET `/api/admin/question-templates/{templateId}`  🔴 백엔드 구현됨 / 프론트 미반영

- 응답(200): `ApiResponse<QuestionTemplateResponse>`. 비활성 템플릿도 단건 조회는 허용
- 오류: 404(미존재)

#### POST `/api/admin/question-templates`, POST `/api/admin/question-templates/{templateId}`  🔴 백엔드 구현됨 / 프론트 미반영

- 생성/수정. 요청: `{ title, questionText, helperText?, category, answerType, defaultRequired, defaultMaxLength }`
- 요청에 `active` 없음. 활성 상태는 수정 API로 바꿀 수 없고 별도 command로만 전이한다
- 오류: 400(검증 실패), 404(수정 시 미존재)

#### POST `/api/admin/question-templates/{templateId}/deactivate`  🔴 백엔드 구현됨 / 프론트 미반영

- 비활성화(soft disable). `active=false`로 UPDATE, row 삭제 아님
- 비활성 템플릿은 신규 공고 질문 생성에 사용할 수 없다(기존 공고 질문 snapshot에는 영향 없음)
- 응답(200): `ApiResponse<QuestionTemplateResponse>` (`active=false`)
- 오류: 404(미존재). 이미 비활성인 템플릿 재호출은 통과(멱등)

#### POST `/api/admin/question-templates/{templateId}/activate`  🟡 백엔드 구현·검증 완료(2026-08-19) / 프론트 미반영

- 비활성 템플릿 재활성화. `active=true`로 UPDATE
- 이미 활성인 템플릿에 호출하면 400(`Active template cannot be activated again.`) — 예외 방식 확정
- 응답(200): `ApiResponse<QuestionTemplateResponse>` (`active=true`)
- 오류: 400(이미 활성), 404(미존재)
- 매핑: front (미구현) ↔ back `QuestionTemplateController.activateTemplate()`
- 참고: 공고별 질문(`JobPostingQuestion`)에는 activate 명령이 없다. 비활성 시 `sortOrder`가 남아 있고 중복 검사는 active 행만 대상이라, 재활성 시 sortOrder 충돌 해소 정책이 먼저 필요하다

### 화면: FAQ (지원자 FaqView / 관리자 AdminFaqManageView)

- 프론트: `src/views/applicant/FaqView.vue`, `src/views/admin/faq/AdminFaqManageView.vue`, `src/api/faqApi.ts`, `src/api/adminFaqApi.ts`
- 백엔드: `com.shinyoung.recruit.controller.FaqController`(공개), `com.shinyoung.recruit.controller.AdminFaqController`(관리자)
- 도메인: `FaqCategory`(카테고리) 1 : N `Faq`(질문/답변). 카테고리·FAQ 각각 `sortOrder`(정렬)와 `active`(노출) 보유
- 페이징 없음. 지원자 화면은 전체를 한 번에 받아 스크롤로 노출한다
- 답변(`answer`)은 **평문**이다. HTML을 저장/렌더링하지 않으며 줄바꿈만 그대로 노출한다(`white-space: pre-wrap`)
- CORS가 GET/POST만 허용하므로 삭제도 POST를 쓴다(`/delete`). 삭제는 `active=false` soft delete다

#### GET `/api/faqs`  🟢 확정(2026-08-26)

- 설명: 지원자 화면용 공개 조회. 인증 불필요
- 요청: 없음
- 응답(200): `ApiResponse<[{ id, name, faqs: [{ id, question, answer }] }]>`
- 노출 규칙
  - `active=true` 카테고리만, 그 안의 `active=true` FAQ만 반환
  - 정렬은 카테고리·FAQ 모두 `sortOrder ASC, id ASC`
  - 노출 가능한 FAQ가 0건인 카테고리는 응답에서 제외한다(빈 카테고리 클릭 방지)
- 매핑: front `faqApi.fetchFaqs()` ↔ back `FaqController.getFaqs()`

#### GET `/api/admin/faq-categories`  🟢 확정(2026-08-26)

- 설명: 관리자 카테고리 목록. 비활성 포함 전체를 `sortOrder ASC, id ASC`로 반환
- 응답(200): `ApiResponse<[{ id, name, sortOrder, active, faqCount }]>`
- `faqCount`는 해당 카테고리의 **활성 FAQ 수**다
- 권한: `ROLE_ADMIN` 또는 `ROLE_RECRUIT_ADMIN`
- 매핑: front `adminFaqApi.fetchCategories()` ↔ back `AdminFaqController.getCategories()`

#### POST `/api/admin/faq-categories`  🟢 확정(2026-08-26)

- 설명: 카테고리 생성. `sortOrder`는 서버가 `현재 최대값 + 1`로 부여한다(요청에 없음)
- 요청: `{ name, active }`
- 응답(200): `ApiResponse<{ id, name, sortOrder, active, faqCount }>`
- 오류: 400(`name` 공백), 400(이름 중복)

#### POST `/api/admin/faq-categories/{categoryId}`  🟢 확정(2026-08-26)

- 설명: 카테고리 수정. `sortOrder`는 이 API로 바꾸지 않는다(reorder 전용)
- 요청: `{ name, active }`
- 응답(200): `ApiResponse<{ id, name, sortOrder, active, faqCount }>`
- 오류: 400(검증 실패·이름 중복), 404(미존재)

#### POST `/api/admin/faq-categories/{categoryId}/delete`  🟢 확정(2026-08-26)

- 설명: 카테고리 soft delete(`active=false`). row 삭제 아님. 이미 비활성이면 멱등 통과
- 하위 FAQ의 `active`는 건드리지 않는다. 카테고리가 비활성이면 공개 조회에서 통째로 빠진다
- 응답(200): `ApiResponse<Void>`
- 오류: 404(미존재)

#### POST `/api/admin/faq-categories/reorder`  🟢 확정(2026-08-26)

- 설명: 카테고리 정렬 일괄 반영. 배열 순서대로 `sortOrder`를 `0..n-1`로 정규화한다
- 요청: `{ ids: [3, 1, 2] }`
- `ids`는 전체 카테고리 id 집합과 정확히 일치해야 한다(누락·중복·미존재 id는 400)
- 응답(200): `ApiResponse<Void>`

#### GET `/api/admin/faqs`  🟢 확정(2026-08-26)

- 설명: 관리자 FAQ 목록. 비활성 포함 전체를 `sortOrder ASC, id ASC`로 반환
- 요청(query): `categoryId`(필수)
- 응답(200): `ApiResponse<[{ id, categoryId, question, answer, sortOrder, active }]>`
- 오류: 404(카테고리 미존재)

#### POST `/api/admin/faqs`  🟢 확정(2026-08-26)

- 설명: FAQ 생성. `sortOrder`는 서버가 해당 카테고리 내 `최대값 + 1`로 부여한다
- 요청: `{ categoryId, question, answer, active }`
- 응답(200): `ApiResponse<{ id, categoryId, question, answer, sortOrder, active }>`
- 오류: 400(`question`/`answer` 공백, `question` 500자 초과), 404(카테고리 미존재)

#### POST `/api/admin/faqs/{faqId}`  🟢 확정(2026-08-26)

- 설명: FAQ 수정. `categoryId`를 바꾸면 다른 카테고리로 이동하며, 이동 시 `sortOrder`는 대상 카테고리의 `최대값 + 1`로 재부여한다
- 요청: `{ categoryId, question, answer, active }`
- 응답(200): `ApiResponse<{ id, categoryId, question, answer, sortOrder, active }>`
- 오류: 400(검증 실패), 404(FAQ·카테고리 미존재)

#### POST `/api/admin/faqs/{faqId}/delete`  🟢 확정(2026-08-26)

- 설명: FAQ soft delete(`active=false`). 이미 비활성이면 멱등 통과
- 응답(200): `ApiResponse<Void>`
- 오류: 404(미존재)

#### POST `/api/admin/faqs/reorder`  🟢 확정(2026-08-26)

- 설명: 한 카테고리 안의 FAQ 정렬 일괄 반영. 배열 순서대로 `sortOrder`를 `0..n-1`로 정규화한다
- 요청: `{ categoryId, ids: [7, 5, 6] }`
- `ids`는 해당 카테고리의 전체 FAQ id 집합과 정확히 일치해야 한다(누락·중복·타 카테고리 id는 400)
- 응답(200): `ApiResponse<Void>`
- 오류: 400(id 집합 불일치), 404(카테고리 미존재)

### 화면: 관리자 공통코드 관리 (AdminCommonCodeManageView)  🟢 확정(2026-08-28)

- 백엔드는 Phase 08a 기구현분을 그대로 사용하며 이번 화면 작업으로 변경되지 않았다
- 그룹 목록을 담는 테이블이 없어 그룹 자체를 `groupCode=CODE_GROUP` 의 코드 행으로 관리한다(self-host). 이 행의 `description`이 그룹의 사용 화면 메모다
- 삭제 API는 없다. 삭제는 `active=false` soft delete로만 처리한다
- `groupCode`/`code`는 생성 후 불변이라 수정 요청에 포함하지 않는다

#### GET `/api/admin/codes`  🟢 확정(2026-08-28)

- 설명: 비활성 포함 코드 조회. `groupCode` 생략 시 전체를 `groupCode` → `sortOrder` → `id` 순으로 반환한다. 화면은 그룹 목록도 이 응답에서 파생하므로 항상 생략 형태로 호출한다
- 요청: `?groupCode=NATIONALITY` (선택)
- 응답(200): `ApiResponse<[{ id, groupCode, code, displayName, sortOrder, active, description }]>`

#### POST `/api/admin/codes`  🟢 확정(2026-08-28)

- 설명: 코드 생성. `sortOrder` 미지정 시 0, `active` 미지정 시 true. 화면은 그룹 내 `최대값 + 10`을 기본값으로 채워 보낸다
- 요청: `{ groupCode, code, displayName, sortOrder, active, description }`
- 응답(200): `ApiResponse<{ id, groupCode, code, displayName, sortOrder, active, description }>`
- 오류: 400(필수값 공백, 길이 초과, `groupCode`+`code` 중복)

#### POST `/api/admin/codes/{id}`  🟢 확정(2026-08-28)

- 설명: 코드 수정. `active=false`가 soft delete다. `groupCode`/`code`는 불변이라 요청에 없다
- 요청: `{ displayName, sortOrder, active, description }`
- 응답(200): `ApiResponse<{ id, groupCode, code, displayName, sortOrder, active, description }>`
- 오류: 400(필수값 공백, 길이 초과), 404(미존재)

### 화면: 직무별 근무지 선택 (공고 상세 ApplicationDetailView + 관리자 공고 등록 AdminJobPostingFormView)

- 프론트: `src/views/applicant/ApplicationDetailView.vue`(직무·근무지 선택), `src/views/admin/jobPosting/AdminJobPostingFormView.vue`(근무지 후보 등록), `src/views/admin/application/ApplicationStatus.vue`(근무지 검색), `src/types/jobPosting.ts`
- 백엔드: `com.shinyoung.recruit.controller.JobPostingController` · `JobPostingPublicController` · `ApplicationController` · `AdminApplicationController`, `service.JobPostingService` · `JobApplicationService`
- 배경: 기존에는 `JobPosition.workLocation`이 단일 자유텍스트라 "직무 동일 / 근무지만 다름"을 별도 모집분야 행으로 등록해야 했다. 근무지를 공통코드화하고 **직무 1건이 후보 근무지 N건**을 갖도록 바꾼다. 지원자는 공고 상세에서 직무를 고르면 그 직무의 후보 근무지가 드롭다운에 동적으로 채워지고, 근무지를 함께 선택해 지원을 시작한다.
- 근무지 코드는 CommonCode 그룹 **`WORK_LOCATION`**을 쓴다. 코드 관리는 기존 공통코드 관리 화면(`/api/admin/codes`)을 그대로 사용하며 신규 코드 관리 API는 없다.
- 후보 개수가 곧 직무별 분기다(별도 `required` 플래그 없음).
  - 0개 → 근무지 선택 개념 없음. 지원자 화면에 드롭다운 미표시, 지원서에 근무지 저장 안 함(null)
  - 1개 → 근무지 고정(예: IT=본사). 프론트가 자동 선택 + readonly 표시
  - N개 → 지원자가 선택
- 마이그레이션: **신규 공고부터 적용**. 기존 `job_position.work_location` 컬럼은 코드에서 참조하지 않게 되며(`ddl-auto: update`는 컬럼을 지우지 않음) 정리는 수동 DDL로 한다.

#### POST `/admin/job-postings`, POST `/admin/job-postings/{id}`  🟢 확정(2026-08-31, front-back 반영 완료) (근무지 후보 등록)

- 변경: `jobPositions[]` 항목의 `workLocation`(단일 문자열) → **`workLocationCodes: string[]`**(CommonCode `WORK_LOCATION`의 `code` 목록, 순서가 곧 노출 순서)
- 요청(변경분): `jobPositions: [{ positionName, applicationType, jobTitle, workLocationCodes: ["HQ","BSN"], employmentType, sortOrder }]`
- 검증: 미지정/빈 배열 허용(후보 0개), 배열 내 중복 코드 400, `WORK_LOCATION` 그룹의 **활성 코드가 아니면** 400

#### GET `/admin/job-postings/{id}`  🟢 확정(2026-08-31)

- 변경: `jobPositions[].workLocation` → **`workLocations: [{ code, name }]`**. `name`은 CommonCode `displayName`

#### GET `/job-postings/{id}` (공개 상세)  🟢 확정(2026-08-31)

- 변경: `jobPositions[].workLocation` → **`workLocations: [{ code, name }]`** (활성 코드만, 등록 순서)
- 프론트는 이 배열로 근무지 드롭다운을 구성한다. **추가 API 호출 없음**(공고 상세 응답 한 번으로 직무별 후보를 모두 받음)

#### POST `/applications`  🟢 확정(2026-08-31) (지원 시작)

- 변경: 요청에 `workLocationCode` 추가 → `{ jobPostingId, jobPositionId, workLocationCode? }`
- 검증: 선택 직무의 후보가 1건 이상인데 `workLocationCode`가 없으면 400 / 후보에 없는 코드면 400 / 후보 0건인데 값을 보내면 400
- 저장: `JobApplication.workLocationCode` + `workLocationNameSnapshot`(선택 시점 `displayName` 스냅샷, 기존 `jobPositionNameSnapshot`과 동일 규약)

#### POST `/applications/{applicationId}`  🟢 확정(2026-08-31) (지원분야 수정)

- 변경: 요청에 `workLocationCode` 추가 → `{ jobPositionId, workLocationCode? }`. 검증 규칙은 생성과 동일(변경된 직무의 후보 기준)

#### GET `/applications/{applicationId}/form-page`  🟢 확정(2026-08-31) (표시용 확장)

- 변경: 응답에 `workLocationCode` + `workLocationName` 추가(스냅샷 기준, 미선택이면 null). 지원서 작성 화면 헤더의 "모집분야 / 근무지" 읽기전용 표시와, 지원분야 변경 모달의 초기 선택값으로 쓴다
- 지원분야 변경(2026-08-31): 지원서 작성 화면 헤더의 "변경" 버튼이 모달을 열고 `POST /applications/{applicationId}` 로 저장한다. 모집분야·근무지 후보 목록은 **공개 공고 상세(`GET /job-postings/{id}`)를 재사용**하며 전용 API를 두지 않는다. 철회 전(DRAFT·SUBMITTED)이고 접수기간 중이면 노출한다(`editable`, 2026-09-02 변경 — 이전에는 DRAFT 전용)

#### GET `/admin/applications`, GET `/admin/job-postings/{jobPostingId}/applications`  🟢 확정(2026-08-31) (근무지 조건 의미 변경)

- 변경: 검색 파라미터 `workLocation`이 `JobPosition.workLocation` 자유텍스트 완전일치 → **`JobApplication.workLocationCode` 완전일치**. 프론트 검색폼은 텍스트 입력 → `WORK_LOCATION` 공통코드 select로 교체
- 변경: 응답 `AdminApplicationSummaryResponse.workLocation`이 지원자가 선택한 근무지의 **표시명**(`workLocationNameSnapshot`)을 담는다. 미선택이면 null
- 제출 시 재검증: 공고 수정으로 후보 목록이 바뀌어 이미 선택한 근무지가 무효가 될 수 있으므로, 제출(`POST /applications/{id}/submit`) 시 `validateSelectedJobPosition`과 동일한 방식으로 근무지 유효성을 재검증하고 위반 시 400

#### GET `/admin/applications/{applicationId}`  🟢 확정(2026-08-31) (지원서 상세 표시 확장)

- 변경: 응답에 `workLocationNameSnapshot` 추가. 관리자 지원서 상세의 "직무/근무지" 칸은 모집분야의 후보가 아니라 **지원자가 실제로 선택한 근무지**를 보여준다. 미선택이면 null

#### 구현 메모 (🟢 확정 시점 기준)

- `job_position_work_location` 은 `@ElementCollection` + `@OrderColumn(sort_order)` 이며 `code` 와 **저장 시점 `displayName` 스냅샷(`name`)** 을 함께 담는다. 공고는 시점 문서라 라벨을 굳혀 두며, 공통코드 `displayName` 변경은 공고를 재저장할 때 반영된다
- 근무지 코드 유효성(활성 코드 존재)은 공고 저장 1회당 `WORK_LOCATION` 활성 코드 1회 조회로 검증한다(N+1 없음)
- `ApplicationCreateRequest` / `ApplicationUpdateRequest` 는 `workLocationCode` 를 생략한 축약 생성자를 유지한다(근무지 후보 0개 모집분야용)

#### 직군(`jobGroup`) 제거  🟢 확정(2026-08-31)

- 모집분야 입력은 **모집분야명 + 담당 직무(`jobTitle`)** 로 충분하다는 판단에 따라 `jobGroup` 을 전 구간에서 제거했다
- 영향: `JobPosition.jobGroup` 필드, 공고 등록/수정 요청의 `jobPositions[].jobGroup`, 공고 상세(관리자·공개) 응답의 `jobGroup`, 지원현황 검색 조건 `jobGroup`, `AdminApplicationSummaryResponse.jobGroup`
- 프론트에서 `jobGroup` 을 실제로 노출하던 곳은 관리자 공고 등록 폼의 "직군" 입력 하나뿐이었고, 검색폼·그리드 컬럼에는 없었다
- DB 컬럼 `job_position.job_group` 은 `ddl-auto: update` 특성상 자동 삭제되지 않는다. 정리는 수동 DDL 로 한다

---

### 화면: 지원서 설정 현황 (AdminApplicationFormListView)  🟡 초안 (2026-09-02)

- 프론트: `src/views/admin/applicationForm/AdminApplicationFormListView.vue`, `src/api/admin/adminApplicationFormApi.ts` (신규)
- 백엔드: `com.shinyoung.recruit.controller.AdminApplicationFormController` (신규)
- 배경: 공고별 지원서 설정 상태를 한 표에서 조망한다. 특히 **질문/첨부 추가로 활성 섹션이 늘어 저장된 레이아웃이 `placed == enabled` 불변식을 위반한 공고**를 찾아내는 것이 이 화면의 존재 이유다(위반 시 지원자 `form-page` 조회가 예외로 막힌다)

#### GET `/admin/application-forms`  🟢 확정(2026-09-02, front-back 반영 완료)

- 설명: 공고별 지원서 설정 요약 목록(페이지). 공고 목록 API와 별개다 — 기존 `GET /admin/job-postings` 는 필터 파라미터가 없고 설정 관련 필드도 없다
- 요청(query): `{ status?, receptionStatus?, configState?, editableOnly?, keyword?, page=0, size=20 }`
  - `status`: `DRAFT | PUBLISHED | CLOSED`
  - `receptionStatus`: `UPCOMING | ACCEPTING | CLOSED`
  - `configState`: `OK | DEFAULT | RELAYOUT_REQUIRED | MISSING`
  - `editableOnly`: boolean (접수 시작 전 & 미마감만)
  - 기본 정렬: `RELAYOUT_REQUIRED` 우선 → 접수 시작일 임박순
- 응답(200): `ApiResponse<PageResponse<{ jobPostingId, title, postingType, status, receptionStatus, receptionStartDateTime, receptionEndDateTime, sectionSummary: { enabledCount, requiredCount }, activeQuestionCount, requiredQuestionCount, layoutStored, pageCount, configState, editable, updatedAt }>>`
- `configState` 판정(서버 계산, 단일 출처):
  - `MISSING`: `applicationFormConfig == null` → 게시 불가
  - `RELAYOUT_REQUIRED`: `layoutStored && placedSections != enabledSections` → **지원자 form-page 조회 실패 상태**
  - `DEFAULT`: `!layoutStored` → `ApplicationFormLayoutDefaultFactory` 결과로 동작 중
  - `OK`: `layoutStored && placedSections == enabledSections`
  - `editable == false` 는 별도 필드로 내려 화면에서 🔒 로 덧붙인다(상태값과 직교)
- 성능: 질문 수·첨부요구 수는 기존 집계 쿼리(`countActiveQuestionPolicyByJobPostingIds`, `countPolicyByJobPostingIds`)를 재사용하고, 레이아웃은 `findLayoutItemsByJobPostingIds` 로 (공고, pageNo, sectionType) 을 한 번에 읽어 메모리 조합한다. 공고당 개별 조회는 하지 않는다
- 파생값 필터의 한계: `receptionStatus`·`configState`·`editableOnly` 는 계산 후에만 판정할 수 있어 **SQL 로 좁힌 뒤 메모리에서 거르고 페이징**한다. SQL 로 넘기는 조건은 `status`·`keyword` 뿐이다. 공고 테이블 규모가 작다는 전제이며, 규모가 커지면 파생값을 컬럼으로 승격해야 한다
- `size` 는 1~100. 벗어나면 400
- 마감 제외는 서버 조건이 아니라 **화면 기본값**이다(프론트가 응답에서 `status === 'CLOSED'` 를 거른다). `status` 에 "마감 제외"를 표현할 값이 없기 때문
- 매핑: front `adminApplicationFormApi.getSummaries()` ↔ back `AdminApplicationFormController.getSummaries()`

---

### 화면: 공고별 지원서 설정 (AdminApplicationFormDetailView)  🟡 초안 (2026-09-02)

- 프론트: `src/views/admin/applicationForm/AdminApplicationFormDetailView.vue` (탭 호스트, 신규)
- 라우트: `/admin/application-forms/:jobPostingId` — 메뉴에 없는 화면이라 route meta `activeMenuPath` 로 `지원서 설정 현황` 을 활성 표시한다
- 탭 UI는 `MenuManageView` 의 `nav.site-tabs` / `button.site-tab` (밑줄형 커스텀 탭, `role="tablist"`) 패턴을 그대로 쓴다
- 탭 구성: **[지원서 양식] [폼 구성]** — `자기소개서 질문` 탭은 이번 범위에서 제외(`TABS` 상수에 자리만 비워둔다)
- 편집 가능 여부는 클라이언트 시계로 계산하지 않고 공고 상세의 `status` + `receptionStatus === 'UPCOMING'` 으로 판단한다(백엔드 가드와 동일 의미)
- 진입 경로: **지원서 설정 현황(`/admin/application-forms`)의 행 클릭**이 기본이며, 공고 상세 헤더의 "지원서 설정" 버튼으로도 들어간다. route meta `activeMenuPath` 는 `/admin/application-forms` 를 가리킨다

#### POST `/admin/job-postings/{jobPostingId}/application-form-config`  🟢 확정(2026-09-02, front-back 반영 완료)

- 설명: 지원서 양식(섹션 사용/필수) 단독 저장. 공고 등록/수정 API에서 분리된 전용 엔드포인트다
- 요청: 기존 `ApplicationFormConfigRequest` 재사용 — `{ useEducation, requireEducation?, useCareer, requireCareer?, useCertificate, requireCertificate?, useLanguage, requireLanguage?, useMilitary, requireMilitary?, useAward, requireAward?, useGapPeriod, requireGapPeriod?, useAttachment }`
- 응답(200): `ApiResponse<ApplicationFormConfigResponse>`
- 편집 가능 조건: **접수 시작 전 && 미마감**. `ApplicationFormLayoutService.validateEditable` 과 동일한 규칙으로 맞춘다(2026-09-02 결정 — 접수 중 섹션 변경은 실무에서 쓰지 않으며, 제출된 지원서와 어긋나는 것을 막는다)
- 오류: 400 접수 시작 후/마감, 404 공고 없음
- 이동 완료: `JobPostingService` 의 `toCreateApplicationFormConfig` / `toUpdateApplicationFormConfig` / `resolveUpdatedRequired` / `validateApplicationFormRequirement` 병합·검증 로직을 `ApplicationFormConfigService` 로 옮겼다
- `require*` 를 생략(null)하면 **기존 설정값을 유지**한다. 단 해당 섹션을 끄면 필수도 함께 해제된다(`resolveUpdatedRequired`)
- 매핑: front `adminApplicationFormApi.saveFormConfig()` ↔ back `AdminApplicationFormConfigController.saveConfig()`
- 컨트롤러는 경로 체계를 맞춰 `AdminApplicationFormConfigController` 로 둔다(요약 목록용 `AdminApplicationFormController` 와 분리)

#### GET·POST `/admin/job-postings/{jobPostingId}/application-form-layout`  🟢 확정(2026-09-02, front-back 반영 완료)

- 백엔드는 `AdminApplicationFormLayoutController` 로 기구현. 프론트는 S3에서 `adminApplicationFormApi` 로 GET·POST·preview 를 모두 연동했다
- 관리자 지원서 상세(`Application.vue`)가 쓰는 기존 `adminApplicationApi.getApplicationFormLayout()` 은 용도가 달라 그대로 둔다
- 응답(GET): `ApiResponse<{ jobPostingId, layoutStored, editable, pages: [{ pageNo, title, description, sortOrder, items: [{ sectionType, sectionName, sortOrder, enabled, required, placed }] }], availableSections: [{ sectionType, sectionName, enabled, required, placed, source }] }>`
- 요청(POST): `{ pages: [{ pageNo, title, description?, sortOrder, items: [{ sectionType, sortOrder }] }] }` — **전체 치환**(delete 후 재삽입). 부분 저장 없음, 낙관적 잠금 없음
- 화면이 반드시 지켜야 할 서버 불변식(`ApplicationFormLayoutValidator`):
  1. 배치 섹션 집합 == 활성 섹션 집합 (미배치 잔여 0이어야 저장 가능)
  2. 페이지 ≥ 1, 페이지당 항목 ≥ 1
  3. 제목 필수(≤100), 설명 ≤500, `pageNo`·페이지 `sortOrder` 중복 불가, 항목 `sortOrder` 는 페이지 내 중복 불가
  4. 섹션 사용/필수 여부는 이 API로 못 바꾼다(양식 탭·질문·첨부요구에서 파생)
- `sectionName` 은 영문("Basic Info", "Questions")이므로 프론트가 `src/common/applicationSection.ts` 의 `SECTION_LABELS`(sectionType → 한글)로 매핑한다. `source` 도 `SECTION_SOURCE_LABELS` 로 "어디서 켜지는 섹션인지" 안내한다
- 매핑: front `adminApplicationFormApi.getLayout()` / `saveLayout()` ↔ back `AdminApplicationFormLayoutController.getLayout()` / `saveLayout()`

#### GET `/admin/job-postings/{jobPostingId}/application-form-layout/preview`  🟢 확정(2026-09-02, front-back 반영 완료)

- 설명: 활성 섹션만 남기고 빈 페이지를 제외한 지원자 관점 미리보기
- 응답: `ApiResponse<{ jobPostingId, jobPostingTitle, pages: [{ pageNo, title, description, sortOrder, items: [{ sectionType, sectionName, required, sortOrder }] }] }>`

---

### 변경: 공고 등록/수정에서 지원서 양식 분리  🟡 초안 (2026-09-02)

- 배경: `applicationFormConfig` 가 `JobPostingCreateRequest` / `JobPostingUpdateRequest` 에 `@NotNull @Valid` 로 묶여 있어, 공고 수정 화면이 낡은 값을 들고 저장하면 설정 화면에서 바꾼 값을 되돌려버린다. 요청에서 제거해 구조적으로 차단한다

#### POST `/admin/job-postings` (등록)  🟢 확정(2026-09-02, 선택 필드로 완화)

- 변경: `applicationFormConfig` 의 `@NotNull` 을 제거해 **생략 가능**하게 했다. 생략하면 `ApplicationFormConfigService.createFrom(null)` 이 기본 설정을 만든다
- 제거가 아니라 완화한 이유: 필드를 삭제하면 `new JobPostingCreateRequest(...)` 호출부 74곳(테스트 56개 파일)이 깨지고, 서버 기본값을 무엇으로 잡느냐에 따라 제출 검증 테스트의 의미까지 바뀐다. 덮어쓰기 위험은 수정(update)에만 있으므로 등록은 완화로 충분하다
- 기본값은 분리 이전 등록 화면의 기본값과 동일하다: **전 섹션 사용, 학력·경력·병역만 필수, 첨부 미사용**. 동작 보존이 목적이다
- null 로 두지 않는 이유: config 가 없으면 `getLayout`/`saveLayout`/`getPreview` 가 `requireFormConfig` 예외, 게시 불가, 지원자 form-page 흐름도 막힌다
- 프론트는 이 필드를 더 이상 보내지 않는다(`AdminJobPostingSaveRequest` 에서 제거)

#### POST `/admin/job-postings/{id}` (수정)  🟢 확정(2026-09-02, 필드 제거)

- 변경: 요청에서 `applicationFormConfig` **제거**. 이 API는 더 이상 양식 설정을 건드리지 않는다
- 이전 클라이언트가 이 필드를 그대로 보내도 400이 되지 않고 **무시**된다(Jackson 미지정 속성 무시)
- `GET /admin/job-postings/{id}` **응답의 `applicationFormConfig` 는 그대로 유지**한다(설정 화면·현황판이 읽어야 함). 요청에서만 제거

#### 딸려온 변경 (S1 구현 완료)

- `JobPostingUpdateRequest` 호출부 6곳 정리(`JobPostingServiceTest`)
- 병합 로직 테스트 2건을 `JobPostingServiceTest` → `ApplicationFormConfigServiceTest` 로 이관
- `JobPostingControllerTest.update_job_posting_with_extended_fields_success`: 수정 요청의 양식이 **무시되는지**를 검증하도록 기대값 변경
- `JobPostingServiceTest.게시_시_저장된_레이아웃이_현재_설정과_불일치하면_실패`: 불일치를 update 가 아니라 `applicationFormConfigService.save` 로 만든다
- 프론트: `AdminJobPostingSaveRequest.applicationFormConfig` 제거, `AdminJobPostingFormView.vue` 의 "지원서 양식 구성" 카드 + `formConfig` ref + `formSections` 상수 + 관련 스타일 삭제
- 남은 공백: 양식 설정을 편집할 화면이 S3(설정 상세 탭) 전까지 없다. 등록 시 기본값으로 생성되며, 기존 공고의 설정은 그대로 유지된다
- 운영 확인: 기존 DB에 `application_form_config` 가 없는 공고가 있으면 현황판에 `MISSING` 으로 뜬다. 백필 여부 확인 필요
