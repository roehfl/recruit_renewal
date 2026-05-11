<template>
  <a-card class="notice-card" :bordered="false">
    <div class="notice-header">
      <h2 class="notice-title">공지사항</h2>

      <button type="button" class="notice-more" @click="goNoticeList">
        더보기
        <RightOutlined />
      </button>
    </div>

    <ul class="notice-list">
      <li
        v-for="notice in visibleNotices"
        :key="notice.id"
        class="notice-item"
        @click="goNoticeDetail(notice.url)"
      >
        <div class="notice-main">
          <span v-if="notice.isNew" class="new-badge">NEW</span>
          <span class="notice-text">{{ notice.title }}</span>
        </div>

        <span class="notice-date">{{ notice.createdDate }}</span>
      </li>
    </ul>
  </a-card>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { RightOutlined } from '@ant-design/icons-vue'

interface NoticeItem {
  id: number
  title: string
  createdDate: string
  url: string
  isNew?: boolean
}

interface Props {
  maxCount?: number
}

const props = withDefaults(defineProps<Props>(), {
  maxCount: 4,
})

const router = useRouter()

const notices = ref<NoticeItem[]>([
  {
    id: 1,
    title: '2026년 상반기 공개채용 서류전형 결과 발표 안내',
    createdDate: '2026.05.06',
    url: '/notice/1',
    isNew: true,
  },
  {
    id: 2,
    title: '입사지원서 작성 시 유의사항 안내',
    createdDate: '2026.05.03',
    url: '/notice/2',
  },
  {
    id: 3,
    title: '채용 홈페이지 시스템 점검 안내',
    createdDate: '2026.05.01',
    url: '/notice/3',
  },
  {
    id: 4,
    title: '4번째 row',
    createdDate: '2026.05.05',
    url: '/notice/4',
  },
  {
    id: 5,
    title: '5번째 row',
    createdDate: '2026.03.05',
    url: '/notice/5',
  },
])

const visibleNotices = computed<NoticeItem[]>(() => {
  return notices.value.slice(0, props.maxCount)
})

const goNoticeDetail = async (url: string): Promise<void> => {
  await router.push(url)
}

const goNoticeList = async (): Promise<void> => {
  await router.push('/notice')
}
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
  color: var(--app-text-secondary);
  font-size: 13px;
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
  color: #2f6f55;
  text-decoration: underline;
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
</style>
