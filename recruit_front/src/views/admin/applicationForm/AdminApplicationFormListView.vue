<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { adminApplicationFormApi } from '@/api/admin/adminApplicationFormApi'
import { getApiErrorMessage } from '@/api/apiError'
import type {
  AdminApplicationFormSummary,
  AdminApplicationFormSummarySearchRequest,
  ApplicationFormConfigState,
} from '@/types/admin/application'

const router = useRouter()

const loading = ref(false)
const rows = ref<AdminApplicationFormSummary[]>([])
const page = ref(0)
const pageSize = 20
const totalElements = ref(0)

/*
 * 기본값은 마감 공고 제외다. 마감된 공고는 설정을 바꿀 수 없어 목록에 남겨둘 이유가 적다.
 * status 에 '마감 제외'를 표현할 값이 없으므로 화면 상태로 따로 들고, 요청에서는 status 를 비워 보낸다.
 */
const excludeClosed = ref(true)
const statusFilter = ref<AdminApplicationFormSummarySearchRequest['status']>()
const receptionStatusFilter = ref<AdminApplicationFormSummarySearchRequest['receptionStatus']>()
const configStateFilter = ref<ApplicationFormConfigState>()
const editableOnly = ref(false)
const keyword = ref('')

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
const receptionStatusLabelMap: Record<string, string> = {
  UPCOMING: '접수 예정',
  ACCEPTING: '접수 중',
  CLOSED: '접수 종료',
}
const postingTypeLabelMap: Record<string, string> = {
  PUBLIC_RECRUITMENT: '공개채용',
  EXPERIENCED_RECRUITMENT: '경력채용',
  INTERN_RECRUITMENT: '인턴채용',
  ROLLING_RECRUITMENT: '수시채용',
}

const configStateMeta: Record<ApplicationFormConfigState, { label: string; color: string; hint: string }> = {
  RELAYOUT_REQUIRED: {
    label: '재배치 필요',
    color: 'red',
    hint: '활성 섹션이 저장된 레이아웃과 어긋납니다. 지원자가 지원서를 열 수 없습니다.',
  },
  MISSING: { label: '설정 없음', color: 'red', hint: '지원서 항목 설정이 없어 공고를 게시할 수 없습니다.' },
  DEFAULT: { label: '기본값 사용', color: 'orange', hint: '레이아웃을 저장한 적이 없어 기본 구성으로 동작합니다.' },
  OK: { label: '정상', color: 'green', hint: '저장된 레이아웃과 활성 섹션이 일치합니다.' },
}

/** a-table 슬롯의 record 는 any 라 색인 접근 대신 타입이 붙은 함수로 감싼다. */
const configStateOf = (state: ApplicationFormConfigState) => configStateMeta[state] ?? configStateMeta.OK

const statusOptions = [
  { value: 'DRAFT', label: '작성 중' },
  { value: 'PUBLISHED', label: '게시 중' },
  { value: 'CLOSED', label: '마감' },
]
const receptionStatusOptions = [
  { value: 'UPCOMING', label: '접수 예정' },
  { value: 'ACCEPTING', label: '접수 중' },
  { value: 'CLOSED', label: '접수 종료' },
]
const configStateOptions = [
  { value: 'RELAYOUT_REQUIRED', label: '재배치 필요' },
  { value: 'MISSING', label: '설정 없음' },
  { value: 'DEFAULT', label: '기본값 사용' },
  { value: 'OK', label: '정상' },
]

const columns = [
  { title: '공고명', key: 'title' },
  { title: '상태', key: 'status', width: 160 },
  { title: '접수 기간', key: 'reception', width: 250 },
  { title: '섹션', key: 'sections', width: 120 },
  { title: '자기소개서 질문', key: 'questions', width: 130 },
  { title: '폼 레이아웃', key: 'layout', width: 130 },
  { title: '설정 상태', key: 'configState', width: 130 },
  { title: '최종 수정', key: 'updatedAt', width: 140 },
]

const pagination = computed(() => ({
  current: page.value + 1,
  pageSize,
  total: totalElements.value,
  showSizeChanger: false,
}))

const attentionCount = computed(
  () => rows.value.filter((row) => row.configState === 'RELAYOUT_REQUIRED' || row.configState === 'MISSING').length,
)

const formatDateTime = (value: string) => value.replace('T', ' ').slice(0, 16)

const buildSearchRequest = (): AdminApplicationFormSummarySearchRequest => {
  const request: AdminApplicationFormSummarySearchRequest = {}
  if (statusFilter.value) {
    request.status = statusFilter.value
  }
  if (receptionStatusFilter.value) {
    request.receptionStatus = receptionStatusFilter.value
  }
  if (configStateFilter.value) {
    request.configState = configStateFilter.value
  }
  if (editableOnly.value) {
    request.editableOnly = true
  }
  if (keyword.value.trim().length > 0) {
    request.keyword = keyword.value.trim()
  }
  return request
}

const load = async () => {
  loading.value = true
  try {
    const response = await adminApplicationFormApi.getSummaries(buildSearchRequest(), page.value, pageSize)
    const data = response.data.data
    // '마감 제외'는 서버 조건이 아니라 화면 기본값이라 응답을 걸러서 쓴다.
    rows.value = excludeClosed.value ? data.content.filter((row) => row.status !== 'CLOSED') : data.content
    totalElements.value = data.totalElements
  } catch (error) {
    message.error(getApiErrorMessage(error, '지원서 설정 현황을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

const search = () => {
  page.value = 0
  void load()
}

const resetFilters = () => {
  excludeClosed.value = true
  statusFilter.value = undefined
  receptionStatusFilter.value = undefined
  configStateFilter.value = undefined
  editableOnly.value = false
  keyword.value = ''
  search()
}

const handleTableChange = (nextPagination: { current?: number }) => {
  page.value = (nextPagination.current ?? 1) - 1
  void load()
}

const goToDetail = (row: AdminApplicationFormSummary) => {
  void router.push({ name: 'AdminApplicationFormDetail', params: { jobPostingId: row.jobPostingId } })
}

onMounted(load)
</script>

<template>
  <div class="application-form-list">
    <header class="page-header">
      <div>
        <h2 class="page-title">지원서 설정 현황</h2>
        <p class="page-description">공고별 지원서 양식과 폼 구성 상태를 확인하고 설정 화면으로 이동합니다.</p>
      </div>
    </header>

    <a-alert
      v-if="attentionCount > 0"
      type="error"
      show-icon
      class="attention"
      message="바로 확인이 필요한 공고가 있습니다."
      :description="`재배치가 필요하거나 설정이 없는 공고 ${attentionCount}건. 지원자가 지원서를 열지 못할 수 있습니다.`"
    />

    <div class="filters">
      <a-input v-model:value="keyword" placeholder="공고명 검색" allow-clear style="width: 200px" @press-enter="search" />
      <a-select
        v-model:value="statusFilter"
        :options="statusOptions"
        placeholder="공고 상태"
        allow-clear
        style="width: 130px"
      />
      <a-select
        v-model:value="receptionStatusFilter"
        :options="receptionStatusOptions"
        placeholder="접수 상태"
        allow-clear
        style="width: 130px"
      />
      <a-select
        v-model:value="configStateFilter"
        :options="configStateOptions"
        placeholder="설정 상태"
        allow-clear
        style="width: 140px"
      />
      <a-checkbox v-model:checked="excludeClosed">마감 제외</a-checkbox>
      <a-checkbox v-model:checked="editableOnly">지금 수정 가능한 것만</a-checkbox>
      <a-button type="primary" @click="search">검색</a-button>
      <a-button @click="resetFilters">초기화</a-button>
    </div>

    <a-table
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="pagination"
      row-key="jobPostingId"
      size="middle"
      :custom-row="(record: AdminApplicationFormSummary) => ({ onClick: () => goToDetail(record) })"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'title'">
          <span class="row-title">{{ record.title }}</span>
          <a-tag class="row-type">{{ postingTypeLabelMap[record.postingType] ?? record.postingType }}</a-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="statusColorMap[record.status]">{{ statusLabelMap[record.status] ?? record.status }}</a-tag>
          <a-tag>{{ receptionStatusLabelMap[record.receptionStatus] ?? record.receptionStatus }}</a-tag>
        </template>
        <template v-else-if="column.key === 'reception'">
          {{ formatDateTime(record.receptionStartDateTime) }} ~ {{ formatDateTime(record.receptionEndDateTime) }}
        </template>
        <template v-else-if="column.key === 'sections'">
          {{ record.sectionSummary.enabledCount }}개
          <span class="sub">(필수 {{ record.sectionSummary.requiredCount }})</span>
        </template>
        <template v-else-if="column.key === 'questions'">
          <template v-if="record.activeQuestionCount > 0">
            {{ record.activeQuestionCount }}개
            <span class="sub">(필수 {{ record.requiredQuestionCount }})</span>
          </template>
          <span v-else class="sub">없음</span>
        </template>
        <template v-else-if="column.key === 'layout'">
          <template v-if="record.layoutStored">저장됨 {{ record.pageCount }}페이지</template>
          <span v-else class="sub">기본값</span>
        </template>
        <template v-else-if="column.key === 'configState'">
          <a-tooltip :title="configStateOf(record.configState).hint">
            <a-tag :color="configStateOf(record.configState).color">
              {{ configStateOf(record.configState).label }}
            </a-tag>
          </a-tooltip>
          <span v-if="!record.editable" class="lock" title="접수가 시작되었거나 마감되어 수정할 수 없습니다.">🔒</span>
        </template>
        <template v-else-if="column.key === 'updatedAt'">
          {{ formatDateTime(record.updatedAt) }}
        </template>
      </template>
    </a-table>
  </div>
</template>

<style scoped lang="scss">
.application-form-list {
  padding: 24px;
}
.page-header {
  margin-bottom: 16px;
}
.page-title {
  margin: 0 0 4px;
}
.page-description {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 13px;
}
.attention {
  margin-bottom: 14px;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}
.row-title {
  margin-right: 6px;
}
.row-type {
  font-size: 11.5px;
}
.sub {
  color: var(--app-text-muted);
  font-size: 12.5px;
}
.lock {
  margin-left: 4px;
}
:deep(.ant-table-row) {
  cursor: pointer;
}
</style>
