import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ApplicantInfoTabPanel from '../ApplicantInfoTabPanel.vue'
import type { PanelTab } from '../infoTabPanel'

const tabs: PanelTab[] = [
  {
    key: 'a',
    label: 'AAA',
    chips: [
      {
        key: 'all',
        label: '전체',
        items: Array.from({ length: 10 }, (_, i) => ({ label: `L${i}`, desc: `D${i}` })),
      },
      { key: 'sub', label: '서브', items: [{ label: 'only', desc: 'od' }] },
    ],
  },
  {
    key: 'b',
    label: 'BBB',
    chips: [{ key: 'all', label: '전체', items: [{ label: 'b1' }, { label: 'b2', desc: 'bd2' }] }],
  },
]

const factory = () =>
  mount(ApplicantInfoTabPanel, {
    props: { title: '제목', subtitle: '설명', tabs },
    global: { stubs: { ApplicantBreadcrumb: true } },
  })

describe('ApplicantInfoTabPanel', () => {
  it('제목과 설명을 렌더한다', () => {
    const w = factory()
    expect(w.text()).toContain('제목')
    expect(w.text()).toContain('설명')
  })

  it('기본으로 첫 탭이 활성이고 첫 칩 항목을 보여준다', () => {
    const w = factory()
    expect(w.find('#tab-a').classes()).toContain('active')
    expect(w.find('#tab-b').classes()).not.toContain('active')
    expect(w.findAll('.card')).toHaveLength(10)
  })

  it('항목이 7개 초과면 2열 클래스가 붙는다', () => {
    const w = factory()
    expect(w.find('.card-list').classes()).toContain('two-col')
  })

  it('칩을 클릭하면 해당 칩 항목으로 필터링된다', async () => {
    const w = factory()
    const chips = w.findAll('.chip')
    expect(chips).toHaveLength(2)
    await chips[1]!.trigger('click') // 서브
    expect(w.findAll('.card')).toHaveLength(1)
    expect(w.text()).toContain('only')
    expect(w.find('.card-list').classes()).not.toContain('two-col')
  })

  it('탭을 바꾸면 항목이 바뀌고 칩이 첫 칩으로 리셋된다', async () => {
    const w = factory()
    await w.findAll('.chip')[1]!.trigger('click') // A의 서브 → 1개
    expect(w.findAll('.card')).toHaveLength(1)

    await w.find('#tab-b').trigger('click') // B 탭
    expect(w.find('#tab-b').classes()).toContain('active')
    expect(w.findAll('.card')).toHaveLength(2)

    await w.find('#tab-a').trigger('click') // 다시 A → 칩 리셋되어 전체(10)
    expect(w.findAll('.card')).toHaveLength(10)
  })

  it('desc가 없으면 설명 노드를 렌더하지 않는다', async () => {
    const w = factory()
    await w.find('#tab-b').trigger('click') // b1(설명없음), b2(설명있음)
    const cards = w.findAll('.card')
    expect(cards[0]!.find('.card-desc').exists()).toBe(false) // b1
    expect(cards[1]!.find('.card-desc').exists()).toBe(true) // b2
  })
})
