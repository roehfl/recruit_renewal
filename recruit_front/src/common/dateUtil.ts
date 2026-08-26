/*    사용 예
import { formatDate } from '@/common/dateUtil'

<span>
    {{ formatDate(item.createdAt, 'YYYY-MM-DD HH:mm) }}
    {{ formatDate(item.createdAt, 'YYYY.MM.DD HH:mm) }}
</span>
*/

type DateInput = Date | string | number | null | undefined
type DateFormatToken = 'YYYY' | 'MM' | 'DD' | 'HH' | 'mm' | 'ss'

const pad2 = (value: number): string => String(value).padStart(2, '0')

const toDate = (value: DateInput): Date | null => {
  if (value === null || value === undefined || value === '') {
    return null
  }

  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : value
  }

  if (typeof value === 'number') {
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? null : date
  }

  if (typeof value === 'string') {
    // Backend LocalDateTime 예: 2026-05-08T15:03:42
    // 브라우저별 Date 파싱 차이를 줄이기 위해 직접 파싱
    const match = value.match(
      /^(\d{4})-(\d{2})-(\d{2})(?:[T\s](\d{2}):(\d{2})(?::(\d{2}))?)?/,
    )

    if (match) {
      const [, year, month, day, hour = '00', minute = '00', second = '00'] = match

      const date = new Date(
        Number(year),
        Number(month) - 1,
        Number(day),
        Number(hour),
        Number(minute),
        Number(second),
      )

      return Number.isNaN(date.getTime()) ? null : date
    }

    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? null : date
  }

  return null
}

export const formatDate = (
  value: DateInput,
  format = 'YYYY-MM-DD',
): string => {
  const date = toDate(value)

  if (!date) {
    return ''
  }

  const tokens: Record<DateFormatToken, string> = {
    YYYY: String(date.getFullYear()),
    MM: pad2(date.getMonth() + 1),
    DD: pad2(date.getDate()),
    HH: pad2(date.getHours()),
    mm: pad2(date.getMinutes()),
    ss: pad2(date.getSeconds()),
  }

  return format.replace(/YYYY|MM|DD|HH|mm|ss/g, (token) => {
    return tokens[token as DateFormatToken]
  })
}

// 접수 마감까지 남은 일수. 지난 공고는 '마감', 당일은 'D-DAY'.
export const getDDay = (endDateTime: DateInput): string => {
  const endDate = toDate(endDateTime)

  if (!endDate) {
    return ''
  }

  const diffTime = endDate.getTime() - new Date().getTime()
  if (diffTime < 0) return '마감'

  const diffDay = Math.floor(diffTime / (1000 * 60 * 60 * 24))
  if (diffDay === 0) return 'D-DAY'

  return `D-${diffDay}`
}
