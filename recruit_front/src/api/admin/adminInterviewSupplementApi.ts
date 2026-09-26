import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type {
  AdminInterviewSupplement,
  AdminInterviewSupplementAnswerDetail,
  AdminInterviewSupplementCandidate,
  InterviewSupplementWindowSaveRequest,
} from '@/types/admin/interviewSupplement'

const base = (stageId: number) => `/admin/stages/${stageId}/interview-supplement`

/* 면접 추가사항 관리. 질문 명령은 모두 바뀐 세트 상태를, 시간 명령은 대상 지원자 전체를 돌려준다. */
export const adminInterviewSupplementApi = {
  getSupplement(stageId: number) {
    return apiClient.get<ApiResponse<AdminInterviewSupplement>>(base(stageId))
  },

  enable(stageId: number) {
    return apiClient.post<ApiResponse<AdminInterviewSupplement>>(base(stageId))
  },

  /** 답변이 하나라도 있으면 400 */
  disable(stageId: number) {
    return apiClient.post<ApiResponse<AdminInterviewSupplement>>(`${base(stageId)}/delete`)
  },

  addQuestion(stageId: number, content: string) {
    return apiClient.post<ApiResponse<AdminInterviewSupplement>>(`${base(stageId)}/questions`, { content })
  },

  updateQuestion(stageId: number, questionId: number, content: string) {
    return apiClient.post<ApiResponse<AdminInterviewSupplement>>(`${base(stageId)}/questions/${questionId}`, {
      content,
    })
  },

  /** 답변이 달린 질문은 400 */
  deleteQuestion(stageId: number, questionId: number) {
    return apiClient.post<ApiResponse<AdminInterviewSupplement>>(`${base(stageId)}/questions/${questionId}/delete`)
  },

  /** 세트의 질문 id 전체를 새 순서대로 */
  reorderQuestions(stageId: number, questionIds: number[]) {
    return apiClient.post<ApiResponse<AdminInterviewSupplement>>(`${base(stageId)}/questions/reorder`, {
      questionIds,
    })
  },

  getCandidates(stageId: number) {
    return apiClient.get<ApiResponse<AdminInterviewSupplementCandidate[]>>(`${base(stageId)}/candidates`)
  },

  saveWindows(stageId: number, request: InterviewSupplementWindowSaveRequest) {
    return apiClient.post<ApiResponse<AdminInterviewSupplementCandidate[]>>(`${base(stageId)}/windows`, request)
  },

  resetWindows(stageId: number, jobApplicationIds: number[]) {
    return apiClient.post<ApiResponse<AdminInterviewSupplementCandidate[]>>(`${base(stageId)}/windows/reset`, {
      jobApplicationIds,
    })
  },

  getAnswers(stageId: number, jobApplicationId: number) {
    return apiClient.get<ApiResponse<AdminInterviewSupplementAnswerDetail>>(
      `${base(stageId)}/candidates/${jobApplicationId}/answers`,
    )
  },
}
