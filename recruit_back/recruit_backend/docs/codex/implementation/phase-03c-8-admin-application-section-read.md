# Phase 03c-8 - Admin Application Detail Section Read API

## Phase 이름

Phase 03c-8: Admin Application Detail Section Read API 확장

## 목적

Phase 03b-1에서 구현한 관리자 Application 루트 목록/상세 조회를 유지하면서, 상세 섹션은 섹션별 lazy read-only API로 확장한다. 관리자는 특정 지원서의 학력, 경력, 자격, 어학, 병역, 수상, 공백기간, 첨부 metadata를 조회할 수 있고, 기존 지원자 상세 섹션 저장 API와 최종제출 검증 정책은 변경하지 않는다.

## 구현 범위

- 관리자 상세 섹션 조회 Controller 추가
- 관리자 상세 섹션 read-only Service 추가
- 관리자 전용 상세 섹션 응답 DTO 추가
- `applicationId` 존재 여부 검증
- 상세 섹션별 Repository 기반 조회
- 목록형 섹션 빈 배열 응답
- Career profile 미저장 시 `NOT_SELECTED + []` 응답
- Military 미저장 시 `data=null` 응답
- 자격번호와 병역 면제 사유 마스킹
- Attachment `storedFileName`, `storagePath`, 다운로드 URL 비노출
- `GET` API만 추가
- Service/Controller 테스트 추가
- 설계 문서, 구현 이력, HTML report 갱신

## 미구현 범위

- 지원자 상세 섹션 저장 API 변경
- 지원자 상세 섹션 응답 DTO 변경
- 상세 섹션 Entity 구조 변경
- `JobApplication` 상세 섹션 컬렉션 추가
- cascade/orphanRemoval 추가
- `ApplicationSubmitValidator` 정책 변경
- `JobApplicationService.submit()` 변경
- 관리자 상세 aggregate 단일 API
- 관리자 수정/삭제 command
- 관리자 파일 다운로드 API
- Attachment 실제 파일 업로드/다운로드/저장소 연동
- `MultipartFile`, multipart/form-data API
- StageResult
- 자기소개서/질문답변 도메인
- `PUT`, HTTP `DELETE`, `POST` command
- SecurityConfig 대규모 변경
- CommonCode

## 변경 파일 목록

### 코드 변경

- `src/main/java/com/shinyoung/recruit/controller/AdminApplicationSectionController.java`
- `src/main/java/com/shinyoung/recruit/service/AdminApplicationSectionService.java`
- `src/main/java/com/shinyoung/recruit/dto/response/AdminEducationResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/AdminSemesterGradeResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/AdminCareerResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/AdminCareerItemResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/AdminCertificateResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/AdminLanguageResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/AdminMilitaryResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/AdminAwardResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/AdminGapPeriodResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/AdminAttachmentResponse.java`

### 테스트 변경

- `src/test/java/com/shinyoung/recruit/service/AdminApplicationSectionServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/AdminApplicationSectionControllerTest.java`

### 문서 변경

- `docs/codex/implementation/phase-03c-8-admin-application-section-read.md`
- `docs/codex/design/phase-03-application-design.md`
- `docs/codex/design/phase-03c-application-detail-design.md`
- `docs/codex/07-implementation-history.md`
- `docs/codex/reports/phase-03c-8-admin-application-section-read.html`

## 신규 클래스 목록

- `AdminApplicationSectionController`
- `AdminApplicationSectionService`
- `AdminEducationResponse`
- `AdminSemesterGradeResponse`
- `AdminCareerResponse`
- `AdminCareerItemResponse`
- `AdminCertificateResponse`
- `AdminLanguageResponse`
- `AdminMilitaryResponse`
- `AdminAwardResponse`
- `AdminGapPeriodResponse`
- `AdminAttachmentResponse`
- `AdminApplicationSectionServiceTest`
- `AdminApplicationSectionControllerTest`

## 수정 클래스 목록

- 없음

## 클래스별 설명

| 구분 | 패키지 | 클래스 | 책임 | 주요 필드/메서드 | 관련 클래스 | 구현 메모 |
|---|---|---|---|---|---|---|
| Controller | `com.shinyoung.recruit.controller` | `AdminApplicationSectionController` | 관리자 상세 섹션 GET API | `getEducations`, `getCareers`, `getCertificates`, `getLanguages`, `getMilitary`, `getAwards`, `getGapPeriods`, `getAttachments` | `AdminApplicationSectionService`, `ApiResponse` | 조회 전용이며 `GET`만 제공 |
| Service | `com.shinyoung.recruit.service` | `AdminApplicationSectionService` | 관리자 상세 섹션 read-only 조회 | 섹션별 `get...`, `validateApplicationExists`, masking private method | 상세 섹션 Repository, `JobApplicationRepository` | 지원자 Service를 재사용하지 않음 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `AdminEducationResponse` | 관리자 학력 row 응답 | 학력 필드, `semesterGrades`, `from` | `ApplicationEducation`, `AdminSemesterGradeResponse` | 지원자 DTO와 분리 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `AdminSemesterGradeResponse` | 관리자 학기 성적 응답 | 학년/학기/학점/성적 필드, `from` | `ApplicationEducationSemesterGrade` | `schoolYear ASC, semester ASC, id ASC` 조회 결과 사용 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `AdminCareerResponse` | 관리자 경력 섹션 응답 | `careerType`, `careers` | `AdminCareerItemResponse` | profile 없으면 `NOT_SELECTED + []` |
| Response DTO | `com.shinyoung.recruit.dto.response` | `AdminCareerItemResponse` | 관리자 경력 row 응답 | 회사/부서/직위/기간/업무 필드, `from` | `ApplicationCareer` | 지원자 DTO와 분리 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `AdminCertificateResponse` | 관리자 자격 row 응답 | `certificateNumberMasked` 포함 | `ApplicationCertificate` | `certificateNumber` 원문 비노출 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `AdminLanguageResponse` | 관리자 어학 row 응답 | 어학명/시험명/점수/등급/일자 필드, `from` | `ApplicationLanguage` | 지원자 DTO와 분리 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `AdminMilitaryResponse` | 관리자 병역 응답 | `exemptionReasonMasked` 포함 | `ApplicationMilitary` | `exemptionReason` 원문 비노출 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `AdminAwardResponse` | 관리자 수상 row 응답 | 수상명/기관/일자/설명 필드, `from` | `ApplicationAward` | 상세 섹션 조회에서만 설명 포함 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `AdminGapPeriodResponse` | 관리자 공백기간 row 응답 | 기간/유형/사유/설명 필드, `from` | `ApplicationGapPeriod` | 상세 섹션 조회에서만 사유/설명 포함 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `AdminAttachmentResponse` | 관리자 첨부 metadata 응답 | 파일명/contentType/fileSize 필드, `from` | `ApplicationAttachment` | `storedFileName`, `storagePath` 비노출 |
| Test | `com.shinyoung.recruit.service` | `AdminApplicationSectionServiceTest` | 관리자 상세 섹션 Service 검증 | 정렬, 빈 응답, 마스킹, 상태 무관 조회, not found | `AdminApplicationSectionService` | 실제 Repository 기반 통합 테스트 |
| Test | `com.shinyoung.recruit.controller` | `AdminApplicationSectionControllerTest` | 관리자 상세 섹션 API 계약 검증 | 8개 GET, 빈 응답, 민감 필드 비노출, method 미지원 | `AdminApplicationSectionController` | MockMvc 기반 |

## API 목록

| Method | Path | 목적 | Request | Response |
|---|---|---|---|---|
| GET | `/admin/applications/{applicationId}/educations` | 관리자 학력/성적 조회 | 없음 | `ApiResponse<List<AdminEducationResponse>>` |
| GET | `/admin/applications/{applicationId}/careers` | 관리자 경력 조회 | 없음 | `ApiResponse<AdminCareerResponse>` |
| GET | `/admin/applications/{applicationId}/certificates` | 관리자 자격 조회 | 없음 | `ApiResponse<List<AdminCertificateResponse>>` |
| GET | `/admin/applications/{applicationId}/languages` | 관리자 어학 조회 | 없음 | `ApiResponse<List<AdminLanguageResponse>>` |
| GET | `/admin/applications/{applicationId}/military` | 관리자 병역 조회 | 없음 | `ApiResponse<AdminMilitaryResponse>` |
| GET | `/admin/applications/{applicationId}/awards` | 관리자 수상 조회 | 없음 | `ApiResponse<List<AdminAwardResponse>>` |
| GET | `/admin/applications/{applicationId}/gap-periods` | 관리자 공백기간 조회 | 없음 | `ApiResponse<List<AdminGapPeriodResponse>>` |
| GET | `/admin/applications/{applicationId}/attachments` | 관리자 첨부 metadata 조회 | 없음 | `ApiResponse<List<AdminAttachmentResponse>>` |

## 섹션별 응답 DTO 구조

| 섹션 | DTO | 주요 필드 | 빈 응답 정책 |
|---|---|---|---|
| Education | `AdminEducationResponse` | `educationId`, 학력/학교/전공/졸업/정렬, `semesterGrades` | `[]` |
| SemesterGrade | `AdminSemesterGradeResponse` | `semesterGradeId`, `schoolYear`, `semester`, 학점/성적 | Education 내부 빈 배열 |
| Career | `AdminCareerResponse` | `careerType`, `careers` | profile 없으면 `NOT_SELECTED`, `careers=[]` |
| Certificate | `AdminCertificateResponse` | 자격명/기관/취득일/만료일/등급, `certificateNumberMasked` | `[]` |
| Language | `AdminLanguageResponse` | 언어/시험/점수/등급/시험일/만료일/기관 | `[]` |
| Military | `AdminMilitaryResponse` | 병역 유형/복무 구분/군별/계급/기간, `exemptionReasonMasked` | `data=null` |
| Award | `AdminAwardResponse` | 수상명/기관/일자/설명/정렬 | `[]` |
| GapPeriod | `AdminGapPeriodResponse` | 시작일/종료일/유형/사유/설명/정렬 | `[]` |
| Attachment | `AdminAttachmentResponse` | 첨부 유형/섹션/원본파일명/contentType/fileSize/정렬 | `[]` |

## 정렬 정책

| 섹션 | 정렬 |
|---|---|
| Education | `sortOrder ASC, id ASC` |
| SemesterGrade | `schoolYear ASC, semester ASC, id ASC` |
| Career | `sortOrder ASC, id ASC` |
| Certificate | `sortOrder ASC, id ASC` |
| Language | `sortOrder ASC, id ASC` |
| Award | `sortOrder ASC, id ASC` |
| GapPeriod | `sortOrder ASC, id ASC` |
| Attachment | `sortOrder ASC, id ASC` |

## 마스킹/응답 제한 정책

- `AdminCertificateResponse`는 `certificateNumber` 원문을 제공하지 않고 `certificateNumberMasked`만 제공한다.
- 자격번호 마스킹은 null/blank이면 null, 값이 있으면 앞 2~3자만 남기고 `***`를 붙인다.
- `AdminMilitaryResponse`는 `exemptionReason` 원문을 제공하지 않고 `exemptionReasonMasked`만 제공한다.
- 면제 사유 마스킹은 null/blank이면 null, 값이 있으면 `"***"`를 반환한다.
- `AdminAttachmentResponse`는 `storedFileName`, `storagePath`, 다운로드 URL을 제공하지 않는다.
- `originalFileName`은 metadata 표시용으로 제공한다.
- GapPeriod `reason`, `description`과 Award `description`은 관리자 상세 섹션 조회 목적상 포함하되, 관리자 목록 응답에는 포함하지 않는다.

## read-only 정책

- 관리자 상세 섹션 API는 모두 `GET`만 제공한다.
- Controller에는 `@PostMapping`, `@PutMapping`, `@DeleteMapping`을 추가하지 않았다.
- Service는 `@Transactional(readOnly = true)`를 사용한다.
- 지원자 상세 섹션 Service의 본인/상태/접수기간/저장 정책을 재사용하지 않는다.
- 관리자 조회는 `DRAFT`, `SUBMITTED`, `WITHDRAWN` 상태 모두 허용한다.
- 관리자 조회는 `JobPosting.status`, 접수기간, `ApplicationFormConfig.useXxx`와 무관하게 허용한다.

## 관리자 aggregate API 미구현 사유

이번 Phase의 관리자 화면 구성 방향은 Phase 03b-1 루트 상세 API와 섹션별 lazy API 조합이다. 모든 상세 섹션을 한 번에 반환하는 aggregate API는 응답 크기, 민감정보 마스킹/권한 분기, 파일 다운로드 권한, StageResult/질문답변 확장 정책이 섞일 수 있으므로 이번 Phase에서 구현하지 않았다.

## 테스트 목록

- `AdminApplicationSectionServiceTest`
  - Education + SemesterGrade 조회 성공 및 정렬 확인
  - 빈 목록형 섹션은 빈 배열 반환
  - Military 미저장 시 null 반환
  - CareerProfile 미저장 시 `NOT_SELECTED + []` 반환
  - CareerProfile + Career row 조회 성공
  - NEWCOMER + 빈 목록 조회 성공
  - Certificate 자격번호 마스킹 확인
  - Language 조회 및 정렬 확인
  - Military 면제 사유 마스킹 확인
  - Award, GapPeriod, Attachment 조회 및 정렬 확인
  - 존재하지 않는 applicationId 조회 시 `JobApplicationNotFoundException`
  - DRAFT/SUBMITTED/WITHDRAWN 상태 모두 조회 가능
- `AdminApplicationSectionControllerTest`
  - 8개 관리자 상세 섹션 GET API 성공
  - 빈 섹션 응답 shape 확인
  - 존재하지 않는 applicationId 404 + `ApiResponse.fail`
  - Attachment `storedFileName`, `storagePath` 비노출
  - Military `exemptionReason` 원문 비노출
  - Certificate `certificateNumber` 원문 비노출
  - 모든 섹션 대표 path에서 PUT/DELETE/POST 미지원 확인

## 테스트 명령

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.AdminApplicationSectionServiceTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.AdminApplicationSectionControllerTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.AdminApplicationControllerTest --tests com.shinyoung.recruit.service.ApplicationSubmitValidatorTest --tests com.shinyoung.recruit.service.JobApplicationServiceTest --tests com.shinyoung.recruit.controller.ApplicationControllerTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationEducationServiceTest --tests com.shinyoung.recruit.controller.ApplicationEducationControllerTest --tests com.shinyoung.recruit.service.ApplicationCareerServiceTest --tests com.shinyoung.recruit.controller.ApplicationCareerControllerTest --tests com.shinyoung.recruit.service.ApplicationCertificateServiceTest --tests com.shinyoung.recruit.controller.ApplicationCertificateControllerTest --tests com.shinyoung.recruit.service.ApplicationLanguageServiceTest --tests com.shinyoung.recruit.controller.ApplicationLanguageControllerTest --tests com.shinyoung.recruit.service.ApplicationMilitaryServiceTest --tests com.shinyoung.recruit.controller.ApplicationMilitaryControllerTest --tests com.shinyoung.recruit.service.ApplicationAwardServiceTest --tests com.shinyoung.recruit.controller.ApplicationAwardControllerTest --tests com.shinyoung.recruit.service.ApplicationGapPeriodServiceTest --tests com.shinyoung.recruit.controller.ApplicationGapPeriodControllerTest --tests com.shinyoung.recruit.service.ApplicationAttachmentServiceTest --tests com.shinyoung.recruit.controller.ApplicationAttachmentControllerTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test
```

## 테스트 결과

- `AdminApplicationSectionServiceTest`: 성공
- `AdminApplicationSectionControllerTest`: 성공
- 기존 관리자 Application 루트 조회 테스트: 성공
- Application submit validator 및 지원자 Application API 회귀 테스트: 성공
- Education/Career/Certificate/Language/Military/Award/GapPeriod/Attachment 상세 섹션 회귀 테스트: 성공
- `./gradlew.bat clean test`: 성공

## 남은 이슈

- 실제 관리자 권한 검증은 아직 SecurityConfig에 추가하지 않았다. 운영 전 `/admin/applications/**`는 관리자 또는 채용담당자 권한으로 보호해야 한다.
- 자격번호와 면제 사유 원문 열람 권한이 필요하면 후속 보안/권한 Phase에서 별도 권한 또는 감사 로그와 함께 설계한다.
- Attachment 다운로드 권한, 파일 접근 감사 로그, 파일 저장소 연동은 후속 파일 Phase에서 처리한다.
- 관리자 상세 aggregate 단일 API는 아직 없다.
- 자기소개서/질문답변 도메인과 StageResult는 아직 구현하지 않았다.

## 다음 Phase 추천

다음 Phase는 자기소개서/질문답변 도메인 또는 StageResult 전 Application 상세 조회 범위 확정을 추천한다. 관리자 상세 섹션 read-only API는 마련되었으므로, 이후 민감정보 원문 열람 권한과 파일 다운로드 권한은 보안/파일 Phase에서 별도로 다루는 편이 안전하다.
