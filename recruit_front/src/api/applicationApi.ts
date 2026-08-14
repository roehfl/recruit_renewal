import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { ApplicationSearchParams, MyApplicationList, ChangePasswordParams, ChangePasswordRequest, SignupUser, checkEmailRequest } from '@/types/application'

export const applicationApi = {

  getMyApplications(params: ApplicationSearchParams){
    return apiClient.get<ApiResponse<MyApplicationList>>('/applications/me')
  },

  changePassword(params: ChangePasswordParams){
    return apiClient.post<ApiResponse<ChangePasswordRequest>>('/applicant/account/password', params)
  },

  signup(request: SignupUser) {
    return apiClient.post<ApiResponse<SignupUser>>('/auth/applicants/signup', request)
  },
  
  checkEmail(email: string) {
    return apiClient.get<ApiResponse<checkEmailRequest>>('/auth/applicants/check-email', {
      params: {
        email,
      },
    })
  },

}