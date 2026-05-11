export type JobPostingStatusCode = 'OPEN' | 'CLOSED'

export interface JobPostingListItem {
  id: number
  title: string
  type: string
  description: string
  status: JobPostingStatusCode
  startDate: string
  endDate: string
}

export interface JobPostingSearchParams {
  page: number
  size: number
  type: string
  status: JobPostingStatusCode
  keyword: string
}
