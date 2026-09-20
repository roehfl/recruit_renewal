import { describe, expect, it, vi } from 'vitest'
import { DOMWrapper, mount } from '@vue/test-utils'
import Antd from 'ant-design-vue'

import ForcedPurgeConfirmModal from '../ForcedPurgeConfirmModal.vue'
import type { DataSubjectDetail } from '@/types/admin/retention'

/*
 * 강제 파기 확인 모달의 안전장치 회귀 테스트.
 * 체크박스를 체크·사유를 바꾼 뒤 모달을 닫았다가 다시 열면 초기값(사유=DATA_SUBJECT_REQUEST,
 * 체크 해제)으로 돌아가고 실행 버튼이 다시 비활성화되는지 확인한다. 리셋 watch가 닫힘 경로 하나를
 * 놓치거나 앞으로 새 닫힘 경로가 추가돼도, 이 테스트는 "다시 열었을 때" 상태만 보므로 계속 잡아낸다.
 */

vi.mock('@/api/admin/retentionApi', () => ({
  retentionApi: {
    forcePurge: vi.fn(),
  },
}))

const detail: DataSubjectDetail = {
  applicantId: 1,
  name: '홍길동',
  email: 'hong@example.com',
  phoneNumber: '010-1111-2222',
  hasActiveHold: false,
  applications: [],
}

describe('ForcedPurgeConfirmModal', () => {
  it('체크·사유 변경 후 닫았다가 다시 열면 초기값으로 돌아가고 실행 버튼이 다시 비활성화된다', async () => {
    const wrapper = mount(ForcedPurgeConfirmModal, {
      attachTo: document.body,
      global: { plugins: [Antd] },
      props: { open: true, detail },
    })

    const body = new DOMWrapper(document.body)
    const findExecuteButton = () => body.findAll('button').find((b) => b.text().includes('파기 실행'))

    // 처음 열렸을 때: 기본 사유(첫 번째 라디오 = DATA_SUBJECT_REQUEST)가 선택되어 있고 실행 버튼은 비활성이다.
    const radiosInitial = body.findAll('input[type="radio"]')
    expect((radiosInitial[0]!.element as HTMLInputElement).checked).toBe(true)
    expect(findExecuteButton()?.attributes('disabled')).toBeDefined()

    // 사유를 바꾸고(두 번째 라디오 = DUPLICATE_ACCOUNT) 체크박스를 체크한다.
    await radiosInitial[1]!.setValue(true)
    const checkbox = body.find('input[type="checkbox"]')
    await checkbox.setValue(true)

    expect((checkbox.element as HTMLInputElement).checked).toBe(true)
    expect(findExecuteButton()?.attributes('disabled')).toBeUndefined()

    // 부모가 모달을 닫는다(취소·X·마스크 클릭 전부 open prop을 false로 만든다).
    await wrapper.setProps({ open: false })
    // 같은 대상자로 다시 연다.
    await wrapper.setProps({ open: true })

    const radiosAfterReopen = body.findAll('input[type="radio"]')
    expect((radiosAfterReopen[0]!.element as HTMLInputElement).checked).toBe(true) // 기본 사유로 복귀
    expect((radiosAfterReopen[1]!.element as HTMLInputElement).checked).toBe(false)
    expect((body.find('input[type="checkbox"]').element as HTMLInputElement).checked).toBe(false) // 체크 해제
    expect(findExecuteButton()?.attributes('disabled')).toBeDefined() // 다시 비활성

    wrapper.unmount()
  })
})
