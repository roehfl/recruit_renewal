<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'

import { getAllJobPostings } from '@/api/adminJobPostingApi'
import { messageApi } from '@/api/admin/messageApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import type { AnyMessageType, MessageHistoryQuery, MessageOrigin, MessageSendSummary } from '@/types/admin/message'
import type { AdminJobPostingListItem } from '@/types/jobPosting'
import MessageHistoryDrawer from './MessageHistoryDrawer.vue'
import { MESSAGE_ORIGIN_LABEL, channelCellText, defaultHistoryRange, resultCounts, sendStatusView } from './messageHistory'
import { ALL_MESSAGE_TYPES, messageTypeLabel } from './messageTypes'

type TestFilter = 'ALL' | 'REAL' | 'TEST'
type HistoryFilter = Omit<MessageHistoryQuery, 'page' | 'size'>

const PAGE_SIZE = 20

const TYPE_OPTIONS = ALL_MESSAGE_TYPES.map((meta) => ({ value: meta.type, label: meta.name }))
const ORIGIN_OPTIONS: { value: MessageOrigin; label: string }[] = [
  { value: 'ADMIN', label: MESSAGE_ORIGIN_LABEL.ADMIN },
  { value: 'SYSTEM', label: MESSAGE_ORIGIN_LABEL.SYSTEM },
]
const TEST_FILTER_OPTIONS: { value: TestFilter; label: string }[] = [
  { value: 'ALL', label: '실발송+테스트' },
  { value: 'REAL', label: '실발송만' },
  { value: 'TEST', label: '테스트만' },
]

const columns = [
  { title: '발송일시', dataIndex: 'requestedAt', key: 'requestedAt', width: 140 },
  { title: '발송 구분', dataIndex: 'originLabel', key: 'origin', width: 120 },
  { title: '종류', key: 'type', width: 200 },
  { title: '공고 · 조건', key: 'posting', width: 240 },
  { title: '제목', dataIndex: 'title', key: 'title', ellipsis: true },
  { title: '메일', dataIndex: 'mail', key: 'mail', width: 80 },
  { title: 'SMS', dataIndex: 'sms', key: 'sms', width: 80 },
  { title: '대상', dataIndex: 'recipientCount', key: 'recipientCount', width: 70 },
  { title: '결과', key: 'result', width: 260 },
  { title: '발송자', dataIndex: 'senderName', key: 'senderName', width: 100 },
]

const route = useRoute()
const router = useRouter()

const range = ref<[string, string]>(defaultHistoryRange(new Date()))
const typeFilter = ref<AnyMessageType | undefined>(undefined)
const originFilter = ref<MessageOrigin | undefined>(undefined)
const jobPostingId = ref<number | undefined>(undefined)
const testFilter = ref<TestFilter>('ALL')
const postings = ref<AdminJobPostingListItem[]>([])

const rows = ref<MessageSendSummary[]>([])
const page = ref(0)
const totalElements = ref(0)
const loading = ref(false)
/* 조회 버튼을 누른 시점의 조건. 페이지 이동·새로고침은 이 조건으로 다시 읽는다. */
const appliedFilter = ref<HistoryFilter>({})

const drawerOpen = ref(false)
const detailId = ref<number | null>(null)

const postingOptions = computed(() => postings.value.map((posting) => ({ value: posting.id, label: posting.title })))

const tableRows = computed(() =>
  rows.value.map((summary) => ({
    id: summary.id,
    requestedAt: formatDate(summary.requestedAt, 'YYYY-MM-DD HH:mm'),
    typeLabel: messageTypeLabel(summary.type),
    originLabel: MESSAGE_ORIGIN_LABEL[summary.origin],
    test: summary.test,
    jobPostingTitle: summary.jobPostingTitle ?? '-',
    conditionSummary: summary.conditionSummary ?? '',
    title: summary.title ?? '',
    mail: channelCellText(summary.mailEnabled, summary.mail),
    sms: channelCellText(summary.smsEnabled, summary.sms),
    recipientCount: summary.recipientCount,
    status: sendStatusView(summary.status, summary.delayed),
    counts: resultCounts(summary.mail, summary.sms),
    senderName: summary.senderName ?? '',
  })),
)

type HistoryRow = (typeof tableRows.value)[number]

const pagination = computed(() => ({
  current: page.value + 1,
  pageSize: PAGE_SIZE,
  total: totalElements.value,
  showSizeChanger: false,
}))

const toFilter = (): HistoryFilter => ({
  from: range.value[0],
  to: range.value[1],
  type: typeFilter.value,
  origin: originFilter.value,
  jobPostingId: jobPostingId.value,
  test: testFilter.value === 'ALL' ? undefined : testFilter.value === 'TEST',
})

/* 늦게 도착한 목록 응답은 요청 번호로 버린다. */
let listRequest = 0

const loadList = async (): Promise<void> => {
  const request = ++listRequest
  loading.value = true
  try {
    const response = await messageApi.getHistory({ ...appliedFilter.value, page: page.value, size: PAGE_SIZE })
    if (request !== listRequest) return
    rows.value = response.data.data.content
    totalElements.value = response.data.data.totalElements
  } catch (error) {
    if (request !== listRequest) return
    message.error(getApiErrorMessage(error, '발송 이력을 불러오지 못했습니다.'))
  } finally {
    if (request === listRequest) {
      loading.value = false
    }
  }
}

const search = (): void => {
  appliedFilter.value = toFilter()
  page.value = 0
  void loadList()
}

const handleTableChange = (nextPagination: { current?: number }): void => {
  page.value = (nextPagination.current ?? 1) - 1
  void loadList()
}

const onRangeChange = (_: unknown, dateStrings: [string, string]): void => {
  if (dateStrings[0] && dateStrings[1]) {
    range.value = dateStrings
  }
}

const openDetail = (sendId: number): void => {
  detailId.value = sendId
  drawerOpen.value = true
}

/*
 * 발송 화면의 "이력 보기"는 ?sendId= 로 들어온다. 라우트 컴포넌트가 재사용돼(쿼리만 바뀜)
 * onMounted 가 다시 불리지 않는 이동에도 열리도록 watch 로 처리한다.
 */
watch(
  () => route.query.sendId,
  (sendId) => {
    if (typeof sendId !== 'string') return
    const parsed = Number(sendId)
    if (Number.isInteger(parsed) && parsed > 0) {
      openDetail(parsed)
    }
  },
  { immediate: true },
)

/*
 * 드로어를 닫으면 쿼리의 sendId 를 지워 새로고침해도 다시 열리지 않게 한다.
 * 위 watch 는 sendId 가 없으면 아무 것도 하지 않으므로 이 갱신이 다시 열기로 이어지지 않는다.
 */
watch(drawerOpen, (isOpen) => {
  if (isOpen || route.query.sendId === undefined) return
  const query = Object.fromEntries(Object.entries(route.query).filter(([key]) => key !== 'sendId'))
  void router.replace({ query })
})

onMounted(async () => {
  search()
  try {
    postings.value = await getAllJobPostings()
  } catch (error) {
    message.error(getApiErrorMessage(error, '공고 목록을 불러오지 못했습니다.'))
  }
})
</script>

<template>
  <div class="message-history-view">
    <header class="page-header">
      <div>
        <h1 class="page-title">발송 이력</h1>
        <p class="page-desc">
          발송 요청별 대상·채널 건수와 결과를 봅니다. 결과는 솔루션에서 받는 대로 반영되며, 목록은 새로고침으로 갱신합니다.
        </p>
      </div>
    </header>

    <section class="filters">
      <a-range-picker
        :value="range"
        value-format="YYYY-MM-DD"
        :allow-clear="false"
        :placeholder="['시작일', '종료일']"
        @change="onRangeChange"
      />
      <a-select
        v-model:value="typeFilter"
        class="type-select"
        :options="TYPE_OPTIONS"
        placeholder="종류 전체"
        aria-label="종류"
        allow-clear
      />
      <a-select
        v-model:value="originFilter"
        class="origin-select"
        :options="ORIGIN_OPTIONS"
        placeholder="발송 구분 전체"
        aria-label="발송 구분"
        allow-clear
      />
      <a-select
        v-model:value="jobPostingId"
        class="posting-select"
        :options="postingOptions"
        placeholder="공고 전체"
        aria-label="공고"
        allow-clear
        show-search
        option-filter-prop="label"
      />
      <a-radio-group v-model:value="testFilter" :options="TEST_FILTER_OPTIONS" option-type="button" />
      <a-button type="primary" @click="search"><SearchOutlined /> 조회</a-button>
      <a-button :loading="loading" @click="loadList"><ReloadOutlined /> 새로고침</a-button>
    </section>

    <a-table
      :columns="columns"
      :data-source="tableRows"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      :custom-row="(record: HistoryRow) => ({ onClick: () => openDetail(record.id) })"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'type'">
          {{ record.typeLabel }}
          <a-tag v-if="record.test" color="purple">테스트</a-tag>
        </template>
        <template v-else-if="column.key === 'posting'">
          <div>{{ record.jobPostingTitle }}</div>
          <div class="sub">{{ record.conditionSummary }}</div>
        </template>
        <template v-else-if="column.key === 'result'">
          <a-tag :color="record.status.color">{{ record.status.label }}</a-tag>
          <span class="sub">
            성공 {{ record.counts.sent }} · 실패 {{ record.counts.failed }} · 수신 중 {{ record.counts.inProgress }}
          </span>
        </template>
      </template>
    </a-table>

    <MessageHistoryDrawer v-model:open="drawerOpen" :send-id="detailId" />
  </div>
</template>

<style scoped lang="scss">
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 18px;
}

.page-title {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 700;
}

.page-desc {
  margin: 0;
  color: var(--app-text-secondary);
}

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.type-select {
  width: 170px;
}

.origin-select {
  width: 150px;
}

.posting-select {
  width: 280px;
}

.sub {
  font-size: 12px;
  color: var(--app-text-secondary);
}

:deep(.ant-table-row) {
  cursor: pointer;
}
</style>
