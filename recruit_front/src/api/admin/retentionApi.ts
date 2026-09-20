import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type {
  DataSubjectDetail,
  DataSubjectQuery,
  DataSubjectSummary,
  ForcedPurgeRequest,
  PurgeBatchDetail,
  PurgeBatchSummary,
  RetentionPolicy,
  RetentionPolicySaveRequest,
  RetentionSchedule,
} from '@/types/admin/retention'
import type { PageResponse } from '@/types/page'

/**
 * data-subjects, 강제 파기, 스케줄 토글, 정책 등록·수정(createPolicy·updatePolicy)은
 * ROLE_PRIVACY_ADMIN 전용이다(SecurityConfig: POST /api/admin/retention/policies/**).
 * 권한 없이 호출하면 공통 인터셉터가 403 을 받아 /403 으로 보내 버리므로,
 * 화면에서 권한을 먼저 확인하고 호출해야 한다.
 */
export const retentionApi = {
  /** 보존 정책 목록. */
  getPolicies() {
    return apiClient.get<ApiResponse<RetentionPolicy[]>>('/admin/retention/policies')
  },

  /** 보존 정책 등록. */
  createPolicy(request: RetentionPolicySaveRequest) {
    return apiClient.post<ApiResponse<RetentionPolicy>>('/admin/retention/policies', request)
  },

  /** 보존 정책 수정(전체 교체). */
  updatePolicy(policyId: number, request: RetentionPolicySaveRequest) {
    return apiClient.post<ApiResponse<RetentionPolicy>>(`/admin/retention/policies/${policyId}`, request)
  },

  /** 자동 파기 설정(on/off·다음 예정일·마지막 실행 결과). */
  getSchedule() {
    return apiClient.get<ApiResponse<RetentionSchedule>>('/admin/retention/schedule')
  },

  /** 자동 파기 켜기/끄기. 보존 정책이 없으면 켤 수 없다(켜도 전건 스킵되므로). */
  updateSchedule(enabled: boolean) {
    return apiClient.post<ApiResponse<RetentionSchedule>>('/admin/retention/schedule', { enabled })
  },

  /** 이름·휴대폰·이메일로 파기 대상자 검색(조건 1개 이상 필수, 상한 50건). */
  searchDataSubjects(query: DataSubjectQuery) {
    return apiClient.get<ApiResponse<DataSubjectSummary[]>>('/admin/retention/data-subjects', { params: query })
  },

  /** 지원자 1명의 지원서 목록과 지원서별 적격성 판정. */
  getDataSubject(applicantId: number) {
    return apiClient.get<ApiResponse<DataSubjectDetail>>(`/admin/retention/data-subjects/${applicantId}`)
  },

  /** 비가역 파기. 지원자 1명의 모든 지원서와 계정을 지운다. */
  forcePurge(request: ForcedPurgeRequest) {
    return apiClient.post<ApiResponse<PurgeBatchDetail>>('/admin/retention/purge-batches/force', request)
  },

  /** 파기 배치 이력 목록(페이지). */
  getPurgeBatches(page: number, size: number) {
    return apiClient.get<ApiResponse<PageResponse<PurgeBatchSummary>>>('/admin/retention/purge-batches', {
      params: { page, size },
    })
  },

  /** 파기 배치 1건의 상세(지원서별 처리 결과 포함). */
  getPurgeBatch(batchId: number) {
    return apiClient.get<ApiResponse<PurgeBatchDetail>>(`/admin/retention/purge-batches/${batchId}`)
  },
}
