/* 면접 추가사항(지원자). 입력 가능 여부는 서버가 판정한 open·remainingSeconds 만 믿는다. */

/** 마이페이지 지원 목록의 "추가사항 입력" 칸. 지원서당 1건 */
export interface ApplicantInterviewSupplementSummary {
  applicationId: number
  stageId: number
  stageName: string
  startDateTime: string
  endDateTime: string
  open: boolean
  remainingSeconds: number
  questionCount: number
  answeredCount: number
}

export interface ApplicantInterviewSupplementQuestion {
  questionId: number
  content: string
  answerText: string | null
}

export interface ApplicantInterviewSupplementForm {
  applicationId: number
  stageId: number
  jobPostingTitle: string
  stageName: string
  endDateTime: string
  remainingSeconds: number
  questions: ApplicantInterviewSupplementQuestion[]
}

export interface ApplicantInterviewSupplementSaveRequest {
  answers: { questionId: number; answerText: string }[]
}

export interface ApplicantInterviewSupplementSaveResponse {
  savedAt: string
  remainingSeconds: number
  answeredCount: number
}
