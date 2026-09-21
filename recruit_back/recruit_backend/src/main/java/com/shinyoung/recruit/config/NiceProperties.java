package com.shinyoung.recruit.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * NICE 체크플러스 본인확인 연동 설정.
 *
 * <p>사이트코드·사이트패스워드는 운영 자격증명이므로 코드·문서·커밋에 값을 두지 않는다.
 * 환경변수로만 주입한다(AES_SECRET_KEY 와 같은 취급).
 *
 * <p>{@code mockEnabled} 와 실연동 설정(siteCode·sitePassword·returnUrl·errorUrl)의 조합 검증은
 * {@code NiceClientConfig} 가 기동 시 수행한다.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "recruit.nice")
public class NiceProperties {

    /** NICE 발급 사이트코드. 실제 연동 시 필수. */
    private String siteCode = "";

    /** NICE 발급 사이트패스워드. 실제 연동 시 필수. */
    private String sitePassword = "";

    /** 인증 성공 시 NICE 팝업이 POST 할 우리 서버 URL. NICE 에 등록된 값과 같아야 한다. */
    private String returnUrl = "";

    /** 인증 실패·취소 시 POST 될 우리 서버 URL. */
    private String errorUrl = "";

    /** 개발용 Mock 구현 사용 여부. 운영은 반드시 false. */
    private boolean mockEnabled = false;

    /** 요청 발급 → 콜백 수신 허용 시간(분). 통신사 인증에 걸리는 시간. */
    @Min(1)
    private int requestTtlMinutes = 10;

    /** 인증 완료 → 가입 제출 허용 시간(분). 폼 작성 시간. */
    @Min(1)
    private int verifiedTtlMinutes = 30;

    public String getSiteCode() {
        return siteCode;
    }

    public void setSiteCode(String siteCode) {
        this.siteCode = siteCode;
    }

    public String getSitePassword() {
        return sitePassword;
    }

    public void setSitePassword(String sitePassword) {
        this.sitePassword = sitePassword;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getErrorUrl() {
        return errorUrl;
    }

    public void setErrorUrl(String errorUrl) {
        this.errorUrl = errorUrl;
    }

    public boolean isMockEnabled() {
        return mockEnabled;
    }

    public void setMockEnabled(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }

    public int getRequestTtlMinutes() {
        return requestTtlMinutes;
    }

    public void setRequestTtlMinutes(int requestTtlMinutes) {
        this.requestTtlMinutes = requestTtlMinutes;
    }

    public int getVerifiedTtlMinutes() {
        return verifiedTtlMinutes;
    }

    public void setVerifiedTtlMinutes(int verifiedTtlMinutes) {
        this.verifiedTtlMinutes = verifiedTtlMinutes;
    }
}
