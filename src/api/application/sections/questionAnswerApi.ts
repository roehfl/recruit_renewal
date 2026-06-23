import { apiClient } from '../../client'
import type { ApiResponse } from '@/types/api'
import type {
  ApplicationAnswerReplaceRequest,
  ApplicationQuestionResponse,
} from '@/types/application/sections/questionAnswer'

export const questionAnswerApi = {
  getApplicationsQuestions(applicationId: number) {
    return apiClient.get<ApiResponse<ApplicationQuestionResponse[]>>(`applications/${applicationId}/questions`)
  },

  replaceApplicationsAnswers(applicationId: number, payload: ApplicationAnswerReplaceRequest) {
    return apiClient.post<ApiResponse<ApplicationQuestionResponse[]>>(`applications/${applicationId}/answers`, payload)
  },
}
