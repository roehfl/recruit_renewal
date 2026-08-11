import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { PageResponse } from '@/types/page'
import type { AdminJobPostingListItem } from '@/types/jobPosting'

export const adminJobPostingApi = {
  getJobPostings(page = 0, size = 50) {
    return apiClient.get<ApiResponse<PageResponse<AdminJobPostingListItem>>>('/admin/job-postings', {
      params: {
        page,
        size,
      },
    })
  },
}
