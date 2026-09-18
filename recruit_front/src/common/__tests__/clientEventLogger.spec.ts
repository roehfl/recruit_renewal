import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { buildPayload, logClientEvent } from '@/common/clientEventLogger'

const recordMock = vi.fn()

vi.mock('@/api/clientEventApi', () => ({
  clientEventApi: {
    record: (...args: unknown[]) => recordMock(...args),
  },
}))

describe('clientEventLogger', () => {
  beforeEach(() => {
    recordMock.mockReset()
    recordMock.mockResolvedValue({})
    window.history.pushState({}, '', '/applicant/recruit?keyword=test')
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  /*
   * sanitizeInput의 routePath(undefined)가 현재 경로 기본값을 덮어써
   * 자동 수집 이벤트에 routePath가 항상 빠졌다.
   */
  it('routePath를 넘기지 않으면 쿼리를 뺀 현재 경로를 담는다', () => {
    const payload = buildPayload({ eventType: 'JS_ERROR', severity: 'ERROR' })

    expect(payload.routePath).toBe('/applicant/recruit')
  })

  it('routePath를 넘기면 쿼리를 뺀 그 값을 담는다', () => {
    const payload = buildPayload({ eventType: 'JS_ERROR', severity: 'ERROR', routePath: '/admin/home?tab=1' })

    expect(payload.routePath).toBe('/admin/home')
  })

  it('sessionStorage 접근이 막혀도 예외 없이 메모리 세션 id로 전송한다', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('The operation is insecure.', 'SecurityError')
    })

    expect(() => logClientEvent({ eventType: 'API_ERROR', severity: 'ERROR' })).not.toThrow()
    expect(recordMock).toHaveBeenCalledTimes(1)
    expect(recordMock.mock.calls[0]?.[0]).toMatchObject({ clientSessionId: expect.stringMatching(/^[A-Za-z0-9-]{8,80}$/) })
  })

  it('전송 호출이 동기 예외를 던져도 호출부로 전파하지 않는다', () => {
    recordMock.mockImplementation(() => {
      throw new Error('boom')
    })

    expect(() => logClientEvent({ eventType: 'API_ERROR', severity: 'ERROR' })).not.toThrow()
  })
})
