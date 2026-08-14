<template>
  <div class="section-body">
    <!-- <a-checkbox v-model:checked="notApplicable" class="na-checkbox">
      어학 성적 없음 (해당 사항 없음)
    </a-checkbox> -->

    <div v-if="notApplicable" class="na-box">어학 성적 없음으로 표시되었습니다.</div>

    <template v-else>
      <div class="card-list">
        <div v-for="(item, index) in items" :key="index" class="item-card">
          <div class="item-card-head">
            <span class="num-pill">어학 {{ index + 1 }}</span>
            <button type="button" class="remove-btn" @click="removeItem(index)">
              <DeleteOutlined /> 삭제
            </button>
          </div>

          <table class="field-table">
            <colgroup>
              <col style="width: 14%" /><col style="width: 36%" />
              <col style="width: 14%" /><col style="width: 36%" />
            </colgroup>
            <tbody>
              <tr>
                <th>언어<em> *</em></th>
                <td><a-input v-model:value="item.languageName" placeholder="예) 영어" /></td>
                <th>시험명<em> *</em></th>
                <td><a-input v-model:value="item.testName" placeholder="예) TOEIC" /></td>
              </tr>
              <tr>
                <th>점수/등급</th>
                <td><a-input v-model:value="item.scoreOrGrade" placeholder="예) 950점 / 1급 / Level 7" /></td>
                <th>회화능력</th>
                <td>
                  <a-select
                    v-model:value="item.conversationalAbility"
                    :options="conversationOptions"
                    placeholder="선택"
                    allow-clear
                  />
                </td>
              </tr>
              <tr>
                <th>응시일자<em> *</em></th>
                <td><a-date-picker v-model:value="item.examDate" value-format="YYYY-MM-DD" /></td>
                <th>유효기간</th>
                <td><a-date-picker v-model:value="item.expiredDate" value-format="YYYY-MM-DD" /></td>
              </tr>
              <tr>
                <th>주관기관</th>
                <td colspan="3">
                  <a-input v-model:value="item.issuingOrganization" placeholder="예) ETS / 한국산업인력공단" />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-if="items.length === 0" class="empty-box">
        <p class="empty-title">등록된 어학 성적이 없습니다.</p>
        <p class="empty-desc">아래 버튼으로 어학 성적을 추가하세요.</p>
      </div>

      <button type="button" class="add-btn" @click="addItem">
        <PlusOutlined /> 어학 성적 추가
      </button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { languageApi } from '@/api/application/sections/languageApi'
import { commonCodeApi } from '@/api/commonApi'
import { getApiErrorMessage } from '@/api/apiError'
import { logClientEvent } from '@/common/clientEventLogger'
import type { SectionComponentProps } from '@/types/application'
import type { LanguageItem, LanguageResponse, LanguageReplaceRequest } from '@/types/application/sections/language'
import type { CommonCodeItems } from '@/types/commonCode'

const props = defineProps<SectionComponentProps>()

const loading = ref(false)
const notApplicable = ref(false)
const items = reactive<LanguageItem[]>([])

const conversationList = ref<CommonCodeItems[]>([])
const conversationOptions = computed(() =>
  conversationList.value.map((code) => ({ value: code.code, label: code.displayName })),
)

function createEmptyItem(): LanguageItem {
  return {
    languageName: '',
    testName: '',
    scoreOrGrade: '',
    conversationalAbility: undefined,
    examDate: '',
    expiredDate: '',
    issuingOrganization: '',
  }
}

function setItems(list: LanguageResponse[]) {
  items.splice(
    0,
    items.length,
    ...list.map((row) => ({
      languageId: row.languageId,
      languageName: row.languageName,
      testName: row.testName,
      scoreOrGrade: row.scoreOrGrade ?? '',
      conversationalAbility: row.conversationalAbility ?? undefined,
      examDate: row.examDate ?? '',
      expiredDate: row.expiredDate ?? '',
      issuingOrganization: row.issuingOrganization ?? '',
    })),
  )
}

function addItem() {
  items.push(createEmptyItem())
}

function removeItem(index: number) {
  items.splice(index, 1)
}

function buildPayload(): LanguageReplaceRequest {
  if (notApplicable.value) return { languages: [] }
  return {
    languages: items.map((item, index) => ({
      languageName: item.languageName,
      testName: item.testName,
      scoreOrGrade: item.scoreOrGrade || undefined,
      conversationalAbility: item.conversationalAbility || undefined,
      examDate: item.examDate,
      expiredDate: item.expiredDate || undefined,
      issuingOrganization: item.issuingOrganization || undefined,
      sortOrder: index,
    })),
  }
}

function validate(): boolean {
  if (notApplicable.value) return true
  if (items.length === 0) {
    if (props.section.required) {
      message.warning("어학 성적을 추가하거나 '어학 성적 없음'을 선택하세요.")
      return false
    }
    return true
  }
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (!item) continue
    if (!item.languageName || !item.testName || !item.examDate) {
      message.warning(`어학 ${i + 1}: 언어, 시험명, 응시일자는 필수입니다.`)
      return false
    }
  }
  return true
}

async function loadLanguages() {
  loading.value = true
  try {
    const result = await languageApi.getApplicationsLanguages(props.applicationId)
    setItems(result.data.data ?? [])
  } finally {
    loading.value = false
  }
}

async function loadConversationCodes() {
  const result = await commonCodeApi.getCommonCodes('LANGUAGE_CONVERSATION')
  conversationList.value = result.data.data ?? []
}

async function saveDraft() {
  if (!validate()) throw new Error('입력값을 확인해주세요.')
  loading.value = true
  try {
    const result = await languageApi.replaceApplicationsLanguages(props.applicationId, buildPayload())
    setItems(result.data.data ?? [])
    return result.data.data
  } catch (error) {
    logClientEvent({
      eventType: 'APPLICATION_DRAFT_SAVE_FAILED',
      severity: 'INFO',
      pageCode: 'APPLICATION_FORM_LANGUAGE',
      operation: 'SAVE_DRAFT_LANGUAGE',
      applicationId: props.applicationId,
      message: 'APPLICATION_DRAFT_SAVE_FAILED',
    })
    throw new Error(getApiErrorMessage(error, '어학 정보 저장에 실패했습니다.'))
  } finally {
    loading.value = false
  }
}

function validateBeforeSubmit(): boolean {
  return validate()
}

onMounted(() => {
  loadLanguages()
  loadConversationCodes()
})

defineExpose({ saveDraft, validateBeforeSubmit })
</script>

<style scoped>
.section-body {
  margin: 24px;
}
.na-checkbox {
  margin-bottom: 18px;
  font-weight: 600;
  color: #1f2937;
}
.na-checkbox :deep(.ant-checkbox-checked .ant-checkbox-inner) {
  background-color: #0f4726;
  border-color: #0f4726;
}
.na-box {
  padding: 34px 20px;
  text-align: center;
  border: 1px dashed #d9d9d9;
  border-radius: 10px;
  background: #fafafa;
  color: #9ca3af;
  font-size: 14px;
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
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.item-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.num-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 6px;
  background: #f4f8f0;
  color: #536d2f;
  font-size: 13px;
  font-weight: 800;
}
.remove-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  color: #ff4d4f;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
  padding: 4px 6px;
  border-radius: 6px;
}
.remove-btn:hover {
  background: #fff2f0;
}
.field-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}
.field-table th,
.field-table td {
  border: 1px solid #f0f0f0;
  padding: 12px;
  font-size: 14px;
}
.field-table th {
  background: #fafafa;
  text-align: left;
  font-weight: 600;
  color: #1f2937;
  white-space: nowrap;
}
.field-table td {
  vertical-align: top;
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
.add-btn {
  margin-top: 14px;
  width: 100%;
  height: 42px;
  border: 1px dashed #b7c4a8;
  border-radius: 10px;
  background: #f8faf6;
  color: #536d2f;
  font-weight: 700;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.15s;
}
.add-btn:hover {
  border-color: #6f8f3d;
  background: #f1f6ea;
}
em {
  color: #ff4d4f;
  font-style: normal;
}
:deep(.ant-input),
:deep(.ant-picker),
:deep(.ant-select) {
  width: 100%;
}
</style>
