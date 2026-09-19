<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { CloseOutlined, ExperimentOutlined, PlusOutlined, SendOutlined } from '@ant-design/icons-vue'

import type { MessageChannel, MessageTester, MessageTestSendResultItem } from '@/types/admin/message'
import { DELIVERY_STATUS_COLOR, DELIVERY_STATUS_LABEL, failureReasonLabel } from './messageHistory'
import { isValidEmail, isValidPhone } from './messageSendSummary'

const MAX_TESTERS = 5
/* 최근 테스트 수신자는 이 브라우저에만 기억한다. 저장소를 못 쓰면 기억만 하지 않는다. */
const STORAGE_KEY = 'recruit.message.testers'

const props = defineProps<{
  /** 미리보기 중인 수신자 이름. 없으면 테스트 발송을 할 수 없다. */
  previewName: string | null
  /** 발송 요약의 막는 이유(테스트 발송도 같은 조건으로 막는다) */
  blockReason: string | null
  sending: boolean
  results: MessageTestSendResultItem[]
}>()

const emit = defineEmits<{
  test: [testers: MessageTester[]]
}>()

const CHANNEL_TEXT: Record<MessageChannel, string> = {
  MAIL: '메일',
  SMS: 'SMS',
}

/* 저장값은 옛 형식이거나 손댄 값일 수 있어 추가할 때와 같은 규칙으로 다시 검사한다. */
const isTester = (value: unknown): value is MessageTester => {
  if (typeof value !== 'object' || value === null) return false
  const { name, email, phone } = value as Partial<MessageTester>
  if (typeof name !== 'string' || !name.trim()) return false
  return typeof email === 'string' ? isValidEmail(email) : typeof phone === 'string' && isValidPhone(phone)
}

/* 같은 연락처를 두 번 넣지 않도록 비교용 키를 만든다(휴대폰은 숫자만, 이메일은 소문자). */
const contactKey = (text: string): string => (text.includes('@') ? text.toLowerCase() : text.replace(/\D/g, ''))

const loadTesters = (): MessageTester[] => {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    const parsed: unknown = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed.filter(isTester).slice(0, MAX_TESTERS) : []
  } catch {
    return []
  }
}

const testers = ref<MessageTester[]>(loadTesters())

watch(
  testers,
  (value) => {
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(value))
    } catch {
      /* 저장소를 못 쓰면 기억하지 않는다. */
    }
  },
  { deep: true },
)

const name = ref('')
const contact = ref('')

const addTester = (): void => {
  const value = contact.value.trim()
  if (!value) {
    message.warning('이메일이나 휴대폰 번호를 입력해 주세요.')
    return
  }
  if (testers.value.length >= MAX_TESTERS) {
    message.warning(`테스트 수신자는 최대 ${MAX_TESTERS}명입니다.`)
    return
  }
  const email = value.includes('@')
  if (email ? !isValidEmail(value) : !isValidPhone(value)) {
    message.warning('연락처 형식이 올바르지 않습니다.')
    return
  }
  if (testers.value.some((tester) => contactKey(tester.email ?? tester.phone ?? '') === contactKey(value))) {
    message.warning('이미 추가한 연락처입니다.')
    return
  }
  testers.value = [
    ...testers.value,
    { name: name.value.trim() || '담당자', email: email ? value : null, phone: email ? null : value },
  ]
  name.value = ''
  contact.value = ''
}

const removeTester = (index: number): void => {
  testers.value = testers.value.filter((_, position) => position !== index)
}

const disabledReason = computed(() => {
  if (props.blockReason) return props.blockReason
  if (!props.previewName) return '미리볼 수신자가 없습니다.'
  if (testers.value.length === 0) return '테스트 받을 담당자를 추가하세요.'
  return null
})

/* 채널을 껐거나 그 담당자에게 해당 연락처가 없어 건너뛴 결과는 보여 주지 않는다. */
const visibleResults = computed(() =>
  props.results.filter((result) => result.failureReason !== 'CHANNEL_OFF' && result.failureReason !== 'NO_CONTACT'),
)
</script>

<template>
  <section class="test-card">
    <div class="card-head">
      <span class="card-title"><ExperimentOutlined /> 테스트 발송</span>
      <span class="card-hint">인사팀 담당자에게 먼저 보내 확인</span>
    </div>

    <div class="tester-input">
      <a-input v-model:value="name" class="name-input" placeholder="이름" :maxlength="50" />
      <a-input v-model:value="contact" placeholder="이메일 또는 휴대폰" :maxlength="200" @press-enter="addTester" />
      <a-button aria-label="테스트 수신자 추가" @click="addTester"><PlusOutlined /></a-button>
    </div>

    <div class="chips">
      <span v-for="(tester, index) in testers" :key="`${index}-${tester.email ?? tester.phone}`" class="chip">
        {{ tester.name }} · {{ tester.email ?? tester.phone }}
        <button type="button" class="chip-remove" :aria-label="`${tester.name} 삭제`" @click="removeTester(index)">
          <CloseOutlined />
        </button>
      </span>
      <span v-if="testers.length === 0" class="empty">받을 담당자를 추가하세요</span>
    </div>

    <a-tooltip :title="disabledReason">
      <a-button block :disabled="disabledReason !== null" :loading="sending" @click="emit('test', testers)">
        <SendOutlined /> 현재 미리보기 내용으로 테스트 발송
      </a-button>
    </a-tooltip>
    <p v-if="previewName" class="preview-note">
      {{ previewName }}님 데이터로 치환하고 메일 제목과 문자 앞에 [테스트]를 붙입니다.
    </p>

    <ul v-if="visibleResults.length" class="results">
      <li v-for="(result, index) in visibleResults" :key="index">
        <a-tag :color="DELIVERY_STATUS_COLOR[result.status]">{{ DELIVERY_STATUS_LABEL[result.status] }}</a-tag>
        {{ result.name }} · {{ CHANNEL_TEXT[result.channel] }}
        <span v-if="result.failureReason" class="reason">({{ failureReasonLabel(result.failureReason) }})</span>
      </li>
    </ul>
  </section>
</template>

<style scoped lang="scss">
.test-card {
  padding: 14px;
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius-lg);
  background: var(--app-bg-surface);
  box-shadow: var(--app-card-shadow);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.card-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.card-hint,
.preview-note,
.empty {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.tester-input {
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr) auto;
  gap: 6px;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 8px 0 10px;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 6px 3px 10px;
  border: 1px solid var(--app-border-default);
  border-radius: 16px;
  background: var(--app-bg-muted);
  font-size: 12px;
}

.chip-remove {
  display: inline-flex;
  padding: 0;
  border: 0;
  background: none;
  color: var(--app-text-muted);
  cursor: pointer;
}

.preview-note {
  margin: 8px 0 0;
}

.results {
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
  font-size: 12px;

  li + li {
    margin-top: 4px;
  }
}

.reason {
  color: var(--app-text-secondary);
}
</style>
