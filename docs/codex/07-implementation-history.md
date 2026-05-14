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
