<template>
  <div class="section-body">
    <a-form>
      <div aria-label="기본정보" class="form-table">
        <table class="table-area">
          <colgroup>
            <col style="width: 10%;">  <col style="width: 35%;">
            <col style="width: 10%;">  <col style="width: 45%;">
          </colgroup>
          <tbody>
            <tr>
              <th>사진</th>
              <td>
                <div aria-label="사진" class="picture-section">
                  <ul>
                    <li>파일 형식 : JPG/JPEG</li>
                    <li>용량 제한 : 1MB 미만</li>
                  </ul>
                  <a-upload
                    v-model:file-list="fileList"  list-type="picture-card"
                    :max-count="1" :before-upload="() => false" style="width: auto;"
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
                    <a-select
                      v-if="form.nationalityType ==='FOREIGN'"    v-model:value="form.countryCode" 
                      :options="nationalityOptions"   style="width: 120px"  show-search   placeholder="ex) 미국"
                      ref="nationalityTypeInputRef"   :filter-option="filterNationality"
                    />
                  </a-radio-group>
                </a-form-item>
              </td>

              <th>보훈여부<em> *</em></th>
              <td>
                <a-form-item>
                  <a-radio-group v-model:value="form.veteranStatus" @change="onVeteranStatusChange">
                    <a-radio value="NOT_SUBJECT">비대상</a-radio>
                    <a-radio value="SUBJECT">대상</a-radio>
                    <a-input v-if="form.veteranStatus ==='SUBJECT'" 
                      v-model:value="form.veteranType"
                      style="width: 200px"    placeholder="ex) 국가유공자의 자"
                    />
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
                    <a-select v-model:value="form.disabilityGradeCode" style="width: 67px"  placeholder="선택"
                      :options="disabilityGradeOptions"   :disabled="form.disabilityStatus !=='SUBJECT'"
                    />
                    <label> / 유형: </label>
                    <a-select v-model:value="form.disabilityTypeCode" style="width: 120px"  placeholder="선택"
                      :options="disabilityStatusOptions"  :disabled="form.disabilityStatus !=='SUBJECT'"
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
                  <a-form-item label="휴대폰">
                    <a-input v-model:value="form.mobilePhone" disabled/>
                  </a-form-item>
                  <a-form-item label="비상연락처">
                    <a-input v-model:value="form.emergencyPhone" :maxlength="11" @keydown="onlyNumber"/>
                  </a-form-item>
                </div>
              </td>
              
              <th rowspan="2" style="border-bottom: 0;">주소</th>
              <td rowspan="2">
                <div class="td-column">
                  <a-form-item>
                    <a-input class="item-margin" v-model:value="form.zipCode" @click="openAddressCode" readonly style="width: 100px;"/>
                    <a-button @click="openAddressCode" shape="circle"><SearchOutlined /></a-button>
                  </a-form-item>
                  <a-input class="item-margin" v-model:value="form.addressBasic" readonly/>
                </div>
                <a-form-item label="상세주소">
                  <a-input v-model:value="form.addressDetail"  placeholder="000동 000호"/>
                </a-form-item>
              </td>
            </tr>

            <tr>
              <th>이메일<em> *</em></th>
              <td>
                <a-input v-model:value="form.email" />
              </td>
            </tr>

            <tr>
              <th>지원 경로</th>
              <td colspan="3">
                <a-form-item>
                  <a-select
                    v-model:value="form.applicationRouteCode" :options="applicationRouteOptions"
                    style="width: 220px" placeholder="선택" allow-clear
                  />
                </a-form-item>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </a-form>
  </div>

  <!-- 주소 검색 모달 -->
  <a-modal
    v-model:open="AddressModalOpen"
    title="주소 검색"
    :width="600"
    :footer="null"
  >
    <div class="table-border">

    <table class="table-area">
      <colgroup>
        <col style="width: 20%" /><col style="width: 80%" />
      </colgroup>
      <tbody>
        <tr>
          <th>주소<em> *</em></th>
          <td>
            <div class="div-flex">
              <a-input 
                v-model:value="AddressSearch" 
                @pressEnter="onSerach" 
                ref="addressSearchRef"
                placeholder="ex) 반포대로 58, 독립기념관, 삼성동 25" 
              />
              <a-button @click="onSerach" type="primary">검색</a-button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
    </div>
          
    <div class="address-result" v-if="addressSearchList.length > 0">
      <div
        v-for="address in addressSearchList" :key="`${address.zipNo}-${address.roadAddr}-${address.jibunAddr}`" class="address-card"
        @click="selectAddress(address)"
      >
        <div class="card-content">
          <div class="zip">{{ address.zipNo }}</div>
          <div class="road">{{ address.roadAddr }}</div>
        </div>
        <div>
          <div class="jibun-tag">지번주소</div>
          <div class="jibun">{{ address.jibunAddr }}</div>
        </div>
      </div>
      <div class="pagination-area">
      <a-pagination
        v-model:current="pagination.current" :total="pagination.total" :page-size="pagination.pageSize"
        @change="changePage" 
      /> 
      </div>
    </div>
    
    <a-empty v-else-if="searched" class="ant-empty-area" description="검색 결과가 없습니다."/>
  </a-modal>
</template>

<script setup lang="ts">
import { message } from 'ant-design-vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import { ref, reactive, onMounted, computed } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { basicInfoApi } from '@/api/application/sections/basicInfoApi'
import { attachmentApi } from '@/api/application/sections/attachmentApi'
import { commonCodeApi } from '@/api/commonApi'
import { addressApi } from '@/api/application/addressApi'
import { logClientEvent } from '@/common/clientEventLogger'
import { getApiErrorMessage } from '@/api/apiError'
import type { DefaultOptionType } from 'ant-design-vue/es/select'
import type { AddressItem } from '@/types/application/address'
import type { BasicInfoParams, AttachmentResponse } from '@/types/application/sections/basicInfo'
import type { CommonCodeItems } from '@/types/commonCode'
import type { SectionComponentProps } from '@/types/application'
import type { UploadFile } from 'ant-design-vue'

const authStore = useAuthStore();
const loading = ref(false)
const props = defineProps<SectionComponentProps>()
const fileList = ref<UploadFile[]>([])
const originAttachments = ref<AttachmentResponse[]>([])
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

// 지원 경로
const applicationRouteList = ref<CommonCodeItems[]>([])
const applicationRouteOptions = computed(() =>
  applicationRouteList.value.map( item =>({
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
    nameEnglish:undefined,              // 영문명
    nationalityType: undefined,         // 내/외국인
    countryCode:undefined,              // 국가번호
    veteranStatus: undefined,           // 보훈여부
    veteranType: undefined,             // 보훈 유형
    birthDate: undefined,               // 생년월일
    disabilityStatus: undefined,        // 장애여부 
    disabilityGradeCode: undefined,     // 장애 등급
    disabilityTypeCode: undefined,      // 장애 유형
    zipCode: undefined,                 // 도로명주소
    addressBasic: undefined,            // 주소
    addressDetail:undefined,            // 상세주소
    email:authStore.loginId,            // 이메일
    mobilePhone:authStore.phoneNumber,  // 전화번호 
    emergencyPhone:undefined,           // 비상연락처
    applicationRouteCode:undefined,     // 지원 경로
})

// 내외국인 '내국인' 클릭 시 드롭박스 초기화 
const onNationalityTypeChange = () => {
  form.countryCode = undefined;
}

// 보훈여부 '비대상' 클릭 시 텍스트필드 초기화 
const onVeteranStatusChange = () => {
  form.veteranType = undefined;
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
    if (!/^\d$/.test(e.key)) e.preventDefault();
}

// 주소 찾기 클릭 시 
const AddressModalOpen = ref(false)
const AddressSearch = ref<string>('');
const openAddressCode = () => {
  // 초기화 
  searched.value = false;
  AddressSearch.value = '';
  addressSearchList.value = [];

  // 모달 OPEN
  AddressModalOpen.value = true;
}


// 주소 검색
const searched = ref(false)
const addressSearchRef = ref();
const pagination = reactive({ current: 1, pageSize: 7, total: 0 })
const addressSearchList = ref<AddressItem[]>([])

// 주소 검색 클릭 시
function onSerach() {
  pagination.current = 1;
  SearchClick();
}

// GET Addresses
async function SearchClick () {
  const keyword = AddressSearch.value.trim();

  let warnMessage = '';
  if (!keyword)                 warnMessage = '검색어를 입력해주세요.';
  else if (keyword.length < 2)  warnMessage = '두 글자 이상 입력해주세요.';
    
  if (warnMessage) {
    message.warn(warnMessage);
    addressSearchRef.value?.focus();
    addressSearchList.value = [];
    return;
  }
  
  searched.value = true;
  loading.value = true
  try {
    const result = await addressApi.getAddresses({
      keyword: AddressSearch.value,
      currentPage: pagination.current,
      countPerPage: pagination.pageSize,
    })
    addressSearchList.value = result.data.data.addresses
    pagination.total = result.data.data.totalCount
  } catch(error) {
    addressSearchList.value = [];
    logClientEvent({
      eventType: 'API_ERROR',
      severity: 'ERROR',
      pageCode: 'APPLICATION_FORM_BASIC_INFO_ADDRESS_SEARCH',
      operation: 'SEARCH_APPLICATION_BASIC_INFO_ADDRESS',
      applicationId: props.applicationId,
      message: 'APPLICATION_SEARCH_ADDRESS',
    })
    message.error(getApiErrorMessage(error, 'fallback 메세지'))
  } finally {
    loading.value = false
  }
}

function changePage(page: number) {
  pagination.current = page; 
  SearchClick()
}

// 주소 선택시
function selectAddress(address: AddressItem) {
  form.zipCode = address.zipNo;
  form.addressBasic = address.roadAddr;
  form.addressDetail = address.bdNm+' ';

  AddressModalOpen.value = false;

}

// 부모 임시저장 버튼 
const saveDraft = () => {
  vaildation();

  return deleteAttachmentFile().then(result => {
    if (!result.success) throw new Error(result.error);
    return postAttachmentFile();
  })
  .then(result => {
    if (!result.success) throw new Error(result.error);
    return postBasicInfo();
  })
  .then(result => {
    if (result.success) return result.data;
    else throw new Error(result.error);
  });
}

const validateBeforeSubmit = () => {
  vaildation();
  
  return true;
}

const vaildation = () => {
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
  else {
    if (form.veteranStatus ==='SUBJECT' &&  !form.veteranType) throw new Error("보훈 유형을 입력하세요.");
  }

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

function setFileList(list: AttachmentResponse, url?: string) {
  fileList.value = [{
    uid: String(list.attachmentId),
    name: list.originalFileName, 
    status: 'done', 
    url: url
  }]
}

async function loadCommonCode(groupCode: string) {
  loading.value = true
  try {
    const result = await commonCodeApi.getCommonCodes(groupCode)
    
    if (groupCode === 'DISABILITY_GRADE')     disabilityGradeList.value= result.data.data;
    else if(groupCode === 'DISABILITY_TYPE')  disabilityStatusList.value = result.data.data;
    else if(groupCode === 'NATIONALITY')      nationalityList.value = result.data.data || undefined;
    else if(groupCode === 'APPLICATION_ROUTE') applicationRouteList.value = result.data.data;

  } finally {
    loading.value = false
  }
}

// GET AttachmentFile 
async function loadAttachmentFile() {
  loading.value = true
  try {
    const result = await attachmentApi.getApplicationAttachments(props.applicationId)
    const attachments = result.data.data;
    if (attachments.length === 0) return fileList.value = [];

    // 첨부파일 있을 경우, BASIC_INFO에 업로드한 사진만 따로 저장하여 세팅 
    const photos = attachments.filter( attachment => 
      attachment.attachmentType === 'ETC' && attachment.sectionType === 'BASIC_INFO'
    )
    originAttachments.value = photos;

    if (originAttachments.value.length === 0) return fileList.value = [];
    const photo = originAttachments.value[originAttachments.value.length - 1]!
    await downloadAttachment(photo);

  } finally {
    loading.value = false
  }
}

// GET attachmentDownload
async function downloadAttachment(photo: AttachmentResponse) {

  // 이미지 Download 받아서 세팅 
  const image = await attachmentApi.downloadApplicationAttachment(props.applicationId, photo.attachmentId)
  const imageUrl = URL.createObjectURL(image.data);
  setFileList(photo, imageUrl)
}

// POST attachmentFile
async function postAttachmentFile() {
  const file = fileList.value[0]?.originFileObj
  if (!file) return { success: true }

  loading.value = true
  try {
    const formData = new FormData();
    formData.append('file', file)

    const result = await attachmentApi.postApplicationAttachmentsFile(formData, {
      applicationId: props.applicationId,
      attachmentType: "ETC",
      sectionType: 'BASIC_INFO',
    })
    return {success: true, data: result.data.data}
  } catch(error) {
    logClientEvent({
      eventType: 'APPLICATION_SUBMIT_FAILED',
      severity: 'INFO',
      pageCode: 'APPLICATION_FORM_BASIC_INFO',
      operation: 'SUBMIT_APPLICATION_BASIC_INFO',
      applicationId: props.applicationId,
      message: 'APPLICATION_SUBMIT_CLICKED',
    })
    return {success: false, error: getApiErrorMessage(error, 'fallback 메세지')};
  } finally {
    loading.value = false
  }
}

// 파일 delete, post 함수 
async function deleteAttachmentFile() {
  if (!originAttachments.value) return { success: true }
  const currentUid = Number(fileList.value[0]?.uid);
  const deleteTargets = originAttachments.value.filter(item => item.attachmentId !== currentUid)

  if (deleteTargets.length === 0) return { success: true }

  // 삭제 대상 처리 
  loading.value = true
  try {
    for (const attachment of deleteTargets) {
      await attachmentApi.deleteApplicationAttachments(props.applicationId, attachment.attachmentId) 
    }
    originAttachments.value = [];
    return {success: true}
  } catch(error) {
    logClientEvent({
      eventType: 'APPLICATION_SUBMIT_FAILED',
      severity: 'INFO',
      pageCode: 'APPLICATION_FORM_BASIC_INFO',
      operation: 'SUBMIT_APPLICATION_BASIC_INFO',
      applicationId: props.applicationId,
      message: 'APPLICATION_SUBMIT_CLICKED',
    })
    return {success: false, error: getApiErrorMessage(error, 'fallback 메세지')};
  } finally {
    loading.value = false
  }
}

// GET BasicInfo 
async function loadBasicInfo() {
  loading.value = true
  try {
    const result = await basicInfoApi.getApplicationsBasicInfo(props.applicationId)
    Object.assign(form, result.data.data);

  } finally {
    loading.value = false
  }
}


// POST BasicInfo
async function postBasicInfo() {
  loading.value = true
  try {
    const result = await basicInfoApi.postApplicationsBasicInfo(props.applicationId, form);
    return {success: true, data: result.data.data}

  } catch(error) {
    logClientEvent({
      eventType: 'APPLICATION_SUBMIT_FAILED',
      severity: 'INFO',
      pageCode: 'APPLICATION_FORM_BASIC_INFO',
      operation: 'SUBMIT_APPLICATION_BASIC_INFO',
      applicationId: props.applicationId,
      message: 'APPLICATION_SUBMIT_CLICKED',
    })
    return {success: false, error: getApiErrorMessage(error, 'fallback 메세지')};
  }   finally {
    loading.value = false
  }
}

onMounted(() => {
  loadAttachmentFile()
  loadBasicInfo()

  loadCommonCode('DISABILITY_TYPE')
  loadCommonCode('DISABILITY_GRADE')
  loadCommonCode('NATIONALITY')
  loadCommonCode('APPLICATION_ROUTE')
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
.table-area {
    width: 100%;
    border-collapse: collapse;
}
.table-area th, 
.table-area td {
    border: 1px solid #f0f0f0;
    padding: 12px;
}
.table-area th {
    background: #fafafa;
    text-align: left;
}
/* 첫 행 */
.table-area tr:first-child th, 
.table-area tr:first-child td {
    border-top: none;
}
/* 마지막 행 */
.table-area tr:last-child th, 
.table-area tr:last-child td {
    border-bottom: none;
}
/* 첫 컬럼 */
.table-area tr th:first-child, 
.table-area tr td:first-child {
    border-left: none;
}
/* 마지막 컬럼 */
.table-area tr th:last-child, 
.table-area tr td:last-child {
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

.table-border {
  border: 1px solid #f0f0f0;
}

.div-flex {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 주소 검색 리스트 테이블 */
.address-result {
  display: flex;
  flex-direction: column;
  margin-top: 12px;
  border: 1px solid #f0f0f0;
}
.address-card {
  border-bottom: 1px dotted #f0f0f0;
  padding: 8px 12px;
  cursor: pointer;
  transition: .2s;
}
.address-card:last-child {
  border: none;
}
.address-card:hover {
  background-color: var(--app-bg-btn-hover);
}
.card-content {
  display: flex;
  align-items: center;
}
.road {
  font-size: 15px;
  font-weight: 500;
  color: var(--app-text-primary);
  margin-right: 8px;
}
.jibun-tag {
  background-color: #f8faf6;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  display: inline;
  padding: 2px 4px;
  font-size: 12px;
  margin-right: 8px;
}
.jibun {
  color: var(--app-text-secondary);
  display: inline;
  word-break: keep-all;
}
.zip {
  color: var(--app-color-primary-hover);
  font-size: 15px;
  font-weight: 500;
  margin: 0px 10px 0px 3px;
}
.pagination-area {
  justify-items: center;
  padding: 8px;
}

:deep(.ant-form-item) {
  margin-bottom: 0;
}

:deep(.ant-upload-select),
:deep(.ant-upload-list-picture-card-container),
:deep(.ant-upload-list-item-container) {
    margin-bottom: 0 !important;
}

.ant-empty-area {
  margin-top: 24px;
}

:deep(.ant-pagination-options) {
  display: none;
}

</style>
