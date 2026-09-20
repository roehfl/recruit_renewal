import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import Antd from 'ant-design-vue'

import '../../retention/__tests__/antdJsdomPolyfill'
import NoticeEditorDrawer from '../NoticeEditorDrawer.vue'
import type { AdminNoticeDetail } from '@/types/notice'

/*
 * 공지 등록·수정 드로어의 저장 분기 회귀 테스트.
 * 삭제 상태는 수정 API가 아니라 별도 엔드포인트(delete/restore)로만 바뀌므로,
 * "바뀐 경우에만 한 번" 호출되는지가 핵심이다. 잘못 호출하면 멀쩡한 공지가 내려가거나
 * 내려둔 공지가 지원자 화면에 다시 뜬다.
 */

const fetchNoticeDetailMock = vi.fn()
const createNoticeMock = vi.fn()
const updateNoticeMock = vi.fn()
const deleteNoticeMock = vi.fn()
const restoreNoticeMock = vi.fn()

vi.mock('@/api/admin/adminNoticeApi', () => ({
  adminNoticeApi: {
    fetchNoticeDetail: (...args: unknown[]) => fetchNoticeDetailMock(...args),
    createNotice: (...args: unknown[]) => createNoticeMock(...args),
    updateNotice: (...args: unknown[]) => updateNoticeMock(...args),
    deleteNotice: (...args: unknown[]) => deleteNoticeMock(...args),
    restoreNotice: (...args: unknown[]) => restoreNoticeMock(...args),
  },
}))

const baseDetail: AdminNoticeDetail = {
  id: 7,
  title: '기존 공지',
  contentHtml: '<p>기존 본문</p>',
  pinned: false,
  deleted: false,
  createdAt: '2026-09-01T10:00:00',
  createdBy: null,
  updatedAt: null,
  updatedBy: null,
}

const mountDrawer = (noticeId: number | null) =>
  mount(NoticeEditorDrawer, {
    attachTo: document.body,
    props: { open: true, noticeId },
    global: { plugins: [Antd] },
  })

const findButton = (label: string) =>
  [...document.body.querySelectorAll('button')].find((button) => button.textContent?.includes(label))

const clickSave = async (): Promise<void> => {
  findButton('저장')?.click()
  await flushPromises()
}

/** 드로어 안의 스위치를 순서대로 찾는다(0: 삭제, 1: 상단 고정). */
const toggleSwitch = async (index: number): Promise<void> => {
  const switches = document.body.querySelectorAll<HTMLElement>('button.ant-switch')
  switches[index]?.click()
  await flushPromises()
}

const typeContent = async (html: string): Promise<void> => {
  const editor = document.body.querySelector<HTMLElement>('[contenteditable="true"]')
  if (!editor) throw new Error('editor not found')
  editor.innerHTML = html
  editor.dispatchEvent(new Event('input'))
  await flushPromises()
}

const typeTitle = async (title: string): Promise<void> => {
  const input = document.body.querySelector<HTMLInputElement>('input.ant-input')
  if (!input) throw new Error('title input not found')
  input.value = title
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

beforeEach(() => {
  document.body.innerHTML = ''
  fetchNoticeDetailMock.mockReset()
  createNoticeMock.mockReset()
  updateNoticeMock.mockReset()
  deleteNoticeMock.mockReset()
  restoreNoticeMock.mockReset()

  createNoticeMock.mockResolvedValue({ data: { data: 101 } })
  updateNoticeMock.mockResolvedValue({ data: { data: 7 } })
  deleteNoticeMock.mockResolvedValue({ data: { data: null } })
  restoreNoticeMock.mockResolvedValue({ data: { data: null } })
})

describe('NoticeEditorDrawer - 저장 분기', () => {
  it('삭제 상태를 바꾸지 않은 수정은 delete·restore를 호출하지 않는다', async () => {
    fetchNoticeDetailMock.mockResolvedValueOnce({ data: { data: baseDetail } })
    const wrapper = mountDrawer(7)
    await flushPromises()

    await clickSave()

    expect(updateNoticeMock).toHaveBeenCalledTimes(1)
    expect(deleteNoticeMock).not.toHaveBeenCalled()
    expect(restoreNoticeMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('삭제 토글을 켜고 저장하면 수정 뒤 delete를 호출한다', async () => {
    fetchNoticeDetailMock.mockResolvedValueOnce({ data: { data: baseDetail } })
    const wrapper = mountDrawer(7)
    await flushPromises()

    await toggleSwitch(0)
    await clickSave()

    expect(updateNoticeMock).toHaveBeenCalledTimes(1)
    expect(deleteNoticeMock).toHaveBeenCalledWith(7)
    expect(restoreNoticeMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('삭제된 공지에서 토글을 끄고 저장하면 restore를 호출한다', async () => {
    fetchNoticeDetailMock.mockResolvedValueOnce({
      data: { data: { ...baseDetail, deleted: true } },
    })
    const wrapper = mountDrawer(7)
    await flushPromises()

    await toggleSwitch(0)
    await clickSave()

    expect(restoreNoticeMock).toHaveBeenCalledWith(7)
    expect(deleteNoticeMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('등록은 create 뒤 새 id로 delete를 호출한다(삭제 상태로 등록한 경우)', async () => {
    const wrapper = mountDrawer(null)
    await flushPromises()

    await typeTitle('새 공지')
    await typeContent('<p>새 본문</p>')
    await toggleSwitch(0)
    await clickSave()

    expect(createNoticeMock).toHaveBeenCalledWith({
      title: '새 공지',
      content: '<p>새 본문</p>',
      isPinned: false,
    })
    expect(deleteNoticeMock).toHaveBeenCalledWith(101)
    wrapper.unmount()
  })

  it('제목이 비어 있으면 저장 API를 부르지 않는다', async () => {
    const wrapper = mountDrawer(null)
    await flushPromises()

    await typeContent('<p>본문만 있음</p>')
    await clickSave()

    expect(createNoticeMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('본문이 비어 있으면 저장 API를 부르지 않는다', async () => {
    const wrapper = mountDrawer(null)
    await flushPromises()

    await typeTitle('제목만 있음')
    await clickSave()

    expect(createNoticeMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
