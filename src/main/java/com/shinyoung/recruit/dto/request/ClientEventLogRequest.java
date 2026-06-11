package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.ClientEventSeverity;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.enumeration.ClientEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * client event 수집 요청(Phase 09f, 설계 6.1).
 *
 * <p>{@code ipAddress}/{@code userAgent}/{@code principalHash}/{@code principalType}/{@code receivedAt}/
 * {@code ingestCorrelationId}는 body로 받지 않는다 — 서버에서만 채운다(FE 값 신뢰 금지).
 *
 * <p>{@code clientSessionId}/{@code clientEventId}는 UUID 등 안전한 opaque id 형식으로 제한한다
 * (unique 컬럼/rate limiter map key 오염 방지, 리뷰 Major 5). {@code message}는 자유 문자열이 아니라
 * 대문자 safe message code({@code API_REQUEST_FAILED} 등)만 허용한다(리뷰 Blocker 3 + 2차 리뷰 Major 2)
 * — 한글은 물론 영문 자유 문장(이름/회사명/주소성 문자열 혼입 가능)도 400. FE는 표시 문구 대신
 * messageCode만 전송한다. axios 기본 문구("Request failed with status code 500")도 자유 문장이므로
 * 거부 — 해당 정보는 {@code httpStatus}/{@code errorCode}로 전달한다.
 */
public record ClientEventLogRequest(
        @NotNull ClientEventType eventType,
        @NotNull ClientEventSeverity severity,
        @NotNull ClientEventSource source,
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9\\-]{8,80}$", message = "clientSessionId 형식이 올바르지 않습니다.")
        String clientSessionId,
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9\\-]{8,80}$", message = "clientEventId 형식이 올바르지 않습니다.")
        String clientEventId,
        LocalDateTime clientOccurredAt,

        @Size(max = 100) String relatedCorrelationId,
        @Size(max = 80) String pageCode,
        @Size(max = 80) String componentCode,
        @Size(max = 300) String routePath,
        @Size(max = 80) String operation,

        Long jobPostingId,
        Long applicationId,

        @Size(max = 10) String httpMethod,
        @Size(max = 300) String apiPath,
        Integer httpStatus,
        @Size(max = 100) String errorCode,
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,80}$", message = "message는 safe message code만 허용됩니다.")
        String message,
        @Size(max = 128) String stackHash,
        @Size(max = 2000) String stackSummary,

        @Size(max = 80) String frontendVersion,
        @Size(max = 80) String browserName,
        @Size(max = 80) String browserVersion,
        @Size(max = 80) String osName,
        @Size(max = 40) String viewport,
        @Size(max = 80) String timezone,

        Map<String, Object> metadata
) {
}
