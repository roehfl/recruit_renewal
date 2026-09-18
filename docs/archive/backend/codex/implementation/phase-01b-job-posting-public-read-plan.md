# Phase 01b Plan - Public JobPosting Read API

## Summary

- Implement public/applicant read APIs using the existing Phase 01a `JobPosting`, `JobPosition`, and `ApplicationFormConfig` model only.
- Before Phase 01b implementation, resolve existing Git conflict markers in JobPosting Phase 01a files by keeping the documented review-fix behavior:
  - admin list returns `PageResponse`
  - update remains `POST /admin/job-postings/{id}`
- Public APIs expose only `PUBLISHED` postings.
- `DRAFT` and `CLOSED` postings are not visible through public APIs.

## Key Changes

- Add public read endpoints:
  - `GET /job-postings?page={page}&size={size}`
  - `GET /job-postings/{id}`
- Add public DTOs, separate from admin DTOs:
  - `JobPostingPublicListResponse`
  - `JobPostingPublicDetailResponse`
  - `JobPositionPublicResponse`
  - `ApplicationFormConfigPublicResponse`
- Add a dedicated read-only `JobPostingPublicService` to keep public lookup rules separate from admin lifecycle commands.
- Extend `JobPostingRepository` with explicit `PUBLISHED` queries:
  - paged list by status
  - detail lookup by `id + status` with `@EntityGraph` for positions/config
- Public list response includes:
  - `id`
  - `title`
  - `receptionStartDateTime`
  - `receptionEndDateTime`
  - `accepting`
- Public detail response includes:
  - list fields
  - `contentHtml`
  - sorted positions
  - application form config
- `accepting` is computed as:
  - `now >= receptionStartDateTime && now <= receptionEndDateTime`
- Reuse `JobPostingNotFoundException` for hidden or nonexistent public detail records so unpublished state is not leaked.

## Tests

- Add `JobPostingPublicServiceTest`.
- Cover:
  - `PUBLISHED` posting appears in public list
  - `DRAFT` posting does not appear in list
  - `CLOSED` posting does not appear in list
  - `PUBLISHED` detail lookup succeeds
  - `DRAFT` detail lookup fails
  - `CLOSED` detail lookup fails
  - detail response includes positions and application form config
  - `accepting` is true/false based on reception period
- Keep or update existing `JobPostingServiceTest` after conflict resolution so admin `PageResponse` behavior remains covered.

## Documentation

- Create or update `docs/codex/implementation/phase-01b-job-posting-public-read.md` during implementation.
- Update `docs/codex/07-implementation-history.md` during implementation.
- Preserve the current roadmap cleanup in `docs/codex/06-implementation-roadmap.md`.
- Do not reintroduce `PUT` or conflict markers.

## Verification

- Run after implementation:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test
```

- Current pre-implementation baseline note:
  - the repository contains conflict markers in `JobPostingRepository`, `JobPostingService`, `JobPostingController`, and `JobPostingServiceTest`
  - a Gradle wrapper run required network access and timed out during distribution/test setup
- Final acceptance requires `clean test` to pass after conflict cleanup and Phase 01b implementation.

## Assumptions

- Public list shows all `PUBLISHED` postings regardless of reception period.
- The reception period only affects the `accepting` flag.
- No new `Application`, `Stage`, `Interview`, `Message`, or `CommonCode` domain is introduced.
- No large `SecurityConfig` change is needed because current config already permits all requests during this development phase.
