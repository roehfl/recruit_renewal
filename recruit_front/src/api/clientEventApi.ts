import { telemetryClient } from './telemetryClient'
import type { ApiResponse } from '@/types/api'
import type { ClientEventIngestResponse, ClientEventPayload } from '@/types/clientEvent'

export const clientEventApi = {
  record(payload: ClientEventPayload) {
    return telemetryClient.post<ApiResponse<ClientEventIngestResponse>>('/client-events', payload)
  },
}