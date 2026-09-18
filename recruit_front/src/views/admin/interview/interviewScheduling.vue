<!-- eslint-disable @typescript-eslint/no-unused-vars -->
<script setup lang="ts">
import { computed, onMounted, ref, reactive } from 'vue'
import { message } from 'ant-design-vue'
import { adminJobPostingApi } from '@/api/admin/adminJobPostingApi'
import { adminInterviewApi } from '@/api/admin/adminInterviewApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { AdminJobPosition } from '@/types/admin/jobPosting'
import type { AdminJobPostingListItem } from '@/types/jobPosting'
import type {
    InterviewSearchParams,
    AdminInterviewSummaryResponse,
} from '@/types/admin/interview'
import { ReloadOutlined, SearchOutlined, UploadOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import { formatDate } from '@/common/dateUtil'
import { saveBlobResponse } from '@/common/fileDownload'
import type { TableColumnsType } from 'ant-design-vue'
import { apiClient } from '@/api/client'

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

/* 공통 검색 조건 */
const initialSearchRequest: InterviewSearchParams = {
    stageId: undefined,
    status: undefined,
    from: undefined,
    to: undefined,
    jobPositionId: undefined,
    workLocation: undefined,
    applicationType: undefined,
    groupName: undefined,
};
const searchRequest = reactive<InterviewSearchParams>({
  ...initialSearchRequest,
})

const changeJobPosting = async (jobPostingId: number): Promise<void> => {
  selectedJobPostingId.value = jobPostingId;
  Object.assign(searchRequest, initialSearchRequest);
  refreshing.value = true
  hasSearched.value = false
  try {
    const response = await adminJobPostingApi.getJobPosting(jobPostingId)
    const detail = response.data.data;
    jobPositions.value = detail.jobPositions;
  } catch (error) {
    message.error(getApiErrorMessage(error, '지원분야를 불러오지 못했습니다.'))
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
const interviews = ref<AdminInterviewSummaryResponse[]>([])

const rowKey = (record: AdminInterviewSummaryResponse): string | number => {
  return (record as AdminInterviewSummaryResponse).interviewId
}

/* ---------------- 검색 ---------------- */
const search = async () => {
  // 면접 단계 화면 추가 후 주석 해제
  // if (!searchRequest.stageId) return message.warning('면접단계 선택은 필수입니다.');
  loading.value = true

  try {
    if (selectedJobPostingId.value) {
      const response = await adminInterviewApi.getInterviews(selectedJobPostingId.value, searchRequest);
      const data = response.data.data;

      interviews.value = data;
    }
  } catch (error) {
    message.error(getApiErrorMessage(error, '면접 스케줄링 조회에 실패했습니다.'))
  } finally {
    loading.value = false
    hasSearched.value = true
  }
}

const refresh = (): void => {
  if (selectedJobPostingId.value === null || refreshing.value) return
  Object.assign(searchRequest, initialSearchRequest)
  interviews.value = [];
  hasSearched.value = false
}

/* ---------------- 엑셀 다운로드 ---------------- */
const downloadExcel = async () => {
  if (!selectedJobPostingId.value) return
  loading.value = true
  try {
    const stageId = searchRequest.stageId;
    const response = await apiClient.get(`/admin/job-postings/${selectedJobPostingId.value}/interviews/export`, {
      params: { stageId },
      responseType: 'blob',
    })
    saveBlobResponse(response, `${selectedJobPostingId.value}_${stageId}_면접스케줄링.xlsx`)
  } catch (error) {
    message.error(getApiErrorMessage(error, '엑셀 다운로드에 실패했습니다.'))
  } finally {
    loading.value = false
  }
}

/* ---------------- 엑셀 업로드 (세팅) ---------------- */
const uploadExcel = (file: File) => {
  loading.value = true
  try {
    // TODO: 엑셀 업로드 API 연동 후 데이터 매핑
    message.success('엑셀 업로드가 완료되었습니다.')
  } catch (error) {
    message.error(getApiErrorMessage(error, '엑셀 업로드에 실패했습니다.'))
  } finally {
    loading.value = false
  }
  return false
}

const beforeUpload = (file: File) => {
  uploadExcel(file)
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
                  <!-- 면접 단계 데이터 처리 확인 후 추가 예정 -->
                  <!-- <a-radio-group v-model:value="searchRequest.stageId" button-style="solid">
                    <a-radio-button :value="2">1차 면접</a-radio-button>
                    <a-radio-button :value="3">최종 면접</a-radio-button>
                  </a-radio-group> -->
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
                <template v-if="column.key === 'interviewDate'">{{ formatDate(record.startDateTime, 'YYYY-MM-DD') }}</template>
                <template v-else-if="column.key === 'locationName'">{{ `${record.locationName ?? ''} ${record.roomName ?? ''}`.trim() }}</template>
                <template v-else-if="column.key === 'arrivalTime'">{{ formatDate(record.startDateTime, 'HH:mm') }}</template>
                <template v-else-if="column.key === 'interviewTime'">{{ formatDate(record.endDateTime, 'HH:mm') }}</template>
                <template v-else-if="column.key === 'interviewOrder'">{{ record.candidateCount }}</template>
                <template v-else-if="column.key === 'groupName'">{{ record.groupName }}</template>
                <template v-else-if="column.key === 'interviewerName'">{{ record.interviewerCount }}</template>
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