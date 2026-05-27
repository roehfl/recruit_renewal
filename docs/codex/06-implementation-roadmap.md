# 06. Implementation Roadmap

이 문서는 2026-05-26 현재 코드와 `docs/codex/implementation` 산출물을 기준으로 다시 정리한 구현 로드맵이다.

기준 문서:

- `docs/codex/01-project-context.md`
- `docs/codex/02-domain-design.md`
- `docs/codex/03-legacy-feature-map.md`
- `docs/codex/07-implementation-history.md`

정합성 원칙:

- 이 문서는 현재 진행상황의 상위 로드맵이다.
- 실제 구현 상세는 각 phase별 `docs/codex/implementation/*.md`가 기준이다.
- 완료 여부는 Java source, test source, API controller, phase 문서 존재 여부를 함께 보고 판단한다.
- 설계만 완료된 항목은 구현 완료로 표시하지 않는다.
- migration framework가 없으므로 persistent DB 반영이 필요한 항목은 “수동 DDL 필요”로 별도 표기한다.

---

## 1. 현재 구현 요약

현재 백엔드는 초기 scaffold 단계를 넘어 채용 공고, 전형 단계, 지원서 작성, 지원서 상세 섹션, 질문/답변, 전형 결과, 지원자 대시보드, 첨부파일 업로드/다운로드/삭제/상태 점검, 공고 표시 정책, 지원서 항목 필수 정책, 첨부 필수 정책까지 구현되어 있다.

아직 남아있는 큰 축은 면접 일정/평가, 메시지 발송, 엑셀/PDF/통계, 개인정보 파기/감사 로그, 공통코드 관리다.

### 1.1 완료된 기반 기능

| 영역 | 상태 | 현재 구현 |
| --- | --- | --- |
| 인증 기본 흐름 | 부분 완료 | Session 기반 `/auth/login`, `/auth/logout`, `/auth/me`, 지원자 DB 인증, 임직원 LDAP/JIT 후보 구조 |
| 보안 응답/인가 hardening | 부분 완료 | URL 인가 강화, JSON 401/403 응답, StageResult actor propagation 일부 구현 |
| 메뉴 | 완료 | 메뉴 tree/detail/breadcrumb, 관리자 메뉴 생성/수정 |
| 공지사항 | 완료 | 목록/상세/등록 |
| 공통 응답/예외 | 완료 | `ApiResponse<T>`, `PageResponse<T>`, `GlobalExceptionHandler` |
| 암호화/해시 | 완료 | AES converter, crypto holder, SHA-256 hash helper |

### 1.2 완료된 채용 도메인 기능

| 영역 | 상태 | 대표 산출물 |
| --- | --- | --- |
| 채용 공고 관리자 CRUD | 완료 | `phase-01a-job-posting.md` |
| 채용 공고 public 조회 | 완료 | `phase-01b-job-posting-public-read.md` |
| 공고 도메인 확장 | 완료 | `phase-03j-1-job-posting-domain-expansion-status.md` |
| public 공고 노출/필터/정렬 | 완료 | `phase-03j-2-public-job-posting-exposure-status.md` |
| 공고별 모집분야 | 완료 | `JobPosition`, admin/public response |
| 지원서 항목 사용/필수 정책 | 완료 | `phase-03k-2-application-form-required-policy.md` |
| 첨부 요구사항 정책 | 완료 | `phase-03i-5-2-attachment-required-policy.md` |

### 1.3 완료된 지원서 기능

| 영역 | 상태 | 대표 산출물 |
| --- | --- | --- |
| 지원서 생성/조회 | 완료 | `phase-03a-1-application-basic-create-read.md` |
| 지원서 임시저장/제출/철회 | 완료 | `phase-03a-2-application-commands.md`, `phase-03a-3-application-api.md` |
| 관리자 지원서 목록/상세 | 완료 | `phase-03b-1-admin-application-read.md` |
| 학력 | 완료 | `phase-03c-1-application-education.md` |
| 경력 | 완료 | `phase-03c-2-application-career.md` |
| 자격/어학 | 완료 | `phase-03c-3-application-certificate-language.md` |
| 병역 | 완료 | `phase-03c-4-application-military.md` |
| 수상/공백기간 | 완료 | `phase-03c-5-application-award-gap-period.md` |
| 첨부 metadata | 완료 | `phase-03c-6-application-attachment-metadata.md` |
| 제출 validator | 완료 | `phase-03c-7-application-submit-validator.md` |
| 관리자 섹션 read | 완료 | `phase-03c-8-admin-application-section-read.md` |
| 질문 템플릿/공고 질문 | 완료 | `phase-03c-9-1-question-template-job-posting-question.md` |
| 지원자 답변 | 완료 | `phase-03c-9-2-application-answer.md` |
| 답변 제출 검증 | 완료 | `phase-03c-9-3-answer-submit-validator.md` |
| 관리자 답변 조회 | 완료 | `phase-03c-9-4-admin-answer-read.md` |
| 내 지원서 목록 | 완료 | `phase-03h-2-applicant-my-applications.md` |
| 지원자 대시보드 | 완료 | `phase-03h-4-applicant-application-dashboard.md` |

### 1.4 완료된 전형/결과 기능

| 영역 | 상태 | 대표 산출물 |
| --- | --- | --- |
| 전형 단계 CRUD | 완료 | `phase-02a-1-stage-basic-crud.md` |
| 전형 단계 명령 | 완료 | `phase-02a-2-stage-command.md` |
| 전형 API 테스트 | 완료 | `phase-02a-3-stage-api-test.md` |
| 결과 초기화/목록 | 완료 | `phase-03d-1-stage-result-initialize-list.md` |
| 결과 수정/발표 guard | 완료 | `phase-03d-2-stage-result-update-announce-guard.md` |
| 관리자 지원서 timeline | 완료 | `phase-03d-3-admin-application-stage-result-timeline.md` |
| 지원자 결과 조회 | 완료 | `phase-03d-4-applicant-stage-result-read.md` |
| 결과 정정 이력 | 완료 | `phase-03d-5-result-correction-history.md` |

### 1.5 완료된 첨부파일 기능

| 영역 | 상태 | 대표 산출물 |
| --- | --- | --- |
| 첨부 metadata | 완료 | `phase-03c-6-application-attachment-metadata.md` |
| 물리 파일 업로드 | 완료 | `phase-03i-2-attachment-file-upload.md` |
| 파일 다운로드 | 완료 | `phase-03i-3-attachment-file-download.md` |
| soft delete | 완료 | `phase-03i-4-2-attachment-delete-command.md` |
| storage health scan dry-run | 완료 | `phase-03i-4-3-attachment-storage-health-scan.md` |
| 첨부 필수 정책/dashboard/submit 연동 | 완료 | `phase-03i-5-2-attachment-required-policy.md` |

---

## 2. 현재 API 범위

아래는 현재 controller source 기준의 주요 API 범위다.

### 2.1 Auth / Menu / Notice

| Method | Path | 상태 |
| --- | --- | --- |
| `POST` | `/auth/login` | 구현 |
| `POST` | `/auth/logout` | 구현 |
| `GET` | `/auth/me` | 구현 |
| `GET` | `/menu/tree` | 구현 |
| `GET` | `/menu/{menuId}` | 구현 |
| `GET` | `/menu/breadcrumb` | 구현 |
| `POST` | `/menu/admin/menu` | 구현 |
| `POST` | `/menu/admin/menu/{menuId}` | 구현 |
| `GET` | `/board/notices` | 구현 |
| `GET` | `/board/notices/{noticeId}` | 구현 |
| `POST` | `/board/notices` | 구현 |

### 2.2 JobPosting / Question / Attachment Policy

| Method | Path | 상태 |
| --- | --- | --- |
| `GET` | `/admin/job-postings` | 구현 |
| `GET` | `/admin/job-postings/{id}` | 구현 |
| `POST` | `/admin/job-postings` | 구현 |
| `POST` | `/admin/job-postings/{id}` | 구현 |
| `POST` | `/admin/job-postings/{id}/publish` | 구현 |
| `POST` | `/admin/job-postings/{id}/close` | 구현 |
| `GET` | `/job-postings` | 구현 |
| `GET` | `/job-postings/{id}` | 구현 |
| `GET` | `/admin/job-postings/{jobPostingId}/questions` | 구현 |
| `POST` | `/admin/job-postings/{jobPostingId}/questions` | 구현 |
| `POST` | `/admin/job-postings/{jobPostingId}/questions/{questionId}` | 구현 |
| `POST` | `/admin/job-postings/{jobPostingId}/questions/reorder` | 구현 |
| `POST` | `/admin/job-postings/{jobPostingId}/questions/{questionId}/delete` | 구현 |
| `GET` | `/admin/question-templates` | 구현 |
| `GET` | `/admin/question-templates/{templateId}` | 구현 |
| `POST` | `/admin/question-templates` | 구현 |
| `POST` | `/admin/question-templates/{templateId}` | 구현 |
| `POST` | `/admin/question-templates/{templateId}/deactivate` | 구현 |
| `GET` | `/admin/job-postings/{jobPostingId}/attachment-requirements` | 구현 |
| `POST` | `/admin/job-postings/{jobPostingId}/attachment-requirements` | 구현 |
| `GET` | `/admin/job-postings/{jobPostingId}/application-form-layout` | 구현 |
| `POST` | `/admin/job-postings/{jobPostingId}/application-form-layout` | 구현 |
| `GET` | `/admin/job-postings/{jobPostingId}/application-form-layout/preview` | 구현 |

### 2.3 Stage / StageResult

| Method | Path | 상태 |
| --- | --- | --- |
| `GET` | `/admin/job-postings/{jobPostingId}/stages` | 구현 |
| `GET` | `/admin/job-postings/{jobPostingId}/stages/{stageId}` | 구현 |
| `POST` | `/admin/job-postings/{jobPostingId}/stages` | 구현 |
| `POST` | `/admin/job-postings/{jobPostingId}/stages/{stageId}` | 구현 |
| `POST` | `/admin/job-postings/{jobPostingId}/stages/reorder` | 구현 |
| `POST` | `/admin/job-postings/{jobPostingId}/stages/{stageId}/start` | 구현 |
| `POST` | `/admin/job-postings/{jobPostingId}/stages/{stageId}/announce` | 구현 |
| `POST` | `/admin/job-postings/{jobPostingId}/stages/{stageId}/close` | 구현 |
| `POST` | `/admin/job-postings/{jobPostingId}/stages/{stageId}/delete` | 구현 |
| `GET` | `/admin/stages/{stageId}/results` | 구현 |
| `POST` | `/admin/stages/{stageId}/results/initialize` | 구현 |
| `POST` | `/admin/stages/{stageId}/results/{resultId}` | 구현 |
| `POST` | `/admin/stages/{stageId}/results/bulk` | 구현 |
| `POST` | `/admin/stages/{stageId}/results/{resultId}/correct` | 구현 |
| `GET` | `/admin/stages/{stageId}/results/{resultId}/histories` | 구현 |
| `GET` | `/applications/{applicationId}/stage-results` | 구현 |

### 2.4 Application / Application Sections

| Method | Path | 상태 |
| --- | --- | --- |
| `POST` | `/applications` | 구현 |
| `GET` | `/applications/{applicationId}` | 구현 |
| `GET` | `/applications/me` | 구현 |
| `GET` | `/applications/{applicationId}/dashboard` | 구현 |
| `POST` | `/applications/{applicationId}` | 구현 |
| `POST` | `/applications/{applicationId}/submit` | 구현 |
| `POST` | `/applications/{applicationId}/withdraw` | 구현 |
| `GET` | `/job-postings/{jobPostingId}/application` | 구현 |
| `GET/POST` | `/applications/{applicationId}/educations` | 구현 |
| `GET/POST` | `/applications/{applicationId}/careers` | 구현 |
| `GET/POST` | `/applications/{applicationId}/certificates` | 구현 |
| `GET/POST` | `/applications/{applicationId}/languages` | 구현 |
| `GET/POST` | `/applications/{applicationId}/military` | 구현 |
| `GET/POST` | `/applications/{applicationId}/awards` | 구현 |
| `GET/POST` | `/applications/{applicationId}/gap-periods` | 구현 |
| `GET` | `/applications/{applicationId}/questions` | 구현 |
| `POST` | `/applications/{applicationId}/answers` | 구현 |
| `GET` | `/applications/{applicationId}/form-page` | 구현 |

### 2.5 Attachment / Admin Application Read

| Method | Path | 상태 |
| --- | --- | --- |
| `GET` | `/applications/{applicationId}/attachments` | 구현 |
| `POST` | `/applications/{applicationId}/attachments` | metadata replace 구현 |
| `POST` | `/applications/{applicationId}/attachments/file` | physical upload 구현 |
| `GET` | `/applications/{applicationId}/attachments/{attachmentId}/download` | 구현 |
| `POST` | `/applications/{applicationId}/attachments/{attachmentId}/delete` | 구현 |
| `GET` | `/admin/applications` | 구현 |
| `GET` | `/admin/applications/{applicationId}` | 구현 |
| `GET` | `/admin/job-postings/{jobPostingId}/applications` | 구현 |
| `GET` | `/admin/applications/{applicationId}/attachments` | 구현 |
| `GET` | `/admin/applications/{applicationId}/attachments/{attachmentId}/download` | 구현 |
| `POST` | `/admin/applications/{applicationId}/attachments/{attachmentId}/delete` | 구현 |
| `POST` | `/admin/attachments/storage-health/scan` | dry-run 구현 |

---

## 3. 완료/부분완료/미구현 분류

### 3.1 완료로 보는 범위

다음 범위는 entity, repository, service, controller, DTO, test, implementation 문서가 존재하므로 완료 범위로 본다.

- JobPosting 기본 CRUD/public read/domain expansion/public exposure
- JobPosition metadata
- ApplicationFormConfig use/require split
- Stage 기본 CRUD/명령/API
- StageResult 초기화/조회/수정/bulk/정정/지원자 조회
- JobApplication 생성/조회/임시저장/제출/철회/내 지원서 목록
- Admin application list/detail/section lazy read
- Education/Career/Certificate/Language/Military/Award/GapPeriod
- QuestionTemplate/JobPostingQuestion/ApplicationAnswer
- Submit validator와 dashboard readiness
- ApplicationAttachment metadata/upload/download/delete/storage health scan
- JobPostingAttachmentRequirement와 첨부 필수 dashboard/submit 연동

### 3.2 부분 완료로 보는 범위

| 영역 | 완료된 부분 | 남은 부분 |
| --- | --- | --- |
| 인증/인가 | 세션 인증, DB/LDAP 인증 구조, URL 인가 강화, JSON 401/403 | 실제 운영 권한 매트릭스 최종 확정, 관리자/면접관 세부 권한, role mapping 운영 검증 |
| 관리자 actor/audit | StageResult actor propagation 일부 | 전체 admin command audit, immutable activity log |
| 첨부 lifecycle | upload/download/soft delete/dry-run scan | repair/cleanup commit command, 삭제 이력 별도 테이블 여부 |
| 지원서 정책 | use/require split, attachment requirement | sectionRecordId 단위 첨부 요구, 조건부 요구, versioning |
| 운영 DB 적용 | H2 generated schema/test | persistent DB 반영이 필요한 변경은 각 구현 문서의 수동 DDL/운영 메모 기준으로 별도 적용 필요 |

### 3.3 미구현으로 보는 범위

| 우선순위 | 영역 | 남은 작업 |
| --- | --- | --- |
| 1 | 면접 일정/조 | `Interview`, `InterviewParticipant` entity/API, 면접관 배정, 지원자 배정 |
| 2 | 면접 평가 | `InterviewEvaluation`, 면접관별 임시저장/제출, 관리자 조회 |
| 3 | 메시지 발송 | `MessageBatch`, `MessageSendLog`, SMS/Email/알림톡 adapter boundary |
| 4 | 엑셀/PDF/통계 | 지원자 목록/전형 결과/면접 배정 download/upload, 지원서 PDF, 지원율/합격률/학교/자격/전형 단계별 통계 |
| 5 | 개인정보 파기/감사 | retention policy, bulk/single purge, 파기 이력, 접근/변경 감사 로그, correlation/trace id 정책 |
| 6 | CommonCode/School | 공통코드 관리, 학교 master import/search |
| 7 | 운영 hardening | public/admin/interviewer 권한 matrix, 보안 regression, 운영 profile 검증 |

---

## 4. 앞으로의 권장 로드맵

기존 로드맵의 Phase 01~03k는 대부분 완료되었다. 이후는 현재 코드의 빈 영역을 기준으로 아래 순서를 권장한다.

Roadmap revision note - 2026-05-26:

- The numbered next-phase sequence is now:
  - Phase 05 - Application Form Page Layout
  - Phase 06 - Interview Evaluation
  - Phase 07 - Export, PDF, Statistics
  - Phase 08 - CommonCode And School Master
- Messaging and privacy/audit/retention remain valid backlog domains but are not numbered phases in this revision.

### Phase 04 - Interview Scheduling

Status:

| Slice | Status | Scope |
| --- | --- | --- |
| 04a - Interview Scheduling Domain | Completed | `Interview`/`InterviewParticipant`, enums, entities, repositories, targeted tests |
| 04b - Admin Interview Schedule Management | Completed | Admin create/update/read, participant replace, confirm/cancel guards |
| 04c - Applicant Interview Read | Completed | Applicant-owned confirmed/cancelled schedule read, DRAFT hiding, applicant-safe response |
| 04d - Interviewer Interview Read | Completed | Interviewer-owned confirmed/cancelled schedule read, assigned candidate list, interviewer-safe response |
| 04e - Interview Scheduling Stabilization / Test Hardening | Completed | Cross-slice consistency, cancelled schedule edge cases, participant lifecycle regression, authorization matrix |

목적:

- 면접 일정/조/참가자 배정 기반을 만든다.

범위:

- `Interview`
- `InterviewParticipant`
- 관리자 면접 일정 CRUD
- 지원자/면접관 participant 배정
- stage/application과의 연결 규칙
- 이전 전형 `StageResult` 합격/발표 가시성 기반 지원자 배정 자격 검증
- Stage 상태별 생성/확정/취소 guard
- `StageResult` 비변경 정책

Out of scope:

- 면접 평가 점수/제출
- `StageResult` 생성/수정/발표/정정
- 메시지 발송
- 엑셀 upload/download
- PDF/calendar/frontend

설계 산출물:

- `docs/codex/design/phase-04-interview-scheduling-design.md`
- `docs/codex/reports/phase-04-interview-scheduling-design.html`

Implementation outputs:

- `docs/codex/implementation/phase-04a-interview-scheduling-domain.md`
- `docs/codex/reports/phase-04a-interview-scheduling-domain.html`
- `docs/codex/implementation/phase-04b-admin-interview-schedule-management.md`
- `docs/codex/reports/phase-04b-admin-interview-schedule-management.html`
- `docs/codex/implementation/phase-04c-applicant-interview-read.md`
- `docs/codex/reports/phase-04c-applicant-interview-read.html`
- `docs/codex/implementation/phase-04d-interviewer-interview-read.md`
- `docs/codex/reports/phase-04d-interviewer-interview-read.html`
- `docs/codex/implementation/phase-04e-interview-scheduling-stabilization.md`
- `docs/codex/reports/phase-04e-interview-scheduling-stabilization.html`

04b Admin Interview Schedule Management completed scope:

- admin schedule create/update/read service and API
- participant replace for DRAFT schedules
- confirm/cancel commands
- `StageType` allowlist guard
- `StageStatus` command guard
- candidate eligibility checks
- duplicate assignment and confirmed-schedule collision checks
- `StageResult` non-mutation regression

04c Applicant Interview Read completed scope:

- applicant-owned schedule read API
- hide `DRAFT` schedules
- return only assigned `CONFIRMED` and `CANCELLED` schedules
- hide other candidates, interviewer lists, and admin memo
- keep `StageResult` unchanged

04d Interviewer Interview Read completed scope:

- interviewer-owned assigned schedule read API
- hide `DRAFT` schedules
- return only assigned `CONFIRMED` and `CANCELLED` schedules
- hide admin memo and `StageResult` internals
- keep APIs read-only
- expose assigned candidate list in interviewer detail only

04e Interview Scheduling Stabilization / Test Hardening completed scope:

- cross-slice cancellation regression confirms applicant and interviewer visibility after admin cancel
- participant lifecycle regression confirms cancel keeps candidate/interviewer rows `ASSIGNED`
- `StageResult` non-mutation regression confirms scheduling cancel/read flows do not change result state
- authorization matrix review covers admin, applicant, employee/interviewer, and anonymous boundaries
- Phase 04 is ready to hand off to `Phase 05 - Application Form Page Layout`

### Phase 05 - Application Form Page Layout

Status:

| Slice | Status | Scope |
| --- | --- | --- |
| 05 design - Application Form Page Layout | Completed | Page layout design, API contract, validation rules, slice breakdown |
| 05a - Application Form Layout Domain | Completed | `ApplicationFormPage`, `ApplicationFormPageItem`, section enum extension, repositories, validator, policy helper, default factory |
| 05b - Applicant Layout Read | Completed | Applicant-owned form-page bootstrap API with effective section response |
| 05c - Admin Layout Management | Completed | Admin layout read/save/preview APIs, available section response, state guard, replace-all save |
| 05d - Publish/Layout Guard Integration | Pending | Layout validation during publish and default/fallback policy |
| 05e - Layout Stabilization / Test Hardening | Pending | Validation matrix, fallback edge cases, policy regression |

Purpose:

- Design and implement backend support for page-level arrangement of applicant application form sections.

Scope:

- `ApplicationFormPage`
- `ApplicationFormPageItem`
- `ApplicationSectionType` layout section values
- `ApplicationFormLayoutValidator`
- `ApplicationFormLayoutSectionPolicy`
- `ApplicationFormLayoutDefaultFactory`
- Admin layout read/save/preview contract
- Applicant layout read contract
- use/require/layout consistency validation
- question/answer page placement using active `JobPostingQuestion`
- default layout and fallback policy
- publish/reception-start guard policy

Out of scope:

- Frontend Vue implementation
- field-level form builder
- page-level application save API
- section command API refactoring
- attachment required policy redesign
- application required policy redesign

Design outputs:

- `docs/codex/design/phase-05-application-form-page-layout-design.md`
- `docs/codex/reports/phase-05-application-form-page-layout-design.html`

Implementation outputs:

- `docs/codex/implementation/phase-05a-application-form-layout-domain.md`
- `docs/codex/reports/phase-05a-application-form-layout-domain.html`
- `docs/codex/implementation/phase-05b-application-form-page-layout.md`
- `docs/codex/reports/phase-05b-application-form-page-layout.html`
- `docs/codex/implementation/phase-05c-admin-layout-management.md`
- `docs/codex/reports/phase-05c-admin-layout-management.html`

05a completed scope:

- `ApplicationSectionType` now includes `BASIC_INFO`, `QUESTION_ANSWER`, and `ATTACHMENT`.
- `APPLICATION` and `ETC` are not allowed as layout items.
- `ApplicationFormPage` and `ApplicationFormPageItem` persist a JobPosting-owned page layout.
- `ApplicationFormPageRepository` and `ApplicationFormPageItemRepository` provide persistence access.
- `ApplicationFormLayoutValidator` validates stored layout shape and effective section consistency.
- `ApplicationFormLayoutSectionPolicy` calculates enabled/required layout sections from existing policy inputs.
- `ApplicationFormLayoutDefaultFactory` creates the default 5-group layout without saving it.
- No API, frontend, existing section save API refactoring, attachment policy change, submit policy change, or migration file was added.

05b completed scope:

- Applicant-owned `GET /applications/{applicationId:[0-9]+}/form-page` bootstrap API.
- Application/posting/position/form-config/action metadata response.
- Effective enabled/required section layout response using Phase 05a policy and factory helpers.
- Default/stored layout validation.
- Targeted service and controller tests.

05c completed scope:

- Admin layout read API with stored/default fallback and `availableSections` metadata.
- Admin layout save API with replace-all semantics and full validation.
- Admin applicant-facing preview projection with page structure.
- State guard: CLOSED and reception-started block save.
- Request DTO with Bean Validation and nested page/item records.
- Response DTOs with nested records for admin layout, preview, and section availability.
- Targeted service unit tests (13) and controller integration tests (11).

Next slice:

- `Phase 05d - Publish/Layout Guard Integration`

### Phase 06 - Interview Evaluation

목적:

- 면접관 평가 작성/임시저장/제출과 관리자 조회를 구현한다.

범위:

- `InterviewEvaluation`
- 면접관 본인 배정 건 조회
- 평가 임시저장/제출
- 관리자 평가 조회
- StageResult 반영 정책 초안

Out of scope:

- 대량 엑셀 평가 업로드
- 메시지 발송

### Backlog - Messaging History

목적:

- 결과 안내/면접 안내/정정 안내 등 메시지 발송 이력 기반을 만든다.

범위:

- `MessageBatch`
- `MessageSendLog`
- 발송 요청 저장
- external adapter boundary
- 테스트/실발송 profile 분리

Out of scope:

- 실제 운영 EAI/SMS/Email provider 상세 연동은 adapter phase로 분리 가능.

### Phase 07 - Export, PDF, Statistics

목적:

- 운영자가 필요한 대량 조회/다운로드/출력 기능을 구현한다.

범위:

- Excel download
- Excel upload preview/commit
- 지원서 PDF generation integration
- 전형/지원 통계 query

Out of scope:

- 개인정보 파기

### Backlog - Privacy, Audit, Retention

목적:

- 개인정보 파기, 접근 감사, 보관주기 정책을 구현한다.

범위:

- `ActivityLog`
- retention metadata
- bulk/single purge
- 파기 이력
- 파기 안내 메시지 연동

Out of scope:

- 통계/엑셀 신규 기능

### Phase 08 - CommonCode And School Master

목적:

- 공통코드와 학교 master data를 관리한다.

범위:

- `CommonCode`
- `School`
- master data import/search
- 공고/지원서 입력값과의 application-level validation

Out of scope:

- 면접/평가/메시지 신규 기능

---

## 5. 현재 정합성 메모

### 5.1 기존 roadmap과 달라진 점

기존 `06-implementation-roadmap.md`는 2026-05-19 전후 상태를 기준으로 작성되어 다음을 미구현으로 보았다.

- JobPosting
- Stage/StageResult
- Application
- Application detail sections
- Attachment file handling
- ApplicationFormConfig required policy

하지만 2026-05-26 현재 코드 기준으로 위 범위는 대부분 구현 완료 상태다. 따라서 이 문서에서는 해당 범위를 완료로 이동하고, 남은 로드맵을 면접 일정/평가, 메시지, 엑셀/PDF/통계, 파기/감사 중심으로 재정렬했다.

### 5.2 설계 문서와 구현 문서의 읽는 순서

향후 진행상황 파악 시에는 다음 순서로 확인한다.

1. `docs/codex/06-implementation-roadmap.md`
2. `docs/codex/07-implementation-history.md`
3. 해당 phase의 `docs/codex/implementation/*.md`
4. 해당 phase의 `docs/codex/reports/*.html`
5. source/test code

### 5.3 아직 정리 필요할 수 있는 문서

다음 문서는 초기에 작성된 기반 문서라 현재 API 전체를 모두 반영하지 않는다.

- `docs/codex/01-project-context.md`
- `docs/codex/02-domain-design.md`
- `docs/codex/03-legacy-feature-map.md`
- `docs/codex/04-implementation-guide.md`

이 문서들은 “프로젝트 기준/설계 원칙/legacy mapping”으로 유지하고, 최신 구현 현황은 이 roadmap과 implementation history를 기준으로 본다.

---

## 6. 최근 검증 상태

최근 전체 검증:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test --no-daemon
```

결과:

- `BUILD SUCCESSFUL`
- 실행 시점: 2026-05-22
- 소요: 12m 49s

이번 문서 정합성 작업은 문서 갱신만 수행하므로 별도 테스트 실행은 필수 범위가 아니다.
