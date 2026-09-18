# Phase 03i-2 - Attachment File Upload

## Phase Summary

Phase 03i-2 implements applicant-owned single physical file upload for application attachments:

```text
POST /applications/{applicationId}/attachments/files
```

The phase keeps the existing metadata JSON APIs, but hardens metadata replace so it manages only `METADATA_ONLY` rows and cannot delete or mutate file-backed rows. Download, admin upload, delete, orphan cleanup, S3/NAS, dashboard readiness, submit validator changes, and `downloadAvailable` remain out of scope.

## Purpose

- Add real multipart upload while preserving the existing attachment metadata response shape.
- Store physical bytes through a local filesystem abstraction.
- Mark file-backed rows explicitly with `physicalFileStatus=STORED`.
- Keep storage internals out of applicant/admin responses.
- Prevent the existing metadata replace API from orphaning stored files.

## Implemented Scope

- Added `PhysicalFileStatus` enum with `METADATA_ONLY`, `STORED`, and `MISSING`.
- Added `ApplicationAttachment.physicalFileStatus` as `@Enumerated(EnumType.STRING)`, `nullable=false`, length `30`, default `METADATA_ONLY`.
- Hardened `POST /applications/{applicationId}/attachments` to delete/replace only `METADATA_ONLY` rows.
- Added metadata replace validation so requested metadata `sortOrder` values cannot conflict with existing `STORED` row `sortOrder` values.
- Changed metadata replace to reject client-supplied `storedFileName` and `storagePath` with 400.
- Added `AttachmentProperties` under `recruit.attachment`.
- Enabled multipart parsing and added parser size limits.
- Added `AttachmentStorageService` and `LocalAttachmentStorageService`.
- Added `AttachmentFilePolicy` for file, filename, extension, content type, and file-size validation.
- Added `ApplicationAttachmentFileService` for upload orchestration, ownership/writable checks, STORED count/total-size limits, file storage, rollback cleanup, DB save, and response mapping.
- Added `POST /applications/{applicationId}/attachments/files`.
- Added service/controller tests for upload, metadata hardening, forbidden fields, limits, ownership, and response exposure.
- Updated design/history documents and added this implementation reference plus a self-contained HTML report.

## Out-of-Scope Items

- Applicant/admin download endpoint.
- Admin upload endpoint.
- File delete endpoint.
- Orphan cleanup scheduler/job.
- S3, NAS, object storage, virus scan, or DLP integration.
- Dashboard readiness changes.
- Submit validator changes.
- `SecurityConfig` changes.
- `downloadAvailable` response field.
- File-backed row reorder/update endpoint.
- File-backed row metadata edit API.
- Attachment required policy.

## Changed Files

| Path | Type | Notes |
|---|---|---|
| `src/main/java/com/shinyoung/recruit/enumeration/PhysicalFileStatus.java` | New | Physical file lifecycle enum |
| `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationAttachment.java` | Modified | Added `physicalFileStatus` and `createStored(...)` |
| `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationAttachmentRepository.java` | Modified | Added status delete/count/sum and max sort-order queries |
| `src/main/java/com/shinyoung/recruit/dto/request/AttachmentRequest.java` | Modified | Retains forbidden storage fields for explicit validation, no longer requires them |
| `src/main/java/com/shinyoung/recruit/config/AttachmentProperties.java` | New | Attachment upload configuration with startup validation |
| `src/main/java/com/shinyoung/recruit/service/AttachmentStorageService.java` | New | Storage abstraction |
| `src/main/java/com/shinyoung/recruit/service/LocalAttachmentStorageService.java` | New | Local filesystem storage implementation |
| `src/main/java/com/shinyoung/recruit/service/StoredAttachmentFile.java` | New | Stored file result record |
| `src/main/java/com/shinyoung/recruit/service/AttachmentFilePolicy.java` | New | File validation and filename normalization |
| `src/main/java/com/shinyoung/recruit/service/ApplicationAttachmentFileService.java` | New | Applicant upload orchestration |
| `src/main/java/com/shinyoung/recruit/service/ApplicationAttachmentService.java` | Modified | Metadata replace hardening |
| `src/main/java/com/shinyoung/recruit/controller/ApplicationAttachmentController.java` | Modified | Added multipart upload endpoint and forbidden part detection |
| `src/main/java/com/shinyoung/recruit/exception/GlobalExceptionHandler.java` | Modified | Added multipart/request parameter failure handling |
| `src/main/resources/application.yaml` | Modified | Enabled multipart and added `recruit.attachment` defaults |
| `src/test/resources/application.yaml` | Modified | Enabled multipart and test storage root |
| `src/test/java/com/shinyoung/recruit/service/ApplicationAttachmentServiceTest.java` | Modified | Metadata hardening and STORED preservation coverage |
| `src/test/java/com/shinyoung/recruit/service/ApplicationAttachmentFileServiceTest.java` | New | Upload service/policy coverage |
| `src/test/java/com/shinyoung/recruit/controller/ApplicationAttachmentControllerTest.java` | Modified | Multipart API and forbidden field coverage |
| `docs/codex/implementation/phase-03i-2-attachment-file-upload.md` | New | Implementation reference |
| `docs/codex/reports/phase-03i-2-attachment-file-upload.html` | New | Human-readable report |
| `docs/codex/design/phase-03i-attachment-file-upload-download-design.md` | Modified | Added implementation note |
| `docs/codex/design/phase-03c-application-detail-design.md` | Modified | Added implementation note |
| `docs/codex/design/phase-03-application-design.md` | Modified | Added implementation note |
| `docs/codex/07-implementation-history.md` | Modified | Added Phase 03i-2 history |

## New Classes

| Package | Class | Class Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.enumeration` | `PhysicalFileStatus` | Enum | Attachment physical file lifecycle status |
| `com.shinyoung.recruit.config` | `AttachmentProperties` | Config | Binds `recruit.attachment` upload/storage limits |
| `com.shinyoung.recruit.service` | `AttachmentStorageService` | Service | Storage abstraction for storing/deleting physical files |
| `com.shinyoung.recruit.service` | `LocalAttachmentStorageService` | Service | Local filesystem implementation with root containment |
| `com.shinyoung.recruit.service` | `StoredAttachmentFile` | Response-like record | Internal result of physical storage |
| `com.shinyoung.recruit.service` | `AttachmentFilePolicy` | Service | Multipart file validation and filename policy |
| `com.shinyoung.recruit.service` | `ApplicationAttachmentFileService` | Service | Applicant upload orchestration |
| `com.shinyoung.recruit.service` | `ApplicationAttachmentFileServiceTest` | Test | Upload service tests |

## Modified Classes

| Package | Class | Class Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.domain.entity` | `ApplicationAttachment` | Entity | Adds physical file state |
| `com.shinyoung.recruit.domain.repository` | `ApplicationAttachmentRepository` | Repository | Status-aware metadata and upload queries |
| `com.shinyoung.recruit.dto.request` | `AttachmentRequest` | Request DTO | Keeps forbidden storage fields detectable |
| `com.shinyoung.recruit.service` | `ApplicationAttachmentService` | Service | Metadata replace remains metadata-only |
| `com.shinyoung.recruit.controller` | `ApplicationAttachmentController` | Controller | Adds applicant upload endpoint |
| `com.shinyoung.recruit.exception` | `GlobalExceptionHandler` | Exception | Maps multipart/request parameter failures to 400 |
| `src/main/resources` | `application.yaml` | Config | Multipart and attachment properties |
| `src/test/resources` | `application.yaml` | Test config | Multipart and test storage root |
| `com.shinyoung.recruit.service` | `ApplicationAttachmentServiceTest` | Test | Metadata regression tests |
| `com.shinyoung.recruit.controller` | `ApplicationAttachmentControllerTest` | Test | API contract tests |

## Class-by-Class Explanation

| Package | Class | Type | Responsibility | Key fields or methods | Related classes | Important implementation notes |
|---|---|---|---|---|---|---|
| `enumeration` | `PhysicalFileStatus` | Enum | Distinguishes metadata-only and physical file rows | `METADATA_ONLY`, `STORED`, `MISSING` | `ApplicationAttachment` | `MISSING` is reserved for Phase 03i-3 download/missing-file policy |
| `domain.entity` | `ApplicationAttachment` | Entity | Attachment metadata row | `physicalFileStatus`, `create(...)`, `createStored(...)` | `JobApplication`, `AttachmentResponse` | Java default is `METADATA_ONLY`; upload uses `createStored(...)` |
| `domain.repository` | `ApplicationAttachmentRepository` | Repository | Attachment persistence | `deleteByJobApplicationIdAndPhysicalFileStatus`, `findSortOrdersBy...`, `countBy...`, `sumFileSizeBy...`, `findMaxSortOrderBy...` | `ApplicationAttachmentFileService`, `ApplicationAttachmentService` | Count/total-size limits and metadata sort conflict checks use `STORED` rows; named query parameters use `@Param` |
| `dto.request` | `AttachmentRequest` | Request DTO | JSON metadata item | storage fields retained, no required constraint | `ApplicationAttachmentService` | `storedFileName`/`storagePath` are explicitly rejected when non-null |
| `config` | `AttachmentProperties` | Config | Upload/storage policy | storage root, max size, max files, max total, allowlists | `AttachmentFilePolicy`, `LocalAttachmentStorageService` | Uses `@Validated`, non-empty allowlists, positive sizes, and positive file count |
| `service` | `AttachmentFilePolicy` | Service | Validates multipart file | `validate(MultipartFile)` | `AttachmentProperties` | Rejects missing/empty/oversized files, path separators, control chars, reserved names, disallowed extensions/content types |
| `service` | `AttachmentStorageService` | Service | Storage contract | `store`, `deleteIfExists`, `exists` | `LocalAttachmentStorageService` | Keeps future S3/NAS migration behind an interface |
| `service` | `LocalAttachmentStorageService` | Service | Local byte storage | root normalization, generated path, idempotent delete | `StoredAttachmentFile`, `AttachmentProperties` | Stores under generated relative keys and verifies final path stays under root |
| `service` | `StoredAttachmentFile` | Internal record | Storage result | `storedFileName`, `storagePath`, `contentType`, `fileSize` | `ApplicationAttachmentFileService` | Not exposed through API |
| `service` | `ApplicationAttachmentFileService` | Service | Upload orchestration | `upload(...)` | access service, repository, policy, storage | Uses `saveAndFlush(...)`; registers rollback cleanup for the just-stored file |
| `service` | `ApplicationAttachmentService` | Service | Metadata replace/read | `replaceAttachments(...)` | `ApplicationAttachmentRepository` | Deletes only `METADATA_ONLY`; preserves `STORED`; creates metadata rows as `METADATA_ONLY` |
| `controller` | `ApplicationAttachmentController` | Controller | Applicant attachment HTTP APIs | `uploadAttachmentFile(...)` | `CurrentApplicantService`, `ApplicationAttachmentFileService` | Rejects forbidden multipart parts before service call |
| `exception` | `GlobalExceptionHandler` | Exception | API error mapping | request parameter and multipart handlers | `ApiResponse` | Invalid multipart/request values return 400 JSON |
| `service test` | `ApplicationAttachmentFileServiceTest` | Test | Upload service coverage | success, limits, validation, ownership | Spring Boot test, H2, local storage | Uses test storage root and fixed clock |
| `service test` | `ApplicationAttachmentServiceTest` | Test | Metadata regression coverage | STORED preservation, forbidden fields | Repository, service | Confirms metadata replace no longer deletes file-backed rows |
| `controller test` | `ApplicationAttachmentControllerTest` | Test | API contract coverage | multipart success, forbidden parts, response fields | MockMvc | Confirms storage internals and `downloadAvailable` are absent |

## API List

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| `POST` | `/applications/{applicationId}/attachments/files` | Applicant uploads one physical attachment file for an owned writable application | `multipart/form-data`: required `file`, `attachmentType`, `sectionType`; optional `sectionRecordId` | `ApiResponse<AttachmentResponse>` |
| `GET` | `/applications/{applicationId}/attachments` | Existing applicant metadata read | unchanged | unchanged |
| `POST` | `/applications/{applicationId}/attachments` | Existing applicant metadata replace | JSON metadata only; `storedFileName`/`storagePath` rejected | unchanged response shape |

Not implemented:

| Method | Path | Status |
|---|---|---|
| `GET` | `/applications/{applicationId}/attachments/{attachmentId}/download` | Deferred to Phase 03i-3 |
| `GET` | `/admin/applications/{applicationId}/attachments/{attachmentId}/download` | Deferred to Phase 03i-3 |
| `POST` | `/admin/applications/{applicationId}/attachments/files` | Deferred |
| `DELETE` | attachment delete endpoint | Deferred |

## Entity Relationship Summary

- `JobApplication` owns many `ApplicationAttachment` rows.
- `ApplicationAttachment.physicalFileStatus=METADATA_ONLY` rows are created by the JSON metadata replace API.
- `ApplicationAttachment.physicalFileStatus=STORED` rows are created only by the multipart upload API.
- `ApplicationAttachment.physicalFileStatus=MISSING` is not assigned in Phase 03i-2.
- `storedFileName`, `storagePath`, and `physicalFileStatus` remain internal fields and are not exposed by `AttachmentResponse` or `AdminAttachmentResponse`.

## Validation And Business Rules

- Upload is applicant-only under the existing `/applications/**` security policy; `SecurityConfig` was not changed.
- Current applicant id comes from `CurrentApplicantService`.
- Other applicant's application remains hidden by the existing 404 ownership policy.
- Upload is allowed only when the application is `DRAFT`, posting is `PUBLISHED`, and current time is inside the reception period.
- Upload request must not include `sortOrder`, `displayName`, `originalFileName`, `storedFileName`, or `storagePath` multipart parts.
- Server derives `originalFileName` from sanitized multipart original filename.
- Server generates UUID-based `storedFileName` and relative `storagePath`.
- Upload assigns append-only `sortOrder = max(sortOrder for all rows in application) + 1`, or `0` when no row exists.
- `max-files-per-application` counts only `physicalFileStatus=STORED`.
- `max-total-size-per-application` sums only `fileSize` for `physicalFileStatus=STORED`.
- `METADATA_ONLY` rows are excluded from physical file count/total-size limits.
- File validation rejects missing/empty files, oversized files, missing/invalid extension, disallowed content type, path separators, control characters, Windows reserved base names, and filenames over 255 chars.
- Local storage normalizes final path and verifies it stays under `storageRoot`.
- On DB failure or transaction rollback after physical storage, cleanup attempts to delete only the just-stored file.
- Metadata replace deletes/replaces only `METADATA_ONLY` rows and preserves `STORED` rows unchanged.
- Metadata replace fails with 400 when a submitted metadata row `sortOrder` conflicts with an existing `STORED` row `sortOrder`.
- Attachment configuration fails startup validation for null storage root, non-positive size/count limits, or empty allowlists.

## Test Commands

Targeted verification completed:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationAttachmentServiceTest --tests com.shinyoung.recruit.service.ApplicationAttachmentFileServiceTest --tests com.shinyoung.recruit.controller.ApplicationAttachmentControllerTest --no-daemon
```

Full verification should be run before merge:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test --no-daemon
```

## Test Results

- Targeted attachment tests: success.
- Initial targeted test attempt failed because the sandbox blocked Gradle distribution download.
- A second attempt timed out and left Gradle test result files locked.
- After `.\gradlew.bat --stop`, targeted tests passed with `--no-daemon`.
- Full `clean test --no-daemon`: success after review fix rerun with a longer timeout.

## Test Coverage

- Metadata replace success and read success.
- Metadata replace deletes/replaces only `METADATA_ONLY` rows.
- Metadata replace preserves `STORED` rows and does not update their sort/type/section.
- Metadata replace rejects metadata `sortOrder` conflicts with existing `STORED` rows.
- Metadata replace rejects `storedFileName` and `storagePath`.
- Response DTO excludes `storedFileName`, `storagePath`, `physicalFileStatus`, and `downloadAvailable`.
- Upload creates `STORED` rows.
- Upload generates internal storage filename/path.
- Upload stores bytes under local storage root.
- Upload appends sort order based on current application max sort order.
- Upload count and total-size limits use only `STORED` rows.
- Invalid filename, extension, content type, and ownership/writable cases fail.
- Multipart API returns `ApiResponse<AttachmentResponse>`.
- Multipart API rejects forbidden request parts.

## Known Limitations

- No download endpoint exists yet.
- No admin upload, replace, or download endpoint exists.
- No explicit delete command or orphan cleanup job exists.
- No S3/NAS/object storage implementation exists.
- No malware scanning or DLP integration exists.
- `MISSING` status is defined but not assigned by runtime code in Phase 03i-2.
- Local filesystem storage assumes single-node or shared-volume deployment.
- Metadata rows still store server-generated placeholder storage fields because current DB columns are non-null.

## Remaining Issues

- Decide Phase 03i-3 download behavior for missing physical files and whether to update rows to `MISSING`.
- Decide whether to expose `downloadAvailable` after download semantics exist.
- Decide admin upload/download/delete workflow.
- Decide required attachment policy and dashboard/submit integration.
- Decide production storage backend before multi-node deployment.

## Next Phase Recommendation

Phase 03i-3 should implement applicant/admin download:

- Allow download only for owned/admin-authorized `physicalFileStatus=STORED` rows.
- Return streaming file response, not `ApiResponse`.
- Use safe `Content-Disposition` with ASCII fallback and UTF-8 filename.
- Keep `storedFileName`, `storagePath`, and storage root hidden.
- Define behavior when the DB row is `STORED` but the physical file is missing.
