package com.shinyoung.recruit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * CSRF 헤더 검사({@link CsrfHeaderFilter}) 사용 여부. 기본 켜짐.
 *
 * <p>끄는 경우는 둘뿐이다 — 테스트(MockMvc 요청이 헤더를 붙이지 않는다)와 로컬 Swagger 로 POST 를 호출할 때.
 */
@Component
@ConfigurationProperties(prefix = "recruit.csrf")
public class CsrfProperties {

    private boolean headerRequired = true;

    public boolean isHeaderRequired() {
        return headerRequired;
    }

    public void setHeaderRequired(boolean headerRequired) {
        this.headerRequired = headerRequired;
    }
}
