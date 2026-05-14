# 07. Implementation History

## 2026-05-13 - Phase 01a JobPosting Vertical Slice

- Document: `docs/codex/implementation/phase-01a-job-posting.md`
- Scope:
  - Added JobPosting aggregate (`JobPosting`, `JobPosition`, `ApplicationFormConfig`)
  - Added posting lifecycle enum/status transition service flow
  - Added admin posting CRUD-like APIs (POST-based update policy)
  - Added service-level business validation and tests
- Notes:
  - Focused on Phase 01a only.
  - Did not add Application/Stage/Interview/Message/CommonCode domains.

## 2026-05-13 - Phase 01a Review Fixes

- Updated JobPosting list API to follow PageResponse pattern (`page`, `size`).
- Added `GlobalExceptionHandler` for JobPosting exceptions:
  - `JobPostingNotFoundException` -> 404
  - `InvalidJobPostingException` -> 400
- Updated Phase 01a implementation document to reflect review fixes.

## 2026-05-14 - Phase 01b JobPosting Public Read API

- Document: `docs/codex/implementation/phase-01b-job-posting-public-read.md`
- Scope:
  - Resolved existing conflict markers in Phase 01a JobPosting files while keeping admin `PageResponse` list behavior.
  - Kept admin update as `POST /admin/job-postings/{id}` and did not reintroduce PUT.
  - Added public/applicant JobPosting list/detail read APIs.
  - Added public DTOs separated from admin DTOs.
  - Added `Clock` injection for testable `accepting` calculation.
  - Added public visibility tests for `PUBLISHED`, `DRAFT`, and `CLOSED` postings.
- Notes:
  - Public APIs expose only `PUBLISHED` postings.
  - `PUBLISHED` postings are shown regardless of reception period; `accepting` reports current receivable status.
  - Did not add Application/Stage/Interview/Message/CommonCode domains.

## 2026-05-14 - Phase 01b Review Fixes

- Document: `docs/codex/implementation/phase-01b-job-posting-public-read.md`
- Scope:
  - Removed collection `@EntityGraph` from admin pageable list query.
  - Added admin detail-only repository lookup with `@EntityGraph`.
  - Unified admin `publish`/`close` timestamps on injected `Clock`.
  - Added page/size validation for admin and public JobPosting list queries.
  - Made public detail JobPosition `sortOrder` sorting null-safe.
  - Added tests for Clock-based publish/close timestamps and invalid paging requests.
- Notes:
  - Did not reintroduce PUT.
  - Did not allow status updates through the general admin update API.

## 2026-05-14 - Phase 01a/01b Integration Check

- Documents:
  - `docs/codex/implementation/phase-01a-job-posting.md`
  - `docs/codex/implementation/phase-01b-job-posting-public-read.md`
  - `docs/codex/07-implementation-history.md`
- Scope:
  - Checked the current branch and confirmed local `main` matches `origin/main`.
  - Reconciled Phase 01a documentation with the actual repository/service/controller behavior.
  - Rewrote Phase 01a and Phase 01b class-by-class documentation into the required table format.
  - Confirmed admin list/detail separation: pageable list has no collection fetch, detail lookups use `@EntityGraph`.
  - Confirmed public reads expose only `PUBLISHED` postings and use the same not-found exception for hidden or nonexistent detail records.
- Notes:
  - No code, API, entity, or configuration changes were made for this integration check.
  - Did not add Application/Stage/Interview/Message/CommonCode domains.

## 2026-05-14 - Phase 02 Stage Design

- Document: `docs/codex/design/phase-02-stage-design.md`
- Scope: Designed Phase 02a as JobPosting child Stage management and deferred StageResult until after the Application domain.
- Notes: Documentation-only design work; no Java code or new domain classes were added.

## Phase 02a-1 - Stage Basic CRUD

- 작업일: 2026-05-14
- 목적: `JobPosting` 하위 전형단계(`Stage`)의 관리자 기본 CRUD 기반을 추가했다.
- 핵심 구현:
  - `Stage` Entity와 `StageType`, `StageStatus` enum 추가
  - `StageRepository`, `StageService`, `StageController` 추가
  - Stage 생성/목록/상세/수정 API 추가
  - `stageOrder` 중복과 `finalStage=true` 중복을 Service 검증으로 차단
  - `CLOSED` JobPosting의 Stage 생성/수정 차단
  - `READY` 상태 Stage만 일반 수정 허용
  - `@Valid` 실패 응답을 `ApiResponse.fail()` 형식으로 처리
- 주요 클래스:
  - `Stage`
  - `StageRepository`
  - `StageService`
  - `StageController`
  - `StageCreateRequest`
  - `StageUpdateRequest`
  - `StageListResponse`
  - `StageDetailResponse`
  - `StageNotFoundException`
  - `InvalidStageException`
- API:
  - `GET /admin/job-postings/{jobPostingId}/stages`
  - `GET /admin/job-postings/{jobPostingId}/stages/{stageId}`
  - `POST /admin/job-postings/{jobPostingId}/stages`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}`
- 테스트 결과:
  - `StageServiceTest` 성공
  - `StageControllerTest` 성공
  - 전체 `clean test` 성공
- 남은 이슈:
  - reorder/start/announce/close/delete command는 Phase 02a-2로 분리
  - `StageResult`는 `Application` 도메인 이후로 보류
  - `stageOrder` DB unique 제약과 동시성 제어는 reorder 정책 확정 이후 재검토
  - `Stage.update()` 상태 방어는 현재 Service 책임으로 유지
- 다음 작업:
  - Phase 02a-2에서 Stage reorder와 상태 command, delete 정책 구현

## Phase 02a-2 - Stage Reorder and Commands

- 작업일: 2026-05-14
- 목적: Phase 02a-1 Stage 기본 CRUD 위에 reorder, 상태 전이, 삭제 command를 추가했다.
- 핵심 구현:
  - Stage reorder command 추가
  - `READY -> IN_PROGRESS -> RESULT_ANNOUNCED -> CLOSED` 상태 전이 command 추가
  - READY Stage 물리 삭제 command 추가
  - reorder 요청 DTO와 nested validation 추가
  - `ConstraintViolationException`도 `ApiResponse.fail()` 형식으로 처리
  - Service 테스트에 reorder/status/delete 정책 검증 추가
- 주요 클래스:
  - `Stage`
  - `StageService`
  - `StageController`
  - `StageOrderRequest`
  - `StageReorderRequest`
  - `GlobalExceptionHandler`
  - `StageServiceTest`
  - `StageControllerTest`
- API:
  - `POST /admin/job-postings/{jobPostingId}/stages/reorder`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/start`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/announce`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/close`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/delete`
- 테스트 결과:
  - `StageServiceTest` 성공
  - `StageControllerTest` 성공
  - 전체 `clean test` 성공
- 남은 이슈:
  - DB unique 제약과 동시성 제어는 아직 보류
  - Application/StageResult 도입 후 진행 중 Stage 수정/삭제 정책 재검토 필요
- 다음 작업:
  - Phase 02a-3 Controller/API 테스트 보강 또는 Application 기본 흐름 구현 결정

## Phase 02a-3 - Stage Controller API Test

- 작업일: 2026-05-14
- 목적: Phase 02a Stage 관리자 API의 path, method, 응답 포맷을 Controller 테스트로 고정했다.
- 핵심 구현:
  - `StageControllerTest`에 CRUD API 성공 응답 검증 추가
  - reorder/start/announce/close/delete command API 성공 응답 검증 추가
  - validation 실패, Stage 미존재, 잘못된 상태 command 실패 응답 검증 추가
  - PUT 및 DELETE HTTP method 미지원 정책 검증 추가
  - Phase 02a-3 구현 문서 생성 및 Phase 02 설계/구현 문서 정합성 보완
- 주요 클래스:
  - `StageControllerTest`
- API:
  - `GET /admin/job-postings/{jobPostingId}/stages`
  - `GET /admin/job-postings/{jobPostingId}/stages/{stageId}`
  - `POST /admin/job-postings/{jobPostingId}/stages`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}`
  - `POST /admin/job-postings/{jobPostingId}/stages/reorder`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/start`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/announce`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/close`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/delete`
- 테스트 결과:
  - `StageControllerTest` 성공
  - 전체 `clean test` 성공
- 남은 이슈:
  - Stage 공개 노출 API는 아직 구현하지 않았다.
  - JobPosting publish 조건에 Stage 최소 1개 검증은 아직 추가하지 않았다.
  - StageResult는 Application 도메인 이후로 보류한다.
- 다음 작업:
  - Application 기본 흐름 구현을 우선 검토한다.

## 2026-05-14 - Phase 03 Application Design

- Document: `docs/codex/design/phase-03-application-design.md`
- Scope: Designed the applicant Application basic flow as the foundation for later StageResult implementation.
- Key decisions: Use `JobApplication` as the recommended Java class name while keeping Application as the API/document term; split Phase 03 into applicant basic flow, admin read APIs, detail sections, and later StageResult.
- Review update: Fixed Phase 03a-1 decisions for `applicant_id + job_posting_id` unique, `JobPositionRepository` lookup, and applicant name snapshot source.
- Notes: Documentation-only design work; no Java code or new domain classes were added.

## Phase 03a-1 - Application Basic Create/Read

- 작업일: 2026-05-14
- 목적: 지원자 Application 루트의 기본 생성/조회 기반을 추가했다.
- 핵심 구현:
  - `JobApplication` Entity와 `JobApplicationStatus` enum 추가
  - `JobApplicationRepository`, `JobPositionRepository` 추가
  - `JobApplicationService.create`, `getApplication`, `getMyApplicationByJobPosting` 추가
  - `applicant_id + job_posting_id` DB unique 제약과 Service 중복 검증 추가
  - PUBLISHED/접수기간/모집분야 소속/ApplicationFormConfig 존재 검증 추가
  - 지원자명, 공고명, 모집분야명 snapshot 저장
- 주요 클래스:
  - `JobApplication`
  - `JobApplicationStatus`
  - `JobApplicationRepository`
  - `JobPositionRepository`
  - `ApplicationCreateRequest`
  - `ApplicationDetailResponse`
  - `JobApplicationService`
  - `JobApplicationNotFoundException`
  - `InvalidJobApplicationException`
  - `JobApplicationServiceTest`
- API:
  - 없음. 이번 Phase에서는 Controller를 만들지 않았다.
- 테스트 결과:
  - `JobApplicationServiceTest` 성공
  - 전체 `clean test` 성공
- 남은 이슈:
  - `ApplicationController`, `updateDraft`, `submit`, `withdraw`는 후속 Phase로 분리
  - 현재 로그인 Applicant 식별 방식은 Controller 도입 전 확정 필요
  - 철회 후 재지원 허용 시 unique 제약 재검토 필요
  - 동시 unique 충돌 예외 변환과 Applicant not-found 응답 정책은 Controller/API 단계에서 재검토
- 다음 작업:
  - Phase 03a-2에서 updateDraft/submit/withdraw command 구현 여부 검토
