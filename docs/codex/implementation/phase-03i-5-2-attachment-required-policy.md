# Phase 03i-5-2 - Attachment Required Policy Implementation

## Phase Summary

- Date: 2026-05-22
- Purpose: implement the dedicated attachment required policy designed in Phase 03i-5 so job postings can define attachment requirements without adding attachment flags to `ApplicationFormConfig`.
- Result: `JobPostingAttachmentRequirement` is now configurable for DRAFT postings, public job posting responses derive attachment policy from actual requirement rows, applicant dashboard readiness reports attachment issues, and final submit validation blocks only missing required attachments.
- Review fix: when a posting has any required attachment requirement, dashboard readiness treats `ATTACHMENT` as a required group only and suppresses optional missing attachment issues so optional incomplete counts cannot diverge from optional group counts.

## Scope

- Add the per-posting attachment requirement domain model.
- Add admin read/replace-all endpoints for attachment requirements.
- Enforce replace-all mutation only while the job posting is `DRAFT`.
- Reject duplicate `(attachmentType, sectionType)` rows at service level.
- Derive public `applicationFormRequiredPolicy.attachmentRequired` from actual required attachment rows.
- Keep public list response aggregate-only and expose the safe `attachmentRequirements` list only on public detail.
- Add dashboard attachment readiness:
  - required rows create blocking `ATTACHMENT` issues when missing.
  - optional rows with `minCount > 0` create non-blocking optional issues only when the posting has no required attachment rows.
  - optional rows with `minCount = 0` are guide-only.
- Add final submit attachment validation:
  - required rows block submit when stored active attachment count is below `minCount`.
  - optional rows never block submit.
- Count only `ApplicationAttachment` rows with matching application, matching `attachmentType`, matching `sectionType`, `physicalFileStatus=STORED`, and `deletedAt == null`.

## Out Of Scope

- No `ApplicationFormConfig.useAttachment` or `ApplicationFormConfig.requireAttachment` field was added.
- No `sectionRecordId`-level attachment requirement was added.
- No `maxCount`, conditional requirement, applicant-type requirement, position-specific requirement, versioning, active flag, or template linkage was added.
- No attachment upload, download, delete, storage scan, or storage repair behavior was changed.
- No filesystem existence check was added to dashboard or submit validation.
- No SecurityConfig, LDAP, frontend/static resource, SMS/email, batch, or migration framework behavior was changed.
- No Flyway/Liquibase migration file was added because the repository does not currently use a migration convention.

## Changed Files

| File | Change |
| --- | --- |
| `src/main/java/com/shinyoung/recruit/domain/entity/JobPostingAttachmentRequirement.java` | Added the per-posting attachment requirement entity. |
| `src/main/java/com/shinyoung/recruit/domain/repository/JobPostingAttachmentRequirementRepository.java` | Added requirement lookup, replace-all delete, required-only lookup, and aggregate policy count queries. |
| `src/main/java/com/shinyoung/recruit/domain/repository/JobPostingAttachmentRequirementPolicyCount.java` | Added lightweight aggregate count record for public policy derivation. |
| `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationAttachmentRepository.java` | Added stored-status attachment lookup used by dashboard and submit validation. |
| `src/main/java/com/shinyoung/recruit/dto/request/AttachmentRequirementRequest.java` | Added admin request row DTO. |
| `src/main/java/com/shinyoung/recruit/dto/request/AttachmentRequirementReplaceRequest.java` | Added admin replace-all request DTO. |
| `src/main/java/com/shinyoung/recruit/dto/response/JobPostingAttachmentRequirementResponse.java` | Added admin response DTO. |
| `src/main/java/com/shinyoung/recruit/dto/response/AttachmentRequirementPublicResponse.java` | Added public safe response DTO. |
| `src/main/java/com/shinyoung/recruit/dto/response/ApplicationFormRequiredPolicyResponse.java` | Added attachment policy derivation from aggregate requirement counts. |
| `src/main/java/com/shinyoung/recruit/dto/response/JobPostingPublicListResponse.java` | Public list now uses aggregate attachment policy counts without exposing full requirement rows. |
| `src/main/java/com/shinyoung/recruit/dto/response/JobPostingPublicDetailResponse.java` | Public detail now exposes safe `attachmentRequirements`. |
| `src/main/java/com/shinyoung/recruit/service/JobPostingAttachmentRequirementService.java` | Added DRAFT-only replace-all admin service with normalization and validation. |
| `src/main/java/com/shinyoung/recruit/service/JobPostingPublicService.java` | Added aggregate public list policy and public detail requirement loading. |
| `src/main/java/com/shinyoung/recruit/service/ApplicationCompletionReadChecker.java` | Added dashboard attachment readiness checks and review fix for mixed required/optional attachment policies. |
| `src/main/java/com/shinyoung/recruit/service/ApplicationSubmitValidator.java` | Added final submit required attachment validation. |
| `src/main/java/com/shinyoung/recruit/controller/JobPostingAttachmentRequirementController.java` | Added admin requirement GET/POST endpoints. |
| `src/test/java/com/shinyoung/recruit/service/JobPostingAttachmentRequirementServiceTest.java` | Added service-level replace, validation, duplicate, and DRAFT-only tests. |
| `src/test/java/com/shinyoung/recruit/controller/JobPostingControllerTest.java` | Added admin endpoint JSON tests. |
| `src/test/java/com/shinyoung/recruit/service/JobPostingPublicServiceTest.java` | Added public list/detail policy tests. |
| `src/test/java/com/shinyoung/recruit/controller/JobPostingPublicControllerTest.java` | Added public JSON exposure tests. |
| `src/test/java/com/shinyoung/recruit/dto/response/ApplicationFormPolicyResponseTest.java` | Added attachment policy derivation tests. |
| `src/test/java/com/shinyoung/recruit/service/ApplicationDashboardServiceTest.java` | Added attachment dashboard readiness tests. |
| `src/test/java/com/shinyoung/recruit/service/ApplicationSubmitValidatorTest.java` | Added submit-blocking attachment validation tests. |
| `docs/codex/07-implementation-history.md` | Added Phase 03i-5-2 history entry. |
| `docs/codex/reports/phase-03i-5-2-attachment-required-policy.html` | Added the human-readable status report. |

## New Classes

| Package | Class | Type | Responsibility |
| --- | --- | --- | --- |
| `com.shinyoung.recruit.domain.entity` | `JobPostingAttachmentRequirement` | Entity | Stores one attachment requirement row for a job posting and a `(attachmentType, sectionType)` unit. |
| `com.shinyoung.recruit.domain.repository` | `JobPostingAttachmentRequirementRepository` | Repository | Reads, deletes, and aggregates attachment requirement rows. |
| `com.shinyoung.recruit.domain.repository` | `JobPostingAttachmentRequirementPolicyCount` | Repository Projection | Carries aggregate total/required counts per job posting for public policy. |
| `com.shinyoung.recruit.dto.request` | `AttachmentRequirementRequest` | Request DTO | Receives one admin attachment requirement row. |
| `com.shinyoung.recruit.dto.request` | `AttachmentRequirementReplaceRequest` | Request DTO | Receives the admin replace-all requirement list. |
| `com.shinyoung.recruit.dto.response` | `JobPostingAttachmentRequirementResponse` | Response DTO | Returns admin requirement rows with internal ids. |
| `com.shinyoung.recruit.dto.response` | `AttachmentRequirementPublicResponse` | Response DTO | Returns only public-safe requirement fields. |
| `com.shinyoung.recruit.service` | `JobPostingAttachmentRequirementService` | Service | Implements DRAFT-only admin read/replace behavior and validation. |
| `com.shinyoung.recruit.controller` | `JobPostingAttachmentRequirementController` | Controller | Exposes admin attachment requirement endpoints. |
| `com.shinyoung.recruit.service` | `JobPostingAttachmentRequirementServiceTest` | Test | Covers service validation and replace-all behavior. |

## Modified Classes

| Package | Class | Type | Responsibility | Key fields or methods | Related classes | Important notes |
| --- | --- | --- | --- | --- | --- | --- |
| `com.shinyoung.recruit.domain.repository` | `ApplicationAttachmentRepository` | Repository | Provides attachment metadata lookup. | `findByJobApplicationIdAndPhysicalFileStatus(...)` | `ApplicationAttachment`, `PhysicalFileStatus` | Dashboard and submit validation request only `STORED` rows, then ignore rows with `deletedAt != null`. |
| `com.shinyoung.recruit.dto.response` | `ApplicationFormRequiredPolicyResponse` | Response DTO | Normalizes public required/optional policy. | `from(config, questionCount, attachmentPolicyCount)`, `from(projection, questionCount, attachmentPolicyCount)` | `JobPostingAttachmentRequirementPolicyCount` | `ATTACHMENT` is `DISABLED` when there are no rows, `OPTIONAL` when only optional rows exist, and `REQUIRED` when any required row exists. |
| `com.shinyoung.recruit.dto.response` | `JobPostingPublicListResponse` | Response DTO | Public list response. | `from(...)` | `ApplicationFormRequiredPolicyResponse` | Does not expose `attachmentRequirements`; uses aggregate count only. |
| `com.shinyoung.recruit.dto.response` | `JobPostingPublicDetailResponse` | Response DTO | Public detail response. | `attachmentRequirements`, `from(...)` | `AttachmentRequirementPublicResponse` | Exposes safe requirement fields without storage path, file status, applicant file ids, or delete metadata. |
| `com.shinyoung.recruit.service` | `JobPostingPublicService` | Service | Reads public postings. | `getJobPostings`, `getJobPosting`, `getAttachmentPolicyCountByPostingId` | `JobPostingAttachmentRequirementRepository` | Public list batches aggregate counts; public detail loads rows sorted by `sortOrder`, then id. |
| `com.shinyoung.recruit.service` | `ApplicationCompletionReadChecker` | Service | Builds applicant dashboard readiness. | `checkAttachments(...)`, `AttachmentRequirementKey` | `ApplicationDashboardService`, `ApplicationAttachmentRepository` | Uses one `ATTACHMENT` group; when any required attachment row exists, optional missing rows are suppressed on the dashboard to keep required/optional counts internally consistent. Optional guide-only rows with `minCount=0` do not create optional incomplete issues. |
| `com.shinyoung.recruit.service` | `ApplicationSubmitValidator` | Service | Validates final submit blockers. | `validateAttachments(...)`, `AttachmentRequirementKey` | `JobApplication`, `ApplicationAttachmentRepository` | Loads only required rows and blocks when active stored count is below `minCount`. |
| `com.shinyoung.recruit.controller` | `JobPostingControllerTest` | Test | Admin job posting controller coverage. | attachment requirement endpoint tests | `JobPostingAttachmentRequirementController` | Existing controller test context also covers the new controller bean. |
| `com.shinyoung.recruit.service` | `JobPostingPublicServiceTest` | Test | Public service coverage. | list/detail policy tests | `JobPostingPublicService` | Verifies public list aggregate-only behavior and detail safe list behavior. |
| `com.shinyoung.recruit.controller` | `JobPostingPublicControllerTest` | Test | Public API JSON coverage. | `attachmentRequirements` JSON assertions | `JobPostingPublicController` | Verifies public list has no full `attachmentRequirements` field. |
| `com.shinyoung.recruit.dto.response` | `ApplicationFormPolicyResponseTest` | Test | Policy DTO coverage. | attachment required/optional policy tests | `ApplicationFormRequiredPolicyResponse` | Verifies `attachmentRequired` and `ATTACHMENT` section type are derived from counts. |
| `com.shinyoung.recruit.service` | `ApplicationDashboardServiceTest` | Test | Dashboard readiness coverage. | required/optional attachment tests | `ApplicationCompletionReadChecker` | Verifies optional attachment issues are non-blocking. |
| `com.shinyoung.recruit.service` | `ApplicationSubmitValidatorTest` | Test | Submit validator coverage. | stored/matching/deleted/minCount tests | `ApplicationSubmitValidator` | Verifies optional rows do not block submit. |

## API List

| Method | Path | Purpose | Request | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/admin/job-postings/{jobPostingId}/attachment-requirements` | Read configured attachment requirements for an admin. | None | `ApiResponse<List<JobPostingAttachmentRequirementResponse>>` |
| `POST` | `/admin/job-postings/{jobPostingId}/attachment-requirements` | Replace all configured attachment requirements for a DRAFT posting. | `AttachmentRequirementReplaceRequest` | `ApiResponse<List<JobPostingAttachmentRequirementResponse>>` |
| `GET` | `/job-postings` | Public list. | None | Existing list response with aggregate `applicationFormRequiredPolicy`; no full `attachmentRequirements`. |
| `GET` | `/job-postings/{id}` | Public detail. | None | Existing detail response plus safe `attachmentRequirements`. |
| existing dashboard read flow | existing applicant dashboard endpoint | Applicant completion/readiness summary. | None | May include `ATTACHMENT` required/optional readiness issues. |
| existing final submit flow | existing applicant submit endpoint | Final submit validation. | None | Required missing attachments fail validation. |

## Entity Relationship Summary

```text
JobPosting 1 --- N JobPostingAttachmentRequirement

JobPostingAttachmentRequirement
  - attachmentType
  - sectionType
  - required
  - minCount
  - sortOrder
  - displayName
  - description

JobApplication 1 --- N ApplicationAttachment

Requirement satisfaction:
  JobApplication.id
  + ApplicationAttachment.attachmentType == JobPostingAttachmentRequirement.attachmentType
  + ApplicationAttachment.sectionType == JobPostingAttachmentRequirement.sectionType
  + ApplicationAttachment.physicalFileStatus == STORED
  + ApplicationAttachment.deletedAt == null
```

- The requirement unit is `jobPosting + attachmentType + sectionType`.
- `sectionRecordId` is intentionally not part of the Phase 03i-5-2 requirement unit.
- The implementation enforces duplicate rows in the service layer. A persistent DB unique key is documented below as a manual DDL candidate, but no migration file was added.

## Validation And Business Rules

- Admin replace-all is accepted only when the target posting status is `DRAFT`.
- `request == null` or `requirements == null` is treated as an empty replace-all request.
- Duplicate `(attachmentType, sectionType)` rows are rejected.
- `attachmentType` is required.
- `sectionType` is required.
- `displayName` is required after trimming and must be at most 100 characters.
- `description` is optional, trimmed, and must be at most 500 characters.
- `required` defaults to `false` when omitted.
- `minCount` defaults to `1` for required rows and `0` for optional rows.
- Required rows must have `minCount >= 1`.
- Optional rows must have `minCount >= 0`.
- `sortOrder` defaults to request row order and must be `>= 0`.
- Existing upload/download/delete/storage behavior is unchanged.

## Public Policy Behavior

- Public list:
  - batches aggregate counts by posting id.
  - does not expose full `attachmentRequirements`.
  - derives only the normalized `applicationFormRequiredPolicy`.
- Public detail:
  - exposes safe `attachmentRequirements`.
  - does not expose applicant file ids, storage paths, stored file names, physical file status, deletion metadata, or filesystem information.
- `ApplicationFormRequiredPolicyResponse` derives attachment policy as follows:
  - no rows: `ATTACHMENT` is `DISABLED`, `attachmentRequired=false`.
  - at least one row and no required rows: `ATTACHMENT` is `OPTIONAL`, `attachmentRequired=false`.
  - at least one required row: `ATTACHMENT` is `REQUIRED`, `attachmentRequired=true`.

## Dashboard Behavior

- Dashboard uses one normalized readiness group: `ATTACHMENT`.
- Required attachment rows:
  - add `ATTACHMENT` to required section count.
  - create `REQUIRED_ATTACHMENT_MISSING` when active stored count is below `minCount`.
  - block `submittable`.
- Optional attachment rows:
  - `minCount=0`: guide-only; no optional incomplete issue.
  - `minCount>0` with no required attachment row on the posting: add one optional `ATTACHMENT` group and create `OPTIONAL_ATTACHMENT_MISSING` when active stored count is below `minCount`.
  - `minCount>0` with at least one required attachment row on the posting: no optional dashboard issue is created; the normalized required `ATTACHMENT` group is the only dashboard group for attachments.
  - never block submit.

## Submit Validator Behavior

- Submit validator loads only required attachment requirement rows.
- Missing required attachments throw `InvalidJobApplicationException`.
- Required rows are satisfied only by active stored metadata rows:
  - same application id.
  - same attachment type.
  - same section type.
  - `physicalFileStatus=STORED`.
  - `deletedAt == null`.
- `METADATA_ONLY`, `MISSING`, `DELETED`, wrong type, wrong section, and soft-deleted rows do not satisfy requirements.
- The validator does not inspect the filesystem. Storage health scan/repair remains the operational control for broken physical files.

## Security And Exposure Policy

- No credential, LDAP, DB password, encryption key, storage root, or internal path was added to source or documentation.
- Public attachment requirements expose only policy metadata needed by applicants.
- Admin responses expose requirement ids and posting ids for management workflows, but no applicant file metadata.
- Security configuration and authentication/authorization structure were not changed.

## Manual DB Notes

No migration framework is active in this repository. H2 tests rely on generated schema. Before applying this phase to a persistent MariaDB database, create the requirement table through the deployment process.

Recommended DDL candidate:

```sql
CREATE TABLE job_posting_attachment_requirement (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_posting_id BIGINT NOT NULL,
    attachment_type VARCHAR(50) NOT NULL,
    section_type VARCHAR(50) NOT NULL,
    required BOOLEAN NOT NULL,
    min_count INT NOT NULL,
    sort_order INT NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_job_posting_attachment_requirement_posting
        FOREIGN KEY (job_posting_id) REFERENCES job_posting(id),
    CONSTRAINT uk_job_posting_attachment_requirement_type_section
        UNIQUE (job_posting_id, attachment_type, section_type)
);

CREATE INDEX idx_attachment_requirement_posting
    ON job_posting_attachment_requirement (job_posting_id);

CREATE INDEX idx_attachment_requirement_required
    ON job_posting_attachment_requirement (job_posting_id, required);
```

The Java implementation enforces duplicate rows at service level because no migration file was added in this phase.

## Test Coverage

- Admin service:
  - replace-all create/update behavior.
  - null request as empty replace.
  - normalization/default values.
  - duplicate row rejection.
  - invalid row rejection.
  - non-DRAFT mutation rejection.
- Admin controller:
  - GET returns configured rows.
  - POST replaces rows.
  - invalid request returns bad request.
  - published posting mutation returns bad request.
- Public policy:
  - list derives aggregate attachment policy without exposing full requirements.
  - detail exposes safe requirements.
  - detail/list policy maps no rows to `DISABLED`, optional-only rows to `OPTIONAL`, and required rows to `REQUIRED`.
- Dashboard:
  - required missing attachment blocks submit.
  - matching stored row completes required group.
  - wrong type, wrong section, and deleted row do not satisfy a requirement.
  - mixed required and optional attachment requirements do not report optional missing issues when the required group is complete.
  - optional `minCount=0` creates no optional issue.
  - optional `minCount>0` creates non-blocking optional issue.
- Submit validator:
  - required missing attachment fails submit.
  - matching stored row allows submit.
  - wrong type, wrong section, deleted row, and insufficient count fail submit.
  - optional rows do not block submit.

## Test Commands

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*AttachmentRequirement*" --tests com.shinyoung.recruit.service.ApplicationSubmitValidatorTest --tests com.shinyoung.recruit.service.ApplicationDashboardServiceTest --tests com.shinyoung.recruit.service.JobPostingPublicServiceTest --tests com.shinyoung.recruit.controller.JobPostingPublicControllerTest --no-daemon
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*ApplicationAttachment*" --tests "*AttachmentStorage*" --no-daemon
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test --no-daemon
```

## Test Results

- Initial non-escalated Gradle run failed because the wrapper attempted to download Gradle and network access was sandbox-blocked.
- First escalated target run compiled the code but failed 8 newly added dashboard/submit tests because test helpers created Mockito mocks while another stubbing call was unfinished.
- The helper issue was fixed by using entity factory methods for attachment test rows.
- Target Phase 03i-5-2 test command result: `BUILD SUCCESSFUL`.
- Existing attachment upload/download/delete/storage regression command result: `BUILD SUCCESSFUL`.
- Full verification command result: `BUILD SUCCESSFUL` in 12m 49s.
- Review fix verification command result: `BUILD SUCCESSFUL` for `ApplicationDashboardServiceTest`.
- Review fix target Phase 03i-5-2 command rerun result: `BUILD SUCCESSFUL` in 2m 56s.

## Known Limitations

- Persistent database DDL remains manual for this phase.
- Public detail exposes all active policy rows for the posting, but row-level conditional requirements are not supported.
- Published posting attachment requirement edits remain blocked.
- Physical storage health is not checked during dashboard or submit validation.
- Optional attachment requirements are advisory only and do not affect final submit.
- When required and optional attachment rows are mixed, optional attachment guidance is not represented as a separate dashboard incomplete issue in this phase.

## Next Phase Recommendation

- Decide whether published postings need versioned attachment policy amendments.
- If row-level requirements become necessary, extend the model with `sectionRecordId` only after the target section domain stabilizes.
- Keep storage health scan/repair as the place that reconciles DB file status with the physical filesystem.
