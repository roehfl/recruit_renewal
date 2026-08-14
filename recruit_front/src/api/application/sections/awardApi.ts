import { apiClient } from '../../client'
import type { ApiResponse } from '@/types/api'
import type { AwardReplaceRequest, AwardResponse } from '@/types/application/sections/award'

export const awardApi = {
  getApplicationsAwards(applicationId: number) {
    return apiClient.get<ApiResponse<AwardResponse[]>>(`applications/${applicationId}/awards`)
  },

  replaceApplicationsAwards(applicationId: number, payload: AwardReplaceRequest) {
    return apiClient.post<ApiResponse<AwardResponse[]>>(`applications/${applicationId}/awards`, payload)
  },
}
