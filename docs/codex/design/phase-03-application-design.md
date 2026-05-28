# Phase 03 Application Design

## Phase 03i-4-3 Attachment Storage Health Scan Implementation Note

- Phase 03i-4-3 implemented the admin storage health scan dry-run API:
  - `POST /admin/attachments/storage-health/scan`
  - response: `ApiResponse<AttachmentStorageHealthScanResponse>`
- Existing `/admin/**` authorization is reused; `SecurityConfig` was not modified.
- The scan is read-only and does not delete files, mutate DB rows, or mark attachments as `MISSING`.
- The scan compares managed local storage files with `ApplicationAttachment` rows in `STORED`, `DELETED`, and `MISSING`.
- `METADATA_ONLY` rows are excluded from physical file mismatch checks.
- The response summarizes stored-row missing files, deleted-row remaining files, orphan physical files, invalid storage keys, missing-row physical files, and ignored unmanaged files.
- Issue rows expose `fileKeyHash` and safe metadata only; raw storage paths, storage roots, absolute paths, and stored filenames are not exposed.
- `AttachmentStorageService.deleteIfExistsWithResult(...)` was added for delete observability while preserving the existing delete API contract.
- Cleanup execution, quarantine, scheduler, admin repair, mark-missing command, and persisted scan history remain deferred.
- References:
  - `docs/codex/implementation/phase-03i-4-3-attachment-storage-health-scan.md`
  - `docs/codex/reports/phase-03i-4-3-attachment-storage-health-scan.html`

## Phase 03i-4-2 Attachment Delete Command Implementation Note

- Phase 03i-4-2 implemented attachment soft delete command APIs:
  - Applicant: `POST /applications/{applicationId}/attachments/{attachmentId}/delete`
  - Admin: `POST /admin/applications/{applicationId}/attachments/{attachmentId}/delete`
- Applicant delete is bodyless and allowed only for the current applicant's own `DRAFT` application while the published posting is accepting.
- Admin delete requires `{ "reason": "..." }`, validates it with `@NotBlank` and `@Size(max=1000)`, and allows `DRAFT`, `SUBMITTED`, and `WITHDRAWN`.
- Added `PhysicalFileStatus.DELETED`.
- Added `AttachmentDeleteActorType.APPLICANT` and `AttachmentDeleteActorType.EMPLOYEE`.
- Added `ApplicationAttachment` lifecycle fields: `deletedAt`, `deletedBy`, `deletedByType`, and `deletionReason`.
- Delete keeps the DB row and marks it `DELETED`; it does not hard-delete attachment rows.
- Already deleted rows, attachment/application mismatch, missing application, and other applicant access return controlled/hidden 404.
- Applicant/admin normal metadata lists exclude `DELETED`.
- Download API remains unchanged and only accepts `STORED`, so deleted rows are non-downloadable 404 cases.
- Physical file deletion happens after the DB transaction commits; physical delete failure is logged and does not roll back the committed DB soft delete.
- Delete response uses `AttachmentDeleteResponse` and does not expose storage internals, `physicalFileStatus`, or `downloadAvailable`.
- Orphan scan/cleanup, admin repair, include-deleted read, separate deletion history table, required attachment policy, dashboard readiness, submit validation, and HTTP DELETE remain deferred.
- References:
  - `docs/codex/implementation/phase-03i-4-2-attachment-delete-command.md`
  - `docs/codex/reports/phase-03i-4-2-attachment-delete-command.html`

## Phase 03i-4 Attachment Delete / Cleanup / Repair Design Note

- Phase 03i-4 designed attachment delete, orphan cleanup, and admin repair policy after Phase 03i-2 upload and Phase 03i-3 download.
- This is a documentation-only design phase. It does not change Java source, tests, `SecurityConfig`, build, YAML, DB schema, runtime APIs, upload/download behavior, dashboard readiness, submit validator, S3/NAS, virus scan/DLP, or `downloadAvailable`.
- Candidate delete command APIs:
  - Applicant: `POST /applications/{applicationId}/attachments/{attachmentId}/delete`
  - Admin: `POST /admin/applications/{applicationId}/attachments/{attachmentId}/delete`
- Use POST command endpoints rather than HTTP DELETE.
- Hard DB row delete is not the default recommendation. Use soft lifecycle state:
  - retain the `ApplicationAttachment` row;
  - `PhysicalFileStatus.DELETED` was added in Phase 03i-4-2;
  - exclude deleted rows from normal metadata lists;
  - treat deleted rows as non-downloadable 404 cases.
- Repeated delete of an already `DELETED` row should return 404.
- Upload append ordering keeps using max `sortOrder` across all rows including `DELETED`.
- Metadata replace sort-order collision checks use active rows only and may ignore `DELETED` row sort orders.
- Applicant delete policy:
  - current applicant's own application only;
  - allowed only for `DRAFT` + accepting window;
  - rejected for `SUBMITTED` and `WITHDRAWN`.
- Admin delete policy:
  - admin/recruit-admin can delete on `DRAFT`, `SUBMITTED`, and `WITHDRAWN`;
  - reason is required;
  - reason should be persisted on `ApplicationAttachment` through minimal delete audit fields;
  - separate audit/history table remains a later implementation candidate.
- Transaction policy:
  - update DB lifecycle state first;
  - delete physical file after transaction commit;
  - physical delete failure is logged and handled by later orphan cleanup.
- Missing physical file is distinct from orphan physical file. `MISSING` is a DB row state; orphan files have no active DB reference and should not be modeled as an enum value.
- Cleanup/repair should be split into later phases and begin with dry-run scan.
- Storage internals remain hidden in all candidate responses.
- References:
  - `docs/codex/design/phase-03i-4-attachment-delete-cleanup-repair-design.md`
  - `docs/codex/reports/phase-03i-4-attachment-delete-cleanup-repair-design.html`

## Phase 03i-3 Attachment File Download Implementation Note

- Phase 03i-3 implemented download APIs for stored attachment files:
  - Applicant: `GET /applications/{applicationId}/attachments/{attachmentId}/download`
  - Admin: `GET /admin/applications/{applicationId}/attachments/{attachmentId}/download`
- Successful download responses stream file bytes directly through `ResponseEntity<Resource>` and are not wrapped in `ApiResponse`.
- Error responses remain JSON through existing `GlobalExceptionHandler`, `CustomAuthenticationEntryPoint`, and `CustomAccessDeniedHandler`.
- Applicant access is limited to the current applicant's own application through `CurrentApplicantService` and existing ownership checks.
- Admin access follows the existing `/admin/**` policy for `ROLE_ADMIN` and `ROLE_RECRUIT_ADMIN`; `SecurityConfig` was not modified.
- Download is allowed only for `ApplicationAttachment.physicalFileStatus=STORED`.
- `METADATA_ONLY`, `MISSING`, missing application, attachment/application mismatch, and missing physical file cases return controlled 404.
- Missing physical files are logged and returned as 404 but do not mutate DB status to `MISSING` in this phase.
- Review fix: absolute `storagePath` values are rejected before root resolution; download storage lookup accepts only relative server-generated keys.
- Response headers include safe `Content-Disposition` with ASCII fallback and UTF-8 `filename*`, actual physical `Content-Length`, DB-backed `Content-Type` with octet-stream fallback, `nosniff`, `no-store`, and `no-cache`.
- Storage internals remain hidden: no `storedFileName`, `storagePath`, storage root, or absolute path is exposed in headers or bodies.
- Not changed: upload API, metadata replace, dashboard readiness, submit validator, admin upload, delete, orphan cleanup, S3/NAS, virus scan/DLP, required attachment policy, and `downloadAvailable`.
- References:
  - `docs/codex/implementation/phase-03i-3-attachment-file-download.md`
  - `docs/codex/reports/phase-03i-3-attachment-file-download.html`

## Phase 03i-2 Attachment File Upload Implementation Note

- Phase 03i-2 implemented applicant-owned physical attachment upload:
  - `POST /applications/{applicationId}/attachments/files`
  - Request: single `multipart/form-data` file plus `attachmentType`, `sectionType`, optional `sectionRecordId`
  - Response: `ApiResponse<AttachmentResponse>`
- Added `PhysicalFileStatus` and `ApplicationAttachment.physicalFileStatus`.
- New metadata rows are `METADATA_ONLY`; upload rows are `STORED`; `MISSING` is reserved for a later download/missing-file phase.
- Existing metadata replace now deletes/replaces only `METADATA_ONLY` rows and preserves `STORED` rows.
- Existing metadata replace rejects metadata `sortOrder` values that conflict with preserved `STORED` rows.
- Client-supplied `storedFileName` and `storagePath` in metadata JSON are rejected with 400.
- Upload rejects forbidden multipart parts: `sortOrder`, `displayName`, `originalFileName`, `storedFileName`, `storagePath`.
- Upload sort order is append-only and server-assigned from current max application attachment sort order + 1.
- File count and total-size limits are based only on `physicalFileStatus=STORED` rows.
- Local filesystem storage is implemented behind `AttachmentStorageService`.
- `AttachmentProperties` has startup validation for required storage root, positive size/count limits, and non-empty allowlists.
- `AttachmentResponse` still excludes storage internals and `downloadAvailable`.
- Not changed: `SecurityConfig`, dashboard readiness, submit validator, download, admin upload, delete, orphan cleanup, S3/NAS, virus scan/DLP, and attachment required policy.
- References:
  - `docs/codex/implementation/phase-03i-2-attachment-file-upload.md`
  - `docs/codex/reports/phase-03i-2-attachment-file-upload.html`

## Phase 03i-1 Attachment File Upload/Download Design Note

- Phase 03i-1 is a design-only phase for moving the existing attachment metadata slice toward real file upload/download.
- No Java source, test source, `SecurityConfig`, build, YAML, DB schema, existing attachment APIs, submit validator, dashboard readiness, StageResult API, storage service, upload implementation, or download implementation is changed.
- Current attachment state:
  - Applicant metadata APIs remain `GET /applications/{applicationId}/attachments` and `POST /applications/{applicationId}/attachments`.
  - Admin metadata API remains `GET /admin/applications/{applicationId}/attachments`.
  - `ApplicationAttachment` already has `originalFileName`, `storedFileName`, `storagePath`, `contentType`, `fileSize`, `attachmentType`, `sectionType`, `sectionRecordId`, and `sortOrder`.
  - Applicant/admin metadata responses still do not expose `storedFileName`, `storagePath`, absolute paths, or download URLs.
- Recommended upload API for Phase 03i-2:
  - `POST /applications/{applicationId}/attachments/files`
  - `multipart/form-data`
  - Parts: `file`, `attachmentType`, `sectionType`, optional `sectionRecordId`.
  - Do not accept display/original filename override in Phase 03i-2; derive `originalFileName` from the sanitized multipart original filename.
  - Add `ApplicationAttachment.physicalFileStatus` as `@Enumerated(EnumType.STRING)`, `nullable=false`, default `METADATA_ONLY`; existing rows and test fixtures are `METADATA_ONLY`.
  - Values are `METADATA_ONLY`, `STORED`, and `MISSING`; only `STORED` server-uploaded rows are downloadable.
  - Keep existing metadata replace API separate from physical file upload, but harden it before upload ships so only `METADATA_ONLY` rows are replaced, file-backed rows are preserved, and client-supplied `storedFileName`/`storagePath` are rejected with 400.
  - Keep forbidden storage fields in `AttachmentRequest` or otherwise detect them explicitly for 400; do not rely on global Jackson unknown-property failure.
  - File-backed row `sortOrder`, `attachmentType`, and `sectionType` edits are out of scope for Phase 03i-2 and need a later explicit reorder/update endpoint.
  - Do not accept `sortOrder` in upload requests; upload is append-only and the server assigns the next `sortOrder`.
  - Include server-assigned `sortOrder` when reusing `AttachmentResponse`; do not add `downloadAvailable` until Phase 03i-3 download semantics exist.
- Recommended download APIs for Phase 03i-3:
  - Applicant: `GET /applications/{applicationId}/attachments/{attachmentId}/download`
  - Admin: `GET /admin/applications/{applicationId}/attachments/{attachmentId}/download`
  - Use streaming response headers, not `ApiResponse`.
  - Include `Content-Type`, `Content-Length`, and `Content-Disposition` with ASCII `filename` fallback plus UTF-8 `filename*`.
- Storage recommendation:
  - Start with local filesystem storage behind an `AttachmentStorageService` abstraction.
  - Generate UUID/ULID based stored filenames.
  - Store under a server-generated relative key such as `applications/{applicationId}/{yyyy}/{MM}/{dd}/{uuid}.{ext}`.
  - Never use user-provided paths or original filenames for physical storage.
  - Normalize and verify final paths remain under `storageRoot`.
  - Local filesystem storage assumes single-node or shared-volume deployment. Multi-node production needs NAS, S3/object storage, or another shared durable store; sticky session is not a storage solution.
- Validation recommendation:
  - Property-backed max file size, recommended 20 MB default.
  - Separate Spring multipart parser limits from the business attachment limit and handle `MaxUploadSizeExceededException`.
  - Add property-backed per-application file count and total-size limits, calculated from `physicalFileStatus=STORED` rows only.
  - Initial extension allowlist: `pdf`, `jpg`, `jpeg`, `png`, `doc`, `docx`, `xls`, `xlsx`, `hwp`, `hwpx`.
  - Validate content type conservatively and do not trust the client-provided value alone.
  - Reject blank filenames, path separators, control characters, and Windows reserved names.
  - Register transaction rollback cleanup after file storage, and use `saveAndFlush(...)` to catch DB failures before return when possible.
  - Antivirus/malware scanning is deferred but should be treated as a production security requirement.
- Phase split:
  - Phase 03i-2: `physicalFileStatus` schema addition, metadata replace hardening, storage abstraction, applicant single-file upload, and DB-failure/rollback file cleanup compensation.
  - Phase 03i-3: applicant/admin download.
  - Phase 03i-4 candidate: admin upload/replace, delete, orphan cleanup.
  - Phase 03i-5 candidate: attachment submit-required policy and dashboard readiness integration.
- Tests were not run because this is a documentation-only phase.
- References:
  - `docs/codex/design/phase-03i-attachment-file-upload-download-design.md`
  - `docs/codex/reports/phase-03i-attachment-file-upload-download-design.html`

## Phase 03h-4 Applicant Application Dashboard Implementation Note

- Phase 03h-4 implemented the applicant-owned application dashboard summary API: `GET /applications/{applicationId:[0-9]+}/dashboard`.
- Response wrapper: `ApiResponse<ApplicationDashboardResponse>`.
- The endpoint uses `CurrentApplicantService`; `applicantId` is not accepted as a query parameter.
- The endpoint remains under the existing `/applications/**` `ROLE_APPLICANT` security policy.
- Employee/admin users receive 403 through Spring Security when security filters are active.
- Anonymous users receive 401 JSON through existing security exception handlers.
- Other applicant application access uses the existing hidden 404 ownership policy.
- Added response DTOs:
  - `ApplicationDashboardResponse`
  - `ApplicationCompletionSummaryResponse`
  - `ApplicationSectionReadinessResponse`
- Added services:
  - `ApplicationCompletionReadChecker`
  - `ApplicationDashboardService`
- Added repository support:
  - `JobApplicationRepository.findDashboardByIdAndApplicantId`
  - optional section `existsByJobApplicationId` methods for certificate, language, award, and gap period.
- Action flag policy:
  - `accepting = JobPosting.status == PUBLISHED` and current time is inside the reception period.
  - `editable = DRAFT + accepting`.
  - `submittable = DRAFT + accepting + submitBlockingIssueCount == 0`.
  - `withdrawable = SUBMITTED + accepting`.
  - `WITHDRAWN` returns all command flags as `false`.
- Completion checker policy:
  - Mirrors current `ApplicationSubmitValidator` in read-only form.
  - Blocking readiness covers `useEducation`, `useCareer`, `useMilitary`, active required question answers, and answer length.
  - Optional guidance covers `useCertificate`, `useLanguage`, `useAward`, and `useGapPeriod`.
  - Attachment readiness is still deferred.
- Result summary:
  - Uses existing applicant-visible StageResult query.
  - Only stages with `Stage.status == RESULT_ANNOUNCED || CLOSED` are visible.
  - Latest result uses `stageOrder DESC, stage.id DESC`.
  - Detailed results remain in `GET /applications/{applicationId}/stage-results`.
- Exposure policy:
  - Dashboard response excludes applicant personal data, `applicantId`, `stageResultId`, `score`, `comment`, `decidedBy`, `correctedBy`, correction history, `answerText`, `exemptionReason`, `certificateNumber`, and storage fields.
- Preserved:
  - `SecurityConfig`
  - `ApplicationSubmitValidator`
  - `POST /applications/{applicationId}/submit`
  - detailed section save APIs
  - applicant StageResult detail API
  - admin APIs
  - DB schema
- Tests passed:
  - `ApplicationDashboardServiceTest`
  - `ApplicationControllerTest`
  - `JobApplicationServiceTest`
  - `ApplicationSubmitValidatorTest`
  - `ApplicationStageResultServiceTest` + `ApplicationStageResultControllerTest`
- Full `clean test --no-daemon`: success.
- References:
  - `docs/codex/implementation/phase-03h-4-applicant-application-dashboard.md`
  - `docs/codex/reports/phase-03h-4-applicant-application-dashboard.html`

## Phase 03h-3 Applicant Application Dashboard Design Note

- Phase 03h-3 is a design-only phase for an applicant-owned application dashboard summary API.
- Candidate API: `GET /applications/{applicationId}/dashboard`.
- No Java source, test source, `SecurityConfig`, build, YAML, DB schema, Repository, Service, Controller, DTO, submit validator, StageResult API, or runtime dashboard implementation is changed in this phase.
- Recommended response wrapper: `ApiResponse<ApplicationDashboardResponse>`.
- Access policy:
  - The endpoint is under the existing `/applications/**` applicant policy.
  - Only `ROLE_APPLICANT` can access it.
  - Employee/admin users receive 403 and must use admin APIs.
  - The current applicant id must come from `CurrentApplicantService`; the request must not accept `applicantId`.
  - Access to another applicant's application should use the existing owned lookup and 404 hiding policy.
- Recommended response fields:
  - `applicationId`, `jobPostingId`, `jobPostingTitle`, `jobPositionName`
  - `applicationStatus`, `accepting`, `editable`, `submittable`, `withdrawable`
  - `submittedAt`, `withdrawnAt`
  - `completionSummary`
  - `requiredMissingSections`
  - `optionalIncompleteSections`
  - `latestAnnouncedStageName`, `latestResultStatus`
- Action flag policy:
  - `accepting = JobPosting.status == PUBLISHED` and current time is inside the reception period.
  - `editable = DRAFT + accepting`.
  - `submittable = DRAFT + accepting + no submit-blocking readiness issue`.
  - `withdrawable = SUBMITTED + accepting`.
  - `WITHDRAWN` returns every command flag as `false`.
- Completion and missing-section policy:
  - Phase 03h-4 should add a read-only checker that mirrors current `ApplicationSubmitValidator` behavior without changing the submit command.
  - `useEducation=true` requires at least one education row.
  - `useCareer=true` requires career profile, selected career type, and career row rules by type.
  - `useMilitary=true` requires military row, subject type, and type-specific required fields.
  - Active required posting questions require non-null, non-blank answers.
  - Present non-null answers must satisfy effective length rules.
  - `useCertificate`, `useLanguage`, `useAward`, and `useGapPeriod` are optional guidance under the current submit policy.
  - Attachment readiness is deferred because `ApplicationFormConfig` has no attachment flag.
- Result summary policy:
  - Same as Phase 03h-2: visible summary uses only `Stage.status == RESULT_ANNOUNCED || CLOSED`.
  - `READY` and `IN_PROGRESS` are excluded.
  - Detailed rows remain in `GET /applications/{applicationId}/stage-results`.
  - `score`, `comment`, `decidedBy`, `correctedBy`, correction reason/history, and storage fields remain hidden.
- Phase 03h-4 implementation recommendation:
  - Add dashboard response DTO records.
  - Add `ApplicationDashboardService` or a clearly scoped read method.
  - Add `ApplicationCompletionReadChecker`.
  - Add `GET /applications/{applicationId:[0-9]+}/dashboard`.
  - Add service/controller tests.
- References:
  - `docs/codex/design/phase-03h-3-applicant-application-dashboard-design.md`
  - `docs/codex/reports/phase-03h-3-applicant-application-dashboard-design.html`

## Phase 03h-2 Applicant My Applications Implementation Note

- Phase 03h-2 implemented the applicant my applications list API: `GET /applications/me`.
- Response wrapper: `ApiResponse<PageResponse<MyApplicationResponse>>`.
- Query parameters:
  - `page`, default `0`
  - `size`, default `20`
- Page validation:
  - `page < 0` fails.
  - `size <= 0` fails.
  - `size > 100` fails.
- Default sort is fixed in the service as `createdAt DESC, id DESC`.
- The endpoint uses `CurrentApplicantService`; `applicantId` is not accepted as a query parameter.
- `/applications/me` remains under the existing `/applications/**` `ROLE_APPLICANT` security policy.
- Employee/admin users receive 403 through Spring Security when security filters are active.
- Anonymous users receive 401 JSON through the existing security exception handlers.
- Included application statuses:
  - `DRAFT`
  - `SUBMITTED`
  - `WITHDRAWN`
- Existing applications remain listed even when the posting is `CLOSED`.
- `accepting` is calculated from `JobPosting.status == PUBLISHED` and the reception period using the injected `Clock`.
- Result summary:
  - Uses one batch StageResult query for the current page's application ids.
  - Includes only stages with `Stage.status == RESULT_ANNOUNCED || CLOSED`.
  - Excludes `READY` and `IN_PROGRESS`.
  - Does not synthesize missing StageResult rows.
  - Latest summary uses `stageOrder DESC, stage.id DESC`.
- `MyApplicationResponse` excludes applicant personal data, `stageResultId`, `score`, `comment`, `decidedBy`, `correctedBy`, correction reason/history, and storage fields.
- Existing detailed applicant result API `GET /applications/{applicationId}/stage-results` is unchanged.
- `ApplicationController` numeric id mappings were narrowed to avoid `/applications/me` colliding with `{applicationId}` command paths.
- Tests passed:
  - `JobApplicationServiceTest`
  - `ApplicationControllerTest`
  - `ApplicationStageResultServiceTest` + `ApplicationStageResultControllerTest`
  - `StageResultServiceTest` + `StageResultCorrectionServiceTest`
  - `.\gradlew.bat clean test --no-daemon`
- Reference:
  - `docs/codex/implementation/phase-03h-2-applicant-my-applications.md`
  - `docs/codex/reports/phase-03h-2-applicant-my-applications.html`

## Phase 03h-1 Applicant My Applications Design Note

- Phase 03h-1 is a design-only phase for the applicant my applications list API.
- Candidate API: `GET /applications/me`.
- No Java source, test source, `SecurityConfig`, build, YAML, DB schema, Repository, Service, Controller, DTO, or existing API behavior is changed in this phase.
- Recommended response wrapper: `ApiResponse<PageResponse<MyApplicationResponse>>`.
- Access policy:
  - `/applications/me` is covered by the existing `/applications/**` applicant policy.
  - Only `ROLE_APPLICANT` can access the endpoint.
  - Employee/admin users receive 403 and must use admin APIs.
  - The current applicant id must come from `CurrentApplicantService`; the request must not accept `applicantId`.
- Pagination and sorting:
  - `page` and `size` query parameters are recommended for Phase 03h-2.
  - Default sort is `createdAt DESC, id DESC`.
- Application inclusion policy:
  - `DRAFT`, `SUBMITTED`, and `WITHDRAWN` applications are all included.
  - Existing applications remain listed even when the `JobPosting` is `CLOSED`.
- Recommended response fields:
  - `applicationId`, `jobPostingId`, `jobPostingTitle`, `jobPostingStatus`
  - `jobPositionId`, `jobPositionName`, `applicationStatus`
  - `createdAt`, `submittedAt`, `withdrawnAt`
  - `receptionStartDateTime`, `receptionEndDateTime`, `accepting`
  - `announcedResultCount`, `latestAnnouncedStageName`, `latestResultStatus`
- Result summary policy:
  - Detailed results remain in `GET /applications/{applicationId}/stage-results`.
  - `/applications/me` includes only count and latest visible announced result summary.
  - Visible result summary uses `Stage.status == RESULT_ANNOUNCED || CLOSED`.
  - `READY` and `IN_PROGRESS` stage results are excluded.
  - `score`, `comment`, `decidedBy`, `correctedBy`, correction reason, and correction history remain hidden.
- Phase 03h-2 implementation recommendation:
  - Extend existing `JobApplicationService`.
  - Add an applicant-owned pageable list query to `JobApplicationRepository`.
  - Add a batch visible StageResult summary query to avoid N+1 result loading.
  - Add service/controller tests for ownership, status inclusion, sorting, `accepting`, result summary, and 401/403 behavior.
- Reference:
  - `docs/codex/design/phase-03h-applicant-my-applications-design.md`
  - `docs/codex/reports/phase-03h-applicant-my-applications-design.html`

## Phase 03e-4 Security Exception JSON Response Implementation Note

- Phase 03e-4 added Spring Security 401/403 JSON response handlers.
- `CustomAuthenticationEntryPoint` handles unauthenticated requests before controller invocation and returns `401 + ApiResponse.fail("Authentication is required.")`.
- `CustomAccessDeniedHandler` handles authenticated principals without required authority and returns `403 + ApiResponse.fail("Access is denied.")`.
- `SecurityConfig` now registers both handlers through `exceptionHandling(...)`.
- Phase 03e-3 URL authorization rules are unchanged:
  - `/admin/**` requires `ROLE_ADMIN` or `ROLE_RECRUIT_ADMIN`.
  - `/applications/**` requires `ROLE_APPLICANT`.
  - `GET /job-postings/{jobPostingId}/application` requires `ROLE_APPLICANT`.
  - public job posting reads remain public.
  - fallback remains `anyRequest().permitAll()`.
- Business validation and not-found responses still flow through `GlobalExceptionHandler`.
- Applicant result responses still do not expose `stageResultId`, `score`, `comment`, `decidedBy`, `correctedBy`, or correction history.
- Targeted security controller tests passed:
  - `ApplicationStageResultControllerTest`
  - `StageResultControllerTest`
  - `StageControllerTest`
- StageResult service regression tests passed:
  - `StageResultServiceTest`
  - `StageResultCorrectionServiceTest`
- Full `clean test --no-daemon` was attempted twice and timed out without a completed success result.
- Next recommendation: classify remaining API families before replacing the fallback `permitAll` rule.

## Phase 03e-2 StageResult Actor Propagation Implementation Note

- Phase 03e-2 added `CurrentEmployeeService` for admin-side StageResult command actor resolution.
- The resolver accepts `CustomUserDetails`, requires `userType == Employee`, and returns `getUsername()` as the actor.
- Null principals, applicant principals, and blank usernames fail with `InvalidStageResultException`.
- No Employee DB lookup, Employee FK, `CurrentAdminService`, or `SecurityConfig` change was added.
- Admin StageResult update, bulk update, and correction commands now receive an actor from the controller.
- `StageResult.decidedBy` and `StageResultCorrectionHistory.correctedBy` now store the employee login id instead of `"SYSTEM"`.
- Applicant result read remains unchanged.
- `ApplicantStageResultResponse` still exposes only stage/result display fields and does not expose `decidedBy`, `correctedBy`, score, comment, or correction history.
- Next recommendation: Phase 03e-3 should enforce URL authorization for `/admin/**` and `/applications/**`.

## Phase 03e-1 Admin/Auth Hardening Design Note

- Phase 03e-1 is a design-only phase for production-oriented authentication and authorization hardening.
- No Java source, test source, `SecurityConfig`, build, YAML, or schema file is changed in this phase.
- Current issue: `SecurityConfig` still permits all requests, so `/admin/**` and `/applications/**` rely mainly on controller/service checks during development.
- Recommended applicant API policy:
  - `/applications/**` requires authentication.
  - `/applications/**` allows only `ROLE_APPLICANT`.
  - Employee/admin users receive 403 on applicant APIs.
  - Service-level applicant ownership checks remain mandatory.
  - Access to another applicant's application continues to use the existing 404 hiding policy.
- Recommended admin API policy:
  - `/admin/**` requires authenticated employee/admin authority.
  - Applicant users receive 403.
  - Unauthenticated users receive 401.
  - Actual `DeptRoleMapping.roleName` values must be confirmed before choosing `hasRole` or `hasAuthority`.
- Current-user resolver recommendation:
  - Keep `CurrentApplicantService`.
  - Add `CurrentEmployeeService` in Phase 03e-2.
  - Add `CurrentAdminService` only if admin authority checks need a dedicated abstraction.
- Security exception policy:
  - authentication failure: `401 + ApiResponse.fail`
  - authorization failure: `403 + ApiResponse.fail`
  - use Spring Security `AuthenticationEntryPoint` and `AccessDeniedHandler`, not only `GlobalExceptionHandler`.
- Recommended implementation split:
  - Phase 03e-2: current employee/admin identity resolver and StageResult actor propagation.
  - Phase 03e-3: `/admin/**` and `/applications/**` URL authorization.
  - Phase 03e-4: JSON 401/403 response handlers and tests.
- Reference:
  - `docs/codex/design/phase-03e-admin-auth-hardening-design.md`
  - `docs/codex/reports/phase-03e-admin-auth-hardening-design.html`

## Phase 03d-5 Result Correction History Implementation Note

- Phase 03d-5 added admin-only post-announcement StageResult correction.
- Added APIs:
  - `POST /admin/stages/{stageId}/results/{resultId}/correct`
  - `GET /admin/stages/{stageId}/results/{resultId}/histories`
- Added `StageResultCorrectionHistory` as an append-only history entity.
- The correction service finds a result by `resultId + stageId`; a mismatch is treated as `StageResultNotFoundException`.
- Correction is allowed only after announcement: `Stage.status` must be `RESULT_ANNOUNCED` or `CLOSED`.
- `READY` and `IN_PROGRESS` stages cannot use correction; `IN_PROGRESS` still uses the existing general result update API.
- Correction updates the latest `StageResult` row and stores previous/new snapshots for status, score, comment, and decidedAt.
- Correction reason is mandatory and limited to 1000 characters.
- `correctedBy` and `decidedBy` still use `SYSTEM` until the real admin identity source is connected.
- Applicant-facing result read remains unchanged and exposes only the latest corrected result.
- Applicant responses do not expose correction history, score, comment, or decidedBy.
- Message/notification, SecurityConfig changes, fine-grained authorization, and interview/evaluation aggregation remain deferred.

## Phase 03d-4 Applicant StageResult Read Implementation Note

- Phase 03d-4 added applicant-facing result read API: `GET /applications/{applicationId}/stage-results`.
- The controller uses the existing applicant API pattern through `CurrentApplicantService`.
- The service receives `applicantId` and `applicationId` and loads `JobApplication` by both values.
- Other applicant's application remains hidden through the existing not-found policy.
- `DRAFT` applications fail because they are not submitted result targets.
- `SUBMITTED` and `WITHDRAWN` applications can read visible results.
- Visible results are existing `StageResult` rows whose `Stage.status` is `RESULT_ANNOUNCED` or `CLOSED`.
- `READY` and `IN_PROGRESS` stages are not returned, including "announcement pending" rows.
- Missing StageResult rows are not synthesized as null rows.
- The response DTO is `ApplicantStageResultResponse`; `AdminApplicationStageResultResponse` is not reused.
- Applicant response excludes `stageResultId`, `score`, `comment`, `decidedBy`, audit fields, and correction history.
- `resultAnnouncementDateTime` is returned as display data only; scheduled release guard is not implemented.
- Phase 03d-5 correction/history remains deferred.

## Phase 03d-4/03d-5 Result Read and Correction Design Note

- Phase 03d-4 and Phase 03d-5 were split as design-only follow-up work after the admin StageResult timeline.
- Phase 03d-4 candidate API is `GET /applications/{applicationId}/stage-results`.
- Applicant result read must validate applicant ownership and must not reuse `AdminApplicationStageResultResponse`.
- `DRAFT` applications are not applicant result-read targets.
- `SUBMITTED` and `WITHDRAWN` applications can read visible results.
- Applicant-visible stages are limited to `Stage.status == RESULT_ANNOUNCED || CLOSED`.
- `READY` and `IN_PROGRESS` stages are not returned to applicants, even as "announcement pending" rows.
- Applicant response is limited to stage display fields and latest result status/time data.
- Applicant response must not expose `score`, `comment`, `decidedBy`, or correction history.
- Phase 03d-5 candidate APIs are `POST /admin/stages/{stageId}/results/{resultId}/correct` and `GET /admin/stages/{stageId}/results/{resultId}/histories`.
- Result correction requires a reason and should persist append-only `StageResultCorrectionHistory`.
- Applicant-facing result read shows only the latest corrected result.
- Reference design: `docs/codex/design/phase-03d-4-5-result-read-correction-design.md`.

## Phase 03d-3 Admin Application StageResult Timeline Note

- Phase 03d-3 added the admin application detail lazy API: `GET /admin/applications/{applicationId}/stage-results`.
- The API is attached to the existing `AdminApplicationSectionController` and does not change the admin application root detail response.
- Timeline rows are based on the application's `JobPosting` stages, sorted by `stageOrder ASC, id ASC`.
- Existing `StageResult` data is merged by `Stage.id`; stages without result rows still appear with null result fields.
- `DRAFT`, `SUBMITTED`, and `WITHDRAWN` applications are all readable in this admin path.
- `decidedBy` is not exposed.
- `comment` is admin-visible only and must not be reused in applicant-facing result responses by default.
- Applicant-facing result read, result correction/history, message/notification, and read audit logging remain deferred.

## Phase 03d-0 StageResult Design Note

- Phase 03d-0 designed `StageResult` after `JobApplication`, application detail sections, and question/answer read flows became available.
- `StageResult` is defined as the N:M connection result record for `Stage + JobApplication`.
- The recommended relationship is unidirectional `StageResult -> Stage` and `StageResult -> JobApplication`.
- `Stage` and `JobApplication` should not receive `StageResult` collections in the initial implementation.
- The recommended unique candidate is `stage_id + job_application_id`.
- Initial creation should use an explicit initialize command that creates missing `PENDING` results for `SUBMITTED` applications.
- `DRAFT` applications are excluded, and applications already withdrawn before initialize are excluded.
- This design-only phase did not change Java code, Entity, Repository, Service, Controller, DTO, Test, DB schema, SecurityConfig, or existing APIs.
- Phase 03d-1 implemented the StageResult Entity + initialize/list admin API.
- Next implementation recommendation: Phase 03d-2 StageResult update commands and Stage announce pending-result guard.

## Phase 03c-9-4 Implementation Note

- Phase 03c-9-4 added the admin answer lazy read API: `GET /admin/applications/{applicationId}/answers`.
- The API is attached to the existing admin application section read flow.
- Admin answer rows are based on active `JobPostingQuestion` rows and include current `ApplicationAnswer` data when present.
- Unanswered active questions are still returned so the admin detail screen can render the full question list.
- `answerText` is exposed only in this admin detail lazy API. It must not be added to admin list, search, statistics, or root detail responses by default.
- Inactive question answers and active-question-external orphan answers are excluded until revision/history policy is finalized.
- Fine-grained original-text permission, masking, and read audit logs remain security-phase TODOs.
- Future refactoring candidate: move duplicated answer type length limits into a shared `QuestionAnswerPolicy` when answer types expand.

## Phase 03c-9-3 Implementation Note

- Phase 03c-9-3 connected question/answer final-submit validation to `ApplicationSubmitValidator`.
- Submit now checks active `JobPostingQuestion` rows for the application's `JobPosting`.
- `required=true` active questions require a matching `ApplicationAnswer` row with non-null, non-blank `answerText`.
- Existing optional question behavior is preserved: `required=false` questions may be unanswered or blank at submit.
- Answer length is revalidated at submit against `JobPostingQuestion.maxLength`; when maxLength is unavailable, the answer type default is used.
- Answer type hard limits are enforced defensively: `SHORT_TEXT <= 500`, `LONG_TEXT <= 5000`.
- `minLength` is still stored but not enforced by submit validation in this phase.
- Inactive questions and answers outside the active question set are ignored by submit validation.
- Phase 03c-9-4 later added the admin answer lazy read API. Remaining follow-up work is answer original-text authorization, masking, read audit logging, export policy, or StageResult sequencing.

## Phase 03c-9-2 Implementation Note

- Phase 03c-9-2 added `ApplicationAnswer` as the applicant-side answer record under `JobApplication`.
- Applicant APIs were added:
  - `GET /applications/{applicationId}/questions`
  - `POST /applications/{applicationId}/answers`
- The question list is based on active `JobPostingQuestion` rows for the application's `JobPosting`, sorted by `sortOrder ASC, id ASC`.
- Answer replace save is allowed only while the application is writable through the existing detail-section policy: applicant ownership, `DRAFT`, `JobPosting.status=PUBLISHED`, and reception period.
- Saved answers snapshot `JobPostingQuestion` fields at answer save time: question text, category, answer type, required flag, min/max length, and sort order.
- Required blank-answer validation is now handled by Phase 03c-9-3 submit validator integration. DRAFT save still allows null/blank answers but blocks length violations.
- Phase 03c-9-4 later added the admin answer read API. Choice option domain, file answer type, Attachment linkage, and answer text security policy remain deferred.

## Phase 03c-9-1 구현 반영 메모

- Phase 03c-9-1에서 자기소개서/질문답변 도메인의 첫 구현 단계로 `QuestionTemplate`과 `JobPostingQuestion` 관리자 API를 추가했다.
- `QuestionTemplate`은 전역 질문 은행이며, `JobPostingQuestion`은 특정 `JobPosting`에 배치된 질문 snapshot record이다.
- `QuestionCategory`는 `SELF_INTRODUCTION`, `GENERAL`, `JOB_SPECIFIC`, `ETC`로 시작하고, `QuestionAnswerType`은 `SHORT_TEXT`, `LONG_TEXT`만 구현했다.
- `JobPostingQuestion.questionTemplate`은 nullable 참조이며, 템플릿 기반 생성 시 템플릿 값을 기본 복사하되 요청 override를 최종 snapshot에 반영한다.
- 공고별 질문 구성 변경은 `JobPosting.status=DRAFT`에서만 허용한다. `PUBLISHED`/`CLOSED` 이후에는 생성, 수정, 정렬, 비활성화 command를 차단한다.
- 질문 삭제는 HTTP DELETE나 물리 삭제가 아니라 `POST /admin/job-postings/{jobPostingId}/questions/{questionId}/delete` command로 `active=false` 처리한다.
- 지원자 답변 저장, `ApplicationAnswer`, `ApplicationSubmitValidator` 질문답변 연동, 관리자 답변 조회 API는 구현하지 않았다.
- Phase 03c-9-2, Phase 03c-9-3, and Phase 03c-9-4 are now complete. The current next recommendation is answer text authorization/audit/masking policy or StageResult sequencing.

## Phase 03c-9 설계 반영 메모

- Phase 03c-9에서 `JobApplication` 하위 자기소개서/질문답변 도메인을 구현하기 전 설계를 정리했다.
- 추천 구조는 `QuestionTemplate` + `JobPostingQuestion` + `ApplicationAnswer`이다.
- `QuestionTemplate`은 전역 질문 은행이며, `JobPostingQuestion`은 특정 공고에 실제 배치된 질문 snapshot record로 둔다.
- `JobPostingQuestion.questionTemplate`은 nullable로 두어 템플릿 기반 질문과 직접 작성 질문을 모두 지원한다.
- `ApplicationAnswer`는 지원서별 답변 record이며, `job_application_id + job_posting_question_id` unique 후보를 둔다.
- 자기소개서는 별도 Entity가 아니라 `QuestionCategory.SELF_INTRODUCTION` 카테고리의 질문답변으로 다룬다.
- 초기 답변 타입은 `SHORT_TEXT`, `LONG_TEXT` 중심으로 시작하고 선택형/파일형 답변은 후속 Phase로 보류한다.
- 공고 질문 구성은 `JobPosting.status=DRAFT`에서만 수정 허용하고, `PUBLISHED` 이후 수정은 revision/reopen 정책 확정 전까지 금지하는 방향을 추천한다.
- 지원자 답변 저장은 `DRAFT` 상태에서만 허용하며, required 미입력은 DRAFT 저장에서는 허용하되 submit 시 `ApplicationSubmitValidator`에서 실패시키는 방향으로 설계했다.
- 관리자 답변 조회는 Phase 03c-8 lazy section API 흐름에 맞춰 `GET /admin/applications/{applicationId}/answers` 후보로 둔다.
- 이번 Phase는 설계 문서 작업만 수행했고 Java 코드, DB schema, 기존 API, `ApplicationSubmitValidator`, 관리자 상세 섹션 API는 변경하지 않았다.
- Phase 03c-9-1 through Phase 03c-9-4 are now complete. The current next recommendation is answer text authorization/audit/masking policy or StageResult sequencing.

## Phase 03c-8 구현 반영 메모

- Phase 03c-8에서 Phase 03b-1의 관리자 Application 루트 목록/상세 조회 구조를 유지한 채, 상세 섹션별 lazy read-only API를 추가했다.
- 추가 API는 `GET /admin/applications/{applicationId}/educations`, `/careers`, `/certificates`, `/languages`, `/military`, `/awards`, `/gap-periods`, `/attachments`이다.
- `/admin/applications/{applicationId}` 루트 상세 응답 구조는 변경하지 않았고, `/admin/applications/{applicationId}/details` 같은 aggregate API도 만들지 않았다.
- 관리자 상세 섹션 조회는 `AdminApplicationSectionService`가 담당하며, 지원자용 상세 섹션 저장 Service를 재사용하지 않는다.
- 관리자 조회는 `applicationId` 존재 여부만 확인하며, 지원자 소유자 검증, DRAFT/PUBLISHED/접수기간 검증, submit validator 검증을 적용하지 않는다.
- 관리자 응답 DTO는 지원자용 DTO를 재사용하지 않고 `AdminEducationResponse`, `AdminCareerResponse`, `AdminCertificateResponse`, `AdminMilitaryResponse` 등으로 분리했다.
- 자격번호는 `certificateNumberMasked`, 병역 면제 사유는 `exemptionReasonMasked`만 응답하고 원문 필드는 노출하지 않는다.
- Attachment 관리자 응답도 지원자 응답과 동일하게 `storedFileName`, `storagePath`, 다운로드 URL을 노출하지 않는다.
- 이번 Phase에서는 관리자 수정/삭제 command, 파일 업로드/다운로드, StageResult, 자기소개서/질문답변, 보안 권한 세분화는 구현하지 않았다.

## Phase 03c-7 구현 반영 메모

- Phase 03c-7에서 `ApplicationSubmitValidator`를 추가하고 `JobApplicationService.submit()`에 연결했다.
- 기존 submit 검증인 본인 지원서 조회, `DRAFT`, PUBLISHED 공고, 접수기간, `ApplicationFormConfig` 존재, 모집분야 소속 검증은 유지한다.
- submit 상태 변경 직전에 validator를 호출하며, 실패하면 `InvalidJobApplicationException`으로 400 응답 정책을 유지하고 `submittedAt`은 세팅하지 않는다.
- `useEducation=true`이면 `ApplicationEducation` 최소 1건을 요구한다.
- `useCareer=true`이면 `ApplicationCareerProfile`과 유효한 `CareerType`을 요구한다. `EXPERIENCED`는 Career row 최소 1건이 필요하고, `NEWCOMER`/`NOT_APPLICABLE`은 Career row가 있으면 실패한다.
- `useMilitary=true`이면 `ApplicationMilitary` 1건을 요구한다. `COMPLETED`는 복무기간, `EXEMPTED`는 면제 사유를 최종제출 시점에 요구한다.
- Certificate, Language, Award, GapPeriod는 현재 선택 섹션으로 보고 최소 row를 강제하지 않는다.
- Attachment는 `ApplicationFormConfig` flag가 없어 이번 Phase에서 제출 필수 검증을 하지 않는다.
- 신규 API는 없고 `POST /applications/{applicationId}/submit` 내부 검증만 강화했다.
- 관리자 상세 섹션 API, StageResult, 자기소개서/질문답변, 파일 업로드/다운로드는 구현하지 않았다.

## Phase 03c-6 구현 반영 메모

- Phase 03c-6에서 `JobApplication` 하위 첨부파일 metadata vertical slice를 구현했다.
- 추가 도메인은 `ApplicationAttachment`이며, `JobApplication`에는 Attachment 컬렉션을 추가하지 않았다.
- 첨부 유형은 `AttachmentType`, 귀속 섹션 힌트는 `ApplicationSectionType` enum으로 시작한다.
- 지원자 API는 `GET /applications/{applicationId}/attachments`, `POST /applications/{applicationId}/attachments`이다.
- 실제 multipart 파일 업로드, 다운로드, 저장소 연동은 구현하지 않았다.
- `storedFileName`, `storagePath`는 내부 관리 필드로 저장하되 응답에는 노출하지 않는다.
- Attachment는 현재 `ApplicationFormConfig`에 flag가 없으므로 `DRAFT`, PUBLISHED 공고, 접수기간 내 조건만 검증한다.
- submit 통합 검증과 관리자 상세 섹션 API는 아직 연결하지 않았다.

## Phase 03c-5 구현 반영 메모

- Phase 03c-5에서 `JobApplication` 하위 수상/포상사항과 공백기간 vertical slice를 구현했다.
- 추가 도메인은 `ApplicationAward`, `ApplicationGapPeriod`이며, `JobApplication`에는 Award/GapPeriod 컬렉션을 추가하지 않았다.
- `GapType`은 `EDUCATION`, `CAREER`, `OTHER`로 시작한다.
- 지원자 API는 `GET /applications/{applicationId}/awards`, `POST /applications/{applicationId}/awards`, `GET /applications/{applicationId}/gap-periods`, `POST /applications/{applicationId}/gap-periods`이다.
- 저장은 본인 지원서, `DRAFT`, `JobPosting.status=PUBLISHED`, 접수기간 내 조건을 모두 만족해야 한다.
- Award 저장은 `ApplicationFormConfig.useAward=true`, GapPeriod 저장은 `ApplicationFormConfig.useGapPeriod=true`일 때만 가능하다.
- replace 저장은 기존 row를 `applicationId` 기준 명시 삭제한 뒤 새 row를 저장한다.
- GapPeriod는 `startDate <= endDate`를 검증하며, overlap 검증은 아직 하지 않는다.
- `ApplicationSectionAccessService`는 `validateAwardEnabled`, `validateGapPeriodEnabled`까지 확장되었다.
- submit 통합 검증과 관리자 상세 섹션 API는 아직 연결하지 않았다.

## Phase 03c-4R 구현 반영 메모

- Phase 03c-4R에서 상세 섹션 공통 접근/수정 가능 검증 helper `ApplicationSectionAccessService`를 추가했다.
- helper 범위는 `findOwnedApplication`, `validateWritable`, `validateEducationEnabled`, `validateCareerEnabled`, `validateCertificateEnabled`, `validateLanguageEnabled`, `validateMilitaryEnabled`로 제한했다.
- `SectionType` enum 기반 일반화는 도입하지 않았다.
- Education, Career, Certificate, Language, Military Service는 본인 지원서 조회, DRAFT/PUBLISHED/접수기간, config enabled 검증을 helper에 위임한다.
- 기존 상세 섹션 API path, 저장 정책, 응답 DTO, submit validator 보류 정책은 변경하지 않았다.
- 다음 Award + GapPeriod 구현은 이 helper를 재사용하는 방향으로 진행한다.

## Phase 03c-4 구현 반영 메모

- Phase 03c-4에서 `JobApplication` 하위 병역사항 vertical slice를 구현했다.
- 추가 도메인은 `ApplicationMilitary`이며, `JobApplication`에는 Military 필드를 추가하지 않았다.
- 병역은 다건 목록이 아니라 `job_application_id` unique를 가진 단건 record로 구현했다.
- 병역 저장은 `ApplicationFormConfig.useMilitary=true`일 때만 허용한다.
- 지원자 API는 `GET /applications/{applicationId}/military`, `POST /applications/{applicationId}/military`이다.
- 조회는 본인 지원서라면 `DRAFT`, `SUBMITTED`, `WITHDRAWN` 모두 허용하며, 아직 저장 전이면 `data=null`로 응답한다.
- 저장은 `DRAFT`, `JobPosting.status=PUBLISHED`, 접수기간 내에서만 허용한다.
- `MilitarySubjectType`은 `SUBJECT`, `NOT_SUBJECT`, `COMPLETED`, `EXEMPTED`, `NOT_APPLICABLE`로 시작한다.
- `SUBJECT`, `NOT_SUBJECT`, `NOT_APPLICABLE`은 상세 병역 필드를 허용하지 않는다.
- `COMPLETED`는 복무 상세 필드를 허용하되 면제 사유는 허용하지 않고, `EXEMPTED`는 면제 사유를 허용하되 복무 상세 필드는 허용하지 않는다.
- submit 통합 검증은 아직 연결하지 않았고, `useMilitary=true`이면 `ApplicationMilitary` 1건 필수 검증은 Phase 03c-7에서 구현한다.
- Education/Career/Certificate/Language/Military에서 반복되던 지원서 소유자, DRAFT 수정 가능, PUBLISHED 공고, 접수기간, config enabled 검증은 Phase 03c-4R에서 `ApplicationSectionAccessService`로 최소 추출했다.

## Phase 03c-3 구현 반영 메모

- Phase 03c-3에서 `JobApplication` 하위 자격사항/어학사항 vertical slice를 구현했다.
- 추가 도메인은 `ApplicationCertificate`, `ApplicationLanguage`이며, `JobApplication`에는 Certificate/Language 컬렉션을 추가하지 않았다.
- 자격 저장은 `ApplicationFormConfig.useCertificate=true`, 어학 저장은 `ApplicationFormConfig.useLanguage=true`일 때만 허용한다.
- 저장은 본인 지원서, `DRAFT` 상태, `JobPosting.status=PUBLISHED`, 접수기간 내 조건을 모두 만족해야 한다.
- replace 저장은 기존 Certificate/Language row를 `applicationId` 기준 명시 삭제한 뒤 새 row를 저장한다.
- Certificate는 `expiredDate`가 있으면 `acquiredDate <= expiredDate`, Language는 `expiredDate`가 있으면 `examDate <= expiredDate`를 검증한다.
- Language의 `score`, `grade`는 DRAFT 저장에서는 둘 다 비어 있어도 허용하고, submit 필수 여부는 Phase 03c-7에서 재검토한다.
- API는 `GET /applications/{applicationId}/certificates`, `POST /applications/{applicationId}/certificates`, `GET /applications/{applicationId}/languages`, `POST /applications/{applicationId}/languages`이다.
- submit 시 Certificate/Language 최소 row 필수 여부와 관리자 상세 섹션 응답은 아직 연결하지 않았다.
- Education/Career/Certificate/Language에서 지원서 소유자, DRAFT 수정 가능, PUBLISHED 공고, 접수기간, config enabled 검증이 반복되고 있다. Military 구현 후 `ApplicationSectionAccessService` 같은 최소 helper 추출을 검토한다.
- Certificate/Language의 일부 자유 입력 문자열 길이 제한은 아직 두지 않았고, 운영 DB schema 확정 시 `@Column(length = ...)` 또는 DTO `@Size`를 검토한다.

## Phase 03c-2 구현 반영 메모

- Phase 03c-2에서 `JobApplication` 하위 경력사항 vertical slice를 구현했다.
- 추가 도메인은 `ApplicationCareerProfile`, `ApplicationCareer`이며, `JobApplication`에는 Career 컬렉션이나 단건 필드를 추가하지 않았다.
- `CareerType`은 `NOT_SELECTED`, `NEWCOMER`, `EXPERIENCED`, `NOT_APPLICABLE`로 구성한다.
- `ApplicationCareerProfile`은 지원서별 경력 선택 상태를 저장하는 단건 record이고, `ApplicationCareer`는 `EXPERIENCED`일 때 사용하는 경력 row 목록이다.
- 저장은 본인 지원서, `DRAFT` 상태, `JobPosting.status=PUBLISHED`, 접수기간 내, `ApplicationFormConfig.useCareer=true` 조건을 모두 만족해야 한다.
- replace 저장은 profile을 upsert하고 기존 Career row를 `applicationId` 기준 명시 삭제한 뒤 새 Career row를 저장한다.
- `EXPERIENCED`가 아닌 `CareerType`은 Career row를 허용하지 않는다. `EXPERIENCED`는 DRAFT 저장에서 빈 목록도 허용한다.
- submit 시 `CareerType.NOT_SELECTED` 실패 여부와 `EXPERIENCED` 최소 1개 검증은 아직 연결하지 않았고, Phase 03c-7 `ApplicationSubmitValidator`에서 처리한다.
- API는 `GET /applications/{applicationId}/careers`, `POST /applications/{applicationId}/careers`이다.

## Phase 03c-1 구현 반영 메모

- Phase 03c-1에서 `JobApplication` 하위 학력/성적 vertical slice를 구현했다.
- 추가 도메인은 `ApplicationEducation`, `ApplicationEducationSemesterGrade`이며, `JobApplication`에는 상세 섹션 컬렉션을 추가하지 않았다.
- 학력/성적 저장은 `POST /applications/{applicationId}/educations` replace 방식으로 제공한다.
- 조회는 `GET /applications/{applicationId}/educations`로 제공한다.
- 저장은 본인 지원서, `DRAFT` 상태, `JobPosting.status=PUBLISHED`, 접수기간 내, `ApplicationFormConfig.useEducation=true` 조건을 모두 만족해야 한다.
- replace 저장은 기존 SemesterGrade를 먼저 삭제한 뒤 기존 Education을 삭제하고 새 요청 목록을 저장한다.
- `EducationLevel.HIGH_SCHOOL`에는 SemesterGrade 입력을 허용하지 않는다.
- `admissionDate`와 `graduationDate`가 모두 있으면 입학일이 졸업일보다 늦을 수 없다.
- submit 시 Education 최소 1개 필수 검증은 아직 연결하지 않았고, Phase 03c-7 `ApplicationSubmitValidator`에서 처리한다.
- 잘못된 enum 값 바인딩은 `HttpMessageNotReadableException` 공통 처리로 `ApiResponse.fail` 400 응답을 반환한다.
- 성적/학점 `BigDecimal` precision/scale 지정과 상세 섹션 공통 검증 helper 추출은 다음 섹션 구현 전 다시 검토한다.

## Phase 03c-0 설계 반영 메모

- Phase 03c-0에서 `JobApplication` 하위 상세 섹션 도메인 설계 문서 `docs/codex/design/phase-03c-application-detail-design.md`를 추가했다.
- 상세 섹션은 기본적으로 `JobApplication` 하위 application-specific record로 둔다.
- 이름, 휴대폰번호, 주소, CI 같은 기본 개인정보의 원천은 `Applicant`/`User` 계층에 두고, `JobApplication`에는 필요한 최소 snapshot만 둔다.
- 학력, 학기별 성적, 경력, 자격, 어학, 병역, 수상, 공백기간, 첨부파일 metadata를 Phase 03c 상세 섹션 후보로 정리했다.
- `ApplicationFormConfig.useXxx=false`이면 지원자 화면 노출과 저장을 차단하고, 최종제출 필수 검증 대상에서도 제외하는 방향을 추천한다.
- 상세 섹션 수정은 `DRAFT` 상태에서만 허용하고, `SUBMITTED`/`WITHDRAWN`은 조회만 허용하는 정책을 추천한다.
- 최종제출 상세 검증은 후속 Phase에서 `ApplicationSubmitValidator`와 섹션별 validator로 분리해 `JobApplicationService.submit()`에 연결한다.
- 관리자 상세 조회는 현재 루트 정보 응답을 유지하고, 상세 섹션은 섹션별 lazy 조회 API로 확장하는 방식을 추천한다.
- 리뷰 반영으로 replace 저장은 기존 row 명시 삭제 후 새 row `saveAll`을 하나의 transaction에서 처리하는 정책으로 구체화했다.
- Education replace 시에는 기존 SemesterGrade를 먼저 삭제한 뒤 Education을 삭제해야 한다.
- Phase 03c 초기 상세 섹션 code 값은 Java enum으로 시작하고, CommonCode 전환은 후속 Phase에서 검토한다.
- `useMilitary=true`이면 submit 시 `ApplicationMilitary` 1건을 필수로 두는 방향으로 정리했다.
- 다음 구현은 공통 helper만 별도 구현하기보다 Education + SemesterGrade vertical slice 안에서 최소 helper를 함께 도입하는 방향을 추천한다.

## Phase 03b-1 구현 반영 메모

- Phase 03b-1에서 관리자 Application 루트 목록/상세 조회 API를 구현했다.
- 추가 API는 `GET /admin/applications`, `GET /admin/applications/{applicationId}`, `GET /admin/job-postings/{jobPostingId}/applications`이다.
- 관리자 응답 DTO는 지원자용 `ApplicationDetailResponse`를 재사용하지 않고 `AdminApplicationSummaryResponse`, `AdminApplicationDetailResponse`로 분리했다.
- 목록 조회는 `jobPostingId`, `jobPositionId`, `status` 필터와 `page`, `size` 페이징을 지원한다.
- `size` 최대값은 100이며 기본 정렬은 `createdAt DESC, id DESC`이다.
- 관리자 목록/상세 조회는 `Applicant`, `JobPosting`, `JobPosition` to-one 연관만 `@EntityGraph`로 조회하며 collection fetch는 사용하지 않는다.
- 관리자 조회 조건 객체 `AdminApplicationSearchCondition`은 Controller request DTO가 아니므로 `dto.condition`에 둔다.
- 관리자 status 필터는 앞뒤 공백을 제거하고 대소문자를 구분하지 않도록 정규화한다.
- 응답에는 Application 루트 snapshot과 상태/시각 정보만 포함하고, CI, ciHash, password, phoneNumber, address, email, 암호화 원문 개인정보는 포함하지 않는다.
- 관리자 수정/삭제 command, StageResult, 상세 섹션 도메인은 계속 보류 상태다.
- 실제 관리자 권한 검증은 아직 SecurityConfig에 추가하지 않았으며, 운영 전 `/admin/applications/**`를 관리자 또는 채용담당자 권한으로 보호해야 한다.

## Phase 03a-3 구현 반영 메모

- Phase 03a-3에서 `ApplicationController`가 추가되어 지원자 Application 생성/조회/수정/제출/철회 HTTP API가 연결되었다.
- `GET /applications/me` 목록 API는 이번 Phase에서 구현하지 않고 별도 Phase로 유지했다.
- 현재 `CustomUserDetails`에는 `userId/applicantId`가 없으므로 `CurrentApplicantService`가 `userType=Applicant`를 확인하고 `loginId`로 `ApplicantRepository.findByLoginId`를 조회한다.
- `CustomUserDetails.USER_TYPE_APPLICANT/USER_TYPE_EMPLOYEE` 상수를 추가해 문자열 비교를 한 곳에 모았고, `getUsername() == loginId` 전제는 `CustomUserDetailsTest`로 고정했다.
- 미로그인 401, Employee/Admin 403, 실제 SecurityFilterChain/CSRF 검증은 아직 별도 보안 보강 Phase로 남긴다.
- SecurityConfig는 변경하지 않았다.
- 관리자 Application API, StageResult, 상세 섹션 도메인은 계속 보류 상태다.

## Phase 03a-2 구현 반영 메모

- Phase 03a-2는 Controller 없이 `JobApplicationService` command만 구현한다.
- 구현된 command는 `updateDraft`, `submit`, `withdraw`이다.
- `updateDraft`는 DRAFT 상태에서 모집분야 변경만 허용한다.
- `submit`은 `DRAFT -> SUBMITTED`, `withdraw`는 `SUBMITTED -> WITHDRAWN`만 허용한다.
- 세 command 모두 `PUBLISHED` JobPosting과 접수기간 내 조건을 요구한다.
- `submittedAt`, `withdrawnAt`은 주입된 `Clock` 기준으로 저장한다.
- 상세 섹션 필수값 검증과 HTTP API 계약은 후속 Phase로 유지한다.

## 1. Summary

Phase 03의 목적은 지원자가 공개된 채용공고에 대해 지원서를 생성하고, 임시저장하고, 최종제출하고, 필요 시 철회할 수 있는 최소 루트 모델을 설계하는 것이다. 이 설계는 Phase 02에서 보류한 `StageResult`를 정합성 있게 구현하기 위한 선행 기반이다.

구현 대상 후보:

- 지원서 루트 Entity 후보
- 지원자 기준 Application 생성/조회/수정/제출/철회 흐름
- 지원자 API와 관리자 API 분리 방향
- 중복 지원 방지 정책
- `Applicant`, `JobPosting`, `JobPosition` 연결 정책
- 이후 `StageResult`가 참조할 수 있는 Application 식별 축

이번 설계에서 구현하지 않을 대상:

- Java 코드
- Entity, Repository, Service, Controller, DTO, Test 생성
- `StageResult`
- 학력, 경력, 자격, 어학, 병역, 포상, 공백기간, 첨부파일 등 상세 섹션
- Interview, Message, CommonCode
- JobPosting publish 조건의 Stage 최소 1개 강제
- JobPosting 생성 시 기본 Stage 자동 생성
- SecurityConfig 대규모 변경

추천 결론:

- 다음 Phase는 Stage 공개 노출이나 JobPosting publish 조건 보강보다 Application 기본 흐름을 우선한다.
- Phase 03a는 지원자 Application 루트 생성/조회/임시저장/제출/철회만 구현한다.
- StageResult는 Application 도메인 구현 이후 Phase 03d 또는 별도 Phase로 넘긴다.

## 2. Current Context

### Phase 01 JobPosting 구조

현재 구현된 공고 도메인은 다음 구조를 가진다.

- `JobPosting`
  - `title`
  - `contentHtml`
  - `receptionStartDateTime`
  - `receptionEndDateTime`
  - `status`: `DRAFT`, `PUBLISHED`, `CLOSED`
  - `publishedAt`
  - `closedAt`
  - `jobPositions`
  - `applicationFormConfig`
- `JobPosition`
  - `jobPosting`
  - `positionName`
  - `sortOrder`
- `ApplicationFormConfig`
  - `jobPosting`
  - `useEducation`
  - `useCareer`
  - `useCertificate`
  - `useLanguage`
  - `useMilitary`
  - `useAward`
  - `useGapPeriod`

공개 JobPosting API는 `PUBLISHED` 상태만 노출하고, `accepting`은 접수기간 기준으로 계산한다. `PUBLISHED` 공고 자체는 접수기간과 무관하게 공개 목록/상세에 노출된다.

### Phase 02 Stage 구조

현재 구현된 Stage 도메인은 다음 구조를 가진다.

- `Stage`
  - `jobPosting`
  - `stageName`
  - `stageType`: `DOCUMENT`, `FIRST_INTERVIEW`, `SECOND_INTERVIEW`, `FINAL_INTERVIEW`, `ETC`
  - `stageOrder`
  - `status`: `READY`, `IN_PROGRESS`, `RESULT_ANNOUNCED`, `CLOSED`
  - `resultAnnouncementDateTime`
  - `finalStage`
- Stage는 `JobPosting` 하위 설정이며, `Stage -> JobPosting` 단방향 N:1로 구현되어 있다.
- StageResult는 Application이 없어서 보류되어 있다.

### Application과 연결되는 지점

Application은 다음 도메인의 교차점이다.

- `Applicant`: 지원서 작성자
- `JobPosting`: 지원 대상 공고
- `JobPosition`: 공고 내 선택 모집분야
- `ApplicationFormConfig`: 이후 상세 섹션 필수/노출 검증 기준
- `Stage`: 이후 `StageResult`의 전형단계 기준
- `StageResult`: Application 구현 이후 `Application + Stage` 조합으로 결과를 저장

## 3. Naming Decision

### 후보 비교

| 후보 | 장점 | 단점 | 판단 |
|---|---|---|---|
| `Application` | 기존 설계 문서와 채용 도메인 용어에 가장 가깝다. | Spring Boot main class, 애플리케이션 런타임 개념과 혼동 가능성이 있다. 검색 시 의미가 넓다. | 문서/화면 용어로 유지 |
| `JobApplication` | 채용 지원서 의미가 명확하고 Java/Spring의 Application 용어와 충돌이 적다. | 기존 설계 문서의 `Application`과 클래스명이 달라진다. | Java 구현 클래스명으로 추천 |
| `RecruitmentApplication` | 채용 시스템 맥락이 명확하다. | 이름이 길고 DTO/API 명칭이 무거워진다. | 보조 후보 |

### 추천

- 문서와 API 설명 용어: `Application`, `지원서`
- Java Entity 클래스명: `JobApplication`
- DB table 후보: `job_application`
- Status enum 후보: `JobApplicationStatus`
- Repository/Service 후보:
  - `JobApplicationRepository`
  - `JobApplicationService`
- DTO 후보:
  - `ApplicationCreateRequest`
  - `ApplicationDetailResponse`

이렇게 하면 도메인 대화에서는 기존 설계서의 Application 용어를 유지하면서, Java 코드에서는 `RecruitApplication` main class나 Spring Application 개념과의 혼동을 줄일 수 있다.

## 4. Domain Model

### Application 루트 설계

Application은 한 지원자가 하나의 채용공고에 대해 작성하는 지원서 루트다. Phase 03a에서는 상세 섹션을 붙이지 않고, 지원 대상과 상태 전이만 안정화한다.

관계 추천:

- `Applicant` 1 : N `JobApplication`
- `JobPosting` 1 : N `JobApplication`
- `JobPosition` 1 : N `JobApplication`
- `JobApplication` N : 1 `Applicant`
- `JobApplication` N : 1 `JobPosting`
- `JobApplication` N : 1 `JobPosition`

구현 방향:

- `JobApplication -> Applicant` 단방향 N:1
- `JobApplication -> JobPosting` 단방향 N:1
- `JobApplication -> JobPosition` 단방향 N:1
- `Applicant`, `JobPosting`, `JobPosition`에는 `List<JobApplication>`을 바로 추가하지 않는다.
- cascade/orphanRemoval은 사용하지 않는다.

### JobPosition 참조와 snapshot

`JobApplication`은 `JobPosition`을 직접 참조하는 것이 좋다.

이유:

- 지원자는 공고 안의 모집분야 중 하나를 선택한다.
- 관리자 통계, 전형결과, 지원자 목록 조회에서 모집분야 기준 필터가 필요하다.
- `JobPosition`이 해당 `JobPosting` 소속인지 검증할 수 있다.

단, 공고나 모집분야가 이후 수정될 수 있으므로 snapshot 필드도 함께 두는 것을 추천한다.

snapshot 후보:

- `applicantNameSnapshot`
- `jobPostingTitleSnapshot`
- `jobPositionNameSnapshot`

Phase 03a 최소 추천:

- `jobPostingTitleSnapshot`
- `jobPositionNameSnapshot`
- `applicantNameSnapshot`

snapshot은 제출 당시뿐 아니라 생성 당시 화면 표시 안정성을 위해 생성 시 저장하고, 최종 제출 시 필요하면 다시 동기화할지 정책을 검토한다. 기본값은 생성 시 snapshot 저장이다.

`applicantNameSnapshot`의 원천은 구현 전에 실제 `Applicant`/`User` 엔티티의 이름 필드를 확인해 사용한다. 현재 사용자 계층에는 상위 `User.name`과 `Applicant.userName`처럼 혼동될 수 있는 필드가 있으므로, 구현자는 임의로 새 이름 필드를 추가하지 않는다. 필드 의미가 불명확하면 보고하고 정책을 확정한다. `loginId`나 `email`을 이름 snapshot 대체값으로 사용하지 않는다.

### 필드 후보

| 필드 | 타입 후보 | 설명 | Phase 03a 추천 |
|---|---|---|---|
| `id` | `Long` | PK | 포함 |
| `applicant` | `Applicant` | 지원자 FK | 포함 |
| `jobPosting` | `JobPosting` | 공고 FK | 포함 |
| `jobPosition` | `JobPosition` | 모집분야 FK | 포함 |
| `status` | `JobApplicationStatus` | 지원서 상태 | 포함 |
| `submittedAt` | `LocalDateTime` | 최종제출 시각 | 포함 |
| `withdrawnAt` | `LocalDateTime` | 철회 시각 | 포함 |
| `applicantNameSnapshot` | `String` | 지원자 이름 snapshot | 포함 |
| `jobPostingTitleSnapshot` | `String` | 공고 제목 snapshot | 포함 |
| `jobPositionNameSnapshot` | `String` | 모집분야명 snapshot | 포함 |
| `createdAt` / `updatedAt` | `BaseEntity` | 감사 필드 | 포함 |

### ApplicationStatus 후보

| 상태 | 의미 | Phase 03a 추천 |
|---|---|---|
| `DRAFT` | 작성 중/임시저장 | 포함 |
| `SUBMITTED` | 최종제출 완료 | 포함 |
| `WITHDRAWN` | 지원 철회 | 포함 |
| `DELETED` | 임시지원서 삭제 | 보류 |
| `CANCELLED` | 취소, 철회와 의미 중복 가능 | 보류 |
| `EXPIRED` | 접수기간 종료로 제출 불가 | 상태가 아니라 계산/정책으로 처리 |

추천 최소 enum:

```text
DRAFT
SUBMITTED
WITHDRAWN
```

## 5. Business Rules

### 생성 규칙

- 로그인한 지원자만 Application을 생성할 수 있다.
- Phase 03a Service 설계는 `applicantId`를 명시적으로 입력받는 구조로 시작한다.
- `JobPosting`은 존재해야 한다.
- `JobPosting.status`는 `PUBLISHED`여야 한다.
- 생성 시점은 접수기간 내여야 한다.
- `JobPosting`이 `DRAFT` 또는 `CLOSED`이면 생성할 수 없다.
- `JobPosition`은 존재해야 한다.
- `JobPosition`은 해당 `JobPosting` 소속이어야 한다.
- 같은 `Applicant + JobPosting` 조합의 중복 Application은 허용하지 않는다.
- 생성 시 상태는 항상 `DRAFT`다.

### 임시저장 규칙

- `DRAFT` 상태에서만 일반 수정 가능하다.
- Phase 03a에서 수정 가능한 필드는 최소한 `jobPosition` 정도로 제한한다.
- 상세 섹션이 도입되기 전까지 자기소개/학력/경력 등 상세 필드는 다루지 않는다.
- 접수기간이 지난 뒤에는 `DRAFT` 수정도 차단하는 것을 추천한다.
- 접수기간 이후에는 조회만 허용한다.
- `SUBMITTED`, `WITHDRAWN` 상태는 일반 수정 불가다.

### 제출 규칙

- `DRAFT -> SUBMITTED`만 허용한다.
- 제출 시 `JobPosting`은 `PUBLISHED`여야 한다.
- 제출 시점은 접수기간 내여야 한다.
- `JobPosting`이 `CLOSED`이면 제출할 수 없다.
- 제출 성공 시 `submittedAt = now(clock)`을 저장한다.
- Phase 03a에서는 상세 섹션 필수값 검증을 깊게 수행하지 않는다.
- Phase 03a 제출 검증은 다음 정도로 제한한다.
  - Application 존재
  - 본인 Application
  - 상태가 `DRAFT`
  - 공고가 `PUBLISHED`
  - 접수기간 내
  - 선택 모집분야가 공고 소속
  - `ApplicationFormConfig` 존재

### 철회 규칙

- `SUBMITTED -> WITHDRAWN`만 허용하는 것을 추천한다.
- 철회 성공 시 `withdrawnAt = now(clock)`을 저장한다.
- `DRAFT` 철회는 의미가 애매하므로 Phase 03a에서는 별도 삭제 command를 만들지 않고 보류한다.
- `WITHDRAWN` 이후 수정/제출/재철회는 차단한다.
- `WITHDRAWN` 이후 재지원은 정책 확정 전까지 차단한다.
- Phase 03a-2 기본 추천은 `JobPosting.status = PUBLISHED`이고 접수기간 내인 경우에만 철회를 허용하는 것이다. 접수기간 이후 또는 공고 `CLOSED` 이후 철회는 통계, 전형대상, 메시지 발송 기준에 영향을 줄 수 있으므로 관리자 문의/운영 처리로 분리한다.

### 접수기간 검증

접수기간 판단은 Phase 01b의 `accepting` 정책과 같은 기준을 사용한다.

```text
now >= receptionStartDateTime
now <= receptionEndDateTime
```

추천:

- 생성: 접수기간 내에서만 허용
- 수정: `DRAFT`이고 접수기간 내에서만 허용
- 제출: 접수기간 내에서만 허용
- 철회: Phase 03a-2 기본값은 `SUBMITTED`이고 공고 상태가 `PUBLISHED`이며 접수기간 내일 때만 허용한다. 마감 후 철회 허용 여부는 별도 운영 정책으로 재검토한다.

### 중복 지원 정책

추천 정책:

- 같은 `Applicant + JobPosting` 조합은 하나의 Application만 허용한다.
- 같은 공고에서 다른 `JobPosition`으로 복수 지원하는 것은 허용하지 않는다.
- `DRAFT`가 이미 있으면 새로 만들지 않고 기존 Application을 반환할지, 중복 생성 실패로 처리할지 결정이 필요하다.
- Phase 03a 기본 추천은 중복 생성 실패다. API 동작이 명확하고 테스트가 단순하다.
- `SUBMITTED`가 있으면 중복 지원 차단.
- `WITHDRAWN` 이후 재지원도 정책 확정 전까지 차단.

대안:

- `POST /applications`가 기존 `DRAFT`를 반환하는 idempotent API가 될 수 있다.
- 다만 프론트가 실수로 다른 모집분야를 선택해 재호출했을 때 기존 지원서를 반환하면 사용자가 오해할 수 있으므로 Phase 03a에서는 명시적 실패가 더 안전하다.

### JobPosting 상태별 허용 정책

| JobPosting 상태 | 생성 | DRAFT 수정 | 제출 | 철회 | 조회 |
|---|---|---|---|---|---|
| `DRAFT` | 차단 | 차단 | 차단 | 차단 | 본인 기존 지원서가 있을 수 없으므로 보통 없음 |
| `PUBLISHED` + 접수기간 내 | 허용 | 허용 | 허용 | 허용 후보 | 허용 |
| `PUBLISHED` + 접수기간 외 | 차단 | 차단 | 차단 | 정책 결정 필요 | 허용 |
| `CLOSED` | 차단 | 차단 | 차단 | 차단 후보 | 허용 |

## 6. API Candidate

### Phase 분리 추천

- Phase 03a: 지원자 Application 기본 흐름
- Phase 03b: 관리자 Application 목록/상세 조회
- Phase 03c: Application 상세 섹션 도메인 시작
- Phase 03d: Application 이후 StageResult 구현

### 지원자 API 후보

Base path 후보:

```text
/applications
```

| Method | Path | 목적 | 요청 DTO 후보 | 응답 DTO 후보 | Service 메서드 후보 | 주요 검증 | 상태 전이 |
|---|---|---|---|---|---|---|---|
| POST | `/applications` | 지원서 생성 | `ApplicationCreateRequest` | `ApplicationStatusResponse` 또는 `ApplicationDetailResponse` | `create(applicantId, request)` | 로그인 지원자, PUBLISHED 공고, 접수기간, 모집분야 소속, 중복 지원 차단 | 생성 시 `DRAFT` |
| GET | `/applications/{applicationId}` | 내 지원서 상세 조회 | 없음 | `ApplicationDetailResponse` | `getApplication(applicantId, applicationId)` | 본인 지원서 여부 | 없음 |
| POST | `/applications/{applicationId}` | DRAFT 지원서 수정/임시저장 | `ApplicationUpdateRequest` | `ApplicationStatusResponse` 또는 `ApplicationDetailResponse` | `updateDraft(applicantId, applicationId, request)` | 본인, DRAFT, 접수기간, 모집분야 소속 | 상태 유지 |
| POST | `/applications/{applicationId}/submit` | 최종제출 | `ApplicationSubmitRequest` 또는 없음 | `ApplicationStatusResponse` | `submit(applicantId, applicationId)` | 본인, DRAFT, PUBLISHED, 접수기간, config 존재 | `DRAFT -> SUBMITTED` |
| POST | `/applications/{applicationId}/withdraw` | 제출 철회 | `ApplicationWithdrawRequest` | `ApplicationStatusResponse` | `withdraw(applicantId, applicationId, request)` | 본인, SUBMITTED, 철회 허용 정책 | `SUBMITTED -> WITHDRAWN` |
| GET | `/applications/me` | 내 지원서 목록 | page, size | `PageResponse<MyApplicationResponse>` | `getMyApplications(applicantId, pageable)` | 로그인 지원자, page/size 검증 | 없음 |
| GET | `/job-postings/{jobPostingId}/application` | 특정 공고에 대한 내 지원서 조회 | 없음 | `ApplicationDetailResponse` | `getMyApplicationByJobPosting(applicantId, jobPostingId)` | 로그인 지원자, 공고 존재 | 없음 |

`GET /job-postings/{jobPostingId}/application`은 공개 공고 상세 화면에서 "내 지원 상태"를 붙이기 자연스럽다. 다만 `/applications/by-job-posting/{jobPostingId}`도 가능하다. 추천은 공고 맥락이 강한 `GET /job-postings/{jobPostingId}/application`이다.

### 관리자 API 후보

Phase 03a에서는 관리자 API를 구현하지 않는 것을 추천한다. 지원자 생성/제출 흐름이 먼저 안정화되어야 관리자 조회 기준도 명확해진다.

Phase 03b 후보:

| Method | Path | 목적 | 요청/Query 후보 | 응답 DTO 후보 | Service 메서드 후보 |
|---|---|---|---|---|---|
| GET | `/admin/applications` | 전체 지원서 목록 조회 | page, size, status, jobPostingId, jobPositionId | `PageResponse<ApplicationSummaryResponse>` | `getApplications(condition)` |
| GET | `/admin/applications/{applicationId}` | 지원서 상세 조회 | 없음 | `ApplicationDetailResponse` | `getApplicationForAdmin(applicationId)` |
| GET | `/admin/job-postings/{jobPostingId}/applications` | 공고별 지원서 목록 조회 | page, size, status, jobPositionId | `PageResponse<ApplicationSummaryResponse>` | `getApplicationsByJobPosting(jobPostingId, condition)` |

관리자 API는 개인정보 응답 범위와 마스킹 정책이 필요하므로 Phase 03b에서 별도 설계/구현한다.

## 7. Entity Candidate

### Entity 후보

Java 클래스명 추천: `JobApplication`

테이블명 추천: `job_application`

필드 후보:

| 필드 | 타입 | 매핑 후보 | 비고 |
|---|---|---|---|
| `id` | `Long` | `@Id`, `IDENTITY` | PK |
| `applicant` | `Applicant` | `@ManyToOne(fetch = LAZY)`, `nullable=false` | 지원자 |
| `jobPosting` | `JobPosting` | `@ManyToOne(fetch = LAZY)`, `nullable=false` | 공고 |
| `jobPosition` | `JobPosition` | `@ManyToOne(fetch = LAZY)`, `nullable=false` | 선택 모집분야 |
| `status` | `JobApplicationStatus` | `@Enumerated(STRING)`, `nullable=false` | `DRAFT` 기본 |
| `submittedAt` | `LocalDateTime` | nullable | 제출 시각 |
| `withdrawnAt` | `LocalDateTime` | nullable | 철회 시각 |
| `applicantNameSnapshot` | `String` | nullable=false 후보 | 지원자명 snapshot |
| `jobPostingTitleSnapshot` | `String` | nullable=false | 공고명 snapshot |
| `jobPositionNameSnapshot` | `String` | nullable=false | 모집분야명 snapshot |
| `createdAt` / `updatedAt` | `BaseEntity` | 상속 | 감사 필드 |

### 연관관계 후보

```text
JobApplication N : 1 Applicant
JobApplication N : 1 JobPosting
JobApplication N : 1 JobPosition
```

추천:

- 단방향 ManyToOne으로 시작
- `Applicant`에 `List<JobApplication>` 추가하지 않음
- `JobPosting`에 `List<JobApplication>` 추가하지 않음
- `JobPosition`에 `List<JobApplication>` 추가하지 않음
- cascade/orphanRemoval 사용하지 않음

### Repository 후보

`JobApplicationRepository` 메서드 후보:

- `Optional<JobApplication> findByIdAndApplicantId(Long id, Long applicantId)`
- `Optional<JobApplication> findByApplicantIdAndJobPostingId(Long applicantId, Long jobPostingId)`
- `boolean existsByApplicantIdAndJobPostingId(Long applicantId, Long jobPostingId)`
- `Page<JobApplication> findByApplicantIdOrderByCreatedAtDesc(Long applicantId, Pageable pageable)`
- Phase 03b 관리자 조회 후보:
  - `Page<JobApplication> findByJobPostingIdOrderByCreatedAtDesc(Long jobPostingId, Pageable pageable)`
  - status/filter 조합은 Specification 또는 Querydsl 도입 전까지 명시 query 후보

`JobPosition` 조회 방식:

- 기존 `JobPositionRepository`가 없으면 Phase 03a-1에서 `JobPositionRepository`를 추가한다.
- 이는 새 도메인 추가가 아니라 Phase 01a에서 이미 구현된 `JobPosition` 조회 인프라를 보강하는 것이다.
- 필요한 메서드 후보:
  - `Optional<JobPosition> findById(Long id)`
  - `Optional<JobPosition> findByIdAndJobPostingId(Long id, Long jobPostingId)`
- Application 생성 시 `findByIdAndJobPostingId(jobPositionId, jobPostingId)`로 모집분야가 해당 공고 소속인지 검증한다.

### Index/unique 후보

최종 권장:

- unique: `applicant_id + job_posting_id`
- index: `job_posting_id`
- index: `applicant_id`
- index: `status`
- index: `job_posting_id + status`
- index: `job_position_id`

Phase 03a-1 구현 결정:

- `applicant_id + job_posting_id` unique 제약을 Phase 03a-1에서 추가한다.
- Service의 `existsByApplicantIdAndJobPostingId` 검증도 함께 둔다.
- 중복 지원 차단은 Application의 핵심 정책이고, Stage reorder와 달리 순서 교환 중간 충돌 같은 문제가 없다.
- `WITHDRAWN` 이후 재지원도 현재 정책상 차단하므로 unique 제약과 충돌하지 않는다.
- 나중에 철회 후 재지원을 허용하기로 정책이 바뀌면 이 unique 제약은 재검토한다.
- 동시 생성 요청에서는 두 요청이 Service 중복 검증을 모두 통과한 뒤 DB unique 제약에서 하나가 실패할 수 있다. Controller/API가 붙는 Phase 03a-3 또는 운영 안정화 단계에서 `DataIntegrityViolationException`을 `InvalidJobApplicationException` 성격의 실패 응답으로 변환할지 검토한다.

### application_number 여부

`applicationNumber` unique 필드는 Phase 03a에서는 보류한다.

이유:

- 외부 노출용 접수번호 포맷 정책이 아직 없다.
- 채번 규칙, 연도/공고 prefix, 재지원/철회 처리 정책이 필요하다.
- 내부 PK와 별개로 접수번호가 필요해지면 Phase 03b 또는 통계/출력 Phase에서 추가한다.

## 8. Validation and Security Considerations

### 현재 로그인 Applicant 식별 방식

현재 `CustomUserDetails`는 `loginId`, `deptName`, `name`, `userType`을 가지고 있으나 `userId` 또는 `applicantId`를 직접 제공하지 않는다.

설계 추천:

- Phase 03a Service 메서드는 `applicantId`를 입력받도록 설계한다.
- Controller에서는 인증 객체에서 applicant를 식별해 `applicantId`를 넘기는 구조를 목표로 한다.
- 구현 전에 다음 중 하나를 결정해야 한다.
  - `CustomUserDetails`에 `userId` 추가
  - `loginId`로 `ApplicantRepository`를 조회하는 resolver/helper 추가
  - `@AuthenticationPrincipal CustomUserDetails` + 별도 `CurrentApplicantService` 사용

추천 기본값:

- `CustomUserDetails`에 `userId`를 추가하는 방향이 장기적으로 단순하다.
- 단, SecurityConfig 대규모 변경은 피하고, Phase 03a에서는 작은 보완으로 한정한다.

### 접근 제어

- 로그인하지 않은 사용자의 Application 생성/조회/수정/제출/철회는 차단해야 한다.
- Employee/Admin은 지원자 Application 생성 API를 호출할 수 없어야 한다.
- 지원자 API는 본인 Application만 조회/수정/제출/철회 가능해야 한다.
- 관리자 API는 Phase 03b에서 별도 권한 정책과 함께 설계한다.

현재 `SecurityConfig`가 개발 단계에서 `permitAll`이므로 Phase 03a 구현 시 보안은 두 층으로 나눠 접근한다.

1. Service는 반드시 `applicantId` 기준으로 소유자 검증을 수행한다.
2. 실제 HTTP 인증/인가 적용은 보안 정책 확정 후 작은 범위로 보완한다.

### 개인정보 응답 제한

Application 응답에는 다음 정보를 섞지 않는다.

- 주민번호
- CI
- 휴대폰번호
- 이메일
- 주소
- 암호화된 개인정보 원문

Phase 03a 응답은 지원서 식별자, 공고/모집분야 식별자와 snapshot, 상태, 시각 정보 중심으로 제한한다. Applicant 개인정보 상세는 Profile API 또는 Applicant 도메인에서 분리 관리한다.

### ApplicationFormConfig 검증 시점

Phase 03a에서는 `ApplicationFormConfig`를 깊게 검증하지 않는다.

추천:

- Application 생성/제출 시 `JobPosting.applicationFormConfig` 존재 여부만 확인한다.
- `useEducation`, `useCareer` 등 상세 섹션 필수 검증은 각 섹션 도메인 구현 Phase에서 추가한다.
- Phase 03a 문서와 코드 TODO에 "submit 필수 섹션 검증은 후속 Phase"로 남긴다.

## 9. Test Plan

### Service 테스트 후보

- `PUBLISHED` + 접수기간 내 공고에 Application 생성 성공
- `DRAFT` JobPosting에는 Application 생성 실패
- `CLOSED` JobPosting에는 Application 생성 실패
- 접수기간 전 Application 생성 실패
- 접수기간 후 Application 생성 실패
- 존재하지 않는 JobPosting 실패
- 존재하지 않는 JobPosition 실패
- JobPosition이 해당 JobPosting 소속이 아니면 실패
- 같은 Applicant + JobPosting 중복 생성 실패
- DRAFT Application 조회 성공
- 다른 Applicant의 Application 조회 실패
- DRAFT Application 수정 성공
- SUBMITTED Application 수정 실패
- WITHDRAWN Application 수정 실패
- DRAFT -> SUBMITTED 성공
- 접수기간 지난 뒤 submit 실패
- SUBMITTED -> WITHDRAWN 성공
- WITHDRAWN 이후 submit 실패
- WITHDRAWN 이후 update 실패

### Controller/API 테스트 후보

- `POST /applications` 생성 성공
- `GET /applications/{id}` 조회 성공
- `POST /applications/{id}` 수정 성공
- `POST /applications/{id}/submit` 성공
- `POST /applications/{id}/withdraw` 성공
- `GET /applications/me` 내 지원서 목록 조회 성공
- `GET /job-postings/{jobPostingId}/application` 공고별 내 지원서 조회 성공
- validation 실패 시 `400 + ApiResponse.fail`
- 존재하지 않는 Application 조회 시 `404 + ApiResponse.fail`
- 타인의 Application 조회/수정/제출/철회 차단
- PUT 미지원 확인
- DELETE HTTP method 미지원 확인

### 보안/권한 테스트 후보

- 미로그인 사용자 Application 생성 차단
- Employee 사용자 Application 생성 차단
- Applicant 사용자 본인 Application 접근 허용
- Applicant 사용자 타인 Application 접근 차단

현재 SecurityConfig가 `permitAll`이므로 위 보안 테스트는 실제 권한 적용 Phase에서 구현하거나, Phase 03a에서는 Service 소유자 검증 테스트로 우선 대체한다.

## 10. Implementation Plan

### Phase 03a-1: Application Entity/Repository/Service 기본 생성/조회

- `JobApplication` Entity 후보 구현
- `JobApplicationStatus` enum 후보 구현
- `JobApplicationRepository` 후보 구현
- `JobApplicationService.create`
- `JobApplicationService.getApplication`
- `JobApplicationService.getMyApplicationByJobPosting`
- 중복 지원 방지를 위한 `applicant_id + job_posting_id` DB unique 제약
- 기존 `JobPositionRepository`가 없으면 `findByIdAndJobPostingId` 중심의 Repository 추가
- PUBLISHED/접수기간/JobPosition 소속/중복 지원 검증
- Service 테스트 중심
- Controller는 만들지 않고 다음 Phase로 분리
- `updateDraft`, `submit`, `withdraw`는 구현하지 않음

### Phase 03a-2: draft update / submit / withdraw command

- `updateDraft`
- `submit`
- `withdraw`
- `JobApplication` Entity에 `submit(now)`와 `withdraw(now)` 같은 의미 있는 상태 변경 메서드 추가
- `Clock` 주입으로 `submittedAt`, `withdrawnAt` 안정화
- 상태 전이 검증
- 접수기간 검증
- Service 테스트 보강

### Phase 03a-3: 지원자 Controller/API/Test/문서화

- `ApplicationController`
- `POST /applications`
- `GET /applications/{applicationId}`
- `POST /applications/{applicationId}`
- `POST /applications/{applicationId}/submit`
- `POST /applications/{applicationId}/withdraw`
- `GET /job-postings/{jobPostingId}/application`
- MockMvc 기반 API 계약 테스트
- Phase 03a 구현 문서 작성
- `GET /applications/me` 목록 API는 Phase 03a-3에서 구현하지 않았고, `PageResponse<MyApplicationResponse>` 형태의 별도 Phase 후보로 유지한다.

### Phase 03b: 관리자 Application 목록/상세 조회

- `GET /admin/applications`
- `GET /admin/applications/{applicationId}`
- `GET /admin/job-postings/{jobPostingId}/applications`
- page/size/filter 설계
- 개인정보 응답 제한/마스킹 정책 검토

### Phase 03c: Application 상세 섹션 도메인 시작

- Education
- Career
- Certificate
- Language
- Military
- Award
- GapPeriod
- Attachment
- `ApplicationFormConfig` 기반 섹션 노출/필수 검증

### Phase 03d: Application 이후 StageResult 구현

- `StageResult`
- `StageResultStatus`
- `Application + Stage` unique
- 관리자 전형결과 조회/저장
- 관리자 지원서 상세 전형결과 timeline 조회
- 지원자 결과 조회는 Phase 03d-4에서 별도 applicant DTO와 visibility guard로 구현 완료
- 발표 후 결과 정정은 Phase 03d-5에서 correction command와 history로 구현
- Stage 상태와 결과 발표 정책 연동

## 11. Risks and Open Questions

### 반드시 결정해야 할 것

- Java 클래스명을 `JobApplication`으로 확정할지
- 중복 `DRAFT` 생성 요청을 실패로 처리할지, 기존 DRAFT를 반환할지
- `WITHDRAWN` 이후 재지원 허용 여부
- 철회 가능 기간: 접수기간 이후에도 철회 가능한지
- 현재 로그인 Applicant 식별을 `CustomUserDetails.userId`로 풀지, 별도 조회 helper로 풀지
- 인증 연동 이후 Applicant 미존재를 400, 404, 인증/인가 예외 중 무엇으로 매핑할지
- DB unique 충돌을 Controller/API 응답에서 별도 Application 비즈니스 예외로 변환할지

### Codex 기본값으로 둘 수 있는 것

- `JobApplication` 클래스명 사용
- `DRAFT`, `SUBMITTED`, `WITHDRAWN` 3상태로 시작
- `JobApplication -> Applicant/JobPosting/JobPosition` 단방향 N:1
- `Applicant`, `JobPosting`, `JobPosition`에는 Application 컬렉션 미추가
- cascade/orphanRemoval 미사용
- Phase 03a-1에서 `applicant_id + job_posting_id` DB unique 추가 및 Service 중복 검증 병행
- `JobPositionRepository.findByIdAndJobPostingId`로 모집분야 소속 검증
- snapshot 3종 저장
- `applicantNameSnapshot`은 실제 `Applicant`/`User` 이름 필드를 확인해 사용하고, `loginId`/`email`로 대체하지 않음
- 상세 섹션 필수 검증은 후속 Phase로 보류
- 관리자 Application API는 Phase 03b로 분리
- StageResult는 Phase 03d 또는 Application 이후 별도 Phase로 보류

## 12. Archived Phase 03a-1 Codex Prompt

아래 지시문은 Phase 03a-1 구현 당시 사용하기 위한 보관용 초안이다. Phase 03a-3 이후 현재 구현 지시문이 아니며, `ApplicationController`와 command 구현 금지 문구는 Phase 03a-1 범위 제한을 뜻한다.

```text
AGENTS.md와 docs/codex/*.md를 먼저 읽어라.
특히 다음 문서를 확인해라.

- docs/codex/design/phase-03-application-design.md
- docs/codex/implementation/phase-01a-job-posting.md
- docs/codex/implementation/phase-01b-job-posting-public-read.md
- docs/codex/design/phase-02-stage-design.md
- docs/codex/implementation/phase-02a-3-stage-api-test.md
- docs/codex/07-implementation-history.md

이번 작업은 Phase 03a-1: 지원자 Application 기본 생성/조회 기반 구현이다.

중요:
- 이번 작업에서는 Application Entity/Repository/Service 기본 생성/조회까지만 구현한다.
- draft update, submit, withdraw command는 이번 작업에서 구현하지 마라.
- ApplicationController는 이번 작업에서 만들지 마라.
- 관리자 Application API는 구현하지 마라.
- StageResult는 아직 구현하지 마라.
- Education, Career, Certificate, Language, Military, Award, GapPeriod, Interview, Message, CommonCode를 만들지 마라.

구현 범위:
1. Entity
   - Java 클래스명은 JobApplication을 사용한다.
   - table 후보는 job_application을 사용한다.
   - BaseEntity를 상속한다.
   - Applicant, JobPosting, JobPosition을 LAZY ManyToOne으로 참조한다.
   - status는 JobApplicationStatus enum을 STRING으로 저장한다.
   - status 기본값은 DRAFT다.
   - submittedAt, withdrawnAt 필드는 후보로 추가하되 Phase 03a-1에서는 값 변경 command를 만들지 않는다.
   - applicantNameSnapshot, jobPostingTitleSnapshot, jobPositionNameSnapshot을 저장한다.
   - applicantNameSnapshot은 기존 Applicant/User 엔티티의 실제 이름 필드를 확인해 사용한다.
   - 필드 의미가 불명확하면 임의로 새 필드를 만들지 말고 보고한다.
   - loginId나 email을 이름 snapshot으로 대체하지 않는다.
   - Applicant/JobPosting/JobPosition 쪽에 List<JobApplication>을 추가하지 않는다.
   - cascade/orphanRemoval을 사용하지 않는다.
   - applicant_id + job_posting_id unique 제약을 추가한다.

2. Enum
   - JobApplicationStatus: DRAFT, SUBMITTED, WITHDRAWN

3. Repository
   - JobApplicationRepository 추가
   - findByIdAndApplicantId
   - findByApplicantIdAndJobPostingId
   - existsByApplicantIdAndJobPostingId
   - findByApplicantIdOrderByCreatedAtDesc 후보
   - 기존 JobPositionRepository가 없으면 추가한다.
   - JobPositionRepository.findByIdAndJobPostingId(Long id, Long jobPostingId)를 추가한다.
   - Application 생성 시 findByIdAndJobPostingId로 모집분야 소속을 검증한다.

4. Service
   - JobApplicationService 추가
   - create(Long applicantId, ApplicationCreateRequest request)
   - getApplication(Long applicantId, Long applicationId)
   - getMyApplicationByJobPosting(Long applicantId, Long jobPostingId)
   - Service는 applicantId를 입력받는 구조로 설계한다.
   - Controller 인증 연동은 다음 Phase에서 보강할 수 있다.
   - 같은 Applicant + JobPosting 중복은 Service 검증과 DB unique로 함께 차단한다.

5. DTO
   - ApplicationCreateRequest: jobPostingId, jobPositionId
   - ApplicationDetailResponse
   - ApplicationStatusResponse 또는 ApplicationSummaryResponse
   - 민감정보는 응답에 포함하지 않는다.

6. Business Rules
   - Application 생성은 PUBLISHED JobPosting에만 허용한다.
   - Application 생성은 접수기간 내에만 허용한다.
   - DRAFT/CLOSED JobPosting에는 생성할 수 없다.
   - JobPosition은 해당 JobPosting 소속이어야 한다.
   - 같은 Applicant + JobPosting 중복 생성은 차단한다.
   - WITHDRAWN 이후 재지원도 현재 정책상 차단한다.
   - 생성 시 상태는 DRAFT로 고정한다.
   - ApplicationFormConfig는 존재 여부 정도만 확인하고 상세 섹션 필수 검증은 하지 않는다.

7. Test
   - PUBLISHED + 접수기간 내 공고에 Application 생성 성공
   - DRAFT JobPosting 생성 실패
   - CLOSED JobPosting 생성 실패
   - 접수기간 전/후 생성 실패
   - 존재하지 않는 JobPosting 실패
   - 존재하지 않는 JobPosition 실패
   - JobPosition이 해당 JobPosting 소속이 아니면 실패
   - 같은 Applicant + JobPosting 중복 생성 실패
   - 내 Application 조회 성공
   - 다른 Applicant의 Application 조회 실패
   - 공고별 내 Application 조회 성공

금지:
- PUT을 추가하지 마라.
- DELETE HTTP method를 추가하지 마라.
- ApplicationController를 이번 Phase에 만들지 마라.
- submit/withdraw/update command를 이번 Phase에 구현하지 마라.
- StageResult를 구현하지 마라.
- 상세 섹션 도메인을 만들지 마라.
- SecurityConfig를 대규모로 변경하지 마라.
- LDAP 설정을 하드코딩하지 마라.
- src/main/resources/static을 복구하지 마라.
- build.gradle Java 기준을 21로 올리지 마라.

문서화:
- docs/codex/implementation/phase-03a-1-application-basic-create-read.md 생성
- docs/codex/07-implementation-history.md 갱신

검증:
- ./gradlew clean test 실행
- 변경 파일, 테스트 결과, 남은 이슈를 보고해라.
```
## Phase 03d-1 StageResult Implementation Note

- Phase 03d-1 implemented the first StageResult vertical slice after Application detail sections and question/answer flows.
- StageResult is now an admin-only result record for one `Stage + JobApplication` pair.
- The implemented admin APIs are:
  - `GET /admin/stages/{stageId}/results`
  - `POST /admin/stages/{stageId}/results/initialize`
- Initialize creates missing `PENDING` rows only for `SUBMITTED` applications belonging to the Stage's JobPosting.
- `DRAFT` and `WITHDRAWN` applications are excluded from new StageResult creation.
- StageResult does not add collections to `Stage` or `JobApplication`.
- Result update, bulk update, applicant-facing result read, and correction/audit policies remain deferred to Phase 03d-2 or later.

## Phase 03d-2 StageResult Update and Announce Guard Note

- Phase 03d-2 implemented admin StageResult result input commands.
- Added:
  - `POST /admin/stages/{stageId}/results/{resultId}`
  - `POST /admin/stages/{stageId}/results/bulk`
- Updates modify existing StageResult rows only; missing result rows are still created only by initialize.
- General updates are allowed only when Stage is `IN_PROGRESS`.
- `PENDING` rollback is rejected.
- Stage announce now fails when StageResult rows are missing or any result remains `PENDING`.
- Applicant-facing result read and admin application stage-result timeline remain deferred.

## Phase 03e-3 Application URL Authorization Note

- Phase 03e-3 protected applicant-owned Application APIs at URL level.
- `/applications/**` now requires `ROLE_APPLICANT`.
- `GET /job-postings/{jobPostingId}/application` also requires `ROLE_APPLICANT` because it returns the current applicant's application for a posting.
- Public job posting reads under `GET /job-postings/**` remain public.
- Employee/admin principals must use admin APIs instead of applicant APIs.
- Applicant resource ownership remains a service-level rule and continues to hide other applicants' resources as 404.
- 401/403 JSON `ApiResponse.fail` response handling is deferred to Phase 03e-4.
