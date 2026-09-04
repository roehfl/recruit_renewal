import type { AxiosResponse } from 'axios'

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
