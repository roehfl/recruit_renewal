<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'

import { adminAuditApi } from '@/api/admin/adminAuditApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import type {
  AuditActionResult,
  AuditActionType,
  AuditActivityQuery,
  AuditActivityResponse,
  AuditTargetType,
} from '@/types/admin/auditLog'
import AuditLogDrawer from './AuditLogDrawer.vue'
import { ACTION_RESULT_OPTIONS, ACTION_TYPE_OPTIONS, TARGET_TYPE_OPTIONS, actionResultTag, actionTypeLabel, actorTypeLabel, targetTypeLabel } from './logLabel'
import { RANGE_PRESETS, type DateRange, presetRange, rangeError, toDateTimeRange } from './logQuery'

/*
 * 감사 로그 탭. 관리자·시스템의 서버 행위 증적이라 지원자 스코프가 아니다.
 * 지원자 찾기는 이 탭에 없고, 지원번호는 필터 입력칸으로 직접 받는다.
 * 진입 시 자동 조회하지 않는다(조회 버튼을 눌러야 첫 조회).
 */

const PAGE_SIZE = 20
const DEFAULT_RANGE_DAYS = 30

const emit = defineEmits<{ (e: 'open-applicant-events', applicationId: number): void }>()

const range = ref<DateRange>(presetRange(DEFAULT_RANGE_DAYS, new Date()))
const actionType = ref<AuditActionType | undefined>(undefined)
const actionResult = ref<AuditActionResult | undefined>(undefined)
const targetType = ref<AuditTargetType | undefined>(undefined)
const actorId = ref('')
const jobPostingId = ref('')
const applicationId = ref('')

const rows = ref<AuditActivityResponse[]>([])
const page = ref(0)
const totalElements = ref(0)
const loading = ref(false)
const searched = ref(false)
/* 조회 버튼을 누른 시점의 조건. 페이지 이동은 이 조건으로 다시 읽는다. */
const appliedQuery = ref<Omit<AuditActivityQuery, 'page' | 'size'>>({})

const columns = [
  { title: '일시', key: 'occurredAt', width: 160 },
  { title: '행위자', key: 'actor', width: 170 },
  { title: '행위', key: 'actionType', width: 190 },
  { title: '대상', key: 'target', ellipsis: true },
  { title: '결과', key: 'actionResult', width: 90 },
  { title: '지원번호', key: 'applicationId', width: 100 },
  { title: 'IP', key: 'ipAddress', width: 140 },
]

const pagination = computed(() => ({
  current: page.value + 1,
  pageSize: PAGE_SIZE,
  total: totalElements.value,
  showSizeChanger: false,
}))

const asRow = (record: unknown): AuditActivityResponse => record as AuditActivityResponse

const toNumber = (value: string): number | undefined => {
  const trimmed = value.trim()
  if (!trimmed) return undefined
  const parsed = Number(trimmed)
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : undefined
}

const buildQuery = (): Omit<AuditActivityQuery, 'page' | 'size'> => ({
  ...toDateTimeRange(range.value),
  actionType: actionType.value,
  actionResult: actionResult.value,
  targetType: targetType.value,
  actorId: actorId.value.trim() || undefined,
  jobPostingId: toNumber(jobPostingId.value),
  applicationId: toNumber(applicationId.value),
})

let listRequest = 0

const loadList = async (): Promise<void> => {
  const request = ++listRequest
  loading.value = true
  try {
    const response = await adminAuditApi.getActivities({
      ...appliedQuery.value,
      page: page.value,
      size: PAGE_SIZE,
    })
    if (request !== listRequest) return
    rows.value = response.data.data.content
    totalElements.value = response.data.data.totalElements
    searched.value = true
  } catch (error) {
    if (request !== listRequest) return
    message.error(getApiErrorMessage(error, '감사 로그를 불러오지 못했습니다.'))
  } finally {
    if (request === listRequest) {
      loading.value = false
    }
  }
}

const search = (): void => {
  const error = rangeError(range.value[0], range.value[1])
  if (error !== null) {
    message.warning(error)
    return
  }
  appliedQuery.value = buildQuery()
  page.value = 0
  void loadList()
}

const reset = (): void => {
  range.value = presetRange(DEFAULT_RANGE_DAYS, new Date())
  actionType.value = undefined
  actionResult.value = undefined
  targetType.value = undefined
  actorId.value = ''
  jobPostingId.value = ''
  applicationId.value = ''
  rows.value = []
  totalElements.value = 0
  page.value = 0
  searched.value = false
}

const applyPreset = (days: number): void => {
  range.value = presetRange(days, new Date())
}

const onRangeChange = (_: unknown, dateStrings: [string, string]): void => {
  if (dateStrings[0] && dateStrings[1]) {
    range.value = dateStrings
  }
}

const handleTableChange = (nextPagination: { current?: number }): void => {
  page.value = (nextPagination.current ?? 1) - 1
  void loadList()
}

const drawerOpen = ref(false)
const detailId = ref<number | null>(null)

const openDetail = (row: AuditActivityResponse): void => {
  detailId.value = row.id
  drawerOpen.value = true
}

const onRow = (record: unknown) => ({ onClick: () => openDetail(asRow(record)) })

/** 지원자 이벤트 탭에서 넘어올 때: 다른 조건을 비우고 지원번호만 걸어 바로 조회한다. */
const applyApplicationId = async (targetApplicationId: number): Promise<void> => {
  reset()
  applicationId.value = String(targetApplicationId)
  await nextTick()
  search()
}

defineExpose({ applyApplicationId })
</script>

<template>
  <div class="audit-panel">
    <section class="filters">
      <div class="filter-row">
        <label class="filter-label">기간 (시작일·종료일 포함 최대 90일)</label>
        <a-range-picker
          :value="range"
          value-format="YYYY-MM-DD"
          :allow-clear="false"
          :placeholder="['시작일', '종료일']"
          @change="onRangeChange"
        />
        <a-space :size="4" class="presets">
          <a-button v-for="days in RANGE_PRESETS" :key="days" size="small" @click="applyPreset(days)">
            {{ days }}일
          </a-button>
        </a-space>
      </div>

      <div class="filter-row">
        <a-select
          v-model:value="actionType"
          :options="ACTION_TYPE_OPTIONS"
          placeholder="전체 행위"
          allow-clear
          class="filter-select"
        />
        <a-select
          v-model:value="actionResult"
          :options="ACTION_RESULT_OPTIONS"
          placeholder="전체 결과"
          allow-clear
          class="filter-select-sm"
        />
        <a-select
          v-model:value="targetType"
          :options="TARGET_TYPE_OPTIONS"
          placeholder="전체 대상"
          allow-clear
          class="filter-select-sm"
        />
        <a-input v-model:value="actorId" placeholder="행위자 ID" allow-clear class="filter-input" @press-enter="search" />
        <a-input v-model:value="jobPostingId" placeholder="공고 ID" allow-clear class="filter-input-sm" @press-enter="search" />
        <a-input v-model:value="applicationId" placeholder="지원번호" allow-clear class="filter-input-sm" @press-enter="search" />
        <span class="spacer" />
        <a-button @click="reset"><template #icon><ReloadOutlined /></template>초기화</a-button>
        <a-button type="primary" :loading="loading" @click="search">
          <template #icon><SearchOutlined /></template>조회
        </a-button>
      </div>
    </section>

    <p class="list-count">
      <template v-if="searched">총 <b>{{ totalElements }}</b>건</template>
      <template v-else>조회 전</template>
    </p>

    <a-table
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      size="small"
      :custom-row="onRow"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'occurredAt'">
          {{ formatDate(asRow(record).occurredAt, 'YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.key === 'actor'">
          {{ asRow(record).actorId ?? '-' }}
          <span class="sub">{{ actorTypeLabel(asRow(record).actorType) }}</span>
        </template>
        <template v-else-if="column.key === 'actionType'">{{ actionTypeLabel(asRow(record).actionType) }}</template>
        <template v-else-if="column.key === 'target'">
          {{ targetTypeLabel(asRow(record).targetType) }}
          <span v-if="asRow(record).targetId" class="sub">{{ asRow(record).targetId }}</span>
        </template>
        <template v-else-if="column.key === 'actionResult'">
          <a-tag :color="actionResultTag(asRow(record).actionResult).color">
            {{ actionResultTag(asRow(record).actionResult).label }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'applicationId'">{{ asRow(record).applicationId ?? '-' }}</template>
        <template v-else-if="column.key === 'ipAddress'">{{ asRow(record).ipAddress ?? '-' }}</template>
      </template>
      <template #emptyText>
        <a-empty
          :description="searched ? '조회 조건에 맞는 감사 로그가 없습니다.' : '조회 조건을 지정한 뒤 조회를 누르세요.'"
        />
      </template>
    </a-table>

    <AuditLogDrawer
      v-model:open="drawerOpen"
      :activity-id="detailId"
      @open-applicant-events="(id: number) => emit('open-applicant-events', id)"
    />
  </div>
</template>

<style scoped lang="scss">
.audit-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.filters {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-label {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.presets {
  margin-left: 4px;
}

.filter-select {
  width: 200px;
}

.filter-select-sm {
  width: 140px;
}

.filter-input {
  width: 160px;
}

.filter-input-sm {
  width: 110px;
}

.spacer {
  flex: 1;
}

.list-count {
  margin: 0;
  font-size: 13px;
  color: var(--app-text-secondary);
}

.sub {
  margin-left: 6px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

:deep(.ant-table-tbody > tr) {
  cursor: pointer;
}
</style>
