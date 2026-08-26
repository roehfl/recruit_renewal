package com.shinyoung.recruit.exception;

/**
 * FAQ 입력/제약 위반 시 발생(400). 필수값 누락, 카테고리명 중복, reorder id 집합 불일치 등.
 */
public class InvalidFaqException extends RuntimeException {

    public InvalidFaqException(String message) {
        super(message);
    }
}
