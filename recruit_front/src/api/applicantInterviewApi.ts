import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { ApplicantInterviewSummary } from '@/types/applicantInterview'

export const applicantInterviewApi = {
  /** 내 모든 지원서의 확정·취소 면접. 면접 시각 순 */
  getMyInterviews() {
    return apiClient.get<ApiResponse<ApplicantInterviewSummary[]>>('/applicant/interviews')
  },
}
