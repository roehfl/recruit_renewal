import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { menuApi } from '@/api/menuApi'
import type { MenuItem, MenuType, MenuSite } from '@/types/menu'

interface MenuState {
  menuTreeMap: Record<MenuSite, MenuItem[]>
  breadcrumbMap: Record<MenuSite, Record<string, MenuItem[]>>
  loadedMap: Record<MenuSite, boolean>
  loadingMap: Record<MenuSite, boolean>
  errorMessageMap: Record<MenuSite, string | null>
}

const findTrailInTree = (
  menuTree: MenuItem[],
  targetPath: string,
  parents: MenuItem[] = [],
): MenuItem[] | null => {
  for (const menu of menuTree) {
    const currentTrail = [...parents, menu]

    if (menu.type === 'ROUTE' && menu.path === targetPath) {
      return currentTrail
    }

    if (menu.children && menu.children.length > 0) {
      const found = findTrailInTree(menu.children, targetPath, currentTrail)

      if (found) {
        return found
      }
    }
  }
  return null
}

export const useMenuStore = defineStore('menu', {
  state: (): MenuState => ({
    menuTreeMap: {
      APPLICANT: [],
      ADMIN: [],
    },
    breadcrumbMap: {
      APPLICANT: {},
      ADMIN: {},
    },
    loadedMap: {
      APPLICANT: false,
      ADMIN: false,
    },
    loadingMap: {
      APPLICANT: false,
      ADMIN: false,
    },
    errorMessageMap: {
      APPLICANT: null,
      ADMIN: null,
    },
  }),

  getters: {
    getMenuTree: (state) => {
      return (site: MenuSite): MenuItem[] => {
        return state.menuTreeMap[site]
      }
    },

    getBreadcrumb: (state) => {
      return (site: MenuSite, path: string): MenuItem[] => {
        return state.breadcrumbMap[site][path] ?? []
      }
    },

    isActiveMenu: (state) => {
      return (site: MenuSite, menu: MenuItem, currentPath: string): boolean => {
        const trail = findTrailInTree(state.menuTreeMap[site], currentPath) ?? []

        return trail.some((trailItem) => trailItem.id === menu.id)
      }
    },

    isLoaded: (state) => {
      return (site: MenuSite): boolean => {
        return state.loadedMap[site]
      }
    },

    isLoading: (state) => {
      return (site: MenuSite): boolean => {
        return state.loadingMap[site]
      }
    },

    // getErrorMessage: (state) => {
    //     return (site: MenuSite): string | null => {
    //         return state.errorMessageMap[site]
    //     }
    // }
  },

  actions: {
    async fetchMenuTree(site: MenuSite) {
      this.loadingMap[site] = true
      // this.errorMessageMap[site] = null
      const response = await menuApi.getMenuTree(site)
      this.menuTreeMap[site] = response.data.data
      this.loadedMap[site] = true
      if (!response.data.success) {
        throw new Error(response.data.message || '메뉴 정보를 불러오지 못했습니다.')
      }
      this.loadingMap[site] = false
    },

    async fetchBreadcrumb(site: MenuSite, path: string) {
      //   this.loadingMap[site] = true
      // this.errorMessageMap[site] = null
      const response = await menuApi.getBreadcrumb(site, path)
      this.breadcrumbMap[site][path] = response.data.data
      //   this.loadedMap[site] = true
      if (!response.data.success) {
        throw new Error(response.data.message || '메뉴 경로를 불러오지 못했습니다.')
      }
    },

    clearMenuTree(site: MenuSite): void {
      this.menuTreeMap[site] = []
      this.loadedMap[site] = false
      this.loadingMap[site] = false
    },

    clearBreadcrumb(site: MenuSite, path?: string): void {
      if (path) {
        delete this.breadcrumbMap[site][path]
        return
      }
      this.breadcrumbMap[site] = {}
    },

    clearAllMenu(): void {
      this.menuTreeMap.ADMIN = []
      this.menuTreeMap.APPLICANT = []
      this.breadcrumbMap.ADMIN = {}
      this.breadcrumbMap.APPLICANT = {}
      this.loadedMap.ADMIN = false
      this.loadedMap.APPLICANT = false
      this.loadingMap.ADMIN = false
      this.loadingMap.APPLICANT = false
    },
  },
})
