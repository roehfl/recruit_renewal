# Phase 04a - Interview Scheduling Domain

## 1. Phase Summary

- Phase name: Phase 04a - Interview Scheduling Domain
- Date: 2026-05-26
- Purpose: add the domain foundation for interview schedules, interview groups, candidate assignments, and interviewer assignments.
- Status: completed for the domain/repository slice.

This slice adds only the interview scheduling domain model, repositories, and targeted tests. It intentionally does not add HTTP APIs, DTOs, services, service commands, calendar integration, messaging, or interview evaluation.

## 2. Implemented Scope

- Added `Interview` as the schedule/group root.
- Added `InterviewParticipant` as the participant assignment row for both candidates and interviewers.
- Added four interview-related enums.
- Added `InterviewRepository` and `InterviewParticipantRepository`.
- Added entity/domain validation tests.
- Added H2/JPA repository smoke tests.
- Ran targeted tests only, as instructed.

## 3. Out-of-Scope Items

- `InterviewService`
- `ApplicantInterviewService`
- `InterviewerInterviewService`
- `InterviewAdminController`
- `ApplicantInterviewController`
- `InterviewerInterviewController`
- request/response DTOs
- admin create/update/confirm/cancel APIs
- applicant read APIs
- interviewer read APIs
- `InterviewEvaluation`
- `StageResult` creation, update, announcement, or correction
- message sending
- SMS/Email/Alimtalk integration
- Excel upload/download
- PDF generation
- calendar integration
- frontend/static resources
- Flyway/Liquibase/migration file
- operating MariaDB DDL file

## 4. Changed Files

| File | Change |
| --- | --- |
| `src/main/java/com/shinyoung/recruit/enumeration/InterviewMethod.java` | Added interview method enum. |
| `src/main/java/com/shinyoung/recruit/enumeration/InterviewStatus.java` | Added interview lifecycle enum. |
| `src/main/java/com/shinyoung/recruit/enumeration/InterviewParticipantRole.java` | Added participant role enum. |
| `src/main/java/com/shinyoung/recruit/enumeration/InterviewParticipantStatus.java` | Added participant assignment status enum. |
| `src/main/java/com/shinyoung/recruit/domain/entity/Interview.java` | Added interview schedule/group entity. |
| `src/main/java/com/shinyoung/recruit/domain/entity/InterviewParticipant.java` | Added candidate/interviewer assignment entity. |
| `src/main/java/com/shinyoung/recruit/domain/repository/InterviewRepository.java` | Added interview query repository. |
| `src/main/java/com/shinyoung/recruit/domain/repository/InterviewParticipantRepository.java` | Added participant query repository. |
| `src/test/java/com/shinyoung/recruit/domain/entity/InterviewTest.java` | Added entity validation tests for `Interview`. |
| `src/test/java/com/shinyoung/recruit/domain/entity/InterviewParticipantTest.java` | Added entity validation tests for `InterviewParticipant`. |
| `src/test/java/com/shinyoung/recruit/domain/repository/InterviewRepositoryTest.java` | Added repository save/query tests for `Interview`. |
| `src/test/java/com/shinyoung/recruit/domain/repository/InterviewParticipantRepositoryTest.java` | Added repository save/query tests for `InterviewParticipant`. |
| `docs/codex/implementation/phase-04a-interview-scheduling-domain.md` | Added this implementation record. |
| `docs/codex/reports/phase-04a-interview-scheduling-domain.html` | Added human-readable phase report. |
| `docs/codex/06-implementation-roadmap.md` | Marked 04a as complete and 04b as the next slice. |
| `docs/codex/07-implementation-history.md` | Added Phase 04a implementation history. |
| `docs/codex/reports/current-implementation-status.html` | Updated current status with Phase 04a completion. |
| `docs/codex/design/phase-04-interview-scheduling-design.md` | Added implementation note for the 04a domain slice. |

## 5. New Classes

| Package | Class | Type | Responsibility |
| --- | --- | --- | --- |
| `com.shinyoung.recruit.enumeration` | `InterviewMethod` | Enum | Stores interview method: `IN_PERSON`, `ONLINE`, `HYBRID`, `OTHER`. |
| `com.shinyoung.recruit.enumeration` | `InterviewStatus` | Enum | Stores schedule status: `DRAFT`, `CONFIRMED`, `CANCELLED`. |
| `com.shinyoung.recruit.enumeration` | `InterviewParticipantRole` | Enum | Distinguishes `CANDIDATE` and `INTERVIEWER` participant rows. |
| `com.shinyoung.recruit.enumeration` | `InterviewParticipantStatus` | Enum | Stores participant assignment status: `ASSIGNED`, `CANCELLED`. |
| `com.shinyoung.recruit.domain.entity` | `Interview` | Entity | Represents one interview schedule/group for a posting and stage. |
| `com.shinyoung.recruit.domain.entity` | `InterviewParticipant` | Entity | Represents one candidate or interviewer assignment row. |
| `com.shinyoung.recruit.domain.repository` | `InterviewRepository` | Repository | Provides minimal schedule lookup methods for later service slices. |
| `com.shinyoung.recruit.domain.repository` | `InterviewParticipantRepository` | Repository | Provides participant lookup and duplicate-check candidates for later service slices. |
| `com.shinyoung.recruit.domain.entity` | `InterviewTest` | Test | Verifies `Interview` factory, state, and validation rules. |
| `com.shinyoung.recruit.domain.entity` | `InterviewParticipantTest` | Test | Verifies participant factories, role exclusivity, and cancel behavior. |
| `com.shinyoung.recruit.domain.repository` | `InterviewRepositoryTest` | Test | Verifies schedule persistence and repository query methods. |
| `com.shinyoung.recruit.domain.repository` | `InterviewParticipantRepositoryTest` | Test | Verifies participant persistence and repository query methods. |

## 6. Modified Classes

No existing Java source class was modified for this slice. `StageResult` source was not modified.

Existing documentation and status reports were updated to record the implementation.

## 7. Class-by-Class Explanation

### `InterviewMethod`

- Package: `com.shinyoung.recruit.enumeration`
- Type: Enum
- Responsibility: represents the interview method persisted by `Interview.method`.
- Values: `IN_PERSON`, `ONLINE`, `HYBRID`, `OTHER`
- Related classes: `Interview`
- Notes: persisted with `EnumType.STRING`.

### `InterviewStatus`

- Package: `com.shinyoung.recruit.enumeration`
- Type: Enum
- Responsibility: represents the interview schedule lifecycle.
- Values: `DRAFT`, `CONFIRMED`, `CANCELLED`
- Related classes: `Interview`, `InterviewRepository`
- Notes: 04a only adds simple domain state changes. Full confirmation guard is deferred to 04b service logic.

### `InterviewParticipantRole`

- Package: `com.shinyoung.recruit.enumeration`
- Type: Enum
- Responsibility: separates candidate rows from interviewer rows.
- Values: `CANDIDATE`, `INTERVIEWER`
- Related classes: `InterviewParticipant`, `InterviewParticipantRepository`
- Notes: repository duplicate-check methods include role so candidate and interviewer rows remain separate concepts.

### `InterviewParticipantStatus`

- Package: `com.shinyoung.recruit.enumeration`
- Type: Enum
- Responsibility: represents participant assignment lifecycle.
- Values: `ASSIGNED`, `CANCELLED`
- Related classes: `InterviewParticipant`, `InterviewParticipantRepository`
- Notes: first implementation primarily uses `ASSIGNED`; partial participant cancellation/amendment policy remains deferred.

### `Interview`

- Package: `com.shinyoung.recruit.domain.entity`
- Type: Entity
- Responsibility: schedule/group root for interview scheduling.
- Key fields:
  - `jobPosting`
  - `stage`
  - `groupName`
  - `startDateTime`
  - `endDateTime`
  - `method`
  - `locationName`
  - `roomName`
  - `onlineMeetingUrl`
  - `memo`
  - `status`
  - `participants`
- Key methods:
  - `createDraft(...)`
  - `updateDraft(...)`
  - `confirm()`
  - `cancel()`
  - `isDraft()`
  - `isConfirmed()`
  - `isCancelled()`
- Related classes:
  - `JobPosting`
  - `Stage`
  - `InterviewParticipant`
  - `InterviewMethod`
  - `InterviewStatus`
- Important implementation notes:
  - Extends `BaseEntity`.
  - Uses `@ManyToOne(fetch = FetchType.LAZY)` for `JobPosting` and `Stage`.
  - Uses `@OneToMany(mappedBy = "interview", cascade = CascadeType.ALL, orphanRemoval = true)` for participants.
  - Validates that `Stage.jobPosting` matches `Interview.jobPosting`.
  - Requires `groupName`, `startDateTime`, `endDateTime`, and `method`.
  - Requires `endDateTime` after `startDateTime`.
  - Requires `locationName` for `IN_PERSON` and `HYBRID`.
  - Requires `onlineMeetingUrl` for `ONLINE` and `HYBRID`.
  - Sets default status to `DRAFT`.

### `InterviewParticipant`

- Package: `com.shinyoung.recruit.domain.entity`
- Type: Entity
- Responsibility: one participant assignment row in an interview schedule.
- Key fields:
  - `interview`
  - `role`
  - `jobApplication`
  - `employee`
  - `participantStatus`
  - `sortOrder`
- Key methods:
  - `candidate(...)`
  - `interviewer(...)`
  - `cancel()`
  - `isAssigned()`
  - `isCandidate()`
  - `isInterviewer()`
- Related classes:
  - `Interview`
  - `JobApplication`
  - `Employee`
  - `InterviewParticipantRole`
  - `InterviewParticipantStatus`
- Important implementation notes:
  - Extends `BaseEntity`.
  - Candidate rows require `jobApplication` and must not have `employee`.
  - Interviewer rows require `employee` and must not have `jobApplication`.
  - Does not reference `User` directly.
  - Sets default participant status to `ASSIGNED`.
  - Adds itself to the owning `Interview.participants` collection.
  - Adds candidate/interviewer DB unique constraint candidates, but service-level duplicate validation is still required because nullable role-specific foreign keys can weaken DB uniqueness guarantees.

### `InterviewRepository`

- Package: `com.shinyoung.recruit.domain.repository`
- Type: Repository
- Responsibility: minimal interview schedule lookup for later admin/applicant/interviewer service slices.
- Key methods:
  - `findByJobPostingIdOrderByStartDateTimeAsc(Long jobPostingId)`
  - `findByJobPostingIdAndStageIdOrderByStartDateTimeAsc(Long jobPostingId, Long stageId)`
  - `findByJobPostingIdAndStatusOrderByStartDateTimeAsc(Long jobPostingId, InterviewStatus status)`
- Related classes:
  - `Interview`
  - `InterviewStatus`

### `InterviewParticipantRepository`

- Package: `com.shinyoung.recruit.domain.repository`
- Type: Repository
- Responsibility: participant lookup and duplicate-check candidates for later service validations.
- Key methods:
  - `findByInterviewIdOrderByRoleAscSortOrderAscIdAsc(Long interviewId)`
  - `existsByInterviewIdAndRoleAndJobApplicationId(...)`
  - `existsByInterviewIdAndRoleAndEmployeeId(...)`
  - `findByJobApplicationIdAndRoleAndParticipantStatus(...)`
  - `findByEmployeeIdAndRoleAndParticipantStatus(...)`
- Related classes:
  - `InterviewParticipant`
  - `InterviewParticipantRole`
  - `InterviewParticipantStatus`

## 8. API List

No API was added in Phase 04a.

The following API draft remains deferred to later slices:

| Future slice | API group |
| --- | --- |
| Phase 04b | admin schedule CRUD, participant replace, confirm, cancel |
| Phase 04c | applicant own interview schedule read |
| Phase 04d | interviewer own assignment read |

## 9. Entity Relationship Summary

```text
JobPosting 1 ── N Interview
Stage      1 ── N Interview

Interview 1 ── N InterviewParticipant

InterviewParticipant(CANDIDATE)   N ── 1 JobApplication
InterviewParticipant(INTERVIEWER) N ── 1 Employee
```

Rules:

- `Interview` belongs to exactly one `JobPosting`.
- `Interview` belongs to exactly one `Stage`.
- `Stage.jobPosting` must match `Interview.jobPosting`.
- `InterviewParticipant` belongs to exactly one `Interview`.
- Candidate participant rows require `JobApplication`.
- Interviewer participant rows require `Employee`.
- `InterviewParticipant` does not point directly to `User`.

## 10. Validation and Business Rules

Implemented in 04a:

- `Interview.jobPosting` is required.
- `Interview.stage` is required.
- `Interview.stage.jobPosting` must be the same posting as `Interview.jobPosting`.
- `Interview.groupName` is required and trimmed.
- `Interview.startDateTime` is required.
- `Interview.endDateTime` is required and must be after `startDateTime`.
- `Interview.method` is required.
- `IN_PERSON` and `HYBRID` require `locationName`.
- `ONLINE` and `HYBRID` require `onlineMeetingUrl`.
- `OTHER` does not force location or URL.
- New interviews default to `DRAFT`.
- `updateDraft(...)` only works while status is `DRAFT`.
- `confirm()` changes status to `CONFIRMED`.
- `cancel()` changes status to `CANCELLED`.
- Candidate participants require `JobApplication` and no `Employee`.
- Interviewer participants require `Employee` and no `JobApplication`.
- New participants default to `ASSIGNED`.
- `InterviewParticipant.cancel()` changes participant status to `CANCELLED`.

Deferred to 04b or later:

- `StageType` allowlist enforcement.
- `StageStatus` guard enforcement.
- minimum one candidate and one interviewer before confirmation.
- candidate same-posting validation beyond the `Interview.stage` relation.
- candidate eligibility against submitted applications and previous `StageResult`.
- applicant-visible result timing guard.
- duplicate assignment service guard.
- confirmed schedule time collision guard.
- admin API authorization.
- applicant/interviewer schedule visibility.

## 11. StageType Check

Actual source check on 2026-05-26:

```java
public enum StageType {
    DOCUMENT,
    FIRST_INTERVIEW,
    SECOND_INTERVIEW,
    FINAL_INTERVIEW,
    ETC
}
```

Phase 04a did not modify `StageType`. The later service slice should allow only:

- `FIRST_INTERVIEW`
- `SECOND_INTERVIEW`
- `FINAL_INTERVIEW`

The non-interview values `DOCUMENT` and `ETC` must remain invalid for interview schedule creation/confirmation once 04b service validation is implemented.

## 12. StageResult Non-Mutation Check

Phase 04a did not modify:

- `StageResult`
- `StageResultRepository`
- `StageResultService`
- `StageResultController`
- `StageResultCorrectionHistory`

Policy retained:

```text
Interview Scheduling does not mutate StageResult.
Candidate eligibility and result visibility checks will be enforced in service/API slices before confirmation or applicant exposure.
```

## 13. Test Coverage

Added tests:

- `InterviewTest`
- `InterviewParticipantTest`
- `InterviewRepositoryTest`
- `InterviewParticipantRepositoryTest`

Targeted test commands executed:

```powershell
$env:AES_SECRET_KEY="22791194512954214612461221261067"; .\gradlew.bat test --tests com.shinyoung.recruit.domain.entity.InterviewTest --tests com.shinyoung.recruit.domain.entity.InterviewParticipantTest --no-daemon
```

Result:

- `BUILD SUCCESSFUL`

```powershell
$env:AES_SECRET_KEY="22791194512954214612461221261067"; .\gradlew.bat test --tests com.shinyoung.recruit.domain.repository.InterviewRepositoryTest --tests com.shinyoung.recruit.domain.repository.InterviewParticipantRepositoryTest --no-daemon
```

Result:

- `BUILD SUCCESSFUL`

Minimal related regression command executed:

```powershell
$env:AES_SECRET_KEY="22791194512954214612461221261067"; .\gradlew.bat test --tests com.shinyoung.recruit.service.StageServiceTest --tests com.shinyoung.recruit.service.StageResultServiceTest --tests com.shinyoung.recruit.service.JobApplicationServiceTest --no-daemon
```

Result:

- `BUILD SUCCESSFUL`

Full test status:

- Full `.\gradlew.bat test` was not executed.
- Full `.\gradlew.bat clean test` was not executed.
- Reason: `instruction.md` explicitly forbids full test runs for this slice because the development PC can time out on the full suite. Only changed-package targeted tests and minimal related regression tests were run.

Environment note:

- Initial non-escalated Gradle attempts failed because the wrapper attempted to download Gradle inside the sandbox and network access was denied.
- The targeted commands above were then run with approved network access for the Gradle wrapper distribution.

## 14. Manual DDL / Operating DB Note

No migration file was created in Phase 04a.

The H2 test schema is generated from JPA metadata. Persistent MariaDB environments need manual schema application through the deployment process for:

- `interview`
- `interview_participant`
- indexes on schedule posting/stage/status and time range.
- indexes on participant interview, candidate application, and interviewer employee.
- candidate/interviewer uniqueness guard candidates.

Do not apply this domain change to an operating persistent database without coordinating the schema creation separately.

## 15. Known Limitations

- No service-level confirmation validation exists yet.
- No API exists yet.
- No DTO exists yet.
- No applicant or interviewer visibility logic exists yet.
- No time collision validation exists yet.
- No previous-stage `StageResult` eligibility check exists yet.
- No migration file exists.

## 16. Remaining Issues

- Decide exact admin/interviewer authorization names in the service/API slices.
- Implement service-level duplicate assignment checks even though DB unique constraint candidates exist.
- Implement confirmed-schedule collision checks using the design overlap rule.
- Decide whether confirmed schedule amendment remains cancel/recreate only.
- Add applicant and interviewer response models without leaking admin memo or other applicants to applicants.

## 17. Next Phase Recommendation

Proceed with `Phase 04b - Admin Interview Schedule Management`.

Recommended 04b scope:

- add admin service for schedule create/update/read.
- add participant replace command for DRAFT schedules.
- add confirm/cancel service commands.
- enforce `StageType` allowlist.
- enforce `StageStatus` guards.
- enforce minimum assigned candidate/interviewer rules.
- enforce same posting, submitted application, previous-stage pass/result visibility checks.
- enforce duplicate assignment and confirmed-schedule collision checks.
- keep `StageResult` non-mutating.
