export type GapType = 'EDUCATION' | 'CAREER' | 'OTHER'

// 폼 입력용 항목(gapType은 미선택 '' 허용)
export interface GapPeriodItem {
  gapPeriodId?: number
  startDate: string
  endDate: string
  gapType: GapType | ''
  reason: string
  description?: string
}

export interface GapPeriodRequestItem {
  startDate: string
  endDate: string
  gapType: GapType
  reason: string
  description?: string
  sortOrder: number
}

export interface GapPeriodReplaceRequest {
  gapPeriods: GapPeriodRequestItem[]
}

export interface GapPeriodResponse {
  gapPeriodId: number
  startDate: string
  endDate: string
  gapType: GapType
  reason: string
  description?: string
  sortOrder: number
}
