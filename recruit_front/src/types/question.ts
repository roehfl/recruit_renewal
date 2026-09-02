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
  questionTemplateId: number | null,
  questionText: string | null,
  helperText: string | null,
  category: "SELF_INTRODUCTION" | "GENERAL" | "JOB_SPECIFIC" | "ETC" | null,
  answerType: "SHORT_TEXT" | "LONG_TEXT" | null,
  required: boolean | null,
  minLength: number | null,
  maxLength: number | null,
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
    active: boolean | null,
    createdAt: string | null,
    updatedAt: string | null
}