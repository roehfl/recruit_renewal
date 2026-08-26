package com.shinyoung.recruit.exception;

/**
 * FAQ 또는 FAQ 카테고리를 찾을 수 없을 때 발생(404).
 */
public class FaqNotFoundException extends RuntimeException {

    public FaqNotFoundException(String message) {
        super(message);
    }
}
