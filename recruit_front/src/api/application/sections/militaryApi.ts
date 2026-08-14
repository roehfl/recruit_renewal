import { apiClient } from "@/api/client"
import type { ApiResponse } from "@/types/api"
import type { MilitaryRepuest, MilitaryResponse } from "@/types/application/sections/military"

export const applicationMilitaryApi = {

  getApplicationMilitary(applicationId: number){
    return apiClient.get<ApiResponse<MilitaryResponse>>(`/applications/${applicationId}/military`)
  },

  postApplicationvMilitary(applicationId: number, data: MilitaryRepuest){
    return apiClient.post<ApiResponse<MilitaryResponse>>(`/applications/${applicationId}/military`, data)
  },

}