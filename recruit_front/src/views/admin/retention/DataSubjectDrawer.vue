<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'

import { retentionApi } from '@/api/admin/retentionApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import type { DataSubjectApplication, DataSubjectDetail } from '@/types/admin/retention'
import { eligibilityTag } from './retentionLabel'
import ForcedPurgeConfirmModal from './ForcedPurgeConfirmModal.vue'

/*
 * 파기 대상자 상세 드로어. props.applicantId가 바뀌거나 열릴 때마다 상세를 다시 읽는다.
 * 사용자가 행을 빠르게 바꿔 클릭하면 이전 요청의 응답이 나중에 도착할 수 있는데, 그걸 그대로 보여주면
 * 엉뚱한 사람의 정보 위에서 파기 버튼을 누르게 될 수 있다. 그래서 요청마다 증가하는 번호(request)를 두고
 * 응답이 왔을 때 그 번호가 최신 요청과 다르면(=늦게 도착한 응답이면) 버린다.
 */

const props = defineProps<{ open: boolean; applicantId: number | null }>()
const emit = defineEmits<{ 'update:open': [boolean]; purged: [] }>()

const detail = ref<DataSubjectDetail | null>(null)
const loading = ref(false)

let request = 0

const load = async (applicantId: number): Promise<void> => {
  const current = ++request
  loading.value = true
  try {
    const response = await retentionApi.getDataSubject(applicantId)
    if (current !== request) return // 늦게 도착한 응답: 그 사이 다른 사람을 열었거나 드로어가 닫혔다.
    detail.value = response.data.data
  } catch (error) {
    if (current !== request) return
    message.error(getApiErrorMessage(error, '지원자 상세를 불러오지 못했습니다.'))
  } finally {
    if (current === request) {
      loading.value = false
    }
  }
}

watch(
  [() => props.open, () => props.applicantId],
  ([isOpen, applicantId]) => {
    request += 1 // 이전 요청은 이 시점에서 전부 무효화된다.
    if (isOpen && applicantId !== null) {
      detail.value = null
      void load(applicantId)
    } else {
      loading.value = false
    }
  },
  { immediate: true },
)

const columns = [
  { title: '공고명', dataIndex: 'jobPostingTitle', key: 'jobPostingTitle' },
  { title: '지원일', key: 'submittedAt', width: 150 },
  { title: '상태', dataIndex: 'status', key: 'status', width: 110 },
  { title: '판정', key: 'eligibility', width: 110 },
]

const asApplication = (record: unknown): DataSubjectApplication => record as DataSubjectApplication

const hasBlockingHold = computed(() => detail.value?.hasActiveHold === true)

const allApplicationsPurged = computed(() => {
  const applications = detail.value?.applications ?? []
  return applications.length > 0 && applications.every((app) => app.reasonCode === 'ALREADY_PURGED')
})

const purgeDisabled = computed(() => hasBlockingHold.value || allApplicationsPurged.value)

const confirmModalOpen = ref(false)

const openConfirm = (): void => {
  if (detail.value === null || purgeDisabled.value) return
  confirmModalOpen.value = true
}

const onPurged = (): void => {
  emit('update:open', false)
  emit('purged')
}
</script>

<template>
  <a-drawer
    :open="open"
    title="파기 대상자 상세"
    width="720"
    @update:open="(next: boolean) => emit('update:open', next)"
  >
    <a-spin :spinning="loading">
      <template v-if="detail">
        <a-descriptions bordered size="small" :column="1" class="subject-info">
          <a-descriptions-item label="이름">{{ detail.name ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="이메일">{{ detail.email ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="휴대폰">{{ detail.phoneNumber ?? '-' }}</a-descriptions-item>
        </a-descriptions>

        <h3 class="section-title">
          지원서 <span class="section-hint">{{ detail.applications.length }}건</span>
        </h3>
        <a-table
          :columns="columns"
          :data-source="detail.applications"
          row-key="applicationId"
          size="small"
          :pagination="false"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'submittedAt'">
              {{ formatDate(asApplication(record).submittedAt, 'YYYY-MM-DD HH:mm') || '-' }}
            </template>
            <template v-else-if="column.key === 'eligibility'">
              <a-tag :color="eligibilityTag(asApplication(record).eligible, asApplication(record).reasonCode).color">
                {{ eligibilityTag(asApplication(record).eligible, asApplication(record).reasonCode).label }}
              </a-tag>
            </template>
          </template>
        </a-table>

        <a-alert
          v-if="hasBlockingHold"
          class="purge-notice"
          type="error"
          show-icon
          message="파기 보류가 걸려 있어 파기할 수 없습니다. 보류를 먼저 해제해야 합니다."
        />
        <p v-else-if="allApplicationsPurged" class="purge-notice muted">이미 파기된 지원자입니다.</p>

        <a-button class="purge-button" danger :disabled="purgeDisabled" @click="openConfirm">
          삭제 요청 파기
        </a-button>
      </template>
      <a-empty v-else-if="!loading" description="지원자 상세를 불러오지 못했습니다." />
    </a-spin>

    <ForcedPurgeConfirmModal v-model:open="confirmModalOpen" :detail="detail" @purged="onPurged" />
  </a-drawer>
</template>

<style scoped lang="scss">
.subject-info {
  margin-bottom: 16px;
}

.section-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.section-hint {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 400;
  color: var(--app-text-secondary);
}

.purge-notice {
  margin-top: 14px;
}

.muted {
  font-size: 13px;
  color: var(--app-text-secondary);
}

.purge-button {
  margin-top: 14px;
}
</style>
