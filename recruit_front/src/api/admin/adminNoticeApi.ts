import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type { PageResponse } from '@/types/page'
import type {
  AdminNoticeDetail,
  AdminNoticeListItem,
  AdminNoticeSearchParams,
  NoticeSaveRequest,
} from '@/types/notice'

/*
 * 관리자 공지 관리 전용(ROLE_ADMIN·ROLE_RECRUIT_ADMIN).
 * 조회는 /admin/notices, 쓰기는 /board/notices 다 — /board 아래 GET 은 공개라 조회만 /admin 으로 뺐고,
 * 쓰기는 POST /api/board/** 매처가 이미 관리자 전용으로 막고 있다.
 * 백엔드가 GET/POST만 쓰는 관례라 수정·삭제도 POST다. 삭제는 deleted=true soft delete다.
 * 지원자 공개 조회(fetchNotices·fetchNoticeDetail)는 boardApi 에 있다.
 */
export const adminNoticeApi = {
  /** 삭제된 공지까지 조회한다. */
  fetchNotices(params: AdminNoticeSearchParams) {
    return apiClient.get<ApiResponse<PageResponse<AdminNoticeListItem>>>('/admin/notices', {
      params: {
        page: params.page,
        size: params.size,
        searchType: params.searchType,
        keyword: params.keyword,
        deleted: params.deleted,
        pinnedOnly: params.pinnedOnly,
      },
    })
  },

  /** 편집용 상세. contentHtml 은 지원자 상세와 같게 서버가 정제해서 내려준다. */
  fetchNoticeDetail(noticeId: number) {
    return apiClient.get<ApiResponse<AdminNoticeDetail>>(`/admin/notices/${noticeId}`)
  },

  createNotice(request: NoticeSaveRequest) {
    return apiClient.post<ApiResponse<number>>('/board/notices', request)
  },

  updateNotice(noticeId: number, request: NoticeSaveRequest) {
    return apiClient.post<ApiResponse<number>>(`/board/notices/${noticeId}`, request)
  },

  deleteNotice(noticeId: number) {
    return apiClient.post<ApiResponse<void>>(`/board/notices/${noticeId}/delete`)
  },

  restoreNotice(noticeId: number) {
    return apiClient.post<ApiResponse<void>>(`/board/notices/${noticeId}/restore`)
  },

  /** 본문을 다시 보내지 않고 상단 고정만 바꾼다. */
  pinNotice(noticeId: number) {
    return apiClient.post<ApiResponse<void>>(`/board/notices/${noticeId}/pin`)
  },

  unpinNotice(noticeId: number) {
    return apiClient.post<ApiResponse<void>>(`/board/notices/${noticeId}/unpin`)
  },
}
