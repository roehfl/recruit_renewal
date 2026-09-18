# 전형결과 관리 S4 (프론트 단계 설정) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자가 화면에서 전형 단계를 만들고 고칠 수 있게 한다. 지금은 API로만 단계를 만들 수 있어, 단계가 없는 공고는 전형결과 관리 화면이 빈 상태로 멈춘다. 이 슬라이스로 화면이 완결된다.

**Architecture:** 백엔드 변경 없음. 드로어 컴포넌트 하나를 추가하고 스텝퍼에 여는 버튼을 단다. 진입점은 공고 상세와 지원서 설정 현황판에도 링크로 둔다(설계 §2의 "절충안"). 드로어는 자체 편집 버퍼를 갖고 저장 시 필요한 API만 골라 호출한다.

**Tech Stack:** Vue 3 `<script setup lang="ts">`, TypeScript, ant-design-vue 4(`a-drawer`, `a-form`, `a-date-picker`).

**설계서:** `docs/superpowers/specs/2026-09-04-admin-stage-result-management-design.md` (§3.6 단계 설정, §4.2 스텝퍼, §4.8 드로어, §4.10 절충 링크, §5.2 수정 완화, §11~§13)
**계약:** `api-contract.md`의 "전형결과 관리" 섹션 — **변경 2가 이 슬라이스의 몫**이고, 셋 다 S4에서 🟢로 확정한다.
**시안:** `docs/design/전형결과 관리 관리자 시안.html` — "단계 설정 드로어" 탭

**작업 루트:** 모든 경로는 `recruit_front/` 기준. 검증:

```bash
npm run type-check
npm run build-only
```

**커밋:** 프로젝트 규칙(`recruit/CLAUDE.md` §6)상 사용자가 명시 요청할 때만 커밋한다. 각 Task는 검증 통과로 끝내고 커밋 단계를 두지 않는다.

---

## ⚠ S3가 남긴 필수 전제 두 가지

**1. 드로어의 마스크를 끄지 마라.** 업로드·정정 모달은 제출 시점에 `props.stageId`를 실시간으로 읽는다. 지금 정합성은 "모달 마스크가 스텝퍼와 공고 셀렉트를 막는다"는 **코드에 기록되지 않은 전제**에 걸려 있다. 드로어를 `:mask="false"`로 열어 두고 단계를 바꿀 수 있게 하면 이 전제가 조용히 깨진다. `a-drawer`는 기본이 마스크 있음이므로 **끄지만 않으면 된다.**

**2. `AdminJobPostingListItem` import 줄을 건드리지 마라.** `AdminStageResultView.vue`의 `import type { AdminJobPostingListItem } from '@/types/jobPosting'`와 그 위 주석 블록. 동명 타입 중복을 정리하는 작업이 별도 세션에서 돌고 있다. 그 줄이 바뀌어 있으면 그대로 두고 진행하고, type-check가 그 줄 때문에 실패하면 고치지 말고 보고한다.

---

## 백엔드 계약 (S1에서 확정 — 변경 없음)

경로는 모두 `/admin/job-postings/{jobPostingId}/stages` 아래다.

| 메서드 | 경로 | 요청 | 응답 `data` | 가드 |
| --- | --- | --- | --- | --- |
| POST | (base) | `StageCreateRequest` | `number` (stageId) | 공고 CLOSED 불가. 순서 중복 불가. 최종 단계 중복 불가 |
| POST | `/{stageId}` | `StageUpdateRequest` | `number` | 아래 §"수정 가드" 참조 |
| POST | `/{stageId}/delete` | 없음 | `number` | 공고 CLOSED 불가 + 단계 **READY만** |
| POST | `/reorder` | `StageReorderRequest` | `StageListResponse[]` | 공고 CLOSED 불가 + **전 단계 포함** + **전 단계 READY** |

**요청 DTO (create·update 동일 모양):**

```
{ stageName: string, stageType: StageType, stageOrder: number, resultAnnouncementDateTime: string | null, finalStage: boolean }
```

`stageName` `@NotBlank`, `stageType` `@NotNull`, `stageOrder` `@NotNull @Min(0)`. `resultAnnouncementDateTime`만 선택.

**`StageReorderRequest`:** `{ items: [{ stageId: number, stageOrder: number }] }` — `@NotEmpty`.

**수정 가드 (계약 변경 2 — 이 슬라이스가 프론트에 반영한다):**

| 단계 상태 | 허용 |
| --- | --- |
| READY | 전체 필드 수정. 순서 중복·최종단계 유일성 재검증 |
| IN_PROGRESS | **`resultAnnouncementDateTime`만** 변경 가능. 나머지 4개가 현재 값과 다르면 400 `In progress stage allows changing resultAnnouncementDateTime only.` |
| RESULT_ANNOUNCED·CLOSED | 400 `Only READY stage can be changed.` |

**잠긴 필드도 `@NotBlank`/`@NotNull` 검증을 통과해야 한다.** 형식 검증이 완화 분기보다 먼저 돌기 때문이다. 즉 **진행 중 단계를 수정할 때 잠긴 4개 필드에 현재 값을 그대로 실어 보내야 한다.** 비우면 400이다.

**reorder의 제약이 강하다:** 공고의 **모든 단계를 빠짐없이** 보내야 하고(`requestedIds.size() != stages.size()`면 400), **하나라도 READY가 아니면** 400 `Only READY stages can be reordered.`다. 순서 값 중복도 400.

---

## 파일 구조

| 파일 | 역할 | 변경 |
| --- | --- | --- |
| `src/types/admin/stage.ts` | 단계 CRUD 요청 타입, 유형 라벨 | 수정 |
| `src/api/admin/adminStageApi.ts` | 단계 CRUD 메서드 4개 | 수정 |
| `src/views/admin/stageResult/StageConfigDrawer.vue` | 단계 설정 드로어 | 신규 |
| `src/views/admin/stageResult/StageStepper.vue` | 설정 버튼 추가 | 수정 |
| `src/views/admin/stageResult/AdminStageResultView.vue` | 드로어 배선, 빈 상태 버튼 | 수정 |
| `src/views/admin/jobPosting/AdminJobPostingDetailView.vue` | "전형 단계" 링크 | 수정 |
| `src/views/admin/applicationForm/AdminApplicationFormListView.vue` | "전형 단계" 링크 | 수정 |
| `api-contract.md` | 🟢 확정 | 수정 |
| `docs/superpowers/specs/2026-09-04-admin-stage-result-management-design.md` | S4 기록 | 수정 |

---

### Task 1: 타입 + API 확장

**Files:**
- Modify: `src/types/admin/stage.ts` (끝에 추가)
- Modify: `src/api/admin/adminStageApi.ts`

- [ ] **Step 1: 타입 추가**

`src/types/admin/stage.ts` 끝에 추가한다. 기존 내용은 건드리지 않는다.

```ts
/* ---- 단계 설정 (S4) ---- */

/**
 * 단계 생성·수정 요청. 백엔드가 두 요청에 같은 모양을 쓴다.
 *
 * 진행 중(IN_PROGRESS) 단계를 수정할 때도 잠긴 4개 필드를 **현재 값 그대로** 실어야 한다 —
 * 형식 검증(@NotBlank/@NotNull)이 완화 분기보다 먼저 돌아서 비우면 400 이다(계약 변경 2).
 */
export interface StageSaveRequest {
  stageName: string
  stageType: StageType
  stageOrder: number
  /** 발표 예정 일시. 미정이면 null */
  resultAnnouncementDateTime: string | null
  finalStage: boolean
}

/**
 * 순서 일괄 변경 요청. **공고의 모든 단계를 빠짐없이** 보내야 하고,
 * 하나라도 READY 가 아니면 백엔드가 400 으로 거부한다.
 */
export interface StageReorderRequest {
  items: { stageId: number; stageOrder: number }[]
}

export const STAGE_TYPE_LABELS: Record<StageType, string> = {
  DOCUMENT: '서류',
  FIRST_INTERVIEW: '1차 면접',
  SECOND_INTERVIEW: '2차 면접',
  FINAL_INTERVIEW: '최종 면접',
  ETC: '기타',
}

/** 드로어가 새 단계에 부여하는 순서 간격. 중간 삽입 여지를 남긴다(설계 §3.6). */
export const STAGE_ORDER_STEP = 10
```

- [ ] **Step 2: API 메서드 4개 추가**

`adminStageApi.ts`의 `closeStage` 아래(라이프사이클 묶음 뒤)에 추가한다. import에 새 타입을 더한다.

```ts
  /* ---- 단계 설정 ---- */

  /** 단계 생성. 공고가 마감이면 거부된다. 순서·최종단계 중복도 거부된다. */
  createStage(jobPostingId: number, request: StageSaveRequest) {
    return apiClient.post<ApiResponse<number>>(
      `/admin/job-postings/${jobPostingId}/stages`,
      request,
    )
  },

  /**
   * 단계 수정. READY 는 전체, IN_PROGRESS 는 발표일시만 허용된다(계약 변경 2).
   * 잠긴 필드도 형식 검증을 통과해야 하므로 현재 값을 그대로 실어 보낸다.
   */
  updateStage(jobPostingId: number, stageId: number, request: StageSaveRequest) {
    return apiClient.post<ApiResponse<number>>(
      `/admin/job-postings/${jobPostingId}/stages/${stageId}`,
      request,
    )
  },

  /** 단계 삭제. READY 단계만 지울 수 있다. */
  deleteStage(jobPostingId: number, stageId: number) {
    return apiClient.post<ApiResponse<number>>(
      `/admin/job-postings/${jobPostingId}/stages/${stageId}/delete`,
    )
  },

  /** 순서 일괄 변경. 모든 단계를 보내야 하고 전부 READY 여야 한다. */
  reorderStages(jobPostingId: number, request: StageReorderRequest) {
    return apiClient.post<ApiResponse<StageListItem[]>>(
      `/admin/job-postings/${jobPostingId}/stages/reorder`,
      request,
    )
  },
```

- [ ] **Step 3: 검증**

```bash
npm run type-check
npx eslint src/types/admin/stage.ts src/api/admin/adminStageApi.ts
```

---

### Task 2: 단계 설정 드로어

**Files:**
- Create: `src/views/admin/stageResult/StageConfigDrawer.vue`

설계 §3.6·§4.8, 시안의 "단계 설정 드로어" 탭.

**편집 모델:** 드로어를 열 때 서버 단계 목록을 자체 배열로 복사한다. 사용자가 행을 고치거나 추가·삭제해도 서버에 바로 보내지 않고, "저장"을 누를 때 원본과 비교해 필요한 API만 호출한다. 부분 실패가 가능한 구조라(생성 3건 중 2건째 실패) 저장 결과를 정직하게 보고해야 한다.

**행 잠금 규칙:**

| 단계 상태 | 이름·유형·최종단계 | 발표일시 | 순서 | 삭제 |
| --- | --- | --- | --- | --- |
| READY | O | O | O(아래 규칙) | O |
| IN_PROGRESS | X | **O** | X | X |
| RESULT_ANNOUNCED·CLOSED | X | X | X | X |
| 신규 행 | O | O | 맨 뒤 고정 | O(목록에서 제거) |

공고가 CLOSED면 드로어 전체가 읽기 전용이다.

**순서 변경 규칙(백엔드 reorder 제약 때문):**
- **모든 단계가 READY**일 때만 위/아래 이동 버튼이 활성. 저장 시 순서를 10 단위로 다시 매겨 `reorder` 1회.
- 시작된 단계가 하나라도 있으면 이동 버튼 비활성. 대신 READY 행에 **"맨 뒤로"** 버튼을 두고 `update`(`stageOrder = max + 10`)로 처리한다.

드래그 대신 위/아래 버튼을 쓴다. 드래그는 라이브러리가 필요하고(`AGENTS.md`가 새 의존성 추가를 막는다), 단계가 3~5개라 버튼으로 충분하다.

- [ ] **Step 1: 컴포넌트 작성**

`src/views/admin/stageResult/StageConfigDrawer.vue`:

```vue
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { adminStageApi } from '@/api/admin/adminStageApi'
import { getApiErrorMessage } from '@/api/apiError'
import {
  STAGE_ORDER_STEP,
  STAGE_STATUS_LABELS,
  STAGE_TYPE_LABELS,
  type StageListItem,
  type StageSaveRequest,
  type StageStatus,
  type StageType,
} from '@/types/admin/stage'

/** 드로어가 편집하는 한 행. 신규 행은 id 가 null 이다. */
interface StageDraft {
  id: number | null
  stageName: string
  stageType: StageType | null
  resultAnnouncementDateTime: string | null
  finalStage: boolean
  /** 서버 상태. 신규 행은 READY 로 취급한다. */
  status: StageStatus
  /** 원본 스냅샷. 변경 여부 판정에 쓴다. 신규 행은 null. */
  original: StageListItem | null
}

const props = defineProps<{
  open: boolean
  jobPostingId: number | null
  stages: StageListItem[]
  /** 공고가 마감이면 전체 읽기 전용이다. */
  jobPostingClosed: boolean
}>()

const emit = defineEmits<{
  (event: 'update:open', open: boolean): void
  /** 저장 성공(부분 성공 포함). 본체가 단계·결과를 재조회한다. */
  (event: 'saved'): void
}>()

const drafts = ref<StageDraft[]>([])
const saving = ref(false)

const typeOptions = (Object.keys(STAGE_TYPE_LABELS) as StageType[]).map((type) => ({
  value: type,
  label: STAGE_TYPE_LABELS[type],
}))

/** 백엔드 reorder 는 전 단계가 READY 일 때만 허용한다. 하나라도 시작됐으면 순서 이동을 막는다. */
const reorderable = computed(
  () => !props.jobPostingClosed && drafts.value.every((draft) => draft.status === 'READY'),
)

const readOnly = computed(() => props.jobPostingClosed)

const toDraft = (stage: StageListItem): StageDraft => ({
  id: stage.id,
  stageName: stage.stageName,
  stageType: stage.stageType,
  resultAnnouncementDateTime: stage.resultAnnouncementDateTime,
  finalStage: stage.finalStage,
  status: stage.status,
  original: stage,
})

/** 드로어를 열 때마다 서버 목록을 새로 복사한다. 이전 편집이 남아 있으면 안 된다. */
watch(
  () => [props.open, props.stages] as const,
  ([open]) => {
    if (open) {
      drafts.value = props.stages.map(toDraft)
    }
  },
  { immediate: true },
)

const nameEditable = (draft: StageDraft) => !readOnly.value && draft.status === 'READY'

/** 진행 중 단계에서 유일하게 열려 있는 필드다(계약 변경 2). */
const announcementEditable = (draft: StageDraft) =>
  !readOnly.value && (draft.status === 'READY' || draft.status === 'IN_PROGRESS')

const deletable = (draft: StageDraft) => !readOnly.value && draft.status === 'READY'

const addDraft = () => {
  drafts.value = [
    ...drafts.value,
    {
      id: null,
      stageName: '',
      stageType: null,
      resultAnnouncementDateTime: null,
      finalStage: false,
      status: 'READY',
      original: null,
    },
  ]
}

const removeDraft = (index: number) => {
  const draft = drafts.value[index]
  if (draft === undefined) {
    return
  }
  if (draft.id === null) {
    // 아직 저장 전인 행은 목록에서 빼면 끝이다.
    drafts.value = drafts.value.filter((_, i) => i !== index)
    return
  }
  Modal.confirm({
    title: '이 단계를 삭제할까요?',
    content: `"${draft.stageName}" 단계를 지웁니다. 저장을 눌러야 실제로 반영됩니다.`,
    okText: '삭제',
    cancelText: '취소',
    onOk: () => {
      drafts.value = drafts.value.filter((_, i) => i !== index)
    },
  })
}

const move = (index: number, delta: number) => {
  const next = index + delta
  const list = [...drafts.value]
  const current = list[index]
  const target = list[next]
  if (current === undefined || target === undefined) {
    return
  }
  list[index] = target
  list[next] = current
  drafts.value = list
}

/** 시작된 단계가 있어 순서 이동이 막힌 상황에서 READY 행만 맨 뒤로 보낸다. */
const moveToEnd = (index: number) => {
  const list = [...drafts.value]
  const [moved] = list.splice(index, 1)
  if (moved === undefined) {
    return
  }
  drafts.value = [...list, moved]
}

/** 최종 단계는 하나뿐이다. 체크하면 나머지를 해제한다(백엔드도 검증). */
const setFinalStage = (index: number, checked: boolean) => {
  drafts.value = drafts.value.map((draft, i) => ({
    ...draft,
    finalStage: i === index ? checked : checked ? false : draft.finalStage,
  }))
}

/** 저장 전 형식 검증. 백엔드 @NotBlank/@NotNull 과 같은 항목을 본다. */
const validate = (): string | null => {
  for (const [index, draft] of drafts.value.entries()) {
    if (draft.stageName.trim().length === 0) {
      return `${index + 1}번째 단계의 이름을 입력하세요.`
    }
    if (draft.stageType === null) {
      return `${index + 1}번째 단계의 유형을 선택하세요.`
    }
  }
  return null
}

const toSaveRequest = (draft: StageDraft, stageOrder: number): StageSaveRequest => ({
  stageName: draft.stageName.trim(),
  stageType: draft.stageType as StageType,
  stageOrder,
  resultAnnouncementDateTime: draft.resultAnnouncementDateTime,
  finalStage: draft.finalStage,
})

/** 원본과 달라진 행만 update 대상이다. 순서는 따로 판정한다. */
const fieldsChanged = (draft: StageDraft): boolean => {
  const origin = draft.original
  if (origin === null) {
    return true
  }
  return (
    draft.stageName.trim() !== origin.stageName ||
    draft.stageType !== origin.stageType ||
    draft.resultAnnouncementDateTime !== origin.resultAnnouncementDateTime ||
    draft.finalStage !== origin.finalStage
  )
}

const close = () => {
  emit('update:open', false)
}

/*
 * 저장은 삭제 → 수정 → 생성 → 순서 순으로 돈다.
 * 삭제를 먼저 해야 순서·최종단계 중복 검증에 걸리지 않고, 생성을 나중에 해야 새 행이 맨 뒤 순서를 받는다.
 * 부분 실패가 가능한 구조라 성공한 건수와 실패 사유를 함께 보고한다.
 */
const save = async () => {
  if (props.jobPostingId === null) {
    return
  }
  const invalid = validate()
  if (invalid !== null) {
    message.warning(invalid)
    return
  }

  const jobPostingId = props.jobPostingId
  const keptIds = new Set(drafts.value.map((draft) => draft.id).filter((id): id is number => id !== null))
  const removed = props.stages.filter((stage) => !keptIds.has(stage.id))

  saving.value = true
  let applied = 0
  try {
    for (const stage of removed) {
      await adminStageApi.deleteStage(jobPostingId, stage.id)
      applied += 1
    }

    // 순서는 화면 배열 순서를 10 단위로 다시 매긴다. 중간 삽입 여지를 남긴다.
    for (const [index, draft] of drafts.value.entries()) {
      const stageOrder = (index + 1) * STAGE_ORDER_STEP
      if (draft.id === null) {
        await adminStageApi.createStage(jobPostingId, toSaveRequest(draft, stageOrder))
        applied += 1
        continue
      }
      const orderChanged = reorderable.value ? false : draft.original?.stageOrder !== stageOrder
      if (fieldsChanged(draft) || orderChanged) {
        // 진행 중 단계는 발표일시만 바뀌므로, 잠긴 필드는 현재 값이 그대로 실린다.
        const keepOrder = draft.original?.stageOrder ?? stageOrder
        await adminStageApi.updateStage(
          jobPostingId,
          draft.id,
          toSaveRequest(draft, reorderable.value ? keepOrder : stageOrder),
        )
        applied += 1
      }
    }

    if (reorderable.value) {
      // 전 단계가 READY 일 때만 순서 일괄 변경이 허용된다. 모든 단계를 빠짐없이 보낸다.
      const items = drafts.value
        .map((draft, index) => ({ stageId: draft.id, stageOrder: (index + 1) * STAGE_ORDER_STEP }))
        .filter((item): item is { stageId: number; stageOrder: number } => item.stageId !== null)
      if (items.length > 0) {
        await adminStageApi.reorderStages(jobPostingId, { items })
      }
    }

    message.success('전형 단계를 저장했습니다.')
    emit('saved')
    close()
  } catch (error) {
    // 앞선 요청은 이미 반영됐다. 사용자가 무엇이 남았는지 알 수 있게 건수를 함께 알린다.
    message.error(
      `${getApiErrorMessage(error, '전형 단계를 저장하지 못했습니다.')} (${applied}건은 반영됨)`,
    )
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <a-drawer
    :open="open"
    title="전형 단계 설정"
    width="560"
    @update:open="(next: boolean) => emit('update:open', next)"
  >
    <a-alert
      class="notice"
      :type="readOnly ? 'info' : 'warning'"
      show-icon
      :message="
        readOnly
          ? '마감된 공고라 전형 단계를 바꿀 수 없습니다.'
          : reorderable
            ? '아직 시작된 단계가 없어 순서를 바꿀 수 있습니다.'
            : '시작된 단계가 있어 순서 이동은 막혀 있습니다. 대기 단계는 맨 뒤로만 보낼 수 있고, 시작된 단계는 발표일시만 바꿀 수 있습니다.'
      "
    />

    <div v-for="(draft, index) in drafts" :key="draft.id ?? `new-${index}`" class="stage-row">
      <div class="row-head">
        <a-input
          v-model:value="draft.stageName"
          :disabled="!nameEditable(draft)"
          placeholder="단계 이름"
          class="name-input"
        />
        <a-tag>{{ draft.id === null ? '신규' : STAGE_STATUS_LABELS[draft.status] }}</a-tag>
      </div>

      <div class="row-fields">
        <label>
          <span class="field-label">유형</span>
          <a-select
            v-model:value="draft.stageType"
            :options="typeOptions"
            :disabled="!nameEditable(draft)"
            placeholder="선택"
          />
        </label>
        <label>
          <span class="field-label">발표일시</span>
          <a-date-picker
            v-model:value="draft.resultAnnouncementDateTime"
            show-time
            value-format="YYYY-MM-DDTHH:mm:ss"
            :disabled="!announcementEditable(draft)"
          />
        </label>
        <label class="final-field">
          <span class="field-label">최종 단계</span>
          <a-switch
            :checked="draft.finalStage"
            :disabled="!nameEditable(draft)"
            size="small"
            @change="(checked: boolean | string | number) => setFinalStage(index, checked === true)"
          />
        </label>
      </div>

      <div class="row-actions">
        <template v-if="reorderable">
          <a-button size="small" :disabled="index === 0" @click="move(index, -1)">↑</a-button>
          <a-button size="small" :disabled="index === drafts.length - 1" @click="move(index, 1)">↓</a-button>
        </template>
        <a-button
          v-else-if="draft.status === 'READY' && index !== drafts.length - 1"
          size="small"
          :disabled="readOnly"
          @click="moveToEnd(index)"
        >
          맨 뒤로
        </a-button>
        <span class="actions-spacer" />
        <a-button size="small" danger :disabled="!deletable(draft)" @click="removeDraft(index)">
          삭제
        </a-button>
      </div>
    </div>

    <a-button v-if="!readOnly" class="add-button" block @click="addDraft">+ 단계 추가</a-button>

    <a-empty v-if="drafts.length === 0" description="등록된 전형 단계가 없습니다." />

    <template #footer>
      <a-button @click="close">닫기</a-button>
      <a-button type="primary" :disabled="readOnly" :loading="saving" @click="save">저장</a-button>
    </template>
  </a-drawer>
</template>

<style scoped lang="scss">
.notice {
  margin-bottom: 12px;
}

.stage-row {
  padding: 12px;
  margin-bottom: 10px;
  background: var(--app-bg-surface);
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius);
}

.row-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.name-input {
  flex: 1;
}

.row-fields {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 8px;
  margin-bottom: 10px;

  label {
    display: flex;
    flex-direction: column;
    gap: 3px;
    min-width: 0;
  }
}

.field-label {
  font-size: 11px;
  color: var(--app-text-muted);
}

.final-field {
  align-items: flex-start;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.actions-spacer {
  flex: 1;
}

.add-button {
  margin-top: 4px;
}
</style>
```

- [ ] **Step 2: 검증**

```bash
npm run type-check
npx eslint src/views/admin/stageResult/StageConfigDrawer.vue
```

타입 오류가 나면 `a-date-picker`의 `v-model:value`가 문자열을 받는지 확인한다. `ApplicationStatus.vue`가 `value-format="YYYY-MM-DD"`로 같은 패턴을 쓰니 참고한다. `a-switch`의 `@change` 파라미터 타입도 확인한다. **`any`·`@ts-ignore`·`eslint-disable`을 쓰지 않는다.**

---

### Task 3: 스텝퍼에 설정 버튼

**Files:**
- Modify: `src/views/admin/stageResult/StageStepper.vue`

설계 §4.2가 요구하는 우측 끝 설정 버튼이다. S2에서 뒤로 미뤘다.

- [ ] **Step 1: emit 추가와 버튼 배치**

emit에 추가:

```ts
  (event: 'open-config'): void
```

`<nav>` 안, `v-for` 버튼들 **뒤에** 설정 버튼을 넣는다. 스텝과 시각적으로 구분되게 별도 클래스를 준다.

```vue
    <button type="button" class="step-config" @click="emit('open-config')">단계 설정</button>
```

스타일:

```scss
.step-config {
  flex: 0 0 auto;
  padding: 10px 14px;
  border: none;
  border-left: 1px solid var(--app-border-default);
  background: var(--app-bg-muted);
  color: var(--app-text-secondary);
  font-size: 12.5px;
  cursor: pointer;
  white-space: nowrap;

  &:hover {
    background: var(--app-bg-selected);
    color: var(--app-color-primary);
  }
}
```

- [ ] **Step 2: 검증**

```bash
npm run type-check
```

---

### Task 4: 본체 배선

**Files:**
- Modify: `src/views/admin/stageResult/AdminStageResultView.vue`

- [ ] **Step 1: 상태와 핸들러**

import에 `StageConfigDrawer`를 더한다.

```ts
const configDrawerOpen = ref(false)

/** 공고가 마감이면 단계 설정 전체가 읽기 전용이다(백엔드 StageService.validateJobPostingEditable). */
const jobPostingClosed = computed(() => selectedJobPosting.value?.status === 'CLOSED')

const openConfigDrawer = async () => {
  // 드로어 저장이 단계·결과 재조회를 부르므로 편집 버퍼와 충돌한다.
  if (!(await confirmDiscardIfDirty())) {
    return
  }
  configDrawerOpen.value = true
}
```

- [ ] **Step 2: 스텝퍼와 빈 상태 연결**

`StageStepper` 태그에 `@open-config="openConfigDrawer"`를 더한다.

단계 0건 빈 상태(실패가 아닌 쪽)의 임시 문구를 실제 버튼으로 바꾼다. S2가 "단계 설정 화면은 준비 중입니다."로 남겨 둔 자리다.

```vue
        <p class="empty-hint">서류전형 → 면접 순서로 단계를 만들면 지원자 결과를 판정할 수 있습니다.</p>
        <a-button type="primary" @click="openConfigDrawer">전형 단계 설정</a-button>
```

- [ ] **Step 3: 드로어 배치**

다른 모달 둘 옆에 넣는다.

```vue
    <StageConfigDrawer
      v-model:open="configDrawerOpen"
      :job-posting-id="selectedJobPostingId"
      :stages="stages"
      :job-posting-closed="jobPostingClosed"
      @saved="reloadStageAndResults"
    />
```

- [ ] **Step 4: 검증**

```bash
npm run type-check
npm run build-only
npx eslint src/views/admin/stageResult/
```

---

### Task 5: 절충 링크 두 곳

**Files:**
- Modify: `src/views/admin/jobPosting/AdminJobPostingDetailView.vue`
- Modify: `src/views/admin/applicationForm/AdminApplicationFormListView.vue`

설계 §4.10. 두 화면에서 해당 공고의 전형결과 관리로 바로 갈 수 있게 한다. **화면을 만들지 않고 진입점만 추가한다.**

- [ ] **Step 1: 공고 상세에 버튼**

`AdminJobPostingDetailView.vue`의 `goToApplicationForm` 아래에 추가:

```ts
const goToStageResults = () => {
  void router.push({ name: 'AdminStageResult', query: { jobPostingId: String(postingId.value) } })
}
```

`.header-actions`의 "지원서 설정" 버튼 **뒤에** 배치:

```vue
            <a-button @click="goToStageResults">전형 단계</a-button>
```

- [ ] **Step 2: 지원서 설정 현황판에 행 액션**

`AdminApplicationFormListView.vue`는 행 전체 클릭으로 상세에 가는 구조다(`custom-row`). 행 클릭을 방해하지 않게 **열을 하나 추가**하고 버튼의 클릭 전파를 막는다.

`columns` 끝에 추가:

```ts
  { title: '', key: 'stageResults', width: 100 },
```

`goToDetail` 아래에 추가:

```ts
const goToStageResults = (row: AdminApplicationFormSummary) => {
  void router.push({ name: 'AdminStageResult', query: { jobPostingId: String(row.jobPostingId) } })
}
```

`#bodyCell`에 추가(파일에 `#bodyCell` 슬롯이 이미 있으면 분기를 더하고, 없으면 슬롯을 만든다):

```vue
        <template v-else-if="column.key === 'stageResults'">
          <!-- 행 클릭이 지원서 설정 상세로 가므로 버튼 클릭은 전파를 막는다. -->
          <a-button size="small" @click.stop="goToStageResults(record as AdminApplicationFormSummary)">
            전형 단계
          </a-button>
        </template>
```

`record`가 느슨한 타입이라 단언이 필요하면, 이 파일의 기존 `configStateOf` 헬퍼처럼 **필드만 넘기는 헬퍼**로 바꾼다. `jobPostingId`만 있으면 되므로 `goToStageResults(record.jobPostingId)` 형태가 더 깔끔하다. **선택한 방식을 보고한다.**

- [ ] **Step 3: 검증**

```bash
npm run type-check
npm run build-only
npx eslint src/views/admin/jobPosting/AdminJobPostingDetailView.vue src/views/admin/applicationForm/AdminApplicationFormListView.vue
```

두 화면의 기존 동작이 깨지지 않았는지 확인한다. 특히 현황판의 행 클릭이 여전히 지원서 설정 상세로 가야 한다.

---

### Task 6: 계약 확정과 문서 갱신

**Files:**
- Modify: `api-contract.md`
- Modify: `docs/superpowers/specs/2026-09-04-admin-stage-result-management-design.md`

- [ ] **Step 1: 계약 🟢 확정**

"전형결과 관리" 섹션에서:
- 섹션 헤더를 `🟢 확정 (2026-09-04, front-back 반영 완료)`로
- 진행 상황 줄을 S1~S4 완료로 갱신
- 변경 1·2·3의 상태 라벨을 모두 🟢로

**변경 2의 프론트 반영 내용을 한 줄 추가한다** — 드로어가 진행 중 단계에서 발표일시만 열고, 잠긴 4개 필드에 현재 값을 그대로 실어 보낸다는 사실. 이게 이 슬라이스가 계약에 실제로 반영한 것이다.

- [ ] **Step 2: 설계서 갱신**

§8 슬라이스 표의 S4 행을 완료로 바꾸고, §13 뒤에 `## 14. S4에서 내린 결정`을 추가한다. **구현 중 실제로 내린 결정으로 채운다.** 최소한 아래는 담는다.

- 드래그 대신 위/아래 버튼을 쓴 이유(새 의존성 금지 + 단계 3~5개)
- 순서 변경이 두 갈래인 이유(백엔드 reorder가 전 단계 READY를 요구)
- 저장 순서(삭제 → 수정 → 생성 → 순서)와 그 근거
- 부분 실패를 어떻게 보고하는지
- 드로어 마스크를 켠 채로 둔 이유(S3가 남긴 전제)

`### S4가 남긴 것` 절도 만들어 후속 작업에 넘길 사실을 적는다.

- [ ] **Step 3: 메뉴 등록 안내**

설계서 §6에 이미 있는 내용이지만, 최종 보고에 반드시 포함한다. `/admin/stage-results`를 관리자 메뉴 관리 화면에서 등록해야 사이드바에 뜬다. 코드 변경이 아니라 운영 작업이다.

---

### Task 7: HTML 리포트

**Files:**
- Create/Modify: `design-report` 스킬이 정하는 경로

전체 구현(S1~S4)이 끝났으므로 **이 시점에 리포트를 1회 만든다.** 사용자가 "최종 구현 완료 뒤에 1회만"이라고 지시했다.

- [ ] **Step 1: `design-report` 스킬 호출**

Skill 도구로 `design-report`를 호출하고 그 스킬의 지시를 따른다. 리포트 구조·스타일·파일명·필수 섹션을 손으로 만들지 않는다.

리포트에 담을 범위:
- 설계 결정(as-is 서브메뉴 4개 → 공고·단계 단일 화면, 화면 내 판정, 엑셀 보조)
- 슬라이스 4개의 산출물과 검증 결과
- 백엔드 변경 3건 + 부분 판정 업로드
- 프론트 화면 구성
- 계약 확정 내용
- 알려진 한계와 후속 작업

- [ ] **Step 2: 사용자에게 전달**

`SendUserFile`로 리포트를 보낸다.

---

## 자체 검토 결과

- 설계서 §3.6 단계 설정 → Task 2, §4.2 스텝퍼 설정 버튼 → Task 3, §4.8 드로어 → Task 2, §4.9 빈 상태 버튼 → Task 4, §4.10 절충 링크 → Task 5, §5.2 수정 완화 프론트 반영 → Task 1·2, 계약 🟢 → Task 6.
- 설계서 §11·§13의 S4 관련 항목 반영: 드로어 마스크 유지(계획 상단 전제 1), `reloadStageAndResults` 재사용(Task 4), `confirmDiscardIfDirty` 선행 호출(Task 4), 단계 CRUD API 신규 추가(Task 1), 잠긴 필드 현재 값 전송(Task 1·2), 단계 조회 실패와 미존재 구분은 S3가 이미 처리.
- Task 간 타입 일치: `StageSaveRequest`·`StageReorderRequest`·`STAGE_TYPE_LABELS`·`STAGE_ORDER_STEP`은 Task 1이 정의하고 Task 2가 쓴다. `open-config` emit은 Task 3이 만들고 Task 4가 받는다. `AdminStageResult` 라우트 이름은 S2가 만든 것을 Task 5가 쓴다.
- 순서 의존: Task 1 → 2 → 3 → 4 → 5 → 6 → 7. Task 5는 Task 4와 독립이지만 라우트가 이미 있으므로 언제든 가능하다.
- **이 슬라이스로 화면이 완결된다.** 남는 것은 메뉴 등록(운영 작업)과 설계서에 기록한 알려진 한계뿐이다.
