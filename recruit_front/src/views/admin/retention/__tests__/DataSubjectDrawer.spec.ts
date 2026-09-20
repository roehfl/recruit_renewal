import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DOMWrapper, flushPromises, mount } from '@vue/test-utils'
import Antd from 'ant-design-vue'

import './antdJsdomPolyfill'
import DataSubjectDrawer from '../DataSubjectDrawer.vue'
import type { DataSubjectApplication, DataSubjectDetail } from '@/types/admin/retention'

/*
 * 파기 대상자 상세 드로어의 안전장치 회귀 테스트.
 * 1) 삭제 요청 파기 버튼의 비활성 조건(보류 중 / 전부 이미 파기됨) — 특히 지원서 0건일 때
 *    `applications.every(...)`가 빈 배열에서 true를 내는 함정에 걸려 잘못 비활성화되지 않는지.
 * 2) 요청 번호 기반 stale 응답 가드 — 먼저 보낸 요청이 나중에 응답해도 화면에는 나중 대상자 정보만
 *    남는지(엉뚱한 사람 위에서 파기 버튼을 누르게 되는 사고를 막는 장치).
 */

const getDataSubjectMock = vi.fn()

vi.mock('@/api/admin/retentionApi', () => ({
  retentionApi: {
    getDataSubject: (...args: unknown[]) => getDataSubjectMock(...args),
  },
}))

const baseDetail: DataSubjectDetail = {
  applicantId: 1,
  name: '홍길동',
  email: 'hong@example.com',
  phoneNumber: '010-1111-2222',
  hasActiveHold: false,
  applications: [],
}

const alreadyPurgedApplication: DataSubjectApplication = {
  applicationId: 10,
  jobPostingTitle: '2026 상반기 공채',
  status: 'REJECTED',
  submittedAt: '2025-01-01T00:00:00',
  purgeResult: 'PURGED',
  eligible: false,
  reasonCode: 'ALREADY_PURGED',
}

const mockDetail = (data: DataSubjectDetail): void => {
  getDataSubjectMock.mockResolvedValueOnce({ data: { data } })
}

const findPurgeButton = (body: DOMWrapper<HTMLElement>) =>
  body.findAll('button').find((b) => b.text().includes('삭제 요청 파기'))

beforeEach(() => {
  getDataSubjectMock.mockReset()
})

describe('DataSubjectDrawer - 삭제 요청 파기 버튼 비활성 조건', () => {
  it('hasActiveHold가 true면 비활성화된다', async () => {
    mockDetail({ ...baseDetail, hasActiveHold: true })

    const wrapper = mount(DataSubjectDrawer, {
      attachTo: document.body,
      global: { plugins: [Antd] },
      props: { open: true, applicantId: 1 },
    })
    await flushPromises()

    const body = new DOMWrapper(document.body)
    expect(findPurgeButton(body)?.attributes('disabled')).toBeDefined()

    wrapper.unmount()
  })

  it('지원서 2건이 전부 ALREADY_PURGED면 비활성화된다', async () => {
    mockDetail({
      ...baseDetail,
      applications: [
        alreadyPurgedApplication,
        { ...alreadyPurgedApplication, applicationId: 11, jobPostingTitle: '2026 하반기 공채' },
      ],
    })

    const wrapper = mount(DataSubjectDrawer, {
      attachTo: document.body,
      global: { plugins: [Antd] },
      props: { open: true, applicantId: 1 },
    })
    await flushPromises()

    const body = new DOMWrapper(document.body)
    expect(findPurgeButton(body)?.attributes('disabled')).toBeDefined()

    wrapper.unmount()
  })

  it('지원서가 0건이면 비활성화되지 않는다(빈 배열 every 함정 고정)', async () => {
    mockDetail({ ...baseDetail, applications: [] })

    const wrapper = mount(DataSubjectDrawer, {
      attachTo: document.body,
      global: { plugins: [Antd] },
      props: { open: true, applicantId: 1 },
    })
    await flushPromises()

    const body = new DOMWrapper(document.body)
    expect(findPurgeButton(body)?.attributes('disabled')).toBeUndefined()

    wrapper.unmount()
  })
})

describe('DataSubjectDrawer - stale 응답 가드', () => {
  it('먼저 보낸 요청이 나중에 응답해도 화면에는 나중 대상자 정보만 남는다', async () => {
    let resolveFirst!: (value: unknown) => void
    const firstResponse = new Promise((resolve) => {
      resolveFirst = resolve
    })

    // 1번 대상자 조회(느림) → 2번 대상자 조회(빠름) 순으로 나가지만, 응답은 2번이 먼저 온다.
    getDataSubjectMock.mockImplementationOnce(() => firstResponse)
    getDataSubjectMock.mockImplementationOnce(() =>
      Promise.resolve({ data: { data: { ...baseDetail, applicantId: 2, name: '두번째지원자' } } }),
    )

    const wrapper = mount(DataSubjectDrawer, {
      attachTo: document.body,
      global: { plugins: [Antd] },
      props: { open: true, applicantId: 1 },
    })
    await flushPromises()

    // 사용자가 응답을 기다리는 사이 다른 행(2번 대상자)을 클릭한 상황을 흉내낸다.
    await wrapper.setProps({ applicantId: 2 })
    await flushPromises()

    expect(document.body.innerHTML).toContain('두번째지원자')

    // 그 후에야 1번 대상자 조회의 응답이 늦게 도착한다.
    resolveFirst({ data: { data: { ...baseDetail, applicantId: 1, name: '첫번째지원자' } } })
    await flushPromises()

    // 늦게 온 1번 응답이 화면을 덮어써서는 안 된다.
    expect(document.body.innerHTML).toContain('두번째지원자')
    expect(document.body.innerHTML).not.toContain('첫번째지원자')

    wrapper.unmount()
  })
})
