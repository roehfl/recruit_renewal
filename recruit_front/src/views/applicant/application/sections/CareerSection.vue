<template>
  <div class="section-body">
    <p class="section-guide">* 최근 경력부터 순서대로 입력해 주세요.</p>

    <div v-if="notApplicable" class="na-box">경력사항 없음으로 표시되었습니다.</div>

    <template v-else>
      <div class="card-list">
        <div v-for="(item, index) in items" :key="index" class="item-card">
          <div class="item-card-head">
            <span class="num-pill">경력사항 {{ index + 1 }}</span>
            <button type="button" class="remove-btn" @click="removeItem(index)">
              <DeleteOutlined /> 삭제
            </button>
          </div>

          <table class="field-table">
            <colgroup>
              <col style="width: 18%" /><col style="width: 34%" />
              <col style="width: 18%" /><col style="width: 34%" />
              <col style="width: 10%" /><col style="width: 16%" />
            </colgroup>
            <tbody>
              <tr>
                <th>회사명(소재지)<em> *</em></th>
                <td><a-input v-model:value="item.companyName"/></td>
                <th>부서명(담당업무)</th>
                <td><a-input v-model:value="item.departmentName"/></td>
                <th>고용형태</th>
                <td><a-select v-model:value="item.employmentType" :options="employmentType" placeholder="선택">
                    <template #suffix> <SearchOutlined /></template>
                    </a-select></td>
              </tr>
              <tr>
                <th>근무기간<em> *</em></th>
                <td class="working-period" colspan="5">
                    <a-date-picker class="working-date" v-model:value="item.startDate" value-format="YYYY-MM-DD" placeholder="입사년월일"/>
                    <span class="working">~</span>
                    <a-date-picker class="working-date" v-model:value="item.endDate" :disabled="item.currentlyEmployed" value-format="YYYY-MM-DD" placeholder="퇴사년월일"/>
                    <span class="switch-text">재직중</span>
                    <a-switch class="switch-button" v-model:checked="item.currentlyEmployed"/>
                </td> 
            </tr>
            <tr>
                <th>최종직급</th>
                <td class="last-position" colspan="3">
                    <a-input class="last-position-box" v-model:value="item.positionTitle" placeholder="예) 대리" />
                    <span class="last-position-text">승진일</span>
                    <a-date-picker class="last-position-box" v-model:value="item.promotionDate" value-format="YYYY-MM-DD" />
                </td>
                <th>연봉</th>
                <td class="last-position">
                    <a-input class="last-position-salary-box" v-model:value="item.currentSalary" placeholder="예) 0000" />
                    <span>만원</span>
                </td>
            </tr>
            <tr>
                <th>퇴직사유</th>
                <td colspan="5">
                  <a-textarea
                    v-model:value="item.resignationReason"
                    :maxlength="500"
                    :rows="3"
                    show-count
                    placeholder="퇴직사유를 간단히 작성하세요. (선택)"
                  /></td>
            </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-if="items.length === 0" class="empty-box">
        <p class="empty-title">등록된 경력사항이 없습니다.</p>
        <p class="empty-desc">아래 버튼으로 경력사항을 추가하세요.</p>
      </div>

      <button type="button" class="add-btn" @click="addItem">
        <PlusOutlined /> 경력사항 추가
      </button>

      <!-- 경력사항이 1건 이상일 때만 노출한다. -->
      <div v-if="items.length > 0" class="career-description">
        <table class="field-table">
          <colgroup>
            <col style="width: 18%" /><col style="width: 82%" />
          </colgroup>
          <tbody>
            <tr>
              <th>경력기술서</th>
              <td>
                <a-upload
                  v-model:file-list="careerDescriptionFiles"
                  :max-count="1"
                  :before-upload="() => false"
                >
                  <a-button v-if="careerDescriptionFiles.length === 0">
                    <UploadOutlined /> 파일 선택
                  </a-button>
                </a-upload>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { DeleteOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons-vue'
import type { CareerItem, CareerReplaceRequest } from '@/types/application/sections/career';
import { applicationCareerApi } from '@/api/application/sections/careerApi';
import { attachmentApi } from '@/api/application/sections/attachmentApi';
import { SearchOutlined } from '@ant-design/icons-vue'
import type { SectionComponentProps } from '@/types/application'
import type { UploadFile } from 'ant-design-vue'
import type { AttachmentResponse } from '@/types/application/sections/attachment'
import { logClientEvent } from '@/common/clientEventLogger';
import { getApiErrorMessage } from '@/api/apiError';

const props = defineProps<SectionComponentProps>()

const loading = ref(false)
const notApplicable = ref(false)
const items = reactive<CareerItem[]>([])

/** 경력기술서(경력 섹션당 1건). 저장된 파일은 attachmentId 를 uid 로 갖는다. */
const careerDescriptionFiles = ref<UploadFile[]>([])
const careerDescriptionAttachment = ref<AttachmentResponse | null>(null)

function createEmptyItem(): CareerItem {
  return {
    careerId: 0,
    companyName: '',
    departmentName: '',
    positionTitle: '',
    employmentType: '',
    startDate: '',
    endDate: '',
    currentlyEmployed: false,
    promotionDate: '',
    currentSalary: 0,
    resignationReason: '',
    sortOrder: 0
}
}

function setItems(list: CareerItem[]) {
  items.splice(
    0,
    items.length,
    ...list.map((row) => ({
        careerId: row.careerId  as unknown as number,
        companyName: row.companyName ?? '',
        departmentName: row.departmentName ?? '',
        positionTitle: row.positionTitle ?? '',
        employmentType: row.employmentType ?? '',
        startDate: row.startDate ?? '',
        endDate: row.endDate ?? '',
        currentlyEmployed: row.currentlyEmployed ?? false,
        promotionDate: row.promotionDate ?? '',
        currentSalary: row.currentSalary as unknown as number,
        resignationReason: row.resignationReason ?? '',
        sortOrder: row.sortOrder  as unknown as number,
        }),
    ))
}

function addItem() {
  items.push(createEmptyItem())
}

function removeItem(index: number) {
  items.splice(index, 1)
}

function buildPayload(): CareerReplaceRequest {
  if (notApplicable.value) return { careers: [] }
  return {
        careers: items.map((item, index) => ({
        "companyName": item.companyName,
        "departmentName": item.departmentName,
        "positionTitle": item.positionTitle,
        "employmentType": item.employmentType,
        "startDate": item.startDate,
        "endDate": item.endDate,
        "currentlyEmployed": item.currentlyEmployed,
        "promotionDate": item.promotionDate,
        "currentSalary": item.currentSalary,
        "resignationReason": item.resignationReason,
        "sortOrder": index
    })),
  }
}

function validate(): boolean {
  if (notApplicable.value) return true
  if (items.length === 0) {
    if (props.section.required) {
      throw new Error("경력을 추가하거나 '경력 없음'을 선택하세요.");
    }
    return true
  }
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (!item) continue
    if (!item.companyName || !item.startDate) {
      throw new Error(`경력사항 ${i + 1}: 회사명, 근무시작일은 필수입니다.`);
    }
    else if(!item.currentlyEmployed && !item.endDate) {
        throw new Error(`경력사항 ${i + 1}: 현재 재직중인 상태가 아니라면, 퇴사년월일을 작성해주세요.`);
    }
  }
  return true
}

// 경력기술서는 sectionType=CAREER + attachmentType=CAREER_DESCRIPTION 으로 식별한다.
// (백엔드에 sectionType 필터가 없어 전체 첨부가 내려오므로 반드시 걸러낸다.)
async function loadCareerDescription() {
  const result = await attachmentApi.getApplicationAttachments(props.applicationId)
  const attachment = (result.data.data ?? []).find(
    (row) => row.sectionType === 'CAREER' && row.attachmentType === 'CAREER_DESCRIPTION',
  )
  careerDescriptionAttachment.value = attachment ?? null
  careerDescriptionFiles.value = attachment
    ? [{ uid: String(attachment.attachmentId), name: attachment.originalFileName, status: 'done' }]
    : []
}

async function saveCareerDescription() {
  // 경력사항을 모두 지웠으면 첨부도 함께 정리한다.
  if (items.length === 0) careerDescriptionFiles.value = []

  const saved = careerDescriptionAttachment.value
  const file = careerDescriptionFiles.value[0]?.originFileObj
  // originFileObj 가 없고 행이 남아 있으면 이미 업로드된 파일이다. 다시 올리지 않는다.
  const unchanged = !file && careerDescriptionFiles.value.length > 0
  if (unchanged) return
  if (!file && !saved) return

  if (saved) {
    await attachmentApi.deleteApplicationAttachments(props.applicationId, saved.attachmentId)
    careerDescriptionAttachment.value = null
  }
  if (!file) return

  const formData = new FormData()
  formData.append('file', file)
  const result = await attachmentApi.postApplicationAttachmentsFile(formData, {
    applicationId: props.applicationId,
    attachmentType: 'CAREER_DESCRIPTION',
    sectionType: 'CAREER',
  })
  careerDescriptionAttachment.value = result.data.data
  careerDescriptionFiles.value = [{
    uid: String(result.data.data.attachmentId),
    name: result.data.data.originalFileName,
    status: 'done',
  }]
}

async function loadMyCareers() {
  loading.value = true
  try {
    const result = await applicationCareerApi.getApplicationCareer(props.applicationId)
    setItems(result.data.data.careers ?? [])
  } finally {
    loading.value = false
  }
}

async function saveDraft() {
  if (!validate()) throw new Error('입력값을 확인해주세요.')
  loading.value = true
  try {
    const requestBody = { careers: buildPayload().careers }
    const result = await applicationCareerApi.postApplicationCareer(props.applicationId, requestBody)
    setItems(result.data.data.careers ?? [])
    await saveCareerDescription()
    return result.data.data
  } catch (error) {
    console.error(error);
    logClientEvent({
      eventType: 'APPLICATION_DRAFT_SAVE_FAILED',
      severity: 'INFO',
      pageCode: 'APPLICATION_FORM_CERTIFICATE',
      operation: 'SAVE_DRAFT_CERTIFICATE',
      applicationId: props.applicationId,
      message: 'APPLICATION_DRAFT_SAVE_FAILED',
    })
    throw new Error(getApiErrorMessage(error, '경력사항 저장에 실패했습니다.'))
  } finally {
    loading.value = false
  }
}

const employmentType = [
  { label: '선택', value: '' },
  { label: '정규', value: 'FULL_TIME' },
  { label: '계약', value: 'CONTRACT' },
  { label: '인턴', value: 'INTERN' },
  { label: '프리랜서', value: 'FREELANCE' },
  { label: '기타', value: 'ETC' },
];

function validateBeforeSubmit(): boolean {
  return validate()
}

onMounted(async () => {
  await loadMyCareers();
  await loadCareerDescription();
});

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
.section-guide {
  margin: 0 0 14px;
  color: var(--app-primary-color);
  font-size: 13px;
  font-weight: 500;
}
.career-description {
  margin-top: 18px;
}
em {
  color: #ff4d4f;
  font-style: normal;
}
:deep(.ant-input),
:deep(.ant-picker) {
  width: 100%;
}


/* =========================
   경력 입력 영역
========================= */


.CareerView {
    padding: 16px;
    max-height: 500px;
}

:deep(.ant-row) {
    padding: 5px 0;
    max-height: 55px;
}

:deep(.ant-col) {
    max-height: 45px;
}

.working-period {
    white-space: nowrap;
}

.working-date {
    width: 30%;
}

.working {
    padding: 0 15px;
}

.switch-text {
    margin-left: 12px;
}

.switch-button {
    margin-left: 10px;
}

:deep(.ant-select){
    max-width: 95px;
    width: 95%;
}

.last-position {
    white-space: nowrap;
}

.last-position-box {
    width: 30%;
    margin-right: 15px;
}

.last-position-salary-box {
    width: 60%;
    margin-right: 6px;
}

.last-position-text {
    margin-right: 10px;
}

</style>
