# Phase 08 - CommonCode & School Master (Design)

- Date: 2026-06-02
- Work type: **documentation-only 설계 단계** (grill-with-docs 세션 기반). Java/test/migration 구현하지 않는다.
- Source of decisions: 본 세션의 Q1~Q10 + `CONTEXT.md`(CommonCode/School 용어) + `docs/codex/02-domain-design.md` §4.17/§4.20.

## 1. Purpose

- **CommonCode**: 관리자가 런타임에 관리하는 코드성 lookup master 를 도입한다(프론트 드롭다운 소스).
- **School**: 지원자 학력 입력의 자동완성/검색 master 를 도입하고, `ApplicationEducation` 에 optional 링크를 추가해 학교별 통계의 기반을 만든다.
- 둘 다 **추가형(비파괴)** 이며 기존 enum/도메인 쓰기 경로를 깨지 않는다.

## 2. Scope (In)

- `CommonCode` 엔티티 + repository + admin CRUD + public read(groupCode별).
- `School` 엔티티 + repository + 검색/자동완성(public) + admin CRUD + **xlsx 일괄 import(upsert)**.
- `ApplicationEducation` 에 **optional `schoolId`**(nullable, app-level 참조) 추가.
- 기존 enum 의 **CommonCode 전환 후보 카탈로그**(STAY vs CANDIDATE) 문서화.

## 3. Out of scope

- **기존 enum → CommonCode 전환(0건)**. 카탈로그만 만든다. 전환은 "관리자가 런타임에 값을 추가해야 한다"는 구체 요구가 생긴 group 에 한해 별도 진행.
- **CommonCode ↔ 백엔드 도메인 필드 validation 결합 없음**. CommonCode 는 독립 lookup 이고 프론트가 소비한다. `workLocation`/`jobGroup`/`jobTitle` 등 free-text 필드는 그대로 둔다(검증 미결합).
- **SCHOOL/CERTIFICATE funnel dimension(07c 보류)** — Phase 08 는 master + 링크까지만. 학교별 통계 구현은 별도 후속.
- 외부 학교 API 연동, 강한 FK 제약, migration 프레임워크 도입.
- 과거 free-text `schoolName` 의 소급 매칭(backfill).

## 4. Slice plan

| 슬라이스 | 범위 |
| --- | --- |
| **08a** | `CommonCode` 엔티티/repository + admin CRUD + public read |
| **08b** | `School` 엔티티/repository + 검색·자동완성(public) + admin CRUD |
| **08c** | `School` xlsx 일괄 import(upsert) + `ApplicationEducation.schoolId` optional 링크(지원자 학력 쓰기 경로) |

## 5. CommonCode 설계

### 5.1 엔티티

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `groupCode` | `String` | 코드 그룹(별도 CodeGroup 엔티티 없음) |
| `code` | `String` | 코드 값. **생성 후 불변(안정 키)** |
| `displayName` | `String` | 표시명(수정 가능) |
| `sortOrder` | `Integer` | 정렬(수정 가능) |
| `active` | `boolean` | 사용 여부(수정 가능, soft delete) |
| `description` | `String` | 설명(수정 가능, nullable) |

- 제약: `(groupCode, code)` unique. `BaseEntity` 상속(감사 필드).
- 강한 FK 없음(application-level 참조). `groupCode` 는 string 컬럼(그룹 자체는 distinct groupCode 로 조회).

### 5.2 수명주기

- `code` 불변. 수정 가능 = `displayName`/`sortOrder`/`active`/`description`.
- 삭제는 **soft delete**(`active=false`). hard delete 없음 → 프론트가 저장한 `code` 문자열이 dangling 되지 않음.
- public read 는 `active=true` 만, `sortOrder` 순. admin read 는 비활성 포함.

### 5.3 API

| Method | Path | 접근 | Purpose |
| --- | --- | --- | --- |
| GET | `/api/codes?groupCode=` | permitAll | active 코드 목록(sortOrder 순) |
| GET | `/api/admin/codes?groupCode=` | admin | 비활성 포함 관리용 목록 |
| POST | `/api/admin/codes` | admin | 코드 생성 |
| POST | `/api/admin/codes/{id}` | admin | displayName/sortOrder/active/description 수정(admin 커맨드 컨벤션상 POST) |

- 보안: `/api/codes` 는 SecurityConfig `anyRequest().permitAll()` 로 자동 공개, `/api/admin/codes` 는 `/api/admin/**` 로 자동 admin. **SecurityConfig 변경 불필요**.
- 응답: `ApiResponse<List<CommonCodeResponse>>`. Request DTO 는 record + Bean Validation.

### 5.4 seeding

- **자동 seed 없음**(admin CRUD 로 관리). 초기 값은 운영 셋업 시 admin 이 입력. (선택) 개발 편의용 `data.sql`/CommandLineRunner 는 별도 결정 — migration 프레임워크 부재상 기본은 무-seed.

## 6. School 설계

### 6.1 엔티티

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK(내부 surrogate). `ApplicationEducation.schoolId` 가 참조 |
| `schoolCode` | `String` | 외부/레지스트리 학교코드. **있으면 unique**(nullable) |
| `schoolName` | `String` | 정규 학교명 |
| `schoolType` | `String`/code | 고등학교/대학교/대학원 등(코드값, CommonCode group 후보) |
| `educationMode` | `String`/code | OnCampus/Online 등 |
| `region` | `String` | 지역 |
| `address` | `String` | 주소(nullable) |
| `countryCode` | `String` | 국가코드 |
| `active` | `boolean` | 사용 여부 |

- 식별/중복제거 키: `schoolCode`(있으면) **우선**, 없으면 `(schoolName, schoolType, region)` fallback.
- 강한 FK 없음. `ApplicationEducation.schoolId` 는 app-level 참조.

### 6.2 검색/자동완성

| Method | Path | 접근 | Purpose |
| --- | --- | --- | --- |
| GET | `/api/schools?q=&schoolType=` | permitAll | 이름 prefix/contains 검색, top-N(예: 20), active 만 |
| GET | `/api/admin/schools` | admin | 관리용 목록(페이지) |
| POST/PUT | `/api/admin/schools[/{id}]` | admin | 단건 생성/수정 |
| POST | `/api/admin/schools/import` | admin | xlsx 일괄 upsert |

- 검색: `schoolName` prefix 우선 + contains, `schoolType` 옵션 필터, 결과 top-N 제한(cardinality/성능). 응답은 `id`/`schoolName`/`schoolType`/`region` 정도(PII 아님).

### 6.3 xlsx import(upsert)

- 07a/07d POI·parser 패턴 재사용. 컬럼: `schoolCode`/`schoolName`/`schoolType`/`educationMode`/`region`/`address`/`countryCode`.
- upsert 키: `schoolCode`(있으면) → 없으면 `(schoolName, schoolType, region)`. 기존=update, 신규=insert.
- 결과 요약(insert/update/skip 카운트) 반환. 파일 레벨 방어(.xlsx/크기/행수/header)는 07d 패턴 상속. (preview/commit 분리 여부는 구현 슬라이스에서 결정 — 기본은 단일 commit + 요약.)

### 6.4 ApplicationEducation 링크

- `ApplicationEducation` 에 nullable `schoolId` 추가(create 팩토리에 optional 인자). 자동완성 선택 시 채우고, 직접입력 시 null.
- `schoolName`(free-text snapshot)은 **그대로 유지**(master 명과 별개 표시값). 미선택/미매칭은 통계에서 '기타'.
- 기존 education 쓰기/조회 계약 비파괴(필드 추가만).

## 7. 기존 enum → CommonCode 전환 후보 카탈로그

전환은 **하지 않는다**(Phase 08 카탈로그 전용). 분기 로직 사용 여부로 분류.

### 7.1 STAY (enum 유지)

- **상태머신**: `JobApplicationStatus`, `JobPostingStatus`, `StageStatus`, `InterviewStatus`, `EvaluationStatus`, `ReceptionStatus`, `PhysicalFileStatus`, `InterviewParticipantStatus`.
- **분기/정책**: `StageResultStatus`(funnel/upload allowlist), `EducationLevel`(고졸 학기성적 규칙 + 최종학력 정렬), `MilitarySubjectType`(필수 필드 분기), `QuestionAnswerType`/`QuestionCategory`, `ApplicationSectionType`/`ApplicationFormRequirementType`(레이아웃 분기), `InterviewParticipantRole`, `InterviewMethod`, `CareerType`, `GapType`.
- **척도/고정**: `EvaluationGrade`, `EvaluationRecommendation`.
- **내부/API**: `FunnelDimension`, `StageResultUploadCommitOutcome`, `StageResultUploadRowStatus`, `AttachmentDeleteActorType`, `AttachmentStorageHealthIssueType`, `MenuSite`, `MenuType`, `NoticeSearchType`, `JobPostingType`.

### 7.2 CANDIDATE (표시 전용 분류 — 분기 미발견, 향후 admin 편집 요구 시 전환 후보)

- `MilitaryBranch`, `MilitaryRank`, `MilitaryServiceType` (병역 분류, 표시 전용).
- `EmploymentType`, `JobPositionApplicationType` (현재 default 존재, 표시/필터).
- `DayNightType`, `CampusType` (소규모 표시 분류).

### 7.3 재검증 필요(borderline)

- `GraduationStatus`, `StageType`, `AttachmentType` — targeted grep 에서 분기 미발견이나, 실제 전환 전 controller/정책 분기 재확인 필요. 현 단계에선 STAY 로 둔다.

> 주의: CANDIDATE 라도 Phase 08 에선 전환하지 않는다. 전환 시 entity/DTO/validation/test 변경 + 컴파일 타임 안전성 상실이 발생하므로, 구체 요구가 있을 때만 해당 group 단위로 진행한다.

## 8. Validation & Business rules

- CommonCode: `groupCode`/`code`/`displayName` 필수, `(groupCode, code)` unique, `code` 불변, soft delete.
- School: 식별키 정책(§6.1), import upsert 멱등, 검색 top-N 제한.
- ApplicationEducation: `schoolId` optional, `schoolName` free-text 유지, 강한 FK 미사용(app-level).
- 보안: code/school read 는 비민감 → public. 변경은 admin. PII 아님(학교/코드 라벨).

## 9. Component Summary (구현 슬라이스 후보)

| 슬라이스 | 클래스 | 타입 | 책임 |
| --- | --- | --- | --- |
| 08a | `CommonCode` | Entity | groupCode/code/displayName/sortOrder/active/description |
| 08a | `CommonCodeRepository` | Repository | groupCode별 active 정렬 조회, (groupCode,code) 존재 |
| 08a | `CommonCodeService` | Service | CRUD + public 조회(active) |
| 08a | `CommonCodeController` / `AdminCommonCodeController` | Controller | public read / admin CRUD |
| 08a | `CommonCodeRequest`/`CommonCodeResponse` | DTO | record + validation |
| 08b | `School` | Entity | 식별/검색 필드 |
| 08b | `SchoolRepository` | Repository | 이름 검색, schoolCode/속성 upsert 조회 |
| 08b | `SchoolService` | Service | 검색 + admin CRUD |
| 08b | `SchoolSearchController` / `AdminSchoolController` | Controller | public 검색 / admin CRUD |
| 08c | `SchoolImportService` | Service | xlsx upsert(07d parser 재사용) |
| 08c | `ApplicationEducation` | Entity(수정) | nullable `schoolId` 추가 |

## 10. Test strategy (구현 슬라이스용)

- CommonCode: (groupCode,code) unique 위반, code 불변(수정 거부), soft delete 후 public 조회 제외/admin 포함, public read active+정렬, 인가(admin CRUD 403/401).
- School: 검색 prefix/contains/top-N/schoolType 필터, upsert 멱등(재import 중복 없음), schoolCode 우선·속성 fallback, 인가.
- ApplicationEducation: schoolId optional 저장/조회, 미선택 null, 기존 education 테스트 비파괴.

## 11. Test results

- documentation-only 단계 — Gradle 테스트 미실행.

## 12. Remaining issues / Open questions

- School import preview/commit 분리 여부(07d 수준 vs 단일 commit) — 구현 슬라이스에서 확정.
- School `schoolType`/`educationMode` 를 CommonCode group 으로 둘지(코드값) vs 자유문자 — 08b 에서 확정(권장: CommonCode group `SCHOOL_TYPE` 재사용).
- CommonCode seeding(무-seed vs dev data.sql).
- SCHOOL/CERTIFICATE funnel dimension(별도 후속, 07c 패턴 재사용).

## 13. Decision Log

| # | Decision | Rationale | Q |
| --- | --- | --- | --- |
| 1 | CommonCode 추가형 도입, 기존 enum 전환 0 | 타입 안전성/분기 보존, blast radius 0, CLAUDE.md §4.3 | Q1/Q2 |
| 2 | CommonCode 독립 lookup, 백엔드 필드 validation 미결합 | 최소·비파괴, 프론트 드롭다운 소비가 핵심 가치 | Q3 |
| 3 | public read + admin CRUD, group=string 컬럼 | 코드는 비민감 라벨, 공개/지원자 폼 공용 | Q4 |
| 4 | code 불변 + soft delete, (groupCode,code) unique | 저장된 code dangling 방지, 안정 키 | Q5 |
| 5 | School ↔ ApplicationEducation = optional nullable schoolId(app-level) | 비파괴 + 학교별 통계 기반 동시 확보 | Q6 |
| 6 | School 적재 = admin CRUD + xlsx upsert, 외부 API 미연동 | 수천 학교 현실적 적재, 외부 의존 회피 | Q7 |
| 7 | School 식별 = schoolCode 우선 + (name,type,region) fallback | 재import 멱등, 안정 grouping | Q8 |
| 8 | SCHOOL 통계 dimension 은 별도 후속(보류) | Phase 08 = master+링크 집중, 데이터 축적 후 | Q9 |
| 9 | 산출물 = 설계 문서만(구현 08a~08c 후속) | 06/07 패턴(설계→슬라이스 구현) | Q10 |

## 14. Next phase

- **Phase 08a** - CommonCode 구현(엔티티/CRUD/public read).
- 이후 08b(School 검색/CRUD), 08c(import + schoolId 링크).
- 후속: SCHOOL/CERTIFICATE funnel dimension, 메시지 발송, 개인정보 파기/감사(영속 ActivityLog).

## 15. 관련 ADR

- `docs/adr/0003-commoncode-additive-no-enum-migration.md`
- `docs/adr/0004-school-optional-application-level-link.md`
