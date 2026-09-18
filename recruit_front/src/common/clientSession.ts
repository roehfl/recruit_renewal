const CLIENT_SESSION_KEY = 'recruit.clientSessionId'

let memorySessionId: string | null = null

function fallbackId(): string {
  return `evt-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`
}

export function createOpaqueId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }

  return fallbackId()
}

export function getClientSessionId(): string {
  try {
    const current = sessionStorage.getItem(CLIENT_SESSION_KEY)

    if (current) {
      return current
    }

    const next = createOpaqueId()
    sessionStorage.setItem(CLIENT_SESSION_KEY, next)
    return next
  } catch {
    // 사이트 데이터 차단 등으로 sessionStorage 접근이 SecurityError를 던지면 페이지 수명 동안 메모리 id를 쓴다.
    if (!memorySessionId) {
      memorySessionId = createOpaqueId()
    }

    return memorySessionId
  }
}