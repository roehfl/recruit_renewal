export interface CommonCodeItems {
  id: number
  groupCode: string
  code: string
  displayName: string
  sortOrder: number
  active: boolean
  description: string
}

export interface CommonCodeResponse {
  data: CommonCodeItems[]
  message: string
  success: boolean
}