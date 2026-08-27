package com.shinyoung.recruit.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 공공데이터포털 "전국대학별학과정보표준데이터" OpenAPI 연동 설정.
 * 전문대학·대학교·대학원 검색에 사용한다.
 *
 * <p>인증키는 코드에 하드코딩하지 않고 외부 설정/환경변수로 주입한다(CLAUDE.md 4.2).
 *
 * <p>서비스키는 포털의 <b>디코딩 키</b>를 넣는다. 인코딩 키(%-escape 된 값)를 넣으면 URI 빌더가 다시
 * 인코딩해 서명이 깨진다.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "recruit.univ-dept")
public class UnivDeptProperties {

    /**
     * 전국대학별학과정보 표준데이터 조회 엔드포인트(GET). DMZ 웹서버를 경유한다
     * ({@code /gov} → {@code api.data.go.kr}).
     */
    private String baseUrl = "http://juso.go.kr/gov/openapi/tn_pubr_public_univ_major_api";

    /** 공공데이터포털에서 발급받은 서비스키(디코딩 키). 서버 보관(프론트 미노출). 미설정 시 호출 단계에서 502로 실패한다. */
    private String serviceKey = "";

    /** 연결 타임아웃(ms). */
    @Min(0)
    private int connectTimeoutMs = 3000;

    /** 응답 읽기 타임아웃(ms). */
    @Min(0)
    private int readTimeoutMs = 5000;

    /**
     * 한 번에 요청할 행 수(numOfRows). 학과 단위 데이터라 같은 학교가 여러 행으로 오므로
     * 자동완성 상한보다 크게 잡아 중복 제거 후에도 결과가 남게 한다.
     */
    @Min(1)
    private int pageSize = 100;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getServiceKey() {
        return serviceKey;
    }

    public void setServiceKey(String serviceKey) {
        this.serviceKey = serviceKey;
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
