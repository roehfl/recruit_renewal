import { apiClient } from '../../client'
import type { ApiResponse } from '@/types/api'
import type { LanguageReplaceRequest, LanguageResponse } from '@/types/application/sections/language'

export const languageApi = {
  getApplicationsLanguages(applicationId: number) {
    return apiClient.get<ApiResponse<LanguageResponse[]>>(`applications/${applicationId}/languages`)
  },

  replaceApplicationsLanguages(applicationId: number, payload: LanguageReplaceRequest) {
    return apiClient.post<ApiResponse<LanguageResponse[]>>(`applications/${applicationId}/languages`, payload)
  },
}
