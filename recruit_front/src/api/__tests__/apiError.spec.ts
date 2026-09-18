import { describe, expect, it } from 'vitest'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'

import { getApiErrorMessage } from '@/api/apiError'

const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig

describe('getApiErrorMessage', () => {
  it('응답 시간 초과는 axios 영문 메시지 대신 한글 안내를 돌려준다', () => {
    const error = new AxiosError('timeout of 10000ms exceeded', 'ECONNABORTED', config)

    expect(getApiErrorMessage(error, '기본')).toBe('요청 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.')
  })

  it('응답이 없는 네트워크 오류는 연결 안내를 돌려준다', () => {
    const error = new AxiosError('Network Error', 'ERR_NETWORK', config)

    expect(getApiErrorMessage(error, '기본')).toBe('서버에 연결할 수 없습니다. 네트워크 상태를 확인한 뒤 다시 시도해주세요.')
  })

  it('서버가 보낸 메시지가 있으면 그대로 쓴다', () => {
    const error = new AxiosError('Request failed', 'ERR_BAD_REQUEST', config, null, {
      status: 400,
      statusText: 'Bad Request',
      headers: {},
      config,
      data: { success: false, message: '학력을 입력해야 제출할 수 있습니다.' },
    })

    expect(getApiErrorMessage(error, '기본')).toBe('학력을 입력해야 제출할 수 있습니다.')
  })
})
