package com.shinyoung.recruit.exception;

/**
 * School 입력/제약 위반 시 발생(400). 필수값 누락, schoolCode 중복 등.
 */
public class InvalidSchoolException extends RuntimeException {

    public InvalidSchoolException(String message) {
        super(message);
    }
}
