<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { message } from 'ant-design-vue'
import { AimOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'

import { adminClientEventApi } from '@/api/admin/adminClientEventApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import type { ClientEventLogQuery, ClientEventLogResponse } from '@/types/admin/clientEventLog'
import type { ClientEventSeverity, ClientEventType } from '@/types/clientEvent'
import ApplicantEventDrawer from './ApplicantEventDrawer.vue'
import ApplicantFinderPanel from './ApplicantFinderPanel.vue'
import { EVENT_TYPE_OPTIONS, SEVERITY_OPTIONS, eventTypeLabel, severityColor } from './logLabel'
import { LOG_PAGE_SIZE, MAX_RANGE_DAYS, RANGE_PRESETS, type DateRange, presetRange, rangeError, toDateTimeRange } from './logQuery'

/*
 * 지원자 이벤트 탭. 지원자 찾기로 지원번호를 고정한 뒤 그 지원자의 브라우저 오류를 본다.
 * 세션 ID·오류 추적번호만으로도 조회할 수 있다(지원번호 없이).
 * 진입 시 자동 조회하지 않는다.
 */

const DEFAULT_RANGE_DAYS = 7

const emit = defineEmits<{ (e: 'open-audit', applicationId: number): void }>()

const range = ref<DateRange>(presetRange(DEFAULT_RANGE_DAYS, new Date()))
const eventType = ref<ClientEventType | undefined>(undefined)
const severity = ref<ClientEventSeverity | undefined>(undefined)
const clientSessionId = ref('')
const relatedCorrelationId = ref('')

const targetApplicationId = ref<number | null>(null)
const targetApplicantName = ref<string | null>(null)
/* a-collapse 는 열린 패널 key 배열을 받는다. 빈 배열이면 접힌 상태. */
const finderKeys = ref<string[]>(['finder'])

const rows = ref<ClientEventLogResponse[]>([])
const page = ref(0)
const totalElements = ref(0)
const loading = ref(false)
const searched = ref(false)
const appliedQuery = ref<Omit<ClientEventLogQuery, 'page' | 'size'>>({})

const columns = [
  { title: '수신시각', key: 'receivedAt', width: 160 },
  { title: '심각도', key: 'severity', width: 90 },
  { title: '이벤트', key: 'eventType', width: 150 },
  { title: '메시지 코드', key: 'message', width: 200 },
  { title: 'HTTP', key: 'httpStatus', width: 70 },
  { title: '화면 / API', key: 'location', ellipsis: true },
  { title: '지원번호', key: 'applicationId', width: 100 },
]

const pagination = computed(() => ({
  current: page.value + 1,
  pageSize: LOG_PAGE_SIZE,
  total: totalElements.value,
  showSizeChanger: false,
}))

const asRow = (record: unknown): ClientEventLogResponse => record as ClientEventLogResponse

const locationText = (row: ClientEventLogResponse): string =>
  row.pageCode ?? row.apiPath ?? row.routePath ?? '-'

const buildQuery = (): Omit<ClientEventLogQuery, 'page' | 'size'> => ({
  ...toDateTimeRange(range.value),
  eventType: eventType.value,
  severity: severity.value,
  applicationId: targetApplicationId.value ?? undefined,
  clientSessionId: clientSessionId.value.trim() || undefined,
  relatedCorrelationId: relatedCorrelationId.value.trim() || undefined,
})

let listRequest = 0

const loadList = async (): Promise<void> => {
  const request = ++listRequest
  loading.value = true
  try {
    const response = await adminClientEventApi.getClientEvents({
      ...appliedQuery.value,
      page: page.value,
      size: LOG_PAGE_SIZE,
    })
    if (request !== listRequest) return
    rows.value = response.data.data.content
    totalElements.value = response.data.data.totalElements
    searched.value = true
  } catch (error) {
    if (request !== listRequest) return
    message.error(getApiErrorMessage(error, '이벤트를 불러오지 못했습니다.'))
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
  eventType.value = undefined
  severity.value = undefined
  clientSessionId.value = ''
  relatedCorrelationId.value = ''
  targetApplicationId.value = null
  targetApplicantName.value = null
  finderKeys.value = ['finder']
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

const onSelectApplicant = async (applicationId: number, applicantName: string | null): Promise<void> => {
  targetApplicationId.value = applicationId
  targetApplicantName.value = applicantName
  finderKeys.value = []
  await nextTick()
  search()
}

const clearTarget = (): void => {
  targetApplicationId.value = null
  targetApplicantName.value = null
  finderKeys.value = ['finder']
  rows.value = []
  totalElements.value = 0
  page.value = 0
  searched.value = false
}

const drawerOpen = ref(false)
const detailId = ref<number | null>(null)

const openDetail = (row: ClientEventLogResponse): void => {
  detailId.value = row.id
  drawerOpen.value = true
}

const onRow = (record: unknown) => ({ onClick: () => openDetail(asRow(record)) })

/*
 * 세션 단위 조회. 조회 대상(지원번호)을 일부러 푼다 — 한 세션에는 지원서 필드가 빈 행도 있어서
 * 지원번호를 함께 걸면 그 행들이 빠진다.
 */
const filterBySession = async (sessionId: string): Promise<void> => {
  targetApplicationId.value = null
  targetApplicantName.value = null
  eventType.value = undefined
  severity.value = undefined
  relatedCorrelationId.value = ''
  clientSessionId.value = sessionId
  await nextTick()
  search()
}

/**
 * 감사 로그 탭에서 넘어올 때: 지원번호만 걸고 바로 조회한다.
 * 넘어온 지원번호의 이력을 놓치지 않기 위해 기간을 최대(90일)로 연다.
 */
const applyApplicationId = async (applicationId: number): Promise<void> => {
  reset()
  range.value = presetRange(MAX_RANGE_DAYS, new Date())
  await nextTick()
  await onSelectApplicant(applicationId, null)
}

defineExpose({ applyApplicationId })
</script>

<template>
  <div class="applicant-event-panel">
    <a-collapse v-model:activeKey="finderKeys" :bordered="false" class="finder-collapse">
      <a-collapse-panel key="finder" header="지원자 찾기 — 이름·휴대폰으로 검색해 지원번호를 찾습니다">
        <ApplicantFinderPanel @select="onSelectApplicant" />
      </a-collapse-panel>
    </a-collapse>

    <a-alert v-if="targetApplicationId !== null" type="success" class="target-bar">
      <template #message>
        <span class="target-text">
          <AimOutlined />
          조회 대상 <b>지원번호 {{ targetApplicationId }}</b>
          <template v-if="targetApplicantName"> · {{ targetApplicantName }}</template>
          <span class="sub">이 지원자의 브라우저 이벤트만 표시합니다</span>
        </span>
      </template>
      <template #action>
        <a-button size="small" type="text" @click="clearTarget">해제</a-button>
      </template>
    </a-alert>

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
          v-model:value="eventType"
          :options="EVENT_TYPE_OPTIONS"
          placeholder="전체 이벤트"
          allow-clear
          class="filter-select"
        />
        <a-select
          v-model:value="severity"
          :options="SEVERITY_OPTIONS"
          placeholder="전체 심각도"
          allow-clear
          class="filter-select-sm"
        />
        <a-input v-model:value="clientSessionId" placeholder="세션 ID(완전일치)" allow-clear class="filter-input" @press-enter="search" />
        <a-input v-model:value="relatedCorrelationId" placeholder="오류 추적번호(완전일치)" allow-clear class="filter-input" @press-enter="search" />
        <span class="spacer" />
        <a-button @click="reset"><template #icon><ReloadOutlined /></template>초기화</a-button>
        <a-button type="primary" :loading="loading" @click="search">
          <template #icon><SearchOutlined /></template>조회
        </a-button>
      </div>
    </section>

    <p class="list-count">
      <template v-if="searched">
        총 <b>{{ totalElements }}</b>건
        <template v-if="targetApplicationId !== null"> · 지원번호 {{ targetApplicationId }} 필터 적용 중</template>
      </template>
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
        <template v-if="column.key === 'receivedAt'">
          {{ formatDate(asRow(record).receivedAt, 'YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.key === 'severity'">
          <a-tag :color="severityColor(asRow(record).severity)">{{ asRow(record).severity }}</a-tag>
        </template>
        <template v-else-if="column.key === 'eventType'">{{ eventTypeLabel(asRow(record).eventType) }}</template>
        <template v-else-if="column.key === 'message'">{{ asRow(record).message ?? '-' }}</template>
        <template v-else-if="column.key === 'httpStatus'">{{ asRow(record).httpStatus ?? '-' }}</template>
        <template v-else-if="column.key === 'location'">{{ locationText(asRow(record)) }}</template>
        <template v-else-if="column.key === 'applicationId'">{{ asRow(record).applicationId ?? '-' }}</template>
      </template>
      <template #emptyText>
        <a-empty
          :description="searched ? '조회 조건에 맞는 이벤트가 없습니다.' : '지원자를 먼저 선택하세요. 세션 ID·오류 추적번호로 바로 조회할 수도 있습니다.'"
        />
      </template>
    </a-table>

    <ApplicantEventDrawer
      v-model:open="drawerOpen"
      :event-id="detailId"
      @filter-session="filterBySession"
      @open-audit="(id: number) => emit('open-audit', id)"
    />
  </div>
</template>

<style scoped lang="scss">
.applicant-event-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.finder-collapse {
  background: var(--app-surface-muted, #fafafa);
}

.target-bar {
  align-items: center;
}

.target-text {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
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
  width: 220px;
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
  margin-left: 8px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

:deep(.ant-table-tbody > tr) {
  cursor: pointer;
}
</style>
