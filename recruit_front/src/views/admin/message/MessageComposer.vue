<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  FileTextOutlined,
  InfoCircleOutlined,
  MailOutlined,
  MessageOutlined,
  RollbackOutlined,
  SaveOutlined,
} from '@ant-design/icons-vue'

import { messageApi } from '@/api/admin/messageApi'
import { getApiErrorMessage } from '@/api/apiError'
import type {
  MessageContent,
  MessageTemplate,
  MessageTemplateSaveRequest,
  MessageType,
  MessageVariable,
} from '@/types/admin/message'
import { LMS_MAX_BYTES, SMS_MAX_BYTES, type SmsStats } from './messageRender'
import { usedVariableKeys } from './messageSendSummary'
import { FIELD_MAX_LENGTH, useVariableCursor, type EditableField } from './useVariableCursor'

type Channel = 'mail' | 'sms'

/* 템플릿 셀렉트의 "새로 작성" 값. 실제 템플릿 id 와 겹치지 않는다. */
const NEW_TEMPLATE = -1

const props = defineProps<{
  type: MessageType
  /** 이 종류의 템플릿(기본 우선 정렬) */
  templates: MessageTemplate[]
  /** 이 종류에서 쓸 수 있는 변수 */
  variables: MessageVariable[]
  dirty: boolean
  smsStats: SmsStats
}>()

const content = defineModel<MessageContent>('content', { required: true })
const channel = defineModel<Channel>('channel', { required: true })

const emit = defineEmits<{
  selectTemplate: [templateId: number | null]
  revert: []
  templateSaved: [template: MessageTemplate]
}>()

const templateOptions = computed(() => [
  ...props.templates.map((template) => ({
    value: template.id,
    label: template.defaultTemplate ? `★ ${template.name} (기본)` : template.name,
  })),
  { value: NEW_TEMPLATE, label: '새로 작성 (빈 양식)' },
])

const update = (field: EditableField, value: string): void => {
  content.value = { ...content.value, [field]: value }
}

const { resetCursor, rememberCursor, insertVariable } = useVariableCursor((field) => content.value[field], update)

const channelBody = (): EditableField => (channel.value === 'sms' ? 'smsBody' : 'mailBody')

const selectTemplate = (value: unknown): void => {
  resetCursor(channelBody())
  emit('selectTemplate', value === NEW_TEMPLATE ? null : Number(value))
}

watch(channel, () => resetCursor(channelBody()))

const channelEnabled = computed(() => (channel.value === 'sms' ? content.value.smsEnabled : content.value.mailEnabled))

const toggleChannel = (target: Channel, enabled: boolean): void => {
  content.value =
    target === 'mail' ? { ...content.value, mailEnabled: enabled } : { ...content.value, smsEnabled: enabled }
}

/* 켠 채널 원문에 쓴 변수 중 이 종류에서 쓸 수 없는 것. 서버도 발송 시 400 으로 막는다. */
const unknownKeys = computed(() => {
  const allowed = new Set(props.variables.map((variable) => variable.key))
  return usedVariableKeys(content.value).filter((key) => !allowed.has(key))
})

const smsLimit = computed(() => (props.smsStats.kind === 'SMS' ? SMS_MAX_BYTES : LMS_MAX_BYTES))
const smsPercent = computed(() => Math.min(100, Math.round((props.smsStats.currentBytes / smsLimit.value) * 100)))
const smsNote = computed(() => {
  if (props.smsStats.kind === null) {
    return `${props.smsStats.maxBytes}byte인 수신자가 있어 보낼 수 없습니다. 2,000byte 이내로 줄이세요.`
  }
  if (props.smsStats.kind === 'LMS') {
    return `90byte를 넘는 수신자가 있어 LMS로 발송됩니다(최대 ${props.smsStats.maxBytes}byte).`
  }
  return '모든 수신자가 90byte 이내입니다.'
})

/* ---------- 템플릿으로 저장 ---------- */

const saveOpen = ref(false)
const saveMode = ref<'overwrite' | 'new'>('new')
const newName = ref('')
const saving = ref(false)

const currentTemplate = computed(() => props.templates.find((template) => template.id === content.value.templateId))

const openSave = (): void => {
  saveMode.value = currentTemplate.value ? 'overwrite' : 'new'
  newName.value = ''
  saveOpen.value = true
}

const blankToNull = (value: string): string | null => (value.trim() ? value : null)

const submitSave = async (): Promise<void> => {
  const current = currentTemplate.value
  const overwrite = saveMode.value === 'overwrite' && current !== undefined
  const name = overwrite ? current.name : newName.value.trim()
  if (!name) {
    message.warning('템플릿 이름을 입력해 주세요.')
    return
  }
  const request: MessageTemplateSaveRequest = {
    type: props.type,
    name,
    defaultTemplate: overwrite ? current.defaultTemplate : false,
    mailSubject: blankToNull(content.value.mailSubject),
    mailBody: blankToNull(content.value.mailBody),
    smsBody: blankToNull(content.value.smsBody),
  }

  saving.value = true
  try {
    const response = overwrite
      ? await messageApi.updateTemplate(current.id, request)
      : await messageApi.createTemplate(request)
    message.success('템플릿을 저장했습니다.')
    saveOpen.value = false
    emit('templateSaved', response.data.data)
  } catch (error) {
    message.error(getApiErrorMessage(error, '템플릿을 저장하지 못했습니다.'))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="composer">
    <div class="composer-head">
      <FileTextOutlined class="head-icon" />
      <a-select
        class="template-select"
        :value="content.templateId ?? NEW_TEMPLATE"
        :options="templateOptions"
        @change="selectTemplate"
      />
      <a-tag v-if="dirty && content.templateId !== null" color="orange">템플릿에서 수정됨</a-tag>
      <a-space class="head-actions">
        <a-button size="small" type="text" :disabled="!dirty" @click="emit('revert')">
          <RollbackOutlined /> 되돌리기
        </a-button>
        <a-button size="small" @click="openSave"><SaveOutlined /> 템플릿으로 저장</a-button>
      </a-space>
    </div>

    <a-tabs v-model:active-key="channel" class="channel-tabs">
      <a-tab-pane key="mail">
        <template #tab>
          <span class="tab-label" :class="{ off: !content.mailEnabled }">
            <MailOutlined /> 메일
            <span class="switch-wrap" @click.stop>
              <a-switch
                size="small"
                :checked="content.mailEnabled"
                aria-label="메일 발송 켜기/끄기"
                @change="(checked: unknown) => toggleChannel('mail', checked === true)"
              />
            </span>
          </span>
        </template>
        <div v-if="content.mailEnabled" class="pane">
          <div class="field">
            <span class="field-label">제목</span>
            <a-input
              :value="content.mailSubject"
              :maxlength="FIELD_MAX_LENGTH.mailSubject"
              @update:value="(value: string) => update('mailSubject', value)"
              @blur="rememberCursor('mailSubject', $event)"
            />
          </div>
          <div class="field">
            <span class="field-label">본문</span>
            <a-textarea
              :value="content.mailBody"
              :rows="14"
              :maxlength="FIELD_MAX_LENGTH.mailBody"
              @update:value="(value: string) => update('mailBody', value)"
              @blur="rememberCursor('mailBody', $event)"
            />
          </div>
        </div>
        <p v-else class="off-note">메일은 이번 발송에서 제외됩니다. 탭의 스위치로 다시 켤 수 있습니다.</p>
      </a-tab-pane>

      <a-tab-pane key="sms">
        <template #tab>
          <span class="tab-label" :class="{ off: !content.smsEnabled }">
            <MessageOutlined /> SMS
            <span class="switch-wrap" @click.stop>
              <a-switch
                size="small"
                :checked="content.smsEnabled"
                aria-label="SMS 발송 켜기/끄기"
                @change="(checked: unknown) => toggleChannel('sms', checked === true)"
              />
            </span>
          </span>
        </template>
        <div v-if="content.smsEnabled" class="pane">
          <div class="field">
            <span class="field-label">문자 내용</span>
            <a-textarea
              :value="content.smsBody"
              :rows="8"
              :maxlength="FIELD_MAX_LENGTH.smsBody"
              @update:value="(value: string) => update('smsBody', value)"
              @blur="rememberCursor('smsBody', $event)"
            />
          </div>
          <div class="sms-counter">
            <span>
              <a-tag v-if="smsStats.kind === null" color="red">2000byte 초과</a-tag>
              <a-tag v-else-if="smsStats.kind === 'LMS'" color="orange">LMS 장문</a-tag>
              <a-tag v-else color="green">SMS 단문</a-tag>
              <span class="counter-note">{{ smsNote }}</span>
            </span>
            <span>
              <strong>{{ smsStats.currentBytes }}</strong> / {{ smsLimit }} byte
              <span class="counter-note">(미리보기 대상 기준)</span>
            </span>
          </div>
          <a-progress :percent="smsPercent" :show-info="false" size="small" />
        </div>
        <p v-else class="off-note">SMS는 이번 발송에서 제외됩니다. 탭의 스위치로 다시 켤 수 있습니다.</p>
      </a-tab-pane>
    </a-tabs>

    <a-alert
      v-if="unknownKeys.length"
      class="unknown-alert"
      type="error"
      show-icon
      :message="`이 종류에서 쓸 수 없는 변수가 있습니다: ${unknownKeys.map((key) => `#{${key}}`).join(', ')}`"
    />

    <div class="variables">
      <span class="variables-label">변수 넣기</span>
      <a-tooltip v-for="variable in variables" :key="variable.key" :title="variable.label">
        <button
          type="button"
          class="variable-chip"
          :disabled="!channelEnabled"
          @click="insertVariable(variable.key)"
        >
          {{ variable.key }}
        </button>
      </a-tooltip>
    </div>

    <p class="composer-foot">
      <InfoCircleOutlined /> 변수는 수신자마다 실제 값으로 바뀝니다. 여기서 고친 내용은 이번 발송에만 적용됩니다.
    </p>

    <a-modal
      v-model:open="saveOpen"
      title="템플릿으로 저장"
      ok-text="저장"
      cancel-text="취소"
      :confirm-loading="saving"
      @ok="submitSave"
    >
      <a-radio-group v-model:value="saveMode" class="save-mode">
        <a-radio value="overwrite" :disabled="!currentTemplate">
          현재 템플릿 덮어쓰기<template v-if="currentTemplate"> ({{ currentTemplate.name }})</template>
        </a-radio>
        <a-radio value="new">새 템플릿으로 저장</a-radio>
      </a-radio-group>
      <a-input v-if="saveMode === 'new'" v-model:value="newName" :maxlength="100" placeholder="템플릿 이름" />
    </a-modal>
  </section>
</template>

<style scoped lang="scss">
.composer {
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius-lg);
  background: var(--app-bg-surface);
  box-shadow: var(--app-card-shadow);
}

.composer-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--app-border-default);
}

.head-icon {
  color: var(--app-text-secondary);
  font-size: 16px;
}

.template-select {
  flex: 1;
  max-width: 340px;
}

.head-actions {
  margin-left: auto;
}

.channel-tabs {
  padding: 0 16px;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;

  &.off {
    opacity: 0.6;
  }
}

.switch-wrap {
  display: inline-flex;
  margin-left: 4px;
}

.pane {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 12px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.field-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--app-text-secondary);
}

.sms-counter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
}

.counter-note {
  color: var(--app-text-secondary);
}

.off-note {
  margin: 0;
  padding: 28px 0;
  text-align: center;
  color: var(--app-text-secondary);
}

.unknown-alert {
  margin: 0 16px 10px;
}

.variables {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin: 0 16px 14px;
  padding: 10px 12px;
  border: 1px dashed var(--app-border-strong);
  border-radius: var(--app-border-radius);
  background: var(--app-bg-muted);
}

.variables-label {
  margin-right: 2px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.variable-chip {
  height: 24px;
  padding: 0 10px;
  border: 1px solid #cfe0c6;
  border-radius: 14px;
  background: var(--app-bg-surface);
  color: var(--app-color-primary);
  font-size: 12px;
  cursor: pointer;

  &:hover {
    background: var(--app-bg-selected);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.composer-foot {
  margin: 0;
  padding: 10px 16px;
  border-top: 1px solid var(--app-border-default);
  border-radius: 0 0 var(--app-border-radius-lg) var(--app-border-radius-lg);
  background: var(--app-bg-muted);
  font-size: 12px;
  color: var(--app-text-secondary);
}

.save-mode {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}
</style>
