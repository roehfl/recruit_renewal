import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { MenuSite, MenuItem, MenuSaveRequest } from '@/types/menu'

interface MenuIdResponse {
  id: number
}

export const menuApi = {
  getMenuTree(site: MenuSite) {
    return apiClient.get<ApiResponse<MenuItem[]>>('/menu/tree', {
      params: {
        site,
      },
    })
  },

  getBreadcrumb(site: MenuSite, path: string) {
    return apiClient.get<ApiResponse<MenuItem[]>>('/menu/breadcrumb', {
      params: {
        site,
        path,
      },
    })
  },

  /*
   * 관리자 메뉴 관리 화면 전용. 삭제 API는 없고 생성/수정만 제공된다(계약서 메뉴 섹션).
   * 백엔드가 DELETE를 쓰지 않는 관례라 수정도 POST다.
   */
  createMenu(request: MenuSaveRequest) {
    return apiClient.post<ApiResponse<MenuIdResponse>>('/menu/admin/menu', request)
  },

  updateMenu(menuId: number, request: MenuSaveRequest) {
    return apiClient.post<ApiResponse<MenuIdResponse>>(`/menu/admin/menu/${menuId}`, request)
  },
}
