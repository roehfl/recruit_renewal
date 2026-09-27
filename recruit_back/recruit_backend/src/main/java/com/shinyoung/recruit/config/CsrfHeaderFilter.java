package com.shinyoung.recruit.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * CSRF 방어 — {@code /api/**} 상태 변경 요청(GET·HEAD·OPTIONS 외)에 {@code X-Requested-With} 헤더를 요구한다.
 *
 * <p>세션 쿠키 인증인데 CSRF 토큰을 쓰지 않는다. 교차 사이트 폼·{@code no-cors} fetch 는 사용자 정의 헤더를 붙일 수
 * 없고, 붙이려면 preflight 가 필요해 CORS 허용 목록에서 막힌다. 그래서 헤더가 있으면 같은 출처(또는 허용 출처)의
 * 스크립트 요청이다. 본문 없는 POST·multipart POST 가 preflight 없이 쿠키와 함께 오던 경로를 막는다.
 *
 * <p>NICE 콜백처럼 외부 사이트가 폼으로 부르는 경로는 예외로 둔다(세션 없이 동작하도록 설계돼 있다).
 */
public class CsrfHeaderFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Requested-With";
    private static final String API_PREFIX = "/api/";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final Set<String> exemptPaths;
    private final AccessDeniedHandler accessDeniedHandler;

    public CsrfHeaderFilter(Set<String> exemptPaths, AccessDeniedHandler accessDeniedHandler) {
        this.exemptPaths = exemptPaths;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean required = !SAFE_METHODS.contains(request.getMethod())
                && path.startsWith(API_PREFIX)
                && !exemptPaths.contains(path);
        if (required && !StringUtils.hasText(request.getHeader(HEADER))) {
            accessDeniedHandler.handle(request, response, new AccessDeniedException("CSRF header missing"));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
