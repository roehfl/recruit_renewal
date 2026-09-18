# Phase 03j-1 - Job Posting Domain Expansion Status

> **갱신 노트 (2026-05-28):** `JobPosition.headcount` 필드는 불필요하다고 판단되어 이후 제거되었다. 아래 본문의 `headcount` 검증/필드 언급은 역사적 기록이며 현재 코드와 일치하지 않는다. 현재 상태는 `docs/codex/07-implementation-history.md`의 "JobPosition headcount 필드 제거" 항목을 참조한다.

## Phase Summary

Phase 03j-1 implements the internal and admin-side domain expansion for job postings and job positions.

This phase extends the existing `JobPosting` and `JobPosition` model with posting classification, display policy, reception status calculation, and richer position metadata. It also tightens the new application creation guard so applicants can create a new application only for postings that are published, currently accepting, visible, and inside the display period.

## Purpose

- Store admin-managed job posting type, summary, visibility, pinning, display order, and display period.
- Store job position application type, job group, job title, work location, and employment type.
- Preserve existing admin endpoint paths and command flow.
- Keep public listing/filter/sort changes out of this phase.
- Make admin responses expose the newly persisted fields and computed reception state.

## Implemented Scope

- Added `JobPostingType`, `JobPositionApplicationType`, and `ReceptionStatus` enums.
- Extended `JobPosting` with `postingType`, `summary`, `displayStartDateTime`, `displayEndDateTime`, `visible`, `pinned`, and `displayOrder`.
- Extended `JobPosition` with `applicationType`, `jobGroup`, `jobTitle`, `workLocation`, and `employmentType`.
- Extended admin create/update request DTOs to accept the new fields.
- Extended admin list/detail response DTOs to return new posting fields plus `receptionStatus` and `accepting`.
- Extended job position responses with the new position fields.
- Added summary HTML tag rejection, display-period validation, and duplicate position `sortOrder` validation.
- Added Service-level direct-call validation for DTO constraints that were previously covered only by Controller Bean Validation.
- Added grouped position count lookup for admin posting list to avoid loading `jobPositions` per row for `positionCount`.
- Applied default values for omitted new fields to keep backward compatibility.
- Changed new application creation guard to require visible and display-period eligibility.
- Preserved existing update, submit, and withdraw guards as status plus reception-period checks.
- Fixed `ApplicationFormConfig` update to mutate the existing one-to-one row instead of replacing it.
- Added and updated service/controller tests for the new fields and guards.

## Out of Scope

- Public applicant-facing job posting API changes.
- Public listing filter/sort changes for visible, pinned, display order, or posting type.
- Public response shape changes.
- Database migration scripts for non-H2 environments.
- Security or authorization rule changes.
- Admin UI or static frontend resources.

## Changed Files

| Path | Change Type | Notes |
|---|---|---|
| `src/main/java/com/shinyoung/recruit/enumeration/JobPostingType.java` | New | Posting type enum |
| `src/main/java/com/shinyoung/recruit/enumeration/JobPositionApplicationType.java` | New | Position application type enum |
| `src/main/java/com/shinyoung/recruit/enumeration/ReceptionStatus.java` | New | Computed reception status enum |
| `src/main/java/com/shinyoung/recruit/domain/entity/JobPosting.java` | Modified | Added posting/display fields and update defaults |
| `src/main/java/com/shinyoung/recruit/domain/entity/JobPosition.java` | Modified | Added position metadata fields and defaults |
| `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationFormConfig.java` | Modified | Added in-place update method for one-to-one config |
| `src/main/java/com/shinyoung/recruit/domain/repository/JobPositionCountProjection.java` | New | Projection for grouped position counts |
| `src/main/java/com/shinyoung/recruit/domain/repository/JobPositionRepository.java` | Modified | Added grouped count query by posting ids |
| `src/main/java/com/shinyoung/recruit/dto/request/JobPostingCreateRequest.java` | Modified | Added admin create fields |
| `src/main/java/com/shinyoung/recruit/dto/request/JobPostingUpdateRequest.java` | Modified | Added admin update fields |
| `src/main/java/com/shinyoung/recruit/dto/request/JobPositionRequest.java` | Modified | Added position metadata fields |
| `src/main/java/com/shinyoung/recruit/dto/response/JobPostingListResponse.java` | Modified | Added admin list fields and computed reception state |
| `src/main/java/com/shinyoung/recruit/dto/response/JobPostingDetailResponse.java` | Modified | Added admin detail fields and computed reception state |
| `src/main/java/com/shinyoung/recruit/dto/response/JobPositionResponse.java` | Modified | Added position metadata response fields |
| `src/main/java/com/shinyoung/recruit/service/JobPostingService.java` | Modified | Added defaults, validations, and response status calculation |
| `src/main/java/com/shinyoung/recruit/service/JobApplicationService.java` | Modified | Added visible/display guard only to new application creation |
| `src/test/java/com/shinyoung/recruit/service/JobPostingServiceTest.java` | Modified | Added domain expansion service tests |
| `src/test/java/com/shinyoung/recruit/controller/JobPostingControllerTest.java` | Modified | Added admin API expansion tests |
| `src/test/java/com/shinyoung/recruit/service/JobApplicationServiceTest.java` | Modified | Added application creation guard regression tests |
| `docs/codex/implementation/phase-03j-1-job-posting-domain-expansion-status.md` | New | Codex implementation reference |
| `docs/codex/reports/phase-03j-1-job-posting-domain-expansion-report.html` | New | Human-readable report |
| `docs/codex/07-implementation-history.md` | Modified | Phase history entry |

## New Classes

| Package | Class | Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.enumeration` | `JobPostingType` | Enum | Classifies postings as public, experienced, intern, or rolling recruitment |
| `com.shinyoung.recruit.enumeration` | `JobPositionApplicationType` | Enum | Classifies position eligibility as new graduate, experienced, or combined |
| `com.shinyoung.recruit.enumeration` | `ReceptionStatus` | Enum | Computes `UPCOMING`, `ACCEPTING`, or `CLOSED` from reception start/end and current time |
| `com.shinyoung.recruit.domain.repository` | `JobPositionCountProjection` | Repository projection | Provides grouped position count results for admin list mapping |

## Modified Classes

| Package | Class | Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.domain.entity` | `JobPosting` | Entity | Stores posting metadata, reception period, display policy, and status |
| `com.shinyoung.recruit.domain.entity` | `JobPosition` | Entity | Stores recruitment position metadata under a posting |
| `com.shinyoung.recruit.domain.entity` | `ApplicationFormConfig` | Entity | Stores per-posting application section flags |
| `com.shinyoung.recruit.domain.repository` | `JobPositionRepository` | Repository | Reads positions and grouped position counts |
| `com.shinyoung.recruit.dto.request` | `JobPostingCreateRequest` | Request DTO | Accepts admin posting create payload |
| `com.shinyoung.recruit.dto.request` | `JobPostingUpdateRequest` | Request DTO | Accepts admin posting update payload |
| `com.shinyoung.recruit.dto.request` | `JobPositionRequest` | Request DTO | Accepts admin position payload |
| `com.shinyoung.recruit.dto.response` | `JobPostingListResponse` | Response DTO | Returns admin posting list item |
| `com.shinyoung.recruit.dto.response` | `JobPostingDetailResponse` | Response DTO | Returns admin posting detail |
| `com.shinyoung.recruit.dto.response` | `JobPositionResponse` | Response DTO | Returns position metadata |
| `com.shinyoung.recruit.service` | `JobPostingService` | Service | Owns admin posting commands, validation, and response mapping |
| `com.shinyoung.recruit.service` | `JobApplicationService` | Service | Owns applicant application commands and creation eligibility |
| `com.shinyoung.recruit.service` | `JobPostingServiceTest` | Test | Verifies service-level posting expansion rules |
| `com.shinyoung.recruit.controller` | `JobPostingControllerTest` | Test | Verifies admin API expansion behavior |
| `com.shinyoung.recruit.service` | `JobApplicationServiceTest` | Test | Verifies applicant creation guard behavior |

## Class-by-Class Explanation

| Class | Type | Key Fields / Methods | Related Classes | Implementation Notes |
|---|---|---|---|---|
| `JobPostingType` | Enum | posting type constants | `JobPosting`, request/response DTOs | Defaults to `PUBLIC_RECRUITMENT` when omitted |
| `JobPositionApplicationType` | Enum | application type constants | `JobPosition`, request/response DTOs | Defaults to `NEW_GRADUATE_OR_EXPERIENCED` when omitted |
| `ReceptionStatus` | Enum | `from(start, end, now)` | `JobPostingListResponse`, `JobPostingDetailResponse`, `JobApplicationService` | Null periods are treated as closed by current usage because posting validation requires reception dates |
| `JobPosting` | Entity | `postingType`, `summary`, `displayStartDateTime`, `displayEndDateTime`, `visible`, `pinned`, `displayOrder`, `updateBasicInfo(...)` | `JobPosition`, `ApplicationFormConfig` | New defaults preserve old constructors and old request constructors |
| `JobPosition` | Entity | `applicationType`, `jobGroup`, `jobTitle`, `workLocation`, `employmentType`, `create(...)` | `JobPosting` | New fields are persisted with enum string values where applicable |
| `ApplicationFormConfig` | Entity | `update(...)` | `JobPosting` | Prevents unique key collision by updating the existing one-to-one row |
| `JobPositionRepository` | Repository | `countByJobPostingIds(...)` | `JobPositionCountProjection` | Counts positions for one page of admin postings in a single grouped query |
| `JobPositionCountProjection` | Repository projection | `getJobPostingId`, `getPositionCount` | `JobPositionRepository` | Avoids list response N+1 caused by lazy collection size access |
| `JobPostingCreateRequest` | Request DTO | new posting display fields | `JobPostingService` | Keeps legacy constructor for existing tests and callers |
| `JobPostingUpdateRequest` | Request DTO | new posting display fields | `JobPostingService` | Keeps legacy constructor for existing tests and callers |
| `JobPositionRequest` | Request DTO | new position fields | `JobPostingService` | Keeps legacy constructor for existing tests and callers |
| `JobPostingListResponse` | Response DTO | `receptionStatus`, `accepting`, display fields, `positionCount` | `ReceptionStatus`, `JobPostingService` | `accepting` is true only when status is `PUBLISHED` and reception is `ACCEPTING`; list `positionCount` is supplied from grouped count query |
| `JobPostingDetailResponse` | Response DTO | new posting fields, positions, form config | `JobPositionResponse` | Admin detail includes internal display policy fields |
| `JobPositionResponse` | Response DTO | new position fields | `JobPosition` | Mirrors persisted position metadata |
| `JobPostingService` | Service | `create`, `update`, `getPositionCounts`, `validateSummary`, `validateDisplayOrder`, `validateJobPosition`, `validateDisplayPeriod`, `validateJobPositionSortOrders` | entities, repositories, and DTOs | Rejects invalid direct service inputs and maps admin list counts without lazy collection N+1 |
| `JobApplicationService` | Service | `validatePublishedAcceptingAndVisibleForCreate`, `isWithinDisplayPeriod` | `JobPosting`, `ReceptionStatus` | New create only guard includes visible/display period; existing commands still use status/reception |
| `JobPostingServiceTest` | Test | expansion and validation tests | `JobPostingService` | Covers defaults, update, computed reception state, and invalid inputs |
| `JobPostingControllerTest` | Test | admin create/detail/update/list tests | `JobPostingController` | Covers JSON request/response fields and invalid extended fields |
| `JobApplicationServiceTest` | Test | visibility/display guard tests | `JobApplicationService` | Confirms existing application commands ignore display policy after creation |

## API List

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| `GET` | `/admin/job-postings` | Admin posting list | `page`, `size` query params | `ApiResponse<PageResponse<JobPostingListResponse>>` with new admin fields |
| `GET` | `/admin/job-postings/{id}` | Admin posting detail | path id | `ApiResponse<JobPostingDetailResponse>` with new admin fields |
| `POST` | `/admin/job-postings` | Create posting | `JobPostingCreateRequest` with new optional fields | `ApiResponse<Long>` |
| `POST` | `/admin/job-postings/{id}` | Update posting | `JobPostingUpdateRequest` with new optional fields | `ApiResponse<Long>` |
| `POST` | `/admin/job-postings/{id}/publish` | Publish posting | path id | `ApiResponse<Long>` |
| `POST` | `/admin/job-postings/{id}/close` | Close posting | path id | `ApiResponse<Long>` |

No endpoint path was added in this phase.

## Entity Relationship Summary

- `JobPosting` owns many `JobPosition` rows through `@OneToMany(mappedBy = "jobPosting", cascade = ALL, orphanRemoval = true)`.
- `JobPosting` owns one `ApplicationFormConfig` row through `@OneToOne(mappedBy = "jobPosting", cascade = ALL, orphanRemoval = true)`.
- `JobPosition` references `JobPosting` through lazy `@ManyToOne`.
- `JobApplication` still references `JobPosting`, `JobPosition`, and `Applicant`; only its creation eligibility rules changed.

## Validation and Business Rules

- `title` and `contentHtml` remain required.
- `receptionEndDateTime` must be after `receptionStartDateTime`.
- `summary` must not contain HTML tags.
- `summary` must be 500 characters or less.
- `displayEndDateTime` may be equal to or after `displayStartDateTime` when both are present.
- `displayStartDateTime` and `displayEndDateTime` are optional.
- `displayOrder`, when provided, must be 0 or greater.
- At least one job position is required.
- Job position `positionName` is required and must be 100 characters or less.
- Job position `headcount` is required and must be 1 or greater.
- Job position `sortOrder` is required and must be 0 or greater.
- Job position `jobGroup`, `jobTitle`, and `workLocation` must each be 100 characters or less when provided.
- Job position `sortOrder` values must be unique within one request.
- Omitted posting fields default to:
  - `postingType = PUBLIC_RECRUITMENT`
  - `visible = true`
  - `pinned = false`
  - `displayOrder = 0`
- Omitted position fields default to:
  - `applicationType = NEW_GRADUATE_OR_EXPERIENCED`
  - `employmentType = FULL_TIME`
- New application creation requires:
  - posting status `PUBLISHED`
  - reception status `ACCEPTING`
  - `visible = true`
  - current time inside the display period when display period is configured
- Existing draft update, submit, and withdraw continue to check only posting status and reception period.

## Test Coverage

| Test Class | Coverage |
|---|---|
| `JobPostingServiceTest` | create/update defaults, extended fields, direct service validation for DTO constraints, summary HTML rejection, display period validation, duplicate sort order, computed `ReceptionStatus` and `accepting` |
| `JobPostingControllerTest` | admin JSON create/detail/list/update and invalid extended field responses |
| `JobApplicationServiceTest` | new application visibility/display guard and existing command compatibility |

## Test Commands

```bash
$env:AES_SECRET_KEY='<test-value>'; .\gradlew.bat test --tests com.shinyoung.recruit.service.JobPostingServiceTest --tests com.shinyoung.recruit.controller.JobPostingControllerTest --tests com.shinyoung.recruit.service.JobApplicationServiceTest --no-daemon

$env:AES_SECRET_KEY='<test-value>'; .\gradlew.bat clean test --no-daemon
```

## Test Results

- Result: success
- Executed: 2026-05-22
- Notes:
  - Targeted phase regression suite passed.
  - Full `clean test` passed.

## Known Limitations

- Public job posting API still does not filter by `visible`, display period, pinned, display order, or posting type.
- Public list sorting remains unchanged.
- No explicit database migration script was added for non-H2 databases.
- Display policy is enforced only for new application creation in this phase.
- Admin list `positionCount` uses a grouped count query; public list count/sort behavior remains Phase 03j-2 scope.

## Remaining Issues

- Define public applicant-facing posting exposure rules in Phase 03j-2.
- Decide whether admin list ordering should later use `pinned` and `displayOrder`.
- Add migration scripts before applying this schema change to a persistent shared database.

## Next Phase Recommendation

Proceed with Phase 03j-2 public job posting exposure after confirming the desired applicant-facing API contract:

- public list/detail visibility filtering,
- display-period filtering,
- pinned/display-order sorting,
- public response field subset,
- application create flow alignment with public exposure rules.
