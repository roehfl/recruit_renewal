import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ApplicantBenefits from '../ApplicantBenefits.vue'
import ApplicantInfoTabPanel from '../ApplicantInfoTabPanel.vue'
import type { PanelTab } from '../infoTabPanel'

describe('ApplicantBenefits', () => {
  it('복리후생 카테고리 칩과 항목 매핑이 목업과 일치한다', () => {
    const w = mount(ApplicantBenefits, {
      global: { stubs: { ApplicantBreadcrumb: true } },
    })
    const tabs = w.findComponent(ApplicantInfoTabPanel).props('tabs') as PanelTab[]

    expect(tabs.map((t) => t.label)).toEqual(['복리 후생 제도', '휴가 제도'])

    const benefit = tabs.find((t) => t.key === 'benefit')!
    expect(benefit.chips.map((c) => c.label)).toEqual([
      '전체',
      '경제적 지원',
      '건강과 의료',
      '여가와 문화',
      '성장과 교육',
    ])
    expect(benefit.chips[0]!.items).toHaveLength(14)
    expect(benefit.chips.find((c) => c.key === 'econ')!.items.map((i) => i.label)).toEqual([
      '경조사',
      '주택자금대출',
      '선택적 복리후생',
      '명절',
    ])
    expect(benefit.chips.find((c) => c.key === 'growth')!.items.map((i) => i.label)).toEqual([
      '학자금',
      '교육비 지원',
    ])

    const leave = tabs.find((t) => t.key === 'leave')!
    expect(leave.chips).toHaveLength(1)
    expect(leave.chips[0]!.items).toHaveLength(4)
  })
})
