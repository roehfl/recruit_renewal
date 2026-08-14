export interface AwardItem {
  awardId?: number
  awardName: string
  awardingOrganization: string
  awardDate: string
  description?: string
}

export interface AwardRequestItem {
  awardName: string
  awardingOrganization: string
  awardDate: string
  description?: string
  sortOrder: number
}

export interface AwardReplaceRequest {
  awards: AwardRequestItem[]
}

export interface AwardResponse {
  awardId: number
  awardName: string
  awardingOrganization: string
  awardDate: string
  description?: string
  sortOrder: number
}
