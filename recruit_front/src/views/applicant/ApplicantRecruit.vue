<template>
  <section class="recruits-page">
    <div class="page-inner">

      <h1 class="page-title">채용공고</h1>

      <div aria-label="채용절차">
        <a-form layout="inline" class="search-form">
          <a-form-item label="키워드">
            <a-input
              v-model:value="searchType.keyword"
              placeholder="공고명"
              @pressEnter="onSearchClick"
          /></a-form-item>
          <a-form-item label="공고유형">
            <a-select v-model:value="searchType.status" style="width: 140px" :options="statusTypes" />
          </a-form-item>

          <a-form-item>
            <a-space>
              <a-button type="primary" @click="onSearchClick">조회</a-button>
              <a-button @click="onSearchReset">초기화</a-button>
            </a-space>
          </a-form-item>
        </a-form>
      </div>
      <div class="jobPostingTable">
        <a-table :columns="columns" :data-source="jobPostings" :pagination="{ pageSize: 8 }" />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, h, ref } from 'vue'
import type { TableColumnsType } from 'ant-design-vue'
import type { JobPostingListItem } from '@/types/jobPosting'
import { boardApi } from '@/api/boardApi'
import { useRouter } from 'vue-router'
import { formatDate } from '@/common/dateUtil'

const loading = ref(false)
const originJobPostings = ref<JobPostingListItem[]>([])
const jobPostings = ref<JobPostingListItem[]>([])
const searchForm = reactive({
  type: 'ALL' as 'ALL' | 'TITLE' | 'CONTENT',
  status: 'PUBLISHED',
  keyword: '',
})
const pagination = reactive({ current: 1, pageSize: 10, total: 0 })
const router = useRouter()

const postingTypeMap: Record<string, string> = {
  PUBLIC_RECRUITMENT: '공개',
  ROLLING_RECRUITMENT: '수시',
}
const recruitStatusTypeMap: Record<string, string> = {
  ACCEPTING: '진행중',
  CLOSED: '마감',
  UPCOMING: '예정',
}

const searchType = ref({ keyword: '', status: 'ALL' as string | undefined })

const statusTypes = [
  { label: '전체', value: 'ALL' },
  { label: '공개', value: 'PUBLIC_RECRUITMENT' },
  { label: '수시', value: 'ROLLING_RECRUITMENT' },
]
const onSearchReset = () => {
  searchType.value = { keyword: '', status: 'ALL' }
  jobPostings.value = originJobPostings.value
}

const onSearchClick = () => {
  jobPostings.value = originJobPostings.value.filter((item) => {
    const keywordMatched  =  !searchType.value.keyword || item.title.includes(searchType.value.keyword)
    const statusMatched   =  (searchType.value.status === 'ALL')? item : item.postingType === searchType.value.status

    return keywordMatched && statusMatched
  })
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
    jobPostings.value = result.data.data.content
    pagination.total = result.data.data.totalElements
  } finally {
    loading.value = false
  }
}

const columns: TableColumnsType<JobPostingListItem> = [
  {
    title: '공고명',
    dataIndex: 'title',
    key: 'title',
    customRender: ({ text, record }) => h('a', { onClick: () => goDetail(record.id) }, text),
  },
  {
    title: '공고유형',
    dataIndex: 'postingType',
    key: 'postingType',
    customRender: ({ text }: { text: string }) => postingTypeMap[text] || '-',
    width: 120,
  },
  {
    title: '상태',
    dataIndex: 'receptionStatus',
    key: 'status',
    width: 120,
    customRender: ({ text }) =>
      h('span', { class: `status-tag ${text}` }, recruitStatusTypeMap[text]),
  },
  {
    title: '모집시작일',
    dataIndex: 'receptionStartDateTime',
    key: 'startDate',
    customRender: ({ text }: { text: string }) => formatDate(text, 'YYYY-MM-DD HH:mm'),
    width: 160,
  },
  {
    title: '모집종료일',
    dataIndex: 'receptionEndDateTime',
    key: 'endDate',
    customRender: ({ text }: { text: string }) => formatDate(text, 'YYYY-MM-DD HH:mm'),
    width: 160,
  },
]

const goDetail = async (id: number) => {
  const selectedPosting = jobPostings.value.find((item) => item.id === id)
  await router.push({
    path: `/applicant/${id}/detail`,
    state: {
      data: JSON.stringify(selectedPosting),
    },
  })
}

onMounted(() => {
  loadJobPostings()
})
</script>

<style scoped>
.recruitProcedure-page {
  width: 100%;
  background: #ffffff;
  color: var(--tap-text);
}

.page-inner {
  max-width: var(--app-frame-width);
  margin: 0 auto;
  padding: 98px 20px 88px;
  /* padding: 42px 20px 88px; */
}

.page-title {
  margin-bottom: 38px;
  font-size: 38px;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: -0.04em;
  color: var(--tap-text);
}

.sample-page {
  max-width: var(--app-frame-width);
  margin: 0 auto;
  padding: 32px 24px 60px;
}

.search-form {
  margin-bottom: 16px;
}

:deep(.ant-descriptions-item-label) {
  font-size: 16px;
  font-weight: 500;
}

:deep(.ant-descriptions-item-content) {
  font-size: 16px;
}

.jobPosting-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.jobPosting-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-top: 1px solid #edf0f2;
  cursor: pointer;
}

.jobPosting-item:hover .jobPosting-text {
  color: var(--app-color-primary-emerald);
  /* text-decoration: double; */
}

.jobPosting-main {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 6px;
}

.jobPosting-text {
  overflow: hidden;
  color: var(--app-text-primary);
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.jobPosting-date {
  flex-shrink: 0;
  color: var(--app-text-muted);
  font-size: 12px;
}

:deep(.ant-table-cell) .status-tag {
  margin: 0;
  border: 0;
  background: transparent;
  padding: 0;
  font-weight: 500;
  font-size: 13px;
  line-height: 1.2;
}
:deep(.ant-table-cell) .status-tag.ACCEPTING {
  color: var(--app-color-success);
}

:deep(.ant-table-cell) .status-tag.UPCOMING {
  color: #d46b08;
}

:deep(.ant-table-cell) .status-tag.CLOSED {
  color: var(--app-text-muted);
}

/* =========================
   그리드 영역
========================= */

.jobPostingTable {
  border: 1px solid var(--app-border-subtle);
  border-radius: 10px;

  background-color: #ffffff;
  box-shadow: 0 5px 20px var(--tap-panel-shadow);
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

  .benefit-tabs {
    margin-top: 30px;
  }
}
</style>
