package com.shinyoung.recruit.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청 단위 correlationId 전파(Phase 09a). {@code X-Request-Id} 헤더가 있으면 재사용하고 없으면 UUID 를 생성해
 * MDC 에 넣는다. ActivityLog 는 이 값을 {@code correlationId} 로 기록한다(ADR-0006). 응답 헤더로도 echo 한다.
 *
 * <p>외부 입력 헤더는 신뢰하지 않는다(9a 리뷰 보완) — {@code activity_log.correlation_id} 컬럼 길이(100)를
 * 넘거나 CR/LF 를 포함하면 재사용하지 않고 UUID 로 대체한다. 길이 초과 헤더 하나로 audit insert 가 실패해
 * egress fail-close(9b)나 비즈니스 트랜잭션이 막히는 것을 방지한다.
 *
 * <p>{@code traceId}(OpenTelemetry)는 현재 deferred — OTel/Sleuth 도입 시 이 필터에서 함께 채운다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "correlationId";

    /** {@code activity_log.correlation_id} 컬럼 길이와 동일. 초과 헤더는 재사용하지 않는다. */
    static final int MAX_CORRELATION_ID = 100;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolve(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return UUID.randomUUID().toString();
        }

        String trimmed = headerValue.trim();
        if (trimmed.length() > MAX_CORRELATION_ID || trimmed.contains("\r") || trimmed.contains("\n")) {
            return UUID.randomUUID().toString();
        }

        return trimmed;
    }

    /** 현재 요청의 correlationId(없으면 null). ActivityLogService 가 MDC 에서 읽어간다. */
    public static String currentCorrelationId() {
        return MDC.get(MDC_KEY);
    }
}
