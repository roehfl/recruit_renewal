export type FunnelDimension = 'POSITION' | 'SCHOOL' | 'CERTIFICATE'

/*
 * 한 단계의 결과 분포. 분모는 P(공고 전체 지원자)이므로 7버킷 합계가 항상 P다.
 * 화면에서 "그 단계 대상자 중 구성"으로 보이려면 noResult를 제외한 6버킷으로 정규화해야 한다.
 */
export interface StageDistribution {
  passed: number
  failed: number
  absent: number
  hold: number
  pending: number
  withdrawn: number
  noResult: number
}

export interface StageFunnel {
  stageOrder: number | null
  stageId: number
  stageName: string
  stageType: string
  distribution: StageDistribution
  funnelPassedCount: number
  cumulativeRate: number
  stepConversionRate: number
  /* 단계 간 평균 소요일. 표본이 없으면 null이며 0과 구분해야 한다. */
  averageDwellDays: number | null
}

export interface FunnelPopulation {
  p: number
  currentlySubmittedCount: number
  withdrawnCount: number
}

export interface DimensionFunnel {
  groupId: number | null
  groupName: string
  population: FunnelPopulation
  stages: StageFunnel[]
}

export interface DimensionGroup {
  dimension: FunnelDimension
  groups: DimensionFunnel[]
}

/*
 * 백엔드 응답의 dimension/dimensions는 단일 축 요청 전용 하위호환 필드다.
 * 대시보드는 다중 축을 요청하므로 dimensionGroups만 사용한다.
 */
export interface FunnelResult {
  jobPostingId: number
  jobPostingTitle: string
  population: FunnelPopulation
  stages: StageFunnel[]
  dimensionGroups: DimensionGroup[]
}

export interface ApplicationDailyPoint {
  date: string
  submittedCount: number
  cumulativeCount: number
}

export interface ApplicationDaily {
  jobPostingId: number
  jobPostingTitle: string
  from: string
  to: string
  totalSubmitted: number
  days: ApplicationDailyPoint[]
}
