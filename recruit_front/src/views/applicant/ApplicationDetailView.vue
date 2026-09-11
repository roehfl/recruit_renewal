<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { LeftOutlined, LinkOutlined, RightCircleOutlined } from '@ant-design/icons-vue'
import { formatDate, getDDay, isDeadlineSoon } from '@/common/dateUtil'
import { copyText } from '@/common/clipboardUtil'

import { apiClient } from '@/api/client'
import type { ApiResponse } from '@/types/api'
import type { JobPostingDetail, JobPostingImage, MyJobPostingListItem, MyJobPostingDetailListItem } from '@/types/jobPosting'
import { boardApi } from '@/api/boardApi'
import HtmlView from '@/views/common/htmlView.vue'
import JobPostingImageStack from '@/components/jobPosting/JobPostingImageStack.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)

// 공고 Detail
const jobPostDetail = ref<JobPostingDetail | null>(null)
// 공고 이미지 목록
const jobPostImages = ref<JobPostingImage[]>([])

const jobPostingId = Number(route.params.jobPostingId)

const fetchPostingImage = (imageId: number) =>
  boardApi.fetchJobPostingImageBlob(jobPostingId, imageId).then((res) => res.data)

async function loadJobPostingDetail() {
  loading.value = true
  try {
    const result = await boardApi.fetchJobPostingDetail(jobPostingId)

    jobPostImages.value = result.data.data.images ?? []
    jobPostDetail.value = result.data.data
  } finally {
    loading.value = false
  }
}

// 공고 유형(JobPostingType): 신입 = PUBLIC_RECRUITMENT, 경력 = EXPERIENCED_RECRUITMENT
const postingTypeMap: Record<string, string> = {
  PUBLIC_RECRUITMENT: '신입',
  EXPERIENCED_RECRUITMENT: '경력',
}

// 지원하기는 접수중인 공고에서만 노출한다.
const isAccepting = computed(() => jobPostDetail.value?.receptionStatus === 'ACCEPTING')

// 공고 URL 클립보드 복사
async function copyPostingUrl(): Promise<void> {
  try {
    await copyText(window.location.href)
    message.success('공고 링크가 복사되었습니다.')
  } catch {
    message.warning('클립보드 복사에 실패했습니다. 주소창의 URL을 직접 복사하세요.')
  }
}

// 지원서 양식 페이지로 이동
function goApplicationFormPage(applicationId: number): void {
  router.push(`/applicant/${applicationId}/form`)
}

/*
 * 지원하기. 모집분야 선택은 지원서 작성 화면에서 한다.
 * 이미 지원서가 있으면 상태별 안내 후 해당 지원서로, 없으면 지원서 작성 시작 화면으로 이동한다.
 */
async function apply(): Promise<void> {
  const myApplication = await isApplication()

  if (!myApplication) {
    await router.push(`/applicant/${jobPostingId}/apply`)
    return
  }

  const notice = existingApplicationNotice(myApplication.applicationStatus)
  Modal.confirm({
    // 경고가 아닌 안내라 아이콘을 두지 않는다. 아이콘이 있으면 본문이 34px 들여써져 좌측이 비어 보인다.
    icon: null,
    title: notice.title,
    content: notice.content,
    okText: notice.okText,
    cancelText: '취소',
    onOk() {
      goApplicationFormPage(myApplication.applicationId)
    },
  })
}

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
  const result = await apiClient.get<ApiResponse<MyJobPostingListItem>>(`/applications/me`)
  return result.data.data.content.find((item) => item.jobPostingId === jobPostingId)
}

onMounted(async () => {
  await loadJobPostingDetail()
})
</script>

<template>
  <section class="posting-detail-page">
    <div class="page-inner">
      <router-link to="/applicant/recruits" class="back-link">
        <LeftOutlined />
        채용공고 목록
      </router-link>

      <a-spin :spinning="loading">
        <header class="posting-header">
          <div class="posting-heading">
            <div class="title-row">
              <h1 class="posting-title">{{ jobPostDetail?.title }}</h1>
              <button type="button" class="link-button" aria-label="공고 링크 복사" @click="copyPostingUrl">
                <LinkOutlined />
              </button>
            </div>

            <div v-if="jobPostDetail" class="posting-meta">
              <span v-if="postingTypeMap[jobPostDetail.postingType]" class="type-badge">
                {{ postingTypeMap[jobPostDetail.postingType] }}
              </span>
              <span v-if="jobPostDetail.receptionStatus === 'UPCOMING'" class="status-badge">접수예정</span>
              <span :class="['dday-badge', { soon: isDeadlineSoon(jobPostDetail.receptionEndDateTime) }]">
                {{ getDDay(jobPostDetail.receptionEndDateTime) }}
              </span>
              <span class="posting-period">
                {{ formatDate(jobPostDetail.receptionStartDateTime, 'YYYY.MM.DD') }} ~ {{ formatDate(jobPostDetail.receptionEndDateTime, 'YYYY.MM.DD') }}
                <strong>{{ formatDate(jobPostDetail.receptionEndDateTime, 'HH:mm') }}</strong>
              </span>
            </div>
          </div>

          <button v-if="isAccepting" type="button" class="apply-button" @click="apply">
            지원하기
            <RightCircleOutlined />
          </button>
        </header>

        <div class="header-divider" />

        <div class="posting-content">
          <JobPostingImageStack v-if="jobPostImages.length > 0" :images="jobPostImages" :fetch-image="fetchPostingImage" />
          <HtmlView v-else-if="jobPostDetail?.contentHtml" :content="jobPostDetail.contentHtml" />
          <a-empty v-else-if="!loading" class="empty-box" description="지원서 상세 정보가 없습니다." />
        </div>

        <div class="bottom-actions">
          <router-link to="/applicant/recruits" class="list-button">목록</router-link>
          <button v-if="isAccepting" type="button" class="apply-button compact" @click="apply">지원하기</button>
        </div>
      </a-spin>
    </div>
  </section>
</template>

<style scoped lang="scss">
.posting-detail-page {
  width: 100%;
  background: #ffffff;
  color: var(--tap-text);
}

.page-inner {
  max-width: var(--app-frame-width);
  margin: 0 auto;
  padding: 60px var(--app-frame-padding-x) 96px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 20px;
  color: var(--app-text-secondary);
  font-size: 14px;
  font-weight: 500;
}

.back-link:hover {
  color: var(--app-color-primary);
}

.posting-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 32px;
}

.posting-heading {
  display: flex;
  flex: 1 1 420px;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.posting-title {
  margin: 0;
  color: var(--tap-text);
  font-size: 30px;
  font-weight: 800;
  line-height: 1.3;
  letter-spacing: -0.04em;
  text-wrap: pretty;
}

.link-button {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--app-text-secondary);
  font-size: 18px;
  cursor: pointer;
}

.link-button:hover {
  background: #f4f8f0;
  color: var(--app-color-primary);
}

.posting-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.type-badge,
.status-badge,
.dday-badge {
  padding: 6px 11px;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1;
}

.type-badge {
  background: #f4f8f0;
  color: var(--app-color-primary);
  font-weight: 600;
}

.status-badge {
  background: #fdf3e7;
  color: var(--app-color-warning);
  font-weight: 600;
}

.dday-badge {
  background: #f5f7fa;
  color: var(--app-text-secondary);
  font-weight: 700;
}

.dday-badge.soon {
  background: #fdf3e7;
  color: var(--app-color-warning);
}

.posting-period {
  margin-left: 4px;
  color: var(--app-text-secondary);
  font-size: 15px;
  letter-spacing: -0.01em;
}

.posting-period strong {
  color: var(--tap-text);
  font-weight: 600;
}

.apply-button {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 52px;
  padding: 0 26px;
  border: 0;
  border-radius: 10px;
  background: var(--app-color-primary);
  color: #ffffff;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.02em;
  box-shadow: 0 8px 20px rgba(15, 71, 38, 0.18);
  cursor: pointer;
  transition: background 0.15s ease;
}

.apply-button:hover {
  background: var(--app-color-primary-hover);
}

.apply-button :deep(.anticon) {
  font-size: 19px;
}

.apply-button.compact {
  height: 50px;
  padding: 0 30px;
  font-size: 15px;
  box-shadow: none;
}

.header-divider {
  height: 1px;
  margin-top: 26px;
  background: var(--tap-text);
}

.posting-content {
  max-width: 860px;
  margin: 0 auto;
  padding-top: 36px;
}

.empty-box {
  padding: 60px 0;
}

.bottom-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
  padding-top: 44px;
}

.list-button {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 50px;
  padding: 0 28px;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  color: var(--tap-text);
  font-size: 15px;
  font-weight: 600;
}

.list-button:hover {
  border-color: var(--app-color-primary);
  background: #f8faf6;
  color: var(--app-color-primary);
}

@media (max-width: 768px) {
  .page-inner {
    padding: 32px 16px 64px;
  }

  .posting-title {
    font-size: 24px;
  }

  .posting-header > .apply-button {
    width: 100%;
  }
}
</style>
