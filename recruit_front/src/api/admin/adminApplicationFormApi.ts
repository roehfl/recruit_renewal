import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type { PageResponse } from '@/types/page'
import type {
  AdminApplicationFormSummary,
  AdminApplicationFormSummarySearchRequest,
  ApplicationFormLayoutPreviewResponse,
  ApplicationFormLayoutResponse,
  ApplicationFormLayoutSaveRequest,
} from '@/types/admin/application'
import type { AdminApplicationFormConfig } from '@/types/jobPosting'

/** 공고별 지원서 설정(양식 · 폼 구성) 화면 전용 API 모듈. */
export const adminApplicationFormApi = {
  /** 공고별 지원서 설정 요약 목록. 필터는 값이 있는 항목만 전달한다. */
  getSummaries(searchRequest: AdminApplicationFormSummarySearchRequest, page = 0, size = 20) {
    return apiClient.get<ApiResponse<PageResponse<AdminApplicationFormSummary>>>('/admin/application-forms', {
      params: { ...searchRequest, page, size },
    })
  },

  /** 지원서 양식(섹션 사용/필수) 단독 저장. 접수 시작 전에만 허용된다. */
  saveFormConfig(jobPostingId: number, request: AdminApplicationFormConfig) {
    return apiClient.post<ApiResponse<AdminApplicationFormConfig>>(
      `/admin/job-postings/${jobPostingId}/application-form-config`,
      request,
    )
  },

  getLayout(jobPostingId: number) {
    return apiClient.get<ApiResponse<ApplicationFormLayoutResponse>>(
      `/admin/job-postings/${jobPostingId}/application-form-layout`,
    )
  },

  /** 레이아웃 전체 치환 저장. 활성 섹션이 모두 배치되어 있어야 한다. */
  saveLayout(jobPostingId: number, request: ApplicationFormLayoutSaveRequest) {
    return apiClient.post<ApiResponse<ApplicationFormLayoutResponse>>(
      `/admin/job-postings/${jobPostingId}/application-form-layout`,
      request,
    )
  },

  getLayoutPreview(jobPostingId: number) {
    return apiClient.get<ApiResponse<ApplicationFormLayoutPreviewResponse>>(
      `/admin/job-postings/${jobPostingId}/application-form-layout/preview`,
    )
  },
}
