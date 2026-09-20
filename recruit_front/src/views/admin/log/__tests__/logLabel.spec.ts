import { describe, expect, it } from 'vitest'

import {
  ACTION_TYPE_OPTIONS,
  EVENT_TYPE_OPTIONS,
  TARGET_TYPE_OPTIONS,
  actionResultTag,
  actionTypeLabel,
  actorTypeLabel,
  eventTypeLabel,
  severityColor,
  targetTypeLabel,
} from '../logLabel'

describe('logLabel', () => {
  it('감사 행위를 한글로 준다', () => {
    expect(actionTypeLabel('EXPORT_APPLICATIONS')).toBe('지원현황 엑셀 반출')
    expect(actionTypeLabel('PURGE_FORCED')).toBe('강제 파기')
  })

  it('감사 대상 유형을 한글로 준다', () => {
    expect(targetTypeLabel('RETENTION_SCHEDULE')).toBe('파기 스케줄')
  })

  it('행위자 유형을 한글로 준다', () => {
    expect(actorTypeLabel('SYSTEM')).toBe('시스템')
  })

  it('감사 결과는 라벨과 태그 색을 함께 준다', () => {
    expect(actionResultTag('SUCCESS')).toEqual({ label: '성공', color: 'green' })
    expect(actionResultTag('CONFLICT')).toEqual({ label: '충돌', color: 'orange' })
    expect(actionResultTag('SKIPPED')).toEqual({ label: '건너뜀', color: 'default' })
  })

  it('이벤트 유형을 한글로 준다', () => {
    expect(eventTypeLabel('APPLICATION_DRAFT_SAVE_FAILED')).toBe('임시저장 실패')
  })

  it('심각도별 태그 색을 준다', () => {
    expect(severityColor('ERROR')).toBe('red')
    expect(severityColor('WARN')).toBe('orange')
    expect(severityColor('INFO')).toBe('blue')
  })

  it('select 옵션은 코드 전부를 담는다', () => {
    expect(ACTION_TYPE_OPTIONS).toHaveLength(21)
    expect(TARGET_TYPE_OPTIONS).toHaveLength(11)
    expect(EVENT_TYPE_OPTIONS).toHaveLength(14)
  })

  it('select 옵션은 value·label 모양이다', () => {
    expect(EVENT_TYPE_OPTIONS[0]).toEqual({ value: 'PAGE_OPENED', label: '화면 진입' })
  })
})
