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

export interface JobPostingDetail {
  id: number
  title: string
  contentHtml: string
}

export interface MyJobPostingListItem {
  totalElements: number
  content: MyJobPostingDetailListItem[]
}

export interface MyJobPostingDetailListItem {
  applicationId: number
  jobPostingId: number
  jobPostingTitle: string
  status: JobPostingStatusCode
  jobPositionId: number
  jobPositionName: string
  summary: string
  startDate: string
  endDate: string
  postingType: string
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
