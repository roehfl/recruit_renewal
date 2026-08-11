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

/*
 * 메뉴 생성/수정 요청 본문. 백엔드 MenuSaveRequest 와 같은 모양이며 생성·수정이 같은 스키마를 쓴다.
 * parentId가 null이면 메인메뉴(대메뉴), 값이 있으면 그 메뉴의 서브메뉴(소메뉴)로 저장된다.
 */
export interface MenuSaveRequest {
  site: MenuSite
  type: MenuType
  parentId: number | null
  name: string
  path: string | null
  sortOrder: number | null
  icon: string | null
}
