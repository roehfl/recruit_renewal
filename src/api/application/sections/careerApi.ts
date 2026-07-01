import { apiClient } from "@/api/client"
import type { ApiResponse } from "@/types/api"
import type { CareerResponse, CareerReplaceRequest } from "@/types/application/sections/career"

export const applicationCareerApi = {

  getApplicationCareer(applicationId: number){
    return apiClient.get<ApiResponse<CareerResponse>>(`/applications/${applicationId}/careers`)
  },

  postApplicationCareer(applicationId: number, careers: CareerReplaceRequest){
    return apiClient.post<ApiResponse<CareerResponse>>(`/applications/${applicationId}/careers`, careers)
  },

}