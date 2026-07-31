package com.shinyoung.recruit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.shinyoung.recruit.config.JusoProperties;
import com.shinyoung.recruit.exception.AddressSearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 정부 도로명주소 OpenAPI(juso.go.kr) 호출 클라이언트.
 *
 * <p>레거시와 동일한 GET 방식({@code confmKey, currentPage, countPerPage, keyword})에 {@code resultType=json}을
 * 더해 호출한다. 승인키는 서버 설정({@link JusoProperties#getConfmKey()})에서 주입하며 클라이언트에는 노출하지 않는다.
 *
 * <p>juso는 {@code resultType=json}이라도 Content-Type을 {@code text/json} 등으로 내려 표준 컨버터 협상이
 * 어긋날 수 있으므로, 본문을 String으로 받아 {@link ObjectMapper}로 직접 파싱한다.
 *
 * <p>ObjectMapper는 DI로 주입받지 않고 자체 인스턴스를 만든다. Spring Boot 4는 Jackson 3를 auto-config하므로
 * Jackson 2 {@code com.fasterxml.jackson} {@link ObjectMapper} 빈이 없다(주입 실패). 기존
 * {@link com.shinyoung.recruit.service.ClientEventMetadataSanitizer}·{@code ActivityLogService} 선례를 따른다.
 */
@Component
public class JusoAddressClient {

    private static final Logger log = LoggerFactory.getLogger(JusoAddressClient.class);
    private static final String SUCCESS_CODE = "0";

    private final RestClient jusoRestClient;
    private final JusoProperties properties;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public JusoAddressClient(RestClient jusoRestClient, JusoProperties properties) {
        this.jusoRestClient = jusoRestClient;
        this.properties = properties;
    }

    /**
     * juso 주소 검색을 호출한다. 호출자는 keyword 비어있지 않음/페이지 클램핑을 이미 마친 값을 넘긴다.
     *
     * @throws AddressSearchException 승인키 미설정, 네트워크/타임아웃, 파싱 실패, juso 오류코드(비-0)
     */
    JusoApiResponse search(String keyword, int currentPage, int countPerPage) {
        String confmKey = properties.getConfmKey();
        if (!StringUtils.hasText(confmKey)) {
            // 설정 누락은 서버 문제 — 상세는 로깅만, 클라이언트에는 일반 메시지.
            log.error("juso confmKey가 설정되지 않았습니다. recruit.juso.confm-key(JUSO_CONFM_KEY)를 확인하세요.");
            throw new AddressSearchException("주소 검색 서비스가 설정되지 않았습니다.");
        }

        String body;
        try {
            body = jusoRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("confmKey", confmKey)
                            .queryParam("currentPage", currentPage)
                            .queryParam("countPerPage", countPerPage)
                            .queryParam("keyword", keyword)
                            .queryParam("resultType", "json")
                            .build())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            log.warn("juso 주소 검색 호출 실패(keyword 길이={}): {}", keyword.length(), e.getMessage());
            throw new AddressSearchException("주소 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
        }

        JusoApiResponse response = parse(body);
        JusoApiResponse.Common common = response.results() == null ? null : response.results().common();
        if (common == null) {
            log.warn("juso 응답에 common 섹션이 없습니다.");
            throw new AddressSearchException("주소 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
        if (!SUCCESS_CODE.equals(common.errorCode())) {
            // 승인키 오류(E0001) 등 상세는 로깅만 — 클라이언트에 원인/키 관련 메시지를 노출하지 않는다.
            log.warn("juso 오류코드={}, message={}", common.errorCode(), common.errorMessage());
            throw new AddressSearchException("주소 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return response;
    }

    private JusoApiResponse parse(String body) {
        if (!StringUtils.hasText(body)) {
            throw new AddressSearchException("주소 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
        try {
            return objectMapper.readValue(body, JusoApiResponse.class);
        } catch (Exception e) {
            log.warn("juso 응답 파싱 실패: {}", e.getMessage());
            throw new AddressSearchException("주소 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
        }
    }
}
