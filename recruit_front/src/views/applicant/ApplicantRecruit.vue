<template>
  <section class="recruits-page">
    <div class="page-inner">
      <h1 class="page-title">채용공고</h1>

      <div class="recruit-layout">
        <aside class="filter-panel">
          <div class="filter-head">
            <span class="filter-title">Filters</span>
            <button type="button" class="reset-button" @click="onReset">
              초기화
              <ReloadOutlined />
            </button>
          </div>

          <div class="keyword-box">
            <input v-model="keyword" type="text" placeholder="공고명을 검색해주세요" />
            <SearchOutlined class="keyword-icon" />
          </div>

          <div class="filter-group division-group">
            <button type="button" class="filter-toggle" @click="divisionOpen = !divisionOpen">
              <span class="filter-group-title">구분</span>
              <DownOutlined :class="['chevron', { collapsed: !divisionOpen }]" />
            </button>
            <div v-show="divisionOpen" class="chip-list">
              <button
                v-for="option in divisionOptions"
                :key="option.value"
                type="button"
                :class="['chip', { active: division === option.value }]"
                @click="division = option.value"
              >
                {{ option.label }}
              </button>
            </div>
          </div>

        </aside>

        <div class="recruit-main">
          <p class="recruit-count">
            현재 진행중인 채용은 <strong>{{ filteredJobPostings.length }}</strong>건 입니다.
          </p>

          <a-spin :spinning="loading">
            <ul class="recruit-list">
              <li v-for="posting in filteredJobPostings" :key="posting.id" class="recruit-item">
                <div class="recruit-info">
                  <a class="recruit-title" @click="goDetail(posting.id)">{{ posting.title }}</a>
                  <div class="recruit-meta">
                    <span v-if="postingTypeMap[posting.postingType]" class="type-badge">
                      {{ postingTypeMap[posting.postingType] }}
                    </span>
                    <span :class="['dday-badge', { soon: isDeadlineSoon(posting.receptionEndDateTime) }]">
                      {{ getDDay(posting.receptionEndDateTime) }}
                    </span>
                    <span class="recruit-period">{{ formatDate(posting.receptionStartDateTime, 'YYYY.MM.DD') }} ~ {{ formatDate(posting.receptionEndDateTime, 'YYYY.MM.DD') }}</span>
                  </div>
                </div>
                <button type="button" class="link-button" aria-label="공고 링크 복사" @click="copyPostingUrl(posting)">
                  <LinkOutlined />
                </button>
              </li>
            </ul>

            <div v-if="!loading && filteredJobPostings.length === 0" class="empty-box">
              조건에 맞는 채용공고가 없습니다.
            </div>
          </a-spin>
        </div>
      </div>

      <button type="button" class="top-button" aria-label="맨 위로" @click="scrollToTop">
        <UpOutlined />
        <span>TOP</span>
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { DownOutlined, LinkOutlined, ReloadOutlined, SearchOutlined, UpOutlined } from '@ant-design/icons-vue'
import type { JobPostingListItem } from '@/types/jobPosting'
import { boardApi } from '@/api/boardApi'
import { formatDate, getDDay, isDeadlineSoon } from '@/common/dateUtil'
import { copyText } from '@/common/clipboardUtil'

type DivisionFilter = 'ALL' | 'PUBLIC_RECRUITMENT' | 'EXPERIENCED_RECRUITMENT'

// 공개 목록 API 의 최대 페이지 크기. 화면에 페이지네이션이 없어 한 번에 받는다.
const PAGE_SIZE = 100

const router = useRouter()
const loading = ref(false)
const jobPostings = ref<JobPostingListItem[]>([])
const keyword = ref('')
const division = ref<DivisionFilter>('ALL')
const divisionOpen = ref(true)

// 공고 유형(JobPostingType): 신입 = PUBLIC_RECRUITMENT, 경력 = EXPERIENCED_RECRUITMENT
const postingTypeMap: Record<string, string> = {
  PUBLIC_RECRUITMENT: '신입',
  EXPERIENCED_RECRUITMENT: '경력',
}

const divisionOptions: { label: string; value: DivisionFilter }[] = [
  { label: '전체', value: 'ALL' },
  { label: '신입', value: 'PUBLIC_RECRUITMENT' },
  { label: '경력', value: 'EXPERIENCED_RECRUITMENT' },
]

const filteredJobPostings = computed<JobPostingListItem[]>(() => {
  const trimmedKeyword = keyword.value.trim()
  return jobPostings.value.filter(
    (item) =>
      (!trimmedKeyword || item.title.includes(trimmedKeyword)) &&
      (division.value === 'ALL' || item.postingType === division.value),
  )
})

async function loadJobPostings() {
  loading.value = true
  try {
    const result = await boardApi.fetchJobPostings({
      page: 0,
      size: PAGE_SIZE,
      type: 'ALL',
      status: 'OPEN',
      keyword: '',
    })

    // 접수중인 공고만 노출한다(접수 예정·마감 제외).
    jobPostings.value = result.data.data.content.filter((item) => item.receptionStatus === 'ACCEPTING')
  } finally {
    loading.value = false
  }
}

const onReset = () => {
  keyword.value = ''
  division.value = 'ALL'
}

// 공고 상세 URL 클립보드 복사
const copyPostingUrl = async (posting: JobPostingListItem) => {
  const url = new URL(router.resolve(`/applicant/${posting.id}/detail`).href, window.location.origin).href

  try {
    await copyText(url)
    message.success(`“${posting.title}” 링크가 복사되었습니다.`)
  } catch {
    message.warning('클립보드 복사에 실패했습니다.')
  }
}

const goDetail = async (id: number) => {
  const selectedPosting = jobPostings.value.find((item) => item.id === id)
  await router.push({
    path: `/applicant/${id}/detail`,
    state: {
      data: JSON.stringify(selectedPosting),
    },
  })
}

const scrollToTop = () => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  loadJobPostings()
})
</script>

<style scoped>
.recruits-page {
  width: 100%;
  background: #ffffff;
  color: var(--tap-text);
}

.page-inner {
  max-width: var(--app-frame-width);
  margin: 0 auto;
  padding: 98px var(--app-frame-padding-x) 88px;
}

.page-title {
  margin-bottom: 38px;
  font-size: 38px;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: -0.04em;
  color: var(--tap-text);
}

.recruit-layout {
  display: grid;
  grid-template-columns: minmax(0, 232px) minmax(0, 1fr);
  gap: 56px;
  align-items: start;
}

/* =========================
   필터 영역
========================= */

.filter-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}

.filter-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.filter-title {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.reset-button {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--app-text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.reset-button:hover {
  color: var(--app-color-primary);
}

.keyword-box {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 48px;
  padding: 0 14px 0 16px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: #f5f7fa;
}

.keyword-box:focus-within {
  border-color: var(--app-color-primary);
  background: #ffffff;
}

.keyword-box input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--tap-text);
  font-size: 14px;
}

.keyword-box input::placeholder {
  color: var(--app-text-muted);
}

.keyword-icon {
  color: var(--app-text-secondary);
  font-size: 17px;
}

.filter-group {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.division-group {
  padding-top: 8px;
}

.filter-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--tap-text);
  cursor: pointer;
}

.filter-group-title {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.chevron {
  font-size: 14px;
  transition: transform 0.2s ease;
}

.chevron.collapsed {
  transform: rotate(-90deg);
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  padding: 8px 16px;
  border: 1px solid #dfe5dc;
  border-radius: 999px;
  background: #ffffff;
  color: #5b6b5f;
  font-size: 13px;
  font-weight: 500;
  line-height: 1;
  cursor: pointer;
  transition: all 0.15s ease;
}

.chip.active {
  border-color: var(--app-color-primary);
  background: var(--app-color-primary);
  color: #ffffff;
  font-weight: 600;
}

/* =========================
   목록 영역
========================= */

.recruit-main {
  min-width: 0;
}

.recruit-count {
  margin: 0 0 18px;
  font-size: 15px;
  letter-spacing: -0.01em;
}

.recruit-count strong {
  color: var(--app-color-primary);
  font-weight: 700;
}

.recruit-list {
  margin: 0;
  padding: 0;
  border-top: 1px solid #e5e7eb;
  list-style: none;
}

.recruit-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 26px 4px 24px;
  border-bottom: 1px solid #e5e7eb;
}

.recruit-item:hover {
  background: #f8faf6;
}

.recruit-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.recruit-title {
  color: var(--tap-text);
  font-size: 21px;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: -0.03em;
  text-wrap: pretty;
  cursor: pointer;
}

.recruit-title:hover {
  color: var(--app-color-primary);
}

.recruit-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.type-badge,
.dday-badge {
  padding: 5px 10px;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1;
}

.type-badge {
  background: #f4f8f0;
  color: var(--app-color-primary);
  font-weight: 600;
}

.dday-badge {
  background: #f5f7fa;
  color: var(--app-text-secondary);
  font-weight: 700;
}

.dday-badge.soon {
  background: #fdf3e7;
  color: var(--app-color-warning);
}

.recruit-period {
  margin-left: 4px;
  color: var(--app-text-secondary);
  font-size: 14px;
  letter-spacing: -0.01em;
}

.link-button {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  margin-top: 4px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--app-text-secondary);
  font-size: 19px;
  cursor: pointer;
}

.link-button:hover {
  background: #f4f8f0;
  color: var(--app-color-primary);
}

.empty-box {
  padding: 64px 0;
  color: var(--app-text-muted);
  font-size: 15px;
  text-align: center;
}

.top-button {
  position: fixed;
  right: 40px;
  bottom: 48px;
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  width: 56px;
  height: 56px;
  border: 1px solid #e5e7eb;
  border-radius: 50%;
  background: #ffffff;
  color: var(--tap-text);
  font-size: 14px;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.06);
  cursor: pointer;
}

.top-button span {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.top-button:hover {
  border-color: var(--app-color-primary);
  color: var(--app-color-primary);
}

/* =========================
   반응형
========================= */
@media (max-width: 768px) {
  .page-inner {
    padding: 32px 16px 64px;
  }

  .page-title {
    font-size: 30px;
  }

  .recruit-layout {
    grid-template-columns: 1fr;
    gap: 32px;
  }

  .recruit-title {
    font-size: 18px;
  }

  .top-button {
    right: 16px;
    bottom: 24px;
  }
}
</style>
