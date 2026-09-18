# Phase 03b-1 - Admin Application Read

## Phase Name

Phase 03b-1: 관리자 Application 목록/상세 조회 API

## Purpose

Phase 03a에서 생성된 `JobApplication` 루트를 관리자 화면에서 조회할 수 있는 최소 API를 구현한다. 이번 Phase는 지원서 루트 정보의 목록/상세 조회, 필터, 페이징, 관리자 전용 응답 DTO, Controller/Service/Repository/Test에 집중한다.

## Implemented Scope

- 관리자 전체 지원서 목록 조회 API
- 관리자 지원서 상세 조회 API
- 공고 기준 관리자 지원서 목록 조회 API
- `jobPostingId`, `jobPositionId`, `status` 필터
- `page`, `size` 페이징 검증
- 관리자 전용 목록/상세 DTO 분리
- pageable 목록 조회에서 to-one 연관만 `@EntityGraph`로 조회
- Service/Controller 테스트 보강

## Not Implemented

- 지원자 Application API 변경
- 관리자 Application 수정/삭제 command
- StageResult
- Application 상세 섹션 도메인
- Education, Career, Certificate, Language, Military, Award, GapPeriod, Attachment
- Interview, Message, CommonCode
- SecurityConfig 권한 정책 변경
- `PUT`, HTTP `DELETE`

## Changed Files

### Code

- `src/main/java/com/shinyoung/recruit/controller/AdminApplicationController.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/JobApplicationRepository.java`
- `src/main/java/com/shinyoung/recruit/dto/condition/AdminApplicationSearchCondition.java`
- `src/main/java/com/shinyoung/recruit/dto/response/AdminApplicationSummaryResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/AdminApplicationDetailResponse.java`
- `src/main/java/com/shinyoung/recruit/service/JobApplicationService.java`

### Test

- `src/test/java/com/shinyoung/recruit/service/JobApplicationServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/AdminApplicationControllerTest.java`

### Documentation

- `docs/codex/implementation/phase-03b-1-admin-application-read.md`
- `docs/codex/design/phase-03-application-design.md`
- `docs/codex/07-implementation-history.md`

## New Classes

- `AdminApplicationController`
- `AdminApplicationSearchCondition`
- `AdminApplicationSummaryResponse`
- `AdminApplicationDetailResponse`
- `AdminApplicationControllerTest`

## Modified Classes

- `JobApplicationRepository`
- `JobApplicationService`
- `JobApplicationServiceTest`

## Class-by-Class Explanation

| 구분 | 패키지 | 클래스 | 역할 | 주요 필드/메서드 | 연관 클래스 | 비고 |
|---|---|---|---|---|---|---|
| Controller | `com.shinyoung.recruit.controller` | `AdminApplicationController` | 관리자 지원서 조회 API | `getApplications`, `getApplication`, `getApplicationsByJobPosting` | `JobApplicationService` | 조회 API만 제공 |
| Repository | `com.shinyoung.recruit.domain.repository` | `JobApplicationRepository` | 관리자 조회 쿼리 | `searchForAdmin`, `searchByJobPostingForAdmin`, `findAdminDetailById` | `JobApplication` | pageable 목록에서 collection fetch 없음 |
| Condition DTO | `com.shinyoung.recruit.dto.condition` | `AdminApplicationSearchCondition` | 관리자 조회 조건 내부 객체 | `jobPostingId`, `jobPositionId`, `status` | `JobApplicationStatus` | Controller 요청 DTO가 아닌 Service 내부 조건 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `AdminApplicationSummaryResponse` | 관리자 목록 응답 | snapshot, status, timestamps | `JobApplication` | 민감 개인정보 제외 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `AdminApplicationDetailResponse` | 관리자 상세 응답 | snapshot, status, timestamps | `JobApplication` | 상세 섹션은 미포함 |
| Service | `com.shinyoung.recruit.service` | `JobApplicationService` | 관리자 조회 유스케이스 추가 | `getApplicationsForAdmin`, `getApplicationForAdmin`, `getApplicationsByJobPostingForAdmin` | `JobApplicationRepository`, `JobPostingRepository` | 기존 지원자 메서드 의미 유지 |
| Test | `com.shinyoung.recruit.service` | `JobApplicationServiceTest` | 관리자 조회 Service 검증 | 필터, 상세, paging/status 실패 | `JobApplicationService` | 기존 Phase 03a 테스트 유지 |
| Test | `com.shinyoung.recruit.controller` | `AdminApplicationControllerTest` | 관리자 API 계약 검증 | path, method, ApiResponse, 실패 응답 | `AdminApplicationController` | PUT/DELETE/POST 수정 미지원 확인 |

## API List

| Method | Path | Purpose | Query | Response |
|---|---|---|---|---|
| GET | `/admin/applications` | 전체 지원서 목록 조회 | `jobPostingId`, `jobPositionId`, `status`, `page`, `size` | `ApiResponse<PageResponse<AdminApplicationSummaryResponse>>` |
| GET | `/admin/applications/{applicationId}` | 지원서 상세 조회 | 없음 | `ApiResponse<AdminApplicationDetailResponse>` |
| GET | `/admin/job-postings/{jobPostingId}/applications` | 공고별 지원서 목록 조회 | `jobPositionId`, `status`, `page`, `size` | `ApiResponse<PageResponse<AdminApplicationSummaryResponse>>` |

## Admin Response DTO Fields

- `applicationId`
- `applicantId`
- `applicantNameSnapshot`
- `jobPostingId`
- `jobPostingTitleSnapshot`
- `jobPositionId`
- `jobPositionNameSnapshot`
- `status`
- `submittedAt`
- `withdrawnAt`
- `createdAt`
- `updatedAt`

## Entity Relationship Summary

`JobApplication`은 `Applicant`, `JobPosting`, `JobPosition`을 각각 LAZY `ManyToOne`으로 참조한다. 관리자 목록/상세 응답에는 세 연관의 id와 snapshot 필드가 필요하므로, 관리자 조회 Repository 메서드에서 to-one 연관만 `@EntityGraph`로 함께 조회한다. collection fetch는 사용하지 않는다.

## Privacy Policy

관리자 응답에는 현재 Phase에서 필요한 지원서 루트 정보와 snapshot만 포함한다. CI, ciHash, password, phoneNumber, address, email, 암호화 원문 개인정보는 응답 DTO에 포함하지 않았다.

## Business Rules

- `page`는 0 이상이어야 한다.
- `size`는 1 이상 100 이하이어야 한다.
- `status`는 `DRAFT`, `SUBMITTED`, `WITHDRAWN` 중 하나여야 한다.
- `status` 필터는 앞뒤 공백을 제거하고 대소문자를 구분하지 않도록 정규화한다.
- 잘못된 status 값은 `InvalidJobApplicationException`으로 400 처리한다.
- 존재하지 않는 `applicationId` 상세 조회는 `JobApplicationNotFoundException`으로 404 처리한다.
- 공고 기준 목록 조회에서 `jobPostingId`가 존재하지 않으면 `JobPostingNotFoundException`으로 404 처리한다.
- 목록 기본 정렬은 `createdAt DESC, id DESC`이다.

## Test List

- 관리자 전체 Application 목록 조회 성공
- status 필터 조회 성공
- jobPostingId 필터 조회 성공
- jobPositionId 필터 조회 성공
- 공고별 Application 목록 조회 성공
- 관리자 Application 상세 조회 성공
- 존재하지 않는 applicationId 상세 조회 실패
- page 음수 실패
- size 0 실패
- size 100 초과 실패
- 잘못된 status 실패
- Controller `GET /admin/applications` 성공
- Controller status/jobPostingId/jobPositionId 필터 성공
- Controller `GET /admin/applications/{applicationId}` 성공
- Controller 상세 not found 404
- Controller `GET /admin/job-postings/{jobPostingId}/applications` 성공
- Controller page/size/status 실패 응답
- `PUT /admin/applications/{applicationId}` 미지원 확인
- `DELETE /admin/applications/{applicationId}` 미지원 확인
- `POST /admin/applications/{applicationId}` 미지원 확인
- status 필터 소문자/공백 입력 정규화 확인

## Test Commands

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.JobApplicationServiceTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.AdminApplicationControllerTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test
```

## Test Result

- `JobApplicationServiceTest`: 성공
- `AdminApplicationControllerTest`: 성공
- 전체 `clean test`: 성공

## Remaining Issues

- 이번 Phase는 실제 관리자 권한 검증을 SecurityConfig에 추가하지 않았다. 운영 전 `/admin/applications/**`는 `ROLE_ADMIN` 또는 채용담당자 권한으로 보호해야 한다.
- 관리자 상세 응답은 Application 루트 정보만 포함하며, 상세 섹션과 StageResult는 아직 없다.
- 관리자 목록 검색 조건은 최소 필터만 제공한다. 이름/키워드/제출일 범위 검색은 화면 요구사항 확정 후 별도 Phase에서 검토한다.

## Next Phase Considerations

- 관리자 Application 목록/상세 화면에서 필요한 추가 필터를 확정한다.
- 지원서 상세 섹션 도메인 구현을 시작할지, 관리자 조회 API 보강을 먼저 할지 결정한다.
- StageResult는 Application 상세 섹션과 전형 결과 정책 확정 이후 구현한다.
