import { formatDate } from '@/common/dateUtil'

/**
 * 로그 조회 두 탭이 공유하는 기간 규칙.
 *
 * 백엔드(AuditActivityReadService·ClientEventLogReadService)는 Duration.between(from, to) 이
 * 90일을 넘으면 400 을 낸다. 화면은 날짜만 고르고 시작일T00:00:00 ~ 종료일T23:59:59 를 보내므로
 * 날짜 차이가 89일이면 89일 23:59:59 로 통과하고, 90일이면 90일 23:59:59 라 400 이 난다.
 * 그래서 화면 표기·프리셋·검증은 모두 "시작일·종료일을 포함한 일수" 기준이다.
 */

export const MAX_RANGE_DAYS = 90

export type DateRange = [string, string]

/** 프리셋 버튼(포함 일수). */
export const RANGE_PRESETS = [1, 7, 30, MAX_RANGE_DAYS] as const

/** 두 탭 목록의 페이지 크기. 서버 상한은 100 이다. */
export const LOG_PAGE_SIZE = 20

const MS_PER_DAY = 24 * 60 * 60 * 1000

const toDateOnly = (value: string): Date => {
  const parts = value.split('-')
  return new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]))
}

/** 종료일을 today 로 두고, 그 날을 포함해 days 일치 범위를 만든다. */
export const presetRange = (days: number, today: Date): DateRange => {
  const from = new Date(today.getFullYear(), today.getMonth(), today.getDate() - (days - 1))
  return [formatDate(from), formatDate(today)]
}

/** 시작일·종료일을 포함한 일수. 같은 날이면 1이다. */
const rangeDays = (from: string, to: string): number =>
  Math.round((toDateOnly(to).getTime() - toDateOnly(from).getTime()) / MS_PER_DAY) + 1

/** 조회 전에 부르는 검증. 통과하면 null, 아니면 화면에 띄울 한글 메시지. */
export const rangeError = (from: string, to: string): string | null => {
  const days = rangeDays(from, to)
  if (days <= 0) return '시작일이 종료일보다 늦습니다.'
  if (days > MAX_RANGE_DAYS) return `조회 기간은 시작일·종료일 포함 ${MAX_RANGE_DAYS}일까지입니다.`
  return null
}

/** 날짜 범위를 서버가 받는 ISO date-time 으로 바꾼다. */
export const toDateTimeRange = (range: DateRange): { from: string; to: string } => ({
  from: `${range[0]}T00:00:00`,
  to: `${range[1]}T23:59:59`,
})
