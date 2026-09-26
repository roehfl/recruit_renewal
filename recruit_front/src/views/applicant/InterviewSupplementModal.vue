<template>
  <a-modal
    :open="props.open"
    :width="900"
    :mask-closable="false"
    :keyboard="false"
    :footer="null"
    wrap-class-name="supplement-modal-wrap"
    @cancel="close"
  >
    <template #title>
      <div class="title">추가사항 입력</div>
      <div v-if="form" class="context">{{ form.jobPostingTitle }} · {{ form.stageName }}</div>
    </template>

    <a-spin :spinning="loading">
      <div class="layout">
        <template v-if="form">
          <div class="timer" :class="timerLevel">
            <span class="timer-icon"><HourglassOutlined /></span>
            <div class="timer-left">
              <span class="label">남은 입력 시간</span>
              <span class="left">{{ remainingText }}</span>
            </div>
            <div class="timer-end">입력 종료<b>{{ formatDate(form.endDateTime, 'YYYY.MM.DD HH:mm') }}</b></div>
            <div class="timer-bar"><i :style="{ width: progressWidth }" /></div>
          </div>
          <p class="guide">
            <InfoCircleOutlined />
            입력 시간이 끝나면 이 창은 자동으로 닫힙니다. 작성 내용은 자동 저장되며, 종료 시점에 저장된 답변이 최종 답변으로 제출됩니다.
          </p>

          <div class="questions">
            <div v-for="(question, index) in form.questions" :key="question.questionId" class="qa">
              <div class="q"><span class="no">질문 {{ index + 1 }}</span><span>{{ question.content }}</span></div>
              <a-textarea
                v-model:value="answers[question.questionId]"
                :maxlength="MAX_ANSWER_LENGTH"
                :auto-size="{ minRows: 4, maxRows: 10 }"
                show-count
                placeholder="답변을 입력하세요."
                @change="markDirty(question.questionId)"
              />
            </div>
          </div>

          <div class="footer">
            <span class="progress">작성 {{ answeredCount }} / {{ form.questions.length }}</span>
            <span class="save-state" :class="saveState">
              <LoadingOutlined v-if="saveState === 'saving'" />
              <CheckCircleOutlined v-else-if="saveState === 'saved' || saveState === 'idle'" />
              <ExclamationCircleOutlined v-else-if="saveState === 'error'" />
              {{ saveStateText }}
            </span>
            <a-button size="large" @click="close">닫기</a-button>
            <a-button size="large" type="primary" :loading="saving" @click="saveNow"><SaveOutlined />저장</a-button>
          </div>
        </template>
      </div>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import axios from 'axios'
import { message, Modal } from 'ant-design-vue'
import {
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  HourglassOutlined,
  InfoCircleOutlined,
  LoadingOutlined,
  SaveOutlined,
} from '@ant-design/icons-vue'
import { interviewSupplementApi } from '@/api/interviewSupplementApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import type { ApplicantInterviewSupplementForm } from '@/types/interviewSupplement'

/* 백엔드 InterviewSupplementAnswerItemRequest @Size(max = 1000) */
const MAX_ANSWER_LENGTH = 1000
const AUTO_SAVE_DELAY_MS = 1500
/* 마감 직전에는 기다리지 않고 바로 저장한다. 마감 뒤 도착한 저장은 서버가 거부한다(유예 없음). */
const FLUSH_BEFORE_END_MS = 3000
const CLOSED_MESSAGE = '추가사항 입력 시간이 아닙니다.'

const props = defineProps<{ open: boolean; applicationId: number | null; stageId: number | null }>()
const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'closed'): void
}>()

const form = ref<ApplicantInterviewSupplementForm | null>(null)
const answers = reactive<Record<number, string>>({})
const loading = ref(false)
const saving = ref(false)
const saveState = ref<'idle' | 'editing' | 'saving' | 'saved' | 'error'>('idle')
const savedAt = ref<string | null>(null)
const dirty = new Set<number>()

/*
 * 남은 시간은 기기 시계를 쓰지 않는다. 서버가 준 remainingSeconds 를 받은 순간부터
 * performance.now()(기기 시계 변경의 영향을 받지 않음)로 센다. 저장 응답마다 다시 맞춘다.
 */
let deadline = 0
let totalMs = 1
const remainingMs = ref(0)
let ticker: ReturnType<typeof setInterval> | undefined
let saveTimer: ReturnType<typeof setTimeout> | undefined
let saveAgain = false

const syncDeadline = (remainingSeconds: number): void => {
  deadline = performance.now() + remainingSeconds * 1000
  remainingMs.value = Math.max(0, deadline - performance.now())
}

const remainingText = computed(() => {
  const total = Math.ceil(remainingMs.value / 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(Math.floor(total / 3600))}:${pad(Math.floor((total % 3600) / 60))}:${pad(total % 60)}`
})
const timerLevel = computed(() => (remainingMs.value <= 60_000 ? 'crit' : remainingMs.value <= 300_000 ? 'warn' : ''))
const progressWidth = computed(() => `${Math.min(100, (remainingMs.value / totalMs) * 100).toFixed(2)}%`)
const answeredCount = computed(() => form.value?.questions.filter((q) => (answers[q.questionId] ?? '').trim()).length ?? 0)
const saveStateText = computed(() => {
  switch (saveState.value) {
    case 'editing': return '작성 중…'
    case 'saving': return '저장 중…'
    case 'saved': return `저장됨 ${savedAt.value ? formatDate(savedAt.value, 'HH:mm:ss') : ''}`
    case 'error': return '저장하지 못했습니다. 다시 시도해 주세요.'
    default: return answeredCount.value > 0 ? '저장된 답변을 불러왔습니다' : '아직 저장된 답변이 없습니다'
  }
})

const stopTimers = (): void => {
  if (ticker) clearInterval(ticker)
  if (saveTimer) clearTimeout(saveTimer)
  ticker = undefined
  saveTimer = undefined
}

const reset = (): void => {
  stopTimers()
  form.value = null
  Object.keys(answers).forEach((key) => delete answers[Number(key)])
  dirty.clear()
  saveAgain = false
  saveState.value = 'idle'
  savedAt.value = null
}

const load = async (applicationId: number, stageId: number): Promise<void> => {
  loading.value = true
  try {
    const response = await interviewSupplementApi.getForm(applicationId, stageId)
    const data = response.data.data
    data.questions.forEach((question) => {
      answers[question.questionId] = question.answerText ?? ''
    })
    form.value = data
    syncDeadline(data.remainingSeconds)
    totalMs = Math.max(1, remainingMs.value)
    ticker = setInterval(tick, 500)
  } catch (error) {
    message.error(getApiErrorMessage(error, '추가사항을 열 수 없습니다.'))
    finish()
  } finally {
    loading.value = false
  }
}

watch(
  () => props.open,
  (open) => {
    if (open && props.applicationId !== null && props.stageId !== null) {
      reset()
      load(props.applicationId, props.stageId)
    }
  },
)

const tick = (): void => {
  remainingMs.value = Math.max(0, deadline - performance.now())
  if (remainingMs.value <= FLUSH_BEFORE_END_MS && dirty.size > 0 && !saving.value) {
    flush()
  }
  if (remainingMs.value <= 0) {
    expire()
  }
}

const markDirty = (questionId: number): void => {
  dirty.add(questionId)
  saveState.value = 'editing'
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => flush(), AUTO_SAVE_DELAY_MS)
}

const isClosedError = (error: unknown): boolean =>
  axios.isAxiosError(error) && error.response?.status === 400 && error.response.data?.message === CLOSED_MESSAGE

/** 바뀐 답만 보낸다. 저장 중에 또 바뀌면 끝난 뒤 한 번 더 보낸다. */
const flush = async (): Promise<boolean> => {
  if (!form.value || props.applicationId === null || props.stageId === null) return false
  if (saving.value) {
    saveAgain = true
    return false
  }
  if (dirty.size === 0) return true
  if (saveTimer) clearTimeout(saveTimer)
  const ids = [...dirty]
  dirty.clear()
  saving.value = true
  saveState.value = 'saving'
  try {
    const response = await interviewSupplementApi.saveAnswers(props.applicationId, props.stageId, {
      answers: ids.map((questionId) => ({ questionId, answerText: answers[questionId] ?? '' })),
    })
    syncDeadline(response.data.data.remainingSeconds)
    savedAt.value = response.data.data.savedAt
    saveState.value = dirty.size > 0 ? 'editing' : 'saved'
    return true
  } catch (error) {
    ids.forEach((id) => dirty.add(id))
    if (isClosedError(error)) {
      expire()
    } else {
      saveState.value = 'error'
      message.error(getApiErrorMessage(error, '답변을 저장하지 못했습니다.'))
    }
    return false
  } finally {
    saving.value = false
    if (saveAgain) {
      saveAgain = false
      if (dirty.size > 0 && form.value) flush()
    }
  }
}

const saveNow = async (): Promise<void> => {
  if (dirty.size === 0) {
    message.success('저장된 상태입니다.')
    return
  }
  if (await flush()) message.success('답변을 저장했습니다.')
}

const finish = (): void => {
  stopTimers()
  emit('update:open', false)
  emit('closed')
}

/* 입력 시간이 끝나면 창을 닫는다. 이미 닫혔으면 무시한다. */
const expire = (): void => {
  if (!form.value) return
  form.value = null
  finish()
  Modal.info({
    title: '입력 시간이 종료되었습니다',
    content: '창을 닫았습니다. 마지막으로 저장된 답변이 제출되었습니다.',
    okText: '확인',
  })
}

const close = async (): Promise<void> => {
  if (dirty.size > 0) await flush()
  if (form.value) {
    form.value = null
    finish()
  }
}

onBeforeUnmount(stopTimers)
</script>

<style scoped>
.title {
  font-size: 17px;
  font-weight: 700;
}
.context {
  font-size: 13px;
  font-weight: 400;
  color: var(--app-text-secondary);
  margin-top: 2px;
}
.layout {
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 220px);
  min-height: 240px;
}
.timer {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 4px 14px;
  align-items: center;
  padding: 12px 16px;
  border: 1px solid #cfe0c6;
  border-radius: var(--app-border-radius);
  background: var(--app-bg-selected);
}
.timer-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #fff;
  color: var(--app-color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}
.timer-left {
  display: flex;
  flex-direction: column;
}
.timer-left .label {
  font-size: 12.5px;
  color: var(--app-text-secondary);
}
.timer-left .left {
  font-size: 24px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  color: var(--app-color-primary);
  line-height: 1.2;
}
.timer-end {
  text-align: right;
  font-size: 12.5px;
  color: var(--app-text-secondary);
}
.timer-end b {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--app-text-primary);
  font-variant-numeric: tabular-nums;
}
.timer-bar {
  grid-column: 1 / 4;
  height: 4px;
  border-radius: 2px;
  background: rgb(15 71 38 / 10%);
  overflow: hidden;
  margin-top: 8px;
}
.timer-bar i {
  display: block;
  height: 100%;
  background: var(--app-color-primary);
  transition: width 0.5s linear;
}
.timer.warn {
  border-color: #ffd591;
  background: #fff7e6;
}
.timer.warn .timer-icon,
.timer.warn .left {
  color: #b45309;
}
.timer.warn .timer-bar i {
  background: var(--app-color-warning);
}
.timer.crit {
  border-color: #ffccc7;
  background: #fff1f0;
}
.timer.crit .timer-icon,
.timer.crit .left {
  color: var(--app-color-error);
}
.timer.crit .timer-bar i {
  background: var(--app-color-error);
}
.guide {
  margin: 12px 0 0;
  font-size: 12.5px;
  color: var(--app-text-secondary);
}
.questions {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  margin-top: 12px;
  padding-right: 4px;
}
.qa {
  padding: 16px 0 20px;
  border-top: 1px solid var(--app-border-default);
}
.qa:first-child {
  border-top: 0;
  padding-top: 4px;
}
.q {
  display: flex;
  gap: 10px;
  font-weight: 500;
  line-height: 1.6;
  margin-bottom: 10px;
}
.q .no {
  flex: none;
  font-size: 12px;
  font-weight: 700;
  color: var(--app-color-primary);
  background: var(--app-bg-selected);
  border-radius: 4px;
  padding: 1px 7px;
  height: 22px;
}
.footer {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding-top: 14px;
  border-top: 1px solid var(--app-border-default);
}
.progress {
  font-size: 12.5px;
  font-weight: 500;
  color: var(--app-color-primary);
  background: var(--app-bg-selected);
  border-radius: 10px;
  padding: 1px 9px;
}
.save-state {
  margin-right: auto;
  font-size: 12.5px;
  color: var(--app-text-secondary);
}
.save-state.saved,
.save-state.idle {
  color: var(--app-color-success);
}
.save-state.error {
  color: var(--app-color-error);
}

/* 휴대폰: 전체 화면, 타이머·버튼은 고정하고 질문만 스크롤 (모달 틀은 아래 전역 스타일) */
@media (max-width: 760px) {
  .layout {
    height: 100%;
    max-height: none;
  }
  .timer-end {
    grid-column: 2;
    text-align: left;
  }
  .save-state {
    flex: 1 1 auto;
    margin-right: 0;
  }
  .footer .ant-btn {
    flex: 1 1 40%;
  }
}
</style>

<style>
/*
 * 모달은 body 로 옮겨져 그려지므로 틀(.ant-modal*) 재정의는 scoped 로 닿지 않는다.
 * wrap-class-name 으로 이 모달에만 적용한다.
 */
@media (max-width: 760px) {
  .supplement-modal-wrap .ant-modal {
    top: 0;
    max-width: 100vw;
    width: 100vw !important;
    margin: 0;
    padding: 0;
  }
  .supplement-modal-wrap .ant-modal-content {
    height: 100dvh;
    border-radius: 0;
    display: flex;
    flex-direction: column;
  }
  .supplement-modal-wrap .ant-modal-body {
    flex: 1;
    min-height: 0;
  }
  .supplement-modal-wrap .ant-spin-nested-loading,
  .supplement-modal-wrap .ant-spin-container {
    height: 100%;
  }
}
</style>
