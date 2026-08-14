# Phase 03i-3 - Attachment File Download

## Phase Summary

Phase 03i-3 implements applicant and admin physical attachment download for rows created by Phase 03i-2 upload:

```text
GET /applications/{applicationId}/attachments/{attachmentId}/download
GET /admin/applications/{applicationId}/attachments/{attachmentId}/download
```

Successful responses are streaming file responses (`ResponseEntity<Resource>`), not `ApiResponse`. Error responses continue through the existing Spring Security handlers and `GlobalExceptionHandler` JSON failure shape.

## Purpose

- Allow applicants to download only their own stored attachment files.
- Allow admins/recruit-admins to download stored attachments by application id.
- Keep storage internals out of headers and error bodies.
- Return browser-safe download headers, including Korean filename support.
- Treat `METADATA_ONLY`, `MISSING`, missing physical files, and attachment/application mismatches as controlled 404 cases.

## Implemented Scope

- Added applicant download endpoint.
- Added admin download endpoint.
- Added download service that authorizes by applicant ownership or admin application scope.
- Extended storage abstraction with a `load(...)` operation.
- Implemented local filesystem load with root-containment validation.
- Added download response factory for file headers.
- Added repository methods for attachment/application scoped lookup.
- Added service, controller, and storage tests for success, authorization, missing file, status filtering, safe headers, Korean filename encoding, and non-exposure of storage internals.
- Review fix: `LocalAttachmentStorageService` now rejects absolute `storagePath` values before resolving under the storage root, even when the absolute path would point under the configured root.

## Out-of-Scope Items

- Upload API redesign.
- Metadata replace behavior changes.
- File delete endpoint.
- Admin upload endpoint.
- Orphan cleanup scheduler/job.
- Automatic `MISSING` status update when a physical file is absent.
- Dashboard readiness changes.
- Submit validator changes.
- Attachment required policy.
- S3/NAS/object storage migration.
- Virus scan or DLP integration.
- `downloadAvailable` response field.
- `SecurityConfig` changes.

## Changed Files

| Path | Type | Notes |
|---|---|---|
| `src/main/java/com/shinyoung/recruit/service/AttachmentStorageResource.java` | New | Internal storage load result |
| `src/main/java/com/shinyoung/recruit/service/AttachmentDownloadResource.java` | New | Internal download result |
| `src/main/java/com/shinyoung/recruit/service/AttachmentStorageService.java` | Modified | Added `load(String storagePath)` |
| `src/main/java/com/shinyoung/recruit/service/LocalAttachmentStorageService.java` | Modified | Added file load, not-found handling, absolute-path rejection, and path containment validation |
| `src/main/java/com/shinyoung/recruit/service/ApplicationAttachmentDownloadService.java` | New | Applicant/admin download authorization and resource assembly |
| `src/main/java/com/shinyoung/recruit/controller/AttachmentDownloadResponseFactory.java` | New | Builds streaming response headers |
| `src/main/java/com/shinyoung/recruit/controller/ApplicationAttachmentController.java` | Modified | Added applicant download endpoint |
| `src/main/java/com/shinyoung/recruit/controller/AdminApplicationAttachmentController.java` | New | Added admin download endpoint |
| `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationAttachmentRepository.java` | Modified | Added scoped attachment lookup methods |
| `src/test/java/com/shinyoung/recruit/service/ApplicationAttachmentDownloadServiceTest.java` | New | Download service tests |
| `src/test/java/com/shinyoung/recruit/service/LocalAttachmentStorageServiceTest.java` | New | Storage load tests |
| `src/test/java/com/shinyoung/recruit/controller/ApplicationAttachmentDownloadControllerTest.java` | New | Download API/security/header tests |
| `docs/codex/implementation/phase-03i-3-attachment-file-download.md` | New | Implementation reference |
| `docs/codex/reports/phase-03i-3-attachment-file-download.html` | New | Human-readable report |
| `docs/codex/design/phase-03i-attachment-file-upload-download-design.md` | Modified | Added Phase 03i-3 implementation note |
| `docs/codex/design/phase-03c-application-detail-design.md` | Modified | Added Phase 03i-3 implementation note |
| `docs/codex/design/phase-03-application-design.md` | Modified | Added Phase 03i-3 implementation note |
| `docs/codex/07-implementation-history.md` | Modified | Added Phase 03i-3 history |

## New Classes

| Package | Class | Class Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.service` | `AttachmentStorageResource` | Response DTO / internal record | Holds loaded `Resource` and actual byte length |
| `com.shinyoung.recruit.service` | `AttachmentDownloadResource` | Response DTO / internal record | Holds streaming resource, content length, content type, and original filename |
| `com.shinyoung.recruit.service` | `ApplicationAttachmentDownloadService` | Service | Resolves applicant/admin download permissions and loads stored file bytes |
| `com.shinyoung.recruit.controller` | `AttachmentDownloadResponseFactory` | Controller helper | Converts download resource into `ResponseEntity<Resource>` with safe headers |
| `com.shinyoung.recruit.controller` | `AdminApplicationAttachmentController` | Controller | Handles admin download endpoint |
| `com.shinyoung.recruit.service` | `ApplicationAttachmentDownloadServiceTest` | Test | Service download policy coverage |
| `com.shinyoung.recruit.service` | `LocalAttachmentStorageServiceTest` | Test | Local storage load and path-containment coverage |
| `com.shinyoung.recruit.controller` | `ApplicationAttachmentDownloadControllerTest` | Test | HTTP download response and security coverage |

## Modified Classes

| Package | Class | Class Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.service` | `AttachmentStorageService` | Service | Adds storage load contract |
| `com.shinyoung.recruit.service` | `LocalAttachmentStorageService` | Service | Loads existing files and rejects missing/invalid paths as controlled 404 |
| `com.shinyoung.recruit.controller` | `ApplicationAttachmentController` | Controller | Adds applicant-owned download route |
| `com.shinyoung.recruit.domain.repository` | `ApplicationAttachmentRepository` | Repository | Adds application-scoped download lookup methods |

## Class-by-Class Explanation

| Package | Class | Type | Responsibility | Key fields or methods | Related classes | Important implementation notes |
|---|---|---|---|---|---|---|
| `service` | `AttachmentStorageResource` | Internal record | Represents a file loaded from storage | `Resource resource`, `long contentLength` | `LocalAttachmentStorageService` | Uses actual physical file size, not DB size |
| `service` | `AttachmentDownloadResource` | Internal record | Represents a download-ready attachment | resource, content length, content type, original filename | `AttachmentDownloadResponseFactory` | Not an API JSON DTO |
| `service` | `AttachmentStorageService` | Service | Storage abstraction | `load(String storagePath)` | `LocalAttachmentStorageService` | Keeps future storage backends behind the same contract |
| `service` | `LocalAttachmentStorageService` | Service | Local filesystem storage | `load(...)`, `resolveUnderRoot(...)` | `AttachmentProperties` | Blank, absolute, traversal, missing, and non-regular files throw `JobApplicationNotFoundException` without exposing paths |
| `service` | `ApplicationAttachmentDownloadService` | Service | Download authorization and file load | `downloadForApplicant(...)`, `downloadForAdmin(...)` | repository, access service, storage service | Only looks up `physicalFileStatus=STORED`; logs size mismatch and missing physical file |
| `controller` | `AttachmentDownloadResponseFactory` | Controller helper | Streaming response header creation | `toResponse(...)` | `AttachmentDownloadResource` | Adds `Content-Disposition` with ASCII `filename` and UTF-8 `filename*` |
| `controller` | `ApplicationAttachmentController` | Controller | Applicant attachment APIs | `downloadAttachmentFile(...)` | `CurrentApplicantService`, download service | Success returns `ResponseEntity<Resource>`; errors remain JSON |
| `controller` | `AdminApplicationAttachmentController` | Controller | Admin attachment download | `downloadAttachmentFile(...)` | download service | Relies on existing `/admin/**` authorization |
| `domain.repository` | `ApplicationAttachmentRepository` | Repository | Attachment lookup | `findByIdAndJobApplicationId...` | download service | Attachment/application mismatch is handled as 404 |

## API List

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| `GET` | `/applications/{applicationId}/attachments/{attachmentId}/download` | Applicant-owned attachment download | Path ids only | Streaming file response, not `ApiResponse` |
| `GET` | `/admin/applications/{applicationId}/attachments/{attachmentId}/download` | Admin attachment download | Path ids only | Streaming file response, not `ApiResponse` |

Preserved APIs:

| Method | Path | Status |
|---|---|---|
| `POST` | `/applications/{applicationId}/attachments/files` | Unchanged upload API |
| `GET` | `/applications/{applicationId}/attachments` | Unchanged metadata read |
| `POST` | `/applications/{applicationId}/attachments` | Unchanged metadata-only replace |
| `GET` | `/admin/applications/{applicationId}/attachments` | Unchanged admin metadata read |

## Entity Relationship Summary

- `JobApplication` owns many `ApplicationAttachment` rows.
- Download requires `attachment.jobApplication.id == applicationId`.
- Applicant download additionally requires the application belongs to the current applicant.
- Admin download verifies the application exists and the attachment belongs to that application.
- Only `ApplicationAttachment.physicalFileStatus=STORED` rows are downloadable.
- `METADATA_ONLY` and `MISSING` rows are treated as not found for download.
- A `STORED` row whose physical file is absent stays `STORED` in Phase 03i-3 and returns controlled 404.

## Validation And Business Rules

- Applicant path uses existing `/applications/**` security and `ROLE_APPLICANT`.
- Admin path uses existing `/admin/**` security and `ROLE_ADMIN` or `ROLE_RECRUIT_ADMIN`.
- `SecurityConfig` was not changed.
- Applicant identity is resolved through `CurrentApplicantService`.
- Other applicants and attachment/application mismatches return hidden 404.
- Applicant downloads are allowed for owned `DRAFT`, `SUBMITTED`, and `WITHDRAWN` applications.
- Admin/recruit-admin downloads are allowed for `DRAFT`, `SUBMITTED`, and `WITHDRAWN` applications.
- Successful downloads return `ResponseEntity<Resource>`.
- Failure responses remain JSON through existing exception/security handlers.
- `Content-Type` uses the DB content type, with `application/octet-stream` fallback for blank values.
- `Content-Length` uses the actual physical file size.
- DB `fileSize` mismatch is logged as a server warning and is not exposed to clients.
- `Content-Disposition` uses `attachment`, ASCII `filename` fallback, and UTF-8 percent-encoded `filename*`.
- `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`, and `Pragma: no-cache` are included.
- Response headers and bodies do not expose `storedFileName`, `storagePath`, `storageRoot`, or absolute local paths.
- Storage paths stored in DB are treated strictly as server-generated relative keys; absolute keys are rejected with controlled 404.

## Test Commands

Targeted verification completed:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*ApplicationAttachmentDownload*" --no-daemon
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*AttachmentStorage*" --no-daemon
```

Additional verification completed:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*ApplicationAttachment*" --no-daemon
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test --no-daemon
```

## Test Results

| Command | Result | Notes |
|---|---|---|
| `test --tests "*ApplicationAttachmentDownload*" --no-daemon` | Success | Initial sandbox run failed because Gradle distribution download was blocked; rerun with approval passed |
| `test --tests "*AttachmentStorage*" --no-daemon` | Success | Initial sandbox run failed because Gradle distribution download was blocked; rerun with approval passed |
| `test --tests "*ApplicationAttachment*" --no-daemon` | Success | Initial sandbox run failed because Gradle distribution download was blocked; rerun with approval passed |
| `clean test --no-daemon` | Success | Full regression passed |

## Test Coverage

- Applicant owner can download `STORED` attachment from `DRAFT`, `SUBMITTED`, and `WITHDRAWN` applications.
- Other applicant access returns hidden 404.
- Attachment/application mismatch returns 404.
- Admin/recruit-admin can download `STORED` attachment.
- Missing application returns 404.
- `METADATA_ONLY` and `MISSING` rows return 404.
- `STORED` row with missing physical file returns controlled 404.
- Missing physical file does not auto-update DB status to `MISSING`.
- Actual bytes, content type, and content length are returned.
- Actual physical size is used when DB `fileSize` differs.
- Blank content type falls back to `application/octet-stream`.
- Korean filename is emitted through `filename*`.
- `nosniff`, `no-store`, and `no-cache` headers are included.
- Anonymous applicant/admin download returns 401 JSON.
- Wrong-role applicant/admin download returns 403 JSON.
- Error responses do not expose storage internals.
- Local storage load succeeds, missing files fail, path traversal is blocked, and absolute storage paths are rejected.

## Known Limitations

- Download still depends on local filesystem storage and assumes single-node or shared-volume deployment.
- Missing physical files are not automatically marked `MISSING` in this phase.
- No delete endpoint or orphan cleanup exists yet.
- No file-backed reorder/update endpoint exists.
- No `downloadAvailable` field is exposed in metadata responses.
- No malware scan/DLP integration exists.

## Remaining Issues

- Decide Phase 03i-4 orphan cleanup/admin repair policy for missing physical files.
- Decide explicit delete behavior and audit requirements.
- Decide admin upload/replace policy.
- Decide whether metadata responses should later include a computed `downloadAvailable`.
- Decide attachment required policy and dashboard/submit integration.

## Next Phase Recommendation

Recommended next phase: Phase 03i-4 attachment delete/orphan cleanup/admin repair design or implementation.

Priority decisions:

- Whether missing physical files should be marked `MISSING` by a scheduled job, admin repair command, or explicit download-side repair workflow.
- How physical delete and DB row delete should be ordered and audited.
- Whether admins may upload/replace applicant attachments.
