# Phase 03a-1 - Application Basic Create/Read

## Phase 03a-2 반영 메모

- Phase 03a-2에서 `updateDraft`, `submit`, `withdraw` command가 `JobApplicationService`에 추가되었다.
- `JobApplication`에는 `updateDraft`, `submit`, `withdraw` 상태 변경 메서드가 추가되었다.
- `ApplicationController`와 HTTP API는 아직 없으며 Phase 03a-3 범위로 유지한다.
- 상세 섹션 필수값 검증, StageResult, 관리자 Application API는 아직 구현하지 않았다.

## Phase 이름

Phase 03a-1: 지원자 Application 기본 생성/조회 기반

## 구현 목적

지원자가 공개된 채용공고에 대해 지원서 루트(`JobApplication`)를 생성하고, 본인 지원서를 조회할 수 있는 최소 기반을 구현했다. 이번 Phase는 Application 상세 섹션, 임시저장 수정, 최종제출, 철회, 관리자 조회, StageResult 구현 전의 선행 축이다.

## 구현 범위

- `JobApplication` Entity 추가
- `JobApplicationStatus` enum 추가
- `JobApplicationRepository` 추가
- `JobPositionRepository` 추가
- `ApplicationCreateRequest` 추가
- `ApplicationDetailResponse` 추가
- `JobApplicationService` 생성/조회 로직 추가
- Application 전용 예외와 전역 예외 처리 추가
- `JobApplicationServiceTest` 추가

## 구현하지 않은 범위

- `ApplicationController`
- `updateDraft`
- `submit`
- `withdraw`
- `delete`
- 관리자 Application API
- `StageResult`
- Education, Career, Certificate, Language, Military, Award, GapPeriod, Attachment
- Interview, Message, CommonCode
- JobPosting publish 조건의 Stage 최소 1개 검증
- JobPosting 생성 시 기본 Stage 자동 생성

## 변경 파일 목록

### 코드 변경

- `src/main/java/com/shinyoung/recruit/domain/entity/JobApplication.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/JobApplicationRepository.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/JobPositionRepository.java`
- `src/main/java/com/shinyoung/recruit/dto/request/ApplicationCreateRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/response/ApplicationDetailResponse.java`
- `src/main/java/com/shinyoung/recruit/enumeration/JobApplicationStatus.java`
- `src/main/java/com/shinyoung/recruit/exception/InvalidJobApplicationException.java`
- `src/main/java/com/shinyoung/recruit/exception/JobApplicationNotFoundException.java`
- `src/main/java/com/shinyoung/recruit/exception/GlobalExceptionHandler.java`
- `src/main/java/com/shinyoung/recruit/service/JobApplicationService.java`

### 테스트 변경

- `src/test/java/com/shinyoung/recruit/service/JobApplicationServiceTest.java`

### 문서 변경

- `docs/codex/implementation/phase-03a-1-application-basic-create-read.md`
- `docs/codex/07-implementation-history.md`

## 신규 클래스 목록

- `JobApplication`
- `JobApplicationStatus`
- `JobApplicationRepository`
- `JobPositionRepository`
- `ApplicationCreateRequest`
- `ApplicationDetailResponse`
- `JobApplicationService`
- `InvalidJobApplicationException`
- `JobApplicationNotFoundException`
- `JobApplicationServiceTest`

## 수정 클래스 목록

- `GlobalExceptionHandler`

## 클래스별 설명

| 구분 | 패키지 | 클래스 | 역할 | 주요 필드/메서드 | 연관 클래스 | 비고 |
|---|---|---|---|---|---|---|
| Entity | `domain.entity` | `JobApplication` | 지원서 루트 Entity | `applicant`, `jobPosting`, `jobPosition`, `status`, `submittedAt`, `withdrawnAt`, snapshot 필드, `create` | `Applicant`, `JobPosting`, `JobPosition`, `JobApplicationStatus` | `applicant_id + job_posting_id` unique 제약 포함 |
| Enum | `enumeration` | `JobApplicationStatus` | 지원서 상태 | `DRAFT`, `SUBMITTED`, `WITHDRAWN` | `JobApplication` | 이번 Phase에서는 생성 시 `DRAFT`만 사용 |
| Repository | `domain.repository` | `JobApplicationRepository` | 지원서 저장/조회 및 중복 검증 | `findByIdAndApplicantId`, `findByApplicantIdAndJobPostingId`, `existsByApplicantIdAndJobPostingId` | `JobApplication` | 본인 조회와 중복 지원 차단에 사용 |
| Repository | `domain.repository` | `JobPositionRepository` | 모집분야 소속 검증 | `findByIdAndJobPostingId` | `JobPosition` | 기존 JobPosition 조회 인프라 보강 |
| Request DTO | `dto.request` | `ApplicationCreateRequest` | 지원서 생성 요청 | `jobPostingId`, `jobPositionId` | `JobApplicationService` | 두 필드 모두 `@NotNull` |
| Response DTO | `dto.response` | `ApplicationDetailResponse` | 지원서 상세 응답 | `from(JobApplication)` | `JobApplication` | 민감정보 제외, snapshot 기반 공고/모집분야명 응답 |
| Service | `service` | `JobApplicationService` | 지원서 생성/조회 비즈니스 로직 | `create`, `getApplication`, `getMyApplicationByJobPosting` | `JobApplicationRepository`, `ApplicantRepository`, `JobPostingRepository`, `JobPositionRepository`, `Clock` | Controller 없이 applicantId를 명시 파라미터로 받음 |
| Exception | `exception` | `JobApplicationNotFoundException` | 지원서 미존재 또는 타인 지원서 조회 예외 | 생성자 | `GlobalExceptionHandler` | 404 응답 |
| Exception | `exception` | `InvalidJobApplicationException` | 지원서 생성/조회 비즈니스 규칙 위반 | 생성자 | `GlobalExceptionHandler` | 400 응답 |
| Exception | `exception` | `GlobalExceptionHandler` | Application 예외 응답 처리 | `handleJobApplicationNotFound`, `handleInvalidJobApplication` | `ApiResponse` | 기존 응답 규격 유지 |
| Test | `service` | `JobApplicationServiceTest` | Application Service 규칙 검증 | 생성, 상태, snapshot, 공고 상태/기간, 참조 검증, 중복, 본인 조회 테스트 | `JobApplicationService`, `JobPostingService` | 고정 `Clock` 사용 |

## API 목록

없음.

이번 Phase에서는 `ApplicationController`를 만들지 않았으므로 HTTP API는 추가하지 않았다. 지원자 API는 Phase 03a-3에서 구현한다.

## Entity 관계 요약

- `JobApplication` N : 1 `Applicant`
- `JobApplication` N : 1 `JobPosting`
- `JobApplication` N : 1 `JobPosition`
- `Applicant`, `JobPosting`, `JobPosition`에는 `List<JobApplication>`을 추가하지 않았다.
- cascade/orphanRemoval은 사용하지 않았다.
- `job_application`에는 `applicant_id + job_posting_id` unique 제약을 둔다.

## 주요 비즈니스 규칙

- Application 생성은 `PUBLISHED` JobPosting에만 허용한다.
- Application 생성은 접수기간 내에만 허용한다.
- 접수기간 판단은 `LocalDateTime.now(clock)` 기준이다.
- DRAFT/CLOSED JobPosting에는 Application을 생성할 수 없다.
- Applicant는 존재해야 한다.
- JobPosting은 존재해야 한다.
- JobPosition은 존재해야 하며 요청 JobPosting 소속이어야 한다.
- 같은 Applicant + JobPosting 조합은 하나의 JobApplication만 허용한다.
- 다른 JobPosition을 선택하더라도 같은 공고에 복수 지원할 수 없다.
- JobPosting의 ApplicationFormConfig가 존재해야 한다.
- 생성 시 상태는 항상 `DRAFT`다.
- 본인 지원서만 조회할 수 있으며 타인의 지원서는 `JobApplicationNotFoundException`으로 처리한다.

## 중복 지원 정책

- Service에서 `existsByApplicantIdAndJobPostingId`로 중복을 먼저 차단한다.
- DB에서도 `uk_job_application_applicant_job_posting` unique 제약으로 중복을 방어한다.
- `WITHDRAWN` 이후 재지원은 현재 정책상 차단하므로 unique 제약과 충돌하지 않는다.
- 철회 후 재지원 허용 정책이 생기면 unique 제약은 재검토한다.

## 접수기간 검증 정책

```text
now >= receptionStartDateTime
now <= receptionEndDateTime
```

- `now.isBefore(receptionStartDateTime)`이면 생성 실패
- `now.isAfter(receptionEndDateTime)`이면 생성 실패
- 테스트는 고정 `Clock`으로 검증한다.

## snapshot 저장 정책

- `applicantNameSnapshot`
  - `Applicant.userName`을 우선 사용한다.
  - `Applicant.userName`이 비어 있으면 상위 `User.name`을 사용한다.
  - 둘 다 없으면 생성 실패 처리한다.
  - `loginId` 또는 `email`로 대체하지 않는다.
- `jobPostingTitleSnapshot`
  - `JobPosting.title`을 저장한다.
- `jobPositionNameSnapshot`
  - `JobPosition.positionName`을 저장한다.

## 테스트 목록

- `PUBLISHED` + 접수기간 내 공고에 Application 생성 성공
- 생성 시 status가 `DRAFT`인지 확인
- snapshot 저장 확인
- DRAFT JobPosting 생성 실패
- CLOSED JobPosting 생성 실패
- 접수기간 전 생성 실패
- 접수기간 후 생성 실패
- 존재하지 않는 Applicant 실패
- 존재하지 않는 JobPosting 실패
- 존재하지 않는 JobPosition 실패
- JobPosition이 해당 JobPosting 소속이 아니면 실패
- ApplicationFormConfig가 없으면 실패
- 같은 Applicant + JobPosting 중복 생성 실패
- 같은 공고에서 다른 JobPosition으로 다시 생성해도 실패
- 내 Application 조회 성공
- 다른 Applicant의 Application 조회 실패
- 공고별 내 Application 조회 성공
- 공고별 내 Application이 없으면 실패

## 실행한 테스트 명령

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.JobApplicationServiceTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test
```

## 테스트 결과

- `JobApplicationServiceTest`: 성공
- 전체 `clean test`: 성공

## 남은 이슈

- `ApplicationController`는 아직 없다.
- `updateDraft`, `submit`, `withdraw` command는 아직 없다.
- `submittedAt`, `withdrawnAt` 필드는 존재하지만 값을 변경하는 Entity 메서드는 아직 없다. Phase 03a-2에서 `submit(now)`, `withdraw(now)` 같은 의미 있는 상태 변경 메서드를 추가한다.
- 현재 로그인 Applicant 식별 방식은 Controller 도입 시 확정해야 한다.
- `CustomUserDetails`에 `userId`를 추가할지, 별도 CurrentApplicant helper를 둘지 Phase 03a-3 전에 결정해야 한다.
- 현재 `findApplicant()` 실패는 `InvalidJobApplicationException`으로 처리되어 400 계열로 매핑된다. Service 입력의 `applicantId`가 인증 객체에서 나온다는 전제에서는 유지 가능하지만, Controller 인증 연동 시점에 404 또는 인증/인가 예외로 볼지 다시 결정한다.
- 동시 중복 생성 요청은 Service `exists` 검증을 모두 통과한 뒤 DB unique 제약에서 한 요청이 실패할 수 있다. 현재는 별도 `DataIntegrityViolationException` 변환을 추가하지 않았으며, Phase 03a-3 Controller/API 또는 운영 안정화 단계에서 `InvalidJobApplicationException` 성격의 응답으로 변환할지 검토한다.
- 철회 후 재지원 허용 정책이 생기면 `applicant_id + job_posting_id` unique 제약을 재검토해야 한다.

## 다음 Phase 03a-2 전 확인 사항

- `updateDraft`에서 수정 가능한 필드를 `jobPosition`으로 제한할지 확정한다.
- `submit` 시 상세 섹션 필수 검증을 어느 Phase부터 적용할지 결정한다.
- `withdraw`는 `PUBLISHED + 접수기간 내`에서만 허용할지 최종 확정한다.
- `submittedAt`, `withdrawnAt`은 `Clock` 기반으로 저장한다.
- `JobApplication` Entity에 `DRAFT -> SUBMITTED`, `SUBMITTED -> WITHDRAWN` 전이를 담당하는 메서드를 추가한다.
