import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type {
  CommonCodeCreateRequest,
  CommonCodeItems,
  CommonCodeUpdateRequest,
} from '@/types/commonCode'

/*
 * 관리자 공통코드 관리 전용(/api/admin/codes/**, ROLE_ADMIN·ROLE_RECRUIT_ADMIN).
 * 백엔드가 GET/POST만 쓰는 관례라 수정도 POST다. 삭제 API는 없고 active=false soft delete만 있다.
 * groupCode를 생략하면 전체를 그룹/정렬 순으로 돌려준다(화면은 이 형태만 쓴다).
 */
export const adminCommonCodeApi = {
  fetchCodes(groupCode?: string) {
    return apiClient.get<ApiResponse<CommonCodeItems[]>>('/admin/codes', {
      params: groupCode ? { groupCode } : undefined,
    })
  },

  createCode(request: CommonCodeCreateRequest) {
    return apiClient.post<ApiResponse<CommonCodeItems>>('/admin/codes', request)
  },

  updateCode(id: number, request: CommonCodeUpdateRequest) {
    return apiClient.post<ApiResponse<CommonCodeItems>>(`/admin/codes/${id}`, request)
  },
}
