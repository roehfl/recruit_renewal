package com.shinyoung.recruit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 공공데이터포털 서비스키 정규화.
 *
 * <p>포털은 같은 키를 Encoding 표기({@code %2B}, {@code %2F}, {@code %3D})와 Decoding 표기
 * ({@code +}, {@code /}, {@code =}) 두 가지로 보여주고, 화면에 따라 하나만 노출되기도 한다.
 * 호출부는 {@code queryParam} 으로 값을 넘겨 URI 빌더가 한 번 인코딩하므로 Encoding 표기가 들어오면
 * {@code %} 가 {@code %25} 로 이중 인코딩되어 상위 API 가 403(SERVICE_KEY_IS_NOT_REGISTERED_ERROR)을 준다.
 *
 * <p>어느 표기를 설정하든 동작하도록, {@code %} 가 있으면 한 번 디코딩해서 넘긴다.
 */
final class PublicDataServiceKey {

    private static final Logger log = LoggerFactory.getLogger(PublicDataServiceKey.class);

    private PublicDataServiceKey() {
    }

    /**
     * Encoding 표기면 디코딩해서, Decoding 표기면 그대로 돌려준다.
     *
     * <p>{@link URLDecoder} 는 {@code +} 를 공백으로 바꾸므로 리터럴 {@code +} 를 먼저 {@code %2B} 로
     * 치환한 뒤 디코딩한다. 형식이 깨진 값은 그대로 넘겨 인증 실패(502)로 드러나게 둔다.
     */
    static String normalize(String serviceKey) {
        if (serviceKey == null || !serviceKey.contains("%")) {
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
