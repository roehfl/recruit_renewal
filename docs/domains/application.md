# 지원서 작성·제출 (`application`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [job-posting](job-posting.md) · [application-form](application-form.md) · [question](question.md) · [attachment](attachment.md) · [master-data](master-data.md) · [stage-result](stage-result.md) · [auth-account](auth-account.md) · [admin-application](admin-application.md) · [privacy-audit](privacy-audit.md)

## 요약

- 지원자(`ROLE_APPLICANT`)가 모집분야·근무지를 골라 지원서(`JobApplication`)를 만들고, 섹션 9종을 임시저장한 뒤 최종 제출한다. **접수기간 중에는 제출 후에도 수정·재제출**할 수 있다.
- 상태: `DRAFT` → `SUBMITTED`(재제출 시 `submittedAt` 갱신) → `WITHDRAWN`(종착). 지원자·공고 쌍당 1건.
- 섹션 API는 섹션마다 GET/POST 한 쌍. 목록 섹션은 **전체 교체**, 기본정보·병역은 1:1 upsert.
- 화면: `ApplicationFormView.vue`가 두 라우트를 처리한다 — `ApplicationStart`(`/applicant/:jobPostingId/apply`, 모집분야 선택 후 생성)·`application`(`/applicant/:applicationId/form`, 작성). 페이지·섹션 배치는 form-page 응답(레이아웃은 [application-form](application-form.md))을 따른다.
- 경계: 학교 검색 모달 `{FE}/views/common/SchoolModalBody.vue`·주소 `{FE}/api/application/addressApi.ts`·공통코드 → [master-data](master-data.md) / 첨부 `{FE}/api/application/sections/attachmentApi.ts`·`{BE}/controller/ApplicationAttachmentController.java` → [attachment](attachment.md) / `{BE}/controller/ApplicationStageResultController.java` → [stage-result](stage-result.md) / `{BE}/service/ApplicationFormPageService.java`·양식 설정 → [application-form](application-form.md) / 질문 정의 → [question](question.md).

## 용어

| 용어 | 뜻 |
|---|---|
| 지원서 (`JobApplication`) | 테이블 `job_application`, unique `(applicant_id, job_posting_id)`. 이름·공고명·모집분야명·근무지명 스냅샷과 `workLocationCode` 보유 |
| 섹션 9종 | `BASIC_INFO` 기본정보 · `EDUCATION` 학력 · `CAREER` 경력 · `CERTIFICATE` 자격증 · `LANGUAGE` 어학 · `MILITARY` 병역 · `AWARD` 수상 · `GAP_PERIOD` 공백기간 · `QUESTION_ANSWER` 자기소개/질문. `ATTACHMENT` 섹션은 지원자 화면에서 폐지 |
| 완성도 sectionCode | 대시보드 판정 코드. 섹션 타입과 같되 **`QUESTION_ANSWER`↔`QUESTION`**, 추가로 `FORM_CONFIG`·`ATTACHMENT`. `CAREER`는 판정 대상 아님 |
| `accepting` | 공고 `PUBLISHED` && 접수시작 ≤ now ≤ 접수종료 |
| `editable` | `status != WITHDRAWN && accepting`. 섹션 저장·지원분야 변경·제출 가능 여부 |
| 지원분야 | 모집분야(`JobPosition`) + 근무지 코드(CommonCode `WORK_LOCATION`). 후보 0개=선택 없음, 1개=자동 선택, N개=선택 필수 |
| 신입/경력 | 공고 `postingType` `PUBLIC_RECRUITMENT`=신입(학기별 성적+전체 평점), `EXPERIENCED_RECRUITMENT`=경력(전체 평점만). 레거시 `INTERN_RECRUITMENT`·`ROLLING_RECRUITMENT`도 전체 평점만 |
| 전체 교체 | POST 목록으로 기존 행을 모두 지우고 다시 저장. 빈 배열 = 전부 삭제 |
| prefill | 기본정보 미저장 시 GET이 `Applicant`의 `userName`·`phoneNumber`·`email`로 채운 응답(`persisted=false`) |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/ApplicationController.java` | 생성·조회·내 목록·대시보드·form-page·지원분야 수정·제출·철회·공고별 내 지원서 |
| controller | `{BE}/controller/ApplicationBasicInfoController.java` `{BE}/controller/ApplicationEducationController.java` `{BE}/controller/ApplicationCareerController.java` `{BE}/controller/ApplicationCertificateController.java` `{BE}/controller/ApplicationLanguageController.java` `{BE}/controller/ApplicationMilitaryController.java` `{BE}/controller/ApplicationAwardController.java` `{BE}/controller/ApplicationGapPeriodController.java` `{BE}/controller/ApplicationAnswerController.java` | 섹션 9종 GET·POST(답변은 `/questions` GET·`/answers` POST) |
| service | `{BE}/service/JobApplicationService.java` | 생성·지원분야 수정·제출·철회·내 목록. 관리자 검색 메서드도 포함([admin-application](admin-application.md)) |
| service | `{BE}/service/ApplicationSubmitValidator.java` | 최종 제출 검증(한글 메시지) |
| service | `{BE}/service/ApplicationCompletionReadChecker.java` | 완성도 판정 |
| service | `{BE}/service/ApplicationDashboardService.java` | 대시보드 플래그+완성도+최신 발표 결과 |
| service | `{BE}/service/ApplicationSectionAccessService.java` | 섹션 공통: 본인 소유·편집 가능·섹션 사용 여부 |
| service | `{BE}/service/CurrentApplicantService.java` | 세션 → 지원자 id(401/403). 첨부·전형결과·면접·계정 컨트롤러도 사용 |
| service | `{BE}/service/ApplicationBasicInfoService.java` `{BE}/service/ApplicationEducationService.java` `{BE}/service/ApplicationCareerService.java` `{BE}/service/ApplicationCertificateService.java` `{BE}/service/ApplicationLanguageService.java` `{BE}/service/ApplicationMilitaryService.java` `{BE}/service/ApplicationAwardService.java` `{BE}/service/ApplicationGapPeriodService.java` `{BE}/service/ApplicationAnswerService.java` | 섹션별 조회·저장·검증 |
| entity | `{BE}/domain/entity/JobApplication.java` | 루트. `create`·`updateDraft`·`updateWorkLocation`·`submit`·`withdraw` |
| entity | `{BE}/domain/entity/ApplicationBasicInfo.java` `{BE}/domain/entity/ApplicationMilitary.java` | 1:1 섹션(`job_application_id` unique) |
| entity | `{BE}/domain/entity/ApplicationEducation.java` `{BE}/domain/entity/ApplicationEducationSemesterGrade.java` `{BE}/domain/entity/ApplicationCareer.java` `{BE}/domain/entity/ApplicationCertificate.java` `{BE}/domain/entity/ApplicationLanguage.java` `{BE}/domain/entity/ApplicationAward.java` `{BE}/domain/entity/ApplicationGapPeriod.java` | 1:N 목록 섹션(학기 성적은 학력 1:N) |
| entity | `{BE}/domain/entity/ApplicationAnswer.java` | unique `(job_application_id, job_posting_question_id)`, 저장 시 질문 스냅샷 |
| repository | `{BE}/domain/repository/JobApplicationRepository.java` | 본인 지원서·내 목록·대시보드 조회(관리자 검색 쿼리도 포함) |
| repository | `{BE}/domain/repository/ApplicationBasicInfoRepository.java` `{BE}/domain/repository/ApplicationEducationRepository.java` `{BE}/domain/repository/ApplicationEducationSemesterGradeRepository.java` `{BE}/domain/repository/ApplicationCareerRepository.java` `{BE}/domain/repository/ApplicationCertificateRepository.java` `{BE}/domain/repository/ApplicationLanguageRepository.java` `{BE}/domain/repository/ApplicationMilitaryRepository.java` `{BE}/domain/repository/ApplicationAwardRepository.java` `{BE}/domain/repository/ApplicationGapPeriodRepository.java` `{BE}/domain/repository/ApplicationAnswerRepository.java` | 섹션 리포지토리 |
| dto | `{BE}/dto/request/ApplicationCreateRequest.java` `{BE}/dto/request/ApplicationUpdateRequest.java` `{BE}/dto/response/ApplicationDetailResponse.java` `{BE}/dto/response/MyApplicationResponse.java` `{BE}/dto/response/ApplicationDashboardResponse.java` `{BE}/dto/response/ApplicationCompletionSummaryResponse.java` `{BE}/dto/response/ApplicationSectionReadinessResponse.java` | 지원서 루트·대시보드 |
| dto | `{BE}/dto/request/BasicInfoSaveRequest.java` `{BE}/dto/response/BasicInfoResponse.java` `{BE}/dto/request/Education*` `{BE}/dto/request/SemesterGradeRequest.java` `{BE}/dto/response/EducationResponse.java` `{BE}/dto/response/SemesterGradeResponse.java` `{BE}/dto/request/Career*` `{BE}/dto/response/Career*` | 기본정보·학력·경력 |
| dto | `{BE}/dto/request/Certificate*` `{BE}/dto/request/Language*` `{BE}/dto/request/Award*` `{BE}/dto/request/GapPeriod*` `{BE}/dto/response/CertificateResponse.java` `{BE}/dto/response/LanguageResponse.java` `{BE}/dto/response/AwardResponse.java` `{BE}/dto/response/GapPeriodResponse.java` `{BE}/dto/request/MilitarySaveRequest.java` `{BE}/dto/response/MilitaryResponse.java` `{BE}/dto/request/ApplicationAnswer*` `{BE}/dto/response/ApplicationQuestionResponse.java` | 나머지 섹션 |
| enum | `{BE}/enumeration/JobApplicationStatus.java` `{BE}/enumeration/NationalityType.java` `{BE}/enumeration/VeteranStatus.java` `{BE}/enumeration/DisabilityStatus.java` `{BE}/enumeration/EducationLevel.java` `{BE}/enumeration/GraduationStatus.java` `{BE}/enumeration/DayNightType.java` `{BE}/enumeration/CampusType.java` `{BE}/enumeration/MilitarySubjectType.java` `{BE}/enumeration/MilitaryServiceType.java` `{BE}/enumeration/MilitaryBranch.java` `{BE}/enumeration/MilitaryRank.java` `{BE}/enumeration/GapType.java` | 상태·섹션 enum(`EducationLevel` 선언 순서 = 학력 서열) |
| exception | `{BE}/exception/InvalidJobApplicationException.java` `{BE}/exception/InvalidApplicationAnswerException.java` | 검증 실패 → 400 |
| exception | `{BE}/exception/JobApplicationNotFoundException.java` | 없음·타인 지원서 → 404 |
| test | `{BT}/controller/ApplicationControllerTest.java` `{BT}/service/JobApplicationServiceTest.java` `{BT}/service/ApplicationSubmitValidatorTest.java` `{BT}/service/ApplicationSubmitValidatorBasicInfoTest.java` `{BT}/service/ApplicationCompletionReadCheckerTest.java` `{BT}/service/ApplicationDashboardServiceTest.java` `{BT}/service/ApplicationSectionAccessServiceTest.java` `{BT}/service/CurrentApplicantServiceTest.java` | 코어 |
| test | `{BT}/*/ApplicationBasicInfo*` `{BT}/domain/repository/ApplicationBasicInfoEncryptionTest.java` `{BT}/*/ApplicationEducation*` `{BT}/*/ApplicationCareer*` `{BT}/*/ApplicationCertificate*` `{BT}/*/ApplicationLanguage*` `{BT}/*/ApplicationMilitary*` `{BT}/*/ApplicationAward*` `{BT}/*/ApplicationGapPeriod*` `{BT}/*/ApplicationAnswer*` | 섹션별 컨트롤러·서비스(+기본정보 암호화) |

### 프론트

| 구분 | 파일 | 역할 |
|---|---|---|
| route | `{FE}/routes/applicantRoutes.ts` | 공유. 이 카드 몫은 `ApplicationStart`·`application`(`requiresAuth`, `ROLE_APPLICANT`) |
| view | `{FE}/views/applicant/ApplicationFormView.vue` | 지원분야 선택·페이지 스텝·섹션 렌더·자동 임시저장·제출 |
| view | `{FE}/views/applicant/application/sections/*` | 섹션 컴포넌트 9개(`<섹션>Section.vue`, 질문은 `QuestionAnswerSection.vue`) |
| component | `{FE}/views/applicant/application/useSectionDraftState.ts` | 섹션 로드 여부·dirty 스냅샷 |
| api | `{FE}/api/applicationApi.ts` | 공유. 이 카드 몫은 `getMyApplications`. `getStageResults`=[stage-result](stage-result.md), 비밀번호·가입=[auth-account](auth-account.md) |
| api | `{FE}/api/application/dashboardApi.ts` | `getApplicationDashboard` |
| api | `{FE}/api/application/sections/basicInfoApi.ts` `{FE}/api/application/sections/educationApi.ts` `{FE}/api/application/sections/careerApi.ts` `{FE}/api/application/sections/certificateApi.ts` `{FE}/api/application/sections/languageApi.ts` `{FE}/api/application/sections/militaryApi.ts` `{FE}/api/application/sections/awardApi.ts` `{FE}/api/application/sections/gapPeriodApi.ts` `{FE}/api/application/sections/questionAnswerApi.ts` | 섹션 GET/POST(경력=`applicationCareerApi`, 병역=`applicationMilitaryApi`) |
| types | `{FE}/types/application.ts` | 공유. form-page·`SectionComponentProps`·`SectionActionHandle`·내 지원 목록·`postingType` |
| types | `{FE}/types/application/dashboard.ts` `{FE}/types/application/sections/basicInfo.ts` `{FE}/types/application/sections/education.ts` `{FE}/types/application/sections/career.ts` `{FE}/types/application/sections/certificate.ts` `{FE}/types/application/sections/language.ts` `{FE}/types/application/sections/military.ts` `{FE}/types/application/sections/award.ts` `{FE}/types/application/sections/gapPeriod.ts` `{FE}/types/application/sections/questionAnswer.ts` | 대시보드·섹션 타입 |
| types | `{FE}/common/applicationSection.ts` | 섹션 한글 라벨 상수(소비처는 [application-form](application-form.md) 레이아웃 탭) |
| test | `{FE}/api/__tests__/applicationApi.spec.ts` `{FE}/views/applicant/application/__tests__/useSectionDraftState.spec.ts` | 내 목록 파라미터·dirty/로드 전 저장 차단 |

## API 계약

권한 `APPLICANT`: `{BE}/config/SecurityConfig.java`에서 `/api/applications/**`와 `GET /api/job-postings/{jobPostingId}/application`이 `ROLE_APPLICANT`. 핸들러는 `CurrentApplicantService`로 지원자 id를 구하고(세션 없음 401, 지원자 아님 403) `findByIdAndApplicantId`로 본인 지원서만 찾는다(타인·없음 404). 응답은 `ApiResponse<T>`.

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
| 🔴 | GET | /applications/{applicationId}/basic-info | - | `BasicInfoResponse`(미저장이면 prefill) | APPLICANT |
| 🔴 | POST | /applications/{applicationId}/basic-info | `BasicInfoSaveRequest` | `BasicInfoResponse` | APPLICANT |
| 🔴 | GET | /applications/{applicationId}/educations | - | `[EducationResponse]` | APPLICANT |
| 🔴 | POST | /applications/{applicationId}/educations | `{ educations: [...] }` | `[EducationResponse]` | APPLICANT |
| 🔴 | GET | /applications/{applicationId}/careers | - | `{ careers: [...] }` | APPLICANT |
| 🔴 | POST | /applications/{applicationId}/careers | `{ careers: [...] }` | `{ careers: [...] }` | APPLICANT |
| 🟢 | GET | /applications/{applicationId}/certificates | - | `[CertificateResponse]` | APPLICANT |
| 🟢 | POST | /applications/{applicationId}/certificates | `{ certificates: [...] }` | `[CertificateResponse]` | APPLICANT |
| 🟢 | GET | /applications/{applicationId}/languages | - | `[LanguageResponse]` | APPLICANT |
| 🟢 | POST | /applications/{applicationId}/languages | `{ languages: [...] }` | `[LanguageResponse]` | APPLICANT |
| 🟢 | GET | /applications/{applicationId}/military | - | `MilitaryResponse` 또는 `null` | APPLICANT |
| 🟢 | POST | /applications/{applicationId}/military | `MilitarySaveRequest` | `MilitaryResponse` | APPLICANT |
| 🟢 | GET | /applications/{applicationId}/awards | - | `[AwardResponse]` | APPLICANT |
| 🟢 | POST | /applications/{applicationId}/awards | `{ awards: [...] }` | `[AwardResponse]` | APPLICANT |
| 🟢 | GET | /applications/{applicationId}/gap-periods | - | `[GapPeriodResponse]` | APPLICANT |
| 🟢 | POST | /applications/{applicationId}/gap-periods | `{ gapPeriods: [...] }` | `[GapPeriodResponse]` | APPLICANT |
| 🟢 | GET | /applications/{applicationId}/questions | - | `[ApplicationQuestionResponse]` | APPLICANT |
| 🟢 | POST | /applications/{applicationId}/answers | `{ answers: [{ questionId, answerText }] }` | `[ApplicationQuestionResponse]` | APPLICANT |

### 엔드포인트 상세

- `ApplicationController` 경로 변수는 `{applicationId:[0-9]+}` 정규식이라 `/applications/me`와 겹치지 않는다(섹션 컨트롤러는 정규식 없음). 생성·지원분야 수정·form-page·제출은 `ApplicationFormView.vue`가 `apiClient`로 직접 호출한다(api 모듈 없음).
- `POST /applications`(2026-08-31 🟢): `workLocationNameSnapshot`(선택 시점 `displayName`)도 저장. 근무지: 후보 ≥1인데 코드 없음 400 / 후보에 없는 코드 400 / 후보 0인데 값 있음 400. 호출 시점(2026-09-11): 공고 상세 '지원하기' → 작성 시작 화면에서 모집분야·근무지 선택 후 '지원서 작성' 버튼. 모집분야가 1개여도 자동 생성하지 않는다.
- `POST /applications/{id}`(지원분야 수정, 2026-08-31 🟢): 검증은 생성과 동일(바뀐 모집분야 후보 기준). FE(2026-09-11): 헤더 상시 드롭다운 변경 → 확인창 → 저장, 취소 시 저장값 복구. 후보 목록은 공개 공고 상세 `GET /job-postings/{id}` 재사용([job-posting](job-posting.md), 전용 API 없음). `editable`일 때만 변경 가능.
- `GET /applications/{id}`: **FE 미사용**. `{ applicationId, applicantId, jobPostingId, jobPostingTitle, jobPositionId, jobPositionName, status, submittedAt, withdrawnAt, createdAt, updatedAt }`.
- `GET /applications/me`: 호출처는 [auth-account](auth-account.md) 소유 `ApplicantProfile.vue`. 정렬 `createdAt desc, id desc`. 행 `{ applicationId, jobPostingId, jobPostingTitle, jobPostingStatus, jobPositionId, jobPositionName, applicationStatus, createdAt, submittedAt, withdrawnAt, receptionStart/EndDateTime, accepting, announcedResultCount, latestAnnouncedStageName, latestResultStatus }`(결과 요약은 **발표된 결과만**). page<0·size<1·size>100 → 400.
- `GET /applications/{id}/dashboard`(🟢 확정): 작성 화면 스텝의 "완료수/필수수" 카운터 소스. **임시저장 여부가 아니라 저장된 데이터가 필수 규칙을 만족하는지** 판정. 응답 `completionSummary { requiredSectionCount, completedRequiredSectionCount, requiredMissingCount, optionalSectionCount, completedOptionalSectionCount, optionalIncompleteCount, requiredCompletionRate, submitBlockingIssueCount }`, `requiredMissingSections`·`optionalIncompleteSections: [{ sectionCode, sectionName, required, complete, reasonCode, message }]`(**미완만**), 그 외 `{ applicationId, jobPostingId, jobPostingTitle, jobPositionName, applicationStatus, accepting, editable, submittable, withdrawable, submittedAt, withdrawnAt, latestAnnouncedStageName, latestResultStatus }`. 플래그: `editable = status != WITHDRAWN && accepting`(2026-09-02, 제출 후에도 수정), `submittable = editable && 결함 0`(2026-09-11, 재제출 허용), `withdrawable = status == SUBMITTED && accepting`. FE는 필수 섹션 코드가 `requiredMissingSections`에 없으면 완료로 본다(`CAREER`는 완료로 단정 안 함). 재조회: form-page 직후 + 임시저장 성공 직후. 매핑 `dashboardApi.getApplicationDashboard()` ↔ `ApplicationController.getDashboard()`. 메시지는 영문.
- `GET /applications/{id}/form-page`(🟢 확정): 조립은 [application-form](application-form.md)(`ApplicationFormPageService`·`ApplicationFormPageResponse`). 응답 `{ applicationId, jobPostingId, jobPostingTitle, jobPostingStatus, postingType, jobPositionId, jobPositionName, workLocationCode, workLocationName, applicationStatus, receptionStartDateTime, receptionEndDateTime, accepting, editable, submittedAt, withdrawnAt, formConfig, sections: [...] }`. `postingType`(2026-07-06 🟢, 미설정 공고는 `PUBLIC_RECRUITMENT`)으로 FE가 성적 입력 모드를 고른다. `workLocationCode`/`workLocationName`(2026-08-31 🟢, 스냅샷, 미선택 null).
- `POST /applications/{id}/submit`(🟢 확정 2026-09-18): 실패는 400 `ApiResponse.fail(message)`, **메시지 한글**("학력을 입력해야 제출할 수 있습니다." 등)을 FE가 그대로 표시. 질문 `minLength`도 서버가 검증(질문 섹션 없는 페이지에서 제출해 우회하던 문제 해결). 공고 수정으로 근무지 후보가 바뀌었으면 400. 재제출 시 `submittedAt`=마지막 제출 시각. FE 흐름은 규칙의 "프론트" 참고.
- `POST /applications/{id}/withdraw`: **FE 미사용**. `SUBMITTED` + 공고 `PUBLISHED` + 접수기간 내만.
- `GET /job-postings/{jobPostingId}/application`(🟢 확정 2026-09-18, [job-posting](job-posting.md) 화면 섹션에서 이관): 호출처 `ApplicationDetailView.vue`(job-posting 소유). 본인 지원서(`status` 포함), 없으면 404 — FE는 404를 "미지원"으로 처리하고 `skipClientEventLog`로 텔레메트리 제외.
- 기본정보 🔴: FE 구현됨(`BasicInfoSection.vue`) — 🟢 승격은 사용자 확인 대기(🔴 유지). 요청 `{ nameKorean, nameEnglish, nationalityType, countryCode, birthDate, mobilePhone, emergencyPhone, email, veteranStatus, veteranType, disabilityStatus, disabilityGradeCode, disabilityTypeCode, zipCode, addressBasic, addressDetail, applicationRouteCode }`, 응답은 + `basicInfoId, persisted`. `veteranType`(06-23): `veteranStatus=SUBJECT`면 필수, 아니면 비어야 함, 평문 자유 입력, 관리자 응답 포함. `applicationRouteCode`(09-01 🟢): 선택 ≤50, 공통코드 `APPLICATION_ROUTE` **활성 코드 서버 검증**, 평문·파기 대상 아님, FE 드롭다운, 관리자 응답 미포함.
- 학력 🔴: FE 구현됨(`EducationSection.vue`) — 🟢 승격은 사용자 확인 대기(🔴 유지). **응답 모양 차이**: 옛 계약 `{ educations: [...] }`, 코드는 배열 `[EducationResponse]`. 요청 행 `{ educationLevel, schoolName, majorName, additionalMajorType, additionalMajorName, thesisTitle, admissionDate, graduationDate, graduationStatus, dayNightType, campusType, transfer, countryCode, sortOrder, semesterGrades, schoolCode, schoolSource, overallGradePoint, overallMaxGradePoint, overallMajorGradePoint, overallMajorMaxGradePoint }`(응답 + `educationId`), 학기 행 `{ schoolYear, semester, earnedCredits, gradePoint, maxGradePoint, majorGradePoint, majorMaxGradePoint }`(응답 + `semesterGradeId`). 변경: `degreeName` 제거·`additionalMajorType`(공통코드 `MAJOR_TYPE` 문자열, 서버 미결합)·`additionalMajorName`·`thesisTitle`(06-25), 전체 평점 4필드(06-30 🟢, 자동 평균 없음), `HIGH_SCHOOL` 1건(08-26 🟢), 학기 최대 16(09-01 🟢). FE(09-01): 라벨 "고등학교", 1~8학기 기본 + '학기 추가' 16학기까지, N학기 ↔ `(ceil(N/2), (N-1)%2+1)`.
- 경력 🔴: FE 구현됨(`CareerSection.vue`) — 🟢 승격은 사용자 확인 대기(🔴 유지). 요청 행 `{ companyName, departmentName, positionTitle, employmentType, startDate, endDate, promotionDate, currentlyEmployed, currentSalary, resignationReason, sortOrder }`, 응답 `{ careers: [행 + careerId] }`. `careerType` 폐지·`promotionDate`(06-23), `responsibilities` 제거·`currentSalary`(만원, ≥0)(06-25). 0건 허용. 경력기술서(09-01, FE 전용): 경력 ≥1건일 때만 노출, [attachment](attachment.md) API를 `sectionType=CAREER`+`attachmentType=CAREER_DESCRIPTION`(섹션당 1건)으로 사용. 옛 계약의 "delete → upload"와 달리 코드는 **업로드 성공 후 기존 삭제**, 경력 0건이면 첨부도 삭제.
- 자격증 🟢: 행 `{ certificateName, issuingOrganization, acquiredDate, certificateNumber, expiredDate, scoreOrGrade, sortOrder }`(응답 + `certificateId`). 0건 허용.
- 어학 🟢: 행 `{ languageCode, languageName, testCode, testName, scoreOrGrade, conversationalAbility, examDate, expiredDate, issuingOrganization, registrationNumber, sortOrder }`(응답 + `languageId`). 코드 선택(08-28): `languageCode`=공통코드 `LANGUAGE_TYPE`, `testCode`=`LANGUAGE_TEST_{languageCode}`, 이름 필드는 표시명 스냅샷(필수). 코드는 서버 미검증·시드 없음. 코드 `ETC`면 FE가 자유입력을 이름에 넣고 `ETC` 옵션을 목록 끝에 붙인다. 표시·PDF·검색은 이름, 집계는 코드 기준. `registrationNumber`(09-01 🟢): 선택, 파기 대상, 관리자 응답 미포함. FE는 회화능력 입력을 없애 신규 저장분 `conversationalAbility`=null(서버 필드·관리자 필터 `languageLevel`·PDF는 유지).
- 병역 🟢: 옛 계약의 "프론트 미반영(Placeholder)" 표기는 낡음(`MilitarySection.vue` 구현됨). 필드 `{ militarySubjectType, serviceType, militaryBranch, rank, serviceStartDate, serviceEndDate, nonServiceReason }`(응답 + `militaryId`, 미저장이면 `data: null`). 대상구분 `SUBJECT`(미필)/`NOT_SUBJECT`/`COMPLETED`(군필)/`EXEMPTED`(면제)(06-29 `NOT_APPLICABLE` 제거, `exemptionReason`→`nonServiceReason`). 사유는 민감정보 — 관리자 응답 `nonServiceReasonMasked`(`***`), PDF 라벨 "병역사유", 파기 대상.
- 수상 🟢: 행 `{ awardName, awardingOrganization, awardDate, description, sortOrder }`(+ `awardId`). 0건 허용.
- 공백기간 🟢: 행 `{ startDate, endDate, gapType, reason, description, sortOrder }`(+ `gapPeriodId`). `gapType` `EDUCATION`/`CAREER`/`OTHER`(FE 라벨 하드코딩). "해당 사항 없음" 체크박스는 영속화가 없어 주석 처리.
- 질문 🟢: `GET /questions` 행 `{ questionId, questionText, helperText, category, answerType, required, minLength, maxLength, sortOrder, answerId, answerText, updatedAt }` = 공고 질문([question](question.md)) + 본인 답변. `POST /answers`는 전체 교체, `answerText` ≤5000·null 허용(부분 임시저장). 필수·`minLength`는 제출 시(서버+FE) 강제.

## 규칙·불변식

**지원서 생명주기** ({BE}/service/JobApplicationService.java)
- 생성: 공고 `PUBLISHED`·접수기간 내·`visible`·노출기간 내(null=무제한), 양식 config 존재, 같은 공고 지원서 없음(상태 무관 — 철회 후 재지원 불가), 모집분야가 그 공고 소속, 근무지 규칙. 이름 스냅샷 `userName`→`name`, 둘 다 없으면 400. (— create)
- 동시 생성은 DB unique 위반 → 409 "이미 처리되었거나 중복된 데이터입니다." ({BE}/exception/GlobalExceptionHandler.java — handleDataIntegrityViolation)
- 지원분야 수정·제출·철회는 공고 `PUBLISHED`+접수기간 내만. 수정·제출은 `WITHDRAWN` 거부, 철회는 `SUBMITTED`만. 제출은 `DRAFT`·`SUBMITTED` 모두 허용(재제출)하며 config·모집분야 소속·근무지를 재검증한 뒤 `ApplicationSubmitValidator`를 돈다. (— updateDraft·submit·withdraw)
- 근무지: 후보 0개면 코드 금지, 있으면 필수·후보 안의 코드. 코드는 trim, 공백은 null. (— validateWorkLocationChoice)

**섹션 접근** ({BE}/service/ApplicationSectionAccessService.java)
- 본인 지원서가 아니면 404. GET은 소유만 확인(편집 불가·섹션 미사용이어도 조회). (— findOwnedApplication)
- 저장 조건: `WITHDRAWN` 아님 + 공고 `PUBLISHED` + 접수기간 내. `SUBMITTED`도 저장 가능하며 즉시 제출본에 반영. (— validateWritable)
- 학력·경력·자격증·어학·병역·수상·공백기간은 config `useXxx=false`(또는 config 없음)면 저장 400. 기본정보·답변은 검사 없음. (— validate*Enabled)
- 목록 섹션 POST: 목록 null 400, 행마다 `sortOrder` ≥0·요청 내 유일. (각 섹션 서비스 — validateRequest)

**섹션별 저장 검증** (서버 메시지 영문, 400; 위치는 `{BE}/service/` 아래 서비스)

| 섹션 | 방식 | 서버 검증 | 위치 |
|---|---|---|---|
| 기본정보 | upsert | `FOREIGN`⇒`countryCode` 필수·`NATIONALITY` 활성 코드, 국내면 금지 / `veteranStatus=SUBJECT`⇒`veteranType` 필수, 아니면 금지 / 장애 `SUBJECT`⇒등급·유형 필수·`DISABILITY_GRADE`/`DISABILITY_TYPE` 활성 코드, 아니면 금지 / `applicationRouteCode`는 `APPLICATION_ROUTE` 활성 코드 / 만 14~100세 / 휴대전화 필수·비상연락처 선택, `^[0-9-]{9,20}$` | ApplicationBasicInfoService — validateRequest |
| 학력 | 전체 교체(학기 먼저 삭제) | 구분·학교명·졸업구분 필수 / 입학 ≤ 졸업 / `HIGH_SCHOOL` ≤1건·학기 성적 금지 / 고졸 외 전체 평점·만점 필수, 쌍은 함께, 평점 ≥0, 만점 >0, 평점 ≤ 만점 / 전공 전체 쌍 선택·같은 규칙 / 학기 ≤16, 학년·학기 ≥1, 평점 필수 ≥0, 만점 필수 >0, 평점 ≤ 만점, 전공 평점 있으면 전공 만점 필수 | ApplicationEducationService — validateRequest |
| 경력 | 전체 교체 | 회사명·입사일·재직여부 필수 / 재직중이면 퇴사일 금지, 아니면 필수 / 입사 ≤ 퇴사 / 연봉 ≥0 / 퇴사사유 ≤2000 | ApplicationCareerService — validateCareerRequiredFields |
| 자격증 | 전체 교체 | 자격증명·발급기관·취득일 필수 / 취득 ≤ 만료 | ApplicationCertificateService |
| 어학 | 전체 교체 | 언어코드·언어명·시험코드·시험명·응시일 필수 / 응시 ≤ 만료 | ApplicationLanguageService |
| 병역 | upsert | 대상구분 필수 / 사유 ≤1000 / 복무 시작 ≤ 종료 / `NOT_SUBJECT` 상세·사유 금지, `SUBJECT`·`EXEMPTED` 복무 상세 금지, `COMPLETED` 사유 금지 | ApplicationMilitaryService — validateFieldsBySubjectType |
| 수상 | 전체 교체 | 수상명·수여기관·수상일 필수 / 설명 ≤2000 | ApplicationAwardService |
| 공백기간 | 전체 교체 | 시작·종료·구분·사유 필수 / 시작 ≤ 종료 / 설명 ≤2000 | ApplicationGapPeriodService |
| 답변 | 전체 교체(delete+flush 후 insert) | `questionId` 필수·요청 내 유일·이 공고 질문 / 길이 ≤ `maxLength`, `SHORT_TEXT` ≤500, `LONG_TEXT` ≤5000 / null·공백 허용 | ApplicationAnswerService — validateAnswerItems |

- 기본정보 PII(이름·국가·연락처·이메일·장애코드·주소)는 `AesAttributeConverter` 암호화. `birthDate`·`veteranType`·`applicationRouteCode`는 평문. ({BE}/domain/entity/ApplicationBasicInfo.java)

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
- 로드 성공 전·실패 시 저장 금지(빈 목록 전체 교체로 서버 데이터 삭제 방지), 조회·저장 성공 직후 `markSynced()`. ({FE}/views/applicant/application/useSectionDraftState.ts — assertLoaded)
- 자동 임시저장: 페이지 이동·제출·지원분야 변경 전 dirty 섹션만 저장, 실패 시 중단, 편집 불가면 생략. dirty 상태로 이탈(라우트·beforeunload) 시 확인창. `SUBMITTED`면 저장 전 대상 섹션 `validateBeforeSubmit` 통과 필수. (— saveCurrentPage)
- 제출: dirty 자동 저장 → 전 섹션 `validateBeforeSubmit`(실패 페이지로 이동) → `POST /submit` → form-page 재조회. 모집분야만 바꾸고 근무지 미선택(`positionPending`)이면 제출 불가. (— submitApplication·canSubmit)
- `ATTACHMENT` 섹션은 화면에서 제외. 증명사진은 기본정보 섹션(`attachmentType=ETC`, `sectionType=BASIC_INFO`), 경력기술서는 경력 섹션이 [attachment](attachment.md) API로 올린다.
- **학기별 성적은 신입 공고만**: `formPage.postingType === 'PUBLIC_RECRUITMENT'`일 때만 학기 성적 표를 보인다. 경력·레거시는 전체 평점만. 분기는 FE에만 있고 서버는 `postingType`으로 막지 않는다(고졸 학기 금지만). 고졸 행은 `semesterGrades=[]`로 보낸다. ({FE}/views/applicant/application/sections/EducationSection.vue — isPostingPUBLIC·buildPayload)
- 섹션 `validateBeforeSubmit`: `section.required`면 ≥1건(수상·자격증·어학·공백기간·경력), 질문은 `required`·`minLength`·`maxLength`.

## 변경 레시피

### 섹션에 입력 필드 추가 (예: 학력)
1. `{BE}/domain/entity/ApplicationEducation.java` 필드·`create` 인자 추가(`ddl-auto: update`, 컬럼 삭제·제약 변경은 `recruit_back/recruit_backend/docs/ops/`에 수동 DDL).
2. `{BE}/dto/request/EducationRequest.java`·`{BE}/dto/response/EducationResponse.java` 필드 추가(축약 생성자 쓰는 테스트 확인).
3. `{BE}/service/ApplicationEducationService.java` `validateRequest`·`toEducation` 수정. 제출 필수면 아래 레시피. PII면 파기 `ApplicationPiiPurgeService`([privacy-audit](privacy-audit.md)), 관리자 조회(`AdminApplicationSectionService`)·PDF·엑셀은 [admin-application](admin-application.md).
4. `{BT}/service/ApplicationEducationServiceTest.java`·`{BT}/controller/ApplicationEducationControllerTest.java` 케이스 추가·실행.
5. FE: `{FE}/types/application/sections/education.ts` → `EducationSection.vue`(`buildPayload`·`validate`·`:disabled="!editable"`), `npm run type-check`.
6. 카드 API 상세·규칙 표 갱신, `node tools/check-docs.mjs`.

### 제출 필수 규칙 추가·변경
1. `{BE}/service/ApplicationSubmitValidator.java`에 규칙(한글 메시지).
2. 같은 판정을 `{BE}/service/ApplicationCompletionReadChecker.java`에(새 sectionCode면 FE `completionSectionCodeMap`도).
3. 해당 섹션 `validateBeforeSubmit`에 같은 규칙.
4. `{BT}/service/ApplicationSubmitValidatorTest.java`·`{BT}/service/ApplicationCompletionReadCheckerTest.java`·`{BT}/service/ApplicationDashboardServiceTest.java` 갱신·실행.
5. 카드 "최종 제출 검증"·"완성도 판정" 갱신, `node tools/check-docs.mjs`.

### 편집 가능 조건 변경
1. 서버 네 곳을 함께: `ApplicationSectionAccessService.validateWritable`, `JobApplicationService.validatePublishedAndAccepting`, `ApplicationDashboardService`(`editable`), [application-form](application-form.md)의 `ApplicationFormPageService`(`editable`).
2. FE는 `formPage.editable` 하나로 제어. 새 버튼은 `fieldset` 안인지, 모달이면 `:disabled="!editable"`가 있는지 확인.
3. `ApplicationSectionAccessServiceTest`·`JobApplicationServiceTest`·`ApplicationDashboardServiceTest` 실행, 카드 갱신, `node tools/check-docs.mjs`.

## 검증

백엔드(`recruit_back/recruit_backend/`에서). 코어:

```powershell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "*.ApplicationControllerTest" --tests "*.JobApplicationServiceTest" --tests "*.ApplicationSubmitValidator*" --tests "*.ApplicationCompletionReadCheckerTest" --tests "*.ApplicationDashboardServiceTest" --tests "*.ApplicationSectionAccessServiceTest" --tests "*.CurrentApplicantServiceTest" --no-daemon
```

```bash
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "*.ApplicationControllerTest" --tests "*.JobApplicationServiceTest" --tests "*.ApplicationSubmitValidator*" --tests "*.ApplicationCompletionReadCheckerTest" --tests "*.ApplicationDashboardServiceTest" --tests "*.ApplicationSectionAccessServiceTest" --tests "*.CurrentApplicantServiceTest" --no-daemon
```

섹션 1종(`Education`을 `BasicInfo`·`Career`·`Certificate`·`Language`·`Military`·`Award`·`GapPeriod`·`Answer`로 바꿔 쓴다. 컨트롤러·서비스 테스트가 함께 돈다):

```powershell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "*.ApplicationEducation*" --no-daemon
```

```bash
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "*.ApplicationEducation*" --no-daemon
```

프론트(`recruit_front/`에서):

```bash
npm run type-check
npx vitest run src/api/__tests__/applicationApi.spec.ts src/views/applicant/application/__tests__/useSectionDraftState.spec.ts
```

## 함정·결정

- 86d12c9: 편집 불가 비활성화는 `a-config-provider`(antd) + `fieldset disabled`(일반 button·input) 두 겹이다. 모달은 teleport로 fieldset 밖이라 모달 안 일반 버튼엔 `:disabled="!editable"`를 직접 단다(`EducationSection.vue` 학기 추가·삭제).
- 8d7485d: 로드 전·실패 상태 저장이 빈 목록 전체 교체로 서버 데이터를 지우던 결함 → `assertLoaded`. 첨부 교체는 "업로드 성공 후 기존 삭제"(반대면 업로드 실패 시 유실). 제출 메시지 한글화·`minLength` 서버 검증도 이 커밋.
- 523ab48: 폐지된 `ATTACHMENT` 섹션 필수 요건이 제출을 영구히 막아 제출 검증·완성도에서 뺐다. 현재 기준 `ApplicationSectionType.acceptsApplicantAttachment()`는 300e792에서 도입이며 두 곳이 같은 필터를 써야 한다. 요건 등록 거부는 [job-posting](job-posting.md).
- a630a15: 대학 학교 검색은 학교명 완전일치(부분검색 불가) → [master-data](master-data.md).
- `/applications/<문자열>` 경로를 추가할 땐 `{applicationId:[0-9]+}` 정규식과 `/applications/me`를 확인. 지원자 전용 `GET /api/job-postings/{jobPostingId}/application` 매처는 `GET /api/job-postings/**` permitAll보다 먼저 선언돼야 한다(`{BE}/config/SecurityConfig.java`).
- `JobApplicationService`의 공용 private 메서드(`validatePageRequest`·`createPageRequest`)는 관리자 검색도 쓴다 → [admin-application](admin-application.md).
- ADR `recruit_back/recruit_backend/docs/adr/0004-school-optional-application-level-link.md`: 학교 연결은 선택·FK 없음, `schoolName`이 스냅샷. 현 구현은 `schoolCode`+`schoolSource`(ADR의 `schoolId`는 옛 이름).
- ADR `recruit_back/recruit_backend/docs/adr/0003-commoncode-additive-no-enum-migration.md`: 공통코드는 추가만, enum을 코드로 옮기지 않는다(병역 군별·계급·복무형태, `DayNightType`·`CampusType` 유지). 언어·시험·전공구분·회화능력 코드값은 서버 미검증 문자열.
