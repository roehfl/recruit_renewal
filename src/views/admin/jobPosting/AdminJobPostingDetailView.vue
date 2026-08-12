<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import { adminJobPostingApi } from '@/api/adminJobPostingApi'
import { getApiErrorMessage } from '@/api/apiError'
import JobPostingImageStack from '@/components/jobPosting/JobPostingImageStack.vue'
import type { AdminJobPostingDetail } from '@/types/jobPosting'

const route = useRoute()
const router = useRouter()
const postingId = computed(() => Number(route.params.id))

const loading = ref(false)
const acting = ref(false)
const detail = ref<AdminJobPostingDetail | null>(null)

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
const postingTypeLabelMap: Record<string, string> = {
  PUBLIC_RECRUITMENT: '공개채용',
  EXPERIENCED_RECRUITMENT: '경력채용',
  INTERN_RECRUITMENT: '인턴채용',
  ROLLING_RECRUITMENT: '수시채용',
}

const formatDateTime = (value: string | null) => (value ? value.replace('T', ' ').slice(0, 16) : '-')

const fetchImage = (imageId: number) =>
  adminJobPostingApi.fetchImageBlob(postingId.value, imageId).then((response) => response.data)

const loadDetail = async () => {
  loading.value = true
  try {
    const response = await adminJobPostingApi.getJobPosting(postingId.value)
    detail.value = response.data.data
  } catch (error) {
    message.error(getApiErrorMessage(error, '공고 정보를 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

const publish = () => {
  Modal.confirm({
    title: '공고를 발행할까요?',
    content: '발행하면 지원자 화면에 노출됩니다. 미리보기 검수를 완료했는지 확인해 주세요.',
    okText: '발행',
    cancelText: '취소',
    async onOk() {
      acting.value = true
      try {
        await adminJobPostingApi.publishJobPosting(postingId.value)
        message.success('공고가 발행되었습니다.')
        await loadDetail()
      } catch (error) {
        message.error(getApiErrorMessage(error, '발행에 실패했습니다.'))
      } finally {
        acting.value = false
      }
    },
  })
}

const close = () => {
  Modal.confirm({
    title: '공고를 마감할까요?',
    content: '마감한 공고는 수정할 수 없습니다.',
    okText: '마감',
    cancelText: '취소',
    async onOk() {
      acting.value = true
      try {
        await adminJobPostingApi.closeJobPosting(postingId.value)
        message.success('공고가 마감되었습니다.')
        await loadDetail()
      } catch (error) {
        message.error(getApiErrorMessage(error, '마감에 실패했습니다.'))
      } finally {
        acting.value = false
      }
    },
  })
}

const goToEdit = () => {
  void router.push({ name: 'AdminJobPostingEdit', params: { id: postingId.value } })
}

onMounted(loadDetail)
</script>

<template>
  <div class="job-posting-detail">
    <a-spin :spinning="loading">
      <template v-if="detail">
        <header class="page-header">
          <div>
            <h2 class="page-title">
              {{ detail.title }}
              <a-tag :color="statusColorMap[detail.status]">{{ statusLabelMap[detail.status] ?? detail.status }}</a-tag>
            </h2>
            <p class="page-description">아래 미리보기는 지원자에게 보이는 본문과 동일합니다.</p>
          </div>
          <div class="header-actions">
            <a-button @click="router.push({ name: 'AdminJobPostingList' })">목록</a-button>
            <a-button v-if="detail.status !== 'CLOSED'" @click="goToEdit">수정</a-button>
            <a-button v-if="detail.status === 'DRAFT'" type="primary" :loading="acting" @click="publish">발행</a-button>
            <a-button v-if="detail.status === 'PUBLISHED'" danger :loading="acting" @click="close">마감</a-button>
          </div>
        </header>

        <a-descriptions bordered :column="2" size="small" class="info-block">
          <a-descriptions-item label="공고 유형">{{ postingTypeLabelMap[detail.postingType] ?? detail.postingType }}</a-descriptions-item>
          <a-descriptions-item label="표시 순서">{{ detail.displayOrder }}</a-descriptions-item>
          <a-descriptions-item label="접수 기간">
            {{ formatDateTime(detail.receptionStartDateTime) }} ~ {{ formatDateTime(detail.receptionEndDateTime) }}
          </a-descriptions-item>
          <a-descriptions-item label="노출 기간">
            {{ formatDateTime(detail.displayStartDateTime) }} ~ {{ formatDateTime(detail.displayEndDateTime) }}
          </a-descriptions-item>
          <a-descriptions-item label="노출 여부">{{ detail.visible ? '노출' : '비노출' }}</a-descriptions-item>
          <a-descriptions-item label="상단 고정">{{ detail.pinned ? '고정' : '-' }}</a-descriptions-item>
          <a-descriptions-item label="발행일시">{{ formatDateTime(detail.publishedAt) }}</a-descriptions-item>
          <a-descriptions-item label="마감일시">{{ formatDateTime(detail.closedAt) }}</a-descriptions-item>
        </a-descriptions>

        <a-card title="본문 미리보기 (지원자 화면과 동일)" :bordered="false" class="preview-card">
          <JobPostingImageStack v-if="detail.images.length > 0" :images="detail.images" :fetch-image="fetchImage" />
          <p v-else class="state-message">
            등록된 이미지가 없습니다. 발행하려면 이미지를 최소 1장 등록해 주세요.
          </p>
        </a-card>
      </template>
    </a-spin>
  </div>
</template>

<style scoped>
.job-posting-detail {
  padding: 24px;
  max-width: 1080px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.page-title {
  margin: 0 0 4px;
}
.page-description {
  margin: 0;
  color: #888;
}
.header-actions {
  display: flex;
  gap: 8px;
}
.info-block {
  margin-bottom: 16px;
}
.state-message {
  color: #999;
}
</style>
