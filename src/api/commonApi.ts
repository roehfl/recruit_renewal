import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { CommonCodeItems, CommonCodeResponse } from '@/types/commonCode'

export const commonCodeApi = {

  getCommonCodes(groupCode: string){
    return apiClient.get<ApiResponse<CommonCodeItems[]>>('/codes', {
        params: {
            groupCode: groupCode
        }
    })
  },

}