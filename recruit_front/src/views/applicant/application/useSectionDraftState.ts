import { ref } from 'vue'

/**
 * 섹션의 로딩 완료 여부와 마지막으로 서버와 맞춘 시점의 스냅샷을 관리한다.
 * - 로딩 전·실패 상태에서 저장하면 빈 목록으로 서버 데이터를 교체(삭제)하므로 저장을 막는다.
 * - 부모(ApplicationFormView)는 isDirty 로 페이지 이동·제출 전 자동 임시저장과 이탈 확인을 판단한다.
 */
export function useSectionDraftState(serialize: () => unknown) {
  const loaded = ref(false)
  let snapshot = ''

  const current = () => JSON.stringify(serialize())

  /** 조회·저장이 성공한 직후 호출한다. */
  function markSynced(): void {
    snapshot = current()
    loaded.value = true
  }

  function isDirty(): boolean {
    return loaded.value && current() !== snapshot
  }

  function assertLoaded(): void {
    if (!loaded.value) {
      throw new Error('입력 내용을 불러오지 못해 저장할 수 없습니다. 새로고침 후 다시 시도해주세요.')
    }
  }

  return { markSynced, isDirty, assertLoaded }
}
