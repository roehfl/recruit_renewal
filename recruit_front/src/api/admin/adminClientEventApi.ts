import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type { PageResponse } from '@/types/page'
import type { ClientEventLogQuery, ClientEventLogResponse } from '@/types/admin/clientEventLog'

/**
 * 지원자 이벤트(클라이언트 이벤트 로그) 조회. ROLE_RECRUIT_ADMIN·ROLE_PRIVACY_ADMIN 만 허용한다.
 * ROLE_ADMIN 만 가진 계정이 호출하면 403 이고, 공통 인터셉터가 /403 으로 보내 버린다.
 * 화면에서 권한을 먼저 확인하고 호출해야 한다.
 */
export const adminClientEventApi = {
  /** 목록(페이지). 정렬은 서버 고정(receivedAt DESC, id DESC). */
  getClientEvents(query: ClientEventLogQuery) {
    return apiClient.get<ApiResponse<PageResponse<ClientEventLogResponse>>>('/admin/client-events', {
      params: query,
    })
  },

  /** 단건. 없으면 404. */
  getClientEvent(id: number) {
    return apiClient.get<ApiResponse<ClientEventLogResponse>>(`/admin/client-events/${id}`)
  },
}
