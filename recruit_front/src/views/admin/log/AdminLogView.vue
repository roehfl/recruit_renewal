<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { useAuthStore } from '@/stores/authStore'
import ApplicantEventPanel from './ApplicantEventPanel.vue'
import AuditLogPanel from './AuditLogPanel.vue'

/*
 * 로그 조회 화면. 탭 2개(지원자 이벤트 / 감사 로그).
 *
 * 두 조회 API 는 ROLE_RECRUIT_ADMIN·ROLE_PRIVACY_ADMIN 만 허용한다. ROLE_ADMIN 만 가진 계정은
 * 라우트 가드(ADMIN_ROLES)는 통과하지만 조회에서 403 을 받고, 공통 인터셉터가 /403 으로 보내 버린다.
 * 그래서 권한이 없으면 탭 자체를 렌더링하지 않고 안내만 띄운다(AdminRetentionView 와 같은 방식).
 *
 * IP·User-Agent 원문은 ROLE_PRIVACY_ADMIN 만 본다. 그 외에는 서버가 "***" 로 바꿔 보내므로
 * 화면은 마스킹 판정을 하지 않고 안내 배너만 띄운다.
 */

const authStore = useAuthStore()
const canQuery = computed(
  () => authStore.roles.includes('ROLE_RECRUIT_ADMIN') || authStore.roles.includes('ROLE_PRIVACY_ADMIN'),
)
const canSeeSensitive = computed(() => authStore.roles.includes('ROLE_PRIVACY_ADMIN'))

const activeTab = ref<'client' | 'audit'>('client')

const applicantPanelRef = ref<InstanceType<typeof ApplicantEventPanel> | null>(null)
const auditPanelRef = ref<InstanceType<typeof AuditLogPanel> | null>(null)

/*
 * a-tab-pane 은 처음 활성화되기 전까지 렌더링하지 않으므로(lazy), 탭을 바꾼 뒤 nextTick 으로
 * 패널이 마운트되기를 기다린 다음 노출 메서드를 부른다.
 */
const openApplicantEvents = async (applicationId: number): Promise<void> => {
  activeTab.value = 'client'
  await nextTick()
  void applicantPanelRef.value?.applyApplicationId(applicationId)
}

const openAudit = async (applicationId: number): Promise<void> => {
  activeTab.value = 'audit'
  await nextTick()
  void auditPanelRef.value?.applyApplicationId(applicationId)
}

/*
 * 딥링크: /admin/logs?tab=audit&applicationId=1042
 * 다른 화면(지원현황 등)에서 넘어오는 입구다. 진입 시 한 번 읽기만 하고, 이후 필터 변경을
 * URL 에 되쓰지는 않는다(되쓰려면 필터 전체를 쿼리로 직렬화해야 해서 얻는 것보다 비싸다).
 */
const route = useRoute()

onMounted(() => {
  if (!canQuery.value) return
  if (route.query.tab === 'audit') {
    activeTab.value = 'audit'
  }
  const rawApplicationId = route.query.applicationId
  const applicationId = Number(Array.isArray(rawApplicationId) ? rawApplicationId[0] : rawApplicationId)
  if (!Number.isInteger(applicationId) || applicationId <= 0) return
  if (activeTab.value === 'audit') {
    void openAudit(applicationId)
  } else {
    void openApplicantEvents(applicationId)
  }
})
</script>

<template>
  <div class="log-view">
    <header class="page-header">
      <h1 class="page-title">로그 조회</h1>
      <p class="page-desc">
        <b>지원자 이벤트</b>는 지원자 브라우저에서 발생한 오류를, <b>감사 로그</b>는 관리자·시스템의 서버 행위
        증적을 조회합니다. 두 로그 모두 지원자 이름·연락처를 저장하지 않습니다.
      </p>
    </header>

    <a-alert
      v-if="!canQuery"
      type="info"
      show-icon
      message="로그 조회 권한이 없습니다"
      description="로그 조회에는 채용 관리자 권한(ROLE_RECRUIT_ADMIN) 또는 개인정보 관리자 권한(ROLE_PRIVACY_ADMIN)이 필요합니다."
    />

    <template v-else>
      <a-alert
        v-if="!canSeeSensitive"
        type="warning"
        show-icon
        class="mask-notice"
        message="IP 주소·브라우저 정보(User-Agent)와 지원자 이벤트의 사용자 가명키·사용자 유형은 ***로 가려져 있습니다"
        description="원문 확인에는 개인정보 관리자 권한(ROLE_PRIVACY_ADMIN)이 필요합니다."
      />

      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="client" tab="지원자 이벤트">
          <ApplicantEventPanel ref="applicantPanelRef" @open-audit="openAudit" />
        </a-tab-pane>
        <a-tab-pane key="audit" tab="감사 로그">
          <AuditLogPanel ref="auditPanelRef" @open-applicant-events="openApplicantEvents" />
        </a-tab-pane>
      </a-tabs>
    </template>
  </div>
</template>

<style scoped lang="scss">
.log-view {
  display: flex;
  flex-direction: column;
  gap: 12px;
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
  max-width: 900px;
}

.mask-notice {
  margin-bottom: 0;
}
</style>
