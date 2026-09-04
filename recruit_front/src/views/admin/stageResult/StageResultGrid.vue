<script setup lang="ts">
import { computed, ref } from 'vue'
import type { TableColumnsType } from 'ant-design-vue'
import {
  BULK_APPLY_STATUSES,
  DECIDABLE_RESULT_STATUSES,
  APPLICATION_TYPE_LABELS,
  EDUCATION_LEVEL_LABELS,
  STAGE_RESULT_STATUS_COLORS,
  STAGE_RESULT_STATUS_LABELS,
  type AdminStageResult,
  type EducationLevel,
  type JobPositionApplicationType,
  type PendingEdit,
  type StageResultStatus,
} from '@/types/admin/stage'
import { formatDate } from '@/common/dateUtil'

const props = defineProps<{
  results: AdminStageResult[]
  /** stageResultId → 저장 전 변경값 */
  pendingEdits: Map<number, PendingEdit>
  /** IN_PROGRESS 단계에서만 true. false 면 그리드가 읽기 전용이다. */
  editable: boolean
  /** 발표·마감 단계에서만 true. 정정 버튼 열을 띄운다. */
  correctable: boolean
  /** 카운트 카드에서 넘어온 결과 필터. null 이면 전체 */
  statusFilter: StageResultStatus | null
  saving: boolean
}>()

const emit = defineEmits<{
  (event: 'edit', stageResultId: number, patch: Partial<PendingEdit>): void
  (event: 'bulk-apply', stageResultIds: number[], status: StageResultStatus): void
  (event: 'open-application', applicationId: number): void
  (event: 'update:statusFilter', status: StageResultStatus | null): void
  /* 슬롯 record 가 느슨한 타입이라 행 객체 대신 id 를 넘긴다. 본체가 id 로 행을 찾는다. */
  (event: 'correct', stageResultId: number): void
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

/*
 * a-table 의 bodyCell 슬롯은 record 를 Record<string, any> 로 넘겨서 AdminStageResult 로 바로 못 쓴다.
 * 그래서 행을 통째로 받는 대신 타입이 붙은 함수에 필요한 필드만 넘긴다
 * (AdminApplicationFormListView 의 configStateOf 와 같은 방식).
 */
const applicationTypeLabel = (type: JobPositionApplicationType) => APPLICATION_TYPE_LABELS[type]

const educationLevelLabel = (level: EducationLevel) => EDUCATION_LEVEL_LABELS[level]

const resultStatusLabel = (status: StageResultStatus) => STAGE_RESULT_STATUS_LABELS[status]

const resultStatusColor = (status: StageResultStatus) => STAGE_RESULT_STATUS_COLORS[status]

/** 저장 전 변경이 있으면 그 값을, 없으면 원본을 본다. 필터·표시가 화면과 어긋나지 않게 한다. */
const effectiveStatus = (stageResultId: number, original: StageResultStatus): StageResultStatus =>
  props.pendingEdits.get(stageResultId)?.resultStatus ?? original

/* 버퍼에 명시적 null 이 들어 있을 수 있어 ?? 대신 항목 존재 여부로 가른다(?? 면 원본으로 되돌아간다). */
const effectiveScore = (stageResultId: number, original: number | null): number | null => {
  const edit = props.pendingEdits.get(stageResultId)
  return edit ? edit.score : original
}

const effectiveComment = (stageResultId: number, original: string | null): string | null => {
  const edit = props.pendingEdits.get(stageResultId)
  return edit ? edit.comment : original
}

const isDirty = (stageResultId: number) => props.pendingEdits.has(stageResultId)

/** PENDING 은 판정으로 지정할 수 없다. 아직 미판정인 행에서만 현재 값 표시용으로 옵션에 끼워 넣는다. */
const statusOptionsFor = (current: StageResultStatus) =>
  current === 'PENDING'
    ? [{ value: 'PENDING', label: STAGE_RESULT_STATUS_LABELS.PENDING }, ...resultStatusOptions]
    : resultStatusOptions

/** 셀렉트가 돌려주는 값은 SelectValue 라 좁혀서 쓴다. 지우면 undefined 가 온다. */
const isResultStatus = (value: unknown): value is StageResultStatus =>
  typeof value === 'string' && value in STAGE_RESULT_STATUS_LABELS

const handleStatusFilterChange = (value: unknown) => {
  emit('update:statusFilter', isResultStatus(value) ? value : null)
}

const handleResultStatusChange = (stageResultId: number, value: unknown) => {
  if (isResultStatus(value)) {
    emit('edit', stageResultId, { resultStatus: value })
  }
}

/** a-input-number 는 값을 비우면 null 을 넘긴다(선언 타입에는 없다). 숫자가 아니면 미입력으로 본다. */
const handleScoreChange = (stageResultId: number, value: unknown) => {
  emit('edit', stageResultId, { score: typeof value === 'number' ? value : null })
}

/** a-input 이 change 로 넘기는 이벤트. ant-design-vue 내부 ChangeEvent 와 같은 모양이다. */
type InputChangeEvent = Event & { target: { value?: string } }

/** a-input 의 change 는 입력할 때마다 발생한다(blur 아님). 공백만 남으면 미입력으로 본다. */
const handleCommentChange = (stageResultId: number, event: InputChangeEvent) => {
  const value = event.target.value ?? ''
  emit('edit', stageResultId, { comment: value.trim().length === 0 ? null : value })
}

/** 화면에 실제로 그릴 행. 필터는 전부 클라이언트에서 건다(결과 목록이 비페이징이라). */
const filteredResults = computed(() =>
  props.results.filter((result) => {
    if (jobPositionFilter.value !== undefined && result.jobPositionId !== jobPositionFilter.value) {
      return false
    }
    if (workLocationFilter.value !== undefined && result.workLocation !== workLocationFilter.value) {
      return false
    }
    if (
      props.statusFilter !== null &&
      effectiveStatus(result.stageResultId, result.resultStatus) !== props.statusFilter
    ) {
      return false
    }
    const keyword = nameKeyword.value.trim()
    if (keyword.length > 0 && !result.applicantName.includes(keyword)) {
      return false
    }
    return true
  }),
)

/** 정정 열 폭. 열 정의와 가로 스크롤 폭이 따로 놀지 않게 한 곳에서 쓴다. */
const CORRECT_COLUMN_WIDTH = 80

/** 고정 열 폭 합계(1500) + 선택 열(40). 열을 더하면 이 값도 같이 늘려야 마지막 열이 잘리지 않는다. */
const BASE_SCROLL_X = 1540

const columns = computed<TableColumnsType>(() => {
  const base: TableColumnsType = [
    { title: '수험번호', key: 'applicationId', width: 100 },
    { title: '이름', key: 'applicantName', width: 110 },
    // dataIndex 가 없고 bodyCell 분기도 없으면 a-table 이 빈칸을 그린다. 값을 그대로 쓰는 열은 dataIndex 를 준다.
    { title: '지원분야', dataIndex: 'jobPositionName', key: 'jobPositionName', width: 140 },
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
  if (props.correctable) {
    base.push({ title: '정정', key: 'correct', width: CORRECT_COLUMN_WIDTH })
  }
  return base
})

const scrollX = computed(() => BASE_SCROLL_X + (props.correctable ? CORRECT_COLUMN_WIDTH : 0))

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

/*
 * 필터로 가려진 행이 선택된 채 남아 있으면, 사용자가 보지 못하는 행에 판정이 적용된다.
 * 배지 숫자와 일괄 적용 대상 모두 "지금 보이는 선택 행"으로 통일한다.
 * 선택 자체는 지우지 않으므로 필터를 되돌리면 그대로 살아난다.
 */
const visibleSelectedKeys = computed(() => {
  const visibleIds = new Set(filteredResults.value.map((result) => result.stageResultId))
  return selectedRowKeys.value.filter((key) => visibleIds.has(key))
})

const applyBulk = (status: StageResultStatus) => {
  emit('bulk-apply', [...visibleSelectedKeys.value], status)
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
        @change="handleStatusFilterChange"
      />
      <a-input v-model:value="nameKeyword" class="filter" placeholder="이름" allow-clear />
      <a-button @click="resetFilters">초기화</a-button>
      <span class="toolbar-spacer" />
      <span class="row-count">{{ filteredResults.length }} / {{ results.length }}건</span>
    </div>

    <div v-if="editable" class="toolbar">
      <span class="bulk" :class="{ disabled: visibleSelectedKeys.length === 0 }">
        <b>선택 {{ visibleSelectedKeys.length }}건</b>
        <a-button
          v-for="status in BULK_APPLY_STATUSES"
          :key="status"
          size="small"
          :disabled="visibleSelectedKeys.length === 0 || saving"
          @click="applyBulk(status)"
        >
          {{ resultStatusLabel(status) }}
        </a-button>
      </span>
    </div>

    <a-table
      :columns="columns"
      :data-source="filteredResults"
      :row-selection="rowSelection"
      :pagination="{ pageSize: 20, showSizeChanger: true, pageSizeOptions: ['10', '20', '50'] }"
      :row-class-name="(record: AdminStageResult) => (isDirty(record.stageResultId) ? 'dirty-row' : '')"
      row-key="stageResultId"
      size="small"
      :scroll="{ x: scrollX }"
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
          {{ applicationTypeLabel(record.applicationType) }}
        </template>

        <template v-else-if="column.key === 'education'">
          <template v-if="record.finalEducationLevel">
            {{ educationLevelLabel(record.finalEducationLevel) }}
            <span v-if="record.finalSchoolName" class="school">· {{ record.finalSchoolName }}</span>
          </template>
          <template v-else>-</template>
        </template>

        <template v-else-if="column.key === 'previousStageResultStatus'">
          <a-tag
            v-if="record.previousStageResultStatus"
            :color="resultStatusColor(record.previousStageResultStatus)"
          >
            {{ resultStatusLabel(record.previousStageResultStatus) }}
          </a-tag>
          <template v-else>-</template>
        </template>

        <template v-else-if="column.key === 'resultStatus'">
          <a-select
            v-if="editable"
            :value="effectiveStatus(record.stageResultId, record.resultStatus)"
            size="small"
            style="width: 100%"
            :disabled="saving"
            :options="statusOptionsFor(effectiveStatus(record.stageResultId, record.resultStatus))"
            @change="(value: unknown) => handleResultStatusChange(record.stageResultId, value)"
          />
          <a-tag v-else :color="resultStatusColor(record.resultStatus)">
            {{ resultStatusLabel(record.resultStatus) }}
          </a-tag>
        </template>

        <template v-else-if="column.key === 'score'">
          <a-input-number
            v-if="editable"
            :value="effectiveScore(record.stageResultId, record.score) ?? undefined"
            size="small"
            style="width: 100%"
            :disabled="saving"
            @change="(value: unknown) => handleScoreChange(record.stageResultId, value)"
          />
          <template v-else>{{ record.score ?? '-' }}</template>
        </template>

        <template v-else-if="column.key === 'comment'">
          <a-input
            v-if="editable"
            :value="effectiveComment(record.stageResultId, record.comment) ?? ''"
            size="small"
            :maxlength="2000"
            :disabled="saving"
            @change="(event: InputChangeEvent) => handleCommentChange(record.stageResultId, event)"
          />
          <template v-else>{{ record.comment ?? '-' }}</template>
        </template>

        <template v-else-if="column.key === 'decidedAt'">
          <span v-if="isDirty(record.stageResultId)" class="pending-mark">저장 전</span>
          <template v-else>
            {{ record.decidedAt ? formatDate(record.decidedAt, 'MM-DD HH:mm') : '-' }}
          </template>
        </template>

        <template v-else-if="column.key === 'decidedBy'">
          {{ record.decidedBy ?? '-' }}
        </template>

        <template v-else-if="column.key === 'correct'">
          <a-button size="small" @click="emit('correct', record.stageResultId)">정정</a-button>
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
