import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { ApplicantStageResult, ApplicationSearchParams, MyApplicationList, ChangePasswordParams, ChangePasswordRequest, SignupUser, checkEmailRequest } from '@/types/application'

export const applicationApi = {

  getMyApplications(params: ApplicationSearchParams){
    return apiClient.get<ApiResponse<MyApplicationList>>('/applications/me', { params })
  },

  getStageResults(applicationId: number) {
    return apiClient.get<ApiResponse<ApplicantStageResult[]>>(`/applications/${applicationId}/stage-results`)
  },

  changePassword(params: ChangePasswordParams){
    return apiClient.post<ApiResponse<ChangePasswordRequest>>('/applicant/account/password', params)
  },

  signup(request: SignupUser) {
    return apiClient.post<ApiResponse<SignupUser>>('/auth/applicants/sign-up', request)
  },
  
  checkEmail(email: string) {
    return apiClient.get<ApiResponse<checkEmailRequest>>('/auth/applicants/check-email', {
      params: {
        email,
      },
    })
  },

}