export type AuditActorType = 'EMPLOYEE' | 'SYSTEM' | 'APPLICANT' | 'ANONYMOUS'

export type AuditActionResult = 'SUCCESS' | 'FAILURE' | 'DENIED' | 'SKIPPED' | 'CONFLICT'

export type AuditActionType =
  | 'EXPORT_APPLICATIONS'
  | 'EXPORT_STAGE_RESULTS'
  | 'EXPORT_INTERVIEWS'
  | 'EXPORT_EVALUATIONS'
  | 'EXPORT_STAGE_RESULT_TEMPLATE'
  | 'APPLICATION_PDF'
  | 'STAGE_RESULT_UPLOAD'
  | 'STAGE_RESULT_CORRECT'
  | 'STAGE_RESULT_ANNOUNCE'
  | 'STAGE_RESULT_CONFIRM'
  | 'EVALUATION_REOPEN'
  | 'ATTACHMENT_ADMIN_DOWNLOAD'
  | 'ATTACHMENT_ADMIN_DELETE'
  | 'RETENTION_POLICY_UPDATE'
  | 'RETENTION_HOLD_SET'
  | 'RETENTION_HOLD_RELEASE'
  | 'RETENTION_ANCHOR_SET'
  | 'PURGE_SCAN'
  | 'PURGE_EXECUTE'
  | 'PURGE_RECONCILE'
  | 'PURGE_FORCED'

export type AuditTargetType =
  | 'STAGE_RESULT'
  | 'JOB_APPLICATION'
  | 'APPLICATION_ATTACHMENT'
  | 'INTERVIEW_EVALUATION'
  | 'EXPORT_DATASET'
  | 'APPLICATION_PDF'
  | 'RETENTION_POLICY'
  | 'RETENTION_HOLD'
  | 'JOB_POSTING'
  | 'PURGE_BATCH'
  | 'RETENTION_SCHEDULE'

/**
 * GET /api/admin/audit/activities 응답 1행. 백엔드 AuditActivityResponse 와 1:1이다.
 * ipAddress·userAgent 는 ROLE_PRIVACY_ADMIN 이 아니면 서버가 "***" 로 바꿔 보낸다.
 * reasonCode 는 AuditReasonCode enum 이름이지만, 값이 늘어나도 화면이 깨지지 않게 string 으로 받는다.
 */
export interface AuditActivityResponse {
  id: number
  occurredAt: string
  actorType: AuditActorType
  actorId: string | null
  actorRoleSnapshot: string | null
  actionType: AuditActionType
  actionResult: AuditActionResult
  targetType: AuditTargetType
  targetId: string | null
  jobPostingId: number | null
  applicationId: number | null
  applicantRefHash: string | null
  reasonCode: string | null
  reasonMessage: string | null
  correlationId: string | null
  ipAddress: string | null
  userAgent: string | null
  metadataJson: string | null
}

/** GET /api/admin/audit/activities 쿼리. from·to 는 ISO date-time(yyyy-MM-ddTHH:mm:ss). */
export interface AuditActivityQuery {
  actorId?: string
  actionType?: AuditActionType
  actionResult?: AuditActionResult
  targetType?: AuditTargetType
  jobPostingId?: number
  applicationId?: number
  from?: string
  to?: string
  page: number
  size: number
}
