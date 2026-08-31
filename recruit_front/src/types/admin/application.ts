export type sectionType =
    "BASIC_INFO" | "EDUCATION" | "CAREER" | "CERTIFICATE" | "LANGUAGE" | 
    "MILITARY" | "AWARD" | "GAP_PERIOD" | "QUESTION_ANSWER" | "ATTACHMENT" 

export interface AdminApplicationSummaryResponse {
  applicationId: number
  applicantId: number
  applicantNameSnapshot: string
  jobPostingId: number
  jobPostingTitleSnapshot: string
  jobPositionId: number
  jobPositionNameSnapshot: string
  status: 'DRAFT' | 'SUBMITTED' | 'WITHDRAWN'
  submittedAt: string
  withdrawnAt: string
  createdAt: string
  updatedAt: string
  jobGroup: string
  jobTitle: string
  workLocation: string
  birthDate: string
  age: number
  finalEducationLevel: 'HIGH_SCHOOL' | 'COLLEGE' | 'UNIVERSITY' | 'MASTER' | 'DOCTOR'
  finalSchoolName: string
  stageType: 'DOCUMENT' | 'FIRST_INTERVIEW' | 'SECOND_INTERVIEW' | 'FINAL_INTERVIEW' | 'ETC'
  stageResultStatus: 'PENDING' | 'PASSED' | 'FAILED' | 'ABSENT' | 'WITHDRAWN' | 'HOLD'
  careerDescriptionDownloadUrl: string
}

export interface AdminApplicationSearchRequest {
  graduationStatus: string | undefined
  jobPositionId: number | undefined
  certificateName: string | undefined
  jobGroup: string | undefined
  languageName: string | undefined
  workLocation: string | undefined
  birthDateTo: string | undefined
  applicationType: string | undefined
  name: string | undefined
  languageLevel: string | undefined
  stageResultStatus: string | undefined
  finalSchoolCondition: string | undefined
  finalEducationLevel: string | undefined
  status: string | undefined
  birthDateFrom: string | undefined
  stageType: string | undefined
  schoolName: string | undefined
}

export interface AdminApplicationDetailResponse {
  applicationId: number
  applicantId: number
  applicantNameSnapshot: string
  jobPostingId: number
  jobPostingTitleSnapshot: string
  jobPositionId: number
  jobPositionNameSnapshot: string
  /** 지원자가 선택한 근무지 표시명. 근무지 후보가 없는 모집분야면 null. */
  workLocationNameSnapshot: string | null
  status: 'DRAFT' | 'SUBMITTED' | 'WITHDRAWN'
  submittedAt: string
  withdrawnAt: string
  createdAt: string
  updatedAt: string
}

export interface ItemResponse { 
  sectionType: sectionType
  sectionName: string
  sortOrder: number
  enabled: boolean
  required: boolean
  placed: boolean
}

export interface availableSectionsItem { 
  sectionType: sectionType
  sectionName: string
  sortOrder: number
  enabled: boolean
  required: boolean
  placed: boolean
  source: string
}

export interface formLayoutPageItem {
  pageNo: number
  title: string
  description: string
  sortOrder: number
  items: ItemResponse[]
}

export interface ApplicationFormLayoutResponse {
  jobPostingId: number
  layoutStored: boolean
  editable: boolean
  pages: formLayoutPageItem[]
  availableSections: availableSectionsItem[]
}