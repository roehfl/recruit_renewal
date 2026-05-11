import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { LoginRequest, LoginUser } from '@/types/auth'

export const authApi = {
  login(request: LoginRequest) {
    return apiClient.post<ApiResponse<LoginUser>>('/auth/login', request)
  },

  me() {
    return apiClient.get<ApiResponse<LoginUser>>('/auth/me')
  },

  logout() {
    return apiClient.post<ApiResponse<void>>('/auth/logout')
  },
}
