<template>
  <div class="section-body">
    <a-form>
      <div aria-label="기본정보" class="form-table">
        <table class="apply-table">
          <colgroup>
            <col style="width: 10%;">
            <col style="width: 35%;">
            <col style="width: 10%;">
            <col style="width: 45%;">
          </colgroup>
          <tbody>
            <tr>
              <th>사진<em> *</em></th>
              <td>
                <div aria-label="사진" class="picture-section">
                  <ul>
                    <li>파일 형식 : JPG/JPEG</li>
                    <li>용량 제한 : 1MB 미만</li>
                  </ul>
                  <a-upload
                    v-model:file-list="fileList"
                    list-type="picture-card"
                    :max-count="1"
                    :before-upload="() => false"
                    style="width: auto;"
                  >
                    <div v-if="fileList.length === 0">사진등록</div>
                  </a-upload>
                </div>
              </td>

              <th>이름<em> *</em></th>
              <td class="span-section">
                <a-form-item>
                  <span> 한글명 </span>
                  <a-input v-model:value="form.nameKorean" disabled />
                </a-form-item>
                <a-form-item>
                  <span> 영문명 </span>
                  <a-input 
                    v-model:value="form.nameEnglish" 
                    @input="form.nameEnglish = form.nameEnglish?.replace(/[^a-zA-Z\s]/g,'')" 
                    placeholder="Hong Gildong (성 이름)" />
                </a-form-item>
              </td>  
            </tr>

            <tr>
              <th>내/외국인<em> *</em></th>
              <td>
                <a-form-item>
                  <a-radio-group v-model:value="form.nationalityType" @change="onNationalityTypeChange">
                    <a-radio value="DOMESTIC">내국인</a-radio>
                    <a-radio value="FOREIGN">외국인</a-radio>
                    <!-- <a-input 
                      v-if="form.nationalityType ==='FOREIGN'" 
                      v-model:value="form.countryCode" 
                      style="width: 120px" 
                      placeholder="ex) 미국"
                      ref="nationalityTypeInputRef"
                    ></a-input> -->
                    <a-select
                      v-if="form.nationalityType ==='FOREIGN'" 
                      v-model:value="form.countryCode" 
                      :options="nationalityOptions"
                      style="width: 120px" 
                      show-search
                      palceholder="ex) 미국"
                      ref="nationalityTypeInputRef"
                      :filter-option="filterNationality"
                    />
                  </a-radio-group>
                </a-form-item>
              </td>

              <th>보훈여부<em> *</em></th>
              <td>
                <a-form-item>
                  <a-radio-group v-model:value="form.veteranStatus">
                    <a-radio value="NOT_SUBJECT">비대상</a-radio>
                    <a-radio value="SUBJECT">대상</a-radio>
                    <a-input v-if="form.veteranStatus ==='SUBJECT'" style="width: 200px" placeholder="ex) 국가유공자의 자"></a-input>
                  </a-radio-group>
                </a-form-item>
              </td>
            </tr>

            <tr>
              <th>생년월일<em> *</em></th>
              <td>
                <a-form-item>
                  <a-date-picker v-model:value="form.birthDate" value-format="YYYY-MM-DD"></a-date-picker>
                </a-form-item>
              </td>

              <th>장애여부<em> *</em></th>
              <td>
                <div class="disability-section">
                  <a-radio-group v-model:value="form.disabilityStatus" @change="onDisabilityStatusChange">
                    <a-radio value="NOT_SUBJECT">비대상</a-radio>
                    <a-radio value="SUBJECT">대상</a-radio>
                    
                  </a-radio-group>
                  <div v-if="form.disabilityStatus ==='SUBJECT'" >
                    <label>(등급: </label>
                    <a-select v-model:value="form.disabilityGradeCode" style="width: 67px" 
                      :options="disabilityGradeOptions" 
                      :disabled="form.disabilityStatus !=='SUBJECT'"
                      placeholder="선택"
                    />
                    <label> / 유형: </label>
                    <a-select v-model:value="form.disabilityTypeCode" style="width: 120px" 
                      :options="disabilityStatusOptions" 
                      :disabled="form.disabilityStatus !=='SUBJECT'"
                      placeholder="선택"
                    />
                    <label>)</label>
                  </div>
                </div>
              </td>
            </tr>

            <tr>
              <th>연락처<em> *</em></th>
              <td>
                <div class="phone-section">
                  <a-form-item label="휴대폰" class="inner-form-item">
                    <a-input v-model:value="phoneForm.phone1" disabled style="width: 50px;"/>
                    <span> - </span>
                    <a-input v-model:value="phoneForm.phone2" disabled style="width: 60px;"/>
                    <span> - </span>
                    <a-input v-model:value="phoneForm.phone3" disabled style="width: 60px;"/>
                  </a-form-item>
                  <a-form-item label="비상연락처" :label-col="{ style: { width: '75px'}}">
                    <a-input v-model:value="phoneForm.subPhone1" @keydown="onlyNumber" :maxlength="3" style="width: 50px;"/>
                    <span> - </span>
                    <a-input v-model:value="phoneForm.subPhone2" @keydown="onlyNumber" :maxlength="4" style="width: 60px;"/>
                    <span> - </span>
                    <a-input v-model:value="phoneForm.subPhone3" @keydown="onlyNumber" :maxlength="4" style="width: 60px;"/>
                  </a-form-item>
                </div>
              </td>
              
              <th rowspan="2" style="border-bottom: 0;">주소</th>
              <td rowspan="2">
                <div class="td-column">
                  <a-form-item>
                    <a-input class="item-margin" v-model:value="form.zipCode" @click="openPostcode" readonly style="width: 100px;"/>
                    <a-button @click="openPostcode" type="primary" shape="circle"><SearchOutlined /></a-button>
                    <!-- <a-button @click="checkData" type="primary">임시저장</a-button> -->
                  </a-form-item>
                  <a-input class="item-margin" v-model:value="form.addressBasic" />
                </div>
                <a-form-item label="상세주소">
                  <a-input v-model:value="form.addressDetail"  placeholder="000동 000호"/>
                </a-form-item>
              </td>
            </tr>

            <tr>
              <th>e-mail<em> *</em></th>
              <td>
                <a-input v-model:value="form.email" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { message } from 'ant-design-vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import { ref, reactive, onMounted, computed } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { useRoute } from 'vue-router'
import { basicInfoApi } from '@/api/application/sections/basicInfoApi'
import { commonCodeApi } from '@/api/commonApi'
import type { BasicInfoParams } from '@/types/application/sections/basicInfo'
import type {  CommonCodeItems } from '@/types/commonCode'
import { logClientEvent } from '@/common/clientEventLogger'
import { getApiErrorMessage } from '@/api/apiError'
import type { DefaultOptionType } from 'ant-design-vue/es/select'


const authStore = useAuthStore();
const loading = ref(false)
const route = useRoute() 
const fileList = ref([])
const nationalityTypeInputRef = ref();

// 국가 코드  
const nationalityList = ref<CommonCodeItems[]>([])
const nationalityOptions = computed(() => 
  nationalityList.value.map( item =>({
    value: item.code,
    label: item.displayName,
  }))
)
const filterNationality = (input: string, option: DefaultOptionType) => {
  return option.label
    .toLowerCase()
    .includes(input.toLowerCase())
}

// 장애 유형 
const disabilityStatusList = ref<CommonCodeItems[]>([])
const disabilityStatusOptions = computed(() => 
  disabilityStatusList.value.map( item =>({
    value: item.code,
    label: item.displayName,
  }))
)

// 장애 등급 
const disabilityGradeList = ref<CommonCodeItems[]>([])
const disabilityGradeOptions = computed(() => 
  disabilityGradeList.value.map( item =>({
    value: item.code,
    label: item.displayName,
  }))
)

const form = reactive<BasicInfoParams>({
    nameKorean:authStore.name,          // 한글명
    nameEnglish:'',                     // 영문명
    nationalityType: '',                // 내/외국인
    countryCode:'',                     // 국가번호
    veteranStatus: '',                  // 보훈여부
    birthDate: '',                      // 생년월일
    disabilityStatus: '',               // 장애여부 
    disabilityGradeCode: '',            // 장애 등급
    disabilityTypeCode: '',             // 장애 유형
    zipCode: '',                        // 도로명주소
    addressBasic: '',                   // 주소
    addressDetail:'',                   // 상세주소
    email:authStore.loginId,            // 이메일
    mobilePhone:authStore.phoneNumber,  // 전화번호 
    emergencyPhone:'',                  // 비상연락처
})

const phoneForm = reactive({
    phone1:authStore.phoneNumber.substring(0, 3),   // 연락처1
    phone2:authStore.phoneNumber.substring(3, 7),   // 연락처2
    phone3:authStore.phoneNumber.substring(7, 11),  // 연락처3
    subPhone1:'',                                   // 비상연락처1
    subPhone2:'',                                   // 비상연락처2
    subPhone3:'',                                   // 비상연락처3
})

// 장애여부 '비대상' 클릭 시 드롭박스 초기화 
const onNationalityTypeChange = () => {
  form.countryCode = '';
}
// 장애여부 '비대상' 클릭 시 드롭박스 초기화 
const onDisabilityStatusChange = () => {
  form.disabilityGradeCode = undefined;
  form.disabilityTypeCode = undefined;
}

// 전화번호 입력 input (숫자만 입력 가능하도록)
const onlyNumber = (e: KeyboardEvent) => {
    const allowKeys = ['Backspace', 'Delete', 'ArrowLeft', 'ArrowRight', 'Tab'];

    if (allowKeys.includes(e.key)) return;

    if (!/^\d$/.test(e.key)) {
        e.preventDefault();
    }
}

// 주소 찾기 클릭 시 
const openPostcode = () => {
    message.success('주소찾기입니다.')
    // const { kakao } = window as any;

    // new window.kakao.Postcode({
    //     oncomplete(data) {
    //         form.value.zonecode = data.zonecode
    //         form.value.address = data.address
    //     }
    // }).open()
}

// 부모 임시저장 버튼 
const saveDraft = () => {
  vaildation();

  // 비상연락처 format 작업 
  form.emergencyPhone = `${phoneForm.subPhone1}${phoneForm.subPhone2}${phoneForm.subPhone3}`

  return postBasicInfo().then(result => {
    if (result.success){
      return result.data;
    }else {
      throw new Error(result.error);
    }
  });
}

const validateBeforeSubmit = () => {
    form.mobilePhone = `${phoneForm.phone1}${phoneForm.phone2}${phoneForm.phone3}`
    if (!form.nameKorean || !form.nameEnglish || !form.birthDate || !form.mobilePhone || !form.email)  return false
    return true;
}

const vaildation = () => {
  // // 사진
  // if (fileList.value.length === 0) {
  //   message.warn("사진을 등록하세요.")
  //   return false;
  // }

  // 내/외국인
  if (!form.nationalityType) throw new Error("국적을 선택하세요.");
  else {
    if(form.nationalityType === 'FOREIGN' && !form.countryCode){
      nationalityTypeInputRef.value.focus();
      throw new Error("국적을 입력해주세요.");
    }
  }

  // 보훈여부
  if (!form.veteranStatus) throw new Error("보훈여부를 선택하세요.");

  // 생년월일
  if (!form.birthDate) throw new Error("생년월일을 선택하세요.");

  // 장애여부
  if (!form.disabilityStatus) throw new Error("장애여부를 선택하세요.");
  else {
    if (form.disabilityStatus === 'SUBJECT' && !form.disabilityGradeCode && !form.disabilityTypeCode) {
      throw new Error('장애 등급, 유형을 선택해주세요.')
    }
  }

  // 이메일
  if (!form.email) throw new Error("이메일을 입력하세요.")
}

async function loadCommonCode(groupCode: string) {
  loading.value = true
  try {
    const result = await commonCodeApi.getCommonCodes(groupCode)
    
    if (groupCode === 'DISABILITY_GRADE')     disabilityGradeList.value= result.data.data;
    else if(groupCode === 'DISABILITY_TYPE')  disabilityStatusList.value = result.data.data;
    else if(groupCode === 'NATIONALITY')      nationalityList.value = result.data.data;

  } finally {
    loading.value = false
  }
}

// GET BasicInfo 
async function loadBasicInfo() {
  const applicationId = Number(route.params.applicationId);

  loading.value = true
  try {
    const result = await basicInfoApi.getApplicationsBasicInfo(applicationId)
    Object.assign(form, result.data.data);

    if (form.emergencyPhone) {
      phoneForm.subPhone1 = form.emergencyPhone.substring(0, 3);
      phoneForm.subPhone2 = form.emergencyPhone.substring(3, 7);
      phoneForm.subPhone3 = form.emergencyPhone.substring(7, 11);
    }

  } finally {
    loading.value = false
  }
}

// POST BasicInfo
async function postBasicInfo() {
  const applicationId = Number(route.params.applicationId);
  const params = { 
      nameKorean: form.nameKorean,
      nameEnglish: form.nameEnglish,
      nationalityType: form.nationalityType,
      countryCode: form.countryCode,
      birthDate: form.birthDate,
      mobilePhone: form.mobilePhone,
      emergencyPhone: form.emergencyPhone,
      email: form.email,
      veteranStatus: form.veteranStatus,
      disabilityStatus: form.disabilityStatus,
      disabilityGradeCode: form.disabilityGradeCode || '',
      disabilityTypeCode: form.disabilityTypeCode || '',
      zipCode: form.zipCode,
      addressBasic: form.addressBasic,
      addressDetail: form.addressDetail,
  }

  loading.value = true
  try {
    const result = await basicInfoApi.postApplicationsBasicInfo(applicationId, params);
    return {success: true, data: result.data.data}

  } catch(error) {
    logClientEvent({
      eventType: 'APPLICATION_SUBMIT_FAILED',
      severity: 'INFO',
      pageCode: 'APPLICATION_FORM_BASIC_INFO',
      operation: 'SUBMIT_APPLICATION_BASIC_INFO',
      applicationId: applicationId,
      message: 'APPLICATION_SUBMIT_CLICKED',
    })
    return {success: false, error: getApiErrorMessage(error, 'fallback 메세지')};
  }   finally {
    loading.value = false
  }
}

onMounted(() => {
  loadBasicInfo()

  loadCommonCode('DISABILITY_TYPE')
  loadCommonCode('DISABILITY_GRADE')
  loadCommonCode('NATIONALITY')
})

defineExpose({ saveDraft, validateBeforeSubmit })
</script>

<style scoped>
.section-body {
  margin: 24px;
}

.form-table {
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  overflow: hidden;
}

.apply-table {
    width: 100%;
    border-collapse: collapse;
}

.apply-table th, 
.apply-table td {
    border: 1px solid #f0f0f0;
    padding: 12px;
}

.apply-table th {
    background: #fafafa;
    text-align: left;
}

/* 첫 행 */
.apply-table tr:first-child th, 
.apply-table tr:first-child td {
    border-top: none;
}

/* 마지막 행 */
.apply-table tr:last-child th, 
.apply-table tr:last-child td {
    border-bottom: none;
}

/* 첫 컬럼 */
.apply-table tr th:first-child, 
.apply-table tr td:first-child {
    border-left: none;
}

/* 마지막 컬럼 */
.apply-table tr th:last-child, 
.apply-table tr td:last-child {
    border-bottom: none;
    border-right: none;
}

.span-section .ant-form-item:first-child {
  margin-bottom: 8px;
}

.td-column {
    margin-bottom: 8px;
}

.item-margin {
    margin-bottom: 8px;
    margin-right: 8px;
}

.disability-section {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.phone-section .ant-form-item:first-child {
    margin-bottom: 8px;
}

.picture-section {
    display: flex;
    align-items: center;
}

.picture-section > *{
    flex: 1;
}

.picture-section ul{
    padding: 0 5px 0 30px;
    margin-bottom: 0;
}

em {
    color: red;
    font-style: normal;    
}

:deep(.ant-form-item) {
  margin-bottom: 0;
}

:deep(.ant-upload-select),
:deep(.ant-upload-list-picture-card-container),
:deep(.ant-upload-list-item-container) {
    margin-bottom: 0 !important;
}

:deep(.inner-form-item .ant-form-item-row){
    display: flex;
}


</style>
