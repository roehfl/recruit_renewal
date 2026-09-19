import { describe, expect, it } from 'vitest'

import type { MessageContent, MessageTargetRecipient } from '@/types/admin/message'
import type { StageListItem } from '@/types/admin/stage'
import type { MessageCondition } from '../messageCondition'
import { buildSendSummary, describeCondition, isValidEmail, isValidPhone } from '../messageSendSummary'

const recipient = (
  applicationId: number,
  mailAvailable: boolean,
  smsAvailable: boolean,
  missingVariables: string[] = [],
): MessageTargetRecipient => ({
  applicationId,
  name: `지원자${applicationId}`,
  email: mailAvailable ? `u${applicationId}@example.com` : null,
  phone: smsAvailable ? '01000000000' : null,
  mailAvailable,
  smsAvailable,
  resultStatus: null,
  interviewGroup: null,
  interviewDateTime: null,
  draftStartedAt: null,
  variables: {},
  missingVariables,
})

const content = (overrides: Partial<MessageContent> = {}): MessageContent => ({
  templateId: null,
  mailEnabled: true,
  smsEnabled: true,
  mailSubject: '#{이름}님 안내',
  mailBody: '본문',
  smsBody: '문자',
  ...overrides,
})

const input = (overrides: Partial<Parameters<typeof buildSendSummary>[0]> = {}) => ({
  recipients: [recipient(1, true, true), recipient(2, true, false), recipient(3, false, false)],
  smsBytes: [50, 0, 0],
  content: content(),
  allowedKeys: ['이름', '공고명'],
  loading: false,
  error: '',
  ...overrides,
})

describe('buildSendSummary', () => {
  it('채널별 발송 건수와 연락처 없는 인원을 센다', () => {
    const summary = buildSendSummary(input())
    expect(summary).toMatchObject({
      recipientCount: 3,
      mailCount: 2,
      mailMissing: 1,
      smsCount: 1,
      lmsCount: 0,
      smsMissing: 2,
      blockReason: null,
    })
  })

  it('끈 채널은 0건으로 센다', () => {
    const summary = buildSendSummary(input({ content: content({ smsEnabled: false }) }))
    expect(summary.smsCount + summary.lmsCount + summary.smsMissing).toBe(0)
  })

  it('SMS 수신자별 byte로 SMS·LMS를 나누고 2000byte 초과면 막는다', () => {
    const recipients = [recipient(1, true, true), recipient(2, true, true)]
    expect(buildSendSummary(input({ recipients, smsBytes: [100, 60] }))).toMatchObject({ smsCount: 1, lmsCount: 1 })
    expect(buildSendSummary(input({ recipients, smsBytes: [100, 2500] })).blockReason).toBe(
      '2,000byte를 넘는 SMS 수신자가 있습니다.',
    )
  })

  it('값 없는 변수는 본문에 실제로 쓴 변수만 센다', () => {
    const recipients = [recipient(1, true, true, ['도착시각']), recipient(2, true, true, ['이름'])]
    expect(buildSendSummary(input({ recipients, smsBytes: [10, 10] })).missingVariableRecipients).toBe(1)
  })

  it('막는 이유는 불러오는 중 → 조회 실패 → 수신자 없음 → 채널 → 입력 → 변수 순서로 본다', () => {
    expect(buildSendSummary(input({ loading: true, error: 'x' })).blockReason).toBe('대상자를 불러오는 중입니다.')
    expect(buildSendSummary(input({ error: 'x' })).blockReason).toBe('대상자 조회에 실패했습니다. 다시 조회하세요.')
    expect(buildSendSummary(input({ recipients: [], smsBytes: [] })).blockReason).toBe('선택된 수신자가 없습니다.')
    expect(buildSendSummary(input({ content: content({ mailEnabled: false, smsEnabled: false }) })).blockReason).toBe(
      '메일이나 SMS 중 하나 이상 켜세요.',
    )
    expect(buildSendSummary(input({ content: content({ mailBody: ' ' }) })).blockReason).toBe('메일 제목과 본문을 입력하세요.')
    expect(buildSendSummary(input({ content: content({ smsBody: '' }) })).blockReason).toBe('SMS 내용을 입력하세요.')
    const unknown = buildSendSummary(input({ content: content({ mailBody: '#{면접일시}' }) }))
    expect(unknown.unknownKeys).toEqual(['면접일시'])
    expect(unknown.blockReason).toBe('이 종류에서 쓸 수 없는 변수가 있습니다.')
  })

  it('보낼 수 있는 연락처가 하나도 없으면 막는다', () => {
    const summary = buildSendSummary(input({ recipients: [recipient(3, false, false)], smsBytes: [0] }))
    expect(summary.blockReason).toBe('보낼 수 있는 연락처가 없습니다.')
  })
})

describe('describeCondition', () => {
  const stages: StageListItem[] = [
    {
      id: 1,
      jobPostingId: 1,
      stageName: '서류전형',
      stageType: 'DOCUMENT',
      stageOrder: 0,
      status: 'RESULT_ANNOUNCED',
      resultAnnouncementDateTime: null,
      finalStage: false,
    },
    {
      id: 2,
      jobPostingId: 1,
      stageName: '1차 면접',
      stageType: 'FIRST_INTERVIEW',
      stageOrder: 1,
      status: 'READY',
      resultAnnouncementDateTime: null,
      finalStage: false,
    },
  ]
  const base: MessageCondition = {
    jobPostingId: 1,
    stageId: null,
    resultStatus: 'ALL',
    interviewGroup: 'ALL',
    applicationStatus: 'SUBMITTED',
  }

  it('종류별 조건을 서버 조건 요약과 같은 문구로 만든다', () => {
    expect(describeCondition('RESULT_ANNOUNCEMENT', { ...base, stageId: 1, resultStatus: 'PASSED' }, stages)).toBe(
      '서류전형 · 합격',
    )
    expect(describeCondition('INTERVIEW_SCHEDULE', { ...base, stageId: 2, interviewGroup: '2' }, stages)).toBe(
      '1차 면접 · 2조',
    )
    expect(describeCondition('DEADLINE_REMINDER', base, stages)).toBe('작성 중 지원서')
    expect(describeCondition('FREE', base, stages)).toBe('제출 완료')
    expect(describeCondition('FREE', { ...base, stageId: 1, applicationStatus: 'ALL' }, stages)).toBe(
      '서류전형 · 결과 전체 · 작성 중+제출',
    )
  })
})

describe('연락처 형식', () => {
  it('서버 MessageContacts 와 같은 규칙으로 본다', () => {
    expect(isValidEmail(' hr.kim@example.com ')).toBe(true)
    expect(isValidEmail('a@b')).toBe(false)
    expect(isValidPhone('010-1234-5678')).toBe(true)
    expect(isValidPhone('02-1234-5678')).toBe(false)
  })
})
