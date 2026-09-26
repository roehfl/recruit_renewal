<template>
  <div class="window-tab">
    <div class="tools">
      <a-select v-model:value="groupFilter" class="group-select" :options="groupOptions" />
      <a-input v-model:value="keyword" class="keyword" placeholder="성명·수험번호 검색" allow-clear />
      <a-checkbox :checked="allSelected" :indeterminate="someSelected" :disabled="filtered.length === 0" @change="toggleAll">전체 선택</a-checkbox>
      <span class="hint"><InfoCircleOutlined /> 기본값: 지원자 조의 도착시간 ~ +2시간</span>
    </div>

    <div v-if="selected.size > 0" class="bulk">
      <b>{{ selected.size }}명 선택</b>
      <a-button size="small" @click="openTimeModal(selectedCandidates)"><FieldTimeOutlined />시간 변경</a-button>
      <a-button size="small" @click="confirmReset(selectedCandidates)"><UndoOutlined />기본값 복원</a-button>
      <a-button size="small" type="text" class="bulk-clear" @click="selected.clear()">해제</a-button>
    </div>

    <div v-if="candidates.length === 0" class="empty">
      이 면접단계에 배정된 지원자가 없습니다. 면접 스케줄링에서 스케줄을 업로드하면 나타납니다.
    </div>
    <div v-else-if="filtered.length === 0" class="empty">조건에 맞는 지원자가 없습니다.</div>
    <div v-else class="list">
      <div class="list-head"><span></span><span>성명</span><span>수험번호</span><span>입력 가능 시간</span><span></span></div>
      <template v-for="group in groups" :key="group.interviewId">
        <div class="group-row">
          <a-checkbox :checked="group.rows.every((c) => selected.has(c.jobApplicationId))" @change="toggleGroup(group.rows, $event)" />
          <span class="group-name">{{ group.groupName }}조</span>
          <span>{{ formatDate(group.interviewStartDateTime, 'MM.DD') }} 도착 <b>{{ group.arrivalDateTime ? formatDate(group.arrivalDateTime, 'HH:mm') : '—' }}</b></span>
          <span>기본 <b v-if="group.arrivalDateTime">{{ formatDate(group.arrivalDateTime, 'HH:mm') }} → {{ formatDate(addHours(group.arrivalDateTime, 2), 'HH:mm') }}</b><b v-else class="warn">도착시간 없음</b></span>
          <span>{{ group.rows.length }}명</span>
          <a-button size="small" class="group-action" @click="openTimeModal(group.allRows)"><FieldTimeOutlined />조 전체 변경</a-button>
        </div>
        <div v-for="candidate in group.rows" :key="candidate.jobApplicationId" class="row" :class="{ sel: selected.has(candidate.jobApplicationId) }">
          <a-checkbox :checked="selected.has(candidate.jobApplicationId)" @change="toggleOne(candidate.jobApplicationId)" />
          <span class="name">{{ candidate.applicantName }}</span>
          <span class="num">{{ candidate.jobApplicationId }}</span>
          <span class="window">
            <span class="num">{{ formatWindow(candidate.startDateTime, candidate.endDateTime) }}</span>
            <a-tag v-if="!candidate.startDateTime" color="warning">직접 지정 필요</a-tag>
            <a-tag v-else-if="candidate.customized" color="blue">직접 지정</a-tag>
            <a-tag v-else color="green">기본값</a-tag>
          </span>
          <a-button size="small" @click="openTimeModal([candidate])"><FieldTimeOutlined />시간 변경</a-button>
        </div>
      </template>
    </div>

    <a-modal
      v-model:open="timeModalOpen"
      title="입력 가능 시간 변경"
      ok-text="저장"
      cancel-text="취소"
      :confirm-loading="saving"
      @ok="saveTimes"
    >
      <p class="target">{{ targetLabel }}</p>
      <a-alert
        v-if="targetLiveCount > 0"
        type="warning"
        show-icon
        class="modal-alert"
        :message="`지금 입력 시간 중인 지원자가 ${targetLiveCount}명 있습니다. 종료 시각을 앞당기면 그 지원자의 입력 창이 그 시각에 닫힙니다.`"
      />
      <div class="time-form">
        <label>시작</label>
        <a-date-picker v-model:value="startValue" show-time :value-format="DATE_TIME_VALUE_FORMAT" format="YYYY-MM-DD HH:mm" />
        <label>종료</label>
        <a-date-picker v-model:value="endValue" show-time :value-format="DATE_TIME_VALUE_FORMAT" format="YYYY-MM-DD HH:mm" />
      </div>
      <div class="quick">
        <a-button v-for="hours in [1, 2, 3]" :key="hours" size="small" shape="round" :disabled="!startValue" @click="endValue = addHours(startValue!, hours)">
          시작 +{{ hours }}시간
        </a-button>
        <a-button v-if="singleDefault" size="small" shape="round" @click="applyDefault">기본값으로</a-button>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import type { CheckboxChangeEvent } from 'ant-design-vue/es/checkbox/interface'
import { FieldTimeOutlined, InfoCircleOutlined, UndoOutlined } from '@ant-design/icons-vue'
import { adminInterviewSupplementApi } from '@/api/admin/adminInterviewSupplementApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import type { AdminInterviewSupplementCandidate } from '@/types/admin/interviewSupplement'
import { addHours, DATE_TIME_VALUE_FORMAT, formatWindow, isWithinWindow } from './interviewSupplementTime'

const props = defineProps<{ stageId: number; candidates: AdminInterviewSupplementCandidate[] }>()
const emit = defineEmits<{ (e: 'update', value: AdminInterviewSupplementCandidate[]): void }>()

const groupFilter = ref<number | ''>('')
const keyword = ref('')
const selected = reactive(new Set<number>())

/* 단계를 바꾸거나 목록이 바뀌면 없어진 지원자는 선택에서 뺀다. */
watch(() => props.candidates, (list) => {
  const ids = new Set(list.map((c) => c.jobApplicationId))
  ;[...selected].forEach((id) => { if (!ids.has(id)) selected.delete(id) })
})

interface GroupView {
  interviewId: number
  groupName: string
  interviewStartDateTime: string
  arrivalDateTime: string | null
  allRows: AdminInterviewSupplementCandidate[]
  rows: AdminInterviewSupplementCandidate[]
}

const allGroups = computed(() => {
  const map = new Map<number, AdminInterviewSupplementCandidate[]>()
  props.candidates.forEach((candidate) => {
    const rows = map.get(candidate.interviewId) ?? []
    rows.push(candidate)
    map.set(candidate.interviewId, rows)
  })
  return [...map.values()]
})

const groupOptions = computed(() => [
  { value: '' as const, label: '전체 조' },
  ...allGroups.value.map((rows) => ({ value: rows[0]!.interviewId, label: `${rows[0]!.groupName}조` })),
])

const matches = (candidate: AdminInterviewSupplementCandidate): boolean => {
  const k = keyword.value.trim()
  return (groupFilter.value === '' || candidate.interviewId === groupFilter.value)
    && (!k || candidate.applicantName.includes(k) || String(candidate.jobApplicationId).includes(k))
}

const filtered = computed(() => props.candidates.filter(matches))

const groups = computed<GroupView[]>(() =>
  allGroups.value
    .map((rows) => ({
      interviewId: rows[0]!.interviewId,
      groupName: rows[0]!.groupName,
      interviewStartDateTime: rows[0]!.interviewStartDateTime,
      arrivalDateTime: rows[0]!.arrivalDateTime,
      allRows: rows,
      rows: rows.filter(matches),
    }))
    .filter((group) => group.rows.length > 0),
)

const allSelected = computed(() => filtered.value.length > 0 && filtered.value.every((c) => selected.has(c.jobApplicationId)))
const someSelected = computed(() => !allSelected.value && filtered.value.some((c) => selected.has(c.jobApplicationId)))
const selectedCandidates = computed(() => props.candidates.filter((c) => selected.has(c.jobApplicationId)))

const toggleOne = (id: number): void => {
  if (selected.has(id)) selected.delete(id)
  else selected.add(id)
}
const toggleAll = (event: CheckboxChangeEvent): void => {
  filtered.value.forEach((c) => (event.target.checked ? selected.add(c.jobApplicationId) : selected.delete(c.jobApplicationId)))
}
const toggleGroup = (rows: AdminInterviewSupplementCandidate[], event: CheckboxChangeEvent): void => {
  rows.forEach((c) => (event.target.checked ? selected.add(c.jobApplicationId) : selected.delete(c.jobApplicationId)))
}

/* ---- 시간 변경 ---- */
const timeModalOpen = ref(false)
const saving = ref(false)
const targets = ref<AdminInterviewSupplementCandidate[]>([])
const startValue = ref<string | undefined>()
const endValue = ref<string | undefined>()

const targetLabel = computed(() => {
  const list = targets.value
  if (list.length === 1) {
    const c = list[0]!
    return `${c.groupName}조 ${c.candidateOrder ?? ''}번 ${c.applicantName} (수험번호 ${c.jobApplicationId})`
  }
  const byGroup = new Map<string, number>()
  list.forEach((c) => byGroup.set(c.groupName, (byGroup.get(c.groupName) ?? 0) + 1))
  return `선택한 지원자 ${list.length}명 (${[...byGroup].map(([g, n]) => `${g}조 ${n}명`).join(', ')})`
})
const targetLiveCount = computed(() => targets.value.filter((c) => isWithinWindow(c.startDateTime, c.endDateTime)).length)
const singleDefault = computed(() => targets.value.length === 1 && targets.value[0]!.arrivalDateTime !== null)

const openTimeModal = (list: AdminInterviewSupplementCandidate[]): void => {
  if (list.length === 0) return
  targets.value = list
  const first = list[0]!
  const base = first.startDateTime ?? first.arrivalDateTime ?? first.interviewStartDateTime
  startValue.value = formatDate(base, DATE_TIME_VALUE_FORMAT)
  endValue.value = first.endDateTime ? formatDate(first.endDateTime, DATE_TIME_VALUE_FORMAT) : addHours(startValue.value, 2)
  timeModalOpen.value = true
}

const applyDefault = (): void => {
  const arrival = targets.value[0]?.arrivalDateTime
  if (!arrival) return
  startValue.value = formatDate(arrival, DATE_TIME_VALUE_FORMAT)
  endValue.value = addHours(startValue.value, 2)
}

const saveTimes = async (): Promise<void> => {
  if (!startValue.value || !endValue.value) {
    message.warning('시작·종료 시각을 모두 입력하세요.')
    return
  }
  if (endValue.value <= startValue.value) {
    message.warning('종료 시각은 시작 시각보다 늦어야 합니다.')
    return
  }
  saving.value = true
  try {
    const response = await adminInterviewSupplementApi.saveWindows(props.stageId, {
      jobApplicationIds: targets.value.map((c) => c.jobApplicationId),
      startDateTime: startValue.value,
      endDateTime: endValue.value,
    })
    emit('update', response.data.data)
    timeModalOpen.value = false
    message.success(targets.value.length === 1 ? '입력 시간을 저장했습니다.' : `${targets.value.length}명의 입력 시간을 바꿨습니다.`)
  } catch (error) {
    message.error(getApiErrorMessage(error, '입력 시간을 저장하지 못했습니다.'))
  } finally {
    saving.value = false
  }
}

const confirmReset = (list: AdminInterviewSupplementCandidate[]): void => {
  const customized = list.filter((c) => c.customized)
  if (customized.length === 0) {
    message.info('선택한 지원자는 모두 기본값입니다.')
    return
  }
  Modal.confirm({
    title: '기본값 복원',
    content: `직접 지정한 ${customized.length}명의 입력 시간을 각자 조의 도착시간 ~ +2시간으로 되돌립니다. 도착시간이 없는 조의 지원자는 시간이 비게 됩니다.`,
    okText: '복원',
    cancelText: '취소',
    onOk: async () => {
      try {
        const response = await adminInterviewSupplementApi.resetWindows(props.stageId, customized.map((c) => c.jobApplicationId))
        emit('update', response.data.data)
        message.success(`${customized.length}명을 기본값으로 되돌렸습니다.`)
      } catch (error) {
        message.error(getApiErrorMessage(error, '기본값으로 되돌리지 못했습니다.'))
      }
    },
  })
}
</script>

<style scoped>
.tools {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.group-select {
  width: 120px;
}
.keyword {
  width: 220px;
}
.hint {
  margin-left: auto;
  font-size: 12px;
  color: var(--app-text-secondary);
}
.bulk {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  padding: 8px 12px;
  border-radius: var(--app-border-radius);
  background: var(--app-bg-selected);
  border: 1px solid #cfe0c6;
}
.bulk b {
  color: var(--app-color-primary);
}
.bulk-clear {
  margin-left: auto;
}
.empty {
  padding: 40px 0;
  text-align: center;
  color: var(--app-text-muted);
}
.list {
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius);
}
.list-head,
.row {
  display: grid;
  grid-template-columns: 18px 110px 96px 1fr max-content;
  gap: 12px;
  align-items: center;
  padding: 0 12px;
}
.list-head {
  height: 34px;
  background: var(--app-bg-muted);
  border-bottom: 1px solid var(--app-border-default);
  font-size: 12px;
  color: var(--app-text-secondary);
  position: sticky;
  top: 0;
  z-index: 2;
}
.group-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #fbfcfa;
  border-bottom: 1px solid var(--app-border-default);
  font-size: 12px;
  color: var(--app-text-secondary);
  position: sticky;
  top: 34px;
  z-index: 1;
}
.group-row b {
  color: var(--app-text-primary);
  font-weight: 500;
}
.group-row b.warn {
  color: var(--app-color-warning);
}
.group-name {
  font-weight: 700;
  font-size: 13.5px;
  color: var(--app-text-primary);
}
.group-action {
  margin-left: auto;
}
.row {
  padding-top: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--app-border-default);
  font-size: 14px;
}
.row:last-child {
  border-bottom: 0;
}
.row.sel {
  background: var(--app-bg-selected);
}
.name {
  font-weight: 500;
}
.num {
  font-variant-numeric: tabular-nums;
}
.window {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.window .num {
  font-weight: 500;
}
.target {
  margin-bottom: 10px;
}
.modal-alert {
  margin-bottom: 12px;
}
.time-form {
  display: grid;
  grid-template-columns: 40px 1fr;
  gap: 8px;
  align-items: center;
}
.time-form label {
  font-size: 12.5px;
  color: var(--app-text-secondary);
}
.quick {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin: 10px 0 0 48px;
}
@media (max-width: 760px) {
  .hint {
    margin-left: 0;
  }
  .list-head,
  .row {
    grid-template-columns: 18px minmax(60px, 1fr) 72px minmax(140px, 2fr) max-content;
    gap: 8px;
  }
}
</style>
