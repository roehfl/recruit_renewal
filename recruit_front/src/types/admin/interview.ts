export type StageType = 'DOCUMENT' | 'FIRST_INTERVIEW' | 'SECOND_INTERVIEW' | 'FINAL_INTERVIEW' | 'ETC'
export type method = 'IN_PERSON' | 'ONLINE' | 'HYBRID' | 'OTHER'
export type status = 'DRAFT' | 'CONFIRMED' | 'CANCELLED'
export type role = 'CANDIDATE' | 'INTERVIEWER'
export type participantStatus = 'ASSIGNED' | 'CANCELLED'

export interface InterviewCreateRequest {
  stageId: number | null
  groupName: string | undefined
  startDateTime: string | undefined
  endDateTime: string | undefined
  method: method
  locationName?: string
  roomName?: string
  onlineMeetingUrl?: string
  memo?: string
}

export interface InterviewSearchParams {
  stageId?: number
  status?: string
  from?: string
  to?: string
  jobPositionId?: number
  workLocation?: string
  applicationType?: string
  groupName?: string
}

export interface AdminInterviewSummaryResponse {
  interviewId: number
  jobPostingId: number
  jobPostingTitle: string
  stageId: number
  stageName: string
  stageType: StageType
  groupName: string
  startDateTime: string
  endDateTime: string
  method: method
  locationName: string
  roomName: string
  onlineMeetingUrl: string
  status: status
  candidateCount: number
  interviewerCount: number
}

export interface candidateList {
  participantId: number
  role: role
  jobApplicationId: number
  applicantId: number
  applicantName: string
  jobPositionId: number
  jobPositionName: string
  employeeId: number
  employeeName: string
  departmentName: string
  participantStatus: participantStatus
  sortOrder: number
}

export interface interviewerList {
  participantId: number
  role: role
  jobApplicationId: number
  applicantId: number
  applicantName: string
  jobPositionId: number
  jobPositionName: string
  employeeId: number
  employeeName: string
  departmentName: string
  participantStatus: participantStatus
  sortOrder: number
}

export interface AdminInterviewDetailResponse {
  interviewId: number
  jobPostingId: number
  jobPostingTitle: string
  stageId: number
  stageName: string
  stageType: StageType
  groupName: string
  startDateTime: string
  endDateTime: string
  method: method
  locationName: string
  roomName: string
  onlineMeetingUrl: string
  memo: string
  status: status
  candidates: candidateList[]
  interviewers: interviewerList[]
}