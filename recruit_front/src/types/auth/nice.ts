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
