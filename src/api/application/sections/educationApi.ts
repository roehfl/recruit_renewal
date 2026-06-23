import { apiClient } from '../../client'
import type { ApiResponse } from '@/types/api'
import type { EducationRequest } from '@/types/application/sections/education'

export const applicationEducationApi = {

  getApplicationsEducations(applicationId: number){
    return apiClient.get<ApiResponse<EducationRequest>>(`applications/${applicationId}/educations`)
  },

}