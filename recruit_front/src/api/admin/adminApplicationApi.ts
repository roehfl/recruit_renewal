import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type { PageResponse } from '@/types/page'
import type {
  AdminApplicationSummaryResponse,
  AdminApplicationSearchRequest,
  AdminApplicationDetailResponse,
  ApplicationFormLayoutResponse,
} from '@/types/admin/application'
import type {
  AdminBasicInfoResponse,
  AdminMilitaryResponse,
  AdminEducationResponse,
  AdminCareerResponse,
  AdminCertificateResponse,
  AdminLanguageResponse,
  AdminAwardResponse,
  AdminGapPeriodResponse,
  AdminApplicationAnswerResponse,
  AdminAttachmentResponse,
} from '@/types/admin/applicationSections'
import type {
  AdminJobPostingDetail,
  AdminJobPostingListItem,
  AdminJobPostingSaveRequest,
  NewPostingImage,
} from '@/types/jobPosting'

const UPLOAD_TIMEOUT_MS = 120000 // 기본 10초로는 다장 이미지 업로드가 끊길 수 있다.

export const adminApplicationApi = {
  getApplications(jobPostingId: number, searchRequest: AdminApplicationSearchRequest, page = 0, size = 20) {
    return apiClient.get<ApiResponse<PageResponse<AdminApplicationSummaryResponse>>>(
      `/admin/job-postings/${jobPostingId}/applications`, 
      {
        params: {
          page,
          size,
          ...searchRequest,
        },
    })
  },

  getApplication(applicationId: number) {
    return apiClient.get<ApiResponse<AdminApplicationDetailResponse>>(`/admin/applications/${applicationId}`)
  },

  getApplicationFormLayout(jobPostingId: number) {
    return apiClient.get<ApiResponse<ApplicationFormLayoutResponse>>(`/admin/job-postings/${jobPostingId}/application-form-layout`)

  },

  /* ---- section ---- */
  getBasicInfo(applicationId: number){
    return apiClient.get<ApiResponse<AdminBasicInfoResponse>>(`/admin/applications/${applicationId}/basic-info`)
  },
  getMilitary(applicationId: number){
    return apiClient.get<ApiResponse<AdminMilitaryResponse>>(`/admin/applications/${applicationId}/military`)
  },
  getEducations(applicationId: number){
    return apiClient.get<ApiResponse<AdminEducationResponse[]>>(`/admin/applications/${applicationId}/educations`)
  },
  getCareers(applicationId: number){ 
    return apiClient.get<ApiResponse<AdminCareerResponse>>(`/admin/applications/${applicationId}/careers`)
  },
  getCertificates(applicationId: number){
    return apiClient.get<ApiResponse<AdminCertificateResponse[]>>(`/admin/applications/${applicationId}/certificates`)
  },
  getLanguages(applicationId: number){
    return apiClient.get<ApiResponse<AdminLanguageResponse[]>>(`/admin/applications/${applicationId}/languages`)
  },
  getAwards(applicationId: number){
    return apiClient.get<ApiResponse<AdminAwardResponse[]>>(`/admin/applications/${applicationId}/awards`)
  },
  getGapPeriods(applicationId: number){ 
    return apiClient.get<ApiResponse<AdminGapPeriodResponse[]>>(`/admin/applications/${applicationId}/gap-periods`)
  },
  getAnswers(applicationId: number){
    return apiClient.get<ApiResponse<AdminApplicationAnswerResponse[]>>(`/admin/applications/${applicationId}/answers`)
  },
  getAttachments(applicationId: number){
    return apiClient.get<ApiResponse<AdminAttachmentResponse[]>>(`/admin/applications/${applicationId}/attachments`)
  },
  getApplicationAttachments(){

  },
  /** 첨부 원본을 blob으로 받는다. 세션 쿠키가 필요하므로 apiClient를 경유한다. */
  downloadApplicationAttachment(applicationId: number, attachmentId: number) {
    return apiClient.get<Blob>(`/admin/applications/${applicationId}/attachments/${attachmentId}/download`, {
      responseType: 'blob',
    })
  },
}
