package com.shinyoung.recruit.exception;

import com.shinyoung.recruit.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 미인증 요청(서비스 레이어 수동 인증 체크). 401 — 프론트엔드는 이 상태코드로 로그인 화면 라우팅을 분기한다.
     * Security 필터 레벨 미인증(CustomAuthenticationEntryPoint)과 동일한 의미/포맷.
     */
    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationRequired(AuthenticationRequiredException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail(e.getMessage()));
    }

    /** 인증됐지만 사용자 타입/권한 불일치. 403 — CustomAccessDeniedHandler 와 동일한 의미/포맷. */
    @ExceptionHandler(AccessForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessForbidden(AccessForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(e.getMessage()));
    }

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

    @ExceptionHandler(InterviewEvaluationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleInterviewEvaluationNotFound(InterviewEvaluationNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(InvalidInterviewEvaluationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidInterviewEvaluation(InvalidInterviewEvaluationException e) {
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

    @ExceptionHandler(InvalidApplicantAccountException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidApplicantAccount(InvalidApplicantAccountException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(InvalidAuditQueryException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidAuditQuery(InvalidAuditQueryException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler({
            InvalidRetentionPolicyException.class,
            InvalidRetentionHoldException.class,
            InvalidRetentionRequestException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidRetention(RuntimeException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler({
            RetentionPolicyNotFoundException.class,
            RetentionHoldNotFoundException.class,
            PurgeBatchNotFoundException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleRetentionNotFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(ActivityLogNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleActivityLogNotFound(ActivityLogNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(InvalidClientEventLogException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidClientEventLog(InvalidClientEventLogException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
    }

    /** client event 수집 rate limit 초과(Phase 09f). FE telemetry는 fire-and-forget이라 응답을 사용하지 않는다. */
    @ExceptionHandler(ClientEventRateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleClientEventRateLimitExceeded(ClientEventRateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.fail(e.getMessage()));
    }

    /**
     * DB 제약 위반(unique 등). 사전 체크를 통과한 동시 요청 race가 대표 경로이며 409로 응답한다.
     * 제약명/컬럼명/SQL 등 내부 정보는 응답에 노출하지 않는다. 다만 FK/NOT NULL 위반 같은
     * 서버측 결함이 일괄 409 매핑에 가려지지 않도록 원인 예외는 warn 로그로 남긴다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("Data integrity violation", e);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("이미 처리되었거나 중복된 데이터입니다."));
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

    @ExceptionHandler(ExportRowLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleExportRowLimitExceeded(ExportRowLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(ExportGenerationException.class)
    public ResponseEntity<ApiResponse<Void>> handleExportGeneration(ExportGenerationException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("Export 파일 생성에 실패했습니다."));
    }

    @ExceptionHandler(PdfGenerationException.class)
    public ResponseEntity<ApiResponse<Void>> handlePdfGeneration(PdfGenerationException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("지원서 PDF 생성에 실패했습니다."));
    }

    @ExceptionHandler(CommonCodeNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCommonCodeNotFound(CommonCodeNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(InvalidCommonCodeException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCommonCode(InvalidCommonCodeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(SchoolNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleSchoolNotFound(SchoolNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(InvalidSchoolException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidSchool(InvalidSchoolException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(InvalidStatisticsRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidStatisticsRequest(InvalidStatisticsRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(InvalidStageResultUploadException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidStageResultUpload(InvalidStageResultUploadException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(e.getMessage()));
    }

    /**
     * 낙관적 잠금(@Version) 충돌. 모든 StageResult write 경로(수동 update / Excel upload commit) 간에
     * 다른 트랜잭션이 먼저 변경한 경우 flush에서 발생하며, 덮어쓰기를 막고 409로 응답한다.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("다른 사용자가 먼저 변경하여 처리하지 못했습니다. 최신 상태를 다시 확인해 주세요."));
    }
}
