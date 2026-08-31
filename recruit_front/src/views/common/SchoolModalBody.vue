<template>
    <!-- 학교명, 주간/야간, 본교/분교 모달 -->
        <table class="field-table">
          <colgroup>
            <col style="width: 20%" /><col style="width: 80%" />
          </colgroup>
          <tbody>
            <tr>
              <th>학교명<em> *</em></th>
              <td>
                <div class="div-flex">
                  <a-input v-model:value="searchKeyword" @pressEnter="schoolSearchClick" placeholder="학교명" />
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
              >
                <td>{{ school.schoolName }}<template v-if="school.region">({{ school.region }})</template></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="!props.showExtraOptions" class="input-area">
            <a-input v-model:value="schoolForm.schoolName" placeholder="직접 입력" @input="clearSelectedSchool"/>
        </div>

        <table v-if="props.showExtraOptions" class="field-table">
          <colgroup>
            <col style="width: 20%" /><col style="width: 30%" />
            <col style="width: 20%" /><col style="width: 30%" />
          </colgroup>
          <tbody>
            <tr>
              <td colspan="4">
                <a-input v-model:value="schoolForm.schoolName" placeholder="직접 입력" @input="clearSelectedSchool"/>
              </td>
            </tr>
            <tr  v-if="schoolForm.schoolName">
              <th>주간/야간</th>
              <td>
                <a-select
                  v-model:value="schoolForm.dayNightType" :options="dayNightType"
                  placeholder="선택" style="width: 100%"
                />
              </td>
              <th>본교/분교</th>
              <td>
                <a-select
                  v-model:value="schoolForm.campusType" :options="campusType"
                  placeholder="선택" style="width: 100%"
                />
              </td>
            </tr>
          </tbody>
        </table>

</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue'
import type { schoolItem, schoolSource, educationLevelType, dayNightType, campusType } from '@/types/application/sections/education'
import { educationApi } from '@/api/application/sections/educationApi'
import { getApiErrorMessage } from '@/api/apiError'

interface Props {
    open: boolean;
    showExtraOptions?: boolean;
    educationLevel: string | undefined;
    /* 수정 중인 학력의 기존 학교 값. 모달을 열 때 채워 넣는다. */
    initial?: SchoolSelection;
}
const props = withDefaults(
    defineProps<Props>(),
    {
        showExtraOptions: false,
    },
);
interface SchoolSelection {
    schoolName: string | undefined;
    schoolCode: string | null;
    schoolSource: schoolSource | null;
    dayNightType: dayNightType;
    campusType: campusType;
}
const searchKeyword = ref('');
const searchSchoolList = ref<schoolItem[]>([])
const loading = ref(false)

/* 고교는 NEIS(부분검색 가능), 그 외는 대학 표준데이터(학교명 완전일치)라 안내 문구가 다르다. */
const isUniversitySearch = computed(
  () => !!props.educationLevel && props.educationLevel !== 'HIGH_SCHOOL',
)

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

const initialForm: SchoolSelection = {
    schoolName: undefined,
    schoolCode: null,
    schoolSource: null,
    dayNightType: 'UNKNOWN',
    campusType: 'UNKNOWN',
}

const schoolForm = reactive<SchoolSelection>({
  ...initialForm,
});

defineExpose({
    schoolForm,
});

// 검색 결과에서 학교 선택 시
const selectSchool = (school: schoolItem) => {
    schoolForm.schoolName = school.schoolName;
    schoolForm.schoolCode = school.schoolCode;
    schoolForm.schoolSource = school.schoolSource;
}

// 직접 입력하면 검색으로 고른 학교코드를 버린다(코드와 학교명 불일치 방지)
const clearSelectedSchool = () => {
    schoolForm.schoolCode = null;
    schoolForm.schoolSource = null;
}

const resetSchoolModal = () => {
    searchKeyword.value = '';
    searchSchoolList.value = [];

    Object.assign(schoolForm, initialForm, props.initial ?? {});
}

// 학교 검색 클릭 시
// GET schools
async function schoolSearchClick () {
  const educationLevel = props.educationLevel
  if (!educationLevel) return message.warn('학교 구분을 먼저 선택하세요.')

  loading.value = true
  try {
    const result = await educationApi.getSchools({
      q: searchKeyword.value || '',
      educationLevel: educationLevel as educationLevelType,
    })

    searchSchoolList.value = result.data.data
    if (searchSchoolList.value.length === 0) {
      message.info(
        isUniversitySearch.value
          ? '검색 결과가 없습니다. 대학은 학교명을 전체 입력해야 검색됩니다. 없으면 아래에 직접 입력하세요.'
          : '검색 결과가 없습니다. 아래에 직접 입력하세요.',
      )
    }
  } catch (error) {
    // 외부 학교 검색 API 장애·지연으로 막히면 직접 입력으로 진행할 수 있게 안내한다.
    searchSchoolList.value = []
    message.warning(getApiErrorMessage(error, '학교 검색에 실패했습니다. 아래에 직접 입력하세요.'))
  } finally {
    loading.value = false
  }
}

watch(
    () => props.open,
    (open) => {
        if (open) {
            resetSchoolModal();
        }
    }
)
</script>

<style scoped>
.input-area { 
  width: 100%;
  padding: 12px;
  border: 1px solid #f0f0f0;

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
  background: var(--app-bg-selected);
}
.school-table tr.selected:hover {
  background: #e8f0de;
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

em {
  color: #ff4d4f;
  font-style: normal;
}

</style>
