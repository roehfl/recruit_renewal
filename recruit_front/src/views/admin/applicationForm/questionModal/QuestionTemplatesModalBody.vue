<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import type { QuestionTemplateItem, QuestionForm } from '@/types/question';
import { adminJobPostingApi } from '@/api/adminJobPostingApi';
import { message } from 'ant-design-vue';
import { getApiErrorMessage } from '@/api/apiError';
import AddQuestionBody from './AddQuestionBody.vue'

const loading = ref(false);
const cilckTemplate = ref(false);
const page = ref(0);
const pageSize = 6;
const totalElements = ref(0);
const selectedTemplate = ref<QuestionForm | null>(null);

const pagination = computed(() => ({
  current: page.value + 1,
  pageSize,
  total: totalElements.value,
  showSizeChanger: false,
}))

const handleTableChange = (nextPagination: { current?: number }) => {
  page.value = (nextPagination.current ?? 1) - 1
  void loadQuestionTemplates()
}

const templates = ref<QuestionTemplateItem[]>([]);

const QuestionTemplatesCategory: Record<string, string> = {
  SELF_INTRODUCTION: '자기소개',
  GENERAL: '기본질문',
  JOB_SPECIFIC: '직무질문',
  ETC: '기타',
}

const columns = [
  { title: '템플릿명', dataIndex: 'title', key: 'title', width: 220},
  { title: '질문', key: 'questionText', width: 300},
  { title: '카테고리', key: 'category', width: 100, align: 'center'},
  { title: '추가', key: 'add', width: 100, align: 'center'},
]

const emit = defineEmits<{
  (e: 'update:opne', value: boolean): void
  (e: 'success', data: QuestionForm): void
}>()

const handleCancel = (): void => {
  cilckTemplate.value = false;
  selectedTemplate.value = null;
  emit('update:opne', false)
}

const handleRowClick = (record: QuestionTemplateItem): void => {
  cilckTemplate.value = true;
  selectedTemplate.value = {
    category: record.category,
    required: record.defaultRequired,
    answerType: record.answerType,
    maxLength: record.defaultMaxLength,
    minLength: 0,
    questionText: record.questionText,
    helperText: record.helperText
  }
}

const handleResetAdd = (): void => {
  cilckTemplate.value = false;
  selectedTemplate.value = null;
}

const handleSubmit = (data: QuestionForm): void => {
  emit('success', data)
  cilckTemplate.value = false
  selectedTemplate.value = null
  emit('update:opne', false)
}

const loadQuestionTemplates = async () => {
  loading.value = true
  try {
    const response = await adminJobPostingApi.getQuestionTemplatesActive(page.value, pageSize, true)
    templates.value = response.data.data.content
    totalElements.value = response.data.data.totalElements
  } catch (error) {
    message.error(getApiErrorMessage(error, '질문 템플릿을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

onMounted( async () => {
  await loadQuestionTemplates()
})

</script>

<template>
  <div class="dm-modal">
    <div class="dm-header">
      <h2 class="dm-title">질문 템플릿 불러오기</h2>
      <span>템플릿을 클릭하여 추가 할 수 있습니다</span>
      <button class="dm-close" type="button" aria-label="닫기" @click="handleCancel">✕</button>
    </div>

    <div class="dm-body">
      <div v-if="!cilckTemplate">
        <a-table
          :columns="columns"
          :data-source="templates"
          :loading="loading"
          :pagination="pagination"
          row-key="templateId"
          @change="handleTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'questionText'">
              <span>{{ record.questionText }}</span>
            </template>
            <template v-if="column.key === 'category'">
              {{ QuestionTemplatesCategory[record.category] ?? record.category }}
            </template>
            <template v-if="column.key === 'add'">
              <a-button class="questionId-link" @click="handleRowClick(record)">+ 추가</a-button>
            </template>
          </template>
        </a-table>
      </div>

      <AddQuestionBody
        v-else
        :initial-values="selectedTemplate"
        submit-label="추가"
        :show-cancel="true"
        @submit="handleSubmit"
        @cancel="handleResetAdd"
      />
    </div>
  </div>
</template>

<style scoped>

.dm-modal {
  font-family: 'Noto Sans KR', 'Noto Sans CJK KR', 'Apple SD Gothic Neo', sans-serif;
  color: #1f2937;
}

.dm-header {
  position: relative;
  padding: 32px 40px 24px;
  border-bottom: 1px solid #eef1ee;
}

.dm-title {
  margin: 0;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #1f2937;
}

.dm-close {
  position: absolute;
  top: 28px;
  right: 32px;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 8px;
  background: #f5f7fa;
  color: #6b7280;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  transition:
    background-color 0.15s ease,
    color 0.15s ease;
}

.dm-close:hover {
  background: #eaf8f3;
  color: #0f4726;
}

.dm-body {
  max-height: 560px;
  overflow-y: auto;
  padding: 28px 40px 36px;
}

</style>