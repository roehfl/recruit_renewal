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
  /**
   * 서버에 보낼 순서 값. 기존 행은 원본 값을 그대로 들고 있고, 신규 행과 "맨 뒤로" 행만 max + STEP 을 받는다.
   * 화면 위치로 매번 다시 매기면 손대지 않은(시작된) 단계까지 순서가 바뀐 것으로 판정돼 400 이 난다.
   */
  stageOrder: number
  /** 원본 스냅샷. 변경 여부 판정에 쓴다. 신규 행은 null. */
  original: StageListItem | null
}

/** id 가 있는 행 = 서버에 존재하는 단계. filter 로 좁혀 non-null 단언을 피한다. */
const hasId = (draft: StageDraft): draft is StageDraft & { id: number } => draft.id !== null

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

const readOnly = computed(() => props.jobPostingClosed)

/** 백엔드 reorder 는 전 단계가 READY 일 때만 허용한다. 하나라도 시작됐으면 순서 이동을 막는다. */
const reorderable = computed(
  () => !readOnly.value && drafts.value.every((draft) => draft.status === 'READY'),
)

/**
 * 최종 단계는 정확히 하나여야 한다. 0개거나 여러 개면 백엔드
 * RetentionEligibilityService 가 INVALID_STAGE_CONFIGURATION 으로 판정해
 * 이 공고의 지원서가 개인정보 파기 대상에서 영구 제외된다. 화면에 드러나지 않는 실패라 미리 경고한다.
 */
const finalStageCount = computed(() => drafts.value.filter((draft) => draft.finalStage).length)

const statusLabel = (status: StageStatus) => STAGE_STATUS_LABELS[status]

const toDraft = (stage: StageListItem): StageDraft => ({
  id: stage.id,
  stageName: stage.stageName,
  stageType: stage.stageType,
  resultAnnouncementDateTime: stage.resultAnnouncementDateTime,
  finalStage: stage.finalStage,
  status: stage.status,
  stageOrder: stage.stageOrder,
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

/** 이름·유형·최종단계는 대기 단계에서만 고칠 수 있다(백엔드 validateStageEditable). */
const fieldsEditable = (draft: StageDraft) => !readOnly.value && draft.status === 'READY'

/** 진행 중 단계에서 유일하게 열려 있는 필드다(계약 변경 2). */
const announcementEditable = (draft: StageDraft) =>
  !readOnly.value && (draft.status === 'READY' || draft.status === 'IN_PROGRESS')

const deletable = (draft: StageDraft) => !readOnly.value && draft.status === 'READY'

/** 아직 아무도 쓰지 않은 순서 값. 신규 행과 "맨 뒤로" 행이 이 값을 받아 순서 중복(400)을 피한다. */
const nextOrder = () =>
  drafts.value.reduce((max, draft) => Math.max(max, draft.stageOrder), 0) + STAGE_ORDER_STEP

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
      stageOrder: nextOrder(),
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

/**
 * 시작된 단계가 있어 순서 이동이 막힌 상황에서 READY 행만 맨 뒤로 보낸다.
 * reorder 를 쓸 수 없으므로 그 행의 순서 값만 max + STEP 으로 올려 update 로 반영한다.
 */
const moveToEnd = (index: number) => {
  const list = [...drafts.value]
  const moved = list[index]
  if (moved === undefined) {
    return
  }
  list.splice(index, 1)
  drafts.value = [...list, { ...moved, stageOrder: nextOrder() }]
}

/**
 * 최종 단계는 공고에 하나뿐이다(백엔드 validateFinalStage*).
 * 잠긴 행이 최종 단계를 쥐고 있으면 그 행을 고칠 수 없어 옮길 방법이 없으므로 토글 자체를 막는다.
 */
const setFinalStage = (index: number, checked: boolean) => {
  const lockedHolder = drafts.value.find(
    (draft, i) => i !== index && draft.finalStage && !fieldsEditable(draft),
  )
  if (checked && lockedHolder !== undefined) {
    message.warning(
      `"${lockedHolder.stageName}" 단계가 최종 단계입니다. 대기 상태가 아니라 최종 단계를 옮길 수 없습니다.`,
    )
    return
  }
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

const toSaveRequest = (draft: StageDraft): StageSaveRequest => ({
  stageName: draft.stageName.trim(),
  stageType: draft.stageType as StageType,
  stageOrder: draft.stageOrder,
  resultAnnouncementDateTime: draft.resultAnnouncementDateTime,
  finalStage: draft.finalStage,
})

/** 원본과 달라진 행만 update 대상이다. 진행 중 단계는 발표일시만 달라질 수 있다. */
const isChanged = (draft: StageDraft): boolean => {
  const origin = draft.original
  if (origin === null) {
    return true
  }
  return (
    draft.stageName.trim() !== origin.stageName ||
    draft.stageType !== origin.stageType ||
    draft.stageOrder !== origin.stageOrder ||
    draft.resultAnnouncementDateTime !== origin.resultAnnouncementDateTime ||
    draft.finalStage !== origin.finalStage
  )
}

/** 최종 단계 플래그를 내려놓는 행. 먼저 보내지 않으면 뒤 행이 "최종 단계 중복"으로 거부된다. */
const releasesFinalStage = (draft: StageDraft) =>
  draft.original?.finalStage === true && !draft.finalStage

/** 화면 순서와 저장된 순서가 어긋난 상태. 이미 오름차순이면 reorder 를 보낼 이유가 없다. */
const orderOutOfSync = (list: StageDraft[]) =>
  list.some((draft, index) => {
    const previous = list[index - 1]
    return previous !== undefined && draft.stageOrder <= previous.stageOrder
  })

/**
 * 저장하지 않은 변경 여부. 행 추가·삭제·순서 이동은 길이와 같은 자리의 id 로,
 * 필드 수정은 isChanged 로 본다.
 */
const dirty = computed(() => {
  if (drafts.value.length !== props.stages.length) {
    return true
  }
  return drafts.value.some((draft, index) => {
    const origin = props.stages[index]
    return origin === undefined || draft.id !== origin.id || isChanged(draft)
  })
})

/** 확인 없이 닫는다. 저장이 끝난 뒤(성공·부분 실패)에는 이미 반영됐거나 버려야 할 상태라 되묻지 않는다. */
const closeWithoutConfirm = () => {
  emit('update:open', false)
}

/** 사용자가 닫을 때. 판정 버퍼(confirmDiscardIfDirty)와 같이 미저장 변경을 확인받는다. */
const close = () => {
  if (!dirty.value) {
    closeWithoutConfirm()
    return
  }
  Modal.confirm({
    title: '저장하지 않은 변경이 있습니다',
    content: '드로어를 닫으면 편집한 단계 설정이 사라집니다. 계속할까요?',
    okText: '변경 버리고 닫기',
    cancelText: '취소',
    onOk: () => closeWithoutConfirm(),
  })
}

/*
 * 저장은 삭제 → 수정 → 생성 → 순서 순으로 돈다.
 * 삭제를 먼저 해야 지운 단계의 순서·최종단계 값이 중복 검증에 걸리지 않고,
 * 생성을 수정 뒤에 둬야 기존 행이 내려놓은 최종 단계를 새 행이 받을 수 있다.
 * 부분 실패가 가능한 구조라 성공한 건수와 실패 사유를 함께 보고한다.
 */
const save = async () => {
  if (props.jobPostingId === null || readOnly.value) {
    return
  }
  const invalid = validate()
  if (invalid !== null) {
    message.warning(invalid)
    return
  }

  /*
   * 최종 단계 1개는 백엔드가 강제하지 않는다(유일성만 본다). 여러 번에 나눠 단계를 만드는 흐름이
   * 있어 저장을 막지는 않되, 개인정보 파기 대상에서 빠진다는 사실을 알고 넘어가게 한다.
   */
  if (finalStageCount.value !== 1) {
    const proceed = await new Promise<boolean>((resolve) => {
      Modal.confirm({
        title: '최종 단계 없이 저장할까요?',
        content:
          '최종 단계가 정확히 하나가 아니면 이 공고 지원자의 개인정보 보관 기간이 계산되지 않아 파기 대상에서 빠집니다. 나중에 반드시 지정해야 합니다.',
        okText: '이대로 저장',
        cancelText: '취소',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      })
    })
    if (!proceed) {
      return
    }
  }

  const jobPostingId = props.jobPostingId
  const keptIds = new Set(drafts.value.filter(hasId).map((draft) => draft.id))
  const removed = props.stages.filter((stage) => !keptIds.has(stage.id))

  saving.value = true
  let applied = 0
  try {
    for (const stage of removed) {
      await adminStageApi.deleteStage(jobPostingId, stage.id)
      applied += 1
    }

    const changed = drafts.value.filter(hasId).filter(isChanged)
    const updates = [
      ...changed.filter(releasesFinalStage),
      ...changed.filter((draft) => !releasesFinalStage(draft)),
    ]
    for (const draft of updates) {
      // 진행 중 단계는 발표일시만 바뀌므로, 잠긴 필드는 현재 값이 그대로 실린다.
      await adminStageApi.updateStage(jobPostingId, draft.id, toSaveRequest(draft))
      applied += 1
    }

    for (const draft of drafts.value) {
      if (draft.id !== null) {
        continue
      }
      const response = await adminStageApi.createStage(jobPostingId, toSaveRequest(draft))
      // 받은 id 를 바로 적어 둔다. 뒤따르는 reorder 가 이 단계를 포함해야 하고,
      // 부분 실패 후 다시 저장할 때 같은 단계를 두 번 만들지 않기 위해서다.
      draft.id = response.data.data
      applied += 1
    }

    if (reorderable.value && orderOutOfSync(drafts.value)) {
      // 전 단계가 READY 일 때만 순서 일괄 변경이 허용된다. 모든 단계를 빠짐없이 보낸다.
      const items = drafts.value.filter(hasId).map((draft, index) => ({
        stageId: draft.id,
        stageOrder: (index + 1) * STAGE_ORDER_STEP,
      }))
      if (items.length > 0) {
        await adminStageApi.reorderStages(jobPostingId, { items })
        // 순서 변경도 반영 건수에 넣는다. 빠뜨리면 순서만 바꾼 저장이 "0건 반영"으로 보고된다.
        applied += 1
      }
    }

    message.success('전형 단계를 저장했습니다.')
    emit('saved')
    closeWithoutConfirm()
  } catch (error) {
    // 앞선 요청은 이미 반영됐다. 사용자가 무엇이 남았는지 알 수 있게 건수를 함께 알린다.
    message.error(
      `${getApiErrorMessage(error, '전형 단계를 저장하지 못했습니다.')} (${applied}건은 반영됨)`,
    )
    // 열어 두면 부모 재조회가 편집 내용을 덮고, 재조회까지 실패하면 단계가 없는 것처럼 보인다.
    // 닫고 서버 상태를 다시 보게 하는 편이 정직하다.
    emit('saved')
    closeWithoutConfirm()
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
    :mask-closable="false"
    @update:open="(next: boolean) => (next ? emit('update:open', true) : close())"
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

    <!-- 읽기 전용에서도 보여야 한다. 이미 잘못 설정된 공고를 진단하는 용도로도 쓰인다. -->
    <a-alert
      v-if="drafts.length > 0 && finalStageCount !== 1"
      class="notice"
      type="error"
      show-icon
      :message="
        finalStageCount === 0 ? '최종 단계가 지정되지 않았습니다.' : '최종 단계가 여러 개입니다.'
      "
      description="최종 단계가 정확히 하나여야 지원자의 개인정보 보관 기간이 정상적으로 계산됩니다. 마지막 전형 단계에 최종 단계를 켜 주세요."
    />

    <div v-for="(draft, index) in drafts" :key="draft.id ?? `new-${index}`" class="stage-row">
      <div class="row-head">
        <a-input
          v-model:value="draft.stageName"
          :disabled="!fieldsEditable(draft)"
          placeholder="단계 이름"
          class="name-input"
        />
        <a-tag>{{ draft.id === null ? '신규' : statusLabel(draft.status) }}</a-tag>
      </div>

      <div class="row-fields">
        <label>
          <span class="field-label">유형</span>
          <a-select
            v-model:value="draft.stageType"
            :options="typeOptions"
            :disabled="!fieldsEditable(draft)"
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
            :disabled="!fieldsEditable(draft)"
            size="small"
            @change="(checked: boolean | string | number) => setFinalStage(index, checked === true)"
          />
        </label>
      </div>

      <div class="row-actions">
        <template v-if="reorderable">
          <a-button size="small" :disabled="index === 0" @click="move(index, -1)">↑</a-button>
          <a-button size="small" :disabled="index === drafts.length - 1" @click="move(index, 1)">
            ↓
          </a-button>
        </template>
        <a-button
          v-else-if="!readOnly && draft.status === 'READY' && index !== drafts.length - 1"
          size="small"
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

    <a-empty v-if="drafts.length === 0" description="등록된 전형 단계가 없습니다." />

    <a-button v-if="!readOnly" class="add-button" block @click="addDraft">+ 단계 추가</a-button>

    <template #footer>
      <div class="drawer-footer">
        <a-button :disabled="saving" @click="close">닫기</a-button>
        <a-button type="primary" :disabled="readOnly" :loading="saving" @click="save">저장</a-button>
      </div>
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

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
