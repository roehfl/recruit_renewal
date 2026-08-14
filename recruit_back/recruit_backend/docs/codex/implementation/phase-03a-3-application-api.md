# Phase 03a-3 - Application API

## Phase Name

Phase 03a-3: Applicant ApplicationController/API/Test

## Purpose

Phase 03a-1/03a-2에서 구현한 `JobApplicationService`의 생성, 조회, 임시저장 수정, 제출, 철회 흐름을 지원자용 HTTP API로 연결한다. 이번 Phase는 API path, HTTP method, `ApiResponse<T>` 응답 포맷, validation/error 응답을 MockMvc 테스트로 고정하는 데 집중한다.

## Implemented Scope

- `ApplicationController` 추가
- `CurrentApplicantService` 추가
- `ApplicantRepository.findByLoginId` 추가
- `CustomUserDetails` userType 상수 추가
- 지원자 Application 생성/조회/수정/제출/철회 API 연결
- 공고별 내 지원서 조회 API 연결
- `ApplicationControllerTest` 추가
- `CustomUserDetailsTest` 추가
- Phase 03 설계/구현 문서와 구현 이력 갱신

## Not Implemented

- 관리자 Application API
- `GET /applications/me` 목록 API
- StageResult
- Education, Career, Certificate, Language, Military, Award, GapPeriod, Attachment
- Interview, Message, CommonCode
- 상세 섹션 필수값 검증
- SecurityConfig 대규모 변경
- `PUT` 또는 HTTP `DELETE` API

## Changed Files

### Code

- `src/main/java/com/shinyoung/recruit/controller/ApplicationController.java`
- `src/main/java/com/shinyoung/recruit/service/CurrentApplicantService.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/ApplicantRepository.java`
- `src/main/java/com/shinyoung/recruit/security/auth/CustomUserDetails.java`

### Test

- `src/test/java/com/shinyoung/recruit/controller/ApplicationControllerTest.java`
- `src/test/java/com/shinyoung/recruit/security/auth/CustomUserDetailsTest.java`

### Documentation

- `docs/codex/implementation/phase-03a-3-application-api.md`
- `docs/codex/implementation/phase-03a-2-application-commands.md`
- `docs/codex/design/phase-03-application-design.md`
- `docs/codex/07-implementation-history.md`

## New Classes

- `ApplicationController`
- `CurrentApplicantService`
- `ApplicationControllerTest`
- `CustomUserDetailsTest`

## Modified Classes

- `ApplicantRepository`
- `CustomUserDetails`

## Class-by-Class Explanation

| 구분 | 패키지 | 클래스 | 역할 | 주요 필드/메서드 | 연관 클래스 | 비고 |
|---|---|---|---|---|---|---|
| Controller | `com.shinyoung.recruit.controller` | `ApplicationController` | 지원자 Application HTTP API | `create`, `getApplication`, `updateDraft`, `submit`, `withdraw`, `getMyApplicationByJobPosting` | `JobApplicationService`, `CurrentApplicantService` | request validation과 `ApiResponse` wrapping만 담당 |
| Service Helper | `com.shinyoung.recruit.service` | `CurrentApplicantService` | 현재 로그인 지원자 식별 | `getCurrentApplicantId` | `CustomUserDetails`, `ApplicantRepository` | `userType=Applicant` 확인 후 `loginId`로 Applicant 조회 |
| Repository | `com.shinyoung.recruit.domain.repository` | `ApplicantRepository` | 지원자 조회 | `findByLoginId` | `Applicant` | 인증 principal과 Applicant 연결에 사용 |
| Auth | `com.shinyoung.recruit.security.auth` | `CustomUserDetails` | 인증 principal | `USER_TYPE_APPLICANT`, `USER_TYPE_EMPLOYEE`, `getUsername` | `Applicant`, `Employee` | `getUsername()`은 `loginId` 반환 정책 유지 |
| Test | `com.shinyoung.recruit.controller` | `ApplicationControllerTest` | Application API 계약 검증 | 생성/조회/수정/제출/철회/공고별 조회/validation/error/method 테스트 | `ApplicationController`, `JobApplicationService` | MockMvc 기반, 실 LDAP 미사용 |
| Test | `com.shinyoung.recruit.security.auth` | `CustomUserDetailsTest` | 인증 principal 정책 검증 | applicant/employee/ldap username, userType 테스트 | `CustomUserDetails` | `getUsername() == loginId` 전제 고정 |

## API List

| Method | Path | 목적 | Request | Response |
|---|---|---|---|---|
| POST | `/applications` | 지원서 생성 | `ApplicationCreateRequest` | `ApiResponse<Long>` |
| GET | `/applications/{applicationId}` | 내 지원서 상세 조회 | 없음 | `ApiResponse<ApplicationDetailResponse>` |
| POST | `/applications/{applicationId}` | DRAFT 지원서 임시저장 수정 | `ApplicationUpdateRequest` | `ApiResponse<Long>` |
| POST | `/applications/{applicationId}/submit` | 최종제출 | 없음 | `ApiResponse<Long>` |
| POST | `/applications/{applicationId}/withdraw` | 지원 철회 | 없음 | `ApiResponse<Long>` |
| GET | `/job-postings/{jobPostingId}/application` | 특정 공고에 대한 내 지원서 조회 | 없음 | `ApiResponse<ApplicationDetailResponse>` |

## Applicant Identification

- `CustomUserDetails`에는 아직 `userId` 또는 `applicantId`가 없다.
- 이번 Phase에서는 `CurrentApplicantService`가 `@AuthenticationPrincipal CustomUserDetails`를 받아 처리한다.
- `userType`이 `Applicant`인지 먼저 확인한다.
- Applicant인 경우 `CustomUserDetails.getUsername()`의 `loginId`로 `ApplicantRepository.findByLoginId`를 호출해 `applicantId`를 얻는다.
- `userType` 문자열은 `CustomUserDetails.USER_TYPE_APPLICANT/USER_TYPE_EMPLOYEE` 상수로 관리한다.
- `CustomUserDetails.getUsername()`이 `loginId`를 반환한다는 임시 전제는 `CustomUserDetailsTest`로 고정했다.
- Employee/Admin 또는 인증 principal이 없으면 `InvalidJobApplicationException`으로 실패시킨다.
- `SecurityConfig`는 변경하지 않았다.

## Business Rules

- HTTP API는 기존 `JobApplicationService`의 비즈니스 규칙을 그대로 따른다.
- 생성은 PUBLISHED 공고이면서 접수기간 내인 경우만 가능하다.
- 수정은 DRAFT 상태에서만 가능하다.
- 제출은 `DRAFT -> SUBMITTED`만 가능하다.
- 철회는 `SUBMITTED -> WITHDRAWN`만 가능하다.
- 타인의 지원서는 `JobApplicationNotFoundException`으로 처리되어 404 응답이 내려간다.
- Employee/Admin은 지원자 Application API를 사용할 수 없다.

## Response Format

- 성공 응답은 `ResponseEntity<ApiResponse<T>>`를 사용한다.
- 성공 응답은 `success=true`, `message`, `data` 구조를 가진다.
- 실패 응답은 `GlobalExceptionHandler`를 통해 `ApiResponse.fail(...)` 형태로 반환한다.

## Validation and Error Policy

- `ApplicationCreateRequest`, `ApplicationUpdateRequest`에 `@Valid`를 적용한다.
- Bean Validation 실패는 `MethodArgumentNotValidException` 처리로 `400 BAD_REQUEST + ApiResponse.fail`을 반환한다.
- `InvalidJobApplicationException`은 `400 BAD_REQUEST`로 처리한다.
- `JobApplicationNotFoundException`은 `404 NOT_FOUND`로 처리한다.
- `PUT /applications/{applicationId}`는 지원하지 않는다.
- `DELETE /applications/{applicationId}`는 지원하지 않는다.

## Test List

- `POST /applications` 생성 성공
- `POST /applications` validation 실패 시 `400 + ApiResponse.fail`
- Employee principal의 생성 요청 실패
- `GET /applications/{applicationId}` 조회 성공
- 존재하지 않는 Application 조회 시 `404 + ApiResponse.fail`
- 타인 Application 조회 차단
- 타인 Application DRAFT 수정 차단
- 타인 Application 제출 차단
- 타인 Application 철회 차단
- `POST /applications/{applicationId}` DRAFT 수정 성공
- 수정 validation 실패 시 `400 + ApiResponse.fail`
- SUBMITTED 상태 수정 실패 시 `400 + ApiResponse.fail`
- `POST /applications/{applicationId}/submit` 성공
- 잘못된 상태 제출 실패 시 `400 + ApiResponse.fail`
- `POST /applications/{applicationId}/withdraw` 성공
- 잘못된 상태 철회 실패 시 `400 + ApiResponse.fail`
- `GET /job-postings/{jobPostingId}/application` 성공
- 공고별 내 지원서 없음 시 `404 + ApiResponse.fail`
- `PUT /applications/{applicationId}` 미지원 확인
- `DELETE /applications/{applicationId}` 미지원 확인
- `CustomUserDetails.getUsername()`이 Applicant/Employee/LDAP loginId를 반환하는지 확인
- `CustomUserDetails` userType 상수 적용 확인

## Test Commands

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.ApplicationControllerTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test
```

## Test Result

- `ApplicationControllerTest`: 성공
- 전체 `clean test`: 성공

## Remaining Issues

- 현재 `CustomUserDetails`에 `userId/applicantId`가 없어 `loginId` 기반 조회를 사용한다.
- 실제 운영 권한 정책 확정 시 `CustomUserDetails`에 user id 또는 applicant id를 싣거나 별도 current-user resolver를 보강하는 것이 좋다.
- 인증 실패/권한 실패가 현재는 Application 비즈니스 예외 성격의 `400`으로 내려간다. 보안 정책 확정 후 `401/403` 매핑을 검토한다.
- Controller 테스트는 `SecurityContextHolder`에 인증 객체를 직접 넣어 `@AuthenticationPrincipal` 연결과 API 계약을 검증한다. 실제 SecurityFilterChain, 미로그인 401, Employee/Admin 403, CSRF 정책은 별도 보안 통합 테스트에서 검증해야 한다.
- 동시 중복 지원 시 DB unique 충돌을 `InvalidJobApplicationException`으로 변환하는 처리는 아직 없다.
- `GET /applications/me` 목록 API는 page/size와 화면 요구사항 확정 후 별도 Phase에서 구현한다.

## Before Next Phase

- 관리자 Application 목록/상세 조회를 먼저 구현할지, Application 상세 섹션을 먼저 구현할지 결정한다.
- StageResult는 Application 루트가 생겼지만, 전형 결과 관리 정책과 관리자 Application 조회 요구사항이 더 정리된 뒤 구현하는 것을 권장한다.
- 지원자 API의 인증/인가 응답 코드를 `401/403`으로 정교화할지 결정한다.
