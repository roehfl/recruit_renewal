import { logClientEvent } from '@/common/clientEventLogger'

export function installClientErrorHandlers(): void {
  window.addEventListener('error', (event) => {
    const stackSummary = sanitizeStack(event.error?.stack)

    logClientEvent({
      eventType: 'JS_ERROR',
      severity: 'ERROR',
      message: 'JS_RUNTIME_ERROR',
      stackSummary,
      stackHash: hashString(stackSummary),
      metadata: {
        file: sanitizeFilename(event.filename),
        line: event.lineno || null,
        column: event.colno || null,
      },
    })
  })

  window.addEventListener('unhandledrejection', (event) => {
    const reason = event.reason as { stack?: string } | undefined
    const stackSummary = sanitizeStack(reason?.stack)

    logClientEvent({
      eventType: 'UNHANDLED_REJECTION',
      severity: 'ERROR',
      message: 'UNHANDLED_PROMISE_REJECTION',
      stackSummary,
      stackHash: hashString(stackSummary),
      metadata: {
        reasonType: typeof event.reason,
      },
    })
  })
}

/**
 * 원칙:
 * - stack 전체 저장 금지
 * - 상위 6줄만 저장
 * - origin 제거
 * - query string / hash 제거
 * - localhost, 도메인, 절대 URL 제거
 * - 너무 긴 line 제거
 */
function sanitizeStack(stack: string | undefined): string | undefined {
  if (!stack) {
    return undefined
  }

  const lines = stack
    .split('\n')
    .slice(0, 6)
    .map(sanitizeStackLine)
    .filter((line) => line.length > 0)

  if (lines.length === 0) {
    return undefined
  }

  return lines.join('\n').slice(0, 1200)
}

function sanitizeStackLine(line: string): string {
  return line
    .replace(window.location.origin, '')
    .replace(/https?:\/\/[^/\s)]+/g, '')
    .replace(/[?#][^\s)]*/g, '')
    .replace(/\b(applicationId|jobPostingId|id)=\d+\b/gi, '$1=*')
    .replace(/\/applicant\/\d+\/form/g, '/applicant/*/form')
    .replace(/\/applicant\/\d+\/detail/g, '/applicant/*/detail')
    .replace(/\p{C}+/gu, ' ')
    .trim()
    .slice(0, 250)
}

function sanitizeFilename(filename: string | undefined): string | null {
  if (!filename) {
    return null
  }

  return filename
    .replace(window.location.origin, '')
    .replace(/https?:\/\/[^/\s)]+/g, '')
    .replace(/[?#].*$/, '')
    .replace(/\/applicant\/\d+\/form/g, '/applicant/*/form')
    .replace(/\/applicant\/\d+\/detail/g, '/applicant/*/detail')
    .slice(0, 200)
}

function hashString(value: string | undefined): string | undefined {
  if (!value) {
    return undefined
  }

  let hash = 2166136261

  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index)
    hash = Math.imul(hash, 16777619)
  }

  return (hash >>> 0).toString(16)
}