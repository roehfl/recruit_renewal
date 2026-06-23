import type { AxiosError, AxiosRequestConfig } from 'axios'
import { logClientEvent } from '@/common/clientEventLogger'
import type { ClientEventContext, ClientEventMetadata, ClientEventType } from '@/types/clientEvent'

declare module 'axios' {
  export interface AxiosRequestConfig {
    skipClientEventLog?: boolean
    clientEventContext?: ClientEventContext
    requestStartedAt?: number
  }
}

export function logApiError(error: AxiosError): void {
  const config = error.config as AxiosRequestConfig | undefined

  if (!config || config.skipClientEventLog === true) {
    return
  }

  const status = error.response?.status
  const eventType = resolveEventType(error)
  const durationMs = resolveDurationMs(config)
  const relatedCorrelationId = readHeader(error, 'x-request-id')
  const context = config.clientEventContext ?? {}

  logClientEvent({
    eventType,
    severity: eventType === 'SESSION_EXPIRED' || eventType === 'FORBIDDEN' ? 'WARN' : 'ERROR',
    relatedCorrelationId,
    pageCode: context.pageCode,
    componentCode: context.componentCode,
    operation: context.operation,
    jobPostingId: context.jobPostingId,
    applicationId: context.applicationId,
    httpMethod: config.method?.toUpperCase(),
    apiPath: typeof config.url === 'string' ? config.url : undefined,
    httpStatus: status,
    errorCode: extractSafeErrorCode(error),
    message: resolveMessageCode(eventType),
    metadata: buildMetadata(eventType, error, durationMs),
  })
}

function resolveEventType(error: AxiosError): ClientEventType {
  if (error.code === 'ECONNABORTED') {
    return 'API_TIMEOUT'
  }

  if (!error.response) {
    return 'NETWORK_ERROR'
  }

  if (error.response.status === 401) {
    return 'SESSION_EXPIRED'
  }

  if (error.response.status === 403) {
    return 'FORBIDDEN'
  }

  return 'API_ERROR'
}

function resolveMessageCode(eventType: ClientEventType): string {
  switch (eventType) {
    case 'API_TIMEOUT':
      return 'API_TIMEOUT_OCCURRED'
    case 'NETWORK_ERROR':
      return 'NETWORK_ERROR_OCCURRED'
    case 'SESSION_EXPIRED':
      return 'SESSION_EXPIRED'
    case 'FORBIDDEN':
      return 'FORBIDDEN_ACCESS'
    default:
      return 'API_REQUEST_FAILED'
  }
}

function buildMetadata(
  eventType: ClientEventType,
  error: AxiosError,
  durationMs: number | undefined,
): ClientEventMetadata | undefined {
  if (eventType === 'API_TIMEOUT') {
    return {
      durationMs: durationMs ?? null,
      timeoutMs: typeof error.config?.timeout === 'number' ? error.config.timeout : null,
    }
  }

  if (eventType === 'NETWORK_ERROR') {
    return {
      durationMs: durationMs ?? null,
      axiosCode: error.code ?? null,
    }
  }

  if (eventType === 'API_ERROR') {
    return {
      durationMs: durationMs ?? null,
      retryable: isRetryable(error.response?.status),
      axiosCode: error.code ?? null,
    }
  }

  return undefined
}

function resolveDurationMs(config: AxiosRequestConfig): number | undefined {
  if (typeof config.requestStartedAt !== 'number') {
    return undefined
  }

  return Math.max(0, Math.round(performance.now() - config.requestStartedAt))
}

function isRetryable(status: number | undefined): boolean {
  if (!status) {
    return true
  }

  return status === 408 || status === 429 || status >= 500
}

function extractSafeErrorCode(error: AxiosError): string | undefined {
  const data = error.response?.data

  if (isRecord(data)) {
    const candidates = [
      data.errorCode,
      data.code,
      data.messageCode,
      data.status,
    ]

    const found = candidates.find((value) => typeof value === 'string' && /^[A-Z0-9_]{2,100}$/.test(value))

    if (typeof found === 'string') {
      return found.slice(0, 100)
    }
  }

  if (typeof error.code === 'string') {
    return error.code.slice(0, 100)
  }

  return undefined
}

function readHeader(error: AxiosError, name: string): string | undefined {
  const value = error.response?.headers?.[name]

  if (Array.isArray(value)) {
    return value[0]
  }

  return typeof value === 'string' ? value : undefined
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}