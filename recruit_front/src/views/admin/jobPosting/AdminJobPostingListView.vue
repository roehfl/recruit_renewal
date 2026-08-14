<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { adminJobPostingApi } from '@/api/adminJobPostingApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { AdminJobPostingListItem } from '@/types/jobPosting'

const router = useRouter()
const loading = ref(false)
const postings = ref<AdminJobPostingListItem[]>([])
const page = ref(0)
const pageSize = 10
const totalElements = ref(0)

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
const postingTypeLabelMap: Record<string, string> = {
  PUBLIC_RECRUITMENT: '공개채용',
  EXPERIENCED_RECRUITMENT: '경력채용',
  INTERN_RECRUITMENT: '인턴채용',
  ROLLING_RECRUITMENT: '수시채용',
}

const columns = [
  { title: '제목', dataIndex: 'title', key: 'title' },
  { title: '유형', key: 'postingType', width: 110 },
  { title: '상태', key: 'status', width: 90 },
  { title: '접수 기간', key: 'reception', width: 300 },
  { title: '모집분야', dataIndex: 'positionCount', key: 'positionCount', width: 90 },
]

const pagination = computed(() => ({
  current: page.value + 1,
  pageSize,
  total: totalElements.value,
  showSizeChanger: false,
}))

const formatDateTime = (value: string) => value.replace('T', ' ').slice(0, 16)

const loadPostings = async () => {
  loading.value = true
  try {
    const response = await adminJobPostingApi.getJobPostings(page.value, pageSize)
    postings.value = response.data.data.content
    totalElements.value = response.data.data.totalElements
  } catch (error) {
    message.error(getApiErrorMessage(error, '공고 목록을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

const handleTableChange = (nextPagination: { current?: number }) => {
  page.value = (nextPagination.current ?? 1) - 1
  void loadPostings()
}

const goToDetail = (record: AdminJobPostingListItem) => {
  void router.push({ name: 'AdminJobPostingDetail', params: { id: record.id } })
}

const goToCreate = () => {
  void router.push({ name: 'AdminJobPostingCreate' })
}

onMounted(loadPostings)
</script>

<template>
  <div class="job-posting-list">
    <header class="page-header">
      <div>
        <h2 class="page-title">공고 목록</h2>
        <p class="page-description">채용 공고를 조회하고 상세에서 검수·발행합니다.</p>
      </div>
      <a-button type="primary" @click="goToCreate">공고 등록</a-button>
    </header>

    <a-table
      :columns="columns"
      :data-source="postings"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      :custom-row="(record: AdminJobPostingListItem) => ({ onClick: () => goToDetail(record) })"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'postingType'">
          {{ postingTypeLabelMap[record.postingType] ?? record.postingType }}
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="statusColorMap[record.status]">{{ statusLabelMap[record.status] ?? record.status }}</a-tag>
        </template>
        <template v-else-if="column.key === 'reception'">
          {{ formatDateTime(record.receptionStartDateTime) }} ~ {{ formatDateTime(record.receptionEndDateTime) }}
        </template>
      </template>
    </a-table>
  </div>
</template>

<style scoped>
.job-posting-list {
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
  color: #888;
}
:deep(.ant-table-row) {
  cursor: pointer;
}
</style>
