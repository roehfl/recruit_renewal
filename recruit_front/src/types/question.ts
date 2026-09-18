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
  category: string | null
  required: boolean | null
  answerType: string | null
  maxLength: number | null
  minLength: number | null
  questionText: string | null
  helperText: string | null
}