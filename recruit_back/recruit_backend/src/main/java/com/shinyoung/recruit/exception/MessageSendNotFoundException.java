package com.shinyoung.recruit.exception;

public class MessageSendNotFoundException extends RuntimeException {

    public MessageSendNotFoundException(String message) {
        super(message);
    }
}
