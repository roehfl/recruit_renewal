<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { ArrowDownOutlined, ArrowUpOutlined } from '@ant-design/icons-vue'

import { adminFaqApi } from '@/api/adminFaqApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { Faq, FaqCategory } from '@/types/faq'

const categories = ref<FaqCategory[]>([])
const faqs = ref<Faq[]>([])
const selectedCategoryId = ref<number | null>(null)

const categoryLoading = ref(false)
const faqLoading = ref(false)

const selectedCategory = computed<FaqCategory | undefined>(() =>
  categories.value.find((category) => category.id === selectedCategoryId.value),
)

const faqColumns = [
  { title: '순서', key: 'sortOrder', width: 64, align: 'center' as const },
  { title: '질문', key: 'question' },
  { title: '노출', key: 'active', width: 78, align: 'center' as const },
  { title: '관리', key: 'actions', width: 210, align: 'center' as const },
]

/* ---------- 조회 ---------- */

const loadCategories = async (): Promise<void> => {
  categoryLoading.value = true
  try {
    const response = await adminFaqApi.fetchCategories()
    categories.value = response.data.data

    /* 선택 중이던 카테고리가 사라졌으면 첫 항목으로 되돌린다. */
    if (!categories.value.some((category) => category.id === selectedCategoryId.value)) {
      selectedCategoryId.value = categories.value[0]?.id ?? null
    }
  } catch (error) {
    message.error(getApiErrorMessage(error, '카테고리 목록을 불러오지 못했습니다.'))
  } finally {
    categoryLoading.value = false
  }
}

const loadFaqs = async (): Promise<void> => {
  if (selectedCategoryId.value === null) {
    faqs.value = []
    return
  }

  faqLoading.value = true
  try {
    const response = await adminFaqApi.fetchFaqs(selectedCategoryId.value)
    faqs.value = response.data.data
  } catch (error) {
    message.error(getApiErrorMessage(error, 'FAQ 목록을 불러오지 못했습니다.'))
  } finally {
    faqLoading.value = false
  }
}

const selectCategory = async (categoryId: number): Promise<void> => {
  if (categoryId === selectedCategoryId.value) {
    return
  }

  selectedCategoryId.value = categoryId
  await loadFaqs()
}

/* ---------- 카테고리 폼 ---------- */

const categoryModalOpen = ref(false)
const categorySaving = ref(false)
const categoryEditingId = ref<number | null>(null)
const categoryForm = reactive({ name: '', active: true })

const openCategoryCreate = (): void => {
  categoryEditingId.value = null
  categoryForm.name = ''
  categoryForm.active = true
  categoryModalOpen.value = true
}

const openCategoryEdit = (category: FaqCategory): void => {
  categoryEditingId.value = category.id
  categoryForm.name = category.name
  categoryForm.active = category.active
  categoryModalOpen.value = true
}

const saveCategory = async (): Promise<void> => {
  if (!categoryForm.name.trim()) {
    message.warning('카테고리명을 입력해 주세요.')
    return
  }

  categorySaving.value = true
  try {
    const request = { name: categoryForm.name.trim(), active: categoryForm.active }

    if (categoryEditingId.value === null) {
      await adminFaqApi.createCategory(request)
      message.success('카테고리를 추가했습니다.')
    } else {
      await adminFaqApi.updateCategory(categoryEditingId.value, request)
      message.success('카테고리를 수정했습니다.')
    }

    categoryModalOpen.value = false
    await loadCategories()
  } catch (error) {
    message.error(getApiErrorMessage(error, '카테고리를 저장하지 못했습니다.'))
  } finally {
    categorySaving.value = false
  }
}

const deleteCategory = (category: FaqCategory): void => {
  Modal.confirm({
    title: '카테고리를 삭제할까요?',
    content: `"${category.name}" 카테고리가 지원자 화면에서 하위 FAQ와 함께 숨겨집니다.`,
    okText: '삭제',
    okType: 'danger',
    cancelText: '취소',
    onOk: async () => {
      try {
        await adminFaqApi.deleteCategory(category.id)
        message.success('카테고리를 삭제했습니다.')
        await loadCategories()
      } catch (error) {
        message.error(getApiErrorMessage(error, '카테고리를 삭제하지 못했습니다.'))
      }
    },
  })
}

/* ---------- FAQ 폼 ---------- */

const faqModalOpen = ref(false)
const faqSaving = ref(false)
const faqEditingId = ref<number | null>(null)
const faqForm = reactive({ categoryId: 0, question: '', answer: '', active: true })

const openFaqCreate = (): void => {
  if (selectedCategoryId.value === null) {
    message.warning('먼저 카테고리를 추가해 주세요.')
    return
  }

  faqEditingId.value = null
  faqForm.categoryId = selectedCategoryId.value
  faqForm.question = ''
  faqForm.answer = ''
  faqForm.active = true
  faqModalOpen.value = true
}

const openFaqEdit = (faq: Faq): void => {
  faqEditingId.value = faq.id
  faqForm.categoryId = faq.categoryId
  faqForm.question = faq.question
  faqForm.answer = faq.answer
  faqForm.active = faq.active
  faqModalOpen.value = true
}

const validateFaqForm = (): string | null => {
  if (!faqForm.categoryId) {
    return '카테고리를 선택해 주세요.'
  }
  if (!faqForm.question.trim()) {
    return '질문을 입력해 주세요.'
  }
  if (faqForm.question.trim().length > 500) {
    return '질문은 500자를 초과할 수 없습니다.'
  }
  if (!faqForm.answer.trim()) {
    return '답변을 입력해 주세요.'
  }
  return null
}

const saveFaq = async (): Promise<void> => {
  const validationMessage = validateFaqForm()
  if (validationMessage) {
    message.warning(validationMessage)
    return
  }

  faqSaving.value = true
  try {
    const request = {
      categoryId: faqForm.categoryId,
      question: faqForm.question.trim(),
      answer: faqForm.answer.trim(),
      active: faqForm.active,
    }

    if (faqEditingId.value === null) {
      await adminFaqApi.createFaq(request)
      message.success('FAQ를 추가했습니다.')
    } else {
      await adminFaqApi.updateFaq(faqEditingId.value, request)
      message.success('FAQ를 수정했습니다.')
    }

    faqModalOpen.value = false
    /* 카테고리를 옮겼거나 노출을 껐으면 카테고리별 건수도 바뀐다. */
    await Promise.all([loadCategories(), loadFaqs()])
  } catch (error) {
    message.error(getApiErrorMessage(error, 'FAQ를 저장하지 못했습니다.'))
  } finally {
    faqSaving.value = false
  }
}

const deleteFaq = (faq: Faq): void => {
  Modal.confirm({
    title: 'FAQ를 삭제할까요?',
    content: '지원자 화면에서 더 이상 노출되지 않습니다.',
    okText: '삭제',
    okType: 'danger',
    cancelText: '취소',
    onOk: async () => {
      try {
        await adminFaqApi.deleteFaq(faq.id)
        message.success('FAQ를 삭제했습니다.')
        await Promise.all([loadCategories(), loadFaqs()])
      } catch (error) {
        message.error(getApiErrorMessage(error, 'FAQ를 삭제하지 못했습니다.'))
      }
    },
  })
}

/* ---------- 정렬 ---------- */

/*
 * ↑↓는 로컬 배열에서 인접 항목과 자리를 바꾼 뒤 전체 id 순서를 그대로 서버에 보낸다.
 * reorder API가 배열 순서대로 sortOrder를 0..n-1로 정규화하므로 별도 저장 버튼이 없다.
 */
const moveItem = <T,>(items: T[], index: number, direction: -1 | 1): T[] | null => {
  const target = index + direction

  if (target < 0 || target >= items.length) {
    return null
  }

  const next = [...items]
  const [moving] = next.splice(index, 1)

  if (moving === undefined) {
    return null
  }

  next.splice(target, 0, moving)
  return next
}

const moveCategory = async (index: number, direction: -1 | 1): Promise<void> => {
  const reordered = moveItem(categories.value, index, direction)
  if (!reordered) {
    return
  }

  const previous = categories.value
  categories.value = reordered

  try {
    await adminFaqApi.reorderCategories(reordered.map((category) => category.id))
    await loadCategories()
  } catch (error) {
    categories.value = previous
    message.error(getApiErrorMessage(error, '카테고리 순서를 변경하지 못했습니다.'))
  }
}

const moveFaq = async (index: number, direction: -1 | 1): Promise<void> => {
  if (selectedCategoryId.value === null) {
    return
  }

  const reordered = moveItem(faqs.value, index, direction)
  if (!reordered) {
    return
  }

  const previous = faqs.value
  faqs.value = reordered

  try {
    await adminFaqApi.reorderFaqs(
      selectedCategoryId.value,
      reordered.map((faq) => faq.id),
    )
    await loadFaqs()
  } catch (error) {
    faqs.value = previous
    message.error(getApiErrorMessage(error, 'FAQ 순서를 변경하지 못했습니다.'))
  }
}

onMounted(async () => {
  await loadCategories()
  await loadFaqs()
})
</script>

<template>
  <div class="faq-manage">
    <header class="page-header">
      <h1 class="page-title">FAQ 관리</h1>
      <p class="page-desc">카테고리와 질문/답변을 등록하고 노출 순서를 조정합니다.</p>
    </header>

    <div class="manage-body">
      <!-- 좌: 카테고리 -->
      <section class="panel category-panel">
        <div class="panel-header">
          <span class="panel-title">카테고리</span>
          <a-button type="primary" size="small" @click="openCategoryCreate">+ 추가</a-button>
        </div>

        <a-spin :spinning="categoryLoading">
          <p v-if="categories.length === 0" class="empty-message">등록된 카테고리가 없습니다.</p>

          <ul v-else class="category-list">
            <li
              v-for="(category, index) in categories"
              :key="category.id"
              class="category-row"
              :class="{ selected: category.id === selectedCategoryId }"
              @click="selectCategory(category.id)"
            >
              <span class="category-name" :class="{ inactive: !category.active }">
                {{ category.name }}
              </span>

              <a-tag v-if="!category.active" class="row-tag">비노출</a-tag>
              <span v-else class="category-count">{{ category.faqCount }}건</span>

              <span class="row-actions" @click.stop>
                <a-button
                  size="small"
                  :disabled="index === 0"
                  aria-label="위로 이동"
                  @click="moveCategory(index, -1)"
                >
                  <ArrowUpOutlined />
                </a-button>
                <a-button
                  size="small"
                  :disabled="index === categories.length - 1"
                  aria-label="아래로 이동"
                  @click="moveCategory(index, 1)"
                >
                  <ArrowDownOutlined />
                </a-button>
                <a-button size="small" @click="openCategoryEdit(category)">수정</a-button>
                <a-button size="small" danger @click="deleteCategory(category)">삭제</a-button>
              </span>
            </li>
          </ul>
        </a-spin>
      </section>

      <!-- 우: FAQ -->
      <section class="panel">
        <div class="panel-header">
          <span class="panel-title">
            {{ selectedCategory?.name ?? 'FAQ' }}
            <span class="panel-subtitle">— 질문/답변 {{ faqs.length }}건</span>
          </span>
          <a-button type="primary" :disabled="selectedCategoryId === null" @click="openFaqCreate">
            + FAQ 추가
          </a-button>
        </div>

        <a-table
          :columns="faqColumns"
          :data-source="faqs"
          :loading="faqLoading"
          :pagination="false"
          row-key="id"
          size="middle"
        >
          <template #bodyCell="{ column, record, index }">
            <template v-if="column.key === 'sortOrder'">
              <span class="row-index">{{ index + 1 }}</span>
            </template>

            <template v-else-if="column.key === 'question'">
              <div class="faq-question" :class="{ inactive: !record.active }">
                {{ record.question }}
              </div>
              <div class="faq-answer-preview">{{ record.answer }}</div>
            </template>

            <template v-else-if="column.key === 'active'">
              <a-tag :color="record.active ? 'green' : 'default'">
                {{ record.active ? '노출' : '비노출' }}
              </a-tag>
            </template>

            <template v-else-if="column.key === 'actions'">
              <span class="row-actions">
                <a-button
                  size="small"
                  :disabled="index === 0"
                  aria-label="위로 이동"
                  @click="moveFaq(index, -1)"
                >
                  <ArrowUpOutlined />
                </a-button>
                <a-button
                  size="small"
                  :disabled="index === faqs.length - 1"
                  aria-label="아래로 이동"
                  @click="moveFaq(index, 1)"
                >
                  <ArrowDownOutlined />
                </a-button>
                <a-button size="small" @click="openFaqEdit(record)">수정</a-button>
                <a-button size="small" danger @click="deleteFaq(record)">삭제</a-button>
              </span>
            </template>
          </template>
        </a-table>
      </section>
    </div>

    <!-- 카테고리 모달 -->
    <a-modal
      v-model:open="categoryModalOpen"
      :title="categoryEditingId === null ? '카테고리 추가' : '카테고리 수정'"
      :confirm-loading="categorySaving"
      ok-text="저장"
      cancel-text="취소"
      @ok="saveCategory"
    >
      <a-form layout="vertical">
        <a-form-item label="카테고리명" required>
          <a-input v-model:value="categoryForm.name" :maxlength="100" placeholder="예: 지원서 관련" />
        </a-form-item>

        <a-form-item>
          <a-switch v-model:checked="categoryForm.active" />
          <span class="switch-label">지원자 화면에 노출</span>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- FAQ 모달 -->
    <a-modal
      v-model:open="faqModalOpen"
      :title="faqEditingId === null ? 'FAQ 추가' : 'FAQ 수정'"
      :confirm-loading="faqSaving"
      :width="600"
      ok-text="저장"
      cancel-text="취소"
      @ok="saveFaq"
    >
      <a-form layout="vertical">
        <a-form-item label="카테고리" required>
          <a-select v-model:value="faqForm.categoryId">
            <a-select-option
              v-for="category in categories"
              :key="category.id"
              :value="category.id"
            >
              {{ category.name }}
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item label="질문 (Q)" required>
          <a-input
            v-model:value="faqForm.question"
            :maxlength="500"
            placeholder="지원서는 어떻게 작성하나요?"
          />
        </a-form-item>

        <a-form-item
          label="답변 (A)"
          extra="평문으로 저장되며 줄바꿈은 그대로 노출됩니다. HTML 태그는 사용할 수 없습니다."
          required
        >
          <a-textarea v-model:value="faqForm.answer" :rows="7" />
        </a-form-item>

        <a-form-item>
          <a-switch v-model:checked="faqForm.active" />
          <span class="switch-label">지원자 화면에 노출</span>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped lang="scss">
.page-header {
  margin-bottom: 18px;
}

.page-title {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 700;
}

.page-desc {
  margin: 0;
  font-size: 13px;
  color: var(--app-text-secondary);
}

.manage-body {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.panel {
  background: var(--app-bg-surface);
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius);
  box-shadow: var(--app-shadow-soft);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--app-border-default);
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
}

.panel-subtitle {
  font-weight: 400;
  color: var(--app-text-muted);
}

.empty-message {
  margin: 0;
  padding: 40px 0;
  text-align: center;
  color: var(--app-text-muted);
}

/* ---------- 카테고리 ---------- */

.category-list {
  margin: 0;
  padding: 6px;
  list-style: none;
}

.category-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 10px;
  border-radius: 6px;
  cursor: pointer;

  &:hover {
    background: var(--app-bg-muted);
  }

  &.selected {
    background: var(--app-bg-selected);

    .category-name {
      color: var(--app-color-primary);
      font-weight: 600;
    }
  }
}

.category-name {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;

  &.inactive {
    color: var(--app-text-muted);
    text-decoration: line-through;
  }
}

.category-count {
  flex: none;
  font-size: 11px;
  color: var(--app-text-muted);
}

.row-tag {
  flex: none;
  margin-inline-end: 0;
  font-size: 10px;
}

.row-actions {
  flex: none;
  display: inline-flex;
  gap: 4px;
  white-space: nowrap;
}

/* ---------- FAQ 목록 ---------- */

.row-index {
  color: var(--app-text-muted);
}

.faq-question {
  margin-bottom: 3px;
  font-weight: 500;

  &.inactive {
    color: var(--app-text-muted);
  }
}

/* 목록에서는 답변을 한 줄 미리보기로만 보여준다(전문은 수정 모달에서 확인). */
.faq-answer-preview {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--app-text-muted);
}

.switch-label {
  margin-left: 9px;
  font-size: 13px;
  color: var(--app-text-secondary);
}
</style>
