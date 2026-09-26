<template>
  <a-drawer :open="props.open" :width="640" :title="detail ? detail.applicantName : '답변 보기'" @close="close">
    <template #extra>
      <a-button type="text" size="small" title="이전 지원자" :disabled="prevId === null" @click="prevId !== null && emit('navigate', prevId)"><UpOutlined /></a-button>
      <a-button type="text" size="small" title="다음 지원자" :disabled="nextId === null" @click="nextId !== null && emit('navigate', nextId)"><DownOutlined /></a-button>
    </template>
    <a-spin :spinning="loading">
      <template v-if="detail">
        <p class="meta">수험번호 {{ detail.jobApplicationId }} · {{ detail.groupName }}조 {{ detail.candidateOrder ?? '' }}번</p>
        <div class="summary">
          <div><span>입력 가능 시간</span><b>{{ formatWindow(detail.startDateTime, detail.endDateTime) }}</b></div>
          <div><span>작성</span><b>{{ detail.answeredCount }} / {{ detail.items.length }}</b></div>
          <div><span>최종 저장</span><b>{{ detail.lastSavedAt ? formatDate(detail.lastSavedAt, 'MM.DD HH:mm') : '-' }}</b></div>
        </div>
        <a-alert
          v-if="isWithinWindow(detail.startDateTime, detail.endDateTime)"
          type="warning"
          show-icon
          class="live-alert"
          message="지원자가 아직 입력 시간 중입니다. 입력 시간이 끝날 때까지 답변이 바뀔 수 있습니다."
        />
        <div v-for="(item, index) in detail.items" :key="item.questionId" class="answer">
          <div class="question"><span class="no">질문 {{ index + 1 }}</span><span>{{ item.content }}</span></div>
          <div class="text" :class="{ none: !item.answerText }">{{ item.answerText || '작성하지 않았습니다.' }}</div>
          <div v-if="item.answerText" class="length">{{ item.answerText.length.toLocaleString() }}자</div>
        </div>
      </template>
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { DownOutlined, UpOutlined } from '@ant-design/icons-vue'
import { adminInterviewSupplementApi } from '@/api/admin/adminInterviewSupplementApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import type { AdminInterviewSupplementAnswerDetail } from '@/types/admin/interviewSupplement'
import { formatWindow, isWithinWindow } from './interviewSupplementTime'

const props = defineProps<{
  open: boolean
  stageId: number
  jobApplicationId: number | null
  navigableIds: number[]
}>()
const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'navigate', jobApplicationId: number): void
}>()

const detail = ref<AdminInterviewSupplementAnswerDetail | null>(null)
const loading = ref(false)

const position = computed(() => (props.jobApplicationId === null ? -1 : props.navigableIds.indexOf(props.jobApplicationId)))
const prevId = computed(() => (position.value > 0 ? props.navigableIds[position.value - 1] ?? null : null))
const nextId = computed(() =>
  position.value >= 0 && position.value < props.navigableIds.length - 1 ? props.navigableIds[position.value + 1] ?? null : null,
)

/* 다른 지원자로 넘어간 뒤 늦게 도착한 응답은 요청 번호로 버린다. */
let request = 0

const load = async (jobApplicationId: number): Promise<void> => {
  const current = ++request
  loading.value = true
  try {
    const response = await adminInterviewSupplementApi.getAnswers(props.stageId, jobApplicationId)
    if (current !== request) return
    detail.value = response.data.data
  } catch (error) {
    if (current !== request) return
    message.error(getApiErrorMessage(error, '답변을 불러오지 못했습니다.'))
    emit('update:open', false)
  } finally {
    if (current === request) loading.value = false
  }
}

watch(
  () => [props.open, props.jobApplicationId] as const,
  ([open, id]) => {
    if (open && id !== null) {
      detail.value = null
      load(id)
    }
  },
)

const close = (): void => {
  request++
  emit('update:open', false)
}
</script>

<style scoped>
.meta {
  margin: 0 0 12px;
  color: var(--app-text-secondary);
  font-size: 13px;
}
.summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: var(--app-border-default);
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius);
  overflow: hidden;
  margin-bottom: 14px;
}
.summary div {
  background: var(--app-bg-muted);
  padding: 10px 14px;
}
.summary span {
  display: block;
  font-size: 11.5px;
  color: var(--app-text-muted);
}
.summary b {
  font-size: 13px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}
.live-alert {
  margin-bottom: 12px;
}
.answer {
  padding: 14px 0;
  border-top: 1px solid var(--app-border-default);
}
.answer:first-of-type {
  border-top: 0;
}
.question {
  display: flex;
  gap: 8px;
  font-weight: 500;
  line-height: 1.6;
  margin-bottom: 8px;
}
.question .no {
  flex: none;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--app-color-primary);
  background: var(--app-bg-selected);
  border-radius: 4px;
  padding: 1px 7px;
  height: 21px;
}
.text {
  white-space: pre-wrap;
  line-height: 1.7;
  background: var(--app-bg-muted);
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius);
  padding: 10px 12px;
}
.text.none {
  color: var(--app-text-muted);
  background: #fff;
  border-style: dashed;
}
.length {
  text-align: right;
  font-size: 11.5px;
  color: var(--app-text-muted);
  margin-top: 4px;
}
</style>
