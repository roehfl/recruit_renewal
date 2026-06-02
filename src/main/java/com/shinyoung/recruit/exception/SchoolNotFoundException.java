package com.shinyoung.recruit.exception;

/**
 * School 을 찾을 수 없을 때 발생(404).
 */
public class SchoolNotFoundException extends RuntimeException {

    public SchoolNotFoundException(String message) {
        super(message);
    }
}
