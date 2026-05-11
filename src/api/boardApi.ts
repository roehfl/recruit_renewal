import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { PageResponse } from '@/types/page'
import type { NoticeDetail, NoticeListItem, NoticeSearchParams } from '@/types/notice'
import type { JobPostingListItem, JobPostingSearchParams } from '@/types/jobPosting'

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

  fetchJobPostings(params: NoticeSearchParams) {
    return apiClient.get<ApiResponse<PageResponse<JobPostingListItem>>>('/board/job-postings', {
      params: {
        params,
      },
    })
  },

  fetchNoticeDetail(noticeId: number) {
    return apiClient.get<ApiResponse<NoticeDetail>>(`/board/notices/${noticeId}`)
  },
}
