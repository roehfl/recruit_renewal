// src/main/java/com/shinyoung/recruit/service/ClientEventLogService.java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.config.CorrelationIdFilter;
import com.shinyoung.recruit.domain.entity.ClientEventLog;
import com.shinyoung.recruit.domain.repository.ClientEventLogRepository;
import com.shinyoung.recruit.dto.request.ClientEventLogRequest;
import com.shinyoung.recruit.dto.response.ClientEventLogIngestResponse;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.exception.InvalidClientEventLogException;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * client event 수집 파이프라인(Phase 09f, 설계 6.2). best-effort — 수집 실패가 업무 API에
 * 영향을 주지 않고, 진단 로그 실패를 다시 진단 로그로 남기지 않는다.
 *
 * <p>의도적으로 {@code @Transactional}을 붙이지 않는다 — {@code saveAndFlush}의
 * {@link DataIntegrityViolationException}(중복 race)을 catch해 duplicate 성공 응답으로 흡수해야 하는데
 * (리뷰 Major 4), 외부 트랜잭션이 있으면 flush 시 rollback-only로 마킹돼 catch 후 정상 반환이
 * {@code UnexpectedRollbackException}으로 깨진다. repository 호출이 각자 자체 트랜잭션을 쓴다.
 */
@Service
public class ClientEventLogService {

    // 컬럼 길이와 동일(초과분 truncate). message만 safe code 계약에 따라 컬럼(500)보다 좁은 200.
    private static final int MAX_CORRELATION_ID = 100;
    private static final int MAX_PAGE_CODE = 80;
    private static final int MAX_COMPONENT_CODE = 80;
    private static final int MAX_ROUTE_PATH = 300;
    private static final int MAX_OPERATION = 80;
    private static final int MAX_HTTP_METHOD = 10;
    private static final int MAX_API_PATH = 300;
    private static final int MAX_ERROR_CODE = 100;
    private static final int MAX_MESSAGE = 200;
    private static final int MAX_STACK_HASH = 128;
    private static final int MAX_STACK_SUMMARY = 2000;
    private static final int MAX_FRONTEND_VERSION = 80;
    private static final int MAX_BROWSER = 80;
    private static final int MAX_OS = 80;
    private static final int MAX_VIEWPORT = 40;
    private static final int MAX_TIMEZONE = 80;
    private static final int MAX_IP_ADDRESS = 64;
    private static final int MAX_USER_AGENT = 512;

    /** 7자리 이상 연속 숫자(하이픈 개입 허용) — 전화번호류 혼입 마스킹(설계 6.3). */
    private static final String LONG_DIGIT_RUN = "[0-9][0-9\\-]{5,}[0-9]";

    private final ClientEventLogRepository repository;
    private final ClientEventMetadataSanitizer metadataSanitizer;
    private final ClientEventRateLimiter rateLimiter;
    private final AuditHmac auditHmac;
    private final Clock clock;
    private final boolean enabled;

    public ClientEventLogService(
            ClientEventLogRepository repository,
            ClientEventMetadataSanitizer metadataSanitizer,
            ClientEventRateLimiter rateLimiter,
            AuditHmac auditHmac,
            Clock clock,
            @Value("${client-event-log.enabled:true}") boolean enabled
    ) {
        this.repository = repository;
        this.metadataSanitizer = metadataSanitizer;
        this.rateLimiter = rateLimiter;
        this.auditHmac = auditHmac;
        this.clock = clock;
        this.enabled = enabled;
    }

    public ClientEventLogIngestResponse record(
            ClientEventLogRequest request,
            CustomUserDetails userDetails,
            HttpServletRequest servletRequest
    ) {
        if (!enabled) {
            return ClientEventLogIngestResponse.ofDisabled();
        }
        if (request.source() != ClientEventSource.APPLICANT_WEB) {
            // public endpoint에서 ADMIN_WEB 진단 로그 위조 차단(리뷰 Blocker 1).
            throw new InvalidClientEventLogException("source는 APPLICANT_WEB만 허용됩니다.");
        }

        String ip = servletRequest.getRemoteAddr();
        String principalHash = userDetails == null
                ? null
                : auditHmac.hmacHex("CLIENT_PRINCIPAL:" + userDetails.getUsername());
        rateLimiter.check(ip, request.clientSessionId(), principalHash);

        if (repository.existsByClientSessionIdAndClientEventId(request.clientSessionId(), request.clientEventId())) {
            return ClientEventLogIngestResponse.ofDuplicate();
        }

        String metadataJson = metadataSanitizer.sanitize(request.eventType(), request.metadata());
        ClientEventLog entity = ClientEventLog.builder()
                .receivedAt(LocalDateTime.now(clock))
                .clientOccurredAt(request.clientOccurredAt())
                .eventType(request.eventType())
                .severity(request.severity())
                .source(request.source())
                .clientSessionId(request.clientSessionId())
                .clientEventId(request.clientEventId())
                .ingestCorrelationId(safe(CorrelationIdFilter.currentCorrelationId(), MAX_CORRELATION_ID))
                .relatedCorrelationId(safe(request.relatedCorrelationId(), MAX_CORRELATION_ID))
                .pageCode(safe(request.pageCode(), MAX_PAGE_CODE))
                .componentCode(safe(request.componentCode(), MAX_COMPONENT_CODE))
                .routePath(safe(stripQuery(request.routePath()), MAX_ROUTE_PATH))
                .operation(safe(request.operation(), MAX_OPERATION))
                .jobPostingId(request.jobPostingId())
                .applicationId(request.applicationId())
                .httpMethod(safe(request.httpMethod(), MAX_HTTP_METHOD))
                .apiPath(safe(stripQuery(request.apiPath()), MAX_API_PATH))
                .httpStatus(request.httpStatus())
                .errorCode(safe(request.errorCode(), MAX_ERROR_CODE))
                .message(maskLongDigitRuns(safe(request.message(), MAX_MESSAGE)))
                .stackHash(safe(request.stackHash(), MAX_STACK_HASH))
                .stackSummary(safe(request.stackSummary(), MAX_STACK_SUMMARY))
                .frontendVersion(safe(request.frontendVersion(), MAX_FRONTEND_VERSION))
                .browserName(safe(request.browserName(), MAX_BROWSER))
                .browserVersion(safe(request.browserVersion(), MAX_BROWSER))
                .osName(safe(request.osName(), MAX_OS))
                .viewport(safe(request.viewport(), MAX_VIEWPORT))
                .timezone(safe(request.timezone(), MAX_TIMEZONE))
                .ipAddress(safe(ip, MAX_IP_ADDRESS))
                .userAgent(safe(servletRequest.getHeader("User-Agent"), MAX_USER_AGENT))
                .principalHash(principalHash)
                .principalType(userDetails == null ? null : userDetails.getUserType())
                .metadataJson(metadataJson)
                .build();

        try {
            ClientEventLog saved = repository.saveAndFlush(entity);
            return ClientEventLogIngestResponse.ofAccepted(saved.getId());
        } catch (DataIntegrityViolationException e) {
            // 동시 재전송 race — unique(client_session_id, client_event_id)가 최종 방어선.
            return ClientEventLogIngestResponse.ofDuplicate();
        }
    }

    /** CR/LF/TAB 등 제어문자 → 공백 치환 후 trim, 길이 초과분 truncate. blank면 null. */
    private String safe(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = value.replaceAll("\\p{Cntrl}+", " ").trim();
        if (sanitized.isBlank()) {
            return null;
        }
        return sanitized.length() <= max ? sanitized : sanitized.substring(0, max);
    }

    private String stripQuery(String path) {
        if (path == null) {
            return null;
        }
        int queryIndex = path.indexOf('?');
        return queryIndex < 0 ? path : path.substring(0, queryIndex);
    }

    private String maskLongDigitRuns(String message) {
        if (message == null) {
            return null;
        }
        return message.replaceAll(LONG_DIGIT_RUN, "*");
    }
}
