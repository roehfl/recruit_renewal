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

/* 생성 요청. groupCode/code는 생성 시에만 지정할 수 있고 이후 불변이다. */
export interface CommonCodeCreateRequest {
  groupCode: string
  code: string
  displayName: string
  sortOrder: number
  active: boolean
  description: string | null
}

/* 수정 요청. groupCode/code는 불변이라 포함하지 않는다. active=false가 soft delete다. */
export interface CommonCodeUpdateRequest {
  displayName: string
  sortOrder: number
  active: boolean
  description: string | null
}