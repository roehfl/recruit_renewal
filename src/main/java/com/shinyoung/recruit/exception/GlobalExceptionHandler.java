package com.shinyoung.recruit.exception;

import com.shinyoung.recruit.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(JobPostingNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleJobPostingNotFound(JobPostingNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(InvalidJobPostingException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidJobPosting(InvalidJobPostingException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(e.getMessage()));
    }
}
