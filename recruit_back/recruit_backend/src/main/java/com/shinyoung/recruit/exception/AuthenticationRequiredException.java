package com.shinyoung.recruit.exception;

/**
 * 인증되지 않은 요청이 인증 필요 로직에 도달한 경우. GlobalExceptionHandler 가 401(UNAUTHORIZED)로 매핑한다.
 *
 * <p>SecurityConfig 의 {@code anyRequest().permitAll()} 때문에 미인증 요청이 컨트롤러까지 도달할 수 있어,
 * 서비스 레이어의 수동 인증 체크({@code CurrentEmployeeService}/{@code CurrentApplicantService})가
 * 이 예외를 던진다. 도메인 검증 예외(400)와 구분해 프론트엔드가 로그인 라우팅을 분기할 수 있게 한다.
 * 인가 실패(인증됐지만 권한/타입 불일치)는 {@link AccessForbiddenException}(403)을 사용한다.
 */
public class AuthenticationRequiredException extends RuntimeException {

    public AuthenticationRequiredException(String message) {
        super(message);
    }
}
