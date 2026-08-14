import { apiClient } from '../../client'
import type { ApiResponse } from '@/types/api'
import type { CertificateReplaceRequest, CertificateResponse } from '@/types/application/sections/certificate'

export const certificateApi = {
  getApplicationsCertificates(applicationId: number) {
    return apiClient.get<ApiResponse<CertificateResponse[]>>(`applications/${applicationId}/certificates`)
  },

  replaceApplicationsCertificates(applicationId: number, payload: CertificateReplaceRequest) {
    return apiClient.post<ApiResponse<CertificateResponse[]>>(`applications/${applicationId}/certificates`, payload)
  },
}
