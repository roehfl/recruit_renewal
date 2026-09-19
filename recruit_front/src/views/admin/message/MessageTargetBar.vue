<script setup lang="ts">
import { computed } from 'vue'
import { BulbOutlined, TeamOutlined } from '@ant-design/icons-vue'

import type { MessageType } from '@/types/admin/message'
import type { StageListItem } from '@/types/admin/stage'
import type { AdminJobPostingListItem } from '@/types/jobPosting'
import {
  APPLICATION_FILTER_OPTIONS,
  RESULT_FILTER_OPTIONS,
  STAGE_STATUS_LABEL,
  interviewGroupLabel,
  isAnnouncedStage,
  isInterviewStage,
  isInterviewType,
  selectablePostings,
  type ApplicationFilter,
  type MessageCondition,
  type ResultFilter,
} from './messageCondition'

const props = defineProps<{
  type: MessageType
  postings: AdminJobPostingListItem[]
  stages: StageListItem[]
  interviewGroups: string[]
  selectedCount: number
  totalCount: number
  loading: boolean
}>()

const condition = defineModel<MessageCondition>('condition', { required: true })

const emit = defineEmits<{
  changePosting: [jobPostingId: number]
  openDrawer: []
}>()

const HINTS: Record<MessageType, string> = {
  RESULT_ANNOUNCEMENT:
    '발표 완료된 전형만 고를 수 있습니다. 결과로 대상을 좁힌 뒤 중립·합격·불합격 템플릿 중 맞는 것을 쓰세요.',
  DEADLINE_REMINDER: '접수 중인 공고에서 지원서를 작성만 하고 제출하지 않은 지원자에게 보냅니다.',
  INTERVIEW_SCHEDULE:
    '확정된 면접에 배정된 지원자만 대상입니다. 면접 일시·도착 시각·장소는 면접 스케줄링 데이터로 사람마다 자동으로 채워집니다.',
  INTERVIEW_NOTICE: '확정된 면접에 배정된 지원자에게 준비물·유의사항·변경 사항을 공지합니다.',
  FREE: '공고 지원자 중 조건에 맞는 사람에게 직접 작성한 내용을 보냅니다.',
}

const isInterview = computed(() => isInterviewType(props.type))

const postingOptions = computed(() =>
  selectablePostings(props.type, props.postings).map((posting) => ({ value: posting.id, label: posting.title })),
)

const stageOptions = computed(() => {
  const stages = isInterview.value ? props.stages.filter(isInterviewStage) : props.stages
  return stages.map((stage) => ({
    value: stage.id,
    label: `${stage.stageName} · ${STAGE_STATUS_LABEL[stage.status]}`,
    disabled: (props.type === 'RESULT_ANNOUNCEMENT' || props.type === 'FREE') && !isAnnouncedStage(stage),
  }))
})

const stageLabel = computed(() => {
  if (props.type === 'RESULT_ANNOUNCEMENT') return '전형 (발표 완료만 선택 가능)'
  if (isInterview.value) return '면접 전형'
  return '전형 결과 조건 (선택, 발표 완료만)'
})

const showStage = computed(() => props.type !== 'DEADLINE_REMINDER')
const showResult = computed(
  () => props.type === 'RESULT_ANNOUNCEMENT' || (props.type === 'FREE' && condition.value.stageId !== null),
)

const groupOptions = computed(() => [
  { value: 'ALL', label: '전체 조' },
  ...props.interviewGroups.map((group) => ({ value: group, label: interviewGroupLabel(group) })),
])

const changeStage = (value: unknown): void => {
  condition.value = { ...condition.value, stageId: typeof value === 'number' ? value : null, interviewGroup: 'ALL' }
}

const changeResult = (value: unknown): void => {
  condition.value = { ...condition.value, resultStatus: value as ResultFilter }
}

const changeGroup = (value: unknown): void => {
  condition.value = { ...condition.value, interviewGroup: String(value) }
}

const changeApplicationStatus = (value: unknown): void => {
  condition.value = { ...condition.value, applicationStatus: value as ApplicationFilter }
}

const changePosting = (value: unknown): void => {
  if (typeof value === 'number') {
    emit('changePosting', value)
  }
}
</script>

<template>
  <section class="target-bar">
    <div class="bar-body">
      <div class="fields">
        <div class="field wide">
          <span class="field-label">{{ type === 'DEADLINE_REMINDER' ? '공고 (접수 중인 공고만)' : '공고' }}</span>
          <a-select
            popup-class-name="message-select-dropdown"
            :value="condition.jobPostingId ?? undefined"
            :options="postingOptions"
            placeholder="공고를 선택하세요"
            show-search
            option-filter-prop="label"
            not-found-content="선택할 수 있는 공고가 없습니다"
            @change="changePosting"
          />
        </div>
        <div v-if="showStage" class="field">
          <span class="field-label">{{ stageLabel }}</span>
          <a-select
            popup-class-name="message-select-dropdown"
            :value="condition.stageId ?? undefined"
            :options="stageOptions"
            :placeholder="type === 'FREE' ? '조건 없음' : '전형을 선택하세요'"
            :allow-clear="type === 'FREE'"
            not-found-content="선택할 수 있는 전형이 없습니다"
            @change="changeStage"
          />
        </div>
        <div v-if="showResult" class="field">
          <span class="field-label">결과</span>
          <a-select popup-class-name="message-select-dropdown" :value="condition.resultStatus" :options="RESULT_FILTER_OPTIONS" @change="changeResult" />
        </div>
        <div v-if="isInterview" class="field">
          <span class="field-label">조</span>
          <a-select popup-class-name="message-select-dropdown" :value="condition.interviewGroup" :options="groupOptions" @change="changeGroup" />
        </div>
        <div v-if="type === 'FREE'" class="field">
          <span class="field-label">지원 상태</span>
          <a-select
            popup-class-name="message-select-dropdown"
            :value="condition.applicationStatus"
            :options="APPLICATION_FILTER_OPTIONS"
            @change="changeApplicationStatus"
          />
        </div>
      </div>
      <div class="recipient-summary">
        <div class="count">
          <strong>{{ selectedCount }}</strong><span class="count-total"> / {{ totalCount }}명</span>
          <span class="count-label">수신 대상</span>
        </div>
        <a-button :loading="loading" @click="emit('openDrawer')"><TeamOutlined /> 대상자 보기·편집</a-button>
      </div>
    </div>
    <p class="hint"><BulbOutlined /> {{ HINTS[type] }}</p>
  </section>
</template>

<style scoped lang="scss">
.target-bar {
  margin-bottom: 14px;
}

.bar-body {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius-lg);
  background: var(--app-bg-surface);
  box-shadow: var(--app-card-shadow);
}

.fields {
  display: flex;
  flex: 1;
  gap: 12px;
  min-width: 0;
}

.field {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 5px;
  min-width: 0;

  &.wide {
    flex: 1.7;
  }
}

.field-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--app-text-secondary);
}

.recipient-summary {
  display: flex;
  flex: none;
  align-items: center;
  gap: 12px;
  height: 52px;
  padding-left: 14px;
  border-left: 1px solid var(--app-border-default);
}

.count {
  line-height: 1.2;

  strong {
    font-size: 22px;
    color: var(--app-color-primary);
  }
}

.count-total {
  font-size: 13px;
  color: var(--app-text-secondary);
}

.count-label {
  display: block;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 8px 2px 0;
  font-size: 12px;
  color: var(--app-text-secondary);
}
</style>
