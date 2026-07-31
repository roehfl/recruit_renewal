package com.shinyoung.recruit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * juso.go.kr 호출용 {@link RestClient} 빈. baseUrl과 연결/읽기 타임아웃을 {@link JusoProperties}로 구성한다.
 *
 * <p>외부 의존이므로 타임아웃을 명시해 스레드가 무기한 대기하지 않도록 한다.
 *
 * <p>auto-configured {@code RestClient.Builder} 빈에 의존하지 않고 {@link RestClient#builder()} 정적 팩토리로
 * 직접 생성한다. Spring Boot 4의 HTTP 클라이언트 auto-config 구성에 따라 {@code RestClient.Builder} 빈이
 * 없을 수 있어(주입 실패) 이를 회피한다. 기본 메시지 컨버터는 정적 빌더가 포함하므로 String 본문 처리에 문제없다.
 */
@Configuration
public class JusoClientConfig {

    @Bean
    public RestClient jusoRestClient(JusoProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
