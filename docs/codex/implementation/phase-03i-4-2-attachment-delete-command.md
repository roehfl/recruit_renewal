# Phase 03i-4-2 - Attachment Soft Delete Command

## Phase Summary

Phase 03i-4-2 implements the delete command slice from the Phase 03i-4 attachment lifecycle design.

Implemented command APIs:

```text
POST /applications/{applicationId}/attachments/{attachmentId}/delete
POST /admin/applications/{applicationId}/attachments/{attachmentId}/delete
```

The implementation uses soft lifecycle delete. `ApplicationAttachment` rows are retained and moved to `PhysicalFileStatus.DELETED`; normal metadata lists hide deleted rows, and download remains limited to `STORED` rows so deleted rows return controlled 404.

## Purpose

- Allow applicants to remove mistaken attachments while the application is still editable.
- Allow admins/recruit-admins to remove attachments from any application state currently supported by the application model, with a required reason.
- Preserve minimal delete audit data directly on the retained attachment row.
- Delete physical files only after the DB transaction commits.
- Keep storage internals out of API responses and logs.

## Implemented Scope

- Added `PhysicalFileStatus.DELETED`.
- Added `AttachmentDeleteActorType` with `APPLICANT` and `EMPLOYEE`.
- Added delete lifecycle fields to `ApplicationAttachment`:
  - `deletedAt`
  - `deletedBy`
  - `deletedByType`
  - `deletionReason`
- Added `ApplicationAttachment.markDeleted(...)`.
- Added applicant delete command.
- Added admin delete command with `AttachmentAdminDeleteRequest.reason`.
- Added user-safe `AttachmentDeleteResponse`.
- Added active attachment repository lookups excluding `DELETED`.
- Updated applicant/admin metadata reads to exclude `DELETED` rows.
- Preserved download service behavior: only `STORED` rows are downloadable, so `DELETED` rows return 404 without changing download API structure.
- Registered physical file deletion after transaction commit for previously `STORED` rows.
- Kept physical delete failure as log-only; DB soft delete is not rolled back.
- Added service and controller tests for success, validation, security, hidden 404, list filtering, download rejection, after-commit deletion, sort-order behavior, and response non-exposure.

## Out-of-Scope Items

- Orphan scan dry-run.
- Orphan cleanup execution.
- Admin repair command.
- Mark-missing command.
- Include-deleted metadata read.
- Separate full audit/history table.
- Upload API structure changes.
- Download API structure changes.
- Metadata replace API structure changes.
- `SecurityConfig` changes.
- Dashboard readiness changes.
- Submit validator changes.
- Attachment required policy.
- S3/NAS/object storage migration.
- Virus scan/DLP integration.
- `downloadAvailable` response field.
- HTTP DELETE method.

## Changed Files

| Path | Type | Notes |
|---|---|---|
| `src/main/java/com/shinyoung/recruit/enumeration/PhysicalFileStatus.java` | Modified | Added `DELETED` |
| `src/main/java/com/shinyoung/recruit/enumeration/AttachmentDeleteActorType.java` | New | Delete actor type enum |
| `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationAttachment.java` | Modified | Added delete lifecycle fields and `markDeleted(...)` |
| `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationAttachmentRepository.java` | Modified | Added active list and scoped active lookup methods |
| `src/main/java/com/shinyoung/recruit/dto/request/AttachmentAdminDeleteRequest.java` | New | Admin delete reason request |
| `src/main/java/com/shinyoung/recruit/dto/response/AttachmentDeleteResponse.java` | New | User-safe delete command response |
| `src/main/java/com/shinyoung/recruit/service/ApplicationAttachmentDeleteService.java` | New | Applicant/admin delete orchestration |
| `src/main/java/com/shinyoung/recruit/service/ApplicationAttachmentService.java` | Modified | Applicant metadata lists exclude `DELETED` rows |
| `src/main/java/com/shinyoung/recruit/service/AdminApplicationSectionService.java` | Modified | Admin metadata lists exclude `DELETED` rows |
| `src/main/java/com/shinyoung/recruit/service/LocalAttachmentStorageService.java` | Modified | Delete failure log no longer includes storage path |
| `src/main/java/com/shinyoung/recruit/controller/ApplicationAttachmentController.java` | Modified | Added applicant delete endpoint |
| `src/main/java/com/shinyoung/recruit/controller/AdminApplicationAttachmentController.java` | Modified | Added admin delete endpoint |
| `src/test/java/com/shinyoung/recruit/service/ApplicationAttachmentDeleteServiceTest.java` | New | Delete service/policy tests |
| `src/test/java/com/shinyoung/recruit/controller/ApplicationAttachmentDownloadControllerTest.java` | Modified | Added delete API/security tests |
| `docs/codex/implementation/phase-03i-4-2-attachment-delete-command.md` | New | Implementation reference |
| `docs/codex/reports/phase-03i-4-2-attachment-delete-command.html` | New | Human-readable report |
| `docs/codex/design/phase-03i-4-attachment-delete-cleanup-repair-design.md` | Modified | Added implementation note |
| `docs/codex/design/phase-03i-attachment-file-upload-download-design.md` | Modified | Added implementation note |
| `docs/codex/design/phase-03c-application-detail-design.md` | Modified | Added implementation note |
| `docs/codex/design/phase-03-application-design.md` | Modified | Added implementation note |
| `docs/codex/07-implementation-history.md` | Modified | Added Phase 03i-4-2 history |

## New Classes

| Package | Class | Class Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.enumeration` | `AttachmentDeleteActorType` | Enum | Distinguishes applicant self-delete from employee/admin delete |
| `com.shinyoung.recruit.dto.request` | `AttachmentAdminDeleteRequest` | Request DTO | Validates admin delete reason |
| `com.shinyoung.recruit.dto.response` | `AttachmentDeleteResponse` | Response DTO | Returns user-safe delete command result |
| `com.shinyoung.recruit.service` | `ApplicationAttachmentDeleteService` | Service | Validates delete policy, marks DB row deleted, registers after-commit physical delete |
| `com.shinyoung.recruit.service` | `ApplicationAttachmentDeleteServiceTest` | Test | Covers delete business rules and regression behavior |

## Modified Classes

| Package | Class | Class Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.enumeration` | `PhysicalFileStatus` | Enum | Adds `DELETED` lifecycle state |
| `com.shinyoung.recruit.domain.entity` | `ApplicationAttachment` | Entity | Retains attachment row and stores minimal delete lifecycle data |
| `com.shinyoung.recruit.domain.repository` | `ApplicationAttachmentRepository` | Repository | Adds active metadata and active scoped lookup methods |
| `com.shinyoung.recruit.service` | `ApplicationAttachmentService` | Service | Excludes deleted rows from applicant metadata reads |
| `com.shinyoung.recruit.service` | `AdminApplicationSectionService` | Service | Excludes deleted rows from admin metadata reads |
| `com.shinyoung.recruit.service` | `LocalAttachmentStorageService` | Service | Keeps delete failure logs free of storage path internals |
| `com.shinyoung.recruit.controller` | `ApplicationAttachmentController` | Controller | Adds applicant delete command endpoint |
| `com.shinyoung.recruit.controller` | `AdminApplicationAttachmentController` | Controller | Adds admin delete command endpoint |
| `com.shinyoung.recruit.controller` | `ApplicationAttachmentDownloadControllerTest` | Test | Adds delete command HTTP/security coverage |

## Class-by-Class Explanation

| Package | Class | Type | Responsibility | Key fields or methods | Related classes | Important implementation notes |
|---|---|---|---|---|---|---|
| `enumeration` | `PhysicalFileStatus` | Enum | Physical attachment lifecycle state | `METADATA_ONLY`, `STORED`, `MISSING`, `DELETED` | `ApplicationAttachment` | `DELETED` rows are hidden from normal lists and not downloadable |
| `enumeration` | `AttachmentDeleteActorType` | Enum | Delete actor type | `APPLICANT`, `EMPLOYEE` | `ApplicationAttachment` | Admin/recruit-admin delete is stored as `EMPLOYEE` |
| `domain.entity` | `ApplicationAttachment` | Entity | Attachment metadata/file row | `deletedAt`, `deletedBy`, `deletedByType`, `deletionReason`, `markDeleted(...)` | repository, delete service | Storage fields are preserved for audit/debug/cleanup reference; response DTOs still do not expose them |
| `domain.repository` | `ApplicationAttachmentRepository` | Repository | Attachment persistence | `findByJobApplicationIdAndPhysicalFileStatusNotOrderBySortOrderAscIdAsc`, `findByIdAndJobApplicationIdAndPhysicalFileStatusNot` | attachment services | Existing max sort-order query still sees all rows, including `DELETED` |
| `dto.request` | `AttachmentAdminDeleteRequest` | Request DTO | Admin delete request | `@NotBlank @Size(max=1000) String reason` | admin controller | Validation errors return 400 through existing handler |
| `dto.response` | `AttachmentDeleteResponse` | Response DTO | Safe delete response | `applicationId`, `attachmentId`, `deleted`, `physicalDeleteRequested`, `message` | delete service | No storage path, stored filename, physical status, or download flag |
| `service` | `ApplicationAttachmentDeleteService` | Service | Delete orchestration | `deleteForApplicant`, `deleteForAdmin`, after-commit physical delete registration | access service, repository, storage service | DB state changes before physical delete; physical delete failure logs and does not roll back DB |
| `service` | `ApplicationAttachmentService` | Service | Applicant metadata read/replace | `getAttachmentResponses(...)` | repository | Normal list excludes `DELETED`; metadata replace still preserves active `STORED` conflict behavior |
| `service` | `AdminApplicationSectionService` | Service | Admin lazy section read | `getAttachments(...)` | repository | Admin attachment section excludes `DELETED` rows by default |
| `service` | `LocalAttachmentStorageService` | Service | Local filesystem adapter | `deleteIfExists(...)` | delete service, upload rollback | Delete failure log does not include `storagePath` |
| `controller` | `ApplicationAttachmentController` | Controller | Applicant attachment APIs | `deleteAttachment(...)` | current applicant service, delete service | Bodyless POST command, existing `/applications/**` authorization |
| `controller` | `AdminApplicationAttachmentController` | Controller | Admin attachment APIs | `deleteAttachment(...)` | current employee service, delete service | Reason body is required and validated |
| `service` | `ApplicationAttachmentDeleteServiceTest` | Test | Delete policy coverage | after-commit, metadata list, hidden 404, admin reason, sort-order, response reflection | services/repositories | No class-level `@Transactional`, so after-commit behavior is actually verified |
| `controller` | `ApplicationAttachmentDownloadControllerTest` | Test | HTTP/security coverage | applicant/admin delete status and response assertions | MockMvc | Verifies no storage internals in JSON response |

## API List

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| `POST` | `/applications/{applicationId}/attachments/{attachmentId}/delete` | Applicant deletes own editable attachment | Empty body | `ApiResponse<AttachmentDeleteResponse>` |
| `POST` | `/admin/applications/{applicationId}/attachments/{attachmentId}/delete` | Admin/recruit-admin deletes attachment with reason | JSON `{ "reason": "..." }` | `ApiResponse<AttachmentDeleteResponse>` |

Preserved APIs:

| Method | Path | Status |
|---|---|---|
| `GET` | `/applications/{applicationId}/attachments` | Same response shape; now excludes `DELETED` rows |
| `POST` | `/applications/{applicationId}/attachments` | Same metadata replace structure |
| `POST` | `/applications/{applicationId}/attachments/files` | Same upload structure |
| `GET` | `/applications/{applicationId}/attachments/{attachmentId}/download` | Same download structure; `DELETED` remains 404 because only `STORED` is downloadable |
| `GET` | `/admin/applications/{applicationId}/attachments` | Same response shape; now excludes `DELETED` rows |
| `GET` | `/admin/applications/{applicationId}/attachments/{attachmentId}/download` | Same download structure |

## Entity Relationship Summary

- `JobApplication` owns many `ApplicationAttachment` rows.
- Delete commands require `attachment.jobApplication.id == applicationId`.
- Applicant delete additionally requires the current applicant to own the application.
- Soft delete does not break the relationship; the row remains attached to the application.
- `DELETED` rows remain useful for later audit/cleanup but are hidden from normal applicant/admin metadata reads.
- `STORED` rows request physical file deletion after the DB transaction commits.
- `METADATA_ONLY` rows move to `DELETED` without a physical delete request.
- Download still looks up only `STORED` rows, so `DELETED` rows are controlled 404.

## Validation And Business Rules

- Applicant delete:
  - current applicant's own application only;
  - application must be `DRAFT`;
  - job posting must be `PUBLISHED`;
  - current time must be inside reception period;
  - attachment must belong to the application;
  - attachment must not already be `DELETED`;
  - `SUBMITTED` and `WITHDRAWN` are rejected.
- Admin delete:
  - application must exist;
  - attachment must belong to the application;
  - attachment must not already be `DELETED`;
  - `DRAFT`, `SUBMITTED`, and `WITHDRAWN` are allowed;
  - `reason` is required and limited to 1000 characters;
  - reason is trimmed before persistence.
- Exception policy:
  - application missing, attachment/application mismatch, other applicant resource, and already deleted row return 404;
  - non-writable applicant states/windows return 400;
  - admin blank or oversized reason returns 400;
  - anonymous and wrong-role access follow existing security handlers.
- Response exposure:
  - delete response exposes `applicationId`, `attachmentId`, `deleted`, `physicalDeleteRequested`, and `message`;
  - delete response does not expose `storedFileName`, `storagePath`, `storageRoot`, absolute path, `physicalFileStatus`, or `downloadAvailable`.
- Ordering:
  - upload append still uses max sort order across all rows, including `DELETED`;
  - metadata replace can reuse a deleted row's visible sort order because deleted rows are excluded from normal metadata list and active conflict checks.

## Transaction / Physical Delete Strategy

- The DB row is marked `DELETED` inside the service transaction.
- The previous `storagePath` is captured before the DB state transition.
- For previously `STORED` rows with a nonblank storage key, `TransactionSynchronization.afterCommit()` deletes the physical file.
- If transaction synchronization is not active, the service falls back to immediate physical deletion.
- `AttachmentStorageService.deleteIfExists(...)` is idempotent for missing files.
- Physical delete failure is logged only and does not roll back the committed DB soft delete.
- Logs avoid full storage path exposure.

## Test Commands

Targeted verification:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*ApplicationAttachmentDelete*" --no-daemon
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*ApplicationAttachment*" --no-daemon
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*AdminApplicationSection*" --no-daemon
```

Full verification:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test --no-daemon
```

## Test Results

| Command | Result | Notes |
|---|---|---|
| `test --tests "*ApplicationAttachmentDelete*" --no-daemon` | Success | Delete service/policy coverage passed |
| `test --tests "*ApplicationAttachment*" --no-daemon` | Success | Attachment upload/download/delete regression passed |
| `test --tests "*AdminApplicationSection*" --no-daemon` | Success | First sandbox run was blocked by Gradle distribution network access; rerun with approval passed |
| `clean test --no-daemon` | Success | Full regression passed |

## Test Coverage

- Applicant owner can delete `STORED` rows from editable `DRAFT` applications.
- Applicant owner can delete `METADATA_ONLY` rows without physical delete request.
- Deleted rows are marked `PhysicalFileStatus.DELETED`.
- Applicant delete stores `deletedBy`, `deletedByType=APPLICANT`, fixed self-delete reason, and fixed-clock `deletedAt`.
- Admin delete stores trimmed reason, `deletedBy`, and `deletedByType=EMPLOYEE`.
- Admin delete works for `SUBMITTED` and `WITHDRAWN` applications.
- Other applicant access returns hidden 404.
- Applicant `SUBMITTED`, `WITHDRAWN`, unpublished posting, and outside reception period delete fail.
- Attachment/application mismatch and already deleted rows return 404.
- Metadata lists for applicant and admin exclude deleted rows.
- Deleted rows cannot be downloaded.
- `STORED` delete removes the physical file after commit.
- Missing physical file still allows DB soft delete.
- Upload after delete uses max sort order including deleted rows.
- Metadata replace is not blocked by deleted row sort order.
- Delete response record components do not expose storage/status internals.
- Applicant/admin POST delete HTTP responses use `ApiResponse<AttachmentDeleteResponse>`.
- Applicant/admin delete security blocks wrong roles and anonymous users.
- Admin blank reason returns 400.

## Known Limitations

- There is no include-deleted audit read API.
- There is no separate immutable deletion history table.
- Physical delete completion/failure is not persisted to DB.
- Orphan physical file scan and cleanup are still deferred.
- Admin repair and mark-missing commands are still deferred.
- Required attachment readiness and submit validation do not yet account for attachment requirements.
- Local filesystem storage remains the active backend.

## Remaining Issues

- Decide whether a later audit phase needs immutable `ApplicationAttachmentDeletionHistory`.
- Decide how to persist physical delete failure state if operational monitoring requires it.
- Add orphan scan dry-run before any destructive cleanup phase.
- Define admin include-deleted read permissions and response shape.
- Integrate active `STORED` attachment checks into required attachment/dashboard/submit policy in Phase 03i-5.

## Next Phase Recommendation

Recommended next phase: Phase 03i-4-3 orphan storage scan dry-run.

The scan should report orphan physical files, `STORED` rows with missing files, and `DELETED` rows whose physical files still exist, without deleting anything.
