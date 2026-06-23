export type ClientEventSource = 'APPLICANT_WEB'

export type ClientEventSeverity = 'INFO' | 'WARN' | 'ERROR'

export type ClientEventType =
  | 'PAGE_OPENED'
  | 'CHECKPOINT'
  | 'API_ERROR'
  | 'API_TIMEOUT'
  | 'NETWORK_ERROR'
  | 'SESSION_EXPIRED'
  | 'FORBIDDEN'
  | 'JS_ERROR'
  | 'UNHANDLED_REJECTION'
  | 'APPLICATION_DRAFT_SAVE_FAILED'
  | 'APPLICATION_SUBMIT_CLICKED'
  | 'APPLICATION_SUBMIT_FAILED'
  | 'ATTACHMENT_UPLOAD_FAILED'
  | 'CLIENT_VALIDATION_FAILED'

export type ClientEventMetadataValue = string | number | boolean | null

export type ClientEventMetadata = Record<string, ClientEventMetadataValue>

export interface ClientEventPayload {
  eventType: ClientEventType
  severity: ClientEventSeverity
  source: ClientEventSource
  clientSessionId: string
  clientEventId: string
  clientOccurredAt: string

  relatedCorrelationId?: string
  pageCode?: string
  componentCode?: string
  routePath?: string
  operation?: string

  jobPostingId?: number
  applicationId?: number

  httpMethod?: string
  apiPath?: string
  httpStatus?: number
  errorCode?: string
  message?: string
  stackHash?: string
  stackSummary?: string

  frontendVersion?: string
  browserName?: string
  browserVersion?: string
  osName?: string
  viewport?: string
  timezone?: string

  metadata?: ClientEventMetadata
}

export interface ClientEventIngestResponse {
  accepted: boolean
  duplicate: boolean
  id?: number | null
}

export interface ClientEventContext {
  pageCode?: string
  componentCode?: string
  operation?: string
  jobPostingId?: number
  applicationId?: number
}