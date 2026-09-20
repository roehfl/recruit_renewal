package com.shinyoung.recruit.exception;

/** 지원자(Applicant)를 찾을 수 없을 때. 404. */
public class ApplicantNotFoundException extends RuntimeException {
    public ApplicantNotFoundException(String message) {
        super(message);
    }
}
