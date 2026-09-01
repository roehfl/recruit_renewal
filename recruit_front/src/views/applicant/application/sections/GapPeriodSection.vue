<template>
  <div class="section-body">
    <!--
      '해당 사항 없음' 비활성화: 없음 상태가 백엔드에 저장되지 않아(빈 배열=미입력과 구분 불가)
      새로고침 시 체크가 풀리는 문제로 주석 처리. 백엔드에 notApplicable 영속화가 생기면 되살린다.
    <a-checkbox v-model:checked="notApplicable" class="na-checkbox">
      공백기간 없음 (해당 사항 없음)
    </a-checkbox>
    -->

    <div v-if="notApplicable" class="na-box">공백기간 없음으로 표시되었습니다.</div>

    <template v-else>
      <div class="card-list">
        <div v-for="(item, index) in items" :key="index" class="item-card">
          <div class="item-card-head">
            <span class="num-pill">공백 {{ index + 1 }}</span>
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
                <th>공백기간<em> *</em></th>
                <td colspan="3">
                  <div class="period-input">
                    <a-date-picker v-model:value="item.startDate" value-format="YYYY-MM-DD" placeholder="시작일" />
                    <span>~</span>
                    <a-date-picker v-model:value="item.endDate" value-format="YYYY-MM-DD" placeholder="종료일" />
                  </div>
                </td>
              </tr>
              <tr>
                <th>구분<em> *</em></th>
                <td><a-select v-model:value="item.gapType" :options="gapTypeOptions" placeholder="선택" /></td>
                <th>사유<em> *</em></th>
                <td><a-input v-model:value="item.reason" placeholder="예) 어학연수 / 자격증 준비" /></td>
              </tr>
              <tr>
                <th class="th-top">상세설명</th>
                <td colspan="3">
                  <a-textarea
                    v-model:value="item.description"
                    :maxlength="2000"
                    :rows="2"
                    show-count
                    placeholder="공백기간 동안의 활동을 간단히 작성하세요. (선택)"
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-if="items.length === 0" class="empty-box">
        <p class="empty-title">등록된 공백기간이 없습니다.</p>
        <p class="empty-desc">아래 버튼으로 공백기간을 추가하세요.</p>
      </div>

      <button type="button" class="add-btn" @click="addItem">
        <PlusOutlined /> 공백기간 추가
      </button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { gapPeriodApi } from '@/api/application/sections/gapPeriodApi'
import { getApiErrorMessage } from '@/api/apiError'
import { logClientEvent } from '@/common/clientEventLogger'
import type { SectionComponentProps } from '@/types/application'
import type {
  GapType,
  GapPeriodItem,
  GapPeriodResponse,
  GapPeriodReplaceRequest,
} from '@/types/application/sections/gapPeriod'

const props = defineProps<SectionComponentProps>()

const loading = ref(false)
const notApplicable = ref(false)
const items = reactive<GapPeriodItem[]>([])

const gapTypeOptions: { value: GapType; label: string }[] = [
  { value: 'EDUCATION', label: '학업' },
  { value: 'CAREER', label: '경력' },
  { value: 'OTHER', label: '기타' },
]

function createEmptyItem(): GapPeriodItem {
  return {
    startDate: '',
    endDate: '',
    gapType: '',
    reason: '',
    description: '',
  }
}

function setItems(list: GapPeriodResponse[]) {
  items.splice(
    0,
    items.length,
    ...list.map((row) => ({
      gapPeriodId: row.gapPeriodId,
      startDate: row.startDate ?? '',
      endDate: row.endDate ?? '',
      gapType: row.gapType,
      reason: row.reason ?? '',
      description: row.description ?? '',
    })),
  )
}

function addItem() {
  items.push(createEmptyItem())
}

function removeItem(index: number) {
  items.splice(index, 1)
}

function buildPayload(): GapPeriodReplaceRequest {
  if (notApplicable.value) return { gapPeriods: [] }
  return {
    gapPeriods: items.map((item, index) => ({
      startDate: item.startDate,
      endDate: item.endDate,
      gapType: item.gapType as GapType,
      reason: item.reason,
      description: item.description || undefined,
      sortOrder: index,
    })),
  }
}

function validate(): boolean {
  if (notApplicable.value) return true
  if (items.length === 0) {
    if (props.section.required) {
      message.warning("공백기간을 추가하거나 '공백기간 없음'을 선택하세요.")
      return false
    }
    return true
  }
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (!item) continue
    if (!item.startDate || !item.endDate || !item.gapType || !item.reason) {
      message.warning(`공백 ${i + 1}: 공백기간, 구분, 사유는 필수입니다.`)
      return false
    }
  }
  return true
}

async function loadGapPeriods() {
  loading.value = true
  try {
    const result = await gapPeriodApi.getApplicationsGapPeriods(props.applicationId)
    setItems(result.data.data ?? [])
  } finally {
    loading.value = false
  }
}

async function saveDraft() {
  if (!validate()) throw new Error('입력값을 확인해주세요.')
  loading.value = true
  try {
    const result = await gapPeriodApi.replaceApplicationsGapPeriods(props.applicationId, buildPayload())
    setItems(result.data.data ?? [])
    return result.data.data
  } catch (error) {
    logClientEvent({
      eventType: 'APPLICATION_DRAFT_SAVE_FAILED',
      severity: 'INFO',
      pageCode: 'APPLICATION_FORM_GAP_PERIOD',
      operation: 'SAVE_DRAFT_GAP_PERIOD',
      applicationId: props.applicationId,
      message: 'APPLICATION_DRAFT_SAVE_FAILED',
    })
    throw new Error(getApiErrorMessage(error, '공백기간 저장에 실패했습니다.'))
  } finally {
    loading.value = false
  }
}

function validateBeforeSubmit(): boolean {
  return validate()
}

onMounted(() => {
  loadGapPeriods()
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
.th-top {
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
.period-input {
  display: flex;
  align-items: center;
  gap: 10px;
}
.period-input :deep(.ant-picker) {
  width: 180px;
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
