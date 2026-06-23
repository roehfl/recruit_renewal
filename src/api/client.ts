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

    if (status === 403) {
      window.location.href = '/403'
    }

    return Promise.reject(error)
  },
)
