# Phase 09 — PII Field Inventory (파기 필드 분류) — 9d 선행 산출물

> instruction.md 리뷰 #1(Blocker) 반영. 본 문서는 **9d(purge execute) 착수 전 반드시 확정**되어야 하는 선행 산출물이다. 이 분류표가 없으면 "파기했다고 표시했지만 실제 PII 가 남는" 상태가 된다.
> 근거 스캔: 2026-06-04, 실제 엔티티 소스 기준(필드명·nullable·unique·length 확인). 관련: ADR-0005(파기 방식), 설계 §5.2 / §7.2.

## 1. 분류 taxonomy

| 코드 | 의미 | 처리 |
| --- | --- | --- |
| `KEEP_TOMBSTONE` | 비식별, 통계/감사 연결에 필요 | 보존 |
| `NULLIFY` | PII/재식별 가능, **nullable=true** | `null` 처리 |
| `PLACEHOLDER` | PII/재식별 가능, **NOT NULL(String)** | `"__PURGED__"` 비식별 치환 |
| `ALTER_NULLABLE+NULLIFY` | PII/재식별 가능, **NOT NULL(Date/숫자)** — placeholder 불가 | 수동 DDL 로 nullable 화 후 `null` |
| `HASH_ONLY` | 원문 제거 후 HMAC/hash 만 보존 | 원문 삭제, hash 유지 |
| `RETAIN_UNTIL_REF0` | Applicant 공통 PII | 해당 Applicant 의 **모든** 지원서 파기 후에만 처리 |
| `KEEP→BINARY_SAGA` | 첨부 파일 식별/경로 | 바이너리 saga(9d-2)에서 처리 |

**기본 규칙**: nullable PII → `NULLIFY`. NOT NULL String PII → `PLACEHOLDER`(기본) 또는 ALTER nullable. NOT NULL Date/숫자 PII → **placeholder 불가** → `ALTER_NULLABLE+NULLIFY`. ids/FK/enum/coded status/sortOrder/비PII snapshot/`createdAt`/`updatedAt` → `KEEP_TOMBSTONE`.

## 2. Applicant / User (RETAIN_UNTIL_REF0 게이트)

> Applicant 공통 PII 는 그 Applicant 의 **모든 JobApplication 이 파기 대상이 된 시점에만** 처리(ref-count). 일부 지원서만 파기 시 보존.

| 엔티티.필드 | nullable | unique | 분류 | 처리 |
| --- | --- | --- | --- | --- |
| `User.id` | false | true | KEEP_TOMBSTONE | 보존 |
| `User.loginId` | true | false | RETAIN_UNTIL_REF0 → NULLIFY | ref0 후 null |
| `User.name` | true | false | RETAIN_UNTIL_REF0 → NULLIFY | ref0 후 null(지원자 실명) |
| `Applicant.email` | true | true | RETAIN_UNTIL_REF0 → NULLIFY | ref0 후 null |
| `Applicant.userName` | true | false | RETAIN_UNTIL_REF0 → NULLIFY | ref0 후 null |
| `Applicant.password` | true | false | RETAIN_UNTIL_REF0 → NULLIFY | ref0 후 null |
| `Applicant.phoneNumber` | true | false | RETAIN_UNTIL_REF0 → NULLIFY | ref0 후 null |
| `Applicant.ci` (AES @Convert) | true | false | RETAIN_UNTIL_REF0 → **NULLIFY** | ref0 후 `null`(암호문 삭제) |
| `Applicant.ciHash` | **false** | **true** | RETAIN_UNTIL_REF0 → **OVERWRITE(sentinel)** | `"PURGED:" + UUID` 로 덮어씀(NOT NULL·unique 유지). **보존 금지** — 아래 ⚠ |
| `Employee.deptName` | true | true | KEEP_TOMBSTONE | 임직원·조직정보(지원자 파기 대상 아님) |

**⚠ ciHash Blocker (리뷰 2차 #1) — 보존 금지로 정정**: 1차 인벤토리는 ciHash 를 "HMAC 단방향, 보존" 으로 적었으나 **실제 코드와 충돌**한다.
- `HashUtil.sha256()` 은 **plain `SHA-256`**(HMAC/salt/pepper 없음, `ApplicantRefHash` 의 HMAC+pepper 와 다름). 즉 `ciHash = SHA-256(ci)` 는 CI 로부터 **결정적으로 재계산 가능한 연결자**다.
- `ApplicantSignUpService` 가 `existsByCiHash(HashUtil.sha256(ci))` 로 **중복 가입을 차단**한다.
- 따라서 ref0 파기 후 ci/name/email/loginId/password 를 지워도 ciHash 를 그대로 두면 → 실제 CI 기반 연결자 잔존 + 동일인 재지원 영구 차단 = **"비가역 파기"가 아님**. **Blocker**.
- **확정(권장안 A)**: ref0 파기 시 `ci = null`, **`ciHash = "PURGED:" + UUID`**(random)로 덮어쓴다 — NOT NULL·unique 제약 유지하면서 CI 연결 단절. (대안 B: ciHash 컬럼 nullable DDL 후 null.) **결과**: 파기된 사람은 같은 CI 로 재가입 가능해진다 — 중복가입 차단보다 **파기 우선**(Phase 9 방침).
- `applicantRefHash`(ActivityLog 의 감사 연결용, HMAC-SHA256+pepper, 신규)와 혼동 금지 — 그건 가명 유지가 목적이고 ciHash 와 별개다.

## 3. JobApplication / ApplicationAnswer

| 엔티티.필드 | nullable | 분류 | 처리 |
| --- | --- | --- | --- |
| `JobApplication.id` / `jobPosting` / `jobPosition` | - | KEEP_TOMBSTONE | 보존(통계/감사) |
| `JobApplication.applicant`(FK) | false | RETAIN_UNTIL_REF0 | Applicant 파기와 연동 |
| `JobApplication.status` / `submittedAt` / `withdrawnAt` | - | KEEP_TOMBSTONE | 보존(funnel/terminal) |
| `JobApplication.applicantNameSnapshot` | **false** | **PLACEHOLDER** | `"__PURGED__"`(NOT NULL String, 지원자 실명 snapshot) |
| `JobApplication.jobPostingTitleSnapshot` | false | KEEP_TOMBSTONE | 비PII(공고명) |
| `JobApplication.jobPositionNameSnapshot` | false | KEEP_TOMBSTONE | 비PII(분야명) |
| `ApplicationAnswer.answerText` | true (len 5000) | **NULLIFY** | 자유서술 — null |
| `ApplicationAnswer.*Snapshot`(question/category/type/required/min/max/sort) | - | KEEP_TOMBSTONE | 비PII 질문 메타 |
| (공통) `createdBy` / `updatedBy` | true | **NULLIFY** | 지원자 loginId 가능 → null. ⚠ `createdBy` 는 `@Column(updatable=false)` 라 JPA dirty-update 로 안 지워짐 → **JPQL bulk update / native update** 로 명시 클리어 |
| (공통) `createdAt` / `updatedAt` | - | KEEP_TOMBSTONE | 보존 |

## 4. 학력/경력 (Education / Career)

| 엔티티.필드 | nullable | 분류 | 처리 |
| --- | --- | --- | --- |
| `ApplicationEducation.educationLevel` / `graduationStatus` / `dayNightType` / `campusType` / `transfer` / `sortOrder` | - | KEEP_TOMBSTONE | 비PII enum/coded(funnel) |
| `ApplicationEducation.schoolId`(soft link) | true | KEEP_TOMBSTONE | **SCHOOL funnel 의 비식별 grouping 키** — 보존 |
| `ApplicationEducation.schoolName` | **false** | **PLACEHOLDER** | `"__PURGED__"`(free-text, schoolId 로 통계 대체) |
| `ApplicationEducation.majorName` / `degreeName` / `countryCode` | true | NULLIFY | null |
| `ApplicationEducation.admissionDate` / `graduationDate` | true | **NULLIFY (안 A 확정)** | 정확 날짜 **보존 금지**. **안 A 채택(리뷰 3차 #3)**: 전부 `null`, 일반화 컬럼 추가 없음 — 현 funnel 통계(jobPosition/schoolId/certificate/stage)가 입학·졸업일을 안 쓰므로 통계 손실 0. (졸업연도 코호트 통계가 향후 필요하면 안 B로 전환: `graduationYear` 등 nullable 컬럼.) |
| `ApplicationEducationSemesterGrade.*`(year/semester/credits/gpa…) | - | KEEP_TOMBSTONE | 비식별 학업 metric(schoolName/major 소거 후 단독 비식별) |
| `ApplicationCareer.companyName` | **false** | **PLACEHOLDER** | `"__PURGED__"` |
| `ApplicationCareer.departmentName` / `positionTitle` / `responsibilities` / `resignationReason` | true | NULLIFY | null(자유서술 포함) |
| `ApplicationCareer.employmentType` / `currentlyEmployed` / `sortOrder` | - | KEEP_TOMBSTONE | 비PII |
| `ApplicationCareer.startDate` / `endDate` | false/true | **NULLIFY (안 A 확정)** | 정확 날짜 **보존 금지**. **안 A**: 전부 null(`startDate` NOT NULL → ALTER nullable). 일반화/근속개월 컬럼 추가 없음. (안 B 선택 시 `careerStartYearMonth`/`careerEndYearMonth` varchar(7)·`careerDurationMonths` int 추가.) |
| `ApplicationCareerProfile.careerType` | false | KEEP_TOMBSTONE | 비PII enum(funnel) |

## 5. 자격/어학/병역/수상/공백 (상세 PII 묶음)

| 엔티티.필드 | nullable | 분류 | 처리 |
| --- | --- | --- | --- |
| `ApplicationCertificate.certificateName` / `issuingOrganization` | **false** | **PLACEHOLDER** | `"__PURGED__"` |
| `ApplicationCertificate.acquiredDate` | **false** | **ALTER_NULLABLE+NULLIFY** | date NOT NULL → DDL nullable 후 null |
| `ApplicationCertificate.certificateNumber` | true | **HASH_ONLY** | 원문 삭제, HMAC 만(CERT dedup) |
| `ApplicationCertificate.expiredDate` / `scoreOrGrade` | true | NULLIFY | null |
| `ApplicationLanguage.languageName` / `testName` | **false** | **PLACEHOLDER** | `"__PURGED__"` |
| `ApplicationLanguage.examDate` | **false** | **ALTER_NULLABLE+NULLIFY** | DDL nullable 후 null |
| `ApplicationLanguage.score` / `grade` / `expiredDate` / `issuingOrganization` | true | NULLIFY | null |
| `ApplicationMilitary.militarySubjectType` / `serviceType` / `militaryBranch` / `rank` | - | KEEP_TOMBSTONE | 비PII enum |
| `ApplicationMilitary.serviceStartDate` / `serviceEndDate` | true | NULLIFY | null(복무 timeline 재식별) |
| `ApplicationMilitary.exemptionReason` | true (len 1000) | NULLIFY | null(민감) |
| `ApplicationAward.awardName` / `awardingOrganization` | **false** | **PLACEHOLDER** | `"__PURGED__"` |
| `ApplicationAward.awardDate` | **false** | **ALTER_NULLABLE+NULLIFY** | DDL nullable 후 null |
| `ApplicationAward.description` | true (len 2000) | NULLIFY | null |
| `ApplicationGapPeriod.startDate` / `endDate` | **false** | **ALTER_NULLABLE+NULLIFY** | DDL nullable 후 null |
| `ApplicationGapPeriod.reason` | **false** | **PLACEHOLDER** | `"__PURGED__"`(민감 사유) |
| `ApplicationGapPeriod.description` | true (len 2000) | NULLIFY | null |
| `ApplicationGapPeriod.gapType` | false | KEEP_TOMBSTONE | 비PII enum |
| (공통) certificate/language/military/award/gap `sortOrder`,`id`,`jobApplication`(FK) | - | KEEP_TOMBSTONE | 보존 |

## 6. 첨부 (ApplicationAttachment) — 9d-2 바이너리 saga 연동

| 필드 | nullable | 분류 | 처리 |
| --- | --- | --- | --- |
| `id` / `jobApplication` / `attachmentType` / `sectionType` / `sectionRecordId` / `contentType` / `fileSize` / `sortOrder` | - | KEEP_TOMBSTONE | 비PII 메타(보존) |
| `originalFileName` | **false** | **PLACEHOLDER** | `"__PURGED__"`(파일명에 실명 가능) |
| `storedFileName` | false | KEEP→BINARY_SAGA | 시스템 난수명. 바이너리 소멸 후 보존 가능(비식별) |
| `storagePath` | **false (len 1000)** | **ALTER_NULLABLE+NULLIFY** | 삭제 전 필요, **최종 소멸 후 null**. NOT NULL → DDL nullable |
| `filenameHash` (**신규**) | - | 신규 | 필요 시 HMAC 만 |
| `physicalFileStatus` | false | KEEP_TOMBSTONE | saga 상태(아래 §8) |
| `deletedAt` / `binaryDeletedAt`(**신규**) | true | KEEP_TOMBSTONE | 시점 보존 |
| `deletedBy` / `deletionReason` | true | NULLIFY | null(행위자/사유 PII 가능) |
| `deletedByType` | true | KEEP_TOMBSTONE | 비PII enum |
| (공통) `createdBy` / `updatedBy` | true | NULLIFY | 지원자 loginId 가능 |

## 7. 면접/평가 (Interview / Participant / Evaluation)

> 파기 단위 = JobApplication. **Interview 행은 group 단위로 여러 후보가 공유**하므로 per-application 파기가 Interview-level 자유텍스트(memo/location)를 건드리면 안 된다(타 후보 영향). per-candidate 데이터만 처리.

| 엔티티.필드 | nullable | 분류 | 처리 |
| --- | --- | --- | --- |
| `InterviewEvaluation.comment` | true (len 2000) | **NULLIFY** | 후보 평가 자유서술 — per-candidate PII, null |
| `InterviewEvaluation.grade` / `recommendation` / `status` / `submittedAt` / FK들 | - | KEEP_TOMBSTONE | 비PII 평가 결과/연결 |
| `InterviewParticipant.*`(role/status/FK/sortOrder) | - | KEEP_TOMBSTONE | per-candidate 자유텍스트 없음 |
| `Interview.memo` / `locationName` / `roomName` / `onlineMeetingUrl` | true | ⚠ **OUT (interview-level)** | per-application 파기 **대상 아님**(공유 행). memo 가 후보 실명 포함 시 잔존 PII — **별도 검토 필요 항목**으로 flag |
| `Interview.groupName` | false | OUT (interview-level) | 공유, 파기 단위 밖 |

### 7-1. 전형 결과 자유서술 (StageResult / StageResultCorrectionHistory) — 9d-1 리뷰 Major 1 로 추가 분류

> 1차 인벤토리 누락분. 9d-1 구현 리뷰에서 "PURGED marker 전 미소거" Blocker 로 지적되어 분류를 확정하고 9d-1 에서 즉시 구현했다.

| 엔티티.필드 | nullable | 분류 | 처리 |
| --- | --- | --- | --- |
| `StageResult.comment` | true (len 2000) | **NULLIFY** | 관리자 자유서술(지원자 실명/연락처 유입 가능) — null |
| `StageResult.resultStatus` / `score` / `decidedAt` / `decidedBy`(직원) / FK | - | KEEP_TOMBSTONE | funnel/증적 |
| `StageResultCorrectionHistory.reason` | **false (len 1000)** | **PLACEHOLDER** | `"__PURGED__"`(자유입력 정정 사유) |
| `StageResultCorrectionHistory.previousComment` / `newComment` | true (len 2000) | **NULLIFY** | comment 전후 스냅샷 — null |
| `StageResultCorrectionHistory.correctedBy`(직원) / 상태·점수·decidedAt 스냅샷 | - | KEEP_TOMBSTONE | 정정 사실 자체는 증적 보존 |
| (공통) `createdBy` / `updatedBy` | true | NULLIFY | §3 공통 규칙 |

**잔존 위험 flag + 운영 가이드(리뷰 3차 #5)**: `Interview.memo`/`locationName`/`roomName`/`onlineMeetingUrl` 는 group 공유 행이라 per-application 파기로 비우지 않는다(타 후보 영향). 운영상 후보 실명이 memo 에 들어가면 파기 후 잔존 → Phase 9 범위 밖으로 인정하되 **운영 가이드를 명시**한다:

> **운영 가이드**: `Interview.memo` 에는 후보 **실명·전화번호·이메일·평가성 자유서술을 입력하지 않는다**. 후보별 메모/평가는 **`InterviewEvaluation.comment` 로만** 남기고, 그 comment 는 파기 시 `NULLIFY` 된다.

"interview-level 자유텍스트 정리"는 별도 후속.

## 8. PhysicalFileStatus 재정의 (리뷰 #4)

현재: `METADATA_ONLY, STORED, MISSING, DELETED`. `markDeleted()` 가 `DELETED` 세팅(soft-delete). purge saga 와 의미 충돌.

**확정 재정의**:
```
METADATA_ONLY        : 파일 미업로드, metadata 만
STORED               : 파일 존재
MISSING              : DB 는 있으나 파일 없음
SOFT_DELETED         : (기존 DELETED 개명) 사용자/관리자 soft delete
BINARY_DELETE_PENDING: 파기 saga ① 후, 물리 삭제 대기
BINARY_DELETED       : 파기 saga 물리 소멸 확인 완료
BINARY_DELETE_FAILED : 물리 삭제 실패(재시도/ reconciliation 대상)
```
- **3단계 안전 마이그레이션(리뷰 3차 #1 — 한 번에 rename 시 운영 DB `'DELETED'` row enum 매핑 오류)**:
  - **1단계(9d-2)**: enum 에 `DELETED` + `SOFT_DELETED` **둘 다 유지**. `markDeleted()` → `SOFT_DELETED`. health scan 은 `DELETED`·`SOFT_DELETED` **둘 다 soft-deleted 로 취급**.
  - **2단계**: 수동 DDL `UPDATE … SET physical_file_status='SOFT_DELETED' WHERE physical_file_status='DELETED'` + 테스트로 `DELETED` 잔존 **0건 확인**.
  - **3단계(후속 phase)**: `DELETED` enum 값 제거.
- purge 는 `BINARY_DELETE_*` 만 사용 → soft-delete 와 의미 분리.

## 9. DDL 영향 요약 (수동 DDL 필요)

**ALTER → nullable (NOT NULL date PII)**: `ApplicationCertificate.acquiredDate`, `ApplicationLanguage.examDate`, `ApplicationAward.awardDate`, `ApplicationGapPeriod.startDate`/`endDate`, `ApplicationCareer.startDate`(null 위해), `ApplicationAttachment.storagePath`. (education admission/graduation 은 이미 nullable → null.) **안 A 확정: 일반화용 신규 컬럼 추가 없음**(graduationYear/careerDurationMonths 등은 안 B 선택 시에만).

**ciHash overwrite(권장안 A, DDL 불요)**: ref0 파기 시 `ciHash = "PURGED:"+UUID`. 대안 B = ciHash unique+nullable DDL 후 null(H2/MariaDB unique-null 동작 확인 필요). **plain SHA-256 이라 보존 금지** — 위 §2 ⚠.

**PLACEHOLDER 유지(NOT NULL String, DDL 불요)**: `applicantNameSnapshot`, `schoolName`, `companyName`, `certificateName`, `issuingOrganization`, `languageName`, `testName`, `awardName`, `awardingOrganization`, `gapPeriod.reason`, `originalFileName`. (원하면 이들도 ALTER nullable+NULLIFY 로 전환 가능 — 컬럼별 선택지로 문서화.)

**신규 컬럼**: `ApplicationAttachment.filenameHash`, `ApplicationAttachment.binaryDeletedAt`; (설계 §7.2 의 JobApplication/JobPosting/신규 5테이블과 합산).

**enum 마이그레이션**: `PhysicalFileStatus` `'DELETED'` → `'SOFT_DELETED'` 데이터 UPDATE.

**`@Column(updatable=false)` 회피**: `createdBy` 는 JPQL/native bulk update 로만 클리어 가능(엔티티 dirty-update 안 됨).

## 10. ApplicationBasicInfo (Phase 10 추가)

> Phase 10(기본정보 섹션) 구현 완료 후 추가. 근거 스캔: 2026-06-12, 실제 엔티티 소스 기준.

**핵심 정책**: `AesCryptoUtil` 랜덤 IV(비결정적) → 암호화 컬럼에 `'__PURGED__'` 평문 치환 불가(복호화 시 깨짐). 전 PII 컬럼 `null` 파기. 파기 marker는 `JobApplication`이 보유, `application_basic_info` 행은 빈 shell로 보존.

| 엔티티.필드 | nullable | 암호화 | 분류 | 처리 |
| --- | --- | :---: | --- | --- |
| `ApplicationBasicInfo.id` | false (PK) | - | KEEP_TOMBSTONE | 보존 |
| `ApplicationBasicInfo.jobApplication` (FK) | false (UNIQUE) | - | KEEP_TOMBSTONE | 보존 |
| `ApplicationBasicInfo.nameKorean` | true | ✔ AES | NULLIFY | null |
| `ApplicationBasicInfo.nameEnglish` | true | ✔ AES | NULLIFY | null |
| `ApplicationBasicInfo.nationalityType` | true | - | NULLIFY | null (enum) |
| `ApplicationBasicInfo.countryCode` | true | ✔ AES | NULLIFY | null |
| `ApplicationBasicInfo.birthDate` | true | - | NULLIFY | null (LocalDate) |
| `ApplicationBasicInfo.mobilePhone` | true | ✔ AES | NULLIFY | null |
| `ApplicationBasicInfo.emergencyPhone` | true | ✔ AES | NULLIFY | null |
| `ApplicationBasicInfo.email` | true | ✔ AES | NULLIFY | null |
| `ApplicationBasicInfo.veteranStatus` | true | - | NULLIFY | null (enum) |
| `ApplicationBasicInfo.disabilityStatus` | true | - | NULLIFY | null (enum, 민감정보) |
| `ApplicationBasicInfo.disabilityGradeCode` | true | ✔ AES | NULLIFY | null (민감정보) |
| `ApplicationBasicInfo.disabilityTypeCode` | true | ✔ AES | NULLIFY | null (민감정보) |
| `ApplicationBasicInfo.zipCode` | true | ✔ AES | NULLIFY | null |
| `ApplicationBasicInfo.addressBasic` | true | ✔ AES | NULLIFY | null |
| `ApplicationBasicInfo.addressDetail` | true | ✔ AES | NULLIFY | null |
| `ApplicationBasicInfo.createdBy` | true | - | NULLIFY | null (JPQL bulk update — `@Column(updatable=false)` 우회) |
| `ApplicationBasicInfo.updatedBy` | true | - | NULLIFY | null |
| `ApplicationBasicInfo.createdAt` | false | - | KEEP_TOMBSTONE | 보존 |
| `ApplicationBasicInfo.updatedAt` | false | - | KEEP_TOMBSTONE | 보존 |

**특이사항**:
- 전 필드 nullable(DB NOT NULL은 FK만) → `PLACEHOLDER` 없음. DDL 변경 불필요.
- 암호화 컬럼은 AES 랜덤 IV 특성상 `'__PURGED__'` 치환 불가 → `NULLIFY`만.
- 파기 쿼리: `ApplicationPiiPurgeRepository.purgeBasicInfo(@Modifying flushAutomatically=true)` JPQL bulk update.

## 12. 판단 보류 / 확인 필요 항목

- **날짜 일반화 — 안 A 확정(리뷰 3차 #3)**: education(admission/graduation)·career(start/end) 정확 날짜는 **전부 NULLIFY, 일반화 컬럼 추가 없음**. 근거: 현 funnel 통계가 이 날짜들을 안 쓴다 → 통계 손실 0. 가역적(향후 졸업연도/근속 통계 필요 시 안 B 의 nullable 컬럼 추가). "통계 편의" 보다 "재식별 제거" 우선.
- **`ciHash` — 해소(리뷰 2차 #1)**: 보존 금지. ref0 파기 시 `"PURGED:"+UUID` 로 overwrite(권장안 A). 중복가입 차단은 파기 후 미보장(파기 우선).
- **PLACEHOLDER vs ALTER nullable 일괄 정책**: NOT NULL String PII 를 placeholder(기본)로 둘지, 전부 ALTER nullable+NULLIFY 로 통일할지 — 운영 DB DDL 부담과 trade-off. 기본은 placeholder. **확인 필요(낮음).**
- **semesterGrade + schoolId 결합(리뷰 2차 #8)**: `ApplicationEducationSemesterGrade.schoolYear`/gpa 등은 단독 비식별이나 `schoolId`(보존) + 학년/학점 조합 시 좁은 코호트 재식별 가능성. 정확 날짜를 이미 일반화하므로 위험은 낮으나, 필요 시 schoolId 와 결합되는 grade 상세도 generalize 검토. **확인 필요(낮음).**
- **`Interview.memo` 잔존**: §7 flag — Phase 9 범위 밖(group 공유 행). 운영 가이드(실명 금지) 또는 후속 정리. **확인 필요.**
- **StageResult comment 계열 — 해소(9d-1 리뷰 Major 1)**: §7-1 로 분류 확정 + 9d-1 tombstone 에 포함 구현.

## 13. 9d 착수 게이트

본 인벤토리(특히 §9 DDL 목록, §10 확인 항목)가 **확정**되어야 9d-1/9d-2 구현을 시작한다. ADR-0005 는 본 인벤토리 확정 전까지 `accepted-with-implementation-gate`.
