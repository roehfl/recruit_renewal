package com.shinyoung.recruit.exception;

/**
 * 외부 주소 검색(juso.go.kr) 호출 실패(승인키 미설정, 네트워크/타임아웃, 상위 API 오류코드 등).
 * 502(Bad Gateway)로 매핑된다. 내부 상세(승인키/오류코드)는 로깅만 하고 클라이언트에는 일반 메시지를 노출한다.
 */
public class AddressSearchException extends RuntimeException {

    public AddressSearchException(String message) {
        super(message);
    }

    public AddressSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
