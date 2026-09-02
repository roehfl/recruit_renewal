import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { PageResponse } from '@/types/page'
import type {
  AdminJobPostingDetail,
  AdminJobPostingListItem,
  AdminJobPostingSaveRequest,
  NewPostingImage,
} from '@/types/jobPosting'
import type {
  QuestionTemplateItem,
  QuestionTemplateRequest,
  QuestionRequest,
  QuestionItem
} from '@/types/question'

const UPLOAD_TIMEOUT_MS = 120000 // 기본 10초로는 다장 이미지 업로드가 끊길 수 있다.

export const adminJobPostingApi = {
  getJobPostings(page = 0, size = 50) {
    return apiClient.get<ApiResponse<PageResponse<AdminJobPostingListItem>>>('/admin/job-postings', {
      params: {
        page,
        size,
      },
    })
  },
  getJobPosting(id: number) {
    return apiClient.get<ApiResponse<AdminJobPostingDetail>>(`/admin/job-postings/${id}`)
  },
  createJobPosting(request: AdminJobPostingSaveRequest, images: NewPostingImage[]) {
    const formData = new FormData()
    formData.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }))
    if (images.length > 0) {
      const metas = images.map((image, index) => ({ altText: image.altText, sortOrder: index }))
      formData.append('imageMetas', new Blob([JSON.stringify(metas)], { type: 'application/json' }))
      images.forEach((image) => formData.append('imageFiles', image.file))
    }
    return apiClient.post<ApiResponse<number>>('/admin/job-postings', formData, { timeout: UPLOAD_TIMEOUT_MS })
  },
  updateJobPosting(id: number, request: AdminJobPostingSaveRequest & { contentHtml: string | null }) {
    return apiClient.post<ApiResponse<number>>(`/admin/job-postings/${id}`, request)
  },
  publishJobPosting(id: number) {
    return apiClient.post<ApiResponse<number>>(`/admin/job-postings/${id}/publish`)
  },
  closeJobPosting(id: number) {
    return apiClient.post<ApiResponse<number>>(`/admin/job-postings/${id}/close`)
  },
  addImage(id: number, file: File, altText: string, sortOrder?: number) {
    const formData = new FormData()
    formData.append('file', file)
    return apiClient.post<ApiResponse<number>>(`/admin/job-postings/${id}/images`, formData, {
      params: { altText, sortOrder },
      timeout: UPLOAD_TIMEOUT_MS,
    })
  },
  updateImageAltText(id: number, imageId: number, altText: string) {
    return apiClient.post<ApiResponse<number>>(`/admin/job-postings/${id}/images/${imageId}`, { altText })
  },
  deleteImage(id: number, imageId: number) {
    return apiClient.post<ApiResponse<number>>(`/admin/job-postings/${id}/images/${imageId}/delete`)
  },
  reorderImages(id: number, imageIds: number[]) {
    return apiClient.post<ApiResponse<number>>(`/admin/job-postings/${id}/images/order`, { imageIds })
  },
  /** 관리자 미리보기용. 세션 쿠키가 필요해 <img src> 직접 참조 대신 blob으로 받는다. */
  fetchImageBlob(id: number, imageId: number) {
    return apiClient.get<Blob>(`/admin/job-postings/${id}/images/${imageId}/file`, { responseType: 'blob' })
  },

  // 질문 템플릿
  createQuestionTemplate(request: QuestionTemplateRequest) {
    return apiClient.post<ApiResponse<QuestionTemplateRequest>>(`/admin/question-templates`, request)
  },
  updateQuestionTemplate(templateId: number, request: QuestionTemplateRequest) {
    return apiClient.post<ApiResponse<QuestionTemplateRequest>>(`/admin/question-templates/${templateId}`, request)
  },
  selectQuestionTemplate(templateId: number) {
    return apiClient.get<ApiResponse<QuestionTemplateItem>>(`/admin/question-templates/${templateId}`)
  },
  getQuestionTemplates(page = 0, size = 20) {
    return apiClient.get<ApiResponse<PageResponse<QuestionTemplateItem>>>(`/admin/question-templates`, {
      params: {
        page,
        size
      },
    })
  },
  getQuestionTemplatesActive(page = 0, size = 50, active: boolean) {
    return apiClient.get<ApiResponse<PageResponse<QuestionTemplateItem>>>(`/admin/question-templates`, {
      params: {
        page,
        size,
        active
      },
    })
  },
  setQuestionActive(templateId: number) {
    return apiClient.post<ApiResponse<QuestionTemplateItem>>(`/admin/question-templates/${templateId}/activate`)
  },
  setQuestionDeactive(templateId: number) {
    return apiClient.post<ApiResponse<QuestionTemplateItem>>(`/admin/question-templates/${templateId}/deactivate`)
  },

}