# 설계서: 지원서 폼 섹션 (공백기간 · 자기소개)

- 작성일: 2026-06-24
- 상태: 설계 확정(사용자 승인) → 구현 계획 단계로 진행
- 범위: 프론트엔드(`recruit_front/`)만. 백엔드는 이미 구현되어 있어 **변경 없음**.
- 선행: 어학·수상·자격증 3종 슬라이스 완료([2026-06-23 설계서](2026-06-23-application-sections-language-award-certificate-design.md)). 본 슬라이스는 동일 섹션 컴포넌트 패턴을 이어간다.

## 1. 배경 / 목표

`ApplicationFormView`의 `sectionComponentMap`에서 아직 placeholder인 `GAP_PERIOD`(공백기간)와 `QUESTION_ANSWER`(자기소개/질문)를 실제 입력 컴포넌트로 교체한다. 새 화면 목업은 없으므로 **시각 디자인은 앞선 3개 섹션과 동일한 카드 스타일**을 재사용한다.

백엔드를 먼저 확인한 결과, 두 섹션의 **데이터 입력 방식이 서로 다르다**:

- **공백기간**은 앞 3개와 동일한 **전체 교체(replace-list)** 방식(사용자가 항목을 추가/삭제).
- **자기소개**는 **질문 응답형**이다. 공고가 정의한 질문(`JobPostingQuestion`)을 백엔드가 내려주고, 지원자는 각 질문에 **답변만 작성**한다. 항목 추가/삭제·"해당 사항 없음" 개념이 없다.

### 비목표 (Out of scope)

- 백엔드 변경, 새 라우트 추가.
- 관리자 화면(`/api/admin/...`), 질문 템플릿/공고 질문 관리(`JobPostingQuestion`/`QuestionTemplate`는 admin 영역).
- 나머지 placeholder 섹션(MILITARY/EDUCATION/CAREER/ATTACHMENT).

## 2. 아키텍처 (기존 패턴 재사용)

`BasicInfoSection`/`LanguageSection` 등과 동일:
- 컴포넌트는 `src/views/applicant/application/sections/*.vue`, props `SectionComponentProps`(`applicationId`, `section`, ...) 수신.
- `defineExpose({ saveDraft, validateBeforeSubmit })`로 부모 마법사의 임시저장/최종제출 버튼과 연동.
- API 모듈 `src/api/application/sections/*Api.ts`, 타입 `src/types/application/sections/*.ts`.
- 공통: `apiClient`, `getApiErrorMessage`, `logClientEvent`, `ApiResponse<T>`(payload는 `res.data.data`).
- 컴포넌트 자체 완결(공통 컴포저블 미추출). scoped CSS는 앞 3개의 카드 스타일을 재사용.

## 3. 백엔드 계약 (단일 출처 — 이미 구현됨)

### 3.1 공백기간 GAP_PERIOD — `/applications/{applicationId}/gap-periods`

- `GET` → `ApiResponse<GapPeriodResponse[]>`
- `POST` body `GapPeriodReplaceRequest { gapPeriods: GapPeriodRequest[] }` → `ApiResponse<GapPeriodResponse[]>` (전체 교체, 빈 배열 허용)

| 필드 | 타입 | 필수 | 라벨 | 비고 |
|---|---|---|---|---|
| startDate | LocalDate(`YYYY-MM-DD`) | ✓ NotNull | 시작일 | |
| endDate | LocalDate | ✓ NotNull | 종료일 | |
| gapType | enum `EDUCATION`/`CAREER`/`OTHER` | ✓ NotNull | 구분 | 고정 enum (§4-A 라벨) |
| reason | string | ✓ NotBlank | 사유 | 한 줄 입력 |
| description | string | — Size≤2000 | 상세설명 | textarea |
| sortOrder | int(≥0) | ✓ | — | 배열 index 자동 |

응답 항목은 위 필드 + `gapPeriodId`.

### 3.2 자기소개 QUESTION_ANSWER — `/applications/{applicationId}/questions` (GET) · `/answers` (POST)

- `GET /applications/{applicationId}/questions` → `ApiResponse<ApplicationQuestionResponse[]>`
  - 공고가 정의한 질문 목록 + 지원자의 기존 답변이 병합되어 내려온다(미답변이면 answer 필드 null).
- `POST /applications/{applicationId}/answers` body `ApplicationAnswerReplaceRequest { answers: [{ questionId, answerText }] }` → `ApiResponse<ApplicationQuestionResponse[]>` (전체 교체)

`ApplicationQuestionResponse` (질문 메타 + 답변):

| 필드 | 타입 | 비고 |
|---|---|---|
| questionId | number | 질문 PK (답변 전송 시 사용) |
| questionText | string | 질문 본문(=필드 라벨) |
| helperText | string? | 보조 설명 |
| category | enum `SELF_INTRODUCTION`/`GENERAL`/`JOB_SPECIFIC`/`ETC` | 카테고리 칩 (§4-B 라벨) |
| answerType | enum `SHORT_TEXT`/`LONG_TEXT` | 입력 위젯 선택 |
| required | boolean | 필수 답변 여부 |
| minLength | number? | 최소 글자수 |
| maxLength | number? | 최대 글자수 |
| sortOrder | number | 정렬 |
| answerId | number? | 기존 답변 PK(표시용, 전송 안 함) |
| answerText | string? | 기존 답변 본문 |
| updatedAt | datetime? | 표시 안 함 |

`ApplicationAnswerRequest`: `questionId`(NotNull), `answerText`(Size≤5000). **draft POST에 required/minLength 강제 없음**(answerText는 NotBlank 아님) → 부분 저장 가능.

## 4. 확정된 설계 결정

- **A. gapType 라벨(하드코딩, 고정 enum):** `EDUCATION`=학업, `CAREER`=경력, `OTHER`=기타. `<a-select>` 고정 options(공통코드 아님).
- **B. 자기소개 카테고리 칩 표시:** `SELF_INTRODUCTION`=자기소개, `GENERAL`=일반, `JOB_SPECIFIC`=직무, `ETC`=기타. 매핑에 없는 값은 원문 표시.
- **C. 컴포넌트명:** `GapPeriodSection.vue`, `QuestionAnswerSection.vue`.

## 5. 파일 계획

### 신규 (6)

| 경로 | 내용 |
|---|---|
| `src/types/application/sections/gapPeriod.ts` | `GapType`, `GapPeriodItem`, `GapPeriodRequestItem`, `GapPeriodReplaceRequest`, `GapPeriodResponse` |
| `src/types/application/sections/questionAnswer.ts` | `QuestionAnswerType`, `QuestionCategory`, `ApplicationQuestionItem`, `ApplicationAnswerRequestItem`, `ApplicationAnswerReplaceRequest`, `ApplicationQuestionResponse` |
| `src/api/application/sections/gapPeriodApi.ts` | `getApplicationsGapPeriods(id)`, `replaceApplicationsGapPeriods(id, payload)` |
| `src/api/application/sections/questionAnswerApi.ts` | `getApplicationsQuestions(id)`, `replaceApplicationsAnswers(id, payload)` |
| `src/views/applicant/application/sections/GapPeriodSection.vue` | 공백기간(카드형) |
| `src/views/applicant/application/sections/QuestionAnswerSection.vue` | 자기소개(질문 응답형) |

### 수정 (2)

| 경로 | 변경 |
|---|---|
| `src/views/applicant/ApplicationFormView.vue` | import 2 + `sectionComponentMap`의 `GAP_PERIOD`→`GapPeriodSection`, `QUESTION_ANSWER`→`QuestionAnswerSection` |
| `api-contract.md` | 공백기간·자기소개 섹션 신규 추가(🟢) |

## 6. 컴포넌트 동작 사양

### 6.1 GapPeriodSection (카드형 — 수상/자격증과 동일 골격)

- 상태: `items = reactive<GapPeriodItem[]>([])`, `notApplicable = ref(false)`, `loading`.
- "공백기간 없음 (해당 사항 없음)" 체크박스는 **템플릿에서 주석 처리**(앞 3종과 동일 — 없음 상태가 백엔드에 영속화되지 않아 비활성). `notApplicable` 로직은 script에 두되 항상 false로 동작(목록 항상 표시). 백엔드 영속화가 생기면 일괄 복구.
- 카드 4열 그리드: (시작일\* date | 종료일\* date) / (구분\* select | 사유\* input) / (상세설명 textarea ≤2000, colspan).
- 추가/삭제, `sortOrder=index`.
- `saveDraft`: `validate()` 통과 후 POST(빈 카드 불가 — 백엔드 `@NotNull`/`@NotBlank`), 성공 시 응답으로 `items` 갱신, 실패 시 `logClientEvent`+`getApiErrorMessage` 후 throw.
- `validateBeforeSubmit`: NA면 통과. 아니면 각 카드 `startDate`/`endDate`/`gapType`/`reason` 필수 검사. 섹션 `required`인데 0개 & NA 아님 → 실패.

### 6.2 QuestionAnswerSection (질문 응답형)

- 상태: `items = reactive<ApplicationQuestionItem[]>([])`(질문 메타 + 가변 `answerText`), `loading`. **NA 토글·추가/삭제 없음.**
- 마운트: `GET /questions` → `items`에 매핑(`answerText ?? ''`), `sortOrder`로 정렬(백엔드 정렬을 신뢰하되 안전하게 정렬).
- 렌더: 질문 0개면 빈 상태. 아니면 질문 카드마다:
  - 카테고리 칩(§4-B) + 필수 배지(있으면)
  - 질문 본문 `questionText`(필수면 `*`), `helperText`(있으면 보조 문구)
  - 답변 입력: `SHORT_TEXT`→`a-input`, `LONG_TEXT`→`a-textarea :rows="4"`; 둘 다 `:maxlength="maxLength ?? 5000"` + `show-count`(백엔드 5000 상한 보호).
  - `minLength` 있으면 "최소 N자" 안내.
- `saveDraft`: 입력된(공백 trim 후 비어있지 않은) 답변만 `{ questionId, answerText }`로 POST(부분 저장 허용, 비운 답변은 omit→replace로 제거). 성공 시 응답으로 갱신. 실패 시 `logClientEvent`+`getApiErrorMessage` 후 throw. **draft에 required/minLength 강제 안 함.**
- `validateBeforeSubmit`: 각 질문에 대해 — `required`면 답변 trim 비어있지 않아야 하고 `minLength`(있으면) 이상; 답변이 있으면 `maxLength`(있으면) 이내. 위반 시 해당 질문 문구로 `message.warning` + `false`.
- `defineExpose({ saveDraft, validateBeforeSubmit })`.

## 7. 계약 문서(api-contract.md) 갱신안

- `### 화면: 지원자 공백기간 (ApplicationGapPeriod)` 신규(엔드포인트 `/gap-periods`, 필드 요약, gapType enum, 🟢).
- `### 화면: 지원자 자기소개/질문 (ApplicationQuestionAnswer)` 신규(`GET /questions` + `POST /answers`, 질문 메타/답변 요약, 🟢).

## 8. 알려진 한계 / 위험

- **자기소개는 공고 질문에 의존**: 공고에 질문(`JobPostingQuestion`)이 없으면 빈 상태로 표시된다(정상). 질문 정의는 admin 영역으로 본 범위 밖.
- **draft 검증 비대칭**: 공백기간은 백엔드 `@NotNull`/`@NotBlank` 때문에 임시저장도 행 필수값을 요구하지만, 자기소개는 draft 부분 저장이 가능하다(최종 제출에서만 required/minLength 강제).
- **"해당 사항 없음" 체크박스 비활성(주석)**: 없음 상태가 백엔드에 영속화되지 않아(빈 배열=미입력과 구분 불가) 2026-06-24 결정으로 4개 리스트 섹션 모두 체크박스를 주석 처리. 빈 배열=없음으로 취급. ⚠️ 섹션이 `required`로 설정되면 0건일 때 최종제출이 막히므로(없음 체크 경로 제거), 그 경우 `validateBeforeSubmit`의 빈-필수 차단도 완화 필요.
- 날짜는 `a-date-picker value-format="YYYY-MM-DD"`로 문자열 모델 사용.
- 리포지토리에 `noUncheckedIndexedAccess`가 켜져 있어 인덱스 접근(`items[i]`)에는 `if (!item) continue` 가드가 필요(앞 3개와 동일).

## 9. 검증 방법

`recruit_front/`에서 `npm run type-check`(기본), 필요 시 `npm run build`. 단위 테스트는 필요 시에만(AGENTS.md). 실서버 연동은 백엔드 기동 환경에서 별도 확인.

## 10. 수용 기준 (Acceptance)

1. 마법사에서 공백기간/자기소개 단계가 placeholder가 아닌 실제 입력 화면으로 렌더된다.
2. 공백기간: 항목 추가/삭제·해당없음 토글·구분 select(학업/경력/기타) 동작, replace POST/GET 복원.
3. 자기소개: 공고 질문이 카드로 렌더되고, answerType에 따라 input/textarea, 답변 저장/복원, 카테고리 칩 표시.
4. 최종 제출 검증: 공백기간 필수값, 자기소개 required/minLength 차단.
5. `npm run type-check` / `npm run build` 통과.
6. `api-contract.md`가 구현과 일치(🟢).
