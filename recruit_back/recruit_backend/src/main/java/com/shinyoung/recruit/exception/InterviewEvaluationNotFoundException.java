package com.shinyoung.recruit.exception;

public class InterviewEvaluationNotFoundException extends RuntimeException {

    public InterviewEvaluationNotFoundException(Long evaluationId) {
        super("InterviewEvaluation not found. id=" + evaluationId);
    }
}
