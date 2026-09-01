// 폼 입력용 항목(서버 응답의 languageId를 보존)
export interface LanguageItem {
  languageId?: number
  languageCode?: string
  languageName: string
  testCode?: string
  testName: string
  scoreOrGrade?: string
  registrationNumber?: string
  examDate: string
  expiredDate?: string
  issuingOrganization?: string
}

// POST 요청 항목(sortOrder 포함)
export interface LanguageRequestItem {
  languageCode: string
  languageName: string
  testCode: string
  testName: string
  scoreOrGrade?: string
  registrationNumber?: string
  examDate: string
  expiredDate?: string
  issuingOrganization?: string
  sortOrder: number
}

export interface LanguageReplaceRequest {
  languages: LanguageRequestItem[]
}

export interface LanguageResponse {
  languageId: number
  languageCode?: string
  languageName: string
  testCode?: string
  testName: string
  scoreOrGrade?: string
  registrationNumber?: string
  examDate: string
  expiredDate?: string
  issuingOrganization?: string
  sortOrder: number
}
