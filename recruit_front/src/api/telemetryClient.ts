import axios from 'axios'

export const telemetryClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 3000,
  withCredentials: true,
})