package com.shinyoung.recruit.exception;

/**
 * 메뉴 입력/제약 위반 시 발생(400). 같은 사이트 안의 path 중복 등.
 */
public class InvalidMenuException extends RuntimeException {

    public InvalidMenuException(String message) {
        super(message);
    }
}
