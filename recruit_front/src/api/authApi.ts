import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { LoginRequest, LoginUser } from '@/types/auth'

export const authApi = {
  login(request: LoginRequest) {
    // 로그인 401은 아이디·비밀번호 불일치이지 세션 만료가 아니다. 5xx·타임아웃은 계속 기록한다.
    return apiClient.post<ApiResponse<LoginUser>>('/auth/login', request, {
      skipSessionExpiredLog: true,
    })
  },

  me(options?: { skipAuthRedirect?: boolean }) {
    return apiClient.get<ApiResponse<LoginUser>>('/auth/me', {
      skipAuthRedirect: options?.skipAuthRedirect === true,
      // 세션 복구용 조회는 비로그인 방문마다 401이 정상이고 라우트 가드가 재시도하므로 텔레메트리에서 뺀다.
      skipClientEventLog: true,
    })
  },

  logout() {
    return apiClient.post<ApiResponse<void>>('/auth/logout')
  },
}
