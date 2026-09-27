/**
 * 지원자 새 비밀번호 조합 규칙(가입·재설정·변경 공통). 백엔드 PasswordPolicy 와 같은 규칙이다.
 *
 * 영문 대문자·영문 소문자·숫자·특수문자 중 2종류 이상이면 10자 이상, 3종류 이상이면 8자 이상.
 * 공백은 종류로 세지 않고, 영문·숫자·공백이 아닌 글자(한글 포함)는 특수문자로 센다.
 */
export const PASSWORD_POLICY_MESSAGE =
  '비밀번호는 영문 대문자·소문자·숫자·특수문자 중 2종류 이상이면 10자 이상, 3종류 이상이면 8자 이상이어야 합니다.'

export const PASSWORD_POLICY_PLACEHOLDER = '영문 대/소문자·숫자·특수문자 3종류 8자 이상 또는 2종류 10자 이상'

/** BCrypt 상한. 넘으면 서버가 400 을 준다(한글은 글자당 3바이트). */
const MAX_BYTES = 72

export function isPasswordAcceptable(password: string): boolean {
  let upper = false
  let lower = false
  let digit = false
  let special = false
  for (const c of password) {
    if (c >= 'A' && c <= 'Z') upper = true
    else if (c >= 'a' && c <= 'z') lower = true
    else if (c >= '0' && c <= '9') digit = true
    else if (c.trim() !== '') special = true
  }
  const kinds = [upper, lower, digit, special].filter(Boolean).length
  const length = [...password].length
  return (kinds >= 3 && length >= 8) || (kinds >= 2 && length >= 10)
}

/** 규칙 위반이면 안내 문구, 통과면 null. 길이 상한(100자·72바이트)도 함께 본다. */
export function passwordPolicyError(password: string): string | null {
  if (!isPasswordAcceptable(password)) return PASSWORD_POLICY_MESSAGE
  if ([...password].length > 100) return '비밀번호는 100자 이하여야 합니다.'
  if (new TextEncoder().encode(password).length > MAX_BYTES) return '비밀번호는 72바이트 이하여야 합니다(한글은 약 24자).'
  return null
}
