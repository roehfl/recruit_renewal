export interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
  errors?: ApiFieldError[]
}

export interface ApiFieldError {
  field: string
  message: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}