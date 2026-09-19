/*
 * 메시지 본문 규칙. 백엔드 MessageRenderer 와 같은 규칙·같은 테스트 예시를 유지한다(설계서 6절).
 * 치환 결과는 미리보기용이며, 실제 발송 내용은 서버가 다시 계산한다.
 */

export type SmsKind = 'SMS' | 'LMS'

export const SMS_MAX_BYTES = 90
export const LMS_MAX_BYTES = 2000

/** 작성 영역 SMS 카운터. current = 미리보기 수신자 기준, max·kind = 선택 수신자 중 가장 긴 것 기준. */
export interface SmsStats {
  currentBytes: number
  maxBytes: number
  kind: SmsKind | null
}

const VARIABLE_PATTERN = /#\{([^}]+)\}/g

export interface RenderPart {
  text: string
  /** text: 본문 그대로, value: 치환된 값, missing: 값이 없어 #{키}를 그대로 둔 자리 */
  kind: 'text' | 'value' | 'missing'
}

/** 문자 코드 127 이하는 1byte, 그 밖(한글 등)은 2byte. `[Web발신]` 머리말은 세지 않는다. */
export const smsByteLength = (text: string): number => {
  let bytes = 0
  for (const character of text) {
    bytes += (character.codePointAt(0) ?? 0) <= 127 ? 1 : 2
  }
  return bytes
}

/** 90byte 이하 SMS, 2000byte 이하 LMS, 그보다 길면 보낼 수 없어 null. */
export const smsKindOf = (bytes: number): SmsKind | null => {
  if (bytes <= SMS_MAX_BYTES) return 'SMS'
  if (bytes <= LMS_MAX_BYTES) return 'LMS'
  return null
}

/** 줄바꿈을 LF로 통일한다. 서버도 치환 전에 같은 처리를 한다. */
export const normalizeNewlines = (text: string): string => text.replace(/\r\n?/g, '\n')

/** 프로토타입 프로퍼티(`#{constructor}` 등)를 값으로 오인하지 않도록 own-property만 본다. */
const valueOf = (values: Record<string, string>, key: string): string =>
  Object.prototype.hasOwnProperty.call(values, key) ? (values[key] ?? '') : ''

/** 줄바꿈 통일 후 #{키}를 한 번만 치환한다. 값이 없으면 빈 문자열(서버 render 와 같다). */
export const renderMessage = (text: string, values: Record<string, string>): string =>
  normalizeNewlines(text).replace(VARIABLE_PATTERN, (_match, key: string) => valueOf(values, key))

/** 미리보기 강조용으로 치환 결과를 조각으로 나눈다. v-html 없이 그리기 위해 쓴다. */
export const renderParts = (text: string, values: Record<string, string>): RenderPart[] => {
  const normalized = normalizeNewlines(text)
  const parts: RenderPart[] = []
  let last = 0
  for (const match of normalized.matchAll(VARIABLE_PATTERN)) {
    const index = match.index ?? 0
    if (index > last) {
      parts.push({ text: normalized.slice(last, index), kind: 'text' })
    }
    const value = valueOf(values, match[1] ?? '')
    parts.push(value ? { text: value, kind: 'value' } : { text: match[0], kind: 'missing' })
    last = index + match[0].length
  }
  if (last < normalized.length) {
    parts.push({ text: normalized.slice(last), kind: 'text' })
  }
  return parts
}

/** 본문에 쓴 #{키}를 처음 나온 순서대로 중복 없이 돌려준다. */
export const extractVariableKeys = (text: string): string[] => {
  const keys: string[] = []
  for (const match of text.matchAll(VARIABLE_PATTERN)) {
    const key = match[1] ?? ''
    if (!keys.includes(key)) {
      keys.push(key)
    }
  }
  return keys
}
