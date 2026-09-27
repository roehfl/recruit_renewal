import { describe, expect, it } from 'vitest'
import { isPasswordAcceptable, passwordPolicyError, PASSWORD_POLICY_MESSAGE } from '@/common/passwordPolicy'

// 백엔드 PasswordPolicyTest 와 같은 사례다. 두 규칙이 어긋나면 화면은 통과시키고 서버가 400 을 준다.
describe('passwordPolicy', () => {
  it('3종류 이상이면 8자부터 통과한다', () => {
    expect(isPasswordAcceptable('Abcdef1!')).toBe(true)
    expect(isPasswordAcceptable('abcdef1!')).toBe(true)
    expect(isPasswordAcceptable('Abcde1!')).toBe(false)
  })

  it('2종류면 10자부터 통과한다', () => {
    expect(isPasswordAcceptable('abcdefgh12')).toBe(true)
    expect(isPasswordAcceptable('abcdefg12')).toBe(false)
  })

  it('1종류는 길어도 거부하고, 공백은 종류로 세지 않으며 한글은 특수문자로 센다', () => {
    expect(isPasswordAcceptable('abcdefghijklmnop')).toBe(false)
    expect(isPasswordAcceptable('abcd efgh ij')).toBe(false)
    expect(isPasswordAcceptable('비밀번호abcd12')).toBe(true)
  })

  it('위반 문구를 돌려주고 72바이트 상한도 본다', () => {
    expect(passwordPolicyError('abcdefg12')).toBe(PASSWORD_POLICY_MESSAGE)
    expect(passwordPolicyError('Aa1!' + '가'.repeat(23))).toContain('72바이트')
    expect(passwordPolicyError('NewPassword1!')).toBeNull()
  })
})
