<script setup lang="ts">
import { computed, ref } from 'vue'

import { formatDate } from '@/common/dateUtil'
import type { MessageTargetRecipient, MessageType } from '@/types/admin/message'
import { RESULT_LABEL, interviewGroupLabel, isInterviewType, recipientResultTag } from './messageCondition'

const props = defineProps<{
  type: MessageType
  recipients: MessageTargetRecipient[]
}>()

const open = defineModel<boolean>('open', { required: true })
const selectedIds = defineModel<number[]>('selectedIds', { required: true })

const keyword = ref('')
const onlyMissing = ref(false)

const selectable = (recipient: MessageTargetRecipient): boolean => recipient.mailAvailable || recipient.smsAvailable

const filtered = computed(() => {
  const word = keyword.value.trim()
  return props.recipients.filter(
    (recipient) =>
      (!word || (recipient.name ?? '').includes(word) || String(recipient.applicationId).includes(word)) &&
      (!onlyMissing.value || !recipient.mailAvailable || !recipient.smsAvailable),
  )
})

const detailTitle = computed(() => {
  if (props.type === 'RESULT_ANNOUNCEMENT') return '결과'
  if (isInterviewType(props.type)) return '조 · 면접 일시'
  if (props.type === 'DEADLINE_REMINDER') return '작성 시작'
  return null
})

const columns = computed(() => [
  { title: '수험번호', dataIndex: 'applicationId', key: 'applicationId', width: 100 },
  { title: '이름', dataIndex: 'name', key: 'name', width: 110 },
  ...(detailTitle.value ? [{ title: detailTitle.value, key: 'detail', width: 190 }] : []),
  { title: '휴대폰', dataIndex: 'phone', key: 'phone', width: 140 },
  { title: '이메일', dataIndex: 'email', key: 'email' },
  { title: '발송 채널', key: 'channels', width: 170 },
])

const detailText = (recipient: MessageTargetRecipient): string => {
  if (isInterviewType(props.type)) {
    return `${interviewGroupLabel(recipient.interviewGroup)} · ${formatDate(recipient.interviewDateTime, 'YYYY-MM-DD HH:mm')}`
  }
  if (props.type === 'DEADLINE_REMINDER') {
    return formatDate(recipient.draftStartedAt, 'YYYY-MM-DD HH:mm')
  }
  return recipient.resultStatus ? RESULT_LABEL[recipient.resultStatus] : ''
}

const resultTag = (recipient: MessageTargetRecipient) => recipientResultTag(recipient.resultStatus)

const rowSelection = computed(() => ({
  selectedRowKeys: selectedIds.value,
  preserveSelectedRowKeys: true,
  onChange: (keys: (string | number)[]) => {
    selectedIds.value = keys.map(Number)
  },
  getCheckboxProps: (recipient: MessageTargetRecipient) => ({ disabled: !selectable(recipient) }),
}))

const selectAll = (): void => {
  selectedIds.value = props.recipients.filter(selectable).map((recipient) => recipient.applicationId)
}

const clearAll = (): void => {
  selectedIds.value = []
}
</script>

<template>
  <a-drawer
    v-model:open="open"
    title="수신자 선택"
    width="900"
    :body-style="{ padding: 0 }"
    :footer-style="{ padding: '12px 24px' }"
  >
    <div class="drawer-tools">
      <a-input v-model:value="keyword" class="search" placeholder="이름·수험번호 검색" allow-clear />
      <a-checkbox v-model:checked="onlyMissing">연락처 누락만 보기</a-checkbox>
      <a-space class="tools-right">
        <span class="count">{{ selectedIds.length }} / {{ recipients.length }}명 선택</span>
        <a-button size="small" @click="selectAll">전체 선택</a-button>
        <a-button size="small" @click="clearAll">전체 해제</a-button>
      </a-space>
    </div>
    <a-table
      class="recipient-table"
      :columns="columns"
      :data-source="filtered"
      :row-selection="rowSelection"
      row-key="applicationId"
      size="small"
      :pagination="{ pageSize: 50, showSizeChanger: false }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'detail'">
          <a-tag
            v-if="type === 'RESULT_ANNOUNCEMENT' && resultTag(record as MessageTargetRecipient)"
            :color="resultTag(record as MessageTargetRecipient)?.color"
          >
            {{ resultTag(record as MessageTargetRecipient)?.label }}
          </a-tag>
          <template v-else-if="type !== 'RESULT_ANNOUNCEMENT'">{{ detailText(record as MessageTargetRecipient) }}</template>
        </template>
        <template v-else-if="column.key === 'phone'">{{ (record as MessageTargetRecipient).phone ?? '-' }}</template>
        <template v-else-if="column.key === 'email'">{{ (record as MessageTargetRecipient).email ?? '-' }}</template>
        <template v-else-if="column.key === 'channels'">
          <a-tag :color="(record as MessageTargetRecipient).mailAvailable ? undefined : 'red'">
            {{ (record as MessageTargetRecipient).mailAvailable ? '메일' : '이메일 없음' }}
          </a-tag>
          <a-tag :color="(record as MessageTargetRecipient).smsAvailable ? undefined : 'red'">
            {{ (record as MessageTargetRecipient).smsAvailable ? 'SMS' : '휴대폰 없음' }}
          </a-tag>
        </template>
      </template>
    </a-table>
    <template #footer>
      <div class="drawer-footer">
        <span>연락처가 없거나 형식이 맞지 않는 채널은 자동으로 제외됩니다. 연락처는 지원서 기본정보, 없으면 회원정보 기준입니다.</span>
        <a-button type="primary" @click="open = false">선택 완료</a-button>
      </div>
    </template>
  </a-drawer>
</template>

<style scoped lang="scss">
.drawer-tools {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 24px;
  border-bottom: 1px solid var(--app-border-default);
  background: var(--app-bg-muted);
}

.recipient-table {
  padding: 16px 24px;
}

.search {
  width: 220px;
}

/* 기본 선택 행 색은 진한 주색에서 파생돼 칙칙하다. 다른 목록 화면과 같은 연한 색을 쓴다. */
:deep(.ant-table-tbody > tr.ant-table-row-selected > td) {
  background: var(--app-bg-selected);
}

:deep(.ant-table-tbody > tr.ant-table-row-selected:hover > td) {
  background: #e8f0de;
}

.tools-right {
  margin-left: auto;
}

.count {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.drawer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  color: var(--app-text-secondary);
}
</style>
