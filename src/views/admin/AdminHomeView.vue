<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'

import DashboardCard from './dashboard/DashboardCard.vue'
import StageFunnelCard from './dashboard/StageFunnelCard.vue'
import StageResultCompositionCard from './dashboard/StageResultCompositionCard.vue'
import PositionFunnelCard from './dashboard/PositionFunnelCard.vue'
import TopGroupCard from './dashboard/TopGroupCard.vue'
import DailyTrendCard from './dashboard/DailyTrendCard.vue'

import { statisticsApi } from '@/api/statisticsApi'
import { adminJobPostingApi } from '@/api/adminJobPostingApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { AdminJobPostingListItem } from '@/types/jobPosting'
import type { ApplicationDaily, DimensionFunnel, FunnelDimension, FunnelResult } from '@/types/statistics'

/*
 * 관리자 대시보드(시안 2a "퍼널 중심"). 화면 스코프는 공고 1건이다 —
 * 상단에서 고른 공고의 데이터만 모든 위젯에 흐른다. 여러 공고를 한 화면에 나열하지 않는다.
 */

const DASHBOARD_DIMENSIONS: FunnelDimension[] = ['POSITION', 'SCHOOL', 'CERTIFICATE']
const TOP_N = 5

const jobPostings = ref<AdminJobPostingListItem[]>([])
const selectedJobPostingId = ref<number | null>(null)
const funnel = ref<FunnelResult | null>(null)
const daily = ref<ApplicationDaily | null>(null)

const initializing = ref(true)
const refreshing = ref(false)
const loadFailed = ref(false)

const jobPostingOptions = computed(() => {
  return jobPostings.value.map((posting) => ({
    value: posting.id,
    label: posting.title,
  }))
})

const selectedJobPosting = computed<AdminJobPostingListItem | null>(() => {
  return jobPostings.value.find((posting) => posting.id === selectedJobPostingId.value) ?? null
})

const groupsOf = (dimension: FunnelDimension): DimensionFunnel[] => {
  return funnel.value?.dimensionGroups.find((group) => group.dimension === dimension)?.groups ?? []
}

const positionGroups = computed<DimensionFunnel[]>(() => groupsOf('POSITION'))
const schoolGroups = computed<DimensionFunnel[]>(() => groupsOf('SCHOOL'))
const certificateGroups = computed<DimensionFunnel[]>(() => groupsOf('CERTIFICATE'))

/*
 * 축마다 따로 호출하면 서버가 같은 코호트를 축 개수만큼 다시 읽는다. 필요한 축을 한 번에 요청한다.
 * 추이는 시계열이라 집계 경로가 달라 별도 호출이며, 두 호출은 서로를 기다릴 이유가 없어 병렬로 보낸다.
 */
const loadStatistics = async (jobPostingId: number): Promise<void> => {
  const [funnelResponse, dailyResponse] = await Promise.all([
    statisticsApi.getFunnel(jobPostingId, DASHBOARD_DIMENSIONS, TOP_N),
    statisticsApi.getApplicationsDaily(jobPostingId),
  ])

  funnel.value = funnelResponse.data.data
  daily.value = dailyResponse.data.data
}

const changeJobPosting = async (jobPostingId: number): Promise<void> => {
  selectedJobPostingId.value = jobPostingId
  refreshing.value = true
  loadFailed.value = false

  try {
    await loadStatistics(jobPostingId)
  } catch (error) {
    loadFailed.value = true
    message.error(getApiErrorMessage(error, '통계를 불러오지 못했습니다.'))
  } finally {
    refreshing.value = false
  }
}

const refresh = async (): Promise<void> => {
  if (selectedJobPostingId.value === null || refreshing.value) {
    return
  }

  await changeJobPosting(selectedJobPostingId.value)
}

/* 기본 선택은 접수 중인 첫 공고다. 없으면 목록의 첫 공고로 떨어진다. */
const pickDefaultJobPosting = (postings: AdminJobPostingListItem[]): AdminJobPostingListItem | null => {
  return postings.find((posting) => posting.accepting) ?? postings[0] ?? null
}

onMounted(async () => {
  try {
    const response = await adminJobPostingApi.getJobPostings()
    jobPostings.value = response.data.data.content

    const defaultPosting = pickDefaultJobPosting(jobPostings.value)

    if (defaultPosting) {
      await changeJobPosting(defaultPosting.id)
    }
  } catch (error) {
    loadFailed.value = true
    message.error(getApiErrorMessage(error, '공고 목록을 불러오지 못했습니다.'))
  } finally {
    initializing.value = false
  }
})
</script>

<template>
  <div class="dashboard">
    <header class="page-header">
      <h2 class="page-title">전형 진행 현황</h2>
      <p class="page-description">공고 하나를 골라 그 전형의 진행 상황을 봅니다.</p>
    </header>

    <!-- 필터는 차트 카드 안이 아니라 전체 위 한 줄에 둔다. 모든 카드가 같은 공고로 다시 그려진다. -->
    <div class="filter-bar">
      <a-select
        :value="selectedJobPostingId"
        class="posting-select"
        placeholder="공고를 선택하세요"
        :options="jobPostingOptions"
        :disabled="initializing || jobPostings.length === 0"
        show-search
        option-filter-prop="label"
        @change="(value: number) => changeJobPosting(value)"
      />

      <a-button :loading="refreshing" :disabled="selectedJobPostingId === null" @click="refresh">
        <template #icon>
          <ReloadOutlined />
        </template>
        새로고침
      </a-button>

      <span v-if="selectedJobPosting" class="filter-note">
        접수 {{ selectedJobPosting.receptionStartDateTime.slice(0, 10) }} ~
        {{ selectedJobPosting.receptionEndDateTime.slice(0, 10) }}
      </span>

      <span class="filter-hint">퍼널 집계는 공고 단위입니다 — 전사 통합값은 별도 집계가 필요합니다.</span>
    </div>

    <p v-if="initializing" class="state-message">불러오는 중입니다.</p>

    <p v-else-if="jobPostings.length === 0" class="state-message">
      등록된 공고가 없습니다. 공고를 먼저 등록하세요.
    </p>

    <p v-else-if="loadFailed && !funnel" class="state-message">
      통계를 불러오지 못했습니다. 새로고침을 눌러 다시 시도하세요.
    </p>

    <!-- 재조회 중에는 이전 렌더를 흐리게 유지한다. 스켈레톤으로 갈아끼우면 레이아웃이 튄다. -->
    <div v-else-if="funnel && daily" class="card-grid" :class="{ 'is-refreshing': refreshing }">
      <div class="grid-pair">
        <StageFunnelCard :population="funnel.population" :stages="funnel.stages" />
        <StageResultCompositionCard :stages="funnel.stages" />
      </div>

      <PositionFunnelCard :groups="positionGroups" />

      <div class="grid-pair">
        <TopGroupCard title="학교별 지원자" :subtitle="`상위 ${TOP_N}개 · 나머지는 기타`" :groups="schoolGroups" />
        <TopGroupCard title="자격별 보유" :subtitle="`상위 ${TOP_N}개 · 나머지는 기타`" :groups="certificateGroups" />
      </div>

      <DailyTrendCard :daily="daily" />
    </div>

    <DashboardCard v-else title="전형 진행 현황">
      <p class="state-message">표시할 통계가 없습니다.</p>
    </DashboardCard>
  </div>
</template>

<style scoped lang="scss">
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.page-header {
  flex: none;
}

.page-title {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 800;
  color: var(--app-text-primary);
  letter-spacing: -0.02em;
}

.page-description {
  margin: 0;
  font-size: 12.5px;
  color: var(--app-text-secondary);
}

.filter-bar {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.posting-select {
  min-width: 280px;
}

.filter-note {
  font-size: 12px;
  color: var(--app-text-secondary);
  font-variant-numeric: tabular-nums;
}

.filter-hint {
  margin-left: auto;
  font-size: 11px;
  color: var(--app-text-muted);
}

.card-grid {
  display: flex;
  flex-direction: column;
  gap: 14px;
  transition: opacity 0.15s ease;

  &.is-refreshing {
    opacity: 0.6;
  }
}

.grid-pair {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
  gap: 14px;
}

.state-message {
  margin: 0;
  padding: 48px 20px;
  font-size: 13px;
  color: var(--app-text-muted);
  text-align: center;
}
</style>
