<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'

import { retentionApi } from '@/api/admin/retentionApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import type { RetentionSchedule } from '@/types/admin/retention'
import { formatNextPurgeDate, NO_SCHEDULE_DATE, runResultLabel } from './retentionLabel'

/*
 * 자동 파기 on/off·다음 예정일·마지막 실행 결과 카드.
 * 보존 정책이 바뀌면(RetentionPolicyCard의 changed) 부모가 policyVersion을 올려 다시 읽는다.
 */

const props = defineProps<{ canEdit: boolean; policyVersion: number }>()
const emit = defineEmits<{ 'open-batch': [id: number] }>()

const schedule = ref<RetentionSchedule | null>(null)
const loading = ref(false)
const toggling = ref(false)

const loadSchedule = async (): Promise<void> => {
  loading.value = true
  try {
    const response = await retentionApi.getSchedule()
    schedule.value = response.data.data
  } catch (error) {
    message.error(getApiErrorMessage(error, '자동 파기 설정을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

onMounted(loadSchedule)
watch(() => props.policyVersion, loadSchedule)

const switchDisabled = computed(() => !props.canEdit || !(schedule.value?.hasPolicy ?? false))
const switchTooltip = computed(() =>
  schedule.value && !schedule.value.hasPolicy ? '보존 정책을 먼저 등록하세요' : undefined,
)

const nextPurgeDateText = computed(() =>
  schedule.value ? formatNextPurgeDate(schedule.value.nextPurgeDate) : '',
)
const showNextPurgeHint = computed(
  () => schedule.value !== null && schedule.value.nextPurgeDate !== NO_SCHEDULE_DATE,
)

const lastRunAtText = computed(() =>
  schedule.value?.lastRunAt ? formatDate(schedule.value.lastRunAt, 'YYYY-MM-DD HH:mm') : '-',
)
const isLastRunError = computed(() => schedule.value?.lastRunResult === 'ERROR')

const showBatchLink = computed(
  () => schedule.value?.lastRunResult === 'EXECUTED' && schedule.value?.lastRunBatchId != null,
)
const onOpenBatch = (): void => {
  const batchId = schedule.value?.lastRunBatchId
  if (batchId != null) emit('open-batch', batchId)
}

const applyToggle = async (next: boolean): Promise<void> => {
  toggling.value = true
  try {
    const response = await retentionApi.updateSchedule(next)
    schedule.value = response.data.data
  } catch (error) {
    // :checked가 schedule.enabled를 그대로 반영하므로 실패 시 별도로 되돌리지 않아도 이전 값으로 보인다.
    message.error(getApiErrorMessage(error, '자동 파기 설정을 변경하지 못했습니다.'))
  } finally {
    toggling.value = false
  }
}

const onToggle = (checked: boolean | string | number): void => {
  const next = checked === true

  if (!next) {
    Modal.confirm({
      title: '보존기간이 지난 개인정보가 자동으로 파기되지 않습니다. 끄시겠습니까?',
      okText: '끄기',
      okType: 'danger',
      cancelText: '취소',
      onOk: () => applyToggle(false),
    })
    return
  }

  void applyToggle(true)
}
</script>

<template>
  <section class="schedule-card">
    <header class="card-head">
      <h3 class="card-title">자동 파기</h3>
    </header>

    <div class="card-body">
      <a-spin :spinning="loading">
        <div class="switch-row">
          <a-tooltip :title="switchTooltip">
            <a-switch
              :checked="schedule?.enabled ?? false"
              :disabled="switchDisabled"
              :loading="toggling"
              @change="onToggle"
            />
          </a-tooltip>
          <span class="switch-label">{{ schedule?.enabled ? '켜짐' : '꺼짐' }}</span>
        </div>

        <template v-if="schedule">
          <p class="field-line">
            <span class="field-label">다음 파기 예정일</span>
            <span>{{ nextPurgeDateText }}</span>
          </p>
          <p v-if="showNextPurgeHint" class="field-hint">이 날짜부터 파기 대상이 생길 수 있습니다.</p>

          <p class="field-line">
            <span class="field-label">마지막 실행</span>
            <span :class="{ 'last-run-error': isLastRunError }">
              {{ lastRunAtText }} · {{ runResultLabel(schedule.lastRunResult) }}
            </span>
          </p>
          <p v-if="isLastRunError" class="field-hint last-run-error">서버 로그를 확인하세요.</p>
          <a-button v-if="showBatchLink" type="link" size="small" class="batch-link" @click="onOpenBatch">
            파기 이력 보기
          </a-button>
        </template>

        <p class="field-hint">매일 03:00에 확인합니다. 예정일 전에는 스캔하지 않습니다.</p>
      </a-spin>
    </div>
  </section>
</template>

<style scoped lang="scss">
.schedule-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 16px 18px 18px;
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius-lg);
  background: var(--app-bg-surface);
  box-shadow: var(--app-shadow-soft);
}

.card-head {
  margin-bottom: 12px;
}

.card-title {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--app-text-primary);
}

.switch-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.switch-label {
  font-size: 13px;
  color: var(--app-text-secondary);
}

.field-line {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--app-text-primary);
}

.field-label {
  color: var(--app-text-secondary);
}

.field-hint {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--app-text-muted);
}

.last-run-error {
  color: var(--app-color-error);
}

.batch-link {
  padding: 0;
  height: auto;
  margin-top: 4px;
}
</style>
