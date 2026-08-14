package com.shinyoung.recruit.exception;

/**
 * CommonCode 입력/제약 위반 시 발생(400). 필수값 누락, (groupCode, code) 중복 등.
 */
public class InvalidCommonCodeException extends RuntimeException {

    public InvalidCommonCodeException(String message) {
        super(message);
    }
}
