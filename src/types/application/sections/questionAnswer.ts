export type QuestionAnswerType = 'SHORT_TEXT' | 'LONG_TEXT'

export type QuestionCategory = 'SELF_INTRODUCTION' | 'GENERAL' | 'JOB_SPECIFIC' | 'ETC'

// 폼에서 다루는 질문 + 가변 답변(answerText)
export interface ApplicationQuestionItem {
  questionId: number
  questionText: string
  helperText?: string
  category: QuestionCategory
  answerType: QuestionAnswerType
  required: boolean
  minLength?: number
  maxLength?: number
  sortOrder: number
  answerId?: number
  answerText: string
}

export interface ApplicationAnswerRequestItem {
  questionId: number
  answerText: string
}

export interface ApplicationAnswerReplaceRequest {
  answers: ApplicationAnswerRequestItem[]
}

// GET 응답 항목(서버 원본; nullable 필드 포함)
export interface ApplicationQuestionResponse {
  questionId: number
  questionText: string
  helperText?: string | null
  category: QuestionCategory
  answerType: QuestionAnswerType
  required: boolean
  minLength?: number | null
  maxLength?: number | null
  sortOrder: number
  answerId?: number | null
  answerText?: string | null
  updatedAt?: string | null
}
