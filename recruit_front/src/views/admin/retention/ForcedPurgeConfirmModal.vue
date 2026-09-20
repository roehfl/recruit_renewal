<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'

import { retentionApi } from '@/api/admin/retentionApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { DataSubjectDetail, ForcedPurgeReason, PurgeBatchDetail } from '@/types/admin/retention'
import { forcedPurgeReasonLabel } from './retentionLabel'

/*
 * 강제 파기 확인 모달. 이 화면의 유일한 안전장치라 대상자 재확인 → 진행 중 전형 경고 →
 * 고정 경고 → 사유 선택 → 확인 체크 → 실행 버튼 순서를 지킨다.
 * 실패해도 모달을 닫지 않는다(사용자가 상황을 보고 다시 시도하거나 닫게 한다).
 * forcePurge는 이 모달이 직접 호출한다(호출부가 한 곳뿐이라 배선 실수 여지를 줄이는 의도).
 */

const props = defineProps<{ detail: DataSubjectDetail | null }>()
const open = defineModel<boolean>('open', { required: true })
const emit = defineEmits<{ purged: [result: PurgeBatchDetail] }>()

const reasonCode = ref<ForcedPurgeReason>('DATA_SUBJECT_REQUEST')
const confirmed = ref(false)
const submitting = ref(false)

// 열릴 때·닫힐 때 양쪽 다 리셋한다(이중 방어). 체크박스가 남은 채 다시 열리면 안전장치가 무력해진다.
watch(open, () => {
  reasonCode.value = 'DATA_SUBJECT_REQUEST'
  confirmed.value = false
})

const inProgressCount = computed(
  () => props.detail?.applications.filter((app) => app.reasonCode === 'APPLICATION_NOT_TERMINAL').length ?? 0,
)

const onSubmit = async (): Promise<void> => {
  if (props.detail === null || !confirmed.value) return

  const applicantId = props.detail.applicantId
  submitting.value = true
  try {
    const response = await retentionApi.forcePurge({ applicantId, reasonCode: reasonCode.value, confirm: true })
    const result = response.data.data
    const { purgedCount, skippedCount, failedCount, pendingCount } = result.batch

    message.success(`지원서 ${purgedCount}건 파기 완료(스킵 ${skippedCount}건, 실패 ${failedCount}건)`)
    if (failedCount > 0 || pendingCount > 0) {
      message.warning('일부 항목이 완료되지 않았습니다. 파기 이력에서 확인하세요.')
    }

    emit('purged', result)
    open.value = false
  } catch (error) {
    message.error(getApiErrorMessage(error, '파기를 실행하지 못했습니다.'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <a-modal
    v-model:open="open"
    title="삭제 요청 파기 확인"
    width="540px"
    :closable="!submitting"
    :mask-closable="!submitting"
    :keyboard="!submitting"
  >
    <template v-if="detail">
      <p class="subject-line">
        <strong>{{ detail.name ?? '-' }}</strong>
        <span class="sep">·</span>
        <strong>{{ detail.email ?? '-' }}</strong>
        <span class="sep">·</span>
        <strong>{{ detail.phoneNumber ?? '-' }}</strong>
      </p>

      <a-alert
        v-if="inProgressCount > 0"
        class="notice"
        type="error"
        show-icon
        :message="`진행 중인 전형 ${inProgressCount}건이 함께 파기됩니다. 해당 지원자는 더 이상 전형에 참여할 수 없습니다.`"
      />

      <a-alert
        class="notice"
        type="warning"
        show-icon
        message="계정이 익명화되어 이 지원자는 다시 로그인할 수 없습니다. 재지원은 신규 가입이 필요합니다. 이 작업은 되돌릴 수 없습니다."
      />

      <a-radio-group v-model:value="reasonCode" class="reason-group">
        <a-radio value="DATA_SUBJECT_REQUEST">{{ forcedPurgeReasonLabel('DATA_SUBJECT_REQUEST') }}</a-radio>
        <a-radio value="DUPLICATE_ACCOUNT">{{ forcedPurgeReasonLabel('DUPLICATE_ACCOUNT') }}</a-radio>
        <a-radio value="OTHER">{{ forcedPurgeReasonLabel('OTHER') }}</a-radio>
      </a-radio-group>
      <p class="reason-hint">상세 경위는 접수 대장에 기록하세요. 파기 기록에는 사유 구분만 남습니다.</p>

      <a-checkbox v-model:checked="confirmed" class="confirm-check">
        위 내용을 확인했으며 파기에 동의합니다.
      </a-checkbox>
    </template>

    <template #footer>
      <a-button :disabled="submitting" @click="open = false">닫기</a-button>
      <a-button type="primary" danger :disabled="!confirmed" :loading="submitting" @click="onSubmit">
        파기 실행
      </a-button>
    </template>
  </a-modal>
</template>

<style scoped lang="scss">
.subject-line {
  margin: 0 0 12px;
  font-size: 14px;
}

.sep {
  margin: 0 6px;
  color: var(--app-text-muted);
}

.notice {
  margin-bottom: 12px;
}

.reason-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 4px;
}

.reason-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.confirm-check {
  margin-top: 14px;
}
</style>
