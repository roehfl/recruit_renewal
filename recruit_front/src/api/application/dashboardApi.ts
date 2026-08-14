import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type { ApplicationDashboardResponse } from '@/types/application/dashboard'

export const dashboardApi = {
  // 지원서 작성 완성도(섹션별 필수 충족 여부) 조회.
  getApplicationDashboard(applicationId: number) {
    return apiClient.get<ApiResponse<ApplicationDashboardResponse>>(`applications/${applicationId}/dashboard`)
  },
}
