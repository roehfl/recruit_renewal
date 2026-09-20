<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined } from '@ant-design/icons-vue'

import { adminApplicationApi } from '@/api/admin/adminApplicationApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { AdminApplicationSummaryResponse } from '@/types/admin/application'

/*
 * 지원자 찾기. 두 로그 테이블에는 이름·휴대폰이 없어서 지원번호로 바꿔 주는 단계가 필요하다.
 * 응답(AdminApplicationSummaryResponse)에는 휴대폰이 없다(검색 조건으로만 쓴다) — 동명이인은 생년월일로 가린다.
 * 파기된 지원자는 이름 스냅샷이 익명화돼 검색되지 않으므로 "지원번호 직접 입력"을 항상 열어 둔다.
 * 관리자 화면이므로 이름·생년월일은 마스킹하지 않는다.
 */

const emit = defineEmits<{ (e: 'select', applicationId: number, applicantName: string | null): void }>()

const STATUS_LABEL: Record<AdminApplicationSummaryResponse['status'], string> = {
  DRAFT: '작성중',
  SUBMITTED: '제출완료',
  WITHDRAWN: '철회',
}

const name = ref('')
const phoneNumber = ref('')
const directApplicationId = ref('')
const rows = ref<AdminApplicationSummaryResponse[]>([])
const loading = ref(false)
const searched = ref(false)

const columns = [
  { title: '지원번호', key: 'applicationId', width: 100 },
  { title: '이름', key: 'applicantNameSnapshot', width: 110 },
  { title: '생년월일 (나이)', key: 'birthDate', width: 150 },
  { title: '공고 / 모집분야', key: 'posting', ellipsis: true },
  { title: '상태', key: 'status', width: 100 },
]

const asRow = (record: unknown): AdminApplicationSummaryResponse => record as AdminApplicationSummaryResponse

const search = async (): Promise<void> => {
  const trimmedName = name.value.trim()
  const trimmedPhone = phoneNumber.value.trim()
  if (!trimmedName && !trimmedPhone) {
    message.warning('이름 또는 휴대폰 번호를 입력하세요.')
    return
  }
  loading.value = true
  try {
    const response = await adminApplicationApi.searchApplicants(
      trimmedName || undefined,
      trimmedPhone || undefined,
    )
    rows.value = response.data.data.content
    searched.value = true
  } catch (error) {
    message.error(getApiErrorMessage(error, '지원자를 조회하지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

const select = (row: AdminApplicationSummaryResponse): void => {
  emit('select', row.applicationId, row.applicantNameSnapshot)
}

const onRow = (record: unknown) => ({ onClick: () => select(asRow(record)) })

const applyDirectId = (): void => {
  const trimmed = directApplicationId.value.trim()
  if (!/^[0-9]+$/.test(trimmed)) {
    message.warning('지원번호는 숫자로 입력하세요.')
    return
  }
  emit('select', Number(trimmed), null)
}
</script>

<template>
  <div class="finder">
    <div class="search-bar">
      <a-input v-model:value="name" placeholder="지원자 이름" allow-clear class="search-input" @press-enter="search" />
      <a-input v-model:value="phoneNumber" placeholder="휴대폰 번호(숫자 일부)" allow-clear class="search-input" @press-enter="search" />
      <a-button type="primary" :loading="loading" @click="search">
        <template #icon><SearchOutlined /></template>찾기
      </a-button>
    </div>

    <a-table
      v-if="searched"
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="false"
      row-key="applicationId"
      size="small"
      :custom-row="onRow"
      class="result-table"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'applicationId'">{{ asRow(record).applicationId }}</template>
        <template v-else-if="column.key === 'applicantNameSnapshot'">{{ asRow(record).applicantNameSnapshot }}</template>
        <template v-else-if="column.key === 'birthDate'">
          {{ asRow(record).birthDate ?? '-' }}
          <span v-if="asRow(record).age != null" class="sub">({{ asRow(record).age }}세)</span>
        </template>
        <template v-else-if="column.key === 'posting'">
          {{ asRow(record).jobPostingTitleSnapshot }} / {{ asRow(record).jobPositionNameSnapshot }}
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag>{{ STATUS_LABEL[asRow(record).status] }}</a-tag>
        </template>
      </template>
      <template #emptyText>
        <a-empty description="일치하는 지원자가 없습니다. 파기된 지원자는 검색되지 않습니다." />
      </template>
    </a-table>

    <a-divider class="divider">또는</a-divider>

    <div class="direct-bar">
      <a-input
        v-model:value="directApplicationId"
        placeholder="지원번호 직접 입력 (예: 1042)"
        allow-clear
        class="search-input"
        @press-enter="applyDirectId"
      />
      <a-button @click="applyDirectId">적용</a-button>
      <span class="hint">파기된 지원자는 이름·휴대폰으로 찾을 수 없습니다. 지원번호를 알고 있다면 여기에 입력하세요.</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.finder {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.search-bar,
.direct-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.search-input {
  width: 220px;
}

.divider {
  margin: 4px 0;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.hint {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.sub {
  margin-left: 4px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.result-table {
  :deep(.ant-table-tbody > tr) {
    cursor: pointer;
  }
}
</style>
