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
  languageName: string | undefined
  workLocation: string | undefined
  birthDateTo: string | undefined
  applicationType: string | undefined
  name: string | undefined
  phoneNumber: string | undefined
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
  /** 백엔드는 설명이 없으면 null 을 내려준다. */
  description: string | null
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

/** POST /admin/job-postings/{id}/application-form-layout 요청. 레이아웃 전체를 치환한다. */
export interface ApplicationFormLayoutSaveRequest {
  pages: {
    pageNo: number
    title: string
    description: string | null
    sortOrder: number
    items: { sectionType: sectionType; sortOrder: number }[]
  }[]
}

/** GET .../application-form-layout/preview 응답. 활성 섹션만, 빈 페이지는 제외된 지원자 관점 구성이다. */
export interface ApplicationFormLayoutPreviewResponse {
  jobPostingId: number
  jobPostingTitle: string
  pages: {
    pageNo: number
    title: string
    description: string | null
    sortOrder: number
    items: {
      sectionType: sectionType
      sectionName: string
      required: boolean
      sortOrder: number
    }[]
  }[]
}

export type ApplicationFormConfigState = 'MISSING' | 'RELAYOUT_REQUIRED' | 'DEFAULT' | 'OK'

/** GET /admin/application-forms 검색 조건. 값이 없으면 해당 조건을 보내지 않는다. */
export interface AdminApplicationFormSummarySearchRequest {
  status?: 'DRAFT' | 'PUBLISHED' | 'CLOSED'
  receptionStatus?: 'UPCOMING' | 'ACCEPTING' | 'CLOSED'
  configState?: ApplicationFormConfigState
  editableOnly?: boolean
  keyword?: string
}

/** 지원서 설정 현황판의 한 행. */
export interface AdminApplicationFormSummary {
  jobPostingId: number
  title: string
  postingType: string
  status: 'DRAFT' | 'PUBLISHED' | 'CLOSED'
  receptionStatus: 'UPCOMING' | 'ACCEPTING' | 'CLOSED'
  receptionStartDateTime: string
  receptionEndDateTime: string
  sectionSummary: { enabledCount: number; requiredCount: number }
  activeQuestionCount: number
  requiredQuestionCount: number
  layoutStored: boolean
  pageCount: number
  configState: ApplicationFormConfigState
  editable: boolean
  updatedAt: string
}
