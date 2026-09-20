/*
 * 개인정보 파기(retention) 타입. 백엔드 RetentionPolicyResponse / RetentionScheduleResponse /
 * DataSubjectSummaryResponse / DataSubjectDetailResponse / PurgeBatchResponse / PurgeBatchDetailResponse /
 * PurgeJobItemResponse 와 요청 DTO RetentionPolicyRequest / RetentionScheduleRequest / ForcedPurgeRequest 와
 * 대응한다.
 */

export type RetentionBaselineType = 'HIRING_ENDED_AT' | 'CLOSED_AT'

/** 보존 정책. jobPostingId 가 null 이면 전역 기본 정책. */
export interface RetentionPolicy {
  id: number
  jobPostingId: number | null
  retentionPeriodDays: number
  baselineType: RetentionBaselineType
  enabled: boolean
  effectiveFrom: string | null
  effectiveTo: string | null
}

/** 등록·수정(전체 교체) 공용. */
export interface RetentionPolicySaveRequest {
  jobPostingId: number | null
  retentionPeriodDays: number
  baselineType: RetentionBaselineType
  enabled: boolean
  effectiveFrom: string | null
  effectiveTo: string | null
}

/** 자동 파기 스케줄 1회 기동의 결과. */
export type RetentionScheduleRunResult = 'SKIPPED_DISABLED' | 'SKIPPED_NOT_DUE' | 'NO_TARGET' | 'EXECUTED' | 'ERROR'

/** 자동 파기 설정. nextPurgeDate 가 '9999-12-31' 이면 파기 예정이 없다는 뜻이다. */
export interface RetentionSchedule {
  enabled: boolean
  nextPurgeDate: string
  hasPolicy: boolean
  retentionPeriodDays: number | null
  lastRunAt: string | null
  lastRunResult: RetentionScheduleRunResult | null
  lastRunBatchId: number | null
}

/** 파기 대상자 검색 조건. 값이 없는 키는 조건 없음이며 최소 1개는 있어야 한다(서버 검증). */
export interface DataSubjectQuery {
  name?: string
  phoneNumber?: string
  email?: string
}

/** 파기 대상자 검색 결과 행. 연락처는 원문(관리자 화면 규칙에 따라 마스킹하지 않는다). */
export interface DataSubjectSummary {
  applicantId: number
  name: string | null
  email: string | null
  phoneNumber: string | null
  applicationCount: number
  purgedApplicationCount: number
  lastAppliedAt: string | null
  hasActiveHold: boolean
}

/** 대상자 상세의 지원서 1건과 적격성 판정. hold 사유 원문은 주지 않는다. */
export interface DataSubjectApplication {
  applicationId: number
  jobPostingTitle: string
  status: string
  submittedAt: string | null
  purgeResult: string | null
  eligible: boolean
  reasonCode: string | null
}

/** 파기 대상자 상세 = 지원자 정보 + 지원서별 적격성 판정 목록. */
export interface DataSubjectDetail {
  applicantId: number
  name: string | null
  email: string | null
  phoneNumber: string | null
  hasActiveHold: boolean
  applications: DataSubjectApplication[]
}

/** 강제 파기 사유. 자유 텍스트는 받지 않는다. */
export type ForcedPurgeReason = 'DATA_SUBJECT_REQUEST' | 'DUPLICATE_ACCOUNT' | 'OTHER'

/** 강제 파기 요청. confirm 은 비가역 파기의 명시적 확인. */
export interface ForcedPurgeRequest {
  applicantId: number
  reasonCode: ForcedPurgeReason
  confirm: boolean
}

export type PurgeBatchMode = 'DRY_RUN' | 'EXECUTE'
export type PurgeBatchStatus = 'RUNNING' | 'COMPLETED' | 'PARTIAL_FAILED' | 'FAILED'
export type PurgeTriggerType = 'RETENTION' | 'DATA_SUBJECT_REQUEST' | 'FORCED_PURGE'
export type PurgeItemStatus = 'ELIGIBLE' | 'SKIPPED' | 'PENDING' | 'PURGED' | 'FAILED'

/** 파기 배치(산정/실행) 1건의 집계. */
export interface PurgeBatchSummary {
  id: number
  mode: PurgeBatchMode
  status: PurgeBatchStatus
  triggerType: PurgeTriggerType
  scanAt: string
  startedAt: string
  completedAt: string | null
  requestedBy: string
  sourceDryRunBatchId: number | null
  totalCount: number
  eligibleCount: number
  skippedCount: number
  policyConflictCount: number
  purgedCount: number
  pendingCount: number
  failedCount: number
  binaryDeleteFailedCount: number
}

/** 파기 배치의 지원서 1건 처리 결과. */
export interface PurgeBatchItem {
  id: number
  applicationId: number
  jobPostingId: number | null
  status: PurgeItemStatus
  reasonCode: string | null
}

/** 파기 배치 상세 = 배치 집계 + 지원서별 처리 결과. */
export interface PurgeBatchDetail {
  batch: PurgeBatchSummary
  items: PurgeBatchItem[]
}
