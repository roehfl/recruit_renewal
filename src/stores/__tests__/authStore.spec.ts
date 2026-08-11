import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

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
  phoneNumber: '',
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

  it('요청이 실패해도 initialized는 true가 되고 미로그인으로 판정한다', async () => {
    meMock.mockRejectedValue(new Error('network error'))

    const authStore = useAuthStore()

    await expect(authStore.fetchMe()).resolves.toBe(false)
    expect(authStore.isLoggedIn).toBe(false)
    expect(authStore.initialized).toBe(true)
  })
})
