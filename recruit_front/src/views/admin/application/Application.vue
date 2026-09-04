<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { commonCodeApi } from '@/api/commonApi'
import { adminJobPostingApi } from '@/api/admin/adminJobPostingApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { CommonCodeItems } from '@/types/commonCode'
import type { AdminJobPostingDetail, } from '@/types/jobPosting'
import type { availableSectionsItem, AdminApplicationDetailResponse } from '@/types/admin/application'
import type {
  AdminBasicInfoResponse,
  AdminMilitaryResponse,
  AdminEducationResponse,
  AdminCareerResponse,
  AdminCertificateResponse,
  AdminLanguageResponse,
  AdminAwardResponse,
  AdminGapPeriodResponse,
  AdminApplicationAnswerResponse,
  AdminAttachmentResponse,
  AttachmentResponse,
} from '@/types/admin/applicationSections'
import { getLabel } from '@/types/admin/applicationSections'
import { adminApplicationApi } from '@/api/admin/adminApplicationApi'
import { formatDate } from '@/common/dateUtil'
import logoImage from '@/assets/images/logo.png'

interface EditableImage {
  key: string
  id: number | null          // 기존 이미지면 서버 id, 신규면 null
  file: File | null          // 신규 파일
  altText: string
  originalAltText: string    // 수정 여부 판단용
  previewUrl: string
}

const basicInfo = ref<AdminBasicInfoResponse>();
const military = ref<AdminMilitaryResponse>();
const educations = ref<AdminEducationResponse[]>();
const careers = ref<AdminCareerResponse>();
const certificates = ref<AdminCertificateResponse[]>();
const languages = ref<AdminLanguageResponse[]>();
const awards = ref<AdminAwardResponse[]>();
const gapPeriods = ref<AdminGapPeriodResponse[]>();
const answers = ref<AdminApplicationAnswerResponse[]>();
const attachments = ref<AdminAttachmentResponse[]>();

const sectionApiMap = {
  BASIC_INFO: adminApplicationApi.getBasicInfo,
  MILITARY: adminApplicationApi.getMilitary,
  EDUCATION: adminApplicationApi.getEducations,
  CAREER: adminApplicationApi.getCareers,
  CERTIFICATE: adminApplicationApi.getCertificates,
  LANGUAGE: adminApplicationApi.getLanguages,
  AWARD: adminApplicationApi.getAwards,
  GAP_PERIOD: adminApplicationApi.getGapPeriods,
  QUESTION_ANSWER: adminApplicationApi.getAnswers,
  ATTACHMENT: adminApplicationApi.getAttachments,
}
const sectionDataMap = {
  BASIC_INFO: basicInfo,
  MILITARY: military,
  EDUCATION: educations,
  CAREER: careers,
  CERTIFICATE: certificates,
  LANGUAGE: languages,
  AWARD: awards,
  GAP_PERIOD: gapPeriods,
  QUESTION_ANSWER: answers,
  ATTACHMENT: attachments,
}
const applicationTypeMap: Record<string, string> = {
  NEW_GRADUATE: '신입',
  EXPERIENCED: '경력',
  NEW_GRADUATE_OR_EXPERIENCED: '신입/경력',
}
const route = useRoute()
const router = useRouter();
const loading = ref(false)
const isPhotoLoading = ref(true)
const applicationId = Number(route.params.applicationId)

const jobPostingData = ref<AdminJobPostingDetail>()
const jobPositionData = ref()
const applicationData = ref<AdminApplicationDetailResponse>()
const formLayouut = ref()
const sectionData = ref<availableSectionsItem[]>([])
const images = ref<EditableImage[]>([])
const originAttachments = ref<AttachmentResponse[]>([])
const photoUrls = ref<string>()

const nationalityList = ref<CommonCodeItems[]>([])
const disabilityStatusList = ref<CommonCodeItems[]>([])
const disabilityGradeList = ref<CommonCodeItems[]>([])

const semesterList = [
  { schoolYear: 1, semester: 1 },
  { schoolYear: 1, semester: 2 },
  { schoolYear: 2, semester: 1 },
  { schoolYear: 2, semester: 2 },
  { schoolYear: 3, semester: 1 },
  { schoolYear: 3, semester: 2 },
  { schoolYear: 4, semester: 1 },
  { schoolYear: 4, semester: 2 },
]

const getSemesterGradeText = (education: AdminEducationResponse, semester: {schoolYear: number, semester: number}) => {
  // 대학원(석사/박사)은 4학년 자체가 입력 대상이 아님
  if ( (education.educationLevel === "MASTER" || education.educationLevel === "DOCTOR") && semester.schoolYear === 4 ) { 
    return '-'
  }

  const grade = education.semesterGrades?.find( (item) => item.schoolYear === semester.schoolYear && item.semester === semester.semester);

  // 해당 학기에 입력된 성적이 없으면 빈칸
  if (!grade) return '';
  return `${grade.gradePoint} / ${grade.maxGradePoint}`;
}

const formatPhoneNumber = (phone?: string) => {
  if (!phone) return '';
  return phone.replace(/(\d{3})(\d{4})(\d{4})/, '$1-$2-$3');
}

// 목록 페이지로 이동 
function goRecruitPage(): void {
  router.back();
  // router.push('/admin/applications');
}

// SECTION enabled API 호출
const getApplicationSections = async (applicationId: number, availableSections: availableSectionsItem[],) => {
  const enabledSections = availableSections.filter( (section) => section.enabled );

  await Promise.all(
    enabledSections.map(async (section) => {
      const api = sectionApiMap[section.sectionType];
      const target = sectionDataMap[section.sectionType];

      if (!api || !target) return null;

      const response = await api(applicationId);

      target.value = response.data.data;
    }),
  );
}

async function loadCommonCode(groupCode: string) {
  loading.value = true
  try {
    const result = await commonCodeApi.getCommonCodes(groupCode)
    
    if (groupCode === 'DISABILITY_GRADE')     disabilityGradeList.value= result.data.data;
    else if(groupCode === 'DISABILITY_TYPE')  disabilityStatusList.value = result.data.data;
    else if(groupCode === 'NATIONALITY')      nationalityList.value = result.data.data || undefined;

  } finally {
    loading.value = false
  }
}

// GET AttachmentFile 
async function loadAttachmentFile() {
  isPhotoLoading.value = true
  try {
    const result = await adminApplicationApi.getAttachments(applicationId)
    const attachments = result.data.data;

    // 첨부파일 있을 경우, BASIC_INFO에 업로드한 사진만 따로 저장하여 세팅 
    const photos = attachments.filter( attachment => 
      attachment.attachmentType === 'ETC' && attachment.sectionType === 'BASIC_INFO'
    )
    originAttachments.value = photos;

    const photo = originAttachments.value[originAttachments.value.length - 1]!
    await downloadAttachment(photo);

  } finally {
    isPhotoLoading.value = false
  }
}

// GET attachmentDownload
async function downloadAttachment(photo: AttachmentResponse) {
  if (!photo) return;

  // 이미지 Download 받아서 세팅 
  const image = await adminApplicationApi.downloadApplicationAttachment(applicationId, photo.attachmentId)
  photoUrls.value = URL.createObjectURL(image.data);
}


onMounted(async () => {
  loading.value = true
  try {
    // 1. 지원서 조회 -> jobPostingID 추출
    const applicationResponse = await adminApplicationApi.getApplication(applicationId);
    applicationData.value = applicationResponse.data.data;
    const jobPostingId = applicationData.value.jobPostingId;

    // 2. application-form-layout 조회
    const applicationFormLayoutResponse = await adminApplicationApi.getApplicationFormLayout(jobPostingId);
    formLayouut.value = applicationFormLayoutResponse.data.data;
    sectionData.value = formLayouut.value.availableSections;

    // 2-1. job-posting 조회 
    const jobPostingResponse = await adminJobPostingApi.getJobPosting(jobPostingId)
    jobPostingData.value = jobPostingResponse.data.data
    jobPositionData.value = jobPostingData.value.jobPositions.find(position => position.id === applicationData.value?.jobPositionId)

    // 3. enabled section API 조회
    await getApplicationSections(applicationId, sectionData.value);
    
    loadAttachmentFile()
      
    loadCommonCode('DISABILITY_TYPE')
    loadCommonCode('DISABILITY_GRADE')
    loadCommonCode('NATIONALITY')

  } catch (error) {
    message.error(getApiErrorMessage(error, '지원서 정보를 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }

})
onBeforeUnmount(() => {
  images.value.forEach((image) => URL.revokeObjectURL(image.previewUrl))
})
</script>

<template>
  <div class="application-form">
    <header class="page-header">
      <h2 class="page-title">입사지원서</h2>
      <a-button @click="goRecruitPage">목록</a-button>
    </header>

    <a-spin :spinning="loading">
      <a-card title="지원사항" :bordered="false" class="form-card">
        <table class="table-area">
          <colgroup>
            <col style="width: 10%;"> <col style="width: 35%;"> <col style="width: 10%;"> <col style="width: 45%;">
          </colgroup>
          <tbody>
            <tr>
              <th>채용구분 </th>
              <td>{{ jobPostingData?.title }}</td>
              <th>지원구분 </th>
              <td>{{ applicationTypeMap[jobPositionData?.applicationType] }}</td>
            </tr>
            <tr>
              <th>지원분야 </th>
              <td>{{ jobPositionData?.positionName }}</td>
              <th>직무/근무지 </th>
              <td>{{ applicationData?.workLocationNameSnapshot }}</td>
            </tr>
          </tbody>
        </table>
      </a-card>

      <a-card title="기본 정보" :bordered="false" class="form-card">
        <div aria-label="기본정보">
        <table class="table-area">
          <colgroup>
            <col style="width: 10%;">  <col style="width: 25%;">
            <col style="width: 10%;">  <col style="width: 10%;">  <col style="width: 45%;">
          </colgroup>
          <tbody>
            <tr>
              <th rowspan="4">사진</th>
              <td rowspan="4">
                <span class="brand-logo">
                  <template v-if="!isPhotoLoading">
                    <img v-if="photoUrls" :src="photoUrls" :alt="photoUrls" class="posting-image"/>
                    <img v-else :src="logoImage" alt="신영증권 로고" />
                  </template>
                  <!-- <img :src="photoUrls || logoImage" :alt="photoUrls" class="posting-image"/>
                  <img v-if="photoUrls" :src="photoUrls || logoImage" :alt="photoUrls" class="posting-image"/>
                  <img v-else :src="logoImage" alt="신영증권 로고" /> -->
                </span>
              </td>

              <th rowspan="2">이름</th>
              <th class="depth1">한글명</th>
              <td>{{ basicInfo?.nameKorean }}</td>
            </tr>
            <tr>
              <th class="depth1">영문명</th>
              <td>{{ basicInfo?.nameEnglish }}</td>
            </tr>
            <tr>
              <th rowspan="2">연락처</th>
              <th class="depth1">휴대폰</th>
              <td>{{ formatPhoneNumber(basicInfo?.mobilePhone) }}</td>
              <!-- <td>{{ basicInfo?.mobilePhone }}</td> -->
            </tr>
            <tr>
              <th class="depth1">비상연락처</th>
              <td>{{ formatPhoneNumber(basicInfo?.emergencyPhone) }}</td>
            </tr>

            <tr>
              <th>내/외국인</th>
              <td v-if="!basicInfo?.nationalityType"></td>
              <td v-else-if="basicInfo?.nationalityType === 'DOMESTIC'">내국인</td>
              <td v-else> 
                외국인 ({{ nationalityList.find(item => item.code === basicInfo?.countryCode)?.displayName  }})
              </td>

              <th>보훈여부</th>
              <td colspan="2" v-if="!basicInfo?.veteranStatus"></td>
              <td colspan="2" v-else-if="basicInfo?.veteranStatus === 'NOT_SUBJECT'">비대상</td>
              <td colspan="2" v-else> 
                대상 ({{ basicInfo?.veteranType }})
              </td>
            </tr>

            <tr>
              <th>생년월일</th>
              <td> {{ basicInfo?.birthDate }} </td>

              <th>장애여부</th>
              <td colspan="2" v-if="!basicInfo?.disabilityStatus"></td>
              <td colspan="2" v-else-if="basicInfo?.disabilityStatus === 'NOT_SUBJECT'">비대상</td>
              <td colspan="2" v-else> 
                대상 (등급: {{ disabilityGradeList.find(item => item.code === basicInfo?.disabilityGradeCode)?.displayName  }}
                / 유형:  {{ disabilityStatusList.find(item => item.code === basicInfo?.disabilityTypeCode)?.displayName  }})
              </td>
            </tr>

            <tr>
              <th>이메일</th>
              <td> {{ basicInfo?.email }} </td>
              <th style="border-bottom: 0;">주소</th>
              <td colspan="2">
                <div class="field">
                  <div v-if="basicInfo?.zipCode"> ({{ basicInfo?.zipCode }}) </div>
                  <div v-if="basicInfo?.addressBasic"> {{ basicInfo?.addressBasic }}</div>
                  <div v-if="basicInfo?.addressDetail">, {{ basicInfo?.addressDetail }}</div>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </a-card>

    <a-card title="병역사항" v-if="military" :bordered="false" class="form-card">
      <div aria-label="병역">
        <table class="table-area">
          <colgroup>
            <col style="width: 10%" /><col style="width: 15%" /><col style="width: 5%" /><col style="width: 15%" />
            <col style="width: 5%" /><col style="width: 10%" /><col style="width: 10%" /><col style="width: 30%" />
          </colgroup>
          <tbody>
            <tr>
              <th>군필여부</th>
              <td v-if="military?.militarySubjectType === 'COMPLETED'">필</td>
              <td v-else-if="military?.militarySubjectType === 'SUBJECT'">미필 (사유: {{ military.nonServiceReasonMasked }})</td>
              <td v-else-if="military?.militarySubjectType === 'EXEMPTED'">면제 (사유: {{ military.nonServiceReasonMasked }})</td>
              <td v-else-if="military?.militarySubjectType === 'NOT_SUBJECT'">대상아님</td>
              <td v-else></td>
              <th>군벌</th>
              <td v-if="military?.militarySubjectType === 'COMPLETED'">
                {{ getLabel('MILITARY', 'militaryBranchType', military.militaryBranch) }} {{ getLabel('MILITARY', 'militaryServiceType', military.serviceType) }} 
              </td>
              <td v-else class="text-center">-</td>
              <th>계급</th>
              <td v-if="military?.militarySubjectType === 'COMPLETED'">
                {{ getLabel('MILITARY', 'militaryRankType', military.rank) }}
              </td>
              <td v-else class="text-center">-</td>
              <th>복무기간</th>
              <td  v-if="military?.militarySubjectType === 'COMPLETED'">
                {{ military.serviceStartDate }} ~ {{ military.serviceEndDate }}
              </td>
              <td v-else class="text-center">-</td>
            </tr>
          </tbody>
        </table>
      </div>
    </a-card>

    <a-card title="학력사항" v-if="educations" :bordered="false" class="form-card">
      <div aria-label="학력">
        <table class="table-area">
          <thead>
            <tr>
              <th>학교 구분</th><th>학교명</th><th>입학년월</th><th>졸업년월</th><th>졸업 구분</th><th>전공</th><th>평점 평균</th>
            </tr>
          </thead>
          <tbody>
            <template v-if="educations.length">
              <tr v-for="education in educations" :key="education.educationId">
                <!-- 학교 구분 -->
                <td> {{ getLabel('EDUCATION', 'educationLevelType', education?.educationLevel) }} </td>
                <!-- 학교명 -->
                <td> {{ education?.schoolName }} </td>
                <!-- 입학년월 -->
                <td > {{ education?.admissionDate }} </td>
                <!-- 졸업년월 -->
                <td> {{ education?.graduationDate }} </td>
                <!-- 졸업구분 -->
                <td> {{ getLabel('EDUCATION', 'graduationStatus', education?.graduationStatus) }} </td>
                <!-- 전공 -->
                <td v-if="education.educationLevel !== 'HIGH_SCHOOL'"> {{ education?.majorName }} </td>
                <td v-else class="text-center">-</td>
                <!-- 평점  -->
                <td v-if="education.educationLevel !== 'HIGH_SCHOOL'"> {{ education?.overallGradePoint }} / {{ education?.overallMaxGradePoint }} </td>
                <td v-else class="text-center">-</td>
              </tr>
            </template>
            <tr v-else>
              <td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="jobPostingData?.postingType === `PUBLIC_RECRUITMENT`" aria-label="성적" class="education-div">
        <table class="table-area">
          <colgroup>
            <col style="width:28%"/>
            <col style="width:8%"/><col style="width:8%"/><col style="width:8%"/><col style="width:8%"/>
            <col style="width:8%"/><col style="width:8%"/><col style="width:8%"/><col style="width:8%"/>
          </colgroup>
          <thead>
            <tr>
              <th rowspan="2">학교 학기별 성적</th>
              <th colspan="2" class="text-center">1학년</th><th colspan="2" class="text-center">2학년</th>
              <th colspan="2" class="text-center">3학년</th><th colspan="2" class="text-center">4학년</th>
            </tr>    
            <tr class="text-center">
              <th>1학기</th><th>2학기</th><th>1학기</th><th>2학기</th><th>1학기</th><th>2학기</th><th>1학기</th><th>2학기</th>
            </tr>
          </thead>
          <tbody>
            <template v-if="educations.length">
              <tr  v-for="education in educations" :key="education.educationId" >
                <template v-if="education.educationLevel !== 'HIGH_SCHOOL'" >
                  <td>{{ education.schoolName }}</td>
                  <td v-for="semester in semesterList" :key="`${semester.schoolYear}-${semester.semester}`" class="text-center">
                    {{ getSemesterGradeText(education, semester) }} 
                  </td>
                </template>
              </tr>
            </template>
            <tr v-else>
              <td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td>
            </tr>
          </tbody>
        </table>
      </div>
    </a-card>

    <a-card title="경력사항" v-if="careers" :bordered="false" class="form-card">
      <div aria-label="경력">
        <table class="table-area">
          <thead>
            <tr>
              <th>회사명(소재지)</th><th>부서명(담당업무)</th><th>근무기간</th><th>고용형태</th><th colspan="2">최종직급(승진일)</th><th>연봉(만원)</th><th>퇴직 사유</th>
            </tr>
          </thead>
          <tbody>
            <template v-if="careers?.careers.length">
              <tr v-for="career in careers?.careers" :key="career.careerId" >
                <!-- 회사명(소재지) -->
                <td> {{ career.companyName }} </td>
                <!-- 부서명(담당업무) -->
                <td> {{ career.departmentName }} </td>
                <!-- 근무기간 -->
                <td v-if="career.endDate"> {{ career.startDate }} ~ {{ career.endDate }} </td>
                <td v-else> {{ career.startDate }} ~ 재직중 </td>
                <!-- 고용형태 employmentType-->
                <td> {{ getLabel('CAREER', 'employmentType', career.employmentType) }} </td>
                <!-- 최종직급 -->
                <td> {{ career.positionTitle }} </td>
                <!-- 승진일 -->
                <td> {{ formatDate(career.promotionDate, 'YYYY-MM') }} </td>
                <!-- 연봉  -->
                <td> {{ Number(career.currentSalary).toLocaleString() }} </td>
                <!-- 퇴직사유   -->
                <td> {{ career.resignationReason }} </td>
              </tr>
            </template>
            <tr v-else>
              <td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td>
            </tr>
          </tbody>
        </table>
      </div>
    </a-card>

    <a-card title="자격사항" v-if="certificates" :bordered="false" class="form-card">
      <div aria-label="자격">
        <table class="table-area">
          <colgroup>
            <!-- <col style="width: 45%" /><col style="width: 25%" /><col style="width: 15%" /><col style="width: 15%" /> -->
          </colgroup>
          <thead>
            <tr>
              <th>자격증</th><th>발급기관</th><th>취득일자</th><th>자격증번호</th>
            </tr>
          </thead>
          <tbody>
            <template v-if="certificates.length">
              <tr v-for="certificate in certificates" :key="certificate.certificateId">
                <td> {{ certificate.certificateName }} </td>
                <td> {{ certificate.issuingOrganization }} </td>
                <td> {{ certificate.acquiredDate }} </td>
                <td> {{ certificate.certificateNumberMasked }} </td>
              </tr>
            </template>
            <tr v-else>
              <td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td>
            </tr>
          </tbody>
        </table>
      </div>
    </a-card>

    <a-card title="어학" v-if="languages" :bordered="false" class="form-card">
      <div aria-label="어학">
        <table class="table-area">
          <colgroup>
            <col style="width: 10%" /><col style="width: 35%" /><col style="width: 25%" /><col style="width: 15%" /><col style="width: 15%" />
          </colgroup>
          <thead>
            <tr>
              <th>언어</th><th>시험명</th><th>응시일자</th><th>점수/등급</th><th>회화능력</th>
            </tr>
          </thead>
          <tbody>
            <template v-if="languages.length">
              <tr v-for="language in languages" :key="language.languageId">
                <td> {{ language.languageName }} </td>
                <td> {{ language.testName }} </td>
                <td> {{ language.examDate }} </td>
                <td> {{ language.scoreOrGrade }} </td>
                <td> {{ language.conversationalAbility }} </td>
              </tr>
            </template>
            <tr v-else>
              <td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td>
            </tr>
          </tbody>
        </table>
      </div>
    </a-card>

    <a-card title="수상" v-if="awards" :bordered="false" class="form-card">
      <div aria-label="수상">
        <table class="table-area">
          <colgroup>
            <col style="width: 45%" /><col style="width: 40%" /><col style="width: 15%" />
          </colgroup>
          <thead>
            <tr>
              <th>수상명</th><th>수여기관</th><th>수상일자</th>
            </tr>
          </thead>
          <tbody>
            <template v-if="awards.length">
              <tr v-for="award in awards" :key="award.awardId">
                <td> {{ award.awardName }} </td>
                <td> {{ award.awardingOrganization }} </td>
                <td> {{ formatDate(award.awardDate, 'YYYY-MM') }} </td>
              </tr>
            </template>
            <tr v-else>
              <td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td>
            </tr>
          </tbody>
        </table>
      </div>
    </a-card>

    <a-card title="공백기간" v-if="gapPeriods" :bordered="false" class="form-card">
      <div aria-label="공백기간">
        <table class="table-area">
          <colgroup>
            <col style="width: 10%" /><col style="width: 20%" /><col style="width: 15%" /><col style="width: 55%" />
          </colgroup>
          <thead>
            <tr>
              <th>구분</th><th>기간</th><th>사유</th><th>상세 설명</th>
            </tr>
          </thead>
          <tbody>
            <template v-if="gapPeriods.length">
              <tr v-for="gapPeriod in gapPeriods" :key="gapPeriod.gapPeriodId">
                <td> {{ getLabel('GAP_PERIOD', 'gapType', gapPeriod.gapType) }} </td>
                <td> {{ formatDate(gapPeriod.startDate, 'YYYY-MM') }} ~ {{ formatDate(gapPeriod.endDate, 'YYYY-MM') }} </td>
                <td> {{ gapPeriod.reason }} </td>
                <td> {{ gapPeriod.description }} </td>
              </tr>
            </template>
            <tr v-else>
              <td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td>
            </tr>
          </tbody>
        </table>
      </div>
    </a-card>

    <a-card title="자기소개서" v-if="answers" :bordered="false" class="form-card">
      <div aria-label="자기소개서">
        <div v-for="item in answers" :key="item.questionId" class="item-card">
          <p class="q-text">{{ Number(item.sortOrder) + 1 }}. {{ item.questionText }}</p>
          <a-input class="input-area" v-if="item.answerType === 'SHORT_TEXT'"
            v-model:value="item.answerText" :maxlength="item.maxLength ?? 5000" show-count readonly
          />
          <a-textarea class="input-area" v-else
            v-model:value="item.answerText" :maxlength="item.maxLength ?? 5000" show-count readonly
          />
        </div>
      </div>
    </a-card>

    </a-spin>
  </div>
</template>

<style scoped lang="scss">
.application-form {
  padding: 24px;
  max-width: 1080px;
}
.page-header {
  margin-bottom: 16px;
  display: flex;
  justify-content: space-between;
}
.page-title {
  margin: 0 0 4px;
}
.page-description {
  margin: 0;
  color: #888;
}
.form-card {
  margin-bottom: 16px;
}

.table-area {
    width: 100%;
    border: 1px solid #f0f0f0;
    border-collapse: collapse;
}
.table-area th, 
.table-area td {
    border: 1px solid #f0f0f0;
    padding: 8px;
}
.table-area th {
    background: #fafafa;
    text-align: left;
    font-weight: 600;
}
.table-area .depth1 {
  font-weight: normal;
}

.field {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.field > div {
  white-space: nowrap;
}

.brand-logo {
  width: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;

  img {
    width: 100px;
    height: 130px;
    object-fit: contain;
  }
}

.education-div {
  margin-top: 20px;
}

.semester-head th {
  padding: 4px 0;
  text-align: center;
}

.text-center,
.text-center th {
  text-align: center !important;

}

.q-text {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 500;
  color: #1f2937;
  line-height: 1.5;
}
.input-area {
  margin-bottom: 12px;
}
:deep(.ant-input) {
  cursor: default;
}
:deep(.ant-input:hover),
:deep(.ant-input:focus),
:deep(.ant-input-affix-wrapper:hover),
:deep(.ant-input-affix-wrapper-focused) {
  border-color: #d9d9d9;
  box-shadow: none;
}
</style>
