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
    isLoggedIn: (state) => state.user !== null,
    roles: (state) => state.user?.roles ?? [],
    userType: (state) => state.user?.userType,
    name: (state) => state.user?.name ?? '',
    loginId: (state) => state.user?.loginId ?? '',
    deptName: (state) => state.user?.deptName ?? '',
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

    async fetchMe() {
      try {
        const response = await authApi.me({ skipAuthRedirect: true })
        this.user = response.data.data
        this.initialized = true
        return true
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
