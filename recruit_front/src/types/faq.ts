/* 지원자 공개 조회(GET /faqs) — 노출에 필요한 필드만 내려온다. */
export interface PublicFaq {
  id: number
  question: string
  answer: string
}

export interface PublicFaqCategory {
  id: number
  name: string
  faqs: PublicFaq[]
}

/* 관리자 조회 — 비활성 항목까지 포함하고 정렬값도 함께 내려온다. */
export interface FaqCategory {
  id: number
  name: string
  sortOrder: number
  active: boolean
  faqCount: number
}

export interface Faq {
  id: number
  categoryId: number
  question: string
  answer: string
  sortOrder: number
  active: boolean
}

export interface FaqCategorySaveRequest {
  name: string
  active: boolean
}

export interface FaqSaveRequest {
  categoryId: number
  question: string
  answer: string
  active: boolean
}
