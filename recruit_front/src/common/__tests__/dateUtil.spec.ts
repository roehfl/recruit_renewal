import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { getDDay, isDeadlineSoon } from '@/common/dateUtil'

describe('getDDay', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    // 현재: 2026-09-18 18:00 (로컬)
    vi.setSystemTime(new Date(2026, 8, 18, 18, 0, 0))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  /*
   * 경과 시간(24시간 단위 floor)으로 계산하면 마감 시각이 현재 시각보다 이른 경우
   * 달력 기준보다 하루 적게 나왔다. 날짜 기준으로 계산한다.
   */
  it('마감이 내일이면 남은 시간이 24시간 미만이어도 D-1이다', () => {
    expect(getDDay('2026-09-19T10:00:00')).toBe('D-1')
  })

  it('마감일까지 달력 기준 일수를 표시한다', () => {
    expect(getDDay('2026-09-21T09:00:00')).toBe('D-3')
  })

  it('오늘 마감이고 마감 시각이 지나지 않았으면 D-DAY다', () => {
    expect(getDDay('2026-09-18T23:59:00')).toBe('D-DAY')
  })

  it('마감 시각이 지났으면 오늘이어도 마감이다', () => {
    expect(getDDay('2026-09-18T17:00:00')).toBe('마감')
    expect(getDDay('2026-09-17T23:59:00')).toBe('마감')
  })

  it('값이 없으면 빈 문자열이다', () => {
    expect(getDDay(null)).toBe('')
  })

  it('isDeadlineSoon은 D-7까지 참이다', () => {
    expect(isDeadlineSoon('2026-09-18T23:59:00')).toBe(true)
    expect(isDeadlineSoon('2026-09-25T09:00:00')).toBe(true)
    expect(isDeadlineSoon('2026-09-26T09:00:00')).toBe(false)
    expect(isDeadlineSoon('2026-09-18T17:00:00')).toBe(false)
  })
})
