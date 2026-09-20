<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'

import { retentionApi } from '@/api/admin/retentionApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { RetentionBaselineType, RetentionPolicy, RetentionPolicySaveRequest } from '@/types/admin/retention'

/*
 * 전역 보존 정책(jobPostingId === null) 카드. 공고별 정책은 이 화면 범위 밖이다.
 * canEdit(ROLE_PRIVACY_ADMIN)이 없으면 등록·수정 버튼을 렌더링하지 않아
 * createPolicy·updatePolicy(PRIVACY 전용 API)가 호출되지 않게 한다.
 */

const props = defineProps<{ canEdit: boolean }>()
const emit = defineEmits<{ changed: [] }>()

const BASELINE_OPTIONS: { value: RetentionBaselineType; label: string }[] = [
  { value: 'CLOSED_AT', label: '공고 마감일' },
  { value: 'HIRING_ENDED_AT', label: '채용 종료일' },
]

const baselineLabel = (baselineType: RetentionBaselineType): string =>
  baselineType === 'CLOSED_AT' ? '공고 마감일' : '채용 종료일'

// 정확히 연 단위로 나누어떨어질 때만 "(N년)"을 덧붙인다.
const yearsSuffix = (days: number): string => (days > 0 && days % 365 === 0 ? `(${days / 365}년)` : '')

const summaryText = (policy: RetentionPolicy): string =>
  `${baselineLabel(policy.baselineType)} 기준 ${policy.retentionPeriodDays}일${yearsSuffix(policy.retentionPeriodDays)} 보관 후 파기`

const policies = ref<RetentionPolicy[]>([])
const loading = ref(false)

/*
 * 전역 정책(jobPostingId === null)은 enabled 여부와 무관하게 먼저 전부 모은다.
 * "정책이 아예 없음"과 "정책은 있는데 꺼짐"을 구분해야 꺼진 정책을 다시 켤 방법이 화면에서 사라지지 않는다.
 */
const globalPolicies = computed(() => policies.value.filter((policy) => policy.jobPostingId === null))
// 사용 중인 전역 정책. 2건 이상이면 겹침 검증을 빠져나간 과거 데이터다.
const enabledGlobalPolicies = computed(() => globalPolicies.value.filter((policy) => policy.enabled))

// 이 카드가 보여줄 단일 정책. enabled 정책이 하나면 그것, 없으면(전부 꺼짐) 첫 번째 전역 정책을 보여준다.
const primaryPolicy = computed<RetentionPolicy | null>(() => {
  if (enabledGlobalPolicies.value.length === 1) return enabledGlobalPolicies.value[0] ?? null
  if (enabledGlobalPolicies.value.length === 0 && globalPolicies.value.length > 0) {
    return globalPolicies.value[0] ?? null
  }
  return null
})

const loadPolicies = async (): Promise<void> => {
  loading.value = true
  try {
    const response = await retentionApi.getPolicies()
    policies.value = response.data.data
  } catch (error) {
    message.error(getApiErrorMessage(error, '보존 정책을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

const modalOpen = ref(false)
const saving = ref(false)
const editingPolicy = ref<RetentionPolicy | null>(null)
const form = reactive<{ retentionPeriodDays: number; baselineType: RetentionBaselineType; enabled: boolean }>({
  retentionPeriodDays: 1825,
  baselineType: 'CLOSED_AT',
  enabled: true,
})

const openCreate = (): void => {
  if (!props.canEdit) return
  editingPolicy.value = null
  form.retentionPeriodDays = 1825
  form.baselineType = 'CLOSED_AT'
  form.enabled = true
  modalOpen.value = true
}

const openEdit = (policy: RetentionPolicy): void => {
  if (!props.canEdit) return
  editingPolicy.value = policy
  form.retentionPeriodDays = policy.retentionPeriodDays
  form.baselineType = policy.baselineType
  form.enabled = policy.enabled
  modalOpen.value = true
}

const doSave = async (): Promise<void> => {
  if (!props.canEdit) return

  // 전체 교체 API라 이 화면에서 다루지 않는 유효기간은 기존 값을 그대로 보존한다.
  const request: RetentionPolicySaveRequest = {
    jobPostingId: null,
    retentionPeriodDays: form.retentionPeriodDays,
    baselineType: form.baselineType,
    enabled: form.enabled,
    effectiveFrom: editingPolicy.value?.effectiveFrom ?? null,
    effectiveTo: editingPolicy.value?.effectiveTo ?? null,
  }

  saving.value = true
  try {
    if (editingPolicy.value === null) {
      await retentionApi.createPolicy(request)
    } else {
      await retentionApi.updatePolicy(editingPolicy.value.id, request)
    }
    message.success('보존 정책을 저장했습니다.')
    modalOpen.value = false
    emit('changed')
    await loadPolicies()
  } catch (error) {
    message.error(getApiErrorMessage(error, '보존 정책을 저장하지 못했습니다.'))
  } finally {
    saving.value = false
  }
}

const save = (): void => {
  if (!props.canEdit) return

  if (!form.retentionPeriodDays || form.retentionPeriodDays < 1) {
    message.warning('보존 일수는 1일 이상이어야 합니다.')
    return
  }

  const isDecreasing =
    editingPolicy.value !== null && form.retentionPeriodDays < editingPolicy.value.retentionPeriodDays

  if (isDecreasing) {
    Modal.confirm({
      title: '보존 일수를 줄이면 다음 자동 파기 때 더 많은 지원서가 파기됩니다. 계속할까요?',
      okText: '계속',
      cancelText: '취소',
      onOk: doSave,
    })
    return
  }

  void doSave()
}

onMounted(loadPolicies)
</script>

<template>
  <section class="policy-card">
    <header class="card-head">
      <h3 class="card-title">보존 정책</h3>
    </header>

    <div class="card-body">
      <a-spin :spinning="loading">
        <a-alert
          v-if="globalPolicies.length === 0"
          type="error"
          show-icon
          message="보존 정책이 없어 자동 파기가 동작하지 않습니다"
        />

        <template v-else-if="enabledGlobalPolicies.length >= 2">
          <a-alert
            type="warning"
            show-icon
            message="전역 보존 정책이 2건 이상입니다"
            description="겹침 검증을 빠져나간 과거 데이터입니다. 정리가 필요합니다."
          />
          <ul class="policy-list">
            <li v-for="policy in enabledGlobalPolicies" :key="policy.id" class="policy-list-item">
              <span>{{ summaryText(policy) }}</span>
              <a-button v-if="canEdit" size="small" @click="openEdit(policy)">수정</a-button>
            </li>
          </ul>
        </template>

        <template v-else-if="primaryPolicy">
          <a-alert
            v-if="!primaryPolicy.enabled"
            type="warning"
            show-icon
            message="보존 정책이 꺼져 있어 자동 파기가 동작하지 않습니다"
          />
          <p class="summary-text">{{ summaryText(primaryPolicy) }}</p>
        </template>

        <div v-if="canEdit && enabledGlobalPolicies.length < 2" class="card-actions">
          <a-button v-if="globalPolicies.length === 0" type="primary" @click="openCreate">정책 등록</a-button>
          <a-button v-else-if="primaryPolicy" @click="openEdit(primaryPolicy)">수정</a-button>
        </div>
      </a-spin>
    </div>

    <a-modal
      v-model:open="modalOpen"
      :title="editingPolicy === null ? '보존 정책 등록' : '보존 정책 수정'"
      :confirm-loading="saving"
      ok-text="저장"
      cancel-text="취소"
      @ok="save"
    >
      <a-form layout="vertical">
        <a-form-item label="보존 일수" required>
          <a-input-number v-model:value="form.retentionPeriodDays" :min="1" style="width: 160px" />
        </a-form-item>
        <a-form-item label="기산점" required>
          <a-select v-model:value="form.baselineType" :options="BASELINE_OPTIONS" style="width: 200px" />
        </a-form-item>
        <a-form-item label="사용 여부">
          <a-switch v-model:checked="form.enabled" />
        </a-form-item>
      </a-form>
    </a-modal>
  </section>
</template>

<style scoped lang="scss">
.policy-card {
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

.summary-text {
  margin: 4px 0;
  font-size: 14px;
  color: var(--app-text-primary);
}

.policy-list {
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.policy-list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  color: var(--app-text-secondary);
}

.card-actions {
  margin-top: 12px;
}
</style>
