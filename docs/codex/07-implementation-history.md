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
