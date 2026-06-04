package com.shinyoung.recruit.exception;

/**
 * 인증은 됐지만 사용자 타입/권한이 해당 API 에 허용되지 않는 경우. GlobalExceptionHandler 가
 * 403(FORBIDDEN)으로 매핑한다. 미인증은 {@link AuthenticationRequiredException}(401)을 사용한다.
 *
 * <p>Security 필터 레벨 인가 실패(CustomAccessDeniedHandler)와 동일한 의미를 서비스 레이어
 * 수동 체크(지원자/임직원 타입 구분)에서 표현한다.
 */
public class AccessForbiddenException extends RuntimeException {

    public AccessForbiddenException(String message) {
        super(message);
    }
}
