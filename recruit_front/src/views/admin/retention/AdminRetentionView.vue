<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'

import { useAuthStore } from '@/stores/authStore'
import RetentionPolicyCard from './RetentionPolicyCard.vue'
import RetentionScheduleCard from './RetentionScheduleCard.vue'
import DataSubjectSearchPanel from './DataSubjectSearchPanel.vue'
import PurgeBatchPanel from './PurgeBatchPanel.vue'

/*
 * 개인정보 파기 관리자 화면. "삭제 요청 파기" 탭의 API(searchDataSubjects·getDataSubject·forcePurge)는
 * 전부 ROLE_PRIVACY_ADMIN 전용이라, 권한이 없으면 탭 자체를 렌더링하지 않는다.
 * 렌더링하면 탭을 열자마자 조회가 나가 공통 인터셉터가 403을 받아 /403으로 튕긴다.
 */

const authStore = useAuthStore()
const canEdit = computed(() => authStore.roles.includes('ROLE_PRIVACY_ADMIN'))

const activeTab = ref(canEdit.value ? 'dataSubjects' : 'purgeBatches')

// 정책 카드가 저장에 성공할 때마다 올려서 스케줄 카드가 예정일을 다시 읽게 한다.
const policyVersion = ref(0)
const onPolicyChanged = (): void => {
  policyVersion.value += 1
}

// 자동 파기 카드의 "파기 이력 보기"에서 넘어온 batch를 "파기 이력" 탭으로 전환해 연다.
// a-tab-pane은 처음 활성화되기 전까지 렌더링하지 않으므로(lazy), activeTab을 바꾼 뒤
// nextTick으로 PurgeBatchPanel이 마운트되길 기다린 다음 openBatch를 호출한다.
const purgeBatchPanelRef = ref<InstanceType<typeof PurgeBatchPanel> | null>(null)
const onOpenBatch = async (id: number): Promise<void> => {
  activeTab.value = 'purgeBatches'
  await nextTick()
  purgeBatchPanelRef.value?.openBatch(id)
}
</script>

<template>
  <div class="retention-view">
    <header class="page-header">
      <h1 class="page-title">개인정보 파기</h1>
      <p class="page-desc">
        보존기간이 지난 지원서를 자동으로 파기하고, 삭제 요청을 받은 지원자를 즉시 파기합니다.
      </p>
    </header>

    <div class="card-grid">
      <RetentionPolicyCard :can-edit="canEdit" @changed="onPolicyChanged" />
      <RetentionScheduleCard :can-edit="canEdit" :policy-version="policyVersion" @open-batch="onOpenBatch" />
    </div>

    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane v-if="canEdit" key="dataSubjects" tab="삭제 요청 파기">
        <DataSubjectSearchPanel />
      </a-tab-pane>
      <a-tab-pane key="purgeBatches" tab="파기 이력">
        <PurgeBatchPanel ref="purgeBatchPanelRef" />
      </a-tab-pane>
    </a-tabs>

    <a-alert
      v-if="!canEdit"
      class="readonly-alert"
      type="info"
      show-icon
      message="조회 권한으로 보고 있습니다"
      description="삭제 요청 파기와 설정 변경은 개인정보 파기 권한(ROLE_PRIVACY_ADMIN)이 필요합니다."
    />
  </div>
</template>

<style scoped lang="scss">
.retention-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  margin-bottom: 0;
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

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
  gap: 14px;
}

.readonly-alert {
  margin-top: 4px;
}
</style>
