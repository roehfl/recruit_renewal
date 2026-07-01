import { apiClient } from '../../client'
import type { ApiResponse } from '@/types/api'
import type { BasicInfoParams, ApiResponseBasicInfoResponse, AttachmentFileRequest, AttachmentResponse } from '@/types/application/sections/basicInfo'

export const basicInfoApi = {

  getApplicationsBasicInfo(applicationId: number){
    return apiClient.get<ApiResponse<ApiResponseBasicInfoResponse>>(`applications/${applicationId}/basic-info`)
  },

  postApplicationsBasicInfo(applicationId: number, params: BasicInfoParams){
    return apiClient.post<ApiResponse<ApiResponseBasicInfoResponse>>(`applications/${applicationId}/basic-info`, params)
  },

  getApplicationAttachments(applicationId: number){
    return apiClient.get<ApiResponse<AttachmentResponse[]>>(`applications/${applicationId}/attachments`)
  }, 

  postApplicationAttachmentsFile(formData: FormData, params: AttachmentFileRequest){
    return apiClient.post<ApiResponse<AttachmentResponse>>(`applications/${params.applicationId}/attachments/files`, formData, {params})
  }, 

  deleteApplicationAttachments(applicationId: number, attachmentId: number){
    return apiClient.post<ApiResponse<AttachmentResponse>>(`applications/${applicationId}/attachments/${attachmentId}/delete`)
  }, 
}