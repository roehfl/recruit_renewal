import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { ApplicationSearchParams, MyApplicationList } from '@/types/application'

export const applicationApi = {

  getMyApplications(params: ApplicationSearchParams){
    return apiClient.get<ApiResponse<MyApplicationList>>('/applications/me')
  },

}