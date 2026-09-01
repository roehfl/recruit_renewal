export type JobPostingStatusCode = 'OPEN' | 'CLOSED'

export interface JobPostingListItem {
  id: number
  title: string
  type: string
  description: string
  status: JobPostingStatusCode
  startDate: string
  endDate: string
  receptionStatus: string
  postingType: string
  receptionStartDateTime: string
  receptionEndDateTime: string
}

export interface JobPostingSearchParams {
  page: number
  size: number
  type: string
  status: JobPostingStatusCode
  keyword: string
}

export interface JobPostingImage {
  id: number
  altText: string
  sortOrder: number
  contentType: string
  fileSize: number
}

/** 공개 공고 상세의 모집분야. 지원 시작·지원분야 변경 드롭다운의 소스다. */
export interface JobPositionPublicOption {
  id: number
  positionName: string
  workLocations: WorkLocationOption[]
}

export interface JobPostingDetail {
  id: number
  title: string
  contentHtml: string
  images: JobPostingImage[]
  jobPositions?: JobPositionPublicOption[]
}

export interface MyJobPostingListItem {
  totalElements: number
  content: MyJobPostingDetailListItem[]
}

export interface MyJobPostingDetailListItem {
  applicationId: number
  jobPostingId: number
  jobPostingTitle: string
  /** 지원서 상태. 백엔드 MyApplicationResponse.applicationStatus 와 대응한다. */
  applicationStatus: 'DRAFT' | 'SUBMITTED' | 'WITHDRAWN'
  jobPositionId: number
  jobPositionName: string
}
/*
 * 관리자 공고 목록 항목(GET /admin/job-postings). 지원자 화면용 JobPostingListItem과 필드가 달라 분리한다.
 * 백엔드 JobPostingListResponse의 필드 중 관리자 화면이 실제로 쓰는 것만 선언한다.
 */
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

export type AdminJobPostingStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED'

/** 모집분야의 후보 근무지(CommonCode 그룹 WORK_LOCATION). 응답 전용 모양이다. */
export interface WorkLocationOption {
  code: string
  name: string
}

export interface AdminJobPositionForm {
  positionName: string
  applicationType: 'NEW_GRADUATE' | 'EXPERIENCED' | 'NEW_GRADUATE_OR_EXPERIENCED'
  jobTitle: string | null
  /** 후보 근무지 코드 목록. 비면 근무지 선택이 없는 모집분야다. */
  workLocationCodes: string[]
  employmentType: 'FULL_TIME' | 'CONTRACT' | 'INTERN' | 'FREELANCE' | 'PART_TIME' | 'ETC'
  sortOrder: number
}

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

/** POST /admin/job-postings 의 request JSON part. contentHtml은 deprecated라 보내지 않는다. */
export interface AdminJobPostingSaveRequest {
  title: string
  postingType: string | null
  summary: string | null
  receptionStartDateTime: string
  receptionEndDateTime: string
  displayStartDateTime: string | null
  displayEndDateTime: string | null
  visible: boolean
  pinned: boolean
  displayOrder: number
  jobPositions: AdminJobPositionForm[]
  applicationFormConfig: AdminApplicationFormConfig
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
  /** 응답은 코드 목록이 아니라 code+name 쌍으로 내려온다. */
  jobPositions: (Omit<AdminJobPositionForm, 'workLocationCodes'> & {
    id: number
    workLocations: WorkLocationOption[]
  })[]
  applicationFormConfig: AdminApplicationFormConfig
  images: JobPostingImage[]
}

export interface NewPostingImage {
  file: File
  altText: string
}
