<template>
  <section class="faq-page">
    <div class="page-inner">
      <ApplicantBreadcrumb />

      <h1 class="page-title">자주 묻는 질문</h1>
      <p class="page-desc">채용 관련해 자주 문의하시는 내용을 정리했습니다. 그 외 문의사항은 <strong class="desc-email">hr@shinyoung.com</strong> 으로 연락 바랍니다.</p>

      <p v-if="!loading && categories.length === 0" class="empty-message">
        등록된 FAQ가 없습니다.
      </p>

      <div v-else class="faq-body">
        <!-- 좌측: 카테고리 -->
        <aside class="category-panel">
          <ul class="category-list">
            <li v-for="category in categories" :key="category.id" class="category-item">
              <button
                type="button"
                class="category-button"
                :class="{ active: category.id === selectedCategoryId }"
                :aria-current="category.id === selectedCategoryId ? 'true' : undefined"
                @click="selectCategory(category.id)"
              >
                <span class="category-name">{{ category.name }}</span>
                <span class="category-count">{{ category.faqs.length }}</span>
              </button>
            </li>
          </ul>
        </aside>

        <!-- 우측: Q 아코디언 -->
        <section v-if="selectedCategory" class="qa-panel">
          <div class="qa-header">
            <h2 class="qa-title">{{ selectedCategory.name }}</h2>
            <span class="qa-total">총 {{ selectedCategory.faqs.length }}건</span>
          </div>

          <ul class="qa-list">
            <li v-for="faq in selectedCategory.faqs" :key="faq.id" class="qa-item">
              <button
                type="button"
                class="qa-question"
                :class="{ open: isOpen(faq.id) }"
                :aria-expanded="isOpen(faq.id)"
                @click="toggle(faq.id)"
              >
                <span class="qa-mark">Q</span>
                <span class="qa-question-text">{{ faq.question }}</span>
                <span class="qa-arrow" aria-hidden="true">
                  <DownOutlined />
                </span>
              </button>

              <div v-if="isOpen(faq.id)" class="qa-answer">
                <span class="qa-mark qa-mark--answer">A</span>
                <p class="qa-answer-text">{{ faq.answer }}</p>
              </div>
            </li>
          </ul>
        </section>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { DownOutlined } from '@ant-design/icons-vue'

import ApplicantBreadcrumb from '@/views/applicant/ApplicantBreadcrumb.vue'
import { faqApi } from '@/api/faqApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { PublicFaqCategory } from '@/types/faq'

const categories = ref<PublicFaqCategory[]>([])
const selectedCategoryId = ref<number | null>(null)
const openFaqIds = ref<Set<number>>(new Set())
const loading = ref<boolean>(true)

const selectedCategory = computed<PublicFaqCategory | undefined>(() => {
  return categories.value.find((category) => category.id === selectedCategoryId.value)
})

const loadFaqs = async (): Promise<void> => {
  try {
    const { data } = await faqApi.fetchFaqs()
    categories.value = data.data ?? []
    selectedCategoryId.value = categories.value[0]?.id ?? null
  } catch (error) {
    message.error(getApiErrorMessage(error, 'FAQ를 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

/* 카테고리를 바꾸면 이전 카테고리에서 펼쳐둔 항목은 닫는다. */
const selectCategory = (categoryId: number): void => {
  selectedCategoryId.value = categoryId
  openFaqIds.value = new Set()
}

const isOpen = (faqId: number): boolean => {
  return openFaqIds.value.has(faqId)
}

/* 여러 항목을 동시에 펼칠 수 있다. Set 재할당으로 반응성을 확보한다. */
const toggle = (faqId: number): void => {
  const next = new Set(openFaqIds.value)

  if (next.has(faqId)) {
    next.delete(faqId)
  } else {
    next.add(faqId)
  }

  openFaqIds.value = next
}

onMounted(async () => {
  await loadFaqs()
})
</script>

<style scoped lang="scss">
.faq-page {
  width: 100%;
  background: var(--app-bg-surface);
}

.page-inner {
  max-width: var(--app-frame-width);
  margin: 0 auto;
  padding: 42px var(--app-frame-padding-x) 88px;
}

.page-title {
  margin: 0 0 10px;
  font-size: 38px;
  font-weight: 800;
  letter-spacing: -1px;
}

.page-desc {
  margin: 0 0 38px;
  font-size: 15px;
  color: var(--app-text-secondary);
}

.desc-email {
  color: var(--app-text-primary);
  font-weight: 700;
}

.empty-message {
  padding: 80px 0;
  text-align: center;
  color: var(--app-text-muted);
}

.faq-body {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 40px;
  align-items: start;
}

/* ---------- 카테고리 ---------- */

.category-list {
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 2px solid var(--app-color-primary);
}

.category-item {
  border-bottom: 1px solid var(--app-border-default);
}

.category-button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 16px 14px;
  border: none;
  background: transparent;
  color: var(--app-text-secondary);
  font: inherit;
  font-size: 16px;
  text-align: left;
  cursor: pointer;

  &.active {
    background: var(--app-bg-selected);
    color: var(--app-color-primary);

    .category-name {
      font-weight: 700;
    }

    .category-count {
      color: var(--app-color-primary-emerald);
      font-weight: 600;
    }
  }
}

.category-count {
  font-size: 12px;
  color: var(--app-text-muted);
}

/* ---------- Q/A ---------- */

.qa-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}

.qa-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
}

.qa-total {
  font-size: 13px;
  color: var(--app-text-muted);
}

.qa-list {
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 2px solid var(--app-text-primary);
}

.qa-item {
  border-bottom: 1px solid var(--app-border-default);
}

.qa-question {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
  padding: 22px 8px;
  border: none;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;

  &.open {
    .qa-mark,
    .qa-question-text,
    .qa-arrow {
      color: var(--app-color-primary);
    }

    .qa-question-text {
      font-weight: 700;
    }

    .qa-arrow {
      transform: rotate(180deg);
    }
  }
}

.qa-mark {
  flex: none;
  width: 28px;
  font-size: 20px;
  font-weight: 800;
  line-height: 1;
  color: var(--app-text-muted);
}

.qa-question-text {
  flex: 1 1 auto;
  font-size: 17px;
  font-weight: 500;
  color: var(--app-text-primary);
}

.qa-arrow {
  flex: none;
  display: inline-flex;
  color: var(--app-text-muted);
  transition: transform 0.2s ease;
}

.qa-answer {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 24px 8px 30px;
  background: var(--app-bg-soft);
  border-top: 1px solid var(--app-border-subtle);
}

/* 답변 마크는 진행중 공고의 D-day와 같은 강조색을 쓴다. */
.qa-mark--answer {
  color: var(--app-color-warning);
}

.qa-answer-text {
  flex: 1 1 auto;
  margin: 0;
  font-size: 15px;
  line-height: 1.85;
  color: var(--app-text-primary);

  /* 답변은 평문이라 줄바꿈만 그대로 살린다(HTML 렌더링 없음). */
  white-space: pre-wrap;
}
</style>
