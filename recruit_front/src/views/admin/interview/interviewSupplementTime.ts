import { formatDate } from '@/common/dateUtil'

/* 면접 추가사항 관리 화면의 시각 표시·계산. 백엔드 LocalDateTime(타임존 없음)을 그대로 다룬다. */

/** a-date-picker value-format 과 같은 형식 */
export const DATE_TIME_VALUE_FORMAT = 'YYYY-MM-DDTHH:mm:ss'

/** LocalDateTime 문자열을 브라우저 지역 시각으로 해석한다(dateUtil 과 같은 방식). */
const parseLocal = (value: string): Date => {
  const [datePart = '', timePart = '00:00:00'] = value.split('T')
  const [year = 0, month = 1, day = 1] = datePart.split('-').map(Number)
  const [hour = 0, minute = 0, second = 0] = timePart.split(':').map(Number)
  return new Date(year, month - 1, day, hour, minute, second)
}

export const addHours = (value: string, hours: number): string => {
  const date = parseLocal(value)
  date.setHours(date.getHours() + hours)
  return formatDate(date, DATE_TIME_VALUE_FORMAT)
}

/** "10.06 09:30 → 11:30", 날짜가 다르면 끝에도 날짜를 붙인다 */
export const formatWindow = (start: string | null, end: string | null): string => {
  if (!start || !end) return '—'
  const sameDay = formatDate(start, 'YYYY-MM-DD') === formatDate(end, 'YYYY-MM-DD')
  return `${formatDate(start, 'MM.DD HH:mm')} → ${sameDay ? formatDate(end, 'HH:mm') : formatDate(end, 'MM.DD HH:mm')}`
}

/** 관리자 참고용(경고 문구) 판정. 실제 입력 허용은 서버 시각으로만 정해진다. */
export const isWithinWindow = (start: string | null, end: string | null, now = new Date()): boolean => {
  if (!start || !end) return false
  return parseLocal(start) <= now && now < parseLocal(end)
}
