package com.shinyoung.recruit.exception;

public class AuthAttemptLimitExceededException extends RuntimeException {

    public AuthAttemptLimitExceededException(String message) {
        super(message);
    }
}
