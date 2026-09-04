import { ref, type Ref } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { adminStageApi } from '@/api/admin/adminStageApi'
import { getApiErrorMessage } from '@/api/apiError'
import { DECIDABLE_RESULT_STATUSES, STAGE_RESULT_STATUS_LABELS } from '@/types/admin/stage'
import type { AdminStageResult, StageListItem } from '@/types/admin/stage'

/**
 * 전형 단계 라이프사이클 명령(대상자 초기화 · 전형 시작 · 결과 발표 · 단계 마감).
 *
 * 본체 뷰가 소유한 선택 상태와 로딩 함수를 주입받아, 확인 모달 → API 호출 → 재조회까지를 담당한다.
 * 본체에서 분리한 이유는 엑셀·정정이 얹히면 한 파일이 900줄을 넘기 때문이다(S2 통합 리뷰 권고).
 */
export interface StageLifecycleDeps {
  selectedJobPostingId: Ref<number | null>
  selectedStageId: Ref<number | null>
  stages: Ref<StageListItem[]>
  results: Ref<AdminStageResult[]>
  loadStages: () => Promise<void>
  loadResults: () => Promise<void>
  /** 미저장 판정이 있으면 확인을 받는다. 발표 전에만 쓴다. */
  confirmDiscardIfDirty: () => Promise<boolean>
  /** 공고·단계 선택을 주소창 쿼리에 반영한다. */
  syncQuery: () => void
}

export function useStageLifecycle(deps: StageLifecycleDeps) {
  const {
    selectedJobPostingId,
    selectedStageId,
    stages,
    results,
    loadStages,
    loadResults,
    confirmDiscardIfDirty,
    syncQuery,
  } = deps

  const commandRunning = ref(false)

  /** 단계 목록과 결과를 함께 다시 읽는다. 상태 전이 후 배너·버튼이 어긋나지 않게 한다. */
  const reloadStageAndResults = async () => {
    const keepStageId = selectedStageId.value
    await loadStages()
    if (keepStageId !== null && stages.value.some((stage) => stage.id === keepStageId)) {
      selectedStageId.value = keepStageId
    }
    await loadResults()
    // 단계가 사라져 폴백으로 다른 단계를 골랐으면 URL 도 맞춘다. 안 그러면 새로고침마다 폴백이 반복된다.
    syncQuery()
  }

  /*
   * 명령 실행 → 재조회 → 결과 알림. successMessage 를 함수로 주면 응답을 문구에 쓸 수 있다
   * (대상자 초기화가 신규·기존·제외 건수를 알려야 해서 필요하다).
   */
  const runCommand = async <T,>(
    action: () => Promise<T>,
    successMessage: string | ((result: T) => string),
    failMessage: string,
  ) => {
    commandRunning.value = true
    try {
      const result = await action()
      await reloadStageAndResults()
      message.success(typeof successMessage === 'function' ? successMessage(result) : successMessage)
    } catch (error) {
      // 가드 실패의 가장 흔한 원인이 "화면이 stale"(다른 관리자가 먼저 전이시킴, 공고 게시 취소)이다.
      // 재조회하지 않으면 배너가 서버와 어긋난 채로 남아 같은 실패가 반복된다.
      await reloadStageAndResults()
      message.error(getApiErrorMessage(error, failMessage))
    } finally {
      commandRunning.value = false
    }
  }

  interface ConfirmThenRunOptions<T> {
    title: string
    content: string
    okText: string
    action: () => Promise<T>
    success: string | ((result: T) => string)
    fail: string
  }

  /** 확인 모달 → runCommand. 라이프사이클 명령 4개가 공유한다. */
  const confirmThenRun = <T,>(options: ConfirmThenRunOptions<T>) => {
    Modal.confirm({
      title: options.title,
      content: options.content,
      okText: options.okText,
      cancelText: '취소',
      onOk: () => runCommand(options.action, options.success, options.fail),
    })
  }

  const initializeResults = () => {
    if (selectedStageId.value === null) {
      return
    }
    const stageId = selectedStageId.value
    confirmThenRun({
      title: '대상자를 불러올까요?',
      content:
        '제출 완료 지원서를 이 단계의 대상자로 등록합니다. 이미 등록된 대상자는 그대로 두고 새로 제출된 지원서만 추가합니다.',
      okText: '불러오기',
      action: () => adminStageApi.initializeResults(stageId),
      success: (response) => {
        const { createdCount, existingCount, skippedCount } = response.data.data
        return `신규 ${createdCount}건 · 기존 ${existingCount}건 · 제외 ${skippedCount}건`
      },
      fail: '대상자를 불러오지 못했습니다.',
    })
  }

  const startStage = () => {
    if (selectedJobPostingId.value === null || selectedStageId.value === null) {
      return
    }
    const jobPostingId = selectedJobPostingId.value
    const stageId = selectedStageId.value
    confirmThenRun({
      title: '전형을 시작할까요?',
      content: '시작하면 결과를 판정할 수 있고, 단계 이름·유형·순서는 더 이상 바꿀 수 없습니다.',
      okText: '시작',
      action: () => adminStageApi.startStage(jobPostingId, stageId),
      success: '전형을 시작했습니다.',
      fail: '전형을 시작하지 못했습니다.',
    })
  }

  const announceStage = async () => {
    if (selectedJobPostingId.value === null || selectedStageId.value === null) {
      return
    }
    // 발표는 되돌릴 수 없다. 미저장 판정을 남긴 채 발표하면 그 변경이 영영 반영되지 않는다.
    if (!(await confirmDiscardIfDirty())) {
      return
    }
    const jobPostingId = selectedJobPostingId.value
    const stageId = selectedStageId.value
    const summary = DECIDABLE_RESULT_STATUSES.map((status) => ({
      label: STAGE_RESULT_STATUS_LABELS[status],
      count: results.value.filter((result) => result.resultStatus === status).length,
    }))
      .filter((entry) => entry.count > 0)
      .map((entry) => `${entry.label} ${entry.count}`)
      .join(' · ')
    confirmThenRun({
      title: '결과를 발표할까요?',
      content: `대상 ${results.value.length}명 (${summary}). 발표하면 지원자에게 결과가 공개되고, 이후 변경은 사유를 남기는 정정으로만 가능합니다.`,
      okText: '발표',
      action: () => adminStageApi.announceStage(jobPostingId, stageId),
      success: '결과를 발표했습니다.',
      fail: '결과를 발표하지 못했습니다.',
    })
  }

  const closeStage = () => {
    if (selectedJobPostingId.value === null || selectedStageId.value === null) {
      return
    }
    const jobPostingId = selectedJobPostingId.value
    const stageId = selectedStageId.value
    confirmThenRun({
      title: '단계를 마감할까요?',
      content: '마감하면 이 단계의 상태를 더 이상 되돌릴 수 없습니다.',
      okText: '마감',
      action: () => adminStageApi.closeStage(jobPostingId, stageId),
      success: '단계를 마감했습니다.',
      fail: '단계를 마감하지 못했습니다.',
    })
  }

  return {
    commandRunning,
    reloadStageAndResults,
    initializeResults,
    startStage,
    announceStage,
    closeStage,
  }
}
