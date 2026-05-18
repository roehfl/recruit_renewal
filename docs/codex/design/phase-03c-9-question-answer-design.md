# Phase 03c-9 Question/Answer Domain Design

## Phase 03c-9-2 Implementation Reflection

- Phase 03c-9-2 implemented `ApplicationAnswer`, `ApplicationAnswerRepository`, applicant answer request/response DTOs, `ApplicationAnswerService`, and `ApplicationAnswerController`.
- Added applicant APIs:
  - `GET /applications/{applicationId}/questions`
  - `POST /applications/{applicationId}/answers`
- `GET /applications/{applicationId}/questions` returns active `JobPostingQuestion` rows only and merges the applicant's current `ApplicationAnswer` when present.
- `POST /applications/{applicationId}/answers` replaces all answers for the application and stores `JobPostingQuestion` snapshot fields on every saved answer.
- DRAFT save allows null/blank answer text and required question blanks; length violations are blocked during DRAFT save.
- `ApplicationSubmitValidator` question/answer integration, admin answer lazy read API, choice option domain, file answer type, and Attachment linkage remain deferred.
- Next implementation recommendation is Phase 03c-9-3: required/blank/maxLength answer validation in `ApplicationSubmitValidator`.

## Phase 03c-9-1 구현 반영

- 설계 추천안 중 `QuestionTemplate` + `JobPostingQuestion` 관리자 질문 구성 API가 Phase 03c-9-1에서 구현되었다.
- 구현된 범위는 전역 질문 템플릿 관리, 공고별 질문 생성/수정/정렬/비활성화, 템플릿 기반 snapshot 생성, 직접 작성 질문 생성이다.
- Phase 03c-9-1 시점에는 `ApplicationAnswer`, 지원자 질문/답변 API, submit validator 질문답변 연동, 관리자 답변 조회 API가 보류되었고, 그중 `ApplicationAnswer`와 지원자 질문/답변 API는 Phase 03c-9-2에서 구현되었다.
- Phase 03c-9-2 has completed. The next implementation recommendation is Phase 03c-9-3: connect active required `JobPostingQuestion` answer blank/maxLength validation to `ApplicationSubmitValidator`.

## Phase 이름

Phase 03c-9: Application Question/Answer Domain Design

## 설계 목적

`JobApplication` 하위 자기소개서/질문답변 도메인을 구현하기 전에 공고별 질문 구성, 질문 템플릿, 지원서별 답변 저장, 최종제출 검증, 관리자 상세 조회 확장 방향을 정리한다. Phase 03c-1부터 Phase 03c-8까지 구현된 상세 섹션 저장/조회 구조와 충돌하지 않게 설계하며, StageResult 구현 전에 지원서 본문 응답 영역의 큰 설계 축을 닫는 것을 목표로 한다.

이번 Phase는 설계 문서 작업만 수행한다. Java 코드, Entity, Repository, Service, Controller, DTO, Test, DB schema, 기존 API, `ApplicationSubmitValidator`, 관리자 상세 섹션 API, SecurityConfig는 변경하지 않는다.

## 현재 구현 상태 요약

- Phase 03a에서 `JobApplication` 루트 생성, 조회, 임시저장, 제출, 철회 흐름이 구현되었다.
- Phase 03b-1에서 관리자 Application 루트 목록/상세 조회 API가 구현되었다.
- Phase 03c-1부터 Phase 03c-6까지 학력, 경력, 자격, 어학, 병역, 수상, 공백기간, 첨부 metadata 지원자 섹션 API가 구현되었다.
- Phase 03c-7에서 `ApplicationSubmitValidator`가 `JobApplicationService.submit()`에 연결되어 상세 섹션 필수 검증을 수행한다.
- Phase 03c-8에서 관리자 상세 섹션 read-only lazy 조회 API가 구현되었다.
- 자기소개서/질문답변 도메인은 전역 템플릿, 공고별 질문 구성, 지원자 답변 저장까지 구현되었고, submit validator 질문답변 연동과 관리자 답변 조회는 아직 구현하지 않았다. StageResult, 파일 업로드/다운로드, 관리자 상세 aggregate API도 아직 구현하지 않았다.

## 전체 구조 비교

| 후보 | 구조 | 장점 | 단점 | 판단 |
|---|---|---|---|---|
| A | `QuestionTemplate` + `JobPostingQuestion` + `ApplicationAnswer` | 전역 질문 재사용 가능, 공고별 snapshot 가능, 직접 작성 질문도 허용, submit validator 기준이 명확함 | Entity가 3개로 늘어 초기 구현량이 다소 증가 | 추천 |
| B | `QuestionSet` + `QuestionSetItem` + `ApplicationAnswer` | 묶음 단위 배포/복제에 유리, 질문 세트 재사용이 명확함 | 현재는 세트 versioning 요구가 없고 공고별 질문만으로도 충분함 | 보류 |
| C | `JobPostingQuestion` + `ApplicationAnswer` | 가장 단순하고 빠르게 구현 가능 | 전역 질문 재사용성이 약하고 템플릿 관리 화면 확장이 어렵다 | 최소안으로만 유효 |

### 추천안

초기 구현은 Option A를 사용한다.

- `QuestionTemplate`은 전역 질문 은행으로 둔다.
- `JobPostingQuestion`은 특정 공고에 실제로 배치된 질문 record로 둔다.
- `JobPostingQuestion.questionTemplate`은 nullable 참조로 둔다.
- `ApplicationAnswer`는 지원서별 답변 record로 둔다.
- `QuestionSet`은 별도 묶음, 버전, 일괄 복제 요구가 명확해지기 전까지 보류한다.

이 구조는 기존 상세 섹션의 application-specific record 정책과 맞고, 질문 문구가 제출 이후 변경되는 문제를 `JobPostingQuestion`과 `ApplicationAnswer` snapshot으로 방어할 수 있다.

## 자기소개서와 질문답변 관계 정의

자기소개서는 별도 Entity나 별도 table로 분리하지 않고 질문답변 구조의 category로 본다.

추천 정책:

- `QuestionCategory.SELF_INTRODUCTION`을 둔다.
- "지원동기", "성장과정", "입사 후 포부" 같은 자기소개서 항목은 모두 공고별 질문으로 일반화한다.
- 공고별 자유 질문과 자기소개서 항목은 동일한 `JobPostingQuestion` + `ApplicationAnswer` 구조로 처리한다.
- 자기소개서 전용 화면이 필요하더라도 API/DB 구조는 category 필터로 해결한다.

이유:

- 자기소개서 항목도 본질적으로 공고별 질문과 지원서별 답변이다.
- 항목별 필수 여부, 글자 수 제한, 정렬 순서 정책을 일반 질문과 동일하게 적용할 수 있다.
- submit validator와 관리자 상세 조회 API를 하나의 흐름으로 확장할 수 있다.

## Entity 후보

### QuestionTemplate

| 항목 | 설계 |
|---|---|
| 역할 | 전역 질문 은행. 관리자 또는 인사담당자가 자주 쓰는 질문을 재사용하기 위한 원천 |
| 테이블 후보 | `question_template` |
| 주요 필드 | `id`, `title`, `questionText`, `helperText`, `category`, `answerType`, `defaultRequired`, `defaultMaxLength`, `active`, `createdAt`, `updatedAt` |
| 관계 | `JobPostingQuestion`에서 nullable로 참조 가능 |
| 수정 정책 | 템플릿 수정은 이후 새로 배치되는 공고 질문에만 영향. 이미 배치된 `JobPostingQuestion`은 변경하지 않음 |
| 구현 메모 | 템플릿은 원천이며 제출 검증 기준은 아니다. 제출 검증 기준은 공고별 `JobPostingQuestion`이다 |

### JobPostingQuestion

| 항목 | 설계 |
|---|---|
| 역할 | 특정 `JobPosting`에 실제로 포함된 질문. 템플릿에서 복사하거나 직접 작성 가능 |
| 테이블 후보 | `job_posting_question` |
| 주요 필드 | `id`, `jobPosting`, `questionTemplate`, `questionText`, `helperText`, `category`, `answerType`, `required`, `minLength`, `maxLength`, `sortOrder`, `active`, `createdAt`, `updatedAt` |
| 관계 | N:1 `JobPosting`, N:1 nullable `QuestionTemplate` |
| 정렬 | `sortOrder ASC, id ASC` |
| snapshot 정책 | `questionText`, `helperText`, `category`, `answerType`, `required`, `maxLength`를 자체 snapshot으로 보유 |
| 구현 메모 | `JobPosting`에는 질문 컬렉션을 추가하지 않고 repository 조회로 처리하는 방향을 우선한다 |

템플릿 문구가 나중에 바뀌어도 이미 공고에 배치된 질문은 영향받지 않아야 한다. 따라서 `JobPostingQuestion`은 공고 시점의 질문 정책을 자체 필드로 가진다.

### ApplicationAnswer

| 항목 | 설계 |
|---|---|
| 역할 | `JobApplication`별 질문 답변. `JobApplication`과 `JobPostingQuestion`을 연결 |
| 테이블 후보 | `application_answer` |
| 주요 필드 | `id`, `jobApplication`, `jobPostingQuestion`, `answerText`, `questionTextSnapshot`, `categorySnapshot`, `answerTypeSnapshot`, `requiredSnapshot`, `maxLengthSnapshot`, `sortOrderSnapshot`, `createdAt`, `updatedAt` |
| 관계 | N:1 `JobApplication`, N:1 `JobPostingQuestion` |
| unique 후보 | `job_application_id + job_posting_question_id` |
| 상태 정책 | DRAFT 상태에서 저장 가능, SUBMITTED/WITHDRAWN은 조회만 가능 |
| 구현 메모 | 답변 저장 또는 제출 시점에 질문 snapshot을 보존한다 |

`ApplicationAnswer`도 question snapshot을 가진다. 중복 저장은 있지만 다음 이유로 추천한다.

- 제출 이후 공고 질문이 비정상적으로 수정되어도 제출 당시 답변 기준을 보존할 수 있다.
- 관리자 상세, PDF, 감사 로그에서 당시 질문 문구와 답변을 함께 복원하기 쉽다.
- `JobPostingQuestion` soft disable 또는 후속 revision 도입 후에도 과거 지원서 표시가 안정적이다.

최소 snapshot 후보는 `questionTextSnapshot`, `answerTypeSnapshot`, `requiredSnapshot`, `maxLengthSnapshot`이다. 정렬 재현을 위해 `sortOrderSnapshot`도 함께 둔다.

## Enum 후보

| Enum | 초기 값 후보 | 판단 |
|---|---|---|
| `QuestionCategory` | `SELF_INTRODUCTION`, `GENERAL`, `JOB_SPECIFIC`, `ETC` | 자기소개서와 일반 질문을 같은 구조로 다루기 위한 분류 |
| `QuestionAnswerType` | `SHORT_TEXT`, `LONG_TEXT` | 초기 구현 범위 |
| `QuestionAnswerType` 후속 후보 | `SINGLE_CHOICE`, `MULTI_CHOICE`, `DATE`, `NUMBER`, `FILE` | 후속 Phase로 보류 |

초기 구현은 `SHORT_TEXT`, `LONG_TEXT` 중심으로 시작한다. 선택형 질문은 option 도메인이 추가로 필요하고, `FILE`은 이미 Attachment metadata 도메인이 있으므로 질문답변의 answer type으로 바로 붙이지 않는다. 파일 답변 요구가 생기면 `Attachment.sectionType`/`sectionRecordId` 또는 별도 attachment policy와 연결하는 방향을 다시 설계한다.

## JobPosting 질문 구성 정책

| 정책 | 추천 |
|---|---|
| 수정 가능 상태 | `JobPosting.status=DRAFT`일 때만 질문 구성 수정 허용 |
| PUBLISHED 이후 수정 | 원칙적으로 금지 |
| PUBLISHED 이후 긴급 변경 | 별도 revision/reopen 정책으로 분리 |
| 삭제 정책 | HTTP DELETE 대신 POST command 후보. 실제 삭제보다 `active=false` soft disable 우선 |
| 정렬 정책 | 공고 내 active 질문의 `sortOrder` 중복 불가 후보 |
| required 정책 | `required=true` 질문은 submit 시 답변 필수 |
| maxLength 기본값 | `SHORT_TEXT` 기본 500자, `LONG_TEXT` 기본 5000자 후보 |
| minLength | 초기에는 후보 필드로만 두고 필수 검증은 후속 정책 확정 후 강화 |

질문 구성은 채용공고 내용과 마찬가지로 지원자가 작성 중인 화면과 제출 검증에 직접 영향을 준다. 따라서 공고가 게시된 뒤 질문을 바꾸는 것은 기본적으로 금지한다. 운영상 예외가 필요하면 공고 revision 또는 질문 revision을 별도 Phase에서 설계한다.

## 지원자 답변 저장 정책

| 정책 | 추천 |
|---|---|
| 질문 목록 조회 | 지원자는 자기 `JobApplication`의 공고 질문 목록과 내 답변을 함께 조회할 수 있어야 한다 |
| 저장 가능 상태 | `DRAFT`에서만 답변 저장 가능 |
| 제출/철회 상태 | `SUBMITTED`, `WITHDRAWN`은 답변 수정 불가, 조회만 가능 |
| 질문 없음 | 빈 목록 허용 |
| required=false | 빈 답변 허용 |
| required=true | DRAFT 저장에서는 빈 답변 허용, submit 시 실패 |
| maxLength | DRAFT 저장 시 차단하고 submit 시 재검증 |
| 기본 길이 | `SHORT_TEXT` 500자, `LONG_TEXT` 5000자 후보 |
| 저장 방식 | `POST /applications/{applicationId}/answers` replace 저장 후보 |

답변 저장은 기존 상세 섹션과 같이 DRAFT 상태에서 미완성 상태를 허용한다. 다만 길이 초과는 사용자가 임시저장 후 제출 단계에서 뒤늦게 실패하지 않도록 DRAFT 저장 시점에도 차단한다.

## API 후보

### 관리자 질문 구성 API 후보

| Method | Path | 목적 | 장점 | 판단 |
|---|---|---|---|---|
| GET | `/admin/job-postings/{jobPostingId}/questions` | 공고 질문 목록 조회 | 관리자 공고 설정 화면 구성에 필요 | 추천 |
| POST | `/admin/job-postings/{jobPostingId}/questions` | 공고 질문 추가 | PUT 없이 생성 가능 | 추천 |
| POST | `/admin/job-postings/{jobPostingId}/questions/reorder` | 질문 순서 변경 | Stage reorder와 유사한 command 구조 | 추천 |
| POST | `/admin/job-postings/{jobPostingId}/questions/{questionId}` | 질문 수정 | PUT 금지 정책과 일관 | 추천 |
| POST | `/admin/job-postings/{jobPostingId}/questions/{questionId}/delete` | 질문 비활성화 | HTTP DELETE 없이 soft disable 가능 | 추천 |

### QuestionTemplate API 후보

| Method | Path | 목적 | 장점 | 판단 |
|---|---|---|---|---|
| GET | `/admin/question-templates` | 질문 템플릿 목록 | 전역 질문 은행 검색/선택에 필요 | 추천 |
| GET | `/admin/question-templates/{templateId}` | 질문 템플릿 상세 | 수정 화면에 필요 | 추천 |
| POST | `/admin/question-templates` | 템플릿 생성 | POST 기반 command 정책 유지 | 추천 |
| POST | `/admin/question-templates/{templateId}` | 템플릿 수정 | PUT 없이 수정 가능 | 추천 |
| POST | `/admin/question-templates/{templateId}/deactivate` | 템플릿 비활성화 | 기존 공고 질문 snapshot에 영향 없음 | 추천 |

### 지원자 답변 API 후보

| Method | Path | 목적 | 장점 | 판단 |
|---|---|---|---|---|
| GET | `/applications/{applicationId}/questions` | 공고 질문 + 내 답변 조회 | 지원자 작성 화면에 가장 적합 | primary 추천 |
| GET | `/applications/{applicationId}/answers` | 내 답변 목록만 조회 | 검토/저장 상태 확인용으로 단순 | 보조 후보 |
| POST | `/applications/{applicationId}/answers` | 답변 replace 저장 | 기존 상세 섹션 replace 정책과 일관 | 추천 |

`GET /applications/{applicationId}/questions`를 primary로 추천한다. 지원자 화면은 질문 문구, 도움말, 필수 여부, 글자 수 제한, 현재 답변을 한 번에 필요로 하기 때문이다. `GET /applications/{applicationId}/answers`는 후속으로 필요할 때만 추가해도 된다.

### 관리자 답변 조회 API 후보

| Method | Path | 목적 | 판단 |
|---|---|---|---|
| GET | `/admin/applications/{applicationId}/answers` | 관리자 지원서 상세에서 질문답변 lazy 조회 | Phase 03c-8 흐름에 맞춰 추천 |

관리자 루트 상세 응답에 답변을 직접 포함하지 않고, Phase 03c-8의 lazy section API 패턴을 유지한다.

## Submit Validator 연동 설계

Phase 03c-7의 `ApplicationSubmitValidator`는 다음 구현 Phase에서 질문답변 필수 검증을 추가할 수 있다.

검증 후보:

1. `JobApplication.jobPosting.id` 기준 active 질문 목록을 조회한다.
2. 그중 `required=true` 질문을 추린다.
3. `ApplicationAnswer`를 `jobApplicationId` 기준으로 조회해 `jobPostingQuestionId`별 map으로 만든다.
4. required 질문의 답변이 없거나 `answerText`가 null/blank이면 `InvalidJobApplicationException`을 던진다.
5. 답변이 있더라도 `answerText.length()`가 질문의 `maxLength` 또는 기본 상한을 초과하면 실패한다.
6. active 질문이 없으면 통과한다.
7. `required=false` 질문은 답변이 없어도 통과한다.

Repository 후보:

- `JobPostingQuestionRepository.findByJobPostingIdAndActiveTrueOrderBySortOrderAscIdAsc(Long jobPostingId)`
- `ApplicationAnswerRepository.findByJobApplicationId(Long applicationId)`
- `ApplicationAnswerRepository.findByJobApplicationIdAndJobPostingQuestionIdIn(...)` 후보

테스트 후보:

- 공고 질문이 없으면 submit 통과
- required 질문 답변이 없으면 submit 실패
- required 질문 답변이 blank이면 submit 실패
- optional 질문 답변이 없어도 submit 통과
- answerText가 maxLength를 초과하면 submit 실패
- inactive required 질문은 submit 검증에서 제외
- validator 실패 시 `JobApplication.status=DRAFT`, `submittedAt=null` 유지

이번 Phase에서는 `ApplicationSubmitValidator` 구현을 수정하지 않는다.

## 관리자 상세 답변 조회 확장 방향

Phase 03c-8의 관리자 상세 섹션 lazy 조회 API와 같은 방향으로 질문답변 조회를 추가한다.

후보 API:

- `GET /admin/applications/{applicationId}/answers`

응답 DTO 후보:

| 필드 | 설명 |
|---|---|
| `questionId` | `JobPostingQuestion` id |
| `questionText` | 질문 문구. 과거 답변은 snapshot 우선 사용 가능 |
| `category` | 질문 카테고리 |
| `answerType` | 답변 타입 |
| `required` | 제출 필수 여부 |
| `maxLength` | 글자 수 제한 |
| `sortOrder` | 표시 순서 |
| `answerId` | `ApplicationAnswer` id, 미답변이면 null |
| `answerText` | 답변 원문 |
| `updatedAt` | 답변 최종 수정 시각 후보 |

개인정보/마스킹 정책:

- `answerText`에는 지원자가 민감 개인정보를 직접 입력할 수 있다.
- 관리자 상세 화면에서는 업무상 원문 노출을 허용하되, 관리자 목록 응답에는 포함하지 않는다.
- 원문 열람 권한, 열람 감사 로그, 마스킹/권한별 원문 노출은 보안 Phase에서 별도 설계한다.

## 개인정보, 마스킹, 감사 로그 고려사항

- 질문답변은 자유서술 영역이므로 지원자가 주민번호, 연락처, 가족정보, 건강정보 등을 임의 입력할 가능성이 있다.
- 초기 구현에서는 입력값 자동 탐지/마스킹을 하지 않는다.
- 관리자 목록, 통계, 엑셀 목록에는 `answerText`를 포함하지 않는 것을 기본 정책으로 둔다.
- 관리자 상세 답변 원문 열람은 후속 보안 Phase에서 권한, 감사 로그, 열람 사유 기록과 함께 강화한다.
- PDF 출력이나 엑셀 다운로드에 답변을 포함할지는 별도 출력/다운로드 Phase에서 결정한다.
- 질문 템플릿과 공고 질문에는 secret, 내부 경로, 저장소 위치 같은 민감 운영 정보를 넣지 않는 운영 가이드가 필요하다.

## 구현 Phase 분리안

| Phase | 목적 | 구현 범위 | 보류 |
|---|---|---|---|
| Phase 03c-9-1 | 질문 템플릿과 공고 질문 구성 기반 | `QuestionTemplate`, `JobPostingQuestion`, 관리자 질문 구성 API, 템플릿 API, 테스트 구현 완료 | 지원자 답변 저장, submit 연동 |
| Phase 03c-9-2 | 지원자 질문/답변 작성 API | `ApplicationAnswer`, `GET /applications/{applicationId}/questions`, `POST /applications/{applicationId}/answers`, DRAFT 저장 검증 | 관리자 답변 조회, submit 연동 |
| Phase 03c-9-3 | 질문답변 submit validator 연동 | required/blank/maxLength 검증을 `ApplicationSubmitValidator`에 연결 | 선택형 질문, 파일 답변 |
| Phase 03c-9-4 | 관리자 답변 lazy 조회 API | `GET /admin/applications/{applicationId}/answers`, 관리자 응답 DTO, 민감정보 문서화 | 권한별 원문 열람/감사 로그 |
| Phase 03c-9-5 | 확장 정책 정리 | 선택형 답변, 파일 질문, QuestionSet/revision 필요성 재검토 | 실제 파일 업로드/다운로드 |

Phase 03c-9-2 has completed `ApplicationAnswer` and applicant question/answer APIs. The next implementation recommendation is Phase 03c-9-3: connect active required `JobPostingQuestion` answer blank/maxLength validation to `ApplicationSubmitValidator`. After that, implement the admin answer lazy read API.

## 보류 항목

- `QuestionSet` 도입
- 질문 revision/reopen 정책
- PUBLISHED 이후 질문 변경 허용 정책
- 선택형 답변 option 도메인
- 파일형 답변과 Attachment metadata 연결 방식
- 답변 원문 권한별 마스킹
- 답변 원문 열람 감사 로그
- PDF/엑셀 출력 포함 여부
- StageResult 구현
- CommonCode 전환

## 변경 파일 목록

이번 Phase는 문서 전용 작업이다.

- `docs/codex/design/phase-03c-9-question-answer-design.md`
- `docs/codex/design/phase-03-application-design.md`
- `docs/codex/design/phase-03c-application-detail-design.md`
- `docs/codex/07-implementation-history.md`
- `docs/codex/reports/phase-03c-9-question-answer-design.html`
- `docs/codex/reports/phase-03c-8-admin-application-section-read.html`

## 테스트 및 검증

테스트 실행은 필수 대상이 아니다. 이번 작업은 설계 문서와 HTML report 작성이며 Java 코드, 테스트 코드, 설정 파일, DB schema를 변경하지 않는다.

검증 기준:

- Java 코드, 테스트 코드, Gradle, YAML, static 리소스를 변경하지 않는다.
- 신규 Markdown 설계 문서와 HTML report를 생성한다.
- 기존 설계 문서와 구현 이력에 Phase 03c-9 설계 결정을 연결한다.
- Phase 03c-8 HTML report의 깨진 `$env:AES_SECRET_KEY` 마스킹 표기를 보정한다.
- HTML report는 self-contained이며 외부 CDN, 외부 JS, 외부 CSS를 사용하지 않는다.

## 다음 구현 추천 Phase

Phase 03c-9-2 has completed `ApplicationAnswer`, `GET /applications/{applicationId}/questions`, and `POST /applications/{applicationId}/answers`.

The next implementation recommendation is Phase 03c-9-3: connect active required `JobPostingQuestion` answer blank/maxLength validation to `ApplicationSubmitValidator`. After that, implement the admin answer lazy read API.
