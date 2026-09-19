import type { MessageContent, MessageTargetRecipient, MessageType } from '@/types/admin/message'
import type { StageListItem } from '@/types/admin/stage'
import { RESULT_LABEL, interviewGroupLabel, isInterviewType, type MessageCondition } from './messageCondition'
import { extractVariableKeys, smsKindOf } from './messageRender'

/** 하단 발송 바·확인 모달·테스트 카드가 함께 쓰는 발송 요약. */
export interface SendSummary {
  recipientCount: number
  mailCount: number
  mailMissing: number
  smsCount: number
  lmsCount: number
  smsMissing: number
  /** 본문에 실제로 쓴 변수 중 값이 빈 변수가 있는 수신자 수 */
  missingVariableRecipients: number
  /** 켠 채널 원문에 쓴 변수 중 이 종류에서 쓸 수 없는 것 */
  unknownKeys: string[]
  /** 발송·테스트 발송을 막는 이유. 없으면 null */
  blockReason: string | null
}

export interface SendSummaryInput {
  /** 선택된 수신자 */
  recipients: MessageTargetRecipient[]
  /** recipients 와 같은 순서의 치환 후 SMS byte(SMS 를 받을 수 없는 수신자는 0) */
  smsBytes: number[]
  content: MessageContent
  /** 이 종류에서 쓸 수 있는 변수 키 */
  allowedKeys: string[]
  loading: boolean
  error: string
}

const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/
const MOBILE_PHONE_PATTERN = /^01\d{8,9}$/

/** 서버 MessageContacts.isValidEmail 과 같은 규칙. */
export const isValidEmail = (value: string): boolean => EMAIL_PATTERN.test(value.trim())

/** 서버 MessageContacts.isValidPhone 과 같은 규칙(숫자만 남겨 01로 시작하는 10~11자리). */
export const isValidPhone = (value: string): boolean => MOBILE_PHONE_PATTERN.test(value.replace(/\D/g, ''))

/** 켠 채널 원문에 쓴 변수 키. */
export const usedVariableKeys = (content: MessageContent): string[] => {
  const texts = [
    ...(content.mailEnabled ? [content.mailSubject, content.mailBody] : []),
    ...(content.smsEnabled ? [content.smsBody] : []),
  ]
  return [...new Set(texts.flatMap(extractVariableKeys))]
}

export const buildSendSummary = (input: SendSummaryInput): SendSummary => {
  const { recipients, smsBytes, content } = input
  const recipientCount = recipients.length
  const mailCount = content.mailEnabled ? recipients.filter((recipient) => recipient.mailAvailable).length : 0

  let smsCount = 0
  let lmsCount = 0
  let overLimit = 0
  let smsReachable = 0
  if (content.smsEnabled) {
    recipients.forEach((recipient, index) => {
      if (!recipient.smsAvailable) return
      smsReachable += 1
      const kind = smsKindOf(smsBytes[index] ?? 0)
      if (kind === 'SMS') smsCount += 1
      else if (kind === 'LMS') lmsCount += 1
      else overLimit += 1
    })
  }

  const usedKeys = usedVariableKeys(content)
  const allowed = new Set(input.allowedKeys)
  const unknownKeys = usedKeys.filter((key) => !allowed.has(key))
  const missingVariableRecipients = recipients.filter((recipient) =>
    recipient.missingVariables.some((key) => usedKeys.includes(key)),
  ).length

  return {
    recipientCount,
    mailCount,
    mailMissing: content.mailEnabled ? recipientCount - mailCount : 0,
    smsCount,
    lmsCount,
    smsMissing: content.smsEnabled ? recipientCount - smsReachable : 0,
    missingVariableRecipients,
    unknownKeys,
    blockReason: blockReasonOf(input, recipientCount, mailCount + smsCount + lmsCount, overLimit, unknownKeys),
  }
}

const blockReasonOf = (
  input: SendSummaryInput,
  recipientCount: number,
  deliverableCount: number,
  overLimit: number,
  unknownKeys: string[],
): string | null => {
  const { content } = input
  if (input.loading) return '대상자를 불러오는 중입니다.'
  if (input.error) return '대상자 조회에 실패했습니다. 다시 조회하세요.'
  if (recipientCount === 0) return '선택된 수신자가 없습니다.'
  if (!content.mailEnabled && !content.smsEnabled) return '메일이나 SMS 중 하나 이상 켜세요.'
  if (content.mailEnabled && (!content.mailSubject.trim() || !content.mailBody.trim())) return '메일 제목과 본문을 입력하세요.'
  if (content.smsEnabled && !content.smsBody.trim()) return 'SMS 내용을 입력하세요.'
  if (unknownKeys.length > 0) return '이 종류에서 쓸 수 없는 변수가 있습니다.'
  if (overLimit > 0) return '2,000byte를 넘는 SMS 수신자가 있습니다.'
  if (deliverableCount === 0) return '보낼 수 있는 연락처가 없습니다.'
  return null
}

const resultText = (condition: MessageCondition): string =>
  condition.resultStatus === 'ALL' ? '결과 전체' : RESULT_LABEL[condition.resultStatus]

const applicationStatusText = (condition: MessageCondition): string => {
  if (condition.applicationStatus === 'SUBMITTED') return '제출 완료'
  if (condition.applicationStatus === 'DRAFT') return '작성 중'
  return '작성 중+제출'
}

/** 확인 모달용 조건 설명. 서버 MessageSendService.conditionSummary 와 같은 문구다. */
export const describeCondition = (type: MessageType, condition: MessageCondition, stages: StageListItem[]): string => {
  const stage = type === 'DEADLINE_REMINDER' ? undefined : stages.find((item) => item.id === condition.stageId)
  const parts: string[] = stage ? [stage.stageName] : []
  if (type === 'RESULT_ANNOUNCEMENT') parts.push(resultText(condition))
  if (type === 'DEADLINE_REMINDER') parts.push('작성 중 지원서')
  if (isInterviewType(type)) {
    parts.push(condition.interviewGroup === 'ALL' ? '전체 조' : interviewGroupLabel(condition.interviewGroup))
  }
  if (type === 'FREE') {
    if (stage) parts.push(resultText(condition))
    parts.push(applicationStatusText(condition))
  }
  return parts.join(' · ')
}
