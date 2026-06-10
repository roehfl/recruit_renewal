package com.shinyoung.recruit.exception;

public class ClientEventRateLimitExceededException extends RuntimeException {

    public ClientEventRateLimitExceededException(String message) {
        super(message);
    }
}
