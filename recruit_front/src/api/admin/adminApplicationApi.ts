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

/*
 * apiClient 기본 timeout(10초)으로는 PDF 렌더 시간을 감당하지 못한다.
 * 단건은 섹션 조회 + 렌더로 1초 안팎이지만 여유를 두고, 일괄은 상한 20건 × 렌더 시간을 고려해 넉넉히 잡는다.
 */
const PDF_DOWNLOAD_TIMEOUT_MS = 30_000
const PDF_BULK_DOWNLOAD_TIMEOUT_MS = 120_000

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

  // 지원서 1건 PDF. 파일명은 서버가 Content-Disposition 으로 내려준다({수험번호}_{이름}.pdf).
  downloadApplicationPdf(applicationId: number) {
    return apiClient.get<Blob>(`/admin/applications/${applicationId}/pdf`, {
      responseType: 'blob',
      timeout: PDF_DOWNLOAD_TIMEOUT_MS,
    })
  },

  // 선택한 지원서들을 zip 으로. 조회지만 id 를 최대 20개 실어야 해 POST 를 쓴다.
  downloadApplicationPdfBulk(applicationIds: number[]) {
    return apiClient.post<Blob>('/admin/applications/pdf/bulk', { applicationIds }, {
      responseType: 'blob',
      timeout: PDF_BULK_DOWNLOAD_TIMEOUT_MS,
    })
  },

  // 지원현황 엑셀. 목록 조회와 같은 검색 조건을 그대로 넘겨야 화면과 파일 내용이 일치한다.
  downloadApplicationsExcel(jobPostingId: number, searchRequest: AdminApplicationSearchRequest) {
    return apiClient.get<Blob>(`/admin/job-postings/${jobPostingId}/applications/export`, {
      params: { ...searchRequest },
      responseType: 'blob',
      timeout: PDF_BULK_DOWNLOAD_TIMEOUT_MS,
    })
  },
}
