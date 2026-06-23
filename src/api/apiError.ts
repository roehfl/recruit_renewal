import axios from 'axios'
import type { ApiResponse } from '@/types/api'

export function getApiErrorMessage(
  error: unknown,
  fallback = '처리 중 오류가 발생했습니다.',
): string {
  if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
    const responseMessage = error.response?.data?.message

    if (responseMessage && responseMessage.trim().length > 0) {
      return responseMessage
    }

    if (error.response?.status === 400) {
      return '요청값을 확인해주세요.'
    }

    if (error.response?.status === 401) {
      return '로그인이 필요합니다.'
    }

    if (error.response?.status === 403) {
      return '권한이 없습니다.'
    }

    if (error.response?.status && error.response.status >= 500) {
      return '서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.'
    }

    if (error.message) {
      return error.message
    }
  }

  if (error instanceof Error && error.message) {
    return error.message
  }

  return fallback
}