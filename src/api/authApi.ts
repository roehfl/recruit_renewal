import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { LoginRequest, LoginUser, SignupUser, checkEmailRequest } from '@/types/auth'

export const authApi = {
  login(request: LoginRequest) {
    return apiClient.post<ApiResponse<LoginUser>>('/auth/login', request)
  },

  me(options?: { skipAuthRedirect?: boolean }) {
    return apiClient.get<ApiResponse<LoginUser>>('/auth/me', {
      skipAuthRedirect: options?.skipAuthRedirect === true,
    })
  },

  logout() {
    return apiClient.post<ApiResponse<void>>('/auth/logout')
  },

  signup(request: SignupUser) {
    return apiClient.post<ApiResponse<SignupUser>>('/auth/applicants/sign-up', request)
  },

  checkEmail(email: string) {
    return apiClient.get<ApiResponse<checkEmailRequest>>('/auth/applicants/check-email', {
      params: {
        email,
      },
    })
  },
}
