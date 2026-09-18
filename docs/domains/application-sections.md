# 지원서 섹션 9종 (`application-sections`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [application](application.md)(지원서 생명주기·섹션 접근·제출 검증·완성도·작성 화면 틀) · [master-data](master-data.md) · [attachment](attachment.md) · [question](question.md) · [application-form](application-form.md) · [admin-application](admin-application.md) · [privacy-audit](privacy-audit.md) · [client-event-log](client-event-log.md) · [statistics](statistics.md)

## 요약

- 지원서([application](application.md))의 입력 섹션 9종을 섹션마다 GET/POST 한 쌍으로 조회·임시저장한다(답변은 `/questions` GET·`/answers` POST). 목록 섹션은 **전체 교체**, 기본정보·병역은 1:1 upsert.
- 공통 전제(본인 소유 404·저장 가능 조건·`useXxx` 사용 여부 400)는 `ApplicationSectionAccessService` — [application](application.md) 규칙 "섹션 접근". 최종 제출 검증·완성도 판정도 [application](application.md).
- 화면: `{FE}/views/applicant/application/sections/*` 컴포넌트 9개를 `ApplicationFormView.vue`([application](application.md))가 form-page 섹션 배치대로 렌더한다. 컴포넌트 계약(props·`defineExpose`·등록 맵)은 [application](application.md) 규칙 "프론트".
- 경계: 학교 검색 모달 `{FE}/views/common/SchoolModalBody.vue`·주소 `{FE}/api/application/addressApi.ts`·공통코드 → [master-data](master-data.md) / 첨부 `{FE}/api/application/sections/attachmentApi.ts`(이 디렉터리에 있지만 첨부 소유) → [attachment](attachment.md) / 질문 정의 → [question](question.md) / 섹션 한글 라벨 `{FE}/common/applicationSection.ts`(레이아웃 탭 전용) → [application-form](application-form.md) / 관리자 섹션 조회·PDF·엑셀 → [admin-application](admin-application.md) / 저장 실패 수동 로깅(`logClientEvent`) → [client-event-log](client-event-log.md).

## 용어

| 용어 | 뜻 |
|---|---|
| 섹션 9종 | `BASIC_INFO` 기본정보 · `EDUCATION` 학력 · `CAREER` 경력 · `CERTIFICATE` 자격증 · `LANGUAGE` 어학 · `MILITARY` 병역 · `AWARD` 수상 · `GAP_PERIOD` 공백기간 · `QUESTION_ANSWER` 자기소개/질문. `ATTACHMENT` 섹션은 지원자 화면에서 폐지 |
| 신입/경력 | 공고 `postingType` `PUBLIC_RECRUITMENT`=신입(학기별 성적+전체 평점), `EXPERIENCED_RECRUITMENT`=경력(전체 평점만). 레거시 `INTERN_RECRUITMENT`·`ROLLING_RECRUITMENT`도 전체 평점만 |
| 전체 교체 | POST 목록으로 기존 행을 모두 지우고 다시 저장. 빈 배열 = 전부 삭제 |
| upsert | 기본정보·병역. 지원서당 1행(`job_application_id` unique)을 만들거나 갱신 |
| prefill | 기본정보 미저장 시 GET이 `Applicant`의 `userName`·`phoneNumber`·`email`로 채운 응답(`persisted=false`) |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/ApplicationBasicInfoController.java` `{BE}/controller/ApplicationEducationController.java` `{BE}/controller/ApplicationCareerController.java` `{BE}/controller/ApplicationCertificateController.java` `{BE}/controller/ApplicationLanguageController.java` `{BE}/controller/ApplicationMilitaryController.java` `{BE}/controller/ApplicationAwardController.java` `{BE}/controller/ApplicationGapPeriodController.java` `{BE}/controller/ApplicationAnswerController.java` | 섹션 9종 GET·POST(답변은 `/questions` GET·`/answers` POST) |
| service | `{BE}/service/ApplicationBasicInfoService.java` `{BE}/service/ApplicationEducationService.java` `{BE}/service/ApplicationCareerService.java` `{BE}/service/ApplicationCertificateService.java` `{BE}/service/ApplicationLanguageService.java` `{BE}/service/ApplicationMilitaryService.java` `{BE}/service/ApplicationAwardService.java` `{BE}/service/ApplicationGapPeriodService.java` `{BE}/service/ApplicationAnswerService.java` | 섹션별 조회·저장·검증 |
| entity | `{BE}/domain/entity/ApplicationBasicInfo.java` `{BE}/domain/entity/ApplicationMilitary.java` | 1:1 섹션(`job_application_id` unique) |
| entity | `{BE}/domain/entity/ApplicationEducation.java` `{BE}/domain/entity/ApplicationEducationSemesterGrade.java` `{BE}/domain/entity/ApplicationCareer.java` `{BE}/domain/entity/ApplicationCertificate.java` `{BE}/domain/entity/ApplicationLanguage.java` `{BE}/domain/entity/ApplicationAward.java` `{BE}/domain/entity/ApplicationGapPeriod.java` | 1:N 목록 섹션(학기 성적은 학력 1:N) |
| entity | `{BE}/domain/entity/ApplicationAnswer.java` | unique `(job_application_id, job_posting_question_id)`, 저장 시 질문 스냅샷 |
| repository | `{BE}/domain/repository/ApplicationBasicInfoRepository.java` `{BE}/domain/repository/ApplicationEducationRepository.java` `{BE}/domain/repository/ApplicationEducationSemesterGradeRepository.java` `{BE}/domain/repository/ApplicationCareerRepository.java` `{BE}/domain/repository/ApplicationCertificateRepository.java` `{BE}/domain/repository/ApplicationLanguageRepository.java` `{BE}/domain/repository/ApplicationMilitaryRepository.java` `{BE}/domain/repository/ApplicationAwardRepository.java` `{BE}/domain/repository/ApplicationGapPeriodRepository.java` `{BE}/domain/repository/ApplicationAnswerRepository.java` | 섹션 리포지토리(관리자 조회·통계 쿼리도 포함 — 함정 참고) |
| dto | `{BE}/dto/request/BasicInfoSaveRequest.java` `{BE}/dto/response/BasicInfoResponse.java` `{BE}/dto/request/Education*` `{BE}/dto/request/SemesterGradeRequest.java` `{BE}/dto/response/EducationResponse.java` `{BE}/dto/response/SemesterGradeResponse.java` `{BE}/dto/request/Career*` `{BE}/dto/response/Career*` | 기본정보·학력·경력 |
| dto | `{BE}/dto/request/Certificate*` `{BE}/dto/request/Language*` `{BE}/dto/request/Award*` `{BE}/dto/request/GapPeriod*` `{BE}/dto/response/CertificateResponse.java` `{BE}/dto/response/LanguageResponse.java` `{BE}/dto/response/AwardResponse.java` `{BE}/dto/response/GapPeriodResponse.java` `{BE}/dto/request/MilitarySaveRequest.java` `{BE}/dto/response/MilitaryResponse.java` `{BE}/dto/request/ApplicationAnswer*` `{BE}/dto/response/ApplicationQuestionResponse.java` | 나머지 섹션 |
| enum | `{BE}/enumeration/NationalityType.java` `{BE}/enumeration/VeteranStatus.java` `{BE}/enumeration/DisabilityStatus.java` `{BE}/enumeration/EducationLevel.java` `{BE}/enumeration/GraduationStatus.java` `{BE}/enumeration/DayNightType.java` `{BE}/enumeration/CampusType.java` `{BE}/enumeration/MilitarySubjectType.java` `{BE}/enumeration/MilitaryServiceType.java` `{BE}/enumeration/MilitaryBranch.java` `{BE}/enumeration/MilitaryRank.java` `{BE}/enumeration/GapType.java` | 섹션 enum(`EducationLevel` 선언 순서 = 학력 서열) |
| exception | `{BE}/exception/InvalidApplicationAnswerException.java` | 답변 검증 실패 → 400. 다른 섹션은 `InvalidJobApplicationException`([application](application.md)) |
| test | `{BT}/*/ApplicationBasicInfo*` `{BT}/domain/repository/ApplicationBasicInfoEncryptionTest.java` `{BT}/*/ApplicationEducation*` `{BT}/*/ApplicationCareer*` `{BT}/*/ApplicationCertificate*` `{BT}/*/ApplicationLanguage*` `{BT}/*/ApplicationMilitary*` `{BT}/*/ApplicationAward*` `{BT}/*/ApplicationGapPeriod*` `{BT}/*/ApplicationAnswer*` | 섹션별 컨트롤러·서비스(+기본정보 암호화) |

### 프론트

| 구분 | 파일 | 역할 |
|---|---|---|
| view | `{FE}/views/applicant/application/sections/*` | 섹션 컴포넌트 9개(`<섹션>Section.vue`, 질문은 `QuestionAnswerSection.vue`) |
| component | `{FE}/views/applicant/application/useSectionDraftState.ts` | 섹션 로드 여부·dirty 스냅샷 |
| api | `{FE}/api/application/sections/basicInfoApi.ts` `{FE}/api/application/sections/educationApi.ts` `{FE}/api/application/sections/careerApi.ts` `{FE}/api/application/sections/certificateApi.ts` `{FE}/api/application/sections/languageApi.ts` `{FE}/api/application/sections/militaryApi.ts` `{FE}/api/application/sections/awardApi.ts` `{FE}/api/application/sections/gapPeriodApi.ts` `{FE}/api/application/sections/questionAnswerApi.ts` | 섹션 GET/POST(경력=`applicationCareerApi`, 병역=`applicationMilitaryApi`). `educationApi.getSchools`는 [master-data](master-data.md) 학교 검색 호출 |
| types | `{FE}/types/application/sections/basicInfo.ts` `{FE}/types/application/sections/education.ts` `{FE}/types/application/sections/career.ts` `{FE}/types/application/sections/certificate.ts` `{FE}/types/application/sections/language.ts` `{FE}/types/application/sections/military.ts` `{FE}/types/application/sections/award.ts` `{FE}/types/application/sections/gapPeriod.ts` `{FE}/types/application/sections/questionAnswer.ts` | 섹션 타입(`basicInfo.ts`에 첨부 유니언 공유 — [attachment](attachment.md)) |
| test | `{FE}/views/applicant/application/__tests__/useSectionDraftState.spec.ts` | dirty/로드 전 저장 차단 |

## API 계약

권한·본인 확인은 [application](application.md) API 계약 서두와 같다(`ROLE_APPLICANT`, 타인·없음 404, `ApiResponse<T>`). 18개 모두 2026-09-19 코드 기준 확정(FE 호출 확인).

| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |
|---|---|---|---|---|---|
| 🟢 | GET | /applications/{applicationId}/basic-info | - | `BasicInfoResponse`(미저장이면 prefill) | APPLICANT |
| 🟢 | POST | /applications/{applicationId}/basic-info | `BasicInfoSaveRequest` | `BasicInfoResponse` | APPLICANT |
| 🟢 | GET | /applications/{applicationId}/educations | - | `[EducationResponse]` | APPLICANT |
| 🟢 | POST | /applications/{applicationId}/educations | `{ educations: [...] }` | `[EducationResponse]` | APPLICANT |
| 🟢 | GET | /applications/{applicationId}/careers | - | `{ careers: [...] }` | APPLICANT |
| 🟢 | POST | /applications/{applicationId}/careers | `{ careers: [...] }` | `{ careers: [...] }` | APPLICANT |
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

- 섹션 컨트롤러의 경로 변수 `{applicationId}`에는 정규식이 없다(`ApplicationController`와 다름). 호출은 각 섹션 컴포넌트가 섹션 api 모듈로 한다.
- 기본정보(2026-09-19 코드 기준 확정, FE `BasicInfoSection.vue` → `basicInfoApi` 호출 확인): 요청 `{ nameKorean, nameEnglish, nationalityType, countryCode, birthDate, mobilePhone, emergencyPhone, email, veteranStatus, veteranType, disabilityStatus, disabilityGradeCode, disabilityTypeCode, zipCode, addressBasic, addressDetail, applicationRouteCode }`, 응답은 + `basicInfoId, persisted`. `veteranType`(06-23): `veteranStatus=SUBJECT`면 필수, 아니면 비어야 함, 평문 자유 입력, 관리자 응답 포함. `applicationRouteCode`(09-01 🟢): 선택 ≤50, 공통코드 `APPLICATION_ROUTE` **활성 코드 서버 검증**, 평문·파기 대상 아님, FE 드롭다운, 관리자 응답 미포함.
- 학력(2026-09-19 코드 기준 확정, FE `EducationSection.vue` → `educationApi` 호출 확인): GET·POST 응답 모두 **배열 `[EducationResponse]`**(`{ educations: [...] }`로 감싸지 않음, 요청만 `{ educations: [...] }`). 요청 행 `{ educationLevel, schoolName, majorName, additionalMajorType, additionalMajorName, thesisTitle, admissionDate, graduationDate, graduationStatus, dayNightType, campusType, transfer, countryCode, sortOrder, semesterGrades, schoolCode, schoolSource, overallGradePoint, overallMaxGradePoint, overallMajorGradePoint, overallMajorMaxGradePoint }`(응답 + `educationId`), 학기 행 `{ schoolYear, semester, earnedCredits, gradePoint, maxGradePoint, majorGradePoint, majorMaxGradePoint }`(응답 + `semesterGradeId`). 변경: `degreeName` 제거·`additionalMajorType`(공통코드 `MAJOR_TYPE` 문자열, 서버 미결합)·`additionalMajorName`·`thesisTitle`(06-25), 전체 평점 4필드(06-30 🟢, 자동 평균 없음), `HIGH_SCHOOL` 1건(08-26 🟢), 학기 최대 16(09-01 🟢). FE(09-01): 라벨 "고등학교", 1~8학기 기본 + '학기 추가' 16학기까지, N학기 ↔ `(ceil(N/2), (N-1)%2+1)`.
- 경력(2026-09-19 코드 기준 확정, FE `CareerSection.vue` → `applicationCareerApi` 호출 확인): 요청 행 `{ companyName, departmentName, positionTitle, employmentType, startDate, endDate, promotionDate, currentlyEmployed, currentSalary, resignationReason, sortOrder }`, 응답 `{ careers: [행 + careerId] }`. `careerType` 폐지·`promotionDate`(06-23), `responsibilities` 제거·`currentSalary`(만원, ≥0)(06-25). 0건 허용. 경력기술서(09-01, FE 전용): 경력 ≥1건일 때만 노출, [attachment](attachment.md) API를 `sectionType=CAREER`+`attachmentType=CAREER_DESCRIPTION`(섹션당 1건)으로 사용. 경력 저장 성공 후 파일을 처리하며 교체 순서는 **새 파일 업로드 성공 → 기존 파일 삭제**, 경력 0건이면 첨부도 삭제. ({FE}/views/applicant/application/sections/CareerSection.vue — saveCareerDescription)
- 자격증 🟢: 행 `{ certificateName, issuingOrganization, acquiredDate, certificateNumber, expiredDate, scoreOrGrade, sortOrder }`(응답 + `certificateId`). 0건 허용.
- 어학 🟢: 행 `{ languageCode, languageName, testCode, testName, scoreOrGrade, conversationalAbility, examDate, expiredDate, issuingOrganization, registrationNumber, sortOrder }`(응답 + `languageId`). 코드 선택(08-28): `languageCode`=공통코드 `LANGUAGE_TYPE`, `testCode`=`LANGUAGE_TEST_{languageCode}`, 이름 필드는 표시명 스냅샷(필수). 코드는 서버 미검증·시드 없음. 코드 `ETC`면 FE가 자유입력을 이름에 넣고 `ETC` 옵션을 목록 끝에 붙인다. 표시·PDF·검색은 이름, 집계는 코드 기준. `registrationNumber`(09-01 🟢): 선택, 파기 대상, 관리자 응답 미포함. FE는 회화능력 입력을 없애 신규 저장분 `conversationalAbility`=null(서버 필드·관리자 필터 `languageLevel`·PDF는 유지).
- 병역 🟢: FE `MilitarySection.vue`가 `applicationMilitaryApi`로 GET·POST를 호출한다. 필드 `{ militarySubjectType, serviceType, militaryBranch, rank, serviceStartDate, serviceEndDate, nonServiceReason }`(응답 + `militaryId`, 미저장이면 `data: null`). 대상구분 `SUBJECT`(미필)/`NOT_SUBJECT`/`COMPLETED`(군필)/`EXEMPTED`(면제)(06-29 `NOT_APPLICABLE` 제거, `exemptionReason`→`nonServiceReason`). 사유는 민감정보 — 관리자 응답 `nonServiceReasonMasked`(`***`), PDF 라벨 "병역사유", 파기 대상.
- 수상 🟢: 행 `{ awardName, awardingOrganization, awardDate, description, sortOrder }`(+ `awardId`). 0건 허용.
- 공백기간 🟢: 행 `{ startDate, endDate, gapType, reason, description, sortOrder }`(+ `gapPeriodId`). `gapType` `EDUCATION`/`CAREER`/`OTHER`(FE 라벨 하드코딩). "해당 사항 없음" 체크박스는 영속화가 없어 주석 처리.
- 질문 🟢: `GET /questions` 행 `{ questionId, questionText, helperText, category, answerType, required, minLength, maxLength, sortOrder, answerId, answerText, updatedAt }` = 공고 질문([question](question.md)) + 본인 답변. `POST /answers`는 전체 교체, `answerText` ≤5000·null 허용(부분 임시저장). 필수·`minLength`는 제출 시(서버+FE) 강제([application](application.md) "최종 제출 검증").

## 규칙·불변식

- 섹션 접근·저장 가능 조건·`useXxx` 사용 여부는 [application](application.md) "섹션 접근"을 따른다.
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

**프론트** ({FE}/views/applicant/application/sections/)
- 로드 성공 전·실패 시 저장 금지(빈 목록 전체 교체로 서버 데이터 삭제 방지), 조회·저장 성공 직후 `markSynced()`. ({FE}/views/applicant/application/useSectionDraftState.ts — assertLoaded)
- 첨부 업로드: 증명사진은 기본정보 섹션(`attachmentType=ETC`, `sectionType=BASIC_INFO`), 경력기술서는 경력 섹션이 [attachment](attachment.md) API로 올린다.
- **학기별 성적은 신입 공고만**: `formPage.postingType === 'PUBLIC_RECRUITMENT'`일 때만 학기 성적 표를 보인다. 경력·레거시는 전체 평점만. 분기는 FE에만 있고 서버는 `postingType`으로 막지 않는다(고졸 학기 금지만). 고졸 행은 `semesterGrades=[]`로 보낸다. ({FE}/views/applicant/application/sections/EducationSection.vue — isPostingPUBLIC·buildPayload)
- 섹션 `validateBeforeSubmit`: `section.required`면 ≥1건(수상·자격증·어학·공백기간·경력), 질문은 `required`·`minLength`·`maxLength`.
- 저장 실패 catch에서 `logClientEvent`로 수동 로깅한다([client-event-log](client-event-log.md)).

## 변경 레시피

### 섹션에 입력 필드 추가 (예: 학력)
1. `{BE}/domain/entity/ApplicationEducation.java` 필드·`create` 인자 추가(`ddl-auto: update`, 컬럼 삭제·제약 변경은 `recruit_back/recruit_backend/docs/ops/`에 수동 DDL).
2. `{BE}/dto/request/EducationRequest.java`·`{BE}/dto/response/EducationResponse.java` 필드 추가(축약 생성자 쓰는 테스트 확인).
3. `{BE}/service/ApplicationEducationService.java` `validateRequest`·`toEducation` 수정. 제출 필수면 [application](application.md) "제출 필수 규칙 추가·변경" 레시피. PII면 파기 `ApplicationPiiPurgeService`([privacy-audit](privacy-audit.md)), 관리자 조회(`AdminApplicationSectionService`)·PDF·엑셀은 [admin-application](admin-application.md).
4. `{BT}/service/ApplicationEducationServiceTest.java`·`{BT}/controller/ApplicationEducationControllerTest.java` 케이스 추가·실행.
5. FE: `{FE}/types/application/sections/education.ts` → `EducationSection.vue`(`buildPayload`·`validate`·`:disabled="!editable"`), `npm run type-check`.
6. 카드 API 상세·규칙 표 갱신, `node tools/check-docs.mjs`.

## 검증

백엔드(`recruit_back/recruit_backend/`에서). 섹션 1종(`Education`을 `BasicInfo`·`Career`·`Certificate`·`Language`·`Military`·`Award`·`GapPeriod`·`Answer`로 바꿔 쓴다. 컨트롤러·서비스 테스트가 함께 돈다. `BasicInfo`는 암호화 테스트도 포함):

```powershell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "*.ApplicationEducation*" --no-daemon
```

```bash
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "*.ApplicationEducation*" --no-daemon
```

섹션 접근·제출 검증을 함께 건드렸으면 [application](application.md) 코어 테스트도 돌린다.

프론트(`recruit_front/`에서):

```bash
npm run type-check
npx vitest run src/views/applicant/application/__tests__/useSectionDraftState.spec.ts
```

## 함정·결정

- 86d12c9: 모달은 teleport로 `ApplicationFormView`의 `fieldset` 밖이라 모달 안 일반 버튼엔 `:disabled="!editable"`를 직접 단다(`EducationSection.vue` 학기 추가·삭제).
- 8d7485d: 로드 전·실패 상태 저장이 빈 목록 전체 교체로 서버 데이터를 지우던 결함 → `assertLoaded`. 첨부 교체는 "업로드 성공 후 기존 삭제"(반대면 업로드 실패 시 유실).
- a630a15: 대학 학교 검색은 학교명 완전일치(부분검색 불가) → [master-data](master-data.md).
- 섹션 리포지토리는 제출 검증·완성도([application](application.md)), 관리자 조회·엑셀([admin-application](admin-application.md)), 퍼널([statistics](statistics.md))도 쓴다. 시그니처 변경 시 그 카드 검증도 돌린다.
- ADR `recruit_back/recruit_backend/docs/adr/0004-school-optional-application-level-link.md`: 학교 연결은 선택·FK 없음, `schoolName`이 스냅샷. 현 구현은 `schoolCode`+`schoolSource`(ADR의 `schoolId`는 옛 이름).
- ADR `recruit_back/recruit_backend/docs/adr/0003-commoncode-additive-no-enum-migration.md`: 공통코드는 추가만, enum을 코드로 옮기지 않는다(병역 군별·계급·복무형태, `DayNightType`·`CampusType` 유지). 언어·시험·전공구분·회화능력 코드값은 서버 미검증 문자열.
