import axios from 'axios'

export const telemetryClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 3000,
  withCredentials: true,
  // CSRF 방어 헤더(api/client.ts 와 같다). 없으면 POST /client-events 가 403 이다.
  headers: { 'X-Requested-With': 'XMLHttpRequest' },
})