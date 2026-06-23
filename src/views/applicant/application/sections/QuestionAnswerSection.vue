<template>
  <div class="section-body">
    <div v-if="items.length === 0" class="empty-box">
      <p class="empty-title">등록된 질문이 없습니다.</p>
      <p class="empty-desc">이 공고에는 작성할 자기소개 질문이 없습니다.</p>
    </div>

    <div v-else class="card-list">
      <div v-for="item in items" :key="item.questionId" class="item-card">
        <div class="q-head">
          <span class="category-chip">{{ categoryLabel(item.category) }}</span>
          <span v-if="item.required" class="req-badge">필수</span>
        </div>

        <p class="q-text">{{ item.questionText }}<em v-if="item.required"> *</em></p>
        <p v-if="item.helperText" class="q-helper">{{ item.helperText }}</p>

        <a-input
          v-if="item.answerType === 'SHORT_TEXT'"
          v-model:value="item.answerText"
          :maxlength="item.maxLength ?? 5000"
          show-count
          placeholder="답변을 입력하세요."
        />
        <a-textarea
          v-else
          v-model:value="item.answerText"
          :maxlength="item.maxLength ?? 5000"
          :rows="4"
          show-count
          placeholder="답변을 입력하세요."
        />

        <p v-if="item.minLength" class="q-min">최소 {{ item.minLength }}자</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { questionAnswerApi } from '@/api/application/sections/questionAnswerApi'
import { getApiErrorMessage } from '@/api/apiError'
import { logClientEvent } from '@/common/clientEventLogger'
import type { SectionComponentProps } from '@/types/application'
import type {
  QuestionCategory,
  ApplicationQuestionItem,
  ApplicationQuestionResponse,
  ApplicationAnswerReplaceRequest,
} from '@/types/application/sections/questionAnswer'

const props = defineProps<SectionComponentProps>()

const loading = ref(false)
const items = reactive<ApplicationQuestionItem[]>([])

const CATEGORY_LABELS: Record<QuestionCategory, string> = {
  SELF_INTRODUCTION: '자기소개',
  GENERAL: '일반',
  JOB_SPECIFIC: '직무',
  ETC: '기타',
}

function categoryLabel(category: QuestionCategory): string {
  return CATEGORY_LABELS[category] ?? category
}

function setItems(list: ApplicationQuestionResponse[]) {
  const mapped = list.map((row) => ({
    questionId: row.questionId,
    questionText: row.questionText,
    helperText: row.helperText ?? undefined,
    category: row.category,
    answerType: row.answerType,
    required: row.required,
    minLength: row.minLength ?? undefined,
    maxLength: row.maxLength ?? undefined,
    sortOrder: row.sortOrder,
    answerId: row.answerId ?? undefined,
    answerText: row.answerText ?? '',
  }))
  mapped.sort((a, b) => a.sortOrder - b.sortOrder)
  items.splice(0, items.length, ...mapped)
}

function buildPayload(): ApplicationAnswerReplaceRequest {
  return {
    answers: items
      .filter((item) => item.answerText.trim().length > 0)
      .map((item) => ({ questionId: item.questionId, answerText: item.answerText })),
  }
}

function validate(): boolean {
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (!item) continue
    const text = item.answerText.trim()
    if (item.required && !text) {
      message.warning(`'${item.questionText}'은(는) 필수 답변입니다.`)
      return false
    }
    if (text && item.minLength && text.length < item.minLength) {
      message.warning(`'${item.questionText}'은(는) 최소 ${item.minLength}자 이상 입력하세요.`)
      return false
    }
    if (item.maxLength && item.answerText.length > item.maxLength) {
      message.warning(`'${item.questionText}'은(는) 최대 ${item.maxLength}자까지 입력할 수 있습니다.`)
      return false
    }
  }
  return true
}

async function loadQuestions() {
  loading.value = true
  try {
    const result = await questionAnswerApi.getApplicationsQuestions(props.applicationId)
    setItems(result.data.data ?? [])
  } finally {
    loading.value = false
  }
}

async function saveDraft() {
  loading.value = true
  try {
    const result = await questionAnswerApi.replaceApplicationsAnswers(props.applicationId, buildPayload())
    setItems(result.data.data ?? [])
    return result.data.data
  } catch (error) {
    logClientEvent({
      eventType: 'APPLICATION_DRAFT_SAVE_FAILED',
      severity: 'INFO',
      pageCode: 'APPLICATION_FORM_QUESTION_ANSWER',
      operation: 'SAVE_DRAFT_QUESTION_ANSWER',
      applicationId: props.applicationId,
      message: 'APPLICATION_DRAFT_SAVE_FAILED',
    })
    throw new Error(getApiErrorMessage(error, '자기소개 답변 저장에 실패했습니다.'))
  } finally {
    loading.value = false
  }
}

function validateBeforeSubmit(): boolean {
  return validate()
}

onMounted(() => {
  loadQuestions()
})

defineExpose({ saveDraft, validateBeforeSubmit })
</script>

<style scoped>
.section-body {
  margin: 24px;
}
.card-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.item-card {
  border: 1px solid #eef1ee;
  border-radius: 12px;
  background: #fff;
  padding: 16px 18px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.q-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.category-chip {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 9px;
  border-radius: 6px;
  background: #f4f8f0;
  color: #536d2f;
  font-size: 12px;
  font-weight: 800;
}
.req-badge {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: 4px;
  background: #fff2f0;
  border: 1px solid #ffccc7;
  color: #ff4d4f;
  font-size: 12px;
  font-weight: 600;
}
.q-text {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 700;
  color: #1f2937;
  line-height: 1.5;
}
.q-helper {
  margin: 0 0 12px;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
}
.q-min {
  margin: 6px 0 0;
  font-size: 12px;
  color: #9ca3af;
}
.empty-box {
  padding: 34px 20px;
  text-align: center;
  border: 1px dashed #d9d9d9;
  border-radius: 10px;
  background: #fafafa;
}
.empty-title {
  margin: 0 0 4px;
  color: #6b7280;
  font-size: 14px;
  font-weight: 600;
}
.empty-desc {
  margin: 0;
  color: #9ca3af;
  font-size: 13px;
}
em {
  color: #ff4d4f;
  font-style: normal;
}
:deep(.ant-input),
:deep(.ant-input-affix-wrapper),
:deep(.ant-input-textarea) {
  width: 100%;
}
</style>
