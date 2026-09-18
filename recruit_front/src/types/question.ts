export interface QuestionTemplateRequest {
  title: string
  questionText: string
  helperText: string | null
  category: 'SELF_INTRODUCTION' | 'GENERAL' | 'JOB_SPECIFIC' | 'ETC'
  answerType: 'SHORT_TEXT' | 'LONG_TEXT'
  defaultRequired: boolean
  defaultMaxLength: number
}

export interface QuestionTemplateItem {
  templateId: number,
  title: string,
  questionText: string ,
  helperText: string | null,
  category: 'SELF_INTRODUCTION' | 'GENERAL' | 'JOB_SPECIFIC' | 'ETC',
  answerType: 'SHORT_TEXT' | 'LONG_TEXT',
  defaultRequired: boolean,
  defaultMaxLength: number,
  active: boolean | null,
  createdAt: string | null,
  updatedAt: string | null
}

export interface QuestionRequest {
  /** 생성 전용. 템플릿에서 불러온 질문이면 템플릿 id 를 보내 출처를 남긴다(수정 API 는 받지 않는다). */
  questionTemplateId?: number | null
  questionText: string
  helperText: string | null,
  category: string,
  answerType: string,
  required: boolean,
  minLength: number | null,
  maxLength: number,
  sortOrder: number,
}

export interface QuestionItem {
    questionId: number | null,
    questionTemplateId: number | null,
    questionText: string | null,
    helperText: string | null,
    category: "SELF_INTRODUCTION" | "GENERAL" | "JOB_SPECIFIC" | "ETC" | null,
    answerType: "SHORT_TEXT" | "LONG_TEXT" | null,
    required: boolean | null,
    minLength: number | null,
    maxLength: number | null,
    sortOrder: number | null,
    createdAt: string | null,
    updatedAt: string | null
}

export interface QuestionList {
  questions: QuestionItem
}

export interface QuestionReOrderItem {
  questionId: number,
  sortOrder: number
}

export interface QuestionReOrderRequest {
  questions: QuestionReOrderItem[]
} 

export interface QuestionForm {
  /** 템플릿에서 불러온 질문일 때만 채운다. */
  questionTemplateId?: number | null
  category: string | null
  required: boolean | null
  answerType: string | null
  maxLength: number | null
  minLength: number | null
  questionText: string | null
  helperText: string | null
}