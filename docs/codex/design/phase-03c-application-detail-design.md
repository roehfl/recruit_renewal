# Phase 03c Application Detail Design

## Phase 03c-9-2 Implementation Note

- Phase 03c-9-2 implemented `ApplicationAnswer` and applicant question/answer APIs.
- Added applicant APIs:
  - `GET /applications/{applicationId}/questions`
  - `POST /applications/{applicationId}/answers`
- `ApplicationAnswer` is a `JobApplication` child answer record and references one active `JobPostingQuestion` at save time.
- `JobApplication` and `JobPostingQuestion` still do not have answer collections; cascade/orphanRemoval was not added.
- Applicant question list returns active posting questions only, with current answer data when present.
- Applicant answer save uses replace semantics: delete all existing answers for the application, then save requested rows with question snapshots.
- DRAFT save permits null/blank answers, including required questions. Required/blank validation is deferred to submit validator integration.
- Admin answer read API and `ApplicationSubmitValidator` question/answer integration remain out of scope for this phase.

## Phase 03c-9-1 Implementation Note

- Phase 03c-9-1에서 `QuestionTemplate` + `JobPostingQuestion` 관리자 질문 구성 API를 구현했다.
- 추가 API는 `GET/POST /admin/question-templates`, `GET/POST /admin/question-templates/{templateId}`, `POST /admin/question-templates/{templateId}/deactivate`, `GET/POST /admin/job-postings/{jobPostingId}/questions`, `POST /admin/job-postings/{jobPostingId}/questions/{questionId}`, `POST /admin/job-postings/{jobPostingId}/questions/reorder`, `POST /admin/job-postings/{jobPostingId}/questions/{questionId}/delete`이다.
- `QuestionTemplate`은 전역 질문 은행, `JobPostingQuestion`은 공고별 질문 snapshot record로 두며 `JobPosting`/`QuestionTemplate`에는 역방향 컬렉션을 추가하지 않았다.
- `JobPosting.status=DRAFT`에서만 질문 생성/수정/정렬/비활성화를 허용하고, 게시 이후 질문 변경은 후속 revision/reopen 정책 전까지 금지한다.
- `active=false` soft delete만 구현했고 HTTP DELETE, PUT, 물리 삭제는 추가하지 않았다.
- 지원자 답변 저장, `ApplicationAnswer`, submit validator 질문답변 연동, 관리자 답변 조회는 후속 Phase로 보류했다.

## Phase 03c-9 Design Note

- Phase 03c-9에서 자기소개서/질문답변 도메인 설계를 정리했다.
- 추천 구조는 `QuestionTemplate` + `JobPostingQuestion` + `ApplicationAnswer`이다.
- 자기소개서는 별도 Entity가 아니라 `QuestionCategory.SELF_INTRODUCTION` 카테고리의 질문답변으로 일반화한다.
- `JobPostingQuestion`은 공고별 실제 질문 record이며 템플릿 참조는 nullable로 둔다. 질문 문구, 도움말, category, answerType, required, maxLength, sortOrder는 공고 배치 시점 snapshot으로 자체 보유한다.
- `ApplicationAnswer`는 지원서별 답변 record이며 `questionTextSnapshot`, `answerTypeSnapshot`, `requiredSnapshot`, `maxLengthSnapshot`, `sortOrderSnapshot` 후보를 가진다.
- 초기 답변 타입은 `SHORT_TEXT`, `LONG_TEXT`로 시작하고, 선택형/파일형 답변은 후속 Phase로 보류한다.
- 공고 질문 구성은 `JobPosting.status=DRAFT`에서만 수정 허용하고, 게시 이후 질문 변경은 revision/reopen 정책 전까지 금지하는 방향을 추천한다.
- 지원자 답변은 `DRAFT` 상태에서만 저장 가능하며, required 미입력은 DRAFT 저장에서는 허용하되 submit 시 실패시키는 방향을 추천한다.
- 관리자 답변 조회는 Phase 03c-8의 lazy API 흐름에 맞춰 `GET /admin/applications/{applicationId}/answers` 후보로 둔다.
- 이번 Phase는 설계 문서 작업만 수행했고 상세 섹션 Entity/API, `ApplicationSubmitValidator`, 관리자 상세 섹션 API는 변경하지 않았다.

## Phase 03c-8 Implementation Note

- Phase 03c-8에서 관리자 상세 섹션별 read-only lazy 조회 API를 구현했다.
- 관리자 API는 `GET /admin/applications/{applicationId}/educations`, `/careers`, `/certificates`, `/languages`, `/military`, `/awards`, `/gap-periods`, `/attachments`만 추가했다.
- 지원자 상세 섹션 API path/method, 저장 정책, replace 저장 방식, Entity 구조는 변경하지 않았다.
- 관리자 조회용 `AdminApplicationSectionService`를 별도로 두어 지원자 소유자 검증과 DRAFT 저장 정책이 섞인 지원자 Service를 재사용하지 않았다.
- 목록형 섹션은 빈 배열을 반환하고, `military`는 저장 전 `data=null`, `careers`는 profile이 없으면 `careerType=NOT_SELECTED`, `careers=[]`를 반환한다.
- Education은 `sortOrder ASC, id ASC`, SemesterGrade는 `schoolYear ASC, semester ASC, id ASC`로 조회한다. Career/Certificate/Language/Award/GapPeriod/Attachment는 `sortOrder ASC, id ASC`로 조회한다.
- Certificate 관리자 응답은 `certificateNumber` 원문 대신 `certificateNumberMasked`만 제공한다.
- Military 관리자 응답은 `exemptionReason` 원문 대신 `exemptionReasonMasked`만 제공한다.
- Attachment 관리자 응답은 `storedFileName`, `storagePath`, 다운로드 URL을 제공하지 않는다.
- GapPeriod의 `reason`, `description`과 Award의 `description`은 관리자 상세 섹션 조회 목적상 포함하되, 관리자 목록 응답에는 포함하지 않는 정책을 유지한다.
- 관리자 aggregate 상세 API, 관리자 수정/삭제 command, 파일 업로드/다운로드, StageResult, 자기소개서/질문답변은 이번 Phase에서 구현하지 않았다.

## Phase 03c-7 Implementation Note

- Phase 03c-7에서 `ApplicationSubmitValidator`를 구현해 `JobApplicationService.submit()`에 연결했다.
- Validator는 상태 전이를 수행하지 않고 `ApplicationFormConfig` 기반 상세 섹션 제출 가능 여부만 검증한다.
- Education은 `useEducation=true`일 때 `ApplicationEducation` 최소 1건을 요구한다.
- Career는 `useCareer=true`일 때 profile 필수, `NOT_SELECTED` 제출 실패, `EXPERIENCED` Career row 최소 1건, `NEWCOMER`/`NOT_APPLICABLE` Career row 없음 정책을 적용한다.
- Military는 `useMilitary=true`일 때 record 필수, `militarySubjectType` 필수, `COMPLETED` 복무기간 필수, `EXEMPTED` 면제 사유 필수 정책을 적용한다.
- Certificate, Language, Award, GapPeriod는 현재 선택 섹션으로 보고 최소 row를 강제하지 않는다.
- Attachment는 현재 `ApplicationFormConfig` flag가 없어 submit 필수 검증에서 제외한다.
- 기존 상세 섹션 저장 API path/method, Entity 구조, replace 저장 정책은 변경하지 않았다.
- 관리자 상세 섹션 API, StageResult, 자기소개서/질문답변, 실제 파일 업로드/다운로드는 구현하지 않았다.

## Phase 03c-6 Implementation Note

- Phase 03c-6에서 Attachment metadata vertical slice를 구현했다.
- 구현 클래스는 `ApplicationAttachment`, `ApplicationAttachmentService`, `ApplicationAttachmentController`, `ApplicationAttachmentRepository`, `AttachmentReplaceRequest`, `AttachmentRequest`, `AttachmentResponse`이다.
- `AttachmentType`은 `RESUME`, `TRANSCRIPT`, `GRADUATION_CERTIFICATE`, `CAREER_CERTIFICATE`, `CERTIFICATE_PROOF`, `LANGUAGE_SCORE_REPORT`, `PORTFOLIO`, `ETC`로 시작한다.
- `ApplicationSectionType`은 `APPLICATION`, `EDUCATION`, `CAREER`, `CERTIFICATE`, `LANGUAGE`, `MILITARY`, `AWARD`, `GAP_PERIOD`, `ETC`로 시작한다.
- 지원자 API는 `GET /applications/{applicationId}/attachments`, `POST /applications/{applicationId}/attachments`이다.
- replace 저장은 기존 Attachment row를 `applicationId` 기준 명시 삭제하고 새 metadata row를 저장하는 방식이다.
- Attachment 목록 정렬은 구현 기준으로 `sortOrder ASC, id ASC`이다.
- `storedFileName`, `storagePath`는 내부 관리 필드이며 `AttachmentResponse`에 포함하지 않는다.
- 실제 multipart 파일 업로드, 다운로드, 저장소 연동, 파일 삭제 API는 구현하지 않았다.
- Attachment는 현재 `ApplicationFormConfig` flag 없이 저장 가능하며, `DRAFT`, PUBLISHED 공고, 접수기간 내 조건만 검증한다.
- `sectionType=APPLICATION`이면 `sectionRecordId`는 null이어야 하고, 그 외 sectionType은 DRAFT 저장에서 `sectionRecordId` null을 허용한다. 값이 있으면 1 이상이어야 한다.
- submit 통합 검증과 관리자 상세 섹션 API는 아직 연결하지 않았다.

## Phase 03c-5 Implementation Note

- Phase 03c-5에서 Award + GapPeriod vertical slice를 구현했다.
- 구현 클래스는 `ApplicationAward`, `ApplicationGapPeriod`, `ApplicationAwardService`, `ApplicationGapPeriodService`, `ApplicationAwardController`, `ApplicationGapPeriodController`이다.
- `GapType`은 `EDUCATION`, `CAREER`, `OTHER`로 시작했다.
- 지원자 API는 `GET /applications/{applicationId}/awards`, `POST /applications/{applicationId}/awards`, `GET /applications/{applicationId}/gap-periods`, `POST /applications/{applicationId}/gap-periods`이다.
- replace 저장은 기존 Award/GapPeriod row를 `applicationId` 기준 명시 삭제하고 새 row를 저장하는 방식이다.
- 저장은 `DRAFT`, `JobPosting.status=PUBLISHED`, 접수기간 내에서만 가능하다.
- Award 저장은 `ApplicationFormConfig.useAward=true`, GapPeriod 저장은 `ApplicationFormConfig.useGapPeriod=true`일 때만 가능하다.
- GapPeriod는 `startDate <= endDate`를 검증하며, overlap 검증은 후속 정책 확정 전까지 강제하지 않는다.
- description은 DTO와 Service 직접 호출 모두에서 2000자 이하로 검증한다.
- `ApplicationSectionAccessService`는 `validateAwardEnabled`, `validateGapPeriodEnabled`까지 확장되었다.
- 이번 Phase에서 Attachment, StageResult, 관리자 상세 섹션 API, submit 통합 검증은 구현하지 않았다.

## Phase 03c-4R Implementation Note

- Phase 03c-4R에서 상세 섹션 공통 접근/수정 가능 검증 helper `ApplicationSectionAccessService`를 추가했다.
- helper는 `findOwnedApplication`, `validateWritable`, `validateEducationEnabled`, `validateCareerEnabled`, `validateCertificateEnabled`, `validateLanguageEnabled`, `validateMilitaryEnabled`만 담당한다.
- `SectionType` enum이나 submit validator까지 일반화하지 않았다.
- Education/Career/Certificate/Language/Military Service는 공통 검증을 helper에 위임하고, 각 섹션 고유 request 검증과 저장 로직만 유지한다.
- 신규 API나 Entity 관계 변경은 없다.

## Phase 03c-4 Implementation Note

- Phase 03c-4에서 Military vertical slice를 구현했다.
- 구현 클래스는 `ApplicationMilitary`, `ApplicationMilitaryService`, `ApplicationMilitaryController`, `ApplicationMilitaryRepository`, `MilitarySaveRequest`, `MilitaryResponse`이다.
- 병역 관련 enum은 `MilitarySubjectType`, `MilitaryServiceType`, `MilitaryBranch`, `MilitaryRank`로 시작했다.
- `ApplicationMilitary`는 `JobApplication`과 1:1 단건 record이며, `JobApplication`에는 Military 필드를 추가하지 않았다.
- 저장은 upsert 방식이며, 기존 record가 있으면 update하고 없으면 create한다.
- `GET /applications/{applicationId}/military`는 저장 전이면 `data=null`로 응답한다.
- `POST /applications/{applicationId}/military`는 `DRAFT`, `JobPosting.status=PUBLISHED`, 접수기간 내, `useMilitary=true`에서만 가능하다.
- `SUBJECT`, `NOT_SUBJECT`, `NOT_APPLICABLE`은 상세 병역 필드를 허용하지 않는다.
- `COMPLETED`는 복무 상세 필드를 허용하되 `exemptionReason`은 허용하지 않는다.
- `EXEMPTED`는 `exemptionReason`을 허용하되 복무 상세 필드는 허용하지 않는다.
- submit 통합 검증은 아직 연결하지 않았고, `useMilitary=true`이면 `ApplicationMilitary` 1건 필수 정책은 Phase 03c-7에서 구현한다.
- Education/Career/Certificate/Language/Military의 접근/상태/접수기간/config enabled 검증 반복은 Phase 03c-4R에서 `ApplicationSectionAccessService`로 최소 추출했다.

## Phase 03c-3 Implementation Note

- Phase 03c-3에서 Certificate + Language vertical slice를 구현했다.
- 구현 클래스는 `ApplicationCertificate`, `ApplicationLanguage`, `ApplicationCertificateService`, `ApplicationLanguageService`, `ApplicationCertificateController`, `ApplicationLanguageController`이다.
- 지원자 API는 `GET /applications/{applicationId}/certificates`, `POST /applications/{applicationId}/certificates`, `GET /applications/{applicationId}/languages`, `POST /applications/{applicationId}/languages`이다.
- replace 저장은 기존 Certificate/Language row를 `applicationId` 기준 명시 삭제하고 새 row를 저장하는 방식으로 확정했다.
- 저장은 `DRAFT` 지원서에서만 가능하며, `SUBMITTED`/`WITHDRAWN`은 조회만 가능하다.
- Certificate 저장은 `ApplicationFormConfig.useCertificate=true`, Language 저장은 `ApplicationFormConfig.useLanguage=true`일 때만 가능하다.
- Certificate는 `expiredDate`가 있으면 `acquiredDate <= expiredDate`, Language는 `expiredDate`가 있으면 `examDate <= expiredDate`를 검증한다.
- Language의 `score`, `grade`는 DRAFT 저장에서는 둘 다 비어 있어도 허용하며, submit 필수 여부는 Phase 03c-7에서 재검토한다.
- 이번 Phase에서 Military, Award, GapPeriod, Attachment, StageResult, 관리자 상세 섹션 API는 구현하지 않았다.
- Education/Career/Certificate/Language에서 지원서 접근/수정 가능 여부/config enabled 검증이 반복되고 있으므로, Military 구현 후 `ApplicationSectionAccessService` 같은 최소 공통 helper 추출을 검토한다.
- helper 후보 범위는 본인 지원서 조회, 상세 섹션 쓰기 가능 검증, 섹션 enabled 검증 정도로 제한한다.
- Certificate/Language의 일부 자유 입력 문자열 길이 제한은 아직 구현하지 않았고, 운영 DB schema 기준 확정 후 `@Column(length = ...)` 또는 DTO `@Size`를 검토한다.

## Phase 03c-2 Implementation Note

- Phase 03c-2에서 Career vertical slice를 구현했다.
- 구현 클래스는 `ApplicationCareerProfile`, `ApplicationCareer`, `ApplicationCareerService`, `ApplicationCareerController`이다.
- `CareerType`은 `NOT_SELECTED`, `NEWCOMER`, `EXPERIENCED`, `NOT_APPLICABLE`을 사용한다.
- `EmploymentType`은 `FULL_TIME`, `CONTRACT`, `INTERN`, `FREELANCE`, `PART_TIME`, `ETC`를 사용한다.
- `ApplicationCareerProfile`은 `JobApplication`별 경력 선택 상태를 나타내는 단건 record이고, `ApplicationCareer`는 경력 row 목록이다.
- replace 저장은 profile upsert 후 기존 Career row를 `applicationId` 기준 명시 삭제하고 새 Career row를 저장하는 방식으로 확정했다.
- `CareerType.EXPERIENCED`가 아닌 경우 Career row가 있으면 실패한다.
- `CareerType.EXPERIENCED`는 DRAFT 저장에서 빈 목록을 허용하며, submit 시 최소 1개 필수 여부는 Phase 03c-7에서 검증한다.
- 저장은 `ApplicationFormConfig.useCareer=true`일 때만 가능하다.
- 이번 Phase에서 Certificate, Language, Military, Award, GapPeriod, Attachment, StageResult는 구현하지 않았다.

## Phase 03c-1 Implementation Note

- Phase 03c-1에서 Education + EducationSemesterGrade vertical slice를 구현했다.
- 구현 클래스는 `ApplicationEducation`, `ApplicationEducationSemesterGrade`, `ApplicationEducationService`, `ApplicationEducationController`이다.
- 지원자 API는 `GET /applications/{applicationId}/educations`, `POST /applications/{applicationId}/educations`이다.
- replace 저장은 기존 SemesterGrade를 먼저 삭제한 뒤 Education을 삭제하고 새 Education/SemesterGrade 목록을 저장하는 방식으로 확정했다.
- `admissionDate`와 `graduationDate`가 모두 있으면 `admissionDate <= graduationDate`를 검증한다.
- 저장은 `DRAFT` 지원서에서만 가능하며, `SUBMITTED`/`WITHDRAWN`은 조회만 가능하다.
- 저장은 `ApplicationFormConfig.useEducation=true`일 때만 가능하다.
- submit 통합 검증은 아직 연결하지 않았고 Phase 03c-7에서 `ApplicationSubmitValidator`로 처리한다.
- 이번 Phase에서 Career, Certificate, Language, Military, Award, GapPeriod, Attachment, StageResult는 구현하지 않았다.
- 성적/학점 `BigDecimal` 컬럼의 precision/scale 명시는 운영 DB schema 정책 확정 후 검토한다.
- 다음 섹션에서 지원서 접근/상태/접수기간/config enabled 검증이 반복되면 최소 공통 helper 추출을 검토한다.

## 1. Summary

Phase 03c-0의 목적은 Phase 03a/03b에서 구현된 `JobApplication` 루트에 연결될 지원서 상세 섹션 도메인을 설계하는 것이다. 이번 문서는 설계 전용 문서이며 Java 코드, DB schema, API 구현, 테스트 코드를 추가하지 않는다.

설계 대상은 지원자가 실제로 작성하는 지원서 상세 항목이다.

- 기본 인적사항 / 지원자 프로필 연동
- 학력사항
- 학기별 성적
- 경력사항
- 자격사항
- 어학사항
- 병역사항
- 수상/포상사항
- 공백기간
- 첨부파일 metadata

자기소개서/질문답변 영역은 질문 템플릿, 질문 세트, 답변 구조가 필요하므로 Phase 03c 상세 섹션과 분리해 별도 Phase로 설계한다.

## 2. Current State

현재 구현 상태는 다음과 같다.

- Phase 01a/01b
  - `JobPosting`, `JobPosition`, `ApplicationFormConfig` 구현 완료
  - 공개 채용공고 조회는 `PUBLISHED` 공고만 노출
  - `accepting`은 `Clock` 기반으로 계산
- Phase 02a
  - `Stage` 기본 CRUD, reorder, start/announce/close/delete command 구현 완료
  - `StageResult`는 `Application` 기반이 필요해 보류
- Phase 03a
  - `JobApplication` 루트 구현 완료
  - 지원자 생성/조회/updateDraft/submit/withdraw API 구현 완료
  - `JobApplicationStatus`: `DRAFT`, `SUBMITTED`, `WITHDRAWN`
- Phase 03b-1
  - 관리자 Application 목록/상세 조회 API 구현 완료
  - 관리자 상세 응답은 현재 Application 루트 정보만 포함
  - 상세 섹션, StageResult, 평가/합격 결과는 미구현

## 3. Overall Structure Decision

### Option A. 모든 상세 섹션을 JobApplication 하위 도메인으로 둔다

장점:

- 지원 당시 작성 내용이 지원서 단위로 고정된다.
- 같은 Applicant가 다른 공고에 지원할 때 서로 다른 학력/경력/첨부를 작성할 수 있다.
- 제출 후 수정 금지, 관리자 조회, 전형 결과 연결이 `JobApplication` 기준으로 명확하다.

단점:

- Applicant 프로필과 중복되는 개인정보가 늘어날 수 있다.
- 개인정보 암호화/마스킹 정책을 섹션별로 세밀하게 관리해야 한다.

### Option B. 일부 정보는 Applicant 프로필에 두고 JobApplication에는 snapshot 또는 지원서별 기록만 둔다

장점:

- 이름, 휴대폰번호, 주소, CI 등 계정/본인확인 정보의 원천을 한 곳으로 유지할 수 있다.
- 암호화/hash 검색 정책을 Applicant/User 계층에 집중할 수 있다.
- Application 상세 섹션은 채용 지원서별로 달라지는 내용에 집중한다.

단점:

- 제출 시점 snapshot이 필요한 항목을 별도로 정해야 한다.
- 프로필 변경 후 과거 지원서 표시 기준이 애매해질 수 있다.

### Recommendation

혼합 구조를 사용한다.

- 이름, 휴대폰번호, 이메일, 주소, CI, ciHash 등 기본 개인정보의 원천은 `Applicant`/`User` 계층에 둔다.
- `JobApplication`에는 이미 구현된 `applicantNameSnapshot`처럼 지원 시점 표시/감사용으로 필요한 최소 snapshot만 둔다.
- CI는 `JobApplication`에 snapshot으로 복제하지 않는다.
- 휴대폰번호, 이메일, 주소 snapshot은 화면/운영 요구가 확정될 때 별도 정책으로 추가한다. 기본값은 Application 상세 섹션에 저장하지 않는 것이다.
- 학력, 성적, 경력, 자격, 어학, 병역, 수상, 공백기간, 첨부파일 metadata는 `JobApplication` 하위 application-specific record로 둔다.

## 4. ApplicationFormConfig Policy

`ApplicationFormConfig`의 `useXxx` flag는 섹션의 사용 여부를 의미한다. Phase 03c 기본 정책은 다음과 같다.

| Flag | 화면 노출 | 임시저장 | 최종제출 검증 | 관리자 상세 포함 | 빈 목록 허용 |
|---|---|---|---|---|---|
| `useEducation` | true면 노출 | true일 때만 저장 허용 | 최소 1개 권장 | true면 포함 | DRAFT는 허용, submit은 불가 권장 |
| `useCareer` | true면 노출 | true일 때만 저장 허용 | `CareerType.NOT_SELECTED` 실패 후보, `EXPERIENCED`는 Career row 최소 1개 후보 | true면 포함 | DRAFT는 허용, submit 정책은 Phase 03c-7에서 확정 |
| `useCertificate` | true면 노출 | true일 때만 저장 허용 | 기본 선택 | true면 포함 | 허용 |
| `useLanguage` | true면 노출 | true일 때만 저장 허용 | 기본 선택 | true면 포함 | 허용 |
| `useMilitary` | true면 노출 | true일 때만 저장 허용 | `ApplicationMilitary` 1건 필수 | true면 포함 | DRAFT는 허용, submit은 불가 |
| `useAward` | true면 노출 | true일 때만 저장 허용 | 기본 선택 | true면 포함 | 허용 |
| `useGapPeriod` | true면 노출 | true일 때만 저장 허용 | 기본 선택 | true면 포함 | 허용 |

추천 정책:

- `useXxx=false`이면 지원자 화면에서 숨기고 저장 API도 차단한다.
- `useXxx=false`인 섹션은 최종제출 필수 검증 대상에서 제외한다.
- 과거에 저장된 데이터가 있는데 공고 설정이 `false`로 바뀐 경우, submit validator는 해당 섹션을 필수로 요구하지 않는다. 표시/삭제/무시 정책은 운영 정책 확정 후 정한다.
- DRAFT 상태에서는 enabled 섹션의 빈 목록을 허용한다.
- SUBMITTED 전환 시에는 `ApplicationSubmitValidator`가 `ApplicationFormConfig`를 기준으로 섹션별 validator를 호출한다.

### Review Decisions

- `useMilitary=true`이면 submit 시 `ApplicationMilitary` 1건을 필수로 둔다.
- 병역 필수 여부는 성별로 추론하지 않는다. 지원자가 `militarySubjectType`으로 대상, 비대상, 복무완료, 면제, 해당없음 중 하나를 명시하는 구조를 추천한다.
- Career는 Phase 03c-2에서 `CareerType`을 도입해 "미선택", "신입/경력 없음", "경력 있음", "해당 없음"을 구분한다.
- `CareerType.NOT_SELECTED`는 DRAFT 저장에서는 허용하지만, submit validator에서는 실패시키는 방향을 후보로 둔다.
- `CareerType.EXPERIENCED`는 DRAFT 저장에서 Career row 빈 목록을 허용하지만, submit validator에서는 최소 1개 필수 검증을 후보로 둔다.

## 5. Section Entity Candidates

| Section | Java Entity 후보명 | DB table 후보명 | JobApplication 관계 | 주요 필드 후보 | 필수 필드 후보 | 정렬 기준 | cascade/orphanRemoval | 개인정보/암호화 | 관리자 응답 포함 | 우선순위 |
|---|---|---|---|---|---|---|---|---|---|---|
| Education | `ApplicationEducation` | `application_education` | N:1 `JobApplication` | `educationLevel`, `schoolName`, `majorName`, `degreeName`, `admissionDate`, `graduationDate`, `graduationStatus`, `dayNightType`, `campusType`, `transfer`, `countryCode`, `sortOrder` | `educationLevel`, `schoolName`, `graduationStatus`, `sortOrder` | `sortOrder ASC, id ASC` | 기본 미사용 | 학교/전공은 개인정보 성격. 기본 암호화는 보류, 관리자 표시 주의 | 포함 | 1 |
| EducationSemesterGrade | `ApplicationEducationSemesterGrade` | `application_education_semester_grade` | N:1 `ApplicationEducation` | `schoolYear`, `semester`, `earnedCredits`, `gradePoint`, `maxGradePoint`, `majorGradePoint`, `majorMaxGradePoint` | `schoolYear`, `semester`, `gradePoint`, `maxGradePoint` | `schoolYear ASC, semester ASC, id ASC` | 기본 미사용 | 성적 민감정보. 관리자 상세에서 필요 시에만 표시 | 포함 | 1 |
| CareerProfile | `ApplicationCareerProfile` | `application_career_profile` | 1:1 `JobApplication` | `careerType` | `careerType` | 단건 | 기본 미사용 | 경력 선택 상태 | 포함 | 2 |
| Career | `ApplicationCareer` | `application_career` | N:1 `JobApplication` | `companyName`, `departmentName`, `positionTitle`, `employmentType`, `startDate`, `endDate`, `currentlyEmployed`, `responsibilities`, `resignationReason`, `sortOrder` | `companyName`, `startDate`, `currentlyEmployed`, `sortOrder` | `sortOrder ASC, id ASC` | 기본 미사용 | 경력 정보는 개인정보. 담당업무/퇴사사유 노출 주의 | 포함 | 2 |
| Certificate | `ApplicationCertificate` | `application_certificate` | N:1 `JobApplication` | `certificateName`, `issuingOrganization`, `acquiredDate`, `certificateNumber`, `expiredDate`, `scoreOrGrade`, `sortOrder` | `certificateName`, `issuingOrganization`, `acquiredDate`, `sortOrder` | `sortOrder ASC, id ASC` | 기본 미사용 | 자격번호는 마스킹 또는 암호화 검토 | 포함 | 구현 완료 |
| Language | `ApplicationLanguage` | `application_language` | N:1 `JobApplication` | `languageName`, `testName`, `score`, `grade`, `examDate`, `expiredDate`, `issuingOrganization`, `sortOrder` | `languageName`, `testName`, `examDate`, `sortOrder` | `sortOrder ASC, id ASC` | 기본 미사용 | 점수/등급은 민감도가 낮으나 관리자 노출 범위 제한 | 포함 | 구현 완료 |
| Military | `ApplicationMilitary` | `application_military` | 0..1 `JobApplication` | `militarySubjectType`, `serviceType`, `militaryBranch`, `rank`, `serviceStartDate`, `serviceEndDate`, `exemptionReason` | `militarySubjectType` | 단건 | 기본 미사용 | 면제 사유는 민감정보. 암호화/마스킹 우선 검토 | 포함 | 구현 완료 |
| Award | `ApplicationAward` | `application_award` | N:1 `JobApplication` | `awardName`, `awardingOrganization`, `awardDate`, `description`, `sortOrder` | `awardName`, `awardingOrganization`, `awardDate`, `sortOrder` | `sortOrder ASC, id ASC` | 기본 미사용 | 설명에 개인정보가 들어갈 수 있어 주의 | 포함 | 구현 완료 |
| GapPeriod | `ApplicationGapPeriod` | `application_gap_period` | N:1 `JobApplication` | `startDate`, `endDate`, `gapType`, `reason`, `description`, `sortOrder` | `startDate`, `endDate`, `gapType`, `reason`, `sortOrder` | `sortOrder ASC, id ASC` | 기본 미사용 | 사유/설명은 민감정보 가능. 관리자 노출 주의 | 포함 | 구현 완료 |
| Attachment | `ApplicationAttachment` | `application_attachment` | N:1 `JobApplication` | `originalFileName`, `storedFileName`, `storagePath`, `contentType`, `fileSize`, `attachmentType`, `sectionType`, `sectionRecordId`, `sortOrder` | `originalFileName`, `storedFileName`, `storagePath`, `contentType`, `fileSize`, `attachmentType`, `sectionType`, `sortOrder` | `sortOrder ASC, id ASC` | 기본 미사용 | `storedFileName`, `storagePath`는 내부 관리 필드로 응답 제외. 원본 파일명 마스킹은 관리자 상세 Phase에서 재검토 | 제한 포함 | 구현 완료 |

### Section Notes

#### Education

- 고등학교/대학교/석사/박사 구분은 `educationLevel` enum 후보로 시작한다.
- 대학교/석사/박사 성적은 `ApplicationEducationSemesterGrade`가 `ApplicationEducation`을 참조한다.
- 학교명과 전공명은 지원서별 작성 정보이므로 `JobApplication` 하위 기록으로 둔다.

#### EducationSemesterGrade

- 학위 구분은 `ApplicationEducation.educationLevel`에서 가져온다.
- grade row에는 별도 학위 구분 필드를 두지 않는다.
- 전체 평균/전공 평균은 기본적으로 semester grade 목록에서 계산한다.
- 조회 성능 또는 화면 고정값 요구가 생기면 `ApplicationEducation`에 cached average 필드를 추가하는 방안을 후속 검토한다.

#### Career

- 경력기간은 `startDate`, `endDate`, `currentlyEmployed`로 계산한다.
- Phase 03c 초기 구현에서는 기간 월수를 저장하지 않는다.
- Phase 03c-2 보완으로 `currentlyEmployed=true`이면 `endDate`는 null이어야 하고, `currentlyEmployed=false`이면 `endDate`는 필수로 확정했다.
- `responsibilities`, `resignationReason`은 Service 직접 호출에서도 2000자 이하로 검증한다.

#### Certificate

- 자격번호는 관리자 응답에서 기본 마스킹을 권장한다.
- 자격번호 검색이 필요하면 평문 검색이 아니라 hash 필드를 별도로 검토한다.
- Phase 03c-3 구현에서는 `expiredDate`가 있으면 `acquiredDate <= expiredDate`를 검증한다.
- `ApplicationFormConfig.useCertificate=false`이면 저장을 차단한다.

#### Language

- 점수와 등급은 시험별로 둘 중 하나만 필요한 경우가 있으므로 둘 다 nullable 후보로 둔다.
- 시험명/응시일은 제출 검증의 기본 필수 후보로 둔다.
- Phase 03c-3 구현에서는 `expiredDate`가 있으면 `examDate <= expiredDate`를 검증한다.
- `score`, `grade`는 DRAFT 저장에서는 둘 다 비어 있어도 허용하고, submit 필수 여부는 Phase 03c-7에서 재검토한다.
- `ApplicationFormConfig.useLanguage=false`이면 저장을 차단한다.

#### Military

- 성별로 병역 대상 여부를 추론하지 않는다.
- `militarySubjectType`으로 대상/비대상/면제/복무완료 등을 명시한다.
- 면제 사유는 민감정보로 취급한다.

#### Award

- 수상 내용은 긴 텍스트가 될 수 있으므로 관리자 목록에는 포함하지 않고 상세에서만 표시한다.

#### GapPeriod

- `gapType`은 `EDUCATION`, `CAREER`, `OTHER` 후보를 둔다.
- 공백기간은 다른 섹션에서 자동 산출하기보다 지원자가 사유를 직접 작성하는 application-specific record로 둔다.

#### Attachment

- 첨부파일은 `JobApplication` 소속 metadata로 시작한다.
- 특정 섹션에 붙는 파일 힌트는 `sectionType`, `sectionRecordId`로 저장한다.
- `sectionType=APPLICATION`이면 `sectionRecordId`는 null이어야 한다.
- `sectionType!=APPLICATION`이면 DRAFT 저장에서는 `sectionRecordId` null을 허용한다. 값이 있으면 1 이상이어야 한다.
- `sectionRecordId`가 실제 섹션 row에 존재하는지 검증은 파일 업로드/관리자 상세/섹션별 첨부 정책 확정 후 보완한다.
- `storedFileName`, `storagePath`는 내부 관리 필드로 저장하되 지원자 응답에는 노출하지 않는다.
- 현재 `ApplicationFormConfig`에 attachment flag가 없으므로 Attachment는 config enabled 검증을 하지 않는다.
- 실제 파일 업로드, 저장소, 바이러스 검사, 다운로드 권한은 별도 Phase로 분리한다.

### Enum and Code Policy

Phase 03c 초기 구현에서는 Java enum으로 시작하는 것을 추천한다. CommonCode는 아직 구현되어 있지 않고, 상세 섹션을 작은 vertical slice로 검증해야 하므로 enum이 테스트와 도메인 규칙 고정에 유리하다.

후보 enum:

- `EducationLevel`
- `GraduationStatus`
- `DayNightType`
- `CampusType`
- `EmploymentType`
- `MilitarySubjectType`
- `MilitaryServiceType`
- `GapType`
- `AttachmentType`
- `ApplicationSectionType`

주의:

- enum name은 저장/비즈니스 규칙용 값으로 두고, 화면 표시명은 enum 자체에 강하게 묶지 않는다.
- 회사 내부 공통코드 관리 요구가 확정되면 후속 Phase에서 CommonCode로 전환할 수 있게 DTO/API 문서에는 표시명보다 code 값을 중심으로 남긴다.
- DB 저장은 기존 프로젝트 방향과 동일하게 `EnumType.STRING`을 우선 검토한다.

## 6. Aggregate and Relationship Design

추천안:

- 상세 섹션 Entity는 `JobApplication`을 `@ManyToOne(fetch = FetchType.LAZY)`로 참조한다.
- `JobApplication`에는 처음부터 모든 `List<ApplicationEducation>` 같은 컬렉션을 추가하지 않는다.
- cascade/orphanRemoval은 초기에는 사용하지 않는다.
- 섹션별 Service가 `JobApplication` 소유자와 상태를 검증한 뒤 repository로 명시적으로 저장/삭제한다.
- 다중 row 섹션은 DRAFT에서 section-level replace 방식을 우선한다.
- 개별 삭제가 필요하면 HTTP `DELETE` 대신 `POST /applications/{applicationId}/{section}/{sectionId}/delete` command 후보를 사용한다.

### Replace Save Policy

cascade/orphanRemoval을 초기에는 사용하지 않으므로 replace 저장은 반드시 Service에서 명시적으로 처리한다.

다중 row 섹션 replace 절차:

1. `applicationId` 기준 기존 section row 전체를 조회한다.
2. 기존 row를 `repository.deleteAll(...)` 또는 `deleteByApplicationId(...)`로 명시 삭제한다.
3. 새 요청 목록의 `sortOrder`, 중복, 필수값을 검증한다.
4. 새 Entity 목록을 생성한다.
5. `saveAll(...)`로 저장한다.
6. 전체 과정을 하나의 `@Transactional` 안에서 처리한다.

Education + SemesterGrade replace 절차:

1. `applicationId` 기준 기존 Education 목록을 조회한다.
2. 기존 Education id 기준으로 기존 `ApplicationEducationSemesterGrade`를 먼저 명시 삭제한다.
3. 그 다음 기존 Education을 명시 삭제한다.
4. 새 Education 요청과 하위 SemesterGrade 요청을 검증한다.
5. 새 Education을 저장한 뒤, 저장된 Education을 참조하는 SemesterGrade를 저장한다.

이 순서를 지키지 않으면 cascade/orphanRemoval을 쓰지 않는 구조에서 orphan semester grade 데이터가 남을 수 있다.

이유:

- `JobApplication` 루트가 지나치게 커지는 것을 막을 수 있다.
- 관리자 목록/상세 조회에서 collection fetch 문제가 생기는 것을 피할 수 있다.
- 섹션별 vertical slice 구현과 테스트가 쉬워진다.
- 개인정보/마스킹 정책을 섹션별로 분리하기 쉽다.

## 7. API Candidate

### Option A. 섹션별 API

예시:

| Method | Path | 목적 |
|---|---|---|
| GET | `/applications/{applicationId}/educations` | 내 학력 목록 조회 |
| POST | `/applications/{applicationId}/educations` | 학력 목록 저장 또는 replace |
| POST | `/applications/{applicationId}/educations/{educationId}/delete` | 학력 삭제 command |
| GET | `/applications/{applicationId}/careers` | 내 경력 목록 조회 |
| POST | `/applications/{applicationId}/careers` | 경력 목록 저장 또는 replace |
| GET | `/applications/{applicationId}/certificates` | 내 자격 목록 조회 |
| POST | `/applications/{applicationId}/certificates` | 자격 목록 저장 또는 replace |
| GET | `/applications/{applicationId}/languages` | 내 어학 목록 조회 |
| POST | `/applications/{applicationId}/languages` | 어학 목록 저장 또는 replace |
| GET | `/applications/{applicationId}/military` | 내 병역 정보 조회 |
| POST | `/applications/{applicationId}/military` | 병역 정보 저장 또는 replace |
| GET | `/applications/{applicationId}/awards` | 내 수상 목록 조회 |
| POST | `/applications/{applicationId}/awards` | 수상 목록 저장 또는 replace |
| GET | `/applications/{applicationId}/gap-periods` | 내 공백기간 목록 조회 |
| POST | `/applications/{applicationId}/gap-periods` | 공백기간 목록 저장 또는 replace |
| GET | `/applications/{applicationId}/attachments` | 내 첨부 metadata 조회 |
| POST | `/applications/{applicationId}/attachments` | 첨부 metadata 저장 후보 |

장점:

- 구현 단위가 작고 테스트가 쉽다.
- `ApplicationFormConfig` flag별 저장 차단을 명확히 적용할 수 있다.
- 화면이 탭/섹션 단위일 때 불필요한 데이터를 덜 조회한다.

단점:

- API 수가 많아진다.
- 최종 검토 화면에서는 여러 API를 호출해야 할 수 있다.

### Option B. 전체 details API

예시:

| Method | Path | 목적 |
|---|---|---|
| GET | `/applications/{applicationId}/details` | 지원서 상세 전체 조회 |
| POST | `/applications/{applicationId}/details` | 지원서 상세 전체 저장 |

장점:

- 최종 검토 화면에서 편하다.
- 프론트엔드 저장 모델이 단순할 수 있다.

단점:

- DTO가 커지고 부분 실패/부분 저장 정책이 어려워진다.
- disabled 섹션 처리와 validation 오류 응답이 복잡해진다.
- 동시 수정 충돌 범위가 커진다.

### Recommendation

초기 구현은 섹션별 API를 추천한다. 이후 최종 검토 화면 편의를 위해 읽기 전용 aggregate API인 `GET /applications/{applicationId}/details`를 추가할 수 있다. 전체 POST 저장은 상세 섹션 정책이 안정된 뒤 다시 검토한다.

## 8. Status Edit Policy

`JobApplicationStatus` 기준 상세 섹션 수정 가능 정책은 다음과 같다.

| Status | 조회 | 생성/수정/삭제 | 비고 |
|---|---|---|---|
| `DRAFT` | 가능 | 가능 | 접수기간과 공고 상태 검증은 기존 Application command 정책과 동일하게 적용 |
| `SUBMITTED` | 가능 | 불가 | 제출 후 수정은 관리자 반려/수정요청 Phase 전까지 금지 |
| `WITHDRAWN` | 가능 | 불가 | 철회 후 재제출/수정은 현재 정책상 금지 |

추천 기본값:

- 상세 섹션은 `DRAFT` 상태에서만 수정 가능하다.
- `SUBMITTED`, `WITHDRAWN`은 조회만 가능하다.
- 최종제출 후 수정 요청/반려/재작성은 별도 Phase에서 설계한다.

## 9. Submit Validation Design

Phase 03a-2의 `JobApplicationService.submit()`은 현재 상세 섹션 필수값 검증을 수행하지 않는다. Phase 03c-7에서 다음 구조로 통합한다.

### Validator 후보

- `ApplicationSubmitValidator`
  - `JobApplication`
  - `ApplicationFormConfig`
  - 섹션별 repository 또는 section reader
  - 섹션별 validator를 조합
- `EducationSubmitValidator`
- `CareerSubmitValidator`
- `CertificateSubmitValidator`
- `LanguageSubmitValidator`
- `MilitarySubmitValidator`
- `AwardSubmitValidator`
- `GapPeriodSubmitValidator`
- `AttachmentSubmitValidator`

### 검증 정책 후보

| Section | Config enabled 시 submit 검증 추천 |
|---|---|
| Education | 최소 1개 필요. 대학교 이상이면 성적 입력 필요 여부는 공고 정책 확정 후 적용 |
| Career | `CareerType.NOT_SELECTED`는 실패 후보. `CareerType.EXPERIENCED`이면 Career row 최소 1개 필요 후보. `NEWCOMER`, `NOT_APPLICABLE`은 row 없이 통과 후보 |
| Certificate | 기본 선택. 필수 여부 flag가 별도로 생기기 전까지 최소 1개 강제하지 않음 |
| Language | 기본 선택. 필수 여부 flag가 별도로 생기기 전까지 최소 1개 강제하지 않음 |
| Military | `ApplicationMilitary` 1건 필수. 성별로 추론하지 않고 `militarySubjectType`으로 대상/비대상/복무완료/면제/해당없음 값을 명시 |
| Award | 기본 선택 |
| GapPeriod | 기본 선택. 공백기간 자동 산출 정책이 없으므로 최소 1개 강제하지 않음 |
| Attachment | `attachmentType`별 필수 정책이 생긴 뒤 검증 |

### 예외와 응답

- 상세 섹션 submit 검증 실패는 기존 `InvalidJobApplicationException` 성격의 400 응답을 우선 사용한다.
- 다중 필드 오류가 필요하면 후속으로 validation error code list를 `ApiResponse`에 확장할지 검토한다.
- Controller validation 실패는 이미 `MethodArgumentNotValidException -> ApiResponse.fail` 구조를 사용한다.

## 10. Privacy, Encryption, and Masking

개인정보 처리 기본 정책:

- CI, ciHash, password, phoneNumber, email, address 등 계정/본인확인 개인정보는 `Applicant`/`User` 계층을 원천으로 둔다.
- `JobApplication` 상세 섹션에는 CI를 복제하지 않는다.
- 암호화 원문 개인정보는 관리자 DTO에 직접 포함하지 않는다.
- 검색이 필요한 민감정보는 hash 필드를 별도로 검토한다.
- 관리자 상세 응답은 업무상 필요한 범위만 포함하고, 자격번호/첨부 원본파일명/면제사유 등은 마스킹 또는 별도 권한으로 분리한다.

섹션별 주의:

- Education/Career: 학교명, 회사명 자체는 일반 텍스트로 시작하되 개인정보로 취급한다.
- Military: 면제 사유는 민감정보로 보고 암호화 또는 마스킹을 우선 검토한다.
- Certificate: 자격번호는 마스킹 또는 암호화 후보이다.
- Attachment: 저장 경로를 응답에 직접 노출하지 않는다. 다운로드는 권한 검증 API를 통해 처리한다.

## 11. Admin Detail Expansion

현재 `AdminApplicationDetailResponse`는 Application 루트 정보만 포함한다.

### Option A. AdminApplicationDetailResponse에 모든 섹션 list 포함

장점:

- 한 번의 호출로 관리자 상세 화면을 구성할 수 있다.

단점:

- 응답이 커지고 N+1/fetch 전략이 복잡해진다.
- 섹션 추가 때마다 루트 DTO 변경 폭이 커진다.

### Option B. 관리자 섹션별 API 분리

예시:

- `GET /admin/applications/{applicationId}/educations`
- `GET /admin/applications/{applicationId}/careers`
- `GET /admin/applications/{applicationId}/attachments`

장점:

- 성능과 권한 제어가 섹션별로 명확하다.
- 민감정보 섹션만 별도 권한/마스킹 정책을 적용하기 쉽다.

단점:

- 화면에서 여러 API를 호출해야 한다.

### Option C. 루트 상세 + 섹션별 lazy 조회

추천안은 Option C이다.

- `GET /admin/applications/{applicationId}`는 계속 루트 정보를 반환한다.
- 상세 섹션은 섹션별 관리자 조회 API로 lazy loading한다.
- 화면 요구가 확정되면 읽기 전용 aggregate API를 추가할 수 있다.

## 12. Implementation Phase Split

| Phase | 구현 범위 | 미구현 범위 | 테스트 방향 |
|---|---|---|---|
| Phase 03c-1 | Education + EducationSemesterGrade vertical slice 구현 완료 | 다른 섹션, submit 통합 검증 전체 | 학력 저장/조회/replace, 성적 정렬/검증, `useEducation`, DRAFT 상태, 타인 지원서 차단 |
| Phase 03c-2 | Career vertical slice 구현 완료 | 다른 섹션, submit 통합 검증 전체 | 경력 선택 상태, 경력 기간 검증, 재직중 endDate 정책, `useCareer`, DRAFT 상태, 타인 지원서 차단 |
| Phase 03c-3 | Certificate + Language vertical slice 구현 완료 | 다른 섹션, submit 통합 검증 전체 | 취득일/응시일/만료일 검증, 정렬, config 연동 |
| Phase 03c-4 | Military vertical slice 구현 완료 | Award, GapPeriod, Attachment | 단건 병역 upsert, `useMilitary=true` submit 필수 1건 정책, 민감정보 응답 정책 |
| Phase 03c-4R | 상세 섹션 공통 접근/수정 가능 helper 구현 완료 | SectionType enum 일반화, submit validator | 본인 지원서 조회, DRAFT/PUBLISHED/접수기간, config enabled 검증 |
| Phase 03c-5 | Award + GapPeriod vertical slice 구현 완료 | Attachment, submit 통합 검증 전체 | 수상/공백기간 기간 검증, 정렬, config 연동 |
| Phase 03c-6 | Attachment metadata 구현 완료 | 실제 파일 업로드/다운로드 저장소 연동 | metadata 저장/조회, 저장 경로 비노출, attachmentType 검증 |
| Phase 03c-7 | `ApplicationSubmitValidator` 통합 | 관리자 aggregate 상세 | `submit()`에서 config 기반 섹션 필수 검증 실패/성공 |
| Phase 03c-8 | 관리자 상세 섹션 조회 API 확장 | StageResult | 관리자 섹션별 조회, 마스킹, 권한 보완 TODO |
| Phase 03c-9 | 질문답변 도메인 설계 완료 | Java 구현, DB schema, API 구현 | `QuestionTemplate` + `JobPostingQuestion` + `ApplicationAnswer` 추천 구조, submit 연동 방향, 관리자 답변 조회 후보 |
| Phase 03c-9-1 | 질문 템플릿/공고 질문 구성 구현 완료 | 지원자 답변 저장, submit 연동 | 관리자 질문 구성 API와 템플릿 API |
| Phase 03c-9-2 | 지원자 질문 목록/답변 저장 구현 후보 | 관리자 답변 조회, submit 연동 | `ApplicationAnswer`, 지원자 질문/답변 API |

## 13. Deferred Items

- Question/answer remaining work: connect answer required/blank/maxLength validation to `ApplicationSubmitValidator`, then add admin answer lazy read API.
- 상세 섹션별 required flag 세분화
- Career submit 정책: `NOT_SELECTED` 실패 여부와 `EXPERIENCED` 최소 1개 필수 여부
- 최종제출 후 수정요청/반려/reopen 정책
- 첨부파일 실제 업로드/저장소/다운로드/바이러스 검사
- 상세 섹션 개인정보 암호화 대상 최종 확정
- 관리자 상세 aggregate API
- StageResult 구현
- 보안 Phase에서 관리자/지원자 권한 검증 강화

## 14. Recommended Next Phase

Phase 03c-9-2 has completed the applicant `ApplicationAnswer` and question/answer APIs. The next implementation recommendation is Phase 03c-9-3: connect active required `JobPostingQuestion` answer blank/maxLength validation to `ApplicationSubmitValidator`. After that, implement the admin answer lazy read API. Before StageResult implementation, confirm whether Application detail read models and reports include question/answer content.


