<script setup lang="ts">
import { computed } from 'vue'
import { LeftOutlined, RightOutlined } from '@ant-design/icons-vue'

import { formatDate } from '@/common/dateUtil'
import type { MessageContent, MessageSender, MessageTargetRecipient, MessageType } from '@/types/admin/message'
import { interviewGroupLabel, isInterviewType, recipientResultTag, type ResultTag } from './messageCondition'
import { renderMessage, renderParts, smsByteLength, smsKindOf } from './messageRender'

type Channel = 'mail' | 'sms'

const props = defineProps<{
  type: MessageType
  /** 선택된 수신자 */
  recipients: MessageTargetRecipient[]
  content: MessageContent
  sender: MessageSender | null
}>()

const index = defineModel<number>('index', { required: true })
const channel = defineModel<Channel>('channel', { required: true })

const current = computed<MessageTargetRecipient | null>(() => props.recipients[index.value] ?? null)
const values = computed<Record<string, string>>(() => current.value?.variables ?? {})

const subjectParts = computed(() => renderParts(props.content.mailSubject, values.value))
const mailBodyParts = computed(() => renderParts(props.content.mailBody, values.value))
const smsParts = computed(() => renderParts(props.content.smsBody, values.value))
const smsKind = computed(() => smsKindOf(smsByteLength(renderMessage(props.content.smsBody, values.value))))

const subInfo = computed(() => {
  const recipient = current.value
  if (!recipient) return ''
  if (isInterviewType(props.type)) {
    return `${interviewGroupLabel(recipient.interviewGroup)} ${formatDate(recipient.interviewDateTime, 'HH:mm')}`
  }
  if (props.type === 'DEADLINE_REMINDER') {
    return `작성 시작 ${formatDate(recipient.draftStartedAt, 'MM-DD HH:mm')}`
  }
  return ''
})

/** 결과 발표의 수신자 정보는 텍스트 대신 색 배지로 보여준다. */
const subResultTag = computed<ResultTag | null>(() =>
  props.type === 'RESULT_ANNOUNCEMENT' ? recipientResultTag(current.value?.resultStatus ?? null) : null,
)

/* 미리보기를 그릴 수 없는 이유. 빈 문자열이면 그린다. */
const blockedReason = computed(() => {
  const recipient = current.value
  if (!recipient) return '미리볼 수신자가 없습니다. 조건을 고르거나 대상자를 선택하세요.'
  const name = recipient.name ?? '이 수신자'
  if (channel.value === 'mail') {
    if (!props.content.mailEnabled) return '이번 발송에서 메일은 제외됩니다.'
    if (!recipient.mailAvailable) return `${name}님은 이메일이 없거나 형식이 맞지 않아 메일에서 제외됩니다.`
  } else {
    if (!props.content.smsEnabled) return '이번 발송에서 SMS는 제외됩니다.'
    if (!recipient.smsAvailable) return `${name}님은 휴대폰 번호가 없거나 형식이 맞지 않아 SMS에서 제외됩니다.`
  }
  return ''
})

const move = (step: number): void => {
  const count = props.recipients.length
  if (count > 0) {
    index.value = (index.value + step + count) % count
  }
}
</script>

<template>
  <section class="preview">
    <div class="preview-head">
      <span class="preview-title">미리보기</span>
      <a-radio-group v-model:value="channel" size="small" button-style="solid">
        <a-radio-button value="mail">메일</a-radio-button>
        <a-radio-button value="sms">SMS</a-radio-button>
      </a-radio-group>
    </div>

    <div class="who">
      <a-button size="small" :disabled="recipients.length < 2" aria-label="이전 수신자" @click="move(-1)">
        <LeftOutlined />
      </a-button>
      <span class="who-name">{{ current?.name ?? '-' }}</span>
      <span class="who-sub">
        <a-tag v-if="subResultTag" :color="subResultTag.color" class="sub-tag">{{ subResultTag.label }}</a-tag>
        <template v-else>{{ subInfo }}</template>
      </span>
      <span class="who-pos">{{ recipients.length ? index + 1 : 0 }} / {{ recipients.length }}</span>
      <a-button size="small" :disabled="recipients.length < 2" aria-label="다음 수신자" @click="move(1)">
        <RightOutlined />
      </a-button>
    </div>

    <div class="stage">
      <p v-if="blockedReason" class="blocked">{{ blockedReason }}</p>

      <div v-else-if="channel === 'mail'" class="mail">
        <div class="mail-meta">
          <div class="mail-subject">
            <template v-for="(part, partIndex) in subjectParts" :key="partIndex">
              <mark v-if="part.kind !== 'text'" :class="part.kind">{{ part.text }}</mark>
              <template v-else>{{ part.text }}</template>
            </template>
          </div>
          <div>보낸사람 <strong>{{ sender?.name }}</strong> &lt;{{ sender?.email }}&gt;</div>
          <div>받는사람 {{ current?.name }} &lt;{{ current?.email }}&gt;</div>
        </div>
        <div class="mail-brand"><span class="brand-mark">SY</span>신영증권 채용</div>
        <div class="mail-body">
          <template v-for="(part, partIndex) in mailBodyParts" :key="partIndex">
            <mark v-if="part.kind !== 'text'" :class="part.kind">{{ part.text }}</mark>
            <template v-else>{{ part.text }}</template>
          </template>
        </div>
        <div class="mail-foot">본 메일은 발신 전용입니다.</div>
      </div>

      <div v-else class="phone">
        <div class="phone-screen">
          <div class="phone-top">
            <span class="notch" />
            <strong>{{ sender?.smsCallbackNumber }}</strong>
            <span>문자 메시지</span>
          </div>
          <div class="phone-body">
            <div class="bubble">
              <span>[Web발신]</span>{{ '\n' }}
              <template v-for="(part, partIndex) in smsParts" :key="partIndex">
                <mark v-if="part.kind !== 'text'" :class="part.kind">{{ part.text }}</mark>
                <template v-else>{{ part.text }}</template>
              </template>
            </div>
            <div class="phone-meta">
              <span>{{ smsKind ?? '2000byte 초과' }}</span>
              <span>{{ current?.phone }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.preview {
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius-lg);
  background: var(--app-bg-surface);
  box-shadow: var(--app-card-shadow);
  overflow: hidden;
}

.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid var(--app-border-default);
}

.preview-title {
  font-weight: 600;
}

.who {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--app-border-default);
  background: var(--app-bg-soft);
  font-size: 12.5px;
}

.who-name {
  font-weight: 600;
}

.who-sub {
  color: var(--app-text-secondary);
}

.sub-tag {
  margin: 0;
  line-height: 1.5;
}

.who-pos {
  margin-left: auto;
  color: var(--app-text-secondary);
}

.stage {
  display: flex;
  justify-content: center;
  min-height: 470px;
  padding: 18px;
  background: repeating-linear-gradient(45deg, #f3f5f7, #f3f5f7 10px, #eff2f4 10px, #eff2f4 20px);
}

.blocked {
  align-self: center;
  margin: 0;
  text-align: center;
  color: var(--app-text-secondary);
}

mark {
  border-radius: 3px;
  padding: 0 2px;

  &.value {
    background: #e7f3df;
    color: var(--app-color-primary-active);
  }

  &.missing {
    background: #fff1f0;
    color: var(--app-color-error);
  }
}

.mail {
  align-self: flex-start;
  width: 100%;
  border: 1px solid var(--app-border-default);
  border-radius: 10px;
  background: var(--app-bg-surface);
  overflow: hidden;
}

.mail-meta {
  padding: 10px 12px;
  border-bottom: 1px solid var(--app-border-default);
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--app-text-secondary);
}

.mail-subject {
  margin-bottom: 2px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.mail-brand {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 11px 16px;
  background: var(--app-color-primary);
  color: #fff;
  font-size: 12.5px;
  font-weight: 600;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 4px;
  background: #fff;
  color: var(--app-color-primary);
  font-size: 10px;
  font-weight: 700;
}

.mail-body {
  max-height: 320px;
  overflow: auto;
  padding: 16px;
  font-size: 12px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-all;
}

.mail-foot {
  padding: 10px 16px;
  border-top: 1px solid var(--app-border-default);
  background: var(--app-bg-muted);
  font-size: 10.5px;
  color: var(--app-text-muted);
}

.phone {
  width: 264px;
  padding: 9px;
  border-radius: 34px;
  background: #111;
}

.phone-screen {
  display: flex;
  flex-direction: column;
  height: 452px;
  border-radius: 26px;
  background: #fff;
  overflow: hidden;
}

.phone-top {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 14px 8px;
  border-bottom: 1px solid #eee;
  font-size: 12.5px;

  span:last-child {
    font-size: 10.5px;
    color: var(--app-text-muted);
  }
}

.notch {
  width: 70px;
  height: 6px;
  margin-bottom: 8px;
  border-radius: 3px;
  background: #e5e5e5;
}

.phone-body {
  flex: 1;
  overflow: auto;
  padding: 12px 10px;
  background: #f7f7f8;
}

.bubble {
  max-width: 92%;
  padding: 9px 11px;
  border: 1px solid #ececec;
  border-radius: 14px 14px 14px 4px;
  background: #fff;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-all;
}

.phone-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  padding: 0 2px;
  font-size: 10.5px;
  color: var(--app-text-muted);
}
</style>
