# 로컬 LLM 유지보수용 문서 재편 설계

- 작성일: 2026-09-18
- 상태: 승인됨 (설계), 구현 계획 대기
- 범위: `recruit/` 모노레포 전체 문서 구조 (코드 변경 없음, 점검 스크립트 1개 추가)

## 1. 배경

- 이 프로젝트는 폐쇄망으로 반입되어 **로컬 LLM 기반 에이전트**가 유지보수한다. 모델·에이전트 도구는 미정, 컨텍스트는 최대 180K 토큰으로 가정한다.
- 현재 문서는 Claude Code 기준으로 쌓였고 다음 문제가 있다.
  - 필독 문서가 크다: 백엔드 `CLAUDE.md` 16KB + `docs/codex/01~04` 약 86KB + `api-contract.md` 124KB. 한국어는 토큰 효율이 낮아 필독만으로 수만 토큰을 쓴다.
  - 이력성 문서가 섞여 있다: `07-implementation-history.md` 349KB, `docs/codex/reports` 107개, `implementation` 86개, `docs/superpowers` specs/plans 다수. 검색 잡음이 되고, 옛 결정을 현행 규칙으로 오인할 위험이 있다.
  - `docs/codex/02-domain-design.md`는 구현 전 설계라 현재 코드와 다르다(예: `MessageBatch`, `DocumentEvaluation`는 미구현).
  - 백엔드 `AGENTS.md`와 `CLAUDE.md`가 거의 같은 내용으로 중복되어 있다.
  - 일부 도메인 지식이 레포 밖(`~/.claude` memory, 홈 `CLAUDE.md`)에 있다. 폐쇄망에는 반입되지 않는다.
- 코드 구조: 백엔드는 레이어별 평면 패키지다(`controller` 56, `service` 143, `dto/response` 142, `domain/entity` 46). 프론트는 `views/admin/<화면>`만 도메인별이고 `api`·`types`·`stores`는 레이어별이다. 따라서 **패키지(레이어)별 문서는 정보 가치가 낮다.** 기능 하나가 모든 레이어를 가로지르기 때문이다.

## 2. 목표와 성공 기준

1. 로컬 에이전트가 **루트 `AGENTS.md` → 레포 `AGENTS.md` → 도메인 카드 1~2개**만 읽고 수정할 파일과 검증 명령을 특정할 수 있다.
2. 작업 1건의 문서 읽기 총량이 **컨텍스트의 25%(약 45K 토큰) 이하**다.
3. Claude 전용 요소(skill, memory, 홈 `CLAUDE.md`)가 없어도 작업 규칙이 완결된다.
4. 문서와 코드의 불일치를 기계적으로 잡아내는 점검 수단이 있다.

## 3. 결정 사항

| 항목 | 결정 |
|---|---|
| 문서 단위 | **도메인별 통합 카드.** 카드 1개에 API 계약 + 백엔드 + 프론트를 담는다 |
| 레이어별 규칙 | 레포별 `AGENTS.md`에만 둔다 (코드 스타일·보안·테스트) |
| 진입 파일 | `AGENTS.md`를 정본으로 한다. `CLAUDE.md`는 `@AGENTS.md` import와 Claude 전용 규칙만 담는다 |
| API 계약 | `api-contract.md`를 카드별로 분할 이관한 뒤 archive로 옮긴다 |
| 이력 문서 | `docs/archive/`로 `git mv`한다. 루트 `.ignore`로 검색에서 제외한다 |
| 점검 | Node 스크립트 1개로 경로 존재, 커버리지, 크기를 검사한다 |

## 4. 목표 구조

```
recruit/
├── AGENTS.md                  # 정본 진입점 (≤12KB)
├── CLAUDE.md                  # "@AGENTS.md" + Claude 전용 규칙
├── .ignore                    # docs/archive/ 검색 제외
├── tools/check-docs.mjs       # 문서 점검 스크립트
├── docs/
│   ├── domains/
│   │   ├── _index.md          # 라우터
│   │   └── <domain>.md        # 카드 15개
│   ├── archive/               # 이력·옛 설계·보고서 (작업 시 읽지 않음)
│   └── superpowers/           # 이번 작업 spec/plan (완료 후 archive로 이동)
├── recruit_back/recruit_backend/
│   ├── AGENTS.md              # 백엔드 규칙 (≤15KB)
│   ├── CLAUDE.md              # "@AGENTS.md"
│   └── docs/adr/, docs/ops/   # 유지
└── recruit_front/
    └── AGENTS.md              # 프론트 규칙 (≤15KB)
```

크기 예산은 UTF-8 바이트 기준이며 한·영 혼합에서 대략 3.5바이트가 1토큰이다.

| 문서 | 권장 | 상한 |
|---|---|---|
| 루트 `AGENTS.md` | 12KB | 16KB |
| 레포 `AGENTS.md` | 15KB | 20KB |
| 도메인 카드 | 30KB | 40KB |
| `_index.md` | 8KB | 12KB |

작업 1건의 전형적인 읽기량: 루트 12 + 레포 15×2 + 카드 30×2 ≈ 100KB ≈ 30K 토큰. 목표 2를 충족한다.

## 5. 경로 표기 규약

카드와 `_index.md`는 다음 접두 표기를 쓴다. 정의는 루트 `AGENTS.md`와 `_index.md`에 두고, 각 카드 머리에도 한 줄로 반복한다.

| 표기 | 실제 경로 (레포 루트 기준) |
|---|---|
| `{BE}` | `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit` |
| `{BT}` | `recruit_back/recruit_backend/src/test/java/com/shinyoung/recruit` |
| `{BR}` | `recruit_back/recruit_backend/src/main/resources` |
| `{FE}` | `recruit_front/src` |

예: `{BE}/service/StageResultService.java`, `{FE}/views/admin/stageResult/AdminStageResultView.vue`

그 밖의 경로는 레포별 `AGENTS.md`를 포함해 모두 **레포 루트 기준**으로 쓴다(예: `recruit_back/recruit_backend/docs/adr/...`). 점검 스크립트는 `docs/`로 시작하는 경로를 레포 루트 기준으로 해석한다.

## 6. 도메인 카드

### 6.1 목록 (15개)

| 파일 | 도메인 | 주요 백엔드 | 주요 프론트 |
|---|---|---|---|
| `auth-account.md` | 인증·지원자 계정 | `AuthController`, `ApplicantSignUpController`, `ApplicantAccountController`, `security/auth/*`, `SecurityConfig`, LDAP | Login, `SignupView`, `AccountRecovery`, `ApplicantProfile`, NiceAuth 팝업, `authStore` |
| `role-menu.md` | 권한·메뉴 | `AdminRoleMappingController`, `MenuController` | `RoleMappingView`, `MenuManageView`, `menuStore` |
| `job-posting.md` | 공고·직무·근무지·공고 이미지·첨부 요건 | `JobPosting*Controller`, `JobPostingPublicController` | `views/admin/jobPosting/*`, `ApplicantRecruit*`, `ApplicationDetailView` |
| `question.md` | 질문 템플릿·공고별 질문 | `QuestionTemplateController`, `JobPostingQuestionController` | `views/admin/applicant/*`, `ApplicationFormQuestionTab` |
| `application-form.md` | 지원서 양식 설정 (config·layout) | `AdminApplicationForm*Controller` | `views/admin/applicationForm/*` |
| `application.md` | 지원서 작성·제출 (지원자) | `ApplicationController`, 섹션 컨트롤러(기본정보·학력·경력·자격증·어학·병역·수상·공백기간·답변), `ApplicationSubmitValidator` | `ApplicationFormView`, `views/applicant/application/sections/*` |
| `attachment.md` | 첨부 저장소 | `ApplicationAttachment*`, `AttachmentStorage*`, `AdminAttachmentStorageHealthController` | (지원서 화면 내부) |
| `admin-application.md` | 지원현황·지원서 상세·엑셀/PDF | `AdminApplication*Controller`, `AdminExportController`, `ApplicationPdfController` | `views/admin/application/*` |
| `stage-result.md` | 전형·전형결과 | `StageController`, `StageResult*Controller`, `ApplicationStageResultController` | `views/admin/stageResult/*` |
| `interview.md` | 면접 일정·평가 | `Interview*Controller`, `ApplicantInterviewController` | `views/admin/interview/*` |
| `master-data.md` | 공통코드·학교·주소 | `*CommonCodeController`, `*SchoolController`, `SchoolSearchController`, `AddressSearchController` | `AdminCommonCodeManageView` |
| `board.md` | FAQ·공지 | `FaqController`, `AdminFaqController`, `BoardController` | `FaqView`, `AdminFaqManageView`, `NoticeListView` |
| `statistics.md` | 관리자 대시보드·통계 | `AdminStatisticsController` | `AdminHomeView`, `views/admin/dashboard/*` |
| `privacy-audit.md` | 보존·파기·감사 로그 | `AdminRetentionController`, `AdminAuditController`, `Purge*`, `Retention*`, `ActivityLog*` | - |
| `client-event-log.md` | 클라이언트 이벤트 로그 | `ClientEventLogController`, `AdminClientEventLogController` | `telemetryClient`, `clientEventLogger`, `httpErrorTelemetry` |

지원자 정적 페이지(홈, 인사제도, 직무소개, 채용절차, 개인정보처리방침)는 카드 없이 `_index.md`에 파일 경로만 적는다.

### 6.2 경계 규칙

- 모든 `*Controller.java`와 `{FE}/views/**/*.vue`는 **정확히 한 카드의 파일 지도에 소유자로 등장**한다. 점검 스크립트로 검증한다.
- 다른 도메인 클래스를 사용하면 소유 카드로 링크만 건다(예: `application.md` → `attachment.md`). 내용은 중복하지 않는다.
- 공통 기반(`ApiResponse`, `GlobalExceptionHandler`, `common/*`, `config/*` 중 도메인 무관 항목, `{FE}/api/client.ts`, 레이아웃)은 레포 `AGENTS.md`에 적는다.
- 경계는 구현 계획 단계에서 실제 코드를 보고 조정할 수 있다. 카드 수가 바뀌면 이 문서의 6.1을 갱신한다.

### 6.3 카드 템플릿

```markdown
# <도메인 한글명> (<code-name>)

> 경로 표기: {BE} {BT} {BR} {FE} — 정의는 docs/domains/_index.md
> 관련 카드: [..](..)

## 요약
3줄 이내. 무엇을 하는 도메인인지, 누가 쓰는지 (지원자/관리자/면접관).

## 용어
| 한글 | 코드 식별자 | 비고 |

## 파일 지도
### 백엔드
| 레이어 | 파일 | 역할 |   (controller / service / entity / repository / dto / enum / exception / test)
### 프론트
| 구분 | 파일 | 역할 |    (route / view / component / api / types / store / test)

## API 계약
| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |
상태: 🟢 확정 / 🟡 초안 / 🔴 미정 / ⛔ 폐지.
필드 모양 요약 수준만 적는다. 정확한 타입·검증은 백엔드 DTO가 단일 출처다.

## 규칙·불변식
코드만 봐서는 드러나지 않는 것 (상태 전이, 권한 경계, 신입/경력 차이, 삭제 정책 등).
각 규칙 옆에 강제하는 코드 위치를 적는다.

## 변경 레시피
### <자주 하는 작업>
1. 수정 파일과 순서 체크리스트

## 검증
이 도메인 한정 백엔드 테스트 명령 + 프론트 type-check.

## 함정·결정
- 과거 버그·주의점 1~2줄
- ADR: [0002 ...](../../recruit_back/recruit_backend/docs/adr/...)
```

### 6.4 `_index.md` 형식

1. 경로 표기 정의 (5절 표)
2. 카드 목록: 파일 · 한 줄 설명
3. **역색인**: 화면명·라우트 경로·라우트 name·API 경로 접두·클래스 접두어·한국어 키워드 → 카드
4. 카드 없는 파일 목록 (지원자 정적 페이지 등)

## 7. AGENTS.md 계층

### 7.1 루트 `AGENTS.md` (정본 진입점)

- 구성: 모노레포 구조, 레포별 기술 스택 한 줄
- 응답 언어: 한국어 (홈 `CLAUDE.md` Language Policy 요지)
- 엔지니어링 규칙: 가정 명시, 최소 구현, 외과적 변경, 검증 후 보고 (홈 `CLAUDE.md` Core Engineering Rules 요지)
- **읽기 라우팅**: 작업 유형 → 읽을 문서 (`_index.md` → 카드 → 레포 `AGENTS.md`)
- 화면 슬라이스 워크플로우: 현 루트 `CLAUDE.md` 3절을 계승하고, 계약 기준을 `api-contract.md`에서 **도메인 카드의 API 계약 절**로 변경
- 검증 명령 (현 루트 `CLAUDE.md` 5절)
- git 규칙 (현 루트 `CLAUDE.md` 6절). 홈 디렉터리 관련 조항은 폐쇄망 환경에 맞게 일반화
- **문서 갱신 의무 (DoD)**: 코드를 바꾸면 해당 카드의 파일 지도·API 계약·규칙을 같은 변경에서 갱신하고 `node tools/check-docs.mjs`를 통과시킨다
- `docs/archive/`는 작업 근거로 쓰지 않는다. 과거 경위를 조사할 때만 참고한다

### 7.2 루트 `CLAUDE.md`

- `@AGENTS.md` import
- Claude 전용: design-report 기반 HTML 리포트 정책 등 폐쇄망에서 쓰지 않는 규칙만

### 7.3 백엔드 `AGENTS.md`

- 현 `AGENTS.md` + `CLAUDE.md` 병합: 금지사항(원본 Excel·보안·구조 변경·git), 코드 스타일(패키지·Entity·DTO·Service·Controller), 인증/인가 기준, 테스트 기준
- `01-project-context.md`와 `04-implementation-guide.md` 중 **현행 코드와 일치하는** 설정·빌드·스타일 항목을 흡수
- 공통 기반 클래스 안내 (6.2)
- 제거: phase 이력 서술, Codex/Claude 도구별 작업 방식, 문서화 규칙(Implementation Documentation Rules, Documentation Output Rule — docs/codex 산출물 규칙은 폐지)
- 백엔드 `CLAUDE.md`는 `@AGENTS.md`만 남긴다

### 7.4 프론트 `AGENTS.md`

- 유지: 기술 스택, TS·Vue·라우팅·store·API·스타일·한글·에러·보안 규칙, 명령, DoD
- 정리: 초기 셋업 전제 절(Current Project State, Recommended First Task, Setup and Validation Flow 중 이미 끝난 내용)
- 추가: 공통 기반 파일 안내(`api/client.ts`, `apiError.ts`, 레이아웃, `common/*`)

## 8. 기존 문서 처리

`git mv`로 이동해 이력을 보존한다. archive 안의 구조는 원래 위치를 반영한다.

| 원래 위치 | 처리 | 이동 위치 |
|---|---|---|
| `api-contract.md` | 섹션별로 카드 API 계약 절에 이관 (🟢/🟡/⛔ 상태, 날짜 보존) | `docs/archive/api-contract.md` |
| 백엔드 `docs/codex/01~07` | 현행 유효 내용만 AGENTS·카드에 반영 | `docs/archive/backend/codex/` |
| 백엔드 `docs/codex/{design,implementation,reports,plans,templates}` | 규칙·불변식 추출 시 참고 | `docs/archive/backend/codex/` |
| 백엔드 `docs/codex/ops/*.sql` | 운영 DDL이므로 유지 | `recruit_back/recruit_backend/docs/ops/` |
| 백엔드 `docs/adr/` | 유지, 카드에서 링크 | 그대로 |
| 백엔드 `docs/instructions/`, `docs/superpowers/` | 이동 | `docs/archive/backend/` |
| 백엔드 `CONTEXT.md` | 용어를 카드 용어 절로 이관 | `docs/archive/backend/` |
| 백엔드 `instruction.md`, `README-codex-docs.md`, `HELP.md` | 이동 | `docs/archive/backend/` |
| 루트 `docs/superpowers/` (기존 specs/plans) | 이동. 이번 작업 spec/plan은 완료 시 이동 | `docs/archive/superpowers/` |
| 루트 `docs/design/` (HTML 시안) | 이동 | `docs/archive/design/` |
| 루트 `docs/archive/reports/` | 그대로 | - |
| `~/.claude` memory "공고유형 신입/경력" | `job-posting.md` 규칙 절로 이관 (신입=PUBLIC, 경력=EXPERIENCED 재사용, 학기별 성적은 신입만, 마감 공고 비노출). 코드로 사실 확인 후 기재 | - |
| 루트 `design/` (미추적, 진행 중 작업물) | **건드리지 않음** | - |

이동 후 남은 문서 안의 상대 링크(`docs/codex/...` 등)는 카드와 AGENTS에서 새 경로로 고친다. archive 내부 링크는 고치지 않는다.

## 9. 점검 스크립트 `tools/check-docs.mjs`

- 실행: 레포 루트에서 `node tools/check-docs.mjs`. 의존성 없이 Node 표준 모듈만 쓴다. 실패 시 exit 1.
- 대상: `AGENTS.md`(루트·레포별), `docs/domains/*.md`
- 검사
  1. **경로 존재**: 백틱 안의 토큰 중 `{BE}`·`{BT}`·`{BR}`·`{FE}`로 시작하거나 `recruit_back/`·`recruit_front/`·`docs/`로 시작하는 것을 확장해 파일 또는 디렉터리 존재를 확인한다. 글롭(`*`)이 들어간 토큰은 매칭이 1개 이상인지 확인한다.
  2. **소유 커버리지**: `{BE}/controller/*Controller.java`와 `{FE}/views/**/*.vue` 각각이 카드의 `## 파일 지도` 절(다음 `## ` 제목 전까지) 안 경로 토큰에 정확히 1회 매칭되는지 확인한다. 글롭 토큰은 매칭된 파일 전체를 소유한 것으로 본다. 2개 이상 카드에 매칭되면 중복, 0개면 누락으로 실패한다. `_index.md`의 "카드 없는 파일" 절에 적힌 파일은 예외다.
  3. **크기**: 4절 상한 초과 시 실패, 권장 초과 시 경고.
- 출력: 한국어 메시지, 문제별 한 줄 (`파일:줄: 내용`)

## 10. 작성 방식

- 카드는 **현재 코드(작업 트리)를 기준으로** 쓴다. 옛 설계·이력 문서는 "왜"를 보충할 때만 쓰고, 코드와 다르면 코드를 따른다.
- 도메인별로 독립 작성이 가능하므로 병렬로 진행한다. 순서: 공통(루트/레포 AGENTS, `_index.md` 골격, 점검 스크립트) → 카드 15개 → archive 이동과 링크 정리 → 전체 점검.
- 검증: 점검 스크립트 통과 + 카드별 교차 검토(파일 지도 누락, API 표와 컨트롤러 매핑 일치, 규칙의 코드 근거).
- 코드는 수정하지 않는다. 문서 작성 중 발견한 결함은 보고만 한다.

## 11. 선행 조건과 리스크

- **작업 트리가 dirty 상태다**: `api-contract.md`와 프론트·백엔드 파일 다수가 수정되어 있다(진행 중 작업). 이 작업은 `api-contract.md`를 이동하고 카드가 코드를 반영하므로, **시작 전에 진행 중 변경분을 커밋하거나 정리해야 한다.** 커밋 여부는 사용자가 결정한다.
- 옛 문서를 이동하는 동안 Claude Code 기반 작업이 계속되면 경로 참조가 깨질 수 있다. 이동은 한 번에 수행한다.
- 카드 갱신 규율은 결국 에이전트가 지켜야 한다. 점검 스크립트는 경로와 커버리지만 잡고 의미 불일치는 못 잡는다.
- `.ignore`는 ripgrep 기반 도구에만 효과가 있다. 다른 도구를 쓰면 도구 설정으로 archive를 제외해야 한다(반입 후 도구 확정 시 처리).

## 12. 범위 밖

- 코드 구조 변경 (백엔드 패키지를 도메인별로 재편하는 것 등)
- 파일 지도·엔드포인트 목록 자동 생성
- 에이전트 도구별 설정 파일 (도구 확정 후 별도 작업)
- archive 내부 문서 수정
