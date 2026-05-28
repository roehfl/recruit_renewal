package com.shinyoung.recruit.exception;

import com.shinyoung.recruit.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

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

    @ExceptionHandler(StageNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleStageNotFound(StageNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(InvalidStageException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidStage(InvalidStageException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(InvalidStageResultException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidStageResult(InvalidStageResultException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(StageResultNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleStageResultNotFound(StageResultNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(InterviewNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleInterviewNotFound(InterviewNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(InvalidInterviewException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidInterview(InvalidInterviewException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(QuestionTemplateNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleQuestionTemplateNotFound(QuestionTemplateNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(InvalidQuestionTemplateException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidQuestionTemplate(InvalidQuestionTemplateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(JobPostingQuestionNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleJobPostingQuestionNotFound(JobPostingQuestionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(InvalidJobPostingQuestionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidJobPostingQuestion(InvalidJobPostingQuestionException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(InvalidApplicationAnswerException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidApplicationAnswer(InvalidApplicationAnswerException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(JobApplicationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleJobApplicationNotFound(JobApplicationNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(InvalidJobApplicationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidJobApplication(InvalidJobApplicationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(InvalidApplicationFormLayoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidApplicationFormLayout(InvalidApplicationFormLayoutException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(InvalidApplicantSignUpException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidApplicantSignUp(InvalidApplicantSignUpException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Invalid request.");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("Invalid request.");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("Invalid request."));
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequestParameter(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("Invalid request."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("Attachment file size exceeds the allowed limit."));
    }

    @ExceptionHandler(StorageHealthScanException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorageHealthScan(StorageHealthScanException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(e.getMessage()));
    }
}
