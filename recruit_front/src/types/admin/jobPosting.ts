export interface AdminJobPostingListItem {
  id: number
  title: string
  postingType: string
  status: 'DRAFT' | 'PUBLISHED' | 'CLOSED'
  receptionStatus: 'UPCOMING' | 'ACCEPTING' | 'CLOSED'
  accepting: boolean
  receptionStartDateTime: string
  receptionEndDateTime: string
  positionCount: number
  jobPositions : AdminJobPosition[]
}

export interface AdminJobPosition {
  id: number | null
  positionName: string
  applicationType: 'NEW_GRADUATE' | 'EXPERIENCED' | 'NEW_GRADUATE_OR_EXPERIENCED'
  jobGroup: string | null
  jobTitle: string | null
  workLocation: string | null
  employmentType: 'FULL_TIME' | 'CONTRACT' | 'INTERN' | 'FREELANCE' | 'PART_TIME' | 'ETC'
  sortOrder: number
}

export type AdminJobPostingStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED'

export interface AdminApplicationFormConfig {
  useEducation: boolean
  requireEducation: boolean | null
  useCareer: boolean
  requireCareer: boolean | null
  useCertificate: boolean
  requireCertificate: boolean | null
  useLanguage: boolean
  requireLanguage: boolean | null
  useMilitary: boolean
  requireMilitary: boolean | null
  useAward: boolean
  requireAward: boolean | null
  useGapPeriod: boolean
  requireGapPeriod: boolean | null
  useAttachment: boolean
}
export interface AdminJobPostingDetail {
  id: number
  title: string
  postingType: string
  summary: string | null
  contentHtml: string | null
  receptionStartDateTime: string
  receptionEndDateTime: string
  receptionStatus: 'UPCOMING' | 'ACCEPTING' | 'CLOSED'
  accepting: boolean
  status: AdminJobPostingStatus
  visible: boolean
  pinned: boolean
  displayOrder: number
  displayStartDateTime: string | null
  displayEndDateTime: string | null
  publishedAt: string | null
  closedAt: string | null
  createdAt: string
  updatedAt: string
  positionCount: number
  jobPositions: AdminJobPosition[]
  applicationFormConfig: AdminApplicationFormConfig
}

export interface AdminJobPostingListItem {
  id: number
  title: string
  postingType: string
  status: 'DRAFT' | 'PUBLISHED' | 'CLOSED'
  receptionStatus: 'UPCOMING' | 'ACCEPTING' | 'CLOSED'
  accepting: boolean
  receptionStartDateTime: string
  receptionEndDateTime: string
  positionCount: number
}