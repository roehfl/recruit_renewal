package com.shinyoung.recruit.exception;

import org.springframework.security.authentication.BadCredentialsException;

/**
 * 지원자 로그인 2차 인증(NICE) 실패. 비밀번호는 맞았다.
 *
 * <p>인증 예외 계열이라 {@code RoutingAuthenticationProvider} 가 로그인 실패 횟수에 센다.
 * 사유 문구는 비밀번호가 맞은 뒤에만 나가므로 구분해 보여줘도 정보가 되지 않는다.
 */
public class LoginSecondFactorException extends BadCredentialsException {

    public enum Reason {
        NICE_REQUIRED("본인인증을 먼저 진행해주세요."),
        EXPIRED("본인인증이 만료되었습니다. 다시 진행해주세요."),
        MISMATCH("본인인증 명의가 가입자 정보와 일치하지 않습니다.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    private final Reason reason;

    public LoginSecondFactorException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
