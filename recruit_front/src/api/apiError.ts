import axios from 'axios'
import type { ApiResponse } from '@/types/api'

export function getApiErrorMessage(
  error: unknown,
  fallback = '처리 중 오류가 발생했습니다.',
): string {
  if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
    // 응답을 받지 못한 경우 axios 영문 메시지(timeout of …ms exceeded, Network Error)를 그대로 보여 주지 않는다.
    if (!error.response) {
      if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
        return '요청 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.'
      }
      if (error.code === 'ERR_CANCELED') {
        return fallback
      }
      return '서버에 연결할 수 없습니다. 네트워크 상태를 확인한 뒤 다시 시도해주세요.'
    }

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