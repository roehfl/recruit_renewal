import axios from 'axios'
import type { AxiosResponse } from 'axios'
import { getApiErrorMessage } from '@/api/apiError'

/**
 * blob 응답을 파일로 저장한다. 서버가 노출하면 Content-Disposition 의 `filename*=UTF-8''` 를 쓰고,
 * 아니면 호출부가 준 기본값을 쓴다.
 */
export const saveBlobResponse = (response: AxiosResponse<Blob>, fallbackFileName: string): void => {
  const disposition = response.headers['content-disposition']
  let fileName = fallbackFileName
  if (typeof disposition === 'string') {
    const match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
    if (match?.[1]) {
      fileName = decodeURIComponent(match[1])
    }
  }

  const blobUrl = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = blobUrl
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(blobUrl)
}

/**
 * blob 다운로드 실패 메시지를 읽는다.
 *
 * `responseType: 'blob'` 요청은 오류 응답 본문도 Blob 으로 오기 때문에 `getApiErrorMessage` 가
 * 서버 메시지를 꺼내지 못한다. Blob 을 텍스트로 되돌려 ApiResponse.message 를 복구한다.
 */
export const getBlobErrorMessage = async (error: unknown, fallback: string): Promise<string> => {
  if (axios.isAxiosError(error) && error.response?.data instanceof Blob) {
    try {
      const text = await error.response.data.text()
      const message = JSON.parse(text)?.message
      if (typeof message === 'string' && message.trim().length > 0) {
        return message
      }
    } catch {
      // 본문이 JSON 이 아니면 기본 메시지로 넘어간다.
    }
  }
  return getApiErrorMessage(error, fallback)
}
