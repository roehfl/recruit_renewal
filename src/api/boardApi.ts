import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { PageResponse } from '@/types/page'
import type { NoticeDetail, NoticeListItem, NoticeSearchParams } from '@/types/notice'
import type { JobPostingListItem, JobPostingSearchParams, JobPostingDetail } from '@/types/jobPosting'

export const boardApi = {
  fetchNotices(params: NoticeSearchParams) {
    return apiClient.get<ApiResponse<PageResponse<NoticeListItem>>>('/board/notices', {
      params: {
        page: params.page,
        size: params.size,
        searchType: params.searchType,
        keyword: params.keyword,
      },
    })
  },

  fetchJobPostings(params: JobPostingSearchParams) {
    return apiClient.get<ApiResponse<PageResponse<JobPostingListItem>>>('/job-postings', {
      params: {
        page: params.page,
        size: params.size,
      },
    })
  },

  fetchNoticeDetail(noticeId: number) {
    return apiClient.get<ApiResponse<NoticeDetail>>(`/board/notices/${noticeId}`)
  },

  fetchJobPostingDetail(id: number) {
    return apiClient.get<ApiResponse<JobPostingDetail>>(`/job-postings/${id}`)
  },
}
