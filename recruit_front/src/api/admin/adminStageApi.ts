import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type { PageResponse } from '@/types/page'
import type {
  AdminStageResult,
  StageListItem,
  StageResultBulkUpdateRequest,
  StageResultBulkUpdateResponse,
  StageResultCorrectionHistory,
  StageResultCorrectionRequest,
  StageReorderRequest,
  StageResultInitializeResponse,
  StageResultUploadCommit,
  StageResultUploadPreview,
  StageSaveRequest,
} from '@/types/admin/stage'

// 기본 10초로는 대량 엑셀 왕복이 끊길 수 있다. 다운로드도 백엔드가 xlsx 를 다 만든 뒤에야 응답을 시작한다.
const EXCEL_TIMEOUT_MS = 120000

/** 전형결과 관리 화면 전용 API 모듈. 단계 조회·라이프사이클과 결과 조회·판정을 담당한다. */
export const adminStageApi = {
  /** 공고의 전형 단계 목록. stageOrder 오름차순이다. */
  getStages(jobPostingId: number) {
    return apiClient.get<ApiResponse<StageListItem[]>>(`/admin/job-postings/${jobPostingId}/stages`)
  },

  /** 단계의 전형 결과 전체. 비페이징이라 필터·페이징은 화면에서 처리한다. */
  getResults(stageId: number) {
    return apiClient.get<ApiResponse<AdminStageResult[]>>(`/admin/stages/${stageId}/results`)
  },

  /** 제출 완료 지원서를 대상자로 등록한다. 기존 행은 유지하고 신규만 추가(멱등). 단계 READY·IN_PROGRESS 에서만 가능. */
  initializeResults(stageId: number) {
    return apiClient.post<ApiResponse<StageResultInitializeResponse>>(
      `/admin/stages/${stageId}/results/initialize`,
    )
  },

  /** 결과 일괄 판정. 단계 IN_PROGRESS 에서만 가능하고 resultStatus 에 PENDING 을 보낼 수 없다. 동시 수정 시 409. */
  bulkUpdateResults(stageId: number, request: StageResultBulkUpdateRequest) {
    return apiClient.post<ApiResponse<StageResultBulkUpdateResponse>>(
      `/admin/stages/${stageId}/results/bulk`,
      request,
    )
  },

  /** 전형 시작(READY → IN_PROGRESS). 공고가 게시 중이어야 한다. */
  startStage(jobPostingId: number, stageId: number) {
    return apiClient.post<ApiResponse<number>>(
      `/admin/job-postings/${jobPostingId}/stages/${stageId}/start`,
    )
  },

  /** 결과 발표(IN_PROGRESS → RESULT_ANNOUNCED). 대기 결과가 하나라도 남아 있으면 백엔드가 거부한다. */
  announceStage(jobPostingId: number, stageId: number) {
    return apiClient.post<ApiResponse<number>>(
      `/admin/job-postings/${jobPostingId}/stages/${stageId}/announce`,
    )
  },

  /** 단계 마감(RESULT_ANNOUNCED → CLOSED). */
  closeStage(jobPostingId: number, stageId: number) {
    return apiClient.post<ApiResponse<number>>(
      `/admin/job-postings/${jobPostingId}/stages/${stageId}/close`,
    )
  },

  /* ---- 단계 설정 ---- */

  /** 단계 생성. 공고가 마감이면 거부된다. 순서·최종단계 중복도 거부된다. */
  createStage(jobPostingId: number, request: StageSaveRequest) {
    return apiClient.post<ApiResponse<number>>(
      `/admin/job-postings/${jobPostingId}/stages`,
      request,
    )
  },

  /**
   * 단계 수정. READY 는 전체, IN_PROGRESS 는 발표일시만 허용된다(계약 변경 2).
   * 잠긴 필드도 형식 검증을 통과해야 하므로 현재 값을 그대로 실어 보낸다.
   */
  updateStage(jobPostingId: number, stageId: number, request: StageSaveRequest) {
    return apiClient.post<ApiResponse<number>>(
      `/admin/job-postings/${jobPostingId}/stages/${stageId}`,
      request,
    )
  },

  /** 단계 삭제. READY 단계만 지울 수 있다. */
  deleteStage(jobPostingId: number, stageId: number) {
    return apiClient.post<ApiResponse<number>>(
      `/admin/job-postings/${jobPostingId}/stages/${stageId}/delete`,
    )
  },

  /** 순서 일괄 변경. 모든 단계를 보내야 하고 전부 READY 여야 한다. */
  reorderStages(jobPostingId: number, request: StageReorderRequest) {
    return apiClient.post<ApiResponse<StageListItem[]>>(
      `/admin/job-postings/${jobPostingId}/stages/reorder`,
      request,
    )
  },

  /** 빈 상태에서 "제출 완료 n건"을 보여주기 위한 건수 조회. totalElements 만 쓴다. */
  countSubmittedApplications(jobPostingId: number) {
    return apiClient.get<ApiResponse<PageResponse<unknown>>>('/admin/applications', {
      params: { jobPostingId, status: 'SUBMITTED', page: 0, size: 1 },
    })
  },

  /* ---- 엑셀 왕복 ---- */

  /** 현재 결과가 프리필된 업로드 템플릿(xlsx). 업로드는 이 파일만 받는다. */
  downloadUploadTemplate(stageId: number) {
    return apiClient.get<Blob>(`/admin/stages/${stageId}/results/upload-template`, {
      responseType: 'blob',
      timeout: EXCEL_TIMEOUT_MS,
    })
  },

  /** 결과 목록 xlsx(읽기 전용). 열 구성이 업로드 템플릿과 달라 업로드 소스로 쓸 수 없다. */
  exportResults(stageId: number) {
    return apiClient.get<Blob>(`/admin/stages/${stageId}/results/export`, {
      responseType: 'blob',
      timeout: EXCEL_TIMEOUT_MS,
    })
  },

  /** 업로드 검증·diff. 저장하지 않는다. 파일 자체가 거부되면 data 없는 400. */
  previewUpload(stageId: number, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return apiClient.post<ApiResponse<StageResultUploadPreview>>(
      `/admin/stages/${stageId}/results/upload/preview`,
      formData,
      { timeout: EXCEL_TIMEOUT_MS },
    )
  },

  /**
   * 업로드 적용. all-or-nothing 이라 오류·STALE 이 하나라도 있으면 0건 반영된다.
   * outcome 이 REJECTED_VALIDATION 이면 400, REJECTED_STALE 이면 409 로 오는데
   * **둘 다 응답 본문의 data 에 commit 결과가 들어 있다**(failedRows 포함).
   */
  commitUpload(stageId: number, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return apiClient.post<ApiResponse<StageResultUploadCommit>>(
      `/admin/stages/${stageId}/results/upload/commit`,
      formData,
      { timeout: EXCEL_TIMEOUT_MS },
    )
  },

  /* ---- 발표 후 정정 ---- */

  /** 발표·마감된 단계의 결과를 사유와 함께 고친다. 이력이 append 된다. */
  correctResult(stageId: number, resultId: number, request: StageResultCorrectionRequest) {
    return apiClient.post<ApiResponse<AdminStageResult>>(
      `/admin/stages/${stageId}/results/${resultId}/correct`,
      request,
    )
  },

  /** 정정 이력. 최신순이다. */
  getCorrectionHistories(stageId: number, resultId: number) {
    return apiClient.get<ApiResponse<StageResultCorrectionHistory[]>>(
      `/admin/stages/${stageId}/results/${resultId}/histories`,
    )
  },
}
