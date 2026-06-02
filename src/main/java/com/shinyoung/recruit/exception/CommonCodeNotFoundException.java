package com.shinyoung.recruit.exception;

/**
 * CommonCode 를 찾을 수 없을 때 발생(404).
 */
public class CommonCodeNotFoundException extends RuntimeException {

    public CommonCodeNotFoundException(String message) {
        super(message);
    }
}
