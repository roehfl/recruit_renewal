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
  /*
   * ant-design-vue 아이콘 컴포넌트명을 문자열 그대로 담는다(예: 'SettingOutlined').
   * 관리자 좌측 사이드바 전용이며 지원자(가로 헤더) 메뉴는 null이다.
   */
  icon: string | null
  children: MenuItem[]
}
