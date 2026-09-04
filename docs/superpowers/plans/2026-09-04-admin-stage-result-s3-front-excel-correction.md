# 전형결과 관리 S3 (프론트 엑셀·정정) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 전형결과 관리 화면에 엑셀 왕복(템플릿 다운로드 → 편집 → 업로드 미리보기 → 적용)과 발표 후 정정(사유 필수 + 이력)을 붙인다. S2에서 자리만 비워 둔 버튼들이 실제로 동작하게 된다.

**Architecture:** 백엔드 변경 없음. S2가 만든 본체 뷰가 이미 761줄이라, 먼저 라이프사이클 로직을 컴포저블로 빼서 자리를 만든 뒤(Task 1) 엑셀·정정을 얹는다. 모달 둘은 자체 상태를 갖는 독립 컴포넌트로 두고, 본체는 열기·닫기·성공 후 재조회만 담당한다.

**Tech Stack:** Vue 3 `<script setup lang="ts">`, TypeScript, ant-design-vue 4(`a-modal`, `a-upload`, `a-table`), Axios(multipart, blob).

**설계서:** `docs/superpowers/specs/2026-09-04-admin-stage-result-management-design.md` (§3.4 정정, §3.5 엑셀 왕복, §4.6 정정 모달, §4.7 업로드 미리보기, §11·§12)
**계약:** `api-contract.md`의 "전형결과 관리" 섹션
**시안:** `docs/design/전형결과 관리 관리자 시안.html` — "엑셀 업로드 미리보기" 탭, "상태별 화면" 탭의 정정 모달

**작업 루트:** 모든 경로는 `recruit_front/` 기준. 검증:

```bash
npm run type-check
```

**커밋:** 프로젝트 규칙(`recruit/CLAUDE.md` §6)상 사용자가 명시 요청할 때만 커밋한다. 각 Task는 `type-check` 통과로 끝내고 커밋 단계를 두지 않는다.

**⚠ 다른 세션과의 충돌 주의:** `AdminJobPostingListItem` 타입 중복을 정리하는 작업이 별도 세션에서 돌고 있다. 그 작업은 `src/types/jobPosting.ts`·`src/types/admin/jobPosting.ts`와 그 사용처를 건드리며, `AdminStageResultView.vue`의 import 한 줄도 대상이다. **이 계획의 어느 Task도 그 import 줄을 수정하지 않는다.** 만약 작업 중 그 줄이 바뀌어 있으면 그대로 두고 진행한다. type-check가 그 줄 때문에 실패하면 고치지 말고 보고한다.

---

## 백엔드 계약 (S1 확정 — 이 계획이 의존하는 전부)

| 메서드 | 경로 | 요청 | 응답 |
| --- | --- | --- | --- |
| GET | `/admin/stages/{stageId}/results/upload-template` | — | xlsx 스트림 (`Content-Disposition` 헤더에 파일명) |
| POST | `/admin/stages/{stageId}/results/upload/preview` | multipart `file` | `ApiResponse<StageResultUploadPreviewResponse>` |
| POST | `/admin/stages/{stageId}/results/upload/commit` | multipart `file` | 아래 참조 |
| GET | `/admin/stages/{stageId}/results/export` | — | xlsx 스트림 (읽기 전용 목록, 업로드 소스 아님) |
| POST | `/admin/stages/{stageId}/results/{resultId}/correct` | `{ resultStatus, score, comment, reason }` | `ApiResponse<AdminStageResultResponse>` |
| GET | `/admin/stages/{stageId}/results/{resultId}/histories` | — | `ApiResponse<StageResultCorrectionHistoryResponse[]>` (최신순) |

**preview 응답:** `{ stageId, totalRows, changedCount, unchangedCount, errorCount, committable, rows[] }`
**commit 응답:** `{ stageId, outcome, totalRows, changedCount, unchangedCount, errorCount, staleCount, failedRows[] }`
**행:** `{ rowNumber, stageResultId, applicationId, applicantName, status, errors[], diff }`
**diff:** `{ oldResultStatus, newResultStatus, oldScore, newScore, oldComment, newComment }` — **모두 문자열**이고 `resultStatus`는 **enum 이름**(`PASSED` 등)이다. 화면에 그릴 때 한글 라벨로 바꿔야 한다.
**행 상태:** `CHANGED | UNCHANGED | ERROR | STALE`

**commit의 HTTP 상태와 본문이 outcome마다 다르다 (핵심):**

| outcome | HTTP | 본문 |
| --- | --- | --- |
| `APPLIED` | 200 | `ApiResponse.success(response)` — `data`에 commit 응답 |
| `REJECTED_VALIDATION` | **400** | `ApiResponse.fail(message, response)` — `data`에 commit 응답이 **들어 있다** |
| `REJECTED_STALE` | **409** | 위와 같음 |

즉 400·409여도 axios가 던지는 에러의 `error.response.data.data`에 `failedRows`가 담겨 있다. 이걸 꺼내 표에 그려야 한다. **파일 자체가 거부되는 경우(구 영문 헤더, 확장자, 크기 초과)는 400이지만 `data`가 없다.** 두 경우를 갈라 처리해야 한다(설계서 §11).

낙관적 잠금(`@Version`) 충돌은 outcome이 아니라 예외로 터져 **409 + `data` 없음**이다.

**정정 가드:** 단계가 `RESULT_ANNOUNCED` 또는 `CLOSED`일 때만. `reason` 필수(1000자), `comment` 2000자, `resultStatus`에 `PENDING` 불가.

---

## 파일 구조

| 파일 | 역할 | 변경 |
| --- | --- | --- |
| `src/types/admin/stage.ts` | 업로드·정정 타입 추가 | 수정 |
| `src/api/admin/adminStageApi.ts` | 엔드포인트 6개 추가 | 수정 |
| `src/common/fileDownload.ts` | blob 다운로드 공통 함수 | 신규 |
| `src/views/admin/stageResult/useStageLifecycle.ts` | 라이프사이클 명령 컴포저블 | 신규 |
| `src/views/admin/stageResult/StageUploadPreviewModal.vue` | 업로드 미리보기·적용 | 신규 |
| `src/views/admin/stageResult/StageResultCorrectModal.vue` | 정정 + 이력 | 신규 |
| `src/views/admin/stageResult/StageResultGrid.vue` | 정정 버튼 열 추가 | 수정 |
| `src/views/admin/stageResult/AdminStageResultView.vue` | 컴포저블 사용, 엑셀·정정 배선 | 수정 |

책임 경계: 모달 둘은 자체 상태(파일, 미리보기 결과, 폼 입력, 이력)를 갖고 API도 직접 호출한다. 본체는 `open`/`close`와 성공 이벤트만 다룬다. 저장 버퍼처럼 본체가 소유해야 할 상태가 없기 때문이다.

---

### Task 1: 라이프사이클 컴포저블 추출 (선행 정리)

**Files:**
- Create: `src/views/admin/stageResult/useStageLifecycle.ts`
- Modify: `src/views/admin/stageResult/AdminStageResultView.vue`

S2 통합 리뷰가 "S3 착수 직전에 빼라"고 권고한 정리다. **동작을 하나도 바꾸지 않는다.** 순수 이동이라 이 Task의 성공 기준은 "type-check 통과 + 화면 동작 동일"이다.

옮길 대상: `commandRunning`, `reloadStageAndResults`, `runCommand`, `confirmThenRun`, `ConfirmThenRunOptions`, `initializeResults`, `startStage`, `announceStage`, `closeStage`. (본체 기준 약 392~527행)

남길 대상: 로딩 함수(`loadStages`/`loadResults`/`loadSubmittedCount`), 편집 버퍼 일체, `confirmDiscardIfDirty`, 배너·차단 사유 computed, 라우트 동기화.

- [ ] **Step 1: 컴포저블 파일 작성**

`src/views/admin/stageResult/useStageLifecycle.ts`:

```ts
import { ref, type Ref } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { adminStageApi } from '@/api/admin/adminStageApi'
import { getApiErrorMessage } from '@/api/apiError'
import { DECIDABLE_RESULT_STATUSES, STAGE_RESULT_STATUS_LABELS } from '@/types/admin/stage'
import type { AdminStageResult, StageListItem } from '@/types/admin/stage'

/**
 * 전형 단계 라이프사이클 명령(대상자 초기화 · 전형 시작 · 결과 발표 · 단계 마감).
 *
 * <p>본체 뷰가 소유한 선택 상태와 로딩 함수를 주입받아, 확인 모달 → API 호출 → 재조회까지를 담당한다.
 * 본체에서 분리한 이유는 S3 에서 엑셀·정정이 얹히면 한 파일이 900줄을 넘기 때문이다(S2 통합 리뷰 권고).
 */
export interface StageLifecycleDeps {
  selectedJobPostingId: Ref<number | null>
  selectedStageId: Ref<number | null>
  stages: Ref<StageListItem[]>
  results: Ref<AdminStageResult[]>
  loadStages: () => Promise<void>
  loadResults: () => Promise<void>
  /** 미저장 판정이 있으면 확인을 받는다. 발표 전에만 쓴다. */
  confirmDiscardIfDirty: () => Promise<boolean>
  /** 공고·단계 선택을 주소창 쿼리에 반영한다. */
  syncQuery: () => void
}

interface ConfirmThenRunOptions<T> {
  title: string
  content: string
  okText: string
  action: () => Promise<T>
  success: string | ((result: T) => string)
  fail: string
}

export function useStageLifecycle(deps: StageLifecycleDeps) {
  const commandRunning = ref(false)

  /** 단계 목록과 결과를 함께 다시 읽는다. 상태 전이 후 배너·버튼이 어긋나지 않게 한다. */
  const reloadStageAndResults = async () => {
    const keepStageId = deps.selectedStageId.value
    await deps.loadStages()
    if (keepStageId !== null && deps.stages.value.some((stage) => stage.id === keepStageId)) {
      deps.selectedStageId.value = keepStageId
    }
    await deps.loadResults()
    // 단계가 사라져 폴백된 경우 URL 이 사라진 stageId 를 계속 가리키지 않게 한다.
    deps.syncQuery()
  }

  const runCommand = async <T,>(
    action: () => Promise<T>,
    successMessage: string | ((result: T) => string),
    failMessage: string,
  ) => {
    commandRunning.value = true
    try {
      const result = await action()
      await reloadStageAndResults()
      message.success(typeof successMessage === 'function' ? successMessage(result) : successMessage)
    } catch (error) {
      // 가드 실패의 가장 흔한 원인이 "화면이 stale"(다른 관리자가 먼저 전이시킴, 공고 게시 취소)이다.
      // 재조회하지 않으면 배너가 서버와 어긋난 채로 남아 같은 실패가 반복된다.
      await reloadStageAndResults()
      message.error(getApiErrorMessage(error, failMessage))
    } finally {
      commandRunning.value = false
    }
  }

  const confirmThenRun = <T,>(options: ConfirmThenRunOptions<T>) => {
    Modal.confirm({
      title: options.title,
      content: options.content,
      okText: options.okText,
      cancelText: '취소',
      onOk: () => runCommand(options.action, options.success, options.fail),
    })
  }

  const initializeResults = () => {
    const stageId = deps.selectedStageId.value
    if (stageId === null) {
      return
    }
    confirmThenRun({
      title: '대상자를 불러올까요?',
      content:
        '제출 완료 지원서를 이 단계의 대상자로 등록합니다. 이미 등록된 대상자는 그대로 두고 새로 제출된 지원서만 추가합니다.',
      okText: '불러오기',
      action: () => adminStageApi.initializeResults(stageId),
      success: (response) => {
        const { createdCount, existingCount, skippedCount } = response.data.data
        return `신규 ${createdCount}건 · 기존 ${existingCount}건 · 제외 ${skippedCount}건`
      },
      fail: '대상자를 불러오지 못했습니다.',
    })
  }

  const startStage = () => {
    const jobPostingId = deps.selectedJobPostingId.value
    const stageId = deps.selectedStageId.value
    if (jobPostingId === null || stageId === null) {
      return
    }
    confirmThenRun({
      title: '전형을 시작할까요?',
      content: '시작하면 결과를 판정할 수 있고, 단계 이름·유형·순서는 더 이상 바꿀 수 없습니다.',
      okText: '시작',
      action: () => adminStageApi.startStage(jobPostingId, stageId),
      success: '전형을 시작했습니다.',
      fail: '전형을 시작하지 못했습니다.',
    })
  }

  const announceStage = async () => {
    const jobPostingId = deps.selectedJobPostingId.value
    const stageId = deps.selectedStageId.value
    if (jobPostingId === null || stageId === null) {
      return
    }
    // 발표는 되돌릴 수 없어 미저장 판정을 남긴 채 진행하면 안 된다.
    if (!(await deps.confirmDiscardIfDirty())) {
      return
    }
    const summary = DECIDABLE_RESULT_STATUSES.map((status) => ({
      label: STAGE_RESULT_STATUS_LABELS[status],
      count: deps.results.value.filter((result) => result.resultStatus === status).length,
    }))
      .filter((entry) => entry.count > 0)
      .map((entry) => `${entry.label} ${entry.count}`)
      .join(' · ')
    confirmThenRun({
      title: '결과를 발표할까요?',
      content: `대상 ${deps.results.value.length}명 (${summary}). 발표하면 지원자에게 결과가 공개되고, 이후 변경은 사유를 남기는 정정으로만 가능합니다.`,
      okText: '발표',
      action: () => adminStageApi.announceStage(jobPostingId, stageId),
      success: '결과를 발표했습니다.',
      fail: '결과를 발표하지 못했습니다.',
    })
  }

  const closeStage = () => {
    const jobPostingId = deps.selectedJobPostingId.value
    const stageId = deps.selectedStageId.value
    if (jobPostingId === null || stageId === null) {
      return
    }
    confirmThenRun({
      title: '단계를 마감할까요?',
      content: '마감하면 이 단계의 상태를 더 이상 되돌릴 수 없습니다.',
      okText: '마감',
      action: () => adminStageApi.closeStage(jobPostingId, stageId),
      success: '단계를 마감했습니다.',
      fail: '단계를 마감하지 못했습니다.',
    })
  }

  return {
    commandRunning,
    reloadStageAndResults,
    initializeResults,
    startStage,
    announceStage,
    closeStage,
  }
}
```

**주의:** 본체의 현재 코드와 위 코드가 세부에서 다를 수 있다(S2 리뷰 반영으로 `runCommand`가 제네릭 + 함수형 메시지가 됐고 `confirmThenRun`이 4개 명령을 모두 흡수했다). **본체의 실제 코드를 읽고 그것을 그대로 옮겨라.** 위 코드는 형태 참고용이며, 동작이 다르면 본체 쪽이 정답이다.

- [ ] **Step 2: 본체에서 제거하고 컴포저블 사용**

옮긴 블록을 본체에서 지우고, `openApplication` 위에 배선을 넣는다:

```ts
const {
  commandRunning,
  reloadStageAndResults,
  initializeResults,
  startStage,
  announceStage,
  closeStage,
} = useStageLifecycle({
  selectedJobPostingId,
  selectedStageId,
  stages,
  results,
  loadStages,
  loadResults,
  confirmDiscardIfDirty,
  syncQuery,
})
```

`reloadStageAndResults`가 이제 컴포저블에서 오므로, 본체 안의 다른 호출부(있다면)가 그대로 동작하는지 확인한다. import에서 더 이상 쓰지 않게 된 것(`DECIDABLE_RESULT_STATUSES`, `STAGE_RESULT_STATUS_LABELS`, `Modal` 등)이 있으면 정리한다. **`Modal`은 `confirmDiscardIfDirty`가 계속 쓰므로 남는다.**

- [ ] **Step 3: 검증**

```bash
npm run type-check
npx eslint src/views/admin/stageResult/
```

Expected: 통과. 본체 줄 수가 약 600줄로 줄어야 한다(`wc -l`로 확인).

---

### Task 2: 타입 추가

**Files:**
- Modify: `src/types/admin/stage.ts` (파일 끝에 추가)

- [ ] **Step 1: 업로드·정정 타입 추가**

```ts
/* ---- 엑셀 업로드 (S3) ---- */

/** 업로드 행 상태. STALE 은 commit 시점의 낙관적 동시성 위반이라 preview 에는 나오지 않는다. */
export type StageResultUploadRowStatus = 'CHANGED' | 'UNCHANGED' | 'ERROR' | 'STALE'

/** all-or-nothing 결과. REJECTED_* 는 0건 적용이다. */
export type StageResultUploadCommitOutcome = 'APPLIED' | 'REJECTED_VALIDATION' | 'REJECTED_STALE'

/**
 * 변경 전후 비교. **모든 값이 문자열**이고 결과는 enum 이름(`PASSED` 등)으로 온다 —
 * 화면에 그릴 때 STAGE_RESULT_STATUS_LABELS 로 바꿔야 한다. 값이 없으면 null.
 */
export interface StageResultUploadDiff {
  oldResultStatus: string | null
  newResultStatus: string | null
  oldScore: string | null
  newScore: string | null
  oldComment: string | null
  newComment: string | null
}

export interface StageResultUploadRow {
  /** 스프레드시트 행 번호(1-based, 헤더가 1행) */
  rowNumber: number
  /** 파싱 실패 시 null */
  stageResultId: number | null
  applicationId: number | null
  applicantName: string | null
  status: StageResultUploadRowStatus
  /** ERROR·STALE 사유. 그 외엔 빈 배열 */
  errors: string[]
  /** CHANGED·STALE 만 값이 있다 */
  diff: StageResultUploadDiff | null
}

export interface StageResultUploadPreview {
  stageId: number
  totalRows: number
  changedCount: number
  unchangedCount: number
  errorCount: number
  /** errorCount === 0. 동시성 위반은 commit 에서 따로 판정하므로 이 값이 true 여도 거부될 수 있다. */
  committable: boolean
  rows: StageResultUploadRow[]
}

export interface StageResultUploadCommit {
  stageId: number
  outcome: StageResultUploadCommitOutcome
  totalRows: number
  changedCount: number
  unchangedCount: number
  errorCount: number
  staleCount: number
  /** 거부 시 ERROR·STALE 행. 성공 시 빈 배열 */
  failedRows: StageResultUploadRow[]
}

/* ---- 발표 후 정정 (S3) ---- */

export interface StageResultCorrectionRequest {
  resultStatus: StageResultStatus
  score: number | null
  comment: string | null
  /** 필수, 1000자 이하. 이력에 남는다. */
  reason: string
}

export interface StageResultCorrectionHistory {
  historyId: number
  stageResultId: number
  correctedAt: string
  correctedBy: string
  reason: string
  previousStatus: StageResultStatus
  newStatus: StageResultStatus
  previousScore: number | null
  newScore: number | null
  previousComment: string | null
  newComment: string | null
  previousDecidedAt: string | null
  newDecidedAt: string | null
}
```

- [ ] **Step 2: 검증**

```bash
npm run type-check
```

---

### Task 3: 파일 다운로드 공통 함수 + API 확장

**Files:**
- Create: `src/common/fileDownload.ts`
- Modify: `src/api/admin/adminStageApi.ts`

`ApplicationStatus.vue`의 `careerDescriptionDownload`가 같은 일을 하는데 그 안에 인라인으로 박혀 있다. S3는 다운로드가 둘(템플릿·export)이라 공통 함수로 뺀다. **기존 화면은 건드리지 않는다**(범위 밖).

- [ ] **Step 1: 다운로드 함수 작성**

`src/common/fileDownload.ts`:

```ts
import type { AxiosResponse } from 'axios'

/**
 * blob 응답을 파일로 저장한다. 파일명은 Content-Disposition 의 `filename*=UTF-8''` 를 우선 쓰고,
 * 없으면 호출부가 준 기본값을 쓴다(서버가 헤더를 못 주는 경우 대비).
 */
export const saveBlobResponse = (response: AxiosResponse<Blob>, fallbackFileName: string): void => {
  const disposition = response.headers['content-disposition']
  let fileName = fallbackFileName
  if (typeof disposition === 'string') {
    const match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
    if (match) {
      fileName = decodeURIComponent(match[1])
    }
  }

  const blobUrl = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = blobUrl
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(blobUrl)
}
```

- [ ] **Step 2: API 메서드 6개 추가**

`src/api/admin/adminStageApi.ts`의 `countSubmittedApplications` 아래에 추가. import에 필요한 타입을 더한다.

```ts
  /* ---- 엑셀 왕복 ---- */

  /** 현재 결과가 프리필된 업로드 템플릿(xlsx). 업로드는 이 파일만 받는다. */
  downloadUploadTemplate(stageId: number) {
    return apiClient.get<Blob>(`/admin/stages/${stageId}/results/upload-template`, {
      responseType: 'blob',
    })
  },

  /** 결과 목록 xlsx(읽기 전용). 열 구성이 업로드 템플릿과 달라 업로드 소스로 쓸 수 없다. */
  exportResults(stageId: number) {
    return apiClient.get<Blob>(`/admin/stages/${stageId}/results/export`, {
      responseType: 'blob',
    })
  },

  /** 업로드 검증·diff. 저장하지 않는다. 파일 자체가 거부되면 data 없는 400. */
  previewUpload(stageId: number, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return apiClient.post<ApiResponse<StageResultUploadPreview>>(
      `/admin/stages/${stageId}/results/upload/preview`,
      formData,
      { timeout: UPLOAD_TIMEOUT_MS },
    )
  },

  /**
   * 업로드 적용. all-or-nothing 이라 오류·STALE 이 하나라도 있으면 0건 반영된다.
   * outcome 이 REJECTED_VALIDATION 이면 400, REJECTED_STALE 이면 409 로 오는데
   * **둘 다 응답 본문의 data 에 commit 결과가 들어 있다**(failedRows 포함).
   */
  commitUpload(stageId: number, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return apiClient.post<ApiResponse<StageResultUploadCommit>>(
      `/admin/stages/${stageId}/results/upload/commit`,
      formData,
      { timeout: UPLOAD_TIMEOUT_MS },
    )
  },

  /* ---- 발표 후 정정 ---- */

  /** 발표·마감된 단계의 결과를 사유와 함께 고친다. 이력이 append 된다. */
  correctResult(stageId: number, resultId: number, request: StageResultCorrectionRequest) {
    return apiClient.post<ApiResponse<AdminStageResult>>(
      `/admin/stages/${stageId}/results/${resultId}/correct`,
      request,
    )
  },

  /** 정정 이력. 최신순이다. */
  getCorrectionHistories(stageId: number, resultId: number) {
    return apiClient.get<ApiResponse<StageResultCorrectionHistory[]>>(
      `/admin/stages/${stageId}/results/${resultId}/histories`,
    )
  },
```

파일 상단에 상수를 추가한다(`adminJobPostingApi.ts`가 같은 이름·값을 쓴다):

```ts
const UPLOAD_TIMEOUT_MS = 120000 // 기본 10초로는 대량 엑셀 업로드가 끊길 수 있다.
```

- [ ] **Step 3: 검증**

```bash
npm run type-check
npx eslint src/common/fileDownload.ts src/api/admin/adminStageApi.ts
```

---

### Task 4: 업로드 미리보기 모달

**Files:**
- Create: `src/views/admin/stageResult/StageUploadPreviewModal.vue`

설계 §4.7, 시안의 "엑셀 업로드 미리보기" 탭. 파일 선택 → 즉시 preview → 표 확인 → 적용.

**이 컴포넌트가 스스로 갖는 상태:** 선택한 파일, preview 결과, commit 결과, 보기 필터, 로딩 플래그. 본체는 `open`(v-model)과 `stageId`만 주고 `applied` 이벤트를 받는다.

- [ ] **Step 1: 컴포넌트 작성**

`src/views/admin/stageResult/StageUploadPreviewModal.vue`:

```vue
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import axios from 'axios'
import type { TableColumnsType } from 'ant-design-vue'
import { adminStageApi } from '@/api/admin/adminStageApi'
import { getApiErrorMessage } from '@/api/apiError'
import { saveBlobResponse } from '@/common/fileDownload'
import {
  STAGE_RESULT_STATUS_LABELS,
  type ApiFailurePayload,
  type StageResultStatus,
  type StageResultUploadCommit,
  type StageResultUploadPreview,
  type StageResultUploadRow,
  type StageResultUploadRowStatus,
} from '@/types/admin/stage'

const props = defineProps<{
  open: boolean
  stageId: number | null
  stageName: string
}>()

const emit = defineEmits<{
  (event: 'update:open', open: boolean): void
  /** 적용 성공. 본체가 결과를 재조회한다. */
  (event: 'applied'): void
}>()

const file = ref<File | null>(null)
const preview = ref<StageResultUploadPreview | null>(null)
/** 거부된 commit 결과. 적용 실패 시 failedRows 를 보여주기 위해 둔다. */
const rejected = ref<StageResultUploadCommit | null>(null)
/** 파일 자체가 거부된 경우(구 템플릿·확장자·크기). 행 결과가 없어 문구만 보여준다. */
const fileError = ref<string | null>(null)
const previewing = ref(false)
const committing = ref(false)
const showAllRows = ref(false)

const rowStatusMeta: Record<StageResultUploadRowStatus, { label: string; color: string }> = {
  CHANGED: { label: '변경', color: 'orange' },
  UNCHANGED: { label: '미변경', color: 'default' },
  ERROR: { label: '오류', color: 'red' },
  STALE: { label: '충돌', color: 'red' },
}

const columns: TableColumnsType = [
  { title: '행', key: 'rowNumber', width: 60 },
  { title: '수험번호', key: 'applicationId', width: 100 },
  { title: '이름', key: 'applicantName', width: 100 },
  { title: '결과', key: 'result', width: 160 },
  { title: '점수', key: 'score', width: 120 },
  { title: '코멘트', key: 'comment', width: 200 },
  { title: '판정', key: 'status', width: 260 },
]

/** 표시 대상 행. 거부된 commit 이 있으면 그 실패 행을, 아니면 preview 행을 본다. */
const sourceRows = computed<StageResultUploadRow[]>(() => rejected.value?.failedRows ?? preview.value?.rows ?? [])

const visibleRows = computed(() =>
  showAllRows.value
    ? sourceRows.value
    : sourceRows.value.filter((row) => row.status !== 'UNCHANGED'),
)

const committable = computed(() => preview.value?.committable === true && rejected.value === null)

/** enum 이름으로 오는 diff 값을 한글 라벨로 바꾼다. 알 수 없는 값은 그대로 보여준다. */
const statusLabel = (raw: string | null): string => {
  if (raw === null) {
    return '-'
  }
  return STAGE_RESULT_STATUS_LABELS[raw as StageResultStatus] ?? raw
}

const textOrDash = (raw: string | null) => (raw === null || raw.length === 0 ? '-' : raw)

const reset = () => {
  file.value = null
  preview.value = null
  rejected.value = null
  fileError.value = null
  showAllRows.value = false
}

const close = () => {
  emit('update:open', false)
}

/** 모달을 열 때마다 이전 결과를 지운다. 다른 단계의 결과가 남아 보이면 안 된다. */
watch(
  () => props.open,
  (open) => {
    if (open) {
      reset()
    }
  },
)

const downloadTemplate = async () => {
  if (props.stageId === null) {
    return
  }
  try {
    const response = await adminStageApi.downloadUploadTemplate(props.stageId)
    saveBlobResponse(response, `${props.stageName}_결과등록양식.xlsx`)
  } catch (error) {
    message.error(getApiErrorMessage(error, '템플릿을 내려받지 못했습니다.'))
  }
}

/**
 * 400·409 응답 본문에서 commit 결과를 꺼낸다. 백엔드가 거부 시에도 data 에 결과를 담아 주므로
 * 행별 실패 사유를 보여줄 수 있다. 파일 자체가 거부된 경우엔 data 가 없어 null 이 나온다.
 */
const extractCommitPayload = (error: unknown): StageResultUploadCommit | null => {
  if (!axios.isAxiosError<ApiFailurePayload<StageResultUploadCommit>>(error)) {
    return null
  }
  return error.response?.data?.data ?? null
}

const selectFile = async (selected: File) => {
  if (props.stageId === null) {
    return
  }
  reset()
  file.value = selected
  previewing.value = true
  try {
    const response = await adminStageApi.previewUpload(props.stageId, selected)
    preview.value = response.data.data
  } catch (error) {
    // 파일 레벨 거부(구 영문 템플릿·확장자·크기)는 행 결과가 없다. 문구만 보여준다.
    fileError.value = getApiErrorMessage(error, '업로드 파일을 읽지 못했습니다.')
  } finally {
    previewing.value = false
  }
}

/** a-upload 가 자동 전송하지 않게 false 를 돌려준다. 전송은 우리가 직접 한다. */
const beforeUpload = (selected: File) => {
  void selectFile(selected)
  return false
}

const commit = async () => {
  if (props.stageId === null || file.value === null) {
    return
  }
  committing.value = true
  try {
    const response = await adminStageApi.commitUpload(props.stageId, file.value)
    const applied = response.data.data
    message.success(`${applied.changedCount}건을 반영했습니다.`)
    emit('applied')
    close()
  } catch (error) {
    const payload = extractCommitPayload(error)
    if (payload === null) {
      fileError.value = getApiErrorMessage(error, '업로드를 적용하지 못했습니다.')
    } else {
      rejected.value = payload
      showAllRows.value = false
    }
  } finally {
    committing.value = false
  }
}
</script>

<template>
  <a-modal
    :open="open"
    :title="`엑셀 업로드 · ${stageName}`"
    width="1000px"
    :mask-closable="false"
    @update:open="(next: boolean) => emit('update:open', next)"
  >
    <div class="upload-bar">
      <a-upload :before-upload="beforeUpload" :show-upload-list="false" accept=".xlsx">
        <a-button :loading="previewing">{{ file ? '파일 변경' : '파일 선택' }}</a-button>
      </a-upload>
      <span v-if="file" class="file-name">{{ file.name }}</span>
      <span class="bar-spacer" />
      <a-button @click="downloadTemplate">엑셀 템플릿 내려받기</a-button>
    </div>

    <a-alert
      v-if="!file && !fileError"
      class="hint"
      type="info"
      show-icon
      message="템플릿 다운로드 파일만 업로드할 수 있습니다."
      description="결과 목록 다운로드 파일은 열 구성이 달라 업로드할 수 없습니다. 결과·점수·코멘트만 고치고 나머지 열은 그대로 두세요."
    />

    <a-alert v-if="fileError" class="hint" type="error" show-icon :message="fileError" />

    <template v-if="preview">
      <div class="summary">
        <a-tag>총 {{ preview.totalRows }}행</a-tag>
        <a-tag color="orange">변경 {{ preview.changedCount }}</a-tag>
        <a-tag>미변경 {{ preview.unchangedCount }}</a-tag>
        <a-tag :color="preview.errorCount > 0 ? 'red' : 'default'">오류 {{ preview.errorCount }}</a-tag>
        <a-tag v-if="rejected && rejected.staleCount > 0" color="red">충돌 {{ rejected.staleCount }}</a-tag>
        <span class="bar-spacer" />
        <a-switch v-model:checked="showAllRows" size="small" />
        <span class="switch-label">미변경 행도 보기</span>
      </div>

      <a-alert
        v-if="rejected"
        class="hint"
        type="error"
        show-icon
        :message="
          rejected.outcome === 'REJECTED_STALE'
            ? '다른 관리자가 먼저 값을 바꿔 적용하지 않았습니다.'
            : '오류가 있어 적용하지 않았습니다.'
        "
        :description="
          rejected.outcome === 'REJECTED_STALE'
            ? '템플릿을 다시 내려받아 최신 값으로 편집한 뒤 올려주세요. 한 행이라도 충돌하면 전체가 반영되지 않습니다.'
            : '아래 행을 고친 뒤 다시 올려주세요. 한 행이라도 오류가 있으면 전체가 반영되지 않습니다.'
        "
      />
      <a-alert
        v-else-if="preview.errorCount > 0"
        class="hint"
        type="error"
        show-icon
        :message="`오류 ${preview.errorCount}건이 있어 적용할 수 없습니다.`"
        description="한 행이라도 오류가 있으면 전체가 반영되지 않습니다. 파일을 고친 뒤 다시 올려주세요."
      />

      <a-table
        :columns="columns"
        :data-source="visibleRows"
        :pagination="{ pageSize: 10 }"
        row-key="rowNumber"
        size="small"
        :scroll="{ x: 1000, y: 320 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'applicationId'">{{ record.applicationId ?? '-' }}</template>
          <template v-else-if="column.key === 'applicantName'">{{ record.applicantName ?? '-' }}</template>

          <template v-else-if="column.key === 'result'">
            <template v-if="record.diff">
              {{ statusLabel(record.diff.oldResultStatus) }} → {{ statusLabel(record.diff.newResultStatus) }}
            </template>
            <template v-else>-</template>
          </template>

          <template v-else-if="column.key === 'score'">
            <template v-if="record.diff">
              {{ textOrDash(record.diff.oldScore) }} → {{ textOrDash(record.diff.newScore) }}
            </template>
            <template v-else>-</template>
          </template>

          <template v-else-if="column.key === 'comment'">
            <template v-if="record.diff">
              {{ textOrDash(record.diff.oldComment) }} → {{ textOrDash(record.diff.newComment) }}
            </template>
            <template v-else>-</template>
          </template>

          <template v-else-if="column.key === 'status'">
            <a-tag :color="rowStatusMeta[record.status as StageResultUploadRowStatus].color">
              {{ rowStatusMeta[record.status as StageResultUploadRowStatus].label }}
            </a-tag>
            <span v-if="record.errors.length > 0" class="row-error">{{ record.errors.join(' / ') }}</span>
          </template>
        </template>
      </a-table>
    </template>

    <template #footer>
      <span class="footer-note">
        적용하면 변경 행이 한 번에 반영됩니다. 미리보기 이후 다른 관리자가 바꾼 행이 있으면 전체가 거부됩니다.
      </span>
      <a-button @click="close">닫기</a-button>
      <a-button type="primary" :disabled="!committable" :loading="committing" @click="commit">
        적용<template v-if="preview"> ({{ preview.changedCount }}건)</template>
      </a-button>
    </template>
  </a-modal>
</template>

<style scoped lang="scss">
.upload-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.bar-spacer {
  flex: 1;
}

.file-name {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.hint {
  margin-bottom: 12px;
}

.summary {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.switch-label {
  font-size: 12px;
  color: var(--app-text-muted);
}

.row-error {
  margin-left: 6px;
  font-size: 11.5px;
  color: var(--app-color-error);
}

.footer-note {
  float: left;
  max-width: 60%;
  text-align: left;
  font-size: 11.5px;
  color: var(--app-text-muted);
  line-height: 1.4;
}
</style>
```

- [ ] **Step 2: `ApiFailurePayload` 타입 추가**

위 코드가 쓰는 타입을 `src/types/api.ts`가 아니라 `src/types/admin/stage.ts`에 둔다(`src/types/api.ts`는 다른 화면이 공유하는 파일이라 이번 슬라이스에서 건드리지 않는다).

```ts
/**
 * 백엔드가 400·409 로 거부하면서도 본문 data 에 결과를 담아 주는 응답의 모양.
 * 엑셀 commit 이 이 형태다 — 거부여도 failedRows 를 꺼내 행별 사유를 보여줄 수 있다.
 */
export interface ApiFailurePayload<T> {
  success: boolean
  message?: string
  data: T | null
}
```

- [ ] **Step 3: 검증**

```bash
npm run type-check
npx eslint src/views/admin/stageResult/StageUploadPreviewModal.vue
```

타입 오류가 나면 `a-table`의 `bodyCell` 슬롯 `record`가 느슨한 타입인 것이 원인일 가능성이 높다. `StageResultGrid.vue`가 같은 문제를 **타입이 붙은 헬퍼에 필드만 넘기는 방식**으로 풀었으니 그 파일을 읽고 같은 패턴을 쓴다. `any`·`@ts-ignore`·`eslint-disable`은 쓰지 않는다.

---

### Task 5: 정정 모달

**Files:**
- Create: `src/views/admin/stageResult/StageResultCorrectModal.vue`

설계 §4.6, 시안의 정정 모달. 발표·마감된 단계의 한 행을 사유와 함께 고치고, 같은 모달에 이력을 보여준다.

- [ ] **Step 1: 컴포넌트 작성**

`src/views/admin/stageResult/StageResultCorrectModal.vue`:

```vue
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { adminStageApi } from '@/api/admin/adminStageApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import {
  DECIDABLE_RESULT_STATUSES,
  STAGE_RESULT_STATUS_COLORS,
  STAGE_RESULT_STATUS_LABELS,
  type AdminStageResult,
  type StageResultCorrectionHistory,
  type StageResultStatus,
} from '@/types/admin/stage'

const props = defineProps<{
  open: boolean
  stageId: number | null
  /** 정정 대상 행. 모달을 열 때 본체가 넘긴다. */
  target: AdminStageResult | null
}>()

const emit = defineEmits<{
  (event: 'update:open', open: boolean): void
  /** 정정 성공. 본체가 결과를 재조회한다. */
  (event: 'corrected'): void
}>()

const resultStatus = ref<StageResultStatus>('PASSED')
const score = ref<number | null>(null)
const comment = ref<string | null>(null)
const reason = ref('')
const submitting = ref(false)
const histories = ref<StageResultCorrectionHistory[]>([])
const loadingHistories = ref(false)

const statusOptions = DECIDABLE_RESULT_STATUSES.map((status) => ({
  value: status,
  label: STAGE_RESULT_STATUS_LABELS[status],
}))

/** 사유는 필수다(백엔드 @NotBlank). 버튼 활성 조건을 서버 규칙과 맞춘다. */
const submittable = computed(() => reason.value.trim().length > 0 && props.target !== null)

const statusLabel = (status: StageResultStatus) => STAGE_RESULT_STATUS_LABELS[status]
const statusColor = (status: StageResultStatus) => STAGE_RESULT_STATUS_COLORS[status]

const loadHistories = async () => {
  if (props.stageId === null || props.target === null) {
    histories.value = []
    return
  }
  loadingHistories.value = true
  try {
    const response = await adminStageApi.getCorrectionHistories(props.stageId, props.target.stageResultId)
    histories.value = response.data.data
  } catch {
    // 이력은 부가 정보라 실패해도 정정 자체를 막지 않는다.
    histories.value = []
  } finally {
    loadingHistories.value = false
  }
}

/** 모달을 열 때마다 대상 행의 현재 값으로 폼을 채우고 이력을 읽는다. */
watch(
  () => props.open,
  (open) => {
    if (!open || props.target === null) {
      return
    }
    // 현재 값이 대기면 셀렉트 기본값을 합격으로 둔다(대기는 정정 값으로 보낼 수 없다).
    resultStatus.value = props.target.resultStatus === 'PENDING' ? 'PASSED' : props.target.resultStatus
    score.value = props.target.score
    comment.value = props.target.comment
    reason.value = ''
    void loadHistories()
  },
)

const close = () => {
  emit('update:open', false)
}

const submit = async () => {
  if (props.stageId === null || props.target === null) {
    return
  }
  submitting.value = true
  try {
    await adminStageApi.correctResult(props.stageId, props.target.stageResultId, {
      resultStatus: resultStatus.value,
      score: score.value,
      comment: comment.value,
      reason: reason.value.trim(),
    })
    message.success('결과를 정정했습니다.')
    emit('corrected')
    close()
  } catch (error) {
    message.error(getApiErrorMessage(error, '결과를 정정하지 못했습니다.'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <a-modal
    :open="open"
    :title="target ? `결과 정정 · ${target.applicationId} ${target.applicantName}` : '결과 정정'"
    width="640px"
    :mask-closable="false"
    @update:open="(next: boolean) => emit('update:open', next)"
  >
    <a-alert
      class="notice"
      type="warning"
      show-icon
      message="발표 후 정정입니다."
      description="사유가 이력에 남고, 지원자 화면에는 최신 결과만 표시됩니다."
    />

    <dl v-if="target" class="current">
      <dt>현재 결과</dt>
      <dd>
        <a-tag :color="statusColor(target.resultStatus)">{{ statusLabel(target.resultStatus) }}</a-tag>
        <span v-if="target.score !== null">{{ target.score }}점</span>
      </dd>
      <dt>현재 코멘트</dt>
      <dd>{{ target.comment ?? '-' }}</dd>
    </dl>

    <a-form layout="vertical">
      <a-form-item label="변경할 결과">
        <a-select v-model:value="resultStatus" :options="statusOptions" />
      </a-form-item>
      <a-form-item label="점수">
        <a-input-number v-model:value="score" style="width: 100%" />
      </a-form-item>
      <a-form-item label="코멘트">
        <a-input v-model:value="comment" :maxlength="2000" />
      </a-form-item>
      <a-form-item label="정정 사유" required>
        <a-textarea
          v-model:value="reason"
          :rows="3"
          :maxlength="1000"
          show-count
          placeholder="예: 채점 누락분 반영 (면접위원 B 점수표)"
        />
      </a-form-item>
    </a-form>

    <section v-if="histories.length > 0" class="histories">
      <h4>정정 이력 {{ histories.length }}건</h4>
      <a-spin :spinning="loadingHistories">
        <ul>
          <li v-for="history in histories" :key="history.historyId">
            <span class="when">{{ formatDate(history.correctedAt, 'YYYY-MM-DD HH:mm') }}</span>
            <span class="who">{{ history.correctedBy }}</span>
            <span class="change">
              {{ statusLabel(history.previousStatus) }} → {{ statusLabel(history.newStatus) }}
            </span>
            <span class="reason">{{ history.reason }}</span>
          </li>
        </ul>
      </a-spin>
    </section>

    <template #footer>
      <a-button @click="close">취소</a-button>
      <a-button type="primary" :disabled="!submittable" :loading="submitting" @click="submit">
        정정 저장
      </a-button>
    </template>
  </a-modal>
</template>

<style scoped lang="scss">
.notice {
  margin-bottom: 12px;
}

.current {
  display: grid;
  grid-template-columns: 90px 1fr;
  gap: 4px 10px;
  margin-bottom: 12px;
  font-size: 13px;

  dt {
    color: var(--app-text-secondary);
  }

  dd {
    margin: 0;
    font-weight: 600;
  }
}

.histories {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--app-border-default);

  h4 {
    margin: 0 0 8px;
    font-size: 13px;
  }

  ul {
    margin: 0;
    padding: 0;
    list-style: none;
  }

  li {
    display: flex;
    gap: 8px;
    align-items: baseline;
    padding: 6px 0;
    border-bottom: 1px solid var(--app-border-subtle);
    font-size: 12px;

    &:last-child {
      border-bottom: none;
    }
  }

  .when {
    color: var(--app-text-muted);
    white-space: nowrap;
  }

  .who {
    color: var(--app-text-secondary);
    white-space: nowrap;
  }

  .change {
    font-weight: 600;
    white-space: nowrap;
  }

  .reason {
    color: var(--app-text-secondary);
    min-width: 0;
  }
}
</style>
```

- [ ] **Step 2: 검증**

```bash
npm run type-check
npx eslint src/views/admin/stageResult/StageResultCorrectModal.vue
```

---

### Task 6: 그리드에 정정 버튼 열 추가

**Files:**
- Modify: `src/views/admin/stageResult/StageResultGrid.vue`

발표·마감 단계에서만 보이는 열이다. 편집 가능(`editable`)일 때는 나오지 않는다.

- [ ] **Step 1: props와 emit 추가**

```ts
  /** 발표·마감 단계에서만 true. 정정 버튼 열을 띄운다. */
  correctable: boolean
```

```ts
  (event: 'correct', result: AdminStageResult): void
```

- [ ] **Step 2: 열 정의를 조건부로 변경**

`columns`가 현재 정적 배열이면 `computed`로 바꾸고, 끝에 조건부로 정정 열을 붙인다:

```ts
const columns = computed<TableColumnsType>(() => {
  const base: TableColumnsType = [
    /* 기존 12개 열 그대로 */
  ]
  if (props.correctable) {
    base.push({ title: '정정', key: 'correct', width: 80 })
  }
  return base
})
```

`:scroll="{ x: ... }"` 값을 정정 열 폭만큼 늘린다(현재 1540 → 정정 열이 있을 때 1620). `computed`로 계산해도 되고 큰 값 하나로 통일해도 된다. **선택한 방식을 보고하라.**

- [ ] **Step 3: 셀 렌더 추가**

`#bodyCell` 안에 추가:

```vue
        <template v-else-if="column.key === 'correct'">
          <a-button size="small" @click="emit('correct', record as AdminStageResult)">정정</a-button>
        </template>
```

`record`를 `AdminStageResult`로 넘겨야 하는데 슬롯 `record`가 느슨한 타입이다. 이 파일이 이미 쓰는 방식(타입이 붙은 헬퍼에 필드만 넘기기)과 어긋나므로, **필드만 넘기는 형태로 바꾸는 편이 낫다.** 예를 들어 emit 시그니처를 `(event: 'correct', stageResultId: number)`로 두고 본체가 id로 행을 찾게 한다. **어느 쪽을 택했는지 보고하라.**

- [ ] **Step 4: 검증**

```bash
npm run type-check
npx eslint src/views/admin/stageResult/StageResultGrid.vue
```

---

### Task 7: 본체에 엑셀·정정 배선

**Files:**
- Modify: `src/views/admin/stageResult/AdminStageResultView.vue`

- [ ] **Step 1: 상태와 핸들러 추가**

import에 두 모달과 `saveBlobResponse`를 더한다.

```ts
const uploadModalOpen = ref(false)
const correctModalOpen = ref(false)
const correctTarget = ref<AdminStageResult | null>(null)
const exporting = ref(false)

/** 발표·마감 단계에서만 정정할 수 있다(백엔드 StageResultCorrectionService.validateCorrectable). */
const correctable = computed(
  () => selectedStage.value?.status === 'RESULT_ANNOUNCED' || selectedStage.value?.status === 'CLOSED',
)

const openUploadModal = async () => {
  // 저장 전 판정이 남은 채 엑셀을 올리면 어느 값이 반영됐는지 알 수 없게 된다.
  if (!(await confirmDiscardIfDirty())) {
    return
  }
  uploadModalOpen.value = true
}

const openCorrectModal = (stageResultId: number) => {
  correctTarget.value = findResult(stageResultId)
  if (correctTarget.value !== null) {
    correctModalOpen.value = true
  }
}

const exportResults = async () => {
  if (selectedStageId.value === null || selectedStage.value === null) {
    return
  }
  exporting.value = true
  try {
    const response = await adminStageApi.exportResults(selectedStageId.value)
    saveBlobResponse(response, `${selectedStage.value.stageName}_전형결과.xlsx`)
  } catch (error) {
    message.error(getApiErrorMessage(error, '결과를 내려받지 못했습니다.'))
  } finally {
    exporting.value = false
  }
}
```

Task 6에서 emit 시그니처를 `AdminStageResult`로 정했다면 `openCorrectModal`도 그에 맞춘다.

- [ ] **Step 2: 배너 액션에 엑셀 버튼 추가**

`IN_PROGRESS`일 때 "엑셀 업로드", 발표·마감일 때 "엑셀 다운로드"를 배너 액션에 넣는다. `<a-space>` 안, 기존 라이프사이클 버튼 **앞**에 둔다(파괴적이지 않은 동작을 먼저).

```vue
              <a-button
                v-if="selectedStage?.status === 'IN_PROGRESS'"
                size="small"
                @click="openUploadModal"
              >
                엑셀 업로드
              </a-button>
              <a-button
                v-if="correctable"
                size="small"
                :loading="exporting"
                @click="exportResults"
              >
                엑셀 다운로드
              </a-button>
```

템플릿 다운로드 버튼은 배너에 두지 않는다. 업로드 모달 안에 있어서, 사용자가 "엑셀 업로드"를 누르면 거기서 템플릿을 받고 바로 올릴 수 있다. 배너 버튼이 늘어나는 것보다 낫다.

- [ ] **Step 3: 모달 두 개를 템플릿 끝에 배치**

`</a-spin>` 뒤, `</div>` 앞에 넣는다.

```vue
    <StageUploadPreviewModal
      v-model:open="uploadModalOpen"
      :stage-id="selectedStageId"
      :stage-name="selectedStage?.stageName ?? ''"
      @applied="reloadStageAndResults"
    />

    <StageResultCorrectModal
      v-model:open="correctModalOpen"
      :stage-id="selectedStageId"
      :target="correctTarget"
      @corrected="reloadStageAndResults"
    />
```

- [ ] **Step 4: 그리드에 새 props·이벤트 연결**

```vue
            :correctable="correctable"
            @correct="openCorrectModal"
```

- [ ] **Step 5: 검증**

```bash
npm run type-check
npm run build-only
npx eslint src/views/admin/stageResult/
```

- [ ] **Step 6: 브라우저 확인**

`.claude/launch.json`이 이미 있다. preview를 열고 `/admin/stage-results`에서 확인한다. 백엔드가 떠 있지 않으면 목록이 비므로, 확인 불가면 그 사실을 보고한다.

확인 항목:
- IN_PROGRESS 단계에서 "엑셀 업로드"가 뜨고, 저장 전 변경이 있으면 먼저 확인 모달이 뜬다
- 업로드 모달에서 템플릿을 내려받을 수 있다
- xlsx를 고르면 즉시 미리보기가 뜨고, 오류가 있으면 적용 버튼이 비활성이다
- 발표 완료 단계에서 그리드에 "정정" 열이 뜨고, 편집 열은 배지로 바뀌어 있다
- 정정 모달에서 사유가 비면 저장 버튼이 비활성이다

---

### Task 8: 문서 갱신

**Files:**
- Modify: `docs/superpowers/specs/2026-09-04-admin-stage-result-management-design.md`

S3는 계약을 바꾸지 않으므로 `api-contract.md`는 손대지 않는다(🟢 확정은 S4).

- [ ] **Step 1: 슬라이스 표 갱신**

§8의 S3 행을 완료로 바꾼다.

```markdown
| ✅ S3 프론트 엑셀·정정 (2026-09-04 완료) | 템플릿↓, 업로드 미리보기/적용, export↓, 정정 모달·이력, 라이프사이클 컴포저블 추출 | `type-check`·`build` 통과 |
```

- [ ] **Step 2: S3 결정 사항 기록**

§12 뒤에 절을 추가한다. 아래는 뼈대이며, **구현 중 실제로 내린 결정으로 채운다.**

```markdown
## 13. S3에서 내린 결정

- **라이프사이클을 `useStageLifecycle` 컴포저블로 뺐다.** S2 통합 리뷰 권고대로 S3 착수 직전에 처리했다. 본체는 약 600줄로 줄었고, 엑셀·정정을 얹어도 관리 가능한 크기를 유지한다.
- **템플릿 다운로드 버튼을 배너가 아니라 업로드 모달 안에 뒀다.** 사용자가 "엑셀 업로드"를 누르면 그 자리에서 템플릿을 받고 바로 올릴 수 있다. 배너 버튼 수를 늘리지 않는다.
- **commit 거부(400·409) 응답의 `data`에서 `failedRows`를 꺼내 표에 그린다.** 백엔드가 거부하면서도 행별 결과를 담아 주기 때문이다. 파일 자체가 거부되는 경우(구 템플릿·확장자·크기)는 `data`가 없어 문구만 보여준다.
- (구현 중 추가된 결정을 여기에 적는다)
```

---

## 자체 검토 결과

- 설계서 §3.4 정정 → Task 5·6·7, §3.5 엑셀 왕복 → Task 3·4·7, §4.6 정정 모달 → Task 5, §4.7 업로드 미리보기 → Task 4. §12가 예고한 컴포저블 추출 → Task 1.
- 설계서 §11의 관련 항목 반영: 업로드 400 두 종류 구분(Task 4의 `extractCommitPayload`), `results/export`는 업로드 소스가 아님(Task 4의 안내 문구), 라벨 맵 동기화(Task 4·5가 `STAGE_RESULT_STATUS_LABELS`를 그대로 씀).
- Task 간 타입 일치: `StageResultUploadPreview`·`StageResultUploadCommit`·`StageResultUploadRow`·`ApiFailurePayload`·`StageResultCorrectionRequest`·`StageResultCorrectionHistory`는 Task 2가 정의하고 Task 3~5가 쓴다. `saveBlobResponse`는 Task 3이 만들고 Task 4·7이 쓴다. `useStageLifecycle`은 Task 1이 만들고 Task 7이 `reloadStageAndResults`를 모달 성공 핸들러로 쓴다.
- 순서 의존: Task 1 → 2 → 3 → (4, 5 병렬 가능) → 6 → 7 → 8. Task 1은 순수 이동이라 먼저 끝내야 이후 Task가 깨끗한 파일 위에서 작업한다.
- **S3가 끝나면 S4만 남는다.** S4는 단계 설정 드로어, 공고 상세·지원서 설정 현황판의 절충 링크, 메뉴 등록 안내, `api-contract.md` 🟢 확정이다. HTML 리포트는 사용자 지시대로 S4 완료 후 1회만 만든다.
