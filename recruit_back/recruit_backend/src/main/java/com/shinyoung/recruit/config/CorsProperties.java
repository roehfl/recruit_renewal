package com.shinyoung.recruit.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * 자격 증명 포함 CORS 허용 출처.
 *
 * <p>기본은 운영 주소만 둔다. 로컬·개발 서버 주소를 운영에서도 허용하면, 더 약한 개발 서버가 장악됐을 때
 * 그 출처의 스크립트가 운영 API 를 관리자 쿠키로 호출하고 응답을 읽을 수 있다.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "recruit.cors")
public class CorsProperties {

    @NotEmpty
    private List<String> allowedOrigins = new ArrayList<>(List.of("https://rec.shinyoung.com"));

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
