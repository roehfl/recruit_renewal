/*
 * 지원자 본인 면접 일정(interview 카드 `GET /applicant/interviews`). 확정·취소된 면접만 내려온다.
 * 응답의 method·onlineMeetingUrl 은 쓰지 않는다 — 면접은 엑셀 업로드로만 만들고 항상 대면(IN_PERSON)이다.
 */

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
  locationName: string | null
  roomName: string | null
  status: 'CONFIRMED' | 'CANCELLED'
  cancelled: boolean
}
