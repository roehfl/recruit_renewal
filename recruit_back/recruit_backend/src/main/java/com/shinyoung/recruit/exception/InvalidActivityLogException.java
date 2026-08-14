package com.shinyoung.recruit.exception;

/**
 * ActivityLog 생성 시 필수 필드 누락 등 무결성 위반(Phase 09a).
 */
public class InvalidActivityLogException extends RuntimeException {
    public InvalidActivityLogException(String message) {
        super(message);
    }
}
