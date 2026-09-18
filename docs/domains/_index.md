# 도메인 카드 색인

작업할 기능을 아래 역색인에서 찾아 **해당 카드만** 읽는다. 카드 하나에 그 도메인의 API 계약, 백엔드·프론트 파일 지도, 규칙, 변경 레시피가 모두 있다.

## 경로 표기

| 표기 | 실제 경로 (레포 루트 기준) |
|---|---|
| `{BE}` | `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit` |
| `{BT}` | `recruit_back/recruit_backend/src/test/java/com/shinyoung/recruit` |
| `{BR}` | `recruit_back/recruit_backend/src/main/resources` |
| `{FE}` | `recruit_front/src` |

- 백엔드 API에는 모두 `/api` 접두가 붙는다(`{BE}/config/WebMvcConfig.java`). 카드의 API 경로는 `/api`를 뺀 형태다.
- 문서 안의 파일 경로는 **레포 루트 기준**으로 쓴다(레포별 AGENTS.md 포함). 예: 백엔드 ADR은 `recruit_back/recruit_backend/docs/adr/`로 쓴다(백엔드 디렉터리 기준 상대 경로 금지).
- 카드 `## 파일 지도` 절에 적힌 경로가 그 파일의 **소유 카드**다. `*Controller.java`와 `views/**/*.vue`는 정확히 한 카드가 소유해야 한다(`node tools/check-docs.mjs`가 검사).

## 카드 목록

| 카드 | 도메인 | 주 사용자 |
|---|---|---|
| [auth-account](auth-account.md) | 로그인(세션·LDAP)·본인인증·지원자 가입/계정 | 지원자·관리자 |
| [role-menu](role-menu.md) | 부서 권한 매핑·메뉴 | 관리자 |
| [job-posting](job-posting.md) | 공고·직무·근무지·공고 이미지·첨부 요건 | 관리자·지원자 |
| [question](question.md) | 질문 템플릿·공고별 질문 | 관리자 |
| [application-form](application-form.md) | 공고별 지원서 양식 설정(config·layout) | 관리자 |
| [application](application.md) | 지원서 생성·제출·철회·내 지원 현황·완성도·폼 로드 | 지원자 |
| [application-sections](application-sections.md) | 지원서 섹션 9종(기본정보~자기소개) 조회·저장 | 지원자 |
| [attachment](attachment.md) | 첨부파일 저장·다운로드·삭제·저장소 점검 | 지원자·관리자 |
| [admin-application](admin-application.md) | 지원현황 검색·지원서 상세·엑셀·PDF | 관리자 |
| [stage-result](stage-result.md) | 전형·전형결과(업로드·정정·지원자 조회) | 관리자·지원자 |
| [interview](interview.md) | 면접 일정·면접관·면접 평가 | 관리자·면접관·지원자 |
| [master-data](master-data.md) | 공통코드·학교·주소 검색 | 관리자·지원자 |
| [board](board.md) | FAQ·공지사항 | 관리자·지원자 |
| [statistics](statistics.md) | 관리자 대시보드·통계 | 관리자 |
| [privacy-audit](privacy-audit.md) | 개인정보 보존·파기·감사 로그 | 관리자 |
| [client-event-log](client-event-log.md) | 프론트 이벤트·오류 로그 수집 | 시스템·관리자 |

## 역색인

### 라우트 → 카드

| 라우트 name | 경로 | 카드 |
|---|---|---|
| `Login` · `NiceAuthPopup` | `/login` · `/nice-auth` | auth-account |
| `Signup` · `accountRecovery` · `ApplicantProfile` | `/applicant/signup` · `/applicant/accountRecovery` · `/applicant/profile` | auth-account |
| `AdminMenuManage` · `AdminRoleMapping` | `/admin/menus` · `/admin/role-mappings` | role-menu |
| `AdminJobPostingList` · `AdminJobPostingCreate` · `AdminJobPostingDetail` · `AdminJobPostingEdit` | `/admin/job-postings…` | job-posting |
| `ApplicantRecruits` · `ApplicationDetail` | `/applicant/recruits` · `/applicant/:jobPostingId/detail` | job-posting |
| `AdminQuestionTemplates` · `AdminJobPostingQuestionTemplateEdit` | `/admin/question-templates` · `/admin/question-template/:id?/edit` | question |
| `AdminApplicationFormList` · `AdminApplicationFormDetail` | `/admin/application-forms…` | application-form |
| `ApplicationStart` · `application` | `/applicant/:jobPostingId/apply` · `/applicant/:applicationId/form` | application |
| `AdminApplicationStatus` · `AdminApplication` | `/admin/applications` · `/admin/applications/:applicationId` | admin-application |
| `AdminStageResult` | `/admin/stage-results` | stage-result |
| `InterviewSchedulingSetting` | `/admin/interview` | interview |
| `AdminCommonCodeManage` | `/admin/codes` | master-data |
| `AdminFaqManage` · `ApplicantFaq` · `NoticeList` | `/admin/faqs` · `/applicant/faq` · `/applicant/noticeList` | board |
| `AdminHome` | `/admin` | statistics |
| `ApplicantHome` · `ApplicantBenefits` · `ApplicantDutyIntroduction` · `ApplicantRecruitProcedure` · `ApplicantPrivacy` | `/applicant/…` | 카드 없음(정적 화면) |

### API 경로 접두 → 카드 (`/api` 생략)

| 접두 | 카드 |
|---|---|
| `/auth` · `/applicant/account` | auth-account |
| `/menu` · `/admin/role-mappings` | role-menu |
| `/admin/job-postings`(기본·`/images`·`/attachment-requirements`) · `/job-postings`(단, `/job-postings/{id}/application`은 application) | job-posting |
| `/admin/question-templates` · `/admin/job-postings/{id}/questions` | question |
| `/admin/application-forms` · `/admin/job-postings/{id}/application-form-config` · `/admin/job-postings/{id}/application-form-layout` | application-form |
| `/applications`(기본·`/me`·`/submit`·`/withdraw`·`/form-page`·`/dashboard`) · `/job-postings/{id}/application` | application |
| `/applications/{id}/` + `basic-info`·`educations`·`careers`·`certificates`·`languages`·`military`·`awards`·`gap-periods`·`questions`·`answers` | application-sections |
| `/applications/{id}/attachments` · `/admin/applications/{id}/attachments/{attachmentId}/…` · `/admin/attachments/storage-health` | attachment |
| `/admin/applications`(검색·상세·섹션 조회·`/export`·`/pdf`) · `/admin/job-postings/{id}/applications` | admin-application |
| `/admin/job-postings/{id}/stages` · `/admin/stages/{id}/results`(단, `/export`는 admin-application) · `/applications/{id}/stage-results` | stage-result |
| `/admin/job-postings/{id}/interviews`(단, `/export`는 admin-application) · `/admin/interviews` · `/admin/job-postings/{id}/interview-schedules` · `/admin/applications/{id}/interview-evaluations` · `/interviewer` · `/applicant/interviews` · `/applicant/applications/{id}/interviews` | interview |
| `/codes` · `/admin/codes` · `/schools` · `/admin/schools` · `/addresses` | master-data |
| `/faqs` · `/admin/faq-categories` · `/board/notices` | board |
| `/admin/job-postings/{id}/statistics` | statistics |
| `/admin/retention` · `/admin/audit` | privacy-audit |
| `/client-events` · `/admin/client-events` | client-event-log |

### 키워드 → 카드

| 키워드 | 카드 |
|---|---|
| 로그인, 세션, LDAP, 본인인증(NICE), 회원가입, 비밀번호, 휴대폰 변경, 계정 찾기 | auth-account |
| 권한, 역할, 부서 권한 매핑, 메뉴, 사이드바, 헤더 메뉴 | role-menu |
| 공고, 채용공고, 모집분야, 직무(`JobPosition`), 근무지, 공고 이미지, 첨부 요건, 신입/경력, 마감 | job-posting |
| 질문 템플릿, 질문 은행, 공고 질문 | question |
| 지원서 양식, 지원서 설정, 폼 레이아웃, 필수/선택 항목, 수정 가능 기간 | application-form |
| 지원서 작성·제출·철회, 지원분야, 완성도, 지원서 대시보드, 편집 가능, 내 지원 현황 | application |
| 기본정보, 보훈, 학력, 학기별 성적, 경력, 경력기술서, 자격증, 어학, 병역, 수상, 공백기간, 자기소개·답변, 섹션 임시저장 | application-sections |
| 첨부파일, 파일 업로드/다운로드, 저장소 점검 | attachment |
| 지원현황, 지원서 검색, 관리자 지원서 상세, 엑셀 다운로드, 컬럼 선택, PDF | admin-application |
| 전형, 전형결과, 합격/불합격, 결과 업로드, 결과 정정, 지원자 결과 조회 | stage-result |
| 면접, 면접 일정, 스케줄링, 면접관, 면접 평가 | interview |
| 공통코드, 학교, 학교 검색, 학교 가져오기, 주소 검색 | master-data |
| FAQ, 공지사항, 게시판 | board |
| 대시보드, 통계, 퍼널, 일별 추이 | statistics |
| 개인정보 보존, 파기(purge), 보존 정책(retention), 보류(hold), 감사 로그, activity log | privacy-audit |
| 클라이언트 이벤트, 텔레메트리, 프론트 오류 로그 | client-event-log |

## 공통 기반 (카드 없음 — 레포 AGENTS.md 참조)

- 백엔드: `{BE}/dto/response/ApiResponse.java`, `{BE}/exception/GlobalExceptionHandler.java`, `{BE}/domain/entity/BaseEntity.java`, `{BE}/common/`, `{BE}/config/WebMvcConfig.java`, `{BE}/config/CorrelationIdFilter.java`
- 프론트: `{FE}/api/client.ts`, `{FE}/api/apiError.ts`, `{FE}/layouts/`, `{FE}/routes/index.ts`, `{FE}/stores/uiStore.ts`, `{FE}/common/`, `{FE}/styles/`

## 카드 없는 파일

소유 카드가 없는 화면(주로 지원자 정적 페이지)이다. 새로 추가하면 여기에 적거나 카드에 등록한다.

- `{FE}/views/applicant/ApplicantHomeView.vue` — 지원자 홈
- `{FE}/views/applicant/ApplicantQuickLinkCards.vue` — 홈 바로가기 카드
- `{FE}/views/applicant/banners/*` — 홈 배너
- `{FE}/views/applicant/ApplicantBenefits.vue` — 인사제도(보상·교육·복리후생 탭)
- `{FE}/views/applicant/ApplicantInfoTabPanel.vue` — 인사제도 탭 패널
- `{FE}/views/applicant/ApplicantDutyIntroduction.vue` — 직무소개
- `{FE}/views/applicant/DutyIntroModalBody.vue` — 직무소개 모달
- `{FE}/views/applicant/ApplicantRecruitProcedure.vue` — 채용절차
- `{FE}/views/applicant/ApplicantPrivacy.vue` — 개인정보처리방침
- `{FE}/views/applicant/ApplicantBreadcrumb.vue` — 지원자 공통 breadcrumb
- `{FE}/views/common/htmlView.vue` — HTML 본문 렌더 공용 컴포넌트
- `{FE}/views/error/*` — 403·404
- `{FE}/views/samples/*` — 개발용 샘플

## 카드 템플릿

새 카드는 아래 형식을 따른다. `## 파일 지도` 제목은 점검 스크립트가 쓰므로 바꾸지 않는다.

```markdown
# <도메인 한글명> (`<card-name>`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [<카드>](<카드>.md)

## 요약
## 용어
## 파일 지도
### 백엔드
### 프론트
## API 계약
### 엔드포인트 상세
## 규칙·불변식
## 변경 레시피
## 검증
## 함정·결정
```
