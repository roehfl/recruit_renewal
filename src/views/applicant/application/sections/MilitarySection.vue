<template>
  <div class="section-body">

    <div v-if="notApplicable" class="na-box">병역 사항이 없음으로 표시되었습니다.</div>

    <table class="field-table">
            <colgroup>
              <col style="width: 14%" /><col style="width: 50%" />
              <col style="width: 14%" /><col style="width: 40%" />
              <col style="width: 8%" /><col style="width: 15%" />
            </colgroup>
            <tbody>
              <tr>
                <th>군필여부<em> *</em></th>
                <td class="military-box">
                    <a-select class="militarySubject-select" v-model:value="MilitaryForm.militarySubjectType" :options="militarySubjectType" @change="format()" placeholder="선택">
                    <template #suffix> <SearchOutlined /></template></a-select>
                    <a-input class="militarySubject-input" v-model:value="MilitaryForm.nonServiceReason" :disabled="MilitaryForm.militarySubjectType == 'COMPLETED' || MilitaryForm.militarySubjectType == 'NOT_SUBJECT'" placeholder="미필/면제 사유 작성" />
                  </td>
                <th>군벌</th>
                <td class="military-box">
                    <a-select class="militaryBranch-select" v-model:value="MilitaryForm.militaryBranch" :options="militaryBranchType" :disabled="MilitaryForm.militarySubjectType != 'COMPLETED'" placeholder="선택">
                    <template #suffix> <SearchOutlined /></template></a-select>
                    <a-select v-model:value="MilitaryForm.serviceType" :options="militaryServiceType" :disabled="MilitaryForm.militarySubjectType != 'COMPLETED'" placeholder="선택">
                    <template #suffix> <SearchOutlined /></template></a-select>
                  </td>
                <th>계급</th>
                <td><a-select v-model:value="MilitaryForm.rank" :options="militaryRankType" :disabled="MilitaryForm.militarySubjectType != 'COMPLETED'" placeholder="선택">
                    <template #suffix> <SearchOutlined /></template>
                    </a-select></td>
              </tr>
              <tr>
                <th>복무기간</th>
                <td class="working-period" colspan="5">
                    <a-date-picker class="working-date" v-model:value="MilitaryForm.serviceStartDate" :disabled="MilitaryForm.militarySubjectType != 'COMPLETED'" value-format="YYYY-MM-DD" placeholder="입대년월일"/>
                    <span class="working">~</span>
                    <a-date-picker class="working-date" v-model:value="MilitaryForm.serviceEndDate" :disabled="MilitaryForm.militarySubjectType != 'COMPLETED'" value-format="YYYY-MM-DD" placeholder="전역년월일"/>
                </td> 
            </tr>
          </tbody>
      </table>

  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { SearchOutlined } from '@ant-design/icons-vue'
import type { SectionComponentProps } from '@/types/application'
import { logClientEvent } from '@/common/clientEventLogger';
import { getApiErrorMessage } from '@/api/apiError';
import type { MilitaryRepuest, MilitaryResponse } from '@/types/application/sections/military';
import { applicationMilitaryApi } from '@/api/application/sections/militaryApi';

const props = defineProps<SectionComponentProps>()

const loading = ref(false)
const notApplicable = ref(false)
const requestBody = ref<MilitaryRepuest>()

const militarySubjectType = [
  { label: '필', value: 'COMPLETED' },
  { label: '미필', value: 'SUBJECT' },
  { label: '면제', value: 'EXEMPTED' },
  { label: '대상아님', value: 'NOT_SUBJECT' },
];

const militaryBranchType = [
  { label: '육군', value: 'ARMY' },
  { label: '해군', value: 'NAVY' },
  { label: '공군', value: 'AIR_FORCE' },
  { label: '해병대', value: 'MARINE' },
  { label: '의무소방', value: 'FIRE_SERVICE' },
  { label: '의무경찰', value: 'POLICE' },
  { label: '기타', value: 'ETC' },
];

const militaryServiceType = [
  { label: '현역복무', value: 'ACTIVE_DUTY' },
  { label: '부사관', value: 'NON_COMMISSIONED_OFFICER' },
  { label: '장교', value: 'OFFICER' },
  { label: '전문연구요원', value: 'PROFESSIONAL_RESEARCH' },
  { label: '산업기능요원', value: 'INDUSTRIAL_TECHNICAL' },
  { label: '공익근무요원', value: 'PUBLIC_SERVICE' },
  { label: '사회복무요원', value: 'SOCIAL_SERVICE' },
  { label: '보충역', value: 'SUPPLEMENTARY' },
  { label: '기타', value: 'ETC' },
];

const militaryRankType = [
  { label: '이병', value: 'PRIVATE' },
  { label: '일병', value: 'PRIVATE_FIRST_CLASS' },
  { label: '상병', value: 'CORPORAL' },
  { label: '병장', value: 'SERGEANT' },
  { label: '하사', value: 'STAFF_SERGEANT' },
  { label: '중사', value: 'SERGEANT_FIRST_CLASS' },
  { label: '상사', value: 'MASTER_SERGEANT' },
  { label: '준위', value: 'WARRANT_OFFICER' },
  { label: '소위', value: 'SECOND_LIEUTENANT' },
  { label: '중위', value: 'FIRST_LIEUTENANT' },
  { label: '대위', value: 'CAPTAIN' },
  { label: '소령', value: 'MAJOR' },
  { label: '기타', value: 'ETC' },
];

const MilitaryForm = reactive<MilitaryResponse>({
    militaryId: 0,
    militarySubjectType: '',
    serviceType: null,
    militaryBranch: null,
    rank: null,
    serviceStartDate: null,
    serviceEndDate: null,
    nonServiceReason: null
})

async function setItem(item: MilitaryResponse) {
  MilitaryForm.militaryId =  item.militaryId,
  MilitaryForm.militarySubjectType = item.militarySubjectType ?? null,
  MilitaryForm.serviceType = item.serviceType ?? null,
  MilitaryForm.militaryBranch = item.militaryBranch ?? null,
  MilitaryForm.rank = item.rank ?? null,
  MilitaryForm.serviceStartDate = item.serviceStartDate ?? null,
  MilitaryForm.serviceEndDate = item.serviceEndDate ?? null,
  MilitaryForm.nonServiceReason = item.nonServiceReason ?? null
}

function validate(): boolean {
  if (notApplicable.value) return true
  if (!MilitaryForm.militarySubjectType) {
    throw new Error("군필여부는 필수입니다.");
  }
    return true
}

async function format() {
  MilitaryForm.nonServiceReason = null;
  MilitaryForm.militaryBranch = null;
  MilitaryForm.rank = null;
  MilitaryForm.serviceType = null;
  MilitaryForm.serviceStartDate = null;
  MilitaryForm.serviceEndDate = null;
}

function setRequestBody() {
   if(MilitaryForm.militarySubjectType == 'COMPLETED'){
      requestBody.value = {
        militarySubjectType: MilitaryForm.militarySubjectType,
        serviceType: MilitaryForm.serviceType,
        militaryBranch: MilitaryForm.militaryBranch,
        rank: MilitaryForm.rank,
        serviceStartDate: MilitaryForm.serviceStartDate, 
        serviceEndDate: MilitaryForm.serviceEndDate,
        nonServiceReason: MilitaryForm.nonServiceReason
      }
    } else if(MilitaryForm.militarySubjectType == 'NOT_SUBJECT'){
      requestBody.value = {
        militarySubjectType: MilitaryForm.militarySubjectType ?? '',
        serviceType: null,
        militaryBranch: null,
        rank: null,
        serviceStartDate: null, 
        serviceEndDate: null,
        nonServiceReason: null
      }
    } else{
      requestBody.value = {
        militarySubjectType: MilitaryForm.militarySubjectType ?? '',
        nonServiceReason: MilitaryForm.nonServiceReason ?? '',
        serviceType: null,
        militaryBranch: null,
        rank: null,
        serviceStartDate: null, 
        serviceEndDate: null,
      }
    }
}

async function loadMyMilitary() {
  loading.value = true
  try {
    const result = await applicationMilitaryApi.getApplicationMilitary(props.applicationId)
    if(result.data.data) setItem(result.data.data);
  } finally {
    loading.value = false
  }
}

async function saveDraft() {
  if (!validate()) throw new Error('입력값을 확인해주세요.')
  loading.value = true
  setRequestBody();
  try {
    const result = await applicationMilitaryApi.postApplicationvMilitary(props.applicationId, requestBody.value as MilitaryRepuest)
    setItem(result.data.data)
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
    throw new Error(getApiErrorMessage(error, '병역사항 저장에 실패했습니다.'))
  } finally {
    loading.value = false
  }
}

function validateBeforeSubmit(): boolean {
  return validate()
}

onMounted(async () => {
  await loadMyMilitary();
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
em {
  color: #ff4d4f;
  font-style: normal;
}
:deep(.ant-input),
:deep(.ant-picker) {
  width: 100%;
}


/* =========================
   병역 입력 영역
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

/*근무기간*/
.working-period {
    white-space: nowrap;
}

.working-date {
    width: 30%;
}

.working {
    padding: 0 15px;
}

/*재직여부*/
.switch-text {
    margin-left: 12px;
}

.switch-button {
  margin-left: 10px;
}

:deep(.ant-select){
  max-width: 145px;
  width: 95%;
}

.military-box .militarySubject-select{
  white-space: nowrap;
  margin-right: 12px;
}

.militarySubject-select {
  width: 95%;
}

.militarySubject-input {
  width: 50%;
}

.militaryBranch-select {
  max-width: 100px;
  width: 95%;
  margin-right: 12px;
}

</style>
