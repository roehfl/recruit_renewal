import axios from 'axios'
import { useUiStore } from '@/stores/uiStore'
import { logApiError } from '@/common/httpErrorTelemetry'

declare module 'axios' {
  export interface AxiosRequestConfig {
    skipAuthRedirect?: boolean
  }
}

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000,
  withCredentials: true,
})

apiClient.interceptors.request.use(
  (config) => {
    const uiStore = useUiStore()
    uiStore.showLoading()

    return config
  },
  (error) => {
    return Promise.reject(error)
  },
)

apiClient.interceptors.response.use(
  (response) => {
    const uiStore = useUiStore()
    uiStore.hideLoading()

    return response
  },
  (error) => {
    const uiStore = useUiStore()
    const currentPath = window.location.pathname
    uiStore.hideLoading()

    logApiError(error)

    const status = error.response?.status
    const skipAuthRedirect = error.config?.skipAuthRedirect === true

    if (status === 401 && !skipAuthRedirect && currentPath !== '/login') {
      // 세션 만료 또는 미로그인
      window.location.href = '/login'
    }

    /*
     * 401과 동일하게 skipAuthRedirect를 존중한다.
     * 세션 복구용 조회(authApi.me)처럼 호출부가 실패를 직접 처리하는 요청까지
     * 강제 이동시키면 화면 전환이 끊긴다. 이미 /403이면 재이동하지 않는다.
     */
    if (status === 403 && !skipAuthRedirect && currentPath !== '/403') {
      window.location.href = '/403'
    }

    return Promise.reject(error)
  },
)
