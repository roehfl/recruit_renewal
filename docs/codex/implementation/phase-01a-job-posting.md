# Phase 01a - JobPosting Vertical Slice

## 1. Phase summary

Phase 01a implements the first admin vertical slice for recruitment posting management. It introduces a minimal but working domain/API/test set for JobPosting lifecycle handling (create, update, publish, close) without expanding into Application/Stage/Interview/Message domains.

## 2. Implemented scope

- Domain entities
  - `JobPosting`
  - `JobPosition`
  - `ApplicationFormConfig`
- Enum
  - `JobPostingStatus` (`DRAFT`, `PUBLISHED`, `CLOSED`)
- Repository
  - `JobPostingRepository`
- Service
  - `JobPostingService`
- Controller
  - `JobPostingController`
- DTO
  - Request: `JobPostingCreateRequest`, `JobPostingUpdateRequest`, `JobPositionRequest`, `ApplicationFormConfigRequest`
  - Response: `JobPostingListResponse`, `JobPostingDetailResponse`, `JobPositionResponse`, `ApplicationFormConfigResponse`
- Exception
  - `JobPostingNotFoundException`
  - `InvalidJobPostingException`
- Test
  - `JobPostingServiceTest`

## 3. Changed files

- `src/main/java/com/shinyoung/recruit/controller/JobPostingController.java`
- `src/main/java/com/shinyoung/recruit/domain/entity/JobPosting.java`
- `src/main/java/com/shinyoung/recruit/domain/entity/JobPosition.java`
- `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationFormConfig.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/JobPostingRepository.java`
- `src/main/java/com/shinyoung/recruit/service/JobPostingService.java`
- `src/main/java/com/shinyoung/recruit/enumeration/JobPostingStatus.java`
- `src/main/java/com/shinyoung/recruit/exception/JobPostingNotFoundException.java`
- `src/main/java/com/shinyoung/recruit/exception/InvalidJobPostingException.java`
- `src/main/java/com/shinyoung/recruit/dto/request/JobPostingCreateRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/JobPostingUpdateRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/JobPositionRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/ApplicationFormConfigRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/response/JobPostingListResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/JobPostingDetailResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/JobPositionResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/ApplicationFormConfigResponse.java`
- `src/test/java/com/shinyoung/recruit/service/JobPostingServiceTest.java`
- `src/main/java/com/shinyoung/recruit/exception/GlobalExceptionHandler.java`

## 4. New classes

All files listed above were newly added in Phase 01a.

## 5. Modified classes

- `JobPostingService` (list response changed to PageResponse with paging parameters)
- `JobPostingController` (list endpoint now accepts page/size and returns PageResponse)
- `JobPostingRepository` (paging query method added)
- `JobPostingServiceTest` (list assertion updated for paging response)

## 6. Class-by-class explanation

### 6.1 `com.shinyoung.recruit.domain.entity.JobPosting`
- class type: Entity
- responsibility: Root aggregate for recruitment posting metadata and lifecycle status.
- key fields or methods:
  - fields: `title`, `contentHtml`, `receptionStartDateTime`, `receptionEndDateTime`, `status`, `publishedAt`, `closedAt`
  - methods: `create`, `updateBasicInfo`, `replaceJobPositions`, `updateApplicationFormConfig`, `publish`, `close`
- related classes: `JobPosition`, `ApplicationFormConfig`, `JobPostingStatus`
- implementation notes:
  - status defaults to `DRAFT` at creation.
  - status update is intentionally separated into command methods (`publish`, `close`).

### 6.2 `com.shinyoung.recruit.domain.entity.JobPosition`
- class type: Entity
- responsibility: Represents each position row under a job posting.
- key fields or methods:
  - fields: `jobPosting`, `positionName`, `headcount`, `sortOrder`
  - methods: `create`, `assignJobPosting`
- related classes: `JobPosting`
- implementation notes:
  - many-to-one lazy relationship to `JobPosting`.

### 6.3 `com.shinyoung.recruit.domain.entity.ApplicationFormConfig`
- class type: Entity
- responsibility: Posting-level configuration flags for application form sections.
- key fields or methods:
  - fields: `useEducation`, `useCareer`, `useCertificate`, `useLanguage`, `useMilitary`, `useAward`, `useGapPeriod`
  - methods: `create`, `assignJobPosting`
- related classes: `JobPosting`
- implementation notes:
  - one-to-one relationship using unique `job_posting_id`.

### 6.4 `com.shinyoung.recruit.domain.repository.JobPostingRepository`
- class type: Repository
- responsibility: Persistence entry for JobPosting aggregate.
- key fields or methods:
  - `findAllByOrderByCreatedAtDesc()` with `@EntityGraph(jobPositions, applicationFormConfig)`
- related classes: `JobPosting`
- implementation notes:
  - reduces lazy loading issues for list retrieval.

### 6.5 `com.shinyoung.recruit.service.JobPostingService`
- class type: Service
- responsibility: Handles use-cases and business validation for posting lifecycle.
- key fields or methods:
  - commands: `create`, `update`, `publish`, `close`
  - queries: `getJobPostings`, `getJobPosting`
  - validations: title required, reception period valid, at least one position
- related classes: `JobPostingRepository`, all request/response DTOs, custom exceptions
- implementation notes:
  - `@Transactional(readOnly = true)` class-level, command methods override with `@Transactional`.
  - closed posting update is blocked.

### 6.6 `com.shinyoung.recruit.controller.JobPostingController`
- class type: Controller
- responsibility: Admin REST API entrypoint for posting operations.
- key fields or methods:
  - endpoints: list/detail/create/update/publish/close
- related classes: `JobPostingService`, `ApiResponse`
- implementation notes:
  - update API uses `POST /admin/job-postings/{id}` per policy.

### 6.7 Request DTOs

#### `com.shinyoung.recruit.dto.request.JobPostingCreateRequest`
- class type: Request DTO
- responsibility: Accept posting create payload excluding status.
- key fields or methods: title/contentHtml/reception period/positions/form config
- related classes: `JobPositionRequest`, `ApplicationFormConfigRequest`
- notes: `@NotEmpty` on positions.

#### `com.shinyoung.recruit.dto.request.JobPostingUpdateRequest`
- class type: Request DTO
- responsibility: Accept posting update payload excluding status.
- key fields or methods: same as create DTO.
- related classes: `JobPositionRequest`, `ApplicationFormConfigRequest`
- notes: used by `POST /admin/job-postings/{id}`.

#### `com.shinyoung.recruit.dto.request.JobPositionRequest`
- class type: Request DTO
- responsibility: Position row input validation.
- key fields or methods: `positionName`, `headcount`, `sortOrder`
- related classes: `JobPostingCreateRequest`, `JobPostingUpdateRequest`
- notes: headcount >= 1, sortOrder >= 0.

#### `com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest`
- class type: Request DTO
- responsibility: Form config flags payload.
- key fields or methods: seven `use*` boolean flags.
- related classes: create/update request DTOs
- notes: minimal flag-only scope in Phase 01a.

### 6.8 Response DTOs

#### `com.shinyoung.recruit.dto.response.JobPostingListResponse`
- class type: Response DTO
- responsibility: List projection of posting summary.
- key fields or methods: includes status/publishedAt/closedAt
- related classes: `JobPosting`
- notes: static `from` mapper.

#### `com.shinyoung.recruit.dto.response.JobPostingDetailResponse`
- class type: Response DTO
- responsibility: Detail projection with nested positions and form config.
- key fields or methods: nested `jobPositions`, `applicationFormConfig`
- related classes: `JobPositionResponse`, `ApplicationFormConfigResponse`
- notes: positions sorted by `sortOrder` in mapper.

#### `com.shinyoung.recruit.dto.response.JobPositionResponse`
- class type: Response DTO
- responsibility: Position detail output model.
- key fields or methods: id/name/headcount/sortOrder
- related classes: `JobPosition`
- notes: static `from` mapper.

#### `com.shinyoung.recruit.dto.response.ApplicationFormConfigResponse`
- class type: Response DTO
- responsibility: Output model for form section flags.
- key fields or methods: seven `use*` flags
- related classes: `ApplicationFormConfig`
- notes: static `from` mapper.

### 6.9 `com.shinyoung.recruit.enumeration.JobPostingStatus`
- class type: Enum
- responsibility: Posting lifecycle state machine values.
- key fields or methods: `DRAFT`, `PUBLISHED`, `CLOSED`
- related classes: `JobPosting`, `JobPostingService`
- notes: persisted with `EnumType.STRING` in entity.

### 6.10 Exceptions

#### `com.shinyoung.recruit.exception.JobPostingNotFoundException`
- class type: Exception
- responsibility: Not-found condition for posting id lookups.
- key fields or methods: constructor(message)
- related classes: `JobPostingService`
- notes: used by detail/update/publish/close lookup path.

#### `com.shinyoung.recruit.exception.InvalidJobPostingException`
- class type: Exception
- responsibility: Business rule violation for posting operations.
- key fields or methods: constructor(message)
- related classes: `JobPostingService`
- notes: used in period/position/status transition validations.

### 6.11 `com.shinyoung.recruit.exception.GlobalExceptionHandler`
- class type: Exception
- responsibility: Map domain exceptions to HTTP response codes and ApiResponse failure body.
- key fields or methods: `handleJobPostingNotFound`, `handleInvalidJobPosting`
- related classes: `JobPostingNotFoundException`, `InvalidJobPostingException`, `ApiResponse`
- notes: returns 404 for not found, 400 for invalid request/business rule.

### 6.12 `com.shinyoung.recruit.service.JobPostingServiceTest`
- class type: Test
- responsibility: Verify service business rules and lifecycle transitions.
- key fields or methods: tests for create validation, publish/close transitions, not-found, list/detail
- related classes: `JobPostingService`, DTOs, exceptions
- notes: `@SpringBootTest` + `@Transactional`.

## 7. API list

- `GET /admin/job-postings?page={page}&size={size}`
- `GET /admin/job-postings/{id}`
- `POST /admin/job-postings`
- `POST /admin/job-postings/{id}`
- `POST /admin/job-postings/{id}/publish`
- `POST /admin/job-postings/{id}/close`

## 8. Entity relationship summary

- `JobPosting` 1 : N `JobPosition`
- `JobPosting` 1 : 1 `ApplicationFormConfig`
- All three entities are introduced only for posting-phase scope and do not depend on `Application`, `Stage`, `Interview`, `Message` in this phase.

## 9. Business rules

1. Posting title must not be blank.
2. `receptionEndDateTime` must be after `receptionStartDateTime`.
3. Create/update requires at least one job position.
4. New posting status is always `DRAFT`.
5. Publish requires valid period + at least one position.
6. `CLOSED -> PUBLISHED` transition is not allowed.
7. Close is allowed only from `PUBLISHED`.
8. Closed posting is not editable in general update API.

## 10. Test coverage

Covered by `JobPostingServiceTest`:
- create success
- invalid date period failure
- empty positions failure
- draft to published success
- closed to published blocked
- published to closed success
- not-found detail failure
- list/detail retrieval verification
- empty positions update failure

## 11. Known limitations

1. No dedicated global exception-to-HTTP mapping for new exceptions yet.
2. No role-based authorization rule specific to posting APIs yet.
3. Advanced filtering on list API is not implemented yet (paging only).
4. No audit log/notification side effects on publish/close yet.
5. No required-field validation matrix for form config (flag-only scope).

## 12. Next phase considerations

1. Add global exception handler mappings for posting exceptions.
2. Add list paging/filter and admin permission constraints.
3. Introduce `Stage` integration after posting lifecycle stabilizes.
4. Expand form config semantics from `use*` flags to required validation rules when Application domain is introduced.
