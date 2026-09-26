<template>
  <div class="interview-supplement">
    <header class="page-header">
      <h2 class="page-title">면접 추가사항 관리</h2>
      <p class="page-description">
        면접단계마다 추가사항을 받을지 정하고, 질문과 지원자별 입력 가능 시간을 설정하고, 작성된 답변을 봅니다.
        입력 시간은 기본으로 <b>지원자 조의 도착시간 ~ +2시간</b>입니다.
      </p>
    </header>

    <a-card :bordered="false" class="form-card">
      <div class="filter-row">
        <div class="filter-field">
          <label>채용공고</label>
          <a-select
            :value="selectedJobPostingId"
            class="posting-select"
            placeholder="공고를 선택하세요"
            :options="jobPostingOptions"
            :disabled="initializing || jobPostings.length === 0"
            show-search
            option-filter-prop="label"
            @change="changeJobPosting"
          />
        </div>
        <div class="filter-field">
          <label>면접단계</label>
          <a-radio-group
            v-if="interviewStages.length > 0"
            :value="selectedStageId"
            button-style="solid"
            @change="(event: RadioChangeEvent) => changeStage(event.target.value)"
          >
            <a-radio-button v-for="stage in interviewStages" :key="stage.id" :value="stage.id">
              {{ stage.stageName }}
            </a-radio-button>
          </a-radio-group>
          <span v-else-if="selectedJobPostingId !== null && !stageLoading" class="muted">
            면접 단계가 없습니다. 전형결과 관리의 단계 설정에서 면접 단계를 추가하세요.
          </span>
        </div>
        <div v-if="supplement?.enabled" class="filter-summary">
          <span>면접 조 <b>{{ groupCount }}개</b></span>
          <span>대상 지원자 <b>{{ candidates.length }}명</b></span>
        </div>
      </div>
    </a-card>

    <a-spin :spinning="loading">
      <a-card v-if="supplement && !supplement.enabled" :bordered="false" class="off-card">
        <div class="off-icon"><StopOutlined /></div>
        <h3>이 면접단계는 추가사항을 받지 않습니다</h3>
        <p>지원자 마이페이지에도 이 면접의 추가사항 입력 버튼이 나오지 않습니다.<br />추가사항을 받으려면 켜고 질문을 등록하세요.</p>
        <a-button type="primary" size="large" @click="enable"><PlusOutlined />이 면접에서 추가사항 받기</a-button>
      </a-card>

      <a-card v-else-if="supplement && selectedStageId !== null" :bordered="false" class="form-card tabs-card">
        <a-tabs v-model:active-key="activeTab">
          <template #rightExtra>
            <a-button type="text" danger size="small" @click="confirmDisable"><PoweroffOutlined />추가사항 받지 않기</a-button>
          </template>
          <a-tab-pane key="questions">
            <template #tab>질문 설정 <span class="tab-count">{{ supplement.questions.length }}</span></template>
            <InterviewSupplementQuestionTab
              :stage-id="selectedStageId"
              :supplement="supplement"
              :live-count="liveCount"
              @update="supplement = $event"
            />
          </a-tab-pane>
          <a-tab-pane key="windows">
            <template #tab>입력 시간 설정 <span class="tab-count">{{ candidates.length }}</span></template>
            <InterviewSupplementWindowTab
              :stage-id="selectedStageId"
              :candidates="candidates"
              @update="candidates = $event"
            />
          </a-tab-pane>
          <a-tab-pane key="answers">
            <template #tab>답변 조회 <span class="tab-count">{{ answeredCount }} / {{ candidates.length }}</span></template>
            <InterviewSupplementAnswerTab
              :stage-id="selectedStageId"
              :candidates="candidates"
              :question-count="supplement.questions.length"
            />
          </a-tab-pane>
        </a-tabs>
      </a-card>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message, Modal, type RadioChangeEvent } from 'ant-design-vue'
import { PlusOutlined, PoweroffOutlined, StopOutlined } from '@ant-design/icons-vue'
import { getAllJobPostings } from '@/api/adminJobPostingApi'
import { adminStageApi } from '@/api/admin/adminStageApi'
import { adminInterviewSupplementApi } from '@/api/admin/adminInterviewSupplementApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { AdminJobPostingListItem } from '@/types/jobPosting'
import type { StageListItem, StageType } from '@/types/admin/stage'
import type { AdminInterviewSupplement, AdminInterviewSupplementCandidate } from '@/types/admin/interviewSupplement'
import InterviewSupplementQuestionTab from './InterviewSupplementQuestionTab.vue'
import InterviewSupplementWindowTab from './InterviewSupplementWindowTab.vue'
import InterviewSupplementAnswerTab from './InterviewSupplementAnswerTab.vue'
import { isWithinWindow } from './interviewSupplementTime'

/* 면접 유형 단계만 추가사항을 가진다(백엔드 InterviewSupplementAdminService 와 같은 기준) */
const INTERVIEW_STAGE_TYPES: StageType[] = ['FIRST_INTERVIEW', 'SECOND_INTERVIEW', 'FINAL_INTERVIEW']

const initializing = ref(true)
const stageLoading = ref(false)
const loading = ref(false)
const activeTab = ref('questions')

const jobPostings = ref<AdminJobPostingListItem[]>([])
const selectedJobPostingId = ref<number | null>(null)
const stages = ref<StageListItem[]>([])
const selectedStageId = ref<number | null>(null)
const supplement = ref<AdminInterviewSupplement | null>(null)
const candidates = ref<AdminInterviewSupplementCandidate[]>([])

const jobPostingOptions = computed(() =>
  jobPostings.value.map((posting) => ({ value: posting.id, label: posting.title })),
)
const interviewStages = computed(() =>
  stages.value
    .filter((stage) => INTERVIEW_STAGE_TYPES.includes(stage.stageType))
    .sort((a, b) => a.stageOrder - b.stageOrder),
)
const groupCount = computed(() => new Set(candidates.value.map((c) => c.interviewId)).size)
const answeredCount = computed(() => candidates.value.filter((c) => c.answeredCount > 0).length)
const liveCount = computed(() =>
  candidates.value.filter((c) => isWithinWindow(c.startDateTime, c.endDateTime)).length,
)

/* 공고·단계를 빠르게 바꾸면 이전 응답이 늦게 올 수 있다. 가장 최근 요청의 응답만 반영한다. */
let requestSeq = 0

const changeJobPosting = async (jobPostingId: number): Promise<void> => {
  const seq = ++requestSeq
  selectedJobPostingId.value = jobPostingId
  stages.value = []
  selectedStageId.value = null
  supplement.value = null
  candidates.value = []
  stageLoading.value = true
  try {
    const response = await adminStageApi.getStages(jobPostingId)
    if (seq !== requestSeq) return
    stages.value = response.data.data
    const defaultStage = interviewStages.value.find((stage) => stage.status === 'IN_PROGRESS')
      ?? interviewStages.value.find((stage) => stage.status === 'READY')
      ?? interviewStages.value[0]
    if (defaultStage) {
      await changeStage(defaultStage.id)
    }
  } catch (error) {
    if (seq !== requestSeq) return
    message.error(getApiErrorMessage(error, '면접단계를 불러오지 못했습니다.'))
  } finally {
    if (seq === requestSeq) stageLoading.value = false
  }
}

const changeStage = async (stageId: number): Promise<void> => {
  const seq = ++requestSeq
  selectedStageId.value = stageId
  supplement.value = null
  candidates.value = []
  loading.value = true
  try {
    const response = await adminInterviewSupplementApi.getSupplement(stageId)
    if (seq !== requestSeq) return
    supplement.value = response.data.data
    if (supplement.value.enabled) {
      await loadCandidates(stageId, seq)
    }
  } catch (error) {
    if (seq !== requestSeq) return
    message.error(getApiErrorMessage(error, '추가사항 설정을 불러오지 못했습니다.'))
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

const loadCandidates = async (stageId: number, seq: number): Promise<void> => {
  const response = await adminInterviewSupplementApi.getCandidates(stageId)
  if (seq !== requestSeq) return
  candidates.value = response.data.data
}

const enable = async (): Promise<void> => {
  if (selectedStageId.value === null) return
  const stageId = selectedStageId.value
  loading.value = true
  try {
    const response = await adminInterviewSupplementApi.enable(stageId)
    supplement.value = response.data.data
    activeTab.value = 'questions'
    await loadCandidates(stageId, requestSeq)
    message.success('추가사항을 받습니다. 질문을 등록하세요.')
  } catch (error) {
    message.error(getApiErrorMessage(error, '추가사항을 켜지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

const confirmDisable = (): void => {
  if (!supplement.value || selectedStageId.value === null) return
  const stageId = selectedStageId.value
  if (supplement.value.answeredApplicantCount > 0) {
    Modal.warning({
      title: '추가사항을 끌 수 없습니다',
      content: `이미 답변을 작성한 지원자가 ${supplement.value.answeredApplicantCount}명 있습니다. 작성된 답변을 보호하기 위해 끌 수 없습니다.`,
    })
    return
  }
  Modal.confirm({
    title: '추가사항 받지 않기',
    content: `등록한 질문 ${supplement.value.questions.length}개와 지원자별로 바꾼 입력 시간이 삭제되고, 지원자 화면에서 버튼이 사라집니다.`,
    okText: '받지 않기',
    okType: 'danger',
    cancelText: '취소',
    onOk: async () => {
      try {
        const response = await adminInterviewSupplementApi.disable(stageId)
        supplement.value = response.data.data
        candidates.value = []
        message.success('추가사항을 받지 않도록 바꿨습니다.')
      } catch (error) {
        message.error(getApiErrorMessage(error, '추가사항을 끄지 못했습니다.'))
      }
    },
  })
}

onMounted(async () => {
  try {
    jobPostings.value = await getAllJobPostings()
    const defaultPosting = jobPostings.value[0]
    if (defaultPosting) {
      await changeJobPosting(defaultPosting.id)
    }
  } catch (error) {
    message.error(getApiErrorMessage(error, '공고 목록을 불러오지 못했습니다.'))
  } finally {
    initializing.value = false
  }
})
</script>

<style scoped>
.interview-supplement {
  padding: 24px;
  max-width: 1080px;
}
.page-header {
  margin-bottom: 16px;
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
.filter-row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 16px 24px;
}
.filter-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.filter-field label {
  font-size: 12px;
  color: var(--app-text-secondary);
}
.posting-select {
  width: 340px;
}
.filter-summary {
  margin-left: auto;
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: var(--app-text-secondary);
}
.filter-summary b {
  color: var(--app-text-primary);
  font-weight: 500;
}
.muted {
  color: var(--app-text-secondary);
}
.off-card {
  text-align: center;
  padding: 32px 0;
}
.off-card h3 {
  margin: 12px 0 6px;
}
.off-card p {
  color: var(--app-text-secondary);
  margin-bottom: 18px;
}
.off-icon {
  font-size: 32px;
  color: var(--app-text-muted);
}
.tabs-card :deep(.ant-card-body) {
  padding-top: 8px;
}
.tab-count {
  margin-left: 4px;
  padding: 0 8px;
  border-radius: 10px;
  font-size: 12px;
  background: var(--app-bg-muted);
  color: var(--app-text-secondary);
}
@media (max-width: 760px) {
  .posting-select {
    width: 100%;
  }
  .filter-field {
    width: 100%;
  }
  .filter-summary {
    margin-left: 0;
  }
}
</style>
