import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { MenuSite, MenuItem } from '@/types/menu'

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
}
