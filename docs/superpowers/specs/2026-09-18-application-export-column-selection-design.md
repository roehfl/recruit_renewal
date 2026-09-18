# 지원현황 엑셀 다운로드 컬럼 선택 설계

- 작성일: 2026-09-18
- 대상: 관리자 지원현황 조회 화면(`ApplicationStatus.vue`)의 **엑셀 다운로드**
- 범위: 백엔드 export 확장(컬럼 카탈로그 enum + 카탈로그 조회 API + `columns` 파라미터) + 프론트 컬럼 선택 모달 1개 + 지원현황 화면 결함 2건 수정(§9). 신규 엔티티·테이블 없음.

## 1. 배경 및 현황

- 현재 엑셀은 `ApplicationExportService.APPLICATIONS_SPEC`에 **고정 11컬럼**(영문 헤더 `applicationId`, `status`=`SUBMITTED` 원문 등)이다.
- 검색 조건은 목록과 공유된다(`AdminApplicationSearchConditionFactory` + `JobApplicationRepository.ADMIN_SEARCH_WHERE`). 이번 작업은 **조건이 아니라 컬럼만** 바꾼다.
- 생성 구조: count 선검증(상한 `recruit.export.max-rows`=50,000) → `ExcelExportWriter`가 1,000행 페이지 단위로 SXSSF temp 파일 생성 → 감사 로그(egress fail-close) → 스트리밍.
- 재사용 가능한 자산:
  - `ApplicationPdfLabels.label(Enum)` — 지원서 enum 14종 한글 라벨(같은 `service` 패키지). **값 표기(국적·보훈·장애·주소·평점·날짜)는 PDF(`ApplicationPdfService`)와 같게 맞춘다.** 휴대폰은 기존 엑셀처럼 저장값 그대로(하이픈 가공 없음).
  - `CommonCodeService.getActiveCodes(group)` — `NATIONALITY`, `DISABILITY_GRADE`, `DISABILITY_TYPE`, `APPLICATION_ROUTE`, `MAJOR_TYPE` 표시명. export 1회당 그룹별 1번만 조회해 재사용.
  - `JobApplicationService.loadAdminSummaryEnrichments` — 지원서 id 배치 조회 패턴과 최종학력 판정 규칙.

## 2. 요구사항

- 엑셀 다운로드 버튼 → **모달**에서 지원자 정보 항목을 체크박스로 전부 제공 → 체크된 항목만 엑셀 컬럼이 된다.
- 1행 = 1지원자를 유지한다. 1:N 정보는 **섹션당 한 셀 요약**(줄바꿈 나열).
- 자기소개서(질문 답변)는 **제외**.
- 민감 항목(주소·비상연락처, 보훈·장애, 현재연봉, 병역)도 선택 목록에 포함. 반출 범위는 감사 로그로 추적.
- 모달을 열 때마다 **기본 컬럼만 체크**된 상태로 시작한다(선택 기억 없음).
- 항목 추가·삭제는 백엔드 enum 한 곳만 고치면 되도록 한다.

## 3. 컬럼 카탈로그

✔ = 기본 체크. 엑셀 컬럼 순서는 **아래 카탈로그 순서로 고정**(체크 순서 무관). ↵ = 셀 줄바꿈(`wrapText`).

### 3.1 지원사항 (`APPLICATION`)

| key | 헤더 | 기본 | 값 |
| --- | --- | --- | --- |
| `APPLICATION_ID` | 수험번호 | ✔ | `JobApplication.id` |
| `JOB_POSTING_TITLE` | 공고명 | | `jobPostingTitleSnapshot` |
| `APPLICATION_TYPE` | 지원구분 | | `JobPosition.applicationType` 라벨(`신입` · `경력` · `신입/경력` — PDF·그리드와 같은 표기) |
| `JOB_POSITION_NAME` | 지원분야 | ✔ | `jobPositionNameSnapshot` |
| `JOB_TITLE` | 직무 | | `JobPosition.jobTitle` |
| `WORK_LOCATION` | 근무지 | ✔ | `workLocationNameSnapshot` |
| `STATUS` | 지원상태 | ✔ | DRAFT 임시저장 / SUBMITTED 제출 완료 / WITHDRAWN 지원 철회 (화면 `statusLabelMap`과 동일 — §9.1) |
| `SUBMITTED_AT` | 최종제출일시 | ✔ | `yyyy-MM-dd HH:mm` |
| `CREATED_AT` | 작성시작일시 | | 〃 |
| `UPDATED_AT` | 최종수정일시 | | 〃 |
| `WITHDRAWN_AT` | 철회일시 | | 〃 |

### 3.2 전형결과 (`STAGE_RESULT`)

| key | 헤더 | 기본 | 값 |
| --- | --- | --- | --- |
| `LATEST_STAGE_RESULT` | 최신 전형결과 | ✔ | 최신 결과(stageOrder 최대, 동률 stage id 최대 — 목록과 동일) `"{stageName} {결과}"`. 결과 없으면 빈칸 |
| `STAGE_RESULTS` | 전형별 결과 ↵ | | stageOrder 순 `"{stageName}: {결과}"` 한 줄씩 |

결과 라벨은 `StageResultStatusLabels.label()`. 발표 여부 무관(관리자 화면, 목록과 동일).

### 3.3 기본정보 (`BASIC_INFO`)

| key | 헤더 | 기본 | 값 |
| --- | --- | --- | --- |
| `NAME` | 이름 | ✔ | 아래 "원천 규칙" |
| `NAME_ENGLISH` | 영문이름 | | `nameEnglish` |
| `NATIONALITY` | 국적 | | `nationalityType` 라벨, 외국인이면 `"외국인 ({NATIONALITY 표시명})"` |
| `BIRTH_DATE` | 생년월일 | ✔ | `yyyy-MM-dd` |
| `AGE` | 나이 | ✔ | 서버 `Clock` 기준 만 나이(목록과 동일) |
| `MOBILE_PHONE` | 휴대폰 | ✔ | 원천 규칙 |
| `EMAIL` | 이메일 | ✔ | 원천 규칙 |
| `EMERGENCY_PHONE` | 비상연락처 | | `emergencyPhone` |
| `ADDRESS` | 주소 | | `"({zipCode}) {addressBasic}, {addressDetail}"` |
| `VETERAN` | 보훈 | | `veteranStatus` 라벨 + 대상이면 `" ({veteranType})"`(저장값 그대로) |
| `DISABILITY` | 장애 | | `disabilityStatus` 라벨 + 대상이면 `" (등급: {DISABILITY_GRADE 표시명} / 유형: {DISABILITY_TYPE 표시명})"` |
| `APPLICATION_ROUTE` | 지원경로 | | `APPLICATION_ROUTE` 표시명 |

**원천 규칙(이름·휴대폰·이메일)**: PDF(`ApplicationPdfService.buildHeader`)와 같다. `ApplicationBasicInfo` 행이 있으면 그 값(파기로 null이어도 fallback 없음), 없으면 이름=`applicantNameSnapshot`, 휴대폰·이메일=`Applicant` 계정 값. 기존 엑셀은 항상 계정 값이었으므로 **값 출처가 바뀐다**(의도된 변경).

### 3.4 병역 (`MILITARY`)

| key | 헤더 | 기본 | 값 |
| --- | --- | --- | --- |
| `MILITARY` | 병역 | | `"{구분} / {군별} {복무형태} / {계급} / {시작} ~ {종료}"` — 군필(`COMPLETED`)이 아니면 구분 라벨만. 빈 값 항목은 생략 |

**면제·미필 사유(`nonServiceReason`)는 넣지 않는다.** 관리자 화면·PDF에서도 `***`로 마스킹되는 민감정보라, 엑셀로만 평문 반출하면 기존 정책과 어긋난다.

### 3.5 최종학력 (`FINAL_EDUCATION`)

최종학력 행 = 최고 `EducationLevel`(선언 순서=서열), 동률이면 id 최대 — 목록·전형결과 그리드와 **같은 규칙**. 학력이 없으면 전부 빈칸.

| key | 헤더 | 기본 | 값 |
| --- | --- | --- | --- |
| `FINAL_EDUCATION_LEVEL` | 최종학력 | ✔ | `educationLevel` 라벨 |
| `FINAL_SCHOOL_NAME` | 최종학교 | ✔ | `schoolName` |
| `FINAL_MAJOR` | 전공 | | `majorName` |
| `FINAL_ADDITIONAL_MAJOR` | 부·복수전공 | | `"{MAJOR_TYPE 표시명}: {additionalMajorName}"` |
| `FINAL_GRADUATION_STATUS` | 졸업구분 | | `graduationStatus` 라벨 |
| `FINAL_ADMISSION_DATE` | 입학년월 | | `yyyy-MM` |
| `FINAL_GRADUATION_DATE` | 졸업년월 | ✔ | `yyyy-MM` |
| `FINAL_GPA` | 평점 | | `"{overallGradePoint} / {overallMaxGradePoint}"` |
| `FINAL_MAJOR_GPA` | 전공평점 | | `"{overallMajorGradePoint} / {overallMajorMaxGradePoint}"` |
| `FINAL_SCHOOL_LOCATION` | 국내/해외 | | `countryCode` 없으면 `국내`, 있으면 `"해외 ({NATIONALITY 표시명})"` |
| `FINAL_SCHOOL_TYPE` | 편입·분교·야간 | | 해당 항목을 `", "`로 연결(`transfer=true`→편입, `campusType=BRANCH`→분교, `dayNightType=NIGHT`→야간). 없으면 빈칸 |

### 3.6 다건 요약 (`SUMMARY`) — 전부 ↵

각 섹션 `sortOrder`(동률 id) 순으로 1건 = 1줄, 필드는 `" / "` 구분, 빈 값 필드는 생략. 날짜·기간 표기는 PDF와 같다(학력·경력·자격·어학 `yyyy-MM-dd`, 수상·공백기간 `yyyy-MM`, 기간은 `"{시작} ~ {종료}"`).

| key | 헤더 | 줄 형식 |
| --- | --- | --- |
| `EDUCATIONS` | 학력 | 학력 / 학교 / 전공 / 기간 / 졸업구분 / 평점 |
| `CAREERS` | 경력 | 회사 / 부서 / 직위 / 고용형태 / 기간(종료일 없으면 `"{시작} ~ 재직중"`) / 퇴사사유 |
| `CURRENT_SALARY` | 현재연봉(만원) | `currentlyEmployed=true`이고 연봉이 있는 경력의 `"{companyName}: {currentSalary:,}"` |
| `CERTIFICATES` | 자격증 | 자격명 / 발급기관 / 취득일 / 점수·등급 (자격증번호는 화면·PDF처럼 마스킹 대상이라 제외) |
| `LANGUAGES` | 어학 | 언어 / 시험 / 점수·등급 / 회화수준 / 응시일 |
| `AWARDS` | 수상 | 수상명 / 기관 / 수상일 |
| `GAP_PERIODS` | 공백기간 | 기간 / 유형 / 사유 |

셀 값이 32,766자(Excel 한도 32,767자 − 수식 escape 문자 1자 여유)를 넘으면 잘라내고 끝에 `…(이하 생략)`을 붙인다. surrogate pair 는 가르지 않는다.

## 4. API 계약

### 4.1 `GET /admin/applications/export/columns` (신규)

- 요청: 없음
- 응답(200): `ApiResponse<List<ApplicationExportColumnGroupResponse>>`
  - `[{ group: "기본정보", columns: [{ key: "BIRTH_DATE", label: "생년월일", defaultSelected: true }] }]`
  - 그룹·컬럼 순서 = 카탈로그 순서
- 권한: 기존 `/api/admin/**` 매처(ADMIN, RECRUIT_ADMIN)

### 4.2 `GET /admin/applications/export`, `GET /admin/job-postings/{jobPostingId}/applications/export` (확장)

- 기존 요청(`AdminApplicationSearchRequest` 전체) 유지 + `columns` 추가(query, 콤마 연결: `columns=APPLICATION_ID,NAME`)
  - 미지정/빈값 → 기본 컬럼(`defaultSelected=true`). 기존 호출 하위호환
  - 정의 밖 key → 400 (`"엑셀 컬럼 값이 올바르지 않습니다. columns=..."`)
  - 중복 제거, 출력 순서는 카탈로그 순서
  - `columns`는 `AdminApplicationSearchRequest`에 넣지 않고 컨트롤러 `@RequestParam`으로 받는다(목록 조회 요청 오염 방지)
- 응답: 기존과 동일(xlsx 스트리밍, 파일명 불변)
- 감사: 기존 filters(`jobPostingId`/`jobPositionId`/canonical `status`) + `columns`(적용된 key 목록, 카탈로그 순서)
- 오류: 기존 + 400(columns 값 오류)

## 5. 백엔드 설계 (`com.shinyoung.recruit.service` 외 표기분)

| 단위 | 책임 |
| --- | --- |
| `ApplicationExportColumn` (enum) | 카탈로그. 상수마다 `group`, `label`, `defaultSelected`, `section`(필요 데이터), `wrapText`. `parse(List<String>)`: 빈값→기본, 미정의→예외, 중복 제거·카탈로그 순 정렬 |
| `ApplicationExportSection` (enum) | 페이지 배치 로딩 단위: `BASIC_INFO`, `MILITARY`, `EDUCATION`, `CAREER`, `CERTIFICATE`, `LANGUAGE`, `AWARD`, `GAP_PERIOD`, `STAGE_RESULT`. 지원사항 컬럼은 base 조회로 충족(섹션 없음) |
| `ApplicationExportRowAssembler` | 페이지(최대 1,000건) 지원서 id로 **선택 컬럼이 요구하는 섹션만** `findByJobApplicationIdIn` 배치 조회 → 지원서별 `Map<ApplicationExportColumn, String>` 조립. 한글 라벨·공통코드 표시명·날짜 포맷·셀 길이 제한 담당. entity는 이 안에서만 다루고 writer에는 문자열만 넘긴다. 페이지 조립 후 `EntityManager.clear()`로 영속성 컨텍스트를 비운다(5만 행 export에서 섹션 entity 누적 방지) |
| `CommonCodeNames` | export 1회용 공통코드 표시명 캐시(그룹별 1회 조회). 미등록 코드는 코드값 그대로(PDF와 동일) |
| `ApplicationExportService` | 파싱된 컬럼 목록을 받아 기존 count/상한 검증 → 선택 컬럼으로 `ExcelExportSpec` 동적 생성 → writer 호출. 컬럼 파싱은 컨트롤러가 `ApplicationExportColumn.parse()`로 하고 같은 목록을 감사 로그에도 넘긴다 |
| `ApplicationExportRow` (dto) | base projection 확장: `applicationType`, `jobTitle`, `workLocationName`, `jobPostingTitle`(snapshot) 등 지원사항 컬럼 원천 |
| `ExportColumn` / `ExcelExportWriter` | `wrapText` 플래그 추가 → 해당 컬럼 데이터 셀에 줄바꿈 스타일. 기존 `readOnly` 플래그와 같은 방식. 기존 호출부 시그니처 유지 |
| `controller.AdminExportController` | `columns` 파라미터 수신·전달, `GET /admin/applications/export/columns` 추가 |
| `ExportAuditLogger.logApplicationsExport` | `columns` 인자 추가 → filters에 기록 |

- 최종학력·최신 전형결과 비교 규칙은 기존 관례대로 인라인 + "목록과 같은 규칙" 주석. 기존 두 곳(`JobApplicationService`, `AdminStageResultEnricher`)은 리팩터링하지 않는다.
- 섹션 repository에 `findByJobApplicationIdIn`이 없으면 추가한다.
- 수식 주입 방지(`escapeFormulaPrefix`)·temp 파일 정리·fail-close 감사는 기존 동작 그대로.

## 6. 프론트 설계

- `src/api/admin/adminApplicationApi.ts`
  - `getApplicationExportColumns()` 추가
  - `downloadApplicationsExcel(jobPostingId, searchRequest, columns: string[])` — `params: { ...searchRequest, columns: columns.join(',') }` (axios 기본 배열 직렬화 `columns[]=` 회피)
- `src/types/admin/application.ts` — `ApplicationExportColumnGroup`, `ApplicationExportColumnOption` 타입
- 신규 `src/views/admin/application/ApplicationExcelColumnModal.vue`
  - `a-modal` 제목 "엑셀 다운로드 항목 선택"
  - 그룹별 섹션: 그룹 체크박스(전체/일부 indeterminate/없음) + 항목 체크박스 목록
  - 상단 버튼: 전체 선택 · 전체 해제 · 기본값
  - 열 때마다 `defaultSelected` 항목으로 초기화
  - 카탈로그는 첫 오픈 시 1회 조회 후 컴포넌트 내 캐시. 조회 실패 시 에러 메시지 + 다운로드 비활성
  - 선택 0개면 다운로드 버튼 비활성. 다운로드 중 로딩 표시, 성공 시 닫힘
- `ApplicationStatus.vue` — "엑셀 다운로드" 버튼은 모달 오픈으로 변경. 모달 확인 시 기존 `downloadExcel` 흐름(검색 조건 그대로)에 `columns` 추가

## 7. 오류 처리

| 상황 | 처리 |
| --- | --- |
| 카탈로그 조회 실패 | `message.error` + 모달 다운로드 비활성 |
| 선택 0개 | 다운로드 버튼 비활성(서버 호출 없음) |
| 정의 밖 key(400) | 기존 `getBlobErrorMessage`로 서버 메시지 표시 |
| 행 수 상한 초과 / 공고 미존재 | 기존 처리 그대로 |

## 8. 테스트

- 백엔드(수정 패키지만)
  - `ApplicationExportColumn.parse`: 빈값→기본 14개, 미정의 key→예외, 중복 제거, 카탈로그 순 정렬
  - `ApplicationExportServiceTest`: 선택 컬럼만 헤더·값 생성, 미선택 섹션 repository 미호출, 1:N 요약 포맷, 최종학력 판정, 연락처 원천 규칙(basicInfo 유/무), 셀 길이 제한
  - `AdminExportControllerTest`: 카탈로그 응답 구조, `columns` 전달, 400, 감사 filters에 `columns`
  - `ExcelExportWriterTest`: `wrapText` 컬럼 셀 스타일
- 프론트: `npm run type-check`
- 수동 확인: 실제 Excel에서 줄바꿈 셀 행 높이 표시(SXSSF는 행 높이를 계산하지 않으므로 Excel 자동 맞춤 동작 확인)

## 9. 함께 수정하는 결함 (지원현황 화면)

### 9.1 지원상태 라벨 오류

- 현상: `ApplicationStatus.vue` `statusLabelMap.WITHDRAWN = '작성 완료'`. 철회 상태에 맞지 않는 라벨.
- 조치: `'지원 철회'`로 수정(전형결과 `stageResultStatusMap.WITHDRAWN`과 같은 표기).
- 참고: 이 맵을 쓰는 템플릿 분기(`column.key === 'stageType'`)는 해당 key 컬럼이 없어 현재 렌더되지 않는다(잠재 결함). 분기 정리는 범위 밖 — 라벨만 수정.

### 9.2 "졸업년월" 컬럼이 철회일시를 표시

- 현상: 그리드 "졸업년월" 컬럼이 `dataIndex: 'withdrawnAt'`에 묶여 `YYYY-MM-DD HH:mm` 철회일시를 보여준다. 목록 응답에 졸업일 필드가 없다.
- 조치(백엔드): `AdminApplicationSummaryResponse`(+`Enrichment`)에 `finalGraduationDate`(LocalDate) 추가. 값 = 목록이 이미 고르는 **최종학력 행**의 `ApplicationEducation.graduationDate`(추가 조회 없음). 없으면 null. 하위호환 필드 추가.
  - **응답 전용 파생 필드**다(`finalEducationLevel`/`finalSchoolName`과 같은 방식). 엔티티·테이블 변경 없음.
- 조치(프론트): 컬럼을 `dataIndex/key: 'finalGraduationDate'`로 바꾸고 `YYYY-MM` 표시(PDF 년월 표기와 동일). 이 변경으로 쓰이지 않게 되는 `withdrawnAt` bodyCell 분기는 `finalGraduationDate` 분기로 교체. 타입 `AdminApplicationSummaryResponse`에 필드 추가.
- 테스트: `JobApplicationServiceTest` 목록 파생 필드 검증에 `finalGraduationDate` 단언 추가(최종학력 행 기준, 학력 없으면 null).

## 10. 범위 밖

- 자기소개서(질문 답변)·첨부파일 목록
- 선택 기억(브라우저/서버 프리셋)
- 다른 export(전형결과·면접·평가) 컬럼 선택
- 별도 메뉴 화면
- 1:N 번호별 컬럼 펼침 / 섹션별 별도 시트
