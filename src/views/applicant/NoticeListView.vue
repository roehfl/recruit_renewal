<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
// import { useRouter } from 'vue-router'
import type { TableColumnsType, TablePaginationConfig } from 'ant-design-vue'
import { boardApi } from '@/api/boardApi'
import type { NoticeListItem, NoticeDetail } from '@/types/notice'

const detailModalOpen = ref(false)
const detailLoading = ref(false)
const selectedNotice = ref<NoticeDetail | null>(null)
// const router = useRouter()
const loading = ref(false)
const notices = ref<NoticeListItem[]>([])
const searchForm = reactive({ searchType: 'ALL' as 'ALL' | 'TITLE' | 'CONTENT', keyword: '' })
const pagination = reactive({ current: 1, pageSize: 8, total: 0 })
const columns: TableColumnsType<NoticeListItem> = [
  {
    title: '번호',
    key: 'number',
    width: 90,
    align: 'center',
  },
  {
    title: '제목',
    key: 'title',
  },
  {
    title: '등록일',
    dataIndex: 'createdAt',
    key: 'createdAt',
    width: 150,
    align: 'center',
  },
]
async function loadNotices() {
  loading.value = true
  try {
    const result = await boardApi.fetchNotices({
      page: pagination.current - 1,
      size: pagination.pageSize,
      searchType: searchForm.searchType,
      keyword: searchForm.keyword || undefined,
    })

    notices.value = result.data.data.content
    pagination.total = result.data.data.totalElements
  } finally {
    loading.value = false
  }
}
async function openNoticeDetail(id: number) {
  detailModalOpen.value = true
  detailLoading.value = true
  selectedNotice.value = null

  try {
    selectedNotice.value = (await boardApi.fetchNoticeDetail(id)).data.data
  } finally {
    detailLoading.value = false
  }
}
function handleSearch() {
  pagination.current = 1
  loadNotices()
}
function handleReset() {
  searchForm.searchType = 'ALL'
  searchForm.keyword = ''
  pagination.current = 1
  loadNotices()
}
function handleTableChange(pageInfo: TablePaginationConfig) {
  pagination.current = pageInfo.current ?? 1
  pagination.pageSize = pageInfo.pageSize ?? 10
  loadNotices()
}

function formatDate(value: string) {
  if (!value) return ''
  return value.substring(0, 10)
}
onMounted(() => {
  loadNotices()
})
</script>
<template>
  <section class="board-page">
    <div class="board-container">
      <div class="board-card">
        <div class="board-header">
          <div>
            <h2>공지사항</h2>
            <!-- <p>채용 관련 공지사항을 확인할 수 있습니다.</p> -->
          </div>
          <span class="board-count">
            총 <strong>{{ pagination.total }}</strong
            >건
          </span>
        </div>
        <a-form class="board-search" layout="inline">
          <a-form-item>
            <a-select
              v-model:value="searchForm.searchType"
              class="search-select"
              popupClassName="board-search-select-dropdown"
            >
              <a-select-option value="ALL">전체</a-select-option>
              <a-select-option value="TITLE">제목</a-select-option>
              <a-select-option value="CONTENT">내용</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item class="keyword-item">
            <a-input-search
              v-model:value="searchForm.keyword"
              placeholder="검색어를 입력하세요"
              enter-button="검색"
              @search="handleSearch"
            />
          </a-form-item>
          <a-form-item> <a-button @click="handleReset">초기화</a-button> </a-form-item>
        </a-form>
        <a-table
          :columns="columns"
          :data-source="notices"
          :loading="loading"
          :pagination="{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: pagination.total,
            showSizeChanger: false,
          }"
          row-key="id"
          @change="handleTableChange"
        >
          <template #bodyCell="{ column, record, index }">
            <template v-if="column.key === 'number'">
              <span v-if="record.pinned" class="new-badge">NEW</span>
              <span v-else>
                {{ pagination.total - ((pagination.current - 1) * pagination.pageSize + index) }}
              </span>
            </template>
            <template v-if="column.key === 'title'">
              <button class="title-button" @click="openNoticeDetail(record.id)">
                {{ record.title }}
              </button>
            </template>
            <template v-if="column.key === 'createdAt'">
              {{ formatDate(record.createdAt) }}
            </template>
          </template>
        </a-table>
      </div>
    </div>
  </section>
  <a-modal v-model:open="detailModalOpen" width="900px" :footer="null" :destroy-on-close="true">
    <a-spin :spinning="detailLoading">
      <article v-if="selectedNotice" class="notice-detail">
        <header class="notice-detail-header">
          <h3>{{ selectedNotice.title }}</h3>
          <span>{{ selectedNotice.createdAt.substring(0, 10) }}</span>
        </header>
        <div class="notice-detail-content" v-html="selectedNotice.contentHtml" />
      </article>
    </a-spin>
  </a-modal>
</template>
<style scoped>
.board-page {
  padding: 0;
}
.board-container {
  max-width: 1180px;
  margin: 0 auto;
  padding: 32px 24px 60px;
}
.board-card {
  background: var(--app-bg-surface);
  border: 1px solid var(--app-border-default);
  border-top: 3px solid var(--app-primary-hover-color);
  box-shadow: 0 8px 24px rgba(31, 41, 55, 0.06);
}
.board-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 22px 24px 16px;
  border-bottom: 1px solid var(--app-border-default);
}
.board-header h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.04em;
  color: var(--app-text-primary);
}
.board-header p {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--app-text-secondary);
}
.board-count {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-secondary);
}
.board-count strong {
  color: var(--theme-primary-dark);
}
.board-search {
  display: flex;
  padding: 16px 24px;
  background: #fbfcfa;
  border-bottom: 1px solid var(--app-border-default);
}

.board-search :deep(.ant-select-selector),
.board-search :deep(.ant-input),
.board-search :deep(.ant-btn),
.board-search :deep(.ant-input-search-button) {
  height: 32px !important;
  border-radius: 2px !important;
}

.board-search :deep(.ant-input-search-button),
.board-search :deep(.an-btn-primary) {
  box-shadow: none !important;
}

.search-select {
  width: 130px;
}
.keyword-item {
  flex: 1;
}
.keyword-item :deep(.ant-form-item-control-input-content) {
  width: 100%;
}
.notice-tag {
  color: var(--app-primary-hover-color);
  background: var(--theme-primary-soft);
  /* border-color: var(--theme-border); */
  font-weight: 600;
}
:deep(.notice-tag.ant-tag) {
  margin-inline-end: 0px;
  margin-right: 0px;
}

.title-button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--app-text-primary);
  font-size: 14px;
  font-weight: 400;
  cursor: pointer;
}
.title-button:hover {
  color: var(--theme-primary);
  text-decoration: underline;
}
:deep(.ant-table-thead > tr > th) {
  background: #f9fafb;
  color: #4b5563;
  font-weight: 800;
}
:deep(.ant-table-tbody > tr > td) {
  height: 48px;
}
:deep(.ant-pagination-item-active) {
  border-color: var(--theme-primary);
}
:deep(.ant-pagination-item-active a) {
  color: var(--theme-primary);
}

:global(
  .board-search-select-dropdown
    .ant-select-item-option-selected:not(.ant-select-item-option-disabled)
) {
  background-color: #f4f8f0 !important;
  color: var(--app-color-primary-olive-dark) !important;
  font-weight: 700;
}

:global(
  .board-search-select-dropdown .ant-select-item-option-active:not(.ant-select-item-option-disabled)
) {
  background-color: #f8faf6 !important;
}

.notice-detail-header {
  padding-bottom: 16px;
  border-bottom: 1px solid var(--app-border-default);
}

.notice-detail-header h3 {
  margin: 0;
  color: var(--app-text-primary);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.04em;
}

.notice-detail-header span {
  display: block;
  margin-top: 8px;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.notice-detail-content {
  min-height: 240px;
  padding: 24px 0 8px;
  color: var(--app-text-secondary);
  font-size: 14px;
  line-height: 1.8;
}

.notice-detail-content :deep(p) {
  margin: 0 0 12px;
}

.notice-detail-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
}

.notice-detail-content :deep(td),
.notice-detail-content :deep(th) {
  padding: 8px;
  border: 1px solid #d9d9d9;
}

.new-badge {
  flex-shrink: 0;
  border-radius: 4px;
  background: #e8f6ef;
  padding: 2px 5px;
  color: var(--app-color-success);
  font-size: 10px;
  font-weight: 700;
}
</style>
