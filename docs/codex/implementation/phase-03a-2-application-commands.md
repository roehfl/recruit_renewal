# Phase 03a-2 - Application Commands

## Phase 03a-3 반영 메모

- Phase 03a-3에서 `ApplicationController`가 추가되어 이 문서의 Service command가 HTTP API로 연결되었다.
- 연결된 API는 `POST /applications/{applicationId}`, `/submit`, `/withdraw`이며 응답은 기존 command 스타일에 맞춰 `ApiResponse<Long>`를 사용한다.
- `GET /applications/me` 목록 API, 관리자 Application API, StageResult, 상세 섹션 도메인은 아직 구현하지 않았다.

## Phase Name

Phase 03a-2: Application draft update / submit / withdraw command

## Purpose

Phase 03a-1에서 만든 `JobApplication` 루트에 지원자 임시저장 수정, 최종제출, 철회 command를 추가했다. 이번 Phase는 HTTP Controller 없이 Service command와 Entity 상태 변경 메서드, Service 테스트로 지원서 기본 생명주기를 고정하는 데 집중했다.

## Implemented Scope

- `JobApplication` Entity command 메서드 추가
- `ApplicationUpdateRequest` 추가
- `JobApplicationService.updateDraft`
- `JobApplicationService.submit`
- `JobApplicationService.withdraw`
- 지원서 command Service 테스트 보강
- Phase 03a-2 구현 문서 작성

## Not Implemented

- `ApplicationController`
- 관리자 Application API
- `updateDraft`, `submit`, `withdraw` HTTP API
- StageResult
- Education, Career, Certificate, Language, Military, Award, GapPeriod, Attachment
- Interview, Message, CommonCode
- 상세 섹션 필수값 검증
- 철회 후 재지원 허용
- JobPosting publish 조건의 Stage 최소 1개 검증
- JobPosting 생성 시 기본 Stage 자동 생성

## Changed Files

### Code

- `src/main/java/com/shinyoung/recruit/domain/entity/JobApplication.java`
- `src/main/java/com/shinyoung/recruit/dto/request/ApplicationUpdateRequest.java`
- `src/main/java/com/shinyoung/recruit/service/JobApplicationService.java`

### Test

- `src/test/java/com/shinyoung/recruit/service/JobApplicationServiceTest.java`

### Documentation

- `docs/codex/implementation/phase-03a-2-application-commands.md`
- `docs/codex/implementation/phase-03a-1-application-basic-create-read.md`
- `docs/codex/design/phase-03-application-design.md`
- `docs/codex/07-implementation-history.md`

## New Classes

- `ApplicationUpdateRequest`

## Modified Classes

- `JobApplication`
- `JobApplicationService`
- `JobApplicationServiceTest`

## Class-by-Class Explanation

| 구분 | 패키지 | 클래스 | 역할 | 주요 필드/메서드 | 연관 클래스 | 비고 |
|---|---|---|---|---|---|---|
| Entity | `com.shinyoung.recruit.domain.entity` | `JobApplication` | 지원서 루트 Entity | `updateDraft`, `submit`, `withdraw` | `Applicant`, `JobPosting`, `JobPosition`, `JobApplicationStatus` | 상태 검증은 Service에서 우선 수행 |
| Request DTO | `com.shinyoung.recruit.dto.request` | `ApplicationUpdateRequest` | 임시저장 수정 요청 | `jobPositionId` | `JobApplicationService` | 모집분야 변경만 허용, `@NotNull` 적용 |
| Service | `com.shinyoung.recruit.service` | `JobApplicationService` | 지원서 command 비즈니스 로직 | `updateDraft`, `submit`, `withdraw` | `JobApplicationRepository`, `JobPositionRepository`, `Clock` | 소유자, 상태, 접수기간, 공고 상태 검증 |
| Test | `com.shinyoung.recruit.service` | `JobApplicationServiceTest` | 지원서 생성/조회/command 규칙 검증 | command 성공/실패 테스트 | `JobApplicationService`, `JobPostingService` | 고정 `Clock` 사용 |

## API List

없음.

이번 Phase에서는 `ApplicationController`를 만들지 않았으므로 HTTP API를 추가하지 않았다. 지원자 API는 Phase 03a-3에서 구현한다.

## Entity Relationship Summary

- `JobApplication` N : 1 `Applicant`
- `JobApplication` N : 1 `JobPosting`
- `JobApplication` N : 1 `JobPosition`
- `Applicant`, `JobPosting`, `JobPosition`에는 `List<JobApplication>`을 추가하지 않았다.
- cascade/orphanRemoval은 사용하지 않는다.
- `job_application`에는 Phase 03a-1에서 추가한 `applicant_id + job_posting_id` unique 제약을 유지한다.

## Business Rules

### Common

- applicantId 소유의 `JobApplication`만 command를 수행할 수 있다.
- 타인의 지원서는 `JobApplicationNotFoundException`으로 처리한다.
- `JobPosting.status`가 `PUBLISHED`여야 한다.
- 현재 시각이 접수기간 안이어야 한다.
- 접수기간 판단은 `LocalDateTime.now(clock)`을 사용한다.

```text
now >= receptionStartDateTime
now <= receptionEndDateTime
```

### updateDraft Policy

- `DRAFT` 상태에서만 가능하다.
- 변경 대상은 선택 모집분야(`jobPosition`)와 `jobPositionNameSnapshot`뿐이다.
- `SUBMITTED`, `WITHDRAWN` 상태에서는 수정할 수 없다.
- 요청한 `JobPosition`은 현재 지원서의 `JobPosting` 소속이어야 한다.
- `status`, `submittedAt`, `withdrawnAt`, `jobPostingTitleSnapshot`, `applicantNameSnapshot`은 변경하지 않는다.

### submit Policy

- `DRAFT -> SUBMITTED`만 허용한다.
- 재제출과 철회 후 재제출은 차단한다.
- 제출 시 `submittedAt`을 `LocalDateTime.now(clock)`으로 저장한다.
- `withdrawnAt`은 변경하지 않는다.
- 제출 시 `ApplicationFormConfig` 존재 여부를 다시 확인한다.
- 선택 모집분야가 여전히 해당 공고 소속인지 확인한다.
- 학력/경력/자격 등 상세 섹션 필수값 검증은 후속 Phase에서 구현한다.

### withdraw Policy

- `SUBMITTED -> WITHDRAWN`만 허용한다.
- `DRAFT` 철회와 재철회는 차단한다.
- 철회는 `PUBLISHED` 공고이고 접수기간 내인 경우에만 허용한다.
- 철회 시 `withdrawnAt`을 `LocalDateTime.now(clock)`으로 저장한다.
- `submittedAt`은 유지한다.
- `WITHDRAWN` 이후 재지원은 현재 unique 정책상 계속 차단한다.

## State Transition Rules

```text
DRAFT -> SUBMITTED
SUBMITTED -> WITHDRAWN
```

허용하지 않는 전이:

- `SUBMITTED -> SUBMITTED`
- `WITHDRAWN -> SUBMITTED`
- `DRAFT -> WITHDRAWN`
- `WITHDRAWN -> WITHDRAWN`
- `SUBMITTED/WITHDRAWN -> DRAFT`

## Test List

- Application 생성 성공
- 생성 시 `DRAFT` 상태 확인
- snapshot 저장 확인
- DRAFT/CLOSED JobPosting 생성 실패
- 접수기간 전/후 생성 실패
- Applicant/JobPosting/JobPosition 참조 오류 실패
- JobPosition이 다른 JobPosting 소속이면 생성 실패
- ApplicationFormConfig 누락 시 생성 실패
- 같은 Applicant + JobPosting 중복 생성 실패
- 내 Application 조회 성공
- 타인 Application 조회 실패
- 공고별 내 Application 조회 성공/실패
- DRAFT Application 모집분야 수정 성공
- updateDraft 시 `jobPositionNameSnapshot` 갱신 확인
- updateDraft 후 `DRAFT` 유지 확인
- SUBMITTED/WITHDRAWN Application 수정 실패
- 타인 Application 수정 실패
- 접수기간 전/후 수정 실패
- DRAFT/CLOSED JobPosting 수정 실패
- 존재하지 않는 JobPosition 수정 실패
- 다른 JobPosting 소속 JobPosition 수정 실패
- DRAFT -> SUBMITTED 성공
- submit 시 `submittedAt`이 fixed Clock 기준으로 저장됨
- SUBMITTED 재제출 실패
- WITHDRAWN 재제출 실패
- 타인 Application 제출 실패
- 접수기간 전/후 제출 실패
- PUBLISHED가 아닌 JobPosting 제출 실패
- ApplicationFormConfig 누락 시 제출 실패
- JobPosition 소속 불일치 시 제출 실패
- SUBMITTED -> WITHDRAWN 성공
- withdraw 시 `withdrawnAt`이 fixed Clock 기준으로 저장됨
- withdraw 후 `submittedAt` 유지 확인
- DRAFT 철회 실패
- WITHDRAWN 재철회 실패
- 타인 Application 철회 실패
- 접수기간 전/후 철회 실패
- PUBLISHED가 아닌 JobPosting 철회 실패

## Test Commands

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.JobApplicationServiceTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test
```

## Test Result

- `JobApplicationServiceTest`: 성공
- 전체 `clean test`: 성공

## Remaining Issues

- `ApplicationController`가 아직 없다.
- HTTP API 응답 계약은 Phase 03a-3에서 MockMvc로 고정해야 한다.
- 동시 중복 지원에서 DB unique 충돌이 발생할 경우 `DataIntegrityViolationException` 변환 정책은 아직 없다.
- 현재 Service는 `applicantId`를 명시 파라미터로 받는다. 인증 객체에서 현재 Applicant를 식별하는 방식은 Phase 03a-3 전에 결정해야 한다.
- 상세 섹션 필수값 검증은 아직 구현하지 않았다.
- 철회 후 재지원 허용 정책이 생기면 `applicant_id + job_posting_id` unique 제약을 재검토해야 한다.

## Before Phase 03a-3

- `ApplicationController`에서 현재 로그인 Applicant를 식별하는 방식을 결정한다.
- `POST /applications/{applicationId}`, `/submit`, `/withdraw` API 응답 DTO를 `Long`으로 갈지 `ApplicationDetailResponse`로 갈지 확정한다.
- `DataIntegrityViolationException`을 Application 비즈니스 예외로 변환할지 검토한다.
- 지원자 목록 API(`/applications/me`)는 `PageResponse<MyApplicationResponse>`로 설계하는 방향을 유지한다.
