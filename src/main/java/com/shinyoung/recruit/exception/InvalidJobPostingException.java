package com.shinyoung.recruit.exception;

public class InvalidJobPostingException extends RuntimeException {
    public InvalidJobPostingException(String message) {
        super(message);
    }
}
