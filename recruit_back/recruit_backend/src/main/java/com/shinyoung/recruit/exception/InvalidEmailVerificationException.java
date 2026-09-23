package com.shinyoung.recruit.exception;

public class InvalidEmailVerificationException extends RuntimeException {
    public InvalidEmailVerificationException(String message) {
        super(message);
    }
}
