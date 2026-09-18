# Phase 03i-1 - Attachment File Upload/Download Design

## Phase 03i-4-3 Attachment Storage Health Scan Implementation Note

Phase 03i-4-3 implemented a read-only admin storage health scan:

- API: `POST /admin/attachments/storage-health/scan`
- Response: `ApiResponse<AttachmentStorageHealthScanResponse>`
- The response always reports `dryRun=true`.
- The endpoint reuses existing `/admin/**` security; `SecurityConfig` was not changed.
- Admin and recruit-admin users can scan; applicants receive 403 and anonymous users receive 401.
- The scan reads local physical files under the configured attachment storage root without following symbolic links.
- It compares managed physical files with `ApplicationAttachment` rows in `STORED`, `DELETED`, and `MISSING`.
- `METADATA_ONLY` rows remain excluded from physical file comparison.
- It reports stored-row missing files, deleted-row remaining files, orphan physical files, invalid storage keys, missing-row physical files that still exist, and ignored unmanaged files.
- It does not delete files, mark rows missing, repair rows, persist scan history, or run as a scheduler.
- Issue responses expose only IDs, status, category, safe messages, physical size, and `fileKeyHash`; raw `storagePath`, storage root, absolute path, and stored filenames remain hidden.
- The storage delete contract now has `deleteIfExistsWithResult(...)`, while the old `deleteIfExists(...)` remains available as a compatibility wrapper.

Implementation reference:

- `docs/codex/implementation/phase-03i-4-3-attachment-storage-health-scan.md`
- `docs/codex/reports/phase-03i-4-3-attachment-storage-health-scan.html`

## Phase 03i-4-2 Attachment Delete Command Implementation Note

Phase 03i-4-2 implemented the soft delete command portion of the later lifecycle design:

- Applicant delete:
  - `POST /applications/{applicationId}/attachments/{attachmentId}/delete`
  - empty body
  - current applicant-owned `DRAFT` application only
  - posting must be `PUBLISHED` and currently accepting
- Admin delete:
  - `POST /admin/applications/{applicationId}/attachments/{attachmentId}/delete`
  - request body: `{ "reason": "..." }`
  - `reason` is required and limited to 1000 characters
  - `DRAFT`, `SUBMITTED`, and `WITHDRAWN` application rows are allowed
- Added `PhysicalFileStatus.DELETED`.
- Added `AttachmentDeleteActorType` with `APPLICANT` and `EMPLOYEE`.
- Added `ApplicationAttachment` delete lifecycle fields: `deletedAt`, `deletedBy`, `deletedByType`, and `deletionReason`.
- Delete commands retain the `ApplicationAttachment` row and move it to `DELETED`; they do not hard-delete DB rows.
- Already deleted rows are excluded from delete lookup and return 404.
- Applicant and admin normal metadata lists now exclude `DELETED`.
- Download structure was not changed; `DELETED` remains 404 because download only accepts `STORED`.
- Upload append `sortOrder` still uses max sort order across all rows, including `DELETED`.
- Metadata replace is not blocked by deleted row sort order.
- Physical file delete for previously `STORED` rows runs after transaction commit.
- Physical delete failure is log-only and does not roll back the DB soft delete.
- Delete response does not expose storage internals, `physicalFileStatus`, or `downloadAvailable`.
- Orphan scan/cleanup, admin repair, mark-missing, include-deleted reads, separate deletion history table, required attachment policy, dashboard/submit integration, and HTTP DELETE remain deferred.

Implementation reference:

- `docs/codex/implementation/phase-03i-4-2-attachment-delete-command.md`
- `docs/codex/reports/phase-03i-4-2-attachment-delete-command.html`

## Phase 03i-4 Delete / Cleanup / Repair Design Note

Phase 03i-4 designed the remaining attachment lifecycle policy after upload and download:

- Applicant delete candidate: `POST /applications/{applicationId}/attachments/{attachmentId}/delete`.
- Admin delete candidate: `POST /admin/applications/{applicationId}/attachments/{attachmentId}/delete`.
- Use POST command endpoints, not HTTP DELETE, to match current command API style.
- Recommended delete policy is soft lifecycle state, not hard delete:
  - keep the `ApplicationAttachment` row;
  - `DELETED` was added to `PhysicalFileStatus` in Phase 03i-4-2;
  - exclude deleted rows from normal applicant/admin metadata lists;
  - reject deleted rows from download with controlled 404.
- Already `DELETED` rows are also excluded from delete command lookup and return controlled 404 on repeated delete.
- Upload append `sortOrder` must continue using max sort order across all rows including `DELETED`, so deleted physical file order values are not reused.
- Metadata replace sort-order conflict checks should use active rows only; `DELETED` row sort orders are not conflict targets for metadata-only visible-list editing.
- Applicant delete should be allowed only for the current applicant's own `DRAFT` application while the posting is accepting.
- Applicant delete should reject `SUBMITTED` and `WITHDRAWN` applications.
- Admin delete can apply to `DRAFT`, `SUBMITTED`, and `WITHDRAWN` applications, but a reason is required.
- Phase 03i-4-2 persists admin delete reason on `ApplicationAttachment` through minimal delete fields: `deletedAt`, `deletedBy`, `deletedByType`, and `deletionReason`; a separate deletion history table remains deferred.
- Physical file deletion should happen after the DB transaction commits; physical delete failure should be logged and handled by a later orphan cleanup phase.
- `MISSING` means a DB row exists but the physical file is absent.
- Orphan physical file means a file exists without an active DB row reference; do not model it as a `PhysicalFileStatus.ORPHANED` enum.
- Cleanup/repair should start with dry-run scan and admin confirmation; actual scheduler/repair commands are deferred.
- Storage internals remain hidden: no `storedFileName`, `storagePath`, storage root, absolute path, or local filesystem path is exposed in metadata, delete, or repair responses.

Design reference:

- `docs/codex/design/phase-03i-4-attachment-delete-cleanup-repair-design.md`
- `docs/codex/reports/phase-03i-4-attachment-delete-cleanup-repair-design.html`

## Phase 03i-3 Implementation Note

Phase 03i-3 implemented the download portion of this design:

```text
GET /applications/{applicationId}/attachments/{attachmentId}/download
GET /admin/applications/{applicationId}/attachments/{attachmentId}/download
```

Implemented:

- Added `ApplicationAttachmentDownloadService` for applicant/admin download policy.
- Added `AttachmentStorageResource` and `AttachmentDownloadResource` internal records.
- Extended `AttachmentStorageService` with `load(String storagePath)`.
- Added local storage load with root-containment validation and controlled 404 for blank, traversal, missing, or non-regular files.
- Review fix: absolute `storagePath` values are rejected before root resolution, because persisted storage keys must be server-generated relative keys.
- Added `AttachmentDownloadResponseFactory` to return `ResponseEntity<Resource>` with download headers.
- Added applicant download in `ApplicationAttachmentController`.
- Added admin download in `AdminApplicationAttachmentController`.
- Added scoped repository lookup methods for attachment/application validation.
- Download succeeds only for `physicalFileStatus=STORED` rows.
- `METADATA_ONLY`, `MISSING`, application mismatch, attachment mismatch, and missing physical files return controlled 404.
- Missing physical files are not auto-updated to `MISSING` in this phase.
- Success response is a streaming file response, not `ApiResponse`; errors still use existing JSON handlers.
- `Content-Type` uses DB value with `application/octet-stream` fallback.
- `Content-Length` uses actual physical file size; DB size mismatch is logged server-side only.
- `Content-Disposition` includes `attachment`, ASCII `filename`, and UTF-8 percent-encoded `filename*` for Korean filenames.
- Added `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`, and `Pragma: no-cache`.

Preserved/deferred:

- `SecurityConfig`, upload API, metadata replace behavior, dashboard readiness, submit validator, admin upload, delete, orphan cleanup, S3/NAS, virus scan/DLP, required attachment policy, and `downloadAvailable` remain unchanged.
- Metadata responses still do not expose `storedFileName`, `storagePath`, `physicalFileStatus`, storage root, absolute paths, or download URL fields.

Implementation reference:

- `docs/codex/implementation/phase-03i-3-attachment-file-download.md`
- `docs/codex/reports/phase-03i-3-attachment-file-download.html`

## Phase 03i-2 Implementation Note

Phase 03i-2 implemented the upload portion of this design:

```text
POST /applications/{applicationId}/attachments/files
```

Implemented:

- Added `PhysicalFileStatus` enum with `METADATA_ONLY`, `STORED`, and `MISSING`.
- Added `ApplicationAttachment.physicalFileStatus` as `@Enumerated(EnumType.STRING)`, `nullable=false`, default `METADATA_ONLY`.
- Added `AttachmentProperties` under `recruit.attachment`.
- Enabled multipart parsing with parser-level size limits.
- Added `AttachmentStorageService`, `LocalAttachmentStorageService`, `StoredAttachmentFile`, `AttachmentFilePolicy`, and `ApplicationAttachmentFileService`.
- Added applicant single-file multipart upload to `ApplicationAttachmentController`.
- Hardened `POST /applications/{applicationId}/attachments` so metadata replace deletes/replaces only `METADATA_ONLY` rows and preserves `STORED` rows.
- Metadata replace also rejects requested metadata `sortOrder` values that conflict with existing `STORED` row `sortOrder` values.
- Rejected client-supplied `storedFileName` and `storagePath` in metadata replace with 400.
- Rejected forbidden multipart parts `sortOrder`, `displayName`, `originalFileName`, `storedFileName`, and `storagePath` with 400.
- Assigned upload `sortOrder` append-only from the current max sort order across all attachment rows for the application.
- Enforced file count and total-size limits using only `physicalFileStatus=STORED` rows.
- Added `@Param` annotations to repository named queries and startup validation to `AttachmentProperties`.
- Stored generated UUID-based filenames and relative storage keys server-side.
- Registered rollback cleanup for the just-stored physical file and used `saveAndFlush(...)` for DB save.

Preserved/deferred:

- `AttachmentResponse` still does not expose `storedFileName`, `storagePath`, `physicalFileStatus`, or `downloadAvailable`.
- `SecurityConfig`, dashboard readiness, submit validator, admin upload, download, delete, orphan cleanup, S3/NAS, virus scan, DLP, and attachment required policy remain unchanged.
- `MISSING` is defined but not assigned until the download/missing-file phase.

Implementation reference:

- `docs/codex/implementation/phase-03i-2-attachment-file-upload.md`
- `docs/codex/reports/phase-03i-2-attachment-file-upload.html`

## Phase Name

Phase 03i-1 - Attachment File Upload/Download Design

## Purpose

Phase 03i-1 designs the next attachment phase after the existing metadata-only vertical slice.

The current implementation can store and read `ApplicationAttachment` metadata, but it does not accept multipart file content, write bytes to storage, stream downloads, or define file validation and filename security policy. This phase decides the API split, storage policy, response exposure boundary, authorization rules, validation rules, test plan, and next implementation phases.

This is a documentation-only design phase. No Java source, test source, `SecurityConfig`, build files, YAML, database schema, existing attachment API behavior, dashboard readiness, submit validator, StageResult API, storage service, upload implementation, download implementation, delete command, orphan cleanup, or S3/NAS integration is changed.

## Scope

- Summarize the current attachment metadata state.
- Compare upload API candidates and choose a recommended default.
- Compare download API candidates for applicants and admins.
- Define local filesystem storage policy for the first implementation phase.
- Define storage abstraction candidates for later S3/NAS migration.
- Review whether current `ApplicationAttachment` fields are enough.
- Define authorization and application-state policy.
- Define file validation policy: size, extension, content type, filename, path traversal, and antivirus deferral.
- Define response DTO exposure policy.
- Split implementation into Phase 03i-2 and later phases.
- Define upload/download/controller/regression test plan for implementation phases.
- Produce a paired self-contained HTML report.
- Update related design and history documents.

## Out of Scope

- Java source changes.
- Test source changes.
- `SecurityConfig` changes.
- `build.gradle`, `settings.gradle`, Gradle wrapper, or YAML changes.
- Database schema changes.
- Multipart upload implementation.
- Download implementation.
- Storage service implementation.
- Existing `GET /applications/{applicationId}/attachments` behavior changes.
- Runtime implementation of `POST /applications/{applicationId}/attachments` metadata replace hardening. The hardening policy is designed here, but Java code is not changed in Phase 03i-1.
- Existing admin attachment metadata read behavior changes.
- `ApplicationSubmitValidator` changes.
- Dashboard readiness changes.
- StageResult API changes.
- Admin upload or replace implementation.
- File delete command.
- Orphan cleanup job.
- S3, NAS, virus scan, or DLP integration.

## Changed Files

| Path | Change Type | Notes |
|---|---|---|
| `docs/codex/design/phase-03i-attachment-file-upload-download-design.md` | New | Codex reference design |
| `docs/codex/reports/phase-03i-attachment-file-upload-download-design.html` | New | Human-readable self-contained report |
| `docs/codex/design/phase-03-application-design.md` | Modified | Adds Phase 03i-1 design note |
| `docs/codex/design/phase-03c-application-detail-design.md` | Modified | Adds attachment upload/download design note |
| `docs/codex/design/phase-03h-3-applicant-application-dashboard-design.md` | Modified | Notes attachment readiness remains deferred |
| `docs/codex/07-implementation-history.md` | Modified | Adds Phase 03i-1 history entry |

No production or test code is changed in this phase.

## Current Attachment Metadata State

### Entity

`ApplicationAttachment` currently stores a metadata row under one `JobApplication`.

Current fields:

| Field | Current Role | Phase 03i Assessment |
|---|---|---|
| `id` | Metadata row id | Can be used as attachment id |
| `jobApplication` | Owning application | Required ownership boundary |
| `attachmentType` | Business type | Reused by upload API |
| `sectionType` | Detail section hint | Reused by upload API |
| `sectionRecordId` | Optional detail section row hint | Reused; detailed row existence validation remains deferred |
| `originalFileName` | User-visible name | Reused; must be sanitized before persistence |
| `storedFileName` | Internal stored filename | Reused; must not be derived from user filename |
| `storagePath` | Internal storage locator | Reused as logical relative key, not exposed |
| `contentType` | Stored content type | Reused; should be server-validated |
| `fileSize` | Stored byte size | Reused; should come from uploaded file bytes |
| `sortOrder` | Display ordering | Reused |
| `physicalFileStatus` | File-backed state | Phase 03i-2 schema addition required; see status policy below |
| inherited audit fields | created/updated metadata | `createdAt` can serve as initial upload timestamp unless a dedicated `uploadedAt` field is later required |

Current field conclusion:

- The existing fields can support first upload/download only if `storagePath` is treated as a server-generated relative logical key and `createdAt` is accepted as upload time.
- A dedicated `uploadedAt` field can be deferred unless product requirements need upload time separate from metadata update time.
- A physical-file state flag is not currently present. Phase 03i-2 should add `physicalFileStatus` instead of inferring file-backed state from `storagePath`, because older metadata requests may already have supplied arbitrary storage values.
- Recommended `physicalFileStatus` values: `METADATA_ONLY`, `STORED`, `MISSING`.
- Existing rows and metadata API rows default to `METADATA_ONLY`.
- Only `physicalFileStatus=STORED` rows created by the server upload command are downloadable.
- If a stored file is missing during download, the row is treated as `MISSING` for response/download policy; it must not be reported as downloadable.
- Phase 03i-1 does not change schema.

Phase 03i-2 column and enum implementation guidance:

- Add `ApplicationAttachment.physicalFileStatus`.
- Map it with `@Enumerated(EnumType.STRING)`.
- Persist it with `nullable = false`.
- Entity default should be `METADATA_ONLY`.
- Existing tests and fixture data should treat existing attachment rows as `METADATA_ONLY`.
- Operational DB migration should add a non-null column with default `METADATA_ONLY` for existing rows.

### Current Applicant APIs

| Method | Path | Current Behavior |
|---|---|---|
| `GET` | `/applications/{applicationId}/attachments` | Reads current applicant-owned attachment metadata |
| `POST` | `/applications/{applicationId}/attachments` | Replaces current applicant-owned attachment metadata list |

Current applicant metadata policy:

- Current applicant id comes from `CurrentApplicantService`.
- Applicant ownership is checked through the section access service.
- Replace is allowed only when the application is `DRAFT`, the posting is `PUBLISHED`, and the reception period is open.
- `ApplicationFormConfig` has no attachment flag, so attachment metadata is not gated by a config flag.
- `storedFileName` and `storagePath` are internal fields and are not exposed by `AttachmentResponse`.
- Existing metadata replace currently deletes rows by `applicationId` and inserts the submitted metadata list. Before Phase 03i-2 file-backed rows are introduced, replace must be hardened so it only replaces `physicalFileStatus=METADATA_ONLY` rows.
- Phase 03i-2 metadata replace must not delete or update file-backed rows.
- `AttachmentRequest` must reject client-supplied `storedFileName` or `storagePath` with 400.
- Phase 03i-2 does not support file-backed row metadata edits such as `sortOrder`, `attachmentType`, or `sectionType` changes through the metadata replace API. A later explicit reorder/update endpoint is required if product UX needs those edits.

### Current Admin API

| Method | Path | Current Behavior |
|---|---|---|
| `GET` | `/admin/applications/{applicationId}/attachments` | Reads attachment metadata for an existing application |

Current admin metadata policy:

- Admin section read is read-only.
- `DRAFT`, `SUBMITTED`, and `WITHDRAWN` applications are readable.
- `AdminAttachmentResponse` does not expose `storedFileName`, `storagePath`, or download URL.
- Actual admin download is not implemented.

## Upload API Options

### Option A - Direct Multipart Upload

```text
POST /applications/{applicationId}/attachments/files
Content-Type: multipart/form-data
```

Request parts:

| Part | Required | Notes |
|---|---:|---|
| `file` | Yes | Uploaded file bytes |
| `attachmentType` | Yes | Existing `AttachmentType` |
| `sectionType` | Yes | Existing `ApplicationSectionType` |
| `sectionRecordId` | No | Optional; same rules as metadata |
| `displayName` or `originalFileName` | No | Exclude from Phase 03i-2; derive `originalFileName` from sanitized multipart original filename |
| `sortOrder` | No | Exclude from Phase 03i-2 request; server assigns append-only order |

Pros:

- Simple frontend flow for first real upload.
- Does not require a prior metadata row.
- Keeps current metadata replace API separate from file byte handling.
- Avoids coupling metadata-only editing to filesystem transactions.

Cons:

- Updating metadata for many attachments remains separate from uploading one file.
- Reordering uploaded files requires a later explicit reorder endpoint.

### Option B - Attach or Replace File on Existing Metadata Row

```text
POST /applications/{applicationId}/attachments/{attachmentId}/file
Content-Type: multipart/form-data
```

Pros:

- Works well if metadata rows are created before file bytes.
- Explicit replacement target.

Cons:

- Requires metadata-only row lifecycle and partial states.
- Increases rules for rows with no file vs rows with file.
- More fragile while current metadata fields are mandatory.

### Option C - Merge Multipart into Existing Metadata Replace API

```text
POST /applications/{applicationId}/attachments
Content-Type: multipart/form-data
```

Pros:

- One endpoint could eventually save metadata and files together.

Cons:

- Collides conceptually with existing JSON replace semantics.
- Harder to preserve current metadata API contract.
- Harder to handle multiple files, partial failures, and rollback.

### Recommendation

Use Option A as the Phase 03i-2 default:

```text
POST /applications/{applicationId}/attachments/files
```

Keep the existing JSON metadata replace API path, but harden its contract before file-backed rows are introduced:

```text
POST /applications/{applicationId}/attachments
```

Rationale:

- The existing API remains a metadata-only replace command.
- The existing metadata replace command must not delete file-backed rows after Phase 03i-2 upload exists.
- Client-supplied storage fields must be rejected with 400. Do not silently ignore `storedFileName` or `storagePath`.
- Runtime file storage is isolated to a single-file upload command.
- Failure behavior is easier to reason about when upload is isolated: validate request, store file bytes, persist one metadata row, and compensate if DB persistence fails.
- Option B can be added later for explicit file replacement if product UX needs a two-step metadata row workflow.

Selected collision policy:

- Use policy A plus explicit delete later.
- Phase 03i-2 metadata replace must preserve file-backed rows and only replace `physicalFileStatus=METADATA_ONLY` rows managed by that endpoint.
- File-backed row metadata changes are out of scope for Phase 03i-2. Upload appends a new row; file-backed reorder/update requires a later explicit endpoint.
- Phase 03i-2 upload is append-only for ordering. The upload request does not accept `sortOrder`; the service assigns the next order after the current maximum attachment `sortOrder` for that application.
- Physical deletion is not implicit in metadata replace. A later explicit delete command handles DB row deletion and physical file deletion together.
- If this hardening is not implemented, Phase 03i-2 upload must not ship because metadata replace can orphan files immediately.

Recommended Phase 03i-2 upload response:

```text
ApiResponse<AttachmentResponse>
```

Recommended response fields:

- `attachmentId`
- `attachmentType`
- `sectionType`
- `sectionRecordId`
- `originalFileName`
- `contentType`
- `fileSize`
- `sortOrder`
- `uploadedAt` if available without schema change, otherwise omit until schema decision

Do not add `downloadAvailable` in Phase 03i-2 while download endpoints are still deferred. Add it in Phase 03i-3 only if the download implementation can compute it accurately; otherwise keep it omitted.

Do not expose:

- `storedFileName`
- `storagePath`
- absolute path
- local filesystem root

## Download API Design

### Applicant Download

```text
GET /applications/{applicationId}/attachments/{attachmentId}/download
```

Policy:

- Only `ROLE_APPLICANT` can access through existing `/applications/**` policy.
- Current applicant id must come from `CurrentApplicantService`.
- The application must belong to the current applicant.
- The attachment must belong to the same application.
- Other applicant resources use the existing hidden 404 policy.
- `DRAFT`, `SUBMITTED`, and `WITHDRAWN` applications can download their own existing files.
- Employee/admin users must not use applicant download paths.

### Admin Download

```text
GET /admin/applications/{applicationId}/attachments/{attachmentId}/download
```

Policy:

- Uses existing `/admin/**` authorization policy.
- `applicationId` must exist.
- `attachmentId` must belong to the same application.
- Applicant users are forbidden.
- Admin/recruit-admin users can download files after authorization.

### Download Response

Download endpoints should return a streaming response, not `ApiResponse`.

Recommended headers:

| Header | Policy |
|---|---|
| `Content-Type` | Stored validated content type, or `application/octet-stream` fallback |
| `Content-Length` | Stored file size if known and file exists |
| `Content-Disposition` | `attachment; filename="fallback"; filename*=UTF-8''encoded` |
| `X-Content-Type-Options` | `nosniff` recommended |

Filename policy:

- Use the already validated display filename stored as `originalFileName`.
- Upload validation rejects path separators and control characters before persistence; download must not attempt to repair unsafe names on the fly.
- Use an ASCII `filename` fallback.
- Add RFC 5987 `filename*` with UTF-8 percent encoding for Korean or other non-ASCII names.
- Never expose `storedFileName` or `storagePath` in the response body or headers.

## Storage Policy

### First Implementation Storage

Use local filesystem storage for Phase 03i-2 under a single-node or shared-volume deployment assumption.

Operational assumption:

- Local filesystem storage is acceptable for development and single-node MVP deployment.
- Multi-node production requires NAS, S3/object storage, or a shared volume that every serving node can read.
- Sticky session is not a valid fix for file locality because download requests may still need durable shared file access outside the web session.

Recommended configuration keys:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: ${SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE:25MB}
      max-request-size: ${SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE:25MB}

recruit:
  attachment:
    storage-root: ${RECRUIT_ATTACHMENT_STORAGE_ROOT}
    max-file-size: ${RECRUIT_ATTACHMENT_MAX_FILE_SIZE:20MB}
    max-files-per-application: ${RECRUIT_ATTACHMENT_MAX_FILES_PER_APPLICATION:20}
    max-total-size-per-application: ${RECRUIT_ATTACHMENT_MAX_TOTAL_SIZE_PER_APPLICATION:100MB}
    allowed-extensions: ${RECRUIT_ATTACHMENT_ALLOWED_EXTENSIONS:pdf,jpg,jpeg,png,doc,docx,xls,xlsx,hwp,hwpx}
    allowed-content-types: ${RECRUIT_ATTACHMENT_ALLOWED_CONTENT_TYPES:application/pdf,image/jpeg,image/png,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/x-hwp,application/haansofthwp,application/vnd.hancom.hwpx}
```

Phase 03i-1 does not add these keys to YAML.

Size-limit layering:

- `spring.servlet.multipart.max-file-size` and `spring.servlet.multipart.max-request-size` protect the multipart parser before controller/service code receives the file.
- `recruit.attachment.max-file-size` is the business attachment limit enforced by the upload service.
- `recruit.attachment.max-files-per-application` limits `physicalFileStatus=STORED` row count for one application.
- `recruit.attachment.max-total-size-per-application` limits the sum of `fileSize` across `physicalFileStatus=STORED` rows for one application.
- `METADATA_ONLY` rows are excluded from upload file count and total-size calculations.
- `MISSING` rows can be handled by Phase 03i-3 download policy; Phase 03i-2 validation should count only `STORED`.
- The servlet multipart limits should be equal to or slightly higher than the business limit so the application can return domain-specific validation errors when possible.
- `MaxUploadSizeExceededException` must be handled by global exception handling and mapped to a controlled `400` or `413` response with the existing `ApiResponse` failure shape.
- Multipart resolver failures happen before controller validation; service validation failures happen after a parsed `MultipartFile` reaches the application layer.

### Storage Layout

Recommended relative layout:

```text
applications/{applicationId}/{yyyy}/{MM}/{dd}/{uuid}.{ext}
```

or:

```text
applications/{applicationId}/{attachmentId-or-date}/{uuid}.{ext}
```

Default recommendation:

```text
applications/{applicationId}/{yyyy}/{MM}/{dd}/{uuid}.{ext}
```

Reason:

- Does not require an attachment id before storing bytes.
- Avoids placing too many files in one directory.
- Keeps application ownership visible in storage organization.

Stored filename policy:

- Do not use the original filename as the physical filename.
- Generate a UUID or ULID based filename.
- Keep only the validated extension.
- Store the relative path/key in `storagePath`.
- Store generated filename in `storedFileName`.

Path traversal defense:

- Never concatenate user input into a filesystem path.
- Resolve storage root once as an absolute normalized path.
- Resolve relative generated key under the root.
- Normalize the final path.
- Reject the operation if the final path does not start with the root.
- Reject any user-provided filename containing path separators after normalization.

Transaction compensation:

- Recommended order: validate request, store the physical file, insert the DB metadata row, return response.
- Register a Spring `TransactionSynchronization` immediately after the physical file is stored so transaction rollback deletes only the just-stored physical file.
- Use `repository.saveAndFlush(...)` inside the service try block to surface DB constraint failures before returning whenever possible.
- If DB insert or transaction commit fails after storage save succeeds, cleanup is handled by the rollback synchronization; cleanup should be idempotent because an early catch block may also attempt it.
- If cleanup deletion also fails, preserve the original exception flow and log or record a cleanup target for a later orphan cleanup job.
- If a DB row exists but the physical file is missing, Phase 03i-3 download must not report it as downloadable and must return a controlled 404 or business error without exposing storage paths.

### Storage Abstraction

Recommended candidate interfaces for Phase 03i-2 or later:

```text
AttachmentStorageService
LocalAttachmentStorageService
```

Candidate responsibilities:

| Component | Responsibility |
|---|---|
| `AttachmentStorageService` | Save bytes, open read stream, check existence, delete/orphan cleanup hook |
| `LocalAttachmentStorageService` | Local filesystem implementation with root-path containment |
| `AttachmentFilePolicy` | Size, extension, content type, and filename validation |
| `AttachmentDownloadResource` | Stream, content type, length, and display filename |

## Authorization and State Policy

### Applicant Upload

Allowed only when all conditions are true:

- Current user is applicant.
- Application belongs to current applicant.
- Application status is `DRAFT`.
- `JobPosting.status == PUBLISHED`.
- Current time is inside the reception period.
- The uploaded file passes validation.
- Existing `ApplicationSectionAccessService` or equivalent attachment service validation is reused.

Rejected:

- `SUBMITTED` upload.
- `WITHDRAWN` upload.
- Closed or unpublished posting upload.
- Outside reception period upload.
- Other applicant's application upload.
- Employee/admin principal on applicant path.
- Anonymous request.

### Applicant Download

Allowed:

- Current applicant owns the application.
- Attachment belongs to the application.
- Application status is `DRAFT`, `SUBMITTED`, or `WITHDRAWN`.

Rejected:

- Other applicant's application or attachment mismatch: hidden 404.
- Employee/admin principal on applicant path: 403 through URL authorization.
- Anonymous request: 401.

### Admin Download

Allowed:

- Current principal has existing admin/recruit-admin authority.
- Application exists.
- Attachment belongs to the application.

Rejected:

- Applicant principal: 403.
- Anonymous request: 401.
- Attachment/application mismatch: 404.

## File Validation Policy

### Size

Recommended default:

- 20 MB per file as configurable default.

Alternative:

- 10 MB if infrastructure bandwidth/storage is constrained.

Phase 03i-2 should choose a property-backed default. Do not hardcode a production-only value in business logic.

### Extension Allowlist

Recommended initial allowlist:

```text
pdf, jpg, jpeg, png, doc, docx, xls, xlsx, hwp, hwpx
```

Default-block:

- executable files
- shell scripts
- JavaScript/HTML/SVG where script execution risk exists
- archives until extraction and scan policy exists
- files with no extension unless explicitly allowed
- double-extension tricks after normalization when the effective last extension is disallowed

### MIME / Content Type

Policy:

- Do not trust client-provided `MultipartFile.getContentType()` alone.
- Store client-provided content type only after allowlist validation.
- Phase 03i-2 can start with conservative validation using extension plus multipart content type allowlist.
- Later phases can add magic-number sniffing or Apache Tika-like detection.

### Filename

Policy:

- Phase 03i-2 does not accept `displayName` or `originalFileName` override fields.
- `originalFileName` is derived only from `MultipartFile.getOriginalFilename()` after validation and normalization.
- Original filename may be used only as a display filename.
- Reject null or blank original filename with 400.
- Reject `/`, `\`, and control characters with 400.
- Normalize whitespace.
- Limit length to 255 characters.
- Reject Windows reserved device names such as `CON`, `PRN`, `AUX`, `NUL`, `COM1` through `COM9`, and `LPT1` through `LPT9`.
- Never use original filename for physical storage.
- Never expose local absolute paths.

### Antivirus / Malware Scan

Phase 03i-1 recommendation:

- Do not implement virus scan in Phase 03i-2.
- Document it as an operational security requirement before production.
- If later integrated, the upload flow should allow a scan status field or quarantine workflow.

## Response Exposure Policy

Expose in metadata responses:

| Field | Applicant | Admin | Notes |
|---|---:|---:|---|
| `attachmentId` | Yes | Yes | Stable metadata id |
| `attachmentType` | Yes | Yes | Business type |
| `sectionType` | Yes | Yes | Section hint |
| `sectionRecordId` | Yes | Yes | May be null |
| `originalFileName` | Yes | Yes | Sanitized display filename |
| `contentType` | Yes | Yes | Validated content type |
| `fileSize` | Yes | Yes | Stored byte length |
| `sortOrder` | Yes | Yes | Existing display ordering |
| `uploadedAt` | Candidate | Candidate | Needs schema/DTO decision |
| `downloadAvailable` | Phase 03i-3 candidate | Phase 03i-3 candidate | Do not add in Phase 03i-2 while download endpoints are deferred |
| `physicalFileStatus` | No | No | Internal state for authorization/download decisions, not user-facing metadata |

Do not expose:

| Field | Reason |
|---|---|
| `storedFileName` | Internal storage name |
| `storagePath` | Internal storage key/path |
| `physicalFileStatus` | Internal file lifecycle state |
| absolute path | Infrastructure detail and security risk |
| storage root | Secret-like operational path |
| pre-signed local path | Bypasses authorization |

Download URL policy:

- Prefer frontend-generated URL from `applicationId + attachmentId`.
- If a URL is returned later, it must be an API path only, never a storage path.

## API List

### Existing APIs Preserved

| Method | Path | Purpose | Status in Phase 03i-1 |
|---|---|---|---|
| `GET` | `/applications/{applicationId}/attachments` | Applicant metadata list | Preserved |
| `POST` | `/applications/{applicationId}/attachments` | Applicant metadata replace | Path preserved; Phase 03i-2 hardening required before file upload ships |
| `GET` | `/admin/applications/{applicationId}/attachments` | Admin metadata list | Preserved |

### Recommended Upload API

| Method | Path | Purpose | Request | Response | Implementation Phase |
|---|---|---|---|---|---|
| `POST` | `/applications/{applicationId}/attachments/files` | Applicant single-file upload | `multipart/form-data` | `ApiResponse<AttachmentResponse>` | Phase 03i-2 |

### Recommended Download APIs

| Method | Path | Purpose | Request | Response | Implementation Phase |
|---|---|---|---|---|---|
| `GET` | `/applications/{applicationId}/attachments/{attachmentId}/download` | Applicant-owned file download | Path ids | Streaming response with headers | Phase 03i-3 |
| `GET` | `/admin/applications/{applicationId}/attachments/{attachmentId}/download` | Admin file download | Path ids | Streaming response with headers | Phase 03i-3 |

### Deferred APIs

| Method | Path | Purpose | Reason Deferred |
|---|---|---|---|
| `POST` | `/applications/{applicationId}/attachments/{attachmentId}/file` | Replace file on existing metadata row | Only needed if two-step metadata workflow is chosen |
| `POST` | `/admin/applications/{applicationId}/attachments/files` | Admin upload | Requires product and permission policy |
| `POST` | `/applications/{applicationId}/attachments/{attachmentId}/delete` | Applicant delete command | Requires physical file deletion/orphan policy |
| `POST` | `/admin/applications/{applicationId}/attachments/{attachmentId}/delete` | Admin delete command | Requires audit and authorization policy |

## Entity / DTO / Service / Controller Summary

No class is implemented in Phase 03i-1. Candidate Phase 03i-2/03i-3 classes:

| Package | Candidate Class | Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.config` | `AttachmentProperties` | Configuration properties | Bind storage root, max size, allowlists |
| `com.shinyoung.recruit.enumeration` | `PhysicalFileStatus` | Enum | `METADATA_ONLY`, `STORED`, `MISSING` lifecycle state |
| `com.shinyoung.recruit.domain.entity` | Existing `ApplicationAttachment` extension | Entity | Add non-null `physicalFileStatus` enum field defaulted to `METADATA_ONLY` |
| `com.shinyoung.recruit.service` | `AttachmentStorageService` | Service interface | Store and load physical file bytes |
| `com.shinyoung.recruit.service` | `LocalAttachmentStorageService` | Service implementation | Local filesystem storage |
| `com.shinyoung.recruit.service` | `AttachmentFilePolicy` | Service/helper | Validate size, extension, content type, filename |
| `com.shinyoung.recruit.service` | `ApplicationAttachmentFileService` | Service | Upload/download orchestration |
| `com.shinyoung.recruit.dto.request` | `AttachmentUploadRequest` | Request DTO or command object | Normalized upload metadata from multipart fields |
| `com.shinyoung.recruit.dto.response` | Existing `AttachmentResponse` reuse candidate | Response DTO | Include existing `sortOrder`; do not add `downloadAvailable` until Phase 03i-3 |
| `com.shinyoung.recruit.controller` | Existing `ApplicationAttachmentController` extension candidate | Controller | Add applicant upload/download endpoints |
| `com.shinyoung.recruit.controller` | Existing `AdminApplicationSectionController` or separate admin attachment controller | Controller | Add admin download endpoint |

## Validation and Business Rules

- Upload is a write operation and follows current detail-section write policy.
- Download is a read operation and should be allowed for the owner across `DRAFT`, `SUBMITTED`, and `WITHDRAWN`.
- Admin download follows admin read authorization and application/attachment ownership matching.
- Attachment metadata responses must continue hiding storage internals.
- `sectionType=APPLICATION` must not have `sectionRecordId`.
- `sectionType!=APPLICATION` can keep nullable `sectionRecordId` until row-existence validation policy is finalized.
- `storagePath` must be generated by the server.
- `storedFileName` must be generated by the server.
- Existing metadata replace must reject client-supplied storage fields with 400.
- Recommended implementation: keep `storedFileName` and `storagePath` fields on `AttachmentRequest` and fail service validation when either value is present. Do not rely on removing DTO fields or global Jackson `FAIL_ON_UNKNOWN_PROPERTIES`.
- A lower-level JSON forbidden-field detector is also acceptable, but global unknown-property failure is not recommended because it can affect unrelated APIs.
- Existing metadata replace must not delete or update file-backed rows once upload exists.
- Phase 03i-2 upload must add `physicalFileStatus`; only `STORED` rows created by the server upload API are downloadable.
- Legacy rows and metadata API rows are `METADATA_ONLY` and non-downloadable.
- A physical file missing at download time must not be treated as available; mark/handle it as `MISSING` if the implementation updates state.
- A missing physical file on download should not expose local path details. Phase 03i-3 should choose either 404 or a controlled business error; recommendation is 404.

## Implementation Phase Split

| Phase | Recommended Scope | Explicitly Not Included |
|---|---|---|
| Phase 03i-1 | Design only | Any code/schema/config/test changes |
| Phase 03i-2 | `physicalFileStatus` schema addition, metadata replace hardening, storage abstraction, applicant single-file upload, DB-failure/rollback file cleanup compensation | Download, admin upload, explicit delete command, scheduled orphan cleanup, file-backed reorder/update, S3/NAS |
| Phase 03i-3 | Applicant/admin download streaming | Upload redesign, delete, orphan cleanup |
| Phase 03i-4 | Admin upload/replace, delete command, orphan cleanup | Submit required policy unless decided |
| Phase 03i-5 | Attachment submit required policy and dashboard readiness integration | Storage mechanics unless gaps remain |

## Test Plan for Implementation Phases

### Upload Service

- Applicant-owned `DRAFT` application upload succeeds.
- `SUBMITTED` upload fails.
- `WITHDRAWN` upload fails.
- Other applicant application upload returns hidden 404.
- Upload fails when posting is not `PUBLISHED`.
- Upload fails outside reception period.
- Missing file fails.
- Empty file fails.
- Size over limit fails.
- Upload fails when current `STORED` row count plus the new upload would exceed `recruit.attachment.max-files-per-application`.
- Upload fails when the sum of `fileSize` across current `STORED` rows plus the new upload size would exceed `recruit.attachment.max-total-size-per-application`.
- Disallowed extension fails.
- Disallowed content type fails.
- Generated `storedFileName` uses UUID/ULID and allowed extension.
- Generated `storagePath` remains under storage root.
- Original filename with path separators or control characters is rejected.
- Null or blank original filename is rejected.
- Windows reserved filenames are rejected.
- `displayName`/`originalFileName` override parts are rejected in Phase 03i-2.
- `sortOrder` request part is rejected in Phase 03i-2; the service appends ordering server-side.
- Server-assigned `sortOrder` is included in the upload response when `AttachmentResponse` is reused.
- Uploaded rows are stored with `physicalFileStatus=STORED`.
- Metadata API rows are stored with `physicalFileStatus=METADATA_ONLY`.
- Storage save success followed by DB insert failure attempts cleanup of the just-stored physical file.
- Transaction rollback after method return triggers registered cleanup of the just-stored physical file.
- Cleanup failure preserves the original failure and records/logs a later cleanup target.
- Existing metadata replace preserves file-backed rows.
- Existing metadata replace rejects client-supplied `storedFileName` and `storagePath`.
- Existing metadata replace does not support changing file-backed row `sortOrder`, `attachmentType`, or `sectionType`.

### Download Service

- Applicant owner download succeeds.
- Other applicant download returns hidden 404.
- Admin download succeeds.
- Attachment/application mismatch returns 404.
- Missing physical file returns controlled 404.
- DB row without `physicalFileStatus=STORED` is not downloadable.
- Korean filename produces safe `Content-Disposition` with `filename` and `filename*`.
- `storedFileName` and `storagePath` are not included in response headers as filenames.

### Controller

- Multipart upload success.
- Validation failure returns `400 + ApiResponse.fail`.
- `MaxUploadSizeExceededException` from multipart parsing returns a controlled `ApiResponse` failure response before service validation.
- Anonymous applicant upload/download returns 401.
- Employee/admin applicant upload/download returns 403.
- Applicant admin download returns 403.
- Unsupported methods are rejected.

### Regression

- Existing metadata API path and metadata-only purpose remain unchanged.
- Existing metadata API hardening does not remove file-backed rows and rejects untrusted storage fields.
- Existing metadata API hardening replaces only `METADATA_ONLY` rows.
- Existing admin metadata API remains unchanged.
- Existing dashboard/list/stage-result APIs are unaffected.
- Existing submit validator remains unchanged until Phase 03i-5.

## Test Commands

Not executed in Phase 03i-1 because this is a documentation-only design phase.

Recommended Phase 03i-2 targeted commands after implementation:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationAttachmentFileServiceTest
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.ApplicationAttachmentControllerTest
```

Recommended Phase 03i-3 targeted commands after implementation:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationAttachmentFileServiceTest
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.ApplicationAttachmentControllerTest --tests com.shinyoung.recruit.controller.AdminApplicationSectionControllerTest
```

Recommended full regression after implementation phases:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test --no-daemon
```

## Test Results

| Command | Result | Notes |
|---|---|---|
| Gradle tests | Not run | Documentation-only phase; no Java/test/config/schema changes |
| Documentation verification | Success | `git diff --check` reported no whitespace errors; HTML report has no external script, CDN, or URL dependency |

## Remaining Issues

- Decide whether `uploadedAt` should become a dedicated column or remain derived from `createdAt`.
- Decide if `downloadAvailable` should be returned in metadata responses in Phase 03i-3; do not add it in Phase 03i-2.
- Decide exact max file size default: 10 MB vs 20 MB. Recommendation: property-backed 20 MB default.
- Decide exact attachment count and total-size defaults after frontend UX confirms expected attachment volume. Recommendation: property-backed defaults of 20 files and 100 MB total per application.
- Decide whether magic-number sniffing is required in Phase 03i-2 or can wait.
- Decide production antivirus/malware scanning integration.
- Decide admin upload/replace, explicit delete, and scheduled orphan cleanup audit policy.
- Decide later file-backed reorder/update API if users need to change uploaded attachment ordering or metadata after upload.
- Decide attachment required policy and dashboard readiness integration in a later phase.

## Next Phase Recommendation

Recommended next phase: Phase 03i-2.

Implement local storage abstraction and applicant single-file upload:

- Add `AttachmentProperties`.
- Add `physicalFileStatus` schema/enum with `METADATA_ONLY`, `STORED`, `MISSING`.
- Add `ApplicationAttachment.physicalFileStatus` as `@Enumerated(EnumType.STRING)`, `nullable=false`, default `METADATA_ONLY`; DB migration should default existing rows to `METADATA_ONLY`.
- Add `AttachmentStorageService`.
- Add `LocalAttachmentStorageService`.
- Add file validation helper/policy.
- Add Spring multipart size-limit exception handling.
- Add per-application file count and total-size validation based only on `physicalFileStatus=STORED` rows.
- Harden existing metadata replace so it cannot delete/update file-backed rows and rejects client-supplied storage fields with 400.
- Add applicant upload endpoint `POST /applications/{applicationId}/attachments/files`.
- Generate `storedFileName` and `storagePath` server-side.
- Use multipart original filename only; do not accept display/original filename override in Phase 03i-2.
- Do not accept `sortOrder` in Phase 03i-2 upload requests; assign append-only ordering server-side.
- Include `sortOrder` in upload response if `AttachmentResponse` is reused.
- Do not add `downloadAvailable` until Phase 03i-3.
- Register transaction rollback cleanup for the just-stored physical file and use `saveAndFlush(...)` to surface DB failures before returning where possible.
- Keep download endpoints deferred to Phase 03i-3.
