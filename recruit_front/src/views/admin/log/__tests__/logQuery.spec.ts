import { describe, expect, it } from 'vitest'

import { MAX_RANGE_DAYS, presetRange, rangeError, toDateTimeRange } from '../logQuery'

describe('logQuery', () => {
  it('1일 프리셋은 종료일 하루만 고른다', () => {
    expect(presetRange(1, new Date(2026, 8, 20))).toEqual(['2026-09-20', '2026-09-20'])
  })

  it('7일 프리셋은 종료일을 포함해 7일이다', () => {
    expect(presetRange(7, new Date(2026, 8, 20))).toEqual(['2026-09-14', '2026-09-20'])
  })

  it('30일 프리셋은 종료일을 포함해 30일이다', () => {
    expect(presetRange(30, new Date(2026, 8, 20))).toEqual(['2026-08-22', '2026-09-20'])
  })

  it('90일 프리셋은 종료일을 포함해 90일이다(차이 89일)', () => {
    expect(presetRange(MAX_RANGE_DAYS, new Date(2026, 8, 20))).toEqual(['2026-06-23', '2026-09-20'])
  })

  it('월을 넘어가도 날짜를 정확히 센다', () => {
    expect(presetRange(7, new Date(2026, 0, 3))).toEqual(['2025-12-28', '2026-01-03'])
  })

  it('시작일이 종료일보다 늦으면 오류 메시지를 준다', () => {
    expect(rangeError('2026-09-21', '2026-09-20')).toBe('시작일이 종료일보다 늦습니다.')
  })

  it('포함 일수 90일까지는 통과한다', () => {
    expect(rangeError('2026-06-23', '2026-09-20')).toBeNull()
  })

  it('포함 일수 91일이면 오류 메시지를 준다', () => {
    expect(rangeError('2026-06-22', '2026-09-20')).toBe('조회 기간은 시작일·종료일 포함 90일까지입니다.')
  })

  it('같은 날이면 통과한다', () => {
    expect(rangeError('2026-09-20', '2026-09-20')).toBeNull()
  })

  it('날짜를 서버가 받는 ISO date-time 으로 바꾼다', () => {
    expect(toDateTimeRange(['2026-09-14', '2026-09-20'])).toEqual({
      from: '2026-09-14T00:00:00',
      to: '2026-09-20T23:59:59',
    })
  })
})
