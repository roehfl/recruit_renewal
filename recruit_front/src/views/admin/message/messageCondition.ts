import type { MessageTargetQuery, MessageType } from '@/types/admin/message'
import { STAGE_RESULT_STATUS_LABELS, STAGE_STATUS_LABELS } from '@/types/admin/stage'
import type { StageListItem, StageResultStatus, StageStatus } from '@/types/admin/stage'
import type { AdminJobPostingListItem } from '@/types/jobPosting'

export type ResultFilter = StageResultStatus | 'ALL'
export type ApplicationFilter = 'SUBMITTED' | 'DRAFT' | 'ALL'

/** 발송 화면 조건 바의 상태. 'ALL' 은 조건 없음이며 조회 쿼리에서 빠진다. */
export interface MessageCondition {
  jobPostingId: number | null
  stageId: number | null
  resultStatus: ResultFilter
  interviewGroup: string
  applicationStatus: ApplicationFilter
}

/** `@/types/admin/stage`의 라벨과 같다(백엔드 기준 단일 출처). */
export const RESULT_LABEL: Record<StageResultStatus, string> = STAGE_RESULT_STATUS_LABELS
export const STAGE_STATUS_LABEL: Record<StageStatus, string> = STAGE_STATUS_LABELS

export const RESULT_FILTER_OPTIONS: { value: ResultFilter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'PASSED', label: RESULT_LABEL.PASSED },
  { value: 'FAILED', label: RESULT_LABEL.FAILED },
  { value: 'HOLD', label: RESULT_LABEL.HOLD },
  { value: 'ABSENT', label: RESULT_LABEL.ABSENT },
]

export const APPLICATION_FILTER_OPTIONS: { value: ApplicationFilter; label: string }[] = [
  { value: 'SUBMITTED', label: '제출 완료' },
  { value: 'DRAFT', label: '작성 중' },
  { value: 'ALL', label: '전체' },
]

const INTERVIEW_STAGE_TYPES: StageListItem['stageType'][] = ['FIRST_INTERVIEW', 'SECOND_INTERVIEW', 'FINAL_INTERVIEW']

export const isInterviewType = (type: MessageType): boolean =>
  type === 'INTERVIEW_SCHEDULE' || type === 'INTERVIEW_NOTICE'

/** 숫자만인 조 이름은 뒤에 "조"를 붙여 보여 준다(서버 #{조} 변수와 같은 규칙). */
export const interviewGroupLabel = (group: string | null): string => {
  if (!group) return ''
  return /^\d+$/.test(group) ? `${group}조` : group
}

export const isAnnouncedStage = (stage: StageListItem): boolean =>
  stage.status === 'RESULT_ANNOUNCED' || stage.status === 'CLOSED'

export const isInterviewStage = (stage: StageListItem): boolean => INTERVIEW_STAGE_TYPES.includes(stage.stageType)

/** 서류 마감 임박은 게시 중이면서 접수 중인 공고만 고를 수 있다. */
export const selectablePostings = (type: MessageType, postings: AdminJobPostingListItem[]): AdminJobPostingListItem[] =>
  type === 'DEADLINE_REMINDER' ? postings.filter((posting) => posting.status === 'PUBLISHED' && posting.accepting) : postings

/** 종류·공고를 바꿨을 때의 기본 전형. 전형 목록은 stageOrder 오름차순이다. */
export const defaultStageId = (type: MessageType, stages: StageListItem[]): number | null => {
  if (type === 'RESULT_ANNOUNCEMENT') {
    const announced = stages.filter(isAnnouncedStage)
    return announced[announced.length - 1]?.id ?? null
  }
  if (isInterviewType(type)) {
    return stages.find(isInterviewStage)?.id ?? null
  }
  return null
}

/** 조회에 필요한 조건이 다 있으면 API 쿼리로 바꾸고, 모자라면 null(조회하지 않음). */
export const toTargetQuery = (type: MessageType, condition: MessageCondition): MessageTargetQuery | null => {
  if (condition.jobPostingId === null) {
    return null
  }
  const query: MessageTargetQuery = { type, jobPostingId: condition.jobPostingId }
  if (type === 'RESULT_ANNOUNCEMENT' || isInterviewType(type)) {
    if (condition.stageId === null) {
      return null
    }
    query.stageId = condition.stageId
  }
  if (type === 'RESULT_ANNOUNCEMENT' && condition.resultStatus !== 'ALL') {
    query.resultStatus = condition.resultStatus
  }
  if (isInterviewType(type) && condition.interviewGroup !== 'ALL') {
    query.interviewGroup = condition.interviewGroup
  }
  if (type === 'FREE') {
    if (condition.stageId !== null) {
      query.stageId = condition.stageId
      if (condition.resultStatus !== 'ALL') {
        query.resultStatus = condition.resultStatus
      }
    }
    if (condition.applicationStatus !== 'ALL') {
      query.applicationStatus = condition.applicationStatus
    }
  }
  return query
}
