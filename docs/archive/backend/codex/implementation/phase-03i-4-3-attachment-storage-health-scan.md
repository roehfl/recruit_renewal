# Phase 03i-4-3 - Attachment Storage Health Scan Dry-Run

## Phase Summary

Phase 03i-4-3 implements the read-only attachment storage health scan slice from the Phase 03i-4 lifecycle design.

The phase adds an admin dry-run API that compares retained attachment DB rows with local physical files under the configured attachment storage root. It reports mismatches without deleting files and without mutating attachment rows.

## Purpose

- Detect `STORED` DB rows whose physical file is missing.
- Detect `DELETED` DB rows whose physical file still remains.
- Detect managed physical files that have no active DB reference.
- Detect invalid persisted storage keys.
- Keep cleanup and repair decisions separate from the scan.

## Implemented Scope

- Added `POST /admin/attachments/storage-health/scan`.
- Response is `ApiResponse<AttachmentStorageHealthScanResponse>`.
- The response always returns `dryRun=true`.
- Scan reads local storage under `recruit.attachment.storage-root`.
- Scan compares physical files with `ApplicationAttachment` rows in `STORED`, `DELETED`, and `MISSING`.
- `METADATA_ONLY` rows are excluded from scan comparison.
- Managed file key pattern is `applications/{applicationId}/{yyyy}/{MM}/{dd}/{filename}`.
- Physical scan uses regular files without following symbolic links.
- Unknown unmanaged files are ignored and reported as `IGNORED_UNMANAGED_FILE`.
- Issue responses expose only IDs, row status, hashed file key, physical size, category, and safe messages.
- `storagePath`, `storedFileName`, storage root, absolute path, and raw relative file key are not exposed.
- Delete observability was improved with `AttachmentStorageDeleteResult`.
- Existing delete response shape remains unchanged.
- `ApplicationAttachmentDeleteService` logs structured post-commit physical delete results.

## Out of Scope

- Physical deletion of orphan candidates.
- Marking `STORED` rows as `MISSING`.
- Repair or reattach commands.
- Scheduler or persisted scan history.
- Include-deleted metadata read API.
- Separate deletion history table.
- `SecurityConfig` changes.
- Upload/download redesign.
- S3/NAS/object storage migration.

## Changed Files

| Path | Change Type | Notes |
|---|---|---|
| `src/main/java/com/shinyoung/recruit/controller/AdminAttachmentStorageHealthController.java` | New | Admin dry-run scan endpoint |
| `src/main/java/com/shinyoung/recruit/service/AttachmentStorageHealthScanService.java` | New | Read-only DB/storage comparison |
| `src/main/java/com/shinyoung/recruit/dto/response/AttachmentStorageHealthScanResponse.java` | New | Dry-run scan summary response |
| `src/main/java/com/shinyoung/recruit/dto/response/AttachmentStorageHealthIssueResponse.java` | New | Safe issue item response |
| `src/main/java/com/shinyoung/recruit/enumeration/AttachmentStorageHealthIssueType.java` | New | Scan issue categories |
| `src/main/java/com/shinyoung/recruit/service/AttachmentStorageDeleteResult.java` | New | Structured physical delete result |
| `src/main/java/com/shinyoung/recruit/service/AttachmentStorageService.java` | Modified | Added result-returning delete method while preserving old void method |
| `src/main/java/com/shinyoung/recruit/service/LocalAttachmentStorageService.java` | Modified | Returns delete result without exposing storage internals |
| `src/main/java/com/shinyoung/recruit/service/ApplicationAttachmentDeleteService.java` | Modified | Logs post-commit delete result details |
| `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationAttachmentRepository.java` | Modified | Added status-list lookup for scan |
| `src/main/java/com/shinyoung/recruit/exception/StorageHealthScanException.java` | New | Safe scan failure exception |
| `src/main/java/com/shinyoung/recruit/exception/GlobalExceptionHandler.java` | Modified | Handles scan failures as safe 500 responses |
| `src/test/java/com/shinyoung/recruit/service/AttachmentStorageHealthScanServiceTest.java` | New | Service scan categories and dry-run tests |
| `src/test/java/com/shinyoung/recruit/controller/AdminAttachmentStorageHealthControllerTest.java` | New | API response/security tests |
| `src/test/java/com/shinyoung/recruit/service/LocalAttachmentStorageServiceTest.java` | Modified | Delete result tests |
| `src/test/java/com/shinyoung/recruit/service/ApplicationAttachmentDeleteServiceTest.java` | Modified | Invalid storage delete result regression |
| `docs/codex/implementation/phase-03i-4-3-attachment-storage-health-scan.md` | New | Codex implementation reference |
| `docs/codex/reports/phase-03i-4-3-attachment-storage-health-scan.html` | New | Human-readable report |
| `docs/codex/design/phase-03i-4-attachment-delete-cleanup-repair-design.md` | Modified | Phase 03i-4-3 implementation note |
| `docs/codex/design/phase-03i-attachment-file-upload-download-design.md` | Modified | Phase 03i-4-3 implementation note |
| `docs/codex/design/phase-03c-application-detail-design.md` | Modified | Phase 03i-4-3 implementation note |
| `docs/codex/design/phase-03-application-design.md` | Modified | Phase 03i-4-3 implementation note |
| `docs/codex/07-implementation-history.md` | Modified | Phase history entry |

## New Classes

| Package | Class | Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.controller` | `AdminAttachmentStorageHealthController` | Controller | Exposes admin dry-run scan API |
| `com.shinyoung.recruit.service` | `AttachmentStorageHealthScanService` | Service | Compares DB rows and local physical files without mutation |
| `com.shinyoung.recruit.service` | `AttachmentStorageDeleteResult` | Service record | Represents result of physical delete attempts |
| `com.shinyoung.recruit.dto.response` | `AttachmentStorageHealthScanResponse` | Response DTO | Summarizes dry-run scan counts and issues |
| `com.shinyoung.recruit.dto.response` | `AttachmentStorageHealthIssueResponse` | Response DTO | Represents one safe scan issue |
| `com.shinyoung.recruit.enumeration` | `AttachmentStorageHealthIssueType` | Enum | Defines scan issue categories |
| `com.shinyoung.recruit.exception` | `StorageHealthScanException` | Exception | Reports safe scan failures |
| `com.shinyoung.recruit.service.AttachmentStorageHealthScanService` | `PhysicalFileInfo` | Internal record | Holds managed file key hash and size |
| `com.shinyoung.recruit.service.AttachmentStorageHealthScanService` | `PhysicalScanResult` | Internal record | Holds physical scan counts and managed files |
| `com.shinyoung.recruit.service.AttachmentStorageHealthScanService` | `AttachmentRowInfo` | Internal record | Holds row identity, status, safe key hash, and internal comparison key |
| `com.shinyoung.recruit.service.AttachmentStorageHealthScanService` | `RowScanResult` | Internal record | Holds row scan groups and counts |

## Modified Classes

| Package | Class | Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.service` | `AttachmentStorageService` | Service interface | Adds result-returning physical delete contract |
| `com.shinyoung.recruit.service` | `LocalAttachmentStorageService` | Service | Implements structured delete result and keeps root containment validation |
| `com.shinyoung.recruit.service` | `ApplicationAttachmentDeleteService` | Service | Logs physical delete result after DB soft delete commit |
| `com.shinyoung.recruit.domain.repository` | `ApplicationAttachmentRepository` | Repository | Provides status-list lookup for scan rows |
| `com.shinyoung.recruit.exception` | `GlobalExceptionHandler` | Exception handler | Converts scan failure to safe `ApiResponse.fail` 500 |

## Class-by-Class Explanation

| Class | Type | Key Fields / Methods | Related Classes | Implementation Notes |
|---|---|---|---|---|
| `AdminAttachmentStorageHealthController` | Controller | `scan()` | `AttachmentStorageHealthScanService`, `ApiResponse` | `POST /admin/attachments/storage-health/scan`; no request body; relies on existing `/admin/**` security |
| `AttachmentStorageHealthScanService` | Service | `scanDryRun()` | `ApplicationAttachmentRepository`, `AttachmentProperties`, `HashUtil` | `@Transactional(readOnly=true)`; scans files and rows, creates issues, never deletes or updates |
| `AttachmentStorageHealthScanResponse` | Response DTO | counts, `dryRun`, `scannedAt`, `issues` | `AttachmentStorageHealthIssueResponse` | Uses summary counts for physical files, row states, and issue categories |
| `AttachmentStorageHealthIssueResponse` | Response DTO | `category`, `applicationId`, `attachmentId`, `rowStatus`, `fileKeyHash`, `physicalFileSize`, `message` | `AttachmentStorageHealthIssueType` | Does not include raw storage key or path |
| `AttachmentStorageHealthIssueType` | Enum | scan category constants | scan service | Includes `STORED_MISSING_PHYSICAL_FILE`, `DELETED_PHYSICAL_FILE_REMAINING`, `ORPHAN_PHYSICAL_FILE`, `INVALID_STORAGE_PATH`, `MISSING_ROW_PHYSICAL_FILE_PRESENT`, `IGNORED_UNMANAGED_FILE` |
| `AttachmentStorageDeleteResult` | Service record | `requested`, `deleted`, `existed`, `failed`, `failureCode`, `message` | `AttachmentStorageService` | User-safe result for physical delete attempts |
| `AttachmentStorageService` | Service interface | `deleteIfExistsWithResult`, default `deleteIfExists` | `LocalAttachmentStorageService` | Keeps existing callers compatible |
| `LocalAttachmentStorageService` | Service | `deleteIfExistsWithResult` | `AttachmentStorageDeleteResult` | Invalid path and IO failures return structured failure without exposing path details |
| `ApplicationAttachmentDeleteService` | Service | `deletePhysicalFile` | `AttachmentStorageService` | Logs `applicationId`, `attachmentId`, `deleted`, `existed`, failure code, and safe message |
| `ApplicationAttachmentRepository` | Repository | `findByPhysicalFileStatusIn(...)` | `ApplicationAttachment` | Reads `STORED`, `DELETED`, `MISSING` rows for scan |
| `StorageHealthScanException` | Exception | constructors | `GlobalExceptionHandler` | Safe message only |

## API List

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| `POST` | `/admin/attachments/storage-health/scan` | Run read-only attachment storage health scan | none | `ApiResponse<AttachmentStorageHealthScanResponse>` |

## Response Fields

| Field | Notes |
|---|---|
| `dryRun` | Always `true` in Phase 03i-4-3 |
| `scannedAt` | Scan timestamp from injected `Clock` |
| `scannedPhysicalFileCount` | Regular physical files scanned under storage root |
| `managedPhysicalFileCount` | Files matching managed attachment key pattern |
| `ignoredPhysicalFileCount` | Unmanaged or symlink entries ignored |
| `storedRowCount` | `ApplicationAttachment` rows with `STORED` |
| `deletedRowCount` | Rows with `DELETED` |
| `missingRowCount` | Rows with `MISSING` |
| `storedMissingPhysicalFileCount` | `STORED` rows whose file is absent |
| `deletedPhysicalFileRemainingCount` | `DELETED` rows whose file still exists |
| `orphanPhysicalFileCount` | Managed files without active DB reference |
| `invalidStoragePathCount` | Rows with invalid persisted storage keys |
| `issues` | Safe issue list |

## Entity Relationship Summary

- `ApplicationAttachment` belongs to `JobApplication`.
- Scan reads `ApplicationAttachment.jobApplication.id` for safe application identity.
- Scan does not add relationships and does not change entity state.
- `METADATA_ONLY` rows remain metadata-only and are not compared to physical storage.
- `STORED`, `DELETED`, and `MISSING` rows are read for comparison.

## Business Rules

- Scan is dry-run only.
- Scan must not delete physical files.
- Scan must not mutate `ApplicationAttachment.physicalFileStatus`.
- Scan must not mark `STORED` rows as `MISSING`.
- Scan uses only server-managed storage keys under configured storage root.
- Absolute paths, blank paths, traversal paths, root escapes, and malformed managed keys are `INVALID_STORAGE_PATH`.
- Physical file listing uses local filesystem only and does not follow symbolic links.
- Managed physical file keys must match `applications/{applicationId}/{yyyy}/{MM}/{dd}/{filename}`.
- `STORED` row without a matching managed file is `STORED_MISSING_PHYSICAL_FILE`.
- `DELETED` row with a matching managed file is `DELETED_PHYSICAL_FILE_REMAINING`.
- Managed physical file not referenced by `STORED` or `DELETED` rows is an orphan candidate.
- If the managed physical file matches a `MISSING` row, it is reported as `MISSING_ROW_PHYSICAL_FILE_PRESENT`.
- Raw storage paths, storage roots, absolute paths, and stored filenames are not exposed.

## Security Rules

- The endpoint is under `/admin/**` and reuses existing security rules.
- `ROLE_ADMIN` and `ROLE_RECRUIT_ADMIN` can run the scan.
- Applicant principals receive 403.
- Anonymous requests receive 401.
- `SecurityConfig` was not modified.

## Test Coverage

| Test Class | Coverage |
|---|---|
| `AttachmentStorageHealthScanServiceTest` | clean scan, stored missing, deleted remaining, orphan, missing-present, invalid paths, metadata-only exclusion, dry-run no mutation, hashed key exposure |
| `AdminAttachmentStorageHealthControllerTest` | admin/recruit-admin success, applicant 403, anonymous 401, response hides storage internals |
| `LocalAttachmentStorageServiceTest` | structured delete result for deleted, absent, invalid path |
| `ApplicationAttachmentDeleteServiceTest` | invalid storage path delete keeps API contract and marks DB row deleted |

## Test Commands

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*AttachmentStorageHealth*" --no-daemon
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*LocalAttachmentStorageServiceTest*" --no-daemon
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*ApplicationAttachmentDelete*" --no-daemon
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*ApplicationAttachment*" --no-daemon
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test --no-daemon
```

## Test Results

| Command | Result | Notes |
|---|---|---|
| `test --tests "*AttachmentStorageHealth*" --no-daemon` | Success | Initial sandbox run needed approval for Gradle wrapper network access; after compile/test fixes, focused scan tests passed |
| `test --tests "*LocalAttachmentStorageServiceTest*" --no-daemon` | Success | Delete result contract passed |
| `test --tests "*ApplicationAttachmentDelete*" --no-daemon` | Success | Delete command regressions passed |
| `test --tests "*ApplicationAttachment*" --no-daemon` | Success | Broader attachment regressions passed |
| `clean test --no-daemon` | Success | Full regression passed after documentation updates |

## Known Limitations

- Scan results are not persisted.
- There is no scheduler.
- There is no cleanup command.
- There is no quarantine/move step.
- There is no admin repair or mark-missing command.
- Local filesystem storage is the only scanned backend.
- `IGNORED_UNMANAGED_FILE` is report-only and does not imply ownership.

## Remaining Issues

- Decide whether a later scan result should be persisted for audit.
- Decide whether cleanup uses quarantine before delete.
- Decide exact admin confirmation workflow for orphan cleanup.
- Decide mark-missing and repair command audit requirements.
- Decide whether object storage needs a separate scanner implementation.

## Next Phase Recommendation

Recommended next phase: Phase 03i-4-4.

Implement admin cleanup/repair only after dry-run output is reviewed:

- keep cleanup behind explicit admin confirmation;
- do not delete outside managed storage keys;
- consider quarantine before permanent delete;
- add mark-missing command for `STORED` rows whose file is absent;
- add audit/logging for cleanup and repair decisions;
- keep API responses free of raw storage paths.
