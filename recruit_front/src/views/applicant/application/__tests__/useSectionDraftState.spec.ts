import { describe, expect, it } from 'vitest'
import { reactive } from 'vue'

import { useSectionDraftState } from '@/views/applicant/application/useSectionDraftState'

describe('useSectionDraftState', () => {
  it('조회 전에는 저장을 막고 변경 없음으로 본다', () => {
    const items = reactive<string[]>([])
    const draftState = useSectionDraftState(() => items)

    expect(() => draftState.assertLoaded()).toThrow('불러오지 못해 저장할 수 없습니다')
    items.push('입력')
    expect(draftState.isDirty()).toBe(false)
  })

  it('조회·저장 시점 이후 입력이 바뀌면 변경으로 본다', () => {
    const items = reactive<string[]>(['학력1'])
    const draftState = useSectionDraftState(() => items)

    draftState.markSynced()
    expect(() => draftState.assertLoaded()).not.toThrow()
    expect(draftState.isDirty()).toBe(false)

    items.push('학력2')
    expect(draftState.isDirty()).toBe(true)

    draftState.markSynced()
    expect(draftState.isDirty()).toBe(false)
  })
})
