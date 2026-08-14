const CLIENT_SESSION_KEY = 'recruit.clientSessionId'

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
  const current = sessionStorage.getItem(CLIENT_SESSION_KEY)

  if (current) {
    return current
  }

  const next = createOpaqueId()
  sessionStorage.setItem(CLIENT_SESSION_KEY, next)
  return next
}