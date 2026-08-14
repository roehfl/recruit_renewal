<template>
  <a-card class="notice-card" :bordered="false">
    <div class="notice-header">
      <h2 class="notice-title">공지사항</h2>

      <button type="button" class="notice-more" @click="goNoticeList">
        더보기
        <RightOutlined class="go-icon" />
      </button>
    </div>

    <ul class="notice-list">
      <li v-for="notice in notices" :key="notice.id" class="notice-item" @click="goNoticeList()">
        <div class="notice-main">
          <span v-if="notice.pinned" class="new-badge">NEW</span>
          <span class="notice-text">{{ notice.title }}</span>
        </div>

        <span class="notice-date">{{ notice.createdAt }}</span>
      </li>
    </ul>
  </a-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { RightOutlined } from '@ant-design/icons-vue'
import type { NoticeListItem } from '@/types/notice'
import { boardApi } from '@/api/boardApi'

const loading = ref(false)
const notices = ref<NoticeListItem[]>([])
const searchForm = reactive({ searchType: 'ALL' as 'ALL' | 'TITLE' | 'CONTENT', keyword: '' })
const pagination = reactive({ current: 1, pageSize: 4, total: 0 })

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

const router = useRouter()

const goNoticeList = async (): Promise<void> => {
  await router.push('/applicant/noticeList')
}

onMounted(() => {
  loadNotices()
})
</script>

<style scoped>
.notice-card {
  border: 1px solid var(--app-border-soft);
  border-radius: 8px;
  background: var(--app-bg-surface);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.035);
}

:deep(.ant-card-body) {
  padding: 20px 22px;
}

.notice-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.notice-title {
  margin: 0;
  color: var(--app-text-primary);
  font-size: 18px;
  font-weight: 700;
}

.notice-more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 0;
  background: transparent;
  /* color: var(--app-text-secondary); */
  color: var(--app-primary-color);
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
}

.notice-more:hover {
  color: #2f6f55;
}

.notice-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.notice-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-top: 1px solid #edf0f2;
  cursor: pointer;
}

.notice-item:hover .notice-text {
  color: var(--app-color-primary-emerald);
  /* text-decoration: double; */
}

.notice-main {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 6px;
}

.notice-text {
  overflow: hidden;
  color: var(--app-text-primary);
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-date {
  flex-shrink: 0;
  color: var(--app-text-muted);
  font-size: 12px;
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

.go-icon {
  font-size: 12px;
}
</style>
