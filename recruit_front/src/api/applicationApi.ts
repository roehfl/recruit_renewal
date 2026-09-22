import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { ApplicantStageResult, ApplicationSearchParams, MyApplicationList, ChangePasswordParams, ChangePasswordRequest, SignupUser, checkEmailRequest, FindEmailResponse } from '@/types/application'

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

  /** 아이디 찾기. 요청 본문은 없다 — 서버가 세션의 NICE 인증 결과(용도 FIND_EMAIL)를 1회 소비한다. */
  findEmail() {
    return apiClient.post<ApiResponse<FindEmailResponse>>('/auth/applicants/find-email')
  },

}