import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { ApplicantStageResult, ApplicationSearchParams, MyApplicationList, ChangePasswordParams, ChangePasswordRequest, SignupUser, checkEmailRequest, FindEmailResponse, EmailVerificationRequest, PasswordResetRequest } from '@/types/application'

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

  /** 가입 이메일 인증번호 발송. 가입된 이메일·60초 안 재요청·발송 실패는 400(서버 문구). */
  sendSignupEmailVerification(email: string) {
    return apiClient.post<ApiResponse<null>>('/auth/applicants/email-verification/send', { email })
  },

  /** 가입 이메일 인증번호 확인. 성공해야 가입할 수 있다(확인 후 10분). */
  verifySignupEmail(request: EmailVerificationRequest) {
    return apiClient.post<ApiResponse<null>>('/auth/applicants/email-verification/verify', request)
  },

  /** 비밀번호 재설정 인증번호 발송. 미가입 이메일은 404(서버 문구). */
  sendPasswordResetCode(email: string) {
    return apiClient.post<ApiResponse<null>>('/auth/applicants/password-reset/send', { email })
  },

  verifyPasswordResetCode(request: EmailVerificationRequest) {
    return apiClient.post<ApiResponse<null>>('/auth/applicants/password-reset/verify', request)
  },

  /** 새 비밀번호 설정. 같은 세션에서 인증번호를 확인한 뒤에만 된다. */
  resetPassword(request: PasswordResetRequest) {
    return apiClient.post<ApiResponse<null>>('/auth/applicants/password-reset', request)
  },

}