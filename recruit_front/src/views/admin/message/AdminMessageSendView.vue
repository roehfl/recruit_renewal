<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Button, message, notification } from 'ant-design-vue'
import axios from 'axios'
import { FileTextOutlined, HistoryOutlined } from '@ant-design/icons-vue'

import { adminStageApi } from '@/api/admin/adminStageApi'
import { getAllJobPostings } from '@/api/adminJobPostingApi'
import { messageApi } from '@/api/admin/messageApi'
import { getApiErrorMessage } from '@/api/apiError'
import type {
  MessageContent,
  MessageContentRequest,
  MessageTargetQuery,
  MessageTargetResponse,
  MessageTemplate,
  MessageTester,
  MessageTestSendResultItem,
  MessageType,
  MessageVariable,
} from '@/types/admin/message'
import type { StageListItem } from '@/types/admin/stage'
import type { AdminJobPostingListItem } from '@/types/jobPosting'
import MessageComposer from './MessageComposer.vue'
import MessagePreview from './MessagePreview.vue'
import MessageRecipientDrawer from './MessageRecipientDrawer.vue'
import MessageSendBar from './MessageSendBar.vue'
import MessageSendConfirmModal from './MessageSendConfirmModal.vue'
import MessageTargetBar from './MessageTargetBar.vue'
import MessageTestSendCard from './MessageTestSendCard.vue'
import MessageTypePicker from './MessageTypePicker.vue'
import { defaultStageId, selectablePostings, toTargetQuery, type MessageCondition } from './messageCondition'
import { hasRequested, toTestResults } from './messageHistory'
import { renderMessage, smsByteLength, smsKindOf, type SmsStats } from './messageRender'
import { buildSendSummary, describeCondition } from './messageSendSummary'
import { MESSAGE_TYPES } from './messageTypes'

type Channel = 'mail' | 'sms'

const router = useRouter()

const type = ref<MessageType>('RESULT_ANNOUNCEMENT')
const postings = ref<AdminJobPostingListItem[]>([])
const stages = ref<StageListItem[]>([])
const templates = ref<MessageTemplate[]>([])
const variables = ref<MessageVariable[]>([])

const initialCondition = (messageType: MessageType, jobPostingId: number | null): MessageCondition => ({
  jobPostingId,
  stageId: null,
  resultStatus: messageType === 'RESULT_ANNOUNCEMENT' ? 'PASSED' : 'ALL',
  interviewGroup: 'ALL',
  applicationStatus: 'SUBMITTED',
})

const condition = ref<MessageCondition>(initialCondition('RESULT_ANNOUNCEMENT', null))

const target = ref<MessageTargetResponse | null>(null)
const targetLoading = ref(false)
const targetError = ref('')
const selectedIds = ref<number[]>([])
const drawerOpen = ref(false)
const previewIndex = ref(0)
const channel = ref<Channel>('mail')
/* 지금 목록을 만든 조회 쿼리. 발송·테스트 발송은 이 쿼리로 보낸다(조건을 바꿨는데 목록이 옛것인 상태를 막는다). */
const loadedQuery = ref<MessageTargetQuery | null>(null)
const tested = ref(false)
const testSending = ref(false)
const testResults = ref<MessageTestSendResultItem[]>([])
const confirmOpen = ref(false)
const sending = ref(false)

const emptyContent = (): MessageContent => ({
  templateId: null,
  mailEnabled: true,
  smsEnabled: true,
  mailSubject: '',
  mailBody: '',
  smsBody: '',
})

const content = ref<MessageContent>(emptyContent())
/* 되돌리기·수정됨 판정 기준: 마지막으로 적용하거나 저장한 템플릿 내용 */
const baseContent = ref<MessageContent>(emptyContent())

const typeTemplates = computed(() => templates.value.filter((template) => template.type === type.value))
const typeVariables = computed(() => variables.value.filter((variable) => variable.types.includes(type.value)))
const recipients = computed(() => target.value?.recipients ?? [])
const selectedRecipients = computed(() => {
  const selected = new Set(selectedIds.value)
  return recipients.value.filter((recipient) => selected.has(recipient.applicationId))
})

const dirty = computed(
  () =>
    content.value.mailSubject !== baseContent.value.mailSubject ||
    content.value.mailBody !== baseContent.value.mailBody ||
    content.value.smsBody !== baseContent.value.smsBody,
)

const smsBody = computed(() => content.value.smsBody)
/* 선택 수신자별 SMS byte. SMS를 받을 수 없는 수신자는 판정에서 뺀다. */
const smsBytesByRecipient = computed(() =>
  selectedRecipients.value.map((recipient) =>
    recipient.smsAvailable ? smsByteLength(renderMessage(smsBody.value, recipient.variables)) : 0,
  ),
)
const smsStats = computed<SmsStats>(() => {
  const current = selectedRecipients.value[previewIndex.value]
  const currentBytes = smsByteLength(renderMessage(smsBody.value, current ? current.variables : {}))
  const maxBytes = smsBytesByRecipient.value.reduce((max, bytes) => Math.max(max, bytes), selectedRecipients.value.length > 0 ? 0 : currentBytes)
  return { currentBytes, maxBytes, kind: smsKindOf(maxBytes) }
})

const typeName = computed(() => MESSAGE_TYPES.find((meta) => meta.type === type.value)?.name ?? '')
const postingTitle = computed(
  () => postings.value.find((posting) => posting.id === condition.value.jobPostingId)?.title ?? '',
)
const conditionText = computed(() => describeCondition(type.value, condition.value, stages.value))
const templateName = computed(
  () => templates.value.find((template) => template.id === content.value.templateId)?.name ?? null,
)
const previewRecipient = computed(() => selectedRecipients.value[previewIndex.value] ?? null)

const sendSummary = computed(() =>
  buildSendSummary({
    recipients: selectedRecipients.value,
    smsBytes: smsBytesByRecipient.value,
    content: content.value,
    allowedKeys: typeVariables.value.map((variable) => variable.key),
    loading: targetLoading.value,
    error: targetError.value,
  }),
)

/* 테스트 발송 응답은 솔루션 접수 결과다. 결과 수신 중(REQUESTED)인 채널이 있으면 이력 상세를 3초마다 다시 읽어 최종 결과로 바꾼다(최대 2분, 설계서 3.1-5). */
const TEST_POLL_INTERVAL_MS = 3000
const TEST_POLL_LIMIT_MS = 120000

/* 새 테스트·내용 변경·발송·화면 이탈 때 번호를 올려 진행 중인 폴링과 늦은 응답을 버린다. */
let testPollRun = 0
let testPollTimer: ReturnType<typeof setTimeout> | null = null

const stopTestPolling = (): void => {
  testPollRun += 1
  if (testPollTimer !== null) {
    clearTimeout(testPollTimer)
    testPollTimer = null
  }
}

const refreshTestResults = async (sendId: number, run: number, deadline: number): Promise<void> => {
  try {
    const response = await messageApi.getHistoryDetail(sendId)
    if (run !== testPollRun) return
    testResults.value = toTestResults(response.data.data)
  } catch {
    /* 일시 오류는 다음 주기에 다시 읽는다. */
  }
  if (run === testPollRun && hasRequested(testResults.value) && Date.now() < deadline) {
    pollTestResults(sendId, run, deadline)
  }
}

const pollTestResults = (sendId: number, run: number, deadline: number): void => {
  testPollTimer = setTimeout(() => {
    testPollTimer = null
    void refreshTestResults(sendId, run, deadline)
  }, TEST_POLL_INTERVAL_MS)
}

/* 내용이나 종류가 바뀌면 이전 테스트 발송은 지금 내용과 다르다. 진행 중이던 테스트 응답·결과 폴링도 버린다. */
let testVersion = 0
watch(
  [content, type],
  () => {
    testVersion += 1
    stopTestPolling()
    tested.value = false
    testResults.value = []
  },
  { deep: true },
)

const toContentRequest = (): MessageContentRequest => ({
  templateId: content.value.templateId,
  mailEnabled: content.value.mailEnabled,
  smsEnabled: content.value.smsEnabled,
  mailSubject: content.value.mailSubject,
  mailBody: content.value.mailBody,
  smsBody: content.value.smsBody,
})

const runTest = async (testers: MessageTester[]): Promise<void> => {
  const query = loadedQuery.value
  const preview = previewRecipient.value
  if (!query || !preview) return
  stopTestPolling()
  const version = testVersion
  const run = testPollRun
  testSending.value = true
  try {
    const response = await messageApi.testSend({
      ...query,
      previewApplicationId: preview.applicationId,
      testers,
      content: toContentRequest(),
    })
    if (version !== testVersion) {
      message.warning('테스트 발송 중에 내용이 바뀌었습니다. 바뀐 내용으로 다시 테스트하세요.')
      return
    }
    /* 언마운트·발송·새 테스트로 폴링 번호가 바뀌었으면 늦게 온 이번 응답은 조용히 버린다(토스트·폴링 예약 없음). */
    if (run !== testPollRun) return
    const { sendId, results } = response.data.data
    testResults.value = results
    /* 솔루션이 접수한 채널이 하나라도 있으면 테스트한 것으로 본다. 최종 성공·실패는 폴링으로 갱신한다. */
    tested.value = results.some((result) => result.status === 'REQUESTED' || result.status === 'SENT')
    if (tested.value) {
      message.success('테스트 발송을 접수했습니다. 결과가 오면 아래에 표시합니다.')
    } else {
      message.warning('테스트 발송이 접수된 채널이 없습니다. 결과를 확인하세요.')
    }
    if (hasRequested(results)) {
      pollTestResults(sendId, run, Date.now() + TEST_POLL_LIMIT_MS)
    }
  } catch (error) {
    message.error(getApiErrorMessage(error, '테스트 발송에 실패했습니다.'))
  } finally {
    testSending.value = false
  }
}

/* 발송은 서버에서 비동기로 진행된다. 알림의 "이력 보기"로 그 발송 상세를 연다(설계서 3.1-9). */
const showSendAccepted = (sendId: number, recipientCount: number): void => {
  const key = `message-send-${sendId}`
  notification.success({
    key,
    message: '발송을 요청했습니다',
    description: `발송 번호 ${sendId}, ${recipientCount}명. 결과는 발송 이력에서 확인할 수 있습니다.`,
    duration: 8,
    btn: () =>
      h(
        Button,
        {
          type: 'primary',
          size: 'small',
          onClick: () => {
            notification.close(key)
            void router.push({ name: 'AdminMessageHistory', query: { sendId: String(sendId) } })
          },
        },
        { default: () => '이력 보기' },
      ),
  })
}

const confirmSend = async (): Promise<void> => {
  const query = loadedQuery.value
  if (!query) return
  sending.value = true
  try {
    const response = await messageApi.send({
      ...query,
      applicationIds: [...selectedIds.value],
      content: toContentRequest(),
    })
    const result = response.data.data
    confirmOpen.value = false
    /* 발송하면 테스트 카드를 비운다. 진행 중인 테스트 응답·결과 폴링도 버린다. */
    stopTestPolling()
    tested.value = false
    testResults.value = []
    showSendAccepted(result.sendId, result.recipientCount)
    if (result.excludedCount > 0) {
      message.warning(`조건이 바뀌어 ${result.excludedCount}명은 발송에서 제외했습니다.`)
    }
  } catch (error) {
    /* 응답을 못 받으면(예: 60초 타임아웃) 서버가 이미 접수했을 수 있고 멱등키가 없다. 모달을 닫아 곧바로 다시 누르지 않게 하고 이력 확인을 안내한다. */
    if (axios.isAxiosError(error) && !error.response && error.code !== 'ERR_CANCELED') {
      confirmOpen.value = false
      message.warning('발송 요청의 응답을 받지 못했습니다. 이미 접수됐을 수 있으니 발송 이력을 확인한 뒤 다시 시도하세요.')
    } else {
      message.error(getApiErrorMessage(error, '발송 요청에 실패했습니다.'))
    }
  } finally {
    sending.value = false
  }
}

/* ---------- 템플릿 ---------- */

const applyTemplate = (templateId: number | null): void => {
  const template = templates.value.find((item) => item.id === templateId)
  const next: MessageContent = template
    ? {
        templateId: template.id,
        mailEnabled: template.mailSubject !== null,
        smsEnabled: template.smsBody !== null,
        mailSubject: template.mailSubject ?? '',
        mailBody: template.mailBody ?? '',
        smsBody: template.smsBody ?? '',
      }
    : emptyContent()
  content.value = next
  baseContent.value = { ...next }
}

const applyDefaultTemplate = (): void => {
  applyTemplate(typeTemplates.value.find((template) => template.defaultTemplate)?.id ?? null)
}

const revert = (): void => {
  content.value = {
    ...content.value,
    mailSubject: baseContent.value.mailSubject,
    mailBody: baseContent.value.mailBody,
    smsBody: baseContent.value.smsBody,
  }
}

const loadTemplates = async (): Promise<void> => {
  try {
    const response = await messageApi.getTemplates()
    templates.value = response.data.data
  } catch (error) {
    message.error(getApiErrorMessage(error, '템플릿 목록을 불러오지 못했습니다.'))
  }
}

const onTemplateSaved = async (saved: MessageTemplate): Promise<void> => {
  await loadTemplates()
  content.value = { ...content.value, templateId: saved.id }
  baseContent.value = { ...content.value }
}

/* ---------- 공고·전형 ---------- */

let stageRequest = 0

const refreshStages = async (): Promise<void> => {
  const request = ++stageRequest
  const jobPostingId = condition.value.jobPostingId
  if (type.value === 'DEADLINE_REMINDER') {
    stages.value = []
    condition.value = { ...condition.value, stageId: null }
    return
  }
  if (jobPostingId === null) {
    stages.value = []
    condition.value = { ...condition.value, stageId: null }
    return
  }
  stages.value = []
  try {
    const response = await adminStageApi.getStages(jobPostingId)
    if (request !== stageRequest) return
    stages.value = response.data.data
    condition.value = { ...condition.value, stageId: defaultStageId(type.value, stages.value), interviewGroup: 'ALL' }
  } catch (error) {
    if (request !== stageRequest) return
    stages.value = []
    message.error(getApiErrorMessage(error, '전형 목록을 불러오지 못했습니다.'))
  }
}

const onChangePosting = async (jobPostingId: number): Promise<void> => {
  condition.value = { ...initialCondition(type.value, jobPostingId), resultStatus: condition.value.resultStatus }
  await refreshStages()
}

const resetForType = async (): Promise<void> => {
  const selectable = selectablePostings(type.value, postings.value)
  const keep = selectable.some((posting) => posting.id === condition.value.jobPostingId)
  const jobPostingId = keep ? condition.value.jobPostingId : (selectable[0]?.id ?? null)
  condition.value = initialCondition(type.value, jobPostingId)
  channel.value = 'mail'
  applyDefaultTemplate()
  await refreshStages()
}

watch(type, () => {
  void resetForType()
})

/* ---------- 대상자 ---------- */

const query = computed(() => toTargetQuery(type.value, condition.value))

let targetRequest = 0

const loadTargets = async (): Promise<void> => {
  const request = ++targetRequest
  const currentQuery = query.value
  previewIndex.value = 0
  if (currentQuery === null) {
    target.value = null
    selectedIds.value = []
    targetError.value = ''
    targetLoading.value = false
    loadedQuery.value = null
    return
  }
  loadedQuery.value = null
  targetLoading.value = true
  try {
    const response = await messageApi.getTargets(currentQuery)
    if (request !== targetRequest) return
    target.value = response.data.data
    loadedQuery.value = currentQuery
    selectedIds.value = target.value.recipients
      .filter((recipient) => recipient.mailAvailable || recipient.smsAvailable)
      .map((recipient) => recipient.applicationId)
    targetError.value = ''
  } catch (error) {
    if (request !== targetRequest) return
    target.value = null
    loadedQuery.value = null
    selectedIds.value = []
    targetError.value = getApiErrorMessage(error, '대상자를 불러오지 못했습니다.')
  } finally {
    if (request === targetRequest) {
      targetLoading.value = false
    }
  }
}

/* 조건 객체는 바뀔 때마다 새로 만들어지므로 쿼리 내용이 같으면 다시 읽지 않는다. */
watch(
  () => JSON.stringify(query.value),
  () => {
    void loadTargets()
  },
)

watch(
  () => selectedRecipients.value.length,
  (count) => {
    if (previewIndex.value >= count) {
      previewIndex.value = 0
    }
  },
)

onMounted(async () => {
  const [postingResult] = await Promise.allSettled([
    getAllJobPostings(),
    loadTemplates(),
    messageApi.getVariables().then((response) => {
      variables.value = response.data.data
    }),
  ])
  if (postingResult.status === 'fulfilled') {
    postings.value = postingResult.value
  } else {
    message.error(getApiErrorMessage(postingResult.reason, '공고 목록을 불러오지 못했습니다.'))
  }
  if (variables.value.length === 0) {
    message.error('변수 목록을 불러오지 못했습니다.')
  }
  await resetForType()
})

onBeforeUnmount(() => {
  stopTestPolling()
})
</script>

<template>
  <div class="message-send-view">
    <header class="page-header">
      <div>
        <h1 class="page-title">메시지 발송</h1>
        <p class="page-desc">
          보낼 메시지 종류를 고르면 대상자와 내용이 자동으로 채워집니다. 확인 후 테스트 발송, 실제 발송 순서로 진행하세요.
        </p>
      </div>
      <a-space>
        <a-button @click="router.push({ name: 'AdminMessageHistory' })"><HistoryOutlined /> 발송 이력</a-button>
        <a-button @click="router.push({ name: 'AdminMessageTemplates' })"><FileTextOutlined /> 템플릿 관리</a-button>
      </a-space>
    </header>

    <MessageTypePicker v-model="type" />

    <MessageTargetBar
      v-model:condition="condition"
      :type="type"
      :postings="postings"
      :stages="stages"
      :interview-groups="target?.interviewGroups ?? []"
      :selected-count="selectedIds.length"
      :total-count="recipients.length"
      :loading="targetLoading"
      @change-posting="onChangePosting"
      @open-drawer="drawerOpen = true"
    />

    <a-alert v-if="targetError" class="target-alert" type="warning" :message="targetError" show-icon>
      <template #action>
        <a-button size="small" @click="loadTargets">다시 조회</a-button>
      </template>
    </a-alert>

    <div class="work">
      <MessageComposer
        v-model:content="content"
        v-model:channel="channel"
        :type="type"
        :templates="typeTemplates"
        :variables="typeVariables"
        :dirty="dirty"
        :sms-stats="smsStats"
        @select-template="applyTemplate"
        @revert="revert"
        @template-saved="onTemplateSaved"
      />
      <div class="side">
        <MessagePreview
          v-model:index="previewIndex"
          v-model:channel="channel"
          :type="type"
          :recipients="selectedRecipients"
          :content="content"
          :sender="target?.sender ?? null"
        />
        <MessageTestSendCard
          :preview-name="previewRecipient?.name ?? null"
          :block-reason="sendSummary.blockReason"
          :sending="testSending"
          :results="testResults"
          @test="runTest"
        />
      </div>
    </div>

    <MessageSendBar
      :type-name="typeName"
      :summary="sendSummary"
      :mail-enabled="content.mailEnabled"
      :sms-enabled="content.smsEnabled"
      :tested="tested"
      :sending="sending"
      @send="confirmOpen = true"
    />

    <MessageSendConfirmModal
      v-model:open="confirmOpen"
      :type-name="typeName"
      :posting-title="postingTitle"
      :condition-text="conditionText"
      :template-name="templateName"
      :dirty="dirty"
      :summary="sendSummary"
      :mail-enabled="content.mailEnabled"
      :sms-enabled="content.smsEnabled"
      :tested="tested"
      :sending="sending"
      @confirm="confirmSend"
    />

    <MessageRecipientDrawer
      v-model:open="drawerOpen"
      v-model:selected-ids="selectedIds"
      :type="type"
      :recipients="recipients"
    />
  </div>
</template>

<style scoped lang="scss">
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 18px;
}

.page-title {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 700;
}

.page-desc {
  margin: 0;
  color: var(--app-text-secondary);
}

.target-alert {
  margin-bottom: 14px;
}

.work {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 404px;
  gap: 14px;
  align-items: start;
}

.side {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
</style>
