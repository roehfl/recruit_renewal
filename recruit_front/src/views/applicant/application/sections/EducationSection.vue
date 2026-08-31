<template>
  <div class="section-body">
    <div class="card-list">
      <div v-for="(item, index) in items" :key="index" class="item-card">
        <div class="item-card-head">
          <span class="num-pill">학력 {{ index + 1 }}</span>
          <button type="button" class="remove-btn" @click="removeItem(index)">
            <DeleteOutlined /> 삭제
          </button>
        </div>

        <div class="education-table-scroll">
          <table class="field-table">
            <tbody>
              <tr>
                <th>학교 구분<em> *</em></th>
                <th>학교명<em> *</em></th>
                <th>입학년월</th>
                <th>졸업년월</th>
                <th>편입 여부</th>
                <th>졸업 구분<em> *</em></th>
                <th>전공 및 평점<em> *</em></th>
              </tr>

              <tr>
                <!-- 학교 구분 -->
                <td>
                  <a-select
                    v-model:value="item.educationLevel" :options="educationLevelOptions(item)"
                    placeholder="선택" style="width: 145px"
                  />
                </td>

                <!-- 학교명 -->
                <td>
                  <div class="div-flex">
                    <a-input
                      v-model:value="item.schoolName" @click="openSchoolModal(item)"
                      readonly style="min-width: 130px"
                    />
                    <a-button class="shcoolNameSearchBtn" @click="openSchoolModal(item)" shape="circle">
                      <SearchOutlined />
                    </a-button>
                  </div>
                </td>

                <!-- 입학년월 -->
                <td>
                  <a-date-picker v-if="item.educationLevel !== 'HIGH_SCHOOL'"  
                    v-model:value="item.admissionDate" value-format="YYYY-MM-DD" style="width: 120px"
                  />
                  <div v-else style="text-align: center; width: 120px">-</div>
                </td>

                <!-- 졸업년월 -->
                <td>
                  <a-date-picker v-model:value="item.graduationDate" value-format="YYYY-MM-DD" style="width: 120px" />
                </td>

                <!-- 입학구분 -->
                <td>
                  <div v-if="item.educationLevel !== 'HIGH_SCHOOL'" style="width: 60px">
                    <a-checkbox v-model:checked="item.transfer">편입</a-checkbox>
                  </div>
                  <div v-else style="text-align: center; width: 60px">-</div>
                </td>

                <!-- 졸업구분 -->
                <td>
                  <a-select v-model:value="item.graduationStatus" :options="graduationStatus" placeholder="선택" style="width: 95px"/>
                </td>

                <!-- 전공 -->
                <td>
                  <div v-if="item.educationLevel !== 'HIGH_SCHOOL'" style="text-align: center;">
                    <a @click="openMajorModal(item)">
                      <EditOutlined v-if="item.majorName"/>
                      <FileAddOutlined v-else/>
                      {{ item.majorName ? '수정' : '입력' }} 
                    </a>
                  </div>
                  <div v-else style="text-align: center;">-</div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 학교명, 주간/야간, 본교/분교 모달 -->
      <a-modal
        v-model:open="schoolModalOpen"
        title="학교 찾기"
        @ok="handleSchoolConfirm"
      >
        <table class="field-table">
          <colgroup>
            <col style="width: 20%" /><col style="width: 80%" />
          </colgroup>
          <tbody>
            <tr>
              <th>학교명<em> *</em></th>
              <td>
                <div class="div-flex">
                  <a-input v-model:value="schoolForm.search" @pressEnter="schoolSearchClick" placeholder="학교명" />
                  <a-button @click="schoolSearchClick" type="primary">검색</a-button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        
        <div class="modal-span">
          <span> ※ 아래 학교명을 클릭하세요.</span>
          <!-- 대학 학교정보 OpenAPI는 학교명 완전일치만 지원한다. 부분검색이 안 되는 점을 미리 알린다. -->
          <span v-if="isUniversitySearch"> ※ 대학은 학교명을 전체 입력해야 검색됩니다. (예: 서울대학교)</span>
          <span> ※ 해외 소재 학교인 경우, 공식 명칭을 검색해주세요.</span>
          <span> ※ 검색결과에 없는 경우, 아래에 직접 입력하세요.</span>
        </div>

        <div  class="school-result" v-if="searchSchoolList.length > 0">
          <table class="school-table">
            <tbody>
              <tr
                v-for="school in searchSchoolList"
                :key="school.schoolCode"
                :class="{ selected: schoolForm.schoolCode === school.schoolCode}"
                @click="selectSchool(school)"
                @cancel="cancelSchoolModal"
              >
                <td>{{ school.schoolName }}<template v-if="school.region">({{ school.region }})</template></td>
              </tr>
            </tbody>
          </table>
        </div>

        <table class="field-table">
          <colgroup>
            <col style="width: 20%" /><col style="width: 30%" />
            <col style="width: 20%" /><col style="width: 30%" />
          </colgroup>
          <tbody>
            <tr>
              <td colspan="4">
                <a-input v-model:value="schoolForm.schoolName" @input="clearSelectedSchool" placeholder="직접 입력" />
              </td>
            </tr>
            <tr  v-if="schoolForm.schoolName">
              <th>주간/야간</th>
              <td>
                <a-select
                  v-model:value="schoolForm.dayNightType" :options="dayNightType"
                  palceholder="선택" style="width: 100%"
                />
              </td>
              <th>본교/분교</th>
              <td>
                <a-select
                  v-model:value="schoolForm.campusType" :options="campusType"
                  palceholder="선택" style="width: 100%"
                />
              </td>
            </tr>
          </tbody>
        </table>
      </a-modal>

      <!-- 전공명, 논문명, 학점 모달 -->
      <a-modal
        v-model:open="majorModalOpen"
        title="전공 및 평점 입력"  width="900px"
        @ok="handleMajorConfirm"
        @cancel="cancelMajorModal"
      >
        <table class="field-table">
          <colgroup>
            <col style="width: 14%" /><col style="width: 36%" />
            <col style="width: 14%" /><col style="width: 36%" />
          </colgroup>
          <tbody>
            <tr>
              <th>전공<em> *</em></th>
              <td>
                <a-input v-model:value="majorForm.majorName" placeholder="전공명" />
              </td>
              <th>
                <a-select
                  v-model:value="majorForm.additionalMajorType" :options="majorTypeOptions" :disabled="isGraduate"
                  placeholder="선택" style="width: 100px"
                />
              </th>
              <td>
                <a-input v-model:value="majorForm.additionalMajorName" placeholder="전공명" />
              </td>
            </tr>
            
            <tr v-if="isGraduate">
              <th>논문명</th>
              <td colspan="3">
                <div class="grade-input">
                  <a-textarea v-model:value="majorForm.thesisTitle" :auto-size="{maxRows:3}" placeholder="논문명"/>
                </div>
              </td>
            </tr>
            
            <tr>
              <th>평점 평균<em> *</em></th>
              <td colspan="3">
                <div class="grade-input">
                  <a-input-number v-model:value="majorForm.overallGradePoint" style="width: 80px" :min="0" :step="0.01"/>
                  <span> 점 /  </span>
                  <a-input-number v-model:value="majorForm.overallMaxGradePoint" style="width: 80px" :min="0" :step="0.5"/>
                  <span> 만점</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>

        <div  v-if="isPostingPUBLIC()">
          <div class="modal-span">
            <span> ※ 학력사항에 작성한 모든 학교에 대해 입학년도 순서대로 기재해 주세요. (입학일 기준 과거에서 현재 순)</span>
            <span> ※ 학기별 성적은 정규 학기에 한해 입력해주세요. (계절학기, 초과학기 등은 제외)</span>
          </div>
          <table class="field-table">
            <colgroup>
              <col style="width: 14%" /><col style="width: 43%" /><col style="width: 43%" />
            </colgroup>
            <thead>
              <tr>
                <th>학년</th> <th>1학기</th> <th>2학기</th>
              </tr>    
            </thead>
            <tbody>
              <tr v-for="row in semesterRows" :key="row[0]?.schoolYear">
                <td>{{ row[0]?.schoolYear }}학년</td>
                <td>
                  <div class="grade-input">
                    <a-input-number v-model:value="row[0].gradePoint" style="width: 80px" :min="0" :step="0.01"/>
                    <span> 점 /  </span>
                    <a-input-number v-model:value="row[0].maxGradePoint" style="width: 80px" :min="0" :step="0.5"/>
                    <span> 만점</span>
                  </div>
                </td>
                <td>
                  <div class="grade-input">
                    <a-input-number v-model:value="row[1].gradePoint" style="width: 80px" :min="0" :step="0.01"/>
                    <span> 점 /  </span>
                    <a-input-number v-model:value="row[1].maxGradePoint" style="width: 80px" :min="0" :step="0.5"/>
                    <span> 만점</span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </a-modal>
    </div>

    <div v-if="items.length === 0" class="empty-box">
      <p class="empty-title">등록된 학력이 없습니다.</p>
      <p class="empty-desc">아래 버튼으로 학력을 추가하세요.</p>
    </div>

    <button type="button" class="add-btn" @click="addItem"><PlusOutlined /> 학력 추가</button>
  </div>
</template>

<script setup lang="ts">
import {
  DeleteOutlined,
  PlusOutlined,
  SearchOutlined,
  EditOutlined,
  FileAddOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { ref, reactive, onMounted, computed } from 'vue'
import { commonCodeApi } from '@/api/commonApi'
import { educationApi } from '@/api/application/sections/educationApi'
import { logClientEvent } from '@/common/clientEventLogger'
import { getApiErrorMessage } from '@/api/apiError'
import type {  CommonCodeItems } from '@/types/commonCode'
import type { schoolItem, schoolSource, educationLevelType, graduationStatus, dayNightType, campusType, semesterGradeItem } from '@/types/application/sections/education'
import type { SectionComponentProps } from '@/types/application'
import type {
  EducationItem,
  EducationResponse,
  EducationReplaceRequest,
} from '@/types/application/sections/education'

const loading = ref(false)
const notApplicable = ref(false)
const items = reactive<EducationItem[]>([])
const props = defineProps<SectionComponentProps>()
const searchSchoolList = ref<schoolItem[]>([])

// 수정중인 학력 저장
const selecedEducation = ref<EducationItem | null>(null);

/* 고교는 NEIS(부분검색 가능), 그 외는 대학 표준데이터(학교명 완전일치)라 안내 문구가 다르다. */
const isUniversitySearch = computed(
  () => !!selecedEducation.value?.educationLevel && selecedEducation.value.educationLevel !== 'HIGH_SCHOOL',
)

function createEmptyItem(): EducationItem {
  return {
    educationLevel: undefined,
    schoolName: '',
    majorName: '',
    additionalMajorType: undefined,
    additionalMajorName: undefined,
    thesisTitle: '',
    degreeName: '',
    admissionDate: '',
    graduationDate: '',
    graduationStatus: undefined,
    dayNightType: 'UNKNOWN',
    campusType: 'UNKNOWN',
    transfer: false,
    countryCode: '',
    semesterGrades: [],
    schoolCode: null,
    schoolSource: null,
    overallGradePoint: null,
    overallMaxGradePoint: null,
    overallMajorGradePoint: null,
    overallMajorMaxGradePoint: null,
  }
}

function setItems(list: EducationResponse[]) {
  items.splice(
    0,
    items.length,
    ...list.map((row) => ({
      educationId: row.educationId,
      educationLevel: row.educationLevel,
      schoolName: row.schoolName,
      majorName: row.majorName,
      additionalMajorType: row.additionalMajorType,
      additionalMajorName: row.additionalMajorName,
      thesisTitle: row.thesisTitle,
      degreeName: row.degreeName,
      admissionDate: row.admissionDate ?? '',
      graduationDate: row.graduationDate ?? '',
      graduationStatus: row.graduationStatus,
      dayNightType: row.dayNightType,
      campusType: row.campusType,
      transfer: row.transfer,
      countryCode: row.countryCode,
      semesterGrades: row.semesterGrades ?? [],
      schoolCode: row.schoolCode,
      schoolSource: row.schoolSource,
      overallGradePoint: row.overallGradePoint,
      overallMaxGradePoint: row.overallMaxGradePoint,
      overallMajorGradePoint: row.overallMajorGradePoint,
      overallMajorMaxGradePoint: row.overallMajorMaxGradePoint,
    })),
  )
}

function addItem() {
  items.push(createEmptyItem())
}

function removeItem(index: number) {
  items.splice(index, 1)
}

// 학교 구분
const educationLevelType: { value: educationLevelType; label: string }[] = [
  { value: 'HIGH_SCHOOL', label: '최종 고등학교' },
  { value: 'COLLEGE', label: '전문대학교' },
  { value: 'UNIVERSITY', label: '대학교' },
  { value: 'MASTER', label: '대학원(석사)' },
  { value: 'DOCTOR', label: '대학원(박사)' },
]

// 최종 고등학교는 1개만 입력 가능하므로, 다른 학력이 이미 선택했으면 옵션을 막는다.
function educationLevelOptions(current: EducationItem) {
  const highSchoolTaken = items.some(item => item !== current && item.educationLevel === 'HIGH_SCHOOL')
  return educationLevelType.map(option => ({
    ...option,
    disabled: option.value === 'HIGH_SCHOOL' && highSchoolTaken,
  }))
}

// 졸업 구분
const graduationStatus: { value: graduationStatus; label: string }[] = [
  { value: 'GRADUATED', label: '졸업' },
  { value: 'EXPECTED', label: '졸업예정' },
  { value: 'ENFOLLED', label: '재학' },
  { value: 'LEAVE_OF_ABSENCE', label: '휴학' },
  { value: 'DROPPED_OUT', label: '중퇴' },
  { value: 'COMPLETED', label: '수료' },
]

// 주간 야간
const dayNightType: { value: dayNightType; label: string }[] = [
  { value: 'UNKNOWN', label: '선택' },
  { value: 'DAY', label: '주간' },
  { value: 'NIGHT', label: '야간' },
  { value: 'CYBER', label: '사이버' },
]

// 본교 분교
const campusType: { value: campusType; label: string }[] = [
  { value: 'UNKNOWN', label: '선택' },
  { value: 'MAIN', label: '본교' },
  { value: 'BRANCH', label: '분교' },
]

const isPostingPUBLIC = () => {
  return props.formPage.postingType === 'PUBLIC_RECRUITMENT' || props.formPage.postingType === 'EXPERIENCED_RECRUITMENT'
}

// 학교 검색 모달
const openSchoolModal = ( education : EducationItem) => {
    selecedEducation.value = education;
    if (!selecedEducation.value.educationLevel) return message.warn('학교 구분을 먼저 선택하세요.');

    Object.assign(schoolForm, {
        schoolName: education.schoolName,
        schoolCode: education.schoolCode,
        schoolSource: education.schoolSource,
        dayNightType: education.dayNightType ?? 'UNKNOWN',
        campusType: education.campusType ??'UNKNOWN',

    })
    schoolForm.search = undefined;
    searchSchoolList.value = [];

    schoolModalOpen.value = true;
}
const schoolModalOpen = ref(false)
const schoolForm: {
  search: string | undefined,
  schoolName: string | undefined,
  dayNightType: dayNightType,
  campusType: campusType,
  schoolCode: string | null,
  schoolSource: schoolSource | null,
} = reactive({
  search: undefined,
  schoolName: undefined,
  dayNightType: 'UNKNOWN',
  campusType: 'UNKNOWN',
  schoolCode: null,
  schoolSource: null,
})

const handleSchoolConfirm = ( ) => {
    if (!selecedEducation.value) return;
    if (!schoolForm.schoolName) return message.warn('학교명을 선택하거나 입력하세요.');

    selecedEducation.value.schoolName = schoolForm.schoolName
    selecedEducation.value.schoolCode = schoolForm.schoolCode
    selecedEducation.value.schoolSource = schoolForm.schoolSource
    selecedEducation.value.dayNightType = schoolForm.dayNightType
    selecedEducation.value.campusType = schoolForm.campusType

    schoolModalOpen.value = false
}

// 학교 검색 클릭 시
// GET schools
async function schoolSearchClick () {
  const educationLevel = selecedEducation.value?.educationLevel
  if (!educationLevel) return message.warn('학교 구분을 먼저 선택하세요.')

  loading.value = true
  try {
    const result = await educationApi.getSchools({
      q: schoolForm.search || '',
      educationLevel,
    })

    searchSchoolList.value = result.data.data
    if (searchSchoolList.value.length === 0) {
      message.info(
        isUniversitySearch.value
          ? '검색 결과가 없습니다. 대학은 학교명을 전체 입력해야 검색됩니다. 없으면 아래에 직접 입력하세요.'
          : '검색 결과가 없습니다. 아래에 직접 입력하세요.',
      )
    }
  } catch {
    // 외부 학교 검색 API 장애·지연으로 막히면 직접 입력으로 진행할 수 있게 안내한다.
    searchSchoolList.value = []
    message.warning('학교 검색에 실패했습니다. 아래에 직접 입력하세요.')
  } finally {
    loading.value = false
  }
}

// 검색 결과에서 학교 선택 시
const selectSchool = (school: schoolItem) => {
  schoolForm.schoolName = school.schoolName
  schoolForm.schoolCode = school.schoolCode
  schoolForm.schoolSource = school.schoolSource
}

// 직접 입력하면 검색으로 고른 학교코드를 버린다(코드와 학교명 불일치 방지)
const clearSelectedSchool = () => {
  schoolForm.schoolCode = null
  schoolForm.schoolSource = null
}

const cancelSchoolModal = () => {
  schoolForm.schoolName = undefined
  schoolForm.schoolCode = null
  schoolForm.schoolSource = null
}

// 전공 입력 모달
const openMajorModal = ( education: EducationItem ) => {
    selecedEducation.value = education;
    if (!selecedEducation.value.educationLevel) return message.warn('학교 구분을 먼저 선택하세요.');

    const additionalMajorType = (isGraduate.value)? 'MT_003' : education.additionalMajorType || undefined;
    const semesterGrades = createDefaultSemesterGrades(education.educationLevel);

    education.semesterGrades?.forEach(saveDraft => {
      const target = semesterGrades.find(grade => grade.schoolYear === saveDraft.schoolYear && grade.semester === saveDraft.semester)
      if (target) Object.assign(target, saveDraft)
    })

    Object.assign( majorForm, {
        majorName: education.majorName,
        thesisTitle: education.thesisTitle,
        additionalMajorType: additionalMajorType,
        additionalMajorName: education.additionalMajorName,
        semesterGrades: semesterGrades,
        overallGradePoint: education.overallGradePoint,
        overallMaxGradePoint: education.overallMaxGradePoint,
    })
    
    majorModalOpen.value = true;
}
const majorModalOpen = ref(false)
const majorForm: {
    majorName: string | undefined, thesisTitle: string | undefined, additionalMajorType: string | undefined, additionalMajorName: string | undefined, 
    semesterGrades: semesterGradeItem[], overallGradePoint: number | null, overallMaxGradePoint: number | null
} = reactive({
  majorName: undefined,
  thesisTitle: undefined,
  additionalMajorType: undefined,
  additionalMajorName: undefined,
  semesterGrades: [],
  overallGradePoint: null,
  overallMaxGradePoint: null,
})

// 학교 구분에 따라 학점 개수 
const createDefaultSemesterGrades = ( educationLevel : educationLevelType | undefined ): semesterGradeItem[] => {
    const maxYear = ( educationLevel === "MASTER" || educationLevel === "DOCTOR" )? 3 : 4;
    const grades: semesterGradeItem[] = [];

    for (let year = 1; year <= maxYear; year++) {
        grades.push(
            createSemesterGrade(year, 1),
            createSemesterGrade(year, 2)
        )
    }
    return grades;
}
const createSemesterGrade = (schoolYear: number, semester: number): semesterGradeItem => ({
    schoolYear,
    semester,
    gradePoint: null,
    maxGradePoint: null,
})

const semesterRows = computed<[semesterGradeItem, semesterGradeItem][]>(() => {
    const rows: [semesterGradeItem, semesterGradeItem][] = []

    for (let i=0; i<majorForm.semesterGrades.length; i+=2){
        rows.push([
            majorForm.semesterGrades[i]!,
            majorForm.semesterGrades[i+1]!,
        ])
    }
    return rows;
})
// 전공 타입  
const majorTypeList = ref<CommonCodeItems[]>([])
const majorTypeOptions = computed(() => 
  majorTypeList.value.map( item =>({
    value: item.code,
    label: item.displayName,
  }))
)

// 전공 타입
const isGraduate = computed(() =>  ['MASTER', 'DOCTOR'].includes(selecedEducation.value?.educationLevel ?? '') )

const handleMajorConfirm = () => {
  if (!selecedEducation.value) return;

  // 학점이 모두 입력된 학기만 저장 
  const enterdGrades = majorForm.semesterGrades.filter( item => item.gradePoint != null || item.maxGradePoint != null )

  Object.assign( selecedEducation.value, {
    majorName: majorForm.majorName,
    thesisTitle: majorForm.thesisTitle,
    additionalMajorType: majorForm.additionalMajorType,
    additionalMajorName: majorForm.additionalMajorName,
    semesterGrades: enterdGrades,
    overallGradePoint: majorForm.overallGradePoint,
    overallMaxGradePoint: majorForm.overallMaxGradePoint,
  })

  majorModalOpen.value = false
}

const cancelMajorModal = () => {
    majorForm.majorName = undefined
    majorForm.additionalMajorType = undefined
    majorForm.additionalMajorName = undefined
    majorForm.overallGradePoint = null
    majorForm.overallMaxGradePoint = null
}

// 부모 임시저장 버튼
const saveDraft = () => {
  vaildation()

    return postEducations().then(result => {
      if (result.success) return result.data;
      else                throw new Error(result.error);
    });
}

const validateBeforeSubmit = () => {
  vaildation();

  return true
}

const vaildation = () => {
    if (items.length === 0) {
      if (props.section.required) throw new Error("학력을 추가하세요.")
    }

    if (items.filter(item => item?.educationLevel === 'HIGH_SCHOOL').length > 1) {
      throw new Error('최종 고등학교는 1개만 입력할 수 있습니다.')
    }

    for (let i = 0; i < items.length; i++) {
      const item = items[i]
      if (!item) continue;
      if (!item.schoolName || !item.educationLevel || !item.graduationStatus)  throw new Error(`학력 ${i + 1} : 학교구분, 학교명, 졸업구분은 필수입니다.`);
      if (item.educationLevel !== 'HIGH_SCHOOL' &&  (!item.majorName || !item.overallMaxGradePoint || !item.overallGradePoint) ) {
        throw new Error(`학력 ${i + 1} : 전공 및 평점은 필수입니다.`)
      }
    }
}

// GET education
async function loadEducations() {
  loading.value = true
  try {
    const result = await educationApi.getApplicationsEducations(props.applicationId)
    setItems(result.data.data ?? [])
  } finally {
    loading.value = false
  }
}

function buildPayload(): EducationReplaceRequest {
  if (notApplicable.value) return { educations: [] }
  return {
    educations: items.map((item, index) => ({
      educationLevel: item.educationLevel,
      schoolName: item.schoolName,
      majorName: item.majorName || undefined,
      additionalMajorType: item.additionalMajorType || undefined,
      additionalMajorName: item.additionalMajorName || undefined,
      thesisTitle: item.thesisTitle || undefined,
      degreeName: item.degreeName || undefined,
      admissionDate: item.admissionDate || undefined,
      graduationDate: item.graduationDate || undefined,
      graduationStatus: item.graduationStatus,
      dayNightType: item.dayNightType,
      campusType: item.campusType,
      transfer: item.transfer,
      countryCode: item.countryCode || undefined,
      semesterGrades: item.semesterGrades || [],
      schoolCode: item.schoolCode || null,
      schoolSource: item.schoolSource || null,
      sortOrder: index,
      overallGradePoint: item.overallGradePoint || null,
      overallMaxGradePoint: item.overallMaxGradePoint || null,
    })),
  }
}

// POST education
async function postEducations() {
  loading.value = true
  try {
    const result = await educationApi.postApplicationsEducations(props.applicationId, buildPayload());
    return {success: true, data: result.data.data}
  } catch (error) {
    logClientEvent({
      eventType: 'APPLICATION_DRAFT_SAVE_FAILED',
      severity: 'INFO',
      pageCode: 'APPLICATION_FORM_EDUCATION',
      operation: 'SUBMIT_APPLICATION_EDUCATION',
      applicationId: props.applicationId,
      message: 'APPLICATION_DRAFT_SAVE_FAILED',
    })
    return { success: false, error: getApiErrorMessage(error, 'fallback 메세지') }
  } finally {
    loading.value = false
  }
}

async function loadCommonCode(groupCode: string) {
  loading.value = true
  try {
    const result = await commonCodeApi.getCommonCodes(groupCode)
    majorTypeList.value= result.data.data;
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadEducations()

  loadCommonCode('MAJOR_TYPE');
})

defineExpose({ saveDraft, validateBeforeSubmit })
</script>

<style scoped>
.section-body {
  margin: 24px;
}

.education-table-scroll {
  overflow-x: auto;
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

.modal-span {
  display: flex;
  flex-direction: column;
  margin: 8px;
  gap: 2px;
  color: var(--app-primary-color);
  font-weight: 300;
}

/* 학교 검색 리스트 테이블 */
.school-result {
  max-height: 125px;
  overflow-y: auto;
  border: 1px solid #f0f0f0;
  border-bottom: none;
}

.school-table {
  width: 100%;
  border-collapse: collapse;
}

.school-table tr {
  cursor: pointer;
}

.school-table tr:hover {
  background: #fafafa;
}

.school-table tr.selected {
  font-weight: 600;
  background: #fafafa;
}

.school-table td {
  border-bottom: 1px dotted #f0f0f0;
  padding: 4px 12px;
}

.school-table tr:last-child td {
  border: none;
}

.div-flex {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 학점 테이블 */
.grade-input {
    display: flex;
    align-items: center;
    gap: 4px;
}

em {
  color: #ff4d4f;
  font-style: normal;
}

:deep(.ant-input),
:deep(.ant-picker) {
  width: 100%;
  text-overflow: ellipsis;
}

</style>
