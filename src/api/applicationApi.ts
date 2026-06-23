import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { ApplicationSearchParams, MyApplicationList, ChangePasswordParams, ChangePasswordRequest } from '@/types/application'

export const applicationApi = {

  getMyApplications(params: ApplicationSearchParams){
    return apiClient.get<ApiResponse<MyApplicationList>>('/applications/me')
  },

  changePassword(params: ChangePasswordParams){
    return apiClient.post<ApiResponse<ChangePasswordRequest>>('/applicant/account/password', params)
  },

}