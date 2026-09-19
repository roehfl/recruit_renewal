import { reactive } from 'vue'
import { message } from 'ant-design-vue'

export type EditableField = 'mailSubject' | 'mailBody' | 'smsBody'

/** 입력란 글자 수 제한. 백엔드 요청 DTO @Size 와 같다. */
export const FIELD_MAX_LENGTH: Record<EditableField, number> = {
  mailSubject: 200,
  mailBody: 10000,
  smsBody: 2000,
}

/**
 * 변수 칩으로 #{키}를 넣는다. 입력란 blur 때 커서 위치를 기억하고(칩 클릭 전에 blur 가 먼저 일어난다),
 * 기억이 없으면 메일 본문 끝에 넣는다. 글자 수 제한을 넘으면 넣지 않는다.
 */
export const useVariableCursor = (
  read: (field: EditableField) => string,
  write: (field: EditableField, value: string) => void,
) => {
  const cursor = reactive<{ field: EditableField; start: number; end: number }>({
    field: 'mailBody',
    start: Number.MAX_SAFE_INTEGER,
    end: Number.MAX_SAFE_INTEGER,
  })

  const resetCursor = (field: EditableField = 'mailBody'): void => {
    cursor.field = field
    cursor.start = Number.MAX_SAFE_INTEGER
    cursor.end = Number.MAX_SAFE_INTEGER
  }

  const rememberCursor = (field: EditableField, event: Event): void => {
    const target = event.target as HTMLInputElement | HTMLTextAreaElement
    cursor.field = field
    cursor.start = target.selectionStart ?? read(field).length
    cursor.end = target.selectionEnd ?? cursor.start
  }

  const insertVariable = (key: string): void => {
    const token = `#{${key}}`
    const value = read(cursor.field)
    const start = Math.min(cursor.start, value.length)
    const end = Math.min(cursor.end, value.length)
    const next = value.slice(0, start) + token + value.slice(end)
    if (next.length > FIELD_MAX_LENGTH[cursor.field]) {
      message.warning('글자 수 제한을 넘어 변수를 넣을 수 없습니다.')
      return
    }
    write(cursor.field, next)
    cursor.start = start + token.length
    cursor.end = cursor.start
  }

  return { resetCursor, rememberCursor, insertVariable }
}
