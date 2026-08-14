package com.shinyoung.recruit.exception;

public class InvalidApplicantSignUpException extends RuntimeException {
    public InvalidApplicantSignUpException(String message) {
        super(message);
    }
}
