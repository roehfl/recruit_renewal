import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { AxiosError, type AxiosResponse } from 'axios'

import { useAuthStore } from '@/stores/authStore'
import type { LoginUser } from '@/types/auth'

const meMock = vi.fn()

vi.mock('@/api/authApi', () => ({
  authApi: {
    me: () => meMock(),
    login: vi.fn(),
    logout: vi.fn(),
  },
}))

const loginUser: LoginUser = {
  loginId: 'admin',
  name: '채용관리자',
  deptName: '인사기획부',
  userType: 'Employee',
  roles: ['ROLE_RECRUIT_ADMIN'],
}

describe('authStore.fetchMe', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    meMock.mockReset()
  })

  it('로그인 사용자가 있으면 복구하고 로그인 상태로 판정한다', async () => {
    meMock.mockResolvedValue({ data: { success: true, data: loginUser } })

    const authStore = useAuthStore()

    await expect(authStore.fetchMe()).resolves.toBe(true)
    expect(authStore.isLoggedIn).toBe(true)
    expect(authStore.roles).toEqual(['ROLE_RECRUIT_ADMIN'])
    expect(authStore.initialized).toBe(true)
  })

  /*
   * 200이지만 data가 비어 오는 경우. user에 undefined가 담기면
   * isLoggedIn이 참이 되어 미로그인 사용자가 /login 대신 /403으로 빠졌다.
   */
  it('200이어도 사용자 데이터가 undefined면 미로그인으로 판정한다', async () => {
    meMock.mockResolvedValue({ data: { success: true, data: undefined } })

    const authStore = useAuthStore()

    await expect(authStore.fetchMe()).resolves.toBe(false)
    expect(authStore.isLoggedIn).toBe(false)
    expect(authStore.roles).toEqual([])
  })

  it('200이어도 사용자 데이터가 null이면 미로그인으로 판정한다', async () => {
    meMock.mockResolvedValue({ data: { success: true, data: null } })

    const authStore = useAuthStore()

    await expect(authStore.fetchMe()).resolves.toBe(false)
    expect(authStore.isLoggedIn).toBe(false)
  })

  it('success가 false면 data가 있어도 미로그인으로 판정한다', async () => {
    meMock.mockResolvedValue({
      data: { success: false, data: loginUser, message: '로그인이 필요합니다.' },
    })

    const authStore = useAuthStore()

    await expect(authStore.fetchMe()).resolves.toBe(false)
    expect(authStore.isLoggedIn).toBe(false)
  })

  it('401이면 initialized는 true가 되고 미로그인으로 확정한다', async () => {
    meMock.mockRejectedValue(
      new AxiosError('Request failed with status code 401', AxiosError.ERR_BAD_REQUEST, undefined, undefined, {
        status: 401,
      } as AxiosResponse),
    )

    const authStore = useAuthStore()

    await expect(authStore.fetchMe()).resolves.toBe(false)
    expect(authStore.isLoggedIn).toBe(false)
    expect(authStore.initialized).toBe(true)
  })

  /*
   * 네트워크 오류·5xx는 세션 유무를 알 수 없으므로 미로그인으로 확정하지 않는다.
   * initialized를 false로 남겨 다음 내비게이션에서 다시 확인한다.
   */
  it('네트워크 오류면 미로그인으로 판정하되 initialized는 false로 남긴다', async () => {
    meMock.mockRejectedValue(new AxiosError('Network Error', AxiosError.ERR_NETWORK))

    const authStore = useAuthStore()

    await expect(authStore.fetchMe()).resolves.toBe(false)
    expect(authStore.isLoggedIn).toBe(false)
    expect(authStore.initialized).toBe(false)
  })

  it('5xx면 initialized는 false로 남고, 다음 조회가 성공하면 복구한다', async () => {
    meMock.mockRejectedValueOnce(
      new AxiosError('Request failed with status code 502', AxiosError.ERR_BAD_RESPONSE, undefined, undefined, {
        status: 502,
      } as AxiosResponse),
    )
    meMock.mockResolvedValueOnce({ data: { success: true, data: loginUser } })

    const authStore = useAuthStore()

    await expect(authStore.fetchMe()).resolves.toBe(false)
    expect(authStore.initialized).toBe(false)

    await expect(authStore.fetchMe()).resolves.toBe(true)
    expect(authStore.isLoggedIn).toBe(true)
    expect(authStore.initialized).toBe(true)
  })
})
