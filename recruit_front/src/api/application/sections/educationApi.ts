import { apiClient } from '../../client'
import type { ApiResponse } from '@/types/api'
import type { EducationResponse, schoolItem, EducationReplaceRequest, schoolResponse, shcoolSerachParams } from '@/types/application/sections/education'

export const educationApi = {

  getApplicationsEducations(applicationId: number){
    return apiClient.get<ApiResponse<EducationResponse[]>>(`applications/${applicationId}/educations`)
  },

  postApplicationsEducations(applicationId: number, payload: EducationReplaceRequest){
    return apiClient.post<ApiResponse<EducationResponse[]>>(`applications/${applicationId}/educations`, payload)
  },

  getSchools(params: shcoolSerachParams){
    return apiClient.get<ApiResponse<schoolItem[]>>(`schools`, {
      params: {
        q: params.q,
        educationLevel: params.educationLevel,
      }
    })
  },
}