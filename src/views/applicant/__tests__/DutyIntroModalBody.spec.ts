import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DutyIntroModalBody from '../DutyIntroModalBody.vue'
import type { DutyItem } from '@/types/duty'

const job: DutyItem = {
  url: 'Sample Url',
  eyebrow: 'Wealth Management Service',
  title: '자산관리 서비스',
  major: '전공 무관',
  degree: '학사 이상',
  intro: '직무소개 본문입니다.',
  skills: [
    { lead: '역량A:', body: '설명A' },
    { lead: '역량B:', body: '설명B' },
  ],
  career: '커리어패스 본문입니다.',
  diff: '차별점 본문입니다.',
}

const factory = () => mount(DutyIntroModalBody, { props: { job } })

describe('DutyIntroModalBody', () => {
  it('eyebrow와 국문 타이틀을 렌더한다', () => {
    const w = factory()
    expect(w.find('.dm-eyebrow-text').text()).toBe('Wealth Management Service')
    expect(w.find('.dm-title').text()).toBe('자산관리 서비스')
  })

  it('세 개의 번호 섹션과 제목을 렌더한다', () => {
    const w = factory()
    const badges = w.findAll('.dm-badge')
    expect(badges.map((b) => b.text())).toEqual(['1', '2', '3'])
    expect(w.text()).toContain('직무소개')
    expect(w.text()).toContain('필요역량')
    expect(w.text()).toContain('커리어패스')
  })

  it('전공/학위 pill과 스킬 항목을 렌더한다', () => {
    const w = factory()
    const pills = w.findAll('.dm-pill')
    expect(pills).toHaveLength(2)
    expect(pills[0]!.text()).toContain('전공')
    expect(pills[0]!.text()).toContain('전공 무관')
    expect(pills[1]!.text()).toContain('학위')
    expect(pills[1]!.text()).toContain('학사 이상')

    const skills = w.findAll('.dm-skill')
    expect(skills).toHaveLength(2)
    expect(skills[0]!.find('.dm-skill-lead').text()).toBe('역량A:')
    expect(skills[0]!.text()).toContain('설명A')
  })

  it('차별점은 번호 뱃지 없이 하이라이트 박스로 렌더한다', () => {
    const w = factory()
    const box = w.find('.dm-highlight')
    expect(box.exists()).toBe(true)
    expect(box.find('.dm-highlight-title').text()).toBe('신영증권만의 차별점')
    expect(box.text()).toContain('차별점 본문입니다.')
    expect(box.find('.dm-badge').exists()).toBe(false)
  })

  it('✕ 클릭 시 close 이벤트를 emit 한다', async () => {
    const w = factory()
    await w.find('.dm-close').trigger('click')
    expect(w.emitted('close')).toHaveLength(1)
  })
})
