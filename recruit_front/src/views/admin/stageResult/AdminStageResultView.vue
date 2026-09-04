<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import axios from 'axios'
import { adminJobPostingApi } from '@/api/adminJobPostingApi'
import { adminStageApi } from '@/api/admin/adminStageApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import { saveBlobResponse } from '@/common/fileDownload'
import StageStepper from './StageStepper.vue'
import StageResultCounts from './StageResultCounts.vue'
import StageResultGrid from './StageResultGrid.vue'
import StageUploadPreviewModal from './StageUploadPreviewModal.vue'
import StageResultCorrectModal from './StageResultCorrectModal.vue'
import StageConfigDrawer from './StageConfigDrawer.vue'
import { useStageLifecycle } from './useStageLifecycle'
/*
 * 공고 목록 아이템 타입은 두 벌이 있다. adminJobPostingApi.getJobPostings 가 실제로 돌려주는 쪽인
 * @/types/jobPosting 를 쓴다(@/types/admin/jobPosting 쪽은 목록 응답에 없는 jobPositions 를 요구한다).
 * 이 화면은 id·title·accepting 만 쓴다.
 */
import type { AdminJobPostingListItem } from '@/types/jobPosting'
import type {
  AdminStageResult,
  PendingEdit,
  StageListItem,
  StageResultBulkUpdateItem,
  StageResultStatus,
} from '@/types/admin/stage'

/** 낙관적 잠금 충돌. 백엔드가 동시 수정에 409 를 준다. */
const isConflict = (error: unknown) => axios.isAxiosError(error) && error.response?.status === 409

const route = useRoute()
const router = useRouter()

const initializing = ref(true)
const loadingResults = ref(false)
const saving = ref(false)

const jobPostings = ref<AdminJobPostingListItem[]>([])
const selectedJobPostingId = ref<number | null>(null)
const stages = ref<StageListItem[]>([])
const selectedStageId = ref<number | null>(null)
const results = ref<AdminStageResult[]>([])
const statusFilter = ref<StageResultStatus | null>(null)
const gridRef = ref<InstanceType<typeof StageResultGrid> | null>(null)
/** 결과 조회 실패 여부. 빈 상태에서 "미조회"와 "로드 실패"를 갈라 보여주기 위해 둔다. */
const resultsLoadFailed = ref(false)
/** 단계 조회 실패 여부. 실패를 "단계 없음"으로 오해시키지 않기 위해 둔다(빈 상태에 단계 생성 유도가 붙을 예정). */
const stagesLoadFailed = ref(false)
/** stageResultId → 저장 전 변경값. 원본과 같아지면 항목을 지운다. */
const pendingEdits = ref(new Map<number, PendingEdit>())

const uploadModalOpen = ref(false)
const correctModalOpen = ref(false)
const configDrawerOpen = ref(false)
/** 정정 모달에 넘길 대상 행. 재조회하면 참조가 낡으므로 정정 성공 후 비운다. */
const correctTarget = ref<AdminStageResult | null>(null)
const exporting = ref(false)

const dirtyCount = computed(() => pendingEdits.value.size)

const jobPostingOptions = computed(() =>
  jobPostings.value.map((posting) => ({ value: posting.id, label: posting.title })),
)

const selectedStage = computed(
  () => stages.value.find((stage) => stage.id === selectedStageId.value) ?? null,
)

const editable = computed(() => selectedStage.value?.status === 'IN_PROGRESS')

/** 발표·마감 단계에서만 정정할 수 있다(백엔드 StageResultCorrectionService.validateCorrectable). */
const correctable = computed(
  () => selectedStage.value?.status === 'RESULT_ANNOUNCED' || selectedStage.value?.status === 'CLOSED',
)

const selectedJobPosting = computed(
  () => jobPostings.value.find((posting) => posting.id === selectedJobPostingId.value) ?? null,
)

/** 시작·발표·마감은 게시 중(PUBLISHED) 공고에서만 가능하다(백엔드 StageService.validateJobPostingPublishedForCommand). */
const jobPostingPublished = computed(() => selectedJobPosting.value?.status === 'PUBLISHED')

/** 공고가 마감이면 단계 설정 전체가 읽기 전용이다(백엔드 StageService.validateJobPostingEditable). */
const jobPostingClosed = computed(() => selectedJobPosting.value?.status === 'CLOSED')

const pendingCount = computed(
  () => results.value.filter((result) => result.resultStatus === 'PENDING').length,
)

/*
 * 시작·발표·마감이 왜 막혔는지 알려준다. 백엔드가 PUBLISHED 만 허용하므로 사유가 두 갈래다 —
 * 게시 전이면 게시하면 되지만, 마감된 공고는 되돌릴 수 없어 남은 단계를 전이시킬 방법이 없다.
 * 후자에 "게시하세요"라고 안내하면 실행 불가능한 행동을 시키는 셈이라 문구를 가른다.
 */
const lifecycleDisabledReason = computed(() => {
  if (jobPostingPublished.value) {
    return ''
  }
  return selectedJobPosting.value?.status === 'CLOSED'
    ? '마감된 공고라 전형 단계를 더 진행할 수 없습니다.'
    : '공고를 게시한 뒤에 사용할 수 있습니다.'
})

/*
 * 발표 차단 사유. 백엔드 StageService.validateStageResultsReadyForAnnounce 는 대상자 0건과
 * 대기 잔여를 모두 거부하므로 두 가지를 같이 본다. 빈 문자열이면 발표 가능(툴팁도 뜨지 않는다).
 */
const announceBlockedReason = computed(() => {
  if (!jobPostingPublished.value) {
    return lifecycleDisabledReason.value
  }
  if (results.value.length === 0) {
    return '대상자를 먼저 불러와야 발표할 수 있습니다.'
  }
  return pendingCount.value > 0
    ? `대기 ${pendingCount.value}건을 모두 판정해야 발표할 수 있습니다.`
    : ''
})

/** 전형 시작이 막힌 이유. 없으면 빈 문자열이고 버튼이 활성된다. */
const startBlockedReason = computed(() => {
  if (!jobPostingPublished.value) {
    return lifecycleDisabledReason.value
  }
  return results.value.length === 0 ? '대상자를 먼저 불러와야 전형을 시작할 수 있습니다.' : ''
})

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
        ? {
            type: 'info',
            message: `${stage.stageName} 준비 중`,
            description: '대상자를 불러오면 전형을 시작할 수 있습니다.',
          }
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
          announceBlockedReason.value === ''
            ? '모든 결과가 판정되었습니다. 결과를 발표할 수 있습니다.'
            : announceBlockedReason.value,
      }
    case 'RESULT_ANNOUNCED':
      return {
        type: 'success',
        message: `${stage.stageName} 발표 완료${announceAt ? ` · ${announceAt}` : ''}`,
        description: '결과가 잠겨 있습니다. 수정하려면 정정 기능을 사용하세요.',
      }
    case 'CLOSED':
      return {
        type: 'info',
        message: `${stage.stageName} 마감`,
        description: '더 이상 변경할 수 없습니다.',
      }
    default:
      return null
  }
})

/** 기본 공고: 쿼리 → 접수 중 첫 공고 → 목록 첫 공고. 지원현황 조회 화면과 같은 규칙이다. */
const pickDefaultJobPosting = (
  postings: AdminJobPostingListItem[],
): AdminJobPostingListItem | null => {
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

/*
 * 새로고침·링크 공유로 같은 위치를 복원할 수 있게 선택을 쿼리에 남긴다.
 * 같은 라우트 안의 쿼리 변경은 leavingRecords 가 비어 onBeforeRouteLeave 를 트리거하지 않는다
 * (vue-router extractChangingRecords). 저장 전 변경이 있어도 확인 모달이 뜨지 않는다.
 */
const syncQuery = () => {
  void router.replace({
    query: {
      ...(selectedJobPostingId.value !== null
        ? { jobPostingId: String(selectedJobPostingId.value) }
        : {}),
      ...(selectedStageId.value !== null ? { stageId: String(selectedStageId.value) } : {}),
    },
  })
}

const loadResults = async () => {
  if (selectedStageId.value === null) {
    results.value = []
    resultsLoadFailed.value = false
    return
  }
  loadingResults.value = true
  try {
    const response = await adminStageApi.getResults(selectedStageId.value)
    results.value = response.data.data
    resultsLoadFailed.value = false
  } catch (error) {
    results.value = []
    resultsLoadFailed.value = true
    message.error(getApiErrorMessage(error, '전형 결과를 불러오지 못했습니다.'))
  } finally {
    loadingResults.value = false
  }
}

/** 빈 상태에서 "제출 완료 n건"을 보여주기 위한 값. 조회 실패 시 null 이면 문구에서 생략한다. */
const submittedCount = ref<number | null>(null)

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

const loadStages = async () => {
  if (selectedJobPostingId.value === null) {
    stages.value = []
    selectedStageId.value = null
    stagesLoadFailed.value = false
    return
  }
  try {
    const response = await adminStageApi.getStages(selectedJobPostingId.value)
    stages.value = response.data.data
    selectedStageId.value = pickDefaultStage(stages.value)?.id ?? null
    stagesLoadFailed.value = false
  } catch (error) {
    stages.value = []
    selectedStageId.value = null
    stagesLoadFailed.value = true
    message.error(getApiErrorMessage(error, '전형 단계를 불러오지 못했습니다.'))
  }
}

const findResult = (stageResultId: number) =>
  results.value.find((result) => result.stageResultId === stageResultId) ?? null

/** 원본과 같은 값이면 버퍼에서 빼서 "변경 없음"으로 되돌린다(저장 버튼 건수가 실제와 맞게). */
const sameAsOriginal = (result: AdminStageResult, edit: PendingEdit) =>
  edit.resultStatus === result.resultStatus &&
  edit.score === result.score &&
  edit.comment === result.comment

/*
 * 그리드가 넘긴 값을 가공하지 않고 그대로 담는다.
 * a-input 의 change 는 키 입력마다 발생하고 controlled input 이라, 여기서 trim 등으로 바꿔 되돌려주면
 * 한글 IME 조합이 깨진다. 빈 문자열 → null 변환은 이미 그리드가 한다.
 *
 * 넘겨받은 map 을 직접 고친다(호출자가 복사본을 넘긴다는 전제). 원본과 같아지면 항목을 지우는
 * 규칙이 단건·일괄 양쪽에서 똑같이 지켜지도록 두 진입점이 이 함수를 공유한다.
 */
const writeEdit = (
  map: Map<number, PendingEdit>,
  stageResultId: number,
  patch: Partial<PendingEdit>,
) => {
  const result = findResult(stageResultId)
  if (result === null) {
    return
  }
  const current: PendingEdit = map.get(stageResultId) ?? {
    resultStatus: result.resultStatus,
    score: result.score,
    comment: result.comment,
  }
  const next: PendingEdit = { ...current, ...patch }
  if (sameAsOriginal(result, next)) {
    map.delete(stageResultId)
  } else {
    map.set(stageResultId, next)
  }
}

const applyEdit = (stageResultId: number, patch: Partial<PendingEdit>) => {
  const map = new Map(pendingEdits.value)
  writeEdit(map, stageResultId, patch)
  pendingEdits.value = map
}

/** 일괄 적용. 한 번만 복사하고 writeEdit 를 반복한다(id 마다 Map 을 새로 만들지 않는다). */
const applyBulk = (stageResultIds: number[], status: StageResultStatus) => {
  const map = new Map(pendingEdits.value)
  stageResultIds.forEach((stageResultId) => writeEdit(map, stageResultId, { resultStatus: status }))
  pendingEdits.value = map
}

const discardEdits = () => {
  pendingEdits.value = new Map()
  gridRef.value?.clearSelection()
}

/** 미저장 변경이 있으면 확인을 받는다. 확인하면 버퍼를 비우고 true 를 반환한다. */
const confirmDiscardIfDirty = (): Promise<boolean> => {
  if (pendingEdits.value.size === 0) {
    return Promise.resolve(true)
  }
  return new Promise((resolve) => {
    Modal.confirm({
      title: '저장하지 않은 판정이 있습니다',
      content: `변경 ${pendingEdits.value.size}건이 사라집니다. 계속할까요?`,
      okText: '변경 버리고 계속',
      cancelText: '취소',
      onOk: () => {
        discardEdits()
        resolve(true)
      },
      onCancel: () => resolve(false),
    })
  })
}

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
    const response = await adminStageApi.bulkUpdateResults(selectedStageId.value, {
      results: items,
    })
    results.value = response.data.data.results
    pendingEdits.value = new Map()
    gridRef.value?.clearSelection()
    message.success(`${response.data.data.updatedCount}건을 저장했습니다.`)
  } catch (error) {
    if (isConflict(error)) {
      // 다른 관리자가 먼저 저장했다. 최신 목록을 보여주되 입력값은 살려 재검토하게 한다.
      await loadResults()
      message.warning(
        '다른 관리자가 먼저 수정했습니다. 최신 목록을 불러왔으니 변경 내용을 다시 확인해주세요.',
      )
    } else {
      message.error(getApiErrorMessage(error, '판정 저장에 실패했습니다.'))
    }
  } finally {
    saving.value = false
  }
}

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

const openUploadModal = async () => {
  // 저장 전 판정이 남은 채 엑셀을 올리면 어느 값이 반영됐는지 알 수 없게 된다.
  if (!(await confirmDiscardIfDirty())) {
    return
  }
  uploadModalOpen.value = true
}

const openConfigDrawer = async () => {
  // 드로어 저장이 단계·결과 재조회를 부르므로 편집 버퍼와 충돌한다.
  if (!(await confirmDiscardIfDirty())) {
    return
  }
  configDrawerOpen.value = true
}

const openCorrectModal = (stageResultId: number) => {
  correctTarget.value = findResult(stageResultId)
  if (correctTarget.value !== null) {
    correctModalOpen.value = true
  }
}

/** 적용된 행이 선택으로 남으면 다음 일괄 판정이 엉뚱한 행에 걸린다. 저장·변경 취소와 같이 선택을 비운다. */
const onUploadApplied = async () => {
  await reloadStageAndResults()
  gridRef.value?.clearSelection()
}

const onCorrected = async () => {
  await reloadStageAndResults()
  // 재조회로 results 가 새 객체로 바뀌므로 옛 행을 가리키던 참조를 버린다.
  correctTarget.value = null
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

const openApplication = (applicationId: number) => {
  const resolved = router.resolve({ name: 'AdminApplication', params: { applicationId } })
  window.open(resolved.href, '_blank', 'noopener')
}

onBeforeRouteLeave(async () => await confirmDiscardIfDirty())

/** 브라우저 새로고침·닫기. 브라우저가 문구를 무시하고 기본 경고를 띄운다. */
const warnUnsavedOnUnload = (event: BeforeUnloadEvent) => {
  if (pendingEdits.value.size > 0) {
    event.preventDefault()
    event.returnValue = ''
  }
}

onMounted(async () => {
  window.addEventListener('beforeunload', warnUnsavedOnUnload)
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

onBeforeUnmount(() => window.removeEventListener('beforeunload', warnUnsavedOnUnload))
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
        <StageStepper
          :stages="stages"
          :selected-stage-id="selectedStageId"
          @select="selectStage"
          @open-config="openConfigDrawer"
        />

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
              <a-button
                v-if="selectedStage?.status === 'IN_PROGRESS'"
                size="small"
                @click="openUploadModal"
              >
                엑셀 업로드
              </a-button>
              <a-button v-if="correctable" size="small" :loading="exporting" @click="exportResults">
                엑셀 다운로드
              </a-button>
              <a-button
                v-if="selectedStage?.status === 'READY' || selectedStage?.status === 'IN_PROGRESS'"
                size="small"
                :loading="commandRunning"
                @click="initializeResults"
              >
                {{ results.length === 0 ? '대상자 불러오기' : '대상자 다시 불러오기' }}
              </a-button>
              <a-tooltip v-if="selectedStage?.status === 'READY'" :title="startBlockedReason">
                <a-button
                  type="primary"
                  size="small"
                  :disabled="startBlockedReason !== ''"
                  :loading="commandRunning"
                  @click="startStage"
                >
                  전형 시작
                </a-button>
              </a-tooltip>
              <a-tooltip
                v-if="selectedStage?.status === 'IN_PROGRESS'"
                :title="announceBlockedReason"
              >
                <a-button
                  type="primary"
                  size="small"
                  :disabled="announceBlockedReason !== ''"
                  :loading="commandRunning"
                  @click="announceStage"
                >
                  결과 발표
                </a-button>
              </a-tooltip>
              <a-tooltip
                v-if="selectedStage?.status === 'RESULT_ANNOUNCED'"
                :title="lifecycleDisabledReason"
              >
                <a-button
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

        <!-- 결과 목록 블록 밖에 둔다. 재조회가 실패해 목록이 비어도 버퍼가 남아 있으면 저장·취소가 가능해야 한다. -->
        <div v-if="editable && (results.length > 0 || dirtyCount > 0)" class="save-bar">
          <span class="dirty-count">저장 전 변경 {{ dirtyCount }}건</span>
          <span class="save-bar-spacer" />
          <a-button :disabled="dirtyCount === 0 || saving" @click="discardEdits">변경 취소</a-button>
          <a-button type="primary" :loading="saving" :disabled="dirtyCount === 0" @click="saveEdits">
            변경사항 저장 ({{ dirtyCount }})
          </a-button>
        </div>

        <template v-if="results.length > 0">
          <StageResultCounts
            :results="results"
            :active-status="statusFilter"
            @toggle="(status) => (statusFilter = status)"
          />

          <StageResultGrid
            ref="gridRef"
            :results="results"
            :pending-edits="pendingEdits"
            :editable="editable"
            :correctable="correctable"
            :status-filter="statusFilter"
            :saving="saving"
            @edit="applyEdit"
            @bulk-apply="applyBulk"
            @open-application="openApplication"
            @update:status-filter="(status) => (statusFilter = status)"
            @correct="openCorrectModal"
          />
        </template>

        <a-empty
          v-else-if="resultsLoadFailed"
          class="empty-state"
          description="전형 결과를 불러오지 못했습니다."
        >
          <a-button @click="loadResults">다시 시도</a-button>
        </a-empty>

        <a-empty
          v-else-if="!loadingResults"
          class="empty-state"
          :description="
            submittedCount !== null
              ? `대상자를 아직 불러오지 않았습니다. 제출 완료 지원서 ${submittedCount}건.`
              : '대상자를 아직 불러오지 않았습니다.'
          "
        />
      </template>

      <!-- 조회 실패를 "단계 없음"과 갈라 놓는다. 네트워크 오류인데 단계를 만들라고 안내하면 안 된다. -->
      <a-empty
        v-else-if="!initializing && stagesLoadFailed"
        class="empty-state"
        description="전형 단계를 불러오지 못했습니다."
      >
        <a-button @click="reloadStageAndResults">다시 시도</a-button>
      </a-empty>

      <a-empty
        v-else-if="!initializing && selectedJobPostingId !== null"
        class="empty-state"
        description="이 공고에는 전형 단계가 아직 없습니다."
      >
        <p class="empty-hint">서류전형 → 면접 순서로 단계를 만들면 지원자 결과를 판정할 수 있습니다.</p>
        <a-button type="primary" @click="openConfigDrawer">전형 단계 설정</a-button>
      </a-empty>
    </a-spin>

    <StageUploadPreviewModal
      v-model:open="uploadModalOpen"
      :stage-id="selectedStageId"
      :stage-name="selectedStage?.stageName ?? ''"
      @applied="onUploadApplied"
    />

    <StageResultCorrectModal
      v-model:open="correctModalOpen"
      :stage-id="selectedStageId"
      :stage-name="selectedStage?.stageName ?? ''"
      :target="correctTarget"
      @corrected="onCorrected"
    />

    <StageConfigDrawer
      v-model:open="configDrawerOpen"
      :job-posting-id="selectedJobPostingId"
      :stages="stages"
      :job-posting-closed="jobPostingClosed"
      @saved="reloadStageAndResults"
    />
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
</style>
