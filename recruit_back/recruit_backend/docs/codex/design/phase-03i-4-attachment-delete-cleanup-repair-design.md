# Phase 03i-4 - Attachment Delete / Orphan Cleanup / Admin Repair Design

## Phase 03i-4-3 Implementation Note

Phase 03i-4-3 implemented the storage health scan dry-run portion of this design.

Implemented:

- Added admin dry-run scan API:
  - `POST /admin/attachments/storage-health/scan`
  - no request body
  - response: `ApiResponse<AttachmentStorageHealthScanResponse>`
- The scan is read-only and always reports `dryRun=true`.
- Existing `/admin/**` security is reused; `SecurityConfig` was not changed.
- `ROLE_ADMIN` and `ROLE_RECRUIT_ADMIN` can scan.
- Applicant access is blocked with 403 and anonymous access with 401.
- Scan compares local physical files under `recruit.attachment.storage-root` with `ApplicationAttachment` rows in `STORED`, `DELETED`, and `MISSING`.
- `METADATA_ONLY` rows are excluded from storage comparison.
- Managed physical file pattern is `applications/{applicationId}/{yyyy}/{MM}/{dd}/{filename}`.
- Scan categories implemented:
  - `STORED_MISSING_PHYSICAL_FILE`
  - `DELETED_PHYSICAL_FILE_REMAINING`
  - `ORPHAN_PHYSICAL_FILE`
  - `INVALID_STORAGE_PATH`
  - `MISSING_ROW_PHYSICAL_FILE_PRESENT`
  - `IGNORED_UNMANAGED_FILE`
- Scan does not delete physical files.
- Scan does not mutate DB rows.
- Scan does not mark `STORED` rows as `MISSING`.
- Issue responses expose `fileKeyHash` instead of raw storage keys.
- Scan responses do not expose `storedFileName`, `storagePath`, storage root, absolute path, or local filesystem paths.
- `AttachmentStorageService` now has result-returning `deleteIfExistsWithResult(...)`; the legacy `deleteIfExists(...)` method remains as a default compatibility wrapper.
- `LocalAttachmentStorageService` returns `AttachmentStorageDeleteResult` for deleted, absent, invalid-path, and failure outcomes.
- `ApplicationAttachmentDeleteService` logs post-commit physical delete results with application/attachment IDs and safe result metadata while preserving the existing delete API response.
- Cleanup execution, scheduler, quarantine, admin repair, mark-missing command, persisted scan history, include-deleted read, and object storage scanning remain deferred.

Implementation reference:

- `docs/codex/implementation/phase-03i-4-3-attachment-storage-health-scan.md`
- `docs/codex/reports/phase-03i-4-3-attachment-storage-health-scan.html`

## Phase 03i-4-2 Implementation Note

Phase 03i-4-2 implemented the delete-command portion of this design.

Implemented:

- Added `PhysicalFileStatus.DELETED`.
- Added `AttachmentDeleteActorType` with `APPLICANT` and `EMPLOYEE`.
- Added minimal delete lifecycle fields to `ApplicationAttachment`:
  - `deletedAt`
  - `deletedBy`
  - `deletedByType`
  - `deletionReason`
- Added `ApplicationAttachment.markDeleted(...)`.
- Added applicant delete command:
  - `POST /applications/{applicationId}/attachments/{attachmentId}/delete`
- Added admin delete command:
  - `POST /admin/applications/{applicationId}/attachments/{attachmentId}/delete`
  - request body: `{ "reason": "..." }`
  - `reason` uses `@NotBlank` and `@Size(max=1000)`.
- Applicant delete uses current applicant ownership plus existing writable policy: `DRAFT`, published posting, and accepting period.
- Admin delete allows `DRAFT`, `SUBMITTED`, and `WITHDRAWN` application rows and persists the trimmed reason.
- Delete command lookup excludes `DELETED`; already deleted rows return controlled 404.
- Applicant and admin normal metadata lists exclude `DELETED`.
- Download API structure was not changed; download still looks up only `STORED`, so `DELETED` rows return controlled 404.
- Upload append `sortOrder` continues to use max sort order across all rows, including `DELETED`.
- Metadata replace is not blocked by deleted row sort order.
- DB soft delete occurs before physical file deletion.
- Physical deletion for previously `STORED` rows runs after transaction commit.
- Physical deletion failure is logged only and does not roll back DB state.
- Delete response exposes only `applicationId`, `attachmentId`, `deleted`, `physicalDeleteRequested`, and `message`.
- Delete response does not expose `storedFileName`, `storagePath`, `storageRoot`, absolute path, `physicalFileStatus`, or `downloadAvailable`.
- Separate deletion history table, include-deleted read, orphan scan/cleanup, admin repair, mark-missing, required attachment policy, dashboard/submit integration, and HTTP DELETE remain deferred.

Implementation reference:

- `docs/codex/implementation/phase-03i-4-2-attachment-delete-command.md`
- `docs/codex/reports/phase-03i-4-2-attachment-delete-command.html`

## Phase Name

Phase 03i-4 - Attachment Delete / Orphan Cleanup / Admin Repair Design

## Purpose

Phase 03i-4 defines the attachment lifecycle policy that remains after Phase 03i-2 upload and Phase 03i-3 download:

- applicant attachment delete policy;
- admin attachment delete policy;
- DB row state and physical file delete ordering;
- missing physical file handling;
- orphan physical file cleanup;
- orphan DB row / `MISSING` row repair;
- audit/logging policy;
- follow-up phase split.

This is a documentation-only design phase. It does not implement delete endpoints, cleanup schedulers, admin repair commands, Java source, test source, `SecurityConfig`, build files, YAML, database schema, runtime APIs, upload/download behavior, dashboard readiness, submit validation, S3/NAS migration, virus scan/DLP integration, or a `downloadAvailable` field.

## Scope

- Decide applicant delete eligibility.
- Decide admin delete eligibility.
- Compare hard delete vs soft state delete.
- Choose the recommended DB row lifecycle policy.
- Define physical file delete and DB transaction ordering.
- Define compensation policy when physical delete fails.
- Distinguish missing physical files from orphan physical files.
- Define orphan cleanup and admin repair candidates without implementation.
- Define response exposure and storage-internal non-exposure rules.
- Define audit/logging requirements.
- Define metadata list behavior for deleted rows.
- Define implementation phase split and test plan.
- Create a paired self-contained HTML report.
- Update related design and history documents.

## Out of Scope

- Java source changes.
- Test source changes.
- Controller, Service, Repository, Entity, DTO, enum, scheduler, or command implementation.
- `SecurityConfig` changes.
- Build, YAML, or DB schema changes.
- Actual delete endpoint implementation.
- Actual cleanup scheduler implementation.
- Actual admin repair command implementation.
- Upload/download API behavior changes.
- Dashboard readiness changes.
- Submit validator changes.
- S3, NAS, or object storage migration.
- Virus scan or DLP integration.
- `downloadAvailable` field.

## Changed Files

| Path | Change Type | Notes |
|---|---|---|
| `docs/codex/design/phase-03i-4-attachment-delete-cleanup-repair-design.md` | New | Codex reference design |
| `docs/codex/reports/phase-03i-4-attachment-delete-cleanup-repair-design.html` | New | Human-readable self-contained report |
| `docs/codex/design/phase-03i-attachment-file-upload-download-design.md` | Modified | Adds Phase 03i-4 design note |
| `docs/codex/design/phase-03c-application-detail-design.md` | Modified | Adds Phase 03i-4 design note |
| `docs/codex/design/phase-03-application-design.md` | Modified | Adds Phase 03i-4 design note |
| `docs/codex/07-implementation-history.md` | Modified | Adds Phase 03i-4 history entry |

No production code, test code, config, schema, or runtime API is changed in this phase.

## Current Attachment Lifecycle State

Phase 03i-2 and Phase 03i-3 established these runtime facts:

- Applicant upload exists at `POST /applications/{applicationId}/attachments/files`.
- Upload creates `ApplicationAttachment` rows with `physicalFileStatus=STORED`.
- Metadata-only rows use `physicalFileStatus=METADATA_ONLY`.
- `MISSING` enum exists but is not actively assigned by runtime code.
- Applicant download exists at `GET /applications/{applicationId}/attachments/{attachmentId}/download`.
- Admin download exists at `GET /admin/applications/{applicationId}/attachments/{attachmentId}/download`.
- Download is allowed only for `physicalFileStatus=STORED`.
- `METADATA_ONLY`, `MISSING`, application mismatch, attachment mismatch, and missing physical files return controlled 404.
- Missing physical files do not automatically update DB status in Phase 03i-3.
- Local filesystem storage assumes a single node or shared volume.
- Storage internals are not exposed in metadata or download responses.

## Delete Policy Options

### Option A - Hard Delete DB Row + Delete Physical File

Behavior:

- Remove the `ApplicationAttachment` row.
- Delete the physical file from storage.

Pros:

- The list naturally stops showing the attachment.
- Implementation is simple for DRAFT-only applicant delete.
- No new status filtering is required.

Cons:

- Audit trace is weak.
- Submitted-application evidence can disappear without a durable lifecycle record.
- DB transaction and filesystem deletion cannot be atomic.
- If DB update and file delete diverge, diagnosis depends on logs only.

### Option B - Keep DB Row + Soft Lifecycle State + Delete Physical File

Behavior:

- Keep the `ApplicationAttachment` row.
- Mark the row as deleted, preferably with `physicalFileStatus=DELETED`.
- Exclude deleted rows from normal applicant/admin metadata lists.
- Delete the physical file after the DB transaction commits.
- Preserve enough information for audit or later deletion-history migration.

Pros:

- Operationally safer for a recruitment system with evidence/audit needs.
- Allows minimal delete audit fields such as `deletedAt`, `deletedBy`, `deletedByType`, and `deletionReason`.
- Allows admin-only include-deleted/audit views later.
- Easier to reason about delete requests after a successful DB state transition.

Cons:

- Requires enum and possibly schema expansion in a later implementation phase.
- Default metadata list queries must filter deleted rows.
- Physical delete failure may leave an orphan file until cleanup runs.

### Option C - Narrow MVP Delete Only

Behavior:

- Implement only applicant `DRAFT` delete first.
- Keep admin delete, audit table, cleanup, and repair commands deferred.
- Could use hard delete for MVP or soft status if schema work is accepted.

Pros:

- Smallest implementation surface.
- Useful if frontend only needs applicants to remove mistaken draft uploads.

Cons:

- Does not solve admin operations.
- If hard delete is chosen for MVP, later audit behavior is harder to reconstruct.
- Cleanup/repair gaps remain.

## Recommended Policy

The recommended default is Option B: keep the DB row and move it to a deleted lifecycle state.

Decision:

- Do not hard delete attachment rows as the default policy.
- Add `DELETED` to `PhysicalFileStatus` in the implementation phase that actually implements delete.
- Keep `METADATA_ONLY`, `STORED`, and `MISSING`.
- Do not use `ORPHANED` as a DB enum value because orphan physical files have no DB row.
- Consider `DELETE_FAILED` only if a later two-step delete workflow persists physical delete results in DB. It is not required for the minimal delete command.
- Normal metadata reads must exclude `DELETED` rows.
- Download must reject `DELETED` rows with the same controlled 404 style as `METADATA_ONLY` and `MISSING`.
- Delete commands must also exclude `DELETED` rows from lookup and return 404 when the same attachment is deleted again.
- POST delete commands are not required to behave like idempotent HTTP DELETE; repeated success would make admin reason and audit semantics ambiguous.
- Admin include-deleted read is deferred to an audit phase.

Minimum later implementation recommendation:

- Phase 03i-4-2 should implement applicant DRAFT delete and admin delete commands with the soft `DELETED` state.
- Phase 03i-4-2 should persist admin delete reason on `ApplicationAttachment` with minimal delete audit fields.
- Implemented minimal fields are `deletedAt`, `deletedBy`, `deletedByType`, and `deletionReason`.
- `ApplicationAttachmentDeletionHistory` remains deferred; the retained attachment row is the MVP audit record.
- Server logs remain useful for operations, but they are not the source of truth for admin delete reason.

## Applicant Delete Policy

Recommended applicant behavior:

- Applicant can delete only attachments belonging to the current applicant's own application.
- Applicant delete is allowed only when:
  - application status is `DRAFT`;
  - job posting status is `PUBLISHED`;
  - current time is inside the reception period;
  - the attachment belongs to the application.
- Applicant delete is rejected for:
  - `SUBMITTED` applications;
  - `WITHDRAWN` applications;
  - closed/unpublished postings;
  - outside reception period;
  - other applicant resources;
  - attachment/application mismatch.
- Other applicant access keeps the existing hidden 404 policy.
- Applicant delete should support `STORED` rows and `METADATA_ONLY` rows.
- Already `DELETED` rows are not valid delete targets and return 404.
- Deleting a `METADATA_ONLY` row only changes DB lifecycle state; there is no physical file operation.
- Deleting a `STORED` row changes DB lifecycle state and requests physical file deletion after commit.
- Missing physical file during delete should not fail the user-facing delete after the DB row is marked deleted; it should be logged and treated as already absent.

Rationale:

- Applicant-side delete is an editing operation, so it should follow the same writable-window policy as upload.
- Submitted evidence should not be deleted by the applicant because it may be used for screening, audit, or dispute handling.

## Admin Delete Policy

Recommended admin behavior:

- Admin/recruit-admin can delete attachments on `DRAFT`, `SUBMITTED`, and `WITHDRAWN` applications after authorization.
- Admin delete requires a reason.
- A reason is especially mandatory for `SUBMITTED` and `WITHDRAWN` applications because the operation can affect screening evidence.
- Applicant users on admin paths receive 403 through security.
- Anonymous users receive 401 through security.
- Attachment/application mismatch returns 404.
- Already `DELETED` rows are not valid delete targets and return 404.
- Admin delete must not expose storage internals in response or error details.

Initial implementation boundary:

- Implement admin delete as a command endpoint with a persisted reason on the attachment row.
- Do not implement a full audit table unless a later phase explicitly includes it.
- Preserve a future extension point for `ApplicationAttachmentDeletionHistory`.

## API Candidates

### Recommended Delete Commands

Use POST command endpoints instead of HTTP DELETE, matching the current command style.

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| `POST` | `/applications/{applicationId}/attachments/{attachmentId}/delete` | Applicant deletes an owned editable attachment | none or empty body | `ApiResponse<AttachmentDeleteResponse>` |
| `POST` | `/admin/applications/{applicationId}/attachments/{attachmentId}/delete` | Admin deletes an attachment with reason | JSON body with `reason` | `ApiResponse<AttachmentDeleteResponse>` |

### Response Candidate

`AttachmentDeleteResponse` candidate fields:

| Field | Expose? | Notes |
|---|---:|---|
| `attachmentId` | Yes | Deleted attachment id |
| `applicationId` | Yes | Owning application id |
| `deleted` | Yes | DB lifecycle state changed |
| `physicalDeleteRequested` | Yes | True for previously `STORED` rows |
| `physicalDeleteCompleted` | Candidate | Only if known synchronously; otherwise omit or return null |
| `message` | Yes | User-safe message |
| `storagePath` | No | Internal storage key |
| `storedFileName` | No | Internal filename |
| `storageRoot` | No | Infrastructure path |
| absolute local path | No | Security-sensitive operational detail |

Recommended response policy:

- Do not expose physical paths even to admins.
- Avoid returning `physicalDeleteCompleted=true` if file deletion is after-commit and not part of the service return path.
- Prefer a truthful response such as `deleted=true`, `physicalDeleteRequested=true`, and `physicalDeleteCompleted=null` when deletion is asynchronous or after-commit.

### Admin Repair / Cleanup Candidates

These are candidates only; do not implement all in Phase 03i-4-2.

| Method | Path | Purpose | Recommendation |
|---|---|---|---|
| `GET` | `/admin/attachments/storage-health` | Read last scan summary | Later phase |
| `POST` | `/admin/attachments/storage-health/scan` | Start dry-run scan | Implemented in Phase 03i-4-3 |
| `POST` | `/admin/applications/{applicationId}/attachments/{attachmentId}/mark-missing` | Mark a `STORED` row as `MISSING` | Later repair phase |
| `POST` | `/admin/applications/{applicationId}/attachments/{attachmentId}/repair` | Repair metadata or reattach file | Later repair phase |
| `POST` | `/admin/attachments/orphans/cleanup` | Cleanup orphan physical files | Phase 03i-4-4 candidate |

Keep Phase 03i-4-2 narrow: delete commands only.

## Transaction / Compensation Strategy

Filesystem operations and DB transactions are not atomic. The design must choose the mismatch that is easier to repair.

### Rejected Ordering: Physical Delete First

Problems:

- If physical delete succeeds but DB commit fails, the row still appears to exist but the file is gone.
- Download then becomes a `STORED` row with missing physical file.
- This is harder to explain to users and auditors.

### Recommended Ordering: DB State First, Physical Delete After Commit

Recommended flow for `STORED` row:

1. Validate ownership, role, application state, attachment/application match, and delete reason when required.
2. Store the current relative `storagePath` in memory for the after-commit delete operation.
3. Update DB row to `physicalFileStatus=DELETED` inside the transaction.
4. Exclude the row from normal metadata reads after commit.
5. Register an after-commit action to delete the physical file.
6. If physical delete succeeds, log success at debug/info level if needed.
7. If physical delete fails, log warn/error with a cleanup-safe identifier and schedule or report the path for orphan cleanup.

Recommended flow for `METADATA_ONLY` row:

1. Validate policy.
2. Update DB row to `DELETED`.
3. Do not request physical delete.

Recommended flow for `STORED` row whose physical file is already missing:

1. Validate policy.
2. Update DB row to `DELETED`.
3. After-commit delete attempts may see no file and should be idempotent.
4. Log as already absent or missing, without exposing paths to API clients.

Rationale:

- After DB commit, users no longer see the attachment in normal metadata lists.
- If physical deletion fails, the leftover file is an orphan physical file that can be detected by cleanup.
- Orphan physical files are less user-visible than DB rows pointing to missing files.

### Two-Step Delete Candidate

An operations-heavy system can later use:

- `DELETE_REQUESTED` or `DELETING`;
- after-commit physical delete;
- final `DELETED` or `DELETE_FAILED`.

This is safer for detailed operations but too broad for the next implementation phase. It can be revisited if audit/compliance requires persistent physical delete status.

## Missing vs Orphan Definitions

| Case | Definition | DB Row Exists | Physical File Exists | Policy |
|---|---|---:|---:|---|
| `MISSING` DB row | DB row says file should exist but storage file is absent | Yes | No | Detect by scan or admin repair; normal download returns 404 |
| Orphan physical file | File exists under storage root but no active DB row references it | No active row | Yes | Detect by dry-run scan; cleanup after admin confirmation |
| Metadata-only row | DB row never had a physical file | Yes | No | No repair needed |
| Deleted row | DB row is retained as deleted lifecycle state | Yes | Maybe temporarily | Excluded from normal lists; physical file should be removed after commit |

Important distinction:

- `MISSING` is a DB lifecycle state for rows that still exist.
- Orphan physical files are storage artifacts without an active DB reference and must not be represented by a `PhysicalFileStatus.ORPHANED` enum.

## Orphan Cleanup Policy

Recommended cleanup approach:

- Do not implement a scheduler in Phase 03i-4-2.
- Start with a manual dry-run scan in a later phase.
- Dry-run must report candidate orphan files without deleting them.
- Cleanup must never escape the configured storage root.
- Cleanup should ignore unknown directories unless the scanner explicitly owns the storage layout.
- Physical deletion should prefer quarantine/move before permanent delete when operations can support it.
- If quarantine is too much for MVP, use log-only report -> admin confirmation -> delete.

Recommended scan inputs:

- Storage root.
- Expected server-generated key pattern, such as `applications/{applicationId}/...`.
- Active DB storage keys for `physicalFileStatus=STORED`.
- Deleted DB rows whose after-commit cleanup may still be pending.

Recommended scan outputs:

- orphan physical file candidates;
- `STORED` rows with missing physical files;
- `DELETED` rows with still-present physical files;
- invalid path/key records;
- dry-run deletion count and total size.

## Orphan DB Row / MISSING Repair Policy

Recommended repair direction:

- Do not mutate `STORED` rows to `MISSING` during user download in the delete phase.
- Add admin scan/repair as a separate phase.
- A scan can detect `STORED` rows with missing files and report them.
- A later admin command may mark those rows as `MISSING`.
- Repair by reattaching a file should be a separate command because it changes evidence and requires audit.

Candidate admin repair commands:

- `POST /admin/applications/{applicationId}/attachments/{attachmentId}/mark-missing`
- `POST /admin/applications/{applicationId}/attachments/{attachmentId}/repair`

Both are deferred.

## Audit / Logging Policy

Minimum logging:

- applicant delete actor: current applicant id and login id candidate;
- admin delete actor: current employee login id candidate;
- actor type: `APPLICANT` or `EMPLOYEE`;
- application id;
- attachment id;
- attachment type;
- original filename;
- previous physical file status;
- delete reason for admin;
- timestamp;
- physical delete requested/completed/failed log status.

Do not log:

- full absolute storage root;
- absolute local path in normal application logs;
- secrets or credentials.

Future audit table candidate: `ApplicationAttachmentDeletionHistory`.

Candidate fields:

| Field | Purpose |
|---|---|
| `id` | History id |
| `attachmentId` | Original attachment id |
| `applicationId` | Owning application id |
| `originalFileName` | Display filename snapshot |
| `attachmentType` | Business type snapshot |
| `actorId` | Applicant/user/employee id candidate |
| `actorLoginId` | Login id snapshot |
| `actorType` | `APPLICANT` or `EMPLOYEE` |
| `reason` | Admin reason or applicant self-delete marker |
| `deletedAt` | Delete request time |
| `physicalDeleteStatus` | Requested/completed/failed candidate |

The audit table is deferred unless a later phase explicitly includes it.

### Phase 03i-4-2 Minimum Persistent Delete Fields

Phase 03i-4-2 should not keep admin delete reason only in logs. The minimum realistic implementation is to persist delete metadata on `ApplicationAttachment` while keeping the separate history table deferred.

Recommended fields:

| Field | Required | Notes |
|---|---:|---|
| `deletedAt` | Yes | Set when the row moves to `DELETED` |
| `deletedBy` | Yes | Actor login id or stable id string; avoid requiring a hard FK at this stage |
| `deletedByType` | Yes | `APPLICANT` or `EMPLOYEE` candidate |
| `deletionReason` | Required for admin, fixed marker for applicant | Admin request reason; applicant self-delete uses `APPLICANT_SELF_DELETE` |

Rationale:

- A required admin reason that is only logged is weak for operations and audit.
- Retaining the attachment row plus delete fields is enough for the MVP audit trail.
- A separate `ApplicationAttachmentDeletionHistory` table can still be added later if immutable history or multiple lifecycle events are required.

## Metadata Response Impact

Recommended default:

- Applicant metadata list excludes `DELETED` rows.
- Admin metadata list excludes `DELETED` rows by default.
- Download rejects `DELETED` rows with controlled 404.
- Applicant/admin delete commands reject already `DELETED` rows with controlled 404.
- `includeDeleted` query option is deferred until audit/read policy exists.
- `downloadAvailable` remains omitted.
- `physicalFileStatus` remains internal and is not exposed in applicant/admin metadata responses.

Rationale:

- Normal UI should show current editable/readable attachments only.
- Deleted evidence requires a separate audit view rather than overloading the main metadata list.

## Sort Order Policy With Deleted Rows

Phase 03i-4-2 must define how retained `DELETED` rows affect ordering.

Recommended policy:

- Upload append sort order continues to use the max `sortOrder` across all rows for the application, including `DELETED`.
- New uploads therefore always use `max(all attachment rows sortOrder) + 1`.
- Do not reuse a deleted row's `sortOrder` for new physical uploads.
- This preserves internal ordering history and avoids audit/read confusion when deleted rows are later inspected.

Metadata replace policy:

- Metadata replace conflict checks should use active rows only.
- Active rows are rows not in `DELETED`.
- `DELETED` row `sortOrder` values are not conflict targets for metadata replace.
- This allows the visible metadata list UI to remain compact while preserving append-only upload ordering for physical files.

Rationale:

- Upload is append-only and should not reuse historical physical file order values.
- Metadata-only list editing is a visible-list operation, so it should not be blocked by deleted hidden rows.
- `STORED` active rows must still block metadata replace sort-order collisions as implemented in Phase 03i-2.

## Phase 03i-5 Required Attachment Policy Connection

Phase 03i-4 delete policy directly affects the later required attachment and dashboard readiness phase.

Recommended connection points:

- Phase 03i-5 required attachment checks must count only active, usable rows.
- Active physical attachments should be `physicalFileStatus=STORED`.
- `METADATA_ONLY`, `MISSING`, and `DELETED` rows must not satisfy a required physical attachment rule.
- If a future requirement accepts metadata-only evidence, that rule must be explicit per `AttachmentType`; do not treat all metadata rows as valid files.
- Dashboard readiness should not show deleted rows as present.
- Submit validation should not allow a required attachment to pass with a deleted or missing file.
- Orphan physical files must never satisfy readiness because they have no active DB row.
- Admin repair that reattaches or marks a file available can affect readiness and therefore needs audit.

Phase 03i-5 should reuse the same active-row filtering introduced by the delete implementation instead of re-implementing attachment visibility rules independently.

## Entity / DTO / Service / Controller Summary

No class is implemented in Phase 03i-4. Candidate later classes or modifications:

| Package | Candidate Class | Type | Responsibility |
|---|---|---|---|
| `com.shinyoung.recruit.enumeration` | `PhysicalFileStatus` | Enum | Add `DELETED` if soft delete is implemented |
| `com.shinyoung.recruit.domain.entity` | `ApplicationAttachment` | Entity | Add deleted lifecycle state and minimal delete audit fields |
| `com.shinyoung.recruit.domain.repository` | `ApplicationAttachmentRepository` | Repository | Active-row filters, scoped delete lookup excluding `DELETED`, storage-health queries |
| `com.shinyoung.recruit.dto.request` | `AttachmentAdminDeleteRequest` | Request DTO | Admin delete reason |
| `com.shinyoung.recruit.dto.response` | `AttachmentDeleteResponse` | Response DTO | User-safe delete command result |
| `com.shinyoung.recruit.service` | `ApplicationAttachmentDeleteService` | Service | Applicant/admin delete orchestration |
| `com.shinyoung.recruit.service` | `AttachmentOrphanScanService` | Service | Dry-run scan candidate for later phase |
| `com.shinyoung.recruit.controller` | `ApplicationAttachmentController` | Controller | Applicant delete command candidate |
| `com.shinyoung.recruit.controller` | `AdminApplicationAttachmentController` | Controller | Admin delete command candidate |

## API List

| Method | Path | Purpose | Status |
|---|---|---|---|
| `POST` | `/applications/{applicationId}/attachments/{attachmentId}/delete` | Applicant delete command | Recommended for Phase 03i-4-2 |
| `POST` | `/admin/applications/{applicationId}/attachments/{attachmentId}/delete` | Admin delete command with reason | Recommended for Phase 03i-4-2 |
| `POST` | `/admin/attachments/storage-health/scan` | Dry-run storage scan | Candidate for Phase 03i-4-3 |
| `POST` | `/admin/attachments/orphans/cleanup` | Orphan cleanup | Candidate for Phase 03i-4-4 |
| `POST` | `/admin/applications/{applicationId}/attachments/{attachmentId}/mark-missing` | Mark missing file row | Deferred repair phase |
| `POST` | `/admin/applications/{applicationId}/attachments/{attachmentId}/repair` | Repair attachment row/file | Deferred repair phase |

## Validation and Business Rules

- Applicant delete uses the current applicant identity and existing ownership hidden-404 policy.
- Applicant delete is allowed only for `DRAFT` applications during the accepting window.
- Applicant delete rejects `SUBMITTED` and `WITHDRAWN` applications.
- Admin delete may operate on `DRAFT`, `SUBMITTED`, and `WITHDRAWN` applications.
- Admin delete requires a reason.
- Attachment/application mismatch returns 404.
- Already deleted attachment rows return 404 for delete commands.
- Deleted rows are excluded from normal metadata lists.
- Deleted rows are not downloadable.
- Upload append `sortOrder` uses the max across all attachment rows, including `DELETED`.
- Metadata replace sort-order conflict checks use active rows only and ignore `DELETED`.
- Admin delete reason is persisted on `ApplicationAttachment` through minimal delete audit fields in Phase 03i-4-2.
- Physical file deletion is after DB commit.
- Physical delete failure does not roll back a committed DB delete state; it creates cleanup work.
- Missing physical file during delete is handled idempotently.
- Storage internals are never exposed in API responses.
- Orphan cleanup must be dry-run first.
- Cleanup and repair commands are separate from the initial delete command implementation.

## Phase Split Recommendation

| Phase | Scope | Explicitly Not Included |
|---|---|---|
| Phase 03i-4-1 | Delete/cleanup/repair design only | Any Java/test/config/schema/runtime changes |
| Phase 03i-4-2 | Applicant DRAFT delete + admin delete command using soft `DELETED` lifecycle state; reason required and persisted for admin; already deleted rows return 404 | Scheduler, repair API, separate deletion history table |
| Phase 03i-4-3 | Orphan storage scan dry-run and storage-health report | Physical deletion of candidates; implemented |
| Phase 03i-4-4 | Admin cleanup/repair commands after dry-run policy is validated | Upload/download redesign |
| Phase 03i-5 | Attachment required policy + dashboard/submit integration | Storage lifecycle mechanics unless gaps remain |

## Test Plan for Implementation Phases

### Applicant Delete Tests

- Owner `DRAFT` delete succeeds.
- Owner `SUBMITTED` delete fails.
- Owner `WITHDRAWN` delete fails.
- Other applicant access returns hidden 404.
- Attachment/application mismatch returns 404.
- `METADATA_ONLY` delete changes DB lifecycle only.
- `STORED` delete marks row deleted and requests physical delete.
- Already `DELETED` row delete returns 404.
- Missing physical file delete is idempotent and does not expose paths.
- Deleted row is absent from normal applicant metadata list.
- Deleted row cannot be downloaded.
- Upload after delete uses max `sortOrder` across all rows including `DELETED`.
- Metadata replace may reuse a `DELETED` row's visible `sortOrder`, but not an active `STORED` row's `sortOrder`.
- Response does not expose `storedFileName`, `storagePath`, storage root, or absolute path.

### Admin Delete Tests

- Admin delete succeeds.
- Recruit-admin delete succeeds.
- Applicant on admin path receives 403.
- Anonymous admin delete receives 401.
- Attachment/application mismatch returns 404.
- Reason is required.
- Reason is persisted on `ApplicationAttachment` minimal delete audit fields.
- Submitted application admin delete requires reason.
- Already `DELETED` row delete returns 404.
- Deleted row is absent from normal admin metadata list.
- Deleted row cannot be downloaded.
- Response and errors do not expose storage internals.

### Cleanup / Repair Candidate Tests

- Orphan physical file scan detects files without active DB rows.
- `STORED` row missing physical file is detected.
- `DELETED` row with still-present physical file is detected.
- Dry-run does not delete files.
- Cleanup does not escape storage root.
- Cleanup does not delete unknown directories.
- Invalid absolute or traversal storage keys are rejected.

## Test Commands

Gradle tests were not executed because this is a documentation-only design phase with no Java, test, config, schema, or runtime API changes.

Recommended Phase 03i-4-2 targeted commands after implementation:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*ApplicationAttachmentDelete*" --no-daemon
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*ApplicationAttachment*" --no-daemon
```

Recommended full regression after implementation:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test --no-daemon
```

## Test Results

| Command | Result | Notes |
|---|---|---|
| Gradle tests | Not run | Documentation-only phase; no Java/test/config/schema changes |
| Documentation verification | Success | `git diff --check` reported no whitespace errors for the touched docs; HTML report has no external script, CDN, or URL dependency |

## Remaining Decisions

- Exact Java field names for the minimal delete audit fields on `ApplicationAttachment`.
- Whether physical delete completion should be represented in DB or handled by logs plus cleanup scan.
- Whether cleanup uses quarantine/move before permanent delete.
- Whether a later admin view should support `includeDeleted`.
- Whether production storage migration should happen before scheduler-based cleanup.

## Next Phase Recommendation

Recommended next phase: Phase 03i-4-2.

Implement the smallest useful delete command set:

- add soft deleted lifecycle state;
- implement applicant DRAFT delete;
- implement admin delete with required reason;
- persist minimal delete audit fields on `ApplicationAttachment`;
- return 404 for already `DELETED` rows on delete commands;
- update normal metadata queries to exclude deleted rows;
- keep upload append ordering based on all rows including `DELETED`;
- keep metadata replace sort-order collision checks based on active rows only;
- keep physical delete after DB commit;
- log physical delete failure for later cleanup;
- keep scheduler, repair, and separate deletion history table work split into later phases.
