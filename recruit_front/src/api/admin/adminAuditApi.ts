import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type { PageResponse } from '@/types/page'
import type { AuditActivityQuery, AuditActivityResponse } from '@/types/admin/auditLog'

/**
 * 감사 로그 조회. ROLE_RECRUIT_ADMIN·ROLE_PRIVACY_ADMIN 만 허용한다.
 * 기록 API 는 없다(각 도메인 서비스가 내부에서 남긴다).
 */
export const adminAuditApi = {
  /** 목록(페이지). 정렬은 서버 고정(occurredAt DESC, id DESC). */
  getActivities(query: AuditActivityQuery) {
    return apiClient.get<ApiResponse<PageResponse<AuditActivityResponse>>>('/admin/audit/activities', {
      params: query,
    })
  },

  /** 단건. 없으면 404. */
  getActivity(id: number) {
    return apiClient.get<ApiResponse<AuditActivityResponse>>(`/admin/audit/activities/${id}`)
  },
}
