package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ClientEventLog;
import com.shinyoung.recruit.enumeration.ClientEventSeverity;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.enumeration.ClientEventType;

import java.time.LocalDateTime;

/**
 * 클라이언트 이벤트 로그 read API 응답(Phase 09f-3). 권한별 projection 분리 —
 * {@code ipAddress}/{@code userAgent}/{@code principalHash}/{@code stackSummary}는
 * ROLE_PRIVACY_ADMIN만 원문을 보고, 그 외 권한은 마스킹({@code ***}) 값을 받는다
 * (stackSummary는 자유 문자열 성격이라 PII 혼입 가능성, 리뷰 Blocker 3).
 * {@code message}(safe code 강제)/{@code metadataJson}(exact allowlist)/{@code stackHash}(group by용)는
 * 양 권한 모두에 노출한다.
 */
public record ClientEventLogResponse(
        Long id,
        LocalDateTime receivedAt,
        LocalDateTime clientOccurredAt,
        ClientEventType eventType,
        ClientEventSeverity severity,
        ClientEventSource source,
        String clientSessionId,
        String clientEventId,
        String ingestCorrelationId,
        String relatedCorrelationId,
        String pageCode,
        String componentCode,
        String routePath,
        String operation,
        Long jobPostingId,
        Long applicationId,
        String httpMethod,
        String apiPath,
        Integer httpStatus,
        String errorCode,
        String message,
        String stackHash,
        String stackSummary,
        String frontendVersion,
        String browserName,
        String browserVersion,
        String osName,
        String viewport,
        String timezone,
        String ipAddress,
        String userAgent,
        String principalHash,
        String principalType,
        String metadataJson
) {

    private static final String MASKED = "***";

    public static ClientEventLogResponse from(ClientEventLog log, boolean includeSensitive) {
        return new ClientEventLogResponse(
                log.getId(),
                log.getReceivedAt(),
                log.getClientOccurredAt(),
                log.getEventType(),
                log.getSeverity(),
                log.getSource(),
                log.getClientSessionId(),
                log.getClientEventId(),
                log.getIngestCorrelationId(),
                log.getRelatedCorrelationId(),
                log.getPageCode(),
                log.getComponentCode(),
                log.getRoutePath(),
                log.getOperation(),
                log.getJobPostingId(),
                log.getApplicationId(),
                log.getHttpMethod(),
                log.getApiPath(),
                log.getHttpStatus(),
                log.getErrorCode(),
                log.getMessage(),
                log.getStackHash(),
                mask(log.getStackSummary(), includeSensitive),
                log.getFrontendVersion(),
                log.getBrowserName(),
                log.getBrowserVersion(),
                log.getOsName(),
                log.getViewport(),
                log.getTimezone(),
                mask(log.getIpAddress(), includeSensitive),
                mask(log.getUserAgent(), includeSensitive),
                mask(log.getPrincipalHash(), includeSensitive),
                log.getPrincipalType(),
                log.getMetadataJson()
        );
    }

    private static String mask(String value, boolean includeSensitive) {
        if (value == null || includeSensitive) {
            return value;
        }
        return MASKED;
    }
}
