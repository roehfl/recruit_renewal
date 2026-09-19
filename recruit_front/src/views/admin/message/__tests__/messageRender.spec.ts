import { describe, expect, it } from 'vitest'

import { extractVariableKeys, renderMessage, renderParts, smsByteLength, smsKindOf } from '../messageRender'

describe('smsByteLength', () => {
  it('ASCII는 1byte, 그 밖(한글 등)은 2byte로 센다', () => {
    expect(smsByteLength('abc 123')).toBe(7)
    expect(smsByteLength('신영')).toBe(4)
    expect(smsByteLength('[신영증권] 안내')).toBe(15)
  })

  it('이모지는 코드포인트 1개라 2byte다', () => {
    expect(smsByteLength('😀')).toBe(2)
  })

  it('빈 문자열은 0byte다', () => {
    expect(smsByteLength('')).toBe(0)
  })
})

describe('smsKindOf', () => {
  it('90byte 이하는 SMS, 2000byte 이하는 LMS, 초과는 null이다', () => {
    expect(smsKindOf(90)).toBe('SMS')
    expect(smsKindOf(91)).toBe('LMS')
    expect(smsKindOf(2000)).toBe('LMS')
    expect(smsKindOf(2001)).toBeNull()
  })
})

describe('renderMessage', () => {
  it('변수를 값으로 한 번만 치환하고 없는 값은 빈 문자열로 둔다', () => {
    const values = { 이름: '김#{공고명}', 공고명: '2026 공채', 전형명: '' }
    expect(renderMessage('#{이름}님 #{공고명} #{전형명}결과 #{도착시각}', values)).toBe(
      '김#{공고명}님 2026 공채 결과 ',
    )
  })

  it('치환 전에 줄바꿈을 LF로 통일한다', () => {
    expect(renderMessage('a\r\nb\rc\nd', {})).toBe('a\nb\nc\nd')
  })
})

describe('renderParts', () => {
  it('값이 있으면 value, 없거나 비면 missing 으로 나눈다', () => {
    expect(renderParts('안녕 #{이름}님 #{도착시각}까지', { 이름: '김', 도착시각: '' })).toEqual([
      { text: '안녕 ', kind: 'text' },
      { text: '김', kind: 'value' },
      { text: '님 ', kind: 'text' },
      { text: '#{도착시각}', kind: 'missing' },
      { text: '까지', kind: 'text' },
    ])
  })

  it('프로토타입 프로퍼티(#{constructor})는 값으로 보지 않는다', () => {
    expect(renderMessage('#{constructor}', {})).toBe('')
    expect(renderParts('#{constructor}', {})).toEqual([{ text: '#{constructor}', kind: 'missing' }])
  })
})

describe('extractVariableKeys', () => {
  it('본문의 #{키}를 처음 나온 순서대로 중복 없이 돌려준다', () => {
    expect(extractVariableKeys('#{이름}님 #{공고명} #{이름}')).toEqual(['이름', '공고명'])
    expect(extractVariableKeys('변수 없음')).toEqual([])
  })
})
