import { formatDate } from '@/common/dateUtil'
import type {
  MessageChannelCount,
  MessageDeliveryStatus,
  MessageHistoryRecipient,
  MessageSendDetail,
  MessageSendStatus,
  MessageTestSendResultItem,
} from '@/types/admin/message'

/** 발송 이력 기본 기간(일). 서버 MessageHistoryService 기본값(종료일 오늘, 시작일 = 종료일 - 29일)과 같다. */
export const HISTORY_RANGE_DAYS = 30

export const DELIVERY_STATUS_LABEL: Record<MessageDeliveryStatus, string> = {
  PENDING: '대기',
  REQUESTED: '결과 수신 중',
  SENT: '성공',
  FAILED: '실패',
  SKIPPED: '제외',
}

/** a-tag color. */
export const DELIVERY_STATUS_COLOR: Record<MessageDeliveryStatus, string> = {
  PENDING: 'default',
  REQUESTED: 'processing',
  SENT: 'green',
  FAILED: 'red',
  SKIPPED: 'default',
}

const FAILURE_REASON_LABEL: Record<string, string> = {
  NO_CONTACT: '연락처 없음',
  INVALID_CONTACT: '형식 오류',
  CHANNEL_OFF: '채널 끔',
  GATEWAY_ERROR: '발송 오류',
}

/** 실패·제외 사유. 서버가 정한 코드가 아니면 솔루션 결과코드다(코드별 설명은 스펙 확정 후, 설계서 17절 6번). */
export const failureReasonLabel = (reason: string | null): string => {
  if (!reason) return ''
  return FAILURE_REASON_LABEL[reason] ?? `결과코드 ${reason}`
}

export interface StatusView {
  label: string
  color: string
}

/** 계산된 발송 상태의 표시. 지연(delayed)이면 결과 수신 중 → 결과 미수신, 발송 중 → 발송 중단(설계서 7.4). */
export const sendStatusView = (status: MessageSendStatus, delayed: boolean): StatusView => {
  if (status === 'COMPLETED') return { label: '완료', color: 'green' }
  if (status === 'RESULT_PENDING') {
    return delayed ? { label: '결과 미수신', color: 'orange' } : { label: '결과 수신 중', color: 'processing' }
  }
  return delayed ? { label: '발송 중단', color: 'red' } : { label: '발송 중', color: 'blue' }
}

/** 그 채널로 실제로 보내려 한 건수(제외 빼고). */
const targetCount = (count: MessageChannelCount): number =>
  count.pending + count.requested + count.sent + count.failed

/** 목록의 채널 칸. 끈 채널은 수신자가 모두 SKIPPED 라 건수가 아니라 enabled 로 판단해 '제외'로 보인다. */
export const channelCellText = (enabled: boolean, count: MessageChannelCount): string =>
  enabled ? `${targetCount(count)}건` : '제외'

/** 상세의 채널 집계 한 줄. */
export const channelSummaryText = (enabled: boolean, count: MessageChannelCount): string => {
  if (!enabled) return '이번 발송에서 제외'
  const inProgress = count.pending + count.requested
  return `${targetCount(count)}건 · 성공 ${count.sent} · 실패 ${count.failed} · 수신 중 ${inProgress} · 제외 ${count.skipped}`
}

export interface ResultCounts {
  sent: number
  failed: number
  /** 호출 전(PENDING) + 결과 대기(REQUESTED) */
  inProgress: number
}

/** 목록 결과 칸: 두 채널 합계. */
export const resultCounts = (mail: MessageChannelCount, sms: MessageChannelCount): ResultCounts => ({
  sent: mail.sent + sms.sent,
  failed: mail.failed + sms.failed,
  inProgress: mail.pending + mail.requested + sms.pending + sms.requested,
})

/** 지원서 수신자인데 이름·연락처가 모두 비었으면 파기된 수신자다(테스트 수신자는 파기 대상이 아니다). */
export const isPurgedRecipient = (recipient: MessageHistoryRecipient): boolean =>
  recipient.applicationId !== null && recipient.name === null && recipient.email === null && recipient.phone === null

export const recipientNameLabel = (recipient: MessageHistoryRecipient): string =>
  isPurgedRecipient(recipient) ? '(파기됨)' : (recipient.name ?? '-')

/** 테스트 발송 카드용: 이력 상세의 수신자별 결과를 테스트 발송 응답과 같은 모양(수신자마다 메일 → SMS)으로 바꾼다. */
export const toTestResults = (detail: MessageSendDetail): MessageTestSendResultItem[] =>
  detail.recipients.flatMap((recipient): MessageTestSendResultItem[] => [
    {
      name: recipient.name ?? '',
      channel: 'MAIL',
      status: recipient.mailStatus,
      failureReason: recipient.mailFailureReason,
    },
    {
      name: recipient.name ?? '',
      channel: 'SMS',
      status: recipient.smsStatus,
      failureReason: recipient.smsFailureReason,
    },
  ])

/** 결과 수신 중(REQUESTED)인 채널이 남았는지. */
export const hasRequested = (results: MessageTestSendResultItem[]): boolean =>
  results.some((result) => result.status === 'REQUESTED')

/** 이력 화면 기본 기간 [시작일, 종료일](YYYY-MM-DD, 양끝 포함, 로컬 날짜 기준). */
export const defaultHistoryRange = (today: Date): [string, string] => {
  const from = new Date(today.getFullYear(), today.getMonth(), today.getDate() - (HISTORY_RANGE_DAYS - 1))
  return [formatDate(from, 'YYYY-MM-DD'), formatDate(today, 'YYYY-MM-DD')]
}
