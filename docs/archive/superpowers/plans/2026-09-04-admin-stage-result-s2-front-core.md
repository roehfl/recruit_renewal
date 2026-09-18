# 전형결과 관리 S2 (프론트 본체) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자 화면 `/admin/stage-results`를 만든다. 공고 선택 → 단계 스텝퍼 → 상태 배너·카운트 → 그리드에서 결과를 직접 판정하고, 대상자 초기화·전형 시작·결과 발표·단계 마감까지 한 화면에서 처리한다.

**Architecture:** S1이 확정한 백엔드 API를 그대로 쓴다(백엔드 변경 없음). 화면은 본체 뷰 하나 + 프레젠테이션 컴포넌트 3개로 나눈다. 본체가 로딩·상태·판정 편집 버퍼·라이프사이클 명령을 모두 소유하고, 자식은 props/emit만 받는다. 결과 목록은 비페이징 List라 필터·페이징·카운트를 클라이언트에서 계산한다.

**Tech Stack:** Vue 3 `<script setup lang="ts">`, TypeScript, Vue Router, ant-design-vue 4, Axios. 상태 관리는 Pinia 없이 뷰 로컬(`ref`/`computed`)로 충분하다 — 다른 화면과 공유할 상태가 없다.

**설계서:** `docs/superpowers/specs/2026-09-04-admin-stage-result-management-design.md` (§3 동작 모델, §4 화면 사양, §11 S1 완료 후 알아야 할 것)
**계약:** `api-contract.md`의 "전형결과 관리" 섹션
**시안:** `docs/design/전형결과 관리 관리자 시안.html` (시안 A 탭 + "상태별 화면" 탭)

**작업 루트:** 모든 경로는 `recruit_front/` 기준. 검증은 그 디렉터리에서:

```bash
npm run type-check
```

**커밋:** 프로젝트 규칙(`recruit/CLAUDE.md` §6)상 사용자가 명시 요청할 때만 커밋한다. 각 Task는 `type-check` 통과로 끝내고 커밋 단계를 두지 않는다.

**S2 범위 밖(후속 슬라이스):** 엑셀 템플릿 다운로드·업로드 미리보기·정정 모달은 S3, 단계 설정 드로어와 절충 링크는 S4다. S2에서는 해당 버튼을 **렌더하지 않는다**(비활성 버튼도 두지 않는다 — 눌러서 아무 일도 안 일어나는 버튼은 만들지 않는다). 단, 단계가 0건인 공고의 빈 상태 문구는 S4 드로어를 전제하므로 Task 8에서 임시 안내로 처리한다.

---

## 백엔드 계약 요약 (S1 확정 — 이 계획이 의존하는 전부)

경로는 모두 `apiClient`의 `baseURL`(`VITE_API_BASE_URL`, 예 `/api`) 뒤에 붙는다. 응답은 전부 `ApiResponse<T>`.

| 메서드 | 경로 | 요청 | 응답 `data` |
| --- | --- | --- | --- |
| GET | `/admin/job-postings?page&size` | — | `PageResponse<AdminJobPostingListItem>` |
| GET | `/admin/job-postings/{jobPostingId}/stages` | — | `StageListResponse[]` (stageOrder 오름차순) |
| GET | `/admin/stages/{stageId}/results` | — | `AdminStageResultResponse[]` (비페이징) |
| POST | `/admin/stages/{stageId}/results/initialize` | 없음 | `{ stageId, createdCount, existingCount, skippedCount, results[] }` |
| POST | `/admin/stages/{stageId}/results/bulk` | `{ results: [{ stageResultId, resultStatus, score, comment }] }` | `{ stageId, updatedCount, results[] }` |
| POST | `/admin/job-postings/{jobPostingId}/stages/{stageId}/start` | 없음 | `Long`(stageId) |
| POST | `/admin/job-postings/{jobPostingId}/stages/{stageId}/announce` | 없음 | `Long` |
| POST | `/admin/job-postings/{jobPostingId}/stages/{stageId}/close` | 없음 | `Long` |
| GET | `/admin/applications?jobPostingId&status=SUBMITTED&page=0&size=1` | — | `PageResponse<...>` — `totalElements`만 쓴다 |

`StageListResponse`: `{ id, jobPostingId, stageName, stageType, stageOrder, status, resultAnnouncementDateTime, finalStage }`
`AdminStageResultResponse`: `{ stageResultId, stageId, applicationId, applicantName, jobPositionId, jobPositionName, applicationStatus, resultStatus, score, comment, submittedAt, decidedAt, decidedBy, workLocation, applicationType, finalEducationLevel, finalSchoolName, previousStageResultStatus }`

가드(프론트가 미러링해야 함):
- `initialize` — 단계 READY 또는 IN_PROGRESS
- `bulk` — 단계 IN_PROGRESS, `resultStatus`에 `PENDING` 불가
- `start` — 공고 PUBLISHED + 단계 READY
- `announce` — 공고 PUBLISHED + 단계 IN_PROGRESS + 결과 행 누락·PENDING 0건
- `close` — 공고 PUBLISHED + 단계 RESULT_ANNOUNCED
- `bulk` 낙관적 잠금 충돌 → **409**

**결과 상태 한글 라벨은 API로 내려오지 않는다.** 프론트 상수 맵이 백엔드 `StageResultStatusLabels`와 글자까지 같아야 한다(대기/합격/불합격/보류/결시/철회). 단계 유형 라벨은 공통코드 `STAGE_TYPE`가 있으나, S2는 단계명(`stageName`)을 그대로 쓰므로 조회하지 않는다.

---

## 파일 구조

| 파일 | 역할 | 변경 |
| --- | --- | --- |
| `src/types/admin/stage.ts` | Stage·StageResult 타입, 상태/라벨 상수 | 신규 |
| `src/api/admin/adminStageApi.ts` | 위 표의 엔드포인트 래퍼 | 신규 |
| `src/views/admin/stageResult/StageStepper.vue` | 단계 스텝퍼(표시 + 선택 emit) | 신규 |
| `src/views/admin/stageResult/StageResultCounts.vue` | 카운트 카드 6개(표시 + 필터 토글 emit) | 신규 |
| `src/views/admin/stageResult/StageResultGrid.vue` | 필터 툴바 + 일괄 버튼 + 그리드 | 신규 |
| `src/views/admin/stageResult/AdminStageResultView.vue` | 본체: 로딩·상태·판정 버퍼·라이프사이클 | 신규 |
| `src/routes/adminRoutes.ts` | `/admin/stage-results` 라우트 추가 | 수정 |

책임 경계: 자식 3개는 **상태를 소유하지 않는다.** props로 받은 것만 그리고 사용자 조작을 emit한다. 본체가 API 호출·버퍼·확인 모달을 전부 갖는다. 이렇게 나눠야 각 파일이 200~350줄 안에 들어오고 독립적으로 읽힌다.

---

### Task 1: 타입 정의

**Files:**
- Create: `src/types/admin/stage.ts`

- [ ] **Step 1: 파일 작성**

`src/types/admin/stage.ts`:

```ts
/*
 * 전형 단계(Stage)와 전형 결과(StageResult) 타입.
 * 백엔드 StageListResponse / AdminStageResultResponse 와 대응한다(api-contract.md "전형결과 관리").
 */

/** 단계 라이프사이클. READY → IN_PROGRESS → RESULT_ANNOUNCED → CLOSED */
export type StageStatus = 'READY' | 'IN_PROGRESS' | 'RESULT_ANNOUNCED' | 'CLOSED'

export type StageType = 'DOCUMENT' | 'FIRST_INTERVIEW' | 'SECOND_INTERVIEW' | 'FINAL_INTERVIEW' | 'ETC'

/** 전형 결과 상태. PENDING 은 초기화 직후 값이며 판정으로 지정할 수 없다. */
export type StageResultStatus = 'PENDING' | 'PASSED' | 'FAILED' | 'ABSENT' | 'WITHDRAWN' | 'HOLD'

export type EducationLevel = 'HIGH_SCHOOL' | 'COLLEGE' | 'UNIVERSITY' | 'MASTER' | 'DOCTOR'

export type JobPositionApplicationType = 'NEW_GRADUATE' | 'EXPERIENCED' | 'NEW_GRADUATE_OR_EXPERIENCED'

export interface StageListItem {
  id: number
  jobPostingId: number
  stageName: string
  stageType: StageType
  stageOrder: number
  status: StageStatus
  /** 발표 예정 일시. 미정이면 null */
  resultAnnouncementDateTime: string | null
  finalStage: boolean
}

/** 전형 결과 한 행. 뒤쪽 6개는 그리드 표시용 파생 필드다(2026-09-04 백엔드 확장). */
export interface AdminStageResult {
  stageResultId: number
  stageId: number
  applicationId: number
  applicantName: string
  jobPositionId: number
  jobPositionName: string
  applicationStatus: 'DRAFT' | 'SUBMITTED' | 'WITHDRAWN'
  resultStatus: StageResultStatus
  score: number | null
  comment: string | null
  submittedAt: string | null
  decidedAt: string | null
  /** 판정자 로그인 id. 미판정이면 null */
  decidedBy: string | null
  /** 지원자가 선택한 근무지 표시명. 근무지 후보가 없는 모집분야면 null */
  workLocation: string | null
  applicationType: JobPositionApplicationType
  finalEducationLevel: EducationLevel | null
  finalSchoolName: string | null
  /** stageOrder 가 바로 앞인 단계의 결과. 첫 단계이거나 그 단계에 결과 행이 없으면 null */
  previousStageResultStatus: StageResultStatus | null
}

export interface StageResultBulkUpdateItem {
  stageResultId: number
  resultStatus: StageResultStatus
  score: number | null
  comment: string | null
}

export interface StageResultBulkUpdateRequest {
  results: StageResultBulkUpdateItem[]
}

export interface StageResultBulkUpdateResponse {
  stageId: number
  updatedCount: number
  results: AdminStageResult[]
}

export interface StageResultInitializeResponse {
  stageId: number
  createdCount: number
  existingCount: number
  skippedCount: number
  results: AdminStageResult[]
}

/*
 * 결과 상태 한글 라벨. 백엔드 StageResultStatusLabels 와 **글자까지 같아야 한다** —
 * 엑셀 템플릿의 드롭다운 값이 이 표에서 나오므로, 한쪽만 바꾸면 엑셀과 화면의 단어가 갈라진다.
 */
export const STAGE_RESULT_STATUS_LABELS: Record<StageResultStatus, string> = {
  PENDING: '대기',
  PASSED: '합격',
  FAILED: '불합격',
  ABSENT: '결시',
  WITHDRAWN: '철회',
  HOLD: '보류',
}

/** 배지 색. ant-design-vue a-tag 의 프리셋 색 이름이다. */
export const STAGE_RESULT_STATUS_COLORS: Record<StageResultStatus, string> = {
  PENDING: 'orange',
  PASSED: 'green',
  FAILED: 'red',
  ABSENT: 'default',
  WITHDRAWN: 'default',
  HOLD: 'blue',
}

/** 판정으로 지정 가능한 값 = 전체 − PENDING. 셀렉트 옵션 순서다. */
export const DECIDABLE_RESULT_STATUSES: StageResultStatus[] = [
  'PASSED',
  'FAILED',
  'HOLD',
  'ABSENT',
  'WITHDRAWN',
]

/** 선택 행 일괄 적용 버튼. 철회는 개별 판단이 필요해 버튼에서 제외한다(행 셀렉트로만 지정). */
export const BULK_APPLY_STATUSES: StageResultStatus[] = ['PASSED', 'FAILED', 'HOLD', 'ABSENT']

export const STAGE_STATUS_LABELS: Record<StageStatus, string> = {
  READY: '대기',
  IN_PROGRESS: '진행중',
  RESULT_ANNOUNCED: '발표완료',
  CLOSED: '마감',
}

export const EDUCATION_LEVEL_LABELS: Record<EducationLevel, string> = {
  HIGH_SCHOOL: '고등학교',
  COLLEGE: '전문대학교',
  UNIVERSITY: '대학교',
  MASTER: '대학원(석사)',
  DOCTOR: '대학원(박사)',
}

export const APPLICATION_TYPE_LABELS: Record<JobPositionApplicationType, string> = {
  NEW_GRADUATE: '신입',
  EXPERIENCED: '경력',
  NEW_GRADUATE_OR_EXPERIENCED: '신입/경력',
}
```

- [ ] **Step 2: 타입 체크**

```bash
npm run type-check
```

Expected: 통과. (아직 아무도 이 타입을 쓰지 않으므로 사용처 오류는 없다.)

---

### Task 2: API 모듈

**Files:**
- Create: `src/api/admin/adminStageApi.ts`

`adminApplicationFormApi.ts`와 같은 구조를 따른다: 객체 리터럴 하나에 메서드를 모으고, 각 메서드에 한 줄 주석으로 가드를 적는다.

- [ ] **Step 1: 파일 작성**

`src/api/admin/adminStageApi.ts`:

```ts
import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type { PageResponse } from '@/types/page'
import type {
  AdminStageResult,
  StageListItem,
  StageResultBulkUpdateRequest,
  StageResultBulkUpdateResponse,
  StageResultInitializeResponse,
} from '@/types/admin/stage'

/** 전형결과 관리 화면 전용 API 모듈. 단계 조회·라이프사이클과 결과 조회·판정을 담당한다. */
export const adminStageApi = {
  /** 공고의 전형 단계 목록. stageOrder 오름차순이다. */
  getStages(jobPostingId: number) {
    return apiClient.get<ApiResponse<StageListItem[]>>(`/admin/job-postings/${jobPostingId}/stages`)
  },

  /** 단계의 전형 결과 전체. 비페이징이라 필터·페이징은 화면에서 처리한다. */
  getResults(stageId: number) {
    return apiClient.get<ApiResponse<AdminStageResult[]>>(`/admin/stages/${stageId}/results`)
  },

  /** 제출 완료 지원서를 대상자로 등록한다. 기존 행은 유지하고 신규만 추가(멱등). 단계 READY·IN_PROGRESS 에서만 가능. */
  initializeResults(stageId: number) {
    return apiClient.post<ApiResponse<StageResultInitializeResponse>>(
      `/admin/stages/${stageId}/results/initialize`,
    )
  },

  /** 결과 일괄 판정. 단계 IN_PROGRESS 에서만 가능하고 resultStatus 에 PENDING 을 보낼 수 없다. 동시 수정 시 409. */
  bulkUpdateResults(stageId: number, request: StageResultBulkUpdateRequest) {
    return apiClient.post<ApiResponse<StageResultBulkUpdateResponse>>(
      `/admin/stages/${stageId}/results/bulk`,
      request,
    )
  },

  /** 전형 시작(READY → IN_PROGRESS). 공고가 게시 중이어야 한다. */
  startStage(jobPostingId: number, stageId: number) {
    return apiClient.post<ApiResponse<number>>(
      `/admin/job-postings/${jobPostingId}/stages/${stageId}/start`,
    )
  },

  /** 결과 발표(IN_PROGRESS → RESULT_ANNOUNCED). 대기 결과가 하나라도 남아 있으면 백엔드가 거부한다. */
  announceStage(jobPostingId: number, stageId: number) {
    return apiClient.post<ApiResponse<number>>(
      `/admin/job-postings/${jobPostingId}/stages/${stageId}/announce`,
    )
  },

  /** 단계 마감(RESULT_ANNOUNCED → CLOSED). */
  closeStage(jobPostingId: number, stageId: number) {
    return apiClient.post<ApiResponse<number>>(
      `/admin/job-postings/${jobPostingId}/stages/${stageId}/close`,
    )
  },

  /** 빈 상태에서 "제출 완료 n건"을 보여주기 위한 건수 조회. totalElements 만 쓴다. */
  countSubmittedApplications(jobPostingId: number) {
    return apiClient.get<ApiResponse<PageResponse<unknown>>>('/admin/applications', {
      params: { jobPostingId, status: 'SUBMITTED', page: 0, size: 1 },
    })
  },
}
```

- [ ] **Step 2: 타입 체크**

```bash
npm run type-check
```

Expected: 통과.

---

### Task 3: 스텝퍼 컴포넌트

**Files:**
- Create: `src/views/admin/stageResult/StageStepper.vue`

설계 §4.2. 단계 칸: 번호(완료면 ✓) · 단계명 · 부제(상태 라벨 + 발표일). **부제에 대상 수는 넣지 않는다** — `StageListResponse`에 결과 건수가 없고, 결과 목록은 선택 단계 1개만 로드하기 때문이다(설계서 §11 첫 항목).

S2에서는 우측 ⚙ "단계 설정" 버튼을 렌더하지 않는다(S4).

- [ ] **Step 1: 컴포넌트 작성**

`src/views/admin/stageResult/StageStepper.vue`:

```vue
<script setup lang="ts">
import { STAGE_STATUS_LABELS, type StageListItem } from '@/types/admin/stage'
import { formatDate } from '@/common/dateUtil'

const props = defineProps<{
  stages: StageListItem[]
  selectedStageId: number | null
}>()

const emit = defineEmits<{
  (event: 'select', stageId: number): void
}>()

/** 부제: 상태 라벨 + 발표일(있을 때). 대상 수는 단계 목록 API 에 없어 넣지 않는다. */
const subtitleOf = (stage: StageListItem): string => {
  const label = STAGE_STATUS_LABELS[stage.status] ?? stage.status
  if (stage.resultAnnouncementDateTime === null) {
    return label
  }
  const date = formatDate(stage.resultAnnouncementDateTime, 'MM-DD')
  return stage.status === 'RESULT_ANNOUNCED' || stage.status === 'CLOSED'
    ? `${label} · ${date}`
    : `${label} · ${date} 발표`
}

const isDone = (stage: StageListItem) => stage.status === 'RESULT_ANNOUNCED' || stage.status === 'CLOSED'

const orderOf = (stage: StageListItem) => props.stages.indexOf(stage) + 1
</script>

<template>
  <nav class="stage-stepper" aria-label="전형 단계">
    <button
      v-for="stage in stages"
      :key="stage.id"
      type="button"
      class="step"
      :class="{ active: stage.id === selectedStageId, done: isDone(stage) }"
      :aria-current="stage.id === selectedStageId ? 'step' : undefined"
      @click="emit('select', stage.id)"
    >
      <span class="step-no">{{ isDone(stage) ? '✓' : orderOf(stage) }}</span>
      <span class="step-body">
        <span class="step-name">{{ stage.stageName }}</span>
        <span class="step-sub">{{ subtitleOf(stage) }}</span>
      </span>
    </button>
  </nav>
</template>

<style scoped lang="scss">
.stage-stepper {
  display: flex;
  overflow-x: auto;
  background: var(--app-bg-surface);
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius);
  margin-bottom: 12px;
}

.step {
  flex: 1 0 auto;
  min-width: 180px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: none;
  border-right: 1px solid var(--app-border-default);
  background: transparent;
  cursor: pointer;
  text-align: left;

  &:last-child {
    border-right: none;
  }

  &:hover {
    background: var(--app-bg-selected);
  }

  &.active {
    background: var(--app-bg-selected);
    box-shadow: inset 0 -3px 0 var(--app-color-primary);
  }
}

.step-no {
  flex: 0 0 auto;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border: 1.5px solid var(--app-border-strong);
  border-radius: 50%;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--app-text-muted);
}

.step.done .step-no {
  background: #e8f4ec;
  border-color: #a8d5b8;
  color: var(--app-color-success);
}

.step.active .step-no {
  background: var(--app-color-primary);
  border-color: var(--app-color-primary);
  color: #fff;
}

.step-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.step-name {
  font-size: 13px;
  font-weight: 650;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.step.active .step-name {
  color: var(--app-color-primary);
}

.step-sub {
  font-size: 11px;
  color: var(--app-text-muted);
  white-space: nowrap;
}
</style>
```

- [ ] **Step 2: 타입 체크**

```bash
npm run type-check
```

Expected: 통과.

---

### Task 4: 카운트 카드 컴포넌트

**Files:**
- Create: `src/views/admin/stageResult/StageResultCounts.vue`

설계 §4.3. 카드 6개(대상 전체 / 대기 / 합격 / 불합격 / 보류 / 결시). 카드 클릭은 결과 필터 토글이다. 철회는 카드로 두지 않고 필터 셀렉트에서만 고른다(대개 0건이라 카드 한 칸을 쓸 값어치가 없다).

- [ ] **Step 1: 컴포넌트 작성**

`src/views/admin/stageResult/StageResultCounts.vue`:

```vue
<script setup lang="ts">
import { computed } from 'vue'
import type { AdminStageResult, StageResultStatus } from '@/types/admin/stage'

const props = defineProps<{
  results: AdminStageResult[]
  /** 현재 적용된 결과 필터. null 이면 전체 */
  activeStatus: StageResultStatus | null
}>()

const emit = defineEmits<{
  (event: 'toggle', status: StageResultStatus | null): void
}>()

/** 카드로 노출할 상태. 철회는 건수가 드물어 필터 셀렉트로만 고른다. */
const CARD_STATUSES: { status: StageResultStatus; label: string; tone: string }[] = [
  { status: 'PENDING', label: '대기', tone: 'pending' },
  { status: 'PASSED', label: '합격', tone: 'passed' },
  { status: 'FAILED', label: '불합격', tone: 'failed' },
  { status: 'HOLD', label: '보류', tone: 'hold' },
  { status: 'ABSENT', label: '결시', tone: 'absent' },
]

const countOf = (status: StageResultStatus) =>
  props.results.filter((result) => result.resultStatus === status).length
</script>

<template>
  <div class="counts">
    <button
      type="button"
      class="count-card"
      :class="{ active: activeStatus === null }"
      @click="emit('toggle', null)"
    >
      <span class="count-label">대상 전체</span>
      <span class="count-value">{{ results.length }}</span>
    </button>
    <button
      v-for="card in CARD_STATUSES"
      :key="card.status"
      type="button"
      class="count-card"
      :class="[card.tone, { active: activeStatus === card.status }]"
      @click="emit('toggle', activeStatus === card.status ? null : card.status)"
    >
      <span class="count-label">{{ card.label }}</span>
      <span class="count-value">{{ countOf(card.status) }}</span>
    </button>
  </div>
</template>

<style scoped lang="scss">
.counts {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.count-card {
  flex: 1 1 88px;
  min-width: 88px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  background: var(--app-bg-surface);
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius);
  cursor: pointer;
  text-align: left;

  &:hover {
    border-color: var(--app-border-strong);
  }

  &.active {
    border-color: var(--app-color-primary);
    background: var(--app-bg-selected);
  }
}

.count-label {
  font-size: 11px;
  color: var(--app-text-muted);
}

.count-value {
  font-size: 18px;
  font-weight: 800;
  line-height: 1.2;
}

.pending .count-value {
  color: var(--app-color-warning);
}

.passed .count-value {
  color: var(--app-color-success);
}

.failed .count-value {
  color: var(--app-color-error);
}

.hold .count-value {
  color: var(--app-color-info);
}
</style>
```

- [ ] **Step 2: 타입 체크**

```bash
npm run type-check
```

Expected: 통과.

---

### Task 5: 그리드 컴포넌트

**Files:**
- Create: `src/views/admin/stageResult/StageResultGrid.vue`

설계 §4.4·§4.5. 필터 툴바 + 선택 일괄 버튼 + `a-table`. 편집은 IN_PROGRESS일 때만 활성이고, 변경 값은 본체가 넘겨준 `pendingEdits` 맵에서 읽어 표시한다. 이 컴포넌트는 상태를 소유하지 않고 조작을 emit만 한다.

편집 열 3개(결과·점수·코멘트)는 편집 가능 여부에 따라 입력 컨트롤과 읽기 전용 표시를 바꾼다.

- [ ] **Step 1: 컴포넌트 작성**

`src/views/admin/stageResult/StageResultGrid.vue`:

```vue
<script setup lang="ts">
import { computed, ref } from 'vue'
import type { TableColumnsType } from 'ant-design-vue'
import {
  APPLICATION_TYPE_LABELS,
  BULK_APPLY_STATUSES,
  DECIDABLE_RESULT_STATUSES,
  EDUCATION_LEVEL_LABELS,
  STAGE_RESULT_STATUS_COLORS,
  STAGE_RESULT_STATUS_LABELS,
  type AdminStageResult,
  type StageResultStatus,
} from '@/types/admin/stage'
import { formatDate } from '@/common/dateUtil'

/** 판정 편집 버퍼의 한 항목. 저장 전 값이며 원본과 같아지면 본체가 항목을 지운다. */
export interface PendingEdit {
  resultStatus: StageResultStatus
  score: number | null
  comment: string | null
}

const props = defineProps<{
  results: AdminStageResult[]
  /** stageResultId → 저장 전 변경값 */
  pendingEdits: Map<number, PendingEdit>
  /** IN_PROGRESS 단계에서만 true. false 면 그리드가 읽기 전용이다. */
  editable: boolean
  /** 카운트 카드에서 넘어온 결과 필터. null 이면 전체 */
  statusFilter: StageResultStatus | null
  saving: boolean
}>()

const emit = defineEmits<{
  (event: 'edit', stageResultId: number, patch: Partial<PendingEdit>): void
  (event: 'bulk-apply', stageResultIds: number[], status: StageResultStatus): void
  (event: 'open-application', applicationId: number): void
  (event: 'update:statusFilter', status: StageResultStatus | null): void
}>()

const jobPositionFilter = ref<number | undefined>()
const workLocationFilter = ref<string | undefined>()
const nameKeyword = ref('')
const selectedRowKeys = ref<number[]>([])

const jobPositionOptions = computed(() => {
  const options = new Map<number, string>()
  props.results.forEach((result) => options.set(result.jobPositionId, result.jobPositionName))
  return [...options].map(([value, label]) => ({ value, label }))
})

const workLocationOptions = computed(() => {
  const options = new Set<string>()
  props.results.forEach((result) => {
    if (result.workLocation !== null) {
      options.add(result.workLocation)
    }
  })
  return [...options].map((value) => ({ value, label: value }))
})

const statusFilterOptions = DECIDABLE_RESULT_STATUSES.concat('PENDING').map((status) => ({
  value: status,
  label: STAGE_RESULT_STATUS_LABELS[status],
}))

const resultStatusOptions = DECIDABLE_RESULT_STATUSES.map((status) => ({
  value: status,
  label: STAGE_RESULT_STATUS_LABELS[status],
}))

/** 화면에 실제로 그릴 행. 필터는 전부 클라이언트에서 건다(결과 목록이 비페이징이라). */
const filteredResults = computed(() =>
  props.results.filter((result) => {
    if (jobPositionFilter.value !== undefined && result.jobPositionId !== jobPositionFilter.value) {
      return false
    }
    if (workLocationFilter.value !== undefined && result.workLocation !== workLocationFilter.value) {
      return false
    }
    if (props.statusFilter !== null && effectiveStatus(result) !== props.statusFilter) {
      return false
    }
    const keyword = nameKeyword.value.trim()
    if (keyword.length > 0 && !result.applicantName.includes(keyword)) {
      return false
    }
    return true
  }),
)

/** 저장 전 변경이 있으면 그 값을, 없으면 원본을 본다. 필터·카운트가 화면과 어긋나지 않게 한다. */
const effectiveStatus = (result: AdminStageResult): StageResultStatus =>
  props.pendingEdits.get(result.stageResultId)?.resultStatus ?? result.resultStatus

const effectiveScore = (result: AdminStageResult): number | null => {
  const edit = props.pendingEdits.get(result.stageResultId)
  return edit ? edit.score : result.score
}

const effectiveComment = (result: AdminStageResult): string | null => {
  const edit = props.pendingEdits.get(result.stageResultId)
  return edit ? edit.comment : result.comment
}

const isDirty = (result: AdminStageResult) => props.pendingEdits.has(result.stageResultId)

const columns = computed<TableColumnsType>(() => {
  const base: TableColumnsType = [
    { title: '수험번호', key: 'applicationId', width: 100 },
    { title: '이름', key: 'applicantName', width: 110 },
    { title: '지원분야', key: 'jobPositionName', width: 140 },
    { title: '근무지', key: 'workLocation', width: 110 },
    { title: '지원구분', key: 'applicationType', width: 90 },
    { title: '최종학력', key: 'education', width: 180 },
    { title: '직전 단계', key: 'previousStageResultStatus', width: 90 },
    { title: '결과', key: 'resultStatus', width: 130 },
    { title: '점수', key: 'score', width: 100 },
    { title: '코멘트', key: 'comment', width: 200 },
    { title: '판정일시', key: 'decidedAt', width: 140 },
    { title: '판정자', key: 'decidedBy', width: 110 },
  ]
  return base
})

const rowSelection = computed(() =>
  props.editable
    ? {
        selectedRowKeys: selectedRowKeys.value,
        onChange: (keys: (string | number)[]) => {
          selectedRowKeys.value = keys.map(Number)
        },
      }
    : undefined,
)

const applyBulk = (status: StageResultStatus) => {
  emit('bulk-apply', [...selectedRowKeys.value], status)
  selectedRowKeys.value = []
}

const resetFilters = () => {
  jobPositionFilter.value = undefined
  workLocationFilter.value = undefined
  nameKeyword.value = ''
  emit('update:statusFilter', null)
}

/** 저장·재조회 후 본체가 호출해 선택을 비운다. */
const clearSelection = () => {
  selectedRowKeys.value = []
}

defineExpose({ clearSelection })
</script>

<template>
  <div class="result-grid">
    <div class="toolbar">
      <a-select
        v-model:value="jobPositionFilter"
        class="filter"
        placeholder="지원분야 전체"
        allow-clear
        :options="jobPositionOptions"
      />
      <a-select
        v-model:value="workLocationFilter"
        class="filter"
        placeholder="근무지 전체"
        allow-clear
        :options="workLocationOptions"
      />
      <a-select
        :value="statusFilter ?? undefined"
        class="filter"
        placeholder="결과 전체"
        allow-clear
        :options="statusFilterOptions"
        @change="(value: StageResultStatus | undefined) => emit('update:statusFilter', value ?? null)"
      />
      <a-input v-model:value="nameKeyword" class="filter" placeholder="이름" allow-clear />
      <a-button @click="resetFilters">초기화</a-button>
      <span class="toolbar-spacer" />
      <span class="row-count">{{ filteredResults.length }} / {{ results.length }}건</span>
    </div>

    <div v-if="editable" class="toolbar">
      <span class="bulk" :class="{ disabled: selectedRowKeys.length === 0 }">
        <b>선택 {{ selectedRowKeys.length }}건</b>
        <a-button
          v-for="status in BULK_APPLY_STATUSES"
          :key="status"
          size="small"
          :disabled="selectedRowKeys.length === 0 || saving"
          @click="applyBulk(status)"
        >
          {{ STAGE_RESULT_STATUS_LABELS[status] }}
        </a-button>
      </span>
    </div>

    <a-table
      :columns="columns"
      :data-source="filteredResults"
      :row-selection="rowSelection"
      :pagination="{ pageSize: 20, showSizeChanger: true, pageSizeOptions: ['10', '20', '50'] }"
      :row-class-name="(record: AdminStageResult) => (isDirty(record) ? 'dirty-row' : '')"
      row-key="stageResultId"
      size="small"
      :scroll="{ x: 1400 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'applicationId'">
          <a class="application-link" @click="emit('open-application', record.applicationId)">
            {{ record.applicationId }}
          </a>
        </template>

        <template v-else-if="column.key === 'applicantName'">
          {{ record.applicantName }}
          <a-tag v-if="record.applicationStatus === 'WITHDRAWN'" color="default">철회</a-tag>
        </template>

        <template v-else-if="column.key === 'workLocation'">
          {{ record.workLocation ?? '-' }}
        </template>

        <template v-else-if="column.key === 'applicationType'">
          {{ APPLICATION_TYPE_LABELS[record.applicationType] ?? record.applicationType }}
        </template>

        <template v-else-if="column.key === 'education'">
          <template v-if="record.finalEducationLevel">
            {{ EDUCATION_LEVEL_LABELS[record.finalEducationLevel] }}
            <span v-if="record.finalSchoolName" class="school">· {{ record.finalSchoolName }}</span>
          </template>
          <template v-else>-</template>
        </template>

        <template v-else-if="column.key === 'previousStageResultStatus'">
          <a-tag
            v-if="record.previousStageResultStatus"
            :color="STAGE_RESULT_STATUS_COLORS[record.previousStageResultStatus]"
          >
            {{ STAGE_RESULT_STATUS_LABELS[record.previousStageResultStatus] }}
          </a-tag>
          <template v-else>-</template>
        </template>

        <template v-else-if="column.key === 'resultStatus'">
          <a-select
            v-if="editable"
            :value="effectiveStatus(record)"
            size="small"
            style="width: 100%"
            :options="
              effectiveStatus(record) === 'PENDING'
                ? [{ value: 'PENDING', label: '대기' }, ...resultStatusOptions]
                : resultStatusOptions
            "
            @change="(value: StageResultStatus) => emit('edit', record.stageResultId, { resultStatus: value })"
          />
          <a-tag v-else :color="STAGE_RESULT_STATUS_COLORS[record.resultStatus]">
            {{ STAGE_RESULT_STATUS_LABELS[record.resultStatus] }}
          </a-tag>
        </template>

        <template v-else-if="column.key === 'score'">
          <a-input-number
            v-if="editable"
            :value="effectiveScore(record)"
            size="small"
            style="width: 100%"
            @change="(value: number | null) => emit('edit', record.stageResultId, { score: value })"
          />
          <template v-else>{{ record.score ?? '-' }}</template>
        </template>

        <template v-else-if="column.key === 'comment'">
          <a-input
            v-if="editable"
            :value="effectiveComment(record) ?? ''"
            size="small"
            :maxlength="2000"
            @change="
              (event: Event) =>
                emit('edit', record.stageResultId, {
                  comment: (event.target as HTMLInputElement).value.trim().length === 0
                    ? null
                    : (event.target as HTMLInputElement).value,
                })
            "
          />
          <template v-else>{{ record.comment ?? '-' }}</template>
        </template>

        <template v-else-if="column.key === 'decidedAt'">
          <span v-if="isDirty(record)" class="pending-mark">저장 전</span>
          <template v-else>{{ record.decidedAt ? formatDate(record.decidedAt, 'MM-DD HH:mm') : '-' }}</template>
        </template>

        <template v-else-if="column.key === 'decidedBy'">
          {{ record.decidedBy ?? '-' }}
        </template>
      </template>
    </a-table>
  </div>
</template>

<style scoped lang="scss">
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.toolbar-spacer {
  flex: 1;
}

.filter {
  width: 160px;
}

.row-count {
  font-size: 12px;
  color: var(--app-text-muted);
}

.bulk {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: var(--app-bg-selected);
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius-sm);
  font-size: 12px;

  &.disabled {
    background: var(--app-bg-muted);
    color: var(--app-text-muted);
  }

  b {
    color: var(--app-color-primary);
    margin-right: 4px;
  }
}

.application-link {
  color: var(--app-color-primary);
  font-weight: 600;
  text-decoration: underline;
  cursor: pointer;
}

.school {
  color: var(--app-text-secondary);
}

.pending-mark {
  color: var(--app-color-warning);
  font-weight: 600;
}

:deep(.dirty-row > td) {
  background: #fffdf3;
}

:deep(.dirty-row:hover > td) {
  background: #fff9e6;
}
</style>
```

- [ ] **Step 2: 타입 체크**

```bash
npm run type-check
```

Expected: 통과. 실패 시 흔한 원인은 (a) `a-table`의 `bodyCell` 슬롯 `record`가 `any`라 발생하는 암묵 any, (b) `rowSelection`의 `onChange` 시그니처. 둘 다 위 코드처럼 명시 타입을 붙여 해결한다.

---

### Task 6: 본체 뷰 — 로딩·선택·라우트 동기화

**Files:**
- Create: `src/views/admin/stageResult/AdminStageResultView.vue`
- Modify: `src/routes/adminRoutes.ts`

설계 §3.2. 이 Task에서는 **읽기 흐름만** 만든다(공고 → 단계 → 결과 로드, 스텝퍼·카운트·그리드 렌더). 판정 편집과 라이프사이클 명령은 Task 7·8이다.

- [ ] **Step 1: 라우트 추가**

`src/routes/adminRoutes.ts`의 `applications/:applicationId` 라우트 **아래에** 추가한다(관련 화면끼리 모아 둔다):

```ts
      {
        path: 'stage-results',
        name: 'AdminStageResult',
        component: () => import('@/views/admin/stageResult/AdminStageResultView.vue'),
      },
```

- [ ] **Step 2: 본체 뷰 작성 (읽기 흐름)**

`src/views/admin/stageResult/AdminStageResultView.vue`:

```vue
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { adminJobPostingApi } from '@/api/adminJobPostingApi'
import { adminStageApi } from '@/api/admin/adminStageApi'
import { getApiErrorMessage } from '@/api/apiError'
import StageStepper from './StageStepper.vue'
import StageResultCounts from './StageResultCounts.vue'
import StageResultGrid from './StageResultGrid.vue'
import type { AdminJobPostingListItem } from '@/types/admin/jobPosting'
import type { AdminStageResult, StageListItem, StageResultStatus } from '@/types/admin/stage'
import { formatDate } from '@/common/dateUtil'

const route = useRoute()
const router = useRouter()

const initializing = ref(true)
const loadingResults = ref(false)

const jobPostings = ref<AdminJobPostingListItem[]>([])
const selectedJobPostingId = ref<number | null>(null)
const stages = ref<StageListItem[]>([])
const selectedStageId = ref<number | null>(null)
const results = ref<AdminStageResult[]>([])
const statusFilter = ref<StageResultStatus | null>(null)
/** 빈 상태에서 "제출 완료 n건"을 보여주기 위한 값. 조회 실패 시 null 이면 문구에서 생략한다. */
const submittedCount = ref<number | null>(null)

const jobPostingOptions = computed(() =>
  jobPostings.value.map((posting) => ({ value: posting.id, label: posting.title })),
)

const selectedJobPosting = computed(
  () => jobPostings.value.find((posting) => posting.id === selectedJobPostingId.value) ?? null,
)

const selectedStage = computed(
  () => stages.value.find((stage) => stage.id === selectedStageId.value) ?? null,
)

/** 시작·발표·마감은 게시 중(PUBLISHED) 공고에서만 가능하다(백엔드 가드와 동일). */
const jobPostingPublished = computed(() => selectedJobPosting.value?.status === 'PUBLISHED')

const editable = computed(() => selectedStage.value?.status === 'IN_PROGRESS')

const pendingCount = computed(
  () => results.value.filter((result) => result.resultStatus === 'PENDING').length,
)

/** 기본 공고: 쿼리 → 접수 중 첫 공고 → 목록 첫 공고. 지원현황 조회 화면과 같은 규칙이다. */
const pickDefaultJobPosting = (postings: AdminJobPostingListItem[]): AdminJobPostingListItem | null => {
  const queryId = Number(route.query.jobPostingId)
  const fromQuery = postings.find((posting) => posting.id === queryId)
  return fromQuery ?? postings.find((posting) => posting.accepting) ?? postings[0] ?? null
}

/** 기본 단계: 쿼리 → 진행 중 → 첫 대기 → 마지막. */
const pickDefaultStage = (list: StageListItem[]): StageListItem | null => {
  const queryId = Number(route.query.stageId)
  const fromQuery = list.find((stage) => stage.id === queryId)
  return (
    fromQuery ??
    list.find((stage) => stage.status === 'IN_PROGRESS') ??
    list.find((stage) => stage.status === 'READY') ??
    list[list.length - 1] ??
    null
  )
}

/** 새로고침·링크 공유로 같은 위치를 복원할 수 있게 선택을 쿼리에 남긴다. */
const syncQuery = () => {
  void router.replace({
    query: {
      ...(selectedJobPostingId.value !== null ? { jobPostingId: String(selectedJobPostingId.value) } : {}),
      ...(selectedStageId.value !== null ? { stageId: String(selectedStageId.value) } : {}),
    },
  })
}

const loadResults = async () => {
  if (selectedStageId.value === null) {
    results.value = []
    return
  }
  loadingResults.value = true
  try {
    const response = await adminStageApi.getResults(selectedStageId.value)
    results.value = response.data.data
  } catch (error) {
    results.value = []
    message.error(getApiErrorMessage(error, '전형 결과를 불러오지 못했습니다.'))
  } finally {
    loadingResults.value = false
  }
}

const loadSubmittedCount = async () => {
  if (selectedJobPostingId.value === null) {
    submittedCount.value = null
    return
  }
  try {
    const response = await adminStageApi.countSubmittedApplications(selectedJobPostingId.value)
    submittedCount.value = response.data.data.totalElements
  } catch {
    // 빈 상태 안내 문구의 부가 정보라 실패해도 화면을 막지 않는다.
    submittedCount.value = null
  }
}

const selectStage = async (stageId: number) => {
  if (stageId === selectedStageId.value) {
    return
  }
  selectedStageId.value = stageId
  statusFilter.value = null
  syncQuery()
  await loadResults()
}

const loadStages = async () => {
  if (selectedJobPostingId.value === null) {
    stages.value = []
    selectedStageId.value = null
    return
  }
  try {
    const response = await adminStageApi.getStages(selectedJobPostingId.value)
    stages.value = response.data.data
    selectedStageId.value = pickDefaultStage(stages.value)?.id ?? null
  } catch (error) {
    stages.value = []
    selectedStageId.value = null
    message.error(getApiErrorMessage(error, '전형 단계를 불러오지 못했습니다.'))
  }
}

const changeJobPosting = async (jobPostingId: number) => {
  selectedJobPostingId.value = jobPostingId
  statusFilter.value = null
  await loadStages()
  syncQuery()
  await Promise.all([loadResults(), loadSubmittedCount()])
}

const openApplication = (applicationId: number) => {
  const resolved = router.resolve({ name: 'AdminApplication', params: { applicationId } })
  window.open(resolved.href, '_blank', 'noopener')
}

onMounted(async () => {
  try {
    const response = await adminJobPostingApi.getJobPostings()
    jobPostings.value = response.data.data.content
    const defaultPosting = pickDefaultJobPosting(jobPostings.value)
    if (defaultPosting) {
      await changeJobPosting(defaultPosting.id)
    }
  } catch (error) {
    message.error(getApiErrorMessage(error, '공고 목록을 불러오지 못했습니다.'))
  } finally {
    initializing.value = false
  }
})
</script>

<template>
  <div class="stage-result-view">
    <header class="page-header">
      <div>
        <h2 class="page-title">전형결과 관리</h2>
        <p class="page-description">공고를 선택하고 단계별로 결과를 판정·발표합니다.</p>
      </div>
      <a-select
        :value="selectedJobPostingId"
        class="posting-select"
        placeholder="공고를 선택하세요"
        :options="jobPostingOptions"
        :disabled="initializing || jobPostings.length === 0"
        show-search
        option-filter-prop="label"
        @change="changeJobPosting"
      />
    </header>

    <a-spin :spinning="initializing || loadingResults">
      <template v-if="stages.length > 0">
        <StageStepper :stages="stages" :selected-stage-id="selectedStageId" @select="selectStage" />

        <StageResultCounts
          v-if="results.length > 0"
          :results="results"
          :active-status="statusFilter"
          @toggle="(status) => (statusFilter = status)"
        />

        <StageResultGrid
          v-if="results.length > 0"
          ref="gridRef"
          :results="results"
          :pending-edits="pendingEdits"
          :editable="editable"
          :status-filter="statusFilter"
          :saving="false"
          @open-application="openApplication"
          @update:status-filter="(status) => (statusFilter = status)"
        />
      </template>
    </a-spin>
  </div>
</template>

<style scoped lang="scss">
.stage-result-view {
  padding: 4px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0 0 4px;
}

.page-description {
  margin: 0;
}

.posting-select {
  min-width: 320px;
}
</style>
```

이 시점에서는 `pendingEdits`·`gridRef`가 아직 없어 타입 오류가 난다. Task 7에서 채운다.

- [ ] **Step 3: 타입 체크로 미완성 지점 확인**

```bash
npm run type-check
```

Expected: FAIL — `pendingEdits`, `gridRef` 미정의. Task 7에서 해소된다. **이 상태로 Task 6을 끝내지 말고 Task 7까지 이어서 진행한다.**

---

### Task 7: 본체 뷰 — 판정 편집 버퍼와 저장

**Files:**
- Modify: `src/views/admin/stageResult/AdminStageResultView.vue`

설계 §3.3. 서버 원본 + 로컬 변경 맵. 저장은 `bulk` 1회. 409는 재조회 + 버퍼 유지.

- [ ] **Step 1: 편집 버퍼와 저장 로직 추가**

`<script setup>`의 import에 추가:

```ts
import { Modal } from 'ant-design-vue'
import type { PendingEdit } from './StageResultGrid.vue'
import { STAGE_RESULT_STATUS_LABELS, type StageResultBulkUpdateItem } from '@/types/admin/stage'
```

상태 선언부(`submittedCount` 아래)에 추가:

```ts
const gridRef = ref<InstanceType<typeof StageResultGrid> | null>(null)
const saving = ref(false)
/** stageResultId → 저장 전 변경값. 원본과 같아지면 항목을 지운다. */
const pendingEdits = ref(new Map<number, PendingEdit>())

const dirtyCount = computed(() => pendingEdits.value.size)
```

`openApplication` 위에 편집·저장 함수를 추가:

```ts
const findResult = (stageResultId: number) =>
  results.value.find((result) => result.stageResultId === stageResultId) ?? null

/** 원본과 같은 값이면 버퍼에서 빼서 "변경 없음"으로 되돌린다(저장 버튼 건수가 실제와 맞게). */
const sameAsOriginal = (result: AdminStageResult, edit: PendingEdit) =>
  edit.resultStatus === result.resultStatus &&
  edit.score === result.score &&
  edit.comment === result.comment

const applyEdit = (stageResultId: number, patch: Partial<PendingEdit>) => {
  const result = findResult(stageResultId)
  if (result === null) {
    return
  }
  const current: PendingEdit = pendingEdits.value.get(stageResultId) ?? {
    resultStatus: result.resultStatus,
    score: result.score,
    comment: result.comment,
  }
  const next: PendingEdit = { ...current, ...patch }
  const map = new Map(pendingEdits.value)
  if (sameAsOriginal(result, next)) {
    map.delete(stageResultId)
  } else {
    map.set(stageResultId, next)
  }
  pendingEdits.value = map
}

const applyBulk = (stageResultIds: number[], status: StageResultStatus) => {
  stageResultIds.forEach((stageResultId) => applyEdit(stageResultId, { resultStatus: status }))
}

const discardEdits = () => {
  pendingEdits.value = new Map()
  gridRef.value?.clearSelection()
}

/*
 * 저장 대상은 대기(PENDING)가 아닌 행만이다. 원본이 대기인 행을 셀렉트에서 대기로 되돌리면
 * 버퍼에서 빠지므로 여기에 남을 일은 없지만, 백엔드가 PENDING 을 거부하므로 한 번 더 막는다.
 */
const buildBulkItems = (): StageResultBulkUpdateItem[] =>
  [...pendingEdits.value.entries()]
    .filter(([, edit]) => edit.resultStatus !== 'PENDING')
    .map(([stageResultId, edit]) => ({
      stageResultId,
      resultStatus: edit.resultStatus,
      score: edit.score,
      comment: edit.comment,
    }))

const saveEdits = async () => {
  if (selectedStageId.value === null || pendingEdits.value.size === 0) {
    return
  }
  const items = buildBulkItems()
  if (items.length === 0) {
    message.warning('저장할 판정이 없습니다. 결과를 대기가 아닌 값으로 지정하세요.')
    return
  }
  saving.value = true
  try {
    const response = await adminStageApi.bulkUpdateResults(selectedStageId.value, { results: items })
    results.value = response.data.data.results
    pendingEdits.value = new Map()
    gridRef.value?.clearSelection()
    message.success(`${response.data.data.updatedCount}건을 저장했습니다.`)
  } catch (error) {
    if (isConflict(error)) {
      // 다른 관리자가 먼저 저장했다. 최신 목록을 보여주되 입력값은 살려 재검토하게 한다.
      await loadResults()
      message.warning('다른 관리자가 먼저 수정했습니다. 최신 목록을 불러왔으니 변경 내용을 다시 확인해주세요.')
    } else {
      message.error(getApiErrorMessage(error, '판정 저장에 실패했습니다.'))
    }
  } finally {
    saving.value = false
  }
}
```

409 판별 헬퍼를 파일 상단(import 아래)에 추가:

```ts
import axios from 'axios'

/** 낙관적 잠금 충돌. 백엔드가 동시 수정에 409 를 준다. */
const isConflict = (error: unknown) => axios.isAxiosError(error) && error.response?.status === 409
```

- [ ] **Step 2: 단계·공고 전환 시 미저장 변경 확인**

`selectStage`와 `changeJobPosting`이 버퍼를 버리기 전에 확인을 받도록 바꾼다. `selectStage` 앞에 헬퍼를 둔다:

```ts
/** 미저장 변경이 있으면 확인을 받는다. 확인하면 버퍼를 비우고 true 를 반환한다. */
const confirmDiscardIfDirty = (): Promise<boolean> => {
  if (pendingEdits.value.size === 0) {
    return Promise.resolve(true)
  }
  return new Promise((resolve) => {
    Modal.confirm({
      title: '저장하지 않은 판정이 있습니다',
      content: `변경 ${pendingEdits.value.size}건이 사라집니다. 계속할까요?`,
      okText: '변경 버리고 이동',
      cancelText: '취소',
      onOk: () => {
        discardEdits()
        resolve(true)
      },
      onCancel: () => resolve(false),
    })
  })
}
```

`selectStage` 본문 첫 줄을 교체:

```ts
const selectStage = async (stageId: number) => {
  if (stageId === selectedStageId.value) {
    return
  }
  if (!(await confirmDiscardIfDirty())) {
    return
  }
  selectedStageId.value = stageId
  statusFilter.value = null
  syncQuery()
  await loadResults()
}
```

`changeJobPosting` 본문 첫 줄에도 같은 가드를 넣는다:

```ts
const changeJobPosting = async (jobPostingId: number) => {
  if (!(await confirmDiscardIfDirty())) {
    return
  }
  selectedJobPostingId.value = jobPostingId
  statusFilter.value = null
  await loadStages()
  syncQuery()
  await Promise.all([loadResults(), loadSubmittedCount()])
}
```

공고 셀렉트는 `:value` 바인딩이라 취소해도 표시가 되돌아온다(양방향 바인딩이 아니므로 별도 롤백이 필요 없다).

- [ ] **Step 3: 라우트 이탈·브라우저 닫기 가드**

import에 추가:

```ts
import { onBeforeUnmount } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
```

`onMounted` 위에 추가:

```ts
onBeforeRouteLeave(async () => {
  return await confirmDiscardIfDirty()
})

/** 브라우저 새로고침·닫기. 브라우저가 문구를 무시하고 기본 경고를 띄운다. */
const warnUnsavedOnUnload = (event: BeforeUnloadEvent) => {
  if (pendingEdits.value.size > 0) {
    event.preventDefault()
    event.returnValue = ''
  }
}

window.addEventListener('beforeunload', warnUnsavedOnUnload)
onBeforeUnmount(() => window.removeEventListener('beforeunload', warnUnsavedOnUnload))
```

- [ ] **Step 4: 템플릿에 저장 툴바와 그리드 바인딩 연결**

`StageResultCounts` 아래, `StageResultGrid` 위에 저장 툴바를 넣는다:

```vue
        <div v-if="editable && results.length > 0" class="save-bar">
          <span class="dirty-count">저장 전 변경 {{ dirtyCount }}건</span>
          <span class="save-bar-spacer" />
          <a-button :disabled="dirtyCount === 0 || saving" @click="discardEdits">변경 취소</a-button>
          <a-button type="primary" :loading="saving" :disabled="dirtyCount === 0" @click="saveEdits">
            변경사항 저장 ({{ dirtyCount }})
          </a-button>
        </div>
```

`StageResultGrid` 태그의 `:saving` 과 이벤트를 실제 값으로 교체:

```vue
        <StageResultGrid
          v-if="results.length > 0"
          ref="gridRef"
          :results="results"
          :pending-edits="pendingEdits"
          :editable="editable"
          :status-filter="statusFilter"
          :saving="saving"
          @edit="applyEdit"
          @bulk-apply="applyBulk"
          @open-application="openApplication"
          @update:status-filter="(status) => (statusFilter = status)"
        />
```

스타일에 추가:

```scss
.save-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.save-bar-spacer {
  flex: 1;
}

.dirty-count {
  font-size: 12px;
  color: var(--app-color-warning);
  font-weight: 600;
}
```

- [ ] **Step 5: 타입 체크**

```bash
npm run type-check
```

Expected: 통과.

- [ ] **Step 6: 브라우저 확인**

`.claude/launch.json`이 없으면 아래 내용으로 만들고 preview를 연다.

```json
{
  "version": "0.0.1",
  "configurations": [
    { "name": "recruit-front", "runtimeExecutable": "npm", "runtimeArgs": ["run", "dev"], "port": 5173 }
  ]
}
```

`/admin/stage-results`로 이동해 확인한다. 백엔드가 떠 있지 않으면 목록이 비므로, 확인이 불가능하면 그 사실을 보고한다.

확인 항목:
- 공고 셀렉트 변경 시 단계·결과가 다시 로드되고 주소창 쿼리가 바뀐다
- 결과 셀렉트를 바꾸면 행 배경이 노랗게 변하고 "저장 전 변경 n건"이 증가한다
- 원래 값으로 되돌리면 건수가 줄어든다
- 미저장 상태에서 단계를 바꾸면 확인 모달이 뜬다

---

### Task 8: 본체 뷰 — 상태 배너와 라이프사이클 명령

**Files:**
- Modify: `src/views/admin/stageResult/AdminStageResultView.vue`

설계 §3.1·§3.4·§4.3·§4.9. 단계 상태가 배너·버튼·빈 상태를 결정한다.

- [ ] **Step 1: 배너 메타 계산**

`editable` 아래에 추가:

```ts
type BannerType = 'info' | 'warning' | 'success' | 'error'

/** 단계 상태 → 배너. 색과 문구가 백엔드 가드와 같은 이야기를 하게 한다. */
const banner = computed<{ type: BannerType; message: string; description?: string } | null>(() => {
  const stage = selectedStage.value
  if (stage === null) {
    return null
  }
  const announceAt = stage.resultAnnouncementDateTime
    ? formatDate(stage.resultAnnouncementDateTime, 'YYYY-MM-DD HH:mm')
    : null
  switch (stage.status) {
    case 'READY':
      return results.value.length === 0
        ? { type: 'info', message: `${stage.stageName} 준비 중`, description: '대상자를 불러오면 전형을 시작할 수 있습니다.' }
        : {
            type: 'info',
            message: `${stage.stageName} 준비 완료 · 대상 ${results.value.length}명`,
            description: '전형을 시작하면 결과를 판정할 수 있습니다.',
          }
    case 'IN_PROGRESS':
      return {
        type: 'warning',
        message: `${stage.stageName} 진행 중${announceAt ? ` · 발표 예정 ${announceAt}` : ''}`,
        description:
          pendingCount.value > 0
            ? `대기 ${pendingCount.value}건이 남아 결과를 발표할 수 없습니다.`
            : '모든 결과가 판정되었습니다. 결과를 발표할 수 있습니다.',
      }
    case 'RESULT_ANNOUNCED':
      return {
        type: 'success',
        message: `${stage.stageName} 발표 완료${announceAt ? ` · ${announceAt}` : ''}`,
        description: '결과가 잠겨 있습니다. 수정하려면 정정 기능을 사용하세요.',
      }
    case 'CLOSED':
      return { type: 'info', message: `${stage.stageName} 마감`, description: '더 이상 변경할 수 없습니다.' }
    default:
      return null
  }
})

/** 게시 전 공고에서 시작·발표·마감이 왜 막혔는지 알려준다. */
const lifecycleDisabledReason = computed(() =>
  jobPostingPublished.value ? '' : '공고를 게시한 뒤에 사용할 수 있습니다.',
)
```

- [ ] **Step 2: 라이프사이클 명령 추가**

`openApplication` 위에 추가:

```ts
const commandRunning = ref(false)

/** 단계 목록과 결과를 함께 다시 읽는다. 상태 전이 후 배너·버튼이 어긋나지 않게 한다. */
const reloadStageAndResults = async () => {
  const keepStageId = selectedStageId.value
  await loadStages()
  if (keepStageId !== null && stages.value.some((stage) => stage.id === keepStageId)) {
    selectedStageId.value = keepStageId
  }
  await loadResults()
}

const runCommand = async (
  action: () => Promise<unknown>,
  successMessage: string,
  failMessage: string,
) => {
  commandRunning.value = true
  try {
    await action()
    await reloadStageAndResults()
    message.success(successMessage)
  } catch (error) {
    message.error(getApiErrorMessage(error, failMessage))
  } finally {
    commandRunning.value = false
  }
}

const initializeResults = () => {
  if (selectedStageId.value === null) {
    return
  }
  const stageId = selectedStageId.value
  Modal.confirm({
    title: '대상자를 불러올까요?',
    content:
      '제출 완료 지원서를 이 단계의 대상자로 등록합니다. 이미 등록된 대상자는 그대로 두고 새로 제출된 지원서만 추가합니다.',
    okText: '불러오기',
    cancelText: '취소',
    onOk: async () => {
      commandRunning.value = true
      try {
        const response = await adminStageApi.initializeResults(stageId)
        const { createdCount, existingCount, skippedCount } = response.data.data
        await reloadStageAndResults()
        message.success(`신규 ${createdCount}건 · 기존 ${existingCount}건 · 제외 ${skippedCount}건`)
      } catch (error) {
        message.error(getApiErrorMessage(error, '대상자를 불러오지 못했습니다.'))
      } finally {
        commandRunning.value = false
      }
    },
  })
}

const startStage = () => {
  if (selectedJobPostingId.value === null || selectedStageId.value === null) {
    return
  }
  const jobPostingId = selectedJobPostingId.value
  const stageId = selectedStageId.value
  Modal.confirm({
    title: '전형을 시작할까요?',
    content: '시작하면 결과를 판정할 수 있고, 단계 이름·유형·순서는 더 이상 바꿀 수 없습니다.',
    okText: '시작',
    cancelText: '취소',
    onOk: () =>
      runCommand(
        () => adminStageApi.startStage(jobPostingId, stageId),
        '전형을 시작했습니다.',
        '전형을 시작하지 못했습니다.',
      ),
  })
}

const announceStage = async () => {
  if (selectedJobPostingId.value === null || selectedStageId.value === null) {
    return
  }
  if (!(await confirmDiscardIfDirty())) {
    return
  }
  const jobPostingId = selectedJobPostingId.value
  const stageId = selectedStageId.value
  const summary = ['PASSED', 'FAILED', 'HOLD', 'ABSENT', 'WITHDRAWN']
    .map((status) => ({
      label: STAGE_RESULT_STATUS_LABELS[status as StageResultStatus],
      count: results.value.filter((result) => result.resultStatus === status).length,
    }))
    .filter((entry) => entry.count > 0)
    .map((entry) => `${entry.label} ${entry.count}`)
    .join(' · ')
  Modal.confirm({
    title: '결과를 발표할까요?',
    content: `대상 ${results.value.length}명 (${summary}). 발표하면 지원자에게 결과가 공개되고, 이후 변경은 사유를 남기는 정정으로만 가능합니다.`,
    okText: '발표',
    cancelText: '취소',
    onOk: () =>
      runCommand(
        () => adminStageApi.announceStage(jobPostingId, stageId),
        '결과를 발표했습니다.',
        '결과를 발표하지 못했습니다.',
      ),
  })
}

const closeStage = () => {
  if (selectedJobPostingId.value === null || selectedStageId.value === null) {
    return
  }
  const jobPostingId = selectedJobPostingId.value
  const stageId = selectedStageId.value
  Modal.confirm({
    title: '단계를 마감할까요?',
    content: '마감하면 이 단계의 상태를 더 이상 되돌릴 수 없습니다.',
    okText: '마감',
    cancelText: '취소',
    onOk: () =>
      runCommand(
        () => adminStageApi.closeStage(jobPostingId, stageId),
        '단계를 마감했습니다.',
        '단계를 마감하지 못했습니다.',
      ),
  })
}
```

`announceStage`가 `STAGE_RESULT_STATUS_LABELS`를 쓰므로 Task 7에서 넣은 import에 이미 포함되어 있다.

- [ ] **Step 3: 템플릿에 배너·명령 버튼·빈 상태 배치**

`StageStepper` 아래, `StageResultCounts` 위에 배너를 넣는다:

```vue
        <a-alert
          v-if="banner"
          class="stage-banner"
          :type="banner.type"
          :message="banner.message"
          :description="banner.description"
          show-icon
        >
          <template #action>
            <a-space>
              <a-tooltip :title="lifecycleDisabledReason">
                <a-button
                  v-if="selectedStage?.status === 'READY' || selectedStage?.status === 'IN_PROGRESS'"
                  size="small"
                  :loading="commandRunning"
                  @click="initializeResults"
                >
                  {{ results.length === 0 ? '대상자 불러오기' : '대상자 다시 불러오기' }}
                </a-button>
              </a-tooltip>
              <a-tooltip :title="lifecycleDisabledReason">
                <a-button
                  v-if="selectedStage?.status === 'READY'"
                  type="primary"
                  size="small"
                  :disabled="!jobPostingPublished || results.length === 0"
                  :loading="commandRunning"
                  @click="startStage"
                >
                  전형 시작
                </a-button>
              </a-tooltip>
              <a-tooltip
                :title="
                  !jobPostingPublished
                    ? lifecycleDisabledReason
                    : pendingCount > 0
                      ? `대기 ${pendingCount}건을 모두 판정해야 발표할 수 있습니다.`
                      : ''
                "
              >
                <a-button
                  v-if="selectedStage?.status === 'IN_PROGRESS'"
                  type="primary"
                  size="small"
                  :disabled="!jobPostingPublished || pendingCount > 0"
                  :loading="commandRunning"
                  @click="announceStage"
                >
                  결과 발표
                </a-button>
              </a-tooltip>
              <a-tooltip :title="lifecycleDisabledReason">
                <a-button
                  v-if="selectedStage?.status === 'RESULT_ANNOUNCED'"
                  size="small"
                  :disabled="!jobPostingPublished"
                  :loading="commandRunning"
                  @click="closeStage"
                >
                  단계 마감
                </a-button>
              </a-tooltip>
            </a-space>
          </template>
        </a-alert>
```

`stages.length > 0` 블록 뒤에 빈 상태 두 개를 추가한다(`</template>` 다음, `</a-spin>` 앞):

```vue
      <a-empty
        v-else-if="!initializing && selectedJobPostingId !== null"
        class="empty-state"
        description="이 공고에는 전형 단계가 아직 없습니다."
      >
        <p class="empty-hint">
          서류전형 → 면접 순서로 단계를 만들면 지원자 결과를 판정할 수 있습니다. 단계 설정 화면은 준비 중입니다.
        </p>
      </a-empty>
```

`stages.length > 0` 블록 **안에서** 결과가 0건일 때의 빈 상태를 `StageResultCounts` 자리에 넣는다:

```vue
        <a-empty
          v-if="results.length === 0 && !loadingResults"
          class="empty-state"
          :description="
            submittedCount !== null
              ? `대상자를 아직 불러오지 않았습니다. 제출 완료 지원서 ${submittedCount}건.`
              : '대상자를 아직 불러오지 않았습니다.'
          "
        />
```

스타일에 추가:

```scss
.stage-banner {
  margin-bottom: 12px;
}

.empty-state {
  padding: 40px 0;
}

.empty-hint {
  color: var(--app-text-secondary);
  font-size: 13px;
}
```

- [ ] **Step 4: 타입 체크**

```bash
npm run type-check
```

Expected: 통과. `a-alert`의 `type`이 유니온이라 `BannerType`을 그 값으로 맞춰 두었다.

- [ ] **Step 5: 브라우저 확인**

`/admin/stage-results`에서 상태별 화면을 확인한다. 백엔드가 필요하며, 확인 불가면 그 사실을 보고한다.

확인 항목:
- READY 단계: "대상자 불러오기" 버튼, 결과 0건이면 빈 상태 문구에 제출 건수가 뜬다
- 초기화 후: 대상 수가 배너에 뜨고 "전형 시작"이 활성된다
- IN_PROGRESS: 그리드가 편집 가능해지고, 대기가 남아 있으면 "결과 발표"가 비활성 + 툴팁이 이유를 말한다
- RESULT_ANNOUNCED: 그리드가 읽기 전용 배지로 바뀌고 "단계 마감"이 뜬다
- 공고가 게시 전(DRAFT)이면 시작·발표·마감이 비활성이고 툴팁이 이유를 말한다

---

### Task 9: 메뉴 등록 안내와 문서 갱신

**Files:**
- Modify: `docs/superpowers/specs/2026-09-04-admin-stage-result-management-design.md`

S2는 계약을 바꾸지 않으므로 `api-contract.md`는 손대지 않는다(🟢 확정은 S4).

- [ ] **Step 1: 설계서 슬라이스 표 갱신**

§8 슬라이스 표의 S2 행을 완료 표시로 바꾸고, 실제 만든 파일과 S2에서 내린 결정을 적는다.

```markdown
| ✅ S2 프론트 본체 (2026-09-04 완료) | 라우트·타입·API·뷰·스텝퍼·카운트·그리드·판정 저장·초기화/시작/발표/마감·빈 상태 | `type-check` 통과 |
```

- [ ] **Step 2: S2 결정 사항 기록**

§11 아래에 절을 추가한다.

```markdown
## 12. S2에서 내린 결정

- **스텝퍼 부제에서 대상 수를 뺐다.** 단계 목록 API에 결과 건수가 없고 결과는 선택 단계만 로드하므로, 부제는 "상태 · 발표일"로 한정했다. 대상 수는 선택 단계의 배너에만 나온다.
- **카운트 카드에서 철회를 뺐다.** 대개 0건이라 카드 한 칸을 쓸 값어치가 없다. 결과 필터 셀렉트로는 고를 수 있다.
- **수험번호 링크는 새 탭으로 연다.** 판정 중 목록을 잃지 않게 한다. 미저장 변경이 있어도 이탈 확인이 뜨지 않는다.
- **엑셀·정정·단계 설정 버튼은 렌더하지 않는다.** 눌러서 아무 일도 안 일어나는 버튼을 두지 않는다. S3·S4에서 같은 자리에 붙인다.
- **단계 0건 공고의 빈 상태는 임시 문구다.** "단계 설정 화면은 준비 중입니다."로 두고, S4에서 드로어를 여는 버튼으로 바꾼다.
```

- [ ] **Step 3: 메뉴 등록 필요 안내**

§6의 메뉴 관련 줄이 이미 있다. 배포 전 관리자 메뉴 관리 화면에서 "전형결과 관리"(`/admin/stage-results`)를 등록해야 사이드바에 뜬다는 사실을 사용자에게 보고에 포함한다(코드 변경 아님).

---

## 자체 검토 결과

- 설계서 §3.1 → Task 8(상태별 배너·버튼), §3.2 → Task 6(로딩 순서·기본 선택·쿼리 동기화), §3.3 → Task 7(편집 버퍼·저장·409·이탈 가드), §3.4 → Task 8(초기화·시작·발표·마감), §4.1~§4.5 → Task 3~7, §4.9 → Task 8(빈 상태 2종). §4.6~§4.8(정정 모달·업로드 모달·설정 드로어)은 S3·S4다.
- 설계서 §11의 7개 주의사항 반영: 대상 수 제외(Task 3), 제출 건수 조회(Task 2·6), 라벨 맵 동기화 주석(Task 1), `previousStageResultStatus` 의미(Task 1 주석), export 미포함(S3 소관), 잠긴 필드 전송(S4 소관), SXSSF 함정(백엔드 테스트 소관).
- Task 간 타입 일치: `PendingEdit`는 Task 5가 export하고 Task 7이 import한다. `StageResultGrid`의 `clearSelection`은 `defineExpose`로 노출하고 Task 7이 `gridRef`로 호출한다. `AdminStageResult`·`StageListItem`·`StageResultStatus`는 Task 1이 정의하고 Task 3~8이 쓴다.
- 순서 의존: Task 1 → 2 → (3, 4, 5 병렬 가능) → 6 → 7 → 8 → 9. Task 6은 단독으로 타입 체크를 통과하지 않으므로 Task 7과 이어서 진행한다.
