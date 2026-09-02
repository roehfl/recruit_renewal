<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { adminApplicationFormApi } from '@/api/admin/adminApplicationFormApi'
import { getApiErrorMessage } from '@/api/apiError'
import { sectionLabel, sectionSourceLabel } from '@/common/applicationSection'
import type {
  ApplicationFormLayoutPreviewResponse,
  availableSectionsItem,
  sectionType,
} from '@/types/admin/application'

const props = defineProps<{ jobPostingId: number }>()

const TITLE_MAX_LENGTH = 100
const DESCRIPTION_MAX_LENGTH = 500

interface EditablePage {
  key: number
  title: string
  description: string
  items: sectionType[]
}

/** 드래그 중인 칩의 출처. number 는 페이지 인덱스, 'palette' 는 미배치 서랍이다. */
interface DragSource {
  sectionType: sectionType
  from: number | 'palette'
}

const loading = ref(false)
const saving = ref(false)
const editable = ref(false)
const layoutStored = ref(false)
const pages = ref<EditablePage[]>([])
const palette = ref<sectionType[]>([])
const sections = ref<availableSectionsItem[]>([])
const removedDisabled = ref<sectionType[]>([])
const dragging = ref<DragSource | null>(null)
const previewOpen = ref(false)
const preview = ref<ApplicationFormLayoutPreviewResponse | null>(null)
const previewLoading = ref(false)

let pageKeySeq = 0

const sectionMeta = computed<Record<string, availableSectionsItem>>(() =>
  Object.fromEntries(sections.value.map((section) => [section.sectionType, section])),
)

const disabledSections = computed(() => sections.value.filter((section) => !section.enabled))

const isRequired = (type: sectionType) => Boolean(sectionMeta.value[type]?.required)

/*
 * 서버는 "배치된 섹션 == 활성 섹션"을 강제한다(ApplicationFormLayoutValidator).
 * 미배치가 남아 있으면 저장할 수 없으므로 서랍이 비었는지가 저장 가능 여부의 1차 조건이다.
 */
const blockingReason = computed<string | null>(() => {
  if (!editable.value) {
    return '접수가 시작되었거나 마감된 공고는 폼 구성을 수정할 수 없습니다.'
  }
  if (palette.value.length > 0) {
    return `배치되지 않은 섹션이 ${palette.value.length}개 있습니다. 모두 페이지에 배치해야 저장할 수 있습니다.`
  }
  if (pages.value.length === 0) {
    return '페이지를 최소 1개 만들어야 합니다.'
  }
  const emptyPage = pages.value.findIndex((page) => page.items.length === 0)
  if (emptyPage >= 0) {
    return `${emptyPage + 1}페이지에 배치된 섹션이 없습니다. 항목이 없는 페이지는 저장할 수 없습니다.`
  }
  const blankTitle = pages.value.findIndex((page) => page.title.trim().length === 0)
  if (blankTitle >= 0) {
    return `${blankTitle + 1}페이지의 제목을 입력해 주세요.`
  }
  return null
})

const load = async () => {
  loading.value = true
  try {
    const response = await adminApplicationFormApi.getLayout(props.jobPostingId)
    const data = response.data.data

    editable.value = data.editable
    layoutStored.value = data.layoutStored
    sections.value = data.availableSections

    const enabled = new Set(
      data.availableSections.filter((section) => section.enabled).map((section) => section.sectionType),
    )

    const dropped: sectionType[] = []
    pages.value = data.pages.map((page) => {
      const items: sectionType[] = []
      page.items.forEach((item) => {
        // 저장 이후 섹션이 꺼졌다면 그 항목은 더 이상 배치할 수 없다.
        if (enabled.has(item.sectionType)) {
          items.push(item.sectionType)
        } else {
          dropped.push(item.sectionType)
        }
      })
      return {
        key: ++pageKeySeq,
        title: page.title,
        description: page.description ?? '',
        items,
      }
    })
    removedDisabled.value = dropped

    const placed = new Set(pages.value.flatMap((page) => page.items))
    palette.value = [...enabled].filter((type) => !placed.has(type))
  } catch (error) {
    message.error(getApiErrorMessage(error, '폼 구성을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

const addPage = () => {
  pages.value.push({ key: ++pageKeySeq, title: `${pages.value.length + 1}페이지`, description: '', items: [] })
}

const removePage = (index: number) => {
  const [removed] = pages.value.splice(index, 1)
  if (removed) {
    palette.value.push(...removed.items)
  }
}

const movePage = (index: number, offset: number) => {
  const target = index + offset
  if (target < 0 || target >= pages.value.length) {
    return
  }
  const [moved] = pages.value.splice(index, 1)
  if (moved) {
    pages.value.splice(target, 0, moved)
  }
}

const startDrag = (event: DragEvent, sectionType: sectionType, from: number | 'palette') => {
  if (!editable.value) {
    return
  }
  dragging.value = { sectionType, from }
  event.dataTransfer?.setData('text/plain', sectionType)
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

const endDrag = () => {
  dragging.value = null
}

const detachDragged = (source: DragSource) => {
  if (source.from === 'palette') {
    palette.value = palette.value.filter((type) => type !== source.sectionType)
    return
  }
  const page = pages.value[source.from]
  if (page) {
    page.items = page.items.filter((type) => type !== source.sectionType)
  }
}

/** insertAt 이 null 이면 페이지 끝에 붙인다. */
const dropOnPage = (pageIndex: number, insertAt: number | null) => {
  const source = dragging.value
  if (!editable.value || !source) {
    return
  }
  const target = pages.value[pageIndex]
  if (!target) {
    return
  }
  const samePageIndexBefore = source.from === pageIndex ? target.items.indexOf(source.sectionType) : -1
  detachDragged(source)

  let position = insertAt ?? target.items.length
  // 같은 페이지 안에서 앞쪽 항목을 뒤로 옮기면 제거 때문에 인덱스가 하나 당겨진다.
  if (samePageIndexBefore >= 0 && insertAt !== null && samePageIndexBefore < insertAt) {
    position -= 1
  }
  target.items.splice(Math.max(0, Math.min(position, target.items.length)), 0, source.sectionType)
  dragging.value = null
}

const dropOnPalette = () => {
  const source = dragging.value
  if (!editable.value || !source || source.from === 'palette') {
    return
  }
  detachDragged(source)
  palette.value.push(source.sectionType)
  dragging.value = null
}

const buildRequest = () => ({
  pages: pages.value.map((page, index) => ({
    pageNo: index + 1,
    title: page.title.trim(),
    description: page.description.trim().length > 0 ? page.description.trim() : null,
    sortOrder: index,
    items: page.items.map((sectionType, itemIndex) => ({ sectionType, sortOrder: itemIndex })),
  })),
})

const save = async () => {
  const reason = blockingReason.value
  if (reason) {
    message.warning(reason)
    return
  }
  const tooLong = pages.value.find(
    (page) => page.title.trim().length > TITLE_MAX_LENGTH || page.description.trim().length > DESCRIPTION_MAX_LENGTH,
  )
  if (tooLong) {
    message.warning(`페이지 제목은 ${TITLE_MAX_LENGTH}자, 설명은 ${DESCRIPTION_MAX_LENGTH}자를 넘을 수 없습니다.`)
    return
  }

  saving.value = true
  try {
    await adminApplicationFormApi.saveLayout(props.jobPostingId, buildRequest())
    message.success('폼 구성을 저장했습니다.')
    removedDisabled.value = []
    await load()
  } catch (error) {
    message.error(getApiErrorMessage(error, '폼 구성 저장에 실패했습니다.'))
  } finally {
    saving.value = false
  }
}

const openPreview = async () => {
  previewLoading.value = true
  previewOpen.value = true
  try {
    const response = await adminApplicationFormApi.getLayoutPreview(props.jobPostingId)
    preview.value = response.data.data
  } catch (error) {
    message.error(getApiErrorMessage(error, '미리보기를 불러오지 못했습니다.'))
    previewOpen.value = false
  } finally {
    previewLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="layout-tab">
    <a-spin :spinning="loading">
      <div class="tab-head">
        <p class="tab-description">
          지원자가 보게 될 페이지와 순서를 정합니다. 왼쪽 서랍의 섹션을 오른쪽 페이지로 끌어다 놓으세요.
          <a-tag v-if="!layoutStored" color="orange">기본 레이아웃 · 미저장</a-tag>
        </p>
        <div class="tab-actions">
          <a-button @click="openPreview">미리보기</a-button>
          <a-button type="primary" :loading="saving" :disabled="Boolean(blockingReason)" @click="save">저장</a-button>
        </div>
      </div>

      <a-alert
        v-if="removedDisabled.length > 0"
        type="warning"
        show-icon
        class="tab-alert"
        message="저장된 레이아웃에 꺼진 섹션이 있어 제외했습니다."
        :description="`제외된 섹션: ${removedDisabled.map(sectionLabel).join(', ')} — 저장하면 반영됩니다.`"
      />
      <a-alert
        v-else-if="blockingReason"
        :type="editable ? 'warning' : 'info'"
        show-icon
        class="tab-alert"
        :message="editable ? '아직 저장할 수 없습니다' : '읽기 전용'"
        :description="blockingReason"
      />
      <a-alert
        v-else
        type="success"
        show-icon
        class="tab-alert"
        message="저장할 수 있습니다."
        :description="`활성 섹션이 모두 배치되었습니다. 총 ${pages.length}개 페이지.`"
      />

      <div class="layout-grid">
        <aside class="palette" @dragover.prevent @drop.prevent="dropOnPalette">
          <h4 class="palette-title">
            미배치 섹션
            <span class="palette-count" :class="{ warn: palette.length > 0 }">{{ palette.length }}</span>
          </h4>

          <div
            v-for="type in palette"
            :key="type"
            class="chip unplaced"
            :draggable="editable"
            @dragstart="(event) => startDrag(event, type, 'palette')"
            @dragend="endDrag"
          >
            <span class="grip">⠿</span>
            <span class="chip-name">{{ sectionLabel(type) }}</span>
            <a-tag v-if="isRequired(type)" color="red">필수</a-tag>
          </div>
          <p v-if="palette.length === 0" class="palette-empty">모두 배치되었습니다.</p>

          <h4 class="palette-title muted">비활성 섹션</h4>
          <div v-for="section in disabledSections" :key="section.sectionType" class="chip disabled">
            <span class="chip-name">{{ sectionLabel(section.sectionType) }}</span>
            <span class="chip-source">{{ sectionSourceLabel(section.source) }}</span>
          </div>
          <p v-if="disabledSections.length === 0" class="palette-empty">없음</p>
        </aside>

        <div class="canvas">
          <section
            v-for="(page, pageIndex) in pages"
            :key="page.key"
            class="page-card"
            @dragover.prevent
            @drop.prevent="dropOnPage(pageIndex, null)"
          >
            <header class="page-head">
              <span class="page-no">{{ pageIndex + 1 }}</span>
              <a-input
                v-model:value="page.title"
                class="page-title-input"
                :maxlength="TITLE_MAX_LENGTH"
                :disabled="!editable"
                placeholder="페이지 제목"
              />
              <span class="page-count">{{ page.items.length }}개 항목</span>
              <a-button size="small" :disabled="!editable || pageIndex === 0" @click="movePage(pageIndex, -1)">↑</a-button>
              <a-button size="small" :disabled="!editable || pageIndex === pages.length - 1" @click="movePage(pageIndex, 1)">↓</a-button>
              <a-button size="small" danger :disabled="!editable" @click="removePage(pageIndex)">삭제</a-button>
            </header>

            <a-input
              v-model:value="page.description"
              class="page-description-input"
              :maxlength="DESCRIPTION_MAX_LENGTH"
              :disabled="!editable"
              placeholder="페이지 설명 (선택)"
            />

            <div class="page-body">
              <div
                v-for="(type, itemIndex) in page.items"
                :key="type"
                class="chip"
                :draggable="editable"
                @dragstart="(event) => startDrag(event, type, pageIndex)"
                @dragend="endDrag"
                @dragover.prevent
                @drop.prevent.stop="dropOnPage(pageIndex, itemIndex)"
              >
                <span class="grip">⠿</span>
                <span class="chip-name">{{ sectionLabel(type) }}</span>
                <a-tag v-if="isRequired(type)" color="red">필수</a-tag>
                <span class="chip-source">{{ sectionSourceLabel(sectionMeta[type]?.source ?? '') }}</span>
              </div>
              <p v-if="page.items.length === 0" class="drop-hint">여기에 끌어다 놓으세요</p>
            </div>
          </section>

          <a-button block :disabled="!editable" class="add-page" @click="addPage">＋ 페이지 추가</a-button>
        </div>
      </div>
    </a-spin>

    <a-modal v-model:open="previewOpen" title="지원자 화면 미리보기" :footer="null" width="640px">
      <a-spin :spinning="previewLoading">
        <div v-if="preview">
          <p class="preview-subject">{{ preview.jobPostingTitle }}</p>
          <div v-for="page in preview.pages" :key="page.pageNo" class="preview-page">
            <h4>{{ page.pageNo }}. {{ page.title }}</h4>
            <p v-if="page.description" class="preview-page-description">{{ page.description }}</p>
            <ul>
              <li v-for="item in page.items" :key="item.sectionType">
                {{ sectionLabel(item.sectionType) }}
                <a-tag v-if="item.required" color="red">필수</a-tag>
              </li>
            </ul>
          </div>
          <p v-if="preview.pages.length === 0" class="preview-empty">표시할 페이지가 없습니다.</p>
        </div>
      </a-spin>
    </a-modal>
  </div>
</template>

<style scoped lang="scss">
.layout-tab {
  padding-top: 18px;
}
.tab-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}
.tab-description {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 13px;
}
.tab-actions {
  display: flex;
  gap: 8px;
  flex: none;
}
.tab-alert {
  margin-bottom: 14px;
}

.layout-grid {
  display: grid;
  grid-template-columns: 232px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.palette {
  background: var(--app-bg-surface);
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius);
  padding: 14px;
}
.palette-title {
  margin: 0 0 8px;
  font-size: 12.5px;
  font-weight: 700;
  color: var(--app-text-secondary);
  display: flex;
  align-items: center;
  gap: 6px;

  &.muted {
    margin-top: 18px;
  }
}
.palette-count {
  background: var(--app-bg-muted);
  color: var(--app-text-muted);
  border-radius: 999px;
  padding: 1px 8px;
  font-size: 11.5px;

  &.warn {
    background: #fff4e5;
    color: var(--app-color-warning);
  }
}
.palette-empty {
  margin: 0;
  font-size: 12px;
  color: var(--app-text-muted);
}

.chip {
  display: flex;
  align-items: center;
  gap: 7px;
  background: var(--app-bg-surface);
  border: 1px solid var(--app-border-strong);
  border-radius: var(--app-border-radius-sm);
  padding: 7px 9px;
  font-size: 13px;
  margin-bottom: 6px;
  cursor: grab;

  &.unplaced {
    border-style: dashed;
    border-color: var(--app-color-warning);
    background: #fffdf7;
  }

  &.disabled {
    background: var(--app-bg-muted);
    color: var(--app-text-muted);
    cursor: default;
  }
}
.grip {
  color: var(--app-border-strong);
  letter-spacing: -1px;
}
.chip-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chip-source {
  font-size: 11px;
  color: var(--app-text-muted);
}

.canvas {
  min-width: 0;
}
.page-card {
  background: var(--app-bg-surface);
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius);
  padding: 12px;
  margin-bottom: 10px;
}
.page-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.page-no {
  flex: none;
  width: 22px;
  height: 22px;
  border-radius: var(--app-border-radius-sm);
  background: var(--app-color-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: grid;
  place-items: center;
}
.page-title-input {
  flex: 1;
  min-width: 0;
}
.page-count {
  flex: none;
  font-size: 12px;
  color: var(--app-text-muted);
}
.page-description-input {
  margin-bottom: 8px;
}
.page-body {
  min-height: 44px;
  border: 1px dashed var(--app-border-default);
  border-radius: var(--app-border-radius-sm);
  padding: 8px 8px 2px;
}
.drop-hint {
  margin: 0 0 6px;
  text-align: center;
  font-size: 12px;
  color: var(--app-text-muted);
}
.add-page {
  border-style: dashed;
}

.preview-subject {
  margin: 0 0 12px;
  font-weight: 700;
}
.preview-page {
  border-left: 2px solid var(--app-color-primary);
  padding-left: 10px;
  margin-bottom: 14px;

  h4 {
    margin: 0 0 4px;
    font-size: 14px;
  }

  ul {
    margin: 0;
    padding-left: 18px;
    font-size: 13px;
  }
}
.preview-page-description {
  margin: 0 0 4px;
  font-size: 12.5px;
  color: var(--app-text-secondary);
}
.preview-empty {
  color: var(--app-text-muted);
}
</style>
