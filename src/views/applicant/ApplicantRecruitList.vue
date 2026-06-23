<template>
  <a-card class="recruit-card" :bordered="false">
    <div class="section-header">
      <div>
        <h2 class="section-title">진행중인 채용공고</h2>
        <p class="section-desc">현재 진행중인 채용공고를 확인해보세요.</p>
      </div>
    </div>

    <a-tabs v-model:activeKey="activeTab" class="recruit-tabs" :tabBarGutter="8">
      <a-tab-pane key="PUBLIC_RECRUITMENT" tab="공개채용" />
      <a-tab-pane key="ROLLING_RECRUITMENT" tab="수시채용" />
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
            <a-tag :class="['status-tag', recruit.receptionStatus]">
                {{ recruitStatusTypeMap[recruit.receptionStatus] }}
            </a-tag>

            <span class="dday">
              {{ getDDay(recruit.receptionEndDateTime) }}
            </span>
          </div>

          <div class="recruit-title">
            {{ recruit.title }}
          </div>

          <div class="recruit-period">{{ formatDate(recruit.receptionStartDateTime, 'YYYY.MM.DD') }} ~ {{ formatDate(recruit.receptionEndDateTime, 'YYYY.MM.DD') }}</div>

        </button>
      </template>

      <a-empty v-else class="empty-box" description="진행중인 채용공고가 없습니다." />
    </div>

    <button type="button" class="more-button" @click="goRecruitList">
      <span> 바로가기 </span>
      <RightOutlined class="go-icon" />
    </button>
  </a-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { RightOutlined } from '@ant-design/icons-vue'
import { boardApi } from '@/api/boardApi'
import type { JobPostingListItem } from '@/types/jobPosting'
import { formatDate } from '@/common/dateUtil'

type RecruitType = 'PUBLIC_RECRUITMENT' | 'ROLLING_RECRUITMENT'

const router = useRouter()
const loading = ref(false)
const pagination = reactive({ current: 1, pageSize: 10, total: 0 })
const activeTab = ref<RecruitType>('PUBLIC_RECRUITMENT')

const originJobPostings = ref<JobPostingListItem[]>([])
const jobPostings = ref<JobPostingListItem[]>([])
const searchForm = reactive({
  type: 'ALL' as 'ALL' | 'TITLE' | 'CONTENT',
  status: 'PUBLISHED',
  keyword: '',
})
const searchType = ref({ keyword: '', status: 'ALL' as string | undefined })

// 공고 상태
const recruitStatusTypeMap: Record<string, string> = {
  ACCEPTING: '진행중',
  CLOSED: '마감',
  UPCOMING: '예정',
}

async function loadJobPostings() {
  loading.value = true
  try {
    const result = await boardApi.fetchJobPostings({
      page: pagination.current - 1,
      size: pagination.pageSize,
      type: searchForm.type,
      status: 'OPEN',
      keyword: searchType.value.keyword,
    })

    originJobPostings.value = result.data.data.content
    // 진행중인 공고
    jobPostings.value = originJobPostings.value.filter((item) => item.receptionStatus === 'ACCEPTING' )
    pagination.total = result.data.data.totalElements

  } finally {
    loading.value = false
  }
}

const getDDay = (endDateTime: string) => {
  const now = new Date();
  const endDate = new Date(endDateTime)

  const diffTime = endDate.getTime() - now.getTime();
  if (diffTime < 0) return '마감'

  const diffDay = Math.floor( diffTime / ( 1000 * 60 * 60 * 24 ) );
  if (diffDay === 0) return 'D-DAY'
  
  return `D-${diffDay}`;
}

const filteredRecruitList = computed<JobPostingListItem[]>(() => {
  return jobPostings.value.filter((item) => item.postingType === activeTab.value)
})

const goRecruitDetail = async (recruitId: number): Promise<void> => {
  await router.push(`/applicant/${recruitId}/detail`)
}

const goRecruitList = async (): Promise<void> => {
  await router.push('/applicant/recruits')
}

onMounted(() => {
  loadJobPostings()
})
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
  margin-bottom: 0px;
}

:deep(.ant-tabs-tab) {
  padding: 0;
}

:deep(.ant-tabs-tab-btn) {
  min-width: 96px;
  padding: 9px 18px;
  border-radius: 8px;
  color: var(--tap-muted);
  font-size: 15px;
  text-align: center;
}

:deep(.ant-tabs-tab-btn:hover) {
  color: var(--app-color-primary);
  font-weight: 500;
}

/* :deep(.ant-tabs-tab-active .ant-tabs-tab-btn) {
  background: var(--app-bg-btn-hover);
  color: var(--app-color-primary);
  font-weight: 600;
} */

:deep(.ant-tabs-tab-active .ant-tabs-tab-btn) {
  /* background: color-mix(in srgb, var(--app-color-primary) 5%, transparent); */
  color: var(--app-color-primary);
  font-weight: 600;
}

:deep(.ant-tabs-ink-bar) {
  /* display: none; */
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
  color: var(--app-color-primary-emerald);
  /* text-decoration: underline; */
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
  font-weight: 700;
  font-size: 13px;
  line-height: 1.2;
}

.status-tag.ACCEPTING {
  color: var(--app-color-success);
}

.status-tag.UPCOMING {
  color: var(--app-color-warning);
}

.status-tag.CLOSED {
  color: var(--app-text-muted);
}

.dday {
  color: var(--app-color-warning);
  font-size: 13px;
  font-weight: 600;
}

.recruit-title {
  display: block;
  overflow: hidden;
  color: var(--app-text-primary);
  font-size: 15px;
  font-weight: 500;
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
  gap: 6px;
  width: calc(100% + 48px);
  height: 50px;
  margin: 0px 0px -24px -24px;
  padding: 12px 24px;
  border: 0;
  border-radius: 0 0 8px 8px;
  background: var(--app-primary-subtle-color);
  color: var(--app-primary-color);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.more-button:hover {
  background: var(--app-bg-btn-hover);
}

.empty-box {
  padding: 28px 0 20px;
}

.go-icon {
  font-size: 12px;
}
</style>
