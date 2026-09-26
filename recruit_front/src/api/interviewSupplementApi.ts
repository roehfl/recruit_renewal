import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type {
  ApplicantInterviewSupplementForm,
  ApplicantInterviewSupplementSaveRequest,
  ApplicantInterviewSupplementSaveResponse,
  ApplicantInterviewSupplementSummary,
} from '@/types/interviewSupplement'

const formPath = (applicationId: number, stageId: number) =>
  `/applicant/applications/${applicationId}/interview-supplements/${stageId}`

/* 지원자 면접 추가사항. 입력 시간 밖이면 조회·저장 모두 400 이다(서버 시각 기준, 유예 없음). */
export const interviewSupplementApi = {
  getMySupplements() {
    return apiClient.get<ApiResponse<ApplicantInterviewSupplementSummary[]>>('/applicant/interview-supplements')
  },

  getForm(applicationId: number, stageId: number) {
    return apiClient.get<ApiResponse<ApplicantInterviewSupplementForm>>(formPath(applicationId, stageId))
  },

  saveAnswers(applicationId: number, stageId: number, request: ApplicantInterviewSupplementSaveRequest) {
    return apiClient.post<ApiResponse<ApplicantInterviewSupplementSaveResponse>>(
      `${formPath(applicationId, stageId)}/answers`,
      request,
    )
  },
}
