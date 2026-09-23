import { describe, expect, it } from 'vitest'

import { ALL_MESSAGE_TYPES, MESSAGE_TYPES, isSystemMessageType, messageTypeLabel } from '../messageTypes'

describe('messageTypes', () => {
  it('발송 화면 종류에는 시스템 자동발송이 없고 전체 종류에는 있다', () => {
    expect(MESSAGE_TYPES.map((meta) => meta.type)).toEqual([
      'RESULT_ANNOUNCEMENT',
      'DEADLINE_REMINDER',
      'INTERVIEW_SCHEDULE',
      'INTERVIEW_NOTICE',
      'FREE',
    ])
    expect(ALL_MESSAGE_TYPES.map((meta) => meta.type).slice(5)).toEqual([
      'SIGNUP_VERIFICATION',
      'PASSWORD_RESET',
      'APPLICATION_SUBMITTED',
    ])
  })

  it('시스템 자동발송 종류를 구분하고 라벨을 붙인다', () => {
    expect(isSystemMessageType('PASSWORD_RESET')).toBe(true)
    expect(isSystemMessageType('FREE')).toBe(false)
    expect(messageTypeLabel('APPLICATION_SUBMITTED')).toBe('시스템 자동발송 · 지원서 제출 완료')
    expect(messageTypeLabel('FREE')).toBe('기타 · 직접 입력')
  })
})
