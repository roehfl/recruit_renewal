import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { useMenuStore } from '@/stores/menuStore'
import type { MenuItem } from '@/types/menu'

const createMenu = (menu: Partial<MenuItem> & Pick<MenuItem, 'id' | 'name'>): MenuItem => ({
  parentId: null,
  site: 'APPLICANT',
  type: 'ROUTE',
  path: null,
  sortOrder: 0,
  icon: null,
  children: [],
  ...menu,
})

/*
 * 실제 운영 형태를 따른 트리.
 * - 대메뉴는 path 없는 그룹 라벨
 * - 첫 소메뉴는 외부 URL 링크
 * - 홈은 path가 짧은(=덜 구체적인) 메뉴
 */
const applicantMenuTree: MenuItem[] = [
  createMenu({
    id: 1,
    name: '인사제도',
    children: [
      createMenu({
        id: 11,
        parentId: 1,
        name: '신영증권 홈페이지',
        type: 'URL',
        path: 'https://www.example.com',
      }),
      createMenu({ id: 12, parentId: 1, name: '보상 및 평가', path: '/applicant/hrRule' }),
    ],
  }),
  createMenu({
    id: 2,
    name: '채용공고',
    children: [createMenu({ id: 21, parentId: 2, name: '채용공고', path: '/applicant/recruits' })],
  }),
  createMenu({ id: 3, name: '홈', path: '/applicant' }),
]

describe('menuStore.isActiveMenu', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  const activeIds = (currentPath: string): number[] => {
    const menuStore = useMenuStore()
    menuStore.menuTreeMap.APPLICANT = applicantMenuTree

    const allMenus = applicantMenuTree.flatMap((menu) => [menu, ...menu.children])

    return allMenus
      .filter((menu) => menuStore.isActiveMenu('APPLICANT', menu, currentPath))
      .map((menu) => menu.id)
  }

  it('path 없는 그룹 대메뉴도 하위 소메뉴가 활성이면 함께 활성 처리된다', () => {
    expect(activeIds('/applicant/hrRule')).toEqual([1, 12])
  })

  it('외부 URL 소메뉴는 활성 대상이 아니고, 다른 형제 소메뉴 판정도 방해하지 않는다', () => {
    expect(activeIds('/applicant/hrRule')).not.toContain(11)
  })

  it('대메뉴를 URL 타입으로 등록해도 하위 소메뉴 기준으로 활성 처리된다', () => {
    const menuStore = useMenuStore()
    const urlRoot = createMenu({
      id: 100,
      name: 'URL 대메뉴',
      type: 'URL',
      path: 'https://www.example.com',
      children: [createMenu({ id: 101, parentId: 100, name: '소메뉴', path: '/applicant/benefits' })],
    })

    menuStore.menuTreeMap.APPLICANT = [urlRoot]

    expect(menuStore.isActiveMenu('APPLICANT', urlRoot, '/applicant/benefits')).toBe(true)
  })

  it('메뉴에 등록되지 않은 하위 경로는 가장 구체적인 상위 메뉴가 활성이 된다', () => {
    expect(activeIds('/applicant/recruits/9')).toEqual([2, 21])
  })

  it('완전일치 메뉴가 있으면 상위 경로 메뉴보다 우선한다', () => {
    expect(activeIds('/applicant/recruits')).toEqual([2, 21])
  })

  it('일치하는 메뉴가 없으면 아무 메뉴도 활성이 아니다', () => {
    expect(activeIds('/login')).toEqual([])
  })
})
