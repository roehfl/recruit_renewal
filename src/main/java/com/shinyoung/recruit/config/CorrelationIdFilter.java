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
 * <p>{@code traceId}(OpenTelemetry)는 현재 deferred — OTel/Sleuth 도입 시 이 필터에서 함께 채운다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "correlationId";

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
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue.trim();
        }
        return UUID.randomUUID().toString();
    }

    /** 현재 요청의 correlationId(없으면 null). ActivityLogService 가 MDC 에서 읽어간다. */
    public static String currentCorrelationId() {
        return MDC.get(MDC_KEY);
    }
}
