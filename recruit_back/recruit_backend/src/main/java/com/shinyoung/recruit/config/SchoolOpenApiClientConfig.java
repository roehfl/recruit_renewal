package com.shinyoung.recruit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 학교 검색 외부 OpenAPI 호출용 {@link RestClient} 빈. NEIS(고교), 대학 학교정보, 대학 학과정보를 각각 구성한다.
 *
 * <p>{@link JusoClientConfig}와 동일하게 auto-configured {@code RestClient.Builder} 빈에 의존하지 않고
 * 정적 팩토리로 직접 생성한다. 외부 의존이므로 타임아웃을 명시해 스레드가 무기한 대기하지 않도록 한다.
 */
@Configuration
public class SchoolOpenApiClientConfig {

    @Bean
    public RestClient neisRestClient(NeisProperties properties) {
        return build(properties.getBaseUrl(), properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
    }

    /** 학교 검색(전국대학및전문대학정보)용. */
    @Bean
    public RestClient univInfoRestClient(UnivInfoProperties properties) {
        return build(properties.getBaseUrl(), properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
    }

    /** 학과 정보(전국대학별학과정보)용. 현재 학교 검색 경로에서는 쓰지 않는다(전공 자동완성 후속 대비). */
    @Bean
    public RestClient univDeptRestClient(UnivDeptProperties properties) {
        return build(properties.getBaseUrl(), properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
    }

    private RestClient build(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }
}
