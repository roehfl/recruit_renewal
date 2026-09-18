import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type {
  InterviewSearchParams,
  AdminInterviewSummaryResponse,
  AdminInterviewDetailResponse,
  AdminInterviewScheduleRow,
  InterviewScheduleSearchParams,
  InterviewScheduleUploadResponse,
} from '@/types/admin/interview'

// 기본 10초로는 엑셀 왕복이 끊길 수 있다. 다운로드도 백엔드가 xlsx 를 다 만든 뒤에야 응답을 시작한다.
const EXCEL_TIMEOUT_MS = 120000

export const adminInterviewApi = {
  getInterviews(jobPostingId: number, params: InterviewSearchParams ) {
    return apiClient.get<ApiResponse<AdminInterviewSummaryResponse[]>>(`/admin/job-postings/${jobPostingId}/interviews`, {
      params: {
        stageId: params.stageId,
        status: params.status,
        from: params.from,
        to: params.to,
      },
    })
  },
  getInterview(interviewId: number) {
      return apiClient.get<ApiResponse<AdminInterviewDetailResponse[]>>(`/admin/interviews/${interviewId}`)
    },

  /* ---- 면접 스케줄링(엑셀 전용 입력) ---- */

  /** 단계의 면접에 배정된 지원자 행. 조 → 면접순서 순 */
  getSchedules(jobPostingId: number, params: InterviewScheduleSearchParams) {
    return apiClient.get<ApiResponse<AdminInterviewScheduleRow[]>>(
      `/admin/job-postings/${jobPostingId}/interview-schedules`,
      { params },
    )
  },

  /** 조회 조건 그대로 행을 채운 xlsx. 다시 업로드할 수 있는 양식이다 */
  exportSchedules(jobPostingId: number, params: InterviewScheduleSearchParams) {
    return apiClient.get<Blob>(`/admin/job-postings/${jobPostingId}/interview-schedules/export`, {
      params,
      responseType: 'blob',
      timeout: EXCEL_TIMEOUT_MS,
    })
  },

  /** 헤더만 있는 업로드 양식 */
  downloadScheduleTemplate(jobPostingId: number) {
    return apiClient.get<Blob>(`/admin/job-postings/${jobPostingId}/interview-schedules/upload-template`, {
      responseType: 'blob',
      timeout: EXCEL_TIMEOUT_MS,
    })
  },

  /**
   * 단계 스케줄 전체 교체 + 즉시 확정. 행 오류가 있으면 400 이고 **응답 본문 data 에 오류 목록이 들어 있다**.
   * 파일 자체가 거부되면 data 없는 400.
   */
  uploadSchedules(jobPostingId: number, stageId: number, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return apiClient.post<ApiResponse<InterviewScheduleUploadResponse>>(
      `/admin/job-postings/${jobPostingId}/interview-schedules/upload`,
      formData,
      { params: { stageId }, timeout: EXCEL_TIMEOUT_MS },
    )
  },
}