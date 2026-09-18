import { apiClient } from '../../client'
import type { BasicInfoParams, ApiResponseBasicInfoResponse } from '@/types/application/sections/basicInfo'

export const basicInfoApi = {

  getApplicationsBasicInfo(applicationId: number){
    return apiClient.get<ApiResponseBasicInfoResponse>(`applications/${applicationId}/basic-info`)
  },

  postApplicationsBasicInfo(applicationId: number, params: BasicInfoParams){
    return apiClient.post<ApiResponseBasicInfoResponse>(`applications/${applicationId}/basic-info`, params)
  },
  
}