<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { formatDate, getDDay } from '@/common/dateUtil'


import { apiClient } from '@/api/client'
import type { ApiResponse } from '@/types/api'
import type { MyJobPostingListItem, MyJobPostingDetailListItem } from '@/types/jobPosting'
import { boardApi } from '@/api/boardApi'
import HtmlView from '@/views/common/htmlView.vue'
import JobPostingImageStack from '@/components/jobPosting/JobPostingImageStack.vue'
import { LinkOutlined } from '@ant-design/icons-vue'
import type { JobPostingImage } from '@/types/jobPosting'


const route = useRoute();
const router = useRouter();

const loading = ref(false)
const submitting = ref(false)

// 공고 Detail
const jobPostDetail = ref();
// 공고 HTML
const jobPostContentHtml = ref();
// 공고 이미지 목록
const jobPostImages = ref<JobPostingImage[]>([])

const fetchPostingImage = (imageId: number) =>
  boardApi.fetchJobPostingImageBlob(Number(route.params.jobPostingId), imageId).then((res) => res.data)

// const jobPostingId = computed<number | null>(() => {
//   const raw = route.params.jobPostingId //?? route.query.jobPostingId
//   const value = Array.isArray(raw) ? raw[0] : raw
//   const parsed = Number(value)

//   return Number.isFinite(parsed) && parsed > 0 ? parsed : null
// })

async function loadJobPostingDetail() {
  loading.value = true
  try {
    const result = await boardApi.fetchJobPostingDetail(Number(route.params.jobPostingId))

    jobPostContentHtml.value = result.data.data.contentHtml;
    jobPostImages.value = result.data.data.images ?? []
    jobPostDetail.value = result.data.data;

  } finally {
    loading.value = false
  }
}

// 공고 유형
const postingTypeMap: Record<string, string> = {
  PUBLIC_RECRUITMENT: '공개', 
  ROLLING_RECRUITMENT: '수시',
}

// 공고 상태 
const recruitStatusTypeMap: Record<string, string> = {
  ACCEPTING: '진행중', 
  CLOSED: '마감', 
  UPCOMING: '예정', 
}
// 공고 모집분야
const positionOptions = computed(() => {
  return jobPostDetail.value?.jobPositions.map( (item: { positionName: string; id: number; }) => ({
    label: item.positionName,
    value: item.id
  }))
})

// 선택한 모집분야의 후보 근무지. 후보 개수가 곧 화면 분기다(0=미표시, 1=고정, N=선택).
const workLocationOptions = computed<{ label: string; value: string }[]>(() => {
  const positionId = selectedPosition.value?.value
  if (!positionId) {
    return []
  }
  const position = jobPostDetail.value?.jobPositions
    ?.find((item: { id: number }) => item.id === positionId)
  return (position?.workLocations ?? []).map((it: { code: string; name: string }) => ({
    label: it.name,
    value: it.code
  }))
})


// 공고 URL 클립보드 복사
async function copyPostingUrl(): Promise<void> {
  const url = window.location.href

  try {
    // clipboard API 는 보안 컨텍스트(https/localhost)에서만 제공되므로 없으면 execCommand 로 대체한다.
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(url)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = url
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    message.success('공고 URL이 복사되었습니다.')
  } catch {
    message.warning('클립보드 복사에 실패했습니다. 주소창의 URL을 직접 복사하세요.')
  }
}

// 목록 페이지로 이동 
function goRecruitPage(): void {
  // router.back();
  router.push('/applicant/recruits');
}

// 지원서 양식 페이지로 이동 
function goApplicationFormPage(applicationId: unknown): void {
  router.push(`/applicant/${applicationId}/form`);
}

// 선택한 모집분야
const selectedPosition = ref< {label: string; value: number} | undefined >()

// 선택한 근무지 코드
const selectedWorkLocation = ref<string | undefined>()

// 모집분야를 바꾸면 근무지 선택을 초기화한다. 후보가 1개뿐이면 자동 선택한다.
watch(selectedPosition, () => {
  const options = workLocationOptions.value
  selectedWorkLocation.value = options.length === 1 ? options[0]!.value : undefined
})

// 모집분야 선택 포커스
const positionSelectRef = ref();

// 확인 모달에 보여줄 근무지 표시명
const selectedWorkLocationLabel = computed(() =>
  workLocationOptions.value.find((it) => it.value === selectedWorkLocation.value)?.label ?? ''
)

// 지원하기 버튼 클릭 시 
function confirmSubmit(): void {
  // 상태가 모집 예정일 경우 
  if (jobPostDetail.value.receptionStatus === 'UPCOMING') {
    message.error('모집 예정 공고입니다.');
    return;
  }
  // 선택 모집분야 : selectedPosition
  // label : 모집분야명
  // value : 모집분야 Id
  if (!selectedPosition.value ) {
    message.warning('모집분야를 선택해주세요.');
    positionSelectRef.value?.focus();

    return;
  }

  // 후보 근무지가 있는 모집분야는 근무지 선택이 필수다.
  if (workLocationOptions.value.length > 0 && !selectedWorkLocation.value) {
    message.warning('근무지를 선택해주세요.');
    return;
  }

  // 지원한 공고일 경우
  isApplication().then((Response) => {
  // isApply().then((Response) => {
    if (Response) {
      const notice = existingApplicationNotice(Response.applicationStatus)
      Modal.confirm({
        title: notice.title,
        content: notice.content,
        okText: notice.okText,
        cancelText: '취소',
        async onOk() {
          await goApplicationFormPage(Response.applicationId);
        },
      })
      return;
    }
    else {
      Modal.confirm({
        title: '공고 지원',
        content: selectedWorkLocation.value
          ? `지원 모집분야는 '${selectedPosition.value?.label}', 근무지는 '${selectedWorkLocationLabel.value}' 입니다. 지원하시겠습니까?`
          : `지원 모집분야는 '${selectedPosition.value?.label}' 입니다. 지원하시겠습니까?`,
        okText: '지원하기',
        cancelText: '취소',
        async onOk() {
          await submitApplication()
        },
      })
    }
  })

  
}

async function submitApplication(): Promise<void> {
  const id = jobPostDetail.value.id;
  const positionId = selectedPosition.value?.value;

  if (!id) {
    message.error('지원서 식별자가 올바르지 않습니다.')
    return
  }

  submitting.value = true

  try {
    

    const response = await apiClient.post<ApiResponse<unknown>>(
      `/applications`, 
      {
        jobPostingId : id, 
        jobPositionId: positionId,
        workLocationCode: selectedWorkLocation.value ?? null,
      })

    if (!response.data.success) {
      throw new Error(response.data.message || '공고 지원에 실패했습니다.')
    }

    await goApplicationFormPage(response.data.data);

  } catch (error) {
    message.error(getErrorMessage(error, '공고 지원에 실패했습니다.'))
  } finally {
    submitting.value = false
  }
}


// async function isApply(): Promise<boolean> {
//   const id = jobPostDetail.value.id;
//   const result = await apiClient.get<ApiResponse<MyJobPostingListItem>>(`/applications/me`);
//   const response = result.data.data;

//   if (!response.totalElements) return false;

//   return response.content.some((item) => item.jobPostingId === id);
  
// }

/*
 * 기지원 이력 안내 문구. 지원서 상태에 따라 문구가 달라진다.
 * 철회분은 동일 공고 재지원이 백엔드에서 막히므로(중복 검사는 상태를 보지 않는다) 그 사실을 함께 알린다.
 */
function existingApplicationNotice(
  applicationStatus: MyJobPostingDetailListItem['applicationStatus'],
): { title: string; content: string; okText: string } {
  if (applicationStatus === 'SUBMITTED') {
    return {
      title: '제출 완료된 공고',
      content: '해당 공고에 제출을 완료한 지원서가 존재합니다. 제출한 지원서를 확인하시겠습니까?',
      okText: '지원서 확인',
    }
  }

  if (applicationStatus === 'WITHDRAWN') {
    return {
      title: '철회한 공고',
      content: '해당 공고에서 철회한 지원서가 존재하여 다시 지원할 수 없습니다. 철회한 지원서를 확인하시겠습니까?',
      okText: '지원서 확인',
    }
  }

  return {
    title: '지원중인 공고',
    content: '해당 공고에 작성중인 지원서가 존재합니다. 작성중인 지원서로 이동하시겠습니까?',
    okText: '지원서 이동',
  }
}

async function isApplication(): Promise<MyJobPostingDetailListItem | undefined> {
  const id = jobPostDetail.value.id;
  const result = await apiClient.get<ApiResponse<MyJobPostingListItem>>(`/applications/me`);
  const response = result.data.data;
  const myApplication = response.content.find((item) => item.jobPostingId === id);

  if (myApplication) return myApplication;
  return ;
  
}

function getErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error) {
    return error.message
  }

  if (typeof error === 'object' && error !== null && 'response' in error) {
    const responseError = error as { response?: { data?: { message?: string } } }
    return responseError.response?.data?.message ?? fallback
  }

  return fallback
}

onMounted(async () => {
  await loadJobPostingDetail()
})
</script>

<template>
  <section class="application-form-page">
    <div class="page-inner">
      <a-card class="application-header-card" :bordered="false">
        <div class="application-header">
          <div>
            <p class="eyebrow">Job Posting Detail</p>
            <h1>채용 공고</h1>
          </div>

          <a-space wrap>
            <a-form-item v-if="jobPostDetail?.receptionStatus === 'ACCEPTING'" label="모집분야" required>
              <a-select
                ref="positionSelectRef"
                v-model:value="selectedPosition"
                style="width: 300px"
                :options="positionOptions" 
                label-in-value
                placeholder="모집분야를 선택해주세요"
              />
            </a-form-item>
            <a-form-item
              v-if="jobPostDetail?.receptionStatus === 'ACCEPTING' && workLocationOptions.length > 0"
              label="근무지"
              required
            >
              <a-select
                v-model:value="selectedWorkLocation"
                style="width: 200px"
                :options="workLocationOptions"
                :disabled="workLocationOptions.length === 1"
                placeholder="근무지를 선택해주세요"
              />
            </a-form-item>
            <a-form-item>
              <a-space>
                <a-button v-if="jobPostDetail?.receptionStatus === 'ACCEPTING'" type="primary" @click="confirmSubmit()">지원하기</a-button>
                <a-button @click="copyPostingUrl">
                  <LinkOutlined /> URL 복사
                </a-button>
                <a-button @click="goRecruitPage">목록</a-button>
              </a-space>
            </a-form-item>
          </a-space>
        </div>
        <div  class="steps-card" >
          <a-descriptions bordered :column="2">
          <a-descriptions-item label="공고명" span="2">{{ jobPostDetail?.title }}</a-descriptions-item>
          <a-descriptions-item label="공고유형">{{ postingTypeMap[jobPostDetail?.postingType] }}</a-descriptions-item>
          <a-descriptions-item label="상태">
            <a-tag v-if="jobPostDetail?.receptionStatus === 'ACCEPTING'" color="green">{{ recruitStatusTypeMap[jobPostDetail?.receptionStatus]}}</a-tag>
            <a-tag v-else-if="jobPostDetail?.receptionStatus === 'UPCOMING'" color="orange">{{ recruitStatusTypeMap[jobPostDetail?.receptionStatus]}}</a-tag>
            <a-tag v-else color="default">{{ recruitStatusTypeMap[jobPostDetail?.receptionStatus]}}</a-tag>
            <!-- <a-tag color="recruitStatusTypeMap[jobPostDetail?.receptionStatus]?.color">
              {{ recruitStatusTypeMap[jobPostDetail?.receptionStatus]?.text }}
            </a-tag> -->
          </a-descriptions-item>
          <a-descriptions-item label="접수기간" span="2">
            {{ formatDate(jobPostDetail?.receptionStartDateTime, 'YYYY-MM-DD HH:mm') }} ~ {{ formatDate(jobPostDetail?.receptionEndDateTime, 'YYYY-MM-DD HH:mm') }}
            <span v-if="jobPostDetail?.receptionEndDateTime" class="dday">{{ getDDay(jobPostDetail.receptionEndDateTime) }}</span>
          </a-descriptions-item>
          </a-descriptions>
        </div>
      </a-card>

      <a-spin :spinning="loading">
        <template v-if="jobPostImages.length > 0">
          <a-card class="form-content-card" :bordered="false">
            <JobPostingImageStack :images="jobPostImages" :fetch-image="fetchPostingImage" />

            <div class="posting-bottom-actions">
              <a-button v-if="jobPostDetail?.receptionStatus === 'ACCEPTING'" type="primary" size="large" @click="confirmSubmit()">지원하기</a-button>
              <a-button size="large" @click="goRecruitPage">목록</a-button>
            </div>
          </a-card>
        </template>

        <template v-else-if="jobPostContentHtml">
          <a-card class="form-content-card" :bordered="false">
            <HtmlView :content="jobPostContentHtml" />
            <!-- <div v-html="jobPostContentHtml"></div> -->
          </a-card>
        </template>

        <a-empty v-else class="empty-box" description="지원서 상세 정보가 없습니다." />
      </a-spin>
    </div>
  </section>
</template>

<style scoped lang="scss">
.application-form-page {
  width: 100%;
  background: var(--app-bg-page);
}

.page-inner {
  max-width: var(--app-frame-width);
  margin: 0 auto;
  padding: 32px var(--app-frame-padding-x) 72px;
}

.application-header-card,
.steps-card,
.form-content-card {
  // border: 1px solid var(--app-border-default);
  border-radius: 10px;
  // box-shadow: var(--app-shadow-soft);
}

.dday {
  margin-left: 8px;
  color: var(--app-color-warning);
  font-size: 13px;
  font-weight: 600;
}

.posting-bottom-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 32px;
}

.application-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--app-color-primary-emerald);
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.application-header h1 {
  margin: 0;
  color: var(--app-text-primary);
  font-size: 30px;
  font-weight: 800;
  letter-spacing: -0.04em;
  line-height: 1.25;
}

.header-desc {
  margin: 10px 0 0;
  color: var(--app-text-secondary);
  font-size: 14px;
  line-height: 1.5;
}

.steps-card {
  margin-top: 18px;
}

.steps-card :deep(.ant-steps-item-title) {
  font-weight: 700;
}

.form-content-card {
  margin-top: 18px;
}

.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--app-border-default);
}

.page-heading h2 {
  margin: 0;
  color: var(--app-text-primary);
  font-size: 23px;
  font-weight: 800;
  letter-spacing: -0.035em;
}

.page-heading p {
  margin: 8px 0 0;
  color: var(--app-text-secondary);
  font-size: 14px;
}

.page-count {
  flex: 0 0 auto;
  min-width: 64px;
  padding: 6px 10px;
  border-radius: 999px;
  background: var(--app-bg-selected);
  color: var(--app-color-primary-olive-dark);
  font-size: 13px;
  font-weight: 800;
  text-align: center;
}

.section-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 20px;
}

.section-panel {
  overflow: hidden;
  border: 1px solid var(--app-border-soft);
  border-radius: 10px;
  background: #fff;
}

.section-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--app-border-subtle);
  background: #fbfcfa;
}

.section-panel-header h3 {
  margin: 0;
  color: var(--app-text-primary);
  font-size: 18px;
  font-weight: 800;
  letter-spacing: -0.03em;
}

.section-panel-header p {
  margin: 5px 0 0;
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.section-placeholder {
  padding: 22px 18px;
  background: #fff;
}

.placeholder-title {
  color: var(--app-text-primary);
  font-size: 15px;
  font-weight: 800;
}

.placeholder-desc {
  margin: 8px 0 0;
  color: var(--app-text-secondary);
  font-size: 14px;
  line-height: 1.55;
}

.placeholder-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 18px 0 0;
}

.placeholder-meta div {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--app-bg-muted);
}

.placeholder-meta dt {
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.placeholder-meta dd {
  margin: 4px 0 0;
  color: var(--app-text-primary);
  font-size: 13px;
  font-weight: 700;
}

.bottom-actions {
  position: sticky;
  bottom: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 18px;
  padding: 16px 18px;
  border: 1px solid var(--app-border-default);
  border-radius: 10px;
  background: rgb(255 255 255 / 94%);
  box-shadow: 0 -6px 18px rgb(15 23 42 / 6%);
  backdrop-filter: blur(8px);
}

.empty-box {
  margin-top: 40px;
  padding: 60px 0;
  border: 1px solid var(--app-border-default);
  border-radius: 10px;
  background: #fff;
}

@media (max-width: 768px) {
  .page-inner {
    padding: 24px 16px 56px;
  }

  .application-header,
  .page-heading,
  .bottom-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .application-header h1 {
    font-size: 25px;
  }

  .placeholder-meta {
    grid-template-columns: 1fr;
  }

  .bottom-actions :deep(.ant-space) {
    width: 100%;
  }

  .bottom-actions :deep(.ant-btn) {
    flex: 1;
  }
}
</style>
