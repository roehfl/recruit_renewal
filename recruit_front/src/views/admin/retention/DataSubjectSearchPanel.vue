<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'

import { retentionApi } from '@/api/admin/retentionApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import type { DataSubjectQuery, DataSubjectSummary } from '@/types/admin/retention'
import DataSubjectDrawer from './DataSubjectDrawer.vue'

/*
 * "삭제 요청 파기" 탭. 이름·휴대폰·이메일로 지원자를 검색해 상세 드로어를 연다.
 * 드로어에서 강제 파기가 끝나면(purged) 목록이 낡은 상태로 남지 않도록 같은 조건으로 다시 검색한다.
 * 연락처는 관리자 화면 규칙에 따라 마스킹하지 않고 원문을 그대로 보여준다.
 */

interface SearchForm {
  name: string
  phoneNumber: string
  email: string
}

const form = ref<SearchForm>({ name: '', phoneNumber: '', email: '' })
const results = ref<DataSubjectSummary[]>([])
const loading = ref(false)
const searched = ref(false)
/** 드로어의 파기 완료 후 같은 조건으로 재조회하기 위해 마지막으로 성공한 검색 조건을 들고 있는다. */
let lastQuery: DataSubjectQuery | null = null

const columns = [
  { title: '이름', dataIndex: 'name', key: 'name' },
  { title: '이메일', dataIndex: 'email', key: 'email' },
  { title: '휴대폰', dataIndex: 'phoneNumber', key: 'phoneNumber' },
  { title: '지원 건수', key: 'applicationCount', width: 100 },
  { title: '최근 지원일', key: 'lastAppliedAt', width: 150 },
  { title: '상태', key: 'status', width: 110 },
]

interface StatusTag {
  label: string
  color: string
}

const statusTag = (row: DataSubjectSummary): StatusTag | null => {
  if (row.hasActiveHold) return { label: '보류 중', color: 'red' }
  if (row.applicationCount > 0 && row.purgedApplicationCount === row.applicationCount) {
    return { label: '파기 완료', color: 'default' }
  }
  if (row.purgedApplicationCount > 0) return { label: '일부 파기', color: 'orange' }
  return null
}

const asRow = (record: unknown): DataSubjectSummary => record as DataSubjectSummary

const buildQuery = (): DataSubjectQuery | null => {
  const query: DataSubjectQuery = {
    name: form.value.name.trim() || undefined,
    phoneNumber: form.value.phoneNumber.trim() || undefined,
    email: form.value.email.trim() || undefined,
  }
  if (!query.name && !query.phoneNumber && !query.email) return null
  return query
}

const runSearch = async (query: DataSubjectQuery): Promise<void> => {
  loading.value = true
  try {
    const response = await retentionApi.searchDataSubjects(query)
    results.value = response.data.data
    lastQuery = query
    searched.value = true
  } catch (error) {
    message.error(getApiErrorMessage(error, '지원자를 조회하지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

const search = (): void => {
  const query = buildQuery()
  if (query === null) {
    message.warning('검색 조건을 1개 이상 입력하세요.')
    return
  }
  void runSearch(query)
}

const drawerOpen = ref(false)
const selectedApplicantId = ref<number | null>(null)

const openDetail = (row: DataSubjectSummary): void => {
  selectedApplicantId.value = row.applicantId
  drawerOpen.value = true
}

const onRow = (record: unknown) => ({
  onClick: () => openDetail(asRow(record)),
})

const onPurged = (): void => {
  if (lastQuery !== null) {
    void runSearch(lastQuery)
  }
}
</script>

<template>
  <div class="search-panel">
    <div class="search-bar">
      <a-input
        v-model:value="form.name"
        placeholder="이름"
        allow-clear
        class="search-input"
        @press-enter="search"
      />
      <a-input
        v-model:value="form.phoneNumber"
        placeholder="휴대폰"
        allow-clear
        class="search-input"
        @press-enter="search"
      />
      <a-input
        v-model:value="form.email"
        placeholder="이메일"
        allow-clear
        class="search-input"
        @press-enter="search"
      />
      <a-button type="primary" :loading="loading" @click="search">조회</a-button>
    </div>

    <a-table
      class="result-table"
      :columns="columns"
      :data-source="results"
      :loading="loading"
      :pagination="false"
      row-key="applicantId"
      :custom-row="onRow"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">{{ asRow(record).name ?? '-' }}</template>
        <template v-else-if="column.key === 'email'">{{ asRow(record).email ?? '-' }}</template>
        <template v-else-if="column.key === 'phoneNumber'">{{ asRow(record).phoneNumber ?? '-' }}</template>
        <template v-else-if="column.key === 'applicationCount'">{{ asRow(record).applicationCount }}건</template>
        <template v-else-if="column.key === 'lastAppliedAt'">
          {{ formatDate(asRow(record).lastAppliedAt, 'YYYY-MM-DD HH:mm') || '-' }}
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag v-if="statusTag(asRow(record))" :color="statusTag(asRow(record))?.color">
            {{ statusTag(asRow(record))?.label }}
          </a-tag>
        </template>
      </template>
      <template #emptyText>
        <a-empty :description="searched ? '조회 결과가 없습니다.' : '검색 조건을 입력하고 조회하세요.'" />
      </template>
    </a-table>

    <p v-if="results.length === 50" class="limit-hint">상위 50건만 표시합니다. 조건을 좁혀 주세요.</p>

    <DataSubjectDrawer v-model:open="drawerOpen" :applicant-id="selectedApplicantId" @purged="onPurged" />
  </div>
</template>

<style scoped lang="scss">
.search-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.search-bar {
  display: flex;
  gap: 8px;
}

.search-input {
  width: 220px;
}

.result-table {
  :deep(.ant-table-tbody > tr) {
    cursor: pointer;
  }
}

.limit-hint {
  margin: 0;
  font-size: 12px;
  color: var(--app-text-muted);
}
</style>
