<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { adminJobPostingApi } from '@/api/adminJobPostingApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { QuestionTemplateItem } from '@/types/question'

const router = useRouter();
const loading = ref(false);
const templates = ref<QuestionTemplateItem[]>([]);
const page = ref(0);
const pageSize = 10;
const totalElements = ref(0);

const QuestionTemplatesCategory: Record<string, string> = {
  SELF_INTRODUCTION: '자기소개',
  GENERAL: '기본질문',
  JOB_SPECIFIC: '직무질문',
  ETC: '기타',
}

const QuestionTemplatesRequired: Record<string, string> = {
  true: '필수',
  false: '선택'
}

const columns = [
  { title: '템플릿명', dataIndex: 'title', key: 'title', width: 250 },
  { title: '질문', key: 'questionText', width: 300},
  { title: '카테고리', key: 'category', width: 100, align: 'center'},
  { title: '필수여부', key: 'defaultRequired', width: 100, align: 'center'},
  { title: '사용여부', key: 'active', width: 100, align: 'center'},
]

const pagination = computed(() => ({
  current: page.value + 1,
  pageSize,
  total: totalElements.value,
  showSizeChanger: false,
}))

const loadQuestionTemplates = async () => {
  loading.value = true
  try {
    const response = await adminJobPostingApi.getQuestionTemplates(page.value, pageSize)
    templates.value = response.data.data.content
    totalElements.value = response.data.data.totalElements
  } catch (error) {
    message.error(getApiErrorMessage(error, '질문 템플릿을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

const changeQuestionTemplateActive = async (template: QuestionTemplateItem) => {
  loading.value = true
  try {
    template.active ? await adminJobPostingApi.setQuestionActive(template.templateId)
    : await adminJobPostingApi.setQuestionDeactive(template.templateId)
  } catch (error) {
    message.error(getApiErrorMessage(error, '사용여부가 변경되지 않았습니다.'));
  } finally {
    await loadQuestionTemplates();
    loading.value = false
  }
}

const handleTableChange = (nextPagination: { current?: number }) => {
  page.value = (nextPagination.current ?? 1) - 1
  void loadQuestionTemplates()
}

const goToDetail = (record: QuestionTemplateItem) => {
  void router.push({ name: 'AdminJobPostingQuestionTemplateEdit', params: { id: record.templateId } })
}

const goToCreate = () => {
  void router.push({ name: 'AdminJobPostingQuestionTemplateEdit' })
}

const changeActive = (record: QuestionTemplateItem) => {
  changeQuestionTemplateActive(record);
}

onMounted(loadQuestionTemplates)
</script>

<template>
  <div class="job-posting-list">
    <header class="page-header">
      <div>
        <h2 class="page-title">질문 템플릿 관리</h2>
        <p class="page-description">자주 사용하는 질문을 템플릿을 관리할 수 있습니다.</p>
      </div>
      <a-button type="primary" @click="goToCreate">질문 템플릿 등록</a-button>
    </header>

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
          <span @click="goToDetail(record)" class="questionId-link">{{ record.questionText }}</span>
        </template>
        <template v-if="column.key === 'category'">
          {{ QuestionTemplatesCategory[record.category] ?? record.category }}
        </template>
        <template v-if="column.key === 'defaultRequired'">
          {{ QuestionTemplatesRequired[record.defaultRequired] ?? record.defaultRequired }}
        </template>
        <template v-else-if="column.key === 'active'">
          <a-switch class="switch-button" v-model:checked="record.active" @change="changeActive(record)"/>
        </template>
      </template>
    </a-table>
    <p class="page-description">질문을 누르면 수정이 가능합니다.</p>
  </div>
</template>

<style scoped>
.job-posting-list {
  padding: 24px;
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
:deep(.ant-table-row) {
  cursor: pointer;
}
:deep(.questionId-link) {
  font-weight: 500;
  &:hover {
    color: dodgerblue;
    text-decoration: underline;
  }
}
</style>
