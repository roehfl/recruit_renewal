<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import type { SendSummary } from './messageSendSummary'

const props = defineProps<{
  typeName: string
  postingTitle: string
  conditionText: string
  templateName: string | null
  dirty: boolean
  summary: SendSummary
  mailEnabled: boolean
  smsEnabled: boolean
  tested: boolean
  sending: boolean
}>()

const open = defineModel<boolean>('open', { required: true })

const emit = defineEmits<{
  confirm: []
}>()

const confirmed = ref(false)

watch(open, (value) => {
  if (value) {
    confirmed.value = false
  }
})

const templateText = computed(() =>
  props.templateName ? `${props.templateName}${props.dirty ? ' (수정됨)' : ''}` : '새로 작성',
)

const channelText = computed(() =>
  [
    props.mailEnabled ? `메일 ${props.summary.mailCount}건` : '메일 제외',
    props.smsEnabled ? `SMS ${props.summary.smsCount}건 · LMS ${props.summary.lmsCount}건` : 'SMS 제외',
  ].join(' / '),
)
</script>

<template>
  <a-modal
    v-model:open="open"
    title="메시지를 발송할까요?"
    width="540px"
    :closable="!sending"
    :mask-closable="!sending"
    :keyboard="!sending"
  >
    <p class="lead">발송 후에는 취소할 수 없습니다. 아래 내용을 확인하세요.</p>
    <a-descriptions bordered size="small" :column="1">
      <a-descriptions-item label="종류">{{ typeName }}</a-descriptions-item>
      <a-descriptions-item label="공고 · 조건">
        {{ postingTitle }}<template v-if="conditionText"> · {{ conditionText }}</template>
      </a-descriptions-item>
      <a-descriptions-item label="수신 대상"><strong>{{ summary.recipientCount }}명</strong></a-descriptions-item>
      <a-descriptions-item label="채널">{{ channelText }}</a-descriptions-item>
      <a-descriptions-item label="템플릿">{{ templateText }}</a-descriptions-item>
    </a-descriptions>
    <a-alert
      v-if="!tested"
      class="notice"
      type="warning"
      show-icon
      message="아직 테스트 발송을 하지 않았습니다. 실제 수신 화면을 먼저 확인하는 것을 권장합니다."
    />
    <a-alert
      v-if="summary.missingVariableRecipients > 0"
      class="notice"
      type="warning"
      show-icon
      :message="`값이 없는 변수가 있는 수신자가 ${summary.missingVariableRecipients}명 있습니다. 그 자리는 빈칸으로 발송됩니다.`"
    />
    <a-checkbox v-model:checked="confirmed" class="confirm-check">대상자와 내용을 확인했습니다</a-checkbox>
    <template #footer>
      <a-button :disabled="sending" @click="open = false">취소</a-button>
      <a-button type="primary" :disabled="!confirmed" :loading="sending" @click="emit('confirm')">
        {{ summary.recipientCount }}명에게 발송
      </a-button>
    </template>
  </a-modal>
</template>

<style scoped lang="scss">
.lead {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--app-text-secondary);
}

.notice {
  margin-top: 12px;
}

.confirm-check {
  margin-top: 14px;
}
</style>
