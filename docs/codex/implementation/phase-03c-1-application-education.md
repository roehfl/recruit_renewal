# Phase 03c-1 - Application Education

## Phase 이름

Phase 03c-1: Education + EducationSemesterGrade vertical slice

## 목적

`JobApplication` 하위 상세 섹션 중 학력사항과 학기별 성적을 먼저 구현한다. 지원자는 본인 지원서의 학력/성적 목록을 조회할 수 있고, 지원서가 `DRAFT` 상태일 때 전체 replace 방식으로 저장할 수 있다.

## 구현 범위

- 학력/성적 enum 추가
- `ApplicationEducation`, `ApplicationEducationSemesterGrade` Entity 추가
- 학력/성적 Repository 추가
- 지원자 학력 replace 저장/조회 DTO 추가
- `ApplicationEducationService` 추가
- `ApplicationEducationController` 추가
- `GET /applications/{applicationId}/educations`
- `POST /applications/{applicationId}/educations`
- `ApplicationFormConfig.useEducation=false` 저장 차단
- 타인 지원서 접근 차단
- `DRAFT` 상태에서만 저장 허용
- replace 저장 시 기존 SemesterGrade 선삭제 후 Education 삭제
- validation 및 invalid enum 요청을 `ApiResponse.fail` 형식으로 처리

## 구현하지 않은 범위

- Career, Certificate, Language, Military, Award, GapPeriod, Attachment
- StageResult
- 자기소개서/질문답변
- 관리자 상세 섹션 API
- `ApplicationSubmitValidator`
- submit 시 학력 필수 검증 연동
- 개별 학력/성적 삭제 API
- `PUT`, HTTP `DELETE`
- SecurityConfig 권한 정책 변경

## 변경 파일 목록

### 코드 변경

- `src/main/java/com/shinyoung/recruit/controller/ApplicationEducationController.java`
- `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationEducation.java`
- `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationEducationSemesterGrade.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationEducationRepository.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationEducationSemesterGradeRepository.java`
- `src/main/java/com/shinyoung/recruit/dto/request/EducationReplaceRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/EducationRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/SemesterGradeRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/response/EducationResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/SemesterGradeResponse.java`
- `src/main/java/com/shinyoung/recruit/enumeration/EducationLevel.java`
- `src/main/java/com/shinyoung/recruit/enumeration/GraduationStatus.java`
- `src/main/java/com/shinyoung/recruit/enumeration/DayNightType.java`
- `src/main/java/com/shinyoung/recruit/enumeration/CampusType.java`
- `src/main/java/com/shinyoung/recruit/exception/GlobalExceptionHandler.java`
- `src/main/java/com/shinyoung/recruit/service/ApplicationEducationService.java`

### 테스트 변경

- `src/test/java/com/shinyoung/recruit/service/ApplicationEducationServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/ApplicationEducationControllerTest.java`

### 문서 변경

- `docs/codex/implementation/phase-03c-1-application-education.md`
- `docs/codex/design/phase-03-application-design.md`
- `docs/codex/design/phase-03c-application-detail-design.md`
- `docs/codex/07-implementation-history.md`

## 신규 클래스 목록

- `ApplicationEducation`
- `ApplicationEducationSemesterGrade`
- `ApplicationEducationRepository`
- `ApplicationEducationSemesterGradeRepository`
- `EducationReplaceRequest`
- `EducationRequest`
- `SemesterGradeRequest`
- `EducationResponse`
- `SemesterGradeResponse`
- `EducationLevel`
- `GraduationStatus`
- `DayNightType`
- `CampusType`
- `ApplicationEducationService`
- `ApplicationEducationController`
- `ApplicationEducationServiceTest`
- `ApplicationEducationControllerTest`

## 수정 클래스 목록

- `GlobalExceptionHandler`

## 클래스별 설명

| 구분 | 패키지 | 클래스 | 역할 | 주요 필드/메서드 | 연관 클래스 | 비고 |
|---|---|---|---|---|---|---|
| Entity | `com.shinyoung.recruit.domain.entity` | `ApplicationEducation` | 지원서 학력 row | `jobApplication`, `educationLevel`, `schoolName`, `graduationStatus`, `sortOrder`, `create` | `JobApplication` | `JobApplication` 컬렉션 추가 없음 |
| Entity | `com.shinyoung.recruit.domain.entity` | `ApplicationEducationSemesterGrade` | 학력별 학기 성적 row | `education`, `schoolYear`, `semester`, `gradePoint`, `maxGradePoint`, `create` | `ApplicationEducation` | cascade/orphanRemoval 미사용 |
| Repository | `com.shinyoung.recruit.domain.repository` | `ApplicationEducationRepository` | 학력 조회/삭제 | `findByJobApplicationIdOrderBySortOrderAscIdAsc`, `deleteByJobApplicationId` | `ApplicationEducation` | replace 저장용 명시 삭제 |
| Repository | `com.shinyoung.recruit.domain.repository` | `ApplicationEducationSemesterGradeRepository` | 성적 조회/삭제 | `findByEducationIdInOrderBySchoolYearAscSemesterAscIdAsc`, `deleteByEducationIdIn` | `ApplicationEducationSemesterGrade` | Education 삭제 전 선삭제 |
| Enum | `com.shinyoung.recruit.enumeration` | `EducationLevel` | 학력 구분 | `HIGH_SCHOOL`, `COLLEGE`, `UNIVERSITY`, `MASTER`, `DOCTOR` | `ApplicationEducation` | DB `STRING` 저장 |
| Enum | `com.shinyoung.recruit.enumeration` | `GraduationStatus` | 졸업 상태 | `GRADUATED`, `EXPECTED`, `ENROLLED`, `LEAVE_OF_ABSENCE`, `DROPPED_OUT`, `COMPLETED` | `ApplicationEducation` | DB `STRING` 저장 |
| Enum | `com.shinyoung.recruit.enumeration` | `DayNightType` | 주야간 구분 | `DAY`, `NIGHT`, `CYBER`, `UNKNOWN` | `ApplicationEducation` | 선택 필드 |
| Enum | `com.shinyoung.recruit.enumeration` | `CampusType` | 캠퍼스 구분 | `MAIN`, `BRANCH`, `UNKNOWN` | `ApplicationEducation` | 선택 필드 |
| Request DTO | `com.shinyoung.recruit.dto.request` | `EducationReplaceRequest` | 학력 replace 요청 | `educations` | `EducationRequest` | null은 400, 빈 목록은 허용 |
| Request DTO | `com.shinyoung.recruit.dto.request` | `EducationRequest` | 학력 row 요청 | 학력 필드, `semesterGrades` | `SemesterGradeRequest` | 필수값 Bean Validation |
| Request DTO | `com.shinyoung.recruit.dto.request` | `SemesterGradeRequest` | 학기 성적 row 요청 | `schoolYear`, `semester`, `gradePoint`, `maxGradePoint` | `ApplicationEducationService` | 교차 검증은 Service |
| Response DTO | `com.shinyoung.recruit.dto.response` | `EducationResponse` | 학력 응답 | 학력 필드, `semesterGrades`, `from` | `ApplicationEducation` | 개인정보 원문 미포함 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `SemesterGradeResponse` | 성적 응답 | 성적 필드, `from` | `ApplicationEducationSemesterGrade` | 성적 정렬 후 반환 |
| Service | `com.shinyoung.recruit.service` | `ApplicationEducationService` | 학력 조회/replace 저장 | `getEducations`, `replaceEducations` | `JobApplicationRepository`, 학력 Repository | 상태/기간/config/소유자 검증 |
| Controller | `com.shinyoung.recruit.controller` | `ApplicationEducationController` | 지원자 학력 API | `getEducations`, `replaceEducations` | `CurrentApplicantService`, `ApplicationEducationService` | 기존 인증 helper 재사용 |
| Exception Handler | `com.shinyoung.recruit.exception` | `GlobalExceptionHandler` | 요청 파싱 오류 응답 통일 | `handleHttpMessageNotReadable` | `ApiResponse` | 잘못된 enum 값 400 처리 |
| Test | `com.shinyoung.recruit.service` | `ApplicationEducationServiceTest` | Service 규칙 검증 | 저장/조회/replace/검증 실패 | `ApplicationEducationService` | 고정 Clock 사용 |
| Test | `com.shinyoung.recruit.controller` | `ApplicationEducationControllerTest` | API 계약 검증 | path/method/응답 포맷 | `ApplicationEducationController` | PUT/DELETE 미지원 확인 |

## Entity 관계 요약

- `ApplicationEducation` N:1 `JobApplication`
- `ApplicationEducationSemesterGrade` N:1 `ApplicationEducation`
- `JobApplication`에는 `List<ApplicationEducation>`을 추가하지 않았다.
- `ApplicationEducation`에는 `List<ApplicationEducationSemesterGrade>`를 추가하지 않았다.
- cascade/orphanRemoval은 사용하지 않는다.
- replace 저장 시 기존 `ApplicationEducationSemesterGrade`를 먼저 삭제하고, 이후 기존 `ApplicationEducation`을 삭제한다.

## API 목록

| Method | Path | 목적 | Request | Response |
|---|---|---|---|---|
| GET | `/applications/{applicationId}/educations` | 내 지원서 학력/성적 목록 조회 | 없음 | `ApiResponse<List<EducationResponse>>` |
| POST | `/applications/{applicationId}/educations` | 내 지원서 학력/성적 replace 저장 | `EducationReplaceRequest` | `ApiResponse<List<EducationResponse>>` |

## Request/Response DTO 구조

`EducationReplaceRequest`

- `educations`: `List<EducationRequest>`, null 불가, 빈 목록 허용

`EducationRequest`

- `educationLevel`
- `schoolName`
- `majorName`
- `degreeName`
- `admissionDate`
- `graduationDate`
- `graduationStatus`
- `dayNightType`
- `campusType`
- `transfer`
- `countryCode`
- `sortOrder`
- `semesterGrades`

`SemesterGradeRequest`

- `schoolYear`
- `semester`
- `earnedCredits`
- `gradePoint`
- `maxGradePoint`
- `majorGradePoint`
- `majorMaxGradePoint`

`EducationResponse`와 `SemesterGradeResponse`는 위 요청 필드에 저장된 id를 포함해 반환한다.

## replace 저장 정책

1. `applicationId`와 `applicantId`로 본인 지원서를 조회한다.
2. 저장 가능 상태, 공고 상태, 접수기간, `useEducation`을 검증한다.
3. 요청 목록의 필수값, `sortOrder` 중복, 성적 교차 필드를 검증한다.
4. 기존 Education 목록을 조회한다.
5. 기존 Education id가 있으면 SemesterGrade를 먼저 삭제한다.
6. 기존 Education을 삭제한다.
7. 새 Education을 저장한다.
8. 저장된 Education을 참조해 SemesterGrade를 저장한다.
9. `sortOrder ASC, id ASC`, 성적은 `schoolYear ASC, semester ASC, id ASC`로 다시 조회해 응답한다.

## useEducation 연동 정책

- `ApplicationFormConfig.useEducation=true`일 때만 저장 가능하다.
- `useEducation=false`이면 `InvalidJobApplicationException`으로 400 처리한다.
- 조회는 저장 차단과 별개로 본인 지원서이면 허용한다.
- submit 시 최소 1개 필수 검증은 아직 연결하지 않았다. Phase 03c-7에서 `ApplicationSubmitValidator`로 통합한다.

## 상태별 수정 정책

| JobApplicationStatus | 조회 | 저장 |
|---|---|---|
| `DRAFT` | 가능 | 가능 |
| `SUBMITTED` | 가능 | 불가 |
| `WITHDRAWN` | 가능 | 불가 |

저장은 추가로 `JobPosting.status=PUBLISHED`이고 접수기간 내여야 한다.

## 성적 검증 정책

- `EducationLevel.HIGH_SCHOOL`은 `semesterGrades`가 비어 있어야 한다.
- `gradePoint <= maxGradePoint`
- `majorGradePoint`가 있으면 `majorMaxGradePoint`가 필요하다.
- `majorGradePoint <= majorMaxGradePoint`
- `earnedCredits`는 null 또는 0 이상이다.
- `admissionDate`와 `graduationDate`가 모두 있으면 `admissionDate <= graduationDate`이어야 한다.
- 이번 Phase에서는 대학교/석사/박사 성적 최소 1개를 강제하지 않는다.

## 개인정보/응답 제한 정책

학력/성적 응답에는 지원자 CI, ciHash, 전화번호, 이메일, 주소, 비밀번호 같은 민감정보를 포함하지 않는다. 학교명/전공/성적은 지원서 상세 정보로 관리자 상세 확장 시 마스킹 또는 노출 범위를 별도 검토한다.

## 테스트 목록

- DRAFT 지원서에 학력/성적 replace 저장 성공
- 저장 후 조회 성공
- 여러 Education 정렬 확인
- SemesterGrade 정렬 확인
- replace 저장 시 기존 Education/SemesterGrade 삭제 확인
- 빈 `educations` 목록 replace 저장 허용 및 기존 데이터 삭제
- `useEducation=false` 저장 실패
- `SUBMITTED` 상태 저장 실패
- `WITHDRAWN` 상태 저장 실패
- 타인 지원서 조회/저장 실패
- 접수기간 전/후 저장 실패
- `PUBLISHED`가 아닌 JobPosting 저장 실패
- Education 필수값 누락 실패
- `gradePoint > maxGradePoint` 실패
- `majorGradePoint`만 있고 `majorMaxGradePoint` 없으면 실패
- HIGH_SCHOOL 성적 입력 실패
- `sortOrder` 중복 실패
- Controller GET 성공
- Controller POST 성공
- validation 실패 400 + `ApiResponse.fail`
- 잘못된 enum 값 400 + `ApiResponse.fail`
- `useEducation=false` 저장 실패 응답
- 타인 지원서 접근 404 응답
- `SUBMITTED` 저장 실패 400 응답
- PUT/DELETE 미지원 확인

## 실행한 테스트 명령

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationEducationServiceTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.ApplicationEducationControllerTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test
```

## 테스트 결과

- `ApplicationEducationServiceTest`: 성공
- `ApplicationEducationControllerTest`: 성공
- 전체 `clean test`: 성공

## 남은 이슈

- submit 시 `useEducation=true`일 때 Education 최소 1개 필수 검증은 아직 연결하지 않았다.
- 관리자 Application 상세에 학력/성적 섹션을 포함하지 않았다.
- 학교명/전공/성적의 관리자 노출/마스킹 정책은 관리자 상세 섹션 확장 시 재검토한다.
- 상세 섹션 공통 helper는 Education vertical slice 안에서 최소한으로만 구현했다. 다음 섹션에서 반복이 확인되면 공통화 범위를 다시 판단한다.
- 성적/학점 `BigDecimal` 컬럼의 `precision`, `scale`은 아직 명시하지 않았다. 운영 DB schema 관리 기준 확정 시 명시 여부를 검토한다.
- Career 등 다음 섹션에서 `findApplication`, DRAFT/PUBLISHED/접수기간/config enabled 검증이 반복되면 `ApplicationSectionAccessService` 또는 `ApplicationDetailPolicyService` 같은 최소 공통 helper 추출을 검토한다.

## 다음 Phase 추천

다음 Phase는 Career 구현으로 가는 것이 자연스럽다. 다만 Career는 "미입력"과 "해당 없음" 구분이 필요하므로 `careerApplicable`, `hasCareer`, `careerType` 중 어떤 값을 둘지 먼저 확정해야 한다.
