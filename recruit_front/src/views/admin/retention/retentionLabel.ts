import type { ForcedPurgeReason, PurgeTriggerType, RetentionScheduleRunResult } from '@/types/admin/retention'

/*
 * 파기 화면의 코드 → 한글 라벨 매핑. 화면 여러 곳이 같은 라벨을 써야 해서 한 곳에 모은다.
 */

/** 백엔드가 "파기 예정 없음"을 뜻할 때 쓰는 약속된 날짜. */
export const NO_SCHEDULE_DATE = '9999-12-31'

export interface RetentionTag {
  label: string
  color: string
}

export const formatNextPurgeDate = (date: string): string =>
  date === NO_SCHEDULE_DATE ? '예정 없음(파기 대상 없음)' : date

/**
 * 지원서별 판정 태그. 실제로 파기를 막는 것은 보류(RETENTION_HOLD)와 이미 파기된 건(ALREADY_PURGED)뿐이다.
 * APPLICATION_NOT_TERMINAL을 "진행 중"으로 따로 표시하는 것은 파기를 막기 위해서가 아니라,
 * 전형이 진행 중인 지원서도 함께 파기된다는 사실을 담당자에게 알리기 위해서다.
 * 그 밖의 사유(POLICY_NOT_FOUND·POLICY_CONFLICT·ANCHOR_NOT_FIXED·INVALID_STAGE_CONFIGURATION)가
 * "파기 가능"에 묶이는 것도 의도된 동작이다.
 */
export const eligibilityTag = (eligible: boolean, reasonCode: string | null): RetentionTag => {
  if (!eligible) {
    if (reasonCode === 'APPLICATION_NOT_TERMINAL') return { label: '진행 중', color: 'orange' }
    if (reasonCode === 'RETENTION_HOLD') return { label: '보류', color: 'red' }
    if (reasonCode === 'ALREADY_PURGED') return { label: '파기됨', color: 'default' }
  }
  return { label: '파기 가능', color: 'green' }
}

const RUN_RESULT_LABEL: Record<RetentionScheduleRunResult, string> = {
  SKIPPED_DISABLED: '실행 안 함(꺼짐)',
  SKIPPED_NOT_DUE: '실행 안 함(예정일 전)',
  NO_TARGET: '대상 없음',
  EXECUTED: '파기 실행',
  ERROR: '실패',
}

export const runResultLabel = (result: RetentionScheduleRunResult | null): string =>
  result === null ? '실행 이력 없음' : RUN_RESULT_LABEL[result]

const FORCED_PURGE_REASON_LABEL: Record<ForcedPurgeReason, string> = {
  DATA_SUBJECT_REQUEST: '본인 삭제 요청',
  DUPLICATE_ACCOUNT: '중복·오입력 계정 정리',
  OTHER: '기타',
}

export const forcedPurgeReasonLabel = (reasonCode: ForcedPurgeReason): string => FORCED_PURGE_REASON_LABEL[reasonCode]

const TRIGGER_LABEL: Record<PurgeTriggerType, string> = {
  RETENTION: '보존기간 만료',
  DATA_SUBJECT_REQUEST: '삭제 요청',
  FORCED_PURGE: '강제 파기',
}

/** 파기 이력 화면의 트리거 라벨. */
export const triggerLabel = (trigger: PurgeTriggerType): string => TRIGGER_LABEL[trigger]
