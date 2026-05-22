package com.shinyoung.recruit.exception;

public class StorageHealthScanException extends RuntimeException {

    public StorageHealthScanException(String message) {
        super(message);
    }

    public StorageHealthScanException(String message, Throwable cause) {
        super(message, cause);
    }
}
