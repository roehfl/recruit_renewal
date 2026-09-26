<template>
  <div class="answer-tab">
    <div class="tools">
      <a-select v-model:value="groupFilter" class="group-select" :options="groupOptions" />
      <a-input v-model:value="keyword" class="keyword" placeholder="성명·수험번호 검색" allow-clear />
      <a-select v-model:value="answerFilter" class="answer-select" :options="answerOptions" />
      <span class="summary">모두 작성 <b>{{ fullCount }}</b> · 미작성 <b>{{ zeroCount }}</b></span>
    </div>

    <div v-if="candidates.length === 0" class="empty">이 면접단계에 배정된 지원자가 없습니다.</div>
    <div v-else-if="filtered.length === 0" class="empty">조건에 맞는 지원자가 없습니다.</div>
    <div v-else class="list">
      <div class="list-head"><span>성명</span><span>수험번호</span><span>입력 가능 시간</span><span>작성</span><span>최종 저장</span><span></span></div>
      <template v-for="group in groups" :key="group.interviewId">
        <div class="group-row">
          <span class="group-name">{{ group.groupName }}조</span>
          <span>{{ formatDate(group.interviewStartDateTime, 'MM.DD') }} 도착 <b>{{ group.arrivalDateTime ? formatDate(group.arrivalDateTime, 'HH:mm') : '—' }}</b></span>
          <span>면접 <b>{{ formatDate(group.interviewStartDateTime, 'HH:mm') }}</b></span>
          <span>{{ group.rows.length }}명</span>
        </div>
        <div v-for="candidate in group.rows" :key="candidate.jobApplicationId" class="row">
          <span class="name">{{ candidate.applicantName }}</span>
          <span class="num">{{ candidate.jobApplicationId }}</span>
          <span class="window">
            <span class="num">{{ formatWindow(candidate.startDateTime, candidate.endDateTime) }}</span>
            <a-tag v-if="candidate.customized" color="blue">직접 지정</a-tag>
          </span>
          <span class="progress" :class="{ zero: candidate.answeredCount === 0 }">
            <span class="bar"><i :class="{ full: candidate.answeredCount >= questionCount }" :style="{ width: progressWidth(candidate) }" /></span>
            <span class="num">{{ candidate.answeredCount === 0 ? '미작성' : `${candidate.answeredCount} / ${questionCount}` }}</span>
          </span>
          <span class="num saved" :class="{ muted: !candidate.lastSavedAt }">{{ candidate.lastSavedAt ? formatDate(candidate.lastSavedAt, 'MM.DD HH:mm') : '-' }}</span>
          <a-button size="small" :disabled="candidate.answeredCount === 0" @click="openAnswers(candidate.jobApplicationId)"><FileTextOutlined />답변 보기</a-button>
        </div>
      </template>
    </div>

    <InterviewSupplementAnswerDrawer
      v-model:open="drawerOpen"
      :stage-id="stageId"
      :job-application-id="drawerApplicationId"
      :navigable-ids="navigableIds"
      @navigate="openAnswers"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { FileTextOutlined } from '@ant-design/icons-vue'
import { formatDate } from '@/common/dateUtil'
import type { AdminInterviewSupplementCandidate } from '@/types/admin/interviewSupplement'
import InterviewSupplementAnswerDrawer from './InterviewSupplementAnswerDrawer.vue'
import { formatWindow } from './interviewSupplementTime'

const props = defineProps<{ stageId: number; candidates: AdminInterviewSupplementCandidate[]; questionCount: number }>()

type AnswerFilter = '' | 'zero' | 'part' | 'full'

const groupFilter = ref<number | ''>('')
const keyword = ref('')
const answerFilter = ref<AnswerFilter>('')
const answerOptions: { value: AnswerFilter; label: string }[] = [
  { value: '', label: '작성 전체' },
  { value: 'zero', label: '미작성' },
  { value: 'part', label: '작성 중' },
  { value: 'full', label: '모두 작성' },
]

const isFull = (c: AdminInterviewSupplementCandidate) => props.questionCount > 0 && c.answeredCount >= props.questionCount
const fullCount = computed(() => props.candidates.filter(isFull).length)
const zeroCount = computed(() => props.candidates.filter((c) => c.answeredCount === 0).length)

const groupOptions = computed(() => {
  const seen = new Map<number, string>()
  props.candidates.forEach((c) => seen.set(c.interviewId, c.groupName))
  return [{ value: '' as const, label: '전체 조' }, ...[...seen].map(([id, name]) => ({ value: id, label: `${name}조` }))]
})

const filtered = computed(() => {
  const k = keyword.value.trim()
  return props.candidates.filter((c) => {
    const answer = answerFilter.value === ''
      || (answerFilter.value === 'zero' && c.answeredCount === 0)
      || (answerFilter.value === 'full' && isFull(c))
      || (answerFilter.value === 'part' && c.answeredCount > 0 && !isFull(c))
    return answer
      && (groupFilter.value === '' || c.interviewId === groupFilter.value)
      && (!k || c.applicantName.includes(k) || String(c.jobApplicationId).includes(k))
  })
})

const groups = computed(() => {
  const map = new Map<number, AdminInterviewSupplementCandidate[]>()
  filtered.value.forEach((c) => {
    const rows = map.get(c.interviewId) ?? []
    rows.push(c)
    map.set(c.interviewId, rows)
  })
  return [...map.values()].map((rows) => ({
    interviewId: rows[0]!.interviewId,
    groupName: rows[0]!.groupName,
    interviewStartDateTime: rows[0]!.interviewStartDateTime,
    arrivalDateTime: rows[0]!.arrivalDateTime,
    rows,
  }))
})

const progressWidth = (c: AdminInterviewSupplementCandidate): string =>
  props.questionCount > 0 ? `${Math.min(100, (c.answeredCount / props.questionCount) * 100)}%` : '0%'

/* 답변 보기: 창을 닫지 않고 현재 필터의 작성한 지원자 사이를 오간다. */
const drawerOpen = ref(false)
const drawerApplicationId = ref<number | null>(null)
const navigableIds = computed(() => filtered.value.filter((c) => c.answeredCount > 0).map((c) => c.jobApplicationId))

const openAnswers = (jobApplicationId: number): void => {
  drawerApplicationId.value = jobApplicationId
  drawerOpen.value = true
}
</script>

<style scoped>
.tools {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.group-select {
  width: 120px;
}
.keyword {
  width: 220px;
}
.answer-select {
  width: 120px;
}
.summary {
  margin-left: auto;
  font-size: 12.5px;
  color: var(--app-text-secondary);
}
.summary b {
  color: var(--app-text-primary);
  font-weight: 500;
}
.empty {
  padding: 40px 0;
  text-align: center;
  color: var(--app-text-muted);
}
.list {
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius);
}
.list-head,
.row {
  display: grid;
  grid-template-columns: minmax(60px, 100px) 72px minmax(140px, 1fr) 118px 84px max-content;
  gap: 10px;
  align-items: center;
  padding: 0 12px;
}
.list-head {
  height: 34px;
  background: var(--app-bg-muted);
  border-bottom: 1px solid var(--app-border-default);
  font-size: 12px;
  color: var(--app-text-secondary);
  position: sticky;
  top: 0;
  z-index: 2;
}
.group-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #fbfcfa;
  border-bottom: 1px solid var(--app-border-default);
  font-size: 12px;
  color: var(--app-text-secondary);
  position: sticky;
  top: 34px;
  z-index: 1;
}
.group-row b {
  color: var(--app-text-primary);
  font-weight: 500;
}
.group-name {
  font-weight: 700;
  font-size: 13.5px;
  color: var(--app-text-primary);
}
.row {
  padding-top: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--app-border-default);
  font-size: 14px;
}
.row:last-child {
  border-bottom: 0;
}
.name {
  font-weight: 500;
}
.num {
  font-variant-numeric: tabular-nums;
}
.window {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.progress {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
}
.progress .bar {
  flex: 1;
  max-width: 64px;
  height: 6px;
  border-radius: 3px;
  background: var(--app-border-default);
  overflow: hidden;
}
.progress .bar i {
  display: block;
  height: 100%;
  background: var(--app-color-warning);
}
.progress .bar i.full {
  background: var(--app-color-success);
}
.progress.zero {
  color: var(--app-text-muted);
}
.saved {
  font-size: 13px;
}
.muted {
  color: var(--app-text-muted);
}
</style>
