<script setup lang="ts">
import { computed, defineComponent, h, ref, watch } from 'vue'
import type { Component, ComponentPublicInstance } from 'vue'
import type { ApplicationSectionType, ApplicationFormItem, ApplicationFormPage, ApplicationFormPageResponse, SectionActionHandle } from '@/types/application'
import { useRoute } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  LeftOutlined,
  ReloadOutlined,
  RightOutlined,
  SaveOutlined,
  SendOutlined,
} from '@ant-design/icons-vue'

import { apiClient } from '@/api/client'
import type { ApiResponse } from '@/types/api'
import { dashboardApi } from '@/api/application/dashboardApi'

import BasicInfoSection from '@/views/applicant/application/sections/BasicInfoSection.vue'
import EducationSection from '@/views/applicant/application/sections/EducationSection.vue'
import CareerSection from '@/views/applicant/application/sections/CareerSection.vue'
import LanguageSection from '@/views/applicant/application/sections/LanguageSection.vue'
import AwardSection from '@/views/applicant/application/sections/AwardSection.vue'
import CertificateSection from '@/views/applicant/application/sections/CertificateSection.vue'
import GapPeriodSection from '@/views/applicant/application/sections/GapPeriodSection.vue'
import QuestionAnswerSection from '@/views/applicant/application/sections/QuestionAnswerSection.vue'
import MilitarySection from './application/sections/MilitarySection.vue'

/**
 * 실제 섹션 컴포넌트가 준비되기 전까지 화면 구조를 확인하기 위한 fallback 컴포넌트다.
 * 각 섹션 컴포넌트가 생기면 sectionComponentMap의 값을 실제 import 컴포넌트로 교체하면 된다.
 *
 * 각 섹션 컴포넌트는 필요 시 다음 메서드를 expose하면 하단 버튼과 연동된다.
 * - defineExpose({ saveDraft, validateBeforeSubmit })
 */
const ApplicationSectionPlaceholder = defineComponent({
  name: 'ApplicationSectionPlaceholder',
  props: {
    applicationId: {
      type: Number,
      required: true,
    },
    section: {
      type: Object,
      required: true,
    },
    page: {
      type: Object,
      required: true,
    },
    editable: {
      type: Boolean,
      required: true,
    },
  },
  setup(props) {
    return () =>
      h('div', { class: 'section-placeholder' }, [
        h('div', { class: 'placeholder-title' }, `${sectionDisplayName(props.section as ApplicationFormItem)} 영역`),
        h(
          'p',
          { class: 'placeholder-desc' },
          '아직 실제 입력 컴포넌트가 연결되지 않았습니다. sectionComponentMap에서 해당 sectionType에 맞는 Vue 컴포넌트로 교체하세요.',
        ),
        h('dl', { class: 'placeholder-meta' }, [
          h('div', [h('dt', 'applicationId'), h('dd', String(props.applicationId))]),
          h('div', [h('dt', 'sectionType'), h('dd', (props.section as ApplicationFormItem).sectionType)]),
          h('div', [h('dt', 'editable'), h('dd', props.editable ? 'true' : 'false')]),
        ]),
      ])
  },
})

/**
 * 실제 컴포넌트가 생기면 아래처럼 교체한다.
 *
 * import BasicInfoSection from '@/views/applicant/application/sections/BasicInfoSection.vue'
 * const sectionComponentMap = {
 *   BASIC_INFO: BasicInfoSection,
 *   ...
 * }
 */
const sectionComponentMap: Record<ApplicationSectionType, Component> = {
  // BASIC_INFO: ApplicationSectionPlaceholder,
  BASIC_INFO: BasicInfoSection,
  MILITARY: MilitarySection,
  EDUCATION: EducationSection,
  CAREER: CareerSection,
  CERTIFICATE: CertificateSection,
  LANGUAGE: LanguageSection,
  AWARD: AwardSection,
  GAP_PERIOD: GapPeriodSection,
  QUESTION_ANSWER: QuestionAnswerSection,
  ATTACHMENT: ApplicationSectionPlaceholder,
}

const sectionNameMap: Record<ApplicationSectionType, string> = {
  BASIC_INFO: '기본 정보',
  MILITARY: '병역',
  EDUCATION: '학력',
  CAREER: '경력',
  CERTIFICATE: '자격',
  LANGUAGE: '어학',
  AWARD: '수상',
  GAP_PERIOD: '공백기간',
  QUESTION_ANSWER: '자기소개/질문',
  ATTACHMENT: '첨부파일',
}

/**
 * 프론트 섹션 타입 → 백엔드 완성도(dashboard) sectionCode 매핑.
 * 대부분 동일하나 자기소개/질문은 백엔드에서 `QUESTION`으로 내려온다.
 * CAREER는 백엔드 완성도 판정(ApplicationCompletionReadChecker) 대상이 아니므로 매핑하지 않는다.
 */
const completionSectionCodeMap: Partial<Record<ApplicationSectionType, string>> = {
  BASIC_INFO: 'BASIC_INFO',
  MILITARY: 'MILITARY',
  EDUCATION: 'EDUCATION',
  CERTIFICATE: 'CERTIFICATE',
  LANGUAGE: 'LANGUAGE',
  AWARD: 'AWARD',
  GAP_PERIOD: 'GAP_PERIOD',
  QUESTION_ANSWER: 'QUESTION',
  ATTACHMENT: 'ATTACHMENT',
}

// 백엔드가 실제로 완성도를 판정하는 sectionCode 집합.
const backendTrackedCompletionCodes = new Set<string>(Object.values(completionSectionCodeMap))

type StepStatus = 'wait' | 'process' | 'finish' | 'error'

type ApplicationStepItem = {
  title: string
  description: string
  status: StepStatus
}

type SectionRefValue = Element | ComponentPublicInstance | SectionActionHandle | null

type CompletionAwareSection = ApplicationFormItem & {
  completed?: boolean
  complete?: boolean
  ready?: boolean
  valid?: boolean
  requiredSatisfied?: boolean
}

type PageAwareSection = ApplicationFormItem & {
  pageNo?: number | null
  pageTitle?: string | null
  pageDescription?: string | null
  pageSortOrder?: number | null
}

const route = useRoute()

const loading = ref(false)
const submitting = ref(false)
const saving = ref(false)
const formPage = ref<ApplicationFormPageResponse | null>(null)
const currentPageIndex = ref(0)
const sectionRefs = ref<Map<string, SectionActionHandle>>(new Map())
// 백엔드 완성도 판정 결과 중 "필수인데 아직 미완"인 섹션 코드 집합.
const incompleteRequiredSectionCodes = ref<Set<string>>(new Set())

const applicationId = computed<number | null>(() => {
  const raw = route.params.applicationId ?? route.query.applicationId
  const value = Array.isArray(raw) ? raw[0] : raw
  const parsed = Number(value)

  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
})

const resolvedApplicationId = computed(() => applicationId.value ?? 0)

const pages = computed<ApplicationFormPage[]>(() => {
  const data = formPage.value

  if (!data) {
    return []
  }

  const rawPages = data.pages?.length
    ? data.pages
    : buildPagesFromSections(data.sections ?? [])

  return rawPages
    .map((page) => ({
      ...page,
      items: [...(page.items ?? [])].sort(compareBySortOrder),
    }))
    .sort(compareBySortOrder)
})

const currentPage = computed<ApplicationFormPage | null>(() => {
  return pages.value[currentPageIndex.value] ?? null
})

const stepItems = computed<ApplicationStepItem[]>(() => {
  return pages.value.map((page, index) => ({
    title: page.title || `${page.pageNo}페이지`,
    description: pageStepDescription(page),
    status: resolvePageStepStatus(page, index),
  }))
})

const pageTitle = computed(() => {
  return formPage.value?.jobPostingTitle ?? formPage.value?.postingTitle ?? '지원서 작성'
})

const selectedPositionText = computed(() => {
  if (!formPage.value?.jobPositionName) {
    return '모집분야 미선택'
  }

  return formPage.value.jobPositionName
})

const canEdit = computed(() => formPage.value?.editable === true)
const isFirstPage = computed(() => currentPageIndex.value <= 0)
const isLastPage = computed(() => currentPageIndex.value >= pages.value.length - 1)

watch(
  applicationId,
  async (id) => {
    if (!id) {
      message.error('지원서 식별자가 올바르지 않습니다.')
      return
    }

    await fetchFormPage(id)
  },
  { immediate: true },
)

watch(
  pages,
  (nextPages) => {
    if (nextPages.length === 0) {
      currentPageIndex.value = 0
      return
    }

    if (currentPageIndex.value > nextPages.length - 1) {
      currentPageIndex.value = nextPages.length - 1
    }
  },
  { immediate: true },
)


function buildPagesFromSections(sections: ApplicationFormItem[]): ApplicationFormPage[] {
  if (sections.length === 0) {
    return []
  }

  const pageAwareSections = sections.map((section) => section as PageAwareSection)
  const hasPageInfo = pageAwareSections.some((section) => typeof section.pageNo === 'number')

  if (!hasPageInfo) {
    return [
      {
        pageNo: 1,
        title: '지원서',
        description: null,
        sortOrder: 0,
        items: sections,
      },
    ]
  }

  const pageMap = new Map<number, ApplicationFormPage>()

  for (const section of pageAwareSections) {
    const pageNo = normalizePositiveNumber(section.pageNo) ?? 1
    const page = pageMap.get(pageNo)

    if (page) {
      page.items.push(section)
      continue
    }

    pageMap.set(pageNo, {
      pageNo,
      title: section.pageTitle || `${pageNo}페이지`,
      description: section.pageDescription ?? null,
      sortOrder: section.pageSortOrder ?? pageNo,
      items: [section],
    })
  }

  return [...pageMap.values()]
}

function pageStepDescription(page: ApplicationFormPage): string {
  const requiredItems = page.items.filter((item) => item.required)

  if (requiredItems.length === 0) {
    return '선택 항목'
  }

  const completedCount = requiredItems.filter(isSectionCompleted).length
  return `${completedCount}/${requiredItems.length} 완료`
}

function resolvePageStepStatus(page: ApplicationFormPage, index: number): StepStatus {
  if (index === currentPageIndex.value) {
    return 'process'
  }

  // 체크 아이콘 미사용
  // return isPageCompleted(page) ? 'finish' : 'wait'
  return 'wait'
}

function isPageCompleted(page: ApplicationFormPage): boolean {
  const requiredItems = page.items.filter((item) => item.required)

  if (requiredItems.length === 0) {
    return true
  }

  return requiredItems.every(isSectionCompleted)
}

function isSectionCompleted(item: ApplicationFormItem): boolean {
  const section = item as CompletionAwareSection

  if (typeof section.completed === 'boolean') {
    return section.completed
  }

  if (typeof section.complete === 'boolean') {
    return section.complete
  }

  if (typeof section.ready === 'boolean') {
    return section.ready
  }

  if (typeof section.requiredSatisfied === 'boolean') {
    return section.requiredSatisfied
  }

  if (typeof section.valid === 'boolean') {
    return section.valid
  }

  // 선택 섹션은 카운터 상 항상 충족으로 본다(분모에는 필수 섹션만 들어간다).
  if (!item.required) {
    return true
  }

  // 백엔드 완성도(dashboard) 판정 사용: 필수 섹션 코드가 미완 목록에 없으면 완료.
  const code = completionSectionCodeMap[item.sectionType]
  if (code && backendTrackedCompletionCodes.has(code)) {
    return !incompleteRequiredSectionCodes.value.has(code)
  }

  // 백엔드가 완성도를 판정하지 않는 섹션(예: CAREER)은 완료로 단정하지 않는다.
  return false
}

function normalizePositiveNumber(value: number | null | undefined): number | null {
  if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) {
    return null
  }

  return value
}

async function fetchFormPage(id = applicationId.value): Promise<void> {
  if (!id) {
    return
  }

  loading.value = true

  try {
    const response = await apiClient.get<ApiResponse<ApplicationFormPageResponse>>(
      `/applications/${id}/form-page`,
    )

    if (!response.data.success) {
      throw new Error(response.data.message || '지원서 구성 조회에 실패했습니다.')
    }

    formPage.value = response.data.data
    currentPageIndex.value = 0
    sectionRefs.value.clear()
    await fetchCompletion(id)
  } catch (error) {
    message.error(getErrorMessage(error, '지원서 구성 조회에 실패했습니다.'))
  } finally {
    loading.value = false
  }
}

// 지원서 완성도(섹션별 필수 충족 여부) 조회 후 미완 필수 섹션 코드 집합을 갱신한다.
// 보조 정보이므로 실패해도 화면 로딩·저장 흐름을 막지 않는다.
async function fetchCompletion(id = applicationId.value): Promise<void> {
  if (!id) {
    return
  }

  try {
    const response = await dashboardApi.getApplicationDashboard(id)

    if (!response.data.success || !response.data.data) {
      return
    }

    const missing = response.data.data.requiredMissingSections ?? []
    incompleteRequiredSectionCodes.value = new Set(missing.map((section) => section.sectionCode))
  } catch (error) {
    console.warn('지원서 완성도 조회에 실패했습니다.', error)
  }
}

function handleStepChange(nextIndex: number): void {
  if (nextIndex < 0 || nextIndex > pages.value.length - 1) {
    return
  }

  currentPageIndex.value = nextIndex
}

function goPrevious(): void {
  if (!isFirstPage.value) {
    currentPageIndex.value -= 1
  }
}

function goNext(): void {
  if (!isLastPage.value) {
    currentPageIndex.value += 1
  }
}

async function saveCurrentPage(): Promise<void> {
  const page = currentPage.value

  if (!page) {
    return
  }

  const handles = page.items
    .map((item) => sectionRefs.value.get(sectionKey(page, item)))
    .filter((handle): handle is SectionActionHandle => !!handle)

  const savableHandles = handles.filter((handle) => typeof handle.saveDraft === 'function')

  if (savableHandles.length === 0) {
    message.info('현재 페이지에 연결된 임시저장 가능 섹션 컴포넌트가 없습니다.')
    return
  }

  saving.value = true

  try {
    for (const handle of savableHandles) {
      await handle.saveDraft?.()
    }

    // 저장된 데이터 기준으로 완성도를 다시 판정해 카운터를 갱신한다.
    await fetchCompletion()

    message.success('현재 페이지를 임시저장했습니다.')
  } catch (error) {
    message.error(getErrorMessage(error, '임시저장에 실패했습니다.'))
  } finally {
    saving.value = false
  }
}

function confirmSubmit(): void {
  Modal.confirm({
    title: '최종 제출',
    content: '최종 제출 후에는 지원서 수정이 제한될 수 있습니다. 제출하시겠습니까?',
    okText: '최종 제출',
    cancelText: '취소',
    async onOk() {
      await submitApplication()
    },
  })
}

async function submitApplication(): Promise<void> {
  const id = applicationId.value

  if (!id) {
    message.error('지원서 식별자가 올바르지 않습니다.')
    return
  }

  submitting.value = true

  try {
    const valid = await validateAllVisibleSections()

    if (!valid) {
      throw new Error('입력값을 확인해주세요.')
    }

    const response = await apiClient.post<ApiResponse<unknown>>(`/applications/${id}/submit`)

    if (!response.data.success) {
      throw new Error(response.data.message || '최종 제출에 실패했습니다.')
    }

    message.success('지원서가 최종 제출되었습니다.')
    await fetchFormPage(id)
  } catch (error) {
    message.error(getErrorMessage(error, '최종 제출에 실패했습니다.'))
  } finally {
    submitting.value = false
  }
}

async function validateAllVisibleSections(): Promise<boolean> {
  for (const page of pages.value) {
    for (const item of page.items) {
      const handle = sectionRefs.value.get(sectionKey(page, item))

      if (typeof handle?.validateBeforeSubmit === 'function') {
        const valid = await handle.validateBeforeSubmit()

        if (!valid) {
          currentPageIndex.value = pages.value.findIndex((candidate) => candidate.pageNo === page.pageNo)
          return false
        }
      }
    }
  }

  return true
}

function createSectionRefSetter(
  page: ApplicationFormPage | null,
  item: ApplicationFormItem,
): (value: SectionRefValue) => void {
  return (value: SectionRefValue) => setSectionRef(page, item, value)
}

function setSectionRef(page: ApplicationFormPage | null, item: ApplicationFormItem, value: SectionRefValue): void {
  if (!page) {
    return
  }

  const key = sectionKey(page, item)

  if (!value) {
    sectionRefs.value.delete(key)
    return
  }

  sectionRefs.value.set(key, value as SectionActionHandle)
}

function resolveSectionComponent(sectionType: ApplicationSectionType): Component {
  return sectionComponentMap[sectionType] ?? ApplicationSectionPlaceholder
}

function sectionKey(page: ApplicationFormPage, item: ApplicationFormItem): string {
  return `${page.pageNo}:${item.sectionType}`
}

function sectionDisplayName(item: ApplicationFormItem): string {
  return sectionNameMap[item.sectionType] ?? item.sectionName ?? item.sectionType
}

function compareBySortOrder<T extends { sortOrder?: number; pageNo?: number }>(a: T, b: T): number {
  const sortA = a.sortOrder ?? a.pageNo ?? 0
  const sortB = b.sortOrder ?? b.pageNo ?? 0

  if (sortA !== sortB) {
    return sortA - sortB
  }

  return (a.pageNo ?? 0) - (b.pageNo ?? 0)
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
</script>

<template>
  <section class="application-form-page">
    <div class="page-inner">
      <a-card class="application-header-card" :bordered="false">
        <div class="application-header">
          <div>
            <p class="eyebrow">Application Form</p>
            <h1>{{ pageTitle }}</h1>
            <p class="header-desc">
              모집분야: {{ selectedPositionText }}
            </p>
          </div>

          <a-space wrap>
            <a-tag v-if="formPage?.accepting" color="green">접수중</a-tag>
            <a-tag v-else color="default">접수상태 확인 필요</a-tag>
            <a-tag v-if="canEdit" color="blue">수정 가능</a-tag>
            <a-tag v-else color="default">수정 제한</a-tag>
            <a-button :loading="loading" @click="fetchFormPage()">
              <ReloadOutlined />
              새로고침
            </a-button>
          </a-space>
        </div>
      </a-card>

      <a-spin :spinning="loading">
        <template v-if="pages.length > 0">
          <a-card class="steps-card" :bordered="false">
            <div class="steps-scroll">
              <a-steps
                type="navigation"
                :current="currentPageIndex"
                :items="stepItems"
                :responsive="false"
                @change="handleStepChange"
              />
            </div>
          </a-card>

          <a-card v-if="currentPage" class="form-content-card" :bordered="false">
            <div class="page-heading">
              <div>
                <h2>{{ currentPage.title }}</h2>
                <p v-if="currentPage.description">
                  {{ currentPage.description }}
                </p>
              </div>

              <span class="page-count"> {{ currentPageIndex + 1 }} / {{ pages.length }} </span>
            </div>

            <div class="section-stack">
              <article
                v-for="item in currentPage.items"
                :key="sectionKey(currentPage, item)"
                class="section-panel"
              >
                <header class="section-panel-header">
                  <div>
                    <h3>{{ sectionDisplayName(item) }}</h3>
                    <p>{{ item.sectionType }}</p>
                  </div>

                  <a-tag v-if="item.required" color="red">필수</a-tag>
                  <a-tag v-else>선택</a-tag>
                </header>

                <component
                  :is="resolveSectionComponent(item.sectionType)"
                  :ref="createSectionRefSetter(currentPage, item)"
                  :application-id="resolvedApplicationId"
                  :section="item"
                  :page="currentPage"
                  :editable="canEdit"
                  :form-page="formPage"
                />
              </article>
            </div>
          </a-card>

          <div class="bottom-actions">
            <a-space wrap>
              <a-button :disabled="isFirstPage" @click="goPrevious">
                <LeftOutlined />
                이전
              </a-button>

              <a-button :disabled="isLastPage" @click="goNext">
                다음
                <RightOutlined />
              </a-button>
            </a-space>

            <a-space wrap>
              <a-button :disabled="!canEdit" :loading="saving" @click="saveCurrentPage">
                <SaveOutlined />
                임시저장
              </a-button>

              <a-button type="primary" :disabled="!canEdit" :loading="submitting" @click="confirmSubmit">
                <SendOutlined />
                최종 제출
              </a-button>
            </a-space>
          </div>
        </template>

        <a-empty v-else class="empty-box" description="지원서 구성 정보가 없습니다." />
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
  max-width: 1180px;
  margin: 0 auto;
  padding: 32px 24px 72px;
}

.application-header-card,
.steps-card,
.form-content-card {
  border: 1px solid var(--app-border-default);
  border-radius: 10px;
  box-shadow: var(--app-shadow-soft);
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

.steps-scroll {
  overflow-x: auto;
  padding-bottom: 4px;
}

.steps-scroll :deep(.ant-steps) {
  min-width: max-content;
}

.steps-scroll :deep(.ant-steps-item) {
  min-width: 148px;
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

:deep(.ant-steps .ant-steps-item-finish .ant-steps-item-icon) {
  background-color: rgba(0, 0, 0, 0.06);
  border-color: rgba(0, 0, 0, 0);
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
