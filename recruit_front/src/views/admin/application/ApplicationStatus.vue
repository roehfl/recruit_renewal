<!-- eslint-disable @typescript-eslint/no-unused-vars -->
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, h, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { adminJobPostingApi, getAllJobPostings } from '@/api/adminJobPostingApi'
import { adminApplicationApi } from '@/api/admin/adminApplicationApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { AdminJobPosition } from '@/types/admin/jobPosting'
import type { AdminJobPostingListItem } from '@/types/jobPosting'
import type { 
  AdminApplicationSummaryResponse,
  AdminApplicationSearchRequest,
} from '@/types/admin/application'
import { ReloadOutlined, SearchOutlined, PrinterOutlined, FileExcelOutlined, FilePdfOutlined } from '@ant-design/icons-vue'
import { formatDate } from '@/common/dateUtil'
import { getBlobErrorMessage, saveBlobResponse } from '@/common/fileDownload'
import type { TableColumnsType } from 'ant-design-vue'
import { apiClient } from '@/api/client'
import ApplicationExcelColumnModal from './ApplicationExcelColumnModal.vue'

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
// 백엔드 recruit.pdf.bulk-max-count 와 같은 값. 서버가 최종 검증하고 여기서는 UX 안내만 한다.
const PDF_BULK_MAX_COUNT = 20

const selectRowKeys = ref<number[]>([]);

const rowSelection = {
  selectedRowKeys: selectRowKeys, 
  onChange: (keys: number[]) => {
    selectRowKeys.value = keys;
  },
}
const statusLabelMap: Record<string, string> = {
  DRAFT: '임시저장',
  SUBMITTED: '제출 완료',
  WITHDRAWN: '지원 철회',
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
  { value: '', label: '전체' },
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
  // 검색 조건이 없으면 임시저장·철회 지원서도 함께 조회되므로 상태를 보여 준다.
  { title: '지원상태', dataIndex: 'status', key: 'status' },
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
  { title: '졸업년월', dataIndex: 'finalGraduationDate', key: 'finalGraduationDate' },
  { title: '최종제출일시', dataIndex: 'submittedAt', key: 'submittedAt' },
  { title: '경력기술서', dataIndex: 'careerDescriptionDownloadUrl', key: 'careerDescriptionDownloadUrl' },
]

const initializing = ref(true)
const refreshing = ref(false)
const loadFailed = ref(false)

const statusOptions = Object.entries(statusLabelMap).map(([value, label]) => ({ value, label }))

const stageTypeOptions = computed(() => {
  const options = Object.entries(stageTypeMap).map(([value, label]) => ({ value, label }))
  options.unshift({ value: '', label: '전체' })
  return options
})
const stageResultStatusOptions = computed(() => {
  const options = Object.entries(stageResultStatusMap).map(([value, label]) => ({ value, label }))
  options.unshift({ value: '', label: '전체' })
  return options
})

const jobPositions = ref<AdminJobPosition[]>([])

const jobPostings = ref<AdminJobPostingListItem[]>([])
const selectedJobPostingId = ref<number | null>(null)

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
// 근무지 검색은 공통코드 code 로 비교한다. 공고의 모집분야들이 제시한 후보를 코드 기준으로 중복 제거한다.
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

const changeJobPosting = async (jobPostingId: number): Promise<void> => {
  selectedJobPostingId.value = jobPostingId;
  Object.assign(searchRequest, initialSearchRequest);
  resetPaging();
  refreshing.value = true
  loadFailed.value = false
  try {
    const response = await adminJobPostingApi.getJobPosting(jobPostingId)
    // 응답이 오기 전에 다른 공고를 골랐으면 늦게 온 응답은 버린다.
    if (jobPostingId !== selectedJobPostingId.value) return
    const detail = response.data.data;
    jobPositions.value = detail.jobPositions;
  } catch (error) {
    if (jobPostingId !== selectedJobPostingId.value) return
    // 이전 공고의 지원분야·근무지 옵션이 남으면 그 값으로 검색해 항상 0건이 나온다.
    jobPositions.value = []
    loadFailed.value = true
    message.error(getApiErrorMessage(error, '지원분야를 불러오지 못했습니다.'))
  } finally {
    if (jobPostingId === selectedJobPostingId.value) {
      refreshing.value = false
    }
  }
}

const changeJobPosition = async (jobPositionId: number | null) => {
  searchRequest.jobPositionId = jobPositionId ?? undefined;
}

// 검색 조건만 초기값으로 되돌린다. 선택한 공고와 모집분야 목록은 유지하므로 재조회하지 않는다.
const refresh = (): void => {
  if (selectedJobPostingId.value === null || refreshing.value) return

  Object.assign(searchRequest, initialSearchRequest)
  resetPaging()
}

const resetPaging = (): void => {
  applications.value = []
  selectRowKeys.value = []
  pagination.current = 1
  pagination.total = 0
  saving.value = false
  appliedSearch.value = null
}

// 지원서 상세는 메인 프레임을 벗어나 새 탭으로 연다. 상대경로 window.open 은 현재 URL 기준으로
// 해석돼 라우트 변경에 취약하므로, router 가 계산한 절대 경로(href)를 쓴다.
const goApplication = (applicationId: number) => {
  const { href } = router.resolve({ name: 'AdminApplication', params: { applicationId } })
  window.open(href, '_blank', 'noopener')
}

// 전화번호 입력 input (숫자만 입력 가능하도록)
const onlyNumber = (e: KeyboardEvent) => {
    const allowKeys = ['Backspace', 'Delete', 'ArrowLeft', 'ArrowRight', 'Tab'];

    // 복사·붙여넣기·전체선택 같은 Ctrl/Cmd 조합은 막지 않는다. 붙여넣은 값은 onPhoneNumberInput 이 숫자만 남긴다.
    if (e.ctrlKey || e.metaKey) return;
    if (allowKeys.includes(e.key)) return;
    if (!/^\d$/.test(e.key)) e.preventDefault();
}

// '010-1234-5678' 처럼 붙여넣어도 숫자만 남긴다. maxlength 를 두면 하이픈 포함 값이 먼저 잘려 여기서 자른다.
const onPhoneNumberInput = (value: string) => {
  searchRequest.phoneNumber = value.replace(/\D/g, '').slice(0, 11)
}

const applications = ref<AdminApplicationSummaryResponse[]>([])

const initialSearchRequest: AdminApplicationSearchRequest = {
    jobPositionId:undefined,          // 지원 분야
    workLocation: undefined,          // 근무지
    birthDateTo: undefined,           // 생년월일 TO
    applicationType: undefined,       // 지원 구분 
    name: undefined,                  // 이름
    phoneNumber: undefined,           // 연락처
    stageResultStatus: undefined,     // 전형별 결과
    status:undefined,                 // 지원서 상태
    birthDateFrom:undefined,          // 생년월일 FROM 
    stageType:undefined,              // 비상연락처
    schoolName:undefined,             // 학교명
};

const searchRequest = reactive<AdminApplicationSearchRequest>({
  ...initialSearchRequest,
});

/** 마지막으로 조회에 성공한 공고·검색 조건. 엑셀은 입력란의 현재 값이 아니라 이 조건으로 받는다. */
const appliedSearch = ref<{ jobPostingId: number; request: AdminApplicationSearchRequest } | null>(null)

// 서버 페이징. a-table 의 current 는 1-based, 백엔드 page 는 0-based 라 호출 시점에 변환한다.
const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: false,
})

// 검색 버튼: 조건이 바뀌었으므로 항상 첫 페이지부터 다시 조회한다.
const save = async () => {
  await loadApplications(1)
}

// 페이지 이동 시 이전 페이지의 선택은 버린다. 화면에 보이지 않는 항목이 선택된 채로 남으면
// 일괄 다운로드에서 의도하지 않은 지원서가 함께 나갈 수 있다.
const handleTableChange = async (page: { current?: number }) => {
  await loadApplications(page.current ?? 1)
}

const loadApplications = async (page: number) => {
  applications.value = [];
  loading.value = true
  saving.value = true
  selectRowKeys.value = [];
  appliedSearch.value = null

  try {
    if (selectedJobPostingId.value) {
      const jobPostingId = selectedJobPostingId.value
      const request = { ...searchRequest }
      const response = await adminApplicationApi.getApplications(
        jobPostingId,
        request,
        page - 1,
        pagination.pageSize,
      );

      const pageResponse = response.data.data;
      applications.value = pageResponse.content;
      pagination.current = pageResponse.page + 1;
      pagination.total = pageResponse.totalElements;
      appliedSearch.value = { jobPostingId, request }
    }
  } catch (error) {
    message.error(getApiErrorMessage(error, '지원현황 조회에 실패했습니다.'))
  } finally {
    loading.value = false
  }
}

// 선택한 지원서를 zip 으로 받는다. 상한 초과는 서버가 400 으로 막지만, 화면에서도 먼저 안내한다.
const downloadingPdf = ref(false)
const downloadSelectedPdf = async () => {
  if (downloadingPdf.value || selectRowKeys.value.length === 0) return
  if (selectRowKeys.value.length > PDF_BULK_MAX_COUNT) {
    message.warning(`한 번에 최대 ${PDF_BULK_MAX_COUNT}건까지 다운로드할 수 있습니다. (선택 ${selectRowKeys.value.length}건)`)
    return
  }

  downloadingPdf.value = true
  try {
    const response = await adminApplicationApi.downloadApplicationPdfBulk([...selectRowKeys.value])
    saveBlobResponse(response, '지원서.zip')
  } catch (error) {
    message.error(await getBlobErrorMessage(error, '지원서 PDF를 내려받지 못했습니다.'))
  } finally {
    downloadingPdf.value = false
  }
}

/*
 * 인쇄: 선택한 지원서를 PDF 한 개로 받아 화면에 보이지 않는 iframe 에 불러온 뒤 바로 인쇄 대화상자를 연다.
 * 새 탭을 열지 않아 팝업 차단과 무관하다. iframe 과 blob 주소는 다음 인쇄나 화면을 떠날 때 정리한다
 * (대화상자가 닫히는 시점을 알 수 없어서 인쇄 직후에는 지우지 않는다).
 */
const printingPdf = ref(false)
let printFrame: HTMLIFrameElement | null = null
let printUrl: string | null = null

/*
 * 진행 표시: 서버가 PDF 를 다 만든 뒤 한 번에 보내므로 실제 %는 알 수 없다.
 * 단계(만드는 중 → 인쇄 창 여는 중)와 경과 시간만 보여 주고, 인쇄 대화상자가 뜰 때까지 유지한다.
 */
const PRINT_LOAD_TIMEOUT_MS = 20000
const printStep = ref<'building' | 'opening'>('building')
const printCount = ref(0)
const printElapsed = ref(0)
let printTicker: ReturnType<typeof setInterval> | undefined
let printLoadTimer: ReturnType<typeof setTimeout> | undefined

const startPrintProgress = (count: number) => {
  printCount.value = count
  printStep.value = 'building'
  printElapsed.value = 0
  printingPdf.value = true
  const startedAt = Date.now()
  printTicker = setInterval(() => {
    printElapsed.value = Math.floor((Date.now() - startedAt) / 1000)
  }, 1000)
}

const finishPrintProgress = () => {
  printingPdf.value = false
  if (printTicker) clearInterval(printTicker)
  if (printLoadTimer) clearTimeout(printLoadTimer)
  printTicker = undefined
  printLoadTimer = undefined
}

const clearPrintFrame = () => {
  finishPrintProgress()
  printFrame?.remove()
  printFrame = null
  if (printUrl) URL.revokeObjectURL(printUrl)
  printUrl = null
}

const printSelectedPdf = async () => {
  if (printingPdf.value || selectRowKeys.value.length === 0) return
  if (selectRowKeys.value.length > PDF_BULK_MAX_COUNT) {
    message.warning(`한 번에 최대 ${PDF_BULK_MAX_COUNT}건까지 인쇄할 수 있습니다. (선택 ${selectRowKeys.value.length}건)`)
    return
  }

  const ids = [...selectRowKeys.value]
  clearPrintFrame()
  startPrintProgress(ids.length)
  try {
    const response = await adminApplicationApi.printApplicationPdf(ids)
    printStep.value = 'opening'
    const url = URL.createObjectURL(new Blob([response.data], { type: 'application/pdf' }))
    const frame = document.createElement('iframe')
    // display:none 이면 브라우저가 PDF 를 그리지 않아 빈 인쇄가 될 수 있어 크기 0 으로 숨긴다.
    frame.setAttribute('style', 'visibility: hidden; position: fixed; right: 0; bottom: 0; width: 0; height: 0; border: 0')
    frame.onload = () => {
      // 진행 창을 먼저 닫는다. print() 는 대화상자가 닫힐 때까지 화면을 멈추게 할 수 있다.
      finishPrintProgress()
      setTimeout(() => {
        try {
          frame.contentWindow?.focus()
          frame.contentWindow?.print()
        } catch {
          message.warning('이 브라우저에서는 바로 인쇄할 수 없습니다. PDF 다운로드 후 인쇄해 주세요.')
        }
      }, 100)
    }
    printLoadTimer = setTimeout(() => {
      finishPrintProgress()
      message.warning('인쇄 창을 열지 못했습니다. PDF 다운로드 후 인쇄해 주세요.')
    }, PRINT_LOAD_TIMEOUT_MS)
    frame.src = url
    document.body.appendChild(frame)
    printFrame = frame
    printUrl = url
  } catch (error) {
    finishPrintProgress()
    message.error(await getBlobErrorMessage(error, '인쇄할 지원서를 만들지 못했습니다.'))
  }
}

onBeforeUnmount(clearPrintFrame)

// 엑셀 버튼은 항목 선택 모달을 연다. 모달에서 고른 컬럼으로, 마지막으로 조회한 조건 그대로 받는다
// (검색 없이 입력란만 바꿔도 조건이 달라지지 않아 보이는 목록과 일치한다). 조회 전에는 버튼을 막는다.
const excelModalOpen = ref(false)
const downloadingExcel = ref(false)
const downloadExcel = async (columns: string[]) => {
  const applied = appliedSearch.value
  if (downloadingExcel.value || applied === null) return

  downloadingExcel.value = true
  try {
    const response = await adminApplicationApi.downloadApplicationsExcel(applied.jobPostingId, applied.request, columns)
    saveBlobResponse(response, '지원현황.xlsx')
    excelModalOpen.value = false
  } catch (error) {
    message.error(await getBlobErrorMessage(error, '지원현황 엑셀을 내려받지 못했습니다.'))
  } finally {
    downloadingExcel.value = false
  }
}

// 인사팀 양식 엑셀. 항목 선택 없이 마지막으로 조회한 조건 그대로 받는다.
const downloadingHrExcel = ref(false)
const downloadHrExcel = async () => {
  const applied = appliedSearch.value
  if (downloadingHrExcel.value || applied === null) return

  downloadingHrExcel.value = true
  try {
    const response = await adminApplicationApi.downloadApplicationsHrExcel(applied.jobPostingId, applied.request)
    saveBlobResponse(response, '지원현황_인사양식.xlsx')
  } catch (error) {
    message.error(await getBlobErrorMessage(error, '인사 양식 엑셀을 내려받지 못했습니다.'))
  } finally {
    downloadingHrExcel.value = false
  }
}

const CAREER_DESCRIPTION_DOWNLOAD_TIMEOUT_MS = 60_000 // 기본 10초로는 큰 첨부가 느린 망에서 끊길 수 있다.

const careerDescriptionDownload = async (url: string) => {
  try {
    const response = await apiClient.get(url, {responseType: 'blob', timeout: CAREER_DESCRIPTION_DOWNLOAD_TIMEOUT_MS});

    saveBlobResponse(response, '경력기술서');
  } catch (error) {
    message.error(await getBlobErrorMessage(error, '경력기술서 다운로드에 실패했습니다.'))
  }
}

/* 기본 선택은 접수 중인 첫 공고다. 없으면 목록의 첫 공고로 떨어진다. */
const pickDefaultJobPosting = (postings: AdminJobPostingListItem[]): AdminJobPostingListItem | null => {
  return postings.find((posting) => posting.accepting) ?? postings[0] ?? null
}

onMounted(async () => {
  try {
    jobPostings.value = await getAllJobPostings()
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
            <col style="width: 12%;">  <col style="width: 38%;">
            <col style="width: 12%;">  <col style="width: 38%;">
          </colgroup>
          <tbody>
            <tr>
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
                  <a-input v-model:value="searchRequest.name" style="width: 200px" placeholder="예: 홍길동"/>
              </td>
            </tr>

            <tr>
              <th>전형별 결과</th> 
              <td>
                <div class="flex-div-area">
                  <a-select
                    v-model:value="searchRequest.stageType" style="width: 120px"
                    :options="stageTypeOptions" placeholder="전체" allow-clear
                  />
                  <a-select
                    v-model:value="searchRequest.stageResultStatus" style="width: 120px"
                    :options="stageResultStatusOptions" placeholder="전체" allow-clear
                  />
                </div>
              </td>
              <th>연락처</th>
              <td>
                <a-input :value="searchRequest.phoneNumber" style="width: 200px"
                  placeholder="예: 01012345678" @update:value="onPhoneNumberInput" @keydown="onlyNumber"
                />
              </td>
            </tr>

            <tr>
              <th>지원상태</th>
              <td colspan="3">
                <a-select v-model:value="searchRequest.status" style="width: 200px" placeholder="전체"
                  :options="statusOptions" allow-clear
                />
              </td>
            </tr>
          </tbody>
        </table>
        </div>
        
      <div class="form-actions">
        <a-button type="primary" @click="save">
          <SearchOutlined />검색
        </a-button>
        <a-button :disabled="selectedJobPostingId === null" @click="refresh">
          <ReloadOutlined />초기화
        </a-button>
      </div>
      </a-card>


      <a-card :bordered="false" class="form-card">
        <div class="button-area">
          <a-button :loading="printingPdf" :disabled="selectRowKeys.length === 0" @click="printSelectedPdf">
            <PrinterOutlined />인쇄
          </a-button>
          <a-button :disabled="appliedSearch === null" @click="excelModalOpen = true">
            <FileExcelOutlined />엑셀 다운로드
          </a-button>
          <a-button :loading="downloadingHrExcel" :disabled="appliedSearch === null" @click="downloadHrExcel">
            <FileExcelOutlined />인사 양식 다운로드
          </a-button>
          <a-button :loading="downloadingPdf" :disabled="selectRowKeys.length === 0" @click="downloadSelectedPdf">
            <FilePdfOutlined />PDF 다운로드
          </a-button>
          
          <span v-if="selectRowKeys.length" class="selected-count">{{ selectRowKeys.length }}건 선택</span>
        </div>
        <div>
          <a-table :columns="columns" :data-source="applications" :pagination="pagination"
            :row-selection="rowSelection" row-key="applicationId" @change="handleTableChange">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                {{ statusLabelMap[record.status] ?? record.status }}
              </template>
              <template v-else-if="column.key === 'finalEducationLevel'">
                {{ educationLevelMap[record.finalEducationLevel] ?? record.finalEducationLevel }}
              </template>
              <template v-else-if="column.key === 'finalGraduationDate'">
                {{ formatDate(record.finalGraduationDate, 'YYYY-MM') }}
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
            <template #emptyText>
              <a-empty class="empty-box" v-if="!saving" description="검색 버튼을 클릭하세요."/>
              <a-empty class="empty-box" v-else-if="!loading" description="조회 내역이 없습니다."/>
            </template>
          </a-table>

        </div>
      </a-card>

    </a-spin>

    <a-modal
      :open="printingPdf"
      :closable="false"
      :mask-closable="false"
      :keyboard="false"
      :footer="null"
      :width="420"
      centered
    >
      <div class="print-progress">
        <PrinterOutlined class="print-progress-icon" />
        <div class="print-progress-title">{{ printStep === 'building' ? '인쇄 준비 중' : '인쇄 창을 여는 중' }}</div>
        <p class="print-progress-text">
          <template v-if="printStep === 'building'">지원서 {{ printCount }}건을 인쇄용 PDF로 만드는 중입니다.</template>
          <template v-else>곧 인쇄 창이 열립니다.</template>
        </p>
        <div class="print-progress-bar"><i /></div>
        <div class="print-progress-meta">
          <span>{{ printStep === 'building' ? '1/2 PDF 만들기' : '2/2 인쇄 창 열기' }}</span>
          <span>{{ printElapsed }}초</span>
        </div>
      </div>
    </a-modal>

    <ApplicationExcelColumnModal
      v-model:open="excelModalOpen"
      :downloading="downloadingExcel"
      @download="downloadExcel"
    />
  </div>
</template>

<style scoped>
.print-progress {
  padding: 12px 4px 4px;
  text-align: center;
}
.print-progress-icon {
  font-size: 30px;
  color: var(--app-color-primary);
}
.print-progress-title {
  margin-top: 10px;
  font-size: 16px;
  font-weight: 700;
}
.print-progress-text {
  margin: 6px 0 16px;
  color: var(--app-text-secondary);
}
/* 실제 %를 알 수 없어 한 구간이 계속 흐르는 막대로 "진행 중"만 알린다. */
.print-progress-bar {
  position: relative;
  height: 6px;
  border-radius: 3px;
  background: var(--app-bg-selected);
  overflow: hidden;
}
.print-progress-bar i {
  position: absolute;
  top: 0;
  left: -40%;
  width: 40%;
  height: 100%;
  border-radius: 3px;
  background: var(--app-color-primary);
  animation: print-progress-slide 1.2s ease-in-out infinite;
}
@keyframes print-progress-slide {
  to {
    left: 100%;
  }
}
.print-progress-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 12px;
  color: var(--app-text-muted);
  font-variant-numeric: tabular-nums;
}

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
.form-actions {
  margin-top: 12px;
  display: flex;
  justify-content: center;
  gap: 8px;
}
.selected-count {
  margin-left: 4px;
  font-size: 13px;
  color: #595959;
}
.button-area {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 12px;
  align-items: center;
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
  /* min-width: 280px; */
  width: 100%;
}

.flex-div-area{
  display: flex;
  gap: 8px;
  align-items: center;
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
  /* font-size: 13.5px; */
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

.careerButton{
  font-size: 10px;
  padding: 0px 8px;
  height: 30px;
}

:deep(.applicationId-link) {
  font-weight: 500;
  text-decoration: underline;
}
</style>
