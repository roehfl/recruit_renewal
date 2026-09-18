<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { adminJobPostingApi } from '@/api/adminJobPostingApi'
import { getApiErrorMessage } from '@/api/apiError'
import ApplicationFormConfigTab from './ApplicationFormConfigTab.vue'
import ApplicationFormLayoutTab from './ApplicationFormLayoutTab.vue'
import ApplicationFormQuestionTab from './ApplicationFormQuestionTab.vue'
import type { AdminJobPostingDetail } from '@/types/jobPosting'

type TabKey = 'config' | 'layout' | 'question'

/* 자기소개서 질문 탭은 후속 slice 에서 이 목록에 추가한다. */
const TABS: { key: TabKey; label: string }[] = [
  { key: 'config', label: '지원서 양식' },
  { key: 'layout', label: '폼 구성' },
  { key: 'question', label: '질문 설정' }
]

const route = useRoute()
const router = useRouter()
const jobPostingId = computed(() => Number(route.params.jobPostingId))

const loading = ref(false)
const detail = ref<AdminJobPostingDetail | null>(null)
const activeTab = ref<TabKey>('config')
/* 탭 전환 시 리마운트해 최신 상태를 다시 읽게 한다(양식 변경이 폼 구성의 활성 섹션을 바꾼다). */
const tabGeneration = ref(0)

const statusLabelMap: Record<string, string> = {
  DRAFT: '작성 중',
  PUBLISHED: '게시 중',
  CLOSED: '마감',
}
const statusColorMap: Record<string, string> = {
  DRAFT: 'default',
  PUBLISHED: 'green',
  CLOSED: 'red',
}

/*
 * 편집 가능 조건은 백엔드 ApplicationFormEditWindow 와 동일하다.
 * 작성 중(DRAFT) 공고는 지원서가 0건이라 접수일이 지나도 수정할 수 있고,
 * 게시된 공고는 접수 시작 전까지만 수정할 수 있다.
 * 클라이언트 시계로 계산하지 않고 서버가 내려준 status/receptionStatus 를 그대로 쓴다.
 */
const editable = computed(() => {
  if (detail.value === null || detail.value.status === 'CLOSED') {
    return false
  }
  return detail.value.status === 'DRAFT' || detail.value.receptionStatus === 'UPCOMING'
})

/* 질문 추가·삭제·순서 변경은 백엔드가 작성 중(DRAFT) 공고에서만 허용한다. 게시 후에는 문구 수정만 된다. */
const structureEditable = computed(() => detail.value?.status === 'DRAFT')

/* 현재 탭(한 번에 하나만 렌더된다)의 미저장 변경. 탭 전환·목록 이동 시 탭이 파기되므로 먼저 확인한다. */
const tabRef = ref<{ isDirty?: () => boolean } | null>(null)
const hasUnsavedChanges = () => tabRef.value?.isDirty?.() === true

const confirmDiscardIfDirty = (): Promise<boolean> => {
  if (!hasUnsavedChanges()) {
    return Promise.resolve(true)
  }
  return new Promise((resolve) => {
    Modal.confirm({
      title: '저장하지 않은 변경이 있습니다',
      content: '이동하면 저장하지 않은 변경이 사라집니다. 계속할까요?',
      okText: '변경 버리고 계속',
      cancelText: '취소',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    })
  })
}

onBeforeRouteLeave(() => confirmDiscardIfDirty())

const handleBeforeUnload = (event: BeforeUnloadEvent) => {
  if (hasUnsavedChanges()) {
    event.preventDefault()
    event.returnValue = ''
  }
}

onMounted(() => window.addEventListener('beforeunload', handleBeforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', handleBeforeUnload))

const formatDateTime = (value: string | null) => (value ? value.replace('T', ' ').slice(0, 16) : '-')

const loadDetail = async () => {
  loading.value = true
  try {
    const response = await adminJobPostingApi.getJobPosting(jobPostingId.value)
    detail.value = response.data.data
  } catch (error) {
    message.error(getApiErrorMessage(error, '공고 정보를 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

const changeTab = async (key: TabKey) => {
  if (!(await confirmDiscardIfDirty())) {
    return
  }
  activeTab.value = key
  tabGeneration.value += 1
}

const onConfigSaved = async () => {
  // 섹션 사용 여부가 바뀌면 폼 구성의 활성 섹션 집합도 달라진다.
  await loadDetail()
  tabGeneration.value += 1
}

onMounted(loadDetail)
</script>

<template>
  <div class="application-form-detail">
    <a-spin :spinning="loading">
      <header class="page-header">
        <div>
          <h2 class="page-title">
            지원서 설정
            <a-tag v-if="detail" :color="statusColorMap[detail.status]">
              {{ statusLabelMap[detail.status] ?? detail.status }}
            </a-tag>
          </h2>
          <p class="page-description">
            <template v-if="detail">
              {{ detail.title }} · 접수 {{ formatDateTime(detail.receptionStartDateTime) }} ~
              {{ formatDateTime(detail.receptionEndDateTime) }}
            </template>
          </p>
        </div>
        <div class="header-actions">
          <a-button @click="router.push({ name: 'AdminApplicationFormList' })">목록</a-button>
          <a-button @click="router.push({ name: 'AdminJobPostingDetail', params: { id: jobPostingId } })">
            공고 상세
          </a-button>
        </div>
      </header>

      <nav class="site-tabs" role="tablist" aria-label="지원서 설정 구분">
        <button
          v-for="tab in TABS"
          :key="tab.key"
          type="button"
          role="tab"
          class="site-tab"
          :class="{ active: activeTab === tab.key }"
          :aria-selected="activeTab === tab.key"
          @click="changeTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </nav>

      <ApplicationFormConfigTab
        v-if="activeTab === 'config' && detail"
        ref="tabRef"
        :key="`config-${tabGeneration}`"
        :job-posting-id="jobPostingId"
        :config="detail.applicationFormConfig"
        :editable="editable"
        @saved="onConfigSaved"
      />
      <ApplicationFormLayoutTab
        v-if="activeTab === 'layout'"
        ref="tabRef"
        :key="`layout-${tabGeneration}`"
        :job-posting-id="jobPostingId"
      />
      <ApplicationFormQuestionTab
        v-if="activeTab === 'question'"
        :editable="editable"
        :structure-editable="structureEditable"
        :key="`layout-${tabGeneration}`"
        :job-posting-id="jobPostingId"
      />
    </a-spin>
  </div>
</template>

<style scoped lang="scss">
.application-form-detail {
  padding: 24px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.page-title {
  margin: 0 0 4px;
}
.page-description {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 13px;
}
.header-actions {
  display: flex;
  gap: 8px;
}

/* 탭은 메뉴 관리 화면(MenuManageView)의 밑줄형 탭과 같은 규약을 쓴다. */
.site-tabs {
  flex: none;
  display: flex;
  gap: 24px;
  border-bottom: 1px solid var(--app-border-default);
}
.site-tab {
  padding: 0 2px 9px;
  margin-bottom: -1px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--app-text-secondary);
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition:
    color 0.15s ease,
    border-color 0.15s ease;

  &:hover {
    color: var(--app-color-primary);
  }

  &.active {
    color: var(--app-color-primary);
    font-weight: 700;
    border-bottom-color: var(--app-color-primary);
  }
}
</style>
