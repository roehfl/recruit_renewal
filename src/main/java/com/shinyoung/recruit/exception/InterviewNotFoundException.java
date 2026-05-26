package com.shinyoung.recruit.exception;

public class InterviewNotFoundException extends RuntimeException {

    public InterviewNotFoundException(Long interviewId) {
        super("Interview not found. id=" + interviewId);
    }
}
