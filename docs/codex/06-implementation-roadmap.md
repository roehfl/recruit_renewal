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

Roadmap revision note - 2026-06-02:

- Phase 08 (CommonCode/School master, 08a~08e 포함 — SCHOOL/CERTIFICATE funnel dimension까지)이 전체 완료되었다.
- 다음 번호 Phase는 **Phase 09 - 개인정보 파기/감사 (Privacy Purge, Audit, Retention)** 로 진행하기로 결정했다(위 §3.3 우선순위 5 영역).
  - 범위 후보: retention policy, bulk/single purge, 파기 이력, 영속 `ActivityLog`(접근/변경 감사), correlation/trace id 정책.
  - 동기: export/PDF/upload/`@Version` 충돌 등 감사 이벤트가 현재 SLF4J 임시 로그로만 남고 있어 영속 감사 기반이 필요하다.
- **설계는 아직 진행하지 않았다.** 본 노트는 다음 Phase 진행 방향만 기록한 것이며, 상세 설계(grill-with-docs)와 슬라이스 분할은 별도 세션에서 수행한다.
- 메시지 발송(우선순위 3)과 운영 hardening(우선순위 7)은 여전히 유효한 backlog로 남는다.

Roadmap revision note - 2026-06-04:

- Phase 09 (Privacy Purge / Audit / Retention) **설계 완료**(grill-with-docs). 아래 "Phase 09 - Privacy Purge / Audit / Retention" 섹션과 `docs/codex/design/phase-09-privacy-purge-audit-retention-design.md` 참조.
- 확정: 감사 우선 빌드, 파기 방식 = tombstone 익명화 + 첨부 바이너리 물리삭제(ADR-0005), 감사 트랜잭션 3-way(ADR-0006), ROLE_PRIVACY_ADMIN 분리(ADR-0007). 슬라이스 9a→9b→9c→9d-1→9d-2→9e.
- 구현 미착수. 다음 작업 = 9a(ActivityLog foundation). 신규 5 테이블 + 컬럼 확장 + `AUDIT_HMAC_SECRET`/`ROLE_PRIVACY_ADMIN` 은 전부 수동 DDL/운영 설정.

Roadmap revision note - 2026-06-05:

- **Phase 05y - Applicant Account Hardening 설계 완료**(9b 착수 전 선행 슬라이스). `docs/codex/design/phase-05y-applicant-account-hardening-design.md` 참조.
- 배경: 지원자 loginId 정책(이메일=loginId vs 별도 ID) 미결정 → **결정-독립** 계정 작업만 분리.
- 범위: `User.loginId` 유니크 무결성(임직원-지원자 loginId 충돌 결함 수정 포함), `GET /auth/applicants/check-email`, `POST /applicant/account/password`, `POST /applicant/account/phone-number`, `DataIntegrityViolationException`→409.
- 결정-의존 보류: check-login-id, 이메일 변경 API, 가입 email 필수화, 아이디 찾기.
- 2026-06-05 리뷰 1차 반영(instruction.md): loginId 정규화 정책 명시(trim only · collation 의존 제거 후속), LDAP JIT 동시 생성 race 복구(선택 B — 재조회 후 복구), 전화번호 변경 currentPassword 재확인 채택, check-email advisory 명확화, 운영 DDL 사전 점검 3종, JIT race/복구 테스트 추가.
- 2026-06-05 리뷰 2차 반영(instruction.md): JIT race 복구는 `processLdap()` 재호출(LDAP 재인증) 대신 기존 ldapUser로 `buildEmployeeAuthentication()` 토큰 생성, collation 점검을 `INFORMATION_SCHEMA.COLUMNS`로 교체, 복구 범위는 loginId race 한정(deptName unique 등 기타 제약 위반은 예외 전파).
- **2026-06-05 구현 완료** — scoped 40 tests passed. `docs/codex/implementation/phase-05y-applicant-account-hardening.md` 참조. 운영 MariaDB `uk_users_login_id` 수동 DDL 적용 항목 잔존.
- 2026-06-05 구현 리뷰 3차 반영: `ApplicantAccountControllerTest` `springSecurity()` 적용(matcher 실검증, 7/7 통과), 07-history stale 문구 정정.
- ~~후속 Fix phase 권고(3차 리뷰): `Employee.deptName` unique 제거~~ → **2026-06-05 처리 완료** — `docs/codex/implementation/fix-employee-dept-name-unique-removal.md` 참조(scoped 10 tests passed, 운영 unique 인덱스 수동 제거 항목 잔존). 구현 후 9b 진행(audit 파이프라인 비접촉 — 영향 없음).

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
| 05d - Publish/Layout Guard Integration | Completed | Layout validation during publish and default/fallback policy |
| 05e - Layout Stabilization / Test Hardening | Completed | Validation matrix, fallback edge cases, policy regression |

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

Status:

| Slice | Status | Scope |
| --- | --- | --- |
| 06 design - Interview Evaluation | Completed | Entity design, enum design, API candidates, validation rules, StageResult boundary, slice breakdown |
| 06a - InterviewEvaluation Domain | Completed | Entity, enums (EvaluationStatus/EvaluationGrade/EvaluationRecommendation), repository, unique constraint, entity validation, status transition (initialize/updateContent/submit/reopen), 27 targeted tests |
| 06b - Admin Initialize + Interviewer Evaluation Write | Completed | Admin initialize command (idempotent for sequential calls), interviewer list/detail/save/submit, participant-scoped read isolation (cancelled interviewer blocked at every entry point), CONFIRMED/ASSIGNED guard, SUBMITTED immutability, 23 targeted tests (50 with 06a regression) |
| 06c - Admin Evaluation Read | Completed | Interview/stage/application-level evaluation read, candidate-grouped response, SUBMITTED-only summary aggregation, GradeDistribution/RecommendationDistribution, interviewerName 노출, 7 targeted tests (57 with 06a/06b regression) |
| 06d - Reopen + StageResult Boundary | Completed | Admin reopen command (SUBMITTED→DRAFT, submittedAt 초기화), CONFIRMED+ASSIGNED 재개 가드, 감사 로그 기록(영속 ActivityLog는 보류), StageResult 비변경 보장(구조적), reflect 커맨드 보류, 8 targeted tests (65 with 06a~06c regression) |
| 06e - Stabilization / Test Hardening | Completed | N×M matrix + 집계 회귀, reopen→재제출 사이클, StageResult 비변경 회귀(submit/reopen/read), 기존 가드 재검증, 5 targeted tests (70 evaluation tests total) |

목적:

- 면접관 평가 작성/임시저장/제출과 관리자 조회를 구현한다.
- StageResult는 면접관 평가와 완전히 분리하여, 관리자 수동 확정 워크플로우를 기본으로 한다.

범위:

- `InterviewEvaluation` entity
- `EvaluationStatus` enum (DRAFT, SUBMITTED)
- `EvaluationGrade` enum (F, G_MINUS, G, G_PLUS, VG)
- `EvaluationRecommendation` enum (STRONG_YES, YES, NEUTRAL, NO, STRONG_NO)
- Admin evaluation initialize command
- Interviewer evaluation save/submit/list/detail
- Admin evaluation read (interview/stage/application level)
- Admin evaluation reopen command
- Candidate-grouped summary with GradeDistribution and RecommendationDistribution
- StageResult non-mutation guarantee

Out of scope:

- StageResult reflection/sync command (deferred to future phase)
- 대량 엑셀 평가 업로드
- 메시지 발송
- 평가표 템플릿 관리
- 가중치/세부 등급
- 자동 합격/불합격 판정
- 평가 row admin memo

Design outputs:

- `docs/codex/design/phase-06-interview-evaluation-design.md`
- `docs/codex/reports/phase-06-interview-evaluation-design.html`

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

Status:

| Slice | Status | Scope |
| --- | --- | --- |
| 07 design - Export, PDF, Statistics | Completed | 4개 기둥 범위/슬라이스 확정, 모집단 P 코호트·funnel 7-bucket·upload 경계·PDF 스택·PII 정책 |
| 07a - Excel Export Infra + Applications Download | Completed | POI SXSSF writer, row cap, export audit, applications download(목록+연락처). `AdminExportController` 2개 엔드포인트, formula injection 방어, temp file 스트리밍+삭제, projection Stream. 7 tests |
| 07b - Remaining Dataset Download | Completed | stage results / interviews / interview evaluations list-parity download. 기존 list 쿼리 재사용으로 parity 보장, 공용 `ExcelExportService`(materialize+row cap), 평가 읽기 전용(Phase 06 경계). `AdminDatasetExportService` + 3 엔드포인트. 10 tests |
| 07c - Statistics Funnel | Completed | overall + 분야별(POSITION FK), stage별 7-bucket 분포(+synthetic NO_RESULT, 합=|P|) + 순차 통과 집합 funnelPassedCount·누적/직전 전환 비율, P=submittedAt≠null 코호트. SCHOOL/CERTIFICATE 미지원(400). `FunnelStatisticsService` + `AdminStatisticsController`. 5 tests |
| 07d - Excel Upload (StageResult) | Pending | stateless preview/commit, all-or-nothing, 3중 교차검증, bulkUpdateResults 재사용 |
| 07e - Application PDF | Pending | admin 전용, Thymeleaf + openhtmltopdf(PDFBox) + CJK 폰트, PDF audit |
| 07f - Stabilization / Test Hardening | Pending | 회귀, PII 부재 검증, row cap·upload 경계 회귀 |

목적:

- 운영자가 필요한 대량 조회/다운로드/출력/집계 기능을 구현한다.

범위:

- Excel download (4 dataset, list-parity)
- Excel upload preview/commit (StageResult only, stateless, all-or-nothing)
- 지원서 PDF generation (admin 전용, Thymeleaf + openhtmltopdf)
- 공고 단위 전형 funnel 통계 (전체/분야별 우선)

Out of scope:

- `InterviewEvaluation` Excel upload (Phase 06 경계로 영구 제외)
- 학교별 통계 dimension (Phase 08 School master 이후)
- 개인정보 파기
- 새 entity/table/migration (전부 read-only 또는 기존 명령 재사용)

핵심 설계 결정:

- Excel = Apache POI + SXSSF(streaming) + 하드 row cap(기본 50,000, 초과 시 `400 EXPORT_ROW_LIMIT_EXCEEDED`, 조용한 truncation 금지).
- download 전부 list-parity. applications export만 `name`/`phoneNumber`/`email` 연락처 확장(export가 admin의 phone/email 최초 surface).
- 모집단 P = `submittedAt != null` 코호트(현재 status 무관, 재현 가능). funnel 단계 분포 = 7-bucket(6 resultStatus + 응답 전용 synthetic `NO_RESULT`), 합 = |P|. PASSED 기준 누적/전환 두 비율.
- Excel upload = `StageResult`만, stateless, all-or-nothing, `stageResultId`+`applicationId`+path `stageId` 3중 교차검증, 기존 `StageResultService.bulkUpdateResults` 경유(공유 DTO 불변).
- PDF = Thymeleaf + openhtmltopdf(PDFBox) + CJK 폰트 임베드, iText(AGPL) 회피, 생성 시 audit.
- `ci`/`ciHash`/`password`는 어떤 export/PDF/statistics에도 절대 미포함.

설계 산출물:

- `docs/codex/design/phase-07-export-pdf-statistics-design.md`
- `docs/codex/reports/phase-07-export-pdf-statistics-design.html`
- `CONTEXT.md` (Export/Reporting glossary)
- `docs/adr/0001-application-pdf-openhtmltopdf-avoid-itext-agpl.md`
- `docs/adr/0002-phase07-export-readonly-upload-stageresult-only.md`

### Phase 09 - Privacy Purge / Audit / Retention

설계 확정: `docs/codex/design/phase-09-privacy-purge-audit-retention-design.md`
(grill-with-docs, 2026-06-04). ADR 0005/0006/0007. `CONTEXT.md` Privacy/Audit glossary.
HTML 리포트: `docs/codex/reports/phase-09-privacy-purge-audit-retention-design.html`.
2026-06-04 리뷰 1차(instruction.md) 반영: **PII 필드 인벤토리 선행 산출물** `docs/codex/implementation/phase-09-pii-field-inventory.md`(9d 게이트), terminal query 구체화, typed AuditMetadata, PhysicalFileStatus `DELETED`→`SOFT_DELETED` 분리, requestMatcher HTTP method 분기, 9b read 가드, ADR-0005 accepted-with-implementation-gate.
2026-06-04 리뷰 2차(instruction.md) 반영: **Blocker — ciHash 보존 금지**(plain SHA-256+중복가입차단 → ref0 시 `"PURGED:"+UUID` overwrite), upload sourceFileName→hash+ext, export fail-close temp file 누수 방지, RetentionPolicy 선택 규칙 6개+`POLICY_NOT_FOUND`, `hiringEndedAt` 수동 anchor 명령, RetentionHold manual-only, holds matcher method 분기, 학력·경력 정확 날짜 보존 금지(일반화), storage-health-scan 상태별 정책, PurgeBatch/Item = delete 금지 ledger(append-only 아님).
2026-06-04 리뷰 3차(instruction.md) 반영: PhysicalFileStatus 개명 3단계 안전 마이그레이션, `applicantRefHash`=HMAC(pepper,"APPLICANT:"+applicantId) 입력 확정, 학력·경력 날짜 안 A 확정(전부 null·일반화 컬럼 없음), RetentionPolicy fail-safe(`POLICY_CONFLICT`+policyConflictCount), `Interview.memo` 운영 가이드.

Status:

| Slice | Status | Scope |
| --- | --- | --- |
| 09 design | Completed | 두 기둥(영속 감사 / 파기·보존) 설계, ADR 0005/0006/0007, glossary, 슬라이스 분할 |
| 09a - ActivityLog Foundation | Completed | `ActivityLog` schema/enums/repo, `ActivityLogService`(recordInCurrentTx/recordRequiresNew), `CorrelationIdFilter`, `AuditHmac`+`AuditConfig`(applicantRefHash), `AuditEvent`/`AuditMetadata`. 19 tests. → `docs/codex/implementation/phase-09a-activity-log-foundation.md` |
| 09b - 로그 흡수 + 관리자 변경 audit + read API | **Completed (2026-06-05, 리뷰 1차 반영)** | Export/Pdf/Upload adapter(dual-write, DB=source of truth), egress fail-close+temp 정리, sealed typed AuditMetadata 7종, reopen·StageResult 정정/발표/확정·첨부 admin 계측(in-tx, announce/close 는 검증된 actor 명시 전달), `GET /api/admin/audit/activities`(가드 — range 는 Duration 비교 + 권한별 마스킹), narrow matcher. 리뷰 반영: canonical status audit, toJson ObjectMapper 화, Stage 픽스처 동적 접수기간(기존 날짜 의존 실패 8건 해소). scoped 111/111 passed. → `docs/codex/implementation/phase-09b-audit-instrumentation-read-api.md` |
| 09c - Retention 모델 + eligibility scan + dry-run | **Completed (2026-06-05, 리뷰 1차 반영)** | RetentionPolicy(전역+override, overlap 금지, 선택 규칙+POLICY_CONFLICT fail-safe)/RetentionHold(manual only, **조회도 PRIVACY 전용** — reason 노출 차단)/`JobPosting.hiringEndedAt` 수동 anchor, eligibility(판정 순서 고정+terminal query 계약), dry-run PurgeBatch/Item(delete 금지 ledger, 무변경, 목록 page/size≤100)+PURGE_SCAN coarse 감사, method 분기 matcher 9종, write 서비스 requireActor(ANONYMOUS 감사 차단). 리뷰 반영 후 39/39 passed(+초기 34+회귀 91). 수동 DDL: `docs/codex/ops/phase-09c-retention-ddl.sql`. → `docs/codex/implementation/phase-09c-retention-model-dry-run.md` |
| 09d-1 - Purge execute core | **Completed (2026-06-05, 리뷰 1차 반영)** | execute API(PRIVACY_ADMIN, confirm 필수, bulk=**COMPLETED** dry-run ELIGIBLE/단건 XOR), 실행 시 재검증(drift=SKIPPED), item REQUIRES_NEW all-or-nothing/batch 비원자(PARTIAL_FAILED, complete 실패 시 RUNNING 방치 방지), 인벤토리 §3~**§7-1** tombstone 1:1(전용 bulk repo, HASH_ONLY/안 A, **StageResult comment+정정 이력 포함** — 리뷰 Major 1), ref-count 익명화(ciHash sentinel), **PURGED 승격 가드(STORED+soft-DELETED outstanding)**, PURGE_EXECUTE coarse 감사, FAILED item=PURGE_ITEM_FAILED. 적대 검증 5-agent(REAL 1건 반영). 리뷰 반영 후 42 passed. 수동 DDL: `phase-09d-1-purge-execute-ddl.sql`. → `docs/codex/implementation/phase-09d-1-purge-execute-core.md` |
| 09d-2 - Attachment binary delete saga | **Completed (2026-06-05, 리뷰 1차 반영)** | PhysicalFileStatus 재정의+1단계 마이그레이션(DELETED·SOFT_DELETED 병존, markDeleted→SOFT_DELETED, 전 사용처 호환), saga ①(첨부 PII §6+PENDING 마킹, item tx)②(멱등 삭제+존재 재확인, **빈 storagePath=실패** — 리뷰 Major 1)③(REQUIRES_NEW — BINARY_DELETED/storagePath null, **전부 소멸 시에만 최종 PURGED 승격** fail-loud), 실패=FAILED 유지+binary_delete_failed_count+PARTIAL_FAILED. health scan 은 BINARY_* 파일 **orphan 오탐 방지**(known but deferred — 리뷰 Medium 1). 적대 검증 3-agent(§6 confirmed, REAL 1건 반영) + 실파일 실삭제 실증 테스트(리뷰 Medium 2). scoped 48+리뷰 22 passed. 수동 DDL: `phase-09d-2-attachment-saga-ddl.sql`(2단계 UPDATE 별도 시점). → `docs/codex/implementation/phase-09d-2-attachment-binary-delete-saga.md` |
| 09e - Reconciliation + 안정화 | **Completed (2026-06-08, 리뷰 1차 반영)** | reconciliation sweep(`POST /retention/purge-batches/reconcile?limit=100`, PRIVACY_ADMIN — PURGE_PENDING 잔여 건 재처리→최종 PURGED 승격, 유일 재처리 경로, **chunk 처리**+null batchId 격리+**STARTED/SUMMARY 2-phase 감사**), storage-health-scan §6.1(`PURGED_PHYSICAL_FILE_PRESENT` 치명탐지 + BINARY_DELETED null 정상 + PENDING/FAILED retry 가시화, fileKeyHash 만), Low 2 row 수준 `binaryDeleteFailureCode`(엔티티 경계 sanitize 강제), 회귀(reconcile 권한/멱등/actor/Clock/chunk). 적대 검증 3-agent(2영역 confirmed, REAL 1건 반영) + 리뷰 Medium 1·2/Low 1 반영. scoped 42+28 passed. 수동 DDL: `phase-09e-reconciliation-ddl.sql`. **Phase 09 종료.** → `docs/codex/implementation/phase-09e-reconciliation.md` |

목적:

- 영속 `ActivityLog` 감사 기반(SLF4J 이관) + 보존기간 경과 지원자 개인정보 파기(tombstone 익명화 + 첨부 바이너리 물리삭제).

핵심 설계 결정:

- 파기 방식 = tombstone 익명화 + 첨부 바이너리 물리삭제(ADR-0005). crypto-shred·전면 hard delete 기각. `PURGED` = 관계형 PII 제거 + 바이너리 소멸 확인까지(saga + reconciliation), "DB PURGED인데 파일 잔존" 불허.
- 감사 트랜잭션 = 커밋변경 in-tx / 실패·거부·충돌·스킵 REQUIRES_NEW / 정보반출 fail-close(ADR-0006). ActivityLog append-only, 지원자 원문 PII 미저장.
- retentionAnchorAt = `JobPosting.hiringEndedAt`(암묵 closedAt fallback 금지). eligibility = anchor 종료+retentionPeriod 경과+not purged+not hold+terminal. RetentionHold 자동제외=onboarded/입사확정만.
- 인가 = ROLE_PRIVACY_ADMIN 분리(ADR-0007), narrow requestMatcher 우선. manual dry-run/execute(스케줄 auto-execute disabled-by-default).

Out of scope:

- AOP blanket 접근/페이지 감사(VIEW_PAGE/ACCESS_API), ActivityLog 자체 lifecycle policy, forced purge(정보주체 삭제요청 — enum 슬롯만), 파기 후 통지메일(hook만), 스케줄 auto-execute, per-subject envelope key/crypto-shred, Messaging·통계·엑셀 신규 기능.

### Phase 08 - CommonCode And School Master

설계 확정: `docs/codex/design/phase-08-commoncode-school-master-design.md`
(grill-with-docs, 2026-06-02). ADR 0003/0004.

목적:

- 관리자 코드성 lookup(`CommonCode`)과 학교 자동완성/통계 기반(`School`) master 를 **추가형(비파괴)** 으로 도입한다.

범위(확정):

- `CommonCode`: groupCode+code(불변)+displayName+sortOrder+active. public read + admin CRUD. 프론트 드롭다운 소비(백엔드 검증 미결합).
- `School`: 검색/자동완성(public) + admin CRUD + xlsx upsert. 외부 API 미연동.
- `ApplicationEducation` optional `schoolId`(app-level, FK 없음) 링크.
- 기존 enum 전환 후보 카탈로그(STAY/CANDIDATE).

슬라이스: 08a CommonCode / 08b School(검색·CRUD) / 08c xlsx import + schoolId 링크.

Out of scope:

- 기존 enum → CommonCode 전환(0건, 카탈로그만).
- CommonCode ↔ 백엔드 필드 validation 결합.
- SCHOOL/CERTIFICATE funnel dimension(07c 보류, 별도 후속).
- 외부 학교 API, 강한 FK, free-text 소급 매칭.
- 면접/평가/메시지 신규 기능.

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
