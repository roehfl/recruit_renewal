import { apiClient } from '../../client'
import type { ApiResponse } from '@/types/api'
import type {
  AttachmentFileRequest,
  AttachmentResponse,
  AttachmentDeleteResponse,
} from '@/types/application/sections/attachment'

/**
 * 첨부파일 API.
 * basicInfoApi에도 같은 엔드포인트가 선언되어 있으나(증명사진 전용으로 먼저 만들어짐),
 * 그쪽은 건드리지 않고 첨부 섹션은 이 모듈을 쓴다.
 */
export const attachmentApi = {
  /**
   * 지원서의 첨부 전체를 반환한다.
   * 주의: 백엔드에 sectionType 필터가 없어 BASIC_INFO 증명사진까지 함께 내려온다.
   * 첨부 섹션에서 쓸 때는 반드시 sectionType === 'ATTACHMENT'로 필터링할 것.
   */
  getApplicationAttachments(applicationId: number) {
    return apiClient.get<ApiResponse<AttachmentResponse[]>>(`applications/${applicationId}/attachments`)
  },

  postApplicationAttachmentsFile(formData: FormData, params: AttachmentFileRequest) {
    return apiClient.post<ApiResponse<AttachmentResponse>>(
      `applications/${params.applicationId}/attachments/files`,
      formData,
      { params },
    )
  },

  deleteApplicationAttachments(applicationId: number, attachmentId: number) {
    return apiClient.post<ApiResponse<AttachmentDeleteResponse>>(
      `applications/${applicationId}/attachments/${attachmentId}/delete`,
    )
  },

  /** 첨부 원본을 blob으로 받는다. 세션 쿠키가 필요하므로 apiClient를 경유한다. */
  downloadApplicationAttachment(applicationId: number, attachmentId: number) {
    return apiClient.get<Blob>(`applications/${applicationId}/attachments/${attachmentId}/download`, {
      responseType: 'blob',
    })
  },
}
