import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type { NiceRequestResponse, NiceResultResponse } from '@/types/auth/nice'

/** NICE 본인확인. 가입 전 흐름이라 인증 없이 호출된다. */
export const niceApi = {
  /** 요청번호를 발급받고 표준창으로 보낼 암호문을 받는다. */
  request() {
    return apiClient.post<ApiResponse<NiceRequestResponse>>('/auth/nice/request')
  },

  /** 콜백 리다이렉트로 받은 1회용 토큰을 인증 결과로 교환한다. 서버 세션에 CI 가 심긴다. */
  exchangeResult(token: string) {
    return apiClient.post<ApiResponse<NiceResultResponse>>('/auth/nice/result', { token })
  },
}
