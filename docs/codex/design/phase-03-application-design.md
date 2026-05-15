# Phase 03 Application Design

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
  - `headcount`
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
- 지원자 결과 조회
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
