/** 팝업이 NICE 표준창으로 POST 할 암호문. */
export interface NiceRequestResponse {
  encodeData: string
}

/**
 * 결과 교환 응답. ci 는 없다 — 서버 세션에만 보관한다.
 */
export interface NiceResultResponse {
  status: 'SUCCESS'
  name: string
  phoneNumber: string
}

/** 팝업이 부모창으로 보내는 postMessage 페이로드. */
export interface NiceAuthMessage {
  source: 'nice-auth'
  status: 'SUCCESS' | 'FAIL'
  name?: string
  phoneNumber?: string
}

/** NICE 표준창 엔드포인트. 레거시와 동일하다. */
export const NICE_CHECKPLUS_ACTION = 'https://nice.checkplus.co.kr/CheckPlusSafeModel/checkplus.cb'

/** NICE 규격상 고정값. 'Serivce' 오타는 규격 그대로이므로 고치지 않는다. */
export const NICE_CHECKPLUS_M = 'checkplusSerivce'

/** postMessage 식별자. 다른 라이브러리 메시지와 섞이지 않게 한다. */
export const NICE_MESSAGE_SOURCE = 'nice-auth'

/** 본인확인 용도. 백엔드 NiceVerificationPurpose 와 같은 값이다. 서버가 발급 시 기록하고 소비 시 대조한다. */
export const NICE_PURPOSES = ['SIGNUP', 'FIND_EMAIL'] as const

export type NiceVerificationPurpose = (typeof NICE_PURPOSES)[number]

/** 팝업 쿼리(?purpose=)로 받은 값이 알려진 용도인지 확인한다. */
export function isNicePurpose(value: unknown): value is NiceVerificationPurpose {
  return typeof value === 'string' && (NICE_PURPOSES as readonly string[]).includes(value)
}
