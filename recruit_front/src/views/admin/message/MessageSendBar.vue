<script setup lang="ts">
import { MailOutlined, MessageOutlined, SendOutlined, WarningOutlined } from '@ant-design/icons-vue'

import type { SendSummary } from './messageSendSummary'

defineProps<{
  typeName: string
  summary: SendSummary
  mailEnabled: boolean
  smsEnabled: boolean
  tested: boolean
  sending: boolean
}>()

const emit = defineEmits<{
  send: []
}>()
</script>

<template>
  <div class="send-bar">
    <div class="bar-summary">
      <a-tag color="green">{{ typeName }}</a-tag>
      <span>수신 대상 <strong>{{ summary.recipientCount }}명</strong></span>
      <span class="divider" />
      <span>
        <MailOutlined /> 메일 <strong>{{ mailEnabled ? `${summary.mailCount}건` : '제외' }}</strong>
        <span v-if="summary.mailMissing" class="missing">(이메일 없음 {{ summary.mailMissing }})</span>
      </span>
      <span>
        <MessageOutlined /> 문자
        <strong>{{ smsEnabled ? `SMS ${summary.smsCount} · LMS ${summary.lmsCount}건` : '제외' }}</strong>
        <span v-if="summary.smsMissing" class="missing">(휴대폰 없음 {{ summary.smsMissing }})</span>
      </span>
    </div>
    <div class="bar-actions">
      <span v-if="summary.blockReason" class="block-reason">{{ summary.blockReason }}</span>
      <span v-else-if="!tested" class="untested"><WarningOutlined /> 아직 테스트 발송 안 함</span>
      <a-button
        type="primary"
        size="large"
        :disabled="summary.blockReason !== null"
        :loading="sending"
        @click="emit('send')"
      >
        <SendOutlined /> 발송하기
      </a-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.send-bar {
  position: sticky;
  bottom: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 18px;
  margin-top: 16px;
  padding: 12px 20px;
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius-lg);
  background: var(--app-bg-surface);
  box-shadow: 0 -6px 18px rgb(0 0 0 / 5%);
}

.bar-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  font-size: 13px;
  color: var(--app-text-secondary);

  strong {
    color: var(--app-text-primary);
  }
}

.divider {
  width: 1px;
  height: 18px;
  background: var(--app-border-default);
}

.missing,
.block-reason {
  color: var(--app-color-error);
}

.bar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
  font-size: 13px;
}

.untested {
  color: var(--app-color-warning);
}
</style>
