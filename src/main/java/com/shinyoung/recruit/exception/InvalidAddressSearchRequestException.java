package com.shinyoung.recruit.exception;

/**
 * 주소 검색 요청 파라미터가 올바르지 않을 때 발생(예: 검색어 공백). 400으로 매핑된다.
 */
public class InvalidAddressSearchRequestException extends RuntimeException {

    public InvalidAddressSearchRequestException(String message) {
        super(message);
    }
}
