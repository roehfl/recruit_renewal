package com.shinyoung.recruit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 공공데이터포털 서비스키를 쿼리스트링에 넣을 형태로 변환한다.
 *
 * <p>포털은 같은 키를 Encoding 표기({@code %2B}, {@code %2F}, {@code %3D})와 Decoding 표기
 * ({@code +}, {@code /}, {@code =}) 두 가지로 보여주고, 화면에 따라 하나만 노출되기도 한다.
 * 어느 쪽을 설정해도 동작해야 하므로 <b>항상 Encoding 표기로 정규화</b>해서 돌려준다.
 *
 * <p>Spring 의 URI 빌더에 값을 그대로 넘기면 안 된다. 쿼리 값에서 {@code =} 는 인코딩하지만
 * {@code +} 와 {@code /} 는 허용 문자로 보아 그대로 두는데, 쿼리스트링의 {@code +} 는 공백으로
 * 해석되어 키가 깨진다(상위 API 403). 그래서 호출부는 이 값을 이미 인코딩된 것으로 보고
 * URI 를 직접 조립한다.
 */
final class PublicDataServiceKey {

    private static final Logger log = LoggerFactory.getLogger(PublicDataServiceKey.class);

    private PublicDataServiceKey() {
    }

    /**
     * 설정된 서비스키를 쿼리스트링에 바로 붙일 수 있는 Encoding 표기로 만든다.
     *
     * <p>Encoding 표기가 들어오면 한 번 디코딩한 뒤 다시 인코딩하므로 이중 인코딩되지 않는다.
     * Decoding 표기가 들어오면 인코딩만 한다.
     */
    static String toQueryValue(String serviceKey) {
        if (serviceKey == null || serviceKey.isBlank()) {
            return serviceKey;
        }
        return URLEncoder.encode(decodeIfEncoded(serviceKey), StandardCharsets.UTF_8);
    }

    /**
     * {@code %} 가 있으면 Encoding 표기로 보고 한 번 디코딩한다.
     *
     * <p>{@link URLDecoder} 는 {@code +} 를 공백으로 바꾸므로 리터럴 {@code +} 를 먼저 {@code %2B} 로
     * 치환한 뒤 디코딩한다. 형식이 깨진 값은 그대로 넘겨 인증 실패(502)로 드러나게 둔다.
     */
    private static String decodeIfEncoded(String serviceKey) {
        if (!serviceKey.contains("%")) {
            return serviceKey;
        }
        try {
            return URLDecoder.decode(serviceKey.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 키 값은 로깅하지 않는다.
            log.warn("공공데이터 서비스키 디코딩 실패. 설정값을 그대로 사용한다: {}", e.getMessage());
            return serviceKey;
        }
    }
}
