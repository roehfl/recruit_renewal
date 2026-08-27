package com.shinyoung.recruit.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 정부 도로명주소 OpenAPI(juso.go.kr) 연동 설정.
 *
 * <p>승인키(confmKey)는 코드에 하드코딩하지 않고 외부 설정/환경변수로 주입한다(CLAUDE.md 4.2).
 * 로컬/테스트에서는 승인키가 비어 있을 수 있으므로 {@code confmKey}는 {@code @NotBlank}로 강제하지 않고,
 * 실제 호출 시점({@link com.shinyoung.recruit.service.JusoAddressClient})에서 비어 있으면 명확히 실패시킨다.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "recruit.juso")
public class JusoProperties {

    /**
     * juso 도로명주소 검색 API 엔드포인트(GET). DMZ 웹서버를 경유한다
     * ({@code /juso} → {@code business.juso.go.kr}).
     */
    @NotBlank
    private String baseUrl = "https://juso.go.kr/juso/addrlink/addrLinkApi.do";

    /** juso에서 발급받은 승인키. 서버 보관(프론트 미노출). 미설정 시 호출 단계에서 502로 실패한다. */
    private String confmKey = "";

    /** 연결 타임아웃(ms). */
    @Min(0)
    private int connectTimeoutMs = 3000;

    /** 응답 읽기 타임아웃(ms). */
    @Min(0)
    private int readTimeoutMs = 5000;

    /** countPerPage 상한(과대 요청 방어). juso 자체 제한이 100이다. */
    @Min(1)
    private int maxCountPerPage = 100;

    /**
     * juso가 조회를 허용하는 최대 범위({@code currentPage * countPerPage}). 초과하면 juso가 E0015
     * ("검색 범위를 초과하였습니다")를 준다.
     *
     * <p>실측(2026-07-31, keyword="중앙로", totalCount=10,715)으로 경계를 이진탐색한 결과 <b>9,000</b>이다.
     * countPerPage와 무관한 offset 기준이다.
     * <pre>
     * countPerPage=10 : 900×10  = 9,000 정상 / 901×10  = 9,010 E0015
     * countPerPage=100:  90×100 = 9,000 정상 /  91×100 = 9,100 E0015
     * </pre>
     *
     * <p>주의: 이 상한은 {@code totalCount}와 무관하다. totalCount가 10,715여도 offset 9,010은 E0015다.
     * 따라서 "totalCount > currentPage×countPerPage"는 해당 페이지 조회 가능의 근거가 되지 못한다.
     * juso가 상한을 조정할 수 있으므로 코드 상수가 아니라 설정값으로 둔다.
     */
    @Min(1)
    private int maxSearchRange = 9000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getConfmKey() {
        return confmKey;
    }

    public void setConfmKey(String confmKey) {
        this.confmKey = confmKey;
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

    public int getMaxCountPerPage() {
        return maxCountPerPage;
    }

    public void setMaxCountPerPage(int maxCountPerPage) {
        this.maxCountPerPage = maxCountPerPage;
    }

    public int getMaxSearchRange() {
        return maxSearchRange;
    }

    public void setMaxSearchRange(int maxSearchRange) {
        this.maxSearchRange = maxSearchRange;
    }
}
