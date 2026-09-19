import { describe, expect, it } from 'vitest'

import type { StageListItem } from '@/types/admin/stage'
import type { AdminJobPostingListItem } from '@/types/jobPosting'
import {
  defaultStageId,
  isFinalResult,
  recipientResultTag,
  requiresStage,
  resultConditionTag,
  selectablePostings,
  toTargetQuery,
  type MessageCondition,
} from '../messageCondition'

const stage = (id: number, stageType: StageListItem['stageType'], status: StageListItem['status']): StageListItem => ({
  id,
  jobPostingId: 1,
  stageName: `전형${id}`,
  stageType,
  stageOrder: id,
  status,
  resultAnnouncementDateTime: null,
  finalStage: false,
})

const posting = (id: number, status: AdminJobPostingListItem['status'], accepting: boolean): AdminJobPostingListItem => ({
  id,
  title: `공고${id}`,
  postingType: 'PUBLIC',
  status,
  receptionStatus: accepting ? 'ACCEPTING' : 'CLOSED',
  accepting,
  receptionStartDateTime: '2026-09-01T09:00:00',
  receptionEndDateTime: '2026-09-22T18:00:00',
  positionCount: 1,
})

const base: MessageCondition = {
  jobPostingId: 1,
  stageId: 3,
  resultStatus: 'ALL',
  interviewGroup: 'ALL',
  applicationStatus: 'ALL',
}

describe('defaultStageId', () => {
  const stages = [
    stage(1, 'DOCUMENT', 'CLOSED'),
    stage(2, 'FIRST_INTERVIEW', 'RESULT_ANNOUNCED'),
    stage(3, 'SECOND_INTERVIEW', 'READY'),
  ]

  it('결과 발표는 발표된 전형 중 마지막 전형', () => {
    expect(defaultStageId('RESULT_ANNOUNCEMENT', stages)).toBe(2)
  })

  it('면접 안내는 첫 면접 전형', () => {
    expect(defaultStageId('INTERVIEW_NOTICE', stages)).toBe(2)
  })

  it('마감 임박·직접 입력은 전형을 고르지 않는다', () => {
    expect(defaultStageId('DEADLINE_REMINDER', stages)).toBeNull()
    expect(defaultStageId('FREE', stages)).toBeNull()
  })
})

describe('selectablePostings', () => {
  it('마감 임박은 게시 중이면서 접수 중인 공고만', () => {
    const postings = [posting(1, 'PUBLISHED', true), posting(2, 'PUBLISHED', false), posting(3, 'DRAFT', false)]
    expect(selectablePostings('DEADLINE_REMINDER', postings).map((item) => item.id)).toEqual([1])
    expect(selectablePostings('FREE', postings)).toHaveLength(3)
  })
})

describe('toTargetQuery', () => {
  it('공고가 없으면 조회하지 않는다', () => {
    expect(toTargetQuery('FREE', { ...base, jobPostingId: null })).toBeNull()
  })

  it('결과 발표·면접은 전형이 없으면 조회하지 않는다', () => {
    expect(toTargetQuery('RESULT_ANNOUNCEMENT', { ...base, stageId: null })).toBeNull()
    expect(toTargetQuery('INTERVIEW_SCHEDULE', { ...base, stageId: null })).toBeNull()
  })

  it('ALL 은 쿼리에서 뺀다', () => {
    expect(toTargetQuery('RESULT_ANNOUNCEMENT', base)).toEqual({ type: 'RESULT_ANNOUNCEMENT', jobPostingId: 1, stageId: 3 })
    expect(toTargetQuery('RESULT_ANNOUNCEMENT', { ...base, resultStatus: 'PASSED' })).toEqual({
      type: 'RESULT_ANNOUNCEMENT',
      jobPostingId: 1,
      stageId: 3,
      resultStatus: 'PASSED',
    })
  })

  it('면접은 조를, 마감 임박은 공고만 보낸다', () => {
    expect(toTargetQuery('INTERVIEW_NOTICE', { ...base, interviewGroup: '2' })).toEqual({
      type: 'INTERVIEW_NOTICE',
      jobPostingId: 1,
      stageId: 3,
      interviewGroup: '2',
    })
    expect(toTargetQuery('DEADLINE_REMINDER', { ...base, resultStatus: 'PASSED' })).toEqual({
      type: 'DEADLINE_REMINDER',
      jobPostingId: 1,
    })
  })

  it('직접 입력은 전형이 있을 때만 결과 조건을 보낸다', () => {
    expect(toTargetQuery('FREE', { ...base, stageId: null, resultStatus: 'PASSED', applicationStatus: 'SUBMITTED' })).toEqual({
      type: 'FREE',
      jobPostingId: 1,
      applicationStatus: 'SUBMITTED',
    })
    expect(toTargetQuery('FREE', { ...base, resultStatus: 'PASSED' })).toEqual({
      type: 'FREE',
      jobPostingId: 1,
      stageId: 3,
      resultStatus: 'PASSED',
    })
  })
})

describe('requiresStage', () => {
  it('결과 발표와 면접 안내만 전형이 필요하다', () => {
    expect(requiresStage('RESULT_ANNOUNCEMENT')).toBe(true)
    expect(requiresStage('INTERVIEW_SCHEDULE')).toBe(true)
    expect(requiresStage('INTERVIEW_NOTICE')).toBe(true)
    expect(requiresStage('DEADLINE_REMINDER')).toBe(false)
    expect(requiresStage('FREE')).toBe(false)
  })
})

describe('resultConditionTag', () => {
  it('결과 전체는 배지 없음', () => {
    expect(resultConditionTag('RESULT_ANNOUNCEMENT', base)).toBeNull()
  })

  it('결과 발표는 선택한 결과의 라벨·색', () => {
    expect(resultConditionTag('RESULT_ANNOUNCEMENT', { ...base, resultStatus: 'PASSED' })).toEqual({
      label: '합격',
      color: 'green',
    })
    expect(resultConditionTag('RESULT_ANNOUNCEMENT', { ...base, resultStatus: 'FAILED' })).toEqual({
      label: '불합격',
      color: 'red',
    })
  })

  it('면접·마감 임박은 결과 조건이 없다', () => {
    expect(resultConditionTag('INTERVIEW_NOTICE', { ...base, resultStatus: 'PASSED' })).toBeNull()
    expect(resultConditionTag('INTERVIEW_SCHEDULE', { ...base, resultStatus: 'PASSED' })).toBeNull()
    expect(resultConditionTag('DEADLINE_REMINDER', { ...base, resultStatus: 'PASSED' })).toBeNull()
  })

  it('직접 입력은 전형을 골랐을 때만 결과 배지', () => {
    expect(resultConditionTag('FREE', { ...base, stageId: null, resultStatus: 'PASSED' })).toBeNull()
    expect(resultConditionTag('FREE', { ...base, resultStatus: 'PASSED' })).toEqual({ label: '합격', color: 'green' })
  })
})

describe('recipientResultTag', () => {
  it('결과가 없으면 null', () => {
    expect(recipientResultTag(null)).toBeNull()
  })

  it('결과별 라벨·색', () => {
    expect(recipientResultTag('PASSED')).toEqual({ label: '합격', color: 'green' })
    expect(recipientResultTag('FAILED')).toEqual({ label: '불합격', color: 'red' })
    expect(recipientResultTag('HOLD')).toEqual({ label: '보류', color: 'blue' })
    expect(recipientResultTag('ABSENT')).toEqual({ label: '결시', color: 'default' })
  })
})

describe('isFinalResult', () => {
  it('합격·불합격만 최종 처리로 본다', () => {
    expect(isFinalResult('PASSED')).toBe(true)
    expect(isFinalResult('FAILED')).toBe(true)
    expect(isFinalResult('HOLD')).toBe(false)
    expect(isFinalResult('ABSENT')).toBe(false)
    expect(isFinalResult('ALL')).toBe(false)
  })
})
