import type { WorkLocationOption } from '@/types/jobPosting'

/**
 * 관리자 공고 등록·수정 폼이 다루는 모집분야.
 *
 * 공고 목록·상세 타입(`AdminJobPostingListItem`, `AdminJobPostingDetail`)과
 * 그 부속 타입은 `@/types/jobPosting` 이 단일 출처다. 여기에 중복 선언하지 않는다.
 */
export interface AdminJobPosition {
  id: number | null
  positionName: string
  applicationType: 'NEW_GRADUATE' | 'EXPERIENCED' | 'NEW_GRADUATE_OR_EXPERIENCED'
  jobTitle: string | null
  workLocations: WorkLocationOption[]
  employmentType: 'FULL_TIME' | 'CONTRACT' | 'INTERN' | 'FREELANCE' | 'PART_TIME' | 'ETC'
  sortOrder: number
}
