import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type { InterviewSearchParams, AdminInterviewSummaryResponse, AdminInterviewDetailResponse } from '@/types/admin/interview'

export const adminInterviewApi = {
  getInterviews(jobPostingId: number, params: InterviewSearchParams ) {
    return apiClient.get<ApiResponse<AdminInterviewSummaryResponse[]>>(`/admin/job-postings/${jobPostingId}/interviews`, {
      params: {
        stageId: params.stageId,
        status: params.status,
        from: params.from,
        to: params.to,
      },
    })
  },
  getInterview(interviewId: number) {
      return apiClient.get<ApiResponse<AdminInterviewDetailResponse[]>>(`/admin/interviews/${interviewId}`)
    },

}