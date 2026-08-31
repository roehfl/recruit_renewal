<!-- eslint-disable @typescript-eslint/no-unused-vars -->
<script setup lang="ts">
import { computed, onMounted, ref, h, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { adminJobPostingApi } from '@/api/admin/adminJobPostingApi'
import { adminApplicationApi } from '@/api/admin/adminApplicationApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { AdminJobPosition, AdminJobPostingListItem, } from '@/types/admin/jobPosting'
import type { 
  AdminApplicationSummaryResponse,
  AdminApplicationSearchRequest,
} from '@/types/admin/application'
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { formatDate } from '@/common/dateUtil'
import type { CommonCodeItems } from '@/types/commonCode'
import type { TableColumnsType } from 'ant-design-vue'
import SchoolModalBody from '../../common/SchoolModalBody.vue'
import { apiClient } from '@/api/client'
import { commonCodeApi } from '@/api/commonApi'

interface TableRow {
  key: string
  jobPositionNameSnapshot: string
  status: 'DRAFT' | 'SUBMITTED' | 'WITHDRAWN'
  applicationId: string
  applicantNameSnapshot: string
  submittedAt: string
}

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const schoolModalOpen = ref(false)
const schoolModalBodyRef = ref<InstanceType<typeof SchoolModalBody>>();

const handleSchoolConfirm = () => {
  const schoolForm = schoolModalBodyRef.value?.schoolForm;
  searchRequest.schoolName = schoolForm?.schoolName

  schoolModalOpen.value = false
}

const selectRowKeys = ref<string[]>([]);

const rowSelection = {
  selectRowKeys, 
  onchange: (keys: string[]) => {
    selectRowKeys.value = keys;
  },
}
const statusLabelMap: Record<string, string> = {
  DRAFT: '임시저장',
  SUBMITTED: '제출 완료',
  WITHDRAWN: '작성 완료',
}
const educationLevelMap: Record<string, string> = {
  HIGH_SCHOOL: '고등학교', 
  COLLEGE: '전문대학교',
  UNIVERSITY: '대학교', 
  MASTER: '대학원(석사)', 
  DOCTOR: '대학원(박사)',
}
const stageTypeMap: Record<string, string> = {
  DOCUMENT: '서류',
  FIRST_INTERVIEW: '1차 면접',
  SECOND_INTERVIEW: '2차 면접',
  FINAL_INTERVIEW: '최종 면접',
  ETC: '기타',
}
const stageResultStatusMap: Record<string, string> = {
  PENDING: '대기',
  PASSED: '합격',
  FAILED: '불합격',
  ABSENT: '결시',
  WITHDRAWN: '지원 철회',
  HOLD: '보류',
}
const applicationTypeOptions = [
  { value: 'NEW_GRADUATE', label: '신입' },
  { value: 'EXPERIENCED', label: '경력' },
  { value: 'NEW_GRADUATE_OR_EXPERIENCED', label: '신입/경력' },
]

const columns: TableColumnsType<TableRow> = [
  { title: '지원분야', dataIndex: 'jobPositionNameSnapshot', key: 'jobPosition' },
  {  
    title: '전형별결과', 
    key: 'stageResult',
    customRender: ({ text }) => `${stageTypeMap[text.stageType] ?? ''} ${stageResultStatusMap[text.stageResultStatus] ?? ''}`,
  },
  { title: '근무지', dataIndex: 'workLocation', key: 'workLocation' },
  {
    title: '수험번호', dataIndex: 'applicationId', key: 'applicationId',
    customRender: ( {text}) => h('a',  { onClick: () => goApplication(text), class: 'applicationId-link' }, text),
  },
  { title: '이름', dataIndex: 'applicantNameSnapshot', key: 'applicantName' },
  { title: '생년월일', dataIndex: 'birthDate', key: 'birthDate' },
  { title: '나이', dataIndex: 'age', key: 'age' },
  { title: '최종대학교', dataIndex: 'finalSchoolName', key: 'finalSchoolName' },
  { title: '최종학력', dataIndex: 'finalEducationLevel', key: 'finalEducationLevel' },
  { title: '졸업년월', dataIndex: 'withdrawnAt', key: 'withdrawnAt' },
  { title: '최종제출일시', dataIndex: 'submittedAt', key: 'submittedAt' },
  { title: '경력기술서', dataIndex: 'careerDescriptionDownloadUrl', key: 'careerDescriptionDownloadUrl' },
]

const initializing = ref(true)
const refreshing = ref(false)
const loadFailed = ref(false)

const stageTypeList = ref<CommonCodeItems[]>([])
const stageTypeOptions = computed(() =>
  stageTypeList.value.map((code) => ({ value: code.code, label: code.displayName })),
)

const stageResultStatusList = ref<CommonCodeItems[]>([])
const stageResultStatusOptions = computed(() =>
  stageResultStatusList.value.map((code) => ({ value: code.code, label: code.displayName })),
)

const jobPositions = ref<AdminJobPosition[]>([
  { id: null, positionName: '', applicationType: 'NEW_GRADUATE_OR_EXPERIENCED', jobTitle: null, workLocations: [], employmentType: 'FULL_TIME', sortOrder: 0 },
])

const jobPostings = ref<AdminJobPostingListItem[]>([])
const selectedJobPostingId = ref<number | null>(null)

const jobPostingOptions = computed(() => {
  return jobPostings.value.map((posting) => ({
    value: posting.id,
    label: posting.title,
  }))
})
const jobPositionOptions = computed(() => {
  return jobPositions.value.map((posting) => ({
    value: posting.id,
    label: posting.positionName,
  }))
})
// 근무지 검색은 공통코드 code 로 비교한다. 공고의 모집분야들이 제시한 후보를 코드 기준으로 중복 제거한다.
const jobWorkLocationOptions = computed(() => {
  const workLocations = new Map<string, string>()
  jobPositions.value.forEach((position) => {
    position.workLocations?.forEach((workLocation) => {
      workLocations.set(workLocation.code, workLocation.name)
    })
  })
  return [...workLocations].map(([code, name]) => ({ value: code, label: name }))
})

const changeJobPosting = async (jobPostingId: number): Promise<void> => {
  selectedJobPostingId.value = jobPostingId;
  Object.assign(searchRequest, initialSearchRequest);
  selectRowKeys.value = [];
  refreshing.value = true
  loadFailed.value = false
  try {
    const response = await adminJobPostingApi.getJobPosting(jobPostingId)
    const detail = response.data.data;
    jobPositions.value = detail.jobPositions;
  } catch (error) {
    loadFailed.value = true
    message.error(getApiErrorMessage(error, '지원분야를 불러오지 못했습니다.'))
  } finally {
    refreshing.value = false
  }
}

const changeJobPosition = async (jobPositionId: number) => {
  searchRequest.jobPositionId = jobPositionId;
}

const refresh = async (): Promise<void> => {
  if (selectedJobPostingId.value === null || refreshing.value) return

  await changeJobPosting(selectedJobPostingId.value)
}

const goApplication = async (applicationId: number) => {
  await router.push(`applications/${applicationId}`)
}

// 전화번호 입력 input (숫자만 입력 가능하도록)
const onlyNumber = (e: KeyboardEvent) => {
    const allowKeys = ['Backspace', 'Delete', 'ArrowLeft', 'ArrowRight', 'Tab'];

    if (allowKeys.includes(e.key)) return;
    if (!/^\d$/.test(e.key)) e.preventDefault();
}

const applications = ref<AdminApplicationSummaryResponse[]>([])

const initialSearchRequest: AdminApplicationSearchRequest = {
    graduationStatus:undefined,       // 졸업 여부
    jobPositionId:undefined,          // 지원 분야
    certificateName: undefined,       // 자격증
    languageName: undefined,          // 언어
    workLocation: undefined,          // 근무지
    birthDateTo: undefined,           // 생년월일 TO
    applicationType: undefined,       // 지원 구분 
    name: undefined,                  // 이름
    languageLevel: undefined,         // 외국어 수준
    stageResultStatus: undefined,     // 전형별 결과
    finalSchoolCondition: undefined,  // 최종학교 조건
    finalEducationLevel:undefined,    // 최종 학력
    status:undefined,                 // 지원서 상태
    birthDateFrom:undefined,          // 생년월일 FROM 
    stageType:undefined,              // 비상연락처
    schoolName:undefined,             // 학교명
};

const searchRequest = reactive<AdminApplicationSearchRequest>({
  ...initialSearchRequest,
});

const save = async () => {
  applications.value = [];
  loading.value = true

  try {
    if (selectedJobPostingId.value) {
      const response = await adminApplicationApi.getApplications(selectedJobPostingId.value, searchRequest);

      applications.value = response.data.data.content;
    }
  } catch (error) {
    message.error(getApiErrorMessage(error, '지원현황 조회에 실패했습니다.'))
  } finally {
    loading.value = false
  }
}

const careerDescriptionDownload = async (url: string) => {
  try {
    const response = await apiClient.get(url, {responseType: 'blob'});
    
    const contentDisposition = response.headers['content-disposition'];
    let fileName = '경력기술서';
    const match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i,);
    if (match) fileName = decodeURIComponent(match[1]);
    
    const blobUrl = URL.createObjectURL(response.data); 
    const link = document.createElement('a');
    link.href = blobUrl;
    link.download = fileName;

    document.body.appendChild(link);
    link.click();
    link.remove();

    URL.revokeObjectURL(blobUrl);
  } catch (error) {
    message.error(getApiErrorMessage(error, '경력기술서 다운로드에 실패했습니다.'))
  } finally {
    loading.value = false
  }
}

/* 기본 선택은 접수 중인 첫 공고다. 없으면 목록의 첫 공고로 떨어진다. */
const pickDefaultJobPosting = (postings: AdminJobPostingListItem[]): AdminJobPostingListItem | null => {
  return postings.find((posting) => posting.accepting) ?? postings[0] ?? null
}

async function loadStageTypeCodes() {
  const result = await commonCodeApi.getCommonCodes('STAGE_TYPE')
  stageTypeList.value = result.data.data ?? []
}

async function loadStageResultStatusCodes() {
  const result = await commonCodeApi.getCommonCodes('STAGE_RESULT_STATUS')
  stageResultStatusList.value = result.data.data ?? []
}

onMounted(async () => {
  loadStageTypeCodes()
  loadStageResultStatusCodes()

  try {
    const response = await adminJobPostingApi.getJobPostings()
    jobPostings.value = response.data.data.content;
    const defaultPosting = pickDefaultJobPosting(jobPostings.value)

    if (defaultPosting) {
      await changeJobPosting(defaultPosting.id)
    }
  } catch (error) {
    loadFailed.value = true
    message.error(getApiErrorMessage(error, '공고 목록을 불러오지 못했습니다.'))
  } finally {
    initializing.value = false
  }
})


</script>
<template>
  <div class="job-posting-form">
    <header class="page-header">
      <h2 class="page-title">지원현황 조회</h2>
      <p class="page-description">지원 현황을 조회하고 상세 목록에서 지원서를 조회합니다.</p>
    </header>

    <a-spin :spinning="loading">
      <a-card :bordered="false" class="form-card">
        <div>
          <table class="table-area">
          <colgroup>
            <col style="width: 15%;">  <col style="width: 12%;">  <col style="width: 30%;">
            <col style="width: 13%;">  <col style="width: 25%;">
          </colgroup>
          <tbody>
            <tr>
              <th rowspan="2" class="depth1">채용정보</th>
              <th>채용구분</th> 
              <td>
                <div class="filter-bar">
                  <a-select
                    :value="selectedJobPostingId"
                    class="posting-select"
                    placeholder="공고를 선택하세요"
                    :options="jobPostingOptions"
                    :disabled="initializing || jobPostings.length === 0"
                    show-search
                    option-filter-prop="label"
                    @change="changeJobPosting"
                  />
                  </div>
              </td>
              <th>지원구분</th>
              <td class="span-section">
                <a-select v-model:value="searchRequest.applicationType" style="width: 200px"  placeholder="전체"
                :options="applicationTypeOptions"
                />
              </td>  
            </tr>

            <tr>
              <th>지원분야</th>
              <td>
                <a-select :value="searchRequest.jobPositionId" style="width: 200px" placeholder="전체"
                  :options="jobPositionOptions" @change="changeJobPosition"
                />
              </td>
              <th>직무/근무지</th>
              <td>
                <a-select v-model:value="searchRequest.workLocation" style="width: 200px"  placeholder="전체"
                :options="jobWorkLocationOptions"
                />
              </td>
            </tr>

            <tr>
              <th class="depth1">인적사항</th>
              <th>생년월일</th>
              <td>
                <div class="flex-div-area">
                  <a-date-picker v-model:value="searchRequest.birthDateFrom" value-format="YYYY-MM-DD" ></a-date-picker>
                  <span>~</span>
                  <a-date-picker v-model:value="searchRequest.birthDateTo" value-format="YYYY-MM-DD"></a-date-picker>
                </div>
              </td>
              <th>이름</th> 
              <td>
                  <a-input v-model:value="searchRequest.name" style="width: 200px" placeholder="홍길동"/>
              </td>
            </tr>

            <tr>
              <th class="depth1">기타</th>
              <th>전형별 결과</th> 
              <td>
                <div class="flex-div-area">
                  <a-select
                    v-model:value="searchRequest.stageType" style="width: 120px"
                    :options="stageTypeOptions" placeholder="선택" allow-clear
                  />
                  <a-select
                    v-model:value="searchRequest.stageResultStatus" style="width: 120px"
                    :options="stageResultStatusOptions" placeholder="선택" allow-clear
                  />
                </div>
              </td>
              <th>연락처</th>
              <td>
                <a-input v-model:value="searchRequest.certificateName" style="width: 200px" 
                  placeholder="01012345678" :maxlength="11" @keydown="onlyNumber"
                />
              </td>
            </tr>
          </tbody>
        </table>
        </div>
        
      <div class="form-actions">
        <a-button type="primary" :loading="saving" @click="save">
          <SearchOutlined />
          검색
        </a-button>
        <a-button :disabled="selectedJobPostingId === null" @click="refresh">
          <ReloadOutlined />
          초기화
        </a-button>
      </div>
      </a-card>


      <a-card :bordered="false" class="form-card">
        <div class="button-area">
          <a-button>인쇄</a-button>
          <a-button>PDF 인쇄</a-button>
          <a-button>미리보기</a-button>
          <a-button>엑셀 다운로드</a-button>
        </div>
        <div>
          <a-table :columns="columns" :data-source="applications" :pagination="{ pageSize: 5 }" :row-selection="rowSelection" row-key="applicationId">
            <template #bodyCell="{ column, record }">
              <!-- <template v-if="column.key === 'status'">
                <a-tag :color="statusColorMap[record.status]">{{ statusLabelMap[record.status] ?? record.status }}</a-tag>
                {{ statusLabelMap[record.status] ?? record.status }}
              </template> -->
              <template v-if="column.key === 'stageType'">
                <!-- <a-tag :color="statusColorMap[record.status]">{{ statusLabelMap[record.status] ?? record.status }}</a-tag> -->
                {{ statusLabelMap[record.status] ?? record.status }}
              </template>
              <template v-else-if="column.key === 'finalEducationLevel'">
                {{ educationLevelMap[record.finalEducationLevel] ?? record.finalEducationLevel }}
              </template>
              <template v-else-if="column.key === 'withdrawnAt'">
                {{ formatDate(record.withdrawnAt, 'YYYY-MM-DD HH:mm') }}
              </template>
              <template v-else-if="column.key === 'submittedAt'">
                {{ formatDate(record.submittedAt, 'YYYY-MM-DD HH:mm') }}
              </template>
              <template v-else-if="column.key === 'careerDescriptionDownloadUrl'">
                <a-button v-if="record.careerDescriptionDownloadUrl" 
                  class="careerButton" 
                  @click="careerDescriptionDownload(record.careerDescriptionDownloadUrl)">
                  DOWNLOAD
                </a-button>
              </template>
            </template>
          </a-table>

        </div>
        <div class="config-grid">
        </div>
      </a-card>

      <!-- 학교 모달 -->
      <a-modal
        v-model:open="schoolModalOpen"
        title="학교 찾기"
        @ok="handleSchoolConfirm"
      >
        <SchoolModalBody 
          ref="schoolModalBodyRef"
          :open="schoolModalOpen"
          :education-level="searchRequest.finalEducationLevel"
        />
      </a-modal>
    </a-spin>
  </div>
</template>

<style scoped>
.job-posting-form {
  padding: 24px;
  /* max-width: 1080px; */
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
.table-area .depth1 {
    background: #EEE;
    border-bottom: 1px solid #dadada;
    text-align: center;
}

em {
    color: red;
    font-style: normal;    
}


.form-actions {
  margin-top: 12px;
  display: flex;
  justify-content: center;
  gap: 8px;
}
.button-area {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 12px;
}
.button-area * {
  font-size: 13px;
  line-height: normal;
}

.filter-bar {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.posting-select {
  min-width: 280px;
}

.flex-div-area{
  display: flex;
  gap: 8px;
  align-items: center;
}

.shcoolNameSearchBtn { 
  margin-left: 8px;
}

:deep(.ant-table-tbody >tr.ant-table-row-selected >td){
  background: var(--app-bg-selected);
}

:deep(.ant-table-tbody >tr.ant-table-row-selected:hover>td){
  background: #e8f0de;
}

:deep(.ant-table) { 
  /* font-size: 13px; */
  text-align-last: center;
}

:deep(.ant-input) {
  width: 100%;
  text-overflow: ellipsis;
}

.careerButton{
  font-size: 10px;
  padding: 0px 8px;
}

:deep(.applicationId-link) {
  font-weight: 500;
  text-decoration: underline;
}
</style>
