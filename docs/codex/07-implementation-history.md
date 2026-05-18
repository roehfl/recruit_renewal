# 07. Implementation History

## Phase 03c-9-3 - ApplicationSubmitValidator Question/Answer Integration

- 작업일: 2026-05-18
- 목적: 최종제출 시 active `JobPostingQuestion` 기준으로 지원자 답변 누락, blank, 길이 초과를 검증하도록 `ApplicationSubmitValidator`에 질문답변 검증을 연결했다.
- 구현 범위:
  - `ApplicationSubmitValidator`에 `JobPostingQuestionRepository`, `ApplicationAnswerRepository` 의존성 추가
  - active 질문 목록 조회 후 required 질문의 answer row/null/blank 검증
  - optional 질문은 미답변/blank 허용
  - `JobPostingQuestion.maxLength` 및 answerType 상한(`SHORT_TEXT` 500, `LONG_TEXT` 5000) 재검증
  - inactive 질문과 active 질문 외 answer row는 검증 대상에서 제외
  - validator 실패 시 기존 `InvalidJobApplicationException` 400 정책 유지
  - validator 실패 시 submit 상태 전이가 일어나지 않도록 기존 `JobApplicationService.submit()` 흐름 유지
- 주요 수정 클래스:
  - `ApplicationSubmitValidator`
  - `ApplicationSubmitValidatorTest`
  - `JobApplicationServiceTest`
  - `ApplicationControllerTest`
  - `ApplicationAnswerServiceTest`
  - `ApplicationAnswerControllerTest`
- API:
  - 신규 API 없음
  - 기존 `POST /applications/{applicationId}/submit`의 제출 검증만 보강
- 테스트 결과:
  - `ApplicationSubmitValidatorTest` 성공
  - `JobApplicationServiceTest`, `ApplicationControllerTest` 성공
  - `ApplicationAnswerServiceTest`, `ApplicationAnswerControllerTest` 성공
  - `QuestionTemplateServiceTest`, `QuestionTemplateControllerTest` 성공
  - `JobPostingQuestionServiceTest`, `JobPostingQuestionControllerTest` 성공
  - `./gradlew.bat clean test --no-daemon` 성공
- 미구현/보류:
  - 관리자 답변 조회 API `GET /admin/applications/{applicationId}/answers`
  - `GET /applications/{applicationId}/answers`
  - 선택형 답변 option, 파일형 답변, Attachment 연동, QuestionSet
  - `minLength` submit 강제
  - active 질문 외 answer row 정합성 검증
- 다음 작업: Phase 03c-9-4에서 관리자 답변 lazy read API를 구현한다.

## Phase 03c-9-2 - ApplicationAnswer + Applicant Question/Answer API

- 작업일: 2026-05-18
- 목적: 지원자가 본인 지원서의 공고 질문 목록과 현재 답변을 조회하고, DRAFT 상태에서 답변을 replace 저장할 수 있도록 `ApplicationAnswer` 기반 applicant API를 추가했다.
- 구현 범위:
  - `ApplicationAnswer` Entity 추가
  - `ApplicationAnswerRepository` 추가
  - `ApplicationAnswerRequest`, `ApplicationAnswerReplaceRequest`, `ApplicationQuestionResponse` 추가
  - `ApplicationAnswerService`, `ApplicationAnswerController` 추가
  - `InvalidApplicationAnswerException` 추가 및 `GlobalExceptionHandler` 400 매핑
  - `GET /applications/{applicationId}/questions` 구현
  - `POST /applications/{applicationId}/answers` 구현
  - active `JobPostingQuestion` 목록 기준 질문 조회와 현재 답변 merge 구현
  - 답변 replace 저장 시 applicationId 기준 기존 답변 삭제 후 새 답변 저장
  - 답변 저장 시 질문 문구/category/answerType/required/minLength/maxLength/sortOrder snapshot 보존
  - DRAFT 저장 시 null/blank 답변 허용, maxLength/SHORT_TEXT/LONG_TEXT 길이 초과 차단
- 주요 클래스:
  - `ApplicationAnswer`
  - `ApplicationAnswerRepository`
  - `ApplicationAnswerService`
  - `ApplicationAnswerController`
  - `ApplicationQuestionResponse`
  - `ApplicationAnswerServiceTest`
  - `ApplicationAnswerControllerTest`
- API:
  - `GET /applications/{applicationId}/questions`
  - `POST /applications/{applicationId}/answers`
- 테스트 결과:
  - `ApplicationAnswerServiceTest` 성공
  - `ApplicationAnswerControllerTest` 성공
  - `QuestionTemplateServiceTest` 성공
  - `QuestionTemplateControllerTest` 성공
  - `JobPostingQuestionServiceTest` 성공
  - `JobPostingQuestionControllerTest` 성공
  - `ApplicationSubmitValidatorTest` 성공
  - `ApplicationControllerTest` 성공
  - `./gradlew.bat clean test` 성공
- 미구현/보류:
  - `ApplicationSubmitValidator` 질문답변 필수 검증 연동
  - 관리자 답변 조회 API `GET /admin/applications/{applicationId}/answers`
  - 지원자 답변 전용 조회 API `GET /applications/{applicationId}/answers`
  - 선택형 option, 파일형 답변, Attachment 연동, QuestionSet, StageResult
- 다음 작업: Phase 03c-9-3에서 active required `JobPostingQuestion` 기준 answer blank/maxLength 검증을 `ApplicationSubmitValidator`에 연결한다.

## Phase 03c-9-1 - QuestionTemplate + JobPostingQuestion Admin API

- 작업일: 2026-05-18
- 목적: 자기소개서/질문답변 도메인의 첫 구현 단계로 전역 질문 템플릿과 공고별 질문 구성 관리자 API를 추가했다.
- 구현 범위:
  - `QuestionCategory`, `QuestionAnswerType` enum 추가
  - `QuestionTemplate`, `JobPostingQuestion` Entity 추가
  - `QuestionTemplateRepository`, `JobPostingQuestionRepository` 추가
  - `QuestionTemplateService`, `JobPostingQuestionService` 추가
  - `QuestionTemplateController`, `JobPostingQuestionController` 추가
  - 질문 템플릿 생성/조회/수정/비활성화, 공고별 질문 생성/조회/수정/정렬/비활성화 API 구현
  - 템플릿 기반 질문 생성 시 템플릿 값을 복사하고 요청 override를 최종 snapshot에 반영
  - 공고 질문 구성 변경은 `JobPosting.status=DRAFT`에서만 허용
  - 질문 삭제는 HTTP DELETE가 아니라 POST command로 `active=false` soft delete 처리
- 주요 클래스:
  - `QuestionTemplate`
  - `JobPostingQuestion`
  - `QuestionTemplateService`
  - `JobPostingQuestionService`
  - `QuestionTemplateController`
  - `JobPostingQuestionController`
  - `QuestionTemplateServiceTest`
  - `QuestionTemplateControllerTest`
  - `JobPostingQuestionServiceTest`
  - `JobPostingQuestionControllerTest`
- API:
  - `GET /admin/question-templates`
  - `GET /admin/question-templates/{templateId}`
  - `POST /admin/question-templates`
  - `POST /admin/question-templates/{templateId}`
  - `POST /admin/question-templates/{templateId}/deactivate`
  - `GET /admin/job-postings/{jobPostingId}/questions`
  - `POST /admin/job-postings/{jobPostingId}/questions`
  - `POST /admin/job-postings/{jobPostingId}/questions/{questionId}`
  - `POST /admin/job-postings/{jobPostingId}/questions/reorder`
  - `POST /admin/job-postings/{jobPostingId}/questions/{questionId}/delete`
- 테스트 결과:
  - `QuestionTemplateServiceTest` 성공
  - `QuestionTemplateControllerTest` 성공
  - `JobPostingQuestionServiceTest` 성공
  - `JobPostingQuestionControllerTest` 성공
  - `JobPostingServiceTest` 성공
  - `JobPostingControllerTest` 성공
  - `ApplicationSubmitValidatorTest` 성공
  - `AdminApplicationSectionControllerTest` 성공
  - `./gradlew.bat clean test` 성공
- 미구현/보류:
  - `ApplicationAnswer`
  - 지원자 질문 목록/답변 저장 API
  - 질문답변 submit validator 연동
  - 관리자 답변 조회 API
  - `QuestionSet`, 선택형 option, 파일형 답변, Attachment 연동
- 다음 작업: Phase 03c-9-2에서 `ApplicationAnswer`와 지원자 질문 목록/답변 replace 저장 API를 구현한다.

## Phase 03c-9 - Application Question/Answer Domain Design

- 작업일: 2026-05-18
- 목적: `JobApplication` 하위 자기소개서/질문답변 도메인을 구현하기 전에 공고별 질문 구성, 질문 템플릿, 지원서별 답변 저장, 제출 시 필수 답변 검증, 관리자 상세 답변 조회 확장 방향을 설계했다.
- 핵심 설계:
  - 추천 구조는 `QuestionTemplate` + `JobPostingQuestion` + `ApplicationAnswer`로 확정했다.
  - `QuestionTemplate`은 전역 질문 은행으로 두고, `JobPostingQuestion`은 공고별 실제 질문 snapshot record로 둔다.
  - `JobPostingQuestion.questionTemplate`은 nullable로 두어 템플릿 기반 질문과 직접 작성 질문을 모두 지원한다.
  - `ApplicationAnswer`는 지원서별 답변 record이며 `job_application_id + job_posting_question_id` unique 후보를 둔다.
  - 자기소개서는 별도 Entity가 아니라 `QuestionCategory.SELF_INTRODUCTION` 카테고리로 일반 질문답변 구조에 포함한다.
  - 초기 답변 타입은 `SHORT_TEXT`, `LONG_TEXT`로 시작하고 선택형/파일형 답변은 후속 Phase로 보류한다.
  - 공고 질문 구성은 `JobPosting.status=DRAFT`에서만 수정 허용하고, `PUBLISHED` 이후 변경은 revision/reopen 정책 전까지 금지하는 방향을 추천한다.
  - 지원자 답변 저장은 `DRAFT` 상태에서만 허용하며, required 미입력은 DRAFT 저장에서는 허용하고 submit 시 실패시키는 방향으로 설계했다.
  - 관리자 답변 조회는 Phase 03c-8 lazy section API 흐름에 맞춰 `GET /admin/applications/{applicationId}/answers` 후보로 둔다.
- 문서:
  - `docs/codex/design/phase-03c-9-question-answer-design.md`
  - `docs/codex/reports/phase-03c-9-question-answer-design.html`
  - `docs/codex/design/phase-03-application-design.md`
  - `docs/codex/design/phase-03c-application-detail-design.md`
- 테스트 결과: 설계 문서 작업이므로 테스트는 실행하지 않음. Java 코드, 테스트 코드, 설정 파일, DB schema는 변경하지 않음.
- 보정 사항:
  - `docs/codex/design/phase-03c-application-detail-design.md`의 다음 Phase 추천을 Phase 03c-9 설계 완료 이후 구현 흐름으로 갱신했다.
  - `docs/codex/reports/phase-03c-8-admin-application-section-read.html`의 깨진 PowerShell `AES_SECRET_KEY` 마스킹 표기를 `$env:AES_SECRET_KEY='***'; .\gradlew.bat ...` 형태로 보정했다.
- 남은 이슈:
  - `QuestionSet` 도입, 질문 revision/reopen 정책, 선택형 답변 option 도메인, 파일형 답변과 Attachment 연결 방식은 보류했다.
  - 답변 원문 열람 권한과 감사 로그는 보안 Phase에서 별도 설계한다.
- 다음 작업: Phase 03c-9-1에서 `QuestionTemplate` + `JobPostingQuestion` 관리자 질문 구성 API를 구현한다.

## Phase 03c-8 - Admin Application Detail Section Read API

- 작업일: 2026-05-18
- 목적: 관리자 Application 루트 상세 조회는 유지하면서, 학력/경력/자격/어학/병역/수상/공백기간/첨부 metadata를 섹션별 lazy read-only API로 조회할 수 있게 확장했다.
- 핵심 구현:
  - `AdminApplicationSectionController` 추가
  - `AdminApplicationSectionService` 추가
  - 관리자 전용 상세 섹션 응답 DTO 10종 추가
  - 지원자 상세 섹션 Service 재사용 없이 Repository 기반 read-only 조회 구현
  - 목록형 섹션 빈 배열, `military` 저장 전 `data=null`, `careers` profile 없음 시 `NOT_SELECTED + []` 정책 적용
  - 자격번호는 `certificateNumberMasked`, 병역 면제 사유는 `exemptionReasonMasked`만 응답
  - Attachment 응답에서 `storedFileName`, `storagePath`, 다운로드 URL 비노출 유지
  - 관리자 조회는 DRAFT/SUBMITTED/WITHDRAWN 상태와 공고 상태/접수기간에 무관하게 허용
- 주요 클래스:
  - `AdminApplicationSectionController`
  - `AdminApplicationSectionService`
  - `AdminEducationResponse`, `AdminSemesterGradeResponse`
  - `AdminCareerResponse`, `AdminCareerItemResponse`
  - `AdminCertificateResponse`, `AdminLanguageResponse`, `AdminMilitaryResponse`
  - `AdminAwardResponse`, `AdminGapPeriodResponse`, `AdminAttachmentResponse`
  - `AdminApplicationSectionServiceTest`
  - `AdminApplicationSectionControllerTest`
- API:
  - `GET /admin/applications/{applicationId}/educations`
  - `GET /admin/applications/{applicationId}/careers`
  - `GET /admin/applications/{applicationId}/certificates`
  - `GET /admin/applications/{applicationId}/languages`
  - `GET /admin/applications/{applicationId}/military`
  - `GET /admin/applications/{applicationId}/awards`
  - `GET /admin/applications/{applicationId}/gap-periods`
  - `GET /admin/applications/{applicationId}/attachments`
- 테스트 결과:
  - `AdminApplicationSectionServiceTest` 성공
  - `AdminApplicationSectionControllerTest` 성공
  - 기존 관리자 Application 루트 조회 테스트 성공
  - Application submit validator 및 지원자 Application API 회귀 테스트 성공
  - Education/Career/Certificate/Language/Military/Award/GapPeriod/Attachment 상세 섹션 회귀 테스트 성공
  - `./gradlew.bat clean test` 성공
- 남은 이슈:
  - 실제 관리자 권한 세분화는 SecurityConfig 보안 Phase에서 처리한다.
  - 자격번호/면제 사유 원문 열람 권한, 감사 로그, 다운로드 권한은 후속 보안/파일 Phase에서 검토한다.
  - 관리자 상세 aggregate 단일 API는 아직 구현하지 않았다.
- 다음 작업: 자기소개서/질문답변 도메인 또는 StageResult 전 Application 상세 조회 범위를 검토한다.

## Phase 03c-7 - Application Submit Validator

- 작업일: 2026-05-18
- 목적: `JobApplicationService.submit()`에 `ApplicationFormConfig` 기반 상세 섹션 최종제출 검증을 연결했다.
- 핵심 구현:
  - `ApplicationSubmitValidator` 신규 추가
  - `JobApplicationService.submit()`에서 기존 submit 가능 검증 이후, 상태 변경 직전에 validator 호출
  - `ApplicationEducationRepository.existsByJobApplicationId` 추가
  - `ApplicationCareerRepository.existsByJobApplicationId` 추가
  - `useEducation=true`이면 Education 최소 1건 필수 검증
  - `useCareer=true`이면 CareerProfile 필수, `NOT_SELECTED` 실패, `EXPERIENCED` Career row 필수, `NEWCOMER`/`NOT_APPLICABLE` Career row 방어 실패 검증
  - `useMilitary=true`이면 Military record 필수, `COMPLETED` 복무기간 필수, `EXEMPTED` 면제 사유 필수 검증
  - Certificate, Language, Award, GapPeriod, Attachment는 이번 Phase에서 선택 섹션으로 유지
  - 기존 상세 섹션 저장 API path/method, Entity 구조, replace 저장 정책은 변경하지 않음
- 주요 클래스:
  - `ApplicationSubmitValidator`
  - `ApplicationSubmitValidatorTest`
  - `JobApplicationService`
  - `ApplicationEducationRepository`
  - `ApplicationCareerRepository`
- API:
  - 신규 API 없음
  - `POST /applications/{applicationId}/submit` 내부 검증 강화
- 테스트 결과:
  - `ApplicationSubmitValidatorTest` 성공
  - `JobApplicationServiceTest` 성공
  - `ApplicationControllerTest` 성공
  - Education/Career/Certificate/Language/Military/Award/GapPeriod/Attachment 상세 섹션 회귀 테스트 성공
  - `./gradlew.bat clean test` 성공
- 남은 이슈:
  - Attachment 제출 필수 정책은 `ApplicationFormConfig` 확장 또는 별도 policy 도입 후 검토한다.
  - Certificate/Language/Award/GapPeriod의 세부 required flag가 생기면 submit validator에 연결한다.
  - 관리자 상세 섹션 API, StageResult, 자기소개서/질문답변은 후속 Phase로 유지한다.
- 다음 작업: 관리자 상세 섹션 조회 API 또는 자기소개서/질문답변 도메인 범위를 검토한다.

## Phase 03c-6 - Application Attachment Metadata

- 작업일: 2026-05-15
- 목적: `JobApplication` 하위 첨부파일 metadata를 지원자가 조회/replace 저장할 수 있게 구현했다.
- 핵심 구현:
  - `AttachmentType`, `ApplicationSectionType` enum 추가
  - `ApplicationAttachment` Entity 추가
  - `ApplicationAttachmentRepository` 추가
  - `ApplicationAttachmentService`에서 본인 지원서, DRAFT 상태, PUBLISHED 공고, 접수기간 검증을 재사용해 첨부 metadata replace 저장 구현
  - Attachment는 현재 `ApplicationFormConfig` flag 없이 저장 가능하도록 처리
  - `sectionType=APPLICATION`이면 `sectionRecordId` 금지, 그 외 sectionType은 null 허용 및 값이 있으면 1 이상 검증
  - `storedFileName`, `storagePath`는 저장하되 지원자 응답에서 제외
  - `ApplicationAttachmentController`로 지원자 첨부 metadata 조회/저장 API 추가
- 주요 클래스:
  - `AttachmentType`
  - `ApplicationSectionType`
  - `ApplicationAttachment`
  - `ApplicationAttachmentRepository`
  - `AttachmentReplaceRequest`, `AttachmentRequest`
  - `AttachmentResponse`
  - `ApplicationAttachmentService`
  - `ApplicationAttachmentController`
  - `ApplicationAttachmentServiceTest`
  - `ApplicationAttachmentControllerTest`
- API:
  - `GET /applications/{applicationId}/attachments`
  - `POST /applications/{applicationId}/attachments`
- 테스트 결과:
  - `ApplicationAttachmentServiceTest` 성공
  - `ApplicationAttachmentControllerTest` 성공
  - Education/Career/Certificate/Language/Military/Award/GapPeriod 상세 섹션 회귀 테스트 성공
  - `./gradlew.bat clean test` 성공
- 남은 이슈:
  - 실제 multipart 업로드/다운로드/저장소 연동은 구현하지 않았다.
  - `sectionRecordId` 실제 상세 섹션 row 존재성 검증은 후속 정책 확정 후 보완한다.
  - Attachment submit 필수 정책은 Phase 03c-7에서 검토한다.
- 다음 작업: Phase 03c-7 `ApplicationSubmitValidator` 통합을 검토한다.

## Phase 03c-5 - Application Award + GapPeriod

- 작업일: 2026-05-15
- 목적: `JobApplication` 하위 수상/포상사항과 공백기간을 지원자가 조회/replace 저장할 수 있게 구현했다.
- 핵심 구현:
  - `GapType` enum 추가
  - `ApplicationAward`, `ApplicationGapPeriod` Entity 추가
  - `ApplicationAwardRepository`, `ApplicationGapPeriodRepository` 추가
  - `ApplicationSectionAccessService`에 `validateAwardEnabled`, `validateGapPeriodEnabled` 추가
  - `ApplicationAwardService`, `ApplicationGapPeriodService`에서 본인 지원서, DRAFT 상태, PUBLISHED 공고, 접수기간, `useAward`/`useGapPeriod` 검증 구현
  - Award/GapPeriod row는 applicationId 기준 명시 삭제 후 새 row 저장
  - GapPeriod `startDate <= endDate`, description 2000자 제한, sortOrder 중복 검증 구현
  - `ApplicationAwardController`, `ApplicationGapPeriodController`로 지원자 수상/공백기간 조회/저장 API 추가
- 주요 클래스:
  - `GapType`
  - `ApplicationAward`
  - `ApplicationGapPeriod`
  - `ApplicationAwardService`
  - `ApplicationGapPeriodService`
  - `ApplicationAwardController`
  - `ApplicationGapPeriodController`
  - `AwardReplaceRequest`, `AwardRequest`, `GapPeriodReplaceRequest`, `GapPeriodRequest`
  - `AwardResponse`, `GapPeriodResponse`
  - `ApplicationAwardServiceTest`, `ApplicationAwardControllerTest`
  - `ApplicationGapPeriodServiceTest`, `ApplicationGapPeriodControllerTest`
- API:
  - `GET /applications/{applicationId}/awards`
  - `POST /applications/{applicationId}/awards`
  - `GET /applications/{applicationId}/gap-periods`
  - `POST /applications/{applicationId}/gap-periods`
- 테스트 결과:
  - `ApplicationAwardServiceTest`, `ApplicationAwardControllerTest`, `ApplicationGapPeriodServiceTest`, `ApplicationGapPeriodControllerTest` 성공
  - Education/Career/Certificate/Language/Military 상세 섹션 회귀 테스트 성공
  - `./gradlew.bat clean test` 성공
- 남은 이슈:
  - `ApplicationSubmitValidator`는 아직 구현하지 않았다.
  - GapPeriod overlap 검증은 정책 확정 전까지 보류한다.
  - 관리자 상세 섹션 API와 수상/공백기간 마스킹 정책은 후속 Phase에서 확정한다.
- 다음 작업: Phase 03c-6 Attachment metadata vertical slice를 검토한다.

## Phase 03c-4R - Application Section Access Helper

- 작업일: 2026-05-15
- 목적: Education, Career, Certificate, Language, Military 상세 섹션 Service에 반복되던 지원서 접근/쓰기 가능/config enabled 검증을 최소 helper로 추출했다.
- 핵심 구현:
  - `ApplicationSectionAccessService` 추가
  - `findOwnedApplication`, `validateWritable`, 섹션별 `validateXxxEnabled` 메서드 구현
  - `ApplicationEducationService`, `ApplicationCareerService`, `ApplicationCertificateService`, `ApplicationLanguageService`, `ApplicationMilitaryService`에서 중복 검증 제거
  - `SectionType` enum 기반 일반화는 도입하지 않고 명시 메서드로 유지
- 주요 클래스:
  - `ApplicationSectionAccessService`
  - `ApplicationEducationService`
  - `ApplicationCareerService`
  - `ApplicationCertificateService`
  - `ApplicationLanguageService`
  - `ApplicationMilitaryService`
- API:
  - 신규 API 없음
- 테스트 결과:
  - Education/Career/Certificate/Language/Military 상세 섹션 Service/Controller 회귀 테스트 성공
  - `./gradlew.bat clean test` 성공
- 남은 이슈:
  - `ApplicationSubmitValidator`는 아직 구현하지 않았다.
  - 병역 submit 필수 정책은 Phase 03c-7에서 연결한다.
- 다음 작업: Phase 03c-5 Award + GapPeriod vertical slice에서 helper를 재사용한다.

## Phase 03c-4 - Application Military

- 작업일: 2026-05-15
- 목적: `JobApplication` 하위 병역사항 단건 record를 지원자 본인이 조회하고 `DRAFT` 상태에서 저장할 수 있게 구현했다.
- 핵심 구현:
  - `ApplicationMilitary` Entity 추가
  - `MilitarySubjectType`, `MilitaryServiceType`, `MilitaryBranch`, `MilitaryRank` enum 추가
  - `ApplicationMilitaryRepository` 추가
  - `ApplicationMilitaryService`에서 본인 지원서, DRAFT 상태, PUBLISHED 공고, 접수기간, `useMilitary` 검증 구현
  - 병역 record는 `job_application_id` unique 단건 upsert 구조로 구현
  - 병역 유형별 허용 필드 검증 구현
  - `ApplicationMilitaryController`로 지원자 병역 조회/저장 API 추가
- 주요 클래스:
  - `ApplicationMilitary`
  - `MilitarySubjectType`, `MilitaryServiceType`, `MilitaryBranch`, `MilitaryRank`
  - `ApplicationMilitaryRepository`
  - `MilitarySaveRequest`, `MilitaryResponse`
  - `ApplicationMilitaryService`
  - `ApplicationMilitaryController`
  - `ApplicationMilitaryServiceTest`
  - `ApplicationMilitaryControllerTest`
- API:
  - `GET /applications/{applicationId}/military`
  - `POST /applications/{applicationId}/military`
- 테스트 결과:
  - `ApplicationMilitaryServiceTest`, `ApplicationMilitaryControllerTest` 성공
  - Education/Career/Certificate/Language 상세 섹션 회귀 테스트 성공
  - `./gradlew.bat clean test` 성공
- 남은 이슈:
  - submit 시 `useMilitary=true`이면 `ApplicationMilitary` 1건 필수 검증을 Phase 03c-7에서 연결한다.
  - `COMPLETED` 복무기간 필수, `EXEMPTED` 면제 사유 필수 여부는 submit validator에서 확정한다.
  - 면제 사유의 관리자 응답 마스킹/암호화 정책은 관리자 상세 섹션 Phase에서 확정한다.
  - 상세 섹션 공통 접근/수정 가능 검증이 반복되므로 다음 섹션 전 최소 helper 추출을 검토한다.
- 다음 작업: `ApplicationSectionAccessService` 같은 최소 helper 추출 후 Phase 03c-5 Award + GapPeriod vertical slice를 진행한다.

## Phase 03c-3 - Application Certificate + Language

- 작업일: 2026-05-15
- 목적: `JobApplication` 하위 상세 섹션 중 자격사항과 어학사항을 지원자가 조회/replace 저장할 수 있게 구현했다.
- 핵심 구현:
  - `ApplicationCertificate`, `ApplicationLanguage` Entity 추가
  - `ApplicationCertificateRepository`, `ApplicationLanguageRepository` 추가
  - `ApplicationCertificateService`, `ApplicationLanguageService`에서 본인 지원서, DRAFT 상태, PUBLISHED 공고, 접수기간, `useCertificate`/`useLanguage` 검증 구현
  - Certificate/Language row는 applicationId 기준 명시 삭제 후 새 row 저장
  - Certificate 취득일/만료일, Language 응시일/만료일 교차 검증 구현
  - `ApplicationCertificateController`, `ApplicationLanguageController`로 지원자 자격/어학 조회/저장 API 추가
- 주요 클래스:
  - `ApplicationCertificate`
  - `ApplicationLanguage`
  - `ApplicationCertificateService`
  - `ApplicationLanguageService`
  - `ApplicationCertificateController`
  - `ApplicationLanguageController`
  - `CertificateReplaceRequest`, `CertificateRequest`, `LanguageReplaceRequest`, `LanguageRequest`
  - `CertificateResponse`, `LanguageResponse`
  - `ApplicationCertificateServiceTest`, `ApplicationCertificateControllerTest`
  - `ApplicationLanguageServiceTest`, `ApplicationLanguageControllerTest`
- API:
  - `GET /applications/{applicationId}/certificates`
  - `POST /applications/{applicationId}/certificates`
  - `GET /applications/{applicationId}/languages`
  - `POST /applications/{applicationId}/languages`
- 테스트 결과:
  - `ApplicationCertificateServiceTest`, `ApplicationCertificateControllerTest`, `ApplicationLanguageServiceTest`, `ApplicationLanguageControllerTest` 성공
  - `ApplicationEducationServiceTest`, `ApplicationEducationControllerTest`, `ApplicationCareerServiceTest`, `ApplicationCareerControllerTest` 성공
  - 전체 `clean test` 성공
- 남은 이슈:
  - submit 시 Certificate/Language 최소 row 필수 여부는 Phase 03c-7에서 결정한다.
  - Language의 score/grade 필수 여부는 DRAFT 저장에서는 강제하지 않았고 submit validator에서 재검토한다.
  - 관리자 상세 응답에 Certificate/Language 섹션은 아직 포함하지 않았다.
  - 자격번호 관리자 마스킹/암호화 정책은 관리자 상세 섹션 확장 시 결정한다.
  - Education/Career/Certificate/Language의 접근/상태/접수기간/config enabled 검증이 반복되므로 Military 구현 후 최소 공통 helper 추출을 검토한다.
  - Certificate/Language 자유 입력 문자열 길이 제한은 운영 DB schema 기준 확정 후 보완한다.
- 다음 작업:
  - Military vertical slice를 구현하거나, 상세 섹션 공통 접근/수정 정책 helper를 최소 범위로 추출할지 검토한다.

## Phase 03c-2 - Application Career

- 작업일: 2026-05-15
- 목적: `JobApplication` 하위 상세 섹션 중 경력사항을 지원자가 조회/replace 저장할 수 있게 구현했다.
- 핵심 구현:
  - `ApplicationCareerProfile`, `ApplicationCareer` Entity 추가
  - `CareerType`, `EmploymentType` enum 추가
  - `ApplicationCareerProfileRepository`, `ApplicationCareerRepository` 추가
  - `ApplicationCareerService`에서 본인 지원서, DRAFT 상태, PUBLISHED 공고, 접수기간, `useCareer` 검증 구현
  - Career profile은 upsert하고, Career row는 applicationId 기준 명시 삭제 후 새 row 저장
  - 리뷰 보완으로 `currentlyEmployed=true`이면 `endDate`를 금지하고, Service 직접 호출에서도 담당업무/퇴사사유 2000자 제한을 검증
  - `ApplicationCareerController`로 지원자 경력 조회/저장 API 추가
- 주요 클래스:
  - `ApplicationCareerProfile`
  - `ApplicationCareer`
  - `ApplicationCareerService`
  - `ApplicationCareerController`
  - `CareerReplaceRequest`, `CareerRequest`
  - `CareerResponse`, `CareerItemResponse`
  - `ApplicationCareerServiceTest`, `ApplicationCareerControllerTest`
- API:
  - `GET /applications/{applicationId}/careers`
  - `POST /applications/{applicationId}/careers`
- 테스트 결과:
  - `ApplicationCareerServiceTest` 성공
  - `ApplicationCareerControllerTest` 성공
  - `ApplicationEducationServiceTest` 성공
  - `ApplicationEducationControllerTest` 성공
  - 전체 `clean test` 성공
- 남은 이슈:
  - submit 시 `CareerType.NOT_SELECTED` 실패 여부와 `EXPERIENCED` 최소 1개 필수 검증은 Phase 03c-7에서 연결한다.
  - 관리자 상세 응답에 Career 섹션은 아직 포함하지 않았다.
  - 다음 상세 섹션에서 검증 반복이 커지면 최소 공통 helper 추출을 검토한다.
- 다음 작업:
  - Certificate + Language 또는 Military vertical slice를 구현한다.

## Phase 03c-1 - Application Education

- 작업일: 2026-05-15
- 목적: `JobApplication` 하위 상세 섹션 중 학력사항과 학기별 성적을 지원자가 조회/replace 저장할 수 있게 구현했다.
- 핵심 구현:
  - `ApplicationEducation`, `ApplicationEducationSemesterGrade` Entity 추가
  - 학력/성적 enum `EducationLevel`, `GraduationStatus`, `DayNightType`, `CampusType` 추가
  - `ApplicationEducationRepository`, `ApplicationEducationSemesterGradeRepository` 추가
  - `ApplicationEducationService`에서 본인 지원서, DRAFT 상태, PUBLISHED 공고, 접수기간, `useEducation` 검증 구현
  - replace 저장 시 기존 SemesterGrade 선삭제 후 Education 삭제, 새 Education/SemesterGrade 저장
  - 입학일/졸업일이 모두 있으면 입학일이 졸업일보다 늦지 않도록 검증
  - `ApplicationEducationController`로 지원자 학력 조회/저장 API 추가
  - invalid enum 요청을 `ApiResponse.fail`로 반환하도록 `HttpMessageNotReadableException` 처리 추가
- 주요 클래스:
  - `ApplicationEducation`
  - `ApplicationEducationSemesterGrade`
  - `ApplicationEducationService`
  - `ApplicationEducationController`
  - `EducationReplaceRequest`, `EducationRequest`, `SemesterGradeRequest`
  - `EducationResponse`, `SemesterGradeResponse`
  - `ApplicationEducationServiceTest`, `ApplicationEducationControllerTest`
- API:
  - `GET /applications/{applicationId}/educations`
  - `POST /applications/{applicationId}/educations`
- 테스트 결과:
  - `ApplicationEducationServiceTest` 성공
  - `ApplicationEducationControllerTest` 성공
  - 전체 `clean test` 성공
- 남은 이슈:
  - submit 시 Education 최소 1개 필수 검증은 Phase 03c-7에서 연결한다.
  - 관리자 상세 섹션 API는 아직 없다.
  - 학교명/전공/성적의 관리자 노출/마스킹 정책은 관리자 상세 확장 시 재검토한다.
  - 성적/학점 `BigDecimal` precision/scale 명시는 운영 DB schema 정책 확정 후 검토한다.
  - 다음 섹션에서 접근/상태/접수기간/config enabled 검증이 반복되면 최소 공통 helper 추출을 검토한다.
- 다음 작업:
  - Career 구현 전 `careerApplicable`, `hasCareer`, `careerType` 중 어떤 정책을 둘지 결정한다.

## Phase 03c-0 - Application Detail Design

- 작업일: 2026-05-15
- 목적: `JobApplication` 루트에 연결될 지원서 상세 섹션 도메인을 실제 구현 전 설계했다.
- 핵심 설계:
  - 기본 개인정보 원천은 `Applicant`/`User` 계층에 두고, 지원서에는 필요한 최소 snapshot만 둔다.
  - 학력, 학기별 성적, 경력, 자격, 어학, 병역, 수상, 공백기간, 첨부파일 metadata를 `JobApplication` 하위 상세 섹션 후보로 정리했다.
  - `ApplicationFormConfig.useXxx` flag는 화면 노출, 저장 허용, submit validation 분기 기준으로 사용한다.
  - 상세 섹션 수정은 `DRAFT` 상태에서만 허용하고, `SUBMITTED`/`WITHDRAWN`은 조회만 허용한다.
  - submit 상세 검증은 후속 Phase에서 `ApplicationSubmitValidator`와 섹션별 validator로 분리한다.
- 문서:
  - `docs/codex/design/phase-03c-application-detail-design.md`
  - `docs/codex/design/phase-03-application-design.md`
- 테스트 결과: 문서 설계 작업이므로 테스트는 실행하지 않음.
- 남은 이슈: 상세 섹션별 required flag 세분화, 신입/경력 구분 정책, 병역 필수 정책, 첨부파일 저장소/권한 정책은 구현 전 확정 필요.
- 리뷰 반영:
  - replace 저장 절차를 명시 삭제 후 신규 저장 방식으로 구체화했다.
  - Education replace 시 기존 SemesterGrade 선삭제 정책을 추가했다.
  - `useMilitary=true`이면 submit 시 `ApplicationMilitary` 1건 필수로 정리했다.
  - 상세 섹션 code 값은 Java enum으로 시작하는 방향을 명시했다.
  - Career 최소 1개 검증은 `careerApplicable` 또는 지원 유형 도입 전까지 보류로 정리했다.
- 다음 작업: Phase 03c-1에서 Education + SemesterGrade vertical slice를 구현하고, 필요한 최소 공통 helper만 함께 도입한다.

## Phase 03b-1 - Admin Application Read

- 작업일: 2026-05-15
- 목적: Phase 03a에서 생성된 `JobApplication` 루트를 관리자 화면에서 목록/상세로 조회할 수 있는 최소 API를 구현했다.
- 핵심 구현:
  - `AdminApplicationController` 추가
  - 관리자 전체/공고별 Application 목록 조회 API 추가
  - 관리자 Application 상세 조회 API 추가
  - 관리자 전용 `AdminApplicationSummaryResponse`, `AdminApplicationDetailResponse` 추가
  - `JobApplicationRepository` 관리자 조회 쿼리와 to-one `@EntityGraph` 추가
  - `JobApplicationService` 관리자 조회, status 파싱, page/size 검증 추가
  - 리뷰 반영으로 `AdminApplicationSearchCondition`을 `dto.condition`으로 이동
  - status 필터를 `trim + uppercase` 기준으로 정규화
  - `JobApplicationServiceTest`, `AdminApplicationControllerTest` 보강
- 주요 클래스:
  - `AdminApplicationController`
  - `AdminApplicationSearchCondition`
  - `AdminApplicationSummaryResponse`
  - `AdminApplicationDetailResponse`
  - `JobApplicationRepository`
  - `JobApplicationService`
  - `AdminApplicationControllerTest`
- API:
  - `GET /admin/applications`
  - `GET /admin/applications/{applicationId}`
  - `GET /admin/job-postings/{jobPostingId}/applications`
- 테스트 결과:
  - `JobApplicationServiceTest` 성공
  - `AdminApplicationControllerTest` 성공
  - 전체 `clean test` 성공
- 남은 이슈:
  - 실제 관리자 권한 검증은 SecurityConfig에 추가하지 않았다. 운영 전 `/admin/applications/**`는 `ROLE_ADMIN` 또는 채용담당자 권한으로 보호해야 한다.
  - 관리자 응답은 Application 루트 정보만 포함하며 상세 섹션/StageResult는 아직 없다.
- 다음 작업:
  - 관리자 목록 추가 필터 또는 Application 상세 섹션 구현 범위를 결정한다.

## Phase 03a-3 - Application API

- 작업일: 2026-05-15
- 목적: Phase 03a-1/03a-2에서 구현한 지원자 Application 생성/조회/수정/제출/철회 Service 흐름을 HTTP API로 연결했다.
- 핵심 구현:
  - `ApplicationController` 추가
  - `CurrentApplicantService` 추가
  - `ApplicantRepository.findByLoginId` 추가
  - `CustomUserDetails` userType 상수 추가 및 `getUsername() == loginId` 테스트 고정
  - 지원자 Application 생성, 상세 조회, DRAFT 수정, 제출, 철회 API 추가
  - 공고별 내 지원서 조회 API 추가
  - `ApplicationControllerTest`로 path, method, `ApiResponse` 포맷, validation/error 응답, 타인 command 차단 고정
- 주요 클래스:
  - `ApplicationController`
  - `CurrentApplicantService`
  - `ApplicantRepository`
  - `CustomUserDetails`
  - `ApplicationControllerTest`
  - `CustomUserDetailsTest`
- API:
  - `POST /applications`
  - `GET /applications/{applicationId}`
  - `POST /applications/{applicationId}`
  - `POST /applications/{applicationId}/submit`
  - `POST /applications/{applicationId}/withdraw`
  - `GET /job-postings/{jobPostingId}/application`
- 테스트 결과:
  - `ApplicationControllerTest` 성공
  - 전체 `clean test` 성공
- 남은 이슈:
  - `CustomUserDetails`에 `applicantId`가 없어 `loginId` 조회 helper를 사용한다.
  - 인증/인가 실패 응답의 `401/403` 정교화는 보안 정책 확정 후 보완한다.
  - 실제 SecurityFilterChain, CSRF, 미로그인/권한 실패 통합 테스트는 별도 보안 Phase에서 보완한다.
  - `GET /applications/me` 목록 API는 별도 Phase로 분리했다.
- 다음 작업:
  - 관리자 Application 목록/상세 조회 또는 Application 상세 섹션 구현 범위를 결정한다.

## Phase 03a-2 - Application Commands

- 작업일: 2026-05-15
- 목적: Phase 03a-1의 `JobApplication` 루트에 임시저장 수정, 최종제출, 철회 command를 추가했다.
- 핵심 구현:
  - `JobApplication.updateDraft`, `submit`, `withdraw` 추가
  - `ApplicationUpdateRequest` 추가
  - `JobApplicationService.updateDraft`, `submit`, `withdraw` 추가
  - `PUBLISHED` 공고와 접수기간 내 조건을 command 공통 검증으로 적용
  - DRAFT 수정, DRAFT -> SUBMITTED, SUBMITTED -> WITHDRAWN 상태 전이 검증
  - Service 테스트에 updateDraft/submit/withdraw 성공 및 실패 케이스 추가
- 주요 클래스:
  - `JobApplication`
  - `ApplicationUpdateRequest`
  - `JobApplicationService`
  - `JobApplicationServiceTest`
- API:
  - 없음. `ApplicationController`는 Phase 03a-3으로 분리했다.
- 테스트 결과:
  - `JobApplicationServiceTest` 성공
  - 전체 `clean test` 성공
- 남은 이슈:
  - Application HTTP API와 MockMvc 계약 테스트는 아직 없다.
  - 상세 섹션 필수값 검증은 후속 Phase에서 구현한다.
  - 동시 unique 충돌 예외 변환은 Controller/API 단계에서 재검토한다.
- 다음 작업:
  - Phase 03a-3에서 ApplicationController/API/Test 구현을 진행한다.

## 2026-05-13 - Phase 01a JobPosting Vertical Slice

- Document: `docs/codex/implementation/phase-01a-job-posting.md`
- Scope:
  - Added JobPosting aggregate (`JobPosting`, `JobPosition`, `ApplicationFormConfig`)
  - Added posting lifecycle enum/status transition service flow
  - Added admin posting CRUD-like APIs (POST-based update policy)
  - Added service-level business validation and tests
- Notes:
  - Focused on Phase 01a only.
  - Did not add Application/Stage/Interview/Message/CommonCode domains.

## 2026-05-13 - Phase 01a Review Fixes

- Updated JobPosting list API to follow PageResponse pattern (`page`, `size`).
- Added `GlobalExceptionHandler` for JobPosting exceptions:
  - `JobPostingNotFoundException` -> 404
  - `InvalidJobPostingException` -> 400
- Updated Phase 01a implementation document to reflect review fixes.

## 2026-05-14 - Phase 01b JobPosting Public Read API

- Document: `docs/codex/implementation/phase-01b-job-posting-public-read.md`
- Scope:
  - Resolved existing conflict markers in Phase 01a JobPosting files while keeping admin `PageResponse` list behavior.
  - Kept admin update as `POST /admin/job-postings/{id}` and did not reintroduce PUT.
  - Added public/applicant JobPosting list/detail read APIs.
  - Added public DTOs separated from admin DTOs.
  - Added `Clock` injection for testable `accepting` calculation.
  - Added public visibility tests for `PUBLISHED`, `DRAFT`, and `CLOSED` postings.
- Notes:
  - Public APIs expose only `PUBLISHED` postings.
  - `PUBLISHED` postings are shown regardless of reception period; `accepting` reports current receivable status.
  - Did not add Application/Stage/Interview/Message/CommonCode domains.

## 2026-05-14 - Phase 01b Review Fixes

- Document: `docs/codex/implementation/phase-01b-job-posting-public-read.md`
- Scope:
  - Removed collection `@EntityGraph` from admin pageable list query.
  - Added admin detail-only repository lookup with `@EntityGraph`.
  - Unified admin `publish`/`close` timestamps on injected `Clock`.
  - Added page/size validation for admin and public JobPosting list queries.
  - Made public detail JobPosition `sortOrder` sorting null-safe.
  - Added tests for Clock-based publish/close timestamps and invalid paging requests.
- Notes:
  - Did not reintroduce PUT.
  - Did not allow status updates through the general admin update API.

## 2026-05-14 - Phase 01a/01b Integration Check

- Documents:
  - `docs/codex/implementation/phase-01a-job-posting.md`
  - `docs/codex/implementation/phase-01b-job-posting-public-read.md`
  - `docs/codex/07-implementation-history.md`
- Scope:
  - Checked the current branch and confirmed local `main` matches `origin/main`.
  - Reconciled Phase 01a documentation with the actual repository/service/controller behavior.
  - Rewrote Phase 01a and Phase 01b class-by-class documentation into the required table format.
  - Confirmed admin list/detail separation: pageable list has no collection fetch, detail lookups use `@EntityGraph`.
  - Confirmed public reads expose only `PUBLISHED` postings and use the same not-found exception for hidden or nonexistent detail records.
- Notes:
  - No code, API, entity, or configuration changes were made for this integration check.
  - Did not add Application/Stage/Interview/Message/CommonCode domains.

## 2026-05-14 - Phase 02 Stage Design

- Document: `docs/codex/design/phase-02-stage-design.md`
- Scope: Designed Phase 02a as JobPosting child Stage management and deferred StageResult until after the Application domain.
- Notes: Documentation-only design work; no Java code or new domain classes were added.

## Phase 02a-1 - Stage Basic CRUD

- 작업일: 2026-05-14
- 목적: `JobPosting` 하위 전형단계(`Stage`)의 관리자 기본 CRUD 기반을 추가했다.
- 핵심 구현:
  - `Stage` Entity와 `StageType`, `StageStatus` enum 추가
  - `StageRepository`, `StageService`, `StageController` 추가
  - Stage 생성/목록/상세/수정 API 추가
  - `stageOrder` 중복과 `finalStage=true` 중복을 Service 검증으로 차단
  - `CLOSED` JobPosting의 Stage 생성/수정 차단
  - `READY` 상태 Stage만 일반 수정 허용
  - `@Valid` 실패 응답을 `ApiResponse.fail()` 형식으로 처리
- 주요 클래스:
  - `Stage`
  - `StageRepository`
  - `StageService`
  - `StageController`
  - `StageCreateRequest`
  - `StageUpdateRequest`
  - `StageListResponse`
  - `StageDetailResponse`
  - `StageNotFoundException`
  - `InvalidStageException`
- API:
  - `GET /admin/job-postings/{jobPostingId}/stages`
  - `GET /admin/job-postings/{jobPostingId}/stages/{stageId}`
  - `POST /admin/job-postings/{jobPostingId}/stages`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}`
- 테스트 결과:
  - `StageServiceTest` 성공
  - `StageControllerTest` 성공
  - 전체 `clean test` 성공
- 남은 이슈:
  - reorder/start/announce/close/delete command는 Phase 02a-2로 분리
  - `StageResult`는 `Application` 도메인 이후로 보류
  - `stageOrder` DB unique 제약과 동시성 제어는 reorder 정책 확정 이후 재검토
  - `Stage.update()` 상태 방어는 현재 Service 책임으로 유지
- 다음 작업:
  - Phase 02a-2에서 Stage reorder와 상태 command, delete 정책 구현

## Phase 02a-2 - Stage Reorder and Commands

- 작업일: 2026-05-14
- 목적: Phase 02a-1 Stage 기본 CRUD 위에 reorder, 상태 전이, 삭제 command를 추가했다.
- 핵심 구현:
  - Stage reorder command 추가
  - `READY -> IN_PROGRESS -> RESULT_ANNOUNCED -> CLOSED` 상태 전이 command 추가
  - READY Stage 물리 삭제 command 추가
  - reorder 요청 DTO와 nested validation 추가
  - `ConstraintViolationException`도 `ApiResponse.fail()` 형식으로 처리
  - Service 테스트에 reorder/status/delete 정책 검증 추가
- 주요 클래스:
  - `Stage`
  - `StageService`
  - `StageController`
  - `StageOrderRequest`
  - `StageReorderRequest`
  - `GlobalExceptionHandler`
  - `StageServiceTest`
  - `StageControllerTest`
- API:
  - `POST /admin/job-postings/{jobPostingId}/stages/reorder`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/start`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/announce`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/close`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/delete`
- 테스트 결과:
  - `StageServiceTest` 성공
  - `StageControllerTest` 성공
  - 전체 `clean test` 성공
- 남은 이슈:
  - DB unique 제약과 동시성 제어는 아직 보류
  - Application/StageResult 도입 후 진행 중 Stage 수정/삭제 정책 재검토 필요
- 다음 작업:
  - Phase 02a-3 Controller/API 테스트 보강 또는 Application 기본 흐름 구현 결정

## Phase 02a-3 - Stage Controller API Test

- 작업일: 2026-05-14
- 목적: Phase 02a Stage 관리자 API의 path, method, 응답 포맷을 Controller 테스트로 고정했다.
- 핵심 구현:
  - `StageControllerTest`에 CRUD API 성공 응답 검증 추가
  - reorder/start/announce/close/delete command API 성공 응답 검증 추가
  - validation 실패, Stage 미존재, 잘못된 상태 command 실패 응답 검증 추가
  - PUT 및 DELETE HTTP method 미지원 정책 검증 추가
  - Phase 02a-3 구현 문서 생성 및 Phase 02 설계/구현 문서 정합성 보완
- 주요 클래스:
  - `StageControllerTest`
- API:
  - `GET /admin/job-postings/{jobPostingId}/stages`
  - `GET /admin/job-postings/{jobPostingId}/stages/{stageId}`
  - `POST /admin/job-postings/{jobPostingId}/stages`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}`
  - `POST /admin/job-postings/{jobPostingId}/stages/reorder`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/start`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/announce`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/close`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/delete`
- 테스트 결과:
  - `StageControllerTest` 성공
  - 전체 `clean test` 성공
- 남은 이슈:
  - Stage 공개 노출 API는 아직 구현하지 않았다.
  - JobPosting publish 조건에 Stage 최소 1개 검증은 아직 추가하지 않았다.
  - StageResult는 Application 도메인 이후로 보류한다.
- 다음 작업:
  - Application 기본 흐름 구현을 우선 검토한다.

## 2026-05-14 - Phase 03 Application Design

- Document: `docs/codex/design/phase-03-application-design.md`
- Scope: Designed the applicant Application basic flow as the foundation for later StageResult implementation.
- Key decisions: Use `JobApplication` as the recommended Java class name while keeping Application as the API/document term; split Phase 03 into applicant basic flow, admin read APIs, detail sections, and later StageResult.
- Review update: Fixed Phase 03a-1 decisions for `applicant_id + job_posting_id` unique, `JobPositionRepository` lookup, and applicant name snapshot source.
- Notes: Documentation-only design work; no Java code or new domain classes were added.

## Phase 03a-1 - Application Basic Create/Read

- 작업일: 2026-05-14
- 목적: 지원자 Application 루트의 기본 생성/조회 기반을 추가했다.
- 핵심 구현:
  - `JobApplication` Entity와 `JobApplicationStatus` enum 추가
  - `JobApplicationRepository`, `JobPositionRepository` 추가
  - `JobApplicationService.create`, `getApplication`, `getMyApplicationByJobPosting` 추가
  - `applicant_id + job_posting_id` DB unique 제약과 Service 중복 검증 추가
  - PUBLISHED/접수기간/모집분야 소속/ApplicationFormConfig 존재 검증 추가
  - 지원자명, 공고명, 모집분야명 snapshot 저장
- 주요 클래스:
  - `JobApplication`
  - `JobApplicationStatus`
  - `JobApplicationRepository`
  - `JobPositionRepository`
  - `ApplicationCreateRequest`
  - `ApplicationDetailResponse`
  - `JobApplicationService`
  - `JobApplicationNotFoundException`
  - `InvalidJobApplicationException`
  - `JobApplicationServiceTest`
- API:
  - 없음. 이번 Phase에서는 Controller를 만들지 않았다.
- 테스트 결과:
  - `JobApplicationServiceTest` 성공
  - 전체 `clean test` 성공
- 남은 이슈:
  - `ApplicationController`, `updateDraft`, `submit`, `withdraw`는 후속 Phase로 분리
  - 현재 로그인 Applicant 식별 방식은 Controller 도입 전 확정 필요
  - 철회 후 재지원 허용 시 unique 제약 재검토 필요
  - 동시 unique 충돌 예외 변환과 Applicant not-found 응답 정책은 Controller/API 단계에서 재검토
- 다음 작업:
  - Phase 03a-2에서 updateDraft/submit/withdraw command 구현 여부 검토
