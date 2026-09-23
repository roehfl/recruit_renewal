<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { message } from 'ant-design-vue'

import { messageApi } from '@/api/admin/messageApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import type { MessageHistoryRecipient, MessageSendDetail } from '@/types/admin/message'
import {
  DELIVERY_STATUS_COLOR,
  DELIVERY_STATUS_LABEL,
  channelSummaryText,
  failureReasonLabel,
  recipientNameLabel,
  sendStatusView,
} from './messageHistory'
import { messageTypeLabel } from './messageTypes'

/* 완료가 아니면 열려 있는 동안 5초마다 다시 읽는다(설계서 3.2). */
const REFRESH_INTERVAL_MS = 5000

const props = defineProps<{
  /** 열 발송 번호 */
  sendId: number | null
}>()

const open = defineModel<boolean>('open', { required: true })

const detail = ref<MessageSendDetail | null>(null)
const loading = ref(false)

/* 닫거나 다른 발송을 연 뒤 늦게 도착한 응답은 요청 번호로 버린다. */
let request = 0
let refreshTimer: ReturnType<typeof setTimeout> | null = null

const stopRefresh = (): void => {
  if (refreshTimer !== null) {
    clearTimeout(refreshTimer)
    refreshTimer = null
  }
}

const status = computed(() => (detail.value ? sendStatusView(detail.value.status, detail.value.delayed) : null))

const recipientColumns = [
  { title: '수험번호', key: 'applicationId', width: 100 },
  { title: '이름', key: 'name', width: 110 },
  { title: '이메일', key: 'email' },
  { title: '휴대폰', key: 'phone', width: 130 },
  { title: '메일 결과', key: 'mail', width: 190 },
  { title: 'SMS 결과', key: 'sms', width: 220 },
]

const asRecipient = (record: unknown): MessageHistoryRecipient => record as MessageHistoryRecipient

const load = async (): Promise<void> => {
  const sendId = props.sendId
  if (sendId === null || !open.value) return
  const current = ++request
  stopRefresh()
  loading.value = detail.value === null
  try {
    const response = await messageApi.getHistoryDetail(sendId)
    if (current !== request) return
    const loaded = response.data.data
    detail.value = loaded
    if (loaded.status !== 'COMPLETED') {
      refreshTimer = setTimeout(() => {
        refreshTimer = null
        void load()
      }, REFRESH_INTERVAL_MS)
    }
  } catch (error) {
    if (current !== request) return
    if (detail.value === null) {
      message.error(getApiErrorMessage(error, '발송 상세를 불러오지 못했습니다.'))
    } else if (open.value) {
      /* 이미 상세를 보여 주는 중이면(폴링 중) 이번 실패는 조용히 넘기고 다음 주기에 다시 시도한다. */
      refreshTimer = setTimeout(() => {
        refreshTimer = null
        void load()
      }, REFRESH_INTERVAL_MS)
    }
  } finally {
    if (current === request) {
      loading.value = false
    }
  }
}

watch(
  [open, () => props.sendId],
  ([isOpen]) => {
    request += 1
    stopRefresh()
    loading.value = false
    if (isOpen) {
      detail.value = null
      void load()
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  request += 1
  stopRefresh()
})
</script>

<template>
  <a-drawer v-model:open="open" :title="detail ? `발송 상세 · 발송 번호 ${detail.id}` : '발송 상세'" width="980">
    <a-spin :spinning="loading">
      <template v-if="detail">
        <a-descriptions bordered size="small" :column="2">
          <a-descriptions-item label="종류">
            {{ messageTypeLabel(detail.type) }}
            <a-tag v-if="detail.test" color="purple">테스트</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="발송일시">{{ formatDate(detail.requestedAt, 'YYYY-MM-DD HH:mm:ss') }}</a-descriptions-item>
          <a-descriptions-item label="공고">{{ detail.jobPostingTitle ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="전형 · 조건">{{ detail.conditionSummary || '-' }}</a-descriptions-item>
          <a-descriptions-item label="템플릿">{{ detail.templateName ?? '직접 작성' }}</a-descriptions-item>
          <a-descriptions-item label="발송자">{{ detail.senderName ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="상태">
            <a-tag v-if="status" :color="status.color">{{ status.label }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="대상">{{ detail.recipientCount }}명</a-descriptions-item>
          <a-descriptions-item label="메일">{{ channelSummaryText(detail.mailEnabled, detail.mail) }}</a-descriptions-item>
          <a-descriptions-item label="SMS">{{ channelSummaryText(detail.smsEnabled, detail.sms) }}</a-descriptions-item>
        </a-descriptions>

        <h3 class="section-title">발송 원문 <span class="section-hint">변수 치환 전</span></h3>
        <div class="originals">
          <div class="original">
            <div class="original-label">메일</div>
            <template v-if="detail.mailEnabled">
              <div class="original-subject">{{ detail.mailSubject }}</div>
              <pre class="original-body">{{ detail.mailBody }}</pre>
            </template>
            <p v-else class="muted">이번 발송에서 제외</p>
          </div>
          <div class="original">
            <div class="original-label">SMS</div>
            <pre v-if="detail.smsEnabled" class="original-body">{{ detail.smsBody }}</pre>
            <p v-else class="muted">이번 발송에서 제외</p>
          </div>
        </div>

        <h3 class="section-title">수신자별 결과 <span class="section-hint">{{ detail.recipients.length }}명</span></h3>
        <a-table
          :columns="recipientColumns"
          :data-source="detail.recipients"
          row-key="id"
          size="small"
          :pagination="{ pageSize: 50, showSizeChanger: false }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'applicationId'">{{ asRecipient(record).applicationId ?? '테스트' }}</template>
            <template v-else-if="column.key === 'name'">{{ recipientNameLabel(asRecipient(record)) }}</template>
            <template v-else-if="column.key === 'email'">{{ asRecipient(record).email ?? '-' }}</template>
            <template v-else-if="column.key === 'phone'">{{ asRecipient(record).phone ?? '-' }}</template>
            <template v-else-if="column.key === 'mail'">
              <a-tag :color="DELIVERY_STATUS_COLOR[asRecipient(record).mailStatus]">
                {{ DELIVERY_STATUS_LABEL[asRecipient(record).mailStatus] }}
              </a-tag>
              <span class="reason">{{ failureReasonLabel(asRecipient(record).mailFailureReason) }}</span>
            </template>
            <template v-else-if="column.key === 'sms'">
              <a-tag :color="DELIVERY_STATUS_COLOR[asRecipient(record).smsStatus]">
                {{ DELIVERY_STATUS_LABEL[asRecipient(record).smsStatus] }}
              </a-tag>
              <span v-if="asRecipient(record).smsKind" class="kind">{{ asRecipient(record).smsKind }}</span>
              <span class="reason">{{ failureReasonLabel(asRecipient(record).smsFailureReason) }}</span>
            </template>
          </template>
        </a-table>
      </template>
      <a-empty v-else-if="!loading" description="발송 상세를 불러오지 못했습니다." />
    </a-spin>
  </a-drawer>
</template>

<style scoped lang="scss">
.section-title {
  margin: 20px 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.section-hint {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 400;
  color: var(--app-text-secondary);
}

.originals {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.original {
  padding: 12px;
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius-lg);
  background: var(--app-bg-muted);
}

.original-label {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--app-text-secondary);
}

.original-subject {
  margin-bottom: 6px;
  font-weight: 600;
}

.original-body {
  margin: 0;
  font-family: inherit;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
}

.muted,
.reason,
.kind {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.kind {
  margin-right: 4px;
}
</style>
