<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'

import { adminNoticeApi } from '@/api/admin/adminNoticeApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { AdminNoticeListItem } from '@/types/notice'
import NoticeEditorDrawer from './NoticeEditorDrawer.vue'

const PAGE_SIZE = 10

const notices = ref<AdminNoticeListItem[]>([])
const totalElements = ref(0)
const page = ref(0)
const loading = ref(false)
/* 조회 실패와 "공지 없음"을 구분한다(지원자 공지 화면과 같은 규칙). */
const loadFailed = ref(false)

const selectedIds = ref<number[]>([])

/* 입력 중인 값과 실제 조회 조건을 나눠 둔다. 검색 버튼을 눌러야 조회 조건이 바뀐다. */
const searchForm = reactive({
  searchType: 'ALL' as 'ALL' | 'TITLE' | 'CONTENT',
  keyword: '',
  deleted: 'ALL' as 'ALL' | 'Y' | 'N',
  pinnedOnly: false,
})
const query = reactive({ ...searchForm })

const drawerOpen = ref(false)
const editingNoticeId = ref<number | null>(null)

const columns = [
  { title: '번호', key: 'no', width: 72, align: 'center' as const },
  { title: '제목', key: 'title' },
  { title: '상태', key: 'status', width: 96, align: 'center' as const },
  { title: '등록일', dataIndex: 'createdAt', key: 'createdAt', width: 116 },
  { title: '관리', key: 'actions', width: 150, align: 'center' as const },
]

const pagination = computed(() => ({
  current: page.value + 1,
  pageSize: PAGE_SIZE,
  total: totalElements.value,
  showSizeChanger: false,
}))

const deletedCount = computed(() => notices.value.filter((notice) => notice.deleted).length)

const loadNotices = async (): Promise<void> => {
  loading.value = true
  try {
    const response = await adminNoticeApi.fetchNotices({
      page: page.value,
      size: PAGE_SIZE,
      searchType: query.searchType,
      keyword: query.keyword.trim() || undefined,
      deleted: query.deleted === 'ALL' ? undefined : query.deleted === 'Y',
      pinnedOnly: query.pinnedOnly || undefined,
    })

    notices.value = response.data.data.content
    totalElements.value = response.data.data.totalElements
    loadFailed.value = false
  } catch (error) {
    notices.value = []
    totalElements.value = 0
    loadFailed.value = true
    message.error(getApiErrorMessage(error, '공지사항을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

const search = (): void => {
  Object.assign(query, searchForm)
  page.value = 0
  selectedIds.value = []
  void loadNotices()
}

const resetSearch = (): void => {
  searchForm.searchType = 'ALL'
  searchForm.keyword = ''
  searchForm.deleted = 'ALL'
  searchForm.pinnedOnly = false
  search()
}

const handleTableChange = (nextPagination: { current?: number }): void => {
  page.value = (nextPagination.current ?? 1) - 1
  selectedIds.value = []
  void loadNotices()
}

/* 번호는 최신순 기준 역순으로 매긴다(지원자 공지 목록과 같은 계산). */
const rowNumber = (index: number): number => totalElements.value - (page.value * PAGE_SIZE + index)

/* ---------- 등록·수정 ---------- */

const openCreate = (): void => {
  editingNoticeId.value = null
  drawerOpen.value = true
}

const openEdit = (notice: AdminNoticeListItem): void => {
  editingNoticeId.value = notice.id
  drawerOpen.value = true
}

/* ---------- 단건 상태 변경 ---------- */

const runRowAction = async (action: () => Promise<unknown>, successText: string, failText: string) => {
  try {
    await action()
    message.success(successText)
    await loadNotices()
  } catch (error) {
    message.error(getApiErrorMessage(error, failText))
  }
}

const confirmDelete = (notice: AdminNoticeListItem): void => {
  Modal.confirm({
    title: '공지를 삭제할까요?',
    content: `"${notice.title}" — 지원자 화면에서 사라집니다. 목록에는 삭제됨으로 남고 복구할 수 있습니다.`,
    okText: '삭제',
    okType: 'danger',
    cancelText: '취소',
    onOk: () =>
      runRowAction(
        () => adminNoticeApi.deleteNotice(notice.id),
        '공지를 삭제했습니다.',
        '공지를 삭제하지 못했습니다.',
      ),
  })
}

const restoreNotice = (notice: AdminNoticeListItem): void => {
  void runRowAction(
    () => adminNoticeApi.restoreNotice(notice.id),
    '공지를 복구했습니다.',
    '공지를 복구하지 못했습니다.',
  )
}

const togglePinned = (notice: AdminNoticeListItem): void => {
  void runRowAction(
    () => (notice.pinned ? adminNoticeApi.unpinNotice(notice.id) : adminNoticeApi.pinNotice(notice.id)),
    notice.pinned ? '고정을 해제했습니다.' : '상단에 고정했습니다.',
    '고정 상태를 바꾸지 못했습니다.',
  )
}

/* ---------- 일괄 처리 ---------- */

/* 일괄 API가 없어 건별로 호출한다. 일부만 실패해도 나머지는 반영하고 실패 건수를 알린다. */
const runBulk = async (
  ids: number[],
  action: (id: number) => Promise<unknown>,
  successText: string,
): Promise<void> => {
  loading.value = true
  try {
    const results = await Promise.allSettled(ids.map(action))
    const failed = results.filter((result) => result.status === 'rejected').length

    if (failed === 0) {
      message.success(`${ids.length}건을 ${successText}`)
    } else if (failed === ids.length) {
      message.error(`${failed}건 모두 처리하지 못했습니다.`)
    } else {
      message.warning(`${ids.length - failed}건을 ${successText} ${failed}건은 실패했습니다.`)
    }
  } finally {
    selectedIds.value = []
    loading.value = false
    await loadNotices()
  }
}

const bulkDelete = (): void => {
  const ids = [...selectedIds.value]
  Modal.confirm({
    title: `공지 ${ids.length}건을 삭제할까요?`,
    content: '지원자 화면에서 사라집니다. 목록에는 삭제됨으로 남고 복구할 수 있습니다.',
    okText: '삭제',
    okType: 'danger',
    cancelText: '취소',
    onOk: () => runBulk(ids, (id) => adminNoticeApi.deleteNotice(id), '삭제했습니다.'),
  })
}

const bulkRestore = (): void => {
  void runBulk([...selectedIds.value], (id) => adminNoticeApi.restoreNotice(id), '복구했습니다.')
}

const bulkPin = (): void => {
  void runBulk([...selectedIds.value], (id) => adminNoticeApi.pinNotice(id), '상단에 고정했습니다.')
}

const bulkUnpin = (): void => {
  void runBulk([...selectedIds.value], (id) => adminNoticeApi.unpinNotice(id), '고정 해제했습니다.')
}

onMounted(loadNotices)
</script>

<template>
  <div class="notice-manage">
    <header class="page-header">
      <div>
        <h2 class="page-title">공지사항 관리</h2>
        <p class="page-description">
          지원자 공지사항 화면에 보이는 글을 등록·수정합니다. 삭제하면 지원자 화면에서만 사라지고 이
          목록에는 남습니다.
        </p>
      </div>
      <a-button type="primary" @click="openCreate">공지 등록</a-button>
    </header>

    <div class="search-bar">
      <a-select v-model:value="searchForm.searchType" class="search-type">
        <a-select-option value="ALL">제목+내용</a-select-option>
        <a-select-option value="TITLE">제목</a-select-option>
        <a-select-option value="CONTENT">내용</a-select-option>
      </a-select>

      <a-input
        v-model:value="searchForm.keyword"
        class="search-keyword"
        placeholder="검색어를 입력하세요"
        allow-clear
        @press-enter="search"
      />

      <a-select v-model:value="searchForm.deleted" class="search-status">
        <a-select-option value="ALL">상태 전체</a-select-option>
        <a-select-option value="N">정상</a-select-option>
        <a-select-option value="Y">삭제됨</a-select-option>
      </a-select>

      <a-checkbox v-model:checked="searchForm.pinnedOnly">고정만 보기</a-checkbox>

      <div class="search-actions">
        <a-button @click="resetSearch">초기화</a-button>
        <a-button type="primary" @click="search">검색</a-button>
      </div>
    </div>

    <div class="list-summary">
      <span v-if="!loadFailed">
        전체 <b>{{ totalElements }}</b
        >건
        <span v-if="deletedCount > 0" class="summary-sub">· 이 페이지 삭제됨 {{ deletedCount }}건</span>
      </span>
    </div>

    <div v-if="selectedIds.length > 0" class="bulk-bar">
      <b>{{ selectedIds.length }}건 선택됨</b>
      <a-button size="small" @click="bulkPin">상단 고정</a-button>
      <a-button size="small" @click="bulkUnpin">고정 해제</a-button>
      <a-button size="small" @click="bulkRestore">복구</a-button>
      <a-button size="small" danger @click="bulkDelete">삭제</a-button>
      <a-button size="small" type="link" @click="selectedIds = []">선택 해제</a-button>
    </div>

    <a-table
      :columns="columns"
      :data-source="notices"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      :row-selection="{
        selectedRowKeys: selectedIds,
        onChange: (keys: (string | number)[]) => (selectedIds = keys.map(Number)),
      }"
      @change="handleTableChange"
    >
      <template #emptyText>
        <div class="empty-area">
          <template v-if="loadFailed">
            <p>공지사항을 불러오지 못했습니다.</p>
            <a-button size="small" @click="loadNotices">다시 시도</a-button>
          </template>
          <p v-else>조건에 맞는 공지사항이 없습니다.</p>
        </div>
      </template>

      <template #bodyCell="{ column, record, index }">
        <template v-if="column.key === 'no'">
          <a-tag v-if="record.pinned" color="orange">고정</a-tag>
          <span v-else class="row-number">{{ rowNumber(index) }}</span>
        </template>

        <template v-else-if="column.key === 'title'">
          <a class="notice-title" :class="{ deleted: record.deleted }" @click="openEdit(record)">
            {{ record.title }}
          </a>
        </template>

        <template v-else-if="column.key === 'status'">
          <a-tag :color="record.deleted ? 'red' : 'green'">
            {{ record.deleted ? '삭제됨' : '정상' }}
          </a-tag>
        </template>

        <template v-else-if="column.key === 'actions'">
          <span class="row-actions">
            <a-button size="small" @click="togglePinned(record)">
              {{ record.pinned ? '고정 해제' : '고정' }}
            </a-button>
            <a-button v-if="record.deleted" size="small" @click="restoreNotice(record)">복구</a-button>
            <a-button v-else size="small" danger @click="confirmDelete(record)">삭제</a-button>
          </span>
        </template>
      </template>
    </a-table>

    <NoticeEditorDrawer
      v-model:open="drawerOpen"
      :notice-id="editingNoticeId"
      @saved="loadNotices"
    />
  </div>
</template>

<style scoped>
.notice-manage {
  padding: 24px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 18px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.page-description {
  margin: 4px 0 0;
  color: #8c8c8c;
  font-size: 13px;
}

.search-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}

.search-type {
  width: 120px;
}

.search-keyword {
  width: 280px;
}

.search-status {
  width: 130px;
}

.search-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.list-summary {
  min-height: 22px;
  margin: 14px 2px 6px;
  font-size: 13px;
  color: #595959;
}

.summary-sub {
  color: #8c8c8c;
}

.bulk-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 8px;
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 6px;
}

.bulk-bar b {
  font-weight: 500;
}

.row-number {
  color: #8c8c8c;
}

.notice-title {
  color: inherit;
}

.notice-title:hover {
  color: #1677ff;
}

.notice-title.deleted {
  color: #bfbfbf;
  text-decoration: line-through;
}

.row-actions {
  display: inline-flex;
  gap: 4px;
}

.empty-area {
  padding: 32px 0;
  color: #8c8c8c;
}

.empty-area p {
  margin: 0 0 12px;
}
</style>
