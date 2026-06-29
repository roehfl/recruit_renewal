import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ApplicantDutyIntroduction from '../ApplicantDutyIntroduction.vue'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

const stubs = {
  ApplicantBreadcrumb: true,
  DutyIntroModalBody: true,
  'a-button': { template: '<button><slot /></button>' },
  'a-card': { template: '<div class="quick-link-card"><slot /></div>' },
  'a-modal': { template: '<div class="a-modal-stub"><slot /></div>', props: ['open'] },
}

const factory = () => mount(ApplicantDutyIntroduction, { global: { stubs } })

describe('ApplicantDutyIntroduction', () => {
  it('7개 직무 카드를 렌더한다', () => {
    const w = factory()
    expect(w.findAll('.quick-link-card')).toHaveLength(7)
  })

  it('카드에 국문 타이틀과 영문 eyebrow를 바인딩한다', () => {
    const w = factory()
    const titles = w.findAll('.card-title').map((n) => n.text())
    const descs = w.findAll('.card-desc').map((n) => n.text())
    expect(titles).toContain('자산관리 서비스')
    expect(titles).toContain('IB')
    expect(descs).toContain('Wealth Management Service')
    expect(descs).toContain('Investment Banking')
  })
})
