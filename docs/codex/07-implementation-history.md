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
