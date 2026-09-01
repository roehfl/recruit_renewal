import type { attachmentType, sectionType, AttachmentFileRequest, AttachmentResponse } from './basicInfo'

export type { attachmentType, sectionType, AttachmentFileRequest, AttachmentResponse }

export interface AttachmentDeleteResponse {
  applicationId: number
  attachmentId: number
  deleted: boolean
  physicalDeleteRequested: boolean
  message: string
}
