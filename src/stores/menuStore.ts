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

interface MenuPathEntry {
  path: string
  trail: MenuItem[]
}

/*
 * 현재 경로와 비교 가능한 메뉴만 (path, 대메뉴→소메뉴 경로) 형태로 펼친다.
 * URL 타입은 path가 http(s) 절대주소라 라우터 경로와 비교 대상이 아니다.
 */
const collectRoutePathEntries = (
  menuTree: MenuItem[],
  parents: MenuItem[] = [],
): MenuPathEntry[] => {
  const entries: MenuPathEntry[] = []

  for (const menu of menuTree) {
    const currentTrail = [...parents, menu]

    if (menu.type === 'ROUTE' && menu.path) {
      entries.push({ path: menu.path, trail: currentTrail })
    }

    if (menu.children && menu.children.length > 0) {
      entries.push(...collectRoutePathEntries(menu.children, currentTrail))
    }
  }

  return entries
}

/*
 * 현재 경로에 해당하는 메뉴 경로(대메뉴 → 소메뉴)를 찾는다.
 * 1) path가 완전히 같은 메뉴를 우선한다.
 * 2) 없으면 현재 경로의 상위 경로를 가진 메뉴 중 가장 구체적인(path가 긴) 메뉴를 쓴다.
 *    메뉴에 등록되지 않은 하위 화면에서도 상위 메뉴 표시를 유지하기 위한 fallback이다.
 */
const findTrailInTree = (menuTree: MenuItem[], targetPath: string): MenuItem[] | null => {
  const entries = collectRoutePathEntries(menuTree)

  const exactEntry = entries.find((entry) => entry.path === targetPath)

  if (exactEntry) {
    return exactEntry.trail
  }

  let ancestorEntry: MenuPathEntry | null = null

  for (const entry of entries) {
    if (!targetPath.startsWith(`${entry.path}/`)) {
      continue
    }

    if (!ancestorEntry || entry.path.length > ancestorEntry.path.length) {
      ancestorEntry = entry
    }
  }

  return ancestorEntry ? ancestorEntry.trail : null
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

    /*
     * 대메뉴는 자기 path가 아니라 현재 경로의 메뉴 경로에 포함되는지로 판정된다.
     * 따라서 path 없는 그룹 대메뉴도 하위 소메뉴가 활성이면 함께 활성 처리된다.
     */
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
