import type { ClientEventSeverity, ClientEventSource, ClientEventType } from '@/types/clientEvent'

/**
 * GET /api/admin/client-events 응답 1행. 백엔드 ClientEventLogResponse 와 1:1이다.
 * ipAddress·userAgent·principalHash·principalType 은 ROLE_PRIVACY_ADMIN 이 아니면
 * 서버가 "***" 로 바꿔 보낸다(원래 null 이면 null). 프론트는 판정하지 않고 받은 값을 그대로 쓴다.
 */
export interface ClientEventLogResponse {
  id: number
  receivedAt: string
  clientOccurredAt: string | null
  eventType: ClientEventType
  severity: ClientEventSeverity
  source: ClientEventSource
  clientSessionId: string
  clientEventId: string
  ingestCorrelationId: string | null
  relatedCorrelationId: string | null
  pageCode: string | null
  componentCode: string | null
  routePath: string | null
  operation: string | null
  jobPostingId: number | null
  applicationId: number | null
  httpMethod: string | null
  apiPath: string | null
  httpStatus: number | null
  errorCode: string | null
  message: string | null
  stackHash: string | null
  stackSummary: string | null
  frontendVersion: string | null
  browserName: string | null
  browserVersion: string | null
  osName: string | null
  viewport: string | null
  timezone: string | null
  ipAddress: string | null
  userAgent: string | null
  principalHash: string | null
  principalType: 'APPLICANT' | 'EMPLOYEE' | null
  /** metadata 를 직렬화한 JSON 문자열 그대로. 객체가 아니다. */
  metadataJson: string | null
}

/** GET /api/admin/client-events 쿼리. from·to 는 ISO date-time(yyyy-MM-ddTHH:mm:ss). */
export interface ClientEventLogQuery {
  eventType?: ClientEventType
  severity?: ClientEventSeverity
  applicationId?: number
  jobPostingId?: number
  clientSessionId?: string
  relatedCorrelationId?: string
  from?: string
  to?: string
  page: number
  size: number
}
