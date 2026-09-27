package com.shinyoung.recruit.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 로그인·이메일 인증번호 시도 횟수 제한(세션과 무관하게 서버 전역으로 센다).
 *
 * <p>테스트는 한 컨텍스트에서 같은 계정·이메일을 여러 번 쓰므로 테스트 yaml에서 한도를 크게 둔다.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "recruit.auth-attempt-limit")
public class AuthAttemptLimitProperties {

    /** 로그인 아이디별 연속 실패 허용 횟수. 넘으면 {@link #loginLockMinutes} 동안 막는다. */
    @Min(1)
    private int loginMaxFailures = 5;

    @Min(1)
    private int loginLockMinutes = 15;

    /** 이메일별 인증번호 발송 허용 횟수({@link #codeWindowMinutes} 안). */
    @Min(1)
    private int codeMaxSends = 5;

    /** 이메일별 인증번호 오답 허용 횟수({@link #codeWindowMinutes} 안). 세션을 새로 만들어도 합산된다. */
    @Min(1)
    private int codeMaxFailures = 10;

    @Min(1)
    private int codeWindowMinutes = 60;

    public int getLoginMaxFailures() {
        return loginMaxFailures;
    }

    public void setLoginMaxFailures(int loginMaxFailures) {
        this.loginMaxFailures = loginMaxFailures;
    }

    public int getLoginLockMinutes() {
        return loginLockMinutes;
    }

    public void setLoginLockMinutes(int loginLockMinutes) {
        this.loginLockMinutes = loginLockMinutes;
    }

    public int getCodeMaxSends() {
        return codeMaxSends;
    }

    public void setCodeMaxSends(int codeMaxSends) {
        this.codeMaxSends = codeMaxSends;
    }

    public int getCodeMaxFailures() {
        return codeMaxFailures;
    }

    public void setCodeMaxFailures(int codeMaxFailures) {
        this.codeMaxFailures = codeMaxFailures;
    }

    public int getCodeWindowMinutes() {
        return codeWindowMinutes;
    }

    public void setCodeWindowMinutes(int codeWindowMinutes) {
        this.codeWindowMinutes = codeWindowMinutes;
    }
}
