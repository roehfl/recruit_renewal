import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { ApplicationDaily, FunnelDimension, FunnelResult } from '@/types/statistics'

export const statisticsApi = {
  /*
   * dimension은 콤마 구분 다중 값을 받는다. 축마다 따로 호출하면 서버가 같은 코호트를 축 개수만큼
   * 다시 읽으므로, 대시보드가 필요한 축을 한 번에 요청한다.
   */
  getFunnel(jobPostingId: number, dimensions: FunnelDimension[], topN?: number) {
    return apiClient.get<ApiResponse<FunnelResult>>(
      `/admin/job-postings/${jobPostingId}/statistics/funnel`,
      {
        params: {
          ...(dimensions.length > 0 ? { dimension: dimensions.join(',') } : {}),
          ...(topN !== undefined ? { topN } : {}),
        },
      },
    )
  },

  getApplicationsDaily(jobPostingId: number) {
    return apiClient.get<ApiResponse<ApplicationDaily>>(
      `/admin/job-postings/${jobPostingId}/statistics/applications-daily`,
    )
  },
}
