import { beforeEach, describe, expect, it, vi } from 'vitest'

import { applicationApi } from '@/api/applicationApi'

const getMock = vi.fn()

vi.mock('@/api/client', () => ({
  apiClient: {
    get: (...args: unknown[]) => getMock(...args),
  },
}))

describe('applicationApi.getMyApplications', () => {
  beforeEach(() => {
    getMock.mockReset()
  })

  it('page/size를 쿼리 파라미터로 전달한다', () => {
    applicationApi.getMyApplications({ page: 2, size: 10 })

    expect(getMock).toHaveBeenCalledWith('/applications/me', { params: { page: 2, size: 10 } })
  })
})
