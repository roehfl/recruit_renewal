<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { adminJobPostingApi } from '@/api/adminJobPostingApi'
import { getApiErrorMessage } from '@/api/apiError'
import type {
  AdminApplicationFormConfig,
  AdminJobPositionForm,
  AdminJobPostingSaveRequest,
} from '@/types/jobPosting'

const MAX_IMAGES = 10
const MAX_IMAGE_SIZE = 10 * 1024 * 1024
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

interface EditableImage {
  key: string
  id: number | null          // 기존 이미지면 서버 id, 신규면 null
  file: File | null          // 신규 파일
  altText: string
  originalAltText: string    // 수정 여부 판단용
  previewUrl: string
}

const route = useRoute()
const router = useRouter()
const editingId = computed(() => (route.params.id ? Number(route.params.id) : null))
const isEdit = computed(() => editingId.value !== null)

const loading = ref(false)
const saving = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const title = ref('')
const postingType = ref('PUBLIC_RECRUITMENT')
const summary = ref('')
const receptionStart = ref<string | null>(null)
const receptionEnd = ref<string | null>(null)
const displayStart = ref<string | null>(null)
const displayEnd = ref<string | null>(null)
const visible = ref(true)
const pinned = ref(false)
const displayOrder = ref(0)
const contentHtmlLegacy = ref<string | null>(null) // 수정 시 기존 값 보존용(화면 미노출)

const jobPositions = ref<AdminJobPositionForm[]>([
  { positionName: '', applicationType: 'NEW_GRADUATE_OR_EXPERIENCED', jobGroup: null, jobTitle: null, workLocation: null, employmentType: 'FULL_TIME', sortOrder: 0 },
])

const formConfig = ref<AdminApplicationFormConfig>({
  useEducation: true, requireEducation: null,
  useCareer: true, requireCareer: null,
  useCertificate: true, requireCertificate: null,
  useLanguage: true, requireLanguage: null,
  useMilitary: true, requireMilitary: null,
  useAward: true, requireAward: null,
  useGapPeriod: true, requireGapPeriod: null,
  useAttachment: false,
})

const images = ref<EditableImage[]>([])
const removedImageIds = ref<number[]>([])

const postingTypeOptions = [
  { value: 'PUBLIC_RECRUITMENT', label: '공개채용' },
  { value: 'EXPERIENCED_RECRUITMENT', label: '경력채용' },
  { value: 'INTERN_RECRUITMENT', label: '인턴채용' },
  { value: 'ROLLING_RECRUITMENT', label: '수시채용' },
]
const applicationTypeOptions = [
  { value: 'NEW_GRADUATE', label: '신입' },
  { value: 'EXPERIENCED', label: '경력' },
  { value: 'NEW_GRADUATE_OR_EXPERIENCED', label: '신입/경력' },
]
const employmentTypeOptions = [
  { value: 'FULL_TIME', label: '정규직' },
  { value: 'CONTRACT', label: '계약직' },
  { value: 'INTERN', label: '인턴' },
  { value: 'FREELANCE', label: '프리랜서' },
  { value: 'PART_TIME', label: '파트타임' },
  { value: 'ETC', label: '기타' },
]
const formSections: { useKey: keyof AdminApplicationFormConfig; requireKey: keyof AdminApplicationFormConfig; label: string }[] = [
  { useKey: 'useEducation', requireKey: 'requireEducation', label: '학력' },
  { useKey: 'useCareer', requireKey: 'requireCareer', label: '경력' },
  { useKey: 'useCertificate', requireKey: 'requireCertificate', label: '자격증' },
  { useKey: 'useLanguage', requireKey: 'requireLanguage', label: '어학' },
  { useKey: 'useMilitary', requireKey: 'requireMilitary', label: '병역' },
  { useKey: 'useAward', requireKey: 'requireAward', label: '포상' },
  { useKey: 'useGapPeriod', requireKey: 'requireGapPeriod', label: '공백기간' },
]

let imageKeySeq = 0

const openFilePicker = () => fileInput.value?.click()

const handleFilesSelected = (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  for (const file of files) {
    if (images.value.length >= MAX_IMAGES) {
      message.warning(`이미지는 최대 ${MAX_IMAGES}장까지 등록할 수 있습니다.`)
      break
    }
    if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
      message.warning(`${file.name}: jpg/png/webp 형식만 등록할 수 있습니다.`)
      continue
    }
    if (file.size > MAX_IMAGE_SIZE) {
      message.warning(`${file.name}: 장당 10MB 이하만 등록할 수 있습니다.`)
      continue
    }
    images.value.push({
      key: `new-${imageKeySeq++}`,
      id: null,
      file,
      altText: '',
      originalAltText: '',
      previewUrl: URL.createObjectURL(file),
    })
  }
}

const removeImage = (index: number) => {
  const [removed] = images.value.splice(index, 1)
  if (!removed) return
  if (removed.id !== null) {
    removedImageIds.value.push(removed.id)
  } else {
    URL.revokeObjectURL(removed.previewUrl)
  }
}

const moveImage = (index: number, delta: number) => {
  const target = index + delta
  if (target < 0 || target >= images.value.length) return
  const next = [...images.value]
  const current = next[index]
  const other = next[target]
  if (!current || !other) return
  next[index] = other
  next[target] = current
  images.value = next
}

const addPosition = () => {
  jobPositions.value.push({
    positionName: '',
    applicationType: 'NEW_GRADUATE_OR_EXPERIENCED',
    jobGroup: null,
    jobTitle: null,
    workLocation: null,
    employmentType: 'FULL_TIME',
    sortOrder: jobPositions.value.length,
  })
}

const removePosition = (index: number) => {
  if (jobPositions.value.length <= 1) {
    message.warning('모집분야는 최소 1개 이상이어야 합니다.')
    return
  }
  jobPositions.value.splice(index, 1)
}

const validate = (): string | null => {
  if (!title.value.trim()) return '공고 제목을 입력해 주세요.'
  if (!receptionStart.value || !receptionEnd.value) return '접수 기간을 입력해 주세요.'
  if (receptionEnd.value <= receptionStart.value) return '접수 종료일시는 시작일시 이후여야 합니다.'
  if (jobPositions.value.some((position) => !position.positionName.trim())) return '모집분야명을 입력해 주세요.'
  if (images.value.some((image) => !image.altText.trim())) return '모든 이미지에 대체 텍스트를 입력해 주세요.'
  return null
}

const buildSaveRequest = (): AdminJobPostingSaveRequest => ({
  title: title.value.trim(),
  postingType: postingType.value,
  summary: summary.value.trim() || null,
  receptionStartDateTime: receptionStart.value!,
  receptionEndDateTime: receptionEnd.value!,
  displayStartDateTime: displayStart.value,
  displayEndDateTime: displayEnd.value,
  visible: visible.value,
  pinned: pinned.value,
  displayOrder: displayOrder.value,
  jobPositions: jobPositions.value.map((position, index) => ({ ...position, sortOrder: index })),
  applicationFormConfig: formConfig.value,
})

const save = async () => {
  const errorMessage = validate()
  if (errorMessage) {
    message.warning(errorMessage)
    return
  }
  saving.value = true
  try {
    if (!isEdit.value) {
      const response = await adminJobPostingApi.createJobPosting(
        buildSaveRequest(),
        images.value.map((image) => ({ file: image.file!, altText: image.altText.trim() })),
      )
      message.success('공고가 등록되었습니다. 미리보기로 검수 후 발행해 주세요.')
      void router.push({ name: 'AdminJobPostingDetail', params: { id: response.data.data } })
      return
    }

    const id = editingId.value!
    await adminJobPostingApi.updateJobPosting(id, { ...buildSaveRequest(), contentHtml: contentHtmlLegacy.value })
    // 이미지 diff: 삭제 → 추가(id 확보) → altText 변경 → 전체 순서 재지정
    for (const removedId of removedImageIds.value) {
      await adminJobPostingApi.deleteImage(id, removedId)
    }
    for (const image of images.value) {
      if (image.id === null) {
        const response = await adminJobPostingApi.addImage(id, image.file!, image.altText.trim())
        image.id = response.data.data
      } else if (image.altText.trim() !== image.originalAltText) {
        await adminJobPostingApi.updateImageAltText(id, image.id, image.altText.trim())
      }
    }
    if (images.value.length > 0) {
      await adminJobPostingApi.reorderImages(id, images.value.map((image) => image.id!))
    }
    message.success('공고가 저장되었습니다.')
    void router.push({ name: 'AdminJobPostingDetail', params: { id } })
  } catch (error) {
    message.error(getApiErrorMessage(error, '공고 저장에 실패했습니다.'))
  } finally {
    saving.value = false
  }
}

const loadForEdit = async () => {
  if (!isEdit.value) return
  loading.value = true
  try {
    const response = await adminJobPostingApi.getJobPosting(editingId.value!)
    const detail = response.data.data
    title.value = detail.title
    postingType.value = detail.postingType
    summary.value = detail.summary ?? ''
    receptionStart.value = detail.receptionStartDateTime
    receptionEnd.value = detail.receptionEndDateTime
    displayStart.value = detail.displayStartDateTime
    displayEnd.value = detail.displayEndDateTime
    visible.value = detail.visible
    pinned.value = detail.pinned
    displayOrder.value = detail.displayOrder
    contentHtmlLegacy.value = detail.contentHtml
    jobPositions.value = detail.jobPositions.map(({ id: _id, ...position }) => position)
    formConfig.value = detail.applicationFormConfig
    const loaded: EditableImage[] = []
    for (const image of detail.images) {
      const blob = await adminJobPostingApi.fetchImageBlob(editingId.value!, image.id)
      loaded.push({
        key: `existing-${image.id}`,
        id: image.id,
        file: null,
        altText: image.altText,
        originalAltText: image.altText,
        previewUrl: URL.createObjectURL(blob.data),
      })
    }
    images.value = loaded
  } catch (error) {
    message.error(getApiErrorMessage(error, '공고 정보를 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

onMounted(loadForEdit)
onBeforeUnmount(() => {
  images.value.forEach((image) => URL.revokeObjectURL(image.previewUrl))
})
</script>

<template>
  <div class="job-posting-form">
    <header class="page-header">
      <h2 class="page-title">{{ isEdit ? '공고 수정' : '공고 등록' }}</h2>
      <p class="page-description">저장하면 작성 중(draft) 상태로 보관되며, 상세 화면에서 미리보기 검수 후 발행합니다.</p>
    </header>

    <a-spin :spinning="loading">
      <a-card title="기본 정보" :bordered="false" class="form-card">
        <div class="field-grid">
          <label class="field field-wide">
            <span class="field-label">공고 제목 *</span>
            <a-input v-model:value="title" placeholder="예: 2026년 신입사원 공개채용" />
          </label>
          <label class="field">
            <span class="field-label">공고 유형</span>
            <a-select v-model:value="postingType" :options="postingTypeOptions" />
          </label>
          <label class="field">
            <span class="field-label">표시 순서</span>
            <a-input-number v-model:value="displayOrder" :min="0" style="width: 100%" />
          </label>
          <label class="field field-wide">
            <span class="field-label">요약</span>
            <a-textarea v-model:value="summary" :rows="2" :maxlength="500" placeholder="목록에 노출되는 짧은 설명 (HTML 불가)" />
          </label>
          <label class="field">
            <span class="field-label">접수 시작일시 *</span>
            <a-date-picker v-model:value="receptionStart" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
          </label>
          <label class="field">
            <span class="field-label">접수 종료일시 *</span>
            <a-date-picker v-model:value="receptionEnd" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
          </label>
          <label class="field">
            <span class="field-label">노출 시작일시</span>
            <a-date-picker v-model:value="displayStart" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
          </label>
          <label class="field">
            <span class="field-label">노출 종료일시</span>
            <a-date-picker v-model:value="displayEnd" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
          </label>
          <label class="field">
            <span class="field-label">노출 여부</span>
            <a-switch v-model:checked="visible" />
          </label>
          <label class="field">
            <span class="field-label">상단 고정</span>
            <a-switch v-model:checked="pinned" />
          </label>
        </div>
      </a-card>

      <a-card :bordered="false" class="form-card">
        <template #title>공고 이미지 ({{ images.length }}/{{ MAX_IMAGES }})</template>
        <template #extra>
          <a-button @click="openFilePicker" :disabled="images.length >= MAX_IMAGES">이미지 추가</a-button>
        </template>
        <input
          ref="fileInput"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          multiple
          class="hidden-input"
          @change="handleFilesSelected"
        />
        <p v-if="images.length === 0" class="state-message">
          공고 본문으로 노출할 포스터 이미지를 추가해 주세요. (jpg/png/webp, 장당 10MB, 최대 10장)
        </p>
        <div v-for="(image, index) in images" :key="image.key" class="image-row">
          <img :src="image.previewUrl" :alt="image.altText || '공고 이미지 미리보기'" class="image-thumb" />
          <div class="image-meta">
            <a-input v-model:value="image.altText" :maxlength="200" placeholder="대체 텍스트(필수) — 예: 2026 신입 공채 모집 부문 안내" />
          </div>
          <div class="image-actions">
            <a-button size="small" :disabled="index === 0" @click="moveImage(index, -1)">위로</a-button>
            <a-button size="small" :disabled="index === images.length - 1" @click="moveImage(index, 1)">아래로</a-button>
            <a-button size="small" danger @click="removeImage(index)">삭제</a-button>
          </div>
        </div>
      </a-card>

      <a-card title="모집분야" :bordered="false" class="form-card">
        <template #extra>
          <a-button @click="addPosition">모집분야 추가</a-button>
        </template>
        <div v-for="(position, index) in jobPositions" :key="index" class="position-row">
          <a-input v-model:value="position.positionName" placeholder="모집분야명 *" class="position-name" />
          <a-select v-model:value="position.applicationType" :options="applicationTypeOptions" class="position-select" />
          <a-select v-model:value="position.employmentType" :options="employmentTypeOptions" class="position-select" />
          <a-input v-model:value="position.jobGroup" placeholder="직군" class="position-input" />
          <a-input v-model:value="position.jobTitle" placeholder="담당 직무" class="position-input" />
          <a-input v-model:value="position.workLocation" placeholder="근무지" class="position-input" />
          <a-button danger size="small" @click="removePosition(index)">삭제</a-button>
        </div>
      </a-card>

      <a-card title="지원서 양식 구성" :bordered="false" class="form-card">
        <div class="config-grid">
          <div v-for="section in formSections" :key="section.useKey" class="config-item">
            <a-checkbox
              :checked="Boolean(formConfig[section.useKey])"
              @update:checked="(checked: boolean) => { (formConfig[section.useKey] as boolean) = checked; if (!checked) (formConfig[section.requireKey] as boolean | null) = false }"
            >
              {{ section.label }}
            </a-checkbox>
            <a-checkbox
              :checked="Boolean(formConfig[section.requireKey])"
              :disabled="!formConfig[section.useKey]"
              @update:checked="(checked: boolean) => { (formConfig[section.requireKey] as boolean | null) = checked }"
            >
              필수
            </a-checkbox>
          </div>
          <div class="config-item">
            <a-checkbox v-model:checked="formConfig.useAttachment">첨부파일</a-checkbox>
          </div>
        </div>
      </a-card>

      <div class="form-actions">
        <a-button @click="router.back()">취소</a-button>
        <a-button type="primary" :loading="saving" @click="save">저장</a-button>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.job-posting-form {
  padding: 24px;
  max-width: 1080px;
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
.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.field-wide {
  grid-column: 1 / -1;
}
.field-label {
  font-size: 13px;
  color: #666;
}
.hidden-input {
  display: none;
}
.state-message {
  color: #999;
}
.image-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}
.image-thumb {
  width: 96px;
  height: 96px;
  object-fit: contain;
  background: #fafafa;
  border: 1px solid #eee;
}
.image-meta {
  flex: 1;
}
.image-actions {
  display: flex;
  gap: 4px;
}
.position-row {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 6px 0;
}
.position-name {
  width: 180px;
}
.position-select {
  width: 130px;
}
.position-input {
  width: 140px;
}
.config-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 16px;
}
.config-item {
  display: flex;
  gap: 12px;
}
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
