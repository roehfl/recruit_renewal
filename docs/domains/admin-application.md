# 관리자 지원현황·상세·엑셀·PDF (`admin-application`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [application](application.md)(조회 구현) · [application-sections](application-sections.md)(섹션 엔티티) · [stage-result](stage-result.md) · [attachment](attachment.md) · [interview](interview.md)(면접 평가 조회) · [statistics](statistics.md) · [privacy-audit](privacy-audit.md) · [application-form](application-form.md) · [job-posting](job-posting.md) · [master-data](master-data.md) · [auth-account](auth-account.md)

## 요약

- 지원현황(`/admin/applications`): 공고 선택 → 검색 → 그리드 → 지원서 상세(새 탭)·PDF. 엑셀은 같은 검색 조건 + 선택 컬럼(44개, 기본 14). PDF는 1건 1파일, 최대 20건 zip.
- export 컨트롤러는 전형결과·면접·평가 목록 export도 제공한다. 전부 읽기 전용, 반출은 `ActivityLog` 감사 성공 후에만(fail-close).
- 목록·상세 조회 구현은 [application](application.md) 소유 파일에 있다: `{BE}/service/JobApplicationService.java`(`getApplicationsForAdmin`·`getApplicationForAdmin`·`loadAdminSummaryEnrichments`), `{BE}/domain/repository/JobApplicationRepository.java`(`ADMIN_SEARCH_WHERE`와 쿼리 3개). 고칠 때 이 카드 규칙을 따른다.
- 공용 export 인프라(`ExcelExportWriter`·`ExcelExportService`·`ExcelExportResponseFactory`·`ExportAuditLogger`)도 이 카드 소유. 소비처: `{BE}/controller/StageResultUploadController.java`, `{BE}/controller/InterviewScheduleController.java`.

## 용어

| 용어 | 뜻 |
|---|---|
| Export | 조회 데이터를 xlsx/PDF로 내려받기(첨부 다운로드와 구분) |
| list-parity | 목록 export는 대응 목록 조회를 재사용(필터·정렬 동일), page/size 무시 |
| 반출 fail-close | 감사 commit이 성공해야 파일 반환. 기록 후 전송 실패(over-record)는 허용 |
| row cap | `recruit.export.max-rows`(기본 50,000). 초과 시 생성 전 400. 조용한 잘라내기 금지 |
| 컬럼 카탈로그 | `ApplicationExportColumn` enum — 모달·헤더·열 순서의 단일 출처 |
| 최종학력 행 | 최고 `EducationLevel`(선언 순서 = 서열), 동률이면 id 큰 행 |
| 최신 전형결과 | `stageOrder` 최대(동률은 stage id 큰) 전형의 결과. 발표 여부 무관 |
| 수험번호 | `applicationId` |
| 원천 규칙 | 이름·휴대폰·이메일: `ApplicationBasicInfo` 행이 있으면 그 값(파기로 null이어도 fallback 없음), 없으면 `applicantNameSnapshot`·`Applicant` 계정 값. PDF·엑셀 공통 |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/AdminApplicationController.java` | 목록 2종·상세 |
| controller | `{BE}/controller/AdminApplicationSectionController.java` | 섹션 조회 11종 |
| controller | `{BE}/controller/AdminExportController.java` | 엑셀 5종·카탈로그, 감사 후 응답 |
| controller | `{BE}/controller/ApplicationPdfController.java` | PDF 단건·일괄, 감사 후 응답 |
| controller | `{BE}/controller/ExcelExportResponseFactory.java` | xlsx 스트리밍·보안 헤더·전송 후 삭제(공용) |
| service | `{BE}/service/AdminApplicationSearchConditionFactory.java` | 검색 조건 정규화·파싱(목록·엑셀 공유) |
| service | `{BE}/service/AdminApplicationSectionService.java` | 섹션 조회·마스킹(PDF 재사용) |
| service | `{BE}/service/AdminStageResultEnricher.java` | 전형결과 그리드 파생값(stage-result가 호출) |
| service | `{BE}/service/ApplicationExportService.java` | 지원현황 엑셀: 공고 확인 → row cap → writer |
| service | `{BE}/service/ApplicationExportColumn.java`, `{BE}/service/ApplicationExportSection.java` | 카탈로그, 섹션 배치 조회 단위 |
| service | `{BE}/service/ApplicationExportRowAssembler.java`, `{BE}/service/CommonCodeNames.java` | 셀 조립·표기·길이 제한, 공통코드명 캐시 |
| service | `{BE}/service/AdminDatasetExportService.java` | 전형결과·면접·평가 export |
| service | `{BE}/service/ExcelExportService.java`, `{BE}/service/ExcelExportWriter.java`, `{BE}/service/ExcelExportSpec.java`, `{BE}/service/ExportColumn.java`, `{BE}/service/ExportRowSource.java`, `{BE}/service/ExcelSheetDecorator.java`, `{BE}/service/ExcelExportFile.java` | 공용: row cap, SXSSF·formula escape, 시트 정의, temp 핸들 |
| service | `{BE}/service/ExportAuditLogger.java`, `{BE}/service/ExportAuditContext.java`, `{BE}/service/ExportMetadata.java` | export 감사 |
| service | `{BE}/service/ApplicationPdfService.java`, `{BE}/service/ApplicationPdfLabels.java` | PDF 표시 모델, enum 한글 라벨(엑셀 공용) |
| service | `{BE}/service/ApplicationPdfRenderer.java`, `{BE}/service/ApplicationPdfDocument.java`, `{BE}/service/ApplicationPhotoLoader.java` | 렌더, 결과, 증명사진 data URI |
| service | `{BE}/service/ApplicationPdfBulkService.java`, `{BE}/service/ApplicationPdfZipFile.java` | 일괄 zip |
| service | `{BE}/service/PdfAuditLogger.java`, `{BE}/service/PdfMetadata.java` | PDF 감사 |
| dto | `{BE}/dto/request/AdminApplicationSearchRequest.java`, `{BE}/dto/condition/AdminApplicationSearchCondition.java` | 검색 조건 17종, 파싱 결과 |
| dto | `{BE}/dto/request/ApplicationPdfBulkRequest.java` | `{ applicationIds }` |
| dto | `{BE}/dto/response/AdminApplicationSummaryResponse.java`, `{BE}/dto/response/AdminApplicationDetailResponse.java` | 목록 행(+`Enrichment`), 상세 |
| dto | `{BE}/dto/response/AdminBasicInfoResponse.java`, `{BE}/dto/response/AdminMilitaryResponse.java`, `{BE}/dto/response/AdminEducationResponse.java`, `{BE}/dto/response/AdminSemesterGradeResponse.java`, `{BE}/dto/response/AdminCareerResponse.java`, `{BE}/dto/response/AdminCareerItemResponse.java`, `{BE}/dto/response/AdminCertificateResponse.java`, `{BE}/dto/response/AdminLanguageResponse.java`, `{BE}/dto/response/AdminAwardResponse.java`, `{BE}/dto/response/AdminGapPeriodResponse.java`, `{BE}/dto/response/AdminAttachmentResponse.java`, `{BE}/dto/response/AdminApplicationAnswerResponse.java`, `{BE}/dto/response/AdminApplicationStageResultResponse.java` | 섹션 응답 |
| dto | `{BE}/dto/response/ApplicationExportColumnGroupResponse.java`, `{BE}/dto/response/ApplicationExportRow.java`, `{BE}/dto/response/InterviewEvaluationExportRow.java`, `{BE}/dto/response/ApplicationPdfView.java` | 카탈로그, 엑셀 projection, 평가 행, PDF 모델 |
| config | `{BE}/config/ExportProperties.java`, `{BE}/config/PdfProperties.java` | `recruit.export.*`, `recruit.pdf.*` |
| config | `{BR}/templates/application-pdf.html`, `{BR}/fonts/NanumGothic-Regular.ttf` | 리소스: PDF 템플릿, 기본 폰트(SIL OFL) |
| exception | `{BE}/exception/ExportRowLimitExceededException.java`, `{BE}/exception/PdfBulkLimitExceededException.java` | 상한 초과 400 |
| exception | `{BE}/exception/ExportGenerationException.java`, `{BE}/exception/PdfGenerationException.java` | 생성 실패 500 |
| test | `{BT}/controller/AdminApplicationControllerTest.java`, `{BT}/controller/AdminApplicationSectionControllerTest.java`, `{BT}/service/AdminApplicationSectionServiceTest.java` | 목록·섹션 |
| test | `{BT}/controller/AdminExportControllerTest.java`, `{BT}/controller/AdminExportRowCapTest.java`, `{BT}/controller/AdminDatasetExportControllerTest.java` | 엑셀 API |
| test | `{BT}/service/ApplicationExportColumnTest.java`, `{BT}/service/ApplicationExportRowAssemblerTest.java`, `{BT}/service/ApplicationExportRowAssemblerSectionLoadingTest.java`, `{BT}/service/ApplicationExportServiceTest.java`, `{BT}/service/CommonCodeNamesTest.java` | 컬럼·셀 조립 |
| test | `{BT}/service/ExcelExportServiceTest.java`, `{BT}/service/ExcelExportWriterTest.java`, `{BT}/service/ExportAuditLoggerTest.java` | 공용 인프라·감사 |
| test | `{BT}/controller/ApplicationPdfControllerTest.java`, `{BT}/controller/ApplicationPdfBulkControllerTest.java`, `{BT}/controller/ApplicationPdfSecurityHardeningTest.java` | PDF API·템플릿 보안 |
| test | `{BT}/service/ApplicationPdfServiceTest.java`, `{BT}/service/ApplicationPdfBulkServiceTest.java`, `{BT}/service/ApplicationPdfPhotoEmbedTest.java`, `{BT}/service/ApplicationPhotoLoaderTest.java` | PDF 서비스 |

### 프론트

| 구분 | 파일 | 역할 |
|---|---|---|
| route | `{FE}/routes/adminRoutes.ts` | (공유) `AdminApplicationStatus`, `AdminApplication`(레이아웃 밖, 새 탭) |
| view | `{FE}/views/admin/application/ApplicationStatus.vue` | 지원현황: 검색폼·그리드(20건)·엑셀 모달·선택 PDF zip·경력기술서 |
| view | `{FE}/views/admin/application/Application.vue` | 상세: layout `enabled` 섹션만 조회, 사진·공통코드 표시, PDF |
| component | `{FE}/views/admin/application/ApplicationExcelColumnModal.vue` | 엑셀 컬럼 선택 모달 |
| api | `{FE}/api/admin/adminApplicationApi.ts` | 이 카드 API + 레이아웃·첨부 다운로드 호출 |
| api | `{FE}/common/fileDownload.ts` | (공유) `saveBlobResponse`, `getBlobErrorMessage`(blob 오류 본문) |
| types | `{FE}/types/admin/application.ts` | (공유) 목록·검색·상세·카탈로그 타입 |
| types | `{FE}/types/admin/applicationSections.ts` | 섹션 응답 타입, `getLabel` |

## API 계약

JSON 응답은 `ApiResponse<T>`, 파일 응답은 래핑 없음(오류만 JSON). 권한 `관리자` = `/api/admin/**`(`ROLE_ADMIN`·`ROLE_RECRUIT_ADMIN`). `관리자+임직원` = 추가로 `CurrentEmployeeService.getCurrentEmployeeActor`가 임직원 세션 확인(비로그인 401, 지원자 403).

| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |
|---|---|---|---|---|---|
| 🟢 | GET | /admin/applications | `jobPostingId?` + 검색 조건 + `page`·`size` | `PageResponse<AdminApplicationSummaryResponse>` | 관리자 |
| 🟢 | GET | /admin/job-postings/{jobPostingId}/applications | 검색 조건 + `page`·`size` | 위와 같음 | 관리자 |
| 🟢 | GET | /admin/applications/{applicationId} | path | `AdminApplicationDetailResponse` | 관리자 |
| 🟢 | GET | /admin/applications/{applicationId}/basic-info | path | 기본정보 또는 `null` | 관리자 |
| 🟢 | GET | /admin/applications/{applicationId}/military | path | 병역(사유 마스킹) 또는 `null` | 관리자 |
| 🟢 | GET | /admin/applications/{applicationId}/educations | path | 학력[] + `semesterGrades[]` | 관리자 |
| 🟢 | GET | /admin/applications/{applicationId}/careers | path | `{ careers: [...] }` | 관리자 |
| 🟢 | GET | /admin/applications/{applicationId}/certificates | path | 자격증[](번호 마스킹) | 관리자 |
| 🟢 | GET | /admin/applications/{applicationId}/languages | path | 어학[] | 관리자 |
| 🟢 | GET | /admin/applications/{applicationId}/awards | path | 수상[] | 관리자 |
| 🟢 | GET | /admin/applications/{applicationId}/gap-periods | path | 공백기간[] | 관리자 |
| 🟢 | GET | /admin/applications/{applicationId}/attachments | path | 첨부 메타[] | 관리자 |
| 🟢 | GET | /admin/applications/{applicationId}/answers | path | 공고 질문 전체 + 답변 | 관리자 |
| 🟢 | GET | /admin/applications/{applicationId}/stage-results | path | 공고 전형 전체 + 결과 | 관리자 |
| 🟢 | GET | /admin/applications/export | `jobPostingId?` + 검색 조건 + `columns?` | xlsx | 관리자+임직원 |
| 🟢 | GET | /admin/job-postings/{jobPostingId}/applications/export | 검색 조건 + `columns?` | xlsx | 관리자+임직원 |
| 🟢 | GET | /admin/applications/export/columns | 없음 | `[{ group, columns: [{ key, label, defaultSelected }] }]` | 관리자 |
| 🟢 | GET | /admin/stages/{stageId}/results/export | path | xlsx | 관리자+임직원 |
| 🟢 | GET | /admin/job-postings/{jobPostingId}/interviews/export | `stageId?`·`status?`·`from?`·`to?`(ISO date-time) | xlsx | 관리자+임직원 |
| 🟢 | GET | /admin/stages/{stageId}/interview-evaluations/export | path | xlsx | 관리자+임직원 |
| 🟢 | GET | /admin/applications/{applicationId}/pdf | path | `application/pdf` | 관리자+임직원 |
| 🟢 | POST | /admin/applications/pdf/bulk | `{ applicationIds: number[] }` | `application/zip` | 관리자+임직원 |

### 엔드포인트 상세

**목록** — 🟢(2026-08-14 검색 확장 · 2026-08-31 근무지 의미 변경·FE 반영 · 2026-09-18 `finalGraduationDate`)
- 두 경로는 같은 쿼리. 공고 없으면 404. page ≥ 0, size 1~100(기본 20), 정렬 `createdAt desc, id desc`.
- 검색 조건(모두 optional, 빈 값 무시, AND). enum은 trim·대소문자 무시, 정의 밖이면 400.
  - `jobPositionId` · `status` · `applicationType`(모집분야 값) · `workLocation`(`workLocationCode` 완전일치)
  - `name`(`applicantNameSnapshot` 부분일치) · `phoneNumber`(계정 번호의 `-`·공백을 빼고 숫자 부분일치)
  - `birthDateFrom`·`birthDateTo`(ISO date). from > to면 400 `"생년월일 검색 범위가 올바르지 않습니다."`
  - `finalEducationLevel`(최종학력 행 레벨) · `schoolName`(학력 행 아무거나 부분일치) · `graduationStatus`·`finalSchoolCondition`(DOMESTIC=`countryCode` 없음/OVERSEAS/TRANSFER/BRANCH/NIGHT) — 최종학력 행 기준
  - `certificateName` 부분일치 · `languageName`·`languageLevel`(`conversationalAbility`) 완전일치
  - `stageType`+`stageResultStatus`: 둘 다 만족하는 결과 행이 하나라도 있으면 매칭(최신 결과만 보지 않음)
- 응답 파생값(없으면 null, 페이지 배치 조회 4회): `jobTitle`, `workLocation`(선택 근무지명), `birthDate`·`age`(서버 Clock 만 나이), 최종학력 행의 `finalEducationLevel`·`finalSchoolName`·`finalGraduationDate`, 최신 결과의 `stageType`·`stageResultStatus`, `careerDescriptionDownloadUrl`(STORED·미삭제 경력기술서 중 id 최대의 다운로드 경로).
- 제외 확정: 성별, 채용구분 연도, `jobGroup`(2026-08-31 제거).
- FE는 공고 경로만 쓰고 학력·자격·어학 조건은 화면에 없다(API 전용). `/admin/applications`는 [stage-result](stage-result.md) 화면이 제출 건수용으로 호출(`{FE}/api/admin/adminStageApi.ts`).

**상세·섹션** — 🟢(상세 2026-08-31)
- 상세: 지원서 스냅샷 + `workLocationNameSnapshot`(실제 고른 근무지). 없으면 404 `"지원서를 찾을 수 없습니다. id=..."`.
- 섹션 11종: 지원서 없으면 404, 상태 무관, `sortOrder, id` 순. `basic-info`·`military`는 행이 없으면 `data: null`. 필드는 `Admin*Response` DTO가 단일 출처.
- 제외 필드: 사유·자격증번호 원문(마스킹 값만), `languages.registrationNumber`, `basic-info.applicationRouteCode`, `careers.careerType`.
- `attachments`: 메타만, 삭제·파기 상태(`HIDDEN_FROM_LISTING`) 제외. `answers`: 미답변은 답변 필드 null. `stage-results`: `decidedBy` 비노출, **FE 미사용**.

**지원현황 엑셀** — 🟢(2026-09-09 필터 확장 · 2026-09-18 컬럼 선택)
- 요청: 목록 검색 조건 전체 + `columns`(콤마 연결, 검색 DTO가 아닌 별도 `@RequestParam`). page/size 없음.
- `columns`: 대소문자·공백 무시, 중복 제거, 출력 순서 = 카탈로그 선언 순. 없으면 기본 14. 정의 밖이면 400 `"엑셀 컬럼 값이 올바르지 않습니다. columns=<key>"`.
- 기본 14: APPLICATION_ID, JOB_POSITION_NAME, WORK_LOCATION, STATUS, SUBMITTED_AT, LATEST_STAGE_RESULT, NAME, BIRTH_DATE, AGE, MOBILE_PHONE, EMAIL, FINAL_EDUCATION_LEVEL, FINAL_SCHOOL_NAME, FINAL_GRADUATION_DATE.
- 파일 `applications-export-job-posting-{id}.xlsx`(공고 없으면 `applications-export.xlsx`), 헤더 = 한글 라벨.
- 오류: 400(enum·columns·생년월일 범위·row cap `"EXPORT_ROW_LIMIT_EXCEEDED: …"`), 404(공고), 500(`"Export 파일 생성에 실패했습니다."`).
- FE는 공고 경로만 쓴다(`/admin/applications/export`는 **FE 미사용**). axios 기본 배열 직렬화(`columns[]=`)는 서버가 못 받아 `join(',')`.
- 2026-09-18 파일 형식 비호환 변경(헤더 한글, 상태 라벨, 일시 포맷). 요청은 하위호환.

**카탈로그** — 🟢(2026-09-18). `group`은 한글 라벨(안정 키 아님), 순서 = 선언 순.

**PDF 단건** — 🟢 확정(2026-09-19 코드 기준. 2026-09-09 표시 내용 개편, 경로·형태 불변, FE `Application.vue`가 호출)
- 파일명 `{applicationId}_{이름}.pdf`(+`filename*`, 이름이 비면 `{applicationId}.pdf`).
- 구성: 지원사항 → 기본정보(사진) → 병역 → 학력 → 학기별 성적(공채만) → 경력 → 자격 → 어학 → 수상 → 공백기간 → 자기소개서. 첨부 목록·전형결과 제외. layout 설정과 무관하게 전 섹션.
- 사진: `ETC`+`BASIC_INFO` 첨부 중 목록 마지막, JPEG/PNG·5MB 이하, 실패 시 생략.
- 오류: 404, 500(`"지원서 PDF 생성에 실패했습니다."`).

**PDF 일괄** — 🟢(2026-09-09. FE 연동 완료)
- 빈 배열 400 `"다운로드할 지원서를 선택하세요."`. 중복 제거 후 최대 20건(`recruit.pdf.bulk-max-count`), 초과 400 `"한 번에 최대 20건까지 다운로드할 수 있습니다. (요청 N건)"`.
- `applications-yyyyMMddHHmmss.zip`, 내부 이름이 겹치면 `(2)` 접미사. id 하나라도 없으면 404로 전체 실패. POST는 id 배열 때문(의도). FE도 20건을 먼저 검사.

**다른 목록 export** — 🟢. 헤더 영문 필드명, enum 원문, 일시 ISO. 없는 단계·공고 404.
- 전형결과(2026-09-04 전형결과 화면 계약): `StageResultService.getResults` 12열. 업로드 템플릿과 열이 달라 업로드 불가. FE는 [stage-result](stage-result.md) 화면.
- 면접: `InterviewService.getAdminInterviews` 14열(2026-09-18 종료시각 삭제, `arrivalDateTime` 추가). **FE 미사용**.
- 평가: 평가 1건 = 1행 10열. **FE 미사용**.

## 규칙·불변식

**검색·판정**
- 목록·엑셀은 같은 조건 객체·WHERE 상수를 쓴다. 조건 변경 시 factory·condition·`ADMIN_SEARCH_WHERE`·쿼리 3개를 함께 고친다(과거: 엑셀이 필터 3개만 받아 이름 검색 후 전체 반출). ({BE}/service/AdminApplicationSearchConditionFactory.java — create)
- enum 파싱 실패·생년월일 역전 → `InvalidJobApplicationException` 400. ({BE}/service/AdminApplicationSearchConditionFactory.java — parseSearchEnum)
- `EducationLevel` 선언 순서 = 서열. 쿼리 CASE rank(0~4)와 `finalEducationRank()`(ordinal)가 일치해야 한다. ({BE}/dto/condition/AdminApplicationSearchCondition.java — finalEducationRank)
- 최종학력 행·최신 결과 판정은 3곳 복제: `JobApplicationService.loadAdminSummaryEnrichments`, `AdminStageResultEnricher.loadFinalEducations`, `ApplicationExportRowAssembler`. 함께 바꾼다. ({BE}/service/ApplicationExportRowAssembler.java — source)
- Enricher 입력은 한 단계의 결과만(직전 단계를 첫 행 기준 판정). ({BE}/service/AdminStageResultEnricher.java — toResponses)

**마스킹**
- 면제·미필 사유는 `***`, 자격증번호는 앞 2~3자 + `***`. 원문은 관리자 응답·PDF·엑셀 어디에도 없다. `ci`·`ciHash`·`password`도 금지. ({BE}/service/AdminApplicationSectionService.java — maskSensitiveText/maskCertificateNumber)

**엑셀 공통**
- 읽기 전용 tx. row cap은 생성 전 count(초과면 writer 미호출). writer 실패만 `ExportGenerationException` 500. ({BE}/service/ApplicationExportService.java — exportApplications, {BE}/service/ExcelExportService.java — generate)
- SXSSF(window 100) + 1,000행 페이지. writer엔 entity가 아니라 문자열·projection만. ({BE}/service/ExcelExportWriter.java — writeToTempFile)
- 지원현황 엑셀은 페이지마다 `EntityManager.clear()` — 호출 전에 로드한 entity를 뒤에서 쓰지 않는다(OSIV). ({BE}/service/ApplicationExportRowAssembler.java — assemble)
- 모든 셀은 string. `=` `+` `-` `@` tab CR LF로 시작하면 `'` 접두(formula injection). 업로드 템플릿만 예외(`escapeFormulaPrefix=false`). ({BE}/service/ExcelExportWriter.java — sanitize)
- temp 파일은 전송 후 스트림 finally에서 삭제, 감사 실패 시 컨트롤러가 즉시 삭제. ({BE}/controller/ExcelExportResponseFactory.java — toResponse, {BE}/controller/AdminExportController.java — deleteQuietly)
- 파일 응답 헤더: attachment + `filename*=UTF-8''`, `nosniff`, `no-store`, `no-cache`. JS가 파일명을 읽으려면 CORS에 `Content-Disposition` 노출 필요(`{BE}/config/SecurityConfig.java`, `3ff110b`). FE는 `saveBlobResponse`·`getBlobErrorMessage`로 받는다.

**엑셀 컬럼**
- 컬럼 정의는 `ApplicationExportColumn` 한 곳. FE 무변경. 새 상수는 assembler `value()` switch가 컴파일 오류로 강제. ({BE}/service/ApplicationExportColumn.java — parse)
- 선택 컬럼이 요구하는 섹션만 배치 조회. ({BE}/service/ApplicationExportRowAssembler.java — load)
- 1:N 요약: 1건 1줄, 필드 `" / "`, 빈 필드 생략, sortOrder 순. 셀 32,766자 초과 시 `…(이하 생략)`(surrogate 분리 금지). ({BE}/service/ApplicationExportRowAssembler.java — lines/truncate)
- 반출 제외: 자기소개서, 면제·미필 사유, 자격증번호. 병역은 군필만 상세. ({BE}/service/ApplicationExportRowAssembler.java — military)
- 이름·연락처는 원천 규칙 — 검색 조건 `name`(snapshot)·`phoneNumber`(계정)과 출처가 다를 수 있다. 휴대폰은 저장값 그대로. ({BE}/service/ApplicationExportRowAssembler.java — value)
- 라벨: 지원상태는 화면 `statusLabelMap`과 동일, 전형결과 `StageResultStatusLabels`, enum `ApplicationPdfLabels`, 공통코드 `CommonCodeNames`(미등록 코드는 코드값). ({BE}/service/CommonCodeNames.java — name)

**날짜·졸업년월 표기**

| 위치 | 입학·졸업 |
|---|---|
| 목록 그리드 "졸업년월"(`finalGraduationDate`) | `YYYY-MM` |
| 엑셀 FINAL_ADMISSION_DATE·FINAL_GRADUATION_DATE | `yyyy-MM` |
| 엑셀 학력·경력·자격·어학 요약 | `yyyy-MM-dd`(수상·공백기간 `yyyy-MM`) |
| PDF·상세 화면 학력표(헤더 "입학년월/졸업년월") | `yyyy-MM-dd` 원값 |

- 일시는 그리드·엑셀 모두 `yyyy-MM-dd HH:mm`. ({FE}/views/admin/application/ApplicationStatus.vue — columns)

**감사**
- 파일 생성 → `ActivityLog` `recordRequiresNew` → 성공해야 응답. 실패하면 파일 미반출. ({BE}/service/ExportAuditLogger.java — logExport, {BE}/service/PdfAuditLogger.java — logApplicationPdf)
- 일괄 PDF: zip 완성 → 건별 `APPLICATION_PDF` 감사(같은 `requestId`) → 스트리밍(시작 후엔 200을 되돌릴 수 없음). ({BE}/controller/ApplicationPdfController.java — applicationPdfBulk)
- datasetType → `AuditActionType` 고정 매핑(APPLICATIONS·STAGE_RESULTS·INTERVIEWS·INTERVIEW_SCHEDULES·INTERVIEW_EVALUATIONS·STAGE_RESULT_UPLOAD_TEMPLATE). 미등록이면 예외 → 반출 실패. ({BE}/service/ExportAuditLogger.java — exportActionType)
- 필터는 비-PII allowlist만: 지원현황은 `jobPostingId`·`jobPositionId`·canonical `status`·`columns`. 이름·연락처 조건은 기록 안 함. 제어문자 제거 후 JSON + `filtersHash`. 주체는 loginId·authority·IP·UA·`X-Request-Id`. ({BE}/service/ExportAuditLogger.java — logApplicationsExport)

**PDF**
- Thymeleaf → jsoup XHTML → openhtmltopdf(PDFBox). iText 금지. ({BE}/service/ApplicationPdfRenderer.java — render)
- 템플릿은 `th:text`만(`th:utext` 금지), 줄바꿈 CSS `pre-wrap`, 외부 리소스 금지(사진은 data URI). (`{BR}/templates/application-pdf.html`)
- 폰트 `recruit.pdf.font-classpath`(기본 NanumGothic)를 패밀리 `ApplicationPdfFont`로 등록 — 템플릿 CSS와 이름 일치 필수. 변수폰트 NotoSansKR은 PDFBox 2.x 문제로 제외. 폰트가 없으면 한글이 깨진다. ({BE}/service/ApplicationPdfRenderer.java — applyFont)
- `recruit.pdf.keep-section-together`(기본 true): 섹션을 페이지 경계에서 쪼개지 않는다. ({BE}/config/PdfProperties.java)
- 섹션은 `AdminApplicationSectionService` 재사용(마스킹 상속), 기본정보는 원천 규칙. ({BE}/service/ApplicationPdfService.java — buildBasicInfo)
- 학기별 성적: 공채 공고·전문대 이상만, 1~8학기 고정 표, 5학년 이상 성적이 있으면 9~16학기 표 추가. ({BE}/service/ApplicationPdfService.java — semesterGradeSections)
- 일괄: 중복 제거로 상한 우회 차단, 1건씩 렌더해 zip으로 흘림, 한 건 실패 시 전체 실패 + temp 삭제. ({BE}/service/ApplicationPdfBulkService.java — generate)

## 변경 레시피

### 지원현황 검색 조건 추가
1. `{BE}/dto/request/AdminApplicationSearchRequest.java` → `{BE}/dto/condition/AdminApplicationSearchCondition.java` → `{BE}/service/AdminApplicationSearchConditionFactory.java`.
2. `{BE}/domain/repository/JobApplicationRepository.java`: `ADMIN_SEARCH_WHERE`에 null-guard 절, 쿼리 3개에 파라미터. `{BE}/service/JobApplicationService.java`·`{BE}/service/ApplicationExportService.java`에서 전달.
3. `{BT}/controller/AdminApplicationControllerTest.java`와 `{BT}/controller/AdminExportControllerTest.java` 둘 다에 케이스. PII성 조건은 감사 filters에 넣지 않는다.
4. FE `{FE}/types/admin/application.ts` → `ApplicationStatus.vue` 검색폼·`initialSearchRequest`.
5. `npm run type-check`, 카드 갱신, `node tools/check-docs.mjs`.

### 엑셀 컬럼 추가·기본값 변경
1. `{BE}/service/ApplicationExportColumn.java`에 상수 추가(선언 위치 = 열 순서).
2. 새 섹션 데이터면 `ApplicationExportSection` + assembler `load()` 배치 조회(리포지토리는 [application-sections](application-sections.md) 소유).
3. `{BE}/service/ApplicationExportRowAssembler.java` `value()`에 표기(PDF와 같게, 마스킹 대상 금지).
4. `{BT}/service/ApplicationExportColumnTest.java`, `{BT}/service/ApplicationExportRowAssemblerTest.java`. FE 무변경. 카드 기본 컬럼 갱신, `node tools/check-docs.mjs`.

### PDF 섹션·표기 변경
1. `{BE}/service/AdminApplicationSectionService.java` → `{BE}/dto/response/ApplicationPdfView.java` → `{BE}/service/ApplicationPdfService.java` → `{BE}/service/ApplicationPdfLabels.java`.
2. 템플릿은 `th:text`만. 엑셀과 공유하는 표기면 assembler도 바꾸고, `Application.vue` 구성과 맞춘다.
3. `{BT}/service/ApplicationPdfServiceTest.java`, `{BT}/controller/ApplicationPdfControllerTest.java`, `{BT}/controller/ApplicationPdfSecurityHardeningTest.java` → 카드 갱신, `node tools/check-docs.mjs`.

### 새 목록 export 추가
1. 대응 목록 서비스를 재사용해 `{BE}/service/AdminDatasetExportService.java`에 spec·메서드 추가.
2. `{BE}/controller/AdminExportController.java`에 GET: actor 확인 → 생성 → `logExport` → `toResponse`, 감사 실패 시 `deleteQuietly` 후 재던짐.
3. **`{BE}/service/ExportAuditLogger.java` `exportActionType`에 매핑 추가**(없으면 매 요청 실패). 새 `AuditActionType`은 [privacy-audit](privacy-audit.md).
4. `{BT}/controller/AdminDatasetExportControllerTest.java`에 xlsx·404·권한. 카드 API 표 🟡 → 🟢, `node tools/check-docs.mjs`.

## 검증

백엔드(`recruit_back/recruit_backend/`에서):

```powershell
# Windows PowerShell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.AdminApplicationControllerTest" --tests "com.shinyoung.recruit.controller.AdminApplicationSectionControllerTest" --tests "com.shinyoung.recruit.controller.Admin*Export*" --tests "com.shinyoung.recruit.controller.ApplicationPdf*" --tests "com.shinyoung.recruit.service.AdminApplicationSectionServiceTest" --tests "com.shinyoung.recruit.service.ApplicationExport*" --tests "com.shinyoung.recruit.service.ApplicationPdf*" --tests "com.shinyoung.recruit.service.ApplicationPhotoLoaderTest" --tests "com.shinyoung.recruit.service.CommonCodeNamesTest" --tests "com.shinyoung.recruit.service.Ex*" --no-daemon
```

```bash
# Linux
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "com.shinyoung.recruit.controller.AdminApplicationControllerTest" --tests "com.shinyoung.recruit.controller.AdminApplicationSectionControllerTest" --tests "com.shinyoung.recruit.controller.Admin*Export*" --tests "com.shinyoung.recruit.controller.ApplicationPdf*" --tests "com.shinyoung.recruit.service.AdminApplicationSectionServiceTest" --tests "com.shinyoung.recruit.service.ApplicationExport*" --tests "com.shinyoung.recruit.service.ApplicationPdf*" --tests "com.shinyoung.recruit.service.ApplicationPhotoLoaderTest" --tests "com.shinyoung.recruit.service.CommonCodeNamesTest" --tests "com.shinyoung.recruit.service.Ex*" --no-daemon
```

다른 카드 테스트: 목록 조회 → `com.shinyoung.recruit.service.JobApplicationServiceTest`. 공용 인프라 → `com.shinyoung.recruit.controller.StageResultUploadControllerTest`, `com.shinyoung.recruit.controller.InterviewScheduleControllerTest`. Enricher → `com.shinyoung.recruit.service.StageResultServiceTest`.

프론트(`recruit_front/`에서, 이 도메인 vitest spec 없음):

```bash
npm run type-check
```

## 함정·결정

- `bbe5b53` 엑셀 컬럼 선택 도입. 그리드 "졸업년월"이 `withdrawnAt`을 보이던 결함 수정, 철회 라벨 '작성 완료'→'지원 철회'.
- `86d12c9` 모달은 열 때와 카탈로그 도착 시 기본 컬럼으로 채운다. 선택 기억 없음(의도).
- `8d7485d` 엑셀은 마지막 조회 성공 조건(`appliedSearch`)으로 받는다. 페이지 이동·재검색 때 행 선택을 비운다(안 보이는 지원서가 일괄 PDF에 섞임 방지).
- `3156492` 연락처 입력이 `certificateName`에 묶여 자격증명으로 검색되던 결함.
- 상세·PDF 학력표는 헤더가 "년월"인데 값은 `yyyy-MM-dd`. 전형결과 WITHDRAWN 라벨은 화면 "지원 철회", 엑셀 "철회".
- `AdminExportController.EXPORT_APPLICATION_PII`는 "PII 반출 관리자" 분리용 표식일 뿐 참조 없음(현재 목록 권한 = 반출 권한).
- PDF Header의 `status`·`submittedAt`은 템플릿 미출력. `adminApplicationApi.getApplicationAttachments()`는 빈 함수.
- `recruit_back/recruit_backend/docs/adr/0001-application-pdf-openhtmltopdf-avoid-itext-agpl.md` — iText는 AGPL이라 사내 폐쇄소스에 못 써 openhtmltopdf 채택, OFL 폰트 번들.
- `recruit_back/recruit_backend/docs/adr/0002-phase07-export-readonly-upload-stageresult-only.md` — export·PDF는 비변경. 쓰기는 StageResult 업로드만, 면접평가 업로드는 영구 제외.
- `recruit_back/recruit_backend/docs/adr/0006-audit-transaction-policy.md` — 반출 감사는 별도 tx + fail-close, over-record 허용.
