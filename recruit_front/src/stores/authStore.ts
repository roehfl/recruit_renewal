import { defineStore } from 'pinia'
import { authApi } from '@/api/authApi'
import type { LoginRequest, LoginUser } from '@/types/auth'

interface AuthState {
  user: LoginUser | null
  initialized: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    user: null,
    initialized: false,
  }),

  getters: {
    isLoggedIn: (state) => state.user != null,
    roles: (state) => state.user?.roles ?? [],
    userType: (state) => state.user?.userType,
    name: (state) => state.user?.name ?? '',
    loginId: (state) => state.user?.loginId ?? '',
    deptName: (state) => state.user?.deptName ?? '',
    phoneNumber: (state) => state.user?.phoneNumber ?? '',
  },

  actions: {
    async login(request: LoginRequest) {
      const response = await authApi.login(request)

      if (!response.data.success) {
        throw new Error(response.data.message || '로그인에 실패했습니다.')
      }

      this.user = response.data.data
      this.initialized = true
    },

    /*
     * 세션 복구용. 응답이 200이어도 로그인 사용자가 없을 수 있으므로
     * success와 data를 함께 확인하고, user는 항상 null로 정규화한다.
     * (data가 undefined인 채로 담기면 isLoggedIn이 참이 되어 미로그인 상태가 로그인으로 잘못 판정된다.)
     * 반환값은 "로그인 상태로 복구되었는지"다.
     */
    async fetchMe() {
      try {
        const response = await authApi.me({ skipAuthRedirect: true })
        const loginUser = response.data.success ? response.data.data : null

        this.user = loginUser ?? null
        this.initialized = true

        return this.user !== null
      } catch {
        this.user = null
        this.initialized = true
        return false
      }
    },

    async logout() {
      await authApi.logout()
      this.user = null
      this.initialized = true
    },
  },
})
