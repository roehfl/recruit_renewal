import { apiClient } from '../../client'
import type { ApiResponse } from '@/types/api'
import type { BasicInfoParams, ApiResponseBasicInfoResponse } from '@/types/application/sections/basicInfo'

export const basicInfoApi = {

  getApplicationsBasicInfo(applicationId: number){
    return apiClient.get<ApiResponse<ApiResponseBasicInfoResponse>>(`applications/${applicationId}/basic-info`)
  },

  postApplicationsBasicInfo(applicationId: number, params: BasicInfoParams){
    return apiClient.post<ApiResponse<ApiResponseBasicInfoResponse>>(`applications/${applicationId}/basic-info`, params)
  },

}