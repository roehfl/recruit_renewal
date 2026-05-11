<template>
  <a-card class="recruit-card" :bordered="false">
    <div class="section-header">
      <div>
        <h2 class="section-title">진행중인 채용공고</h2>
        <p class="section-desc">현재 진행중인 채용공고를 확인해보세요.</p>
      </div>
    </div>

    <a-tabs v-model:activeKey="activeTab" class="recruit-tabs" :tabBarGutter="8">
      <a-tab-pane key="PUBLIC" tab="공개채용" />
      <a-tab-pane key="REGULAR" tab="수시채용" />
    </a-tabs>

    <div class="recruit-list">
      <template v-if="filteredRecruitList.length > 0">
        <button
          v-for="recruit in filteredRecruitList"
          :key="recruit.id"
          type="button"
          class="recruit-item"
          @click="goRecruitDetail(recruit.id)"
        >
          <div class="recruit-item-top">
            <a-tag :class="['status-tag', recruit.status]">
              {{ getStatusText(recruit.status) }}
            </a-tag>

            <span class="dday">
              {{ recruit.dday }}
            </span>
          </div>

          <div class="recruit-title">
            {{ recruit.title }}
          </div>

          <div class="recruit-period">{{ recruit.startDate }} ~ {{ recruit.endDate }}</div>
        </button>
      </template>

      <a-empty v-else class="empty-box" description="진행중인 채용공고가 없습니다." />
    </div>

    <button type="button" class="more-button" @click="goRecruitList">
      <span>{{ moreButtonText }}</span>
      <RightOutlined />
    </button>
  </a-card>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { RightOutlined } from '@ant-design/icons-vue'

type RecruitType = 'PUBLIC' | 'REGULAR'
type RecruitStatus = 'OPEN' | 'CLOSED' | 'SOON'

interface RecruitItem {
  id: number
  type: RecruitType
  status: RecruitStatus
  title: string
  startDate: string
  endDate: string
  dday: string
}

const router = useRouter()

const activeTab = ref<RecruitType>('PUBLIC')

const recruitList = ref<RecruitItem[]>([
  {
    id: 1,
    type: 'PUBLIC',
    status: 'OPEN',
    title: '개발계 공고 테스트임',
    startDate: '2026.05.01',
    endDate: '2026.05.20',
    dday: 'D-14',
  },
  {
    id: 2,
    type: 'PUBLIC',
    status: 'OPEN',
    title: '개발계 테스트 공고',
    startDate: '2026.05.03',
    endDate: '2026.05.31',
    dday: 'D-25',
  },
  {
    id: 3,
    type: 'PUBLIC',
    status: 'CLOSED',
    title: '2112년 공채',
    startDate: '2026.04.01',
    endDate: '2026.04.30',
    dday: '마감',
  },
  {
    id: 4,
    type: 'REGULAR',
    status: 'OPEN',
    title: 'IT 직무 수시채용',
    startDate: '2026.05.01',
    endDate: '2026.12.31',
    dday: '상시',
  },
])

const filteredRecruitList = computed<RecruitItem[]>(() => {
  return recruitList.value.filter((item) => item.type === activeTab.value)
})

const moreButtonText = computed<string>(() => {
  return activeTab.value === 'PUBLIC' ? '공개채용 더보기' : '수시채용 더보기'
})

const getStatusText = (status: RecruitStatus): string => {
  const statusMap: Record<RecruitStatus, string> = {
    OPEN: '접수중',
    CLOSED: '접수마감',
    SOON: '마감임박',
  }

  return statusMap[status]
}

const goRecruitDetail = async (recruitId: number): Promise<void> => {
  await router.push(`/recruit/${recruitId}`)
}

const goRecruitList = async (): Promise<void> => {
  await router.push({
    path: '/recruit',
    query: {
      type: activeTab.value,
    },
  })
}
</script>

<style scoped>
.recruit-card {
  width: 100%;
  border: 1px solid var(--app-border-soft);
  border-radius: 8px;
  background: var(--app-bg-surface);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.04);
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
}

.section-title {
  margin: 0;
  color: var(--app-text-primary);
  font-size: 22px;
  font-weight: 700;
  line-height: 1.35;
}

.section-desc {
  margin: 6px 0 0;
  color: var(--app-text-secondary);
  font-size: 14px;
  line-height: 1.45;
}

.recruit-tabs {
  margin-top: 8px;
}

:deep(.ant-tabs-nav) {
  margin-bottom: 8px;
}

:deep(.ant-tabs-tab) {
  padding: 0;
}

:deep(.ant-tabs-tab-btn) {
  min-width: 96px;
  padding: 9px 18px;
  border-radius: 8px;
  color: var(--app-text-primary);
  font-size: 15px;
  text-align: center;
}

:deep(.ant-tabs-tab-active .ant-tabs-tab-btn) {
  background: var(--app-primary-subtle-color);
  color: var(--app-text-primary);
  font-weight: 700;
}

:deep(.ant-tabs-ink-bar) {
  display: none;
}

.recruit-list {
  margin-top: 4px;
}

.recruit-item {
  display: block;
  width: 100%;
  padding: 10px 0;
  border: 0;
  border-bottom: 1px solid var(--app-border-subtle);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.recruit-item:hover .recruit-title {
  color: var(--app-color-warning);
  text-decoration: underline;
}

.recruit-item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.status-tag {
  margin: 0;
  border: 0;
  background: transparent;
  padding: 0;
  font-size: 13px;
  line-height: 1.2;
}

.status-tag.OPEN {
  color: var(--app-color-success);
}

/* .status-tag.SOON {
  color: #d46b08;
} */

.status-tag.CLOSED {
  color: var(--app-text-muted);
}

.dday {
  color: var(--app-color-warning);
  font-size: 13px;
  font-weight: 700;
}

.recruit-title {
  display: block;
  overflow: hidden;
  color: var(--app-text-primary);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.2s ease;
}

.recruit-period {
  margin-top: 3px;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.4;
}

.more-button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: calc(100% + 48px);
  height: 42px;
  margin: 12px -24px -24px;
  padding: 0 22px;
  border: 0;
  border-radius: 0 0 8px 8px;
  background: var(--app-primary-subtle-color);
  color: var(--app-primary-color);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

.more-button:hover {
  background: #ece7d7;
}

.empty-box {
  padding: 28px 0 20px;
}
</style>
