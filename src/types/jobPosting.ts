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