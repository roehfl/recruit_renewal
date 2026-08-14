// 지원서 작성 완성도(대시보드) 응답 타입.
// 백엔드 ApplicationDashboardResponse / ApplicationCompletionReadChecker 결과에 대응한다.
// 정확한 타입·검증은 백엔드 DTO가 단일 출처이며, 여기서는 프론트 사용에 필요한 필드 모양만 정의한다.

export interface ApplicationCompletionSummary {
  requiredSectionCount: number
  completedRequiredSectionCount: number
  requiredMissingCount: number
  optionalSectionCount: number
  completedOptionalSectionCount: number
  optionalIncompleteCount: number
  requiredCompletionRate: number
  submitBlockingIssueCount: number
}

export interface ApplicationSectionReadiness {
  sectionCode: string
  sectionName: string
  required: boolean
  complete: boolean
  reasonCode?: string | null
  message?: string | null
}

export interface ApplicationDashboardResponse {
  applicationId: number
  jobPostingId: number
  jobPostingTitle?: string | null
  jobPositionName?: string | null
  applicationStatus?: string
  accepting: boolean
  editable: boolean
  submittable: boolean
  withdrawable: boolean
  submittedAt?: string | null
  withdrawnAt?: string | null
  completionSummary: ApplicationCompletionSummary
  requiredMissingSections: ApplicationSectionReadiness[]
  optionalIncompleteSections: ApplicationSectionReadiness[]
  latestAnnouncedStageName?: string | null
  latestResultStatus?: string | null
}
