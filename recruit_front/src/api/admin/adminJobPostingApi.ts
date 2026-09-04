import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type { PageResponse } from '@/types/page'
import type { AdminJobPostingDetail, AdminJobPostingListItem } from '@/types/jobPosting'

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

}