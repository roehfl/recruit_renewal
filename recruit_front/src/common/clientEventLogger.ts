import { clientEventApi } from '@/api/clientEventApi'
import { createOpaqueId, getClientSessionId } from '@/common/clientSession'
import type {
  ClientEventMetadata,
  ClientEventPayload,
  ClientEventSeverity,
  ClientEventType,
} from '@/types/clientEvent'

const SAFE_MESSAGE_CODE = /^[A-Z][A-Z0-9_]{2,80}$/

type LogClientEventInput = Partial<ClientEventPayload> & {
  eventType: ClientEventType
  severity: ClientEventSeverity
}

export function logClientEvent(input: LogClientEventInput): void {
  const payload = buildPayload(input)

  void clientEventApi.record(payload).catch(() => {
    // best-effort: 사용자 업무 흐름 방해 금지, telemetry 실패 재로깅 금지
  })
}

export function buildPayload(input: LogClientEventInput): ClientEventPayload {
  const payload: ClientEventPayload = {
    eventType: input.eventType,
    severity: input.severity,
    source: 'APPLICANT_WEB',
    clientSessionId: getClientSessionId(),
    clientEventId: createOpaqueId(),
    clientOccurredAt: toLocalDateTimeString(new Date()),
    routePath: stripQuery(input.routePath ?? window.location.pathname),
    frontendVersion: safeText(import.meta.env.VITE_APP_VERSION, 80),
    viewport: `${window.innerWidth}x${window.innerHeight}`,
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    ...sanitizeInput(input),
  }

  return removeUndefined(payload)
}

function sanitizeInput(input: LogClientEventInput): Partial<ClientEventPayload> {
  return {
    relatedCorrelationId: safeText(input.relatedCorrelationId, 100),
    pageCode: safeText(input.pageCode, 80),
    componentCode: safeText(input.componentCode, 80),
    routePath: stripQuery(input.routePath),
    operation: safeText(input.operation, 80),
    jobPostingId: input.jobPostingId,
    applicationId: input.applicationId,
    httpMethod: safeText(input.httpMethod?.toUpperCase(), 10),
    apiPath: stripQuery(input.apiPath),
    httpStatus: input.httpStatus,
    errorCode: safeText(input.errorCode, 100),
    message: safeMessageCode(input.message),
    stackHash: safeText(input.stackHash, 128),
    stackSummary: safeText(input.stackSummary, 2000),
    browserName: safeText(input.browserName ?? detectBrowserName(), 80),
    browserVersion: safeText(input.browserVersion ?? detectBrowserVersion(), 80),
    osName: safeText(input.osName ?? detectOsName(), 80),
    metadata: sanitizeMetadata(input.metadata),
  }
}

function safeMessageCode(value: string | undefined): string | undefined {
  if (!value) {
    return undefined
  }

  const normalized = value.trim().toUpperCase()

  if (SAFE_MESSAGE_CODE.test(normalized)) {
    return normalized
  }

  return 'CLIENT_EVENT_RECORDED'
}

function sanitizeMetadata(metadata: ClientEventMetadata | undefined): ClientEventMetadata | undefined {
  if (!metadata) {
    return undefined
  }

  const result: ClientEventMetadata = {}

  for (const [key, value] of Object.entries(metadata).slice(0, 20)) {
    const safeKey = key.slice(0, 50).trim()

    if (!safeKey) {
      continue
    }

    if (typeof value === 'string') {
      result[safeKey] = safeText(value, 200) ?? null
      continue
    }

    result[safeKey] = value
  }

  return Object.keys(result).length > 0 ? result : undefined
}

function safeText(value: string | undefined, maxLength: number): string | undefined {
  if (!value) {
    return undefined
  }

  const next = value.replace(/\p{C}+/gu, ' ').trim()

  if (!next) {
    return undefined
  }

  return next.length > maxLength ? next.slice(0, maxLength) : next
}

function stripQuery(value: string | undefined): string | undefined {
  if (!value) {
    return undefined
  }

  const path = value.split('?')[0]

  if (!path) {
    return undefined
  }

  return path.slice(0, 300)
}

function toLocalDateTimeString(date: Date): string {
  const localTime = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return localTime.toISOString().slice(0, 23)
}

function removeUndefined<T extends object>(value: T): T {
  const result: Partial<T> = {}

  for (const key of Object.keys(value) as Array<keyof T>) {
    const entryValue = value[key]

    if (entryValue !== undefined) {
      result[key] = entryValue
    }
  }

  return result as T
}

function detectBrowserName(): string | undefined {
  const ua = navigator.userAgent

  if (ua.includes('Edg/')) return 'Edge'
  if (ua.includes('Chrome/')) return 'Chrome'
  if (ua.includes('Safari/') && !ua.includes('Chrome/')) return 'Safari'
  if (ua.includes('Firefox/')) return 'Firefox'

  return undefined
}

function detectBrowserVersion(): string | undefined {
  const ua = navigator.userAgent
  const match = ua.match(/(Edg|Chrome|Firefox|Version)\/([0-9.]+)/)

  return match?.[2]
}

function detectOsName(): string | undefined {
  const ua = navigator.userAgent

  if (ua.includes('Windows')) return 'Windows'
  if (ua.includes('Mac OS')) return 'macOS'
  if (ua.includes('Android')) return 'Android'
  if (ua.includes('iPhone') || ua.includes('iPad')) return 'iOS'
  if (ua.includes('Linux')) return 'Linux'

  return undefined
}