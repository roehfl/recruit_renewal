import { describe, expect, it } from 'vitest'

import { NO_SCHEDULE_DATE, eligibilityTag, formatNextPurgeDate, forcedPurgeReasonLabel, runResultLabel, triggerLabel } from '../retentionLabel'

describe('retentionLabel', () => {
  it('파기 예정이 없으면 날짜 대신 안내 문구를 준다', () => {
    expect(formatNextPurgeDate(NO_SCHEDULE_DATE)).toBe('예정 없음(파기 대상 없음)')
  })

  it('예정일이 있으면 날짜를 그대로 보여 준다', () => {
    expect(formatNextPurgeDate('2029-03-09')).toBe('2029-03-09')
  })

  it('진행 중 지원서는 경고 태그로 표시한다', () => {
    expect(eligibilityTag(false, 'APPLICATION_NOT_TERMINAL')).toEqual({ label: '진행 중', color: 'orange' })
  })

  it('보류는 빨간 태그로 표시한다', () => {
    expect(eligibilityTag(false, 'RETENTION_HOLD')).toEqual({ label: '보류', color: 'red' })
  })

  it('이미 파기된 건은 회색 태그로 표시한다', () => {
    expect(eligibilityTag(false, 'ALREADY_PURGED')).toEqual({ label: '파기됨', color: 'default' })
  })

  it('보존기간 미도래는 강제 파기 대상이므로 파기 가능으로 본다', () => {
    expect(eligibilityTag(false, 'RETENTION_NOT_DUE')).toEqual({ label: '파기 가능', color: 'green' })
  })

  it('적격 건은 파기 가능으로 표시한다', () => {
    expect(eligibilityTag(true, null)).toEqual({ label: '파기 가능', color: 'green' })
  })

  it('실행 결과 라벨을 한글로 준다', () => {
    expect(runResultLabel('SKIPPED_NOT_DUE')).toBe('실행 안 함(예정일 전)')
    expect(runResultLabel('ERROR')).toBe('실패')
    expect(runResultLabel(null)).toBe('실행 이력 없음')
  })

  it('사유 라벨을 한글로 준다', () => {
    expect(forcedPurgeReasonLabel('DATA_SUBJECT_REQUEST')).toBe('본인 삭제 요청')
  })
})

describe('triggerLabel', () => {
  it('파기 배치 트리거를 한글로 준다', () => {
    expect(triggerLabel('RETENTION')).toBe('보존기간 만료')
    expect(triggerLabel('DATA_SUBJECT_REQUEST')).toBe('삭제 요청')
    expect(triggerLabel('FORCED_PURGE')).toBe('강제 파기')
  })
})
