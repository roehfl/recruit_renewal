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
| `Applicant.ci` (AES @Convert) | true | false | RETAIN_UNTIL_REF0 → **HASH_ONLY** | ref0 후 암호문 삭제(ciHash 유지) |
| `Applicant.ciHash` | **false** | **true** | **HASH_ONLY (보존)** | null 불가(NOT NULL·unique). 단방향 hash·가명 식별자라 보존 허용. 재등록 dedup 용도 |
| `Employee.deptName` | true | true | KEEP_TOMBSTONE | 임직원·조직정보(지원자 파기 대상 아님) |

**주의**: `ciHash` 는 NOT NULL·unique 제약으로 null/placeholder 불가. HMAC 단방향이라 원문 복원 불가 → 가명 식별자로 보존(파기 인정에 영향 없음). 단 "완전 삭제" 정책이 추후 요구되면 ciHash 컬럼 제약(ALTER) + dedup 정책 재설계가 별도로 필요(후속).

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
| `ApplicationEducation.admissionDate` / `graduationDate` | true | KEEP_TOMBSTONE | 코호트 분석용(연/분기 bucket) 보존 — 잔존위험 수용 |
| `ApplicationEducationSemesterGrade.*`(year/semester/credits/gpa…) | - | KEEP_TOMBSTONE | 비식별 학업 metric(schoolName/major 소거 후 단독 비식별) |
| `ApplicationCareer.companyName` | **false** | **PLACEHOLDER** | `"__PURGED__"` |
| `ApplicationCareer.departmentName` / `positionTitle` / `responsibilities` / `resignationReason` | true | NULLIFY | null(자유서술 포함) |
| `ApplicationCareer.employmentType` / `currentlyEmployed` / `sortOrder` | - | KEEP_TOMBSTONE | 비PII |
| `ApplicationCareer.startDate` / `endDate` | false/true | KEEP_TOMBSTONE | 경력기간 분석용 보존(bucket) — 잔존위험 수용 |
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

**잔존 위험 flag**: `Interview.memo`(자유서술)는 group 공유라 per-application 파기로 비우지 않는다. 운영상 후보 실명이 memo 에 들어가면 파기 후에도 잔존한다. → "interview-level 자유텍스트 정리"는 별도 후속(또는 운영 가이드로 memo 에 실명 금지) 으로 분리. 본 Phase 9 범위 밖임을 명시.

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
- 기존 `DELETED` → `SOFT_DELETED` 개명. `markDeleted()` 를 `SOFT_DELETED` 로 변경. **기존 DB 의 `'DELETED'` row 는 수동 DDL UPDATE 로 `'SOFT_DELETED'` 마이그레이션**.
- purge 는 `BINARY_DELETE_*` 만 사용 → soft-delete 와 의미 분리.

## 9. DDL 영향 요약 (수동 DDL 필요)

**ALTER → nullable (NOT NULL date PII)**: `ApplicationCertificate.acquiredDate`, `ApplicationLanguage.examDate`, `ApplicationAward.awardDate`, `ApplicationGapPeriod.startDate`/`endDate`, `ApplicationAttachment.storagePath`.

**PLACEHOLDER 유지(NOT NULL String, DDL 불요)**: `applicantNameSnapshot`, `schoolName`, `companyName`, `certificateName`, `issuingOrganization`, `languageName`, `testName`, `awardName`, `awardingOrganization`, `gapPeriod.reason`, `originalFileName`. (원하면 이들도 ALTER nullable+NULLIFY 로 전환 가능 — 컬럼별 선택지로 문서화.)

**신규 컬럼**: `ApplicationAttachment.filenameHash`, `ApplicationAttachment.binaryDeletedAt`; (설계 §7.2 의 JobApplication/JobPosting/신규 5테이블과 합산).

**enum 마이그레이션**: `PhysicalFileStatus` `'DELETED'` → `'SOFT_DELETED'` 데이터 UPDATE.

**`@Column(updatable=false)` 회피**: `createdBy` 는 JPQL/native bulk update 로만 클리어 가능(엔티티 dirty-update 안 됨).

## 10. 판단 보류 / 확인 필요 항목

- **날짜 보존 trade-off**: education(admission/graduation), career(start/end) 날짜는 funnel/코호트용으로 KEEP 했다. 이는 잔존 quasi-identifier(연도+학교코드 조합) 위험을 일부 수용한 결정 — 더 강한 파기를 원하면 연/분기 bucket 으로 절단(generalization) 후 원본 날짜 NULLIFY 로 전환 가능. **확인 필요.**
- **PLACEHOLDER vs ALTER nullable 일괄 정책**: NOT NULL String PII 를 placeholder(기본)로 둘지, 전부 ALTER nullable+NULLIFY 로 통일할지 — 운영 DB DDL 부담과 trade-off. 기본은 placeholder.
- **`Interview.memo` 잔존**: §7 flag — Phase 9 범위 밖. 운영 가이드(실명 금지) 또는 후속 정리.
- **`ciHash` 보존**: 완전삭제 요구 시 별도 후속.

## 11. 9d 착수 게이트

본 인벤토리(특히 §9 DDL 목록, §10 확인 항목)가 **확정**되어야 9d-1/9d-2 구현을 시작한다. ADR-0005 는 본 인벤토리 확정 전까지 `accepted-with-implementation-gate`.
