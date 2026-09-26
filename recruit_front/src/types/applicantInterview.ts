/* 지원자 본인 면접 일정(interview 카드 `GET /applicant/interviews`). 확정·취소된 면접만 내려온다. */

export type InterviewMethod = 'IN_PERSON' | 'ONLINE' | 'HYBRID' | 'OTHER'

export interface ApplicantInterviewSummary {
  interviewId: number
  applicationId: number
  jobPostingId: number
  jobPostingTitle: string
  positionId: number
  positionName: string
  stageId: number
  stageName: string
  groupName: string
  startDateTime: string
  arrivalDateTime: string | null
  method: InterviewMethod
  locationName: string | null
  roomName: string | null
  onlineMeetingUrl: string | null
  status: 'CONFIRMED' | 'CANCELLED'
  cancelled: boolean
}
