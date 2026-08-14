import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type {
  AssignableRole,
  DeptRoleMapping,
  DeptRoleMappingSaveRequest,
  UserRoleMapping,
  UserRoleMappingSaveRequest,
} from '@/types/roleMapping'

interface RoleMappingIdResponse {
  id: number
}

/*
 * 관리자 권한 관리 화면 전용(/api/admin/role-mappings/**, ROLE_ADMIN·ROLE_RECRUIT_ADMIN).
 * 백엔드가 DELETE를 쓰지 않는 관례라 수정·삭제도 POST다.
 */
export const adminRoleMappingApi = {
  getAssignableRoles() {
    return apiClient.get<ApiResponse<AssignableRole[]>>('/admin/role-mappings/roles')
  },

  getDeptMappings() {
    return apiClient.get<ApiResponse<DeptRoleMapping[]>>('/admin/role-mappings/dept')
  },

  createDeptMapping(request: DeptRoleMappingSaveRequest) {
    return apiClient.post<ApiResponse<RoleMappingIdResponse>>('/admin/role-mappings/dept', request)
  },

  updateDeptMapping(id: number, request: DeptRoleMappingSaveRequest) {
    return apiClient.post<ApiResponse<RoleMappingIdResponse>>(`/admin/role-mappings/dept/${id}`, request)
  },

  deleteDeptMapping(id: number) {
    return apiClient.post<ApiResponse<void>>(`/admin/role-mappings/dept/${id}/delete`)
  },

  getUserMappings() {
    return apiClient.get<ApiResponse<UserRoleMapping[]>>('/admin/role-mappings/user')
  },

  createUserMapping(request: UserRoleMappingSaveRequest) {
    return apiClient.post<ApiResponse<RoleMappingIdResponse>>('/admin/role-mappings/user', request)
  },

  updateUserMapping(id: number, request: UserRoleMappingSaveRequest) {
    return apiClient.post<ApiResponse<RoleMappingIdResponse>>(`/admin/role-mappings/user/${id}`, request)
  },

  deleteUserMapping(id: number) {
    return apiClient.post<ApiResponse<void>>(`/admin/role-mappings/user/${id}/delete`)
  },
}
