<template>
  <div class="section-body">
    <!-- <a-checkbox v-model:checked="notApplicable" class="na-checkbox">
      보유 자격증 없음 (해당 사항 없음)
    </a-checkbox> -->

    <div v-if="notApplicable" class="na-box">보유 자격증 없음으로 표시되었습니다.</div>

    <template v-else>
      <div class="card-list">
        <div v-for="(item, index) in items" :key="index" class="item-card">
          <div class="item-card-head">
            <span class="num-pill">자격증 {{ index + 1 }}</span>
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
                <th>자격증명<em> *</em></th>
                <td><a-input v-model:value="item.certificateName" placeholder="예) 정보처리기사" /></td>
                <th>발급기관<em> *</em></th>
                <td><a-input v-model:value="item.issuingOrganization" placeholder="예) 한국산업인력공단" /></td>
              </tr>
              <tr>
                <th>취득일자<em> *</em></th>
                <td><a-date-picker v-model:value="item.acquiredDate" value-format="YYYY-MM-DD" /></td>
                <th>유효일자</th>
                <td><a-date-picker v-model:value="item.expiredDate" value-format="YYYY-MM-DD" /></td>
              </tr>
              <tr>
                <th>자격증번호</th>
                <td><a-input v-model:value="item.certificateNumber" placeholder="예) 20-12-345678" /></td>
                <th>점수/등급</th>
                <td><a-input v-model:value="item.scoreOrGrade" placeholder="예) 1급 / 850점" /></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-if="items.length === 0" class="empty-box">
        <p class="empty-title">등록된 자격증이 없습니다.</p>
        <p class="empty-desc">아래 버튼으로 자격증을 추가하세요.</p>
      </div>

      <button type="button" class="add-btn" @click="addItem">
        <PlusOutlined /> 자격증 추가
      </button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { certificateApi } from '@/api/application/sections/certificateApi'
import { getApiErrorMessage } from '@/api/apiError'
import { logClientEvent } from '@/common/clientEventLogger'
import type { SectionComponentProps } from '@/types/application'
import type {
  CertificateItem,
  CertificateResponse,
  CertificateReplaceRequest,
} from '@/types/application/sections/certificate'

const props = defineProps<SectionComponentProps>()

const loading = ref(false)
const notApplicable = ref(false)
const items = reactive<CertificateItem[]>([])

function createEmptyItem(): CertificateItem {
  return {
    certificateName: '',
    issuingOrganization: '',
    acquiredDate: '',
    certificateNumber: '',
    expiredDate: '',
    scoreOrGrade: '',
  }
}

function setItems(list: CertificateResponse[]) {
  items.splice(
    0,
    items.length,
    ...list.map((row) => ({
      certificateId: row.certificateId,
      certificateName: row.certificateName,
      issuingOrganization: row.issuingOrganization,
      acquiredDate: row.acquiredDate ?? '',
      certificateNumber: row.certificateNumber ?? '',
      expiredDate: row.expiredDate ?? '',
      scoreOrGrade: row.scoreOrGrade ?? '',
    })),
  )
}

function addItem() {
  items.push(createEmptyItem())
}

function removeItem(index: number) {
  items.splice(index, 1)
}

function buildPayload(): CertificateReplaceRequest {
  if (notApplicable.value) return { certificates: [] }
  return {
    certificates: items.map((item, index) => ({
      certificateName: item.certificateName,
      issuingOrganization: item.issuingOrganization,
      acquiredDate: item.acquiredDate,
      certificateNumber: item.certificateNumber || undefined,
      expiredDate: item.expiredDate || undefined,
      scoreOrGrade: item.scoreOrGrade || undefined,
      sortOrder: index,
    })),
  }
}

function validate(): boolean {
  if (notApplicable.value) return true
  if (items.length === 0) {
    if (props.section.required) {
      message.warning("자격증을 추가하거나 '보유 자격증 없음'을 선택하세요.")
      return false
    }
    return true
  }
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (!item) continue
    if (!item.certificateName || !item.issuingOrganization || !item.acquiredDate) {
      message.warning(`자격증 ${i + 1}: 자격증명, 발급기관, 취득일자는 필수입니다.`)
      return false
    }
  }
  return true
}

async function loadCertificates() {
  loading.value = true
  try {
    const result = await certificateApi.getApplicationsCertificates(props.applicationId)
    setItems(result.data.data ?? [])
  } finally {
    loading.value = false
  }
}

async function saveDraft() {
  if (!validate()) throw new Error('입력값을 확인해주세요.')
  loading.value = true
  try {
    const result = await certificateApi.replaceApplicationsCertificates(props.applicationId, buildPayload())
    setItems(result.data.data ?? [])
    return result.data.data
  } catch (error) {
    logClientEvent({
      eventType: 'APPLICATION_DRAFT_SAVE_FAILED',
      severity: 'INFO',
      pageCode: 'APPLICATION_FORM_CERTIFICATE',
      operation: 'SAVE_DRAFT_CERTIFICATE',
      applicationId: props.applicationId,
      message: 'APPLICATION_DRAFT_SAVE_FAILED',
    })
    throw new Error(getApiErrorMessage(error, '자격증 정보 저장에 실패했습니다.'))
  } finally {
    loading.value = false
  }
}

function validateBeforeSubmit(): boolean {
  return validate()
}

onMounted(() => {
  loadCertificates()
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
:deep(.ant-picker) {
  width: 100%;
}
</style>
