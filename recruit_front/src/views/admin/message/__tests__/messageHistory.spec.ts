import { describe, expect, it } from 'vitest'

import type { MessageChannelCount, MessageHistoryRecipient, MessageSendDetail } from '@/types/admin/message'
import {
  DELIVERY_STATUS_COLOR,
  DELIVERY_STATUS_LABEL,
  channelCellText,
  channelSummaryText,
  defaultHistoryRange,
  failureReasonLabel,
  hasRequested,
  isPurgedRecipient,
  recipientNameLabel,
  resultCounts,
  sendStatusView,
  toTestResults,
} from '../messageHistory'

const count = (overrides: Partial<MessageChannelCount> = {}): MessageChannelCount => ({
  pending: 0,
  requested: 0,
  sent: 0,
  failed: 0,
  skipped: 0,
  ...overrides,
})

const recipient = (overrides: Partial<MessageHistoryRecipient> = {}): MessageHistoryRecipient => ({
  id: 1,
  applicationId: 10,
  name: '김지원',
  email: 'kim@example.com',
  phone: '01000000000',
  mailStatus: 'SENT',
  mailFailureReason: null,
  smsStatus: 'SKIPPED',
  smsFailureReason: 'NO_CONTACT',
  smsKind: null,
  ...overrides,
})

const detail = (recipients: MessageHistoryRecipient[]): MessageSendDetail => ({
  id: 7,
  requestedAt: '2026-09-19T10:00:00',
  type: 'FREE',
  test: true,
  origin: 'ADMIN',
  jobPostingTitle: '공고',
  stageName: null,
  conditionSummary: '제출 완료',
  title: '[신영증권] 안내',
  mailEnabled: true,
  smsEnabled: true,
  recipientCount: recipients.length,
  mail: count(),
  sms: count(),
  status: 'RESULT_PENDING',
  delayed: false,
  senderName: '김인사',
  templateName: null,
  mailSubject: '[신영증권] 안내',
  mailBody: '본문',
  smsBody: '문자',
  recipients,
})

describe('sendStatusView', () => {
  it('계산된 발송 상태를 라벨과 태그 색으로 바꾼다', () => {
    expect(sendStatusView('SENDING', false)).toEqual({ label: '발송 중', color: 'blue' })
    expect(sendStatusView('RESULT_PENDING', false)).toEqual({ label: '결과 수신 중', color: 'processing' })
    expect(sendStatusView('COMPLETED', false)).toEqual({ label: '완료', color: 'green' })
  })

  it('지연이면 결과 수신 중은 결과 미수신, 발송 중은 발송 중단으로 보인다', () => {
    expect(sendStatusView('RESULT_PENDING', true)).toEqual({ label: '결과 미수신', color: 'orange' })
    expect(sendStatusView('SENDING', true)).toEqual({ label: '발송 중단', color: 'red' })
    expect(sendStatusView('COMPLETED', true)).toEqual({ label: '완료', color: 'green' })
  })
})

describe('채널 결과 표시', () => {
  it('REQUESTED 는 결과 수신 중(processing 태그)이다', () => {
    expect(DELIVERY_STATUS_LABEL.REQUESTED).toBe('결과 수신 중')
    expect(DELIVERY_STATUS_COLOR.REQUESTED).toBe('processing')
    expect(DELIVERY_STATUS_LABEL.PENDING).toBe('대기')
    expect(DELIVERY_STATUS_LABEL.SENT).toBe('성공')
    expect(DELIVERY_STATUS_LABEL.FAILED).toBe('실패')
    expect(DELIVERY_STATUS_LABEL.SKIPPED).toBe('제외')
  })

  it('정해진 사유는 한글로, 그 밖의 값은 솔루션 결과코드로 보인다', () => {
    expect(failureReasonLabel('NO_CONTACT')).toBe('연락처 없음')
    expect(failureReasonLabel('INVALID_CONTACT')).toBe('형식 오류')
    expect(failureReasonLabel('CHANNEL_OFF')).toBe('채널 끔')
    expect(failureReasonLabel('GATEWAY_ERROR')).toBe('발송 오류')
    expect(failureReasonLabel('9999')).toBe('결과코드 9999')
    expect(failureReasonLabel(null)).toBe('')
  })
})

describe('채널 건수', () => {
  it('목록 채널 칸은 끈 채널이면 제외, 켠 채널이면 제외를 뺀 건수다', () => {
    expect(channelCellText(false, count({ skipped: 3 }))).toBe('제외')
    expect(channelCellText(true, count({ requested: 1, sent: 2, failed: 1, skipped: 1 }))).toBe('4건')
  })

  it('상세 채널 집계는 성공·실패·수신 중·제외를 한 줄로 보여 준다', () => {
    expect(channelSummaryText(true, count({ pending: 1, requested: 1, sent: 2, failed: 1, skipped: 1 }))).toBe(
      '5건 · 성공 2 · 실패 1 · 수신 중 2 · 제외 1',
    )
    expect(channelSummaryText(false, count({ skipped: 2 }))).toBe('이번 발송에서 제외')
  })

  it('목록 결과는 두 채널을 합치고 수신 중에는 호출 전과 결과 대기를 모두 센다', () => {
    expect(resultCounts(count({ sent: 2, requested: 1 }), count({ failed: 1, pending: 2 }))).toEqual({
      sent: 2,
      failed: 1,
      inProgress: 3,
    })
  })
})

describe('수신자 표시', () => {
  it('지원서 수신자인데 이름·연락처가 모두 비었으면 파기됨으로 보인다', () => {
    const purged = recipient({ name: null, email: null, phone: null })

    expect(isPurgedRecipient(purged)).toBe(true)
    expect(recipientNameLabel(purged)).toBe('(파기됨)')
    expect(isPurgedRecipient(recipient({ applicationId: null, name: null, email: null, phone: null }))).toBe(false)
    expect(recipientNameLabel(recipient())).toBe('김지원')
  })
})

describe('toTestResults', () => {
  it('이력 상세 수신자를 테스트 발송 응답과 같은 모양(수신자마다 메일 → SMS)으로 바꾼다', () => {
    const results = toTestResults(
      detail([
        recipient({ name: '김인사', mailStatus: 'SENT', smsStatus: 'FAILED', smsFailureReason: '9999' }),
        recipient({ id: 2, name: '이인사', mailStatus: 'REQUESTED', smsStatus: 'SKIPPED' }),
      ]),
    )

    expect(results).toEqual([
      { name: '김인사', channel: 'MAIL', status: 'SENT', failureReason: null },
      { name: '김인사', channel: 'SMS', status: 'FAILED', failureReason: '9999' },
      { name: '이인사', channel: 'MAIL', status: 'REQUESTED', failureReason: null },
      { name: '이인사', channel: 'SMS', status: 'SKIPPED', failureReason: 'NO_CONTACT' },
    ])
    expect(hasRequested(results)).toBe(true)
    expect(hasRequested(results.filter((result) => result.status !== 'REQUESTED'))).toBe(false)
  })
})

describe('defaultHistoryRange', () => {
  it('오늘을 포함한 최근 30일(시작일 = 오늘 - 29일)을 YYYY-MM-DD 로 준다', () => {
    expect(defaultHistoryRange(new Date(2026, 8, 19, 15, 30))).toEqual(['2026-08-21', '2026-09-19'])
    expect(defaultHistoryRange(new Date(2026, 2, 1))).toEqual(['2026-01-31', '2026-03-01'])
  })
})
