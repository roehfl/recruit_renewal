import { apiClient } from '../../client'
import type { ApiResponse } from '@/types/api'
import type { GapPeriodReplaceRequest, GapPeriodResponse } from '@/types/application/sections/gapPeriod'

export const gapPeriodApi = {
  getApplicationsGapPeriods(applicationId: number) {
    return apiClient.get<ApiResponse<GapPeriodResponse[]>>(`applications/${applicationId}/gap-periods`)
  },

  replaceApplicationsGapPeriods(applicationId: number, payload: GapPeriodReplaceRequest) {
    return apiClient.post<ApiResponse<GapPeriodResponse[]>>(`applications/${applicationId}/gap-periods`, payload)
  },
}
