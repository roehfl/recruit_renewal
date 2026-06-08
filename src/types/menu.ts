export type MenuSite = 'APPLICANT' | 'ADMIN'
export type MenuType = 'ROUTE' | 'URL'

export interface MenuItem {
  id: number
  parentId: number | null
  site: MenuSite
  type: MenuType
  name: string
  path: string | null
  sortOrder: number
  children: MenuItem[]
}
