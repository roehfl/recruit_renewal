import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type { PageResponse } from '@/types/page'
import type { AddressSearchParams, AddressSearchResponse } from '@/types/application/address'

export const addressApi = {
  // 지원서 작성 완성도(섹션별 필수 충족 여부) 조회.
  getAddresses(params: AddressSearchParams) {
    return apiClient.get<ApiResponse<AddressSearchResponse>>(`addresses`, {
      params: {
        keyword: params.keyword,
        currentPage: params.currentPage,
        countPerPage: params.countPerPage,
      },
    })
  },
}
