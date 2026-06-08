export type ApplicationSectionType =
  | 'BASIC_INFO'
  | 'MILITARY'
  | 'EDUCATION'
  | 'CAREER'
  | 'CERTIFICATE'
  | 'LANGUAGE'
  | 'AWARD'
  | 'GAP_PERIOD'
  | 'QUESTION_ANSWER'
  | 'ATTACHMENT'

export type applicationStatus = 
  | 'DRAFT'
  | 'SUBMITTED'
  | 'WITHDRAWN'

export type latestResultStatus =
  | 'PENDING'

export interface ApplicationFormItem {
  sectionType: ApplicationSectionType
  sectionName?: string
  required: boolean
  sortOrder: number
}

export interface ApplicationFormPage {
  pageNo: number
  title: string
  description?: string | null
  sortOrder: number
  items: ApplicationFormItem[]
}

export interface ApplicationFormPageResponse {
  applicationId: number
  jobPostingId: number
  jobPostingTitle?: string
  postingTitle?: string
  jobPositionId?: number
  jobPositionName?: string
  accepting?: boolean
  editable?: boolean
  status?: string
  applicationStatus?: string
  pages?: ApplicationFormPage[]
  sections?: ApplicationFormItem[]
}

export interface SectionActionHandle {
  saveDraft?: () => Promise<void> | void
  validateBeforeSubmit?: () => Promise<boolean> | boolean
}

export interface SectionComponentProps {
  applicationId: number
  section: ApplicationFormItem
  page: ApplicationFormPage
  editable: boolean
  formPage: ApplicationFormPageResponse
}

export interface MyApplicationListItem {
  applicationId: number
  jobPostingId: number
  jobPostingTitle?: string
  postingTitle?: string
  jobPositionId?: number
  jobPositionName?: string
  applicationStatus?: applicationStatus
  createdAt?: string
  submittedAt?: string
  withdrawnAt?: string 
  receptionStartDateTime?: string
  receptionEndDateTime?: string
  accepting?: boolean
  announcedResultCount?: number
  latestAnnouncedStagName?: string
  latestResultStatus?: string
}

export interface MyApplicationList {
  content: MyApplicationListItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface ApplicationSearchParams {
  page: number
  size: number
}