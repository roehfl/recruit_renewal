<!-- eslint-disable @typescript-eslint/no-unused-vars -->
<script setup lang="ts">
import { computed, h, onMounted, ref, reactive } from 'vue'
import { message, Modal } from 'ant-design-vue'
import axios from 'axios'
import { adminJobPostingApi } from '@/api/admin/adminJobPostingApi'
import { adminInterviewApi } from '@/api/admin/adminInterviewApi'
import { adminStageApi } from '@/api/admin/adminStageApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { AdminJobPosition } from '@/types/admin/jobPosting'
import type { AdminJobPostingListItem } from '@/types/jobPosting'
import type { ApiFailurePayload, StageListItem, StageType } from '@/types/admin/stage'
import type {
    AdminInterviewScheduleInterviewer,
    AdminInterviewScheduleRow,
    InterviewScheduleSearchParams,
    InterviewScheduleUploadResponse,
} from '@/types/admin/interview'
import { ReloadOutlined, SearchOutlined, UploadOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import { formatDate } from '@/common/dateUtil'
import { getBlobErrorMessage, saveBlobResponse } from '@/common/fileDownload'
import type { TableColumnsType } from 'ant-design-vue'

const loading = ref(false)
const hasSearched = ref(false)
const initializing = ref(true)
const refreshing = ref(false)

const jobPostings = ref<AdminJobPostingListItem[]>([])
const selectedJobPostingId = ref<number | null>(null)
const jobPositions = ref<AdminJobPosition[]>([
  { id: null, positionName: '', applicationType: 'NEW_GRADUATE_OR_EXPERIENCED', jobTitle: null, workLocations: [], employmentType: 'FULL_TIME', sortOrder: 0 },
])

const jobPostingOptions = computed(() => {
  return jobPostings.value.map((posting) => ({
    value: posting.id,
    label: posting.title,
  }))
})
const jobPositionOptions = computed(() => {
  const options = jobPositions.value.map((posting) => ({
    value: posting.id,
    label: posting.positionName,
  }))
  options.unshift({ value: null, label: '전체' })
  return options
})
const jobWorkLocationOptions = computed(() => {
  const workLocations = new Map<string, string>()
  jobPositions.value.forEach((position) => {
    position.workLocations?.forEach((workLocation) => {
      workLocations.set(workLocation.code, workLocation.name)
    })
  })
  const options = [...workLocations].map(([code, name]) => ({ value: code, label: name }))
  options.unshift({ value: '', label: '전체' })
  return options
})

const applicationTypeOptions = [
  { value: '', label: '전체' },
  { value: 'NEW_GRADUATE', label: '신입' },
  { value: 'EXPERIENCED', label: '경력' },
  { value: 'NEW_GRADUATE_OR_EXPERIENCED', label: '신입/경력' },
]

/* 면접 단계: 면접 유형 단계만 스케줄을 가진다(백엔드 InterviewScheduleService 와 같은 기준) */
const INTERVIEW_STAGE_TYPES: StageType[] = ['FIRST_INTERVIEW', 'SECOND_INTERVIEW', 'FINAL_INTERVIEW']
const stages = ref<StageListItem[]>([])
const interviewStages = computed(() =>
  stages.value
    .filter((stage) => INTERVIEW_STAGE_TYPES.includes(stage.stageType))
    .sort((a, b) => a.stageOrder - b.stageOrder),
)

/* 공통 검색 조건 */
interface ScheduleSearchForm {
  stageId: number | undefined
  applicationType: string | undefined
  jobPositionId: number | undefined
  workLocation: string | undefined
  groupName: string | undefined
}
const initialSearchRequest: ScheduleSearchForm = {
    stageId: undefined,
    jobPositionId: undefined,
    workLocation: undefined,
    applicationType: undefined,
    groupName: undefined,
};
const searchRequest = reactive<ScheduleSearchForm>({
  ...initialSearchRequest,
})

/** 화면의 빈 선택('전체')은 조건 없음으로 보낸다. 면접단계가 없으면 조회할 수 없다. */
const toSearchParams = (): InterviewScheduleSearchParams | null => {
  if (searchRequest.stageId === undefined) return null
  const groupName = searchRequest.groupName?.trim()
  return {
    stageId: searchRequest.stageId,
    applicationType: searchRequest.applicationType || undefined,
    jobPositionId: searchRequest.jobPositionId,
    workLocation: searchRequest.workLocation || undefined,
    groupName: groupName || undefined,
  }
}

/** 기본 면접단계: 진행 중 → 준비 → 첫 면접 단계. */
const pickDefaultStage = (list: StageListItem[]): StageListItem | undefined =>
  list.find((stage) => stage.status === 'IN_PROGRESS')
  ?? list.find((stage) => stage.status === 'READY')
  ?? list[0]

const changeJobPosting = async (jobPostingId: number): Promise<void> => {
  selectedJobPostingId.value = jobPostingId;
  Object.assign(searchRequest, initialSearchRequest);
  interviews.value = []
  lastSearchParams.value = null
  refreshing.value = true
  hasSearched.value = false
  try {
    const [postingResponse, stageResponse] = await Promise.all([
      adminJobPostingApi.getJobPosting(jobPostingId),
      adminStageApi.getStages(jobPostingId),
    ])
    jobPositions.value = postingResponse.data.data.jobPositions;
    stages.value = stageResponse.data.data
    searchRequest.stageId = pickDefaultStage(interviewStages.value)?.id
  } catch (error) {
    message.error(getApiErrorMessage(error, '지원분야·면접단계를 불러오지 못했습니다.'))
  } finally {
    refreshing.value = false
  }
}

const changeJobPosition = async (jobPositionId: number | null) => {
  searchRequest.jobPositionId = jobPositionId ?? undefined;
}

const columns: TableColumnsType = [
  { title: '일자', key: 'interviewDate' },
  { title: '장소', key: 'locationName' },
  { title: '도착시간', key: 'arrivalTime' },
  { title: '면접시간', key: 'interviewTime' },
  { title: '면접순서', key: 'interviewOrder' },
  { title: '조', key: 'groupName' },
  { title: '면접관', key: 'interviewerName' },
  { title: '수험번호', key: 'applicationId' },
  { title: '성명', key: 'applicantName' },
]

/* ---------------- 데이터 ---------------- */
const interviews = ref<AdminInterviewScheduleRow[]>([])
/** 표에 보이는 행을 만든 조건. 다운로드가 화면과 같은 행을 받도록 이 조건으로 요청한다. */
const lastSearchParams = ref<InterviewScheduleSearchParams | null>(null)

/* 한 면접(조)에 지원자가 여럿이라 면접 id 만으로는 행이 겹친다. */
const rowKey = (record: AdminInterviewScheduleRow): string => `${record.interviewId}-${record.applicationId}`

const interviewersText = (interviewers: AdminInterviewScheduleInterviewer[]): string =>
  interviewers.map((interviewer) => `${interviewer.name}(${interviewer.loginId})`).join(', ')

/* ---------------- 검색 ---------------- */
const search = async () => {
  if (!selectedJobPostingId.value) return
  const params = toSearchParams()
  if (!params) {
    message.warning('면접단계를 선택하세요.')
    return
  }
  loading.value = true

  try {
    const response = await adminInterviewApi.getSchedules(selectedJobPostingId.value, params);
    interviews.value = response.data.data;
    lastSearchParams.value = params
  } catch (error) {
    message.error(getApiErrorMessage(error, '면접 스케줄링 조회에 실패했습니다.'))
  } finally {
    loading.value = false
    hasSearched.value = true
  }
}

/* 면접단계는 필수 선택이라 초기화해도 유지한다. */
const refresh = (): void => {
  if (selectedJobPostingId.value === null || refreshing.value) return
  Object.assign(searchRequest, { ...initialSearchRequest, stageId: searchRequest.stageId })
  interviews.value = [];
  lastSearchParams.value = null
  hasSearched.value = false
}

/* ---------------- 엑셀 다운로드 ---------------- */
/* 표에 행이 있으면 그 행을 채운 파일, 비어 있으면 헤더만 있는 양식을 받는다. */
const downloadExcel = async () => {
  if (!selectedJobPostingId.value) return
  loading.value = true
  try {
    if (interviews.value.length > 0 && lastSearchParams.value) {
      const response = await adminInterviewApi.exportSchedules(selectedJobPostingId.value, lastSearchParams.value)
      saveBlobResponse(response, '면접스케줄.xlsx')
    } else {
      const response = await adminInterviewApi.downloadScheduleTemplate(selectedJobPostingId.value)
      saveBlobResponse(response, '면접스케줄_양식.xlsx')
    }
  } catch (error) {
    message.error(await getBlobErrorMessage(error, '엑셀 다운로드에 실패했습니다.'))
  } finally {
    loading.value = false
  }
}

/* ---------------- 엑셀 업로드 ---------------- */
/** 400 본문에서 업로드 결과를 꺼낸다. 행 오류면 data 에 오류 목록이 있고, 파일 자체가 거부되면 null. */
const extractUploadPayload = (error: unknown): InterviewScheduleUploadResponse | null => {
  if (!axios.isAxiosError<ApiFailurePayload<InterviewScheduleUploadResponse>>(error)) {
    return null
  }
  return error.response?.data?.data ?? null
}

const MAX_ERROR_LINES = 50

const showUploadErrors = (payload: InterviewScheduleUploadResponse) => {
  const lines = [
    ...payload.errors,
    ...payload.rowErrors.map((rowError) => `${rowError.rowNumber}행: ${rowError.messages.join(' / ')}`),
  ]
  const shown = lines.slice(0, MAX_ERROR_LINES)
  Modal.error({
    title: '업로드 검증에 실패하여 반영하지 않았습니다.',
    width: 720,
    content: h('div', [
      h('p', '아래 오류를 고친 뒤 다시 업로드하세요. 오류가 하나라도 있으면 아무것도 반영되지 않습니다.'),
      h('ul', shown.map((line) => h('li', line))),
      lines.length > shown.length ? h('p', `외 ${lines.length - shown.length}건`) : null,
    ]),
  })
}

const uploadExcel = async (file: File) => {
  if (!selectedJobPostingId.value || searchRequest.stageId === undefined) return
  loading.value = true
  try {
    const response = await adminInterviewApi.uploadSchedules(selectedJobPostingId.value, searchRequest.stageId, file)
    const result = response.data.data
    message.success(`엑셀 업로드가 완료되었습니다. 면접 ${result.interviewCount}건 · 지원자 ${result.candidateCount}명`)
    loading.value = false
    await search()
  } catch (error) {
    const payload = extractUploadPayload(error)
    if (payload) {
      showUploadErrors(payload)
    } else {
      message.error(getApiErrorMessage(error, '엑셀 업로드에 실패했습니다.'))
    }
  } finally {
    loading.value = false
  }
}

/* 업로드는 단계 스케줄 전체 교체 + 즉시 공개라 되돌릴 수 없다. 확인을 받는다. */
const beforeUpload = (file: File) => {
  if (!selectedJobPostingId.value) return false
  const stage = interviewStages.value.find((item) => item.id === searchRequest.stageId)
  if (!stage) {
    message.warning('면접단계를 선택하세요.')
    return false
  }
  Modal.confirm({
    title: '면접 스케줄을 업로드할까요?',
    content: `${stage.stageName}의 기존 스케줄을 파일 내용으로 모두 교체하고, 바로 지원자·면접관에게 공개합니다.`,
    okText: '업로드',
    cancelText: '취소',
    onOk: () => uploadExcel(file),
  })
  return false
}

/* ---------------- 공통 초기화 ---------------- */
const pickDefaultJobPosting = (postings: AdminJobPostingListItem[]): AdminJobPostingListItem | null => {
  return postings.find((posting) => posting.accepting) ?? postings[0] ?? null
}

onMounted(async () => {
  try {
    const response = await adminJobPostingApi.getJobPostings()
    jobPostings.value = response.data.data.content;
    const defaultPosting = pickDefaultJobPosting(jobPostings.value)

    if (defaultPosting) {
      await changeJobPosting(defaultPosting.id)
    }
  } catch (error) {
    message.error(getApiErrorMessage(error, '공고 목록을 불러오지 못했습니다.'))
  } finally {
    initializing.value = false
  }
})
</script>
<template>
  <div class="job-posting-form">
    <header class="page-header">
      <h2 class="page-title">면접 스케줄링</h2>
      <p class="page-description">면접 스케줄을 조회하고 엑셀 업로드를 통해 세팅합니다.</p>
    </header>

    <a-spin :spinning="loading">
      <a-card :bordered="false" class="form-card">
        <div>
          <table class="table-area">
            <colgroup>
              <col style="width: 15%;"> <col style="width: 35%;"> <col style="width: 15%;"> <col style="width: 35%;">
            </colgroup>
            <tbody>
              <tr>
                <th>채용구분<em> *</em></th>
                <td>
                  <div class="filter-bar">
                    <a-select :value="selectedJobPostingId" class="posting-select"
                      placeholder="공고를 선택하세요" :options="jobPostingOptions" :disabled="initializing || jobPostings.length === 0"
                      show-search option-filter-prop="label" @change="changeJobPosting"
                    />
                  </div>
                </td>
                <th>면접단계<em> *</em></th>
                <td>
                  <a-radio-group v-if="interviewStages.length > 0" v-model:value="searchRequest.stageId" button-style="solid">
                    <a-radio-button v-for="stage in interviewStages" :key="stage.id" :value="stage.id">
                      {{ stage.stageName }}
                    </a-radio-button>
                  </a-radio-group>
                  <span v-else-if="selectedJobPostingId !== null && !refreshing" class="guide-item-default">
                    면접 단계가 없습니다. 전형결과 관리의 단계 설정에서 면접 단계를 추가하세요.
                  </span>
                </td>
              </tr>
              <tr>
                <th>응시구분</th>
                <td><a-select v-model:value="searchRequest.applicationType" style="width: 200px" placeholder="전체" :options="applicationTypeOptions"/></td>
                <th>지원분야</th>
                <td><a-select :value="searchRequest.jobPositionId" style="width: 200px" placeholder="전체" :options="jobPositionOptions" @change="changeJobPosition"/></td>
              </tr>
              <tr>
                <th>근무지</th>
                <td><a-select v-model:value="searchRequest.workLocation" style="width: 200px" placeholder="전체" :options="jobWorkLocationOptions"/></td>
                <th>면접조</th>
                <td><a-input v-model:value="searchRequest.groupName" style="width: 200px" placeholder="전체" /></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="form-actions">
          <a-button type="primary" @click="search"><SearchOutlined />검색</a-button>
          <a-button :disabled="selectedJobPostingId === null" @click="refresh"><ReloadOutlined />초기화</a-button>
        </div>
      </a-card>

      <a-card :bordered="false" class="form-card">
        <div class="interviewer-setting-guide-div">
          <div class="interviewer-setting-guide">
          <p class="guide-item-default">※ 면접 스케줄링 방법 : 채용구분, 면접 단계 선택 → 검색버튼으로 조회 → 엑셀 다운로드 후 내용 작성 → 엑셀 업로드</p>
          <p class="guide-item-default">※ 엑셀 업로드 방법 : 엑셀을 다운로드 → 암호화 해제 → 엑셀 업로드</p>
          <p class="guide-item-default">※ 업로드하면 선택한 면접단계의 기존 스케줄이 파일 내용으로 교체되고, 바로 지원자·면접관에게 공개됩니다.</p>
          <p class="guide-item-default">※ 한 행에 지원자 1명. 같은 조는 일자·장소·도착시간·면접시간·면접관이 같아야 하고, 조·면접순서는 1부터 이어서 적습니다. 면접관은 이름(로그인ID)을 쉼표로 구분합니다.</p>
          <div>
            <b class="guide-item-required">※ 화면에서 편집 불가&nbsp;</b>
            <b class="guide-item">엑셀 다운로드 후 수정한 엑셀을 업로드 하여 편집 가능</b>
          </div>
        </div>
          <div class="button-area">
              <a-upload :before-upload="beforeUpload" :show-upload-list="false" accept=".xlsx,.xls">
                <a-button danger><UploadOutlined />엑셀 업로드</a-button>
              </a-upload>
              <a-button @click="downloadExcel"><DownloadOutlined />엑셀 다운로드</a-button>
          </div>
        </div>

        <div class="table-overflow">
          <a-table class="table-width" :columns="columns" :data-source="interviews" :pagination="{ pageSize: 10 }" :row-key="rowKey">
            <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'interviewDate'">{{ formatDate(record.interviewDateTime, 'YYYY-MM-DD') }}</template>
                <template v-else-if="column.key === 'locationName'">{{ record.locationName ?? '' }}</template>
                <template v-else-if="column.key === 'arrivalTime'">{{ record.arrivalDateTime ? formatDate(record.arrivalDateTime, 'HH:mm') : '' }}</template>
                <template v-else-if="column.key === 'interviewTime'">{{ formatDate(record.interviewDateTime, 'HH:mm') }}</template>
                <template v-else-if="column.key === 'interviewOrder'">{{ record.candidateOrder ?? '' }}</template>
                <template v-else-if="column.key === 'groupName'">{{ record.groupName }}</template>
                <template v-else-if="column.key === 'interviewerName'">{{ interviewersText(record.interviewers) }}</template>
                <template v-else-if="column.key === 'applicationId'">{{ record.applicationId ?? '' }}</template>
                <template v-else-if="column.key === 'applicantName'">{{ record.applicantName ?? '' }}</template>
              </template>
            <template #emptyText>
              <a-empty class="empty-box" v-if="!hasSearched" description="검색 버튼을 클릭하세요."/>
              <a-empty class="empty-box" v-else description="조회 내역이 없습니다."/>
            </template>
          </a-table>
        </div>
      </a-card>
    </a-spin>

  </div>
</template>

<style scoped>
.job-posting-form {
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
  color: #888;
}
.form-card {
  margin-bottom: 16px;
}

.table-area {
    width: 100%;
    border: 1px solid #f0f0f0;
    border-collapse: collapse;
}
.table-area th,
.table-area td {
    border: 1px solid #f0f0f0;
    padding: 8px;
}
.table-area th {
    background: #fafafa;
    text-align: left;
    padding: 8px 16px;
}

.table-overflow {
  overflow-y: auto;
  margin-top: 12px;
}
.table-width {
  min-width: 800px;
}

.form-actions {
  margin-top: 12px;
  display: flex;
  justify-content: center;
  gap: 8px;
}

.button-area {
  display: flex;
  gap: 8px;
}
.button-area * {
  font-size: 13px;
  line-height: normal;
  align-content: center;
}

.filter-bar {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.posting-select {
  width: 100%;
}

.flex-div-area{
  display: flex;
  gap: 8px;
  align-items: center;
}
em {
    color: red;
    font-style: normal;
}
.empty-box{
  padding: 16px 12px 12px;
}
:deep(.ant-table-tbody >tr.ant-table-row-selected >td){
  background: var(--app-bg-selected);
}
:deep(.ant-table-tbody >tr.ant-table-row-selected:hover>td){
  background: #e8f0de;
}
:deep(.ant-table) {
  text-align-last: center;
}
:deep(.ant-table-thead>tr>th),
:deep(.ant-table-tbody>tr>td) {
  padding: 12px;
}
:deep(.ant-table-tbody>tr>td:last-child) {
  padding: 8px;
}
:deep(.ant-input) {
  width: 100%;
  text-overflow: ellipsis;
}

.interviewer-setting-guide-div {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 8px;
}
.interviewer-setting-guide {
  display: flex;
  flex-direction: column;
  gap: 4px 16px;
}
.guide-item-default {
  color: #888;
  font-size: 13px;
  margin: 0;
}
.guide-item-required {
  margin: 0;
  font-size: 13px;
  color: red
}
.guide-item {
  margin: 0;
  font-size: 13px;
  color: var(--app-primary-color);
}
</style>