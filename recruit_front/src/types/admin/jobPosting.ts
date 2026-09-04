import type { WorkLocationOption } from '@/types/jobPosting'

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
  jobTitle: string | null
  workLocations: WorkLocationOption[]
  employmentType: 'FULL_TIME' | 'CONTRACT' | 'INTERN' | 'FREELANCE' | 'PART_TIME' | 'ETC'
  sortOrder: number
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
