package com.shinyoung.recruit.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * NEIS(교육행정정보시스템) 학교기본정보 OpenAPI 연동 설정. 고등학교 검색에 사용한다.
 *
 * <p>인증키는 코드에 하드코딩하지 않고 외부 설정/환경변수로 주입한다(CLAUDE.md 4.2).
 * 로컬/테스트에서는 비어 있을 수 있으므로 {@code apiKey}는 {@code @NotBlank}로 강제하지 않고,
 * 실제 호출 시점({@link com.shinyoung.recruit.service.NeisSchoolClient})에서 비어 있으면 명확히 실패시킨다.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "recruit.neis")
public class NeisProperties {

    /**
     * NEIS 학교기본정보 조회 엔드포인트(GET). DMZ 웹서버를 경유한다
     * ({@code /neis} → {@code open.neis.go.kr}).
     */
    @NotBlank
    private String baseUrl = "https://juso.go.kr/neis/hub/schoolInfo";

    /** NEIS에서 발급받은 인증키. 서버 보관(프론트 미노출). 미설정 시 호출 단계에서 502로 실패한다. */
    private String apiKey = "";

    /** 연결 타임아웃(ms). */
    @Min(0)
    private int connectTimeoutMs = 3000;

    /** 응답 읽기 타임아웃(ms). */
    @Min(0)
    private int readTimeoutMs = 5000;

    /** 한 번에 요청할 행 수(pSize). 자동완성 상한과 맞춘다. */
    @Min(1)
    private int pageSize = 20;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
