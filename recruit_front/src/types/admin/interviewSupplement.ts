/* 면접 추가사항(관리자). 시각은 백엔드 LocalDateTime ISO 문자열이다. */

export interface AdminInterviewSupplementQuestion {
  questionId: number
  content: string
  sortOrder: number
}

/** 면접단계의 추가사항 세트 상태. 꺼져 있으면 enabled=false, questions 빈 배열 */
export interface AdminInterviewSupplement {
  stageId: number
  enabled: boolean
  questions: AdminInterviewSupplementQuestion[]
  answeredApplicantCount: number
}

/** 대상 지원자 1명. 도착시간이 없어 기본값을 못 만들면 startDateTime·endDateTime 이 null */
export interface AdminInterviewSupplementCandidate {
  jobApplicationId: number
  applicantName: string
  interviewId: number
  groupName: string
  candidateOrder: number | null
  arrivalDateTime: string | null
  interviewStartDateTime: string
  startDateTime: string | null
  endDateTime: string | null
  customized: boolean
  answeredCount: number
  lastSavedAt: string | null
}

export interface AdminInterviewSupplementAnswerItem {
  questionId: number
  content: string
  answerText: string | null
  savedAt: string | null
}

export interface AdminInterviewSupplementAnswerDetail {
  jobApplicationId: number
  applicantName: string
  groupName: string
  candidateOrder: number | null
  startDateTime: string | null
  endDateTime: string | null
  answeredCount: number
  lastSavedAt: string | null
  items: AdminInterviewSupplementAnswerItem[]
}

export interface InterviewSupplementWindowSaveRequest {
  jobApplicationIds: number[]
  startDateTime: string
  endDateTime: string
}
