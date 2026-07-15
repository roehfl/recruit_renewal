import type { UploadFile } from 'ant-design-vue'
import type { attachmentType, sectionType, AttachmentFileRequest, AttachmentResponse } from './basicInfo'

export type { attachmentType, sectionType, AttachmentFileRequest, AttachmentResponse }

/** 첨부 섹션 전용 sectionType. 증명사진(BASIC_INFO)과 구분하는 기준. */
export const ATTACHMENT_SECTION_TYPE: sectionType = 'ATTACHMENT'

/**
 * 첨부 섹션 드롭다운에 노출하는 종류.
 * 백엔드 AttachmentType enum의 부분집합이다. RESUME/TRANSCRIPT/CAREER_CERTIFICATE는
 * enum에 남아 있으나 이 화면에서는 선택지로 제공하지 않는다.
 */
export const ATTACHMENT_TYPE_OPTIONS: { value: attachmentType; label: string }[] = [
  { value: 'CAREER_DESCRIPTION', label: '경력기술서' },
  { value: 'PORTFOLIO', label: '포트폴리오' },
  { value: 'CERTIFICATE_PROOF', label: '자격증명' },
  { value: 'GRADUATION_CERTIFICATE', label: '졸업증명' },
  { value: 'EMPLOYMENT_CERTIFICATE', label: '재직증명' },
  { value: 'LANGUAGE_SCORE_REPORT', label: '어학점수' },
  { value: 'ETC', label: '기타' },
]

export interface AttachmentDeleteResponse {
  applicationId: number
  attachmentId: number
  deleted: boolean
  physicalDeleteRequested: boolean
  message: string
}

/** 첨부 섹션의 화면 행. 저장 전 행은 attachmentId가 없다. */
export interface AttachmentItem {
  attachmentId?: number
  attachmentType: attachmentType | undefined
  fileList: UploadFile[]
}
